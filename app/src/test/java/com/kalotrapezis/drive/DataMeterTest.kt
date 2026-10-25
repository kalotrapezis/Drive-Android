package com.kalotrapezis.drive

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataMeterTest {
    @Test fun `ping's loss and average`() {
        val out = "20 packets transmitted, 19 received, 5% packet loss, time 3805ms\nrtt min/avg/max/mdev = 1.204/3.412/9.950/1.101 ms\n"
        assertEquals(5 to 3.412, DataMeter.parsePing(out))
        assertNull(DataMeter.parsePing("ping: unknown host"))
    }

    @Test fun `the computer's link in words`() {
        assertEquals("Ethernet 1000 Mb/s", DataMeter.computerLink(JSONObject("""{"ethernet":{"iface":"enp3s0","speed":1000},"rssi":-50}""")))
        assertEquals("Wi-Fi -58 dBm", DataMeter.computerLink(JSONObject("""{"rssi":-58,"speed":433,"ssid":"Home"}""")))
    }

    @Test fun `advice, a quieter channel, the band and the signal here`() {
        val mine = WifiReading(-78, 72, 2437, "Home") // channel 6, weak
        val heard = listOf(Triple("Home", 2437, -78), Triple("A", 2437, -50), Triple("B", 2442, -55), Triple("C", 2452, -60), Triple("D", 2412, -90))
        val a = DataMeter.advice(mine, hotspot = false, heard)
        assertEquals("Channel 1 is quieter", a[0].title)
        assertTrue(a.any { it.title == "2.4 GHz" })
        assertTrue(a.any { it.title.startsWith("Weak here") && "router" in it.text })
        val h = DataMeter.advice(mine, hotspot = true, heard)
        assertTrue(h.first { it.title.startsWith("Weak here") }.text.contains("pocket")) // a phone's hotspot: advice for the phone
    }

    @Test fun `a rough distance grows as the signal falls`() {
        assertTrue(DataSignal.distance(-40, 2437) < DataSignal.distance(-70, 2437))
        assertEquals(1, DataSignal.distance(-40, 2437)) // ~1 m at −40 dBm on 2.4 GHz
    }
}
