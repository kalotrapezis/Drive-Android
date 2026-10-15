package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreaksTest {
    private val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private fun docx(body: String, cols: Int): ByteArray {
        val parts = OfficeFormats.unzip(OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("x"))))), OfficeKind.Docx))
        val path = documentPath(parts)
        val xml = String(parts.getValue(path)).replace(Regex("""<w:body>[\s\S]*?(<w:sectPr)"""), "<w:body>$body$1").replace("</w:sectPr>", """<w:cols w:num="$cols" w:space="720"/></w:sectPr>""")
        return OfficeFormats.zip(LinkedHashMap(parts).also { it[path] = xml.toByteArray() }, null)
    }
    private fun xmlOf(bytes: ByteArray) = OfficeFormats.unzip(bytes).let { String(it.getValue(documentPath(it))) }

    @Test fun `a column break in a two-column page is kept, no longer copy-only`() {
        val file = docx("""<w:p><w:r><w:t>Left column</w:t></w:r><w:r><w:br w:type="column"/></w:r><w:r><w:t>Right column</w:t></w:r></w:p>""", 2)
        val doc = OfficeFormats.read(file, OfficeKind.Docx)
        assertTrue(doc.blocking.toString(), doc.blocking.isEmpty())
        assertEquals(listOf("Left column", "Right column"), doc.paras.map { it.text })
        assertTrue(doc.paras[1].keep!!.parts.any { it.first == COLUMN_BREAK })
        assertTrue(DocStyles.of(OfficeKind.Docx, file).look(doc.paras[1]).columnBreakBefore)
        val saved = OfficeFormats.write(doc, OfficeKind.Docx, file)
        val xml = xmlOf(saved)
        assertTrue(xml, Regex("""<w:p>(?:<w:pPr>.*?</w:pPr>)?<w:r><w:br w:type="column"/></w:r><w:r>.*?Right column""").containsMatchIn(xml))
        assertTrue(xml, "<w:columnBreak" !in xml)
        assertEquals(doc.paras.map { it.text to it.keep }, OfficeFormats.read(saved, OfficeKind.Docx).paras.map { it.text to it.keep })
    }

    @Test fun `a column break put in, in both kinds`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("One"))), DocPara(listOf(DocRun("Two")), keep = OfficeFormats.withColumnBreak(null, kind, true))))
            // In columns (in a page of one column Word's column break is the page break it looks like, and read so).
            val bytes = OfficeFormats.write(doc, kind, if (kind == OfficeKind.Docx) docx("", 2) else null)
            val back = OfficeFormats.read(bytes, kind)
            assertTrue(kind.name, DocStyles.of(kind, bytes).look(back.paras[1]).columnBreakBefore)
            assertTrue(kind.name, !DocStyles.of(kind, bytes).look(back.paras[0]).columnBreakBefore)
        }
    }

    @Test fun `a section break ends a section at the paragraph, the next starting as asked`() {
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("First"))), DocPara(listOf(DocRun("Second"))), DocPara(listOf(DocRun("Third")))))
        val file = OfficeFormats.write(doc, OfficeKind.Docx)
        val body = SectionBreaks.bodySect(xmlOf(file))!!
        val (one, newBody) = SectionBreaks.insert(doc, 0, body, "continuous")
        assertEquals("continuous", SectionBreaks.typeOf(newBody!!))
        val withBody = OfficeFormats.zip(SectionBreaks.withBodySect(OfficeFormats.unzip(file), newBody), null)
        // A second one, before the last paragraph: the body's section is cut again, the new one after it a new page.
        val (two, last) = SectionBreaks.insert(one, 1, SectionBreaks.bodySect(xmlOf(withBody))!!, "nextPage")
        val final = OfficeFormats.zip(SectionBreaks.withBodySect(OfficeFormats.unzip(withBody), last!!), null)
        val saved = OfficeFormats.write(two, OfficeKind.Docx, final)
        val back = OfficeFormats.read(saved, OfficeKind.Docx)
        assertTrue(back.blocking.toString(), back.blocking.isEmpty())
        val sections = DocStyles.of(OfficeKind.Docx, saved).sections(back.paras)
        assertEquals(3, sections.zipWithNext().count { (a, b) -> a !== b } + 1)
        assertEquals(listOf(false, true, false), sections.map { it.continuous })
        // w:type in its place: before the page's size.
        assertTrue(Regex("""<w:type w:val="nextPage"/><w:pgSz""").containsMatchIn(xmlOf(saved)))
    }

    @Test fun `a section's columns and page set on its own sectPr, in Word's order`() {
        val sect = """<w:sectPr><w:headerReference w:type="default" r:id="rId8"/><w:type w:val="continuous"/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/><w:docGrid w:linePitch="360"/></w:sectPr>"""
        val two = SectionBreaks.withColumns(sect, 2, 36f)
        assertTrue(two, two.contains("""<w:cols w:num="2" w:space="720"/><w:docGrid"""))
        assertEquals(two, SectionBreaks.withColumns(SectionBreaks.withColumns(sect, 3, 36f), 2, 36f)) // one w:cols, replaced
        val wide = PageSetup.docxSection(two, DocPage(width = 842f, height = 595f, left = 36f, right = 36f, columns = 2))
        assertTrue(wide, "w:orient=\"landscape\"" in wide && "<w:type w:val=\"continuous\"/>" in wide && "rId8" in wide && "w:header=\"708\"" in wide)
    }
}
