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
    @Test fun `comments are read, kept, followed through editing, added and taken off, in both kinds`() {
        val suite = File(System.getProperty("user.home"), "Έγγραφα/Claude/Projects/Defaulty/Word App Test Suite/05_Review_and_Forms.docx")
        if (suite.isFile) {
            val bytes = suite.readBytes()
            val doc = OfficeFormats.read(bytes, OfficeKind.Docx)
            assert("comments" !in doc.unsupported) { doc.unsupported.toString() }
            val c = doc.comments.single()
            assertEquals("Test Reviewer", c.author)
            assertEquals("specific phrase", doc.paras.flatMap { it.runs }.filter { c.id in it.comments }.joinToString("") { it.text })
            val again = OfficeFormats.read(OfficeFormats.write(doc, OfficeKind.Docx, bytes), OfficeKind.Docx)
            assertEquals(doc.comments, again.comments)
            assertEquals("specific phrase", again.paras.flatMap { it.runs }.filter { c.id in it.comments }.joinToString("") { it.text })
        }
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val base = OfficeDoc(listOf(DocPara(listOf(DocRun("Review this specific phrase and go on."))), DocPara(listOf(DocRun("Second paragraph.")))))
            val id = OfficeFormats.newCommentId(base, kind)
            val note = DocComment(id, "Θεολόγος", "2026-09-29T18:00:00Z", "Check this.\nΚαι αυτό.", initials = "Θ")
            // From "specific" on, across into the next paragraph's first word.
            val doc = OfficeFormats.withComment(base, listOf(Triple(0, 12, 38), Triple(1, 0, 6)), note)
            val bytes = OfficeFormats.write(doc, kind)
            File("build/office-roundtrip").apply { mkdirs() }.resolve("comments.${kind.extension}").writeBytes(bytes)
            val back = OfficeFormats.read(bytes, kind)
            assertEquals(kind.name, emptySet<String>(), back.unsupported)
            val read = back.comments.single()
            assertEquals(kind.name, listOf("Θεολόγος", "Check this.\nΚαι αυτό."), listOf(read.author, read.text))
            assertEquals(kind.name, listOf("specific phrase and go on.", "Second"), back.paras.map { p -> p.runs.filter { read.id in it.comments }.joinToString("") { it.text } })
            // Typing inside it joins it; at its edge, not; the words around are untouched.
            val typed = listOf(DocPara(listOf(DocRun("Review this! specific new phrase and go on."))), back.paras[1])
            val carried = OfficeFormats.carryFonts(back.paras, typed)
            assertEquals(kind.name, "specific new phrase and go on.", carried[0].runs.filter { read.id in it.comments }.joinToString("") { it.text })
            // Two stretches of one comment (never made by Writer): written as one — the first — not started twice.
            val split = OfficeFormats.withComment(base, listOf(Triple(0, 12, 27), Triple(1, 0, 6)), note)
            val splitBack = OfficeFormats.read(OfficeFormats.write(split, kind), kind)
            assertEquals(kind.name, listOf("specific phrase", ""), splitBack.paras.map { p -> p.runs.filter { it.comments.isNotEmpty() }.joinToString("") { it.text } })
            // Its letters all deleted: the comment is not written again.
            val gone = back.copy(paras = listOf(DocPara(listOf(DocRun("Review this "))), DocPara(listOf(DocRun(" paragraph.")))))
            val carriedGone = gone.copy(paras = OfficeFormats.carryFonts(back.paras, gone.paras))
            assertEquals(kind.name, emptyList<DocComment>(), OfficeFormats.read(OfficeFormats.write(carriedGone, kind, bytes), kind).comments)
            // Taken off by hand: the letters stay, the comment goes.
            val off = OfficeFormats.withoutComment(back, read.id)
            val offBack = OfficeFormats.read(OfficeFormats.write(off, kind, bytes), kind)
            assertEquals(kind.name, emptyList<DocComment>(), offBack.comments)
            assertEquals(kind.name, back.paras.map { it.text }, offBack.paras.map { it.text })
        }
    }

    /** Forms (the test suite's 05): text, drop-down and date content controls read, filled in, written back as Word's. */
    @Test fun `a form's content controls are kept and filled in`() {
        val f = File(System.getProperty("user.home"), "Έγγραφα/Claude/Projects/Defaulty/Word App Test Suite/05_Review_and_Forms.docx")
        if (!f.isFile) return
        val bytes = f.readBytes()
        val doc = OfficeFormats.read(bytes, OfficeKind.Docx)
        assert("content controls" !in doc.unsupported) { doc.unsupported.toString() }
        val controls = doc.paras.flatMap { it.runs }.mapNotNull { r -> r.control?.let { it to r.text } }
        assertEquals(listOf(Forms.Kind.Text, Forms.Kind.List, Forms.Kind.Date), controls.map { Forms.kind(it.first) })
        assertEquals(listOf("Name", "Status", "Date"), controls.map { Forms.name(it.first) })
        assertEquals(listOf("Ready", "Review", "Done"), Forms.items(controls[1].first))
        val (name, status, date) = controls.map { it.first }
        val d = java.time.LocalDate.of(2026, 10, 1)
        var filled = Forms.fill(doc, name, name, "Θεολόγος")
        filled = Forms.fill(filled, status, status, "Done")
        filled = Forms.fill(filled, date, Forms.dateTo(date, d), Forms.dateText(date, d))
        // The editor gives the letters back without their controls: carried again.
        val bare = filled.paras.map { p -> p.copy(runs = OfficeFormats.tidy(p.runs.map { it.copy(control = null, comments = emptyList(), change = null) })) }
        assertEquals(3, OfficeFormats.carryFonts(filled.paras, bare).flatMap { it.runs }.mapNotNull { it.control }.distinct().size)
        val out = OfficeFormats.write(filled, OfficeKind.Docx, bytes)
        File("build/office-roundtrip").apply { mkdirs() }.resolve("forms.docx").writeBytes(out)
        val back = OfficeFormats.read(out, OfficeKind.Docx).paras.flatMap { it.runs }.mapNotNull { r -> r.control?.let { it to r.text } }
        assertEquals(listOf("Θεολόγος", "Done", "2026-10-01"), back.map { it.second })
        assertEquals(d, Forms.date(back[2].first))
        // Typing at a field's end fills it; the words around stay outside.
        val p = doc.paras.indexOfFirst { p -> p.runs.any { it.control == name } }
        val typed = doc.paras[p].text.replace("Enter a test name", "Enter a test name!")
        val carried = OfficeFormats.carryFonts(doc.paras, listOf(doc.paras[p].copy(runs = listOf(DocRun(typed)))).let { doc.paras.subList(0, p) + it + doc.paras.subList(p + 1, doc.paras.size) })
        assertEquals("Enter a test name!", carried[p].runs.filter { it.control == name }.joinToString("") { it.text })
        // A check box: ticked, its letter and value both.
        val box = "<w:sdtPr><w:id w:val=\"7\"/><w14:checkbox><w14:checked w14:val=\"0\"/><w14:checkedState w14:val=\"2612\" w14:font=\"MS Gothic\"/><w14:uncheckedState w14:val=\"2610\" w14:font=\"MS Gothic\"/></w14:checkbox></w:sdtPr>"
        assertEquals(listOf(Forms.Kind.Check, false), listOf(Forms.kind(box), Forms.checked(box)))
        assertEquals(listOf(true, "☒"), listOf(Forms.checked(Forms.checkedTo(box, true)), Forms.box(box, true)))
    }

    /** Word's table of contents (a docPartObj content control around a TOC field): shown, kept whole, not copy-only. */
    @Test fun `a Word table of contents is kept as the file has it`() {
        val base = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("Body"))), DocPara(listOf(DocRun("Method")), heading = 1),
            DocPara(listOf(DocRun("Text."))), DocPara(listOf(DocRun("Results")), heading = 1))), OfficeKind.Docx)
        val parts = OfficeFormats.unzip(base).toMutableMap()
        fun entry(text: String, n: Int, first: Boolean, last: Boolean) = "<w:p><w:pPr><w:pStyle w:val=\"TOC1\"/><w:tabs><w:tab w:val=\"right\" w:leader=\"dot\" w:pos=\"9350\"/></w:tabs></w:pPr>" +
            (if (first) "<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText xml:space=\"preserve\"> TOC \\o \"1-3\" \\h \\z \\u </w:instrText></w:r><w:r><w:fldChar w:fldCharType=\"separate\"/></w:r>" else "") +
            "<w:hyperlink w:anchor=\"_Toc$n\" w:history=\"1\"><w:r><w:t>$text</w:t></w:r><w:r><w:tab/></w:r><w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText xml:space=\"preserve\"> PAGEREF _Toc$n \\h </w:instrText></w:r><w:r><w:fldChar w:fldCharType=\"separate\"/></w:r><w:r><w:t>1</w:t></w:r><w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:hyperlink>" +
            (if (last) "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r>" else "") + "</w:p>"
        val toc = "<w:sdt><w:sdtPr><w:id w:val=\"5\"/><w:docPartObj><w:docPartGallery w:val=\"Table of Contents\"/><w:docPartUnique/></w:docPartObj></w:sdtPr><w:sdtContent>" +
            "<w:p><w:pPr><w:pStyle w:val=\"TOCHeading\"/></w:pPr><w:r><w:t>Contents</w:t></w:r></w:p>" + entry("Method", 1, true, false) + entry("Results", 2, false, true) + "</w:sdtContent></w:sdt>"
        var xml = String(parts.getValue("word/document.xml"), Charsets.UTF_8)
        for ((n, h) in listOf(1 to "Method", 2 to "Results")) xml = xml.replace(Regex("(<w:p>(?:(?!</w:p>).)*?</w:pPr>)((?:(?!</w:p>).)*?>$h</w:t></w:r>)")) { m -> m.groupValues[1] + "<w:bookmarkStart w:id=\"$n\" w:name=\"_Toc$n\"/>" + m.groupValues[2] + "<w:bookmarkEnd w:id=\"$n\"/>" }
        xml = xml.replaceFirst("<w:body>", "<w:body>$toc")
        parts["word/document.xml"] = xml.toByteArray()
        val bytes = OfficeFormats.zip(parts, null)
        val doc = OfficeFormats.read(bytes, OfficeKind.Docx)
        assertEquals(emptySet<String>(), doc.unsupported)
        assertEquals(listOf("Contents", "Method\t1", "Results\t1", "Body"), doc.paras.take(4).map { it.text })
        val out = String(OfficeFormats.unzip(OfficeFormats.write(doc, OfficeKind.Docx, bytes)).getValue("word/document.xml"), Charsets.UTF_8)
        val kept = out.substringAfter("<w:body>").substringBefore("</w:sdt>")
        assert(listOf("docPartObj", "TOC \\o", "PAGEREF _Toc1", "PAGEREF _Toc2", "w:anchor=\"_Toc2\"").all { it in kept }) { kept }
        assertEquals(listOf("_Toc1", "_Toc2"), Regex("w:name=\"(_Toc\\d)\"").findAll(out).map { it.groupValues[1] }.toList())
        // Its words changed: written as they are now, plain (and no longer the file's block).
        val edited = doc.copy(paras = doc.paras.mapIndexed { i, p -> if (i == 1) p.copy(runs = listOf(DocRun("Methods\t1"))) else p })
        val out2 = String(OfficeFormats.unzip(OfficeFormats.write(edited, OfficeKind.Docx, bytes)).getValue("word/document.xml"), Charsets.UTF_8)
        assert("docPartObj" !in out2 && "Methods" in out2) { out2 }
    }

    @Test fun `an APA 7 student paper is set up in both kinds`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Method")), heading = 1), DocPara(listOf(DocRun("Participants")), heading = 3),
                DocPara(listOf(DocRun("Some text set by hand.", sizePt = 14f)), align = DocAlign.Justify, keep = OfficeFormats.withSpacing(null, kind, line = 1.15f, after = 10f))))
            val bytes = OfficeFormats.write(doc, kind)
            val out = OfficeFormats.zip(ApaPaper.file(OfficeFormats.unzip(bytes), kind, DocStyles.of(kind, bytes)), if (kind == OfficeKind.Odt) "mimetype" else null)
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("apa.${kind.extension}").writeBytes(out)
            val styles = DocStyles.of(kind, out)
            val page = styles.page
            assert(kotlin.math.abs(page.left - 72f) < 0.5f && kotlin.math.abs(page.right - 72f) < 0.5f && kotlin.math.abs(page.bottom - 72f) < 1f && kotlin.math.abs(page.top - 72f) < 1f) { "$kind $page" }
            assert(styles.section.headers["default"].orEmpty().any { PAGE_FIELD in it.text }) { "$kind: no page number" }
            val back = OfficeFormats.read(OfficeFormats.write(ApaPaper.clean(OfficeFormats.read(out, kind), kind), kind, out), kind)
            val text = styles.look(back.paras[2])
            assertEquals(kind.name, listOf(2f, 0f, 0f, 36f, 12f), listOf(text.lineMultiple, text.before, text.after, text.firstLine, text.sizePt ?: back.basePt))
            assertEquals(kind.name, "Times New Roman", text.font)
            assertEquals(kind.name, DocAlign.Start, back.paras[2].align) // no longer justified
            assertEquals(kind.name, null, back.paras[2].runs[0].sizePt) // 14 pt by hand: the style's 12
            val h1 = styles.look(back.paras[0]); val h3 = styles.look(back.paras[1])
            assertEquals(kind.name, listOf(DocAlign.Center, true, false, 12f), listOf(h1.align, h1.bold, h1.italic, h1.sizePt ?: back.basePt))
            assertEquals(kind.name, listOf(true, true), listOf(h3.bold, h3.italic))
            // The title page: the title in the Title style, bold and centred; the text on the next page.
            val tp = ApaPaper.titlePage(ApaTitle("Learning With Tablets", "Theologos K.", date = "30 September 2026"), kind, "Title")
            assertEquals(kind.name, listOf("", "", "", "Learning With Tablets", "", "Theologos K.", "30 September 2026"), tp.map { it.text })
        }
    }

    @Test fun `citations and a reference list are kept in each kind's own words`() {
        val book = DocSource("Παπ20", DocSource.Type.Book, listOf(DocSource.Person("Παπαδόπουλος", "Γιώργος")), "2020", "Εισαγωγή στην παιδαγωγική", publisher = "Gutenberg")
        val article = DocSource("Smi19", DocSource.Type.Article, listOf(DocSource.Person("Smith", "John R."), DocSource.Person("Jones", "Anna")), "2019",
            "Learning with tablets", "Journal of Education", volume = "12", issue = "3", pages = "45-60", doi = "10.1000/xyz123")
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            // Tags as each kind makes them: Word's Παπ20; an ODT's the citation (LibreOffice shows it).
            val book = book.copy(tag = Citations.tagFor(kind, book, emptyList())); val article = article.copy(tag = Citations.tagFor(kind, article, emptyList()))
            if (kind == OfficeKind.Odt) assertEquals("Smith & Jones, 2019", article.tag)
            val c1 = Citations.cite(kind, listOf(book.tag), if (kind == OfficeKind.Docx) "12" else "")
            val c2 = Citations.cite(kind, listOf(article.tag), "")
            val doc0 = OfficeDoc(listOf(DocPara(listOf(DocRun("As research shows "), DocRun(Citations.text(c1, listOf(book)), cite = c1), DocRun(", and "),
                DocRun(Citations.text(c2, listOf(article)), cite = c2), DocRun(" agrees."))), DocPara(emptyList())), sources = listOf(book, article))
            val doc = Citations.insertList(doc0, kind, 1)
            assertEquals(kind.name, listOf(Citations.HEADING, "Smith, J. R., & Jones, A. (2019). Learning with tablets. Journal of Education, 12(3), 45–60. https://doi.org/10.1000/xyz123",
                "Παπαδόπουλος, Γ. (2020). Εισαγωγή στην παιδαγωγική. Gutenberg."), doc.paras.drop(1).map { it.text })
            val bytes = OfficeFormats.write(doc, kind)
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("citations.${kind.extension}").writeBytes(bytes)
            val back = OfficeFormats.read(bytes, kind)
            // Not copy-only: the citations are kept, not simplified.
            assertEquals(kind.name, emptySet<String>(), back.blocking)
            assertEquals(kind.name, doc.paras.map { it.text }, back.paras.map { it.text })
            assertEquals(kind.name, listOf(c1, c2), back.paras[0].runs.mapNotNull { it.cite }.distinct())
            assertEquals(kind.name, listOf(book, article).map { it.copy(extra = "") }, back.sources.map { it.copy(extra = "") }.sortedBy { it.tag != book.tag })
            assertEquals(kind.name, 1..3, Citations.find(back, kind))
            // A source changed: Update rewrites its citation and the list.
            val later = back.copy(sources = back.sources.map { if (it.tag == article.tag) it.copy(year = "2021") else it })
            val updated = Citations.update(later, kind)
            val c2now = if (kind == OfficeKind.Odt) "Smith & Jones, 2021" else c2 // an ODT's mark renamed with it
            assertEquals(kind.name, "(Smith & Jones, 2021)", updated.paras[0].runs.first { it.cite == c2now }.text)
            assert(updated.paras[2].text.startsWith("Smith, J. R., & Jones, A. (2021).")) { updated.paras[2].text }
            assertEquals(kind.name, 4, updated.paras.size)
            // Written again: the same.
            val again = OfficeFormats.read(OfficeFormats.write(back, kind, bytes), kind)
            assertEquals(kind.name, back.paras.map { p -> p.runs.map { it.text to it.cite } }, again.paras.map { p -> p.runs.map { it.text to it.cite } })
        }
    }

    @Test fun `a drop cap is written in each kind's own words and read back as one paragraph`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val text = "Βροχή έπεφτε όλη τη νύχτα πάνω στα κεραμίδια του σπιτιού, και το πρωί ο κήπος ήταν γεμάτος λάσπη και φύλλα που είχε ρίξει ο αέρας."
            val drop = DropCap(lines = 3, margin = false, distance = 2f, length = 1)
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun(text)), keep = OfficeFormats.withDropCap(null, kind, drop)), DocPara(listOf(DocRun("The next paragraph.")))))
            val bytes = OfficeFormats.write(doc, kind)
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("drop-cap.${kind.extension}").writeBytes(bytes)
            if (kind == OfficeKind.Docx) String(OfficeFormats.unzip(bytes).getValue("word/document.xml")).let { xml ->
                assert("w:dropCap=\"drop\"" in xml && ">Β</w:t></w:r></w:p>" in xml) { xml }
            }
            val back = OfficeFormats.read(bytes, kind)
            assertEquals(kind.name, listOf(text, "The next paragraph."), back.paras.map { it.text })
            assertEquals(kind.name, drop, DropCap.of(back.paras[0].keep))
            assertEquals(kind.name, null, DropCap.of(back.paras[1].keep))
            // Written again: the same; taken off: gone.
            val again = OfficeFormats.read(OfficeFormats.write(back, kind, bytes), kind)
            assertEquals(kind.name, back.paras.map { it.text to DropCap.of(it.keep) }, again.paras.map { it.text to DropCap.of(it.keep) })
            val off = again.copy(paras = again.paras.mapIndexed { i, p -> if (i == 0) p.copy(keep = OfficeFormats.withDropCap(p.keep, kind, null)) else p })
            assertEquals(kind.name, null, DropCap.of(OfficeFormats.read(OfficeFormats.write(off, kind, bytes), kind).paras[0].keep))
            // Its own look: the letter in Cambria, red; the rest as it was.
            val styled = back.copy(paras = back.paras.mapIndexed { i, p -> if (i == 0) OfficeFormats.withLettersLook(p, 1, kind, "Cambria", 0xC00000) else p })
            val looked = OfficeFormats.read(OfficeFormats.write(styled, kind, bytes), kind)
            val styles = DocStyles.of(kind, OfficeFormats.write(styled, kind, bytes))
            assertEquals(kind.name, "Β" to 0xC00000, looked.paras[0].runs[0].let { it.text to it.color })
            assertEquals(kind.name, "Cambria", styles.fontName(looked.paras[0].runs[0].font))
            assertEquals(kind.name, null, looked.paras[0].runs[1].color)
            assertEquals(kind.name, drop, DropCap.of(looked.paras[0].keep))
            // A new paragraph after it (Enter) does not get it.
            val entered = OfficeFormats.carryKeep(back.paras, back.paras + DocPara(listOf(DocRun("Typed after."))))
            assertEquals(kind.name, null, DropCap.of(entered.last().keep))
        }
    }

    @Test fun `a watermark is set in both kinds, replaced, read back and taken away`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("A page with a watermark behind its text.")))))
            val bytes = OfficeFormats.write(doc, kind)
            val page = DocStyles.of(kind, bytes).page
            fun set(from: ByteArray, text: String?) = OfficeFormats.zip(OfficeFormats.unzip(from).let {
                if (kind == OfficeKind.Docx) Watermark.docx(it, text, page) else Watermark.odt(it, text, page) }, if (kind == OfficeKind.Odt) "mimetype" else null)
            val draft = set(bytes, "DRAFT")
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("watermark.${kind.extension}").writeBytes(draft)
            val styles = DocStyles.of(kind, draft)
            assertEquals(kind.name, "DRAFT", styles.section.watermark)
            // Not a picture in the header's text: the pages draw it.
            assertEquals(kind.name, false, styles.section.headers.values.flatten().any { p -> p.runs.any { it.image != null } })
            // Replaced, not added; then taken away.
            val other = set(draft, "CONFIDENTIAL & CO")
            assertEquals(kind.name, "CONFIDENTIAL & CO", DocStyles.of(kind, other).section.watermark)
            val all = OfficeFormats.unzip(other).values.sumOf { Regex(Watermark.NAME).findAll(String(it, Charsets.UTF_8)).count() }
            // Word: one in its header; an ODT: one in each page style (as LibreOffice does), the old one gone.
            val pageStyles = Regex("<style:master-page\\b").findAll(String(OfficeFormats.unzip(other).getValue(if (kind == OfficeKind.Docx) "word/document.xml" else "styles.xml"))).count()
            assertEquals(kind.name, if (kind == OfficeKind.Docx) 1 else pageStyles, all)
            assertEquals(kind.name, null, DocStyles.of(kind, set(other, null)).section.watermark)
            // The text still reads.
            assertEquals(kind.name, doc.paras.single().text, OfficeFormats.read(other, kind).paras.single().text)
        }
    }

    @Test fun `page colour and page border are set in both kinds, read back and taken away`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("A page with a colour and a box round it.")))))
            val bytes = OfficeFormats.write(doc, kind)
            fun set(from: ByteArray, color: Int?, border: Border?) = OfficeFormats.zip(OfficeFormats.unzip(from).let {
                if (kind == OfficeKind.Docx) PageDesign.docx(it, color, border) else PageDesign.odt(it, color, border) }, if (kind == OfficeKind.Odt) "mimetype" else null)
            val out = set(bytes, 0xFFF2CC, Border(1.5f, 0x2F5496))
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("page-design.${kind.extension}").writeBytes(out)
            val page = DocStyles.of(kind, out).page
            assertEquals(kind.name, 0xFFF2CC, page.color)
            assertEquals(kind.name, Border(1.5f, 0x2F5496), page.border?.line)
            if (kind == OfficeKind.Docx) assertEquals(24f, page.border?.left) else assertEquals(page.left - 4f, page.border?.left) // the text 4 pt inside it
            // Set twice: still one of each; then taken away.
            val again = set(out, 0xFFF2CC, Border(1.5f, 0x2F5496))
            assertEquals(kind.name, page, DocStyles.of(kind, again).page)
            if (kind == OfficeKind.Docx) String(OfficeFormats.unzip(again).getValue("word/settings.xml")).let { assertEquals(1, Regex("displayBackgroundShape").findAll(it).count()) }
            val none = DocStyles.of(kind, set(again, null, null)).page
            assertEquals(kind.name, null to null, none.color to none.border)
        }
    }

    @Test fun `the page's size, orientation and margins are set in both kinds and read back`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("A page set from the Layout tab.")))))
            val bytes = OfficeFormats.write(doc, kind)
            val before = DocStyles.of(kind, bytes).page
            // Letter, landscape, APA's inch but a wide left.
            val want = before.copy(width = 792f, height = 612f, top = 72f, bottom = 72f, left = 108f, right = 72f, columns = 2, columnGap = 36f)
            val parts = OfficeFormats.unzip(bytes).let { if (kind == OfficeKind.Docx) PageSetup.docx(it, want) else PageSetup.odt(it, want, before) }
            val out = OfficeFormats.zip(parts, if (kind == OfficeKind.Odt) "mimetype" else null)
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("page.${kind.extension}").writeBytes(out)
            val got = DocStyles.of(kind, out).page
            for ((name, a, b) in listOf(Triple("width", want.width, got.width), Triple("height", want.height, got.height), Triple("top", want.top, got.top),
                Triple("bottom", want.bottom, got.bottom), Triple("left", want.left, got.left), Triple("right", want.right, got.right)))
                assert(kotlin.math.abs(a - b) < 0.5f) { "$kind $name: $a ≠ $b" }
            assert(PageSetup.landscape(got) && PageSetup.sizeName(got) == "Letter") { kind.name }
            assertEquals(kind.name, 2 to 36f, got.columns to got.columnGap)
            // A column is the text's width less the gap, halved; the second starts after the gap.
            assertEquals(kind.name, (792f - 108f - 72f - 36f) / 2, got.column(1).textWidth, 0.5f)
            assertEquals(kind.name, 108f + (792f - 108f - 72f - 36f) / 2 + 36f, got.column(1).left, 0.5f)
            assertEquals(kind.name, doc.paras.map { it.text }, OfficeFormats.read(out, kind).paras.map { it.text })
        }
        // A Word file with several sections: every one set.
        val two = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("one")), keep = DocKeep(parts = listOf("sectPr" to "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr>"))),
            DocPara(listOf(DocRun("two"))))), OfficeKind.Docx)
        val set = String(PageSetup.docx(OfficeFormats.unzip(two), DocPage(width = 612f, height = 792f, top = 36f, bottom = 36f, left = 36f, right = 36f)).getValue("word/document.xml"))
        assertEquals(2, Regex("""<w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="720"""").findAll(set).count())
        assert("""w:header="708"""" in set) // its header distance kept
    }

    @Test fun `Line numbers are set, read back and taken away, in both kinds`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val bytes = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("one"))))), kind)
            fun set(from: ByteArray, mode: String?) = OfficeFormats.zip(PageSetup.lineNumbers(OfficeFormats.unzip(from), kind, mode), if (kind == OfficeKind.Odt) "mimetype" else null)
            assertEquals(kind.name, null, DocStyles.of(kind, bytes).section.lineNumbers)
            for (mode in listOf("continuous", "newPage") + if (kind == OfficeKind.Docx) listOf("newSection") else emptyList()) {
                val out = set(bytes, mode)
                assertEquals("$kind $mode", mode, DocStyles.of(kind, out).section.lineNumbers?.restart)
                assertEquals("$kind $mode none", null, DocStyles.of(kind, set(set(out, mode), null)).section.lineNumbers) // again, then none
            }
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("lines.${kind.extension}").writeBytes(set(bytes, "newPage"))
        }
    }

    @Test fun `a style set restyles Normal, Title and the headings, in both kinds`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) for (set in StyleSet.entries) {
            val bytes = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("Head")), heading = 1), DocPara(listOf(DocRun("text"))))), kind)
            val parts = OfficeFormats.unzip(bytes)
            val path = if (kind == OfficeKind.Docx) "word/styles.xml" else "styles.xml"
            parts[path] = set.applyTo(String(parts.getValue(path), Charsets.UTF_8), kind, DocStyles.of(kind, bytes)).toByteArray()
            val styles = DocStyles.of(kind, OfficeFormats.zip(parts, if (kind == OfficeKind.Odt) "mimetype" else null))
            val h1 = styles.look(DocPara(emptyList(), heading = 1)); val want = set.changes.getValue("Heading 1")
            assertEquals("$kind $set", listOf(want.sizePt, want.bold == true, want.color.takeIf { it != -1 }), listOf(h1.sizePt, h1.bold == true, h1.color))
            assertEquals("$kind $set normal", set.changes.getValue("Normal").sizePt, styles.look(DocPara(emptyList())).sizePt)
        }
    }

    @Test fun `Hyphenation is set and taken away, in both kinds`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val bytes = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("one"))))), kind)
            fun set(from: ByteArray, on: Boolean) = OfficeFormats.zip(PageDesign.hyphenation(OfficeFormats.unzip(from), kind, on), if (kind == OfficeKind.Odt) "mimetype" else null)
            assertEquals(kind.name, false, DocStyles.of(kind, bytes).hyphenate)
            val on = set(bytes, true)
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("hyphens.${kind.extension}").writeBytes(on)
            assertEquals(kind.name, true, DocStyles.of(kind, on).hyphenate)
            assertEquals(kind.name, true, DocStyles.of(kind, set(on, true)).hyphenate)
            assertEquals(kind.name, false, DocStyles.of(kind, set(on, false)).hyphenate)
        }
    }

    @Test fun `a table of contents lists the headings with their pages, in the file's contents styles, and updates in place`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Title page"))), DocPara(emptyList()),
                DocPara(listOf(DocRun("Εισαγωγή")), heading = 1), DocPara(listOf(DocRun("text"))),
                DocPara(listOf(DocRun("Method")), heading = 2), DocPara(listOf(DocRun("Deep")), heading = 4), DocPara(listOf(DocRun("Results")), heading = 1)))
            val styles = DocStyles.of(kind, OfficeFormats.write(doc, kind))
            // A stand-in for the page layout: each paragraph on the page of its index.
            val fake: (OfficeDoc, IntArray) -> Unit = { d, pages -> d.paras.indices.forEach { pages[it] = it + 1 } }
            val with = Contents.insert(doc, kind, styles, 1, fake)
            // At the empty paragraph: its title and the three headings of levels 1–3, each with a tab and its page.
            assertEquals(kind.name, listOf("Title page", "Contents", "Εισαγωγή\t6", "Method\t8", "Results\t10"), with.paras.take(5).map { it.text })
            assertEquals(kind.name, listOf(Contents.headingStyle(kind), Contents.levelStyle(kind, 1), Contents.levelStyle(kind, 2)), with.paras.drop(1).take(3).map { it.keep?.style })
            // Written, read back: the same, and the styles are in the file.
            val bytes = OfficeFormats.write(with, kind)
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("contents.${kind.extension}").writeBytes(bytes)
            val back = OfficeFormats.read(bytes, kind)
            assertEquals(kind.name, with.paras.map { it.text }, back.paras.map { it.text })
            val tabs = DocStyles.of(kind, bytes).look(back.paras[2]).tabs
            assert(tabs.any { it.type == 'r' && it.leader == '.' }) { "$kind $tabs" }
            // A new heading, then Update: the same place, one more line, no second copy.
            val more = back.copy(paras = back.paras + DocPara(listOf(DocRun("Discussion")), heading = 1))
            val updated = Contents.insert(more, kind, styles, more.paras.size, fake)
            assertEquals(kind.name, 1, updated.paras.count { it.text == "Contents" })
            assertEquals(kind.name, "Discussion\t12", updated.paras[5].text)
        }
    }

    @Test fun `a shape made in Writer is written as the file's own and read back as one`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val yellow = 0x0DFFC114 // mostly see-through fill, as the keyboard's circles
            fun shape(k: String, fill: Int?, line: Int?, wrap: PictureWrap? = null) =
                DocImage("s$k", null, 72f, 72f, PictureXml.newShape(k, kind, fill, line, 1f, 72f, 72f), wrap = wrap)
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Circle: "), DocRun(OBJECT_MARK.toString(), image = shape("ellipse", yellow, 0xFFFFC114.toInt())),
                DocRun(" star: "), DocRun(OBJECT_MARK.toString(), image = shape("star5", 0xFF4472C4.toInt(), null)),
                DocRun(" in front: "), DocRun(OBJECT_MARK.toString(), image = shape("rightArrow", 0xFFE53935.toInt(), 0xFF000000.toInt(), PictureWrap.InFront))))))
            val bytes = OfficeFormats.write(doc, kind)
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("shapes.${kind.extension}").writeBytes(bytes)
            val back = OfficeFormats.read(bytes, kind)
            assertEquals(kind.name, emptySet<String>(), back.blocking)
            val imgs = back.paras.single().runs.mapNotNull { it.image }
            assertEquals(kind.name, 3, imgs.size)
            // Each drawn again as it was made.
            val shapes = imgs.map { ShapePicture.of(it.raw) }
            assertEquals(kind.name, listOf("ellipse", "star5", "rightArrow"), shapes.map { it?.geometry })
            assertEquals(kind.name, listOf(yellow, 0xFF4472C4.toInt(), 0xFFE53935.toInt()), shapes.map { it?.fill })
            assertEquals(kind.name, listOf(0xFFFFC114.toInt(), null, 0xFF000000.toInt()), shapes.map { it?.line })
            assertEquals(kind.name, listOf(PictureWrap.InLine, PictureWrap.InLine, PictureWrap.InFront), imgs.map { PictureXml.wrapOf(it) })
            // Written again unchanged: the same (a style not added twice).
            assertEquals(kind.name, imgs.map { it.raw }, OfficeFormats.read(OfficeFormats.write(back, kind, bytes), kind).paras.single().runs.mapNotNull { it.image?.raw })
        }
    }

    @Test fun `a drawing made on a page stays where it was drawn`() {
        val png = java.io.ByteArrayOutputStream().also { out ->
            val b = java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            b.createGraphics().apply { color = java.awt.Color.RED; fillOval(0, 0, 40, 20); dispose() }
            javax.imageio.ImageIO.write(b, "png", out)
        }.toByteArray()
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val ink = DocImage("n1", null, 120f, 60f, null, png, "png", PictureWrap.InFront, onPage = 200f to 300f)
            // New: placed from the page's corner at once.
            assertEquals(kind.name, PictureXml.Place("page", 200f, null, "page", 300f, null, PictureWrap.InFront), PictureXml.placeOf(ink))
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Text "), DocRun(OBJECT_MARK.toString(), image = ink), DocRun(" more")))))
            val bytes = OfficeFormats.write(doc, kind)
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("drawn-on-page.${kind.extension}").writeBytes(bytes)
            val back = OfficeFormats.read(bytes, kind)
            val img = back.paras.single().runs.mapNotNull { it.image }.single()
            assertEquals(kind.name, PictureXml.Place("page", 200f, null, "page", 300f, null, PictureWrap.InFront), PictureXml.placeOf(img))
            // Written again unchanged: still there.
            val again = OfficeFormats.read(OfficeFormats.write(back, kind, bytes), kind).paras.single().runs.mapNotNull { it.image }.single()
            assertEquals(kind.name, PictureXml.placeOf(img), PictureXml.placeOf(again))
        }
    }

    @Test fun `Position moves a file's floating picture or shape on its page, through the file`() {
        val png = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(java.awt.image.BufferedImage(10, 5, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", it) }.toByteArray()
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val shape = DocImage("s1", null, 72f, 72f, PictureXml.newShape("ellipse", kind, 0xFFFFC114.toInt(), 0xFF000000.toInt(), 1f, 72f, 72f))
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Text "), DocRun(OBJECT_MARK.toString(), image = DocImage("n1", null, 100f, 50f, null, png, "png")),
                DocRun(" and "), DocRun(OBJECT_MARK.toString(), image = shape)))))
            val bytes = OfficeFormats.write(doc, kind)
            val read = OfficeFormats.read(bytes, kind)
            fun images(d: OfficeDoc) = d.paras.single().runs.mapNotNull { it.image }
            // A file's picture made Square, a shape In front; each moved (twice: the second place wins).
            val (pic, shp) = images(read).let { PictureXml.withWrap(it[0], PictureWrap.Square, kind) to PictureXml.withWrap(it[1], PictureWrap.InFront, kind) }
            val moved = listOf(PictureXml.placed(PictureXml.placed(pic, 10f, 10f, kind), 150f, 250f, kind), PictureXml.placed(shp, 300f, 400.5f, kind))
            // The pages see it at once…
            assertEquals(kind.name, PictureXml.Place("page", 150f, null, "page", 250f, null, PictureWrap.Square), PictureXml.placeOf(moved[0]))
            assertEquals(kind.name, PictureXml.Place("page", 300f, null, "page", 400.5f, null, PictureWrap.InFront), PictureXml.placeOf(moved[1]))
            // …and the file keeps it.
            val changed = read.copy(paras = listOf(read.paras.single().let { p -> var i = 0; p.copy(runs = p.runs.map { r -> if (r.image != null) r.copy(image = moved[i++]) else r }) }))
            val out = OfficeFormats.write(changed, kind, bytes)
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("positioned.${kind.extension}").writeBytes(out)
            val back = images(OfficeFormats.read(out, kind))
            assertEquals(kind.name, moved.map { PictureXml.placeOf(it) }, back.map { PictureXml.placeOf(it) })
            if (kind == OfficeKind.Odt) assertTrue("the shape's style places it from the page", Regex("""Tetra_20_shape_[^"]*_20_page" style:family="graphic"><style:graphic-properties [^>]*vertical-rel="page"""")
                .containsMatchIn(String(OfficeFormats.unzip(out).getValue("content.xml"), Charsets.UTF_8)))
        }
    }

    @Test fun `a text box keeps its words, and Edit text changes them, through the file`() {
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val lines = listOf("First line & more", "Second <line>")
            val box = PictureXml.withWrap(DocImage("b1", null, 180f, 44f, PictureXml.newTextBox(kind, 180f, 44f, lines)), PictureWrap.Square, kind)
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Before "), DocRun(OBJECT_MARK.toString(), image = box), DocRun(" after")))))
            val bytes = OfficeFormats.write(doc, kind)
            java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("textbox.${kind.extension}").writeBytes(bytes)
            val read = OfficeFormats.read(bytes, kind)
            val img = read.paras.single().runs.mapNotNull { it.image }.single()
            assertEquals(kind.name, lines, img.box?.map { it.text })
            assertEquals(kind.name, PictureWrap.Square, PictureXml.wrapOf(img))
            assertEquals(kind.name, "Before  after", read.paras.single().text.replace(OBJECT_MARK.toString(), "")) // its words are not the paragraph's
            assertTrue(kind.name, PictureXml.editable(img))
            // Edit text: new words, in the file too.
            val edited = img.with(raw = PictureXml.boxText(img.raw!!, kind, listOf("Changed")))
            val again = OfficeFormats.read(OfficeFormats.write(read.copy(paras = listOf(read.paras.single().let { p -> p.copy(runs = p.runs.map { r -> if (r.image != null) r.copy(image = edited) else r }) })), kind, bytes), kind)
            assertEquals(kind.name, listOf("Changed"), again.paras.single().runs.mapNotNull { it.image }.single().box?.map { it.text })
            // WordArt: its words big, bold and in its colour, with no box round them.
            val art = PictureXml.withWrap(DocImage("a1", null, 200f, 55f, PictureXml.newTextBox(kind, 200f, 55f, listOf("Title"), 0xFFED7D31.toInt())), PictureWrap.Square, kind)
            val artBytes = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun(OBJECT_MARK.toString(), image = art), DocRun("Text beside it."))))), kind)
            java.io.File("build/office-roundtrip").resolve("wordart.${kind.extension}").writeBytes(artBytes)
            val artRun = OfficeFormats.read(artBytes, kind).paras.single().runs.mapNotNull { it.image }.single().box!!.single().runs.single()
            assertEquals(kind.name, listOf("Title", true, 36f, 0xED7D31), listOf(artRun.text, artRun.bold, artRun.sizePt, artRun.color))
            // A plain shape is no text box.
            assertEquals(kind.name, null, OfficeFormats.read(OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun(OBJECT_MARK.toString(), image =
                DocImage("s1", null, 72f, 72f, PictureXml.newShape("ellipse", kind, null, 0, 1f, 72f, 72f))))))), kind), kind).paras.single().runs.mapNotNull { it.image }.single().box)
        }
    }

    @Test fun `a drawing keeps its strokes inside its picture, through the file`() {
        val plain = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(java.awt.image.BufferedImage(10, 5, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", it) }.toByteArray()
        val strokes = listOf(InkStroke(listOf(androidx.compose.ui.geometry.Offset(0.1f, 0.2f), androidx.compose.ui.geometry.Offset(0.9f, 0.4f)), 0xFFE53935.toInt(), 0.75f, false),
            InkStroke(listOf(androidx.compose.ui.geometry.Offset(0.5f, 0.25f)), 0xFFFFC114.toInt(), 5f, true))
        val png = InkPicture.withStrokes(plain, strokes, 0.5f)
        assertEquals(null, InkPicture.strokesOf(plain))
        assertEquals(10, javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(png)).width) // still a picture (its CRC right)
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun(OBJECT_MARK.toString(), image = DocImage("n1", null, 100f, 50f, null, png, "png", PictureWrap.InFront, onPage = 10f to 20f))))))
            val bytes = OfficeFormats.write(doc, kind)
            val img = OfficeFormats.read(bytes, kind).paras.single().runs.mapNotNull { it.image }.single()
            val (back, aspect) = InkPicture.strokesOf(OfficeFormats.unzip(bytes).getValue(img.part!!))!!
            assertEquals(kind.name, 0.5f, aspect)
            assertEquals(kind.name, strokes.map { Triple(it.color, it.widthMm, it.highlighter) }, back.map { Triple(it.color, it.widthMm, it.highlighter) })
            assertEquals(kind.name, strokes.map { it.points }, back.map { it.points })
        }
    }

    @Test fun `empty paragraphs pair in order - a blank page keeps its breaks`() {
        fun p(t: String) = DocPara(if (t.isEmpty()) emptyList() else listOf(DocRun(t)))
        // After Blank page the paragraph after it was empty; then text was typed there and a picture put in the first.
        assertEquals(listOf(0, 1, 2), OfficeFormats.pairParagraphs(listOf(p("First page."), p(""), p("")),
            listOf(p("\uFFFCFirst page."), p(""), p("Third page."))).toList())
        // An empty paragraph added (Enter) is a new one; one taken away leaves the others matched.
        assertEquals(listOf(0, -1, 1), OfficeFormats.pairParagraphs(listOf(p("A"), p("B")), listOf(p("A"), p(""), p("B"))).toList())
        assertEquals(listOf(0, 2), OfficeFormats.pairParagraphs(listOf(p("A"), p(""), p("B")), listOf(p("A"), p("B"))).toList())
    }

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
            // Through the editor with the document's own styles drawn: what comes back is what went in, as without them.
            // (Tables left out: laying one out needs Android's text layout, which a unit test does not have.)
            val text = doc.copy(paras = doc.paras.filter { it.table == null })
            fun shown(styles: DocStyles): Boolean {
                val pictures = HashMap<String, DocImage>()
                val state = com.mohamedrejeb.richeditor.model.RichTextState()
                state.setRichTextDocument(WriterBridge.toEditor(text, pictures, styles))
                return WriterBridge.comparable(WriterBridge.fromEditor(state.toRichTextDocument(), text.basePt, pictures)) == WriterBridge.comparable(text.paras)
            }
            val styles = DocStyles.of(kind, bytes)
            assertEquals(f.name, shown(DocStyles.Plain), shown(styles))
        }
    }

    @Test fun `spacing set from Writer is written in the file's own words and read back`() {
        for (kind in OfficeKind.entries) {
            val start = OfficeFormats.write(sample, kind)
            val doc = OfficeFormats.read(start, kind)
            val paras = doc.paras.mapIndexed { i, p ->
                if (i != 1) p else p.copy(keep = OfficeFormats.withSpacing(p.keep, kind, line = 1.5f, before = 6f, after = 12f, left = 18f, firstLine = -9f))
            }
            val back = OfficeFormats.read(OfficeFormats.write(doc.copy(paras = paras), kind, start), kind)
            assertEquals(emptySet<String>(), back.unsupported)
            assertEquals(doc.paras.map { it.text }, back.paras.map { it.text })
            val parts = back.paras[1].keep!!.parts.joinToString("") { it.second }
            if (kind == OfficeKind.Docx) {
                assertTrue(parts, "w:line=\"360\"" in parts && "w:lineRule=\"auto\"" in parts && "w:before=\"120\"" in parts && "w:after=\"240\"" in parts)
                assertTrue(parts, "w:left=\"360\"" in parts && "w:hanging=\"180\"" in parts)
            } else {
                for (a in listOf("fo:line-height=\"150%\"", "fo:margin-top=\"6pt\"", "fo:margin-bottom=\"12pt\"", "fo:margin-left=\"18pt\"", "fo:text-indent=\"-9pt\""))
                    assertTrue(parts, a in parts)
            }
            // Set again: replaced, not added twice.
            val twice = OfficeFormats.withSpacing(back.paras[1].keep, kind, line = 2f).parts.joinToString("") { it.second }
            assertEquals(1, Regex(if (kind == OfficeKind.Docx) "w:line=" else "line-height=").findAll(twice).count())
        }
    }

    @Test fun `Title, Subtitle and Quote go into a document that has not got them`() {
        for ((kind, quote) in listOf(OfficeKind.Docx to "Quote", OfficeKind.Odt to "Quotations")) {
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Big")), keep = DocKeep(style = "Title")), DocPara(listOf(DocRun("Said")), keep = DocKeep(style = quote))))
            val bytes = OfficeFormats.write(doc, kind)
            val styles = String(OfficeFormats.unzip(bytes).getValue(if (kind == OfficeKind.Docx) "word/styles.xml" else "styles.xml"))
            assertTrue(styles, if (kind == OfficeKind.Docx) "w:styleId=\"Title\"" in styles && "w:styleId=\"Quote\"" in styles && "NORMAL" !in styles
                else "style:name=\"Title\"" in styles && "style:name=\"Quotations\"" in styles)
            assertTrue(!styles.contains("Subtitle")) // only what is used
            assertEquals(listOf("Title", quote), OfficeFormats.read(bytes, kind).paras.map { it.keep?.style })
        }
    }

    @Test fun `a style changed from Writer changes in the file's styles, and every paragraph with it`() {
        val change = StyleChange(font = "Times New Roman", sizePt = 16f, bold = false, italic = true, color = 0x1F3864, align = DocAlign.Center,
            line = 2f, before = 12f, after = 6f, left = 36f, firstLine = -18f)
        for (kind in OfficeKind.entries) {
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Head")), heading = 1), DocPara(listOf(DocRun("Big")), keep = DocKeep(style = "Title"))))
            val parts = OfficeFormats.unzip(OfficeFormats.write(doc, kind))
            val path = if (kind == OfficeKind.Docx) "word/styles.xml" else "styles.xml"
            var styles = String(parts.getValue(path))
            styles = if (kind == OfficeKind.Docx) StyleEdit.docx(StyleEdit.docx(styles, "Heading1", 1, "Normal", change), "Subtitle", 0, "Normal", StyleChange(sizePt = 20f))
                else StyleEdit.odt(StyleEdit.odt(styles, "Heading_20_1", 1, change), "Subtitle", 0, StyleChange(sizePt = 20f))
            parts[path] = styles.toByteArray()
            val bytes = OfficeFormats.zip(parts, if (kind == OfficeKind.Odt) "mimetype" else null)
            OfficeFormats.parse(styles.toByteArray()) // still well formed
            val look = DocStyles.of(kind, bytes).look(OfficeFormats.read(bytes, kind).paras[0])
            assertEquals(kind.name, listOf("Times New Roman", 16f, false, true, 0x1F3864, DocAlign.Center, 2f, 12f, 6f, 36f, -18f),
                listOf(look.font, look.sizePt, look.bold, look.italic, look.color, look.align, look.lineMultiple, look.before, look.after, look.left, look.firstLine))
            // A style the document had not got (Subtitle) is added, then changed.
            assertEquals(20f, DocStyles.of(kind, bytes).look(DocPara(emptyList(), keep = DocKeep(style = "Subtitle"))).sizePt)
            // Changed twice: set again, not added again.
            val again = if (kind == OfficeKind.Docx) StyleEdit.docx(styles, "Heading1", 1, "Normal", StyleChange(sizePt = 18f)) else StyleEdit.odt(styles, "Heading_20_1", 1, StyleChange(sizePt = 18f))
            assertEquals(1, Regex(if (kind == OfficeKind.Docx) "w:styleId=\"Heading1\"" else "style:name=\"Heading_20_1\"").findAll(again).count())
        }
    }

    @Test fun `a header, footer and page numbers set from Writer are Word's, and stay through saving`() {
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Body")))))
        for (hf in listOf(
            HeaderFooter(header = "Running head", headerAlign = DocAlign.Start, footer = "Draft", footerAlign = DocAlign.Center,
                number = HeaderFooter.NumberPlace.Header, numberAlign = DocAlign.End, numberFormat = "Page # of N"),
            HeaderFooter(number = HeaderFooter.NumberPlace.Footer, numberAlign = DocAlign.Center, numberFormat = "#", firstDifferent = true),
        )) {
            for (kind in OfficeKind.entries) {
                val start = OfficeFormats.write(doc, kind)
                val parts = if (kind == OfficeKind.Docx) HeaderFooterEdit.docx(OfficeFormats.unzip(start), hf, DocPage()) else HeaderFooterEdit.odt(OfficeFormats.unzip(start), hf, DocPage())
                val bytes = OfficeFormats.zip(parts, if (kind == OfficeKind.Odt) "mimetype" else null)
                // Saved again with new text: the page setup and its header and footer stay.
                val saved = OfficeFormats.write(doc.copy(paras = listOf(DocPara(listOf(DocRun("Changed"))))), kind, bytes)
                for (b in listOf(bytes, saved)) {
                    val section = DocStyles.of(kind, b).section
                    assertEquals(kind.name, hf, HeaderFooter.of(section))
                    OfficeFormats.unzip(b).filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.values.forEach { OfficeFormats.parse(it) } // well formed
                }
                assertEquals(listOf("Changed"), OfficeFormats.read(saved, kind).paras.map { it.text })
            }
        }
    }

    @Test fun `a column break in a one-column document is the page break it looks like`() {
        val parts = OfficeFormats.unzip(OfficeFormats.write(sample, OfficeKind.Docx))
        val xml = String(parts.getValue("word/document.xml"), Charsets.UTF_8)
        val broken = "<w:p><w:r><w:t>Before</w:t></w:r><w:r><w:br w:type=\"column\"/></w:r><w:r><w:t>After</w:t></w:r></w:p>"
        parts["word/document.xml"] = xml.replaceFirst("<w:body>", "<w:body>$broken").toByteArray()
        val bytes = OfficeFormats.zip(parts, null)
        val doc = OfficeFormats.read(bytes, OfficeKind.Docx)
        assertEquals(emptySet<String>(), doc.blocking)
        assertEquals(listOf("Before", "After"), doc.paras.take(2).map { it.text })
        val out = String(OfficeFormats.unzip(OfficeFormats.write(doc, OfficeKind.Docx, bytes)).getValue("word/document.xml"), Charsets.UTF_8)
        assertTrue(out, Regex("<w:pageBreakBefore/>(?:(?!</w:p>).)*>After<").containsMatchIn(out))
        // At a paragraph's end it leaves an empty line at the top of the next page (LibreOffice): an empty paragraph there.
        val atEnd = "<w:p><w:r><w:t>First</w:t></w:r><w:r><w:br w:type=\"column\"/></w:r></w:p><w:p><w:r><w:t>Second</w:t></w:r></w:p>"
        parts["word/document.xml"] = xml.replaceFirst("<w:body>", "<w:body>$atEnd").toByteArray()
        val ended = OfficeFormats.read(OfficeFormats.zip(parts, null), OfficeKind.Docx)
        assertEquals(listOf("First", "", "Second"), ended.paras.take(3).map { it.text })
        assertTrue(ended.paras[1].keep!!.parts.any { it.first == "pageBreakBefore" })
        // A heading whose style is centred, set left in the file: still left after saving, centred once realigned here.
        val left = "<w:p><w:pPr><w:pStyle w:val=\"Title\"/><w:jc w:val=\"left\"/></w:pPr><w:r><w:t>Left</w:t></w:r></w:p>"
        parts["word/document.xml"] = xml.replaceFirst("<w:body>", "<w:body>$left").toByteArray()
        val leftBytes = OfficeFormats.zip(parts, null)
        val leftDoc = OfficeFormats.read(leftBytes, OfficeKind.Docx)
        fun firstP(d: OfficeDoc) = String(OfficeFormats.unzip(OfficeFormats.write(d, OfficeKind.Docx, leftBytes)).getValue("word/document.xml"), Charsets.UTF_8).substringAfter("<w:body>").substringBefore("</w:p>")
        assertTrue(firstP(leftDoc), "<w:jc w:val=\"left\"/>" in firstP(leftDoc))
        val centred = firstP(leftDoc.copy(paras = leftDoc.paras.mapIndexed { i, p -> if (i == 0) p.copy(align = DocAlign.Center) else p }))
        assertEquals(centred, 1, Regex("<w:jc ").findAll(centred).count())
        assertTrue(centred, "<w:jc w:val=\"center\"/>" in centred)
        // Google Docs writes "no page break" on every paragraph: a break before one still starts its page.
        val docs = "<w:p><w:r><w:t>Cover</w:t></w:r></w:p><w:p><w:r><w:br w:type=\"page\"/></w:r></w:p><w:p><w:pPr><w:pageBreakBefore w:val=\"0\"/></w:pPr><w:r><w:t>Contents</w:t></w:r></w:p>"
        parts["word/document.xml"] = xml.replaceFirst("<w:body>", "<w:body>$docs").toByteArray()
        val gdocs = OfficeFormats.zip(parts, null)
        val saved = String(OfficeFormats.unzip(OfficeFormats.write(OfficeFormats.read(gdocs, OfficeKind.Docx), OfficeKind.Docx, gdocs)).getValue("word/document.xml"), Charsets.UTF_8)
        assertTrue(saved, Regex("<w:pageBreakBefore/>(?:(?!</w:p>).)*>Contents<").containsMatchIn(saved))
        // Two columns somewhere: a column break means the next column, which Writer does not lay out yet — a copy.
        parts["word/document.xml"] = xml.replaceFirst("<w:body>", "<w:body>$broken").replace("<w:sectPr>", "<w:sectPr><w:cols w:num=\"2\"/>").toByteArray()
        assertTrue("columns" in OfficeFormats.read(OfficeFormats.zip(parts, null), OfficeKind.Docx).blocking)
    }

    @Test fun `a lettered list level is read, labelled and saved as the file has it`() {
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("One")), list = DocList(true, 0)), DocPara(listOf(DocRun("Sub")), list = DocList(true, 1))))
        val parts = OfficeFormats.unzip(OfficeFormats.write(doc, OfficeKind.Docx))
        val numbering = String(parts.getValue("word/numbering.xml"), Charsets.UTF_8)
        val lettered = Regex("""(<w:abstractNum w:abstractNumId="2">.*?<w:lvl w:ilvl="1">.*?)<w:numFmt w:val="decimal"/><w:lvlText w:val="%2."/>""")
            .replace(numbering) { it.groupValues[1] + "<w:numFmt w:val=\"lowerLetter\"/><w:lvlText w:val=\"%2)\"/>" }
        assertTrue(lettered != numbering)
        parts["word/numbering.xml"] = lettered.toByteArray()
        val bytes = OfficeFormats.zip(parts, null)
        val back = OfficeFormats.read(bytes, OfficeKind.Docx)
        assertEquals(emptySet<String>(), back.blocking)
        val styles = DocStyles.of(OfficeKind.Docx, bytes)
        assertEquals("lowerLetter" to "%2)", styles.orderedLevels(back.paras)[1])
        assertEquals("a)", styles.listLabel(back.paras[1], intArrayOf(1, 1)))
        val saved = OfficeFormats.unzip(OfficeFormats.write(back, OfficeKind.Docx, bytes))
        assertEquals("a)", DocStyles.of(OfficeKind.Docx, OfficeFormats.zip(saved, null)).listLabel(OfficeFormats.read(OfficeFormats.zip(saved, null), OfficeKind.Docx).paras[1], intArrayOf(1, 1)))
    }

    @Test fun `a multilevel scheme is saved, read back and labelled in both formats and in the editor`() {
        val legal = (0..8).map { l -> "decimal" to (1..l + 1).joinToString("") { "%$it." } }
        val lettered = (0..8).map { listOf("decimal", "lowerLetter", "lowerRoman")[it % 3] to "%${it + 1}." }
        fun item(t: String, level: Int) = DocPara(listOf(DocRun(t)), list = DocList(true, level))
        val paras = listOf(item("One", 0), item("Two", 0), item("Sub", 1), item("Deep", 2))
        for (kind in listOf(OfficeKind.Docx, OfficeKind.Odt)) {
            val bytes = OfficeFormats.write(OfficeDoc(paras, numbering = legal), kind)
            val back = OfficeFormats.read(bytes, kind)
            val styles = DocStyles.of(kind, bytes)
            assertEquals("$kind", "2.1.", styles.listLabel(back.paras[2], intArrayOf(2, 1)))
            assertEquals("$kind", "2.1.1.", styles.listLabel(back.paras[3], intArrayOf(2, 1, 1)))
            // The editor gives only an item's own number, in document order: the parents' come from the items before.
            val editor = FileNumbering(styles) { styles.orderedLevels(back.paras) }
            assertTrue("$kind", editor.compound)
            val shown = listOf(1 to 1, 2 to 1, 1 to 2, 1 to 3).map { (n, l) -> editor.format(n, l) + editor.getSuffix(l) }
            assertEquals("$kind", listOf("1.", "2.", "2.1.", "2.1.1."), shown)
            val abc = OfficeFormats.write(OfficeDoc(paras, numbering = lettered), kind)
            assertEquals("$kind", "a.", DocStyles.of(kind, abc).listLabel(OfficeFormats.read(abc, kind).paras[2], intArrayOf(2, 1)))
            assertEquals("$kind", "i.", DocStyles.of(kind, abc).listLabel(OfficeFormats.read(abc, kind).paras[3], intArrayOf(2, 1, 1)))
        }
        // Without a scheme Writer's lists stay 1. 2. 3. at every level.
        val plain = OfficeFormats.write(OfficeDoc(paras), OfficeKind.Docx)
        assertEquals("1.", DocStyles.of(OfficeKind.Docx, plain).listLabel(OfficeFormats.read(plain, OfficeKind.Docx).paras[2], intArrayOf(2, 1)))
    }

    @Test fun `a character style with no formatting of its own is no reason for a copy`() {
        val parts = OfficeFormats.unzip(OfficeFormats.write(sample, OfficeKind.Docx))
        val styles = String(parts.getValue("word/styles.xml"), Charsets.UTF_8)
        parts["word/styles.xml"] = styles.replace("</w:styles>", "<w:style w:type=\"character\" w:customStyle=\"1\" w:styleId=\"given-name\"><w:name w:val=\"given-name\"/></w:style>" +
            "<w:style w:type=\"character\" w:customStyle=\"1\" w:styleId=\"Loud\"><w:name w:val=\"Loud\"/><w:rPr><w:b/></w:rPr></w:style></w:styles>").toByteArray()
        val xml = String(parts.getValue("word/document.xml"), Charsets.UTF_8)
        fun with(style: String): Set<String> {
            parts["word/document.xml"] = xml.replaceFirst("<w:body>", "<w:body><w:p><w:r><w:rPr><w:rStyle w:val=\"$style\"/></w:rPr><w:t>Name</w:t></w:r></w:p>").toByteArray()
            return OfficeFormats.read(OfficeFormats.zip(parts, null), OfficeKind.Docx).blocking
        }
        assertEquals(emptySet<String>(), with("given-name"))
        assertEquals(setOf("character styles"), with("Loud"))
    }

    @Test fun `a heading said to have no number keeps having none`() {
        val parts = OfficeFormats.unzip(OfficeFormats.write(sample, OfficeKind.Docx))
        val xml = String(parts.getValue("word/document.xml"), Charsets.UTF_8)
        val heading = Regex("""<w:pStyle w:val="([^"]+)"/>""").findAll(xml).map { it.groupValues[1] }.first { it.contains("1") }
        parts["word/document.xml"] = xml.replaceFirst("<w:body>", "<w:body><w:p><w:pPr><w:pStyle w:val=\"$heading\"/><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"0\"/></w:numPr></w:pPr><w:r><w:t>Abstract</w:t></w:r></w:p>").toByteArray()
        val bytes = OfficeFormats.zip(parts, null)
        val doc = OfficeFormats.read(bytes, OfficeKind.Docx)
        assertEquals(1, doc.paras[0].heading)
        fun first(d: OfficeDoc) = String(OfficeFormats.unzip(OfficeFormats.write(d, OfficeKind.Docx, bytes)).getValue("word/document.xml"), Charsets.UTF_8).substringAfter("<w:body>").substringBefore("</w:p>")
        assertTrue(first(doc), "<w:numId w:val=\"0\"/>" in first(doc))
        // Made a numbered item here: its own number, and no "none" beside it.
        val listed = first(doc.copy(paras = doc.paras.mapIndexed { i, p -> if (i == 0) p.copy(list = DocList(true, 0)) else p }))
        assertEquals(listed, 1, Regex("<w:numPr>").findAll(listed).count())
    }

    @Test fun `hidden text is kept hidden and is no reason for a copy`() {
        val parts = OfficeFormats.unzip(OfficeFormats.write(sample, OfficeKind.Docx))
        val xml = String(parts.getValue("word/document.xml"), Charsets.UTF_8)
        parts["word/document.xml"] = xml.replaceFirst("<w:body>", "<w:body><w:p><w:r><w:t xml:space=\"preserve\">Seen </w:t></w:r><w:r><w:rPr><w:vanish/></w:rPr><w:t>SECRET</w:t></w:r></w:p>").toByteArray()
        val bytes = OfficeFormats.zip(parts, null)
        val doc = OfficeFormats.read(bytes, OfficeKind.Docx)
        assertEquals(emptySet<String>(), doc.blocking)
        assertEquals("Seen SECRET", doc.paras[0].text)
        assertEquals(listOf(false, true), doc.paras[0].runs.map { LetterLooks.of(it.font).hidden })
        val out = String(OfficeFormats.unzip(OfficeFormats.write(doc, OfficeKind.Docx, bytes)).getValue("word/document.xml"), Charsets.UTF_8)
        assertTrue(out, Regex("<w:vanish/>(?:(?!</w:r>).)*>SECRET<").containsMatchIn(out))
        assertTrue(LetterLooks.of(" text:display=\"none\"").hidden)
        assertTrue(!LetterLooks.of("<w:vanish w:val=\"0\"/>").hidden)
    }

    @Test fun `a date or a reference field is kept with its letters and is no reason for a copy`() {
        val parts = OfficeFormats.unzip(OfficeFormats.write(sample, OfficeKind.Docx))
        val xml = String(parts.getValue("word/document.xml"), Charsets.UTF_8)
        fun field(instr: String, shown: String) = "<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText xml:space=\"preserve\"> $instr </w:instrText></w:r>" +
            "<w:r><w:fldChar w:fldCharType=\"separate\"/></w:r><w:r><w:t>$shown</w:t></w:r><w:r><w:fldChar w:fldCharType=\"end\"/></w:r>"
        parts["word/document.xml"] = xml.replaceFirst("<w:body>", "<w:body><w:p><w:r><w:t xml:space=\"preserve\">Today </w:t></w:r>${field("DATE \\@ &quot;d/M/yyyy&quot;", "13/10/2026")}" +
            "<w:r><w:t xml:space=\"preserve\">, see page </w:t></w:r>${field("PAGEREF _Toc1 \\h", "4")}</w:p>").toByteArray()
        val bytes = OfficeFormats.zip(parts, null)
        val doc = OfficeFormats.read(bytes, OfficeKind.Docx)
        assertEquals(emptySet<String>(), doc.blocking)
        assertEquals("Today 13/10/2026, see page 4", doc.paras[0].text)
        assertEquals("Update leaves a field alone", doc.paras[0], Citations.update(doc, OfficeKind.Docx).paras[0])
        val out = String(OfficeFormats.unzip(OfficeFormats.write(doc, OfficeKind.Docx, bytes)).getValue("word/document.xml"), Charsets.UTF_8)
        assertTrue(out, "DATE \\@ &quot;d/M/yyyy&quot;" in out || "DATE \\@ \"d/M/yyyy\"" in out)
        assertTrue(out, Regex("PAGEREF _Toc1 .*?separate.*?>4<").containsMatchIn(out))
        assertTrue(out, FIELD_CITE !in out)
        assertEquals(doc.paras[0].text, OfficeFormats.read(OfficeFormats.write(doc, OfficeKind.Docx, bytes), OfficeKind.Docx).paras[0].text)
    }
}
