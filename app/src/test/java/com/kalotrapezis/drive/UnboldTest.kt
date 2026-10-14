package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class UnboldTest {
    @Test fun `a heading's words made not bold and not italic are kept in Word and LibreOffice`() {
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Plain ", unbold = true, unitalic = true), DocRun("bold")), heading = 1)))
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val runs = OfficeFormats.read(OfficeFormats.write(doc, kind), kind).paras.first().runs
            assertEquals(kind.name, listOf(true to true, false to false), runs.map { it.unbold to it.unitalic })
        }
    }
}
