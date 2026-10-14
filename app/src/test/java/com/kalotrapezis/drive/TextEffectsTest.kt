package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEffectsTest {
    private fun wordArt(kind: OfficeKind, fx: TextEffects): TextEffects {
        val raw = PictureXml.newTextBox(kind, 200f, 50f, listOf("Big words"), 0x4472C4, fx)
        val img = PictureXml.withWrap(DocImage("w", null, 200f, 50f, raw), PictureWrap.Square, kind)
        val back = OfficeFormats.read(OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("Before "), DocRun(OBJECT_MARK.toString(), image = img))))), kind), kind)
        assertTrue(kind.name, back.blocking.isEmpty())
        val run = back.paras.flatMap { it.runs }.mapNotNull { it.image?.box }.single().first().runs.first()
        assertEquals("Big words", run.text)
        return TextEffects.of(run.font)
    }

    @Test fun `WordArt's outline, shadow and glow are written as Word's and read back`() {
        val fx = TextEffects(outline = 0x000000, shadow = true, glow = 0xFFC000)
        assertEquals(fx, wordArt(OfficeKind.Docx, fx))
        assertEquals(TextEffects(shadow = true), wordArt(OfficeKind.Docx, TextEffects(shadow = true)))
        assertEquals(TextEffects(), wordArt(OfficeKind.Docx, TextEffects()))
    }

    @Test fun `LibreOffice keeps the outline and the shadow`() {
        val back = wordArt(OfficeKind.Odt, TextEffects(outline = 0, shadow = true))
        assertTrue(back.outline != null); assertTrue(back.shadow)
    }

    @Test fun `Word's glow comes before its shadow and outline, each saying its namespace`() {
        val xml = TextEffects(outline = 0xFF0000, shadow = true, glow = 0x00B050).docx()
        assertTrue(xml.indexOf("<w14:glow") < xml.indexOf("<w14:shadow") && xml.indexOf("<w14:shadow") < xml.indexOf("<w14:textOutline"))
        assertEquals(3, Regex("""xmlns:w14=""").findAll(xml).count())
        assertEquals(TextEffects(0xFF0000, true, 0x00B050), TextEffects.of(xml))
    }
}
