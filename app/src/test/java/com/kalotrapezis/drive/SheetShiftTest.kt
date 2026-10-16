package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * Rows and columns inserted and deleted on Excel-test.xlsx (excel-test.py) and LibreOffice's .ods of it: every
 * reference moved — the sheet's own formulas, other sheets', names, the chart, the comment, the rule, the merge, the
 * picture, the table — checked by reading the file back and by what LibreOffice shows.
 */
class SheetShiftTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name).readBytes()

    @Test fun `references move as Excel moves them`() {
        val ins = SheetShift("Rows", rows = true, at = 4, count = 2) // two rows before row 5
        assertEquals("B3", ins.ref("B3")); assertEquals("B7", ins.ref("B5")); assertEquals("B3:B14", ins.ref("B3:B12"))
        assertEquals("\$B\$7", ins.ref("\$B\$5")); assertEquals("A:A", ins.ref("A:A")); assertEquals("7:9", ins.ref("5:7")) // inserted at its first row: the whole range moves down
        assertEquals("SUM(B3:B14)+Other!B5", ins.formula("SUM(B3:B12)+Other!B5", "Rows"))
        assertEquals("SUM(Rows!B3:B14)+B5", ins.formula("SUM(Rows!B3:B12)+B5", "Refs"))
        assertEquals("\"B5\"&B7", ins.formula("\"B5\"&B5", "Rows")) // words in quotes stay
        val del = SheetShift("Rows", rows = false, at = 2, count = -1) // column C deleted
        assertNull(del.ref("C3")); assertEquals("C3", del.ref("D3")); assertEquals("B3:C3", del.ref("B3:D3")); assertNull(del.ref("C3:C9"))
        assertEquals("#REF!*2", del.formula("C3*2", "Rows"))
        assertEquals("[.C3]+[\$Refs.D1]", del.odfFormula("[.D3]+[\$Refs.D1]", "Rows"))
        assertEquals("Rows.B1:Rows.C9", del.odfAddresses("Rows.B1:Rows.D9"))
    }

    /** LibreOffice's CSV of each sheet ("as shown", Greek); null when LibreOffice is not here. */
    private fun shown(file: File): Map<String, List<List<String>>>? {
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() != 0) return null
        val out = Files.createTempDirectory("shown").toFile()
        val p = ProcessBuilder("soffice", "--headless", "--convert-to", "csv:Text - txt - csv (StarCalc):44,34,76,1,,0,false,true,true,false,false,-1", "--outdir", out.path, file.path)
            .redirectErrorStream(true).start()
        p.inputStream.readBytes(); p.waitFor()
        return out.listFiles()!!.associate { csv -> csv.nameWithoutExtension.substringAfterLast('-') to Sheets.csv(csv.readBytes()).sheet(0).let { s -> (0 until s.rows).map { r -> (0 until s.cols).map { c -> s[r, c]?.text.orEmpty() } + List(10) { "" } } } }
    }

    private fun shift(name: String, sheet: Int, rows: Boolean, at: Int, count: Int): File {
        val dir = Files.createTempDirectory("shift").toFile()
        val source = File(dir, name).apply { writeBytes(resource(name)) }
        val target = File(dir, "moved." + name.substringAfterLast('.'))
        val book = Sheets.read(source, ',', '.')
        val sh = SheetShift(book.tabs[sheet].name, rows, at, count)
        if (book.kind == "xlsx") sh.xlsx(source, target, sheet, book.parts, book.tabs.map { it.name }) else sh.ods(source, target, sheet, book.tabs.map { it.name })
        return target
    }

    private fun xml(file: File, part: String) = ZipFile(file).use { z -> String(z.getInputStream(z.getEntry(part)).readBytes()) }

    /** openpyxl keeps no values for formulas: they are worked out when the sheet is read, as Excel does. */
    @Test fun `formulas without values are worked out on reading`() {
        val book = Sheets.read(resource("Excel-test.xlsx"), "Excel-test.xlsx", ',', '.')
        val rows = book.sheet(0)
        assertEquals(20.0, rows[2, 2]?.value); assertEquals("no", rows[2, 3]?.text); assertEquals(167.5, rows[13, 1]?.value)
        assertEquals(16.0, book.sheet(1)[1, 1]?.value); assertEquals(167.5, book.sheet(1)[4, 1]?.value) // through the name "scores"
        assertEquals(12.0, book.sheet(2)[1, 3]?.value)
    }

    /** A score typed over 20 turns pink (the sheet's rule laid again); undone, it is white again. */
    @Test fun `rules follow an edit`() {
        val book = Sheets.read(resource("Excel-test.xlsx"), "Excel-test.xlsx", ',', '.')
        val rows = book.sheet(0); val history = SheetHistory()
        fun fill(r: Int, c: Int) = rows[r, c]?.style?.let { rows.look(it, book.styles) }?.fill
        assertNull(fill(2, 1)); assertEquals(0xFFFFC7CE.toInt(), fill(11, 1))
        history.apply(book, 0, mapOf(Sheet.key(2, 1) to SheetInput.cell("30", rows[2, 1], rows[2, 1]!!.style, "General", book.format)))
        assertEquals(0xFFFFC7CE.toInt(), fill(2, 1))
        assertEquals(60.0, rows[2, 2]?.value); assertEquals("yes", rows[2, 3]?.text)
        history.undo(book)
        assertNull(fill(2, 1))
    }

    @Test fun `two rows inserted in the middle of the scores`() {
        for (name in listOf("Excel-test.xlsx", "Excel-test.ods")) {
            val moved = shift(name, 0, rows = true, at = 4, count = 2)
            val book = Sheets.read(moved, ',', '.')
            val rows = book.sheet(0)
            assertEquals(name, "Γεωργίου", rows[6, 0]?.text) // row 5 is row 7 now
            assertNull(name, rows[4, 0]); assertNull(name, rows[5, 0])
            assertEquals(name, "=SUM(B3:B14)", rows[15, 1]?.formula)
            assertEquals(name, "Teo: Check this score", rows[6, 1]?.note)
            assertEquals(name, listOf(SheetRange(0, 0, 0, 3)), rows.merges)
            assertEquals(name, "=Rows!B9", book.sheet(1)[1, 1]?.formula)
            assertEquals(name, "=SUM(Rows!B3:B14)", book.sheet(1)[0, 1]?.formula)
            if (name.endsWith(".xlsx")) {
                assertEquals(19, rows.drawings.first { it.image != null }.row) // the picture under the table
                val sheetXml = xml(moved, "xl/worksheets/sheet1.xml")
                assertTrue(sheetXml, "sqref=\"B3:B14\"" in sheetXml) // the rule and the validation
                xml(moved, "xl/charts/chart1.xml").let { assertTrue(it, it.contains("'Rows'!\$B\$3:\$B\$14")) }
                xml(moved, "xl/workbook.xml").let { assertTrue(it, it.contains("Rows!\$B\$3:\$B\$14")) }
            }
            val lo = shown(moved) ?: continue
            assertEquals(name, "167,5", lo.getValue("Rows")[15][1]) // the total, the same
            assertEquals(name, "16", lo.getValue("Refs")[1][1]) // Ελένη's score followed her
            assertEquals(name, "14,5", lo.getValue("Refs")[3][1]) // the lookup
            assertEquals(name, "167,5", lo.getValue("Refs")[0][1])
        }
    }

    @Test fun `a column deleted, and one inserted inside a table`() {
        for (name in listOf("Excel-test.xlsx", "Excel-test.ods")) {
            val moved = shift(name, 0, rows = false, at = 2, count = -1) // "Double" goes
            val rows = Sheets.read(moved, ',', '.').sheet(0)
            assertEquals(name, "Pass", rows[1, 2]?.text)
            assertEquals(name, "=COUNTIF(C3:C12,\"yes\")", rows[13, 2]?.formula)
            if (name.endsWith(".xlsx")) assertEquals(4, rows.drawings.first { it.chart != null }.col) // the chart one column left
            shown(moved)?.let { lo -> assertEquals(name, "6", lo.getValue("Rows")[13][2]) }
        }
        val moved = shift("Excel-test.xlsx", 2, rows = false, at = 2, count = 1) // before Price, inside the table
        val t = Sheets.read(moved, ',', '.').sheet(2)
        assertEquals("=B2*D2", t[1, 4]?.formula)
        val table = xml(moved, "xl/tables/table1.xml")
        assertTrue(table, "ref=\"A1:E6\"" in table && "<tableColumns count=\"5\">" in table)
        shown(moved)?.let { lo -> assertEquals("12", lo.getValue("Table")[1][4]) } // Pens: 10 × 1,2
    }
}
