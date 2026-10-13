package com.kalotrapezis.drive

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class WriterDraftTest {
    @Test fun `a failed draft write leaves the last complete recovery file intact`() {
        val dir = Files.createTempDirectory("writer-draft-test").toFile()
        try {
            val file = dir.resolve("recovery.docx")
            writeWriterDraft(file, "complete original".toByteArray())
            dir.resolve(".recovery.docx.saving").mkdir()
            assertTrue(runCatching { writeWriterDraft(file, "new".toByteArray()) }.isFailure)
            assertEquals("complete original", file.readText())
        } finally { dir.deleteRecursively() }
    }
}
