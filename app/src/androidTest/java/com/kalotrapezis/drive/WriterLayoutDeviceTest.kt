package com.kalotrapezis.drive

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mohamedrejeb.richeditor.model.RichTextState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WriterLayoutDeviceTest {
    @Test fun mobileTableDoesNotInheritTheSeparatorFontHeight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        OfficeFonts.assets = context.assets
        val width = WriterBridge.editorWidthSp
        val page = WriterBridge.pageWidthPt
        try {
            WriterBridge.pageWidthPt = null
            WriterBridge.editorWidthSp = 160f
            val table = DocTable("", "", listOf(DocRow("", listOf(DocCell("", listOf(DocPara(listOf(DocRun("tiny", sizePt = 8f)))))))))
            val doc = OfficeDoc(listOf(tablePara(table), DocPara(listOf(DocRun("After table")))), basePt = 12f)
            val state = RichTextState()
            val pictures = HashMap<String, DocImage>()
            state.setRichTextDocument(WriterBridge.toEditor(doc, pictures))
            val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
            val textStyle = TextStyle(fontSize = 16.sp, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.None))
            val before = measurer.measure(state.annotatedString, textStyle, constraints = Constraints(maxWidth = 160))
            WriterBridge.spaceParagraphs(state, before)
            val expected = WriterBridge.shownSize(pictures.values.single(), 16f / 12f).second
            val shown = WriterBridge.displayText(state.annotatedString, state.toRichTextDocument(), doc, DocStyles.Plain)
            val after = measurer.measure(shown, textStyle, constraints = Constraints(maxWidth = 160))
            assertEquals(state.annotatedString.text, shown.text)
            assertTrue("Expected a table smaller than a text line: $expected", expected < 16f)
            assertEquals("Table should occupy exactly its own height", expected, after.getLineBottom(0) - after.getLineTop(0), 1.1f)
            assertEquals("After table", WriterBridge.fromEditor(state.toRichTextDocument(), 12f, pictures)[1].text)
        } finally {
            WriterBridge.editorWidthSp = width
            WriterBridge.pageWidthPt = page
        }
    }

    @Test fun editorUsesTheViewersBundledFontsIncludingLinkedText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        OfficeFonts.assets = context.assets
        val styles = object : DocStyles() {
            override fun look(p: DocPara) = ParaLook(font = "Times New Roman")
        }
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Serif text "), DocRun("linked Arial", font = "<w:rFonts w:ascii=\"Arial\"/>", link = "https://example.com")))))
        val state = RichTextState()
        state.setRichTextDocument(WriterBridge.toEditor(doc, styles = styles))
        val shown = WriterBridge.displayText(state.annotatedString, state.toRichTextDocument(), doc, styles)
        assertNotNull(OfficeFonts.typeface("Times New Roman"))
        assertNotNull(OfficeFonts.typeface("Arial"))
        assertTrue(shown.spanStyles.any { it.item.fontFamily != null && it.start == 0 && it.end == 11 })
        assertTrue(shown.spanStyles.any { it.item.fontFamily != null && it.start == 11 && it.end == 23 })
        assertEquals("https://example.com", WriterBridge.fromEditor(state.toRichTextDocument(), doc.basePt).single().runs.last().link)
    }
    @Test fun multilineParagraphSpacingIsAddedOnceWithoutMovingTextOffsets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val styles = object : DocStyles() {
            override fun look(p: DocPara) = ParaLook(before = 6f, after = 12f)
        }
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("A long paragraph that wraps onto several lines in a narrow editor"))),
            DocPara(listOf(DocRun("Next paragraph")))), basePt = 16f)
        val state = RichTextState()
        state.setRichTextDocument(WriterBridge.toEditor(doc, styles = styles))
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
        val style = TextStyle(fontSize = 16.sp, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.None))
        val before = measurer.measure(state.annotatedString, style, constraints = Constraints(maxWidth = 150))
        WriterBridge.spaceParagraphs(state, before)
        val baseline = measurer.measure(state.annotatedString, style, constraints = Constraints(maxWidth = 150))
        val shown = WriterBridge.displayText(state.annotatedString, state.toRichTextDocument(), doc, styles) { null }
        val spaced = measurer.measure(shown, style, constraints = Constraints(maxWidth = 150))
        assertEquals(state.annotatedString.text, shown.text)
        val at = shown.text.indexOf("Next")
        val difference = spaced.getLineTop(spaced.getLineForOffset(at)) - baseline.getLineTop(baseline.getLineForOffset(at))
        assertEquals("Spacing belongs only at the paragraph boundary", 18f, difference, 1.1f)
        assertEquals(doc.paras, WriterBridge.fromEditor(state.toRichTextDocument(), doc.basePt))
    }

    @Test fun contentsTabAlignsItsNumberAndKeepsTheEditableTab() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val oldWidth = WriterBridge.pageWidthPt
        WriterBridge.pageWidthPt = 300f
        try {
            val styles = object : DocStyles() {
                override fun look(p: DocPara) = ParaLook(tabs = listOf(TabStop(250f, 'r', '.')))
            }
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Contents\t12")))), basePt = 16f)
            val state = RichTextState()
            state.setRichTextDocument(WriterBridge.toEditor(doc, styles = styles))
            val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
            val style = TextStyle(fontSize = 16.sp)
            val plain = WriterBridge.displayText(state.annotatedString, state.toRichTextDocument(), doc, styles) { null }
            val (shown, tabs) = editorTabs(plain, state.toRichTextDocument(), doc, styles, measurer, style, 300, 1f, 1f)
            val laid = measurer.measure(shown, style, constraints = Constraints(maxWidth = 300))
            assertEquals(plain.length, shown.length)
            assertEquals(listOf(EditorTab(8, '.')), tabs)
            assertEquals("Right-aligned number must end at the tab stop", 250f, laid.getHorizontalPosition(shown.length, true), 2f)
            assertEquals("Contents\t12", WriterBridge.fromEditor(state.toRichTextDocument(), doc.basePt).single().text)
            assertEquals(listOf(0), WriterBridge.paragraphStarts(shown.text, state.toRichTextDocument()))
        } finally { WriterBridge.pageWidthPt = oldWidth }
    }

    @Test fun contentsTabScalesWithEditorZoom() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val oldWidth = WriterBridge.pageWidthPt
        WriterBridge.pageWidthPt = 300f
        try {
            val styles = object : DocStyles() {
                override fun look(p: DocPara) = ParaLook(tabs = listOf(TabStop(250f, 'r', '.')))
            }
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Contents\t12")))), basePt = 16f)
            val state = RichTextState()
            state.setRichTextDocument(WriterBridge.toEditor(doc, styles = styles))
            val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
            val plain = WriterBridge.displayText(state.annotatedString, state.toRichTextDocument(), doc, styles) { null }
            for (zoom in listOf(0.7f, 2f)) {
                val style = TextStyle(fontSize = (16f * zoom).sp)
                val width = (300f * zoom).toInt()
                val (shown, _) = editorTabs(plain, state.toRichTextDocument(), doc, styles, measurer, style, width, 1f, zoom)
                val laid = measurer.measure(shown, style, constraints = Constraints(maxWidth = width))
                assertEquals("Tab stop must follow the editor's zoom", 250f * zoom, laid.getHorizontalPosition(shown.length, true), 2f)
            }
        } finally { WriterBridge.pageWidthPt = oldWidth }
    }

}
