package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Word 97–2003 (.doc) and Rich Text (.rtf) read into Writer's document, then saved as .docx. */
class LegacyDocsTest {
    private fun res(n: String) = javaClass.classLoader!!.getResource(n).readBytes()

    private fun check(name: String) {
        // LibreOffice 26.2's .doc and .rtf of the same Word file: a heading, looks, a link, a 2×2 table, a page break.
        val doc = LegacyDocs.read(res(name), name)
        val texts = doc.paras.map { it.text }
        assertEquals(name, "Παλιό έγγραφο — old document", texts[0])
        assertEquals(name, 1, doc.paras[0].heading)
        val p = doc.paras[1]
        assertEquals(name, "Plain, bold, red italic, big underlined.", p.text)
        assertEquals(name, DocAlign.Center, p.align)
        assertTrue(name, p.runs.single { it.text.startsWith("bold") }.bold)
        p.runs.single { it.text.startsWith("red") }.let { assertTrue(name, it.italic); assertEquals(name, 0xC00000, it.color) }
        p.runs.single { it.text.startsWith("big") }.let { assertTrue(name, it.underline); assertEquals(name, 16f, it.sizePt) }
        assertEquals(name, "https://example.com/", doc.paras[2].runs.single { it.text == "example" }.link)
        val table = doc.paras.single { it.table != null }.table!!
        assertEquals(name, listOf(listOf("A1", "B1"), listOf("A2", "B2")), table.rows.map { r -> r.cells.map { c -> c.paras.joinToString("") { it.text } } })
        assertTrue(name, texts.contains("After the table."))
        val second = doc.paras.single { it.text == "Second page." }
        assertTrue(name, second.keep?.parts?.any { it.first == "pageBreakBefore" } == true)
        // It is saved only as a copy (.docx) and reads back the same.
        assertTrue(name, doc.blocking.contains(LegacyDocs.OLDER))
        val back = OfficeFormats.read(OfficeFormats.write(doc, OfficeKind.Docx), OfficeKind.Docx)
        assertEquals(name, doc.paras.map { it.text }, back.paras.map { it.text })
    }

    @Test fun `a doc file`() = check("old.doc")
    @Test fun `an rtf file`() = check("old.rtf")

    @Test fun `rtf's own letters - code pages, unicode, escapes`() {
        val rtf = """{\rtf1\ansi\ansicpg1253\deff0{\fonttbl{\f0\fcharset161 Arial;}}{\colortbl;\red255\green0\blue0;}\pard\qr \'c1\'e8\'de\'ed\'e1 ${"\\"}u8364? \{x\} {\b\cf1 B}\par}"""
        val doc = LegacyDocs.read(rtf.toByteArray(Charsets.ISO_8859_1), "a.rtf")
        assertEquals("Αθήνα € {x} B", doc.paras[0].text)
        assertEquals(DocAlign.End, doc.paras[0].align)
        doc.paras[0].runs.last().let { assertTrue(it.bold); assertEquals(0xFF0000, it.color) }
    }
}
