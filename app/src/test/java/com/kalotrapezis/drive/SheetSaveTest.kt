package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Saving edits back into the file: read back by Tetra, and opened by LibreOffice (its CSV "as shown", Greek locale) —
 * the edited values, a formula worked out from them, and everything else as it was.
 */
class SheetSaveTest {
    private val gr = SheetFormat(',', '.', language = java.util.Locale.forLanguageTag("el"))
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name).readBytes()

    /** LibreOffice's CSV of each sheet of [file], as it shows it; null when LibreOffice is not here. */
    private fun shown(file: File): Map<String, List<List<String>>>? {
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() != 0) return null
        val out = Files.createTempDirectory("shown").toFile()
        val p = ProcessBuilder("soffice", "--headless", "--convert-to", "csv:Text - txt - csv (StarCalc):44,34,76,1,,0,false,true,true,false,false,-1", "--outdir", out.path, file.path)
            .redirectErrorStream(true).start()
        p.inputStream.readBytes(); p.waitFor()
        return out.listFiles()!!.associate { csv -> csv.nameWithoutExtension.substringAfterLast('-') to Sheets.csv(csv.readBytes()).sheet(0).let { s -> (0 until s.rows).map { r -> (0 until s.cols).map { c -> s[r, c]?.text.orEmpty() } } } }
    }

    /** sheet.xlsx / sheet.ods: B2 1234,5 → 2000, A3 → Γάμμα, D3 → 50%, F10 a new formula, A20 a new row; B5's SUM follows. */
    private fun edit(name: String) {
        val dir = Files.createTempDirectory("save").toFile()
        val file = File(dir, name).apply { writeBytes(resource(name)) }
        val book = Sheets.read(file, ',', '.')
        val s = book.sheet(0); val history = SheetHistory()
        fun type(r: Int, c: Int, what: String) = history.apply(book, 0, mapOf(Sheet.key(r, c) to SheetInput.cell(what, s[r, c], s[r, c]?.style ?: 0, s[r, c]?.style?.let { s.look(it, book.styles) }?.format ?: "General", book.format)))
        type(1, 1, "2000"); type(2, 0, "Γάμμα"); type(2, 3, "50%"); type(9, 5, "=b2*2"); type(19, 0, "Νέα γραμμή")
        assertEquals(1958.0, s[4, 1]!!.value) // =SUM(B2:B3): 2000 − 42
        val saved = File(dir, "saved.$name".substringBefore('.') + "." + name.substringAfterLast('.'))
        SheetSave.save(book, file, saved)
        // read back
        val again = Sheets.read(saved, ',', '.')
        val t = again.sheet(0)
        assertEquals(name, 2000.0, t[1, 1]!!.value); assertEquals(name, "Γάμμα", t[2, 0]!!.text); assertEquals(name, 0.5, t[2, 3]!!.value)
        assertEquals(name, "=B2*2", t[9, 5]!!.formula); assertEquals(name, 4000.0, t[9, 5]!!.value)
        assertEquals(name, "Νέα γραμμή", t[19, 0]!!.text); assertEquals(name, 1958.0, t[4, 1]!!.value)
        assertEquals(name, "Teo: Check this", t[1, 0]?.note) // the comment on A2 kept
        assertEquals(name, "Hello", again.sheet(1)[2, 2]?.text) // the other sheet as it was
        assertEquals(name, listOf(SheetRange(3, 0, 3, 1)), t.merges)
        // and as LibreOffice shows it
        val lo = shown(saved) ?: return
        val first = lo.getValue("Numbers").map { it + List(8) { "" } }
        assertEquals(name, "2.000,00", first[1][1]); assertEquals(name, "Γάμμα", first[2][0]); assertEquals(name, "1.958,00", first[4][1])
        assertEquals(name, "4000", first[9][5]); assertEquals(name, "Νέα γραμμή", first[19][0])
        assertTrue(name, first[2][3].startsWith("50"))
    }

    /** Rich text in a cell (Excel's runs): read as Words' runs (shared and inline), written back as Excel's, and read from LibreOffice's own save. */
    @Test fun `rich text in a cell`() {
        val dir = Files.createTempDirectory("rich").toFile()
        val parts = OfficeFormats.unzip(resource("sheet.xlsx"))
        parts["xl/sharedStrings.xml"] = String(parts.getValue("xl/sharedStrings.xml")).replace("""<si><t xml:space="preserve">Name</t></si>""",
            """<si><r><rPr><b/><sz val="11"/></rPr><t xml:space="preserve">Na</t></r><r><rPr><i/><sz val="14"/><color rgb="FFFF0000"/></rPr><t>me</t></r></si>""").toByteArray()
        val file = File(dir, "rich.xlsx").apply { writeBytes(OfficeFormats.zip(parts)) }
        fun check(c: SheetCell?, how: String) {
            val runs = c?.rich; assertTrue(how, runs != null && runs.size == 2)
            assertEquals(how, listOf("Na", "me"), runs!!.map { it.text }); assertTrue(how, runs[0].bold && !runs[0].italic && runs[1].italic && !runs[1].bold)
            assertEquals(how, 0xFF0000, runs[1].color); assertEquals(how, 14f, runs[1].sizePt)
        }
        val book = Sheets.read(file, ',', '.')
        check(book.sheet(0)[0, 0], "shared")
        // as Tetra writes a cell with runs (inline), read again
        val xml = SheetSave.cellXml("A1", 0, book.sheet(0)[0, 0]!!)!!
        parts["xl/worksheets/sheet1.xml"] = String(parts.getValue("xl/worksheets/sheet1.xml")).replace(Regex("""<c r="A1"[^>]*?(/>|>.*?</c>)"""), xml).toByteArray()
        val inline = File(dir, "inline.xlsx").apply { writeBytes(OfficeFormats.zip(parts)) }
        check(Sheets.read(inline, ',', '.').sheet(0)[0, 0], "inline")
        // LibreOffice's own save of it
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() != 0) return
        val out = File(dir, "lo").apply { mkdirs() }
        ProcessBuilder("soffice", "--headless", "--convert-to", "xlsx", "--outdir", out.path, inline.path).redirectErrorStream(true).start().waitFor()
        check(Sheets.read(File(out, "inline.xlsx"), ',', '.').sheet(0)[0, 0], "LibreOffice's save")
    }

    /** Merge & centre (Excel's mergeCell, LibreOffice's spanned and covered cells): C2:D3 one cell, only C2 keeping its value; A4:B4 unmerged. */
    @Test fun `cells merged and unmerged`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("merge").toFile()
            val file = File(dir, name).apply { writeBytes(resource(name)) }
            val book = Sheets.read(file, ',', '.')
            val merged = File(dir, "merged.${file.extension}"); val g = SheetRange(1, 2, 2, 3)
            if (name.endsWith("xlsx")) SheetMerge.xlsx(file, merged, book.parts.getValue("0"), g, true) else SheetMerge.ods(file, merged, 0, g, true)
            fun check(f: File, how: String) {
                val s = Sheets.read(f, ',', '.').sheet(0)
                assertTrue(how, g in s.merges); assertTrue(how, SheetRange(3, 0, 3, 1) in s.merges)
                assertEquals(how, "15/03/2023", s[1, 2]?.text); assertTrue(how, s[1, 3]?.text.isNullOrEmpty() && s[2, 2]?.text.isNullOrEmpty())
                assertEquals(how, "Alpha", s[1, 0]?.text)
            }
            check(merged, "merged $name")
            val apart = File(dir, "apart.${file.extension}")
            if (name.endsWith("xlsx")) SheetMerge.xlsx(merged, apart, book.parts.getValue("0"), SheetRange(3, 0, 3, 1), false) else SheetMerge.ods(merged, apart, 0, SheetRange(3, 0, 3, 1), false)
            Sheets.read(apart, ',', '.').sheet(0).let { s -> assertEquals(name, listOf(g), s.merges) }
            if (lo) {
                val out = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", out.path, merged.path).redirectErrorStream(true).start().waitFor()
                check(File(out, "merged.$other"), "LibreOffice's $other of $name")
            }
        }
    }

    /** Insert › Chart (Words' chart editor): a chart on the sheet beside the cells — Excel's chart in the sheet's drawing, LibreOffice's object in a cell. */
    @Test fun `a chart put on a sheet`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        val ch = DocChart(ChartKind.Column, "Olives", listOf("2025", "2026"), listOf(ChartSeries("Kilos", listOf(120.0, 150.0)), ChartSeries("Trees", listOf(30.0, 32.0))))
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("chart").toFile()
            val file = File(dir, name).apply { writeBytes(resource(name)) }
            val book = Sheets.read(file, ',', '.')
            val out = File(dir, "chart.${file.extension}")
            if (name.endsWith("xlsx")) SheetChart.xlsx(file, out, book.parts.getValue("0"), 1, 5, 15, 8, ch) else SheetChart.ods(file, out, 0, 1, 5, 360f, 216f, ch)
            fun check(f: File, how: String) {
                val s = Sheets.read(f, ',', '.').sheet(0)
                val c = s.drawings.mapNotNull { it.chart }.singleOrNull(); assertTrue("$how: ${s.drawings.size} drawings", c != null)
                assertEquals(how, listOf("Kilos", "Trees"), c!!.series.map { it.name }); assertEquals(how, listOf(120.0, 150.0), c.series[0].values)
                assertEquals(how, 1, s.drawings.first { it.chart != null }.row); assertEquals(how, 5, s.drawings.first { it.chart != null }.col)
                assertEquals(how, "Alpha", s[1, 0]?.text)
            }
            check(out, "put on $name")
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", conv.path, out.path).redirectErrorStream(true).start().waitFor()
                check(File(conv, "chart.$other"), "LibreOffice's $other of $name")
            }
        }
    }

    /** Column width, row height, Hide and Unhide (Excel's cols and rows, LibreOffice's column and row styles and visibility). */
    @Test fun `widths heights and hidden saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("sizes").toFile()
            var f = File(dir, name).apply { writeBytes(resource(name)) }
            val part = Sheets.read(f, ',', '.').parts["0"]
            fun step(cols: Boolean, from: Int, to: Int, px: Float?, hidden: Boolean?) {
                val next = File(dir, "${System.nanoTime()}.${f.extension}")
                if (name.endsWith("xlsx")) SheetSizes.xlsx(f, next, part!!, cols, from, to, px, hidden) else SheetSizes.ods(f, next, 0, cols, from, to, px, hidden)
                f = next
            }
            step(true, 2, 2, 150f, null); step(true, 3, 3, null, true); step(false, 2, 2, 40f, null); step(false, 3, 3, null, true)
            fun check(file: File, how: String, d: Boolean) {
                val s = Sheets.read(file, ',', '.').sheet(0)
                // LibreOffice reads Excel's width in characters with its own character's width (150 px → 159)
                assertEquals(how, 150f, s.width(2), if (how.startsWith("LibreOffice")) 12f else 2f); assertEquals(how, 40f, s.height(2), 2f); assertTrue(how, 3 in s.hiddenRows)
                assertEquals(how, d, 3 in s.hiddenCols); assertEquals(how, "Alpha", s[1, 0]?.text); assertEquals(how, "Βήτα", s[2, 0]?.text)
            }
            check(f, "sized $name", true)
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", conv.path, f.path).redirectErrorStream(true).start().waitFor()
                check(conv.listFiles()!!.single { it.extension == other }, "LibreOffice's $other of $name", true)
            }
            step(true, 3, 3, null, false)
            check(f, "column D shown again, $name", false)
        }
    }

    /** Insert › Link on a cell (Words' dialog): a web page and another sheet — Excel's hyperlinks, LibreOffice's text:a; then one taken away. */
    @Test fun `links on cells saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("links").toFile()
            var f = File(dir, name).apply { writeBytes(resource(name)) }
            val part = Sheets.read(f, ',', '.').parts["0"]
            fun step(r: Int, c: Int, url: String?) { val next = File(dir, "${System.nanoTime()}.${f.extension}")
                if (name.endsWith("xlsx")) SheetLinks.xlsx(f, next, part!!, r, c, url) else SheetLinks.ods(f, next, 0, r, c, url); f = next }
            step(1, 0, "https://example.org/olives"); step(2, 0, "#'Second'!A1")
            fun check(file: File, how: String) {
                val s = Sheets.read(file, ',', '.').sheet(0)
                assertEquals(how, "https://example.org/olives", s[1, 0]?.link); assertEquals(how, "Alpha", s[1, 0]?.text)
                assertTrue(how + ": " + s[2, 0]?.link, s[2, 0]?.link?.replace("'", "")?.equals("#Second!A1", true) == true); assertEquals(how, "Βήτα", s[2, 0]?.text)
            }
            check(f, "linked $name")
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", conv.path, f.path).redirectErrorStream(true).start().waitFor()
                check(conv.listFiles()!!.single { it.extension == other }, "LibreOffice's $other of $name")
            }
            step(1, 0, null)
            Sheets.read(f, ',', '.').sheet(0).let { s -> assertEquals(name, null, s[1, 0]?.link); assertEquals(name, "Alpha", s[1, 0]?.text); assertTrue(name, s[2, 0]?.link != null) }
        }
    }

    /** A sheet hidden and shown again (Excel's state="hidden", LibreOffice's table:display). */
    @Test fun `a sheet hidden and shown`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("hide").toFile()
            val f = File(dir, name).apply { writeBytes(resource(name)) }
            val hidden = File(dir, "hidden.${f.extension}")
            if (name.endsWith("xlsx")) SheetVisibility.xlsx(f, hidden, 1, true) else SheetVisibility.ods(f, hidden, 1, true)
            val was = Sheets.read(f, ',', '.').tabs.map { it.hidden }
            fun check(file: File, how: String) = Sheets.read(file, ',', '.').let { b -> assertEquals(how, was.mapIndexed { k, h -> h || k == 1 }, b.tabs.map { it.hidden }); assertEquals(how, "Hello", b.sheet(1)[2, 2]?.text) }
            check(hidden, "hidden, $name")
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", conv.path, hidden.path).redirectErrorStream(true).start().waitFor()
                check(File(conv, "hidden.$other"), "LibreOffice's $other of $name")
            }
            val shown = File(dir, "shown.${f.extension}")
            if (name.endsWith("xlsx")) SheetVisibility.xlsx(hidden, shown, 1, false) else SheetVisibility.ods(hidden, shown, 1, false)
            assertEquals(name, was, Sheets.read(shown, ',', '.').tabs.map { it.hidden })
        }
    }

    /** View › Gridlines off and on again (Excel's sheet view, LibreOffice's ShowGrid). */
    @Test fun `gridlines off and on`() {
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("grid").toFile()
            val f = File(dir, name).apply { writeBytes(resource(name)) }
            val b0 = Sheets.read(f, ',', '.'); assertTrue(name, b0.sheet(0).grid)
            val off = File(dir, "off.${f.extension}")
            if (name.endsWith("xlsx")) SheetVisibility.gridXlsx(f, off, b0.parts.getValue("0"), false) else SheetVisibility.gridOds(f, off, b0.tabs[0].name, false)
            assertTrue(name, !Sheets.read(off, ',', '.').sheet(0).grid)
            // not checked in LibreOffice: 26.2 carries gridlines neither way (Excel's showGridLines="0" → ShowGrid true; its .xlsx always "true")
            val on = File(dir, "on.${f.extension}")
            if (name.endsWith("xlsx")) SheetVisibility.gridXlsx(off, on, b0.parts.getValue("0"), true) else SheetVisibility.gridOds(off, on, b0.tabs[0].name, true)
            assertTrue(name, Sheets.read(on, ',', '.').sheet(0).grid)
        }
    }

    /** A protected sheet (Excel's sheetProtection): its locked cells refused, an unlocked one (its look's protection) typed in. */
    @Test fun `a protected sheet's locked cells kept`() {
        val parts = OfficeFormats.unzip(resource("sheet.xlsx"))
        parts["xl/worksheets/sheet1.xml"] = String(parts.getValue("xl/worksheets/sheet1.xml")).replace("</sheetData>", """</sheetData><sheetProtection sheet="1" objects="1" scenarios="1"/>""").toByteArray()
        // B2's look (the third) unlocked
        val st = String(parts.getValue("xl/styles.xml")); val xfs = Regex("""<cellXfs[\s\S]*?</cellXfs>""").find(st)!!
        var n = -1
        parts["xl/styles.xml"] = st.replaceRange(xfs.range, Regex("""<xf\b[\s\S]*?</xf>""").replace(xfs.value) { m -> n++; if (n == 2) m.value.replace(Regex("""<protection locked="[^"]*""""), """<protection locked="false"""") else m.value }).toByteArray()
        val file = File(Files.createTempDirectory("protect").toFile(), "p.xlsx").apply { writeBytes(OfficeFormats.zip(parts)) }
        val book = Sheets.read(file, ',', '.'); val s = book.sheet(0); val h = SheetHistory()
        var refused = 0; h.refused = { refused++ }
        assertTrue(s.protected)
        h.apply(book, 0, mapOf(Sheet.key(1, 0) to SheetInput.cell("Gamma", s[1, 0], s[1, 0]!!.style, "General", book.format)))
        assertEquals(1, refused); assertEquals("Alpha", s[1, 0]?.text)
        h.apply(book, 0, mapOf(Sheet.key(1, 1) to SheetInput.cell("2000", s[1, 1], s[1, 1]!!.style, "General", book.format)))
        assertEquals(1, refused); assertEquals(2000.0, s[1, 1]?.value)
        assertTrue(!Sheets.read(File(file.parentFile, "x.xlsx").apply { writeBytes(resource("sheet.xlsx")) }, ',', '.').sheet(0).protected)
        // LibreOffice's .ods of it: table:protected, the unlocked look's cell-protect none
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() != 0) return
        val out = File(file.parentFile, "lo").apply { mkdirs() }
        ProcessBuilder("soffice", "--headless", "--convert-to", "ods", "--outdir", out.path, file.path).redirectErrorStream(true).start().waitFor()
        val ob = Sheets.read(File(out, "p.ods"), ',', '.'); val os = ob.sheet(0); val oh = SheetHistory(); var no = 0; oh.refused = { no++ }
        assertTrue("LibreOffice's ods", os.protected)
        oh.apply(ob, 0, mapOf(Sheet.key(1, 0) to SheetInput.cell("Gamma", os[1, 0], os[1, 0]!!.style, "General", ob.format))); assertEquals("LibreOffice's ods", 1, no)
        oh.apply(ob, 0, mapOf(Sheet.key(1, 1) to SheetInput.cell("2000", os[1, 1], os[1, 1]!!.style, "General", ob.format))); assertEquals("LibreOffice's ods", 1, no)
    }

    /** Page layout: landscape, Letter, wide margins, all columns on one page, gridlines printed, a print area — Excel's and LibreOffice's. */
    @Test fun `page layout saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        val want = SheetPrint(landscape = true, letter = true, margins = listOf(1f, 1f, 1f, 1f), fitWidth = 1, fitHeight = 0, grid = true, area = SheetRange(0, 0, 4, 3))
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("page").toFile()
            val f = File(dir, name).apply { writeBytes(resource(name)) }
            val b0 = Sheets.read(f, ',', '.'); val out = File(dir, "page.${f.extension}")
            if (name.endsWith("xlsx")) SheetPageSetup.xlsx(f, out, b0.parts.getValue("0"), 0, b0.tabs[0].name, want) else SheetPageSetup.ods(f, out, 0, b0.tabs[0].name, want)
            fun check(file: File, how: String) {
                val b = Sheets.read(file, ',', '.'); val p = b.sheet(0).print
                assertTrue(how, p.landscape); assertTrue(how, p.letter); assertTrue(how, p.grid); assertEquals(how, SheetRange(0, 0, 4, 3), p.area)
                assertEquals(how, 1, p.fitWidth); assertEquals(how, 0, p.fitHeight ?: 0)
                // LibreOffice turns a page's header room into Excel's margins its own way (a few hundredths of an inch off)
                p.margins.forEach { assertEquals(how, 1f, it, if (how.startsWith("LibreOffice")) 0.1f else 0.02f) }
                assertEquals(how, "Alpha", b.sheet(0)[1, 0]?.text); assertEquals(how, false, b.sheet(1).print.landscape)
            }
            check(out, "set in $name")
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", conv.path, out.path).redirectErrorStream(true).start().waitFor()
                check(File(conv, "page.$other"), "LibreOffice's $other of $name")
            }
        }
    }

    /** Notes on cells (Excel's comments and VML note shapes, LibreOffice's annotations): made where there were some and where none, one taken away. */
    @Test fun `notes made and taken away`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("notes").toFile()
            var f = File(dir, name).apply { writeBytes(resource(name)) }
            val parts = Sheets.read(f, ',', '.').parts
            fun step(sheet: Int, r: Int, c: Int, text: String?) { val next = File(dir, "${System.nanoTime()}.${f.extension}")
                if (name.endsWith("xlsx")) SheetNotes.xlsx(f, next, parts.getValue("$sheet"), r, c, "Teo", text) else SheetNotes.ods(f, next, sheet, r, c, "Teo", text); f = next }
            step(0, 2, 2, "Check the date"); step(1, 0, 1, "Second sheet's note")
            fun check(file: File, how: String) { val b = Sheets.read(file, ',', '.')
                assertTrue(how + ": " + b.sheet(0)[2, 2]?.note, b.sheet(0)[2, 2]?.note?.contains("Check the date") == true && b.sheet(0)[2, 2]!!.note!!.contains("Teo"))
                assertTrue(how, b.sheet(0)[1, 0]?.note?.contains("Check this") == true) // the file's own kept
                assertTrue(how + ": " + b.sheet(1)[0, 1]?.note, b.sheet(1)[0, 1]?.note?.contains("Second sheet's note") == true)
                assertEquals(how, "15/03/2023", b.sheet(0)[1, 2]?.text) }
            check(f, "made in $name")
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", conv.path, f.path).redirectErrorStream(true).start().waitFor()
                check(conv.listFiles()!!.single { it.extension == other }, "LibreOffice's $other of $name")
            }
            step(0, 2, 2, null)
            Sheets.read(f, ',', '.').let { b -> assertEquals(name, null, b.sheet(0)[2, 2]?.note); assertTrue(name, b.sheet(0)[1, 0]?.note != null) }
        }
    }

    /** Drop-down lists (Excel's list validation, LibreOffice's content-validation): made, read back and by LibreOffice, taken away. */
    @Test fun `drop-down lists made and taken away`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        val values = listOf("Yes", "No", "Say \"maybe\"")
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("lists").toFile()
            var f = File(dir, name).apply { writeBytes(resource(name)) }
            val parts = Sheets.read(f, ',', '.').parts
            fun step(sheet: Int, g: SheetRange, items: List<String>?) { val next = File(dir, "${System.nanoTime()}.${f.extension}")
                if (name.endsWith("xlsx")) SheetLists.xlsx(f, next, parts.getValue("$sheet"), g, items) else SheetLists.ods(f, next, sheet, g, items); f = next }
            step(0, SheetRange(1, 3, 3, 3), values); step(1, SheetRange(0, 2, 0, 2), listOf("A", "B"))
            fun check(file: File, how: String) { val b = Sheets.read(file, ',', '.'); val s = b.sheet(0)
                assertEquals(how, values, s.listAt(2, 3)?.items); assertEquals(how, values, s.listAt(3, 3)?.items)
                assertEquals(how, null, s.listAt(4, 3)); assertEquals(how, null, s.listAt(2, 2))
                assertEquals(how, listOf("A", "B"), b.sheet(1).listAt(0, 2)?.items)
                assertEquals(how, "15/03/2023", s[1, 2]?.text) }
            check(f, "made in $name")
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", conv.path, f.path).redirectErrorStream(true).start().waitFor()
                check(conv.listFiles()!!.single { it.extension == other }, "LibreOffice's $other of $name")
            }
            step(0, SheetRange(2, 3, 2, 3), null)
            Sheets.read(f, ',', '.').let { b -> assertEquals(name, null, b.sheet(0).listAt(2, 3)); assertEquals(name, listOf("A", "B"), b.sheet(1).listAt(0, 2)?.items) }
        }
        // a list from cells of the sheet: their words; LibreOffice's conditions in Excel's form
        assertEquals(listOf("Alpha", "Beta"), SheetList.of(listOf(SheetRange(0, 5, 0, 5)), "\$A\$2:\$A\$3", "Sheet1") { r, c -> mapOf((1 to 0) to "Alpha", (2 to 0) to "Beta")[r to c] }?.items)
        assertEquals("\"a,b\"\"c\"", SheetList.odsList("of:cell-content-is-in-list(\"a\";\"b\"\"c\")"))
        assertEquals("'Lists'!A1:A4", SheetList.odsList("of:cell-content-is-in-list([\$Lists.\$A\$1:.\$A\$4])"))
    }

    @Test fun xlsx() = edit("sheet.xlsx")
    @Test fun ods() = edit("sheet.ods")

    @Test fun csv() {
        val dir = Files.createTempDirectory("save").toFile()
        val file = File(dir, "a.csv").apply { writeBytes("Όνομα;Ποσό\r\nΑ;12,5\r\n\"Β; Γ\";3\r\n".toByteArray(charset("windows-1253"))) }
        val book = Sheets.read(file, ',', '.')
        val history = SheetHistory()
        history.apply(book, 0, mapOf(Sheet.key(1, 1) to SheetInput.cell("13,75", book.sheet(0)[1, 1], 0, "General", book.format)))
        SheetSave.save(book, file, file)
        assertEquals("Όνομα;Ποσό\r\nΑ;13,75\r\n\"Β; Γ\";3\r\n", String(file.readBytes(), charset("windows-1253")))
    }

    /** Excel's shared formula: B2 holds "A2*2" for B2:B4; B2 changed, B3 and B4 get theirs written out. */
    @Test fun `a shared formula broken by an edit`() {
        val xml = """<worksheet><dimension ref="A1:B4"/><sheetData><row r="2"><c r="A2"><v>1</v></c><c r="B2"><f t="shared" ref="B2:B4" si="0">A2*2</f><v>2</v></c></row>""" +
            """<row r="3" spans="1:2"><c r="A3"><v>2</v></c><c r="B3"><f t="shared" si="0"/><v>4</v></c></row><row r="4"><c r="B4"><f t="shared" si="0"/><v>0</v></c></row></sheetData></worksheet>"""
        val out = SheetSave.patchSheet(xml, mapOf(Sheet.key(1, 1) to SheetCell("9", SheetKind.Number, 9.0)), { c, old -> old ?: c.fileStyle },
            { r, _ -> "=A${r + 1}*2" }, 4, 2)
        assertTrue(out, "<c r=\"B2\"><v>9</v></c>" in out)
        assertTrue(out, "<c r=\"B3\"><f>A3*2</f><v>4</v></c>" in out)
        assertTrue(out, "<c r=\"B4\"><f>A4*2</f><v>0</v></c>" in out)
        assertTrue(out, "<row r=\"3\">" in out) // spans dropped from a row written again
    }
}
