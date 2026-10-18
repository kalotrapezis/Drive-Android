package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** PowerPoint's slides (slides.py's deck, python-pptx's default template), as LibreOffice shows them. */
class SlidesTest {
    private fun words(i: SlideItem) = (i as? SlideItem.Shape)?.text?.paras?.joinToString("\n") { p -> p.runs.joinToString("") { it.text } }

    @Test fun `a pptx deck read with its layouts, master and theme`() = check("slides.pptx")

    /** An emoji written as its two halves "&#55357;&#56420;" (Google Slides' .odp export, a user's deck, 17 Oct): read, and saved whole. */
    @Test fun `a letter beyond U+FFFF saved whole`() {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val words = "Mouse \uD83D\uDDB1 & keys \uD83D\uDE00"
        (doc.appendChild(doc.createElement("p")) as org.w3c.dom.Element).apply { textContent = words; setAttribute("name", words) }
        val back = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(SlidesEdit.domXml(doc).inputStream()).documentElement
        assertEquals(words, back.textContent); assertEquals(words, back.getAttribute("name"))
        assertEquals("&#65; \uD83D\uDE00 &#xD83D;x", OfficeFormats.joinHalves("&#65; &#xD83D;&#xDE00; &#xD83D;x"))
        // a file with the halves (as Google Slides' .odp export writes them) read as the letter
        assertEquals("a\uD83D\uDE4D", OfficeFormats.parse("<p>a&#55357;&#56909;</p>".toByteArray()).documentElement.textContent)
    }

    /**
     * Words' text effects on a slide (the Office engine, step 2): text-effects.pptx is slides.pptx with a glow on the
     * rounded box's words and an outline and a shadow on the red ones, as PowerPoint writes them (effectLst, ln).
     */
    @Test fun `text effects read from a pptx`() {
        fun run(d: Deck, words: String) = d.slides[2].items.mapNotNull { (it as? SlideItem.Shape)?.text }.flatMap { t -> t.paras.flatMap { it.runs } }.first { it.text == words }
        val d = Slides.read(File("src/test/resources/text-effects.pptx"))
        assertEquals(TextEffects(glow = 0xFFC000), TextEffects.of(run(d, "A glowing box").font))
        assertEquals(TextEffects(outline = 0x002060, shadow = true), TextEffects.of(run(d, "Bold red words, 28 pt").font))
        assertEquals(TextEffects(), TextEffects.of(run(Slides.read(File("src/test/resources/slides.pptx")), "Bold red words, 28 pt").font))
    }

    /** Words' letter looks on a slide (step 3): text-looks.pptx is slides.pptx with a paragraph of them, its line exactly 30 pt, a tab stop. */
    @Test fun `letter looks read from a pptx`() {
        val p = Slides.read(File("src/test/resources/text-looks.pptx")).slides[2].items.mapNotNull { (it as? SlideItem.Shape)?.text }.flatMap { it.paras }.first { it.runs.size == 4 }
        val (struck, up, caps, link) = p.runs
        assertTrue(struck.strike && !up.strike); assertEquals(1, up.script)
        assertTrue(LetterLooks.of(caps.font).smallCaps); assertEquals(0xFFFF00, caps.highlight); assertEquals("Calibri", DocStyles.Plain.fontName(caps.font))
        assertEquals("https://example.com/", link.link); assertEquals("\tlink", link.text)
        assertEquals(30f, p.look.lineExact!!, 0.01f); assertEquals(listOf(TabStop(288f, 'r')), p.look.tabs)
    }

    /** Text direction: a box's words turned 90° and another's 270° — PowerPoint's vert / vert270, LibreOffice's writing-mode; read back, and by LibreOffice. */
    @Test fun `text turned 90 and 270 degrees`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        fun words(i: SlideItem) = (i as? SlideItem.Shape)?.text?.paras?.joinToString(" ") { p -> p.runs.joinToString("") { it.text } }
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val items = deck.slides[2].items
            val red = items.first { words(it) == "Bold red words, 28 pt" }; val box = items.first { words(it)?.startsWith("A rounded") == true }
            val e = SlidesEdit.start(deck).copy(looks = mapOf((2 to red.anim!!) to SlidesEdit.Look(vert = 270), (2 to box.anim!!) to SlidesEdit.Look(vert = 90)))
            fun check(d: Deck, how: String) { val s = d.slides[2].items
                assertEquals(how, 270, (s.first { words(it) == "Bold red words, 28 pt" } as SlideItem.Shape).text!!.vert)
                assertEquals(how, 90, (s.first { words(it)?.startsWith("A rounded") == true } as SlideItem.Shape).text!!.vert) }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("vert-$name") // kept, for a look
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
        }
    }

    /** Set Up Slide Show: Show without animation and Use timings off — PowerPoint's showPr, LibreOffice's settings; read back, and by LibreOffice. */
    @Test fun `shown without animation and timings`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            assertFalse(name, deck.noAnimation); assertFalse(name, deck.manual)
            val e = SlidesEdit.start(deck).copy(noAnimation = true, manual = true)
            val out = scratchDir("setup").resolve("setup-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            Slides.read(out).let { assertTrue(name, it.noAnimation); assertTrue(name, it.manual); assertEquals(name, deck.slides.size, it.slides.size) }
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                // LibreOffice carries Use timings across (its force-manual), not Show without animation (26.2: its .pptx import drops showAnimation)
                Slides.read(dir.listFiles()!!.single { it.extension == other }).let { assertTrue("LibreOffice's $other of $name", it.manual) }
            }
        }
    }

    /** Reuse slides: another deck's slide 3 (a rounded box, an arrow, red words, a picture) put in after slide 1, both ways between .pptx and .odp; read back, and by LibreOffice. */
    @Test fun `slides reused from another deck`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        fun words(d: Deck, i: Int) = d.slides[i].items.mapNotNull { (it as? SlideItem.Shape)?.text?.paras?.joinToString(" ") { p -> p.runs.joinToString("") { r -> r.text } } }
        for ((name, other) in listOf("slides.odp" to "slides.pptx", "slides.pptx" to "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src); val from = Slides.read(File("src/test/resources/$other"))
            val (e, skipped) = SlidesReuse.reused(SlidesEdit.start(deck), deck, from, listOf(2), 0, scratchDir("reuse-pics"))
            assertEquals(name, 0, skipped)
            fun check(d: Deck, how: String) {
                assertEquals(how, deck.slides.size + 1, d.slides.size)
                val w = words(d, 1)
                assertTrue("$how: $w", "Bold red words, 28 pt" in w && w.any { it.startsWith("A rounded box") })
                assertTrue(how, d.slides[1].items.any { it is SlideItem.Picture })
                val red = d.slides[1].items.mapNotNull { it as? SlideItem.Shape }.first { s -> s.text?.paras?.any { p -> p.runs.any { it.text.contains("Bold red") } } == true }.text!!.paras.flatMap { it.runs }.first { it.text.isNotBlank() }
                assertTrue(how, red.bold); assertEquals(how, 0xC00000, red.color)
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = scratchDir("reuse").resolve("reused-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val conv = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", conv, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == conv }), "LibreOffice's $conv of $name")
            }
        }
    }

    /** File › Save as the other format: slides.pptx as an .odp and slides.odp as a .pptx — every slide, its words, looks, pictures, notes, transitions and hidden mark; read back and by LibreOffice. */
    @Test fun `saved as the other format`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        fun words(d: Deck, i: Int) = d.slides[i].items.mapNotNull { (it as? SlideItem.Shape)?.text?.paras?.joinToString(" ") { p -> p.runs.joinToString("") { r -> r.text } }?.filter { c -> !c.isWhitespace() && c != '•' }?.takeIf { w -> w.isNotEmpty() } }.toSet()
        for ((name, kind) in listOf("slides.pptx" to "odp", "slides.odp" to "pptx")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val out = scratchDir("convert").resolve("converted.$kind")
            SlidesReuse.convert(src, out, File("src/main/assets/blank.$kind").readBytes(), scratchDir("convert-pics"))
            fun check(d: Deck, how: String) {
                assertEquals(how, deck.slides.size, d.slides.size)
                assertEquals(how, deck.width, d.width, 1f); assertEquals(how, deck.height, d.height, 1f)
                deck.slides.indices.forEach { i -> assertTrue("$how, slide ${i + 1}: ${words(deck, i)} in ${words(d, i)}", words(d, i).containsAll(words(deck, i))) }
                assertEquals(how, deck.slides.map { it.notes.trim() }, d.slides.map { it.notes.trim() })
                assertEquals(how, deck.slides.map { it.hidden }, d.slides.map { it.hidden })
                assertEquals(how, deck.slides.map { it.transition?.kind }, d.slides.map { it.transition?.kind })
                assertEquals(how, deck.slides.map { s -> s.items.count { it is SlideItem.Picture && it.media == null } }, d.slides.map { s -> s.items.count { it is SlideItem.Picture } })
                val red = d.slides[2].items.mapNotNull { it as? SlideItem.Shape }.first { s -> s.text?.paras?.any { p -> p.runs.any { it.text.contains("Bold red") } } == true }.text!!.paras.flatMap { it.runs }.first { it.text.isNotBlank() }
                assertTrue(how, red.bold); assertEquals(how, 0xC00000, red.color)
                fun box(dk: Deck) = dk.slides[2].items.mapNotNull { it as? SlideItem.Shape }.first { s -> s.text?.paras?.any { p -> p.runs.any { it.text.startsWith("A rounded box") } } == true }.text!!.let { it.anchor to it.paras.first().para.align }
                assertEquals(how, box(deck), box(d))
            }
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val conv = if (kind == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", conv, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                assertEquals("LibreOffice's $conv of $name", deck.slides.size, Slides.read(dir.listFiles()!!.single { it.extension == conv }).slides.size)
            }
        }
    }

    /** Arrange › On every slide: a text box on the slide master (PowerPoint's master, LibreOffice's master page), on every slide when read back and by LibreOffice; then taken off every slide. */
    @Test fun `a thing on every slide`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        fun everywhere(d: Deck) = d.slides.map { sl -> sl.items.filterIsInstance<SlideItem.Shape>().firstOrNull { sh -> sh.text?.paras?.any { p -> p.runs.any { it.text == "Olive Grove Co-op" } } == true } }
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val box = SlidesEdit.Added("9100", 20f, deck.height - 40f, 200f, 30f, prst = SlidesEdit.TEXT_BOX, runs = listOf(listOf(DocRun("Olive Grove Co-op", sizePt = 12f, color = 0x2E7D32))))
            // and one typed (its words in the edit's texts, as a text box put on and typed in keeps them), its words red
            val typed = SlidesEdit.Added("9101", 300f, deck.height - 40f, 200f, 30f, prst = SlidesEdit.TEXT_BOX)
            val e = SlidesEdit.start(deck).let { it.copy(added = it.added + (SlidesEdit.MASTER to listOf(box, typed)), texts = it.texts + ((SlidesEdit.MASTER to "9101") to listOf("Typed on every slide")),
                looks = it.looks + ((SlidesEdit.MASTER to "9101") to SlidesEdit.Look(text = 0xFFC00000.toInt()))) }
            assertTrue(name, everywhere(SlidesEdit.view(deck, e)).all { it != null })
            fun typedOn(d: Deck) = d.slides.all { sl -> sl.items.filterIsInstance<SlideItem.Shape>().any { sh -> sh.text?.paras?.any { p -> p.runs.any { it.text == "Typed on every slide" && it.color == 0xC00000 } } == true } }
            assertTrue(name, typedOn(SlidesEdit.view(deck, e)))
            val out = scratchDir("every").resolve("every-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            val back = Slides.read(out)
            assertTrue("saved typed, $name", typedOn(back))
            val found = everywhere(back)
            assertTrue("$name: ${found.map { it != null }}", found.all { it != null && it.master != null && it.master!!.startsWith("added").not() })
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "-env:UserInstallation=file://${dir.path}/profile", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                assertTrue("LibreOffice's $other of $name", everywhere(Slides.read(dir.listFiles()!!.single { it.extension == other })).all { it != null })
            }
            // taken off every slide again: the master's own now
            // (each master's copy: an .odp's pages use more than one master page)
            val e2 = SlidesEdit.start(back).let { it.copy(removed = it.removed + found.mapNotNull { f -> f?.master }.toSet().map { SlidesEdit.MASTER to it }) }
            assertTrue(name, everywhere(SlidesEdit.view(back, e2)).all { it == null })
            val out2 = scratchDir("every2").resolve("off-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(out, back, e2, out2) else SlidesEdit.save(out, back, e2, out2)
            assertTrue(name, everywhere(Slides.read(out2)).all { it == null })
        }
    }

    /** Add text: a shape with no words given some — a text body of its own, its words in the middle; read back. */
    @Test fun `words added to a shape that had none`() {
        val xml = """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree>""" +
            """<p:sp><p:nvSpPr><p:cNvPr id="4" name="Heart"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:prstGeom prst="heart"><a:avLst/></a:prstGeom></p:spPr></p:sp></p:spTree></p:cSld></p:sld>"""
        val out = String(SlidesEdit.withTexts(xml.toByteArray(), mapOf("4" to listOf("Love", "it")), null))
        assertTrue(out, Regex("""</p:spPr><p:txBody><a:bodyPr[^>]*anchor="ctr"[^>]*/><a:lstStyle/><a:p><a:pPr algn="ctr"/><a:r><a:rPr[^>]*/><a:t>Love</a:t></a:r>""").containsMatchIn(out))
        assertTrue(out, Regex("""<a:t>it</a:t>""").containsMatchIn(out))
    }

    /**
     * A slide's words edited (the Office engine, step 4, "a slide's words as runs"): each letter keeps its run's look, a
     * typed one the look of the one before; a slide number left as it was stays a field, a line break stays one.
     */
    @Test fun `an edited paragraph keeps each letter's look`() {
        val xml = """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree>""" +
            """<p:sp><p:nvSpPr><p:cNvPr id="2" name="t"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:p><a:r><a:rPr b="1"/><a:t>Bold</a:t></a:r>""" +
            """<a:r><a:rPr/><a:t> and </a:t></a:r><a:r><a:rPr><a:solidFill><a:srgbClr val="FF0000"/></a:solidFill></a:rPr><a:t>red</a:t></a:r><a:br><a:rPr/></a:br>""" +
            """<a:fld id="{1}" type="slidenum"><a:rPr/><a:t>3</a:t></a:fld><a:endParaRPr sz="2000"/></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>"""
        val out = String(SlidesEdit.withTexts(xml.toByteArray(), mapOf("2" to listOf("Bold and very red${LINE_BREAK}3", "")), null))
        assertTrue(out, out.contains("""<a:rPr b="1"/><a:t>Bold</a:t>"""))
        assertTrue(out, out.contains("""<a:t> and very </a:t>"""))
        assertTrue(out, Regex("""val="FF0000"/></a:solidFill></a:rPr><a:t>red</a:t></a:r><a:br>""").containsMatchIn(out))
        assertTrue(out, out.contains("""<a:fld id="{1}" type="slidenum">"""))
        assertTrue(out, out.contains("""<a:p><a:endParaRPr sz="2000"/></a:p>""")) // the empty second line: a paragraph of its own
        // LibreOffice's text-looks.odp (its conversion of text-looks.pptx): words typed after the small capitals take their look
        val src = File("src/test/resources/text-looks.odp")
        val deck = Slides.read(src)
        val id = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { it.text?.paras?.any { p -> p.runs.size == 4 } == true }.id!!
        val e = SlidesEdit.start(deck).let { it.copy(texts = mapOf((2 to id) to listOf("struckupsmall caps and  more\tlink"))) }
        val saved = File.createTempFile("looks", ".odp"); SlidesOdpEdit.save(src, deck, e, saved)
        val (struck, up, caps, link) = Slides.read(saved).slides[2].items.filterIsInstance<SlideItem.Shape>().first { it.id == id }.text!!.paras.single().runs.let(OfficeFormats::tidy) // a run a text node, as read
        assertTrue(struck.strike); assertEquals(1, up.script); assertEquals("small caps and  more", caps.text); assertTrue(LetterLooks.of(caps.font).smallCaps)
        assertEquals("https://example.com/", link.link)
        // the same as it shows while editing
        val shown = SlidesEdit.view(deck, e).slides[2].items.filterIsInstance<SlideItem.Shape>().first { it.id == id }.text!!.paras.single().runs
        assertEquals(listOf("struck", "up", "small caps and  more", "\tlink"), shown.map { it.text }); assertTrue(shown[0].strike)
    }

    /** Slides' Home (17 Oct, Words' tools): a box's words in a face, size, looks (superscript too), colours; its paragraphs aligned, numbered, spaced; its anchor and autofit — saved, read back, opened by LibreOffice. */
    @Test fun `Home's looks on a box saved`() {
        val look = SlidesEdit.Look(font = "Georgia", size = 28f, bold = true, underline = true, strike = true, text = 0xFFC00000.toInt(), highlight = 0xFFFFFF00.toInt(),
            align = DocAlign.Center, list = '1', spacing = 1.5f, anchor = 'b', autofit = 's', script = 1)
        fun check(d: Deck, how: String) {
            val t = d.slides[1].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("Read") == true }.text!!
            val r = t.paras[0].runs[0]
            assertEquals(how, "Georgia", DocStyles.Plain.fontName(r.font)); assertEquals(how, 28f, r.sizePt!!, 0.1f); assertTrue(how, r.bold && r.underline && r.strike)
            assertEquals(how, 1, r.script) // X² (B's Home extras)
            // LibreOffice 26.2 draws the .odp's red, but its .pptx of it names the outline's theme colour instead (its exporter's)
            if (!how.startsWith("LibreOffice's pptx")) assertEquals(how, 0xC00000, r.color); assertEquals(how, 0xFFFF00, r.highlight)
            assertEquals(how, listOf("1.", "1.", "2.", "2.", "1."), t.paras.map { it.bullet?.text?.trim() }) // each level counted on its own
            assertEquals(how, DocAlign.Center, t.paras[0].para.align); assertEquals(how, "1.", t.paras[0].bullet?.text?.trim())
            assertEquals(how, 1.5f, t.paras[0].look.lineMultiple, 0.01f); assertEquals(how, 'b', t.anchor); assertTrue(how, t.shrink)
        }
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val box = deck.slides[1].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("Read") == true }
            val e = SlidesEdit.start(deck).copy(looks = mapOf((1 to box.anim!!) to look))
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("home-$name") // kept, for a look
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo")
                val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
        }
        // Clear formatting: the red bold words in their paragraph's look again (the theme's text, 18 pt)
        val src = File("src/test/resources/slides.pptx"); val deck = Slides.read(src)
        val red = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Bold red words, 28 pt" }
        val out = File.createTempFile("cleared", ".pptx")
        SlidesEdit.save(src, deck, SlidesEdit.start(deck).copy(looks = mapOf((2 to red.anim!!) to SlidesEdit.Look(cleared = true))), out)
        val r = Slides.read(out).slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Bold red words, 28 pt" }.text!!.paras[0].runs[0]
        assertTrue(!r.bold); assertEquals(18f, r.sizePt!!, 0.1f); assertEquals(0, r.color)
    }

    /** Insert › Text box: a box of words alone, typed into, in Home's looks — saved in a .pptx and an .odp, read back here and by LibreOffice. */
    @Test fun `a text box put on and typed into`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val a = SlidesEdit.Added("9200", 100f, 300f, 400f, 40f, prst = SlidesEdit.TEXT_BOX)
            val e = SlidesEdit.start(deck).copy(added = mapOf(0 to listOf(a)), texts = mapOf((0 to "9200") to listOf("Hello there", "A second line")),
                looks = mapOf((0 to "9200") to SlidesEdit.Look(bold = true)))
            fun check(d: Deck, how: String) {
                val box = d.slides[0].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("Hello") == true }
                assertEquals(how, "Hello there\nA second line", words(box)); assertTrue(how, box.text!!.paras[0].runs[0].bold)
                assertTrue(how, box.shape?.fill == null); assertEquals(how, 100f, box.x, 2f); assertTrue(how, box.h > 40f) // grown to its two lines
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("textbox-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
        }
    }

    /** Insert › WordArt (Words' looks and bends): an arch with an outline and a shadow, and a straight one; their colour, effects and bend saved and read back. */
    @Test fun `WordArt put on saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val bent = SlidesEdit.Added("9200", 100f, 100f, 360f, 130f, prst = SlidesEdit.TEXT_BOX)
            val flat = SlidesEdit.Added("9201", 100f, 300f, 360f, 55f, prst = SlidesEdit.TEXT_BOX)
            fun art(c: Int, fx: TextEffects, warp: String) = SlidesEdit.Look(text = 0xFF000000.toInt() or c, size = 36f, bold = true, align = DocAlign.Center, effects = fx, warp = warp, autofit = if (warp.isEmpty()) null else 'n')
            val e = SlidesEdit.start(deck).copy(added = mapOf(0 to listOf(bent, flat)), texts = mapOf((0 to "9200") to listOf("Arched words"), (0 to "9201") to listOf("Flat words")),
                looks = mapOf((0 to "9200") to art(0xED7D31, TextEffects(0x000000, true), "textArchUp"), (0 to "9201") to art(0x4472C4, TextEffects(glow = 0xFFC000), "")))
            fun check(d: Deck, how: String, lo: Boolean = false) {
                val shapes = d.slides[0].items.filterIsInstance<SlideItem.Shape>()
                val a = shapes.first { words(it) == "Arched words" }; val f = shapes.first { words(it) == "Flat words" }
                assertEquals(how, TextWarp.ArchUp, a.text!!.warp); assertEquals(how, null, f.text!!.warp)
                val r = a.text!!.paras[0].runs[0]
                assertEquals(how, 0xED7D31, r.color); assertEquals(how, 130f, a.h, 2f) // a bent one keeps its box
                if (!lo || name.endsWith("pptx")) { assertTrue(how, r.bold); assertEquals(how, 36f, r.sizePt!!, 0.5f) }
                if (!lo) { assertEquals(how, TextEffects(0x000000.takeIf { name.endsWith("pptx") } ?: 0, true), TextEffects.of(r.font))
                    if (name.endsWith("pptx")) assertEquals(how, 0xFFC000, TextEffects.of(f.text!!.paras[0].runs[0].font).glow) }
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("wordart-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name", true)
            }
        }
    }

    /** Review › Comments: a comment and a reply on a slide, another on a second slide; who, when, what and where saved and read back, a deleted one gone. */
    @Test fun `comments saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val first = SlideComment(DocComment("n1", "Teo K", "2026-10-17T10:00:00", "Check this slide", initials = "TK"), 100f, 40f)
            val reply = SlideComment(DocComment("n2", "Ann", "2026-10-17T10:05:00", "Done\nTwo lines"), 100f, 40f).let { it.copy(comment = it.comment.copy(parent = "n1")) }
            val e = SlidesEdit.start(deck).copy(comments = mapOf(0 to listOf(first, reply), 1 to listOf(SlideComment(DocComment("n3", "Teo K", null, "Second slide"), 10f, 10f))))
            fun check(d: Deck, how: String, lo: Boolean = false) {
                val c0 = d.slides[0].comments; val c1 = d.slides[1].comments
                assertEquals(how, listOf("Check this slide", "Done\nTwo lines"), c0.map { it.comment.text }); assertEquals(how, listOf("Teo K", "Ann"), c0.map { it.comment.author })
                // LibreOffice reads a comment's place only roughly (its own 5 cm comes back 4.76 cm)
                if (!lo) { assertEquals(how, 100f, c0[0].x, 1f); assertEquals(how, 40f, c0[0].y, 1f) }; assertTrue(how, c0[0].comment.date!!.startsWith("2026-10-17T10:00"))
                if (!lo && name.endsWith("pptx")) assertEquals(how, c0[0].comment.id, c0[1].comment.parent) // Impress keeps no threads
                assertEquals(how, listOf("Second slide"), c1.map { it.comment.text })
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("comments-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            val saved = Slides.read(out); check(saved, "saved, $name")
            // the second slide's taken off again: none left there
            val again = File("build/office-roundtrip").resolve("comments-again-$name")
            val e2 = SlidesEdit.start(saved).copy(comments = mapOf(1 to emptyList()))
            if (src.extension == "odp") SlidesOdpEdit.save(out, saved, e2, again) else SlidesEdit.save(out, saved, e2, again)
            Slides.read(again).let { assertEquals(name, 2, it.slides[0].comments.size); assertEquals(name, 0, it.slides[1].comments.size) }
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name", true)
            }
        }
    }

    /** Arrange: a shape turned 30°, mirrored and sent to the back, the red words brought to the front; kept where they were. */
    @Test fun `items turned and reordered saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val shapes = deck.slides[2].items.filterIsInstance<SlideItem.Shape>()
            val box = shapes.first { words(it) == "A rounded box" }; val red = shapes.first { words(it) == "Bold red words, 28 pt" }
            val ids = deck.slides[2].items.mapNotNull { it.anim }.distinct()
            val stack = listOf(box.anim!!) + ids.filter { it != box.anim && it != red.anim } + red.anim!!
            val e = SlidesEdit.start(deck).copy(turns = mapOf((2 to box.anim!!) to SlidesEdit.Turn(30f, flipH = true)), stack = mapOf(2 to stack),
                frames = mapOf((2 to box.anim!!) to listOf(box.x, box.y, box.w, box.h)))
            fun check(d: Deck, how: String) {
                val items = d.slides[2].items.filter { it.anim != null || it is SlideItem.Shape && it.id != null }
                val b = items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }
                assertEquals(how, 30f, b.rotation, 0.5f); assertTrue(how, b.flipH)
                assertEquals(how, box.x, b.x, 1f); assertEquals(how, box.y, b.y, 1f); assertEquals(how, box.w, b.w, 1f)
                val order = items.filterIsInstance<SlideItem.Shape>().mapNotNull { words(it) }
                assertEquals(how, "A rounded box", order.first()); assertEquals(how, "Bold red words, 28 pt", order.last())
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("arrange-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
        }
    }

    /** Shape options (Words'): a shape's outline width and alt text, a picture's alt text; saved and read back. */
    @Test fun `shape options and alt text saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val box = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }
            val pic = deck.slides[2].items.filterIsInstance<SlideItem.Picture>().first()
            val e = SlidesEdit.start(deck).copy(looks = mapOf((2 to box.anim!!) to SlidesEdit.Look(line = 0xFF000000.toInt(), lineWidth = 3f, alt = "A blue box", dash = "sysDash", shadow = true),
                (2 to pic.anim!!) to SlidesEdit.Look(alt = "A flag, blue and yellow")))
            fun check(d: Deck, how: String) {
                val b = d.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }
                assertEquals(how, 3f, b.shape!!.lineWidth, 0.1f); assertEquals(how, "A blue box", b.alt)
                assertEquals(how, "sysDash", b.shape!!.dash); assertTrue(how, b.shape!!.shadow)
                assertEquals(how, "A flag, blue and yellow", d.slides[2].items.filterIsInstance<SlideItem.Picture>().first().alt)
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("options-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
        }
    }

    /** Design › Background › Gradient: a slide's own, top to bottom — PowerPoint's gradFill, LibreOffice's gradient — read back from both and their conversions. */
    @Test fun `a gradient background saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val e = SlidesEdit.start(deck).copy(backgrounds = mapOf(1 to SlidesEdit.Back(color = 0xFFDDEBF7.toInt(), to = 0xFF2E75B6.toInt())))
            fun check(d: Deck, how: String) {
                val g = d.slides[1].backGradient; assertTrue(how, g != null)
                assertEquals(how, 0xDDEBF7, g!!.colors.first() and 0xFFFFFF); assertEquals(how, 0x2E75B6, g.colors.last() and 0xFFFFFF); assertEquals(how, 90f, g.angle, 1f)
                assertTrue(how, d.slides[0].backGradient == null)
            }
            check(Deck(deck.width, deck.height, listOf(deck.slides[0], SlidesEdit.slide(deck, e, e.order[1]))), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("gradient-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
        }
    }

    /** Design › Slide size: a 4:3 deck made 16:9 — fitted (as it was, in the middle) or maximized (bigger, its letters too, cut above and below). */
    @Test fun `slide size changed`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) for (max in listOf(false, true)) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val box = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }
            val size = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Bold red words, 28 pt" }.text!!.paras[0].runs[0].sizePt!!
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("size-${if (max) "max" else "fit"}-$name")
            SlidesSize.resize(src, SlidesSize.Size(960f, 540f, max), out)
            val (k, dx, dy) = SlidesSize.fit(deck.width, deck.height, 960f, 540f, max)
            fun check(d: Deck, how: String) {
                assertEquals(how, 960f, d.width, 1f); assertEquals(how, 540f, d.height, 1f)
                val b = d.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }
                assertEquals(how, box.x * k + dx, b.x, 1f); assertEquals(how, box.y * k + dy, b.y, 1f); assertEquals(how, box.w * k, b.w, 1f)
                assertEquals(how, size * k, d.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Bold red words, 28 pt" }.text!!.paras[0].runs[0].sizePt!!, 0.6f)
            }
            check(Slides.read(out), "$name ${if (max) "maximized" else "fitted"}")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name, ${if (max) "maximized" else "fitted"}")
            }
        }
    }

    /** Home › Reset: a title moved and made bold, saved; read again and reset — back where and as its layout has it, shown and saved. */
    @Test fun `a slide reset saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val title = deck.slides[1].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Plan" }
            assertTrue(name, title.anim!! in deck.slides[1].placeholders)
            val e0 = SlidesEdit.start(deck)
            val e = SlidesEdit.framed(e0, 1, title.anim!!, title.x + 60f, title.y + 30f, title.w, title.h).let { it.copy(looks = it.looks + ((1 to title.anim!!) to SlidesEdit.Look(bold = true))) }
            val moved = File("build/office-roundtrip").apply { mkdirs() }.resolve("unreset-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, moved) else SlidesEdit.save(src, deck, e, moved)
            val back = Slides.read(moved)
            fun plan(d: List<SlideItem>) = d.filterIsInstance<SlideItem.Shape>().first { words(it) == "Plan" }
            assertEquals(name, title.x + 60f, plan(back.slides[1].items).x, 0.5f)
            assertTrue(name, plan(back.slides[1].items).text!!.paras[0].runs[0].bold == true)
            val r = SlidesEdit.start(back).let { it.copy(reset = setOf(1)) }
            fun check(its: List<SlideItem>, how: String) { val p = plan(its)
                assertEquals(how, title.x, p.x, 1f); assertEquals(how, title.y, p.y, 1f); assertTrue(how, p.text!!.paras[0].runs[0].bold != true); assertEquals(how, "Plan", words(p)) }
            check(SlidesEdit.slide(back, r, r.order[1]).items, "shown, $name")
            val out = File("build/office-roundtrip").resolve("reset-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(moved, back, r, out) else SlidesEdit.save(moved, back, r, out)
            check(Slides.read(out).slides[1].items, "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }).slides[1].items, "LibreOffice's $other of $name")
            }
        }
    }

    /** Group (two things, then moved as one) and Ungroup (the saved group taken apart, each where it was): PowerPoint's grpSp, LibreOffice's draw:g. */
    @Test fun `things grouped and ungrouped saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val items = deck.slides[2].items
            val box = items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }
            val pic = items.filterIsInstance<SlideItem.Picture>().first()
            val e0 = SlidesEdit.start(deck)
            val g = SlidesEdit.grouped(deck, e0, e0.order[2], listOf(box.anim!!, pic.anim!!))
            val gid = g.groups[2]!!.keys.single()
            val v0 = SlidesEdit.slide(deck, g, g.order[2])
            val b0 = SlidesEdit.box(v0, gid)!!
            val e = SlidesEdit.framed(g, 2, gid, b0.x + 40f, b0.y + 20f, b0.w, b0.h)
            fun check(its: List<SlideItem>, how: String) {
                val bx = its.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }; val p = its.filterIsInstance<SlideItem.Picture>().first()
                assertTrue(how, bx.grouped && p.grouped); assertEquals(how, bx.anim, p.anim)
                assertEquals(how, box.x + 40f, bx.x, 0.5f); assertEquals(how, pic.y + 20f, p.y, 0.5f); assertEquals(how, pic.w, p.w, 0.5f)
                assertTrue(how, its.filterIsInstance<SlideItem.Shape>().first { words(it) == "Bold red words, 28 pt" }.let { !it.grouped })
            }
            check(SlidesEdit.slide(deck, e, e.order[2]).items, "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("grouped-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out).slides[2].items, "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }).slides[2].items, "LibreOffice's $other of $name")
            }
            // the saved group taken apart: each on its own, where it was
            val back = Slides.read(out); val e2 = SlidesEdit.start(back)
            val gid2 = back.slides[2].items.filterIsInstance<SlideItem.Picture>().first().anim!!
            val u = SlidesEdit.ungrouped(back, e2, e2.order[2], gid2)
            val out2 = File("build/office-roundtrip").apply { mkdirs() }.resolve("ungrouped-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(out, back, u, out2) else SlidesEdit.save(out, back, u, out2)
            val its = Slides.read(out2).slides[2].items
            val bx = its.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }; val p = its.filterIsInstance<SlideItem.Picture>().first()
            assertTrue(name, !bx.grouped && !p.grouped); assertTrue(name, bx.anim != p.anim)
            assertEquals(name, box.x + 40f, bx.x, 0.5f); assertEquals(name, pic.y + 20f, p.y, 0.5f)
        }
    }

    /** A picture cropped (Words' crop editor): the cut kept as PowerPoint's srcRect / LibreOffice's clip of the whole picture; Reset takes it away. */
    @Test fun `a picture cropped saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val pic = deck.slides[2].items.filterIsInstance<SlideItem.Picture>().first()
            val cut = listOf(0.1f, 0.2f, 0.1f, 0f)
            val e = SlidesEdit.start(deck).copy(looks = mapOf((2 to pic.anim!!) to SlidesEdit.Look(crop = cut)),
                frames = mapOf((2 to pic.anim!!) to listOf(pic.x + pic.w * 0.1f, pic.y + pic.h * 0.2f, pic.w * 0.8f, pic.h * 0.8f)))
            fun check(d: Deck, how: String) {
                val p = d.slides[2].items.filterIsInstance<SlideItem.Picture>().first()
                assertEquals(how, cut, p.crop!!.toList().map { Math.round(it * 1000) / 1000f }); assertEquals(how, pic.w * 0.8f, p.w, 1f)
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("crop-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            val saved = Slides.read(out); check(saved, "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
            // Reset: the whole picture again
            val p = saved.slides[2].items.filterIsInstance<SlideItem.Picture>().first()
            val back = File("build/office-roundtrip").resolve("crop-reset-$name")
            val e2 = SlidesEdit.start(saved).copy(looks = mapOf((2 to p.anim!!) to SlidesEdit.Look(crop = listOf(0f, 0f, 0f, 0f))))
            if (src.extension == "odp") SlidesOdpEdit.save(out, saved, e2, back) else SlidesEdit.save(out, saved, e2, back)
            assertEquals(name, null, Slides.read(back).slides[2].items.filterIsInstance<SlideItem.Picture>().first().crop)
        }
    }

    /** Home › Layout: the title slide put on another layout — its words kept, its title where that layout puts it, saved and read back. */
    @Test fun `a slide's layout changed saved`() {
        // LibreOffice's .odp of the deck has a master page for each PowerPoint layout
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        val converted = if (!lo) null else scratchDir("lo").let { dir ->
            ProcessBuilder("soffice", "--headless", "--convert-to", "odp", "--outdir", dir.path, File("src/test/resources/slides.pptx").path).redirectErrorStream(true).start().waitFor()
            File(dir, "slides.odp").takeIf { it.exists() } }
        for (src in listOfNotNull(File("src/test/resources/slides.pptx"), File("src/test/resources/slides.odp"), converted)) {
            val name = src.path; val deck = Slides.read(src)
            fun title(s: Slide) = s.items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Tetra Slides" }
            if (src.extension == "odp") {
                // an .odp page's boxes keep their own places (the master gives its looks): the page names the master chosen
                fun master(f: File) = java.util.zip.ZipFile(f).use { z -> Regex("""<draw:page\b[^>]*draw:master-page-name="([^"]*)"""").find(String(z.getInputStream(z.getEntry("content.xml")).readBytes()))?.groupValues?.get(1) }
                val k = deck.layouts.indexOfFirst { it.part != master(src) }.takeIf { it >= 0 } ?: continue
                val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("layout-${src.name}")
                SlidesOdpEdit.save(src, deck, SlidesEdit.start(deck).copy(relayout = mapOf(0 to k)), out)
                assertEquals(name, deck.layouts[k].part, master(out)); assertEquals(name, "Tetra Slides", words(title(Slides.read(out).slides[0])))
                continue
            }
            // a layout whose title stands elsewhere than the title slide's
            val k = deck.layouts.indices.firstOrNull { k -> deck.relaid!!(0, k, false)?.let { s -> Math.abs(title(s).y - title(deck.slides[0]).y) > 20f } == true }
            if (k == null) { println("$name: no layout with the title elsewhere"); continue }
            val moved = title(deck.relaid!!(0, k, false)!!)
            val e = SlidesEdit.start(deck).copy(relayout = mapOf(0 to k))
            assertEquals(name, moved.y, title(SlidesEdit.view(deck, e).slides[0]).y, 1f)
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("layout-${src.name}")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            val saved = Slides.read(out)
            assertEquals(name, moved.y, title(saved.slides[0]).y, 1f); assertEquals(name, "A test deck, 16 Oct 2026", words(saved.slides[0].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("A test") == true }))
        }
    }

    /** Home › Section (PowerPoint's): two sections saved and read back; a slide deleted afterwards leaves the list right. */
    @Test fun `sections saved`() {
        val src = File("src/test/resources/slides.pptx"); val deck = Slides.read(src)
        assertEquals(emptyList<Pair<String, Int>>(), deck.sections)
        assertEquals(null, Slides.read(File("src/test/resources/slides.odp")).sections) // Impress has none
        val e = SlidesEdit.start(deck).copy(sections = listOf("Intro" to 0, "Body" to 2))
        val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("sections.pptx")
        SlidesEdit.save(src, deck, e, out)
        val saved = Slides.read(out); assertEquals(listOf("Intro" to 0, "Body" to 2), saved.sections)
        // slide 2 deleted, the sections not touched: "Body" still begins at the same slide, now the second
        val again = File("build/office-roundtrip").resolve("sections-2.pptx")
        val e2 = SlidesEdit.start(saved).let { it.copy(order = it.order.filter { en -> en.uid != 1 }) }
        SlidesEdit.save(out, saved, e2, again)
        assertEquals(listOf("Intro" to 0, "Body" to 1), Slides.read(again).sections)
    }

    /** Design › Colours and Fonts (Words' strips) on the deck's own theme: the colour set and fonts changed, its masters and backgrounds kept. */
    @Test fun `theme colours and fonts on the deck's own design`() {
        val src = File("src/test/resources/slides.pptx")
        val own = SlidesThemes.fromFile(src)!!.copy(keep = true)
        val green = ThemeColors.Green
        val t = own.copy(colors = own.colors.take(2) + listOf(green.dk2, green.lt2) + green.accents + listOf(green.link, green.followed), major = "Georgia", minor = "Georgia")
        val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("variant.pptx")
        SlidesThemes.apply(src, t, out)
        val again = SlidesThemes.fromFile(out)!!
        assertEquals(green.accents[0], again.colors[4]); assertEquals(own.colors[0], again.colors[0]); assertEquals("Georgia", again.major)
        fun part(f: File, n: String) = java.util.zip.ZipFile(f).use { z -> String(z.getInputStream(z.getEntry(n)).readBytes()) }
        assertEquals(part(src, "ppt/slideMasters/slideMaster1.xml"), part(out, "ppt/slideMasters/slideMaster1.xml")) // the design kept
        // an .odp: its fonts only, its backgrounds and text colours kept
        val odp = File("src/test/resources/slides.odp"); val o = File("build/office-roundtrip").resolve("variant.odp")
        SlidesThemes.applyOdp(odp, SlideTheme("x", false, List(12) { 0 }, "Georgia", "Georgia", keep = true), o)
        val styles = part(o, "styles.xml")
        assertTrue(Regex("""fo:font-family="Georgia"""").containsMatchIn(styles))
        assertEquals(Regex("""draw:fill-color="[^"]*"""").findAll(part(odp, "styles.xml")).map { it.value }.toList(), Regex("""draw:fill-color="[^"]*"""").findAll(styles).map { it.value }.toList())
    }

    /** Design › Format background: a colour on one slide, a picture on another; drawn, saved and read back in both formats. */
    @Test fun `backgrounds saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        val pic = File(scratchDir("bg"), "flag.png").apply { writeBytes(Slides.read(File("src/test/resources/slides.pptx")).slides[2].items.filterIsInstance<SlideItem.Picture>().first().bytes()!!) }
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val e = SlidesEdit.start(deck).copy(backgrounds = mapOf(0 to SlidesEdit.Back(color = 0xFFFFC000.toInt()), 1 to SlidesEdit.Back(picture = pic.path)))
            fun check(d: Deck, how: String, lo: Boolean = false) {
                assertEquals(how, 0xFFC000, d.slides[0].background and 0xFFFFFF)
                val b = d.slides[1].backPicture?.invoke(); assertTrue(how, b != null && b.size > 100)
                if (!lo) assertTrue(how, b!!.contentEquals(pic.readBytes()))
                assertEquals(how, null, d.slides[2].backPicture)
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("background-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name", true)
            }
        }
    }

    /** Replace picture (and Edit drawing, which replaces it with the drawing again): the file's own picture swapped for another, in its frame. */
    @Test fun `a picture replaced saved`() {
        val png = File(scratchDir("pic"), "red.png").apply {
            val img = java.awt.image.BufferedImage(8, 6, java.awt.image.BufferedImage.TYPE_INT_RGB).also { i -> for (x in 0 until 8) for (y in 0 until 6) i.setRGB(x, y, 0xCC0000) }
            javax.imageio.ImageIO.write(img, "png", this) }
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val pic = deck.slides[2].items.filterIsInstance<SlideItem.Picture>().first()
            val e = SlidesEdit.start(deck).copy(looks = mapOf((2 to pic.anim!!) to SlidesEdit.Look(image = png.path)))
            fun check(d: Deck, how: String) {
                val p = d.slides[2].items.filterIsInstance<SlideItem.Picture>().first()
                assertTrue(how, p.bytes()!!.contentEquals(png.readBytes())); assertEquals(how, pic.w, p.w, 0.5f); assertEquals(how, pic.x, p.x, 0.5f)
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("replaced-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
        }
    }

    /** Slide show › Loop until Esc: kept in the file (PowerPoint's showPr loop, LibreOffice's endless), and taken off again. */
    @Test fun `loop until Esc saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            assertEquals(name, false, deck.loop)
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("loop-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, SlidesEdit.start(deck).copy(loop = true), out) else SlidesEdit.save(src, deck, SlidesEdit.start(deck).copy(loop = true), out)
            val saved = Slides.read(out); assertEquals(name, true, saved.loop)
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                assertEquals("LibreOffice's $other of $name", true, Slides.read(dir.listFiles()!!.single { it.extension == other }).loop)
            }
            val back = File("build/office-roundtrip").resolve("loop-off-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(out, saved, SlidesEdit.start(saved).copy(loop = false), back) else SlidesEdit.save(out, saved, SlidesEdit.start(saved).copy(loop = false), back)
            assertEquals(name, false, Slides.read(back).loop)
        }
    }

    /** References: the deck's sources and citation style kept (PowerPoint: Word's bibliography part; an .odp: its user-defined properties, which LibreOffice keeps). */
    @Test fun `sources saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        val src1 = DocSource("Smi20", DocSource.Type.Article, listOf(DocSource.Person("Smith", "Anna")), "2020", "Reading on screens", "Journal of Reading", volume = "12", pages = "3-9")
        val src2 = DocSource("Παπ19", DocSource.Type.Book, listOf(DocSource.Person("Παπαδόπουλος", "Γιώργος")), "2019", "Η τάξη", publisher = "Gutenberg")
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            assertEquals(name, emptyList<DocSource>(), deck.sources)
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("sources-$name")
            val e = SlidesEdit.start(deck).copy(sources = listOf(src1, src2), citeStyle = CiteStyle.MLA)
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            val saved = Slides.read(out)
            assertEquals(name, listOf("Smi20", "Παπ19"), saved.sources.map { it.tag }); assertEquals(name, "Παπαδόπουλος", saved.sources[1].authors[0].last)
            assertEquals(name, CiteStyle.MLA, saved.citeStyle)
            if (lo && name.endsWith("odp")) {
                val dir = scratchDir("lo"); val copy = File(dir, "again.odp").also { out.copyTo(it) }
                ProcessBuilder("soffice", "--headless", "--convert-to", "odp:impress8", "--outdir", File(dir, "out").path, copy.path).redirectErrorStream(true).start().waitFor()
                assertEquals("LibreOffice's own save", listOf("Smi20", "Παπ19"), Slides.read(File(dir, "out/again.odp")).sources.map { it.tag })
            }
        }
    }

    /** References and Contents slides: a text box put on with words of their own looks — an italic title, a link to a slide — saved and read back. */
    @Test fun `words with their own looks put on saved`() {
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val runs = listOf(listOf(DocRun("Smith, A. (2020). ", sizePt = 16f), DocRun("Reading on screens", italic = true, sizePt = 16f), DocRun(".", sizePt = 16f)),
                listOf(DocRun("4   ", sizePt = 20f), DocRun("Scores", sizePt = 20f, link = "slide:3")))
            val e = SlidesEdit.start(deck).copy(added = mapOf(0 to listOf(SlidesEdit.Added("9300", 50f, 300f, 500f, 40f, prst = SlidesEdit.TEXT_BOX, runs = runs, hang = 36f))))
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("runs-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            val box = Slides.read(out).slides[0].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("Smith") == true }
            val p0 = box.text!!.paras[0]; val p1 = box.text!!.paras[1]
            assertTrue(name, p0.runs.any { it.italic && it.text == "Reading on screens" }); assertTrue(name, p0.runs.none { !it.italic && it.text.contains("Reading") })
            assertEquals(name, 36f, p0.look.left, 1f)
            assertTrue(name, p1.runs.first { it.text == "Scores" }.link != null)
        }
    }

    /** Insert › Link on a picture (the user, 17 Oct: maps that zoom in): the object's own click — to a slide, and back — saved and read back. */
    @Test fun `a picture linked to a slide saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val pic = deck.slides[2].items.filterIsInstance<SlideItem.Picture>().first()
            val box = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }
            val e = SlidesEdit.start(deck).copy(looks = mapOf((2 to pic.anim!!) to SlidesEdit.Look(objectLink = "slide:3"), (2 to box.anim!!) to SlidesEdit.Look(objectLink = "back")))
            fun check(d: Deck, how: String, back: Boolean) {
                val p = d.slides[2].items.filterIsInstance<SlideItem.Picture>().first()
                assertEquals(how, d.slides[3].part, p.link)
                if (back) assertEquals(how, "back", d.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }.link)
            }
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("objlink-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name", name.endsWith("pptx")) // LibreOffice has no "the slide it came from"
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name", false)
            }
        }
    }

    /** Insert › Video (asked 17 Oct): a film from the phone kept in the deck, and an online one linked, each with its picture; saved and read back. */
    /** Picture styles and Transparency (B, 17 Oct): a frame with a shadow and 30 % see-through, saved as PowerPoint's and LibreOffice's, read back, through LibreOffice. */
    @Test fun `a picture's style and transparency saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val pic = deck.slides[2].items.filterIsInstance<SlideItem.Picture>().first()
            assertEquals(name, null, pic.pic)
            val e = SlidesEdit.start(deck).copy(looks = mapOf((2 to pic.anim!!) to SlidesEdit.Look(alpha = 0.7f, picStyle = PictureLook(line = 0xFFFFFF, lineW = 6f, shadow = true))))
            fun check(d: Deck, how: String) {
                val l = d.slides[2].items.filterIsInstance<SlideItem.Picture>().first().pic!!
                assertEquals(how, 0.7f, l.alpha, 0.01f); assertEquals(how, 0xFFFFFF, l.line); assertEquals(how, 6f, l.lineW, 0.2f); assertTrue(how, l.shadow)
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("picstyle-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
            // rounded (PowerPoint's roundRect; an .odp keeps no rounded picture)
            if (name.endsWith("pptx")) {
                val e2 = SlidesEdit.start(deck).copy(looks = mapOf((2 to pic.anim!!) to SlidesEdit.Look(picStyle = PictureLook(round = Slides.ROUND))))
                val out2 = File("build/office-roundtrip").resolve("rounded-$name"); SlidesEdit.save(src, deck, e2, out2)
                assertEquals(Slides.ROUND, Slides.read(out2).slides[2].items.filterIsInstance<SlideItem.Picture>().first().pic!!.round, 0.001f)
            }
        }
    }

    /** Home › Selection pane: a thing renamed and hidden, saved as PowerPoint's (cNvPr name, hidden) and LibreOffice's (draw:name, drawooo:display), read back, through LibreOffice. */
    @Test fun `a thing named and hidden saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val box = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("A rounded") == true }
            assertTrue(name, !box.hidden)
            val e = SlidesEdit.start(deck).copy(looks = mapOf((2 to box.anim!!) to SlidesEdit.Look(name = "The blue box", hide = true)))
            fun check(d: Deck, how: String) {
                val b = d.slides[2].items.first { (it as? SlideItem.Shape)?.let(::words)?.startsWith("A rounded") == true }
                assertEquals(how, "The blue box", b.name); assertTrue(how, b.hidden)
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("hidden-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                // (a shape of its own: on a placeholder LibreOffice 26.2 loses hidden both ways, and its .pptx renames placeholders)
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
            // shown again: no longer hidden once saved
            val again = Slides.read(out); val b2 = again.slides[2].items.first { (it as? SlideItem.Shape)?.let(::words)?.startsWith("A rounded") == true }
            val out2 = File("build/office-roundtrip").resolve("shown-$name")
            val e2 = SlidesEdit.start(again).copy(looks = mapOf((2 to b2.anim!!) to SlidesEdit.Look(hide = false)))
            if (src.extension == "odp") SlidesOdpEdit.save(out, again, e2, out2) else SlidesEdit.save(out, again, e2, out2)
            assertTrue(name, !Slides.read(out2).slides[2].items.first { (it as? SlideItem.Shape)?.let(::words)?.startsWith("A rounded") == true }.hidden)
        }
    }

    /** A text box's Layout (B, 17 Oct): inner margins, wrap and columns saved as PowerPoint's and LibreOffice's, read back, through LibreOffice. */
    @Test fun `a box's layout saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val box = deck.slides[1].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("Read") == true }
            val e = SlidesEdit.start(deck).copy(looks = mapOf((1 to box.anim!!) to SlidesEdit.Look(insets = listOf(14f, 10f, 20f, 6f), cols = 2, colGap = 18f)))
            fun check(d: Deck, how: String) {
                val t = d.slides[1].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("Read") == true }.text!!
                listOf(14f, 10f, 20f, 6f).forEachIndexed { i, m -> assertEquals("$how margin $i", m, t.insets[i], 0.2f) }
                assertEquals(how, 2, t.cols); assertEquals(how, 18f, t.colGap, 0.2f)
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("layout-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
            // not wrapped: saved as such
            val e2 = SlidesEdit.start(deck).copy(looks = mapOf((1 to box.anim!!) to SlidesEdit.Look(wrap = false)))
            val out2 = File("build/office-roundtrip").resolve("nowrap-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e2, out2) else SlidesEdit.save(src, deck, e2, out2)
            assertEquals(name, false, Slides.read(out2).slides[1].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("Read") == true }.text!!.wrap)
        }
    }

    /** Table tab (Words'): rows and columns put in and taken away round a cell of a table put on a slide, the words staying in their cells. */
    @Test fun `a table's rows and columns`() {
        val t = SlidesEdit.Added("t", 0f, 0f, 200f, 100f, prst = "table", rows = 2, cols = 2, cells = listOf("a", "b", "c", "d"))
        val r = SlidesEdit.rowAdded(t, 1)
        assertEquals(listOf("a", "b", "", "", "c", "d"), r.cells); assertEquals(3, r.rows); assertEquals(150f, r.h, 0.01f)
        val c = SlidesEdit.columnAdded(t, 0)
        assertEquals(listOf("", "a", "b", "", "c", "d"), c.cells); assertEquals(3, c.cols); assertEquals(300f, c.w, 0.01f)
        assertEquals(t, SlidesEdit.rowTaken(r, 1)); assertEquals(t, SlidesEdit.columnTaken(c, 0))
        assertEquals(listOf("b", "d"), SlidesEdit.columnTaken(t, 0).cells)
        assertEquals(SlidesEdit.rowTaken(t, 0).let { it.rows to it.cells }, 1 to listOf("c", "d"))
    }

    /** Home › Multilevel list (Words' schemes) and Paragraph (Words' page) on a box with two levels: 1. a. b. 2. a., space before and after, line spacing exactly. */
    @Test fun `a multilevel list and a paragraph look saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val body = deck.slides[1].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("Read the slides") == true }
            val e = SlidesEdit.start(deck).copy(looks = mapOf((1 to body.anim!!) to SlidesEdit.Look(scheme = 1, para = ParaLook(before = 6f, after = 3f, lineExact = 26f, left = 36f, firstLine = -18f))))
            fun check(d: Deck, how: String, lo: Boolean = false) {
                val b = d.slides[1].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("Read the slides") == true }
                assertEquals(how, listOf("1.", "a.", "b.", "2.", "a."), b.text!!.paras.map { it.bullet?.text })
                val q = b.text!!.paras[0].look
                assertEquals(how, 6f, q.before, 0.6f); assertEquals(how, 3f, q.after, 0.6f); assertEquals(how, 26f, q.lineExact ?: 0f, 0.6f)
                if (!lo && !how.endsWith("odp")) { assertEquals(how, 36f, q.left, 0.6f); assertEquals(how, -18f, q.firstLine, 0.6f) } // an .odp list paragraph's margin is on top of its list's indent
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("multilevel-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name", true)
            }
        }
    }

    /** The Table tab on a deck's own table (one saved here, then read as the file's): a row and a column added, another row taken — in the XML, the frame grown. */
    @Test fun `a deck's own table's rows and columns saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val e0 = SlidesEdit.start(deck)
            val put = e0.copy(added = mapOf(1 to listOf(SlidesEdit.Added(e0.newId(1, deck.slides[1].items.mapNotNull { it.anim }), 100f, 300f, 300f, 90f, prst = "table", rows = 3, cols = 2, cells = listOf("Year", "Olives", "2025", "12", "2026", "15")))))
            val made = File("build/office-roundtrip").apply { mkdirs() }.resolve("own-table0-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, put, made) else SlidesEdit.save(src, deck, put, made)
            val own = Slides.read(made); val t = own.slides[1].items.filterIsInstance<SlideItem.Table>().single()
            fun text(c: Pair<Int?, SlideText?>) = c.second?.paras?.joinToString("") { p -> p.runs.joinToString("") { it.text } }.orEmpty()
            // a row below the first, a column left of the second, the last row taken
            val e = SlidesEdit.start(own).copy(tables = mapOf((1 to t.anim!!) to listOf('r' to 1, 'c' to 1, 'R' to 3)))
            fun check(d: Deck, how: String) {
                val n = d.slides[1].items.filterIsInstance<SlideItem.Table>().single()
                assertEquals(how, listOf(listOf("Year", "", "Olives"), listOf("", "", ""), listOf("2025", "", "12")), n.cells.map { r -> r.map(::text) })
                assertEquals(how, t.w * 1.5f, n.w, 2f); assertEquals(how, t.h, n.h, 2f)
            }
            check(SlidesEdit.view(own, e), "shown, $name")
            val out = File("build/office-roundtrip").resolve("own-table-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(made, own, e, out) else SlidesEdit.save(made, own, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
        }
    }

    @Test fun `videos put on saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        val dir = scratchDir("video")
        val poster = File(dir, "poster.png").apply { writeBytes(Slides.read(File("src/test/resources/slides.pptx")).slides[2].items.filterIsInstance<SlideItem.Picture>().first().bytes()!!) }
        val film = File(dir, "film.mp4").apply { writeBytes(ByteArray(2048) { (it % 251).toByte() }) }
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val e = SlidesEdit.start(deck).copy(added = mapOf(1 to listOf(SlidesEdit.Added("9400", 100f, 100f, 320f, 180f, image = poster.path, video = film.path),
                SlidesEdit.Added("9401", 100f, 300f, 320f, 180f, image = poster.path, video = url))))
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("video-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            val films = Slides.read(out).slides[1].items.filterIsInstance<SlideItem.Picture>().filter { it.media != null }
            assertEquals(name, 2, films.size)
            val inside = films.first { !it.media!!.startsWith("http") }.media!!
            assertTrue(name, java.util.zip.ZipFile(out).use { z -> z.getInputStream(z.getEntry(inside)).readBytes().contentEquals(film.readBytes()) })
            assertEquals(name, url, films.first { it.media!!.startsWith("http") }.media)
            assertTrue(name, films.all { (it.bytes()?.size ?: 0) > 100 }) // their pictures
            if (lo) {
                val d2 = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", d2.path, out.path).redirectErrorStream(true).start().waitFor()
                assertEquals("LibreOffice's $other of $name", 2, Slides.read(d2.listFiles()!!.single { it.extension == other }).slides[1].items.mapNotNull { it.media }.size)
            }
        }
    }

    /** Insert › Drop cap on a slide: the first letter raised (a run of its own, 3 × the words' size, its own colour), and made plain again. */
    @Test fun `a raised first letter saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val red = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Bold red words, 28 pt" }
            fun look(l: SlidesEdit.Look) = SlidesEdit.start(deck).copy(looks = mapOf((2 to red.anim!!) to l))
            fun runs(d: Deck) = d.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Bold red words, 28 pt" }.text!!.paras[0].runs.let(OfficeFormats::tidy)
            fun check(d: Deck, how: String) { val r = runs(d)
                assertEquals(how, "B", r[0].text); assertEquals(how, 84f, r[0].sizePt!!, 0.5f); assertEquals(how, 0x0070C0, r[0].color); assertEquals(how, 28f, r[1].sizePt!!, 0.5f) }
            val e = look(SlidesEdit.Look(raise = 84f, raiseColor = 0xFF0070C0.toInt()))
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("raised-$name")
            fun save(from: File, d: Deck, ed: SlidesEdit.Edit, to: File) = if (from.extension == "odp") SlidesOdpEdit.save(from, d, ed, to) else SlidesEdit.save(from, d, ed, to)
            save(src, deck, e, out); check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
            // None: the saved one's letter made plain again
            val again = Slides.read(out); val plain = File.createTempFile("plain", "." + src.extension)
            val box = again.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Bold red words, 28 pt" }
            save(out, again, SlidesEdit.start(again).copy(looks = mapOf((2 to box.anim!!) to SlidesEdit.Look(raise = 0f))), plain)
            assertEquals(name, 28f, runs(Slides.read(plain))[0].sizePt!!, 0.5f)
        }
    }

    /**
     * Insert › Drop cap on a slide, dropped: the first letter in a text box of its own, the paragraph split where its lines beside it end — the
     * lines before standing in (no space under them), the rest under them (no space over it), each letter's look kept. As the screen makes it.
     */
    @Test fun `a dropped first letter saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val red = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Bold red words, 28 pt" }
            val id = red.anim!!
            val e = SlidesEdit.start(deck).copy(texts = mapOf((2 to id) to listOf("old red words,", "28 pt"), (2 to "9300") to listOf("B")),
                looks = mapOf((2 to id) to SlidesEdit.Look(drop = 60f), (2 to "9300") to SlidesEdit.Look(size = 84f, bold = true, text = 0xFFC00000.toInt())),
                added = mapOf(2 to listOf(SlidesEdit.Added("9300", red.x, red.y, 70f, 100f, prst = SlidesEdit.TEXT_BOX))))
            fun check(d: Deck, how: String) {
                val shapes = d.slides[2].items.filterIsInstance<SlideItem.Shape>()
                val ps = shapes.first { words(it) == "old red words,\n28 pt" }.text!!.paras
                assertEquals(how, 60f, ps[0].look.left, 1f); assertEquals(how, 0f, ps[0].look.after, 0.1f); assertEquals(how, 0f, ps[1].look.before, 0.1f)
                assertTrue(how, ps.all { p -> p.runs.filter { it.text.isNotBlank() }.all { it.bold && it.color == 0xC00000 } }) // each letter's look kept
                val letter = shapes.first { words(it) == "B" }.text!!.paras[0].runs[0]
                assertEquals(how, 84f, letter.sizePt!!, 0.5f); assertEquals(how, 0xC00000, letter.color)
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("dropped-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
        }
    }

    /** Insert › Chart: Words' chart put on a slide — saved as PowerPoint's chart part (with its workbook) and LibreOffice's chart object, read back here and by LibreOffice. */
    @Test fun `a chart put on saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        val ch = DocChart(ChartKind.Column, "Apples", listOf("Mon", "Tue", "Wed"), listOf(ChartSeries("Sold", listOf(3.0, 5.0, 2.0)), ChartSeries("Eaten", listOf(1.0, 2.0, 4.0))))
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val e = SlidesEdit.start(deck).copy(added = mapOf(1 to listOf(SlidesEdit.Added("9400", 100f, 100f, 400f, 300f, prst = "chart", chart = ch))))
            fun check(d: Deck, how: String) {
                val got = d.slides[1].items.filterIsInstance<SlideItem.Chart>().single().chart
                assertEquals(how, listOf("Mon", "Tue", "Wed"), got.categories); assertEquals(how, listOf(1.0, 2.0, 4.0), got.series[1].values)
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("chart-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            assertEquals(name, 1, Slides.read(out).slides[3].items.count { it is SlideItem.Chart }) // the deck's own chart as it was
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
        }
    }

    /** Insert › Link: a box's words linked to a web page, another's to a slide of the deck — saved as PowerPoint's hlinkClick and LibreOffice's text:a, read back. */
    @Test fun `links put on saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            fun box(d: Deck, i: Int, start: String) = d.slides[i].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith(start) == true }
            val red = box(deck, 2, "Bold red").anim!!; val plan = box(deck, 1, "Read the slides").anim!!
            val e = SlidesEdit.start(deck).copy(looks = mapOf((2 to red) to SlidesEdit.Look(link = "https://example.com/a"), (1 to plan) to SlidesEdit.Look(link = "slide:3")))
            fun links(d: Deck, i: Int, start: String) = box(d, i, start).text!!.paras.flatMap { it.runs }.filter { it.text.isNotBlank() }.map { it.link }.distinct()
            fun check(d: Deck, how: String, slide: (Deck) -> String) {
                assertEquals(how, listOf("https://example.com/a"), links(d, 2, "Bold red"))
                assertEquals(how, listOf(slide(d)), links(d, 1, "Read the slides"))
            }
            check(SlidesEdit.view(deck, e), "shown, $name") { "slide:3" }
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("links-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            // a slide's link: its part (.pptx), the page's name (.odp)
            fun target(d: Deck) = if (d.slides[3].part.endsWith(".xml")) d.slides[3].part else links(d, 1, "Read the slides").single()!!
            check(Slides.read(out), "saved, $name", ::target)
            if (src.extension == "odp") assertTrue(target(Slides.read(out)).startsWith("#"))
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                val back = Slides.read(dir.listFiles()!!.single { it.extension == other })
                assertEquals("LibreOffice's $other of $name", listOf("https://example.com/a"), links(back, 2, "Bold red"))
                assertTrue("LibreOffice's $other of $name: ${links(back, 1, "Read the slides")}", links(back, 1, "Read the slides").single() != null)
            }
            // taken off again
            val off = File.createTempFile("off", "." + src.extension); val again = Slides.read(out)
            val e2 = SlidesEdit.start(again).copy(looks = mapOf((2 to box(again, 2, "Bold red").anim!!) to SlidesEdit.Look(link = "")))
            if (src.extension == "odp") SlidesOdpEdit.save(out, again, e2, off) else SlidesEdit.save(out, again, e2, off)
            assertEquals(name, listOf<String?>(null), links(Slides.read(off), 2, "Bold red"))
        }
    }

    /** Insert › Header & footer: a fixed date, the slide's number and a footer on every slide but the title slide — saved, read back here and by LibreOffice. */
    @Test fun `header and footer saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            assertTrue(name, deck.slides[0].titleSlide); assertTrue(name, !deck.slides[1].titleSlide)
            val f = SlidesEdit.Footers(date = true, fixed = "1 May 2026", number = true, text = "Tetra talk")
            val e0 = SlidesEdit.start(deck)
            val e = e0.copy(footers = e0.order.associate { it.uid to if (deck.slides[it.src].titleSlide) SlidesEdit.Footers() else f })
            fun words(d: Deck, i: Int) = d.slides[i].items.filterIsInstance<SlideItem.Shape>().mapNotNull { words(it) }.toSet()
            fun check(d: Deck, how: String) {
                assertTrue("$how: ${words(d, 1)}", words(d, 1).containsAll(listOf("1 May 2026", "2", "Tetra talk")))
                assertTrue("$how: ${words(d, 3)}", words(d, 3).containsAll(listOf("4", "Tetra talk")))
                assertTrue("$how: ${words(d, 0)}", "Tetra talk" !in words(d, 0))
                // in the layout's places: the number at the right, under the words
                val n = d.slides[1].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "2" }
                assertTrue(how, n.x > d.width / 2 && n.y > d.height * 0.8f)
            }
            check(SlidesEdit.view(deck, e), "shown, $name")
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("footers-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            check(Slides.read(out), "saved, $name")
            if (lo) {
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                check(Slides.read(dir.listFiles()!!.single { it.extension == other }), "LibreOffice's $other of $name")
            }
        }
    }

    /** Insert › Equation: Words' equation as PowerPoint keeps one (a14:m over its picture) and LibreOffice (a Math object, its picture the replacement), read back with its equation. */
    @Test fun `an equation put on saved`() {
        val lo = runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0
        val png = File.createTempFile("equation", ".png").also { javax.imageio.ImageIO.write(java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", it) }
        val m = OfficeMath.fromLinear("x^2+1")
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val e = SlidesEdit.start(deck).copy(added = mapOf(0 to listOf(SlidesEdit.Added("9600", 200f, 400f, 120f, 60f, image = png.path, ext = "png", math = m))))
            fun eq(d: Deck) = d.slides[0].items.filterIsInstance<SlideItem.Picture>().single { kotlin.math.abs(it.x - 200f) < 2f && kotlin.math.abs(it.y - 400f) < 2f }
            assertEquals("shown, $name", "x^2+1", OfficeMath.linear(eq(SlidesEdit.view(deck, e)).math!!))
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("equation-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            val back = eq(Slides.read(out))
            assertEquals("saved, $name", "x^2+1", OfficeMath.linear(back.math!!)); assertTrue(name, back.bytes()!!.isNotEmpty())
            if (lo) {
                // LibreOffice: a .pptx's equation as its picture (it draws no a14:m), an .odp's as its Math object
                val dir = scratchDir("lo"); val other = if (src.extension == "odp") "pptx" else "odp"
                ProcessBuilder("soffice", "--headless", "--convert-to", other, "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
                val d = Slides.read(dir.listFiles()!!.single { it.extension == other })
                assertTrue("LibreOffice's $other of $name", d.slides[0].items.any { kotlin.math.abs(it.x - 200f) < 2f && kotlin.math.abs(it.y - 400f) < 2f })
                // its .pptx keeps the equation (PowerPoint's a14:m), read as one here
                if (other == "pptx") assertEquals("x^2+1", OfficeMath.linear(eq(d).math!!))
            }
        }
    }

    /** A line break typed in a paragraph (Insert › Line break): saved as PowerPoint's a:br and LibreOffice's text:line-break. */
    @Test fun `a line break typed saved`() {
        for (name in listOf("slides.pptx", "slides.odp")) {
            val src = File("src/test/resources/$name"); val deck = Slides.read(src)
            val red = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Bold red words, 28 pt" }
            val e = SlidesEdit.start(deck).copy(texts = mapOf((2 to red.anim!!) to listOf("Bold red${LINE_BREAK}words, 28 pt")))
            val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("linebreak-$name")
            if (src.extension == "odp") SlidesOdpEdit.save(src, deck, e, out) else SlidesEdit.save(src, deck, e, out)
            val box = Slides.read(out).slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("Bold red") == true }
            assertEquals(name, 1, box.text!!.paras.size); assertEquals(name, "Bold red${LINE_BREAK}words, 28 pt", box.text.paras[0].runs.joinToString("") { it.text })
        }
    }

    /** The same deck as LibreOffice keeps it (slides.odp: LibreOffice's of slides.pptx): read the same. */
    @Test fun `an odp deck read with its styles and lists`() = check("slides.odp")

    private fun check(name: String) {
        val d = Slides.read(File("src/test/resources/$name"))
        assertEquals(720f, d.width, 0.5f); assertEquals(540f, d.height, 0.5f) // 10 × 7.5 in
        assertEquals(4, d.slides.size)
        // the title slide: its placeholders' places and looks from the layout and the master
        val title = d.slides[0].items.first { words(it) == "Tetra Slides" } as SlideItem.Shape
        assertTrue(title.w > 500 && title.y > 100) // the layout's centred title box
        assertEquals(44f, title.text!!.paras[0].runs[0].sizePt!!, 0.1f) // the master's title style
        assertEquals(DocAlign.Center, title.text.paras[0].para.align)
        assertEquals("Say hello first.", d.slides[0].notes)
        // bullets in two levels, the second smaller and further in
        val body = d.slides[1].items.first { words(it)?.startsWith("Read the slides") == true } as SlideItem.Shape
        val paras = body.text!!.paras
        assertEquals(listOf(0, 1, 1, 0, 1), paras.map { it.level })
        assertEquals("•", paras[0].bullet?.text); assertTrue(paras[1].look.left > paras[0].look.left); assertTrue(paras[1].runs[0].sizePt!! < paras[0].runs[0].sizePt!!)
        // shapes, words of their own, a picture
        val third = d.slides[2].items
        val shapes = third.filterIsInstance<SlideItem.Shape>()
        assertEquals("roundRect", shapes.first { words(it) == "A rounded box" }.shape!!.geometry)
        assertEquals(0xFF70AD47.toInt(), shapes.first { it.shape?.geometry == "rightArrow" }.shape!!.fill)
        val red = shapes.first { words(it) == "Bold red words, 28 pt" }.text!!.paras[0].runs[0]
        assertTrue(red.bold); assertEquals(28f, red.sizePt!!, 0.1f); assertEquals(0xC00000, red.color)
        assertEquals(1, third.count { it is SlideItem.Picture })
        // a chart
        val chart = d.slides[3].items.filterIsInstance<SlideItem.Chart>().single().chart
        assertEquals(listOf(120.0, 90.0, 150.0), chart.series[0].values)
    }

    /** Edits saved into the .pptx (moved, duplicated, deleted, hidden, words changed), read back here and by LibreOffice. */
    @Test fun `a pptx edited and saved`() {
        val src = File("src/test/resources/slides.pptx")
        val deck = Slides.read(src)
        var e = SlidesEdit.start(deck)
        // duplicate "Plan", delete "Scores", the shapes' slide first, the title slide hidden and retitled
        e = e.copy(order = e.order.toMutableList().apply { add(2, SlidesEdit.Entry(1, e.next())) })
        e = e.copy(order = e.order.filter { it.src != 3 })
        e = e.copy(order = e.order.toMutableList().apply { val s = first { it.src == 2 }; remove(s); add(0, s) })
        val title = deck.slides[0].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Tetra Slides" }
        e = e.copy(hidden = mapOf(0 to true), texts = mapOf((0 to title.id!!) to listOf("Tetra Slides, edited")))
        // a new slide of "Title and Content" at the end, its title typed, its body left empty (no hint saved)
        val layout = deck.layouts.indexOfFirst { it.name == "Title and Content" }
        assertTrue(deck.layouts.size >= 5 && layout >= 0)
        val titleId = deck.layouts[layout].preview.items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Title" }.id!!
        val uid = e.next()
        e = e.copy(order = e.order + SlidesEdit.Entry(-1, uid, layout), texts = e.texts + ((uid to titleId) to listOf("A new slide")))
        // a picture and a star put on the shapes' slide (uid 2)
        val png = File.createTempFile("pic", ".png").apply { writeBytes(File("src/test/resources/slides.pptx").let { java.util.zip.ZipFile(it).use { z -> z.getInputStream(z.entries().asSequence().first { en -> en.name.startsWith("ppt/media/") }).readBytes() } }) }
        e = e.copy(added = mapOf(2 to listOf(SlidesEdit.Added(e.newId(2), 400f, 300f, 120f, 80f, image = png.path), SlidesEdit.Added("9100", 550f, 50f, 100f, 100f, prst = "star5", fill = 0xFFFFC000.toInt()),
            SlidesEdit.Added("9101", 60f, 360f, 300f, 72f, prst = "table", rows = 2, cols = 2, cells = listOf("Name", "Score", "Anna", "18")))))
        val out = File.createTempFile("edited", ".pptx")
        SlidesEdit.save(src, deck, e, out)
        val back = Slides.read(out)
        assertEquals(5, back.slides.size)
        assertEquals("A new slide", back.slides[4].items.firstNotNullOf { i -> words(i)?.takeIf { !(i as SlideItem.Shape).hint } }) // its title
        assertTrue(back.slides[4].items.none { words(it) == "Text" && !(it as SlideItem.Shape).hint }) // the hint not saved: an empty box again
        assertTrue(back.slides[0].items.any { words(it) == "A rounded box" }) // moved first
        assertEquals(2, back.slides[0].items.count { it is SlideItem.Picture }) // its own and the one put on it
        val table = back.slides[0].items.filterIsInstance<SlideItem.Table>().single()
        assertEquals(listOf("Name", "Score", "Anna", "18"), table.cells.flatten().map { (_, t) -> t?.paras?.joinToString { p -> p.runs.joinToString("") { it.text } }.orEmpty() })
        assertEquals(0xFF4F81BD.toInt(), table.cells[0][0].first) // PowerPoint's default style: the first row in the theme's accent (this deck's: Office 2007's)
        assertEquals(0xFFFFC000.toInt(), back.slides[0].items.filterIsInstance<SlideItem.Shape>().first { it.shape?.geometry == "star5" }.shape!!.fill)
        assertTrue(back.slides[0].items.filterIsInstance<SlideItem.Picture>().any { it.bytes()?.size == png.length().toInt() })
        assertTrue(back.slides[1].hidden)
        assertEquals("Tetra Slides, edited", back.slides[1].items.firstNotNullOf { words(it)?.takeIf { w -> w.startsWith("Tetra") } })
        assertEquals(44f, (back.slides[1].items.first { words(it)?.startsWith("Tetra") == true } as SlideItem.Shape).text!!.paras[0].runs[0].sizePt!!, 0.1f) // its look kept
        assertEquals(2, back.slides.count { s -> s.items.any { words(it)?.startsWith("Plan") == true } }) // duplicated
        assertTrue(back.slides.none { s -> s.items.any { it is SlideItem.Chart } }) // deleted
        assertEquals("Say hello first.", back.slides[1].notes) // its notes went with it
        // LibreOffice opens it: four pages
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
            val dir = scratchDir("lo")
            ProcessBuilder("soffice", "--headless", "--convert-to", "odp", "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
            val odp = dir.listFiles()!!.single { it.extension == "odp" }
            val lo = Slides.read(odp)
            assertEquals(5, lo.slides.size)
            assertTrue(lo.slides[1].items.any { words(it) == "Tetra Slides, edited" })
        }
    }

    /** Colours, a transition and click animations chosen, saved into the .pptx, read back here and by LibreOffice. */
    @Test fun `looks, transitions and animations saved`() {
        val src = File("src/test/resources/slides.pptx")
        val deck = Slides.read(src)
        val box = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }
        var e = SlidesEdit.start(deck)
        e = e.copy(added = mapOf(2 to listOf(SlidesEdit.Added("9100", 550f, 50f, 100f, 100f, prst = "star5"))),
            looks = mapOf((2 to box.id!!) to SlidesEdit.Look(fill = 0xFFC00000.toInt(), line = SlidesEdit.NONE, text = 0xFFFFFFFF.toInt()), (2 to "9100") to SlidesEdit.Look(fill = 0xFF00B050.toInt())),
            transitions = mapOf(2 to SlideTransition("wipe", "d", 1000), 0 to SlideTransition("", null, 750)),
            anims = mapOf(2 to listOf(SlideEffect(box.anim!!, "fade", 'd', 0, 500), SlideEffect("9100", "fly", 'l', 0, 1000), SlideEffect(box.anim!!, "fly", 'r', 0, 500, exit = true))),
            frames = mapOf((2 to box.id!!) to listOf(100f, 120f, 200f, 90f)), // the file's own box moved and sized
            removed = setOf(2 to deck.slides[2].items.first { it is SlideItem.Picture }.anim!!)) // and its picture deleted
        val pictures = deck.slides[2].items.count { it is SlideItem.Picture }
        // as it shows while editing
        SlidesEdit.view(deck, e).slides[2].let { s ->
            assertEquals("wipe", s.transition!!.kind); assertEquals(listOf("fade", "fly", "fly"), s.steps.map { it.effects.single().kind })
            assertEquals(0xFF00B050.toInt(), s.items.filterIsInstance<SlideItem.Shape>().first { it.id == "9100" }.shape!!.fill)
        }
        val out = File.createTempFile("looks", ".pptx")
        SlidesEdit.save(src, deck, e, out)
        val back = Slides.read(out)
        val b = back.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }
        assertEquals(listOf(100f, 120f, 200f, 90f), listOf(b.x, b.y, b.w, b.h))
        assertEquals(pictures - 1, back.slides[2].items.count { it is SlideItem.Picture })
        assertEquals(0xFFC00000.toInt(), b.shape!!.fill); assertEquals(null, b.shape!!.line); assertEquals(0xFFFFFF, b.text!!.paras[0].runs[0].color)
        assertEquals(0xFF00B050.toInt(), back.slides[2].items.filterIsInstance<SlideItem.Shape>().first { it.shape?.geometry == "star5" }.shape!!.fill)
        assertEquals(SlideTransition("wipe", "d", 1000), back.slides[2].transition)
        assertEquals(null, back.slides[0].transition)
        back.slides[2].steps.let { st -> assertEquals(3, st.size); assertEquals(b.anim, st[0].effects.single().target); assertEquals("fade", st[0].effects.single().kind)
            st[1].effects.single().let { assertEquals("fly", it.kind); assertEquals('l', it.from); assertEquals(1000, it.ms); assertTrue(!it.exit) }
            st[2].effects.single().let { assertEquals(b.anim, it.target); assertEquals("fly", it.kind); assertEquals('r', it.from); assertTrue(it.exit) } }
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
            val dir = scratchDir("lo")
            ProcessBuilder("soffice", "--headless", "--convert-to", "odp", "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
            val odp = dir.listFiles()!!.single { it.extension == "odp" }
            val content = java.util.zip.ZipFile(odp).use { z -> String(z.getInputStream(z.getEntry("content.xml")).readBytes()) + String(z.getInputStream(z.getEntry("styles.xml")).readBytes()) }
            assertTrue("LibreOffice sees the wipe", Regex("""smil:type="(barWipe|slideWipe)"""").containsMatchIn(content))
            assertTrue("and the exit", content.contains("""presentation:preset-class="exit""""))
            assertEquals("LibreOffice sees three clicks", 3, Regex("""presentation:node-type="on-click"""").findAll(content).count())
            assertTrue("and a fade", content.contains("""smil:type="fade""""))
            val lo = Slides.read(odp)
            assertEquals(0xFFC00000.toInt(), lo.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }.shape!!.fill)
        }
    }

    /** An .odp edited and saved as the .pptx is (asked 16 Oct: the three apps alike), read back here and by LibreOffice. */
    @Test fun `an odp edited and saved`() {
        val src = File("src/test/resources/slides.odp")
        val deck = Slides.read(src)
        val png = File.createTempFile("pic", ".png").apply { writeBytes(java.util.zip.ZipFile(File("src/test/resources/slides.pptx")).use { z -> z.getInputStream(z.entries().asSequence().first { en -> en.name.startsWith("ppt/media/") }).readBytes() }) }
        var e = SlidesEdit.start(deck)
        // the shapes' page first, "Plan" duplicated, the chart's page deleted, the title page hidden and retitled
        e = e.copy(order = listOf(SlidesEdit.Entry(2, 2), SlidesEdit.Entry(0, 0), SlidesEdit.Entry(1, 1), SlidesEdit.Entry(1, 4)),
            hidden = mapOf(0 to true), texts = mapOf((0 to "o0") to listOf("Tetra Slides, edited"), (1 to "o1") to listOf("One", "Two")),
            looks = mapOf((2 to "o0") to SlidesEdit.Look(fill = 0xFFC00000.toInt(), text = 0xFFFFFFFF.toInt())), frames = mapOf((2 to "o1") to listOf(100f, 400f, 150f, 60f)),
            removed = setOf(2 to "o3"), added = mapOf(2 to listOf(SlidesEdit.Added("9100", 550f, 50f, 100f, 100f, prst = "star5", fill = 0xFFFFC000.toInt()),
                SlidesEdit.Added("9101", 400f, 300f, 120f, 80f, image = png.path),
                SlidesEdit.Added("9102", 360f, 420f, 300f, 72f, prst = "table", rows = 2, cols = 2, cells = listOf("Name", "Score", "Anna", "18")))),
            transitions = mapOf(2 to SlideTransition("push", "u", 1000)),
            anims = mapOf(2 to listOf(SlideEffect("o0", "fade", 'd', 0, 500), SlideEffect("9100", "fly", 'l', 0, 1000), SlideEffect("o0", "fade", 'd', 0, 500, exit = true))))
        // a new page of "Title and Content" at the end: its title and two points typed
        val layout = deck.layouts.indexOfFirst { it.name == "Title and Content" }
        assertTrue(deck.layouts.size >= 5 && layout >= 0)
        assertEquals(listOf("Title", "Text"), deck.layouts[layout].preview.items.filterIsInstance<SlideItem.Shape>().filter { it.hint }.map { words(it) })
        e = e.copy(order = e.order + SlidesEdit.Entry(-1, 5, layout), texts = e.texts + mapOf((5 to "o0") to listOf("A new page"), (5 to "o1") to listOf("First point", "Second point")))
        val out = File.createTempFile("edited", ".odp")
        SlidesEdit.view(deck, e) // as it shows while editing (no failure)
        SlidesOdpEdit.save(src, deck, e, out)
        fun check(d: Deck, odp: Boolean) {
            assertEquals(5, d.slides.size)
            assertTrue(d.slides[4].items.any { words(it) == "A new page" })
            assertEquals(listOf("First point", "Second point"), (d.slides[4].items.first { words(it)?.startsWith("First") == true } as SlideItem.Shape).text!!.paras.map { p -> p.runs.joinToString("") { it.text } })
            assertEquals("•", (d.slides[4].items.first { words(it)?.startsWith("First") == true } as SlideItem.Shape).text!!.paras[0].bullet?.text?.trim()?.take(1)?.let { if (it.isNotEmpty()) "•" else it })
            val shapes = d.slides[0].items.filterIsInstance<SlideItem.Shape>()
            val box = shapes.first { words(it) == "A rounded box" }
            assertEquals(0xFFC00000.toInt(), box.shape!!.fill); assertEquals(0xFFFFFF, box.text!!.paras[0].runs[0].color)
            assertTrue(shapes.any { Math.abs(it.x - 100f) < 1 && Math.abs(it.y - 400f) < 1 && Math.abs(it.w - 150f) < 1 }) // moved and sized
            assertEquals(1, d.slides[0].items.count { it is SlideItem.Picture }) // its own deleted, one put on
            assertTrue(shapes.any { it.shape?.fill == 0xFFFFC000.toInt() && Math.abs(it.x - 550f) < 1 }) // the star
            val table = d.slides[0].items.filterIsInstance<SlideItem.Table>().single()
            assertEquals(listOf("Name", "Score", "Anna", "18"), table.cells.flatten().map { (_, t) -> t?.paras?.joinToString { p -> p.runs.joinToString("") { it.text } }.orEmpty() })
            assertEquals(0xFF4472C4.toInt(), table.cells[0][0].first); assertEquals(0xFFCFD5EA.toInt(), table.cells[1][1].first)
            assertEquals(0xFFFFFF, table.cells[0][0].second!!.paras[0].runs[0].color); assertEquals(150f, table.cols[0], 1f)
            assertTrue(d.slides[1].hidden); assertTrue(d.slides[1].items.any { words(it) == "Tetra Slides, edited" })
            for (k in 2..3) assertEquals(listOf("One", "Two"), (d.slides[k].items.first { words(it)?.startsWith("One") == true } as SlideItem.Shape).text!!.paras.map { p -> p.runs.joinToString("") { it.text } })
            assertTrue(d.slides.none { s -> s.items.any { it is SlideItem.Chart } })
            if (odp) assertEquals(d.slides[2].items.size, d.slides[3].items.size)
            // the transition and the clicks (LibreOffice's push from below, its fade in, fly in from the left, fade out)
            assertEquals("push", d.slides[0].transition!!.kind); assertEquals("u", d.slides[0].transition!!.dir); assertEquals(1000, d.slides[0].transition!!.ms)
            val st = d.slides[0].steps.map { it.effects.single() }
            assertEquals(listOf("fade", "fly", "fade"), st.map { it.kind }); assertEquals(listOf(false, false, true), st.map { it.exit })
            assertEquals(box.anim, st[0].target); assertEquals(box.anim, st[2].target); assertEquals('l', st[1].from); assertEquals(1000, st[1].ms)
            assertEquals(0xFFFFC000.toInt(), (d.slides[0].items.first { it.anim == st[1].target } as SlideItem.Shape).shape!!.fill) // the star's
        }
        check(Slides.read(out), true)
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
            val dir = scratchDir("lo")
            ProcessBuilder("soffice", "--headless", "--convert-to", "pptx", "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
            check(Slides.read(dir.listFiles()!!.single { it.extension == "pptx" }), false)
        }
    }

    /** PowerPoint's Start: effects with the one before and after it in one click, a first one by itself — .pptx and .odp, read back here and by LibreOffice. */
    @Test fun `animations starting with and after the one before`() {
        fun lo(file: File, to: String): File? {
            if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() != 0) return null
            val dir = scratchDir("lo")
            ProcessBuilder("soffice", "--headless", "--convert-to", to, "--outdir", dir.path, file.path).redirectErrorStream(true).start().waitFor()
            return dir.listFiles()!!.single { it.extension == to }
        }
        // .pptx: one click — the box fades in, the star flies in with it, the box fades out after them
        val src = File("src/test/resources/slides.pptx"); val deck = Slides.read(src)
        val box = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }.anim!!
        val e = SlidesEdit.start(deck).copy(added = mapOf(2 to listOf(SlidesEdit.Added("9100", 550f, 50f, 100f, 100f, prst = "star5"))),
            anims = mapOf(2 to listOf(SlideEffect(box, "fade", 'd', 0, 500), SlideEffect("9100", "fly", 'l', 0, 1000, start = 'w'), SlideEffect(box, "fade", 'd', 0, 500, exit = true, start = 'a'))))
        val out = File.createTempFile("starts", ".pptx"); SlidesEdit.save(src, deck, e, out)
        fun one(d: Deck, k: Int) { val st = d.slides[k].steps; assertEquals(1, st.size); assertTrue(!st[0].auto); assertEquals(listOf(0, 0, 1000), st[0].effects.map { it.delay }) }
        one(Slides.read(out), 2)
        assertEquals(listOf('c', 'w', 'a'), SlidesEdit.flat(Slides.read(out).slides[2].steps).map { it.start })
        lo(out, "odp")?.let { one(Slides.read(it), 2) }
        // .odp: the box fades in by itself as the page begins, then a click flies the star in and the box out with it
        val osrc = File("src/test/resources/slides.odp"); val odeck = Slides.read(osrc)
        val oe = SlidesEdit.start(odeck).copy(added = mapOf(2 to listOf(SlidesEdit.Added("9100", 550f, 50f, 100f, 100f, prst = "star5"))),
            anims = mapOf(2 to listOf(SlideEffect("o0", "fade", 'd', 0, 500, start = 'a'), SlideEffect("9100", "fly", 'l', 0, 1000), SlideEffect("o0", "fade", 'd', 0, 500, exit = true, start = 'w'))))
        val oout = File.createTempFile("starts", ".odp"); SlidesOdpEdit.save(osrc, odeck, oe, oout)
        fun two(d: Deck) { val st = d.slides[2].steps; assertEquals(2, st.size); assertTrue(st[0].auto); assertTrue(!st[1].auto); assertEquals(2, st[1].effects.size); assertEquals(listOf(0, 0), st[1].effects.map { it.delay }) }
        two(Slides.read(oout))
        lo(oout, "pptx")?.let { two(Slides.read(it)) }
    }

    /** Emphasis — pulse, spin, grow — saved in both formats, read back here and by LibreOffice. */
    @Test fun `emphasis effects saved`() {
        fun lo(file: File, to: String): File? {
            if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() != 0) return null
            val dir = scratchDir("lo")
            ProcessBuilder("soffice", "--headless", "--convert-to", to, "--outdir", dir.path, file.path).redirectErrorStream(true).start().waitFor()
            return dir.listFiles()!!.single { it.extension == to }
        }
        fun check(d: Deck) = assertEquals(listOf("pulse 1000", "spin 2000", "grow 2000"), d.slides[2].steps.map { it.effects.single().let { f -> "${f.kind} ${f.ms}" } })
        val src = File("src/test/resources/slides.pptx"); val deck = Slides.read(src)
        val box = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }.anim!!
        val list = listOf(SlideEffect(box, "pulse", 'd', 0, 1000), SlideEffect("9100", "spin", 'd', 0, 2000), SlideEffect(box, "grow", 'd', 0, 2000))
        val star = mapOf(2 to listOf(SlidesEdit.Added("9100", 550f, 50f, 100f, 100f, prst = "star5")))
        val out = File.createTempFile("emph", ".pptx"); SlidesEdit.save(src, deck, SlidesEdit.start(deck).copy(added = star, anims = mapOf(2 to list)), out)
        check(Slides.read(out)); lo(out, "odp")?.let { check(Slides.read(it)) }
        val osrc = File("src/test/resources/slides.odp"); val odeck = Slides.read(osrc)
        val oout = File.createTempFile("emph", ".odp"); SlidesOdpEdit.save(osrc, odeck, SlidesEdit.start(odeck).copy(added = star, anims = mapOf(2 to list.map { if (it.target == box) it.copy(target = "o0") else it })), oout)
        check(Slides.read(oout)); lo(oout, "pptx")?.let { check(Slides.read(it)) }
    }

    /** Wipe from a side and zoom, in and out — both formats, and through LibreOffice each way. */
    @Test fun `wipe and zoom saved`() {
        fun lo(file: File, to: String): File? {
            if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() != 0) return null
            val dir = scratchDir("lo")
            ProcessBuilder("soffice", "--headless", "--convert-to", to, "--outdir", dir.path, file.path).redirectErrorStream(true).start().waitFor()
            return dir.listFiles()!!.single { it.extension == to }
        }
        fun check(d: Deck) = assertEquals(listOf("wipe l in", "zoom d in", "wipe u out", "zoom d out"),
            SlidesEdit.flat(d.slides[2].steps).map { f -> "${f.kind} ${if (f.kind == "zoom") 'd' else f.from} ${if (f.exit) "out" else "in"}" })
        val src = File("src/test/resources/slides.pptx"); val deck = Slides.read(src)
        val box = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }.anim!!
        val list = listOf(SlideEffect(box, "wipe", 'l', 0, 500), SlideEffect("9100", "zoom", 'd', 0, 500),
            SlideEffect(box, "wipe", 'u', 0, 500, exit = true), SlideEffect("9100", "zoom", 'd', 0, 500, exit = true))
        val star = mapOf(2 to listOf(SlidesEdit.Added("9100", 550f, 50f, 100f, 100f, prst = "star5")))
        val out = File.createTempFile("wipe", ".pptx"); SlidesEdit.save(src, deck, SlidesEdit.start(deck).copy(added = star, anims = mapOf(2 to list)), out)
        check(Slides.read(out)); lo(out, "odp")?.let { check(Slides.read(it)) }
        val osrc = File("src/test/resources/slides.odp"); val odeck = Slides.read(osrc)
        val oout = File.createTempFile("wipe", ".odp"); SlidesOdpEdit.save(osrc, odeck, SlidesEdit.start(odeck).copy(added = star, anims = mapOf(2 to list.map { if (it.target == box) it.copy(target = "o0") else it })), oout)
        check(Slides.read(oout)); lo(oout, "pptx")?.let { check(Slides.read(it)) }
    }

    /** Delay and "next slide after": an effect waiting after its click, one after the one before with a wait; slides going on by themselves — both formats, and LibreOffice. */
    @Test fun `delays and slides going on by themselves`() {
        fun lo(file: File, to: String): File? {
            if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() != 0) return null
            val dir = scratchDir("lo")
            ProcessBuilder("soffice", "--headless", "--convert-to", to, "--outdir", dir.path, file.path).redirectErrorStream(true).start().waitFor()
            return dir.listFiles()!!.single { it.extension == to }
        }
        fun check(d: Deck, plain: Boolean = true) {
            // the box 1 s after the click, the star 2 s after the box has faded in (0.5 s): at 1 s and 3.5 s
            assertEquals(listOf(1000, 3500), d.slides[2].steps.single().effects.map { it.delay })
            assertEquals(listOf(1000, 2000), SlidesEdit.flat(d.slides[2].steps).map { it.wait })
            assertEquals(5000, d.slides[2].transition!!.after); assertEquals("fade", d.slides[2].transition!!.kind)
            // by itself, no transition (LibreOffice's .pptx import drops a time on a slide without one — PowerPoint's own way of writing it)
            if (plain) d.slides[1].transition!!.let { assertEquals("", it.kind); assertEquals(3000, it.after) }
        }
        val star = mapOf(2 to listOf(SlidesEdit.Added("9100", 550f, 50f, 100f, 100f, prst = "star5")))
        val tr = mapOf(2 to SlideTransition("fade", null, 750, 5000), 1 to SlideTransition("", null, 0, 3000))
        val src = File("src/test/resources/slides.pptx"); val deck = Slides.read(src)
        val box = deck.slides[2].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "A rounded box" }.anim!!
        val list = listOf(SlideEffect(box, "fade", 'd', 0, 500, wait = 1000), SlideEffect("9100", "fly", 'l', 0, 1000, start = 'a', wait = 2000))
        val out = File.createTempFile("delay", ".pptx"); SlidesEdit.save(src, deck, SlidesEdit.start(deck).copy(added = star, anims = mapOf(2 to list), transitions = tr), out)
        check(Slides.read(out)); lo(out, "odp")?.let { check(Slides.read(it), plain = false) }
        val osrc = File("src/test/resources/slides.odp"); val odeck = Slides.read(osrc)
        val oout = File.createTempFile("delay", ".odp")
        SlidesOdpEdit.save(osrc, odeck, SlidesEdit.start(odeck).copy(added = star, anims = mapOf(2 to list.map { if (it.target == box) it.copy(target = "o0") else it }), transitions = tr), oout)
        check(Slides.read(oout)); lo(oout, "pptx")?.let { check(Slides.read(it)) }
    }

    /** A new item's id is past every one on the slide: one saved there in an earlier session (9000) is not taken again. */
    @Test fun `a new item's id is free on the slide`() {
        val e = SlidesEdit.start(Slides.read(File("src/test/resources/slides.pptx")))
        assertEquals("9004", e.newId(0, listOf("2", "9000", "9003")))
        assertEquals("9000", e.newId(0, listOf("2", "3")))
        val more = e.copy(added = mapOf(0 to listOf(SlidesEdit.Added("9004", 0f, 0f, 1f, 1f, prst = "rect"))))
        assertEquals("9005", more.newId(0, listOf("9000", "9003")))
    }

    /** Design's themes (asked 16 Oct: light and dark): each applied to the blank deck, read back, and drawn by LibreOffice. */
    @Test fun `themes applied to a deck`() {
        val blank = File("src/main/assets/blank.pptx")
        for (theme in SlidesThemes.BUILT_IN) {
            val out = File.createTempFile("theme", ".pptx")
            SlidesThemes.apply(blank, theme, out)
            val d = Slides.read(out)
            // the slide's background is the theme's (its lt1), and the theme read back is the same
            assertEquals(theme.name, 0xFF000000.toInt() or theme.colors[1], d.slides.first().background)
            val back = SlidesThemes.fromFile(out)!!
            assertEquals(theme.name, theme.colors, back.colors); assertEquals(theme.name, theme.dark, back.dark)
            assertEquals(theme.name, listOf(theme.major, theme.minor), listOf(back.major, back.minor))
            val master = java.util.zip.ZipFile(out).use { z -> String(z.getInputStream(z.getEntry("ppt/slideMasters/slideMaster1.xml")).readBytes()) }
            assertEquals(theme.name, if (theme.band != null) 1 else 0, Regex("Tetra theme band").findAll(master).count())
            // applied again (another theme over it): still one band at most
            val again = File.createTempFile("theme2", ".pptx"); SlidesThemes.apply(out, SlidesThemes.BUILT_IN[1], again)
            assertEquals(1, Regex("Tetra theme band").findAll(java.util.zip.ZipFile(again).use { z -> String(z.getInputStream(z.getEntry("ppt/slideMasters/slideMaster1.xml")).readBytes()) }).count())
        }
        // a band on the master is no placeholder: slides.pptx's title and text keep their places and looks under a banded theme
        val src = File("src/test/resources/slides.pptx"); val before = Slides.read(src)
        val banded = File.createTempFile("banded", ".pptx"); SlidesThemes.apply(src, SlidesThemes.BUILT_IN.first { it.name == "Midnight" }, banded)
        val after = Slides.read(banded)
        for (k in 0..1) {
            val a = before.slides[k].items.filterIsInstance<SlideItem.Shape>().filter { it.text != null }; val b = after.slides[k].items.filterIsInstance<SlideItem.Shape>().filter { it.text != null }
            assertEquals(a.map { listOf(it.x, it.y, it.w, it.h) }, b.map { listOf(it.x, it.y, it.w, it.h) })
            assertTrue("slide $k: a text box took the band's fill", b.none { it.shape?.fill == 0xFF000000.toInt() or 0xFCA311 })
        }
        // LibreOffice draws a dark one dark
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
            val night = SlidesThemes.BUILT_IN.first { it.name == "Night" }
            val dir = scratchDir("lo")
            val f = File(dir, "n.pptx"); SlidesThemes.apply(blank, night, f)
            ProcessBuilder("soffice", "--headless", "--convert-to", "odp", "--outdir", dir.path, f.path).redirectErrorStream(true).start().waitFor()
            assertEquals(0xFF000000.toInt() or night.colors[1], Slides.read(File(dir, "n.odp")).slides.first().background)
        }
    }

    /** A theme on a LibreOffice deck: its master pages' background, band and placeholders' words; LibreOffice draws it so too. */
    @Test fun `a theme on an odp`() {
        val src = File("src/test/resources/slides.odp")
        val midnight = SlidesThemes.BUILT_IN.first { it.name == "Midnight" }
        val out = File("build/office-roundtrip").apply { mkdirs() }.resolve("midnight.odp"); SlidesThemes.apply(src, midnight, out) // kept, for a look
        val d = Slides.read(out)
        assertEquals(0xFF000000.toInt() or midnight.colors[1], d.slides[1].background)
        val plan = d.slides[1].items.filterIsInstance<SlideItem.Shape>().first { words(it) == "Plan" }
        val run = plan.text!!.paras[0].runs[0]
        assertEquals(midnight.colors[0], run.color!! and 0xFFFFFF)
        assertEquals(midnight.minor, DocStyles.Plain.fontName(run.font)) // its words in the theme's font (the deck's own font was on its words)
        val paper = File.createTempFile("paper", ".odp"); SlidesThemes.apply(src, SlidesThemes.BUILT_IN.first { it.name == "Paper" }, paper)
        assertEquals("Georgia", Slides.read(paper).slides[1].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("Read") == true }.text!!.paras[0].runs[0].font.let(DocStyles.Plain::fontName))
        assertTrue(d.slides[1].items.any { it is SlideItem.Shape && it.shape?.fill == (0xFF000000.toInt() or midnight.colors[4]) }) // the band
        // again with another: one band still
        val again = File.createTempFile("theme2", ".odp"); SlidesThemes.apply(out, SlidesThemes.BUILT_IN.first { it.name == "Leaf" }, again)
        assertEquals(1, Slides.read(again).slides[1].items.count { it is SlideItem.Shape && it.shape?.fill == (0xFF000000.toInt() or 0x90C226) })
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
            val dir = scratchDir("lo")
            val f = File(dir, "t.odp").also { out.copyTo(it) }
            ProcessBuilder("soffice", "--headless", "--convert-to", "pptx", "--outdir", dir.path, f.path).redirectErrorStream(true).start().waitFor()
            assertEquals(0xFF000000.toInt() or midnight.colors[1], Slides.read(File(dir, "t.pptx")).slides[1].background)
        }
    }

    /** A template made by the tetra-slides-theme skill: its theme read, a new presentation made from it (a .potx said to be a deck), LibreOffice opening that. */
    @Test fun `a new presentation from a template`() {
        val potx = File("src/test/resources/example-school.potx")
        val theme = SlidesThemes.fromFile(potx)!!
        assertEquals(listOf("Georgia", "Calibri"), listOf(theme.major, theme.minor)); assertEquals(0x1F3A5F, theme.colors[4]); assertTrue(!theme.dark)
        val deck = File.createTempFile("from", ".pptx"); SlidesTemplates.make(potx, deck)
        val types = java.util.zip.ZipFile(deck).use { z -> String(z.getInputStream(z.getEntry("[Content_Types].xml")).readBytes()) }
        assertTrue("presentationml.presentation.main+xml" in types && "template.main" !in types)
        val d = Slides.read(deck)
        assertEquals(1, d.slides.size)
        assertTrue(d.slides[0].items.any { it is SlideItem.Picture }) // the logo on the master
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
            val dir = scratchDir("lo")
            val f = File(dir, "d.pptx").also { deck.copyTo(it) }
            ProcessBuilder("soffice", "--headless", "--convert-to", "odp", "--outdir", dir.path, f.path).redirectErrorStream(true).start().waitFor()
            assertEquals(1, Slides.read(File(dir, "d.odp")).slides.size)
        }
    }

    /** Speaker notes typed (asked 17 Oct): saved into notes pages — rewritten, made new, a copy's own, a notes master made — read back, and by LibreOffice. */
    @Test fun `speaker notes saved`() {
        fun lo(file: File, to: String): File? {
            if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() != 0) return null
            val dir = scratchDir("lo")
            ProcessBuilder("soffice", "--headless", "--convert-to", to, "--outdir", dir.path, file.path).redirectErrorStream(true).start().waitFor()
            return dir.listFiles()!!.single { it.extension == to }
        }
        // .pptx: the first slide's notes rewritten (two lines), the second given notes (it had none), the first duplicated at the end
        val src = File("src/test/resources/slides.pptx"); val deck = Slides.read(src)
        assertTrue(deck.slides[0].notes.isNotEmpty() && deck.slides[1].notes.isEmpty())
        var e = SlidesEdit.start(deck)
        // (a duplicate takes the notes as they are then, as Duplicate does)
        val copy = e.next()
        e = e.copy(order = e.order + SlidesEdit.Entry(0, copy), notes = mapOf(0 to "Greet them.\nThen the plan.", 1 to "Ask who has read it.", copy to "Greet them.\nThen the plan."))
        assertEquals("Ask who has read it.", SlidesEdit.view(deck, e).slides[1].notes)
        val out = File.createTempFile("notes", ".pptx"); SlidesEdit.save(src, deck, e, out)
        fun check(d: Deck) {
            assertEquals("Greet them.\nThen the plan.", d.slides[0].notes)
            assertEquals("Ask who has read it.", d.slides[1].notes)
            assertEquals("Greet them.\nThen the plan.", d.slides.last().notes) // the copy's own notes page
        }
        check(Slides.read(out))
        java.util.zip.ZipFile(out).use { z -> assertEquals(1, z.entries().asSequence().count { it.name.startsWith("ppt/notesMasters/notesMaster") && it.name.endsWith(".xml") }) }
        lo(out, "odp")?.let { check(Slides.read(it)) }
        // a deck that never had notes: a notes master made with its own theme, listed in presentation.xml
        val bare = File.createTempFile("bare", ".pptx"); SlidesTemplates.make(File("src/test/resources/example-school.potx"), bare)
        val bd = Slides.read(bare)
        val bout = File.createTempFile("bare-notes", ".pptx"); SlidesEdit.save(bare, bd, SlidesEdit.start(bd).copy(notes = mapOf(0 to "Welcome everyone.")), bout)
        java.util.zip.ZipFile(bout).use { z ->
            fun text(n: String) = String(z.getInputStream(z.getEntry(n)).readBytes())
            assertTrue("notesMasterIdLst" in text("ppt/presentation.xml"))
            assertTrue("theme" in text("ppt/notesMasters/_rels/notesMaster1.xml.rels"))
            assertTrue("notesMaster+xml" in text("[Content_Types].xml") && "notesSlide+xml" in text("[Content_Types].xml"))
        }
        assertEquals("Welcome everyone.", Slides.read(bout).slides[0].notes)
        lo(bout, "odp")?.let { assertEquals("Welcome everyone.", Slides.read(it).slides[0].notes) }
        // .odp: the first page's notes rewritten, the second's (an empty notes box) typed
        val osrc = File("src/test/resources/slides.odp"); val od = Slides.read(osrc)
        val oout = File.createTempFile("notes", ".odp")
        SlidesOdpEdit.save(osrc, od, SlidesEdit.start(od).copy(notes = mapOf(0 to "Greet them.\nThen the plan.", 1 to "Ask who has read it.")), oout)
        fun ocheck(d: Deck) { assertEquals("Greet them.\nThen the plan.", d.slides[0].notes); assertEquals("Ask who has read it.", d.slides[1].notes) }
        ocheck(Slides.read(oout))
        lo(oout, "pptx")?.let { ocheck(Slides.read(it)) }
    }

    /** A group chosen as a whole: moved and halved, its shapes coloured, it fading in — saved and read back, and by LibreOffice. */
    @Test fun `a group moved, coloured and animated`() {
        val src = File("src/test/resources/effects.pptx")
        val deck = Slides.read(src)
        val items = deck.slides[3].items
        assertTrue(items.size == 2 && items.all { it.grouped && it.anim == "2" })
        val box = SlidesEdit.box(deck.slides[3], "2")!!
        assertEquals(listOf(72f, 72f, 360f, 144f), listOf(box.x, box.y, box.w, box.h))
        val e = SlidesEdit.start(deck).copy(frames = mapOf((3 to "2") to listOf(100f, 100f, 180f, 72f)), looks = mapOf((3 to "2") to SlidesEdit.Look(fill = 0xFF00B050.toInt())),
            anims = mapOf(3 to listOf(SlideEffect("2", "fade", 'd', 0, 500))))
        fun check(s: Slide) {
            val (oval, rect) = s.items.filterIsInstance<SlideItem.Shape>().sortedBy { it.x }
            assertEquals(listOf(100f, 100f, 72f, 72f), listOf(oval.x, oval.y, oval.w, oval.h).map { Math.round(it * 10) / 10f })
            assertEquals(listOf(208f, 136f, 72f, 36f), listOf(rect.x, rect.y, rect.w, rect.h).map { Math.round(it * 10) / 10f })
            assertTrue(s.items.all { (it as SlideItem.Shape).shape!!.fill == 0xFF00B050.toInt() })
        }
        check(SlidesEdit.view(deck, e).slides[3])
        val out = File.createTempFile("group", ".pptx")
        SlidesEdit.save(src, deck, e, out)
        val back = Slides.read(out).slides[3]
        check(back)
        assertEquals("2", back.steps.single().effects.single().target)
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
            val dir = scratchDir("lo")
            ProcessBuilder("soffice", "--headless", "--convert-to", "odp", "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
            val lo = Slides.read(dir.listFiles()!!.single { it.extension == "odp" }).slides[3]
            val (oval, rect) = lo.items.filterIsInstance<SlideItem.Shape>().sortedBy { it.x }
            assertEquals(100f, oval.x, 0.5f); assertEquals(208f, rect.x, 0.5f); assertEquals(0xFF00B050.toInt(), rect.shape!!.fill)
        }
    }

    /** Transitions and click animations (effects.py's deck: PowerPoint's own XML in python-pptx's slides). */
    @Test fun `transitions and click animations read`() {
        val d = Slides.read(File("src/test/resources/effects.pptx"))
        assertEquals(null, d.slides[0].transition)
        d.slides[1].transition!!.let { assertEquals("push", it.kind); assertEquals("u", it.dir); assertEquals(1000, it.ms) }
        assertEquals("fade", d.slides[2].transition!!.kind)
        val steps = d.slides[2].steps
        assertEquals(2, steps.size); assertTrue(steps.none { it.auto })
        val box = d.slides[2].items.first { words(it) == "Fades in" }
        val star = d.slides[2].items.filterIsInstance<SlideItem.Shape>().first { it.shape?.geometry == "star5" }
        assertEquals(box.anim, steps[0].effects.single().target); assertEquals("fade", steps[0].effects.single().kind)
        steps[1].effects.single().let { assertEquals(star.anim, it.target); assertEquals("fly", it.kind); assertEquals('l', it.from); assertEquals(1000, it.ms) }
    }

    /** olive.pptx (python-pptx, 17 Oct): a picture cropped by a:srcRect and a bold title, as LibreOffice shows them; a save keeps the crop. */
    @Test fun `a cropped picture and a bold title`() {
        fun check(d: Deck) {
            val pic = d.slides[0].items.filterIsInstance<SlideItem.Picture>().single()
            assertEquals(listOf(0.22222f, 0f, 0.22222f, 0f), pic.crop!!.toList())
            val title = d.slides[0].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("From grove") == true }.text!!.paras
            assertTrue(title[0].runs[0].bold); assertTrue(!title[1].runs[0].bold)
        }
        val src = File("src/test/resources/olive.pptx")
        val deck = Slides.read(src); check(deck)
        val title = deck.slides[0].items.filterIsInstance<SlideItem.Shape>().first { words(it)?.startsWith("From grove") == true }
        var e = SlidesEdit.start(deck)
        e = e.copy(texts = mapOf((0 to title.id!!) to listOf("From grove to bottle", "The 2026 olive harvest")))
        val out = File.createTempFile("olive", ".pptx")
        SlidesEdit.save(src, deck, e, out)
        check(Slides.read(out))
    }

    /** The crop's maths: a 900 × 600 picture with 2/9 cut from each side shows its middle 500 × 600. */
    @Test fun `a crop in pixels`() {
        assertEquals(listOf(200, 0, 700, 600), Slides.crop(900, 600, floatArrayOf(0.22222f, 0f, 0.22222f, 0f)).toList())
        assertEquals(listOf(0, 0, 900, 600), Slides.crop(900, 600, null).toList())
        assertEquals(listOf(0, 60, 900, 480), Slides.crop(900, 600, floatArrayOf(-0.1f, 0.1f, 0f, 0.2f)).toList()) // a negative side drawn as none
        Slides.crop(10, 10, floatArrayOf(0.7f, 0f, 0.7f, 0f)).let { assertTrue(it[2] > it[0]) } // never an empty part
    }
}
