package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editing: typed entries, the formula bar's text, formulas worked out again, undo. */
class SheetEditTest {
    /** Insert function: every category's name is one the bar knows, and every one the bar knows is in a category. */
    @org.junit.Test fun `functions in categories`() {
        val known = SheetFunctions.all.map { it.name }.toSet()
        FunctionCategories.ALL.forEach { (c, names) -> org.junit.Assert.assertTrue("$c: ${names - known}", (names - known).isEmpty()) }
        org.junit.Assert.assertEquals(emptyList<String>(), FunctionCategories.of("Other").map { it.name })
    }

    /** Text to columns: cut at the separator, not inside quotes, pieces trimmed; spaces: runs as one. */
    @org.junit.Test fun `text cut into columns`() {
        org.junit.Assert.assertEquals(listOf("Alpha", "12", "Crete"), SheetInput.split("Alpha; 12 ;Crete", ";"))
        org.junit.Assert.assertEquals(listOf("Smith, J.", "2020"), SheetInput.split("\"Smith, J.\",2020", ","))
        org.junit.Assert.assertEquals(listOf("a", "b", "c"), SheetInput.split("  a   b c ", " "))
        org.junit.Assert.assertEquals(listOf("one"), SheetInput.split("one", ","))
    }

    /** Remove duplicates: the first of rows alike kept (any case, spaces round trimmed); a header row is not data (a later row like it stays, as Excel's). */
    @org.junit.Test fun `duplicate rows found`() {
        val rows = listOf(listOf("Name", "Kilos"), listOf("Alpha", "12"), listOf("alpha ", "12"), listOf("Beta", "12"), listOf("Name", "Kilos"), listOf("Alpha", "13"))
        org.junit.Assert.assertEquals(listOf(0, 1, 3, 4, 5), SheetInput.unique(rows, headers = true))
        org.junit.Assert.assertEquals(listOf(0, 1, 3, 5), SheetInput.unique(rows, headers = false))
        org.junit.Assert.assertEquals(listOf(0, 1), SheetInput.unique(listOf(listOf("x"), listOf("x"), listOf("y"), listOf("X")), headers = true).take(2))
    }

    /** Find and Replace: every one, any case; in a formula too (Excel's "Look in: Formulas"); none: nothing to change. */
    @org.junit.Test fun `words replaced in a cell`() {
        org.junit.Assert.assertEquals("Gamma and Gamma", SheetInput.replaced("Alpha and alpha", "ALPHA", "Gamma"))
        org.junit.Assert.assertEquals("=SUM(C1:C9)", SheetInput.replaced("=SUM(B1:B9)", "b", "C"))
        org.junit.Assert.assertEquals("a\$1 (x)", SheetInput.replaced("a.b (x)", ".b", "\$1"))
        org.junit.Assert.assertEquals(null, SheetInput.replaced("Beta", "alpha", "x"))
    }

    private val gr = SheetFormat(',', '.', language = java.util.Locale.forLanguageTag("el"))

    @Test fun `typed entries`() {
        fun cell(s: String, format: String = "General") = SheetInput.cell(s, null, 0, format, gr)
        assertEquals(1234.5, cell("1.234,5").value); assertEquals("1234,5", cell("1.234,5").text)
        assertEquals(12.5, cell("12,5").value)
        assertEquals(12.5, cell("12.5").value) // one dot, no comma: a decimal point
        assertEquals(1234567.0, cell("1.234.567").value)
        assertEquals(-3.0, cell("-3").value); assertEquals(30.0, cell("€30").value)
        assertEquals(SheetKind.Text, cell("12a").kind)
        assertEquals(SheetKind.Text, cell("'007").kind); assertEquals("007", cell("'007").text)
        assertEquals(SheetKind.Bool, cell("true").kind)
        cell("25%").let { assertEquals(0.25, it.value!!, 1e-12); assertEquals("0%", it.format); assertEquals("25%", it.text) }
        cell("12,5%").let { assertEquals(0.125, it.value!!, 1e-12); assertEquals("0.0%", it.format); assertEquals("12,5%", it.text) }
        cell("15/3/2026").let { assertEquals(SheetCalc.serial(java.time.LocalDate.of(2026, 3, 15).atStartOfDay()), it.value!!, 1e-9); assertEquals("d/m/yyyy", it.format); assertEquals("15/3/2026", it.text) }
        cell("15/3/2026", "dd/mm/yyyy").let { assertNull(it.format); assertEquals("15/03/2026", it.text) } // the cell's own date format
        cell("9:30").let { assertEquals(9.5 / 24, it.value!!, 1e-12); assertEquals("9:30", it.text) }
        assertEquals("=SUM(A1,B2)", cell("=sum(a1;b2)").formula)
        assertEquals("=IF(A1=\"ναι\",1,'my sheet'!B2)", cell("=if(a1=\"ναι\";1;'my sheet'!b2)").formula) // words and sheet names as typed
        assertEquals(SheetKind.Empty, cell("").kind)
    }

    @Test fun `the formula bar shows the value as typed`() {
        fun bar(c: SheetCell, f: String = "General") = SheetInput.text(c, f, gr)
        assertEquals("1234,5", bar(SheetCell("1.234,50", SheetKind.Number, 1234.5)))
        assertEquals("15/3/2026", bar(SheetCell("15/03/2026", SheetKind.Number, 46096.0), "dd/mm/yyyy"))
        assertEquals("25%", bar(SheetCell("25,0%", SheetKind.Number, 0.25), "0.0%"))
        assertEquals("=A1*2", bar(SheetCell("4", SheetKind.Number, 4.0, formula = "=A1*2")))
        assertEquals("0,1", bar(SheetCell("0,1", SheetKind.Number, 0.1 + 0.2 - 0.2)))
    }

    /** A1 = 2, A2 = A1*3, A3 = SUM(A1:A2), B1 = Other!A1+A3; editing A1 works them out; undo puts them back. */
    @Test fun `formulas worked out again, and undo`() {
        fun sheet(name: String, vararg cells: Pair<Long, SheetCell>) = Sheet(name, CellMap().apply { cells.forEach { (k, c) -> set(k, c) } }, 3, 2, emptyMap(), emptyMap(), 64f, 20f, emptySet(), emptySet(), emptyList(), 0, 0)
        val main = sheet("Main",
            Sheet.key(0, 0) to SheetCell("2", SheetKind.Number, 2.0),
            Sheet.key(1, 0) to SheetCell("6", SheetKind.Number, 6.0, formula = "=A1*3"),
            Sheet.key(2, 0) to SheetCell("8", SheetKind.Number, 8.0, formula = "=SUM(A1:A2)"),
            Sheet.key(0, 1) to SheetCell("18", SheetKind.Number, 18.0, formula = "=Other!A1+A3"),
            Sheet.key(0, 2) to SheetCell("x", SheetKind.Number, 7.0, formula = "=UNKNOWNFN(A1)"))
        val other = sheet("Other", Sheet.key(0, 0) to SheetCell("10", SheetKind.Number, 10.0))
        val book = Workbook(listOf(SheetTab("Main"), SheetTab("Other")), listOf(SheetStyle()), format = gr) { i -> if (i == 0) main else other }
        val history = SheetHistory()
        history.apply(book, 0, mapOf(Sheet.key(0, 0) to SheetInput.cell("5", main[0, 0], 0, "General", gr)))
        assertEquals(15.0, main[1, 0]!!.value); assertEquals(20.0, main[2, 0]!!.value); assertEquals(30.0, main[0, 1]!!.value)
        assertEquals("x", main[0, 2]!!.text) // a function it does not know: the file's value stays
        assertTrue(history.canUndo); assertEquals(1, history.unsaved)
        // another sheet's change reaches a formula that reads it
        history.apply(book, 1, mapOf(Sheet.key(0, 0) to SheetInput.cell("100", other[0, 0], 0, "General", gr)))
        history.apply(book, 0, mapOf(Sheet.key(1, 1) to SheetInput.cell("=B1/0", null, 0, "General", gr)))
        assertEquals("#DIV/0!", main[1, 1]!!.text)
        history.undo(book); history.undo(book); history.undo(book)
        assertEquals(2.0, main[0, 0]!!.value); assertEquals(6.0, main[1, 0]!!.value); assertEquals(18.0, main[0, 1]!!.value)
        assertFalse(history.canUndo); assertEquals(0, history.unsaved)
        history.redo(book)
        assertEquals(5.0, main[0, 0]!!.value); assertEquals(30.0, main[0, 1]!!.value)
    }

    @Test fun `the functions the bar offers`() {
        // every one is worked out by the engine (none unknown)
        val ctx = SheetCalc.Context({ _, _, _ -> null })
        for (f in SheetFunctions.all) {
            val e = runCatching { SheetCalc.eval("${f.name}(1,1,1)", ctx) }.exceptionOrNull()
            assertFalse(f.name, e is SheetCalc.Unknown && e.message == f.name)
        }
        fun names(t: String, at: Int = t.length) = SheetFunctions.matching(t, at).map { it.name }
        assertEquals(listOf("SUM", "SUMIF", "SUMIFS", "SUBSTITUTE"), names("su"))
        assertEquals("SUM", names("=1+su").first())
        assertTrue("COUNTIF" in names("=countif"))
        assertTrue("SUMIFS" in names("=IFS")) // inside the name
        assertEquals(SheetFunctions.all.size, names("=").size)
        assertEquals(emptyList<String>(), names("=B12")) // a cell, not a function
        assertEquals(emptyList<String>(), names("12"))
        assertEquals(emptyList<String>(), names("=SUM(")) // in the brackets: what SUM takes instead
        assertEquals("SUM", SheetFunctions.inside("=SUM(B3:B12", 11)?.name)
        assertEquals("IF", SheetFunctions.inside("=IF(SUM(A1), \"(\"", 18)?.name)
        assertNull(SheetFunctions.inside("=SUM(A1)+", 9))
        val sum = SheetFunctions.all.first { it.name == "SUM" }
        assertEquals("=SUM(" to 5, SheetFunctions.insert("su", 2, sum))
        assertEquals("=1+SUM()*2" to 7, SheetFunctions.insert("=1+su()*2", 5, sum))
        assertEquals("=TODAY()" to 8, SheetFunctions.insert("=tod", 4, SheetFunctions.all.first { it.name == "TODAY" }))
    }

    @Test fun `the fill handle`() {
        val f = SheetFormat()
        fun num(v: Double, code: String = "General") = SheetCell(f.format(v, code).text, SheetKind.Number, v, format = code.takeIf { it != "General" })
        fun text(t: String) = SheetCell(t, SheetKind.Text)
        fun fill(src: List<SheetCell?>, count: Int, down: Int = 1) = SheetFill.series(src, count, f, { it.format ?: "General" }) { fm, j, i -> SheetFill.moved(fm, (src.size + i - j) * down, 0) }
        assertEquals(listOf(3.0, 4.0, 5.0), fill(listOf(num(1.0), num(2.0)), 3).map { it.value })
        assertEquals(listOf(30.0, 40.0), fill(listOf(num(10.0), num(20.0)), 2).map { it.value })
        assertEquals(listOf(7.0, 7.0), fill(listOf(num(7.0)), 2).map { it.value }) // one number: copied, as Excel
        val day = SheetCalc.serial(java.time.LocalDate.of(2026, 10, 16).atStartOfDay())
        assertEquals(listOf(day + 1, day + 2), fill(listOf(num(day, "d/m/yyyy")), 2).map { it.value }) // a date: the next days
        assertEquals("17/10/2026", fill(listOf(num(day, "d/m/yyyy")), 1)[0].text)
        assertEquals(listOf("Week 2", "Week 3"), fill(listOf(text("Week 1")), 2).map { it.text })
        assertEquals(listOf("Week 5", "Week 7"), fill(listOf(text("Week 1"), text("Week 3")), 2).map { it.text })
        assertEquals(listOf("A2", "B2", "A3"), fill(listOf(text("A1"), text("B1")), 3).map { it.text })
        assertEquals(listOf("Item 010"), fill(listOf(text("Item 009")), 1).map { it.text })
        assertEquals(listOf("yes", "no", "yes"), fill(listOf(text("yes"), text("no")), 3).map { it.text })
        // formulas: relative references follow, $ ones stay; up past row 1 is #REF!
        val sum = SheetCell("", SheetKind.Number, 3.0, formula = "=SUM(B3:B12)*\$A\$1+Rows!C3")
        assertEquals(listOf("=SUM(B4:B13)*\$A\$1+Rows!C4", "=SUM(B5:B14)*\$A\$1+Rows!C5"), fill(listOf(sum), 2).map { it.formula })
        assertEquals("=A1&\"B3\"", SheetFill.moved("=A2&\"B3\"", -1, 0)) // words in quotes alone
        assertEquals("=#REF!+1", SheetFill.moved("=A1+1", -1, 0))
        assertEquals("=C1+\$B2", SheetFill.moved("=B1+\$B1", 1, 1).let { SheetFill.moved("=B1+\$B2", 0, 1) })
        assertEquals("=LOG10(B2)", SheetFill.moved("=LOG10(A2)", 0, 1)) // a function's name is not a cell
    }

    @Test fun `month and day names and Fill series`() {
        val f = SheetFormat()
        fun text(t: String) = SheetCell(t, SheetKind.Text)
        fun fill(src: List<SheetCell?>, count: Int) = SheetFill.series(src, count, f, { "General" }) { fm, _, _ -> fm }
        assertEquals(listOf("February", "March"), fill(listOf(text("January")), 2).map { it.text })
        assertEquals(listOf("Jan", "Feb"), fill(listOf(text("Nov"), text("Dec")), 2).map { it.text }) // round the year
        assertEquals(listOf("MAY", "JUL"), fill(listOf(text("JAN"), text("MAR")), 2).map { it.text }) // its step, its capitals
        assertEquals(listOf("mon", "tue"), fill(listOf(text("sun")), 2).map { it.text })
        // Fill ▸ Series
        assertEquals(listOf(1.0, 3.0, 5.0), SheetFill.stepped(1.0, 3, 2.0, "Linear"))
        assertEquals(listOf(2.0, 6.0, 18.0), SheetFill.stepped(2.0, 3, 3.0, "Growth"))
        fun d(y: Int, m: Int, day: Int) = SheetCalc.serial(java.time.LocalDate.of(y, m, day).atStartOfDay())
        assertEquals(listOf(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31)), SheetFill.stepped(d(2026, 1, 31), 3, 1.0, "Month"))
        assertEquals(listOf(d(2026, 10, 16), d(2026, 10, 19), d(2026, 10, 20)), SheetFill.stepped(d(2026, 10, 16), 3, 1.0, "Weekday")) // Friday, then Monday
        assertEquals(listOf(d(2024, 2, 29), d(2025, 2, 28)), SheetFill.stepped(d(2024, 2, 29), 2, 1.0, "Year"))
    }

    /** Insert cells shifting down and Delete cells shifting up in column A: the cells after move, formulas reading them follow, column B's own stay. */
    @Test fun `cells shifted`() {
        fun sheet() = Sheets.csv("1,x\n2,y\n3,z\n4,w\n5,v".toByteArray()).sheet(0).also { s ->
            s.cells[Sheet.key(0, 2)] = SheetCell("", SheetKind.Empty, formula = "=SUM(A1:A5)"); s.cells[Sheet.key(1, 2)] = SheetCell("", SheetKind.Empty, formula = "=A3+B3") }
        fun after(s: Sheet, ch: Map<Long, SheetCell>) = { r: Int, c: Int -> (ch[Sheet.key(r, c)] ?: s[r, c]) }
        val s = sheet(); val a = after(s, SheetCellShift.changes(s, SheetRange(1, 0, 1, 0), rows = true, insert = true))
        assertEquals("", a(1, 0)?.text); assertEquals(listOf("1", "2", "3", "4", "5"), listOf(0, 2, 3, 4, 5).map { a(it, 0)?.text })
        assertEquals(listOf("x", "y", "z"), listOf(0, 1, 2).map { a(it, 1)?.text }) // column B not moved
        assertEquals("=SUM(A1:A6)", a(0, 2)?.formula); assertEquals("=A4+B3", a(1, 2)?.formula)
        val t = sheet(); val d = after(t, SheetCellShift.changes(t, SheetRange(1, 0, 1, 0), rows = true, insert = false))
        assertEquals(listOf("1", "3", "4", "5", ""), (0..4).map { d(it, 0)?.text.orEmpty() })
        assertEquals("=SUM(A1:A4)", d(0, 2)?.formula); assertEquals("=A2+B3", d(1, 2)?.formula)
        // right: row 1's cells from B on move one column over
        val u = sheet(); val rgt = after(u, SheetCellShift.changes(u, SheetRange(0, 1, 0, 1), rows = false, insert = true))
        assertEquals("", rgt(0, 1)?.text); assertEquals("x", rgt(0, 2)?.text); assertEquals("=SUM(A1:A5)", rgt(0, 3)?.formula); assertEquals("y", rgt(1, 1)?.text)
    }

    /** Data › Sort: by a column then another, headers kept, blanks last either way, formulas following their row; the current region. */
    @Test fun `rows sorted`() {
        val s = Sheets.csv("Name,Score,Year\nbeta,3,2020\nAlpha,5,2021\ngamma,,2019\ndelta,3,2018\n\nlater,1,1".toByteArray()).sheet(0)
        s.cells[Sheet.key(1, 3)] = SheetCell("", SheetKind.Empty, formula = "=B2*2")
        val g = SheetSortRows.region(s, 1, 1, s.rows, 4); assertEquals(SheetRange(0, 0, 4, 3), g) // the empty row ends it
        fun sorted(keys: List<Pair<Int, Boolean>>) = SheetSortRows.changes(s, g, keys, headers = true).let { ch -> (1..4).map { r -> (ch[Sheet.key(r, 0)] ?: s[r, 0])?.text } to ch }
        assertEquals(listOf("Alpha", "beta", "delta", "gamma"), sorted(listOf(0 to true)).first)
        val (byScore, ch) = sorted(listOf(1 to false, 2 to true))
        assertEquals(listOf("Alpha", "delta", "beta", "gamma"), byScore) // 5, then the 3s by year up (2018 before 2020), the blank score last
        assertEquals("=B4*2", ch[Sheet.key(3, 3)]?.formula) // beta's row went from 2 to 4: its formula follows
        assertEquals("Name", (ch[Sheet.key(0, 0)] ?: s[0, 0])?.text)
        assertEquals(listOf("gamma", "delta", "beta", "Alpha"), sorted(listOf(0 to false)).first)
    }

    /** Trace precedents and dependents, and error checking: the cells a formula reads, those that read a cell, the error cells in order. */
    @Test fun `formulas traced and errors found`() {
        val s = Sheets.csv("1,2\n3,4".toByteArray()).sheet(0)
        s.cells[Sheet.key(0, 2)] = SheetCell("3", SheetKind.Number, 3.0, formula = "=SUM(A1:B1)")
        s.cells[Sheet.key(1, 2)] = SheetCell("#DIV/0!", SheetKind.Error, formula = "=A2/0")
        s.cells[Sheet.key(2, 0)] = SheetCell("#REF!", SheetKind.Error, formula = "=#REF!")
        s.cells[Sheet.key(2, 1)] = SheetCell("4", SheetKind.Number, 4.0, formula = "=Other!A1+B1")
        fun k(r: Int, c: Int) = Sheet.key(r, c)
        assertEquals(setOf(k(0, 0), k(0, 1)), SheetTrace.precedents(s, 0, 2))
        assertEquals(setOf(k(0, 1)), SheetTrace.precedents(s, 2, 1)) // another sheet's cells not marked here
        assertEquals(setOf(k(0, 2), k(2, 1)), SheetTrace.dependents(s, 0, 1))
        assertEquals(listOf(k(1, 2), k(2, 0)), SheetTrace.errors(s))
    }

    @Test fun `text pasted from another app`() {
        assertEquals(listOf(listOf("Name", "Score"), listOf("Αλεξίου", "10,5")), SheetPaste.table("Name\tScore\r\nΑλεξίου\t10,5\r\n"))
        assertEquals(listOf(listOf("two\nlines", "say \"hi\""), listOf("", "x")), SheetPaste.table("\"two\nlines\"\t\"say \"\"hi\"\"\"\n\tx"))
        assertEquals(listOf(listOf("one")), SheetPaste.table("one"))
    }
}
