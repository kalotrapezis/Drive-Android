package com.kalotrapezis.drive

import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Birthdays into the Calendar (Plan.md § Contacts › Birthdays): the contact is the truth and its event follows it; the
 * events already in Γενέθλια (from Google Calendar) are matched by name and linked, never doubled. Made-up people.
 */
class ContactsCalendarTest {
    private val zone = ZoneId.of("Europe/Athens")
    private val today = LocalDate.of(2026, 10, 23)

    private fun setUp(): Triple<File, File, CalendarInfo> {
        val tetra = scratchDir("tetra")
        val calRoot = CalendarFiles.root(tetra)
        val cal = CalendarFiles.newCalendar(calRoot, "Γενέθλια", 0xFF8E24AA.toInt())
        CalendarFiles.newCalendar(calRoot, "Personal", 0xFFE91E63.toInt())
        return Triple(calRoot, File(tetra, ContactsFiles.FOLDER), cal)
    }

    /** An event as Google Calendar's import left them in Γενέθλια. */
    private fun googleEvent(cal: CalendarInfo, title: String, date: LocalDate) = CalendarFiles.newFile(cal.dir, title, title).also { f ->
        f.writeText("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:${title.hashCode()}@google.com\r\nDTSTART;VALUE=DATE:${date.toString().replace("-", "")}\r\n" +
            "RRULE:FREQ=YEARLY\r\nSUMMARY:$title\r\nBEGIN:VALARM\r\nACTION:DISPLAY\r\nTRIGGER:-P0DT15H0M0S\r\nEND:VALARM\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n")
    }

    @Test fun aBirthdayBecomesAYearlyEventThatFollowsTheContact() {
        val (calRoot, dir, cal) = setUp()
        var c = ContactsFiles.save(dir, null, ContactInfo(given = "Ελένη", family = "Δοκιμαστή", birthday = VDate(1985, 3, 14), dates = listOf(VField("Anniversary", "2015-06-12"))))
        assertEquals(2, ContactsBirthdays.sync(calRoot, c.info, null, emptyList(), zone = zone, today = today))
        var have = ContactsBirthdays.linked(calRoot)[c.info.uid]!!
        assertEquals(2, have.size)
        val bday = have.single { it.master!!.title == "Ελένη Δοκιμαστή" }
        assertEquals(cal.dir, bday.file.parentFile)
        val text = bday.file.readText()
        for (l in listOf("DTSTART;VALUE=DATE:19850314", "RRULE:FREQ=YEARLY", "X-TETRA-CONTACT:${c.info.uid}", "X-TETRA-BIRTH-YEAR:1985")) assertTrue(l, text.contains(l))
        assertEquals(2, bday.master!!.alarms.size) // 9:00 on the day and 9:00 the day before
        assertTrue(have.single { it != bday }.file.readText().contains("X-TETRA-KIND:Anniversary"))
        // nothing changed: nothing written
        assertEquals(0, ContactsBirthdays.sync(calRoot, c.info, null, have, zone = zone, today = today))
        // the birthday moved and the year forgotten; the anniversary removed
        c = ContactsFiles.save(dir, c, c.info.copy(birthday = VDate(null, 3, 15), dates = emptyList()))
        ContactsBirthdays.sync(calRoot, c.info, null, have, zone = zone, today = today)
        have = ContactsBirthdays.linked(calRoot)[c.info.uid]!!
        val moved = have.single().master!!
        assertEquals(LocalDate.of(2026, 3, 15), moved.start.toLocalDate())
        assertTrue(!have.single().file.readText().contains("X-TETRA-BIRTH-YEAR"))
        assertEquals(1, File(TetraSystem.tetraOf(calRoot), TetraSystem.onDisk("Trash")).listFiles()!!.size) // the anniversary's event: in the Trash, not deleted
        // the contact to the Trash: its event too
        ContactsBirthdays.sync(calRoot, c.info, null, have, gone = true)
        assertNull(ContactsBirthdays.linked(calRoot)[c.info.uid])
    }

    @Test fun theEventsAlreadyThereAreLinkedNotDoubled() {
        val (calRoot, dir, cal) = setUp()
        googleEvent(cal, "Γενέθλια νίκου (ανηψιός)", LocalDate.of(2015, 3, 14))
        googleEvent(cal, "🎂 Σοφία Υποθετική", LocalDate.of(2024, 8, 29))
        googleEvent(cal, "🎂 Βαφτιστήρι", LocalDate.of(2024, 10, 24)) // names no one: stays as it is
        val nikos = ContactsFiles.save(dir, null, ContactInfo(given = "Νίκος", family = "Ανηψιός", formatted = "Νίκος Ανηψιός")).info // no birthday yet
        val sofia = ContactsFiles.save(dir, null, ContactInfo(given = "Σοφία", family = "Υποθετική", birthday = VDate(null, 8, 29))).info
        ContactsFiles.save(dir, null, ContactInfo(given = "Σοφία", family = "Άλλη", birthday = VDate(null, 1, 5))) // another Σοφία, another day
        val all = ContactsFiles.all(dir).map { it.info }
        val links = ContactsBirthdays.suggestions(calRoot, all, null)
        assertEquals(listOf("Γενέθλια νίκου (ανηψιός)" to nikos.uid, "🎂 Σοφία Υποθετική" to sofia.uid).sortedBy { it.first }, links.map { it.title to it.contact.uid }.sortedBy { it.first })
        links.forEach { ContactsBirthdays.link(calRoot, dir, it) }
        // Νίκος took the event's day; Σοφία's event is hers
        assertEquals(VDate(null, 3, 14), ContactsFiles.all(dir).single { it.info.uid == nikos.uid }.info.birthday)
        ContactsBirthdays.syncAll(calRoot, ContactsFiles.all(dir).map { it.info }, null)
        val files = cal.dir.listFiles()!!.filter { it.name.endsWith(".ics") }
        assertEquals(4, files.size) // three of Google's and Σοφία Άλλη's new one: no doubles
        val linked = ContactsBirthdays.linked(calRoot)
        assertEquals("Γενέθλια νίκου (ανηψιός)", linked[nikos.uid]!!.single().master!!.title) // its own title kept
        assertTrue(ContactsBirthdays.suggestions(calRoot, ContactsFiles.all(dir).map { it.info }, null).isEmpty())
    }
}
