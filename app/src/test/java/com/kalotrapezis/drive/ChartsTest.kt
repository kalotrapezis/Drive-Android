package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartsTest {
    private fun charts(name: String, kind: OfficeKind) = OfficeFormats.read(javaClass.getResourceAsStream("/charts/$name")!!.readBytes(), kind)
        .paras.flatMap { it.runs }.mapNotNull { it.image?.chart }

    /** LibreOffice made them (column, bar, line, pie over the same data) and saved them both ways. */
    @Test fun `LibreOffice's charts read from both kinds of file`() {
        for ((name, kind) in listOf("lo-charts.odt" to OfficeKind.Odt, "lo-charts.docx" to OfficeKind.Docx)) {
            val got = charts(name, kind)
            assertEquals(name, listOf(ChartKind.Column, ChartKind.Bar, ChartKind.Line, ChartKind.Pie), got.map { it.kind })
            assertEquals(name, listOf("Sales by quarter", "Bars", "Trend", "Share"), got.map { it.title })
            for (c in got) {
                assertEquals(name, listOf("Q1", "Q2", "Q3", "Q4"), c.categories)
                assertEquals(name, listOf("North", "South"), c.series.map { it.name })
                assertEquals(name, listOf(listOf(10.0, 20.0, 15.0, 25.0), listOf(4.0, 8.5, 12.0, 6.0)), c.series.map { it.values })
            }
            assertEquals(name, 0x004586, got[0].series[0].color) // LibreOffice's first colour, its own
        }
    }
}
