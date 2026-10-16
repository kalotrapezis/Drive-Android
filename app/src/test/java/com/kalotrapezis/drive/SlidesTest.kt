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
}
