package com.kalotrapezis.drive

import org.w3c.dom.Element
import java.io.File
import java.util.Locale

/**
 * Editing a LibreOffice deck (.odp), as [SlidesEdit] does a .pptx (asked 16 Oct: the three apps alike): the pages in
 * their new order (a duplicate a copy of its page, a deleted one gone), hidden or shown, words typed, the page's own
 * items moved, sized, coloured or deleted, pictures and shapes put on — written into content.xml (and the pictures with
 * the manifest), speaker notes in the page's notes, every other part as it was. Items are found by their place on the page ([SlidesOdp]'s "o3", "o3.1").
 */
internal object SlidesOdpEdit {
    private const val OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    private const val DRAW = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    private const val SVG = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
    private const val PRES = "urn:oasis:names:tc:opendocument:xmlns:presentation:1.0"
    private const val TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private const val STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    private const val FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    private const val XLINK = "http://www.w3.org/1999/xlink"
    private const val XML = "http://www.w3.org/XML/1998/namespace"
    private const val MANIFEST = "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0"
    private const val TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    private const val ANIM = "urn:oasis:names:tc:opendocument:xmlns:animation:1.0"
    private const val SMIL = "urn:oasis:names:tc:opendocument:xmlns:smil-compatible:1.0"
    private const val LOEXT = "urn:org:documentfoundation:names:experimental:office:xmlns:loext:1.0"

    private fun Element.kids(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private fun Element.a(ns: String, n: String): String? = getAttributeNS(ns, n).takeIf { it.isNotEmpty() }
    private fun Element.all(): List<Element> = kids().flatMap { listOf(it) + it.all() }
    private fun cm(pt: Float) = String.format(Locale.ROOT, "%.4fcm", pt / 72f * 2.54f)
    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
    private fun darker(c: Int): Int {
        fun ch(s: Int) = (((c shr s) and 255) * 0.7f).toInt() shl s
        return (c and 0xFF000000.toInt()) or ch(16) or ch(8) or ch(0)
    }
    private fun xml(doc: org.w3c.dom.Document): ByteArray = SlidesEdit.domXml(doc)

    fun save(source: File, deck: Deck, edit: SlidesEdit.Edit, target: File) {
        val zip = java.util.zip.ZipFile(source)
        fun read(n: String) = zip.getEntry(n)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
        val out = HashMap<String, ByteArray?>()
        val doc = OfficeFormats.parse(read("content.xml") ?: error("Not a presentation."))
        val root = doc.documentElement
        val pres = root.getElementsByTagNameNS(OFFICE, "presentation").item(0) as Element? ?: error("Not a presentation.")
        val pages = pres.kids().filter { it.localName == "page" && it.namespaceURI == DRAW }
        val autos = root.kids().firstOrNull { it.localName == "automatic-styles" }
            ?: doc.createElementNS(OFFICE, "office:automatic-styles").also { a -> root.insertBefore(a, root.kids().firstOrNull { it.localName == "body" }) }
        val names = root.all().flatMap { listOfNotNull(it.a(STYLE, "name"), it.a(DRAW, "name"), it.a(XML, "id"), it.a(DRAW, "id")) }.toMutableSet()
        fun fresh(prefix: String) = generateSequence(1) { it + 1 }.map { "$prefix$it" }.first { it !in names }.also { names += it }

        // a style's properties in the schema's order
        val order = listOf("drawing-page-properties", "graphic-properties", "paragraph-properties", "text-properties")
        fun props(st: Element, kind: String): Element = st.kids().firstOrNull { it.localName == kind } ?: doc.createElementNS(STYLE, "style:$kind").also { p ->
            st.insertBefore(p, st.kids().firstOrNull { order.indexOf(it.localName) > order.indexOf(kind) }) }
        val made = HashMap<String, String>()
        /** A new automatic style of [family] for an element styled [name]: a copy of it when it is content.xml's own, else one on it; [key] the change, made once. */
        fun derive(family: String, name: String?, key: String, change: (Element) -> Unit): String = made.getOrPut("$family|$name|$key") {
            val old = name?.let { nm -> autos.kids().firstOrNull { it.localName == "style" && it.a(STYLE, "name") == nm && it.a(STYLE, "family") == family } }
            val st = old?.cloneNode(true) as Element? ?: doc.createElementNS(STYLE, "style:style").also { s ->
                s.setAttributeNS(STYLE, "style:family", family); if (name != null) s.setAttributeNS(STYLE, "style:parent-style-name", name) }
            val nm = fresh(when (family) { "drawing-page" -> "dpT"; "presentation" -> "prT"; "text" -> "TT"; else -> "grT" })
            st.setAttributeNS(STYLE, "style:name", nm); change(st); autos.appendChild(st); nm
        }

        /** The words' holder of a frame (its text box) or shape (itself). */
        fun holder(el: Element) = if (el.localName == "frame") el.kids().firstOrNull { it.localName == "text-box" } else el
        fun paras(h: Element) = h.all().filter { it.namespaceURI == TEXT && (it.localName == "p" || it.localName == "h") }
        fun words(p: Element, line: String) {
            val span = p.all().firstOrNull { it.localName == "span" && it.namespaceURI == TEXT }?.a(TEXT, "style-name")
            while (p.firstChild != null) p.removeChild(p.firstChild)
            if (line.isEmpty()) return
            p.appendChild(if (span != null) doc.createElementNS(TEXT, "text:span").also { it.setAttributeNS(TEXT, "text:style-name", span); it.textContent = line } else doc.createTextNode(line))
        }
        fun type(el: Element, lines: List<String>) {
            val h = holder(el) ?: return
            var ps = paras(h)
            if (ps.isEmpty()) {
                val p = doc.createElementNS(TEXT, "text:p")
                // an empty text placeholder: its lines a list, as LibreOffice's bullets are
                val unit = if (el.a(PRES, "class") == "outline") doc.createElementNS(TEXT, "text:list").also { l -> l.appendChild(doc.createElementNS(TEXT, "text:list-item").also { it.appendChild(p) }) } else p
                h.insertBefore(unit, h.kids().firstOrNull { it.localName == "enhanced-geometry" }); ps = paras(h)
            }
            lines.forEachIndexed { i, line ->
                val p = ps.getOrNull(i) ?: run {
                    // another paragraph: a copy of the last (in a list, its list item)
                    val last = ps.last(); val item = (last.parentNode as? Element)?.takeIf { it.localName == "list-item" }
                    val unit = item ?: last
                    val copy = unit.cloneNode(true) as Element
                    unit.parentNode.insertBefore(copy, unit.nextSibling)
                    (if (item != null) paras(copy).first() else copy).also { ps = ps + it }
                }
                words(p, line)
            }
            for (p in ps.drop(lines.size)) {
                val item = (p.parentNode as? Element)?.takeIf { it.localName == "list-item" && paras(it).size == 1 }
                val gone = item ?: p
                val list = (gone.parentNode as? Element)
                list?.removeChild(gone)
                if (list != null && list.localName == "list" && list.kids().none { it.localName == "list-item" }) list.parentNode.removeChild(list)
            }
            if (lines.any { it.isNotBlank() }) el.removeAttributeNS(PRES, "placeholder")
        }
        fun restyle(el: Element, l: SlidesEdit.Look) {
            val shapes = if (el.localName == "g") el.all().filter { it.namespaceURI == DRAW && it.localName in setOf("frame", "custom-shape", "rect", "ellipse") } else listOf(el)
            for (s in shapes) {
                if (l.fill != null || l.line != null) {
                    val pr = s.a(PRES, "style-name")
                    val nm = derive(if (pr != null) "presentation" else "graphic", pr ?: s.a(DRAW, "style-name"), "fill ${l.fill} line ${l.line}") { st ->
                        val g = props(st, "graphic-properties")
                        l.fill?.let { g.removeAttributeNS(DRAW, "fill-gradient-name"); if (it == SlidesEdit.NONE) g.setAttributeNS(DRAW, "draw:fill", "none") else { g.setAttributeNS(DRAW, "draw:fill", "solid"); g.setAttributeNS(DRAW, "draw:fill-color", hex(it)) } }
                        l.line?.let { if (it == SlidesEdit.NONE) g.setAttributeNS(DRAW, "draw:stroke", "none") else { g.setAttributeNS(DRAW, "draw:stroke", "solid"); g.setAttributeNS(SVG, "svg:stroke-color", hex(it)) } }
                    }
                    if (pr != null) s.setAttributeNS(PRES, "presentation:style-name", nm) else s.setAttributeNS(DRAW, "draw:style-name", nm)
                    // LibreOffice keeps a copy of the fill in the shape's text style (loext:graphic-properties), and paints that one
                    val ts = s.a(DRAW, "text-style-name")
                    val copy = ts?.let { n -> autos.kids().firstOrNull { it.a(STYLE, "name") == n && it.a(STYLE, "family") == "paragraph" } }?.kids()?.firstOrNull { it.localName == "graphic-properties" && it.namespaceURI == LOEXT }
                    if (l.fill != null && copy != null) s.setAttributeNS(DRAW, "draw:text-style-name", derive("paragraph", ts, "fill ${l.fill}") { st ->
                        val g = st.kids().first { it.localName == "graphic-properties" && it.namespaceURI == LOEXT }
                        g.removeAttributeNS(DRAW, "fill-gradient-name")
                        if (l.fill == SlidesEdit.NONE) g.setAttributeNS(DRAW, "draw:fill", "none") else { g.setAttributeNS(DRAW, "draw:fill", "solid"); g.setAttributeNS(DRAW, "draw:fill-color", hex(l.fill)) }
                    })
                }
                val c = l.text ?: continue
                for (p in holder(s)?.let(::paras).orEmpty()) {
                    // each span in the colour, and words outside spans put in one
                    for (span in p.all().filter { it.localName == "span" && it.namespaceURI == TEXT })
                        span.setAttributeNS(TEXT, "text:style-name", derive("text", span.a(TEXT, "style-name"), "color $c") { props(it, "text-properties").setAttributeNS(FO, "fo:color", hex(c)) })
                    for (t in (0 until p.childNodes.length).map { p.childNodes.item(it) }.filterIsInstance<org.w3c.dom.Text>()) {
                        val span = doc.createElementNS(TEXT, "text:span")
                        span.setAttributeNS(TEXT, "text:style-name", derive("text", null, "color $c") { props(it, "text-properties").setAttributeNS(FO, "fo:color", hex(c)) })
                        p.replaceChild(span, t); span.appendChild(t)
                    }
                }
            }
        }
        /** [el] (or, a group, each item in it as the group's [box] moves to [f]) at its new place. */
        fun place(el: Element, f: List<Float>, box: SlideItem?) {
            fun len(e: Element, n: String) = odfLength(e.a(SVG, n))
            val items = if (el.localName == "g") el.all().filter { it.a(SVG, "x") != null && it.a(SVG, "width") != null } else listOf(el)
            for (e in items) {
                // shortcut: a turned item keeps its place (its place is in its draw:transform), until turning is edited here
                if (e.a(DRAW, "transform") != null) continue
                val x = len(e, "x") ?: 0f; val y = len(e, "y") ?: 0f; val w = len(e, "width") ?: 0f; val h = len(e, "height") ?: 0f
                val n = if (box == null || el.localName != "g") f else {
                    val kx = if (box.w > 0) f[2] / box.w else 1f; val ky = if (box.h > 0) f[3] / box.h else 1f
                    listOf(f[0] + (x - box.x) * kx, f[1] + (y - box.y) * ky, w * kx, h * ky)
                }
                e.setAttributeNS(SVG, "svg:x", cm(n[0])); e.setAttributeNS(SVG, "svg:y", cm(n[1])); e.setAttributeNS(SVG, "svg:width", cm(n[2])); e.setAttributeNS(SVG, "svg:height", cm(n[3]))
            }
        }

        // pictures put on: each a part of its own, listed in the manifest
        val manifest = read("META-INF/manifest.xml")?.let(OfficeFormats::parse)
        var nextPicture = (zip.entries().asSequence().mapNotNull { Regex("""^Pictures/tetra(\d+)\.""").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
        fun picture(a: SlidesEdit.Added): String {
            val ext = when (a.ext) { "jpeg", "jpg" -> "jpg"; "gif" -> "gif"; else -> "png" }
            val path = "Pictures/tetra${nextPicture++}.$ext"
            out[path] = File(a.image!!).readBytes()
            manifest?.documentElement?.appendChild(manifest.createElementNS(MANIFEST, "manifest:file-entry").apply {
                setAttributeNS(MANIFEST, "manifest:full-path", path); setAttributeNS(MANIFEST, "manifest:media-type", if (ext == "jpg") "image/jpeg" else "image/$ext") })
            return path
        }
        val ns = """xmlns:draw="$DRAW" xmlns:svg="$SVG" xmlns:text="$TEXT" xmlns:xlink="$XLINK""""
        fun added(a: SlidesEdit.Added, look: SlidesEdit.Look?): Element? {
            val box = """svg:x="${cm(a.x)}" svg:y="${cm(a.y)}" svg:width="${cm(a.w)}" svg:height="${cm(a.h)}""""
            val x = if (a.image != null) {
                val st = derive("graphic", null, "picture") { st -> props(st, "graphic-properties").apply { setAttributeNS(DRAW, "draw:stroke", "none"); setAttributeNS(DRAW, "draw:fill", "none") } }
                """<draw:frame $ns draw:style-name="$st" draw:layer="layout" $box><draw:image xlink:href="${picture(a)}" xlink:type="simple" xlink:show="embed" xlink:actuate="onLoad"><text:p/></draw:image></draw:frame>"""
            } else if (a.prst != null && a.prst != "table") {
                val fill = look?.fill ?: a.fill; val line = look?.line ?: darker(a.fill)
                val st = derive("graphic", null, "shape $fill $line") { st -> props(st, "graphic-properties").apply {
                    if (fill == SlidesEdit.NONE) setAttributeNS(DRAW, "draw:fill", "none") else { setAttributeNS(DRAW, "draw:fill", "solid"); setAttributeNS(DRAW, "draw:fill-color", hex(fill)) }
                    if (line == SlidesEdit.NONE) setAttributeNS(DRAW, "draw:stroke", "none") else { setAttributeNS(DRAW, "draw:stroke", "solid"); setAttributeNS(SVG, "svg:stroke-color", hex(line)); setAttributeNS(SVG, "svg:stroke-width", "0.053cm") }
                } }
                val geometry = ShapeGeometry.odfGeometry(a.prst, a.w, a.h) ?: """<draw:enhanced-geometry svg:viewBox="0 0 21600 21600" draw:type="${ShapePicture.odfType(a.prst)}"/>"""
                """<draw:custom-shape $ns draw:style-name="$st" draw:layer="layout" $box><text:p/>$geometry</draw:custom-shape>"""
            } else if (a.prst == "table") {
                // PowerPoint's default look, as LibreOffice keeps it: the first row in the accent, the others banded, white lines
                val co = derive("table-column", null, "col ${a.w / a.cols}") { props(it, "table-column-properties").setAttributeNS(STYLE, "style:column-width", cm(a.w / a.cols)) }
                val ro = derive("table-row", null, "row ${a.h / a.rows}") { props(it, "table-row-properties").setAttributeNS(STYLE, "style:row-height", cm(a.h / a.rows)) }
                fun cell(r: Int) = SlidesEdit.cellFill(look?.fill, r).let { fill ->
                    derive("table-cell", null, "cell $fill") { st ->
                        st.appendChild(doc.createElementNS(LOEXT, "loext:graphic-properties").apply { setAttributeNS(DRAW, "draw:fill", "solid"); setAttributeNS(DRAW, "draw:fill-color", hex(fill))
                            setAttributeNS(FO, "fo:padding-top", "0.127cm"); setAttributeNS(FO, "fo:padding-bottom", "0.127cm"); setAttributeNS(FO, "fo:padding-left", "0.254cm"); setAttributeNS(FO, "fo:padding-right", "0.254cm") })
                        props(st, "paragraph-properties").setAttributeNS(FO, "fo:border", "0.48pt solid #ffffff")
                    } }
                fun words(head: Boolean) = derive("text", null, "cell words $head ${look?.text}") { st -> props(st, "text-properties").apply {
                    setAttributeNS(FO, "fo:font-size", "14pt"); setAttributeNS(FO, "fo:color", hex(look?.text ?: if (head) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())); if (head) setAttributeNS(FO, "fo:font-weight", "bold") } }
                fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                val st = derive("graphic", null, "table") { }
                """<draw:frame $ns xmlns:table="$TABLE" draw:style-name="$st" draw:layer="layout" $box><table:table><table:table-column table:style-name="$co" table:number-columns-repeated="${a.cols}"/>""" +
                    (0 until a.rows).joinToString("") { r -> """<table:table-row table:style-name="$ro" table:default-cell-style-name="${cell(r)}">""" + (0 until a.cols).joinToString("") { c ->
                        val t = a.cells.getOrNull(r * a.cols + c).orEmpty()
                        """<table:table-cell><text:p>${if (t.isEmpty()) "" else """<text:span text:style-name="${words(r == 0)}">${esc(t)}</text:span>"""}</text:p></table:table-cell>"""
                    } + "</table:table-row>" } + "</table:table></draw:frame>"
            } else return null
            return doc.importNode(OfficeFormats.parse(x.toByteArray()).documentElement, true) as Element
        }

        /** [el]'s id, what an animation names (one of its own made when it has none). */
        fun idOf(el: Element): String = el.a(XML, "id") ?: el.a(DRAW, "id") ?: fresh("idT").also { el.setAttributeNS(XML, "xml:id", it); el.setAttributeNS(DRAW, "draw:id", it) }
        val ans = """xmlns:anim="$ANIM" xmlns:smil="$SMIL" xmlns:presentation="$PRES""""
        fun secs(ms: Int) = String.format(Locale.ROOT, "%.3fs", ms / 1000f)
        /** [fx] on [target], in LibreOffice's presets, starting [node] (on-click, with-previous, after-previous). */
        fun effect(fx: SlideEffect, target: String, node: String): String {
            val kind = when (fx.kind) { "appear" -> if (fx.exit) "disappear" else "appear"; "fly" -> if (fx.exit) "fly-out" else "fly-in"; "wipe" -> "wipe"; "zoom" -> "zoom"
                "spin" -> "spin"; "grow" -> "grow-and-shrink"; "pulse" -> "flash-bulb"; else -> if (fx.exit) "fade-out" else "fade-in" }
            val cls = if (fx.emphasis) "emphasis" else if (fx.exit) "exit" else "entrance"
            val sub = when (fx.kind) { "fly", "wipe" -> """ presentation:preset-sub-type="${OdpEffects.subtype(fx.from)}""""; "zoom" -> """ presentation:preset-sub-type="${if (fx.exit) "out" else "in"}""""; else -> "" }
            val begin = if (fx.exit && fx.kind != "appear") secs(fx.ms - 1) else "0s"
            val set = if (fx.emphasis) "" else """<anim:set smil:begin="$begin" smil:dur="0.001s" smil:fill="hold" smil:targetElement="$target" smil:attributeName="visibility" smil:to="${if (fx.exit) "hidden" else "visible"}"/>"""
            fun transform(type: String, by: String, back: Boolean = false) =
                """<anim:animateTransform xmlns:svg="$SVG" smil:dur="${secs(if (back) (fx.ms / 2).coerceAtLeast(1) else fx.ms)}" smil:fill="hold"${if (back) " smil:autoReverse=\"true\"" else ""} smil:targetElement="$target" smil:by="$by" smil:attributeName="transform" svg:type="$type"/>"""
            val move = when (fx.kind) {
                // emphasis as LibreOffice writes PowerPoint's: a whole turn, half as big again, a little bigger and back
                "spin" -> transform("rotate", "360")
                "grow" -> transform("scale", "0.5,0.5")
                "pulse" -> transform("scale", "0.1,0.1", back = true)
                "appear" -> ""
                "fly" -> {
                    val edge = when (fx.from) { 'l' -> "x" to "0-width/2"; 'r' -> "x" to "1+width/2"; 'u' -> "y" to "0-height/2"; else -> "y" to "1+height/2" }
                    listOf("x", "y").joinToString("") { at ->
                        val there = if (at == edge.first) edge.second else at
                        """<anim:animate smil:dur="${secs(fx.ms)}" smil:fill="hold" smil:targetElement="$target" smil:attributeName="$at" smil:values="${if (fx.exit) "$at;$there" else "$there;$at"}" smil:keyTimes="0;1"/>"""
                    }
                }
                // LibreOffice's bar wipe: top to bottom (reversed: from the bottom), left to right (reversed: from the right)
                "wipe" -> """<anim:transitionFilter smil:dur="${secs(fx.ms)}" smil:targetElement="$target" smil:type="barWipe" smil:subtype="${if (fx.from == 'l' || fx.from == 'r') "leftToRight" else "topToBottom"}"${if (fx.from == 'd' || fx.from == 'r') " smil:direction=\"reverse\"" else ""}${if (fx.exit) " smil:mode=\"out\"" else ""}/>"""
                "zoom" -> listOf("width", "height").joinToString("") { at ->
                    """<anim:animate smil:dur="${secs(fx.ms)}" smil:fill="hold" smil:targetElement="$target" smil:attributeName="$at" smil:values="${if (fx.exit) "$at;0" else "0;$at"}" smil:keyTimes="0;1"/>"""
                }
                else -> """<anim:transitionFilter smil:dur="${secs(fx.ms)}" smil:targetElement="$target" smil:type="fade" smil:subtype="crossfade"${if (fx.exit) " smil:mode=\"out\"" else ""}/>"""
            }
            return """<anim:par smil:begin="0s" smil:fill="hold" presentation:node-type="$node" presentation:preset-class="$cls" presentation:preset-id="ooo-$cls-$kind"$sub>$set$move</anim:par>"""
        }
        /** The clicks: each a group (on a click, or by itself), its effects in pars by when they start. */
        fun clicks(list: List<SlideEffect>, ids: Map<String, String>): String = SlidesEdit.group(list.filter { it.target in ids }).joinToString("") { step ->
            var last: SlideEffect? = null
            """<anim:par smil:begin="${if (step.auto) "0s" else "next"}" smil:fill="hold">""" + step.effects.groupBy { it.delay }.toSortedMap().entries.joinToString("") { (delay, fxs) ->
                """<anim:par smil:begin="${secs(delay)}" smil:fill="hold">""" + fxs.joinToString("") { fx ->
                    val node = when { last == null -> if (step.auto) "after-previous" else "on-click"; fx.start == 'a' -> "after-previous"; else -> "with-previous" }
                    last = fx
                    effect(fx, ids.getValue(fx.target), node)
                } + "</anim:par>"
            } + "</anim:par>"
        }
        fun fragment(x: String) = doc.importNode(OfficeFormats.parse(x.toByteArray()).documentElement, true) as Element
        /** The page's timing root, made when it has none (after its shapes, before its notes). */
        fun timingRoot(page: Element): Element = page.kids().firstOrNull { it.namespaceURI == ANIM && it.a(PRES, "node-type") == "timing-root" }
            ?: fragment("""<anim:par $ans smil:dur="indefinite" smil:restart="never" presentation:node-type="timing-root"/>""").also { r -> page.insertBefore(r, page.kids().firstOrNull { it.localName == "notes" }) }

        /** Page [page] (showing [slide] as read, [uid] in the edit) with its edits. */
        fun apply(page: Element, slide: Slide, uid: Int) {
            fun at(id: String): Element? { var el: Element? = page; for (k in id.removePrefix("o").split('.')) el = el?.kids()?.getOrNull(k.toIntOrNull() ?: return null); return el }
            // found before anything moves: the places name the page as it was read
            val texts = edit.texts.filterKeys { it.first == uid }.mapNotNull { (k, v) -> at(k.second)?.let { it to v } }
            val looks = edit.looks.filterKeys { it.first == uid && it.second.startsWith("o") }.mapNotNull { (k, v) -> at(k.second)?.let { it to v } }
            val frames = edit.frames.filterKeys { it.first == uid }.mapNotNull { (k, v) -> at(k.second)?.let { Triple(it, v, SlidesEdit.box(slide, k.second)) } }
            val removed = edit.removed.filter { it.first == uid }.mapNotNull { at(it.second) }
            val anims = edit.anims[uid]?.mapNotNull { fx -> (if (fx.target.startsWith("o")) at(fx.target) else null).let { fx to it } }
            texts.forEach { (el, lines) -> type(el, lines) }
            looks.forEach { (el, l) -> restyle(el, l) }
            frames.forEach { (el, f, box) -> place(el, f, box) }
            removed.forEach { it.parentNode?.removeChild(it) }
            // put on: over the page's own, before its notes and animations
            val before = page.kids().firstOrNull { it.localName in setOf("notes", "par", "seq", "animations") }
            val put = HashMap<String, Element>()
            for (a in edit.added[uid].orEmpty()) added(a, edit.looks[uid to a.id])?.let { page.insertBefore(it, before); put[a.id] = it }
            // the transition: in the page's style and the timing's own filter, as LibreOffice writes it
            edit.transitions[uid]?.let { t ->
                val smil = t.kind.takeIf { it.isNotEmpty() }?.let { OdpEffects.smil(it, t.dir) }
                page.setAttributeNS(DRAW, "draw:style-name", derive("drawing-page", page.a(DRAW, "style-name"), "transition $t") { st ->
                    val d = props(st, "drawing-page-properties")
                    for (n in listOf("type", "subtype", "direction", "fadeColor")) d.removeAttributeNS(SMIL, n)
                    d.removeAttributeNS(PRES, "transition-speed"); d.removeAttributeNS(PRES, "transition-type"); d.removeAttributeNS(PRES, "duration")
                    // going on by itself after its time
                    t.after?.let { ms -> d.setAttributeNS(PRES, "presentation:transition-type", "automatic"); d.setAttributeNS(PRES, "presentation:duration", String.format(Locale.ROOT, "PT%.3fS", ms / 1000f)) }
                    if (smil != null) {
                        d.setAttributeNS(SMIL, "smil:type", smil.first); d.setAttributeNS(SMIL, "smil:subtype", smil.second)
                        if (smil.third) d.setAttributeNS(SMIL, "smil:direction", "reverse")
                        d.setAttributeNS(PRES, "presentation:transition-speed", when { t.ms <= 500 -> "fast"; t.ms >= 1000 -> "slow"; else -> "medium" })
                    }
                })
                val r = timingRoot(page)
                r.kids().filter { p -> p.kids().any { it.localName == "transitionFilter" && it.a(SMIL, "targetElement") == null } }.forEach { r.removeChild(it) }
                if (smil != null) r.insertBefore(fragment("""<anim:par $ans smil:begin="${idOf(page)}.begin"><anim:transitionFilter smil:dur="${secs(t.ms)}" smil:type="${smil.first}" smil:subtype="${smil.second}"${if (smil.third) " smil:direction=\"reverse\"" else ""}/></anim:par>"""), r.firstChild)
            }
            // the clicks: the main sequence written again (what is still on the page)
            // shortcut: rewrites the whole main sequence, so an effect of a kind not offered here comes back as its nearest (a pulse, a fade) when its animations are changed
            if (anims != null) {
                val r = timingRoot(page)
                r.kids().filter { it.localName == "seq" && it.a(PRES, "node-type") == "main-sequence" }.forEach { r.removeChild(it) }
                val ids = anims.mapNotNull { (fx, el) -> (el?.takeIf { it.parentNode != null } ?: put[fx.target])?.let { fx.target to idOf(it) } }.toMap()
                val xml = clicks(anims.map { it.first }, ids)
                if (xml.isNotEmpty()) r.appendChild(fragment("""<anim:seq $ans smil:dur="indefinite" presentation:node-type="main-sequence">$xml</anim:seq>"""))
            }
            page.kids().firstOrNull { it.namespaceURI == ANIM && it.a(PRES, "node-type") == "timing-root" }?.let { if (it.kids().isEmpty()) page.removeChild(it) }
            // the speaker notes as typed: the page's notes box's paragraphs (in the look of its first), a notes part made for a page without
            edit.notes[uid]?.let { text ->
                val notes = page.kids().firstOrNull { it.localName == "notes" && it.namespaceURI == PRES } ?: if (text.isBlank()) return@let else
                    // shortcut: LibreOffice's A4 notes page places (cm), whatever the deck's own notes master says; it moves them to its own on the first save there
                    fragment("""<presentation:notes xmlns:presentation="$PRES" xmlns:draw="$DRAW" xmlns:svg="$SVG"><draw:page-thumbnail draw:layer="layout" svg:width="17.271cm" svg:height="${String.format(Locale.ROOT, "%.3fcm", 17.271f * deck.height / deck.width.coerceAtLeast(1f))}" svg:x="1.865cm" svg:y="2.3cm" presentation:class="page"/>""" +
                        """<draw:frame draw:layer="layout" svg:width="17.271cm" svg:height="13cm" svg:x="1.865cm" svg:y="14.2cm" presentation:class="notes" presentation:placeholder="true"><draw:text-box/></draw:frame></presentation:notes>""").also { page.appendChild(it) }
                val frame = notes.kids().firstOrNull { it.a(PRES, "class") == "notes" } ?: fragment("""<draw:frame xmlns:presentation="$PRES" xmlns:draw="$DRAW" draw:layer="layout" presentation:class="notes"><draw:text-box/></draw:frame>""").also { notes.appendChild(it) }
                val box = frame.kids().firstOrNull { it.localName == "text-box" } ?: doc.createElementNS(DRAW, "draw:text-box").also { frame.appendChild(it) }
                frame.removeAttributeNS(PRES, "placeholder")
                val old = box.kids().filter { it.namespaceURI == TEXT && (it.localName == "p" || it.localName == "h" || it.localName == "list") }
                val pStyle = old.firstNotNullOfOrNull { o -> (listOf(o) + o.all()).firstOrNull { it.localName == "p" }?.a(TEXT, "style-name") }
                val sStyle = old.firstNotNullOfOrNull { o -> o.all().firstOrNull { it.localName == "span" }?.a(TEXT, "style-name") }
                old.forEach { box.removeChild(it) }
                for (line in text.split("\n")) box.appendChild(doc.createElementNS(TEXT, "text:p").also { p ->
                    pStyle?.let { p.setAttributeNS(TEXT, "text:style-name", it) }
                    if (line.isNotEmpty()) p.appendChild(if (sStyle == null) doc.createTextNode(line) else doc.createElementNS(TEXT, "text:span").also { sp -> sp.setAttributeNS(TEXT, "text:style-name", sStyle); sp.textContent = line })
                })
            }
            edit.hidden[uid]?.let { h -> if (h != slide.hidden)
                page.setAttributeNS(DRAW, "draw:style-name", derive("drawing-page", page.a(DRAW, "style-name"), "hidden $h") { props(it, "drawing-page-properties").setAttributeNS(PRES, "presentation:visibility", if (h) "hidden" else "visible") }) }
        }

        val used = HashSet<Int>()
        val ordered = ArrayList<Element>()
        for (e in edit.order) {
            // a new slide: its layout's page (empty boxes in the master's places)
            if (e.layout >= 0) {
                val l = deck.layouts.getOrNull(e.layout) ?: continue
                val page = fragment(String(l.xml)).also { it.setAttributeNS(DRAW, "draw:name", fresh("page")) }
                apply(page, l.preview, e.uid); ordered += page; continue
            }
            val orig = pages.getOrNull(e.src) ?: continue
            val page = if (used.add(e.src)) orig else (orig.cloneNode(true) as Element).also { copy ->
                // a copy: its own name, and ids of its own (its animations follow them)
                copy.setAttributeNS(DRAW, "draw:name", fresh("page"))
                val renamed = HashMap<String, String>()
                val els = listOf(copy) + copy.all()
                for (el in els) for ((n, a) in listOf(XML to "id", DRAW to "id")) el.a(n, a)?.let { old -> renamed.getOrPut(old) { fresh("${old}c") }.let { el.setAttributeNS(n, if (n == XML) "xml:id" else "draw:id", it) } }
                for (el in els) for (i in 0 until el.attributes.length) el.attributes.item(i).let { at ->
                    if (at.localName == "targetElement") renamed[at.nodeValue]?.let { at.nodeValue = it }
                    if (at.localName == "begin" && at.nodeValue.endsWith(".begin")) renamed[at.nodeValue.removeSuffix(".begin")]?.let { at.nodeValue = "$it.begin" }
                }
            }
            apply(page, deck.slides[e.src], e.uid)
            ordered += page
        }
        // the pages in their order where they were (before the presentation's settings)
        pages.forEach { pres.removeChild(it) }
        val after = pres.kids().firstOrNull { it.localName == "settings" }
        ordered.forEach { pres.insertBefore(it, after) }
        out["content.xml"] = xml(doc)
        if (manifest != null && out.keys.any { it.startsWith("Pictures/") }) out["META-INF/manifest.xml"] = xml(manifest)
        SheetSave.rezip(zip, target, out, stored = "mimetype")
        zip.close()
    }
}
