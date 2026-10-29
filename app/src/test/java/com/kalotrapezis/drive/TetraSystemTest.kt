package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Tetra's own folders in /sdcard/Tetra/.System, sync's names kept (the computer's test/system.test.js has the same cases). */
class TetraSystemTest {
    private fun put(f: File, text: String = "x") { f.parentFile?.mkdirs(); f.writeText(text) }

    @Test fun syncNamesMapToSharedAndBack() {
        assertEquals(".System/Calendar/Shared/Work/a.ics", TetraSystem.onDisk("Calendar/Work/a.ics"))
        assertEquals(".System/Trash/Shared", TetraSystem.onDisk("Trash"))
        assertEquals("Documents/Calendar/a.ics", TetraSystem.onDisk("Documents/Calendar/a.ics"))
        assertEquals("Calendar/Work/a.ics", TetraSystem.fromDisk(".System/Calendar/Shared/Work/a.ics"))
        assertEquals("Trash", TetraSystem.fromDisk(".System/Trash/Shared"))
        assertEquals(".System/Office/Data/a.potx", TetraSystem.fromDisk(".System/Office/Data/a.potx"))
        val tetra = File("/sdcard/Tetra")
        assertEquals(tetra, TetraSystem.tetraOf(File(tetra, TetraSystem.onDisk("Calendar"))))
    }

    @Test fun theFoldersMoveByRenamingAndAnInterruptedMoveFinishes() {
        val tetra = Files.createTempDirectory("tetra-system").toFile()
        put(File(tetra, "Calendar/Personal/bday.ics"), "ev")
        put(File(tetra, "Maps/greece.pmtiles"), "tiles")
        put(File(tetra, "Trash/old.docx"), "doc")
        put(File(tetra, ".notes/n1.json"), "{}")
        put(File(tetra, ".templates/School.potx"), "theme")
        put(File(tetra, "Documents/mine.docx"), "mine")
        put(File(tetra, "From computer/pic.jpg"), "pic")
        put(File(tetra, ".System/Timer/Shared/new.json"), "new") // written by the new version before the move
        put(File(tetra, "Timer/new.json"), "old")
        put(File(tetra, "Timer/a.json"), "a")
        val key = Files.readAttributes(File(tetra, "Maps/greece.pmtiles").toPath(), java.nio.file.attribute.BasicFileAttributes::class.java).fileKey()
        val (_, failed) = TetraSystem.migrate(tetra)
        fun read(p: String) = File(tetra, ".System/$p").readText()
        assertEquals("ev", read("Calendar/Shared/Personal/bday.ics"))
        assertEquals("renamed, not copied", key, Files.readAttributes(File(tetra, ".System/Maps/Shared/greece.pmtiles").toPath(), java.nio.file.attribute.BasicFileAttributes::class.java).fileKey())
        assertEquals("doc", read("Trash/Shared/old.docx"))
        assertEquals("{}", read("Notes/Shared/n1.json"))
        assertEquals("theme", read("Office/Data/School.potx"))
        assertEquals("a", read("Timer/Shared/a.json"))
        assertEquals("new", read("Timer/Shared/new.json"))
        assertEquals("both kept", "old", File(tetra, "Timer/new.json").readText())
        assertEquals(1, failed.size)
        assertEquals(listOf(".System", "Documents", "From computer", "Timer"), tetra.list()!!.sorted())
        File(tetra, ".System/Timer/Shared/new.json").delete()
        assertTrue("run again, it finishes", TetraSystem.migrate(tetra).second.isEmpty())
        assertEquals(listOf(".System", "Documents", "From computer"), tetra.list()!!.sorted())
        assertEquals("and only once", emptyList<String>() to emptyList<String>(), TetraSystem.migrate(tetra))
        tetra.deleteRecursively()
    }

    @Test fun filesAndSyncSeeTheSamePathsAsBefore() {
        val tetra = Files.createTempDirectory("tetra-paths").toFile()
        put(File(tetra, ".System/Calendar/Shared/Work/a.ics"), "ev")
        put(File(tetra, "Documents/a.txt"), "doc")
        DriveRules.ensureSystemFolders(tetra)
        assertFalse("no Tetra/Calendar made again", File(tetra, "Calendar").exists())
        assertEquals("ev", DriveRules.file(tetra, "Calendar/Work/a.ics").readText())
        assertEquals("Calendar/Work/a.ics", DriveRules.relative(tetra, File(tetra, ".System/Calendar/Shared/Work/a.ics")))
        // a sync's new event lands in .System; one deleted goes to .System/Trash/Shared and is called Trash/… as before
        assertEquals(File(tetra, ".System/Calendar/Shared/Work/b.ics").canonicalFile, DriveRules.newFile(tetra, "Calendar/Work/b.ics"))
        assertEquals("Trash/a.ics", DriveRules.moveToTrash(tetra, "Calendar/Work/a.ics"))
        assertTrue(File(tetra, ".System/Trash/Shared/a.ics").isFile)
        assertEquals(listOf("Trash/a.ics"), listDriveFolder(tetra, "Trash").map { it.relativePath })
        val top = listDriveFolder(tetra, "").map { it.relativePath }
        assertTrue("Files shows only the user's own", "Documents" in top && top.none { it in TetraSystem.SHARED || it.startsWith(".") })
        tetra.deleteRecursively()
    }

    @Test fun identicalSyncCopiesGoToBackupsAndTheOthersStayListed() {
        val tetra = Files.createTempDirectory("tetra-copies").toFile()
        put(File(tetra, "Documents/a.docx"), "same"); put(File(tetra, "Documents/a (2).docx"), "same")
        put(File(tetra, "Documents/b.docx"), "one"); put(File(tetra, "Documents/b (2).docx"), "two")
        put(File(tetra, "Documents/c (3).pdf"), "alone")
        put(File(tetra, ".System/Calendar/Shared/Work/x.ics"), "ev"); put(File(tetra, ".System/Calendar/Shared/Work/x (2).ics"), "ev")
        put(File(tetra, ".System/Trash/Shared/y.ics"), "ev"); put(File(tetra, ".System/Trash/Shared/y (2).ics"), "ev") // the Trash's own names
        val dry = TetraSystem.setAsideCopies(tetra, "2026-10-30", dryRun = true)
        assertTrue("a dry run moves nothing", File(tetra, "Documents/a (2).docx").exists())
        val r = TetraSystem.setAsideCopies(tetra, "2026-10-30")
        assertEquals(dry.moved.sorted(), r.moved.sorted())
        assertEquals(listOf("Calendar/Work/x (2).ics", "Documents/a (2).docx"), r.moved.sorted())
        assertEquals(listOf("Documents/b (2).docx", "Documents/c (3).pdf"), r.left.sorted())
        val aside = File(tetra, ".System/Backups/Data/Sync copies/2026-10-30")
        assertEquals("same", File(aside, "Documents/a (2).docx").readText())
        assertEquals("ev", File(aside, "Calendar/Work/x (2).ics").readText())
        assertFalse(File(tetra, "Documents/a (2).docx").exists())
        assertTrue(File(tetra, "Documents/b (2).docx").exists() && File(tetra, "Documents/c (3).pdf").exists())
        assertTrue(File(aside, "List.txt").readText().contains("Documents/b (2).docx"))
        tetra.deleteRecursively()
    }
}
