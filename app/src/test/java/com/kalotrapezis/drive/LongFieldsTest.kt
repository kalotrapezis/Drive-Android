package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongFieldsTest {
    private fun docx(body: String): ByteArray {
        val parts = OfficeFormats.unzip(OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("x"))))), OfficeKind.Docx))
        val path = documentPath(parts)
        val xml = String(parts.getValue(path)).replace(Regex("""<w:body>[\s\S]*?(<w:sectPr)"""), Regex.escapeReplacement("<w:body>$body") + "$1")
        return OfficeFormats.zip(LinkedHashMap(parts).also { it[path] = xml.toByteArray() }, null)
    }
    private fun xmlOf(bytes: ByteArray) = OfficeFormats.unzip(bytes).let { String(it.getValue(documentPath(it))) }.replace("&quot;", "\"") // the same XML either way
    private fun r(t: String) = "<w:r><w:t xml:space=\"preserve\">$t</w:t></w:r>"
    private fun fld(c: String) = "<w:r><w:fldChar w:fldCharType=\"$c\"/></w:r>"
    private fun instr(t: String) = "<w:r><w:instrText xml:space=\"preserve\"> $t </w:instrText></w:r>"

    @Test fun `a contents list or reference list in one paragraph is kept as its field`() {
        val toc = "<w:p>${fld("begin")}${instr("TOC \\o \"1-3\" \\h \\z \\u")}${fld("separate")}<w:r><w:t>Intro .... 1</w:t><w:br/><w:t>Method .... 2</w:t></w:r>${fld("end")}</w:p>"
        val bib = "<w:p>${fld("begin")}${instr("BIBLIOGRAPHY \\l 1033")}${fld("separate")}${r("Example, A. (2026). Testing.")}${fld("end")}</w:p>"
        val file = docx("<w:p>${r("Before")}</w:p>$toc$bib")
        val doc = OfficeFormats.read(file, OfficeKind.Docx)
        assertTrue(doc.blocking.toString(), doc.blocking.isEmpty())
        val xml = xmlOf(OfficeFormats.write(doc, OfficeKind.Docx, file))
        assertTrue(xml, "TOC \\o \"1-3\" \\h \\z \\u" in xml)
        assertTrue(xml, "BIBLIOGRAPHY \\l 1033" in xml)
        assertEquals(2, Regex("""fldCharType="begin"""").findAll(xml).count())
    }

    private val zotero = "<w:p>${fld("begin")}${instr("ADDIN ZOTERO_BIBL {\"uncited\":[]} CSL_BIBLIOGRAPHY")}${fld("separate")}${r("Adams, B. (2020). One.")}</w:p>" +
        "<w:p>${r("Brown, C. (2021). Two.")}</w:p><w:p>${r("Clark, D. (2022). Three.")}${fld("end")}</w:p>"
    // Word's own contents list as a bare field: links to the headings and their page numbers (PAGEREF fields inside it).
    private val wordToc = "<w:p><w:pPr><w:pStyle w:val=\"TOC1\"/></w:pPr>${fld("begin")}${instr("TOC \\o \"1-3\" \\h \\z \\u")}${fld("separate")}" +
        "<w:hyperlink w:anchor=\"_Toc1\" w:history=\"1\">${r("Intro")}<w:r><w:tab/></w:r>${fld("begin")}${instr("PAGEREF _Toc1 \\h")}${fld("separate")}${r("1")}${fld("end")}</w:hyperlink></w:p>" +
        "<w:p><w:pPr><w:pStyle w:val=\"TOC1\"/></w:pPr><w:hyperlink w:anchor=\"_Toc2\" w:history=\"1\">${r("Method")}<w:r><w:tab/></w:r>${fld("begin")}${instr("PAGEREF _Toc2 \\h")}${fld("separate")}${r("2")}${fld("end")}</w:hyperlink></w:p>" +
        "<w:p>${fld("end")}</w:p>"

    @Test fun `a field over paragraphs is kept whole while its words are unchanged`() {
        for (body in listOf(zotero, wordToc)) {
            val file = docx("<w:p>${r("Before")}</w:p>$body<w:p>${r("After")}</w:p>")
            val doc = OfficeFormats.read(file, OfficeKind.Docx)
            assertTrue(doc.blocking.toString(), doc.blocking.isEmpty())
            assertEquals("Before", doc.paras.first().text); assertEquals("After", doc.paras.last().text)
            val xml = xmlOf(OfficeFormats.write(doc, OfficeKind.Docx, file))
            assertTrue(xml, body in xml) // as the file has it, nested fields and links too
            // Read again: the same, still not a copy.
            val again = OfficeFormats.read(OfficeFormats.write(doc, OfficeKind.Docx, file), OfficeKind.Docx)
            assertEquals(doc.paras.map { it.text }, again.paras.map { it.text })
            assertTrue(again.blocking.isEmpty())
        }
    }

    @Test fun `edited, a field over paragraphs is written as its paragraphs, plain`() {
        val file = docx(zotero)
        val doc = OfficeFormats.read(file, OfficeKind.Docx)
        val edited = doc.copy(paras = doc.paras.map { p -> if (p.text.startsWith("Brown")) p.copy(runs = listOf(DocRun("Brown, C. (2021). Two, revised."))) else p })
        val xml = xmlOf(OfficeFormats.write(edited, OfficeKind.Docx, file))
        assertTrue(xml, "Two, revised." in xml && "Clark, D. (2022)" in xml)
        assertFalse(xml, "ZOTERO_BIBL" in xml)
        assertEquals(3, OfficeFormats.read(OfficeFormats.zip(OfficeFormats.unzip(OfficeFormats.write(edited, OfficeKind.Docx, file)), null), OfficeKind.Docx).paras.size)
    }

    @Test fun `a field that never ends still makes a copy`() {
        val doc = OfficeFormats.read(docx("<w:p>${fld("begin")}${instr("TOC")}${fld("separate")}${r("Open")}</w:p><w:p>${r("still in it")}</w:p>"), OfficeKind.Docx)
        assertTrue(doc.blocking.any { "fields" in it })
    }
}
