package com.kalotrapezis.drive

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
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

    /** A new PDF in [dir] (asked 24 Oct: "a blank page so we can create a PDF"): one empty A4 portrait page, "New PDF (n).pdf". */
    fun newBlank(dir: File): File {
        dir.mkdirs()
        val f = generateSequence(1) { it + 1 }.map { File(dir, if (it == 1) "New PDF.pdf" else "New PDF ($it).pdf") }.first { !it.exists() }
        val out = com.artifex.mupdf.fitz.PDFDocument()
        try {
            out.insertPage(0, out.addPage(com.artifex.mupdf.fitz.Rect(0f, 0f, 595f, 842f), 0, out.newDictionary(), ""))
            out.save(f.path, "compress")
        } finally { out.destroy() }
        return f
    }

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

/** A comment on a page, as the Comments pane lists it: its kind, words, place (page points) and [index] among the page's. */
/** Find & redact's ready patterns (the user, 22 Oct: "text redaction in data level"): what people most often black out. */
internal val REDACT_PATTERNS = listOf(
    R.string.pdf_redact_phones to Regex("""(?<!\d)(?:\+30[\s-]?)?(?:2\d|69)\d(?:[\s-]?\d){7}(?!\d)"""),
    R.string.pdf_redact_emails to Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+"""),
    R.string.pdf_redact_afm to Regex("""(?<!\d)\d{9}(?!\d)"""),
    R.string.pdf_redact_amka to Regex("""(?<!\d)\d{11}(?!\d)"""),
    R.string.pdf_redact_iban to Regex("""\b[A-Z]{2}\d{2}(?:\s?[A-Z0-9]{4}){3,7}(?:\s?[A-Z0-9]{1,4})?\b"""),
)

internal class PdfComment(val page: Int, val index: Int, @androidx.annotation.StringRes val kind: Int, val words: String, val at: Offset, val replyTo: Int? = null, val author: String = "")

/** A bookmark (the file's contents): its title, page, depth. */
internal class PdfMark(val title: String, val page: Int, val depth: Int)

/** A list's marker at a line's start (Edit in Words): a bullet, or a number or letter with . or ). */
internal val LIST_BULLET = Regex("""^[•◦▪▫●○■□‣⁃∙·\-–—*\uF0B7\uF0A7]\s*""")
internal val LIST_NUMBER = Regex("""^(\d{1,3}|[a-zα-ω]|[ivx]{1,4})[.)](\s+|$)""", RegexOption.IGNORE_CASE)
internal fun isMarkerOnly(t: String) = t.isNotBlank() && (LIST_BULLET.replace(t.trim(), "").isEmpty() || LIST_NUMBER.replace(t.trim(), "").isEmpty())

/** A piece of a PDF's line between wide gaps (Edit in Words): a table's cell when it lines up with others, else words. */
internal class PdfSeg(val text: String, val x0: Float, val x1: Float, val y: Float, val size: Float, val bold: Boolean, val italic: Boolean, val color: Int)

/** A form's field tapped: what it is ("text", "check", "radio", "choice", other), its name, value and choices. */
internal class PdfField(val kind: String, val name: String, val value: String, val options: List<String>, val multiline: Boolean, val readOnly: Boolean)

/** The editor's tools that work on the page itself (Review and Draw); a finger draws while one is on. */
internal enum class PdfTool(@androidx.annotation.StringRes val label: Int, val icon: Int) {
    Highlight(R.string.words_highlight, R.drawable.ic_highlighter), Underline(R.string.words_underline, R.drawable.ic_format_underlined), Strike(R.string.words_strikethrough, R.drawable.ic_format_strikethrough),
    Note(R.string.pdf_note, R.drawable.ic_comment), Rectangle(R.string.pdf_rectangle, R.drawable.ic_crop_square), Oval(R.string.pdf_oval, R.drawable.ic_circle), Arrow(R.string.pdf_arrow, R.drawable.ic_arrow_forward),
    Pen(R.string.words_pen, R.drawable.ic_ink_pen), Marker(R.string.words_highlighter, R.drawable.ic_ink_highlighter), Erase(R.string.words_delete_comment, R.drawable.ic_delete),
    Text(R.string.pdf_text, R.drawable.ic_text_fields), Tick(R.string.pdf_tick, R.drawable.ic_check), Cross(R.string.pdf_cross, R.drawable.ic_close), Dot(R.string.pdf_dot, R.drawable.ic_circle),
    Date(R.string.words_date, R.drawable.ic_calendar_today), Sign(R.string.pdf_sign, R.drawable.ic_draw), Redact(R.string.pdf_mark_area, R.drawable.ic_crop_square),
    TextBox(R.string.words_text_box, R.drawable.ic_text_fields), Stamp(R.string.pdf_stamp, R.drawable.ic_branding_watermark), RedactText(R.string.pdf_select_text, R.drawable.ic_format_strikethrough),
    TextField(R.string.words_text_field, R.drawable.ic_crop_square), CheckBox(R.string.words_cc_check_box, R.drawable.ic_check_box_outline_blank),
}

/** PDF's standard stamps, as every reader names and draws them. */
internal const val PICTURE_STAMP = "Picture"
/** Tetra's key on a text it put on ([PdfDoc.putText]): its words and look, to be typed in again. */
private const val TYPED_KEY = "TetraText"
internal val STAMPS = listOf("Approved", "Draft", "Final", "Confidential", "Experimental", "Expired", "NotApproved", "ForComment", "AsIs", "Sold",
    "Departmental", "NotForPublicRelease", "TopSecret", "ForPublicRelease")

/** The PDF app's menus: white words and icons on the black menu. */
@Composable internal fun PDF_MENU_ITEM() = androidx.compose.material3.MenuDefaults.itemColors(textColor = Color.White, leadingIconColor = Color.White, trailingIconColor = Color.White)

/** A word found: its page and its boxes there (in the page's points). */
internal class PdfMatch(val page: Int, val boxes: List<RectF>)

/** A PDF that needs its password: [tried] when one was given and it was wrong. */
internal class PdfPasswordException(val tried: Boolean) : Exception("password")

/** A failure of ours, its words for the user in [res]: what [pdfSays] shows. */
internal class PdfError(@androidx.annotation.StringRes val res: Int) : Exception()
/** What to tell the user about [t]: our own words, else what the library said, else [fallback]. */
internal fun pdfSays(context: android.content.Context, t: Throwable, @androidx.annotation.StringRes fallback: Int): String =
    (t as? PdfError)?.let { context.getString(it.res) } ?: t.message ?: context.getString(fallback)

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
    // MuPDF's undo cannot take off what a save wrote (it says it can, and changes nothing; changes after the save undo
    // and redo as before, 27 Oct): Undo goes back to the last save, Redo only to what was undone since
    private var savedAt = 0
    private var redoStale = false
    val canUndo get() = synchronized(this) { (pdf?.undoRedoPosition() ?: 0) > savedAt }
    val canRedo get() = synchronized(this) { !redoStale && pdf?.let { it.undoRedoPosition() < it.undoRedoSteps() } == true }
    val dirty get() = synchronized(this) { pdf?.hasUnsavedChanges() == true || rewrite != null || copied }
    /** Saved only as a copy (File › Save a copy) since the last change: the file itself still is not. */
    private var copied = false
    /** The document's own details (Document.META_INFO_TITLE…), null when it has none. */
    fun info(key: String): String? = synchronized(this) { runCatching { doc.getMetaData(key) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() } }

    /** One change, one undo step named [what]; the pages drawn again. */
    fun edit(what: String, change: (com.artifex.mupdf.fitz.PDFDocument) -> Unit) = synchronized(this) {
        val p = pdf ?: throw PdfError(R.string.pdf_cannot_edit)
        WriterLog.i("PDF: $what")
        p.beginOperation(what)
        try { change(p) } finally { p.endOperation() }
        redoStale = false // what could be redone is gone with a new change
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
    /**
     * Every page made [to] its size as shown (Layout › Size and Orientation, as Word's: the whole document), its top left
     * where it was so what is on it stays put; its crop the same box (the old one would cut the new page).
     */
    fun resize(to: (Size) -> Size) = edit("Page size") { p ->
        sizes.forEachIndexed { i, now ->
            val want = to(now); if (want == now) return@forEachIndexed
            val o = p.findPage(i); val mb = o.getInheritable("MediaBox")
            val x0 = mb.get(0).asFloat(); val y1 = mb.get(3).asFloat()
            val turned = Math.floorMod(o.getInheritable("Rotate").asInteger(), 180) == 90 // shown turned: the box the other way round
            val (w, h) = if (turned) want.height to want.width else want.width to want.height
            val box = com.artifex.mupdf.fitz.Rect(x0, y1 - h, x0 + w, y1)
            o.put("MediaBox", box); o.put("CropBox", box); listOf("TrimBox", "BleedBox", "ArtBox").forEach { o.delete(it) }
        }
    }
    /** Another PDF's pages at [at] (Combine): how many came. */
    fun insertFile(at: Int, other: File): Int {
        val src = com.artifex.mupdf.fitz.Document.openDocument(other.path)
        try {
            if (src.needsPassword()) throw PdfError(R.string.pdf_insert_has_password)
            val o = src.asPDF() ?: throw PdfError(R.string.pdf_not_a_pdf)
            val n = o.countPages()
            edit("Insert ${other.name}") { p -> repeat(n) { k -> p.graftPage(at + k, o, k) } }
            return n
        } finally { src.destroy() }
    }
    /** Pages [pages] as a PDF of their own in [to] (Extract). */
    fun extract(pages: List<Int>, to: File) = synchronized(this) {
        val p = pdf ?: throw PdfError(R.string.pdf_cannot_edit)
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
        com.artifex.mupdf.fitz.PDFAnnotation.TYPE_UNDERLINE -> "Underline"; com.artifex.mupdf.fitz.PDFAnnotation.TYPE_STRIKE_OUT -> "Strikethrough"; com.artifex.mupdf.fitz.PDFAnnotation.TYPE_REDACT -> "Mark for redaction"; else -> "Highlight" }) { p, x0, y0 ->
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

    /**
     * Words put on the page at [at] (Fill & Sign › Text, Date), [size] points, no box round them: a text box comment whose
     * look MuPDF makes with its own fonts, Greek included (URW's base fonts carry Latin, Greek and Cyrillic).
     */
    fun text(i: Int, at: Offset, words: String, size: Float, color: Int) = annotate(i, "Text") { p, x0, y0 ->
        val lines = words.lines()
        val w = lines.maxOf { it.length } * size * 0.62f + size; val h = lines.size * size * 1.25f + size * 0.4f
        p.createAnnotation(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_FREE_TEXT).apply {
            setRect(com.artifex.mupdf.fitz.Rect(at.x + x0, at.y + y0, at.x + x0 + w, at.y + y0 + h))
            // the Greek set MuPDF writes text boxes in has no long dashes or curly quotes: their plain ones
            setDefaultAppearance("Helv", size, rgb(color)); setContents(words.replace('—', '-').replace('–', '-').replace('“', '"').replace('”', '"'))
            setBorderWidth(0f); update()
        }; true
    }

    /** A tick, a cross or a dot ([mark] ✓ ✗ •) at [at], [size] points: drawn strokes (no font needed), as Fill & Sign's. */
    fun mark(i: Int, at: Offset, mark: Char, size: Float, color: Int) = annotate(i, "Fill: $mark") { p, x0, y0 ->
        val x = at.x + x0 - size / 2; val y = at.y + y0 - size / 2
        fun pt(fx: Float, fy: Float) = com.artifex.mupdf.fitz.Point(x + fx * size, y + fy * size)
        if (mark == '•') p.createAnnotation(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_CIRCLE).apply {
            setRect(com.artifex.mupdf.fitz.Rect(x + size * 0.3f, y + size * 0.3f, x + size * 0.7f, y + size * 0.7f)); setColor(rgb(color)); setInteriorColor(rgb(color)); update()
        } else p.createAnnotation(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_INK).apply {
            setInkList(if (mark == '✓') arrayOf(arrayOf(pt(0.1f, 0.55f), pt(0.4f, 0.85f), pt(0.9f, 0.15f)))
                else arrayOf(arrayOf(pt(0.15f, 0.15f), pt(0.85f, 0.85f)), arrayOf(pt(0.85f, 0.15f), pt(0.15f, 0.85f))))
            setColor(rgb(color)); setBorderWidth(size / 9); update()
        }; true
    }

    /** A picture (the signature, drawn once and kept) at [at], [width] points wide: a stamp whose look is the picture. */
    fun picture(i: Int, at: Offset, png: ByteArray, width: Float, aspect: Float) = annotate(i, "Signature") { p, x0, y0 ->
        p.createAnnotation(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_STAMP).apply {
            setRect(com.artifex.mupdf.fitz.Rect(at.x + x0, at.y + y0, at.x + x0 + width, at.y + y0 + width * aspect))
            setAppearance(com.artifex.mupdf.fitz.Image(png)); update()
        }; true
    }

    // —— Recognize text (OCR): a scan's words, invisible over it, so Find, Copy and other apps read it ——

    private var ocrFont: com.artifex.mupdf.fitz.PDFObject? = null
    private val greek = charset("ISO-8859-7") // MuPDF's Greek simple encoding: ASCII and Greek in one byte each

    /** Page [i] has words of its own (not a scan, or read already). */
    fun hasText(i: Int): Boolean = synchronized(this) { page(i) { p -> p.toStructuredText().let { t -> t.asText().isNotBlank().also { t.destroy() } } } }
    /** Page [i]'s films and sounds (PdfMedia), and the [n]th annotation's written into [dir] to play. */
    fun media(i: Int) = synchronized(this) { page(i) { p -> (p as? com.artifex.mupdf.fitz.PDFPage)?.let(PdfMedia::spots).orEmpty() } }
    /** Page [i]'s [n]th annotation (a film, a sound) moved [dx], [dy] points (right, down): one undo step. */
    fun moveMedia(i: Int, n: Int, dx: Float, dy: Float) = annotate(i, "Move") { p, _, _ ->
        val o = p.annotations.orEmpty().getOrNull(n)?.`object` ?: return@annotate false
        val r = o.get("Rect"); val v = (0 until 4).map { r.get(it).asFloat() }
        // shortcut: on a page turned by its Rotate it goes the page's own way, not the screen's
        o.put("Rect", pdf!!.newArray().apply { push(v[0] + dx); push(v[1] - dy); push(v[2] + dx); push(v[3] - dy) }); true
    }
    fun mediaFile(i: Int, n: Int, dir: File) = synchronized(this) { page(i) { p -> (p as? com.artifex.mupdf.fitz.PDFPage)?.let { PdfMedia.play(it, n, dir) } } }

    /**
     * The file's words as a Words document (the user, 22 Oct: "make it in to words"; "can we make a table and content table
     * transfer correctly … assume the output was a table and fix it after the fact"):
     *  - each block of text a paragraph (its lines joined, a word broken by a hyphen made whole), a page's end a page break;
     *    a block in bigger letters than most a heading (1.6× level 1, 1.3× 2, 1.15× 3), or the level the PDF's bookmarks give
     *    it; colours, sizes, bold, italic as the PDF has them;
     *  - rows of words split by wide gaps, two or more of them with their columns lined up, a Words table;
     *  - a contents page (lines ending in a page number, with dots or not, most of the page) Words' own table of contents,
     *    made from the headings; also when the PDF has bookmarks and no such page, at the start;
     *  - a header or footer (the same words on most pages, numbers aside) left out.
     */
    fun toWords(): OfficeDoc = synchronized(this) {
        class Block(val lines: List<List<PdfSeg>>, val image: com.artifex.mupdf.fitz.Rect? = null)
        val pages = sizes.indices.map { i -> page(i) { p ->
            val t = p.toStructuredText("preserve-images") // the pictures' places too (left out by default)
            try {
                val json = org.json.JSONObject(t.asJSON())
                // the JSON has the fonts, the blocks the letters' places and colours: matched by where each line starts
                val lineChars = t.blocks.orEmpty().flatMap { b -> b.lines.orEmpty().map { l -> Triple(l.bbox.x0, l.bbox.y0, l) } }
                val blocks = json.optJSONArray("blocks") ?: org.json.JSONArray()
                (0 until blocks.length()).mapNotNull { b -> val jb = blocks.getJSONObject(b)
                    // a picture: where it is, cut from the page below
                    if (jb.optString("type") == "image") return@mapNotNull jb.optJSONObject("bbox")?.let { bb ->
                        val x = bb.optDouble("x").toFloat(); val y = bb.optDouble("y").toFloat()
                        Block(emptyList(), com.artifex.mupdf.fitz.Rect(x, y, x + bb.optDouble("w").toFloat(), y + bb.optDouble("h").toFloat())) }
                    jb.takeIf { it.optString("type") == "text" }?.optJSONArray("lines")?.let { ls ->
                    Block((0 until ls.length()).map { k ->
                        val l = ls.getJSONObject(k); val font = l.optJSONObject("font"); val bb = l.optJSONObject("bbox")
                        val x = bb?.optDouble("x", 0.0)?.toFloat() ?: 0f; val y = bb?.optDouble("y", 0.0)?.toFloat() ?: 0f
                        val size = font?.optDouble("size", 11.0)?.toFloat() ?: 11f
                        val bold = font?.optString("weight") == "bold"; val italic = font?.optString("style") == "italic"
                        val line = lineChars.minByOrNull { (cx, cy, _) -> kotlin.math.abs(cx - x) + kotlin.math.abs(cy - y) }?.third
                        val chars = line?.chars.orEmpty().toList()
                        if (chars.isEmpty()) listOf(PdfSeg(l.optString("text"), x, x + (bb?.optDouble("w", 0.0)?.toFloat() ?: 0f), y, size, bold, italic, 0))
                        else {
                            // split where the gap between two letters is wider than a letter and a half
                            val out = ArrayList<PdfSeg>(); var from = 0
                            for (c in 1..chars.size) {
                                val gap = if (c < chars.size) chars[c].quad.ul_x - chars[c - 1].quad.ur_x else Float.MAX_VALUE
                                if (gap > size * 1.5f) {
                                    val part = chars.subList(from, c)
                                    val text = part.joinToString("") { String(Character.toChars(it.c)) }.trim()
                                    if (text.isNotEmpty()) out += PdfSeg(text, part.first().quad.ul_x, part.last().quad.ur_x, y, size, bold, italic,
                                        part.firstOrNull { !it.isWhitespace }?.argb?.and(0xFFFFFF) ?: 0)
                                    from = c
                                }
                            }
                            out
                        }
                    })
                } }
            } finally { t.destroy() }
        } }
        fun textOf(line: List<PdfSeg>) = line.joinToString(" ") { it.text }
        // headers and footers: the same words, numbers aside, on most pages
        fun bare(t: String) = t.replace(Regex("\\d+"), "#").trim()
        val repeated = if (pages.size < 3) emptySet() else pages.flatMap { bs -> bs.map { b -> bare(b.lines.joinToString(" ") { textOf(it) }) }.distinct() }
            .groupingBy { it }.eachCount().filter { (t, n) -> t.isNotBlank() && n * 2 > pages.size }.keys
        val kept = pages.map { bs -> bs.filter { b -> bare(b.lines.joinToString(" ") { textOf(it) }) !in repeated } }
        // the body's size: the one most letters have
        val body = kept.flatten().flatMap { it.lines }.flatten().groupBy { Math.round(it.size * 2) / 2f }.maxByOrNull { (_, ss) -> ss.sumOf { it.text.length } }?.key ?: 11f
        // the headings' sizes, biggest first: the biggest is level 1, the next 2, the next 3 (a title over sections over sub-sections)
        val headingSizes = kept.flatten().flatMap { it.lines }.flatten().map { Math.round(it.size * 2) / 2f }.filter { it >= body * 1.15f }.distinct().sortedDescending().take(3)
        // the PDF's bookmarks: a block with a bookmark's title is a heading of its depth
        val marks = bookmarks().filter { it.title.isNotBlank() }.associate { MapsSearch.fold(it.title.trim()) to (it.depth + 1).coerceAtMost(3) }
        // a contents line: words, then dots or spaces, then a page number
        val contentsLine = Regex("""^(.{2,}?)[\s.·…_]{2,}(\d{1,4})$""")
        fun run(seg: PdfSeg, text: String = seg.text, heading: Int = 0) = DocRun(text, bold = seg.bold && heading == 0, italic = seg.italic,
            color = seg.color.takeIf { it != 0 }, sizePt = if (heading == 0) Math.round(seg.size * 2) / 2f else null)
        val out = ArrayList<DocPara>()
        var contentsAt: Int? = null
        kept.forEachIndexed { pi, blocks ->
            var first = pi > 0 && out.isNotEmpty() // the page's first paragraph starts a page, as in the PDF
            fun add(p: DocPara) { out += if (first) p.copy(keep = OfficeFormats.withPageBreak(p.keep, OfficeKind.Docx, true)) else p; first = false }
            // a contents page: most of its lines end in a page number
            // a contents page: three or more lines that end in a page number — they and their title make way for Words' own
            val contentsTitle = Regex("""^(περιεχόμενα|πίνακας περιεχομένων|contents|table of contents)$""", RegexOption.IGNORE_CASE)
            fun isContents(b: Block) = b.lines.isNotEmpty() && b.lines.all { l -> textOf(l).trim().let { contentsLine.matches(it) || contentsTitle.matches(it) } }
            val blocks = if (contentsAt == null && blocks.flatMap { it.lines }.count { contentsLine.matches(textOf(it).trim()) } >= 3) {
                val firstAt = blocks.indexOfFirst(::isContents)
                blocks.filterIndexed { k, b -> !isContents(b) || k == firstAt }.map { b -> if (isContents(b)) Block(emptyList(), null).also { } else b }
                    .also { contentsAt = -1 } // placed below, where its first block was
            } else blocks
            // the page's lines in rows by where they sit (a table's columns can come as separate blocks)
            val all = blocks.flatMap { it.lines }.filter { it.isNotEmpty() }
            val rows = all.sortedBy { it.first().y }.fold(ArrayList<MutableList<PdfSeg>>()) { acc, line ->
                val last = acc.lastOrNull()
                if (last != null && kotlin.math.abs(last.first().y - line.first().y) < line.first().size * 0.5f) last.addAll(line) else acc += line.toMutableList()
                acc
            }.map { r -> r.sortedBy { it.x0 } }
            // runs of rows of two cells or more, their cells starting where the run's columns do: tables
            val tableOf = HashMap<PdfSeg, Int>(); val tables = ArrayList<List<List<PdfSeg>>>()
            var k = 0
            while (k < rows.size) {
                if (rows[k].size < 2 || isMarkerOnly(rows[k].first().text)) { k++; continue }
                var e = k + 1
                // a cell lines up with a column by its left edge, its right edge or its middle (centred columns); the run's
                // columns grow with each row taken in
                val seen = ArrayList(rows[k])
                fun lines(a: PdfSeg, b: PdfSeg) = kotlin.math.abs(a.x0 - b.x0) < body * 2 || kotlin.math.abs(a.x1 - b.x1) < body * 2 ||
                    kotlin.math.abs((a.x0 + a.x1) - (b.x0 + b.x1)) < body * 4
                fun aligned(b: List<PdfSeg>) = b.size >= 2 && !isMarkerOnly(b.first().text) && b.all { s -> seen.any { lines(it, s) } }
                while (e < rows.size && aligned(rows[e]) && rows[e].first().y - rows[e - 1].first().y < body * 4) { seen.addAll(rows[e]); e++ }
                if (e - k >= 2) { val t = rows.subList(k, e).toList(); t.flatten().forEach { tableOf[it] = tables.size }; tables += t }
                k = maxOf(e, k + 1)
            }
            val emitted = HashSet<Int>()
            // the page drawn once (150 dpi) when it has pictures, each cut from it as the PDF shows it
            val drawn by lazy { page(pi) { p -> val o = p.bounds; com.artifex.mupdf.fitz.android.AndroidDrawDevice.drawPage(p, com.artifex.mupdf.fitz.Matrix.Scale(150f / 72f)) to o } }
            // a list's level from how far its marker is in from the page's least indented marker (level 1 there)
            val left = blocks.flatMap { it.lines }.mapNotNull { it.firstOrNull() }.filter { sg -> sg.size < body * 1.15f && (isMarkerOnly(sg.text) ||
                LIST_BULLET.containsMatchIn(sg.text.trimStart().take(2)) || LIST_NUMBER.find(sg.text.trimStart()) != null) }.minOfOrNull { it.x0 } ?: 0f
            var pending: PdfSeg? = null // a bullet or number standing alone (its own line or block): put before the words after it
            fun withPending(ls: List<List<PdfSeg>>): List<List<PdfSeg>> {
                val out = ArrayList<List<PdfSeg>>()
                for (l in ls) {
                    if (l.size == 1 && isMarkerOnly(l[0].text)) { pending = l[0]; continue }
                    val m = pending
                    if (m != null && l.isNotEmpty()) { out += listOf(PdfSeg(m.text.trim() + " " + l[0].text, m.x0, l[0].x1, l[0].y, l[0].size, l[0].bold, l[0].italic, l[0].color)) + l.drop(1); pending = null }
                    else out += l
                }
                return out
            }
            for (b0 in blocks) {
                // the contents' place: an empty paragraph Words' contents replace
                if (b0.image == null && b0.lines.isEmpty()) { if (contentsAt == -1) { contentsAt = out.size; add(DocPara(listOf(DocRun("")))) }; continue }
                val b = if (b0.image != null) b0 else Block(withPending(b0.lines))
                if (b.image == null && b.lines.isEmpty()) continue
                b.image?.let { r ->
                    if (r.x1 - r.x0 < 24 || r.y1 - r.y0 < 24) return@let // a rule or a dot, not a picture
                    runCatching {
                        val (bmp, o) = drawn; val k = 150f / 72f
                        val cx = ((r.x0 - o.x0) * k).toInt().coerceIn(0, bmp.width - 1); val cy = ((r.y0 - o.y0) * k).toInt().coerceIn(0, bmp.height - 1)
                        val cut = Bitmap.createBitmap(bmp, cx, cy, ((r.x1 - r.x0) * k).toInt().coerceIn(1, bmp.width - cx), ((r.y1 - r.y0) * k).toInt().coerceIn(1, bmp.height - cy))
                        val jpg = java.io.ByteArrayOutputStream().also { cut.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
                        val w = (r.x1 - r.x0).coerceAtMost(sizes[pi].width - 2 * 56f); val h = w * (r.y1 - r.y0) / (r.x1 - r.x0)
                        add(DocPara(listOf(DocRun(OBJECT_MARK.toString(), image = DocImage("n" + java.util.UUID.randomUUID(), null, w, h, null, jpg, "jpg")))))
                    }.onFailure { WriterLog.w("PDF: a picture to Words", it) }
                    continue
                }
                val inTable = b.lines.flatten().firstOrNull { it in tableOf }?.let { tableOf[it] }
                if (inTable != null) {
                    if (emitted.add(inTable)) {
                        val t = tables[inTable]
                        // the columns: where the cells start, the widest row's
                        val cols = t.maxBy { it.size }.map { (it.x0 + it.x1) / 2 }
                        val width = (sizes[pi].width - 2 * 56f).coerceAtLeast(200f)
                        val table = TableEdit.newTable(t.size, cols.size, width).let { tb ->
                            tb.copy(rows = tb.rows.mapIndexed { r, row -> row.copy(cells = row.cells.mapIndexed { c, cell ->
                                val inCell = t[r].filter { s -> cols.indices.minBy { kotlin.math.abs(cols[it] - (s.x0 + s.x1) / 2) } == c }
                                cell.copy(paras = listOf(DocPara(if (inCell.isEmpty()) listOf(DocRun("")) else inCell.mapIndexed { n, s -> run(s, (if (n > 0) " " else "") + s.text) })))
                            }) })
                        }
                        add(DocPara(listOf(DocRun(OBJECT_MARK.toString())), table = table))
                    }
                    // the block's lines that are not the table's stay text
                    val rest = b.lines.filter { l -> l.none { it in tableOf } }
                    if (rest.isEmpty()) continue
                    paragraph(rest, ::add, body, headingSizes, marks, left, ::run)
                } else paragraph(b.lines, ::add, body, headingSizes, marks, left, ::run)
            }
        }
        var doc = OfficeDoc(out)
        // the contents: where the PDF's were, or at the start when it had bookmarks but no such page
        val at = contentsAt?.takeIf { it >= 0 } ?: if (marks.isNotEmpty() && doc.paras.any { it.heading > 0 }) 0 else null
        if (at != null && doc.paras.any { it.heading > 0 }) runCatching {
            val styles = DocStyles.of(OfficeKind.Docx, null)
            doc = Contents.insert(doc, OfficeKind.Docx, styles, at, 3) { d, pg -> OfficePages.layout(d, styles, pg) { null } }
        }.onFailure { WriterLog.e("PDF: contents", it) }
        doc
    }

    /**
     * [lines] as paragraphs given to [add]: one, a heading by size or by a bookmark's title; or, where lines start with a
     * bullet (• ◦ ▪ – - *) or a number (1. 2) a. α.), a list item each, its level from how far it is in from [left].
     */
    private fun paragraph(lines: List<List<PdfSeg>>, add: (DocPara) -> Unit, body: Float, headingSizes: List<Float>, marks: Map<String, Int>, left: Float,
        run: (PdfSeg, String, Int) -> DocRun) {
        val bullet = LIST_BULLET; val number = LIST_NUMBER
        fun marker(l: List<PdfSeg>) = l.firstOrNull()?.text?.trimStart()?.let { t -> bullet.find(t)?.let { false to it.value } ?: number.find(t)?.let { true to it.value } }
        // the lines in groups: a new one at each line that starts with a marker
        val groups = ArrayList<MutableList<List<PdfSeg>>>()
        for (l in lines) { if (l.isEmpty()) continue; if (groups.isEmpty() || marker(l) != null) groups += mutableListOf(l) else groups.last() += l }
        for (g in groups) {
            val segs = g.flatten()
            val text = StringBuilder()
            for (l in g) { val w = l.joinToString(" ") { it.text }.trim(); if (w.isEmpty()) continue
                if (text.isNotEmpty()) { if (text.endsWith("-") && w.first().isLowerCase()) text.setLength(text.length - 1) else text.append(' ') }
                text.append(w) }
            if (text.isBlank()) continue
            // a number in a heading's bigger letters ("1. Introduction") is the heading's, not a list's
            val m = marker(g.first())?.takeIf { mk -> !mk.first || g.flatten().maxOf { it.size } < body * 1.15f }
            val words = if (m != null) text.toString().trimStart().removePrefix(m.second).trim() else text.toString()
            val size = segs.maxOf { it.size }
            val heading = if (m != null) 0 else marks[MapsSearch.fold(words.trim())] ?: if (words.length > 160) 0
                else headingSizes.indexOfFirst { kotlin.math.abs(it - Math.round(size * 2) / 2f) < 0.6f }.let { if (it < 0) 0 else it + 1 }
            // the paragraph's look: its most common letters' (colour, size, bold) — one run, as the block is one paragraph
            val main = segs.groupBy { Triple(it.color, Math.round(it.size * 2), it.bold) }.maxBy { (_, ss) -> ss.sumOf { it.text.length } }.value.first()
            val list = m?.let { DocList(ordered = it.first, level = ((g.first().first().x0 - left) / 18f).toInt().coerceIn(0, 4)) }
            add(DocPara(listOf(run(main, words, heading)), heading = heading, list = list))
        }
    }

    /** Page [i] drawn for reading: about 300 dpi, its long edge at most 3300 px (a phone's memory). */
    fun scanOf(i: Int): Pair<Bitmap, Float> = synchronized(this) { page(i) { p ->
        val s = sizes[i]; val k = minOf(300f / 72f, 3300f / maxOf(s.width, s.height))
        com.artifex.mupdf.fitz.android.AndroidDrawDevice.drawPage(p, com.artifex.mupdf.fitz.Matrix.Scale(k)) to k
    } }

    /** [words] (boxes in the pixels of a drawing [k] times the page's points) put on page [i] as invisible text, one undo step. */
    fun addTextLayer(i: Int, words: List<OcrWord>, k: Float) = edit("Recognize text, page ${i + 1}") { pdf ->
        if (words.isEmpty()) return@edit
        val font = com.artifex.mupdf.fitz.Font("Helvetica")
        val ref = ocrFont ?: pdf.addSimpleFont(font, com.artifex.mupdf.fitz.Font.SIMPLE_ENCODING_GREEK).also { ocrFont = it }
        val obj = pdf.findPage(i)
        val res = obj.get("Resources").takeIf { !it.isNull } ?: pdf.newDictionary().also { r ->
            obj.getInheritable("Resources").takeIf { !it.isNull }?.let { inh -> for (key in listOf("Font", "XObject", "ExtGState", "ColorSpace", "Pattern", "Shading", "ProcSet")) inh.get(key).takeIf { !it.isNull }?.let { r.put(key, it) } }
            obj.put("Resources", r) }
        val fonts = res.get("Font").takeIf { !it.isNull } ?: pdf.newDictionary().also { res.put("Font", it) }
        fonts.put("TetraOCR", ref)
        // from the drawing's top-left pixels to the PDF's own space (its origin bottom left, any turn the page has)
        val toPdf = page(i) { p -> val o = p.bounds; com.artifex.mupdf.fitz.Matrix.Inverted((p as com.artifex.mupdf.fitz.PDFPage).transform).let { inv ->
            com.artifex.mupdf.fitz.Matrix(com.artifex.mupdf.fitz.Matrix.Scale(1f / k), com.artifex.mupdf.fitz.Matrix(com.artifex.mupdf.fitz.Matrix.Translate(o.x0, o.y0), inv)) } }
        val sb = StringBuilder("q BT 3 Tr\n")
        for (w in words) {
            val bytes = w.text.toByteArray(greek)
            val adv = w.text.sumOf { c -> font.advanceGlyph(font.encodeCharacter(c.code)).toDouble() }.toFloat().coerceAtLeast(0.1f)
            val bl = com.artifex.mupdf.fitz.Point(w.box.left, w.box.bottom).transform(toPdf)
            val tl = com.artifex.mupdf.fitz.Point(w.box.left, w.box.top).transform(toPdf)
            val br = com.artifex.mupdf.fitz.Point(w.box.right, w.box.bottom).transform(toPdf)
            val hx = tl.x - bl.x; val hy = tl.y - bl.y; val wx = br.x - bl.x; val wy = br.y - bl.y
            // the word's own matrix: along its width, up its height, the font's em that tall and stretched to the box
            sb.append("/TetraOCR 1 Tf ").append(f(wx / adv)).append(' ').append(f(wy / adv)).append(' ').append(f(hx)).append(' ').append(f(hy)).append(' ')
                .append(f(bl.x)).append(' ').append(f(bl.y)).append(" Tm <").append(bytes.joinToString("") { "%02x".format(it) }).append("> Tj\n")
        }
        sb.append("ET Q\n")
        val before = pdf.addStream("q\n"); val after = pdf.addStream("Q\n" + sb)
        val old = obj.get("Contents")
        val list = pdf.newArray(); list.push(before)
        if (old.isArray) for (j in 0 until old.size()) list.push(old.get(j)) else if (!old.isNull) list.push(old)
        list.push(after)
        obj.put("Contents", list)
    }
    private fun f(v: Float) = "%.3f".format(java.util.Locale.ROOT, v)

    // —— Find and redact: words or patterns marked everywhere, then taken out of the file ——

    /** Every place [query] is (or, a pattern, every match of [regex] in a page's text) marked for redaction: how many. */
    fun markAll(query: String?, regex: Regex?): Int {
        var n = 0
        edit("Mark for redaction") { _ ->
            sizes.indices.forEach { i -> page(i) { p ->
                val pp = p as com.artifex.mupdf.fitz.PDFPage
                val hits: List<Array<com.artifex.mupdf.fitz.Quad>> = if (query != null) p.search(query).orEmpty().toList()
                else {
                    val t = p.toStructuredText()
                    try {
                        val text = t.asText()
                        // each match looked for as words on the page (StructuredText.search crashes MuPDF 1.28.5's JNI; Page.search is Find's own)
                        regex!!.findAll(text).map { it.value.trim() }.distinct().flatMap { v -> p.search(v).orEmpty().toList() }.toList()
                    } finally { t.destroy() }
                }
                hits.forEach { quads -> pp.createAnnotation(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_REDACT).apply { setQuadPoints(quads); update() }; n++ }
                pp.update()
            } }
        }
        return n
    }

    // —— the Comments pane and Bookmarks ——

    private val kinds = mapOf(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_HIGHLIGHT to R.string.words_highlight, com.artifex.mupdf.fitz.PDFAnnotation.TYPE_UNDERLINE to R.string.words_underline,
        com.artifex.mupdf.fitz.PDFAnnotation.TYPE_STRIKE_OUT to R.string.words_strikethrough, com.artifex.mupdf.fitz.PDFAnnotation.TYPE_SQUIGGLY to R.string.pdf_kind_squiggly,
        com.artifex.mupdf.fitz.PDFAnnotation.TYPE_TEXT to R.string.pdf_note, com.artifex.mupdf.fitz.PDFAnnotation.TYPE_FREE_TEXT to R.string.pdf_text,
        com.artifex.mupdf.fitz.PDFAnnotation.TYPE_INK to R.string.pdf_kind_drawing, com.artifex.mupdf.fitz.PDFAnnotation.TYPE_SQUARE to R.string.pdf_rectangle,
        com.artifex.mupdf.fitz.PDFAnnotation.TYPE_CIRCLE to R.string.pdf_oval, com.artifex.mupdf.fitz.PDFAnnotation.TYPE_LINE to R.string.words_line,
        com.artifex.mupdf.fitz.PDFAnnotation.TYPE_STAMP to R.string.pdf_stamp, com.artifex.mupdf.fitz.PDFAnnotation.TYPE_REDACT to R.string.pdf_kind_to_redact,
        com.artifex.mupdf.fitz.PDFAnnotation.TYPE_POLYGON to R.string.pdf_kind_polygon, com.artifex.mupdf.fitz.PDFAnnotation.TYPE_CARET to R.string.pdf_kind_caret)

    /** Every comment, page by page (not form fields, links or popups); a highlight's words are the ones it covers. */
    fun comments(): List<PdfComment> = synchronized(this) {
        sizes.indices.flatMap { i -> page(i) { p ->
            val o = p.bounds
            val all = (p as? com.artifex.mupdf.fitz.PDFPage)?.annotations.orEmpty()
            val byNumber = all.withIndex().associate { (k, a) -> runCatching { a.`object`.asIndirect() }.getOrDefault(-1) to k }
            all.withIndex().mapNotNull { (k, a) ->
                val kind = kinds[a.type] ?: return@mapNotNull null
                val parent = runCatching { a.`object`.get("IRT").takeIf { !it.isNull }?.asIndirect()?.let { byNumber[it] } }.getOrNull()
                var words = a.contents.orEmpty()
                if (words.isBlank() && a.type in listOf(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_HIGHLIGHT, com.artifex.mupdf.fitz.PDFAnnotation.TYPE_UNDERLINE,
                        com.artifex.mupdf.fitz.PDFAnnotation.TYPE_STRIKE_OUT)) runCatching {
                    val r = a.bounds; val t = p.toStructuredText()
                    words = t.copy(com.artifex.mupdf.fitz.Point(r.x0, (r.y0 + r.y1) / 2), com.artifex.mupdf.fitz.Point(r.x1, (r.y0 + r.y1) / 2)).orEmpty().trim(); t.destroy()
                }
                val r = a.bounds
                PdfComment(i, k, if (parent != null) R.string.words_reply else kind, words.replace('\n', ' '), Offset((r.x0 + r.x1) / 2 - o.x0, (r.y0 + r.y1) / 2 - o.y0),
                    parent, runCatching { a.author.orEmpty() }.getOrDefault(""))
            }
        } }
    }

    /** A reply to [c]: a note in the same place that answers it (PDF's In Reply To), as other readers thread them. */
    fun reply(c: PdfComment, words: String) = annotate(c.page, "Reply") { p, x0, y0 ->
        val parent = p.annotations.orEmpty().getOrNull(c.index) ?: return@annotate false
        p.createAnnotation(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_TEXT).apply {
            setRect(com.artifex.mupdf.fitz.Rect(c.at.x + x0, c.at.y + y0, c.at.x + x0 + 20, c.at.y + y0 + 20)); setContents(words)
            `object`.put("IRT", parent.`object`); `object`.put("RT", pdf!!.newName("R")); update()
        }; true
    }

    // —— Forms: fields made (a text field, a check box), as other apps fill them ——

    private var fieldCount = 0
    /**
     * A [checkbox] or a text field from [a] to [b] on page [i], named Field n: MuPDF's own widget (so the page and the form's
     * list refer to one object; built by hand, MuPDF 1.28's Java copied it into each), its field properties set on it.
     */
    fun addField(i: Int, a: Offset, b: Offset, checkbox: Boolean) = annotate(i, if (checkbox) "Check box" else "Text field") { p, x0, y0 ->
        val w = p.createAnnotation(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_WIDGET)
        w.setRect(com.artifex.mupdf.fitz.Rect(minOf(a.x, b.x) + x0, minOf(a.y, b.y) + y0, maxOf(a.x, b.x) + x0, maxOf(a.y, b.y) + y0))
        val o = w.`object`
        val taken = runCatching { pdf!!.trailer.get("Root").get("AcroForm").get("Fields").let { f -> (0 until f.size()).map { f.get(it).get("T").asString() } } }.getOrDefault(emptyList()).toSet()
        val name = generateSequence(fieldCount + 1) { it + 1 }.map { "Field $it" }.first { it !in taken }; fieldCount++
        o.put("T", name); o.put("F", 4)
        if (checkbox) { o.put("FT", pdf!!.newName("Btn")); o.put("V", pdf.newName("Off")); o.put("AS", pdf.newName("Off")); o.put("DA", "/ZaDb 0 Tf 0 g") }
        else { o.put("FT", pdf!!.newName("Tx")); o.put("DA", "/Helv 11 Tf 0 g") }
        // in the form's list (a form made if the file had none)
        val root = pdf.trailer.get("Root")
        if (root.get("AcroForm").isNull) root.put("AcroForm", pdf.newDictionary())
        val acro = root.get("AcroForm")
        if (acro.get("Fields").isNull) acro.put("Fields", pdf.newArray())
        if (acro.get("DA").isNull) acro.put("DA", "/Helv 11 Tf 0 g")
        acro.get("Fields").push(o)
        w.update(); true
    }

    /** The comment [c] deleted (the Comments pane's bin). */
    fun deleteComment(c: PdfComment) = annotate(c.page, "Delete comment") { p, _, _ ->
        p.annotations.orEmpty().getOrNull(c.index)?.let { p.deleteAnnotation(it); true } ?: false
    }

    /** The file's bookmarks, nested ones a step in. */
    fun bookmarks(): List<PdfMark> = synchronized(this) {
        val out = ArrayList<PdfMark>()
        fun walk(items: Array<com.artifex.mupdf.fitz.Outline>?, depth: Int) {
            items.orEmpty().forEach { o ->
                val pg = runCatching { doc.pageNumberFromLocation(doc.resolveLink(o)) }.getOrDefault(-1)
                out += PdfMark(o.title.orEmpty(), pg, depth); walk(o.down, depth + 1)
            }
        }
        runCatching { walk(doc.loadOutline(), 0) }
        out
    }

    /** A bookmark called [title] to page [i], after the last one at the top level. */
    fun addBookmark(title: String, i: Int) = edit("Bookmark") { _ ->
        val it0 = doc.outlineIterator()
        try {
            while (it0.next() == com.artifex.mupdf.fitz.OutlineIterator.ITERATOR_AT_ITEM) Unit
            it0.insert(title, doc.formatLinkURI(com.artifex.mupdf.fitz.LinkDestination.Fit(0, i)), false)
        } finally { it0.destroy() }
    }

    /** The [n]th bookmark (in [bookmarks]' order) deleted, with any under it. */
    fun deleteBookmark(n: Int) = edit("Delete bookmark") { _ ->
        val it0 = doc.outlineIterator()
        try {
            var k = 0
            fun visit(): Boolean {
                do {
                    if (it0.item() == null) return false
                    if (k == n) { it0.delete(); return true }
                    k++
                    if (it0.down() == com.artifex.mupdf.fitz.OutlineIterator.ITERATOR_AT_ITEM) { if (visit()) return true; it0.up() }
                } while (it0.next() == com.artifex.mupdf.fitz.OutlineIterator.ITERATOR_AT_ITEM)
                return false
            }
            visit()
        } finally { it0.destroy() }
    }

    /** Reduce size: the next save rewrites the file whole — what nothing uses dropped, copies of the same thing made one, streams and fonts compressed. */
    fun shrink() { rewrite = rewrite ?: "garbage=deduplicate,compress,compress-fonts,compress-images" }

    // —— Insert: on every page ——

    /** A picture on page [i], [box] in page points (from its top left); [opacity] 1 solid. */
    private fun stamp(p: com.artifex.mupdf.fitz.PDFPage, x0: Float, y0: Float, png: ByteArray, box: android.graphics.RectF, opacity: Float) =
        p.createAnnotation(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_STAMP).apply {
            setRect(com.artifex.mupdf.fitz.Rect(box.left + x0, box.top + y0, box.right + x0, box.bottom + y0))
            setAppearance(com.artifex.mupdf.fitz.Image(png)); if (opacity < 1f) setOpacity(opacity); update()
        }

    /** A picture (Insert › Image) in the middle of page [i], half its width. */
    fun image(i: Int, png: ByteArray, aspect: Float) = annotate(i, "Image") { p, x0, y0 ->
        val s = sizes[i]; val w = s.width / 2; val h = w * aspect
        stamp(p, x0, y0, png, android.graphics.RectF((s.width - w) / 2, (s.height - h) / 2, (s.width + w) / 2, (s.height + h) / 2), 1f); true
    }

    /** A watermark ([png], drawn turned) across the middle of every page, faint. */
    fun watermark(png: ByteArray, aspect: Float) = edit("Watermark") { _ ->
        sizes.indices.forEach { i -> page(i) { p -> val o = p.bounds; val s = sizes[i]; val w = s.width * 0.8f; val h = w * aspect
            stamp(p as com.artifex.mupdf.fitz.PDFPage, o.x0, o.y0, png, android.graphics.RectF((s.width - w) / 2, (s.height - h) / 2, (s.width + w) / 2, (s.height + h) / 2), 0.25f); p.update() } }
    }

    /** Words at the top and/or the bottom of every page ([footer] "{n}" and "{N}" for the page number and count), [size] points. */
    fun headerFooter(header: String, footer: String, size: Float) = edit("Header and footer") { _ ->
        sizes.indices.forEach { i -> page(i) { p -> val o = p.bounds; val s = sizes[i]; val pp = p as com.artifex.mupdf.fitz.PDFPage
            fun put(words: String, top: Float) {
                if (words.isBlank()) return
                val t = words.replace("{n}", "${i + 1}").replace("{N}", "${sizes.size}").replace('—', '-').replace('–', '-')
                val w = t.length * size * 0.6f + size
                pp.createAnnotation(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_FREE_TEXT).apply {
                    setRect(com.artifex.mupdf.fitz.Rect(o.x0 + (s.width - w) / 2, o.y0 + top, o.x0 + (s.width + w) / 2, o.y0 + top + size * 1.6f))
                    setDefaultAppearance("Helv", size, floatArrayOf(0.25f, 0.25f, 0.25f)); setContents(t); setBorderWidth(0f); update()
                }
            }
            put(header, 18f); put(footer, s.height - 18f - size * 1.6f); pp.update() } }
    }

    // —— Protect ——

    /** Changes that an appended save would leave readable in the file (redaction) or that change how it is read (a password). */
    private var rewrite: String? = null

    /** An area marked to be blacked out (Protect › Redact), from [a] to [b]: nothing is removed until [applyRedactions]. */
    fun markRedaction(i: Int, a: Offset, b: Offset) = annotate(i, "Mark for redaction") { p, x0, y0 ->
        p.createAnnotation(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_REDACT).apply {
            setRect(com.artifex.mupdf.fitz.Rect(minOf(a.x, b.x) + x0, minOf(a.y, b.y) + y0, maxOf(a.x, b.x) + x0, maxOf(a.y, b.y) + y0)); update()
        }; true
    }

    /** Every marked area's text, pictures and drawings taken out of the file, black boxes in their place; how many pages had some. */
    fun applyRedactions(): Int {
        var n = 0
        edit("Redact") { _ -> sizes.indices.forEach { i -> page(i) { p -> if ((p as com.artifex.mupdf.fitz.PDFPage).applyRedactions(true, com.artifex.mupdf.fitz.PDFPage.REDACT_IMAGE_PIXELS)) n++ } } }
        if (n > 0) rewrite = rewrite ?: "garbage,compress"
        return n
    }
    val hasRedactionMarks get() = synchronized(this) { sizes.indices.any { i -> page(i) { p -> (p as? com.artifex.mupdf.fitz.PDFPage)?.annotations.orEmpty().any { it.type == com.artifex.mupdf.fitz.PDFAnnotation.TYPE_REDACT } } } }

    /** The next save asks for [password] to open the file (AES-256), or with null opens without one. */
    fun protect(password: String?) {
        rewrite = if (password == null) "garbage,compress,decrypt" else "garbage,compress,encrypt=aes-256,user-password=${password.replace(",", "")},owner-password=${password.replace(",", "")}"
    }
    val rewriting get() = rewrite != null

    // —— forms (their own fields: fill in, tick, choose) ——

    /** The file has form fields to fill in. */
    val hasForm: Boolean by lazy { synchronized(this) { runCatching { (pdf?.trailer?.get("Root")?.get("AcroForm")?.get("Fields")?.size() ?: 0) > 0 }.getOrDefault(false) } }

    private fun widget(p: com.artifex.mupdf.fitz.Page, x: Float, y: Float) = (p as? com.artifex.mupdf.fitz.PDFPage)?.widgets.orEmpty()
        .lastOrNull { w -> w.bounds.let { r -> x >= r.x0 && x <= r.x1 && y >= r.y0 && y <= r.y1 } }

    /** The field under [at]. */
    fun fieldAt(i: Int, at: Offset): PdfField? = synchronized(this) { page(i) { p ->
        val o = p.bounds
        widget(p, at.x + o.x0, at.y + o.y0)?.let { w ->
            PdfField(when { w.isText -> "text"; w.isCheckbox -> "check"; w.isRadioButton -> "radio"; w.isChoice -> "choice"; else -> "other" },
                w.label?.ifBlank { null } ?: w.name.orEmpty(), w.value.orEmpty(), w.options?.toList().orEmpty(), w.isMultiline, w.isReadOnly)
        }
    } }

    /** The field under [at] filled with [value] (text, a choice) or, a box or a round button, ticked or not. */
    fun fill(i: Int, at: Offset, value: String?) = annotate(i, "Fill in") { p, x0, y0 ->
        val w = widget(p, at.x + x0, at.y + y0) ?: return@annotate false
        when {
            w.isText -> w.setTextValue(value.orEmpty())
            w.isCheckbox || w.isRadioButton -> w.toggle()
            w.isChoice -> w.setChoiceValue(value.orEmpty())
            else -> return@annotate false
        }
        w.update(); true
    }

    /** A standard stamp ([name] one of [STAMPS]: Approved, Draft, …) centred on [at], drawn by MuPDF in the stamp's own look. */
    fun stampAt(i: Int, at: Offset, name: String) = annotate(i, "Stamp") { p, x0, y0 ->
        p.createAnnotation(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_STAMP).apply {
            setIcon(name); setRect(com.artifex.mupdf.fitz.Rect(at.x + x0 - 75, at.y + y0 - 22, at.x + x0 + 75, at.y + y0 + 22)); update()
        }; true
    }

    /** A text box from [a] to [b]: [words] inside a frame of [color], as a comment others' readers show and let you edit. */
    /**
     * Words with their look ([PdfText]) on page [i], their top left at [x], [y] (page points), [room] across (a text box:
     * its width; a text: as far as its longest line), at least [minHeight] tall: a text annotation whose look is the
     * words laid out by Android with their font embedded (MuPDF's own text boxes know only its few fonts), its words kept
     * in it for other readers and the look in Tetra's own key. [replacing]: a point on the one it was, taken out in the same step.
     */
    fun putText(i: Int, x: Float, y: Float, t: PdfText, room: Float, minHeight: Float = 0f, replacing: Offset? = null) = annotate(i, "Text") { p, x0, y0 ->
        replacing?.let { at -> typedAt(p, at.x + x0, at.y + y0)?.let { p.deleteAnnotation(it.first) } }
        if (t.words.isBlank()) return@annotate true // emptied: only taken out
        val (lw, lh) = PdfText.size(t, room, minHeight)
        val (w, h) = PdfText.bounds(t, lw, lh) // its place on the page, turned
        val src = com.artifex.mupdf.fitz.Document.openDocument(PdfText.pdf(t, lw, lh), "application/pdf")
        try {
            val sp = (src.asPDF() ?: error("No PDF")).findPage(0)
            val c = sp.get("Contents")
            val contents = if (c.isArray) (0 until c.size()).fold(ByteArray(0)) { all, k -> all + c.get(k).readStream() + '\n'.code.toByte() } else c.readStream()
            val res = pdf!!.graftObject(sp.get("Resources"))
            p.createAnnotation(com.artifex.mupdf.fitz.PDFAnnotation.TYPE_FREE_TEXT).apply {
                setRect(com.artifex.mupdf.fitz.Rect(x + x0, y + y0, x + x0 + w, y + y0 + h))
                setDefaultAppearance("Helv", t.look.size, rgb(t.look.color)); setContents(t.words); setBorderWidth(0f)
                setAppearance(com.artifex.mupdf.fitz.Rect(0f, 0f, w, h), res, com.artifex.mupdf.fitz.Buffer().apply { writeBytes(contents) })
                getObject().put(TYPED_KEY, pdf!!.newString(t.json(lw to lh)))
            }
        } finally { src.destroy() }
        true
    }
    /**
     * Words put on with [putText] under [at] on page [i] moved among the page's comments, pictures and drawings (Word's
     * Arrange): [to] 1 forward, -1 backward, 2 to the front, -2 to the back. False when there was nowhere to go.
     */
    fun restack(i: Int, at: Offset, to: Int): Boolean {
        var moved = false
        annotate(i, if (to > 0) "Bring forward" else "Send backward") { p, x0, y0 ->
            val me = typedAt(p, at.x + x0, at.y + y0)?.first?.getObject() ?: return@annotate false
            val annots = p.getObject().get("Annots").takeIf { it.isArray } ?: return@annotate false
            val list = (0 until annots.size()).map { annots.get(it) }.toMutableList()
            val k = list.indexOfFirst { runCatching { it.asIndirect() == me.asIndirect() }.getOrDefault(false) }.takeIf { it >= 0 } ?: return@annotate false
            val n = when (to) { 2 -> list.size - 1; -2 -> 0; else -> (k + to).coerceIn(0, list.size - 1) }
            if (n == k) return@annotate false
            list.add(n, list.removeAt(k)) // drawn in this order: the last on top
            val again = pdf!!.newArray(); list.forEach { again.push(it) }; p.getObject().put("Annots", again)
            moved = true; true
        }
        return moved
    }
    /** Words put on with [putText] under [at] on page [i]: where they are (page points) and how they were typed. */
    fun typedAt(i: Int, at: Offset): Pair<android.graphics.RectF, PdfText>? = synchronized(this) { page(i) { p ->
        val o = p.bounds
        typedAt(p as? com.artifex.mupdf.fitz.PDFPage ?: return@page null, at.x + o.x0, at.y + o.y0)?.let { (a, t) ->
            val r = a.rect; android.graphics.RectF(r.x0 - o.x0, r.y0 - o.y0, r.x1 - o.x0, r.y1 - o.y0) to t }
    } }
    private fun typedAt(p: com.artifex.mupdf.fitz.PDFPage, x: Float, y: Float) = p.annotations.orEmpty().reversed().firstNotNullOfOrNull { a ->
        if (a.type != com.artifex.mupdf.fitz.PDFAnnotation.TYPE_FREE_TEXT) return@firstNotNullOfOrNull null
        val r = a.rect; if (x < r.x0 - 4 || x > r.x1 + 4 || y < r.y0 - 4 || y > r.y1 + 4) return@firstNotNullOfOrNull null
        a.getObject().get(TYPED_KEY).takeIf { !it.isNull }?.asString()?.let(PdfText::parse)?.let { a to it }
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

    /** The file as this document read it (its length and time): what its one appended save goes onto. */
    private val openedAs = file.length() to file.lastModified()
    /** Saved once already: MuPDF appends only onto the file it opened, so every save after the first is written whole. */
    private var savedOnce = false

    /**
     * Saved over [file] (or into [to]), through a copy beside it renamed over it. The session's first save appends its
     * changes to the file as opened (an incremental save: all the file had stays, signatures too); every later one is
     * written whole, as is one after another app changed the file, or one MuPDF had to repair. (Before 27 Oct every save
     * appended: MuPDF chains an appended part to the file it opened, so the parts of the saves before were lost — a
     * second save's, AutoSave's, a second day's.)
     */
    fun save(to: File = file, copy: Boolean = false) = synchronized(this) {
        val p = pdf ?: throw PdfError(R.string.pdf_cannot_edit)
        val was = dirty
        val temp = File(to.parentFile, ".${to.name}.saving")
        // redacted or (un)locked: written whole, the old objects dropped (an appended save would keep what was taken out);
        // a copy leaves that still to do for the file itself
        val whole = rewrite
        val untouched = (file.length() to file.lastModified()) == openedAs
        val appendable = p.canBeSavedIncrementally() && !savedOnce && untouched
        if (whole != null) { p.save(temp.path, whole); if (!copy) rewrite = null }
        // nothing new and nothing written since it was opened: the file as it is (MuPDF appending nothing left a broken stub)
        else if (!p.hasUnsavedChanges() && untouched && !savedOnce) file.copyTo(temp, overwrite = true)
        else if (appendable) { file.copyTo(temp, overwrite = true); p.save(temp.path, "incremental,compress") } // new pictures compressed (a watermark was 2.3 MB raw)
        // what is unused dropped, the objects keep their numbers (compact renumbered every one on each save)
        else p.save(temp.path, "garbage,compress")
        if (!temp.renameTo(to)) throw PdfError(R.string.pdf_save_failed_file)
        p.undoRedoPosition().let { at -> savedAt = at; redoStale = p.undoRedoSteps() > at }
        savedOnce = true
        copied = copy && was
        WriterLog.i("PDF: saved${if (copy) " a copy" else ""}${if (whole == null && !appendable) " whole" else ""} (${to.length() / 1024} KB)")
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
internal fun PdfScreen(back: () -> Unit, open: String, openWords: (String) -> Unit = {}) {
    val context = LocalContext.current
    remember { OfficeFonts.assets = context.applicationContext.assets } // Words' fonts (Times' and Calibri's twins) for its texts, as Words' and Slides'
    val scope = rememberCoroutineScope()
    var file by remember(open) { mutableStateOf(Uri.parse(open).path?.let(::File)) } // File › Save as and Rename go on with another
    remember { WriterUnits.load(context) } // View's cm or inches, Words' own setting
    val unitName = stringResource(if (WriterUnits.inches) R.string.pdf_unit_in else R.string.words_unit_cm)
    val paperWay: (String, Boolean) -> String = { n, land -> context.getString(if (land) R.string.pdf_paper_landscape else R.string.pdf_paper_portrait, n) }
    RememberOpened(file) // the Office home's Recents
    var doc by remember { mutableStateOf<PdfDoc?>(null) }
    OpenTiming("PDF", doc != null)
    var failed by remember { mutableStateOf<Int?>(null) }
    var password by remember { mutableStateOf<String?>(null) } // given in the password dialog
    var askPassword by remember { mutableStateOf<Boolean?>(null) } // the dialog: true when the last one was wrong
    LaunchedEffect(open, password) {
        WriterLog.init(context)
        val f = file ?: run { failed = R.string.pdf_cannot_open; return@LaunchedEffect }
        WriterLog.i("PDF: open ${f.length() / 1024} KB")
        withContext(Dispatchers.IO) { runCatching { WriterLog.timed("PDF: read") { PdfDoc(f, password) } } }
            .onSuccess { doc = it; askPassword = null; WriterLog.i("PDF: ${it.sizes.size} pages") }
            .onFailure {
                if (it is PdfPasswordException) { askPassword = it.tried; return@onFailure }
                WriterLog.e("PDF: could not open", it)
                failed = R.string.pdf_unreadable
            }
    }
    askPassword?.let { wrong ->
        var typed by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(onDismissRequest = { askPassword = null; back() },
            title = { Text(stringResource(R.string.pdf_has_password)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wrong) Text(stringResource(R.string.pdf_wrong_password), color = MaterialTheme.colorScheme.error)
                androidx.compose.material3.OutlinedTextField(typed, { typed = it }, label = { Text(stringResource(R.string.pdf_password)) }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { askPassword = null; password = typed }))
            } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { askPassword = null; password = typed }) { Text(stringResource(R.string.common_open), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { askPassword = null; back() }) { Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } })
    }
    DisposableEffect(doc) { val d = doc; onDispose { d?.close() } } // this one: reading doc when it goes would close the next one
    BackHandler { back() }

    // View, as Words': pages two across, as wide as the screen, rulers (a tablet), the page list at the side
    var across by remember { mutableIntStateOf(1) }
    var fitWidth by remember { mutableStateOf(false) }
    var rulers by remember { mutableStateOf(true) }
    var navigation by remember { mutableStateOf(true) }
    // Layout › Margins (Word's presets): where Position puts a text and a new one starts; the rulers show them
    var margins by remember { mutableStateOf(PdfMargins.load(context)) }
    val list = rememberLazyListState()
    // the page in the middle of the screen, where a swipe leaves it; at either end the first or last (short pages, as beside a
    // phone's page list, never reach the middle there)
    val at by remember { derivedStateOf { list.layoutInfo.let { l -> val mid = (l.viewportStartOffset + l.viewportEndOffset) / 2
        if (!list.canScrollBackward) 0 else if (!list.canScrollForward) (l.totalItemsCount - 1).coerceAtLeast(0)
        else l.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - mid) }?.index ?: 0 }
        .let { row -> (row * across).coerceAtMost(((doc?.sizes?.size ?: 1) - 1).coerceAtLeast(0)) } } } // two across: a row's first page
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    // the zoom a pinch ended at: the page in the middle drawn again that sharp
    var settled by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(zoom) { delay(250); settled = zoom }
    fun goTo(page: Int) { zoom = 1f; pan = Offset.Zero; scope.launch { list.scrollToItem(page / across) } }

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
            if (text.isBlank()) Toast.makeText(context, context.getString(R.string.pdf_page_no_text, page + 1), Toast.LENGTH_LONG).show()
            else context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.pdf_page_n, page + 1), text))
        }
    }
    /**
     * The PDF's words as a Words document beside it, opened there (the user, 22 Oct: "export in to image is stupid … make it in
     * to words"): its paragraphs, headings told by their size, bold kept; a scan's after Recognize text.
     */
    fun exportWords() {
        val d = doc ?: return; val f = file ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching {
                val words = d.toWords()
                if (words.paras.none { it.text.isNotBlank() || it.table != null }) throw PdfError(R.string.pdf_no_text_scan)
                val to = run { var n = 0; var t: File; do { n++; t = File(f.parentFile, "${f.nameWithoutExtension}${if (n > 1) " $n" else ""}.docx") } while (t.exists()); t }
                to.writeBytes(OfficeFormats.write(words, OfficeKind.Docx)); to
            } }
            r.onSuccess { to -> WriterLog.i("PDF: to Words, ${to.length() / 1024} KB"); Toast.makeText(context, context.getString(R.string.words_saved_as, to.name), Toast.LENGTH_SHORT).show(); openWords(Uri.fromFile(to).toString()) }
                .onFailure { WriterLog.e("PDF: to Words", it); Toast.makeText(context, pdfSays(context, it, R.string.pdf_doc_not_made), Toast.LENGTH_LONG).show() }
        }
    }
    /** All the file's words (a scan's after Recognize text) in a .txt beside it. */
    fun exportText() {
        val d = doc ?: return; val f = file ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching {
                val text = d.sizes.indices.joinToString("\n\n") { i -> kotlinx.coroutines.runBlocking { d.text(i) }.trim() }
                val to = generateSequence(1) { it }.let { _ -> var n = 0; var t: File; do { n++; t = File(f.parentFile, "${f.nameWithoutExtension}${if (n > 1) " $n" else ""}.txt") } while (t.exists()); t }
                to.writeText(text); to to text.isBlank()
            } }
            r.onSuccess { (to, empty) -> Toast.makeText(context, if (empty) context.getString(R.string.pdf_no_text_scan) else context.getString(R.string.words_saved_as, to.name), Toast.LENGTH_LONG).show() }
                .onFailure { WriterLog.e("PDF: export text", it); Toast.makeText(context, pdfSays(context, it, R.string.pdf_export_failed), Toast.LENGTH_LONG).show() }
        }
    }
    fun print() {
        val f = file ?: return
        runCatching { context.getSystemService(android.print.PrintManager::class.java)?.print(f.nameWithoutExtension, PdfPrintAdapter(f, f.nameWithoutExtension), null) }
            .onSuccess { WriterLog.i("PDF: print") }.onFailure { WriterLog.e("PDF: print", it) }
    }

    // —— editing (Plan.md § PDF): opened view only, Edit as Words' ——
    var editing by remember { mutableStateOf(JustMade.take(open)) } // a file just made with New: editing at once
    var tab by remember { mutableStateOf("Home") }
    val media = remember { PdfMediaState() } // a film or sound chosen (its Video tab) and the one playing
    var tool by remember { mutableStateOf<PdfTool?>(null) }
    var markColour by remember { mutableStateOf(0xFFEB3B) } // Review's colour (highlight, shapes, notes)
    var penColour by remember { mutableStateOf(0xE53935) } // Draw's
    var penWidth by remember { mutableStateOf(2f) }
    var writing by remember { mutableStateOf<Pair<Int, Offset>?>(null) } // a note being written: its page and place
    var reading by remember { mutableStateOf<String?>(null) } // a note tapped: its words
    var typing by remember { mutableStateOf<PdfTyping?>(null) } // Fill & Sign › Text or Review › Text box being typed on the page
    val typed = remember { com.mohamedrejeb.richeditor.model.RichTextState() } // the words typed, in Words' editor (some words with their own look)
    var textLook by remember { mutableStateOf(PdfTextLook()) } // the last look used: the next text starts with it
    var fontBefore by remember { mutableStateOf<String?>(null) } // Font's strip open: the font before it (Cancel puts it back)
    var chosen by remember { mutableStateOf<Triple<Int, android.graphics.RectF, PdfText>?>(null) } // a text put on, chosen (Slides' first tap): its page, place, words
    val framePreview = remember { PdfFramePreview() } // where a finger or an arrow has the chosen text, until put on
    var clip by remember { mutableStateOf<Pair<android.graphics.RectF, PdfText>?>(null) } // Copy's text, for Paste
    var copiedLook by remember { mutableStateOf<PdfTextLook?>(null) } // Selection › Copy formatting, for Paste formatting (Slides')
    var tabBefore by remember { mutableStateOf("Home") } // the tab shown before a text was chosen, back when none is
    // a text chosen: its own tab shown (as Words' Image tab when a picture is tapped); none chosen: the tab before
    LaunchedEffect(chosen != null) {
        if (chosen != null) { media.chosen = null; if (tab != "Selection" && tab != "Text box") { tabBefore = tab; tab = "Text box" } }
        else if (tab == "Selection" || tab == "Text box") tab = tabBefore
    }
    // a film or sound chosen: its Video (or Audio) tab, as Words' when one is tapped
    LaunchedEffect(media.chosen != null) {
        if (media.chosen != null) { chosen = null; if (tab != "Video") { tabBefore = tab; tab = "Video" } }
        else if (tab == "Video") tab = tabBefore
    }
    LaunchedEffect(editing) { media.chosen = null }
    var pageMenu by remember { mutableStateOf<Triple<Int, Offset, Offset>?>(null) } // a right-click's menu: its page, where (px on the page), where (page points)
    var filling by remember { mutableStateOf<Triple<Int, Offset, PdfField>?>(null) } // a form's text or choice field tapped
    var fillSize by remember { mutableStateOf(11f) }
    val signatureFile = remember { File(context.filesDir, "pdf-signature.png") }
    var signing by remember { mutableStateOf(false) } // drawing the signature
    var asking by remember { mutableStateOf<String?>(null) } // Insert's and Protect's windows: "header", "watermark", "password", "bookmark"
    var stampName by remember { mutableStateOf("Approved") }
    var stampPicture by remember { mutableStateOf<Pair<ByteArray, Float>?>(null) } // your own stamp: a picture and its height / width
    val pickStamp = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bmp = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
                val k = minOf(1f, 800f / maxOf(info.size.width, info.size.height)); dec.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1)) }
            java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() to bmp.height.toFloat() / bmp.width
        }.onSuccess { stampPicture = it; stampName = PICTURE_STAMP; tool = PdfTool.Stamp; Toast.makeText(context, R.string.pdf_tap_stamp, Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, R.string.pdf_not_a_picture, Toast.LENGTH_SHORT).show() }
    }
    var reading2 by remember { mutableStateOf<Pair<Int, Int>?>(null) } // Recognize text running: the page, of how many
    var ocrJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var pane by remember { mutableStateOf<String?>(null) } // "Comments" or "Bookmarks", beside the pages (a phone: over their lower half)
    var version by remember { mutableStateOf(0) } // after each change: the pages drawn again
    var organize by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) } // Back with changes not saved: Save or not
    var showVersions by remember { mutableStateOf(false) }
    // the old saves' copies of files as first opened (a save now goes onto the file itself): gone
    remember { runCatching { File(context.cacheDir, "pdf-base").deleteRecursively() } }
    fun change(what: () -> Unit) {
        runCatching(what).onFailure { WriterLog.e("PDF: change", it); Toast.makeText(context, pdfSays(context, it, R.string.pdf_could_not_do), Toast.LENGTH_LONG).show() }
        version++
    }
    /** Saved over the file, or into [to] (File › Save as, Save a copy: [copy] leaves the file itself unsaved). */
    fun save(to: File? = null, copy: Boolean = false, then: () -> Unit = {}) {
        val d = doc ?: return; val f = file ?: return; val t = to ?: f
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { if (t == f) keepPrevious(context, f); d.save(t, copy) } }
            r.onFailure { WriterLog.e("PDF: save", it); Toast.makeText(context, context.getString(R.string.pdf_could_not_save, pdfSays(context, it, R.string.pdf_could_not_do)), Toast.LENGTH_LONG).show() }
                .onSuccess { version++; Toast.makeText(context, if (t != f) context.getString(R.string.pdf_saved_as_kb, t.name, t.length() / 1024) else context.getString(R.string.pdf_saved_kb, t.length() / 1024), Toast.LENGTH_SHORT).show(); then() }
        }
    }
    var autoSave by remember { mutableStateOf(TextEditorStore(context).autoSave) } // File › AutoSave: Words' own setting, one for all
    var naming by remember { mutableStateOf<String?>(null) } // File › Save as ("as") or Rename ("rename")
    var about by remember { mutableStateOf(false) } // File › About this file
    var officeSettings by remember { mutableStateOf(false) }
    // Pages › Insert PDF (Combine): another PDF's pages after the one shown
    // Insert PDF: one or several, each after the one before, in the order chosen
    val pickPdf = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val d = doc ?: return@rememberLauncherForActivityResult
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val at0 = at
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching {
                val tmp = File(context.cacheDir, "pdf-insert.pdf")
                uris.fold(0) { n, uri -> context.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                    n + d.insertFile(at0 + 1 + n, tmp) }.also { tmp.delete() }
            } }
            r.onSuccess { n -> version++; Toast.makeText(context, context.resources.getQuantityString(R.plurals.pdf_pages_put_in, n, n, at0 + 1), Toast.LENGTH_SHORT).show() }
                .onFailure { WriterLog.e("PDF: insert", it); Toast.makeText(context, pdfSays(context, it, R.string.pdf_insert_failed), Toast.LENGTH_LONG).show() }
        }
    }
    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val d = doc ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        val page = at
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching {
                // a photo made a JPEG no wider than 1600 px (a camera's 12 MP would make the PDF huge)
                val raw = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: throw PdfError(R.string.pdf_not_a_picture)
                val k = minOf(1f, 1600f / bmp.width); val small = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
                val png = java.io.ByteArrayOutputStream().also { small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
                d.image(page, png, small.height.toFloat() / small.width)
            } }
            r.onSuccess { version++ }.onFailure { WriterLog.e("PDF: image", it); Toast.makeText(context, pdfSays(context, it, R.string.pdf_image_failed), Toast.LENGTH_LONG).show() }
        }
    }
    /** Typing on the page starts at [t]; [old]: a text put on before, typed in again with its look. */
    fun startTyping(t: PdfTyping, old: PdfText? = null) {
        chosen = null
        // a new text box starts with a border (as before), a new text with neither border nor fill
        textLook = old?.look ?: if (t.framed) textLook.copy(line = textLook.line ?: 0x000000) else textLook.copy(line = null, fill = null)
        val words = old?.words.orEmpty()
        typed.setText(words); old?.runs?.forEach { r -> if (r.to <= words.length) typed.addSpanStyle(r.style(), androidx.compose.ui.text.TextRange(r.from, r.to)) }
        typed.selection = androidx.compose.ui.text.TextRange(words.length); typing = t
    }
    /** A text put on before ([r] where it is), tapped: typed in again in its place. */
    fun retype(i: Int, r: android.graphics.RectF, old: PdfText) { val (uw, uh) = old.unturned(r)
        startTyping(PdfTyping(i, r.left, r.top, if (old.framed || old.turned) uw else maxOf(uw, (doc?.sizes?.getOrNull(i)?.width ?: 595f) - r.left - 18f),
            if (old.framed) uh else 0f, old.framed, Offset(r.centerX(), r.centerY())), old) }
    /** The words typed put on the page, one undo step (emptied: the old one only taken out). */
    fun putTyped() {
        val t = typing ?: return; typing = null; fontBefore = null
        val d = doc ?: return
        val words = typed.toText()
        if (words.isBlank() && t.replacing == null) return
        change { d.putText(t.page, t.x, t.y, PdfText(words.trimEnd(), textLook, t.framed, PdfRun.of(typed.annotatedString, words)), t.room, t.minHeight, t.replacing) }
        WriterLog.i("PDF: text ${if (t.replacing != null) "changed" else "put on"} (${textLook.font}, ${textLook.size} pt)")
        // it stays chosen, as a Slides box after typing: moved, sized, restyled from there
        if (words.isNotBlank()) chosen = d.typedAt(t.page, Offset(t.x + 1f, t.y + 1f))?.let { (r, pt) -> Triple(t.page, r, pt) }
    }
    /**
     * The chosen text put on again, one undo step: at [x], [y], [room] across and at least [minH] tall, as [t] — it stays
     * chosen. A text box keeps its width; a text is as wide as its words up to the page's edge.
     */
    fun rechange(x: Float? = null, y: Float? = null, room: Float? = null, minH: Float? = null, t: PdfText? = null) {
        val (i, r, old) = chosen ?: return; val d = doc ?: return
        val s = d.sizes.getOrNull(i) ?: return; val nt = t ?: old
        val (uw, uh) = old.unturned(r) // as laid out before turning
        val nx = (x ?: r.left).coerceIn(0f, maxOf(0f, s.width - 12f)); val ny = (y ?: r.top).coerceIn(0f, maxOf(0f, s.height - 8f))
        change { d.putText(i, nx, ny, nt, room ?: if (nt.framed || nt.turned) uw else maxOf(uw, s.width - nx - 18f), minH ?: if (nt.framed) uh else 0f, Offset(r.centerX(), r.centerY())) }
        chosen = d.typedAt(i, Offset(nx + 1f, ny + 1f))?.let { (nr, pt) -> Triple(i, nr, pt) }
    }
    /** The chosen text stretched by Words' handles to [lw] × [lh] before turning: a fixed width from then on (a text becomes a box without a border). */
    fun sizeChosen(lw: Float, lh: Float) {
        val t = chosen?.third ?: return
        rechange(room = lw.coerceAtLeast(12f), minH = lh.coerceAtLeast(8f), t = t.copy(framed = true))
    }
    /** The arrows' nudge put on where they have got to (one step of Undo for a whole press). */
    fun nudged() { val sh = framePreview.shift; framePreview.reset(); if (sh != Offset.Zero) chosen?.second?.let { r -> rechange(x = r.left + sh.x, y = r.top + sh.y) } }
    /** The chosen text turned to [to] degrees clockwise about its middle (one undo step). */
    fun turnChosen(to: Int) {
        val (_, r, old) = chosen ?: return; val (lw, lh) = old.unturned(r)
        val nt = old.copy(look = old.look.copy(turn = Math.floorMod(to, 360))); val (bw, bh) = PdfText.bounds(nt, lw, lh)
        rechange(x = r.centerX() - bw / 2, y = r.centerY() - bh / 2, t = nt)
        WriterLog.i("PDF: text turned ${nt.look.turn}°")
    }
    /** The chosen text taken off the page (one undo step). */
    fun deleteChosen() {
        val (i, r, old) = chosen ?: return; chosen = null
        doc?.let { d -> change { d.putText(i, r.left, r.top, old.copy(words = ""), r.width(), 0f, Offset(r.centerX(), r.centerY())) } }
        WriterLog.i("PDF: text deleted")
    }
    /** Word's Align on the page for the chosen text: at the margin it is put by (Layout › Margins), or the middle. */
    fun position(across: Char?, down: Char?) {
        val (i, r, _) = chosen ?: return; val s = doc?.sizes?.getOrNull(i) ?: return; val m = margins
        rechange(x = when (across) { 'l' -> m.left; 'c' -> (s.width - r.width()) / 2; 'r' -> s.width - m.right - r.width(); else -> null },
            y = when (down) { 't' -> m.top; 'm' -> (s.height - r.height()) / 2; 'b' -> s.height - m.bottom - r.height(); else -> null })
        WriterLog.i("PDF: text positioned ${across ?: ""}${down ?: ""}")
    }
    /** Copy (Cut): the chosen text kept for Paste, as Slides' items; its words on the clipboard for other apps. */
    fun copyChosen(): Boolean {
        val (_, r, t) = chosen ?: return false
        clip = android.graphics.RectF(r) to t
        runCatching { context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("PDF text", t.words)) }
        WriterLog.i("PDF: text copied"); return true
    }
    /**
     * Paste on page [i]: what was copied here, a little lower and to the right of where it stood (Slides' way); else
     * words copied in another app, a new text in the look last used, 2 cm in from the top left.
     */
    fun paste(i: Int, at: Offset? = null) {
        val d = doc ?: return; val s = d.sizes.getOrNull(i) ?: return; val m = margins.left; val mt = margins.top
        val t: PdfText; val x: Float; val y: Float; val room: Float; val minH: Float
        val c = clip
        if (c != null) {
            val (r, ct) = c; val (uw, uh) = ct.unturned(r)
            t = ct; x = at?.x ?: (r.left + 12f); y = at?.y ?: (r.top + 12f); room = if (ct.framed || ct.turned) uw else maxOf(uw, s.width - x - 18f); minH = if (ct.framed) uh else 0f
        } else {
            val words = runCatching { context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()?.trim()
            if (words.isNullOrEmpty()) { Toast.makeText(context, R.string.pdf_nothing_to_paste, Toast.LENGTH_SHORT).show(); return }
            t = PdfText(words, textLook.copy(fill = null, line = null, turn = 0), false); x = at?.x ?: m; y = at?.y ?: mt; room = maxOf(72f, s.width - x - margins.right); minH = 0f
        }
        val px = x.coerceAtMost(s.width - 12f); val py = y.coerceAtMost(s.height - 8f)
        change { d.putText(i, px, py, t, room, minH) }
        chosen = d.typedAt(i, Offset(px + 1f, py + 1f))?.let { (nr, pt) -> Triple(i, nr, pt) }
        WriterLog.i("PDF: text pasted")
    }
    /** Duplicate (Ctrl+D): a copy of the chosen text beside it, Copy's text and the clipboard left as they were. */
    fun duplicateChosen() { val (i, r, t) = chosen ?: return; val keep = clip; clip = android.graphics.RectF(r) to t; paste(i); clip = keep }
    /** The chosen text one step forward or backward among the page's comments, pictures and drawings ([to] as [PdfDoc.restack]). */
    fun restackChosen(to: Int) {
        val (i, r, _) = chosen ?: return; val d = doc ?: return
        var moved = false
        change { moved = d.restack(i, Offset(r.centerX(), r.centerY()), to) }
        if (!moved) Toast.makeText(context, if (to > 0) R.string.pdf_in_front_already else R.string.pdf_at_back_already, Toast.LENGTH_SHORT).show()
    }
    BackHandler(enabled = editing || organize) { if (organize) organize = false else if (typing != null) putTyped() else if (chosen != null) chosen = null else if (tool != null) tool = null else if (doc?.dirty == true) leaving = true else editing = false }
    /** What a finger did on page [i] with the tool on: [pts] its path in page points (a tap: one point). */
    fun onPage(i: Int, pts: List<Offset>) {
        if (typing != null) { putTyped(); return } // a tap off the words being typed puts them on, as Words' click outside a box
        chosen = null // a tap off the chosen text: none chosen (on it, its frame takes the tap)
        val d = doc ?: return; val t = tool ?: run {
            if (pts.size != 1) return
            // a text put on before, tapped while editing: chosen, as a Slides box (a second tap types in it)
            if (editing) d.typedAt(i, pts[0])?.let { (r, old) -> chosen = Triple(i, r, old); return }
            // no tool: a form's field is filled in (a box ticked at once), a note tapped shows its words
            d.fieldAt(i, pts[0])?.let { f ->
                if (f.readOnly) { Toast.makeText(context, R.string.pdf_field_read_only, Toast.LENGTH_SHORT).show(); return }
                editing = true
                when (f.kind) {
                    "check", "radio" -> change { d.fill(i, pts[0], null) }
                    "text", "choice" -> filling = Triple(i, pts[0], f)
                }
                return
            }
            d.noteAt(i, pts[0])?.let { reading = it.ifBlank { context.getString(R.string.pdf_empty_note) } }; return }
        val a = pts.first(); val b = pts.last()
        when (t) {
            PdfTool.RedactText -> change {
                // the words a finger crossed, marked to be taken out (the user, 22 Oct: "cant we do select and reduct?")
                if (!d.markText(i, a, b, com.artifex.mupdf.fitz.PDFAnnotation.TYPE_REDACT, 0x000000))
                    Toast.makeText(context, R.string.pdf_no_words_redact, Toast.LENGTH_SHORT).show()
            }
            PdfTool.Highlight, PdfTool.Underline, PdfTool.Strike -> change {
                val type = when (t) { PdfTool.Underline -> com.artifex.mupdf.fitz.PDFAnnotation.TYPE_UNDERLINE; PdfTool.Strike -> com.artifex.mupdf.fitz.PDFAnnotation.TYPE_STRIKE_OUT
                    else -> com.artifex.mupdf.fitz.PDFAnnotation.TYPE_HIGHLIGHT }
                if (!d.markText(i, a, b, type, if (t == PdfTool.Highlight) markColour else if (markColour == 0xFFEB3B) 0xE53935 else markColour))
                    Toast.makeText(context, R.string.pdf_no_words_mark, Toast.LENGTH_SHORT).show()
            }
            PdfTool.Note -> writing = i to a
            PdfTool.Redact -> if ((b - a).getDistance() > 4f) change { d.markRedaction(i, a, b) }
            PdfTool.TextBox -> if ((b - a).getDistance() > 10f) startTyping(PdfTyping(i, minOf(a.x, b.x), minOf(a.y, b.y), kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y), true))
            PdfTool.TextField -> if ((b - a).getDistance() > 10f) change { d.addField(i, a, b, checkbox = false) }
            PdfTool.CheckBox -> change { d.addField(i, a - Offset(7f, 7f), a + Offset(7f, 7f), checkbox = true) }
            PdfTool.Stamp -> change {
                val pic = stampPicture
                if (stampName == PICTURE_STAMP && pic != null) d.picture(i, a - Offset(70f, 70f * pic.second / 2), pic.first, 140f, pic.second) else d.stampAt(i, a, stampName)
            }
            PdfTool.Rectangle, PdfTool.Oval, PdfTool.Arrow -> if ((b - a).getDistance() > 4f) change {
                d.shape(i, when (t) { PdfTool.Oval -> com.artifex.mupdf.fitz.PDFAnnotation.TYPE_CIRCLE; PdfTool.Arrow -> com.artifex.mupdf.fitz.PDFAnnotation.TYPE_LINE
                    else -> com.artifex.mupdf.fitz.PDFAnnotation.TYPE_SQUARE }, a, b, if (markColour == 0xFFEB3B) 0xE53935 else markColour, 2f)
            }
            PdfTool.Pen -> if (pts.size > 1) change { d.ink(i, listOf(pts), penColour, penWidth) }
            PdfTool.Marker -> if (pts.size > 1) change { d.ink(i, listOf(pts), 0xFFEB3B, 12f) }
            PdfTool.Erase -> change { if (!d.deleteAt(i, a)) Toast.makeText(context, R.string.pdf_no_comment_there, Toast.LENGTH_SHORT).show() }
            // the words start where the finger was (its line through the tap), as far across as the page leaves room
            PdfTool.Text -> d.typedAt(i, a)?.let { (r, old) -> retype(i, r, old) }
                ?: startTyping(PdfTyping(i, a.x, a.y - textLook.size * 0.8f, (d.sizes[i].width - a.x - minOf(margins.right, d.sizes[i].width - a.x - 72f)).coerceAtLeast(72f), 0f, false))
            PdfTool.Tick -> change { d.mark(i, a, '✓', fillSize * 1.3f, 0x000000) }
            PdfTool.Cross -> change { d.mark(i, a, '✗', fillSize * 1.3f, 0x000000) }
            PdfTool.Dot -> change { d.mark(i, a, '•', fillSize * 1.3f, 0x000000) }
            PdfTool.Date -> change { d.text(i, a - Offset(0f, fillSize * 0.6f), java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), fillSize, 0x000000) }
            PdfTool.Sign -> if (!signatureFile.isFile) signing = true else change {
                val b = signatureFile.readBytes(); val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size, o)
                val w = 140f; d.picture(i, a - Offset(w / 2, w * o.outHeight / o.outWidth.coerceAtLeast(1) / 2), b, w, o.outHeight.toFloat() / o.outWidth.coerceAtLeast(1))
            }
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
            open = { k -> organize = false; scope.launch { delay(50); goTo(k) } }, back = { organize = false }, title = stringResource(R.string.pdf_organize),
            actions = { RibbonButton(stringResource(R.string.common_done), MaterialTheme.colorScheme.onBackground, R.drawable.ic_check) {
                if (order != d1.sizes.indices.toList()) change { d1.move(order) }
                organize = false
            } }) { k, m -> PdfPage(d1, order[k], with(density) { 160.dp.roundToPx() }, m.background(Color.White)) }
        return
    }
    leaving.takeIf { it }?.let {
        androidx.compose.material3.AlertDialog(onDismissRequest = { leaving = false }, title = { Text(stringResource(R.string.pdf_save_changes)) },
            text = { Text(stringResource(R.string.pdf_stays_in_versions)) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { leaving = false; save { editing = false } }) { Text(stringResource(R.string.common_save), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            dismissButton = { Row {
                androidx.compose.material3.TextButton(onClick = {
                    leaving = false; editing = false
                    // not saved: the file read again as it is
                    val f = file; doc?.close(); doc = null
                    if (f != null) scope.launch { withContext(Dispatchers.IO) { runCatching { PdfDoc(f, password) } }.onSuccess { doc = it; version++ } }
                }) { Text(stringResource(R.string.words_dont_save), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                androidx.compose.material3.TextButton(onClick = { leaving = false }) { Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } })
    }
    writing?.let { (i, at0) ->
        var words by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(onDismissRequest = { writing = null }, title = { Text(stringResource(R.string.pdf_note)) },
            text = { androidx.compose.material3.OutlinedTextField(words, { words = it }, Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text(stringResource(R.string.pdf_note_hint)) }) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { writing = null; doc?.let { d -> change { d.note(i, at0, words.trim(), markColour) } } }) { Text(stringResource(R.string.common_add), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { writing = null }) { Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } })
    }
    // the signature: drawn once in Words' drawing box, kept in the app (never in a file), put where Sign is tapped
    if (signing) DrawDialog(200f, { signing = false }, boxAspect = 0.4f) { png, _, _, _, _ ->
        signing = false
        runCatching { signatureFile.writeBytes(png) }.onSuccess { tool = PdfTool.Sign; Toast.makeText(context, R.string.pdf_signature_kept, Toast.LENGTH_LONG).show() }
    }
    filling?.let { (i, at0, f) ->
        var value by remember { mutableStateOf(f.value) }
        androidx.compose.material3.AlertDialog(onDismissRequest = { filling = null }, title = { Text(f.name.ifBlank { stringResource(R.string.pdf_field) }) },
            text = {
                if (f.kind == "choice") Column { f.options.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { value = o }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(value == o, { value = o }); Text(o)
                    } } }
                else androidx.compose.material3.OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), singleLine = !f.multiline, minLines = if (f.multiline) 3 else 1)
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { filling = null; doc?.let { d -> change { d.fill(i, at0, value) } } }) { Text(stringResource(R.string.common_ok), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { filling = null }) { Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } })
    }
    when (asking) {
        "header" -> {
            var head by remember { mutableStateOf("") }; var foot by remember { mutableStateOf("") }
            androidx.compose.material3.AlertDialog(onDismissRequest = { asking = null }, title = { Text(stringResource(R.string.words_header_footer)) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(head, { head = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.words_header)) }, singleLine = true)
                    androidx.compose.material3.OutlinedTextField(foot, { foot = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.words_footer)) }, singleLine = true)
                    Text(stringResource(R.string.pdf_header_hint), style = MaterialTheme.typography.bodySmall)
                } },
                confirmButton = { androidx.compose.material3.TextButton(enabled = head.isNotBlank() || foot.isNotBlank(), onClick = { asking = null
                    doc?.let { d -> change { d.headerFooter(head.trim(), foot.trim(), 10f) } } }) { Text(stringResource(R.string.pdf_put_in), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = null }) { Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } })
        }
        "redact" -> {
            var words by remember { mutableStateOf("") }
            var pattern by remember { mutableStateOf<Int?>(null) }
            androidx.compose.material3.AlertDialog(onDismissRequest = { asking = null }, title = { Text(stringResource(R.string.pdf_find_redact)) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.pdf_find_redact_about), style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.OutlinedTextField(words, { words = it; if (it.isNotEmpty()) pattern = null }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.pdf_words)) }, singleLine = true)
                    Text(stringResource(R.string.pdf_or_every), style = MaterialTheme.typography.labelLarge)
                    REDACT_PATTERNS.forEachIndexed { k, (label, _) ->
                        Row(Modifier.fillMaxWidth().clickable { pattern = k; words = "" }, verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(pattern == k, { pattern = k; words = "" }); Text(stringResource(label))
                        }
                    }
                } },
                confirmButton = { androidx.compose.material3.TextButton(enabled = words.isNotBlank() || pattern != null, onClick = { asking = null
                    val d = doc ?: return@TextButton
                    var n = 0
                    change { n = d.markAll(words.trim().ifEmpty { null }, pattern?.let { REDACT_PATTERNS[it].second }) }
                    Toast.makeText(context, if (n == 0) context.getString(R.string.pdf_redact_not_found) else context.resources.getQuantityString(R.plurals.pdf_marked, n, n), Toast.LENGTH_LONG).show()
                }) { Text(stringResource(R.string.pdf_mark), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = null }) { Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } })
        }
        "bookmark" -> {
            var title by remember { mutableStateOf(context.getString(R.string.pdf_page_n, at + 1)) }
            androidx.compose.material3.AlertDialog(onDismissRequest = { asking = null }, title = { Text(stringResource(R.string.pdf_bookmark_page, at + 1)) },
                text = { androidx.compose.material3.OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.words_title)) }, singleLine = true) },
                confirmButton = { androidx.compose.material3.TextButton(enabled = title.isNotBlank(), onClick = { asking = null; val pg = at
                    doc?.let { d -> editing = true; change { d.addBookmark(title.trim(), pg) } } }) { Text(stringResource(R.string.common_add), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = null }) { Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } })
        }
        "watermark" -> {
            var words by remember { mutableStateOf("ΠΡΟΣΧΕΔΙΟ") }
            androidx.compose.material3.AlertDialog(onDismissRequest = { asking = null }, title = { Text(stringResource(R.string.words_watermark)) },
                text = { androidx.compose.material3.OutlinedTextField(words, { words = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.pdf_watermark_words)) }, singleLine = true) },
                confirmButton = { androidx.compose.material3.TextButton(enabled = words.isNotBlank(), onClick = { asking = null
                    val (png, aspect) = watermarkPng(words.trim())
                    doc?.let { d -> change { d.watermark(png, aspect) } } }) { Text(stringResource(R.string.pdf_put_in), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = null }) { Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } })
        }
        "password" -> {
            var pw by remember { mutableStateOf("") }; var again by remember { mutableStateOf("") }
            androidx.compose.material3.AlertDialog(onDismissRequest = { asking = null }, title = { Text(stringResource(R.string.pdf_password)) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.pdf_password_about), style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.pdf_password)) }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    androidx.compose.material3.OutlinedTextField(again, { again = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.pdf_password_again)) }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), isError = again.isNotEmpty() && again != pw)
                } },
                confirmButton = { androidx.compose.material3.TextButton(enabled = pw.length >= 4 && pw == again, onClick = { asking = null
                    doc?.protect(pw); version++; Toast.makeText(context, R.string.pdf_password_on_save, Toast.LENGTH_LONG).show() }) { Text(stringResource(R.string.common_ok), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = null }) { Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } })
        }
    }
    reading2?.let { (k, n) ->
        androidx.compose.material3.AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.pdf_recognizing)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.pdf_recognizing_page, k, n))
                androidx.compose.material3.LinearProgressIndicator(progress = { (k - 1f) / n }, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { ocrJob?.cancel(); reading2 = null }) { Text(stringResource(R.string.common_stop), maxLines = 1, overflow = TextOverflow.Ellipsis) } })
    }
    reading?.let { words ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { reading = null }, title = { Text(stringResource(R.string.pdf_note)) }, text = { Text(words) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { reading = null }) { Text(stringResource(R.string.common_close), maxLines = 1, overflow = TextOverflow.Ellipsis) } })
    }
    // File › AutoSave (Words' own): a few seconds after the last change, saved
    LaunchedEffect(version, autoSave, editing, typing == null) { if (autoSave && editing && typing == null && doc?.dirty == true) { delay(4000); save() } }
    if (officeSettings) OfficeSettingsDialog { officeSettings = false }
    // File › Save as and Rename (Words' and Slides'): beside it, a PDF; Save as goes on with the new one
    naming?.let { how -> file?.let { f ->
        var typedName by remember { mutableStateOf(if (how == "as") "${f.nameWithoutExtension} (2)" else f.nameWithoutExtension) }
        androidx.compose.material3.AlertDialog(onDismissRequest = { naming = null }, title = { Text(stringResource(if (how == "as") R.string.words_save_as else R.string.common_rename)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(typedName, { typedName = it }, singleLine = true, label = { Text(stringResource(R.string.words_file_name)) }, suffix = { Text(".pdf") })
                if (how == "as") Text(stringResource(R.string.pdf_save_as_beside, f.name), style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                val t = typedName.trim().removeSuffix(".pdf").takeIf { it.isNotEmpty() && '/' !in it }?.let { File(f.parentFile, "$it.pdf") }
                    ?: run { Toast.makeText(context, R.string.words_not_a_file_name, Toast.LENGTH_SHORT).show(); return@TextButton }
                if (t == f) { naming = null; return@TextButton }
                if (t.exists()) { Toast.makeText(context, context.getString(R.string.words_name_taken, t.name), Toast.LENGTH_LONG).show(); return@TextButton }
                naming = null
                if (how == "as") save(to = t) { file = t; WriterLog.i("PDF: saved as") }
                else scope.launch {
                    // the file and its versions under the new name (as Words and Slides rename)
                    val ok = withContext(Dispatchers.IO) { runCatching { val v = versionsDir(context, f)
                        check(f.renameTo(t)) { "Could not rename ${f.name}." }; if (v.isDirectory) v.renameTo(versionsDir(context, t)) }.onFailure { WriterLog.e("PDF: rename", it) }.isSuccess }
                    if (ok) { file = t; Toast.makeText(context, context.getString(R.string.words_renamed, t.name), Toast.LENGTH_SHORT).show() } else Toast.makeText(context, R.string.words_could_not_rename, Toast.LENGTH_LONG).show()
                }
            }) { Text(stringResource(if (how == "as") R.string.common_save else R.string.common_rename), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { naming = null }) { Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } })
    } }
    // File › About this file (as Words' and Slides'): its kind, size, date, pages and their size, its own details
    if (about) androidx.compose.material3.AlertDialog(onDismissRequest = { about = false },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { about = false }) { Text(stringResource(R.string.common_close), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        title = { Text(file?.name ?: stringResource(R.string.app_pdf)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val d = doc
            Text(d?.info("format")?.let { stringResource(R.string.pdf_document_format, it) } ?: stringResource(R.string.pdf_document))
            file?.let { Text(stringResource(R.string.pdf_size_date, "%.1f".format(it.length() / 1024.0 / 1024.0), java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.lastModified())))) }
            d?.sizes?.let { ss -> val s0 = ss.firstOrNull()
                Text(listOfNotNull(pluralStringResource(R.plurals.pdf_pages, ss.size, ss.size), s0?.let { PdfPaper.name(it, paperWay, unitName) }).joinToString(" · ") +
                    if (ss.distinct().size > 1) " " + stringResource(R.string.pdf_not_all_same_size) else "") }
            listOf(R.string.pdf_meta_title to com.artifex.mupdf.fitz.Document.META_INFO_TITLE, R.string.pdf_meta_author to com.artifex.mupdf.fitz.Document.META_INFO_AUTHOR,
                R.string.pdf_meta_creator to com.artifex.mupdf.fitz.Document.META_INFO_CREATOR, R.string.pdf_meta_producer to com.artifex.mupdf.fitz.Document.META_INFO_PRODUCER)
                .forEach { (label, key) -> d?.info(key)?.let { Text(stringResource(label, it), style = MaterialTheme.typography.bodySmall) } }
        } })
    if (showVersions) file?.let { f -> VersionsDialog(f, "", { showVersions = false }, about = stringResource(R.string.pdf_versions_about)) { v ->
        showVersions = false
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { keepPrevious(context, f); doc?.close(); v.copyTo(f, overwrite = true) } }
            withContext(Dispatchers.IO) { runCatching { PdfDoc(f, password) } }.onSuccess { doc = it; version++ }
            Toast.makeText(context, R.string.pdf_version_back, Toast.LENGTH_SHORT).show()
        }
    } }
    // a keyboard's shortcuts, as Slides' (asked 24 Oct: "look how the other applets work"): the screen holds the keys while nothing is typed in
    val keys = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(editing, typing == null) { if (typing == null) runCatching { keys.requestFocus() } }
    Column(Modifier.fillMaxSize().background(Color(0xFF2B2B2B)).focusRequester(keys).focusable().onPreviewKeyEvent { k ->
        if (k.type != KeyEventType.KeyDown || typing != null || !editing) return@onPreviewKeyEvent false
        val d = doc ?: return@onPreviewKeyEvent false
        val c = chosen; val step = if (k.isShiftPressed) 10f else 1f // points
        when {
            k.isCtrlPressed && k.key == Key.S -> { if (d.dirty) save(); true }
            k.isCtrlPressed && (k.key == Key.Y || (k.key == Key.Z && k.isShiftPressed)) -> { chosen = null; change { d.redo() }; true }
            k.isCtrlPressed && k.key == Key.Z -> { chosen = null; change { d.undo() }; true }
            k.isCtrlPressed && k.key == Key.V -> { paste(c?.first ?: at); true }
            c == null -> false
            k.isCtrlPressed && k.key == Key.C -> { copyChosen(); true }
            k.isCtrlPressed && k.key == Key.X -> { if (copyChosen()) deleteChosen(); true }
            k.isCtrlPressed && k.key == Key.D -> { duplicateChosen(); true }
            k.key == Key.Delete || k.key == Key.Backspace -> { deleteChosen(); true }
            k.key == Key.Enter -> { retype(c.first, c.second, c.third); true }
            k.key == Key.Escape -> { chosen = null; true }
            k.key == Key.DirectionLeft -> { rechange(x = c.second.left - step); true }
            k.key == Key.DirectionRight -> { rechange(x = c.second.left + step); true }
            k.key == Key.DirectionUp -> { rechange(y = c.second.top - step); true }
            k.key == Key.DirectionDown -> { rechange(y = c.second.top + step); true }
            else -> false
        }
    }) {
        Surface(color = islandColor(), contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.common_back)) }
                    Column(Modifier.weight(1f)) {
                        Text(file?.name ?: stringResource(R.string.app_pdf), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        doc?.let { Text(pluralStringResource(R.plurals.pdf_pages, it.sizes.size, it.sizes.size) + " · " + stringResource(if (editing) if (version >= 0 && it.dirty) R.string.words_not_saved else R.string.pdf_editing else R.string.pdf_view_only), style = MaterialTheme.typography.labelMedium, color = islandContentColor().copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    val d0 = doc
                    if (editing && d0 != null) {
                        key(version) {
                            // words being typed on the page: put on first (Undo then takes them off, as any change)
                            Tool(R.drawable.ic_undo, stringResource(R.string.common_undo), islandContentColor(), enabled = d0.canUndo || typing != null) { putTyped(); change { d0.undo() } }
                            Tool(R.drawable.ic_redo, stringResource(R.string.common_redo), islandContentColor(), enabled = d0.canRedo) { putTyped(); change { d0.redo() } }
                            Tool(R.drawable.ic_save, stringResource(R.string.common_save), islandContentColor(), enabled = d0.dirty || typing != null) { putTyped(); save() }
                        }
                        Tool(R.drawable.ic_check, stringResource(R.string.common_done), islandContentColor()) { putTyped(); if (d0.dirty) leaving = true else editing = false }
                    } else {
                    // a phone: the pencil alone, as Tables' and Slides' (a longer word, "Επεξεργασία", left the file's name no room)
                    if (d0?.editable == true) { if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) Tool(R.drawable.ic_edit, stringResource(R.string.common_edit), islandContentColor()) { editing = true; WriterLog.i("PDF: edit") }
                        else RibbonButton(stringResource(R.string.common_edit), islandContentColor(), R.drawable.ic_edit) { editing = true; WriterLog.i("PDF: edit") } }
                    if (Pdf.hasText && doc != null) IconButton(onClick = { finding = !finding; if (!finding) matches = null }) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.office_find), tint = if (finding) PDF_RED else islandContentColor()) }
                    FavouriteHeart(file, islandContentColor()) // a tablet or bigger: the Office home's Favourites (the user, 18 Oct)
                    val fav = rememberOfficeFavourite(file)?.takeIf { !favouriteInTopBar() } // a phone: in ⋯
                    file?.let { f ->
                        IconButton(onClick = { shareDriveFile(context, f) }) { Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.common_share)) }
                        IconButton(onClick = ::print, enabled = doc != null) { Icon(painterResource(R.drawable.ic_print), contentDescription = stringResource(R.string.common_print)) }
                        // the rest behind ⋯, so a phone's bar keeps room for the name
                        var more by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { more = true }) { Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.words_more_tools)) }
                            // black with round corners, as the islands (the user, 22 Oct: "a nice black menu with rounded corners")
                            DropdownMenu(more, { more = false }, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), containerColor = Color(0xFF151515),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))) {
                                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
                                if (Pdf.hasText && doc != null) DropdownMenuItem({ Text(stringResource(R.string.pdf_copy_page_text)) }, { more = false; copyPage() },
                                    colors = PDF_MENU_ITEM(), leadingIcon = { Icon(painterResource(R.drawable.ic_copy), contentDescription = null) })
                                DropdownMenuItem({ Text(stringResource(R.string.words_open_in_another_app)) }, { more = false; openElsewhere(context, f) },
                                    colors = PDF_MENU_ITEM(), leadingIcon = { Icon(painterResource(R.drawable.ic_open_with), contentDescription = null) })
                                fav?.let { h -> DropdownMenuItem({ Text(stringResource(if (h.on) R.string.office_favourite_remove else R.string.office_favourite_add)) }, { more = false; h.toggle() },
                                    colors = PDF_MENU_ITEM(), leadingIcon = { Icon(painterResource(if (h.on) R.drawable.ic_favorite else R.drawable.ic_favorite_border), contentDescription = null) }) }
                                if (doc != null) {
                                    DropdownMenuItem({ Text(stringResource(R.string.words_comments)) }, { more = false; pane = "Comments" }, colors = PDF_MENU_ITEM(), leadingIcon = { Icon(painterResource(R.drawable.ic_comment), contentDescription = null) })
                                    DropdownMenuItem({ Text(stringResource(R.string.pdf_bookmarks)) }, { more = false; pane = "Bookmarks" }, colors = PDF_MENU_ITEM(), leadingIcon = { Icon(painterResource(R.drawable.ic_bookmark), contentDescription = null) })
                                }
                                if (doc != null) {
                                    DropdownMenuItem({ Text(stringResource(R.string.pdf_edit_in_words)) }, { more = false; exportWords() }, colors = PDF_MENU_ITEM(), leadingIcon = { Icon(painterResource(R.drawable.ic_edit_note), contentDescription = null) })
                                    DropdownMenuItem({ Text(stringResource(R.string.pdf_export_text)) }, { more = false; exportText() }, colors = PDF_MENU_ITEM(), leadingIcon = { Icon(painterResource(R.drawable.ic_text_fields), contentDescription = null) })
                                }
                                if (versions(context, f).isNotEmpty()) DropdownMenuItem({ Text(stringResource(R.string.words_versions)) }, { more = false; showVersions = true },
                                    colors = PDF_MENU_ITEM(), leadingIcon = { Icon(painterResource(R.drawable.ic_history), contentDescription = null) })
                                }
                            }
                        }
                    }
                    }
                }
                if (finding) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    Row(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(query, { query = it; matches = null }, Modifier.weight(1f).focusRequester(focus), singleLine = true, placeholder = { Text(stringResource(R.string.pdf_find_hint)) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { find() }),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF3A3A3A), unfocusedContainerColor = Color(0xFF3A3A3A)))
                        Text(when { searching -> stringResource(R.string.pdf_looking); matches == null -> ""; matches!!.isEmpty() -> stringResource(R.string.common_none); else -> stringResource(R.string.pdf_n_of_m, match + 1, matches!!.size) },
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp))
                        IconButton(onClick = { step(-1) }, enabled = !matches.isNullOrEmpty()) { Icon(painterResource(R.drawable.ic_keyboard_arrow_up), contentDescription = stringResource(R.string.common_previous)) }
                        IconButton(onClick = { if (matches == null) find() else step(1) }, enabled = query.isNotBlank()) { Icon(painterResource(R.drawable.ic_keyboard_arrow_down), contentDescription = stringResource(R.string.common_next)) }
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val d = doc
            when {
                failed != null -> Text(stringResource(failed!!), color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                d == null -> Text(stringResource(R.string.words_opening), color = Color.LightGray, modifier = Modifier.align(Alignment.Center))
                d.sizes.isEmpty() -> Text(stringResource(R.string.pdf_no_pages), color = Color.White, modifier = Modifier.align(Alignment.Center))
                else -> key(version) { Row(Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    // a tablet: the pages small at the side, the one shown marked, tapped to go to it; a phone: the same, narrower, when asked for
                    if (pane != null && wide) PdfPane(d, pane!!, editing, version, Modifier.width(300.dp).fillMaxHeight(), { goTo(it) }, { pane = null }, { asking = "bookmark" }, { editing = true }) { act -> change(act) }
                    else if ((wide && navigation) || (!wide && pages)) {
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
                                if (ev.changes.count { it.pressed } >= 2 || (zoom > 1f && tool == null && chosen == null)) {
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
                        // Words' rulers on a tablet (one page across, not zoomed in): along the top for the page in view, down each page's side
                        val ruled = rulers && wide && editing && across == 1 && zoom == 1f
                        val rulerW = 18.dp; val sideRuler = if (ruled) rulerW + 12.dp else 0.dp // the side ruler and the row's gap after it
                        val roomW = maxWidth - 24.dp; val roomH = maxHeight - 24.dp - (if (ruled) 22.dp else 0.dp)
                        // the page shown on the screen: as wide as there is room (Fit width: all of it), no taller than the screen
                        fun widthOf(s: Size) = ((roomW - sideRuler - 12.dp * (across - 1)) / across).let { room -> if (fitWidth) room else minOf(room, roomH * (s.width / s.height)) }
                        fun pageOf(s: Size) = DocPage(s.width, s.height, margins.top, margins.bottom, margins.left, margins.right)
                        Column(Modifier.fillMaxSize()) {
                        if (ruled) d.sizes.getOrNull(at)?.let { s -> val w = widthOf(s)
                            HorizontalRuler(pageOf(s), w / s.width, 12.dp + (roomW - (sideRuler + w)) / 2 + sideRuler, Modifier.fillMaxWidth().height(22.dp)) }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }, state = list,
                            userScrollEnabled = zoom == 1f && tool == null,
                            // a page at a time, as Slides' list; Fit width's pages are taller than the screen: they scroll freely
                            flingBehavior = if (fitWidth) androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior() else androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(oneAtATime),
                            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // View › Two pages: a row of two, as Words'
                            items((d.sizes.size + across - 1) / across) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                              for (i in row * across until minOf(d.sizes.size, row * across + across)) {
                                val s = d.sizes[i]
                                val w = widthOf(s)
                                val px = with(density) { w.roundToPx() }
                                if (ruled) VerticalRuler(pageOf(s), w / s.width, Modifier.width(rulerW).height(w * (s.height / s.width)))
                                val marks = matches?.filter { it.page == i }.orEmpty()
                                PdfPage(d, i, if (i == at) Pdf.renderWidth(px, settled, s.width / s.height) else px,
                                    Modifier.width(w)
                                        // a mouse's right button (as Slides'): the text under it chosen, and its menu; before the taps see it
                                        .pointerInput(editing, i) {
                                            if (!editing) return@pointerInput
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                                if (!currentEvent.buttons.isSecondaryPressed) return@awaitEachGesture
                                                down.consume()
                                                if (typing != null) putTyped()
                                                val pt = down.position / (size.width / s.width)
                                                chosen = doc?.typedAt(i, pt)?.let { (r, t) -> Triple(i, r, t) }
                                                pageMenu = Triple(i, down.position, pt)
                                            }
                                        }
                                        // a tap anywhere lets go of a chosen film (one on a film chooses it again, after this); a drag does not
                                        .pointerInput(editing) { if (editing) awaitEachGesture { val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                            waitForUpOrCancellation(PointerEventPass.Initial)?.takeIf { (it.position - down.position).getDistance() < viewConfiguration.touchSlop }?.let { media.chosen = null } } }
                                        // a text on this page as a text box in Words: pressed and dragged it moves at once, a tap chooses it, a tap on the chosen one types in it
                                        .pdfTextGestures(s, editing && typing == null && (tool == null || tool == PdfTool.Text || tool == PdfTool.TextBox),
                                            { pt -> doc?.typedAt(i, pt) != null },
                                            { chosen?.takeIf { it.first == i }?.let { (_, r, t) -> Triple(r, t.unturned(r), t.look.turn) } }, framePreview,
                                            { pt -> doc?.typedAt(i, pt)?.let { (r, t) -> chosen = Triple(i, r, t); tool = null } },
                                            { chosen?.let { (ci, r, t) -> retype(ci, r, t) } },
                                            { dx, dy -> chosen?.second?.let { r -> rechange(x = r.left + dx, y = r.top + dy) } }), marks, matches?.getOrNull(match)?.takeIf { it.page == i }) {
                                    if (editing || d.hasForm) PdfDrawLayer(s, tool, if (tool == PdfTool.Pen) penColour else if (tool == PdfTool.Marker) 0xFFEB3B else markColour) { pts -> onPage(i, pts) }
                                    if (tool == null && typing == null) PdfMediaLayer(d, i, s, editing, media, version) { sp, dx, dy -> change { d.moveMedia(i, sp.index, dx, dy) } } // over a form's layer: only its films' boxes take taps
                                    typing?.takeIf { it.page == i }?.let { tp -> PdfTypingField(s, tp, typed, textLook) }
                                    pageMenu?.takeIf { it.first == i }?.let { (_, spot, pt) -> OfficeMenu(spot, { pageMenu = null }) {
                                        @Composable fun entry(label: String, icon: Int, act: () -> Unit) = OfficeMenuItem(label, icon) { pageMenu = null; WriterLog.i("PDF: menu › $label"); act() }
                                        val c = chosen
                                        if (c != null) {
                                            entry(stringResource(R.string.words_edit_text), R.drawable.ic_edit) { retype(c.first, c.second, c.third) }
                                            entry(stringResource(R.string.common_cut), R.drawable.ic_content_cut) { if (copyChosen()) deleteChosen() }
                                            entry(stringResource(R.string.common_copy), R.drawable.ic_copy) { copyChosen() }
                                        }
                                        entry(stringResource(R.string.common_paste), R.drawable.ic_content_paste) { paste(i, if (c == null) pt else null) }
                                        if (c != null) {
                                            entry(stringResource(R.string.words_bring_to_front), R.drawable.ic_flip_to_front) { restackChosen(2) }
                                            entry(stringResource(R.string.words_send_to_back), R.drawable.ic_flip_to_back) { restackChosen(-2) }
                                            entry(stringResource(R.string.common_delete), R.drawable.ic_delete) { deleteChosen() }
                                        }
                                    } }
                                    chosen?.takeIf { it.first == i && typing == null }?.let { (_, r, t) ->
                                        PdfChosenHandles(s, r, t.unturned(r), t.look.turn, framePreview, ::sizeChosen, ::turnChosen)
                                    }
                                }
                              } }
                            }
                        }
                        }
                    }
                } }
            }
            // on a phone the label is also the button for the list of pages; gone while editing, as Words' (the user, 24 Oct: it sat on the page)
            if (d != null && d.sizes.isNotEmpty() && !editing) Surface(color = Color.Black.copy(alpha = 0.75f), contentColor = if (pages) PDF_RED else Color.White, shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomCenter).bottomBarPadding().padding(bottom = 16.dp).then(if (wide) Modifier else Modifier.clickable { val keep = at; pages = !pages; scope.launch { list.scrollToItem(keep / across) } })) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!wide) Icon(painterResource(R.drawable.ic_view_grid), contentDescription = stringResource(if (pages) R.string.pdf_hide_pages else R.string.pdf_show_pages), modifier = Modifier.padding(end = 8.dp).size(18.dp))
                    Text(stringResource(R.string.words_page_of, (at + 1).coerceAtMost(d.sizes.size), d.sizes.size), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // a phone: the pane over the lower half of the pages
            if (d != null && pane != null && !wide) PdfPane(d, pane!!, editing, version, Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.55f),
                { goTo(it) }, { pane = null }, { asking = "bookmark" }, { editing = true }) { act -> change(act) }
        }
        // the ribbon while editing, at the bottom as Slides' (a tablet: the floating island; a phone: across the bottom)
        val d = doc
        // typing on the page: Font's strip in the ribbon's place, as Words' and Slides'; a card tapped shows on the words at once
        val before = fontBefore
        val ch0 = chosen
        if (typing != null && before != null) DesignGalleryStrip(DesignGallery.Font, FontChoice(textLook.font), cancel = { textLook = textLook.copy(font = before); fontBefore = null },
            ok = { fontBefore = null }, pick = { ch -> (ch as? FontChoice)?.let { textLook = textLook.copy(font = it.name) } }, fonts = STYLE_FONTS, now = textLook.font)
        // a chosen text: the font a card shows put on at once (Cancel puts the one before back)
        else if (ch0 != null && before != null) DesignGalleryStrip(DesignGallery.Font, FontChoice(ch0.third.look.font),
            cancel = { if (ch0.third.look.font != before) rechange(t = ch0.third.copy(look = ch0.third.look.copy(font = before))); fontBefore = null },
            ok = { fontBefore = null }, pick = { c -> (c as? FontChoice)?.let { rechange(t = ch0.third.copy(look = ch0.third.look.copy(font = it.name))) } },
            fonts = STYLE_FONTS, now = ch0.third.look.font)
        // Home with nothing chosen: the next text's font
        else if (before != null && editing) DesignGalleryStrip(DesignGallery.Font, FontChoice(textLook.font), cancel = { textLook = textLook.copy(font = before); fontBefore = null },
            ok = { fontBefore = null }, pick = { ch -> (ch as? FontChoice)?.let { textLook = textLook.copy(font = it.name) } }, fonts = STYLE_FONTS, now = textLook.font)
        else if (editing && d != null) {
            val w = Color.White
            Surface(color = Color.Black, contentColor = w, modifier = (if (wide) Modifier.fillMaxWidth().bottomBarPadding().padding(12.dp).clip(MaterialTheme.shapes.extraLarge) else Modifier.fillMaxWidth())
                .then(if (typing != null) Modifier.imePadding() else Modifier)) {
                OfficeStripFrame(w, if (wide) Modifier.padding(vertical = 4.dp) else Modifier.bottomBarPadding()) { Column {
                    // typing on the page: Words' Home tools for its look instead of the tabs, × and ✓ at the ends
                    if (typing != null) { PdfTextTools(w, textLook, { textLook = it }, { fontBefore = textLook.font }, { typing = null }, ::putTyped, typed); return@Column }
                    // a text chosen, as a Slides box: Selection first (Cut, Copy, Paste, formatting), the text's own tab last in the lighter colour
                    // Words' tabs in Words' order (the user, 24 Oct: "take a look on what the other applets have"), the PDF's own among them
                    val tabNames = mapOf("Selection" to stringResource(R.string.words_tab_selection), "File" to stringResource(R.string.words_tab_file),
                        "Home" to stringResource(R.string.words_tab_home), "Insert" to stringResource(R.string.words_tab_insert), "Draw" to stringResource(R.string.words_tab_draw),
                        "Layout" to stringResource(R.string.words_tab_layout), "Pages" to stringResource(R.string.words_pages), "Review" to stringResource(R.string.words_tab_review),
                        "Fill & Sign" to stringResource(R.string.pdf_tab_fill_sign), "Protect" to stringResource(R.string.pdf_tab_protect), "View" to stringResource(R.string.words_tab_view),
                        "Text box" to stringResource(R.string.words_text_box),
                        "Video" to stringResource(if (media.chosen?.second?.name?.let(OfficeVideo::audio) == true) R.string.slides_audio else R.string.words_video))
                    OfficeTabRow(listOfNotNull("Selection".takeIf { chosen != null }, "File", "Home", "Insert", "Draw", "Layout", "Pages", "Review", "Fill & Sign", "Protect", "View",
                        "Text box".takeIf { chosen != null }, "Video".takeIf { media.chosen != null }),
                        tab, w, PDF_RED, { tab = it; tool = null }, contextual = if (media.chosen != null) "Video" else "Text box", label = { tabNames[it] ?: it })
                    @Composable fun ToolButton(t: PdfTool) = RibbonButton(stringResource(t.label), w, t.icon, on = tool == t) { tool = if (tool == t) null else t }
                    key(tab) { RibbonLine(androidx.compose.foundation.rememberScrollState(), Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                        val c0 = chosen
                        // Words', Tables' and Slides' File: the file's own actions
                        if (tab == "File") {
                            RibbonButton(stringResource(R.string.common_save), w, R.drawable.ic_save, enabled = d.dirty) { save() }
                            RibbonButton(stringResource(R.string.words_save_copy), w, R.drawable.ic_copy, enabled = file != null) {
                                file?.let { f -> save(to = generateSequence(1) { it + 1 }.map { File(f.parentFile, "${f.nameWithoutExtension} (copy${if (it == 1) "" else " $it"}).pdf") }.first { !it.exists() }, copy = true) } }
                            RibbonButton(stringResource(R.string.words_autosave), w, R.drawable.ic_history, on = autoSave) {
                                autoSave = !autoSave; TextEditorStore(context).autoSave = autoSave
                                Toast.makeText(context, if (autoSave) R.string.words_autosave_on else R.string.words_autosave_off_msg, Toast.LENGTH_SHORT).show() }
                            FavouriteFileButton(file, w) // a phone: the Office home's favourite (a tablet: the heart in the top bar)
                            RibbonButton(stringResource(R.string.words_save_as), w, R.drawable.ic_save, enabled = file != null) { naming = "as" }
                            RibbonButton(stringResource(R.string.common_rename), w, R.drawable.ic_edit, enabled = file != null) { naming = "rename" }
                            RibbonDivider(w)
                            RibbonButton(stringResource(R.string.words_versions), w, R.drawable.ic_history, enabled = file != null) { showVersions = true }
                            RibbonButton(stringResource(R.string.pdf_edit_in_words), w, R.drawable.ic_edit_note) { exportWords() }
                            RibbonButton(stringResource(R.string.pdf_export_text), w, R.drawable.ic_text_fields) { exportText() }
                            RibbonButton(stringResource(R.string.common_print), w, R.drawable.ic_print) { if (d.dirty) save { print() } else print() }
                            file?.let { f ->
                                RibbonDivider(w)
                                // a change saved first, so what goes is what shows (Slides')
                                RibbonButton(stringResource(R.string.common_share), w, R.drawable.ic_share) { if (d.dirty) save { shareDriveFile(context, f) } else shareDriveFile(context, f) }
                                RibbonButton(stringResource(R.string.words_open_in_another_app), w, R.drawable.ic_open_with) { if (d.dirty) save { openElsewhere(context, f) } else openElsewhere(context, f) }
                            }
                            RibbonDivider(w)
                            RibbonButton(stringResource(R.string.words_about_file), w, R.drawable.ic_info) { about = true }
                            RibbonButton(stringResource(R.string.words_send_log), w, R.drawable.ic_share) { WriterLog.export(context)?.let { shareDriveFile(context, it) } }
                            RibbonDivider(w)
                            RibbonButton(stringResource(R.string.common_settings), w, R.drawable.ic_settings) { officeSettings = true }
                            return@RibbonLine
                        }
                        // Home, as Slides': a new text's tools, then the look — of the chosen text, else the next one typed
                        if (tab == "Home") {
                            ToolButton(PdfTool.Text); ToolButton(PdfTool.TextBox)
                            RibbonDivider(w)
                            val look = c0?.third?.look ?: textLook
                            PdfLookTools(w, look, { l -> if (c0 != null) rechange(t = c0.third.copy(look = l)) else textLook = l }, { fontBefore = look.font })
                            Tool(R.drawable.ic_format_clear, stringResource(R.string.words_clear_formatting), w) {
                                if (c0 != null) rechange(t = c0.third.copy(look = PdfTextLook(fill = look.fill, line = look.line, turn = look.turn), runs = emptyList())) else textLook = PdfTextLook() }
                            return@RibbonLine
                        }
                        // Layout, as Words' and Tables': the pages' size and way round (all of them, as Word's without sections), the margins
                        if (tab == "Layout") {
                            val now = d.sizes.getOrNull(at)
                            RibbonMenu(stringResource(R.string.words_size), w, R.drawable.ic_fit_page, preset = false, choices = PdfPaper.all.map { (name, p) ->
                                MenuChoice(stringResource(R.string.pdf_paper_size, name, WriterUnits.text(p.width), WriterUnits.text(p.height), unitName), checked = now?.let { PdfPaper.paper(it) } == name) {
                                    // the paper's size, each page kept the way round it was
                                    change { d.resize { s -> if (s.width > s.height) Size(p.height, p.width) else p } }; WriterLog.i("PDF: page size $name") } })
                            RibbonButton(stringResource(R.string.words_portrait), w, R.drawable.ic_crop_portrait, on = now != null && now.height >= now.width) {
                                change { d.resize { s -> if (s.width > s.height) Size(s.height, s.width) else s } }; WriterLog.i("PDF: portrait") }
                            RibbonButton(stringResource(R.string.words_landscape), w, R.drawable.ic_crop_landscape, on = now != null && now.width > now.height) {
                                change { d.resize { s -> if (s.height > s.width) Size(s.height, s.width) else s } }; WriterLog.i("PDF: landscape") }
                            RibbonMenu(stringResource(R.string.words_margins), w, R.drawable.ic_margin, preset = false, choices = PdfMargins.all.map { (name, v) ->
                                MenuChoice(stringResource(R.string.pdf_margin_size, stringResource(PdfMargins.labels.getValue(name)), WriterUnits.text(v.first), WriterUnits.text(v.second), unitName), checked = PdfMargins.nameOf(margins) == name) {
                                    margins = PdfMargins.page(name); PdfMargins.save(context, name) } })
                            RibbonDivider(w)
                            val page = at
                            RibbonButton(stringResource(R.string.words_blank_page), w, R.drawable.ic_note_add) { change { d.insertBlank(page + 1) }; scope.launch { delay(100); goTo(page + 1) } }
                            RibbonButton(stringResource(R.string.pdf_delete_page), w, R.drawable.ic_delete, enabled = d.sizes.size > 1) { change { d.delete(listOf(page)) } }
                            RibbonButton(stringResource(R.string.pdf_rotate_left), w, R.drawable.ic_rotate_left) { change { d.rotate(listOf(page), -90) } }
                            RibbonButton(stringResource(R.string.pdf_rotate_right), w, R.drawable.ic_rotate_right) { change { d.rotate(listOf(page), 90) } }
                            return@RibbonLine
                        }
                        // View, as Words': the page list, comments, bookmarks; the zoom; one or two pages, as wide as the screen; rulers; the unit
                        if (tab == "View") {
                            RibbonButton(stringResource(R.string.words_navigation), w, R.drawable.ic_navigation, on = if (wide) navigation else pages) { if (wide) navigation = !navigation else pages = !pages }
                            RibbonButton(stringResource(R.string.words_comments), w, R.drawable.ic_comment, on = pane == "Comments") { pane = if (pane == "Comments") null else "Comments" }
                            RibbonButton(stringResource(R.string.pdf_bookmarks), w, R.drawable.ic_bookmark, on = pane == "Bookmarks") { pane = if (pane == "Bookmarks") null else "Bookmarks" }
                            RibbonDivider(w)
                            Tool(R.drawable.ic_remove, stringResource(R.string.words_zoom_out), w, enabled = zoom > 1f) { zoom = (zoom - 0.25f).coerceAtLeast(1f); pan = Offset.Zero; settled = zoom }
                            Text("${Math.round(zoom * 100)}%", color = w, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Tool(R.drawable.ic_add, stringResource(R.string.words_zoom_in), w, enabled = zoom < Pdf.MAX_ZOOM) { zoom = (zoom + 0.25f).coerceAtMost(Pdf.MAX_ZOOM); pan = Offset.Zero; settled = zoom }
                            RibbonDivider(w)
                            RibbonButton(stringResource(R.string.words_fit_width), w, R.drawable.ic_fit_page, on = fitWidth) { val keep = at; fitWidth = !fitWidth; goTo(keep) }
                            RibbonButton(stringResource(R.string.words_two_pages), w, R.drawable.ic_view_column, on = across == 2) { val keep = at; across = 3 - across; goTo(keep) }
                            if (wide) RibbonButton(stringResource(R.string.pdf_ruler), w, R.drawable.ic_margin, on = rulers) { rulers = !rulers }
                            RibbonDivider(w)
                            RibbonButton(stringResource(R.string.words_unit_cm), w, on = !WriterUnits.inches) { WriterUnits.set(context, false) }
                            RibbonButton(stringResource(R.string.words_unit_inches), w, on = WriterUnits.inches) { WriterUnits.set(context, true) }
                            return@RibbonLine
                        }
                        // Words' Video tab: Play
                        if (tab == "Video") { media.chosen?.let { (pi, sp) -> RibbonButton(stringResource(R.string.words_play), w, R.drawable.ic_play) { scope.launch { media.play(d, pi, sp, context.cacheDir) } } }
                            return@RibbonLine }
                        // Slides' Selection tab: what is chosen cut, copied, pasted; words pasted alone; its look copied and put on another
                        if (tab == "Selection" && c0 != null) {
                            val (ci, _, ct) = c0
                            RibbonButton(stringResource(R.string.common_cut), w, R.drawable.ic_content_cut) { if (copyChosen()) deleteChosen() }
                            RibbonButton(stringResource(R.string.common_copy), w, R.drawable.ic_copy) { copyChosen() }
                            RibbonButton(stringResource(R.string.common_paste), w, R.drawable.ic_content_paste, enabled = clip != null) { paste(ci) }
                            RibbonDivider(w)
                            RibbonButton(stringResource(R.string.words_paste_text_only), w, R.drawable.ic_content_paste) { val keep = clip; clip = null; paste(ci); clip = keep }
                            RibbonDivider(w)
                            RibbonButton(stringResource(R.string.words_copy_formatting), w, R.drawable.ic_format_paint) { copiedLook = ct.look; Toast.makeText(context, R.string.pdf_formatting_copied, Toast.LENGTH_SHORT).show() }
                            // its turn, fill and border its own, as Slides' (the words' look only)
                            RibbonButton(stringResource(R.string.words_paste_formatting), w, R.drawable.ic_format_paint, enabled = copiedLook != null) { copiedLook?.let { l ->
                                rechange(t = ct.copy(look = l.copy(turn = ct.look.turn, fill = ct.look.fill, line = ct.look.line))); WriterLog.i("PDF: formatting pasted") } }
                            return@RibbonLine
                        }
                        // the text's own tab (Slides' item tab): its look at once, where it stands, Edit text, Delete
                        if (tab == "Text box" && c0 != null) {
                            val (ci, cr, ct) = c0
                            RibbonButton(stringResource(R.string.words_edit_text), w, R.drawable.ic_edit) { retype(ci, cr, ct) }
                            RibbonDivider(w)
                            PdfLookTools(w, ct.look, { l -> rechange(t = ct.copy(look = l)) }, { fontBefore = ct.look.font })
                            Tool(R.drawable.ic_format_clear, stringResource(R.string.words_clear_formatting), w) { rechange(t = ct.copy(look = PdfTextLook(fill = ct.look.fill, line = ct.look.line, turn = ct.look.turn), runs = emptyList())) }
                            RibbonDivider(w)
                            RibbonMenu(stringResource(R.string.words_position), w, R.drawable.ic_align_horizontal_center, preset = false, choices = listOf(
                                MenuChoice(stringResource(R.string.words_left), icon = R.drawable.ic_align_left) { position('l', null) }, MenuChoice(stringResource(R.string.words_centre), icon = R.drawable.ic_align_center) { position('c', null) },
                                MenuChoice(stringResource(R.string.words_right), icon = R.drawable.ic_align_right) { position('r', null) }, MenuChoice(stringResource(R.string.words_top), icon = R.drawable.ic_vertical_align_top) { position(null, 't') },
                                MenuChoice(stringResource(R.string.pdf_middle), icon = R.drawable.ic_vertical_align_center) { position(null, 'm') }, MenuChoice(stringResource(R.string.words_bottom), icon = R.drawable.ic_vertical_align_bottom) { position(null, 'b') }))
                            // the arrows (the user, 25 Oct): a point at a tap, held it goes on; put on when let go
                            NudgeTool(R.drawable.ic_arrow_forward, 180f, stringResource(R.string.pdf_move_left), w, { framePreview.shift += Offset(-it, 0f) }, ::nudged)
                            NudgeTool(R.drawable.ic_arrow_forward, 0f, stringResource(R.string.pdf_move_right), w, { framePreview.shift += Offset(it, 0f) }, ::nudged)
                            NudgeTool(R.drawable.ic_arrow_upward, 0f, stringResource(R.string.pdf_move_up), w, { framePreview.shift += Offset(0f, -it) }, ::nudged)
                            NudgeTool(R.drawable.ic_arrow_downward, 0f, stringResource(R.string.pdf_move_down), w, { framePreview.shift += Offset(0f, it) }, ::nudged)
                            RibbonMenu(stringResource(R.string.pdf_arrange), w, R.drawable.ic_flip_to_front, preset = false, choices = listOf(
                                MenuChoice(stringResource(R.string.words_bring_to_front), icon = R.drawable.ic_flip_to_front) { restackChosen(2) }, MenuChoice(stringResource(R.string.pdf_bring_forward), icon = R.drawable.ic_flip_to_front) { restackChosen(1) },
                                MenuChoice(stringResource(R.string.pdf_send_backward), icon = R.drawable.ic_flip_to_back) { restackChosen(-1) }, MenuChoice(stringResource(R.string.words_send_to_back), icon = R.drawable.ic_flip_to_back) { restackChosen(-2) }))
                            RibbonMenu(stringResource(R.string.words_rotate), w, R.drawable.ic_rotate_right, preset = false, choices = listOf(
                                MenuChoice(stringResource(R.string.pdf_rotate_right_90), icon = R.drawable.ic_rotate_right) { turnChosen(ct.look.turn + 90) },
                                MenuChoice(stringResource(R.string.pdf_rotate_left_90), icon = R.drawable.ic_rotate_left) { turnChosen(ct.look.turn - 90) },
                                MenuChoice(stringResource(R.string.pdf_upright), enabled = ct.look.turn != 0) { turnChosen(0) }))
                            RibbonDivider(w)
                            RibbonButton(stringResource(R.string.pdf_duplicate), w, R.drawable.ic_add_to_collection) { duplicateChosen() }
                            RibbonButton(stringResource(R.string.common_delete), w, R.drawable.ic_delete) { deleteChosen() }
                            return@RibbonLine
                        }
                        if (tab == "Review") {
                            ToolButton(PdfTool.Highlight); ToolButton(PdfTool.Underline); ToolButton(PdfTool.Strike)
                            RibbonDivider(w)
                            ToolButton(PdfTool.Note); ToolButton(PdfTool.TextBox); ToolButton(PdfTool.Rectangle); ToolButton(PdfTool.Oval); ToolButton(PdfTool.Arrow)
                            // Stamp: the kind chosen in a strip (✓/×, as Office's options), then a tap on the page puts it there
                            val stampStrip = officeStrip(stringResource(R.string.pdf_stamp), ok = { st -> (st.picked as? String)?.let { stampName = it; tool = PdfTool.Stamp } }) { st ->
                                STAMPS.forEach { n -> RibbonButton(n.replace(Regex("(?<=[a-z])(?=[A-Z])"), " "), w, on = st.picked == n) { st.picked = n } }
                                val strips = LocalOfficeStrips.current
                                RibbonButton(stringResource(R.string.pdf_picture_more), w, R.drawable.ic_gallery) { strips?.open = null; pickStamp.launch(arrayOf("image/*")) }
                            }
                            RibbonButton(if (tool != PdfTool.Stamp) stringResource(R.string.pdf_stamp) else if (stampName == PICTURE_STAMP) stringResource(R.string.words_picture)
                                else stampName.replace(Regex("(?<=[a-z])(?=[A-Z])"), " "), w, R.drawable.ic_branding_watermark, on = tool == PdfTool.Stamp) {
                                if (stampStrip != null) stampStrip() else tool = if (tool == PdfTool.Stamp) null else PdfTool.Stamp
                            }
                            // Words' colours, white among them (the user, 25 Oct: to cover what was drawn), and its highlighters; More… Words' table
                            Swatches(w, R.drawable.ic_palette, stringResource(R.string.words_colour), (HIGHLIGHT_COLOURS + TEXT_COLOURS).distinct()) { c ->
                                c?.let { markColour = (it.toArgb() and 0xFFFFFF) } }
                            RibbonDivider(w)
                            ToolButton(PdfTool.Erase)
                            RibbonButton(stringResource(R.string.words_comments), w, R.drawable.ic_view_list, on = pane == "Comments") { pane = if (pane == "Comments") null else "Comments" }
                            return@RibbonLine
                        }
                        if (tab == "Insert") {
                            // as Words' Insert: pages, words, pictures and drawings, notes, page furniture
                            val page = at
                            RibbonButton(stringResource(R.string.words_blank_page), w, R.drawable.ic_note_add) { change { d.insertBlank(page + 1) }; scope.launch { delay(100); goTo(page + 1) } }
                            RibbonButton(stringResource(R.string.pdf_insert_pdf), w, R.drawable.ic_add_to_collection) { pickPdf.launch(arrayOf("application/pdf")) }
                            RibbonDivider(w)
                            ToolButton(PdfTool.Text); ToolButton(PdfTool.TextBox)
                            RibbonDivider(w)
                            RibbonButton(stringResource(R.string.words_picture), w, R.drawable.ic_gallery) { pickImage.launch(arrayOf("image/*")) }
                            RibbonButton(stringResource(R.string.words_signature), w, R.drawable.ic_draw, on = tool == PdfTool.Sign) { if (!signatureFile.isFile) signing = true else tool = if (tool == PdfTool.Sign) null else PdfTool.Sign }
                            RibbonButton(stringResource(R.string.pdf_stamp), w, R.drawable.ic_branding_watermark, on = tool == PdfTool.Stamp) { tool = if (tool == PdfTool.Stamp) null else PdfTool.Stamp }
                            ToolButton(PdfTool.Rectangle); ToolButton(PdfTool.Oval); ToolButton(PdfTool.Arrow)
                            RibbonDivider(w)
                            ToolButton(PdfTool.Note); ToolButton(PdfTool.Date)
                            RibbonDivider(w)
                            RibbonButton(stringResource(R.string.words_page_numbers), w, R.drawable.ic_format_list_numbered) { change { d.headerFooter("", "{n} / {N}", 10f) } }
                            RibbonButton(stringResource(R.string.words_header_footer), w, R.drawable.ic_text_fields) { asking = "header" }
                            RibbonButton(stringResource(R.string.words_watermark), w, R.drawable.ic_branding_watermark) { asking = "watermark" }
                            return@RibbonLine
                        }
                        if (tab == "Protect") {
                            RibbonButton(stringResource(R.string.pdf_password), w, R.drawable.ic_lock) { asking = "password" }
                            if (password != null) RibbonButton(stringResource(R.string.pdf_remove_password), w, R.drawable.ic_lock_open) { d.protect(null); version++
                                Toast.makeText(context, R.string.pdf_password_off_on_save, Toast.LENGTH_LONG).show() }
                            RibbonDivider(w)
                            ToolButton(PdfTool.RedactText)
                            ToolButton(PdfTool.Redact)
                            RibbonButton(stringResource(R.string.pdf_find_redact), w, R.drawable.ic_search) { asking = "redact" }
                            RibbonButton(stringResource(R.string.pdf_redact_marked), w, R.drawable.ic_check) {
                                var n = 0; change { n = d.applyRedactions() }
                                Toast.makeText(context, if (n == 0) context.getString(R.string.pdf_mark_first) else context.resources.getQuantityString(R.plurals.pdf_redacted_pages, n, n), Toast.LENGTH_LONG).show()
                            }
                            return@RibbonLine
                        }
                        if (tab == "Fill & Sign") {
                            ToolButton(PdfTool.Text); ToolButton(PdfTool.Tick); ToolButton(PdfTool.Cross); ToolButton(PdfTool.Dot); ToolButton(PdfTool.Date)
                            RibbonDivider(w)
                            ToolButton(PdfTool.TextField); ToolButton(PdfTool.CheckBox)
                            RibbonDivider(w)
                            ToolButton(PdfTool.Sign)
                            RibbonButton(stringResource(if (signatureFile.isFile) R.string.pdf_new_signature else R.string.pdf_draw_signature), w, R.drawable.ic_edit) { signing = true }
                            RibbonDivider(w)
                            listOf(9f to R.string.pdf_small, 11f to R.string.pdf_normal, 14f to R.string.pdf_large).forEach { (pt, label) -> RibbonButton(stringResource(label), w, on = fillSize == pt) { fillSize = pt } }
                            RibbonDivider(w)
                            ToolButton(PdfTool.Erase)
                            return@RibbonLine
                        }
                        if (tab == "Draw") {
                            ToolButton(PdfTool.Pen); ToolButton(PdfTool.Marker)
                            // Words' text colours, white among them: a white pen covers a stroke (the user, 25 Oct)
                            Swatches(w, R.drawable.ic_palette, stringResource(R.string.pdf_pen_colour), TEXT_COLOURS) { c ->
                                c?.let { penColour = (it.toArgb() and 0xFFFFFF) } }
                            RibbonDivider(w)
                            listOf(1f to R.string.words_thin, 2f to R.string.words_medium, 4f to R.string.words_thick).forEach { (pt, label) -> RibbonButton(stringResource(label), w, on = penWidth == pt) { penWidth = pt } }
                            RibbonDivider(w)
                            ToolButton(PdfTool.Erase)
                            return@RibbonLine
                        }
                        val page = at
                        RibbonButton(stringResource(R.string.pdf_organize), w, R.drawable.ic_view_grid) { organize = true }
                        RibbonDivider(w)
                        RibbonButton(stringResource(R.string.pdf_rotate_left), w, R.drawable.ic_rotate_left) { change { d.rotate(listOf(page), -90) } }
                        RibbonButton(stringResource(R.string.pdf_rotate_right), w, R.drawable.ic_rotate_right) { change { d.rotate(listOf(page), 90) } }
                        RibbonButton(stringResource(R.string.pdf_delete_page), w, R.drawable.ic_delete, enabled = d.sizes.size > 1) { change { d.delete(listOf(page)) } }
                        RibbonDivider(w)
                        RibbonButton(stringResource(R.string.words_blank_page), w, R.drawable.ic_note_add) { change { d.insertBlank(page + 1) }; scope.launch { delay(100); goTo(page + 1) } }
                        RibbonButton(stringResource(R.string.pdf_insert_pdf), w, R.drawable.ic_add_to_collection) { pickPdf.launch(arrayOf("application/pdf")) }
                        RibbonButton(stringResource(R.string.pdf_bookmarks), w, R.drawable.ic_bookmark, on = pane == "Bookmarks") { pane = if (pane == "Bookmarks") null else "Bookmarks" }
                        RibbonButton(stringResource(R.string.pdf_recognize_text), w, R.drawable.ic_text_fields) {
                            // every page without words of its own read (a scan, a photo), each one undo step; Find and Copy then work on it
                            ocrJob = scope.launch {
                                val todo = withContext(Dispatchers.IO) { d.sizes.indices.filter { !d.hasText(it) } }
                                if (todo.isEmpty()) { Toast.makeText(context, R.string.pdf_all_have_text, Toast.LENGTH_SHORT).show(); return@launch }
                                var words = 0
                                for ((k, i) in todo.withIndex()) {
                                    reading2 = k + 1 to todo.size
                                    val got = withContext(Dispatchers.Default) { runCatching { val (bmp, sc) = d.scanOf(i); Ocr.words(context, bmp).also { ws -> d.addTextLayer(i, ws, sc) } } }
                                    got.onFailure { WriterLog.e("PDF: recognize text", it) }.onSuccess { words += it.size }
                                    version++
                                }
                                reading2 = null
                                WriterLog.i("PDF: recognized ${todo.size} pages, $words words")
                                Toast.makeText(context, context.resources.getQuantityString(R.plurals.pdf_ocr_done, todo.size, words, todo.size), Toast.LENGTH_LONG).show()
                            }
                        }
                        RibbonButton(stringResource(R.string.pdf_reduce_size), w, R.drawable.ic_compress) {
                            d.shrink(); version++
                            Toast.makeText(context, context.getString(R.string.pdf_shrink_note, (file?.length() ?: 0) / 1024), Toast.LENGTH_LONG).show()
                        }
                        RibbonButton(stringResource(R.string.pdf_extract_page), w, R.drawable.ic_copy) {
                            val f = file ?: return@RibbonButton
                            scope.launch {
                                val to = generateSequence(1) { it }.let { _ -> var n = 0; var t: File; do { n++; t = File(f.parentFile, "${f.nameWithoutExtension} (page ${page + 1}${if (n > 1) " $n" else ""}).pdf") } while (t.exists()); t }
                                withContext(Dispatchers.IO) { runCatching { d.extract(listOf(page), to) } }
                                    .onSuccess { Toast.makeText(context, context.getString(R.string.words_saved_as, to.name), Toast.LENGTH_LONG).show() }
                                    .onFailure { WriterLog.e("PDF: extract", it); Toast.makeText(context, pdfSays(context, it, R.string.pdf_extract_failed), Toast.LENGTH_LONG).show() }
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
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = stringResource(R.string.pdf_page_n, i + 1), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
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
        if (tool == null || tool == PdfTool.Note || tool == PdfTool.Erase || tool == PdfTool.Stamp || tool == PdfTool.CheckBox || tool in PdfTool.Text..PdfTool.Sign) detectTapGestures { p -> done(listOf(p / k)) }
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
            PdfTool.Rectangle, PdfTool.TextBox, PdfTool.TextField -> drawRect(c, Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
            PdfTool.Redact -> drawRect(Color.Black.copy(alpha = 0.5f), Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)))
            PdfTool.Oval -> drawOval(c, Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
            PdfTool.Arrow -> drawLine(c, a, b, 4f)
            else -> drawLine(c.copy(alpha = 0.5f), a, b, 18f) // the words a highlight will cover, roughly
        }
    }
}

/** A watermark's words drawn turned 35° up, grey, as a PNG (Android draws Greek with the phone's own fonts), and its height / width. */
internal fun watermarkPng(words: String): Pair<ByteArray, Float> {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 160f; color = 0xFF808080.toInt(); typeface = android.graphics.Typeface.DEFAULT_BOLD }
    val tw = paint.measureText(words); val th = paint.fontMetrics.let { it.descent - it.ascent }
    val angle = Math.toRadians(35.0)
    val w = (tw * Math.cos(angle) + th * Math.sin(angle)).toInt() + 20; val h = (tw * Math.sin(angle) + th * Math.cos(angle)).toInt() + 20
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bmp).apply { rotate(-35f, w / 2f, h / 2f); drawText(words, (w - tw) / 2f, h / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f, paint) }
    return java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() to h.toFloat() / w
}

/**
 * The Comments or Bookmarks pane: every comment with its words and page, or the file's bookmarks; a row tapped goes to its
 * page. While editing a comment can be deleted, a bookmark deleted or one added for the page shown. [version] reads them again.
 */
@Composable
private fun PdfPane(d: PdfDoc, which: String, editing: Boolean, version: Int, modifier: Modifier, go: (Int) -> Unit, close: () -> Unit, addMark: () -> Unit,
    edit: () -> Unit = {}, change: (() -> Unit) -> Unit) {
    var replying by remember { mutableStateOf<PdfComment?>(null) }
    replying?.let { c ->
        var words by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(onDismissRequest = { replying = null }, title = { Text(stringResource(R.string.words_reply)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (c.words.isNotBlank()) Text(stringResource(R.string.pdf_quoted, c.words.take(140)), style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.OutlinedTextField(words, { words = it }, Modifier.fillMaxWidth(), minLines = 2)
            } },
            confirmButton = { androidx.compose.material3.TextButton(enabled = words.isNotBlank(), onClick = { replying = null; edit(); change { d.reply(c, words.trim()) } }) { Text(stringResource(R.string.words_reply), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { replying = null }) { Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } })
    }
    var comments by remember { mutableStateOf<List<PdfComment>?>(null) }
    var marks by remember { mutableStateOf<List<PdfMark>?>(null) }
    LaunchedEffect(which, version) {
        withContext(Dispatchers.IO) { if (which == "Comments") comments = runCatching { d.comments() }.getOrDefault(emptyList()) else marks = runCatching { d.bookmarks() }.getOrDefault(emptyList()) }
    }
    Surface(color = Color(0xFF1F1F1F), contentColor = Color.White, modifier = modifier) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (which == "Comments") R.string.words_comments else R.string.pdf_bookmarks), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (which == "Bookmarks") androidx.compose.material3.TextButton(onClick = addMark) { Text(stringResource(R.string.pdf_add_for_page), color = PDF_RED, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                IconButton(onClick = close) { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.common_close)) }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (which == "Comments") {
                    val l = comments
                    if (l == null) item { Text(stringResource(R.string.pdf_reading), Modifier.padding(16.dp), color = Color.LightGray) }
                    else if (l.isEmpty()) item { Text(stringResource(R.string.pdf_no_comments), Modifier.padding(16.dp), color = Color.LightGray) }
                    else {
                        // each comment with its replies under it, a step in
                        val ordered = l.filter { it.replyTo == null }.flatMap { c -> listOf(c) + l.filter { r -> r.page == c.page && r.replyTo == c.index } }
                        items(ordered.size) { k -> val c = ordered[k]; val reply = c.replyTo != null
                            Row(Modifier.fillMaxWidth().clickable { go(c.page) }.padding(start = if (reply) 36.dp else 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(listOfNotNull(stringResource(c.kind), c.author.ifBlank { null }, stringResource(R.string.pdf_page_lower, c.page + 1)).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = PDF_RED)
                                    if (c.words.isNotBlank()) Text(c.words, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                if (!reply) IconButton(onClick = { replying = c }) { Icon(painterResource(R.drawable.ic_comment), contentDescription = stringResource(R.string.words_reply)) }
                                if (editing) IconButton(onClick = { change { d.deleteComment(c) } }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.common_delete)) }
                            }
                        }
                    }
                } else {
                    val l = marks
                    if (l == null) item { Text(stringResource(R.string.pdf_reading), Modifier.padding(16.dp), color = Color.LightGray) }
                    else if (l.isEmpty()) item { Text(stringResource(R.string.pdf_no_bookmarks), Modifier.padding(16.dp), color = Color.LightGray) }
                    else items(l.size) { k -> val m = l[k]
                        Row(Modifier.fillMaxWidth().clickable(enabled = m.page >= 0) { go(m.page) }.padding(start = 16.dp + 16.dp * m.depth, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(m.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (m.page >= 0) Text("${m.page + 1}", style = MaterialTheme.typography.labelMedium, color = Color.LightGray, modifier = Modifier.padding(horizontal = 8.dp))
                            if (editing) IconButton(onClick = { change { d.deleteBookmark(k) } }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.common_delete)) }
                        }
                    }
                }
            }
        }
    }
}
