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
            .filter { it.isFile && OfficeKind.of(it.name) != null }.toList()
        val failed = ArrayList<String>(); val copyOnly = ArrayList<String>()
        files.forEach { f ->
            val kind = OfficeKind.of(f.name)!!
            runCatching {
                val bytes = f.readBytes()
                val doc = OfficeFormats.read(bytes, kind)
                if (doc.blocking.isNotEmpty()) copyOnly += "${f.relativeTo(root)}: ${doc.blocking}"
                val again = OfficeFormats.read(OfficeFormats.write(doc, kind, bytes), kind)
                check(doc.paras.map { it.text } == again.paras.map { it.text }) { "text changed" }
            }.onFailure { failed += "${f.relativeTo(root)}: $it" }
        }
        println("Sweep: ${files.size} documents, ${failed.size} failed, ${copyOnly.size} copy-only")
        (failed + copyOnly).forEach(::println)
        assertTrue((failed + copyOnly).joinToString("\n"), failed.isEmpty() && copyOnly.isEmpty())
    }
}
