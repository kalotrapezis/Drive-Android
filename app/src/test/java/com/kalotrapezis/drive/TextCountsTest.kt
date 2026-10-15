package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class TextCountsTest {
    @Test fun countsAsWord() {
        val c = TextCounts.of("Ο Γιάννης's well-known cat.\nIt sat  ￼ twice")
        assertEquals(7, c.words) // Ο, Γιάννης's, well-known, cat, It, sat, twice
        assertEquals(34, c.chars)
        assertEquals(40, c.withSpaces)
    }
    @Test fun aTableCountsItsCells() {
        fun p(t: String) = DocPara(listOf(DocRun(t)))
        val table = DocTable("", "", listOf(DocRow("", listOf(DocCell("", listOf(p("one two"))), DocCell("", listOf(p("three")))))))
        assertEquals(3, TextCounts.of(DocPara(listOf(DocRun(OBJECT_MARK.toString())), table = table).countedText()).words)
    }
    @Test fun theLiveCountSaysTheSelectionsWordsOfAll() {
        assertEquals("1 word", TextCounts.label(1, null))
        val n = "%,d".format(1234) // the phone's own way of writing thousands
        assertEquals("$n words", TextCounts.label(1234, null))
        assertEquals("12 of $n words", TextCounts.label(1234, 12))
    }
}
