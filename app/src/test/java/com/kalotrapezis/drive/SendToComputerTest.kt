package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Send to phone, the phone's side (SyncServer PUT /send → FromComputer.receive), and Send to computer's names. */
class SendToComputerTest {
    private fun sha(b: ByteArray) = SyncRules.hex(MessageDigest.getInstance("SHA-256").digest(b))
    private fun receive(dir: File, bytes: ByteArray, name: String, sha: String = sha(bytes), modified: Long = 0) =
        FromComputer.receive(bytes.inputStream(), bytes.size.toLong(), dir, sha, name, modified)

    @Test fun aFileLandsVerifiedAndNeverOverAnother() {
        val dir = File(scratchDir("from-computer"), FromComputer.FOLDER)
        val pdf = "%PDF-1.4 boarding pass".toByteArray()
        val kept = receive(dir, pdf, "pass.pdf", modified = 1_700_000_000_000)
        assertEquals(File(dir, "pass.pdf"), kept)
        assertEquals(pdf.toList(), kept.readBytes().toList())
        assertEquals(1_700_000_000_000, kept.lastModified())
        assertEquals("sent again: the same file, no copy", kept, receive(dir, pdf, "pass.pdf"))
        val other = "%PDF-1.4 the return trip".toByteArray()
        assertEquals("pass (2).pdf", receive(dir, other, "pass.pdf").name)
        assertEquals(pdf.toList(), File(dir, "pass.pdf").readBytes().toList())
        assertEquals(listOf("pass (2).pdf", "pass.pdf"), dir.list()!!.sorted())
    }

    @Test fun badBytesOrNamesKeepNothing() {
        val dir = scratchDir("from-computer-bad")
        val bytes = "hello".toByteArray()
        assertThrows(IllegalArgumentException::class.java) { receive(dir, bytes, "a.txt", sha = sha("other".toByteArray())) }
        assertThrows(IllegalArgumentException::class.java) { receive(dir, bytes, "../a.txt") }
        assertThrows(IllegalArgumentException::class.java) { receive(dir, bytes, "..") }
        assertThrows(java.io.EOFException::class.java) { FromComputer.receive(bytes.inputStream(), 10, dir, sha(bytes), "short.txt", 0) }
        assertTrue("no file and no .part left", dir.list()!!.isEmpty())
    }

    @Test fun sharedNamesAreFileNames() {
        assertEquals("IMG_1.jpg", Outbox.safeName("IMG_1.jpg", "jpg"))
        assertEquals("x.pdf", Outbox.safeName("Download/x.pdf", null))
        assertEquals("evil.sh", Outbox.safeName("..\\evil.sh", null))
        val made = Outbox.safeName(null, "pdf")
        assertTrue(made, made.startsWith("Shared ") && made.endsWith(".pdf"))
        assertFalse(Outbox.safeName("..", null) == "..")
    }
}
