package com.kalotrapezis.drive

import org.w3c.dom.Element
import java.io.File

/**
 * Editing a .pptx (16 Oct, the plan's next step after the viewer): the slides' order — moved, duplicated, deleted —
 * hidden or shown, and the words of the slides' own text boxes; kept apart from the deck as read, so Undo is the edit
 * before, and saved into the file: only the slides and lists that changed are written, every other part as it was.
 */
internal object SlidesEdit {
    /** One slide in the edited deck: [src] the deck's slide it shows, [uid] its own (a duplicate is a new one); [layout]: a new slide of that layout. */
    data class Entry(val src: Int, val uid: Int, val layout: Int = -1)
    /** A picture ([image]: a copy on the phone, [ext] png/jpeg) or a shape ([prst], [fill]) put on a slide, where it is (points). */
    data class Added(val id: String, val x: Float, val y: Float, val w: Float, val h: Float, val image: String? = null, val ext: String = "png",
        val prst: String? = null, val fill: Int = 0xFF4472C4.toInt(),
        /** a table ([prst] "table"): its rows and columns, its cells' words row by row */ val rows: Int = 0, val cols: Int = 0, val cells: List<String> = emptyList())
    /** A shape's looks as chosen (asked 16 Oct): its fill, border and words' colour — [NONE] taken away, null as the file has it. */
    data class Look(val fill: Int? = null, val line: Int? = null, val text: Int? = null)
    const val NONE = 0
    data class Edit(val order: List<Entry>, val hidden: Map<Int, Boolean> = emptyMap(), val texts: Map<Pair<Int, String>, List<String>> = emptyMap(),
        val added: Map<Int, List<Added>> = emptyMap(),
        /** (slide uid, shape id) → its looks */ val looks: Map<Pair<Int, String>, Look> = emptyMap(),
        /** a slide's transition as chosen: its kind "" none, else PowerPoint's name (fade, push, wipe, cover, pull, cut), its side and length */ val transitions: Map<Int, SlideTransition> = emptyMap(),
        /** a slide's click animations as chosen, each its own click in their order: entrances and exits (appear, fade, fly) */ val anims: Map<Int, List<SlideEffect>> = emptyMap(),
        /** (slide uid, item id) → where the file's own item was moved or sized to: x, y, w, h (points) */ val frames: Map<Pair<Int, String>, List<Float>> = emptyMap(),
        /** (slide uid, item id): the file's own items deleted */ val removed: Set<Pair<Int, String>> = emptySet(),
        /** Design's theme chosen (null: the file's own) — shown and saved from a copy of the deck with it */ val theme: SlideTheme? = null) {
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
            is SlideItem.Picture -> SlideItem.Picture(x, y, w, h, item.rotation, item.bytes)
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
            val paras = lines.mapIndexed { i, line ->
                val p = old?.paras?.getOrNull(i) ?: old?.paras?.lastOrNull()
                val look = p?.runs?.firstOrNull { it.text.isNotBlank() } ?: p?.runs?.firstOrNull()
                // a hint's grey is not the words' colour: those take the theme's text colour
                val color = if (item.hint) 0xFF000000.toInt() else look?.color ?: 0xFF000000.toInt()
                val run = SlideRun(line, look?.size ?: 18f, look?.bold == true, look?.italic == true, look?.underline == true, color, look?.font)
                if (p == null) SlidePara(listOf(run), 'l', 0, null, null, 0f, 0f, 0f, 0f, 1f)
                else SlidePara(listOf(run), p.align, p.level, p.bullet, p.bulletColor, p.marL, p.indent, p.before, p.after, p.line)
            }
            SlideItem.Shape(item.x, item.y, item.w, item.h, item.rotation, item.shape, old?.let { SlideText(paras, it.anchor, it.insets, it.scale, it.wrap, it.shrink) }
                ?: SlideText(paras, 't', floatArrayOf(7.2f, 3.6f, 7.2f, 3.6f)), item.paths, item.flipH, item.flipV, item.gradient, item.id).also { it.anim = item.anim; it.grouped = item.grouped }
        }
        // what was put on it: pictures and shapes, over the rest
        val added = edit.added[e.uid].orEmpty().map { a ->
            (if (a.prst == "table") table(a, edit.looks[e.uid to a.id]) else if (a.image != null) SlideItem.Picture(a.x, a.y, a.w, a.h, 0f) { runCatching { File(a.image).readBytes() }.getOrNull() }
            else SlideItem.Shape(a.x, a.y, a.w, a.h, 0f, ShapePicture(a.prst ?: "rect", a.fill, darker(a.fill), 1.5f), null, id = a.id)).also { it.anim = a.id } // what it is chosen and animated by
        }
        val groups = s.items.filter { it.grouped && it.anim != null }.map { it.anim!! }.distinct().associateWith { box(s, it) }
        val all = (items.filter { it.anim == null || (e.uid to it.anim!!) !in edit.removed }.map { moved(it, it.anim?.let { id -> edit.frames[e.uid to id] }, if (it.grouped) groups[it.anim] else null) } + added).map { styled(it, it.anim?.let { id -> edit.looks[e.uid to id] }) }
        val transition = edit.transitions[e.uid]?.takeIf { it.kind.isNotEmpty() || it.after != null } ?: s.transition.takeIf { e.uid !in edit.transitions }
        val steps = edit.anims[e.uid]?.let(::group) ?: s.steps
        return Slide(all, s.background, s.notes, edit.hidden[e.uid] ?: s.hidden, s.part, transition, steps)
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
        val text = item.text?.let { t -> if (l.text == null) t else SlideText(t.paras.map { p -> SlidePara(p.runs.map { r -> SlideRun(r.text, r.size, r.bold, r.italic, r.underline, l.text, r.font) },
            p.align, p.level, p.bullet, p.bulletColor, p.marL, p.indent, p.before, p.after, p.line) }, t.anchor, t.insets, t.scale, t.wrap, t.shrink) }
        return SlideItem.Shape(item.x, item.y, item.w, item.h, item.rotation, shape, text, item.paths, item.flipH, item.flipV, item.gradient.takeIf { l.fill == null }, item.id, item.hint).also { it.anim = item.anim; it.grouped = item.grouped }
    }
    /** A table put on a slide, as PowerPoint's default style shows it: the first row in the accent, the others banded. */
    fun table(a: Added, look: Look? = null): SlideItem.Table {
        fun cell(r: Int, c: Int): Pair<Int?, SlideText?> {
            val head = r == 0
            val words = a.cells.getOrNull(r * a.cols + c).orEmpty()
            val run = SlideRun(words, 14f, head, false, false, look?.text ?: if (head) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), null)
            val fill = cellFill(look?.fill, r)
            return fill to SlideText(listOf(SlidePara(listOf(run), 'l', 0, null, null, 0f, 0f, 0f, 0f, 1f)), 't', floatArrayOf(7.2f, 3.6f, 7.2f, 3.6f))
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

    /** The edited deck, as it shows. */
    fun view(deck: Deck, edit: Edit) = Deck(deck.width, deck.height, edit.order.map { slide(deck, edit, it) })

    /** The words of a text box as lines, for editing: one a paragraph. */
    fun lines(item: SlideItem.Shape): List<String> = item.text?.paras?.map { p -> p.runs.joinToString("") { it.text }.replace("\n", " ") } ?: listOf("")

    private fun Element.kids(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun xml(doc: org.w3c.dom.Document): ByteArray = java.io.ByteArrayOutputStream().also { out ->
        javax.xml.transform.TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8"); setOutputProperty(javax.xml.transform.OutputKeys.STANDALONE, "yes")
        }.transform(javax.xml.transform.dom.DOMSource(doc), javax.xml.transform.stream.StreamResult(out))
    }.toByteArray()

    /** A slide's XML with [texts] (shape id → lines) in its shapes: each paragraph keeps its look, its words in its first run; [added] put on it ([rels]: each picture's relationship). */
    fun withTexts(bytes: ByteArray, texts: Map<String, List<String>>, hidden: Boolean?, added: List<Added> = emptyList(), rels: Map<String, String> = emptyMap(),
        looks: Map<String, Look> = emptyMap(), transition: SlideTransition? = null, anims: List<SlideEffect>? = null, frames: Map<String, List<Float>> = emptyMap(),
        removed: Set<String> = emptySet()): ByteArray {
        val doc = OfficeFormats.parse(bytes)
        val root = doc.documentElement
        val tree = root.getElementsByTagNameNS(P, "spTree").item(0) as Element?
        fun emu(pt: Float) = (pt * 12700).toLong()
        for (a in added) {
            val xfrm = """<a:xfrm><a:off x="${emu(a.x)}" y="${emu(a.y)}"/><a:ext cx="${emu(a.w)}" cy="${emu(a.h)}"/></a:xfrm>"""
            val ns = """xmlns:p="$P" xmlns:a="$A" xmlns:r="$R""""
            fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            val x = if (a.prst == "table") """<p:graphicFrame $ns><p:nvGraphicFramePr><p:cNvPr id="${a.id}" name="Table ${a.id}"/><p:cNvGraphicFramePr><a:graphicFrameLocks noGrp="1"/></p:cNvGraphicFramePr><p:nvPr/></p:nvGraphicFramePr><p:xfrm><a:off x="${emu(a.x)}" y="${emu(a.y)}"/><a:ext cx="${emu(a.w)}" cy="${emu(a.h)}"/></p:xfrm><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table"><a:tbl><a:tblPr firstRow="1" bandRow="1"><a:tableStyleId>{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}</a:tableStyleId></a:tblPr><a:tblGrid>""" +
                    (0 until a.cols).joinToString("") { """<a:gridCol w="${emu(a.w / a.cols)}"/>""" } + "</a:tblGrid>" +
                    (0 until a.rows).joinToString("") { r -> """<a:tr h="${emu(a.h / a.rows)}">""" + (0 until a.cols).joinToString("") { c ->
                        val look = looks[a.id]
                        fun solid(c: Int) = """<a:solidFill><a:srgbClr val="${"%06X".format(c and 0xFFFFFF)}"/></a:solidFill>"""
                        val rPr = """<a:rPr lang="el-GR" sz="1400"${if (r == 0) " b=\"1\"" else ""}>${look?.text?.let(::solid).orEmpty()}</a:rPr>"""
                        """<a:tc><a:txBody><a:bodyPr/><a:lstStyle/><a:p>""" + (a.cells.getOrNull(r * a.cols + c)?.takeIf { it.isNotEmpty() }?.let { """<a:r>$rPr<a:t>${esc(it)}</a:t></a:r>""" } ?: """<a:endParaRPr lang="el-GR" sz="1400"/>""") +
                            "</a:p></a:txBody><a:tcPr>${look?.fill?.takeIf { it != NONE }?.let { solid(cellFill(it, r)) }.orEmpty()}</a:tcPr></a:tc>"
                    } + "</a:tr>" } + "</a:tbl></a:graphicData></a:graphic></p:graphicFrame>"
                else if (a.image != null) """<p:pic $ns><p:nvPicPr><p:cNvPr id="${a.id}" name="Picture ${a.id}"/><p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="${rels[a.id]}"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr>$xfrm<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>"""
                else """<p:sp $ns><p:nvSpPr><p:cNvPr id="${a.id}" name="Shape ${a.id}"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>$xfrm<a:prstGeom prst="${a.prst ?: "rect"}"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="${"%06X".format(a.fill and 0xFFFFFF)}"/></a:solidFill><a:ln w="19050"><a:solidFill><a:srgbClr val="${"%06X".format(darker(a.fill) and 0xFFFFFF)}"/></a:solidFill></a:ln></p:spPr><p:txBody><a:bodyPr rtlCol="0" anchor="ctr"/><a:lstStyle/><a:p><a:pPr algn="ctr"/><a:endParaRPr lang="el-GR"/></a:p></p:txBody></p:sp>"""
            tree?.appendChild(doc.importNode(OfficeFormats.parse(x.toByteArray()).documentElement, true))
        }
        if (hidden != null) { if (hidden) root.setAttribute("show", "0") else root.removeAttribute("show") }
        val shapes = root.getElementsByTagNameNS(P, "sp").let { l -> (0 until l.length).map { l.item(it) as Element } }
        for ((id, lines) in texts) {
            val sp = shapes.firstOrNull { s -> s.getElementsByTagNameNS(P, "cNvPr").item(0)?.let { (it as Element).getAttribute("id") } == id } ?: continue
            val body = sp.kids().firstOrNull { it.localName == "txBody" } ?: continue
            val paras = body.kids().filter { it.localName == "p" }
            val template = paras.lastOrNull() ?: continue
            lines.forEachIndexed { i, line ->
                val p = paras.getOrNull(i) ?: (template.cloneNode(true) as Element).also { body.appendChild(it) }
                val runs = p.kids().filter { it.localName in setOf("r", "br", "fld") }
                val keep = runs.firstOrNull { it.localName == "r" }
                // the first run keeps its look and takes the line; the others go (a line break, a field, other runs)
                val r = keep ?: doc.createElementNS(A, "a:r").also { nr ->
                    p.kids().firstOrNull { it.localName == "endParaRPr" }?.let { e -> nr.appendChild(doc.createElementNS(A, "a:rPr").also { rp -> for (k in 0 until e.attributes.length) e.attributes.item(k).let { a -> rp.setAttribute(a.nodeName, a.nodeValue) } }) }
                    nr.appendChild(doc.createElementNS(A, "a:t"))
                    p.insertBefore(nr, p.kids().firstOrNull { it.localName == "endParaRPr" })
                }
                runs.filter { it !== r }.forEach { p.removeChild(it) }
                val t = r.kids().firstOrNull { it.localName == "t" } ?: doc.createElementNS(A, "a:t").also { r.appendChild(it) }
                t.textContent = line
            }
            paras.drop(lines.size).forEach { body.removeChild(it) }
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
            l.text?.let { c ->
                val body = sp.kids().firstOrNull { it.localName == "txBody" } ?: return@let
                body.getElementsByTagNameNS(A, "p").let { ps -> (0 until ps.length).map { ps.item(it) as Element } }.forEach { p ->
                    for (r in p.kids().filter { it.localName in setOf("r", "fld") }) {
                        val rPr = r.kids().firstOrNull { it.localName == "rPr" } ?: doc.createElementNS(A, "a:rPr").also { r.insertBefore(it, r.firstChild) }
                        refill(rPr, c, rPr.kids().map { it.localName }.filter { it != "ln" && it !in fills }.toSet())
                    }
                    p.kids().firstOrNull { it.localName == "endParaRPr" }?.let { refill(it, c, it.kids().map { k -> k.localName }.filter { k -> k != "ln" && k !in fills }.toSet()) }
                }
            }
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
                    val (preset, sub) = when (fx.kind) { "appear" -> 1 to 0; "fly" -> 2 to when (fx.from) { 'u' -> 1; 'r' -> 2; 'l' -> 8; else -> 4 }
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
     * The deck saved: [source] to [target] with the slides in their new order (a duplicate a new slide part, a deleted
     * slide's parts gone), hidden ones marked, edited words in their slides.
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
        /** The pictures put on slide [uid] into the file: each a media part and a relationship in [relsXml] (the slide's); their ids → rIds. */
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
        }
        fun relsDoc(part: String?) = (part?.let { read(relsPath(it)) } ?: """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>""".toByteArray()).let(OfficeFormats::parse)
        /** A new slide part's relationships: its layout's, or the duplicated slide's without its notes (they belong to the first). */
        fun newRels(layout: String?, from: String?): org.w3c.dom.Document {
            if (layout != null) return OfficeFormats.parse("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/${layout.substringAfterLast('/')}"/></Relationships>""".toByteArray())
            val d = relsDoc(from)
            d.documentElement.kids().filter { it.getAttribute("Type").endsWith("/notesSlide") }.forEach { d.documentElement.removeChild(it) }
            return d
        }
        fun list(part: String) {
            val rid = "rId${nextRel++}"
            rels.documentElement.appendChild(rels.createElementNS(rels.documentElement.namespaceURI, "Relationship").apply {
                setAttribute("Id", rid); setAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide"); setAttribute("Target", "slides/" + part.substringAfterLast('/'))
            })
            list.appendChild(pres.createElementNS(P, "p:sldId").apply { setAttribute("id", "${nextId++}"); setAttributeNS(R, "r:id", rid) })
            typesOut = typesOut.replace("</Types>", """<Override PartName="/$part" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/></Types>""")
        }
        for (e in edit.order) {
            val texts = edit.texts.filterKeys { it.first == e.uid }.mapKeys { it.key.second }
            val looks = edit.looks.filterKeys { it.first == e.uid }.mapKeys { it.key.second }
            val transition = edit.transitions[e.uid]; val anims = edit.anims[e.uid]
            val frames = edit.frames.filterKeys { it.first == e.uid }.mapKeys { it.key.second }
            val removed = edit.removed.filter { it.first == e.uid }.map { it.second }.toSet()
            val hide = edit.hidden[e.uid]
            val added = edit.added[e.uid].orEmpty()
            val source = if (e.layout >= 0) null else deck.slides[e.src]
            val original = source != null && used.add(e.src)
            // the slide's part: its own (the first time it is in the order), else a new one (a duplicate, a new slide of a layout)
            val part = if (original) source!!.part else "ppt/slides/slide${nextSlide++}.xml"
            val relsXml = if (original) relsDoc(part) else newRels(if (e.layout >= 0) deck.layouts[e.layout].part else null, source?.part)
            val ids = media(e.uid, relsXml)
            val base = if (e.layout >= 0) deck.layouts[e.layout].xml else read(source!!.part) ?: continue
            if (original) {
                byPart[part]?.let { list.appendChild(it) }
                // written only when its words, its hiding or what is on it changed
                if (texts.isNotEmpty() || added.isNotEmpty() || looks.isNotEmpty() || frames.isNotEmpty() || removed.isNotEmpty() || transition != null || anims != null || (hide != null && hide != source!!.hidden))
                    out[part] = withTexts(base, texts, hide, added, ids, looks, transition, anims, frames, removed)
                if (ids.isNotEmpty()) out[relsPath(part)] = xml(relsXml)
            } else {
                out[part] = withTexts(base, texts, hide, added, ids, looks, transition, anims, frames, removed)
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
        out[presPath] = xml(pres); out[presRels] = xml(rels)
        if (typesOut != types) out["[Content_Types].xml"] = typesOut.toByteArray()
        SheetSave.rezip(zip, target, out, stored = null)
        zip.close()
    }
}
