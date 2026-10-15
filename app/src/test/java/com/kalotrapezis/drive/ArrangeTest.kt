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

    @Test fun `LibreOffice's own group is read piece by piece and kept`() {
        val bytes = javaClass.classLoader!!.getResource("group.odt").readBytes() // a red and a blue rectangle, grouped, through LibreOffice 26.2
        val doc = OfficeFormats.read(bytes, OfficeKind.Odt)
        assertTrue(doc.blocking.toString(), doc.blocking.isEmpty())
        val g = images(doc).single()
        val parts = g.group!!
        assertEquals(2, parts.size)
        assertEquals(0f, parts[0].x, 0.1f); assertEquals(4.001f * 28.3465f, parts[1].x, 0.2f); assertEquals(2f * 28.3465f, parts[1].y, 0.2f)
        assertEquals(7f * 28.3465f, g.widthPt, 0.3f) // 2 cm … 9 cm
        assertEquals(2f * 28.3465f, PictureXml.placeOf(g)!!.x!!, 0.2f)
        val xml = String(OfficeFormats.unzip(OfficeFormats.write(doc, OfficeKind.Odt, bytes)).getValue("content.xml"), Charsets.UTF_8)
        assertTrue(Regex("<draw:g [^>]*>(<draw:custom-shape[\\s\\S]*?</draw:custom-shape>){2}</draw:g>").containsMatchIn(xml))
    }

    @Test fun `two floating shapes grouped in LibreOffice are one draw-g, and come apart where they were`() {
        val a = shape(OfficeKind.Odt, "a", 100f, 100f)
        val b = shape(OfficeKind.Odt, "b", 250f, 180f, 0xFFFF0000.toInt())
        val g = Arrange.odtGroup(listOf(a to floatArrayOf(100f, 100f, 100f, 60f), b to floatArrayOf(250f, 180f, 100f, 60f)), PictureWrap.InFront, 3, "g")!!
        assertTrue(g.raw!!, g.raw!!.startsWith("<draw:g") && "Tetra_20_wrap_20_InFront_20_page" in g.raw!!)
        assertEquals(250f, g.widthPt, 0.01f); assertEquals(140f, g.heightPt, 0.01f)
        assertEquals(100f to 100f, PictureXml.placeOf(g)!!.let { it.x!! to it.y!! }.let { (x, y) -> Math.round(x).toFloat() to Math.round(y).toFloat() })
        val bytes = OfficeFormats.write(OfficeDoc(listOf(para(g))), OfficeKind.Odt)
        val xml = String(OfficeFormats.unzip(bytes).getValue("content.xml"), Charsets.UTF_8)
        // Both pieces' own looks and the group's wrap are styles in the file.
        assertEquals(2, Regex("""<style:style style:name="Tetra_20_shape_""").findAll(xml).count())
        assertTrue("""style:name="Tetra_20_wrap_20_InFront_20_page"""" in xml)
        val read = OfficeFormats.read(bytes, OfficeKind.Odt)
        assertTrue(read.blocking.isEmpty())
        val back = images(read).single()
        assertEquals(2, back.group!!.size)
        assertEquals(150f, back.group!![1].x, 0.2f); assertEquals(80f, back.group!![1].y, 0.2f)
        // Moved by Picture › Position: both pieces go.
        val moved = PictureXml.odt(back.raw!!, PictureWrap.InFront, 300f to 400f)
        assertEquals(300f, Arrange.odtGroupBox(moved)!![0], 0.2f); assertEquals(400f, Arrange.odtGroupBox(moved)!![1], 0.2f)
        assertEquals(250f, Arrange.odtGroupBox(moved)!![2], 0.2f)
        val apart = Arrange.odtUngroup(back, 100f, 100f, PictureWrap.InFront, 3)!!
        assertEquals(listOf(100f to 100f, 250f to 180f), apart.map { PictureXml.placeOf(it)!!.let { p -> Math.round(p.x!!).toFloat() to Math.round(p.y!!).toFloat() } })
        val again = images(saved(OfficeDoc(listOf(para(*apart.toTypedArray()))), OfficeKind.Odt))
        assertEquals(2, again.size)
        assertTrue(again.all { it.group == null && it.raw!!.startsWith("<draw:custom-shape") })
    }
}
