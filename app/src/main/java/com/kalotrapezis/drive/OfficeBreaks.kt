package com.kalotrapezis.drive

/**
 * Layout › Breaks › Section (Phase 2, Word files): a section ends with a paragraph whose pPr holds a sectPr; the
 * document's last section's is the body's own. A sectPr's w:type says how its section starts (on a new page, or on
 * down the same one), so a break put in makes the paragraph at the break end a section like the one it was in, and
 * the section after it starts as asked.
 */
internal object SectionBreaks {
    private val SECT = Regex("""<w:sectPr\b[^>]*?(?:/>|>[\s\S]*?</w:sectPr>)""")
    /** What comes after w:type in a sectPr (the schema's order). */
    private val AFTER_TYPE = Regex("""<w:(pgSz|pgMar|paperSrc|pgBorders|lnNumType|pgNumType|cols|formProt|vAlign|noEndnote|titlePg|textDirection|bidi|rtlGutter|docGrid|printerSettings|sectPrChange)\b""")

    /** [sect] starting its section as [type] ("nextPage", "continuous", "evenPage", "oddPage", "nextColumn"). */
    fun withType(sect: String, type: String): String {
        val self = sect.trimEnd().endsWith("/>") && "</w:sectPr>" !in sect
        val open = if (self) sect.trimEnd().dropLast(2) + ">" else sect.substring(0, sect.indexOf('>') + 1)
        var inner = if (self) "" else sect.substring(sect.indexOf('>') + 1, sect.lastIndexOf("</w:sectPr>"))
        inner = inner.replace(Regex("""<w:type\b[^>]*/>"""), "")
        val at = AFTER_TYPE.find(inner)?.range?.first ?: inner.length
        return open + inner.substring(0, at) + """<w:type w:val="$type"/>""" + inner.substring(at) + "</w:sectPr>"
    }
    /** [sect] with [n] columns [gapPt] apart (Word's w:cols, in its place: after the page numbering, before the rest). */
    fun withColumns(sect: String, n: Int, gapPt: Float): String {
        val self = sect.trimEnd().endsWith("/>") && "</w:sectPr>" !in sect
        val open = if (self) sect.trimEnd().dropLast(2) + ">" else sect.substring(0, sect.indexOf('>') + 1)
        var inner = if (self) "" else sect.substring(sect.indexOf('>') + 1, sect.lastIndexOf("</w:sectPr>"))
        inner = inner.replace(Regex("""<w:cols\b[^>]*?(?:/>|>[\s\S]*?</w:cols>)"""), "")
        val at = Regex("""<w:(formProt|vAlign|noEndnote|titlePg|textDirection|bidi|rtlGutter|docGrid|printerSettings|sectPrChange)\b""").find(inner)?.range?.first ?: inner.length
        return open + inner.substring(0, at) + """<w:cols w:num="$n" w:space="${Math.round(gapPt * 20)}"/>""" + inner.substring(at) + "</w:sectPr>"
    }
    fun typeOf(sect: String) = Regex("""<w:type\b[^>]*w:val="(\w+)"""").find(sect)?.groupValues?.get(1) ?: "nextPage"

    /** The body's own sectPr (the last section's) in a document.xml. */
    fun bodySect(documentXml: String): String? {
        val end = documentXml.lastIndexOf("</w:body>").takeIf { it >= 0 } ?: return null
        val start = documentXml.lastIndexOf("<w:sectPr", end).takeIf { it >= 0 } ?: return null
        return SECT.find(documentXml, start)?.value?.takeIf { documentXml.indexOf("</w:p>", start).let { p -> p < 0 || p > end } }
    }
    fun withBodySect(parts: Map<String, ByteArray>, sect: String): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap(parts)
        val path = documentPath(out)
        val xml = String(out.getValue(path), Charsets.UTF_8)
        val old = bodySect(xml)
        out[path] = (if (old != null) xml.substring(0, xml.lastIndexOf(old)) + sect + xml.substring(xml.lastIndexOf(old) + old.length)
            else xml.replace("</w:body>", "$sect</w:body>")).toByteArray()
        return out
    }

    /**
     * [doc] with a section ending at paragraph [at], the next section starting as [type]; the second is the body's new
     * sectPr when the section after the break is the document's last (else null: it is a paragraph's, changed in [doc]).
     */
    fun insert(doc: OfficeDoc, at: Int, body: String, type: String): Pair<OfficeDoc, String?> {
        val next = (at + 1 until doc.paras.size).firstOrNull { j -> doc.paras[j].keep?.parts?.any { it.first == "sectPr" } == true }
        fun sectOf(j: Int) = doc.paras[j].keep!!.parts.first { it.first == "sectPr" }.second
        val own = doc.paras[at].keep?.parts?.firstOrNull { it.first == "sectPr" }?.second
        val following = next?.let(::sectOf) ?: body
        val paras = doc.paras.toMutableList()
        // The part before the break: a section as the one it was in, starting as that one did.
        if (own == null) paras[at] = paras[at].let { p -> val k = p.keep ?: DocKeep(); p.copy(keep = k.copy(parts = k.parts + ("sectPr" to following))) }
        val changed = withType(following, type)
        if (next != null) { paras[next] = paras[next].let { p -> p.copy(keep = p.keep!!.copy(parts = p.keep.parts.map { if (it.first == "sectPr") it.first to changed else it })) }; return doc.copy(paras = paras) to null }
        return doc.copy(paras = paras) to changed
    }
}
