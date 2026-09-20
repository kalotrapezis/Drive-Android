package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisProgressTest {
    @Test fun `progress stays within zero and one`() {
        assertEquals(0f, analysisProgressFraction(0, 0), 0f)
        assertEquals(0.5f, analysisProgressFraction(5, 10), 0f)
        assertEquals(1f, analysisProgressFraction(11, 10), 0f)
    }
}
