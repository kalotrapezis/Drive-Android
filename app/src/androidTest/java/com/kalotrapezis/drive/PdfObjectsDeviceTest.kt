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

        d.save(); d.close()
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

    @Test fun findReadsTetrasTexts() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        OfficeFonts.assets = target.assets
        val dir = java.io.File(target.cacheDir, "pdf-objects-find").apply { deleteRecursively() }
        val d = PdfDoc(Pdf.newBlank(dir))
        d.putText(0, 100f, 100f, PdfText("Find the needle here", PdfTextLook(), true), 300f, 40f)
        val found = kotlinx.coroutines.runBlocking { d.find("needle") }
        assertEquals(1, found.size)
        d.close(); dir.deleteRecursively()
    }

    @Test fun designOnEveryPage() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        OfficeFonts.assets = target.assets
        val dir = java.io.File(target.cacheDir, "pdf-objects-design").apply { deleteRecursively() }
        val f = Pdf.newBlank(dir)
        val d = PdfDoc(f)
        d.insertBlank(1); d.rotate(listOf(1), 90)
        d.putText(0, 100f, 100f, PdfText("Words on top", PdfTextLook(size = 20f), false), 300f)
        // a yellow page, a red border along the top only, DRAFT over it
        d.setDesign(PdfDesign(color = 0xFFF2CC, border = PdfDesign.border(Border(3f, 0xFF0000), "t"), watermark = "DRAFT"))
        assertEquals(PdfDesign(color = 0xFFF2CC, border = PdfDesign.border(Border(3f, 0xFF0000), "t"), watermark = "DRAFT"), d.design())
        fun px(i: Int, x: Int, y: Int) = kotlinx.coroutines.runBlocking { d.page(i, d.sizes[i].width.toInt()) }!!.getPixel(x, y)
        // a colour drawn and read back within a shade (rounding on the way)
        fun near(want: Int, got: Int) = assertTrue("%06X ≈ %06X".format(got and 0xFFFFFF, want), listOf(16, 8, 0).all { k -> kotlin.math.abs((want shr k and 0xFF) - (got shr k and 0xFF)) <= 3 })
        // the colour behind (the corner is yellow), the words still black over it
        near(0xFFF2CC, px(0, 5, 5))
        // the border 24 pt down from the top, on the page shown turned too
        for (i in 0..1) assertTrue("border on page ${i + 1}", (20..28).any { y -> px(i, (d.sizes[i].width / 2).toInt(), y).let { c -> android.graphics.Color.red(c) > 200 && android.graphics.Color.green(c) < 60 } })
        val text = kotlinx.coroutines.runBlocking { d.text(0) }
        assertTrue(text, "DRAFT" in text && "Words on top" in text)
        // changed: one design, not two; taken off: the page as it was
        d.setDesign(PdfDesign(color = 0xDDEBF7))
        near(0xDDEBF7, px(0, 5, 5))
        assertTrue("DRAFT" !in kotlinx.coroutines.runBlocking { d.text(0) })
        d.setDesign(PdfDesign())
        near(0xFFFFFF, px(0, 5, 5))
        d.save(); d.close()
        val again = PdfDoc(f)
        assertEquals(PdfDesign(), again.design())
        assertTrue("Words on top" in kotlinx.coroutines.runBlocking { again.text(0) })
        again.close(); dir.deleteRecursively()
    }

    @Test fun commentsResolvedEditedReplied() {
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-objects-comments").apply { deleteRecursively() }
        val f = Pdf.newBlank(dir)
        val d = PdfDoc(f)
        d.note(0, Offset(100f, 100f), "Look here", 0xFFEB3B, "Anna Field")
        val c = d.comments().single()
        assertEquals("Anna Field", c.author)
        d.reply(c, "Seen", "Ben Stone")
        d.resolve(d.comments().first { it.replyTo == null }, true)
        d.editComment(d.comments().first { it.replyTo == null }, "Look here, please")
        d.save(); d.close()
        val again = PdfDoc(f)
        val all = again.comments()
        assertEquals("the resolve mark is no comment of its own", 2, all.size)
        val top = all.first { it.replyTo == null }
        assertTrue(top.done); assertEquals("Look here, please", top.words)
        val card = top.card("Note")
        assertEquals("Anna Field", card.author); assertTrue(card.done)
        assertEquals(card.id, all.first { it.replyTo != null }.card("Reply").parent)
        again.resolve(top, false)
        assertTrue(!again.comments().first { it.replyTo == null }.done)
        again.close(); dir.deleteRecursively()
    }

    @Test fun everySaveKeepsWhatTheOnesBeforeDid() {
        // 27 Oct: a second save (AutoSave's), or a second day's, dropped what the first one had written
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-objects-saves").apply { deleteRecursively() }
        val f = Pdf.newBlank(dir)
        val d = PdfDoc(f)
        d.note(0, Offset(100f, 100f), "first", 0xFFEB3B); d.save()
        d.note(0, Offset(200f, 100f), "second", 0xFFEB3B); d.save()
        d.save(java.io.File(dir, "copy.pdf"), copy = true)
        d.note(0, Offset(300f, 100f), "third", 0xFFEB3B); d.save()
        d.close()
        // every part of the file reachable (an appended part chained to the file as first opened lost the parts between)
        assertTrue(f.readBytes().toString(Charsets.ISO_8859_1).let { "first" in it && "second" in it && "third" in it })
        val day2 = PdfDoc(f)
        assertEquals(listOf("first", "second", "third"), day2.comments().map { it.words })
        day2.note(0, Offset(400f, 100f), "fourth", 0xFFEB3B); day2.save(); day2.close()
        val day3 = PdfDoc(f)
        assertEquals(listOf("first", "second", "third", "fourth"), day3.comments().map { it.words })
        day3.close()
        val copy = PdfDoc(java.io.File(dir, "copy.pdf"))
        assertEquals(listOf("first", "second"), copy.comments().map { it.words })
        copy.close(); dir.deleteRecursively()
    }

    @Test fun pagesDuplicatedSplitCroppedFlattened() {
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-objects-pages").apply { deleteRecursively() }
        val f = Pdf.newBlank(dir)
        val d = PdfDoc(f)
        d.ink(0, listOf(listOf(Offset(100f, 100f), Offset(200f, 150f))), 0x000000, 2f)
        d.duplicatePage(0)
        assertEquals(2, d.sizes.size)
        d.insertBlank(2)
        d.save()
        // split in two before page 3: pages 1–2 and page 3
        val parts = d.split(f, every = false, at = 2)
        assertEquals(listOf(2, 1), parts.map { p -> PdfDoc(p).let { x -> x.sizes.size.also { x.close() } } })
        assertEquals(3, d.split(f, every = true, at = 0).size)
        // cropped to the stroke and a little round it; the blank page has nothing to crop to
        assertEquals(1, d.cropToContent(listOf(0, 2)))
        assertTrue(d.sizes[0].width < 130f && d.sizes[0].height < 90f)
        // flattened: the stroke is the page's own, no comment any more
        d.flatten()
        assertEquals(0, d.objects(1).size)
        d.removeHidden(); d.save(); d.close()
        val again = PdfDoc(f)
        assertEquals(3, again.sizes.size)
        assertEquals(null, again.info(com.artifex.mupdf.fitz.Document.META_INFO_CREATOR))
        again.close(); dir.deleteRecursively()
    }

    @Test fun linksPutReadAndTakenOff() {
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-objects-links").apply { deleteRecursively() }
        val f = Pdf.newBlank(dir)
        val d = PdfDoc(f)
        d.insertBlank(1)
        val r = android.graphics.RectF(100f, 100f, 300f, 140f)
        d.link(0, r, "https://example.org/a")
        assertEquals("https://example.org/a" to null, d.linkAt(0, Offset(200f, 120f)))
        assertEquals(null, d.linkAt(0, Offset(200f, 200f)))
        // another over it: the one before goes; a page of this file
        d.link(0, r, "#page=2")
        assertEquals(1, d.linkAt(0, Offset(200f, 120f))?.second)
        d.save(); d.close()
        val again = PdfDoc(f)
        assertEquals(1, again.linkAt(0, Offset(150f, 110f))?.second)
        again.link(0, r, null)
        assertEquals(null, again.linkAt(0, Offset(200f, 120f)))
        again.close(); dir.deleteRecursively()
    }

    @Test fun undoTakesAnObjectOffThePage() {
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-objects-undo").apply { deleteRecursively() }
        val f = Pdf.newBlank(dir)
        val d = PdfDoc(f)
        // what is drawn in the object's box (page points = pixels)
        fun inked() = kotlinx.coroutines.runBlocking { d.page(0, d.sizes[0].width.toInt()) }!!.let { bm ->
            (260 until 341).sumOf { y -> (140 until 261).count { x -> bm.getPixel(x, y) and 0xFFFFFF != 0xFFFFFF } } }
        val shape = PdfWords.of(DocImage("s1", null, 120f, 80f, PictureXml.newShape("rect", OfficeKind.Docx, 0xFF0000, 0xFF0000, 1f, 120f, 80f), wrap = PictureWrap.InFront))
        d.putWords(0, shape, 200f, 300f)
        assertEquals(1, d.objects(0).size)
        assertTrue(inked() > 100)
        d.undo()
        assertEquals(0, d.objects(0).size)
        assertEquals(0, inked())
        d.close(); dir.deleteRecursively()
    }

    @Test fun undoGoesBackToTheLastSave() {
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-objects-undo-save").apply { deleteRecursively() }
        val f = Pdf.newBlank(dir)
        val d = PdfDoc(f)
        fun shape(x: Float) = d.putWords(0, PdfWords.of(DocImage("s$x", null, 60f, 40f, PictureXml.newShape("rect", OfficeKind.Docx, 0xFF0000, 0xFF0000, 1f, 60f, 40f), wrap = PictureWrap.InFront)), x, 300f)
        shape(100f)
        d.save()
        // what the save wrote stays (MuPDF's undo would say it can, and change nothing)
        assertTrue(!d.canUndo)
        shape(200f); shape(300f)
        d.undo(); assertEquals(2, d.objects(0).size)
        d.redo(); assertEquals(3, d.objects(0).size)
        d.undo(); d.save()
        // undone before the save: not to be redone after it
        assertTrue(!d.canRedo && !d.canUndo)
        shape(400f); d.undo(); assertEquals(2, d.objects(0).size)
        assertTrue(d.canRedo)
        d.close()
        val again = PdfDoc(f)
        assertEquals(2, again.objects(0).size)
        again.close(); dir.deleteRecursively()
    }

    /** The screens load: Android's verifier takes their code (PdfScreen past its size failed so, 27 Oct, unseen by the tests above). */
    @Test fun screensLoad() {
        listOf("PdfScreenKt", "WriterKt", "SlidesScreenKt").forEach { Class.forName("com.kalotrapezis.drive.$it") }
    }

    @Test fun aTextThatGrowsPushesWhatIsStackedUnderIt() {
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-objects-stack").apply { deleteRecursively() }
        val f = Pdf.newBlank(dir)
        val d = PdfDoc(f)
        val a = d.objectById(0, d.putText(0, 72f, 100f, PdfText("One line", PdfTextLook(), true), 300f))!!
        // a shape stuck under it (6 pt), another under that, and one off to the side (not stacked)
        val s1 = d.putWords(0, PdfWords.of(DocImage("s1", null, 100f, 40f, PictureXml.newShape("rect", OfficeKind.Docx, 0xFF0000, 0xFF0000, 1f, 100f, 40f), wrap = PictureWrap.InFront)), 122f, a.rect.bottom + 6f + 20f)
        val s2 = d.putWords(0, PdfWords.of(DocImage("s2", null, 100f, 40f, PictureXml.newShape("rect", OfficeKind.Docx, 0x00FF00, 0x00FF00, 1f, 100f, 40f), wrap = PictureWrap.InFront)), 122f, a.rect.bottom + 6f + 40f + 6f + 20f)
        val side = d.putWords(0, PdfWords.of(DocImage("s3", null, 60f, 40f, PictureXml.newShape("rect", OfficeKind.Docx, 0x0000FF, 0x0000FF, 1f, 60f, 40f), wrap = PictureWrap.InFront)), 500f, a.rect.bottom + 26f)
        val stack = stackedUnder(d.objects(0), a.rect, a.id)
        assertEquals(setOf(s1, s2), stack.map { it.id }.toSet())
        val before = d.objects(0).associate { it.id to it.rect.top }
        val id = d.putTextStacked(0, 72f, 100f, PdfText("One line\nTwo lines\nThree lines", PdfTextLook(), true), 300f, 0f, a.id, stack.map { it.id }, a.rect.bottom, 0f)
        val grew = d.objectById(0, id)!!.rect.bottom - a.rect.bottom
        assertTrue(grew > 10f)
        assertEquals(before[s1]!! + grew, d.objectById(0, s1)!!.rect.top, 0.5f)
        assertEquals(before[s2]!! + grew, d.objectById(0, s2)!!.rect.top, 0.5f)
        assertEquals(before[side]!!, d.objectById(0, side)!!.rect.top, 0.5f)
        // one undo step for the text and what moved with it
        d.undo()
        assertEquals(before[s1]!!, d.objectById(0, s1)!!.rect.top, 0.5f)
        assertEquals(a.rect.bottom, d.objectById(0, a.id)!!.rect.bottom, 0.5f)
        d.close(); dir.deleteRecursively()
    }

    @Test fun groupedAndUngroupedAsWords() {
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-objects-group").apply { deleteRecursively() }
        val f = Pdf.newBlank(dir)
        val d = PdfDoc(f)
        val shape = d.putWords(0, PdfWords.of(DocImage("s1", null, 100f, 60f, PictureXml.newShape("ellipse", OfficeKind.Docx, 0xFF0000, 0x000000, 1f, 100f, 60f), wrap = PictureWrap.InFront)), 150f, 200f)
        val bmp = android.graphics.Bitmap.createBitmap(40, 20, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2E7D32.toInt()) }
        val jpg = java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        val pic = d.putWords(0, PdfWords.of(DocImage("n1", null, 80f, 40f, bytes = jpg, extension = "jpg", wrap = PictureWrap.InFront)), 300f, 260f)
        val g = d.groupObjects(0, d.objectById(0, shape)!!, d.objectById(0, pic)!!)
        assertNotNull(g)
        assertEquals(1, d.objects(0).size)
        val go = d.objectById(0, g!!)!!
        // the group's box: from the shape's corner to the picture's
        assertEquals(100f, go.rect.left, 1f); assertEquals(170f, go.rect.top, 1f); assertEquals(340f, go.rect.right, 1f); assertEquals(280f, go.rect.bottom, 1f)
        d.save(); d.close()
        val again = PdfDoc(f)
        val back = again.objects(0).single()
        assertNotNull(back.words!!.img.group)
        val bw = back.words!!
        assertTrue("parts ${bw.parts.keys.filter { !it.endsWith(".xml") && !it.endsWith(".rels") }} pieces ${bw.img.group!!.map { it.img.part to (it.img.bytes?.size) }}",
            bw.img.group!!.any { p -> bw.bytes(p.img)?.size == jpg.size })
        assertEquals(2, again.ungroupObject(0, back))
        val parts = again.objects(0).sortedBy { it.rect.left }
        assertEquals(listOf("Shape", "Image"), parts.map { it.kind })
        assertEquals(150f, parts[0].rect.centerX(), 1f); assertEquals(260f, parts[1].rect.centerY(), 1f)
        // the picture's own bytes came through the group and back
        assertEquals(jpg.size, parts[1].words!!.bytes()!!.size)
        again.undo(); assertEquals(1, again.objects(0).size)
        again.close(); dir.deleteRecursively()
    }

    @Test fun objectsKeepTheirNumbersThroughSaves() {
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-objects-ids").apply { deleteRecursively() }
        val f = Pdf.newBlank(dir)
        val d = PdfDoc(f)
        fun shape(x: Float) = d.putWords(0, PdfWords.of(DocImage("s$x", null, 60f, 40f, PictureXml.newShape("rect", OfficeKind.Docx, 0xFF0000, 0xFF0000, 1f, 60f, 40f), wrap = PictureWrap.InFront)), x, 300f)
        val a = shape(100f); d.save() // the first save appends
        val b = shape(200f); d.save() // the next ones write the file whole
        d.note(0, Offset(300f, 100f), "x", 0xFFEB3B); d.save()
        assertNotNull(d.objectById(0, a)); assertNotNull(d.objectById(0, b))
        d.close(); dir.deleteRecursively()
    }
}
