package com.kalotrapezis.drive

import android.provider.CallLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PhoneLogTest {
    private val zone = ZoneId.of("Europe/Athens")
    private fun at(day: Int, h: Int) = ZonedDateTime.of(2026, 10, day, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun call(id: Long, n: String, day: Int, h: Int, type: Int = CallLog.Calls.INCOMING_TYPE) = LoggedCall(id, n, at(day, h), 30, type)

    @Test fun `calls in a row from one number on one day are one line`() {
        val g = PhoneLog.group(listOf(
            call(1, "+30 697 000 0002", 24, 15), call(2, "6970000002", 24, 12, CallLog.Calls.MISSED_TYPE), // the same number written two ways
            call(3, "2310000000", 24, 11),
            call(4, "6970000002", 24, 10), // the same person again, after another: a line of its own
            call(5, "6970000002", 23, 22), // yesterday: another line
        ), zone)
        assertEquals(listOf(listOf(1L, 2L), listOf(3L), listOf(4L), listOf(5L)), g.map { it.calls.map(LoggedCall::id) })
    }

    @Test fun `hidden numbers are not one person`() {
        assertEquals(2, PhoneLog.group(listOf(call(1, "", 24, 15), call(2, "", 24, 14)), zone).size)
    }

    @Test fun `the keys find Greek and Latin names`() {
        assertEquals("52742 727236768568", PhoneT9.digits("Μαρία Παπαδοπούλου"))
        assertTrue(PhoneT9.matches("527", "Μαρία Παπαδοπούλου", emptyList())) // ΜΑΡ (Μ is on 5)
        assertTrue(PhoneT9.matches("7272", "Μαρία Παπαδοπούλου", emptyList())) // the surname
        assertTrue(PhoneT9.matches("5377", "Jess Taylor", emptyList()))
        assertTrue(PhoneT9.matches("5274", "Μαρίνα", emptyList())) // ί as Ι
        assertFalse(PhoneT9.matches("628", "Μαρία", emptyList()))
        assertTrue(PhoneT9.matches("0000", "Μαρία", listOf("+30 697 000 0002"))) // inside a number
        assertFalse(PhoneT9.matches("69", "Άλλος", listOf("6970000002"))) // two digits are too few for numbers
        assertEquals("777", PhoneT9.digits("Σςσ")) // final sigma too
    }

    @Test fun `unknown Greek numbers say what they are`() {
        assertEquals(R.string.contacts_label_mobile, PhoneArea.of("+30 697 000 0002", "GR"))
        assertEquals(R.string.phone_area_landline, PhoneArea.of("2310 123456", "GR"))
        assertEquals(R.string.phone_area_landline, PhoneArea.of("0030 210 1234567", "GR"))
        assertEquals(R.string.phone_area_abroad, PhoneArea.of("+44 20 7946 0000", "GR"))
        assertEquals(null, PhoneArea.of("112", "GR"))
    }

    @Test fun `a number without + or 00 is the phone's own country's`() {
        // Germany and North Macedonia: a trunk 0 before the area code
        assertEquals(R.string.contacts_label_mobile, PhoneArea.of("0171 1234567", "DE"))
        assertEquals(R.string.phone_area_landline, PhoneArea.of("030 12345678", "DE"))
        assertEquals(R.string.phone_area_landline, PhoneArea.of("02 3123456", "MK"))
        assertEquals(R.string.contacts_label_mobile, PhoneArea.of("070 123456", "MK"))
        // Italy keeps its 0: it is part of the number, +39 06…
        assertEquals(R.string.phone_area_landline, PhoneArea.of("06 1234 5678", "IT"))
        assertEquals("+390612345678", PhoneCountry.international("06 1234 5678", "IT"))
        assertEquals("+4930123456", PhoneCountry.international("030 123456", "DE"))
        // abroad is another country than the phone's: a Greek number is abroad in Germany, a German one at home there
        assertEquals(R.string.phone_area_abroad, PhoneArea.of("+30 697 000 0002", "DE"))
        assertEquals(R.string.phone_area_abroad, PhoneArea.of("0030 697 000 0002", "DE"))
        assertEquals(R.string.contacts_label_mobile, PhoneArea.of("+49 171 1234567", "DE"))
        // the US's 011 for abroad; its numbers may be mobile or landline alike: only a place is said
        assertEquals(R.string.phone_area_abroad, PhoneArea.of("011 30 697 000 0002", "US"))
        assertEquals(0, PhoneArea.of("650 253 0000", "US"))
    }

    @Test fun `the maps' phone tables name the place, the longest prefix winning`() {
        val gr = PhoneArea.read("#prefix\ten\tel\n3021\tAthens\tΑθήνα\n30231\tThessaloniki\tΘεσσαλονίκη\n302313\tNorth Thessaloniki\t\n")
        val be = PhoneArea.read("#prefix\ten\tfr\tnl\n3210\tWavre\tWavre\tWaver\n")
        val all = listOf(gr, be)
        assertEquals("Θεσσαλονίκη", PhoneArea.place("2310 123456", "GR", "el", all))
        assertEquals("Θεσσαλονίκη", PhoneArea.place("+30 2310 123456", "GR", "el", all))
        assertEquals("North Thessaloniki", PhoneArea.place("0030 2313 012345", "GR", "el", all)) // longer, no Greek name: English
        assertEquals("Athens", PhoneArea.place("210 1234567", "GR", "de", all)) // a language the table lacks: English
        assertEquals("Waver", PhoneArea.place("+32 10 123456", "GR", "nl", all))
        assertEquals(null, PhoneArea.place("+44 20 7946 0000", "GR", "el", all))
        assertEquals(null, PhoneArea.place("2810 123456", "GR", "el", emptyList()))
        val mk = PhoneArea.read("#prefix\ten\n3892\tSkopje\n")
        val it = PhoneArea.read("#prefix\ten\tit\n3906\tRome\tRoma\n")
        assertEquals("Skopje", PhoneArea.place("02 3123456", "MK", "en", listOf(mk)))
        assertEquals("Roma", PhoneArea.place("06 1234 5678", "IT", "it", listOf(it)))
        assertEquals("Rome", PhoneArea.place("0039 06 1234 5678", "GR", "en", listOf(gr, it)))
        assertEquals("Athens", PhoneArea.place("+30 210 1234567", "IT", "en", listOf(gr, it)))
    }

    @Test fun `a phone table is read only beside its map`() {
        val root = kotlin.io.path.createTempDirectory("maps").toFile()
        try {
            java.io.File(root, "greece-phone.tsv").writeText("#prefix\ten\tel\n30231\tThessaloniki\tΘεσσαλονίκη\n")
            java.io.File(root, "cyprus-phone.tsv").writeText("#prefix\ten\tel\n35722\tNicosia\tΛευκωσία\n")
            java.io.File(root, "greece.pmtiles").writeText("")
            assertEquals(listOf("en", "el"), PhoneArea.tables(root).single().langs)
        } finally { root.deleteRecursively() }
    }

    @Test fun `the history for the computer is a month a file, oldest first`() {
        val maria = ContactInfo(uid = "m-1", formatted = "Μαρία", phones = listOf(VField("Mobile", "+30 697 000 0002")))
        val m = PhoneHistory.months(listOf(call(2, "6970000002", 24, 15, CallLog.Calls.MISSED_TYPE), call(1, "2310000000", 24, 9), LoggedCall(3, "6970000002", ZonedDateTime.of(2026, 9, 30, 23, 0, 0, 0, zone).toInstant().toEpochMilli(), 61, CallLog.Calls.OUTGOING_TYPE)),
            { n -> maria.takeIf { PhoneLog.same(n, "+30 697 000 0002") } }, { c -> if (c.id == 1L) "about the books" else null }, zone)
        assertEquals(setOf("2026-10", "2026-09"), m.keys)
        assertEquals(listOf(
            """{"at":"2026-10-24T09:00+03:00","number":"2310000000","dir":"in","secs":30,"note":"about the books"}""",
            """{"at":"2026-10-24T15:00+03:00","number":"6970000002","dir":"missed","secs":30,"contact":"m-1","name":"Μαρία"}""",
        ), m.getValue("2026-10").trimEnd().lines())
        assertEquals("""{"at":"2026-09-30T23:00+03:00","number":"6970000002","dir":"out","secs":61,"contact":"m-1","name":"Μαρία"}""" + "\n", m.getValue("2026-09"))
    }
}
