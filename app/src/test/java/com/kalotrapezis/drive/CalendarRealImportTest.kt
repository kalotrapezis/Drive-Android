package com.kalotrapezis.drive

import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The user's own calendar (a Google export), imported, imported again and exported, run only when asked:
 * `TETRA_CALENDAR_ZIP=… TETRA_CALENDAR_OUT=dir ./gradlew testDebugUnitTest --tests '*CalendarRealImportTest*'`.
 * It writes its occurrences and reminders to OUT, to be compared with the desktop's and the skill's; nothing of the
 * calendar is kept in the repository.
 */
class CalendarRealImportTest {
    @Test fun `the user's Google calendar in and out again`() {
        val zip = System.getenv("TETRA_CALENDAR_ZIP"); val out = System.getenv("TETRA_CALENDAR_OUT")
        assumeTrue(zip != null && out != null)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens"))
        val root = Files.createTempDirectory("real-calendar").toFile()
        try {
            val incs = CalendarTransfer.readZip(File(zip!!).readBytes())
            val inc = incs.single()
            val cal = CalendarFiles.newCalendar(root, inc.name, CalendarFiles.PALETTE[1])
            val first = CalendarTransfer.write(cal, inc)
            assertEquals(inc.events.size to 0, first)
            assertEquals(0 to inc.events.size, CalendarTransfer.write(cal, CalendarTransfer.readZip(File(zip).readBytes()).single()))
            val o = File(out!!).apply { mkdirs() }
            File(o, "kotlin-export.ics").writeText(CalendarTransfer.export(cal))
            val from = Instant.parse("2015-01-01T00:00:00Z"); val to = Instant.parse("2031-01-01T00:00:00Z")
            val occ = CalendarFiles.occurrences(listOf(cal), from, to)
            File(o, "kotlin-occ.txt").writeText(occ.map { "${it.file.uid} ${it.start.toInstant().toEpochMilli().let { ms -> java.time.format.DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms)).replace("Z", ".000Z") }}" }.sorted().joinToString("\n") + "\n")
            val rem = CalendarAlarms.reminders(listOf(cal), from, to)
            File(o, "kotlin-rem.txt").writeText(rem.map { "${it.key.substringBefore('|')} ${java.time.format.DateTimeFormatter.ISO_INSTANT.format(it.at).replace("Z", ".000Z")}" }.sorted().joinToString("\n") + "\n")
            println("real calendar: ${inc.events.size} events, ${occ.size} occurrences, ${rem.size} reminders")
        } finally { root.deleteRecursively() }
    }
}
