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

    @Test fun `unknown Greek numbers say where they are`() {
        assertEquals("Mobile", PhoneArea.of("+30 697 000 0002"))
        assertEquals("Thessaloniki", PhoneArea.of("2310 123456"))
        assertEquals("Athens", PhoneArea.of("0030 210 1234567"))
        assertEquals("Volos", PhoneArea.of("2421012345"))
        assertEquals("Landline", PhoneArea.of("2231012345"))
        assertEquals("Abroad", PhoneArea.of("+44 20 7946 0000"))
        assertEquals(null, PhoneArea.of("112"))
    }
}
