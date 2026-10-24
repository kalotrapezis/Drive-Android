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
}
