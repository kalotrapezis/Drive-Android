package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRulesTest {
    private val fp = "a".repeat(64)

    @Test fun theCopiesColoursAreTheComputersOwn() {
        assertEquals(listOf(125f, 220f, 173f, 268f, 149f), (3..7).map { SyncRules.copiesColor(it).first })
        assertEquals(358f, SyncRules.copiesColor(1).first)
        assertEquals(30, (3..32).map { SyncRules.copiesColor(it).first }.toSet().size)
    }

    @Test fun aMoveWindowIsSaidInItsLargestUnit() {
        assertEquals("1 month", SyncRules.span(30))
        assertEquals("1 year", SyncRules.span(365))
        assertEquals("2 weeks", SyncRules.span(14))
        assertEquals("10 days", SyncRules.span(10))
        assertEquals("Photos → pc · Move, keeping 3 months", SyncConnection("photos", "send", "nothing", 90).sentence("pc"))
    }

    @Test fun parsesTheDesktopPairingQr() {
        val qr = SyncRules.parseQr("""{"v":1,"name":"teo-pc","hosts":["192.168.1.146"],"port":43180,"fp":"${fp.uppercase()}","code":"abcdefghijklmnopqrstuv"}""")!!
        assertEquals(listOf("192.168.1.146"), qr.hosts)
        assertEquals(43180, qr.port)
        assertEquals(fp, qr.fingerprint)
    }

    @Test fun retriesALostNetworkThreeTimesAndNotAStop() {
        assertTrue(SyncRules.retriesAfterNetworkLoss(1, SyncException("Could not reach the computer")))
        assertTrue(SyncRules.retriesAfterNetworkLoss(2, SyncException("Could not reach the computer")))
        assertFalse("the fourth attempt gives up", SyncRules.retriesAfterNetworkLoss(3, SyncException("boom")))
        assertFalse("a finished sync is not retried", SyncRules.retriesAfterNetworkLoss(1, null))
        assertFalse("Stop means stop", SyncRules.retriesAfterNetworkLoss(1, kotlinx.coroutines.CancellationException()))
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

class SyncDiscoveryTest {
    @Test
    fun `a probe asks for one computer and a reply only says where to knock`() {
        val fingerprint = "a".repeat(64)
        val probe = String(SyncDiscovery.probe(fingerprint))
        assertTrue("the probe names the computer it is looking for", probe.contains(fingerprint))
        assertFalse("and nothing else about this phone", probe.contains("token"))

        val reply = """{"v":1,"name":"desk","port":43180}""".toByteArray()
        assertEquals(43180, SyncDiscovery.replyPort(reply, reply.size))
    }

    @Test
    fun `anything that is not an answer is ignored`() {
        for (junk in listOf("", "not json", """{"v":2,"port":43180}""", """{"v":1}""", """{"v":1,"port":0}""", """{"v":1,"port":70000}""")) {
            val bytes = junk.toByteArray()
            assertNull("«$junk» is not a reply", SyncDiscovery.replyPort(bytes, bytes.size))
        }
    }
}

class SyncConnectionTest {
    @Test
    fun `direction is read from this device's side, and says what it may do`() {
        val send = SyncConnection("photos", "send", "everything")
        val receive = SyncConnection("photos", "receive", "everything")
        val both = SyncConnection("files", "both", "everything")
        assertTrue(send.sends); assertFalse(send.receives)
        assertFalse(receive.sends); assertTrue(receive.receives)
        assertTrue(both.sends && both.receives)
    }

    @Test
    fun `the sentence names the other device and what happens to this one's copy`() {
        assertEquals("Photos → Desk · Copy", SyncConnection("photos", "send", "everything").sentence("Desk"))
        assertEquals("Photos ← Desk · Copy", SyncConnection("photos", "receive", "everything").sentence("Desk"))
        assertEquals("Files ⇄ Desk · Copy", SyncConnection("files", "both", "everything").sentence("Desk"))
        assertEquals("Files → Desk · Move, keeping 1 month", SyncConnection("files", "send", "nothing").sentence("Desk"))
    }

    @Test
    fun `a computer too old to answer is treated as two-way, which is what it always did`() {
        assertEquals(listOf("photos", "files"), SyncConnection.defaults.map { it.content })
        assertTrue(SyncConnection.defaults.all { it.sends && it.receives && it.keep == "everything" })
    }
}

class DriveNewFileTest {
    @Test
    fun `a synced file never replaces one that is already there`() {
        val root = java.io.File.createTempFile("drive-root", "").let { it.delete(); it.mkdirs(); it }
        try {
            val first = DriveRules.newFile(root, "Notes/letter.txt")
            assertEquals(java.io.File(root, "Notes/letter.txt").canonicalFile, first)
            first.parentFile!!.mkdirs()
            first.writeText("the one already here")

            val second = DriveRules.newFile(root, "Notes/letter.txt")
            assertEquals("letter (2).txt", second.name)
            second.writeText("the one that arrived")
            assertEquals("letter (3).txt", DriveRules.newFile(root, "Notes/letter.txt").name)
            assertEquals("the one already here", java.io.File(root, "Notes/letter.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a path that tries to leave Drive is refused`() {
        val root = java.io.File.createTempFile("drive-root", "").let { it.delete(); it.mkdirs(); it }
        try {
            for (bad in listOf("../escape.txt", "/etc/passwd", "Notes/../../escape.txt", "")) {
                assertThrows(IllegalArgumentException::class.java) { DriveRules.newFile(root, bad) }
            }
        } finally {
            root.deleteRecursively()
        }
    }
}

class IncomingPhotoFolderTest {
    @Test
    fun `the computer's own folder is kept when Android allows it there`() {
        assertEquals("DCIM/Camera", SyncRules.incomingFolder("DCIM/Camera", video = false))
        assertEquals("Pictures/Screenshots", SyncRules.incomingFolder("/Pictures/Screenshots/", video = false))
        assertEquals("Movies/Holiday", SyncRules.incomingFolder("Movies/Holiday", video = true))
    }

    @Test
    fun `anywhere Android would refuse becomes Tetra's own folder instead of a failure`() {
        assertEquals("Pictures/Tetra", SyncRules.incomingFolder("Photos/2024", video = false))
        assertEquals("Pictures/Tetra", SyncRules.incomingFolder("", video = false))
        assertEquals("Pictures/Tetra", SyncRules.incomingFolder("../escape", video = false))
        assertEquals("Movies/Tetra", SyncRules.incomingFolder("Home videos", video = true))
        assertEquals("Movies/Tetra", SyncRules.incomingFolder("Documents/clips", video = true))
    }
}

class SyncConnectionOffTest {
    @Test
    fun `off means neither way, and says so`() {
        val off = SyncConnection("photos", "off", "everything")
        assertFalse(off.sends)
        assertFalse(off.receives)
        assertEquals("Photos · not synced", off.sentence("Desk"))
    }
}
