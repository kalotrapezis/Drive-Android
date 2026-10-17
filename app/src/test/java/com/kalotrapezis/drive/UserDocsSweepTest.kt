package com.kalotrapezis.drive

import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every one of the user's documents (~/Tetra/Files, not Κλειδιά) read, written and read again: none may fail or be
 * copy-only. Runs only when asked (`TETRA_SWEEP=1 ./gradlew testDebugUnitTest --tests '*UserDocsSweepTest*'`) — it reads
 * the user's own files, which a build elsewhere does not have. Nothing is written beside them.
 */
class UserDocsSweepTest {
    @Test fun `the user's documents read and write`() {
        assumeTrue(System.getenv("TETRA_SWEEP") == "1")
        val root = File(System.getProperty("user.home"), "Tetra/Files")
        val files = root.walkTopDown().onEnter { it.name != "Κλειδιά 🔑" && !it.name.startsWith("Κλειδιά") }
            .filter { it.isFile && (OfficeKind.of(it.name) != null || LegacyDocs.isLegacy(it.name)) }.toList()
        val failed = ArrayList<String>(); val copyOnly = ArrayList<String>()
        files.forEach { f ->
            // An older file (.doc, .rtf) is read by Writer's own readers and saved as a .docx (always beside it).
            val legacy = LegacyDocs.isLegacy(f.name)
            val kind = OfficeKind.of(f.name) ?: OfficeKind.Docx
            runCatching {
                val bytes = f.readBytes()
                val doc = if (legacy) LegacyDocs.read(bytes, f.name) else OfficeFormats.read(bytes, kind)
                if ((doc.blocking - LegacyDocs.OLDER).isNotEmpty()) copyOnly += "${f.relativeTo(root)}: ${doc.blocking}"
                val again = OfficeFormats.read(OfficeFormats.write(doc, kind, bytes.takeIf { !legacy }), kind)
                check(doc.paras.map { it.text } == again.paras.map { it.text }) { "text changed" }
            }.onFailure { failed += "${f.relativeTo(root)}: $it" }
        }
        println("Sweep: ${files.size} documents, ${failed.size} failed, ${copyOnly.size} copy-only")
        (failed + copyOnly).forEach(::println)
        assertTrue((failed + copyOnly).joinToString("\n"), failed.isEmpty() && copyOnly.isEmpty())
    }

    /**
     * One of the user's documents as the test list has it (TETRA_FILE, its path): opened, a new empty line after its first
     * paragraph (Enter), saved into TETRA_OUT — never over it — for LibreOffice to show beside the original.
     */
    @Test fun `one of the user's documents edited and saved`() {
        val path = System.getenv("TETRA_FILE"); val out = System.getenv("TETRA_OUT")
        assumeTrue(path != null && out != null)
        val f = File(path!!); val kind = OfficeKind.of(f.name)!!
        val bytes = f.readBytes()
        val doc = OfficeFormats.read(bytes, kind)
        val at = doc.paras.indexOfFirst { it.table == null && it.text.isNotBlank() }.coerceAtLeast(0)
        val edited = doc.copy(paras = doc.paras.toMutableList().apply { add(at + 1, DocPara(emptyList())) })
        File(out!!).writeBytes(OfficeFormats.write(edited, kind, bytes))
        val again = OfficeFormats.read(File(out).readBytes(), kind)
        assertTrue("the new line is there", again.paras.size == doc.paras.size + 1)
    }

    /** Every one of the user's decks read, saved unchanged into a temporary file (never beside it) and read again: the same slides and words. */
    @Test fun `the user's decks read and save`() {
        assumeTrue(System.getenv("TETRA_SWEEP") == "1")
        val root = File(System.getProperty("user.home"), "Tetra/Files")
        val files = root.walkTopDown().onEnter { !it.name.startsWith("Κλειδιά") }
            .filter { it.isFile && it.extension.lowercase() in setOf("pptx", "odp") }.toList()
        val failed = ArrayList<String>()
        fun words(d: Deck) = d.slides.map { s -> s.items.filterIsInstance<SlideItem.Shape>().filter { !it.hint }.mapNotNull { it.text?.paras?.joinToString("|") { p -> p.runs.joinToString("") { r -> r.text } } } }
        files.forEach { f ->
            runCatching {
                val deck = Slides.read(f)
                val out = File.createTempFile("sweep", "." + f.extension.lowercase())
                try {
                    if (f.extension.equals("odp", true)) SlidesOdpEdit.save(f, deck, SlidesEdit.start(deck), out) else SlidesEdit.save(f, deck, SlidesEdit.start(deck), out)
                    val again = Slides.read(out)
                    check(again.slides.size == deck.slides.size) { "${deck.slides.size} slides, ${again.slides.size} after saving" }
                    check(words(again) == words(deck)) { "words changed" }
                    check(again.slides.map { it.steps.size } == deck.slides.map { it.steps.size }) { "animations changed" }
                    // LibreOffice opens what was saved: as many pages as it makes of the file itself (hidden ones left out of both)
                    if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
                        fun pages(file: File): Int {
                            val dir = java.nio.file.Files.createTempDirectory("lo").toFile()
                            val copy = File(dir, "deck." + f.extension.lowercase()).also { file.copyTo(it) }
                            ProcessBuilder("soffice", "--headless", "--convert-to", "pdf", "--outdir", dir.path, copy.path).redirectErrorStream(true).start().waitFor()
                            return File(dir, "deck.pdf").takeIf { it.exists() }?.readBytes()?.let { Regex("/Type\\s*/Page[^s]").findAll(String(it, Charsets.ISO_8859_1)).count() } ?: -1
                        }
                        val a = pages(f); val b = pages(out)
                        // a file LibreOffice itself cannot open (Google Slides' emoji halves, 17 Oct): the saved one must open
                        check(b > 0 && (a == b || a < 0)) { "LibreOffice: $a pages from the file, $b from the saved one" }
                    }
                } finally { out.delete() }
            }.onFailure { failed += "${f.relativeTo(root)}: $it" }
        }
        println("Sweep: ${files.size} decks, ${failed.size} failed")
        failed.forEach(::println)
        assertTrue(failed.joinToString("\n"), failed.isEmpty())
    }
}
