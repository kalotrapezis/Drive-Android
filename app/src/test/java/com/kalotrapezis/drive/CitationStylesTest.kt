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
}
