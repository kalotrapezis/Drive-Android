package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Formatting on Excel-test (.xlsx and LibreOffice's .ods of it): bold, a fill, a border round a range, centring, a
 * currency format — saved, read back by Tetra, and by LibreOffice (as shown, and its own copy of the file read back).
 */
class SheetLookTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name).readBytes()
    private val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0

    private fun convert(file: File, to: String): File? {
        if (!lo) return null
        val out = Files.createTempDirectory("lo").toFile()
        ProcessBuilder("soffice", "--headless", "--convert-to", to, "--outdir", out.path, file.path).redirectErrorStream(true).start().let { it.inputStream.readBytes(); it.waitFor() }
        return out.listFiles()!!.firstOrNull { it.name.endsWith(to.substringBefore(':')) || it.extension == to.substringBefore(':') }
            ?: out.listFiles()!!.firstOrNull()
    }

    private fun format(name: String) {
        val dir = Files.createTempDirectory("look").toFile()
        val file = File(dir, name).apply { writeBytes(resource(name)) }
        val book = Sheets.read(file, ',', '.')
        val s = book.sheet(0); val history = SheetHistory()
        fun look(keys: Collection<Long>, change: (SheetStyle, Long) -> SheetStyle) = history.apply(book, 0, SheetLook.change(book, s, keys, change))
        val k = { r: Int, c: Int -> Sheet.key(r, c) }
        look(listOf(k(2, 0))) { st, _ -> st.copy(bold = true) }                                          // A3 bold
        look(listOf(k(3, 0), k(4, 0))) { st, _ -> st.copy(fill = 0xFFFFFF00.toInt()) }                   // A4:A5 yellow
        val box = SheetRange(2, 1, 4, 3); val thin = SheetLine(0xFF000000.toInt(), 1f)
        look((2..4).flatMap { r -> (1..3).map { c -> k(r, c) } }) { st, key -> SheetLook.edges(st, key, box, thin, all = false) } // B3:D5 outside
        look((2..11).map { k(it, 1) }) { st, _ -> st.copy(format = "#,##0.00 \"€\"") }                  // B3:B12 euros
        look(listOf(k(1, 1))) { st, _ -> st.copy(align = SheetAlign.Center, italic = true) }             // B2 centred, italic
        assertEquals("10,00 €", s[2, 1]?.text)
        val saved = File(dir, "saved." + name.substringAfterLast('.'))
        SheetSave.save(book, file, saved)
        fun check(f: File, who: String) {
            val b = Sheets.read(f, ',', '.'); val t = b.sheet(0)
            fun st(r: Int, c: Int) = t.look(t[r, c]!!.style, b.styles)!!
            assertTrue(who, st(2, 0).bold); assertFalse(who, st(3, 0).bold)
            assertEquals(who, 0xFFFFFF00.toInt(), st(3, 0).fill); assertEquals(who, 0xFFFFFF00.toInt(), st(4, 0).fill)
            assertTrue(who, st(2, 1).top != null && st(2, 1).left != null && st(2, 1).right == null && st(2, 1).bottom == null)
            assertTrue(who, st(4, 3).bottom != null && st(4, 3).right != null && st(4, 3).top == null)
            assertTrue(who, st(3, 2).let { it.top == null && it.left == null && it.right == null && it.bottom == null })
            assertEquals(who, SheetAlign.Center, st(1, 1).align); assertTrue(who, st(1, 1).italic)
            assertEquals(who, "10,00 €", t[2, 1]?.text); assertEquals(who, "23,50 €", t[11, 1]?.text)
            assertEquals(who, "Αλεξίου", t[2, 0]?.text); assertEquals(who, 20.0, t[2, 2]?.value) // values as they were
        }
        check(saved, "$name by Tetra")
        // LibreOffice's own copy of the saved file, read back: it understood the looks
        convert(saved, if (name.endsWith("ods")) "xlsx" else "ods")?.let { check(it, "$name by LibreOffice") }
    }

    /** Excel's cell styles: Good, Heading 1, Note and Total put on, saved, read back by Tetra and LibreOffice; a number format kept. */
    private fun cellStyles(name: String) {
        val dir = Files.createTempDirectory("styles").toFile()
        val file = File(dir, name).apply { writeBytes(resource(name)) }
        val book = Sheets.read(file, ',', '.'); val s = book.sheet(0); val history = SheetHistory()
        fun put(r: Int, c: Int, style: String) = history.apply(book, 0, SheetLook.change(book, s, listOf(Sheet.key(r, c))) { st, _ ->
            SheetLook.styled(st, SheetLook.STYLES.first { it.name == style }, book.styles[0]) })
        history.apply(book, 0, SheetLook.change(book, s, listOf(Sheet.key(0, 0))) { st, _ -> st.copy(fill = 0xFFFFFF00.toInt()) })
        // a heading: font and border, the cell's fill kept (as Excel's)
        put(2, 0, "Good"); put(0, 0, "Heading 1"); put(3, 0, "Note"); put(11, 1, "Total"); put(4, 0, "Good"); put(4, 0, "Normal")
        val saved = File(dir, "styled." + name.substringAfterLast('.')); SheetSave.save(book, file, saved)
        fun check(f: File, who: String) {
            val b = Sheets.read(f, ',', '.'); val t = b.sheet(0)
            fun st(r: Int, c: Int) = t.look(t[r, c]!!.style, b.styles)!!
            // LibreOffice 26.2's .xlsx of an .ods whose Default style has a theme colour gives every cell that colour (it shows ours)
            val colours = who != "Excel-test.ods by LibreOffice"
            assertEquals(who, 0xFFC6EFCE.toInt(), st(2, 0).fill); if (colours) assertEquals(who, 0xFF006100.toInt(), st(2, 0).color)
            assertTrue(who, st(0, 0).bold); assertEquals(who, 15f, st(0, 0).size); assertEquals(who, 0xFF4472C4.toInt(), st(0, 0).bottom?.color); assertEquals(who, 0xFFFFFF00.toInt(), st(0, 0).fill)
            assertEquals(who, 0xFFFFFFCC.toInt(), st(3, 0).fill); assertTrue(who, st(3, 0).let { it.left != null && it.top != null && it.right != null && it.bottom != null })
            assertTrue(who, st(11, 1).bold); assertTrue(who, st(11, 1).bottom?.double == true); assertTrue(who, st(11, 1).top != null)
            assertEquals(who, null, st(4, 0).fill); assertEquals(who, false, st(4, 0).bold) // Normal: back to the workbook's own
            assertEquals(who, "Αλεξίου", t[2, 0]?.text)
        }
        check(saved, "$name by Tetra")
        convert(saved, if (name.endsWith("ods")) "xlsx" else "ods")?.let { check(it, "$name by LibreOffice") }
    }
    /** Home's alignment: top and middle, an indent, text angled up and down — saved, read back by Tetra and by LibreOffice. */
    private fun alignment(name: String) {
        val dir = Files.createTempDirectory("align").toFile()
        val file = File(dir, name).apply { writeBytes(resource(name)) }
        val book = Sheets.read(file, ',', '.'); val s = book.sheet(0); val history = SheetHistory()
        fun put(r: Int, c: Int, change: (SheetStyle) -> SheetStyle) = history.apply(book, 0, SheetLook.change(book, s, listOf(Sheet.key(r, c))) { st, _ -> change(st) })
        put(2, 0) { it.copy(valign = SheetVAlign.Top) }; put(3, 0) { it.copy(valign = SheetVAlign.Center, indent = 2, align = SheetAlign.Left) }
        put(4, 0) { it.copy(rotation = 45) }; put(5, 0) { it.copy(rotation = 135) }
        val saved = File(dir, "aligned." + name.substringAfterLast('.')); SheetSave.save(book, file, saved)
        fun check(f: File, who: String) {
            val b = Sheets.read(f, ',', '.'); val t = b.sheet(0)
            fun st(r: Int, c: Int) = t.look(t[r, c]!!.style, b.styles)!!
            assertEquals(who, SheetVAlign.Top, st(2, 0).valign); assertEquals(who, SheetVAlign.Center, st(3, 0).valign); assertEquals(who, 2, st(3, 0).indent)
            assertEquals(who, 45, st(4, 0).rotation); assertEquals(who, 135, st(5, 0).rotation); assertEquals(who, 0, st(6, 0).rotation)
        }
        check(saved, "$name by Tetra")
        convert(saved, if (name.endsWith("ods")) "xlsx" else "ods")?.let { check(it, "$name by LibreOffice") }
    }
    @Test fun `alignment xlsx`() = alignment("Excel-test.xlsx")
    @Test fun `alignment ods`() = alignment("Excel-test.ods")

    @Test fun `cell styles xlsx`() = cellStyles("Excel-test.xlsx")
    @Test fun `cell styles ods`() = cellStyles("Excel-test.ods")

    @Test fun xlsx() = format("Excel-test.xlsx")
    @Test fun ods() = format("Excel-test.ods")
}
