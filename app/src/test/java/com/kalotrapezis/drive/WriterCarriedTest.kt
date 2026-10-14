package com.kalotrapezis.drive

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class WriterCarriedTest {
    private val bold = SpanStyle(fontWeight = FontWeight.Bold)
    private val italic = SpanStyle(fontStyle = FontStyle.Italic)
    private val font = SpanStyle(fontFamily = FontFamily.Serif) // what Writer adds over the editor's text
    private val base = AnnotatedString("Hello world", listOf(AnnotatedString.Range(bold, 0, 5)))
    private val shown = AnnotatedString("Hello world", listOf(AnnotatedString.Range(bold, 0, 5), AnnotatedString.Range(font, 0, 11)))
    private fun spans(a: AnnotatedString) = a.spanStyles.map { Triple(it.item, it.start, it.end) }.toSet()

    @Test fun `a letter typed - the editor's spans as they are, Writer's font moved over it`() {
        val now = AnnotatedString("Hellos world", listOf(AnnotatedString.Range(bold, 0, 6)))
        assertEquals(setOf(Triple(bold, 0, 6), Triple(font, 0, 12)), spans(WriterBridge.carried(shown, base, now)!!))
    }

    @Test fun `italic put on with the text the same shows, Writer's font kept`() {
        val now = AnnotatedString("Hello world", listOf(AnnotatedString.Range(bold, 0, 5), AnnotatedString.Range(italic, 6, 11)))
        assertEquals(setOf(Triple(bold, 0, 5), Triple(italic, 6, 11), Triple(font, 0, 11)), spans(WriterBridge.carried(shown, base, now)!!))
    }

    @Test fun `letters removed shorten Writer's span`() {
        val now = AnnotatedString("Helo world", listOf(AnnotatedString.Range(bold, 0, 4)))
        assertEquals(setOf(Triple(bold, 0, 4), Triple(font, 0, 10)), spans(WriterBridge.carried(shown, base, now)!!))
    }
}
