package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** PowerPoint's slides (slides.py's deck, python-pptx's default template), as LibreOffice shows them. */
class SlidesTest {
    private fun words(i: SlideItem) = (i as? SlideItem.Shape)?.text?.paras?.joinToString("\n") { p -> p.runs.joinToString("") { it.text } }

    @Test fun `a pptx deck read with its layouts, master and theme`() = check("slides.pptx")

    /** The same deck as LibreOffice keeps it (slides.odp: LibreOffice's of slides.pptx): read the same. */
    @Test fun `an odp deck read with its styles and lists`() = check("slides.odp")

    private fun check(name: String) {
        val d = Slides.read(File("src/test/resources/$name"))
        assertEquals(720f, d.width, 0.5f); assertEquals(540f, d.height, 0.5f) // 10 × 7.5 in
        assertEquals(4, d.slides.size)
        // the title slide: its placeholders' places and looks from the layout and the master
        val title = d.slides[0].items.first { words(it) == "Tetra Slides" } as SlideItem.Shape
        assertTrue(title.w > 500 && title.y > 100) // the layout's centred title box
        assertEquals(44f, title.text!!.paras[0].runs[0].size, 0.1f) // the master's title style
        assertEquals('c', title.text.paras[0].align)
        assertEquals("Say hello first.", d.slides[0].notes)
        // bullets in two levels, the second smaller and further in
        val body = d.slides[1].items.first { words(it)?.startsWith("Read the slides") == true } as SlideItem.Shape
        val paras = body.text!!.paras
        assertEquals(listOf(0, 1, 1, 0, 1), paras.map { it.level })
        assertEquals("•", paras[0].bullet); assertTrue(paras[1].marL > paras[0].marL); assertTrue(paras[1].runs[0].size < paras[0].runs[0].size)
        // shapes, words of their own, a picture
        val third = d.slides[2].items
        val shapes = third.filterIsInstance<SlideItem.Shape>()
        assertEquals("roundRect", shapes.first { words(it) == "A rounded box" }.shape!!.geometry)
        assertEquals(0xFF70AD47.toInt(), shapes.first { it.shape?.geometry == "rightArrow" }.shape!!.fill)
        val red = shapes.first { words(it) == "Bold red words, 28 pt" }.text!!.paras[0].runs[0]
        assertTrue(red.bold); assertEquals(28f, red.size, 0.1f); assertEquals(0xFFC00000.toInt(), red.color)
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
        assertEquals(44f, (back.slides[1].items.first { words(it)?.startsWith("Tetra") == true } as SlideItem.Shape).text!!.paras[0].runs[0].size, 0.1f) // its look kept
        assertEquals(2, back.slides.count { s -> s.items.any { words(it)?.startsWith("Plan") == true } }) // duplicated
        assertTrue(back.slides.none { s -> s.items.any { it is SlideItem.Chart } }) // deleted
        assertEquals("Say hello first.", back.slides[1].notes) // its notes went with it
        // LibreOffice opens it: four pages
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
            val dir = java.nio.file.Files.createTempDirectory("lo").toFile()
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
        assertEquals(0xFFC00000.toInt(), b.shape!!.fill); assertEquals(null, b.shape!!.line); assertEquals(0xFFFFFFFF.toInt(), b.text!!.paras[0].runs[0].color)
        assertEquals(0xFF00B050.toInt(), back.slides[2].items.filterIsInstance<SlideItem.Shape>().first { it.shape?.geometry == "star5" }.shape!!.fill)
        assertEquals(SlideTransition("wipe", "d", 1000), back.slides[2].transition)
        assertEquals(null, back.slides[0].transition)
        back.slides[2].steps.let { st -> assertEquals(3, st.size); assertEquals(b.anim, st[0].effects.single().target); assertEquals("fade", st[0].effects.single().kind)
            st[1].effects.single().let { assertEquals("fly", it.kind); assertEquals('l', it.from); assertEquals(1000, it.ms); assertTrue(!it.exit) }
            st[2].effects.single().let { assertEquals(b.anim, it.target); assertEquals("fly", it.kind); assertEquals('r', it.from); assertTrue(it.exit) } }
        if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() == 0) {
            val dir = java.nio.file.Files.createTempDirectory("lo").toFile()
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
            assertEquals("•", (d.slides[4].items.first { words(it)?.startsWith("First") == true } as SlideItem.Shape).text!!.paras[0].bullet?.trim()?.take(1)?.let { if (it.isNotEmpty()) "•" else it })
            val shapes = d.slides[0].items.filterIsInstance<SlideItem.Shape>()
            val box = shapes.first { words(it) == "A rounded box" }
            assertEquals(0xFFC00000.toInt(), box.shape!!.fill); assertEquals(0xFFFFFFFF.toInt(), box.text!!.paras[0].runs[0].color)
            assertTrue(shapes.any { Math.abs(it.x - 100f) < 1 && Math.abs(it.y - 400f) < 1 && Math.abs(it.w - 150f) < 1 }) // moved and sized
            assertEquals(1, d.slides[0].items.count { it is SlideItem.Picture }) // its own deleted, one put on
            assertTrue(shapes.any { it.shape?.fill == 0xFFFFC000.toInt() && Math.abs(it.x - 550f) < 1 }) // the star
            val table = d.slides[0].items.filterIsInstance<SlideItem.Table>().single()
            assertEquals(listOf("Name", "Score", "Anna", "18"), table.cells.flatten().map { (_, t) -> t?.paras?.joinToString { p -> p.runs.joinToString("") { it.text } }.orEmpty() })
            assertEquals(0xFF4472C4.toInt(), table.cells[0][0].first); assertEquals(0xFFCFD5EA.toInt(), table.cells[1][1].first)
            assertEquals(0xFFFFFFFF.toInt(), table.cells[0][0].second!!.paras[0].runs[0].color); assertEquals(150f, table.cols[0], 1f)
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
            val dir = java.nio.file.Files.createTempDirectory("lo").toFile()
            ProcessBuilder("soffice", "--headless", "--convert-to", "pptx", "--outdir", dir.path, out.path).redirectErrorStream(true).start().waitFor()
            check(Slides.read(dir.listFiles()!!.single { it.extension == "pptx" }), false)
        }
    }

    /** PowerPoint's Start: effects with the one before and after it in one click, a first one by itself — .pptx and .odp, read back here and by LibreOffice. */
    @Test fun `animations starting with and after the one before`() {
        fun lo(file: File, to: String): File? {
            if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() != 0) return null
            val dir = java.nio.file.Files.createTempDirectory("lo").toFile()
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
            val dir = java.nio.file.Files.createTempDirectory("lo").toFile()
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

    /** Delay and "next slide after": an effect waiting after its click, one after the one before with a wait; slides going on by themselves — both formats, and LibreOffice. */
    @Test fun `delays and slides going on by themselves`() {
        fun lo(file: File, to: String): File? {
            if (runCatching { ProcessBuilder("soffice", "--version").start().waitFor() }.getOrNull() != 0) return null
            val dir = java.nio.file.Files.createTempDirectory("lo").toFile()
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
            val dir = java.nio.file.Files.createTempDirectory("lo").toFile()
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
}
