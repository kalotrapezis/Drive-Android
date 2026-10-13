package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldsTest {
    private fun seq(instr: String, shown: String) = DocRun(shown, cite = FIELD_CITE + instr)
    private fun caption(vararg runs: DocRun) = DocPara(runs.toList())

    @Test fun `figures and tables are counted again each on their own`() {
        val doc = OfficeDoc(listOf(
            caption(DocRun("Figure "), seq("SEQ Figure \\* ARABIC", "1"), DocRun(": a")),
            caption(DocRun("Table "), seq("SEQ Table \\* ARABIC", "7")),
            caption(DocRun("Figure "), seq("SEQ Figure \\* ARABIC", "3"), DocRun(": c")), // the figure between was deleted
            caption(DocRun("Figure "), seq("SEQ Figure \\* ROMAN", "x")),
            caption(DocRun("see "), seq("SEQ Figure \\c", "?")),
            caption(DocRun("Appendix "), seq("SEQ Appendix \\r 5 \\* ALPHABETIC", "?")),
        ))
        assertEquals(listOf("Figure 1: a", "Table 1", "Figure 2: c", "Figure III", "see 3", "Appendix E"), Fields.update(doc).paras.map { it.text })
    }

    @Test fun `a field shown over several runs becomes one number, other fields are left alone`() {
        val f = FIELD_CITE + "SEQ Figure"
        val date = DocRun("13/10/2026", cite = FIELD_CITE + "DATE")
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Figure "), DocRun("1", cite = f), DocRun("2", bold = true, cite = f), DocRun(" "), date))))
        assertEquals("Figure 1 13/10/2026", Fields.update(doc).paras[0].text)
    }

    @Test fun `a caption is saved as Word makes one`() {
        val caption = DocPara(listOf(DocRun("Figure "), DocRun("1", cite = FIELD_CITE + "SEQ Figure \\* ARABIC"), DocRun(": The map")), keep = DocKeep("Caption"))
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Text"))), caption))
        val parts = OfficeFormats.unzip(OfficeFormats.write(doc, OfficeKind.Docx))
        val xml = String(parts.getValue("word/document.xml"), Charsets.UTF_8)
        val styles = String(parts.getValue("word/styles.xml"), Charsets.UTF_8)
        org.junit.Assert.assertTrue(xml, Regex("""<w:pStyle w:val="Caption"/>.*?>Figure <.*?SEQ Figure \\\* ARABIC.*?separate.*?>1<.*?fldCharType="end".*?>: The map<""").containsMatchIn(xml))
        org.junit.Assert.assertTrue("w:styleId=\"Caption\"" in styles)
        val back = OfficeFormats.read(OfficeFormats.zip(parts, null), OfficeKind.Docx)
        assertEquals("Figure 1: The map", back.paras[1].text)
        assertEquals(emptySet<String>(), back.blocking)
    }
}
