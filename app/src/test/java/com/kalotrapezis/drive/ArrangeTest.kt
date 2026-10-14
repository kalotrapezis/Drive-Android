package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangeTest {
    private fun shape(kind: OfficeKind, key: String, x: Float, y: Float, color: Int = 0xFF4472C4.toInt()) =
        PictureXml.placed(DocImage(key, null, 100f, 60f, PictureXml.newShape("rect", kind, color, 0xFF000000.toInt(), 1f, 100f, 60f)), x, y, kind)

    private fun images(doc: OfficeDoc) = doc.paras.flatMap { p -> p.runs.mapNotNull { it.image } }
    private fun saved(doc: OfficeDoc, kind: OfficeKind) = OfficeFormats.read(OfficeFormats.write(doc, kind), kind)
    private fun para(vararg imgs: DocImage) = DocPara(listOf(DocRun("Text ")) + imgs.map { DocRun(OBJECT_MARK.toString(), image = it) })

    @Test fun `a floating shape turned in Word and LibreOffice keeps its angle and its place`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val s = shape(kind, "a", 150f, 200f)
            val turned = Arrange.rotated(s, 30f)!!
            assertEquals(kind.name, 30f, Arrange.rotationOf(turned), 0.01f)
            // Turning about its middle does not move it: its unturned corner is where it was.
            val before = PictureXml.placeOf(s)!!; val after = PictureXml.placeOf(turned)!!
            assertEquals(kind.name, before.x!!, after.x!!, 0.1f); assertEquals(kind.name, before.y!!, after.y!!, 0.1f)
            val back = images(saved(OfficeDoc(listOf(para(turned))), kind)).single()
            assertEquals(kind.name, 30f, Arrange.rotationOf(back), 0.01f)
            // Straightened: no angle left, the place as it was.
            val straight = Arrange.rotated(turned, 0f)!!
            assertEquals(kind.name, 0f, Arrange.rotationOf(straight), 0.01f)
            assertEquals(kind.name, before.x!!, PictureXml.placeOf(straight)!!.x!!, 0.1f)
        }
        // Word: 60000ths of a degree, clockwise.
        assertTrue(Arrange.rotated(shape(OfficeKind.Docx, "b", 0f, 0f), -90f)!!.raw!!.contains("rot=\"16200000\""))
    }

    @Test fun `two floating shapes grouped in Word are one drawing, and come apart where they were`() {
        val a = shape(OfficeKind.Docx, "a", 100f, 100f)
        val b = Arrange.rotated(shape(OfficeKind.Docx, "b", 250f, 180f, 0xFFFF0000.toInt()), 45f)!!
        val g = Arrange.group(listOf(a to floatArrayOf(100f, 100f, 100f, 60f), b to floatArrayOf(250f, 180f, 100f, 60f)), PictureWrap.InFront, 251659300, "g")!!
        assertEquals(250f, g.widthPt, 0.01f); assertEquals(140f, g.heightPt, 0.01f)
        assertEquals(100f to 100f, PictureXml.placeOf(g)!!.let { it.x to it.y })
        val read = saved(OfficeDoc(listOf(para(g))), OfficeKind.Docx)
        assertTrue(read.blocking.toString(), read.blocking.isEmpty())
        val back = images(read).single()
        val parts = back.group!!
        assertEquals(2, parts.size)
        assertEquals(150f, parts[1].x, 0.1f); assertEquals(80f, parts[1].y, 0.1f); assertEquals(100f, parts[1].w, 0.1f)
        assertEquals(45f, Arrange.rotationOf(parts[1].img), 0.01f) // each piece keeps its own angle
        assertTrue(parts[0].img.raw!!.contains("prst=\"rect\""))
        val apart = Arrange.ungroup(back, 100f, 100f, PictureWrap.InFront, 251659300)!!
        assertEquals(listOf(100f to 100f, 250f to 180f), apart.map { PictureXml.placeOf(it)!!.let { p -> p.x!! to p.y!! } })
        assertEquals(45f, Arrange.rotationOf(apart[1]), 0.01f)
        val again = images(saved(OfficeDoc(listOf(para(*apart.toTypedArray()))), OfficeKind.Docx))
        assertEquals(2, again.size)
        assertTrue(again.all { it.group == null && it.raw!!.contains("<wps:spPr>") })
    }
}
