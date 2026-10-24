package com.kalotrapezis.drive

import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.input.TextFieldValue
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
    /** the box's fill and its border's colour (0xRRGGBB), as a Slides box's; none: clear, no border */ val fill: Int? = null, val line: Int? = null)

/** A text put on a page: its words, their look, and whether it is a text box (a thin frame, as wide as it was drawn). */
internal data class PdfText(val words: String, val look: PdfTextLook, val framed: Boolean) {
    /** Kept in the file with it (the annotation's own key), so a tap opens it again as it was typed. */
    fun json(): String = JSONObject().put("words", words).put("font", look.font).put("size", look.size.toDouble()).put("bold", look.bold).put("italic", look.italic)
        .put("underline", look.underline).put("color", look.color).put("highlight", look.highlight ?: -1).put("align", look.align.name).put("framed", framed)
        .put("fill", look.fill ?: -1).put("line", look.line ?: -1).toString()

    /** Inside its frame, in points: a text box's words off its frame, a text's hugging them. */
    val pad get() = if (framed) 4f else 1f

    companion object {
        fun parse(s: String): PdfText? = runCatching {
            val o = JSONObject(s)
            PdfText(o.getString("words"), PdfTextLook(o.optString("font", "Times New Roman"), o.optDouble("size", 12.0).toFloat(), o.optBoolean("bold"), o.optBoolean("italic"),
                o.optBoolean("underline"), o.optInt("color", 0), o.optInt("highlight", -1).takeIf { it >= 0 }, runCatching { DocAlign.valueOf(o.optString("align")) }.getOrDefault(DocAlign.Start),
                o.optInt("fill", -1).takeIf { it >= 0 }, o.optInt("line", if (o.optBoolean("framed")) 0 else -1).takeIf { it >= 0 }), // a box put on before borders were chosen: its black frame
                o.optBoolean("framed"))
        }.getOrNull()

        private fun layout(t: PdfText, width: Float): StaticLayout {
            val l = t.look
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = l.size; color = 0xFF000000.toInt() or l.color; isUnderlineText = l.underline
                fontFeatureSettings = "'liga' 0, 'clig' 0" // a joined pair ("ll" in a script font) copied and found as nothing in the PDF
                typeface = Typeface.create(OfficeFonts.typeface(l.font) ?: Typeface.DEFAULT, if (l.bold) 700 else 400, l.italic)
            }
            val s = SpannableString(t.words)
            l.highlight?.let { s.setSpan(BackgroundColorSpan(0xFF000000.toInt() or it), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
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

        /** The words drawn [w] × [h] points: a one-page PDF of their own (Android's, its fonts embedded), to be the annotation's look. */
        fun pdf(t: PdfText, w: Float, h: Float): ByteArray {
            val doc = android.graphics.pdf.PdfDocument()
            try {
                val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(ceil(w).toInt(), ceil(h).toInt(), 1).create())
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
internal fun BoxScope.PdfTypingField(page: androidx.compose.ui.geometry.Size, t: PdfTyping, value: TextFieldValue, look: PdfTextLook, change: (TextFieldValue) -> Unit) =
    BoxWithConstraints(Modifier.matchParentSize()) {
        val k = maxWidth / page.width // a page point on the screen
        val pad = if (t.framed) 4f else 1f
        val focus = remember { FocusRequester() }
        LaunchedEffect(t) { focus.requestFocus() } // the keyboard at once
        // the selection blue on the white page whatever the app's theme (dark: Ctrl+A's selection hardly showed, 24 Oct)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.text.selection.LocalTextSelectionColors provides
            androidx.compose.foundation.text.selection.TextSelectionColors(handleColor = Color(0xFF1A73E8), backgroundColor = Color(0x661A73E8))) {
        BasicTextField(value, change, cursorBrush = SolidColor(Color.Black),
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
internal fun PdfTextTools(w: Color, look: PdfTextLook, set: (PdfTextLook) -> Unit, font: () -> Unit, cancel: () -> Unit, done: () -> Unit) =
    RibbonLine(rememberScrollState(), Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
        Tool(R.drawable.ic_close, "Leave it as it was", w, click = cancel)
        PdfLookTools(w, look, set, font)
        RibbonDivider(w)
        RibbonButton("Done", w, R.drawable.ic_check, click = done)
    }

/** A text's look as a Slides box's Home tools give it: font, size, B I U, colour, highlight, the box's fill and border, alignment. */
@Composable
internal fun PdfLookTools(w: Color, look: PdfTextLook, set: (PdfTextLook) -> Unit, font: () -> Unit) {
    FontButton(look.font, w, click = font)
    val pt = Math.round(look.size)
    SizeStepper("$pt", w, pt > 4, pt < 200, { set(look.copy(size = (pt - 1).toFloat())) }, { set(look.copy(size = (pt + 1).toFloat())) })
    RibbonDivider(w)
    Tool(R.drawable.ic_format_bold, "Bold", w, on = look.bold) { set(look.copy(bold = !look.bold)) }
    Tool(R.drawable.ic_format_italic, "Italic", w, on = look.italic) { set(look.copy(italic = !look.italic)) }
    Tool(R.drawable.ic_format_underlined, "Underline", w, on = look.underline) { set(look.copy(underline = !look.underline)) }
    Swatches(w, R.drawable.ic_format_color_text, "Text colour", TEXT_COLOURS) { c -> set(look.copy(color = (c ?: Color.Black).toArgb() and 0xFFFFFF)) }
    Swatches(w, R.drawable.ic_highlighter, "Highlight", HIGHLIGHT_COLOURS) { c -> set(look.copy(highlight = c?.let { it.toArgb() and 0xFFFFFF })) }
    Swatches(w, R.drawable.ic_format_color_fill, "Fill", TEXT_COLOURS) { c -> set(look.copy(fill = c?.let { it.toArgb() and 0xFFFFFF })) }
    Swatches(w, R.drawable.ic_border_outer, "Border", TEXT_COLOURS) { c -> set(look.copy(line = c?.let { it.toArgb() and 0xFFFFFF })) }
    RibbonDivider(w)
    listOf(DocAlign.Start to R.drawable.ic_align_left, DocAlign.Center to R.drawable.ic_align_center, DocAlign.End to R.drawable.ic_align_right, DocAlign.Justify to R.drawable.ic_align_justify).forEach { (a, icon) ->
        Tool(icon, "Align ${a.name.lowercase().replace("start", "left").replace("end", "right")}", w, on = look.align == a) { set(look.copy(align = a)) }
    }
}

/**
 * A chosen text ([r], page points, on a page [page] points big): its frame, moved by a finger dragging it, sized by its
 * corner's handle (as a Slides box's); a tap on it types in it. [moved] and [sized] get where it went when the finger lifts.
 */
@Composable
internal fun BoxScope.PdfChosenFrame(page: androidx.compose.ui.geometry.Size, r: android.graphics.RectF, type: () -> Unit, moved: (Float, Float) -> Unit, sized: (Float, Float) -> Unit) =
    BoxWithConstraints(Modifier.matchParentSize()) {
        val k = maxWidth / page.width
        val px = with(LocalDensity.current) { k.toPx() } // screen pixels a page point
        var shift by remember(r) { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        var grow by remember(r) { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        val blue = Color(0xFF1A73E8)
        androidx.compose.foundation.layout.Box(Modifier.offset(k * (r.left + shift.x / px), k * (r.top + shift.y / px))
            .width(k * maxOf(12f, r.width() + grow.x / px)).heightIn(min = k * maxOf(8f, r.height() + grow.y / px))
            .border(2.dp, blue)
            .pointerInput(r) { detectTapGestures { type() } }
            .pointerInput(r) { detectDragGestures(onDragEnd = { if (shift != androidx.compose.ui.geometry.Offset.Zero) moved(shift.x / px, shift.y / px) }) { ch, d -> ch.consume(); shift += d } }) {
            // the corner's handle: the size
            androidx.compose.foundation.layout.Box(Modifier.align(androidx.compose.ui.Alignment.BottomEnd).offset(10.dp, 10.dp).size(20.dp)
                .background(Color.White, androidx.compose.foundation.shape.CircleShape).border(2.dp, blue, androidx.compose.foundation.shape.CircleShape)
                .pointerInput(r) { detectDragGestures(onDragEnd = { if (grow != androidx.compose.ui.geometry.Offset.Zero) sized(r.width() + grow.x / px, r.height() + grow.y / px) }) { ch, d -> ch.consume(); grow += d } })
        }
    }
