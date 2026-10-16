package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun `a new chart is written as each program keeps one, and reads back the same`() {
        val data = DocChart(ChartKind.Column, "Sales & costs", listOf("Q1", "Q2", "Q3"), listOf(ChartSeries("North", listOf(4.3, 2.5, 3.5)), ChartSeries("South", listOf(2.4, null, 1.8), 0x70AD47)))
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) for (chartKind in ChartKind.entries.filter { it.made }) for (stacked in listOf(false, true)) {
            if (stacked && (chartKind == ChartKind.Pie || chartKind == ChartKind.Doughnut)) continue
            if (chartKind == ChartKind.Scatter) continue // its x values are numbers: below
            val chart = data.copy(kind = chartKind, stacked = stacked)
            val img = PictureXml.withWrap(DocImage("c1", null, 396f, 230f, chart = chart), if (stacked) PictureWrap.Square else PictureWrap.InLine, kind)
            val bytes = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("A chart: "), DocRun(OBJECT_MARK.toString(), image = img))))), kind)
            if (!stacked) java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("chart-${chartKind.name}.${kind.extension}").writeBytes(bytes)
            val back = OfficeFormats.read(bytes, kind).paras.single().runs.mapNotNull { it.image }.single()
            val round = chartKind == ChartKind.Pie || chartKind == ChartKind.Doughnut
            fun plain(c: DocChart?) = c?.copy(series = c.series.map { it.copy(color = null, pointColors = emptyMap()) })
            assertEquals("$kind $chartKind $stacked", plain(chart), plain(back.chart))
            // Colours: each series its own (the palette's when it had none); a pie's slices each theirs.
            if (!round) assertEquals("$kind $chartKind", listOf(OfficeCharts.PALETTE[0], 0x70AD47), back.chart!!.series.map { it.color })
            else assertEquals("$kind $chartKind", OfficeCharts.PALETTE.take(3), (0..2).map { back.chart!!.series[0].pointColors[it] ?: OfficeCharts.PALETTE[it] })
            assertEquals("$kind $chartKind", if (stacked) PictureWrap.Square else PictureWrap.InLine, PictureXml.wrapOf(back))
        }
    }
    @Test fun `axis titles, data labels and a scatter are written and read back in both kinds`() {
        val titled = DocChart(ChartKind.Column, "Sales", listOf("Q1", "Q2"), listOf(ChartSeries("North", listOf(4.0, 2.5))),
            catTitle = "Quarter", valTitle = "Euros & cents", dataLabels = true)
        val scatter = DocChart(ChartKind.Scatter, "Height by age", listOf("2", "4.5", "7"), listOf(ChartSeries("Children", listOf(85.0, 104.0, 122.0))),
            catTitle = "Age", valTitle = "cm")
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) for ((name, chart) in listOf("titled" to titled, "scatter" to scatter, "bar" to titled.copy(kind = ChartKind.Bar), "pie" to titled.copy(kind = ChartKind.Pie, catTitle = null, valTitle = null))) {
            val img = DocImage("c1", null, 396f, 230f, chart = chart)
            val bytes = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun(OBJECT_MARK.toString(), image = img))))), kind)
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("chart-options-$name.${kind.extension}").writeBytes(bytes)
            val back = OfficeFormats.read(bytes, kind).paras.single().runs.mapNotNull { it.image }.single().chart
            fun plain(c: DocChart?) = c?.copy(series = c.series.map { it.copy(color = null, pointColors = emptyMap()) })
            assertEquals("$kind $name", plain(chart), plain(back))
        }
        // The chart's own title is not an axis's.
        val untitled = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun(OBJECT_MARK.toString(), image = DocImage("c1", null, 396f, 230f, chart = titled.copy(title = null))))))), OfficeKind.Docx)
        assertEquals(null, OfficeFormats.read(untitled, OfficeKind.Docx).paras.single().runs.mapNotNull { it.image }.single().chart?.title)
    }

    @Test fun `saving a new chart twice keeps its editable data and floating placement`() {
        for (kind in OfficeKind.entries) {
            val chart = DocChart(ChartKind.Column, "Still editable", listOf("A", "B"), listOf(ChartSeries("Values", listOf(2.0, 5.0))))
            val image = DocImage("inserted", null, 200f, 120f, wrap = PictureWrap.InFront, onPage = 30f to 50f, chart = chart)
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun(OBJECT_MARK.toString(), image = image)))))
            val first = OfficeFormats.write(doc, kind)
            val reread = OfficeFormats.read(first, kind).paras.single().runs.single().image!!
            val bound = reread.with(key = image.key)
            assertEquals(image.key, bound.key)
            assertEquals(reread.chart, bound.chart)
            assertEquals(PictureXml.wrapOf(reread), PictureXml.wrapOf(bound))
            assertEquals(PictureXml.placeOf(reread), PictureXml.placeOf(bound))
            val second = OfficeFormats.write(doc.copy(paras = listOf(DocPara(listOf(DocRun(OBJECT_MARK.toString(), image = bound))))), kind, first)
            val again = OfficeFormats.read(second, kind).paras.single().runs.single().image!!
            assertEquals(reread.chart, again.chart)
            assertEquals(PictureXml.placeOf(reread), PictureXml.placeOf(again))
        }
    }


    @Test fun `a Word chart's numbers are in an embedded workbook, for Word's Edit Data`() {
        for (chart in listOf(DocChart(ChartKind.Column, "Sales & costs", listOf("Q1", "Q2", "Q3"), listOf(ChartSeries("North", listOf(4.3, 2.5, 3.5)), ChartSeries("South", listOf(2.4, null, 1.8)))),
            DocChart(ChartKind.Scatter, "Growth", listOf("1", "2", "4"), listOf(ChartSeries("Height", listOf(10.0, 14.5, 21.0)))))) {
            val img = DocImage("c", null, 300f, 180f, chart = chart)
            val bytes = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun(OBJECT_MARK.toString(), image = img))))), OfficeKind.Docx)
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("chart-${chart.kind}.docx").writeBytes(bytes) // for a look in LibreOffice
            val parts = OfficeFormats.unzip(bytes)
            val chartPart = String(parts.getValue(parts.keys.single { it.matches(Regex("word/charts/chart\\d+\\.xml")) }), Charsets.UTF_8)
            assertTrue(chartPart, "<c:externalData r:id=\"rId1\">" in chartPart && "<c:f>Sheet1!\$B\$2:\$B\$4</c:f>" in chartPart)
            val book = parts.keys.single { it.startsWith("word/embeddings/") && it.endsWith(".xlsx") }
            assertTrue(parts.keys.any { it.startsWith("word/charts/_rels/") && String(parts.getValue(it), Charsets.UTF_8).contains(book.substringAfterLast('/')) })
            val sheet = String(OfficeFormats.unzip(parts.getValue(book)).getValue("xl/worksheets/sheet1.xml"), Charsets.UTF_8)
            assertTrue(sheet, "<c r=\"B1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${chart.series[0].name}</t></is></c>" in sheet)
            assertTrue(sheet, "<c r=\"B3\"><v>${if (chart.kind == ChartKind.Scatter) "14.5" else "2.5"}</v></c>" in sheet)
            // Read back: the same chart (from the cached values).
            val back = OfficeFormats.read(bytes, OfficeKind.Docx).paras[0].runs.single().image!!.chart!!
            assertEquals(chart.series.map { it.name to it.values }, back.series.map { it.name to it.values })
            assertEquals(chart.categories, back.categories)
        }
    }

    /** Excel's other charts (asked 16 Oct), from charts.py's workbook and LibreOffice's .ods of it: a combo, a radar, bubbles. */
    @Test fun `combo, radar and bubble charts read from both kinds of file`() {
        // as a sheet reads them (openpyxl keeps no values in the chart: they are read from the cells)
        val book = Sheets.read(java.io.File("src/test/resources/charts.xlsx"), ',', '.')
        fun xlsx(n: Int) = book.sheet(n - 1).drawings.firstNotNullOf { it.chart }
        val ods = java.util.zip.ZipFile(java.io.File("src/test/resources/charts.ods"))
        fun ods(n: Int) = OfficeCharts.readOdf(ods.getInputStream(ods.getEntry("Object $n/content.xml")).readBytes())!!
        for (combo in listOf(xlsx(1), ods(1))) {
            assertEquals(ChartKind.Column, combo.kind)
            assertEquals(listOf("Sales", "Target"), combo.series.map { it.name })
            assertEquals(listOf(null, ChartKind.Line), combo.series.map { it.kind }) // the target a line over the columns
            assertEquals(listOf(100.0, 110.0, 120.0, 130.0, 140.0), combo.series[1].values)
        }
        for (radar in listOf(xlsx(2), ods(2))) {
            assertEquals(ChartKind.Radar, radar.kind)
            assertEquals(listOf("Reading", "Writing", "Maths", "Science", "Art"), radar.categories)
            assertEquals(listOf(6.0, 9.0, 5.0, 8.0, 7.0), radar.series[1].values)
        }
        for (bubble in listOf(xlsx(3), ods(3))) {
            assertEquals(ChartKind.Bubble, bubble.kind)
            assertEquals(listOf("10", "20", "30", "40"), bubble.categories) // x
            assertEquals(listOf(50.0, 35.0, 42.0, 20.0), bubble.series[0].values) // y
            assertEquals(listOf(3.0, 8.0, 5.0, 12.0), bubble.series[0].sizes)
            assertEquals("Shops", bubble.series[0].name)
        }
    }
}
