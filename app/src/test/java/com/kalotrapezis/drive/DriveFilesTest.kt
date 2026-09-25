package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DriveFilesTest {
    @Test fun aMoveTakesFilesKeepsSystemFoldersAndLetsEmptyRegularFoldersGo() {
        val root = Files.createTempDirectory("tetra").toFile()
        val gone = listOf("Documents/Scanned Documents/scan.pdf", "Documents/cv.pdf", "Work/2019/tax.pdf")
        for (f in gone + "Work/keep.txt") File(root, f).apply { parentFile!!.mkdirs(); writeText(f) }
        gone.forEach { assertTrue(DriveRules.file(root, it).delete()) }
        DriveRules.removeEmptyFolders(root, setOf("Documents/Scanned Documents", "Documents", "Work/2019"))
        assertTrue("system folders stay", File(root, "Documents/Scanned Documents").isDirectory && File(root, "Documents").isDirectory)
        assertFalse("an emptied regular folder goes", File(root, "Work/2019").exists())
        assertTrue("a folder with something left in it stays", File(root, "Work/keep.txt").exists())
    }

    @Test fun theDriveFolderBecomesTetraByRenamingItOnce() {
        val storage = Files.createTempDirectory("sdcard").toFile()
        File(storage, "Drive/Documents/Scanned Documents").mkdirs()
        File(storage, "Drive/Documents/Scanned Documents/scan.pdf").writeText("pdf")
        val root = TetraFolder.root(storage)
        assertEquals(File(storage, "Tetra"), root)
        assertEquals("pdf", File(root, "Documents/Scanned Documents/scan.pdf").readText())
        assertFalse(File(storage, "Drive").exists())
        assertEquals(root, TetraFolder.root(storage))
    }

    @Test fun whenBothFoldersExistTetraIsUsedAndDriveIsLeftAlone() {
        val storage = Files.createTempDirectory("sdcard").toFile()
        File(storage, "Drive/keep.txt").apply { parentFile!!.mkdirs(); writeText("x") }
        File(storage, "Tetra").mkdirs()
        assertEquals(File(storage, "Tetra"), TetraFolder.root(storage))
        assertTrue(File(storage, "Drive/keep.txt").exists())
    }

    @Test fun systemFoldersStayButTheirContentsMove() {
        val root = Files.createTempDirectory("drive-root").toFile()
        DriveRules.ensureSystemFolders(root)
        assertTrue(File(root, "Documents/Scanned Documents").isDirectory)
        for (attempt in listOf<() -> Unit>(
            { DriveRules.rename(root, "Documents", "Docs") },
            { DriveRules.moveToTrash(root, "Documents") },
            { DriveRules.move(root, "Documents/Scanned Documents", "") },
            { DriveRules.moveToTrash(root, "Documents/Scanned Documents") },
        )) try { attempt(); fail("a system folder moved") } catch (_: IllegalArgumentException) {}
        File(root, "Documents/Scanned Documents/scan.pdf").writeText("x")
        DriveRules.moveToTrash(root, "Documents/Scanned Documents/scan.pdf")
        assertTrue(File(root, "Trash/scan.pdf").exists())
        assertTrue(File(root, "Documents/Scanned Documents").isDirectory)
    }

    @Test fun containmentRejectsSiblingPrefixAndTraversal() {
        val base = Files.createTempDirectory("drive-root").toFile()
        val root = File(base, "Drive").apply { mkdirs() }
        assertTrue(DriveRules.inside(root, File(root, "nested/file.txt")))
        assertFalse(DriveRules.inside(root, File(base, "Drive-copy/file.txt")))
        assertFalse(DriveRules.inside(root, File(root, "../outside.txt")))
    }

    @Test fun containmentRejectsSymlinkEscape() {
        val base = Files.createTempDirectory("drive-root").toFile()
        val root = File(base, "Drive").apply { mkdirs() }
        val outside = File(base, "outside").apply { mkdirs() }
        val link = File(root, "escape")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertFalse(DriveRules.inside(root, File(link, "private.txt")))
    }

    @Test fun recentReopenMovesItToFrontAndCapsAtFifty() {
        val original = (1..50).map { DriveRecent("$it.txt", it.toLong()) }
        val reordered = DriveRecentsRules.record(original, "20.txt", 100)
        assertEquals("20.txt", reordered.first().relativePath)
        assertEquals(50, reordered.size)
        assertEquals(1, reordered.count { it.relativePath == "20.txt" })
    }

    @Test fun storedFileHandlerFollowsFolderMove() {
        assertEquals("Archive/report.pdf", DriveStoredPathRules.rewrite("Work/report.pdf", "Work", "Archive"))
        assertEquals("Archive/nested/report.pdf", DriveStoredPathRules.rewrite("Work/nested/report.pdf", "Work", "Archive"))
        assertEquals("Else/report.pdf", DriveStoredPathRules.rewrite("Else/report.pdf", "Work", "Archive"))
    }

    @Test fun tagsAreTrimmedValidatedAndUnique() {
        assertEquals(setOf("Work", "Tax"), DriveTagRules.names(setOf(" Work ", "Tax")))
        try {
            DriveTagRules.name("one,two")
            fail("Comma-separated tags are not one tag")
        } catch (_: IllegalArgumentException) { }
        try {
            DriveTagRules.names(setOf("Work", " Work "))
            fail("Normalized tags must remain unique")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun driveSpaceCountsTypesAndKeepsTrashSeparateFromSearch() {
        val root = Files.createTempDirectory("drive-space").toFile()
        File(root, "note.md").writeText("abc")
        File(root, "photo.jpg").writeText("12345")
        File(root, "Trash").apply { mkdirs() }
        File(root, "Trash/old.pdf").writeText("1234567")
        assertEquals(setOf("note.md", "photo.jpg"), DriveRules.allItems(root).map { it.relativePath }.toSet())
        assertEquals(3L, DriveRules.spaceUsage(root).bytesByType["Text"])
        assertEquals(5L, DriveRules.spaceUsage(root).bytesByType["Pictures"])
        assertEquals(7L, DriveRules.spaceUsage(root).bytesByType["PDF"])
    }

    @Test fun mutationsStayInsideDriveAndNeverOverwrite() {
        val base = Files.createTempDirectory("drive-root").toFile()
        val root = File(base, "Drive").apply { mkdirs() }
        val nested = File(root, "nested").apply { mkdirs() }
        File(root, "one.txt").writeText("one")
        File(nested, "two.txt").writeText("two")

        assertEquals("nested/one.txt", DriveRules.move(root, "one.txt", "nested"))
        assertTrue(File(nested, "one.txt").isFile)
        assertEquals("nested/copy.txt", DriveRules.rename(root, "nested/two.txt", "copy.txt"))
        assertEquals("copy.txt", DriveRules.copy(root, "nested/copy.txt", ""))
        assertEquals("Trash/copy.txt", DriveRules.moveToTrash(root, "copy.txt"))
        assertTrue(File(root, "Trash/copy.txt").isFile)

        try {
            DriveRules.move(root, "nested", "nested")
            fail("A folder must not move into itself")
        } catch (_: IllegalArgumentException) { }
        try {
            DriveRules.rename(root, "nested/one.txt", "../outside.txt")
            fail("A rename must not escape Drive")
        } catch (_: IllegalArgumentException) { }
        assertFalse(File(base, "outside.txt").exists())
    }

    @Test fun emptyTrashPermanentlyDeletesOnlyItsContents() {
        val root = Files.createTempDirectory("drive-trash").toFile()
        File(root, "keep.txt").writeText("keep")
        File(root, "Trash").mkdirs()
        File(root, "Trash/delete.txt").writeText("delete")
        assertEquals(1, DriveRules.emptyTrash(root))
        assertTrue(File(root, "keep.txt").exists())
        assertTrue(File(root, "Trash").isDirectory)
        assertFalse(File(root, "Trash/delete.txt").exists())
    }
}
