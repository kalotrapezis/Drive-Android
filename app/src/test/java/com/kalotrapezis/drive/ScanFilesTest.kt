package com.kalotrapezis.drive

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanFilesTest {
    @Test fun `corner stability accepts small movement and rejects a jump`() {
        val page = DocumentQuad(ScanPoint(.2f, .2f), ScanPoint(.8f, .2f), ScanPoint(.8f, .8f), ScanPoint(.2f, .8f))
        val nearby = page.copy(topLeft = ScanPoint(.21f, .21f))
        val moved = page.copy(topLeft = ScanPoint(.3f, .3f))

        assert(ScanDetection.isStable(page, nearby))
        assert(!ScanDetection.isStable(page, moved))
    }

    @Test fun `corners of a tilted page are ordered clockwise without crossing`() {
        // 45° diamond: x+y ties between corners, which previously produced a twisted crop.
        val diamond = listOf(ScanPoint(.5f, .9f), ScanPoint(.1f, .5f), ScanPoint(.9f, .5f), ScanPoint(.5f, .1f))
        val ordered = ScanDetection.orderCorners(diamond)!!
        val quad = DocumentQuad(ordered[0], ordered[1], ordered[2], ordered[3])
        fun cross(a: ScanPoint, b: ScanPoint, c: ScanPoint) = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        val turns = quad.points.indices.map { cross(quad.points[it], quad.points[(it + 1) % 4], quad.points[(it + 2) % 4]) }
        assert(turns.all { it > 0f }) { "corners must be clockwise: $ordered" }

        val skewed = listOf(ScanPoint(.8f, .75f), ScanPoint(.2f, .1f), ScanPoint(.15f, .85f), ScanPoint(.9f, .2f))
        assertEquals(listOf(ScanPoint(.2f, .1f), ScanPoint(.9f, .2f), ScanPoint(.8f, .75f), ScanPoint(.15f, .85f)), ScanDetection.orderCorners(skewed))
    }

    @Test fun `live outline maps into the rotated capture frame`() {
        // 640x480 buffer, full crop, rotated 90°: buffer top-left becomes photo top-right.
        val quad = DocumentQuad(ScanPoint(.1f, .2f), ScanPoint(.6f, .2f), ScanPoint(.6f, .7f), ScanPoint(.1f, .7f))
        val mapped = ScanDetection.toCaptureFrame(quad, 640, 480, 0, 0, 640, 480, 90)!!
        val expected = listOf(ScanPoint(.3f, .1f), ScanPoint(.8f, .1f), ScanPoint(.8f, .6f), ScanPoint(.3f, .6f))
        mapped.points.zip(expected).forEach { (actual, want) ->
            assertEquals(want.x, actual.x, 1e-4f)
            assertEquals(want.y, actual.y, 1e-4f)
        }
    }

    @Test fun `gap fill paints table at the edges and keeps ink on the page`() {
        val width = 100
        val height = 100
        val table = 0xFF7A5230.toInt()
        val paper = 0xFFDDEEF5.toInt()
        val ink = 0xFF101040.toInt()
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            when {
                x < 3 || y < 2 || (x > 95 && y > 90) -> table // uneven gaps, like a slightly off crop
                x in 48..52 && y in 48..52 -> ink
                else -> paper
            }
        }
        ScanDetection.fillPageGaps(pixels, width, height)

        assertEquals(paper, pixels[0])
        assertEquals(paper, pixels[99 * width + 99])
        assertEquals(ink, pixels[50 * width + 50])
    }

    @Test fun `gap fill leaves an edge that is already paper untouched`() {
        val width = 100
        val height = 100
        // Paper darkening gently toward the bottom edge, plus ink near the edge: a perfect crop.
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val shade = 230 - maxOf(0, y - 80)
            if (x in 40..44 && y in 95..98) 0xFF202020.toInt() else (0xFF shl 24) or (shade shl 16) or (shade shl 8) or shade
        }
        val before = pixels.copyOf()
        ScanDetection.fillPageGaps(pixels, width, height)
        assertEquals(before.toList(), pixels.toList())
    }

    @Test fun `scan names are safe and avoid overwriting an existing PDF`() {
        val folder = Files.createTempDirectory("scans").toFile()
        File(folder, "Invoice.pdf").writeText("first")

        assertEquals("Invoice.pdf", ScanFiles.outputName(" Invoice.pdf "))
        assertEquals("Invoice (2).pdf", ScanFiles.nextAvailable(folder, "Invoice.pdf").name)
    }

    @Test fun `auto capture ring reaches completion after two and a half seconds`() {
        assertEquals(0f, autoCaptureProgress(-1), 0f)
        assertEquals(0.5f, autoCaptureProgress(1_250), 0f)
        assertEquals(1f, autoCaptureProgress(2_500), 0f)
        assertEquals(1f, autoCaptureProgress(9_000), 0f)
    }

    @Test fun `next auto capture is rearmed after two seconds`() {
        assertEquals(2_000L, NEXT_PAGE_REARM_DELAY_MILLIS)
    }

    @Test fun `detection must settle before the capture countdown starts`() {
        assert(!detectionIsSettled(1_000L, 1_649L))
        assert(detectionIsSettled(1_000L, 1_650L))
    }

    @Test fun `crop margin protects a small border around detected paper`() {
        val quad = DocumentQuad(ScanPoint(.2f, .2f), ScanPoint(.8f, .2f), ScanPoint(.8f, .8f), ScanPoint(.2f, .8f))
        val expanded = quad.withCropMargin()

        assert(expanded.topLeft.x < quad.topLeft.x)
        assert(expanded.topLeft.y < quad.topLeft.y)
        assert(expanded.bottomRight.x > quad.bottomRight.x)
        assert(expanded.bottomRight.y > quad.bottomRight.y)
    }

    @Test fun `corner markers remain visible at the preview edge`() {
        assertEquals(10f, ScanPoint(-8f, 300f).clampToViewport(100f, 200f, 10f).x, 0f)
        assertEquals(190f, ScanPoint(80f, 300f).clampToViewport(100f, 200f, 10f).y, 0f)
    }
}
