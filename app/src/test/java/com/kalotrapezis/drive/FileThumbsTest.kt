package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class FileThumbsTest {
    @Test fun aChangedFileIsAnotherThumbnail() {
        val key = FileThumbs.key("/Tetra/a.docx", 100, 1_000)
        assertEquals(key, FileThumbs.key("/Tetra/a.docx", 100, 1_000))
        assertNotEquals(key, FileThumbs.key("/Tetra/a.docx", 101, 1_000)) // edited: another size
        assertNotEquals(key, FileThumbs.key("/Tetra/a.docx", 100, 2_000)) // edited: another time
        assertNotEquals(key, FileThumbs.key("/Tetra/b.docx", 100, 1_000)) // renamed or another file
        assertEquals(40, key.length)
    }

    @Test fun picturesAndVideosHaveAPreviewDocumentsNone() {
        assertNull(FileThumbs.kind("Essay.DOCX")) // their tile instead (the user, 24 Oct)
        assertNull(FileThumbs.kind("marks.ods"))
        assertNull(FileThumbs.kind("talk.pptx"))
        assertEquals(FileThumbs.Kind.Picture, FileThumbs.kind("IMG_1.heic"))
        assertEquals(FileThumbs.Kind.Video, FileThumbs.kind("clip.mp4"))
        assertNull(FileThumbs.kind("paper.pdf"))
        assertNull(FileThumbs.kind("notes.txt"))
        assertNull(FileThumbs.kind("README"))
    }

    @Test fun trimKeepsTheNewest() {
        val dir = scratchDir("thumbs")
        val files = (1..5).map { n -> File(dir, "$n.webp").apply { writeText("x"); setLastModified(n * 10_000L) } }
        FileThumbs.trim(dir, 3)
        assertEquals(listOf("3.webp", "4.webp", "5.webp"), dir.list()!!.sorted())
        FileThumbs.trim(dir, 3) // nothing more to do
        assertEquals(3, dir.list()!!.size)
        assertEquals(false, files[0].exists())
    }
}
