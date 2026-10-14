package com.kalotrapezis.drive

/**
 * WordArt's text effects (Phase 2): an outline round the letters ([outline], its colour), a shadow below and right of
 * them, a glow round them ([glow], its colour). Word keeps them in the run (w14:glow, w14:shadow, w14:textOutline — Word
 * 2010's); LibreOffice has an outline (hollow letters, in their own colour) and a shadow, no glow.
 */
internal data class TextEffects(val outline: Int? = null, val shadow: Boolean = false, val glow: Int? = null) {
    val any get() = outline != null || shadow || glow != null

    /** Word's run properties for them, in the order Word wants (glow, shadow, outline), each saying its namespace. */
    fun docx(): String = buildString {
        fun c(rgb: Int) = "%06X".format(rgb and 0xFFFFFF)
        glow?.let { append("""<w14:glow xmlns:w14="$W14" w14:rad="101600"><w14:srgbClr w14:val="${c(it)}"><w14:alpha w14:val="40000"/></w14:srgbClr></w14:glow>""") }
        if (shadow) append("""<w14:shadow xmlns:w14="$W14" w14:blurRad="38100" w14:dist="38100" w14:dir="2700000" w14:sx="100000" w14:sy="100000" w14:kx="0" w14:ky="0" w14:algn="tl"><w14:srgbClr w14:val="000000"><w14:alpha w14:val="60000"/></w14:srgbClr></w14:shadow>""")
        outline?.let { append("""<w14:textOutline xmlns:w14="$W14" w14:w="12700" w14:cap="flat" w14:cmpd="sng" w14:algn="ctr"><w14:solidFill><w14:srgbClr w14:val="${c(it)}"/></w14:solidFill><w14:prstDash w14:val="solid"/><w14:round/></w14:textOutline>""") }
    }
    /** LibreOffice's text properties for them (the glow has none). */
    fun odt(): String = (if (outline != null) " style:text-outline=\"true\"" else "") + (if (shadow) " fo:text-shadow=\"1pt 1pt\"" else "")
    /** A name part for Writer's own WordArt style in an .odt (Tetra_20_art_RRGGBB…): what makes the style. */
    fun odtName() = (if (outline != null) "_o" else "") + (if (shadow) "_s" else "")

    companion object {
        const val W14 = "http://schemas.microsoft.com/office/word/2010/wordml"
        private fun color(xml: String) = Regex("""<w14:srgbClr\s+w14:val="([0-9A-Fa-f]{6})"""").find(xml)?.groupValues?.get(1)?.toInt(16)
        /** The effects a run's own settings hold (Word's w14 elements, or LibreOffice's attributes). */
        fun of(font: String?): TextEffects {
            if (font.isNullOrEmpty()) return TextEffects()
            val outline = Regex("""<w14:textOutline\b[\s\S]*?</w14:textOutline>""").find(font)?.value?.takeIf { "<w14:noFill" !in it }?.let { color(it) ?: 0 }
                ?: if ("style:text-outline=\"true\"" in font) 0 else null
            val shadow = Regex("""<w14:shadow\b""").containsMatchIn(font) || Regex("""fo:text-shadow="(?!none)[^"]+"""").containsMatchIn(font)
            val glow = Regex("""<w14:glow\b[\s\S]*?</w14:glow>""").find(font)?.value?.let { color(it) ?: 0xFFC000 }
            return TextEffects(outline, shadow, glow)
        }
    }
}

/**
 * Letters drawn with their [effects] (the pages, a text box in the editor): the glow and the shadow under them, their
 * [fill] colour, the outline over it. Put on each word, so lines still break between words. [unit]: the layout's units
 * per point.
 */
internal class EffectSpan(val effects: TextEffects, val fill: Int, val unit: Float) : android.text.style.ReplacementSpan() {
    override fun getSize(paint: android.graphics.Paint, text: CharSequence, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?): Int {
        fm?.let { paint.getFontMetricsInt(it) }
        return Math.round(paint.measureText(text, start, end))
    }
    override fun draw(canvas: android.graphics.Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: android.graphics.Paint) {
        val p = android.text.TextPaint(paint)
        val s = text.subSequence(start, end).toString()
        val size = p.textSize
        p.style = android.graphics.Paint.Style.FILL; p.color = fill
        effects.glow?.let { g -> p.setShadowLayer(size * 0.12f, 0f, 0f, 0xCC000000.toInt() or g); canvas.drawText(s, x, y.toFloat(), p); canvas.drawText(s, x, y.toFloat(), p) }
        if (effects.shadow) { p.setShadowLayer(size * 0.04f, size * 0.05f, size * 0.05f, 0x99000000.toInt()); canvas.drawText(s, x, y.toFloat(), p) }
        p.clearShadowLayer()
        canvas.drawText(s, x, y.toFloat(), p)
        effects.outline?.let { o ->
            p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = maxOf(unit * 1f, size * 0.025f); p.strokeJoin = android.graphics.Paint.Join.ROUND
            p.color = 0xFF000000.toInt() or o
            canvas.drawText(s, x, y.toFloat(), p)
        }
    }
}
