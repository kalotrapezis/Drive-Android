package com.kalotrapezis.drive

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

/*
 * Office › Writer (asked 2026-09-27): .docx and .odt, read into what Writer can show and edit, and written back.
 *
 * What Writer keeps: the text; bold, italic, underline, strikethrough, text size, text colour, highlight and links;
 * headings 1–6, bulleted and numbered lists, and alignment. Everything outside the body — page size and margins,
 * headers and footers, the document's own styles and fonts — is kept by writing the new body into the original
 * package, never by rebuilding the file. Anything in the body Writer cannot keep (images, tables, comments, tracked
 * changes…) is named in [OfficeDoc.unsupported]; such a file opens, and is saved only as a copy — the original is
 * never overwritten (the user's rule). Nothing here depends on Android, so it is tested on the computer.
 */

internal enum class DocAlign { Start, Center, End, Justify }

internal data class DocRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val sizePt: Float? = null, // null: the document's own size
    val color: Int? = null, // 0xRRGGBB
    val highlight: Int? = null, // 0xRRGGBB
    val link: String? = null,
) {
    fun sameLook(o: DocRun) = copy(text = "") == o.copy(text = "")
}

/** A list item: bulleted or numbered, how deep (0 = top), and the number it starts at when that is not 1. */
internal data class DocList(val ordered: Boolean, val level: Int, val start: Int? = null)

internal data class DocPara(val runs: List<DocRun>, val heading: Int = 0, val list: DocList? = null, val align: DocAlign = DocAlign.Start) {
    val text: String get() = runs.joinToString("") { it.text }
}

/**
 * A document as Writer holds it. [basePt] is the document's normal text size; [unsupported] names, in plain words,
 * what the file has that Writer cannot keep — empty means saving over the file loses nothing.
 */
internal data class OfficeDoc(
    val paras: List<DocPara>,
    val unsupported: Set<String> = emptySet(),
    val basePt: Float = 11f,
) {
    /** What would go missing on saving over the file: content, not just its look. Any of it → Save as a copy only. */
    val blocking: Set<String> get() = unsupported - SIMPLIFIED
    /** What saving over the file simplifies to Writer's own (agreed 2026-09-27, option b): said, not a reason for a copy. */
    val simplified: Set<String> get() = unsupported intersect SIMPLIFIED

    companion object {
        val SIMPLIFIED = setOf("paragraph spacing and indents", "fonts", "tab stops")
    }
}

internal enum class OfficeKind(val extension: String, val mime: String) {
    Docx("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    Odt("odt", "application/vnd.oasis.opendocument.text");

    companion object {
        fun of(name: String): OfficeKind? = entries.firstOrNull { name.endsWith(".${it.extension}", ignoreCase = true) }
    }
}

internal object OfficeFormats {
    /** Reads a document. Throws when the bytes are not a document Writer can read at all (encrypted, damaged…). */
    fun read(bytes: ByteArray, kind: OfficeKind): OfficeDoc {
        val parts = unzip(bytes)
        return when (kind) {
            OfficeKind.Docx -> DocxReader(parts).read()
            OfficeKind.Odt -> OdtReader(parts).read()
        }
    }

    /** The file's bytes: [doc] written into [original] (the file it came from), or into a new blank document. */
    fun write(doc: OfficeDoc, kind: OfficeKind, original: ByteArray? = null): ByteArray = when (kind) {
        OfficeKind.Docx -> DocxWriter.write(doc, original?.let { runCatching { unzip(it) }.getOrNull() }?.takeIf { DocxWriter.canReuse(it) } ?: DocxWriter.blank())
        OfficeKind.Odt -> OdtWriter.write(doc, original?.let { runCatching { unzip(it) }.getOrNull() }?.takeIf { OdtWriter.canReuse(it) } ?: OdtWriter.blank())
    }

    /** Adjacent runs that look the same become one, and empty runs go: what the editor gives back is compared cleanly. */
    fun tidy(runs: List<DocRun>): List<DocRun> {
        val out = ArrayList<DocRun>()
        for (r in runs) {
            if (r.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && last.sameLook(r)) out[out.size - 1] = last.copy(text = last.text + r.text) else out.add(r)
        }
        return out
    }

    // --- shared helpers ---

    internal fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val parts = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) parts[e.name] = zip.readBytes()
            }
        }
        require(parts.isNotEmpty()) { "Not a document." }
        return parts
    }

    /** A zip of [parts] in their order; [stored] is written first and uncompressed (ODF's mimetype must be). */
    internal fun zip(parts: Map<String, ByteArray>, stored: String? = null): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val names = listOfNotNull(stored?.takeIf { it in parts }) + parts.keys.filter { it != stored }
            for (name in names) {
                val data = parts.getValue(name)
                val e = ZipEntry(name)
                if (name == stored) {
                    e.method = ZipEntry.STORED; e.size = data.size.toLong(); e.compressedSize = data.size.toLong()
                    e.crc = CRC32().apply { update(data) }.value
                }
                zip.putNextEntry(e); zip.write(data); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    internal fun parse(bytes: ByteArray): org.w3c.dom.Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        f.isExpandEntityReferences = false
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    internal fun esc(s: String): String = buildString(s.length) {
        for (c in s) when {
            c == '&' -> append("&amp;")
            c == '<' -> append("&lt;")
            c == '>' -> append("&gt;")
            c == '"' -> append("&quot;")
            c == '\t' || c == '\n' || c == '\r' || c >= ' ' -> if (c != '￾' && c != '￿') append(c)
        }
    }

    internal fun hex(rgb: Int) = "%06X".format(rgb and 0xFFFFFF)
    internal fun parseHex(s: String?): Int? = s?.removePrefix("#")?.takeIf { it.length == 6 }?.toIntOrNull(16)
}

internal fun Element.kids(): List<Element> {
    val out = ArrayList<Element>()
    var n = firstChild
    while (n != null) { if (n is Element) out.add(n); n = n.nextSibling }
    return out
}
internal fun Element.kid(ns: String, name: String): Element? = kids().firstOrNull { it.namespaceURI == ns && it.localName == name }
internal fun Element.attr(ns: String, name: String): String? = getAttributeNS(ns, name).takeIf { hasAttributeNS(ns, name) }

// ============================================================================================ Word (.docx)

private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val REL_HYPERLINK = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
private const val REL_NUMBERING = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering"
private const val REL_STYLES = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"

/** Word's fixed highlight colours; a highlight is written as the nearest of them. */
internal val WORD_HIGHLIGHTS = linkedMapOf(
    "yellow" to 0xFFFF00, "green" to 0x00FF00, "cyan" to 0x00FFFF, "magenta" to 0xFF00FF, "blue" to 0x0000FF,
    "red" to 0xFF0000, "darkBlue" to 0x000080, "darkCyan" to 0x008080, "darkGreen" to 0x008000, "darkMagenta" to 0x800080,
    "darkRed" to 0x800000, "darkYellow" to 0x808000, "darkGray" to 0x808080, "lightGray" to 0xC0C0C0, "black" to 0x000000, "white" to 0xFFFFFF,
)

private fun documentPath(parts: Map<String, ByteArray>): String {
    val rels = parts["_rels/.rels"] ?: return "word/document.xml"
    val root = OfficeFormats.parse(rels).documentElement
    return root.kids().firstOrNull { it.getAttribute("Type").endsWith("/officeDocument") }?.getAttribute("Target")?.removePrefix("/") ?: "word/document.xml"
}
private fun relsPath(part: String) = part.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } + "_rels/" + part.substringAfterLast('/') + ".rels"

private class DocxReader(val parts: Map<String, ByteArray>) {
    val notes = linkedSetOf<String>()
    val docPath = documentPath(parts)
    val dir = docPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    val links = HashMap<String, String>()
    val styleName = HashMap<String, String>() // styleId → name, lowercased
    var defaultParaStyle: String? = null
    var basePt = 10f
    val numAbstract = HashMap<String, String>() // numId → abstractNumId
    val numStart = HashMap<Pair<String, Int>, Int>() // (numId, level) → startOverride
    val levels = HashMap<Pair<String, Int>, Pair<String, Int>>() // (abstractNumId, level) → (numFmt, start)
    val counters = HashMap<String, IntArray>() // numId → the count at each level, as Word numbers them

    fun read(): OfficeDoc {
        val xml = parts[docPath] ?: error("Not a Word document.")
        val root = OfficeFormats.parse(xml).documentElement
        if (root.namespaceURI != W) error("This Word document is in a format Writer does not read (Strict Open XML).")
        if ("<w:body>" !in String(xml, Charsets.UTF_8)) notes += "an unusual file layout"
        parts[relsPath(docPath)]?.let { r ->
            OfficeFormats.parse(r).documentElement.kids().forEach { if (it.getAttribute("Type") == REL_HYPERLINK) links[it.getAttribute("Id")] = it.getAttribute("Target") }
        }
        readStyles(); readNumbering()
        val body = root.kid(W, "body") ?: error("Not a Word document.")
        val paras = ArrayList<DocPara>()
        block(body, paras)
        return OfficeDoc(paras.ifEmpty { listOf(DocPara(emptyList())) }, notes, basePt)
    }

    fun readStyles() {
        val root = parts[dir + "styles.xml"]?.let { OfficeFormats.parse(it).documentElement } ?: return
        root.kid(W, "docDefaults")?.kid(W, "rPrDefault")?.kid(W, "rPr")?.kid(W, "sz")?.attr(W, "val")?.toFloatOrNull()?.let { basePt = it / 2 }
        for (s in root.kids().filter { it.localName == "style" }) {
            val id = s.attr(W, "styleId") ?: continue
            styleName[id] = (s.kid(W, "name")?.attr(W, "val") ?: id).lowercase()
            if (s.attr(W, "type") == "paragraph" && s.attr(W, "default").let { it == "1" || it == "true" }) {
                defaultParaStyle = id
                s.kid(W, "rPr")?.kid(W, "sz")?.attr(W, "val")?.toFloatOrNull()?.let { basePt = it / 2 }
            }
        }
    }

    fun readNumbering() {
        val root = parts[dir + "numbering.xml"]?.let { OfficeFormats.parse(it).documentElement } ?: return
        for (e in root.kids()) when (e.localName) {
            "abstractNum" -> {
                val id = e.attr(W, "abstractNumId") ?: continue
                if (e.kid(W, "numStyleLink") != null || e.kid(W, "styleLink") != null) notes += "other list formats"
                for (lvl in e.kids().filter { it.localName == "lvl" }) {
                    val i = lvl.attr(W, "ilvl")?.toIntOrNull() ?: continue
                    levels[id to i] = (lvl.kid(W, "numFmt")?.attr(W, "val") ?: "decimal") to (lvl.kid(W, "start")?.attr(W, "val")?.toIntOrNull() ?: 1)
                }
            }
            "num" -> {
                val id = e.attr(W, "numId") ?: continue
                numAbstract[id] = e.kid(W, "abstractNumId")?.attr(W, "val") ?: continue
                e.kids().filter { it.localName == "lvlOverride" }.forEach { o ->
                    val i = o.attr(W, "ilvl")?.toIntOrNull() ?: return@forEach
                    o.kid(W, "startOverride")?.attr(W, "val")?.toIntOrNull()?.let { numStart[id to i] = it }
                    if (o.kid(W, "lvl") != null) notes += "other list formats"
                }
            }
        }
    }

    /** The body, a table cell or a content control: paragraphs, and what Writer cannot keep, named. */
    fun block(parent: Element, out: MutableList<DocPara>) {
        for (e in parent.kids()) {
            if (e.namespaceURI != W) { if (e.localName != "AlternateContent") notes += "other content" ; continue }
            when (e.localName) {
                "p" -> paragraph(e, out)
                "tbl" -> { notes += "tables"; e.kids().filter { it.localName == "tr" }.forEach { tr -> tr.kids().filter { it.localName == "tc" }.forEach { block(it, out) } } }
                "sdt" -> { notes += "content controls"; e.kid(W, "sdtContent")?.let { block(it, out) } }
                "customXml" -> { notes += "content controls"; block(e, out) }
                "ins", "moveTo" -> { notes += "tracked changes"; block(e, out) }
                "del", "moveFrom" -> notes += "tracked changes"
                "sectPr", "bookmarkStart", "bookmarkEnd", "proofErr", "permStart", "permEnd", "tcPr", "trPr", "tblPr", "tblGrid" -> Unit
                "commentRangeStart", "commentRangeEnd" -> notes += "comments"
                "altChunk" -> notes += "embedded objects"
                else -> if (e.localName.endsWith("RangeStart") || e.localName.endsWith("RangeEnd")) notes += "tracked changes" else notes += "other content"
            }
        }
    }

    fun paragraph(p: Element, out: MutableList<DocPara>) {
        var heading = 0
        var list: DocList? = null
        var align = DocAlign.Start
        p.kid(W, "pPr")?.let { pPr ->
            for (e in pPr.kids()) when (e.localName) {
                "pStyle" -> heading = paragraphStyle(e.attr(W, "val") ?: "")
                "numPr" -> list = listItem(e)
                "jc" -> align = when (e.attr(W, "val")) { "center" -> DocAlign.Center; "right", "end" -> DocAlign.End; "both", "distribute", "thaiDistribute" -> DocAlign.Justify; else -> DocAlign.Start }
                "rPr", "widowControl", "keepNext", "keepLines", "snapToGrid", "autoSpaceDE", "autoSpaceDN", "adjustRightInd", "contextualSpacing",
                "suppressAutoHyphens", "kinsoku", "wordWrap", "overflowPunct", "topLinePunct", "textAlignment", "outlineLvl", "divId", "cnfStyle",
                "suppressLineNumbers", "mirrorIndents", "bidi", "textDirection", "textboxTightWrap" -> Unit
                "spacing", "ind" -> if (e.attributes.length > 0) notes += "paragraph spacing and indents" // an empty one changes nothing
                "pageBreakBefore" -> if (e.attr(W, "val").let { it == null || it == "1" || it == "true" }) notes += "page breaks"
                "sectPr" -> notes += "section breaks"
                "tabs" -> if (e.kids().isNotEmpty()) notes += "tab stops"
                "pBdr" -> if (e.kids().any { it.attr(W, "val").let { v -> v != null && v != "none" && v != "nil" } }) notes += "borders and shading"
                "shd" -> if (e.attr(W, "fill").let { it != null && it != "auto" && it != "FFFFFF" }) notes += "borders and shading"
                "framePr" -> notes += "shapes and text boxes"
                "pPrChange" -> notes += "tracked changes"
                else -> notes += "other paragraph formatting"
            }
        }
        val runs = ArrayList<DocRun>()
        val done = ArrayList<DocPara>()
        fun breakLine() { done += DocPara(OfficeFormats.tidy(runs), heading, list, align); runs.clear(); list = list?.let { null } }
        fun content(parent: Element, link: String?) {
            for (e in parent.kids()) {
                if (e.namespaceURI != W) { notes += if (e.localName == "oMath" || e.localName == "oMathPara") "equations" else "other content"; continue }
                when (e.localName) {
                    "pPr" -> Unit
                    "r" -> run(e, link, runs, ::breakLine)
                    "hyperlink" -> {
                        val url = e.attr(R, "id")?.let { links[it] }
                        if (url == null) notes += "links inside the document"
                        content(e, url ?: link)
                    }
                    "ins", "moveTo" -> { notes += "tracked changes"; content(e, link) }
                    "del", "moveFrom" -> notes += "tracked changes"
                    "fldSimple" -> { notes += "fields (page numbers, dates, contents)"; content(e, link) }
                    "smartTag", "customXml" -> content(e, link)
                    "sdt" -> { notes += "content controls"; e.kid(W, "sdtContent")?.let { content(it, link) } }
                    "commentRangeStart", "commentRangeEnd" -> notes += "comments"
                    "bookmarkStart", "bookmarkEnd", "proofErr", "permStart", "permEnd", "lastRenderedPageBreak" -> Unit
                    else -> if (e.localName.endsWith("RangeStart") || e.localName.endsWith("RangeEnd")) notes += "tracked changes" else notes += "other content"
                }
            }
        }
        content(p, null)
        done += DocPara(OfficeFormats.tidy(runs), heading, list, align)
        out += done
    }

    fun run(r: Element, link: String?, runs: MutableList<DocRun>, breakLine: () -> Unit) {
        var look = DocRun("", link = link)
        r.kid(W, "rPr")?.let { rPr ->
            for (e in rPr.kids()) {
                val on = e.attr(W, "val").let { it == null || it == "1" || it == "true" || it == "on" }
                when (e.localName) {
                    "b" -> look = look.copy(bold = on)
                    "i" -> look = look.copy(italic = on)
                    "strike" -> look = look.copy(strike = on)
                    "u" -> look = look.copy(underline = e.attr(W, "val") != "none")
                    "sz" -> e.attr(W, "val")?.toFloatOrNull()?.let { look = look.copy(sizePt = (it / 2).takeIf { pt -> pt != basePt }) }
                    "color" -> look = look.copy(color = OfficeFormats.parseHex(e.attr(W, "val")))
                    "highlight" -> look = look.copy(highlight = WORD_HIGHLIGHTS[e.attr(W, "val")])
                    "rStyle" -> when (styleName[e.attr(W, "val")] ?: e.attr(W, "val")?.lowercase()) {
                        "strong" -> look = look.copy(bold = true)
                        "emphasis" -> look = look.copy(italic = true)
                        "hyperlink", "internet link", "default paragraph font" -> Unit
                        else -> notes += "character styles"
                    }
                    "rFonts" -> if (listOf("ascii", "hAnsi", "asciiTheme", "hAnsiTheme").any { e.hasAttributeNS(W, it) }) notes += "fonts"
                    "bCs", "iCs", "szCs", "lang", "noProof", "webHidden", "kern", "rtl", "cs", "specVanish", "eastAsianLayout", "snapToGrid" -> Unit
                    "vertAlign" -> if (e.attr(W, "val") != "baseline") notes += "superscript and subscript"
                    "shd" -> if (e.attr(W, "fill").let { it != null && it != "auto" && it != "FFFFFF" }) notes += "borders and shading"
                    "bdr" -> notes += "borders and shading"
                    "vanish" -> if (on) notes += "hidden text"
                    "rPrChange" -> notes += "tracked changes"
                    else -> if (on) notes += "other text effects"
                }
            }
        }
        for (e in r.kids()) {
            if (e.namespaceURI != W) { notes += "other content"; continue }
            when (e.localName) {
                "rPr" -> Unit
                "t" -> runs += look.copy(text = e.textContent)
                "tab", "ptab" -> runs += look.copy(text = "\t")
                "noBreakHyphen" -> runs += look.copy(text = "‑")
                "softHyphen", "lastRenderedPageBreak" -> Unit
                "br", "cr" -> when (e.attr(W, "type")) {
                    "page" -> { notes += "page breaks"; breakLine() }
                    "column" -> { notes += "columns"; breakLine() }
                    else -> { notes += "line breaks inside a paragraph"; breakLine() }
                }
                "drawing", "pict" -> notes += "images"
                "object" -> notes += "embedded objects"
                "fldChar", "instrText" -> notes += "fields (page numbers, dates, contents)"
                "delText", "delInstrText" -> notes += "tracked changes"
                "footnoteReference", "endnoteReference" -> notes += "footnotes"
                "commentReference", "annotationRef" -> notes += "comments"
                "sym" -> notes += "symbols"
                else -> notes += "other content"
            }
        }
    }

    fun paragraphStyle(id: String): Int {
        val name = styleName[id] ?: id.lowercase()
        Regex("""heading ?([1-9])""").matchEntire(name)?.let { m -> return m.groupValues[1].toInt().coerceAtMost(6).also { if (m.groupValues[1].toInt() > 6) notes += "headings below level 6" } }
        if (id == defaultParaStyle || name == "normal" || name == "list paragraph" || name == "default paragraph style") return 0
        notes += "the paragraph style “${styleName[id]?.let { name.replaceFirstChar { it.uppercase() } } ?: id}”"
        return 0
    }

    fun listItem(numPr: Element): DocList? {
        val numId = numPr.kid(W, "numId")?.attr(W, "val") ?: return null
        if (numId == "0") return null
        val level = numPr.kid(W, "ilvl")?.attr(W, "val")?.toIntOrNull() ?: 0
        val abstract = numAbstract[numId] ?: return null
        val (fmt, start) = levels[abstract to level] ?: ("decimal" to 1)
        val ordered = when (fmt) {
            "bullet" -> false
            "decimal" -> true
            "none" -> { notes += "other list formats"; false }
            else -> { notes += "letter or roman numbering"; true }
        }
        // Word counts on through the whole document for one list; a number is kept by saying where each part starts.
        val c = counters.getOrPut(numId) { IntArray(9) { Int.MIN_VALUE } }
        val first = numStart[numId to level] ?: start
        c[level.coerceIn(0, 8)] = if (c[level.coerceIn(0, 8)] == Int.MIN_VALUE) first else c[level.coerceIn(0, 8)] + 1
        for (deeper in level + 1 until 9) c[deeper] = Int.MIN_VALUE
        return DocList(ordered, level.coerceIn(0, 8), c[level.coerceIn(0, 8)].takeIf { ordered })
    }
}

private object DocxWriter {
    fun canReuse(parts: Map<String, ByteArray>): Boolean = runCatching {
        String(parts.getValue(documentPath(parts)), Charsets.UTF_8).let { "<w:body>" in it && "</w:body>" in it }
    }.getOrDefault(false)

    fun blank(): LinkedHashMap<String, ByteArray> {
        val ns = """xmlns:w="$W" xmlns:r="$R""""
        fun heading(n: Int, halfPoints: Int) = """<w:style w:type="paragraph" w:styleId="Heading$n"><w:name w:val="heading $n"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:uiPriority w:val="9"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="80"/><w:outlineLvl w:val="${n - 1}"/></w:pPr><w:rPr><w:b/><w:sz w:val="$halfPoints"/></w:rPr></w:style>"""
        val m = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
            "word/_rels/document.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$REL_STYLES" Target="styles.xml"/><Relationship Id="rId2" Type="$REL_NUMBERING" Target="numbering.xml"/></Relationships>""",
            "word/document.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document $ns><w:body><w:p/><w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>""",
            "word/styles.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles $ns><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Calibri" w:cs="Calibri"/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style><w:style w:type="character" w:default="1" w:styleId="DefaultParagraphFont"><w:name w:val="Default Paragraph Font"/><w:uiPriority w:val="1"/><w:semiHidden/></w:style><w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:ind w:left="720"/><w:contextualSpacing/></w:pPr></w:style>${heading(1, 36)}${heading(2, 30)}${heading(3, 26)}${heading(4, 24)}${heading(5, 22)}${heading(6, 22)}<w:style w:type="character" w:styleId="Hyperlink"><w:name w:val="Hyperlink"/><w:basedOn w:val="DefaultParagraphFont"/><w:rPr><w:color w:val="0563C1"/><w:u w:val="single"/></w:rPr></w:style></w:styles>""",
            "word/numbering.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:numbering $ns></w:numbering>""",
        )
        return LinkedHashMap(m.mapValues { it.value.toByteArray() })
    }

    fun write(doc: OfficeDoc, source: LinkedHashMap<String, ByteArray>): ByteArray {
        val parts = LinkedHashMap(source)
        val docPath = documentPath(parts)
        val dir = docPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val relsPath = relsPath(docPath)
        var rels = parts[relsPath]?.let { String(it, Charsets.UTF_8) }
            ?: """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>"""
        var types = String(parts.getValue("[Content_Types].xml"), Charsets.UTF_8)
        val usedIds = Regex("""Id="([^"]+)"""").findAll(rels).map { it.groupValues[1] }.toMutableSet()
        fun newRel(type: String, target: String, external: Boolean): String {
            val id = generateSequence(1) { it + 1 }.map { "rIdT$it" }.first { it !in usedIds }
            usedIds += id
            rels = rels.replace("</Relationships>", """<Relationship Id="$id" Type="$type" Target="${OfficeFormats.esc(target)}"${if (external) " TargetMode=\"External\"" else ""}/></Relationships>""")
            return id
        }
        fun ensurePart(name: String, relType: String, contentType: String, empty: String) {
            if (dir + name in parts) return
            parts[dir + name] = empty.toByteArray()
            if (!rels.contains("Target=\"$name\"")) newRel(relType, name, false)
            if (!types.contains("PartName=\"/$dir$name\"")) types = types.replace("</Types>", """<Override PartName="/$dir$name" ContentType="$contentType"/></Types>""")
        }
        val ns = """xmlns:w="$W" xmlns:r="$R""""
        ensurePart("styles.xml", REL_STYLES, "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles $ns></w:styles>""")
        if (doc.paras.any { it.list != null })
            ensurePart("numbering.xml", REL_NUMBERING, "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:numbering $ns></w:numbering>""")

        // Styles: the headings and Hyperlink this document already has, found by name; any missing are added.
        var styles = String(parts.getValue(dir + "styles.xml"), Charsets.UTF_8)
        val styleRoot = OfficeFormats.parse(styles.toByteArray()).documentElement
        val byName = styleRoot.kids().filter { it.localName == "style" }.associate { (it.kid(W, "name")?.attr(W, "val") ?: "").lowercase() to (it.attr(W, "styleId") ?: "") }
        val ids = byName.values.toMutableSet()
        val normal = styleRoot.kids().firstOrNull { it.localName == "style" && it.attr(W, "type") == "paragraph" && it.attr(W, "default").let { d -> d == "1" || d == "true" } }?.attr(W, "styleId")
        fun styleId(name: String, preferred: String, definition: (String) -> String): String = byName[name] ?: run {
            val id = if (preferred in ids) "Tetra$preferred" else preferred
            ids += id
            styles = styles.replace("</w:styles>", definition(id) + "</w:styles>")
            id
        }
        val headingIds = (1..6).filter { n -> doc.paras.any { it.heading == n } }.associateWith { n ->
            styleId("heading $n", "Heading$n") { id -> """<w:style w:type="paragraph" w:styleId="$id"><w:name w:val="heading $n"/>${normal?.let { "<w:basedOn w:val=\"$it\"/><w:next w:val=\"$it\"/>" } ?: ""}<w:uiPriority w:val="9"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="80"/><w:outlineLvl w:val="${n - 1}"/></w:pPr><w:rPr><w:b/><w:sz w:val="${listOf(36, 30, 26, 24, 22, 22)[n - 1]}"/></w:rPr></w:style>""" }
        }
        val hyperlinkId = if (doc.paras.any { p -> p.runs.any { it.link != null } })
            styleId("hyperlink", "Hyperlink") { id -> """<w:style w:type="character" w:styleId="$id"><w:name w:val="Hyperlink"/><w:rPr><w:color w:val="0563C1"/><w:u w:val="single"/></w:rPr></w:style>""" } else null
        parts[dir + "styles.xml"] = styles.toByteArray()

        // Lists: two new list definitions (bullets, numbers) and one numbering instance per list, so each numbered
        // list counts from its own start, as it did in the editor.
        val numIds = HashMap<Int, String>() // paragraph index → numId
        if (doc.paras.any { it.list != null }) {
            var numbering = String(parts.getValue(dir + "numbering.xml"), Charsets.UTF_8)
            val nroot = OfficeFormats.parse(numbering.toByteArray()).documentElement
            var nextAbstract = (nroot.kids().filter { it.localName == "abstractNum" }.mapNotNull { it.attr(W, "abstractNumId")?.toIntOrNull() }.maxOrNull() ?: 0) + 1
            var nextNum = (nroot.kids().filter { it.localName == "num" }.mapNotNull { it.attr(W, "numId")?.toIntOrNull() }.maxOrNull() ?: 0) + 1
            val bulletChars = listOf("•", "◦", "▪")
            fun abstractNum(id: Int, ordered: Boolean) = buildString {
                append("""<w:abstractNum w:abstractNumId="$id"><w:multiLevelType w:val="hybridMultilevel"/>""")
                for (l in 0..8) {
                    val indent = 720 * (l + 1)
                    if (ordered) append("""<w:lvl w:ilvl="$l"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%${l + 1}."/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="$indent" w:hanging="360"/></w:pPr></w:lvl>""")
                    else append("""<w:lvl w:ilvl="$l"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="${bulletChars[l % 3]}"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="$indent" w:hanging="360"/></w:pPr></w:lvl>""")
                }
                append("</w:abstractNum>")
            }
            val bulletAbstract = nextAbstract++
            val orderedAbstract = nextAbstract++
            val nums = StringBuilder()
            fun num(abstract: Int, start: Int?): String {
                val id = nextNum++
                nums.append("""<w:num w:numId="$id"><w:abstractNumId w:val="$abstract"/>""")
                if (start != null && start != 1) nums.append("""<w:lvlOverride w:ilvl="0"><w:startOverride w:val="$start"/></w:lvlOverride>""")
                nums.append("</w:num>")
                return id.toString()
            }
            val bulletNum = num(bulletAbstract, null)
            var i = 0
            while (i < doc.paras.size) {
                if (doc.paras[i].list == null) { i++; continue }
                var end = i
                while (end < doc.paras.size && doc.paras[end].list != null) end++
                val start = doc.paras.subList(i, end).firstOrNull { it.list!!.ordered && it.list.level == 0 }?.list?.start
                val orderedNum = if (doc.paras.subList(i, end).any { it.list!!.ordered }) num(orderedAbstract, start) else null
                for (k in i until end) numIds[k] = if (doc.paras[k].list!!.ordered) orderedNum!! else bulletNum
                i = end
            }
            val abstracts = abstractNum(bulletAbstract, false) + abstractNum(orderedAbstract, true)
            // Schema order: every abstractNum before the first num.
            val firstNum = Regex("""<w:num[\s>]""").find(numbering)?.range?.first
            numbering = if (firstNum != null) numbering.substring(0, firstNum) + abstracts + numbering.substring(firstNum) else numbering.replace("</w:numbering>", "$abstracts</w:numbering>")
            val cleanup = numbering.indexOf("<w:numIdMacAtCleanup").takeIf { it >= 0 } ?: numbering.lastIndexOf("</w:numbering>")
            numbering = numbering.substring(0, cleanup) + nums + numbering.substring(cleanup)
            parts[dir + "numbering.xml"] = numbering.toByteArray()
        }

        // The body: the new paragraphs, then the original page setup (the last sectPr).
        val xml = String(parts.getValue(docPath), Charsets.UTF_8)
        val bodyStart = xml.indexOf("<w:body>") + "<w:body>".length
        val bodyEnd = xml.lastIndexOf("</w:body>")
        val sect = xml.lastIndexOf("<w:sectPr", bodyEnd).takeIf { it >= bodyStart }?.let { xml.substring(it, bodyEnd) } ?: ""
        val linkIds = HashMap<String, String>()
        val body = StringBuilder()
        doc.paras.forEachIndexed { index, p ->
            body.append("<w:p>")
            val pPr = StringBuilder()
            if (p.heading > 0) pPr.append("""<w:pStyle w:val="${headingIds.getValue(p.heading)}"/>""")
            p.list?.let { pPr.append("""<w:numPr><w:ilvl w:val="${it.level}"/><w:numId w:val="${numIds.getValue(index)}"/></w:numPr>""") }
            when (p.align) {
                DocAlign.Center -> pPr.append("""<w:jc w:val="center"/>""")
                DocAlign.End -> pPr.append("""<w:jc w:val="right"/>""")
                DocAlign.Justify -> pPr.append("""<w:jc w:val="both"/>""")
                DocAlign.Start -> Unit
            }
            if (pPr.isNotEmpty()) body.append("<w:pPr>").append(pPr).append("</w:pPr>")
            var k = 0
            val runs = OfficeFormats.tidy(p.runs)
            while (k < runs.size) {
                val link = runs[k].link
                var end = k
                while (end < runs.size && runs[end].link == link) end++
                if (link != null) body.append("""<w:hyperlink r:id="${linkIds.getOrPut(link) { newRel(REL_HYPERLINK, link, true) }}">""")
                for (r in runs.subList(k, end)) body.append(run(r, if (link != null) hyperlinkId else null, doc.basePt))
                if (link != null) body.append("</w:hyperlink>")
                k = end
            }
            body.append("</w:p>")
        }
        var out = xml.substring(0, bodyStart) + body + sect + xml.substring(bodyEnd)
        if (linkIds.isNotEmpty() && !Regex("""<w:document[^>]*xmlns:r=""").containsMatchIn(out)) out = out.replaceFirst("<w:document", "<w:document xmlns:r=\"$R\"")
        parts[docPath] = out.toByteArray()
        parts[relsPath] = rels.toByteArray()
        parts["[Content_Types].xml"] = types.toByteArray()
        return OfficeFormats.zip(parts)
    }

    private fun run(r: DocRun, style: String?, basePt: Float): String = buildString {
        val rPr = buildString {
            style?.let { append("""<w:rStyle w:val="$it"/>""") }
            if (r.bold) append("<w:b/>")
            if (r.italic) append("<w:i/>")
            if (r.strike) append("<w:strike/>")
            r.color?.let { append("""<w:color w:val="${OfficeFormats.hex(it)}"/>""") }
            r.sizePt?.takeIf { it != basePt }?.let { append("""<w:sz w:val="${(it * 2).roundToInt()}"/>""") }
            r.highlight?.let { append("""<w:highlight w:val="${nearestHighlight(it)}"/>""") }
            if (r.underline) append("""<w:u w:val="single"/>""")
        }
        val pieces = r.text.split('\t')
        append("<w:r>")
        if (rPr.isNotEmpty()) append("<w:rPr>").append(rPr).append("</w:rPr>")
        pieces.forEachIndexed { i, s ->
            if (i > 0) append("<w:tab/>")
            if (s.isNotEmpty()) append("""<w:t xml:space="preserve">""").append(OfficeFormats.esc(s)).append("</w:t>")
        }
        append("</w:r>")
    }

    fun nearestHighlight(rgb: Int): String = WORD_HIGHLIGHTS.minBy { (_, c) ->
        val dr = (c shr 16 and 255) - (rgb shr 16 and 255); val dg = (c shr 8 and 255) - (rgb shr 8 and 255); val db = (c and 255) - (rgb and 255)
        dr * dr + dg * dg + db * db
    }.key
}

// ============================================================================================ OpenDocument (.odt)

private const val OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
private const val TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
private const val STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
private const val FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
private const val XLINK = "http://www.w3.org/1999/xlink"
private const val TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"

/** LibreOffice's name for a style ("Heading_20_1") as people see it ("Heading 1"). */
internal fun odfDisplayName(name: String) = Regex("""_([0-9a-fA-F]{2})_""").replace(name) { it.groupValues[1].toInt(16).toChar().toString() }

private class OdtStyle(val name: String, val family: String, val parent: String?, val listStyle: String?, val masterPage: String?, val text: Element?, val paragraph: Element?, val automatic: Boolean)

private class OdtReader(val parts: Map<String, ByteArray>) {
    val notes = linkedSetOf<String>()
    val styles = HashMap<String, OdtStyle>() // "family/name"
    val listStyles = HashMap<String, Element>()
    var basePt = 12f
    val listCounters = HashMap<String, IntArray>() // list style → the count at each level, for a list that continues

    fun read(): OfficeDoc {
        val content = OfficeFormats.parse(parts["content.xml"] ?: error("Not an OpenDocument text.")).documentElement
        parts["styles.xml"]?.let { OfficeFormats.parse(it).documentElement }?.let { root ->
            root.kid(OFFICE, "styles")?.let { collect(it, false) }
            root.kid(OFFICE, "styles")?.kids()?.firstOrNull { it.localName == "default-style" && it.attr(STYLE, "family") == "paragraph" }
                ?.kid(STYLE, "text-properties")?.attr(FO, "font-size")?.let { size(it, 12f) }?.let { basePt = it }
            styles["paragraph/Standard"]?.text?.attr(FO, "font-size")?.let { size(it, basePt) }?.let { basePt = it }
        }
        content.kid(OFFICE, "automatic-styles")?.let { collect(it, true) }
        val text = content.kid(OFFICE, "body")?.kid(OFFICE, "text") ?: error("Not an OpenDocument text.")
        val out = ArrayList<DocPara>()
        block(text, out, null, -1)
        return OfficeDoc(out.ifEmpty { listOf(DocPara(emptyList())) }, notes, basePt)
    }

    fun collect(parent: Element, automatic: Boolean) {
        for (e in parent.kids()) when (e.localName) {
            "style" -> {
                val family = e.attr(STYLE, "family") ?: continue
                val name = e.attr(STYLE, "name") ?: continue
                styles["$family/$name"] = OdtStyle(name, family, e.attr(STYLE, "parent-style-name"), e.attr(STYLE, "list-style-name"),
                    e.attr(STYLE, "master-page-name"), e.kid(STYLE, "text-properties"), e.kid(STYLE, "paragraph-properties"), automatic)
            }
            "list-style" -> e.attr(STYLE, "name")?.let { listStyles[it] = e }
        }
    }

    fun size(v: String, base: Float): Float? = when {
        v.endsWith("pt") -> v.removeSuffix("pt").toFloatOrNull()
        v.endsWith("%") -> v.removeSuffix("%").toFloatOrNull()?.let { base * it / 100 }
        else -> null
    }

    /** A value that changes nothing ("none", "0cm", "normal"…), so leaving it out loses nothing. */
    fun neutral(v: String) = v.isEmpty() || v in setOf("none", "normal", "auto", "false", "transparent", "100%", "0%", "0% 100%", "start", "page", "0")
        || Regex("""^-?0+(\.0+)?(cm|mm|in|pt|pc|px)?$""").matches(v)

    /** Applies a style's text properties to [look]; what Writer cannot keep is named. */
    fun textLook(props: Element?, look: DocRun, inLink: Boolean = false): DocRun {
        props ?: return look
        var l = look
        val a = props.attributes
        for (i in 0 until a.length) {
            val n = a.item(i)
            val ns = n.namespaceURI ?: continue
            val local = n.localName; val v = n.nodeValue.trim()
            if (local.endsWith("-asian") || local.endsWith("-complex") || !(ns == FO || ns == STYLE)) continue
            when (local) {
                "font-weight" -> l = l.copy(bold = v == "bold" || (v.toIntOrNull() ?: 400) >= 600)
                "font-style" -> l = l.copy(italic = v == "italic" || v == "oblique")
                "text-underline-style" -> if (!inLink) l = l.copy(underline = v != "none")
                "text-line-through-style" -> l = l.copy(strike = v != "none")
                "font-size" -> size(v, basePt)?.let { l = l.copy(sizePt = it.takeIf { pt -> pt != basePt }) } ?: run { notes += "other text effects" }
                "color" -> if (!inLink) l = l.copy(color = OfficeFormats.parseHex(v))
                "background-color" -> l = l.copy(highlight = if (v == "transparent") null else OfficeFormats.parseHex(v))
                "font-name", "font-family" -> notes += "fonts"
                "text-position" -> if (!neutral(v)) notes += "superscript and subscript"
                "display" -> if (v == "none") notes += "hidden text"
                "language", "country", "script", "rfc-language-tag", "letter-kerning", "hyphenate", "hyphenation-remain-char-count",
                "hyphenation-push-char-count", "text-underline-type", "text-underline-width", "text-underline-color", "text-underline-mode",
                "text-line-through-type", "text-line-through-width", "text-line-through-color", "text-line-through-mode", "use-window-font-color",
                "font-style-name", "font-family-generic", "font-pitch", "font-charset" -> Unit
                else -> if (!neutral(v)) notes += "other text effects"
            }
        }
        return l
    }

    /** The look a named character style (and the styles it is based on) gives. */
    fun charStyle(name: String, look: DocRun, inLink: Boolean): DocRun {
        val chain = generateSequence(styles["text/$name"]) { s -> s.parent?.let { styles["text/$it"] } }.take(10).toList().asReversed()
        if (chain.isEmpty()) return look
        if (inLink && chain.any { !it.automatic && odfDisplayName(it.name).lowercase().let { n -> "internet link" in n || "visited" in n } }) return look
        return chain.fold(look) { l, s -> textLook(s.text, l, inLink) }
    }

    fun paragraphLook(p: OdtStyle?): DocAlign {
        val props = p?.paragraph ?: return DocAlign.Start
        var align = DocAlign.Start
        val a = props.attributes
        for (i in 0 until a.length) {
            val n = a.item(i)
            if (n.namespaceURI != FO && n.namespaceURI != STYLE) continue
            val v = n.nodeValue.trim()
            when (n.localName) {
                "text-align" -> align = when (v) { "center" -> DocAlign.Center; "end", "right" -> DocAlign.End; "justify" -> DocAlign.Justify; else -> DocAlign.Start }
                "break-before", "break-after" -> when (v) { "page" -> notes += "page breaks"; "column" -> notes += "columns" }
                "margin", "margin-left", "margin-right", "margin-top", "margin-bottom", "text-indent", "line-height", "line-spacing",
                "line-height-at-least", "padding", "padding-left", "padding-right", "padding-top", "padding-bottom" -> if (!neutral(v)) notes += "paragraph spacing and indents"
                "background-color", "border", "border-left", "border-right", "border-top", "border-bottom", "shadow" -> if (!neutral(v)) notes += "borders and shading"
                "text-align-last", "justify-single-word", "auto-text-indent", "writing-mode", "keep-with-next", "keep-together", "widows", "orphans",
                "hyphenation-ladder-count", "contextual-spacing", "page-number", "punctuation-wrap", "line-break", "vertical-align", "snap-to-layout-grid",
                "number-lines", "line-number", "text-autospace", "font-independent-line-spacing", "join-border", "tab-stop-distance", "register-true" -> Unit
                else -> if (!neutral(v)) notes += "other paragraph formatting"
            }
        }
        props.kid(STYLE, "tab-stops")?.let { if (it.kids().isNotEmpty()) notes += "tab stops" }
        if (props.kid(STYLE, "drop-cap") != null || props.kid(STYLE, "background-image") != null) notes += "other paragraph formatting"
        return align
    }

    /** Whether a named paragraph style is plain body text, and if not, it is named. */
    fun checkNamedParagraph(name: String?, isHeading: Boolean) {
        name ?: return
        val shown = odfDisplayName(name)
        val n = shown.lowercase()
        if (n in setOf("standard", "default", "default paragraph style", "text body", "body text", "list paragraph", "list contents", "list", "normal", "heading")) return
        if (isHeading && Regex("""heading ?\d""").matches(n)) return
        notes += "the paragraph style “$shown”"
    }

    fun block(parent: Element, out: MutableList<DocPara>, list: String?, level: Int) {
        for (e in parent.kids()) when {
            e.namespaceURI == TEXT -> when (e.localName) {
                "p", "h" -> paragraph(e, out, null)
                "list" -> list(e, out, null, 0, null)
                "sequence-decls", "variable-decls", "user-field-decls", "dde-connection-decls", "soft-page-break", "bookmark", "bookmark-start", "bookmark-end" -> Unit
                "tracked-changes" -> notes += "tracked changes"
                "section" -> { notes += "sections"; block(e, out, list, level) }
                "table-of-content", "alphabetical-index", "illustration-index", "table-index", "object-index", "user-index", "bibliography" ->
                    { notes += "fields (page numbers, dates, contents)"; e.kid(TEXT, "index-body")?.let { block(it, out, list, level) } }
                "numbered-paragraph" -> { notes += "other list formats"; block(e, out, list, level) }
                else -> notes += "other content"
            }
            e.namespaceURI == TABLE && e.localName == "table" -> { notes += "tables"; cells(e, out) }
            e.namespaceURI == OFFICE && e.localName == "forms" -> Unit
            e.localName == "frame" || e.namespaceURI?.contains("drawing") == true -> notes += "shapes and text boxes"
            else -> notes += "other content"
        }
    }

    fun cells(e: Element, out: MutableList<DocPara>) {
        for (c in e.kids()) if (c.localName == "table-cell") block(c, out, null, -1) else if (c.namespaceURI == TABLE) cells(c, out)
    }

    fun list(e: Element, out: MutableList<DocPara>, inherited: String?, level: Int, parentCounter: IntArray?) {
        val styleName = e.attr(TEXT, "style-name") ?: inherited
        val counter = if (level == 0) {
            val cont = e.attr(TEXT, "continue-numbering") == "true" || e.attr(TEXT, "continue-list") != null
            if (cont) listCounters.getOrPut(styleName ?: "") { IntArray(10) } else IntArray(10).also { listCounters[styleName ?: ""] = it }
        } else parentCounter!!
        for (d in level + 1 until 10) counter[d] = 0
        for (item in e.kids()) {
            if (item.localName == "list-header") notes += "other list formats"
            if (item.localName != "list-item" && item.localName != "list-header") continue
            var numbered = false
            for (c in item.kids()) when (c.localName) {
                "p", "h" -> {
                    val style = styleName ?: c.attr(TEXT, "style-name")?.let { styles["paragraph/$it"]?.listStyle }
                    val (ordered, start) = levelStyle(style, level)
                    val value = if (!numbered) {
                        item.attr(TEXT, "start-value")?.toIntOrNull()?.let { counter[level] = it } ?: run { counter[level] = if (counter[level] == 0) start else counter[level] + 1 }
                        for (d in level + 1 until 10) counter[d] = 0
                        numbered = true
                        counter[level]
                    } else null
                    paragraph(c, out, if (value == null) null else DocList(ordered, level.coerceAtMost(8), value.takeIf { ordered }))
                    if (value == null) { notes += "paragraphs inside list items" }
                }
                "list" -> list(c, out, styleName, level + 1, counter)
                "soft-page-break" -> Unit
                else -> notes += "other content"
            }
        }
    }

    /** Whether a list level is numbered, and where it starts. */
    fun levelStyle(name: String?, level: Int): Pair<Boolean, Int> {
        val style = name?.let { listStyles[it] } ?: return false to 1
        val l = style.kids().firstOrNull { it.attr(TEXT, "level")?.toIntOrNull() == level + 1 } ?: return false to 1
        return when (l.localName) {
            "list-level-style-number" -> {
                val format = l.attr(STYLE, "num-format") ?: "1"
                if (format.isEmpty()) { notes += "other list formats"; false to 1 }
                else { if (format != "1") notes += "letter or roman numbering"; true to (l.attr(TEXT, "start-value")?.toIntOrNull() ?: 1) }
            }
            else -> false to 1
        }
    }

    fun paragraph(p: Element, out: MutableList<DocPara>, list: DocList?) {
        val isHeading = p.localName == "h"
        val heading = if (isHeading) (p.attr(TEXT, "outline-level")?.toIntOrNull() ?: 1).also { if (it > 6) notes += "headings below level 6" }.coerceIn(1, 6) else 0
        val styleName = p.attr(TEXT, "style-name")
        val style = styleName?.let { styles["paragraph/$it"] }
        var base = DocRun("")
        var align = DocAlign.Start
        if (style != null && style.automatic) {
            align = paragraphLook(style)
            base = textLook(style.text, base)
            checkNamedParagraph(style.parent, isHeading)
            style.masterPage?.let { if (it.isNotEmpty() && it != "Standard" && it != "Default_20_Page_20_Style") notes += "page styles" }
        } else checkNamedParagraph(styleName, isHeading)
        if (isHeading && p.attr(TEXT, "is-list-header") == null && p.parentNode.let { it is Element && it.localName == "list-item" }) notes += "numbered headings"
        val runs = ArrayList<DocRun>()
        var currentList = list
        fun breakLine() { out += DocPara(OfficeFormats.tidy(runs), heading, currentList, align); runs.clear(); currentList = null }
        fun content(parent: Element, look: DocRun, inLink: Boolean) {
            var n: Node? = parent.firstChild
            while (n != null) {
                if (n.nodeType == Node.TEXT_NODE || n.nodeType == Node.CDATA_SECTION_NODE) {
                    // ODF collapses runs of white space like HTML; <text:s/> carries the spaces that count.
                    val t = n.nodeValue.replace(Regex("""[ \t\r\n]+"""), " ")
                    if (t.isNotEmpty()) runs += look.copy(text = t)
                } else if (n is Element) {
                    val e = n
                    if (e.namespaceURI == TEXT) when (e.localName) {
                        "span" -> content(e, e.attr(TEXT, "style-name")?.let { charStyle(it, look, inLink) } ?: look, inLink)
                        "a" -> {
                            val href = e.attr(XLINK, "href")
                            if (href == null || href.startsWith("#")) { notes += "links inside the document"; content(e, look, inLink) }
                            else content(e, look.copy(link = href), true)
                        }
                        "s" -> runs += look.copy(text = " ".repeat(e.attr(TEXT, "c")?.toIntOrNull()?.coerceIn(1, 1000) ?: 1))
                        "tab" -> runs += look.copy(text = "\t")
                        "line-break" -> { notes += "line breaks inside a paragraph"; breakLine() }
                        "soft-page-break", "bookmark", "bookmark-start", "bookmark-end", "reference-mark", "reference-mark-start", "reference-mark-end", "alphabetical-index-mark", "toc-mark" -> Unit
                        "note" -> notes += "footnotes"
                        "change", "change-start", "change-end" -> notes += "tracked changes"
                        "ruby" -> { notes += "other text effects"; e.kid(TEXT, "ruby-base")?.let { content(it, look, inLink) } }
                        "meta", "meta-field" -> content(e, look, inLink)
                        else -> { notes += "fields (page numbers, dates, contents)"; content(e, look, inLink) }
                    } else if (e.namespaceURI == OFFICE && (e.localName == "annotation" || e.localName == "annotation-end")) notes += "comments"
                    else if (e.localName == "frame" || e.namespaceURI?.contains("drawing") == true) notes += if (e.getElementsByTagNameNS("*", "image").length > 0) "images" else "shapes and text boxes"
                    else notes += "other content"
                }
                n = n.nextSibling
            }
        }
        content(p, base, false)
        // Leading white space in an ODF paragraph is not shown; a trailing space is kept (LibreOffice keeps it).
        if (runs.isNotEmpty() && p.firstChild?.nodeType == Node.TEXT_NODE) runs[0] = runs[0].copy(text = runs[0].text.trimStart(' '))
        out += DocPara(OfficeFormats.tidy(runs), heading, currentList, align)
    }
}

private object OdtWriter {
    val NS = """xmlns:office="$OFFICE" xmlns:style="$STYLE" xmlns:text="$TEXT" xmlns:table="$TABLE" xmlns:fo="$FO" xmlns:xlink="$XLINK" xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0" xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0""""

    fun canReuse(parts: Map<String, ByteArray>): Boolean = runCatching {
        String(parts.getValue("content.xml"), Charsets.UTF_8).let { Regex("""<office:text[\s>/]""").containsMatchIn(it) && "xmlns:text=" in it && "xmlns:style=" in it && "xmlns:fo=" in it }
    }.getOrDefault(false)

    fun blank(): LinkedHashMap<String, ByteArray> {
        fun heading(n: Int, size: String) = """<style:style style:name="Heading_20_$n" style:display-name="Heading $n" style:family="paragraph" style:parent-style-name="Heading" style:next-style-name="Text_20_body" style:default-outline-level="$n" style:class="text"><style:text-properties fo:font-size="$size" fo:font-weight="bold"/></style:style>"""
        val m = linkedMapOf(
            "mimetype" to "application/vnd.oasis.opendocument.text",
            "META-INF/manifest.xml" to """<?xml version="1.0" encoding="UTF-8"?><manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.3"><manifest:file-entry manifest:full-path="/" manifest:version="1.3" manifest:media-type="application/vnd.oasis.opendocument.text"/><manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/><manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/></manifest:manifest>""",
            "content.xml" to """<?xml version="1.0" encoding="UTF-8"?><office:document-content $NS office:version="1.3"><office:automatic-styles/><office:body><office:text><text:p text:style-name="Text_20_body"/></office:text></office:body></office:document-content>""",
            "styles.xml" to """<?xml version="1.0" encoding="UTF-8"?><office:document-styles $NS office:version="1.3"><office:styles><style:default-style style:family="paragraph"><style:paragraph-properties style:writing-mode="page"/><style:text-properties fo:font-size="12pt" style:font-name="Liberation Serif" fo:language="none"/></style:default-style><style:style style:name="Standard" style:family="paragraph" style:class="text"/><style:style style:name="Text_20_body" style:display-name="Text body" style:family="paragraph" style:parent-style-name="Standard" style:class="text"><style:paragraph-properties fo:margin-top="0cm" fo:margin-bottom="0.247cm" fo:line-height="115%"/></style:style><style:style style:name="Heading" style:family="paragraph" style:parent-style-name="Standard" style:next-style-name="Text_20_body" style:class="text"><style:paragraph-properties fo:margin-top="0.423cm" fo:margin-bottom="0.212cm" fo:keep-with-next="always"/></style:style>${heading(1, "130%")}${heading(2, "115%")}${heading(3, "101%")}${heading(4, "95%")}${heading(5, "85%")}${heading(6, "85%")}</office:styles><office:automatic-styles><style:page-layout style:name="pm1"><style:page-layout-properties fo:page-width="21.001cm" fo:page-height="29.7cm" fo:margin-top="2cm" fo:margin-bottom="2cm" fo:margin-left="2cm" fo:margin-right="2cm"/></style:page-layout></office:automatic-styles><office:master-styles><style:master-page style:name="Standard" style:page-layout-name="pm1"/></office:master-styles></office:document-styles>""",
        )
        return LinkedHashMap(m.mapValues { it.value.toByteArray() })
    }

    fun write(doc: OfficeDoc, source: LinkedHashMap<String, ByteArray>): ByteArray {
        val parts = LinkedHashMap(source)
        var stylesXml = parts["styles.xml"]?.let { String(it, Charsets.UTF_8) }
        val named = stylesXml?.let { s -> Regex("""style:name="([^"]+)"""").findAll(s).map { it.groupValues[1] }.toSet() } ?: emptySet()
        // Plain paragraphs keep the style this document uses for its text (Text body in LibreOffice, else Standard).
        val body = if ("Text_20_body" in named) "Text_20_body" else "Standard"
        // A heading style the document does not have is added, so its headings still look like headings.
        if (stylesXml != null && "</office:styles>" in stylesXml) {
            val add = (1..6).filter { n -> doc.paras.any { it.heading == n } && "Heading_20_$n" !in named }.joinToString("") { n ->
                """<style:style style:name="Heading_20_$n" style:display-name="Heading $n" style:family="paragraph" style:parent-style-name="Standard" style:next-style-name="$body" style:default-outline-level="$n" style:class="text"><style:paragraph-properties fo:margin-top="0.423cm" fo:margin-bottom="0.212cm" fo:keep-with-next="always"/><style:text-properties fo:font-size="${listOf("130%", "115%", "101%", "95%", "85%", "85%")[n - 1]}" fo:font-weight="bold"/></style:style>"""
            }
            if (add.isNotEmpty()) { stylesXml = stylesXml.replace("</office:styles>", "$add</office:styles>"); parts["styles.xml"] = stylesXml.toByteArray() }
        }

        val auto = StringBuilder()
        val paraStyles = HashMap<Pair<String, DocAlign>, String>()
        val textStyles = HashMap<DocRun, String>()
        val listStyles = HashMap<String, String>()
        fun paraStyle(parent: String, align: DocAlign): String {
            if (align == DocAlign.Start) return parent
            return paraStyles.getOrPut(parent to align) {
                val name = "TP${paraStyles.size + 1}"
                val a = when (align) { DocAlign.Center -> "center"; DocAlign.End -> "end"; DocAlign.Justify -> "justify"; DocAlign.Start -> "start" }
                auto.append("""<style:style style:name="$name" style:family="paragraph" style:parent-style-name="$parent"><style:paragraph-properties fo:text-align="$a"${if (align == DocAlign.Justify) " style:justify-single-word=\"false\"" else ""}/></style:style>""")
                name
            }
        }
        fun textStyle(r: DocRun): String? {
            val look = r.copy(text = "", link = null)
            if (look == DocRun("")) return null
            return textStyles.getOrPut(look) {
                val name = "TT${textStyles.size + 1}"
                val p = buildString {
                    if (r.bold) append(""" fo:font-weight="bold" style:font-weight-asian="bold" style:font-weight-complex="bold"""")
                    if (r.italic) append(""" fo:font-style="italic" style:font-style-asian="italic" style:font-style-complex="italic"""")
                    if (r.underline) append(""" style:text-underline-style="solid" style:text-underline-width="auto" style:text-underline-color="font-color"""")
                    if (r.strike) append(""" style:text-line-through-style="solid" style:text-line-through-type="single"""")
                    r.sizePt?.let { val s = fmt(it); append(""" fo:font-size="${s}pt" style:font-size-asian="${s}pt" style:font-size-complex="${s}pt"""") }
                    r.color?.let { append(""" fo:color="#${OfficeFormats.hex(it).lowercase()}"""") }
                    r.highlight?.let { append(""" fo:background-color="#${OfficeFormats.hex(it).lowercase()}"""") }
                }
                auto.append("""<style:style style:name="$name" style:family="text"><style:text-properties$p/></style:style>""")
                name
            }
        }
        /** A list style whose levels are numbered or bulleted as this list's items are. */
        fun listStyle(items: List<DocPara>): String {
            val pattern = (0..9).joinToString("") { l -> if (items.firstOrNull { it.list!!.level == l }?.list?.ordered == true) "n" else "b" }
            return listStyles.getOrPut(pattern) {
                val name = "TL${listStyles.size + 1}"
                auto.append("""<text:list-style style:name="$name">""")
                pattern.forEachIndexed { i, c ->
                    val indent = "%.3fcm".format(java.util.Locale.ROOT, 0.635 * (i + 1))
                    val props = """<style:list-level-properties text:list-level-position-and-space-mode="label-alignment"><style:list-level-label-alignment text:label-followed-by="listtab" text:list-tab-stop-position="$indent" fo:text-indent="-0.635cm" fo:margin-left="$indent"/></style:list-level-properties>"""
                    if (c == 'n') auto.append("""<text:list-level-style-number text:level="${i + 1}" style:num-suffix="." style:num-format="1">$props</text:list-level-style-number>""")
                    else auto.append("""<text:list-level-style-bullet text:level="${i + 1}" text:bullet-char="${listOf("•", "◦", "▪")[i % 3]}">$props</text:list-level-style-bullet>""")
                }
                auto.append("</text:list-style>")
                name
            }
        }

        fun paragraphXml(p: DocPara): String = buildString {
            val tag = if (p.heading > 0) "text:h" else "text:p"
            val style = paraStyle(if (p.heading > 0) "Heading_20_${p.heading}" else body, p.align)
            append("<$tag text:style-name=\"$style\"")
            if (p.heading > 0) append(" text:outline-level=\"${p.heading}\"")
            append(">")
            var k = 0
            var prev: String? = null
            val runs = OfficeFormats.tidy(p.runs)
            while (k < runs.size) {
                val link = runs[k].link
                var end = k
                while (end < runs.size && runs[end].link == link) end++
                if (link != null) append("""<text:a xlink:type="simple" xlink:href="${OfficeFormats.esc(link)}">""")
                for (r in runs.subList(k, end)) {
                    val s = textStyle(r)
                    if (s != null) append("<text:span text:style-name=\"$s\">")
                    append(spaces(r.text, afterSpace = prev?.endsWith(' ') ?: true))
                    prev = r.text
                    if (s != null) append("</text:span>")
                }
                if (link != null) append("</text:a>")
                k = end
            }
            append("</$tag>")
        }

        val text = StringBuilder()
        var i = 0
        while (i < doc.paras.size) {
            val p = doc.paras[i]
            if (p.list == null) { text.append(paragraphXml(p)); i++; continue }
            var end = i
            while (end < doc.paras.size && doc.paras[end].list != null) end++
            val items = doc.paras.subList(i, end)
            text.append("<text:list text:style-name=\"${listStyle(items)}\">")
            var depth = 0 // lists open below the top one
            var itemOpen = false
            for ((n, item) in items.withIndex()) {
                val level = item.list!!.level
                while (depth > level) { text.append("</text:list-item></text:list>"); depth-- }
                if (itemOpen && depth == level) text.append("</text:list-item>")
                while (depth < level) { if (!itemOpen || n == 0) text.append("<text:list-item>"); text.append("<text:list>"); depth++; itemOpen = false }
                val start = item.list.start?.takeIf { item.list.ordered && (n == 0 || items[n - 1].list!!.level < level) && it != 1 }
                text.append(if (start != null) "<text:list-item text:start-value=\"$start\">" else "<text:list-item>")
                text.append(paragraphXml(item))
                itemOpen = true
            }
            text.append("</text:list-item>")
            while (depth > 0) { text.append("</text:list></text:list-item>"); depth-- }
            text.append("</text:list>")
            i = end
        }

        val content = String(parts.getValue("content.xml"), Charsets.UTF_8)
        val open = Regex("""<office:text(\s[^>]*)?(/?)>""").find(content)!!
        val decls: String
        val rest: String
        if (open.groupValues[2] == "/") { decls = ""; rest = content.substring(open.range.last + 1) }
        else {
            val close = content.indexOf("</office:text>", open.range.last)
            val inner = content.substring(open.range.last + 1, close)
            // Declarations (sequences, variables, forms) stay; the text is replaced.
            decls = Regex("""<(text:[a-z-]+-decls|office:forms)(\s[^>]*)?(/>|>[\s\S]*?</\1>)""").findAll(inner).joinToString("") { it.value }
            rest = content.substring(close + "</office:text>".length)
        }
        var out = content.substring(0, open.range.first) + "<office:text${open.groupValues[1]}>" + decls + text + "</office:text>" + rest
        val autoRe = Regex("""<office:automatic-styles\s*/>|<office:automatic-styles>[\s\S]*?</office:automatic-styles>""")
        val autoXml = "<office:automatic-styles>$auto</office:automatic-styles>"
        out = if (autoRe.containsMatchIn(out)) out.replaceFirst(autoRe, Regex.escapeReplacement(autoXml)) else out.replaceFirst("<office:body>", "$autoXml<office:body>")
        if ("xmlns:xlink=" !in out.substring(0, out.indexOf('>', out.indexOf("<office:document-content")))) out = out.replaceFirst("<office:document-content", "<office:document-content xmlns:xlink=\"$XLINK\"")
        parts["content.xml"] = out.toByteArray()
        return OfficeFormats.zip(parts, stored = "mimetype")
    }

    fun fmt(pt: Float): String = if (pt == pt.toInt().toFloat()) pt.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, pt)

    /** Text with ODF's spaces: a space after a space (or at the start of a paragraph) is a <text:s/>; a tab a <text:tab/>. */
    fun spaces(s: String, afterSpace: Boolean): String = buildString {
        var waiting = 0
        fun flush() { if (waiting > 0) append(if (waiting == 1) "<text:s/>" else "<text:s text:c=\"$waiting\"/>"); waiting = 0 }
        for ((i, c) in s.withIndex()) {
            if (c == ' ') {
                if (if (i == 0) afterSpace else s[i - 1] == ' ') waiting++ else append(' ')
                continue
            }
            flush()
            if (c == '\t') append("<text:tab/>") else append(OfficeFormats.esc(c.toString()))
        }
        flush()
    }
}
