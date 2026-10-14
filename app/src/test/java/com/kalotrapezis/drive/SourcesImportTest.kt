package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesImportTest {
    @Test fun `the example in TETRA_SOURCES_FORMAT reads, and APA writes it`() {
        val md = java.io.File("../TETRA_SOURCES_FORMAT.md").readText()
        val json = md.substringAfter("```json").substringBefore("```")
        val r = SourcesImport.read(json)
        assertEquals(emptyList<String>(), r.skipped)
        assertEquals(listOf(DocSource.Type.Article, DocSource.Type.Book, DocSource.Type.Chapter, DocSource.Type.Web), r.sources.map { it.type })
        val article = r.sources[0]
        assertEquals(listOf("Paisley", "Tomeny"), article.authors.map { it.last })
        assertEquals("10.1016/j.rasd.2022.102000", article.doi)
        assertEquals("Research in Autism Spectrum Disorders", article.container)
        assertEquals(DocSource.Person("Bandura", "Albert"), r.sources[2].authors[0])
        assertEquals("12–30", r.sources[2].pages)
        assertEquals("Theories of learning", r.sources[2].container)
        assertEquals("American Psychiatric Association", r.sources[1].authors[0].last)
        val entry = Apa.entry(article).joinToString("") { it.text }
        assertTrue(entry, entry.startsWith("Paisley, C. A., & Tomeny, T. S. (2022). A systematic review") && entry.endsWith("https://doi.org/10.1016/j.rasd.2022.102000"))
    }

    @Test fun `kind to mistakes, and no source twice`() {
        val r = SourcesImport.read("""[{"title":"","year":"2020"},{"title":"T","doi":"https://doi.org/10.1/x","journal":"J"},"x"]""")
        assertEquals(2, r.skipped.size)
        assertEquals(DocSource.Type.Article, r.sources[0].type)
        assertEquals("10.1/x", r.sources[0].doi)
        assertTrue(SourcesImport.same(DocSource("", title = "Other", doi = "10.1/X"), r.sources))
        assertTrue(SourcesImport.same(DocSource("", title = "t ", doi = ""), listOf(DocSource("a", title = "T"))))
        assertTrue(!SourcesImport.same(DocSource("", title = "T", year = "2021"), listOf(DocSource("a", title = "T", year = "2020"))))
    }

    @Test fun `pasted from a chat, the JSON is found inside the words and the marks`() {
        val r = SourcesImport.read("Here is your file:\n```json\n{\"format\":\"tetra-sources\",\"sources\":[{\"title\":\"T\",\"year\":\"2020\"}]}\n```\n1 source.")
        assertEquals(listOf("T"), r.sources.map { it.title })
    }
}
