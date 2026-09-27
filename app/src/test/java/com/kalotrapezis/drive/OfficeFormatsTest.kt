package com.kalotrapezis.drive

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeFormatsTest {
    private val sample = OfficeDoc(listOf(
        DocPara(listOf(DocRun("Title")), heading = 1),
        DocPara(listOf(DocRun("Plain, "), DocRun("bold", bold = true), DocRun(" and "), DocRun("red italic", italic = true, color = 0xFF0000),
            DocRun(" big", sizePt = 18f), DocRun(" marked", highlight = 0xFFFF00), DocRun(" a link", link = "https://example.com/?a=1&b=2"))),
        DocPara(listOf(DocRun("Centered  two spaces\tand a tab", underline = true, strike = true)), align = DocAlign.Center),
        DocPara(listOf(DocRun("one")), list = DocList(true, 0, 1)),
        DocPara(listOf(DocRun("inner")), list = DocList(false, 1)),
        DocPara(listOf(DocRun("two")), list = DocList(true, 0, 2)),
        DocPara(listOf(DocRun("Between"), DocRun("\u2028second line", bold = true))),
        DocPara(listOf(DocRun("five")), list = DocList(true, 0, 5)),
        DocPara(listOf(DocRun("Ελληνικά <&> \"quotes\"")), align = DocAlign.Justify),
    ))

    private fun roundTrip(kind: OfficeKind) {
        val bytes = OfficeFormats.write(sample, kind)
        val back = OfficeFormats.read(bytes, kind)
        assertEquals(emptySet<String>(), back.unsupported)
        assertEquals(sample.paras, back.paras)
        // Written into itself again: nothing drifts.
        val again = OfficeFormats.read(OfficeFormats.write(back, kind, bytes), kind)
        assertEquals(sample.paras, again.paras)
    }

    @Test fun `a new docx reads back as written`() = roundTrip(OfficeKind.Docx)
    @Test fun `a new odt reads back as written`() = roundTrip(OfficeKind.Odt)

    @Test fun `a docx keeps its other parts and names a table`() {
        val parts = OfficeFormats.unzip(OfficeFormats.write(sample, OfficeKind.Docx))
        parts["word/header1.xml"] = "<hdr/>".toByteArray()
        val xml = String(parts.getValue("word/document.xml")).replace("</w:body>", "").replace("<w:sectPr", "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>cell</w:t></w:r></w:p></w:tc></w:tr></w:tbl><w:sectPr") + ""
        parts["word/document.xml"] = xml.replace("</w:document>", "</w:body></w:document>").toByteArray()
        val withTable = OfficeFormats.zip(parts)
        val doc = OfficeFormats.read(withTable, OfficeKind.Docx)
        assertEquals(setOf("tables"), doc.unsupported)
        assertEquals("cell", doc.paras.last().text)
        val saved = OfficeFormats.unzip(OfficeFormats.write(doc, OfficeKind.Docx, withTable))
        assertEquals("<hdr/>", String(saved.getValue("word/header1.xml")))
        assertTrue("<w:pgSz" in String(saved.getValue("word/document.xml")))
    }

    @Test fun `an odt starts with its stored mimetype`() {
        val bytes = OfficeFormats.write(sample, OfficeKind.Odt)
        assertEquals("mimetype", String(bytes, 30, 8))
        assertEquals("application/vnd.oasis.opendocument.text", String(bytes, 38, 39))
    }

    /** The user's own documents on this computer, when they are here: each must read without an error. */
    @Test fun `real documents read`() {
        val home = File(System.getProperty("user.home"), "Έγγραφα")
        home.walkTopDown().maxDepth(3).filter { OfficeKind.of(it.name) != null }.take(40).forEach { f ->
            val kind = OfficeKind.of(f.name)!!
            val bytes = f.readBytes()
            val doc = OfficeFormats.read(bytes, kind)
            println("${f.name}: ${doc.paras.size} paragraphs; not kept: ${doc.unsupported.ifEmpty { setOf("—") }.joinToString()}")
            // Written back (into memory only) and read again: the same text.
            val again = OfficeFormats.read(OfficeFormats.write(doc, kind, bytes), kind)
            assertEquals(f.name, doc.paras.map { it.text }, again.paras.map { it.text })
        }
    }
}
