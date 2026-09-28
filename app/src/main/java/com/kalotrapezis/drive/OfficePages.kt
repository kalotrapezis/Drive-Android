package com.kalotrapezis.drive

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.text.style.SubscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.text.style.RelativeSizeSpan
import org.w3c.dom.Element

/*
 * Proper pages (asked 2026-09-27): the document laid out on its own pages — the file's page size and margins, each
 * paragraph's spacing, line spacing and indents as its style and its own settings say — for the page view and for
 * Export › PDF alike. Laid out by Android's text layout, in points (1 unit = 1 pt).
 */

/** A page: its size and margins in points. */
internal data class DocPage(val width: Float = 595.3f, val height: Float = 841.9f, val top: Float = 56.7f, val bottom: Float = 56.7f,
    val left: Float = 56.7f, val right: Float = 56.7f) {
    val textWidth get() = (width - left - right).coerceAtLeast(72f)
}

/** How a paragraph is laid out: spacing (pt), line spacing (a multiple, or exact pt), indents (pt), its text's look. */
internal data class ParaLook(
    val before: Float = 0f, val after: Float = 0f,
    val lineMultiple: Float = 1f, val lineExact: Float? = null, val lineAtLeast: Boolean = false,
    val left: Float = 0f, val right: Float = 0f, val firstLine: Float = 0f, // firstLine < 0: hanging
    val pageBreakBefore: Boolean = false, val contextual: Boolean = false,
    val sizePt: Float? = null, val bold: Boolean? = null, val italic: Boolean? = null, val color: Int? = null, val font: String? = null,
    val tabs: List<TabStop> = emptyList(),
    val listIndented: Boolean = false, // a list item's indent is already in left/firstLine
)

/** A tab stop: where (pt from the text's left edge), how the text after it lines up ('l', 'r', 'c'), what fills the gap. */
internal data class TabStop(val pos: Float, val type: Char, val leader: Char? = null)

/** A tab laid out: a gap of its own width, filled with its leader (the dots of a table of contents). */
private class TabSpan(val width: Float, val leader: Char?) : android.text.style.ReplacementSpan() {
    override fun getSize(paint: android.graphics.Paint, text: CharSequence?, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?) = width.toInt()
    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: android.graphics.Paint) {
        val c = leader ?: return
        val dot = paint.measureText(c.toString()).takeIf { it > 0f } ?: return
        val n = ((width - dot * 0.5f) / dot).toInt()
        if (n > 0) canvas.drawText(c.toString().repeat(n), x + width - n * dot, y.toFloat(), paint)
    }
}

/** The document's styles, as far as laying it out needs them: its page, and each paragraph's look. */
internal abstract class DocStyles {
    open val page = DocPage()
    abstract fun look(p: DocPara): ParaLook
    /** Where a list item's text starts and its number or bullet hangs (pt), by the file's own list when it has one. */
    open fun listIndent(p: DocPara): Pair<Float, Float> = (36f * (p.list!!.level + 1)) to 18f
    /** A run's font name from its raw settings (Word's rFonts, ODF's font attributes). */
    open fun fontName(raw: String?): String? = raw?.let { Regex("""(?:w:ascii|w:hAnsi|style:font-name|fo:font-family)="([^"]+)"""").find(it)?.groupValues?.get(1) }
        ?.trim('\'', ' ')?.replace(Regex("""\d+$"""), "")
    /** A list item's number or bullet as its list writes it ("2.1.", "a)", "iv."), from the counts at each level. */
    open fun listLabel(p: DocPara, counts: IntArray): String? = null

    /** A number in a list's style: "1", "a", "A", "i", "I", "01"… */
    fun number(n: Int, format: String): String = when (format) {
        "lowerLetter", "a" -> letters(n).lowercase()
        "upperLetter", "A" -> letters(n)
        "lowerRoman", "i" -> roman(n).lowercase()
        "upperRoman", "I" -> roman(n)
        "decimalZero" -> if (n < 10) "0$n" else "$n"
        "none", "" -> ""
        else -> "$n"
    }
    private fun letters(n: Int): String = if (n <= 0) "" else ('A' + (n - 1) % 26).toString().repeat((n - 1) / 26 + 1)
    private fun roman(n: Int): String {
        var k = n.coerceIn(1, 3999); val sb = StringBuilder()
        for ((v, r) in listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"))
            while (k >= v) { sb.append(r); k -= v }
        return sb.toString()
    }
    /** Word's bullets in the Symbol and Wingdings fonts are private-use characters: the bullets they draw. */
    fun bullet(text: String): String = text.map { c -> when (c) { '\uF0B7', '\uF06C' -> '•'; '\uF0A7', '\uF0FC' -> '▪'; '\uF06F' -> '◦'; '\uF0D8', '\uF0E0' -> '➢'; else -> if (c in '\uF000'..'\uF0FF') '•' else c } }.joinToString("")

    companion object {
        fun of(kind: OfficeKind, bytes: ByteArray?): DocStyles = runCatching {
            val parts = bytes?.let { OfficeFormats.unzip(it) } ?: return@runCatching null
            if (kind == OfficeKind.Docx) DocxStyles(parts) else OdtStyles(parts)
        }.getOrNull() ?: Plain

        /** A new document: Writer's own (A4, 2 cm, as the blank files it writes). */
        val Plain = object : DocStyles() {
            override fun look(p: DocPara) = if (p.heading > 0) ParaLook(before = 12f, after = 6f, bold = true) else ParaLook(after = 6f, lineMultiple = 1.08f)
        }
    }
}

/** Word: page from the last sectPr; looks from docDefaults, the style chain and the paragraph's own pPr. */
private class DocxStyles(parts: Map<String, ByteArray>) : DocStyles() {
    private val styles = HashMap<String, Element>() // paragraph styleId → style
    private val byName = HashMap<String, String>() // name, lowercased → styleId
    private var defaultStyle: String? = null
    private var defaults: Element? = null // docDefaults
    private val numInd = HashMap<Pair<String, Int>, Element>() // (numId, level) → the level's ind
    private val numLevels = HashMap<String, Array<Pair<String, String>?>>() // numId → each level's (numFmt, lvlText)
    private var minorFont: String? = null; private var majorFont: String? = null // the theme's body and heading fonts
    private fun theme(name: String?) = when { name == null -> null; name.startsWith("minor") -> minorFont; name.startsWith("major") -> majorFont; else -> null }
    override fun fontName(raw: String?): String? = super.fontName(raw)
        ?: raw?.let { Regex("""w:(?:ascii|hAnsi)Theme="([^"]+)"""").find(it)?.groupValues?.get(1) }?.let(::theme)
    override val page: DocPage

    init {
        val dir = documentPath(parts).substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        parts[dir + "styles.xml"]?.let { OfficeFormats.parse(it).documentElement }?.let { root ->
            defaults = root.kid(W, "docDefaults")
            for (s in root.kids().filter { it.localName == "style" && it.attr(W, "type") == "paragraph" }) {
                val id = s.attr(W, "styleId") ?: continue
                styles[id] = s
                byName[(s.kid(W, "name")?.attr(W, "val") ?: id).lowercase()] = id
                if (s.attr(W, "default").let { it == "1" || it == "true" }) defaultStyle = id
            }
        }
        parts[dir + "numbering.xml"]?.let { OfficeFormats.parse(it).documentElement }?.let { root ->
            val abstracts = root.kids().filter { it.localName == "abstractNum" }.associateBy { it.attr(W, "abstractNumId") }
            for (n in root.kids().filter { it.localName == "num" }) {
                val id = n.attr(W, "numId") ?: continue
                val abs = abstracts[n.kid(W, "abstractNumId")?.attr(W, "val")] ?: continue
                val levels = arrayOfNulls<Pair<String, String>>(9)
                for (lvl in abs.kids().filter { it.localName == "lvl" }) {
                    val l = lvl.attr(W, "ilvl")?.toIntOrNull() ?: continue
                    lvl.kid(W, "pPr")?.kid(W, "ind")?.let { numInd[id to l] = it }
                    if (l in 0..8) levels[l] = (lvl.kid(W, "numFmt")?.attr(W, "val") ?: "decimal") to (lvl.kid(W, "lvlText")?.attr(W, "val") ?: "")
                }
                numLevels[id] = levels
            }
        }
        parts[dir + "theme/theme1.xml"]?.let { String(it, Charsets.UTF_8) }?.let { t ->
            fun latin(tag: String) = Regex("""<a:$tag>[\s\S]*?<a:latin typeface="([^"]*)"""").find(t)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
            minorFont = latin("minorFont"); majorFont = latin("majorFont")
        }
        val doc = OfficeFormats.parse(parts.getValue(documentPath(parts))).documentElement
        val sect = doc.kid(W, "body")?.kid(W, "sectPr")
        fun twips(e: Element?, name: String) = e?.attr(W, name)?.toFloatOrNull()?.div(20f)
        val size = sect?.kid(W, "pgSz"); val mar = sect?.kid(W, "pgMar")
        val d = DocPage()
        page = DocPage(twips(size, "w") ?: d.width, twips(size, "h") ?: d.height, twips(mar, "top") ?: d.top, twips(mar, "bottom") ?: d.bottom,
            twips(mar, "left") ?: d.left, twips(mar, "right") ?: d.right)
    }

    private fun chain(id: String?): List<Element> {
        val out = ArrayList<Element>()
        var s = styles[id ?: defaultStyle] ?: return out
        while (out.size < 20) { out.add(0, s); s = styles[s.kid(W, "basedOn")?.attr(W, "val")] ?: break }
        return out
    }

    private fun apply(l: ParaLook, pPr: Element?, rPr: Element?): ParaLook {
        var look = l
        pPr?.kids()?.forEach { e -> look = pPrOne(look, e) }
        rPr?.kids()?.forEach { e ->
            val on = e.attr(W, "val").let { it == null || it == "1" || it == "true" || it == "on" }
            when (e.localName) {
                "sz" -> e.attr(W, "val")?.toFloatOrNull()?.let { look = look.copy(sizePt = it / 2) }
                "b" -> look = look.copy(bold = on)
                "i" -> look = look.copy(italic = on)
                "color" -> OfficeFormats.parseHex(e.attr(W, "val"))?.let { look = look.copy(color = it) }
                "rFonts" -> (e.attr(W, "ascii") ?: e.attr(W, "hAnsi") ?: theme(e.attr(W, "asciiTheme") ?: e.attr(W, "hAnsiTheme")))?.let { look = look.copy(font = it) }
            }
        }
        return look
    }

    private fun pPrOne(l: ParaLook, e: Element): ParaLook {
        fun tw(name: String) = e.attr(W, name)?.toFloatOrNull()?.div(20f)
        return when (e.localName) {
            "spacing" -> {
                var n = l.copy(before = tw("before") ?: l.before, after = tw("after") ?: l.after)
                e.attr(W, "line")?.toFloatOrNull()?.let { line ->
                    n = when (e.attr(W, "lineRule")) {
                        "exact" -> n.copy(lineExact = line / 20f, lineAtLeast = false)
                        "atLeast" -> n.copy(lineExact = line / 20f, lineAtLeast = true)
                        else -> n.copy(lineMultiple = line / 240f, lineExact = null)
                    }
                }
                n
            }
            "ind" -> l.copy(left = tw("left") ?: tw("start") ?: l.left, right = tw("right") ?: tw("end") ?: l.right,
                firstLine = tw("hanging")?.let { -it } ?: tw("firstLine") ?: l.firstLine)
            "tabs" -> {
                val t = l.tabs.toMutableList()
                for (tab in e.kids().filter { it.localName == "tab" }) {
                    val pos = tab.attr(W, "pos")?.toFloatOrNull()?.div(20f) ?: continue
                    t.removeAll { kotlin.math.abs(it.pos - pos) < 1f }
                    val v = tab.attr(W, "val")
                    if (v != "clear" && v != "bar") t += TabStop(pos, when (v) { "right", "end", "decimal" -> 'r'; "center" -> 'c'; else -> 'l' },
                        when (tab.attr(W, "leader")) { "dot" -> '.'; "middleDot" -> '·'; "hyphen" -> '-'; "underscore", "heavy" -> '_'; else -> null })
                }
                l.copy(tabs = t.sortedBy { it.pos })
            }
            "pageBreakBefore" -> l.copy(pageBreakBefore = e.attr(W, "val").let { it == null || it == "1" || it == "true" })
            "contextualSpacing" -> l.copy(contextual = e.attr(W, "val").let { it == null || it == "1" || it == "true" })
            else -> l
        }
    }

    private var listIndented = false
    override fun look(p: DocPara): ParaLook {
        listIndented = false
        val d = defaults
        var look = apply(ParaLook(), d?.kid(W, "pPrDefault")?.kid(W, "pPr"), d?.kid(W, "rPrDefault")?.kid(W, "rPr"))
        val styleId = if (p.heading > 0) byName["heading ${p.heading}"] else p.keep?.style
        for (s in chain(styleId)) look = apply(look, s.kid(W, "pPr"), s.kid(W, "rPr"))
        // A list item: its level's indent comes after the style's and before the paragraph's own (Word's order).
        if (p.list != null) p.keep?.list?.substringBefore('|')?.let { numInd[it to p.list.level] }?.let { look = pPrOne(look, it); listIndented = true }
        p.keep?.parts?.forEach { (_, xml) -> runCatching { OfficeFormats.parse(("<x xmlns:w=\"$W\">$xml</x>").toByteArray()).documentElement.kids().forEach { look = pPrOne(look, it) } } }
        return look.copy(listIndented = listIndented)
    }

    override fun listLabel(p: DocPara, counts: IntArray): String? {
        val levels = p.keep?.list?.substringBefore('|')?.let { numLevels[it] } ?: return null
        val (fmt, text) = levels.getOrNull(p.list!!.level) ?: return null
        if (fmt == "bullet") return bullet(text)
        return Regex("%([1-9])").replace(text) { m -> val l = m.groupValues[1].toInt() - 1; number(counts.getOrElse(l) { 1 }.coerceAtLeast(1), levels.getOrNull(l)?.first ?: "decimal") }
    }

    override fun listIndent(p: DocPara): Pair<Float, Float> {
        val ind = p.keep?.list?.substringBefore('|')?.let { numInd[it to p.list!!.level] }
        val left = ind?.let { (it.attr(W, "left") ?: it.attr(W, "start"))?.toFloatOrNull()?.div(20f) }
        val hang = ind?.attr(W, "hanging")?.toFloatOrNull()?.div(20f)
        return if (left != null) left to (hang ?: 0f) else super.listIndent(p)
    }
}

/** ODF: page from the master page's layout; looks from the default style, the named style chain and the paragraph's own. */
private class OdtStyles(parts: Map<String, ByteArray>) : DocStyles() {
    private val named = HashMap<String, Element>() // paragraph style name → style
    private var defaults: Element? = null
    private val listLevels = HashMap<Pair<String, Int>, Element>() // (list style, level) → list-level-label-alignment
    private val listStyles = HashMap<Pair<String, Int>, Element>() // (list style, level) → the level's own style
    override val page: DocPage

    init {
        val styles = parts["styles.xml"]?.let { OfficeFormats.parse(it).documentElement }
        val content = parts["content.xml"]?.let { OfficeFormats.parse(it).documentElement }
        var layouts = HashMap<String, Element>()
        var master: Element? = null
        for (root in listOfNotNull(styles, content)) {
            for (group in root.kids()) when (group.localName) {
                "styles", "automatic-styles" -> for (s in group.kids()) when (s.localName) {
                    "default-style" -> if (s.attr(STYLE, "family") == "paragraph") defaults = s
                    "style" -> if (s.attr(STYLE, "family") == "paragraph" && group.localName == "styles") s.attr(STYLE, "name")?.let { named[it] = s }
                    "page-layout" -> s.attr(STYLE, "name")?.let { layouts[it] = s }
                    "list-style" -> s.attr(STYLE, "name")?.let { name ->
                        s.kids().forEach { lvl -> lvl.attr(TEXT, "level")?.toIntOrNull()?.let { n ->
                            listStyles[name to n - 1] = lvl
                            lvl.kid(STYLE, "list-level-properties")?.kid(STYLE, "list-level-label-alignment")?.let { listLevels[name to n - 1] = it }
                        } }
                    }
                }
                "master-styles" -> master = group.kids().firstOrNull { it.attr(STYLE, "name") == "Standard" } ?: group.kids().firstOrNull()
            }
        }
        val props = master?.attr(STYLE, "page-layout-name")?.let { layouts[it] }?.kid(STYLE, "page-layout-properties")
        fun len(name: String) = odfLength(props?.attr(FO, name))
        val d = DocPage()
        page = DocPage(len("page-width") ?: d.width, len("page-height") ?: d.height, len("margin-top") ?: d.top, len("margin-bottom") ?: d.bottom,
            len("margin-left") ?: d.left, len("margin-right") ?: d.right)
    }

    private fun one(l: ParaLook, name: String, v: String, base: Float): ParaLook = when (name) {
        "fo:margin-top" -> odfLength(v)?.let { l.copy(before = it) } ?: l
        "fo:margin-bottom" -> odfLength(v)?.let { l.copy(after = it) } ?: l
        "fo:margin-left" -> odfLength(v)?.let { l.copy(left = it) } ?: l
        "fo:margin-right" -> odfLength(v)?.let { l.copy(right = it) } ?: l
        "fo:text-indent" -> odfLength(v)?.let { l.copy(firstLine = it) } ?: l
        "fo:line-height" -> if (v.endsWith("%")) v.removeSuffix("%").toFloatOrNull()?.let { l.copy(lineMultiple = it / 100f, lineExact = null) } ?: l
            else odfLength(v)?.let { l.copy(lineExact = it, lineAtLeast = false) } ?: l
        "style:line-height-at-least" -> odfLength(v)?.let { l.copy(lineExact = it, lineAtLeast = true) } ?: l
        "fo:break-before" -> l.copy(pageBreakBefore = v == "page")
        "style:contextual-spacing", "fo:contextual-spacing" -> l.copy(contextual = v == "true")
        "fo:font-size" -> (if (v.endsWith("%")) v.removeSuffix("%").toFloatOrNull()?.let { (l.sizePt ?: base) * it / 100 } else odfLength(v))?.let { l.copy(sizePt = it) } ?: l
        "fo:font-weight" -> l.copy(bold = v == "bold" || (v.toIntOrNull() ?: 400) >= 600)
        "fo:font-style" -> l.copy(italic = v == "italic" || v == "oblique")
        "fo:color" -> OfficeFormats.parseHex(v)?.let { l.copy(color = it) } ?: l
        "style:font-name" -> l.copy(font = v)
        else -> l
    }

    /** A style's or a paragraph's tab stops (they replace the ones before), positions from the text's indent. */
    private fun tabs(l: ParaLook, xml: String): ParaLook {
        if ("tab-stops" !in xml) return l
        val t = Regex("""<style:tab-stop\s([^>]*?)/?>""").findAll(xml).mapNotNull { m ->
            fun a(n: String) = Regex("""$n="([^"]*)"""").find(m.groupValues[1])?.groupValues?.get(1)
            val pos = odfLength(a("style:position")) ?: return@mapNotNull null
            TabStop(pos, when (a("style:type")) { "right", "char" -> 'r'; "center" -> 'c'; else -> 'l' },
                a("style:leader-text")?.firstOrNull()?.takeIf { it != ' ' } ?: when (a("style:leader-style")) { "dotted" -> '.'; "dash" -> '-'; "solid" -> '_'; else -> null })
        }.toList()
        return l.copy(tabs = t.sortedBy { it.pos })
    }

    private fun apply(l: ParaLook, style: Element?, base: Float): ParaLook {
        var look = l
        style?.kid(STYLE, "paragraph-properties")?.let { pp -> pp.kids().firstOrNull { it.localName == "tab-stops" }?.let { look = tabs(look, OfficeFormats.xml(it)) } }
        for (group in listOfNotNull(style?.kid(STYLE, "paragraph-properties"), style?.kid(STYLE, "text-properties"))) {
            val a = group.attributes
            for (i in 0 until a.length) a.item(i).let { look = one(look, it.nodeName, it.nodeValue.trim(), base) }
        }
        return look
    }

    override fun look(p: DocPara): ParaLook {
        var look = apply(ParaLook(), defaults, 12f)
        val name = if (p.heading > 0) "Heading_20_${p.heading}".takeIf { it in named } ?: "Heading" else p.keep?.style ?: "Standard"
        val chain = ArrayList<Element>()
        var s = named[name]
        while (s != null && chain.size < 20) { chain.add(0, s); s = named[s.attr(STYLE, "parent-style-name")] }
        for (st in chain) look = apply(look, st, look.sizePt ?: 12f)
        p.keep?.parts?.forEach { (kind, xml) ->
            if (kind == "@" || kind == "text@") Regex(""" (\S+)="([^"]*)"""").findAll(xml).forEach { m -> look = one(look, m.groupValues[1], m.groupValues[2], look.sizePt ?: 12f) }
            if (kind == "child") look = tabs(look, xml)
        }
        // LibreOffice counts tab stops from the paragraph's indent.
        return look.copy(tabs = look.tabs.map { it.copy(pos = it.pos + look.left) })
    }

    override fun listLabel(p: DocPara, counts: IntArray): String? {
        val name = p.keep?.list?.substringBefore('|') ?: return null
        val level = p.list!!.level
        val l = listStyles[name to level] ?: return null
        if (l.localName == "list-level-style-bullet") return bullet(l.attr(TEXT, "bullet-char") ?: "•")
        val shown = (l.attr(TEXT, "display-levels")?.toIntOrNull() ?: 1).coerceIn(1, level + 1)
        val numbers = (level - shown + 1..level).joinToString(".") { k -> number(counts[k].coerceAtLeast(1), listStyles[name to k]?.attr(STYLE, "num-format") ?: "1") }
        return (l.attr(STYLE, "num-prefix") ?: "") + numbers + (l.attr(STYLE, "num-suffix") ?: "")
    }

    override fun listIndent(p: DocPara): Pair<Float, Float> {
        val a = p.keep?.list?.substringBefore('|')?.let { listLevels[it to p.list!!.level] } ?: return super.listIndent(p)
        val left = odfLength(a.attr(FO, "margin-left")) ?: return super.listIndent(p)
        return left to -(odfLength(a.attr(FO, "text-indent")) ?: 0f)
    }
}

/** An ODF length in points ("2cm", "0.5in", "12pt"…); null for anything else. */
internal fun odfLength(v: String?): Float? = v?.let { Regex("""^(-?[\d.]+)(cm|mm|in|pt|pc|px)$""").find(it.trim()) }?.let { m ->
    val n = m.groupValues[1].toFloatOrNull() ?: return null
    when (m.groupValues[2]) { "cm" -> n * 28.3465f; "mm" -> n * 2.83465f; "in" -> n * 72; "pc" -> n * 12; "px" -> n * 0.75f; else -> n }
}

/** One page, laid out: pieces of paragraphs (some of their lines) and where they go, and floating pictures. */
internal class LaidPage(val page: DocPage, val pieces: List<Piece>, val floats: List<Floating> = emptyList()) {
    class Piece(val layout: StaticLayout, val firstLine: Int, val lastLine: Int, val x: Float, val y: Float)
    class Floating(val picture: android.graphics.drawable.Drawable, val x: Float, val y: Float, val w: Float, val h: Float, val behind: Boolean)

    /** Drawn with the canvas in points, the page's top left at 0,0 (white is the caller's). */
    fun draw(canvas: Canvas) {
        fun floats(behind: Boolean) = floats.filter { it.behind == behind }.forEach { f ->
            f.picture.setBounds(f.x.toInt(), f.y.toInt(), (f.x + f.w).toInt(), (f.y + f.h).toInt()); f.picture.draw(canvas)
        }
        floats(behind = true)
        for (p in pieces) {
            val top = p.layout.getLineTop(p.firstLine).toFloat()
            val bottom = p.layout.getLineBottom(p.lastLine).toFloat()
            canvas.save()
            canvas.translate(p.x, p.y - top)
            canvas.clipRect(-p.x, top, p.layout.width + p.x + 1000f, bottom)
            p.layout.draw(canvas)
            canvas.restore()
        }
        floats(behind = false)
    }
}

internal object OfficePages {
    /** Word's default tab stops: every half inch. */
    private const val TAB = 36f

    fun layout(doc: OfficeDoc, styles: DocStyles, pictureBytes: (DocImage) -> ByteArray?): List<LaidPage> {
        val page = styles.page
        val pages = ArrayList<LaidPage>()
        var pieces = ArrayList<LaidPage.Piece>()
        var floats = ArrayList<LaidPage.Floating>()
        var y = page.top
        fun newPage() { pages += LaidPage(page, pieces, floats); pieces = ArrayList(); floats = ArrayList(); y = page.top }
        val counters = IntArray(9)
        var prevLook: ParaLook? = null; var prevStyle: String? = null
        doc.paras.forEachIndexed { i, p ->
            val list = p.list
            if (list == null) counters.fill(0) else {
                // A list begins after a paragraph that is not in one, or where a top-level item is of another list.
                fun key(q: DocPara) = q.keep?.list?.substringBefore('|') ?: "writer ${q.list?.ordered}"
                val prev = doc.paras.getOrNull(i - 1)
                if (prev?.list == null || (list.level == 0 && key(prev) != key(p))) { counters.fill(0); counters[0] = (list.start ?: 1) - 1 }
                list.start?.takeIf { list.level > 0 && counters[list.level] == 0 }?.let { counters[list.level] = it - 1 }
                counters[list.level]++
                for (d in list.level + 1 until 9) counters[d] = 0
            }
            val look = styles.look(p)
            val marker = if (list == null) null else styles.listLabel(p, counters) ?: if (list.ordered) "${counters[list.level]}." else listOf("•", "◦", "▪")[list.level % 3]
            val floating = ArrayList<Pair<DocImage, PictureXml.Place>>()
            val layout = paragraph(doc, p, look, styles, marker, pictureBytes, floating)
            val style = p.keep?.style + "/" + p.heading
            // Space between paragraphs: after the one before, then before this one — none between two of the same
            // style that say so (contextual spacing).
            val same = look.contextual && prevLook?.contextual == true && style == prevStyle
            if (look.pageBreakBefore && pieces.isNotEmpty()) newPage()
            if (pieces.isNotEmpty() && !same) y += look.before
            val limit = page.height - page.bottom
            var line = 0
            while (line < layout.lineCount) {
                // As many lines as fit on this page (a line never splits; one taller than a page goes on a page of its own).
                val top = layout.getLineTop(line)
                if (y + (layout.getLineBottom(line) - top) > limit && pieces.isNotEmpty()) { newPage(); continue }
                var last = line
                while (last + 1 < layout.lineCount && y + (layout.getLineBottom(last + 1) - top) <= limit) last++
                pieces += LaidPage.Piece(layout, line, last, page.left, y)
                // Floating pictures, placed where the paragraph starts: from the page, the margin, or the paragraph.
                if (line == 0) for ((img, place) in floating) {
                    val (w, h) = img.widthPt to img.heightPt
                    val x = when (place.alignH) {
                        "center" -> (if (place.fromH == "page") page.width else page.textWidth) / 2 - w / 2 + (if (place.fromH == "page") 0f else page.left)
                        "right", "outside" -> (if (place.fromH == "page") page.width else page.left + page.textWidth) - w
                        "left", "inside" -> if (place.fromH == "page") 0f else page.left
                        else -> (place.x ?: 0f) + if (place.fromH == "page") 0f else page.left
                    }
                    val fy = when (place.alignV) {
                        "center" -> (page.height - h) / 2
                        "bottom" -> (if (place.fromV == "page") page.height else page.height - page.bottom) - h
                        "top" -> if (place.fromV == "page") 0f else page.top
                        else -> (place.y ?: 0f) + when (place.fromV) { "page" -> 0f; "margin" -> page.top; else -> y }
                    }
                    val picture = pictureBytes(img)?.let { b -> decodePicture(b, 1600) }?.let { BitmapDrawable(null, it) } ?: ColorDrawable(0xFFE3E3E3.toInt())
                    floats += LaidPage.Floating(picture, x, fy, w, h, place.wrap == PictureWrap.Behind)
                    // Top and bottom: the text goes on below it. ponytail: square wrap does not yet flow the text beside it.
                    if (place.wrap == PictureWrap.TopBottom) y = maxOf(y, fy + h - (layout.getLineBottom(last) - top))
                }
                y += layout.getLineBottom(last) - top
                line = last + 1
                if (line < layout.lineCount) newPage()
            }
            // Word spaces every line, the last one too; Android's layout leaves the last line unspaced.
            if (look.lineExact == null && layout.lineCount > 0) layout.lineCount.minus(1).let { last -> y += (look.lineMultiple.coerceIn(0.5f, 5f) - 1f) * (layout.getLineBottom(last) - layout.getLineTop(last)) }
            if (!same || i + 1 >= doc.paras.size) y += look.after
            prevLook = look; prevStyle = style
        }
        pages += LaidPage(page, pieces, floats)
        return pages
    }

    private fun paragraph(doc: OfficeDoc, p: DocPara, look: ParaLook, styles: DocStyles, marker: String?, pictureBytes: (DocImage) -> ByteArray?,
        floating: MutableList<Pair<DocImage, PictureXml.Place>>): StaticLayout {
        val page = styles.page
        val size = look.sizePt ?: doc.basePt
        val text = SpannableStringBuilder()
        // Indents: the list's own (a number hanging before the text), else the paragraph's.
        val (left, first) = if (p.list != null && !look.listIndented) styles.listIndent(p).let { (l, hang) -> l to l - hang } else look.left to look.left + look.firstLine
        if (marker != null) {
            text.append(marker).append('\t')
            // The number or bullet in the look of the paragraph's first letter (Word: its paragraph mark's).
            p.runs.firstOrNull { it.image == null && it.text.isNotBlank() }?.let { r ->
                fun mark(s: Any) = text.setSpan(s, 0, marker.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (r.bold) mark(StyleSpan(Typeface.BOLD))
                r.sizePt?.let { mark(AbsoluteSizeSpan(it.toInt().coerceAtLeast(1))) }
                OfficeFonts.typeface(styles.fontName(r.font))?.let { mark(TypefaceSpan(OfficeFonts.styled(it, r.bold, r.italic))) }
                r.color?.let { mark(ForegroundColorSpan(0xFF000000.toInt() or it)) }
            }
        }
        for (r in OfficeFormats.tidy(p.runs)) {
            // A floating picture takes no room in the line: it is placed with the paragraph.
            r.image?.let { img -> PictureXml.placeOf(img)?.let { floating += img to it } }?.let { continue }
            val start = text.length
            text.append(r.text.replace(LINE_BREAK, '\n'))
            val end = text.length
            fun span(s: Any) = text.setSpan(s, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            r.image?.let { img ->
                val width = page.textWidth - left.coerceAtLeast(0f)
                val fit = minOf(width / img.widthPt, (page.height - page.top - page.bottom - 20f) / img.heightPt, 1f)
                val w = (img.widthPt * fit).toInt().coerceAtLeast(1); val h = (img.heightPt * fit).toInt().coerceAtLeast(1)
                val drawable = pictureBytes(img)?.let { b -> decodePicture(b, 1600) }?.let { BitmapDrawable(null, it) } ?: ColorDrawable(0xFFE3E3E3.toInt())
                drawable.setBounds(0, 0, w, h)
                span(ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM))
                continue
            }
            when { // the style's bold and italic are the paint's
                r.bold && r.italic -> span(StyleSpan(Typeface.BOLD_ITALIC))
                r.bold -> span(StyleSpan(Typeface.BOLD))
                r.italic -> span(StyleSpan(Typeface.ITALIC))
            }
            if (r.underline || r.link != null) span(UnderlineSpan())
            if (r.strike) span(StrikethroughSpan())
            r.sizePt?.let { span(AbsoluteSizeSpan(it.toInt().coerceAtLeast(1))) }
            if (r.script != 0) { span(if (r.script > 0) SuperscriptSpan() else SubscriptSpan()); span(RelativeSizeSpan(0.65f)) }
            (r.color ?: if (r.link != null) 0x0563C1 else null)?.let { span(ForegroundColorSpan(0xFF000000.toInt() or it)) }
            r.highlight?.let { span(BackgroundColorSpan(0xFF000000.toInt() or it)) }
            OfficeFonts.typeface(styles.fontName(r.font))?.let { span(TypefaceSpan(OfficeFonts.styled(it, r.bold || look.bold == true, r.italic || look.italic == true))) }
        }
        if (text.isEmpty()) text.append(" ") // an empty paragraph is still a line
        fun whole(s: Any) = text.setSpan(s, 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        if (left > 0f || first > 0f || first != left) whole(LeadingMarginSpan.Standard(first.coerceAtLeast(0f).toInt(), left.coerceAtLeast(0f).toInt()))
        look.lineExact?.let { exact -> if (!look.lineAtLeast || exact > size * 1.17f) whole(LineHeightSpan.Standard(exact.toInt().coerceAtLeast(1))) }
        // Laid out in points and drawn scaled: linear, sub-pixel text, or letters snap to whole points and space unevenly.
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.LINEAR_TEXT_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
            color = look.color?.let { 0xFF000000.toInt() or it } ?: Color.BLACK
            textSize = size
            typeface = OfficeFonts.styled(OfficeFonts.typeface(look.font), look.bold == true, look.italic == true)
        }
        val width = (page.textWidth - look.right.coerceAtLeast(0f)).toInt().coerceAtLeast(40)
        val align = when (p.align) { DocAlign.Center -> Layout.Alignment.ALIGN_CENTER; DocAlign.End -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
        fun build() = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(align)
            .setLineSpacing(0f, if (look.lineExact == null) look.lineMultiple.coerceIn(0.5f, 5f) else 1f).setIncludePad(false)
            .setJustificationMode(if (p.align == DocAlign.Justify) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE)
            .build()
        // Tabs, left to right (Android's layout knows only left stops): each goes to the next stop after where it
        // starts — the paragraph's own, a list's text position, then every half inch — the text after it lined up to
        // the stop's side (a table of contents: page numbers right-aligned, dots before them).
        val stops = (look.tabs + if (first < left) listOf(TabStop(left, 'l')) else emptyList()).sortedBy { it.pos }
        for (k in text.indices.filter { text[it] == '\t' }) {
            val x = build().getPrimaryHorizontal(k)
            val stop = stops.firstOrNull { it.pos > x + 0.5f } ?: TabStop((kotlin.math.floor(x / TAB) + 1) * TAB, 'l')
            val end = (k + 1 until text.length).firstOrNull { text[it] == '\t' || text[it] == '\n' } ?: text.length
            val after = Layout.getDesiredWidth(text, k + 1, end, paint)
            val gap = when (stop.type) { 'r' -> stop.pos - x - after; 'c' -> stop.pos - x - after / 2; else -> stop.pos - x }
            text.setSpan(TabSpan(gap.coerceAtLeast(1f), stop.leader), k, k + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return build()
    }

}

/**
 * The fonts Writer brings (assets/fonts, SIL Open Font License): free fonts made with the letter widths of Word's own —
 * Liberation Serif and Sans for Times New Roman and Arial, Carlito for Calibri, Caladea for Cambria — so lines and
 * pages break where Word's and LibreOffice's do (2026-09-28: with the phone's own fonts a 20-page paper took 24).
 */
internal object OfficeFonts {
    @Volatile var assets: android.content.res.AssetManager? = null
    private val families = HashMap<String, Typeface?>()

    private fun load(name: String, fallback: String): Typeface? = synchronized(families) {
        families.getOrPut(name) {
            val a = assets ?: return@getOrPut null
            runCatching {
                fun font(style: String, weight: Int, italic: Boolean) = android.graphics.fonts.Font.Builder(a, "fonts/$name-$style.ttf").setWeight(weight)
                    .setSlant(if (italic) android.graphics.fonts.FontStyle.FONT_SLANT_ITALIC else android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT).build()
                val family = android.graphics.fonts.FontFamily.Builder(font("Regular", 400, false)).addFont(font("Bold", 700, false))
                    .addFont(font("Italic", 400, true)).addFont(font("BoldItalic", 700, true)).build()
                Typeface.CustomFallbackBuilder(family).setSystemFallback(fallback).build() // letters it lacks: the phone's
            }.getOrNull()
        }
    }

    /** The typeface for a document's font name; null: the phone's own. */
    fun typeface(name: String?): Typeface? {
        val n = name?.lowercase()?.trim() ?: return null
        return when {
            "times" in n || n == "liberation serif" -> load("LiberationSerif", "serif")
            "arial" in n || "helvetica" in n || n == "liberation sans" -> load("LiberationSans", "sans-serif")
            "calibri" in n || "carlito" in n -> load("Carlito", "sans-serif")
            "cambria" in n || "caladea" in n -> load("Caladea", "serif")
            listOf("mono", "courier", "consol").any { it in n } -> Typeface.MONOSPACE
            listOf("serif", "georgia", "garamond", "book", "palatino", "roman").any { it in n } && "sans" !in n -> load("LiberationSerif", "serif")
            else -> null
        }
    }

    fun styled(tf: Typeface?, bold: Boolean, italic: Boolean): Typeface =
        Typeface.create(tf ?: Typeface.DEFAULT, if (bold) 700 else 400, italic)
}
