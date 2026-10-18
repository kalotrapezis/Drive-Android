package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Character spacing (Words' and Slides' Home › Spacing): read, set and kept in Word's, LibreOffice's and PowerPoint's forms. */
class CharacterSpacingTest {
    private val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0

    @Test fun `read and set in each form`() {
        assertEquals(1.5f, LetterLooks.spacingOf("""<w:b/><w:spacing w:val="30"/>"""), 0.001f)
        assertEquals(-0.5f, LetterLooks.spacingOf("""<w:spacing w:val="-10"/>"""), 0.001f)
        assertEquals(1.5f, LetterLooks.spacingOf(""" fo:letter-spacing="0.0529cm""""), 0.01f)
        assertEquals(0f, LetterLooks.spacingOf(""" fo:letter-spacing="normal""""), 0f)
        assertFalse(LetterLooks.of("<w:b/>").any); assertTrue(LetterLooks.of("""<w:spacing w:val="30"/>""").any)
        val r = DocRun("wide", font = """<w:caps/><w:spacing w:val="10"/>""")
        assertEquals("""<w:caps/><w:spacing w:val="60"/>""", LetterLooks.withSpacing(r, OfficeKind.Docx, 3f).font)
        assertEquals("<w:caps/>", LetterLooks.withSpacing(r, OfficeKind.Docx, 0f).font)
        assertEquals(""" fo:letter-spacing="1.50pt"""", LetterLooks.withSpacing(DocRun("x", font = """ fo:letter-spacing="2pt""""), OfficeKind.Odt, 1.5f).font)
    }

    /** Words: a run's spacing written to .docx and .odt and read back; LibreOffice keeps it either way. */
    @Test fun `kept in a Words document`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("plain "), LetterLooks.withSpacing(DocRun("spaced out"), kind, 3f)))))
            val bytes = OfficeFormats.write(doc, kind)
            fun check(runs: List<DocRun>, how: String) = assertEquals(how, 3f, LetterLooks.of(runs.first { "spaced" in it.text }.font).spacing, 0.05f)
            check(OfficeFormats.read(bytes, kind).paras.flatMap { it.runs }, "written, $kind")
            if (lo) {
                val dir = scratchDir("spacing"); val ext = if (kind == OfficeKind.Docx) "docx" else "odt"; val other = if (kind == OfficeKind.Docx) "odt" else "docx"
                val f = File(dir, "s.$ext").apply { writeBytes(bytes) }
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", dir.path, f.path).redirectErrorStream(true).start().waitFor()
                val back = OfficeFormats.read(File(dir, "s.$other").readBytes(), if (other == "docx") OfficeKind.Docx else OfficeKind.Odt)
                check(back.paras.flatMap { it.runs }, "LibreOffice's $other of $ext")
            }
        }
    }

    /** Slides: a box's letters spaced (PowerPoint's spc, LibreOffice's fo:letter-spacing); read back, and by LibreOffice. */
    @Test fun `kept in a deck`() {
        fun words(i: SlideItem) = (i as? SlideItem.Shape)?.text?.paras?.joinToString(" ") { p -> p.runs.joinToString("") { it.text } }
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val red = deck.slides[2].items.first { words(it) == "Bold red words, 28 pt" }
            val e = SlidesEdit.start(deck).copy(looks = mapOf((2 to red.anim!!) to SlidesEdit.Look(letterSpacing = 1.5f)))
            fun check(d: Deck, how: String) = assertEquals(how, 1.5f, LetterLooks.of((d.slides[2].items.first { words(it) == "Bold red words, 28 pt" } as SlideItem.Shape)
                .text!!.paras.flatMap { it.runs }.first { it.text.isNotBlank() }.font).spacing, 0.05f)
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = scratchDir("spc").resolve("spc-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
        }
    }
}
