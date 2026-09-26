package com.kalotrapezis.drive

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.TextUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The read view drops markers; what it must never drop is the text itself. */
class NoteMarkdownTest {

    private fun plain(md: String) = renderMarkdown(md).text

    @Test
    fun `markers are removed but the words survive`() {
        assertEquals("bold and italic", plain("**bold** and *italic*"))
        assertEquals("gone", plain("~~gone~~"))
        assertEquals("code", plain("`code`"))
        assertEquals("Heading", plain("## Heading"))
        assertEquals("• item", plain("- item"))
        assertEquals("1. first", plain("1. first"))
        assertEquals("☐ todo\n☑ done", plain("- [ ] todo\n- [x] done"))
        assertEquals("│ quoted", plain("> quoted"))
    }

    @Test
    fun `an unclosed marker stays literal`() {
        assertEquals("2 * 3 = 6", plain("2 * 3 = 6"))
        assertEquals("**dangling", plain("**dangling"))
    }

    @Test
    fun `fences are dropped and greek is untouched`() {
        assertEquals("\nrm -rf\n", plain("```\nrm -rf\n```"))
        assertEquals("Ψώνια για το σπίτι", plain("Ψώνια **για** το σπίτι").replace("**", ""))
    }

    @Test
    fun `styles actually land on the right words`() {
        val rendered = renderMarkdown("a **b** c")
        val bold = rendered.spanStyles.single()
        assertEquals("b", rendered.text.substring(bold.start, bold.end))
        assertTrue(bold.item.fontWeight != null)
    }

    private fun preview(md: String) = markdownVisualTransformation().filter(AnnotatedString(md))

    @Test
    fun `edit preview hides paired syntax but leaves raw source untouched`() {
        val raw = "1. **what** is happening?"
        val source = AnnotatedString(raw)
        val transformed = markdownVisualTransformation().filter(source)
        assertEquals("1. what is happening?", transformed.text.text)
        assertEquals(raw, source.text)
        val bold = transformed.text.spanStyles.single { it.item.fontWeight != null }
        assertEquals("what", transformed.text.text.substring(bold.start, bold.end))
    }

    @Test
    fun `edit preview keeps unmatched markers and handles greek multiline and empty text`() {
        assertEquals("**ανολοκλήρωτο\nΤίτλος", preview("**ανολοκλήρωτο\n## Τίτλος").text.text)
        assertEquals("", preview("").text.text)
        assertEquals("bold, italic, gone", preview("**bold**, *italic*, ~~gone~~").text.text)
        val heading = preview("### Ελληνικός τίτλος")
        assertEquals("Ελληνικός τίτλος", heading.text.text)
        assertTrue(heading.text.spanStyles.any { it.item.fontWeight != null && it.item.fontSize != TextUnit.Unspecified })
    }

    @Test
    fun `edit preview offset mapping is monotonic and in bounds`() {
        val raw = "## Ελληνικά **bold**\n*italic*"
        val transformed = preview(raw)
        var previous = 0
        for (offset in 0..raw.length) {
            val mapped = transformed.offsetMapping.originalToTransformed(offset)
            assertTrue(mapped in previous..transformed.text.length)
            previous = mapped
        }
        previous = 0
        for (offset in 0..transformed.text.length) {
            val mapped = transformed.offsetMapping.transformedToOriginal(offset)
            assertTrue(mapped in previous..raw.length)
            previous = mapped
        }
    }
}
