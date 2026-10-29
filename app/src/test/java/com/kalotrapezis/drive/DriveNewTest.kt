package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DriveNewTest {
    @Test fun `new folders, template copies and Writer's own files, never over what is there`() {
        val root = Files.createTempDirectory("tetra").toFile().canonicalFile
        File(root, "Documents").mkdir()
        TetraSystem.dir(root, "Office", "Data").mkdirs(); File(TetraSystem.dir(root, "Office", "Data"), "Νέο αρχείο.txt").writeText("hello")
        val kinds = DriveRules.newKinds(root)
        assertEquals(listOf("Folder", "Νέο αρχείο", "Words document", "Tables spreadsheet"), kinds.map { it.label }) // the template stands in for the text file
        assertEquals("Documents/Notes", DriveRules.create(root, "Documents", kinds[0], "Notes"))
        assertTrue(File(root, "Documents/Notes").isDirectory)
        assertEquals("Documents/a.txt", DriveRules.create(root, "Documents", kinds[1], "a"))
        assertEquals("hello", File(root, "Documents/a.txt").readText())
        assertEquals("Documents/Report.docx", DriveRules.create(root, "Documents", kinds[2], "Report.docx"))
        assertEquals(1, OfficeFormats.read(File(root, "Documents/Report.docx").readBytes(), OfficeKind.Docx).paras.size)
        assertEquals("Documents/Budget.xlsx", DriveRules.create(root, "Documents", kinds[3], "Budget"))
        assertEquals(1, Sheets.read(File(root, "Documents/Budget.xlsx")).tabs.size) // Excel's empty workbook
        listOf("a", "../x", ".hidden", "a/b").forEach { bad -> assertTrue(bad, runCatching { DriveRules.create(root, "Documents", kinds[1], bad) }.isFailure) }
        root.deleteRecursively()
    }
}
