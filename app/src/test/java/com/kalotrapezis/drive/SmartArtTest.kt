package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SmartArt shown from the drawing Word saves with it, and kept as it was. */
class SmartArtTest {
    @Test fun `a SmartArt's saved drawing is its pieces, with their words`() {
        val bytes = javaClass.classLoader!!.getResource("smartart.docx").readBytes() // three chevrons: Plan, Write, Submit
        val doc = OfficeFormats.read(bytes, OfficeKind.Docx)
        assertTrue(doc.blocking.toString(), doc.blocking.isEmpty())
        val art = doc.paras.flatMap { p -> p.runs.mapNotNull { it.image } }.single()
        val parts = art.group!!
        // Each shape, then its words in their own rectangle (txXfrm: inside the chevron's body).
        assertEquals(listOf("Plan", "Write", "Submit"), parts.mapNotNull { it.img.box?.single()?.text })
        assertEquals(144f + 600075 / 12700f, parts[3].x, 0.1f)
        assertEquals(144f, parts[2].x, 0.1f)
        val shape = ShapePicture.of(parts[0].img)!!
        assertEquals("chevron", shape.geometry)
        assertEquals(0xFF4472C4.toInt(), shape.fill) // Office's accent 1
        assertEquals(0xFFFFFF, parts[1].img.box!![0].runs[0].color) // its words in the theme's light colour
        assertEquals(DocAlign.Center, parts[1].img.box!![0].align)
        // Saved: the SmartArt as it was, every part of it there.
        val back = OfficeFormats.unzip(OfficeFormats.write(doc, OfficeKind.Docx, bytes))
        assertTrue(String(back.getValue("word/document.xml"), Charsets.UTF_8).contains("r:dm=\"rId10\""))
        assertTrue(listOf("data1", "layout1", "quickStyle1", "colors1", "drawing1").all { "word/diagrams/$it.xml" in back })
    }
}
