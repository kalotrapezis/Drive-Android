package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSignalTest {
    @Test fun `channels and bands from the frequency`() {
        assertEquals(1, DataSignal.channel(2412)); assertEquals(6, DataSignal.channel(2437)); assertEquals(13, DataSignal.channel(2472))
        assertEquals(36, DataSignal.channel(5180)); assertEquals(149, DataSignal.channel(5745))
        assertEquals("5 GHz", DataSignal.band(5180)); assertEquals("2.4 GHz", DataSignal.band(2437)); assertEquals("6 GHz", DataSignal.band(5975))
    }

    @Test fun `the least crowded channel is the one with fewer and weaker neighbours`() {
        val heard = listOf(6 to -45, 6 to -60, 7 to -70, 1 to -88, 11 to -50, 11 to -55, 36 to -70)
        val rated = DataSignal.crowding(heard)
        assertEquals(1, rated.first { it.first <= 14 }.first)
        assertEquals(36, rated.first { it.first > 14 }.first)
    }

    @Test fun `rounds side by side, each spot's newest reading`() {
        val spots = listOf(
            DataSpot("2026-10-25T10:00:00+03:00", "desk", "hall", -50, 433, 5180),
            DataSpot("2026-10-25T10:02:00+03:00", "bedroom", "hall", -60, 200, 5180),
            DataSpot("2026-10-25T11:00:00+03:00", "desk", "tv shelf", -45, 433, 5180),
            DataSpot("2026-10-25T11:02:00+03:00", "bedroom", "tv shelf", -79, 54, 5180),
        )
        assertEquals(listOf("hall" to -55.0, "tv shelf" to -62.0), DataSignal.rounds(spots))
    }

    @Test fun `a spot's line reads back`() {
        val s = DataSpot("2026-10-25T10:00:00+03:00", "γραφείο", "hall", -50, 433, 5180, 310.5, 120.0, 4)
        assertEquals(s, DataSignal.parseSpot(DataSignal.spotLine(s)))
    }

    @Test fun `a drop is short and comes back, leaving is not one`() {
        assertTrue(DataSignal.isDrop(40)); assertFalse(DataSignal.isDrop(2)); assertFalse(DataSignal.isDrop(3600))
    }

    @Test fun `the beeps quicken as the signal grows`() {
        assertTrue(DataSignal.beepEvery(-40) < DataSignal.beepEvery(-70))
        assertEquals(DataSignal.beepEvery(-95), DataSignal.beepEvery(-120))
    }
}
