package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** LibreOffice's sections (text:section) kept, shown in their columns, and made by Layout › Breaks › Section in an .odt. */
class OdtSectionsTest {
    private fun content(bytes: ByteArray) = String(OfficeFormats.unzip(bytes).getValue("content.xml"), Charsets.UTF_8)

    @Test fun `a file's sections are kept round their paragraphs`() {
        // LibreOffice 26.2's conversion of a Word file: a title, a two-column section, a next-page section (another page style).
        val bytes = javaClass.classLoader!!.getResource("sections.odt").readBytes()
        val doc = OfficeFormats.read(bytes, OfficeKind.Odt)
        assertTrue(doc.blocking.toString(), doc.blocking.isEmpty())
        assertEquals(listOf<String>(), OdtSections.tags(doc.paras[0]))
        OdtSections.tags(doc.paras[2]).single().let { assertTrue(it, """text:style-name="Sect1"""" in it && """text:name="TextSection"""" in it) }
        val xml = content(OfficeFormats.write(doc, OfficeKind.Odt, bytes))
        assertEquals(2, Regex("<text:section ").findAll(xml).count())
        assertEquals(2, Regex("</text:section>").findAll(xml).count())
        assertTrue(xml, Regex("""</text:p><text:section text:name="TextSection" text:style-name="Sect1"><text:p[^>]*/?>""").containsMatchIn(xml))
        assertTrue("its style stays", Regex("""<style:style (style:name="Sect1" style:family="section"|style:family="section" style:name="Sect1")>""").containsMatchIn(xml))
        // The pages: the title across, the section's text in two columns on down the same page; the last part a page of its own style.
        val sections = DocStyles.of(OfficeKind.Odt, bytes).sections(doc.paras)
        assertEquals(1, sections[0].page.columns)
        assertEquals(2, sections[2].page.columns); assertTrue(sections[2].continuous)
        assertFalse(sections.last().continuous)
    }

    @Test fun `a section break in an odt starts a section, columns set the cursor's`() {
        val start = OfficeDoc(listOf("Title", "One", "Two", "Three").map { DocPara(listOf(DocRun(it))) })
        val broken = OdtSections.insert(start, 1, false) { it }
        assertEquals(listOf(0, 1, 1, 1), broken.paras.map { OdtSections.tags(it).size })
        val two = OdtSections.withColumns(broken, 2, 2)!!
        val bytes = OfficeFormats.write(two, OfficeKind.Odt)
        val xml = content(bytes)
        assertTrue(xml, """>Title</text:p><text:section text:style-name="Tetra_20_sect_2" text:name="Section1"><text:p text:style-name="Text_20_body">One</text:p>""" in xml)
        assertTrue(xml, """<style:style style:name="Tetra_20_sect_2" style:family="section">""" in xml && """fo:column-count="2"""" in xml)
        // Another break further on (next page): a second section, its first paragraph after a page break.
        val again = OdtSections.insert(OfficeFormats.read(bytes, OfficeKind.Odt), 3, true) { it.copy(keep = OfficeFormats.withPageBreak(it.keep, OfficeKind.Odt, true)) }
        fun same(t: List<String>) = t.map { tag -> Regex("""\s(\S+)="([^"]*)"""").findAll(tag).map { it.value }.sorted().joinToString() }
        assertEquals("Section2", Regex("""text:name="(\w+)"""").find(OdtSections.tags(again.paras[3]).single())!!.groupValues[1])
        assertEquals(same(OdtSections.tags(again.paras[1])), same(OdtSections.tags(again.paras[2])))
        val back = OfficeFormats.read(OfficeFormats.write(again, OfficeKind.Odt, bytes), OfficeKind.Odt)
        assertEquals(again.paras.map { same(OdtSections.tags(it)) }, back.paras.map { same(OdtSections.tags(it)) })
        assertTrue(DocStyles.of(OfficeKind.Odt, OfficeFormats.write(again, OfficeKind.Odt, bytes)).look(back.paras[3]).pageBreakBefore)
        assertTrue(back.blocking.isEmpty())
    }
}
