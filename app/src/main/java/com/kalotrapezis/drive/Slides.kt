package com.kalotrapezis.drive

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import org.w3c.dom.Element
import kotlin.math.max

/**
 * PowerPoint (the plan: after Excel; a viewer first, its animations later with the user watching): a deck's slides as
 * they show — text in placeholders and boxes with the looks their layout, master and theme give them, shapes in their
 * outlines, pictures, charts, tables, groups, backgrounds — and each slide's speaker notes. Sizes in points.
 */
internal class SlideRun(val text: String, val size: Float, val bold: Boolean, val italic: Boolean, val underline: Boolean, val color: Int, val font: String?)
internal class SlidePara(val runs: List<SlideRun>, val align: Char, val level: Int, val bullet: String?, val bulletColor: Int?,
    val marL: Float, val indent: Float, val before: Float, val after: Float, val line: Float)
/** [shrink]: "shrink text on overflow" (PowerPoint's normAutofit, LibreOffice's shrink-to-fit): made smaller until it fits its box. */
internal class SlideText(val paras: List<SlidePara>, val anchor: Char, val insets: FloatArray, val scale: Float = 1f, val wrap: Boolean = true, val shrink: Boolean = false)

/**
 * A shape's own outline (DrawingML's custGeom): its moves (0), lines (1), curves (2) and closes (4), the points from 0 to 1
 * of its box; filled and stroked as it says.
 */
internal class SlidePath(val ops: List<FloatArray>, val fill: Boolean, val stroke: Boolean)

/** A gradient fill: its colours at their stops (0–1), along [angle] degrees (0: left to right, 90: top to bottom). */
internal class SlideGradient(val colors: IntArray, val stops: FloatArray, val angle: Float)

internal sealed class SlideItem(val x: Float, val y: Float, val w: Float, val h: Float, val rotation: Float) {
    class Shape(x: Float, y: Float, w: Float, h: Float, rotation: Float, val shape: ShapePicture?, val text: SlideText?,
        val paths: List<SlidePath> = emptyList(), val flipH: Boolean = false, val flipV: Boolean = false, val gradient: SlideGradient? = null,
        /** The slide's own shape's id (cNvPr id), its words editable; null: the master's or layout's. */ val id: String? = null,
        /** An empty placeholder's grey hint (Title, Text): shown only while editing. */ val hint: Boolean = false) : SlideItem(x, y, w, h, rotation)
    class Picture(x: Float, y: Float, w: Float, h: Float, rotation: Float, val bytes: () -> ByteArray?) : SlideItem(x, y, w, h, rotation)
    class Chart(x: Float, y: Float, w: Float, h: Float, val chart: DocChart) : SlideItem(x, y, w, h, 0f)
    /** A table: its columns' widths and rows' heights, each cell's fill and words. */
    class Table(x: Float, y: Float, w: Float, h: Float, val cols: List<Float>, val rows: List<Float>, val cells: List<List<Pair<Int?, SlideText?>>>,
        val line: Int?) : SlideItem(x, y, w, h, 0f)
}

/** [part]: the slide's part in the file (ppt/slides/slide3.xml), for saving into. */
internal class Slide(val items: List<SlideItem>, val background: Int, val notes: String, val hidden: Boolean = false, val part: String = "")
internal class Deck(val width: Float, val height: Float, val slides: List<Slide>, val layouts: List<SlideLayout> = emptyList())

/** A layout a new slide can take: its name, its part, a slide of it with grey hints (Title, Text), the empty slide saved. */
internal class SlideLayout(val name: String, val part: String, val preview: Slide, val xml: ByteArray)

internal object Slides {
    fun isSlides(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("pptx", "ppsx", "odp")

    private const val EMU = 12700f // EMUs in a point

    private fun Element.kids(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private fun Element.kids(name: String) = kids().filter { it.localName == name }
    private fun Element.kid(name: String): Element? = kids().firstOrNull { it.localName == name }
    private fun Element.below(name: String): List<Element> = getElementsByTagNameNS("*", name).let { l -> (0 until l.length).map { l.item(it) as Element } }
    private fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }
    private fun Element.rid(name: String): String? = getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", name).takeIf { it.isNotEmpty() }

    /** A deck read from its parts. */
    fun read(file: java.io.File): Deck {
        if (file.extension.equals("odp", true)) return SlidesOdp.read(file)
        val zip = java.util.zip.ZipFile(file)
        val cache = HashMap<String, ByteArray?>()
        fun part(name: String): ByteArray? = synchronized(cache) { cache.getOrPut(name) { zip.getEntry(name)?.let { e -> zip.getInputStream(e).use { it.readBytes() } } } }
        fun xml(name: String) = part(name)?.let { OfficeFormats.parse(it).documentElement }
        return try { Pptx(::part, ::xml).read() } finally { /* parts are read as slides are drawn: the zip stays open for pictures */ }
    }

    /** A part's relationships: id → the part's path. */
    private fun rels(xml: (String) -> Element?, path: String): Map<String, String> {
        val dir = path.substringBeforeLast('/', "")
        val r = xml((if (dir.isEmpty()) "" else "$dir/") + "_rels/" + path.substringAfterLast('/') + ".rels") ?: return emptyMap()
        return r.kids("Relationship").associate { e ->
            val t = e.attr("Target").orEmpty()
            e.attr("Id").orEmpty() to (if (t.startsWith("/")) t.drop(1) else normalize("$dir/$t"))
        }
    }
    private fun relType(xml: (String) -> Element?, path: String, type: String): String? {
        val dir = path.substringBeforeLast('/', "")
        val r = xml((if (dir.isEmpty()) "" else "$dir/") + "_rels/" + path.substringAfterLast('/') + ".rels") ?: return null
        return r.kids("Relationship").firstOrNull { it.attr("Type")?.endsWith("/$type") == true }?.attr("Target")?.let { if (it.startsWith("/")) it.drop(1) else normalize("$dir/$it") }
    }
    private fun normalize(p: String): String { val out = ArrayList<String>(); for (s in p.split('/')) when (s) { ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex); ".", "" -> Unit; else -> out += s }; return out.joinToString("/") }

    private class Pptx(val part: (String) -> ByteArray?, val xml: (String) -> Element?) {
        // the theme's colours and fonts, the master's map of them (bg1 → lt1…)
        var scheme = HashMap<String, Int>(); var major = "Calibri Light"; var minor = "Calibri"; var map = HashMap<String, String>()
        var fillStyles = emptyList<Element>() // the theme's fills a shape style's fillRef names (1, 2, 3)
        var ph: Int? = null // the style's colour, for the theme's fills that say "phClr"

        fun read(): Deck {
            val pres = xml("ppt/presentation.xml") ?: error("Not a presentation.")
            val size = pres.kid("sldSz")
            val w = (size?.attr("cx")?.toFloatOrNull() ?: 9144000f) / EMU; val h = (size?.attr("cy")?.toFloatOrNull() ?: 6858000f) / EMU
            val rels = rels(xml, "ppt/presentation.xml")
            val defaults = pres.kid("defaultTextStyle")
            val slides = pres.kid("sldIdLst")?.kids("sldId").orEmpty().mapNotNull { it.rid("id")?.let(rels::get) }
            val read = slides.mapNotNull { path -> runCatching { slide(path, defaults) }.onFailure { WriterLog.e("Slides: $path", it) }.getOrNull() }
            return Deck(w, h, read, runCatching { layouts(pres, rels, defaults) }.onFailure { WriterLog.w("Slides: layouts", it) }.getOrDefault(emptyList()))
        }

        /**
         * The first master's layouts, each as a new slide would be: its title, body and other placeholders (not the date,
         * footer or number), empty to save, with grey hints to show.
         */
        fun layouts(pres: Element, rels: Map<String, String>, defaults: Element?): List<SlideLayout> {
            val masterPath = pres.kid("sldMasterIdLst")?.kids("sldMasterId")?.firstOrNull()?.rid("id")?.let(rels::get) ?: return emptyList()
            val master = xml(masterPath) ?: return emptyList()
            val mrels = rels(xml, masterPath)
            return master.kid("sldLayoutIdLst")?.kids("sldLayoutId").orEmpty().mapNotNull { it.rid("id")?.let(mrels::get) }.mapNotNull { path ->
                val layout = xml(path) ?: return@mapNotNull null
                val name = layout.kid("cSld")?.attr("name") ?: "Layout"
                val phs = layout.kid("cSld")?.kid("spTree")?.kids("sp").orEmpty().mapNotNull { sp ->
                    sp.kid("nvSpPr")?.kid("nvPr")?.kid("ph")?.takeIf { it.attr("type") !in setOf("dt", "ftr", "sldNum", "hdr", "sldImg") } }
                fun slideXml(hints: Boolean) = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>""")
                    phs.forEachIndexed { i, ph ->
                        val type = ph.attr("type"); val idx = ph.attr("idx")
                        val hint = when (type) { "title", "ctrTitle" -> "Title"; "subTitle" -> "Subtitle"; "pic" -> "Picture"; else -> "Text" }
                        append("""<p:sp><p:nvSpPr><p:cNvPr id="${i + 2}" name="$hint ${i + 1}"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph${type?.let { " type=\"$it\"" }.orEmpty()}${idx?.let { " idx=\"$it\"" }.orEmpty()}/></p:nvPr></p:nvSpPr><p:spPr/>""")
                        append(if (hints) """<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:pPr><a:buNone/></a:pPr><a:r><a:rPr lang="el-GR"><a:solidFill><a:srgbClr val="8C8C8C"/></a:solidFill></a:rPr><a:t>$hint</a:t></a:r></a:p></p:txBody></p:sp>"""
                            else """<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr lang="el-GR"/></a:p></p:txBody></p:sp>""")
                    }
                    append("""</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>""")
                }
                val preview = slide(path, defaults, OfficeFormats.parse(slideXml(true).toByteArray()).documentElement, path)
                SlideLayout(name, path, Slide(preview.items.map { i -> if (i is SlideItem.Shape && i.id != null) SlideItem.Shape(i.x, i.y, i.w, i.h, i.rotation, i.shape, i.text, i.paths, i.flipH, i.flipV, i.gradient, i.id, hint = true) else i },
                    preview.background, ""), slideXml(false).toByteArray())
            }
        }

        /** A colour as DrawingML gives it: its own, a scheme's (through the master's map), changed by lumMod/lumOff, shade, tint, alpha. */
        fun color(e: Element?): Int? {
            val c = e?.kids()?.firstOrNull { it.localName in setOf("srgbClr", "schemeClr", "sysClr", "prstClr") } ?: return null
            var rgb = when (c.localName) {
                "srgbClr" -> c.attr("val")?.toIntOrNull(16)
                "sysClr" -> (c.attr("lastClr") ?: "000000").toIntOrNull(16)
                "prstClr" -> mapOf("black" to 0, "white" to 0xFFFFFF, "red" to 0xFF0000, "green" to 0x008000, "blue" to 0x0000FF, "yellow" to 0xFFFF00)[c.attr("val")]
                else -> c.attr("val")?.let { n -> if (n == "phClr") ph?.and(0xFFFFFF) else scheme[map[n] ?: n] ?: scheme[n] }
            } ?: return null
            var a = 255
            for (m in c.kids()) {
                val v = (m.attr("val")?.toFloatOrNull() ?: continue) / 100_000f
                when (m.localName) {
                    "lumMod", "lumOff" -> {
                        val hsl = FloatArray(3); androidx.core.graphics.ColorUtils.colorToHSL(rgb or 0xFF000000.toInt(), hsl)
                        hsl[2] = if (m.localName == "lumMod") hsl[2] * v else (hsl[2] + v).coerceAtMost(1f)
                        rgb = androidx.core.graphics.ColorUtils.HSLToColor(hsl) and 0xFFFFFF
                    }
                    "shade" -> rgb = mix(rgb, 0, 1 - v)
                    "tint" -> rgb = mix(rgb, 0xFFFFFF, 1 - v)
                    "alpha" -> a = (255 * v).toInt().coerceIn(0, 255)
                }
            }
            return (a shl 24) or (rgb and 0xFFFFFF)
        }
        private fun mix(c: Int, to: Int, f: Float): Int {
            fun ch(s: Int) = (((c shr s) and 255) + (((to shr s) and 255) - ((c shr s) and 255)) * f).toInt().coerceIn(0, 255) shl s
            return ch(16) or ch(8) or ch(0)
        }
        /** A gradient: its stops' colours (the style's colour where they say phClr) along its angle. */
        fun gradient(g: Element?, base: Int? = null): SlideGradient? {
            g ?: return null
            ph = base
            val stops = g.kid("gsLst")?.kids("gs").orEmpty().mapNotNull { gs -> color(gs)?.let { (gs.attr("pos")?.toFloatOrNull() ?: 0f) / 100_000f to it } }.sortedBy { it.first }
            ph = null
            if (stops.size < 2) return null
            return SlideGradient(stops.map { it.second }.toIntArray(), stops.map { it.first }.toFloatArray(), (g.kid("lin")?.attr("ang")?.toFloatOrNull() ?: 5_400_000f) / 60_000f)
        }
        /** A shape style's fill (fillRef idx 1–3: the theme's fills, in the style's colour): a colour or a gradient. */
        fun styleFill(ref: Element?): Pair<Int?, SlideGradient?> {
            val base = color(ref) ?: return null to null
            val idx = ref?.attr("idx")?.toIntOrNull() ?: return base to null
            val theme = fillStyles.getOrNull((if (idx > 1000) idx - 1001 else idx - 1))
            return when (theme?.localName) {
                "gradFill" -> base to gradient(theme, base)
                "noFill" -> null to null
                else -> base to null
            }
        }
        fun fill(spPr: Element?): Int? = spPr?.kid("solidFill")?.let(::color)
            ?: spPr?.kid("gradFill")?.below("gs")?.firstOrNull()?.let(::color) // shortcut: a gradient as its first colour; draw gradients if decks need them

        /** One slide: its layout's and master's placeholders under it, its background, its notes. */
        /** [own]: a slide not in the file yet (a layout's new slide), [layoutOf] its layout. */
        fun slide(path: String, defaults: Element?, own: Element? = null, layoutOf: String? = null): Slide {
            val s = own ?: xml(path) ?: error("No $path.")
            val rels = if (own != null) emptyMap() else rels(xml, path)
            val layoutPath = layoutOf ?: relType(xml, path, "slideLayout"); val layout = layoutPath?.let(xml)
            val masterPath = layoutPath?.let { relType(xml, it, "slideMaster") }; val master = masterPath?.let(xml)
            masterPath?.let { relType(xml, it, "theme") }?.let(xml)?.let(::theme)
            map = HashMap<String, String>().also { m -> master?.kid("clrMap")?.let { cm -> for (i in 0 until cm.attributes.length) cm.attributes.item(i).let { a -> m[a.localName ?: a.nodeName] = a.nodeValue } } }
            val bg = listOfNotNull(s, layout, master).firstNotNullOfOrNull { e -> e.kid("cSld")?.kid("bg")?.let { b -> b.kid("bgPr")?.let(::fill) ?: b.kid("bgRef")?.let(::color) } }
                ?: 0xFFFFFFFF.toInt()
            val context = Placeholders(layout, master, defaults)
            val items = ArrayList<SlideItem>()
            fun tree(e: Element?, from: String, ctx: Placeholders?) = e?.kid("cSld")?.kid("spTree")?.let { shapes(it, rels(xml, from), ctx, items, null) }
            // what the master and the layout show on every slide (a logo, a line), under the slide's own
            if (s.attr("showMasterSp") != "0" && layout?.attr("showMasterSp") != "0") tree(master, masterPath!!, null)
            if (s.attr("showMasterSp") != "0") tree(layout, layoutPath!!, null)
            s.kid("cSld")?.kid("spTree")?.let { shapes(it, rels, context, items, null) }
            val notes = (if (own != null) null else relType(xml, path, "notesSlide"))?.let(xml)?.below("sp")?.firstOrNull { it.below("ph").firstOrNull()?.attr("type") == "body" }
                ?.below("p")?.joinToString("\n") { p -> p.below("t").joinToString("") { it.textContent } }?.trim().orEmpty()
            return Slide(items, bg, notes, s.attr("show") == "0", path)
        }

        fun theme(t: Element) {
            scheme = HashMap()
            t.below("clrScheme").firstOrNull()?.kids()?.forEach { c -> color(c)?.let { scheme[c.localName] = it and 0xFFFFFF } }
            fillStyles = t.below("fillStyleLst").firstOrNull()?.kids().orEmpty()
            t.below("majorFont").firstOrNull()?.kid("latin")?.attr("typeface")?.let { major = it }
            t.below("minorFont").firstOrNull()?.kid("latin")?.attr("typeface")?.let { minor = it }
        }

        /** The layout's and master's shapes a placeholder takes its place and looks from, and the deck's text defaults. */
        inner class Placeholders(val layout: Element?, val master: Element?, val defaults: Element?) {
            private fun phOf(sp: Element) = sp.kids().firstOrNull { it.localName.startsWith("nv") }?.kid("nvPr")?.kid("ph")
            private fun find(tree: Element?, type: String, idx: String?): Element? {
                val shapes = tree?.kid("cSld")?.kid("spTree")?.kids("sp").orEmpty()
                fun kind(t: String?) = when (t) { null, "", "obj", "body", "subTitle" -> "body"; "ctrTitle", "title" -> "title"; else -> t }
                return idx?.let { i -> shapes.firstOrNull { phOf(it)?.attr("idx") == i } } ?: shapes.firstOrNull { kind(phOf(it)?.attr("type")) == kind(type) }
            }
            fun chain(sp: Element): List<Element> {
                val ph = phOf(sp) ?: return emptyList()
                val type = ph.attr("type") ?: "body"; val idx = ph.attr("idx")
                return listOfNotNull(find(layout, type, idx), find(master, type, null))
            }
            /** The master's text style for this kind of shape: titles', the body's, or other text's. */
            fun style(sp: Element): Element? {
                val type = phOf(sp)?.attr("type")
                val styles = master?.kid("txStyles") ?: return defaults
                return when {
                    phOf(sp) == null -> defaults ?: styles.kid("otherStyle")
                    type == "title" || type == "ctrTitle" -> styles.kid("titleStyle")
                    type in setOf("dt", "ftr", "sldNum") -> styles.kid("otherStyle")
                    else -> styles.kid("bodyStyle")
                }
            }
        }

        /** The shapes of a tree, [group] placing a group's children in its box. */
        fun shapes(tree: Element, rels: Map<String, String>, ctx: Placeholders?, out: MutableList<SlideItem>, group: ((FloatArray) -> FloatArray)?) {
            for (e in tree.kids()) runCatching {
                // a placeholder on the master or layout shows its words only on slides that fill it: left out there
                if (ctx == null && e.localName == "sp" && e.below("ph").isNotEmpty()) return@runCatching
                val chain = if (ctx != null && e.localName == "sp") ctx.chain(e) else emptyList()
                val spPr = e.kid("spPr") ?: e.kid("grpSpPr")
                val xfrm = (spPr?.kid("xfrm") ?: e.kid("xfrm")) ?: chain.firstNotNullOfOrNull { it.kid("spPr")?.kid("xfrm") }
                val off = xfrm?.kid("off"); val ext = xfrm?.kid("ext")
                var box = floatArrayOf((off?.attr("x")?.toFloatOrNull() ?: 0f) / EMU, (off?.attr("y")?.toFloatOrNull() ?: 0f) / EMU,
                    (ext?.attr("cx")?.toFloatOrNull() ?: 0f) / EMU, (ext?.attr("cy")?.toFloatOrNull() ?: 0f) / EMU)
                if (group != null) box = group(box)
                val rot = (xfrm?.attr("rot")?.toFloatOrNull() ?: 0f) / 60_000f
                when (e.localName) {
                    "sp" -> {
                        val style = e.kid("style")
                        val geom = spPr?.kid("prstGeom")?.attr("prst") ?: chain.firstNotNullOfOrNull { it.kid("spPr")?.kid("prstGeom")?.attr("prst") }
                        val noFill = spPr?.kid("noFill") != null
                        val styled = if (noFill || spPr?.kid("solidFill") != null || spPr?.kid("gradFill") != null) null to null
                            else style?.kid("fillRef")?.takeIf { it.attr("idx") != "0" }?.let(::styleFill) ?: (null to null)
                        val grad = if (noFill) null else gradient(spPr?.kid("gradFill")) ?: styled.second
                        val fillC = if (noFill) null else fill(spPr) ?: chain.firstNotNullOfOrNull { fill(it.kid("spPr")) } ?: styled.first
                        val ln = spPr?.kid("ln")
                        val lineC = if (ln?.kid("noFill") != null) null else ln?.kid("solidFill")?.let(::color) ?: style?.kid("lnRef")?.takeIf { it.attr("idx") != "0" }?.let(::color)
                        val width = (ln?.attr("w")?.toFloatOrNull() ?: 12700f) / EMU
                        val shape = if (fillC != null || lineC != null) ShapePicture(geom?.takeIf { it in ShapeGeometry.presets || it in ShapePicture.KINDS } ?: "rect", fillC, lineC, width,
                            adjust = spPr?.kid("prstGeom")?.below("gd")?.mapNotNull { g -> g.attr("fmla")?.removePrefix("val ")?.trim()?.toDoubleOrNull()?.let { (g.attr("name") ?: "") to it } }?.toMap().orEmpty()) else null
                        val fontColor = style?.kid("fontRef")?.let(::color)
                        val paths = spPr?.kid("custGeom")?.let(::custom).orEmpty()
                        val text = e.kid("txBody")?.let { text(it, chain.mapNotNull { c -> c.kid("txBody") }, ctx?.style(e) ?: ctx?.defaults, fontColor) }
                        if (shape != null || text != null) out += SlideItem.Shape(box[0], box[1], box[2], box[3], rot, shape, text, paths,
                            xfrm?.attr("flipH") == "1", xfrm?.attr("flipV") == "1", grad,
                            id = if (ctx != null && group == null) e.kid("nvSpPr")?.kid("cNvPr")?.attr("id") else null)
                    }
                    "pic" -> e.below("blip").firstOrNull()?.rid("embed")?.let(rels::get)?.let { p -> out += SlideItem.Picture(box[0], box[1], box[2], box[3], rot) { part(p) } }
                    "graphicFrame" -> {
                        e.below("chart").firstOrNull()?.rid("id")?.let(rels::get)?.let(part)?.let(OfficeCharts::readDrawingml)?.let { out += SlideItem.Chart(box[0], box[1], box[2], box[3], it) }
                        e.below("tbl").firstOrNull()?.let { out += table(it, box) }
                    }
                    "grpSp" -> {
                        val ch = xfrm?.kid("chOff"); val che = xfrm?.kid("chExt")
                        val cx = (ch?.attr("x")?.toFloatOrNull() ?: 0f) / EMU; val cy = (ch?.attr("y")?.toFloatOrNull() ?: 0f) / EMU
                        val cw = ((che?.attr("cx")?.toFloatOrNull() ?: 0f) / EMU).takeIf { it > 0 } ?: box[2]; val chh = ((che?.attr("cy")?.toFloatOrNull() ?: 0f) / EMU).takeIf { it > 0 } ?: box[3]
                        val kx = if (cw > 0) box[2] / cw else 1f; val ky = if (chh > 0) box[3] / chh else 1f
                        val g0 = box
                        shapes(e, rels, ctx, out) { b -> floatArrayOf(g0[0] + (b[0] - cx) * kx, g0[1] + (b[1] - cy) * ky, b[2] * kx, b[3] * ky) }
                    }
                    "AlternateContent" -> e.kid("Fallback")?.let { shapes(it, rels, ctx, out, group) }
                }
            }.onFailure { WriterLog.w("Slides: a shape could not be read", it) }
        }

        /**
         * A custom outline's paths (custGeom), in its path's own units made 0..1: lines, curves, arcs (as curves).
         * Null points (a guide's name, not a number): the shape is left a box.
         */
        // shortcut: guides (gdLst) are not worked out — their shapes stay boxes; work them out if decks show many
        fun custom(g: Element): List<SlidePath> = g.kid("pathLst")?.kids("path").orEmpty().mapNotNull { p ->
            val pw = p.attr("w")?.toFloatOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val ph = p.attr("h")?.toFloatOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val ops = ArrayList<FloatArray>(); var cx = 0f; var cy = 0f; var sx = 0f; var sy = 0f; var named = false
            fun pts(e: Element) = e.kids("pt").map { pt -> val x = pt.attr("x")?.toFloatOrNull(); val y = pt.attr("y")?.toFloatOrNull(); if (x == null || y == null) named = true; (x ?: 0f) to (y ?: 0f) }
            for (c in p.kids()) when (c.localName) {
                "moveTo" -> pts(c).firstOrNull()?.let { (x, y) -> ops += floatArrayOf(0f, x / pw, y / ph); cx = x; cy = y; sx = x; sy = y }
                "lnTo" -> pts(c).firstOrNull()?.let { (x, y) -> ops += floatArrayOf(1f, x / pw, y / ph); cx = x; cy = y }
                "cubicBezTo" -> pts(c).takeIf { it.size == 3 }?.let { q -> ops += floatArrayOf(2f, q[0].first / pw, q[0].second / ph, q[1].first / pw, q[1].second / ph, q[2].first / pw, q[2].second / ph); cx = q[2].first; cy = q[2].second }
                "quadBezTo" -> pts(c).takeIf { it.size == 2 }?.let { q ->
                    // a quadratic as a cubic: its control point two thirds of the way from each end
                    val c1x = cx + 2f / 3 * (q[0].first - cx); val c1y = cy + 2f / 3 * (q[0].second - cy)
                    val c2x = q[1].first + 2f / 3 * (q[0].first - q[1].first); val c2y = q[1].second + 2f / 3 * (q[0].second - q[1].second)
                    ops += floatArrayOf(2f, c1x / pw, c1y / ph, c2x / pw, c2y / ph, q[1].first / pw, q[1].second / ph); cx = q[1].first; cy = q[1].second }
                "arcTo" -> {
                    val wr = c.attr("wR")?.toFloatOrNull() ?: return@mapNotNull null; val hr = c.attr("hR")?.toFloatOrNull() ?: return@mapNotNull null
                    val st = Math.toRadians((c.attr("stAng")?.toDoubleOrNull() ?: 0.0) / 60_000); val sw = Math.toRadians((c.attr("swAng")?.toDoubleOrNull() ?: 0.0) / 60_000)
                    // the ellipse's centre: the pen is on it at the start angle; the sweep in pieces of at most a quarter turn
                    val ox = cx - wr * Math.cos(st); val oy = cy - hr * Math.sin(st)
                    val n = maxOf(1, Math.ceil(Math.abs(sw) / (Math.PI / 2)).toInt()); val step = sw / n
                    var a = st
                    repeat(n) {
                        val k = 4.0 / 3 * Math.tan(step / 4)
                        val x1 = ox + wr * Math.cos(a); val y1 = oy + hr * Math.sin(a); val b = a + step
                        val x2 = ox + wr * Math.cos(b); val y2 = oy + hr * Math.sin(b)
                        ops += floatArrayOf(2f, ((x1 - k * wr * Math.sin(a)) / pw).toFloat(), ((y1 + k * hr * Math.cos(a)) / ph).toFloat(),
                            ((x2 + k * wr * Math.sin(b)) / pw).toFloat(), ((y2 - k * hr * Math.cos(b)) / ph).toFloat(), (x2 / pw).toFloat(), (y2 / ph).toFloat())
                        a = b
                    }
                    cx = (ox + wr * Math.cos(a)).toFloat(); cy = (oy + hr * Math.sin(a)).toFloat()
                }
                "close" -> { ops += floatArrayOf(4f); cx = sx; cy = sy }
            }
            if (ops.isEmpty() || named) null else SlidePath(ops, p.attr("fill") != "none", p.attr("stroke") != "0" && p.attr("stroke") != "false")
        }

        /** A table: its grid, each cell's fill (the table style's banded look when it has none) and words. */
        fun table(t: Element, box: FloatArray): SlideItem.Table {
            val cols = t.kid("tblGrid")?.kids("gridCol").orEmpty().map { (it.attr("w")?.toFloatOrNull() ?: 0f) / EMU }
            val trs = t.kids("tr")
            val pr = t.kid("tblPr")
            // PowerPoint's Medium Style 2 (its default, banded in an accent) by its id; any other style: plain, with grid lines
            val id = pr?.kid("tableStyleId")?.textContent?.trim()?.uppercase()
            val medium = mapOf("{073A0DAA-6AF3-43AB-8588-CEC1D06C72B9}" to "dk1", "{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}" to "accent1",
                "{21E4AEA4-8DFA-4A89-87EB-49C32662AFE8}" to "accent2", "{F5AB1C69-6EDB-4FF4-983F-18BD219EF322}" to "accent3",
                "{00A15C55-8517-42AA-B614-E9B94910E393}" to "accent4", "{7DF18680-E054-41AD-8BC1-D1AEF772440D}" to "accent5",
                "{93296810-A885-4BE3-A3E7-6D5BEEA58F35}" to "accent6")[id]
            val styled = medium != null
            val first = pr?.attr("firstRow") == "1"; val banded = pr?.attr("bandRow") == "1"
            val accent = scheme[medium ?: "accent1"] ?: 0x4472C4
            val cells = trs.mapIndexed { r, tr -> tr.kids("tc").map { tc ->
                val own = fill(tc.kid("tcPr"))
                val look = when { own != null -> own; !styled -> null; first && r == 0 -> accent or 0xFF000000.toInt()
                    banded && (r - if (first) 1 else 0) % 2 == 0 -> 0xFF000000.toInt() or mix(accent, 0xFFFFFF, 0.6f) else -> 0xFF000000.toInt() or mix(accent, 0xFFFFFF, 0.8f) }
                look to tc.kid("txBody")?.let { text(it, emptyList(), null, if (styled && first && r == 0 && own == null) 0xFFFFFFFF.toInt() else null, defaultSize = 18f, boldAll = styled && first && r == 0) }
            } }
            return SlideItem.Table(box[0], box[1], box[2], box[3], cols, trs.map { (it.attr("h")?.toFloatOrNull() ?: 0f) / EMU }, cells, if (styled) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        }

        /** A text body: its paragraphs with the looks of their level from the shape, its placeholders and the master. */
        fun text(body: Element, inherited: List<Element>, master: Element?, fontColor: Int?, defaultSize: Float = 18f, boldAll: Boolean = false): SlideText? {
            val paras = body.kids("p")
            if (paras.none { p -> p.below("t").any { it.textContent.isNotEmpty() } }) return null
            // a level's pPr from the nearest: the shape's lstStyle, its placeholders', the master's style
            val lists = listOfNotNull(body.kid("lstStyle")) + inherited.mapNotNull { it.kid("lstStyle") } + listOfNotNull(master)
            fun level(n: Int) = lists.mapNotNull { it.kid("lvl${n + 1}pPr") }
            val bodyPr = (listOf(body) + inherited).mapNotNull { it.kid("bodyPr") }
            fun bodyAttr(a: String) = bodyPr.firstNotNullOfOrNull { it.attr(a) }
            val anchor = when (bodyAttr("anchor")) { "ctr" -> 'c'; "b" -> 'b'; else -> 't' }
            fun inset(a: String, d: Float) = (bodyAttr(a)?.toFloatOrNull()?.div(EMU)) ?: d
            val scale = bodyPr.firstNotNullOfOrNull { it.kid("normAutofit")?.attr("fontScale")?.toFloatOrNull() }?.div(100_000f) ?: 1f
            val out = paras.map { p ->
                val pPr = p.kid("pPr")
                val lvl = pPr?.attr("lvl")?.toIntOrNull() ?: 0
                val looks = listOfNotNull(pPr) + level(lvl)
                fun pAttr(a: String) = looks.firstNotNullOfOrNull { it.attr(a) }
                fun pKid(n: String) = looks.firstNotNullOfOrNull { it.kid(n) }
                val defR = looks.mapNotNull { it.kid("defRPr") }
                fun runOf(r: Element?, t: String): SlideRun {
                    val rPr = listOfNotNull(r?.kid("rPr")) + defR
                    fun rAttr(a: String) = rPr.firstNotNullOfOrNull { it.attr(a) }
                    val size = (rAttr("sz")?.toFloatOrNull()?.div(100f) ?: defaultSize) * scale
                    // its own colour, its paragraph's, the shape style's (white words on Office's blue shapes), then the inherited styles'
                    val own = listOfNotNull(r?.kid("rPr"), pPr?.kid("defRPr")).firstNotNullOfOrNull { it.kid("solidFill")?.let(::color) }
                    val color = own ?: fontColor ?: rPr.firstNotNullOfOrNull { it.kid("solidFill")?.let(::color) } ?: scheme[map["tx1"] ?: "dk1"]?.or(0xFF000000.toInt()) ?: 0xFF000000.toInt()
                    val face = rPr.firstNotNullOfOrNull { it.kid("latin")?.attr("typeface") }?.let { if (it == "+mj-lt") major else if (it == "+mn-lt") minor else it }
                        ?: if (master?.localName == "titleStyle") major else minor
                    return SlideRun(t, size, boldAll || rAttr("b") == "1", rAttr("i") == "1", rAttr("u").let { it != null && it != "none" }, color, face)
                }
                val runs = p.kids().mapNotNull { r -> when (r.localName) { "r", "fld" -> runOf(r, r.kid("t")?.textContent.orEmpty()); "br" -> runOf(r, "\n"); else -> null } }
                    .ifEmpty { listOf(runOf(null, "")) }
                val bullet = when {
                    pKid("buNone") != null || pPr?.kid("buNone") != null -> null
                    else -> pKid("buChar")?.attr("char") ?: pKid("buAutoNum")?.let { "${paras.indexOf(p) + 1}." }
                }
                fun pt(a: String) = pAttr(a)?.toFloatOrNull()?.div(EMU) ?: 0f
                fun spacing(n: String) = pKid(n)?.let { s -> s.kid("spcPts")?.attr("val")?.toFloatOrNull()?.div(100f) ?: s.kid("spcPct")?.attr("val")?.toFloatOrNull()?.let { it / 100_000f * (runs.firstOrNull()?.size ?: defaultSize) } } ?: 0f
                val line = pKid("lnSpc")?.kid("spcPct")?.attr("val")?.toFloatOrNull()?.div(100_000f) ?: 1f
                SlidePara(runs, when (pAttr("algn")) { "ctr" -> 'c'; "r" -> 'r'; "just" -> 'j'; else -> 'l' }, lvl, bullet,
                    pKid("buClr")?.let(::color), pt("marL"), pt("indent"), spacing("spcBef") * scale, spacing("spcAft") * scale, line * (1 - (1 - scale) / 5))
            }
            return SlideText(out, anchor, floatArrayOf(inset("lIns", 7.2f), inset("tIns", 3.6f), inset("rIns", 7.2f), inset("bIns", 3.6f)), scale, bodyAttr("wrap") != "none",
                bodyPr.any { it.kid("normAutofit") != null })
        }
    }

    // --- drawing ---

    private val pictures = android.util.LruCache<Any, android.graphics.Bitmap>(24)

    /** A slide drawn on [c] at [k] screen pixels a point, from 0,0. */
    fun draw(c: Canvas, deck: Deck, slide: Slide, k: Float, hints: Boolean = false) {
        c.save(); c.scale(k, k)
        c.clipRect(0f, 0f, deck.width, deck.height)
        c.drawColor(slide.background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (item in slide.items) {
            if (item is SlideItem.Shape && item.hint && !hints) continue
            c.save()
            if (item.rotation != 0f) c.rotate(item.rotation, item.x + item.w / 2, item.y + item.h / 2)
            when (item) {
                is SlideItem.Shape -> {
                    if (item.flipH || item.flipV) c.scale(if (item.flipH) -1f else 1f, if (item.flipV) -1f else 1f, item.x + item.w / 2, item.y + item.h / 2)
                    if (item.paths.isNotEmpty() || item.gradient != null) outline(c, item, paint) else item.shape?.drawAt(c, item.x, item.y, item.w, item.h)
                    if (item.flipH || item.flipV) c.scale(if (item.flipH) -1f else 1f, if (item.flipV) -1f else 1f, item.x + item.w / 2, item.y + item.h / 2) // words not mirrored
                    item.text?.let { text(c, it, item.x, item.y, item.w, item.h) }
                }
                is SlideItem.Picture -> {
                    val bmp = pictures.get(item) ?: runCatching { item.bytes()?.let { b ->
                        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size, o)
                        var sample = 1; while (o.outWidth / (sample * 2) >= item.w * 3) sample *= 2
                        android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                    } }.getOrNull()?.also { pictures.put(item, it) }
                    bmp?.let { c.drawBitmap(it, null, android.graphics.RectF(item.x, item.y, item.x + item.w, item.y + item.h), paint) }
                }
                is SlideItem.Chart -> ChartDrawable(item.chart).apply { setBounds(item.x.toInt(), item.y.toInt(), (item.x + item.w).toInt(), (item.y + item.h).toInt()) }.draw(c)
                is SlideItem.Table -> table(c, item, paint)
            }
            c.restore()
        }
        c.restore()
    }

    /**
     * A shape drawn here (its own outline, or a preset filled with a gradient): each path filled with its fill or gradient
     * and stroked with its line.
     */
    private fun outline(c: Canvas, item: SlideItem.Shape, paint: Paint) {
        val fill = item.shape?.fill; val line = item.shape?.line
        val paths: List<Pair<android.graphics.Path, SlidePath?>> = if (item.paths.isNotEmpty()) item.paths.map { sp ->
            val path = android.graphics.Path()
            fun x(v: Float) = item.x + v * item.w
            fun y(v: Float) = item.y + v * item.h
            for (o in sp.ops) when (o[0].toInt()) {
                0 -> path.moveTo(x(o[1]), y(o[2]))
                1 -> path.lineTo(x(o[1]), y(o[2]))
                2 -> path.cubicTo(x(o[1]), y(o[2]), x(o[3]), y(o[4]), x(o[5]), y(o[6]))
                4 -> path.close()
            }
            path to sp
        } else {
            // a preset's outline from its geometry, placed in the box
            val kind = item.shape?.geometry ?: "rect"
            val geo = ShapeGeometry.presets[kind]
            if (geo != null) geo.paths(item.w, item.h, item.shape?.adjust.orEmpty()).map { sub ->
                val path = android.graphics.Path()
                for (op in sub.ops) when (op) {
                    is ShapeGeometry.Op.Move -> path.moveTo(item.x + op.x, item.y + op.y)
                    is ShapeGeometry.Op.Line -> path.lineTo(item.x + op.x, item.y + op.y)
                    is ShapeGeometry.Op.Cubic -> path.cubicTo(item.x + op.x1, item.y + op.y1, item.x + op.x2, item.y + op.y2, item.x + op.x, item.y + op.y)
                    is ShapeGeometry.Op.Quad -> path.quadTo(item.x + op.x1, item.y + op.y1, item.x + op.x, item.y + op.y)
                    ShapeGeometry.Op.Close -> path.close()
                }
                path to SlidePath(emptyList(), sub.fill, sub.stroke)
            } else listOf(ShapePicture.path(kind, item.w, item.h).apply { offset(item.x, item.y) } to null)
        }
        val g = item.gradient
        for ((path, sp) in paths) {
            if ((sp?.fill ?: true) && g != null) {
                // along its angle through the box's middle, as far as the box reaches that way
                val a = Math.toRadians(g.angle.toDouble()); val cx = item.x + item.w / 2; val cy = item.y + item.h / 2
                val r = (Math.abs(Math.cos(a)) * item.w / 2 + Math.abs(Math.sin(a)) * item.h / 2).toFloat()
                paint.shader = android.graphics.LinearGradient(cx - r * Math.cos(a).toFloat(), cy - r * Math.sin(a).toFloat(), cx + r * Math.cos(a).toFloat(), cy + r * Math.sin(a).toFloat(),
                    g.colors, g.stops, android.graphics.Shader.TileMode.CLAMP)
                paint.style = Paint.Style.FILL; paint.color = 0xFF000000.toInt(); c.drawPath(path, paint); paint.shader = null
            } else if ((sp?.fill ?: true) && fill != null) { paint.style = Paint.Style.FILL; paint.color = fill; c.drawPath(path, paint) }
            if ((sp?.stroke ?: true) && line != null) { paint.style = Paint.Style.STROKE; paint.strokeWidth = item.shape?.lineWidth ?: 1f; paint.strokeJoin = Paint.Join.ROUND; paint.color = line; c.drawPath(path, paint) }
        }
    }

    private fun table(c: Canvas, t: SlideItem.Table, paint: Paint) {
        var y = t.y
        t.cells.forEachIndexed { r, row ->
            // a row grows to its words, as PowerPoint's
            val need = row.mapIndexed { i, (_, text) -> text?.let { measure(it, t.cols.getOrElse(i) { 0f }) } ?: 0f }.maxOrNull() ?: 0f
            val h = max(t.rows.getOrElse(r) { 0f }, need)
            var x = t.x
            row.forEachIndexed { i, (fill, text) ->
                val w = t.cols.getOrElse(i) { 0f }
                fill?.let { paint.style = Paint.Style.FILL; paint.color = it; c.drawRect(x, y, x + w, y + h, paint) }
                t.line?.let { paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = it; c.drawRect(x, y, x + w, y + h, paint) }
                text?.let { text(c, it, x, y, w, h) }
                x += w
            }
            y += h
        }
    }

    private fun layouts(t: SlideText, w: Float, k: Float = 1f): List<Pair<SlidePara, StaticLayout>> {
        val width = max(1f, w - t.insets[0] - t.insets[2])
        return t.paras.map { p ->
            val sb = SpannableStringBuilder()
            val lead = p.marL + p.indent
            p.bullet?.let { b -> sb.append(b).append(' '); p.bulletColor?.let { sb.setSpan(android.text.style.ForegroundColorSpan(it), 0, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) } }
            for (r in p.runs) {
                val s = sb.length; sb.append(r.text); val e = sb.length
                if (e == s) continue
                sb.setSpan(android.text.style.AbsoluteSizeSpan((r.size * k).toInt().coerceAtLeast(1)), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(android.text.style.ForegroundColorSpan(r.color), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val style = (if (r.bold) android.graphics.Typeface.BOLD else 0) or (if (r.italic) android.graphics.Typeface.ITALIC else 0)
                if (style != 0) sb.setSpan(android.text.style.StyleSpan(style), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (r.underline) sb.setSpan(android.text.style.UnderlineSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                OfficeFonts.typeface(r.font ?: "")?.let { tf -> sb.setSpan(android.text.style.TypefaceSpan(tf), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            }
            // the first line from marL + indent (a bullet hanging), the rest from marL
            if (sb.isNotEmpty()) sb.setSpan(android.text.style.LeadingMarginSpan.Standard(lead.toInt().coerceAtLeast(0), p.marL.toInt().coerceAtLeast(0)), 0, sb.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            val size = (p.runs.firstOrNull()?.size ?: 18f) * k
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; color = p.runs.firstOrNull()?.color ?: 0xFF000000.toInt() }
            val align = when (p.align) { 'c' -> Layout.Alignment.ALIGN_CENTER; 'r' -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
            val text: CharSequence = if (sb.isEmpty()) " " else sb
            p to StaticLayout.Builder.obtain(text, 0, text.length, paint, if (t.wrap) width.toInt() else 100_000).setAlignment(align)
                .setIncludePad(false).setLineSpacing(0f, p.line).build()
        }
    }

    private fun measure(t: SlideText, w: Float) = layouts(t, w).sumOf { (p, l) -> (p.before + l.height + p.after).toDouble() }.toFloat() + t.insets[1] + t.insets[3]

    /** A text body in its box: its paragraphs one under the other, from the top, the middle or the bottom. */
    fun text(c: Canvas, t: SlideText, x: Float, y: Float, w: Float, h: Float) {
        val room = h - t.insets[1] - t.insets[3]
        fun height(l: List<Pair<SlidePara, StaticLayout>>) = l.sumOf { (p, s) -> (p.before + s.height + p.after).toDouble() }.toFloat()
        // shrink on overflow: a size a twentieth smaller each time, until the words fit (no smaller than a third)
        var k = 1f
        var laid = layouts(t, w)
        while (t.shrink && height(laid) > room && k > 0.35f) { k -= 0.05f; laid = layouts(t, w, k) }
        val total = height(laid)
        var top = y + t.insets[1] + when (t.anchor) { 'c' -> (room - total) / 2; 'b' -> room - total; else -> 0f }
        for ((p, l) in laid) {
            top += p.before
            c.save(); c.translate(x + t.insets[0], top); l.draw(c); c.restore()
            top += l.height + p.after
        }
    }
}
