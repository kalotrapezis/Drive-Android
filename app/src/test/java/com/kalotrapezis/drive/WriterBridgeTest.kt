package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class WriterBridgeTest {
    @Test fun `superscript and subscript keep their sizes through the editor`() {
        // The document's own size, a size set on purpose (even the normal one), both scripts.
        val paras = listOf(DocPara(listOf(DocRun("x"), DocRun("2", script = 1), DocRun(" H"), DocRun("2", script = -1, sizePt = 11f),
            DocRun("O"), DocRun("n", script = 1, sizePt = 8.5f), DocRun(" big", sizePt = 14f))))
        val doc = OfficeDoc(paras, basePt = 11f)
        assertEquals(paras, WriterBridge.fromEditor(WriterBridge.toEditor(doc), doc.basePt))
    }

    @Test fun `a line break inside a paragraph is a new line in the editor and one paragraph again in the file`() {
        val br = LINE_BREAK.toString()
        val paras = listOf(
            DocPara(listOf(DocRun("Line one"), DocRun("${br}Line two", bold = true), DocRun(" end"))),
            DocPara(listOf(DocRun("item${br}${br}after an empty line")), list = DocList(false, 0)),
            DocPara(listOf(DocRun("ends with a break$br"))),
            DocPara(listOf(DocRun("next paragraph"))),
        )
        val rich = WriterBridge.toEditor(OfficeDoc(paras, basePt = 11f))
        assertEquals(listOf(false, true, false, true, true, false, true, false), rich.blocks.map { it.isLineBreak })
        assertEquals(WriterBridge.comparable(paras), WriterBridge.comparable(WriterBridge.fromEditor(rich, 11f)))
        // A break whose look differs from the text after it is compared by where it stands, not its look.
        val odd = listOf(DocPara(listOf(DocRun("a"), DocRun(br, italic = true), DocRun("b"))))
        assertEquals(WriterBridge.comparable(odd), WriterBridge.comparable(WriterBridge.fromEditor(WriterBridge.toEditor(OfficeDoc(odd, basePt = 11f)), 11f)))
    }

    @Test fun `a picture goes where the mark was typed, and nothing else moves`() {
        val img = DocImage("k", "word/media/a.png", 10f, 10f)
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("ab"))), DocPara(listOf(DocRun("cd$PICTURE_SPOT", bold = true), DocRun("ef"))), DocPara(listOf(DocRun("gh")))))
        val put = WriterBridge.putPicture(doc, img)
        assertEquals(listOf("ab", "cd${OBJECT_MARK}ef", "gh"), put.paras.map { it.text })
        assertEquals(img, put.paras[1].runs[1].image)
        assertEquals(DocRun("ef"), put.paras[1].runs[2])
    }

    @Test fun `table cell edits and new rows keep the existing look`() {
        val keep = DocKeep(style = "CellText")
        val first = DocPara(listOf(DocRun("old", bold = true, font = "<w:rFonts w:ascii=\"Arial\"/>")), heading = 2, keep = keep)
        val cell = DocCell("<w:tcPr><w:vMerge w:val=\"continue\"/></w:tcPr>", listOf(first, DocPara(listOf(DocRun("second")))))
        val table = DocTable("<w:tblPr/>", "<w:tblGrid/>", listOf(DocRow("<w:trPr><w:tblHeader/></w:trPr>", listOf(cell))))

        val changed = WriterBridge.withCellText(cell, "new\nline")
        assertEquals(listOf("new", "line"), changed.paras.map { it.text })
        assertEquals(listOf(2, 0), changed.paras.map { it.heading }) // each paragraph keeps its own style
        assertEquals(listOf(keep, null), changed.paras.map { it.keep })
        assertEquals(listOf(true, true), changed.paras.map { it.runs.single().bold })

        // One word changed: the others keep their looks; typed text takes the look of what it replaced, or of the letter before.
        val mixed = DocCell("", listOf(DocPara(listOf(DocRun("Total: ", bold = true), DocRun("56.25"), DocRun(" EUR", italic = true)))))
        val price = WriterBridge.withCellText(mixed, "Total: 60.25 EUR").paras.single().runs
        assertEquals(listOf(DocRun("Total: ", bold = true), DocRun("60.25"), DocRun(" EUR", italic = true)), price)
        // A new line splits the paragraph (both halves keep its style); taking it out joins them again.
        val split = WriterBridge.withCellText(mixed, "Total:\n 56.25 EUR")
        assertEquals(listOf("Total:", " 56.25 EUR"), split.paras.map { it.text })
        assertEquals(listOf(DocRun(" ", bold = true), DocRun("56.25"), DocRun(" EUR", italic = true)), split.paras[1].runs)
        assertEquals(mixed.paras.single().runs, WriterBridge.withCellText(split, "Total: 56.25 EUR").paras.single().runs)
        assertEquals("", WriterBridge.withCellText(mixed, "").paras.single().text)
        assertEquals(DocRun("Total: 1", bold = true), WriterBridge.withCellText(mixed, "Total: 156.25 EUR").paras.single().runs.first())

        val back = OfficeFormats.read(OfficeFormats.write(OfficeDoc(listOf(tablePara(table.copy(rows = listOf(DocRow("", listOf(changed))))))), OfficeKind.Docx), OfficeKind.Docx).paras.single().table!!
        assertEquals(listOf("new", "line"), back.rows.first().cells.single().paras.map { it.text })
    }

    @Test fun `a nested table stays inside its parent cell`() {
        // A 100 pt parent cell with 5 pt left/right margins lets its nested table end at the cell's right edge:
        // 90 pt text width + the 5 pt right margin. Its original 160 pt grid must be fitted to those 95 pt.
        assertEquals(listOf(47.5f, 47.5f), OfficePages.fittedTableColumns(listOf(80f, 80f), 95f, 0f))
        assertEquals(listOf(40f, 60f), OfficePages.fittedTableColumns(listOf(40f, 60f), 120f, 0f))
    }

    @Test fun `the paragraphs a selection touches are found in the editor's text`() {
        val state = com.mohamedrejeb.richeditor.model.RichTextState()
        state.setRichTextDocument(WriterBridge.toEditor(OfficeDoc(listOf(
            DocPara(listOf(DocRun("abc"))), DocPara(listOf(DocRun("one")), list = DocList(false, 0)),
            DocPara(listOf(DocRun("two")), list = DocList(true, 0, 1)), DocPara(listOf(DocRun("x${LINE_BREAK}y"))), DocPara(emptyList()), DocPara(listOf(DocRun("end")))))))
        val text = state.annotatedString.text
        val rich = state.toRichTextDocument()
        fun at(word: String, to: String = word) = WriterBridge.paragraphsAt(text, rich, text.indexOf(word), text.indexOf(to) + to.length)
        assertEquals(setOf(0), at("abc"))
        assertEquals(setOf(1), at("one"))
        assertEquals(setOf(2), at("two"))
        assertEquals(setOf(3), at("y")) // the line after a break is still its paragraph
        assertEquals(setOf(5), at("end"))
        assertEquals(setOf(1, 2, 3), at("one", "x"))
        assertEquals(setOf(0), WriterBridge.paragraphsAt(text, rich, 1, 1))
    }

    @Test fun `clear formatting takes the letters' own looks off and leaves the style's`() {
        val title = DocKeep(style = "Title")
        val styles = object : DocStyles() {
            override fun look(p: DocPara) = if (p.keep?.style == "Title") ParaLook(sizePt = 28f, bold = true, italic = true, color = 0x2F5496) else ParaLook()
        }
        val img = DocImage("k", "word/media/a.png", 10f, 10f)
        val paras = listOf(
            DocPara(listOf(DocRun("Head "), DocRun("own", sizePt = 14f, color = 0xFF0000, underline = true)), keep = title),
            DocPara(listOf(DocRun("a "), DocRun("bold", bold = true, italic = true, highlight = 0xFFFF00, link = "https://x"),
                DocRun("$OBJECT_MARK", image = img), DocRun(" sup", script = 1), DocRun(" kept", bold = true))))
        val doc = OfficeDoc(paras, basePt = 11f)
        val state = com.mohamedrejeb.richeditor.model.RichTextState()
        state.setRichTextDocument(WriterBridge.toEditor(doc, HashMap(), styles))
        val text = state.annotatedString.text
        val cleared = WriterBridge.clearFormatting(text, state.toRichTextDocument(), text.indexOf("own"), text.indexOf(" kept"), 11f)
        // Shown again through the document (as the button does): the style's look where the letters' own was.
        val back = WriterBridge.fromEditor(WriterBridge.toEditor(doc.copy(paras = OfficeFormats.carryKeep(paras,
            WriterBridge.fromEditor(cleared, 11f, mapOf("k" to img)))), HashMap(), styles), 11f, mapOf("k" to img))
        assertEquals(listOf(DocRun("Head own")), back[0].runs)
        assertEquals(listOf("a ", "bold", "$OBJECT_MARK", " sup", " kept"), back[1].runs.map { it.text })
        assertEquals(DocRun("bold", link = "https://x"), back[1].runs[1]) // the link stays
        assertEquals(img, back[1].runs[2].image)
        assertEquals(0, back[1].runs[3].script)
        assertEquals(true, back[1].runs[4].bold) // outside the selection
        // A picture's paragraph (and those after it) are found too: its character differs on the screen.
        assertEquals(setOf(1), WriterBridge.paragraphsAt(text, state.toRichTextDocument(), text.indexOf("kept"), text.indexOf("kept")))
    }

    @Test fun `a page break is kept and read back in both kinds, and a new line in the editor splits the paragraph`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val paras = listOf(DocPara(listOf(DocRun("before"))), DocPara(listOf(DocRun("after")), keep = OfficeFormats.withPageBreak(null, kind, true)))
            val bytes = OfficeFormats.write(OfficeDoc(paras), kind)
            val back = OfficeFormats.read(bytes, kind)
            val styles = DocStyles.of(kind, bytes)
            assertEquals(kind.name, listOf(false, true), back.paras.map { styles.look(it).pageBreakBefore })
            // Taken out again.
            val off = back.paras[1].copy(keep = OfficeFormats.withPageBreak(back.paras[1].keep, kind, false))
            assertEquals(kind.name, false, styles.look(off).pageBreakBefore)
        }
        val state = com.mohamedrejeb.richeditor.model.RichTextState()
        state.setRichTextDocument(WriterBridge.toEditor(OfficeDoc(listOf(DocPara(listOf(DocRun("onetwo")))))))
        state.selection = androidx.compose.ui.text.TextRange(3)
        state.addTextAfterSelection("\n")
        assertEquals(listOf("one", "two"), state.toRichTextDocument().blocks.map { it.text })
        val text = state.annotatedString.text
        assertEquals(listOf(0, text.indexOf("two")), WriterBridge.paragraphStarts(text, state.toRichTextDocument()))
    }

    @Test fun `dates are offered in the phone's language and in English`() {
        val forms = dateForms(java.time.LocalDateTime.of(2026, 9, 29, 15, 45), java.util.Locale.forLanguageTag("el-GR"))
        assertEquals("29/09/2026", forms[0])
        assert("29 Σεπτεμβρίου 2026" in forms) { forms.toString() }
        assert("29 September 2026" in forms) { forms.toString() }
        assert("15:45" in forms)
    }

    @Test fun `a paragraph style's look shows in the editor and never comes back as the letters' own`() {
        val title = DocKeep(style = "Title")
        val styles = object : DocStyles() {
            override fun look(p: DocPara) = if (p.keep?.style == "Title") ParaLook(sizePt = 28f, bold = true, italic = true, color = 0x2F5496) else ParaLook()
        }
        val paras = listOf(
            DocPara(listOf(DocRun("A title "), DocRun("own", sizePt = 14f, color = 0xFF0000, italic = true, bold = true)), keep = title),
            DocPara(listOf(DocRun("Plain text"))))
        val state = com.mohamedrejeb.richeditor.model.RichTextState()
        state.setRichTextDocument(WriterBridge.toEditor(OfficeDoc(paras, basePt = 11f), styles = styles))
        val back = WriterBridge.fromEditor(state.toRichTextDocument(), 11f)
        assertEquals(paras.map { it.copy(keep = null) }, back)
        // The style's look is drawn: bigger, bold, slanted, coloured.
        val shown = state.toRichTextDocument().blocks[0].spans
        assertEquals(true, shown.any { it is com.mohamedrejeb.richeditor.document.RichTextSpanMark.FontSize && 0 in it.range })
        assertEquals(true, shown.any { it is com.mohamedrejeb.richeditor.document.RichTextSpanMark.Italic && 0 in it.range })
    }
}
