package com.kalotrapezis.drive

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun `corner markers remain visible at the preview edge`() {
        assertEquals(10f, ScanPoint(-8f, 300f).clampToViewport(100f, 200f, 10f).x, 0f)
        assertEquals(190f, ScanPoint(80f, 300f).clampToViewport(100f, 200f, 10f).y, 0f)
    }
}

/** The geometry the finder leans on: what counts as a page, and where a torn edge's straight line ran. */
class ScanDetectionGeometryTest {
    private fun quad(vararg xy: Float) = DocumentQuad(
        ScanPoint(xy[0], xy[1]), ScanPoint(xy[2], xy[3]), ScanPoint(xy[4], xy[5]), ScanPoint(xy[6], xy[7]),
    )

    @Test fun `a page is convex with four honest corners`() {
        assertTrue(ScanDetection.isPageShaped(quad(0.1f, 0.1f, 0.9f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f)))
        // Seen at an angle a page is a trapezoid, and that has to stay acceptable.
        assertTrue(ScanDetection.isPageShaped(quad(0.3f, 0.1f, 0.7f, 0.1f, 0.95f, 0.9f, 0.05f, 0.9f)))
        // A sliver with a spike for a corner is a shadow or a tile edge, not paper.
        assertFalse(ScanDetection.isPageShaped(quad(0.1f, 0.5f, 0.5f, 0.48f, 0.9f, 0.5f, 0.5f, 0.52f)))
        // Bow-tie: the same four points, crossed over.
        assertFalse(ScanDetection.isPageShaped(quad(0.1f, 0.1f, 0.9f, 0.9f, 0.9f, 0.1f, 0.1f, 0.9f)))
    }

    @Test fun `a torn edge is cut where the straight edge ran`() {
        // A rectangle whose top edge has a flap torn upwards in the middle third.
        val corners = listOf(ScanPoint(0.2f, 0.2f), ScanPoint(0.8f, 0.2f), ScanPoint(0.8f, 0.8f), ScanPoint(0.2f, 0.8f))
        val outline = buildList {
            add(ScanPoint(0.2f, 0.2f))
            add(ScanPoint(0.4f, 0.2f))
            add(ScanPoint(0.5f, 0.14f)) // the tear
            add(ScanPoint(0.6f, 0.2f))
            add(ScanPoint(0.8f, 0.2f))
            for (y in listOf(0.4f, 0.6f)) add(ScanPoint(0.8f, y))
            add(ScanPoint(0.8f, 0.8f))
            for (x in listOf(0.6f, 0.4f)) add(ScanPoint(x, 0.8f))
            add(ScanPoint(0.2f, 0.8f))
            for (y in listOf(0.6f, 0.4f)) add(ScanPoint(0.2f, y))
        }
        val straightened = ScanDetection.straighten(outline, corners)!!
        // The cut runs along the paper's own edge, not up over the flap, and not down into the page either.
        straightened.take(2).forEach { corner -> assertEquals(0.2f, corner.y, 0.02f) }
        assertEquals(0.2f, straightened[0].x, 0.02f)
        assertEquals(0.8f, straightened[1].x, 0.02f)
    }

    @Test fun `otsu splits paper from table wherever the light puts them`() {
        val histogram = IntArray(256)
        repeat(3) { histogram[60 + it] = 1000 }   // the table
        repeat(3) { histogram[200 + it] = 1000 }  // the paper
        val threshold = ScanDetection.otsu(histogram, 6000)
        assertTrue("splits between the two, was $threshold", threshold in 61..201)
        // The same scene under a flash: everything brighter, and a fixed floor of 170 would keep nothing.
        val lit = IntArray(256)
        repeat(3) { lit[180 + it] = 1000 }
        repeat(3) { lit[250 + it] = 1000 }
        assertTrue(ScanDetection.otsu(lit, 6000) in 181..251)
    }

    @Test fun `the outline follows a real move at once and ignores a shiver`() {
        val page = quad(0.2f, 0.2f, 0.8f, 0.2f, 0.8f, 0.8f, 0.2f, 0.8f)
        val shivered = quad(0.21f, 0.2f, 0.8f, 0.21f, 0.8f, 0.8f, 0.2f, 0.79f)
        val smoothed = ScanDetection.smooth(page, shivered)
        assertEquals(0.2045f, smoothed.topLeft.x, 0.001f) // most of the way back to where it was
        val moved = quad(0.5f, 0.5f, 0.95f, 0.5f, 0.95f, 0.95f, 0.5f, 0.95f)
        assertEquals(0.5f, ScanDetection.smooth(page, moved).topLeft.x, 0.0001f)
    }
}

class ScanFrameTest {
    @Test fun `a shape running off the edge is not the page you were asked to frame`() {
        val inside = DocumentQuad(ScanPoint(0.1f, 0.1f), ScanPoint(0.9f, 0.1f), ScanPoint(0.9f, 0.9f), ScanPoint(0.1f, 0.9f))
        assertTrue(ScanDetection.isInsideFrame(inside))
        val acrossTheView = DocumentQuad(ScanPoint(0f, 0.4f), ScanPoint(1f, 0.4f), ScanPoint(1f, 0.6f), ScanPoint(0f, 0.6f))
        assertFalse(ScanDetection.isInsideFrame(acrossTheView))
    }
}

class ScanGapFillTest {
    /** A crop that left a deep bite of table along one edge — deeper than the band the fill works in. */
    @Test fun `the colour comes from the paper, never from what the crop left behind`() {
        val width = 120
        val height = 120
        val table = 0xFF7A5230.toInt()
        val paper = 0xFFEFF3F6.toInt()
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (y < 20 && x in 30..90) table else paper // a bite 20 deep, band is 7
        }
        ScanDetection.fillPageGaps(pixels, width, height)
        // The fill reaches as far as its band, and what it paints there is the paper — before, it sampled its
        // colour from inside the bite and so painted the table back over itself, changing nothing.
        for (x in 30..90) for (y in 0 until 7) {
            assertEquals("row $y column $x kept the table", paper, pixels[y * width + x])
        }
    }

    @Test fun `ink in the middle never becomes the colour of the page`() {
        val width = 100
        val height = 100
        val paper = 0xFFF0F0F0.toInt()
        val ink = 0xFF101010.toInt()
        // A wide band of ink across the middle: the paper colour must still be the paper, not a grey average of
        // the two. It stops short of the edges, because ink that runs off the page cannot be told from a gap.
        val pixels = IntArray(width * height) { index ->
            if ((index / width) in 40..60 && (index % width) in 20..80) ink else paper
        }
        val before = pixels.copyOf()
        ScanDetection.fillPageGaps(pixels, width, height)
        assertEquals(before.toList(), pixels.toList())
    }
}

class ScanTextCropTest {
    private val page = DocumentQuad(
        ScanPoint(0.2f, 0.2f), ScanPoint(0.8f, 0.2f), ScanPoint(0.8f, 0.8f), ScanPoint(0.2f, 0.8f),
    )

    @Test fun `letters well inside let the cut move into the paper, away from the table`() {
        val text = listOf(ScanPoint(0.3f, 0.3f), ScanPoint(0.7f, 0.3f), ScanPoint(0.7f, 0.7f), ScanPoint(0.3f, 0.7f))
        val cut = ScanDetection.fitToLetters(page, text)
        assertTrue("the top steps inside the paper", cut.topLeft.y > 0.2f)
        // 1.5% of a 0.6-tall page is 0.009 — a step that actually lands on paper, not a rounding error.
        assertEquals(0.209f, cut.topLeft.y, 0.002f)
        assertTrue("and every side does the same", cut.bottomLeft.y < 0.8f && cut.topLeft.x > 0.2f && cut.topRight.x < 0.8f)
    }

    @Test fun `letters close to the edge push the cut off the paper, for the fill to paint`() {
        // A page printed to its own edge: the first line is 0.005 from the paper, well under the margin it needs.
        val text = listOf(ScanPoint(0.3f, 0.205f), ScanPoint(0.7f, 0.205f), ScanPoint(0.7f, 0.5f), ScanPoint(0.3f, 0.5f))
        val cut = ScanDetection.fitToLetters(page, text)
        assertTrue("the top goes outside the paper", cut.topLeft.y < 0.2f)
        // 1.5% of a 0.6-tall page is 0.009, and the letters start at 0.205.
        assertEquals(0.205f - 0.009f, cut.topLeft.y, 0.002f)
        assertTrue("the bottom, with nothing near it, still steps in", cut.bottomLeft.y < 0.8f)
    }

    @Test fun `a letter already outside the crop is taken back in`() {
        val text = listOf(ScanPoint(0.3f, 0.17f), ScanPoint(0.7f, 0.17f), ScanPoint(0.7f, 0.5f), ScanPoint(0.3f, 0.5f))
        val cut = ScanDetection.fitToLetters(page, text)
        assertTrue("past the letters, not just up to them", cut.topLeft.y < 0.17f)
    }

    @Test fun `a page with nothing readable on it is left alone`() {
        assertEquals(page, ScanDetection.fitToLetters(page, emptyList()))
    }
}

class ScanAimAndTrimTest {
    @Test fun `only a shape you are pointing at counts as the page`() {
        val aimed = DocumentQuad(ScanPoint(0.2f, 0.2f), ScanPoint(0.8f, 0.2f), ScanPoint(0.8f, 0.8f), ScanPoint(0.2f, 0.8f))
        assertTrue(ScanDetection.coversCentre(aimed))
        // A laptop lid off to one side, page-shaped and bright, but not what the camera is aimed at.
        val besideIt = DocumentQuad(ScanPoint(0.05f, 0.1f), ScanPoint(0.4f, 0.1f), ScanPoint(0.4f, 0.9f), ScanPoint(0.05f, 0.9f))
        assertFalse(ScanDetection.coversCentre(besideIt))
    }

    @Test fun `the crop is cut back to where the paper starts, and never through the text`() {
        val width = 200
        val height = 200
        val table = 0xFF6A4A28.toInt()
        val paper = 0xFFF1F1EE.toInt()
        val ink = 0xFF141414.toInt()
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            when {
                y < 9 || x < 6 -> table                                    // a band the outline kept
                y in 30..34 && x in 20..180 -> ink                         // a line of text
                else -> paper
            }
        }
        val trim = ScanDetection.trimToPaper(pixels, width, height)
        assertEquals("the band on the left goes", 6, trim[0])
        assertEquals("and the one along the top", 9, trim[1])
        assertEquals("nothing is taken from a side that was already paper", 0, trim[2])
        assertEquals(0, trim[3])
    }

    @Test fun `a page with no band around it is left exactly as it is`() {
        val width = 120
        val height = 120
        val pixels = IntArray(width * height) { index ->
            if ((index / width) in 40..44) 0xFF111111.toInt() else 0xFFEFEFEF.toInt()
        }
        assertEquals(listOf(0, 0, 0, 0), ScanDetection.trimToPaper(pixels, width, height).toList())
    }
}

class ScanFillRestraintTest {
    private val paper = 0xFFF0F0EC.toInt()

    @Test fun `a printed line running to the very edge is not eaten`() {
        val width = 200
        val height = 200
        // An invoice ruled to its own border: black lines touch every edge, and none of it is background.
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (x in 40..42 || y in 60..62) 0xFF101010.toInt() else paper
        }
        val before = pixels.copyOf()
        ScanDetection.fillPageGaps(pixels, width, height)
        assertEquals("the page is left exactly as it was", before.toList(), pixels.toList())
    }

    @Test fun `a corner of table is painted out, in the paper's own colour`() {
        val width = 200
        val height = 200
        val table = 0xFF6B4A2A.toInt()
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (x < 10 && y < 10) table else paper // within the 6% band the fill is allowed to reach
        }
        ScanDetection.fillPageGaps(pixels, width, height)
        assertEquals("the corner goes", paper, pixels[0])
        assertEquals(paper, pixels[9 * width + 9])
        assertTrue("and what replaces it is one flat colour, not a streak", pixels.all { it == paper })
    }
}
