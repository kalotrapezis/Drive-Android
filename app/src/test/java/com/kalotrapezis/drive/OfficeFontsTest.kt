package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The phone's fonts are found by the family name inside their files (asked 17 Oct): read here from Tetra's own. */
class OfficeFontsTest {
    @Test fun `a font file's family name`() {
        assertEquals("Carlito", OfficeFonts.familyName(File("src/main/assets/fonts/Carlito-Regular.ttf")))
        assertEquals("Liberation Serif", OfficeFonts.familyName(File("src/main/assets/fonts/LiberationSerif-BoldItalic.ttf")))
        assertEquals("Caladea", OfficeFonts.familyName(File("src/main/assets/fonts/Caladea-Bold.ttf")))
        // its own letters: Latin and Greek, not Hebrew
        val carlito = File("src/main/assets/fonts/Carlito-Regular.ttf")
        org.junit.Assert.assertTrue(OfficeFonts.covers(carlito, 0, 'a'.code) && OfficeFonts.covers(carlito, 0, 'α'.code))
        org.junit.Assert.assertFalse(OfficeFonts.covers(carlito, 0, 'א'.code))
    }
}
