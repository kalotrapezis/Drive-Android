package com.kalotrapezis.drive

import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Calendar's files (Plan.md § Calendar): Google's, Claude's and Tetra's own, read, repeated, reminded and written back. */
class CalendarTest {
    private val athens = ZoneId.of("Europe/Athens")
    private lateinit var root: File
    private lateinit var cal: CalendarInfo

    @Before fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone(athens))
        root = Files.createTempDirectory("calendar").toFile()
        cal = CalendarFiles.newCalendar(root, "Work", CalendarFiles.PALETTE[5])
        File("src/test/resources/calendar").listFiles()!!.forEach { it.copyTo(File(cal.dir, it.name)) }
    }

    private fun at(s: String) = LocalDateTime.parse(s).atZone(athens).toInstant()
    private fun occ(from: String, to: String) = CalendarFiles.occurrences(CalendarFiles.calendars(root), at(from), at(to))
    private fun local(o: Occurrence) = o.start.withZoneSameInstant(athens).toLocalDateTime().toString()

    @Test fun `a calendar is a folder with its colour`() {
        val c = CalendarFiles.calendars(root).single()
        assertEquals("Work", c.name)
        assertEquals("#33B679\n", File(c.dir, "color").readText())
        assertEquals(CalendarFiles.PALETTE[5], c.color)
    }

    @Test fun `Google's weekly meeting with a day off and a day moved`() {
        val staff = occ("2026-10-01T00:00", "2026-12-01T00:00").filter { it.event.uid.startsWith("staff") }
        assertEquals(listOf("2026-10-06T10:00", "2026-10-13T10:00", "2026-10-27T12:00", "2026-11-03T10:00", "2026-11-10T10:00"), staff.map(::local))
        assertEquals("Staff meeting (moved)", staff[2].event.title)
        assertEquals("Room 2, first floor", staff[0].event.place)
        assertEquals("Agenda:\n1. Timetable\n2. Trips", staff[0].event.notes)
        // Its COUNT still holds when the range starts after the first ones (09:00 stays 10:00 across the DST change).
        assertEquals(listOf("2026-11-03T10:00", "2026-11-10T10:00"),
            occ("2026-11-01T00:00", "2027-01-01T00:00").filter { it.event.uid.startsWith("staff") }.map(::local))
        assertEquals(listOf(-900L), staff[0].event.alarms.map { it.days * 86400 + it.seconds })
    }

    @Test fun `the third Tuesday of each month until the year ends`() {
        val council = occ("2026-10-01T00:00", "2027-03-01T00:00").filter { it.event.uid == "parents-council" }
        assertEquals(listOf("2026-10-20T18:00", "2026-11-17T18:00", "2026-12-15T18:00"), council.map(::local))
        assertEquals(90L, java.time.Duration.between(council[0].start, council[0].end).toMinutes())
    }

    @Test fun `a birthday every year, reminded three days running at 9`() {
        val b = occ("2027-03-01T00:00", "2027-04-01T00:00").single { it.event.uid == "mum-birthday-2026" }
        assertTrue(b.allDay)
        assertEquals("2027-03-12T00:00", local(b))
        assertEquals(listOf("2027-03-09T09:00", "2027-03-10T09:00", "2027-03-11T09:00"),
            b.event.alarms.single().times(b.start, b.end).map { it.toLocalDateTime().toString() })
    }

    @Test fun `a file Claude wrote, in UTC with plain line ends, reads`() {
        val d = occ("2026-10-20T00:00", "2026-10-21T00:00").single { it.event.title == "Dentist" }
        assertEquals("2026-10-20T09:30", local(d))
        assertEquals("Dr Papadopoulou, Thiva", d.event.place)
    }

    @Test fun `an edit keeps every line it does not change`() {
        val f = CalendarFiles.files(cal).single { it.uid.startsWith("staff") }
        CalendarFiles.save(f.file, f.master!!.copy(title = "Staff meeting, Room 4"), Instant.parse("2026-10-18T10:00:00Z"))
        val text = f.file.readText()
        assertTrue("X-GOOGLE-CONFERENCE:https://meet.google.com/abc-defg-hij" in text)
        assertTrue("RECURRENCE-ID;TZID=Europe/Athens:20261027T100000" in text)
        assertTrue(text.split("\r\n").all { it.toByteArray().size <= 75 })
        val again = CalendarFiles.read(f.file)
        assertEquals("Staff meeting, Room 4", again.master!!.title)
        assertTrue(again.comps.single().comps("VEVENT")[0].prop("ATTENDEE")!!.param("CN") == "Θεολόγος Καλοτραπέζης")
        assertEquals("1", again.comps.single().comps("VEVENT")[0].prop("SEQUENCE")!!.value)
        assertEquals(5, CalendarFiles.occurrences(cal, again, at("2026-10-01T00:00"), at("2026-12-01T00:00")).size)
    }

    @Test fun `a new event is written and read back the same`() {
        val uid = CalendarFiles.newUid()
        val start = LocalDateTime.parse("2026-11-02T00:00").atZone(athens)
        val e = CalEvent(uid, "School trip; Delphi", "Delphi", "Bring lunch,\nand water", true, start, start.plusDays(2),
            rrule = null, alarms = listOf(CalAlarm(-1, 9 * 3600L), CalAlarm(-3, 9 * 3600L, repeat = 2, every = 86400)))
        val file = File(cal.dir, CalendarFiles.fileName(e.title, uid))
        assertTrue(file.name.startsWith("school-trip-delphi-"))
        val back = CalendarFiles.save(file, e).master!!
        assertEquals(e.copy(modified = back.modified, alarms = back.alarms), back)
        // "The day before at 9" is written as 15 hours before midnight; the same times either way.
        assertEquals(e.alarms.map { it.times(e.start, e.end) }, back.alarms.map { it.times(back.start, back.end) })
        assertTrue("DTSTART;VALUE=DATE:20261102" in file.readText())
        assertTrue("TRIGGER:-P2DT15H" in file.readText())
    }

    @Test fun `the same event twice after a sync conflict shows once, the newer`() {
        val f = CalendarFiles.files(cal).single { it.uid.startsWith("staff") }
        val copy = File(cal.dir, "staff-meeting (2).ics")
        f.file.copyTo(copy)
        CalendarFiles.save(copy, f.master!!.copy(title = "Newer"), Instant.parse("2026-10-18T10:00:00Z"))
        val shown = CalendarFiles.files(cal).filter { it.uid.startsWith("staff") }
        assertEquals(1, shown.size)
        assertEquals("Newer", shown[0].master!!.title)
        assertEquals(2, shown[0].versions)
    }

    @Test fun `durations both ways`() {
        assertEquals("-P2DT15H", ICal.durationValue(-2, -15 * 3600))
        assertEquals("-PT15M", ICal.durationValue(0, -900))
        assertEquals("PT0S", ICal.durationValue(0, 0))
        assertEquals("P1D", ICal.durationValue(1, 0))
        assertEquals(-14L to 0L, ICal.duration("-P2W"))
        assertEquals(0L to -900L, ICal.duration("-P0DT0H15M0S"))
    }

    @Test fun `zones as Google, Mozilla and Outlook write them`() {
        assertEquals(athens, ICal.zone("Europe/Athens"))
        assertEquals(athens, ICal.zone("/mozilla.org/20050126_1/Europe/Athens"))
    }

    private fun staff() = CalendarFiles.files(cal).single { it.uid.startsWith("staff") }
    private fun staffDays() = CalendarFiles.occurrences(cal, staff(), at("2026-10-01T00:00"), at("2026-12-01T00:00")).map(::local)
    private fun z(s: String) = LocalDateTime.parse(s).atZone(athens)

    @Test fun `one day of a repeat moved, then deleted`() {
        val f = staff()
        val day = z("2026-11-03T10:00")
        CalendarFiles.saveDay(f, day, f.master!!.copy(title = "Staff meeting, in the library", start = z("2026-11-03T13:00"), end = z("2026-11-03T14:00")))
        assertEquals(listOf("2026-10-06T10:00", "2026-10-13T10:00", "2026-10-27T12:00", "2026-11-03T13:00", "2026-11-10T10:00"), staffDays())
        assertEquals("Staff meeting, in the library", CalendarFiles.occurrences(cal, staff(), at("2026-11-03T00:00"), at("2026-11-04T00:00")).single().event.title)
        // the same day changed again replaces its change, not a second one
        CalendarFiles.saveDay(staff(), day, f.master!!.copy(start = z("2026-11-03T15:00"), end = z("2026-11-03T16:00")))
        assertEquals("2026-11-03T15:00", staffDays()[3])
        CalendarFiles.skipDay(staff(), day)
        assertEquals(listOf("2026-10-06T10:00", "2026-10-13T10:00", "2026-10-27T12:00", "2026-11-10T10:00"), staffDays())
    }

    @Test fun `this and following - the old one ends the day before, a moved day after it goes`() {
        CalendarFiles.endBefore(staff(), z("2026-10-27T10:00"))
        assertEquals(listOf("2026-10-06T10:00", "2026-10-13T10:00"), staffDays())
        assertTrue("UNTIL=20261027T075959Z" in staff().file.readText())
    }

    @Test fun `an event to another calendar and to the Trash`() {
        val home = CalendarFiles.newCalendar(root, "Home", CalendarFiles.PALETTE[0])
        val moved = CalendarFiles.move(staff().file, home)
        assertEquals(1, CalendarFiles.files(home).size)
        File(root.parentFile, "Trash").deleteRecursively()
        val tetra = Files.createTempDirectory("tetra").toFile()
        val calRoot = File(tetra, "Calendar"); root.copyRecursively(calRoot)
        val there = File(calRoot, "Home/${moved.name}")
        assertEquals("Trash/${moved.name}", CalendarFiles.trash(calRoot, there))
        assertTrue(!there.exists() && File(tetra, "Trash/${moved.name}").isFile)
    }

    @Test fun `the reminders due in a span, a repeating one each day`() {
        val due = CalendarAlarms.reminders(CalendarFiles.calendars(root), at("2027-03-08T00:00"), at("2027-03-12T00:00"))
            .filter { it.occ.event.uid == "mum-birthday-2026" }
        assertEquals(listOf("2027-03-09T09:00", "2027-03-10T09:00", "2027-03-11T09:00"), due.map { LocalDateTime.ofInstant(it.at, athens).toString() })
        assertEquals(3, due.map { it.key }.toSet().size)
        // the same span again gives the same keys: a reminder is never shown twice under two names
        assertEquals(due.map { it.key }, CalendarAlarms.reminders(CalendarFiles.calendars(root), at("2027-03-08T00:00"), at("2027-03-12T00:00")).filter { it.occ.event.uid == "mum-birthday-2026" }.map { it.key })
        // Google's 15 minutes before the staff meeting
        val staff = CalendarAlarms.reminders(CalendarFiles.calendars(root), at("2026-10-13T00:00"), at("2026-10-14T00:00")).single { it.occ.event.uid.startsWith("staff") }
        assertEquals("2026-10-13T09:45", LocalDateTime.ofInstant(staff.at, athens).toString())
    }

    @Test fun `Google's export imported, split per event, and again without doubles`() {
        val text = File("src/test/resources/calendar-import/takeout-greece.ics").readText()
        val inc = CalendarTransfer.read(text, "takeout-greece")
        assertEquals("School 2026-27", inc.name)
        val into = CalendarFiles.newCalendar(root, inc.name, CalendarFiles.PALETTE[3])
        assertEquals(2 to 0, CalendarTransfer.write(into, inc))
        assertEquals(0 to 2, CalendarTransfer.write(into, CalendarTransfer.read(text, "x")))
        val files = CalendarFiles.files(into)
        assertEquals(2, files.size)
        val club = files.single { it.uid == "4k1m8s@google.com" }
        assertTrue("BEGIN:VTIMEZONE" in club.file.readText()) // the zone it names comes with it
        assertEquals(listOf("2026-11-06T09:00", "2026-11-13T11:00", "2026-11-20T09:00"),
            CalendarFiles.occurrences(into, club, at("2026-11-01T00:00"), at("2026-11-21T00:00")).map(::local))
    }

    @Test fun `Takeout's zip, and a calendar out again for other apps`() {
        val bytes = java.io.ByteArrayOutputStream().also { b ->
            java.util.zip.ZipOutputStream(b).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("Takeout/Calendar/kalotrapezis@gmail.com.ics")); z.write(File("src/test/resources/calendar/ai-style.ics").readBytes())
                z.putNextEntry(java.util.zip.ZipEntry("Takeout/Calendar/School.ics")); z.write(File("src/test/resources/calendar-import/takeout-greece.ics").readBytes())
            }
        }.toByteArray()
        val got = CalendarTransfer.readZip(bytes)
        assertEquals(listOf("kalotrapezis@gmail.com", "School 2026-27"), got.map { it.name })
        assertEquals(listOf(1, 2), got.map { it.events.size })
        val out = CalendarTransfer.export(cal)
        assertTrue("X-WR-CALNAME:Work" in out && "X-APPLE-CALENDAR-COLOR:#33B679" in out)
        assertTrue("TZID:Europe/Athens" in out) // a VTIMEZONE written for the zone the events name
        assertTrue("RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU" in out && "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU" in out)
        val back = CalendarTransfer.read(out, "x")
        assertEquals(CalendarFiles.files(cal).size, back.events.size)
    }

    @Test fun `two events of one title whose UIDs begin alike get two files`() {
        // Google's UIDs of a series share long beginnings; the user's import lost two events to one file name (18 Oct).
        val ics = { uid: String -> "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:$uid\r\nDTSTAMP:20261018T000000Z\r\nSUMMARY:Lesson\r\nDTSTART:20261020T080000Z\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n" }
        val inc = CalendarTransfer.read(ics("_60q30c1g60o30e1i60o4ac1g60rj8gpl88rj2c1h84s34h9g64o30c1g60o30c1g70o30c1g6ksk2e1n6sp3gc1g64o30c1g60o30c1g60o32c1g60o30c1g6os3achm6or3achl6gpjcdi36l0j0d9m8l0@google.com") +
            ics("_60q30c1g60o30e1i60o4ac1g60rj8gpl88rj2c1h84s34h9g64o30c1g60o30c1g70o30c1g6ksk2e1n6sp3gc1g64o30c1g60o30c1g60o32c1g60o30c1g6os3achm6or3achl6gpjcdi36l0j0d9m8l1@google.com"), "x")
        val into = CalendarFiles.newCalendar(root, "Google", CalendarFiles.PALETTE[1])
        assertEquals(2 to 0, CalendarTransfer.write(into, inc))
        assertEquals(2, CalendarFiles.files(into).size)
        assertEquals(0 to 2, CalendarTransfer.write(into, inc))
        // and a name already taken by another event is never written over
        val f = File(into.dir, CalendarFiles.fileName("Lesson", "a")); f.writeText("x")
        assertEquals(File(into.dir, f.nameWithoutExtension + " (2).ics"), CalendarFiles.newFile(into.dir, "Lesson", "a"))
    }
}
