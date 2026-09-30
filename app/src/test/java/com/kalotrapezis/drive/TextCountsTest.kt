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
}
