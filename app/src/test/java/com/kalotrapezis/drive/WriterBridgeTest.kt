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

        val added = WriterBridge.addRow(table).rows.last()
        assertEquals(1, added.cells.size)
        assertEquals("", added.cells.single().paras.single().text)
        assertEquals(keep, added.cells.single().paras.single().keep)
        assertEquals(true, added.cells.single().paras.single().runs.single().bold)
        assertEquals(false, "tblHeader" in added.props)
        assertEquals(false, "vMerge" in added.cells.single().props)

        val edited = table.copy(rows = listOf(table.rows.single().copy(cells = listOf(changed))) + added)
        val back = OfficeFormats.read(OfficeFormats.write(OfficeDoc(listOf(tablePara(edited))), OfficeKind.Docx), OfficeKind.Docx).paras.single().table!!
        assertEquals(listOf("new", "line"), back.rows.first().cells.single().paras.map { it.text })
        assertEquals(2, back.rows.size)
        assertEquals("", back.rows.last().cells.single().paras.single().text)
    }

    @Test fun `a simple table can add columns and merge a rectangle`() {
        val table = WriterBridge.addColumn(WriterBridge.newTable(3, 3, 450f))
        assertEquals(listOf(4, 4, 4), table.rows.map { it.cells.size })
        // The table keeps its width: four columns of 2250 twentieths of a point, and each cell says so too.
        assertEquals(listOf(2250f, 2250f, 2250f, 2250f), WriterBridge.gridColumns(table))
        assertEquals(true, table.rows.all { r -> r.cells.all { "<w:tcW w:w=\"2250\" w:type=\"dxa\"/>" in it.props } })
        val filled = table.copy(rows = table.rows.mapIndexed { r, row -> row.copy(cells = row.cells.mapIndexed { c, cell -> WriterBridge.withCellText(cell, "$r,$c") }) })
        val merged = WriterBridge.merge(filled, 0, 1, 2, 2)!!
        assertEquals(listOf(3, 3, 4), merged.rows.map { it.cells.size })
        assertEquals(true, "gridSpan" in merged.rows[0].cells[1].props)
        assertEquals(true, "restart" in merged.rows[0].cells[1].props)
        assertEquals(true, "vMerge" in merged.rows[1].cells[1].props)
        assertEquals(listOf("0,1", "0,2", "1,1", "1,2"), merged.rows[0].cells[1].paras.map { it.text })

        val back = OfficeFormats.read(OfficeFormats.write(OfficeDoc(listOf(tablePara(merged))), OfficeKind.Docx), OfficeKind.Docx).paras.single().table!!
        assertEquals(listOf(3, 3, 4), back.rows.map { it.cells.size })
        assertEquals(listOf("0,1", "0,2", "1,1", "1,2"), back.rows[0].cells[1].paras.map { it.text })
    }

    @Test fun `merging and columns follow the grid, not the count of cells`() {
        val table = WriterBridge.newTable(3, 4, 400f)
        // Row 0: its first two columns merged, so its cells are 3; the rectangle from grid column 2 is still columns 2–3.
        val wide = WriterBridge.merge(table, 0, 0, 1, 2)!!
        assertEquals(listOf(3, 4, 4), wide.rows.map { it.cells.size })
        val block = WriterBridge.merge(wide, 0, 2, 2, 2)!!
        assertEquals(listOf(2, 3, 4), block.rows.map { it.cells.size })
        assertEquals(2, WriterBridge.gridStart(block.rows[0], 1))
        assertEquals(2, WriterBridge.span(block.rows[1].cells[2]))
        // Cutting through the merged cell is refused rather than guessed at.
        assertEquals(null, WriterBridge.merge(block, 0, 1, 1, 2))
        // Properties in Word's order: width, span, merge, then the rest.
        val props = block.rows[0].cells[1].props
        assertEquals(true, props.indexOf("tcW") < props.indexOf("gridSpan") && props.indexOf("gridSpan") < props.indexOf("vMerge"))
        // The last column away: the spanning cell narrows, the rest widen to keep the width.
        val fewer = WriterBridge.deleteColumn(block)!!
        assertEquals(1, WriterBridge.span(fewer.rows[0].cells[1]))
        assertEquals(listOf(3, 3, 3), fewer.rows.map { r -> r.cells.sumOf { WriterBridge.span(it) } })
        assertEquals(8000f, WriterBridge.gridColumns(fewer).sum(), 3f)
        val back = OfficeFormats.read(OfficeFormats.write(OfficeDoc(listOf(tablePara(block))), OfficeKind.Docx), OfficeKind.Docx).paras.single().table!!
        assertEquals(block.rows.map { it.cells.size }, back.rows.map { it.cells.size })
    }

    @Test fun `a nested table stays inside its parent cell`() {
        // A 100 pt parent cell with 5 pt left/right margins lets its nested table end at the cell's right edge:
        // 90 pt text width + the 5 pt right margin. Its original 160 pt grid must be fitted to those 95 pt.
        assertEquals(listOf(47.5f, 47.5f), OfficePages.fittedTableColumns(listOf(80f, 80f), 95f, 0f))
        assertEquals(listOf(40f, 60f), OfficePages.fittedTableColumns(listOf(40f, 60f), 120f, 0f))
    }
}
