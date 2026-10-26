package com.kalotrapezis.drive

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import org.json.JSONObject
import kotlin.math.ceil

/**
 * Words on a PDF page with Words' looks (the user, 24 Oct: "text input fonts format like in Words, Slides and Tables"):
 * Fill & Sign › Text and Review › Text box. One look for all its words, as a Slides box's Home tools give it.
 */
internal data class PdfTextLook(val font: String = "Times New Roman", val size: Float = 12f, val bold: Boolean = false, val italic: Boolean = false,
    val underline: Boolean = false, /** 0xRRGGBB */ val color: Int = 0x000000, /** 0xRRGGBB or none */ val highlight: Int? = null, val align: DocAlign = DocAlign.Start,
    /** the box's fill and its border's colour (0xRRGGBB), as a Slides box's; none: clear, no border */ val fill: Int? = null, val line: Int? = null,
    /** turned clockwise, degrees (0–359) */ val turn: Int = 0)

/**
 * Some of a text's words with a look of their own (the user, 24 Oct: "finish"): [from] to [to] in its words, bold,
 * italic, underlined, struck out, coloured or highlighted over the text's own look, as Words' letters are.
 */
internal data class PdfRun(val from: Int, val to: Int, val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false,
    val strike: Boolean = false, /** 0xRRGGBB */ val color: Int? = null, val highlight: Int? = null) {
    /** As Words' editor holds a letter's look. */
    fun style() = androidx.compose.ui.text.SpanStyle(fontWeight = if (bold) FontWeight.Bold else null, fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = TextDecoration.combine(listOfNotNull(TextDecoration.Underline.takeIf { underline }, TextDecoration.LineThrough.takeIf { strike })).takeIf { underline || strike },
        color = color?.let { Color(0xFF000000 or it.toLong()) } ?: Color.Unspecified, background = highlight?.let { Color(0xFF000000 or it.toLong()) } ?: Color.Unspecified)

    companion object {
        /**
         * The looks Words' editor gives some of [words]' letters ([text]: its styled copy, a paragraph's end a space there),
         * as runs; none when the two do not line up letter for letter.
         */
        fun of(text: androidx.compose.ui.text.AnnotatedString, words: String): List<PdfRun> = if (text.length != words.length) emptyList() else text.spanStyles.mapNotNull { r ->
            val st = r.item; val deco = st.textDecoration
            PdfRun(r.start, r.end, (st.fontWeight?.weight ?: 0) >= 600, st.fontStyle == FontStyle.Italic, deco?.contains(TextDecoration.Underline) == true,
                deco?.contains(TextDecoration.LineThrough) == true, st.color.takeIf { it.isSpecified }?.let { it.toArgb() and 0xFFFFFF },
                st.background.takeIf { it.isSpecified && it.alpha > 0f }?.let { it.toArgb() and 0xFFFFFF })
                .takeIf { it.from < it.to && (it.bold || it.italic || it.underline || it.strike || it.color != null || it.highlight != null) }
        }
    }
}

/** A text put on a page: its words, their look, whether it is a text box (a thin frame, as wide as it was drawn), and words with their own look. */
internal data class PdfText(val words: String, val look: PdfTextLook, val framed: Boolean, val runs: List<PdfRun> = emptyList()) {
    /** Its size as laid out before turning (points), as the file keeps it; null: from its place on the page ([unturned]). */
    var laid: Pair<Float, Float>? = null
    /** Kept in the file with it (the annotation's own key), so a tap opens it again as it was typed; [laid] its size before turning. */
    fun json(laid: Pair<Float, Float>? = null): String = JSONObject().put("words", words).put("font", look.font).put("size", look.size.toDouble()).put("bold", look.bold).put("italic", look.italic)
        .put("underline", look.underline).put("color", look.color).put("highlight", look.highlight ?: -1).put("align", look.align.name).put("framed", framed)
        .put("fill", look.fill ?: -1).put("line", look.line ?: -1).put("turn", look.turn)
        .apply { laid?.let { (w, h) -> put("laidW", w.toDouble()).put("laidH", h.toDouble()) } }
        .apply { if (runs.isNotEmpty()) put("runs", org.json.JSONArray(runs.map { r -> JSONObject().put("from", r.from).put("to", r.to).put("b", r.bold).put("i", r.italic)
            .put("u", r.underline).put("s", r.strike).put("color", r.color ?: -1).put("highlight", r.highlight ?: -1) })) }.toString()

    /** Inside its frame, in points: a text box's words off its frame, a text's hugging them. */
    val pad get() = if (framed) 4f else 1f
    /** Turned at all: it keeps the width it was laid out in (the page's edge means nothing to it). */
    val turned get() = look.turn != 0
    /** [r] (where it is on the page) as it is laid out before turning: across, down. */
    fun unturned(r: android.graphics.RectF) = laid ?: if (look.turn % 180 == 90) r.height() to r.width() else r.width() to r.height()

    companion object {
        fun parse(s: String): PdfText? = runCatching {
            val o = JSONObject(s)
            PdfText(o.getString("words"), PdfTextLook(o.optString("font", "Times New Roman"), o.optDouble("size", 12.0).toFloat(), o.optBoolean("bold"), o.optBoolean("italic"),
                o.optBoolean("underline"), o.optInt("color", 0), o.optInt("highlight", -1).takeIf { it >= 0 }, runCatching { DocAlign.valueOf(o.optString("align")) }.getOrDefault(DocAlign.Start),
                o.optInt("fill", -1).takeIf { it >= 0 }, o.optInt("line", if (o.optBoolean("framed")) 0 else -1).takeIf { it >= 0 }, // a box put on before borders were chosen: its black frame
                Math.floorMod(o.optInt("turn"), 360)),
                o.optBoolean("framed"), o.optJSONArray("runs")?.let { a -> (0 until a.length()).map { k -> a.getJSONObject(k).let { r ->
                    PdfRun(r.getInt("from"), r.getInt("to"), r.optBoolean("b"), r.optBoolean("i"), r.optBoolean("u"), r.optBoolean("s"),
                        r.optInt("color", -1).takeIf { it >= 0 }, r.optInt("highlight", -1).takeIf { it >= 0 }) } } }.orEmpty()).also { t ->
                if (o.has("laidW")) t.laid = o.getDouble("laidW").toFloat() to o.getDouble("laidH").toFloat() }
        }.getOrNull()

        /** Its place on the page for [w] × [h] laid out, turned: the box round the turned one. */
        fun bounds(t: PdfText, w: Float, h: Float): Pair<Float, Float> {
            val a = Math.toRadians(t.look.turn.toDouble()); val c = kotlin.math.abs(kotlin.math.cos(a)).toFloat(); val s = kotlin.math.abs(kotlin.math.sin(a)).toFloat()
            return ceil(w * c + h * s - 0.01f) to ceil(w * s + h * c - 0.01f) // a hair off: a quarter turn's cos is 6e-17, not 0
        }

        private fun layout(t: PdfText, width: Float): StaticLayout {
            val l = t.look
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = l.size; color = 0xFF000000.toInt() or l.color; isUnderlineText = l.underline
                fontFeatureSettings = "'liga' 0, 'clig' 0" // a joined pair ("ll" in a script font) copied and found as nothing in the PDF
                typeface = Typeface.create(OfficeFonts.typeface(l.font) ?: Typeface.DEFAULT, if (l.bold) 700 else 400, l.italic)
            }
            val s = SpannableString(t.words)
            l.highlight?.let { s.setSpan(BackgroundColorSpan(0xFF000000.toInt() or it), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            // words with their own look, over the text's: the font's own bold and italic faces (not drawn thicker or slanted)
            val base = OfficeFonts.typeface(l.font) ?: Typeface.DEFAULT
            t.runs.filter { it.from < minOf(it.to, s.length) }.forEach { r ->
                fun span(o: Any) = s.setSpan(o, r.from, minOf(r.to, s.length), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (r.bold || r.italic) span(android.text.style.TypefaceSpan(Typeface.create(base, if (l.bold || r.bold) 700 else 400, l.italic || r.italic)))
                if (r.underline) span(android.text.style.UnderlineSpan())
                if (r.strike) span(android.text.style.StrikethroughSpan())
                r.color?.let { span(android.text.style.ForegroundColorSpan(0xFF000000.toInt() or it)) }
                r.highlight?.let { span(BackgroundColorSpan(0xFF000000.toInt() or it)) }
            }
            return StaticLayout.Builder.obtain(s, 0, s.length, paint, ceil(width).toInt().coerceAtLeast(1))
                .setAlignment(when (l.align) { DocAlign.Center -> Layout.Alignment.ALIGN_CENTER; DocAlign.End -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL })
                .setJustificationMode(if (l.align == DocAlign.Justify) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE)
                .setIncludePad(false).build() // as Compose's field lays it out while it is typed
        }

        /**
         * Its size on the page (points) given [room] across: a text box keeps the width it was drawn and grows down to
         * [minHeight]; a text is as wide as its longest line.
         */
        fun size(t: PdfText, room: Float, minHeight: Float = 0f): Pair<Float, Float> {
            val inner = room - 2 * t.pad
            val l = layout(t, inner)
            val w = if (t.framed) room else (0 until l.lineCount).maxOfOrNull { l.getLineWidth(it) }.let { (it ?: 0f) + 2 * t.pad }.coerceAtMost(room)
            return ceil(w) to ceil(maxOf(minHeight, l.height + 2 * t.pad))
        }

        /**
         * The words drawn [w] × [h] points (as laid out, before turning): a one-page PDF of their own (Android's, its fonts
         * embedded), to be the annotation's look — turned, its page the other way round.
         */
        fun pdf(t: PdfText, w: Float, h: Float): ByteArray {
            val doc = android.graphics.pdf.PdfDocument()
            try {
                val (pw, ph) = bounds(t, w, h)
                val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(ceil(pw).toInt(), ceil(ph).toInt(), 1).create())
                // turned about its middle, in the middle of the box round it
                if (t.turned) page.canvas.apply { translate(pw / 2, ph / 2); rotate(t.look.turn.toFloat()); translate(-w / 2, -h / 2) }
                draw(page.canvas, t, w, h)
                doc.finishPage(page)
                return java.io.ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
            } finally { doc.close() }
        }

        private fun draw(c: Canvas, t: PdfText, w: Float, h: Float) {
            t.look.fill?.let { f -> c.drawRect(0f, 0f, w, h, Paint().apply { color = 0xFF000000.toInt() or f }) }
            t.look.line?.let { l -> c.drawRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.75f; color = 0xFF000000.toInt() or l }) }
            // a text box's alignment is across the box; a text's within its own width (its longest line)
            val l = layout(t, w - 2 * t.pad)
            c.save(); c.translate(t.pad, t.pad); l.draw(c); c.restore()
        }
    }
}

/**
 * Words being typed on page [page]: their top left at [x], [y] (page points), [room] across, at least [minHeight] tall;
 * [framed] a text box; [replacing] the text typed in again ([PdfObject.id]: taken out when these go on).
 */
internal data class PdfTyping(val page: Int, val x: Float, val y: Float, val room: Float, val minHeight: Float, val framed: Boolean, val replacing: Int? = null)

/** The words typed on the page itself, in their look as they will be put on ([page] the page's size in points). */
@Composable
internal fun BoxScope.PdfTypingField(page: androidx.compose.ui.geometry.Size, t: PdfTyping, state: com.mohamedrejeb.richeditor.model.RichTextState, look: PdfTextLook) =
    BoxWithConstraints(Modifier.matchParentSize()) {
        val k = maxWidth / page.width // a page point on the screen
        val pad = if (t.framed) 4f else 1f
        val focus = remember { FocusRequester() }
        LaunchedEffect(t) { focus.requestFocus() } // the keyboard at once
        // the selection blue on the white page whatever the app's theme (dark: Ctrl+A's selection hardly showed, 24 Oct)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.text.selection.LocalTextSelectionColors provides
            androidx.compose.foundation.text.selection.TextSelectionColors(handleColor = Color(0xFF1A73E8), backgroundColor = Color(0x661A73E8))) {
        // Words' editor: some words bold, coloured… as Words' letters (the text's own look under them)
        com.mohamedrejeb.richeditor.ui.BasicRichTextEditor(state, cursorBrush = SolidColor(Color.Black),
            modifier = Modifier.offset(k * t.x, k * t.y).width(k * t.room).heightIn(min = k * maxOf(t.minHeight, look.size * 1.2f + 2 * pad))
                // the words as they were hidden while typed again; the box's own fill
                .background(look.fill?.let { Color(0xFF000000 or it.toLong()) } ?: if (t.replacing != null) Color.White else Color.Transparent)
                .border(1.dp, look.line?.let { Color(0xFF000000 or it.toLong()) } ?: Color(0x991A73E8)).padding(k * pad).focusRequester(focus),
            textStyle = TextStyle(color = Color(0xFF000000 or look.color.toLong()), fontSize = with(LocalDensity.current) { (k * look.size).toSp() },
                fontFamily = OfficeFonts.family(look.font), fontWeight = if (look.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (look.italic) FontStyle.Italic else FontStyle.Normal, textDecoration = if (look.underline) TextDecoration.Underline else null,
                background = look.highlight?.let { Color(0xFF000000 or it.toLong()) } ?: Color.Unspecified,
                textAlign = when (look.align) { DocAlign.Center -> TextAlign.Center; DocAlign.End -> TextAlign.End; DocAlign.Justify -> TextAlign.Justify; else -> TextAlign.Start }))
        }
    }

/** Typing on the page: Words' Home tools for its look ([PdfLookTools]); × leaves it as it was, ✓ puts it on. */
@Composable
internal fun PdfTextTools(w: Color, look: PdfTextLook, set: (PdfTextLook) -> Unit, font: () -> Unit, cancel: () -> Unit, done: () -> Unit,
    words: com.mohamedrejeb.richeditor.model.RichTextState? = null) =
    RibbonLine(rememberScrollState(), Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
        Tool(R.drawable.ic_close, stringResource(R.string.pdf_leave_it), w, click = cancel)
        PdfLookTools(w, look, set, font, words)
        RibbonDivider(w)
        RibbonButton(stringResource(R.string.common_done), w, R.drawable.ic_check, click = done)
    }

/**
 * A text's look as a Slides box's Home tools give it: font, size, B I U, colour, highlight, the box's fill and border,
 * alignment. While typing ([words]) with words selected, B I U, colour and highlight go on those words only, as Words'.
 */
@Composable
internal fun PdfLookTools(w: Color, look: PdfTextLook, set: (PdfTextLook) -> Unit, font: () -> Unit, words: com.mohamedrejeb.richeditor.model.RichTextState? = null) {
    val sel = words?.takeIf { !it.selection.collapsed }
    val now = sel?.currentSpanStyle
    FontButton(look.font, w, click = font)
    val pt = Math.round(look.size)
    SizeStepper("$pt", w, pt > 4, pt < 200, { set(look.copy(size = (pt - 1).toFloat())) }, { set(look.copy(size = (pt + 1).toFloat())) })
    RibbonDivider(w)
    val bold = look.bold || (now?.fontWeight?.weight ?: 0) >= 600; val italic = look.italic || now?.fontStyle == FontStyle.Italic
    val under = look.underline || now?.textDecoration?.contains(TextDecoration.Underline) == true
    Tool(R.drawable.ic_format_bold, stringResource(R.string.words_bold), w, on = bold) { if (sel != null) sel.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) else set(look.copy(bold = !look.bold)) }
    Tool(R.drawable.ic_format_italic, stringResource(R.string.words_italic), w, on = italic) { if (sel != null) sel.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)) else set(look.copy(italic = !look.italic)) }
    Tool(R.drawable.ic_format_underlined, stringResource(R.string.words_underline), w, on = under) { if (sel != null) sel.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(textDecoration = TextDecoration.Underline)) else set(look.copy(underline = !look.underline)) }
    Swatches(w, R.drawable.ic_format_color_text, stringResource(R.string.words_text_colour), TEXT_COLOURS) { c ->
        if (sel != null) { sel.removeSpanStyle(androidx.compose.ui.text.SpanStyle(color = sel.currentSpanStyle.color)); if (c != null) sel.addSpanStyle(androidx.compose.ui.text.SpanStyle(color = c)) }
        else set(look.copy(color = (c ?: Color.Black).toArgb() and 0xFFFFFF)) }
    Swatches(w, R.drawable.ic_highlighter, stringResource(R.string.words_highlight), HIGHLIGHT_COLOURS) { c ->
        if (sel != null) { sel.removeSpanStyle(androidx.compose.ui.text.SpanStyle(background = sel.currentSpanStyle.background)); if (c != null) sel.addSpanStyle(androidx.compose.ui.text.SpanStyle(background = c)) }
        else set(look.copy(highlight = c?.let { it.toArgb() and 0xFFFFFF })) }
    Swatches(w, R.drawable.ic_format_color_fill, stringResource(R.string.words_fill), TEXT_COLOURS) { c -> set(look.copy(fill = c?.let { it.toArgb() and 0xFFFFFF })) }
    Swatches(w, R.drawable.ic_border_outer, stringResource(R.string.pdf_border), TEXT_COLOURS) { c -> set(look.copy(line = c?.let { it.toArgb() and 0xFFFFFF })) }
    RibbonDivider(w)
    listOf(Triple(DocAlign.Start, R.drawable.ic_align_left, R.string.words_align_left), Triple(DocAlign.Center, R.drawable.ic_align_center, R.string.words_align_center),
        Triple(DocAlign.End, R.drawable.ic_align_right, R.string.words_align_right), Triple(DocAlign.Justify, R.drawable.ic_align_justify, R.string.words_align_justify)).forEach { (a, icon, label) ->
        Tool(icon, stringResource(label), w, on = look.align == a) { set(look.copy(align = a)) }
    }
}

/** Where a chosen text is being dragged, sized or turned to (page points, degrees) until the finger lifts: drawn, then put on. */
internal class PdfFramePreview {
    var shift by androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    var grow by androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    var spin by androidx.compose.runtime.mutableStateOf<Float?>(null)
    fun reset() { shift = androidx.compose.ui.geometry.Offset.Zero; grow = androidx.compose.ui.geometry.Offset.Zero; spin = null }
}

/** Words' blue for what is chosen (its outline and [ObjectHandles]). */
private val CHOSEN_BLUE = Color(0xFF1A73E8)

/**
 * The chosen text as Words shows a chosen text box (the user, 25 Oct: "everything as in Words"): its outline in Words'
 * blue, turned with its words, and Words' own handles ([ObjectHandles]) — the corner and sides stretch it, the knob
 * on its stem turns it. [r] its place on the page and [laid] its size before turning (page points), [turn] its turn;
 * [preview] where a finger has it. [sized] gets its new size before turning, [turned] its new turn.
 */
@Composable
internal fun BoxScope.PdfChosenHandles(page: androidx.compose.ui.geometry.Size, r: android.graphics.RectF, laid: Pair<Float, Float>, turn: Float, preview: PdfFramePreview,
    rotatable: Boolean, keepAspect: Boolean, sized: (Float, Float) -> Unit, turned: (Float) -> Unit) = BoxWithConstraints(Modifier.matchParentSize()) {
    val k = constraints.maxWidth / page.width // pixels a page point
    val (lw0, lh0) = laid
    val lw = maxOf(12f, lw0 + preview.grow.x); val lh = maxOf(8f, lh0 + preview.grow.y)
    // stretched from its top left as Words', moved with the finger
    val left = (r.centerX() + preview.shift.x - lw0 / 2) * k; val top = (r.centerY() + preview.shift.y - lh0 / 2) * k
    val angle = preview.spin ?: turn
    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
        rotate(angle, androidx.compose.ui.geometry.Offset(left + lw * k / 2, top + lh * k / 2)) {
            drawRect(CHOSEN_BLUE, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(lw * k, lh * k), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())) }
    }
    if (preview.shift == androidx.compose.ui.geometry.Offset.Zero) // as Words', none while it is dragged
        ObjectHandles(left, top, lw * k, lh * k, angle, rotatable = rotatable, keepAspect = keepAspect,
            size = { a, b -> preview.grow = androidx.compose.ui.geometry.Offset(a / k - lw0, b / k - lh0) },
            sized = { val g = preview.grow; preview.reset(); if (g != androidx.compose.ui.geometry.Offset.Zero) sized(lw0 + g.x, lh0 + g.y) },
            turn = { preview.spin = it }, turned = { val a = preview.spin; preview.reset(); a?.let { turned(it) } })
}

/**
 * An object on the page under a finger or the mouse, as Words' (the user, 25–26 Oct: "when I press it with the mouse or
 * the finger, it is to move it"): pressed and dragged it moves at once — chosen if it was not ([choose]) — shown in
 * [preview] and [moved] when let go; a tap chooses it, a tap on a chosen text types in it ([edit]); held still it is
 * chosen and Position opens ([long], Words' long press). [objectAt] the object under a point (page points); [chosen]
 * the chosen one, whose handles keep their own touches. Elsewhere the page's own.
 */
internal fun Modifier.pdfObjectGestures(page: androidx.compose.ui.geometry.Size, on: Boolean, objectAt: (androidx.compose.ui.geometry.Offset) -> PdfObject?,
    chosen: () -> PdfObject?, preview: PdfFramePreview, choose: (PdfObject) -> Unit, edit: () -> Unit, long: () -> Unit,
    moved: (Float, Float) -> Unit): Modifier = if (!on) this else pointerInput(page) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        if (currentEvent.buttons.isSecondaryPressed) return@awaitEachGesture // a mouse's right button: the menu's
        val k = size.width / page.width // pixels a page point
        val p = down.position / k
        val c = chosen()
        // on one of Words' handles of the chosen one: theirs ([ObjectHandles]: 32 dp grips, the knob 36 dp over the top)
        if (c != null) {
            val (lw, lh) = c.laid; val mid = androidx.compose.ui.geometry.Offset(c.rect.centerX() * k, c.rect.centerY() * k)
            val w = lw * k; val h = lh * k
            val grips = listOf(androidx.compose.ui.geometry.Offset(w / 2, h / 2), androidx.compose.ui.geometry.Offset(w / 2, 0f), androidx.compose.ui.geometry.Offset(0f, h / 2),
                androidx.compose.ui.geometry.Offset(0f, -h / 2 - 36.dp.toPx())).map { mid + turnedBy(it, c.turn) }
            if (grips.any { (it - down.position).getDistance() < 16.dp.toPx() }) return@awaitEachGesture
        }
        val onChosen = c?.rect?.let { r -> p.x in r.left..r.right && p.y in r.top..r.bottom } == true
        val under = if (onChosen) c else objectAt(p)
        if (under == null) return@awaitEachGesture
        down.consume()
        var going = false; var lifted = false
        // still until the finger moves, lifts or has been held a long press's time
        val decided = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ch = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                if (ch == null || !ch.pressed) { ch?.consume(); lifted = true; return@withTimeoutOrNull true }
                ch.consume()
                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) { going = true; return@withTimeoutOrNull true }
            }
            @Suppress("UNREACHABLE_CODE") true
        }
        if (decided == null) { // held: chosen, and Position (Words')
            if (!onChosen) choose(under)
            long()
            while (true) { val ch = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break; ch.consume(); if (!ch.pressed) break }
            return@awaitEachGesture
        }
        if (lifted) { if (onChosen) edit() else choose(under); return@awaitEachGesture } // a tap chooses it; on a chosen text, its words are typed in
        if (!onChosen) choose(under)
        while (true) {
            val ch = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            ch.consume()
            preview.shift = ch.position / k - p
            if (!ch.pressed) break
        }
        val sh = preview.shift; preview.reset(); if (going) moved(sh.x, sh.y)
    }
}

/**
 * An arrow that nudges the chosen text (the user, 25 Oct): a tap a point that way, held it goes on, faster the longer;
 * [step] where it has got to (shown, not put on), [done] when the finger lifts (one step of Undo).
 */
@Composable
internal fun NudgeTool(icon: Int, turn: Float, label: String, w: Color, step: (Float) -> Unit, done: () -> Unit) =
    androidx.compose.foundation.layout.Box(Modifier.size(44.dp).pointerInput(Unit) {
        coroutineScope {
            detectTapGestures(onPress = {
                step(1f)
                val held = launch { kotlinx.coroutines.delay(400); var n = 0; while (true) { step(if (n < 10) 1f else if (n < 30) 3f else 6f); n++; kotlinx.coroutines.delay(70) } }
                tryAwaitRelease(); held.cancel(); done()
            })
        }
    }, contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(icon), label, tint = w, modifier = Modifier.size(22.dp).then(Modifier.graphicsLayer { rotationZ = turn }))
    }

/** Layout › Size's papers (points), as Word's and Tables' Size. */
internal object PdfPaper {
    val all = listOf("A4" to Size(595f, 842f), "A5" to Size(420f, 595f), "A3" to Size(842f, 1191f), "Letter" to Size(612f, 792f), "Legal" to Size(612f, 1008f))
    /** [s]'s paper ("A4"), either way round; null when it is none of them. */
    fun paper(s: Size): String? {
        val (w, h) = minOf(s.width, s.height) to maxOf(s.width, s.height)
        return all.firstOrNull { (_, p) -> kotlin.math.abs(p.width - w) < 3 && kotlin.math.abs(p.height - h) < 3 }?.first
    }
    /** [s]'s paper and way round ("A4 portrait", in [way]'s words: the paper, landscape), else its size in [unit]. */
    fun name(s: Size, way: (String, Boolean) -> String = { p, land -> "$p ${if (land) "landscape" else "portrait"}" }, unit: String = WriterUnits.name): String =
        paper(s)?.let { way(it, s.width > s.height) } ?: "${WriterUnits.text(s.width)} × ${WriterUnits.text(s.height)} $unit"
}

/** Layout › Margins (Word's presets), the same for every PDF: where Position puts a text and where a new one starts. */
internal object PdfMargins {
    /** name → top/bottom, left/right (points) */
    val all = listOf("Normal" to (72f to 72f), "Narrow" to (36f to 36f), "Moderate" to (72f to 54f), "Wide" to (72f to 144f))
    /** Their words (the names above are what is kept in the settings). */
    val labels = mapOf("Normal" to R.string.pdf_normal, "Narrow" to R.string.words_margins_narrow, "Moderate" to R.string.words_margins_moderate, "Wide" to R.string.words_margins_wide)
    fun page(name: String) = (all.firstOrNull { it.first == name } ?: all[0]).second.let { (tb, lr) -> DocPage(top = tb, bottom = tb, left = lr, right = lr) }
    fun nameOf(m: DocPage) = all.firstOrNull { (_, v) -> v.first == m.top && v.second == m.left }?.first
    fun load(context: android.content.Context) = page(context.getSharedPreferences("office_editor", android.content.Context.MODE_PRIVATE).getString("pdf_margins", "Normal") ?: "Normal")
    fun save(context: android.content.Context, name: String) = context.getSharedPreferences("office_editor", android.content.Context.MODE_PRIVATE).edit().putString("pdf_margins", name).apply()
}
