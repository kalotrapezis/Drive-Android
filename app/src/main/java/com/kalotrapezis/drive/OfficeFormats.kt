package com.kalotrapezis.drive

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

/*
 * Office › Writer (asked 2026-09-27): .docx and .odt, read into what Writer can show and edit, and written back.
 *
 * What Writer keeps: the text; bold, italic, underline, strikethrough, text size, text colour, highlight and links;
 * headings 1–6, bulleted and numbered lists, and alignment. Everything outside the body — page size and margins,
 * headers and footers, the document's own styles and fonts — is kept by writing the new body into the original
 * package, never by rebuilding the file. Anything in the body Writer cannot keep (images, tables, comments, tracked
 * changes…) is named in [OfficeDoc.unsupported]; such a file opens, and is saved only as a copy — the original is
 * never overwritten (the user's rule). Nothing here depends on Android, so it is tested on the computer.
 */

internal enum class DocAlign { Start, Center, End, Justify }

internal data class DocRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val sizePt: Float? = null, // null: the document's own size
    val color: Int? = null, // 0xRRGGBB
    val highlight: Int? = null, // 0xRRGGBB
    val link: String? = null,
    val image: DocImage? = null, // then [text] is OBJECT_MARK
    val font: String? = null, // the file's own letter settings Writer does not edit, raw: fonts, small caps, spacing, language… (Word's rPr elements; ODF's attributes)
    val script: Int = 0, // 1 superscript, -1 subscript
    val comments: List<String> = emptyList(), // the ids of the comments over these letters (OfficeDoc.comments)
) {
    fun sameLook(o: DocRun) = image == null && o.image == null && copy(text = "") == o.copy(text = "")
}

/**
 * A comment on some letters (asked 2026-09-29): who wrote it, when (ISO), what it says. One from the file goes back as
 * the file had it ([raw]: Word's w:comment, or ODT's annotation contents) — replies and their links stay; a new one is
 * written from its fields.
 */
internal data class DocComment(val id: String, val author: String, val date: String?, val text: String, val raw: String? = null, val initials: String? = null)

/** A list that tags every run added to it with the comments open at that moment (the readers' ranges). */
internal class CommentedRuns(private val open: Collection<String>) : ArrayList<DocRun>() {
    override fun add(element: DocRun) = super.add(if (open.isEmpty()) element else element.copy(comments = open.toList()))
}

/** A picture, a chart or a shape stands in the text as this one character. */
internal const val OBJECT_MARK = '\uFFFC'

/**
 * A picture (or chart, shape, embedded object) in the text. One from the file goes back exactly as it was ([raw], the
 * file's own XML, which points at its [part] inside the package); a new one has its [bytes]. [part] is null for what
 * is not a picture — it shows as a grey box. Sizes in points.
 */
internal class DocImage(val key: String, val part: String?, val widthPt: Float, val heightPt: Float, val raw: String? = null,
    val bytes: ByteArray? = null, val extension: String = "png", val wrap: PictureWrap? = null /* null: as the file has it */,
    val table: DocTable? = null /* the editor's stand-in for a table */,
    val onPage: Pair<Float, Float>? = null /* a new floating one's top left on its page (pt): Draw › Draw on page */) {
    fun with(raw: String? = this.raw, widthPt: Float = this.widthPt, heightPt: Float = this.heightPt, wrap: PictureWrap? = this.wrap,
        table: DocTable? = this.table) = DocImage(key, part, widthPt, heightPt, raw, bytes, extension, wrap, table, onPage)
    override fun equals(other: Any?) = other is DocImage && other.key == key && other.part == part && other.raw == raw && other.widthPt == widthPt && other.heightPt == heightPt && other.wrap == wrap && other.table == table && other.onPage == onPage
    override fun hashCode() = key.hashCode()
    override fun toString() = "DocImage($key, $part, ${widthPt}x$heightPt)"
}

/** How the text goes around a picture — Word's Wrap Text (asked 2026-09-27). */
internal enum class PictureWrap(val label: String) {
    InLine("In line with text"), Square("Square"), TopBottom("Top and bottom"), Behind("Behind text"), InFront("In front of text")
}

/** A picture's own XML, changed by the Picture tab: its size and its wrapping, in each format's words. */
internal object PictureXml {
    private val DOCX_FRAME = Regex("""<wp:(inline|anchor)(\s[^>]*)?>([\s\S]*)</wp:\1>""")

    /** A picture Writer can resize and re-wrap: a new one, or a file's picture — not a chart, shape or old VML drawing. */
    fun editable(img: DocImage): Boolean {
        val raw = img.raw ?: return img.bytes != null
        if (raw.startsWith("<draw:custom-shape")) return true
        return if (raw.startsWith("<draw:frame")) "<draw:image" in raw else ("<pic:pic" in raw || "<wps:wsp" in raw && "<wps:txbx" !in raw) && !raw.startsWith("<mc:") && DOCX_FRAME.containsMatchIn(raw)
    }

    /** What it is, in a word, for the grey box shown in place of what Writer cannot draw. */
    fun kind(img: DocImage): String {
        if (img.table != null) return "Table"
        val raw = img.raw ?: return "Picture"
        return when {
            "drawingml/2006/chart" in raw || "<draw:object" in raw && "chart" in raw -> "Chart"
            "diagram" in raw -> "SmartArt"
            "txbx" in raw || "<draw:text-box" in raw -> "Text box"
            "OLEObject" in raw || "<draw:object" in raw -> "Embedded object"
            "<wps:" in raw || "<v:" in raw || "<draw:custom-shape" in raw || "<draw:rect" in raw || "<draw:line" in raw -> "Shape"
            else -> "Picture"
        }
    }

    /**
     * Where a floating picture stands, from what (page, margin — a column counts as the margin — or its paragraph),
     * in points or by alignment ("left", "center", "right", "top", "bottom"). Null: it stands in the line of text.
     */
    data class Place(val fromH: String, val x: Float?, val alignH: String?, val fromV: String, val y: Float?, val alignV: String?, val wrap: PictureWrap)

    fun placeOf(img: DocImage): Place? {
        val wrap = wrapOf(img) ?: PictureWrap.Square
        if (wrap == PictureWrap.InLine) return null
        // A new one: where it was drawn on its page, or (as it is written) at its paragraph's top left.
        val raw = img.raw ?: return img.onPage?.let { (x, y) -> Place("page", x, null, "page", y, null, wrap) } ?: Place("margin", 0f, null, "paragraph", 0f, null, wrap)
        if (raw.startsWith("<draw:frame") || raw.startsWith("<draw:custom-shape")) {
            val head = raw.substring(0, raw.indexOf('>'))
            fun attr(n: String) = Regex("""\s$n="([^"]*)"""").find(head)?.groupValues?.get(1)
            val from = if (attr("text:anchor-type") == "page" || ODT_PAGE_REL in head) "page" else "paragraph"
            return Place(if (from == "page") "page" else "margin", odfLength(attr("svg:x")) ?: 0f, null, from, odfLength(attr("svg:y")) ?: 0f, null, wrap)
        }
        if ("<wp:anchor" !in raw) return null
        fun axis(tag: String): Triple<String, Float?, String?> {
            val m = Regex("""<wp:$tag relativeFrom="([^"]*)"[^>]*>([\s\S]*?)</wp:$tag>""").find(raw) ?: return Triple("margin", 0f, null)
            val from = when (m.groupValues[1]) { "page" -> "page"; "paragraph", "line", "character" -> "paragraph"; else -> "margin" }
            val off = Regex("""<wp:posOffset>(-?\d+)</wp:posOffset>""").find(m.groupValues[2])?.groupValues?.get(1)?.toFloatOrNull()?.div(12700f)
            val align = Regex("""<wp:align>(\w+)</wp:align>""").find(m.groupValues[2])?.groupValues?.get(1)
            return Triple(from, off, align)
        }
        val (fh, x, ah) = axis("positionH"); val (fv, y, av) = axis("positionV")
        // Pen strokes: placed by their picture's box (the fallback's margins), from the same origin.
        if ("wordprocessingInk" in raw) {
            val style = Regex("""<v:shape\b[^>]*\bstyle="([^"]*)"""").find(raw)?.groupValues?.get(1)
            fun pt(key: String) = style?.let { Regex("""(?:^|;)$key:(-?[\d.]+)pt""").find(it)?.groupValues?.get(1)?.toFloatOrNull() }
            if (pt("margin-left") != null && pt("margin-top") != null) return Place(if (fh == "paragraph") "margin" else fh, pt("margin-left"), ah, fv, pt("margin-top"), av, wrap)
        }
        return Place(if (fh == "paragraph") "margin" else fh, x, ah, fv, y, av, wrap)
    }

    /** Its wrapping as the file has it (null: an ODT frame's, which lives in a style Writer does not look up). */
    fun wrapOf(img: DocImage): PictureWrap? {
        img.wrap?.let { return it }
        val raw = img.raw ?: return PictureWrap.InLine
        if (raw.startsWith("<draw:frame") || raw.startsWith("<draw:custom-shape")) return if ("text:anchor-type=\"as-char\"" in raw) PictureWrap.InLine
            else Regex("""Tetra_20_wrap_20_(\w+?)(?:$ODT_PAGE_REL)?"""").find(raw)?.groupValues?.get(1)?.let { n -> PictureWrap.entries.firstOrNull { it.name == n } }
            ?: Regex("""Tetra_20_shape_\w+?_\w+?_\d+_(\w+)"""").find(raw)?.groupValues?.get(1)?.let { n -> PictureWrap.entries.firstOrNull { it.name == n } }
        return when {
            "<wp:inline" in raw -> PictureWrap.InLine
            "<wp:wrapTopAndBottom" in raw -> PictureWrap.TopBottom
            "<wp:wrapNone" in raw -> if (Regex("""behindDoc="(1|true)"""").containsMatchIn(raw)) PictureWrap.Behind else PictureWrap.InFront
            "<wp:anchor" in raw -> PictureWrap.Square
            else -> null
        }
    }

    fun resized(img: DocImage, widthPt: Float, heightPt: Float): DocImage {
        val raw = img.raw ?: return img.with(widthPt = widthPt, heightPt = heightPt)
        val out = if (raw.startsWith("<draw:frame") || raw.startsWith("<draw:custom-shape")) {
            val end = raw.indexOf('>')
            raw.substring(0, end).replace(Regex("""svg:width="[^"]*""""), "svg:width=\"${OdtWriter.fmt(widthPt)}pt\"")
                .replace(Regex("""svg:height="[^"]*""""), "svg:height=\"${OdtWriter.fmt(heightPt)}pt\"") + raw.substring(end)
        } else {
            // The frame's extent and the picture's own (a:ext) are the same numbers: both change.
            val m = Regex("""<wp:extent cx="(\d+)" cy="(\d+)"""").find(raw) ?: return img
            raw.replace("cx=\"${m.groupValues[1]}\" cy=\"${m.groupValues[2]}\"", "cx=\"${(widthPt * 12700).toLong()}\" cy=\"${(heightPt * 12700).toLong()}\"")
        }
        return img.with(raw = out, widthPt = widthPt, heightPt = heightPt)
    }

    /** Word: wp:inline for in line; otherwise wp:anchor at the paragraph's top left (or where it already was). */
    fun docx(xml: String, wrap: PictureWrap): String {
        val m = DOCX_FRAME.find(xml) ?: return xml
        val inner = m.groupValues[3]
        val ns = Regex("""\sxmlns:\w+="[^"]*"""").findAll(m.groupValues[2]).joinToString("") { it.value }
        val extent = Regex("""<wp:extent[^>]*/>""").find(inner)?.value ?: return xml
        val effect = Regex("""<wp:effectExtent[^>]*/>""").find(inner)?.value ?: ""
        val rest = inner.indexOf("<wp:docPr").takeIf { it >= 0 }?.let { inner.substring(it) } ?: return xml
        val frame = if (wrap == PictureWrap.InLine)
            """<wp:inline distT="0" distB="0" distL="0" distR="0"$ns>$extent$effect${rest.replace(Regex("""<wp14:sizeRel[HV][\s\S]*?</wp14:sizeRel[HV]>"""), "")}</wp:inline>"""
        else {
            val place = (if (m.groupValues[1] == "anchor") Regex("""<wp:simplePos[^>]*/>[\s\S]*</wp:positionV>""").find(inner)?.value else null)
                ?: """<wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="column"><wp:posOffset>0</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>0</wp:posOffset></wp:positionV>"""
            val how = when (wrap) { PictureWrap.Square -> """<wp:wrapSquare wrapText="bothSides"/>"""; PictureWrap.TopBottom -> "<wp:wrapTopAndBottom/>"; else -> "<wp:wrapNone/>" }
            """<wp:anchor distT="0" distB="0" distL="114300" distR="114300" simplePos="0" relativeHeight="251659264" behindDoc="${if (wrap == PictureWrap.Behind) 1 else 0}" locked="0" layoutInCell="1" allowOverlap="1"$ns>$place$extent$effect$how$rest</wp:anchor>"""
        }
        return xml.replaceRange(m.range, frame)
    }

    /** ODF: the frame's anchor, and a graphic style of Writer's own saying how the text goes around it. */
    fun odt(xml: String, wrap: PictureWrap, onPage: Pair<Float, Float>? = null): String {
        // A shape Writer made keeps its look: its style's name changes only in how the text goes around it.
        if (xml.startsWith("<draw:custom-shape") && "Tetra_20_shape_" in xml) {
            val end = xml.indexOf('>')
            val head = xml.substring(0, end).replace(Regex("""(Tetra_20_shape_\w+?_\w+?_\d+_)\w+"""), "$1${wrap.name}")
                .replace(Regex("""\stext:anchor-type="[^"]*""""), "") + " text:anchor-type=\"${if (wrap == PictureWrap.InLine) "as-char" else "char"}\""
            return head + xml.substring(end)
        }
        val end = xml.indexOf('>')
        val head = xml.substring(0, end).replace(Regex("""\s(text:anchor-type|draw:style-name|svg:x|svg:y)="[^"]*""""), "")
        val at = onPage?.takeIf { wrap != PictureWrap.InLine }?.let { (x, y) -> " svg:x=\"${OdtWriter.fmt(x)}pt\" svg:y=\"${OdtWriter.fmt(y)}pt\"" } ?: ""
        return head + " draw:style-name=\"${odtStyleName(wrap, at.isNotEmpty())}\" text:anchor-type=\"${if (wrap == PictureWrap.InLine) "as-char" else "char"}\"" + at + xml.substring(end)
    }
    /** Placed from the page's corner (Draw on page) rather than its paragraph's: a style of its own. */
    const val ODT_PAGE_REL = "_20_page"
    fun odtStyleName(wrap: PictureWrap, onPage: Boolean = false) = "Tetra_20_wrap_20_${wrap.name}" + if (onPage) ODT_PAGE_REL else ""
    /** Word: placed [onPage] (pt from the page's top left) instead of where it was. */
    fun docxOnPage(xml: String, onPage: Pair<Float, Float>): String = xml.replace(Regex("""<wp:positionH[\s\S]*?</wp:positionV>"""),
        """<wp:positionH relativeFrom="page"><wp:posOffset>${(onPage.first * 12700).toLong()}</wp:posOffset></wp:positionH><wp:positionV relativeFrom="page"><wp:posOffset>${(onPage.second * 12700).toLong()}</wp:posOffset></wp:positionV>""")
    /** [img] wrapped as [wrap] — its XML too, so the pages show it at once (a new picture's is written on saving). */
    fun withWrap(img: DocImage, wrap: PictureWrap, file: OfficeKind): DocImage {
        val raw = img.raw ?: return img.with(wrap = wrap)
        return img.with(raw = if (file == OfficeKind.Docx) docx(raw, wrap) else odt(raw, wrap), wrap = wrap)
    }

    /**
     * Insert › Shapes (asked 2026-09-29): a new shape of [kind] (Word's preset name), [fill] (ARGB, null: none) and
     * [line] (null: none) [lineWidth] pt wide, in line with the text — Word's wps:wsp, or LibreOffice's custom shape
     * whose automatic style (named after its look) the writer adds.
     */
    fun newShape(kind: String, file: OfficeKind, fill: Int?, line: Int?, lineWidth: Float, widthPt: Float, heightPt: Float): String {
        fun hex(c: Int) = "%06X".format(c and 0xFFFFFF)
        if (file == OfficeKind.Odt) {
            val style = "Tetra_20_shape_${fill?.let { "%08X".format(it) } ?: "none"}_${line?.let(::hex) ?: "none"}_${(lineWidth * 100).toInt()}_${PictureWrap.InLine.name}"
            return """<draw:custom-shape draw:style-name="$style" text:anchor-type="as-char" svg:width="${OdtWriter.fmt(widthPt)}pt" svg:height="${OdtWriter.fmt(heightPt)}pt" draw:z-index="0"><text:p/><draw:enhanced-geometry svg:viewBox="0 0 21600 21600" draw:type="${ShapePicture.odfType(kind)}"/></draw:custom-shape>"""
        }
        val cx = (widthPt * 12700).toLong(); val cy = (heightPt * 12700).toLong()
        val id = (System.nanoTime() % 900000 + 100000)
        val fillXml = fill?.let { c -> """<a:solidFill><a:srgbClr val="${hex(c)}">${Math.round((c ushr 24) * 100000 / 255f).takeIf { it < 100000 }?.let { "<a:alpha val=\"$it\"/>" } ?: ""}</a:srgbClr></a:solidFill>""" } ?: "<a:noFill/>"
        val lineXml = """<a:ln w="${(lineWidth * 12700).toLong()}">${line?.let { """<a:solidFill><a:srgbClr val="${hex(it)}"/></a:solidFill>""" } ?: "<a:noFill/>"}</a:ln>"""
        return """<w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"><wp:extent cx="$cx" cy="$cy"/><wp:effectExtent l="0" t="0" r="0" b="0"/><wp:docPr id="$id" name="Shape $id"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"><wps:wsp xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"><wps:cNvSpPr/><wps:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="$kind"><a:avLst/></a:prstGeom>$fillXml$lineXml</wps:spPr><wps:bodyPr/></wps:wsp></a:graphicData></a:graphic></wp:inline></w:drawing>"""
    }
    /** The automatic graphic style a shape Writer made is named for: its fill, outline and wrap. */
    fun odtShapeStyle(name: String): String? {
        val m = Regex("""Tetra_20_shape_(\w+?)_(\w+?)_(\d+)_(\w+)""").matchEntire(name) ?: return null
        val wrap = PictureWrap.entries.firstOrNull { it.name == m.groupValues[4] } ?: PictureWrap.InLine
        val base = Regex("""<style:graphic-properties ([^/]*)/>""").find(odtStyle(wrap))?.groupValues?.get(1) ?: ""
        val fill = m.groupValues[1].takeIf { it != "none" }?.toLong(16)
        val fillXml = if (fill == null) """draw:fill="none"""" else """draw:fill="solid" draw:fill-color="#${"%06X".format(fill and 0xFFFFFF)}"""" +
            Math.round((fill ushr 24) * 100 / 255f).let { if (it < 100) """ draw:opacity="$it%"""" else "" }
        val lineXml = m.groupValues[2].takeIf { it != "none" }?.let { """draw:stroke="solid" svg:stroke-color="#$it" svg:stroke-width="${m.groupValues[3].toInt() / 100f}pt"""" } ?: """draw:stroke="none""""
        return """<style:style style:name="$name" style:family="graphic"><style:graphic-properties $fillXml $lineXml $base/></style:style>"""
    }
    fun odtStyle(wrap: PictureWrap, onPage: Boolean = false): String {
        val props = if (onPage) when (wrap) {
            PictureWrap.Square -> """style:wrap="parallel" style:number-wrapped-paragraphs="no-limit""""
            PictureWrap.TopBottom -> """style:wrap="none""""
            PictureWrap.Behind -> """style:wrap="run-through" style:run-through="background""""
            else -> """style:wrap="run-through" style:run-through="foreground""""
        } + """ style:vertical-pos="from-top" style:vertical-rel="page" style:horizontal-pos="from-left" style:horizontal-rel="page"""" else when (wrap) {
            PictureWrap.InLine -> """style:vertical-pos="top" style:vertical-rel="baseline""""
            else -> when (wrap) {
                PictureWrap.Square -> """style:wrap="parallel" style:number-wrapped-paragraphs="no-limit""""
                PictureWrap.TopBottom -> """style:wrap="none""""
                PictureWrap.Behind -> """style:wrap="run-through" style:run-through="background""""
                else -> """style:wrap="run-through" style:run-through="foreground""""
            } + """ style:vertical-pos="top" style:vertical-rel="paragraph" style:horizontal-pos="left" style:horizontal-rel="paragraph""""
        }
        return """<style:style style:name="${odtStyleName(wrap, onPage)}" style:family="graphic"><style:graphic-properties $props/></style:style>"""
    }
}

/** In a header or footer laid out on the pages: the page's number, the number of pages (private-use characters). */
internal const val PAGE_FIELD = '\uE001'
internal const val PAGES_FIELD = '\uE002'

/** A line break inside a paragraph (Shift+Enter in Word): Unicode's line separator, in a run's text. */
internal const val LINE_BREAK = '\u2028'

/** A list item: bulleted or numbered, how deep (0 = top), and the number it starts at when that is not 1. */
internal data class DocList(val ordered: Boolean, val level: Int, val start: Int? = null)

/**
 * A paragraph's own formatting that Writer does not edit — spacing, indents, tab stops, borders, a page break before it,
 * a style such as Title or Quote, one font for the whole paragraph — carried through as the file had it (asked
 * 2026-09-27), raw: [style] is the file's own style name, [parts] its other properties as (name, XML) in the file's own
 * words, [list] the file's own list it belongs to ("id|pattern", the pattern "n"/"b" for each level).
 */
internal data class DocKeep(val style: String? = null, val parts: List<Pair<String, String>> = emptyList(), val list: String? = null) {
    val isEmpty get() = style == null && parts.isEmpty() && list == null
}

/**
 * Insert › Drop Cap (asked 2026-09-30): the paragraph's first [length] letters [lines] lines tall, beside its first
 * lines ([margin]: out in the margin), [distance] pt from the text. Kept with the paragraph's own formatting: an ODT's
 * style:drop-cap; in Word the letters are a framed paragraph of their own before it — read in, written out again.
 */
internal data class DropCap(val lines: Int = 3, val margin: Boolean = false, val distance: Float = 0f, val length: Int = 1) {
    companion object {
        /** The paragraph's drop cap, from either kind's words; null: none. */
        fun of(keep: DocKeep?): DropCap? {
            val parts = keep?.parts ?: return null
            parts.firstOrNull { it.first == "dropCap" }?.second?.let { v ->
                val (n, xml) = v.split('|', limit = 2)
                fun a(name: String) = Regex("""\sw:$name="([^"]*)"""").find(xml)?.groupValues?.get(1)
                return DropCap(a("lines")?.toIntOrNull()?.coerceIn(1, 10) ?: 3, a("dropCap") == "margin", (a("hSpace")?.toFloatOrNull() ?: 0f) / 20f, n.toIntOrNull()?.coerceAtLeast(1) ?: 1)
            }
            parts.firstOrNull { it.first == "child" && it.second.startsWith("<style:drop-cap") }?.second?.let { xml ->
                fun a(name: String) = Regex("""\sstyle:$name="([^"]*)"""").find(xml)?.groupValues?.get(1)
                val lines = a("lines")?.toIntOrNull() ?: 1
                if (lines < 2) return null // one line: no drop cap
                return DropCap(lines.coerceIn(2, 10), false, odfLength(a("distance")) ?: 0f, a("length")?.toIntOrNull()?.coerceAtLeast(1) ?: 1)
            }
            return null
        }
        /** Is this one of a drop cap's own parts (not carried to a new paragraph)? */
        fun isPart(part: Pair<String, String>) = part.first == "dropCap" || (part.first == "child" && part.second.startsWith("<style:drop-cap"))
    }
}

internal data class DocPara(val runs: List<DocRun>, val heading: Int = 0, val list: DocList? = null, val align: DocAlign = DocAlign.Start, val keep: DocKeep? = null,
    val table: DocTable? = null /* then this "paragraph" is a table, its one run OBJECT_MARK */) {
    val text: String get() = runs.joinToString("") { it.text }
}

/**
 * A table (2026-09-28): its rows of cells, each cell ordinary paragraphs (a table in a cell too). What Writer does not
 * edit is kept raw, in the file's own words: the table's properties and column grid, each row's and cell's properties.
 */
internal data class DocTable(val props: String, val grid: String, val rows: List<DocRow>)
internal data class DocRow(val props: String, val cells: List<DocCell>)
internal data class DocCell(val props: String, val paras: List<DocPara>)

/** A table standing in the text as a paragraph of its own. */
internal fun tablePara(t: DocTable) = DocPara(listOf(DocRun(OBJECT_MARK.toString())), table = t)
/** An OpenDocument table's marks, before a row's or cell's own attributes: a header row, a merged-away cell. */
internal const val ODT_HEADER = "<header/>"
internal const val ODT_COVERED = "<covered/>"
/**
 * A paragraph style Writer offers (asked 2026-09-29): its name as people see it, and how a paragraph points to it —
 * [heading] 1–6 for a heading, else [id] (the file's own; null: the document's plain text, "Normal").
 */
internal data class StyleEntry(val name: String, val id: String?, val heading: Int = 0)

/**
 * Word's Title, Subtitle and Quote, for a document that has not got them: shown at once, and written into its
 * styles.xml when a paragraph takes one ("NORMAL" becomes the document's own plain style). As Word 2016 makes them.
 */
internal val DOCX_BUILTIN = mapOf(
    "Title" to """<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="NORMAL"/><w:next w:val="NORMAL"/><w:uiPriority w:val="10"/><w:qFormat/><w:pPr><w:spacing w:after="80" w:line="240" w:lineRule="auto"/><w:contextualSpacing/></w:pPr><w:rPr><w:spacing w:val="-10"/><w:kern w:val="28"/><w:sz w:val="56"/><w:szCs w:val="56"/></w:rPr></w:style>""",
    "Subtitle" to """<w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/><w:basedOn w:val="NORMAL"/><w:next w:val="NORMAL"/><w:uiPriority w:val="11"/><w:qFormat/><w:pPr><w:spacing w:after="160"/></w:pPr><w:rPr><w:color w:val="595959"/><w:spacing w:val="15"/><w:sz w:val="28"/><w:szCs w:val="28"/></w:rPr></w:style>""",
    "TOCHeading" to """<w:style w:type="paragraph" w:styleId="TOCHeading"><w:name w:val="TOC Heading"/><w:basedOn w:val="NORMAL"/><w:next w:val="NORMAL"/><w:uiPriority w:val="39"/><w:unhideWhenUsed/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="120"/></w:pPr><w:rPr><w:b/><w:sz w:val="32"/><w:szCs w:val="32"/></w:rPr></w:style>""",
    "TOC1" to """<w:style w:type="paragraph" w:styleId="TOC1"><w:name w:val="toc 1"/><w:basedOn w:val="NORMAL"/><w:next w:val="NORMAL"/><w:uiPriority w:val="39"/><w:unhideWhenUsed/><w:pPr><w:spacing w:after="100"/></w:pPr></w:style>""",
    "TOC2" to """<w:style w:type="paragraph" w:styleId="TOC2"><w:name w:val="toc 2"/><w:basedOn w:val="NORMAL"/><w:next w:val="NORMAL"/><w:uiPriority w:val="39"/><w:unhideWhenUsed/><w:pPr><w:spacing w:after="100"/><w:ind w:left="220"/></w:pPr></w:style>""",
    "TOC3" to """<w:style w:type="paragraph" w:styleId="TOC3"><w:name w:val="toc 3"/><w:basedOn w:val="NORMAL"/><w:next w:val="NORMAL"/><w:uiPriority w:val="39"/><w:unhideWhenUsed/><w:pPr><w:spacing w:after="100"/><w:ind w:left="440"/></w:pPr></w:style>""",
    "Quote" to """<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:basedOn w:val="NORMAL"/><w:next w:val="NORMAL"/><w:uiPriority w:val="29"/><w:qFormat/><w:pPr><w:spacing w:before="200" w:after="160"/><w:ind w:left="864" w:right="864"/><w:jc w:val="center"/></w:pPr><w:rPr><w:i/><w:iCs/><w:color w:val="404040"/></w:rPr></w:style>""",
)
/** LibreOffice's Title, Subtitle and Quotations, likewise. */
internal val ODT_BUILTIN = mapOf(
    "Title" to """<style:style style:name="Title" style:family="paragraph" style:parent-style-name="Standard" style:next-style-name="Standard" style:class="chapter"><style:paragraph-properties fo:margin-top="0.423cm" fo:margin-bottom="0.212cm" fo:text-align="center" fo:keep-with-next="always"/><style:text-properties fo:font-size="28pt" fo:font-weight="bold"/></style:style>""",
    "Subtitle" to """<style:style style:name="Subtitle" style:family="paragraph" style:parent-style-name="Standard" style:next-style-name="Standard" style:class="chapter"><style:paragraph-properties fo:margin-top="0.106cm" fo:margin-bottom="0.212cm" fo:text-align="center"/><style:text-properties fo:font-size="18pt"/></style:style>""",
    "Contents_20_Heading" to """<style:style style:name="Contents_20_Heading" style:display-name="Contents Heading" style:family="paragraph" style:parent-style-name="Standard" style:next-style-name="Standard" style:class="index"><style:paragraph-properties fo:margin-top="0.423cm" fo:margin-bottom="0.212cm" fo:keep-with-next="always"/><style:text-properties fo:font-size="16pt" fo:font-weight="bold"/></style:style>""",
    "Contents_20_1" to """<style:style style:name="Contents_20_1" style:display-name="Contents 1" style:family="paragraph" style:parent-style-name="Standard" style:class="index"><style:paragraph-properties fo:margin-left="0cm" fo:margin-bottom="0.176cm"/></style:style>""",
    "Contents_20_2" to """<style:style style:name="Contents_20_2" style:display-name="Contents 2" style:family="paragraph" style:parent-style-name="Standard" style:class="index"><style:paragraph-properties fo:margin-left="0.388cm" fo:margin-bottom="0.176cm"/></style:style>""",
    "Contents_20_3" to """<style:style style:name="Contents_20_3" style:display-name="Contents 3" style:family="paragraph" style:parent-style-name="Standard" style:class="index"><style:paragraph-properties fo:margin-left="0.776cm" fo:margin-bottom="0.176cm"/></style:style>""",
    "Quotations" to """<style:style style:name="Quotations" style:family="paragraph" style:parent-style-name="Standard" style:class="html"><style:paragraph-properties fo:margin-left="1cm" fo:margin-right="1cm" fo:margin-top="0cm" fo:margin-bottom="0.283cm"/></style:style>""",
)

/**
 * A table Writer puts into an OpenDocument file: as wide as the text, its cells lined as a new Word table's are. The
 * styles go into the file with it (automatic styles, written only when a table uses them).
 */
internal const val ODT_TETRA_TABLE = "Tetra_20_table"
internal const val ODT_TETRA_CELL = "Tetra_20_cell"
internal const val ODT_TETRA_STYLES = """<style:style style:name="$ODT_TETRA_TABLE" style:family="table"><style:table-properties style:width="17cm" table:align="margins"/></style:style>""" +
    """<style:style style:name="$ODT_TETRA_CELL" style:family="table-cell"><style:table-cell-properties fo:padding="0.1cm" fo:border="0.5pt solid #000000"/></style:style>"""

/**
 * A document as Writer holds it. [basePt] is the document's normal text size; [unsupported] names, in plain words,
 * what the file has that Writer cannot keep — empty means saving over the file loses nothing.
 */
internal data class OfficeDoc(
    val paras: List<DocPara>,
    val unsupported: Set<String> = emptySet(),
    val basePt: Float = 11f,
    val comments: List<DocComment> = emptyList(),
) {
    /** The comments some letters still carry (the text of one deleted, it goes, as in Word). */
    val liveComments: List<DocComment> get() {
        val used = HashSet<String>()
        fun walk(ps: List<DocPara>) { ps.forEach { p -> p.runs.forEach { used += it.comments }; p.table?.rows?.forEach { r -> r.cells.forEach { walk(it.paras) } } } }
        walk(paras)
        return comments.filter { it.id in used }
    }
    /** What would go missing on saving over the file: content, not just its look. Any of it → Save as a copy only. */
    val blocking: Set<String> get() = unsupported - SIMPLIFIED
    /** What saving over the file simplifies to Writer's own (agreed 2026-09-27, option b): said, not a reason for a copy. */
    val simplified: Set<String> get() = unsupported intersect SIMPLIFIED

    companion object {
        val SIMPLIFIED = setOf("paragraph spacing and indents", "fonts", "tab stops")
    }
}

internal enum class OfficeKind(val extension: String, val mime: String) {
    Docx("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    Odt("odt", "application/vnd.oasis.opendocument.text");

    companion object {
        fun of(name: String): OfficeKind? = entries.firstOrNull { name.endsWith(".${it.extension}", ignoreCase = true) }
    }
}

internal object OfficeFormats {
    /** Reads a document. Throws when the bytes are not a document Writer can read at all (encrypted, damaged…). */
    /** A Word header's or footer's paragraphs, for the pages (page-number fields as [PAGE_FIELD] / [PAGES_FIELD]). */
    internal fun docxPart(parts: Map<String, ByteArray>, path: String): List<DocPara> =
        runCatching { DocxReader(parts).apply { fieldMarks = true }.readPart(path) }.getOrDefault(emptyList())

    /**
     * An OpenDocument's header and footer (its main page style's: Standard, else the first), each "default" and
     * "first" (a title page's), for the pages — page-number fields as [PAGE_FIELD] / [PAGES_FIELD].
     */
    internal fun odtMaster(parts: Map<String, ByteArray>): Pair<Map<String, List<DocPara>>, Map<String, List<DocPara>>> =
        runCatching { OdtReader(parts).apply { fieldMarks = true }.readMaster() }.getOrDefault(emptyMap<String, List<DocPara>>() to emptyMap())

    fun read(bytes: ByteArray, kind: OfficeKind): OfficeDoc {
        val parts = unzip(bytes)
        return when (kind) {
            OfficeKind.Docx -> DocxReader(parts).read()
            OfficeKind.Odt -> OdtReader(parts).read()
        }
    }

    /** The file's bytes: [doc] written into [original] (the file it came from), or into a new blank document. */
    fun write(doc: OfficeDoc, kind: OfficeKind, original: ByteArray? = null): ByteArray = when (kind) {
        OfficeKind.Docx -> DocxWriter.write(doc, original?.let { runCatching { unzip(it) }.getOrNull() }?.takeIf { DocxWriter.canReuse(it) } ?: DocxWriter.blank())
        OfficeKind.Odt -> OdtWriter.write(doc, original?.let { runCatching { unzip(it) }.getOrNull() }?.takeIf { OdtWriter.canReuse(it) } ?: OdtWriter.blank())
    }

    /** Adjacent runs that look the same become one, and empty runs go: what the editor gives back is compared cleanly. */
    fun tidy(runs: List<DocRun>): List<DocRun> {
        val out = ArrayList<DocRun>()
        for (r in runs) {
            if (r.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && last.sameLook(r)) out[out.size - 1] = last.copy(text = last.text + r.text) else out.add(r)
        }
        return out
    }

    /**
     * What the editor gives back has lost each paragraph's [DocKeep]; it is put back from [old] (as read from the file).
     * Paragraphs whose text is unchanged are matched first (longest common run); between two matches, changed
     * paragraphs pair up in order; a new paragraph takes the formatting of the one before it, as Enter does in Word —
     * except a page break before it.
     */
    fun carryKeep(old: List<DocPara>, new: List<DocPara>): List<DocPara> {
        val from = pairParagraphs(old, new)
        val m = new.size
        val matched = BooleanArray(m) { from[it] >= 0 }
        val source = Array(m) { k -> from[k].takeIf { it >= 0 }?.let { old[it].keep } }
        return new.mapIndexed { k, p ->
            val keep = if (matched[k]) source[k] else (k - 1 downTo 0).firstOrNull { matched[it] }?.let { source[it] }?.let { inherited ->
                inherited.copy(parts = inherited.parts.filter { (name, xml) -> name != "pageBreakBefore" && name != "sectPr" && name != "master" && !("break-before" in xml && "page" in xml) && !DropCap.isPart(name to xml) })
            } ?: (k + 1 until m).firstOrNull { matched[it] }?.let { source[it] }?.copy(parts = emptyList())
            p.copy(keep = keep?.takeIf { !it.isEmpty })
        }
    }

    /**
     * A paragraph's spacing set from Writer, in the file's own words (the rest of its formatting as it was): line spacing
     * as a multiple, space before and after, left, first-line and right indent (pt; first line < 0: hanging). null: unchanged.
     */
    fun withSpacing(keep: DocKeep?, kind: OfficeKind, line: Float? = null, before: Float? = null, after: Float? = null,
        left: Float? = null, firstLine: Float? = null, right: Float? = null): DocKeep {
        val k = keep ?: DocKeep()
        val parts = k.parts.toMutableList()
        fun set(xml: String, name: String, value: String?, drop: List<String> = emptyList()): String {
            if (value == null) return xml
            var x = xml
            for (a in drop + name) x = x.replace(Regex("""\s$a="[^"]*""""), "")
            return x.replaceFirst(Regex("""\s*/>$"""), """ $name="$value"/>""")
        }
        if (kind == OfficeKind.Docx) {
            fun tw(pt: Float?) = pt?.let { Math.round(it * 20).toString() }
            fun edit(name: String, change: (String) -> String) {
                val i = parts.indexOfFirst { it.first == name }
                val was = if (i >= 0) parts[i].second else "<w:$name/>"
                if (i >= 0) parts[i] = name to change(was) else parts += name to change(was)
            }
            if (line != null || before != null || after != null) edit("spacing") { x ->
                var s = set(x, "w:before", tw(before), listOf("w:beforeAutospacing", "w:beforeLines"))
                s = set(s, "w:after", tw(after), listOf("w:afterAutospacing", "w:afterLines"))
                if (line != null) s = set(set(s, "w:line", Math.round(line * 240).toString()), "w:lineRule", "auto")
                s
            }
            if (left != null || firstLine != null || right != null) edit("ind") { x ->
                var s = set(x, "w:left", tw(left), listOf("w:start", "w:leftChars", "w:startChars"))
                s = set(s, "w:right", tw(right), listOf("w:end", "w:rightChars", "w:endChars"))
                if (firstLine != null) s = if (firstLine < 0) set(s, "w:hanging", tw(-firstLine), listOf("w:firstLine", "w:hangingChars", "w:firstLineChars"))
                    else set(s, "w:firstLine", tw(firstLine), listOf("w:hanging", "w:hangingChars", "w:firstLineChars"))
                s
            }
        } else {
            fun put(name: String, value: String?, drop: List<String> = emptyList()) {
                if (value == null) return
                parts.removeAll { (kind, xml) -> kind == "@" && (drop + name).any { xml.startsWith(" $it=") } }
                parts += "@" to " $name=\"$value\""
            }
            fun pt(v: Float?) = v?.let { OdtWriter.fmt(it) + "pt" }
            put("fo:line-height", line?.let { "${Math.round(it * 100)}%" }, listOf("style:line-height-at-least", "style:line-spacing"))
            put("fo:margin-top", pt(before)); put("fo:margin-bottom", pt(after))
            put("fo:margin-left", pt(left)); put("fo:text-indent", pt(firstLine)); put("fo:margin-right", pt(right))
        }
        return k.copy(parts = parts)
    }

    /** A paragraph's borders set ([box] null or empty: none), in the file's own words: Word's pBdr, ODT's fo:border-*. */
    fun withBorders(keep: DocKeep?, kind: OfficeKind, box: ParaBox?): DocKeep? {
        val k = keep ?: DocKeep()
        val b = box?.takeIf { !it.isEmpty }
        val rest = k.parts.filter { (name, xml) -> name != "pBdr" && !(name == "@" && (xml.startsWith(" fo:border") || xml.startsWith(" fo:padding") || xml.startsWith(" style:join-border"))) }
        val set = if (b == null) emptyList() else if (kind == OfficeKind.Docx) {
            fun side(tag: String, e: Border?, pad: Float) = e?.let { """<w:$tag w:val="single" w:sz="${Math.round(it.width * 8).coerceIn(2, 96)}" w:space="${Math.round(pad)}" w:color="${if (it.color == 0) "auto" else "%06X".format(it.color and 0xFFFFFF)}"/>""" }.orEmpty()
            listOf("pBdr" to "<w:pBdr>" + side("top", b.top, b.padTop) + side("left", b.left, b.padLeft) + side("bottom", b.bottom, b.padBottom) +
                side("right", b.right, b.padRight) + side("between", b.between, b.padTop) + "</w:pBdr>")
        } else {
            fun side(name: String, e: Border?) = e?.let { "@" to " fo:border-$name=\"${"%.2f".format(java.util.Locale.ROOT, it.width).trimEnd('0').trimEnd('.')}pt solid #${"%06X".format(it.color and 0xFFFFFF)}\"" }
            listOfNotNull(side("top", b.top ?: b.between), side("left", b.left), side("bottom", b.bottom ?: b.between), side("right", b.right)) +
                listOf("@" to " fo:padding-top=\"${OdtWriter.fmt(b.padTop)}pt\"", "@" to " fo:padding-bottom=\"${OdtWriter.fmt(b.padBottom)}pt\"",
                    "@" to " fo:padding-left=\"${OdtWriter.fmt(b.padLeft)}pt\"", "@" to " fo:padding-right=\"${OdtWriter.fmt(b.padRight)}pt\"") +
                // A line between paragraphs: LibreOffice joins two alike into one box unless told not to.
                listOfNotNull(("@" to " style:join-border=\"false\"").takeIf { b.between != null })
        }
        return k.copy(parts = rest + set).takeIf { !it.isEmpty }
    }

    /** [c] put on the letters [spans] (paragraph, from, to — in the paragraph's own text): its runs cut there, tagged. */
    fun withComment(doc: OfficeDoc, spans: List<Triple<Int, Int, Int>>, c: DocComment): OfficeDoc {
        val byPara = spans.groupBy { it.first }
        return doc.copy(comments = doc.comments.filter { it.id != c.id } + c, paras = doc.paras.mapIndexed { i, p ->
            val mine = byPara[i] ?: return@mapIndexed p
            var at = 0
            val runs = ArrayList<DocRun>()
            for (r in p.runs) {
                val cuts = sortedSetOf(0, r.text.length)
                mine.forEach { (_, a, b) -> cuts += (a - at).coerceIn(0, r.text.length); cuts += (b - at).coerceIn(0, r.text.length) }
                cuts.toList().zipWithNext().filter { (x, y) -> y > x }.forEach { (x, y) ->
                    val inside = mine.any { (_, a, b) -> at + x >= a && at + y <= b }
                    runs += r.copy(text = r.text.substring(x, y), comments = if (inside && c.id !in r.comments) r.comments + c.id else r.comments)
                }
                at += r.text.length
            }
            p.copy(runs = tidy(runs))
        })
    }
    /** A comment taken off (its letters stay). */
    fun withoutComment(doc: OfficeDoc, id: String): OfficeDoc {
        fun strip(ps: List<DocPara>): List<DocPara> = ps.map { p -> p.copy(runs = tidy(p.runs.map { r -> if (id in r.comments) r.copy(comments = r.comments - id) else r }),
            table = p.table?.let { t -> t.copy(rows = t.rows.map { row -> row.copy(cells = row.cells.map { cell -> cell.copy(paras = strip(cell.paras)) }) }) }) }
        return doc.copy(paras = strip(doc.paras), comments = doc.comments.filter { it.id != id })
    }
    /** An id no comment of [doc] has: Word's are numbers, ODT's names. */
    fun newCommentId(doc: OfficeDoc, kind: OfficeKind): String = if (kind == OfficeKind.Docx)
        ((doc.comments.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: -1) + 1).toString()
        else generateSequence(1) { it + 1 }.map { "__Annotation__Tetra_$it" }.first { n -> doc.comments.none { it.id == n } }

    /** A drop cap put on the paragraph (null: taken off), in the file's own words (see [DropCap]). */
    fun withDropCap(keep: DocKeep?, kind: OfficeKind, drop: DropCap?): DocKeep? {
        val k = keep ?: DocKeep()
        val rest = k.parts.filter { !DropCap.isPart(it) }
        val set = when {
            drop == null -> emptyList()
            kind == OfficeKind.Docx -> listOf("dropCap" to "${drop.length}|<w:framePr w:dropCap=\"${if (drop.margin) "margin" else "drop"}\" w:lines=\"${drop.lines}\" w:hSpace=\"${Math.round(drop.distance * 20)}\" w:wrap=\"around\" w:vAnchor=\"text\" w:hAnchor=\"${if (drop.margin) "page" else "text"}\"/>")
            else -> listOf("child" to "<style:drop-cap style:lines=\"${drop.lines}\" style:length=\"${drop.length}\" style:distance=\"${OdtWriter.fmt(drop.distance)}pt\"/>")
        }
        // ODT's order inside the paragraph's properties: tab stops, then the drop cap, then a background picture.
        val at = rest.indexOfLast { it.first == "child" && it.second.startsWith("<style:tab-stops") } + 1
        val parts = if (kind == OfficeKind.Odt && set.isNotEmpty() && at > 0) rest.toMutableList().apply { addAll(at, set) } else rest + set
        return k.copy(parts = parts).takeIf { !it.isEmpty }
    }

    /** A page break before the paragraph put in ([on]) or taken out, in the file's own words (Word's pageBreakBefore, ODT's fo:break-before). */
    fun withPageBreak(keep: DocKeep?, kind: OfficeKind, on: Boolean): DocKeep? {
        val k = keep ?: DocKeep()
        val rest = k.parts.filter { (name, xml) -> name != "pageBreakBefore" && !(name == "@" && xml.startsWith(" fo:break-before=")) }
        val set = when { !on -> emptyList(); kind == OfficeKind.Docx -> listOf("pageBreakBefore" to "<w:pageBreakBefore/>"); else -> listOf("@" to " fo:break-before=\"page\"") }
        return k.copy(parts = rest + set).takeIf { !it.isEmpty }
    }

    /**
     * Which paragraph of [old] each paragraph of [new] is (-1: a new one). Unchanged paragraphs are matched first
     * (longest common run); between two matches, changed paragraphs pair up in order. Empty paragraphs are no sign
     * of which is which (found 2026-09-30: after Blank page, text typed on the page after it — two empty paragraphs
     * were matched crosswise and the page break went to the wrong one): they pair in order with the changed ones.
     */
    fun pairParagraphs(old: List<DocPara>, new: List<DocPara>): IntArray {
        val n = old.size; val m = new.size
        fun same(i: Int, j: Int) = old[i].text == new[j].text && old[i].text.isNotBlank()
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0)
            lcs[i][j] = if (same(i, j)) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        val from = IntArray(m) { -1 }
        var i = 0; var j = 0
        val gapOld = ArrayList<Int>(); val gapNew = ArrayList<Int>()
        fun flush() { gapNew.forEachIndexed { k, jj -> gapOld.getOrNull(k)?.let { from[jj] = it } }; gapOld.clear(); gapNew.clear() }
        while (i < n && j < m) {
            when {
                same(i, j) && lcs[i][j] == lcs[i + 1][j + 1] + 1 -> { flush(); from[j] = i; i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> gapOld += i++
                else -> gapNew += j++
            }
        }
        while (i < n) gapOld += i++
        while (j < m) gapNew += j++
        flush()
        return from
    }

    /**
     * The editor has no fonts; each letter's font is put back from [old] (asked 2026-09-27: a font may change anywhere,
     * even inside a word). Letters left as they were keep theirs; a typed letter takes the font of the one before it,
     * as in Word; a new paragraph, the font its previous paragraph ended with.
     */
    /**
     * …and each letter's comments the same way (2026-09-29): a letter typed inside a comment's letters joins it (both its
     * neighbours carry it), one typed at its edge does not; all its letters deleted, the comment is gone.
     */
    fun carryFonts(old: List<DocPara>, new: List<DocPara>): List<DocPara> {
        if (old.none { p -> p.runs.any { it.font != null || it.comments.isNotEmpty() } }) return new
        val from = pairParagraphs(old, new)
        var carried: String? = null // the font the previous paragraph ended with
        return new.mapIndexed { j, p ->
            val text = p.text
            val fonts = arrayOfNulls<String>(text.length)
            val notes = arrayOfNulls<List<String>>(text.length)
            val known = BooleanArray(text.length)
            from[j].takeIf { it >= 0 }?.let { old[it] }?.let { o ->
                val oldText = o.text
                val oldFonts = ArrayList<String?>(oldText.length).apply { o.runs.forEach { r -> repeat(r.text.length) { add(r.font) } } }
                val oldNotes = ArrayList<List<String>>(oldText.length).apply { o.runs.forEach { r -> repeat(r.text.length) { add(r.comments) } } }
                fun take(y: Int, x: Int) { fonts[y] = oldFonts[x]; notes[y] = oldNotes[x]; known[y] = true }
                // Common start and end first; the changed middle by longest common subsequence (small, where one typed).
                var a = 0
                while (a < text.length && a < oldText.length && text[a] == oldText[a]) { take(a, a); a++ }
                var z = 0
                while (z < text.length - a && z < oldText.length - a && text[text.length - 1 - z] == oldText[oldText.length - 1 - z]) {
                    take(text.length - 1 - z, oldText.length - 1 - z); z++
                }
                val nn = text.length - a - z; val on = oldText.length - a - z
                if (nn > 0 && on > 0 && nn.toLong() * on <= 4_000_000) {
                    val l = Array(on + 1) { IntArray(nn + 1) }
                    for (x in on - 1 downTo 0) for (y in nn - 1 downTo 0)
                        l[x][y] = if (oldText[a + x] == text[a + y]) l[x + 1][y + 1] + 1 else maxOf(l[x + 1][y], l[x][y + 1])
                    var x = 0; var y = 0
                    while (x < on && y < nn) when {
                        oldText[a + x] == text[a + y] -> { take(a + y, a + x); x++; y++ }
                        l[x + 1][y] >= l[x][y + 1] -> x++
                        else -> y++
                    }
                }
            }
            // Typed letters: the font before them; at the start of the paragraph, the first known one after.
            var before: String? = if (known.isNotEmpty() && !known[0]) (known.indices.firstOrNull { known[it] }?.let { fonts[it] } ?: carried) else carried
            for (k in text.indices) if (known[k]) before = fonts[k] else fonts[k] = before
            carried = fonts.lastOrNull() ?: carried
            // Typed letters: the comments both known neighbours share.
            run {
                var left: List<String> = emptyList()
                var k = 0
                while (k < text.length) {
                    if (known[k]) { left = notes[k].orEmpty(); k++; continue }
                    var e = k
                    while (e < text.length && !known[e]) e++
                    val right = if (e < text.length) notes[e].orEmpty() else emptyList()
                    val both = left.filter { it in right }
                    for (q in k until e) notes[q] = both
                    k = e
                }
            }
            // The runs again, cut where the font or the comments change.
            var at = 0
            val runs = ArrayList<DocRun>()
            for (r in p.runs) {
                var start = 0
                for (k in 1..r.text.length) if (k == r.text.length || fonts[at + k] != fonts[at + start] || notes[at + k].orEmpty() != notes[at + start].orEmpty()) {
                    if (k > start) runs += r.copy(text = r.text.substring(start, k), font = fonts[at + start], comments = notes[at + start].orEmpty())
                    start = k
                }
                at += r.text.length
            }
            p.copy(runs = tidy(runs))
        }
    }

    // --- shared helpers ---

    /** An element as XML, with the prefixes the file uses (the file's root declares them, and it is kept). */
    internal fun xml(e: Node): String = when (e.nodeType) {
        Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> esc(e.nodeValue)
        Node.ELEMENT_NODE -> buildString {
            append('<').append(e.nodeName)
            val a = e.attributes
            // Namespace declarations made here (Word declares a: on a picture's graphic) go with it.
            for (k in 0 until a.length) a.item(k).let { append(' ').append(it.nodeName).append("=\"").append(esc(it.nodeValue)).append('"') }
            if (!e.hasChildNodes()) append("/>") else {
                append('>')
                var c = e.firstChild
                while (c != null) { append(xml(c)); c = c.nextSibling }
                append("</").append(e.nodeName).append('>')
            }
        }
        else -> ""
    }

    internal fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val parts = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) parts[e.name] = zip.readBytes()
            }
        }
        require(parts.isNotEmpty()) { "Not a document." }
        return parts
    }

    /** A zip of [parts] in their order; [stored] is written first and uncompressed (ODF's mimetype must be). */
    internal fun zip(parts: Map<String, ByteArray>, stored: String? = null): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val names = listOfNotNull(stored?.takeIf { it in parts }) + parts.keys.filter { it != stored }
            for (name in names) {
                val data = parts.getValue(name)
                val e = ZipEntry(name)
                if (name == stored) {
                    e.method = ZipEntry.STORED; e.size = data.size.toLong(); e.compressedSize = data.size.toLong()
                    e.crc = CRC32().apply { update(data) }.value
                }
                zip.putNextEntry(e); zip.write(data); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    internal fun parse(bytes: ByteArray): org.w3c.dom.Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        f.isExpandEntityReferences = false
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    internal fun esc(s: String): String = buildString(s.length) {
        for (c in s) when {
            c == '&' -> append("&amp;")
            c == '<' -> append("&lt;")
            c == '>' -> append("&gt;")
            c == '"' -> append("&quot;")
            c == '\t' || c == '\n' || c == '\r' || c >= ' ' -> if (c != '￾' && c != '￿') append(c)
        }
    }

    internal fun hex(rgb: Int) = "%06X".format(rgb and 0xFFFFFF)
    internal fun parseHex(s: String?): Int? = s?.removePrefix("#")?.takeIf { it.length == 6 }?.toIntOrNull(16)
}

internal fun Element.kids(): List<Element> {
    val out = ArrayList<Element>()
    var n = firstChild
    while (n != null) { if (n is Element) out.add(n); n = n.nextSibling }
    return out
}
internal fun Element.kid(ns: String, name: String): Element? = kids().firstOrNull { it.namespaceURI == ns && it.localName == name }
internal fun Element.attr(ns: String, name: String): String? = getAttributeNS(ns, name).takeIf { hasAttributeNS(ns, name) }

// ============================================================================================ Word (.docx)

internal const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
internal const val RELATIONSHIPS = R
private const val REL_HYPERLINK = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
private const val REL_NUMBERING = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering"
private const val REL_STYLES = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"
private const val REL_IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
private const val REL_COMMENTS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments"

/** A relationship's target, from the folder of the part that points at it, as a path in the package. */
internal fun resolve(dir: String, target: String): String {
    if (target.startsWith("/")) return target.removePrefix("/")
    val out = ArrayList(dir.split('/').filter { it.isNotEmpty() })
    target.split('/').forEach { if (it == "..") out.removeLastOrNull() else if (it != "." && it.isNotEmpty()) out += it }
    return out.joinToString("/")
}

/** Word's fixed highlight colours; a highlight is written as the nearest of them. */
internal val WORD_HIGHLIGHTS = linkedMapOf(
    "yellow" to 0xFFFF00, "green" to 0x00FF00, "cyan" to 0x00FFFF, "magenta" to 0xFF00FF, "blue" to 0x0000FF,
    "red" to 0xFF0000, "darkBlue" to 0x000080, "darkCyan" to 0x008080, "darkGreen" to 0x008000, "darkMagenta" to 0x800080,
    "darkRed" to 0x800000, "darkYellow" to 0x808000, "darkGray" to 0x808080, "lightGray" to 0xC0C0C0, "black" to 0x000000, "white" to 0xFFFFFF,
)

internal fun documentPath(parts: Map<String, ByteArray>): String {
    val rels = parts["_rels/.rels"] ?: return "word/document.xml"
    val root = OfficeFormats.parse(rels).documentElement
    return root.kids().firstOrNull { it.getAttribute("Type").endsWith("/officeDocument") }?.getAttribute("Target")?.removePrefix("/") ?: "word/document.xml"
}
/** Where a document keeps its named styles. */
internal fun stylesPath(parts: Map<String, ByteArray>, kind: OfficeKind) = if (kind == OfficeKind.Odt) "styles.xml"
    else documentPath(parts).substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } + "styles.xml"
internal fun relsPath(part: String) = part.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } + "_rels/" + part.substringAfterLast('/') + ".rels"

private class DocxReader(val parts: Map<String, ByteArray>) {
    val notes = linkedSetOf<String>()
    val docPath = documentPath(parts)
    val dir = docPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    val links = HashMap<String, String>()
    val images = HashMap<String, String>() // relationship id → the picture's part in the package
    val styleName = HashMap<String, String>() // styleId → name, lowercased
    var defaultParaStyle: String? = null
    var basePt = 10f
    val numAbstract = HashMap<String, String>() // numId → abstractNumId
    val numStart = HashMap<Pair<String, Int>, Int>() // (numId, level) → startOverride
    val levels = HashMap<Pair<String, Int>, Pair<String, Int>>() // (abstractNumId, level) → (numFmt, start)
    val counters = HashMap<String, IntArray>() // numId → the count at each level, as Word numbers them
    val openComments = LinkedHashSet<String>() // the comments whose range the reading is in
    val comments = LinkedHashMap<String, DocComment>()
    /** The document's comments (comments.xml), and which have a range in the text; one without is not kept yet. */
    fun readComments(body: Element) {
        val ranged = HashSet<String>()
        val starts = body.getElementsByTagNameNS(W, "commentRangeStart")
        for (i in 0 until starts.length) (starts.item(i) as Element).attr(W, "id")?.let { ranged += it }
        val refs = body.getElementsByTagNameNS(W, "commentReference")
        for (i in 0 until refs.length) if ((refs.item(i) as Element).attr(W, "id") !in ranged) notes += "comments"
        val path = parts[relsPath(docPath)]?.let { r -> OfficeFormats.parse(r).documentElement.kids().firstOrNull { it.getAttribute("Type") == REL_COMMENTS }?.getAttribute("Target") }
            ?.let { resolve(dir, it) } ?: return
        val root = parts[path]?.let { OfficeFormats.parse(it).documentElement } ?: return
        for (c in root.kids().filter { it.localName == "comment" }) {
            val id = c.attr(W, "id") ?: continue
            if (id !in ranged) continue
            comments[id] = DocComment(id, c.attr(W, "author") ?: "", c.attr(W, "date"),
                c.kids().filter { it.localName == "p" }.joinToString("\n") { it.textContent }, OfficeFormats.xml(c), c.attr(W, "initials"))
        }
    }

    /** Reading a header or footer for the pages: its page-number fields become [PAGE_FIELD] / [PAGES_FIELD]. */
    var fieldMarks = false
    private var fieldInstr = StringBuilder()
    private var fieldState = 0 // 0 outside a field, 1 in its instruction, 2 in its shown result
    private fun fieldMark(instr: String): Char? = instr.trim().substringBefore(' ').uppercase().let { if (it == "PAGE") PAGE_FIELD else if (it == "NUMPAGES" || it == "SECTIONPAGES") PAGES_FIELD else null }

    /** A header's or footer's paragraphs, with their own relationships (a logo in a header is the header's picture). */
    fun readPart(path: String): List<DocPara> {
        val root = OfficeFormats.parse(parts[path] ?: return emptyList()).documentElement
        val partDir = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        images.clear(); links.clear()
        parts[relsPath(path)]?.let { r -> OfficeFormats.parse(r).documentElement.kids().forEach {
            when (it.getAttribute("Type")) {
                REL_HYPERLINK -> links[it.getAttribute("Id")] = it.getAttribute("Target")
                REL_IMAGE -> if (it.getAttribute("TargetMode") != "External") images[it.getAttribute("Id")] = resolve(partDir, it.getAttribute("Target"))
            }
        } }
        readStyles(); readNumbering()
        return ArrayList<DocPara>().also { block(root, it) }
    }

    fun read(): OfficeDoc {
        val xml = parts[docPath] ?: error("Not a Word document.")
        val root = OfficeFormats.parse(xml).documentElement
        if (root.namespaceURI != W) error("This Word document is in a format Writer does not read (Strict Open XML).")
        if ("<w:body>" !in String(xml, Charsets.UTF_8)) notes += "an unusual file layout"
        parts[relsPath(docPath)]?.let { r ->
            OfficeFormats.parse(r).documentElement.kids().forEach {
                when (it.getAttribute("Type")) {
                    REL_HYPERLINK -> links[it.getAttribute("Id")] = it.getAttribute("Target")
                    REL_IMAGE -> if (it.getAttribute("TargetMode") != "External") images[it.getAttribute("Id")] = resolve(dir, it.getAttribute("Target"))
                }
            }
        }
        readStyles(); readNumbering()
        val body = root.kid(W, "body") ?: error("Not a Word document.")
        readComments(body)
        val paras = ArrayList<DocPara>()
        block(body, paras)
        return OfficeDoc(joinDropCaps(paras).ifEmpty { listOf(DocPara(emptyList())) }, notes, basePt, comments.values.toList())
    }

    /** A drop cap's framed letter paragraph and the paragraph it drops into, as one paragraph with a [DropCap]. */
    private fun joinDropCaps(paras: List<DocPara>): List<DocPara> {
        val out = ArrayList<DocPara>()
        var i = 0
        while (i < paras.size) {
            val p = paras[i]
            val frame = p.keep?.parts?.firstOrNull { it.first == "dropFrame" }
            val next = paras.getOrNull(i + 1)
            if (frame == null || next == null || next.table != null || p.text.isEmpty()) {
                out += if (frame != null) p.copy(keep = p.keep.copy(parts = p.keep.parts - frame).takeIf { !it.isEmpty }) else p
                i++; continue
            }
            // The letters in the look of the text they begin (their own great size is the drop cap's).
            val letters = p.runs.filter { it.image == null }.map { it.copy(sizePt = null, script = 0) }
            val keep = next.keep ?: DocKeep()
            out += next.copy(runs = OfficeFormats.tidy(letters + next.runs), keep = keep.copy(parts = keep.parts + ("dropCap" to "${p.text.length}|${frame.second}")))
            i += 2
        }
        return out
    }

    /** Paragraph styles that number their paragraphs themselves (Word's List Bullet, List Number…): styleId → numPr. */
    val styleLists: Map<String, Element> by lazy { docxStyleLists(parts, dir) }

    fun readStyles() {
        val root = parts[dir + "styles.xml"]?.let { OfficeFormats.parse(it).documentElement } ?: return
        root.kid(W, "docDefaults")?.kid(W, "rPrDefault")?.kid(W, "rPr")?.kid(W, "sz")?.attr(W, "val")?.toFloatOrNull()?.let { basePt = it / 2 }
        for (s in root.kids().filter { it.localName == "style" }) {
            val id = s.attr(W, "styleId") ?: continue
            styleName[id] = (s.kid(W, "name")?.attr(W, "val") ?: id).lowercase()
            if (s.attr(W, "type") == "paragraph" && s.attr(W, "default").let { it == "1" || it == "true" }) {
                defaultParaStyle = id
                s.kid(W, "rPr")?.kid(W, "sz")?.attr(W, "val")?.toFloatOrNull()?.let { basePt = it / 2 }
            }
        }
    }

    fun readNumbering() {
        val root = parts[dir + "numbering.xml"]?.let { OfficeFormats.parse(it).documentElement } ?: return
        for (e in root.kids()) when (e.localName) {
            "abstractNum" -> {
                val id = e.attr(W, "abstractNumId") ?: continue
                if (e.kid(W, "numStyleLink") != null || e.kid(W, "styleLink") != null) notes += "other list formats"
                for (lvl in e.kids().filter { it.localName == "lvl" }) {
                    val i = lvl.attr(W, "ilvl")?.toIntOrNull() ?: continue
                    levels[id to i] = (lvl.kid(W, "numFmt")?.attr(W, "val") ?: "decimal") to (lvl.kid(W, "start")?.attr(W, "val")?.toIntOrNull() ?: 1)
                }
            }
            "num" -> {
                val id = e.attr(W, "numId") ?: continue
                numAbstract[id] = e.kid(W, "abstractNumId")?.attr(W, "val") ?: continue
                e.kids().filter { it.localName == "lvlOverride" }.forEach { o ->
                    val i = o.attr(W, "ilvl")?.toIntOrNull() ?: return@forEach
                    o.kid(W, "startOverride")?.attr(W, "val")?.toIntOrNull()?.let { numStart[id to i] = it }
                    if (o.kid(W, "lvl") != null) notes += "other list formats"
                }
            }
        }
    }

    /** The body, a table cell or a content control: paragraphs, and what Writer cannot keep, named. */
    fun block(parent: Element, out: MutableList<DocPara>) {
        for (e in parent.kids()) {
            if (e.namespaceURI != W) { if (e.localName != "AlternateContent") notes += "other content" ; continue }
            when (e.localName) {
                "p" -> paragraph(e, out)
                "tbl" -> out += tablePara(table(e))
                "sdt" -> { notes += "content controls"; e.kid(W, "sdtContent")?.let { block(it, out) } }
                "customXml" -> { notes += "content controls"; block(e, out) }
                "ins", "moveTo" -> { notes += "tracked changes"; block(e, out) }
                "del", "moveFrom" -> notes += "tracked changes"
                "sectPr", "bookmarkStart", "bookmarkEnd", "proofErr", "permStart", "permEnd", "tcPr", "trPr", "tblPr", "tblGrid" -> Unit
                "commentRangeStart" -> e.attr(W, "id")?.takeIf { it in comments }?.let { openComments += it } ?: run { notes += "comments" }
                "commentRangeEnd" -> e.attr(W, "id")?.let { openComments -= it }
                "altChunk" -> notes += "embedded objects"
                else -> if (e.localName.endsWith("RangeStart") || e.localName.endsWith("RangeEnd")) notes += "tracked changes" else notes += "other content"
            }
        }
    }

    /** A table: rows of cells of paragraphs; its, its rows' and its cells' properties kept raw. */
    fun table(e: Element): DocTable {
        fun cells(parent: Element): List<DocCell> = parent.kids().flatMap { c ->
            when (c.localName) {
                "tc" -> listOf(DocCell(c.kid(W, "tcPr")?.let { OfficeFormats.xml(it) } ?: "", ArrayList<DocPara>().also { block(c, it) }.ifEmpty { listOf(DocPara(emptyList())) }))
                "sdt" -> { notes += "content controls"; c.kid(W, "sdtContent")?.let { cells(it) }.orEmpty() }
                "customXml" -> { notes += "content controls"; cells(c) }
                else -> emptyList()
            }
        }
        val rows = e.kids().filter { it.localName == "tr" || it.localName == "sdt" || it.localName == "customXml" }.flatMap { tr ->
            if (tr.localName != "tr") { notes += "content controls"; (tr.kid(W, "sdtContent") ?: tr).kids().filter { it.localName == "tr" } } else listOf(tr)
        }.map { tr -> DocRow(tr.kids().filter { it.localName == "tblPrEx" || it.localName == "trPr" }.joinToString("") { OfficeFormats.xml(it) }, cells(tr)) }
        return DocTable(e.kid(W, "tblPr")?.let { OfficeFormats.xml(it) } ?: "", e.kid(W, "tblGrid")?.let { OfficeFormats.xml(it) } ?: "", rows)
    }

    fun paragraph(p: Element, out: MutableList<DocPara>) {
        var heading = 0
        var list: DocList? = null
        var align = DocAlign.Start
        var keepStyle: String? = null
        var ownNumbering = false // the paragraph says itself whether it is a list item (numId 0: it is not)
        val keepParts = ArrayList<Pair<String, String>>()
        p.kid(W, "pPr")?.let { pPr ->
            for (e in pPr.kids()) when (e.localName) {
                "pStyle" -> e.attr(W, "val")?.let { id -> heading = paragraphStyle(id); if (heading == 0 && !plainStyle(id)) keepStyle = id }
                "numPr" -> { list = listItem(e); ownNumbering = true }
                "jc" -> align = when (e.attr(W, "val")) { "center" -> DocAlign.Center; "right", "end" -> DocAlign.End; "both", "distribute", "thaiDistribute" -> DocAlign.Justify; else -> DocAlign.Start }
                // A section break (page size, margins, headers of the part before it): kept as the file has it (2026-09-28).
                "sectPr" -> keepParts += e.localName to OfficeFormats.xml(e)
                // A drop cap's letter (a framed paragraph of its own): joined to the paragraph after it, below.
                "framePr" -> if (e.attr(W, "dropCap").let { it == "drop" || it == "margin" }) keepParts += "dropFrame" to OfficeFormats.xml(e) else notes += "shapes and text boxes"
                "pPrChange" -> notes += "tracked changes"
                // The rest — spacing, indents, tab stops, borders, a page break before it… — goes back as it was.
                else -> keepParts += e.localName to OfficeFormats.xml(e)
            }
        }
        // A list numbered by its style shows as the list it is (asked 2026-09-28: the test suite's bullets did not show).
        if (!ownNumbering && heading == 0) keepStyle?.let { styleLists[it] }?.let { list = listItem(it) }
        val listSource = lastListSource.takeIf { list != null }
        val runs = CommentedRuns(openComments)
        // A page break inside a paragraph: the paragraph is split there and the part after it starts a page — Word's
        // own "page break before", which saves and shows the same (2026-09-28; it made files copy-only before).
        // A break at the paragraph's start: the paragraph starts a page. At its end (or alone in it): the next paragraph
        // does, nothing else is kept (LibreOffice shows nothing there either). In the middle: the part after it starts a
        // page, with no space before it (it is the same paragraph going on, in the file).
        var afterPageBreak = pageBreakPending.also { pageBreakPending = false }
        val done = ArrayList<DocPara>()
        fun keep(): DocKeep? {
            var parts: List<Pair<String, String>> = keepParts
            if (afterPageBreak && parts.none { it.first == "pageBreakBefore" }) parts = parts + ("pageBreakBefore" to "<w:pageBreakBefore/>")
            if (afterPageBreak && done.isNotEmpty()) parts = parts.filter { it.first != "spacing" } +
                ("spacing" to (parts.firstOrNull { it.first == "spacing" }?.second?.replace(Regex("""\sw:before(Autospacing)?="[^"]*""""), "")?.replace("<w:spacing", "<w:spacing w:before=\"0\"") ?: "<w:spacing w:before=\"0\"/>"))
            return DocKeep(keepStyle, parts.sortedBy { PPR_ORDER.indexOf(it.first).let { i -> if (i < 0) PPR_ORDER.indexOf("rPr") else i } }, listSource.takeIf { list != null }).takeIf { !it.isEmpty }
        }
        fun breakLine(page: Boolean = false) {
            if (page && runs.isEmpty() && done.isEmpty()) { afterPageBreak = true; return }
            done += DocPara(OfficeFormats.tidy(runs), heading, list, align, keep()); runs.clear(); list = list?.let { null }; afterPageBreak = page
        }
        fun content(parent: Element, link: String?) {
            for (e in parent.kids()) {
                if (e.namespaceURI != W) { notes += if (e.localName == "oMath" || e.localName == "oMathPara") "equations" else "other content"; continue }
                when (e.localName) {
                    "pPr" -> Unit
                    "r" -> run(e, link, runs, ::breakLine)
                    "hyperlink" -> {
                        val url = e.attr(R, "id")?.let { links[it] }
                        if (url == null) notes += "links inside the document"
                        content(e, url ?: link)
                    }
                    "ins", "moveTo" -> { notes += "tracked changes"; content(e, link) }
                    "del", "moveFrom" -> notes += "tracked changes"
                    "fldSimple" -> { notes += "fields (page numbers, dates, contents)"
                        val mark = if (fieldMarks) fieldMark(e.attr(W, "instr") ?: "") else null
                        if (mark != null) runs += DocRun(mark.toString()) else content(e, link) }
                    "smartTag", "customXml" -> content(e, link)
                    "sdt" -> { notes += "content controls"; e.kid(W, "sdtContent")?.let { content(it, link) } }
                    "commentRangeStart" -> e.attr(W, "id")?.takeIf { it in comments }?.let { openComments += it } ?: run { notes += "comments" }
                    "commentRangeEnd" -> e.attr(W, "id")?.let { openComments -= it }
                    "bookmarkStart", "bookmarkEnd", "proofErr", "permStart", "permEnd", "lastRenderedPageBreak" -> Unit
                    else -> if (e.localName.endsWith("RangeStart") || e.localName.endsWith("RangeEnd")) notes += "tracked changes" else notes += "other content"
                }
            }
        }
        content(p, null)
        if (afterPageBreak && runs.isEmpty() && (done.isNotEmpty() || p.getElementsByTagNameNS(W, "br").length > 0)) pageBreakPending = true
        else done += DocPara(OfficeFormats.tidy(runs), heading, list, align, keep())
        out += done
    }
    /** A page break ended the paragraph before: this one starts a page. */
    var pageBreakPending = false

    /** Reads a run into [runs]; returns its font (the rFonts element) when it names one. */
    fun run(r: Element, link: String?, runs: MutableList<DocRun>, breakLine: (page: Boolean) -> Unit) {
        var look = DocRun("", link = link)
        val kept = StringBuilder() // what Writer carries without editing, as the file has it
        r.kid(W, "rPr")?.let { rPr ->
            for (e in rPr.kids()) {
                val on = e.attr(W, "val").let { it == null || it == "1" || it == "true" || it == "on" }
                when (e.localName) {
                    "b" -> look = look.copy(bold = on)
                    "i" -> look = look.copy(italic = on)
                    "strike" -> look = look.copy(strike = on)
                    "u" -> {
                        look = look.copy(underline = e.attr(W, "val") != "none")
                        if (e.attr(W, "val") !in setOf(null, "single", "none")) kept.append(OfficeFormats.xml(e)) // double, dotted, wavy…
                    }
                    "sz" -> e.attr(W, "val")?.toFloatOrNull()?.let { look = look.copy(sizePt = it / 2) } // a size set on purpose stays, even the normal one (inside a heading it matters)
                    "color" -> look = look.copy(color = OfficeFormats.parseHex(e.attr(W, "val")))
                    "highlight" -> look = look.copy(highlight = WORD_HIGHLIGHTS[e.attr(W, "val")])
                    "rStyle" -> when (styleName[e.attr(W, "val")] ?: e.attr(W, "val")?.lowercase()) {
                        "strong" -> look = look.copy(bold = true)
                        "emphasis" -> look = look.copy(italic = true)
                        "hyperlink", "internet link", "default paragraph font" -> Unit
                        else -> notes += "character styles"
                    }
                    // Kept exactly, letter by letter (asked 2026-09-27 for fonts; 2026-09-28 the rest, from the user's test suite).
                    in RPR_CARRIED -> kept.append(OfficeFormats.xml(e))
                    "vertAlign" -> look = look.copy(script = when (e.attr(W, "val")) { "superscript" -> 1; "subscript" -> -1; else -> 0 })
                    "vanish" -> if (on) notes += "hidden text"
                    "rPrChange" -> notes += "tracked changes"
                    else -> if (e.namespaceURI != W) kept.append(OfficeFormats.xml(e)) // Word 2010's own (w14:ligatures…), last in the list
                        else if (on) notes += "other text effects"
                }
            }
        }
        if (kept.isNotEmpty()) look = look.copy(font = kept.toString())
        for (e in r.kids()) {
            if (e.namespaceURI != W) {
                // Word wraps newer drawings in mc:AlternateContent (a shape, and a fallback for older readers): kept whole.
                if (e.localName == "AlternateContent") runs += look.copy(text = OBJECT_MARK.toString(), image = drawing(e)) else notes += "other content"
                continue
            }
            when (e.localName) {
                "rPr" -> Unit
                "t" -> if (!(fieldMarks && fieldState == 2 && fieldMark(fieldInstr.toString()) != null)) runs += look.copy(text = e.textContent) // a page number's old value: the page's own instead
                "tab", "ptab" -> runs += look.copy(text = "\t")
                "noBreakHyphen" -> runs += look.copy(text = "‑")
                "softHyphen", "lastRenderedPageBreak" -> Unit
                "br", "cr" -> when (e.attr(W, "type")) {
                    "page" -> breakLine(true)
                    "column" -> { notes += "columns"; breakLine(false) }
                    else -> runs += look.copy(text = LINE_BREAK.toString())
                }
                "drawing", "pict", "object" -> if (!(e.localName == "pict" && e.getElementsByTagNameNS("urn:schemas-microsoft-com:vml", "shape").let { l ->
                        (0 until l.length).any { (l.item(it) as Element).getAttribute("id").startsWith(Watermark.NAME) } })) // a watermark: the pages draw it
                    runs += look.copy(text = OBJECT_MARK.toString(), image = drawing(e))
                "fldChar", "instrText" -> {
                    notes += "fields (page numbers, dates, contents)"
                    if (fieldMarks) when {
                        e.localName == "instrText" -> fieldInstr.append(e.textContent)
                        e.attr(W, "fldCharType") == "begin" -> { fieldState = 1; fieldInstr.clear() }
                        e.attr(W, "fldCharType") == "separate" -> { fieldState = 2; fieldMark(fieldInstr.toString())?.let { runs += look.copy(text = it.toString()) } }
                        e.attr(W, "fldCharType") == "end" -> fieldState = 0
                    }
                }
                "delText", "delInstrText" -> notes += "tracked changes"
                "footnoteReference", "endnoteReference" -> notes += "footnotes"
                "commentReference" -> Unit // written again after the comment's range
                "annotationRef" -> notes += "comments"
                "sym" -> notes += "symbols"
                else -> notes += "other content"
            }
        }
    }

    fun paragraphStyle(id: String): Int {
        val name = styleName[id] ?: id.lowercase()
        Regex("""heading ?([1-9])""").matchEntire(name)?.let { m -> return m.groupValues[1].toInt().coerceAtMost(6).also { if (m.groupValues[1].toInt() > 6) notes += "headings below level 6" } }
        return 0
    }

    /** A picture, chart or shape, kept as the file has it: its XML, the picture it shows (if any) and its size. */
    fun drawing(e: Element): DocImage {
        val blip = e.getElementsByTagNameNS("*", "blip").let { if (it.length > 0) it.item(0) as Element else null }
        val relId = blip?.getAttributeNS(R, "embed")?.takeIf { it.isNotEmpty() }
            ?: e.getElementsByTagNameNS("*", "imagedata").let { if (it.length > 0) (it.item(0) as Element).getAttributeNS(R, "id").takeIf { id -> id.isNotEmpty() } else null }
        val part = relId?.let { images[it] }
        val extent = e.getElementsByTagNameNS("*", "extent").let { if (it.length > 0) it.item(0) as Element else null }
        // Word's older drawings (VML) give their size in a style: "width:120pt;height:1.5pt"; a horizontal rule spans the page.
        val vml = e.getElementsByTagNameNS("*", "*").let { all -> (0 until all.length).map { all.item(it) as Element }.firstOrNull { it.hasAttribute("style") && "width" in it.getAttribute("style") } }
        fun vmlLength(key: String): Float? = vml?.getAttribute("style")?.let { Regex("""(?:^|;)\s*$key\s*:\s*([\d.]+)(pt|in|px|cm|mm)?""").find(it) }?.let { m ->
            val n = m.groupValues[1].toFloatOrNull() ?: return@let null
            when (m.groupValues[2]) { "in" -> n * 72; "px" -> n * 0.75f; "cm" -> n * 28.3465f; "mm" -> n * 2.83465f; else -> n }
        }
        val rule = vml?.getAttributeNS("urn:schemas-microsoft-com:office:office", "hr") == "t"
        // Pen strokes (Word's ink): the box is the line's centre, a flat stroke is 0 tall; its picture (the older-Word
        // fallback) is the box with the pen's width around it, so that box is the one it is drawn in (2026-09-29).
        val ink = e.getElementsByTagNameNS("*", "contentPart").length > 0
        val w = (if (ink) vmlLength("width") else null) ?: extent?.getAttribute("cx")?.toLongOrNull()?.let { it / 12700f } ?: vmlLength("width")?.takeIf { it > 0 && !rule } ?: if (rule) 450f else 144f
        val h = (if (ink) vmlLength("height") else null) ?: extent?.getAttribute("cy")?.toLongOrNull()?.let { it / 12700f } ?: vmlLength("height")?.takeIf { it > 0 } ?: 96f
        return DocImage("f${imageCount++}", part, w, h, OfficeFormats.xml(e))
    }
    var imageCount = 0

    /** Normal text: the paragraph needs no style of its own to look as it did. */
    // Not List Paragraph: it spaces and indents differently from Normal (lost, a table of contents lost its gaps — 2026-09-28).
    fun plainStyle(id: String): Boolean = (styleName[id] ?: id.lowercase()).let { id == defaultParaStyle || it == "normal" || it == "default paragraph style" }

    var lastListSource: String? = null

    fun listItem(numPr: Element): DocList? {
        val numId = numPr.kid(W, "numId")?.attr(W, "val") ?: return null
        if (numId == "0") return null
        val level = numPr.kid(W, "ilvl")?.attr(W, "val")?.toIntOrNull() ?: 0
        val abstract = numAbstract[numId] ?: return null
        val (fmt, start) = levels[abstract to level] ?: ("decimal" to 1)
        lastListSource = numId + "|" + (0..8).joinToString("") { l -> if ((levels[abstract to l]?.first ?: "decimal") == "bullet") "b" else "n" }
        val ordered = when (fmt) {
            "bullet" -> false
            "decimal" -> true
            "none" -> { notes += "other list formats"; false }
            else -> { notes += "letter or roman numbering"; true }
        }
        // Word counts on through the whole document for one list; a number is kept by saying where each part starts.
        val c = counters.getOrPut(numId) { IntArray(9) { Int.MIN_VALUE } }
        val first = numStart[numId to level] ?: start
        c[level.coerceIn(0, 8)] = if (c[level.coerceIn(0, 8)] == Int.MIN_VALUE) first else c[level.coerceIn(0, 8)] + 1
        for (deeper in level + 1 until 9) c[deeper] = Int.MIN_VALUE
        return DocList(ordered, level.coerceIn(0, 8), c[level.coerceIn(0, 8)].takeIf { ordered })
    }
}

/** Paragraph styles that number their paragraphs (their own numPr, or one they are based on): styleId → numPr. */
private fun docxStyleLists(parts: Map<String, ByteArray>, dir: String): Map<String, Element> {
    val root = parts[dir + "styles.xml"]?.let { runCatching { OfficeFormats.parse(it).documentElement }.getOrNull() } ?: return emptyMap()
    val styles = root.kids().filter { it.localName == "style" && it.attr(W, "type") == "paragraph" }.associateBy { it.attr(W, "styleId") }
    fun numPr(id: String?, depth: Int = 0): Element? {
        val s = styles[id] ?: return null
        return s.kid(W, "pPr")?.kid(W, "numPr") ?: if (depth < 10) numPr(s.kid(W, "basedOn")?.attr(W, "val"), depth + 1) else null
    }
    return styles.keys.filterNotNull().mapNotNull { id -> numPr(id)?.takeIf { it.kid(W, "numId")?.attr(W, "val").let { n -> n != null && n != "0" } }?.let { id to it } }.toMap()
}

/** The order of a paragraph's properties in Word's schema; Word refuses a file with them out of order. */
internal val PPR_ORDER = listOf("pStyle", "keepNext", "keepLines", "pageBreakBefore", "framePr", "widowControl", "numPr", "suppressLineNumbers", "pBdr",
    "shd", "tabs", "suppressAutoHyphens", "kinsoku", "wordWrap", "overflowPunct", "topLinePunct", "autoSpaceDE", "autoSpaceDN", "bidi", "adjustRightInd",
    "snapToGrid", "spacing", "ind", "contextualSpacing", "mirrorIndents", "suppressOverlap", "jc", "textDirection", "textAlignment", "textboxTightWrap",
    "outlineLvl", "divId", "cnfStyle", "rPr", "sectPr", "pPrChange")

/** The text properties Writer writes itself in ODF (from bold, italic, underline, strike, size, colours, super/subscript). */
private val ODF_WRITTEN = setOf("font-weight", "font-weight-asian", "font-weight-complex", "font-style", "font-style-asian", "font-style-complex",
    "text-underline-style", "text-underline-width", "text-underline-color", "text-line-through-style", "text-line-through-type",
    "font-size", "font-size-asian", "font-size-complex", "color", "background-color", "text-position")

/** A run's properties in the order Word's schema requires. */
internal val RPR_ORDER = listOf("rStyle", "rFonts", "b", "bCs", "i", "iCs", "caps", "smallCaps", "strike", "dstrike", "outline", "shadow", "emboss",
    "imprint", "noProof", "snapToGrid", "vanish", "webHidden", "color", "spacing", "w", "kern", "position", "sz", "szCs", "highlight", "u", "effect",
    "bdr", "shd", "fitText", "vertAlign", "rtl", "cs", "em", "lang", "eastAsianLayout", "specVanish", "oMath")
/** The run properties Writer carries as the file has them, without editing them. */
private val RPR_CARRIED = setOf("rFonts", "bCs", "iCs", "caps", "smallCaps", "dstrike", "outline", "shadow", "emboss", "imprint", "noProof", "snapToGrid",
    "webHidden", "spacing", "w", "kern", "position", "szCs", "effect", "bdr", "shd", "fitText", "rtl", "cs", "em", "lang", "eastAsianLayout", "specVanish")
/** One element of carried XML, with its children. */
private val RAW_ELEMENT = Regex("""<(\w+:\w+)\b[^>]*?(?:/>|>[\s\S]*?</\1>)""")

private object DocxWriter {
    fun canReuse(parts: Map<String, ByteArray>): Boolean = runCatching {
        String(parts.getValue(documentPath(parts)), Charsets.UTF_8).let { "<w:body>" in it && "</w:body>" in it }
    }.getOrDefault(false)

    fun blank(): LinkedHashMap<String, ByteArray> {
        val ns = """xmlns:w="$W" xmlns:r="$R""""
        fun heading(n: Int, halfPoints: Int) = """<w:style w:type="paragraph" w:styleId="Heading$n"><w:name w:val="heading $n"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:uiPriority w:val="9"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="80"/><w:outlineLvl w:val="${n - 1}"/></w:pPr><w:rPr><w:b/><w:sz w:val="$halfPoints"/></w:rPr></w:style>"""
        val m = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
            "word/_rels/document.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$REL_STYLES" Target="styles.xml"/><Relationship Id="rId2" Type="$REL_NUMBERING" Target="numbering.xml"/></Relationships>""",
            "word/document.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document $ns><w:body><w:p/><w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>""",
            "word/styles.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles $ns><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Calibri" w:cs="Calibri"/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style><w:style w:type="character" w:default="1" w:styleId="DefaultParagraphFont"><w:name w:val="Default Paragraph Font"/><w:uiPriority w:val="1"/><w:semiHidden/></w:style><w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:ind w:left="720"/><w:contextualSpacing/></w:pPr></w:style>${heading(1, 36)}${heading(2, 30)}${heading(3, 26)}${heading(4, 24)}${heading(5, 22)}${heading(6, 22)}<w:style w:type="character" w:styleId="Hyperlink"><w:name w:val="Hyperlink"/><w:basedOn w:val="DefaultParagraphFont"/><w:rPr><w:color w:val="0563C1"/><w:u w:val="single"/></w:rPr></w:style></w:styles>""",
            "word/numbering.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:numbering $ns></w:numbering>""",
        )
        return LinkedHashMap(m.mapValues { it.value.toByteArray() })
    }

    fun write(doc: OfficeDoc, source: LinkedHashMap<String, ByteArray>): ByteArray {
        val parts = LinkedHashMap(source)
        val docPath = documentPath(parts)
        val dir = docPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val styleLists = docxStyleLists(parts, dir)
        val relsPath = relsPath(docPath)
        var rels = parts[relsPath]?.let { String(it, Charsets.UTF_8) }
            ?: """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>"""
        var types = String(parts.getValue("[Content_Types].xml"), Charsets.UTF_8)
        val usedIds = Regex("""Id="([^"]+)"""").findAll(rels).map { it.groupValues[1] }.toMutableSet()
        fun newRel(type: String, target: String, external: Boolean): String {
            val id = generateSequence(1) { it + 1 }.map { "rIdT$it" }.first { it !in usedIds }
            usedIds += id
            rels = rels.replace("</Relationships>", """<Relationship Id="$id" Type="$type" Target="${OfficeFormats.esc(target)}"${if (external) " TargetMode=\"External\"" else ""}/></Relationships>""")
            return id
        }
        fun ensurePart(name: String, relType: String, contentType: String, empty: String) {
            if (dir + name in parts) return
            parts[dir + name] = empty.toByteArray()
            if (!rels.contains("Target=\"$name\"")) newRel(relType, name, false)
            if (!types.contains("PartName=\"/$dir$name\"")) types = types.replace("</Types>", """<Override PartName="/$dir$name" ContentType="$contentType"/></Types>""")
        }
        val ns = """xmlns:w="$W" xmlns:r="$R""""
        ensurePart("styles.xml", REL_STYLES, "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles $ns></w:styles>""")
        if (doc.paras.any { it.list != null })
            ensurePart("numbering.xml", REL_NUMBERING, "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:numbering $ns></w:numbering>""")

        // Styles: the headings and Hyperlink this document already has, found by name; any missing are added.
        var styles = String(parts.getValue(dir + "styles.xml"), Charsets.UTF_8)
        val styleRoot = OfficeFormats.parse(styles.toByteArray()).documentElement
        val byName = styleRoot.kids().filter { it.localName == "style" }.associate { (it.kid(W, "name")?.attr(W, "val") ?: "").lowercase() to (it.attr(W, "styleId") ?: "") }
        val ids = byName.values.toMutableSet()
        val normal = styleRoot.kids().firstOrNull { it.localName == "style" && it.attr(W, "type") == "paragraph" && it.attr(W, "default").let { d -> d == "1" || d == "true" } }?.attr(W, "styleId")
        val allParas = ArrayList<DocPara>().also { all ->
            fun add(paras: List<DocPara>) {
                paras.forEach { p -> all += p; p.table?.rows?.forEach { row -> row.cells.forEach { cell -> add(cell.paras) } } }
            }
            add(doc.paras)
        }
        fun styleId(name: String, preferred: String, definition: (String) -> String): String = byName[name] ?: run {
            val id = if (preferred in ids) "Tetra$preferred" else preferred
            ids += id
            styles = styles.replace("</w:styles>", definition(id) + "</w:styles>")
            id
        }
        val headingIds = (1..6).filter { n -> allParas.any { it.heading == n } }.associateWith { n ->
            styleId("heading $n", "Heading$n") { id -> docxHeadingStyle(n, id, normal) }
        }
        // Title, Subtitle, Quote: added when a paragraph took one the document had not got.
        allParas.mapNotNull { it.keep?.style }.toSet().filter { it in DOCX_BUILTIN && it !in ids }.forEach { id ->
            ids += id
            styles = styles.replace("</w:styles>", DOCX_BUILTIN.getValue(id).let { d -> if (normal != null) d.replace("NORMAL", normal) else d.replace(Regex("""<w:(basedOn|next) w:val="NORMAL"/>"""), "") } + "</w:styles>")
        }
        val hyperlinkId = if (allParas.any { p -> p.runs.any { it.link != null } })
            styleId("hyperlink", "Hyperlink") { id -> """<w:style w:type="character" w:styleId="$id"><w:name w:val="Hyperlink"/><w:rPr><w:color w:val="0563C1"/><w:u w:val="single"/></w:rPr></w:style>""" } else null
        parts[dir + "styles.xml"] = styles.toByteArray()

        // Lists: two new list definitions (bullets, numbers) and one numbering instance per list, so each numbered
        // list counts from its own start, as it did in the editor.
        val numIds = HashMap<Int, String>() // paragraph index → numId
        if (doc.paras.any { it.list != null }) {
            var numbering = String(parts.getValue(dir + "numbering.xml"), Charsets.UTF_8)
            val nroot = OfficeFormats.parse(numbering.toByteArray()).documentElement
            var nextAbstract = (nroot.kids().filter { it.localName == "abstractNum" }.mapNotNull { it.attr(W, "abstractNumId")?.toIntOrNull() }.maxOrNull() ?: 0) + 1
            var nextNum = (nroot.kids().filter { it.localName == "num" }.mapNotNull { it.attr(W, "numId")?.toIntOrNull() }.maxOrNull() ?: 0) + 1
            val bulletChars = listOf("•", "◦", "▪")
            fun abstractNum(id: Int, ordered: Boolean) = buildString {
                append("""<w:abstractNum w:abstractNumId="$id"><w:multiLevelType w:val="hybridMultilevel"/>""")
                for (l in 0..8) {
                    val indent = 720 * (l + 1)
                    if (ordered) append("""<w:lvl w:ilvl="$l"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%${l + 1}."/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="$indent" w:hanging="360"/></w:pPr></w:lvl>""")
                    else append("""<w:lvl w:ilvl="$l"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="${bulletChars[l % 3]}"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="$indent" w:hanging="360"/></w:pPr></w:lvl>""")
                }
                append("</w:abstractNum>")
            }
            val bulletAbstract = nextAbstract++
            val orderedAbstract = nextAbstract++
            val nums = StringBuilder()
            fun num(abstract: Int, start: Int?): String {
                val id = nextNum++
                nums.append("""<w:num w:numId="$id"><w:abstractNumId w:val="$abstract"/>""")
                if (start != null && start != 1) nums.append("""<w:lvlOverride w:ilvl="0"><w:startOverride w:val="$start"/></w:lvlOverride>""")
                nums.append("</w:num>")
                return id.toString()
            }
            val bulletNum = num(bulletAbstract, null)
            var i = 0
            while (i < doc.paras.size) {
                if (doc.paras[i].list == null) { i++; continue }
                var end = i
                while (end < doc.paras.size && doc.paras[end].list != null) end++
                val start = doc.paras.subList(i, end).firstOrNull { it.list!!.ordered && it.list.level == 0 }?.list?.start
                // Each item keeps the file's own list when it is still of that list's kind at its level (Word then numbers
                // it as before); any other item gets Writer's own list.
                fun own(k: Int): String? = doc.paras[k].keep?.list?.takeIf { src ->
                    val l = doc.paras[k].list!!
                    src.substringAfter('|').getOrNull(l.level)?.let { c -> (c == 'n') == l.ordered } == true
                }?.substringBefore('|')
                val orderedNum = if ((i until end).any { doc.paras[it].list!!.ordered && own(it) == null }) num(orderedAbstract, start) else null
                for (k in i until end) numIds[k] = own(k) ?: if (doc.paras[k].list!!.ordered) orderedNum!! else bulletNum
                i = end
            }
            val abstracts = abstractNum(bulletAbstract, false) + abstractNum(orderedAbstract, true)
            // Schema order: every abstractNum before the first num.
            val firstNum = Regex("""<w:num[\s>]""").find(numbering)?.range?.first
            numbering = if (firstNum != null) numbering.substring(0, firstNum) + abstracts + numbering.substring(firstNum) else numbering.replace("</w:numbering>", "$abstracts</w:numbering>")
            val cleanup = numbering.indexOf("<w:numIdMacAtCleanup").takeIf { it >= 0 } ?: numbering.lastIndexOf("</w:numbering>")
            numbering = numbering.substring(0, cleanup) + nums + numbering.substring(cleanup)
            parts[dir + "numbering.xml"] = numbering.toByteArray()
        }

        // Pictures added in Writer: each a new part in word/media, a relationship, and an inline drawing.
        var docPrId = 9000
        picture = { img -> (img.raw ?: run {
                val bytes = img.bytes ?: return@run ""
                val name = generateSequence(1) { it + 1 }.map { "${dir}media/tetra-$it.${img.extension}" }.first { it !in parts }
                parts[name] = bytes
                if (!Regex("""Extension="${img.extension}"""", RegexOption.IGNORE_CASE).containsMatchIn(types))
                    types = types.replace("</Types>", """<Default Extension="${img.extension}" ContentType="image/${if (img.extension == "jpg") "jpeg" else img.extension}"/></Types>""")
                val rel = newRel(REL_IMAGE, name.removePrefix(dir), false)
                val cx = (img.widthPt * 12700).toLong(); val cy = (img.heightPt * 12700).toLong(); val id = docPrId++
                """<w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="$id" name="Picture $id"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="$id" name="Picture $id"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="$rel" xmlns:r="$R"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing>"""
            }).let { x -> img.wrap?.let { w -> PictureXml.docx(x, w).let { d -> img.onPage?.takeIf { w != PictureWrap.InLine }?.let { PictureXml.docxOnPage(d, it) } ?: d } } ?: x }
        }

        // The body: the new paragraphs, then the original page setup (the last sectPr).
        val xml = String(parts.getValue(docPath), Charsets.UTF_8)
        val bodyStart = xml.indexOf("<w:body>") + "<w:body>".length
        val bodyEnd = xml.lastIndexOf("</w:body>")
        val sect = xml.lastIndexOf("<w:sectPr", bodyEnd).takeIf { it >= bodyStart }?.let { xml.substring(it, bodyEnd) } ?: ""
        val linkIds = HashMap<String, String>()
        val body = StringBuilder()
        /** A table: its rows and cells, their properties as the file had them, the cells' paragraphs written as the body's. */
        fun table(t: DocTable, paragraph: (DocPara) -> Unit) {
            body.append("<w:tbl>").append(t.props).append(t.grid)
            for (row in t.rows) {
                body.append("<w:tr>").append(row.props)
                for (cell in row.cells) {
                    body.append("<w:tc>").append(cell.props)
                    cell.paras.forEach(paragraph)
                    // Word wants a paragraph last in a cell (after a table in it, too).
                    if (cell.paras.lastOrNull()?.let { it.table != null } != false) body.append("<w:p/>")
                    body.append("</w:tc>")
                }
                body.append("</w:tr>")
            }
            body.append("</w:tbl>")
        }
        // Comments: their ranges around the letters they are on, then their part.
        val live = doc.liveComments.associateBy { it.id }
        val openComments = LinkedHashSet<String>()
        val closedComments = HashSet<String>() // started once: never again
        val nextFirst = java.util.IdentityHashMap<DocPara, DocPara?>().also { m ->
            var prev: DocPara? = null
            fun walk(ps: List<DocPara>) { ps.forEach { q -> if (q.table != null) q.table.rows.forEach { r -> r.cells.forEach { walk(it.paras) } } else { prev?.let { m[it] = q }; prev = q } } }
            walk(doc.paras)
        }
        /** Before letters carrying [now]: the comments they leave end (and are referenced), those they enter start. */
        fun commentMarks(now: List<String>) {
            for (id in openComments.filter { it !in now }) {
                body.append("""<w:commentRangeEnd w:id="$id"/><w:r><w:commentReference w:id="$id"/></w:r>""")
                openComments -= id
            }
            // One stretch each: a comment that ended is not started again (its letters further on lose it).
            for (id in now) if (id !in openComments && id in live && closedComments.add(id)) { body.append("""<w:commentRangeStart w:id="$id"/>"""); openComments += id }
        }
        fun paragraph(index: Int?, p: DocPara) {
            p.table?.let { t -> table(t) { paragraph(null, it) }; return }
            // A drop cap: its letters first, in a framed paragraph of their own as Word writes one.
            p.keep?.parts?.firstOrNull { it.first == "dropCap" }?.let { part ->
                val drop = DropCap.of(p.keep)!!
                val frame = part.second.substringAfter('|')
                val runs = OfficeFormats.tidy(p.runs)
                val letters = ArrayList<DocRun>(); val rest = ArrayList<DocRun>()
                var left = drop.length
                for (r in runs) when {
                    left <= 0 || r.image != null -> rest += r
                    r.text.length <= left -> { letters += r; left -= r.text.length }
                    else -> { letters += r.copy(text = r.text.take(left)); rest += r.copy(text = r.text.drop(left)); left = 0 }
                }
                if (letters.isNotEmpty()) {
                    // Word's sizes: the letter's capitals as tall as the lines it drops through, a line of that height.
                    val base = letters.first().sizePt ?: doc.basePt
                    val line = base * 1.2f * 1.08f
                    val size = ((drop.lines - 1) * line + base * 0.66f) / 0.66f
                    val style = (if (p.heading > 0) headingIds.getValue(p.heading) else p.keep.style)?.let { """<w:pStyle w:val="${OfficeFormats.esc(it)}"/>""" } ?: ""
                    body.append("<w:p><w:pPr>$style<w:keepNext/>$frame<w:spacing w:before=\"0\" w:after=\"0\" w:line=\"${Math.round(drop.lines * line * 20)}\" w:lineRule=\"exact\"/><w:textAlignment w:val=\"baseline\"/></w:pPr>")
                    for (r in letters) body.append(run(r.copy(sizePt = Math.round(size * 2) / 2f), null, doc.basePt).replaceFirst("<w:sz ", "<w:position w:val=\"-${Math.round(size * 0.2f)}\"/><w:sz "))
                    body.append("</w:p>")
                }
                return paragraph(index, p.copy(runs = rest, keep = p.keep.copy(parts = p.keep.parts - part).takeIf { !it.isEmpty }))
            }
            body.append("<w:p>")
            // Writer's own properties and the ones carried from the file, in the order Word requires.
            val props = ArrayList(p.keep?.parts.orEmpty().filter { it.first != "runFonts" })
            (if (p.heading > 0) headingIds.getValue(p.heading) else p.keep?.style)?.let { props += "pStyle" to """<w:pStyle w:val="${OfficeFormats.esc(it)}"/>""" }
            // A list item in a table's cell keeps the file's own list (Writer's own lists are numbered for the body's).
            p.list?.let { l -> (index?.let { numIds[it] } ?: p.keep?.list?.substringBefore('|'))?.let { props += "numPr" to """<w:numPr><w:ilvl w:val="${l.level}"/><w:numId w:val="$it"/></w:numPr>""" } }
            // A list taken off a paragraph whose style numbers it: said so, or its style would number it still.
                ?: p.keep?.style?.takeIf { it in styleLists }?.let { props += "numPr" to """<w:numPr><w:numId w:val="0"/></w:numPr>""" }
            when (p.align) {
                DocAlign.Center -> props += "jc" to """<w:jc w:val="center"/>"""
                DocAlign.End -> props += "jc" to """<w:jc w:val="right"/>"""
                DocAlign.Justify -> props += "jc" to """<w:jc w:val="both"/>"""
                DocAlign.Start -> Unit
            }
            if (props.isNotEmpty()) {
                body.append("<w:pPr>")
                props.sortedBy { (name, _) -> PPR_ORDER.indexOf(name).let { if (it < 0) PPR_ORDER.indexOf("rPr") else it } }.forEach { body.append(it.second) }
                body.append("</w:pPr>")
            }
            var k = 0
            val runs = OfficeFormats.tidy(p.runs)
            while (k < runs.size) {
                val link = runs[k].link
                var end = k
                while (end < runs.size && runs[end].link == link) end++
                if (link != null) body.append("""<w:hyperlink r:id="${linkIds.getOrPut(link) { newRel(REL_HYPERLINK, link, true) }}">""")
                for (r in runs.subList(k, end)) { commentMarks(r.comments); body.append(run(r, if (link != null) hyperlinkId else null, doc.basePt)) }
                if (link != null) body.append("</w:hyperlink>")
                k = end
            }
            // A comment ends here unless the next paragraph goes on in it.
            commentMarks(nextFirst[p]?.runs?.firstOrNull()?.comments.orEmpty().filter { it in openComments })
            body.append("</w:p>")
        }
        doc.paras.forEachIndexed { index, p -> paragraph(index, p) }
        if (openComments.isNotEmpty()) { body.append("<w:p>"); commentMarks(emptyList()); body.append("</w:p>") }
        writeComments(parts, dir, rels, live.values.toList(), ::ensurePart)
        var out = xml.substring(0, bodyStart) + body + sect + xml.substring(bodyEnd)
        if (linkIds.isNotEmpty() && !Regex("""<w:document[^>]*xmlns:r=""").containsMatchIn(out)) out = out.replaceFirst("<w:document", "<w:document xmlns:r=\"$R\"")
        parts[docPath] = out.toByteArray()
        parts[relsPath] = rels.toByteArray()
        parts["[Content_Types].xml"] = types.toByteArray()
        return OfficeFormats.zip(parts)
    }

    /**
     * comments.xml: the comments still on some letters — each from the file as it was, a new one written out. What the
     * file says about a comment gone (Word's replies/resolved in commentsExtended, commentsIds) is taken out with it.
     */
    private fun writeComments(parts: LinkedHashMap<String, ByteArray>, dir: String, rels: String, live: List<DocComment>,
        ensurePart: (String, String, String, String) -> Unit) {
        val target = Regex("""<Relationship\b[^>]*Type="$REL_COMMENTS"[^>]*>""").find(rels)?.value?.let { Regex("""Target="([^"]+)"""").find(it)?.groupValues?.get(1) }
        if (target == null && live.isEmpty()) return
        if (target == null) ensurePart("comments.xml", REL_COMMENTS, "application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml", "")
        val path = resolve(dir, target ?: "comments.xml")
        val old = parts[path]?.let { String(it, Charsets.UTF_8) }.orEmpty()
        val root = Regex("""<w:comments\b[^>]*>""").find(old)?.value ?: """<w:comments xmlns:w="$W">"""
        val gone = Regex("""<w:comment\b[^>]*w:id="([^"]+)"[\s\S]*?</w:comment>""").findAll(old).filter { m -> live.none { it.id == m.groupValues[1] } }
            .flatMap { m -> Regex("""w14:paraId="([0-9A-Fa-f]+)"""").findAll(m.value).map { it.groupValues[1] } }.toSet()
        parts[path] = ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" + root + live.joinToString("") { c ->
            c.raw ?: """<w:comment w:id="${c.id}" w:author="${OfficeFormats.esc(c.author)}"${c.date?.let { " w:date=\"$it\"" } ?: ""}${c.initials?.let { " w:initials=\"${OfficeFormats.esc(it)}\"" } ?: ""}>""" +
                c.text.split('\n').joinToString("") { line -> """<w:p><w:r><w:t xml:space="preserve">${OfficeFormats.esc(line)}</w:t></w:r></w:p>""" } + "</w:comment>"
        } + "</w:comments>").toByteArray()
        if (gone.isNotEmpty()) for (name in listOf("commentsExtended.xml", "commentsIds.xml", "commentsExtensible.xml")) {
            val p = parts.keys.firstOrNull { it.endsWith("/$name") || it == name } ?: continue
            parts[p] = String(parts.getValue(p), Charsets.UTF_8).replace(Regex("""<w1[56]\w*:comment\w*\b[^>]*?(?:paraId|durableId)="([0-9A-Fa-f]+)"[^>]*?/>""")) { m ->
                if (gone.any { it in m.value }) "" else m.value }.toByteArray()
        }
    }

    /** A picture's XML: the file's own for one from the file; a new one, written in full, for one added in Writer. */
    var picture: (DocImage) -> String = { it.raw ?: "" }

    private fun run(r: DocRun, style: String?, basePt: Float): String = buildString {
        // Writer's own settings and the ones carried from the file, in the order Word requires.
        val props = ArrayList<Pair<String, String>>()
        r.font?.let { raw -> RAW_ELEMENT.findAll(raw).forEach { m -> val name = m.groupValues[1].removePrefix("w:"); if (name != "u" || r.underline) props += name to m.value } }
        style?.let { props += "rStyle" to """<w:rStyle w:val="$it"/>""" }
        if (r.bold) props += "b" to "<w:b/>"
        if (r.italic) props += "i" to "<w:i/>"
        if (r.strike) props += "strike" to "<w:strike/>"
        r.color?.let { props += "color" to """<w:color w:val="${OfficeFormats.hex(it)}"/>""" }
        r.sizePt?.let { props += "sz" to """<w:sz w:val="${(it * 2).roundToInt()}"/>""" }
        r.highlight?.let { props += "highlight" to """<w:highlight w:val="${nearestHighlight(it)}"/>""" }
        if (r.underline && props.none { it.first == "u" }) props += "u" to """<w:u w:val="single"/>"""
        if (r.script != 0) props += "vertAlign" to """<w:vertAlign w:val="${if (r.script > 0) "superscript" else "subscript"}"/>"""
        val rPr = props.sortedBy { RPR_ORDER.indexOf(it.first).let { i -> if (i < 0) RPR_ORDER.size else i } }.joinToString("") { it.second } // extensions last
        append("<w:r>")
        if (rPr.isNotEmpty()) append("<w:rPr>").append(rPr).append("</w:rPr>")
        if (r.image != null) { append(picture(r.image)); append("</w:r>"); return@buildString }
        Regex("""[\t$LINE_BREAK]|[^\t$LINE_BREAK]+""").findAll(r.text).forEach { m ->
            when (m.value) {
                "\t" -> append("<w:tab/>")
                LINE_BREAK.toString() -> append("<w:br/>")
                else -> append("""<w:t xml:space="preserve">""").append(OfficeFormats.esc(m.value)).append("</w:t>")
            }
        }
        append("</w:r>")
    }

    fun nearestHighlight(rgb: Int): String = WORD_HIGHLIGHTS.minBy { (_, c) ->
        val dr = (c shr 16 and 255) - (rgb shr 16 and 255); val dg = (c shr 8 and 255) - (rgb shr 8 and 255); val db = (c and 255) - (rgb and 255)
        dr * dr + dg * dg + db * db
    }.key
}

// ============================================================================================ OpenDocument (.odt)

internal const val OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
internal const val DC = "http://purl.org/dc/elements/1.1/"
internal const val TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
internal const val STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
internal const val FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
private const val XLINK = "http://www.w3.org/1999/xlink"
internal const val TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
internal const val SVG = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"

/** LibreOffice's name for a style ("Heading_20_1") as people see it ("Heading 1"). */
internal fun odfDisplayName(name: String) = Regex("""_([0-9a-fA-F]{2})_""").replace(name) { it.groupValues[1].toInt(16).toChar().toString() }

private class OdtStyle(val name: String, val family: String, val parent: String?, val listStyle: String?, val masterPage: String?, val text: Element?, val paragraph: Element?, val automatic: Boolean)

private class OdtReader(val parts: Map<String, ByteArray>) {
    val notes = linkedSetOf<String>()
    val styles = HashMap<String, OdtStyle>() // "family/name"
    val listStyles = HashMap<String, Element>()
    var basePt = 12f
    val listCounters = HashMap<String, IntArray>() // list style → the count at each level, for a list that continues
    val openComments = LinkedHashSet<String>()
    val comments = LinkedHashMap<String, DocComment>()
    var ended = emptySet<String>() // the annotations with an end: a comment on some letters (one without is on a point)

    fun read(): OfficeDoc {
        val content = OfficeFormats.parse(parts["content.xml"] ?: error("Not an OpenDocument text.")).documentElement
        parts["styles.xml"]?.let { OfficeFormats.parse(it).documentElement }?.let { root ->
            root.kid(OFFICE, "styles")?.let { collect(it, false) }
            root.kid(OFFICE, "styles")?.kids()?.firstOrNull { it.localName == "default-style" && it.attr(STYLE, "family") == "paragraph" }
                ?.kid(STYLE, "text-properties")?.attr(FO, "font-size")?.let { size(it, 12f) }?.let { basePt = it }
            styles["paragraph/Standard"]?.text?.attr(FO, "font-size")?.let { size(it, basePt) }?.let { basePt = it }
        }
        content.kid(OFFICE, "automatic-styles")?.let { collect(it, true) }
        val text = content.kid(OFFICE, "body")?.kid(OFFICE, "text") ?: error("Not an OpenDocument text.")
        ended = text.getElementsByTagNameNS(OFFICE, "annotation-end").let { l -> (0 until l.length).mapNotNull { (l.item(it) as Element).attr(OFFICE, "name") }.toSet() }
        val out = ArrayList<DocPara>()
        block(text, out, null, -1)
        return OfficeDoc(out.ifEmpty { listOf(DocPara(emptyList())) }, notes, basePt, comments.values.toList())
    }

    /** Reading a header or footer: its page number and count become [PAGE_FIELD] / [PAGES_FIELD]. */
    var fieldMarks = false

    fun readMaster(): Pair<Map<String, List<DocPara>>, Map<String, List<DocPara>>> {
        val root = OfficeFormats.parse(parts["styles.xml"] ?: return emptyMap<String, List<DocPara>>() to emptyMap()).documentElement
        root.kid(OFFICE, "styles")?.let { collect(it, false) }
        root.kid(OFFICE, "automatic-styles")?.let { collect(it, true) }
        val master = root.kid(OFFICE, "master-styles")?.kids()?.let { ms -> ms.firstOrNull { it.attr(STYLE, "name") == "Standard" } ?: ms.firstOrNull() }
            ?: return emptyMap<String, List<DocPara>>() to emptyMap()
        fun read(tag: String) = master.kid(STYLE, tag)?.takeIf { it.attr(STYLE, "display") != "false" }?.let { ArrayList<DocPara>().also { out -> block(it, out, null, -1) } }
        fun both(kind: String) = listOfNotNull(read(kind)?.let { "default" to it }, read("$kind-first")?.let { "first" to it }).toMap()
        return both("header") to both("footer")
    }

    fun collect(parent: Element, automatic: Boolean) {
        for (e in parent.kids()) when (e.localName) {
            "style" -> {
                val family = e.attr(STYLE, "family") ?: continue
                val name = e.attr(STYLE, "name") ?: continue
                styles["$family/$name"] = OdtStyle(name, family, e.attr(STYLE, "parent-style-name"), e.attr(STYLE, "list-style-name"),
                    e.attr(STYLE, "master-page-name"), e.kid(STYLE, "text-properties"), e.kid(STYLE, "paragraph-properties"), automatic)
            }
            "list-style" -> e.attr(STYLE, "name")?.let { listStyles[it] = e }
        }
    }

    fun size(v: String, base: Float): Float? = when {
        v.endsWith("pt") -> v.removeSuffix("pt").toFloatOrNull()
        v.endsWith("%") -> v.removeSuffix("%").toFloatOrNull()?.let { base * it / 100 }
        else -> null
    }

    /** A value that changes nothing ("none", "0cm", "normal"…), so leaving it out loses nothing. */
    fun neutral(v: String) = v.isEmpty() || v in setOf("none", "normal", "auto", "false", "transparent", "100%", "0%", "0% 100%", "start", "page", "0")
        || Regex("""^-?0+(\.0+)?(cm|mm|in|pt|pc|px)?$""").matches(v)

    /** Applies a style's text properties to [look]; what Writer cannot keep is named. */
    fun textLook(props: Element?, look: DocRun, inLink: Boolean = false, fonts: MutableList<Pair<String, String>>? = null): DocRun {
        props ?: return look
        var l = look
        val a = props.attributes
        for (i in 0 until a.length) {
            val n = a.item(i)
            val ns = n.namespaceURI ?: continue
            val local = n.localName; val v = n.nodeValue.trim()
            // Carried as the file has it, letter by letter (fonts asked 2026-09-27; the rest 2026-09-28, from the
            // user's test suite): small caps, letter spacing, language, a double underline… — never what Writer writes.
            fun carry() {
                if (local in ODF_WRITTEN) return
                if (fonts != null) fonts += "text@" to " ${n.nodeName}=\"${OfficeFormats.esc(n.nodeValue)}\"" else l = l.copy(font = withFont(l.font, n.nodeName, n.nodeValue))
            }
            if (!(ns == FO || ns == STYLE)) continue // another program's own (revision ids…)
            if (local.endsWith("-asian") || local.endsWith("-complex")) { carry(); continue }
            when (local) {
                "font-weight" -> l = l.copy(bold = v == "bold" || (v.toIntOrNull() ?: 400) >= 600)
                "font-style" -> l = l.copy(italic = v == "italic" || v == "oblique")
                "text-underline-style" -> if (!inLink) l = l.copy(underline = v != "none")
                "text-line-through-style" -> l = l.copy(strike = v != "none")
                "font-size" -> size(v, basePt)?.let { l = l.copy(sizePt = it) } ?: run { notes += "other text effects" }
                "color" -> if (!inLink) l = l.copy(color = OfficeFormats.parseHex(v))
                "background-color" -> l = l.copy(highlight = if (v == "transparent") null else OfficeFormats.parseHex(v))
                "font-name", "font-family" -> carry()
                // "super 58%", "sub", "33% 58%", "-33% 58%", "0% 100%"
                "text-position" -> v.substringBefore(' ').let { p -> l = l.copy(script = when { p == "super" -> 1; p == "sub" -> -1; else -> p.removeSuffix("%").toFloatOrNull()?.let { Math.signum(it).toInt() } ?: 0 }) }
                "display" -> if (v == "none") notes += "hidden text"
                else -> carry()
            }
        }
        return l
    }

    /** A run's ODF font attributes, one more set (a later style's setting replaces an earlier one's). */
    fun withFont(font: String?, name: String, value: String): String {
        val attrs = LinkedHashMap<String, String>()
        font?.let { f -> Regex(""" (\S+)="([^"]*)"""").findAll(f).forEach { attrs[it.groupValues[1]] = it.groupValues[2] } }
        attrs[name] = OfficeFormats.esc(value)
        return attrs.entries.joinToString("") { " ${it.key}=\"${it.value}\"" }
    }

    /** The look a named character style (and the styles it is based on) gives. */
    fun charStyle(name: String, look: DocRun, inLink: Boolean): DocRun {
        val chain = generateSequence(styles["text/$name"]) { s -> s.parent?.let { styles["text/$it"] } }.take(10).toList().asReversed()
        if (chain.isEmpty()) return look
        if (inLink && chain.any { !it.automatic && odfDisplayName(it.name).lowercase().let { n -> "internet link" in n || "visited" in n } }) return look
        return chain.fold(look) { l, s -> textLook(s.text, l, inLink) }
    }

    /** A paragraph's alignment; its other properties (spacing, indents, borders, breaks, tab stops…) are carried, raw. */
    fun paragraphLook(p: OdtStyle?, parts: MutableList<Pair<String, String>>): DocAlign {
        val props = p?.paragraph ?: return DocAlign.Start
        var align = DocAlign.Start
        val a = props.attributes
        for (i in 0 until a.length) {
            val n = a.item(i)
            if (n.nodeName.startsWith("xmlns")) continue
            if (n.namespaceURI == FO && n.localName == "text-align") {
                align = when (n.nodeValue.trim()) { "center" -> DocAlign.Center; "end", "right" -> DocAlign.End; "justify" -> DocAlign.Justify; else -> DocAlign.Start }
            } else if (!(n.namespaceURI == STYLE && n.localName == "justify-single-word")) parts += "@" to " ${n.nodeName}=\"${OfficeFormats.esc(n.nodeValue)}\""
        }
        props.kids().forEach { parts += "child" to OfficeFormats.xml(it) }
        return align
    }

    /** Plain body text: a paragraph needs no style of its own to look as it did. */
    fun plainParagraph(name: String?, isHeading: Boolean): Boolean {
        name ?: return true
        val n = odfDisplayName(name).lowercase()
        if (n in setOf("standard", "default", "default paragraph style", "text body", "body text", "list paragraph", "list contents", "list", "normal", "heading")) return true
        return isHeading && Regex("""heading ?\d""").matches(n)
    }

    fun block(parent: Element, out: MutableList<DocPara>, list: String?, level: Int) {
        for (e in parent.kids()) when {
            e.namespaceURI == TEXT -> when (e.localName) {
                "p", "h" -> paragraph(e, out, null)
                "list" -> list(e, out, null, 0, null)
                "sequence-decls", "variable-decls", "user-field-decls", "dde-connection-decls", "soft-page-break", "bookmark", "bookmark-start", "bookmark-end" -> Unit
                "tracked-changes" -> notes += "tracked changes"
                "section" -> { notes += "sections"; block(e, out, list, level) }
                "table-of-content", "alphabetical-index", "illustration-index", "table-index", "object-index", "user-index", "bibliography" ->
                    { notes += "fields (page numbers, dates, contents)"; e.kid(TEXT, "index-body")?.let { block(it, out, list, level) } }
                "numbered-paragraph" -> { notes += "other list formats"; block(e, out, list, level) }
                else -> notes += "other content"
            }
            e.namespaceURI == TABLE && e.localName == "table" -> out += tablePara(table(e))
            e.namespaceURI == OFFICE && e.localName == "forms" -> Unit
            e.localName == "frame" || e.namespaceURI?.contains("drawing") == true -> notes += "shapes and text boxes"
            else -> notes += "other content"
        }
    }

    /** An element's attributes as the file wrote them (" table:style-name=\"A1\"…"), less [drop]. */
    private fun attrs(e: Element, vararg drop: String): String {
        val a = e.attributes
        return (0 until a.length).map { a.item(it) }.filter { !it.nodeName.startsWith("xmlns") && it.nodeName !in drop }
            .joinToString("") { " ${it.nodeName}=\"${OfficeFormats.esc(it.nodeValue)}\"" }
    }

    /**
     * A table (2026-09-28): kept in the file's own words, as a Word table is — the table's, rows' and cells' attributes
     * (their styles, spans) and its columns raw; each cell's paragraphs read as the body's. A merged-away cell
     * (covered-table-cell) stays a cell, marked; a header row is marked; repeated rows and cells are written out.
     */
    fun table(e: Element): DocTable {
        val rows = ArrayList<DocRow>()
        val grid = StringBuilder()
        fun rowsIn(parent: Element, header: Boolean) {
            for (c in parent.kids()) when (c.localName) {
                "table-column", "table-columns", "table-header-columns", "table-column-group" -> grid.append(OfficeFormats.xml(c))
                "table-header-rows" -> rowsIn(c, true)
                "table-rows", "table-row-group" -> rowsIn(c, header)
                "table-row" -> {
                    val cells = ArrayList<DocCell>()
                    for (cell in c.kids()) {
                        val covered = cell.localName == "covered-table-cell"
                        if (!covered && cell.localName != "table-cell") continue
                        val paras = ArrayList<DocPara>().also { block(cell, it, null, -1) }
                        val n = cell.attr(TABLE, "number-columns-repeated")?.toIntOrNull()?.coerceIn(1, 64) ?: 1
                        repeat(n) { cells += DocCell((if (covered) ODT_COVERED else "") + attrs(cell, "table:number-columns-repeated"), paras) }
                    }
                    val n = c.attr(TABLE, "number-rows-repeated")?.toIntOrNull()?.coerceIn(1, 1000) ?: 1
                    repeat(n) { rows += DocRow((if (header) ODT_HEADER else "") + attrs(c, "table:number-rows-repeated"), cells) }
                }
                else -> if (c.namespaceURI == TABLE) notes += "other content"
            }
        }
        rowsIn(e, false)
        return DocTable(attrs(e), grid.toString(), rows)
    }

    fun list(e: Element, out: MutableList<DocPara>, inherited: String?, level: Int, parentCounter: IntArray?) {
        val styleName = e.attr(TEXT, "style-name") ?: inherited
        val counter = if (level == 0) {
            val cont = e.attr(TEXT, "continue-numbering") == "true" || e.attr(TEXT, "continue-list") != null
            if (cont) listCounters.getOrPut(styleName ?: "") { IntArray(10) } else IntArray(10).also { listCounters[styleName ?: ""] = it }
        } else parentCounter!!
        for (d in level + 1 until 10) counter[d] = 0
        for (item in e.kids()) {
            if (item.localName == "list-header") notes += "other list formats"
            if (item.localName != "list-item" && item.localName != "list-header") continue
            var numbered = false
            for (c in item.kids()) when (c.localName) {
                "p", "h" -> {
                    val style = styleName ?: c.attr(TEXT, "style-name")?.let { styles["paragraph/$it"]?.listStyle }
                    val (ordered, start) = levelStyle(style, level)
                    val value = if (!numbered) {
                        item.attr(TEXT, "start-value")?.toIntOrNull()?.let { counter[level] = it } ?: run { counter[level] = if (counter[level] == 0) start else counter[level] + 1 }
                        for (d in level + 1 until 10) counter[d] = 0
                        numbered = true
                        counter[level]
                    } else null
                    val source = style?.let { st -> st + "|" + (0..9).joinToString("") { l -> if (levelStyle(st, l).first) "n" else "b" } }
                    paragraph(c, out, if (value == null) null else DocList(ordered, level.coerceAtMost(8), value.takeIf { ordered }), source)
                    if (value == null) { notes += "paragraphs inside list items" }
                }
                "list" -> list(c, out, styleName, level + 1, counter)
                "soft-page-break" -> Unit
                else -> notes += "other content"
            }
        }
    }

    /** Whether a list level is numbered, and where it starts. */
    fun levelStyle(name: String?, level: Int): Pair<Boolean, Int> {
        val style = name?.let { listStyles[it] } ?: return false to 1
        val l = style.kids().firstOrNull { it.attr(TEXT, "level")?.toIntOrNull() == level + 1 } ?: return false to 1
        return when (l.localName) {
            "list-level-style-number" -> {
                val format = l.attr(STYLE, "num-format") ?: "1"
                if (format.isEmpty()) { notes += "other list formats"; false to 1 }
                else { if (format != "1") notes += "letter or roman numbering"; true to (l.attr(TEXT, "start-value")?.toIntOrNull() ?: 1) }
            }
            else -> false to 1
        }
    }

    /** A picture, shape or text box in a paragraph, kept as the file has it. */
    fun frame(e: Element): DocImage {
        val href = e.getElementsByTagNameNS("*", "image").let { if (it.length > 0) (it.item(0) as Element).getAttributeNS(XLINK, "href").takeIf { h -> h.isNotEmpty() && !h.contains(':') } else null }
        fun length(v: String?): Float? = v?.let { Regex("""^([\d.]+)(cm|mm|in|pt|pc|px)$""").find(it.trim()) }?.let { m ->
            val n = m.groupValues[1].toFloat()
            when (m.groupValues[2]) { "cm" -> n * 28.3465f; "mm" -> n * 2.83465f; "in" -> n * 72; "pc" -> n * 12; "px" -> n * 0.75f; else -> n }
        }
        return DocImage("f${imageCount++}", href, length(e.getAttributeNS(SVG, "width")) ?: 144f, length(e.getAttributeNS(SVG, "height")) ?: 96f, OfficeFormats.xml(e))
    }
    var imageCount = 0

    fun paragraph(p: Element, out: MutableList<DocPara>, list: DocList?, listSource: String? = null) {
        val isHeading = p.localName == "h"
        val heading = if (isHeading) (p.attr(TEXT, "outline-level")?.toIntOrNull() ?: 1).also { if (it > 6) notes += "headings below level 6" }.coerceIn(1, 6) else 0
        val styleName = p.attr(TEXT, "style-name")
        val style = styleName?.let { styles["paragraph/$it"] }
        var base = DocRun("")
        var align = DocAlign.Start
        val parts = ArrayList<Pair<String, String>>()
        var keepStyle: String? = null
        if (style != null && style.automatic) {
            align = paragraphLook(style, parts)
            base = textLook(style.text, base)
            // The paragraph's own text settings stay on the paragraph too: they make an empty line as tall as it was.
            style.text?.attributes?.let { at -> for (k in 0 until at.length) at.item(k).let { n ->
                if (!n.nodeName.startsWith("xmlns") && !n.nodeName.startsWith("officeooo:")) parts += "text@" to " ${n.nodeName}=\"${OfficeFormats.esc(n.nodeValue)}\""
            } }
            if (!isHeading || !plainParagraph(style.parent, true)) keepStyle = style.parent // the exact one: Standard and Text body space differently
            style.masterPage?.let { if (it.isNotEmpty()) parts += "master" to it }
        } else if (!isHeading || !plainParagraph(styleName, true)) keepStyle = styleName
        val keep = DocKeep(keepStyle, parts, listSource.takeIf { list != null }).takeIf { !it.isEmpty }
        if (isHeading && p.attr(TEXT, "is-list-header") == null && p.parentNode.let { it is Element && it.localName == "list-item" }) notes += "numbered headings"
        val runs = CommentedRuns(openComments)
        val currentList = list
        fun content(parent: Element, look: DocRun, inLink: Boolean) {
            var n: Node? = parent.firstChild
            while (n != null) {
                if (n.nodeType == Node.TEXT_NODE || n.nodeType == Node.CDATA_SECTION_NODE) {
                    // ODF collapses runs of white space like HTML; <text:s/> carries the spaces that count.
                    val t = n.nodeValue.replace(Regex("""[ \t\r\n]+"""), " ")
                    if (t.isNotEmpty()) runs += look.copy(text = t)
                } else if (n is Element) {
                    val e = n
                    if (e.namespaceURI == TEXT) when (e.localName) {
                        "span" -> content(e, e.attr(TEXT, "style-name")?.let { charStyle(it, look, inLink) } ?: look, inLink)
                        "a" -> {
                            val href = e.attr(XLINK, "href")
                            if (href == null || href.startsWith("#")) { notes += "links inside the document"; content(e, look, inLink) }
                            else content(e, look.copy(link = href), true)
                        }
                        "s" -> runs += look.copy(text = " ".repeat(e.attr(TEXT, "c")?.toIntOrNull()?.coerceIn(1, 1000) ?: 1))
                        "tab" -> runs += look.copy(text = "\t")
                        "line-break" -> runs += look.copy(text = LINE_BREAK.toString())
                        "soft-page-break", "bookmark", "bookmark-start", "bookmark-end", "reference-mark", "reference-mark-start", "reference-mark-end", "alphabetical-index-mark", "toc-mark" -> Unit
                        "note" -> notes += "footnotes"
                        "change", "change-start", "change-end" -> notes += "tracked changes"
                        "ruby" -> { notes += "other text effects"; e.kid(TEXT, "ruby-base")?.let { content(it, look, inLink) } }
                        "meta", "meta-field" -> content(e, look, inLink)
                        "page-number", "page-count" -> if (fieldMarks) runs += look.copy(text = (if (e.localName == "page-number") PAGE_FIELD else PAGES_FIELD).toString())
                            else { notes += "fields (page numbers, dates, contents)"; content(e, look, inLink) }
                        else -> { notes += "fields (page numbers, dates, contents)"; content(e, look, inLink) }
                    } else if (e.namespaceURI == OFFICE && e.localName == "annotation") {
                        val name = e.attr(OFFICE, "name")
                        if (name == null || name !in ended) notes += "comments" // on a point, not some letters: not kept yet
                        else {
                            comments[name] = DocComment(name, e.kid(DC, "creator")?.textContent ?: "", e.kid(DC, "date")?.textContent,
                                e.kids().filter { it.localName == "p" }.joinToString("\n") { it.textContent }, e.kids().joinToString("") { OfficeFormats.xml(it) })
                            openComments += name
                        }
                    } else if (e.namespaceURI == OFFICE && e.localName == "annotation-end") e.attr(OFFICE, "name")?.let { openComments -= it }
                    else if (e.localName == "custom-shape" && e.getAttributeNS(e.namespaceURI, "name").startsWith(Watermark.NAME)) Unit // the pages draw it
                    else if (e.localName == "frame" || e.namespaceURI?.contains("drawing") == true) runs += look.copy(text = OBJECT_MARK.toString(), image = frame(e))
                    else notes += "other content"
                }
                n = n.nextSibling
            }
        }
        content(p, base, false)
        // Leading white space in an ODF paragraph is not shown; a trailing space is kept (LibreOffice keeps it).
        if (runs.isNotEmpty() && p.firstChild?.nodeType == Node.TEXT_NODE) runs[0] = runs[0].copy(text = runs[0].text.trimStart(' '))
        // An empty paragraph is as tall as what it holds (an empty 16 pt span makes a 16 pt line): that stays.
        val finalKeep = if (runs.all { it.text.isEmpty() } && p.kids().isNotEmpty())
            (keep ?: DocKeep()).let { it.copy(parts = it.parts + ("empty" to p.kids().joinToString("") { k -> OfficeFormats.xml(k) })) } else keep
        out += DocPara(OfficeFormats.tidy(runs), heading, currentList, align, finalKeep)
    }
}

private object OdtWriter {
    val NS = """xmlns:office="$OFFICE" xmlns:style="$STYLE" xmlns:text="$TEXT" xmlns:table="$TABLE" xmlns:fo="$FO" xmlns:xlink="$XLINK" xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0" xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0""""

    fun canReuse(parts: Map<String, ByteArray>): Boolean = runCatching {
        String(parts.getValue("content.xml"), Charsets.UTF_8).let { Regex("""<office:text[\s>/]""").containsMatchIn(it) && "xmlns:text=" in it && "xmlns:style=" in it && "xmlns:fo=" in it }
    }.getOrDefault(false)

    fun blank(): LinkedHashMap<String, ByteArray> {
        fun heading(n: Int, size: String) = """<style:style style:name="Heading_20_$n" style:display-name="Heading $n" style:family="paragraph" style:parent-style-name="Heading" style:next-style-name="Text_20_body" style:default-outline-level="$n" style:class="text"><style:text-properties fo:font-size="$size" fo:font-weight="bold"/></style:style>"""
        val m = linkedMapOf(
            "mimetype" to "application/vnd.oasis.opendocument.text",
            "META-INF/manifest.xml" to """<?xml version="1.0" encoding="UTF-8"?><manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.3"><manifest:file-entry manifest:full-path="/" manifest:version="1.3" manifest:media-type="application/vnd.oasis.opendocument.text"/><manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/><manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/></manifest:manifest>""",
            "content.xml" to """<?xml version="1.0" encoding="UTF-8"?><office:document-content $NS office:version="1.3"><office:automatic-styles/><office:body><office:text><text:p text:style-name="Text_20_body"/></office:text></office:body></office:document-content>""",
            "styles.xml" to """<?xml version="1.0" encoding="UTF-8"?><office:document-styles $NS office:version="1.3"><office:styles><style:default-style style:family="paragraph"><style:paragraph-properties style:writing-mode="page"/><style:text-properties fo:font-size="12pt" style:font-name="Liberation Serif" fo:language="none"/></style:default-style><style:style style:name="Standard" style:family="paragraph" style:class="text"/><style:style style:name="Text_20_body" style:display-name="Text body" style:family="paragraph" style:parent-style-name="Standard" style:class="text"><style:paragraph-properties fo:margin-top="0cm" fo:margin-bottom="0.247cm" fo:line-height="115%"/></style:style><style:style style:name="Heading" style:family="paragraph" style:parent-style-name="Standard" style:next-style-name="Text_20_body" style:class="text"><style:paragraph-properties fo:margin-top="0.423cm" fo:margin-bottom="0.212cm" fo:keep-with-next="always"/></style:style>${heading(1, "130%")}${heading(2, "115%")}${heading(3, "101%")}${heading(4, "95%")}${heading(5, "85%")}${heading(6, "85%")}</office:styles><office:automatic-styles><style:page-layout style:name="pm1"><style:page-layout-properties fo:page-width="21.001cm" fo:page-height="29.7cm" fo:margin-top="2cm" fo:margin-bottom="2cm" fo:margin-left="2cm" fo:margin-right="2cm"/></style:page-layout></office:automatic-styles><office:master-styles><style:master-page style:name="Standard" style:page-layout-name="pm1"/></office:master-styles></office:document-styles>""",
        )
        return LinkedHashMap(m.mapValues { it.value.toByteArray() })
    }

    fun write(doc: OfficeDoc, source: LinkedHashMap<String, ByteArray>): ByteArray {
        val parts = LinkedHashMap(source)
        var stylesXml = parts["styles.xml"]?.let { String(it, Charsets.UTF_8) }
        val named = stylesXml?.let { s -> Regex("""style:name="([^"]+)"""").findAll(s).map { it.groupValues[1] }.toSet() } ?: emptySet()
        // Plain paragraphs keep the style this document uses for its text (Text body in LibreOffice, else Standard).
        val body = if ("Text_20_body" in named) "Text_20_body" else "Standard"
        // A heading style the document does not have is added, so its headings still look like headings.
        if (stylesXml != null && "</office:styles>" in stylesXml) {
            val add = (1..6).filter { n -> doc.paras.any { it.heading == n } && "Heading_20_$n" !in named }.joinToString("") { n -> odtHeadingStyle(n, body) }
            val builtin = doc.paras.mapNotNull { it.keep?.style }.toSet().filter { it in ODT_BUILTIN && it !in named }.joinToString("") { ODT_BUILTIN.getValue(it) }
            if (add.isNotEmpty() || builtin.isNotEmpty()) { stylesXml = stylesXml.replace("</office:styles>", "$add$builtin</office:styles>"); parts["styles.xml"] = stylesXml.toByteArray() }
        }

        // The file's own automatic styles: what the new text still refers to stays (a picture's position and wrap, a
        // text box's paragraphs, a list); Writer's own get names the file does not use.
        val content = String(parts.getValue("content.xml"), Charsets.UTF_8)
        val ownAuto = runCatching { OfficeFormats.parse(parts.getValue("content.xml")).documentElement.kid(OFFICE, "automatic-styles")?.kids().orEmpty() }.getOrDefault(emptyList())
            .map { (it.attr(STYLE, "name") ?: "") to OfficeFormats.xml(it) }
        val usedNames = ownAuto.map { it.first }.toMutableSet()
        fun fresh(prefix: String) = generateSequence(1) { it + 1 }.map { "$prefix$it" }.first { it !in usedNames }.also { usedNames += it }
        val auto = StringBuilder()
        val paraStyles = HashMap<Triple<String, DocAlign, List<Pair<String, String>>>, String>()
        val textStyles = HashMap<DocRun, String>()
        val listStyles = HashMap<String, String>()
        /** The paragraph's own style: its parent, its alignment, and what it carries from the file (spacing, indents…). */
        fun paraStyle(parent: String, align: DocAlign, parts: List<Pair<String, String>>): String {
            if (align == DocAlign.Start && parts.isEmpty()) return parent
            return paraStyles.getOrPut(Triple(parent, align, parts)) {
                val name = fresh("TP")
                val master = parts.firstOrNull { it.first == "master" }?.let { " style:master-page-name=\"${OfficeFormats.esc(it.second)}\"" } ?: ""
                val attrs = parts.filter { it.first == "@" }.joinToString("") { it.second }
                val children = parts.filter { it.first == "child" }.joinToString("") { it.second }
                val text = parts.filter { it.first == "text@" }.joinToString("") { it.second }.let { if (it.isEmpty()) "" else "<style:text-properties$it/>" }
                val a = when (align) {
                    DocAlign.Start -> ""
                    DocAlign.Center -> " fo:text-align=\"center\""; DocAlign.End -> " fo:text-align=\"end\""
                    DocAlign.Justify -> " fo:text-align=\"justify\" style:justify-single-word=\"false\""
                }
                val props = if (attrs.isEmpty() && a.isEmpty() && children.isEmpty()) "" else if (children.isEmpty()) "<style:paragraph-properties$attrs$a/>" else "<style:paragraph-properties$attrs$a>$children</style:paragraph-properties>"
                auto.append("""<style:style style:name="$name" style:family="paragraph" style:parent-style-name="$parent"$master>$props$text</style:style>""")
                name
            }
        }
        fun textStyle(r: DocRun): String? {
            val look = r.copy(text = "", link = null, image = null, comments = emptyList())
            if (look == DocRun("")) return null
            return textStyles.getOrPut(look) {
                val name = fresh("TT")
                val p = buildString {
                    if (r.bold) append(""" fo:font-weight="bold" style:font-weight-asian="bold" style:font-weight-complex="bold"""")
                    if (r.italic) append(""" fo:font-style="italic" style:font-style-asian="italic" style:font-style-complex="italic"""")
                    if (r.underline) append(""" style:text-underline-style="solid" style:text-underline-width="auto" style:text-underline-color="font-color"""")
                    if (r.strike) append(""" style:text-line-through-style="solid" style:text-line-through-type="single"""")
                    r.sizePt?.let { val s = fmt(it); append(""" fo:font-size="${s}pt" style:font-size-asian="${s}pt" style:font-size-complex="${s}pt"""") }
                    r.color?.let { append(""" fo:color="#${OfficeFormats.hex(it).lowercase()}"""") }
                    r.highlight?.let { append(""" fo:background-color="#${OfficeFormats.hex(it).lowercase()}"""") }
                    r.font?.let { append(it) }
                    if (r.script != 0) append(""" style:text-position="${if (r.script > 0) "super" else "sub"} 58%"""")
                }
                auto.append("""<style:style style:name="$name" style:family="text"><style:text-properties$p/></style:style>""")
                name
            }
        }
        /** A list style whose levels are numbered or bulleted as this list's items are. */
        fun listStyle(items: List<DocPara>): String {
            val pattern = (0..9).joinToString("") { l -> if (items.firstOrNull { it.list!!.level == l }?.list?.ordered == true) "n" else "b" }
            return listStyles.getOrPut(pattern) {
                val name = fresh("TL")
                auto.append("""<text:list-style style:name="$name">""")
                pattern.forEachIndexed { i, c ->
                    val indent = "%.3fcm".format(java.util.Locale.ROOT, 0.635 * (i + 1))
                    val props = """<style:list-level-properties text:list-level-position-and-space-mode="label-alignment"><style:list-level-label-alignment text:label-followed-by="listtab" text:list-tab-stop-position="$indent" fo:text-indent="-0.635cm" fo:margin-left="$indent"/></style:list-level-properties>"""
                    if (c == 'n') auto.append("""<text:list-level-style-number text:level="${i + 1}" style:num-suffix="." style:num-format="1">$props</text:list-level-style-number>""")
                    else auto.append("""<text:list-level-style-bullet text:level="${i + 1}" text:bullet-char="${listOf("•", "◦", "▪")[i % 3]}">$props</text:list-level-style-bullet>""")
                }
                auto.append("</text:list-style>")
                name
            }
        }

        // Pictures added in Writer: each a new file in Pictures/, listed in the manifest, and a frame in the text.
        var manifest = parts["META-INF/manifest.xml"]?.let { String(it, Charsets.UTF_8) }
        var frames = 0
        val wraps = HashSet<Pair<PictureWrap, Boolean>>()
        val shapeStyles = HashSet<String>()
        fun frame(img: DocImage): String = (img.raw ?: run {
            val bytes = img.bytes ?: return@run ""
            val name = generateSequence(1) { it + 1 }.map { "Pictures/tetra-$it.${img.extension}" }.first { it !in parts }
            parts[name] = bytes
            manifest = manifest?.replace("</manifest:manifest>", """<manifest:file-entry manifest:full-path="$name" manifest:media-type="image/${if (img.extension == "jpg") "jpeg" else img.extension}"/></manifest:manifest>""")
            val n = ++frames
            """<draw:frame draw:name="Tetra picture $n" text:anchor-type="as-char" svg:width="${fmt(img.widthPt)}pt" svg:height="${fmt(img.heightPt)}pt" draw:z-index="0"><draw:image xlink:href="$name" xlink:type="simple" xlink:show="embed" xlink:actuate="onLoad"/></draw:frame>"""
        }).let { x -> img.wrap?.let { w -> val page = img.raw == null && img.onPage != null && w != PictureWrap.InLine
            if (wraps.add(w to page)) auto.append(PictureXml.odtStyle(w, page)); PictureXml.odt(x, w, img.onPage.takeIf { page }) } ?: x }
            .also { x -> Regex("""draw:style-name="(Tetra_20_shape_[^"]+)"""").find(x)?.groupValues?.get(1)?.let { n -> if (n !in usedNames && shapeStyles.add(n)) PictureXml.odtShapeStyle(n)?.let(auto::append) } }

        // Comments: an annotation where one starts (its contents as the file had them, or written out), its end where it ends.
        val live = doc.liveComments.associateBy { it.id }
        val openComments = LinkedHashSet<String>()
        val closedComments = HashSet<String>() // started once: never again
        val nextFirst = java.util.IdentityHashMap<DocPara, DocPara?>().also { m ->
            var prev: DocPara? = null
            fun walk(ps: List<DocPara>) { ps.forEach { q -> if (q.table != null) q.table.rows.forEach { r -> r.cells.forEach { walk(it.paras) } } else { prev?.let { m[it] = q }; prev = q } } }
            walk(doc.paras)
        }
        fun StringBuilder.commentMarks(now: List<String>) {
            for (id in openComments.filter { it !in now }) { append("""<office:annotation-end office:name="${OfficeFormats.esc(id)}"/>"""); openComments -= id }
            for (id in now) if (id !in openComments && closedComments.add(id)) live[id]?.let { c ->
                append("""<office:annotation office:name="${OfficeFormats.esc(id)}">""")
                append(c.raw ?: ("<dc:creator>${OfficeFormats.esc(c.author)}</dc:creator>" + (c.date?.let { "<dc:date>$it</dc:date>" } ?: "") +
                    c.text.split('\n').joinToString("") { "<text:p>${spaces(it, true)}</text:p>" }))
                append("</office:annotation>")
                openComments += id
            }
        }
        fun paragraphXml(p: DocPara): String = buildString {
            val tag = if (p.heading > 0) "text:h" else "text:p"
            val parent = p.keep?.style?.takeIf { p.heading == 0 || !plainNamed(it) } ?: if (p.heading > 0) "Heading_20_${p.heading}" else body
            val style = paraStyle(parent, p.align, p.keep?.parts.orEmpty().filter { it.first != "empty" })
            append("<$tag text:style-name=\"$style\"")
            if (p.heading > 0) append(" text:outline-level=\"${p.heading}\"")
            append(">")
            var k = 0
            var prev: String? = null
            val runs = OfficeFormats.tidy(p.runs)
            if (runs.isEmpty()) p.keep?.parts?.firstOrNull { it.first == "empty" }?.let { append(it.second) }
            while (k < runs.size) {
                val link = runs[k].link
                var end = k
                while (end < runs.size && runs[end].link == link) end++
                if (link != null) append("""<text:a xlink:type="simple" xlink:href="${OfficeFormats.esc(link)}">""")
                for (r in runs.subList(k, end)) {
                    commentMarks(r.comments)
                    if (r.image != null) { append(frame(r.image)); prev = "x"; continue }
                    val s = textStyle(r)
                    if (s != null) append("<text:span text:style-name=\"$s\">")
                    append(spaces(r.text, afterSpace = prev?.endsWith(' ') ?: true))
                    prev = r.text
                    if (s != null) append("</text:span>")
                }
                if (link != null) append("</text:a>")
                k = end
            }
            // A comment ends here unless the next paragraph goes on in it.
            commentMarks(nextFirst[p]?.runs?.firstOrNull()?.comments.orEmpty().filter { it in openComments })
            append("</$tag>")
        }

        /** Paragraphs, lists and tables as the body has them; a table's cells hold the same (a table in a cell too). */
        fun blockXml(paras: List<DocPara>, text: StringBuilder) {
        var i = 0
        while (i < paras.size) {
            val p = paras[i]
            p.table?.let { t ->
                // A table as the file had it: its attributes and columns raw, header rows grouped, merged-away cells kept.
                text.append("<table:table${t.props}>").append(t.grid)
                var inHeader = false
                for (row in t.rows) {
                    val header = row.props.startsWith(ODT_HEADER)
                    if (header != inHeader) { text.append(if (header) "<table:table-header-rows>" else "</table:table-header-rows>"); inHeader = header }
                    text.append("<table:table-row${row.props.removePrefix(ODT_HEADER)}>")
                    for (cell in row.cells) {
                        val covered = cell.props.startsWith(ODT_COVERED)
                        val tag = if (covered) "table:covered-table-cell" else "table:table-cell"
                        val inner = StringBuilder().also { blockXml(cell.paras, it) }
                        text.append("<$tag${cell.props.removePrefix(ODT_COVERED)}")
                        if (inner.isEmpty()) text.append("/>") else text.append(">").append(inner).append("</$tag>")
                    }
                    text.append("</table:table-row>")
                }
                if (inHeader) text.append("</table:table-header-rows>")
                text.append("</table:table>")
                i++; continue
            }
            if (p.list == null) { text.append(paragraphXml(p)); i++; continue }
            var end = i
            while (end < paras.size && paras[end].list != null && paras[end].table == null) end++
            val items = paras.subList(i, end)
            // The file's own list style, when every item still belongs to it and is still of its kind at its level.
            val source = items.first().keep?.list
            val reuse = source != null && items.all { it.keep?.list == source && source.substringAfterLast('|').getOrNull(it.list!!.level)?.let { c -> (c == 'n') == it.list.ordered } == true }
            text.append("<text:list text:style-name=\"${if (reuse) OfficeFormats.esc(source!!.substringBeforeLast('|')) else listStyle(items)}\">")
            var depth = 0 // lists open below the top one
            var itemOpen = false
            for ((n, item) in items.withIndex()) {
                val level = item.list!!.level
                while (depth > level) { text.append("</text:list-item></text:list>"); depth-- }
                if (itemOpen && depth == level) text.append("</text:list-item>")
                while (depth < level) { if (!itemOpen || n == 0) text.append("<text:list-item>"); text.append("<text:list>"); depth++; itemOpen = false }
                val start = item.list.start?.takeIf { item.list.ordered && (n == 0 || items[n - 1].list!!.level < level) && it != 1 }
                text.append(if (start != null) "<text:list-item text:start-value=\"$start\">" else "<text:list-item>")
                text.append(paragraphXml(item))
                itemOpen = true
            }
            text.append("</text:list-item>")
            while (depth > 0) { text.append("</text:list></text:list-item>"); depth-- }
            text.append("</text:list>")
            i = end
        }
        }
        val text = StringBuilder().also { blockXml(doc.paras, it) }

        val open = Regex("""<office:text(\s[^>]*)?(/?)>""").find(content)!!
        val decls: String
        val rest: String
        if (open.groupValues[2] == "/") { decls = ""; rest = content.substring(open.range.last + 1) }
        else {
            val close = content.indexOf("</office:text>", open.range.last)
            val inner = content.substring(open.range.last + 1, close)
            // Declarations (sequences, variables, forms) stay; the text is replaced.
            decls = Regex("""<(text:[a-z-]+-decls|office:forms)(\s[^>]*)?(/>|>[\s\S]*?</\1>)""").findAll(inner).joinToString("") { it.value }
            rest = content.substring(close + "</office:text>".length)
        }
        var out = content.substring(0, open.range.first) + "<office:text${open.groupValues[1]}>" + decls + text + "</office:text>" + rest
        if (ODT_TETRA_CELL in text && ownAuto.none { it.first == ODT_TETRA_CELL }) auto.append(ODT_TETRA_STYLES)
        val autoRe = Regex("""<office:automatic-styles\s*/>|<office:automatic-styles>[\s\S]*?</office:automatic-styles>""")
        val kept = LinkedHashSet<String>()
        var scan = text.toString() + auto
        while (true) {
            val named = Regex("""[a-z-]*style-name="([^"]+)"""").findAll(scan).map { it.groupValues[1] }.toSet()
            val more = ownAuto.filter { it.first in named && it.first !in kept }
            if (more.isEmpty()) break
            more.forEach { kept += it.first }
            scan = more.joinToString("") { it.second }
        }
        val ownKept = ownAuto.filter { it.first in kept }.joinToString("") { it.second }
        val autoXml = "<office:automatic-styles>$ownKept$auto</office:automatic-styles>"
        out = if (autoRe.containsMatchIn(out)) out.replaceFirst(autoRe, Regex.escapeReplacement(autoXml)) else out.replaceFirst("<office:body>", "$autoXml<office:body>")
        if ("xmlns:xlink=" !in out.substring(0, out.indexOf('>', out.indexOf("<office:document-content")))) out = out.replaceFirst("<office:document-content", "<office:document-content xmlns:xlink=\"$XLINK\"")
        if ("<office:annotation" in text && "xmlns:dc=" !in out.substring(0, out.indexOf('>', out.indexOf("<office:document-content")))) out = out.replaceFirst("<office:document-content", "<office:document-content xmlns:dc=\"$DC\"")
        manifest?.let { parts["META-INF/manifest.xml"] = it.toByteArray() }
        if (frames > 0) listOf("draw" to "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0", "svg" to SVG).forEach { (prefix, ns) ->
            val head = out.substring(0, out.indexOf('>', out.indexOf("<office:document-content")))
            if ("xmlns:$prefix=" !in head) out = out.replaceFirst("<office:document-content", "<office:document-content xmlns:$prefix=\"$ns\"")
        }
        parts["content.xml"] = out.toByteArray()
        return OfficeFormats.zip(parts, stored = "mimetype")
    }

    /** A style that is only plain text, which a heading must not take instead of its heading style. */
    private fun plainNamed(name: String) = odfDisplayName(name).lowercase() in setOf("standard", "default", "text body", "body text", "normal")

    fun fmt(pt: Float): String = if (pt == pt.toInt().toFloat()) pt.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, pt)

    /** Text with ODF's spaces: a space after a space (or at the start of a paragraph) is a <text:s/>; a tab a <text:tab/>. */
    fun spaces(s: String, afterSpace: Boolean): String = buildString {
        var waiting = 0
        fun flush() { if (waiting > 0) append(if (waiting == 1) "<text:s/>" else "<text:s text:c=\"$waiting\"/>"); waiting = 0 }
        for ((i, c) in s.withIndex()) {
            if (c == ' ') {
                if (if (i == 0) afterSpace else s[i - 1] == ' ') waiting++ else append(' ')
                continue
            }
            flush()
            when (c) {
                '\t' -> append("<text:tab/>")
                LINE_BREAK -> append("<text:line-break/>")
                else -> append(OfficeFormats.esc(c.toString()))
            }
        }
        flush()
    }
}
