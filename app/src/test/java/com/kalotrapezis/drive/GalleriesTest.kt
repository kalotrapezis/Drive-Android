package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test

/** The ribbon-place galleries' choices: how a list scheme reads, where a page number and a layout's words go. */
class GalleriesTest {
    @Test fun `a list scheme's levels read as Word shows them`() {
        val (plain, mixed, legal, outline) = LIST_SCHEMES
        assertEquals("2.", plain.marker(0, 2))
        assertEquals("a.", mixed.marker(1, 1)); assertEquals("iii.", mixed.marker(2, 3))
        assertEquals("1.1.2.", legal.marker(2, 2))
        assertEquals("II.", outline.marker(0, 2)); assertEquals("A.", outline.marker(1, 1))
    }

    @Test fun `page numbers and layouts fill a header and footer`() {
        val hf = NUMBER_SPOTS.first { it.label == R.string.words_spot_page_1_of_3 }.on(HeaderFooter(header = "Kept"))
        assertEquals(HeaderFooter.NumberPlace.Footer, hf.number); assertEquals("Page # of N", hf.numberFormat); assertEquals("Kept", hf.header)
        val book = HEADER_LAYOUTS.first { it.label == R.string.words_layout_book_outer }.make(HeaderFooter(), "Thesis")
        assertEquals("Thesis", book.header); assertEquals(true, book.oddEven); assertEquals("Thesis", book.evenHeader)
        assertEquals(HeaderFooter(), HEADER_LAYOUTS.last().make(book, "Thesis"))
    }
}
