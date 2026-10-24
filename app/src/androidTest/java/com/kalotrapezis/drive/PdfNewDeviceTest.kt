package com.kalotrapezis.drive

import androidx.compose.ui.geometry.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Office › PDF › New › Blank page (24 Oct): one A4 portrait page, editable, a change saved and read back; a second one
 * does not overwrite the first. Needs MuPDF's native code, so on a device: `am instrument -w -e class com.kalotrapezis.drive.PdfNewDeviceTest …`.
 */
@RunWith(AndroidJUnit4::class)
class PdfNewDeviceTest {
    @Test fun blankA4EditedSavedReopened() {
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-new-test").apply { deleteRecursively() }
        val f = Pdf.newBlank(dir)
        assertEquals("New PDF.pdf", f.name)
        assertEquals("New PDF (2).pdf", Pdf.newBlank(dir).name)
        val d = PdfDoc(f)
        assertTrue(d.editable)
        assertEquals(listOf(Size(595f, 842f)), d.sizes)
        d.text(0, androidx.compose.ui.geometry.Offset(72f, 72f), "Hello Γειά", 14f, 0)
        d.insertBlank(1)
        d.save(java.io.File(dir, "base.pdf"))
        d.close()
        val again = PdfDoc(f)
        assertEquals(2, again.sizes.size)
        again.close()
        dir.deleteRecursively()
    }

    @Test fun pageSizeAndWayRound() {
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-size-test").apply { deleteRecursively() }
        val d = PdfDoc(Pdf.newBlank(dir))
        d.insertBlank(1)
        d.putText(0, 100f, 80f, PdfText("Top left", PdfTextLook(), false), 300f)
        // Layout › Landscape: every page the other way round, what is on it where it was from the top left
        d.resize { s -> if (s.height > s.width) Size(s.height, s.width) else s }
        assertEquals(listOf(Size(842f, 595f), Size(842f, 595f)), d.sizes)
        val (r, _) = d.typedAt(0, androidx.compose.ui.geometry.Offset(110f, 90f))!!
        assertEquals(100f, r.left); assertEquals(80f, r.top)
        // Layout › Size › Letter, each page kept the way round it is
        d.resize { s -> val p = Size(612f, 792f); if (s.width > s.height) Size(p.height, p.width) else p }
        assertEquals(Size(792f, 612f), d.sizes[0])
        assertEquals("Letter landscape", PdfPaper.name(d.sizes[0]))
        d.close(); dir.deleteRecursively()
    }
}
