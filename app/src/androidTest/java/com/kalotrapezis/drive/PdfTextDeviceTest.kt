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
        assertEquals(t.words, back.words)
        assertTrue("its words Words' paragraphs, bold, centred", back.doc.paras.all { p -> p.align == DocAlign.Center && p.runs.all { it.bold } })
        assertEquals(300f, r.width())
        assertTrue(r.height() >= 2 * 24f) // two lines grew it past the 40 drawn
        d.save(); d.close()

        val bytes = f.readBytes().toString(Charsets.ISO_8859_1)
        // Words' Times twin, bold, embedded (not MuPDF's own Helvetica made again from the fallback look)
        assertTrue("its font embedded", bytes.contains("LiberationSerif-Bold") && bytes.contains("/FontFile"))
        val again = PdfDoc(f)
        assertEquals(t.words, again.typedAt(0, Offset(150f, 120f))?.second?.words)
        // drawn in its red, inside its box (595 px across: a point a pixel)
        val bmp = kotlinx.coroutines.runBlocking { again.page(0, 595) }!!
        var red = 0
        for (y in r.top.toInt() until r.bottom.toInt()) for (x in r.left.toInt() until r.right.toInt()) {
            val c = bmp.getPixel(x, y); if (android.graphics.Color.red(c) > 180 && android.graphics.Color.green(c) < 120) red++
        }
        assertTrue("red letters in the box ($red)", red > 200)
        // typed in again: one text, the new words
        again.putText(0, r.left, r.top, PdfText("Changed", look, true), r.width(), r.height(), replacing = again.objectAt(0, Offset(r.centerX(), r.centerY()))!!.id)
        assertEquals("Changed", again.typedAt(0, Offset(r.centerX(), r.centerY()))?.second?.words)
        assertEquals(1, again.objects(0).size)
        // emptied: gone
        again.putText(0, r.left, r.top, PdfText("", look, true), r.width(), r.height(), replacing = again.objectAt(0, Offset(r.centerX(), r.centerY()))!!.id)
        assertNull(again.typedAt(0, Offset(r.centerX(), r.centerY())))
        assertEquals(0, again.objects(0).size)
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
        // at a slant (the handle over it, any angle): its place the box round it, its own size read back from the file
        d.putText(0, 100f, 400f, PdfText("Slant", PdfTextLook(size = 20f, turn = 30), framed = true), 200f, 40f)
        val (sr, slant) = d.typedAt(0, Offset(197f, 467f))!!
        assertEquals(30, slant.look.turn)
        assertEquals(194f, sr.width()); assertEquals(135f, sr.height()) // 200 cos 30 + 40 sin 30, 200 sin 30 + 40 cos 30, up
        assertEquals(200f to 40f, slant.unturned(sr))
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

    @Test fun wordsWithTheirOwnLook() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        OfficeFonts.assets = target.assets
        // two lines, one word bold and red (a text put on before 27 Oct, its runs read into Words' runs)
        val words = "Plain words\nbold one"
        val t = PdfText(words, PdfTextLook(size = 30f), framed = false, runs = listOf(PdfRun(12, 16, bold = true, color = 0xFF0000)))
        val bold = t.doc.paras[1].runs.first()
        assertEquals("bold", bold.text); assertTrue(bold.bold); assertEquals(0xFF0000, bold.color)
        // typed in again in Words' editor and back: the same runs (Words' own step)
        val state = com.mohamedrejeb.richeditor.model.RichTextState()
        var again0: List<DocPara> = emptyList()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            state.setRichTextDocument(WriterBridge.toEditor(t.doc, HashMap(), t.styles))
            again0 = WriterBridge.fromEditorKeeping(t.doc.paras, state.toRichTextDocument(), t.doc.basePt)
        }
        assertEquals(t.doc.paras.map { p -> p.runs.map { it.text to it.bold } }, again0.map { p -> p.runs.map { it.text to it.bold } })
        // put on, saved, read back: the red word drawn red and the plain ones black
        val dir = java.io.File(target.cacheDir, "pdf-text-test3").apply { deleteRecursively() }
        val f = Pdf.newBlank(dir)
        val d = PdfDoc(f)
        d.putText(0, 50f, 50f, t, 400f)
        d.save(); d.close()
        val again = PdfDoc(f)
        val (r, back) = again.typedAt(0, Offset(60f, 60f))!!
        assertEquals(0xFF0000, back.doc.paras[1].runs.first { it.text == "bold" }.color)
        val bmp = kotlinx.coroutines.runBlocking { again.page(0, 595) }!!
        fun count(x0: Int, x1: Int, y0: Int, y1: Int, red: Boolean) = (y0 until y1).sumOf { y -> (x0 until x1).count { x -> val c = bmp.getPixel(x, y)
            if (red) android.graphics.Color.red(c) > 180 && android.graphics.Color.green(c) < 100 else android.graphics.Color.red(c) < 80 && android.graphics.Color.green(c) < 80 } }
        val mid = (r.top + r.height() / 2).toInt()
        assertTrue("the first line black", count(r.left.toInt(), r.right.toInt(), r.top.toInt(), mid, false) > 100)
        assertTrue("no red on the first line", count(r.left.toInt(), r.right.toInt(), r.top.toInt(), mid, true) == 0)
        assertTrue("the bold word red", count(r.left.toInt(), r.right.toInt(), mid, r.bottom.toInt(), true) > 100)
        again.close(); dir.deleteRecursively()
    }

    @Test fun lookKeptInTheFile() {
        val t = PdfText("xyz", PdfTextLook(fill = 0xFFFF00, line = 0x0070C0, highlight = 0x00FF00, align = DocAlign.End, turn = 270), framed = true,
            runs = listOf(PdfRun(0, 2, italic = true, underline = true, strike = true, highlight = 0x00FFFF)))
        val back = PdfText.parse(t.json(), t.docx())!!
        assertEquals(t.words, back.words); assertEquals(t.framed, back.framed)
        assertEquals(listOf(0xFFFF00, 0x0070C0, 270), listOf(back.look.fill, back.look.line, back.look.turn))
        assertTrue(back.doc.paras[0].runs[0].italic && back.doc.paras[0].align == DocAlign.End)
        // a box put on before fills and borders were chosen (24 Oct, first build): its black frame kept
        assertEquals(0x000000, PdfText.parse("""{"words":"a","framed":true}""")?.look?.line)
        assertNull(PdfText.parse("""{"words":"a","framed":false}""")?.look?.line)
    }
}
