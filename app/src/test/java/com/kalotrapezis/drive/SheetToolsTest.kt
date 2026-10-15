package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The viewer's search, filter, sort and pinned columns, on a small made-up table. */
class SheetToolsTest {
    /** A title, a blank row, then a table: Name, Grade, Town. */
    private val sheet = run {
        val rows = listOf(
            listOf("Teachers 2026"), listOf(),
            listOf("Name", "Grade", "Town"),
            listOf("Παπαδόπουλος", 12.5, "Αθήνα"),
            listOf("Ζαχαρίου", 9.0, "Πάτρα"),
            listOf("Αλεξίου", 15.25, "Αθήνα"),
            listOf("Smith", null, "London"),
        )
        val cells = CellMap()
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, v ->
                when (v) {
                    is String -> cells[Sheet.key(r, c)] = SheetCell(v, SheetKind.Text)
                    is Double -> cells[Sheet.key(r, c)] = SheetCell(SheetFormat(',', '.').format(v, "#,##0.00").text, SheetKind.Number, v)
                }
            }
        }
        Sheet("T", cells, rows.size, 3, emptyMap(), emptyMap(), 64f, 20f, emptySet(), emptySet(), emptyList(), 0, 0)
    }

    /** Search every sheet of the user's 46 tables, timed (TETRA_SWEEP=1). */
    @Test fun `search in all of a big workbook`() {
        org.junit.Assume.assumeTrue(System.getenv("TETRA_SWEEP") == "1")
        val file = java.io.File(System.getProperty("user.home"), "Tetra/Files").walkTopDown().firstOrNull { it.name.startsWith("Εκκαθαρισμένοι") && it.extension == "xlsx" } ?: return
        val book = Sheets.read(file)
        for (q in listOf("παπαδοπουλος", "17351", "Αγγελικη Παπα")) {
            var read = 0L; var search = 0L; var n = 0
            for (i in book.tabs.indices) {
                val t0 = System.nanoTime(); val s = book.sheet(i); val t1 = System.nanoTime()
                n += SheetSearch.find(s, q).size; val t2 = System.nanoTime()
                read += t1 - t0; search += t2 - t1
            }
            println("All sheets, \"$q\": $n found; reading ${read / 1_000_000} ms, searching ${search / 1_000_000} ms")
            // the quick scan finds the same cells
            val t0 = System.nanoTime()
            var same = 0; var scanned = 0
            for (i in book.tabs.indices) {
                val quick = book.scan!!.invoke(i, q).map { it.key }.toSet()
                val full = SheetSearch.find(book.sheet(i), q).toSet()
                assertEquals("$q, sheet ${i + 1}", full, quick)
                scanned += quick.size; same++
            }
            println("  scanned the same in $same sheets, $scanned found")
        }
        val t0 = System.nanoTime()
        for (i in book.tabs.indices) book.scan!!.invoke(i, "παπαδοπουλος")
        println("All sheets scanned in ${(System.nanoTime() - t0) / 1_000_000} ms")
    }

    @Test fun `the header is the full row under the title`() = assertEquals(2, SheetLayout.header(sheet))

    @Test fun `search forgives accents, case and a typo`() {
        fun found(q: String) = SheetSearch.find(sheet, q).map { sheet[Sheet.row(it), Sheet.col(it)]!!.text }
        assertEquals(listOf("Παπαδόπουλος"), found("παπαδοπουλος"))
        assertEquals(listOf("Παπαδόπουλος"), found("Παπαδοπουλως")) // one letter wrong
        assertEquals(listOf("Παπαδόπουλος"), found("παπαδοπ")) // the start of the word
        assertEquals(listOf("Ζαχαρίου"), found("Ζαχαριού"))
        assertEquals(listOf("Αθήνα", "Αθήνα"), found("αθηνα"))
        assertEquals(listOf("12,50"), found("12.5")) // a number by its digits
        assertEquals(emptyList<String>(), found("12.4")) // and exactly: no typo forgiven in a number
        assertEquals("ελενη αννα-μαρια", SheetSearch.norm("ΕΛΈΝΗ Άννα-Μαρία"))
        assertEquals("cafe creme", SheetSearch.norm("Café Crème"))
        assertEquals(emptyList<String>(), found("Λονδίνο"))
        assertTrue(SheetSearch.matches(SheetSearch.norm("Smith"), SheetSearch.words("smiht"))) // swapped letters
        assertFalse(SheetSearch.matches(SheetSearch.norm("Ali"), SheetSearch.words("Ale"))) // short words must be exact
        assertEquals("οδοσ", SheetSearch.norm("Οδός"))
    }

    @Test fun `filter keeps the rows with the chosen values, the title and header stay`() {
        val l = SheetLayout.of(sheet, filters = mapOf(2 to setOf("Αθήνα")))
        assertEquals(listOf(0, 1, 2, 3, 5), l.rows.take(5))
        assertEquals(-1, l.row(4)); assertEquals(-1, l.row(6))
        assertEquals(7, l.rows[5]) // then the empty rows after the table
    }

    @Test fun `filter values are listed with their counts`() {
        assertEquals(listOf("Αθήνα" to 2, "London" to 1, "Πάτρα" to 1), SheetLayout.values(sheet, 2).sortedBy { it.first }.let { v -> v.filter { it.first.isNotEmpty() } }.sortedWith(compareBy({ it.first != "Αθήνα" }, { it.first })))
        assertEquals(listOf("9,00" to 1, "12,50" to 1, "15,25" to 1, "" to 1), SheetLayout.values(sheet, 1))
    }

    @Test fun `sort numbers by value, empty last either way`() {
        assertEquals(listOf(4, 3, 5, 6), SheetLayout.of(sheet, sort = SheetSort(1, true)).rows.slice(3..6))
        assertEquals(listOf(5, 3, 4, 6), SheetLayout.of(sheet, sort = SheetSort(1, false)).rows.slice(3..6))
    }

    @Test fun `the status bar's sum, average and count`() {
        val all = SheetLayout.of(sheet)
        val grades = SheetStats.of(sheet, all, SheetRange(2, 1, 6, 1)) // the header, three numbers, an empty cell
        assertEquals(4, grades.count); assertEquals(3, grades.numbers)
        assertEquals(36.75, grades.sum, 1e-9); assertEquals(12.25, grades.average, 1e-9); assertEquals(9.0, grades.min, 0.0); assertEquals(15.25, grades.max, 0.0)
        // Rows a filter hides are not counted (Excel's status bar counts what it shows).
        val athens = SheetLayout.of(sheet, filters = mapOf(2 to setOf("Αθήνα")))
        assertEquals(27.75, SheetStats.of(sheet, athens, SheetRange(3, 1, 4, 1)).sum, 1e-9)
    }

    @Test fun `chosen cells copied as tab-separated text`() {
        assertEquals("Name\tGrade\nΠαπαδόπουλος\t12,50\nΖαχαρίου\t9,00", sheetText(sheet, SheetLayout.of(sheet), SheetRange(2, 0, 4, 1)))
        // as drawn: a filter's rows only
        assertEquals("Παπαδόπουλος\nΑλεξίου", sheetText(sheet, SheetLayout.of(sheet, filters = mapOf(2 to setOf("Αθήνα"))), SheetRange(3, 0, 4, 0)))
    }

    /** 200 rows × 12 columns of 64 px: Excel's cuts at 100 % portrait A4, fitted to one page wide, and a print area. */
    @Test fun `pages as Excel prints them`() {
        val cells = CellMap().apply { for (r in 0 until 200) for (c in 0 until 12) set(Sheet.key(r, c), SheetCell("x", SheetKind.Text)) }
        val big = Sheet("P", cells, 200, 12, emptyMap(), emptyMap(), 64f, 20f, emptySet(), emptySet(), emptyList(), 0, 0)
        val l = SheetLayout.of(big)
        // 100 %: a page is (595 − 2×0.7 in) = 494 pt wide = 658 px: 10 columns; (842 − 2×0.75 in) = 734 pt = 978 px: 48 rows.
        val p = sheetPages(big, l)
        assertEquals(0..9, p.pages[0].cols); assertEquals(0..47, p.pages[0].rows)
        assertEquals(5 * 2, p.pages.size) // 5 bands of rows (200 / 48), 2 of columns
        assertEquals(10..11, p.pages[5].cols) // down, then over
        // fitted to 1 page wide: every column on each page
        val fit = sheetPages(big, l, SheetPrint(fitWidth = 1, fitHeight = 0))
        assertEquals(0..11, fit.pages[0].cols); assertEquals(494f / (12 * 64), fit.k, 0.01f)
        // a print area and its title rows (rows 1–2 on every page)
        val area = sheetPages(big, l, SheetPrint(area = SheetRange(0, 0, 99, 3), titles = 0..1))
        assertTrue(area.pages.all { it.cols == 0..3 && it.titles == 0..1 })
        assertEquals(2, area.pages[0].rows.first); assertEquals(99, area.pages.last().rows.last)
        // landscape: wider pages
        assertEquals(842f, sheetPages(big, l, SheetPrint(landscape = true)).width)
    }

    @Test fun `rows kept at the top`() {
        assertEquals(3, SheetLayout.of(sheet, freeze = 3).frozenRows)
        assertEquals(0, SheetLayout.of(sheet, freeze = 0).frozenRows)
        assertEquals(3, SheetLayout.of(sheet, sort = SheetSort(1, true), freeze = 3).frozenRows)
    }

    @Test fun `pinned columns come first, in the order pinned`() {
        val l = SheetLayout.of(sheet, pins = listOf(2, 0))
        assertEquals(listOf(2, 0, 1, 3), l.cols.take(4))
        assertEquals(2, l.frozenCols)
        assertEquals(0, l.col(2)); assertEquals(1, l.col(0)); assertEquals(2, l.col(1))
    }
}
