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
