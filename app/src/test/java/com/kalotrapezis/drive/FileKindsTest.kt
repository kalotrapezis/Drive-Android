package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.json.JSONObject
import org.junit.Test

class FileKindsTest {
    @Test fun everyCategoryHasItsIcon() {
        FileKinds.iconOf.values.toSet().forEach { assertNotNull(it, FileKinds.drawable(it)) }
    }

    @Test fun anExtensionIsInOneCategoryOnly() {
        val kinds = JSONObject(javaClass.getResourceAsStream("/file-kinds.json")!!.bufferedReader().readText()).getJSONObject("kinds")
        val all = kinds.keys().asSequence().flatMap { k -> kinds.getJSONObject(k).getJSONArray("extensions").let { a -> (0 until a.length()).map { a.getString(it) } } }.toList()
        assertEquals(all.size, all.toSet().size)
        all.forEach { assertEquals(it, it.lowercase()) }
    }

    @Test fun filesFindTheirCategory() {
        assertEquals(R.drawable.kind_docs_word, FileKinds.icon("Essay.DOCX"))
        assertEquals(R.drawable.kind_excel, FileKinds.icon("marks.csv"))
        assertEquals(R.drawable.kind_slides, FileKinds.icon("talk.odp"))
        assertEquals(R.drawable.kind_archives, FileKinds.icon("backup.tar.gz"))
        assertEquals(R.drawable.kind_programs_executables, FileKinds.icon("Tetra.AppImage"))
        assertEquals(R.drawable.kind_docs_txt, FileKinds.icon("notes.md"))
        assertEquals(R.drawable.kind_pdf, FileKinds.icon("paper.PDF"))
        assertNull(FileKinds.icon("photo.jpg"))
        assertNull(FileKinds.icon("README"))
    }
}
