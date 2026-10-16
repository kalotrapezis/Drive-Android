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

    @Test fun `a picture is laid as a letter a line may break at, nothing else changed`() {
        val s = androidx.compose.ui.text.buildAnnotatedString {
            append("Page 1 text.")
            pushStringAnnotation("androidx.compose.foundation.text.inlineContent", "picture")
            pushStyle(androidx.compose.ui.text.SpanStyle(letterSpacing = androidx.compose.ui.unit.TextUnit(3f, androidx.compose.ui.unit.TextUnitType.Sp))); append('\uFFFD'); pop()
            pop()
            append(" more")
        }
        val laid = WriterBridge.breakablePictures(s)
        assertEquals("Page 1 text.${OBJECT_MARK} more", laid.text)
        assertEquals(s.spanStyles, laid.spanStyles)
        assertEquals(s.getStringAnnotations(0, s.length), laid.getStringAnnotations(0, laid.length))
        val plain = androidx.compose.ui.text.AnnotatedString("no picture")
        assert(WriterBridge.breakablePictures(plain) === plain)
    }

    @Test fun `a picture goes where the mark was typed, and nothing else moves`() {
        val img = DocImage("k", "word/media/a.png", 10f, 10f)
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("ab"))), DocPara(listOf(DocRun("cd$PICTURE_SPOT", bold = true), DocRun("ef"))), DocPara(listOf(DocRun("gh")))))
        val put = WriterBridge.putPicture(doc, img)
        assertEquals(listOf("ab", "cd${OBJECT_MARK}ef", "gh"), put.paras.map { it.text })
        assertEquals(img, put.paras[1].runs[1].image)
        assertEquals(DocRun("ef"), put.paras[1].runs[2])
    }

    @Test fun `a picture first in a list item is as wide as itself, the number before it`() {
        // Gallery_test.docx: a WordArt first in "1. Page 1 text." was drawn twice its width, and "Page" broke after its "P".
        val img = DocImage("k", "word/media/a.png", 100f, 20f)
        fun spacing(list: DocList?) = WriterBridge.toEditor(OfficeDoc(listOf(DocPara(listOf(DocRun(OBJECT_MARK.toString(), image = img),
            DocRun("Page 1 text.")), list = list)), basePt = 11f)).blocks[0].spans
            .filterIsInstance<com.mohamedrejeb.richeditor.document.RichTextSpanMark.LetterSpacing>().single().size.value
        assertEquals(spacing(null), spacing(DocList(true, 0)) * 2, 0.001f)
        // …and its number is drawn in the text's look, not the picture's (a number ~100 sp wide pushed "age 1 text." down).
        fun marker(first: List<DocRun>) = com.mohamedrejeb.richeditor.model.RichTextState().apply {
            setRichTextDocument(WriterBridge.toEditor(OfficeDoc(listOf(DocPara(first + DocRun("Page 1 text."), list = DocList(true, 0))), basePt = 11f)))
            WriterBridge.plainMarkersIfPictureFirst(this)
        }.config.listMarkerStyleBehavior
        val plain = com.mohamedrejeb.richeditor.paragraph.type.ListMarkerStyleBehavior.AlwaysDefault
        assertEquals(plain, marker(listOf(DocRun(OBJECT_MARK.toString(), image = img))))
        assertEquals(com.mohamedrejeb.richeditor.paragraph.type.ListMarkerStyleBehavior.InheritFromText, marker(emptyList()))
    }

    @Test fun `letters typed over a chosen picture are the text's size, not the picture's`() {
        val img = DocImage("k", "word/media/a.png", 113f, 113f)
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Text."), DocRun(OBJECT_MARK.toString(), image = img)))), basePt = 11f)
        val pictures = HashMap<String, DocImage>()
        val state = com.mohamedrejeb.richeditor.model.RichTextState()
        state.setRichTextDocument(WriterBridge.toEditor(doc, pictures))
        val at = state.annotatedString.text.indexOf('\uFFFD')
        state.selection = androidx.compose.ui.text.TextRange(at, at + 1)
        state.replaceSelectedText("j")
        val back = WriterBridge.fromEditor(state.toRichTextDocument(), doc.basePt, pictures)
        assertEquals("Text.j", back[0].text)
        assertEquals(listOf<Float?>(null), back[0].runs.map { it.sizePt }.distinct())
        // Half undone: the picture's letter without its picture is not written as a letter.
        val bare = com.mohamedrejeb.richeditor.model.RichTextState()
        bare.setRichTextDocument(WriterBridge.toEditor(OfficeDoc(listOf(DocPara(listOf(DocRun("a\uFFFDb")))), basePt = 11f)))
        assertEquals("ab", WriterBridge.fromEditor(bare.toRichTextDocument(), 11f)[0].text)
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

    @Test fun `a font chosen for some letters is saved on those letters alone and read back`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("plain "), DocRun("bold here", bold = true)))))
            // "ain bo": the end of the first run and the start of the second, each keeping its own look.
            val set = WriterBridge.restyled(doc, listOf(Triple(0, 2, 8))) { OfficeFormats.withFont(it, kind, "Cambria") }
            val again = WriterBridge.restyled(set, listOf(Triple(0, 2, 8))) { OfficeFormats.withFont(it, kind, "Arial") } // replaced, not added
            val bytes = OfficeFormats.write(again, kind)
            val back = OfficeFormats.read(bytes, kind).paras.single()
            val styles = DocStyles.of(kind, bytes)
            val shown = back.runs.map { it.text to (it.font?.let { f -> styles.fontName(f) }) }
            assertEquals("$kind", listOf("pl" to null, "ain " to "Arial", "bo" to "Arial", "ld here" to null), shown)
            assertEquals("$kind", listOf(false, false, true, true), back.runs.map { it.bold })
        }
    }

    @Test fun `an empty list item is found after its number, not on it`() {
        val state = com.mohamedrejeb.richeditor.model.RichTextState()
        state.setRichTextDocument(WriterBridge.toEditor(OfficeDoc(listOf(DocPara(listOf(DocRun("one")), list = DocList(true, 0)),
            DocPara(emptyList(), list = DocList(true, 0)), DocPara(emptyList(), list = DocList(false, 0)), DocPara(listOf(DocRun("end")))))))
        val text = state.annotatedString.text
        val rich = state.toRichTextDocument()
        // "1. one 2.  •  end": each empty item's place is just before its separator, where the cursor stands.
        assertEquals(listOf(3, 10, 13, 14), WriterBridge.blockPlaces(text, rich).map { it.from })
        assertEquals(setOf(1), WriterBridge.paragraphsAt(text, rich, 10, 10))
        assertEquals(setOf(2), WriterBridge.paragraphsAt(text, rich, 13, 13))
        // Writer's own numbers have no space of their own ("1.one 2. end"), as the editor shows a file's lists.
        state.config.orderedListStyleType = FileNumbering(object : DocStyles() { override fun look(p: DocPara) = ParaLook() }) { (0..8).map { "decimal" to "%${it + 1}." } }
        state.setRichTextDocument(WriterBridge.toEditor(OfficeDoc(listOf(DocPara(listOf(DocRun("one")), list = DocList(true, 0)),
            DocPara(emptyList(), list = DocList(true, 0)), DocPara(listOf(DocRun("end")))))))
        val own = state.annotatedString.text
        assertEquals("1.one 2. end", own.trimEnd())
        assertEquals(listOf(2, 8, 9), WriterBridge.blockPlaces(own, state.toRichTextDocument()).map { it.from })
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

    @Test fun `change case keeps each letter's place, and Greek's final sigma`() {
        val el = java.util.Locale.forLanguageTag("el-GR")
        fun c(s: String, case: WriterBridge.Case, start: Boolean = true) = WriterBridge.inCase(s, case, start, el)
        assertEquals("ΤΟ ΣΠΙΤΙ ΜΑΣ, ΑΫΠΝΙΑ Ϊ", c("Το σπίτι μας, αϋπνία ΐ", WriterBridge.Case.Upper)) // Greek capitals: no accent, the dialytika stays
        assertEquals("CAFÉ", c("café", WriterBridge.Case.Upper))
        assertEquals("Άλλος Ένας", c("άλλος ένας", WriterBridge.Case.Words)) // a capital starting a word keeps its accent
        assertEquals("το σπίτι μας", c("ΤΟ ΣΠΊΤΙ ΜΑΣ", WriterBridge.Case.Lower)) // final sigma
        assertEquals("Το σπίτι μας. Και εδώ;", c("ΤΟ ΣΠΊΤΙ ΜΑΣ. ΚΑΙ ΕΔΏ;", WriterBridge.Case.Sentence))
        assertEquals("Hello World, It's Me", c("hello WORLD, it's me", WriterBridge.Case.Words))
        assertEquals("straße".length, c("straße", WriterBridge.Case.Upper).length) // ß stays rather than growing
        // In the editor: only the chosen letters change, their looks stay.
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("one "), DocRun("TWO", bold = true), DocRun(" three")))), basePt = 11f)
        val state = com.mohamedrejeb.richeditor.model.RichTextState()
        state.setRichTextDocument(WriterBridge.toEditor(doc))
        val text = state.annotatedString.text
        val changed = WriterBridge.changeCase(text, state.toRichTextDocument(), text.indexOf("TWO"), text.indexOf("TWO") + 3, WriterBridge.Case.Lower)
        assertEquals(listOf(DocRun("one "), DocRun("two", bold = true), DocRun(" three")), WriterBridge.fromEditor(changed, 11f).single().runs)
    }

    @Test fun `paragraph borders are kept and read back in both kinds`() {
        val thin = Border(0.5f, 0)
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val box = ParaBox(top = thin, left = thin, bottom = thin, right = thin)
            val paras = listOf(DocPara(listOf(DocRun("plain"))),
                DocPara(listOf(DocRun("boxed")), keep = OfficeFormats.withBorders(null, kind, box)),
                DocPara(listOf(DocRun("line under")), keep = OfficeFormats.withBorders(null, kind, ParaBox(bottom = Border(0.75f, 0xC00000)))),
                DocPara(listOf(DocRun("all")), keep = OfficeFormats.withBorders(null, kind, box.copy(between = thin))))
            val bytes = OfficeFormats.write(OfficeDoc(paras), kind)
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("borders.${kind.extension}").writeBytes(bytes) // for a look in LibreOffice
            val styles = DocStyles.of(kind, bytes)
            val looks = OfficeFormats.read(bytes, kind).paras.map { styles.look(it).box }
            assertEquals(kind.name, null, looks[0])
            assertEquals(kind.name, listOf(thin, thin, thin, thin), looks[1]!!.let { listOf(it.top, it.left, it.bottom, it.right) })
            assertEquals(kind.name, Border(0.75f, 0xC00000), looks[2]!!.bottom)
            assertEquals(kind.name, null, looks[2]!!.top)
            assert(looks[3]!!.between != null) { kind.name }
            // Taken off again.
            assertEquals(kind.name, null, OfficeFormats.withBorders(OfficeFormats.read(bytes, kind).paras[1].keep, kind, null)?.let { styles.look(DocPara(emptyList(), keep = it)).box })
            // Their lines as chosen (16 Oct: style and colour): double red, dotted, dashed — read back here, and by LibreOffice
            val lines = ParaBox(top = Border(1.5f, 0xC00000, "double"), bottom = Border(1.5f, 0x4472C4, "dotted"), left = Border(3f, 0, "dashed"))
            val styled = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("lines")), keep = OfficeFormats.withBorders(null, kind, lines)))), kind)
            fun check(b: ParaBox?, how: String) {
                assertEquals("$kind $how", listOf("double", "dotted", "dashed"), listOf(b!!.top!!.style, b.bottom!!.style, b.left!!.style))
                assertEquals("$kind $how", listOf(0xC00000, 0x4472C4), listOf(b.top!!.color, b.bottom!!.color))
                assertEquals("$kind $how", 1.5f, b.top!!.width, 0.2f)
            }
            check(DocStyles.of(kind, styled).look(OfficeFormats.read(styled, kind).paras.single()).box, "read back")
            if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
                val dir = java.nio.file.Files.createTempDirectory("lo").toFile()
                val f = java.io.File(dir, "b.${kind.extension}").apply { writeBytes(styled) }
                val other = if (kind == OfficeKind.Docx) OfficeKind.Odt else OfficeKind.Docx
                ProcessBuilder("soffice", "--headless", "--convert-to", other.extension, "--outdir", dir.path, f.path).redirectErrorStream(true).start().waitFor()
                val again = java.io.File(dir, "b.${other.extension}").readBytes()
                check(DocStyles.of(other, again).look(OfficeFormats.read(again, other).paras.first { it.text == "lines" }).box, "by LibreOffice")
            }
        }
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
    @Test fun `editor fonts follow run overrides and paragraph styles without changing editable text`() {
        val styles = object : DocStyles() {
            override fun look(p: DocPara) = ParaLook(font = "Times New Roman")
        }
        val doc = OfficeDoc(listOf(
            DocPara(listOf(DocRun("style "), DocRun("linked", font = "<w:rFonts w:ascii=\"Arial\"/>", link = "https://example.com"), DocRun("${LINE_BREAK}second")), list = DocList(false, 0)),
            DocPara(listOf(DocRun("plain")))))
        val state = com.mohamedrejeb.richeditor.model.RichTextState()
        state.setRichTextDocument(WriterBridge.toEditor(doc, styles = styles))
        val before = state.toRichTextDocument()
        val seen = mutableListOf<String?>()
        val shown = WriterBridge.displayText(state.annotatedString, before, doc, styles) { name ->
            seen += name
            if (name == "Arial") androidx.compose.ui.text.font.FontFamily.SansSerif else androidx.compose.ui.text.font.FontFamily.Serif
        }
        assertEquals(listOf("Times New Roman", "Arial", "Times New Roman", "Times New Roman"), seen)
        assertEquals(state.annotatedString.text, shown.text)
        val at = shown.text.indexOf("linked")
        assertEquals(true, shown.spanStyles.any { it.start == at && it.end == at + 6 && it.item.fontFamily == androidx.compose.ui.text.font.FontFamily.SansSerif })
        assertEquals(before, state.toRichTextDocument())
        assertEquals(doc.paras.map { it.copy(keep = null, runs = it.runs.map { r -> r.copy(font = null) }) }, WriterBridge.fromEditor(before, doc.basePt))
    }

    /** A drop cap shown in the editor (asked 16 Oct): its letter drawn as tall as the lines it drops, never saved as its size; LibreOffice still has the drop cap. */
    @Test fun `a drop cap is shown big in the editor and saved as it was`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val drop = DropCap(lines = 3)
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Once upon a time there was a drop cap.")), keep = OfficeFormats.withDropCap(null, kind, drop)), DocPara(listOf(DocRun("Next.")))), basePt = 11f)
            val state = com.mohamedrejeb.richeditor.model.RichTextState()
            val rich = WriterBridge.toEditor(doc)
            state.setRichTextDocument(rich)
            val shown = WriterBridge.displayText(state.annotatedString, rich, doc, DocStyles.Plain) { null }
            assertEquals(state.annotatedString.text, shown.text)
            val big = shown.spanStyles.filter { it.item.fontSize.isSp }.maxBy { it.item.fontSize.value }
            assertEquals(kind.name, true, big.item.fontSize.value > 40f) // about four lines of 11 pt
            assertEquals(kind.name, "O", shown.text.substring(big.start, big.end))
            assertEquals(kind.name, true, rich.blocks[0].lineHeight == androidx.compose.ui.unit.TextUnit.Unspecified)
            // saved from the editor: the letters' own size untouched, the drop cap kept, and LibreOffice reads it as one
            val back = WriterBridge.fromEditor(state.toRichTextDocument(), 11f)
            assertEquals(kind.name, listOf(null), back[0].runs.map { it.sizePt }.distinct())
            val bytes = OfficeFormats.write(doc.copy(paras = back.mapIndexed { i, p -> p.copy(keep = doc.paras[i].keep) }), kind)
            assertEquals(kind.name, drop, DropCap.of(OfficeFormats.read(bytes, kind).paras[0].keep))
            if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
                val dir = java.nio.file.Files.createTempDirectory("lo").toFile()
                val f = java.io.File(dir, "d.${kind.extension}").apply { writeBytes(bytes) }
                val out = java.io.File(dir, "out").apply { mkdirs() }
                // as an .odt: LibreOffice's own .docx export leaves drop caps out
                ProcessBuilder("soffice", "--headless", "--convert-to", "odt", "--outdir", out.path, f.path).redirectErrorStream(true).start().waitFor()
                val again = OfficeFormats.read(java.io.File(out, "d.odt").readBytes(), OfficeKind.Odt)
                assertEquals(kind.name, 3, DropCap.of(again.paras.first { it.text.endsWith("drop cap.") }.keep)?.lines)
            }
        }
    }

    @Test fun `heading and at least line spacing follow the same paragraph rules as body text`() {
        val styles = object : DocStyles() {
            override fun look(p: DocPara) = if (p.heading > 0) ParaLook(sizePt = 24f, lineExact = 36f)
                else ParaLook(lineExact = 24f, lineAtLeast = true)
        }
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Heading")), heading = 1),
            DocPara(listOf(DocRun("small", sizePt = 10f))), DocPara(listOf(DocRun("large", sizePt = 40f)))), basePt = 16f)
        val blocks = WriterBridge.toEditor(doc, styles = styles).blocks
        assertEquals(36f, blocks[0].lineHeight.value, 0.001f)
        assertEquals(24f, blocks[1].lineHeight.value, 0.001f)
        assertEquals(40f * WriterBridge.lineRatio, blocks[2].lineHeight.value, 0.001f)
    }

    @Test fun `layout reflow preserves typing undo and redo`() {
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Original")))))
        val state = com.mohamedrejeb.richeditor.model.RichTextState()
        state.setRichTextDocument(WriterBridge.toEditor(doc))
        state.history.clear()
        state.selection = androidx.compose.ui.text.TextRange(0)
        state.replaceTextRange(androidx.compose.ui.text.TextRange(0, 0), "Typed ")
        assertEquals(true, state.history.canUndo)
        val typed = doc.copy(paras = WriterBridge.fromEditor(state.toRichTextDocument(), doc.basePt))
        WriterBridge.reflow(state, typed, HashMap(), DocStyles.Plain)
        assertEquals("Typed Original", state.annotatedString.text)
        assertEquals(true, state.history.undo())
        assertEquals("Original", state.annotatedString.text)
        WriterBridge.reflow(state, doc, HashMap(), DocStyles.Plain)
        assertEquals(true, state.history.redo())
        assertEquals("Typed Original", state.annotatedString.text)
    }

    @Test fun `contextual spacing and hard line breaks do not add extra paragraph gaps`() {
        val styles = object : DocStyles() {
            override fun look(p: DocPara) = ParaLook(before = 6f, after = 12f, contextual = p.keep?.style == "Same")
        }
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("one${LINE_BREAK}two")), keep = DocKeep(style = "Same")),
            DocPara(emptyList(), keep = DocKeep(style = "Same")), DocPara(listOf(DocRun("new style")), keep = DocKeep(style = "Other"))))
        val state = com.mohamedrejeb.richeditor.model.RichTextState()
        state.setRichTextDocument(WriterBridge.toEditor(doc, styles = styles))
        val places = WriterBridge.blockPlaces(state.annotatedString.text, state.toRichTextDocument())
        assertEquals(listOf(places[2].to to 18f), WriterBridge.paragraphGaps(doc, styles, places))
    }

    @Test fun `a hard page break suppresses the ordinary boundary spacing`() {
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("page one"))), DocPara(listOf(DocRun("page two")), heading = 1)))
        val styles = object : DocStyles() {
            override fun look(p: DocPara) = ParaLook(before = 18f, after = 12f, pageBreakBefore = p.heading == 1)
        }
        val state = com.mohamedrejeb.richeditor.model.RichTextState()
        state.setRichTextDocument(WriterBridge.toEditor(doc, styles = styles))
        assertEquals(emptyList<Pair<Int, Float>>(), WriterBridge.paragraphGaps(doc, styles, WriterBridge.blockPlaces(state.annotatedString.text, state.toRichTextDocument())))
    }

}
