package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DriveFilesTest {
    @Test fun recursiveCopyRefusesAnEscapingLinkBeforeCopyingAnything() {
        val base = Files.createTempDirectory("drive-copy-link").toFile()
        val root = File(base, "Tetra").apply { mkdirs() }
        val outside = File(base, "outside").apply { mkdirs() }
        File(outside, "private.txt").writeText("private")
        val source = File(root, "source").apply { mkdirs() }
        File(source, "safe.txt").writeText("safe")
        Files.createSymbolicLink(File(source, "escape").toPath(), outside.toPath())
        File(root, "copies").mkdirs()
        assertThrows(IllegalArgumentException::class.java) { DriveRules.copy(root, "source", "copies") }
        assertFalse(File(root, "copies/source").exists())
        assertEquals("private", File(outside, "private.txt").readText())
    }

    @Test fun emptyTrashRefusesNestedLinksBeforeDeletingAnyContents() {
        val base = Files.createTempDirectory("drive-trash-link").toFile()
        val root = File(base, "Tetra").apply { mkdirs() }
        val outside = File(base, "outside").apply { mkdirs() }
        File(outside, "private.txt").writeText("private")
        val trashed = File(root, TetraSystem.onDisk("Trash/folder")).apply { mkdirs() }
        File(trashed, "keep.txt").writeText("keep")
        Files.createSymbolicLink(File(trashed, "escape").toPath(), outside.toPath())
        assertThrows(IllegalArgumentException::class.java) { DriveRules.emptyTrash(root) }
        assertEquals("keep", File(trashed, "keep.txt").readText())
        assertEquals("private", File(outside, "private.txt").readText())
    }

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
        assertTrue(File(root, TetraSystem.onDisk("Trash/scan.pdf")).exists())
        assertTrue(File(root, "Documents/Scanned Documents").isDirectory)
    }

    @Test fun aChosenFolderIsASystemFolderUntilItIsLeftOut() {
        try {
            DriveRules.chosenFolders = setOf("Music")
            assertTrue(DriveRules.isSystem("Music"))
            assertFalse(DriveRules.isSystem("Work"))
            assertTrue(DriveRules.isSystem("Documents"))
            DriveRules.chosenFolders = emptySet()
            assertFalse(DriveRules.isSystem("Music"))
            assertTrue(DriveRules.isSystem("Documents/Scanned Documents"))
        } finally { DriveRules.chosenFolders = emptySet() }
    }

    @Test fun aFolderLeftOutGoesWithItsContentsOutOrInTheTrash() {
        val root = Files.createTempDirectory("drive-root").toFile()
        try {
            DriveRules.chosenFolders = emptySet()
            File(root, "Music/Live").mkdirs(); File(root, "Music/song.mp3").writeText("x"); File(root, "song.mp3").writeText("y")
            File(root, "Work").mkdirs(); File(root, "Work/plan.pdf").writeText("x"); File(root, "Books").mkdirs()
            val moved = DriveRules.clearLeftOut(root, "Music", toTrash = false).toMap()
            assertEquals("song (2).mp3", moved["Music/song.mp3"])
            assertEquals("Live", moved["Music/Live"])
            assertFalse(File(root, "Music").exists())
            assertEquals(listOf("Work" to "Trash/Work"), DriveRules.clearLeftOut(root, "Work", toTrash = true))
            assertTrue(File(root, TetraSystem.onDisk("Trash/Work/plan.pdf")).exists())
            DriveRules.clearLeftOut(root, "Books", toTrash = true)
            assertFalse(File(root, "Books").exists())
            DriveRules.chosenFolders = setOf("AI")
            try { DriveRules.clearLeftOut(root, "AI", toTrash = false); fail("a chosen folder stays") } catch (_: IllegalArgumentException) {}
            try { DriveRules.clearLeftOut(root, "Documents", toTrash = false); fail("Documents always stays") } catch (_: IllegalArgumentException) {}
        } finally { DriveRules.chosenFolders = emptySet(); root.deleteRecursively() }
    }

    @Test fun theLaterSystemFolderChoiceWinsAndATieGoesToChosen() {
        assertTrue(DriveRules.incomingChoiceWins(localChosen = false, localAt = 0, chosen = true, at = 0))
        assertFalse(DriveRules.incomingChoiceWins(localChosen = true, localAt = 0, chosen = false, at = 0))
        assertTrue(DriveRules.incomingChoiceWins(localChosen = true, localAt = 5, chosen = false, at = 6))
        assertFalse(DriveRules.incomingChoiceWins(localChosen = false, localAt = 6, chosen = true, at = 5))
    }

    @Test fun aNameAlreadyInTrashNeverBlocksADelete() {
        val root = Files.createTempDirectory("drive-root").toFile()
        repeat(2) { File(root, "QA").mkdirs(); File(root, "a.txt").writeText("x") ; DriveRules.moveToTrash(root, "QA"); DriveRules.moveToTrash(root, "a.txt") }
        assertTrue(File(root, TetraSystem.onDisk("Trash/QA (2)")).isDirectory)
        assertTrue(File(root, TetraSystem.onDisk("Trash/a (2).txt")).exists())
        assertFalse(File(root, "QA").exists())
    }

    @Test fun dotFoldersAreHidden() {
        val root = Files.createTempDirectory("drive-root").toFile()
        File(root, ".notes").mkdirs(); File(root, ".notes/n.json").writeText("x"); File(root, "a.txt").writeText("x")
        assertEquals(listOf("a.txt"), listDriveFolder(root, "").map { it.relativePath })
        assertEquals(listOf("a.txt"), DriveRules.allItems(root).map { it.relativePath })
        assertFalse(DriveRules.subfolders(root, "").any { it.startsWith(".") })
    }

    @Test fun aFolderIsListedWithOneLookAtEachFileAndALinkOutsideStaysOut() {
        val root = Files.createTempDirectory("drive-root").toFile()
        val outside = Files.createTempDirectory("outside").toFile().also { File(it, "secret.txt").writeText("no") }
        File(root, "Work/Sub").mkdirs(); File(root, "Work/a.txt").writeText("hello")
        Files.createSymbolicLink(File(root, "Work/out").toPath(), outside.toPath())
        Files.createSymbolicLink(File(root, "Work/in").toPath(), File(root, "Work/Sub").toPath())
        val items = listDriveFolder(root, "Work")
        assertEquals(listOf("Work/Sub" to true, "Work/Sub" to true, "Work/a.txt" to false), items.map { it.relativePath to it.isDirectory })
        val a = items.single { it.file.name == "a.txt" }
        assertEquals(5L, a.length); assertEquals(File(root, "Work/a.txt").lastModified(), a.lastModified)
    }

    @Test fun moveOffersOneLevelAtATime() {
        // asked 17 Oct: Move listed every folder of every level; now the ones right inside the folder you are in
        val root = Files.createTempDirectory("drive-root").toFile()
        listOf("Work/2026/Reports", "Documents", "music", "Trash/old", ".hidden").forEach { File(root, it).mkdirs() }
        File(root, "a.txt").writeText("x")
        assertEquals(listOf("Documents", "music", "Work"), DriveRules.subfolders(root, ""))
        assertEquals(listOf("Work/2026"), DriveRules.subfolders(root, "Work"))
        assertEquals(listOf("Work/2026/Reports"), DriveRules.subfolders(root, "Work/2026"))
        assertEquals(emptyList<String>(), DriveRules.subfolders(root, "Work/2026/Reports"))
    }

    @Test fun theFileProviderCoversTheTetraFolder() {
        // Open and Open with failed for every file after the folder became Tetra (26 September): the provider named Drive/.
        val paths = File("src/main/res/xml/local_drive_paths.xml").readText()
        assertTrue(paths.contains("path=\"Tetra/\""))
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
        File(root, TetraSystem.onDisk("Trash")).apply { mkdirs() }
        File(root, TetraSystem.onDisk("Trash/old.pdf")).writeText("1234567")
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
        assertTrue(File(root, TetraSystem.onDisk("Trash/copy.txt")).isFile)

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
        File(root, TetraSystem.onDisk("Trash")).mkdirs()
        File(root, TetraSystem.onDisk("Trash/delete.txt")).writeText("delete")
        assertEquals(1, DriveRules.emptyTrash(root))
        assertTrue(File(root, "keep.txt").exists())
        assertTrue(File(root, TetraSystem.onDisk("Trash")).isDirectory)
        assertFalse(File(root, TetraSystem.onDisk("Trash/delete.txt")).exists())
    }
}
