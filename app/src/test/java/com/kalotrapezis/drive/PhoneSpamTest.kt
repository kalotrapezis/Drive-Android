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
        assertEquals("telemarketing", PhoneSpam.matches(list, "2105551234")?.note)
        assertEquals("telemarketing", PhoneSpam.matches(list, "00302105551234")?.note)
        assertEquals("a whole range", PhoneSpam.matches(list, "+30 698 001 2345")?.note)
        assertEquals("a whole range", PhoneSpam.matches(list, "6980099999")?.note)
        assertEquals("+442079460000", PhoneSpam.matches(list, "00442079460000")?.number)
        assertNull(PhoneSpam.matches(list, "6970000002"))
        assertNull(PhoneSpam.matches(PhoneSpam.parse("6*"), "6970000002")) // a range of one digit is too wide to mean anything
    }

    @Test fun `CallInsider is only for Greek numbers`() {
        assertEquals("https://www.callinsider.gr/gr/6900009322", PhoneSpam.lookUp("+30 690 000 9322"))
        assertNull(PhoneSpam.lookUp("+44 20 7946 0000"))
    }
}
