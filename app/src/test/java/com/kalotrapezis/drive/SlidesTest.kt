package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** PowerPoint's slides (slides.py's deck, python-pptx's default template), as LibreOffice shows them. */
class SlidesTest {
    private fun words(i: SlideItem) = (i as? SlideItem.Shape)?.text?.paras?.joinToString("\n") { p -> p.runs.joinToString("") { it.text } }

    @Test fun `a pptx deck read with its layouts, master and theme`() = check("slides.pptx")

    /** The same deck as LibreOffice keeps it (slides.odp: LibreOffice's of slides.pptx): read the same. */
    @Test fun `an odp deck read with its styles and lists`() = check("slides.odp")

    private fun check(name: String) {
        val d = Slides.read(File("src/test/resources/$name"))
        assertEquals(720f, d.width, 0.5f); assertEquals(540f, d.height, 0.5f) // 10 × 7.5 in
        assertEquals(4, d.slides.size)
        // the title slide: its placeholders' places and looks from the layout and the master
        val title = d.slides[0].items.first { words(it) == "Tetra Slides" } as SlideItem.Shape
        assertTrue(title.w > 500 && title.y > 100) // the layout's centred title box
        assertEquals(44f, title.text!!.paras[0].runs[0].size, 0.1f) // the master's title style
        assertEquals('c', title.text.paras[0].align)
        assertEquals("Say hello first.", d.slides[0].notes)
        // bullets in two levels, the second smaller and further in
        val body = d.slides[1].items.first { words(it)?.startsWith("Read the slides") == true } as SlideItem.Shape
        val paras = body.text!!.paras
        assertEquals(listOf(0, 1, 1, 0, 1), paras.map { it.level })
        assertEquals("•", paras[0].bullet); assertTrue(paras[1].marL > paras[0].marL); assertTrue(paras[1].runs[0].size < paras[0].runs[0].size)
        // shapes, words of their own, a picture
        val third = d.slides[2].items
        val shapes = third.filterIsInstance<SlideItem.Shape>()
        assertEquals("roundRect", shapes.first { words(it) == "A rounded box" }.shape!!.geometry)
        assertEquals(0xFF70AD47.toInt(), shapes.first { it.shape?.geometry == "rightArrow" }.shape!!.fill)
        val red = shapes.first { words(it) == "Bold red words, 28 pt" }.text!!.paras[0].runs[0]
        assertTrue(red.bold); assertEquals(28f, red.size, 0.1f); assertEquals(0xFFC00000.toInt(), red.color)
        assertEquals(1, third.count { it is SlideItem.Picture })
        // a chart
        val chart = d.slides[3].items.filterIsInstance<SlideItem.Chart>().single().chart
        assertEquals(listOf(120.0, 90.0, 150.0), chart.series[0].values)
    }

    /** Edits saved into the .pptx (moved, duplicated, deleted, hidden, words changed), read back here and by LibreOffice. */
    @Test fun `a pptx edited and saved`() {
        val src = File("src/test/resources/slides.pptx")
        val deck = Slides.read(src)
        var e = SlidesEdit.start(deck)
        // duplicate "Plan", delete "Scores", the shapes' slide first, the title slide hidden and retitled
        e = e.copy(order = e.order.toMutableList().apply { add(2, SlidesEdit.Entry(1, e.next())) })
        e = e.copy(order = e.order.filter { it.src != 3 })
        e = e.copy(order = e.order.toMutableList().apply { val s = first { it.src == 2 }; remove(s); add(0, s) })
        val title = deck.slides[0].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Tetra Slides" }
        e = e.copy(hidden = mapOf(0 to true), texts = mapOf((0 to title.id!!) to listOf("Tetra Slides, edited")))
        // a new slide of "Title and Content" at the end, its title typed, its body left empty (no hint saved)
        val layout = deck.layouts.indexOfFirst { it.name == "Title and Content" }
        assertTrue(deck.layouts.size >= 5 && layout >= 0)
        val titleId = deck.layouts[layout].preview.items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Title" }.id!!
        val uid = e.next()
        e = e.copy(order = e.order + SlidesEdit.Entry(-1, uid, layout), texts = e.texts + ((uid to titleId) to listOf("A new slide")))
        // a picture and a star put on the shapes' slide (uid 2)
        val png = File.createTempFile("pic", ".png").apply { writeBytes(File("src/test/resources/slides.pptx").let { java.util.zip.ZipFile(it).use { z -> z.getInputStream(z.entries().asSequence().first { en -> en.name.startsWith("ppt/media/") }).readBytes() } }) }
        e = e.copy(added = mapOf(2 to listOf(SlidesEdit.Added(e.newId(2), 400f, 300f, 120f, 80f, image = png.path), SlidesEdit.Added("9100", 550f, 50f, 100f, 100f, prst = "star5", fill = 0xFFFFC000.toInt()),
            SlidesEdit.Added("9101", 60f, 360f, 300f, 72f, prst = "table", rows = 2, cols = 2, cells = listOf("Name", "Score", "Anna", "18")))))
        val out = File.createTempFile("edited", ".pptx")
        SlidesEdit.save(src, deck, e, out)
        val back = Slides.read(out)
        assertEquals(5, back.slides.size)
        assertEquals("A new slide", back.slides[4].items.firstNotNullOf { i -> words(i)?.takeIf { !(i as SlideItem.Shape).hint } }) // its title
        assertTrue(back.slides[4].items.none { words(it) == "Text" && !(it as SlideItem.Shape).hint }) // the hint not saved: an empty box again
        assertTrue(back.slides[0].items.any { words(it) == "A rounded box" }) // moved first
        assertEquals(2, back.slides[0].items.count { it is SlideItem.Picture }) // its own and the one put on it
        val table = back.slides[0].items.filterIsInstance<SlideItem.Table>().single()
        assertEquals(listOf("Name", "Score", "Anna", "18"), table.cells.flatten().map { (_, t) -> t?.paras?.joinToString { p -> p.runs.joinToString("") { it.text } }.orEmpty() })
        assertEquals(0xFF4F81BD.toInt(), table.cells[0][0].first) // PowerPoint's default style: the first row in the theme's accent (this deck's: Office 2007's)
        assertEquals(0xFFFFC000.toInt(), back.slides[0].items.filterIsInstance<SlideItem.Shape>().first { it.shape?.geometry == "star5" }.shape!!.fill)
        assertTrue(back.slides[0].items.filterIsInstance<SlideItem.Picture>().any { it.bytes()?.size == png.length().toInt() })
        assertTrue(back.slides[1].hidden)
        assertEquals("Tetra Slides, edited", back.slides[1].items.firstNotNullOf { words(it)?.takeIf { w -> w.startsWith("Tetra") } })
        assertEquals(44f, (back.slides[1].items.first { words(it)?.startsWith("Tetra") == true } as SlideItem.Shape).text!!.paras[0].runs[0].size, 0.1f) // its look kept
        assertEquals(2, back.slides.count { s -> s.items.any { words(it)?.startsWith("Plan") == true } }) // duplicated
        assertTrue(back.slides.none { s -> s.items.any { it is SlideItem.Chart } }) // deleted
        assertEquals("Say hello first.", back.slides[1].notes) // its notes went with it
        // LibreOffice opens it: four pages
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
            val dir = java.nio.file.Files.createTempDirectory("lo").toFile()
            ProcessBuilder("soffice", "--headless", "--convert-to", "odp", "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
            val odp = dir.listFiles()!!.single { it.extension == "odp" }
            val lo = Slides.read(odp)
            assertEquals(5, lo.slides.size)
            assertTrue(lo.slides[1].items.any { words(it) == "Tetra Slides, edited" })
        }
    }
}
