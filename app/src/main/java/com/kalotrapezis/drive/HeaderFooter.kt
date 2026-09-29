package com.kalotrapezis.drive

import kotlin.math.roundToInt

/**
 * The document's header and footer as Writer sets them (asked 2026-09-27, "header/footer/page numbers"): a line of
 * text each, where it stands, and the page number — in the header or the footer, left, centre or right, as "1",
 * "Page 1 of 3" or "1 / 3" — and whether the first page (a title page) has none. The UI is English only (the user,
 * 29 Sep: languages come later with string files); a file already saying "Σελίδα 1 από 3" keeps its own words.
 */
internal data class HeaderFooter(
    val header: String = "", val headerAlign: DocAlign = DocAlign.Start,
    val footer: String = "", val footerAlign: DocAlign = DocAlign.Start,
    val number: NumberPlace? = null, val numberAlign: DocAlign = DocAlign.End, val numberFormat: String = "#",
    val firstDifferent: Boolean = false,
) {
    enum class NumberPlace { Header, Footer }

    companion object {
        /** How the page number reads: # is the page, N the pages. */
        val FORMATS = listOf("#", "Page # of N", "# / N")

        /** What a document's header and footer say now, as far as a line of text each tells it. */
        fun of(section: DocSection): HeaderFooter {
            fun line(paras: List<DocPara>?) = paras.orEmpty().filter { it.table == null }.joinToString(" ") { it.text }.trim()
            val h = line(section.headers["default"]); val f = line(section.footers["default"])
            // Where something stands: its paragraph's alignment, or (after tabs to Word's zones) centre, then right.
            fun zone(p: DocPara, at: Int): DocAlign {
                val n = p.text.substring(0, at).count { it == '\t' }
                if (n == 0) return p.align
                // The n-th of the paragraph's own tab stops says which zone (Word's header has centre, then right).
                val stops = p.keep?.parts?.firstOrNull { it.first == "tabs" || (it.first == "child" && "tab-stops" in it.second) }?.second
                    ?.let { x -> Regex("""(?:w:val|style:type)="(\w+)"""").findAll(x).map { it.groupValues[1] }.filter { it != "clear" }.toList() }.orEmpty()
                return when (stops.getOrNull(n - 1)) { "center" -> DocAlign.Center; "right", "end" -> DocAlign.End; null -> if (n == 1) DocAlign.Center else DocAlign.End; else -> DocAlign.Start }
            }
            fun align(paras: List<DocPara>?) = paras.orEmpty().firstOrNull { it.text.isNotBlank() }?.let { p ->
                zone(p, p.text.indexOfFirst { !it.isWhitespace() && it != PAGE_FIELD && it != PAGES_FIELD }.coerceAtLeast(0)) } ?: DocAlign.Start
            fun numberAlign(paras: List<DocPara>?) = paras.orEmpty().firstOrNull { PAGE_FIELD in it.text }?.let { p ->
                // The number's zone: where "Page" or the number begins.
                val at = p.text.indexOf(PAGE_FIELD).let { i -> Regex("""(Page|Σελίδα)\s*$""").find(p.text.substring(0, i))?.range?.first ?: i }
                zone(p, at) }
            val inHeader = PAGE_FIELD in h
            val withNumber = if (inHeader) h else f
            fun text(s: String) = s.replace(Regex("""\s*(Page|Σελίδα)?\s*${PAGE_FIELD}(\s*(of|από|/)\s*${PAGES_FIELD})?\s*"""), " ").trim()
            val format = when {
                PAGE_FIELD !in withNumber -> "#"
                Regex("""Σελίδα\s*${PAGE_FIELD}""").containsMatchIn(withNumber) -> "Σελίδα # από N"
                Regex("""Page\s*${PAGE_FIELD}""").containsMatchIn(withNumber) -> FORMATS[1]
                Regex("""${PAGE_FIELD}\s*/\s*${PAGES_FIELD}""").containsMatchIn(withNumber) -> FORMATS[2]
                else -> "#"
            }
            return HeaderFooter(text(h), if (text(h).isEmpty()) DocAlign.Start else align(section.headers["default"]),
                text(f), if (text(f).isEmpty()) DocAlign.Start else align(section.footers["default"]),
                when { inHeader -> NumberPlace.Header; PAGE_FIELD in f -> NumberPlace.Footer; else -> null },
                numberAlign(if (inHeader) section.headers["default"] else section.footers["default"]) ?: DocAlign.End,
                format, section.titlePage)
        }

        /**
         * Whether the header or footer has more than a line of text (a logo, a table, several lines): setting it here
         * puts a line of text in its place.
         */
        fun rich(section: DocSection) = listOfNotNull(section.headers["default"], section.footers["default"]).any { paras ->
            paras.count { it.text.isNotBlank() } > 1 || paras.any { p -> p.table != null || p.runs.any { it.image != null } }
        }
    }
}

/** Putting a [HeaderFooter] into a Word package: new header and footer parts, the body's page setup pointing at them. */
internal object HeaderFooterEdit {
    private const val REL_HEADER = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/header"
    private const val REL_FOOTER = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer"
    private const val TYPE_HEADER = "application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"
    private const val TYPE_FOOTER = "application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"

    fun docx(source: Map<String, ByteArray>, hf: HeaderFooter, page: DocPage): LinkedHashMap<String, ByteArray> {
        val parts = LinkedHashMap(source)
        val docPath = documentPath(parts)
        val dir = docPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val relsPath = relsPath(docPath)
        var rels = parts[relsPath]?.let { String(it, Charsets.UTF_8) } ?: """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>"""
        var types = String(parts.getValue("[Content_Types].xml"), Charsets.UTF_8)
        val ids = Regex("""Id="([^"]+)"""").findAll(rels).map { it.groupValues[1] }.toMutableSet()
        fun add(kind: String, xml: String): String {
            val name = generateSequence(1) { it + 1 }.map { "$kind$it.xml" }.first { dir + it !in parts }
            parts[dir + name] = xml.toByteArray()
            val id = generateSequence(1) { it + 1 }.map { "rIdTetra$kind$it" }.first { it !in ids }.also { ids += it }
            rels = rels.replace("</Relationships>", """<Relationship Id="$id" Type="${if (kind == "header") REL_HEADER else REL_FOOTER}" Target="$name"/></Relationships>""")
            types = types.replace("</Types>", """<Override PartName="/$dir$name" ContentType="${if (kind == "header") TYPE_HEADER else TYPE_FOOTER}"/></Types>""")
            return id
        }
        val width = ((page.textWidth) * 20).roundToInt()
        val refs = StringBuilder()
        for (kind in listOf("header", "footer")) {
            val (text, align) = if (kind == "header") hf.header to hf.headerAlign else hf.footer to hf.footerAlign
            val number = hf.number == (if (kind == "header") HeaderFooter.NumberPlace.Header else HeaderFooter.NumberPlace.Footer)
            val tag = if (kind == "header") "hdr" else "ftr"
            if (text.isNotBlank() || number) {
                val id = add(kind, "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:$tag xmlns:w=\"$W\" xmlns:r=\"$RELATIONSHIPS\">" +
                    line(text, align, if (number) hf.numberFormat else null, hf.numberAlign, width) + "</w:$tag>")
                refs.append("""<w:${kind}Reference w:type="default" r:id="$id"/>""")
            }
            // A title page: its own, empty.
            if (hf.firstDifferent) refs.append("""<w:${kind}Reference w:type="first" r:id="${add(kind, "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:$tag xmlns:w=\"$W\" xmlns:r=\"$RELATIONSHIPS\"><w:p/></w:$tag>")}"/>""")
        }
        // The body's page setup (its last sectPr): the old references out, the new in, first (Word's order); titlePg.
        var xml = String(parts.getValue(docPath), Charsets.UTF_8)
        val bodyEnd = xml.lastIndexOf("</w:body>")
        val at = xml.lastIndexOf("<w:sectPr", bodyEnd).takeIf { it >= 0 && xml.indexOf("</w:p>", it).let { p -> p < 0 || p > bodyEnd } }
        val tagEnd = at?.let { xml.indexOf('>', it) }
        val selfClosing = tagEnd != null && xml[tagEnd - 1] == '/'
        val end = at?.let { if (selfClosing) tagEnd!! + 1 else xml.indexOf("</w:sectPr>", it) + "</w:sectPr>".length }
        val open = if (at == null) "<w:sectPr>" else if (selfClosing) xml.substring(at, tagEnd!! - 1) + ">" else xml.substring(at, tagEnd!! + 1)
        var inner = if (at == null || selfClosing) "" else xml.substring(tagEnd!! + 1, end!! - "</w:sectPr>".length)
        inner = inner.replace(Regex("""<w:(header|footer)Reference\b[^>]*/>"""), "").replace(Regex("""<w:titlePg\b[^>]*/>"""), "")
        if (hf.firstDifferent) {
            // titlePg goes before textDirection, bidi, rtlGutter, docGrid, printerSettings (whichever comes first).
            val later = Regex("""<w:(textDirection|bidi|rtlGutter|docGrid|printerSettings)\b""").find(inner)?.range?.first ?: inner.length
            inner = inner.substring(0, later) + "<w:titlePg/>" + inner.substring(later)
        }
        val newSect = open + refs + inner + "</w:sectPr>"
        xml = if (at != null) xml.replaceRange(at, end!!, newSect) else xml.replaceRange(bodyEnd, bodyEnd, newSect)
        if (!xml.substring(0, xml.indexOf('>', xml.indexOf("<w:document"))).contains("xmlns:r=")) xml = xml.replaceFirst("<w:document", "<w:document xmlns:r=\"$RELATIONSHIPS\"")
        parts[docPath] = xml.toByteArray()
        parts[relsPath] = rels.toByteArray()
        parts["[Content_Types].xml"] = types.toByteArray()
        return parts
    }

    /**
     * The same in an OpenDocument: the main page style's (Standard, else the first) header and footer replaced, its
     * paragraphs styled by automatic styles of their own (alignment, the zones' tab stops), the page layout given room
     * for them (at least nothing, and a quarter centimetre between them and the text, as LibreOffice's own).
     */
    fun odt(source: Map<String, ByteArray>, hf: HeaderFooter, page: DocPage): LinkedHashMap<String, ByteArray> {
        val parts = LinkedHashMap(source)
        var xml = String(parts.getValue("styles.xml"), Charsets.UTF_8)
        val masters = Regex("""<style:master-page\b[^>]*?(?:/>|>[\s\S]*?</style:master-page>)""").findAll(xml).toList()
        val master = masters.firstOrNull { """style:name="Standard"""" in it.value.substringBefore('>') } ?: masters.firstOrNull() ?: error("No page style")
        val names = Regex("""style:name="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toMutableSet()
        val auto = StringBuilder()
        fun pt(v: Float) = "${(v * 100).roundToInt() / 100f}pt"
        fun paragraph(text: String, align: DocAlign, format: String?, numberAlign: DocAlign): String {
            fun zone(a: DocAlign) = when (a) { DocAlign.Center -> 1; DocAlign.End -> 2; else -> 0 }
            fun t(v: String) = OfficeFormats.esc(v.replace(Regex(" {2,}"), " "))
            val number = format?.split(Regex("(?=[#N])|(?<=[#N])"))?.filter { it.isNotEmpty() }?.joinToString("") { when (it) {
                "#" -> "<text:page-number text:select-page=\"current\">1</text:page-number>"; "N" -> "<text:page-count>1</text:page-count>"; else -> t(it) } }
            val zones = arrayOf(StringBuilder(), StringBuilder(), StringBuilder())
            if (text.isNotBlank()) zones[zone(align)].append(t(text.trim()))
            if (number != null) zones[zone(numberAlign)].let { if (it.isNotEmpty()) it.append(' '); it.append(number) }
            val used = zones.indices.filter { zones[it].isNotEmpty() }
            val one = used.size <= 1
            val a = if (one) when (used.firstOrNull() ?: 0) { 1 -> "center"; 2 -> "end"; else -> "start" } else "start"
            val stops = if (one) "" else "<style:tab-stops>" + used.filter { it > 0 }.joinToString("") {
                if (it == 1) "<style:tab-stop style:position=\"${pt(page.textWidth / 2)}\" style:type=\"center\"/>" else "<style:tab-stop style:position=\"${pt(page.textWidth)}\" style:type=\"right\"/>" } + "</style:tab-stops>"
            val name = generateSequence(1) { it + 1 }.map { "TetraHF$it" }.first { it !in names }.also { names += it }
            auto.append("""<style:style style:name="$name" style:family="paragraph" style:parent-style-name="Standard"><style:paragraph-properties fo:text-align="$a">$stops</style:paragraph-properties></style:style>""")
            val body = if (one) zones[used.firstOrNull() ?: 0].toString() else used.joinToString("") { z -> (if (z > 0) "<text:tab/>" else "") + zones[z] }
            return """<text:p text:style-name="$name">$body</text:p>"""
        }
        val bands = StringBuilder()
        for (kind in listOf("header", "footer")) {
            val (text, align) = if (kind == "header") hf.header to hf.headerAlign else hf.footer to hf.footerAlign
            val number = hf.number == (if (kind == "header") HeaderFooter.NumberPlace.Header else HeaderFooter.NumberPlace.Footer)
            if (text.isNotBlank() || number) bands.append("<style:$kind>${paragraph(text, align, if (number) hf.numberFormat else null, hf.numberAlign)}</style:$kind>")
        }
        // A title page: an empty header and footer of its own (only when there is one to leave off).
        if (hf.firstDifferent) for (kind in listOf("header", "footer")) if ("<style:$kind>" in bands) bands.append("<style:$kind-first><text:p/></style:$kind-first>")
        // In ODF's order: headers, then footers.
        val ordered = listOf("header", "header-first", "footer", "footer-first").joinToString("") { k -> Regex("""<style:$k>[\s\S]*?</style:$k>""").find(bands)?.value ?: "" }
        val m = master.value
        val open = m.substring(0, m.indexOf('>') + 1).let { if (it.endsWith("/>")) it.dropLast(2) + ">" else it }
        val rest = if (m.endsWith("/>") && m.indexOf('>') == m.length - 1) "" else m.substring(m.indexOf('>') + 1, m.length - "</style:master-page>".length)
            .replace(Regex("""<style:(header|header-left|header-first|footer|footer-left|footer-first)\b[^>]*?(?:/>|>[\s\S]*?</style:\1>)"""), "")
        xml = xml.replaceRange(master.range, "$open$ordered$rest</style:master-page>")
        // Room for them on the page layout the page style uses.
        Regex("""style:page-layout-name="([^"]+)"""").find(open)?.groupValues?.get(1)?.let { layoutName ->
            Regex("""<style:page-layout\b[^>]*style:name="${Regex.escape(layoutName)}"[^>]*?(?:/>|>[\s\S]*?</style:page-layout>)""").find(xml)?.let { lm ->
                var l = lm.value
                if (l.endsWith("/>") && l.indexOf('>') == l.length - 1) l = l.dropLast(2) + "></style:page-layout>"
                for ((kind, gap) in listOf("header" to "margin-bottom", "footer" to "margin-top")) {
                    if ("<style:$kind>" !in ordered) continue
                    val filled = """<style:$kind-style><style:header-footer-properties fo:min-height="0cm" fo:$gap="0.25cm"/></style:$kind-style>"""
                    l = when {
                        Regex("""<style:$kind-style>\s*<style:header-footer-properties""").containsMatchIn(l) -> l
                        Regex("""<style:$kind-style\s*/>|<style:$kind-style>\s*</style:$kind-style>""").containsMatchIn(l) -> l.replace(Regex("""<style:$kind-style\s*/>|<style:$kind-style>\s*</style:$kind-style>"""), filled)
                        // Header style before footer style, both after the page's own properties.
                        kind == "header" && "<style:footer-style" in l -> l.replaceFirst("<style:footer-style", "$filled<style:footer-style")
                        else -> l.replace("</style:page-layout>", "$filled</style:page-layout>")
                    }
                }
                xml = xml.replaceRange(lm.range, l)
            }
        }
        xml = when {
            "</office:automatic-styles>" in xml -> xml.replaceFirst("</office:automatic-styles>", "$auto</office:automatic-styles>")
            "<office:automatic-styles/>" in xml -> xml.replaceFirst("<office:automatic-styles/>", "<office:automatic-styles>$auto</office:automatic-styles>")
            else -> xml.replaceFirst("<office:master-styles", "<office:automatic-styles>$auto</office:automatic-styles><office:master-styles")
        }
        parts["styles.xml"] = xml.toByteArray()
        return parts
    }

    /**
     * One line: the text where it stands and the page number where it stands — Word's header zones: left, a centre
     * tab stop, a right one at the text's edge.
     */
    private fun line(text: String, align: DocAlign, format: String?, numberAlign: DocAlign, width: Int): String {
        fun zone(a: DocAlign) = when (a) { DocAlign.Center -> 1; DocAlign.End -> 2; else -> 0 }
        fun t(s: String) = if (s.isEmpty()) "" else "<w:r><w:t xml:space=\"preserve\">${OfficeFormats.esc(s)}</w:t></w:r>"
        fun field(name: String) = "<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText xml:space=\"preserve\"> $name </w:instrText></w:r>" +
            "<w:r><w:fldChar w:fldCharType=\"separate\"/></w:r><w:r><w:t>1</w:t></w:r><w:r><w:fldChar w:fldCharType=\"end\"/></w:r>"
        val number = format?.let { f -> f.split(Regex("(?=[#N])|(?<=[#N])")).filter { it.isNotEmpty() }.joinToString("") { when (it) { "#" -> field("PAGE"); "N" -> field("NUMPAGES"); else -> t(it) } } }
        val zones = arrayOf(StringBuilder(), StringBuilder(), StringBuilder())
        if (text.isNotBlank()) zones[zone(align)].append(t(text.trim()))
        if (number != null) zones[zone(numberAlign)].let { if (it.isNotEmpty()) it.append(t(" ")); it.append(number) }
        val used = zones.indices.filter { zones[it].isNotEmpty() }
        // All in one zone: that alignment; else tabs to each zone after the first.
        if (used.size <= 1) {
            val z = used.firstOrNull() ?: 0
            val jc = when (z) { 1 -> "<w:jc w:val=\"center\"/>"; 2 -> "<w:jc w:val=\"right\"/>"; else -> "" }
            return "<w:p>${if (jc.isEmpty()) "" else "<w:pPr>$jc</w:pPr>"}${zones[z]}</w:p>"
        }
        // A tab stop for each zone used after the left one (an empty centre has none: two tabs in a row would both
        // stop at it), and one tab to reach each.
        val stops = used.filter { it > 0 }.joinToString("") { if (it == 1) "<w:tab w:val=\"center\" w:pos=\"${width / 2}\"/>" else "<w:tab w:val=\"right\" w:pos=\"$width\"/>" }
        val tabs = "<w:pPr><w:tabs>$stops</w:tabs></w:pPr>"
        val body = StringBuilder()
        for (z in used) { if (z > 0) body.append("<w:r><w:tab/></w:r>"); body.append(zones[z]) }
        return "<w:p>$tabs$body</w:p>"
    }
}
