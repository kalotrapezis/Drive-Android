package com.kalotrapezis.drive

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.produceState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.isSpecified
import com.mohamedrejeb.richeditor.document.RichTextBlock
import com.mohamedrejeb.richeditor.document.RichTextBlockType
import com.mohamedrejeb.richeditor.document.RichTextDocument
import com.mohamedrejeb.richeditor.document.RichTextSpanMark
import com.mohamedrejeb.richeditor.model.HeadingStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Office (asked 2026-09-27): an orange group of its own. Its home starts a new Word document (Excel and PowerPoint
 * come later) and lists the documents in Tetra's Files; Writer edits one, what you see is what the file holds.
 */

/** Each app's own colour, as across Office and its look-alikes (asked 2026-09-27); the Office group stays orange. */
internal val WORD_BLUE = Color(0xFF185ABD)
internal val EXCEL_GREEN = Color(0xFF107C41)
internal val POWERPOINT_ORANGE = Color(0xFFB7472A) // deeper than the Office group's orange (asked 2026-09-27)

/** The editor's normal text size; the document's own normal size is shown at this size, the rest in proportion. */
private const val BASE_SP = 16f

/** Pictures are drawn at most this wide (sp) — about the page's width on a phone; the file keeps their real size. */
private const val MAX_PICTURE_SP = 300f
/** Superscript and subscript are shown this much smaller; a size the file sets and the document's own are told apart by
 *  the factor (the editor keeps only the size shown). */
private const val SCRIPT_SIZE = 0.7f
/** Writer's own changes kept for undo; each is the whole document, so not without end. */
private const val UNDO_STEPS = 50
/** How wide a tablet's comments pane is. */
private val COMMENT_PANE = 260.dp

/** The document as it was before one of Writer's own changes, for undo. */
private class WriterSnapshot(val doc: OfficeDoc, val original: ByteArray?, val styles: DocStyles,
    val pictures: Map<String, DocImage>, val selection: androidx.compose.ui.text.TextRange)
private const val SCRIPT_SIZE_OWN = 0.69f

/**
 * A paragraph's style drawn in the editor (asked 2026-09-29: headings as the document has them, Title as a title):
 * its size, weight, slant and colour put on its letters as marks told apart from the letters' own on the way back —
 * the size by a few hundredths of a point (a letter's own size is in half points), the weight 701 (bold is 700), the
 * colour one step short of opaque, slant with an invisible shadow beside it. So they are never saved as the letters'.
 */
private const val STYLE_SIZE = 0.037f
private const val STYLE_WEIGHT = 701
private const val STYLE_ALPHA = 0xFEL
/** A tracked change's letters: its author's colour, two steps short of opaque (never saved as the letters' own). */
private const val CHANGE_ALPHA = 0xFDL
private const val STYLE_SLANT = 0x01L // the shadow's colour: see-through
/** A paragraph aligned as its style says (not by its own setting); an alignment chosen on the ribbon takes it off. */
private val STYLE_ALIGNED = androidx.compose.ui.text.style.TextDirection.ContentOrRtl

/** The character the editor holds a picture as (its own choice: U+FFFD, not our U+FFFC). */
private const val EDITOR_PICTURE_CHAR = '\uFFFD'
/** Typed where a picture is to go, then replaced by it (a private-use character: never in a document). */
internal const val PICTURE_SPOT = '\uE000'

/** How the editor names a picture: this and the picture's key. */
internal const val PICTURE_URL = "tetra-picture:"

/** OfficeDoc ↔ the editor's document. Kept apart from the screen so the two directions are easy to compare. */
internal object WriterBridge {
    /**
     * The editor's own heading looks (Heading 1 twice the text's size, and so on) taken away, once: a heading shows as
     * the document's style has it, drawn like any paragraph style's. The library has no setting for them.
     */
    fun documentHeadings() = runCatching {
        val field = HeadingStyle::class.java.getDeclaredField("defaultSpanStyle").apply { isAccessible = true }
        HeadingStyle.entries.filter { it != HeadingStyle.Normal }.forEach { field.set(it, SpanStyle()) }
    }.onFailure { Log.w("Writer", "heading looks", it) }

    /**
     * How a picture shows in the editor: its size, scaled with the text, and never wider than the page. One in front of
     * or behind the text moves none of it (the pages show it in its place): here a thumbnail a line tall, to tap.
     */
    fun shownSize(img: DocImage, scale: Float): Pair<Float, Float> {
        val w = img.widthPt * scale
        // A table is laid out at the page's width (its columns as the file sets them, as the pages show it) and drawn
        // smaller when the screen is narrower (a phone), as a picture is — laid out narrower, its columns grew to fit
        // their longest words and the others broke words in two (the user's CV, 2026-09-30).
        if (img.table != null) return (editorWidthSp / w).coerceAtMost(1f).let { fit -> w * fit to img.heightPt * scale * fit }
        val over = PictureXml.wrapOf(img).let { it == PictureWrap.InFront || it == PictureWrap.Behind }
        // On the page (a tablet, Print layout) it is drawn in its place over the text: its letter takes no room.
        if (over && pageWidthPt != null) return 0.5f to 0.5f
        val fit = minOf(MAX_PICTURE_SP / w, if (over) minOf(BASE_SP * 1.2f / (img.heightPt * scale), BASE_SP * 2.4f / w) else 1f).coerceAtMost(1f)
        return w * fit to img.heightPt * scale * fit
    }

    /** [img] where [PICTURE_SPOT] was typed, in the look of its own run (or of the text it was typed in). */
    fun putPicture(doc: OfficeDoc, img: DocImage): OfficeDoc = doc.copy(paras = doc.paras.map { p ->
        if (p.runs.none { PICTURE_SPOT in it.text }) p else p.copy(runs = OfficeFormats.tidy(p.runs.flatMap { r ->
            if (PICTURE_SPOT !in r.text) listOf(r) else r.text.split(PICTURE_SPOT).flatMapIndexed { k, t -> listOfNotNull(
                if (k > 0) (pictureRuns[img.key] ?: r.copy(link = null)).copy(text = OBJECT_MARK.toString(), image = img) else null,
                r.copy(text = t).takeIf { t.isNotEmpty() }) }
        }))
    })

    /** How wide the editor's picture character is, for its size (measured by the screen; about 1 elsewhere). */
    var pictureGlyph = 1f
    /** A line of the editor's text, top to bottom, for each point of its size. */
    var lineRatio = 1.17f

    /** A picture's own run as the file had it (its letter formatting), by the picture's key. */
    val pictureRuns = HashMap<String, DocRun>()

    /**
     * A paragraph's lines as tall as the pages make them (asked 2026-10-01: a cover's text rode up under its logos):
     * its line spacing (1.15, double, exact) over its biggest letters — an empty one's, its paragraph mark's size, as
     * Word gives an empty line. Left to the editor for single spacing, headings, pictures and tables.
     */
    private fun lineHeight(p: DocPara, runs: List<DocRun>, look: ParaLook, basePt: Float, scale: Float): androidx.compose.ui.unit.TextUnit {
        if (p.heading > 0 || p.table != null || runs.any { it.image != null }) return androidx.compose.ui.unit.TextUnit.Unspecified
        look.lineExact?.takeIf { !look.lineAtLeast }?.let { return (it * scale).sp }
        val mark = if (runs.isEmpty()) p.keep?.parts?.firstOrNull { it.first == "rPr" }?.second
            ?.let { Regex("""<w:sz w:val="(\d+)"""").find(it)?.groupValues?.get(1)?.toFloatOrNull()?.div(2) } else null
        if (look.lineMultiple == 1f && mark == null) return androidx.compose.ui.unit.TextUnit.Unspecified
        val size = mark ?: (runs.maxOfOrNull { it.sizePt ?: look.sizePt ?: basePt } ?: look.sizePt ?: basePt)
        return (size * scale * lineRatio * look.lineMultiple).sp
    }

    /** [pictures] learns every picture shown, by the key the editor knows it by. */
    fun toEditor(doc: OfficeDoc, pictures: MutableMap<String, DocImage> = HashMap(), styles: DocStyles = DocStyles.Plain): RichTextDocument {
        val scale = BASE_SP / doc.basePt
        /** One line of the editor: a paragraph, or (after a line break inside it) the next line of one. */
        fun block(p: DocPara, runs: List<DocRun>, afterBreak: Boolean): RichTextBlock {
            val marks = ArrayList<RichTextSpanMark>()
            val look = styles.look(p)
            var at = 0
            for (r in runs) {
                val range = at until at + r.text.length
                // The paragraph style's look, where the letters have none of their own.
                if (r.image == null && r.text.isNotEmpty()) {
                    if (r.sizePt == null && r.script == 0) look.sizePt?.takeIf { kotlin.math.abs(it - doc.basePt) > 0.01f }?.let { marks += RichTextSpanMark.FontSize(range, ((it + STYLE_SIZE) * scale).sp) }
                    if (!r.bold && look.bold == true) marks += RichTextSpanMark.FontWeight(range, STYLE_WEIGHT)
                    if (!r.italic && look.italic == true) { marks += RichTextSpanMark.Italic(range); marks += RichTextSpanMark.Shadow(range, STYLE_SLANT, 0f, 0f, 0f) }
                    if (r.color == null) look.color?.takeIf { it != 0 }?.let { marks += RichTextSpanMark.TextColor(range, (STYLE_ALPHA shl 24) or it.toLong()) }
                }
                if (r.bold) marks += RichTextSpanMark.Bold(range)
                if (r.italic) marks += RichTextSpanMark.Italic(range)
                if (r.underline) marks += RichTextSpanMark.Underline(range)
                if (r.strike) marks += RichTextSpanMark.Strikethrough(range)
                if (r.script != 0) {
                    marks += RichTextSpanMark.BaselineShift(range, if (r.script > 0) 0.4f else -0.15f) // about where Word puts them
                    marks += RichTextSpanMark.FontSize(range, ((r.sizePt ?: doc.basePt) * scale * (if (r.sizePt != null) SCRIPT_SIZE else SCRIPT_SIZE_OWN)).sp)
                } else r.sizePt?.let { marks += RichTextSpanMark.FontSize(range, (it * scale).sp) }
                r.color?.let { marks += RichTextSpanMark.TextColor(range, 0xFF000000L or it.toLong()) }
                r.change?.let { c -> if (r.image == null) marks += RichTextSpanMark.TextColor(range, (CHANGE_ALPHA shl 24) or TrackChanges.color(c.author).toLong()) }
                r.highlight?.let { marks += RichTextSpanMark.Highlight(range, 0xFF000000L or it.toLong()) }
                r.link?.let { marks += RichTextSpanMark.Link(range, it) }
                r.image?.let { img ->
                    pictures[img.key] = img
                    pictureRuns[img.key] = r
                    val (w, h) = shownSize(img, scale)
                    marks += RichTextSpanMark.Image(range, PICTURE_URL + img.key, w.sp, h.sp, "Picture")
                    // The editor cannot draw it (Writer does, over the text): the character is made as tall as the
                    // picture, so its line makes room, and invisible.
                    // A table is a line of its own: its letter's whole line (rise and fall) is the table's height, and it is
                    // drawn from the line's top — standing on the baseline, the fall left a gap a quarter of its height.
                    val size = (if (img.table != null) h / lineRatio else h * 1.12f).coerceAtLeast(4f) // a letter's rise is ~0.9 of its size
                    marks += RichTextSpanMark.FontSize(range, size.sp)
                    marks += RichTextSpanMark.TextColor(range, 0L)
                    // …and exactly as wide as the picture, so the text after it starts where the picture ends. Android gives
                    // half the spacing before a letter and half after, and none before the first of a line: there, twice.
                    marks += RichTextSpanMark.LetterSpacing(range, ((w - pictureGlyph * size) * (if (at == 0) 2 else 1)).sp)
                }
                at += r.text.length
            }
            return RichTextBlock(
                text = runs.joinToString("") { it.text },
                // The line after a break is not a new list item (no bullet of its own).
                type = p.list?.takeIf { !afterBreak }?.let { RichTextBlockType.ListItem(it.ordered, it.level, it.start) } ?: RichTextBlockType.Paragraph,
                spans = marks,
                headingLevel = p.heading,
                // Its own alignment, else its style's — marked (a text direction no paragraph of its own has) as the style's.
                textAlign = when (p.align.takeIf { it != DocAlign.Start } ?: look.align ?: DocAlign.Start) { DocAlign.Start -> TextAlign.Unspecified; DocAlign.Center -> TextAlign.Center; DocAlign.End -> TextAlign.End; DocAlign.Justify -> TextAlign.Justify },
                textDirection = if (p.align == DocAlign.Start && look.align != null && look.align != DocAlign.Start) STYLE_ALIGNED else androidx.compose.ui.text.style.TextDirection.Unspecified,
                // The paragraph's left and first-line indents shown (a list item's indent is the editor's own).
                textIndent = if (p.list != null || p.table != null) null else look.let { l ->
                    val rest = l.left.coerceAtLeast(0f) * scale
                    val first = if (afterBreak) rest else (l.left + l.firstLine).coerceAtLeast(0f) * scale
                    if (rest == 0f && first == 0f) null else androidx.compose.ui.text.style.TextIndent(first.sp, rest.sp)
                },
                isLineBreak = afterBreak,
                lineHeight = lineHeight(p, runs, look, doc.basePt, scale),
            )
        }
        // A line break inside a paragraph (the editor draws U+2028 as a box): the lines after it are the editor's own
        // "from a line break" lines, joined back into the paragraph by fromEditor.
        return RichTextDocument(doc.paras.flatMapIndexed { index, p ->
            // A table: one character in the editor, standing for it (as a picture's does); it comes back as it went.
            // Known by its place, not its contents: a cell's edit changed its key under the Table tab (the next change
            // went to a table no longer shown), and two tables alike shared one.
            p.table?.let { t ->
                val (width, height) = tableSize(doc, t, styles)
                val img = DocImage("t$index", null, width, height, table = t)
                return@flatMapIndexed listOf(block(p, listOf(DocRun(OBJECT_MARK.toString(), image = img)), false))
            }
            val lines = mutableListOf(mutableListOf<DocRun>())
            for (r in OfficeFormats.tidy(p.runs)) r.text.split(LINE_BREAK).forEachIndexed { i, piece ->
                if (i > 0) lines += mutableListOf<DocRun>()
                if (piece.isNotEmpty()) lines.last() += r.copy(text = piece)
            }
            lines.mapIndexed { i, runs -> block(p, runs, i > 0) }
        })
    }

    fun fromEditor(rich: RichTextDocument, basePt: Float, pictures: Map<String, DocImage> = emptyMap()): List<DocPara> {
        val scale = BASE_SP / basePt
        return rich.blocks.map { b ->
            val cuts = sortedSetOf(0, b.text.length)
            b.spans.forEach { cuts += it.range.first.coerceIn(0, b.text.length); cuts += (it.range.last + 1).coerceIn(0, b.text.length) }
            val runs = cuts.toList().zipWithNext().filter { (a, z) -> z > a }.map { (a, z) ->
                var r = DocRun(b.text.substring(a, z))
                // Slant drawn for the paragraph's style, not the letters' own.
                val styleSlant = b.spans.any { it is RichTextSpanMark.Shadow && a in it.range && it.argb == STYLE_SLANT }
                for (m in b.spans) if (a in m.range) r = when (m) {
                    is RichTextSpanMark.Bold -> r.copy(bold = true)
                    is RichTextSpanMark.FontWeight -> if (m.weight == STYLE_WEIGHT) r else r.copy(bold = m.weight >= 600)
                    is RichTextSpanMark.Italic -> if (styleSlant) r else r.copy(italic = true)
                    is RichTextSpanMark.Underline -> r.copy(underline = true)
                    is RichTextSpanMark.Strikethrough -> r.copy(strike = true)
                    is RichTextSpanMark.FontSize -> r.copy(sizePt = spOf(m.size)?.let { it / scale }?.takeIf { !isStyleSize(it) }) // the style's: none of its own; rounded below
                    is RichTextSpanMark.BaselineShift -> r.copy(script = if (m.multiplier > 0) 1 else if (m.multiplier < 0) -1 else 0)
                    is RichTextSpanMark.TextColor -> if ((m.argb ushr 24).let { it == STYLE_ALPHA || it == CHANGE_ALPHA }) r else r.copy(color = (m.argb and 0xFFFFFF).toInt())
                    is RichTextSpanMark.Highlight -> r.copy(highlight = (m.argb and 0xFFFFFF).toInt())
                    is RichTextSpanMark.Link -> r.copy(link = m.url)
                    is RichTextSpanMark.Image -> pictures[m.url.removePrefix(PICTURE_URL)]?.let { img ->
                        return@map (pictureRuns[img.key] ?: DocRun(OBJECT_MARK.toString())).copy(text = OBJECT_MARK.toString(), image = img)
                    } ?: r
                    else -> r
                }
                val shown = r.sizePt
                r.copy(sizePt = when {
                    shown == null -> null
                    r.script == 0 -> (shown * 2).roundToInt() / 2f
                    kotlin.math.abs(shown - basePt * SCRIPT_SIZE_OWN) < 0.001f * shown -> null // the document's own size
                    else -> ((shown / SCRIPT_SIZE) * 2).roundToInt() / 2f
                })
            }
            val list = (b.type as? RichTextBlockType.ListItem)?.let { DocList(it.ordered, it.indent.coerceIn(0, 8), it.startNumber.takeIf { _ -> it.ordered }) }
            val align = if (b.textDirection == STYLE_ALIGNED) DocAlign.Start // its style's
                else when (b.textAlign) { TextAlign.Center -> DocAlign.Center; TextAlign.End, TextAlign.Right -> DocAlign.End; TextAlign.Justify -> DocAlign.Justify; else -> DocAlign.Start }
            DocPara(OfficeFormats.tidy(runs), b.headingLevel.coerceIn(0, 6), list, align) to b.isLineBreak
        }.fold(ArrayList<DocPara>()) { paras, (p, afterBreak) ->
            val prev = paras.lastOrNull()
            if (afterBreak && prev != null) paras[paras.size - 1] = prev.copy(runs = OfficeFormats.tidy(prev.runs + breakRun(prev.runs, p.runs) + p.runs))
            else paras += p
            paras
        }.flatMap { p ->
            // A table's character: the table again, a paragraph of its own; text typed beside it, paragraphs beside it.
            if (p.runs.none { it.image?.table != null }) return@flatMap listOf(p)
            val out = ArrayList<DocPara>(); var run = ArrayList<DocRun>()
            fun text() { if (run.any { it.text.isNotBlank() }) out += p.copy(runs = OfficeFormats.tidy(run), list = null); run = ArrayList() }
            for (r in p.runs) { val t = r.image?.table; if (t != null) { text(); out += tablePara(t) } else run += r }
            text()
            out
        }
    }

    /**
     * The paragraphs (as fromEditor counts them) the text from [start] to [end] touches. The editor's text is its
     * blocks one after another, a space between, a list item's "• " or "1. " before it: each block is found in turn.
     */
    fun paragraphsAt(text: String, rich: RichTextDocument, start: Int, end: Int): Set<Int> {
        var n = -1
        val out = HashSet<Int>()
        blockSpans(text, rich) { k, from, to -> if (!rich.blocks[k].isLineBreak) n++; if (start <= to && end >= from) out += n }
        return out
    }
    /**
     * Clear formatting (Word's eraser; LibreOffice's "clear direct formatting"): the letters' own looks taken off the
     * text from [start] to [end] — bold, italic, underline, strike, size, super/subscript, colour, highlight. What their
     * paragraph's style draws, links and pictures stay. Shown again through the document, the style's look comes back
     * where the letters' own was.
     */
    fun clearFormatting(text: String, rich: RichTextDocument, start: Int, end: Int, basePt: Float): RichTextDocument {
        val scale = BASE_SP / basePt
        val blocks = rich.blocks.toMutableList()
        blockSpans(text, rich) { k, from, to ->
            val s = (start - from).coerceAtLeast(0); val e = (end - from).coerceAtMost(to - from)
            if (e <= s) return@blockSpans
            val b = rich.blocks[k]
            val pictures = b.spans.filterIsInstance<RichTextSpanMark.Image>().map { it.range }.toSet()
            val slanted = b.spans.filter { it is RichTextSpanMark.Shadow && it.argb == STYLE_SLANT }.map { it.range }.toSet()
            fun own(m: RichTextSpanMark) = m.range !in pictures && when (m) {
                is RichTextSpanMark.Bold, is RichTextSpanMark.Underline, is RichTextSpanMark.Strikethrough,
                is RichTextSpanMark.BaselineShift, is RichTextSpanMark.Highlight -> true
                is RichTextSpanMark.FontWeight -> m.weight != STYLE_WEIGHT
                is RichTextSpanMark.Italic -> m.range !in slanted
                is RichTextSpanMark.FontSize -> spOf(m.size)?.let { !isStyleSize(it / scale) } ?: false
                is RichTextSpanMark.TextColor -> (m.argb ushr 24).let { it != STYLE_ALPHA && it != CHANGE_ALPHA }
                else -> false
            }
            blocks[k] = b.copy(spans = b.spans.flatMap { m ->
                if (!own(m) || m.range.last < s || m.range.first >= e) listOf(m)
                else listOfNotNull((m.range.first until s).takeIf { !it.isEmpty() }?.let { m.withRange(it) },
                    (e..m.range.last).takeIf { !it.isEmpty() }?.let { m.withRange(it) })
            })
        }
        return rich.copy(blocks = blocks)
    }
    enum class Case(val label: String) { Sentence("Sentence case."), Lower("lowercase"), Upper("UPPERCASE"), Words("Capitalise Each Word") }

    /** [s] in [case], letter for letter (so each keeps its look); a letter whose case is longer (ß → SS) stays as it was. */
    fun inCase(s: String, case: Case, sentenceStart: Boolean, locale: java.util.Locale = java.util.Locale.getDefault()): String {
        val lower = s.lowercase(locale).takeIf { it.length == s.length } ?: s.map { c -> c.lowercase(locale).singleOrNull() ?: c }.joinToString("")
        fun up(c: Char) = c.toString().uppercase(locale).singleOrNull() ?: c
        // Greek in capitals has no accent (the dialytika stays): ΣΠΙΤΙ, ΑΫΠΝΙΑ — Latin capitals keep theirs.
        fun capital(c: Char): Char {
            if (Character.UnicodeScript.of(c.code) != Character.UnicodeScript.GREEK) return up(c)
            val bare = java.text.Normalizer.normalize(java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFD)
                .replace("\u0301", "").replace("\u0342", "").uppercase(locale), java.text.Normalizer.Form.NFC)
            return bare.singleOrNull() ?: up(c)
        }
        return when (case) {
            Case.Lower -> lower
            Case.Upper -> s.map(::capital).joinToString("")
            Case.Words, Case.Sentence -> buildString {
                var start = if (case == Case.Words) true else sentenceStart
                lower.forEachIndexed { i, c ->
                    append(if (start && c.isLetter()) up(c) else c)
                    if (c.isLetter()) start = false
                    if (case == Case.Words && !c.isLetterOrDigit() && c != '\'' && c != '’') start = true
                    if (case == Case.Sentence && c in ".!?;") start = true // ; is the Greek question mark
                }
            }
        }
    }

    /** Change case (Word's Aa) on the text from [start] to [end]: the letters change, their looks stay. */
    fun changeCase(text: String, rich: RichTextDocument, start: Int, end: Int, case: Case): RichTextDocument {
        val blocks = rich.blocks.toMutableList()
        blockSpans(text, rich) { k, from, to ->
            val s = (start - from).coerceAtLeast(0); val e = (end - from).coerceAtMost(to - from)
            if (e <= s) return@blockSpans
            val b = rich.blocks[k]
            // A sentence starts at the paragraph's start, or after . ! ? (then spaces) before the chosen text.
            val sentenceStart = b.text.substring(0, s).trimEnd().let { it.isEmpty() || it.last() in ".!?;" }
            blocks[k] = b.copy(text = b.text.substring(0, s) + inCase(b.text.substring(s, e), case, sentenceStart) + b.text.substring(e))
        }
        return rich.copy(blocks = blocks)
    }

    private fun RichTextSpanMark.withRange(r: IntRange): RichTextSpanMark = when (this) {
        is RichTextSpanMark.Bold -> copy(range = r)
        is RichTextSpanMark.Underline -> copy(range = r)
        is RichTextSpanMark.Strikethrough -> copy(range = r)
        is RichTextSpanMark.BaselineShift -> copy(range = r)
        is RichTextSpanMark.Highlight -> copy(range = r)
        is RichTextSpanMark.FontWeight -> copy(range = r)
        is RichTextSpanMark.Italic -> copy(range = r)
        is RichTextSpanMark.FontSize -> copy(range = r)
        is RichTextSpanMark.TextColor -> copy(range = r)
        else -> this
    }

    /** The editor's block the text at [at] is in. */
    fun blockAt(text: String, rich: RichTextDocument, at: Int): Int? {
        var found: Int? = null
        blockSpans(text, rich) { k, from, to -> if (found == null && at in from..to) found = k }
        return found
    }
    /**
     * Each editor block's place: its span in the editor's text, its paragraph, and where in that paragraph's own text
     * it starts (a line after a break starts after the break's letter).
     */
    data class BlockPlace(val from: Int, val to: Int, val para: Int, val at: Int)
    fun blockPlaces(text: String, rich: RichTextDocument): List<BlockPlace> {
        val out = ArrayList<BlockPlace>()
        var n = -1; var at = 0
        blockSpans(text, rich) { k, from, to ->
            if (!rich.blocks[k].isLineBreak) { n++; at = 0 } else at++
            out += BlockPlace(from, to, n, at)
            at += to - from
        }
        return out
    }
    /** The letters [from]..[to] of the editor's text as (paragraph, from, to) in the paragraphs' own text. */
    fun paragraphSpans(places: List<BlockPlace>, from: Int, to: Int): List<Triple<Int, Int, Int>> = places.mapNotNull { b ->
        val a = maxOf(from, b.from); val z = minOf(to, b.to)
        if (z <= a) null else Triple(b.para, b.at + a - b.from, b.at + z - b.from)
    }
    /** The editor's text where letters [a]..[b] of paragraph [para] are (the parts on each of its lines). */
    fun editorSpans(places: List<BlockPlace>, para: Int, a: Int, b: Int): List<IntRange> = places.filter { it.para == para }.mapNotNull { p ->
        val x = maxOf(a, p.at); val y = minOf(b, p.at + (p.to - p.from))
        if (y <= x) null else (p.from + x - p.at) until (p.from + y - p.at)
    }

    /** Where each paragraph starts in the editor's text (a line after a break is still its paragraph). */
    fun paragraphStarts(text: String, rich: RichTextDocument): List<Int> {
        val out = ArrayList<Int>()
        blockSpans(text, rich) { k, from, _ -> if (!rich.blocks[k].isLineBreak) out += from }
        return out
    }
    /** Where in the editor's text the block the text at [at] is in stands. */
    fun blockRange(text: String, rich: RichTextDocument, at: Int): Pair<Int, Int>? {
        var found: Pair<Int, Int>? = null
        blockSpans(text, rich) { _, from, to -> if (found == null && at in from..to) found = from to to }
        return found
    }
    private fun blockSpans(text: String, rich: RichTextDocument, each: (Int, Int, Int) -> Unit) {
        var pos = 0
        rich.blocks.forEachIndexed { k, b ->
            // A picture or table is one character in the block and another on the screen.
            val from = text.indexOf(b.text.replace(OBJECT_MARK, EDITOR_PICTURE_CHAR), pos).takeIf { it >= 0 } ?: return
            each(k, from, from + b.text.length)
            pos = from + b.text.length + 1
        }
    }

    /** A size (pt) drawn for a paragraph's style: a few hundredths over half points. */
    fun isStyleSize(pt: Float) = ((pt - STYLE_SIZE) * 2).let { kotlin.math.abs(it - it.roundToInt()) < 0.01f }
    /** Whether a look has a paragraph style's drawn marks in it, and those marks alone (to take them off). */
    fun styleDrawn(s: SpanStyle, basePt: Float) = drawnPart(s, basePt) != SpanStyle()
    fun drawnPart(s: SpanStyle, basePt: Float) = SpanStyle(
        fontSize = s.fontSize.takeIf { it.isSp && isStyleSize(it.value / (BASE_SP / basePt)) } ?: TextUnit.Unspecified,
        fontWeight = s.fontWeight?.takeIf { it.weight == STYLE_WEIGHT },
        color = s.color.takeIf { it != Color.Unspecified && (it.toArgb().toLong() ushr 24 and 0xFF) == STYLE_ALPHA } ?: Color.Unspecified,
        shadow = s.shadow?.takeIf { it.color.toArgb().toLong() and 0xFFFFFFFFL == STYLE_SLANT },
        fontStyle = if (s.shadow?.let { it.color.toArgb().toLong() and 0xFFFFFFFFL == STYLE_SLANT } == true) s.fontStyle else null,
    )


    /** How big a table stands in the editor: exactly the same layout as on its page. */
    fun tableSize(doc: OfficeDoc, table: DocTable, styles: DocStyles) = OfficePages.tableSize(doc, table, styles, styles.page.textWidth)
    /** The text's width (pt) when the editor shows the page itself; null: it fits the screen. */
    var pageWidthPt: Float? = null

    /** How wide the editor's text is (sp), so a table is laid out to fit it; set by the screen. */
    var editorWidthSp = 320f

    fun cellText(cell: DocCell) = cell.paras.joinToString("\n") { it.text }

    /**
     * A cell's text edited in its box: only the stretch that changed is replaced, so every other word keeps its own
     * look and every paragraph its own style (asked 2026-09-28: editing one letter made the whole cell look like its
     * first word). Text typed over text takes the look of what it replaces, else of the letter before it; a new line is a new paragraph like the one it
     * was typed in.
     */
    fun withCellText(cell: DocCell, text: String): DocCell {
        val old = cellText(cell)
        if (old == text) return cell
        var pre = 0
        while (pre < old.length && pre < text.length && old[pre] == text[pre]) pre++
        var suf = 0
        while (suf < old.length - pre && suf < text.length - pre && old[old.length - 1 - suf] == text[text.length - 1 - suf]) suf++
        val paras = cell.paras.ifEmpty { listOf(DocPara(listOf(DocRun("")))) }
        // Where a place in the cell's text falls: which paragraph, how far into it.
        val starts = paras.runningFold(0) { at, p -> at + p.text.length + 1 }
        fun locate(pos: Int) = paras.indices.first { pos <= starts[it] + paras[it].text.length }.let { it to pos - starts[it] }
        fun cut(runs: List<DocRun>, from: Int, to: Int): List<DocRun> {
            var at = 0
            return runs.mapNotNull { r -> val s = at; at += r.text.length
                val a = maxOf(from, s); val b = minOf(to, at)
                if (a < b) r.copy(text = r.text.substring(a - s, b - s)) else null }
        }
        val (a, ao) = locate(pre)
        val (b, bo) = locate(old.length - suf)
        // The look of the letter replaced, or (only typed) of the letter before.
        val runs = paras[a].runs
        val letter = if (b > a || bo > ao) ao else ao - 1
        var at = 0
        val look = (runs.firstOrNull { r -> at += r.text.length; letter >= 0 && letter < at && r.image == null }
            ?: runs.firstOrNull { it.image == null } ?: DocRun("")).copy(image = null)
        fun para(p: DocPara, runs: List<DocRun>) = p.copy(runs = OfficeFormats.tidy(runs).ifEmpty { listOf(look.copy(text = "")) })
        val lines = text.substring(pre, text.length - suf).split('\n')
        val left = cut(paras[a].runs, 0, ao); val right = cut(paras[b].runs, bo, paras[b].text.length)
        val middle = if (lines.size == 1) listOf(para(paras[a], left + look.copy(text = lines[0]) + right))
            else listOf(para(paras[a], left + look.copy(text = lines[0]))) +
                lines.subList(1, lines.size - 1).map { para(paras[a], listOf(look.copy(text = it))) } +
                para(paras[b], listOf(look.copy(text = lines.last())) + right)
        return cell.copy(paras = paras.subList(0, a) + middle + paras.subList(b + 1, paras.size))
    }

    /** The line break itself takes the look of the text after it (or before it, at a paragraph's end). */
    private fun breakRun(before: List<DocRun>, after: List<DocRun>) =
        (after.firstOrNull() ?: before.lastOrNull() ?: DocRun("")).copy(text = LINE_BREAK.toString(), image = null)

    /** Each line break with the look [breakRun] gives it — the look of an invisible character, so files are compared without it. */
    fun breaksLookAround(runs: List<DocRun>): List<DocRun> {
        val pieces = runs.flatMap { r -> if (LINE_BREAK !in r.text) listOf(r) else r.text.split(LINE_BREAK).flatMapIndexed { i, t -> listOfNotNull(if (i > 0) r.copy(text = LINE_BREAK.toString()) else null, r.copy(text = t).takeIf { t.isNotEmpty() }) } }
        return pieces.mapIndexed { i, r ->
            if (r.text != LINE_BREAK.toString()) r
            else breakRun(pieces.subList(0, i).filter { it.text != LINE_BREAK.toString() }, pieces.subList(i + 1, pieces.size).takeWhile { it.text != LINE_BREAK.toString() })
        }
    }

    /** A size in sp: the editor gives some (a heading's, carried on to the next line) relative to the text, in em. */
    fun spOf(size: TextUnit): Float? = when {
        size.isSp -> size.value
        size.isEm -> size.value * BASE_SP
        else -> null
    }

    /**
     * The editor draws every heading bold, so it cannot say which words of a heading the file has bold. A heading left
     * as it was keeps the file's own runs; an edited one takes its heading style's weight.
     */
    fun keepHeadingRuns(old: List<DocPara>, new: List<DocPara>): List<DocPara> {
        val unused = old.filter { it.heading > 0 }.groupBy { it.text }.mapValues { ArrayDeque(it.value) }
        return new.map { p ->
            if (p.heading == 0) return@map p
            val o = unused[p.text]?.removeFirstOrNull() ?: return@map p
            if (o.heading == p.heading && unbold(o.runs) == unbold(p.runs)) p.copy(runs = o.runs) else p
        }
    }
    private fun unbold(runs: List<DocRun>) = OfficeFormats.tidy(runs.map { it.copy(bold = false, font = null, comments = emptyList(), cite = null, change = null, note = null, control = null) })

    /** Paragraphs as they compare: a list's start number counts only where a list begins; bold in a heading, not at all. */
    fun comparable(paras: List<DocPara>) = paras.mapIndexed { i, p ->
        val firstOfList = p.list != null && (i == 0 || paras[i - 1].list == null)
        p.copy(runs = breaksLookAround(if (p.heading > 0) unbold(p.runs) else OfficeFormats.tidy(p.runs.map { it.copy(font = null, comments = emptyList(), cite = null, change = null, note = null, control = null) })).let(OfficeFormats::tidy), keep = null, list = p.list?.let { l -> l.copy(start = if (firstOfList && l.ordered && l.start != 1) l.start else null) })
    }
}

/** A document in Tetra's Files, for the Office home. */
internal data class OfficeFile(val file: File, val relative: String)

/** How many documents the Office home lists: the newest. */
private const val OFFICE_RECENT = 50

internal fun officeFiles(root: File): List<OfficeFile> = root.walkTopDown()
    .onEnter { it == root || (!it.name.startsWith(".") && it.name != "Trash") }
    .filter { it.isFile && OfficeKind.of(it.name) != null && !it.name.startsWith("~$") }
    .map { OfficeFile(it, it.relativeTo(root).path) }
    .sortedByDescending { it.file.lastModified() }
    .toList()

@Composable
internal fun OfficeHome(back: () -> Unit, hasAccess: Boolean, grant: () -> Unit, open: (String?) -> Unit) {
    val root = remember { TetraFolder.root(Environment.getExternalStorageDirectory()) }
    var files by remember { mutableStateOf<List<OfficeFile>?>(null) }
    var total by remember { mutableIntStateOf(0) }
    // Only the newest ones (asked 2026-09-28: all of them made the page heavy); the rest are in Files.
    LaunchedEffect(hasAccess) { if (hasAccess) withContext(Dispatchers.IO) { officeFiles(root) }.let { total = it.size; files = it.take(OFFICE_RECENT) } }
    BackHandler { back() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 136.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NewDocCard("Word", "New document", WORD_BLUE, true, Modifier.weight(1f)) { open(null) }
                    NewDocCard("Excel", "Coming later", EXCEL_GREEN, false, Modifier.weight(1f)) {}
                    NewDocCard("PowerPoint", "Coming later", POWERPOINT_ORANGE, false, Modifier.weight(1f)) {}
                }
            }
            item {
                Column(Modifier.padding(top = 14.dp, bottom = 2.dp)) {
                    Text("Recent documents", style = MaterialTheme.typography.titleMedium)
                    if (total > OFFICE_RECENT) Text("The $OFFICE_RECENT newest of $total — the rest are in Files.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!hasAccess) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tetra needs access to all files to list your documents.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = grant) { Text("Allow access") }
                }
            } else when (val list = files) {
                null -> item { Text("Looking…", style = MaterialTheme.typography.bodyMedium) }
                else -> if (list.isEmpty()) item { Text("No Word or OpenDocument files in Tetra's Files yet.", style = MaterialTheme.typography.bodyMedium) }
                else items(list, key = { it.relative }) { f ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().clickable { open(Uri.fromFile(f.file).toString()) }) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(painterResource(R.drawable.ic_file_text), contentDescription = null, tint = WORD_BLUE, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(f.relative.substringBeforeLast('/', "Files").let { "$it · " } + java.text.DateFormat.getDateInstance().format(java.util.Date(f.file.lastModified())),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        ModuleHeader("Office", back, Modifier.align(Alignment.TopCenter), art = R.drawable.office_art)
    }
}

@Composable private fun NewDocCard(app: String, label: String, color: Color, enabled: Boolean, modifier: Modifier, click: () -> Unit) = Surface(
    shape = MaterialTheme.shapes.extraLarge, color = color,
    contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.7f),
    modifier = modifier.height(104.dp).then(if (enabled) Modifier.clickable(onClick = click) else Modifier),
) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Icon(painterResource(if (enabled) R.drawable.ic_note_add else R.drawable.ic_office), contentDescription = null, modifier = Modifier.size(24.dp))
        Column {
            Text(app, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** Unsaved changes are kept as a file in the app's storage while typing, so closing the app never loses them. */
private fun draftFile(context: Context, key: String, kind: OfficeKind) =
    File(File(context.filesDir, "writer-drafts"), "${key.hashCode().toUInt()}.${kind.extension}")

/** A document's earlier versions: a folder per document (by its path), a file per version named by its time. */
private fun versionsDir(context: Context, file: File) = File(File(context.filesDir, "writer-history"), file.absolutePath.hashCode().toUInt().toString())

internal fun versions(context: Context, file: File): List<File> =
    versionsDir(context, file).listFiles()?.filter { it.isFile }?.sortedByDescending { it.name.substringBefore('.').toLongOrNull() ?: 0 }.orEmpty()

/** Before Writer saves over a document, the file as it was is kept (the last 3), so a save can be undone. */
private fun keepPrevious(context: Context, file: File) {
    val dir = versionsDir(context, file).apply { mkdirs() }
    file.copyTo(File(dir, "${System.currentTimeMillis()}.${file.extension}"), overwrite = true)
    versions(context, file).drop(3).forEach { it.delete() }
}

/** A name not yet taken in [dir]: "name (copy).docx", then "name (copy 2).docx"… */
internal fun freeCopyName(dir: File, stem: String, ext: String): File =
    generateSequence(1) { it + 1 }.map { File(dir, if (it == 1) "$stem (copy).$ext" else "$stem (copy $it).$ext") }.first { !it.exists() }

private enum class WriterTab(val label: String) { File("File"), Home("Home"), Insert("Insert"), Draw("Draw"), Design("Design"), Layout("Layout"), References("References"), Review("Review"), View("View"), Picture("Picture"), Table("Table") }
private data class TableCellRef(val key: String, val row: Int, val cell: Int)
/** A cell being typed in: [cell] holds its looks as they are changed, [value] the text in the box. */
private data class TableCellEdit(val ref: TableCellRef, val value: TextFieldValue, val cell: DocCell)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WriterScreen(back: () -> Unit, open: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { RichTextState() }
    var uri by remember { mutableStateOf(open) }
    val file: File? = uri?.let { Uri.parse(it) }?.takeIf { it.scheme == "file" }?.path?.let(::File)
    var kind by remember { mutableStateOf(OfficeKind.of(open ?: "") ?: OfficeKind.Docx) }
    var name by remember { mutableStateOf(file?.name ?: "New document.docx") }
    var original by remember { mutableStateOf<ByteArray?>(null) }
    var remeasured by remember { mutableStateOf(false) } // the text shown again with tables' true height (not an edit)
    var styleEdits by remember { mutableIntStateOf(0) } // counted, so the draft is kept after a style (or a picture: its wrap, size, place) changes
    var meta by remember { mutableStateOf(OfficeDoc(emptyList())) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(WriterTab.Home) }
    var message by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var zoom by remember { mutableIntStateOf(100) }
    // A phone's Print layout (the user, 2026-09-30: "there are no pages", zoom "does not go beyond the screen"): the page
    // laid out whole and drawn [pageZoom] times the screen's width — pinched larger it goes past the screen and pans both
    // ways, as the pages view. View › Mobile view keeps the text to the screen instead (remembered).
    var pageZoom by remember { mutableFloatStateOf(1f) }
    var mobileView by remember { mutableStateOf(context.getSharedPreferences("writer", Context.MODE_PRIVATE).getBoolean("mobileView", false)) }
    var finding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var replacing by remember { mutableStateOf(false) }
    val draftKey = open ?: "new"
    var reload by remember { mutableIntStateOf(0) } // a restored version is read in again
    var showVersions by remember { mutableStateOf(false) }
    val pictures = remember { HashMap<String, DocImage>() } // every picture in the editor, by its key
    remember { OfficeFonts.assets = context.applicationContext.assets; WriterUnits.load(context); WriterBridge.documentHeadings() }
    remember { WriterBridge.pictureGlyph = android.graphics.Paint().apply { textSize = 100f }.measureText(EDITOR_PICTURE_CHAR.toString()) / 100f }
    remember { WriterBridge.lineRatio = android.graphics.Paint().apply { textSize = 100f }.fontMetrics.let { it.descent - it.ascent } / 100f }
    var packageParts by remember { mutableStateOf<Map<String, ByteArray>>(emptyMap()) } // the file's parts, for its pictures
    var documentStyles by remember { mutableStateOf<DocStyles>(DocStyles.Plain) }
    // A tablet edits on the page itself (asked 2026-09-29, as on a desktop): the page's width and margins at the
    // editor's text size, centred, the ruler above. A phone (or a page too wide) keeps the text to the screen's width.
    val sheet = documentStyles.page
    val paper = Color(0xFF000000 or (sheet.color ?: 0xFFFFFF).toLong()) // Design › Page colour, in the editor too
    val screen = LocalConfiguration.current.screenWidthDp.dp
    // A tablet shows a document's comments in a pane beside the page, as Word does (asked 2026-09-29): the page made
    // smaller when both would not fit.
    val tablet = screen >= 700.dp
    var panelHidden by remember { mutableStateOf(false) }
    val paneOn = tablet && meta.comments.isNotEmpty() && !panelHidden
    val perPt100 = with(LocalDensity.current) { (BASE_SP / meta.basePt).sp.toDp() }
    // A tablet keeps the page whole (a landscape page, the pane beside it): shown smaller rather than reflowed.
    val printPhone = !tablet && !mobileView
    val shownZoom = if (printPhone) 100 else if (!tablet) zoom else minOf(zoom, ((screen - (if (paneOn) COMMENT_PANE + 12.dp else 0.dp) - 44.dp) / (documentStyles.page.width * perPt100) * 100).toInt()).coerceAtLeast(40)
    val perPt = with(LocalDensity.current) { (BASE_SP * shownZoom / 100f / meta.basePt).sp.toDp() }
    val onPage = printPhone || screen >= 700.dp && sheet.width * perPt + 24.dp <= screen
    WriterBridge.pageWidthPt = if (onPage) sheet.textWidth else null
    // Else the page's text is the screen less its margins (10 + 22 dp a side, below).
    WriterBridge.editorWidthSp = if (printPhone) sheet.textWidth * BASE_SP / meta.basePt
        else with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp - 64).dp.toSp().value }
    // The phone's page drawn this many times smaller or larger than laid out: the screen's width at pageZoom 1.
    val pageScale = if (printPhone) (screen - 20.dp) / (sheet.width * perPt) * pageZoom else 1f
    // A document opens view only, as its pages (asked 2026-09-27, as LibreOffice); Edit switches to the editor.
    var viewing by remember { mutableStateOf(open != null) }
    var pages by remember { mutableStateOf<List<LaidPage>?>(null) }
    var sizing by remember { mutableStateOf(false) }
    var wrapping by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var tableDialog by remember { mutableStateOf<String?>(null) } // "split" or "props" (the ⋯ page)
    // Merging: the first cell tapped, then the last; the cells between light up before they are merged.
    var mergeFrom by remember { mutableStateOf<TableCellRef?>(null) }
    var mergeTo by remember { mutableStateOf<TableCellRef?>(null) }
    var insertingTable by remember { mutableStateOf(false) }
    var selectedCell by remember { mutableStateOf<TableCellRef?>(null) }
    var cellEdit by remember { mutableStateOf<TableCellEdit?>(null) }
    var paragraphPage by remember { mutableStateOf<Pair<Set<Int>, ParaLook>?>(null) } // Home's ⋯ page: for which paragraphs, as they look
    // The pictures' keys in the order they stand in the text.
    val keys by remember { derivedStateOf { state.annotatedString.let { state.toRichTextDocument().blocks.flatMap { b -> b.spans.filterIsInstance<RichTextSpanMark.Image>().sortedBy { it.range.first }.map { it.url.removePrefix(PICTURE_URL) } } } } }
    /** The editor's text [from]–[to] counted as Word counts: a table's words where it stands, pictures not at all. */
    fun counted(text: String, from: Int = 0, to: Int = text.length): TextCounts {
        var n = text.substring(0, from).count { it == EDITOR_PICTURE_CHAR }
        val out = StringBuilder()
        for (i in from until to) text[i].let { c -> if (c != EDITOR_PICTURE_CHAR) out.append(c)
            else pictures[keys.getOrNull(n++)]?.table?.let { t -> out.append('\n').append(DocPara(emptyList(), table = t).countedText()).append('\n') } }
        return TextCounts.of(out.toString())
    }
    // The selected picture (a tap on it selects it): its place in the text and its key.
    val selectedPicture by remember { derivedStateOf {
        val text = state.annotatedString.text
        val sel = state.selection
        if (sel.collapsed) null else (sel.min until sel.max).firstOrNull { text.getOrNull(it) == EDITOR_PICTURE_CHAR }
            ?.let { i -> keys.getOrNull(text.substring(0, i).count { it == EDITOR_PICTURE_CHAR })?.let { i to it } }
    } }
    LaunchedEffect(selectedPicture) {
        if (selectedPicture == null) {
            if (tab == WriterTab.Picture || tab == WriterTab.Table) tab = WriterTab.Home
            tableDialog = null; mergeFrom = null; mergeTo = null
        }
    }

    // The editor does not hold each paragraph's own formatting (spacing, indents…); it is carried over from the file.
    fun current() = meta.copy(paras = OfficeFormats.carryFonts(meta.paras, WriterBridge.keepHeadingRuns(meta.paras,
        OfficeFormats.carryKeep(meta.paras, WriterBridge.fromEditor(state.toRichTextDocument(), meta.basePt, pictures)))))

    // Undo for what Writer changes itself (a style, spacing, a picture, a table, the header…): the editor's own undo
    // knows only typing, and forgets it whenever the document is shown again. Each such change keeps the whole
    // document from before it; typing between two changes is the editor's to undo, then one step back as a whole.
    val undone = remember { mutableStateListOf<WriterSnapshot>() }
    val redone = remember { mutableStateListOf<WriterSnapshot>() }
    var base by remember { mutableStateOf<WriterSnapshot?>(null) } // as it was after the last change (or opening)
    var changing by remember { mutableIntStateOf(0) }
    fun snapshot() = WriterSnapshot(current(), original, documentStyles, HashMap(pictures), state.selection)
    fun show(s: WriterSnapshot) {
        original = s.original; documentStyles = s.styles; meta = s.doc
        pictures.clear(); pictures.putAll(s.pictures)
        state.setRichTextDocument(WriterBridge.toEditor(s.doc, pictures, documentStyles), s.selection)
        state.history.clear()
        base = s; dirty = true; styleEdits++
    }
    /** A change of Writer's own, undone as one step. */
    fun <T> change(block: () -> T): T {
        if (changing++ == 0) {
            if (state.history.canUndo) base?.let { undone.add(it) } // the typing since the last change
            undone.add(snapshot())
            while (undone.size > UNDO_STEPS) undone.removeAt(0)
            redone.clear()
        }
        try { return block() } finally { if (--changing == 0) { state.history.clear(); base = snapshot() } }
    }
    fun undo() {
        if (state.history.canUndo) { state.history.undo(); return }
        val s = undone.removeLastOrNull() ?: return
        redone.add(snapshot()); show(s)
    }
    fun redo() {
        if (state.history.canRedo) { state.history.redo(); return }
        val s = redone.removeLastOrNull() ?: return
        undone.add(snapshot()); show(s)
    }
    // Typing after an undo: what was undone cannot come back (as in Word).
    LaunchedEffect(Unit) { snapshotFlow { state.history.canUndo }.collect { if (it) redone.clear() } }

    LaunchedEffect(reload) {
        loaded = false
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = file?.readBytes()
                val doc = bytes?.let { OfficeFormats.read(it, kind) } ?: OfficeDoc(listOf(DocPara(emptyList())), basePt = 12f) // Times New Roman 12, as APA 7 asks (the user's default, 2026-09-30)
                var draftPackage: ByteArray? = null
                val draft = draftFile(context, draftKey, kind).takeIf { it.isFile }?.let { d -> runCatching {
                    val draftBytes = d.readBytes()
                    val draftParts = OfficeFormats.unzip(draftBytes)
                    draftPackage = draftBytes // its styles, header and footer, as changed before it closed
                    val originalParts = bytes?.let { OfficeFormats.unzip(it) }.orEmpty()
                    // A picture added before the app closed lives only in the draft: it is new again, with its bytes.
                    OfficeFormats.read(draftBytes, kind).let { dd -> dd.copy(paras = dd.paras.map { p -> p.copy(runs = p.runs.map { r ->
                        val img = r.image
                        // Its wrapping and its place on the page (Draw on page) kept.
                        val place = PictureXml.placeOf(img ?: return@map r)?.takeIf { it.fromH == "page" && it.fromV == "page" && it.x != null && it.y != null }
                        if (img.part != null && img.part !in originalParts) r.copy(image = DocImage("d" + img.key, null, img.widthPt, img.heightPt, null, draftParts[img.part],
                            img.part.substringAfterLast('.'), PictureXml.wrapOf(img)?.takeIf { it != PictureWrap.InLine }, onPage = place?.let { it.x!! to it.y!! })) else r
                    }) }) }
                }.getOrNull() }
                packageParts = bytes?.let { OfficeFormats.unzip(it) }.orEmpty()
                // With a draft, the file as it will be written starts from the draft (styles, header and footer as changed).
                val start = draftPackage ?: bytes
                Triple(start, doc, draft)
            }
        }
        result.onSuccess { (bytes, doc, draft) ->
            original = bytes
            documentStyles = DocStyles.of(kind, bytes)
            pictures.clear()
            state.setRichTextDocument(WriterBridge.toEditor(doc, pictures, documentStyles))
            // What the editor shows, read back, must be what the file holds; if not, saving over it could change
            // what was not touched — so that file too is saved only as a copy.
            val shown = WriterBridge.comparable(WriterBridge.fromEditor(state.toRichTextDocument(), doc.basePt, pictures))
            val same = shown == WriterBridge.comparable(doc.paras)
            if (!same) Log.w("Writer", "shown differs: ${shown.zip(WriterBridge.comparable(doc.paras)).firstOrNull { it.first != it.second }}")
            meta = if (same) doc else doc.copy(unsupported = doc.unsupported + "formatting it cannot show exactly")
            dirty = false
            if (draft != null) {
                state.setRichTextDocument(WriterBridge.toEditor(draft.copy(basePt = doc.basePt), pictures, documentStyles))
                meta = meta.copy(paras = draft.paras, comments = draft.comments, sources = draft.sources) // the draft's paragraph formatting, comments and sources, not the file's
                dirty = true; message = "Your unsaved changes are back"
            }
            state.history.clear(); undone.clear(); redone.clear()
            // The cursor at the start (the editor leaves it at the end): the first tap brought the end into view.
            state.selection = androidx.compose.ui.text.TextRange(0)
            base = snapshot()
            loaded = true
        }.onFailure { failed = it.message ?: "This file could not be read." }
    }
    // Any change marks the document unsaved, and is kept as a draft after a short pause.
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        // The editor lays a list item's indent out again once it is drawn (its bullet measured): only its look, so the
        // indents are left out of what counts as a change (01_Text_and_Paragraphs opened as changed). Likewise it joins
        // neighbouring letters that look the same (runs the file kept apart — fonts it does not show): compared joined.
        snapshotFlow { Triple(state.annotatedString.let { a -> Triple(a.text, joinedSpans(a.spanStyles), a.paragraphStyles.map { it.item.copy(textIndent = null) to it.start }) }, meta.paras, styleEdits) }
            .drop(1).collectLatest { // meta: a paragraph's spacing set; a style changed
            if (remeasured) { remeasured = false; return@collectLatest } // tables shown again at their true height: no change

            dirty = true
            delay(1500)
            val doc = current()
            withContext(Dispatchers.IO) { runCatching { draftFile(context, draftKey, kind).apply { parentFile?.mkdirs() }.writeBytes(OfficeFormats.write(doc, kind, original)) } }
        }
    }
    // The pages, laid out again whenever they are shown (after editing, they show the edits).
    LaunchedEffect(viewing, loaded) {
        if (!viewing || !loaded) return@LaunchedEffect
        pages = null
        val doc = current()
        val parts = packageParts
        pages = withContext(Dispatchers.Default) { runCatching { OfficePages.layout(doc, documentStyles) { img -> img.bytes ?: img.part?.let { parts[it] } } }.onFailure { Log.e("Writer", "pages", it) }.getOrNull() }
        if (pages == null) viewing = false // could not lay it out: the editor still works
    }
    // Enter after a heading starts a Normal paragraph, as in Word: the editor carries the heading's drawn look (its
    // style's marks) on into the new, empty paragraph — so that look is taken off at once. (After a Title or any other
    // style the new paragraph keeps the style, as it is saved.)
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        snapshotFlow { state.annotatedString to state.selection }.collect {
            val carried = state.currentSpanStyle
            if (state.currentHeadingStyle != HeadingStyle.Normal || !WriterBridge.styleDrawn(carried, meta.basePt)) return@collect
            val rich = state.toRichTextDocument()
            val b = WriterBridge.blockAt(state.annotatedString.text, rich, state.selection.min) ?: return@collect
            val before = rich.blocks.subList(0, b).lastOrNull { !it.isLineBreak }
            if (rich.blocks[b].text.isEmpty() && (before?.headingLevel ?: 0) > 0) state.removeSpanStyle(WriterBridge.drawnPart(carried, meta.basePt))
        }
    }

    fun finishSave(target: File, bytes: ByteArray, saved: OfficeDoc) {
        draftFile(context, draftKey, kind).delete()
        original = bytes
        uri = Uri.fromFile(target).toString()
        name = target.name
        val back = OfficeFormats.read(bytes, kind)
        // A picture added in Writer is in the file now: from here on it goes back as the file has it, not added again.
        val before = saved.paras.flatMap { it.runs }.mapNotNull { it.image }
        val after = back.paras.flatMap { it.runs }.mapNotNull { it.image }
        if (before.size == after.size) before.zip(after).forEach { (b, a) -> if (b.raw == null) pictures[b.key] = DocImage(b.key, a.part, a.widthPt, a.heightPt, a.raw) }
        packageParts = OfficeFormats.unzip(bytes)
        documentStyles = DocStyles.of(kind, bytes)
        meta = meta.copy(unsupported = back.unsupported)
        dirty = false
    }
    /** Save over the file — only when nothing in it would be lost; otherwise a copy beside it. */
    fun save(asCopy: Boolean = false, then: () -> Unit = {}) {
        val f = file
        if (f == null) { naming = true; return }
        scope.launch {
            val doc = current()
            val copy = asCopy || meta.blocking.isNotEmpty()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = OfficeFormats.write(doc, kind, original)
                    val target = if (copy) freeCopyName(f.parentFile!!, f.nameWithoutExtension, kind.extension) else f
                    // Written beside it, then renamed over it: the file is either the old one or the new one, never half.
                    val temp = File(target.parentFile, ".${target.name}.tetra-saving")
                    temp.writeBytes(bytes)
                    if (!copy) keepPrevious(context, f)
                    check(temp.renameTo(target)) { "Could not save ${target.name}." }
                    // Its time is now (the phone's storage was seen keeping the old one): sync and the hash cache know it changed.
                    target.setLastModified(System.currentTimeMillis())
                    target to bytes
                }
            }
            result.onSuccess { (target, bytes) ->
                finishSave(target, bytes, doc)
                message = if (copy) "Saved as ${target.name} — the original is as it was" else "Saved"
                then()
            }.onFailure { message = it.message ?: "Could not save." }
        }
    }
    fun saveNew(typed: String) {
        val clean = typed.trim().removeSuffix(".${kind.extension}")
        if (clean.isEmpty() || '/' in clean) { message = "That is not a file name."; return }
        scope.launch {
            val dir = TextEditorStore(context).saveDirectory()
            val target = File(dir, "$clean.${kind.extension}")
            val doc = current()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    check(dir.isDirectory || dir.mkdirs()) { "Could not make ${dir.name}." }
                    check(!target.exists()) { "${target.name} is already there. Choose another name." }
                    OfficeFormats.write(doc, kind).also { target.writeBytes(it) }
                }
            }
            result.onSuccess { bytes -> naming = false; draftFile(context, draftKey, kind).delete(); finishSave(target, bytes, doc); message = "Saved in ${TextEditorStore(context).saveFolder}" }
                .onFailure { message = it.message }
        }
    }
    /** Export › PDF: beside the document (or in the save folder for a new one), never over another file. */
    fun exportPdf() {
        scope.launch {
            val doc = current()
            val dir = file?.parentFile ?: TextEditorStore(context).saveDirectory()
            val stem = name.substringBeforeLast('.')
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    check(dir.isDirectory || dir.mkdirs()) { "Could not make ${dir.name}." }
                    val target = generateSequence(1) { it + 1 }.map { File(dir, if (it == 1) "$stem.pdf" else "$stem ($it).pdf") }.first { !it.exists() }
                    target.outputStream().use { OfficePdf.write(doc, DocStyles.of(kind, original), it) { img -> img.bytes ?: img.part?.let { p -> packageParts[p] } } }
                    target
                }
            }
            result.onSuccess { message = "Saved ${it.name} in ${dir.name}" }.onFailure { message = it.message ?: "Could not make the PDF." }
        }
    }
    /** A version comes back: the document as it is now is kept as a version first, so restoring is undone the same way. */
    fun restore(version: File) {
        val f = file ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val temp = File(f.parentFile, ".${f.name}.tetra-saving")
                    version.copyTo(temp, overwrite = true)
                    keepPrevious(context, f)
                    check(temp.renameTo(f))
                    draftFile(context, draftKey, kind).delete()
                }.isSuccess
            }
            if (ok) { reload++; message = "Restored the file as it was before ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(version.name.substringBefore('.').toLong()))}" }
            else message = "Could not restore it."
        }
    }
    /**
     * Puts [img] at the cursor and reads the document back in (so its line makes room for it); returns where it is.
     * A mark is typed there and the picture put in its place in Writer's own document — the editor's own way of adding
     * a picture (insertHtml) mixed up the text after it (found 2026-09-28).
     */
    fun placePicture(img: DocImage): Int = change {
        state.addTextAfterSelection(PICTURE_SPOT.toString())
        val at = state.selection.min - 1
        state.setRichTextDocument(WriterBridge.toEditor(WriterBridge.putPicture(current(), img), pictures, documentStyles), androidx.compose.ui.text.TextRange(at, at + 1))
        dirty = true
        at
    }
    /** A picture dragged from [from] to [to] in the text: taken out and put in again there; it stays selected. */
    fun movePicture(from: Int, to: Int, key: String) = change {
        val img = pictures[key] ?: return@change
        if (to == from || to == from + 1) return@change
        state.removeTextRange(androidx.compose.ui.text.TextRange(from, from + 1))
        state.selection = androidx.compose.ui.text.TextRange(if (to > from) to - 1 else to)
        placePicture(img)
    }
    // Insert › Picture: from the phone's photos, at the cursor.
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) scope.launch {
            val img = withContext(Dispatchers.IO) { runCatching { preparePicture(context, picked) }.getOrNull() }
            if (img == null) { message = "Could not add that picture."; return@launch }
            pictures[img.key] = img
            val at = placePicture(img)
            state.selection = androidx.compose.ui.text.TextRange(at + 1) // the cursor after it
        }
    }
    /** A picture changed by the Picture tab: read back in, so its line makes room for its new size; the selection stays. */
    fun changePicture(key: String, img: DocImage) = change {
        pictures[key] = img
        val at = state.selection
        state.setRichTextDocument(WriterBridge.toEditor(current(), pictures, documentStyles), at)
        dirty = true; styleEdits++ // the text may be the same: the draft is kept all the same
    }
    // Picture › Replace: another photo in its place, as wide as it was, wrapped as it was.
    val replacePicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        val (at, key) = selectedPicture ?: return@rememberLauncherForActivityResult
        val old = pictures[key] ?: return@rememberLauncherForActivityResult
        if (picked != null) scope.launch {
            val img = withContext(Dispatchers.IO) { runCatching { preparePicture(context, picked) }.getOrNull() }
            if (img == null) { message = "Could not use that picture."; return@launch }
            changePicture(key, DocImage(key, null, old.widthPt, old.widthPt * img.heightPt / img.widthPt, null, img.bytes, img.extension, PictureXml.wrapOf(old)))
            state.selection = androidx.compose.ui.text.TextRange(at, at + 1)
        }
    }
    /** Picture › Turn and Flip: done away from the screen, then shown. */
    fun transform(turn: Int, flip: Boolean) {
        val (at, key) = selectedPicture ?: return
        val img = pictures[key] ?: return
        val bytes = img.bytes ?: img.part?.let { packageParts[it] } ?: return
        scope.launch {
            val out = withContext(Dispatchers.Default) { runCatching { transformedPicture(img, bytes, turn, flip) }.getOrNull() }
            if (out == null) { message = "Writer cannot turn this kind of picture."; return@launch }
            changePicture(key, out)
            state.selection = androidx.compose.ui.text.TextRange(at, at + 1)
        }
    }
    fun changeTable(key: String, img: DocImage, table: DocTable) {
        val (width, height) = WriterBridge.tableSize(meta, table, documentStyles)
        changePicture(key, img.with(widthPt = width, heightPt = height, table = table))
    }
    /** Spacing set on paragraphs [at]: only what differs from [was] is written, in the file's own words. */
    fun setSpacing(at: Set<Int>, was: ParaLook, now: ParaLook) = change {
        val cur = current()
        fun <T> changed(a: T, b: T) = b.takeIf { it != a }
        val line = now.lineMultiple.takeIf { now.lineExact == null && (was.lineExact != null || it != was.lineMultiple) }
        meta = cur.copy(paras = cur.paras.mapIndexed { i, p ->
            if (i !in at || p.table != null) p else p.copy(keep = OfficeFormats.withSpacing(p.keep, kind, line, changed(was.before, now.before),
                changed(was.after, now.after), changed(was.left, now.left), changed(was.firstLine, now.firstLine), changed(was.right, now.right)))
        })
        dirty = true
        // The editor shows indents: shown again, the cursor where it was.
        if (was.left != now.left || was.firstLine != now.firstLine) state.selection.let { sel -> state.setRichTextDocument(WriterBridge.toEditor(meta, pictures, documentStyles), sel) }
    }
    /** The paragraphs the cursor or selection is in, and how the first of them looks; null when they cannot be told. */
    fun chosen(): Pair<Set<Int>, ParaLook>? {
        val rich = state.toRichTextDocument()
        val cur = current()
        // Text typed beside a table makes one block two paragraphs: then the count differs and nothing is guessed.
        val at = WriterBridge.paragraphsAt(state.annotatedString.text, rich, state.selection.min, state.selection.max)
            .takeIf { it.isNotEmpty() && rich.blocks.count { !it.isLineBreak } == cur.paras.size } ?: return null
        return cur.paras.getOrNull(at.min())?.takeIf { it.table == null }?.let { at to documentStyles.look(it) }
    }
    /** A paragraph style put on the chosen paragraphs: the file's own (or a heading level), shown at once as it looks. */
    fun applyStyle(style: StyleEntry) = change {
        val at = chosen()?.first ?: run { message = "Put the cursor in a paragraph of text"; return@change }
        val cur = current()
        meta = cur.copy(paras = cur.paras.mapIndexed { i, p ->
            if (i !in at || p.table != null) p else p.copy(heading = style.heading, keep = (p.keep ?: DocKeep()).copy(style = style.id).takeIf { !it.isEmpty })
        })
        dirty = true
        state.selection.let { sel -> state.setRichTextDocument(WriterBridge.toEditor(meta, pictures, documentStyles), sel) }
    }
    /**
     * Insert › Page break (Word's Ctrl+Enter): the paragraph is split at the cursor and the part after it starts a page;
     * at a paragraph's start, that paragraph does. [blank]: an empty page between (a break before an empty paragraph
     * and before the text after it). Saved as "page break before" (both formats); Backspace at the paragraph's start
     * joins it to the one before, which takes the break away, as in Word.
     */
    fun insertPageBreak(blank: Boolean) = change {
        val text = state.annotatedString.text
        val rich = state.toRichTextDocument()
        val at = state.selection.min
        val atStart = WriterBridge.blockRange(text, rich, at)?.first == at && rich.blocks.getOrNull(WriterBridge.blockAt(text, rich, at) ?: -1)?.isLineBreak == false
        // The paragraph split: its part after the cursor keeps its style (the editor's Enter makes it Normal after a heading).
        val before = current()
        val split = WriterBridge.paragraphsAt(text, rich, at, at).singleOrNull()?.let { before.paras.getOrNull(it) }
            ?.takeIf { rich.blocks.count { !it.isLineBreak } == before.paras.size }
        state.selection = androidx.compose.ui.text.TextRange(at)
        if (!atStart) state.addTextAfterSelection("\n")
        if (blank) { state.addTextAfterSelection("\n") }
        val cursor = state.selection.min
        val now = state.toRichTextDocument()
        val cur = current()
        val here = WriterBridge.paragraphsAt(state.annotatedString.text, now, cursor, cursor).singleOrNull()
            ?.takeIf { now.blocks.count { !it.isLineBreak } == cur.paras.size } ?: run { message = "Put the cursor in a paragraph of text"; return@change }
        meta = cur.copy(paras = cur.paras.mapIndexed { i, p -> when {
            p.table != null -> p
            i == here -> (if (split != null && !atStart) p.copy(heading = split.heading, keep = split.keep) else p).let { it.copy(keep = OfficeFormats.withPageBreak(it.keep, kind, true)) }
            blank && i == here - 1 -> p.copy(heading = 0, list = null, keep = OfficeFormats.withPageBreak(null, kind, true)) // the empty page: a plain paragraph
            else -> p
        } })
        dirty = true
        state.setRichTextDocument(WriterBridge.toEditor(meta, pictures, documentStyles), androidx.compose.ui.text.TextRange(cursor))
    }
    /** Borders set on paragraphs [at] (the same for each: alike and side by side, they are one box, as in Word). */
    fun setBorders(at: Set<Int>, box: ParaBox) = change {
        val cur = current()
        meta = cur.copy(paras = cur.paras.mapIndexed { i, p -> if (i !in at || p.table != null) p else p.copy(keep = OfficeFormats.withBorders(p.keep, kind, box)) })
        dirty = true
        message = "Borders — they show on the pages"
    }
    /** Insert › Drop Cap on the paragraph at the cursor (null: taken off); the pages show it. */
    var dropCapPage by remember { mutableStateOf<Pair<Int, DropCap?>?>(null) } // the options page: its paragraph, as it is
    fun setDropCap(at: Int, drop: DropCap?, font: String? = null, color: Int? = null) = change {
        val cur = current()
        meta = cur.copy(paras = cur.paras.mapIndexed { i, p -> if (i != at || p.table != null) p
            else p.copy(keep = OfficeFormats.withDropCap(p.keep, kind, drop)).let { q -> if (drop == null) q else OfficeFormats.withLettersLook(q, drop.length, kind, font, color) } })
        dirty = true
        // The letters' colour is the editor's too: shown again.
        if (font != null || color != null) state.setRichTextDocument(WriterBridge.toEditor(meta, pictures, documentStyles), state.selection)
        message = if (drop == null) "Drop cap taken off" else "Drop cap — it shows on the pages"
    }
    /** Word's horizontal line: an empty paragraph with a line under it after this one, and a new paragraph to go on in. */
    fun horizontalLine() = change {
        val text = state.annotatedString.text
        val rich = state.toRichTextDocument()
        val end = WriterBridge.blockRange(text, rich, state.selection.max)?.second ?: return@change
        state.selection = androidx.compose.ui.text.TextRange(end)
        state.addTextAfterSelection("\n")
        state.addTextAfterSelection("\n")
        val cursor = state.selection.min
        val cur = current()
        val here = WriterBridge.paragraphsAt(state.annotatedString.text, state.toRichTextDocument(), cursor, cursor).singleOrNull()
            ?.takeIf { state.toRichTextDocument().blocks.count { !it.isLineBreak } == cur.paras.size && it > 0 } ?: return@change
        meta = cur.copy(paras = cur.paras.mapIndexed { i, p -> when (i) {
            here - 1 -> p.copy(heading = 0, list = null, keep = OfficeFormats.withBorders(null, kind, ParaBox(bottom = Border(0.75f, 0))))
            here -> p.copy(heading = 0, list = null, keep = OfficeFormats.withBorders(p.keep, kind, null))
            else -> p
        } })
        dirty = true
        state.setRichTextDocument(WriterBridge.toEditor(meta, pictures, documentStyles), androidx.compose.ui.text.TextRange(cursor))
    }
    // Comments (asked 2026-09-29): where each one's letters are in the editor, and the one the cursor is in.
    val commentsShown by remember { derivedStateOf {
        if (meta.comments.isEmpty()) emptyList() else {
            val text = state.annotatedString.text
            val places = WriterBridge.blockPlaces(text, state.toRichTextDocument())
            val live = current()
            val byId = live.comments.associateBy { it.id }
            live.paras.flatMapIndexed { i, p ->
                var at = 0
                // Replies: shown in their comment's card, not over the letters again.
                p.runs.flatMap { r -> r.comments.mapNotNull { id -> byId[id]?.takeIf { it.parent == null } }.flatMap { c -> WriterBridge.editorSpans(places, i, at, at + r.text.length).map { c to it } }.also { at += r.text.length } }
            }
        }
    } }
    val hereComment by remember { derivedStateOf { state.selection.min.let { at -> commentsShown.firstOrNull { (_, r) -> at in r.first..r.last + 1 }?.first } } }
    var replyTo by remember { mutableStateOf<DocComment?>(null) }
    fun replyComment(parent: DocComment, author: String, text: String) = change {
        val cur = current()
        meta = OfficeFormats.withReply(cur, parent.id, DocComment(OfficeFormats.newCommentId(cur, kind), author, java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString(), text,
            initials = author.split(' ').filter { it.isNotBlank() }.joinToString("") { it.take(1) }.uppercase()))
        dirty = true
    }
    fun resolveComment(c: DocComment) = change { meta = OfficeFormats.withDone(current(), c.id, !c.done); dirty = true }
    var commentsOpen by remember { mutableStateOf(false) } // the phone's sidebar
    var editorLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var commenting by remember { mutableStateOf(false) }
    /** Insert › Comment: on the chosen letters, or the word at the cursor. */
    fun addComment(author: String, text: String) = change {
        val t = state.annotatedString.text
        val sel = state.selection
        val (from, to) = if (!sel.collapsed) sel.min to sel.max else {
            var a = sel.min; var z = sel.min
            while (a > 0 && t[a - 1].isLetterOrDigit()) a--
            while (z < t.length && t[z].isLetterOrDigit()) z++
            a to z
        }
        val cur = current()
        val spans = WriterBridge.paragraphSpans(WriterBridge.blockPlaces(t, state.toRichTextDocument()), from, to)
        if (spans.isEmpty() || state.toRichTextDocument().blocks.count { !it.isLineBreak } != cur.paras.size) { message = "Choose some words first"; return@change }
        val initials = author.split(' ').filter { it.isNotBlank() }.joinToString("") { it.take(1) }.uppercase()
        meta = OfficeFormats.withComment(cur, spans, DocComment(OfficeFormats.newCommentId(cur, kind), author, java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString(), text, initials = initials))
        dirty = true
    }
    // References › Citations (APA 7): the sources, a citation at the cursor, the reference list, Update.
    var citing by remember { mutableStateOf(false) }
    var sourcesOpen by remember { mutableStateOf(false) }
    var sourceEdit by remember { mutableStateOf<DocSource?>(null) }
    var sourceNew by remember { mutableStateOf(false) }
    /** Shown again after a Writer change of the citations' letters. */
    fun reshow() = state.setRichTextDocument(WriterBridge.toEditor(meta, pictures, documentStyles), state.selection)
    fun saveSource(s: DocSource) = change {
        val cur = current()
        val tagged = if (s.tag.isBlank()) s.copy(tag = Citations.tagFor(kind, s, cur.sources.map { it.tag })) else s
        val list = if (cur.sources.any { it.tag == tagged.tag }) cur.sources.map { if (it.tag == tagged.tag) tagged else it } else cur.sources + tagged
        meta = Citations.update(cur.copy(sources = list), kind)
        dirty = true; reshow()
    }
    fun deleteSource(s: DocSource) = change { meta = current().let { it.copy(sources = it.sources.filter { x -> x.tag != s.tag }) }; dirty = true }
    fun insertCitation(chosenSources: List<DocSource>, page: String) = change {
        val cite = Citations.cite(kind, chosenSources.map { it.tag }, page)
        val text = Citations.text(cite, meta.sources)
        if (!state.selection.collapsed) state.removeSelectedText()
        // After a word, a space first (Word's), not part of the citation.
        state.annotatedString.text.getOrNull(state.selection.min - 1)?.takeIf { !it.isWhitespace() && it != '(' }?.let { state.addTextAfterSelection(" ") }
        val start = state.selection.min
        state.addTextAfterSelection(text)
        val t = state.annotatedString.text
        val spans = WriterBridge.paragraphSpans(WriterBridge.blockPlaces(t, state.toRichTextDocument()), start, start + text.length)
        meta = OfficeFormats.withCite(current(), spans, cite)
        dirty = true
    }
    fun insertReferenceList() = change {
        val cur = current()
        meta = Citations.insertList(cur, kind, chosen()?.first?.minOrNull() ?: cur.paras.size)
        dirty = true; reshow()
        message = "References — in APA's order, from the sources cited"
    }
    fun updateCitations() = change { meta = Citations.update(current(), kind); dirty = true; reshow(); message = "Citations and references updated" }
    // References › Footnotes (asked 2026-09-30): a note's number in the text; its words in a bar and a dialog.
    /** Each note's reference, where it is in the editor: the note and its number. */
    val notesShown by remember { derivedStateOf {
        if (meta.paras.none { p -> p.runs.any { it.note != null } }) emptyList() else {
            val text = state.annotatedString.text
            val places = WriterBridge.blockPlaces(text, state.toRichTextDocument())
            current().paras.flatMapIndexed { i, p ->
                var at = 0
                p.runs.flatMap { r -> (r.note?.let { n -> WriterBridge.editorSpans(places, i, at, at + r.text.length).map { Triple(n, r.text, it) } } ?: emptyList()).also { at += r.text.length } }
            }
        }
    } }
    val hereNote by remember { derivedStateOf { state.selection.min.let { at -> notesShown.firstOrNull { (_, _, r) -> at in r.first..r.last + 1 } } } }
    var noteEditing by remember { mutableStateOf<Pair<DocNote, String>?>(null) } // the note, its number
    fun insertNote(end: Boolean) = change {
        if (!state.selection.collapsed) state.selection = androidx.compose.ui.text.TextRange(state.selection.max)
        val start = state.selection.min
        state.addTextAfterSelection("1") // its number, worked out below
        val t = state.annotatedString.text
        val spans = WriterBridge.paragraphSpans(WriterBridge.blockPlaces(t, state.toRichTextDocument()), start, start + 1)
        val style = if (kind == OfficeKind.Docx) (if (end) "EndnoteText" else "FootnoteText") else (if (end) "Endnote" else "Footnote")
        val note = DocNote(end, listOf(DocPara(emptyList(), keep = DocKeep(style))))
        meta = OfficeFormats.numberNotes(OfficeFormats.withNote(current(), spans, note))
        dirty = true
        reshow()
        state.selection = androidx.compose.ui.text.TextRange(start + 1)
        noteEditing = note to (notesShown.firstOrNull { it.first == note }?.second ?: "")
    }
    fun saveNote(old: DocNote, text: String?) = change {
        val new = text?.let { t ->
            val keep = old.paras.firstOrNull()?.keep
            // Unchanged words keep the note's own formatting; changed ones are its paragraphs anew, in its style.
            if (t == old.paras.joinToString("\n") { it.text }) old
            else DocNote(old.end, t.split('\n').map { line -> DocPara(if (line.isEmpty()) emptyList() else listOf(DocRun(line)), keep = keep) })
        }
        meta = OfficeFormats.replaceNote(current(), old, new)
        dirty = true
        reshow()
        noteEditing = null
    }
    fun deleteComment(id: String) = change { meta = OfficeFormats.withoutComment(current(), id); dirty = true }
    fun commentActions(open: (DocComment) -> Unit) = CommentActions({ c -> meta.comments.filter { it.parent == c.id } }, open, { deleteComment(it.id) }, { replyTo = it }, { resolveComment(it) })
    // Review › Track changes (asked 2026-09-30): where each change is in the editor's text, and the one at the cursor.
    val changesShown by remember { derivedStateOf {
        if (!TrackChanges.any(meta)) emptyList() else {
            val text = state.annotatedString.text
            val places = WriterBridge.blockPlaces(text, state.toRichTextDocument())
            TrackChanges.list(current()).flatMap { c ->
                if (c.from == c.to) listOfNotNull(places.lastOrNull { it.para == c.para }?.let { Triple(c, it.to, it.to) }) // a paragraph mark: its end
                else WriterBridge.editorSpans(places, c.para, c.from, c.to).map { Triple(c, it.first, it.last + 1) }
            }
        }
    } }
    /** The change at a cursor: the one it is inside, else the one just before it (between two, never both). */
    fun changeAt(at: Int) = (changesShown.firstOrNull { (_, a, z) -> at in a until z } ?: changesShown.lastOrNull { (_, a, z) -> at in a..z })?.first
    val hereChange by remember { derivedStateOf { changeAt(state.selection.min) } }
    // Forms (asked 2026-10-01): where each content control's letters are, and the one the cursor is in (at its edge too).
    val controlsShown by remember { derivedStateOf {
        if (meta.paras.none { p -> p.runs.any { it.control != null } }) emptyList() else {
            val places = WriterBridge.blockPlaces(state.annotatedString.text, state.toRichTextDocument())
            current().paras.flatMapIndexed { i, p ->
                var at = 0
                p.runs.flatMap { r -> (r.control?.let { c -> WriterBridge.editorSpans(places, i, at, at + r.text.length).map { Triple(c, r.text, it) } } ?: emptyList()).also { at += r.text.length } }
            }
        }
    } }
    val hereControl by remember { derivedStateOf { state.selection.min.let { at -> controlsShown.firstOrNull { (_, _, r) -> at in r.first..r.last + 1 } } } }
    fun fillControl(old: String, pr: String, text: String) = change {
        meta = Forms.fill(current(), old, pr, text)
        dirty = true
        reshow()
    }
    // Track changes on (Word files): each edit recorded as the reviewer's, compared with the document as it was.
    val prefs = remember { context.getSharedPreferences("writer", Context.MODE_PRIVATE) }
    var tracking by remember { mutableStateOf(false) }
    var askingName by remember { mutableStateOf(false) }
    LaunchedEffect(loaded, tracking) {
        if (!loaded || !tracking) return@LaunchedEffect
        val author = prefs.getString("author", "").orEmpty()
        meta = current() // what was typed before it was switched on is the document, not an edit
        snapshotFlow { state.annotatedString.text }.drop(1).collect {
            val now = current()
            if (now.paras.map { it.text } == meta.paras.map { it.text }) return@collect // a Writer change, shown (Accept, a style…): not an edit
            val me = DocChange(false, author, java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString())
            val r = TrackChanges.record(meta.paras, now.paras, me, state.selection.min)
            meta = now.copy(paras = r.paras)
            if (r.reshow) state.setRichTextDocument(WriterBridge.toEditor(meta, pictures, documentStyles), androidx.compose.ui.text.TextRange(r.cursor))
        }
    }
    // Review › Spelling (asked 2026-09-30): the phone's spell checker, Greek and English, as one types.
    val speller = remember { WriterSpelling(context) }
    DisposableEffect(Unit) { onDispose { speller.close() } }
    var spelling by remember { mutableStateOf(prefs.getBoolean("spelling", true)) }
    var ignored by remember { mutableStateOf(emptySet<String>()) }
    /** The words that look misspelled: where, the word, its suggestions (as the text was when checked). */
    var misspelled by remember { mutableStateOf<List<Triple<IntRange, String, List<String>>>>(emptyList()) }
    var typingAt by remember { mutableIntStateOf(-1) } // the word being typed is not marked until one leaves it
    LaunchedEffect(loaded) {
        var last = ""
        snapshotFlow { state.annotatedString.text to state.selection }.collect { (t, sel) -> typingAt = if (t != last) sel.min else -1; last = t }
    }
    LaunchedEffect(loaded, spelling, ignored) {
        if (!loaded || !spelling) { misspelled = emptyList(); return@LaunchedEffect }
        snapshotFlow { state.annotatedString.text }.collectLatest { text ->
            delay(500)
            val ranges = WriterSpelling.words(text)
            val bad = speller.check(ranges.map { text.substring(it) }.toSet())
            if (speller.silent || !speller.available) { speller.silent = false
                message = "The phone's spell checker is not answering — turn on Spell checker (Gboard) in Settings › Languages & input, and Spell check in Gboard's Text correction"
                delay(60_000) } // not said again at every letter
            misspelled = ranges.mapNotNull { r -> text.substring(r).let { w -> if (w in ignored) null else bad[w]?.let { Triple(r, w, it) } } }
        }
    }
    /** The marked words now: still as they were checked, and not the one being typed. */
    val spellingShown by remember { derivedStateOf {
        val text = state.annotatedString.text
        misspelled.filter { (r, w) -> r.last < text.length && text.regionMatches(r.first, w, 0, w.length) && typingAt !in r.first..r.last + 1 }
    } }
    val hereWord by remember { derivedStateOf { state.selection.let { sel -> spellingShown.firstOrNull { (r) -> sel.min in r.first..r.last + 1 && sel.max <= r.last + 1 } } } }
    fun replaceWord(r: IntRange, with: String) {
        state.selection = androidx.compose.ui.text.TextRange(r.first, r.last + 1)
        state.removeSelectedText(); state.addTextAfterSelection(with)
    }
    fun nextMisspelled() {
        val at = state.selection.max
        val next = spellingShown.firstOrNull { it.first.first >= at } ?: spellingShown.firstOrNull()
        if (next == null) { message = if (spelling) "No spelling mistakes found" else "Spelling is off"; return }
        state.selection = androidx.compose.ui.text.TextRange(next.first.first, next.first.last + 1)
    }
    /** The next (or previous) change, selected. */
    fun goChange(forward: Boolean, from: Int = state.selection.min) {
        val starts = changesShown.map { it.second to it.third }.distinct().sortedBy { it.first }
        val to = if (forward) starts.firstOrNull { it.first > from } else starts.lastOrNull { it.first < from }
        if (to == null) { message = if (starts.isEmpty()) "No tracked changes" else if (forward) "No more changes after this" else "No changes before this"; return }
        state.selection = androidx.compose.ui.text.TextRange(to.first, to.second)
    }
    /** The changes at the cursor (or in the selection) accepted or rejected — or all of them — then on to the next. */
    fun decideChanges(accept: Boolean, all: Boolean = false) = change {
        val cur = current()
        val sel = state.selection
        val chosen = if (all) emptyList() else if (sel.collapsed) listOfNotNull(changeAt(sel.min))
            else changesShown.filter { (_, a, z) -> a < sel.max && z > sel.min || a == z && a in sel.min..sel.max }.map { it.first }.distinct()
        if (!all && chosen.isEmpty()) { message = "No change here — Next finds one"; return@change }
        val at = if (all) sel.min else changesShown.filter { it.first in chosen }.minOfOrNull { it.second } ?: sel.min // before meta changes (it is read again then)
        meta = if (all) TrackChanges.all(cur, accept) else TrackChanges.decide(cur, chosen, accept)
        dirty = true
        state.setRichTextDocument(WriterBridge.toEditor(meta, pictures, documentStyles), androidx.compose.ui.text.TextRange(at.coerceIn(0, state.annotatedString.text.length)))
        message = when { all && accept -> "All changes accepted"; all -> "All changes rejected"; accept -> "Accepted"; else -> "Rejected" }
        if (!all) goChange(true, at - 1)
    }
    /**
     * References › Table of contents: put at the cursor's paragraph (or, if the document has one, brought up to date),
     * its page numbers from the pages as Writer lays them out. ponytail: laid out on the main thread; fine for a
     * thesis-sized document, move off it if a long one stalls.
     */
    fun insertContents() = change {
        val cur = current()
        val at = chosen()?.first?.minOrNull() ?: cur.paras.size
        val parts = packageParts
        meta = runCatching { Contents.insert(cur, kind, documentStyles, at) { d, pages ->
            OfficePages.layout(d, documentStyles, pages) { img -> img.bytes ?: img.part?.let { parts[it] } } } }
            .onFailure { Log.e("Writer", "contents", it) }.getOrNull() ?: run { message = "The contents could not be made."; return@change }
        dirty = true
        state.setRichTextDocument(WriterBridge.toEditor(meta, pictures, documentStyles), state.selection)
        message = "Contents from ${cur.paras.count { it.heading in 1..3 }} headings"
    }
    fun chooseParagraphs(then: (Set<Int>, ParaLook) -> Unit) {
        chosen()?.let { (at, look) -> then(at, look) } ?: run { message = "Put the cursor in a paragraph of text" }
    }
    // The ruler shows the indents of the paragraph the cursor is in, and its marks set them (as Word's).
    var rulerAt by remember { mutableStateOf<Pair<Set<Int>, ParaLook>?>(null) }
    // …and the ribbon which style it has.
    LaunchedEffect(loaded, viewing) {
        if (!loaded || viewing) return@LaunchedEffect
        snapshotFlow { state.selection to meta.paras }.collectLatest { delay(200); rulerAt = chosen() }
    }
    val hereStyle = rulerAt?.first?.min()?.let { i -> meta.paras.getOrNull(i) }?.let { documentStyles.styleOf(it) }
    var stylesPage by remember { mutableStateOf(false) }
    var editingStyle by remember { mutableStateOf<StyleEntry?>(null) }
    var headerPage by remember { mutableStateOf(false) }
    var symbolPage by remember { mutableStateOf(false) }
    var pagePage by remember { mutableStateOf(false) }
    var shapesPage by remember { mutableStateOf(false) }
    /** Insert › Text box (the key null) or Picture › Edit text (a text box's key): its words being typed. */
    var boxing by remember { mutableStateOf<Pair<String?, String>?>(null) }
    var wordArt by remember { mutableStateOf(false) } // Insert › WordArt: its words and colour being chosen
    /** Insert › Chart (the key null) or Picture › Edit data (a chart's key): the chart being made. */
    var charting by remember { mutableStateOf<Pair<String?, DocChart>?>(null) }
    /**
     * The drawing being drawn: on [pages] (each with the paragraph a drawing there is anchored in), or in a box
     * [boxPt] wide when there are none; [edit] is the key of a drawing drawn on again, [strokes] what it had.
     */
    class Drawing(val pages: List<LaidPage> = emptyList(), val anchors: List<Int> = emptyList(), val start: Int = 0, val edit: String? = null,
        val strokes: List<InkStroke> = emptyList(), val boxPt: Float = 0f, val boxAspect: Float = 0.6f)
    var drawing by remember { mutableStateOf<Drawing?>(null) }
    /**
     * A drawing in front of the text (the user, 2026-09-30: drawings go on top of the text): anchored where a paragraph
     * starts, as Word does — never between two letters of a word — [anchor] or the cursor's; at [place] on the page, or
     * (from the drawing box) at the paragraph's top left; the Picture tab moves it into the text if wanted.
     */
    fun putDrawing(bytes: ByteArray, wPt: Float, hPt: Float, place: Pair<Float, Float>?, anchor: Int?) {
        val img = DocImage("n" + java.util.UUID.randomUUID(), null, wPt, hPt, null, bytes, "png", PictureWrap.InFront, onPage = place)
        pictures[img.key] = img
        val text = state.annotatedString.text; val rich = state.toRichTextDocument()
        val cursor = state.selection
        val start = anchor?.let { WriterBridge.paragraphStarts(text, rich).getOrNull(it) } ?: WriterBridge.blockRange(text, rich, cursor.min)?.first
        start?.let { state.selection = androidx.compose.ui.text.TextRange(it) }
        placePicture(img)
        val shift = if (start != null && cursor.min >= start) 1 else 0
        state.selection = androidx.compose.ui.text.TextRange(cursor.min + shift, cursor.max + shift)
        message = "Drawn in front of the text — the pages show it in its place"
    }
    // Pictures in front of or behind the text (asked 2026-10-01: a cover's logos showed as tiny marks in the corner):
    // drawn on the page in their place, as the pages show them — from the pages' layout, measured from their paragraph's
    // top, so typing above them moves them with it. Worked out again after each Writer change, not each keystroke.
    var floatsShown by remember { mutableStateOf<Map<String, FloatShown>>(emptyMap()) }
    LaunchedEffect(meta, styleEdits, documentStyles, onPage, loaded) { // styleEdits: a picture changed (meta stays)
        if (!loaded) return@LaunchedEffect
        val doc = current()
        val over = doc.paras.indices.flatMap { j -> doc.paras[j].runs.mapNotNull { r -> r.image?.takeIf { PictureXml.wrapOf(it).let { w -> w == PictureWrap.InFront || w == PictureWrap.Behind } }?.let { it.key to j } } }.toMap()
        if (!loaded || !onPage || over.isEmpty()) { floatsShown = emptyMap(); return@LaunchedEffect }
        delay(300)
        val parts = packageParts
        val tops = FloatArray(doc.paras.size)
        val laid = withContext(Dispatchers.Default) { runCatching { OfficePages.layout(doc, documentStyles, null, IntArray(doc.paras.size), tops) { img -> img.bytes ?: img.part?.let { parts[it] } } }.getOrNull() } ?: return@LaunchedEffect
        floatsShown = laid.flatMap { page -> page.floats.mapNotNull { f -> f.key?.let { k -> over[k]?.let { j -> k to FloatShown(f.x - page.page.left, f.y - tops[j], f.w, f.h, f.behind, f.x, f.y) } } } }.toMap()
    }
    /** The pages of [doc] as the pages view lays them out, and the page each paragraph starts on (-1: none). */
    suspend fun laidOut(doc: OfficeDoc): Pair<List<LaidPage>, IntArray>? {
        val parts = packageParts
        val index = IntArray(doc.paras.size) { -1 }
        return withContext(Dispatchers.Default) { runCatching { OfficePages.layout(doc, documentStyles, pageIndexOf = index) { img -> img.bytes ?: img.part?.let { parts[it] } } }
            .onFailure { Log.e("Writer", "draw", it) }.getOrNull() }?.let { it to index } ?: run { message = "The page could not be laid out."; null }
    }
    /**
     * Draw › Draw on page: the page the cursor is on, as the pages show it, to draw over (‹ › for another); the drawing
     * is anchored in the first paragraph of text that starts on its page, placed from the page's corner. A page no
     * paragraph starts on (the middle of a long one) is left out.
     */
    fun drawOnPage() = chooseParagraphs { at, _ ->
        val cur = current()
        scope.launch {
            val (laid, index) = laidOut(cur) ?: return@launch
            val anchors = laid.indices.map { k -> cur.paras.indices.firstOrNull { index[it] == k && cur.paras[it].table == null } ?: -1 }
            val usable = laid.indices.filter { anchors[it] >= 0 }
            drawing = Drawing(usable.map { laid[it] }, usable.map { anchors[it] }, usable.indexOf(index[at.min()]).coerceAtLeast(0))
        }
    }
    /** Picture › Position: the page a floating picture is on, as laid out, and its key. */
    var positioning by remember { mutableStateOf<Pair<LaidPage, String>?>(null) }
    fun position(key: String) = scope.launch {
        val (laid, _) = laidOut(current()) ?: return@launch
        positioning = laid.firstOrNull { p -> p.floats.any { it.key == key } }?.let { it to key }
            ?: run { message = "It is not on a page (in a table's cell?)"; null }
    }
    /** A drawing made in Writer (its strokes ride in its PNG), drawn on again: on its page if it is placed on one, else in its box. */
    fun editDrawing(key: String) {
        val img = pictures[key] ?: return
        val (strokes, drawnAspect) = (img.bytes ?: img.part?.let { packageParts[it] })?.let { InkPicture.strokesOf(it) } ?: return
        val ky = img.heightPt / img.widthPt / drawnAspect // sized unevenly since: its strokes stretch with it
        val place = PictureXml.placeOf(img)?.takeIf { it.fromH == "page" && it.fromV == "page" && it.x != null && it.y != null }
        val cur = current()
        val para = cur.paras.indexOfFirst { p -> p.runs.any { it.image?.key == key } }
        if (place == null || para < 0) {
            drawing = Drawing(edit = key, strokes = strokes.map { s -> InkStroke(s.points.map { Offset(it.x, it.y * ky) }, s.color, s.widthMm, s.highlighter) },
                boxPt = img.widthPt, boxAspect = img.heightPt / img.widthPt)
            return
        }
        scope.launch {
            // Its page without it (it is drawn again), its strokes where they are on it.
            val (laid, index) = laidOut(cur.copy(paras = cur.paras.map { p -> p.copy(runs = p.runs.filter { it.image?.key != key }) })) ?: return@launch
            val page = laid.getOrNull(index[para]) ?: return@launch
            val w = page.page.width
            drawing = Drawing(listOf(page), listOf(para), edit = key, strokes = strokes.map { s ->
                InkStroke(s.points.map { Offset((place.x!! + it.x * img.widthPt) / w, (place.y!! + it.y * ky * img.widthPt) / w) }, s.color, s.widthMm, s.highlighter) })
        }
    }
    /** Design › APA 7 paper ([ApaPaper]): styles, page number, margins, and what the page asked for; one step to undo. */
    var apaPage by remember { mutableStateOf(false) }
    fun applyApa(clean: Boolean, title: ApaTitle?) = change {
        val entries = documentStyles.paragraphStyles()
        val parts = runCatching { ApaPaper.file(OfficeFormats.unzip(original ?: OfficeFormats.write(OfficeDoc(emptyList()), kind)), kind, documentStyles) }
            .onFailure { Log.e("Writer", "apa", it) }.getOrNull() ?: run { message = "The document could not be set up."; return@change }
        original = OfficeFormats.zip(parts, if (kind == OfficeKind.Odt) "mimetype" else null)
        documentStyles = DocStyles.of(kind, original)
        var cur = current()
        if (clean) cur = ApaPaper.clean(cur, kind)
        if (title != null) {
            // The title page; the text on the next page, under the title again (APA: bold, centred).
            val text = cur.paras.takeUnless { it.size == 1 && it[0].text.isEmpty() && it[0].table == null } ?: listOf(DocPara(emptyList()))
            val again = DocPara(listOf(DocRun(title.title.trim())), align = DocAlign.Center,
                keep = OfficeFormats.withPageBreak(OfficeFormats.withSpacing(DocKeep(style = entries[1].id), kind, firstLine = 0f), kind, true))
            cur = cur.copy(paras = ApaPaper.titlePage(title, kind, entries[1].id) + again + text)
        }
        if (Citations.find(cur, kind) != null) cur = Citations.update(cur, kind) // the references on a page of their own
        meta = cur
        dirty = true; styleEdits++; reshow()
        message = "APA 7 — shown on the pages (the eye at the top)"
    }
    /** Design › Page colour and Page borders: into the file as it will be written; shown on the pages at once. */
    fun setDesign(color: Int?, border: Border?) = change {
        val parts = runCatching { OfficeFormats.unzip(original ?: OfficeFormats.write(OfficeDoc(emptyList()), kind)).let {
            if (kind == OfficeKind.Docx) PageDesign.docx(it, color, border) else PageDesign.odt(it, color, border) } }
            .onFailure { Log.e("Writer", "design", it) }.getOrNull() ?: run { message = "The page could not be changed."; return@change }
        original = OfficeFormats.zip(parts, if (kind == OfficeKind.Odt) "mimetype" else null)
        documentStyles = DocStyles.of(kind, original)
        dirty = true; styleEdits++
        message = "Shown on the pages (the eye at the top)"
    }
    /** Design › Watermark: [text] across every page (null: none), into the file's headers as it will be written. */
    fun setWatermark(text: String?) = change {
        val parts = runCatching { OfficeFormats.unzip(original ?: OfficeFormats.write(OfficeDoc(emptyList()), kind)).let {
            if (kind == OfficeKind.Docx) Watermark.docx(it, text, sheet) else Watermark.odt(it, text, sheet) } }
            .onFailure { Log.e("Writer", "watermark", it) }.getOrNull() ?: run { message = "The watermark could not be set."; return@change }
        original = OfficeFormats.zip(parts, if (kind == OfficeKind.Odt) "mimetype" else null)
        documentStyles = DocStyles.of(kind, original)
        dirty = true; styleEdits++
        message = if (text == null) "Watermark taken away" else "Shown on the pages (the eye at the top)"
    }
    var watermarking by remember { mutableStateOf(false) } // the custom watermark's page
    /** Layout: the page's size, orientation and margins, for every section, into the file as it will be written; shown at once. */
    fun setPage(page: DocPage) = change {
        val parts = runCatching { OfficeFormats.unzip(original ?: OfficeFormats.write(OfficeDoc(emptyList()), kind)).let {
            if (kind == OfficeKind.Docx) PageSetup.docx(it, page) else PageSetup.odt(it, page, documentStyles.page) } }
            .onFailure { Log.e("Writer", "page", it) }.getOrNull() ?: run { message = "The page could not be set."; return@change }
        original = OfficeFormats.zip(parts, if (kind == OfficeKind.Odt) "mimetype" else null)
        documentStyles = DocStyles.of(kind, original)
        dirty = true; styleEdits++
    }
    /** Layout › Hyphenation: automatic or none, into the file as it will be written; the pages break words by it. */
    fun setHyphenation(on: Boolean) = change {
        val parts = runCatching { PageDesign.hyphenation(OfficeFormats.unzip(original ?: OfficeFormats.write(OfficeDoc(emptyList()), kind)), kind, on) }
            .onFailure { Log.e("Writer", "hyphenation", it) }.getOrNull() ?: run { message = "Hyphenation could not be set."; return@change }
        original = OfficeFormats.zip(parts, if (kind == OfficeKind.Odt) "mimetype" else null)
        documentStyles = DocStyles.of(kind, original)
        dirty = true; styleEdits++
        if (on) message = "Automatic hyphenation — the pages show it (the eye at the top)"
    }
    /** Layout › Line numbers ([restart]: Word's, or null for none), into the file as it will be written; the pages show them. */
    fun setLineNumbers(restart: String?) = change {
        val parts = runCatching { PageSetup.lineNumbers(OfficeFormats.unzip(original ?: OfficeFormats.write(OfficeDoc(emptyList()), kind)), kind, restart) }
            .onFailure { Log.e("Writer", "line numbers", it) }.getOrNull() ?: run { message = "The line numbers could not be set."; return@change }
        original = OfficeFormats.zip(parts, if (kind == OfficeKind.Odt) "mimetype" else null)
        documentStyles = DocStyles.of(kind, original)
        dirty = true; styleEdits++
        if (restart != null) message = "Line numbers — they show on the pages (the eye at the top)"
    }
    var datePage by remember { mutableStateOf(false) }
    /** Symbol or date typed at the cursor, over what is chosen (as typing; the editor's own undo). */
    fun typeIn(s: String) { if (!state.selection.collapsed) state.removeSelectedText(); state.addTextAfterSelection(s) }
    /** The document's header, footer and page numbers set (Word), into the file as it will be written; shown on the pages. */
    fun setHeaderFooter(hf: HeaderFooter) = change {
        val parts = runCatching { OfficeFormats.unzip(original ?: OfficeFormats.write(OfficeDoc(emptyList()), kind)).let {
            if (kind == OfficeKind.Docx) HeaderFooterEdit.docx(it, hf, documentStyles.page) else HeaderFooterEdit.odt(it, hf, documentStyles.page) } }
            .onFailure { Log.e("Writer", "header", it) }.getOrNull() ?: run { message = "The header could not be set."; return@change }
        original = OfficeFormats.zip(parts, if (kind == OfficeKind.Odt) "mimetype" else null)
        documentStyles = DocStyles.of(kind, original)
        dirty = true; styleEdits++
        message = "Shown on the pages (the eye at the top)"
    }
    /** A style's own definition changed (every paragraph with it changes), in the file's styles; shown at once. */
    fun editStyle(entry: StyleEntry, edit: StyleChange) = change {
        val key = documentStyles.styleKey(entry) ?: run { message = "This style cannot be changed here."; return@change }
        val cur = current()
        val parts = OfficeFormats.unzip(original ?: OfficeFormats.write(OfficeDoc(emptyList()), kind))
        val path = stylesPath(parts, kind)
        val xml = parts[path]?.let { String(it, Charsets.UTF_8) } ?: run { message = "This document has no styles to change."; return@change }
        parts[path] = runCatching { if (kind == OfficeKind.Docx) StyleEdit.docx(xml, key, entry.heading, documentStyles.normalId, edit) else StyleEdit.odt(xml, key, entry.heading, edit) }
            .onFailure { Log.e("Writer", "style", it) }.getOrNull()?.toByteArray() ?: run { message = "This style could not be changed."; return@change }
        original = OfficeFormats.zip(parts, if (kind == OfficeKind.Odt) "mimetype" else null)
        documentStyles = DocStyles.of(kind, original)
        meta = cur
        dirty = true; styleEdits++
        state.selection.let { sel -> state.setRichTextDocument(WriterBridge.toEditor(cur, pictures, documentStyles), sel) }
    }
    /** Design › Style sets: Normal, Title and Headings 1–3 changed together, one step to undo. */
    fun applyStyleSet(set: StyleSet) = change {
        val cur = current()
        val parts = OfficeFormats.unzip(original ?: OfficeFormats.write(OfficeDoc(emptyList()), kind))
        val path = stylesPath(parts, kind)
        val xml = parts[path]?.let { String(it, Charsets.UTF_8) } ?: run { message = "This document has no styles to change."; return@change }
        parts[path] = runCatching { set.applyTo(xml, kind, documentStyles) }.onFailure { Log.e("Writer", "style set", it) }.getOrNull()?.toByteArray()
            ?: run { message = "The style set could not be applied."; return@change }
        original = OfficeFormats.zip(parts, if (kind == OfficeKind.Odt) "mimetype" else null)
        documentStyles = DocStyles.of(kind, original)
        meta = cur
        dirty = true; styleEdits++
        state.selection.let { sel -> state.setRichTextDocument(WriterBridge.toEditor(cur, pictures, documentStyles), sel) }
        message = "${set.label}: Normal, Title and the headings"
    }
    val rulerIndents = rulerAt?.second?.let { RulerIndents(it.left, it.firstLine, it.right) }
    fun setIndents(now: RulerIndents) {
        val (at, look) = rulerAt ?: return
        setSpacing(at, look, look.copy(left = now.left, firstLine = now.firstLine, right = now.right))
    }
    fun commitCell() {
        val edit = cellEdit ?: return
        val img = pictures[edit.ref.key] ?: run { cellEdit = null; return }
        val table = img.table ?: run { cellEdit = null; return }
        val row = table.rows.getOrNull(edit.ref.row) ?: run { cellEdit = null; return }
        val cell = row.cells.getOrNull(edit.ref.cell) ?: run { cellEdit = null; return }
        val typed = WriterBridge.withCellText(edit.cell, edit.value.text)
        if (typed != cell) {
            val rows = table.rows.toMutableList()
            val cells = row.cells.toMutableList()
            cells[edit.ref.cell] = typed
            rows[edit.ref.row] = row.copy(cells = cells)
            changeTable(edit.ref.key, img, table.copy(rows = rows))
        }
        cellEdit = null
    }
    fun pictureBytes(img: DocImage) = img.bytes ?: img.part?.let { packageParts[it] }
    /** Save picture as…: into the phone's Pictures/Tetra, where Photos and Files find it. */
    fun savePicture(img: DocImage) {
        val bytes = pictureBytes(img) ?: return
        scope.launch {
            val ext = img.part?.substringAfterLast('.')?.lowercase(Locale.ROOT) ?: img.extension
            val ok = withContext(Dispatchers.IO) { runCatching {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "${name.substringBeforeLast('.')} picture.$ext")
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "image/$ext")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Tetra")
                }
                val target = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                context.contentResolver.openOutputStream(target)!!.use { it.write(bytes) }
            }.isSuccess }
            message = if (ok) "Saved in Pictures/Tetra" else "Could not save the picture."
        }
    }
    fun leave() { commitCell(); if (dirty) leaving = true else back() }
    BackHandler { leave() }

    val ink = Color.Black
    val w = islandContentColor()
    // On a phone the top bar goes away while scrolling down the document and comes back when scrolling up (asked
    // 2026-09-29, for room); a tablet has room and keeps it.
    var barShown by remember { mutableStateOf(true) }
    val phone = screen < 700.dp
    val barScroll = remember(phone) { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
            if (phone && available.y < -6f) barShown = false else if (available.y > 6f) barShown = true
            return androidx.compose.ui.geometry.Offset.Zero
        }
    } }
    LaunchedEffect(viewing, finding) { barShown = true }
    Box(Modifier.fillMaxSize().background(Color(0xFF1B1D1E)).statusBarsPadding().imePadding()
        .then(Modifier.nestedScroll(barScroll))) {
        Column(Modifier.fillMaxSize()) {
            // Find and Replace (Word's magnifier): the matches are found in the text as the editor holds it, so a
            // match's place is where the editor selects it.
            if (finding) {
                val text = state.annotatedString.text
                val matches = remember(text, query) { if (query.isEmpty()) emptyList() else Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(text).map { it.range }.toList() }
                val at = matches.indexOfFirst { it.first >= state.selection.min }.let { if (it < 0) 0 else it }
                fun go(i: Int) { matches.getOrNull(Math.floorMod(i, matches.size.coerceAtLeast(1)))?.let { state.selection = androidx.compose.ui.text.TextRange(it.first, it.last + 1) } }
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tool(R.drawable.ic_close, "Close find", w) { finding = false }
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Find") }, modifier = Modifier.weight(1f).focusRequester(focus),
                            suffix = { Text(if (query.isEmpty()) "" else if (matches.isEmpty()) "0" else "${at + 1}/${matches.size}") })
                        Tool(R.drawable.ic_keyboard_arrow_up, "Previous", w, enabled = matches.isNotEmpty()) { go(at - 1) }
                        Box(Modifier.graphicsLayer { rotationZ = 180f }) { Tool(R.drawable.ic_keyboard_arrow_up, "Next", w, enabled = matches.isNotEmpty()) { go(if (state.selection.collapsed) at else at + 1) } }
                        Tool(R.drawable.ic_swap_horiz, "Replace", w, on = replacing) { replacing = !replacing }
                    }
                    if (replacing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(replacement, { replacement = it }, singleLine = true, placeholder = { Text("Replace with") }, modifier = Modifier.weight(1f))
                        RibbonButton("Replace", w, enabled = matches.isNotEmpty()) {
                            val m = matches.getOrNull(at) ?: return@RibbonButton
                            state.replaceTextRange(androidx.compose.ui.text.TextRange(m.first, m.last + 1), replacement)
                        }
                        RibbonButton("All", w, enabled = matches.isNotEmpty()) {
                            state.history.group { matches.asReversed().forEach { m -> state.replaceTextRange(androidx.compose.ui.text.TextRange(m.first, m.last + 1), replacement) } }
                            message = "Replaced ${matches.size}"
                        }
                    }
                }
            } else
            // Word's top bar: back, the name (a dot while unsaved), find, undo and redo.
            androidx.compose.animation.AnimatedVisibility(barShown) { Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Tool(R.drawable.ic_chevron_left, "Back", w) { leave() }
                Text((if (dirty) "• " else "") + name, color = w, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                // A tablet counts words and characters in the top bar, of the selection when there is one, as Word's status
                // bar does (asked 2026-09-30); a tap shows all the counts. A phone has them in File › About this file.
                if (!phone && loaded) {
                    val text = state.annotatedString.text
                    val sel = state.selection
                    val tables = keys.map { pictures[it]?.table } // a cell edited changes no text
                    val all = remember(text, tables) { counted(text) }
                    val part = remember(text, sel, tables) { if (sel.collapsed) null else counted(text, sel.min, sel.max) }
                    Text(if (part == null) "${plural(all.words, "word")} · ${plural(all.withSpaces, "character")}"
                        else "%,d of ${plural(all.words, "word")} · %,d of ${plural(all.withSpaces, "character")}".format(part.words, part.withSpaces),
                        color = w.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        modifier = Modifier.clickable { details = true }.padding(horizontal = 8.dp))
                }
                if (viewing) {
                    Tool(R.drawable.ic_share, "Share", w, enabled = file?.isFile == true) { file?.let { shareDriveFile(context, it) } }
                    if (dirty) Tool(R.drawable.ic_save, "Save", w, enabled = loaded) { save() }
                    RibbonButton("Edit", w, R.drawable.ic_edit, enabled = loaded) { viewing = false }
                } else {
                Tool(R.drawable.ic_visibility, "Pages", w, enabled = loaded) { viewing = true }
                Tool(R.drawable.ic_search, "Find and replace", w, enabled = loaded) { finding = true }
                Tool(R.drawable.ic_undo, "Undo", w, enabled = state.history.canUndo || undone.isNotEmpty()) { undo() }
                Tool(R.drawable.ic_redo, "Redo", w, enabled = state.history.canRedo || redone.isNotEmpty()) { redo() }
                Tool(R.drawable.ic_save, "Save", w, enabled = loaded && (dirty || file == null || cellEdit != null)) { commitCell(); save() }
                }
            }
            if (loaded && meta.unsupported.isNotEmpty()) {
                val copyOnly = meta.blocking.isNotEmpty()
                Surface(color = if (copyOnly) Category.Office.color.copy(alpha = 0.22f) else Color(0xFF2A2E30), contentColor = Color.White,
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { details = true }) {
                    Text(if (copyOnly) "Writer does not show everything in this document (${meta.blocking.take(3).joinToString()}${if (meta.blocking.size > 3) "…" else ""}). Saving makes a copy; the original stays as it is."
                        else "Saving here simplifies ${meta.simplified.joinToString()} to Writer's own.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            } }
            if (viewing && failed == null) Box(Modifier.weight(1f).fillMaxWidth()) {
                pages?.let { PagesView(it, with(LocalDensity.current) { (BASE_SP / meta.basePt).sp.toDp() }, Modifier.fillMaxSize()) }
                    ?: Text("Laying out the pages…", color = w.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center))
            } else
            // The page: white, as the document will print — its own colours must show as they are.
            Column(Modifier.weight(1f).fillMaxWidth()) {
            val margins = if (onPage) androidx.compose.foundation.layout.PaddingValues(start = sheet.left * perPt, end = sheet.right * perPt, top = sheet.top * perPt, bottom = 180.dp)
                else androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 180.dp)
            // With the comments' pane, page and pane are centred together.
            val shift = if (paneOn && onPage) (COMMENT_PANE + 12.dp) / 2 else 0.dp
            if (onPage && loaded && !printPhone) BoxWithConstraints(Modifier.fillMaxWidth()) {
                val x = (maxWidth - sheet.width * perPt) / 2 - shift
                IndentRuler(sheet, perPt, x, rulerIndents) { setIndents(it) }
            }
            val downScroll = rememberScrollState(); val sideScroll = rememberScrollState()
            Box(Modifier.weight(1f).fillMaxWidth()
                .then(if (printPhone) Modifier.pinchZoom({ pageZoom }, 1f, 5f, sideScroll, downScroll) { pageZoom = it } else Modifier)
                .verticalScroll(downScroll)
                .then(if (printPhone) Modifier.horizontalScroll(sideScroll) else Modifier)
                .padding(horizontal = 10.dp, vertical = 8.dp)) {
                if (paneOn && onPage && loaded) {
                    val density = LocalDensity.current
                    val first = commentsShown.groupBy { it.first }.mapValues { (_, spans) -> spans.minOf { it.second.first } }
                    CommentPane(first.entries.sortedBy { it.value }.map { (c, at) ->
                        c to (sheet.top * perPt + with(density) { (editorLayout?.takeIf { at < it.layoutInput.text.length }?.let { it.getLineTop(it.getLineForOffset(at)) } ?: 0f).toDp() })
                    }, hereComment, Modifier.align(Alignment.TopCenter).offset(x = sheet.width * perPt / 2 - shift + 12.dp + COMMENT_PANE / 2).width(COMMENT_PANE),
                        commentActions { c -> first[c]?.let { state.selection = androidx.compose.ui.text.TextRange(it) } })
                }
                Surface(color = paper, shape = RoundedCornerShape(if (onPage) 0.dp else 6.dp),
                    modifier = (if (printPhone) Modifier.scaledBy(pageScale).width(sheet.width * perPt).heightIn(min = sheet.height * perPt)
                        else if (onPage) Modifier.align(Alignment.TopCenter).offset(x = -shift).width(sheet.width * perPt).heightIn(min = sheet.height * perPt) else Modifier.fillMaxWidth()).heightIn(min = 520.dp)) {
                    when {
                        failed != null -> Text(failed!!, color = ink, modifier = Modifier.padding(24.dp))
                        !loaded -> Text("Opening…", color = ink.copy(alpha = 0.5f), modifier = Modifier.padding(24.dp))
                        else -> Box(Modifier.fillMaxWidth()) {
                            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                            // A selected picture or table has its blue frame: the selection's shading over its (invisible,
                            // picture-tall) letter only showed as a grey block behind it.
                            val selection = androidx.compose.foundation.text.selection.LocalTextSelectionColors.current
                            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.text.selection.LocalTextSelectionColors provides
                                if (selectedPicture != null) androidx.compose.foundation.text.selection.TextSelectionColors(selection.handleColor, Color.Transparent) else selection) {
                            BasicRichTextEditor(state, textStyle = TextStyle(color = ink, fontSize = (BASE_SP * shownZoom / 100).sp),
                                cursorBrush = SolidColor(Color(0xFF1A73E8)), onTextLayout = { layout = it; editorLayout = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 520.dp).padding(margins))
                            }
                            // The pictures, drawn where their character sits: its left, standing on its line's bottom.
                            layout?.let { l ->
                                val text = l.layoutInput.text.text
                                val density = LocalDensity.current
                                val scale = BASE_SP / meta.basePt
                                // Where each picture goes, worked out first; then drawn (no skipping inside the drawing).
                                val placed = text.indices.filter { text[it] == EDITOR_PICTURE_CHAR }.mapIndexedNotNull { n, i ->
                                    keys.getOrNull(n)?.let { pictures[it] }?.let { img -> Triple(i, img, l.getBoundingBox(i).left to l.getLineBaseline(l.getLineForOffset(i))) }
                                }
                                // A table's line must be exactly its height: the editor's line for its letter came out 0.77 of what the
                                // font's measures promise (the user's CV, 2026-09-30 — each table's last line cut off by the next).
                                // Learnt from the first table laid out, and the text shown again once with the real measure.
                                LaunchedEffect(l) {
                                    val (i, img) = placed.firstOrNull { it.second.table != null }?.let { it.first to it.second } ?: return@LaunchedEffect
                                    val (_, h) = WriterBridge.shownSize(img, scale)
                                    val line = l.getLineBottom(l.getLineForOffset(i)) - l.getLineTop(l.getLineForOffset(i))
                                    val real = WriterBridge.lineRatio * line / with(density) { h.sp.toPx() }
                                    if (kotlin.math.abs(real - WriterBridge.lineRatio) > 0.01f) {
                                        WriterBridge.lineRatio = real
                                        remeasured = true // not an edit
                                        val sel = state.selection
                                        state.setRichTextDocument(WriterBridge.toEditor(current(), pictures, documentStyles), sel)
                                    }
                                }
                                var drag by remember { mutableStateOf(Offset.Zero) } // how far the selected picture is being dragged
                                // A page break before a paragraph: the editor has no pages, so a dashed line says where one starts.
                                // A form's fields: a light box behind each (Word shades them), deeper where the cursor is.
                                if (controlsShown.isNotEmpty()) androidx.compose.foundation.Canvas(Modifier.matchParentSize().padding(margins)) {
                                    controlsShown.forEach { (c, _, r) -> if (r.last < text.length) drawPath(l.getPathForRange(r.first, r.last + 1),
                                        if (c == hereControl?.first) Color(0x552196F3) else Color(0x222196F3)) }
                                }
                                // Commented letters: tinted (the comment itself in the bar above the ribbon).
                                if (commentsShown.isNotEmpty()) androidx.compose.foundation.Canvas(Modifier.matchParentSize().padding(margins)) {
                                    // A resolved one only while the cursor is in it (Word hides them).
                                    commentsShown.forEach { (c, r) -> if (r.last < text.length && (!c.done || c == hereComment)) drawPath(l.getPathForRange(r.first, r.last + 1),
                                        if (c == hereComment) Color(0x66FFB300) else Color(0x33FFB300)) }
                                }
                                // Spelling: a red wavy line under each word that looks misspelled.
                                if (spellingShown.isNotEmpty()) androidx.compose.foundation.Canvas(Modifier.matchParentSize().padding(margins)) {
                                    val red = Color(0xFFE53935); val step = 3.dp.toPx(); val amp = 1.2.dp.toPx()
                                    spellingShown.forEach { (r) ->
                                        if (r.last >= text.length) return@forEach
                                        for (line in l.getLineForOffset(r.first)..l.getLineForOffset(r.last)) {
                                            val a = maxOf(r.first, l.getLineStart(line)); val z = minOf(r.last + 1, l.getLineEnd(line, true))
                                            if (z <= a) continue
                                            val x1 = l.getBoundingBox(a).left; val x2 = l.getBoundingBox(z - 1).right
                                            val y = l.getLineBaseline(line) + 2.5.dp.toPx()
                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(x1, y); var x = x1; var up = true
                                                while (x < x2) { val nx = minOf(x + step, x2); lineTo(nx, if (up) y - amp else y + amp); x = nx; up = !up }
                                            }
                                            drawPath(path, red, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                                        }
                                    }
                                }
                                // Tracked changes: inserted letters underlined, deleted struck through (their colour is the author's,
                                // set on the letters), a paragraph mark's change a ¶, and a bar in the margin beside each changed line.
                                if (changesShown.isNotEmpty()) androidx.compose.foundation.Canvas(Modifier.matchParentSize().padding(margins)) {
                                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = (BASE_SP * shownZoom / 100).sp.toPx() * 0.8f }
                                    changesShown.forEach { (c, a, z) ->
                                        if (z > text.length) return@forEach
                                        val color = Color(0xFF000000 or TrackChanges.color(c.author).toLong())
                                        val first = l.getLineForOffset(a); val last = l.getLineForOffset(if (z > a) z - 1 else a)
                                        for (line in first..last) {
                                            drawLine(color, Offset(-8.dp.toPx(), l.getLineTop(line)), Offset(-8.dp.toPx(), l.getLineBottom(line)), 1.5.dp.toPx())
                                            if (z == a) {
                                                paint.color = color.toArgb()
                                                drawContext.canvas.nativeCanvas.drawText("¶", l.getHorizontalPosition(a, true) + 2.dp.toPx(), l.getLineBaseline(line), paint)
                                                continue
                                            }
                                            val s = maxOf(a, l.getLineStart(line)); val e = minOf(z, l.getLineEnd(line, true))
                                            if (e <= s) continue
                                            val x1 = l.getBoundingBox(s).left; val x2 = l.getBoundingBox(e - 1).right
                                            val base = l.getLineBaseline(line)
                                            when (c.kind) {
                                                TrackChanges.Kind.Inserted -> drawLine(color, Offset(x1, base + 2.dp.toPx()), Offset(x2, base + 2.dp.toPx()), 1.dp.toPx())
                                                TrackChanges.Kind.Deleted -> (base - (base - l.getLineTop(line)) * 0.3f).let { y -> drawLine(color, Offset(x1, y), Offset(x2, y), 1.dp.toPx()) }
                                                else -> Unit
                                            }
                                        }
                                    }
                                }
                                // …and a paragraph's borders, as a light outline (the pages draw them as they print).
                                val looks = remember(meta.paras, documentStyles) { meta.paras.map { if (it.table != null) null else documentStyles.look(it) } }
                                val breaks = remember(looks) { looks.indices.filter { it > 0 && looks[it]?.pageBreakBefore == true } }
                                val boxed = remember(looks) { looks.indices.filter { looks[it]?.box != null } }
                                val starts = remember(text, breaks, boxed) { if (breaks.isEmpty() && boxed.isEmpty()) emptyList() else state.toRichTextDocument().let { rich ->
                                    WriterBridge.paragraphStarts(text, rich).takeIf { it.size == meta.paras.size } } ?: emptyList() }
                                if (starts.isNotEmpty()) androidx.compose.foundation.Canvas(Modifier.matchParentSize().padding(margins)) {
                                    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                                    breaks.forEach { i -> starts.getOrNull(i)?.takeIf { it <= text.length }?.let { o ->
                                        val y = l.getLineTop(l.getLineForOffset(o))
                                        drawLine(Color(0xFF9AA0A6), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
                                    } }
                                    boxed.forEach { i ->
                                        val box = looks[i]?.box ?: return@forEach
                                        val a = starts.getOrNull(i)?.takeIf { it <= text.length } ?: return@forEach
                                        val z = ((starts.getOrNull(i + 1) ?: (text.length + 1)) - 1).coerceIn(a, text.length)
                                        val top = l.getLineTop(l.getLineForOffset(a)); val bottom = l.getLineBottom(l.getLineForOffset(z))
                                        val c = Color(0xFF5F6368); val sw = 1.dp.toPx(); val x1 = size.width
                                        val joinsAbove = looks.getOrNull(i - 1)?.box == box; val joinsBelow = looks.getOrNull(i + 1)?.box == box
                                        if (box.top != null && !joinsAbove) drawLine(c, Offset(0f, top), Offset(x1, top), sw)
                                        if (box.between != null && joinsAbove) drawLine(c, Offset(0f, top), Offset(x1, top), sw)
                                        if (box.bottom != null && !joinsBelow) drawLine(c, Offset(0f, bottom), Offset(x1, bottom), sw)
                                        if (box.left != null) drawLine(c, Offset(0f, top), Offset(0f, bottom), sw)
                                        if (box.right != null) drawLine(c, Offset(x1, top), Offset(x1, bottom), sw)
                                    }
                                }
                                if (starts.isNotEmpty()) Box(Modifier.matchParentSize().padding(margins)) {
                                    breaks.forEach { i -> starts.getOrNull(i)?.takeIf { it <= text.length }?.let { o ->
                                        val y = with(density) { l.getLineTop(l.getLineForOffset(o)).toDp() }
                                        Text("Page break", color = Color(0xFF80868B), fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).offset(y = y - 14.dp).background(paper).padding(horizontal = 4.dp))
                                    } }
                                }
                                val paraStarts = remember(text, floatsShown) { if (floatsShown.isEmpty()) emptyList() else WriterBridge.paragraphStarts(text, state.toRichTextDocument()) }
                                Box(Modifier.matchParentSize().padding(margins)) {
                                    placed.forEach { (i, img, at) ->
                                        // In front of or behind the text: in its place on the page, its own size (from its paragraph's top).
                                        val float = floatsShown[img.key]
                                        val left = float?.let { with(density) { (it.x * scale).sp.toPx() } } ?: at.first
                                        val bottom = at.second
                                        val (w, h) = float?.let { it.w * scale to it.h * scale } ?: WriterBridge.shownSize(img, scale)
                                        val y = if (float != null) (l.getLineTop(l.getLineForOffset(paraStarts.lastOrNull { it <= i } ?: i)) + with(density) { (float.y * scale).sp.toPx() }).toInt()
                                            else if (img.table != null) l.getLineTop(l.getLineForOffset(i)).toInt()
                                            else with(density) { (bottom - h.sp.toPx()).toInt() } // standing on the line, as Word places an inline picture
                                        val size = with(density) { Modifier.size(w.sp.toDp(), h.sp.toDp()) }
                                        val shownWidth = with(density) { w.sp.toPx() }
                                        val shownHeight = with(density) { h.sp.toPx() }
                                        val selected = selectedPicture?.first == i
                                        val table = img.table
                                        // A table is laid out once (not on every keystroke) and drawn as page view draws it: sharp however long.
                                        val laidTable by produceState<Pair<LaidPage, List<TableCellArea>>?>(null, img, meta.basePt, documentStyles, packageParts) {
                                            value = table?.let { t -> withContext(Dispatchers.Default) { runCatching {
                                                val areas = ArrayList<TableCellArea>()
                                                OfficePages.tableLaid(meta, t, documentStyles, img.widthPt, areas) { n -> n.bytes ?: n.part?.let { packageParts[it] } } to areas
                                            }.onFailure { Log.e("Writer", "table", it) }.getOrNull() } }
                                        }
                                        val cells = laidTable?.second.orEmpty()
                                        val objectInput = if (table != null) Modifier.pointerInteropFilter { event ->
                                            if (event.action == android.view.MotionEvent.ACTION_UP) {
                                                commitCell()
                                                state.selection = androidx.compose.ui.text.TextRange(i, i + 1)
                                                tab = WriterTab.Table
                                                val x = event.x / shownWidth * img.widthPt
                                                val yy = event.y / shownHeight * img.heightPt
                                                cells.firstOrNull { x >= it.x && x <= it.x + it.width && yy >= it.y && yy <= it.y + it.height }?.let { area ->
                                                    val ref = TableCellRef(img.key, area.row, area.cell)
                                                    if (mergeFrom?.key == img.key) { mergeTo = ref; return@let } // choosing where the merge ends
                                                    selectedCell = ref
                                                    val cell = table.rows[area.row].cells[area.cell]
                                                    if (cell.paras.any { p -> p.table != null || p.runs.any { it.image != null } }) message = "This cell has nested content; its text is kept."
                                                    else WriterBridge.cellText(cell).let { text -> cellEdit = TableCellEdit(ref, TextFieldValue(text, androidx.compose.ui.text.TextRange(text.length)), cell) }
                                                }
                                            }
                                            true
                                        } else Modifier
                                            .then(if (float?.behind == true) Modifier.graphicsLayer { alpha = 0.45f } else Modifier) // behind the text: the text stays readable over it
                                            // Selected, dragged where it goes on its page — fixed there, free of the text (Word's "Fix position
                                            // on page", as Picture › Position places it).
                                            .then(if (!selected || float == null) Modifier else Modifier.graphicsLayer { translationX = drag.x; translationY = drag.y }
                                                .pointerInput(i, img.key, float) {
                                                    detectDragGestures(
                                                        onDrag = { change, delta -> change.consume(); drag += delta },
                                                        onDragEnd = {
                                                            val k = with(density) { scale.sp.toPx() }
                                                            val moved = drag; drag = Offset.Zero
                                                            changePicture(img.key, PictureXml.placed(img, float.pageX + moved.x / k, float.pageY + moved.y / k, kind))
                                                        },
                                                        onDragCancel = { drag = Offset.Zero },
                                                    )
                                                })
                                            .then(if (!selected || float != null) Modifier else Modifier.graphicsLayer { translationX = drag.x; translationY = drag.y; alpha = if (drag == Offset.Zero) 1f else 0.7f }
                                                .pointerInput(i, img.key) {
                                                    detectDragGestures(
                                                        onDrag = { change, delta -> change.consume(); drag += delta },
                                                        onDragEnd = {
                                                            val middle = Offset(left + drag.x, y + drag.y + with(density) { h.sp.toPx() } / 2)
                                                            drag = Offset.Zero
                                                            movePicture(i, l.getOffsetForPosition(middle), img.key)
                                                        },
                                                        onDragCancel = { drag = Offset.Zero },
                                                    )
                                                })
                                            .clickable {
                                                state.selection = androidx.compose.ui.text.TextRange(i, i + 1)
                                                tab = WriterTab.Picture
                                            }
                                        Box(Modifier.offset { IntOffset(left.toInt(), y) }.then(size)
                                            .then(if (selected) Modifier.border(2.dp, Color(0xFF1A73E8)) else Modifier)
                                            .then(objectInput),
                                            contentAlignment = Alignment.Center) {
                                            if (table != null) androidx.compose.foundation.Canvas(Modifier.matchParentSize().background(Color.White)) {
                                                laidTable?.first?.let { laid -> drawIntoCanvas { c ->
                                                    val n = c.nativeCanvas; val s = this.size.width / laid.page.width
                                                    n.save(); n.scale(s, s); laid.draw(n); n.restore()
                                                } }
                                            } else if (img.chart != null) {
                                                // A chart: drawn from what it shows, as the pages draw it.
                                                val drawn = remember(img.chart) { ChartDrawable(img.chart) }
                                                androidx.compose.foundation.Canvas(Modifier.matchParentSize()) { drawIntoCanvas { c ->
                                                    val n = c.nativeCanvas; val k = this.size.width / img.widthPt.coerceAtLeast(0.1f)
                                                    n.save(); n.scale(k, k); drawn.setBounds(0, 0, img.widthPt.toInt(), img.heightPt.toInt()); drawn.draw(n); n.restore()
                                                } }
                                            } else if (img.box != null) {
                                                // A text box: its words in it, as the pages draw it.
                                                val drawn = remember(img, documentStyles) { OfficePages.textBox(OfficeDoc(emptyList(), basePt = meta.basePt), documentStyles, img) { pictureBytes(it) } }
                                                androidx.compose.foundation.Canvas(Modifier.matchParentSize()) { drawIntoCanvas { c ->
                                                    val n = c.nativeCanvas; val k = this.size.width / img.widthPt.coerceAtLeast(0.1f)
                                                    n.save(); n.scale(k, k); drawn?.setBounds(0, 0, img.widthPt.toInt(), img.heightPt.toInt()); drawn?.draw(n); n.restore()
                                                } }
                                            } else if (ShapePicture.of(img.raw) != null) {
                                                // A shape: drawn as the pages draw it (its outline's width in points too).
                                                val shape = remember(img.raw) { ShapePicture.of(img.raw)!! }
                                                androidx.compose.foundation.Canvas(Modifier.matchParentSize()) { drawIntoCanvas { c ->
                                                    val n = c.nativeCanvas; val k = this.size.width / img.widthPt.coerceAtLeast(0.1f)
                                                    n.save(); n.scale(k, k); shape.drawAt(n, 0f, 0f, img.widthPt, img.heightPt); n.restore()
                                                } }
                                            } else Image(picturePainter(img, packageParts), contentDescription = PictureXml.kind(img), contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
                                            // Not a picture (a chart, a shape Writer cannot draw): a grey box that says what it is, kept in the file as it was.
                                            if (pictureBytes(img) == null && img.table == null && img.chart == null && img.box == null && ShapePicture.of(img.raw) == null) Text(PictureXml.kind(img), color = Color(0xFF5F6368), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                            // The cells a merge would take, lit up; else the cell the Table tab acts on, outlined.
                                            if (table != null && tab == WriterTab.Table) {
                                                val from = mergeFrom?.takeIf { it.key == img.key }
                                                val lit = from?.let { f -> TableEdit.cellsBetween(table, f.row to f.cell, (mergeTo ?: f).let { it.row to it.cell }) }
                                                    ?: selectedCell?.takeIf { it.key == img.key && cellEdit == null }?.let { setOf(it.row to it.cell) }.orEmpty()
                                                cells.filter { (it.row to it.cell) in lit }.forEach { a ->
                                                    Box(Modifier.align(Alignment.TopStart)
                                                        .offset { IntOffset((a.x / img.widthPt * shownWidth).roundToInt(), (a.y / img.heightPt * shownHeight).roundToInt()) }
                                                        .size(with(density) { (a.width / img.widthPt * shownWidth).toDp() }, with(density) { (a.height / img.heightPt * shownHeight).toDp() })
                                                        .background(if (from != null) WORD_BLUE.copy(alpha = 0.18f) else Color.Transparent).border(2.dp, WORD_BLUE))
                                                }
                                            }
                                            val edit = cellEdit?.takeIf { it.ref.key == img.key }
                                            val area = edit?.let { e -> cells.firstOrNull { it.row == e.ref.row && it.cell == e.ref.cell } }
                                            if (edit != null && area != null) {
                                                val focus = remember(edit.ref) { FocusRequester() }
                                                val keyboard = LocalSoftwareKeyboardController.current
                                                var hadFocus by remember(edit.ref) { mutableStateOf(false) }
                                                LaunchedEffect(edit.ref) { focus.requestFocus(); keyboard?.show() }
                                                BasicTextField(
                                                    value = edit.value,
                                                    onValueChange = { cellEdit = edit.copy(value = it) },
                                                    // As big as the cell's text is drawn: its size in points, at the table's scale.
                                                    textStyle = TextStyle(color = Color.Black, textAlign = when (edit.cell.paras.firstOrNull()?.align) {
                                                        DocAlign.Center -> TextAlign.Center; DocAlign.End -> TextAlign.End; DocAlign.Justify -> TextAlign.Justify; else -> TextAlign.Start },
                                                        fontSize = with(density) {
                                                        val para = table!!.rows[area.row].cells[area.cell].paras.firstOrNull()
                                                        ((para?.let { documentStyles.look(it).sizePt } ?: meta.basePt) * shownWidth / img.widthPt).toSp()
                                                    }),
                                                    cursorBrush = SolidColor(WORD_BLUE),
                                                    // Its looks shown as they are typed and changed: each run's, from the working cell.
                                                    visualTransformation = { typed ->
                                                        val spPerPt = with(density) { (shownWidth / img.widthPt).toSp().value }
                                                        androidx.compose.ui.text.input.TransformedText(cellAnnotated(WriterBridge.withCellText(edit.cell, typed.text), spPerPt) { p, r ->
                                                            r.sizePt ?: documentStyles.look(p).sizePt ?: meta.basePt }, androidx.compose.ui.text.input.OffsetMapping.Identity)
                                                    },
                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                    keyboardActions = KeyboardActions(onDone = { commitCell(); keyboard?.hide() }),
                                                    // Where the cell's own text sits: its margins, and its vertical alignment.
                                                    decorationBox = { inner ->
                                                        val m = documentStyles.tableSpec(table!!).rows.getOrNull(area.row)?.cells?.getOrNull(area.cell)?.margin
                                                        val px = { pt: Float -> with(density) { (pt * shownWidth / img.widthPt).toDp() } }
                                                        Box(Modifier.fillMaxSize().padding(start = px(m?.get(0) ?: 5.4f), top = px(m?.get(1) ?: 0f), end = px(m?.get(2) ?: 5.4f), bottom = px(m?.get(3) ?: 0f)),
                                                            contentAlignment = when (TableEdit.vAlign(edit.cell.props)) { "center" -> Alignment.CenterStart; "bottom" -> Alignment.BottomStart; else -> Alignment.TopStart }) { inner() }
                                                    },
                                                    modifier = Modifier.align(Alignment.TopStart) // the box centres its content; the cell is placed from the corner
                                                        .offset { IntOffset((area.x / img.widthPt * shownWidth).roundToInt(), (area.y / img.heightPt * shownHeight).roundToInt()) }
                                                        .size(with(density) { (area.width / img.widthPt * shownWidth).toDp() }, with(density) { (area.height / img.heightPt * shownHeight).toDp() })
                                                        .background(TableEdit.shading(edit.cell.props)?.let { Color(0xFF000000 or it.toLong()) } ?: Color.White).border(2.dp, WORD_BLUE)
                                                        .focusRequester(focus).onFocusChanged { f ->
                                                            if (f.isFocused) hadFocus = true else if (hadFocus) commitCell()
                                                        },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
        // The ribbon, like Word's, at the bottom: a line of tabs and a line of that tab's tools, scrolling sideways
        // when they do not fit. A floating island; typing on the screen's keyboard, a flat strip on it. With a physical
        // keyboard (a keyboard case) the island stays, as high above its suggestions bar as above the screen's bottom
        // (asked 2026-09-27: the strip pressed against them).
        val imeOpen = WindowInsets.isImeVisible
        val config = LocalConfiguration.current
        val physicalKeyboard = config.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY &&
            config.hardKeyboardHidden == android.content.res.Configuration.HARDKEYBOARDHIDDEN_NO
        val island = !imeOpen || physicalKeyboard
        if (!viewing) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(
            if (!island) Modifier.background(Color(0xFF16191A))
            else Modifier.then(if (imeOpen) Modifier else Modifier.navigationBarsPadding()).padding(12.dp).clip(MaterialTheme.shapes.extraLarge)
                .background(Color(0xFF16191A)).padding(vertical = 4.dp))) { // solid: the page is white
            // A note's number, the cursor at it: the note's words (Edit opens them).
            hereNote?.let { (n, number, _) ->
                Row(Modifier.fillMaxWidth().clickable { noteEditing = n to number }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text((if (n.end) "Endnote " else "Footnote ") + number, color = w, style = MaterialTheme.typography.labelLarge)
                    Text(n.paras.joinToString(" ") { it.text }.ifBlank { "(empty)" }, color = w.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                    Text("Edit", color = w, style = MaterialTheme.typography.labelLarge)
                }
            }
            // A word that looks misspelled, the cursor in it: the spell checker's suggestions, a tap puts one in.
            hereWord?.let { (r, w, suggestions) ->
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_spellcheck), contentDescription = "Spelling", tint = Color(0xFFE57373), modifier = Modifier.size(20.dp))
                    if (suggestions.isEmpty()) Text("No suggestions", color = w.let { Color.White.copy(alpha = 0.6f) }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 10.dp))
                    suggestions.forEach { sug -> TextButton(onClick = { replaceWord(r, sug) }) { Text(sug, color = Color.White) } }
                    TextButton(onClick = { ignored = ignored + w }) { Text("Ignore", color = Color.White.copy(alpha = 0.7f)) }
                }
            }
            // The form field the cursor is in: its choices, a date, a tick (a text field fills by typing, or Fill in).
            hereControl?.let { (pr, _, _) -> FormBar(pr, controlsShown.filter { it.first == pr }.joinToString("") { it.second }, w) { newPr, text -> fillControl(pr, newPr, text) } }
            // The tracked change the cursor is in: what, who and when (Review has Accept and Reject).
            hereChange?.let { c ->
                Row(Modifier.fillMaxWidth().clickable { tab = WriterTab.Review }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF000000 or TrackChanges.color(c.author).toLong())))
                    Text(c.label + (if (c.author.isNotBlank()) " · " + c.author else "") + (c.date?.let { d -> runCatching { " · " + java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
                        .format(java.time.OffsetDateTime.parse(d).atZoneSameInstant(java.time.ZoneId.systemDefault())) }.getOrNull() } ?: ""),
                        color = w, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                }
            }
            // The comment the cursor is in: who and what, a tap opens it.
            hereComment?.takeIf { !paneOn }?.let { c ->
                Row(Modifier.fillMaxWidth().clickable { commentsOpen = true }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFB300)))
                    Text((if (c.author.isNotBlank()) c.author + ": " else "") + c.text.replace('\n', ' '), color = w, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                    Text("Open", color = w, style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val selectedObject = selectedPicture?.second?.let { pictures[it] }
                WriterTab.entries.filter { t -> when (t) {
                    WriterTab.Picture -> selectedObject != null && selectedObject.table == null
                    WriterTab.Table -> selectedObject?.table != null
                    else -> true
                } }.forEach { t ->
                    Text(t.label, color = if (tab == t) driveNavigationSelectedContentColor() else w, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp).clip(CircleShape).background(if (tab == t) driveNavigationSelectedColor() else Color.Transparent)
                            .clickable { tab = t }.padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
            // The tools of a tab in pages that fit the width, swiped one page at a time (asked 2026-09-27), with dots.
            val span = state.currentSpanStyle
            val para = state.currentParagraphStyle
            val pages: List<@Composable () -> Unit> = when (tab) {
                WriterTab.File -> listOf({
                    RibbonButton("Save", w, R.drawable.ic_save, enabled = loaded && (dirty || file == null || cellEdit != null)) { commitCell(); save() }
                    RibbonButton("Save a copy", w, R.drawable.ic_copy, enabled = loaded && file != null) { commitCell(); save(asCopy = true) }
                }, {
                    RibbonButton("Export PDF", w, R.drawable.ic_file, enabled = loaded) { exportPdf() }
                    RibbonButton("Versions", w, R.drawable.ic_history, enabled = file != null) { showVersions = true }
                }, {
                    RibbonButton("Share", w, R.drawable.ic_share, enabled = file?.isFile == true) {
                        file?.let { shareDriveFile(context, it) }
                        if (dirty) message = "Shared as last saved — your newest changes are not in it yet"
                    }
                    RibbonButton("About this file", w, R.drawable.ic_info, enabled = loaded) { details = true }
                })
                // Typing in a table cell: Home formats the cell's text.
                WriterTab.Home -> cellEdit?.let { e -> cellHomePages(w, WriterBridge.withCellText(e.cell, e.value.text), e.value.selection,
                    { p, r -> r.sizePt ?: documentStyles.look(p).sizePt ?: meta.basePt }) { c -> cellEdit = e.copy(cell = c) } } ?: listOf({
                    Tool(R.drawable.ic_format_bold, "Bold", w, on = span.fontWeight == FontWeight.Bold) { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }
                    Tool(R.drawable.ic_format_italic, "Italic", w, on = span.fontStyle == FontStyle.Italic) { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }
                    Tool(R.drawable.ic_format_underlined, "Underline", w, on = span.textDecoration?.contains(TextDecoration.Underline) == true) { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }
                    Tool(R.drawable.ic_format_strikethrough, "Strikethrough", w, on = span.textDecoration?.contains(TextDecoration.LineThrough) == true) { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) }
                    // Superscript and subscript: read back in at once, so they show smaller as a file's do.
                    listOf("X²" to androidx.compose.ui.text.style.BaselineShift.Superscript, "X₂" to androidx.compose.ui.text.style.BaselineShift.Subscript).forEach { (label, shift) ->
                        val sign = Math.signum(span.baselineShift?.multiplier ?: 0f)
                        RibbonButton(label, w, on = sign == Math.signum(shift.multiplier)) { fun set() {
                            val was = span.baselineShift?.takeIf { sign != 0f }
                            // Off: the letters go back to the size they had (the file's own, or the one set on purpose).
                            if (was != null) {
                                WriterBridge.spOf(span.fontSize)?.let { shownSp ->
                                    if (kotlin.math.abs(shownSp - BASE_SP * SCRIPT_SIZE_OWN) < 0.001f * shownSp) state.removeSpanStyle(SpanStyle(fontSize = span.fontSize))
                                    else state.addSpanStyle(SpanStyle(fontSize = (shownSp / SCRIPT_SIZE).sp))
                                }
                                state.removeSpanStyle(SpanStyle(baselineShift = was))
                            }
                            if (sign != Math.signum(shift.multiplier)) state.addSpanStyle(SpanStyle(baselineShift = shift))
                            if (!state.selection.collapsed) { val at = state.selection; state.setRichTextDocument(WriterBridge.toEditor(current(), pictures, documentStyles), at) }
                        }
                            // Letters chosen are shown again (Writer's change, undone as one); at the cursor it is typing.
                            if (state.selection.collapsed) set() else change { set() }
                        }
                    }
                    Divider(w)
                    // Superscript and subscript are shown smaller: the size told and changed is the letters' own.
                    val shrink = if ((span.baselineShift?.multiplier ?: 0f) != 0f) SCRIPT_SIZE else 1f
                    val size = (WriterBridge.spOf(span.fontSize) ?: BASE_SP) / shrink
                    // One point at a time, in the document's points (asked 2026-09-28: by 2 it skipped 13); − and + close
                    // together around a number of fixed width, so two digits fit on a phone as one does.
                    // At a picture or table the editor's character standing for it has its own (huge) size: the text's is told.
                    val onObject = state.annotatedString.text.let { t -> val i = state.selection.min
                        t.getOrNull(i) == EDITOR_PICTURE_CHAR && (!state.selection.collapsed || i == 0 || t.getOrNull(i - 1) == '\n') || t.getOrNull(i - 1) == EDITOR_PICTURE_CHAR }
                    val pt = if (onObject) meta.basePt.roundToInt() else (size / BASE_SP * meta.basePt).roundToInt()
                    fun setPt(n: Int) = state.addSpanStyle(SpanStyle(fontSize = (n.coerceIn(8, 72) * BASE_SP / meta.basePt * shrink).sp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tool(R.drawable.ic_remove, "Smaller text", w, enabled = pt > 8) { setPt(pt - 1) }
                        Text("$pt", color = w, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.width(26.dp))
                        Tool(R.drawable.ic_add, "Bigger text", w, enabled = pt < 72) { setPt(pt + 1) }
                    }
                }, {
                    Swatches(w, R.drawable.ic_format_color_text, "Text colour", listOf(Color.Black, Color(0xFFC00000), Color(0xFF0070C0), Color(0xFF00B050), Color(0xFF7030A0), Color(0xFFED7D31))) { c ->
                        if (c == null) span.color.takeIf { it != Color.Unspecified }?.let { state.removeSpanStyle(SpanStyle(color = it)) } else state.addSpanStyle(SpanStyle(color = c))
                    }
                    Swatches(w, R.drawable.ic_highlighter, "Highlight", listOf(Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF), Color(0xFFFF00FF))) { c ->
                        if (c == null) span.background.takeIf { it != Color.Unspecified }?.let { state.removeSpanStyle(SpanStyle(background = it)) } else state.addSpanStyle(SpanStyle(background = c))
                    }
                    // Change case (Word's Aa): the chosen letters, or the word at the cursor.
                    Box {
                        var casing by remember { mutableStateOf(false) }
                        RibbonButton("Aa", w) { casing = true }
                        DropdownMenu(casing, { casing = false }) {
                            WriterBridge.Case.entries.forEach { c -> DropdownMenuItem(text = { Text(c.label) }, onClick = { casing = false; change {
                                val text = state.annotatedString.text
                                val sel = state.selection
                                val (from, to) = if (!sel.collapsed) sel.min to sel.max else {
                                    var a = sel.min; var z = sel.min
                                    while (a > 0 && text[a - 1].isLetterOrDigit()) a--
                                    while (z < text.length && text[z].isLetterOrDigit()) z++
                                    a to z
                                }
                                state.setRichTextDocument(WriterBridge.changeCase(text, state.toRichTextDocument(), from, to, c), sel)
                            } }) }
                        }
                    }
                    // Clear formatting: the chosen letters (or, with none chosen, the paragraph) back to their style's look.
                    Tool(R.drawable.ic_format_clear, "Clear formatting", w) { change {
                        val text = state.annotatedString.text
                        val rich = state.toRichTextDocument()
                        val sel = state.selection
                        val (from, to) = if (!sel.collapsed) sel.min to sel.max
                            else WriterBridge.blockRange(text, rich, sel.min) ?: (0 to 0)
                        state.setRichTextDocument(WriterBridge.clearFormatting(text, rich, from, to, meta.basePt), sel)
                        state.setRichTextDocument(WriterBridge.toEditor(current(), pictures, documentStyles), sel) // the style's look where the letters' own was
                    } }
                }, {
                    // The everyday styles; every other (Subtitle, Quote, Heading 4–6, the document's own) behind Styles….
                    val styles = documentStyles.paragraphStyles()
                    listOf(styles[0] to "Normal", styles[1] to "Title", styles.first { it.heading == 1 } to "H1", styles.first { it.heading == 2 } to "H2", styles.first { it.heading == 3 } to "H3").forEach { (style, label) ->
                        RibbonButton(label, w, on = hereStyle == style) { applyStyle(style) }
                    }
                    RibbonButton("Styles…", w, R.drawable.ic_more_vert) { stylesPage = true }
                }, {
                    Tool(R.drawable.ic_format_list_bulleted, "Bullets", w, on = state.isUnorderedList) { state.toggleUnorderedList() }
                    Tool(R.drawable.ic_format_list_numbered, "Numbering", w, on = state.isOrderedList) { state.toggleOrderedList() }
                    Tool(R.drawable.ic_format_indent_decrease, "Decrease indent", w, enabled = state.canDecreaseListLevel) { state.decreaseListLevel() }
                    Tool(R.drawable.ic_format_indent_increase, "Increase indent", w, enabled = state.canIncreaseListLevel) { state.increaseListLevel() }
                }, {
                    listOf(TextAlign.Start to R.drawable.ic_align_left, TextAlign.Center to R.drawable.ic_align_center, TextAlign.End to R.drawable.ic_align_right, TextAlign.Justify to R.drawable.ic_align_justify).forEach { (a, icon) ->
                        val on = para.textAlign == a || (a == TextAlign.Start && (para.textAlign == TextAlign.Unspecified || para.textAlign == TextAlign.Left))
                        Tool(icon, "Align ${a.toString().lowercase(Locale.ROOT)}", w, on = on) { state.addParagraphStyle(ParagraphStyle(textAlign = a, textDirection = androidx.compose.ui.text.style.TextDirection.Content)) }
                    }
                }, {
                    // Line spacing (the pages and the file show it; the editor keeps its own lines) and the ⋯ page.
                    Tool(R.drawable.ic_format_line_spacing, "Line spacing", w) { chooseParagraphs { at, look -> paragraphPage = at to look } }
                    LINE_SPACINGS.forEach { m ->
                        RibbonButton(plain(m), w) {
                            chooseParagraphs { at, look -> setSpacing(at, look, look.copy(lineMultiple = m, lineExact = null)); message = "Line spacing ${plain(m)} — it shows on the pages" }
                        }
                    }
                    Divider(w)
                    // Borders (Word's menu, for paragraphs): each side on or off, a box, none, a horizontal line.
                    Box {
                        var bordering by remember { mutableStateOf(false) }
                        RibbonButton("Borders", w, R.drawable.ic_border_bottom) { bordering = true }
                        DropdownMenu(bordering, { bordering = false }) {
                            val thin = Border(0.5f, 0)
                            @Composable fun icon(id: Int) = Icon(painterResource(id), contentDescription = null, modifier = Modifier.size(20.dp))
                            listOf<Triple<String, Int, (ParaBox) -> ParaBox>>(
                                Triple("Bottom border", R.drawable.ic_border_bottom) { b -> b.copy(bottom = if (b.bottom == null) thin else null) },
                                Triple("Top border", R.drawable.ic_border_top) { b -> b.copy(top = if (b.top == null) thin else null) },
                                Triple("Left border", R.drawable.ic_border_left) { b -> b.copy(left = if (b.left == null) thin else null) },
                                Triple("Right border", R.drawable.ic_border_right) { b -> b.copy(right = if (b.right == null) thin else null) },
                                Triple("No border", R.drawable.ic_border_clear) { _ -> ParaBox() },
                                Triple("All borders", R.drawable.ic_border_all) { b -> b.copy(top = thin, left = thin, bottom = thin, right = thin, between = thin) },
                                Triple("Outside borders", R.drawable.ic_border_outer) { b -> b.copy(top = thin, left = thin, bottom = thin, right = thin, between = null) },
                                Triple("Inside horizontal border", R.drawable.ic_border_horizontal) { b -> b.copy(between = if (b.between == null) thin else null) },
                            ).forEach { (label, id, f) -> DropdownMenuItem(text = { Text(label) }, leadingIcon = { icon(id) }, onClick = { bordering = false; chooseParagraphs { at, look -> setBorders(at, f(look.box ?: ParaBox())) } }) }
                            DropdownMenuItem(text = { Text("Horizontal line") }, leadingIcon = { icon(R.drawable.ic_horizontal_rule) }, onClick = { bordering = false; horizontalLine() })
                        }
                    }
                    RibbonButton("More…", w, R.drawable.ic_more_vert) { chooseParagraphs { at, look -> paragraphPage = at to look } }
                })
                // Word's groups, in its order: Pages, Tables, Illustrations, Links, Comments, Header & Footer, Text, Symbols.
                WriterTab.Insert -> listOf({
                    RibbonButton("Blank page", w, R.drawable.ic_note_add, enabled = loaded) { insertPageBreak(true) }
                    RibbonButton("Page break", w, R.drawable.ic_insert_page_break, enabled = loaded) { insertPageBreak(false) }
                    Divider(w)
                    RibbonButton("Table", w, R.drawable.ic_table, enabled = loaded) { insertingTable = true }
                }, {
                    RibbonButton("Picture", w, R.drawable.ic_gallery, enabled = loaded) { pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    RibbonButton("Shapes", w, R.drawable.ic_category, enabled = loaded) { shapesPage = true }
                }, {
                    RibbonButton("Text box", w, R.drawable.ic_text_box, enabled = loaded) { boxing = null to "" }
                    RibbonButton("WordArt", w, R.drawable.ic_format_color_text, enabled = loaded) { wordArt = true }
                    RibbonButton("Chart", w, R.drawable.ic_bar_chart, enabled = loaded) { charting = null to WORD_SAMPLE_CHART }
                }, {
                    RibbonButton(if (state.isLink) "Edit link" else "Link", w, R.drawable.ic_link) { linking = true }
                    RibbonButton("Comment", w, R.drawable.ic_comment, enabled = loaded) { commenting = true }
                }, {
                    // Header, footer and page numbers: one page for all three (APA: the number top right).
                    RibbonButton("Header & footer", w, R.drawable.ic_page_header, enabled = loaded) { headerPage = true }
                    RibbonButton("Page numbers", w, R.drawable.ic_numbers, enabled = loaded) { headerPage = true }
                }, {
                    // Word's Drop Cap: none, dropped, in the margin, or its options page.
                    Box {
                        var open by remember { mutableStateOf(false) }
                        RibbonButton("Drop cap", w, R.drawable.ic_drop_cap, enabled = loaded) { open = true }
                        DropdownMenu(open, { open = false }) {
                            fun here(then: (Int, DropCap?) -> Unit) { open = false; chooseParagraphs { at, _ -> at.min().let { i -> then(i, DropCap.of(current().paras.getOrNull(i)?.keep)) } } }
                            DropdownMenuItem(text = { Text("None") }, onClick = { here { i, _ -> setDropCap(i, null) } })
                            DropdownMenuItem(text = { Text("Dropped") }, leadingIcon = { Icon(painterResource(R.drawable.ic_drop_cap), contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { here { i, now -> setDropCap(i, (now ?: DropCap()).copy(margin = false)) } })
                            if (kind == OfficeKind.Docx) DropdownMenuItem(text = { Text("In margin") }, onClick = { here { i, now -> setDropCap(i, (now ?: DropCap()).copy(margin = true)) } })
                            DropdownMenuItem(text = { Text("Drop cap options…") }, onClick = { here { i, now -> dropCapPage = i to now } })
                        }
                    }
                    RibbonButton("Date", w, R.drawable.ic_calendar_today, enabled = loaded) { datePage = true }
                    // A new line in the same paragraph — Shift+Enter in Word, which a phone keyboard does not have.
                    RibbonButton("Line break", w, R.drawable.ic_wrap_text) { change {
                        state.addTextAfterSelection(LINE_BREAK.toString())
                        val at = state.selection
                        state.setRichTextDocument(WriterBridge.toEditor(current(), pictures, documentStyles), at) // shown as a new line at once
                    } }
                }, {
                    RibbonButton("Symbol", w, R.drawable.ic_special_character, enabled = loaded) { symbolPage = true }
                })
                WriterTab.Picture -> {
                    val (at, key) = selectedPicture ?: (0 to "")
                    val img = pictures[key]
                    val editable = img != null && PictureXml.editable(img)
                    val hasBytes = img != null && pictureBytes(img) != null
                    // A drawing made in Writer: drawn on again (its strokes ride in its picture).
                    val drawn = remember(key, img) { img?.let { pictureBytes(it) }?.let { InkPicture.strokesOf(it) } != null }
                    listOf({
                        if (drawn) RibbonButton("Edit drawing", w, R.drawable.ic_draw) { editDrawing(key) }
                        img?.chart?.let { ch -> RibbonButton("Edit data", w, R.drawable.ic_bar_chart) { charting = key to ch } }
                        img?.box?.let { paras -> RibbonButton("Edit text", w, R.drawable.ic_edit, enabled = editable) { boxing = key to paras.joinToString("\n") { it.text } } }
                        RibbonButton("Size", w, R.drawable.ic_aspect_ratio, enabled = editable) { sizing = true }
                        RibbonButton("Crop", w, R.drawable.ic_crop, enabled = hasBytes && editable) {
                            scope.launch {
                                cropping = withContext(Dispatchers.IO) { pictureBytes(img!!)?.let { runCatching { decodePicture(it, 2400) }.getOrNull() } }
                                if (cropping == null) message = "Writer cannot crop this kind of picture."
                            }
                        }
                    }, {
                        RibbonButton("Wrap text", w, R.drawable.ic_wrap_text, enabled = editable) { wrapping = true }
                        // Floating ones are dragged where they go on their page.
                        RibbonButton("Position", w, R.drawable.ic_open_with, enabled = editable && img?.let { PictureXml.wrapOf(it) } != PictureWrap.InLine) { position(key) }
                    }, {
                        RibbonButton("Turn left", w, R.drawable.ic_rotate_left, enabled = hasBytes && editable) { transform(-90, false) }
                        RibbonButton("Turn right", w, R.drawable.ic_rotate_right, enabled = hasBytes && editable) { transform(90, false) }
                        RibbonButton("Flip", w, R.drawable.ic_flip, enabled = hasBytes && editable) { transform(0, true) }
                    }, {
                        RibbonButton("Replace", w, R.drawable.ic_gallery, enabled = editable) { replacePicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        RibbonButton("Save as", w, R.drawable.ic_save, enabled = hasBytes) { savePicture(img!!) }
                        RibbonButton("Delete", w, R.drawable.ic_delete) { state.removeTextRange(androidx.compose.ui.text.TextRange(at, at + 1)) }
                    })
                }
                WriterTab.Table -> {
                    val (at, _) = selectedPicture ?: (0 to "")
                    // Rows, columns, merging and splitting for both kinds; looks, shading and the ⋯ page write Word's XML (Word only for now).
                    val table = selectedPicture?.second?.let { pictures[it] }?.table
                    val word = kind == OfficeKind.Docx
                    // The cell the tools act on: the one tapped, else the first.
                    fun here(): TableCellRef? = selectedPicture?.second?.let { key -> selectedCell?.takeIf { it.key == key } ?: TableCellRef(key, 0, 0) }
                    // The cell being typed in is put into the table first, and the change made to the table as it then is
                    // (made to the table from before it, the typing was lost); its key is read again after.
                    fun change(said: String? = null, f: (DocTable, TableCellRef) -> DocTable?) {
                        commitCell()
                        val ref = here() ?: return
                        val img = pictures[ref.key] ?: return
                        f(img.table ?: return, ref)?.let { changeTable(ref.key, img, it) } ?: run { message = said }
                    }
                    val from = mergeFrom?.takeIf { it.key == selectedPicture?.second }
                    if (from != null) listOf({
                        val n = table?.let { t -> TableEdit.cellsBetween(t, from.row to from.cell, (mergeTo ?: from).let { it.row to it.cell }).size } ?: 0
                        Text(if (mergeTo == null) "Tap the last cell to merge" else "$n cells", color = w, style = MaterialTheme.typography.labelLarge)
                        RibbonButton("Merge", w, R.drawable.ic_cell_merge, enabled = mergeTo != null && n > 1) {
                            val to = mergeTo!!
                            change("These cells cannot be merged.") { t, _ -> TableEdit.merge(t, from.row to from.cell, to.row to to.cell) }
                            selectedCell = from; mergeFrom = null; mergeTo = null
                        }
                        RibbonButton("Cancel", w, R.drawable.ic_close) { mergeFrom = null; mergeTo = null }
                    }) else listOf({
                        // Everyday tools on the ribbon, the rest behind ⋯ (asked 2026-09-28); three to a page, as a phone fits them.
                        RibbonButton("Row above", w, R.drawable.ic_add_row_above, enabled = table != null) { change { t, c -> TableEdit.insertRow(t, c.row, c.cell, below = false) } }
                        RibbonButton("Row below", w, R.drawable.ic_add_row_below, enabled = table != null) { change { t, c -> TableEdit.insertRow(t, c.row, c.cell, below = true) } }
                        RibbonButton("Merge", w, R.drawable.ic_cell_merge, enabled = table != null) { commitCell(); mergeFrom = here(); mergeTo = null }
                    }, {
                        RibbonButton("Column left", w, R.drawable.ic_add_column_left, enabled = table != null) { change { t, c -> TableEdit.insertColumn(t, c.row, c.cell, right = false) } }
                        RibbonButton("Column right", w, R.drawable.ic_add_column_right, enabled = table != null) { change { t, c -> TableEdit.insertColumn(t, c.row, c.cell, right = true) } }
                        RibbonButton("Split", w, R.drawable.ic_call_split, enabled = table != null) { commitCell(); tableDialog = "split" }
                    }, {
                        RibbonButton("Delete row", w, R.drawable.ic_delete_row, enabled = table != null && table.rows.size > 1) { change("The last row stays.") { t, c -> TableEdit.deleteRow(t, c.row, c.cell) }; selectedCell = null }
                        RibbonButton("Delete column", w, R.drawable.ic_delete_column, enabled = table != null && TableEdit.gridCount(table) > 1) { change("This column cannot be taken away.") { t, c -> TableEdit.deleteColumn(t, c.row, c.cell) }; selectedCell = null }
                        RibbonButton("Delete table", w, R.drawable.ic_delete, enabled = selectedPicture != null) {
                            commitCell()
                            state.removeTextRange(androidx.compose.ui.text.TextRange(at, at + 1)); tab = WriterTab.Home
                        }
                    }, {
                        TableEdit.LOOKS.take(4).forEach { look -> LookChip(look) { if (word) change { t, _ -> TableEdit.withLook(t, look) } else message = "Table styles are for Word files for now." } }
                    }, {
                        Swatches(w, R.drawable.ic_format_color_fill, "Shading", TABLE_COLORS.take(8).map { Color(0xFF000000 or it.toLong()) }) { color ->
                            if (word) change { t, c -> TableEdit.withCells(t, TableEdit.wholeCell(t, c.row, c.cell)) { it.copy(props = TableEdit.withShading(it.props, color?.let { k -> (k.toArgb() and 0xFFFFFF) })) } }
                            else message = "Shading is for Word files for now."
                        }
                        RibbonButton("More…", w, R.drawable.ic_more_vert, enabled = table != null && word) { commitCell(); tableDialog = "props" }
                    })
                }
                // Word's Design: the page's background — its colour and a box round it.
                WriterTab.Design -> listOf({
                    RibbonButton("APA 7 paper", w, R.drawable.ic_school, enabled = loaded) { apaPage = true }
                    // Word's Document Formatting gallery: a set of looks for Normal, Title and the headings at once.
                    Box {
                        var open by remember { mutableStateOf(false) }
                        RibbonButton("Style set", w, R.drawable.ic_style, enabled = loaded) { open = true }
                        DropdownMenu(open, { open = false }) {
                            StyleSet.entries.forEach { set -> DropdownMenuItem(text = { Text(set.label) }, onClick = { open = false; applyStyleSet(set) }) }
                        }
                    }
                }, {
                    // Word's Paragraph Spacing: the Normal style's (every paragraph that has it, headings keeping their own space).
                    Box {
                        var spacing by remember { mutableStateOf(false) }
                        RibbonButton("Paragraph spacing", w, R.drawable.ic_format_line_spacing, enabled = loaded) { spacing = true }
                        DropdownMenu(spacing, { spacing = false }) {
                            SPACING_SETS.forEach { (label, after, line) ->
                                DropdownMenuItem(text = { Column { Text(label); Text("After ${plain(after)} pt · line ${plain(line)}", style = MaterialTheme.typography.bodySmall) } }, onClick = {
                                    spacing = false
                                    editStyle(documentStyles.paragraphStyles()[0], StyleChange(line = line, before = 0f, after = after))
                                    message = "$label — every Normal paragraph; shown on the pages"
                                })
                            }
                        }
                    }
                }, {
                    // Word's Watermark: its words, one of your own, or none.
                    Box {
                        var open by remember { mutableStateOf(false) }
                        val now = documentStyles.section.watermark
                        RibbonButton("Watermark", w, R.drawable.ic_branding_watermark, enabled = loaded, on = now != null) { open = true }
                        DropdownMenu(open, { open = false }) {
                            Watermark.PRESETS.forEach { t -> DropdownMenuItem(text = { Text(t) }, trailingIcon = { if (t == now) Icon(painterResource(R.drawable.ic_check), contentDescription = "Now", modifier = Modifier.size(18.dp)) },
                                onClick = { open = false; setWatermark(t) }) }
                            DropdownMenuItem(text = { Text("Custom watermark…") }, onClick = { open = false; watermarking = true })
                            if (now != null) DropdownMenuItem(text = { Text("Remove watermark") }, leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { open = false; setWatermark(null) })
                        }
                    }
                    Swatches(w, R.drawable.ic_format_color_fill, "Page colour", PAGE_COLORS.map { Color(0xFF000000 or it.toLong()) }) { c ->
                        setDesign(c?.let { it.toArgb() and 0xFFFFFF }, sheet.border?.line)
                    }
                    Box {
                        var bordering by remember { mutableStateOf(false) }
                        RibbonButton("Page borders", w, R.drawable.ic_border_outer, enabled = loaded, on = sheet.border != null) { bordering = true }
                        DropdownMenu(bordering, { bordering = false }) {
                            listOf("No border" to null, "Box, thin (½ pt)" to 0.5f, "Box, medium (1½ pt)" to 1.5f, "Box, thick (3 pt)" to 3f).forEach { (label, width) ->
                                DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(painterResource(if (width == null) R.drawable.ic_border_clear else R.drawable.ic_border_outer), contentDescription = null, modifier = Modifier.size(20.dp)) },
                                    onClick = { bordering = false; setDesign(sheet.color, width?.let { Border(it, sheet.border?.line?.color ?: 0) }) })
                            }
                        }
                    }
                })
                WriterTab.Draw -> listOf({
                    // On the page (asked 2026-09-30): drawn over the page as it is, put there in front of the text.
                    RibbonButton("Draw on page", w, R.drawable.ic_draw, enabled = loaded) { drawOnPage() }
                    // A drawing box: drawn in, then put in front of the text at its paragraph.
                    RibbonButton("Drawing box", w, R.drawable.ic_ink_pen, enabled = loaded) { drawing = Drawing(boxPt = sheet.textWidth.coerceAtMost(450f)) }
                })
                WriterTab.Layout -> listOf({
                    // Word's Layout: margins, orientation, size (one page for all three), then breaks.
                    RibbonButton("Margins", w, R.drawable.ic_margin, enabled = loaded) { pagePage = true }
                    RibbonButton("Portrait", w, R.drawable.ic_crop_portrait, enabled = loaded, on = !PageSetup.landscape(sheet)) {
                        if (PageSetup.landscape(sheet)) setPage(sheet.copy(width = sheet.height, height = sheet.width)) }
                    RibbonButton("Landscape", w, R.drawable.ic_crop_landscape, enabled = loaded, on = PageSetup.landscape(sheet)) {
                        if (!PageSetup.landscape(sheet)) setPage(sheet.copy(width = sheet.height, height = sheet.width)) }
                }, {
                    RibbonButton("Size", w, R.drawable.ic_fit_page, enabled = loaded) { pagePage = true }
                    // Columns: the pages show them (the editor keeps one column of text).
                    Box {
                        var columning by remember { mutableStateOf(false) }
                        RibbonButton(if (sheet.columns > 1) "Columns: ${sheet.columns}" else "Columns", w, R.drawable.ic_view_column, enabled = loaded) { columning = true }
                        DropdownMenu(columning, { columning = false }) {
                            listOf(1 to "One", 2 to "Two", 3 to "Three").forEach { (n, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { columning = false
                                    if (n != sheet.columns) { setPage(sheet.copy(columns = n)); if (n > 1) message = "$label columns — they show on the pages (the eye at the top)" } })
                            }
                        }
                    }
                }, {
                    RibbonButton("Page break", w, R.drawable.ic_insert_page_break, enabled = loaded) { insertPageBreak(false) }
                    RibbonButton("Blank page", w, R.drawable.ic_note_add, enabled = loaded) { insertPageBreak(true) }
                }, {
                    // Word's Line Numbers: none, continuous, restart each page, restart each section (Word only); the pages show them.
                    Box {
                        var open by remember { mutableStateOf(false) }
                        val now = documentStyles.section.lineNumbers?.restart
                        RibbonButton("Line numbers", w, R.drawable.ic_format_list_numbered, enabled = loaded, on = now != null) { open = true }
                        DropdownMenu(open, { open = false }) {
                            (listOf(null to "None", "continuous" to "Continuous", "newPage" to "Restart each page") + if (kind == OfficeKind.Docx) listOf("newSection" to "Restart each section") else emptyList())
                                .forEach { (mode, label) -> DropdownMenuItem(text = { Text(label) }, trailingIcon = { if (mode == now) Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = { open = false; if (mode != now) setLineNumbers(mode) }) }
                        }
                    }
                    // Word's Hyphenation: none or automatic, for the whole document; the pages break words at lines' ends.
                    Box {
                        var open by remember { mutableStateOf(false) }
                        val now = documentStyles.hyphenate
                        RibbonButton("Hyphenation", w, R.drawable.ic_hyphenation, enabled = loaded, on = now) { open = true }
                        DropdownMenu(open, { open = false }) {
                            listOf(false to "None", true to "Automatic").forEach { (on, label) -> DropdownMenuItem(text = { Text(label) },
                                trailingIcon = { if (on == now) Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = { open = false; if (on != now) setHyphenation(on) }) }
                        }
                    }
                })
                WriterTab.References -> listOf({
                    val has = remember(meta.paras) { Contents.find(meta, kind) != null }
                    RibbonButton(if (has) "Update table" else "Table of contents", w, R.drawable.ic_toc, enabled = loaded) { insertContents() }
                }, {
                    // Word's Footnotes group.
                    RibbonButton("Insert footnote", w, R.drawable.ic_superscript, enabled = loaded) { insertNote(false) }
                    RibbonButton("Insert endnote", w, R.drawable.ic_last_page, enabled = loaded) { insertNote(true) }
                }, {
                    // Word's Citations & Bibliography, APA 7.
                    RibbonButton("Insert citation", w, R.drawable.ic_format_quote, enabled = loaded) { citing = true }
                    RibbonButton("Sources", w, R.drawable.ic_checklist, enabled = loaded) { sourcesOpen = true }
                }, {
                    val listed = remember(meta.paras) { Citations.find(meta, kind) != null }
                    RibbonButton(if (listed) "Update references" else "References list", w, R.drawable.ic_format_list_bulleted, enabled = loaded) { if (listed) updateCitations() else insertReferenceList() }
                    RibbonButton("Update citations", w, R.drawable.ic_refresh, enabled = loaded) { updateCitations() }
                    Text("APA 7", color = w.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp))
                })
                WriterTab.Review -> listOf({
                    // Word's Proofing group: spelling marked as one types (on or off), and the next word marked.
                    RibbonButton("Spelling", w, R.drawable.ic_spellcheck, enabled = loaded, on = spelling) {
                        spelling = !spelling; prefs.edit().putBoolean("spelling", spelling).apply()
                        message = if (spelling) "Spelling on — Greek and English, the phone's own checker" else "Spelling off"
                    }
                    Tool(R.drawable.ic_chevron_right, "Next spelling mistake", w, enabled = loaded && spelling) { nextMisspelled() }
                }, {
                    // Word's Tracking group: recording on or off (Word files: an OpenDocument's changes are not kept yet).
                    RibbonButton("Track changes", w, R.drawable.ic_track_changes, enabled = loaded && kind == OfficeKind.Docx, on = tracking) {
                        if (tracking) { tracking = false; message = "Track changes off" }
                        else if (prefs.getString("author", "").isNullOrBlank()) askingName = true
                        else { tracking = true; message = "Track changes on — your edits are marked" }
                    }
                }, {
                    // Word's Changes group: go through them, each accepted or rejected, or all at once.
                    Tool(R.drawable.ic_chevron_left, "Previous change", w, enabled = loaded) { goChange(false) }
                    Tool(R.drawable.ic_chevron_right, "Next change", w, enabled = loaded) { goChange(true) }
                    RibbonButton("Accept", w, R.drawable.ic_check, enabled = loaded && hereChange != null) { decideChanges(true) }
                    RibbonButton("Reject", w, R.drawable.ic_close, enabled = loaded && hereChange != null) { decideChanges(false) }
                }, {
                    val any = changesShown.isNotEmpty()
                    RibbonButton("Accept all", w, R.drawable.ic_done_all, enabled = loaded && any) { decideChanges(true, all = true) }
                    RibbonButton("Reject all", w, R.drawable.ic_remove_done, enabled = loaded && any) { decideChanges(false, all = true) }
                }, {
                    RibbonButton("Comments", w, R.drawable.ic_comment, enabled = loaded, on = paneOn) {
                        if (tablet) { panelHidden = !panelHidden; if (meta.comments.isEmpty()) message = "No comments in this document" } else commentsOpen = true
                    }
                })
                WriterTab.View -> listOf({
                    // The comments: a pane beside the page on a tablet (shown or hidden), a sidebar on a phone.
                    RibbonButton("Comments", w, R.drawable.ic_comment, enabled = loaded, on = paneOn) {
                        if (tablet) { panelHidden = !panelHidden; if (meta.comments.isEmpty()) message = "No comments in this document" } else commentsOpen = true
                    }
                    Divider(w)
                    if (printPhone) {
                        // The page: its width on the screen, larger (two fingers do the same).
                        Tool(R.drawable.ic_remove, "Zoom out", w, enabled = pageZoom > 1f) { pageZoom = (pageZoom - 0.25f).coerceAtLeast(1f) }
                        Text("${(pageZoom * 100).roundToInt()}%", color = w, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 4.dp))
                        Tool(R.drawable.ic_add, "Zoom in", w, enabled = pageZoom < 5f) { pageZoom = (pageZoom + 0.25f).coerceAtMost(5f) }
                    } else {
                    Tool(R.drawable.ic_remove, "Zoom out", w, enabled = zoom > 70) { zoom -= 10 }
                    Text("$zoom%", color = w, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 4.dp))
                    Tool(R.drawable.ic_add, "Zoom in", w, enabled = zoom < 200) { zoom += 10 }
                    }
                    // A phone: the page as it prints (Print layout), or the text to the screen's width (Mobile view).
                    if (!tablet) RibbonButton("Mobile view", w, R.drawable.ic_wrap_text, on = mobileView) {
                        mobileView = !mobileView
                        context.getSharedPreferences("writer", Context.MODE_PRIVATE).edit().putBoolean("mobileView", mobileView).apply()
                    }
                }, {
                    // Centimetres or inches for the rulers and every length (APA's margins are given in inches).
                    RibbonButton("cm", w, on = !WriterUnits.inches) { WriterUnits.set(context, false) }
                    RibbonButton("inches", w, on = WriterUnits.inches) { WriterUnits.set(context, true) }
                })
            }
            // A tablet has room for a whole tab at once: its tools in one line, close together, centred (asked 2026-09-27:
            // spread over the width they were too far apart). A phone swipes them page by page.
            if (LocalConfiguration.current.screenWidthDp >= 700) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            ) { pages.forEachIndexed { i, page -> if (i > 0) Divider(w); page() } }
            else androidx.compose.runtime.key(tab) {
                val pager = rememberPagerState { pages.size }
                HorizontalPager(pager, Modifier.fillMaxWidth()) { page ->
                    // Spread over the width; a page wider than the phone scrolls sideways (found 2026-09-30: Design's last
                    // three tools were off the screen, out of reach), then the next swipe turns the page.
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).then(Modifier.widthIn(min = maxWidth)).padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { pages[page]() }
                    }
                }
                if (pages.size > 1) Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(pages.size) { i -> Box(Modifier.padding(horizontal = 3.dp).size(6.dp).clip(CircleShape).background(w.copy(alpha = if (i == pager.currentPage) 0.9f else 0.3f))) }
                }
            }
        }
        message?.let { m ->
            LaunchedEffect(m) { delay(2800); message = null }
            Surface(color = Color.Black, contentColor = Color.White, shape = CircleShape, modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp, start = 16.dp, end = 16.dp)) {
                Text(m, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        cropping?.let { bitmap ->
            BackHandler { cropping = null }
            // Photos' crop, with straight cuts (90°) on at first (asked 2026-09-28); off, the corners move freely and the
            // picture is straightened as Photos does. ponytail: the cut picture becomes a new one — Word's own crop
            // (a:srcRect) would keep the rest.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                CornerEditor(bitmap, DocumentQuad.fullFrame(), DocumentQuad.fullFrame(), "Reset", straightOption = true, done = { quad ->
                    cropping = null
                    val (at, key) = selectedPicture ?: return@CornerEditor
                    val img = pictures[key] ?: return@CornerEditor
                    if (quad == DocumentQuad.fullFrame()) return@CornerEditor
                    val cut = runCatching { cropScan(bitmap, quad, 2400f) }.getOrNull() ?: return@CornerEditor
                    val png = img.extension == "png" || img.part?.endsWith(".png", ignoreCase = true) == true
                    val bytes = java.io.ByteArrayOutputStream().also { cut.compress(if (png) android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
                    // As big on the page, piece for piece, as it was.
                    changePicture(key, DocImage(key, null, img.widthPt * cut.width / bitmap.width, img.heightPt * cut.height / bitmap.height, null, bytes, if (png) "png" else "jpg", PictureXml.wrapOf(img)))
                    state.selection = androidx.compose.ui.text.TextRange(at, at + 1)
                }, cancel = { cropping = null })
            }
        }
    }

    if (naming) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Save as") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text("File name") }, suffix = { Text(".${kind.extension}") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OfficeKind.entries.forEach { k -> RibbonButton(if (k == OfficeKind.Docx) "Word (.docx)" else "OpenDocument (.odt)", MaterialTheme.colorScheme.onSurface, on = kind == k) { kind = k } }
                    }
                    Text("In ${TextEditorStore(context).saveFolder} (Settings › Text editor).", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { saveNew(typed) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
    if (leaving) AlertDialog(
        onDismissRequest = { leaving = false },
        title = { Text("Save $name?") },
        text = { Text(if (meta.blocking.isNotEmpty() && file != null) "It has changes that are not saved. Saving makes a copy; the original stays as it is." else "It has changes that are not saved.") },
        confirmButton = { TextButton(onClick = { leaving = false; if (file == null) naming = true else save { back() } }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = { leaving = false }) { Text("Cancel") }
                TextButton(onClick = { leaving = false; draftFile(context, draftKey, kind).delete(); back() }) { Text("Don't save", color = Color(0xFFE35A4F)) }
            }
        },
    )
    if (apaPage) ApaPaperDialog(remember(meta) { current().paras.any { it.text.isNotBlank() } },
        java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG)), { apaPage = false }) { clean, title ->
        apaPage = false; applyApa(clean, title)
    }
    if (citing) CitationDialog(meta.sources, pages = kind == OfficeKind.Docx, multiple = kind == OfficeKind.Docx, { citing = false }, { sourceNew = true }) { list, page ->
        citing = false; insertCitation(list, page)
    }
    if (sourcesOpen) SourcesDialog(meta.sources, remember(meta) { Citations.usedTags(current()) }, { sourcesOpen = false }, { s -> if (s == null) sourceNew = true else sourceEdit = s }) { deleteSource(it) }
    if (sourceNew || sourceEdit != null) SourceDialog(sourceEdit, { sourceNew = false; sourceEdit = null }) { s -> sourceNew = false; sourceEdit = null; saveSource(s) }
    dropCapPage?.let { (at, now) ->
        val first = current().paras.getOrNull(at)?.runs?.firstOrNull { it.image == null && it.text.isNotEmpty() }
        DropCapDialog(now, kind == OfficeKind.Odt, documentStyles.fontName(first?.font), first?.color, { dropCapPage = null }) { d, font, color ->
            dropCapPage = null
            // Automatic chosen where the letters had a colour: taken off (-1).
            setDropCap(at, d, font.takeIf { it != documentStyles.fontName(first?.font) }, color ?: if (first?.color != null) -1 else null)
        }
    }
    if (watermarking) {
        var text by remember { mutableStateOf(documentStyles.section.watermark ?: "") }
        AlertDialog(
            onDismissRequest = { watermarking = false },
            title = { Text("Watermark") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Text across every page") }) },
            confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { watermarking = false; setWatermark(text.trim()) }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { watermarking = false }) { Text("Cancel") } },
        )
    }
    if (linking) {
        var url by remember { mutableStateOf(state.selectedLinkUrl ?: "https://") }
        var text by remember { mutableStateOf(state.selectedLinkText ?: "") }
        val selection = state.selection
        AlertDialog(
            onDismissRequest = { linking = false },
            title = { Text("Link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selection.collapsed && !state.isLink) OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Text to show") })
                    OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Address") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    linking = false
                    when {
                        state.isLink -> state.updateLink(url)
                        !selection.collapsed -> state.addLinkToSelection(url)
                        else -> state.addLink(text.ifBlank { url }, url)
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (state.isLink) TextButton(onClick = { linking = false; state.removeLink() }) { Text("Remove link") }
                    TextButton(onClick = { linking = false }) { Text("Cancel") }
                }
            },
        )
    }
    if (insertingTable) {
        var rows by remember { mutableIntStateOf(2) }
        var columns by remember { mutableIntStateOf(2) }
        AlertDialog(
            onDismissRequest = { insertingTable = false },
            title = { Text("Insert table") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TableSizeGrid(5, 5, rows, columns) { r, c -> rows = r; columns = c }
                    Text("$rows × $columns", style = MaterialTheme.typography.titleMedium)
                    Text("Drag across the grid to choose up to 5 × 5 cells.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = {
                insertingTable = false
                val table = if (kind == OfficeKind.Odt) TableEdit.newOdtTable(rows, columns, "Table " + java.util.UUID.randomUUID().toString().take(6))
                    else TableEdit.newTable(rows, columns, documentStyles.page.textWidth)
                val (width, height) = WriterBridge.tableSize(meta, table, documentStyles)
                val img = DocImage("t" + java.util.UUID.randomUUID(), null, width, height, table = table)
                val at = placePicture(img)
                state.selection = androidx.compose.ui.text.TextRange(at, at + 1)
                selectedCell = selectedPicture?.second?.let { TableCellRef(it, 0, 0) } // the key it is shown by, now
                tab = WriterTab.Table
            }) { Text("Insert") } },
            dismissButton = { TextButton(onClick = { insertingTable = false }) { Text("Cancel") } },
        )
    }
    selectedPicture?.let { (_, key) -> pictures[key] }?.let { img ->
        val key = selectedPicture!!.second
        img.table?.let { table ->
            val ref = selectedCell?.takeIf { it.key == key } ?: TableCellRef(key, 0, 0)
            when (tableDialog) {
                "split" -> TableSplitDialog(TableEdit.size(table, ref.row, ref.cell).let { (w, h) -> if (w * h > 1) w to h else 2 to 1 }, { tableDialog = null }) { cols, rows ->
                    tableDialog = null
                    TableEdit.split(table, ref.row, ref.cell, cols, rows)?.let { changeTable(key, img, it) } ?: run { message = "This cell cannot be split." }
                }
                "props" -> TablePropertiesDialog(table, ref.row, ref.cell, { tableDialog = null }) { tableDialog = null; changeTable(key, img, it) }
            }
        }
        if (sizing) {
            // In pixels, horizontal × vertical (asked 2026-09-27), as Word counts them: 96 to the inch. Or dragged by
            // its side or corner, the numbers following (asked 2026-09-28); the chain keeps its proportions.
            val ratio = img.heightPt / img.widthPt
            var a by remember { mutableFloatStateOf(img.widthPt / 0.75f) }
            var d by remember { mutableFloatStateOf(img.heightPt / 0.75f) }
            var across by remember { mutableStateOf(a.roundToInt().toString()) }
            var down by remember { mutableStateOf(d.roundToInt().toString()) }
            var chained by remember { mutableStateOf(true) }
            fun setA(v: Float, typed: String? = null) { a = v.coerceIn(1f, 10000f); across = typed ?: a.roundToInt().toString(); if (chained) { d = (a * ratio).coerceAtLeast(1f); down = d.roundToInt().toString() } }
            fun setD(v: Float, typed: String? = null) { d = v.coerceIn(1f, 10000f); down = typed ?: d.roundToInt().toString(); if (chained) { a = (d / ratio).coerceAtLeast(1f); across = a.roundToInt().toString() } }
            val ok = across.toIntOrNull() in 1..10000 && down.toIntOrNull() in 1..10000
            val painter = picturePainter(img, packageParts)
            AlertDialog(
                onDismissRequest = { sizing = false },
                title = { Text("Picture size") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // The picture, its top left fixed; handles on its right side, bottom side and corner.
                        BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF303030)).padding(12.dp)) {
                            val frameW = constraints.maxWidth.toFloat(); val frameH = constraints.maxHeight.toFloat()
                            val shown = remember { minOf(frameW * 0.7f / a, frameH * 0.7f / d) } // screen px per picture px, at first
                            fun fit() = minOf(shown, frameW / a, frameH / d)
                            val density = LocalDensity.current
                            val bw = a * fit(); val bh = d * fit()
                            with(density) {
                                Image(painter, contentDescription = "Picture", contentScale = ContentScale.FillBounds, modifier = Modifier.size(bw.toDp(), bh.toDp()).border(1.dp, Color(0xFF1A73E8)))
                                @Composable fun Handle(x: Float, y: Float, drag: (Float, Float) -> Unit) = Box(
                                    Modifier.offset { IntOffset((x - 22.dp.toPx()).roundToInt(), (y - 22.dp.toPx()).roundToInt()) }.size(44.dp)
                                        .pointerInput(Unit) { detectDragGestures { change, delta -> change.consume(); drag(delta.x / fit(), delta.y / fit()) } },
                                    contentAlignment = Alignment.Center,
                                ) { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF1A73E8)).border(2.dp, Color.White, CircleShape)) }
                                Handle(bw, bh / 2) { dx, _ -> setA(a + dx) }
                                Handle(bw / 2, bh) { _, dy -> setD(d + dy) }
                                Handle(bw, bh) { dx, dy -> if (!chained) { setA(a + dx); setD(d + dy) } else if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) setA(a + dx) else setD(d + dy) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(across, { t -> val v = t.filter(Char::isDigit).take(5); v.toIntOrNull()?.let { setA(it.toFloat(), v) } ?: run { across = v } },
                                    singleLine = true, label = { Text("Horizontal") }, suffix = { Text("px") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                                OutlinedTextField(down, { t -> val v = t.filter(Char::isDigit).take(5); v.toIntOrNull()?.let { setD(it.toFloat(), v) } ?: run { down = v } },
                                    singleLine = true, label = { Text("Vertical") }, suffix = { Text("px") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                            }
                            // The chain: on, the two sides change together.
                            Tool(R.drawable.ic_link, if (chained) "Proportions kept" else "Proportions free", MaterialTheme.colorScheme.onSurface, on = chained) {
                                chained = !chained
                                if (chained) setA(a) // back to the picture's own proportions
                            }
                        }
                    }
                },
                confirmButton = { TextButton(enabled = ok, onClick = { sizing = false; changePicture(key, PictureXml.resized(img, across.toInt() * 0.75f, down.toInt() * 0.75f)) }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { sizing = false }) { Text("Cancel") } },
            )
        }
        if (wrapping) {
            val now = PictureXml.wrapOf(img)
            AlertDialog(
                onDismissRequest = { wrapping = false },
                title = { Text("Wrap text") },
                text = {
                    Column {
                        PictureWrap.entries.forEach { wrap ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { wrapping = false; if (wrap != now) changePicture(key, PictureXml.withWrap(img, wrap, kind)) }) {
                                RadioButton(wrap == now, null)
                                Text(wrap.label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        // ponytail: floating pictures are drawn in line; showing them in place comes with dragging.
                        Text("The typing view shows it in line; the pages show it as chosen. Position places a floating one on its page.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                },
                confirmButton = { TextButton(onClick = { wrapping = false }) { Text("Close") } },
            )
        }
    }
    noteEditing?.let { (n, number) -> NoteDialog(n, number, { noteEditing = null }, { saveNote(n, it) }, { saveNote(n, null) }) }
    if (askingName) ReviewerNameDialog(prefs.getString("author", "").orEmpty(), { askingName = false }) { name ->
        prefs.edit().putString("author", name).apply()
        askingName = false; tracking = true; message = "Track changes on — your edits are marked"
    }
    if (commenting) NewCommentDialog(remember { context.getSharedPreferences("writer", Context.MODE_PRIVATE).getString("author", "") ?: "" }, { commenting = false }) { author, text ->
        commenting = false
        context.getSharedPreferences("writer", Context.MODE_PRIVATE).edit().putString("author", author).apply()
        addComment(author, text)
    }
    replyTo?.let { parent ->
        NewCommentDialog(remember { context.getSharedPreferences("writer", Context.MODE_PRIVATE).getString("author", "") ?: "" }, { replyTo = null }, title = "Reply to " + parent.author.ifBlank { "comment" }) { author, text ->
            replyTo = null
            context.getSharedPreferences("writer", Context.MODE_PRIVATE).edit().putString("author", author).apply()
            replyComment(parent, author, text)
        }
    }
    if (commentsOpen) {
        val first = commentsShown.groupBy { it.first }.mapValues { (_, spans) -> spans.minOf { it.second.first } }
        CommentSidebar(first.entries.sortedBy { it.value }.map { it.key }, hereComment, { commentsOpen = false },
            commentActions { c -> commentsOpen = false; first[c]?.let { state.selection = androidx.compose.ui.text.TextRange(it) } })
    }
    positioning?.let { (page, key) ->
        PositionDialog(page, key, { positioning = null }) { x, y ->
            val img = pictures[key] ?: return@PositionDialog
            changePicture(key, PictureXml.placed(img, x, y, kind))
            position(key) // the page again, the text around it where it now is
        }
    }
    drawing?.let { d ->
        DrawDialog(d.boxPt, { drawing = null }, d.pages, d.start, d.boxAspect, d.strokes) { bytes, wPt, hPt, place, k ->
            drawing = null
            val old = d.edit?.let { pictures[it] }
            if (old == null) putDrawing(bytes, wPt, hPt, place, d.anchors.getOrNull(k))
            else changePicture(old.key, DocImage(old.key, null, wPt, hPt, null, bytes, "png", PictureXml.wrapOf(old) ?: PictureWrap.InFront, onPage = place ?: old.onPage))
        }
    }
    if (shapesPage) ShapesDialog(kind == OfficeKind.Odt, { shapesPage = false }) { shape, fill, line ->
        shapesPage = false
        // An inch square (a line: two inches across); its picture tab sizes, wraps and places it after.
        val (w, h) = if (shape == "line") 144f to 0.75f else 72f to 72f
        val img = DocImage("s${System.nanoTime()}", null, w, h, PictureXml.newShape(shape, kind, fill, line, 1f, w, h))
        pictures[img.key] = img
        val at = placePicture(img)
        state.selection = androidx.compose.ui.text.TextRange(at, at + 1)
        tab = WriterTab.Picture
    }
    /** A new text box (WordArt: [art], its words' colour) at the cursor, the text around it (Square); Position places it. */
    fun putTextBox(lines: List<String>, art: Int?) {
        val (w, h) = if (art == null) 180f to (lines.size * 16f + 12f).coerceAtLeast(36f) // 2½ inches wide, as tall as its lines
            else android.graphics.Paint().apply { textSize = PictureXml.ART_PT; typeface = android.graphics.Typeface.DEFAULT_BOLD }
                .let { p -> lines.maxOf { p.measureText(it) } * 1.1f + 16f to lines.size * PictureXml.ART_PT * 1.25f + 10f } // as big as its words
        val box = lines.map { DocPara(listOf(if (art == null) DocRun(it) else DocRun(it, bold = true, sizePt = PictureXml.ART_PT, color = art and 0xFFFFFF))) }
        val img = PictureXml.withWrap(DocImage("b${System.nanoTime()}", null, w, h, PictureXml.newTextBox(kind, w, h, lines, art), box = box), PictureWrap.Square, kind)
        pictures[img.key] = img
        val at = placePicture(img)
        state.selection = androidx.compose.ui.text.TextRange(at, at + 1)
        tab = WriterTab.Picture
    }
    boxing?.let { (key, words) ->
        TextBoxDialog(words, { boxing = null }) { text, _ ->
            boxing = null
            val lines = text.split('\n')
            val old = key?.let { pictures[it] }
            if (old?.raw != null) changePicture(old.key, old.with(raw = PictureXml.boxText(old.raw, kind, lines), box = PictureXml.boxParas(old, lines)))
            else putTextBox(lines, null)
        }
    }
    charting?.let { (key, start) ->
        ChartDialog(start, key != null, { charting = null }) { ch ->
            charting = null
            val old = key?.let { pictures[it] }
            if (old != null) {
                // Written anew (Word's chart part, LibreOffice's object) where it was, as big, wrapped and placed as it was.
                // ponytail: a file's chart keeps only what Writer reads of it (its data, kind, colours, title), and one
                // placed from its paragraph goes to the paragraph's top left.
                val place = PictureXml.placeOf(old)?.takeIf { it.fromH == "page" && it.fromV == "page" && it.x != null && it.y != null }?.let { it.x!! to it.y!! } ?: old.onPage
                changePicture(old.key, DocImage(old.key, null, old.widthPt, old.heightPt, null, wrap = PictureXml.wrapOf(old)?.takeIf { it != PictureWrap.InLine }, onPage = place, chart = ch))
                return@ChartDialog
            }
            // New: Word's size (5½ × 3.2 inches, or the text's width), in line with the text as Word puts it.
            val w = minOf(396f, sheet.textWidth)
            val img = DocImage("c${System.nanoTime()}", null, w, w * 230f / 396f, chart = ch)
            pictures[img.key] = img
            val at = placePicture(img)
            state.selection = androidx.compose.ui.text.TextRange(at, at + 1)
            tab = WriterTab.Picture
        }
    }
    if (wordArt) TextBoxDialog("", { wordArt = false }, art = true) { text, color -> wordArt = false; putTextBox(text.split('\n'), color) }
    if (pagePage) PageSetupDialog(sheet, { pagePage = false }) { pagePage = false; setPage(it) }
    if (symbolPage) SymbolDialog({ symbolPage = false }) { symbolPage = false; typeIn(it) }
    if (datePage) DateDialog({ datePage = false }) { datePage = false; typeIn(it) }
    if (headerPage) HeaderFooterDialog(HeaderFooter.of(documentStyles.section), HeaderFooter.rich(documentStyles.section), { headerPage = false }) { headerPage = false; setHeaderFooter(it) }
    if (stylesPage) StylesDialog(documentStyles, hereStyle, meta.basePt, { stylesPage = false }, { editingStyle = it }) { stylesPage = false; applyStyle(it) }
    editingStyle?.let { entry ->
        StyleDialog(entry, documentStyles.look(DocPara(emptyList(), heading = entry.heading, keep = entry.id?.let { DocKeep(style = it) })), meta.basePt,
            { editingStyle = null }) { change -> editingStyle = null; editStyle(entry, change) }
    }
    paragraphPage?.let { (at, look) ->
        ParagraphDialog(look, { paragraphPage = null }) { now -> paragraphPage = null; setSpacing(at, look, now) }
    }
    if (showVersions) {
        val list = remember(reload, dirty) { file?.let { versions(context, it) }.orEmpty() }
        val format = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }
        AlertDialog(
            onDismissRequest = { showVersions = false },
            title = { Text("Versions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (list.isEmpty()) "Writer keeps the file as it was before each save — the last 3. None yet: this document has not been saved over here."
                        else "The file as it was before each of Writer's saves. Restoring one keeps the document as it is now as a version too.", style = MaterialTheme.typography.bodySmall)
                    list.forEach { v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Before the save at " + format.format(java.util.Date(v.name.substringBefore('.').toLongOrNull() ?: v.lastModified())), style = MaterialTheme.typography.titleSmall)
                                Text("${(v.length() + 1023) / 1024} KB", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { showVersions = false; restore(v) }, enabled = !dirty) { Text("Restore") }
                        }
                    }
                    if (dirty && list.isNotEmpty()) Text("Save or undo your changes first.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showVersions = false }) { Text("Close") } },
        )
    }
    if (details) AlertDialog(
        onDismissRequest = { details = false },
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (kind == OfficeKind.Docx) "Word document (.docx)" else "OpenDocument text (.odt)", style = MaterialTheme.typography.bodyMedium)
                val all = remember { TextCounts.of(current().paras.joinToString("\n") { it.countedText() }) }
                Text("${plural(all.words, "word")} · ${plural(all.chars, "character")} (${all.withSpaces} with spaces) · ${plural(current().paras.count { it.text.isNotBlank() }, "paragraph")}", style = MaterialTheme.typography.bodyMedium)
                // The selection's counts too, as Word's Word Count does (asked 2026-09-30: the phone's counter is here).
                val sel = state.selection
                if (!sel.collapsed) counted(state.annotatedString.text, sel.min, sel.max).let {
                    Text("Selected: ${plural(it.words, "word")} · ${plural(it.chars, "character")} (${it.withSpaces} with spaces)", style = MaterialTheme.typography.bodyMedium)
                }
                if (meta.blocking.isNotEmpty()) Text("Writer does not show: ${meta.blocking.joinToString()}. Saving makes a copy beside it; the original is never overwritten.", style = MaterialTheme.typography.bodyMedium)
                if (meta.simplified.isNotEmpty()) Text("Simplified on saving: ${meta.simplified.joinToString()}.", style = MaterialTheme.typography.bodyMedium)
                if (meta.unsupported.isEmpty()) Text("Writer keeps everything in this document.", style = MaterialTheme.typography.bodyMedium)
                Text("Page size, margins, headers and footers are kept as the file has them.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { details = false }) { Text("OK") } },
    )
}

/** "1 word", "1,234 words". */
internal fun plural(n: Int, one: String) = "%,d %s".format(n, if (n == 1) one else one + "s")

/** Word's counts: words, characters without spaces, characters with spaces (not paragraph marks or pictures). */
internal class TextCounts(val words: Int, val chars: Int, val withSpaces: Int) {
    companion object {
        private val WORD = Regex("""[\p{L}\p{N}]+(['’-][\p{L}\p{N}]+)*""")
        fun of(text: String) = TextCounts(WORD.findAll(text).count(),
            text.count { !it.isWhitespace() && it !in PICTURES }, text.count { it != '\n' && it != LINE_BREAK && it !in PICTURES })
        private val PICTURES = charArrayOf(OBJECT_MARK, EDITOR_PICTURE_CHAR, PICTURE_SPOT)
    }
}
/** A paragraph's letters as Word counts them: a table's are its cells'. */
internal fun DocPara.countedText(): String = table?.rows?.joinToString("\n") { r -> r.cells.joinToString("\n") { c -> c.paras.joinToString("\n") { it.countedText() } } } ?: text

/**
 * Drawn [k] times its laid-out size from its top left, and taking that much room — the phone's page in Print layout
 * (laid out whole, shown at the screen's width or larger). Touches and the cursor follow the drawing.
 */
private fun Modifier.scaledBy(k: Float) = this.layout { m, c ->
    val p = m.measure(androidx.compose.ui.unit.Constraints())
    layout((p.width * k).roundToInt(), (p.height * k).roundToInt()) {
        p.placeWithLayer(0, 0) { scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) }
    }
}

/**
 * Two fingers zoom between [min] and [max] (the point between them stays put, the scrolls following); one finger is
 * left to the scrolling and the editor underneath. [now] reads the zoom as it is.
 */
private fun Modifier.pinchZoom(now: () -> Float, min: Float, max: Float, side: androidx.compose.foundation.ScrollState, down: androidx.compose.foundation.ScrollState,
    set: (Float) -> Unit) = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            if (e.changes.count { it.pressed } < 2) continue
            val z = now()
            val next = (z * e.calculateZoom()).coerceIn(min, max)
            val ratio = next / z
            if (ratio != 1f) {
                val centre = e.calculateCentroid()
                set(next)
                side.dispatchRawDelta((side.value + centre.x) * ratio - centre.x - side.value)
                down.dispatchRawDelta((down.value + centre.y) * ratio - centre.y - down.value)
            }
            e.changes.forEach { it.consume() }
        }
    }
}

/** A text box's words, a paragraph a line (Insert › Text box, Picture › Edit text); with [art], WordArt's and its colour. */
@Composable
private fun TextBoxDialog(initial: String, dismiss: () -> Unit, art: Boolean = false, done: (String, Int) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    // Word's WordArt colours: blue, orange, grey, gold, green, black (its theme's accents).
    val colors = listOf(0xFF4472C4, 0xFFED7D31, 0xFFA5A5A5, 0xFFFFC000, 0xFF70AD47, 0xFF000000).map { it.toInt() }
    var color by remember { mutableIntStateOf(colors[0]) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (art) "WordArt" else if (initial.isEmpty()) "Text box" else "Edit text") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(text, { text = it }, minLines = if (art) 1 else 3, maxLines = 10, placeholder = { Text("Its words") }, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                textStyle = if (art) MaterialTheme.typography.headlineSmall.copy(color = Color(color), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else androidx.compose.material3.LocalTextStyle.current)
            if (art) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                colors.forEach { c -> Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c)).border(3.dp, if (c == color) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape).clickable { color = c }) }
            }
        } },
        confirmButton = { TextButton(onClick = { done(text, color) }, enabled = text.isNotBlank()) { Text(if (initial.isEmpty()) "Insert" else "Done") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

/** A picture of the document, from the file's own parts or a new picture's bytes. A chart or shape is a grey box. */
@Composable
private fun picturePainter(img: DocImage, parts: Map<String, ByteArray>): Painter {
    val bitmap by produceState<ImageBitmap?>(null, img, parts) {
        val bytes = img.bytes ?: img.part?.let { parts[it] }
        value = bytes?.let { withContext(Dispatchers.IO) { runCatching { decodePicture(it, 1400)?.asImageBitmap() }.getOrNull() } }
    }
    return bitmap?.let { BitmapPainter(it) } ?: ColorPainter(Color(0xFFE3E3E3))
}

/** A picture's bytes as a bitmap no wider or taller than [max] pixels (a phone photo is far bigger than the page needs). */
internal fun decodePicture(bytes: ByteArray, max: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/**
 * A picture from the phone, ready to go into a document: a JPEG or PNG no bigger than 2400 pixels is kept as it is;
 * anything bigger (or of another kind) is made a JPEG of that size. As wide as it is (96 dpi), at most the page's width.
 */
private fun preparePicture(context: Context, uri: Uri): DocImage {
    val raw = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    val mime = bounds.outMimeType ?: ""
    val keep = (mime == "image/jpeg" || mime == "image/png") && maxOf(bounds.outWidth, bounds.outHeight) <= 2400
    val bytes: ByteArray; val w: Int; val h: Int; val ext: String
    if (keep) { bytes = raw; w = bounds.outWidth; h = bounds.outHeight; ext = if (mime == "image/png") "png" else "jpg" }
    else {
        val b = decodePicture(raw, 2400) ?: error("Not a picture.")
        val scale = (2400f / maxOf(b.width, b.height)).coerceAtMost(1f)
        val s = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true) else b
        bytes = java.io.ByteArrayOutputStream().also { s.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        w = s.width; h = s.height; ext = "jpg"
    }
    val widthPt = (w * 0.75f).coerceAtMost(450f)
    return DocImage("n" + java.util.UUID.randomUUID(), null, widthPt, widthPt * h / w, null, bytes, ext)
}

/** Word's familiar table picker: tap or drag to choose the rectangle from its top-left corner. */
@Composable private fun TableSizeGrid(maxRows: Int, maxColumns: Int, rows: Int, columns: Int, select: (Int, Int) -> Unit) {
    val cell = 40.dp
    BoxWithConstraints(
        Modifier.width(cell * maxColumns).height(cell * maxRows).pointerInput(maxRows, maxColumns) {
            fun choose(point: Offset) {
                val column = (point.x / (size.width / maxColumns.toFloat())).toInt().coerceIn(0, maxColumns - 1)
                val row = (point.y / (size.height / maxRows.toFloat())).toInt().coerceIn(0, maxRows - 1)
                select(row + 1, column + 1)
            }
            detectDragGestures(onDragStart = ::choose, onDrag = { change, _ -> change.consume(); choose(change.position) })
        },
    ) {
        Column {
            repeat(maxRows) { row ->
                Row {
                    repeat(maxColumns) { column ->
                        Box(
                            Modifier.size(cell).padding(2.dp).clip(RoundedCornerShape(4.dp))
                                .background(if (row < rows && column < columns) WORD_BLUE else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .clickable { select(row + 1, column + 1) },
                        )
                    }
                }
            }
        }
    }
}

@Composable private fun Divider(tint: Color) = Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(24.dp).background(tint.copy(alpha = 0.3f)))

/** Design › Paragraph spacing: Word's sets — space after (pt) and line spacing of the Normal style. */
private val SPACING_SETS = listOf(Triple("No paragraph space", 0f, 1f), Triple("Compact", 4f, 1f), Triple("Tight", 6f, 1.15f),
    Triple("Open", 10f, 1.15f), Triple("Relaxed", 6f, 1.5f), Triple("Double", 8f, 2f))

/** Design › Page colour: Word's light theme tints, and white (a page coloured white is not the same as none in Word). */
private val PAGE_COLORS = listOf(0xFFF2CC, 0xE2EFDA, 0xDDEBF7, 0xFCE4D6, 0xEDEDED, 0xFFFFFF)

/** A named tool of the ribbon (Save, Heading 1…). */
@Composable internal fun RibbonButton(label: String, tint: Color, icon: Int? = null, enabled: Boolean = true, on: Boolean = false, click: () -> Unit) = Row(
    Modifier.height(40.dp).clip(CircleShape).then(if (on) Modifier.background(tint.copy(alpha = 0.18f)) else Modifier)
        // Never taking the focus: tapped while a table cell is being typed in, it took it, the keyboard went down, the
        // ribbon moved under the finger and the tap was lost.
        .focusProperties { canFocus = false }
        .clickable(enabled = enabled, onClick = click).padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    val c = if (enabled) tint else tint.copy(alpha = 0.35f)
    icon?.let { Icon(painterResource(it), contentDescription = null, tint = c, modifier = Modifier.size(18.dp)) }
    Text(label, color = c, style = MaterialTheme.typography.labelLarge, maxLines = 1)
}

/** A picture in front of or behind the text, in the editor: from the text's left and its paragraph's top (pt), its size. */
internal class FloatShown(val x: Float, val y: Float, val w: Float, val h: Float, val behind: Boolean, val pageX: Float, val pageY: Float)

/** Spans with their same-looking neighbours joined, as the editor joins them once laid out. */
private fun joinedSpans(spans: List<androidx.compose.ui.text.AnnotatedString.Range<androidx.compose.ui.text.SpanStyle>>) =
    spans.sortedWith(compareBy({ it.start }, { it.end })).fold(ArrayList<androidx.compose.ui.text.AnnotatedString.Range<androidx.compose.ui.text.SpanStyle>>()) { out, r ->
        val i = out.indexOfLast { it.item == r.item && it.end == r.start }
        if (i >= 0) out[i] = out[i].copy(end = r.end) else out += r
        out
    }.sortedWith(compareBy({ it.start }, { it.end }, { it.item.hashCode() }))

/** A colour tool: tapped, its swatches open beside it on the same line; the crossed-out one removes the colour. */
@Composable internal fun Swatches(tint: Color, icon: Int, label: String, colors: List<Color>, choose: (Color?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Tool(icon, label, tint, on = open) { open = !open }
    if (open) {
        Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClickLabel = "No $label") { choose(null); open = false }, contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_no_color), contentDescription = "No ${label.lowercase(Locale.ROOT)}", tint = tint, modifier = Modifier.size(22.dp))
        }
        colors.forEach { c ->
            Box(Modifier.padding(3.dp).size(28.dp).clip(CircleShape).background(c).border(1.dp, tint.copy(alpha = 0.5f), CircleShape)
                .clickable(onClickLabel = label) { choose(c); open = false })
        }
        Spacer(Modifier.width(4.dp))
    }
}

/**
 * The document's pages (Print layout): white pages of the file's own size on the dark desk, "Page n of N" at the
 * bottom, pinch to zoom (sideways scrolling when wider). A tablet shows them as a desktop does (asked 2026-09-29): a
 * page at the editor's text size ([perPt] a point) when it fits, centred, with Word's rulers — along the top for the
 * page in view, down each page's side. A phone fits the page to its width, no rulers.
 */
@Composable
private fun PagesView(pages: List<LaidPage>, perPt: Dp, modifier: Modifier) {
    var zoom by remember { mutableFloatStateOf(1f) }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    BoxWithConstraints(modifier) {
        val rulers = maxWidth >= 700.dp
        val ruler = if (rulers) 18.dp else 0.dp
        val gap = if (rulers) 6.dp else 0.dp
        val widest = pages.maxOf { it.page.width }
        val fit = (maxWidth - ruler - gap - 24.dp) / widest
        val scale = (if (rulers) minOf(perPt, fit) else fit) * zoom // on screen, per point
        val content = maxOf(maxWidth, widest * scale + ruler + gap + 24.dp)
        val sideways = rememberScrollState()
        val at by remember { derivedStateOf { list.firstVisibleItemIndex + if (list.firstVisibleItemScrollOffset > 0 && list.layoutInfo.visibleItemsInfo.size > 1) 1 else 0 } }
        Column(Modifier.fillMaxSize()) {
            if (rulers) {
                val page = pages[at.coerceIn(0, pages.lastIndex)].page
                val x = (content - (page.width * scale + ruler + gap)) / 2 + ruler + gap - with(LocalDensity.current) { sideways.value.toDp() }
                HorizontalRuler(page, scale, x, Modifier.fillMaxWidth().height(22.dp))
            }
            androidx.compose.foundation.lazy.LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).horizontalScroll(sideways).width(content)
                    .transformable(androidx.compose.foundation.gestures.rememberTransformableState { change, _, _ -> zoom = (zoom * change).coerceIn(0.5f, 4f) }, canPan = { false }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 72.dp),
            ) {
                items(pages.size) { i ->
                    val laid = pages[i]
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        if (rulers) VerticalRuler(laid.page, scale, Modifier.width(ruler).height(scale * laid.page.height))
                        androidx.compose.foundation.Canvas(Modifier.width(scale * laid.page.width).aspectRatio(laid.page.width / laid.page.height).background(Color.White)) {
                            drawIntoCanvas { c ->
                                val n = c.nativeCanvas
                                val k = size.width / laid.page.width
                                n.save(); n.scale(k, k); laid.draw(n); n.restore()
                            }
                        }
                    }
                }
            }
        }
        Surface(color = Color.Black.copy(alpha = 0.75f), contentColor = Color.White, shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Page ${(at + 1).coerceAtMost(pages.size)} of ${pages.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
}
