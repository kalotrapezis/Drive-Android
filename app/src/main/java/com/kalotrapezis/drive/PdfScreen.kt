package com.kalotrapezis.drive

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.ceil

/** PDF's red, as Files colours a .pdf. */
internal val PDF_RED = Color(0xFFE3685F)

/** PDFs (asked 17 Oct: a fourth Office app, a viewer — share, print and the basics, no editing). */
internal object Pdf {
    const val MAX_ZOOM = 5f
    fun isPdf(name: String) = name.endsWith(".pdf", ignoreCase = true)

    /** Page text and search: MuPDF reads them on every Android (Android's own renderer only from 15). */
    const val hasText = true

    /**
     * A pinch: the zoom times [by] about [at] (from the middle of the view) and moved by [pan], kept to 1–5× and to the
     * zoomed view's edges, so the point under the fingers stays under them.
     */
    fun zoomed(zoom: Float, offset: Offset, by: Float, at: Offset, pan: Offset, size: Size): Pair<Float, Offset> {
        val z = (zoom * by).coerceIn(1f, MAX_ZOOM)
        val k = z / zoom
        val o = at * (1 - k) + offset * k + pan
        val mx = size.width * (z - 1) / 2; val my = size.height * (z - 1) / 2
        return z to Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    /** A page's bitmap width for [widthPx] on screen at [zoom]: in half steps of zoom (not one per frame), its long edge at most 4096 px (memory). */
    fun renderWidth(widthPx: Int, zoom: Float, aspect: Float): Int {
        val w = widthPx * ceil(zoom * 2) / 2
        val cap = if (aspect >= 1f) 4096f else 4096f * aspect
        return minOf(w, cap).toInt().coerceAtLeast(1)
    }
}

/** The editor's tools that work on the page itself (Review and Draw); a finger draws while one is on. */
internal enum class PdfTool(val label: String, val icon: Int) {
    Highlight("Highlight", R.drawable.ic_highlighter), Underline("Underline", R.drawable.ic_format_underlined), Strike("Strikethrough", R.drawable.ic_format_strikethrough),
    Note("Note", R.drawable.ic_comment), Rectangle("Rectangle", R.drawable.ic_crop_square), Oval("Oval", R.drawable.ic_circle), Arrow("Arrow", R.drawable.ic_arrow_forward),
    Pen("Pen", R.drawable.ic_ink_pen), Marker("Highlighter", R.drawable.ic_ink_highlighter), Erase("Delete comment", R.drawable.ic_delete),
}

/** A word found: its page and its boxes there (in the page's points). */
internal class PdfMatch(val page: Int, val boxes: List<RectF>)

/** A PDF that needs its password: [tried] when one was given and it was wrong. */
internal class PdfPasswordException(val tried: Boolean) : Exception("password")

/**
 * One open PDF, read by MuPDF (Plan.md § PDF: it draws a file's comments — highlights, notes, ink, stamps — which Android's
 * PdfRenderer leaves out, and it edits): every use takes the lock, off the main thread; its pages' sizes; a small cache
 * of drawn pages so scrolling back is instant.
 */
internal class PdfDoc(private val file: File, password: String? = null) {
    private val doc: com.artifex.mupdf.fitz.Document = com.artifex.mupdf.fitz.Document.openDocument(file.path).also { d ->
        if (d.needsPassword() && (password == null || !d.authenticatePassword(password))) { d.destroy(); throw PdfPasswordException(password != null) }
    }
    /** The PDF itself, for editing (every change an undo step of MuPDF's own journal). */
    private val pdf: com.artifex.mupdf.fitz.PDFDocument? = doc.asPDF()?.also { p -> runCatching { p.enableJournal() }.onFailure { WriterLog.w("PDF: no undo", it) } }
    private var closed = false
    private var logged = false

    /** Each page's size in points (1/72 in), read again after a change: the list lays out every page before any is drawn. */
    var sizes: List<Size> = synchronized(this) { readSizes() }
        private set
    private fun readSizes() = (0 until doc.countPages()).map { i -> page(i) { p ->
        val b = p.bounds; Size((b.x1 - b.x0).coerceAtLeast(1f), (b.y1 - b.y0).coerceAtLeast(1f)) } }

    // —— editing (Plan.md § PDF, Phase 1: pages) ——

    val editable get() = pdf != null
    val canUndo get() = synchronized(this) { (pdf?.undoRedoPosition() ?: 0) > 0 }
    val canRedo get() = synchronized(this) { pdf?.let { it.undoRedoPosition() < it.undoRedoSteps() } == true }
    val dirty get() = synchronized(this) { pdf?.hasUnsavedChanges() == true }

    /** One change, one undo step named [what]; the pages drawn again. */
    fun edit(what: String, change: (com.artifex.mupdf.fitz.PDFDocument) -> Unit) = synchronized(this) {
        val p = pdf ?: error("This file cannot be edited.")
        WriterLog.i("PDF: $what")
        p.beginOperation(what)
        try { change(p) } finally { p.endOperation() }
        changed()
    }
    fun undo() = synchronized(this) { if (canUndo) { pdf?.undo(); changed() } }
    fun redo() = synchronized(this) { if (canRedo) { pdf?.redo(); changed() } }
    private fun changed() { cache.evictAll(); sizes = readSizes() }

    /** Pages [pages] turned by [by] degrees (90 right, -90 left). */
    fun rotate(pages: Collection<Int>, by: Int) = edit("Rotate") { p ->
        pages.forEach { i -> val o = p.findPage(i); o.put("Rotate", ((o.getInheritable("Rotate").asInteger() + by) % 360 + 360) % 360) }
    }
    fun delete(pages: Collection<Int>) = edit("Delete pages") { p -> pages.sortedDescending().forEach { p.deletePage(it) } }
    /** The pages in a new order: [order][k] is the page that goes k-th. */
    fun move(order: List<Int>) = edit("Move pages") { p -> p.rearrangePages(order.toIntArray()) }
    /** An empty page at [at], the size of the one beside it. */
    fun insertBlank(at: Int) = edit("Blank page") { p ->
        val s = sizes.getOrNull(at.coerceAtMost(sizes.lastIndex).coerceAtLeast(0)) ?: Size(595f, 842f)
        p.insertPage(at, p.addPage(com.artifex.mupdf.fitz.Rect(0f, 0f, s.width, s.height), 0, p.newDictionary(), ""))
    }
    /** Another PDF's pages at [at] (Combine): how many came. */
    fun insertFile(at: Int, other: File): Int {
        val src = com.artifex.mupdf.fitz.Document.openDocument(other.path)
        try {
            if (src.needsPassword()) error("That PDF has a password.")
            val o = src.asPDF() ?: error("That file is not a PDF.")
            val n = o.countPages()
            edit("Insert ${other.name}") { p -> repeat(n) { k -> p.graftPage(at + k, o, k) } }
            return n
        } finally { src.destroy() }
    }
    /** Pages [pages] as a PDF of their own in [to] (Extract). */
    fun extract(pages: List<Int>, to: File) = synchronized(this) {
        val p = pdf ?: error("This file cannot be edited.")
        val out = com.artifex.mupdf.fitz.PDFDocument()
        try { pages.forEachIndexed { k, i -> out.graftPage(k, p, i) }; out.save(to.path, "garbage,compress") } finally { out.destroy() }
    }

    // —— comments (Plan.md § PDF, Phase 1): points are page points from the page's top left, as [sizes] and [find] ——

    private fun rgb(c: Int) = floatArrayOf((c shr 16 and 0xFF) / 255f, (c shr 8 and 0xFF) / 255f, (c and 0xFF) / 255f)
    private fun annotate(i: Int, what: String, make: (com.artifex.mupdf.fitz.PDFPage, Float, Float) -> Boolean): Boolean {
        var made = false
        edit(what) { _ -> page(i) { p -> val o = p.bounds; made = make(p as com.artifex.mupdf.fitz.PDFPage, o.x0, o.y0); p.update() } }
        return made
    }

    /** Highlight, underline or strike out ([type]) the words from [a] to [b], as a finger went over them: false when none. */
    fun markText(i: Int, a: Offset, b: Offset, type: Int, color: Int) = annotate(i, when (type) {
        com.artifex.mupdf.fitz.PDFAnnotation.TYPE_UNDERLINE -> "Underline"; com.artifex.mupdf.fitz.PDFAnnotation.TYPE_STRIKE_OUT -> "Strikethrough"; else -> "Highlight" }) { p, x0, y0 ->
        val t = p.toStructuredText()
        try {
            val quads = t.highlight(com.artifex.mupdf.fitz.Point(a.x + x0, a.y + y0), com.artifex.mupdf.fitz.Point(b.x + x0, b.y + y0)).orEmpty()
            if (quads.isEmpty()) false else { p.createAnnotation(type).apply { setQuadPoints(quads); setColor(rgb(color)); update() }; true }
        } finally { t.destroy() }
    }

    /** A pen's strokes, [width] points wide. */
    fun ink(i: Int, strokes: List<List<Offset>>, color: Int, width: Float) = annotate(i, "Draw") { p, x0, y0 ->
        p.createAnnotation(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_INK).apply {
            setInkList(strokes.map { st -> st.map { com.artifex.mupdf.fitz.Point(it.x + x0, it.y + y0) }.toTypedArray() }.toTypedArray())
            setColor(rgb(color)); setBorderWidth(width); update()
        }; true
    }

    /** A note's icon at [at], [text] in it. */
    fun note(i: Int, at: Offset, text: String, color: Int) = annotate(i, "Note") { p, x0, y0 ->
        p.createAnnotation(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_TEXT).apply {
            setRect(com.artifex.mupdf.fitz.Rect(at.x + x0, at.y + y0, at.x + x0 + 20, at.y + y0 + 20)); setContents(text); setColor(rgb(color)); update()
        }; true
    }

    /** A rectangle, an oval ([type] square or circle) or an arrow from [a] to [b]. */
    fun shape(i: Int, type: Int, a: Offset, b: Offset, color: Int, width: Float) = annotate(i, when (type) {
        com.artifex.mupdf.fitz.PDFAnnotation.TYPE_CIRCLE -> "Oval"; com.artifex.mupdf.fitz.PDFAnnotation.TYPE_LINE -> "Arrow"; else -> "Rectangle" }) { p, x0, y0 ->
        p.createAnnotation(type).apply {
            if (type == com.artifex.mupdf.fitz.PDFAnnotation.TYPE_LINE) {
                setLine(com.artifex.mupdf.fitz.Point(a.x + x0, a.y + y0), com.artifex.mupdf.fitz.Point(b.x + x0, b.y + y0))
                setLineEndingStyles(com.artifex.mupdf.fitz.PDFAnnotation.LINE_ENDING_NONE, com.artifex.mupdf.fitz.PDFAnnotation.LINE_ENDING_OPEN_ARROW)
            } else setRect(com.artifex.mupdf.fitz.Rect(minOf(a.x, b.x) + x0, minOf(a.y, b.y) + y0, maxOf(a.x, b.x) + x0, maxOf(a.y, b.y) + y0))
            setColor(rgb(color)); setBorderWidth(width); update()
        }; true
    }

    /** The comment under [at] (the topmost, a few points round it), deleted: false when there is none. */
    fun deleteAt(i: Int, at: Offset) = annotate(i, "Delete comment") { p, x0, y0 ->
        val x = at.x + x0; val y = at.y + y0; val slop = 6f
        p.annotations.orEmpty().lastOrNull { a -> a.bounds.let { r -> x >= r.x0 - slop && x <= r.x1 + slop && y >= r.y0 - slop && y <= r.y1 + slop } }
            ?.let { p.deleteAnnotation(it); true } ?: false
    }

    /** The note under [at]: its words, to read (a note's icon only says one is there). */
    fun noteAt(i: Int, at: Offset): String? = synchronized(this) { page(i) { p ->
        val o = p.bounds; val x = at.x + o.x0; val y = at.y + o.y0
        (p as? com.artifex.mupdf.fitz.PDFPage)?.annotations.orEmpty().lastOrNull { a -> a.type == com.artifex.mupdf.fitz.PDFAnnotation.TYPE_TEXT &&
            a.bounds.let { r -> x >= r.x0 - 6 && x <= r.x1 + 6 && y >= r.y0 - 6 && y <= r.y1 + 6 } }?.contents
    } }

    /**
     * Saved over [file] (or into [to]): the changes appended to the bytes as they were opened (an incremental save, so all
     * the file had stays, signatures too), through a copy beside it renamed over it; a file MuPDF had to repair is
     * written whole. [base]: the file as opened, kept outside it for the next save.
     */
    fun save(base: File, to: File = file) = synchronized(this) {
        val p = pdf ?: error("This file cannot be edited.")
        if (!base.isFile) file.copyTo(base, overwrite = true)
        val temp = File(to.parentFile, ".${to.name}.saving")
        if (p.canBeSavedIncrementally()) { base.copyTo(temp, overwrite = true); p.save(temp.path, "incremental") }
        else p.save(temp.path, "")
        check(temp.renameTo(to)) { "Could not save the PDF." }
        WriterLog.i("PDF: saved (${to.length() / 1024} KB)")
    }

    private inline fun <T> page(i: Int, use: (com.artifex.mupdf.fitz.Page) -> T): T { val p = doc.loadPage(i); try { return use(p) } finally { p.destroy() } }

    // an eighth of the app's memory, at most 96 MB: a few screens of pages
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).coerceAtMost(96L shl 20).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(i: Int, w: Int): Bitmap? = cache.get("$i/$w")

    /** Page [i] drawn [w] px wide, its comments and form fields too; null when closed, scrolled away before its turn, or it could not be drawn (left white). */
    suspend fun page(i: Int, w: Int): Bitmap? = cached(i, w) ?: withContext(Dispatchers.IO) {
        synchronized(this@PdfDoc) {
            ensureActive() // a page flung past while it waited is not drawn
            if (closed) return@synchronized null
            cached(i, w) ?: runCatching {
                val start = System.nanoTime()
                val b = page(i) { p -> com.artifex.mupdf.fitz.android.AndroidDrawDevice.drawPageFitWidth(p, w) }
                val ms = (System.nanoTime() - start) / 1_000_000
                // the first page's time, then only slow ones (the log is not a list of every page scrolled by)
                if (!logged || ms >= 150) { logged = true; WriterLog.i("PDF: page ${i + 1} at $w px: $ms ms") }
                b.also { cache.put("$i/$w", it) }
            }.onFailure { WriterLog.w("PDF: page ${i + 1} could not be drawn", it) }.getOrNull()
        }
    }

    /** Page [i]'s words, as the file has them (none on a scan). */
    suspend fun text(i: Int): String = withContext(Dispatchers.IO) {
        synchronized(this@PdfDoc) { if (closed) "" else page(i) { p -> p.toStructuredText().let { t -> t.asText().also { t.destroy() } } } }
    }

    /** Every place [query] is, page by page (the lock let go between pages, so the shown pages still draw). */
    suspend fun find(query: String): List<PdfMatch> = withContext(Dispatchers.IO) {
        sizes.indices.flatMap { i ->
            ensureActive()
            synchronized(this@PdfDoc) {
                if (closed) emptyList() else page(i) { p ->
                    val o = p.bounds
                    p.search(query).orEmpty().map { hit -> PdfMatch(i, hit.map { q -> q.toRect().let { r -> RectF(r.x0 - o.x0, r.y0 - o.y0, r.x1 - o.x0, r.y1 - o.y0) } }) }
                }
            }
        }
    }

    /** Closed on a thread of its own: a page being drawn finishes first. */
    fun close() { thread { synchronized(this) { if (!closed) { closed = true; doc.destroy() } } } }
}

/**
 * Office's PDF viewer: the pages one under the other, a swipe going a page at a time as Slides' list, pinched to zoom
 * (and then moved with a finger; a double tap in and out), a tablet's page thumbnails at the side; Find and Copy where
 * Android reads a PDF's words; Share, Print, Open in another app. View only.
 */
@Composable
internal fun PdfScreen(back: () -> Unit, open: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(open) { Uri.parse(open).path?.let(::File) }
    RememberOpened(file) // the Office home's Recents
    var doc by remember { mutableStateOf<PdfDoc?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf<String?>(null) } // given in the password dialog
    var askPassword by remember { mutableStateOf<Boolean?>(null) } // the dialog: true when the last one was wrong
    LaunchedEffect(open, password) {
        WriterLog.init(context)
        val f = file ?: run { failed = "This file cannot be opened."; return@LaunchedEffect }
        WriterLog.i("PDF: open ${f.length() / 1024} KB")
        withContext(Dispatchers.IO) { runCatching { WriterLog.timed("PDF: read") { PdfDoc(f, password) } } }
            .onSuccess { doc = it; askPassword = null; WriterLog.i("PDF: ${it.sizes.size} pages") }
            .onFailure {
                if (it is PdfPasswordException) { askPassword = it.tried; return@onFailure }
                WriterLog.e("PDF: could not open", it)
                failed = "This PDF could not be read. The file may be damaged, or not a PDF."
            }
    }
    askPassword?.let { wrong ->
        var typed by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(onDismissRequest = { askPassword = null; back() },
            title = { Text("This PDF has a password") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wrong) Text("That password is not this file's.", color = MaterialTheme.colorScheme.error)
                androidx.compose.material3.OutlinedTextField(typed, { typed = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { askPassword = null; password = typed }))
            } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { askPassword = null; password = typed }) { Text("Open") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { askPassword = null; back() }) { Text("Cancel") } })
    }
    DisposableEffect(doc) { val d = doc; onDispose { d?.close() } } // this one: reading doc when it goes would close the next one
    BackHandler { back() }

    val list = rememberLazyListState()
    // the page in the middle of the screen, where a swipe leaves it; at either end the first or last (short pages, as beside a
    // phone's page list, never reach the middle there)
    val at by remember { derivedStateOf { list.layoutInfo.let { l -> val mid = (l.viewportStartOffset + l.viewportEndOffset) / 2
        if (!list.canScrollBackward) 0 else if (!list.canScrollForward) (l.totalItemsCount - 1).coerceAtLeast(0)
        else l.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - mid) }?.index ?: 0 } } }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    // the zoom a pinch ended at: the page in the middle drawn again that sharp
    var settled by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(zoom) { delay(250); settled = zoom }
    fun goTo(page: Int) { zoom = 1f; pan = Offset.Zero; scope.launch { list.scrollToItem(page) } }

    var finding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<PdfMatch>?>(null) }
    var match by remember { mutableStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    fun find() {
        val d = doc ?: return
        if (query.isBlank() || !Pdf.hasText) return
        searching = true
        scope.launch {
            val start = System.nanoTime()
            val found = runCatching { d.find(query.trim()) }.onFailure { WriterLog.e("PDF: find", it) }.getOrDefault(emptyList())
            WriterLog.i("PDF: find, ${found.size} found in ${(System.nanoTime() - start) / 1_000_000} ms") // never what was looked for
            matches = found; match = 0; searching = false
            found.firstOrNull()?.let { goTo(it.page) }
        }
    }
    fun step(by: Int) { val m = matches ?: return; if (m.isEmpty()) return; match = (match + by + m.size) % m.size; goTo(m[match].page) }
    fun copyPage() {
        val d = doc ?: return
        if (!Pdf.hasText) return
        val page = at
        scope.launch {
            val text = runCatching { d.text(page) }.onFailure { WriterLog.e("PDF: copy", it) }.getOrDefault("")
            WriterLog.i("PDF: page ${page + 1}'s text copied (${text.length} letters)")
            if (text.isBlank()) Toast.makeText(context, "Page ${page + 1} has no text: a scan is a picture.", Toast.LENGTH_LONG).show()
            else context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Page ${page + 1}", text))
        }
    }
    fun print() {
        val f = file ?: return
        runCatching { context.getSystemService(android.print.PrintManager::class.java)?.print(f.nameWithoutExtension, PdfPrintAdapter(f, f.nameWithoutExtension), null) }
            .onSuccess { WriterLog.i("PDF: print") }.onFailure { WriterLog.e("PDF: print", it) }
    }

    // —— editing (Plan.md § PDF): opened view only, Edit as Words' ——
    var editing by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("Pages") }
    var tool by remember { mutableStateOf<PdfTool?>(null) }
    var markColour by remember { mutableStateOf(0xFFEB3B) } // Review's colour (highlight, shapes, notes)
    var penColour by remember { mutableStateOf(0xE53935) } // Draw's
    var penWidth by remember { mutableStateOf(2f) }
    var writing by remember { mutableStateOf<Pair<Int, Offset>?>(null) } // a note being written: its page and place
    var reading by remember { mutableStateOf<String?>(null) } // a note tapped: its words
    var version by remember { mutableStateOf(0) } // after each change: the pages drawn again
    var organize by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) } // Back with changes not saved: Save or not
    var showVersions by remember { mutableStateOf(false) }
    val base = remember(file) { file?.let { File(File(context.cacheDir, "pdf-base").apply { mkdirs() }, "${it.absolutePath.hashCode().toUInt()}.pdf") } }
    fun change(what: () -> Unit) {
        runCatching(what).onFailure { WriterLog.e("PDF: change", it); Toast.makeText(context, it.message ?: "That could not be done.", Toast.LENGTH_LONG).show() }
        version++
    }
    fun save(then: () -> Unit = {}) {
        val d = doc ?: return; val f = file ?: return; val b = base ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { keepPrevious(context, f); d.save(b) } }
            r.onFailure { WriterLog.e("PDF: save", it); Toast.makeText(context, "Could not save: ${it.message}", Toast.LENGTH_LONG).show() }
                .onSuccess { version++; Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show(); then() }
        }
    }
    // Pages › Insert PDF (Combine): another PDF's pages after the one shown
    val pickPdf = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val d = doc ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        val at0 = at
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching {
                val tmp = File(context.cacheDir, "pdf-insert.pdf")
                context.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                d.insertFile(at0 + 1, tmp).also { tmp.delete() }
            } }
            r.onSuccess { n -> version++; Toast.makeText(context, "$n page${if (n == 1) "" else "s"} put in after page ${at0 + 1}", Toast.LENGTH_SHORT).show() }
                .onFailure { WriterLog.e("PDF: insert", it); Toast.makeText(context, it.message ?: "Could not put that PDF in.", Toast.LENGTH_LONG).show() }
        }
    }
    BackHandler(enabled = editing || organize) { if (organize) organize = false else if (tool != null) tool = null else if (doc?.dirty == true) leaving = true else editing = false }
    /** What a finger did on page [i] with the tool on: [pts] its path in page points (a tap: one point). */
    fun onPage(i: Int, pts: List<Offset>) {
        val d = doc ?: return; val t = tool ?: run {
            // no tool: a note tapped shows its words
            if (pts.size == 1) d.noteAt(i, pts[0])?.let { reading = it.ifBlank { "(an empty note)" } }; return }
        val a = pts.first(); val b = pts.last()
        when (t) {
            PdfTool.Highlight, PdfTool.Underline, PdfTool.Strike -> change {
                val type = when (t) { PdfTool.Underline -> com.artifex.mupdf.fitz.PDFAnnotation.TYPE_UNDERLINE; PdfTool.Strike -> com.artifex.mupdf.fitz.PDFAnnotation.TYPE_STRIKE_OUT
                    else -> com.artifex.mupdf.fitz.PDFAnnotation.TYPE_HIGHLIGHT }
                if (!d.markText(i, a, b, type, if (t == PdfTool.Highlight) markColour else if (markColour == 0xFFEB3B) 0xE53935 else markColour))
                    Toast.makeText(context, "No words there: a scan is a picture. Draw on it with Draw's pen.", Toast.LENGTH_SHORT).show()
            }
            PdfTool.Note -> writing = i to a
            PdfTool.Rectangle, PdfTool.Oval, PdfTool.Arrow -> if ((b - a).getDistance() > 4f) change {
                d.shape(i, when (t) { PdfTool.Oval -> com.artifex.mupdf.fitz.PDFAnnotation.TYPE_CIRCLE; PdfTool.Arrow -> com.artifex.mupdf.fitz.PDFAnnotation.TYPE_LINE
                    else -> com.artifex.mupdf.fitz.PDFAnnotation.TYPE_SQUARE }, a, b, if (markColour == 0xFFEB3B) 0xE53935 else markColour, 2f)
            }
            PdfTool.Pen -> if (pts.size > 1) change { d.ink(i, listOf(pts), penColour, penWidth) }
            PdfTool.Marker -> if (pts.size > 1) change { d.ink(i, listOf(pts), 0xFFEB3B, 12f) }
            PdfTool.Erase -> change { if (!d.deleteAt(i, a)) Toast.makeText(context, "No comment there", Toast.LENGTH_SHORT).show() }
        }
    }

    val wide = LocalConfiguration.current.screenWidthDp >= 700
    var pages by remember { mutableStateOf(false) } // a phone's list of pages, brought out from the page label (asked 17 Oct)
    // Pages › Organize: the scanner's grid (ReorderGrid), long-press and drag; Done puts the pages in that order, one undo step
    val d1 = doc
    if (organize && d1 != null) {
        var order by remember { mutableStateOf(d1.sizes.indices.toList()) }
        val density = LocalDensity.current
        ReorderGrid(order.map { "p$it" }, { from, to -> order = order.toMutableList().apply { add(to, removeAt(from)) } },
            open = { k -> organize = false; scope.launch { delay(50); goTo(k) } }, back = { organize = false }, title = "Organize",
            actions = { RibbonButton("Done", MaterialTheme.colorScheme.onBackground, R.drawable.ic_check) {
                if (order != d1.sizes.indices.toList()) change { d1.move(order) }
                organize = false
            } }) { k, m -> PdfPage(d1, order[k], with(density) { 160.dp.roundToPx() }, m.background(Color.White)) }
        return
    }
    leaving.takeIf { it }?.let {
        androidx.compose.material3.AlertDialog(onDismissRequest = { leaving = false }, title = { Text("Save the changes?") },
            text = { Text("The file as it was stays in Versions.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { leaving = false; save { editing = false } }) { Text("Save") } },
            dismissButton = { Row {
                androidx.compose.material3.TextButton(onClick = {
                    leaving = false; editing = false
                    // not saved: the file read again as it is
                    val f = file; doc?.close(); doc = null; base?.delete()
                    if (f != null) scope.launch { withContext(Dispatchers.IO) { runCatching { PdfDoc(f, password) } }.onSuccess { doc = it; version++ } }
                }) { Text("Don't save") }
                androidx.compose.material3.TextButton(onClick = { leaving = false }) { Text("Cancel") }
            } })
    }
    writing?.let { (i, at0) ->
        var words by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(onDismissRequest = { writing = null }, title = { Text("Note") },
            text = { androidx.compose.material3.OutlinedTextField(words, { words = it }, Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("What the note says") }) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { writing = null; doc?.let { d -> change { d.note(i, at0, words.trim(), markColour) } } }) { Text("Add") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { writing = null }) { Text("Cancel") } })
    }
    reading?.let { words ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { reading = null }, title = { Text("Note") }, text = { Text(words) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { reading = null }) { Text("Close") } })
    }
    if (showVersions) file?.let { f -> VersionsDialog(f, "", { showVersions = false }, about = "The file as it was before a save. Tapping one puts it back at once; the file as it is now goes into this list.") { v ->
        showVersions = false
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { keepPrevious(context, f); doc?.close(); v.copyTo(f, overwrite = true); base?.delete() } }
            withContext(Dispatchers.IO) { runCatching { PdfDoc(f, password) } }.onSuccess { doc = it; version++ }
            Toast.makeText(context, "That version is back", Toast.LENGTH_SHORT).show()
        }
    } }
    Column(Modifier.fillMaxSize().background(Color(0xFF2B2B2B))) {
        Surface(color = islandColor(), contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(file?.name ?: "PDF", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        doc?.let { Text("${it.sizes.size} page${if (it.sizes.size == 1) "" else "s"} · ${if (editing) if (version >= 0 && it.dirty) "Not saved" else "Editing" else "View only"}", style = MaterialTheme.typography.labelMedium, color = islandContentColor().copy(alpha = 0.7f)) }
                    }
                    val d0 = doc
                    if (editing && d0 != null) {
                        key(version) {
                            Tool(R.drawable.ic_undo, "Undo", islandContentColor(), enabled = d0.canUndo) { change { d0.undo() } }
                            Tool(R.drawable.ic_redo, "Redo", islandContentColor(), enabled = d0.canRedo) { change { d0.redo() } }
                            Tool(R.drawable.ic_save, "Save", islandContentColor(), enabled = d0.dirty) { save() }
                        }
                        Tool(R.drawable.ic_check, "Done", islandContentColor()) { if (d0.dirty) leaving = true else editing = false }
                    } else {
                    if (d0?.editable == true) RibbonButton("Edit", islandContentColor(), R.drawable.ic_edit) { editing = true; WriterLog.i("PDF: edit") }
                    if (Pdf.hasText && doc != null) IconButton(onClick = { finding = !finding; if (!finding) matches = null }) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = "Find", tint = if (finding) PDF_RED else islandContentColor()) }
                    FavouriteHeart(file, islandContentColor()) // a tablet or bigger: the Office home's Favourites (the user, 18 Oct)
                    val fav = rememberOfficeFavourite(file)?.takeIf { !favouriteInTopBar() } // a phone: in ⋯
                    file?.let { f ->
                        IconButton(onClick = { shareDriveFile(context, f) }) { Icon(painterResource(R.drawable.ic_share), contentDescription = "Share") }
                        IconButton(onClick = ::print, enabled = doc != null) { Icon(painterResource(R.drawable.ic_print), contentDescription = "Print") }
                        // the rest behind ⋯, so a phone's bar keeps room for the name
                        var more by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { more = true }) { Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More") }
                            DropdownMenu(more, { more = false }) {
                                if (Pdf.hasText && doc != null) DropdownMenuItem({ Text("Copy this page's text") }, { more = false; copyPage() },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_copy), contentDescription = null) })
                                DropdownMenuItem({ Text("Open in another app") }, { more = false; openElsewhere(context, f) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_open_with), contentDescription = null) })
                                fav?.let { h -> DropdownMenuItem({ Text(if (h.on) "Remove from favourites" else "Add to favourites") }, { more = false; h.toggle() },
                                    leadingIcon = { Icon(painterResource(if (h.on) R.drawable.ic_favorite else R.drawable.ic_favorite_border), contentDescription = null) }) }
                                if (versions(context, f).isNotEmpty()) DropdownMenuItem({ Text("Versions") }, { more = false; showVersions = true },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_history), contentDescription = null) })
                            }
                        }
                    }
                    }
                }
                if (finding) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    Row(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(query, { query = it; matches = null }, Modifier.weight(1f).focusRequester(focus), singleLine = true, placeholder = { Text("Find in the PDF") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { find() }),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF3A3A3A), unfocusedContainerColor = Color(0xFF3A3A3A)))
                        Text(when { searching -> "Looking…"; matches == null -> ""; matches!!.isEmpty() -> "None"; else -> "${match + 1} of ${matches!!.size}" },
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp))
                        IconButton(onClick = { step(-1) }, enabled = !matches.isNullOrEmpty()) { Icon(painterResource(R.drawable.ic_keyboard_arrow_up), contentDescription = "Previous") }
                        IconButton(onClick = { if (matches == null) find() else step(1) }, enabled = query.isNotBlank()) { Icon(painterResource(R.drawable.ic_keyboard_arrow_down), contentDescription = "Next") }
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val d = doc
            when {
                failed != null -> Text(failed!!, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                d == null -> Text("Opening…", color = Color.LightGray, modifier = Modifier.align(Alignment.Center))
                d.sizes.isEmpty() -> Text("This PDF has no pages.", color = Color.White, modifier = Modifier.align(Alignment.Center))
                else -> key(version) { Row(Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    // a tablet: the pages small at the side, the one shown marked, tapped to go to it; a phone: the same, narrower, when asked for
                    if (wide || pages) {
                        val side = if (wide) 150.dp else 110.dp
                        val thumbs = rememberLazyListState()
                        LaunchedEffect(at) { if (thumbs.layoutInfo.visibleItemsInfo.none { it.index == at && it.offset >= 0 }) thumbs.animateScrollToItem((at - 1).coerceAtLeast(0)) }
                        LazyColumn(Modifier.width(side).fillMaxHeight().background(Color(0xFF1F1F1F)), state = thumbs, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(d.sizes.size) { i ->
                                Column(Modifier.fillMaxWidth().clickable { goTo(i); if (!wide) pages = false; WriterLog.i("PDF: thumbnail ${i + 1}") }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    PdfPage(d, i, with(density) { (side - 24.dp).roundToPx() }, Modifier.fillMaxWidth().then(if (i == at) Modifier.border(2.dp, PDF_RED) else Modifier))
                                    Text("${i + 1}", color = if (i == at) PDF_RED else Color.LightGray, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clipToBounds().pointerInput(Unit) { // zoomed: kept off the bar and the thumbnails
                        // a pinch zooms (two fingers, before the list scrolls); zoomed in, one finger moves about the page
                        val view = Size(size.width.toFloat(), size.height.toFloat())
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                if (ev.changes.count { it.pressed } >= 2 || (zoom > 1f && tool == null)) {
                                    val by = ev.calculateZoom(); val moved = ev.calculatePan()
                                    val c = ev.calculateCentroid(useCurrent = true).takeIf { it.isSpecified } ?: Offset(view.width / 2, view.height / 2)
                                    val (z, o) = Pdf.zoomed(zoom, pan, by, c - Offset(view.width / 2, view.height / 2), moved, view)
                                    zoom = z; pan = o
                                    // a still tap is left alone, for the double tap
                                    if (by != 1f || moved != Offset.Zero) ev.changes.forEach { it.consume() }
                                }
                            } while (ev.changes.any { it.pressed })
                        }
                    }.pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { p ->
                            val view = Size(size.width.toFloat(), size.height.toFloat())
                            if (zoom > 1f) { zoom = 1f; pan = Offset.Zero }
                            else Pdf.zoomed(1f, Offset.Zero, 2.5f, p - Offset(view.width / 2, view.height / 2), Offset.Zero, view).let { (z, o) -> zoom = z; pan = o }
                        })
                    }) {
                        // page by page, as Slides' list: a swipe ends with the next or the one before in the middle, however hard it is flung
                        val oneAtATime = remember(list) {
                            val base = androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider(list, androidx.compose.foundation.gestures.snapping.SnapPosition.Center)
                            object : androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider by base {
                                override fun calculateApproachOffset(velocity: Float, decayOffset: Float) = 0f
                            }
                        }
                        val roomW = maxWidth - 24.dp; val roomH = maxHeight - 24.dp
                        LazyColumn(Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }, state = list,
                            userScrollEnabled = zoom == 1f && tool == null, flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(oneAtATime),
                            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(d.sizes.size) { i ->
                                // the whole page in view: as wide as there is room, no taller than the screen
                                val s = d.sizes[i]
                                val w = minOf(roomW, roomH * (s.width / s.height))
                                val px = with(density) { w.roundToPx() }
                                val marks = matches?.filter { it.page == i }.orEmpty()
                                PdfPage(d, i, if (i == at) Pdf.renderWidth(px, settled, s.width / s.height) else px,
                                    Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).width(w), marks, matches?.getOrNull(match)?.takeIf { it.page == i }) {
                                    if (editing) PdfDrawLayer(s, tool, if (tool == PdfTool.Pen) penColour else if (tool == PdfTool.Marker) 0xFFEB3B else markColour) { pts -> onPage(i, pts) }
                                }
                            }
                        }
                    }
                } }
            }
            // on a phone the label is also the button for the list of pages
            if (d != null && d.sizes.isNotEmpty()) Surface(color = Color.Black.copy(alpha = 0.75f), contentColor = if (pages) PDF_RED else Color.White, shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomCenter).bottomBarPadding().padding(bottom = 16.dp).then(if (wide) Modifier else Modifier.clickable { val keep = at; pages = !pages; scope.launch { list.scrollToItem(keep) } })) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!wide) Icon(painterResource(R.drawable.ic_view_grid), contentDescription = if (pages) "Hide the pages" else "Show the pages", modifier = Modifier.padding(end = 8.dp).size(18.dp))
                    Text("Page ${(at + 1).coerceAtMost(d.sizes.size)} of ${d.sizes.size}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        // the ribbon while editing, at the bottom as Slides' (a tablet: the floating island; a phone: across the bottom)
        val d = doc
        if (editing && d != null) {
            val w = Color.White
            Surface(color = Color.Black, contentColor = w, modifier = if (wide) Modifier.fillMaxWidth().bottomBarPadding().padding(12.dp).clip(MaterialTheme.shapes.extraLarge) else Modifier.fillMaxWidth()) {
                OfficeStripFrame(w, if (wide) Modifier.padding(vertical = 4.dp) else Modifier.bottomBarPadding()) { Column {
                    OfficeTabRow(listOf("Pages", "Review", "Draw"), tab, w, PDF_RED, { tab = it; tool = null })
                    @Composable fun ToolButton(t: PdfTool) = RibbonButton(t.label, w, t.icon, on = tool == t) { tool = if (tool == t) null else t }
                    key(tab) { RibbonLine(androidx.compose.foundation.rememberScrollState(), Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                        if (tab == "Review") {
                            ToolButton(PdfTool.Highlight); ToolButton(PdfTool.Underline); ToolButton(PdfTool.Strike)
                            RibbonDivider(w)
                            ToolButton(PdfTool.Note); ToolButton(PdfTool.Rectangle); ToolButton(PdfTool.Oval); ToolButton(PdfTool.Arrow)
                            Swatches(w, R.drawable.ic_palette, "Colour", listOf(0xFFEB3B, 0x92D050, 0x00B0F0, 0xE53935, 0x7030A0, 0x000000).map { Color(0xFF000000.toInt() or it) }) { c ->
                                c?.let { markColour = (it.toArgb() and 0xFFFFFF) } }
                            RibbonDivider(w)
                            ToolButton(PdfTool.Erase)
                            return@RibbonLine
                        }
                        if (tab == "Draw") {
                            ToolButton(PdfTool.Pen); ToolButton(PdfTool.Marker)
                            Swatches(w, R.drawable.ic_palette, "Pen colour", listOf(0x000000, 0xE53935, 0x0070C0, 0x00B050, 0x7030A0, 0xFFC000).map { Color(0xFF000000.toInt() or it) }) { c ->
                                c?.let { penColour = (it.toArgb() and 0xFFFFFF) } }
                            RibbonDivider(w)
                            listOf(1f to "Thin", 2f to "Medium", 4f to "Thick").forEach { (pt, label) -> RibbonButton(label, w, on = penWidth == pt) { penWidth = pt } }
                            RibbonDivider(w)
                            ToolButton(PdfTool.Erase)
                            return@RibbonLine
                        }
                        val page = at
                        RibbonButton("Organize", w, R.drawable.ic_view_grid) { organize = true }
                        RibbonDivider(w)
                        RibbonButton("Rotate left", w, R.drawable.ic_rotate_left) { change { d.rotate(listOf(page), -90) } }
                        RibbonButton("Rotate right", w, R.drawable.ic_rotate_right) { change { d.rotate(listOf(page), 90) } }
                        RibbonButton("Delete page", w, R.drawable.ic_delete, enabled = d.sizes.size > 1) { change { d.delete(listOf(page)) } }
                        RibbonDivider(w)
                        RibbonButton("Blank page", w, R.drawable.ic_note_add) { change { d.insertBlank(page + 1) }; scope.launch { delay(100); goTo(page + 1) } }
                        RibbonButton("Insert PDF", w, R.drawable.ic_add_to_collection) { pickPdf.launch(arrayOf("application/pdf")) }
                        RibbonButton("Extract page", w, R.drawable.ic_copy) {
                            val f = file ?: return@RibbonButton
                            scope.launch {
                                val to = generateSequence(1) { it }.let { _ -> var n = 0; var t: File; do { n++; t = File(f.parentFile, "${f.nameWithoutExtension} (page ${page + 1}${if (n > 1) " $n" else ""}).pdf") } while (t.exists()); t }
                                withContext(Dispatchers.IO) { runCatching { d.extract(listOf(page), to) } }
                                    .onSuccess { Toast.makeText(context, "Saved as ${to.name}", Toast.LENGTH_LONG).show() }
                                    .onFailure { WriterLog.e("PDF: extract", it); Toast.makeText(context, it.message ?: "Could not extract the page.", Toast.LENGTH_LONG).show() }
                            }
                        }
                    } }
                } }
            }
        }
    }
}

/** Page [i], drawn [widthPx] wide off the main thread (the sharper one in when it is ready); words found marked on it, the current one stronger. */
@Composable
private fun PdfPage(doc: PdfDoc, i: Int, widthPx: Int, modifier: Modifier, marks: List<PdfMatch> = emptyList(), current: PdfMatch? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {}) {
    val s = doc.sizes[i]
    var bitmap by remember(i) { mutableStateOf(doc.cached(i, widthPx)) }
    LaunchedEffect(i, widthPx) { doc.page(i, widthPx)?.let { bitmap = it } }
    Box(modifier.aspectRatio(s.width / s.height).background(Color.White)) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Page ${i + 1}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        if (marks.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
            val k = size.width / s.width
            for (m in marks) for (r in m.boxes) drawRect(if (m === current) Color(0x88FF8A00) else Color(0x66FFEB3B), Offset(r.left * k, r.top * k), Size(r.width() * k, r.height() * k))
        }
        content()
    }
}

/**
 * The editor's layer over a page: a finger's path while a tool is on, drawn as it goes (the pen's line, a shape's outline,
 * the stretch a highlight will cover), then handed over in the page's points; with no tool, a tap (to read a note).
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PdfDrawLayer(page: Size, tool: PdfTool?, colour: Int, done: (List<Offset>) -> Unit) {
    var path by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val c = Color(0xFF000000.toInt() or colour)
    Canvas(Modifier.matchParentSize().pointerInput(tool) {
        val k = size.width / page.width
        if (tool == null || tool == PdfTool.Note || tool == PdfTool.Erase) detectTapGestures { p -> done(listOf(p / k)) }
        else detectDragGestures(
            onDragStart = { p -> path = listOf(p) },
            onDrag = { ch, _ -> ch.consume(); path = path + ch.position },
            onDragEnd = { val pts = path.map { it / k }; path = emptyList(); done(pts) },
            onDragCancel = { path = emptyList() })
    }) {
        if (path.size < 2) return@Canvas
        val a = path.first(); val b = path.last()
        when (tool) {
            PdfTool.Pen, PdfTool.Marker -> drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(a.x, a.y); path.drop(1).forEach { lineTo(it.x, it.y) } },
                c.copy(alpha = if (tool == PdfTool.Marker) 0.4f else 1f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (tool == PdfTool.Marker) 24f else 5f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            PdfTool.Rectangle -> drawRect(c, Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
            PdfTool.Oval -> drawOval(c, Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
            PdfTool.Arrow -> drawLine(c, a, b, 4f)
            else -> drawLine(c.copy(alpha = 0.5f), a, b, 18f) // the words a highlight will cover, roughly
        }
    }
}
