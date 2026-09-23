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

    @Test fun `sharpen ink keeps blue ink blue and drives black ink darker`() {
        val pixels = page()
        ScanFilters.apply(pixels, 100, 100, ScanFilter.SharpInk)
        assertTrue("paper stays paper", light(pixels[50 * 100 + 2]) >= 245)
        assertTrue("black ink gets darker", light(pixels[20 * 100 + 20]) < 40)
        val blue = pixels[60 * 100 + 60]
        assertTrue("blue ink stays blue", (blue and 0xFF) - maxOf(blue shr 16 and 0xFF, blue shr 8 and 0xFF) > 18)
    }

    @Test fun `black and white finds the letters on a faintly printed page`() {
        // Grey-on-white, like a thermal receipt or a tired toner cartridge: every letter sits above the old
        // fixed line of 170, so the page used to come out blank.
        val width = 100
        val height = 100
        val faint = 0xFFB4B4B4.toInt() // 180
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (y in 20..80 && (x / 6) % 2 == 0 && x in 10..90) faint else 0xFFF2F2F2.toInt()
        }
        ScanFilters.apply(pixels, width, height, ScanFilter.BlackWhite)
        assertEquals("the letters are there", rgb(0, 0, 0), pixels[50 * width + 12])
        assertEquals("and the paper is paper", rgb(255, 255, 255), pixels[50 * width + 18])
    }

    @Test fun `an empty page is not turned into noise`() {
        val width = 60
        val height = 60
        // Paper alone, with the slight unevenness any photo has: nothing here is ink.
        val pixels = IntArray(width * height) { index -> val v = 244 - (index / width) / 20; rgb(v, v, v) }
        ScanFilters.apply(pixels, width, height, ScanFilter.BlackWhite)
        assertTrue("stays blank", pixels.all { it == rgb(255, 255, 255) })
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
