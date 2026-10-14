package com.kalotrapezis.drive

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class WriterCarriedTest {
    private val bold = SpanStyle(fontWeight = FontWeight.Bold)
    private val look = AnnotatedString("Hello world", listOf(AnnotatedString.Range(bold, 0, 5)))

    @Test fun `a letter typed at a span's end takes its look, the rest moves on`() {
        val now = WriterBridge.carried(look, "Hellos world")!!
        assertEquals(listOf(0 to 6), now.spanStyles.map { it.start to it.end })
    }

    @Test fun `a letter typed after the span leaves it`() {
        val shifted = WriterBridge.carried(AnnotatedString("Hello world", listOf(AnnotatedString.Range(bold, 6, 11))), "Hello, world")!!
        assertEquals(listOf(7 to 12), shifted.spanStyles.map { it.start to it.end })
    }

    @Test fun `a letter removed inside the span shortens it, a span removed whole goes`() {
        assertEquals(listOf(0 to 4), WriterBridge.carried(look, "Helo world")!!.spanStyles.map { it.start to it.end })
        assertEquals(emptyList<Pair<Int, Int>>(), WriterBridge.carried(look, " world")!!.spanStyles.map { it.start to it.end })
    }

    @Test fun `the same text keeps the look as it is`() {
        assertEquals(look, WriterBridge.carried(look, "Hello world"))
    }
}
