package com.kalotrapezis.drive

import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PDF like Words (PDF_LIKE_WORDS.md, E1): a shape and a picture put on a page are Words objects — chosen by a tap,
 * moved, sized, turned, saved, read back as Words has them, deleted. MuPDF's native code: on a device,
 * `am instrument -w -e class com.kalotrapezis.drive.PdfObjectsDeviceTest …`.
 */
@RunWith(AndroidJUnit4::class)
class PdfObjectsDeviceTest {
    @Test fun wordsObjectsOnAPage() {
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-objects-test").apply { deleteRecursively() }
        val f = Pdf.newBlank(dir)
        val d = PdfDoc(f)
        val shape = PdfWords.of(DocImage("s1", null, 120f, 80f, PictureXml.newShape("ellipse", OfficeKind.Docx, 0xFF0000, 0x000000, 1f, 120f, 80f), wrap = PictureWrap.InFront))
        val id = d.putWords(0, shape, 200f, 300f)
        val o = d.objectAt(0, Offset(200f, 300f))
        assertNotNull(o); o!!
        assertEquals(id, o.id)
        assertEquals("Shape", o.kind)
        assertEquals(140f, o.rect.left, 0.5f); assertEquals(260f, o.rect.top, 0.5f)

        // moved: put on again by its middle, the old one gone (one object on the page)
        val moved = d.moveObject(0, o, 50f, 20f)
        assertEquals(1, d.objects(0).size)
        assertEquals(250f, d.objectById(0, moved)!!.rect.centerX(), 0.5f)
        // turned a quarter: the box round it the other way round
        val now = d.objectById(0, moved)!!
        val turned = d.putWords(0, now.words!!.with(Arrange.rotated(now.words!!.img, 90f)!!), now.rect.centerX(), now.rect.centerY(), now.id)
        val t = d.objectById(0, turned)!!
        assertEquals(90f, t.turn, 0.01f)
        assertEquals(80f, t.rect.width(), 0.5f); assertEquals(120f, t.rect.height(), 0.5f)

        // a photo: its JPEG itself in the file
        val bmp = android.graphics.Bitmap.createBitmap(64, 32, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2E7D32.toInt()) }
        val jpg = java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        val pic = PdfWords.of(DocImage("n1", null, 200f, 100f, bytes = jpg, extension = "jpg", wrap = PictureWrap.InFront))
        assertTrue(pic.plainPicture)
        d.putWords(0, pic, 300f, 600f)
        assertEquals(listOf("Shape", "Image"), d.objects(0).map { it.kind })
        assertNotNull(d.objectPicture(0, d.objects(0)[1].id, 600))

        d.save(java.io.File(dir, "base.pdf")); d.close()
        val again = PdfDoc(f)
        val back = again.objects(0)
        assertEquals(listOf("Shape", "Image"), back.map { it.kind })
        assertEquals(90f, back[0].turn, 0.01f)
        assertEquals(jpg.size, back[1].words!!.bytes()!!.size)
        // the back one brought to the front, then deleted
        assertTrue(again.restackObject(0, back[0].id, 2))
        assertEquals(listOf("Image", "Shape"), again.objects(0).map { it.kind })
        again.deleteObject(0, back[0].id)
        assertEquals(listOf("Image"), again.objects(0).map { it.kind })
        again.close(); dir.deleteRecursively()
    }

    @Test fun anotherAppsObjectsMoveAndSize() {
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-objects-other").apply { deleteRecursively() }
        val d = PdfDoc(Pdf.newBlank(dir))
        d.ink(0, listOf(listOf(Offset(100f, 100f), Offset(200f, 150f))), 0x000000, 2f)
        val ink = d.objects(0).single()
        assertEquals("Drawing", ink.kind)
        assertTrue(!ink.ours)
        d.moveObject(0, ink, 10f, 10f)
        val moved = d.objects(0).single()
        assertEquals(ink.rect.left + 10f, moved.rect.left, 1f)
        d.sizeOther(0, moved, moved.rect.width() * 2, moved.rect.height())
        // the stroke's own width does not grow with it: about twice as wide
        assertTrue(d.objects(0).single().rect.width() > moved.rect.width() * 1.8f)
        d.close(); dir.deleteRecursively()
    }
}
