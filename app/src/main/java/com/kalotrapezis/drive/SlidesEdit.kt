package com.kalotrapezis.drive

import org.w3c.dom.Element
import java.io.File

/**
 * Editing a .pptx (16 Oct, the plan's next step after the viewer): the slides' order — moved, duplicated, deleted —
 * hidden or shown, the words of the slides' own text boxes and their speaker notes; kept apart from the deck as read, so Undo is the edit
 * before, and saved into the file: only the slides and lists that changed are written, every other part as it was.
 */
internal object SlidesEdit {
    /** One slide in the edited deck: [src] the deck's slide it shows, [uid] its own (a duplicate is a new one); [layout]: a new slide of that layout. */
    data class Entry(val src: Int, val uid: Int, val layout: Int = -1)
    /** A picture ([image]: a copy on the phone, [ext] png/jpeg) or a shape ([prst], [fill]) put on a slide, where it is (points). */
    data class Added(val id: String, val x: Float, val y: Float, val w: Float, val h: Float, val image: String? = null, val ext: String = "png",
        val prst: String? = null, val fill: Int = 0xFF4472C4.toInt(),
        /** a table ([prst] "table"): its rows and columns, its cells' words row by row */ val rows: Int = 0, val cols: Int = 0, val cells: List<String> = emptyList(),
        /** a chart (Insert › Chart, Words' chart editor): its kind, numbers and looks */ val chart: DocChart? = null,
        /** an equation (Insert › Equation, Words' editor): [image] is it drawn, PowerPoint's fallback and LibreOffice's replacement */ val math: MathNode? = null)
    /** A shape's looks as chosen (asked 16 Oct): its fill, border and words' colour — [NONE] taken away, null as the file has it. */
    data class Look(val fill: Int? = null, val line: Int? = null, val text: Int? = null,
        /** Home (17 Oct, Words' tools): every letter's face, size, bold, italic, underline, strike-through, highlight ([NONE]: taken away) — null: as they are */
        val font: String? = null, val size: Float? = null, val bold: Boolean? = null, val italic: Boolean? = null, val underline: Boolean? = null, val strike: Boolean? = null,
        val highlight: Int? = null,
        /** each paragraph's alignment, list ('n' none, 'b' bullets, '1' numbers) and line spacing (PowerPoint's share of its single line) */
        val align: DocAlign? = null, val list: Char? = null, val spacing: Float? = null,
        /** the box's words at its top, middle or bottom ('t', 'c', 'b'); autofit: 'n' none, 's' shrink on overflow, 'g' the box grows to its words */
        val anchor: Char? = null, val autofit: Char? = null,
        /** Clear formatting: the letters' own looks gone, their paragraph's (its level's, the master's) left — the choices above apply after it */
        val cleared: Boolean = false,
        /** Insert › Drop cap, raised: the first letter's size (pt; 0: none, a raised one made plain again), its own face and colour */
        val raise: Float? = null, val raiseFont: String? = null, val raiseColor: Int? = null,
        /** Insert › Drop cap, dropped (the letter in a text box of its own beside it): the first paragraph's left edge (pt), no first-line indent
         *  nor space after it; the paragraph after it (the rest of the words, split where the letter's lines end) no space before nor first-line indent */
        val drop: Float? = null, val dropRest: Boolean = true,
        /** Insert › Link on all its words: a web address, or "slide:" and a slide's uid; "" a link taken off */
        val link: String? = null,
        /** Insert › WordArt (Words' looks and bends): every letter's outline, shadow and glow; the words' bend ("" straight, else PowerPoint's prstTxWarp name) */
        val effects: TextEffects? = null, val warp: String? = null) {
        /** Whether it changes the words (not only the shape's fill and border). */
        val words get() = text != null || font != null || size != null || bold != null || italic != null || underline != null || strike != null || highlight != null ||
            align != null || list != null || spacing != null || anchor != null || autofit != null || cleared || raise != null || drop != null || link != null || effects != null || warp != null
    }
    /** [p] with its first letter raised as [l] says (a run of its own, bigger, in its own face and colour), or a raised one made plain. */
    private fun raised(p: SlidePara, l: Look): SlidePara {
        val k = p.runs.indexOfFirst { it.text.isNotEmpty() }.takeIf { it >= 0 } ?: return p
        val r = p.runs[k]
        val runs = if (l.raise!! > 0f) {
            val n = r.text.offsetByCodePoints(0, 1)
            listOf(r.copy(text = r.text.substring(0, n), sizePt = l.raise, font = l.raiseFont?.let { Slides.face(it) } ?: r.font, color = l.raiseColor?.and(0xFFFFFF) ?: r.color)) +
                listOfNotNull(r.copy(text = r.text.substring(n)).takeIf { it.text.isNotEmpty() })
        } else listOf(isRaised(p.runs, k)?.let { size -> r.copy(sizePt = size) } ?: r)
        return SlidePara(p.para.copy(runs = p.runs.take(k) + runs + p.runs.drop(k + 1)), p.look, p.bullet, p.base)
    }
    /** When run [k] is a raised first letter (one letter, half again as big as the next): the next's size, to make it plain. */
    fun isRaised(runs: List<DocRun>, k: Int): Float? {
        val r = runs.getOrNull(k) ?: return null; val next = runs.getOrNull(k + 1) ?: return null
        return next.sizePt?.takeIf { r.text.codePointCount(0, r.text.length) == 1 && (r.sizePt ?: 0f) >= it * 1.5f }
    }
    /** A run's face, as Word writes it or LibreOffice: what a face chosen replaces. */
    private val FACE = Regex("""<w:rFonts[^>]*/>|\s?style:font-name="[^"]*"|\s?fo:font-family="[^"]*"""")
    /** [t] in Home's looks [l]: every letter's face, size, bold…; each paragraph's alignment, list and spacing; the box's anchor and autofit. */
    fun looked(t: SlideText, l: Look): SlideText {
        if (!l.words) return t
        val first = t.paras.indexOfFirst { p -> p.runs.any { it.text.isNotBlank() } }
        return SlideText(SlidePara.numbered(t.paras.mapIndexed { i, p0 -> val p = if (i == first && l.raise != null) raised(p0, l) else p0
            val runs = p.runs.map { r0 ->
                var r = if (l.cleared) (p.base ?: r0).copy(text = r0.text, link = r0.link) else r0
                l.font?.let { f -> r = r.copy(font = Slides.face(f) + r.font.orEmpty().replace(FACE, "")) }
                l.size?.let { r = r.copy(sizePt = it) }
                l.bold?.let { r = r.copy(bold = it) }; l.italic?.let { r = r.copy(italic = it) }
                l.underline?.let { r = r.copy(underline = it) }; l.strike?.let { r = r.copy(strike = it) }
                l.text?.let { r = r.copy(color = it and 0xFFFFFF) }
                l.highlight?.let { h -> r = r.copy(highlight = h.takeIf { it != NONE }?.and(0xFFFFFF)) }
                // a link in Office's link colour (the theme's, once saved and read again)
                l.link?.let { k -> r = if (k.isEmpty()) r.copy(link = null) else r.copy(link = k, color = 0x0563C1) }
                // WordArt's effects in Word's form, which Words' drawing reads (a run's own ones, either form, replaced)
                l.effects?.let { fx -> r = r.copy(font = r.font.orEmpty().replace(EFFECTS, "") + fx.docx()) }
                r
            }
            // a list as PowerPoint's Bullets and Numbering make it: hanging a quarter inch and a bit (marL 342900, indent −342900); none: no indent
            val bullet = when (l.list) { null -> p.bullet; 'n' -> null; 'b' -> SlideBullet("•"); else -> SlideBullet("1.") } // counted by numbered()
            val look0 = when (l.list) { null -> p.look; 'n' -> p.look.copy(left = 0f, firstLine = 0f); else -> if (p.look.left > 0f) p.look else p.look.copy(left = 27f, firstLine = -27f) }
            val look = l.drop?.let { d -> when (i) { first -> look0.copy(left = d, firstLine = 0f, after = 0f); first + 1 -> if (l.dropRest) look0.copy(firstLine = 0f, before = 0f) else null; else -> null } } ?: look0
            SlidePara(p.para.copy(runs = runs, align = l.align ?: p.para.align, list = DocList(bullet?.text?.first()?.isDigit() == true, p.level)),
                l.spacing?.let { look.copy(lineMultiple = it, lineExact = null) } ?: look, bullet, p.base)
        }), l.anchor ?: t.anchor, t.insets, t.scale, t.wrap, l.autofit?.let { it == 's' } ?: t.shrink, if (l.warp == null) t.warp else TextWarp.entries.firstOrNull { it.word == l.warp })
    }
    /** A run's own text effects, as Word writes them or LibreOffice: what WordArt's replace. */
    private val EFFECTS = Regex("""<w14:(glow|shadow|textOutline)\b[\s\S]*?</w14:\1>|\s?style:text-outline="[^"]*"|\s?fo:text-shadow="[^"]*"""")
    /** Insert › Header & footer on a slide (PowerPoint's): the date ([fixed] its words, null: today's, updated), the slide's number, a footer's words (null: none). */
    data class Footers(val date: Boolean = false, val fixed: String? = null, val number: Boolean = false, val text: String? = null)
    /** Today as Header & footer's date shows it (the first of Words' Date and time forms). */
    fun today(): String = dateForms().first()
    const val NONE = 0
    /** An [Added]'s kind for Insert › Text box: a box of words alone (no fill, no border). */
    const val TEXT_BOX = "textbox"
    data class Edit(val order: List<Entry>, val hidden: Map<Int, Boolean> = emptyMap(), val texts: Map<Pair<Int, String>, List<String>> = emptyMap(),
        val added: Map<Int, List<Added>> = emptyMap(),
        /** (slide uid, shape id) → its looks */ val looks: Map<Pair<Int, String>, Look> = emptyMap(),
        /** a slide's transition as chosen: its kind "" none, else PowerPoint's name (fade, push, wipe, cover, pull, cut), its side and length */ val transitions: Map<Int, SlideTransition> = emptyMap(),
        /** a slide's click animations as chosen, each its own click in their order: entrances and exits (appear, fade, fly) */ val anims: Map<Int, List<SlideEffect>> = emptyMap(),
        /** (slide uid, item id) → where the file's own item was moved or sized to: x, y, w, h (points) */ val frames: Map<Pair<Int, String>, List<Float>> = emptyMap(),
        /** (slide uid, item id): the file's own items deleted */ val removed: Set<Pair<Int, String>> = emptySet(),
        /** Design's theme chosen (null: the file's own) — shown and saved from a copy of the deck with it */ val theme: SlideTheme? = null,
        /** a slide's speaker notes as typed (asked 17 Oct), a line a paragraph */ val notes: Map<Int, String> = emptyMap(),
        /** a slide's date, number and footer as chosen (Insert › Header & footer) — its own ones replaced */ val footers: Map<Int, Footers> = emptyMap(),
        /** a slide's comments as they are now (Review › Comments: added, edited, answered, deleted), the whole list */ val comments: Map<Int, List<SlideComment>> = emptyMap()) {
        fun next() = (order.maxOfOrNull { it.uid } ?: -1) + 1
        /** A new shape id on slide [uid]: past the slide's own (theirs are small numbers) and those added. */
        // past every id in use: the slide's own items' ([taken]: a picture or shape saved here before is one of them now) and those put on in this edit
        fun newId(uid: Int, taken: Collection<String> = emptyList()) =
            ((taken + added.values.flatten().map { it.id }).mapNotNull { it.toIntOrNull() }.filter { it >= 9000 }.maxOrNull()?.plus(1) ?: (9000 + added.values.sumOf { it.size })).toString()
    }
    fun start(deck: Deck) = Edit(deck.slides.indices.map { Entry(it, it) })
    /** [edit] with item [id] of slide [uid] at [x], [y], [w] × [h]: one put on it here, or the file's own. */
    fun framed(edit: Edit, uid: Int, id: String, x: Float, y: Float, w: Float, h: Float): Edit {
        val added = edit.added[uid].orEmpty()
        return if (added.any { it.id == id }) edit.copy(added = edit.added + (uid to added.map { if (it.id == id) it.copy(x = x, y = y, w = w, h = h) else it }))
        else edit.copy(frames = edit.frames + ((uid to id) to listOf(x, y, w, h)))
    }
    /** Where item [id] is on [s]: the item, or for a group its items' bounds (a box with no shape of its own, never drawn). */
    fun box(s: Slide, id: String): SlideItem? {
        val mine = s.items.filter { it.anim == id }
        if (mine.size <= 1) return mine.firstOrNull()
        val x = mine.minOf { it.x }; val y = mine.minOf { it.y }
        return SlideItem.Shape(x, y, mine.maxOf { it.x + it.w } - x, mine.maxOf { it.y + it.h } - y, 0f, null, null).also { it.anim = id; it.grouped = true }
    }
    /** [item] moved or sized to [f] (x, y, w, h); in a group, [group] (the group's bounds) is what moved, the item with it. */
    private fun moved(item: SlideItem, f: List<Float>?, group: SlideItem?): SlideItem {
        if (f == null) return item
        val (x, y, w, h) = if (group == null) f else {
            val gx = if (group.w > 0) f[2] / group.w else 1f; val gy = if (group.h > 0) f[3] / group.h else 1f
            listOf(f[0] + (item.x - group.x) * gx, f[1] + (item.y - group.y) * gy, item.w * gx, item.h * gy)
        }
        val kx = if (item.w > 0) w / item.w else 1f; val ky = if (item.h > 0) h / item.h else 1f
        return when (item) {
            is SlideItem.Shape -> SlideItem.Shape(x, y, w, h, item.rotation, item.shape, item.text, item.paths, item.flipH, item.flipV, item.gradient, item.id, item.hint)
            is SlideItem.Picture -> SlideItem.Picture(x, y, w, h, item.rotation, item.crop, item.math, item.bytes)
            is SlideItem.Chart -> SlideItem.Chart(x, y, w, h, item.chart)
            is SlideItem.Table -> SlideItem.Table(x, y, w, h, item.cols.map { it * kx }, item.rows.map { it * ky }, item.cells, item.line)
        }.also { it.anim = item.anim; it.grouped = item.grouped }
    }

    /** The edited slide of [e]: its words as typed (each line a paragraph in the look of the one it replaces). */
    fun slide(deck: Deck, edit: Edit, e: Entry): Slide {
        val s = if (e.layout >= 0) deck.layouts[e.layout].preview else deck.slides[e.src]
        val items = s.items.map { item ->
            // a new slide's placeholder typed into is no longer a hint
            val lines = (item as? SlideItem.Shape)?.id?.let { edit.texts[e.uid to it] }?.takeIf { l -> l.any { it.isNotBlank() } || !(item as SlideItem.Shape).hint } ?: return@map item
            val old = item.text
            // each line over the paragraph it came from, each letter in its run's look, as the file is saved
            val paras = PictureXml.relaid<SlidePara?, DocRun>(old?.paras.orEmpty().map { p -> p to p.runs.map { it to it.text } }, lines, null, null).map { (p, runs) ->
                // a hint's grey is not the words' colour: those take the theme's text colour
                val rs = runs.map { (r, t) -> (r ?: DocRun("", sizePt = 18f)).copy(text = t).let { if (item.hint) it.copy(color = 0) else it } }
                if (p == null) SlidePara(SlidePara.plain("", 18f, 0).para.copy(runs = rs)) else SlidePara(p.para.copy(runs = rs), p.look, p.bullet)
            }
            SlideItem.Shape(item.x, item.y, item.w, item.h, item.rotation, item.shape, old?.let { SlideText(paras, it.anchor, it.insets, it.scale, it.wrap, it.shrink, it.warp) }
                ?: SlideText(paras, 't', floatArrayOf(7.2f, 3.6f, 7.2f, 3.6f)), item.paths, item.flipH, item.flipV, item.gradient, item.id).also { it.anim = item.anim; it.grouped = item.grouped }
        }
        // what was put on it: pictures and shapes, over the rest
        val added = edit.added[e.uid].orEmpty().map { a ->
            (if (a.chart != null) SlideItem.Chart(a.x, a.y, a.w, a.h, a.chart) else if (a.prst == "table") table(a, edit.looks[e.uid to a.id]) else if (a.image != null) SlideItem.Picture(a.x, a.y, a.w, a.h, 0f, null, a.math) { runCatching { File(a.image).readBytes() }.getOrNull() }
            else {
                // its words as typed (a text box's at its top on the left, a shape's in its middle), as PowerPoint puts a new one's
                val box = a.prst == TEXT_BOX
                val text = edit.texts[e.uid to a.id]?.let { lines -> SlideText(lines.map { SlidePara.plain(it, 18f, 0, align = if (box) DocAlign.Start else DocAlign.Center) }, if (box) 't' else 'c', floatArrayOf(7.2f, 3.6f, 7.2f, 3.6f)) }
                // a text box: no fill, no border, as tall as its words (PowerPoint's "resize shape to fit text")
                // shortcut: without Android's text layout (the JVM tests) a line a paragraph, 1.2 × 18 pt each
                if (box) SlideItem.Shape(a.x, a.y, a.w, text?.takeIf { edit.looks[e.uid to a.id]?.autofit != 'n' }?.let { t -> maxOf(runCatching { SlidesText.height(t, a.w) }.getOrElse { t.paras.size * 21.6f + 7.2f }, 20f) } ?: a.h, 0f, null, text, id = a.id)
                else SlideItem.Shape(a.x, a.y, a.w, a.h, 0f, ShapePicture(a.prst ?: "rect", a.fill, darker(a.fill), 1.5f), text, id = a.id)
            }).also { it.anim = a.id } // what it is chosen and animated by
        }
        // Header & footer: the slide's own date, footer and number gone, the chosen ones in the layout's boxes (the number its place in the deck)
        val foot = edit.footers[e.uid]
        val footed = foot?.let { f -> listOfNotNull(
            s.footers["dt"]?.takeIf { f.date }?.let { worded(it, f.fixed ?: today()) },
            s.footers["ftr"]?.takeIf { f.text != null }?.let { worded(it, f.text!!) },
            s.footers["sldNum"]?.takeIf { f.number }?.let { worded(it, "${edit.order.indexOf(e) + 1}") }) }.orEmpty()
        val groups = s.items.filter { it.grouped && it.anim != null }.map { it.anim!! }.distinct().associateWith { box(s, it) }
        val all = (items.filter { (it.anim == null || (e.uid to it.anim!!) !in edit.removed) && (foot == null || (it as? SlideItem.Shape)?.id !in s.footerIds.keys) }.map { moved(it, it.anim?.let { id -> edit.frames[e.uid to id] }, if (it.grouped) groups[it.anim] else null) } + added).map { styled(it, it.anim?.let { id -> edit.looks[e.uid to id] }) } + footed
        val transition = edit.transitions[e.uid]?.takeIf { it.kind.isNotEmpty() || it.after != null } ?: s.transition.takeIf { e.uid !in edit.transitions }
        val steps = edit.anims[e.uid]?.let(::group) ?: s.steps
        return Slide(all, s.background, edit.notes[e.uid] ?: s.notes.takeIf { e.layout < 0 }.orEmpty(), edit.hidden[e.uid] ?: s.hidden, s.part, transition, steps, s.footers, s.footerIds, s.titleSlide, edit.comments[e.uid] ?: s.comments)
    }
    /** A layout's date, footer or number box [sh] holding [words], in its first run's look ([id]: its own). */
    fun worded(sh: SlideItem.Shape, words: String, id: String? = sh.id): SlideItem.Shape {
        val t = sh.text ?: return sh
        val p = t.paras.firstOrNull() ?: return sh
        val text = SlideText(listOf(SlidePara(p.para.copy(runs = p.runs.take(1).map { it.copy(text = words) }), p.look, null, p.base)), t.anchor, t.insets, t.scale, t.wrap, t.shrink)
        return SlideItem.Shape(sh.x, sh.y, sh.w, sh.h, sh.rotation, sh.shape, text, sh.paths, sh.flipH, sh.flipV, sh.gradient, id)
    }
    /** A slide's clicks as a list to edit, each effect with its start (on a click, with the one before, after it) and its wait from then. */
    fun flat(steps: List<SlideStep>): List<SlideEffect> = steps.flatMap { st ->
        // a group's chain: "with" counts from where the effect before began its run, "after" from when everything so far ends
        var base = 0; var end = 0
        st.effects.mapIndexed { i, e ->
            val start = if (i == 0) (if (st.auto) 'a' else 'c') else if (e.start == 'c') 'w' else e.start
            if (start == 'a' && i > 0) base = end
            if (start == 'c') base = 0
            end = maxOf(end, e.delay + e.ms)
            e.copy(start = start, wait = (e.delay - base).coerceAtLeast(0))
        }
    }
    /** The list back as clicks: a click begins a group; with the one before starts as it does, after it when it ends (a first one not on a click: by itself); each then waits its own time. */
    fun group(list: List<SlideEffect>): List<SlideStep> {
        val out = ArrayList<Pair<Boolean, MutableList<SlideEffect>>>()
        var base = 0
        for (fx in list) {
            val cur = out.lastOrNull()
            if (fx.start == 'c' || cur == null) { base = 0; out += (fx.start != 'c') to mutableListOf(fx.copy(delay = fx.wait)); continue }
            if (fx.start == 'a') base = cur.second.maxOf { it.delay + it.ms }
            cur.second += fx.copy(delay = base + fx.wait)
        }
        return out.map { SlideStep(it.first, it.second) }
    }
    /** [item] in the looks chosen for it (a shape: its fill, border, words' colour; anything else as it is). */
    private fun styled(item: SlideItem, l: Look?): SlideItem {
        if (l == null || item !is SlideItem.Shape) return item
        val sp = item.shape
        fun pick(chosen: Int?, had: Int?) = if (chosen == null) had else chosen.takeIf { it != NONE }
        val shape = if (l.fill == null && l.line == null) sp else ShapePicture(sp?.geometry ?: "rect", pick(l.fill, sp?.fill), pick(l.line, sp?.line), sp?.lineWidth?.takeIf { it > 0 } ?: 1.5f, sp?.custom, sp?.adjust ?: emptyMap())
        val text = item.text?.let { looked(it, l) }
        // the box grown (or shrunk) to its words, as PowerPoint's "resize shape to fit text"
        val h = if (l.autofit == 'g' && text != null) SlidesText.height(text, item.w) else item.h
        return SlideItem.Shape(item.x, item.y, item.w, h, item.rotation, shape, text, item.paths, item.flipH, item.flipV, item.gradient.takeIf { l.fill == null }, item.id, item.hint).also { it.anim = item.anim; it.grouped = item.grouped }
    }
    /** A table put on a slide, as PowerPoint's default style shows it: the first row in the accent, the others banded. */
    fun table(a: Added, look: Look? = null): SlideItem.Table {
        fun cell(r: Int, c: Int): Pair<Int?, SlideText?> {
            val head = r == 0
            val words = a.cells.getOrNull(r * a.cols + c).orEmpty()
            val fill = cellFill(look?.fill, r)
            return fill to SlideText(listOf(SlidePara.plain(words, 14f, look?.text ?: if (head) 0xFFFFFF else 0, bold = head)), 't', floatArrayOf(7.2f, 3.6f, 7.2f, 3.6f))
        }
        return SlideItem.Table(a.x, a.y, a.w, a.h, List(a.cols) { a.w / a.cols }, List(a.rows) { a.h / a.rows }, List(a.rows) { r -> List(a.cols) { c -> cell(r, c) } }, 0xFFFFFFFF.toInt())
    }
    /** A table's cell colour: the first row in [accent] (PowerPoint's default style's, else the chosen colour), the others banded in its tints. */
    internal fun cellFill(accent: Int?, r: Int): Int {
        if (accent == null || accent == NONE) return if (r == 0) 0xFF4472C4.toInt() else if (r % 2 == 1) 0xFFCFD5EA.toInt() else 0xFFE9EBF5.toInt()
        if (r == 0) return accent
        val k = if (r % 2 == 1) 0.75f else 0.87f // toward white
        fun ch(s: Int) = ((((accent shr s) and 255) + (255 - ((accent shr s) and 255)) * k).toInt() and 255) shl s
        return 0xFF000000.toInt() or ch(16) or ch(8) or ch(0)
    }
    private fun darker(c: Int): Int {
        fun ch(s: Int) = (((c shr s) and 255) * 0.7f).toInt() shl s
        return (c and 0xFF000000.toInt()) or ch(16) or ch(8) or ch(0)
    }

    /** The boxes on [e]'s slide that grow to their words (Home's autofit): where they stand now, their height as their words make it. */
    fun grown(deck: Deck, edit: Edit, e: Entry): Map<String, List<Float>> {
        val ids = (edit.looks.filter { (k, l) -> k.first == e.uid && l.autofit == 'g' }.keys.map { it.second } +
            edit.added[e.uid].orEmpty().filter { it.prst == TEXT_BOX && edit.looks[e.uid to it.id]?.autofit != 'n' }.map { it.id }).ifEmpty { return emptyMap() }
        val shown = slide(deck, edit, e).items.filterIsInstance<SlideItem.Shape>()
        return ids.mapNotNull { id -> shown.firstOrNull { it.id == id || it.anim == id }?.let { id to listOf(it.x, it.y, it.w, it.h) } }.toMap()
    }

    /** The edited deck, as it shows. */
    fun view(deck: Deck, edit: Edit) = Deck(deck.width, deck.height, edit.order.map { slide(deck, edit, it) })

    /** The words of a text box as lines, for editing: one a paragraph. */
    fun lines(item: SlideItem.Shape): List<String> = item.text?.paras?.map { p -> p.runs.joinToString("") { it.text } } ?: listOf("") // a line break kept (LINE_BREAK)

    private fun Element.kids(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun xml(doc: org.w3c.dom.Document): ByteArray = domXml(doc, standalone = true)

    /** [el] put in [parent] where DrawingML's [order] of children has it (before the first that comes after it); one of its name there before goes. */
    private fun place(parent: Element, el: Element, order: List<String>) {
        parent.kids().filter { it.localName == el.localName }.forEach { parent.removeChild(it) }
        val at = order.indexOf(el.localName)
        parent.insertBefore(el, parent.kids().firstOrNull { order.indexOf(it.localName) > at })
    }
    private val RPR = listOf("ln", "noFill", "solidFill", "gradFill", "blipFill", "pattFill", "grpFill", "effectLst", "effectDag", "highlight", "uLnTx", "uLn", "uFillTx", "uFill",
        "latin", "ea", "cs", "sym", "hlinkClick", "hlinkMouseOver", "rtl", "extLst")
    private val PPR = listOf("lnSpc", "spcBef", "spcAft", "buClrTx", "buClr", "buSzTx", "buSzPct", "buSzPts", "buFontTx", "buFont", "buNone", "buAutoNum", "buChar", "buBlip", "tabLst", "defRPr", "extLst")
    private val BODY = listOf("prstTxWarp", "noAutofit", "normAutofit", "spAutoFit", "scene3d", "sp3d", "flatTx", "extLst")

    /** [p]'s first letter raised as [l] says — a run of its own at its size, face and colour — or (0) a raised one given the next run's size. */
    private fun raise(doc: org.w3c.dom.Document, p: Element, l: Look) {
        val runs = p.kids().filter { it.localName == "r" }
        val r = runs.firstOrNull { it.kids().any { t -> t.localName == "t" && t.textContent.isNotEmpty() } } ?: return
        val t = r.kids().first { it.localName == "t" }
        fun rPr(of: Element) = of.kids().firstOrNull { it.localName == "rPr" } ?: doc.createElementNS(A, "a:rPr").also { of.insertBefore(it, of.firstChild) }
        if (l.raise!! > 0f) {
            val n = t.textContent.offsetByCodePoints(0, 1)
            val letter = r.cloneNode(true) as Element
            letter.kids().first { it.localName == "t" }.textContent = t.textContent.substring(0, n)
            rPr(letter).let { pr -> pr.setAttribute("sz", "${Math.round(l.raise * 100)}")
                l.raiseColor?.let { c -> listOf("noFill", "solidFill", "gradFill").forEach { nm -> pr.kids().filter { it.localName == nm }.forEach { pr.removeChild(it) } }
                    place(pr, doc.createElementNS(A, "a:solidFill").also { it.appendChild(doc.createElementNS(A, "a:srgbClr").also { s -> s.setAttribute("val", "%06X".format(c and 0xFFFFFF)) }) }, RPR) }
                l.raiseFont?.let { f -> place(pr, doc.createElementNS(A, "a:latin").also { it.setAttribute("typeface", f) }, RPR) } }
            p.insertBefore(letter, r)
            if (n < t.textContent.length) t.textContent = t.textContent.substring(n) else p.removeChild(r)
        } else {
            // plain again: one letter half again as big as the run after it takes that run's size
            val next = runs.getOrNull(runs.indexOf(r) + 1) ?: return
            val size = rPr(next).getAttribute("sz").toIntOrNull(); val mine = rPr(r).getAttribute("sz").toIntOrNull() ?: return
            if (t.textContent.codePointCount(0, t.textContent.length) == 1 && size != null && mine >= size * 1.5) rPr(r).setAttribute("sz", "$size")
        }
    }

    /** A text body ([body]: a:txBody) in Home's looks [l], as PowerPoint writes them: on every run and paragraph mark, each paragraph, the body. */
    private fun words(doc: org.w3c.dom.Document, body: Element, l: Look, link: String? = null) {
        fun a(name: String, vararg attrs: Pair<String, String>) = doc.createElementNS(A, "a:$name").also { e -> attrs.forEach { (k, v) -> e.setAttribute(k, v) } }
        fun rgb(c: Int, holder: String) = a(holder).also { it.appendChild(a("srgbClr", "val" to "%06X".format(c and 0xFFFFFF))) }
        val paras = body.getElementsByTagNameNS(A, "p").let { ps -> (0 until ps.length).map { ps.item(it) as Element } }
        for (p in paras) {
            for (rPr in p.kids().filter { it.localName in setOf("r", "fld") }.map { r -> r.kids().firstOrNull { it.localName == "rPr" } ?: a("rPr").also { r.insertBefore(it, r.firstChild) } } +
                p.kids().filter { it.localName == "endParaRPr" }) {
                if (l.cleared) {
                    // the letters' own looks gone: their paragraph's (the level's, the master's) show; their language and links stay
                    listOf("b", "i", "u", "strike", "sz", "baseline", "cap", "spc", "kern").forEach { rPr.removeAttribute(it) }
                    rPr.kids().filter { it.localName !in setOf("hlinkClick", "hlinkMouseOver", "rtl", "extLst") }.forEach { rPr.removeChild(it) }
                }
                l.size?.let { rPr.setAttribute("sz", "${Math.round(it * 100)}") }
                l.bold?.let { rPr.setAttribute("b", if (it) "1" else "0") }
                l.italic?.let { rPr.setAttribute("i", if (it) "1" else "0") }
                l.underline?.let { rPr.setAttribute("u", if (it) "sng" else "none") }
                l.strike?.let { rPr.setAttribute("strike", if (it) "sngStrike" else "noStrike") }
                l.text?.let { c -> listOf("noFill", "gradFill", "blipFill", "pattFill", "grpFill").forEach { n -> rPr.kids().filter { it.localName == n }.forEach { rPr.removeChild(it) } }; place(rPr, rgb(c, "solidFill"), RPR) }
                l.highlight?.let { h -> if (h == NONE) rPr.kids().filter { it.localName == "highlight" }.forEach { rPr.removeChild(it) } else place(rPr, rgb(h, "highlight"), RPR) }
                l.font?.let { f -> place(rPr, a("latin", "typeface" to f), RPR); rPr.kids().filter { it.localName in setOf("ea", "cs") }.forEach { it.setAttribute("typeface", f) } }
                // WordArt: the letters' outline (a:ln) and their glow and shadow (a:effectLst), as PowerPoint writes them; none said too, over inherited ones
                l.effects?.let { fx ->
                    rPr.kids().filter { it.localName in setOf("ln", "effectLst", "effectDag") }.forEach { rPr.removeChild(it) }
                    place(rPr, a("ln", "w" to "12700").also { ln -> ln.appendChild(fx.outline?.let { rgb(it, "solidFill") } ?: a("noFill")) }, RPR)
                    place(rPr, a("effectLst").also { fl ->
                        fx.glow?.let { g -> fl.appendChild(a("glow", "rad" to "101600").also { it.appendChild(a("srgbClr", "val" to "%06X".format(g and 0xFFFFFF)).also { c -> c.appendChild(a("alpha", "val" to "40000")) }) }) }
                        if (fx.shadow) fl.appendChild(a("outerShdw", "blurRad" to "38100", "dist" to "38100", "dir" to "2700000", "algn" to "tl", "rotWithShape" to "0").also { it.appendChild(a("srgbClr", "val" to "000000").also { c -> c.appendChild(a("alpha", "val" to "60000")) }) })
                    }, RPR)
                }
                // Link: its relationship ([link]: its rId, and " slide" for a jump to a slide, as PowerPoint's); "" taken off
                if (l.link != null && rPr.localName == "rPr") {
                    rPr.kids().filter { it.localName == "hlinkClick" }.forEach { rPr.removeChild(it) }
                    link?.split(' ')?.let { (rid) -> place(rPr, a("hlinkClick").also { h -> h.setAttributeNS(R, "r:id", rid); if (link.endsWith(" slide")) h.setAttribute("action", "ppaction://hlinksldjump") }, RPR) }
                }
            }
            if (p === paras.firstOrNull { q -> q.getElementsByTagNameNS(A, "t").let { ts -> (0 until ts.length).any { ts.item(it).textContent.isNotBlank() } } }) l.raise?.let { raise(doc, p, l) }
            val first = paras.indexOfFirst { q -> q.getElementsByTagNameNS(A, "t").let { ts -> (0 until ts.length).any { ts.item(it).textContent.isNotBlank() } } }
            val dropped = l.drop != null && first >= 0 && p in paras.subList(first, minOf(first + if (l.dropRest) 2 else 1, paras.size))
            if (l.align == null && l.list == null && l.spacing == null && !dropped) continue
            val pPr = p.kids().firstOrNull { it.localName == "pPr" } ?: a("pPr").also { p.insertBefore(it, p.firstChild) }
            // dropped: the lines beside the letter stand in, with no space under them; the rest goes on under them
            if (dropped) { pPr.setAttribute("indent", "0")
                if (p === paras[first]) { pPr.setAttribute("marL", "${Math.round(l.drop!! * 12700)}"); place(pPr, a("spcAft").also { it.appendChild(a("spcPts", "val" to "0")) }, PPR) }
                else place(pPr, a("spcBef").also { it.appendChild(a("spcPts", "val" to "0")) }, PPR) }
            l.align?.let { pPr.setAttribute("algn", when (it) { DocAlign.Center -> "ctr"; DocAlign.End -> "r"; DocAlign.Justify -> "just"; else -> "l" }) }
            l.spacing?.let { m -> place(pPr, a("lnSpc").also { it.appendChild(a("spcPct", "val" to "${Math.round(m * 100_000)}")) }, PPR) }
            l.list?.let { kind ->
                pPr.kids().filter { it.localName in setOf("buNone", "buAutoNum", "buChar", "buBlip", "buFont", "buFontTx") }.forEach { pPr.removeChild(it) }
                if (kind == 'n') { place(pPr, a("buNone"), PPR); pPr.setAttribute("marL", "0"); pPr.setAttribute("indent", "0") }
                else {
                    place(pPr, if (kind == 'b') a("buFont", "typeface" to "Arial") else a("buFont", "typeface" to "+mj-lt"), PPR)
                    place(pPr, if (kind == 'b') a("buChar", "char" to "•") else a("buAutoNum", "type" to "arabicPeriod"), PPR)
                    if ((pPr.getAttribute("marL").toLongOrNull() ?: 0L) == 0L) { pPr.setAttribute("marL", "342900"); pPr.setAttribute("indent", "-342900") }
                }
            }
        }
        val bodyPr = body.kids().firstOrNull { it.localName == "bodyPr" } ?: a("bodyPr").also { body.insertBefore(it, body.firstChild) }
        l.anchor?.let { bodyPr.setAttribute("anchor", when (it) { 'c' -> "ctr"; 'b' -> "b"; else -> "t" }) }
        // WordArt's bend: PowerPoint's preset text warp, first in the body's settings
        l.warp?.let { w -> bodyPr.kids().filter { it.localName == "prstTxWarp" }.forEach { bodyPr.removeChild(it) }
            if (w.isNotEmpty()) { place(bodyPr, a("prstTxWarp", "prst" to w).also { it.appendChild(a("avLst")) }, BODY); bodyPr.setAttribute("fromWordArt", "1") } else bodyPr.removeAttribute("fromWordArt") }
        l.autofit?.let { f -> place(bodyPr, a(when (f) { 's' -> "normAutofit"; 'g' -> "spAutoFit"; else -> "noAutofit" }), BODY)
            BODY.subList(1, 4).filter { it != (when (f) { 's' -> "normAutofit"; 'g' -> "spAutoFit"; else -> "noAutofit" }) }.forEach { n -> bodyPr.kids().filter { it.localName == n }.forEach { bodyPr.removeChild(it) } } }
    }

    /**
     * A DOM as UTF-8 XML, a letter beyond U+FFFF (an emoji) never written as its two halves ([OfficeFormats.joinHalves]).
     */
    fun domXml(doc: org.w3c.dom.Document, standalone: Boolean = false): ByteArray = java.io.ByteArrayOutputStream().also { out ->
        javax.xml.transform.TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8"); if (standalone) setOutputProperty(javax.xml.transform.OutputKeys.STANDALONE, "yes")
        }.transform(javax.xml.transform.dom.DOMSource(doc), javax.xml.transform.stream.StreamResult(out))
    }.toString("UTF-8").let(OfficeFormats::joinHalves).toByteArray()

    /** A slide's XML with [texts] (shape id → lines) in its shapes: each paragraph keeps its look, its words in its first run; [added] put on it ([rels]: each picture's relationship). */
    fun withTexts(bytes: ByteArray, texts: Map<String, List<String>>, hidden: Boolean?, added: List<Added> = emptyList(), rels: Map<String, String> = emptyMap(),
        looks: Map<String, Look> = emptyMap(), transition: SlideTransition? = null, anims: List<SlideEffect>? = null, frames: Map<String, List<Float>> = emptyMap(),
        removed: Set<String> = emptySet(), footers: Footers? = null, number: Int = 0, layoutPhs: List<Triple<String, String?, String?>> = emptyList()): ByteArray {
        val doc = OfficeFormats.parse(bytes)
        val root = doc.documentElement
        val tree = root.getElementsByTagNameNS(P, "spTree").item(0) as Element?
        // Insert › Header & footer: the slide's own date, footer and number placeholders written again from the choices, in its layout's boxes
        if (footers != null && tree != null) {
            val kinds = setOf("dt", "ftr", "sldNum")
            tree.kids().filter { sp -> sp.localName == "sp" && sp.getElementsByTagNameNS(P, "ph").let { l -> l.length > 0 && (l.item(0) as Element).getAttribute("type") in kinds } }.forEach { tree.removeChild(it) }
            val lang = java.util.Locale.getDefault().toLanguageTag()
            fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            fun field(type: String, words: String) = """<a:fld id="{${java.util.UUID.randomUUID().toString().uppercase()}}" type="$type"><a:rPr lang="$lang"/><a:t>${esc(words)}</a:t></a:fld>"""
            fun run(words: String) = """<a:r><a:rPr lang="$lang"/><a:t>${esc(words)}</a:t></a:r>"""
            var id = (root.getElementsByTagNameNS(P, "cNvPr").let { l -> (0 until l.length).mapNotNull { (l.item(it) as Element).getAttribute("id").toIntOrNull() } }.maxOrNull() ?: 1) + 1
            for ((type, idx, sz) in layoutPhs) {
                val runs = when (type) {
                    "dt" -> if (!footers.date) null else footers.fixed?.let(::run) ?: field("datetime1", today())
                    "ftr" -> footers.text?.let(::run)
                    else -> if (footers.number) field("slidenum", "$number") else null
                } ?: continue
                val sp = doc.importNode(OfficeFormats.parse(("""<p:spTree xmlns:p="$P" xmlns:a="$A">""" + Slides.footerSp(type, idx, sz, "${id++}", runs) + "</p:spTree>").toByteArray()).documentElement.firstChild, true)
                tree.insertBefore(sp, tree.kids().firstOrNull { it.localName == "extLst" })
            }
        }
        fun emu(pt: Float) = (pt * 12700).toLong()
        for (a in added) {
            val xfrm = """<a:xfrm><a:off x="${emu(a.x)}" y="${emu(a.y)}"/><a:ext cx="${emu(a.w)}" cy="${emu(a.h)}"/></a:xfrm>"""
            val ns = """xmlns:p="$P" xmlns:a="$A" xmlns:r="$R""""
            fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            // a chart as PowerPoint's Insert › Chart makes it: a frame naming its chart part
            val x = if (a.chart != null) """<p:graphicFrame $ns><p:nvGraphicFramePr><p:cNvPr id="${a.id}" name="Chart ${a.id}"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr><p:xfrm><a:off x="${emu(a.x)}" y="${emu(a.y)}"/><a:ext cx="${emu(a.w)}" cy="${emu(a.h)}"/></p:xfrm><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/chart"><c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" r:id="${rels[a.id]}"/></a:graphicData></a:graphic></p:graphicFrame>"""
                else if (a.prst == "table") """<p:graphicFrame $ns><p:nvGraphicFramePr><p:cNvPr id="${a.id}" name="Table ${a.id}"/><p:cNvGraphicFramePr><a:graphicFrameLocks noGrp="1"/></p:cNvGraphicFramePr><p:nvPr/></p:nvGraphicFramePr><p:xfrm><a:off x="${emu(a.x)}" y="${emu(a.y)}"/><a:ext cx="${emu(a.w)}" cy="${emu(a.h)}"/></p:xfrm><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table"><a:tbl><a:tblPr firstRow="1" bandRow="1"><a:tableStyleId>{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}</a:tableStyleId></a:tblPr><a:tblGrid>""" +
                    (0 until a.cols).joinToString("") { """<a:gridCol w="${emu(a.w / a.cols)}"/>""" } + "</a:tblGrid>" +
                    (0 until a.rows).joinToString("") { r -> """<a:tr h="${emu(a.h / a.rows)}">""" + (0 until a.cols).joinToString("") { c ->
                        val look = looks[a.id]
                        fun solid(c: Int) = """<a:solidFill><a:srgbClr val="${"%06X".format(c and 0xFFFFFF)}"/></a:solidFill>"""
                        val rPr = """<a:rPr lang="el-GR" sz="1400"${if (r == 0) " b=\"1\"" else ""}>${look?.text?.let(::solid).orEmpty()}</a:rPr>"""
                        """<a:tc><a:txBody><a:bodyPr/><a:lstStyle/><a:p>""" + (a.cells.getOrNull(r * a.cols + c)?.takeIf { it.isNotEmpty() }?.let { """<a:r>$rPr<a:t>${esc(it)}</a:t></a:r>""" } ?: """<a:endParaRPr lang="el-GR" sz="1400"/>""") +
                            "</a:p></a:txBody><a:tcPr>${look?.fill?.takeIf { it != NONE }?.let { solid(cellFill(it, r)) }.orEmpty()}</a:tcPr></a:tc>"
                    } + "</a:tr>" } + "</a:tbl></a:graphicData></a:graphic></p:graphicFrame>"
                else if (a.image != null) """<p:pic $ns><p:nvPicPr><p:cNvPr id="${a.id}" name="Picture ${a.id}"/><p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="${rels[a.id]}"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr>$xfrm<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>"""
                // a text box as PowerPoint's Insert › Text Box makes it: no fill, words from the top left, the box fitting them
                else if (a.prst == TEXT_BOX) """<p:sp $ns><p:nvSpPr><p:cNvPr id="${a.id}" name="TextBox ${a.id}"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr><p:spPr>$xfrm<a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/></p:spPr><p:txBody><a:bodyPr wrap="square" rtlCol="0"><a:spAutoFit/></a:bodyPr><a:lstStyle/><a:p><a:endParaRPr lang="el-GR" sz="1800"/></a:p></p:txBody></p:sp>"""
                else """<p:sp $ns><p:nvSpPr><p:cNvPr id="${a.id}" name="Shape ${a.id}"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>$xfrm<a:prstGeom prst="${a.prst ?: "rect"}"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="${"%06X".format(a.fill and 0xFFFFFF)}"/></a:solidFill><a:ln w="19050"><a:solidFill><a:srgbClr val="${"%06X".format(darker(a.fill) and 0xFFFFFF)}"/></a:solidFill></a:ln></p:spPr><p:txBody><a:bodyPr rtlCol="0" anchor="ctr"/><a:lstStyle/><a:p><a:pPr algn="ctr"/><a:endParaRPr lang="el-GR"/></a:p></p:txBody></p:sp>"""
            // an equation as PowerPoint writes one: the equation in a text box (a14:m), its picture for those that cannot draw it (LibreOffice)
            val y = a.math?.takeIf { a.image != null }?.let { m ->
                val omml = OfficeMath.equation(m, true).replace(Regex("<w:rPr>.*?</w:rPr>"), """<a:rPr lang="en-US"><a:latin typeface="Cambria Math"/></a:rPr>""")
                """<mc:AlternateContent xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" xmlns:a14="http://schemas.microsoft.com/office/drawing/2010/main" $ns><mc:Choice Requires="a14">""" +
                    """<p:sp><p:nvSpPr><p:cNvPr id="${a.id}" name="Equation ${a.id}"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr><p:spPr>$xfrm<a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>""" +
                    """<p:txBody><a:bodyPr wrap="none" lIns="0" tIns="0" rIns="0" bIns="0" rtlCol="0"><a:spAutoFit/></a:bodyPr><a:lstStyle/><a:p><a14:m>$omml</a14:m><a:endParaRPr lang="en-US"/></a:p></p:txBody></p:sp>""" +
                    "</mc:Choice><mc:Fallback>$x</mc:Fallback></mc:AlternateContent>"
            } ?: x
            tree?.appendChild(doc.importNode(OfficeFormats.parse(y.toByteArray()).documentElement, true))
        }
        if (hidden != null) { if (hidden) root.setAttribute("show", "0") else root.removeAttribute("show") }
        val shapes = root.getElementsByTagNameNS(P, "sp").let { l -> (0 until l.length).map { l.item(it) as Element } }
        for ((id, lines) in texts) {
            val sp = shapes.firstOrNull { s -> s.getElementsByTagNameNS(P, "cNvPr").item(0)?.let { (it as Element).getAttribute("id") } == id } ?: continue
            val body = sp.kids().firstOrNull { it.localName == "txBody" } ?: continue
            val paras = body.kids().filter { it.localName == "p" }.ifEmpty { continue }
            // each line over the paragraph it came from, each letter in its run's look (Words' text boxes' way): a bold
            // word stays bold, a field kept whole stays a field, a line break stays one
            val parts = setOf("r", "br", "fld")
            fun letters(r: Element) = if (r.localName == "br") LINE_BREAK.toString() else r.kids().firstOrNull { it.localName == "t" }?.textContent.orEmpty()
            val old = paras.map { p -> p to p.kids().filter { it.localName in parts }.map { it to letters(it) }.filter { it.second.isNotEmpty() } }
            fun rPr(of: Element?) = doc.createElementNS(A, "a:rPr").also { rp ->
                // a run's look: its own, else (a new paragraph's first words) its paragraph mark's
                val from = (if (of?.localName == "endParaRPr") of else of?.kids()?.firstOrNull { it.localName == "rPr" }) ?: return@also
                for (k in 0 until from.attributes.length) from.attributes.item(k).let { at -> rp.setAttribute(at.nodeName, at.nodeValue) }
                from.kids().forEach { rp.appendChild(it.cloneNode(true)) }
            }
            fun run(look: Element?, words: String) = doc.createElementNS(A, "a:r").also { r ->
                r.appendChild(rPr(look)); r.appendChild(doc.createElementNS(A, "a:t").also { it.textContent = words })
            }
            val made = PictureXml.relaid<Element, Element>(old, lines, paras.last(), null).map { (from, runs) ->
                val p = from.cloneNode(true) as Element
                p.kids().filter { it.localName in parts }.forEach { p.removeChild(it) }
                val end = p.kids().firstOrNull { it.localName == "endParaRPr" }
                fun put(e: Element) { p.insertBefore(e, end) }
                for ((look, words) in runs) {
                    val field = look?.takeIf { it.localName == "fld" }
                    val whole = field?.let(::letters)?.takeIf { it.isNotEmpty() && it in words }
                    // a field's letters left as they were stay the field (null here); letters typed beside it take its look
                    val pieces = if (whole == null) listOf(words) else words.indexOf(whole).let { i -> listOf(words.substring(0, i), null, words.substring(i + whole.length)) }
                    for (piece in pieces) {
                        if (piece == null) { put(field!!.cloneNode(true) as Element); continue }
                        piece.split(LINE_BREAK).forEachIndexed { k, w ->
                            if (k > 0) put(doc.createElementNS(A, "a:br").also { it.appendChild(rPr(look ?: end)) })
                            if (w.isNotEmpty()) put(if (look?.localName == "r") (look.cloneNode(true) as Element).also { r -> r.kids().first { it.localName == "t" }.textContent = w } else run(look ?: end, w))
                        }
                    }
                }
                p
            }
            paras.forEach { body.removeChild(it) }
            made.forEach { body.appendChild(it) }
        }
        fun idOf(e: Element) = e.kids().firstOrNull { it.localName.startsWith("nv") }?.kids()?.firstOrNull { it.localName == "cNvPr" }?.getAttribute("id")
        val fills = setOf("noFill", "solidFill", "gradFill", "blipFill", "pattFill", "grpFill")
        fun fill(c: Int): Element = if (c == NONE) doc.createElementNS(A, "a:noFill") else doc.createElementNS(A, "a:solidFill").also { f ->
            f.appendChild(doc.createElementNS(A, "a:srgbClr").also { it.setAttribute("val", "%06X".format(c and 0xFFFFFF)) }) }
        /** [el]'s fill made [c]: its old one gone, the new one before the first of [after] (the schema's order). */
        fun refill(el: Element, c: Int, after: Set<String>) {
            el.kids().filter { it.localName in fills }.forEach { el.removeChild(it) }
            el.insertBefore(fill(c), el.kids().firstOrNull { it.localName in after })
        }
        // a group's looks: each shape in it
        fun named(id: String) = root.getElementsByTagNameNS(P, "cNvPr").let { l -> (0 until l.length).map { l.item(it) as Element } }.firstOrNull { it.getAttribute("id") == id }?.parentNode?.parentNode as? Element
        for ((id, l) in looks) for (sp in shapes.filter { idOf(it) == id }.ifEmpty { named(id)?.takeIf { it.localName == "grpSp" }?.getElementsByTagNameNS(P, "sp")?.let { g -> (0 until g.length).map { g.item(it) as Element } }.orEmpty() }) {
            val spPr = sp.kids().firstOrNull { it.localName == "spPr" } ?: continue
            l.fill?.let { refill(spPr, it, setOf("ln", "effectLst", "effectDag", "scene3d", "sp3d", "extLst")) }
            l.line?.let { c ->
                val ln = spPr.kids().firstOrNull { it.localName == "ln" } ?: doc.createElementNS(A, "a:ln").also { n ->
                    if (c != NONE) n.setAttribute("w", "19050"); spPr.insertBefore(n, spPr.kids().firstOrNull { it.localName in setOf("effectLst", "effectDag", "scene3d", "sp3d", "extLst") }) }
                refill(ln, c, ln.kids().map { it.localName }.filter { it !in fills }.toSet())
            }
            if (l.words) sp.kids().firstOrNull { it.localName == "txBody" }?.let { body -> words(doc, body, l, rels["link $id"]) }
        }
        // deleted: the item gone from the tree (its animation is taken out by the caller, so no timing names it)
        for (id in removed) (root.getElementsByTagNameNS(P, "cNvPr").let { l -> (0 until l.length).map { l.item(it) as Element } }.firstOrNull { it.getAttribute("id") == id }
            ?.parentNode?.parentNode as? Element)?.let { it.parentNode.removeChild(it) }
        // moved or sized: the item's own place (one in a group keeps its place in the group)
        val named = root.getElementsByTagNameNS(P, "cNvPr").let { l -> (0 until l.length).map { l.item(it) as Element } }
        for ((id, f) in frames) {
            val el = named.firstOrNull { it.getAttribute("id") == id }?.parentNode?.parentNode as? Element ?: continue
            if ((el.parentNode as? Element)?.localName != "spTree") continue
            val xfrm = if (el.localName == "graphicFrame") el.kids().firstOrNull { it.localName == "xfrm" }
                else el.kids().firstOrNull { it.localName == "spPr" || it.localName == "grpSpPr" }?.let { sp -> sp.kids().firstOrNull { it.localName == "xfrm" } ?: doc.createElementNS(A, "a:xfrm").also { sp.insertBefore(it, sp.firstChild) } }
            if (xfrm == null) continue
            fun put(name: String, a: String, av: Float, b: String, bv: Float) {
                val n = xfrm.kids().firstOrNull { it.localName == name } ?: doc.createElementNS(A, "a:$name").also { if (name == "off") xfrm.insertBefore(it, xfrm.firstChild) else xfrm.insertBefore(it, xfrm.kids().firstOrNull { k -> k.localName != "off" }) }
                n.setAttribute(a, "${emu(av)}"); n.setAttribute(b, "${emu(bv)}")
            }
            put("off", "x", f[0], "y", f[1]); put("ext", "cx", f[2], "cy", f[3])
        }
        fun place(el: Element, before: Set<String>) { root.insertBefore(doc.importNode(el, true), root.kids().firstOrNull { it.localName in before }) }
        if (transition != null) {
            root.kids().filter { it.localName == "transition" || (it.localName == "AlternateContent" && it.getElementsByTagNameNS(P, "transition").length > 0) }.forEach { root.removeChild(it) }
            val spd = when { transition.ms <= 500 -> "fast"; transition.ms >= 1000 -> "slow"; else -> "med" }
            val dir = transition.dir?.takeIf { transition.kind in setOf("push", "wipe", "cover", "pull") }?.let { """ dir="$it"""" }.orEmpty()
            val adv = transition.after?.let { """ advTm="$it"""" }.orEmpty()
            val inner = if (transition.kind.isNotEmpty()) "<p:${transition.kind}$dir/>" else ""
            if (transition.kind.isNotEmpty() || transition.after != null) place(OfficeFormats.parse("""<p:transition xmlns:p="$P" spd="$spd"$adv>$inner</p:transition>""".toByteArray()).documentElement, setOf("timing", "extLst"))
        }
        if (anims != null) {
            root.kids().filter { it.localName == "timing" }.forEach { root.removeChild(it) }
            // only what is still on the slide (a deleted shape's animation would make PowerPoint call the file damaged)
            val ids = root.getElementsByTagNameNS(P, "cNvPr").let { l -> (0 until l.length).map { (l.item(it) as Element).getAttribute("id") } }.toSet()
            val kept = anims.filter { it.target in ids }
            val sps = kept.map { it.target }.distinct().filter { id -> shapes.any { idOf(it) == id } }.toSet()
            if (kept.isNotEmpty()) place(OfficeFormats.parse(timing(kept, sps).toByteArray()).documentElement, setOf("extLst"))
        }
        return xml(doc)
    }

    /**
     * A slide's timing for [anims], PowerPoint's own XML: each on its own click, an entrance or an exit — appear or
     * disappear (preset 1), fade (10), fly (2, its subtype the side: 1 top, 2 right, 4 bottom, 8 left); [sps] the
     * targets that are shapes (they take a build entry).
     */
    // shortcut: rewrites the slide's whole timing, so an effect of a kind not offered here comes back as its nearest (a pulse, a fade) when its animations are changed
    fun timing(anims: List<SlideEffect>, sps: Set<String>): String {
        var n = 2
        fun id() = ++n
        fun tgt(s: String) = """<p:tgtEl><p:spTgt spid="$s"/></p:tgtEl>"""
        // a shape's builds: one group a effect on it (its entrance 0, its exit 1…), each listed in the build list
        val groups = ArrayList<Pair<String, Int>>()
        // each click a group: its effects in pars by when they start, the first on the click (or by itself), the others with or after it
        val clicks = group(anims).joinToString("") { step ->
            val a = id()
            val begin = if (step.auto) """<p:cond delay="indefinite"/><p:cond evt="onBegin" delay="0"><p:tn val="2"/></p:cond>""" else """<p:cond delay="indefinite"/>"""
            var last: SlideEffect? = null
            val inner = step.effects.groupBy { it.delay }.toSortedMap().entries.joinToString("") { (delay, effects) ->
                val b = id()
                """<p:par><p:cTn id="$b" fill="hold"><p:stCondLst><p:cond delay="$delay"/></p:stCondLst><p:childTnLst>""" + effects.joinToString("") { fx ->
                    val node = when { last == null -> if (step.auto) "afterEffect" else "clickEffect"; fx.start == 'a' -> "afterEffect"; else -> "withEffect" }
                    last = fx
                    val s = fx.target; val ms = fx.ms.coerceAtLeast(1)
                    val grp = groups.count { it.first == s }.also { if (s in sps) groups += s to it }
                    val side = when (fx.from) { 'u' -> 1; 'r' -> 2; 'l' -> 8; else -> 4 }
                    val (preset, sub) = when (fx.kind) { "appear" -> 1 to 0; "fly" -> 2 to side; "wipe" -> 22 to side; "zoom" -> 53 to 16
                        "spin" -> 8 to 0; "grow" -> 6 to 0; "pulse" -> 26 to 0; else -> 10 to 0 }
                    val c = id()
                    // shown at the start of an entrance; hidden at the end of an exit (at once for disappear)
                    val at = if (fx.exit && fx.kind != "appear") ms - 1 else 0
                    val set = if (fx.emphasis) "" else """<p:set><p:cBhvr><p:cTn id="${id()}" dur="1" fill="hold"><p:stCondLst><p:cond delay="$at"/></p:stCondLst></p:cTn>${tgt(s)}<p:attrNameLst><p:attrName>style.visibility</p:attrName></p:attrNameLst></p:cBhvr><p:to><p:strVal val="${if (fx.exit) "hidden" else "visible"}"/></p:to></p:set>"""
                    val move = when (fx.kind) {
                        // emphasis: a whole turn; half as big again (and stays so); a little bigger and back
                        "spin" -> """<p:animRot by="21600000"><p:cBhvr><p:cTn id="${id()}" dur="$ms" fill="hold"/>${tgt(s)}<p:attrNameLst><p:attrName>r</p:attrName></p:attrNameLst></p:cBhvr></p:animRot>"""
                        "grow" -> """<p:animScale><p:cBhvr><p:cTn id="${id()}" dur="$ms" fill="hold"/>${tgt(s)}</p:cBhvr><p:by x="150000" y="150000"/></p:animScale>"""
                        "pulse" -> """<p:animScale><p:cBhvr><p:cTn id="${id()}" dur="${(ms / 2).coerceAtLeast(1)}" autoRev="1" fill="hold"/>${tgt(s)}</p:cBhvr><p:by x="110000" y="110000"/></p:animScale>"""
                        "appear" -> ""
                        "fly" -> {
                            // off the slide on its side, and where it stands
                            val edge = when (fx.from) { 'l' -> "ppt_x" to "0-#ppt_w/2"; 'r' -> "ppt_x" to "1+#ppt_w/2"; 'u' -> "ppt_y" to "0-#ppt_h/2"; else -> "ppt_y" to "1+#ppt_h/2" }
                            listOf("ppt_x", "ppt_y").joinToString("") { attr ->
                                val there = if (attr == edge.first) edge.second else "#$attr"
                                val (v0, v1) = if (fx.exit) "#$attr" to there else there to "#$attr"
                                """<p:anim calcmode="lin" valueType="num"><p:cBhvr additive="base"><p:cTn id="${id()}" dur="$ms" fill="hold"/>${tgt(s)}<p:attrNameLst><p:attrName>$attr</p:attrName></p:attrNameLst></p:cBhvr><p:tavLst><p:tav tm="0"><p:val><p:strVal val="$v0"/></p:val></p:tav><p:tav tm="100000"><p:val><p:strVal val="$v1"/></p:val></p:tav></p:tavLst></p:anim>"""
                            }
                        }
                        // PowerPoint's wipe names the way it goes: from the bottom it wipes up
                        "wipe" -> """<p:animEffect transition="${if (fx.exit) "out" else "in"}" filter="wipe(${when (fx.from) { 'u' -> "down"; 'l' -> "right"; 'r' -> "left"; else -> "up" }})"><p:cBhvr><p:cTn id="${id()}" dur="$ms"/>${tgt(s)}</p:cBhvr></p:animEffect>"""
                        // zoom: from nothing to its size (or back), fading as it goes
                        "zoom" -> listOf("ppt_w", "ppt_h").joinToString("") { attr ->
                            val (v0, v1) = if (fx.exit) "#$attr" to "0" else "0" to "#$attr"
                            """<p:anim calcmode="lin" valueType="num"><p:cBhvr><p:cTn id="${id()}" dur="$ms" fill="hold"/>${tgt(s)}<p:attrNameLst><p:attrName>$attr</p:attrName></p:attrNameLst></p:cBhvr><p:tavLst><p:tav tm="0"><p:val><p:strVal val="$v0"/></p:val></p:tav><p:tav tm="100000"><p:val><p:strVal val="$v1"/></p:val></p:tav></p:tavLst></p:anim>"""
                        } + """<p:animEffect transition="${if (fx.exit) "out" else "in"}" filter="fade"><p:cBhvr><p:cTn id="${id()}" dur="$ms"/>${tgt(s)}</p:cBhvr></p:animEffect>"""
                        else -> """<p:animEffect transition="${if (fx.exit) "out" else "in"}" filter="fade"><p:cBhvr><p:cTn id="${id()}" dur="$ms"/>${tgt(s)}</p:cBhvr></p:animEffect>"""
                    }
                    """<p:par><p:cTn id="$c" presetID="$preset" presetClass="${if (fx.emphasis) "emph" else if (fx.exit) "exit" else "entr"}" presetSubtype="$sub" fill="hold"${if (s in sps) " grpId=\"$grp\"" else ""} nodeType="$node"><p:stCondLst><p:cond delay="0"/></p:stCondLst><p:childTnLst>$set$move</p:childTnLst></p:cTn></p:par>"""
                } + "</p:childTnLst></p:cTn></p:par>"
            }
            """<p:par><p:cTn id="$a" fill="hold"><p:stCondLst>$begin</p:stCondLst><p:childTnLst>$inner</p:childTnLst></p:cTn></p:par>"""
        }
        val build = groups.takeIf { it.isNotEmpty() }?.joinToString("", "<p:bldLst>", "</p:bldLst>") { (spid, g) -> """<p:bldP spid="$spid" grpId="$g" animBg="1"/>""" }.orEmpty()
        return """<p:timing xmlns:p="$P"><p:tnLst><p:par><p:cTn id="1" dur="indefinite" restart="never" nodeType="tmRoot"><p:childTnLst><p:seq concurrent="1" nextAc="seek"><p:cTn id="2" dur="indefinite" nodeType="mainSeq"><p:childTnLst>$clicks</p:childTnLst></p:cTn>""" +
            """<p:prevCondLst><p:cond evt="onPrev" delay="0"><p:tgtEl><p:sldTgt/></p:tgtEl></p:cond></p:prevCondLst><p:nextCondLst><p:cond evt="onNext" delay="0"><p:tgtEl><p:sldTgt/></p:tgtEl></p:cond></p:nextCondLst></p:seq></p:childTnLst></p:cTn></p:par></p:tnLst>$build</p:timing>"""
    }

    /**
     * A notes page with [text] as its notes, a line a paragraph: [old]'s (its other parts as they were, the paragraphs in
     * the look of its first one), else a new one whose placeholders take their places from the notes master.
     */
    fun notesXml(old: ByteArray?, text: String): ByteArray {
        val doc = OfficeFormats.parse(old ?: ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:notes xmlns:a="$A" xmlns:r="$R" xmlns:p="$P"><p:cSld><p:spTree>""" +
            """<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>""" +
            """<p:sp><p:nvSpPr><p:cNvPr id="2" name="Slide Image Placeholder 1"/><p:cNvSpPr><a:spLocks noGrp="1" noRot="1" noChangeAspect="1"/></p:cNvSpPr><p:nvPr><p:ph type="sldImg"/></p:nvPr></p:nvSpPr><p:spPr/></p:sp>""" +
            """</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:notes>""").toByteArray())
        val root = doc.documentElement
        fun Element.under(ns: String, name: String) = getElementsByTagNameNS(ns, name).let { l -> (0 until l.length).map { l.item(it) as Element } }
        val tree = root.under(P, "spTree").first()
        val body = tree.kids().firstOrNull { sp -> sp.localName == "sp" && sp.under(P, "ph").firstOrNull()?.getAttribute("type") == "body" }
            ?: (doc.importNode(OfficeFormats.parse(("""<p:sp xmlns:a="$A" xmlns:p="$P"><p:nvSpPr><p:cNvPr id="${(root.under(P, "cNvPr").mapNotNull { it.getAttribute("id").toIntOrNull() }.maxOrNull() ?: 1) + 1}" name="Notes Placeholder"/>""" +
                """<p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:spPr/></p:sp>""").toByteArray()).documentElement, true) as Element).also { tree.appendChild(it) }
        val tx = body.kids().firstOrNull { it.localName == "txBody" } ?: doc.createElementNS(P, "p:txBody").also { t ->
            body.appendChild(t); t.appendChild(doc.createElementNS(A, "a:bodyPr")); t.appendChild(doc.createElementNS(A, "a:lstStyle")) }
        val paras = tx.kids().filter { it.localName == "p" }
        // the first paragraph's look and its first run's, for every line
        val pPr = paras.firstOrNull()?.kids()?.firstOrNull { it.localName == "pPr" }
        val rPr = paras.firstOrNull()?.under(A, "rPr")?.firstOrNull()
        paras.forEach { tx.removeChild(it) }
        for (line in text.split("\n")) tx.appendChild(doc.createElementNS(A, "a:p").also { p ->
            pPr?.let { p.appendChild(it.cloneNode(true)) }
            if (line.isNotEmpty()) p.appendChild(doc.createElementNS(A, "a:r").also { r ->
                r.appendChild(rPr?.cloneNode(true) ?: doc.createElementNS(A, "a:rPr").also { it.setAttribute("dirty", "0") })
                r.appendChild(doc.createElementNS(A, "a:t").also { it.textContent = line })
            })
        })
        return xml(doc)
    }

    /**
     * The deck saved: [source] to [target] with the slides in their new order (a duplicate a new slide part, a deleted
     * slide's parts gone), hidden ones marked, edited words in their slides, notes typed in the slides' notes pages
     * (a notes page made for a slide that had none, and the notes master for a deck that never had notes).
     */
    fun save(source: File, deck: Deck, edit: Edit, target: File) {
        val zip = java.util.zip.ZipFile(source)
        fun read(n: String) = zip.getEntry(n)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
        val out = HashMap<String, ByteArray?>()
        val presPath = "ppt/presentation.xml"; val presRels = "ppt/_rels/presentation.xml.rels"
        val pres = OfficeFormats.parse(read(presPath) ?: error("Not a presentation."))
        val rels = OfficeFormats.parse(read(presRels) ?: error("No relationships."))
        val types = String(read("[Content_Types].xml") ?: error("No content types."))
        var typesOut = types
        val list = pres.documentElement.getElementsByTagNameNS(P, "sldIdLst").item(0) as Element
        val ids = list.kids().filter { it.localName == "sldId" }
        val relOf = rels.documentElement.kids().associateBy { it.getAttribute("Id") }
        // each slide's sldId as the deck was read (the same order as deck.slides)
        val byPart = ids.associateBy { id -> relOf[id.getAttributeNS(R, "id")]?.getAttribute("Target")?.let { "ppt/" + it.removePrefix("/ppt/").removePrefix("/") } }
        var nextId = (ids.maxOfOrNull { it.getAttribute("id").toIntOrNull() ?: 255 } ?: 255) + 1
        var nextRel = (relOf.keys.mapNotNull { it.removePrefix("rId").toIntOrNull() }.maxOrNull() ?: 0) + 1
        var nextSlide = (zip.entries().asSequence().mapNotNull { Regex("""^ppt/slides/slide(\d+)\.xml$""").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
        val used = HashSet<Int>()
        ids.forEach { list.removeChild(it) }
        fun relsPath(part: String) = part.substringBeforeLast('/') + "/_rels/" + part.substringAfterLast('/') + ".rels"
        var nextMedia = (zip.entries().asSequence().mapNotNull { Regex("""^ppt/media/tetra(\d+)\.""").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
        /** The pictures and charts put on slide [uid] into the file: each a part and a relationship in [relsXml] (the slide's); their ids → rIds. */
        fun media(uid: Int, relsXml: org.w3c.dom.Document): Map<String, String> = edit.added[uid].orEmpty().filter { it.image != null }.associate { a ->
            val ext = when (a.ext) { "jpeg", "jpg" -> "jpeg"; "gif" -> "gif"; else -> "png" }
            val name = "tetra${nextMedia++}.$ext"
            out["ppt/media/$name"] = File(a.image!!).readBytes()
            if (!Regex("""<Default Extension="$ext"""", RegexOption.IGNORE_CASE).containsMatchIn(typesOut))
                typesOut = typesOut.replace("</Types>", """<Default Extension="$ext" ContentType="image/$ext"/></Types>""")
            val ids = relsXml.documentElement.kids().map { it.getAttribute("Id") }.toSet()
            val rid = generateSequence(1) { it + 1 }.map { "rId$it" }.first { it !in ids }
            relsXml.documentElement.appendChild(relsXml.createElementNS(relsXml.documentElement.namespaceURI, "Relationship").apply {
                setAttribute("Id", rid); setAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"); setAttribute("Target", "../media/$name") })
            a.id to rid
        } + edit.added[uid].orEmpty().mapNotNull { a -> a.chart?.let { ch ->
            // a chart put on: its part and its numbers in an embedded workbook, as PowerPoint keeps them (Words' ChartWriter)
            val name = generateSequence(1) { it + 1 }.map { "ppt/charts/chart$it.xml" }.first { zip.getEntry(it) == null && it !in out }
            out[name] = ChartWriter.drawingml(ch, workbook = true).toByteArray()
            val book = generateSequence(1) { it + 1 }.map { "ppt/embeddings/Microsoft_Excel_Worksheet_tetra$it.xlsx" }.first { zip.getEntry(it) == null && it !in out }
            out[book] = ChartWriter.workbook(ch)
            out["ppt/charts/_rels/" + name.substringAfterLast('/') + ".rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/package" Target="../embeddings/${book.substringAfterLast('/')}"/></Relationships>""".toByteArray()
            typesOut = typesOut.replace("</Types>", """<Override PartName="/$name" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/></Types>""")
            if (!Regex("""Extension="xlsx"""", RegexOption.IGNORE_CASE).containsMatchIn(typesOut))
                typesOut = typesOut.replace("</Types>", """<Default Extension="xlsx" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"/></Types>""")
            val ids = relsXml.documentElement.kids().map { it.getAttribute("Id") }.toSet()
            val rid = generateSequence(1) { it + 1 }.map { "rId$it" }.first { it !in ids }
            relsXml.documentElement.appendChild(relsXml.createElementNS(relsXml.documentElement.namespaceURI, "Relationship").apply {
                setAttribute("Id", rid); setAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart"); setAttribute("Target", "../charts/${name.substringAfterLast('/')}") })
            a.id to rid
        } }
        fun relsDoc(part: String?) = (part?.let { read(relsPath(it)) } ?: """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>""".toByteArray()).let(OfficeFormats::parse)
        /** A new slide part's relationships: its layout's, or the duplicated slide's without its notes (they belong to the first). */
        fun newRels(layout: String?, from: String?): org.w3c.dom.Document {
            if (layout != null) return OfficeFormats.parse("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/${layout.substringAfterLast('/')}"/></Relationships>""".toByteArray())
            val d = relsDoc(from)
            d.documentElement.kids().filter { it.getAttribute("Type").endsWith("/notesSlide") }.forEach { d.documentElement.removeChild(it) }
            return d
        }
        val relNs = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        fun rel(d: org.w3c.dom.Document, type: String, target: String): String {
            val taken = d.documentElement.kids().map { it.getAttribute("Id") }.toSet()
            val rid = generateSequence(1) { it + 1 }.map { "rId$it" }.first { it !in taken }
            d.documentElement.appendChild(d.createElementNS(d.documentElement.namespaceURI, "Relationship").apply { setAttribute("Id", rid); setAttribute("Type", "$relNs/$type"); setAttribute("Target", target) })
            return rid
        }
        val emptyRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>"""
        // the deck's notes master: its own, else one made (with a copy of the slides' theme, as PowerPoint gives it its own) the first time a notes page needs it
        val notesMaster by lazy {
            rels.documentElement.kids().firstOrNull { it.getAttribute("Type").endsWith("/notesMaster") }?.let { return@lazy "ppt/" + it.getAttribute("Target").removePrefix("/ppt/").removePrefix("/") }
            val part = "ppt/notesMasters/notesMaster1.xml"
            val mRels = OfficeFormats.parse(emptyRels.toByteArray())
            rels.documentElement.kids().firstOrNull { it.getAttribute("Type").endsWith("/theme") }?.let { t -> read("ppt/" + t.getAttribute("Target").removePrefix("/ppt/").removePrefix("/")) }?.let { theme ->
                val n = (zip.entries().asSequence().mapNotNull { Regex("""^ppt/theme/theme(\d+)\.xml$""").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
                out["ppt/theme/theme$n.xml"] = theme
                typesOut = typesOut.replace("</Types>", """<Override PartName="/ppt/theme/theme$n.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/></Types>""")
                rel(mRels, "theme", "../theme/theme$n.xml")
            }
            // its slide picture over its notes, on the notes page's paper (the deck's notesSz)
            val sz = pres.documentElement.getElementsByTagNameNS(P, "notesSz").item(0) as Element?
            val cx = sz?.getAttribute("cx")?.toLongOrNull() ?: 6858000L; val cy = sz?.getAttribute("cy")?.toLongOrNull() ?: 9144000L
            val iw = cx * 8 / 9; val ih = iw * deck.height.toLong().coerceAtLeast(1) / deck.width.toLong().coerceAtLeast(1)
            fun ph(id: Int, name: String, type: String, x: Long, y: Long, w: Long, h: Long, text: Boolean) =
                """<p:sp><p:nvSpPr><p:cNvPr id="$id" name="$name"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph type="$type"${if (type == "body") " idx=\"1\"" else ""}/></p:nvPr></p:nvSpPr>""" +
                """<p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$w" cy="$h"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr>""" +
                (if (text) """<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr lang="en-US"/></a:p></p:txBody>""" else "") + "</p:sp>"
            val lvl = (1..9).joinToString("") { """<a:lvl${it}pPr marL="${(it - 1) * 457200}" algn="l" defTabSz="914400"><a:defRPr sz="1200" kern="1200"><a:solidFill><a:schemeClr val="tx1"/></a:solidFill><a:latin typeface="+mn-lt"/><a:ea typeface="+mn-ea"/><a:cs typeface="+mn-cs"/></a:defRPr></a:lvl${it}pPr>""" }
            out[part] = ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:notesMaster xmlns:a="$A" xmlns:r="$R" xmlns:p="$P"><p:cSld><p:bg><p:bgRef idx="1001"><a:schemeClr val="bg1"/></p:bgRef></p:bg><p:spTree>""" +
                """<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>""" +
                ph(2, "Slide Image Placeholder 1", "sldImg", (cx - iw) / 2, cy / 12, iw, ih, false) +
                ph(3, "Notes Placeholder 2", "body", cx / 10, cy / 12 + ih + cy / 24, cx * 8 / 10, cy - (cy / 12 + ih + cy / 24) - cy / 12, true) +
                """</p:spTree></p:cSld><p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>""" +
                """<p:notesStyle>$lvl</p:notesStyle></p:notesMaster>""").toByteArray()
            out[relsPath(part)] = xml(mRels)
            typesOut = typesOut.replace("</Types>", """<Override PartName="/$part" ContentType="application/vnd.openxmlformats-officedocument.presentationml.notesMaster+xml"/></Types>""")
            val rid = "rId${nextRel++}"
            rels.documentElement.appendChild(rels.createElementNS(rels.documentElement.namespaceURI, "Relationship").apply { setAttribute("Id", rid); setAttribute("Type", "$relNs/notesMaster"); setAttribute("Target", "notesMasters/notesMaster1.xml") })
            // its listing: after the slide masters', as the schema orders presentation.xml
            val lst = pres.createElementNS(P, "p:notesMasterIdLst").also { l -> l.appendChild(pres.createElementNS(P, "p:notesMasterId").also { it.setAttributeNS(R, "r:id", rid) }) }
            val masters = pres.documentElement.kids().firstOrNull { it.localName == "sldMasterIdLst" }
            pres.documentElement.insertBefore(lst, masters?.nextSibling ?: pres.documentElement.firstChild)
            WriterLog.i("Slides: a notes master made")
            part
        }
        // Review › Comments as PowerPoint's older list, which LibreOffice reads and writes and PowerPoint 365 shows: each slide's
        // in a comments part, a reply naming its parent (p15:parentCm), their authors in commentAuthors.xml with each one's last number
        class Author(val id: Int, val name: String, val initials: String, var last: Int, val clr: String)
        val authorsPart = rels.documentElement.kids().firstOrNull { it.getAttribute("Type").endsWith("/commentAuthors") }?.let { "ppt/" + it.getAttribute("Target").removePrefix("/ppt/").removePrefix("/") }
        val authors = authorsPart?.let(::read)?.let(OfficeFormats::parse)?.documentElement?.kids().orEmpty().filter { it.localName == "cmAuthor" }.map {
            Author(it.getAttribute("id").toIntOrNull() ?: 0, it.getAttribute("name"), it.getAttribute("initials"), it.getAttribute("lastIdx").toIntOrNull() ?: 0, it.getAttribute("clrIdx").ifEmpty { "0" }) }.toMutableList()
        var authorsChanged = false
        fun author(name: String, initials: String?) = authors.firstOrNull { it.name == name } ?: Author((authors.maxOfOrNull { it.id } ?: -1) + 1, name,
            initials ?: name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }, 0, "${authors.size % 8}").also { authors += it }
        var nextComments = (zip.entries().asSequence().mapNotNull { Regex("""^ppt/comments/comment(\d+)\.xml$""").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
        /** Slide [part]'s comments as [list] ([own]: the slide's own part, whose old comments part goes or is written again; a copy's stays the first's). */
        fun comments(part: String, relsXml: org.w3c.dom.Document, list: List<SlideComment>, own: Boolean) {
            var reuse: String? = null
            for (r in relsXml.documentElement.kids().filter { it.getAttribute("Type").endsWith("/comments") }) {
                val path = "ppt/comments/" + r.getAttribute("Target").substringAfterLast('/')
                // the older list's part written again; PowerPoint 365's threads become the older list
                if (own) { if (reuse == null && r.getAttribute("Type") == "$relNs/comments") reuse = path else { out[path] = null; typesOut = typesOut.replace(Regex("""<Override PartName="/${Regex.escape(path)}"[^>]*/>"""), "") } }
                relsXml.documentElement.removeChild(r)
            }
            if (list.isEmpty()) { reuse?.let { out[it] = null; typesOut = typesOut.replace(Regex("""<Override PartName="/${Regex.escape(it)}"[^>]*/>"""), "") }; return }
            fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            val numbers = HashMap<String, Pair<Int, Int>>()
            val body = list.joinToString("") { sc -> val c = sc.comment
                val a = author(c.author, c.initials); a.last++; authorsChanged = true; numbers[c.id] = a.id to a.last
                val parent = c.parent?.let(numbers::get)?.let { (pa, pi) -> """<p:extLst><p:ext uri="{C676402C-5697-4E1C-873F-D02D1690AC5C}"><p15:threadingInfo timeZoneBias="0"><p15:parentCm authorId="$pa" idx="$pi"/></p15:threadingInfo></p:ext></p:extLst>""" }.orEmpty()
                """<p:cm authorId="${a.id}" dt="${esc(c.date ?: java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).toString())}" idx="${a.last}"><p:pos x="${Math.round(sc.x * 8)}" y="${Math.round(sc.y * 8)}"/><p:text>${esc(c.text)}</p:text>$parent</p:cm>"""
            }
            val path = reuse ?: "ppt/comments/comment${nextComments++}.xml".also { typesOut = typesOut.replace("</Types>", """<Override PartName="/$it" ContentType="application/vnd.openxmlformats-officedocument.presentationml.comments+xml"/></Types>""") }
            out[path] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:cmLst xmlns:p="$P" xmlns:p15="http://schemas.microsoft.com/office/powerpoint/2012/main">$body</p:cmLst>""".toByteArray()
            rel(relsXml, "comments", "../comments/" + path.substringAfterLast('/'))
        }
        var nextNotes = (zip.entries().asSequence().mapNotNull { Regex("""^ppt/notesSlides/notesSlide(\d+)\.xml$""").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
        /** Slide [part]'s notes as [text]: written into its notes page ([relsXml] names it), else a new notes page (true: [relsXml] changed). */
        fun notes(part: String, relsXml: org.w3c.dom.Document, text: String): Boolean {
            relsXml.documentElement.kids().firstOrNull { it.getAttribute("Type").endsWith("/notesSlide") }?.let { r ->
                val path = "ppt/notesSlides/" + r.getAttribute("Target").substringAfterLast('/')
                out[path] = notesXml(read(path), text); return false
            }
            if (text.isBlank()) return false
            val path = "ppt/notesSlides/notesSlide${nextNotes++}.xml"
            val nRels = OfficeFormats.parse(emptyRels.toByteArray())
            rel(nRels, "notesMaster", "../notesMasters/" + notesMaster.substringAfterLast('/'))
            rel(nRels, "slide", "../slides/" + part.substringAfterLast('/'))
            out[path] = notesXml(null, text); out[relsPath(path)] = xml(nRels)
            typesOut = typesOut.replace("</Types>", """<Override PartName="/$path" ContentType="application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml"/></Types>""")
            rel(relsXml, "notesSlide", "../notesSlides/" + path.substringAfterLast('/'))
            return true
        }
        fun list(part: String) {
            val rid = "rId${nextRel++}"
            rels.documentElement.appendChild(rels.createElementNS(rels.documentElement.namespaceURI, "Relationship").apply {
                setAttribute("Id", rid); setAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide"); setAttribute("Target", "slides/" + part.substringAfterLast('/'))
            })
            list.appendChild(pres.createElementNS(P, "p:sldId").apply { setAttribute("id", "${nextId++}"); setAttributeNS(R, "r:id", rid) })
            typesOut = typesOut.replace("</Types>", """<Override PartName="/$part" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/></Types>""")
        }
        // each slide's part, known before any is written (a link may jump to one further on): its own the first time it is in the order, else a new one
        val partOf = HashMap<Int, String>().also { m -> val seen = HashSet<Int>(); var n = nextSlide
            for (e in edit.order) m[e.uid] = if (e.layout < 0 && seen.add(e.src)) deck.slides[e.src].part else "ppt/slides/slide${n++}.xml" }
        for (e in edit.order) {
            val texts = edit.texts.filterKeys { it.first == e.uid }.mapKeys { it.key.second }
            val looks = edit.looks.filterKeys { it.first == e.uid }.mapKeys { it.key.second }
            val transition = edit.transitions[e.uid]; val anims = edit.anims[e.uid]
            val frames = edit.frames.filterKeys { it.first == e.uid }.mapKeys { it.key.second } + grown(deck, edit, e)
            val removed = edit.removed.filter { it.first == e.uid }.map { it.second }.toSet()
            val hide = edit.hidden[e.uid]
            val added = edit.added[e.uid].orEmpty()
            val source = if (e.layout >= 0) null else deck.slides[e.src]
            val original = source != null && used.add(e.src)
            // the slide's part: its own (the first time it is in the order), else a new one (a duplicate, a new slide of a layout)
            val part = partOf.getValue(e.uid)
            val relsXml = if (original) relsDoc(part) else newRels(if (e.layout >= 0) deck.layouts[e.layout].part else null, source?.part)
            // Insert › Link: a web page (an outside relationship) or a slide (the slide's part), named by the words' hlinkClick
            val ids = media(e.uid, relsXml) + looks.mapNotNull { (id, l) -> l.link?.takeIf { it.isNotEmpty() }?.let { to ->
                if (to.startsWith("slide:")) to.removePrefix("slide:").toIntOrNull()?.let(partOf::get)?.let { p -> "link $id" to rel(relsXml, "slide", p.substringAfterLast('/')) + " slide" }
                else "link $id" to rel(relsXml, "hyperlink", to).also { rid -> relsXml.documentElement.kids().first { it.getAttribute("Id") == rid }.setAttribute("TargetMode", "External") }
            } }
            val base = if (e.layout >= 0) deck.layouts[e.layout].xml else read(source!!.part) ?: continue
            // Header & footer: the choices, the slide's number, its layout's date, footer and number boxes (their idx and size)
            val footers = edit.footers[e.uid]; val number = edit.order.indexOf(e) + 1
            val phs = if (footers == null) emptyList() else relsXml.documentElement.kids().firstOrNull { it.getAttribute("Type").endsWith("/slideLayout") }
                ?.let { read("ppt/slideLayouts/" + it.getAttribute("Target").substringAfterLast('/')) }?.let(OfficeFormats::parse)?.getElementsByTagNameNS(P, "ph")
                ?.let { l -> (0 until l.length).map { l.item(it) as Element }.filter { it.getAttribute("type") in setOf("dt", "ftr", "sldNum") }
                    .map { Triple(it.getAttribute("type"), it.getAttribute("idx").ifEmpty { null }, it.getAttribute("sz").ifEmpty { null }) } }.orEmpty()
            // its notes as typed; a copy's own notes page with the first's words (its relationships leave the first's out)
            val noted = (edit.notes[e.uid] ?: source?.notes?.takeIf { !original && it.isNotEmpty() })?.let { notes(part, relsXml, it) } ?: false
            // its comments as they are now; a copy gets its own copy of the first's
            val commented = (edit.comments[e.uid] ?: source?.comments?.takeIf { !original && it.isNotEmpty() })?.also { comments(part, relsXml, it, original) } != null
            if (original) {
                byPart[part]?.let { list.appendChild(it) }
                // written only when its words, its hiding or what is on it changed
                if (texts.isNotEmpty() || added.isNotEmpty() || looks.isNotEmpty() || frames.isNotEmpty() || removed.isNotEmpty() || transition != null || anims != null || (hide != null && hide != source!!.hidden) || footers != null)
                    out[part] = withTexts(base, texts, hide, added, ids, looks, transition, anims, frames, removed, footers, number, phs)
                if (ids.isNotEmpty() || noted || commented) out[relsPath(part)] = xml(relsXml)
            } else {
                out[part] = withTexts(base, texts, hide, added, ids, looks, transition, anims, frames, removed, footers, number, phs)
                out[relsPath(part)] = xml(relsXml)
                list(part)
            }
        }
        // a deleted slide: its part, relationships, notes and listing gone
        for ((i, s) in deck.slides.withIndex()) if (i !in used) {
            // (a deck's slide not in the order any more)
            byPart[s.part]?.getAttributeNS(R, "id")?.let { rid -> relOf[rid]?.let { rels.documentElement.removeChild(it) } }
            read(relsPath(s.part))?.let { r -> OfficeFormats.parse(r).documentElement.kids().firstOrNull { it.getAttribute("Type").endsWith("/notesSlide") }?.getAttribute("Target")?.let { t ->
                val notes = "ppt/notesSlides/" + t.substringAfterLast('/')
                out[notes] = null; out[relsPath(notes)] = null
                typesOut = typesOut.replace(Regex("""<Override PartName="/${Regex.escape(notes)}"[^>]*/>"""), "")
            } }
            out[s.part] = null; out[relsPath(s.part)] = null
            typesOut = typesOut.replace(Regex("""<Override PartName="/${Regex.escape(s.part)}"[^>]*/>"""), "")
        }
        if (authorsChanged) {
            val path = authorsPart ?: "ppt/commentAuthors.xml".also { p -> rel(rels, "commentAuthors", "commentAuthors.xml")
                typesOut = typesOut.replace("</Types>", """<Override PartName="/$p" ContentType="application/vnd.openxmlformats-officedocument.presentationml.commentAuthors+xml"/></Types>""") }
            fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
            out[path] = ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:cmAuthorLst xmlns:p="$P">""" + authors.joinToString("") {
                """<p:cmAuthor id="${it.id}" name="${esc(it.name)}" initials="${esc(it.initials)}" lastIdx="${it.last}" clrIdx="${it.clr}"/>""" } + "</p:cmAuthorLst>").toByteArray()
        }
        out[presPath] = xml(pres); out[presRels] = xml(rels)
        if (typesOut != types) out["[Content_Types].xml"] = typesOut.toByteArray()
        SheetSave.rezip(zip, target, out, stored = null)
        zip.close()
    }
}
