package com.kalotrapezis.drive

import org.w3c.dom.Element
import java.io.File

/**
 * Editing a .pptx (16 Oct, the plan's next step after the viewer): the slides' order — moved, duplicated, deleted —
 * hidden or shown, and the words of the slides' own text boxes; kept apart from the deck as read, so Undo is the edit
 * before, and saved into the file: only the slides and lists that changed are written, every other part as it was.
 */
internal object SlidesEdit {
    /** One slide in the edited deck: [src] the deck's slide it shows, [uid] its own (a duplicate is a new one). */
    data class Entry(val src: Int, val uid: Int)
    data class Edit(val order: List<Entry>, val hidden: Map<Int, Boolean> = emptyMap(), val texts: Map<Pair<Int, String>, List<String>> = emptyMap()) {
        fun next() = (order.maxOfOrNull { it.uid } ?: -1) + 1
    }
    fun start(deck: Deck) = Edit(deck.slides.indices.map { Entry(it, it) })

    /** The edited slide of [e]: its words as typed (each line a paragraph in the look of the one it replaces). */
    fun slide(deck: Deck, edit: Edit, e: Entry): Slide {
        val s = deck.slides[e.src]
        val items = s.items.map { item ->
            val lines = (item as? SlideItem.Shape)?.id?.let { edit.texts[e.uid to it] } ?: return@map item
            val old = item.text
            val paras = lines.mapIndexed { i, line ->
                val p = old?.paras?.getOrNull(i) ?: old?.paras?.lastOrNull()
                val look = p?.runs?.firstOrNull { it.text.isNotBlank() } ?: p?.runs?.firstOrNull()
                val run = SlideRun(line, look?.size ?: 18f, look?.bold == true, look?.italic == true, look?.underline == true, look?.color ?: 0xFF000000.toInt(), look?.font)
                if (p == null) SlidePara(listOf(run), 'l', 0, null, null, 0f, 0f, 0f, 0f, 1f)
                else SlidePara(listOf(run), p.align, p.level, p.bullet, p.bulletColor, p.marL, p.indent, p.before, p.after, p.line)
            }
            SlideItem.Shape(item.x, item.y, item.w, item.h, item.rotation, item.shape, old?.let { SlideText(paras, it.anchor, it.insets, it.scale, it.wrap, it.shrink) }
                ?: SlideText(paras, 't', floatArrayOf(7.2f, 3.6f, 7.2f, 3.6f)), item.paths, item.flipH, item.flipV, item.gradient, item.id)
        }
        return Slide(items, s.background, s.notes, edit.hidden[e.uid] ?: s.hidden, s.part)
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

    /** A slide's XML with [texts] (shape id → lines) in its shapes: each paragraph keeps its look, its words in its first run. */
    fun withTexts(bytes: ByteArray, texts: Map<String, List<String>>, hidden: Boolean?): ByteArray {
        val doc = OfficeFormats.parse(bytes)
        val root = doc.documentElement
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
        return xml(doc)
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
        for (e in edit.order) {
            val s = deck.slides[e.src]
            val texts = edit.texts.filterKeys { it.first == e.uid }.mapKeys { it.key.second }
            val hide = edit.hidden[e.uid]
            if (used.add(e.src)) {
                // the slide itself, where it was; its XML written only when its words or its hiding changed
                byPart[s.part]?.let { list.appendChild(it) }
                if (texts.isNotEmpty() || (hide != null && hide != s.hidden)) out[s.part] = withTexts(read(s.part) ?: continue, texts, hide)
            } else {
                // a duplicate: a new slide part (its own notes left out: they belong to the first), its relationships, its place in the list
                val part = "ppt/slides/slide${nextSlide++}.xml"
                out[part] = withTexts(read(s.part) ?: continue, texts, hide)
                read(relsPath(s.part))?.let { r ->
                    val d = OfficeFormats.parse(r)
                    d.documentElement.kids().filter { it.getAttribute("Type").endsWith("/notesSlide") }.forEach { d.documentElement.removeChild(it) }
                    out[relsPath(part)] = xml(d)
                }
                val rid = "rId${nextRel++}"
                rels.documentElement.appendChild(rels.createElementNS(rels.documentElement.namespaceURI, "Relationship").apply {
                    setAttribute("Id", rid); setAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide"); setAttribute("Target", "slides/" + part.substringAfterLast('/'))
                })
                list.appendChild(pres.createElementNS(P, "p:sldId").apply { setAttribute("id", "${nextId++}"); setAttributeNS(R, "r:id", rid) })
                typesOut = typesOut.replace("</Types>", """<Override PartName="/$part" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/></Types>""")
            }
        }
        // a deleted slide: its part, relationships, notes and listing gone
        for ((i, s) in deck.slides.withIndex()) if (i !in used) {
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
