package com.kalotrapezis.drive

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

/** The PDF viewer's pinch (the point under the fingers stays there, kept to the edges) and how sharp a zoomed page is drawn. */
class PdfViewTest {
    private val view = Size(1000f, 2000f)

    @Test fun pinchKeepsThePointUnderTheFingers() {
        // twice as big about a point 100 px right of and 200 px below the middle: that point is where it was
        val at = Offset(100f, 200f)
        val (z, o) = Pdf.zoomed(1f, Offset.Zero, 2f, at, Offset.Zero, view)
        assertEquals(2f, z)
        assertEquals(Offset(-100f, -200f), o)
        // screen = middle + offset + zoom × (content − middle): the content point at `at` maps back to `at`
        assertEquals(at, o + at * z)
    }

    @Test fun zoomAndPanAreKeptInBounds() {
        val (z, o) = Pdf.zoomed(4f, Offset.Zero, 3f, Offset.Zero, Offset(10_000f, -10_000f), view)
        assertEquals(Pdf.MAX_ZOOM, z)
        assertEquals(Offset(2000f, -4000f), o) // (5 − 1) / 2 of the view each way
        // out past 1×: back to the page as it was, nothing left over
        assertEquals(1f to Offset.Zero, Pdf.zoomed(2f, Offset(300f, 300f), 0.25f, Offset.Zero, Offset(50f, 50f), view))
    }

    @Test fun zoomedPagesDrawSharperWithinMemory() {
        assertEquals(1000, Pdf.renderWidth(1000, 1f, 0.7f))
        assertEquals(1500, Pdf.renderWidth(1000, 1.3f, 0.7f)) // half steps: 1.3 → 1.5
        assertEquals((4096 * 0.7f).toInt(), Pdf.renderWidth(1000, 5f, 0.7f)) // an upright page: its height at most 4096
        assertEquals(4096, Pdf.renderWidth(1000, 5f, 1.4f)) // a sideways page: its width
    }

    @Test fun pdfsByName() {
        assert(Pdf.isPdf("Scan 2026-10-17.PDF") && !Pdf.isPdf("notes.pdf.txt"))
        assert(OfficeOpen.isOffice("a.pdf") && OfficeOpen.extensionOf("application/pdf") == "pdf")
    }
}
