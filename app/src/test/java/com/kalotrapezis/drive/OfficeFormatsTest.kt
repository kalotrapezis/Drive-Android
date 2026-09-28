package com.kalotrapezis.drive

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeFormatsTest {
    private val sample = OfficeDoc(listOf(
        DocPara(listOf(DocRun("Title")), heading = 1),
        DocPara(listOf(DocRun("Plain, "), DocRun("bold", bold = true), DocRun(" and "), DocRun("red italic", italic = true, color = 0xFF0000),
            DocRun(" big", sizePt = 18f), DocRun(" marked", highlight = 0xFFFF00), DocRun(" a link", link = "https://example.com/?a=1&b=2"))),
        DocPara(listOf(DocRun("Centered  two spaces\tand a tab", underline = true, strike = true)), align = DocAlign.Center),
        DocPara(listOf(DocRun("one")), list = DocList(true, 0, 1)),
        DocPara(listOf(DocRun("inner")), list = DocList(false, 1)),
        DocPara(listOf(DocRun("two")), list = DocList(true, 0, 2)),
        DocPara(listOf(DocRun("Between"), DocRun("\u2028second line", bold = true))),
        DocPara(listOf(DocRun("five")), list = DocList(true, 0, 5)),
        DocPara(listOf(DocRun("Ελληνικά <&> \"quotes\"")), align = DocAlign.Justify),
        DocPara(listOf(DocRun("x"), DocRun("2", script = 1), DocRun(" H"), DocRun("2", script = -1, sizePt = 10f), DocRun("O"))),
    ))

    private fun roundTrip(kind: OfficeKind) {
        val bytes = OfficeFormats.write(sample, kind)
        val back = OfficeFormats.read(bytes, kind)
        assertEquals(emptySet<String>(), back.unsupported)
        // Read back, a list item also knows the file's own list it belongs to; that aside, the same.
        assertEquals(sample.paras, back.paras.map { it.copy(keep = null) })
        // Written into itself again: nothing drifts.
        val again = OfficeFormats.read(OfficeFormats.write(back, kind, bytes), kind)
        assertEquals(back.paras, again.paras)
    }

    @Test fun `a new docx reads back as written`() = roundTrip(OfficeKind.Docx)
    @Test fun `a new odt reads back as written`() = roundTrip(OfficeKind.Odt)

    @Test fun `a docx keeps its other parts and its tables`() {
        val parts = OfficeFormats.unzip(OfficeFormats.write(sample, OfficeKind.Docx))
        parts["word/header1.xml"] = "<hdr/>".toByteArray()
        val xml = String(parts.getValue("word/document.xml")).replace("</w:body>", "").replace("<w:sectPr", "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>cell</w:t></w:r></w:p></w:tc></w:tr></w:tbl><w:sectPr") + ""
        parts["word/document.xml"] = xml.replace("</w:document>", "</w:body></w:document>").toByteArray()
        val withTable = OfficeFormats.zip(parts)
        val doc = OfficeFormats.read(withTable, OfficeKind.Docx)
        assertEquals(emptySet<String>(), doc.unsupported)
        assertEquals("cell", doc.paras.last().table!!.rows.single().cells.single().paras.single().text)
        val saved = OfficeFormats.unzip(OfficeFormats.write(doc, OfficeKind.Docx, withTable))
        assertEquals("<hdr/>", String(saved.getValue("word/header1.xml")))
        assertTrue("<w:pgSz" in String(saved.getValue("word/document.xml")))
        assertEquals(doc.paras.last().table, OfficeFormats.read(OfficeFormats.zip(LinkedHashMap(saved)), OfficeKind.Docx).paras.last().table)
    }

    @Test fun `an odt starts with its stored mimetype`() {
        val bytes = OfficeFormats.write(sample, OfficeKind.Odt)
        assertEquals("mimetype", String(bytes, 30, 8))
        assertEquals("application/vnd.oasis.opendocument.text", String(bytes, 38, 39))
    }

    @Test fun `a paragraph's own formatting is carried through edits`() {
        val keep = DocKeep("Quote", listOf("spacing" to "<w:spacing w:after=\"0\"/>"))
        val old = listOf(DocPara(listOf(DocRun("A"))), DocPara(listOf(DocRun("B")), keep = keep), DocPara(listOf(DocRun("C"))))
        // B edited, a paragraph typed after it, C unchanged.
        val new = listOf(DocPara(listOf(DocRun("A"))), DocPara(listOf(DocRun("B, edited"))), DocPara(listOf(DocRun("new"))), DocPara(listOf(DocRun("C"))))
        val carried = OfficeFormats.carryKeep(old, new)
        assertEquals(listOf(null, keep, keep, null), carried.map { it.keep })
        // A page break before a paragraph is not handed on to the next one.
        val broken = DocKeep(null, listOf("pageBreakBefore" to "<w:pageBreakBefore/>"))
        assertEquals(null, OfficeFormats.carryKeep(listOf(DocPara(listOf(DocRun("X")), keep = broken)), listOf(DocPara(listOf(DocRun("X"))), DocPara(listOf(DocRun("Y")))))[1].keep)
    }

    @Test fun `a picture added in Writer is saved into the file`() {
        val png = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(java.awt.image.BufferedImage(4, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        for (kind in OfficeKind.entries) {
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Before "), DocRun(OBJECT_MARK.toString(), image = DocImage("n1", null, 120f, 60f, bytes = png)), DocRun(" after")))))
            val bytes = OfficeFormats.write(doc, kind)
            val back = OfficeFormats.read(bytes, kind)
            assertEquals(kind.name, emptySet<String>(), back.unsupported)
            assertEquals(kind.name, "Before $OBJECT_MARK after", back.paras.single().text)
            val img = back.paras.single().runs.single { it.image != null }.image!!
            assertTrue(kind.name, img.part!!.endsWith("tetra-1.png"))
            assertTrue(kind.name, png.contentEquals(OfficeFormats.unzip(bytes).getValue(img.part!!)))
            assertEquals(kind.name, 120f, img.widthPt, 0.1f)
            assertEquals(kind.name, 60f, img.heightPt, 0.1f)
            // Saved again, the picture goes back as the file now has it.
            assertEquals(kind.name, back.paras, OfficeFormats.read(OfficeFormats.write(back, kind, bytes), kind).paras)
        }
    }

    @Test fun `a picture's size and wrapping are saved, and can go back in line`() {
        val png = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(java.awt.image.BufferedImage(4, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        for (kind in OfficeKind.entries) {
            fun roundTrip(img: DocImage, original: ByteArray?): Pair<DocImage, ByteArray> {
                val bytes = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("A "), DocRun(OBJECT_MARK.toString(), image = img))))), kind, original)
                return OfficeFormats.read(bytes, kind).paras.single().runs.single { it.image != null }.image!! to bytes
            }
            val (first, bytes) = roundTrip(DocImage("n1", null, 120f, 60f, bytes = png), null)
            assertTrue(PictureXml.editable(first))
            for (wrap in PictureWrap.entries.reversed()) {
                val (back, _) = roundTrip(PictureXml.resized(first, 200f, 100f).with(wrap = wrap), bytes)
                assertEquals("$kind $wrap", 200f, back.widthPt, 0.1f)
                assertEquals("$kind $wrap", 100f, back.heightPt, 0.1f)
                if (kind == OfficeKind.Docx) assertEquals(wrap, PictureXml.wrapOf(back))
                else assertTrue(back.raw!!.contains(PictureXml.odtStyleName(wrap)))
                // A floating picture put back in line.
                val (inline, _) = roundTrip(back.with(wrap = PictureWrap.InLine), bytes)
                assertEquals("$kind $wrap", PictureWrap.InLine, PictureXml.wrapOf(inline))
            }
        }
    }

    @Test fun `each letter keeps its font through edits`() {
        val f = "<w:rFonts w:ascii=\"Arial\"/>"
        val old = listOf(DocPara(listOf(DocRun("Hello wo"), DocRun("rld", font = f))))
        // Typed inside the word in another font: the new letters take the font of the letter before them.
        val typed = OfficeFormats.carryFonts(old, listOf(DocPara(listOf(DocRun("Hello big worlds")))))
        assertEquals(listOf(DocRun("Hello big wo"), DocRun("rlds", font = f)), typed.single().runs)
        // A paragraph added after it starts in the font the previous one ended with.
        val added = OfficeFormats.carryFonts(old, listOf(DocPara(listOf(DocRun("Hello world"))), DocPara(listOf(DocRun("next")))))
        assertEquals(listOf(DocRun("next", font = f)), added[1].runs)
    }

    /** Word's List Bullet / List Number styles number their paragraphs themselves (the user's test suite, file 01). */
    @Test fun `a list numbered by its paragraph style reads as a list, and taking it off stays off`() {
        val f = File(System.getProperty("user.home"), "Έγγραφα/Claude/Projects/Defaulty/Word App Test Suite/01_Text_and_Paragraphs.docx")
        if (!f.isFile) return
        val bytes = f.readBytes()
        val doc = OfficeFormats.read(bytes, OfficeKind.Docx)
        val bullet = doc.paras.indexOfFirst { it.text == "Bullet one" }
        val numbered = doc.paras.indexOfFirst { it.text == "Numbered item two" }
        assertEquals(DocList(false, 0), doc.paras[bullet].list)
        assertEquals(true, doc.paras[numbered].list?.ordered)
        assertEquals(doc.paras.map { it.list }, OfficeFormats.read(OfficeFormats.write(doc, OfficeKind.Docx, bytes), OfficeKind.Docx).paras.map { it.list })
        val off = doc.copy(paras = doc.paras.mapIndexed { i, p -> if (i == bullet) p.copy(list = null) else p })
        val back = OfficeFormats.read(OfficeFormats.write(off, OfficeKind.Docx, bytes), OfficeKind.Docx)
        assertEquals(null, back.paras[bullet].list)
        assertEquals(doc.paras[bullet + 1].list, back.paras[bullet + 1].list)
    }

    /** The user's own documents on this computer, when they are here: each must read without an error. */
    @Test fun `real documents read`() {
        val home = File(System.getProperty("user.home"), "Έγγραφα")
        // And the Word test suite the user made (28 Sep): every feature of Word, one file per area.
        val suite = File(home, "Claude/Projects/Defaulty/Word App Test Suite").listFiles().orEmpty().filter { OfficeKind.of(it.name) != null }
        (home.walkTopDown().maxDepth(3).filter { OfficeKind.of(it.name) != null }.take(40) + suite).forEach { f ->
            val kind = OfficeKind.of(f.name)!!
            val bytes = f.readBytes()
            val doc = OfficeFormats.read(bytes, kind)
            println("${f.name}: ${doc.paras.size} paragraphs; not kept: ${doc.unsupported.ifEmpty { setOf("—") }.joinToString()}")
            // Written back (into memory only) and read again: the same text.
            val written = OfficeFormats.write(doc, kind, bytes)
            // Kept for comparing by eye with LibreOffice (build/office-roundtrip); never beside the originals.
            File("build/office-roundtrip").apply { mkdirs() }.resolve(f.name).writeBytes(written)
            val again = OfficeFormats.read(written, kind)
            assertEquals(f.name, doc.paras.map { it.text }, again.paras.map { it.text })
            // Each paragraph's own formatting comes back as it was read; tables, cell by cell.
            assertEquals(f.name, doc.paras.map { it.keep }, again.paras.map { it.keep })
            assertEquals(f.name, doc.paras.mapNotNull { it.table }, again.paras.mapNotNull { it.table })
        }
    }
}
