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
        assertEquals(listOf(2, 2), changed.paras.map { it.heading })
        assertEquals(listOf(keep, keep), changed.paras.map { it.keep })
        assertEquals(listOf(true, true), changed.paras.map { it.runs.single().bold })

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
        val table = WriterBridge.addColumn(WriterBridge.newTable(3, 3))
        assertEquals(listOf(4, 4, 4), table.rows.map { it.cells.size })
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

    @Test fun `a nested table stays inside its parent cell`() {
        // A 100 pt parent cell with 5 pt left/right margins lets its nested table end at the cell's right edge:
        // 90 pt text width + the 5 pt right margin. Its original 160 pt grid must be fitted to those 95 pt.
        assertEquals(listOf(47.5f, 47.5f), OfficePages.fittedTableColumns(listOf(80f, 80f), 95f, 0f))
        assertEquals(listOf(40f, 60f), OfficePages.fittedTableColumns(listOf(40f, 60f), 120f, 0f))
    }
}
