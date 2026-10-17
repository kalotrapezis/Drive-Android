package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // LibreOffice's .ods of it: its spans read; a fill put on the cell and saved by Tetra (the spans written), read again and by LibreOffice
        ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", "ods", "--outdir", out.path, inline.path).redirectErrorStream(true).start().waitFor()
        val ods = File(out, "inline.ods"); val ob = Sheets.read(ods, ',', '.'); check(ob.sheet(0)[0, 0], "LibreOffice's ods")
        SheetHistory().apply(ob, 0, SheetLook.change(ob, ob.sheet(0), listOf(Sheet.key(0, 0))) { st, _ -> st.copy(fill = 0xFF00FF00.toInt()) })
        val saved = File(dir, "saved.ods"); SheetSave.save(ob, ods, saved)
        Sheets.read(saved, ',', '.').let { b -> check(b.sheet(0)[0, 0], "ods saved by Tetra"); assertEquals(0xFF00FF00.toInt(), b.sheet(0).look(b.sheet(0)[0, 0]!!.style, b.styles)?.fill) }
        val back = File(dir, "back").apply { mkdirs() }
        ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", "xlsx", "--outdir", back.path, saved.path).redirectErrorStream(true).start().waitFor()
        check(Sheets.read(File(back, "saved.xlsx"), ',', '.').sheet(0)[0, 0], "LibreOffice's xlsx of Tetra's ods")
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

    /** View › Headings: Excel's showRowColHeaders (a sheet's), LibreOffice's HasColumnRowHeaders (the file's) — off, by LibreOffice too, and on. */
    @Test fun `headings off and on`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("headings").toFile()
            val f = File(dir, name).apply { writeBytes(resource(name)) }
            val b0 = Sheets.read(f, ',', '.'); assertTrue(name, b0.sheet(0).headings)
            fun step(from: File, to: File, on: Boolean) = if (name.endsWith("xlsx")) SheetVisibility.gridXlsx(from, to, b0.parts.getValue("0"), on, "showRowColHeaders")
                else SheetVisibility.gridOds(from, to, b0.tabs[0].name, on, "HasColumnRowHeaders", perSheet = false)
            val off = File(dir, "off.${f.extension}"); step(f, off, false)
            Sheets.read(off, ',', '.').let { b -> assertTrue(name, !b.sheet(0).headings); assertTrue(name, b.sheet(0).grid); assertEquals(name, "Alpha", b.sheet(0)[1, 0]?.text) }
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", conv.path, off.path).redirectErrorStream(true).start().waitFor()
                assertTrue("LibreOffice's $other of $name", !Sheets.read(File(conv, "off.$other"), ',', '.').sheet(0).headings)
            }
            val on = File(dir, "on.${f.extension}"); step(off, on, true)
            assertTrue(name, Sheets.read(on, ',', '.').sheet(0).headings)
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
        val want = SheetPrint(landscape = true, letter = true, margins = listOf(1f, 1f, 1f, 1f), fitWidth = 1, fitHeight = 0, grid = true, area = SheetRange(0, 0, 4, 3), titles = 0..1,
            header = "&LDraft&CPage &P of &N", footer = "&C&A")
        assertEquals(listOf("Draft", "Page [Page] of [Pages]", "[Date]"), SheetHeaderFooter.parts("&L&\"Arial,Bold\"&12Draft&CPage &P of &N&R&D"))
        assertEquals("&LA&&B&C&P", SheetHeaderFooter.code(listOf("A&B", "[Page]", ""))); assertEquals(listOf("A&B", "[Page]", ""), SheetHeaderFooter.parts("&LA&&B&C&P"))
        assertEquals(null, SheetHeaderFooter.code(listOf("", " ", "")))
        assertEquals("Page 2 of 5, Numbers", SheetHeaderFooter.shown("Page [Page] of [Pages], [Sheet]", 2, 5, "Numbers", "a.xlsx"))
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("page").toFile()
            val f = File(dir, name).apply { writeBytes(resource(name)) }
            val b0 = Sheets.read(f, ',', '.'); val out = File(dir, "page.${f.extension}")
            if (name.endsWith("xlsx")) SheetPageSetup.xlsx(f, out, b0.parts.getValue("0"), 0, b0.tabs[0].name, want) else SheetPageSetup.ods(f, out, 0, b0.tabs[0].name, want)
            fun check(file: File, how: String) {
                val b = Sheets.read(file, ',', '.'); val p = b.sheet(0).print
                assertTrue(how, p.landscape); assertTrue(how, p.letter); assertTrue(how, p.grid); assertEquals(how, SheetRange(0, 0, 4, 3), p.area); assertEquals(how, 0..1, p.titles)
                assertEquals(how, SheetHeaderFooter.parts(want.header), SheetHeaderFooter.parts(p.header)); assertEquals(how, SheetHeaderFooter.parts(want.footer), SheetHeaderFooter.parts(p.footer))
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

    /** Format as table: Excel's table part (its look read back, LibreOffice's database range of it), the cells' looks for LibreOffice. */
    @Test fun `a table made`() {
        assertEquals(listOf("Name", "Column2", "name2", "X"), SheetTables.names(listOf("Name", " ", "name", "X")))
        val dir = Files.createTempDirectory("table").toFile()
        val f = File(dir, "sheet.xlsx").apply { writeBytes(resource("sheet.xlsx")) }
        val b0 = Sheets.read(f, ',', '.'); val g = SheetRange(0, 0, 2, 3)
        val names = SheetTables.names((0..3).map { b0.sheet(0)[0, it]?.text.orEmpty() })
        assertEquals(listOf("Name", "Amount", "Date", "Share"), names)
        val out = File(dir, "table.xlsx"); SheetTables.xlsx(f, out, b0.parts.getValue("0"), g, names, "TableStyleMedium2")
        fun fill(b: Workbook, r: Int, c: Int) = b.sheet(0).let { s -> s.look(s[r, c]!!.style, b.styles)?.fill }
        Sheets.read(out, ',', '.').let { b -> assertTrue(fill(b, 1, 3) != null); assertEquals(null, fill(b, 2, 3)); assertEquals("Alpha", b.sheet(0)[1, 0]?.text) }
        // a second over the same cells refused; one beside it its own
        assertTrue(runCatching { SheetTables.xlsx(out, File(dir, "again.xlsx"), b0.parts.getValue("0"), SheetRange(1, 1, 3, 2), listOf("A", "B"), "TableStyleLight9") }.isFailure)
        val two = File(dir, "two.xlsx"); SheetTables.xlsx(out, two, b0.parts.getValue("0"), SheetRange(0, 5, 2, 6), listOf("Column1", "Column2"), "TableStyleLight9")
        OfficeFormats.unzip(two.readBytes()).let { p -> assertTrue(p.keys.toString(), "xl/tables/table1.xml" in p && "xl/tables/table2.xml" in p)
            assertTrue(String(p.getValue("xl/tables/table2.xml")).contains("""id="2" name="Table2"""")) }
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
            val conv = File(dir, "lo").apply { mkdirs() }
            ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", "ods", "--outdir", conv.path, two.path).redirectErrorStream(true).start().waitFor()
            val content = String(OfficeFormats.unzip(File(conv, "two.ods").readBytes()).getValue("content.xml"))
            assertTrue("LibreOffice's database ranges", Regex("""table:database-range\b[^>]*target-range-address="[^"]*A1:[^"]*D3"""").containsMatchIn(content))
            assertEquals("Alpha", Sheets.read(File(conv, "two.ods"), ',', '.').sheet(0)[1, 0]?.text)
        }
        // LibreOffice's .ods: the table's looks on the cells themselves (its AutoFormat)
        val o = File(dir, "sheet.ods").apply { writeBytes(resource("sheet.ods")) }
        val ob = Sheets.read(o, ',', '.'); val os = ob.sheet(0)
        val laid = SheetTables.lay(SheetTables.builtIn("TableStyleMedium2", SheetTables.OFFICE)!!, g, 1, 0) { it == "showRowStripes" }.toMap()
        SheetHistory().apply(ob, 0, SheetLook.change(ob, os, laid.keys) { st, k -> SheetTables.over(st, laid.getValue(k)) })
        val saved = File(dir, "styled.ods"); SheetSave.save(ob, o, saved)
        Sheets.read(saved, ',', '.').let { b -> assertEquals(0xFF4472C4.toInt(), fill(b, 0, 2)); assertEquals(SheetColors.tint(0xFF4472C4.toInt(), 0.8), fill(b, 1, 3)); assertEquals(null, fill(b, 2, 3))
            assertTrue(b.sheet(0).let { s -> s.look(s[0, 2]!!.style, b.styles)!!.bold }) }
    }

    /** Conditional formatting made here: a highlight rule, text that contains, a data bar — Excel's and LibreOffice's, read back and by LibreOffice; cleared. */
    @Test fun `conditional formatting made and cleared`() {
        assertEquals("1.5", SheetConditional.operand("1,5")); assertEquals("\"Al\"", SheetConditional.operand("Al")); assertEquals("A1*2", SheetConditional.operand("=A1*2"))
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("rules").toFile()
            var f = File(dir, name).apply { writeBytes(resource(name)) }
            val b0 = Sheets.read(f, ',', '.')
            fun step(g: SheetRange, rule: SheetConditional.Rule?) { val next = File(dir, "${System.nanoTime()}.${f.extension}")
                if (name.endsWith("xlsx")) SheetConditional.xlsx(f, next, b0.parts.getValue("0"), b0.parts.getValue("styles"), g, rule)
                else SheetConditional.ods(f, next, 0, b0.tabs[0].name, g, rule); f = next }
            step(SheetRange(1, 1, 4, 1), SheetConditional.Rule("greaterThan", listOf("1000"), 0))
            step(SheetRange(1, 0, 2, 0), SheetConditional.Rule("contains", listOf("Al"), 2))
            step(SheetRange(1, 3, 2, 3), SheetConditional.Rule("dataBar"))
            fun check(file: File, how: String) { val b = Sheets.read(file, ',', '.'); val s = b.sheet(0)
                fun fill(r: Int, c: Int) = s.look(s[r, c]!!.style, b.styles)?.fill
                assertEquals(how, 0xFFFFC7CE.toInt(), fill(1, 1)); assertTrue(how, fill(2, 1) != 0xFFFFC7CE.toInt())
                assertEquals(how, 0xFFC6EFCE.toInt(), fill(1, 0)); assertTrue(how, fill(2, 0) != 0xFFC6EFCE.toInt())
                assertTrue(how + ": " + s.bars.keys, s.bars.containsKey(Sheet.key(1, 3)))
                assertEquals(how, "Alpha", s[1, 0]?.text) }
            check(f, "made in $name")
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", conv.path, f.path).redirectErrorStream(true).start().waitFor()
                check(conv.listFiles()!!.single { it.extension == other }, "LibreOffice's $other of $name")
            }
            step(SheetRange(1, 1, 1, 1), null)
            Sheets.read(f, ',', '.').sheet(0).let { s -> assertTrue(name, s.rules.none { it.type == "cellIs" }); assertTrue(name, s.rules.any { it.type == "containsText" }) }
        }
    }

    /** Protect and Unprotect made here: a password (Excel's SHA-512, LibreOffice's SHA-256) or none; an unlocked cell saved and typed in; LibreOffice keeps it. */
    @Test fun `a sheet protected and unprotected`() {
        assertEquals("CBEB", SheetProtect.legacyHash("test"))
        assertEquals("WtgE534lLyyBpDQaFVk25WfLmmRwfjjvKdTdxyh40fCt80RBDtZWAW3QcNDpyo8ytbX1bUd+Tusq+RxaJ7vInw==",
            java.util.Base64.getEncoder().encodeToString(SheetProtect.excelHash("Tetra", ByteArray(16)))) // as Python's hashlib makes it
        assertTrue(SheetProtect.opens(mapOf("password" to "CBEB"), "test")); assertFalse(SheetProtect.opens(mapOf("password" to "CBEB"), "Test"))
        assertTrue(SheetProtect.opens(mapOf("protection-key" to "K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=", "protection-key-digest-algorithm" to "http://www.w3.org/2000/09/xmldsig#sha256"), "secret"))
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("protect").toFile()
            var f = File(dir, name).apply { writeBytes(resource(name)) }
            // B2 unlocked first (Home › Lock cell off), saved
            val b0 = Sheets.read(f, ',', '.'); val s0 = b0.sheet(0)
            SheetHistory().apply(b0, 0, SheetLook.change(b0, s0, listOf(Sheet.key(1, 1))) { st, _ -> st.copy(locked = false) })
            File(dir, "unlocked.${f.extension}").let { SheetSave.save(b0, f, it); f = it }
            fun step(protect: Boolean, password: String) { val next = File(dir, "${System.nanoTime()}.${f.extension}")
                if (name.endsWith("xlsx")) SheetProtect.xlsx(f, next, b0.parts.getValue("0"), protect, password) else SheetProtect.ods(f, next, 0, protect, password); f = next }
            step(true, "secret")
            fun check(file: File, how: String, guarded: Boolean) { val b = Sheets.read(file, ',', '.'); val s = b.sheet(0); val h = SheetHistory(); var no = 0; h.refused = { no++ }
                assertTrue(how, s.protected); assertEquals(how, guarded, s.guarded); assertTrue(how, !b.sheet(1).protected)
                h.apply(b, 0, mapOf(Sheet.key(1, 0) to SheetInput.cell("Gamma", s[1, 0], s[1, 0]!!.style, "General", b.format))); assertEquals(how, 1, no)
                h.apply(b, 0, mapOf(Sheet.key(1, 1) to SheetInput.cell("7", s[1, 1], s[1, 1]!!.style, "General", b.format))); assertEquals(how, 1, no); assertEquals(how, 7.0, s[1, 1]?.value) }
            check(f, "protected in $name", true)
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", conv.path, f.path).redirectErrorStream(true).start().waitFor()
                // LibreOffice keeps the protection but not the password, either way (it can't turn one hash into the other)
                check(conv.listFiles()!!.single { it.extension == other }, "LibreOffice's $other of $name", false)
            }
            assertTrue(name, runCatching { step(false, "wrong") }.isFailure)
            step(false, "secret"); assertTrue(name, !Sheets.read(f, ',', '.').sheet(0).protected)
            step(true, ""); check(f, "protected without a password in $name", false)
            step(false, ""); assertTrue(name, !Sheets.read(f, ',', '.').sheet(0).protected)
        }
    }

    /** Insert › Picture and Text box: Excel's picture and text box shapes, LibreOffice's frame and rectangle — read back, and by LibreOffice. */
    @Test fun `a picture and a text box put on a sheet`() {
        val png = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(java.awt.image.BufferedImage(8, 6, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("picture").toFile()
            var f = File(dir, name).apply { writeBytes(resource(name)) }
            val parts = Sheets.read(f, ',', '.').parts
            fun step(op: (File, File) -> Unit) { val next = File(dir, "${System.nanoTime()}.${f.extension}"); op(f, next); f = next }
            step { a, b -> if (name.endsWith("xlsx")) SheetChart.pictureXlsx(a, b, parts.getValue("0"), 6, 1, 120f, 90f, png, "png") else SheetChart.pictureOds(a, b, 0, 6, 1, 120f, 90f, png, "png") }
            step { a, b -> if (name.endsWith("xlsx")) SheetChart.textBoxXlsx(a, b, parts.getValue("0"), 6, 4, 144f, 60f, "Hello\nthere") else SheetChart.textBoxOds(a, b, 0, 6, 4, 144f, 60f, "Hello\nthere") }
            fun check(file: File, how: String) { val s = Sheets.read(file, ',', '.').sheet(0)
                val pic = s.drawings.firstOrNull { it.image != null }; assertTrue("$how: ${s.drawings.size}", pic != null)
                assertEquals(how, 6, pic!!.row); assertEquals(how, 1, pic.col); assertTrue(how, pic.image!!.invoke()?.isNotEmpty() == true)
                val box = s.drawings.firstOrNull { it.text?.contains("Hello") == true }; assertTrue("$how: ${s.drawings.map { it.text }}", box != null)
                assertEquals(how, 6, box!!.row); assertEquals(how, 4, box.col)
                assertEquals(how, "Alpha", s[1, 0]?.text) }
            check(f, "made in $name")
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", conv.path, f.path).redirectErrorStream(true).start().waitFor()
                check(conv.listFiles()!!.single { it.extension == other }, "LibreOffice's $other of $name")
            }
        }
    }

    /** The view's filter kept as the file's AutoFilter (Excel's autoFilter, LibreOffice's database range): read back, by LibreOffice, taken away. */
    @Test fun `an AutoFilter kept in the file`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        val g = SheetRange(0, 0, 2, 3); val keep = mapOf(0 to setOf("Alpha"))
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("autofilter").toFile()
            val f = File(dir, name).apply { writeBytes(resource(name)) }
            val b0 = Sheets.read(f, ',', '.'); val tab = b0.tabs[0].name
            val kept = File(dir, "kept.${f.extension}")
            if (name.endsWith("xlsx")) SheetAutoFilter.xlsx(f, kept, b0.parts.getValue("0"), 0, tab, g, keep, setOf(2)) else SheetAutoFilter.ods(f, kept, 0, tab, g, keep, setOf(2))
            fun check(file: File, how: String) { val s = Sheets.read(file, ',', '.').sheet(0)
                if (file.extension == "xlsx") { assertEquals(how, g, s.autoFilter); assertEquals(how, keep, s.filterKeep); assertTrue(how, 2 !in s.hiddenRows) }
                else assertTrue(how, 2 in s.hiddenRows && 1 !in s.hiddenRows) // shortcut: an .ods's filter values are not read; its filtered rows are hidden
                assertEquals(how, "Βήτα", s[2, 0]?.text) }
            check(kept, "kept in $name")
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", conv.path, kept.path).redirectErrorStream(true).start().waitFor()
                check(File(conv, "kept.$other"), "LibreOffice's $other of $name")
            }
            val gone = File(dir, "gone.${f.extension}")
            if (name.endsWith("xlsx")) SheetAutoFilter.xlsx(kept, gone, b0.parts.getValue("0"), 0, tab, null, emptyMap(), emptySet(), old = g) else SheetAutoFilter.ods(kept, gone, 0, tab, null, emptyMap(), emptySet(), old = g)
            Sheets.read(gone, ',', '.').sheet(0).let { s -> assertEquals(name, null, s.autoFilter); assertTrue(name, s.hiddenRows.none { it in 1..2 }); assertTrue(name, s.filterKeep.isEmpty()) }
        }
    }

    /** Formulas › Names: a name for B2:B3 (Excel's definedName, LibreOffice's named range), used by a formula, kept by LibreOffice, taken away. */
    @Test fun `names defined and taken away`() {
        assertEquals("That is a cell's name", SheetNames.valid("AB12", emptyList())); assertEquals(null, SheetNames.valid("Amounts_2026", listOf("Other")))
        assertEquals("'My x'!\$A\$1:\$B\$5", SheetNames.fromOds("\$'My x'.\$A\$1:.\$B\$5"))
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("names").toFile()
            val f = File(dir, name).apply { writeBytes(resource(name)) }
            val b0 = Sheets.read(f, ',', '.'); val g = SheetRange(1, 1, 2, 1)
            val named = File(dir, "named.${f.extension}")
            if (name.endsWith("xlsx")) SheetNames.xlsx(f, named, b0.parts.getValue("workbook"), "Amounts", SheetNames.refersTo(b0.tabs[0].name, g))
            else SheetNames.ods(f, named, "Amounts", b0.tabs[0].name, g)
            fun check(file: File, how: String) { val b = Sheets.read(file, ',', '.')
                assertEquals(how, "Numbers!\$B\$2:\$B\$3", b.names["amounts"]); assertTrue(how + ": " + b.nameList(), b.nameList().any { it.first == "Amounts" })
                val s = b.sheet(0); SheetHistory().apply(b, 0, mapOf(Sheet.key(8, 0) to SheetInput.cell("=SUM(Amounts)", null, 0, "General", b.format)))
                assertEquals(how, 1192.5, s[8, 0]?.value) }
            check(named, "named in $name")
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", conv.path, named.path).redirectErrorStream(true).start().waitFor()
                check(File(conv, "named.$other"), "LibreOffice's $other of $name")
            }
            val gone = File(dir, "gone.${f.extension}")
            if (name.endsWith("xlsx")) SheetNames.xlsx(named, gone, b0.parts.getValue("workbook"), "Amounts", null) else SheetNames.ods(named, gone, "Amounts", null, null)
            assertEquals(name, null, Sheets.read(gone, ',', '.').names["amounts"])
        }
    }

    /** Newer functions as files name them: Excel's _xlfn. (FILTER and SORT _xlfn._xlws.), LibreOffice's COM.MICROSOFT. — written so, read back plain. */
    @Test fun `newer functions named as files name them`() {
        assertEquals("_xlfn.XLOOKUP(A1,B:B,C:C)+_xlfn._xlws.FILTER(A1:A3,B1:B3>0)+SUM(A1)&\"IFS(\"", SheetCalc.toXlsx("XLOOKUP(A1,B:B,C:C)+FILTER(A1:A3,B1:B3>0)+SUM(A1)&\"IFS(\""))
        assertEquals("COM.MICROSOFT.TEXTJOIN(\",\",TRUE,A1:A2)+IFNA(1,2)", SheetCalc.toOds("TEXTJOIN(\",\",TRUE,A1:A2)+IFNA(1,2)"))
        assertEquals("=XLOOKUP(1,A:A,B:B)+SORT(A1:A2)", SheetCalc.fromFile("=_xlfn.XLOOKUP(1,A:A,B:B)+_xlfn._xlws.SORT(A1:A2)"))
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("newer").toFile()
            val f = File(dir, name).apply { writeBytes(resource(name)) }
            val b = Sheets.read(f, ',', '.'); val s = b.sheet(0)
            SheetHistory().apply(b, 0, mapOf(Sheet.key(8, 0) to SheetInput.cell("=XLOOKUP(\"Alpha\",A2:A3,B2:B3)", null, 0, "General", b.format),
                Sheet.key(9, 0) to SheetInput.cell("=TEXTJOIN(\"-\",TRUE,A2:A3)", null, 0, "General", b.format)))
            assertEquals(name, 1234.5, s[8, 0]?.value); assertEquals(name, "Alpha-Βήτα", s[9, 0]?.text)
            val saved = File(dir, "saved.${f.extension}"); SheetSave.save(b, f, saved)
            val xml = String(OfficeFormats.unzip(saved.readBytes()).getValue(if (name.endsWith("xlsx")) "xl/worksheets/sheet1.xml" else "content.xml"))
            if (name.endsWith("xlsx")) assertTrue(name, "_xlfn.XLOOKUP(" in xml && "_xlfn.TEXTJOIN(" in xml) else assertTrue(name, "COM.MICROSOFT.XLOOKUP(" in xml && "COM.MICROSOFT.TEXTJOIN(" in xml)
            Sheets.read(saved, ',', '.').sheet(0).let { t -> assertEquals(name, "=XLOOKUP(\"Alpha\",A2:A3,B2:B3)", t[8, 0]?.formula); assertEquals(name, 1234.5, t[8, 0]?.value) }
        }
    }

    /** A spill saved: Excel's array formula over its cells (_xlfn._xlws.SORT), LibreOffice's matrix — read back as a spill, and by LibreOffice. */
    @Test fun `a spill saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("spill").toFile()
            val f = File(dir, name).apply { writeBytes(resource(name)) }
            val b = Sheets.read(f, ',', '.'); val s = b.sheet(0)
            // numbers, so the order is plain (words in two scripts sort by each app's own language)
            SheetHistory().apply(b, 0, mapOf(Sheet.key(1, 5) to SheetInput.cell("=SORT(B2:B3,1,1)", null, 0, "General", b.format)))
            assertEquals(name, listOf(-42.0, 1234.5), listOf(s[1, 5]?.value, s[2, 5]?.value))
            val saved = File(dir, "saved.${f.extension}"); SheetSave.save(b, f, saved)
            val xml = String(OfficeFormats.unzip(saved.readBytes()).getValue(if (name.endsWith("xlsx")) "xl/worksheets/sheet1.xml" else "content.xml"))
            if (name.endsWith("xlsx")) assertTrue(name, Regex("""<f t="array" ref="F2:F3">_xlfn._xlws.SORT\(""").containsMatchIn(xml))
            else assertTrue(name, xml.contains("table:number-matrix-rows-spanned=\"2\"") && xml.contains("COM.MICROSOFT.SORT("))
            fun check(file: File, how: String) { val t = Sheets.read(file, ',', '.').sheet(0)
                assertEquals(how, listOf(-42.0, 1234.5), listOf(t[1, 5]?.value, t[2, 5]?.value))
                assertEquals(how, SheetRange(1, 5, 2, 5), t[1, 5]?.spill); assertEquals(how, Sheet.key(1, 5), t[2, 5]?.spilledFrom) }
            check(saved, "saved in $name")
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", conv.path, saved.path).redirectErrorStream(true).start().waitFor()
                check(File(conv, "saved.$other"), "LibreOffice's $other of $name")
            }
        }
    }

    /** Data › Group, Collapse, Expand, Ungroup: Excel's outline levels and hidden rows, LibreOffice's row groups — read back, and by LibreOffice. */
    @Test fun `rows grouped and collapsed`() {
        assertEquals(1..2, SheetOutline.groupAt(mapOf(1 to 1, 2 to 1), 2)); assertEquals(1..2, SheetOutline.groupAt(mapOf(1 to 1, 2 to 1), 3)); assertEquals(null, SheetOutline.groupAt(mapOf(1 to 1), 4))
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("outline").toFile()
            var f = File(dir, name).apply { writeBytes(resource(name)) }
            val parts = Sheets.read(f, ',', '.').parts
            fun step(by: Int, collapse: Boolean?) { val next = File(dir, "${System.nanoTime()}.${f.extension}")
                if (name.endsWith("xlsx")) SheetOutline.xlsx(f, next, parts.getValue("0"), 1, 2, by, collapse) else SheetOutline.ods(f, next, 0, 1, 2, by, collapse); f = next }
            step(1, null); step(0, true)
            fun check(file: File, how: String) { val s = Sheets.read(file, ',', '.').sheet(0)
                assertEquals(how, mapOf(1 to 1, 2 to 1), s.rowLevels); assertTrue(how, 1 in s.hiddenRows && 2 in s.hiddenRows && 3 !in s.hiddenRows)
                assertEquals(how, "Alpha", s[1, 0]?.text) }
            check(f, "grouped and collapsed in $name")
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", conv.path, f.path).redirectErrorStream(true).start().waitFor()
                check(conv.listFiles()!!.single { it.extension == other }, "LibreOffice's $other of $name")
            }
            step(0, false); Sheets.read(f, ',', '.').sheet(0).let { s -> assertTrue(name, 1 !in s.hiddenRows && 2 !in s.hiddenRows); assertEquals(name, 1, s.rowLevels[1]) }
            step(-1, null); assertTrue(name, Sheets.read(f, ',', '.').sheet(0).rowLevels.isEmpty())
        }
    }

    /** Data › Subtotal: a total row after each group, a Grand Total that counts each value once, the groups outlined — both formats, and LibreOffice. */
    @Test fun `subtotals made`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("subtotal").toFile()
            val f = File(dir, name).apply { writeBytes(resource(name)) }
            val b = Sheets.read(f, ',', '.'); val out = File(dir, "totals.${f.extension}")
            assertEquals(name, listOf(1..1, 2..2), SheetSubtotal.groups(b.sheet(0), SheetRange(0, 0, 2, 3), 0))
            SheetSubtotal.apply(f, out, b, 0, SheetRange(0, 0, 2, 3), 0, listOf(1), 9, ',', '.')
            fun check(file: File, how: String) { val s = Sheets.read(file, ',', '.').sheet(0)
                assertEquals(how, listOf("Alpha", "Alpha Total", "Βήτα", "Βήτα Total", "Grand Total"), (1..5).map { s[it, 0]?.text })
                assertEquals(how, listOf(1234.5, 1234.5, -42.0, -42.0, 1192.5), (1..5).map { s[it, 1]?.value })
                assertEquals(how, mapOf(1 to 1, 3 to 1), s.rowLevels) }
            check(out, "made in $name")
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", conv.path, out.path).redirectErrorStream(true).start().waitFor()
                check(File(conv, "totals.$other"), "LibreOffice's $other of $name")
            }
        }
    }

    /** Insert › Shape: Excel's preset shape, LibreOffice's custom shape (Words' geometry) — read back with fill, line and outline, and by LibreOffice. */
    @Test fun `shapes put on a sheet`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("shape").toFile()
            var f = File(dir, name).apply { writeBytes(resource(name)) }
            val parts = Sheets.read(f, ',', '.').parts
            fun step(kind: String, r: Int) { val next = File(dir, "${System.nanoTime()}.${f.extension}")
                if (name.endsWith("xlsx")) SheetChart.shapeXlsx(f, next, parts.getValue("0"), r, 1, 72f, 72f, kind, 0xFFFFC000.toInt(), 0xFF000000.toInt())
                else SheetChart.shapeOds(f, next, 0, r, 1, 72f, 72f, kind, 0xFFFFC000.toInt(), 0xFF000000.toInt()); f = next }
            step("rect", 6); step("rightArrow", 12)
            fun check(file: File, how: String) { val s = Sheets.read(file, ',', '.').sheet(0)
                val shapes = s.drawings.filter { it.image == null && it.chart == null }.sortedBy { it.row }
                assertEquals("$how: ${s.drawings.size}", listOf(6, 12), shapes.map { it.row })
                assertEquals(how, 0xFFFFC000.toInt(), shapes[0].fill); assertTrue(how, shapes[1].shape != null) }
            check(f, "put in $name")
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", conv.path, f.path).redirectErrorStream(true).start().waitFor()
                check(conv.listFiles()!!.single { it.extension == other }, "LibreOffice's $other of $name")
            }
        }
    }

    /** A drawing moved to another cell, sized, taken away — found by its place in the file; both formats, and LibreOffice. */
    @Test fun `drawings moved sized and taken away`() {
        val png = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(java.awt.image.BufferedImage(8, 6, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("drawings").toFile()
            var f = File(dir, name).apply { writeBytes(resource(name)) }
            val parts = Sheets.read(f, ',', '.').parts
            fun step(op: (File, File) -> Unit) { val next = File(dir, "${System.nanoTime()}.${f.extension}"); op(f, next); f = next }
            step { a, b -> if (name.endsWith("xlsx")) SheetChart.pictureXlsx(a, b, parts.getValue("0"), 6, 1, 120f, 90f, png, "png") else SheetChart.pictureOds(a, b, 0, 6, 1, 120f, 90f, png, "png") }
            step { a, b -> if (name.endsWith("xlsx")) SheetChart.textBoxXlsx(a, b, parts.getValue("0"), 6, 4, 144f, 60f, "Hello") else SheetChart.textBoxOds(a, b, 0, 6, 4, 144f, 60f, "Hello") }
            fun drawings() = Sheets.read(f, ',', '.').sheet(0).drawings
            fun change(d: SheetDrawing, c: SheetDrawings.Change) = step { a, b -> if (name.endsWith("xlsx")) SheetDrawings.xlsx(a, b, parts.getValue("0"), d.ordinal, c) else SheetDrawings.ods(a, b, 0, d.ordinal, c) }
            change(drawings().first { it.image != null }, SheetDrawings.Move(10, 3))
            drawings().first { it.image != null }.let { assertEquals(name, 10 to 3, it.row to it.col) }
            change(drawings().first { it.text == "Hello" }, SheetDrawings.Size(216f, 108f))
            drawings().first { it.text == "Hello" }.let { assertEquals(name, 288f, it.width, 2f); assertEquals(name, 144f, it.height, 2f); assertEquals(name, 6 to 4, it.row to it.col) }
            change(drawings().first { it.image != null }, SheetDrawings.Delete)
            drawings().let { d -> assertTrue(name, d.none { it.image != null }); assertTrue(name, d.any { it.text == "Hello" }) }
            if (lo) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", conv.path, f.path).redirectErrorStream(true).start().waitFor()
                Sheets.read(conv.listFiles()!!.single { it.extension == other }, ',', '.').sheet(0).drawings.let { d -> assertTrue("LibreOffice's $other of $name", d.size == 1 && d[0].text == "Hello") }
            }
        }
    }

    /** View › Freeze panes kept in the file: two rows and a column, read back and by LibreOffice; the top row; unfrozen. */
    @Test fun `panes frozen`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("freeze").toFile()
            var f = File(dir, name).apply { writeBytes(resource(name)) }
            val b0 = Sheets.read(f, ',', '.')
            fun step(rows: Int, cols: Int) { val next = File(dir, "${System.nanoTime()}.${f.extension}")
                if (name.endsWith("xlsx")) SheetVisibility.freezeXlsx(f, next, b0.parts.getValue("0"), rows, cols) else SheetVisibility.freezeOds(f, next, b0.tabs[0].name, rows, cols); f = next }
            fun frozen(file: File) = Sheets.read(file, ',', '.').sheet(0).let { it.frozenRows to it.frozenCols }
            step(2, 1); assertEquals(name, 2 to 1, frozen(f))
            // LibreOffice run without a window writes no view settings into an .ods: only its .xlsx can carry a freeze
            if (lo && name.endsWith("ods")) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", conv.path, f.path).redirectErrorStream(true).start().waitFor()
                assertEquals("LibreOffice's $other of $name", 2 to 1, frozen(conv.listFiles()!!.single { it.extension == other }))
            }
            step(1, 0); assertEquals(name, 1 to 0, frozen(f))
            step(0, 0); assertEquals(name, 0 to 0, frozen(f))
        }
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
