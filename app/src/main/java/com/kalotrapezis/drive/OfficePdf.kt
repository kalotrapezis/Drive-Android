package com.kalotrapezis.drive

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.text.style.AbsoluteSizeSpan
import android.text.style.ImageSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import java.io.OutputStream

/**
 * Word's Export › PDF: the document as Writer holds it, on A4 pages with 2 cm margins. Laid out by Android's own text
 * layout — what Writer shows, not what Word would print (the document's own fonts and spacing are not read yet).
 */
internal object OfficePdf {
    private const val PAGE_W = 595 // A4 in points; the PDF canvas counts in points
    private const val PAGE_H = 842
    private const val MARGIN = 57 // 2 cm
    private val HEADING_SCALE = floatArrayOf(1f, 1.8f, 1.5f, 1.3f, 1.15f, 1.05f, 1f)

    fun write(doc: OfficeDoc, out: OutputStream, pictureBytes: (DocImage) -> ByteArray? = { null }) {
        val width = PAGE_W - 2 * MARGIN
        val pdf = PdfDocument()
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { pdf.finishPage(it) }
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNumber).create())
            y = MARGIN.toFloat()
        }
        newPage()
        val counters = IntArray(9)
        doc.paras.forEachIndexed { i, p ->
            // Numbers as the editor shows them: a list counts on from its start, a deeper level restarts.
            val list = p.list
            if (list == null) counters.fill(0) else {
                if (i == 0 || doc.paras[i - 1].list == null) { counters.fill(0); counters[0] = (list.start ?: 1) - 1 }
                list.start?.takeIf { list.level > 0 && counters[list.level] == 0 }?.let { counters[list.level] = it - 1 }
                counters[list.level]++
                for (d in list.level + 1 until 9) counters[d] = 0
            }
            val layout = layout(doc, p, pictureBytes, if (list == null) null else if (list.ordered) "${counters[list.level]}." else listOf("•", "◦", "▪")[list.level % 3], width)
            val space = doc.basePt * if (p.heading > 0) 0.9f else 0.5f
            for (line in 0 until layout.lineCount) {
                val top = layout.getLineTop(line)
                val bottom = layout.getLineBottom(line)
                if (y + (bottom - top) > PAGE_H - MARGIN) newPage()
                val canvas = page!!.canvas
                canvas.save()
                canvas.translate(MARGIN.toFloat(), y - top)
                canvas.clipRect(0, top, width, bottom)
                layout.draw(canvas)
                canvas.restore()
                y += bottom - top
            }
            y += space
        }
        page?.let { pdf.finishPage(it) }
        pdf.writeTo(out)
        pdf.close()
    }

    private fun layout(doc: OfficeDoc, p: DocPara, pictureBytes: (DocImage) -> ByteArray?, marker: String?, width: Int): StaticLayout {
        val text = SpannableStringBuilder()
        val scale = HEADING_SCALE[p.heading.coerceIn(0, 6)]
        val indent = (p.list?.level ?: 0) * 18 + if (p.list != null) 18 else 0
        if (marker != null) text.append("$marker ")
        for (r in OfficeFormats.tidy(p.runs)) {
            val start = text.length
            text.append(r.text.replace(LINE_BREAK, '\n'))
            val end = text.length
            fun span(s: Any) = text.setSpan(s, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            // A picture at its size in points (the PDF counts in points), never wider than the text; a chart or shape, a grey box.
            r.image?.let { img ->
                val fit = minOf(width.toFloat() / img.widthPt, (PAGE_H - 2 * MARGIN - 40f) / img.heightPt, 1f)
                val w = (img.widthPt * fit).toInt().coerceAtLeast(1); val h = (img.heightPt * fit).toInt().coerceAtLeast(1)
                val drawable = pictureBytes(img)?.let { b -> decodePicture(b, 1600) }?.let { BitmapDrawable(null, it) } ?: ColorDrawable(0xFFE3E3E3.toInt())
                drawable.setBounds(0, 0, w, h)
                span(ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM))
                continue
            }
            when {
                r.bold && r.italic -> span(StyleSpan(Typeface.BOLD_ITALIC))
                r.bold -> span(StyleSpan(Typeface.BOLD))
                r.italic -> span(StyleSpan(Typeface.ITALIC))
            }
            if (r.underline || r.link != null) span(UnderlineSpan())
            if (r.strike) span(StrikethroughSpan())
            r.sizePt?.let { span(AbsoluteSizeSpan((it * scale).toInt())) }
            (r.color ?: if (r.link != null) 0x0563C1 else null)?.let { span(ForegroundColorSpan(0xFF000000.toInt() or it)) }
            r.highlight?.let { span(BackgroundColorSpan(0xFF000000.toInt() or it)) }
        }
        if (indent > 0) text.setSpan(LeadingMarginSpan.Standard(indent - 12, indent), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = doc.basePt * scale
            if (p.heading > 0) typeface = Typeface.DEFAULT_BOLD
        }
        val align = when (p.align) { DocAlign.Center -> Layout.Alignment.ALIGN_CENTER; DocAlign.End -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(align).setLineSpacing(0f, 1.15f)
            .setJustificationMode(if (p.align == DocAlign.Justify) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE)
            .build()
    }
}
