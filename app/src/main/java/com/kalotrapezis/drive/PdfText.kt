package com.kalotrapezis.drive

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
 * [framed] a text box; [replacing] a point on the text typed in again (taken out when these go on).
 */
internal data class PdfTyping(val page: Int, val x: Float, val y: Float, val room: Float, val minHeight: Float, val framed: Boolean, val replacing: androidx.compose.ui.geometry.Offset? = null)

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
        Tool(R.drawable.ic_close, "Leave it as it was", w, click = cancel)
        PdfLookTools(w, look, set, font, words)
        RibbonDivider(w)
        RibbonButton("Done", w, R.drawable.ic_check, click = done)
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
    Tool(R.drawable.ic_format_bold, "Bold", w, on = bold) { if (sel != null) sel.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) else set(look.copy(bold = !look.bold)) }
    Tool(R.drawable.ic_format_italic, "Italic", w, on = italic) { if (sel != null) sel.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)) else set(look.copy(italic = !look.italic)) }
    Tool(R.drawable.ic_format_underlined, "Underline", w, on = under) { if (sel != null) sel.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(textDecoration = TextDecoration.Underline)) else set(look.copy(underline = !look.underline)) }
    Swatches(w, R.drawable.ic_format_color_text, "Text colour", TEXT_COLOURS) { c ->
        if (sel != null) { sel.removeSpanStyle(androidx.compose.ui.text.SpanStyle(color = sel.currentSpanStyle.color)); if (c != null) sel.addSpanStyle(androidx.compose.ui.text.SpanStyle(color = c)) }
        else set(look.copy(color = (c ?: Color.Black).toArgb() and 0xFFFFFF)) }
    Swatches(w, R.drawable.ic_highlighter, "Highlight", HIGHLIGHT_COLOURS) { c ->
        if (sel != null) { sel.removeSpanStyle(androidx.compose.ui.text.SpanStyle(background = sel.currentSpanStyle.background)); if (c != null) sel.addSpanStyle(androidx.compose.ui.text.SpanStyle(background = c)) }
        else set(look.copy(highlight = c?.let { it.toArgb() and 0xFFFFFF })) }
    Swatches(w, R.drawable.ic_format_color_fill, "Fill", TEXT_COLOURS) { c -> set(look.copy(fill = c?.let { it.toArgb() and 0xFFFFFF })) }
    Swatches(w, R.drawable.ic_border_outer, "Border", TEXT_COLOURS) { c -> set(look.copy(line = c?.let { it.toArgb() and 0xFFFFFF })) }
    RibbonDivider(w)
    listOf(DocAlign.Start to R.drawable.ic_align_left, DocAlign.Center to R.drawable.ic_align_center, DocAlign.End to R.drawable.ic_align_right, DocAlign.Justify to R.drawable.ic_align_justify).forEach { (a, icon) ->
        Tool(icon, "Align ${a.name.lowercase().replace("start", "left").replace("end", "right")}", w, on = look.align == a) { set(look.copy(align = a)) }
    }
}

/**
 * A chosen text ([r], page points, on a page [page] points big; [laid] its size before turning, [turn] its turn): its
 * frame, moved by a finger dragging it, sized by its corner's handle, turned by the handle over it (as a Slides box's);
 * a tap on it types in it. [moved], [turned] (to degrees, near a quarter turn: on it) and [sized] when the finger lifts.
 */
@Composable
internal fun BoxScope.PdfChosenFrame(page: androidx.compose.ui.geometry.Size, r: android.graphics.RectF, laid: Pair<Float, Float>, turn: Int, type: () -> Unit,
    moved: (Float, Float) -> Unit, turned: (Int) -> Unit, sized: (Float, Float) -> Unit) =
    BoxWithConstraints(Modifier.matchParentSize()) {
        val k = maxWidth / page.width
        val density = LocalDensity.current
        val px = with(density) { k.toPx() } // screen pixels a page point
        var shift by remember(r) { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        var grow by remember(r) { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        var spin by remember(r) { androidx.compose.runtime.mutableStateOf<Float?>(null) } // the turn while the handle is dragged
        val blue = Color(0xFF1A73E8)
        // while turning: the words' own box at the new angle, round the same middle
        spin?.let { a -> androidx.compose.foundation.layout.Box(Modifier.offset(k * (r.centerX() - laid.first / 2), k * (r.centerY() - laid.second / 2))
            .width(k * laid.first).heightIn(min = k * laid.second).graphicsLayer { rotationZ = a }.border(1.dp, blue.copy(alpha = 0.7f))) }
        val w = k * maxOf(12f, r.width() + grow.x / px); val h = k * maxOf(8f, r.height() + grow.y / px)
        val left = k * (r.left + shift.x / px); val top = k * (r.top + shift.y / px)
        androidx.compose.foundation.layout.Box(Modifier.offset(left, top).width(w).height(h).border(2.dp, blue)
            .pointerInput(r) { detectTapGestures { type() } }
            .pointerInput(r) { detectDragGestures(onDragEnd = { if (shift != androidx.compose.ui.geometry.Offset.Zero) moved(shift.x / px, shift.y / px) }) { ch, d -> ch.consume(); shift += d } })
        // the handles beside it, not in it (a child past its parent's edge gets no touches)
        @Composable fun handle(m: Modifier) = androidx.compose.foundation.layout.Box(m.size(22.dp)
            .background(Color.White, androidx.compose.foundation.shape.CircleShape).border(2.dp, blue, androidx.compose.foundation.shape.CircleShape))
        // the corner's: the size
        handle(Modifier.offset(left + w - 11.dp, top + h - 11.dp)
            .pointerInput(r) { detectDragGestures(onDragEnd = { if (grow != androidx.compose.ui.geometry.Offset.Zero) sized(r.width() + grow.x / px, r.height() + grow.y / px) }) { ch, d -> ch.consume(); grow += d } })
        // the one over it (under it at the page's top, where over it is off the page): the turn, as far as the finger
        // goes round the box's middle from where it took hold
        val lift = 30.dp
        val below = top < lift + 22.dp
        handle(Modifier.offset(left + w / 2 - 11.dp, if (below) top + h + lift - 11.dp else top - lift - 11.dp)
            .pointerInput(r, turn) {
                val half = size.width / 2f; val reach = lift.toPx() + r.height() * px / 2
                val middle = androidx.compose.ui.geometry.Offset(half, if (below) half - reach else half + reach)
                var at = androidx.compose.ui.geometry.Offset(half, half); var from = 0f
                fun round() = Math.toDegrees(kotlin.math.atan2((at.x - middle.x).toDouble(), (middle.y - at.y).toDouble())).toFloat()
                fun angle() = turn + round() - from
                detectDragGestures(onDragStart = { at = it; from = round(); spin = angle() },
                    onDragEnd = { val a = Math.round(angle()); val q = Math.round(a / 90f) * 90; spin = null; turned(if (kotlin.math.abs(a - q) <= 4) q else a) },
                    onDragCancel = { spin = null }) { ch, d -> ch.consume(); at += d; spin = angle() }
            })
    }
