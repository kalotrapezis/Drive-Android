package com.kalotrapezis.drive

import java.io.File

/** A folder for one test's files (LibreOffice's conversions), gone when the tests end: /tmp is a RAM disk (it filled up, 17 Oct). */
internal fun scratchDir(prefix: String): File = java.nio.file.Files.createTempDirectory(prefix).toFile().also { d ->
    Runtime.getRuntime().addShutdownHook(Thread { d.deleteRecursively() })
}

/** LibreOffice's profile for this test run: its own, never the default one (see [soffice]). */
private val loProfile by lazy { scratchDir("lo-profile") }

/**
 * LibreOffice's conversion of [file] to [to] ("odt", "txt:Text", "csv:…") into [outDir], for the tests that check Tetra's
 * files against it. On this test run's own profile: two runs at once (two worktrees, two sessions) on LibreOffice's default
 * profile lose files — the second hands its job to the first and quits with exit 1 and nothing written (27 Oct, 12 tests
 * failing with a stray "No such file"). A conversion that makes nothing fails the test with LibreOffice's own words.
 */
internal fun soffice(to: String, outDir: String, file: String): Int {
    val p = ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${loProfile.path}", "--convert-to", to, "--outdir", outDir, file)
        .redirectErrorStream(true).start()
    val said = p.inputStream.bufferedReader().readText()
    val code = p.waitFor()
    // its name and the format's extension; a CSV of every sheet (",-1") is name-Sheet.csv, one a sheet
    val stem = File(file).nameWithoutExtension; val ext = to.substringBefore(':')
    val made = File(outDir).listFiles().orEmpty().any { it.name.startsWith(stem) && it.name.endsWith(".$ext") }
    if (code != 0 || !made) throw AssertionError("LibreOffice (exit $code) made no $stem.$ext from ${File(file).name}: ${said.trim()}")
    return code
}
