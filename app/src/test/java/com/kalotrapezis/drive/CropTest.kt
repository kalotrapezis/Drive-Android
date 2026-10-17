package com.kalotrapezis.drive

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Cropped pictures in Words, Tables and .odp slides (17 Oct; cropped.py): a 900 × 600 picture with 2/9 cut from each side
 * and 1/10 from the top, shown as LibreOffice shows it (only the sun); a save keeps the crop.
 */
class CropTest {
    private val want = floatArrayOf(0.22222f, 0.1f, 0.22222f, 0f)
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name).readBytes()
    private fun picture(doc: OfficeDoc) = doc.paras.flatMap { p -> p.runs.mapNotNull { it.image } }.single()

    @Test fun `a docx picture's srcRect, kept on saving`() {
        val bytes = resource("cropped.docx")
        val doc = OfficeFormats.read(bytes, OfficeKind.Docx)
        assertArrayEquals(want, Slides.srcRect(picture(doc).raw), 1e-4f)
        val back = OfficeFormats.read(OfficeFormats.write(doc, OfficeKind.Docx, bytes), OfficeKind.Docx)
        assertArrayEquals(want, Slides.srcRect(picture(back).raw), 1e-4f)
    }

    @Test fun `an odt picture's fo clip, kept on saving`() {
        val bytes = resource("cropped.odt")
        fun crop(doc: OfficeDoc, file: ByteArray): FloatArray? {
            val img = picture(doc)
            val clip = Regex("""fo:clip="([^"]*)"""").findAll(img.look!!).last().groupValues[1]
            return Slides.clip(clip, OfficeFormats.unzip(file).getValue(img.part!!))
        }
        val doc = OfficeFormats.read(bytes, OfficeKind.Odt)
        assertArrayEquals(want, crop(doc, bytes), 1e-3f)
        val saved = OfficeFormats.write(doc, OfficeKind.Odt, bytes)
        assertArrayEquals(want, crop(OfficeFormats.read(saved, OfficeKind.Odt), saved), 1e-3f)
    }

    @Test fun `an xlsx picture's srcRect`() {
        val pic = Sheets.read(resource("cropped.xlsx"), "cropped.xlsx").sheet(0).drawings.single { it.image != null }
        assertArrayEquals(want, pic.crop, 1e-4f)
    }

    @Test fun `an odp picture's fo clip, kept when moved and saved`() {
        val src = File("src/test/resources/cropped.odp")
        fun crop(d: Deck) = d.slides[0].items.filterIsInstance<SlideItem.Picture>().single().crop
        val deck = Slides.read(src)
        assertArrayEquals(want, crop(deck), 1e-3f)
        val id = deck.slides[0].items.filterIsInstance<SlideItem.Picture>().single().anim!!
        val out = File.createTempFile("cropped", ".odp")
        SlidesOdpEdit.save(src, deck, SlidesEdit.start(deck).copy(frames = mapOf((0 to id) to listOf(100f, 100f, 180f, 194f))), out)
        assertArrayEquals(want, crop(Slides.read(out)), 1e-3f)
    }

    /** A picture's size at its resolution: a PNG that says none is at 96 dpi (LibreOffice's), one with pHYs at its own. */
    @Test fun `a picture's size in points`() {
        val png = java.util.zip.ZipFile(File("src/test/resources/cropped.odp")).use { z -> z.getInputStream(z.entries().asSequence().first { it.name.startsWith("Pictures/") }).readBytes() }
        assertEquals(675f to 450f, Slides.picturePt(png))
        // the same with pHYs at 300 dpi (11811 pixels a metre), put after IHDR
        val phys = byteArrayOf(0, 0, 0, 9, 'p'.code.toByte(), 'H'.code.toByte(), 'Y'.code.toByte(), 's'.code.toByte(), 0, 0, 0x2E, 0x23, 0, 0, 0x2E, 0x23, 1, 0, 0, 0, 0)
        val (w, h) = Slides.picturePt(png.copyOfRange(0, 33) + phys + png.copyOfRange(33, png.size))!!
        assertEquals(216f, w, 0.1f); assertEquals(144f, h, 0.1f)
        assertEquals(null, Slides.clip("rect(0cm, 0cm, 0cm, 0cm)", png))
    }
}
