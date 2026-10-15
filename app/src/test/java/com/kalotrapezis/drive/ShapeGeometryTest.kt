package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Custom shapes from their geometry: Word's 187 presets and LibreOffice's enhanced paths. */
class ShapeGeometryTest {
    private fun bounds(subs: List<ShapeGeometry.Sub>): FloatArray {
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        subs.flatMap { it.ops }.forEach { op -> when (op) {
            is ShapeGeometry.Op.Move -> { xs += op.x; ys += op.y }; is ShapeGeometry.Op.Line -> { xs += op.x; ys += op.y }
            is ShapeGeometry.Op.Cubic -> { xs += op.x; ys += op.y }; is ShapeGeometry.Op.Quad -> { xs += op.x; ys += op.y }; else -> Unit } }
        return floatArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
    }

    @Test fun `all of Word's presets are read and drawn`() {
        val all = ShapeGeometry.presets
        assertEquals(187, all.size)
        // A sheet of them all, to look at (build/shapes.svg).
        val svg = StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='1700' height='${(all.size / 10 + 1) * 120}'><rect width='100%' height='100%' fill='white'/>")
        all.keys.sorted().forEachIndexed { i, name ->
            val x = (i % 10) * 170 + 20; val y = (i / 10) * 120 + 10
            all.getValue(name).paths(110f, 70f).forEach { sub ->
                val d = sub.ops.joinToString(" ") { op -> when (op) { is ShapeGeometry.Op.Move -> "M${op.x} ${op.y}"; is ShapeGeometry.Op.Line -> "L${op.x} ${op.y}"
                    is ShapeGeometry.Op.Cubic -> "C${op.x1} ${op.y1} ${op.x2} ${op.y2} ${op.x} ${op.y}"; is ShapeGeometry.Op.Quad -> "Q${op.x1} ${op.y1} ${op.x} ${op.y}"; else -> "Z" } }
                val fill = if (!sub.fill) "none" else if (sub.shade < 0) "#2F5597" else if (sub.shade > 0) "#9DC3E6" else "#4472C4"
                svg.append("<path transform='translate($x,$y)' d='$d' fill='$fill' stroke='${if (sub.stroke) "#1F3864" else "none"}' stroke-width='1'/>")
            }
            svg.append("<text x='$x' y='${y + 100}' font-size='11' font-family='sans-serif'>$name</text>")
        }
        java.io.File("build").mkdirs(); java.io.File("build/shapes.svg").writeText(svg.append("</svg>").toString())
        for ((name, g) in all) {
            val subs = g.paths(200f, 100f)
            assertTrue(name, subs.isNotEmpty() && subs.all { s -> s.ops.isNotEmpty() })
            val b = bounds(subs)
            assertTrue("$name ${b.toList()}", b.all { it.isFinite() } && b[0] > -400 && b[2] < 600 && b[1] > -400 && b[3] < 500)
        }
    }

    @Test fun `a rectangle, an ellipse and adjustments`() {
        val rect = ShapeGeometry.presets.getValue("rect").paths(100f, 50f).single().ops
        assertEquals(listOf(ShapeGeometry.Op.Move(0f, 0f), ShapeGeometry.Op.Line(100f, 0f), ShapeGeometry.Op.Line(100f, 50f), ShapeGeometry.Op.Line(0f, 50f), ShapeGeometry.Op.Close), rect)
        val ellipse = bounds(ShapeGeometry.presets.getValue("ellipse").paths(100f, 50f))
        assertEquals(0f, ellipse[0], 0.01f); assertEquals(100f, ellipse[2], 0.01f); assertEquals(0f, ellipse[1], 0.01f); assertEquals(50f, ellipse[3], 0.01f)
        // A rounded rectangle's corner by its adjustment (Word's adj, 16667 = a sixth of the short side).
        val round = ShapeGeometry.presets.getValue("roundRect").paths(120f, 60f).single().ops.first() as ShapeGeometry.Op.Move
        assertEquals(10f, round.y, 0.1f) // it starts down the left side, past the corner
        val rounder = ShapeGeometry.presets.getValue("roundRect").paths(120f, 60f, mapOf("adj" to 50000.0)).single().ops.first() as ShapeGeometry.Op.Move
        assertEquals(30f, rounder.y, 0.1f)
        // A can: its top a lighter face of its own.
        assertTrue(ShapeGeometry.presets.getValue("can").paths(60f, 100f).any { it.shade != 0f })
    }

    @Test fun `a LibreOffice shape from its own enhanced path`() {
        val xml = """<draw:enhanced-geometry svg:viewBox="0 0 21600 21600" draw:type="mso-spt100" draw:modifiers="5400" draw:enhanced-path="M ?f0 0 L 21600 0 21600 21600 0 21600 Z N"><draw:equation draw:name="f0" draw:formula="${'$'}0 *2"/></draw:enhanced-geometry>"""
        val g = ShapeGeometry.ofOdf(xml)!!
        val ops = g.paths(216f, 108f).single().ops
        assertEquals(ShapeGeometry.Op.Move(108f, 0f), ops[0])
        assertEquals(ShapeGeometry.Op.Line(0f, 108f), ops[3])
        // An ellipse by angles (U).
        val e = ShapeGeometry.ofOdf("""<draw:enhanced-geometry svg:viewBox="0 0 21600 21600" draw:enhanced-path="U 10800 10800 10800 10800 0 360 Z N"/>""")!!
        val b = bounds(e.paths(100f, 100f))
        assertEquals(0f, b[0], 0.1f); assertEquals(100f, b[2], 0.1f)
    }

    @Test fun `a preset written as LibreOffice's geometry reads back the same`() {
        val dir = java.io.File("build/office-roundtrip").apply { mkdirs() }
        val xml = StringBuilder()
        for ((i, name) in listOf("heart", "sun", "cloud", "can", "smileyFace", "wave", "star12", "flowChartMagneticDisk").withIndex()) {
            val geo = ShapeGeometry.odfGeometry(name, 113f, 85f)!!
            val back = ShapeGeometry.ofOdf(geo)!!
            fun flat(subs: List<ShapeGeometry.Sub>) = subs.flatMap { it.ops }.joinToString { "%.1f".format(java.util.Locale.ROOT, (it as? ShapeGeometry.Op.Move)?.x ?: (it as? ShapeGeometry.Op.Line)?.x ?: (it as? ShapeGeometry.Op.Cubic)?.x ?: 0f) }
            assertEquals(name, flat(ShapeGeometry.presets.getValue(name).paths(113f, 85f)), flat(back.paths(113f, 85f)))
            xml.append("""<draw:custom-shape draw:style-name="grT" text:anchor-type="char" svg:width="4cm" svg:height="3cm" svg:x="${1 + (i % 4) * 4.5}cm" svg:y="${8 + (i / 4) * 4}cm" draw:z-index="$i"><text:p/>$geo</draw:custom-shape>""")
        }
        // An .odt of them, for a look in LibreOffice.
        val doc = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("Shapes"))))), OfficeKind.Odt)
        val parts = OfficeFormats.unzip(doc)
        var c = String(parts.getValue("content.xml"), Charsets.UTF_8)
        c = c.replaceFirst(">Shapes<", ">" + xml + "Shapes<").replace("</office:automatic-styles>", """<style:style style:name="grT" style:family="graphic"><style:graphic-properties draw:fill="solid" draw:fill-color="#4472c4" style:wrap="run-through" style:run-through="foreground" style:vertical-pos="from-top" style:vertical-rel="page" style:horizontal-pos="from-left" style:horizontal-rel="page"/></style:style></office:automatic-styles>""")
        if ("xmlns:draw=" !in c.substring(0, 2000)) c = c.replaceFirst("<office:document-content", "<office:document-content xmlns:draw=\"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0\" xmlns:svg=\"urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0\"")
        parts["content.xml"] = c.toByteArray()
        dir.resolve("shapes.odt").writeBytes(OfficeFormats.zip(parts, "mimetype"))
    }

    @Test fun `any preset is put in, restyled and described, in both formats`() {
        for (kind in OfficeKind.entries) for (name in listOf("heart", "sun", "flowChartDecision", "wedgeRectCallout")) {
            val img = PictureXml.placed(DocImage("k", null, 72f, 72f, PictureXml.newShape(name, kind, 0xFF4472C4.toInt(), 0xFF000000.toInt(), 1f, 72f, 72f)), 100f, 100f, kind)
            val shape = ShapePicture.of(img)!!
            assertEquals("$kind $name", name, shape.geometry)
            val red = PictureXml.shapeLook(img, 0xFFE53935.toInt(), null, 2f)!!
            val described = PictureXml.withAlt(red, "A red \"$name\"")!!
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("x"), DocRun(OBJECT_MARK.toString(), image = described)))))
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("shape-$name.${kind.extension}").writeBytes(OfficeFormats.write(doc, kind)) // for a look in LibreOffice
            val back = OfficeFormats.read(OfficeFormats.write(doc, kind), kind).paras[0].runs.mapNotNull { it.image }.single()
            assertTrue(OfficeFormats.read(OfficeFormats.write(doc, kind), kind).blocking.isEmpty())
            val again = ShapePicture.of(back)!!
            assertEquals("$kind $name", 0xFFE53935.toInt(), again.fill)
            assertEquals("$kind $name", null, again.line)
            assertEquals("$kind $name", "A red \"$name\"", PictureXml.altOf(back))
            assertEquals("$kind $name", name, again.geometry)
        }
    }

    @Test fun `a Word shape coloured by its theme`() {
        val raw = """<w:drawing><wp:inline><wp:extent cx="914400" cy="914400"/><wp:docPr id="1" name="Heart 1"/><a:graphic><a:graphicData><wps:wsp><wps:spPr><a:prstGeom prst="heart"><a:avLst/></a:prstGeom></wps:spPr>""" +
            """<wps:style><a:lnRef idx="2"><a:schemeClr val="accent2"><a:shade val="50000"/></a:schemeClr></a:lnRef><a:fillRef idx="1"><a:schemeClr val="accent2"/></a:fillRef></wps:style></wps:wsp></a:graphicData></a:graphic></wp:inline></w:drawing>"""
        val s = ShapePicture.of(raw)!!
        assertEquals(0xFFED7D31.toInt(), s.fill)
        assertEquals(0xFF763E18.toInt(), s.line)
    }
}
