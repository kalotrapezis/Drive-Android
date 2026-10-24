package com.kalotrapezis.drive

import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PDF › Text and Text box with Words' looks (24 Oct): put on a blank page in a font of Words' own (Liberation Serif for
 * Times New Roman), saved, read back — drawn in its colour where it was put, its font embedded, typed in again by a tap
 * (the old one replaced, not doubled), emptied to take it out. On a device (MuPDF's native code):
 * `am instrument -w -e class com.kalotrapezis.drive.PdfTextDeviceTest …`.
 */
@RunWith(AndroidJUnit4::class)
class PdfTextDeviceTest {
    @Test fun richTextPutSavedRetyped() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        OfficeFonts.assets = target.assets
        val dir = java.io.File(target.cacheDir, "pdf-text-test").apply { deleteRecursively() }
        val f = Pdf.newBlank(dir)
        val look = PdfTextLook(font = "Times New Roman", size = 24f, bold = true, color = 0xE53935, align = DocAlign.Center)
        val t = PdfText("Γειά σου\nHello world", look, framed = true)
        val d = PdfDoc(f)
        d.putText(0, 100f, 100f, t, 300f, 40f)
        val (r, back) = d.typedAt(0, Offset(150f, 120f))!!
        assertEquals(t, back)
        assertEquals(300f, r.width())
        assertTrue(r.height() >= 2 * 24f) // two lines grew it past the 40 drawn
        d.save(java.io.File(dir, "base.pdf")); d.close()

        val bytes = f.readBytes().toString(Charsets.ISO_8859_1)
        // Words' Times twin, bold, embedded (not MuPDF's own Helvetica made again from the fallback look)
        assertTrue("its font embedded", bytes.contains("LiberationSerif-Bold") && bytes.contains("/FontFile"))
        val again = PdfDoc(f)
        assertEquals(t, again.typedAt(0, Offset(150f, 120f))?.second)
        // drawn in its red, inside its box (595 px across: a point a pixel)
        val bmp = kotlinx.coroutines.runBlocking { again.page(0, 595) }!!
        var red = 0
        for (y in r.top.toInt() until r.bottom.toInt()) for (x in r.left.toInt() until r.right.toInt()) {
            val c = bmp.getPixel(x, y); if (android.graphics.Color.red(c) > 180 && android.graphics.Color.green(c) < 120) red++
        }
        assertTrue("red letters in the box ($red)", red > 200)
        // typed in again: one text, the new words
        again.putText(0, r.left, r.top, t.copy(words = "Changed"), r.width(), r.height(), replacing = Offset(r.centerX(), r.centerY()))
        assertEquals("Changed", again.typedAt(0, Offset(r.centerX(), r.centerY()))?.second?.words)
        assertEquals(1, again.comments().size)
        // emptied: gone
        again.putText(0, r.left, r.top, t.copy(words = ""), r.width(), r.height(), replacing = Offset(r.centerX(), r.centerY()))
        assertNull(again.typedAt(0, Offset(r.centerX(), r.centerY())))
        assertEquals(0, again.comments().size)
        again.close()
        // a script font's joined "ll" kept as two letters: the words copy and are found as typed (Dancing Script, a phone font)
        val script = PdfText("Hello Γειά", PdfTextLook(font = "Dancing Script", size = 20f), framed = false)
        val (sw, sh) = PdfText.size(script, 300f)
        val made = com.artifex.mupdf.fitz.Document.openDocument(PdfText.pdf(script, sw, sh), "application/pdf")
        val words = made.loadPage(0).toStructuredText().asText(); made.destroy()
        assertTrue("read back: $words", words.contains("Hello Γειά"))
        dir.deleteRecursively()
    }

    @Test fun turnedAndRestacked() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        OfficeFonts.assets = target.assets
        val dir = java.io.File(target.cacheDir, "pdf-text-test2").apply { deleteRecursively() }
        val d = PdfDoc(Pdf.newBlank(dir))
        // turned a quarter: its place on the page the other way round, the look keeps its turn
        val turned = PdfText("Sideways", PdfTextLook(size = 20f, turn = 90), framed = true)
        d.putText(0, 100f, 100f, turned, 200f, 40f)
        val (r, back) = d.typedAt(0, Offset(110f, 110f))!!
        assertEquals(90, back.look.turn)
        assertEquals(40f, r.width()); assertEquals(200f, r.height())
        assertEquals(200f to 40f, back.unturned(r))
        // two filled boxes over each other: the later on top; sent to the back, the other shows
        d.putText(0, 300f, 300f, PdfText("A", PdfTextLook(fill = 0xFF0000), true), 100f, 100f)
        d.putText(0, 320f, 320f, PdfText("B", PdfTextLook(fill = 0x0000FF), true), 100f, 100f)
        fun middle() = kotlinx.coroutines.runBlocking { d.page(0, 595) }!!.getPixel(360, 360)
        assertTrue(android.graphics.Color.blue(middle()) > 200)
        assertTrue(d.restack(0, Offset(415f, 415f), -2))
        assertTrue("red on top after B went to the back", android.graphics.Color.red(middle()) > 200)
        assertTrue(!d.restack(0, Offset(415f, 415f), -1)) // at the back already
        d.close(); dir.deleteRecursively()
    }

    @Test fun lookKeptInTheFile() {
        val t = PdfText("x", PdfTextLook(fill = 0xFFFF00, line = 0x0070C0, highlight = 0x00FF00, align = DocAlign.End, turn = 270), framed = true)
        assertEquals(t, PdfText.parse(t.json()))
        // a box put on before fills and borders were chosen (24 Oct, first build): its black frame kept
        assertEquals(0x000000, PdfText.parse("""{"words":"a","framed":true}""")?.look?.line)
        assertNull(PdfText.parse("""{"words":"a","framed":false}""")?.look?.line)
    }
}
