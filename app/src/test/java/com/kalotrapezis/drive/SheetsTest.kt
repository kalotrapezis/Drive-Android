package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Excel's number formats, each against what Excel itself shows; and the readers on LibreOffice's own files. */
class SheetsTest {
    private val f = SheetFormat()
    private fun show(v: Double, code: String) = f.format(v, code).text

    @Test fun general() {
        assertEquals("1234.5", show(1234.5, "General"))
        assertEquals("0.3", show(0.1 + 0.2, "General"))
        assertEquals("0.333333333", show(1 / 3.0, "General"))
        assertEquals("-1.5", show(-1.5, "General"))
        assertEquals("1.23457E+11", show(123456789012.0, "General"))
        assertEquals("1E-10", show(1e-10, "General"))
        assertEquals("42", show(42.0, "General"))
        assertEquals("0", show(0.0, "General"))
        assertEquals("12345678901", show(12345678901.0, "General"))
    }

    @Test fun numbers() {
        assertEquals("3.14", show(3.14159, "0.00"))
        assertEquals("1,234,568", show(1234567.8, "#,##0"))
        assertEquals("-1,234.50", show(-1234.5, "#,##0.00"))
        assertEquals("26%", show(0.256, "0%"))
        assertEquals("25.6%", show(0.256, "0.0%"))
        assertEquals("1,235", show(1234567.0, "#,##0,"))
        assertEquals("007", show(7.0, "000"))
        assertEquals(".5", show(0.5, "#.##"))
        assertEquals("0.5", show(0.5, "0.0#"))
        assertEquals("1,234.50 €", show(1234.5, "#,##0.00\\ \"€\""))
        assertEquals("€1,234.50", show(1234.5, "[$€-408]#,##0.00"))
        assertEquals("zero", show(0.0, "0;-0;\"zero\""))
        assertEquals("(5)", show(-5.0, "#,##0 ;(#,##0)"))
        assertEquals("5 ", show(5.0, "#,##0_);(#,##0)"))
        assertEquals("", show(5.0, ";;;"))
        assertEquals("-0.5", show(-0.5, "0.0"))
        assertEquals("0.0", show(-0.01, "0.0"))
    }

    @Test fun `sections and colours`() {
        val red = f.format(-5.0, "#,##0;[Red]-#,##0")
        assertEquals("-5", red.text); assertEquals(0xFFFF0000.toInt(), red.color)
        assertEquals("-42.00", show(-42.0, "#,##0.00;[RED]\\-#,##0.00"))
        assertNull(f.format(5.0, "#,##0;[Red]-#,##0").color)
        assertEquals("big", show(150.0, "[>100]\"big\";[<=100]\"small\""))
        assertEquals("small", show(50.0, "[>100]\"big\";[<=100]\"small\""))
    }

    @Test fun scientific() {
        assertEquals("1.23E+04", show(12345.0, "0.00E+00"))
        assertEquals("1.23E-03", show(0.00123, "0.00E+00"))
        assertEquals("12.3E+3", show(12345.0, "##0.0E+0"))
    }

    @Test fun fractions() {
        assertEquals("1 1/2", show(1.5, "# ?/?"))
        assertEquals("1/3", show(1 / 3.0, "# ??/??"))
        assertEquals("3/4", show(0.75, "?/4"))
        assertEquals("2", show(2.0, "# ?/?"))
    }

    @Test fun dates() {
        assertEquals("15/3/2023", show(45000.0, "d/m/yyyy"))
        assertEquals("15/03/2023", show(45000.0, "dd/mm/yyyy"))
        assertEquals("Mar-23", show(45000.0, "mmm-yy"))
        assertEquals("Wednesday 15 March 2023", show(45000.0, "dddd d mmmm yyyy"))
        assertEquals("2023-03-15 06:00", show(45000.25, "yyyy-mm-dd hh:mm"))
        assertEquals("12:00", show(0.5, "h:mm"))
        assertEquals("6:00 PM", show(0.75, "h:mm AM/PM"))
        assertEquals("36:00:00", show(1.5, "[h]:mm:ss"))
        assertEquals("01:30", show(90 / 86400.0, "mm:ss"))
        assertEquals("1/1/1900", show(1.0, "d/m/yyyy"))
        assertEquals("15/10/2026", show(46310.0, "d/m/yyyy"))
        assertEquals("15 Μαρτίου 2023", show(45000.0, "[$-408]dd mmmm yyyy"))
        assertEquals("12:00:00.5", show(0.5 + 0.5 / 86400, "hh:mm:ss.0"))
        assertTrue(f.isDate("dd/mm/yyyy")); assertTrue(f.isDate("[h]:mm")); assertFalse(f.isDate("#,##0.00 \"days\"")); assertFalse(f.isDate("0.00E+00"))
    }

    @Test fun `the phone's separators`() {
        val gr = SheetFormat(',', '.')
        assertEquals("1.234,50", gr.format(1234.5, "#,##0.00").text)
        assertEquals("0,3", gr.format(0.3, "General").text)
    }

    @Test fun `a HYPERLINK formula's address`() {
        assertEquals("https://a.gr/x", Sheets.hyperlink("HYPERLINK(\"https://a.gr/x\", \"words\")"))
        assertEquals("#Sheet2!A1", Sheets.hyperlink("=hyperlink(\"#Sheet2!A1\")"))
        assertNull(Sheets.hyperlink("SUM(A1:A2)"))
    }

    @Test fun `shared formulas move`() {
        assertEquals("SUM(B3:B4)+\$A\$1", Sheets.shift("SUM(B2:B3)+\$A\$1", 1, 0))
        assertEquals("C2*2", Sheets.shift("B2*2", 0, 1))
        assertEquals("LOG10(C2)&\"A1\"", Sheets.shift("LOG10(B2)&\"A1\"", 0, 1))
        assertEquals("=SUM(B2:B3)", Sheets.odfFormula("of:=SUM([.B2:.B3])"))
        assertEquals("=Sheet2!A1+1", Sheets.odfFormula("of:=[\$Sheet2.A1]+1"))
    }

    private fun resource(name: String) = javaClass.classLoader!!.getResource(name).readBytes()

    /** sheet.fods (a hand-written LibreOffice file) as LibreOffice 26.2 saved it in both formats: the same sheets. */
    /** [greek]: an .ods carries its numbers as shown, and LibreOffice here saved them with Greek separators. */
    private fun checkBook(book: Workbook, greek: Boolean = false) {
        fun n(s: String) = if (greek) s.replace(',', '#').replace('.', ',').replace('#', '.') else s
        assertEquals(listOf("Numbers", "Second", "Hidden"), book.tabs.map { it.name })
        assertEquals(listOf(false, false, true), book.tabs.map { it.hidden })
        val s = book.sheet(0)
        assertEquals(5, s.rows); assertEquals(4, s.cols)
        assertEquals("Name", s[0, 0]?.text)
        val head = book.styles[s[0, 0]!!.style]
        assertTrue(head.bold); assertEquals(0xFFFFFF00.toInt(), head.fill); assertEquals(0xFF1F3864.toInt(), head.color)
        assertEquals(n("1,234.50"), s[1, 1]?.text); assertEquals(1234.5, s[1, 1]?.value)
        assertEquals(n("-42.00"), s[2, 1]?.text)
        assertEquals(0xFFFF0000.toInt(), s[2, 1]?.color) // [RED] in Excel's format, LibreOffice's number style's colour
        assertNull(s[1, 1]?.color)
        assertEquals("15/03/2023", s[1, 2]?.text); assertEquals(45000.0, s[1, 2]?.value)
        assertEquals(n("25.6%"), s[1, 3]?.text)
        assertEquals("Βήτα", s[2, 0]?.text)
        assertEquals(listOf(SheetRange(3, 0, 3, 1)), s.merges)
        assertEquals(SheetRange(3, 0, 3, 1), s.merge(3, 1))
        val mid = book.styles[s[3, 0]!!.style]
        assertEquals(SheetAlign.Center, mid.align); assertTrue(mid.italic); assertTrue(mid.wrap)
        assertEquals("=SUM(B2:B3)", s[4, 1]?.formula); assertEquals(n("1,192.50"), s[4, 1]?.text)
        assertTrue(book.styles[s[1, 1]!!.style].left != null)
        assertEquals(147f, s.width(0), 5f) // 4 cm (Excel reads LibreOffice's 20.43 characters as 143 px)
        assertEquals(37.8f, s.height(0), 1f) // 1 cm
        assertEquals("Hello", book.sheets[1][2, 2]?.text)
        assertEquals(4, book.sheets[1].rows)
        // A comment, a link out and a link to a cell (LibreOffice's "#Numbers.B5" read as Excel's).
        assertEquals("Teo: Check this", s[1, 0]?.note)
        assertEquals("https://example.org/", book.sheets[1][3, 0]?.link)
        assertEquals("#Numbers!B5", book.sheets[1][3, 1]?.link)
    }

    @Test fun xlsx() {
        val book = Sheets.read(resource("sheet.xlsx"), "sheet.xlsx")
        checkBook(book)
        assertEquals(1, book.sheet(0).frozenRows); assertEquals(1, book.sheet(0).frozenCols)
    }

    /**
     * Excel Tables: Google Sheets' alternating colours (a table style of the file's own: header, two stripes) and
     * Excel's default TableStyleMedium2 (the theme's first accent), laid under the cells' own looks.
     */
    @Test fun tables() {
        val parts = OfficeFormats.unzip(resource("sheet.xlsx"))
        fun edit(name: String, f: (String) -> String) { parts[name] = f(String(parts.getValue(name))).toByteArray() }
        edit("xl/styles.xml") { it.replace("</styleSheet>", "") + """<dxfs count="3"><dxf><font/><fill><patternFill patternType="solid"><fgColor rgb="FF4472C4"/><bgColor rgb="FF4472C4"/></patternFill></fill><border/></dxf><dxf><fill><patternFill patternType="solid"><bgColor rgb="FFD9E2F3"/></patternFill></fill></dxf><dxf><fill><patternFill patternType="solid"><bgColor rgb="FFB4C6E7"/></patternFill></fill></dxf></dxfs><tableStyles count="1"><tableStyle count="3" pivot="0" name="Φύλλο1-style"><tableStyleElement dxfId="0" type="headerRow"/><tableStyleElement dxfId="1" type="firstRowStripe"/><tableStyleElement dxfId="2" type="secondRowStripe"/></tableStyle></tableStyles></styleSheet>""" }
        edit("xl/worksheets/sheet1.xml") { it.replace("</worksheet>", """<tableParts count="1"><tablePart r:id="rIdT"/></tableParts></worksheet>""") }
        parts["xl/worksheets/_rels/sheet1.xml.rels"] = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdT" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/table" Target="../tables/table1.xml"/></Relationships>""".toByteArray()
        fun table(style: String) { parts["xl/tables/table1.xml"] = """<table xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" ref="A6:B9" name="T" id="1"><tableStyleInfo name="$style" showRowStripes="1" showFirstColumn="0" showLastColumn="0" showColumnStripes="0"/></table>""".toByteArray() }
        fun fill(book: Workbook, r: Int, c: Int): Int? = book.sheet(0).let { s -> s[r, c]?.style?.let { s.look(it, book.styles) }?.fill }

        table("Φύλλο1-style")
        var book = Sheets.read(OfficeFormats.zip(parts), "t.xlsx")
        assertEquals(0xFF4472C4.toInt(), fill(book, 5, 0)) // the header row
        assertEquals(0xFFD9E2F3.toInt(), fill(book, 6, 1)) // first stripe, an empty cell too
        assertEquals(0xFFB4C6E7.toInt(), fill(book, 7, 0))
        assertEquals(0xFFD9E2F3.toInt(), fill(book, 8, 0))
        assertEquals(0xFFFFFF00.toInt(), fill(book, 0, 0)) // outside the table: its own

        table("TableStyleMedium2")
        book = Sheets.read(OfficeFormats.zip(parts), "t.xlsx")
        val accent = fill(book, 5, 0)!!
        assertEquals(accent, SheetColors.tint(accent, 0.0))
        assertTrue(book.sheet(0).look(book.sheet(0)[5, 0]!!.style, book.styles)!!.bold)
        assertEquals(SheetColors.tint(accent, 0.8), fill(book, 6, 0))
        assertNull(fill(book, 7, 0))
    }

    /**
     * Pictures and charts (drawings.py, with openpyxl: a column chart E2 and a picture B9): openpyxl's file keeps no
     * numbers for the chart, so they come from the cells; LibreOffice's .xlsx keeps them; its .ods has the chart as an
     * object and the picture in its cell.
     */
    @Test fun drawings() {
        for (name in listOf("drawings.xlsx", "drawings-lo.xlsx", "drawings.ods")) {
            val s = Sheets.read(resource(name), name).sheet(0)
            assertEquals(name, 2, s.drawings.size)
            val chart = s.drawings.first { it.chart != null }
            assertEquals(name, 1, chart.row); assertEquals(name, 4, chart.col)
            assertEquals(name, listOf("Jan", "Feb", "Mar", "Apr"), chart.chart!!.categories)
            assertEquals(name, listOf(120.0, 150.0, 90.0, 180.0), chart.chart.series[0].values)
            assertEquals(name, "Costs", chart.chart.series[1].name)
            val picture = s.drawings.first { it.image != null }
            assertEquals(name, 8, picture.row); assertEquals(name, 1, picture.col)
            // Its size: its own (one cell's anchor), or as far as the cell it ends in (LibreOffice's two cells).
            if (picture.toRow < 0) assertEquals(name, 120f, picture.width, 2f) else assertTrue(name, picture.toCol >= 2 && picture.toRow >= 11)
            assertTrue(name, picture.image!!.invoke()!!.size > 100)
        }
    }

    /**
     * Conditional formatting (rules.py, with openpyxl; LibreOffice's .ods of it): greater than and between, text that
     * contains, a colour scale, data bars, a formula's stripes — in both formats.
     */
    @Test fun rules() {
        for (name in listOf("rules.xlsx", "rules.ods")) {
            val book = Sheets.read(resource(name), name); val s = book.sheet(0)
            fun look(r: Int, c: Int) = s[r, c]?.style?.let { s.look(it, book.styles) }
            assertEquals(name, 0xFFFFC7CE.toInt(), look(8, 0)?.fill) // 8 > 7
            assertEquals(name, 0xFFC6EFCE.toInt(), look(2, 0)?.fill) // 2 is between 2 and 3
            assertNull(name, look(5, 0)?.fill) // 5: neither
            assertEquals(name, 0xFFC6EFCE.toInt(), look(4, 1)?.fill) // "apple" (A4: the second apple)
            assertEquals(name, 0xFFC6EFCE.toInt(), look(10, 1)?.fill) // "Apple pie": without case
            assertNull(name, look(2, 1)?.fill) // "Banana"
            assertEquals(name, 0xFFF8696B.toInt(), look(1, 2)?.fill) // the scale's least
            assertEquals(name, 0xFF63BE7B.toInt(), look(10, 2)?.fill) // and its most
            assertTrue(name, look(5, 2)?.fill.let { it != null && it != 0xFFF8696B.toInt() && it != 0xFF63BE7B.toInt() })
            assertEquals(name, 1f, s.bars.getValue(Sheet.key(10, 3)).fraction, 0.01f)
            assertTrue(name, s.bars.getValue(Sheet.key(1, 3)).fraction < 0.2f)
            assertEquals(name, 0xFFC6EFCE.toInt(), look(1, 4)?.fill) // row 2: even
            assertNull(name, look(2, 4)?.fill)
        }
        // A rule's formula, as far as a viewer works one out.
        val cells = CellMap().apply { set(Sheet.key(0, 0), SheetCell("5", SheetKind.Number, 5.0)); set(Sheet.key(0, 1), SheetCell("Yes", SheetKind.Text)) }
        val at = SheetRange(0, 0, 0, 0)
        assertEquals(true, SheetRules.formula("=AND(\$A1>3,B1=\"yes\")", 0, 0, at, cells))
        assertEquals(false, SheetRules.formula("OR(A1<3;ISBLANK(B1))", 0, 0, at, cells))
        assertEquals(true, SheetRules.formula("NOT(ISBLANK(B1))", 0, 0, at, cells))
        assertNull(SheetRules.formula("VLOOKUP(A1,X:Y,2,0)>1", 0, 0, at, cells))
    }

    @Test fun ods() = checkBook(Sheets.read(resource("sheet.ods"), "sheet.ods"), greek = true)

    /** LibreOffice's view settings (a headless conversion leaves them out): frozen rows and columns, the sheet left on. */
    @Test fun `ods view settings`() {
        val parts = OfficeFormats.unzip(resource("sheet.ods"))
        val fods = String(resource("sheet.fods"))
        parts["settings.xml"] = ("""<?xml version="1.0" encoding="UTF-8"?><office:document-settings xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:config="urn:oasis:names:tc:opendocument:xmlns:config:1.0" office:version="1.3">""" +
            "<office:settings>" + fods.substringAfter("<office:settings>").substringBefore("</office:settings>") + "</office:settings></office:document-settings>").toByteArray()
        val book = Sheets.read(OfficeFormats.zip(parts, "mimetype"), "s.ods")
        assertEquals(1, book.sheet(0).frozenRows); assertEquals(1, book.sheet(0).frozenCols)
        assertEquals(0, book.sheet(1).frozenRows)
    }

    @Test fun csv() {
        val book = Sheets.read("Name;Amount\n\"Smith; J\";12,5\r\n\"say \"\"hi\"\"\";3\n".toByteArray(), "a.csv")
        val s = book.sheets[0]
        assertEquals("Smith; J", s[1, 0]?.text); assertEquals(12.5, s[1, 1]?.value)
        assertEquals("say \"hi\"", s[2, 0]?.text); assertEquals(3, s.rows); assertEquals(2, s.cols)
        // A CSV Excel saved here: Windows Greek, not UTF-8.
        assertEquals("Όνομα", Sheets.read("Όνομα,1".toByteArray(charset("windows-1253")), "g.csv").sheets[0][0, 0]?.text)
    }

    /** Every one of the user's spreadsheets (~/Tetra/Files, not Κλειδιά) reads. Only with TETRA_SWEEP=1. */
    @Test fun `the user's spreadsheets read`() {
        assumeTrue(System.getenv("TETRA_SWEEP") == "1")
        val root = File(System.getProperty("user.home"), "Tetra/Files")
        val files = root.walkTopDown().onEnter { !it.name.startsWith("Κλειδιά") }.filter { it.isFile && Sheets.isSheet(it.name) }.toList()
        val failed = ArrayList<String>()
        files.forEach { file ->
            runCatching {
                val start = System.nanoTime()
                val book = Sheets.read(file)
                val cells = book.tabs.indices.sumOf { book.sheet(it).cells.size }
                println("${file.name}: ${book.tabs.size} sheets, $cells cells, ${(System.nanoTime() - start) / 1_000_000} ms")
            }.onFailure { failed += "${file.relativeTo(root)}: $it" }
        }
        println("Sheets sweep: ${files.size} files, ${failed.size} failed"); failed.forEach(::println)
        assertTrue(failed.joinToString("\n"), failed.isEmpty())
    }
}
