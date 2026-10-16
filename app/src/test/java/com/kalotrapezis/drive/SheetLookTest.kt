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

    @Test fun xlsx() = format("Excel-test.xlsx")
    @Test fun ods() = format("Excel-test.ods")
}
