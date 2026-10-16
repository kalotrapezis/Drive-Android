package com.kalotrapezis.drive

import org.w3c.dom.Element

/**
 * LibreOffice Impress's decks (.odp): each draw:page's frames (titles, outlines, text boxes, pictures, charts), custom
 * shapes and groups where they are (ODF keeps every shape's place on the page), their words with the looks of their
 * styles — the shape's presentation style and its master's parents, the paragraph's, the span's, the list's bullets —
 * the page's background, its notes; the master page's own shapes under each page.
 */
internal object SlidesOdp {
    private const val DRAW = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    private const val SVG = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
    private const val PRES = "urn:oasis:names:tc:opendocument:xmlns:presentation:1.0"
    private const val TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private const val STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    private const val XLINK = "http://www.w3.org/1999/xlink"

    private fun Element.kids(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private fun Element.a(ns: String, n: String): String? = getAttributeNS(ns, n).takeIf { it.isNotEmpty() }
    private fun Element.attrs(): Map<String, String> = (0 until attributes.length).associate { attributes.item(it).let { a -> (a.localName ?: a.nodeName) to a.nodeValue } }

    /** A length ("2.5cm", "12pt", "0.5in") in points. */
    private fun pt(v: String?): Float? {
        val m = Regex("""^(-?[0-9.]+)\s*(cm|mm|in|pt|pc|px)?$""").find(v?.trim() ?: return null) ?: return null
        val n = m.groupValues[1].toFloatOrNull() ?: return null
        return when (m.groupValues[2]) { "cm" -> n / 2.54f * 72; "mm" -> n / 25.4f * 72; "in" -> n * 72; "pc" -> n * 12; "px" -> n * 0.75f; else -> n }
    }
    /** An element as XML text (a custom shape's geometry, for [ShapeGeometry.ofOdf]). */
    private fun xml(e: Element): String = java.io.StringWriter().also { w ->
        javax.xml.transform.TransformerFactory.newInstance().newTransformer().apply { setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes") }
            .transform(javax.xml.transform.dom.DOMSource(e), javax.xml.transform.stream.StreamResult(w))
    }.toString()
    private fun hex(v: String?) = v?.takeIf { it.startsWith("#") && it.length == 7 }?.substring(1)?.toIntOrNull(16)?.or(0xFF000000.toInt())

    private class Style(val parent: String?, val props: Map<String, String>)
    private class Bullet(val char: String?, val before: Float, val label: Float, val color: Int?)

    fun read(file: java.io.File): Deck = java.util.zip.ZipFile(file).let { zip ->
        fun part(n: String): ByteArray? = zip.getEntry(n)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
        val content = OfficeFormats.parse(part("content.xml") ?: error("Not a presentation.")).documentElement
        val styles = part("styles.xml")?.let { OfficeFormats.parse(it).documentElement }
        // every style by family and name, its properties' attributes as "t." text, "p." paragraph, "g." graphic, "d." drawing page
        val all = HashMap<String, Style>(); val lists = HashMap<String, Map<Int, Bullet>>()
        fun collect(root: Element?) {
            root ?: return
            val l = root.getElementsByTagNameNS(STYLE, "style")
            for (i in 0 until l.length) {
                val s = l.item(i) as Element
                val props = HashMap<String, String>()
                for (k in s.kids()) {
                    val p = when (k.localName) { "text-properties" -> "t."; "paragraph-properties" -> "p."; "graphic-properties" -> "g."; "drawing-page-properties" -> "d."; else -> continue }
                    k.attrs().forEach { (n, v) -> props[p + n] = v }
                }
                all[s.a(STYLE, "family") + ":" + s.a(STYLE, "name")] = Style(s.a(STYLE, "parent-style-name"), props)
            }
            val ls = root.getElementsByTagNameNS(TEXT, "list-style")
            for (i in 0 until ls.length) {
                val s = ls.item(i) as Element
                lists[s.a(STYLE, "name") ?: continue] = s.kids().associate { lv ->
                    val lp = lv.kids().firstOrNull { it.localName == "list-level-properties" }
                    val tp = lv.kids().firstOrNull { it.localName == "text-properties" }
                    (lv.a(TEXT, "level")?.toIntOrNull() ?: 1) - 1 to Bullet(if (lv.localName == "list-level-style-bullet") lv.a(TEXT, "bullet-char") else if (lv.localName == "list-level-style-number") "1." else null,
                        pt(lp?.a(TEXT, "space-before")) ?: 0f, pt(lp?.a(TEXT, "min-label-width")) ?: 0f, hex(tp?.getAttributeNS("urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0", "color")))
                }
            }
        }
        collect(styles); collect(content)
        // LibreOffice's gradients by name: their two colours and angle (0: top to bottom, turning anticlockwise)
        val gradients = HashMap<String, SlideGradient>()
        for (root in listOfNotNull(styles, content)) root.getElementsByTagNameNS(DRAW, "gradient").let { l -> for (i in 0 until l.length) { val g = l.item(i) as Element
            val a = g.a(DRAW, "start-color")?.let(::hex) ?: continue; val b = g.a(DRAW, "end-color")?.let(::hex) ?: continue
            val deg = g.a(DRAW, "angle")?.let { v -> if (v.endsWith("deg")) v.removeSuffix("deg").toFloatOrNull() else v.toFloatOrNull()?.div(10f) } ?: 0f
            val axial = g.a(DRAW, "style") == "axial"
            gradients[g.a(DRAW, "name") ?: continue] = SlideGradient(if (axial) intArrayOf(b, a, b) else intArrayOf(a, b), if (axial) floatArrayOf(0f, 0.5f, 1f) else floatArrayOf(0f, 1f), 90f - deg)
        } }
        /** A style's property with its parents' under it. */
        fun prop(family: String, name: String?, key: String, depth: Int = 0): String? {
            val s = all["$family:${name ?: return null}"] ?: return null
            return s.props[key] ?: if (depth < 12) prop(family, s.parent, key, depth + 1) else null
        }
        // the page's size: the first page's master's page layout
        val masters = styles?.getElementsByTagNameNS(STYLE, "master-page")?.let { l -> (0 until l.length).map { l.item(it) as Element } }.orEmpty().associateBy { it.a(STYLE, "name") }
        val layouts = styles?.getElementsByTagNameNS(STYLE, "page-layout")?.let { l -> (0 until l.length).map { l.item(it) as Element } }.orEmpty()
            .associate { it.a(STYLE, "name") to it.kids().firstOrNull { k -> k.localName == "page-layout-properties" } }
        val pages = content.getElementsByTagNameNS(DRAW, "page").let { l -> (0 until l.length).map { l.item(it) as Element } }
        val firstMaster = masters[pages.firstOrNull()?.a(DRAW, "master-page-name")]
        val layout = layouts[firstMaster?.a(STYLE, "page-layout-name")]
        val fo = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
        val width = pt(layout?.getAttributeNS(fo, "page-width")) ?: 720f; val height = pt(layout?.getAttributeNS(fo, "page-height")) ?: 540f

        fun box(e: Element) = floatArrayOf(pt(e.a(SVG, "x")) ?: 0f, pt(e.a(SVG, "y")) ?: 0f, pt(e.a(SVG, "width")) ?: 0f, pt(e.a(SVG, "height")) ?: 0f)
        fun rotation(e: Element) = e.a(DRAW, "transform")?.let { Regex("""rotate\s*\(\s*(-?[0-9.eE]+)""").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }?.let { (-Math.toDegrees(it)).toFloat() } ?: 0f

        /** The words of a frame or shape: its paragraphs, a list's items a level each, with their looks. */
        fun text(holder: Element, presStyle: String?, graphicStyle: String?): SlideText? {
            val paras = ArrayList<SlidePara>()
            fun family(name: String?) = if (presStyle != null) "presentation" to presStyle else "graphic" to graphicStyle
            fun shapeProp(key: String): String? { val (f, n) = family(null); return prop(f, n, key) }
            fun para(p: Element, level: Int, list: String?) {
                val pStyle = p.a(TEXT, "style-name")
                // an outline's level n takes its master's outline n style (Title_and_Content-outline2…)
                val levelStyle = presStyle?.let { s -> prop("presentation", s, "__") ?: all["presentation:$s"]?.parent }?.let { base ->
                    if (level > 0 && Regex("""outline\d$""").containsMatchIn(base)) base.dropLast(1) + (level + 1) else base }
                fun textProp(span: String?, key: String) = prop("text", span, "t.$key") ?: prop("paragraph", pStyle, "t.$key")
                    ?: levelStyle?.let { prop("presentation", it, "t.$key") } ?: shapeProp("t.$key")
                val runs = ArrayList<SlideRun>()
                fun run(t: String, span: String?) {
                    val size = pt(textProp(span, "font-size")) ?: 18f
                    runs += SlideRun(t, size, textProp(span, "font-weight").let { it == "bold" || (it?.toIntOrNull() ?: 400) >= 600 }, textProp(span, "font-style") == "italic",
                        textProp(span, "text-underline-style").let { it != null && it != "none" }, hex(textProp(span, "color")) ?: 0xFF000000.toInt(),
                        textProp(span, "font-name") ?: textProp(span, "font-family"))
                }
                fun walk(n: org.w3c.dom.Node, span: String?) {
                    when {
                        n is org.w3c.dom.Text -> if (n.data.isNotEmpty()) run(n.data.replace('\n', ' '), span)
                        n is Element && n.localName == "span" -> for (i in 0 until n.childNodes.length) walk(n.childNodes.item(i), n.a(TEXT, "style-name") ?: span)
                        n is Element && n.localName == "s" -> run(" ".repeat(n.a(TEXT, "c")?.toIntOrNull() ?: 1), span)
                        n is Element && n.localName == "tab" -> run("\t", span)
                        n is Element && n.localName == "line-break" -> run("\n", span)
                        n is Element && n.localName in setOf("a", "page-number", "date", "time") -> for (i in 0 until n.childNodes.length) walk(n.childNodes.item(i), span)
                    }
                }
                for (i in 0 until p.childNodes.length) walk(p.childNodes.item(i), null)
                if (runs.isEmpty()) run("", null)
                val bullet = list?.let { lists[it]?.get(level) }
                fun pProp(key: String) = prop("paragraph", pStyle, "p.$key") ?: levelStyle?.let { prop("presentation", it, "p.$key") } ?: shapeProp("p.$key")
                val align = when (pProp("text-align")) { "center" -> 'c'; "end", "right" -> 'r'; "justify" -> 'j'; else -> 'l' }
                val marL = (bullet?.let { it.before + it.label } ?: 0f) + (pt(pProp("margin-left")) ?: 0f)
                SlidePara(runs, align, level, bullet?.char, bullet?.color, marL, -(bullet?.label ?: 0f) + (pt(pProp("text-indent")) ?: 0f),
                    pt(pProp("margin-top")) ?: 0f, pt(pProp("margin-bottom")) ?: 0f, pProp("line-height")?.removeSuffix("%")?.toFloatOrNull()?.div(100f) ?: 1f).let { paras += it }
            }
            // a list's items sit one level in each time a list is inside an item
            fun walkList(list: Element, level: Int, name: String?) {
                for (item in list.kids()) for (inner in item.kids()) when (inner.localName) {
                    "p", "h" -> para(inner, level, name)
                    "list" -> walkList(inner, level + 1, inner.a(TEXT, "style-name") ?: name)
                }
            }
            for (k in holder.kids()) when (k.localName) {
                "p", "h" -> para(k, 0, null)
                "list" -> walkList(k, 0, k.a(TEXT, "style-name"))
            }
            if (paras.none { p -> p.runs.any { it.text.isNotBlank() } }) return null
            fun g(key: String) = shapeProp("g.$key")
            val anchor = when (g("textarea-vertical-align")) { "middle" -> 'c'; "bottom" -> 'b'; else -> 't' }
            return SlideText(paras, anchor, floatArrayOf(pt(g("padding-left")) ?: 7.2f, pt(g("padding-top")) ?: 3.6f, pt(g("padding-right")) ?: 7.2f, pt(g("padding-bottom")) ?: 3.6f),
                shrink = g("shrink-to-fit") == "true" || g("fit-to-size") == "shrink-to-fit")
        }

        /**
         * The shapes under [parent]; a page's own named by their place on it ("o3": the page's fourth element, "o3.1" the
         * second in that group) — what editing finds them by again in the file ([top]: the group's, what moves with it).
         */
        fun shapes(parent: Element, out: MutableList<SlideItem>, master: Boolean, prefix: String? = null, top: String? = null) {
            for ((k, e) in parent.kids().withIndex()) runCatching {
                val path = if (master) null else prefix?.let { "$it.$k" } ?: "o$k"
                val before = out.size
                // a master's placeholders (title, outline, numbers): their words show only on pages that fill them
                if (master && (e.a(PRES, "class") != null || e.a(PRES, "placeholder") == "true")) return@runCatching
                val b = box(e); val rot = rotation(e)
                val pres = e.a(PRES, "style-name"); val gr = e.a(DRAW, "style-name")
                fun g(key: String) = if (pres != null) prop("presentation", pres, "g.$key") else prop("graphic", gr, "g.$key")
                fun fill() = if (g("fill") == "none") null else if (g("fill") == "solid" || g("fill-color") != null) hex(g("fill-color")) else
                    if (g("fill") == "gradient") hex(g("fill-color")) ?: 0xFF729FCF.toInt() else null // shortcut: a gradient as one colour
                fun line() = if (g("stroke") == "none") null else hex(g("stroke-color")) ?: if (g("stroke") == "solid") 0xFF3465A4.toInt() else null
                fun gradient() = if (g("fill") == "gradient") g("fill-gradient-name")?.let(gradients::get) else null
                when (e.localName) {
                    "frame" -> {
                        val inner = e.kids().firstOrNull() ?: return@runCatching
                        when (inner.localName) {
                            "text-box" -> {
                                val t = text(inner, pres, gr)
                                val f = fill(); val l = line()
                                val shape = if (f != null || l != null) ShapePicture("rect", f, l, pt(g("stroke-width"))?.coerceAtLeast(0.5f) ?: 0.75f) else null
                                if (t != null || shape != null) out += SlideItem.Shape(b[0], b[1], b[2], b[3], rot, shape, t, gradient = gradient(), id = path)
                            }
                            "image" -> inner.a(XLINK, "href")?.removePrefix("./")?.let { p -> out += SlideItem.Picture(b[0], b[1], b[2], b[3], rot) { part(p) } }
                            "object" -> inner.a(XLINK, "href")?.removePrefix("./")?.let { p -> part("$p/content.xml") }?.let(OfficeCharts::readOdf)?.let { out += SlideItem.Chart(b[0], b[1], b[2], b[3], it) }
                        }
                    }
                    "custom-shape", "rect", "ellipse" -> {
                        val geo = e.kids().firstOrNull { it.localName == "enhanced-geometry" }
                        val type = geo?.a(DRAW, "type")
                        val preset = if (e.localName == "ellipse") "ellipse" else if (e.localName == "rect") "rect" else type?.let { ShapePicture.presetOf(it) }
                        val mods = geo?.a(DRAW, "modifiers")?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toDoubleOrNull() }.orEmpty()
                        val adjust = preset?.let { ShapeGeometry.presets[it]?.adjustNames?.zip(mods)?.toMap() }.orEmpty()
                        val own = if (preset == null && geo != null) ShapeGeometry.ofOdf(xml(geo)) else null
                        val f = fill() ?: if (g("fill") == null) 0xFF729FCF.toInt() else null; val l = line()
                        val shape = if (preset != null || own != null) ShapePicture(preset ?: "custom", f, l, pt(g("stroke-width"))?.coerceAtLeast(0.5f) ?: 0.75f, own, adjust) else null
                        out += SlideItem.Shape(b[0], b[1], b[2], b[3], rot, shape, text(e, null, gr), gradient = gradient(), id = path)
                    }
                    "g" -> shapes(e, out, master, path, top ?: path)
                }
                if (path != null) for (i in before until out.size) { if (out[i].anim == null) out[i].anim = top ?: path; if (top != null) out[i].grouped = true }
            }.onFailure { WriterLog.w("Slides: an .odp shape could not be read", it) }
        }

        val slides = pages.map { page ->
            val items = ArrayList<SlideItem>()
            val master = masters[page.a(DRAW, "master-page-name")]
            val dp = page.a(DRAW, "style-name")
            if (prop("drawing-page", dp, "d.background-objects-visible") != "false") master?.let { shapes(it, items, true) }
            shapes(page, items, false)
            fun bg(style: String?) = if (prop("drawing-page", style, "d.fill") == "solid" || prop("drawing-page", style, "d.fill-color") != null) hex(prop("drawing-page", style, "d.fill-color")) else null
            val background = bg(dp) ?: bg(master?.a(DRAW, "style-name")) ?: 0xFFFFFFFF.toInt()
            val notes = page.kids().firstOrNull { it.localName == "notes" }?.kids()?.firstOrNull { it.a(PRES, "class") == "notes" }
                ?.getElementsByTagNameNS(TEXT, "p")?.let { l -> (0 until l.length).joinToString("\n") { l.item(it).textContent } }?.trim().orEmpty()
            Slide(items, background, notes, prop("drawing-page", dp, "d.visibility") == "hidden")
        }
        Deck(width, height, slides)
    }
}
