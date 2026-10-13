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

/** New charts, written as each program writes its own. */
internal object ChartWriter {
    private fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun num(v: Double) = if (v == Math.rint(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()
    private fun hex(c: Int) = "%06X".format(c and 0xFFFFFF)
    private fun colorOf(ch: DocChart, k: Int) = ch.series[k].color ?: OfficeCharts.PALETTE[k % OfficeCharts.PALETTE.size]

    /**
     * Word's chart part (c:chartSpace), its data written in it (Word draws a chart from the numbers its part keeps).
     * ponytail: no spreadsheet inside, so Word's own "Edit data" cannot open one — Writer's Edit data can.
     */
    fun drawingml(ch: DocChart): String {
        val round = ch.kind == ChartKind.Pie || ch.kind == ChartKind.Doughnut
        val n = ch.categories.size
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><c:roundedCorners val="0"/><c:chart>""")
        ch.title?.let { sb.append("""<c:title><c:tx><c:rich><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>${esc(it)}</a:t></a:r></a:p></c:rich></c:tx><c:overlay val="0"/></c:title>""") }
        sb.append("""<c:autoTitleDeleted val="${if (ch.title == null) 1 else 0}"/><c:plotArea><c:layout/>""")
        val tag = when (ch.kind) { ChartKind.Column, ChartKind.Bar -> "barChart"; ChartKind.Line -> "lineChart"; ChartKind.Area -> "areaChart"; ChartKind.Pie -> "pieChart"; ChartKind.Doughnut -> "doughnutChart" }
        sb.append("<c:$tag>")
        if (ch.kind == ChartKind.Column || ch.kind == ChartKind.Bar) sb.append("""<c:barDir val="${if (ch.kind == ChartKind.Bar) "bar" else "col"}"/>""")
        if (!round) sb.append("""<c:grouping val="${if (ch.stacked) "stacked" else if (ch.kind == ChartKind.Column || ch.kind == ChartKind.Bar) "clustered" else "standard"}"/>""")
        sb.append("""<c:varyColors val="${if (round) 1 else 0}"/>""")
        ch.series.forEachIndexed { k, s ->
            val fill = """<a:solidFill><a:srgbClr val="${hex(colorOf(ch, k))}"/></a:solidFill>"""
            sb.append("""<c:ser><c:idx val="$k"/><c:order val="$k"/><c:tx><c:v>${esc(s.name)}</c:v></c:tx>""")
            sb.append(when {
                ch.kind == ChartKind.Line -> """<c:spPr><a:ln w="28575" cap="rnd">$fill<a:round/></a:ln></c:spPr><c:marker><c:symbol val="circle"/><c:size val="5"/><c:spPr>$fill</c:spPr></c:marker>"""
                round -> "" // a slice each: Word colours them (varyColors); the points' own below
                else -> """<c:spPr>$fill</c:spPr>"""
            })
            if (ch.kind == ChartKind.Bar || ch.kind == ChartKind.Column) sb.append("""<c:invertIfNegative val="0"/>""")
            if (round) for (i in 0 until n) sb.append("""<c:dPt><c:idx val="$i"/><c:bubble3D val="0"/><c:spPr><a:solidFill><a:srgbClr val="${hex(s.pointColors[i] ?: OfficeCharts.PALETTE[i % OfficeCharts.PALETTE.size])}"/></a:solidFill><a:ln w="12700"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:ln></c:spPr></c:dPt>""")
            sb.append("""<c:cat><c:strLit><c:ptCount val="$n"/>""")
            ch.categories.forEachIndexed { i, c -> sb.append("""<c:pt idx="$i"><c:v>${esc(c)}</c:v></c:pt>""") }
            sb.append("""</c:strLit></c:cat><c:val><c:numLit><c:formatCode>General</c:formatCode><c:ptCount val="$n"/>""")
            s.values.forEachIndexed { i, v -> if (v != null && i < n) sb.append("""<c:pt idx="$i"><c:v>${num(v)}</c:v></c:pt>""") }
            sb.append("</c:numLit></c:val>")
            if (ch.kind == ChartKind.Line) sb.append("""<c:smooth val="0"/>""")
            sb.append("</c:ser>")
        }
        when (ch.kind) {
            ChartKind.Column, ChartKind.Bar -> sb.append("""<c:gapWidth val="150"/>${if (ch.stacked) """<c:overlap val="100"/>""" else ""}<c:axId val="50010001"/><c:axId val="50010002"/>""")
            ChartKind.Line -> sb.append("""<c:marker val="1"/><c:axId val="50010001"/><c:axId val="50010002"/>""")
            ChartKind.Area -> sb.append("""<c:axId val="50010001"/><c:axId val="50010002"/>""")
            ChartKind.Pie -> sb.append("""<c:firstSliceAng val="0"/>""")
            ChartKind.Doughnut -> sb.append("""<c:firstSliceAng val="0"/><c:holeSize val="50"/>""")
        }
        sb.append("</c:$tag>")
        if (!round) {
            val (catPos, valPos) = if (ch.kind == ChartKind.Bar) "l" to "b" else "b" to "l"
            val line = """<c:spPr><a:ln w="9525"><a:solidFill><a:srgbClr val="D9D9D9"/></a:solidFill></a:ln></c:spPr>"""
            sb.append("""<c:catAx><c:axId val="50010001"/><c:scaling><c:orientation val="minMax"/></c:scaling><c:delete val="0"/><c:axPos val="$catPos"/><c:numFmt formatCode="General" sourceLinked="0"/><c:majorTickMark val="none"/><c:minorTickMark val="none"/><c:tickLblPos val="nextTo"/>$line<c:crossAx val="50010002"/><c:crosses val="autoZero"/><c:auto val="1"/><c:lblAlgn val="ctr"/><c:lblOffset val="100"/><c:noMultiLvlLbl val="0"/></c:catAx>""")
            sb.append("""<c:valAx><c:axId val="50010002"/><c:scaling><c:orientation val="minMax"/></c:scaling><c:delete val="0"/><c:axPos val="$valPos"/><c:majorGridlines>$line</c:majorGridlines><c:numFmt formatCode="General" sourceLinked="0"/><c:majorTickMark val="none"/><c:minorTickMark val="none"/><c:tickLblPos val="nextTo"/><c:spPr><a:ln><a:noFill/></a:ln></c:spPr><c:crossAx val="50010001"/><c:crosses val="autoZero"/><c:crossBetween val="${if (ch.kind == ChartKind.Line) "midCat" else "between"}"/></c:valAx>""")
        }
        sb.append("</c:plotArea>")
        if (ch.legend) sb.append("""<c:legend><c:legendPos val="b"/><c:overlay val="0"/></c:legend>""")
        sb.append("""<c:plotVisOnly val="1"/><c:dispBlanksAs val="gap"/></c:chart><c:txPr><a:bodyPr/><a:lstStyle/><a:p><a:pPr><a:defRPr sz="900"><a:solidFill><a:srgbClr val="595959"/></a:solidFill></a:defRPr></a:pPr><a:endParaRPr lang="en-US"/></a:p></c:txPr></c:chartSpace>""")
        return sb.toString()
    }

    /** LibreOffice's chart object: its content.xml, the data in its own table (the first column the categories). */
    fun odf(ch: DocChart, widthPt: Float, heightPt: Float): String {
        val n = ch.categories.size
        val round = ch.kind == ChartKind.Pie || ch.kind == ChartKind.Doughnut
        val cls = when (ch.kind) { ChartKind.Column, ChartKind.Bar -> "chart:bar"; ChartKind.Line -> "chart:line"; ChartKind.Area -> "chart:area"; ChartKind.Pie -> "chart:circle"; ChartKind.Doughnut -> "chart:ring" }
        fun col(k: Int): String { var i = k; var s = ""; do { s = ('A' + i % 26) + s; i = i / 26 - 1 } while (i >= 0); return s }
        val last = col(ch.series.size)
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8"?><office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0" xmlns:chart="urn:oasis:names:tc:opendocument:xmlns:chart:1.0" office:version="1.3"><office:automatic-styles>""")
        sb.append("""<style:style style:name="pa" style:family="chart"><style:chart-properties chart:vertical="${ch.kind == ChartKind.Bar}" chart:stacked="${ch.stacked}"${if (ch.kind == ChartKind.Line) """ chart:symbol-type="automatic"""" else ""}/></style:style>""")
        ch.series.forEachIndexed { k, _ -> val c = "#" + hex(colorOf(ch, k))
            sb.append("""<style:style style:name="s$k" style:family="chart"><style:graphic-properties draw:fill-color="$c" svg:stroke-color="$c"${if (ch.kind == ChartKind.Line) """ svg:stroke-width="0.08cm"""" else """ draw:stroke="none""""}/></style:style>""") }
        if (round) for (i in 0 until n) sb.append("""<style:style style:name="p$i" style:family="chart"><style:graphic-properties draw:fill-color="#${hex(ch.series.getOrNull(0)?.pointColors?.get(i) ?: OfficeCharts.PALETTE[i % OfficeCharts.PALETTE.size])}" svg:stroke-color="#FFFFFF"/></style:style>""")
        sb.append("""<style:style style:name="axis" style:family="chart"><style:chart-properties chart:display-label="true"/><style:graphic-properties svg:stroke-color="#D9D9D9"/><style:text-properties fo:font-size="9pt" fo:color="#595959"/></style:style>""")
        // A pie clockwise, as LibreOffice writes its own. ponytail: LibreOffice 7 still draws Writer's .odt pies anticlockwise
        // (its own it draws clockwise — something else in its files decides it); the slices and their colours are right.
        sb.append("""<style:style style:name="round" style:family="chart"><style:chart-properties chart:reverse-direction="true"/></style:style>""")
        sb.append("""<style:style style:name="grid" style:family="chart"><style:graphic-properties svg:stroke-color="#D9D9D9"/></style:style>""")
        sb.append("""<style:style style:name="wall" style:family="chart"><style:graphic-properties draw:stroke="none" draw:fill="none"/></style:style>""")
        sb.append("""</office:automatic-styles><office:body><office:chart><chart:chart svg:width="${widthPt}pt" svg:height="${heightPt}pt" chart:class="$cls">""")
        ch.title?.let { sb.append("<chart:title><text:p>${esc(it)}</text:p></chart:title>") }
        if (ch.legend) sb.append("""<chart:legend chart:legend-position="bottom" style:legend-expansion="wide"/>""")
        sb.append("""<chart:plot-area chart:style-name="pa" table:cell-range-address="local-table.${'$'}A${'$'}1:.${'$'}$last${'$'}${n + 1}" chart:data-source-has-labels="both">""")
        if (!round) sb.append("""<chart:axis chart:dimension="x" chart:name="primary-x" chart:style-name="axis"><chart:categories table:cell-range-address="local-table.${'$'}A${'$'}2:.${'$'}A${'$'}${n + 1}"/></chart:axis><chart:axis chart:dimension="y" chart:name="primary-y" chart:style-name="axis"><chart:grid chart:style-name="grid" chart:class="major"/></chart:axis>""")
        else sb.append("""<chart:axis chart:dimension="x" chart:name="primary-x" chart:style-name="round"><chart:categories table:cell-range-address="local-table.${'$'}A${'$'}2:.${'$'}A${'$'}${n + 1}"/></chart:axis><chart:axis chart:dimension="y" chart:name="primary-y"/>""")
        ch.series.forEachIndexed { k, _ ->
            val c = col(k + 1)
            sb.append("""<chart:series chart:style-name="s$k" chart:values-cell-range-address="local-table.${'$'}$c${'$'}2:.${'$'}$c${'$'}${n + 1}" chart:label-cell-address="local-table.${'$'}$c${'$'}1" chart:class="$cls">""")
            if (round && k == 0) for (i in 0 until n) sb.append("""<chart:data-point chart:style-name="p$i"/>""") else sb.append("""<chart:data-point chart:repeated="$n"/>""")
            sb.append("</chart:series>")
        }
        sb.append("""<chart:wall chart:style-name="wall"/></chart:plot-area><table:table table:name="local-table"><table:table-header-columns><table:table-column/></table:table-header-columns><table:table-columns><table:table-column table:number-columns-repeated="${ch.series.size}"/></table:table-columns><table:table-header-rows><table:table-row><table:table-cell><text:p/></table:table-cell>""")
        ch.series.forEach { sb.append("""<table:table-cell office:value-type="string"><text:p>${esc(it.name)}</text:p></table:table-cell>""") }
        sb.append("</table:table-row></table:table-header-rows><table:table-rows>")
        ch.categories.forEachIndexed { i, cat ->
            sb.append("""<table:table-row><table:table-cell office:value-type="string"><text:p>${esc(cat)}</text:p></table:table-cell>""")
            ch.series.forEach { s -> val v = s.values.getOrNull(i)
                sb.append(if (v == null) "<table:table-cell/>" else """<table:table-cell office:value-type="float" office:value="${num(v)}"><text:p>${num(v)}</text:p></table:table-cell>""") }
            sb.append("</table:table-row>")
        }
        sb.append("</table:table-rows></table:table></chart:chart></office:chart></office:body></office:document-content>")
        return sb.toString()
    }
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
