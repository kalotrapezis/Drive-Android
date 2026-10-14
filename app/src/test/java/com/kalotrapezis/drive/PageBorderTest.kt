package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class PageBorderTest {
    private fun bordered(kind: OfficeKind, b: Border?, sides: String): ByteArray {
        val parts = OfficeFormats.unzip(OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("Bordered page"))))), kind))
        val out = if (kind == OfficeKind.Docx) PageDesign.docx(parts, null, b, sides) else PageDesign.odt(parts, null, b, sides)
        return OfficeFormats.zip(out, if (kind == OfficeKind.Odt) "mimetype" else null)
    }

    @Test fun `a page border's style, colour and sides are kept in Word and LibreOffice`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) for ((b, sides) in listOf(Border(3f, 0xC00000, "double") to "tb", Border(1.5f, 0x0070C0, "dashed") to "tlbr", Border(1f, 0, "dotted") to "l", Border(0.5f, 0x00B050) to "tlbr")) {
            val bytes = bordered(kind, b, sides)
            val read = DocStyles.of(kind, bytes).page.border!!
            assertEquals("$kind $b", b.style, read.line.style)
            assertEquals("$kind $b", b.color, read.line.color)
            assertEquals("$kind $b", b.width, read.line.width, 0.13f)
            assertEquals("$kind $b", sides, read.sides)
        }
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) assertEquals(null, DocStyles.of(kind, bordered(kind, null, "")).page.border)
    }
}
