package com.kalotrapezis.drive

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A PDF page's objects are Words objects: kept as a one-picture .docx and read back as Words has them. */
class PdfObjectsTest {
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 1, 2, 3)

    @Test fun `a picture comes back with its size and bytes, and Word's XML to turn it`() {
        val w = PdfWords.of(DocImage("n1", null, 200f, 100f, bytes = png, wrap = PictureWrap.InFront))
        assertEquals("Image", w.kind)
        assertEquals(200f to 100f, w.laid)
        assertArrayEquals(png, w.bytes())
        assertTrue(w.img.raw.orEmpty(), "<wp:anchor" in w.img.raw.orEmpty())
        val turned = w.with(Arrange.rotated(w.img, 30f)!!)
        assertEquals(30f, PdfWords.read(turned.docx())!!.turn, 0.01f)
    }

    @Test fun `a JPEG comes back too, read twice`() {
        val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
        val w = PdfWords.of(DocImage("n1", null, 200f, 100f, bytes = jpg, extension = "jpg", wrap = PictureWrap.InFront))
        assertArrayEquals(jpg, w.bytes())
        assertArrayEquals(jpg, PdfWords.read(w.docx())!!.bytes())
    }

    @Test fun `a shape and a text box keep their kind`() {
        val shape = PdfWords.of(DocImage("s1", null, 120f, 80f, PictureXml.newShape("ellipse", OfficeKind.Docx, 0xFF0000, 0x000000, 1f, 120f, 80f), wrap = PictureWrap.InFront))
        assertEquals("Shape", shape.kind)
        assertEquals(shape.img.raw, 120f to 80f, shape.laid)
        val box = PdfWords.of(DocImage("t1", null, 150f, 60f, PictureXml.newTextBox(OfficeKind.Docx, 150f, 60f, listOf("Hello", "there")), wrap = PictureWrap.InFront))
        assertEquals("Text box", box.kind)
        assertEquals(listOf("Hello", "there"), box.img.box?.map { it.text })
    }

    @Test fun `a turned picture's drawing fills the box round it`() {
        assertEquals(100f to 50f, turnedBox(100f, 50f, 0f))
        assertEquals(50f to 100f, turnedBox(100f, 50f, 90f))
        // unturned: scaled to its size, at the box's corner
        assertEquals("q 100.0000 0.0000 0.0000 50.0000 0.0000 0.0000 cm /Im0 Do Q", pictureMatrix(100f, 50f, 100f, 50f, 0f))
    }
}
