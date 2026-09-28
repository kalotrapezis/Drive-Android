package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TableEditTest {
    /** A table of [rows] × [cols] whose cells say where they are: "a1", "b1"… (column letter, row number). */
    private fun lettered(rows: Int, cols: Int): DocTable {
        val t = TableEdit.newTable(rows, cols, 400f)
        return t.copy(rows = t.rows.mapIndexed { r, row -> row.copy(cells = row.cells.mapIndexed { c, cell ->
            cell.copy(paras = listOf(DocPara(listOf(DocRun("${'a' + c}${r + 1}"))))) }) })
    }

    /** The table as its grid: each grid slot shows the text of the cell covering it (a merged cell fills its slots). */
    private fun grid(t: DocTable): List<String> {
        val owner = ArrayList<MutableList<String>>()
        t.rows.forEachIndexed { r, row ->
            val line = ArrayList<String>()
            row.cells.forEach { c ->
                val continuing = Regex("""<w:vMerge\b[^>]*/>""").find(c.props)?.value?.let { "restart" !in it } == true
                val text = if (continuing) owner[r - 1][line.size] else c.paras.joinToString("+") { it.text }.ifEmpty { "_" }
                repeat(TableEdit.span(c)) { line += text }
            }
            owner += line
        }
        return owner.map { it.joinToString(" ") }
    }

    /** Through the real writer and reader, as a saved file would come back. */
    private fun saved(t: DocTable) = OfficeFormats.read(OfficeFormats.write(OfficeDoc(listOf(tablePara(t))), OfficeKind.Docx), OfficeKind.Docx).paras.single().table!!

    @Test fun `rows and columns go in where the cell is`() {
        val t = lettered(2, 2)
        assertEquals(listOf("a1 b1", "_ _", "a2 b2"), grid(TableEdit.insertRow(t, 0, 1, below = true)!!))
        assertEquals(listOf("_ _", "a1 b1", "a2 b2"), grid(TableEdit.insertRow(t, 0, 0, below = false)!!))
        assertEquals(listOf("a1 _ b1", "a2 _ b2"), grid(TableEdit.insertColumn(t, 1, 0, right = true)!!))
        assertEquals(listOf("_ a1 b1", "_ a2 b2"), grid(TableEdit.insertColumn(t, 0, 0, right = false)!!))
        assertEquals(listOf("a2 b2"), grid(TableEdit.deleteRow(t, 0, 1)!!))
        assertEquals(listOf("b1", "b2"), grid(TableEdit.deleteColumn(t, 1, 0)!!))
        // The table keeps its width: 400 pt = 8000 twentieths, in three columns now.
        assertEquals(8000f, TableEdit.gridColumns(TableEdit.insertColumn(t, 0, 0, right = true)!!).sum(), 2f)
        assertNull(TableEdit.deleteRow(lettered(1, 2), 0, 0)) // the last row stays
    }

    @Test fun `merging takes the rectangle between two cells, grown around merged cells`() {
        val t = lettered(3, 3)
        val m = TableEdit.merge(t, 0 to 0, 1 to 1)!!
        assertEquals(listOf("a1+b1+a2+b2 a1+b1+a2+b2 c1", "a1+b1+a2+b2 a1+b1+a2+b2 c2", "a3 b3 c3"), grid(m))
        // From c1 down to b3: the merged block is in the way, so the rectangle grows to take it whole.
        val bigger = TableEdit.cellsBetween(m, 0 to 1, 2 to 1)
        assertEquals(true, (0 to 0) in bigger && (2 to 0) in bigger && (2 to 2) in bigger)
        val all = TableEdit.merge(m, 0 to 1, 2 to 1)!!
        assertEquals(1, grid(all).map { it.split(' ').toSet() }.flatten().toSet().size)
        assertEquals(grid(m), grid(saved(m)))
        // Properties in Word's order: width, span, merge.
        val props = m.rows[0].cells[0].props
        assertEquals(true, props.indexOf("tcW") < props.indexOf("gridSpan") && props.indexOf("gridSpan") < props.indexOf("vMerge"))
    }

    @Test fun `rows and columns through a merged cell make it bigger or smaller`() {
        val m = TableEdit.merge(lettered(3, 3), 0 to 0, 1 to 1)!! // a 2×2 block top left
        // A row in the middle of it: the block grows down across it; the cell beside it is new.
        assertEquals(listOf("M M c1", "M M _", "M M c2", "a3 b3 c3"), grid(TableEdit.insertRow(m, 0, 1, below = true)!!).map { it.replace("a1+b1+a2+b2", "M") })
        // A column through it: it grows across.
        assertEquals(listOf("M M M c1", "M M M c2", "a3 _ b3 c3"), grid(TableEdit.insertColumn(m, 2, 0, right = true)!!).map { it.replace("a1+b1+a2+b2", "M") })
        // Its first row away: its text stays, one row shorter.
        assertEquals(listOf("M M c2", "a3 b3 c3"), grid(TableEdit.deleteRow(m, 0, 0)!!).map { it.replace("a1+b1+a2+b2", "M") })
        // A column of it away: one column narrower.
        assertEquals(listOf("M c1", "M c2", "b3 c3"), grid(TableEdit.deleteColumn(m, 2, 0)!!).map { it.replace("a1+b1+a2+b2", "M") })
    }

    @Test fun `splitting a cell undoes a merge, or divides it evenly`() {
        val m = TableEdit.merge(lettered(3, 3), 0 to 0, 1 to 1)!!
        assertEquals(2 to 2, TableEdit.size(m, 0, 0))
        val back = TableEdit.split(m, 0, 0, 2, 2)!!
        assertEquals(listOf("a1+b1+a2+b2 _ c1", "_ _ c2", "a3 b3 c3"), grid(back))
        assertEquals(TableEdit.gridColumns(m), TableEdit.gridColumns(back))
        // An ordinary cell into three columns: the cells above and below it span the new lines, so nothing else moves.
        val three = TableEdit.split(lettered(2, 2), 0, 0, 3, 1)!!
        assertEquals(listOf("a1 _ _ b1", "a2 a2 a2 b2"), grid(three))
        assertEquals(8000f, TableEdit.gridColumns(three).sum(), 2f)
        assertEquals(listOf(4000f / 3, 4000f / 3, 4000f / 3, 4000f).map { it.toInt() }, TableEdit.gridColumns(three).map { it.toInt() })
        // …and into two rows: the cell beside it grows down.
        assertEquals(listOf("a1 b1", "_ b1", "a2 b2"), grid(TableEdit.split(lettered(2, 2), 0, 0, 1, 2)!!))
        assertEquals(grid(three), grid(saved(three)))
    }
}
