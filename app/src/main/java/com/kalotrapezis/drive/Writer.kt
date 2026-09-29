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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
private const val STYLE_SLANT = 0x01L // the shadow's colour: see-through

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

    /** How a picture shows in the editor: its size, scaled with the text, and never wider than the page. */
    fun shownSize(img: DocImage, scale: Float): Pair<Float, Float> {
        val w = img.widthPt * scale
        if (img.table != null) return w to img.heightPt * scale // laid out for the editor's width already, at its text size
        val fit = (MAX_PICTURE_SP / w).coerceAtMost(1f)
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
                textAlign = when (p.align) { DocAlign.Start -> TextAlign.Unspecified; DocAlign.Center -> TextAlign.Center; DocAlign.End -> TextAlign.End; DocAlign.Justify -> TextAlign.Justify },
                // The paragraph's left and first-line indents shown (a list item's indent is the editor's own).
                textIndent = if (p.list != null || p.table != null) null else look.let { l ->
                    val rest = l.left.coerceAtLeast(0f) * scale
                    val first = if (afterBreak) rest else (l.left + l.firstLine).coerceAtLeast(0f) * scale
                    if (rest == 0f && first == 0f) null else androidx.compose.ui.text.style.TextIndent(first.sp, rest.sp)
                },
                isLineBreak = afterBreak,
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
                    is RichTextSpanMark.TextColor -> if ((m.argb ushr 24) == STYLE_ALPHA) r else r.copy(color = (m.argb and 0xFFFFFF).toInt())
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
            val align = when (b.textAlign) { TextAlign.Center -> DocAlign.Center; TextAlign.End, TextAlign.Right -> DocAlign.End; TextAlign.Justify -> DocAlign.Justify; else -> DocAlign.Start }
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
    /** The editor's block the text at [at] is in. */
    fun blockAt(text: String, rich: RichTextDocument, at: Int): Int? {
        var found: Int? = null
        blockSpans(text, rich) { k, from, to -> if (found == null && at in from..to) found = k }
        return found
    }
    private fun blockSpans(text: String, rich: RichTextDocument, each: (Int, Int, Int) -> Unit) {
        var pos = 0
        rich.blocks.forEachIndexed { k, b ->
            val from = text.indexOf(b.text, pos).takeIf { it >= 0 } ?: return
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
    fun tableSize(doc: OfficeDoc, table: DocTable, styles: DocStyles) = OfficePages.tableSize(doc, table, styles,
        if (pageWidthPt != null) styles.page.textWidth else editorWidthSp / (BASE_SP / doc.basePt))
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
    private fun unbold(runs: List<DocRun>) = OfficeFormats.tidy(runs.map { it.copy(bold = false, font = null) })

    /** Paragraphs as they compare: a list's start number counts only where a list begins; bold in a heading, not at all. */
    fun comparable(paras: List<DocPara>) = paras.mapIndexed { i, p ->
        val firstOfList = p.list != null && (i == 0 || paras[i - 1].list == null)
        p.copy(runs = breaksLookAround(if (p.heading > 0) unbold(p.runs) else OfficeFormats.tidy(p.runs.map { it.copy(font = null) })).let(OfficeFormats::tidy), keep = null, list = p.list?.let { l -> l.copy(start = if (firstOfList && l.ordered && l.start != 1) l.start else null) })
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

private enum class WriterTab(val label: String) { File("File"), Home("Home"), Insert("Insert"), View("View"), Picture("Picture"), Table("Table") }
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
    val perPt = with(LocalDensity.current) { (BASE_SP * zoom / 100f / meta.basePt).sp.toDp() }
    val screen = LocalConfiguration.current.screenWidthDp.dp
    val onPage = screen >= 700.dp && sheet.width * perPt + 24.dp <= screen
    WriterBridge.pageWidthPt = if (onPage) sheet.textWidth else null
    // Else the page's text is the screen less its margins (10 + 22 dp a side, below).
    WriterBridge.editorWidthSp = with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp - 64).dp.toSp().value }
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

    LaunchedEffect(reload) {
        loaded = false
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = file?.readBytes()
                val doc = bytes?.let { OfficeFormats.read(it, kind) } ?: OfficeDoc(listOf(DocPara(emptyList())), basePt = 11f)
                val draft = draftFile(context, draftKey, kind).takeIf { it.isFile }?.let { d -> runCatching {
                    val draftBytes = d.readBytes()
                    val draftParts = OfficeFormats.unzip(draftBytes)
                    val originalParts = bytes?.let { OfficeFormats.unzip(it) }.orEmpty()
                    // A picture added before the app closed lives only in the draft: it is new again, with its bytes.
                    OfficeFormats.read(draftBytes, kind).let { dd -> dd.copy(paras = dd.paras.map { p -> p.copy(runs = p.runs.map { r ->
                        val img = r.image
                        if (img?.part != null && img.part !in originalParts) r.copy(image = DocImage("d" + img.key, null, img.widthPt, img.heightPt, null, draftParts[img.part], img.part.substringAfterLast('.'))) else r
                    }) }) }
                }.getOrNull() }
                packageParts = bytes?.let { OfficeFormats.unzip(it) }.orEmpty()
                Triple(bytes, doc, draft)
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
                meta = meta.copy(paras = draft.paras) // the draft's paragraph formatting (spacing set before it closed), not the file's
                dirty = true; message = "Your unsaved changes are back"
            }
            state.history.clear()
            // The cursor at the start (the editor leaves it at the end): the first tap brought the end into view.
            state.selection = androidx.compose.ui.text.TextRange(0)
            loaded = true
        }.onFailure { failed = it.message ?: "This file could not be read." }
    }
    // Any change marks the document unsaved, and is kept as a draft after a short pause.
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        snapshotFlow { state.annotatedString to meta.paras }.drop(1).collectLatest { // meta: a paragraph's spacing set

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
    fun placePicture(img: DocImage): Int {
        state.addTextAfterSelection(PICTURE_SPOT.toString())
        val at = state.selection.min - 1
        state.setRichTextDocument(WriterBridge.toEditor(WriterBridge.putPicture(current(), img), pictures, documentStyles), androidx.compose.ui.text.TextRange(at, at + 1))
        dirty = true
        return at
    }
    /** A picture dragged from [from] to [to] in the text: taken out and put in again there; it stays selected. */
    fun movePicture(from: Int, to: Int, key: String) {
        val img = pictures[key] ?: return
        if (to == from || to == from + 1) return
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
    fun changePicture(key: String, img: DocImage) {
        pictures[key] = img
        val at = state.selection
        state.setRichTextDocument(WriterBridge.toEditor(current(), pictures, documentStyles), at)
        dirty = true
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
    fun setSpacing(at: Set<Int>, was: ParaLook, now: ParaLook) {
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
    fun chooseParagraphs(then: (Set<Int>, ParaLook) -> Unit) {
        chosen()?.let { (at, look) -> then(at, look) } ?: run { message = "Put the cursor in a paragraph of text" }
    }
    // The ruler shows the indents of the paragraph the cursor is in, and its marks set them (as Word's).
    var rulerAt by remember { mutableStateOf<Pair<Set<Int>, ParaLook>?>(null) }
    LaunchedEffect(loaded, onPage, viewing) {
        if (!loaded || !onPage || viewing) return@LaunchedEffect
        snapshotFlow { state.selection to meta.paras }.collectLatest { delay(200); rulerAt = chosen() }
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
    Box(Modifier.fillMaxSize().background(Color(0xFF1B1D1E)).statusBarsPadding().imePadding()) {
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
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Tool(R.drawable.ic_chevron_left, "Back", w) { leave() }
                Text((if (dirty) "• " else "") + name, color = w, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                if (viewing) {
                    Tool(R.drawable.ic_share, "Share", w, enabled = file?.isFile == true) { file?.let { shareDriveFile(context, it) } }
                    if (dirty) Tool(R.drawable.ic_save, "Save", w, enabled = loaded) { save() }
                    RibbonButton("Edit", w, R.drawable.ic_edit, enabled = loaded) { viewing = false }
                } else {
                Tool(R.drawable.ic_visibility, "Pages", w, enabled = loaded) { viewing = true }
                Tool(R.drawable.ic_search, "Find and replace", w, enabled = loaded) { finding = true }
                Tool(R.drawable.ic_undo, "Undo", w, enabled = state.history.canUndo) { state.history.undo() }
                Tool(R.drawable.ic_redo, "Redo", w, enabled = state.history.canRedo) { state.history.redo() }
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
            if (viewing && failed == null) Box(Modifier.weight(1f).fillMaxWidth()) {
                pages?.let { PagesView(it, with(LocalDensity.current) { (BASE_SP / meta.basePt).sp.toDp() }, Modifier.fillMaxSize()) }
                    ?: Text("Laying out the pages…", color = w.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center))
            } else
            // The page: white, as the document will print — its own colours must show as they are.
            Column(Modifier.weight(1f).fillMaxWidth()) {
            val margins = if (onPage) androidx.compose.foundation.layout.PaddingValues(start = sheet.left * perPt, end = sheet.right * perPt, top = sheet.top * perPt, bottom = 180.dp)
                else androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 180.dp)
            if (onPage && loaded) BoxWithConstraints(Modifier.fillMaxWidth()) {
                val x = (maxWidth - sheet.width * perPt) / 2
                IndentRuler(sheet, perPt, x, rulerIndents) { setIndents(it) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Surface(color = Color.White, shape = RoundedCornerShape(if (onPage) 0.dp else 6.dp),
                    modifier = (if (onPage) Modifier.align(Alignment.TopCenter).width(sheet.width * perPt).heightIn(min = sheet.height * perPt) else Modifier.fillMaxWidth()).heightIn(min = 520.dp)) {
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
                            BasicRichTextEditor(state, textStyle = TextStyle(color = ink, fontSize = (BASE_SP * zoom / 100).sp),
                                cursorBrush = SolidColor(Color(0xFF1A73E8)), onTextLayout = { layout = it },
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
                                var drag by remember { mutableStateOf(Offset.Zero) } // how far the selected picture is being dragged
                                Box(Modifier.matchParentSize().padding(margins)) {
                                    placed.forEach { (i, img, at) ->
                                        val (left, bottom) = at
                                        val (w, h) = WriterBridge.shownSize(img, scale)
                                        val y = if (img.table != null) l.getLineTop(l.getLineForOffset(i)).toInt()
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
                                            .then(if (!selected) Modifier else Modifier.graphicsLayer { translationX = drag.x; translationY = drag.y; alpha = if (drag == Offset.Zero) 1f else 0.7f }
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
                                            } else Image(picturePainter(img, packageParts), contentDescription = PictureXml.kind(img), contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
                                            // Not a picture (a chart, a shape): a grey box that says what it is, kept in the file as it was.
                                            if (pictureBytes(img) == null && img.table == null) Text(PictureXml.kind(img), color = Color(0xFF5F6368), style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
                        RibbonButton(label, w, on = sign == Math.signum(shift.multiplier)) {
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
                    }
                    Divider(w)
                    // Superscript and subscript are shown smaller: the size told and changed is the letters' own.
                    val shrink = if ((span.baselineShift?.multiplier ?: 0f) != 0f) SCRIPT_SIZE else 1f
                    val size = (WriterBridge.spOf(span.fontSize) ?: BASE_SP) / shrink
                    // One point at a time, in the document's points (asked 2026-09-28: by 2 it skipped 13); − and + close
                    // together around a number of fixed width, so two digits fit on a phone as one does.
                    val pt = (size / BASE_SP * meta.basePt).roundToInt()
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
                }, {
                    listOf(HeadingStyle.Normal to "Normal", HeadingStyle.H1 to "H1", HeadingStyle.H2 to "H2", HeadingStyle.H3 to "H3").forEach { (h, label) ->
                        RibbonButton(label, w, on = state.currentHeadingStyle == h) { state.setHeadingStyle(h) }
                    }
                }, {
                    Tool(R.drawable.ic_format_list_bulleted, "Bullets", w, on = state.isUnorderedList) { state.toggleUnorderedList() }
                    Tool(R.drawable.ic_format_list_numbered, "Numbering", w, on = state.isOrderedList) { state.toggleOrderedList() }
                    Tool(R.drawable.ic_format_indent_decrease, "Decrease indent", w, enabled = state.canDecreaseListLevel) { state.decreaseListLevel() }
                    Tool(R.drawable.ic_format_indent_increase, "Increase indent", w, enabled = state.canIncreaseListLevel) { state.increaseListLevel() }
                }, {
                    listOf(TextAlign.Start to R.drawable.ic_align_left, TextAlign.Center to R.drawable.ic_align_center, TextAlign.End to R.drawable.ic_align_right, TextAlign.Justify to R.drawable.ic_align_justify).forEach { (a, icon) ->
                        val on = para.textAlign == a || (a == TextAlign.Start && (para.textAlign == TextAlign.Unspecified || para.textAlign == TextAlign.Left))
                        Tool(icon, "Align ${a.toString().lowercase(Locale.ROOT)}", w, on = on) { state.addParagraphStyle(ParagraphStyle(textAlign = a)) }
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
                    RibbonButton("More…", w, R.drawable.ic_more_vert) { chooseParagraphs { at, look -> paragraphPage = at to look } }
                })
                WriterTab.Insert -> listOf({
                    RibbonButton("Picture", w, R.drawable.ic_gallery, enabled = loaded) { pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    RibbonButton(if (state.isLink) "Edit link" else "Link", w, R.drawable.ic_link) { linking = true }
                }, {
                    // A new line in the same paragraph — Shift+Enter in Word, which a phone keyboard does not have.
                    RibbonButton("Line break", w) {
                        state.addTextAfterSelection(LINE_BREAK.toString())
                        val at = state.selection
                        state.setRichTextDocument(WriterBridge.toEditor(current(), pictures, documentStyles), at) // shown as a new line at once
                    }
                    RibbonButton("Table", w, enabled = loaded) { insertingTable = true }
                })
                WriterTab.Picture -> {
                    val (at, key) = selectedPicture ?: (0 to "")
                    val img = pictures[key]
                    val editable = img != null && PictureXml.editable(img)
                    val hasBytes = img != null && pictureBytes(img) != null
                    listOf({
                        RibbonButton("Size", w, enabled = editable) { sizing = true }
                        RibbonButton("Crop", w, enabled = hasBytes && editable) {
                            scope.launch {
                                cropping = withContext(Dispatchers.IO) { pictureBytes(img!!)?.let { runCatching { decodePicture(it, 2400) }.getOrNull() } }
                                if (cropping == null) message = "Writer cannot crop this kind of picture."
                            }
                        }
                        RibbonButton("Wrap text", w, enabled = editable) { wrapping = true }
                    }, {
                        RibbonButton("Turn left", w, enabled = hasBytes && editable) { transform(-90, false) }
                        RibbonButton("Turn right", w, enabled = hasBytes && editable) { transform(90, false) }
                        RibbonButton("Flip", w, enabled = hasBytes && editable) { transform(0, true) }
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
                        RibbonButton("Merge", w, enabled = mergeTo != null && n > 1) {
                            val to = mergeTo!!
                            change("These cells cannot be merged.") { t, _ -> TableEdit.merge(t, from.row to from.cell, to.row to to.cell) }
                            selectedCell = from; mergeFrom = null; mergeTo = null
                        }
                        RibbonButton("Cancel", w) { mergeFrom = null; mergeTo = null }
                    }) else listOf({
                        // Everyday tools on the ribbon, the rest behind ⋯ (asked 2026-09-28); three to a page, as a phone fits them.
                        RibbonButton("Row above", w, enabled = table != null) { change { t, c -> TableEdit.insertRow(t, c.row, c.cell, below = false) } }
                        RibbonButton("Row below", w, enabled = table != null) { change { t, c -> TableEdit.insertRow(t, c.row, c.cell, below = true) } }
                        RibbonButton("Merge", w, enabled = table != null) { commitCell(); mergeFrom = here(); mergeTo = null }
                    }, {
                        RibbonButton("Column left", w, enabled = table != null) { change { t, c -> TableEdit.insertColumn(t, c.row, c.cell, right = false) } }
                        RibbonButton("Column right", w, enabled = table != null) { change { t, c -> TableEdit.insertColumn(t, c.row, c.cell, right = true) } }
                        RibbonButton("Split", w, enabled = table != null) { commitCell(); tableDialog = "split" }
                    }, {
                        RibbonButton("Delete row", w, enabled = table != null && table.rows.size > 1) { change("The last row stays.") { t, c -> TableEdit.deleteRow(t, c.row, c.cell) }; selectedCell = null }
                        RibbonButton("Delete column", w, enabled = table != null && TableEdit.gridCount(table) > 1) { change("This column cannot be taken away.") { t, c -> TableEdit.deleteColumn(t, c.row, c.cell) }; selectedCell = null }
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
                WriterTab.View -> listOf({
                    Tool(R.drawable.ic_remove, "Zoom out", w, enabled = zoom > 70) { zoom -= 10 }
                    Text("$zoom%", color = w, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 4.dp))
                    Tool(R.drawable.ic_add, "Zoom in", w, enabled = zoom < 200) { zoom += 10 }
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
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { pages[page]() }
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
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { wrapping = false; if (wrap != now) changePicture(key, img.with(wrap = wrap)) }) {
                                RadioButton(wrap == now, null)
                                Text(wrap.label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        // ponytail: floating pictures are drawn in line; showing them in place comes with dragging.
                        Text("Writer shows it in line for now; Word and LibreOffice show it as chosen.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                },
                confirmButton = { TextButton(onClick = { wrapping = false }) { Text("Close") } },
            )
        }
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
                val all = remember { current().paras.joinToString("\n") { it.text } }
                val words = Regex("""[\p{L}\p{N}]+(['’-][\p{L}\p{N}]+)*""").findAll(all).count()
                Text("$words words · ${all.count { !it.isWhitespace() }} characters (${all.count { it != '\n' && it != LINE_BREAK }} with spaces) · ${current().paras.count { it.text.isNotBlank() }} paragraphs", style = MaterialTheme.typography.bodyMedium)
                if (meta.blocking.isNotEmpty()) Text("Writer does not show: ${meta.blocking.joinToString()}. Saving makes a copy beside it; the original is never overwritten.", style = MaterialTheme.typography.bodyMedium)
                if (meta.simplified.isNotEmpty()) Text("Simplified on saving: ${meta.simplified.joinToString()}.", style = MaterialTheme.typography.bodyMedium)
                if (meta.unsupported.isEmpty()) Text("Writer keeps everything in this document.", style = MaterialTheme.typography.bodyMedium)
                Text("Page size, margins, headers and footers are kept as the file has them.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { details = false }) { Text("OK") } },
    )
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
