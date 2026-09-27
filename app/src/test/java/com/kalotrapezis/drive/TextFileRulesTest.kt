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

class CodeKeysTest {
    private fun at(text: String) = NoteFormat.Edit(text, text.length, text.length)

    @Test fun closeTagClosesTheLastOneStillOpen() {
        assertEquals("p", CodeKeys.openTag("<div class=\"a\"><p>hello", 22))
        assertEquals("<div><p>hi</p>", CodeKeys.press("</>", at("<div><p>hi")).text)
        assertEquals("<div><p>hi</p></div>", CodeKeys.press("</>", at("<div><p>hi</p>")).text)
        assertNull(CodeKeys.openTag("<br><img src=x/><input>", 23)) // void and self-closed tags need no closing
        assertEquals("<ul>", CodeKeys.press("</>", at("<ul>")).text.substring(0, 4))
    }

    @Test fun aBracketAroundASelectionWrapsIt() {
        val r = CodeKeys.press("(", NoteFormat.Edit("say hi now", 4, 6))
        assertEquals("say (hi) now", r.text)
        assertEquals(5 to 7, r.start to r.end)
        assertEquals("a    b", CodeKeys.press("Tab", NoteFormat.Edit("ab", 1, 1)).text)
    }
}
