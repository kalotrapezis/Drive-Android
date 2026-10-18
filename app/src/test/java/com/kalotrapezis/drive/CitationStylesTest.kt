package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** MLA 9 and Chicago author-date beside APA 7: citations, list entries, and the style kept in the file. */
class CitationStylesTest {
    private val book = DocSource("Kur18", DocSource.Type.Book, listOf(DocSource.Person("Kurlansky", "Mark")), "2018", "Salt: A World History", publisher = "Penguin")
    private val article = DocSource("Sma19", DocSource.Type.Article, listOf(DocSource.Person("Smith", "John"), DocSource.Person("Jones", "Ann")), "2019",
        "Reading on Screens", container = "Journal of Reading", volume = "12", issue = "3", pages = "45-67", doi = "10.1000/xyz")
    private val many = book.copy(tag = "Lee20", authors = listOf(DocSource.Person("Lee", "Bo"), DocSource.Person("Kim", "Ha"), DocSource.Person("Park", "Su")), year = "2020")
    private fun text(runs: List<DocRun>) = runs.joinToString("") { it.text }

    @Test fun `MLA 9`() {
        assertEquals("(Kurlansky 47)", CiteStyle.MLA.parenthetical(listOf(book), "47"))
        assertEquals("(Smith and Jones)", CiteStyle.MLA.parenthetical(listOf(article)))
        assertEquals("(Lee et al. 3–5)", CiteStyle.MLA.parenthetical(listOf(many), "3-5"))
        assertEquals("Kurlansky, Mark. Salt: A World History. Penguin, 2018.", text(CiteStyle.MLA.entry(book)))
        assertEquals("Smith, John, and Ann Jones. “Reading on Screens.” Journal of Reading, vol. 12, no. 3, 2019, pp. 45–67, doi.org/10.1000/xyz.", text(CiteStyle.MLA.entry(article)))
        assertTrue(CiteStyle.MLA.entry(book).single { it.text.startsWith("Salt") }.italic)
    }

    @Test fun `Chicago author-date`() {
        assertEquals("(Kurlansky 2018, 47)", CiteStyle.Chicago.parenthetical(listOf(book), "47"))
        assertEquals("(Lee, Kim, and Park 2020)", CiteStyle.Chicago.parenthetical(listOf(many)))
        assertEquals("(Kurlansky 2018; Smith and Jones 2019)", CiteStyle.Chicago.parenthetical(listOf(book, article)))
        assertEquals("Kurlansky, Mark. 2018. Salt: A World History. Penguin.", text(CiteStyle.Chicago.entry(book)))
        assertEquals("Smith, John, and Ann Jones. 2019. “Reading on Screens.” Journal of Reading 12 (3): 45–67. https://doi.org/10.1000/xyz", text(CiteStyle.Chicago.entry(article)))
    }

    @Test fun `the style is kept in the file`() {
        val cite = Citations.cite(OfficeKind.Docx, listOf(book.tag), "47")
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Salt "), DocRun("(x)", cite = cite)))), sources = listOf(book), citeStyle = CiteStyle.MLA)
        // Word: its sources part says MLA, and the citation and list are MLA's.
        val word = OfficeFormats.read(OfficeFormats.write(Citations.insertList(Citations.update(doc, OfficeKind.Docx), OfficeKind.Docx, 1), OfficeKind.Docx), OfficeKind.Docx)
        assertEquals(CiteStyle.MLA, word.citeStyle)
        assertTrue(word.paras.any { it.text == "Works Cited" })
        assertTrue(word.paras.any { it.text == "Salt (Kurlansky 47)" })
        // LibreOffice: told by what it shows.
        for (style in listOf(CiteStyle.MLA, CiteStyle.Chicago, CiteStyle.APA)) {
            val odt = Citations.update(doc.copy(citeStyle = style, sources = listOf(book.copy(tag = "x")), paras = listOf(DocPara(listOf(DocRun("Salt "), DocRun("(x)", cite = "x"))))), OfficeKind.Odt)
            assertEquals(style, OfficeFormats.read(OfficeFormats.write(odt, OfficeKind.Odt), OfficeKind.Odt).citeStyle)
        }
    }

    @Test fun `Harvard and IEEE`() {
        assertEquals("(Kurlansky, 2018, p. 47)", CiteStyle.Harvard.parenthetical(listOf(book), "47"))
        assertEquals("(Lee, Kim and Park, 2020)", CiteStyle.Harvard.parenthetical(listOf(many)))
        assertEquals("Kurlansky, M. (2018) Salt: A World History. Penguin.", text(CiteStyle.Harvard.entry(book)))
        assertEquals("Smith, J. and Jones, A. (2019) ‘Reading on Screens’, Journal of Reading, 12(3), pp. 45–67. Available at: https://doi.org/10.1000/xyz", text(CiteStyle.Harvard.entry(article)))
        val order = listOf(article.tag, book.tag)
        assertEquals("[2, p. 47]", CiteStyle.IEEE.parenthetical(listOf(book), "47", order = order))
        assertEquals("[1], [2]", CiteStyle.IEEE.parenthetical(listOf(article, book), order = order))
        assertEquals("M. Kurlansky, Salt: A World History. Penguin, 2018.", text(CiteStyle.IEEE.entry(book)))
        assertEquals("J. Smith and A. Jones, “Reading on Screens,” Journal of Reading, vol. 12, no. 3, pp. 45–67, 2019, doi: 10.1000/xyz.", text(CiteStyle.IEEE.entry(article)))
    }

    /** Word's citation options (Suppress author, year): kept in the field, honoured in every style. */
    @Test fun `a citation leaves out its author or year`() {
        val cite = Citations.cite(OfficeKind.Docx, listOf(book.tag), "47", setOf('n'))
        assertEquals(setOf('n'), Citations.hidden(cite))
        assertEquals(listOf(book.tag) to "47", Citations.parse(cite))
        assertEquals("(2018, p. 47)", Citations.text(cite, listOf(book), CiteStyle.APA))
        assertEquals("(2018, 47)", Citations.text(cite, listOf(book), CiteStyle.Chicago))
        assertEquals("(47)", Citations.text(cite, listOf(book), CiteStyle.MLA))
        assertEquals("(Kurlansky)", Citations.text(Citations.cite(OfficeKind.Docx, listOf(book.tag), "", setOf('y')), listOf(book), CiteStyle.APA))
        // \y used to take the next word as its own: a second source after it was lost
        assertEquals(listOf("Kur18", "Sma19"), Citations.parse("CITATION Kur18 \\l 1033 \\y \\m Sma19").first)
    }

    /** Harvard and IEEE kept in a Word file (its sources part's StyleName); IEEE numbered as cited, its list in that order; LibreOffice reads the citations. */
    @Test fun `Harvard and IEEE are kept in the file`() {
        for (style in listOf(CiteStyle.Harvard, CiteStyle.IEEE)) {
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Screens "), DocRun("(x)", cite = Citations.cite(OfficeKind.Docx, listOf(article.tag), "")),
                DocRun(" and salt "), DocRun("(y)", cite = Citations.cite(OfficeKind.Docx, listOf(book.tag), "", setOf('y'))))), DocPara(emptyList())),
                sources = listOf(book, article), citeStyle = style)
            val made = Citations.insertList(Citations.update(doc, OfficeKind.Docx), OfficeKind.Docx, 1)
            val bytes = OfficeFormats.write(made, OfficeKind.Docx)
            val back = OfficeFormats.read(bytes, OfficeKind.Docx)
            assertEquals(style, back.citeStyle)
            assertTrue(back.paras.any { it.text.trim() == style.heading })
            val first = back.paras.first().text
            if (style == CiteStyle.IEEE) {
                assertEquals("Screens [1] and salt [2]", first)
                assertTrue(back.paras.first { it.text.startsWith("[1]") }.text.contains("Reading on Screens"))
            } else assertEquals("Screens (Smith and Jones, 2019) and salt (Kurlansky)", first)
            if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
                val dir = scratchDir("lo")
                val f = java.io.File(dir, "c.docx").apply { writeBytes(bytes) }
                ProcessBuilder("soffice", "--headless", "--convert-to", "txt:Text", "--outdir", dir.path, f.path).redirectErrorStream(true).start().waitFor()
                val txt = java.io.File(dir, "c.txt").readText()
                assertTrue("$style: $txt", first in txt && style.heading in txt)
            }
        }
        // Harvard in an OpenDocument: told by its list's heading
        val odt = Citations.insertList(Citations.update(OfficeDoc(listOf(DocPara(listOf(DocRun("Salt "), DocRun("(x)", cite = "x"))), DocPara(emptyList())),
            sources = listOf(book.copy(tag = "x")), citeStyle = CiteStyle.Harvard), OfficeKind.Odt), OfficeKind.Odt, 1)
        assertEquals(CiteStyle.Harvard, OfficeFormats.read(OfficeFormats.write(odt, OfficeKind.Odt), OfficeKind.Odt).citeStyle)
    }
}
