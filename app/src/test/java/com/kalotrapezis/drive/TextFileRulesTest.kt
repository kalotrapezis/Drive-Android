package com.kalotrapezis.drive

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextFileRulesTest {
    @Test fun aWindowsFileIsEditedWithNewlinesAndSavedWithItsOwnLineEndings() {
        val bytes = "one\r\ntwo\r\nΕλληνικά".toByteArray()
        val loaded = TextFileRules.read(bytes)
        assertEquals("one\ntwo\nΕλληνικά", loaded.text)
        assertArrayEquals(bytes, TextFileRules.write(loaded.text, loaded.crlf, loaded.bom))
    }

    @Test fun aByteOrderMarkStaysAndIsNotPartOfTheText() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hi\n".toByteArray()
        val loaded = TextFileRules.read(bytes)
        assertEquals("hi\n", loaded.text)
        assertArrayEquals(bytes, TextFileRules.write(loaded.text, loaded.crlf, loaded.bom))
    }

    @Test fun aNewFileGetsTxtOnlyWithoutAnExtension() {
        assertEquals("shopping.txt", TextFileRules.fileName(" shopping "))
        assertEquals("notes.md", TextFileRules.fileName("notes.md"))
        assertEquals(".env.txt", TextFileRules.fileName(".env"))
        assertNull(TextFileRules.fileName("a/b"))
        assertNull(TextFileRules.fileName("  "))
    }
}
