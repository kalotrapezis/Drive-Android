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
}
