package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSearchRulesTest {
    @Test fun `Greek search finds equivalent local labels`() {
        assertTrue(PhotoSearchRules.matches("θάλασσα", listOf("Beach", "Sky")))
        assertTrue(PhotoSearchRules.matches("βουνό", listOf("Mountain")))
        assertTrue(PhotoSearchRules.matches("πρόσωπο", listOf("Portrait")))
        assertTrue(PhotoSearchRules.matches("ζώο", listOf("Dog")))
        assertFalse(PhotoSearchRules.matches("βουνό", listOf("Beach", "Sky")))
    }

    @Test fun `frequent tags rank real labels`() {
        assertEquals(listOf("Plant", "Sky"), PhotoSearchRules.frequentTags(listOf("Sky", "Plant", "Plant"), 2))
    }
}
