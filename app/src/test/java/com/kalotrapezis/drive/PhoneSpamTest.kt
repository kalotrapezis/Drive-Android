package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneSpamTest {
    private val list = PhoneSpam.parse("""
        # Spam and unwanted
        +30 210 555 1234  # telemarketing
        69800*   # a whole range
        +44 20 7946 0000
        not a number
    """.trimIndent())

    @Test fun `the file reads as numbers with notes`() {
        assertEquals(listOf("+302105551234", "69800*", "+442079460000"), list.map { it.number })
        assertEquals("telemarketing", list[0].note)
    }

    @Test fun `a call is caught however its number is written`() {
        assertEquals("telemarketing", PhoneSpam.matches(list, "2105551234", "GR")?.note)
        assertEquals("telemarketing", PhoneSpam.matches(list, "00302105551234", "GR")?.note)
        assertEquals("a whole range", PhoneSpam.matches(list, "+30 698 001 2345", "GR")?.note)
        assertEquals("a whole range", PhoneSpam.matches(list, "6980099999", "GR")?.note)
        assertEquals("+442079460000", PhoneSpam.matches(list, "00442079460000", "GR")?.number)
        assertNull(PhoneSpam.matches(list, "6970000002", "GR"))
        assertNull(PhoneSpam.matches(PhoneSpam.parse("6*"), "6970000002", "GR")) // a range of one digit is too wide to mean anything
    }

    @Test fun `a range is read in the phone's own country`() {
        val de = PhoneSpam.parse("0171*  # a German range, with its trunk 0\n+3069800*")
        assertEquals("0171*", PhoneSpam.matches(de, "+49 171 1234567", "DE")?.number)
        assertEquals("0171*", PhoneSpam.matches(de, "01711234567", "DE")?.number)
        assertEquals("+3069800*", PhoneSpam.matches(de, "0030 698 001 2345", "DE")?.number)
        assertNull(PhoneSpam.matches(de, "+30 171 1234567", "DE"))
        val it = PhoneSpam.parse("06*")
        assertEquals("06*", PhoneSpam.matches(it, "+39 06 1234 5678", "IT")?.number) // Italy's 0 stays
        assertNull(PhoneSpam.matches(it, "+39 612 345 6789", "IT"))
    }

    @Test fun `CallInsider is only for Greek numbers`() {
        assertEquals("https://www.callinsider.gr/gr/6900009322", PhoneSpam.lookUp("+30 690 000 9322", "GR"))
        assertNull(PhoneSpam.lookUp("+44 20 7946 0000", "GR"))
        assertEquals("https://www.callinsider.gr/gr/6900009322", PhoneSpam.lookUp("6900009322", "GR"))
        assertNull(PhoneSpam.lookUp("6900009322", "DE")) // in Germany that is not a Greek number
    }

    @Test fun `the keypad's codes`() {
        assertEquals("4636", PhoneCodes.secret("*#*#4636#*#*"))
        assertNull(PhoneCodes.secret("*#06#"))
        assertNull(PhoneCodes.secret("*100#")) // a USSD code: a call like any number
    }
}
