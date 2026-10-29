package com.kalotrapezis.drive

import android.telecom.CallAudioState.ROUTE_BLUETOOTH
import android.telecom.CallAudioState.ROUTE_EARPIECE
import android.telecom.CallAudioState.ROUTE_SPEAKER
import android.telecom.CallAudioState.ROUTE_WIRED_OR_EARPIECE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Where I answer the call, the sound goes there" (the user, 29 Oct): PhoneRoutes.where with names for Bluetooth devices. */
class PhoneRoutesTest {
    private val pc = "laptop-of-anna"
    private val buds = "Buds"
    private fun where(onPhone: Boolean, route: Int, active: String?, vararg devices: String) = PhoneRoutes.where(onPhone, route, active, devices.toList()) { it == pc }

    @Test fun `taken on the phone, the sound leaves the computer for the earpiece`() {
        assertEquals(ROUTE_WIRED_OR_EARPIECE to null, where(true, ROUTE_BLUETOOTH, pc, pc))
    }

    @Test fun `taken on the phone, earbuds or the car win over the earpiece`() {
        assertEquals(ROUTE_BLUETOOTH to buds, where(true, ROUTE_BLUETOOTH, pc, pc, buds))
        assertNull(where(true, ROUTE_BLUETOOTH, buds, pc, buds)) // Android chose the buds itself: left there
    }

    @Test fun `taken on the phone and already there, nothing moves`() {
        assertNull(where(true, ROUTE_EARPIECE, null, pc))
        assertNull(where(true, ROUTE_SPEAKER, null, pc))
    }

    @Test fun `taken on the computer, the sound goes to it`() {
        assertEquals(ROUTE_BLUETOOTH to pc, where(false, ROUTE_EARPIECE, null, pc))
        assertEquals(ROUTE_BLUETOOTH to pc, where(false, ROUTE_BLUETOOTH, buds, buds, pc))
        assertNull(where(false, ROUTE_BLUETOOTH, pc, pc))
    }

    @Test fun `no computer on the phone's Bluetooth, nothing to send it to`() {
        assertNull(where(false, ROUTE_EARPIECE, null, buds))
        assertNull(where(false, ROUTE_EARPIECE, null))
    }
}
