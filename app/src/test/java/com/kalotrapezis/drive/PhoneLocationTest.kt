package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What desktop Maps gets back when it asks the phone where it is (SyncServer /location; desktop sync.js phoneLocation). */
class PhoneLocationTest {
    private val gps = PhoneLocation.Fix(37.3483, 22.4605, 8f, 1_761_000_000_000, "gps")
    private val wifi = PhoneLocation.Fix(37.35, 22.46, 40f, 1_761_000_000_500, "network")

    @Test fun aFixIsSentAsTheComputerReadsIt() {
        val (status, body) = PhoneLocation.reply(granted = true, enabled = true, fix = gps)
        assertEquals(200, status)
        assertEquals(37.3483, body.getDouble("lat"), 0.0)
        assertEquals(22.4605, body.getDouble("lng"), 0.0)
        assertEquals(8.0, body.getDouble("accuracy"), 0.0)
        assertEquals(1_761_000_000_000, body.getLong("at"))
        assertEquals("gps", body.getString("provider"))
        assertTrue(!body.has("error"))
    }

    @Test fun noFixSaysWhyInWords() {
        val noPermission = PhoneLocation.reply(granted = false, enabled = true, fix = gps)
        assertEquals(403, noPermission.first)
        assertTrue(noPermission.second.getString("error").contains("Allow it on the phone"))
        assertEquals(503 to "Location is off on the phone.", PhoneLocation.reply(true, false, gps).let { it.first to it.second.getString("error") })
        val behind = PhoneLocation.reply(true, true, gps, onScreen = false)
        assertEquals(409, behind.first)
        assertTrue(behind.second.getString("error").contains("in the background"))
        val none = PhoneLocation.reply(true, true, null)
        assertEquals(504, none.first)
        assertTrue(!none.second.has("lat"))
    }

    @Test fun gpsBeforeTheNetwork() {
        assertEquals(gps, PhoneLocation.best(gps, wifi))
        assertEquals(wifi, PhoneLocation.best(null, wifi))
        assertEquals(null, PhoneLocation.best(null, null))
    }
}
