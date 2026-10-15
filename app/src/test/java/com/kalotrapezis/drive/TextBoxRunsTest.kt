package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editing a file's text box keeps every run's look, not just its first words'. */
class TextBoxRunsTest {
    private val b = "<w:rPr><w:b/></w:rPr>"
    private val red = "<w:rPr><w:color w:val=\"FF0000\"/></w:rPr>"
    private val word = """<wps:wsp><wps:txbx><w:txbxContent><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r>$b<w:t>Bold </w:t></w:r><w:r>$red<w:t>red</w:t></w:r></w:p><w:p><w:pPr><w:pStyle w:val="Quote"/></w:pPr><w:r>$red<w:t>Second</w:t></w:r></w:p></w:txbxContent></wps:txbx></wps:wsp>"""

    @Test fun `letters kept keep their run, typed ones the one before`() {
        val runs = listOf("B" to "Bold ", "R" to "red")
        assertEquals(listOf("B" to "Bold ", "R" to "red!"), PictureXml.relook(runs, "Bold red!"))
        assertEquals(listOf("B" to "Very bold ", "R" to "red"), PictureXml.relook(runs, "Very bold red"))
        assertEquals(listOf("B" to "Bold ", "R" to "blue"), PictureXml.relook(runs, "Bold blue"))
        assertEquals(listOf("B" to "Bold"), PictureXml.relook(runs, "Bold"))
    }

    @Test fun `a Word text box keeps each run and each paragraph's look`() {
        val out = PictureXml.boxText(word, OfficeKind.Docx, listOf("New line", "Bold red too", "Second"))
        val paras = Regex("<w:p>.*?</w:p>").findAll(out).map { it.value }.toList()
        assertEquals(3, paras.size)
        // A line put before the first takes the first paragraph's look (nothing before it).
        assertTrue(paras[0], "<w:jc w:val=\"center\"/>" in paras[0])
        assertTrue(paras[1], "<w:r>$b<w:t xml:space=\"preserve\">Bold </w:t></w:r>" in paras[1] && "<w:r>$red<w:t xml:space=\"preserve\">red too</w:t></w:r>" in paras[1])
        assertTrue(paras[2], "Quote" in paras[2] && red in paras[2])
    }

    @Test fun `an odt text box keeps its spans`() {
        val odt = """<draw:frame><draw:text-box><text:p text:style-name="P1"><text:span text:style-name="T1">Big</text:span> plain</text:p></draw:text-box></draw:frame>"""
        val out = PictureXml.boxText(odt, OfficeKind.Odt, listOf("Big plain words"))
        assertEquals("""<draw:frame><draw:text-box><text:p text:style-name="P1"><text:span text:style-name="T1">Big</text:span> plain words</text:p></draw:text-box></draw:frame>""", out)
    }

    @Test fun `WordArt's first words still take its look`() {
        val art = PictureXml.newTextBox(OfficeKind.Docx, 200f, 50f, listOf("Hello"), art = 0xFF4472C4.toInt())
        assertTrue(art, "<w:color w:val=\"4472C4\"/>" in art && "Hello" in art)
        // Edited after: still in it.
        val again = PictureXml.boxText(art, OfficeKind.Docx, listOf("Hello world"))
        assertTrue(again, "<w:color w:val=\"4472C4\"/>" in again && "Hello world" in again)
    }

    @Test fun `the pages' paragraphs follow the same looks`() {
        val img = DocImage("k", null, 10f, 10f, box = listOf(DocPara(listOf(DocRun("Bold ", bold = true), DocRun("red", color = 0xFF0000)))))
        val p = PictureXml.boxParas(img, listOf("Bold red!")).single()
        assertEquals(listOf("Bold " to true, "red!" to false), p.runs.map { it.text to it.bold })
        assertEquals(0xFF0000, p.runs[1].color)
    }
}
