package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editing: typed entries, the formula bar's text, formulas worked out again, undo. */
class SheetEditTest {
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
}
