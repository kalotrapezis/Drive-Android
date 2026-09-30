package com.kalotrapezis.drive

import android.graphics.Canvas
import android.graphics.Paint
import org.w3c.dom.Element

/**
 * Charts (the plan, 2026-10-01: Word's floating objects, then charts — the same DrawingML chart Excel and PowerPoint
 * use): what a chart shows — its kind, title, categories and each series' values — read from Word's chart part or
 * LibreOffice's chart object, drawn on the pages, and written for a new one.
 */
internal enum class ChartKind(val label: String) { Column("Column"), Bar("Bar"), Line("Line"), Area("Area"), Pie("Pie"), Doughnut("Doughnut") }

/** One series: its name, a value for each category (null: none), its colour (RGB; null: the palette's) and its points' own. */
internal data class ChartSeries(val name: String, val values: List<Double?>, val color: Int? = null, val pointColors: Map<Int, Int> = emptyMap())

internal data class DocChart(val kind: ChartKind, val title: String?, val categories: List<String>, val series: List<ChartSeries>, val stacked: Boolean = false,
    val legend: Boolean = true)

internal object OfficeCharts {
    private const val C = "http://schemas.openxmlformats.org/drawingml/2006/chart"
    private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val CHART = "urn:oasis:names:tc:opendocument:xmlns:chart:1.0"
    private const val TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    private const val OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    private const val STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    private const val DRAW = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    private const val SVG = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"

    /** Office's theme colours (accent 1–6): series without a colour of their own take these in turn. */
    val PALETTE = listOf(0x4472C4, 0xED7D31, 0xA5A5A5, 0xFFC000, 0x5B9BD5, 0x70AD47)

    private fun Element.all(ns: String, name: String): List<Element> = getElementsByTagNameNS(ns, name).let { l -> (0 until l.length).map { l.item(it) as Element } }
    private fun Element.first(ns: String, name: String): Element? = getElementsByTagNameNS(ns, name).item(0) as Element?

    /** Word's (and PowerPoint's, Excel's) chart part: c:chartSpace. Null: a kind Writer does not draw. */
    fun readDrawingml(bytes: ByteArray): DocChart? = runCatching {
        val root = OfficeFormats.parse(bytes).documentElement
        val plot = root.first(C, "plotArea") ?: return null
        val types = mapOf("barChart" to ChartKind.Column, "bar3DChart" to ChartKind.Column, "lineChart" to ChartKind.Line, "line3DChart" to ChartKind.Line,
            "scatterChart" to ChartKind.Line, "areaChart" to ChartKind.Area, "area3DChart" to ChartKind.Area, "pieChart" to ChartKind.Pie,
            "pie3DChart" to ChartKind.Pie, "ofPieChart" to ChartKind.Pie, "doughnutChart" to ChartKind.Doughnut)
        val group = plot.kids().firstOrNull { it.namespaceURI == C && it.localName in types } ?: return null
        var kind = types.getValue(group.localName)
        if (kind == ChartKind.Column && group.kid(C, "barDir")?.getAttribute("val") == "bar") kind = ChartKind.Bar
        val stacked = group.kid(C, "grouping")?.getAttribute("val").let { it == "stacked" || it == "percentStacked" }
        fun points(e: Element?): Map<Int, String> = e?.all(C, "pt")?.associate { p -> p.getAttribute("idx").toInt() to (p.kid(C, "v")?.textContent ?: "") }.orEmpty()
        fun color(spPr: Element?): Int? {
            val fill = spPr?.kids()?.firstOrNull { it.localName == "solidFill" } ?: spPr?.kid(A, "ln")?.kid(A, "solidFill") ?: return null
            fill.kid(A, "srgbClr")?.getAttribute("val")?.toIntOrNull(16)?.let { return it }
            return fill.kid(A, "schemeClr")?.getAttribute("val")?.removePrefix("accent")?.toIntOrNull()?.let { PALETTE.getOrNull(it - 1) }
        }
        var categories = emptyList<String>()
        val series = group.kids().filter { it.localName == "ser" }.map { s ->
            val cat = points(s.kid(C, "cat") ?: s.kid(C, "xVal"))
            val vals = points(s.kid(C, "val") ?: s.kid(C, "yVal"))
            val n = maxOf((cat.keys.maxOrNull() ?: -1), (vals.keys.maxOrNull() ?: -1)) + 1
            if (categories.size < n) categories = (0 until n).map { cat[it] ?: "${it + 1}" }
            val spPr = s.kid(C, "spPr")
            ChartSeries(s.kid(C, "tx")?.all(C, "v")?.firstOrNull()?.textContent ?: s.kid(C, "tx")?.textContent?.trim().orEmpty(),
                (0 until n).map { vals[it]?.toDoubleOrNull() },
                color(spPr),
                s.kids().filter { it.localName == "dPt" }.mapNotNull { d -> d.kid(C, "idx")?.getAttribute("val")?.toIntOrNull()?.let { i -> color(d.kid(C, "spPr"))?.let { i to it } } }.toMap())
        }
        if (series.isEmpty()) return null
        val title = root.first(C, "title")?.all(A, "t")?.joinToString("") { it.textContent }?.takeIf { it.isNotBlank() }
        DocChart(kind, title, categories, series, stacked, root.first(C, "legend") != null)
    }.getOrNull()

    /** LibreOffice's chart object: its content.xml (chart:chart, its data in its own table). */
    fun readOdf(bytes: ByteArray): DocChart? = runCatching {
        val root = OfficeFormats.parse(bytes).documentElement
        val chart = root.first(CHART, "chart") ?: return null
        val styles = root.all(STYLE, "style").associateBy { it.getAttributeNS(STYLE, "name") }
        fun props(style: String?, name: String) = styles[style]?.kids()?.firstOrNull { it.localName == name }
        val plot = chart.first(CHART, "plot-area")
        val plotProps = props(plot?.getAttributeNS(CHART, "style-name"), "chart-properties")
        var kind = when (chart.getAttributeNS(CHART, "class")) {
            "chart:bar" -> ChartKind.Column; "chart:line", "chart:scatter" -> ChartKind.Line; "chart:area" -> ChartKind.Area
            "chart:circle" -> ChartKind.Pie; "chart:ring" -> ChartKind.Doughnut; else -> return null
        }
        if (kind == ChartKind.Column && plotProps?.getAttributeNS(CHART, "vertical") == "true") kind = ChartKind.Bar
        val stacked = plotProps?.let { it.getAttributeNS(CHART, "stacked") == "true" || it.getAttributeNS(CHART, "percentage") == "true" } == true
        val table = chart.first(TABLE, "table") ?: return null
        fun cells(row: Element) = row.all(TABLE, "table-cell").flatMap { c -> List(c.getAttributeNS(TABLE, "number-columns-repeated").toIntOrNull() ?: 1) { c } }
        val header = table.first(TABLE, "table-header-rows")?.first(TABLE, "table-row")?.let { cells(it) }.orEmpty()
        val rows = (table.first(TABLE, "table-rows") ?: table).kids().filter { it.localName == "table-row" }.map { cells(it) }
        val categories = rows.map { it.firstOrNull()?.textContent?.trim().orEmpty() }
        val colors = plot?.kids()?.filter { it.localName == "series" }?.map { s ->
            props(s.getAttributeNS(CHART, "style-name"), "graphic-properties")?.let { g ->
                (if (kind == ChartKind.Line) g.getAttributeNS(SVG, "stroke-color") else g.getAttributeNS(DRAW, "fill-color")).removePrefix("#").toIntOrNull(16)
            } }.orEmpty()
        val count = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0) - 1
        if (count <= 0) return null
        val series = (1..count).map { k ->
            ChartSeries(header.getOrNull(k)?.textContent?.trim().orEmpty(), rows.map { r -> r.getOrNull(k)?.let { it.getAttributeNS(OFFICE, "value").toDoubleOrNull() ?: it.textContent.trim().toDoubleOrNull() } },
                colors.getOrNull(k - 1))
        }
        DocChart(kind, chart.first(CHART, "title")?.textContent?.trim()?.takeIf { it.isNotEmpty() }, categories, series, stacked, chart.first(CHART, "legend") != null)
    }.getOrNull()
}

/**
 * A chart drawn in its bounds, which count in points: its title at the top, its legend at the foot, the plot between —
 * value gridlines and labels, the categories along the other axis. As Word draws a clustered or stacked chart, flat.
 */
internal class ChartDrawable(val chart: DocChart) : android.graphics.drawable.Drawable() {
    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.clipRect(0, 0, b.width(), b.height())
        drawChart(canvas, b.width().toFloat(), b.height().toFloat())
        canvas.restore()
    }

    private fun color(i: Int, own: Int?) = 0xFF000000.toInt() or (own ?: OfficeCharts.PALETTE[i % OfficeCharts.PALETTE.size])

    private fun drawChart(c: Canvas, w: Float, h: Float) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF595959.toInt(); textSize = 9f }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(0xFFFFFFFF.toInt())
        var top = 6f
        chart.title?.let { t ->
            val p = Paint(text).apply { textSize = 13f; color = 0xFF404040.toInt() }
            c.drawText(t, (w - p.measureText(t)) / 2, top + 12f, p); top += 20f
        }
        val round = chart.kind == ChartKind.Pie || chart.kind == ChartKind.Doughnut
        // The legend: the series (a pie: its slices), a square of each colour and its name, centred at the foot.
        val keys = if (round) chart.categories.mapIndexed { i, n -> n to color(i, chart.series[0].pointColors[i]) }
            else chart.series.mapIndexed { i, s -> s.name to color(i, s.color) }
        val legendW = keys.sumOf { (n, _) -> (text.measureText(n) + 18f).toDouble() }.toFloat()
        var x = ((w - legendW) / 2).coerceAtLeast(4f)
        val legendY = h - 8f
        if (chart.legend) for ((n, col) in keys) { fill.color = col; c.drawRect(x, legendY - 7f, x + 7f, legendY, fill); c.drawText(n, x + 10f, legendY, text); x += text.measureText(n) + 18f }
        val bottom = h - if (chart.legend) 20f else 6f
        if (round) { drawPie(c, w, top, bottom, fill); return }

        // The values' range, from 0, gridlines at round steps.
        val sums = chart.categories.indices.map { i -> chart.series.sumOf { it.values.getOrNull(i) ?: 0.0 } }
        val all = if (chart.stacked) sums else chart.series.flatMap { it.values.filterNotNull() }
        val max = (all.maxOrNull() ?: 1.0).coerceAtLeast(0.0); val min = (all.minOrNull() ?: 0.0).coerceAtMost(0.0)
        val step = niceStep((max - min).takeIf { it > 0 } ?: 1.0)
        val hi = Math.ceil(max / step) * step; val lo = Math.floor(min / step) * step
        fun label(v: Double) = if (v == Math.rint(v)) "%d".format(v.toLong()) else "%.2f".format(v).trimEnd('0').trimEnd('.', ',')
        val ticks = generateSequence(lo) { it + step }.takeWhile { it <= hi + step / 2 }.toList()
        val horizontal = chart.kind == ChartKind.Bar
        val catW = chart.categories.maxOfOrNull { text.measureText(it) } ?: 0f
        val left = 6f + if (horizontal) catW + 4f else ticks.maxOf { text.measureText(label(it)) } + 4f
        val plotTop = top + 4f
        val plotBottom = bottom - (if (horizontal) 12f else 14f)
        val right = w - 10f
        val grid = Paint().apply { color = 0xFFD9D9D9.toInt(); strokeWidth = 0.5f }
        val n = chart.categories.size.coerceAtLeast(1)
        // Where value v stands along the value axis.
        fun at(v: Double) = if (horizontal) (left + (v - lo) / (hi - lo) * (right - left)).toFloat() else (plotBottom - (v - lo) / (hi - lo) * (plotBottom - plotTop)).toFloat()
        for (t in ticks) {
            val p = at(t)
            if (horizontal) { c.drawLine(p, plotTop, p, plotBottom, grid); val s = label(t); c.drawText(s, p - text.measureText(s) / 2, plotBottom + 10f, text) }
            else { c.drawLine(left, p, right, p, grid); val s = label(t); c.drawText(s, left - 4f - text.measureText(s), p + 3f, text) }
        }
        // The categories: a band each, their names under it (every other one when they do not fit).
        val band = (if (horizontal) plotBottom - plotTop else right - left) / n
        val every = if (horizontal) 1 else Math.ceil((catW + 4f) / band.toDouble()).toInt().coerceAtLeast(1)
        chart.categories.forEachIndexed { i, name ->
            if (i % every != 0) return@forEachIndexed
            // A bar chart lists its first category at the bottom, as Word's.
            if (horizontal) c.drawText(name, left - 4f - text.measureText(name), plotBottom - (i + 0.5f) * band + 3f, text)
            else c.drawText(name, left + (i + 0.5f) * band - text.measureText(name) / 2, plotBottom + 11f, text)
        }
        val zero = at(0.0.coerceIn(lo, hi))
        val ns = chart.series.size
        when (chart.kind) {
            ChartKind.Column, ChartKind.Bar -> {
                val under = DoubleArray(n)
                chart.series.forEachIndexed { k, s ->
                    fill.color = color(k, s.color)
                    s.values.forEachIndexed { i, v ->
                        if (v == null || i >= n) return@forEachIndexed
                        val gap = band * 0.2f
                        val thick = if (chart.stacked) band - 2 * gap else (band - 2 * gap) / ns
                        val from = if (chart.stacked) under[i] else 0.0
                        val a = at(from); val e = at(from + v)
                        s.pointColors[i]?.let { fill.color = 0xFF000000.toInt() or it }
                        // Bars: the first series at the bottom of its band, as Word's.
                        if (horizontal) { val y1 = plotBottom - i * band - gap - (if (chart.stacked) 0 else k) * thick; c.drawRect(minOf(a, e), y1 - thick, maxOf(a, e), y1, fill) }
                        else { val x0 = left + i * band + gap + (if (chart.stacked) 0 else k) * thick; c.drawRect(x0, minOf(a, e), x0 + thick, maxOf(a, e), fill) }
                        fill.color = color(k, s.color)
                        if (chart.stacked) under[i] += v
                    }
                }
            }
            ChartKind.Line, ChartKind.Area -> {
                val under = DoubleArray(n)
                chart.series.forEachIndexed { k, s ->
                    val col = color(k, s.color)
                    val pts = s.values.mapIndexedNotNull { i, v -> v?.takeIf { i < n }?.let { i to (if (chart.stacked) under[i] + it else it) } }
                    val path = android.graphics.Path()
                    pts.forEachIndexed { j, (i, v) -> val px = left + (i + 0.5f) * band; val py = at(v); if (j == 0) path.moveTo(px, py) else path.lineTo(px, py) }
                    if (chart.kind == ChartKind.Area && pts.isNotEmpty()) {
                        path.lineTo(left + (pts.last().first + 0.5f) * band, zero); path.lineTo(left + (pts.first().first + 0.5f) * band, zero); path.close()
                        fill.color = col; fill.alpha = 200; fill.style = Paint.Style.FILL; c.drawPath(path, fill); fill.alpha = 255
                    } else {
                        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = Paint.Style.STROKE; strokeWidth = 2f; strokeJoin = Paint.Join.ROUND })
                        fill.color = col; for ((i, v) in pts) c.drawCircle(left + (i + 0.5f) * band, at(v), 2.5f, fill)
                    }
                    if (chart.stacked) s.values.forEachIndexed { i, v -> if (i < n) under[i] += v ?: 0.0 }
                }
            }
            else -> Unit
        }
        // The axis the bars stand on.
        val axis = Paint().apply { color = 0xFFBFBFBF.toInt(); strokeWidth = 0.75f }
        if (horizontal) c.drawLine(zero, plotTop, zero, plotBottom, axis) else c.drawLine(left, zero, right, zero, axis)
    }

    /** A pie (or doughnut) of the first series, from twelve o'clock round clockwise, as Word's. */
    private fun drawPie(c: Canvas, w: Float, top: Float, bottom: Float, fill: Paint) {
        val s = chart.series[0]
        val total = s.values.sumOf { (it ?: 0.0).coerceAtLeast(0.0) }.takeIf { it > 0 } ?: return
        val r = (minOf(w - 20f, bottom - top - 8f) / 2).coerceAtLeast(4f)
        val cx = w / 2; val cy = (top + bottom) / 2
        val oval = android.graphics.RectF(cx - r, cy - r, cx + r, cy + r)
        var start = -90f
        s.values.forEachIndexed { i, v ->
            val sweep = ((v ?: 0.0).coerceAtLeast(0.0) / total * 360).toFloat()
            fill.color = color(i, s.pointColors[i])
            c.drawArc(oval, start, sweep, true, fill)
            start += sweep
        }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
        start = -90f
        s.values.forEach { v -> val sweep = ((v ?: 0.0).coerceAtLeast(0.0) / total * 360).toFloat(); c.drawArc(oval, start, sweep, true, edge); start += sweep }
        if (chart.kind == ChartKind.Doughnut) { fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(cx, cy, r * 0.5f, fill) }
    }

    /** A round step for about five gridlines: 1, 2 or 5 times a power of ten. */
    private fun niceStep(range: Double): Double {
        val raw = range / 5
        val p = Math.pow(10.0, Math.floor(Math.log10(raw)))
        return listOf(1.0, 2.0, 5.0, 10.0).map { it * p }.first { it >= raw }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
    @Deprecated("Drawable") override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
}
