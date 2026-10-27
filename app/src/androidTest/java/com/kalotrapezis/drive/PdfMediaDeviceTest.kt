package com.kalotrapezis.drive

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A PDF's films and sounds (27 Oct): media.pdf (made by src/test/resources/media.py) has a Screen annotation playing an
 * embedded mp4, a RichMedia annotation with an m4a, and a Sound annotation's own samples — each found where it stands
 * and its file taken out whole. On a device (MuPDF's native code):
 * `am instrument -w -e class com.kalotrapezis.drive.PdfMediaDeviceTest …`.
 */
@RunWith(AndroidJUnit4::class)
class PdfMediaDeviceTest {
    @Test fun filmsAndSoundsFound() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val dir = java.io.File(ins.targetContext.cacheDir, "pdf-media-test").apply { deleteRecursively(); mkdirs() }
        val f = java.io.File(dir, "media.pdf").apply { ins.context.assets.open("media.pdf").use { i -> outputStream().use { i.copyTo(it) } } }
        val d = PdfDoc(f)
        val spots = d.media(0)
        assertEquals(listOf("clip.mp4", "tone.m4a", "sound.wav"), spots.map { it.name })
        // the Screen annotation's place from the page's top left (842 − 680)
        spots[0].let { assertEquals(100f, it.x, 0.5f); assertEquals(162f, it.y, 0.5f); assertEquals(320f, it.w, 0.5f); assertEquals(180f, it.h, 0.5f) }
        val files = spots.map { java.io.File(d.mediaFile(0, it.index, dir)!!).readBytes() }
        assertEquals(8990, files[0].size); assertEquals("ftyp", String(files[0], 4, 4))
        assertEquals(5629, files[1].size); assertEquals("ftyp", String(files[1], 4, 4))
        // 800 samples, 8 kHz, one channel, 8 bits: a WAV Android plays
        assertEquals(44 + 800, files[2].size); assertTrue(String(files[2], 0, 4) == "RIFF" && String(files[2], 8, 4) == "WAVE")
        assertEquals(8000, java.nio.ByteBuffer.wrap(files[2], 24, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int)
        assertTrue(OfficeVideo.length(d.mediaFile(0, spots[0].index, dir)!!)!! in 1500..2500)
        // the RichMedia has no picture of its own (Tetra draws one); the Screen has, and MuPDF draws the Sound's speaker
        assertEquals(listOf(true, false, true), spots.map { it.drawn })
        // moved in Edit (as a picture in Words): 10 points right, 20 down, one undo step
        d.moveMedia(0, spots[0].index, 10f, 20f)
        d.media(0)[0].let { assertEquals(110f, it.x, 0.5f); assertEquals(182f, it.y, 0.5f); assertEquals(320f, it.w, 0.5f) }
        d.undo(); assertEquals(162f, d.media(0)[0].y, 0.5f)
        d.close()
    }
}
