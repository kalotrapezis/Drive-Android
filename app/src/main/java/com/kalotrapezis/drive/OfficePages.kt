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
    val left: Float = 56.7f, val right: Float = 56.7f, val header: Float = 35.4f, val footer: Float = 35.4f /* from the page's edge */,
    val columns: Int = 1, val columnGap: Float = 36f,
    val color: Int? = null /* Design › Page colour (RGB); null: white */, val border: PageBorder? = null /* Design › Page borders */) {
    val textWidth get() = (width - left - right).coerceAtLeast(8f)
    /** Column [k] of the text, as a page of its own width (the text flows down one, then the next). */
    fun column(k: Int): DocPage {
        if (columns <= 1) return this
        val w = ((textWidth - columnGap * (columns - 1)) / columns).coerceAtLeast(8f)
        val l = left + k * (w + columnGap)
        return copy(left = l, right = width - l - w)
    }
}

/** A box round the page: its line, and how far in from each edge of the page it runs (pt). */
internal data class PageBorder(val line: Border, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** How a paragraph is laid out: spacing (pt), line spacing (a multiple, or exact pt), indents (pt), its text's look. */
internal data class ParaLook(
    val before: Float = 0f, val after: Float = 0f,
    val lineMultiple: Float = 1f, val lineExact: Float? = null, val lineAtLeast: Boolean = false,
    val left: Float = 0f, val right: Float = 0f, val firstLine: Float = 0f, // firstLine < 0: hanging
    val pageBreakBefore: Boolean = false, val contextual: Boolean = false, val box: ParaBox? = null,
    val sizePt: Float? = null, val bold: Boolean? = null, val italic: Boolean? = null, val color: Int? = null, val font: String? = null,
    val tabs: List<TabStop> = emptyList(),
    val listIndented: Boolean = false, // a list item's indent is already in left/firstLine
    val align: DocAlign? = null, // the style's alignment (a paragraph's own is DocPara.align)
)

/**
 * Paragraphs are laid out in twentieths of a point (Word's own unit): Android's layout takes whole numbers for widths
 * and indents, and in points a line that fits by a quarter point broke, a number that ends on its tab stop overshot.
 */
private const val U = 20f
private fun StaticLayout.topPt(line: Int) = getLineTop(line) / U
private fun StaticLayout.bottomPt(line: Int) = getLineBottom(line) / U

/** A tab stop: where (pt from the text's left edge), how the text after it lines up ('l', 'r', 'c'), what fills the gap. */
internal data class TabStop(val pos: Float, val type: Char, val leader: Char? = null)

/** A tab laid out: a gap of its own width, filled with its leader (the dots of a table of contents). */
private class TabSpan(val width: Float, val leader: Char?) : android.text.style.ReplacementSpan() {
    override fun getSize(paint: android.graphics.Paint, text: CharSequence?, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?) = Math.round(width)
    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: android.graphics.Paint) {
        val c = leader ?: return
        val dot = paint.measureText(c.toString()).takeIf { it > 0f } ?: return
        val n = ((width - dot * 0.5f) / dot).toInt()
        if (n > 0) canvas.drawText(c.toString().repeat(n), x + width - n * dot, y.toFloat(), paint)
    }
}

/**
 * A section of the document: its pages' size and margins, headers and footers ("default", "first" for a title page,
 * "even"), where its page numbers start (null: they go on) and their style, and whether it starts a new page.
 */
internal data class DocSection(val page: DocPage, val headers: Map<String, List<DocPara>> = emptyMap(), val footers: Map<String, List<DocPara>> = emptyMap(),
    val titlePage: Boolean = false, val pageStart: Int? = null, val pageFormat: String = "decimal", val continuous: Boolean = false,
    val watermark: String? = null /* Design › Watermark: its text */)

/**
 * A table as the pages need it: column widths, indent and alignment; each row's height ("at least", or exact) and
 * whether it is a heading row (repeated on each page); each cell's column span, vertical merge ('n' none, 'r' starts,
 * 'c' goes on), shading, vertical alignment ('t', 'c', 'b'), margins (left, top, right, bottom) and borders (top, left,
 * bottom, right; null: none).
 */
internal class TableSpec(val cols: List<Float>, val indent: Float, val align: DocAlign, val rows: List<RowSpec>)
internal class RowSpec(val height: Float?, val exact: Boolean, val header: Boolean, val cells: List<CellSpec>)
internal class CellSpec(val span: Int, val merge: Char, val fill: Int?, val vAlign: Char, val margin: FloatArray, val borders: Array<Border?>,
    val turn: Int = 0 /* its text read upwards (-90, Word's btLr) or downwards (90, tbRl) */,
    val text: ParaLook? = null /* the table style's look for its text here (header bold…), under the paragraphs' own */)
internal data class Border(val width: Float, val color: Int)

/** A Word border (w:top, w:bottom… in eighths of a point); none when it says so. */
internal fun wordBorder(e: Element): Border? {
    val v = e.attr(W, "val")
    if (v == null || v == "nil" || v == "none") return null
    val sz = (e.attr(W, "sz")?.toFloatOrNull() ?: 4f) / 8f
    return Border(if (v == "double") sz * 3 else sz.coerceAtLeast(0.25f), e.attr(W, "color")?.takeIf { it != "auto" }?.let { OfficeFormats.parseHex(it) } ?: 0)
}

/** An OpenDocument border ("0.5pt solid #000000"); none when it says so. */
internal fun odfBorder(v: String?): Border? {
    if (v == null || v.startsWith("none") || v.startsWith("hidden")) return null
    val parts = v.trim().split(Regex("\\s+"))
    val w = parts.firstNotNullOfOrNull { odfLength(it) } ?: 0.5f
    return Border(if ("double" in parts) w else w.coerceAtLeast(0.25f), parts.firstOrNull { it.startsWith("#") }?.let { OfficeFormats.parseHex(it) } ?: 0)
}

/**
 * A paragraph's borders (Word's pBdr, ODT's fo:border): its four sides, the line between two paragraphs with the
 * same borders (one box, as Word draws them), and how far each side stands from the text (pt).
 */
internal data class ParaBox(val top: Border? = null, val left: Border? = null, val bottom: Border? = null, val right: Border? = null,
    val between: Border? = null, val padTop: Float = 1f, val padLeft: Float = 4f, val padBottom: Float = 1f, val padRight: Float = 4f) {
    val isEmpty get() = top == null && left == null && bottom == null && right == null && between == null
}
internal data class TableCellArea(val row: Int, val cell: Int, val x: Float, val y: Float, val width: Float, val height: Float)

/** The document's styles, as far as laying it out needs them: its sections, and each paragraph's look. */
internal abstract class DocStyles {
    /** The document's last section (a file without section breaks has only this one). */
    open val section = DocSection(DocPage())
    val page get() = section.page
    abstract fun look(p: DocPara): ParaLook
    /** Each paragraph's section: a paragraph that carries a section break ends one. */
    open fun sections(paras: List<DocPara>): List<DocSection> = List(paras.size) { section }
    /** Even pages have headers and footers of their own. */
    open val evenAndOdd = false
    /** A table's geometry; by default columns of equal width, thin black borders. */
    open fun tableSpec(t: DocTable): TableSpec {
        val thin = Border(0.5f, 0)
        return TableSpec(emptyList(), 0f, DocAlign.Start, t.rows.map { r -> RowSpec(null, false, false, r.cells.map { CellSpec(1, 'n', null, 't', floatArrayOf(5.4f, 0f, 5.4f, 0f), arrayOf(thin, thin, thin, thin)) }) })
    }
    /** Where a list item's text starts and its number or bullet hangs (pt), by the file's own list when it has one. */
    open fun listIndent(p: DocPara): Pair<Float, Float> = (36f * (p.list!!.level + 1)) to 18f
    /** A run's font name from its raw settings (Word's rFonts, ODF's font attributes). */
    open fun fontName(raw: String?): String? = raw?.let { Regex("""(?:w:ascii|w:hAnsi|style:font-name|fo:font-family)="([^"]+)"""").find(it)?.groupValues?.get(1) }
        ?.trim('\'', ' ')?.replace(Regex("""\d+$"""), "")
    /** A list item's number or bullet as its list writes it ("2.1.", "a)", "iv."), from the counts at each level. */
    open fun listLabel(p: DocPara, counts: IntArray): String? = null

    /**
     * The paragraph styles to offer: Normal, Title, Subtitle, the six headings, Quote, then the document's own
     * (its hidden ones left out).
     */
    open fun paragraphStyles(): List<StyleEntry> = standardStyles(emptyList())
    protected fun standardStyles(own: List<StyleEntry>, title: String = "Title", subtitle: String = "Subtitle", quote: String = "Quote"): List<StyleEntry> {
        val first = listOf(StyleEntry("Normal", null), StyleEntry("Title", title), StyleEntry("Subtitle", subtitle)) +
            (1..6).map { StyleEntry("Heading $it", null, it) } + StyleEntry("Quote", quote)
        return first + own.filter { o -> first.none { it.id == o.id } }.sortedBy { it.name.lowercase() }
    }
    /** How the document's styles name [e] (its id in Word, its name in LibreOffice); null: it cannot be changed. */
    open fun styleKey(e: StyleEntry): String? = null
    /** Word's plain style's id (what new styles are based on). */
    open val normalId: String? get() = null
    /** Which of [paragraphStyles] a paragraph has. */
    fun styleOf(p: DocPara): StyleEntry = paragraphStyles().firstOrNull { if (p.heading > 0) it.heading == p.heading else it.heading == 0 && it.id == p.keep?.style }
        ?: StyleEntry(p.keep?.style ?: "Normal", p.keep?.style)

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
            // A new document: the styles of the blank file Writer will write, so what is set on it shows before it is saved.
            val parts = OfficeFormats.unzip(bytes ?: OfficeFormats.write(OfficeDoc(emptyList()), kind))
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
    private val tableStyles = HashMap<String, Element>() // table styleId → style
    private val byName = HashMap<String, String>() // name, lowercased → styleId
    private val ownStyles = ArrayList<String>() // the paragraph styles people see, in the file's order
    private var defaultStyle: String? = null
    private var defaults: Element? = null // docDefaults
    private val numInd = HashMap<Pair<String, Int>, Element>() // (numId, level) → the level's ind
    private val numLevels = HashMap<String, Array<Pair<String, String>?>>() // numId → each level's (numFmt, lvlText)
    private var minorFont: String? = null; private var majorFont: String? = null // the theme's body and heading fonts
    override var evenAndOdd = false
    private var bodySect: Element? = null
    private var background: Int? = null
    private val targets: Map<String, String> // relationship id → part (headers, footers)
    private val parts = parts
    private fun theme(name: String?) = when { name == null -> null; name.startsWith("minor") -> minorFont; name.startsWith("major") -> majorFont; else -> null }
    override fun fontName(raw: String?): String? = super.fontName(raw)
        ?: raw?.let { Regex("""w:(?:ascii|hAnsi)Theme="([^"]+)"""").find(it)?.groupValues?.get(1) }?.let(::theme)
    override val section: DocSection

    init {
        val dir = documentPath(parts).substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        parts[dir + "styles.xml"]?.let { OfficeFormats.parse(it).documentElement }?.let { root ->
            defaults = root.kid(W, "docDefaults")
            for (s in root.kids().filter { it.localName == "style" && it.attr(W, "type") == "table" }) s.attr(W, "styleId")?.let { tableStyles[it] = s }
            for (s in root.kids().filter { it.localName == "style" && it.attr(W, "type") == "paragraph" }) {
                val id = s.attr(W, "styleId") ?: continue
                styles[id] = s
                byName[(s.kid(W, "name")?.attr(W, "val") ?: id).lowercase()] = id
                if (s.attr(W, "default").let { it == "1" || it == "true" }) defaultStyle = id
                if (s.kid(W, "semiHidden") == null) ownStyles += id
            }
        }
        // Title, Subtitle and Quote as Word makes them, for a document without: shown before it is saved with them.
        for ((id, xml) in DOCX_BUILTIN) if (id.lowercase() !in byName && id !in styles) {
            val d = defaultStyle?.let { xml.replace("NORMAL", it) } ?: xml.replace(Regex("""<w:(basedOn|next) w:val="NORMAL"/>"""), "")
            styles[id] = OfficeFormats.parse("<x xmlns:w=\"$W\">$d</x>".toByteArray()).documentElement.kids().first()
            byName[id.lowercase()] = id
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
        background = doc.kid(W, "background")?.attr(W, "color")?.takeIf { it != "auto" }?.let { OfficeFormats.parseHex(it) }
        bodySect = doc.kid(W, "body")?.kid(W, "sectPr")
        targets = parts[relsPath(documentPath(parts))]?.let { OfficeFormats.parse(it).documentElement.kids().associate { r -> r.getAttribute("Id") to resolve(dir, r.getAttribute("Target")) } }.orEmpty()
        evenAndOdd = parts[dir + "settings.xml"]?.let { "<w:evenAndOddHeaders" in String(it, Charsets.UTF_8) } ?: false
        section = section(bodySect, null)
    }

    /** A section from its sectPr; headers and footers it does not name are the section before's (Word's "link to previous"). */
    private fun section(sect: Element?, before: DocSection?): DocSection {
        fun twips(e: Element?, name: String) = e?.attr(W, name)?.toFloatOrNull()?.div(20f)
        val size = sect?.kid(W, "pgSz"); val mar = sect?.kid(W, "pgMar")
        val d = DocPage()
        val page = DocPage(twips(size, "w") ?: d.width, twips(size, "h") ?: d.height, twips(mar, "top") ?: d.top, twips(mar, "bottom") ?: d.bottom,
            twips(mar, "left") ?: d.left, twips(mar, "right") ?: d.right, twips(mar, "header") ?: d.header, twips(mar, "footer") ?: d.footer,
            sect?.kid(W, "cols")?.attr(W, "num")?.toIntOrNull()?.coerceIn(1, 6) ?: 1, twips(sect?.kid(W, "cols"), "space") ?: 36f).let { pg ->
            // Page borders: the top one's line for the box; from the page's edge, or (Word's default) from the text.
            val borders = sect?.kid(W, "pgBorders")
            val line = borders?.kids()?.firstNotNullOfOrNull { wordBorder(it) }
            fun space(side: String) = borders?.kid(W, side)?.attr(W, "space")?.toFloatOrNull() ?: 0f
            val box = line?.let { l -> if (borders.attr(W, "offsetFrom") == "page") PageBorder(l, space("left"), space("top"), space("right"), space("bottom"))
                else PageBorder(l, pg.left - space("left"), pg.top - space("top"), pg.right - space("right"), pg.bottom - space("bottom")) }
            pg.copy(color = background, border = box)
        }
        val headers = HashMap(before?.headers.orEmpty()); val footers = HashMap(before?.footers.orEmpty())
        sect?.kids()?.forEach { e ->
            val into = when (e.localName) { "headerReference" -> headers; "footerReference" -> footers; else -> null } ?: return@forEach
            targets[e.attr(RELATIONSHIPS, "id")]?.let { into[e.attr(W, "type") ?: "default"] = OfficeFormats.docxPart(parts, it) }
        }
        val numbers = sect?.kid(W, "pgNumType")
        // Its watermark: in one of its own headers, or (having none) the section before's.
        val refs = sect?.kids()?.filter { it.localName == "headerReference" }.orEmpty()
        val watermark = if (refs.isEmpty()) before?.watermark
            else refs.firstNotNullOfOrNull { r -> targets[r.attr(RELATIONSHIPS, "id")]?.let { parts[it] }?.let { Watermark.textIn(String(it, Charsets.UTF_8)) } }
        return DocSection(page, headers, footers, watermark = watermark,
            titlePage = sect?.kid(W, "titlePg")?.let { it.attr(W, "val").let { v -> v == null || v == "1" || v == "true" } } ?: false,
            pageStart = numbers?.attr(W, "start")?.toIntOrNull(), pageFormat = numbers?.attr(W, "fmt") ?: "decimal",
            continuous = sect?.kid(W, "type")?.attr(W, "val") == "continuous")
    }

    override fun tableSpec(t: DocTable): TableSpec {
        fun el(xml: String) = runCatching { OfficeFormats.parse("<x xmlns:w=\"$W\">$xml</x>".toByteArray()).documentElement }.getOrNull()
        fun tw(e: Element?, n: String = "w") = e?.attr(W, n)?.toFloatOrNull()?.div(20f)
        fun border(e: Element) = wordBorder(e)
        fun edge(name: String) = when (name) { "start" -> "left"; "end" -> "right"; else -> name }
        val tblPr = el(t.props)?.kid(W, "tblPr")
        // The table style (and the styles it is based on), then the table's own settings.
        val chain = ArrayList<Element>()
        var st = tableStyles[tblPr?.kid(W, "tblStyle")?.attr(W, "val")]
        while (st != null && chain.size < 10) { chain.add(0, st); st = tableStyles[st.kid(W, "basedOn")?.attr(W, "val")] }
        val prs = chain.mapNotNull { it.kid(W, "tblPr") } + listOfNotNull(tblPr)
        val borders = HashMap<String, Border?>()
        val margin = floatArrayOf(5.4f, 0f, 5.4f, 0f)
        fun margins(pr: Element?, m: FloatArray) = pr?.kids()?.forEach { e ->
            val v = tw(e) ?: return@forEach
            when (edge(e.localName)) { "left" -> m[0] = v; "top" -> m[1] = v; "right" -> m[2] = v; "bottom" -> m[3] = v }
        }
        for (pr in prs) {
            pr.kid(W, "tblBorders")?.kids()?.forEach { borders[edge(it.localName)] = border(it) }
            margins(pr.kid(W, "tblCellMar"), margin)
        }
        val cols = el(t.grid)?.kid(W, "tblGrid")?.kids()?.filter { it.localName == "gridCol" }?.mapNotNull { tw(it) }.orEmpty()
        val align = when (tblPr?.kid(W, "jc")?.attr(W, "val")) { "center" -> DocAlign.Center; "right", "end" -> DocAlign.End; else -> DocAlign.Start }
        val gridCount = cols.size.coerceAtLeast(t.rows.maxOfOrNull { r -> r.cells.sumOf { c -> el(c.props)?.kid(W, "tcPr")?.kid(W, "gridSpan")?.attr(W, "val")?.toIntOrNull() ?: 1 } } ?: 1)
        // The style's parts for some cells only (Word's conditional formatting): which the table turns on (tblLook),
        // and how many rows or columns make a band.
        val look = tblPr?.kid(W, "tblLook")
        fun flag(name: String, bit: Int) = look?.attr(W, name)?.let { it == "1" || it == "true" } ?: ((look?.attr(W, "val")?.toIntOrNull(16) ?: 0x04A0) and bit != 0)
        val firstRow = flag("firstRow", 0x20); val lastRow = flag("lastRow", 0x40); val firstCol = flag("firstColumn", 0x80); val lastCol = flag("lastColumn", 0x100)
        val hBand = !flag("noHBand", 0x200); val vBand = !flag("noVBand", 0x400)
        fun band(name: String) = prs.firstNotNullOfOrNull { it.kid(W, name)?.attr(W, "val")?.toIntOrNull() }?.coerceAtLeast(1) ?: 1
        val rowBand = band("tblStyleRowBandSize"); val colBand = band("tblStyleColBandSize")
        fun part(type: String) = chain.flatMap { st -> st.kids().filter { it.localName == "tblStylePr" && it.attr(W, "type") == type } }
        val parts = listOf("wholeTable", "band1Vert", "band2Vert", "band1Horz", "band2Horz", "firstCol", "lastCol", "firstRow", "lastRow", "neCell", "nwCell", "seCell", "swCell")
            .associateWith { part(it) }.filterValues { it.isNotEmpty() }
        val headerRows = if (firstRow) 1 else 0
        fun partsFor(ri: Int, g: Int, span: Int): List<Element> {
            val top = firstRow && ri == 0; val bottom = lastRow && ri == t.rows.size - 1
            val left = firstCol && g == 0; val right = lastCol && g + span >= gridCount
            val types = ArrayList<String>().apply {
                add("wholeTable")
                if (vBand && !left && !right) add(if (((g - (if (firstCol) 1 else 0)) / colBand) % 2 == 0) "band1Vert" else "band2Vert")
                if (hBand && !top && !bottom) add(if (((ri - headerRows) / rowBand) % 2 == 0) "band1Horz" else "band2Horz")
                if (left) add("firstCol"); if (right) add("lastCol"); if (top) add("firstRow"); if (bottom) add("lastRow")
                if (top && right) add("neCell"); if (top && left) add("nwCell"); if (bottom && right) add("seCell"); if (bottom && left) add("swCell")
            }
            return types.flatMap { parts[it].orEmpty() }
        }
        val rows = t.rows.mapIndexed { ri, row ->
            val trPr = el(row.props)?.kid(W, "trPr")
            val h = trPr?.kid(W, "trHeight")
            var g = 0
            val cells = row.cells.map { c ->
                val tcPr = el(c.props)?.kid(W, "tcPr")
                val span = tcPr?.kid(W, "gridSpan")?.attr(W, "val")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val merge = tcPr?.kid(W, "vMerge")?.let { if (it.attr(W, "val") == "restart") 'r' else 'c' } ?: 'n'
                val cond = partsFor(ri, g, span)
                fun shdOf(pr: Element?) = pr?.kid(W, "shd")?.attr(W, "fill")?.takeIf { it != "auto" }?.let { OfficeFormats.parseHex(it) }
                // The cell's own shading wins, even "none" (fill auto); else the style's part for this cell, else its whole.
                val fill = if (tcPr?.kid(W, "shd") != null) shdOf(tcPr)
                    else (cond + chain).lastOrNull { it.kid(W, "tcPr")?.kid(W, "shd") != null }?.let { shdOf(it.kid(W, "tcPr")) }
                // Its text's look from those parts: bold, italic, colour, size (the paragraph's own style still wins).
                val text = cond.mapNotNull { it.kid(W, "rPr") }.fold(null as ParaLook?) { l, rPr ->
                    fun on(n: String) = rPr.kid(W, n)?.attr(W, "val").let { v -> if (rPr.kid(W, n) == null) null else v == null || v == "1" || v == "true" }
                    (l ?: ParaLook()).let { x -> x.copy(bold = on("b") ?: x.bold, italic = on("i") ?: x.italic,
                        color = rPr.kid(W, "color")?.attr(W, "val")?.takeIf { it != "auto" }?.let { OfficeFormats.parseHex(it) } ?: x.color,
                        sizePt = rPr.kid(W, "sz")?.attr(W, "val")?.toFloatOrNull()?.div(2) ?: x.sizePt) }
                }
                val m = margin.copyOf().also { margins(tcPr?.kid(W, "tcMar"), it) }
                // Each edge: the table's outer border on the outside, its inner one inside; the cell's own over both.
                val b = arrayOf(
                    borders[if (ri == 0) "top" else "insideH"], borders[if (g == 0) "left" else "insideV"],
                    borders[if (ri == t.rows.size - 1) "bottom" else "insideH"], borders[if (g + span >= gridCount) "right" else "insideV"])
                // The style's parts' lines for this cell, then the cell's own.
                (cond.mapNotNull { it.kid(W, "tcPr")?.kid(W, "tcBorders") } + listOfNotNull(tcPr?.kid(W, "tcBorders"))).forEach { bs -> bs.kids().forEach { e ->
                    when (edge(e.localName)) { "top" -> b[0] = border(e); "left" -> b[1] = border(e); "bottom" -> b[2] = border(e); "right" -> b[3] = border(e) }
                } }
                g += span
                CellSpec(span, merge, fill, when (tcPr?.kid(W, "vAlign")?.attr(W, "val")) { "center" -> 'c'; "bottom" -> 'b'; else -> 't' }, m, b,
                    when (tcPr?.kid(W, "textDirection")?.attr(W, "val")) { "btLr" -> -90; "tbRl", "tbRlV" -> 90; else -> 0 }, text)
            }
            RowSpec(tw(h, "val"), h?.attr(W, "hRule") == "exact", trPr?.kid(W, "tblHeader").let { it != null && it.attr(W, "val").let { v -> v == null || v == "1" || v == "true" } }, cells)
        }
        return TableSpec(cols, tw(tblPr?.kid(W, "tblInd")) ?: 0f, align, rows)
    }

    override fun sections(paras: List<DocPara>): List<DocSection> {
        val out = arrayOfNulls<DocSection>(paras.size)
        var start = 0; var before: DocSection? = null
        for ((i, p) in paras.withIndex()) {
            val xml = p.keep?.parts?.firstOrNull { it.first == "sectPr" }?.second ?: continue
            val sect = runCatching { OfficeFormats.parse("<x xmlns:w=\"$W\" xmlns:r=\"$RELATIONSHIPS\">$xml</x>".toByteArray()).documentElement.kids().firstOrNull() }.getOrNull()
            val s = section(sect, before)
            for (k in start..i) out[k] = s
            before = s; start = i + 1
        }
        val last = if (before == null) section else section(bodySect, before)
        return out.map { it ?: last }
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
            "pBdr" -> {
                var b = ParaBox()
                for (k in e.kids()) {
                    val space = k.attr(W, "space")?.toFloatOrNull()
                    when (k.localName) {
                        "top" -> b = b.copy(top = wordBorder(k), padTop = space ?: b.padTop)
                        "left", "start" -> b = b.copy(left = wordBorder(k), padLeft = space ?: b.padLeft)
                        "bottom" -> b = b.copy(bottom = wordBorder(k), padBottom = space ?: b.padBottom)
                        "right", "end" -> b = b.copy(right = wordBorder(k), padRight = space ?: b.padRight)
                        "between" -> b = b.copy(between = wordBorder(k))
                    }
                }
                l.copy(box = b.takeIf { !it.isEmpty })
            }
            "jc" -> l.copy(align = when (e.attr(W, "val")) { "center" -> DocAlign.Center; "right", "end" -> DocAlign.End; "both", "distribute" -> DocAlign.Justify; else -> DocAlign.Start })
            "contextualSpacing" -> l.copy(contextual = e.attr(W, "val").let { it == null || it == "1" || it == "true" })
            else -> l
        }
    }

    private var listIndented = false
    override fun paragraphStyles(): List<StyleEntry> {
        val headings = (1..6).mapNotNull { byName["heading $it"] }.toSet()
        val own = ownStyles.filter { it != defaultStyle && it !in headings }.map { id ->
            StyleEntry((styles[id]?.kid(W, "name")?.attr(W, "val") ?: id).replaceFirstChar { it.uppercase() }, id)
        }
        return standardStyles(own, byName["title"] ?: "Title", byName["subtitle"] ?: "Subtitle", byName["quote"] ?: "Quote")
    }
    override fun styleKey(e: StyleEntry) = if (e.heading > 0) byName["heading ${e.heading}"] ?: "Heading${e.heading}" else e.id ?: defaultStyle ?: "Normal"
    override val normalId get() = defaultStyle

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
    private val tableStyles = HashMap<String, Element>() // "table-cell/A1" … → style
    private var defaults: Element? = null
    private val listLevels = HashMap<Pair<String, Int>, Element>() // (list style, level) → list-level-label-alignment
    private val listStyles = HashMap<Pair<String, Int>, Element>() // (list style, level) → the level's own style
    override val section: DocSection

    init {
        val styles = parts["styles.xml"]?.let { OfficeFormats.parse(it).documentElement }
        val content = parts["content.xml"]?.let { OfficeFormats.parse(it).documentElement }
        var layouts = HashMap<String, Element>()
        var master: Element? = null
        for (root in listOfNotNull(styles, content)) {
            for (group in root.kids()) when (group.localName) {
                "styles", "automatic-styles" -> for (s in group.kids()) when (s.localName) {
                    "default-style" -> if (s.attr(STYLE, "family") == "paragraph") defaults = s
                    "style" -> {
                        if (s.attr(STYLE, "family") == "paragraph" && group.localName == "styles") s.attr(STYLE, "name")?.let { named[it] = s }
                        s.attr(STYLE, "family")?.takeIf { it.startsWith("table") }?.let { f -> s.attr(STYLE, "name")?.let { tableStyles["$f/$it"] = s } }
                    }
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
        // Title, Subtitle and Quotations as LibreOffice makes them, for a document without: shown before it is saved with them.
        OfficeFormats.parse(("<x xmlns:style=\"$STYLE\" xmlns:fo=\"$FO\">" + ODT_BUILTIN.filterKeys { it !in named }.values.joinToString("") + "</x>").toByteArray())
            .documentElement.kids().forEach { st -> st.attr(STYLE, "name")?.let { named[it] = st } }
        // A table Writer put in, not saved yet: its styles are Writer's own.
        if ("table-cell/$ODT_TETRA_CELL" !in tableStyles) OfficeFormats.parse(("<x xmlns:style=\"$STYLE\" xmlns:fo=\"$FO\" xmlns:table=\"$TABLE\">$ODT_TETRA_STYLES</x>").toByteArray())
            .documentElement.kids().forEach { st -> tableStyles["${st.attr(STYLE, "family")}/${st.attr(STYLE, "name")}"] = st }
        val layout = master?.attr(STYLE, "page-layout-name")?.let { layouts[it] }
        val props = layout?.kid(STYLE, "page-layout-properties")
        fun len(name: String) = odfLength(props?.attr(FO, name))
        val d = DocPage()
        // A header sits at the top margin and the text starts below it (its height, at least one line, and its spacing),
        // as LibreOffice lays a page out; the footer likewise at the bottom.
        val (headers, footers) = OfficeFormats.odtMaster(parts)
        fun band(paras: List<DocPara>?, style: String, gap: String): Float {
            if (paras == null) return 0f
            val hf = layout?.kid(STYLE, style)?.kid(STYLE, "header-footer-properties")
            return maxOf(odfLength(hf?.attr(FO, "min-height")) ?: 0f, paras.size.coerceAtLeast(1) * 14f) + (odfLength(hf?.attr(FO, gap)) ?: 0f)
        }
        val top = len("margin-top") ?: d.top; val bottom = len("margin-bottom") ?: d.bottom
        // A page's border stands at the margins; the text starts its padding inside it.
        val edge = odfBorder(props?.attr(FO, "border") ?: props?.attr(FO, "border-top"))
        fun pad(side: String) = if (edge == null) 0f else len("padding-$side") ?: len("padding") ?: 0f
        section = DocSection(DocPage(len("page-width") ?: d.width, len("page-height") ?: d.height,
            top + pad("top") + band(headers["default"], "header-style", "margin-bottom"), bottom + pad("bottom") + band(footers["default"], "footer-style", "margin-top"),
            (len("margin-left") ?: d.left) + pad("left"), (len("margin-right") ?: d.right) + pad("right"), header = top, footer = bottom,
            columns = props?.kid(STYLE, "columns")?.attr(FO, "column-count")?.toIntOrNull()?.coerceIn(1, 6) ?: 1,
            columnGap = odfLength(props?.kid(STYLE, "columns")?.attr(FO, "column-gap")) ?: 36f,
            color = props?.attr(FO, "background-color")?.takeIf { it.startsWith("#") }?.let { OfficeFormats.parseHex(it.removePrefix("#")) },
            // LibreOffice draws a page's border round its margins.
            border = edge?.let { PageBorder(it, len("margin-left") ?: d.left, top, len("margin-right") ?: d.right, bottom) }),
            headers, footers, titlePage = "first" in headers || "first" in footers,
            watermark = master?.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:drawing:1.0", "custom-shape")?.let { l ->
                (0 until l.length).map { l.item(it) as Element }.firstOrNull { it.getAttributeNS("urn:oasis:names:tc:opendocument:xmlns:drawing:1.0", "name").startsWith(Watermark.NAME) }?.textContent?.trim() })
    }

    /**
     * An OpenDocument table (2026-09-28): its columns' widths, its rows' heights, each cell's background, lines,
     * padding and vertical alignment, from their styles; spans and merged-away (covered) cells as Word's are drawn.
     */
    override fun tableSpec(t: DocTable): TableSpec {
        fun a(xml: String, name: String) = Regex("""$name="([^"]*)"""").find(xml)?.groupValues?.get(1)
        fun props(family: String, attrs: String, kind: String) = a(attrs, "table:style-name")?.let { tableStyles["$family/$it"] }?.kid(STYLE, kind)
        val tp = props("table", t.props, "table-properties")
        val width = odfLength(tp?.attr(STYLE, "width"))
        val cols = ArrayList<Float>()
        Regex("""<table:table-column\b([^>]*?)/?>""").findAll(t.grid).forEach { m ->
            val cp = props("table-column", m.groupValues[1], "table-column-properties")
            val n = a(m.groupValues[1], "table:number-columns-repeated")?.toIntOrNull()?.coerceIn(1, 64) ?: 1
            val w = odfLength(cp?.attr(STYLE, "column-width")) ?: cp?.attr(STYLE, "rel-column-width")?.removeSuffix("*")?.toFloatOrNull()?.let { -it } ?: 0f
            repeat(n) { cols += w }
        }
        // Relative widths (negative here) share the table's width.
        if (cols.any { it < 0f }) { val rel = cols.filter { it < 0f }.sumOf { -it.toDouble() }.toFloat(); val w = width ?: section.page.textWidth; cols.replaceAll { if (it < 0f) -it / rel * w else it } }
        fun border(v: String?) = odfBorder(v)
        val gridCount = cols.size.coerceAtLeast(1)
        val vertical = IntArray(gridCount + 64) // rows still covered below a cell merged down, per grid column
        val verticalSpan = IntArray(gridCount + 64)
        val rows = t.rows.map { row ->
            val rp = props("table-row", row.props, "table-row-properties")
            var g = 0; var sideways = 0
            val cells = row.cells.map { c ->
                val covered = c.props.startsWith(ODT_COVERED)
                val cp = props("table-cell", c.props, "table-cell-properties")
                val pad = odfLength(cp?.attr(FO, "padding")) ?: 0f
                fun padding(side: String) = odfLength(cp?.attr(FO, "padding-$side")) ?: pad
                val all = cp?.attr(FO, "border")
                fun edge(side: String) = border(cp?.attr(FO, "border-$side") ?: all)
                val fill = cp?.attr(FO, "background-color")?.takeIf { it.startsWith("#") }?.let { OfficeFormats.parseHex(it) }
                val vAlign = when (cp?.attr(STYLE, "vertical-align")) { "middle" -> 'c'; "bottom" -> 'b'; else -> 't' }
                val turn = when (cp?.attr(STYLE, "writing-mode")) { "tb-rl", "tb" -> 90; else -> 0 }
                val spec = when {
                    covered && sideways > 0 -> { sideways--; CellSpec(0, 'c', null, 't', FloatArray(4), arrayOfNulls(4)) }
                    covered && vertical.getOrElse(g) { 0 } > 0 -> {
                        val span = verticalSpan[g]; vertical[g]--; sideways = span - 1
                        CellSpec(span, 'c', null, 't', FloatArray(4), arrayOfNulls(4)).also { g += span }
                    }
                    else -> {
                        val span = a(c.props, "table:number-columns-spanned")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val down = a(c.props, "table:number-rows-spanned")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        if (down > 1 && g < vertical.size) { vertical[g] = down - 1; verticalSpan[g] = span }
                        sideways = span - 1
                        CellSpec(span, if (down > 1) 'r' else if (covered) 'c' else 'n', fill, vAlign,
                            floatArrayOf(padding("left"), padding("top"), padding("right"), padding("bottom")),
                            arrayOf(edge("top"), edge("left"), edge("bottom"), edge("right")), turn).also { g += span }
                    }
                }
                spec
            }
            val exact = odfLength(rp?.attr(STYLE, "row-height"))
            RowSpec(exact ?: odfLength(rp?.attr(STYLE, "min-row-height")), exact != null, row.props.startsWith(ODT_HEADER), cells)
        }
        val align = when (tp?.attr(TABLE, "align")) { "center" -> DocAlign.Center; "right" -> DocAlign.End; else -> DocAlign.Start }
        return TableSpec(cols, odfLength(tp?.attr(FO, "margin-left")) ?: 0f, align, rows)
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
        "fo:border" -> odfBorder(v).let { b -> l.copy(box = (l.box ?: ParaBox()).copy(top = b, left = b, bottom = b, right = b).takeIf { !it.isEmpty }) }
        "fo:border-top" -> l.copy(box = (l.box ?: ParaBox()).copy(top = odfBorder(v)).takeIf { !it.isEmpty })
        "fo:border-bottom" -> l.copy(box = (l.box ?: ParaBox()).copy(bottom = odfBorder(v)).takeIf { !it.isEmpty })
        "fo:border-left" -> l.copy(box = (l.box ?: ParaBox()).copy(left = odfBorder(v)).takeIf { !it.isEmpty })
        "fo:border-right" -> l.copy(box = (l.box ?: ParaBox()).copy(right = odfBorder(v)).takeIf { !it.isEmpty })
        "style:join-border" -> if (v == "false") l.box?.let { l.copy(box = it.copy(between = it.top ?: it.bottom)) } ?: l else l
        "fo:padding" -> odfLength(v)?.let { d -> l.box?.let { l.copy(box = it.copy(padTop = d, padLeft = d, padBottom = d, padRight = d)) } } ?: l
        "fo:padding-top" -> odfLength(v)?.let { d -> l.box?.let { l.copy(box = it.copy(padTop = d)) } } ?: l
        "fo:padding-bottom" -> odfLength(v)?.let { d -> l.box?.let { l.copy(box = it.copy(padBottom = d)) } } ?: l
        "fo:padding-left" -> odfLength(v)?.let { d -> l.box?.let { l.copy(box = it.copy(padLeft = d)) } } ?: l
        "fo:padding-right" -> odfLength(v)?.let { d -> l.box?.let { l.copy(box = it.copy(padRight = d)) } } ?: l
        "fo:text-align" -> l.copy(align = when (v) { "center" -> DocAlign.Center; "end", "right" -> DocAlign.End; "justify" -> DocAlign.Justify; else -> DocAlign.Start })
        "fo:font-family" -> l.copy(font = v.trim('\'', '"'))
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

    override fun paragraphStyles(): List<StyleEntry> {
        val own = named.filterKeys { it != "Standard" && it != "Text_20_body" && it != "Heading" && !it.startsWith("Heading_20_") }
            .map { (name, e) -> StyleEntry(e.attr(STYLE, "display-name") ?: odfDisplayName(name), name) }
        return standardStyles(own, "Title", "Subtitle", "Quotations")
    }
    override fun styleKey(e: StyleEntry) = if (e.heading > 0) "Heading_20_${e.heading}" else e.id ?: if ("Text_20_body" in named) "Text_20_body" else "Standard"

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
internal class LaidPage(val page: DocPage, val pieces: List<Piece>, val floats: List<Floating> = emptyList(),
    val section: DocSection = DocSection(page), val number: Int = 1, val firstOfSection: Boolean = false,
    val boxes: List<Box> = emptyList(), val lines: List<Line> = emptyList()) {
    /** [turn]: drawn turned that many degrees about ([tx], [ty]), its x and y then counted in the turned frame. */
    class Piece(val layout: StaticLayout, val firstLine: Int, val lastLine: Int, val x: Float, val y: Float,
        val turn: Int = 0, val tx: Float = 0f, val ty: Float = 0f)
    /** A table cell's shading, and a border. */
    class Box(val x: Float, val y: Float, val w: Float, val h: Float, val color: Int)
    class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val width: Float, val color: Int)
    class Floating(val picture: android.graphics.drawable.Drawable, val x: Float, val y: Float, val w: Float, val h: Float, val behind: Boolean)

    /** Drawn with the canvas in points, the page's top left at 0,0 (white is the caller's; a page colour is drawn). */
    fun draw(canvas: Canvas) {
        page.color?.let { canvas.drawRect(0f, 0f, page.width, page.height, android.graphics.Paint().apply { color = 0xFF000000.toInt() or it }) }
        page.border?.let { b -> canvas.drawRect(b.left, b.top, page.width - b.right, page.height - b.bottom, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE; strokeWidth = b.line.width; color = 0xFF000000.toInt() or b.line.color }) }
        fun floats(behind: Boolean) = floats.filter { it.behind == behind }.forEach { f ->
            val shape = f.picture as? ShapePicture
            if (shape != null) shape.drawAt(canvas, f.x, f.y, f.w, f.h)
            else { f.picture.setBounds(f.x.toInt(), f.y.toInt(), (f.x + f.w).toInt(), (f.y + f.h).toInt()); f.picture.draw(canvas) }
        }
        section.watermark?.let { text ->
            // Across the text's width, turned 45° up, light grey and half see-through, behind the text (Word's look).
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0x80C0C0C0.toInt(); typeface = android.graphics.Typeface.DEFAULT; textSize = 100f }
            p.textSize = 100f * page.textWidth / p.measureText(text).coerceAtLeast(1f)
            canvas.save(); canvas.rotate(-45f, page.width / 2, page.height / 2)
            canvas.drawText(text, (page.width - p.measureText(text)) / 2, page.height / 2 - (p.ascent() + p.descent()) / 2, p)
            canvas.restore()
        }
        floats(behind = true)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        for (b in boxes) { paint.color = 0xFF000000.toInt() or b.color; canvas.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, paint) }
        for (p in pieces) {
            val top = p.layout.getLineTop(p.firstLine).toFloat()
            val bottom = p.layout.getLineBottom(p.lastLine).toFloat()
            canvas.save()
            if (p.turn != 0) { canvas.translate(p.tx, p.ty); canvas.rotate(p.turn.toFloat()) }
            canvas.translate(p.x, p.y)
            canvas.scale(1 / U, 1 / U) // the layout counts in twentieths of a point
            canvas.translate(0f, -top)
            canvas.clipRect(-p.x * U, top, p.layout.width + (p.x + 1000f) * U, bottom)
            p.layout.draw(canvas)
            canvas.restore()
        }
        paint.strokeCap = android.graphics.Paint.Cap.SQUARE
        for (l in lines) { paint.color = 0xFF000000.toInt() or l.color; paint.strokeWidth = l.width; canvas.drawLine(l.x1, l.y1, l.x2, l.y2, paint) }
        floats(behind = false)
    }
}

/** Some of a page laid out on its own (a table's row, a cell's contents): what goes where, from its top left. */
internal class Block(val height: Float, val pieces: List<LaidPage.Piece>, val boxes: List<LaidPage.Box>, val lines: List<LaidPage.Line>,
    val across: Float = 0f /* turned text: how wide it stands */) {
    fun moved(dx: Float, dy: Float) = Block(height, pieces.map {
        if (it.turn == 0) LaidPage.Piece(it.layout, it.firstLine, it.lastLine, it.x + dx, it.y + dy)
        else LaidPage.Piece(it.layout, it.firstLine, it.lastLine, it.x, it.y, it.turn, it.tx + dx, it.ty + dy) },
        boxes.map { LaidPage.Box(it.x + dx, it.y + dy, it.w, it.h, it.color) }, lines.map { LaidPage.Line(it.x1 + dx, it.y1 + dy, it.x2 + dx, it.y2 + dy, it.width, it.color) }, across)
}

internal object OfficePages {
    /**
     * A table's columns fitted into [width] when wider: each keeps at least [least] (its longest word, when known),
     * and what is left is shared in proportion to what they had over that — so on a phone a column of short numbers
     * narrows and a column of words keeps its words whole, rather than every column shrinking alike.
     */
    internal fun fittedTableColumns(columns: List<Float>, width: Float, indent: Float, least: List<Float> = emptyList()): List<Float> {
        val limit = (width - indent.coerceAtLeast(0f)).coerceAtLeast(1f)
        if (columns.sum() <= limit) return columns
        val need = columns.indices.map { (least.getOrNull(it) ?: 0f).coerceAtMost(columns[it]) }
        if (need.sum() >= limit) return need.sum().let { n -> if (n > 0f) need.map { it * limit / n } else columns.map { it * limit / columns.sum() } }
        val over = columns.indices.map { columns[it] - need[it] }
        return columns.indices.map { need[it] + (limit - need.sum()) * over[it] / over.sum() }
    }

    /** The widest word of a cell's paragraphs (pt), in their own sizes and fonts: less than this, a word breaks. */
    private fun longestWord(doc: OfficeDoc, paras: List<DocPara>, styles: DocStyles): Float {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
        return paras.filter { it.table == null }.maxOfOrNull { p ->
            val look = styles.look(p)
            p.runs.filter { it.image == null }.maxOfOrNull { r ->
                paint.textSize = r.sizePt ?: look.sizePt ?: doc.basePt
                paint.typeface = OfficeFonts.styled(OfficeFonts.typeface(styles.fontName(r.font) ?: look.font), r.bold || look.bold == true, r.italic || look.italic == true)
                r.text.split(' ', '\t', LINE_BREAK).maxOfOrNull { paint.measureText(it) } ?: 0f
            } ?: 0f
        } ?: 0f
    }

    /** Word's default tab stops: every half inch. */
    private const val TAB = 36f

    /**
     * [pageOf], when given, learns the page number (as printed) each paragraph starts on — for a table of contents;
     * [pageIndexOf] the page itself (0 = the first), as the list has them.
     */
    fun layout(doc: OfficeDoc, styles: DocStyles, pageOf: IntArray? = null, pageIndexOf: IntArray? = null, pictureBytes: (DocImage) -> ByteArray?): List<LaidPage> {
        val sections = styles.sections(doc.paras)
        var section = sections.firstOrNull() ?: styles.section
        var frame = section.page // the sheet
        var col = 0
        var page = frame.column(0) // where the text flows: the page, or one of its columns
        var number = section.pageStart ?: 1
        var firstOfSection = true
        val pages = ArrayList<LaidPage>()
        var pieces = ArrayList<LaidPage.Piece>()
        var floats = ArrayList<LaidPage.Floating>()
        var boxes = ArrayList<LaidPage.Box>(); var lines = ArrayList<LaidPage.Line>()
        var y = page.top
        // The page's pictures that no text may stand beside (top-and-bottom wrap), top to bottom.
        val bands = ArrayList<Pair<Float, Float>>()
        /** [at] moved below any band a line of height [h] there would cross. */
        fun clear(at: Float, h: Float): Float {
            var yy = at
            var moved = true
            while (moved) { moved = false; for ((a, b) in bands) if (yy < b && yy + h > a) { yy = b; moved = true } }
            return yy
        }
        fun newPage() {
            bands.clear()
            // In columns: the next column first.
            if (col + 1 < frame.columns) { col++; page = frame.column(col); y = page.top; return }
            pages += LaidPage(frame, pieces, floats, section, number, firstOfSection, boxes, lines)
            pieces = ArrayList(); floats = ArrayList(); boxes = ArrayList(); lines = ArrayList(); col = 0; page = frame.column(0); y = page.top; number++; firstOfSection = false
        }
        /** A page break: a whole new page, whatever column the text is in. */
        fun breakPage() { col = frame.columns; newPage() }
        fun place(b: Block) { pieces += b.pieces; boxes += b.boxes; lines += b.lines }
        val counters = IntArray(9)
        var prevLook: ParaLook? = null; var prevStyle: String? = null
        // Where the paragraph before ended (its text, before the space after it), on which page: a bordered box goes on
        // down through the space between two paragraphs with the same borders.
        var prevEnd = 0f; var prevEndPage = -1
        doc.paras.forEachIndexed { i, p ->
            // A new section: a new page (unless it goes on on the same one) of its own size, numbered as it says.
            sections.getOrNull(i)?.takeIf { it !== section }?.let { next ->
                if (!next.continuous) {
                    if (pieces.isNotEmpty() || floats.isNotEmpty()) breakPage()
                    section = next; frame = next.page; col = 0; page = frame.column(0); y = page.top; firstOfSection = true
                    next.pageStart?.let { number = it }
                } else section = next
            }
            // A table: its rows, each kept whole on a page; its heading rows again at the top of each page it goes on to.
            p.table?.let { t ->
                val rows = tableRows(doc, t, styles, page, page.textWidth, pictureBytes)
                val heading = rows.takeWhile { it.second }.map { it.first }
                val limit = page.height - page.bottom
                rows.forEachIndexed { ri, (row, _) ->
                    if (y + row.height > limit && (pieces.isNotEmpty() || boxes.isNotEmpty() || lines.isNotEmpty())) {
                        newPage()
                        if (ri >= heading.size) heading.forEach { h -> place(h.moved(page.left, y)); y += h.height }
                    }
                    place(row.moved(page.left, y)); y += row.height
                }
                counters.fill(0); prevLook = null; prevStyle = null; prevEndPage = -1
                return@forEachIndexed
            }
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
            val drops = ArrayList<DropLetters>()
            val (layout, shift, lastExtra) = paragraph(doc, p, look, styles, page, marker, pictureBytes, floating, drops)
            val style = p.keep?.style + "/" + p.heading
            // Space between paragraphs: after the one before, then before this one — none between two of the same
            // style that say so (contextual spacing).
            val same = look.contextual && prevLook?.contextual == true && style == prevStyle
            if (look.pageBreakBefore && pieces.isNotEmpty()) breakPage()
            if (pieces.isNotEmpty() && !same) y += look.before
            val limit = page.height - page.bottom
            var line = 0
            // Where the paragraph starts as the text flows, before a picture's band pushes its lines down: what its
            // floating pictures are placed from (Word's "from the paragraph"; found 2026-09-29 — pen strokes on a
            // keyboard picture were drawn a keyboard lower).
            var natural = y
            var floated = false
            while (line < layout.lineCount) {
                // As many lines as fit on this page (a line never splits; one taller than a page goes on a page of its own).
                val top = layout.topPt(line)
                y = clear(y, layout.bottomPt(line) - top)
                if (y + (layout.bottomPt(line) - top) > limit && pieces.isNotEmpty()) { newPage(); if (line == 0) natural = y; continue }
                if (line == 0 && !floated) {
                    floated = true
                    for (d in drops) floats += LaidPage.Floating(DropDrawable(d, page.left, y), page.left, y, 1f, 1f, behind = false)
                    for ((img, place) in floating) {
                        val f = floatAt(img, place, natural, page, pictureBytes)
                        floats += f
                        // Top and bottom: no text beside it — lines that would cross it go on below it.
                        // ponytail: square wrap does not yet flow the text beside it.
                        if (place.wrap == PictureWrap.TopBottom) bands += f.y to f.y + f.h
                    }
                    y = clear(y, layout.bottomPt(line) - top)
                }
                var last = line
                while (last + 1 < layout.lineCount && y + (layout.bottomPt(last + 1) - top) <= limit) last++
                pieces += LaidPage.Piece(layout, line, last, page.left - shift, y)
                if (line == 0) { pageOf?.let { if (i < it.size) it[i] = number }; pageIndexOf?.let { if (i < it.size) it[i] = pages.size } }
                look.box?.let { box -> boxLines(box, look, page, y, y + layout.bottomPt(last) - top, line == 0, last == layout.lineCount - 1,
                    box == prevLook?.box && prevEndPage == pages.size, box == doc.paras.getOrNull(i + 1)?.takeIf { it.table == null }?.let { styles.look(it).box },
                    prevEnd, lines) }
                y += layout.bottomPt(last) - top
                line = last + 1
                if (line < layout.lineCount) newPage()
            }
            // Word spaces every line, the last one too; Android's layout leaves the last line unspaced.
            y += lastExtra
            prevEnd = y; prevEndPage = pages.size
            if (!same || i + 1 >= doc.paras.size) y += look.after
            prevLook = look; prevStyle = style
        }
        pages += LaidPage(frame, pieces, floats, section, number, firstOfSection, boxes, lines)
        return pages.mapIndexed { i, laid -> withHeaderAndFooter(doc, styles, laid, i, pages.size, pictureBytes) }
    }

    /**
     * A paragraph's borders around its lines from [y0] to [y1] (one piece of it on a page). With the paragraph before
     * in the same box ([joinsAbove]) the sides go on up to where it ended and the line between them is drawn; the top
     * line only where the box starts, the bottom one where it ends.
     */
    private fun boxLines(box: ParaBox, look: ParaLook, page: DocPage, y0: Float, y1: Float, first: Boolean, last: Boolean,
        joinsAbove: Boolean, joinsBelow: Boolean, prevEnd: Float, out: MutableList<LaidPage.Line>) {
        // The box stands out from the text by its sides' spacing, only where it has a side (a line under alone spans the text).
        val x0 = page.left + minOf(look.left, look.left + look.firstLine) - (if (box.left != null) box.padLeft else 0f)
        val x1 = page.left + page.textWidth - look.right + (if (box.right != null) box.padRight else 0f)
        fun line(b: Border?, xa: Float, ya: Float, xb: Float, yb: Float) { if (b != null) out += LaidPage.Line(xa, ya, xb, yb, b.width, b.color) }
        val top = when {
            !first -> y0
            joinsAbove -> { line(box.between, x0, y0 - box.padTop, x1, y0 - box.padTop); prevEnd }
            else -> (y0 - box.padTop).also { line(box.top, x0, it, x1, it) }
        }
        val bottom = if (last && !joinsBelow) (y1 + box.padBottom).also { line(box.bottom, x0, it, x1, it) } else y1
        line(box.left, x0, top, x0, bottom)
        line(box.right, x1, top, x1, bottom)
    }

    /**
     * The same table layout used on a page, as one block for the editor, [width] pt wide: the editor lays a table out as
     * wide as its screen at its own text size, so a phone re-flows it rather than shrinking the letters.
     */
    private fun tableBlock(doc: OfficeDoc, table: DocTable, styles: DocStyles, width: Float, pictureBytes: (DocImage) -> ByteArray?,
        areas: MutableList<TableCellArea>? = null): Block {
        // On a screen wider than the page, the columns widen with it: the table keeps its share of the text's width.
        val rows = tableRows(doc, table, styles, styles.page, width, pictureBytes, areas, (width / styles.page.textWidth).coerceAtLeast(1f))
        val pieces = ArrayList<LaidPage.Piece>(); val boxes = ArrayList<LaidPage.Box>(); val lines = ArrayList<LaidPage.Line>()
        var y = 0f
        rows.forEach { (row, _) ->
            val placed = row.moved(0f, y)
            pieces += placed.pieces; boxes += placed.boxes; lines += placed.lines; y += row.height
        }
        return Block(y.coerceAtLeast(1f), pieces, boxes, lines)
    }

    /** A table's width (the page's text, or [most]: the editor's) and laid-out height, for its editor stand-in. */
    fun tableSize(doc: OfficeDoc, table: DocTable, styles: DocStyles, most: Float = Float.MAX_VALUE): Pair<Float, Float> {
        val width = if (most < Float.MAX_VALUE) most else styles.page.textWidth
        return width to tableBlock(doc, table, styles, width, { null }).height
    }


    /** The table laid out as page view lays it, [width] pt wide, as a page of its own the editor draws; [areas] learns its cells. */
    fun tableLaid(doc: OfficeDoc, table: DocTable, styles: DocStyles, width: Float, areas: MutableList<TableCellArea>?,
        pictureBytes: (DocImage) -> ByteArray?): LaidPage {
        val block = tableBlock(doc, table, styles, width, pictureBytes, areas)
        return LaidPage(DocPage(width = width, height = block.height, top = 0f, bottom = 0f, left = 0f, right = 0f), block.pieces,
            boxes = block.boxes, lines = block.lines)
    }

    /** The page's header (from the top edge, at its distance) and footer (ending at its distance from the bottom). */
    private fun withHeaderAndFooter(doc: OfficeDoc, styles: DocStyles, laid: LaidPage, index: Int, count: Int, pictureBytes: (DocImage) -> ByteArray?): LaidPage {
        val page = laid.page
        val section = laid.section
        fun which(map: Map<String, List<DocPara>>) = when {
            laid.firstOfSection && section.titlePage -> map["first"] ?: emptyList() // a title page has its own (none, if the file has none)
            styles.evenAndOdd && laid.number % 2 == 0 -> map["even"] ?: map["default"]
            else -> map["default"]
        }.orEmpty()
        val number = styles.number(laid.number, section.pageFormat)
        fun filled(paras: List<DocPara>) = paras.map { p -> p.copy(runs = p.runs.map { r ->
            if (PAGE_FIELD in r.text || PAGES_FIELD in r.text) r.copy(text = r.text.replace(PAGE_FIELD.toString(), number).replace(PAGES_FIELD.toString(), count.toString())) else r
        }) }
        val extraFloats = ArrayList<LaidPage.Floating>()
        val header = stack(doc, filled(which(section.headers)), styles, page, page.textWidth, pictureBytes, extraFloats, page.left, page.header).moved(page.left, page.header)
        // The footer ends at its distance from the page's bottom.
        val f = stack(doc, filled(which(section.footers)), styles, page, page.textWidth, pictureBytes, null, 0f, 0f)
        val footer = f.moved(page.left, page.height - page.footer - f.height)
        return LaidPage(page, header.pieces + footer.pieces + laid.pieces, laid.floats + extraFloats, section, laid.number, laid.firstOfSection,
            header.boxes + footer.boxes + laid.boxes, laid.lines + header.lines + footer.lines)
    }

    /**
     * Paragraphs (and tables) one under the other in [width], from 0,0 — a header, a footer, a table's cell. Floating
     * pictures go to [floats] placed from ([left], [top]) when given; else (in a cell) they are left out.
     */
    private fun stack(doc: OfficeDoc, paras: List<DocPara>, styles: DocStyles, page: DocPage, width: Float, pictureBytes: (DocImage) -> ByteArray?,
        floats: MutableList<LaidPage.Floating>?, left: Float, top: Float, tableExtraWidth: Float = 0f, under: ParaLook? = null): Block {
        val area = page.copy(width = width, left = 0f, right = 0f)
        val pieces = ArrayList<LaidPage.Piece>(); val boxes = ArrayList<LaidPage.Box>(); val lines = ArrayList<LaidPage.Line>()
        var y = 0f
        paras.forEachIndexed { i, p ->
            p.table?.let { t ->
                // Word lets a nested table use the parent cell's right margin; ordinary text still stops before it.
                tableRows(doc, t, styles, page, width + tableExtraWidth, pictureBytes).forEach { (row, _) -> val b = row.moved(0f, y); pieces += b.pieces; boxes += b.boxes; lines += b.lines; y += row.height }
                return@forEachIndexed
            }
            val look = styles.look(p).let { l -> under?.let { u -> l.copy(bold = l.bold ?: u.bold, italic = l.italic ?: u.italic, color = l.color ?: u.color, sizePt = l.sizePt ?: u.sizePt) } ?: l }
            val marker = p.list?.let { if (it.ordered) "${it.start ?: 1}." else "•" }
            val floating = ArrayList<Pair<DocImage, PictureXml.Place>>()
            val (layout, shift, lastExtra) = paragraph(doc, p, look, styles, area, marker, pictureBytes, floating)
            if (i > 0) y += look.before
            pieces += LaidPage.Piece(layout, 0, layout.lineCount - 1, -shift, y)
            if (floats != null) floating.forEach { (img, place) -> floats += floatAt(img, place, top + y, page, pictureBytes) }
            y += layout.height / U
            y += lastExtra
            y += look.after
        }
        return Block(y, pieces, boxes, lines)
    }

    /**
     * A table's rows, each laid out from its top left (0 = the text's left edge), and whether it is a heading row.
     * A row is as tall as its tallest cell (or its set height); a cell merged down over rows makes the last of them
     * taller when it needs to.
     */
    /**
     * A cell whose text reads upwards or downwards: laid out as a line as long as the row is tall (a row of set height),
     * else as long as its longest line, and turned; the block is as tall as that line is long, as Word makes the row.
     */
    private fun turned(doc: OfficeDoc, paras: List<DocPara>, styles: DocStyles, page: DocPage, row: RowSpec?, cs: CellSpec, pictureBytes: (DocImage) -> ByteArray?): Block {
        fun lay(width: Float) = stack(doc, paras, styles, page, width, pictureBytes, null, 0f, 0f)
        val set = row?.height?.minus(cs.margin[1] + cs.margin[3])?.takeIf { it > 8f && row.exact }
        val length = set ?: lay(1000f).pieces.maxOfOrNull { p -> (p.firstLine..p.lastLine).maxOf { p.layout.getLineWidth(it) / U } + p.x }?.plus(1f)?.coerceIn(8f, 1000f) ?: 8f
        val flat = lay(length)
        // Upwards: the line's start at the cell's bottom; downwards: at its top, the first line on the right.
        val (tx, ty) = if (cs.turn < 0) 0f to length else flat.height to 0f
        return Block(length, flat.pieces.map { LaidPage.Piece(it.layout, it.firstLine, it.lastLine, it.x, it.y, cs.turn, tx, ty) }, emptyList(), emptyList(), flat.height)
    }

    private fun tableRows(doc: OfficeDoc, t: DocTable, styles: DocStyles, page: DocPage, width: Float, pictureBytes: (DocImage) -> ByteArray?,
        areas: MutableList<TableCellArea>? = null, grow: Float = 1f): List<Pair<Block, Boolean>> {
        val spec = styles.tableSpec(t)
        val count = spec.rows.maxOfOrNull { r -> r.cells.sumOf { it.span } } ?: 1
        val rawCols = spec.cols.takeIf { it.size >= count && it.sum() > 0f }?.map { it * grow } ?: List(count) { width / count }
        // The least each grid column can take: the longest word of the cells in it alone, and their margins.
        val least = if (rawCols.sum() <= width - spec.indent.coerceAtLeast(0f)) emptyList() else FloatArray(rawCols.size).also { least ->
            t.rows.forEachIndexed { ri, row ->
                var g = 0
                row.cells.forEachIndexed { ci, cell ->
                    val cs = spec.rows.getOrNull(ri)?.cells?.getOrNull(ci) ?: return@forEachIndexed
                    if (cs.span == 1 && g < least.size) least[g] = maxOf(least[g], longestWord(doc, cell.paras, styles) + cs.margin[0] + cs.margin[2] + 1f)
                    g += cs.span
                }
            }
        }.toList()
        val cols = fittedTableColumns(rawCols, width, spec.indent, least)
        val total = cols.sum()
        val x0 = spec.indent + when (spec.align) { DocAlign.Center -> (width - total) / 2; DocAlign.End -> width - total; else -> 0f }
        val colX = cols.runningFold(x0) { a, c -> a + c }
        // Each cell's place in the grid and its contents.
        class Laid(val row: Int, val cell: Int, val g: Int, val spec: CellSpec, val content: Block?)
        val cells = t.rows.mapIndexed { ri, row ->
            var g = 0
            row.cells.mapIndexedNotNull { ci, cell ->
                val cs = spec.rows.getOrNull(ri)?.cells?.getOrNull(ci) ?: return@mapIndexedNotNull null
                val w = cols.subList(g.coerceAtMost(cols.size), (g + cs.span).coerceAtMost(cols.size)).sum()
                val content = if (cs.merge == 'c') null else if (cs.turn != 0) turned(doc, cell.paras, styles, page, spec.rows.getOrNull(ri), cs, pictureBytes)
                    else stack(doc, cell.paras, styles, page, (w - cs.margin[0] - cs.margin[2]).coerceAtLeast(4f), pictureBytes, null, 0f, 0f, cs.margin[2], cs.text)
                Laid(ri, ci, g, cs, content).also { g += cs.span }
            }
        }
        val heights = FloatArray(t.rows.size) { ri ->
            val rs = spec.rows.getOrNull(ri)
            // A row holds its borders too: the one above it, and the one below the last row.
            val lines = (cells[ri].maxOfOrNull { it.spec.borders[0]?.width ?: 0f } ?: 0f) + if (ri == t.rows.size - 1) cells[ri].maxOfOrNull { it.spec.borders[2]?.width ?: 0f } ?: 0f else 0f
            val need = lines + (cells[ri].filter { it.spec.merge == 'n' }.maxOfOrNull { it.content!!.height + it.spec.margin[1] + it.spec.margin[3] } ?: 0f)
            when { rs?.height == null -> need; rs.exact -> rs.height; else -> maxOf(need, rs.height) }
        }
        // How many rows a cell that starts a vertical merge covers: the rows under it that go on in its column.
        fun down(c: Laid): Int { var k = 1; while (c.row + k < cells.size && cells[c.row + k].any { it.g == c.g && it.spec.merge == 'c' }) k++; return k }
        for (c in cells.flatten().filter { it.spec.merge == 'r' }) {
            val k = down(c)
            val have = (c.row until c.row + k).sumOf { heights[it].toDouble() }.toFloat()
            val need = c.content!!.height + c.spec.margin[1] + c.spec.margin[3]
            if (need > have) heights[c.row + k - 1] += need - have
        }
        var rowY = 0f
        return t.rows.indices.map { ri ->
            val pieces = ArrayList<LaidPage.Piece>(); val boxes = ArrayList<LaidPage.Box>(); val lines = ArrayList<LaidPage.Line>(); val edges = ArrayList<LaidPage.Line>()
            for (c in cells[ri]) {
                if (c.spec.merge == 'c') continue // drawn by the cell above that starts it
                val x = colX[c.g.coerceAtMost(colX.size - 1)]; val w = colX[(c.g + c.spec.span).coerceAtMost(colX.size - 1)] - x
                val h = if (c.spec.merge == 'r') (ri until ri + down(c)).sumOf { heights[it].toDouble() }.toFloat() else heights[ri]
                areas?.add(TableCellArea(ri, c.cell, x, rowY, w, h))
                c.spec.fill?.let { boxes += LaidPage.Box(x, 0f, w, h, it) }
                val content = c.content!!
                val free = h - content.height - c.spec.margin[1] - c.spec.margin[3]
                val m = c.spec.margin
                // Turned text is aligned across the cell, as Word turns its top with it: downwards, "top" is the right
                // side; upwards, the left. Its line starts at the top (downwards) or the bottom (upwards).
                val side = (w - m[0] - m[2] - content.across).coerceAtLeast(0f)
                val placed = when {
                    c.spec.turn > 0 -> content.moved(x + m[0] + when (c.spec.vAlign) { 'c' -> side / 2; 'b' -> 0f; else -> side }, m[1])
                    c.spec.turn < 0 -> content.moved(x + m[0] + when (c.spec.vAlign) { 'c' -> side / 2; 'b' -> side; else -> 0f }, (h - m[3] - content.height).coerceAtLeast(m[1]))
                    else -> content.moved(x + m[0], m[1] + when (c.spec.vAlign) { 'c' -> free / 2; 'b' -> free; else -> 0f }.coerceAtLeast(0f))
                }
                pieces += placed.pieces; boxes += placed.boxes; lines += placed.lines
                val b = c.spec.borders
                b[0]?.let { edges += LaidPage.Line(x, 0f, x + w, 0f, it.width, it.color) }
                b[1]?.let { edges += LaidPage.Line(x, 0f, x, h, it.width, it.color) }
                b[2]?.let { edges += LaidPage.Line(x, h, x + w, h, it.width, it.color) }
                b[3]?.let { edges += LaidPage.Line(x + w, 0f, x + w, h, it.width, it.color) }
            }
            // The cells' own lines under what is in them: a table in a cell reaching its edge keeps its line there
            // (the cell's line, and the next cell's, were drawn over it — a nested table lost its right side).
            (Block(heights[ri], pieces, boxes, edges + lines) to (spec.rows.getOrNull(ri)?.header == true)).also { rowY += heights[ri] }
        }
    }

    /** A floating picture's place on the page: from the page, the margin, or its paragraph (at [paraTop]). */
    private fun floatAt(img: DocImage, place: PictureXml.Place, paraTop: Float, page: DocPage, pictureBytes: (DocImage) -> ByteArray?): LaidPage.Floating {
        val (w, h) = img.widthPt to img.heightPt
        val x = when (place.alignH) {
            "center" -> (if (place.fromH == "page") page.width else page.textWidth) / 2 - w / 2 + (if (place.fromH == "page") 0f else page.left)
            "right", "outside" -> (if (place.fromH == "page") page.width else page.left + page.textWidth) - w
            "left", "inside" -> if (place.fromH == "page") 0f else page.left
            else -> (place.x ?: 0f) + if (place.fromH == "page") 0f else page.left
        }
        val y = when (place.alignV) {
            "center" -> (page.height - h) / 2
            "bottom" -> (if (place.fromV == "page") page.height else page.height - page.bottom) - h
            "top" -> if (place.fromV == "page") 0f else page.top
            else -> (place.y ?: 0f) + when (place.fromV) { "page" -> 0f; "margin" -> page.top; else -> paraTop }
        }
        val picture = pictureBytes(img)?.let { b -> decodePicture(b, 1600) }?.let { BitmapDrawable(null, it) } ?: ShapePicture.of(img.raw) ?: ColorDrawable(0xFFE3E3E3.toInt())
        return LaidPage.Floating(picture, x, y, w, h, place.wrap == PictureWrap.Behind)
    }

    /** A drop cap's letters as the pages draw them: in points, [x] from the margin, [baseline] from the paragraph's top. */
    class DropLetters(val text: String, val paint: TextPaint, val x: Float, val baseline: Float)
    /** Drawn where it was placed (the float's box only says it is there). */
    private class DropDrawable(val d: DropLetters, val left: Float, val top: Float) : android.graphics.drawable.Drawable() {
        override fun draw(canvas: Canvas) { canvas.drawText(d.text, left + d.x, top + d.baseline, d.paint) }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
    /** The first lines of a paragraph beside a drop cap: indented by its width. */
    private class DropMargin(val px: Int, val lines: Int) : LeadingMarginSpan.LeadingMarginSpan2 {
        override fun getLeadingMargin(first: Boolean) = if (first) px else 0 // "first": the lines it counts
        override fun drawLeadingMargin(c: Canvas, p: android.graphics.Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?) {}
        override fun getLeadingMarginLineCount() = lines
    }

    private fun paragraph(doc: OfficeDoc, p: DocPara, look: ParaLook, styles: DocStyles, page: DocPage, marker: String?, pictureBytes: (DocImage) -> ByteArray?,
        floating: MutableList<Pair<DocImage, PictureXml.Place>>, drops: MutableList<DropLetters>? = null /* the body's: a drop cap drawn */): Triple<StaticLayout, Float, Float> {
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
                r.sizePt?.let { mark(AbsoluteSizeSpan(Math.round(it * U).coerceAtLeast(1))) }
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
                val w = Math.round(img.widthPt * fit * U).coerceAtLeast(1); val h = Math.round(img.heightPt * fit * U).coerceAtLeast(1)
                // In a line the layout counts in twentieths of a point: a shape's outline is given in them too.
                val drawable = pictureBytes(img)?.let { b -> decodePicture(b, 1600) }?.let { BitmapDrawable(null, it) }
                    ?: ShapePicture.of(img.raw)?.let { ShapePicture(it.geometry, it.fill, it.line, it.lineWidth * U) } ?: ColorDrawable(0xFFE3E3E3.toInt())
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
            r.sizePt?.let { span(AbsoluteSizeSpan(Math.round(it * U).coerceAtLeast(1))) }
            if (r.script != 0) { span(if (r.script > 0) SuperscriptSpan() else SubscriptSpan()); span(RelativeSizeSpan(0.65f)) }
            (r.color ?: if (r.link != null) 0x0563C1 else null)?.let { span(ForegroundColorSpan(0xFF000000.toInt() or it)) }
            r.highlight?.let { span(BackgroundColorSpan(0xFF000000.toInt() or it)) }
            OfficeFonts.typeface(styles.fontName(r.font))?.let { span(TypefaceSpan(OfficeFonts.styled(it, r.bold || look.bold == true, r.italic || look.italic == true))) }
        }
        if (text.isEmpty()) text.append(" ") // an empty paragraph is still a line
        fun whole(s: Any) = text.setSpan(s, 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        // A paragraph may start left of the margin (a negative indent): it is laid out that much wider and drawn that much
        // further left (the layout has nothing left of 0).
        val shift = -minOf(0f, first, left)
        if (left + shift > 0f || first + shift > 0f) whole(LeadingMarginSpan.Standard(Math.round((first + shift) * U), Math.round((left + shift) * U)))
        look.lineExact?.let { exact -> if (!look.lineAtLeast || exact > size * 1.17f) whole(LineHeightSpan.Standard(Math.round(exact * U).coerceAtLeast(1))) }
        // Laid out in points and drawn scaled: linear, sub-pixel text, or letters snap to whole points and space unevenly.
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.LINEAR_TEXT_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
            color = look.color?.let { 0xFF000000.toInt() or it } ?: Color.BLACK
            textSize = size * U
            typeface = OfficeFonts.styled(OfficeFonts.typeface(look.font), look.bold == true, look.italic == true)
        }
        val width = Math.round((page.textWidth - look.right + shift) * U).coerceAtLeast(40)
        val aligned = p.align.takeIf { it != DocAlign.Start } ?: look.align ?: DocAlign.Start // its own, else its style's
        val align = when (aligned) { DocAlign.Center -> Layout.Alignment.ALIGN_CENTER; DocAlign.End -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
        // Word's single line is the font's height and its line gap; Android's leaves the gap out (rows came out 4% short).
        val multiple = if (look.lineExact == null) look.lineMultiple.coerceIn(0.5f, 5f) else 1f
        // The line gap of the font most of the text is in (a paragraph in Calibri may be all Times, letter by letter).
        val main = p.runs.filter { it.image == null }.groupBy { it.font to it.sizePt }.maxByOrNull { (_, rs) -> rs.sumOf { it.text.length } }?.value?.first()
        val gapPaint = TextPaint(paint).apply {
            main?.let { r -> OfficeFonts.typeface(styles.fontName(r.font))?.let { typeface = it }; r.sizePt?.let { textSize = it * U } }
        }
        val gap = if (look.lineExact == null) gapPaint.fontMetrics.leading * multiple else 0f
        fun build() = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(align)
            .setLineSpacing(gap, multiple).setIncludePad(false)
            .setJustificationMode(if (aligned == DocAlign.Justify) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE)
            .build()
        // Tabs, left to right (Android's layout knows only left stops): each goes to the next stop after where it
        // starts — the paragraph's own, a list's text position, then every half inch — the text after it lined up to
        // the stop's side (a table of contents: page numbers right-aligned, dots before them).
        val stops = (look.tabs + if (first < left) listOf(TabStop(left, 'l')) else emptyList()).sortedBy { it.pos }
        for (k in text.indices.filter { text[it] == '\t' }) {
            val x = build().getPrimaryHorizontal(k) / U - shift // in points from the margin, as tab stops are
            // A stop the text reaches exactly still counts (a number as wide as its hanging indent).
            val stop = stops.firstOrNull { it.pos >= x - 0.05f } ?: TabStop((kotlin.math.floor(x / TAB) + 1) * TAB, 'l')
            val end = (k + 1 until text.length).firstOrNull { text[it] == '\t' || text[it] == '\n' } ?: text.length
            val after = Layout.getDesiredWidth(text, k + 1, end, paint) / U
            val gap = when (stop.type) { 'r' -> stop.pos - x - after; 'c' -> stop.pos - x - after / 2; else -> stop.pos - x }
            text.setSpan(TabSpan(gap.coerceAtLeast(0f) * U, stop.leader), k, k + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        var laid = build()
        // A drop cap: its letters out of the first line, as tall as the lines they drop through (their capitals from
        // the first line's top to the last one's baseline), the first lines indented by their width.
        DropCap.of(p.keep)?.takeIf { drops != null }?.let { drop ->
            val from = marker?.let { it.length + 1 } ?: 0
            val n = drop.length.coerceAtMost(text.length - from)
            val letters = if (n > 0) text.substring(from, from + n) else ""
            if (letters.isBlank() || '\n' in letters || text.getSpans(from, from + n, ImageSpan::class.java).isNotEmpty()) return@let
            val r0 = p.runs.firstOrNull { it.image == null && it.text.isNotEmpty() }
            val letterPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = OfficeFonts.styled(r0?.let { OfficeFonts.typeface(styles.fontName(it.font)) } ?: OfficeFonts.typeface(look.font), r0?.bold == true || look.bold == true, r0?.italic == true || look.italic == true)
                color = (r0?.color ?: look.color)?.let { 0xFF000000.toInt() or it } ?: Color.BLACK
                textSize = 100f
            }
            val bounds = android.graphics.Rect(); letterPaint.getTextBounds("H", 0, 1, bounds)
            val capRatio = (bounds.height() / 100f).coerceAtLeast(0.3f)
            val base0 = laid.getLineBaseline(0) / U
            val lineStep = if (laid.lineCount > 1) (laid.getLineBaseline(1) - laid.getLineBaseline(0)) / U else laid.bottomPt(0) - laid.topPt(0)
            val baseN = base0 + (drop.lines - 1) * lineStep
            val capTop = base0 - (r0?.sizePt ?: size) * capRatio
            letterPaint.textSize = ((baseN - capTop) / capRatio).coerceAtLeast(size)
            val w = letterPaint.measureText(letters)
            text.delete(from, from + n)
            if (!drop.margin) text.setSpan(DropMargin(Math.round((w + drop.distance) * U), drop.lines), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            laid = build()
            drops!! += DropLetters(letters, letterPaint, (if (drop.margin) -w - drop.distance else 0f) + first, baseN - laid.topPt(0))
        }
        // Word spaces every line, the last one too; Android's layout leaves the last line unspaced.
        val lastExtra = if (look.lineExact == null && laid.lineCount > 0) laid.lineCount.minus(1).let { last -> (multiple - 1f) * (laid.bottomPt(last) - laid.topPt(last)) + gap / U } else 0f
        return Triple(laid, shift, lastExtra)
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

/**
 * A drawn shape (Word's wps:wsp, LibreOffice's draw:custom-shape): its outline path in a w×h box, with its fill (and
 * see-through) and outline as the file sets them (found 2026-09-29: circles around keys on a keyboard picture were grey
 * boxes; Insert › Shapes makes them). A shape of another kind is still a grey box.
 */
internal class ShapePicture(val geometry: String, val fill: Int?, val line: Int?, val lineWidth: Float) : android.graphics.drawable.Drawable() {
    fun drawAt(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.strokeJoin = android.graphics.Paint.Join.ROUND
        val path = path(geometry, w, h).apply { offset(x, y) }
        if (geometry !in LINES) fill?.let { paint.style = android.graphics.Paint.Style.FILL; paint.color = it; canvas.drawPath(path, paint) }
        line?.let { paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = lineWidth; paint.color = it; canvas.drawPath(path, paint) }
    }
    override fun draw(canvas: Canvas) { val b = bounds; drawAt(canvas, b.left.toFloat(), b.top.toFloat(), b.width().toFloat(), b.height().toFloat()) }
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
    @Deprecated("Drawable") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT

    companion object {
        /** Word's preset names; LibreOffice's draw:type names map to them. */
        val KINDS = listOf("rect", "roundRect", "ellipse", "triangle", "rtTriangle", "diamond", "pentagon", "hexagon", "octagon", "star5",
            "rightArrow", "leftArrow", "upArrow", "downArrow", "plus", "heart", "line")
        private val LINES = setOf("line", "straightConnector1")
        private val ODF = mapOf("rectangle" to "rect", "round-rectangle" to "roundRect", "ellipse" to "ellipse", "isosceles-triangle" to "triangle",
            "right-triangle" to "rtTriangle", "diamond" to "diamond", "pentagon" to "pentagon", "hexagon" to "hexagon", "octagon" to "octagon",
            "star5" to "star5", "right-arrow" to "rightArrow", "left-arrow" to "leftArrow", "up-arrow" to "upArrow", "down-arrow" to "downArrow",
            "cross" to "plus", "heart" to "heart", "mso-spt20" to "line")
        fun odfType(kind: String) = ODF.entries.firstOrNull { it.value == kind }?.key ?: "rectangle"

        /** The shape's outline in a [w]×[h] box from 0,0. */
        fun path(kind: String, w: Float, h: Float): android.graphics.Path = android.graphics.Path().apply {
            fun poly(vararg p: Float) { moveTo(p[0] * w, p[1] * h); var i = 2; while (i < p.size) { lineTo(p[i] * w, p[i + 1] * h); i += 2 }; close() }
            fun ring(n: Int, start: Double = -Math.PI / 2) = poly(*FloatArray(n * 2) { k -> val a = start + 2 * Math.PI * (k / 2) / n
                if (k % 2 == 0) (0.5 + 0.5 * Math.cos(a)).toFloat() else (0.5 + 0.5 * Math.sin(a)).toFloat() })
            when (kind) {
                "ellipse" -> addOval(0f, 0f, w, h, android.graphics.Path.Direction.CW)
                "roundRect" -> addRoundRect(0f, 0f, w, h, minOf(w, h) / 6, minOf(w, h) / 6, android.graphics.Path.Direction.CW)
                "triangle" -> poly(0.5f, 0f, 1f, 1f, 0f, 1f)
                "rtTriangle" -> poly(0f, 0f, 1f, 1f, 0f, 1f)
                "diamond" -> poly(0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, 0.5f)
                "pentagon" -> ring(5)
                "hexagon" -> poly(0.25f, 0f, 0.75f, 0f, 1f, 0.5f, 0.75f, 1f, 0.25f, 1f, 0f, 0.5f)
                "octagon" -> poly(0.29f, 0f, 0.71f, 0f, 1f, 0.29f, 1f, 0.71f, 0.71f, 1f, 0.29f, 1f, 0f, 0.71f, 0f, 0.29f)
                "star5" -> poly(*FloatArray(20) { k -> val i = k / 2; val a = -Math.PI / 2 + Math.PI * i / 5; val r = if (i % 2 == 0) 0.5 else 0.2
                    if (k % 2 == 0) (0.5 + r * Math.cos(a)).toFloat() else (0.5 + r * Math.sin(a) * 1.05 + 0.03).toFloat() })
                "rightArrow" -> poly(0f, 0.25f, 0.6f, 0.25f, 0.6f, 0f, 1f, 0.5f, 0.6f, 1f, 0.6f, 0.75f, 0f, 0.75f)
                "leftArrow" -> poly(1f, 0.25f, 0.4f, 0.25f, 0.4f, 0f, 0f, 0.5f, 0.4f, 1f, 0.4f, 0.75f, 1f, 0.75f)
                "upArrow" -> poly(0.25f, 1f, 0.25f, 0.4f, 0f, 0.4f, 0.5f, 0f, 1f, 0.4f, 0.75f, 0.4f, 0.75f, 1f)
                "downArrow" -> poly(0.25f, 0f, 0.25f, 0.6f, 0f, 0.6f, 0.5f, 1f, 1f, 0.6f, 0.75f, 0.6f, 0.75f, 0f)
                "plus" -> poly(0.35f, 0f, 0.65f, 0f, 0.65f, 0.35f, 1f, 0.35f, 1f, 0.65f, 0.65f, 0.65f, 0.65f, 1f, 0.35f, 1f, 0.35f, 0.65f, 0f, 0.65f, 0f, 0.35f, 0.35f, 0.35f)
                "heart" -> { moveTo(0.5f * w, h); cubicTo(-0.1f * w, 0.55f * h, 0.05f * w, -0.05f * h, 0.5f * w, 0.25f * h)
                    cubicTo(0.95f * w, -0.05f * h, 1.1f * w, 0.55f * h, 0.5f * w, h); close() }
                "line", "straightConnector1" -> { moveTo(0f, 0f); lineTo(w, h) }
                else -> addRect(0f, 0f, w, h, android.graphics.Path.Direction.CW)
            }
        }

        /** From a drawing's XML, when it is a shape of those kinds (Word's, or a custom shape Writer made); null otherwise. */
        fun of(raw: String?): ShapePicture? {
            if (raw == null) return null
            if (raw.startsWith("<draw:custom-shape")) {
                val kind = Regex("""draw:type="([^"]+)"""").find(raw)?.groupValues?.get(1)?.let { ODF[it] } ?: return null
                // Writer's own shapes carry their look in their style's name: Tetra_20_shape_<fill|none>_<line|none>_<width×100>_<wrap>.
                val m = Regex("""draw:style-name="Tetra_20_shape_(\w+?)_(\w+?)_(\d+)_""").find(raw)
                fun hex(v: String?) = v?.takeIf { it != "none" }?.let { t -> t.toLongOrNull(16)?.let { if (t.length == 8) it.toInt() else (0xFF000000 or it).toInt() } }
                return if (m != null) ShapePicture(kind, hex(m.groupValues[1]), hex(m.groupValues[2]), m.groupValues[3].toInt() / 100f)
                    else ShapePicture(kind, 0xFF729FCF.toInt(), 0xFF3465A4.toInt(), 1f) // LibreOffice's own default look
            }
            val sp = Regex("""<wps:spPr>([\s\S]*?)</wps:spPr>""").find(raw)?.groupValues?.get(1) ?: return null
            val geometry = Regex("""<a:prstGeom prst="(\w+)"""").find(sp)?.groupValues?.get(1)?.takeIf { it in KINDS || it in LINES } ?: return null
            fun color(xml: String?): Int? {
                if (xml == null || "<a:noFill" in xml) return null
                val m = Regex("""<a:srgbClr val="([0-9A-Fa-f]{6})"(?:\s*/>|>([\s\S]*?)</a:srgbClr>)""").find(xml) ?: return null
                val alpha = Regex("""<a:alpha val="(\d+)"""").find(m.groupValues[2])?.groupValues?.get(1)?.toIntOrNull()?.let { Math.round(it * 255 / 100000f) } ?: 255
                return (alpha shl 24) or m.groupValues[1].toInt(16)
            }
            val ln = Regex("""<a:ln\b([^>]*)>([\s\S]*?)</a:ln>""").find(sp)
            // What is before the outline is the fill (spPr's order: geometry, fill, outline).
            val fill = color(sp.substringBefore("<a:ln").substringAfter("</a:prstGeom>", ""))
            val width = ln?.groupValues?.get(1)?.let { Regex("""w="(\d+)"""").find(it)?.groupValues?.get(1)?.toFloatOrNull() }?.div(12700f) ?: 0.75f
            return ShapePicture(geometry, fill, color(ln?.groupValues?.get(2)), width)
        }
    }
}
