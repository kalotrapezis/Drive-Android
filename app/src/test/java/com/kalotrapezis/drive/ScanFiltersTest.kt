package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFiltersTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun light(pixel: Int) = ((pixel shr 16 and 0xFF) + (pixel shr 8 and 0xFF) + (pixel and 0xFF)) / 3

    /** Paper shaded from 150 (shadow) to 240 (lit), with black ink at (20,20) and blue ink at (60,60). */
    private fun page(): IntArray = IntArray(100 * 100) { index ->
        val x = index % 100
        val y = index / 100
        val paper = 150 + x * 90 / 99
        when {
            x in 18..22 && y in 18..22 -> rgb(paper / 5, paper / 5, paper / 5)
            x in 58..62 && y in 58..62 -> rgb(paper / 5, paper / 4, paper * 3 / 4)
            else -> rgb(paper, paper, paper)
        }
    }

    @Test fun `fix lighting whitens shaded paper and keeps ink dark`() {
        val pixels = page()
        ScanFilters.apply(pixels, 100, 100, ScanFilter.Lighting)
        assertTrue(light(pixels[50 * 100 + 2]) >= 245)
        assertTrue(light(pixels[50 * 100 + 97]) >= 245)
        assertTrue(light(pixels[20 * 100 + 20]) < 80)
    }

    @Test fun `blue black and white keeps blue ink blue and black ink black`() {
        val pixels = page()
        ScanFilters.apply(pixels, 100, 100, ScanFilter.BlueBlackWhite)
        assertEquals(rgb(255, 255, 255), pixels[50 * 100 + 2])
        assertEquals(rgb(0, 0, 0), pixels[20 * 100 + 20])
        assertEquals(rgb(16, 56, 190), pixels[60 * 100 + 60])
    }

    @Test fun `original leaves pixels untouched`() {
        val pixels = page()
        ScanFilters.apply(pixels, 100, 100, ScanFilter.Original)
        assertEquals(page().toList(), pixels.toList())
    }

    @Test fun `paper swatches go from lit paper to shadow and ignore ink`() {
        val swatches = ScanFilters.paperSwatches(page())
        val lights = swatches.map(::light)
        assertEquals(lights.sortedDescending(), lights)
        assertTrue(lights.last() > 150)
    }

    @Test fun `matched paper tints every page to the same tone`() {
        val tone = rgb(240, 235, 220)
        val first = page()
        val second = IntArray(100 * 100) { rgb(180, 190, 200) }
        ScanFilters.apply(first, 100, 100, ScanFilter.SamePaper, tone)
        ScanFilters.apply(second, 100, 100, ScanFilter.SamePaper, tone)
        assertEquals(tone, first[50 * 100 + 2])
        assertEquals(tone, second[50 * 100 + 50])
    }
}
