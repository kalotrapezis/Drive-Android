package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeOfDayRulesTest {
    @Test fun `time tags stay explicitly tentative`() {
        assertEquals("Likely night", likelyTimeOfDay(1, 120))
        assertEquals("Likely day", likelyTimeOfDay(13 * 60L * 60L * 1000L, 100))
        assertEquals(null, likelyTimeOfDay(13 * 60L * 60L * 1000L, 30))
    }
}
