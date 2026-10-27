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

    @Test fun `WordArt bent along an arch, in both formats, through saving and Edit text`() {
        for (kind in OfficeKind.entries) for (bend in TextWarp.entries) {
            val raw = PictureXml.newTextBox(kind, 300f, 120f, listOf("Bent words"), art = 0xFF4472C4.toInt(), warp = bend)
            assertEquals("$kind $bend", bend, TextWarp.of(raw))
            if (kind == OfficeKind.Docx) assertTrue(raw, "fromWordArt=\"1\"" in raw && "<a:prstTxWarp prst=\"${bend.word}\">" in raw)
            else assertTrue(raw, "draw:type=\"${bend.fontwork}\"" in raw && "draw:text-path=\"true\"" in raw)
            val img = DocImage("w", null, 300f, 120f, raw, box = listOf(DocPara(listOf(DocRun("Bent words")))))
            val back = OfficeFormats.read(OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("x"), DocRun(OBJECT_MARK.toString(), image = img))))), kind), kind)
            val again = back.paras[0].runs.mapNotNull { it.image }.single()
            assertEquals("$kind $bend", bend, TextWarp.of(again.raw))
            assertEquals("$kind $bend", bend, TextWarp.of(PictureXml.boxText(again.raw!!, kind, listOf("Other words")))) // Edit text keeps the bend
        }
        // An arch's middle stands higher than its ends; a smile's lower.
        val up = TextWarp.ArchUp.line(200f, 100f); assertTrue(up[24].second < up[0].second)
        val down = TextWarp.ArchDown.line(200f, 100f); assertTrue(down[24].second > down[0].second)
    }

    /** WordArt's colour, effects and bend changed after it is made (Edit text on a WordArt), in both kinds; LibreOffice draws its new words. */
    @Test fun `WordArt's look is changed after`() {
        for (kind in OfficeKind.entries) {
            val odt = kind == OfficeKind.Odt
            val raw = PictureXml.newTextBox(kind, 300f, 120f, listOf("First words"), art = 0xFF4472C4.toInt(), effects = TextEffects(shadow = true), warp = TextWarp.ArchUp)
            assertEquals(kind.name, 0x4472C4 to TextEffects(shadow = true), TextWarp.art(raw))
            // orange, outlined, no shadow, straight
            val fx = TextEffects(outline = 0xED7D31)
            val changed = TextWarp.removed(PictureXml.boxText(raw, kind, listOf("New words"), 0xFFED7D31.toInt(), fx), odt)
            assertEquals(kind.name, null, TextWarp.of(changed))
            assertEquals(kind.name, 0xED7D31, TextWarp.art(changed)?.first)
            assertEquals(kind.name, true, TextWarp.art(changed)?.second?.outline != null && TextWarp.art(changed)?.second?.shadow == false)
            // and bent again, as a wave
            assertEquals(kind.name, TextWarp.Wave, TextWarp.of(TextWarp.applied(changed, TextWarp.Wave, odt)))
            val img = PictureXml.withWrap(DocImage("w", null, 300f, 120f, changed, box = listOf(DocPara(listOf(DocRun("New words"))))), PictureWrap.Square, kind)
            val bytes = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("x"), DocRun(OBJECT_MARK.toString(), image = img))))), kind)
            val back = OfficeFormats.read(bytes, kind).paras[0].runs.mapNotNull { it.image }.single()
            assertEquals(kind.name, listOf("New words"), back.box?.map { it.text })
            assertEquals(kind.name, 0xED7D31, TextWarp.art(back.raw)?.first)
            if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
                val dir = scratchDir("lo")
                val f = java.io.File(dir, "a.${kind.extension}").apply { writeBytes(bytes) }
                soffice("pdf", dir.path, f.path)
                val text = ProcessBuilder("pdftotext", java.io.File(dir, "a.pdf").path, "-").redirectErrorStream(true).start().inputStream.bufferedReader().readText()
                assertTrue("$kind: $text", "New words" in text && "First" !in text)
            }
        }
    }
}
