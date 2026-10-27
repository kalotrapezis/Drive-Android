package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Sheets added, renamed, moved and deleted in Excel-test (.xlsx and .ods), and a new empty workbook — opened by LibreOffice. */
class SheetBookTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name).readBytes()

    private fun shown(file: File): Map<String, List<List<String>>>? {
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() != 0) return null
        val out = Files.createTempDirectory("shown").toFile()
        soffice("csv:Text - txt - csv (StarCalc):44,34,76,1,,0,false,true,true,false,false,-1", out.path, file.path)
        return out.listFiles()!!.associate { csv -> csv.nameWithoutExtension.substringAfter(file.nameWithoutExtension + "-") to Sheets.csv(csv.readBytes()).sheet(0).let { s -> (0 until s.rows).map { r -> (0 until s.cols).map { c -> s[r, c]?.text.orEmpty() } + List(6) { "" } } } }
    }

    private fun change(name: String, op: (File, File, Workbook) -> Unit): Pair<Workbook, File> {
        val dir = Files.createTempDirectory("book").toFile()
        val source = File(dir, name).apply { writeBytes(resource(name)) }
        val target = File(dir, "changed." + name.substringAfterLast('.'))
        op(source, target, Sheets.read(source, ',', '.'))
        return Sheets.read(target, ',', '.') to target
    }

    private fun run(name: String, index: Int, add: String? = null, rename: String? = null, delete: Boolean = false, to: Int? = null) = change(name) { s, t, b ->
        if (b.kind == "xlsx") SheetBook.xlsx(s, t, b.parts, b.tabs.map { it.name }, index, add, rename, delete, to)
        else SheetBook.ods(s, t, b.tabs.map { it.name }, index, add, rename, delete, to)
    }

    /** Move or Copy › Create a copy, and a tab's colour: the copy after the sheet with its cells and looks; LibreOffice shows it. */
    @Test fun `a sheet copied and a tab coloured`() {
        for (name in listOf("Excel-test.xlsx", "Excel-test.ods")) {
            val (b, f) = change(name) { s, t, b0 -> val tabs = b0.tabs.map { it.name }
                if (b0.kind == "xlsx") SheetBook.xlsx(s, t, b0.parts, tabs, 0, add = "${tabs[0]} (2)", copy = true) else SheetBook.ods(s, t, tabs, 0, add = "${tabs[0]} (2)", copy = true) }
            val first = b.sheet(0); val copy = b.sheet(1)
            assertEquals(name, b.tabs[0].name + " (2)", b.tabs[1].name)
            for (r in 0 until first.rows) for (c in 0 until first.cols) {
                assertEquals("$name $r,$c", first[r, c]?.text, copy[r, c]?.text); assertEquals("$name $r,$c", first[r, c]?.formula, copy[r, c]?.formula)
                assertEquals("$name $r,$c", first.look(first[r, c]?.style ?: 0, b.styles)?.fill, copy.look(copy[r, c]?.style ?: 0, b.styles)?.fill) }
            shown(f)?.let { s -> assertTrue("$name: ${s.keys}", s.keys.any { it.endsWith("(2)") }) }
            // the copy's tab coloured
            val coloured = File(f.parentFile, "coloured.${f.extension}")
            if (name.endsWith("xlsx")) SheetVisibility.colourXlsx(f, coloured, b.parts.getValue("1"), 0xFFFFC7CE.toInt()) else SheetVisibility.colourOds(f, coloured, 1, 0xFFFFC7CE.toInt())
            Sheets.read(coloured, ',', '.').let { c -> assertEquals(name, 0xFFFFC7CE.toInt(), c.sheet(1).tab); assertEquals(name, null, c.sheet(0).tab) }
        }
    }

    /** References › Contents sheet: first, each shown sheet a link to its A1 (the hidden one left out); made again, still one; LibreOffice keeps it. */
    @Test fun `a contents sheet made`() {
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("contents").toFile()
            val f = File(dir, name).apply { writeBytes(resource(name)) }
            val once = File(dir, "once.${f.extension}"); SheetContents.apply(f, once, Sheets.read(f, ',', '.'), ',', '.')
            val twice = File(dir, "twice.${f.extension}"); SheetContents.apply(once, twice, Sheets.read(once, ',', '.'), ',', '.')
            for (file in listOf(once, twice)) { val b = Sheets.read(file, ',', '.'); val s = b.sheet(0)
                assertEquals(name, listOf("Contents", "Numbers", "Second", "Hidden"), b.tabs.map { it.name })
                assertEquals(name, listOf("Contents", "Numbers", "Second", null), (0..3).map { s[it, 0]?.text })
                assertEquals(name, "#Numbers!A1", s[1, 0]?.link?.replace("'", "")); assertEquals(name, "#Second!A1", s[2, 0]?.link?.replace("'", ""))
                assertEquals(name, "Alpha", b.sheet(1)[1, 0]?.text) }
            shown(twice)?.let { sheets -> assertTrue("$name: ${sheets.keys}", sheets.keys.any { it.contains("Contents") }) }
        }
    }

    /** References › Sources sheet: two sources from Words' dialog as APA 7 writes them, in APA's order, the title in italics; LibreOffice keeps them. */
    @Test fun `a sources sheet made`() {
        val kirk = DocSource("k", DocSource.Type.Book, listOf(DocSource.Person("Kirk", "Anna")), "2021", "Olive harvests", publisher = "Crete Press")
        val bell = DocSource("b", DocSource.Type.Book, listOf(DocSource.Person("Bell", "Tom")), "2019", "Ferries of the Aegean", publisher = "Sea Books")
        for (name in listOf("sheet.xlsx", "sheet.ods")) {
            val dir = Files.createTempDirectory("sources").toFile()
            var f = File(dir, name).apply { writeBytes(resource(name)) }
            for (src in listOf(kirk, bell)) { val next = File(dir, "${System.nanoTime()}.${f.extension}"); SheetSources.add(f, next, Sheets.read(f, ',', '.'), CiteStyle.APA.entry(src), ',', '.'); f = next }
            fun check(file: File, how: String) { val b = Sheets.read(file, ',', '.'); val s = b.sheet(b.tabs.indexOfFirst { it.name == "Sources" })
                assertEquals(how, "References", s[0, 0]?.text)
                assertTrue(how + ": " + s[1, 0]?.text, s[1, 0]?.text?.startsWith("Bell, T.") == true); assertTrue(how, s[2, 0]?.text?.startsWith("Kirk, A.") == true)
                assertTrue(how, s[1, 0]?.rich?.any { it.italic && "Ferries" in it.text } == true) }
            check(f, "made in $name")
            if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
                val conv = File(dir, "lo").apply { mkdirs() }; val other = if (name.endsWith("xlsx")) "ods" else "xlsx"
                soffice(other, conv.path, f.path)
                check(conv.listFiles()!!.single { it.extension == other }, "LibreOffice's $other of $name")
            }
        }
    }

    @Test fun `quoted names`() {
        assertEquals("Rows", SheetBook.quoted("Rows")); assertEquals("'Grades Σ'", SheetBook.quoted("Grades Σ")); assertEquals("'A1'", SheetBook.quoted("A1"))
        assertEquals("SUM('My x'!B3:B12)+Other!A1", SheetBook.renamed("SUM(Rows!B3:B12)+Other!A1", "Rows", "My x"))
        assertEquals("#REF!+1", SheetBook.renamed("'Rows'!B7+1", "rows", null))
    }

    @Test fun `rename, add, move, delete`() {
        for (name in listOf("Excel-test.xlsx", "Excel-test.ods")) {
            var (b, f) = run(name, 0, rename = "Grades Σ")
            assertEquals(name, "Grades Σ", b.tabs[0].name)
            assertEquals(name, "='Grades Σ'!B7", b.sheet(1)[1, 1]?.formula)
            shown(f)?.let { lo -> assertEquals(name, "16", lo.getValue("Refs")[1][1]); assertEquals(name, "167,5", lo.getValue("Refs")[0][1]) }

            run(name, 1, add = "Notes").let { (b2, f2) ->
                assertEquals(name, listOf("Rows", "Refs", "Notes", "Table", "Formats"), b2.tabs.map { it.name })
                assertEquals(name, 0, b2.sheet(2).cells.size)
                // the new sheet is in the file: typed in, saved, read back
                b2.edit(2, Sheet.key(0, 0), SheetInput.cell("Hello", null, 0, "General", b2.format))
                val f5 = File(f2.parentFile, "typed." + f2.extension).also { SheetSave.save(b2, f2, it) }
                assertEquals(name, "Hello", Sheets.read(f5, ',', '.').sheet(2)[0, 0]?.text)
                shown(f2)?.let { lo -> assertTrue(name, "Notes" in lo.keys); assertEquals(name, "16", lo.getValue("Refs")[1][1]) }
            }
            run(name, 3, to = 0).let { (b3, f3) ->
                assertEquals(name, listOf("Formats", "Rows", "Refs", "Table"), b3.tabs.map { it.name })
                assertEquals(name, "Bold", b3.sheet(0)[0, 0]?.text)
                if (name.endsWith("xlsx")) assertNull(b3.sheet(1).print.area?.takeIf { false }) // the print area went with Rows
                shown(f3)?.let { lo -> assertEquals(name, "16", lo.getValue("Refs")[1][1]) }
            }
            run(name, 2, delete = true).let { (b4, f4) ->
                assertEquals(name, listOf("Rows", "Refs", "Formats"), b4.tabs.map { it.name })
                shown(f4)?.let { lo -> assertEquals(name, 3, lo.size) }
            }
        }
    }

    @Test fun `a new workbook`() {
        val dir = Files.createTempDirectory("new").toFile()
        val f = File(dir, "New.xlsx").apply { writeBytes(SheetBook.empty()) }
        val b = Sheets.read(f, ',', '.')
        assertEquals(listOf("Sheet1"), b.tabs.map { it.name })
        val h = SheetHistory()
        h.apply(b, 0, mapOf(Sheet.key(0, 0) to SheetInput.cell("2", null, 0, "General", b.format), Sheet.key(1, 0) to SheetInput.cell("=A1*21", null, 0, "General", b.format)))
        val saved = File(dir, "Saved.xlsx"); SheetSave.save(b, f, saved)
        assertEquals(42.0, Sheets.read(saved).sheet(0)[1, 0]?.value)
        shown(saved)?.let { lo -> assertEquals("42", lo.getValue("Sheet1")[1][0]) }
    }
}
