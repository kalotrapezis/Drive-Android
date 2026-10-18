package com.kalotrapezis.drive

import java.io.File

/** A folder for one test's files (LibreOffice's conversions), gone when the tests end: /tmp is a RAM disk (it filled up, 17 Oct). */
internal fun scratchDir(prefix: String): File = java.nio.file.Files.createTempDirectory(prefix).toFile().also { d ->
    Runtime.getRuntime().addShutdownHook(Thread { d.deleteRecursively() })
}
