package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartFromTableTest {
    private fun table(vararg rows: List<String>) = DocTable("", "", rows.map { r -> DocRow("", r.map { DocCell("", listOf(DocPara(listOf(DocRun(it))))) }) })

    @Test fun `the first row names the series and the first column the categories`() {
        val ch = OfficeCharts.fromTable(table(listOf("Σχολείο", "Μαθητές", "Τμήματα", "Σημειώσεις"), listOf("Α'", "120", "6", "νέο"), listOf("Β'", "1.234,5", "", "")))!!
        assertEquals(listOf("Α'", "Β'"), ch.categories)
        assertEquals(listOf("Μαθητές", "Τμήματα"), ch.series.map { it.name }) // a column of words is no series
        assertEquals(listOf(120.0, 1234.5), ch.series[0].values)
        assertEquals(listOf(6.0, null), ch.series[1].values)
    }

    @Test fun `numbers only are data, and words only are no chart`() {
        val ch = OfficeCharts.fromTable(table(listOf("1", "2"), listOf("3", "4")))!!
        assertEquals(listOf("1", "2"), ch.categories)
        assertEquals(listOf(listOf(1.0, 3.0), listOf(2.0, 4.0)), ch.series.map { it.values })
        assertNull(OfficeCharts.fromTable(table(listOf("a", "b"), listOf("c", "d"))))
    }

    @Test fun `numbers as people write them`() {
        assertEquals(listOf(1234.5, 1234.5, 12.0, 30.0, -4.0, 0.5, 1234567.0, 1.5), listOf("1.234,5", "1,234.5", "12%", "€ 30", "-4", "0,5", "1.234.567", "1.5").map { OfficeCharts.number(it) })
        assertNull(OfficeCharts.number("12a"))
    }

    @Test fun `a merged cell keeps the columns where they are, and a totals row is left out`() {
        val span3 = DocCell("<w:tcPr><w:gridSpan w:val=\"3\"/></w:tcPr>", listOf(DocPara(listOf(DocRun("Total")))))
        val t = table(listOf("Item", "Description", "Quantity", "Amount"), listOf("A", "words", "2", "12.50"))
        val total = DocRow("", listOf(span3, DocCell("", listOf(DocPara(listOf(DocRun("56.25")))))))
        val ch = OfficeCharts.fromTable(t.copy(rows = t.rows + total))!!
        assertEquals(listOf("Quantity", "Amount"), ch.series.map { it.name })
        assertEquals(listOf("A"), ch.categories) // the Total row left out
        assertEquals(listOf(12.5), ch.series[1].values)
    }
}
