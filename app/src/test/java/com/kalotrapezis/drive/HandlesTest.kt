package com.kalotrapezis.drive

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/** The knob above a chosen object: the finger's angle from its middle, snapping near each 45°. */
class HandlesTest {
    @Test fun `the knob's angle is clockwise from straight up and snaps`() {
        assertEquals(0f, angleOf(Offset(0f, -10f)), 0.01f)
        assertEquals(90f, angleOf(Offset(10f, 0f)), 0.01f)
        assertEquals(270f, angleOf(Offset(-10f, 0f)), 0.01f)
        assertEquals(45f, angleOf(Offset(10f, -9.5f)), 0.01f) // 46.5° snaps to 45°
        assertEquals(30f, angleOf(turnedBy(Offset(0f, -10f), 30f)), 0.01f)
    }
}
