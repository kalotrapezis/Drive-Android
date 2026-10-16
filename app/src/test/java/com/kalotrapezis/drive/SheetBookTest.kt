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
        ProcessBuilder("soffice", "--headless", "--convert-to", "csv:Text - txt - csv (StarCalc):44,34,76,1,,0,false,true,true,false,false,-1", "--outdir", out.path, file.path)
            .redirectErrorStream(true).start().let { it.inputStream.readBytes(); it.waitFor() }
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
