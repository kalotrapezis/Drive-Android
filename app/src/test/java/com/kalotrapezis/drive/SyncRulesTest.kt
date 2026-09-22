package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncRulesTest {
    private val fp = "a".repeat(64)

    @Test fun parsesTheDesktopPairingQr() {
        val qr = SyncRules.parseQr("""{"v":1,"name":"teo-pc","hosts":["192.168.1.146"],"port":43180,"fp":"${fp.uppercase()}","code":"abcdefghijklmnopqrstuv"}""")!!
        assertEquals(listOf("192.168.1.146"), qr.hosts)
        assertEquals(43180, qr.port)
        assertEquals(fp, qr.fingerprint)
    }

    @Test fun rejectsAnythingElse() {
        assertNull(SyncRules.parseQr("https://example.com"))
        assertNull(SyncRules.parseQr("""{"v":2,"hosts":["h"],"port":1,"fp":"$fp","code":"abcdefghijklmnopqrstuv"}"""))
        assertNull(SyncRules.parseQr("""{"v":1,"hosts":["h"],"port":1,"fp":"short","code":"abcdefghijklmnopqrstuv"}"""))
        assertNull(SyncRules.parseQr("""{"v":1,"hosts":[],"port":1,"fp":"$fp","code":"abcdefghijklmnopqrstuv"}"""))
    }

    @Test fun blobPathKeepsPhoneFoldersAndEncodes() {
        assertEquals("/blob/$fp?path=DCIM%2FCamera&name=IMG+1+%CE%B1.jpg&modified=5", SyncRules.blobPath(fp, "DCIM/Camera/", "IMG 1 α.jpg", 5))
    }
}
