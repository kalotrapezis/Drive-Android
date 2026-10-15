package com.kalotrapezis.drive

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormatSymbols
import kotlin.math.max
import kotlin.math.min

/**
 * Excel's viewer (the user, 15 Oct: "start with the viewer and let's complete it like we did with Word"): a sheet as
 * the file draws it — its widths, heights, fills, borders, fonts, merged cells, frozen rows and columns — on white
 * paper with Excel's letters and numbers round it; pinch to zoom, fling to scroll, a tap shows the cell (its formula,
 * all its words) in the bar above; the sheets as tabs below.
 */
@Composable
internal fun SheetScreen(back: () -> Unit, open: String) {
    val context = LocalContext.current
    val file = remember(open) { Uri.parse(open).path?.let(::File) }
    var book by remember { mutableStateOf<Workbook?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableIntStateOf(-1) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val views = remember { HashMap<Int, SheetView>() }
    remember { OfficeFonts.assets = context.applicationContext.assets }
    BackHandler { back() }

    LaunchedEffect(open) {
        WriterLog.init(context)
        val f = file ?: run { failed = "This file cannot be opened."; return@LaunchedEffect }
        WriterLog.i("Sheets: open .${f.extension} of ${f.length() / 1024} KB")
        withContext(Dispatchers.IO) {
            runCatching {
                val sym = DecimalFormatSymbols.getInstance()
                WriterLog.timed("Sheets: workbook read") { Sheets.read(f, sym.decimalSeparator, sym.groupingSeparator) }
            }
        }.onSuccess { b ->
            book = b
            index = if (b.tabs[b.active].hidden) b.tabs.indexOfFirst { !it.hidden }.coerceAtLeast(0) else b.active
        }.onFailure {
            WriterLog.e("Sheets: could not open", it)
            failed = if (it is OutOfMemoryError) "This spreadsheet is too big to open on this device." else "This spreadsheet could not be opened."
        }
    }
    LaunchedEffect(book, index) {
        val b = book ?: return@LaunchedEffect
        if (index < 0) return@LaunchedEffect
        sheet = null
        sheet = withContext(Dispatchers.IO) {
            runCatching { b.sheet(index) }.onFailure { WriterLog.e("Sheets: sheet ${index + 1} could not be read", it) }.getOrNull()
        }
        if (sheet == null) failed = "This sheet could not be read."
    }

    val view = views.getOrPut(index) { SheetView() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = islandColor(), contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(file?.name ?: "Spreadsheet", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("View only", style = MaterialTheme.typography.labelSmall, color = islandContentColor().copy(alpha = 0.7f))
                    }
                }
                sheet?.let { CellBar(it, view) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
            val s = sheet
            val b = book
            when {
                failed != null -> Text(failed!!, color = Color.Black, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                s != null && b != null -> SheetGrid(s, b.styles, view)
                else -> Text("Opening…", color = Color.DarkGray, modifier = Modifier.align(Alignment.Center))
            }
        }
        book?.let { b -> SheetTabs(b, index, sheet) { i -> WriterLog.i("Sheets: tab ${i + 1} of ${b.tabs.size}"); failed = null; index = i } }
    }
}

/** Where one sheet is looked at: scrolled (in the sheet's pixels, past the frozen panes), zoomed, the cell chosen. */
internal class SheetView {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var zoom by mutableFloatStateOf(1f)
    var row by mutableIntStateOf(0)
    var col by mutableIntStateOf(0)
    var opened by mutableStateOf(false) // the cell bar shows all the cell's words
}

/** The chosen cell's name and what is in it: the formula when it has one, the words in full. */
@Composable
private fun CellBar(sheet: Sheet, view: SheetView) {
    val merge = sheet.merge(view.row, view.col)
    val r = merge?.r1 ?: view.row; val c = merge?.c1 ?: view.col
    val cell = sheet[r, c]
    val content = cell?.formula ?: cell?.text.orEmpty()
    Row(Modifier.fillMaxWidth().clickable { view.opened = !view.opened }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(Sheets.colName(c) + (r + 1), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
        Text("fx", style = MaterialTheme.typography.labelLarge, color = islandContentColor().copy(alpha = 0.6f), fontFamily = FontFamily.Serif, modifier = Modifier.padding(end = 10.dp))
        val text = content + (cell?.link?.let { "\n→ $it" } ?: "")
        if (view.opened) Box(Modifier.weight(1f).heightIn(max = 220.dp).verticalScroll(rememberScrollState())) { Text(text, style = MaterialTheme.typography.bodyMedium) }
        else Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** The sheets as Excel's tabs along the bottom (the file's hidden ones left out), the open one in Excel's green. */
@Composable
private fun SheetTabs(book: Workbook, index: Int, open: Sheet?, pick: (Int) -> Unit) {
    if (book.tabs.count { !it.hidden } < 2 && book.tabs.size < 2) return
    Surface(color = islandColor(), contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            book.tabs.forEachIndexed { i, tab ->
                if (tab.hidden) return@forEachIndexed
                val on = i == index
                val mark = open?.takeIf { on }?.tab?.let { Color(it) }
                Surface(
                    shape = RoundedCornerShape(10.dp), color = if (on) EXCEL_GREEN else Color.Transparent,
                    contentColor = if (on) Color.White else islandContentColor(),
                    modifier = Modifier.clickable { if (!on) pick(i) },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tab.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        if (mark != null) Box(Modifier.width(24.dp).heightIn(min = 3.dp, max = 3.dp).background(mark))
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetGrid(sheet: Sheet, styles: List<SheetStyle>, view: SheetView) {
    val density = LocalDensity.current.density
    val painter = remember(sheet) { SheetPainter(sheet, styles, density) }
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var fling by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(painter) { onDispose { painter.report() } }
    Canvas(
        Modifier.fillMaxSize().onSizeChanged { size = it; painter.clamp(view, it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(painter) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fling?.cancel()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var moved = false; var pinched = false; var travel = Offset.Zero
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.changes.none { it.pressed }) break
                        val zoom = e.calculateZoom(); val pan = e.calculatePan()
                        if (!moved) { travel += pan; if (travel.getDistance() > viewConfiguration.touchSlop || zoom != 1f) moved = true }
                        if (moved) {
                            if (zoom != 1f) { pinched = true; painter.zoomAt(view, zoom, e.calculateCentroid(useCurrent = true), size.width.toFloat(), size.height.toFloat()) }
                            painter.pan(view, pan, size.width.toFloat(), size.height.toFloat())
                            e.changes.forEach { it.consume() }
                        }
                        e.changes.firstOrNull { it.id == down.id && it.pressed }?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                    }
                    if (!moved) painter.tap(view, down.position)
                    else if (!pinched) {
                        val v = tracker.calculateVelocity()
                        fling = scope.launch {
                            var last = Offset.Zero
                            Animatable(Offset.Zero, Offset.VectorConverter).animateDecay(Offset(v.x, v.y), exponentialDecay(frictionMultiplier = 1.4f)) {
                                painter.pan(view, value - last, size.width.toFloat(), size.height.toFloat()); last = value
                            }
                        }
                    }
                }
            },
    ) {
        // Read here, so a scroll or a zoom redraws without recomposing.
        val x = view.x; val y = view.y; val zoom = view.zoom; val row = view.row; val col = view.col
        drawIntoCanvas { painter.draw(it.nativeCanvas, this.size.width, this.size.height, x, y, zoom, row, col) }
    }
}

/**
 * Draws a sheet on an Android canvas. Everything in the cells is drawn in the sheet's own pixels with the canvas
 * scaled, so text is measured and wrapped once, not again at every step of a pinch.
 */
internal class SheetPainter(private val sheet: Sheet, private val styles: List<SheetStyle>, private val density: Float) {
    /** A margin of empty cells past the used ones, as Excel's sheet goes on. */
    private val cols = max(sheet.cols, sheet.frozenCols) + 12
    private val rows = max(sheet.rows, sheet.frozenRows) + 60
    private val colX = FloatArray(cols + 1).also { a -> for (c in 0 until cols) a[c + 1] = a[c] + sheet.width(c) }
    private val rowY = FloatArray(rows + 1).also { a -> for (r in 0 until rows) a[r + 1] = a[r] + sheet.height(r) }
    private val fc = min(sheet.frozenCols, cols); private val fr = min(sheet.frozenRows, rows)

    private val headerH = 24 * density
    // Not in apply: TextPaint has a density of its own (1), which `density` there would mean.
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).also { it.textSize = 12 * density; it.color = 0xFF5F5F5F.toInt(); it.textAlign = Paint.Align.CENTER }
    private val headerW = max(36 * density, headerPaint.measureText(rows.toString()) + 14 * density)
    private val fill = Paint()
    private val line = Paint().apply { style = Paint.Style.STROKE }
    private val grid = Paint().apply { color = 0xFFD9D9D9.toInt(); style = Paint.Style.STROKE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val dash = DashPathEffect(floatArrayOf(3f, 2f), 0f)
    private val dots = DashPathEffect(floatArrayOf(1f, 1f), 0f)
    private val faces = HashMap<String, Typeface>()
    private val wrapped = HashMap<Long, StaticLayout>()
    private var lines = FloatArray(4096)

    // What the log is told about drawing: the first frame, and how many were slow.
    private var frames = 0; private var slow = 0; private var first = -1L; private var worst = 0L

    private fun scale(zoom: Float) = zoom * density
    private fun find(a: FloatArray, v: Float, n: Int): Int {
        var lo = 0; var hi = n - 1
        while (lo < hi) { val mid = (lo + hi + 1) ushr 1; if (a[mid] <= v) lo = mid else hi = mid - 1 }
        return lo
    }

    fun clamp(view: SheetView, w: Float, h: Float) {
        val s = scale(view.zoom)
        val bodyW = (w - headerW) / s - colX[fc]; val bodyH = (h - headerH) / s - rowY[fr]
        view.x = view.x.coerceIn(0f, max(0f, colX[cols] - colX[fc] - bodyW))
        view.y = view.y.coerceIn(0f, max(0f, rowY[rows] - rowY[fr] - bodyH))
    }

    fun pan(view: SheetView, by: Offset, w: Float, h: Float) {
        val s = scale(view.zoom)
        view.x -= by.x / s; view.y -= by.y / s
        clamp(view, w, h)
    }

    /** Zoom about the fingers: the sheet's point under them stays under them. */
    fun zoomAt(view: SheetView, factor: Float, at: Offset, w: Float, h: Float) {
        val s = scale(view.zoom)
        val px = colX[fc] + view.x + (at.x - headerW - colX[fc] * s) / s
        val py = rowY[fr] + view.y + (at.y - headerH - rowY[fr] * s) / s
        view.zoom = (view.zoom * factor).coerceIn(0.25f, 4f)
        val s2 = scale(view.zoom)
        view.x = px - colX[fc] - (at.x - headerW - colX[fc] * s2) / s2
        view.y = py - rowY[fr] - (at.y - headerH - rowY[fr] * s2) / s2
        clamp(view, w, h)
    }

    /** The cell under a tap (a merged one's first cell); a tap on the letters or numbers picks that column's or row's first. */
    fun tap(view: SheetView, at: Offset) {
        val s = scale(view.zoom)
        val sx = if (at.x < headerW + colX[fc] * s) (at.x - headerW) / s else colX[fc] + view.x + (at.x - headerW - colX[fc] * s) / s
        val sy = if (at.y < headerH + rowY[fr] * s) (at.y - headerH) / s else rowY[fr] + view.y + (at.y - headerH - rowY[fr] * s) / s
        val c = find(colX, max(0f, sx), cols); val r = find(rowY, max(0f, sy), rows)
        view.row = r; view.col = c; view.opened = false
        val cell = sheet.merge(r, c)?.let { sheet[it.r1, it.c1] } ?: sheet[r, c]
        WriterLog.i("Sheets: tap ${Sheets.colName(c)}${r + 1} (${cell?.kind ?: "empty"}${if (cell?.formula != null) ", formula" else ""})")
    }

    fun draw(c: android.graphics.Canvas, w: Float, h: Float, x: Float, y: Float, zoom: Float, row: Int, col: Int) {
        val start = System.nanoTime()
        val s = scale(zoom)
        val fw = colX[fc] * s; val fh = rowY[fr] * s
        val bx = headerW + fw; val by = headerH + fh
        val sel = sheet.merge(row, col) ?: SheetRange(row, col, row, col)
        pane(c, bx, by, w - bx, h - by, colX[fc] + x, rowY[fr] + y, s, sel)
        if (fr > 0) pane(c, bx, headerH, w - bx, fh, colX[fc] + x, 0f, s, sel)
        if (fc > 0) pane(c, headerW, by, fw, h - by, 0f, rowY[fr] + y, s, sel)
        if (fr > 0 && fc > 0) pane(c, headerW, headerH, fw, fh, 0f, 0f, s, sel)
        headers(c, w, h, x, y, s, sel)
        // The frozen panes' edges, as Excel draws them.
        line.color = 0xFFA0A0A0.toInt(); line.strokeWidth = 1.5f * density; line.pathEffect = null
        if (fr > 0) c.drawLine(0f, by, w, by, line)
        if (fc > 0) c.drawLine(bx, 0f, bx, h, line)
        val ms = (System.nanoTime() - start) / 1_000_000
        if (first < 0) { first = ms; WriterLog.i("Sheets: first draw $ms ms (${sheet.rows}×${sheet.cols}, ${sheet.cells.size} cells)") }
        frames++; if (ms > 32) slow++; worst = max(worst, ms)
    }

    fun report() { if (frames > 1) WriterLog.i("Sheets: $frames frames drawn, $slow over 32 ms, the slowest $worst ms") }

    private fun headers(c: android.graphics.Canvas, w: Float, h: Float, x: Float, y: Float, s: Float, sel: SheetRange) {
        fill.color = 0xFFF3F3F3.toInt()
        c.drawRect(0f, 0f, w, headerH, fill); c.drawRect(0f, 0f, headerW, h, fill)
        grid.strokeWidth = 1f; grid.color = 0xFFC8C8C8.toInt()
        val fw = colX[fc] * s; val fh = rowY[fr] * s
        val base = headerH / 2 - (headerPaint.ascent() + headerPaint.descent()) / 2
        fun col(k: Int, left: Float, right: Float) {
            if (right - left < 1f) return
            val on = k in sel.c1..sel.c2
            if (on) { fill.color = 0xFFD3E9DC.toInt(); c.drawRect(left, 0f, right, headerH, fill); fill.color = EXCEL_GREEN_ARGB; c.drawRect(left, headerH - 2 * density, right, headerH, fill) }
            c.drawLine(right, 0f, right, headerH, grid)
            val name = Sheets.colName(k)
            if (headerPaint.measureText(name) < right - left) { headerPaint.color = if (on) EXCEL_GREEN_ARGB else 0xFF5F5F5F.toInt(); c.drawText(name, (left + right) / 2, base, headerPaint) }
        }
        fun row(k: Int, top: Float, bottom: Float) {
            if (bottom - top < 1f) return
            val on = k in sel.r1..sel.r2
            if (on) { fill.color = 0xFFD3E9DC.toInt(); c.drawRect(0f, top, headerW, bottom, fill); fill.color = EXCEL_GREEN_ARGB; c.drawRect(headerW - 2 * density, top, headerW, bottom, fill) }
            c.drawLine(0f, bottom, headerW, bottom, grid)
            if (bottom - top >= headerPaint.textSize * 0.9f) {
                headerPaint.color = if (on) EXCEL_GREEN_ARGB else 0xFF5F5F5F.toInt()
                c.drawText((k + 1).toString(), headerW / 2, (top + bottom) / 2 - (headerPaint.ascent() + headerPaint.descent()) / 2, headerPaint)
            }
        }
        // Column letters: the frozen ones, then the scrolled ones beside them.
        c.save(); c.clipRect(headerW, 0f, w, headerH)
        for (k in 0 until fc) col(k, headerW + colX[k] * s, headerW + colX[k + 1] * s)
        c.clipRect(headerW + fw, 0f, w, headerH)
        val x0 = colX[fc] + x
        var k = find(colX, x0, cols)
        while (k < cols) { val l = headerW + fw + (colX[k] - x0) * s; if (l > w) break; col(k, l, headerW + fw + (colX[k + 1] - x0) * s); k++ }
        c.restore()
        c.save(); c.clipRect(0f, headerH, headerW, h)
        for (r in 0 until fr) row(r, headerH + rowY[r] * s, headerH + rowY[r + 1] * s)
        c.clipRect(0f, headerH + fh, headerW, h)
        val y0 = rowY[fr] + y
        var r = find(rowY, y0, rows)
        while (r < rows) { val t = headerH + fh + (rowY[r] - y0) * s; if (t > h) break; row(r, t, headerH + fh + (rowY[r + 1] - y0) * s); r++ }
        c.restore()
        c.drawLine(0f, headerH, w, headerH, grid); c.drawLine(headerW, 0f, headerW, h, grid)
    }

    private fun styleOf(r: Int, c: Int, cell: SheetCell?): SheetStyle =
        styles.getOrNull(cell?.style ?: sheet.rowStyles[r] ?: sheet.colStyles[c] ?: 0) ?: SheetStyle()

    private fun face(st: SheetStyle): Typeface {
        val key = "${st.font}|${st.bold}|${st.italic}"
        return faces.getOrPut(key) {
            val base = OfficeFonts.typeface(st.font) ?: Typeface.SANS_SERIF
            Typeface.create(base, (if (st.bold) Typeface.BOLD else 0) or (if (st.italic) Typeface.ITALIC else 0))
        }
    }

    private fun textPaint(st: SheetStyle, cell: SheetCell) {
        text.typeface = face(st)
        text.textSize = st.size * 4 / 3
        text.color = cell.color ?: st.color ?: if (cell.link != null && st.color == null) 0xFF0563C1.toInt() else 0xFF000000.toInt()
        text.isUnderlineText = st.underline
        text.isStrikeThruText = st.strike
    }

    /** One pane: the sheet from ([sx0], [sy0]) drawn at ([ox], [oy]) on the screen, [w]×[h] of it, [s] screen pixels a sheet pixel. */
    private fun pane(c: android.graphics.Canvas, ox: Float, oy: Float, w: Float, h: Float, sx0: Float, sy0: Float, s: Float, sel: SheetRange) {
        if (w <= 0 || h <= 0) return
        c.save()
        c.clipRect(ox, oy, ox + w, oy + h)
        c.translate(ox, oy); c.scale(s, s); c.translate(-sx0, -sy0)
        val sx1 = sx0 + w / s; val sy1 = sy0 + h / s
        val c0 = find(colX, sx0, cols); val c1 = find(colX, sx1, cols)
        val r0 = find(rowY, sy0, rows); val r1 = find(rowY, sy1, rows)
        fill.color = 0xFFFFFFFF.toInt()
        c.drawRect(sx0, sy0, sx1, sy1, fill)
        val merges = sheet.merges.filter { it.r2 >= r0 && it.r1 <= r1 && it.c2 >= c0 && it.c1 <= c1 }

        // The words first worked out, so the grid knows which lines long words cover (Excel draws no line under them).
        class Words(val cell: SheetCell, val st: SheetStyle, val left: Float, val top: Float, val right: Float, val bottom: Float, val clipL: Float, val clipR: Float, val r: Int, val c: Int)
        val words = ArrayList<Words>()
        val covered = HashSet<Long>() // key(row, column whose left line is covered)
        val reach = 40
        for (r in r0..r1) {
            if (rowY[r + 1] - rowY[r] <= 0f) continue
            for (k in max(0, c0 - reach)..min(cols - 1, c1 + reach)) {
                val cell = sheet[r, k] ?: continue
                if (cell.text.isEmpty() || sheet.merge(r, k) != null) continue
                val st = styleOf(r, k, cell)
                val l = colX[k]; val rt = colX[k + 1]
                if (rt - l <= 0f) continue
                var clipL = l; var clipR = rt
                val align = align(st, cell)
                if (!st.wrap && cell.kind == SheetKind.Text && st.rotation == 0) {
                    textPaint(st, cell)
                    val need = text.measureText(cell.text.substringBefore('\n')) + 4f + st.indent * 9f
                    if (need > rt - l) {
                        // Into the empty cells beside it, as far as the words need.
                        fun free(n: Int) = n in 0 until cols && sheet.merge(r, n) == null && sheet[r, n]?.text.isNullOrEmpty()
                        if (align == SheetAlign.Left || align == SheetAlign.General) {
                            var n = k + 1; while (clipR - l < need && free(n)) { covered += Sheet.key(r, n); clipR = colX[n + 1]; n++ }
                        } else if (align == SheetAlign.Right) {
                            var n = k - 1; while (rt - clipL < need && free(n)) { covered += Sheet.key(r, n + 1); clipL = colX[n]; n-- }
                        } else if (align == SheetAlign.Center) {
                            var a = k - 1; var b = k + 1
                            while (clipR - clipL < need && (free(a) || free(b))) {
                                if (free(a)) { covered += Sheet.key(r, a + 1); clipL = colX[a]; a-- } else a = -1
                                if (free(b)) { covered += Sheet.key(r, b); clipR = colX[b + 1]; b++ } else b = cols
                            }
                        }
                    }
                }
                if (clipR < sx0 || clipL > sx1) continue
                words += Words(cell, st, l, rowY[r], rt, rowY[r + 1], clipL, clipR, r, k)
            }
        }

        // Fills: each cell's, then each merged range's as one.
        for (r in r0..r1) for (k in c0..c1) {
            if (sheet.merge(r, k) != null) continue
            val cell = sheet[r, k]
            val f = styleOf(r, k, cell).fill ?: continue
            fill.color = f; c.drawRect(colX[k], rowY[r], colX[k + 1], rowY[r + 1], fill)
        }
        for (g in merges) styleOf(g.r1, g.c1, sheet[g.r1, g.c1]).fill?.let { fill.color = it; c.drawRect(colX[g.c1], rowY[g.r1], colX[g.c2 + 1], rowY[g.r2 + 1], fill) }

        // The grid: line by line, none inside a merged range, under long words, or over a filled cell.
        if (sheet.grid) {
            var n = 0
            fun add(x1: Float, y1: Float, x2: Float, y2: Float) {
                if (n + 4 > lines.size) lines = lines.copyOf(lines.size * 2)
                lines[n++] = x1; lines[n++] = y1; lines[n++] = x2; lines[n++] = y2
            }
            fun filled(r: Int, k: Int) = r in 0 until rows && k in 0 until cols && (sheet.merge(r, k)?.let { styleOf(it.r1, it.c1, sheet[it.r1, it.c1]) } ?: styleOf(r, k, sheet[r, k])).fill != null
            fun same(r1: Int, k1: Int, r2: Int, k2: Int): Boolean { val a = sheet.merge(r1, k1) ?: return false; return a == sheet.merge(r2, k2) }
            for (r in r0..r1) {
                if (rowY[r + 1] - rowY[r] <= 0f) continue
                for (k in c0..c1 + 1) {
                    if (k > cols || Sheet.key(r, k) in covered || same(r, k - 1, r, k) || (filled(r, k - 1) && filled(r, k))) continue
                    add(colX[k], rowY[r], colX[k], rowY[r + 1])
                }
            }
            for (r in r0..r1 + 1) {
                if (r > rows) continue
                for (k in c0..c1) {
                    if (colX[k + 1] - colX[k] <= 0f || same(r - 1, k, r, k) || (filled(r - 1, k) && filled(r, k))) continue
                    add(colX[k], rowY[r], colX[k + 1], rowY[r])
                }
            }
            grid.color = 0xFFD9D9D9.toInt(); grid.strokeWidth = 1f / s
            c.drawLines(lines, 0, n, grid)
        }

        // Borders: each cell's four edges (a merged range's cells carry its outline).
        for (r in r0..r1) for (k in c0..c1) {
            val cell = sheet[r, k] ?: continue
            val st = styles.getOrNull(cell.style) ?: continue
            if (st.left == null && st.top == null && st.right == null && st.bottom == null) continue
            val l = colX[k]; val t = rowY[r]; val rt = colX[k + 1]; val b = rowY[r + 1]
            st.left?.let { edge(c, it, l, t, l, b, s) }; st.right?.let { edge(c, it, rt, t, rt, b, s) }
            st.top?.let { edge(c, it, l, t, rt, t, s) }; st.bottom?.let { edge(c, it, l, b, rt, b, s) }
        }

        for (wd in words) cellText(c, wd.cell, wd.st, wd.left, wd.top, wd.right, wd.bottom, wd.clipL, wd.clipR, wd.r, wd.c)
        for (g in merges) {
            val cell = sheet[g.r1, g.c1] ?: continue
            if (cell.text.isEmpty()) continue
            cellText(c, cell, styleOf(g.r1, g.c1, cell), colX[g.c1], rowY[g.r1], colX[g.c2 + 1], rowY[g.r2 + 1], colX[g.c1], colX[g.c2 + 1], g.r1, g.c1)
        }

        // The chosen cell: Excel's green frame.
        line.color = EXCEL_GREEN_ARGB; line.strokeWidth = 2.5f * density / s; line.pathEffect = null
        c.drawRect(colX[sel.c1], rowY[sel.r1], colX[min(sel.c2 + 1, cols)], rowY[min(sel.r2 + 1, rows)], line)
        c.restore()
    }

    private fun edge(c: android.graphics.Canvas, e: SheetLine, x1: Float, y1: Float, x2: Float, y2: Float, s: Float) {
        line.color = e.color
        line.pathEffect = if (e.dots) dots else if (e.dashed) dash else null
        if (e.double) {
            line.strokeWidth = max(1f, 1f / s)
            val dx = if (x1 == x2) 1f else 0f; val dy = if (y1 == y2) 1f else 0f
            c.drawLine(x1 - dx, y1 - dy, x2 - dx, y2 - dy, line); c.drawLine(x1 + dx, y1 + dy, x2 + dx, y2 + dy, line)
        } else {
            line.strokeWidth = max(e.width, 1f / s)
            c.drawLine(x1, y1, x2, y2, line)
        }
    }

    private fun align(st: SheetStyle, cell: SheetCell): SheetAlign = when (st.align) {
        SheetAlign.General -> when (cell.kind) { SheetKind.Number -> SheetAlign.Right; SheetKind.Bool, SheetKind.Error -> SheetAlign.Center; else -> SheetAlign.Left }
        else -> st.align
    }

    /** A cell's words in its box ([left]…[bottom]), clipped to [clipL]…[clipR] (wider when they run into empty cells). */
    private fun cellText(c: android.graphics.Canvas, cell: SheetCell, st: SheetStyle, left: Float, top: Float, right: Float, bottom: Float, clipL: Float, clipR: Float, r: Int, k: Int) {
        textPaint(st, cell)
        val pad = 2f + st.indent * 9f
        val align = align(st, cell)
        c.save()
        c.clipRect(clipL, top, clipR, bottom)
        if (st.wrap || st.align == SheetAlign.Justify) {
            val width = max(1f, right - left - 4f).toInt()
            val layout = wrapped.getOrPut(Sheet.key(r, k)) {
                val a = when (align) { SheetAlign.Center -> Layout.Alignment.ALIGN_CENTER; SheetAlign.Right -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
                StaticLayout.Builder.obtain(cell.text, 0, cell.text.length, TextPaint(text), width).setAlignment(a).setIncludePad(false).build()
            }
            val ty = when (st.valign) { SheetVAlign.Top -> top + 1f; SheetVAlign.Center -> (top + bottom - layout.height) / 2; SheetVAlign.Bottom -> bottom - 2f - layout.height }
            c.translate(left + 2f, ty)
            layout.draw(c)
        } else {
            var shown = cell.text.substringBefore('\n')
            var tw = text.measureText(shown)
            // A number that does not fit is ####, never cut.
            if (cell.kind == SheetKind.Number && tw > right - left - 4f) {
                val hash = text.measureText("#").coerceAtLeast(1f)
                shown = "#".repeat(max(1, ((right - left - 4f) / hash).toInt())); tw = text.measureText(shown)
            }
            val fm = text.fontMetrics
            val baseline = when (st.valign) {
                SheetVAlign.Top -> top + 1f - fm.ascent
                SheetVAlign.Center -> (top + bottom) / 2 - (fm.ascent + fm.descent) / 2
                SheetVAlign.Bottom -> bottom - 2f - fm.descent
            }
            if (st.rotation != 0) {
                val deg = if (st.rotation in 91..180) -(st.rotation - 90) else st.rotation
                c.rotate(-deg.toFloat(), (left + right) / 2, (top + bottom) / 2)
                c.drawText(shown, (left + right) / 2 - tw / 2, (top + bottom) / 2 - (fm.ascent + fm.descent) / 2, text)
            } else {
                val x = when (align) {
                    SheetAlign.Right -> right - pad - tw
                    SheetAlign.Center -> (left + right) / 2 - tw / 2
                    else -> left + pad
                }
                c.drawText(shown, x, baseline, text)
            }
        }
        c.restore()
    }

    companion object { val EXCEL_GREEN_ARGB = 0xFF107C41.toInt() }
}

