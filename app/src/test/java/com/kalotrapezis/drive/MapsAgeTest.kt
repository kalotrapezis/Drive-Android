package com.kalotrapezis.drive

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Maps' country details: a map's age (remade on the computer after 3 months), left-hand roundabouts, names in Tetra's language. */
class MapsAgeTest {
    @Test fun oldAfterThreeMonths() {
        val built = LocalDate.of(2026, 7, 27)
        assertFalse(MapsFiles.isOld(built, LocalDate.of(2026, 10, 26)))
        assertTrue(MapsFiles.isOld(built, LocalDate.of(2026, 10, 27)))
        assertTrue(MapsFiles.isOld(LocalDate.of(2026, 11, 30), LocalDate.of(2027, 2, 28))) // 30 Nov + 3 months: the end of February
        assertFalse(MapsFiles.isOld(LocalDate.of(2026, 11, 30), LocalDate.of(2027, 2, 27)))
    }

    @Test fun builtFromManifestElseFile() {
        val root = Files.createTempDirectory("maps").toFile()
        File(root, "cyprus.pmtiles").writeText("x")
        File(root, "cyprus.json").writeText("""{"name":"Cyprus","built":"2026-05-02","drive":"left"}""")
        assertEquals(LocalDate.of(2026, 5, 2), MapsFiles.built(root, "cyprus"))
        // Greece's older pack: no manifest, its file's date
        val greece = File(root, "greece.pmtiles").apply { writeText("x"); setLastModified(java.time.LocalDate.of(2026, 6, 15).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) }
        assertEquals(LocalDate.of(2026, 6, 15), MapsFiles.built(root, greece.nameWithoutExtension))
        // the side traffic keeps to, at a route's point
        File(root, "cyprus.json").writeText("""{"name":"Cyprus","built":"2026-05-02","bounds":[32.2,34.5,34.7,35.8],"drive":"left"}""")
        val r = Route("car", doubleArrayOf(35.17, 37.98), doubleArrayOf(33.36, 23.73), 0.0, 0, emptyList())
        assertTrue(MapsFiles.driveLeftAt(root, r, 0))
        assertFalse(MapsFiles.driveLeftAt(root, r, 1))
    }

    @Test fun ringClockwiseWhereTrafficKeepsLeft() {
        // in from the bottom (180°), out to the right (90°): the first exit where traffic keeps right, a quarter anticlockwise;
        // three quarters clockwise where it keeps left
        assertEquals(90f to -90f, ringSweep(180.0, 90.0, false))
        assertEquals(90f to 270f, ringSweep(180.0, 90.0, true))
        // out to the left (-90°): the other way about
        assertEquals(90f to -270f, ringSweep(180.0, -90.0, false))
        assertEquals(90f to 90f, ringSweep(180.0, -90.0, true))
        // back the way it came: once round, each its own way
        assertEquals(-360f, ringSweep(180.0, 180.0, false).second)
        assertEquals(360f, ringSweep(180.0, 180.0, true).second)
    }

    @Test fun labelsInTetrasLanguage() {
        val style = File("src/main/assets/maps/light.json").readText()
        assertEquals(style, labelsIn(style, "el")) // Greek unchanged
        val de = labelsIn(style, "de")
        assertFalse("name:el" in de); assertFalse("\"Greek\"" in de)
        assertTrue("[\"get\",\"name:de\"]" in de); assertTrue("\"Latin\"" in de)
        assertTrue("\"Cyrillic\"" in labelsIn(style, "bg"))
        org.json.JSONObject(de) // still a style
    }
}
