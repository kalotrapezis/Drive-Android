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
        val prst: String? = null, val fill: Int = 0xFF4472C4.toInt())
    data class Edit(val order: List<Entry>, val hidden: Map<Int, Boolean> = emptyMap(), val texts: Map<Pair<Int, String>, List<String>> = emptyMap(),
        val added: Map<Int, List<Added>> = emptyMap()) {
        fun next() = (order.maxOfOrNull { it.uid } ?: -1) + 1
        /** A new shape id on slide [uid]: past the slide's own (theirs are small numbers) and those added. */
        fun newId(uid: Int) = "${9000 + (added[uid]?.size ?: 0) + added.values.sumOf { it.size }}"
    }
    fun start(deck: Deck) = Edit(deck.slides.indices.map { Entry(it, it) })

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
                ?: SlideText(paras, 't', floatArrayOf(7.2f, 3.6f, 7.2f, 3.6f)), item.paths, item.flipH, item.flipV, item.gradient, item.id)
        }
        // what was put on it: pictures and shapes, over the rest
        val added = edit.added[e.uid].orEmpty().map { a ->
            if (a.image != null) SlideItem.Picture(a.x, a.y, a.w, a.h, 0f) { runCatching { File(a.image).readBytes() }.getOrNull() }.also { pictureIds[it] = a.id }
            else SlideItem.Shape(a.x, a.y, a.w, a.h, 0f, ShapePicture(a.prst ?: "rect", a.fill, darker(a.fill), 1.5f), null, id = a.id)
        }
        return Slide(items + added, s.background, s.notes, edit.hidden[e.uid] ?: s.hidden, s.part)
    }
    /** A picture put on a slide → its id (pictures have none of their own here). */
    val pictureIds = java.util.WeakHashMap<SlideItem, String>()
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
    fun withTexts(bytes: ByteArray, texts: Map<String, List<String>>, hidden: Boolean?, added: List<Added> = emptyList(), rels: Map<String, String> = emptyMap()): ByteArray {
        val doc = OfficeFormats.parse(bytes)
        val root = doc.documentElement
        val tree = root.getElementsByTagNameNS(P, "spTree").item(0) as Element?
        fun emu(pt: Float) = (pt * 12700).toLong()
        for (a in added) {
            val xfrm = """<a:xfrm><a:off x="${emu(a.x)}" y="${emu(a.y)}"/><a:ext cx="${emu(a.w)}" cy="${emu(a.h)}"/></a:xfrm>"""
            val ns = """xmlns:p="$P" xmlns:a="$A" xmlns:r="$R""""
            val x = if (a.image != null) """<p:pic $ns><p:nvPicPr><p:cNvPr id="${a.id}" name="Picture ${a.id}"/><p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="${rels[a.id]}"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr>$xfrm<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>"""
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
        var nextMedia = (zip.entries().asSequence().mapNotNull { Regex("""^ppt/media/tetra(\d+)\.""").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
        /** The pictures put on slide [uid] into the file: each a media part and a relationship in [relsXml] (the slide's); their ids → rIds. */
        fun media(uid: Int, relsXml: org.w3c.dom.Document): Map<String, String> = edit.added[uid].orEmpty().filter { it.image != null }.associate { a ->
            val ext = if (a.ext == "jpeg" || a.ext == "jpg") "jpeg" else "png"
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
                if (texts.isNotEmpty() || added.isNotEmpty() || (hide != null && hide != source!!.hidden)) out[part] = withTexts(base, texts, hide, added, ids)
                if (ids.isNotEmpty()) out[relsPath(part)] = xml(relsXml)
            } else {
                out[part] = withTexts(base, texts, hide, added, ids)
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
