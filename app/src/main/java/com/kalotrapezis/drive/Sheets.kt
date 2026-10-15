package com.kalotrapezis.drive

import org.w3c.dom.Element
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.xml.parsers.SAXParserFactory
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Tetra's spreadsheets (the user, 15 Oct: "start with the viewer and let's complete it like we did with Word"): an
 * .xlsx, .ods or .csv read into sheets of cells, each cell as the file shows it — its text already formatted (Excel's
 * number formats, [SheetFormat]; an .ods carries the shown text itself), its looks, the columns' widths and rows'
 * heights, merged cells, frozen panes. Sizes are in pixels at 100 % (Excel's own unit: a default column is 64, a row 20).
 */
internal enum class SheetAlign { General, Left, Center, Right, Justify }
internal enum class SheetVAlign { Top, Center, Bottom }
internal enum class SheetKind { Number, Text, Bool, Error, Empty }

/**
 * A differential look (Excel's dxf): what a table's style changes in a cell — null leaves it be. [horizontal] and
 * [vertical] are a table's inner lines.
 */
internal data class SheetDxf(
    val fill: Int? = null, val color: Int? = null, val bold: Boolean? = null, val italic: Boolean? = null,
    val left: SheetLine? = null, val right: SheetLine? = null, val top: SheetLine? = null, val bottom: SheetLine? = null,
    val horizontal: SheetLine? = null, val vertical: SheetLine? = null,
) {
    /** [over] laid on this one: its set parts win. */
    operator fun plus(over: SheetDxf) = SheetDxf(
        over.fill ?: fill, over.color ?: color, over.bold ?: bold, over.italic ?: italic, over.left ?: left, over.right ?: right,
        over.top ?: top, over.bottom ?: bottom, over.horizontal ?: horizontal, over.vertical ?: vertical,
    )
}

/**
 * A picture, chart or text box over the cells: from a cell ([row], [col]) and a distance into it, to another cell and a
 * distance into that ([toRow] -1: then [width] × [height] from the start), in pixels. A picture's bytes are read when
 * it is first drawn.
 */
internal class SheetDrawing(
    val row: Int, val col: Int, val dx: Float, val dy: Float,
    val toRow: Int = -1, val toCol: Int = -1, val toDx: Float = 0f, val toDy: Float = 0f,
    val width: Float = 0f, val height: Float = 0f,
    val chart: DocChart? = null, val image: (() -> ByteArray?)? = null, val text: String? = null, val fill: Int? = null, val line: Int? = null,
)

/** A cell's border edge: colour, width in pixels, double or dashed. */
internal data class SheetLine(val color: Int, val width: Float, val double: Boolean = false, val dashed: Boolean = false, val dots: Boolean = false)

internal data class SheetStyle(
    val font: String? = null, val size: Float = 11f, val bold: Boolean = false, val italic: Boolean = false,
    val underline: Boolean = false, val strike: Boolean = false, val color: Int? = null, val fill: Int? = null,
    val align: SheetAlign = SheetAlign.General, val valign: SheetVAlign = SheetVAlign.Bottom, val wrap: Boolean = false,
    val indent: Int = 0, val rotation: Int = 0, val format: String = "General",
    val left: SheetLine? = null, val top: SheetLine? = null, val right: SheetLine? = null, val bottom: SheetLine? = null,
) {
    val boxed get() = fill != null || left != null || top != null || right != null || bottom != null
}

/** A cell: the text shown, what it is, its number, its formula ("=SUM(A1:A3)"), its look ([style] in the workbook's list). */
internal data class SheetCell(
    val text: String, val kind: SheetKind, val value: Double? = null, val formula: String? = null, val style: Int = 0,
    val color: Int? = null, val link: String? = null,
    /** Its comment (Excel's note, a threaded comment, LibreOffice's annotation): "Author: words". */
    val note: String? = null,
)

/** Rows and columns from 0, both ends in. */
internal data class SheetRange(val r1: Int, val c1: Int, val r2: Int, val c2: Int) {
    operator fun contains(rc: Pair<Int, Int>) = rc.first in r1..r2 && rc.second in c1..c2
}

/**
 * A sheet's cells by [Sheet.key] — an open-addressed table of plain longs, as a sheet can hold hundreds of thousands
 * (the user's "Εκκαθαρισμένοι" workbook: 1.8 million in 12 sheets; a HashMap's boxed keys and entries took ~150 bytes each).
 */
internal class CellMap(expected: Int = 64) {
    private var keys = LongArray(capacity(expected)) { EMPTY }
    private var values = arrayOfNulls<SheetCell>(keys.size)
    var size = 0; private set

    private fun capacity(n: Int): Int { var c = 16; while (c * 0.6 < n) c = c shl 1; return c }
    private fun slot(k: Long, mask: Int): Int = ((k * -0x61c8864680b583ebL) ushr 33).toInt() and mask

    operator fun get(k: Long): SheetCell? {
        val mask = keys.size - 1
        var i = slot(k, mask)
        while (true) { val at = keys[i]; if (at == EMPTY) return null; if (at == k) return values[i]; i = (i + 1) and mask }
    }

    operator fun set(k: Long, v: SheetCell) {
        if ((size + 1) > keys.size * 0.6) grow()
        val mask = keys.size - 1
        var i = slot(k, mask)
        while (true) {
            val at = keys[i]
            if (at == EMPTY) { keys[i] = k; values[i] = v; size++; return }
            if (at == k) { values[i] = v; return }
            i = (i + 1) and mask
        }
    }

    private fun grow() {
        val oldK = keys; val oldV = values
        keys = LongArray(oldK.size shl 1) { EMPTY }; values = arrayOfNulls(keys.size); size = 0
        for (i in oldK.indices) if (oldK[i] != EMPTY) set(oldK[i], oldV[i]!!)
    }

    fun forEach(action: (Long, SheetCell) -> Unit) { for (i in keys.indices) if (keys[i] != EMPTY) action(keys[i], values[i]!!) }

    companion object { private const val EMPTY = -1L }
}

internal class Sheet(
    val name: String,
    val cells: CellMap,
    /** The used rows and columns: one past the last cell with anything in it. */
    val rows: Int, val cols: Int,
    val colWidths: Map<Int, Float>, val rowHeights: Map<Int, Float>,
    val defaultColWidth: Float, val defaultRowHeight: Float,
    val hiddenCols: Set<Int>, val hiddenRows: Set<Int>,
    val merges: List<SheetRange>,
    val frozenRows: Int, val frozenCols: Int,
    val grid: Boolean = true,
    val colStyles: Map<Int, Int> = emptyMap(), val rowStyles: Map<Int, Int> = emptyMap(),
    val tab: Int? = null, val hidden: Boolean = false,
    /** The file's AutoFilter: the table its header buttons filter. */
    val autoFilter: SheetRange? = null,
    /** Looks only this sheet's cells have (an Excel Table's stripes, a rule's colour): style indices from [LOOKS] on. */
    val looks: List<SheetStyle> = emptyList(),
    /** Pictures, charts and text boxes over the cells. */
    val drawings: List<SheetDrawing> = emptyList(),
    /** Conditional formatting's data bars, by cell. */
    val bars: Map<Long, SheetBar> = emptyMap(),
    /** How the file prints it: page setup, print area, rows on every page. */
    val print: SheetPrint = SheetPrint(),
) {
    /** A cell's look: the workbook's [styles], or this sheet's own [looks]. */
    fun look(i: Int, styles: List<SheetStyle>): SheetStyle? = if (i >= LOOKS) looks.getOrNull(i - LOOKS) else styles.getOrNull(i)

    operator fun get(r: Int, c: Int): SheetCell? = cells[key(r, c)]
    fun width(c: Int) = if (c in hiddenCols) 0f else colWidths[c] ?: defaultColWidth
    fun height(r: Int) = if (r in hiddenRows) 0f else rowHeights[r] ?: defaultRowHeight
    /** The merged range [r],[c] is in, if any. */
    fun merge(r: Int, c: Int): SheetRange? = mergeAt[key(r, c)]
    private val mergeAt: Map<Long, SheetRange> by lazy {
        HashMap<Long, SheetRange>().also { m -> merges.forEach { g -> for (r in g.r1..g.r2) for (c in g.c1..g.c2) m[key(r, c)] = g } }
    }

    companion object {
        /** Where a sheet's own looks are numbered from: past any workbook's styles, which an .ods adds to as it reads. */
        const val LOOKS = 1 shl 24
        fun key(r: Int, c: Int) = (r.toLong() shl 16) or c.toLong()
        fun row(key: Long) = (key shr 16).toInt()
        fun col(key: Long) = (key and 0xFFFF).toInt()
    }
}

/** A sheet's tab: its name, and whether the file hides it. */
internal data class SheetTab(val name: String, val hidden: Boolean = false)

/**
 * A workbook: its tabs, its looks, the sheet it was left on — and each sheet read only when it is opened ([sheet]),
 * the last two kept, so a workbook of a dozen big sheets never holds them all.
 */
internal class Workbook(val tabs: List<SheetTab>, val styles: List<SheetStyle>, val active: Int = 0, private val load: (Int) -> Sheet) {
    private val kept = LinkedHashMap<Int, Sheet>()
    fun sheet(i: Int): Sheet = synchronized(kept) {
        kept.remove(i)?.let { kept[i] = it; return it }
        WriterLog.timed("Sheets: sheet ${i + 1} of ${tabs.size} read") { load(i) }.also { kept[i] = it; while (kept.size > 2) kept.remove(kept.keys.first()) }
    }
    /** For the tests: every sheet. */
    val sheets: List<Sheet> get() = tabs.indices.map(::sheet)
}

/** Excel's 64 indexed colours, and its theme colours lightened or darkened by a tint. */
internal object SheetColors {
    private val INDEXED = intArrayOf(
        0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF,
        0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF,
        0x800000, 0x008000, 0x000080, 0x808000, 0x800080, 0x008080, 0xC0C0C0, 0x808080,
        0x9999FF, 0x993366, 0xFFFFCC, 0xCCFFFF, 0x660066, 0xFF8080, 0x0066CC, 0xCCCCFF,
        0x000080, 0xFF00FF, 0xFFFF00, 0x00FFFF, 0x800080, 0x800000, 0x008080, 0x0000FF,
        0x00CCFF, 0xCCFFFF, 0xCCFFCC, 0xFFFF99, 0x99CCFF, 0xFF99CC, 0xCC99FF, 0xFFCC99,
        0x3366FF, 0x33CCCC, 0x99CC00, 0xFFCC00, 0xFF9900, 0xFF6600, 0x666699, 0x969696,
        0x003366, 0x339966, 0x003300, 0x333300, 0x993300, 0x993366, 0x333399, 0x333333,
    )

    /** 64: the system's text colour (black), 65: its window (white). */
    fun indexed(i: Int, custom: List<Int>? = null): Int? = when {
        custom != null && i in custom.indices -> custom[i]
        i in INDEXED.indices -> INDEXED[i] or 0xFF000000.toInt()
        i == 64 -> 0xFF000000.toInt()
        i == 65 -> 0xFFFFFFFF.toInt()
        else -> null
    }

    /** Excel's tint: towards white (positive) or black (negative), on the colour's lightness. */
    fun tint(argb: Int, tint: Double): Int {
        if (tint == 0.0) return argb
        val r = (argb shr 16 and 0xFF) / 255.0; val g = (argb shr 8 and 0xFF) / 255.0; val b = (argb and 0xFF) / 255.0
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
        var l = (mx + mn) / 2
        val d = mx - mn
        val s = if (d == 0.0) 0.0 else d / (1 - kotlin.math.abs(2 * l - 1))
        val h = when {
            d == 0.0 -> 0.0
            mx == r -> 60 * (((g - b) / d).mod(6.0))
            mx == g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }
        l = if (tint < 0) l * (1 + tint) else l * (1 - tint) + tint
        val c = (1 - kotlin.math.abs(2 * l - 1)) * s
        val x = c * (1 - kotlin.math.abs((h / 60).mod(2.0) - 1))
        val m = l - c / 2
        val (r1, g1, b1) = when ((h / 60).toInt()) {
            0 -> Triple(c, x, 0.0); 1 -> Triple(x, c, 0.0); 2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c); 4 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x)
        }
        fun ch(v: Double) = ((v + m) * 255).let { kotlin.math.round(it).toInt().coerceIn(0, 255) }
        return (argb and 0xFF000000.toInt()) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }
}

internal object Sheets {
    fun isSheet(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("xlsx", "xlsm", "ods", "csv", "tsv")

    /** Reads a spreadsheet; [decimal] and [group] are the separators numbers are shown with (the phone's). */
    fun read(bytes: ByteArray, name: String, decimal: Char = '.', group: Char = ','): Workbook =
        when (name.substringAfterLast('.', "").lowercase()) {
            "ods" -> OfficeFormats.unzip(bytes).let { m -> Ods(m::get) { n -> m[n]?.let(::ByteArrayInputStream) }.read() }
            "csv", "tsv" -> csv(bytes)
            else -> OfficeFormats.unzip(bytes).let { m -> Xlsx(m::get, { n -> m[n]?.let(::ByteArrayInputStream) }, decimal, group).read() }
        }

    /**
     * A spreadsheet file, each part taken from the zip on disk when it is needed (the user's 20 MB workbook is 178 MB
     * unzipped: holding it all took the phone's heap to 271 MB).
     */
    fun read(file: java.io.File, decimal: Char = '.', group: Char = ','): Workbook {
        val ext = file.extension.lowercase()
        if (ext == "csv" || ext == "tsv") return csv(file.readBytes())
        val names = java.util.zip.ZipFile(file).use { z -> z.entries().asSequence().map { it.name }.toHashSet() }
        val part = { name: String ->
            if (name !in names) null else java.util.zip.ZipFile(file).use { z -> z.getEntry(name)?.let { e -> z.getInputStream(e).use { it.readBytes() } } }
        }
        // A part to read as it streams (an .ods's content.xml holds every sheet: 360 MB for the user's 46 tables).
        val stream = { name: String ->
            if (name !in names) null else java.util.zip.ZipFile(file).let { z ->
                z.getEntry(name)?.let { e -> object : java.io.FilterInputStream(java.io.BufferedInputStream(z.getInputStream(e), 1 shl 16)) { override fun close() { super.close(); z.close() } } }
                    ?: run { z.close(); null }
            }
        }
        return if (ext == "ods") Ods(part, stream).read() else Xlsx(part, stream, decimal, group).read()
    }

    /** A HYPERLINK formula's address: =HYPERLINK("https://…", "words"). */
    fun hyperlink(formula: String): String? = Regex("""^=?\s*HYPERLINK\(\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(formula)?.groupValues?.get(1)

    /** "B12" → (11, 1); null when it is not a cell's name. */
    fun cellRef(ref: String): Pair<Int, Int>? {
        // By hand: every cell of a sheet has one (a regular expression here took most of a big sheet's reading).
        var i = 0; val s = ref.trim(); var col = 0; var row = 0; var letters = 0; var digits = 0
        if (i < s.length && s[i] == '$') i++
        while (i < s.length && s[i].isLetter()) { val ch = s[i].uppercaseChar(); if (ch !in 'A'..'Z') return null; col = col * 26 + (ch - 'A' + 1); letters++; i++ }
        if (i < s.length && s[i] == '$') i++
        while (i < s.length && s[i] in '0'..'9') { row = row * 10 + (s[i] - '0'); digits++; i++; if (digits > 7) return null }
        if (i != s.length || letters !in 1..3 || digits == 0) return null
        return if (row < 1 || col - 1 > 16383) null else row - 1 to col - 1
    }

    fun rangeRef(ref: String): SheetRange? {
        val parts = ref.split(':')
        val a = cellRef(parts[0]) ?: return null
        val b = parts.getOrNull(1)?.let(::cellRef) ?: a
        return SheetRange(min(a.first, b.first), min(a.second, b.second), max(a.first, b.first), max(a.second, b.second))
    }

    fun colName(c: Int): String = ChartWriter.column(c)

    /**
     * A shared formula as it reads in another cell: its relative references moved by [dr] rows and [dc] columns, as
     * Excel stores one formula for a block of cells and leaves the rest empty.
     */
    fun shift(formula: String, dr: Int, dc: Int): String {
        if (dr == 0 && dc == 0) return formula
        val ref = Regex("""(?<![A-Za-z0-9_.$])(\$?)([A-Z]{1,3})(\$?)([0-9]{1,7})(?![0-9A-Za-z_(])""")
        return formula.split('"').mapIndexed { i, part ->
            if (i % 2 == 1) part else ref.replace(part) { m ->
                val (cAbs, col, rAbs, row) = m.destructured
                val c = col.fold(0) { a, ch -> a * 26 + (ch - 'A' + 1) } - 1
                if (c > 16383) return@replace m.value
                val nc = if (cAbs.isEmpty()) c + dc else c
                val nr = if (rAbs.isEmpty()) row.toInt() + dr else row.toInt()
                if (nc < 0 || nr < 1) "#REF!" else cAbs + colName(nc) + rAbs + nr
            }
        }.joinToString("\"")
    }

    /** Thrown by a handler that has what it came for: the rest of the file is not read. */
    private class Enough : RuntimeException() { override fun fillInStackTrace() = this }

    internal fun sax(input: java.io.InputStream, handler: DefaultHandler) {
        val f = SAXParserFactory.newInstance()
        f.isNamespaceAware = true
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        // A parser may wrap the handler's Enough in its own exception: still the handler's stop.
        try { input.use { f.newSAXParser().parse(it, handler) } } catch (e: Exception) { if (generateSequence<Throwable>(e) { it.cause }.none { it is Enough }) throw e }
    }

    internal fun sax(bytes: ByteArray, handler: DefaultHandler) {
        val f = SAXParserFactory.newInstance()
        f.isNamespaceAware = true
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        f.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
    }

    /** An attribute by its local name, whatever its prefix. */
    internal fun Attributes.a(local: String): String? {
        for (i in 0 until length) if (getLocalName(i) == local) return getValue(i)
        return null
    }

    private fun Element.kids(name: String): List<Element> {
        val out = ArrayList<Element>()
        var n = firstChild
        while (n != null) { if (n is Element && n.localName == name) out += n; n = n.nextSibling }
        return out
    }
    private fun Element.kid(name: String): Element? = kids(name).firstOrNull()
    private fun Element.elements(): List<Element> {
        val out = ArrayList<Element>()
        var n = firstChild
        while (n != null) { if (n is Element) out += n; n = n.nextSibling }
        return out
    }
    private fun Element.below(name: String): List<Element> = getElementsByTagNameNS("*", name).let { l -> (0 until l.length).map { l.item(it) as Element } }
    private fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }
    private fun Element.flag(name: String): Boolean = kid(name)?.let { it.attr("val")?.let { v -> v != "0" && v != "false" && v != "none" } ?: true } ?: false

    // --- .xlsx ---

    private class Xlsx(val parts: (String) -> ByteArray?, val stream: (String) -> java.io.InputStream?, decimal: Char, group: Char) {
        val strings = ArrayList<String>()
        val styles = ArrayList<SheetStyle>()
        val dxfs = ArrayList<SheetDxf>()
        val printNames = HashMap<String, Pair<String?, String?>>()
        val tableStyles = HashMap<String, Map<String, SheetDxf>>()
        var theme = IntArray(0)
        var customIndexed: List<Int>? = null
        var date1904 = false
        lateinit var format: SheetFormat
        val sep = decimal to group

        fun rels(part: String): Map<String, String> {
            val dir = part.substringBeforeLast('/', "")
            val relsPath = (if (dir.isEmpty()) "" else "$dir/") + "_rels/" + part.substringAfterLast('/') + ".rels"
            val bytes = parts(relsPath) ?: return emptyMap()
            val out = HashMap<String, String>()
            val doc = OfficeFormats.parse(bytes)
            val list = doc.getElementsByTagNameNS("*", "Relationship")
            for (i in 0 until list.length) {
                val e = list.item(i) as Element
                val target = e.getAttribute("Target")
                out[e.getAttribute("Id")] = if (e.getAttribute("TargetMode") == "External") "ext:$target" else resolve(dir, target)
            }
            return out
        }

        fun resolve(dir: String, target: String): String {
            if (target.startsWith("/")) return target.removePrefix("/")
            val path = ArrayList(if (dir.isEmpty()) emptyList() else dir.split('/'))
            target.split('/').forEach { if (it == "..") path.removeLastOrNull() else if (it != ".") path += it }
            return path.joinToString("/")
        }

        fun read(): Workbook {
            val book = rels("").values.firstOrNull { it.endsWith("workbook.xml") } ?: "xl/workbook.xml"
            val bookRels = rels(book)
            val wb = OfficeFormats.parse(parts(book) ?: error("Not a spreadsheet.")).documentElement
            date1904 = wb.kid("workbookPr")?.attr("date1904")?.let { it == "1" || it == "true" } ?: false
            format = SheetFormat(sep.first, sep.second, date1904)
            bookRels.values.firstOrNull { it.contains("theme/") }?.let(parts)?.let(::readTheme)
            bookRels.values.firstOrNull { it.endsWith("sharedStrings.xml") }?.let(stream)?.let(::readStrings)
            bookRels.values.firstOrNull { it.endsWith("styles.xml") }?.let(parts)?.let(::readStyles)
            if (styles.isEmpty()) styles += SheetStyle()
            val active = wb.kid("bookViews")?.kid("workbookView")?.attr("activeTab")?.toIntOrNull() ?: 0
            // Print areas and rows printed on every page: workbook names kept for each sheet (localSheetId).
            val names = wb.kid("definedNames")?.kids("definedName").orEmpty()
            fun named(name: String, sheet: Int) = names.firstOrNull { it.attr("name") == name && it.attr("localSheetId")?.toIntOrNull() == sheet }?.textContent
            val found = wb.kid("sheets")?.kids("sheet").orEmpty().mapIndexedNotNull { i, s ->
                val rid = s.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id").ifEmpty { s.attr("r:id") ?: "" }
                val path = bookRels[rid] ?: return@mapIndexedNotNull null
                printNames[path] = named("_xlnm.Print_Area", i) to named("_xlnm.Print_Titles", i)
                SheetTab(s.attr("name") ?: "Sheet", s.attr("state").let { it == "hidden" || it == "veryHidden" }) to path
            }
            if (found.isEmpty()) error("No sheets.")
            WriterLog.i("Sheets: xlsx, ${found.size} sheets, ${strings.size} strings, ${styles.size} looks")
            return Workbook(found.map { it.first }, styles, active.coerceIn(0, found.lastIndex)) { i ->
                val (tab, path) = found[i]
                runCatching { readSheet(tab.name, path, stream(path) ?: error("No such part."), tab.hidden) }.getOrElse {
                    WriterLog.w("Sheets: sheet ${i + 1} could not be read", it)
                    Sheet(tab.name, CellMap(), 0, 0, emptyMap(), emptyMap(), 64f, 20f, emptySet(), emptySet(), emptyList(), 0, 0, hidden = tab.hidden)
                }
            }
        }

        fun readTheme(bytes: ByteArray) {
            val doc = OfficeFormats.parse(bytes)
            val scheme = doc.getElementsByTagNameNS("*", "clrScheme").item(0) as? Element ?: return
            val order = listOf("lt1", "dk1", "lt2", "dk2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink")
            theme = order.map { n ->
                val e = scheme.kid(n)
                val c = e?.kid("srgbClr")?.attr("val") ?: e?.kid("sysClr")?.attr("lastClr") ?: "000000"
                (c.toLongOrNull(16)?.toInt() ?: 0) or 0xFF000000.toInt()
            }.toIntArray()
        }

        fun readStrings(input: java.io.InputStream) = sax(input, object : DefaultHandler() {
            val sb = StringBuilder(); var inT = false; var phonetic = 0
            override fun startElement(uri: String?, local: String, q: String?, at: Attributes) {
                when (local) { "si" -> sb.setLength(0); "t" -> inT = phonetic == 0; "rPh" -> phonetic++ }
            }
            override fun endElement(uri: String?, local: String, q: String?) {
                when (local) { "si" -> strings += sb.toString(); "t" -> inT = false; "rPh" -> phonetic-- }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (inT) sb.appendRange(ch, start, start + length) }
        })

        fun color(e: Element?): Int? {
            e ?: return null
            if (e.attr("auto") == "1") return null
            val base = e.attr("rgb")?.let { (it.takeLast(6).toLongOrNull(16)?.toInt() ?: return null) or 0xFF000000.toInt() }
                ?: e.attr("theme")?.toIntOrNull()?.let { theme.getOrNull(it) }
                ?: e.attr("indexed")?.toIntOrNull()?.let { SheetColors.indexed(it, customIndexed) }
                ?: return null
            return SheetColors.tint(base, e.attr("tint")?.toDoubleOrNull() ?: 0.0)
        }

        fun line(e: Element?): SheetLine? {
            val style = e?.attr("style") ?: return null
            if (style == "none") return null
            val c = color(e.kid("color")) ?: 0xFF000000.toInt()
            return when (style) {
                "medium", "mediumDashed", "mediumDashDot", "mediumDashDotDot", "slantDashDot" -> SheetLine(c, 2f, dashed = style != "medium")
                "thick" -> SheetLine(c, 3f)
                "double" -> SheetLine(c, 3f, double = true)
                "hair", "dotted" -> SheetLine(c, 1f, dots = true)
                "dashed", "dashDot", "dashDotDot" -> SheetLine(c, 1f, dashed = true)
                else -> SheetLine(c, 1f)
            }
        }

        fun readStyles(bytes: ByteArray) {
            val root = OfficeFormats.parse(bytes).documentElement
            root.kid("colors")?.kid("indexedColors")?.kids("rgbColor")?.map { (it.attr("rgb")?.takeLast(6)?.toLongOrNull(16)?.toInt() ?: 0) or 0xFF000000.toInt() }
                ?.takeIf { it.isNotEmpty() }?.let { customIndexed = it }
            val formats = HashMap<Int, String>(SheetFormat.BUILT_IN)
            root.kid("numFmts")?.kids("numFmt")?.forEach { f -> f.attr("numFmtId")?.toIntOrNull()?.let { formats[it] = f.getAttribute("formatCode") } }
            val fonts = root.kid("fonts")?.kids("font").orEmpty()
            val fills = root.kid("fills")?.kids("fill").orEmpty().map { f ->
                f.kid("patternFill")?.let { p ->
                    when (p.attr("patternType") ?: if (p.kid("fgColor") != null) "solid" else "none") {
                        "none" -> null
                        "gray125", "gray0625" -> color(p.kid("fgColor")) ?: 0xFFE0E0E0.toInt()
                        else -> color(p.kid("fgColor")) ?: color(p.kid("bgColor"))
                    }
                } ?: f.kid("gradientFill")?.kids("stop")?.firstOrNull()?.let { color(it.kid("color")) }
            }
            val borders = root.kid("borders")?.kids("border").orEmpty()
            root.kid("dxfs")?.kids("dxf")?.forEach { d ->
                val font = d.kid("font"); val p = d.kid("fill")?.kid("patternFill"); val b = d.kid("border")
                dxfs += SheetDxf(
                    // A dxf's solid fill is its bgColor (fgColor is the pattern's), as Excel reads it.
                    fill = p?.takeIf { it.attr("patternType") != "none" }?.let { color(it.kid("bgColor")) ?: color(it.kid("fgColor")) },
                    color = color(font?.kid("color")), bold = font?.kid("b")?.let { font.flag("b") }, italic = font?.kid("i")?.let { font.flag("i") },
                    left = line(b?.kid("left") ?: b?.kid("start")), right = line(b?.kid("right") ?: b?.kid("end")), top = line(b?.kid("top")),
                    bottom = line(b?.kid("bottom")), horizontal = line(b?.kid("horizontal")), vertical = line(b?.kid("vertical")),
                )
            }
            root.kid("tableStyles")?.kids("tableStyle")?.forEach { t ->
                val name = t.attr("name") ?: return@forEach
                tableStyles[name] = t.kids("tableStyleElement").mapNotNull { e -> e.attr("type")?.let { it to (dxfs.getOrNull(e.attr("dxfId")?.toIntOrNull() ?: -1) ?: SheetDxf()) } }.toMap()
            }
            root.kid("cellXfs")?.kids("xf")?.forEach { xf ->
                val font = fonts.getOrNull(xf.attr("fontId")?.toIntOrNull() ?: 0)
                val border = borders.getOrNull(xf.attr("borderId")?.toIntOrNull() ?: 0)
                val al = xf.kid("alignment")
                styles += SheetStyle(
                    font = font?.kid("name")?.attr("val"),
                    size = font?.kid("sz")?.attr("val")?.toFloatOrNull() ?: 11f,
                    bold = font?.flag("b") == true, italic = font?.flag("i") == true,
                    underline = font?.flag("u") == true, strike = font?.flag("strike") == true,
                    color = color(font?.kid("color")),
                    fill = fills.getOrNull(xf.attr("fillId")?.toIntOrNull() ?: 0),
                    align = when (al?.attr("horizontal")) {
                        "left" -> SheetAlign.Left; "center", "centerContinuous" -> SheetAlign.Center; "right" -> SheetAlign.Right
                        "justify", "distributed" -> SheetAlign.Justify; else -> SheetAlign.General
                    },
                    valign = when (al?.attr("vertical")) { "top" -> SheetVAlign.Top; "center", "justify", "distributed" -> SheetVAlign.Center; else -> SheetVAlign.Bottom },
                    wrap = al?.attr("wrapText")?.let { it == "1" || it == "true" } == true,
                    indent = al?.attr("indent")?.toIntOrNull() ?: 0,
                    rotation = al?.attr("textRotation")?.toIntOrNull() ?: 0,
                    format = formats[xf.attr("numFmtId")?.toIntOrNull() ?: 0] ?: "General",
                    left = line(border?.kid("left") ?: border?.kid("start")), right = line(border?.kid("right") ?: border?.kid("end")),
                    top = line(border?.kid("top")), bottom = line(border?.kid("bottom")),
                )
            }
        }

        /** Excel's column width (characters of the default font's digit, 7 px in Calibri 11) in pixels. */
        fun colPx(w: Double) = floor(((256 * w + floor(128.0 / 7)) / 256) * 7).toFloat()

        /** A sheet's part, streamed from the zip (a sheet's XML can be tens of MB). */
        fun readSheet(name: String, path: String, input: java.io.InputStream, hidden: Boolean): Sheet {
            val rels = rels(path)
            val cells = CellMap(1 shl 12)
            val colWidths = HashMap<Int, Float>(); val rowHeights = HashMap<Int, Float>()
            val hiddenCols = HashSet<Int>(); val hiddenRows = HashSet<Int>()
            val colStyles = HashMap<Int, Int>(); val rowStyles = HashMap<Int, Int>()
            val merges = ArrayList<SheetRange>()
            val links = HashMap<Long, String>()
            val tableParts = ArrayList<String>()
            val empties = HashMap<Int, SheetCell>()
            var drawingPart: String? = null
            val rules = ArrayList<SheetRule>()
            val page = HashMap<String, String>() // pageSetup, pageSetUpPr, printOptions and pageMargins, as they are
            val shared = HashMap<String, Triple<String, Int, Int>>()
            var defaultCol = 64f; var defaultRow = 20f
            var frozenRows = 0; var frozenCols = 0; var grid = true; var views = 0
            var tab: Int? = null; var autoFilter: SheetRange? = null
            var maxRow = -1; var maxCol = -1
            val boxed = Extent()
            sax(input, object : DefaultHandler() {
                var row = -1; var col = -1
                var r = 0; var c = 0; var s = 0; var t: String? = null
                val v = StringBuilder(); val f = StringBuilder(); val inline = StringBuilder()
                var into: StringBuilder? = null
                var hasV = false; var fAttrs: Triple<String?, String?, String?>? = null; var inIs = false; var phonetic = 0
                // conditional formatting: the ranges, the rule being read, its formulas and its scale's or bar's points
                var ext = 0; var cfRanges = emptyList<SheetRange>(); var rule: Map<String, String>? = null
                val formula = StringBuilder(); val formulas = ArrayList<String>(); val cfvos = ArrayList<SheetCfvo>(); val colors = ArrayList<Int>()
                fun color(at: Attributes): Int? {
                    val base = at.a("rgb")?.let { (it.takeLast(6).toLongOrNull(16)?.toInt() ?: return null) or 0xFF000000.toInt() }
                        ?: at.a("theme")?.toIntOrNull()?.let { theme.getOrNull(it) } ?: at.a("indexed")?.toIntOrNull()?.let { SheetColors.indexed(it, customIndexed) } ?: return null
                    return SheetColors.tint(base, at.a("tint")?.toDoubleOrNull() ?: 0.0)
                }
                override fun startElement(uri: String?, local: String, q: String?, at: Attributes) {
                    if (local == "extLst") ext++
                    if (ext > 0) return // Excel 2010's copies of rules (and other extensions) are left out
                    when (local) {
                        "conditionalFormatting" -> cfRanges = at.a("sqref").orEmpty().split(' ').mapNotNull(::rangeRef)
                        "pageSetup", "pageSetUpPr", "printOptions", "pageMargins" -> for (i in 0 until at.length) page["$local.${at.getLocalName(i)}"] = at.getValue(i)
                        "cfRule" -> { rule = (0 until at.length).associate { at.getLocalName(it) to at.getValue(it) }; formulas.clear(); cfvos.clear(); colors.clear() }
                        "formula" -> if (rule != null) { formula.setLength(0); into = formula }
                        "cfvo" -> if (rule != null) cfvos += SheetCfvo(at.a("type") ?: "min", at.a("val"))
                        "color" -> if (rule != null) color(at)?.let { colors += it }
                        "sheetView" -> { views++; if (views == 1 && at.a("showGridLines").let { it == "0" || it == "false" }) grid = false }
                        "pane" -> if (views == 1 && at.a("state")?.startsWith("frozen") == true) {
                            frozenCols = at.a("xSplit")?.toDoubleOrNull()?.toInt() ?: 0
                            frozenRows = at.a("ySplit")?.toDoubleOrNull()?.toInt() ?: 0
                        }
                        "tabColor" -> tab = at.a("rgb")?.let { (it.takeLast(6).toLongOrNull(16)?.toInt() ?: 0) or 0xFF000000.toInt() }
                            ?: at.a("theme")?.toIntOrNull()?.let { theme.getOrNull(it) }
                        "sheetFormatPr" -> {
                            at.a("defaultRowHeight")?.toFloatOrNull()?.let { defaultRow = it * 4 / 3 }
                            defaultCol = at.a("defaultColWidth")?.toDoubleOrNull()?.let(::colPx)
                                ?: at.a("baseColWidth")?.toIntOrNull()?.let { (kotlin.math.ceil((it * 7 + 5) / 8.0) * 8).toFloat() } ?: 64f
                        }
                        "col" -> {
                            val a = (at.a("min")?.toIntOrNull() ?: return) - 1
                            val b = min((at.a("max")?.toIntOrNull() ?: (a + 1)) - 1, 16383)
                            val w = at.a("width")?.toDoubleOrNull()?.let(::colPx)
                            val hide = at.a("hidden").let { it == "1" || it == "true" }
                            val style = at.a("style")?.toIntOrNull()
                            for (k in a..b) {
                                if (w != null) colWidths[k] = w
                                if (hide) hiddenCols += k
                                if (style != null && style != 0 && b - a < 1024) colStyles[k] = style
                            }
                        }
                        "row" -> {
                            row = at.a("r")?.toIntOrNull()?.minus(1) ?: (row + 1); col = -1
                            at.a("ht")?.toFloatOrNull()?.let { rowHeights[row] = it * 4 / 3 }
                            if (at.a("hidden").let { it == "1" || it == "true" }) hiddenRows += row
                            if (at.a("customFormat").let { it == "1" || it == "true" }) at.a("s")?.toIntOrNull()?.takeIf { it != 0 }?.let { rowStyles[row] = it }
                        }
                        "c" -> {
                            val ref = at.a("r")?.let(::cellRef)
                            r = ref?.first ?: max(row, 0); c = ref?.second ?: (col + 1); col = c
                            s = at.a("s")?.toIntOrNull() ?: 0; t = at.a("t")
                            v.setLength(0); f.setLength(0); inline.setLength(0); hasV = false; fAttrs = null
                        }
                        "v" -> { into = v; hasV = true }
                        "f" -> { into = f; fAttrs = Triple(at.a("t"), at.a("si"), at.a("ref")) }
                        "is" -> inIs = true
                        "rPh" -> phonetic++
                        "t" -> if (inIs && phonetic == 0) into = inline
                        "mergeCell" -> at.a("ref")?.let(::rangeRef)?.let { merges += it }
                        "autoFilter" -> autoFilter = at.a("ref")?.let(::rangeRef)
                        "tablePart" -> at.a("id")?.let { rels[it] }?.let { tableParts += it }
                        "drawing" -> drawingPart = at.a("id")?.let { rels[it] }
                        "hyperlink" -> {
                            val range = at.a("ref")?.let(::rangeRef) ?: return
                            val target = at.a("id")?.let { rels[it] }?.removePrefix("ext:") ?: at.a("location")?.let { "#$it" } ?: return
                            for (rr in range.r1..min(range.r2, range.r1 + 1000)) for (cc in range.c1..min(range.c2, range.c1 + 100)) links[Sheet.key(rr, cc)] = target
                        }
                    }
                }
                override fun endElement(uri: String?, local: String, q: String?) {
                    if (local == "extLst") { ext--; return }
                    if (ext > 0) return
                    when (local) {
                        "formula" -> if (rule != null) { formulas += formula.toString(); into = null }
                        "cfRule" -> rule?.let { a ->
                            val type = a["type"] ?: return@let
                            fun flag(n: String, default: Boolean) = a[n]?.let { it == "1" || it == "true" } ?: default
                            rules += SheetRule(cfRanges, a["priority"]?.toIntOrNull() ?: 1000, type, a["operator"], formulas.toList(),
                                a["dxfId"]?.toIntOrNull()?.let { dxfs.getOrNull(it) }, a["text"], a["rank"]?.toIntOrNull() ?: 10,
                                flag("percent", false), flag("bottom", false), flag("aboveAverage", true), flag("equalAverage", false), flag("stopIfTrue", false),
                                scale = if (type == "colorScale") cfvos.zip(colors) else emptyList(),
                                bar = colors.firstOrNull().takeIf { type == "dataBar" }, barEnds = if (type == "dataBar") cfvos.toList() else emptyList())
                            rule = null
                        }
                        "v", "f", "t" -> into = null
                        "is" -> inIs = false
                        "rPh" -> phonetic--
                        "c" -> cell()
                    }
                }
                override fun characters(ch: CharArray, start: Int, length: Int) { into?.appendRange(ch, start, start + length) }

                fun cell() {
                    var formula = f.toString().takeIf { it.isNotEmpty() }
                    fAttrs?.let { (type, si, ref) ->
                        if (type == "shared" && si != null) {
                            if (formula != null && ref != null) shared[si] = Triple(formula, r, c)
                            else shared[si]?.let { (base, br, bc) -> formula = shift(base, r - br, c - bc) }
                        }
                    }
                    val style = styles.getOrNull(s) ?: styles.firstOrNull() ?: SheetStyle()
                    val raw = v.toString()
                    val cell = when (t) {
                        "s" -> SheetCell(strings.getOrNull(raw.trim().toIntOrNull() ?: -1) ?: "", SheetKind.Text, style = s)
                        "inlineStr" -> SheetCell(inline.toString(), SheetKind.Text, style = s)
                        "str" -> SheetCell(raw, SheetKind.Text, style = s)
                        "b" -> SheetCell(if (raw.trim() == "1") "TRUE" else "FALSE", SheetKind.Bool, if (raw.trim() == "1") 1.0 else 0.0, style = s)
                        "e" -> SheetCell(raw, SheetKind.Error, style = s)
                        "d" -> runCatching { LocalDateTime.parse(raw.trim().let { if ('T' in it) it else "${it}T00:00" }) }.getOrNull()?.let { dt ->
                            val serial = ChronoUnit.SECONDS.between(LocalDate.of(1899, 12, 30).atStartOfDay(), dt) / 86_400.0
                            val shown = format.format(serial, style.format.takeIf { format.isDate(it) } ?: "d/m/yyyy")
                            SheetCell(shown.text, SheetKind.Number, serial, style = s, color = shown.color)
                        } ?: SheetCell(raw, SheetKind.Text, style = s)
                        else -> raw.trim().toDoubleOrNull()?.let { n ->
                            val shown = runCatching { format.format(n, style.format) }.getOrElse { SheetFormat.Shown(format.general(n)) }
                            SheetCell(shown.text, SheetKind.Number, n, style = s, color = shown.color)
                        } ?: empties.getOrPut(s) { SheetCell("", SheetKind.Empty, style = s) } // one for every empty cell of a look
                    }.let { if (formula != null) it.copy(formula = "=$formula", link = hyperlink(formula!!)) else it }
                    if (cell.kind == SheetKind.Empty && cell.formula == null && !style.boxed) return
                    cells[Sheet.key(r, c)] = cell
                    if (cell.kind != SheetKind.Empty) { maxRow = max(maxRow, r); maxCol = max(maxCol, c) } else boxed.add(r, c)
                }
            })
            links.forEach { (k, target) -> cells[k]?.let { cells[k] = it.copy(link = target) } }
            runCatching { notes(rels).forEach { (k, note) -> cells[k] = (cells[k] ?: SheetCell("", SheetKind.Empty)).copy(note = note) } }
                .onFailure { WriterLog.w("Sheets: comments could not be read", it) }
            val looks = ArrayList<SheetStyle>()
            tableParts.forEach { p -> runCatching { parts(p)?.let { table(it, cells, looks) } }.onFailure { WriterLog.w("Sheets: a table could not be read", it) } }
            val bars = runCatching { SheetRules.apply(rules, cells, { i -> (if (i >= Sheet.LOOKS) looks.getOrNull(i - Sheet.LOOKS) else styles.getOrNull(i)) ?: SheetStyle() }, looks) }
                .onFailure { WriterLog.w("Sheets: conditional formatting could not be laid", it) }.getOrDefault(emptyMap())
            if (rules.isNotEmpty()) WriterLog.i("Sheets: ${rules.size} conditional rules (${rules.map { it.type }.distinct().joinToString()})")
            merges.forEach { maxRow = max(maxRow, it.r2); maxCol = max(maxCol, it.c2) }
            maxRow = boxed.rows(maxRow); maxCol = boxed.cols(maxCol)
            WriterLog.i("Sheets: \"${name.length} letters\" ${cells.size} cells, ${maxRow + 1}×${maxCol + 1}, ${merges.size} merged, frozen $frozenRows/$frozenCols")
            return Sheet(name, cells, maxRow + 1, maxCol + 1, colWidths, rowHeights, defaultCol, defaultRow, hiddenCols, hiddenRows,
                merges, frozenRows, frozenCols, grid, colStyles, rowStyles, tab, hidden, autoFilter, looks,
                drawingPart?.let { p -> runCatching { drawings(p, name, cells) }.onFailure { WriterLog.w("Sheets: drawings could not be read", it) }.getOrNull() }.orEmpty(), bars,
                printOf(page, printNames[path]))
        }

        /** Excel's page setup and the sheet's print names as a [SheetPrint]. */
        fun printOf(page: Map<String, String>, names: Pair<String?, String?>?): SheetPrint {
            fun on(k: String) = page[k].let { it == "1" || it == "true" }
            val fit = on("pageSetUpPr.fitToPage")
            val area = names?.first?.split(',')?.firstOrNull()?.substringAfterLast('!')?.replace("$", "")?.let(::rangeRef)
            val titles = names?.second?.split(',')?.firstNotNullOfOrNull { Regex("""!\$?(\d+):\$?(\d+)$""").find(it) }?.let { it.groupValues[1].toInt() - 1..it.groupValues[2].toInt() - 1 }
            return SheetPrint(
                landscape = page["pageSetup.orientation"] == "landscape",
                scale = page["pageSetup.scale"]?.toIntOrNull()?.coerceIn(10, 400) ?: 100,
                fitWidth = if (fit) page["pageSetup.fitToWidth"]?.toIntOrNull() ?: 1 else null,
                fitHeight = if (fit) page["pageSetup.fitToHeight"]?.toIntOrNull() ?: 1 else null,
                grid = on("printOptions.gridLines"),
                margins = listOf("left", "right", "top", "bottom").map { page["pageMargins.$it"]?.toFloatOrNull() ?: if (it == "left" || it == "right") 0.7f else 0.75f },
                area = area, titles = titles, letter = page["pageSetup.paperSize"] == "1",
            )
        }

        /** The sheet's drawing part: each anchor's picture, chart or text box, where it sits. */
        fun drawings(path: String, sheetName: String, cells: CellMap): List<SheetDrawing> {
            val bytes = parts(path) ?: return emptyList()
            val rels = rels(path)
            val emu = 9525f // EMUs in a pixel
            val out = ArrayList<SheetDrawing>()
            for (a in OfficeFormats.parse(bytes).documentElement.elements()) {
                if (a.localName !in setOf("twoCellAnchor", "oneCellAnchor", "absoluteAnchor")) continue
                fun at(e: Element?, name: String) = e?.kid(name)?.textContent?.trim()?.toLongOrNull() ?: 0L
                val from = a.kid("from"); val to = a.kid("to"); val ext = a.kid("ext"); val pos = a.kid("pos")
                val body = a.elements().firstOrNull { it.localName in setOf("pic", "graphicFrame", "sp") } ?: continue
                val chart = if (body.localName == "graphicFrame") body.below("chart").firstOrNull()?.let { c ->
                    val id = c.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    rels[id]?.let(parts)?.let { b -> OfficeCharts.readDrawingml(b)?.let { ch -> withValues(ch, b, sheetName, cells) } }
                } ?: continue else null
                val image = if (body.localName == "pic") body.below("blip").firstOrNull()?.let { b ->
                    val p = rels[b.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")] ?: return@let null
                    { parts(p) }
                } ?: continue else null
                val text = if (body.localName == "sp") body.below("p").joinToString("\n") { p -> p.below("t").joinToString("") { it.textContent } }.trim() else null
                fun solid(e: Element?) = e?.kid("solidFill")?.let { f ->
                    f.kid("srgbClr")?.attr("val")?.toLongOrNull(16)?.toInt()?.or(0xFF000000.toInt())
                        ?: f.kid("schemeClr")?.attr("val")?.removePrefix("accent")?.toIntOrNull()?.let { theme.getOrNull(3 + it) }
                }
                val spPr = body.kid("spPr")
                if (body.localName == "sp" && text.isNullOrEmpty() && solid(spPr) == null) continue
                val x = at(from, "colOff") / emu; val y = at(from, "rowOff") / emu
                out += if (a.localName == "absoluteAnchor") SheetDrawing(0, 0, (pos?.attr("x")?.toLongOrNull() ?: 0) / emu, (pos?.attr("y")?.toLongOrNull() ?: 0) / emu,
                    width = (ext?.attr("cx")?.toLongOrNull() ?: 0) / emu, height = (ext?.attr("cy")?.toLongOrNull() ?: 0) / emu, chart = chart, image = image, text = text, fill = solid(spPr), line = solid(spPr?.kid("ln")))
                else if (to != null) SheetDrawing(at(from, "row").toInt(), at(from, "col").toInt(), x, y, at(to, "row").toInt(), at(to, "col").toInt(), at(to, "colOff") / emu, at(to, "rowOff") / emu,
                    chart = chart, image = image, text = text, fill = solid(spPr), line = solid(spPr?.kid("ln")))
                else SheetDrawing(at(from, "row").toInt(), at(from, "col").toInt(), x, y, width = (ext?.attr("cx")?.toLongOrNull() ?: 0) / emu, height = (ext?.attr("cy")?.toLongOrNull() ?: 0) / emu,
                    chart = chart, image = image, text = text, fill = solid(spPr), line = solid(spPr?.kid("ln")))
            }
            if (out.isNotEmpty()) WriterLog.i("Sheets: ${out.size} drawings (${out.count { it.chart != null }} charts, ${out.count { it.image != null }} pictures)")
            return out
        }

        /**
         * A chart whose file keeps no numbers of its own (Excel and LibreOffice keep them; some programs only name the
         * cells): its series read from this sheet's cells.
         */
        fun withValues(chart: DocChart, bytes: ByteArray, sheetName: String, cells: CellMap): DocChart {
            if (chart.series.any { s -> s.values.any { it != null } }) return chart
            fun range(f: String?): SheetRange? {
                val ref = f?.trim() ?: return null
                val sheet = ref.substringBeforeLast('!', "").trim('\'')
                if (sheet.isNotEmpty() && sheet != sheetName) return null
                return rangeRef(ref.substringAfterLast('!').replace("$", ""))
            }
            fun cellsOf(g: SheetRange) = (g.r1..g.r2).flatMap { r -> (g.c1..g.c2).map { c -> cells[Sheet.key(r, c)] } }
            val sers = OfficeFormats.parse(bytes).documentElement.below("ser")
            var categories = chart.categories
            val series = chart.series.mapIndexed { i, s ->
                val e = sers.getOrNull(i) ?: return@mapIndexed s
                val values = range(e.kid("val")?.below("f")?.firstOrNull()?.textContent ?: e.kid("yVal")?.below("f")?.firstOrNull()?.textContent)
                    ?.let(::cellsOf)?.map { it?.value } ?: return@mapIndexed s
                range((e.kid("cat") ?: e.kid("xVal"))?.below("f")?.firstOrNull()?.textContent)?.let(::cellsOf)?.map { it?.text.orEmpty() }?.let { if (it.size >= categories.size) categories = it }
                val name = range(e.kid("tx")?.below("f")?.firstOrNull()?.textContent)?.let(::cellsOf)?.firstOrNull()?.text
                s.copy(values = values, name = name ?: s.name)
            }
            if (categories.size < (series.maxOfOrNull { it.values.size } ?: 0)) categories = (1..series.maxOf { it.values.size }).map { "$it" }
            return chart.copy(categories = categories, series = series)
        }

        /**
         * A sheet's comments: Excel's notes (comments1.xml, "Author:" as Excel writes it), and its threaded comments with
         * their replies, which take the place of the note Excel keeps for older versions.
         */
        fun notes(rels: Map<String, String>): Map<Long, String> {
            val out = HashMap<Long, String>()
            rels.values.firstOrNull { it.substringAfterLast('/').startsWith("comments") }?.let(parts)?.let { bytes ->
                val root = OfficeFormats.parse(bytes).documentElement
                val authors = root.kid("authors")?.kids("author")?.map { it.textContent }.orEmpty()
                root.kid("commentList")?.kids("comment")?.forEach { c ->
                    val at = c.attr("ref")?.let(::rangeRef) ?: return@forEach
                    val text = c.getElementsByTagNameNS("*", "t").let { l -> (0 until l.length).joinToString("") { l.item(it).textContent } }.trim()
                    val author = authors.getOrNull(c.attr("authorId")?.toIntOrNull() ?: -1)?.takeIf { it.isNotBlank() && !text.startsWith("$it:") }
                    out[Sheet.key(at.r1, at.c1)] = (author?.let { "$it: " } ?: "") + text
                }
            }
            rels.values.firstOrNull { it.substringAfterLast('/').startsWith("threadedComment") }?.let(parts)?.let { bytes ->
                val people = rels.values.firstOrNull { it.contains("person") }?.let(parts)?.let { p ->
                    val l = OfficeFormats.parse(p).getElementsByTagNameNS("*", "person")
                    (0 until l.length).associate { i -> (l.item(i) as Element).let { it.getAttribute("id") to it.getAttribute("displayName") } }
                } ?: parts("xl/persons/person.xml")?.let { p ->
                    val l = OfficeFormats.parse(p).getElementsByTagNameNS("*", "person")
                    (0 until l.length).associate { i -> (l.item(i) as Element).let { it.getAttribute("id") to it.getAttribute("displayName") } }
                }.orEmpty()
                val thread = LinkedHashMap<Long, MutableList<String>>()
                val l = OfficeFormats.parse(bytes).getElementsByTagNameNS("*", "threadedComment")
                for (i in 0 until l.length) {
                    val c = l.item(i) as Element
                    val at = c.attr("ref")?.let(::rangeRef) ?: continue
                    val who = people[c.getAttribute("personId")]?.takeIf { it.isNotBlank() }
                    val text = c.getElementsByTagNameNS("*", "text").item(0)?.textContent?.trim().orEmpty()
                    thread.getOrPut(Sheet.key(at.r1, at.c1)) { ArrayList() } += (who?.let { "$it: " } ?: "") + text
                }
                thread.forEach { (k, list) -> out[k] = list.joinToString("\n") }
            }
            return out
        }

        /**
         * An Excel Table (Insert › Table, and Google Sheets' alternating colours): its style's header, stripes, first
         * and last column and totals laid on its cells — under each cell's own look, as Excel does. The cells' new looks
         * go in [looks], numbered after the workbook's.
         */
        fun table(bytes: ByteArray, cells: CellMap, looks: ArrayList<SheetStyle>) {
            val t = OfficeFormats.parse(bytes).documentElement
            val ref = t.attr("ref")?.let(::rangeRef) ?: return
            val info = t.kid("tableStyleInfo") ?: return
            val name = info.attr("name") ?: return
            val parts = tableStyles[name] ?: builtInTable(name) ?: return WriterLog.i("Sheets: table style not known: built-in ${name.startsWith("TableStyle")}")
            if ((ref.r2 - ref.r1 + 1L) * (ref.c2 - ref.c1 + 1L) > 500_000) return
            fun on(a: String) = info.attr(a).let { it == "1" || it == "true" }
            val headers = t.attr("headerRowCount")?.toIntOrNull() ?: 1
            val totals = t.attr("totalsRowCount")?.toIntOrNull() ?: 0
            val dataTop = ref.r1 + headers; val dataBottom = ref.r2 - totals
            val index = HashMap<SheetStyle, Int>()
            for (r in ref.r1..ref.r2) for (c in ref.c1..ref.c2) {
                val header = r < dataTop; val total = r > dataBottom
                var d = SheetDxf()
                fun lay(type: String) { val e = parts[type] ?: return
                    // A part's outer lines go round the whole table (or row, column); its inner ones between cells.
                    d += e.copy(left = if (c == ref.c1) e.left else e.vertical, right = if (c == ref.c2) e.right else e.vertical,
                        top = if (r == ref.r1) e.top else e.horizontal, bottom = if (r == ref.r2) e.bottom else e.horizontal)
                }
                lay("wholeTable")
                if (on("showColumnStripes") && !header && !total) lay(if ((c - ref.c1) % 2 == 0) "firstColumnStripe" else "secondColumnStripe")
                if (on("showRowStripes") && !header && !total) lay(if ((r - dataTop) % 2 == 0) "firstRowStripe" else "secondRowStripe")
                if (on("showLastColumn") && c == ref.c2) lay("lastColumn")
                if (on("showFirstColumn") && c == ref.c1) lay("firstColumn")
                if (header) { lay("headerRow"); if (c == ref.c1 && on("showFirstColumn")) lay("firstHeaderCell"); if (c == ref.c2 && on("showLastColumn")) lay("lastHeaderCell") }
                if (total) { lay("totalRow"); if (c == ref.c1) lay("firstTotalCell"); if (c == ref.c2) lay("lastTotalCell") }
                if (d == SheetDxf()) continue
                val k = Sheet.key(r, c)
                val cell = cells[k] ?: SheetCell("", SheetKind.Empty)
                val own = (if (cell.style >= Sheet.LOOKS) looks.getOrNull(cell.style - Sheet.LOOKS) else styles.getOrNull(cell.style)) ?: SheetStyle()
                val look = own.copy(
                    fill = own.fill ?: d.fill, color = own.color ?: d.color, bold = own.bold || d.bold == true, italic = own.italic || d.italic == true,
                    left = own.left ?: d.left, right = own.right ?: d.right, top = own.top ?: d.top, bottom = own.bottom ?: d.bottom,
                )
                if (look == own) continue
                cells[k] = cell.copy(style = Sheet.LOOKS + index.getOrPut(look) { looks += look; looks.lastIndex })
            }
        }

        /**
         * Excel's built-in table styles, made from the theme's colours as Excel makes them: Light, Medium and Dark, each
         * in black and the six accents. shortcut: the common parts (header, stripes, lines) close to Excel's, not every
         * line of all 60; upgrade from Excel's own definitions (presetTableStyles.xml) if a file shows the difference.
         */
        fun builtInTable(name: String): Map<String, SheetDxf>? {
            val m = Regex("TableStyle(Light|Medium|Dark)(\\d+)").matchEntire(name) ?: return null
            val n = m.groupValues[2].toInt()
            val k = (n - 1) % 7; val group = (n - 1) / 7
            val black = 0xFF000000.toInt(); val white = 0xFFFFFFFF.toInt()
            val base = if (k == 0) black else theme.getOrNull(3 + k) ?: return null
            fun t(x: Double) = SheetColors.tint(base, x)
            fun thin(c: Int) = SheetLine(c, 1f)
            val boxed = SheetDxf(left = thin(base), right = thin(base), top = thin(base), bottom = thin(base))
            val total = SheetDxf(bold = true, top = SheetLine(base, 3f, double = true))
            return when (m.groupValues[1]) {
                "Light" -> when (group) {
                    0 -> mapOf("wholeTable" to SheetDxf(top = thin(base), bottom = thin(base)), "headerRow" to SheetDxf(bold = true, bottom = thin(base)),
                        "firstRowStripe" to SheetDxf(fill = t(0.8)), "firstColumn" to SheetDxf(bold = true), "lastColumn" to SheetDxf(bold = true), "totalRow" to total)
                    1 -> mapOf("wholeTable" to boxed, "headerRow" to SheetDxf(fill = base, color = white, bold = true),
                        "firstRowStripe" to SheetDxf(top = thin(base), bottom = thin(base)), "firstColumn" to SheetDxf(bold = true), "lastColumn" to SheetDxf(bold = true), "totalRow" to total)
                    else -> mapOf("wholeTable" to boxed.copy(horizontal = thin(base), vertical = thin(base)), "headerRow" to SheetDxf(bold = true, bottom = SheetLine(base, 2f)),
                        "firstRowStripe" to SheetDxf(fill = t(0.8)), "firstColumn" to SheetDxf(bold = true), "lastColumn" to SheetDxf(bold = true), "totalRow" to total)
                }
                "Medium" -> when (group) {
                    0 -> mapOf("wholeTable" to SheetDxf(left = thin(t(0.4)), right = thin(t(0.4)), top = thin(t(0.4)), bottom = thin(t(0.4)), horizontal = thin(t(0.4))),
                        "headerRow" to SheetDxf(fill = base, color = white, bold = true), "firstRowStripe" to SheetDxf(fill = t(0.8)),
                        "firstColumn" to SheetDxf(bold = true), "lastColumn" to SheetDxf(bold = true), "totalRow" to total)
                    1 -> mapOf("wholeTable" to SheetDxf(fill = t(0.8), horizontal = thin(white), vertical = thin(white)), "headerRow" to SheetDxf(fill = base, color = white, bold = true),
                        "firstRowStripe" to SheetDxf(fill = t(0.6)), "firstColumn" to SheetDxf(fill = base, color = white, bold = true),
                        "lastColumn" to SheetDxf(fill = base, color = white, bold = true), "totalRow" to SheetDxf(fill = base, color = white, bold = true, top = thin(white)))
                    2 -> mapOf("wholeTable" to boxed.copy(horizontal = thin(base), vertical = thin(base)), "headerRow" to SheetDxf(fill = base, color = white, bold = true),
                        "firstRowStripe" to SheetDxf(fill = t(0.8)), "firstColumn" to SheetDxf(bold = true), "lastColumn" to SheetDxf(bold = true), "totalRow" to total)
                    else -> mapOf("wholeTable" to SheetDxf(fill = t(0.8), horizontal = thin(t(0.4)), vertical = thin(t(0.4))), "headerRow" to SheetDxf(bold = true, bottom = SheetLine(base, 2f)),
                        "firstRowStripe" to SheetDxf(fill = t(0.6)), "firstColumn" to SheetDxf(bold = true), "lastColumn" to SheetDxf(bold = true), "totalRow" to total)
                }
                else -> if (group == 0) mapOf("wholeTable" to SheetDxf(fill = SheetColors.tint(base, -0.25).takeIf { k != 0 } ?: 0xFF737373.toInt(), color = white),
                    "headerRow" to SheetDxf(fill = black, color = white, bold = true, bottom = SheetLine(white, 2f)), "firstRowStripe" to SheetDxf(fill = SheetColors.tint(base, -0.5).takeIf { k != 0 } ?: 0xFF404040.toInt()),
                    "firstColumn" to SheetDxf(bold = true), "lastColumn" to SheetDxf(bold = true), "totalRow" to SheetDxf(fill = black, color = white, bold = true))
                else mapOf("wholeTable" to SheetDxf(fill = t(0.8)), "headerRow" to SheetDxf(fill = SheetColors.tint(base, -0.25), color = white, bold = true),
                    "firstRowStripe" to SheetDxf(fill = t(0.6)), "firstColumn" to SheetDxf(bold = true), "lastColumn" to SheetDxf(bold = true), "totalRow" to total)
            }
        }
    }

    /**
     * Cells with only a fill or a border: they widen the used range only near the written ones (the user's school
     * timetable formats 1,024 columns × 65,536 rows of empty cells; Excel would let it be scrolled to the end, which is no
     * use to look at), so an empty bordered form still shows whole.
     */
    internal class Extent {
        private val rowsAt = java.util.BitSet(); private val colsAt = java.util.BitSet()
        fun add(r: Int, c: Int) { rowsAt.set(r); colsAt.set(c) }
        fun rows(written: Int) = max(written, rowsAt.previousSetBit(written + 100))
        fun cols(written: Int) = max(written, colsAt.previousSetBit(written + 26))
    }

    // --- .ods ---

    /** LibreOffice's length ("2.258cm", "0.1736in", "10pt") in pixels at 96 per inch. */
    fun odfPx(length: String?): Float? {
        val m = Regex("""^(-?[0-9.]+)\s*(cm|mm|in|pt|pc|px)?$""").find(length?.trim() ?: return null) ?: return null
        val n = m.groupValues[1].toFloatOrNull() ?: return null
        return when (m.groupValues[2]) { "cm" -> n / 2.54f * 96; "mm" -> n / 25.4f * 96; "in" -> n * 96; "pc" -> n * 16; "px" -> n; else -> n * 4 / 3 }
    }

    private class Ods(val parts: (String) -> ByteArray?, val stream: (String) -> java.io.InputStream?) {
        /** Every style:style by name: its family, parent, and the properties ("c.", "t.", "p.", "col.", "row.", "tab." + name). */
        class Raw(val family: String, val parent: String?, val props: HashMap<String, String> = HashMap())
        val raw = HashMap<String, Raw>()
        /** A style's name by the name people see ("ConditionalStyle_1" for "ConditionalStyle_5f_1"): conditions use it. */
        val shown = HashMap<String, String>()
        var defaultCell: Raw? = null
        val styles = ArrayList<SheetStyle>()
        val styleIndex = HashMap<String, Int>()

        /** A style's properties with its parents' under them ("Default" under every cell style, as LibreOffice). */
        fun props(name: String?, seen: Int = 0): Map<String, String> {
            val r = raw[name ?: "Default"] ?: return defaultCell?.props.orEmpty()
            val parent = r.parent ?: if (r.family == "table-cell" && name != null && name != "Default") "Default" else null
            val base = if (parent == null || seen > 20) defaultCell?.props.orEmpty() else props(parent, seen + 1)
            return base + r.props
        }

        /** A number style's colour ([Red] in LibreOffice) and its maps: "value()>=0" → the style used then. */
        class DataStyle(var color: Int? = null, val maps: ArrayList<Pair<String, String>> = ArrayList())
        val dataStyles = HashMap<String, DataStyle>()
        private val dataStyleOf = HashMap<String?, String?>()

        /** The colour a number's data style gives it ([value] picks the map), if any. */
        fun numberColor(styleName: String?, value: Double): Int? {
            val name = dataStyleOf.getOrPut(styleName) { props(styleName)["data-style"] } ?: return null
            var d = dataStyles[name] ?: return null
            for ((condition, apply) in d.maps) {
                val m = Regex("""value\(\)\s*(<=|>=|!=|<|>|=)\s*(-?[0-9.]+)""").find(condition) ?: continue
                val n = m.groupValues[2].toDoubleOrNull() ?: continue
                val yes = when (m.groupValues[1]) { "<" -> value < n; ">" -> value > n; "<=" -> value <= n; ">=" -> value >= n; "=" -> value == n; else -> value != n }
                if (yes) { d = dataStyles[apply] ?: d; break }
            }
            return d.color
        }

        fun styleHandler() = object : DefaultHandler() {
            var current: Raw? = null
            var data: DataStyle? = null
            override fun startElement(uri: String?, local: String, q: String?, at: Attributes) {
                when (local) {
                    "style" -> current = Raw(at.a("family") ?: "", at.a("parent-style-name")).also { s ->
                        at.a("name")?.let { raw[it] = s; at.a("display-name")?.let { d -> shown[d] = it } }
                        at.a("data-style-name")?.let { s.props["data-style"] = it }
                    }
                    "number-style", "currency-style", "percentage-style", "date-style", "time-style", "boolean-style", "text-style" ->
                        data = DataStyle().also { d -> at.a("name")?.let { dataStyles[it] = d } }
                    "map" -> data?.let { d -> val c = at.a("condition"); val a = at.a("apply-style-name"); if (c != null && a != null) d.maps += c to a }
                    "default-style" -> current = if (at.a("family") == "table-cell") Raw("table-cell", null).also { defaultCell = it } else null
                    "table-cell-properties", "text-properties", "paragraph-properties", "table-column-properties", "table-row-properties", "table-properties" -> {
                        val p = when (local) {
                            "table-cell-properties" -> "c."; "text-properties" -> "t."; "paragraph-properties" -> "p."
                            "table-column-properties" -> "col."; "table-row-properties" -> "row."; else -> "tab."
                        }
                        if (local == "text-properties") data?.let { d -> d.color = hex(at.a("color")) }
                        current?.let { s -> for (i in 0 until at.length) s.props[p + at.getLocalName(i)] = at.getValue(i) }
                    }
                }
            }
            override fun endElement(uri: String?, local: String, q: String?) {
                if (local == "style" || local == "default-style") current = null
                if (local.endsWith("-style") && local != "default-style") data = null
            }
        }

        fun hex(s: String?): Int? = s?.takeIf { it.startsWith("#") && it.length == 7 }?.substring(1)?.toLongOrNull(16)?.toInt()?.or(0xFF000000.toInt())

        fun border(s: String?): SheetLine? {
            if (s == null || s == "none" || s.startsWith("0 ") || s.startsWith("0pt")) return null
            val parts = s.split(' ')
            val w = odfPx(parts.firstOrNull())?.let { if (it < 1.3f) 1f else if (it < 2.6f) 2f else 3f } ?: 1f
            val color = parts.firstNotNullOfOrNull { hex(it) } ?: 0xFF000000.toInt()
            return SheetLine(color, w, double = "double" in parts, dashed = parts.any { it in setOf("dashed", "dash-dot", "dash-dot-dot", "fine-dashed") }, dots = "dotted" in parts)
        }

        fun style(name: String?): Int = styleIndex.getOrPut(name ?: "") {
            val p = props(name)
            val fix = p["c.text-align-source"] != "value-type"
            val all = p["c.border"]
            styles += SheetStyle(
                font = p["t.font-name"] ?: p["t.font-family"], size = odfPx(p["t.font-size"])?.let { it * 3 / 4 } ?: 10f,
                bold = p["t.font-weight"].let { it == "bold" || (it?.toIntOrNull() ?: 400) >= 600 },
                italic = p["t.font-style"] == "italic" || p["t.font-style"] == "oblique",
                underline = p["t.text-underline-style"].let { it != null && it != "none" },
                strike = p["t.text-line-through-style"].let { it != null && it != "none" },
                color = hex(p["t.color"]), fill = hex(p["c.background-color"]),
                align = if (!fix) SheetAlign.General else when (p["p.text-align"]) {
                    "start", "left" -> SheetAlign.Left; "center" -> SheetAlign.Center; "end", "right" -> SheetAlign.Right
                    "justify" -> SheetAlign.Justify; else -> SheetAlign.General
                },
                valign = when (p["c.vertical-align"]) { "top" -> SheetVAlign.Top; "middle" -> SheetVAlign.Center; else -> SheetVAlign.Bottom },
                wrap = p["c.wrap-option"] == "wrap",
                rotation = p["c.rotation-angle"]?.toIntOrNull()?.let { if (it > 180) it - 360 else it } ?: 0,
                left = border(p["c.border-left"] ?: all), right = border(p["c.border-right"] ?: all),
                top = border(p["c.border-top"] ?: all), bottom = border(p["c.border-bottom"] ?: all),
            )
            styles.lastIndex
        }

        lateinit var settings: Settings

        /** The looks and the sheets' names, in one pass that skips every row; [table] reads one sheet when it is opened. */
        fun read(): Workbook {
            parts("styles.xml")?.let { sax(it, styleHandler()) }
            settings = readSettings()
            // The automatic styles, at the head of content.xml: read until the body begins.
            sax(stream("content.xml") ?: error("Not a spreadsheet."), object : DefaultHandler() {
                val autoStyles = styleHandler()
                var inStyles = false
                override fun startElement(uri: String?, local: String, q: String?, at: Attributes) {
                    if (local == "body") throw Enough()
                    if (local == "automatic-styles") inStyles = true
                    if (inStyles) autoStyles.startElement(uri, local, q, at)
                }
                override fun endElement(uri: String?, local: String, q: String?) {
                    if (inStyles) { autoStyles.endElement(uri, local, q); if (local == "automatic-styles") inStyles = false }
                }
            })
            // The sheets' names: each <table:table …> found in the bytes, not parsed (a 360 MB body takes a second, not a minute).
            // meta.xml says how many there are, so the scan stops at the last one's start.
            // shortcut: a giant .ods (LibreOffice's copy of the user's 46 tables: 360 MB of content) takes ~9 s here on
            // the phone, inflating; upgrade to tabs that arrive while the first sheet shows if such files are opened.
            val count = parts("meta.xml")?.let { Regex("""table-count="(\d+)"""").find(String(it))?.groupValues?.get(1)?.toIntOrNull() }
            val tabs = (stream("content.xml") ?: error("Not a spreadsheet.")).use { tables(it, count) }.map { (name, style) -> SheetTab(name, props(style)["tab.display"] == "false") }
            if (tabs.isEmpty()) error("No sheets.")
            // Every cell look made now, so the list does not grow while a sheet is drawn.
            style(null) // index 0: the default look
            raw.filter { it.value.family == "table-cell" }.keys.sorted().forEach(::style)
            WriterLog.i("Sheets: ods, ${tabs.size} sheets, ${styles.size} looks")
            val active = tabs.indexOfFirst { it.name == settings.active }.coerceAtLeast(0)
            return Workbook(tabs, styles, active) { i -> table(stream("content.xml") ?: error("Not a spreadsheet."), i) }
        }

        /** Each <table:table> start tag's name and style, found in the bytes (tables inside cells are not sheets: skipped). */
        fun tables(input: java.io.InputStream, count: Int? = null): List<Pair<String, String?>> {
            val out = ArrayList<Pair<String, String?>>()
            val buf = ByteArray(1 shl 16); val name = java.io.ByteArrayOutputStream(); val attrs = java.io.ByteArrayOutputStream()
            var state = 0 // 0: words, 1: a tag's name, 2: a table's attributes
            var depth = 0
            fun unescape(v: String) = v.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
            fun start(a: String) {
                if (depth == 0) out += unescape(Regex("""table:name="([^"]*)"""").find(a)?.groupValues?.get(1) ?: "Sheet") to
                    Regex("""table:style-name="([^"]*)"""").find(a)?.groupValues?.get(1)?.let(::unescape)
                if (!a.trimEnd().endsWith("/")) depth++
            }
            while (true) {
                val n = input.read(buf); if (n < 0) break
                for (i in 0 until n) {
                    val b = buf[i].toInt()
                    when (state) {
                        0 -> if (b == '<'.code) { name.reset(); state = 1 }
                        1 -> if (b == ' '.code || b == '\n'.code || b == '\r'.code || b == '\t'.code || b == '>'.code || (b == '/'.code && name.size() > 0)) {
                            when (name.toString("UTF-8")) {
                                "table:table" -> if (b == '>'.code) { start(""); state = 0 } else { attrs.reset(); if (b == '/'.code) attrs.write(b); state = 2 }
                                "/table:table" -> { depth--; state = 0 }
                                else -> state = 0
                            }
                        } else if (name.size() > 16) state = 0 else name.write(b)
                        else -> if (b == '>'.code) { start(attrs.toString("UTF-8")); state = 0; if (out.size == count) return out } else attrs.write(b)
                    }
                }
            }
            return out
        }

        fun table(content: java.io.InputStream, target: Int): Sheet {
            var result: Sheet? = null
            var tableIndex = -1
            sax(content, object : DefaultHandler() {
                var inStyles = false
                var name = ""; var tableStyle: String? = null
                var cells = CellMap()
                var colWidths = HashMap<Int, Float>(); var hiddenCols = HashSet<Int>(); var colDefaults = HashMap<Int, String>()
                var rowRuns = ArrayList<Triple<Int, Int, Float>>(); var hiddenRuns = ArrayList<Pair<Int, Int>>()
                var merges = ArrayList<SheetRange>()
                var col = 0; var row = 0; var rowRepeat = 1; var rowDefault: String? = null
                var maxRow = -1; var maxCol = -1
                var boxed = Extent()
                var rowCells = ArrayList<Pair<Int, SheetCell>>()
                // the cell being read
                var inCell = false; var cellRepeat = 1; var cellAttrs: Map<String, String> = emptyMap()
                val text = StringBuilder(); var paras = 0; var skip = 0; var inP = false; var link: String? = null
                // a cell's annotation: its author and words
                var inNote = false; var inNoteP = false; var inCreator = false; val note = StringBuilder(); val author = StringBuilder()
                // a picture or chart: where it is, and what it holds
                var frameDepth = 0; var frame: SheetDrawing? = null; var frameObject: String? = null; var frameImage: String? = null
                var drawings = ArrayList<SheetDrawing>()
                // LibreOffice's conditional formats (calcext:), in the order they come: the first wins
                var rules = ArrayList<SheetRule>(); var cfRanges = emptyList<SheetRange>(); val entries = ArrayList<Pair<SheetCfvo, Int?>>(); var barColor: Int? = null
                fun ranges(address: String?) = address.orEmpty().split(' ').mapNotNull { a -> rangeRef(a.split(':').joinToString(":") { it.substringAfterLast('.').replace("$", "") }) }

                override fun startElement(uri: String?, local: String, q: String?, at: Attributes) {
                    if (local == "automatic-styles") inStyles = true
                    if (inStyles) return
                    if (skip > 0) { skip++; return }
                    if (frameDepth > 0) {
                        frameDepth++
                        when (local) { "object" -> frameObject = frameObject ?: at.a("href"); "image" -> frameImage = frameImage ?: at.a("href") }
                        return
                    }
                    when (local) {
                        "table" -> {
                            tableIndex++
                            if (tableIndex != target) { skip = 1; return }
                            name = at.a("name") ?: "Sheet"; tableStyle = at.a("style-name")
                            cells = CellMap(); colWidths = HashMap(); hiddenCols = HashSet(); colDefaults = HashMap()
                            rowRuns = ArrayList(); hiddenRuns = ArrayList(); merges = ArrayList()
                            col = 0; row = 0; maxRow = -1; maxCol = -1; boxed = Extent(); drawings = ArrayList(); rules = ArrayList()
                        }
                        "table-column" -> {
                            val n = at.a("number-columns-repeated")?.toIntOrNull() ?: 1
                            val w = odfPx(props(at.a("style-name"))["col.column-width"])
                            val hide = at.a("visibility").let { it == "collapse" || it == "filter" }
                            val def = at.a("default-cell-style-name")?.takeIf { it != "Default" }
                            for (k in col until min(col + n, 16384)) {
                                if (k > col + 1024) break
                                if (w != null) colWidths[k] = w
                                if (hide) hiddenCols += k
                                if (def != null) colDefaults[k] = def
                            }
                            col += n
                        }
                        "table-row" -> {
                            rowRepeat = at.a("number-rows-repeated")?.toIntOrNull() ?: 1
                            rowDefault = at.a("default-cell-style-name")
                            odfPx(props(at.a("style-name"))["row.row-height"])?.let { rowRuns += Triple(row, rowRepeat, it) }
                            if (at.a("visibility").let { it == "collapse" || it == "filter" }) hiddenRuns += row to rowRepeat
                            col = 0; rowCells = ArrayList()
                        }
                        "table-cell", "covered-table-cell" -> {
                            cellRepeat = at.a("number-columns-repeated")?.toIntOrNull() ?: 1
                            if (local == "covered-table-cell") { col += cellRepeat; skip = 1; return }
                            inCell = true; text.setLength(0); paras = 0; link = null; note.setLength(0); author.setLength(0)
                            cellAttrs = HashMap<String, String>().also { m -> for (i in 0 until at.length) m[at.getLocalName(i)] = at.getValue(i) }
                        }
                        "annotation" -> if (inCell) inNote = true else skip = 1
                        "creator" -> if (inNote) inCreator = true
                        // In a cell, its place is from the cell's corner; in the sheet's shapes, from the sheet's.
                        "frame" -> {
                            frameDepth = 1; frameObject = null; frameImage = null
                            frame = SheetDrawing(if (inCell) row else 0, if (inCell) col else 0, odfPx(at.a("x")) ?: 0f, odfPx(at.a("y")) ?: 0f,
                                width = odfPx(at.a("width")) ?: 0f, height = odfPx(at.a("height")) ?: 0f)
                        }
                        "custom-shape", "line", "rect", "ellipse", "g" -> skip = 1
                        "conditional-format" -> cfRanges = ranges(at.a("target-range-address"))
                        "condition" -> odsRule(at.a("value").orEmpty(), at.a("apply-style-name"), rules.size)?.let { rules += it }
                        "color-scale", "data-bar" -> { entries.clear(); barColor = if (local == "data-bar") hex(at.a("positive-color")) ?: 0xFF638EC6.toInt() else null }
                        "color-scale-entry", "formatting-entry" -> entries += SheetCfvo(at.a("type") ?: "minimum", at.a("value")) to hex(at.a("color"))
                        "p", "h" -> if (inNote) { if (note.isNotEmpty()) note.append('\n'); inNoteP = true } else if (inCell) { if (paras > 0) text.append('\n'); paras++; inP = true }
                        "s" -> if (inP) repeat(at.a("c")?.toIntOrNull() ?: 1) { text.append(' ') }
                        "tab" -> if (inP) text.append('\t')
                        "line-break" -> if (inP) text.append('\n')
                        // LibreOffice's link to a cell is "#Sheet.B5": as Excel's, "#Sheet!B5".
                        "a" -> if (inCell) link = at.a("href")?.let { if (it.startsWith("#") && '!' !in it && '.' in it) "#" + it.drop(1).substringBeforeLast('.') + "!" + it.substringAfterLast('.') else it }
                    }
                }

                override fun endElement(uri: String?, local: String, q: String?) {
                    if (inStyles) { if (local == "automatic-styles") inStyles = false; return }
                    if (skip > 0) { skip--; return }
                    if (frameDepth > 0) { frameDepth--; if (frameDepth == 0) endFrame(); return }
                    when (local) {
                        "color-scale" -> rules += SheetRule(cfRanges, rules.size, "colorScale", scale = entries.mapNotNull { (v, c) -> c?.let { v to it } })
                        "data-bar" -> rules += SheetRule(cfRanges, rules.size, "dataBar", bar = barColor, barEnds = entries.map { it.first })
                    }
                    when (local) {
                        "p", "h" -> { inP = false; inNoteP = false }
                        "annotation" -> inNote = false
                        "creator" -> inCreator = false
                        "table-cell" -> { endCell(); inCell = false }
                        "table-row" -> endRow()
                        "table" -> endTable()
                    }
                }

                /** A LibreOffice condition ("cell-content()>5", "cell-content-is-between(1;5)", "is-true-formula(…)", "duplicate"…) as Excel's rule. */
                fun odsRule(value: String, styleName: String?, priority: Int): SheetRule? {
                    val st = styleName?.let { styles[style(if (it in raw) it else shown[it] ?: it)] } ?: return null
                    val d = SheetDxf(fill = st.fill, color = st.color, bold = st.bold.takeIf { it }, italic = st.italic.takeIf { it },
                        left = st.left, right = st.right, top = st.top, bottom = st.bottom)
                    fun inner(name: String) = value.removePrefix(name).removePrefix("(").removeSuffix(")")
                    fun quoted(x: String) = x.trim().removeSurrounding("\"")
                    fun rule(type: String, operator: String? = null, formulas: List<String> = emptyList(), text: String? = null, rank: Int = 10, percent: Boolean = false, bottom: Boolean = false, above: Boolean = true, equal: Boolean = false) =
                        SheetRule(cfRanges, priority, type, operator, formulas.map { odfFormula(it).removePrefix("=") }, d, text, rank, percent, bottom, above, equal)
                    // calcext's ">7", "between(2,3)", "formula-is(…)"; the older style:map's "cell-content()>7", "is-true-formula(…)".
                    Regex("""^(?:cell-content\(\)\s*)?(<=|>=|!=|<|>|=)\s*(.+)$""").matchEntire(value)?.let { m ->
                        val op = mapOf("=" to "equal", "!=" to "notEqual", ">" to "greaterThan", ">=" to "greaterThanOrEqual", "<" to "lessThan", "<=" to "lessThanOrEqual").getValue(m.groupValues[1])
                        return rule("cellIs", op, listOf(m.groupValues[2]))
                    }
                    return when {
                        value.startsWith("between(") -> rule("cellIs", "between", SheetRules.argsOf(inner("between")))
                        value.startsWith("not-between(") -> rule("cellIs", "notBetween", SheetRules.argsOf(inner("not-between")))
                        value.startsWith("cell-content-is-between(") -> rule("cellIs", "between", SheetRules.argsOf(inner("cell-content-is-between")))
                        value.startsWith("cell-content-is-not-between(") -> rule("cellIs", "notBetween", SheetRules.argsOf(inner("cell-content-is-not-between")))
                        value.startsWith("formula-is(") -> rule("expression", formulas = listOf(inner("formula-is")))
                        value.startsWith("is-true-formula(") -> rule("expression", formulas = listOf(inner("is-true-formula")))
                        value.startsWith("contains-text(") -> rule("containsText", text = quoted(inner("contains-text")))
                        value.startsWith("not-contains-text(") -> rule("notContainsText", text = quoted(inner("not-contains-text")))
                        value.startsWith("begins-with(") -> rule("beginsWith", text = quoted(inner("begins-with")))
                        value.startsWith("ends-with(") -> rule("endsWith", text = quoted(inner("ends-with")))
                        value == "duplicate" -> rule("duplicateValues"); value == "unique" -> rule("uniqueValues")
                        value == "is-error" -> rule("containsErrors"); value == "is-no-error" -> rule("notContainsErrors")
                        value.startsWith("top-elements(") -> rule("top10", rank = inner("top-elements").trim().toIntOrNull() ?: 10)
                        value.startsWith("bottom-elements(") -> rule("top10", rank = inner("bottom-elements").trim().toIntOrNull() ?: 10, bottom = true)
                        value.startsWith("top-percent(") -> rule("top10", rank = inner("top-percent").trim().toIntOrNull() ?: 10, percent = true)
                        value.startsWith("bottom-percent(") -> rule("top10", rank = inner("bottom-percent").trim().toIntOrNull() ?: 10, percent = true, bottom = true)
                        value == "above-average" -> rule("aboveAverage"); value == "below-average" -> rule("aboveAverage", above = false)
                        value == "above-equal-average" -> rule("aboveAverage", equal = true); value == "below-equal-average" -> rule("aboveAverage", above = false, equal = true)
                        else -> { WriterLog.i("Sheets: a LibreOffice condition not known (${value.substringBefore('(').take(30)})"); null }
                    }
                }

                fun endFrame() {
                    val f = frame ?: return
                    // A chart is LibreOffice's object ("./Object 1"); a picture its file; a chart we cannot read is left out.
                    val chart = frameObject?.removePrefix("./")?.let { parts("$it/content.xml") }?.let { OfficeCharts.readOdf(it) }
                    val image = frameImage?.removePrefix("./")?.takeIf { chart == null && frameObject == null }?.let { p -> { parts(p) } }
                    if (chart != null || image != null) drawings += SheetDrawing(f.row, f.col, f.dx, f.dy, width = f.width, height = f.height, chart = chart, image = image)
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (skip > 0 || frameDepth > 0) return
                    when { inCreator -> author.appendRange(ch, start, start + length); inNoteP -> note.appendRange(ch, start, start + length); inP -> text.appendRange(ch, start, start + length) }
                }

                fun endCell() {
                    val a = cellAttrs
                    val styleName = a["style-name"] ?: rowDefault?.takeIf { it != "Default" } ?: colDefaults[col]
                    val s = style(styleName)
                    val type = a["value-type"]
                    val shown = text.toString()
                    val value = when (type) {
                        "float", "percentage", "currency" -> a["value"]?.toDoubleOrNull()
                        "date" -> a["date-value"]?.let { d ->
                            runCatching { LocalDateTime.parse(if ('T' in d) d else "${d}T00:00") }.getOrNull()
                                ?.let { ChronoUnit.SECONDS.between(LocalDate.of(1899, 12, 30).atStartOfDay(), it) / 86_400.0 }
                        }
                        "time" -> a["time-value"]?.let { t ->
                            Regex("""PT(\d+)H(\d+)M([0-9.]+)S""").find(t)?.destructured?.let { (h, m, sec) -> (h.toDouble() * 3600 + m.toDouble() * 60 + sec.toDouble()) / 86_400 }
                        }
                        "boolean" -> if (a["boolean-value"] == "true") 1.0 else 0.0
                        else -> null
                    }
                    val kind = when {
                        a["value-type"] == "error" -> SheetKind.Error
                        type == "boolean" -> SheetKind.Bool
                        value != null -> SheetKind.Number
                        shown.isNotEmpty() -> SheetKind.Text
                        else -> SheetKind.Empty
                    }
                    val formula = a["formula"]?.let(::odfFormula)
                    val cell = SheetCell(shown, kind, value, formula, s, link = link, color = value?.let { numberColor(styleName, it) },
                        note = note.toString().trim().takeIf { it.isNotEmpty() }?.let { n -> author.toString().trim().takeIf { it.isNotEmpty() }?.let { "$it: $n" } ?: n })
                    val boxed = styles[s].boxed
                    val n = min(cellRepeat, 16384 - col)
                    if (kind != SheetKind.Empty || formula != null || cell.note != null || (boxed && n <= 256)) {
                        for (k in 0 until n) rowCells += (col + k) to cell
                    }
                    val rs = a["number-rows-spanned"]?.toIntOrNull() ?: 1; val cs = a["number-columns-spanned"]?.toIntOrNull() ?: 1
                    if (rs > 1 || cs > 1) merges += SheetRange(row, col, row + rs - 1, col + cs - 1)
                    col += cellRepeat
                }

                fun endRow() {
                    // A long run of repeated rows is LibreOffice's way to the sheet's end: only what is written in it counts.
                    if (rowRepeat > 100) rowCells.removeAll { it.second.kind == SheetKind.Empty && it.second.formula == null }
                    if (rowCells.isNotEmpty()) {
                        for (k in 0 until min(rowRepeat, 1000)) for ((c, cell) in rowCells) {
                            cells[Sheet.key(row + k, c)] = cell
                            if (cell.kind != SheetKind.Empty) { maxRow = max(maxRow, row + k); maxCol = max(maxCol, c) } else boxed.add(row + k, c)
                        }
                    }
                    row += rowRepeat
                }

                fun endTable() {
                    val looks = ArrayList<SheetStyle>()
                    val bars = runCatching { SheetRules.apply(rules, cells, { i -> (if (i >= Sheet.LOOKS) looks.getOrNull(i - Sheet.LOOKS) else styles.getOrNull(i)) ?: SheetStyle() }, looks) }
                        .onFailure { WriterLog.w("Sheets: conditional formatting could not be laid", it) }.getOrDefault(emptyMap())
                    if (rules.isNotEmpty()) WriterLog.i("Sheets: ${rules.size} conditional rules (${rules.map { it.type }.distinct().joinToString()})")
                    merges.forEach { maxRow = max(maxRow, it.r2); maxCol = max(maxCol, it.c2) }
                    maxRow = boxed.rows(maxRow); maxCol = boxed.cols(maxCol)
                    val rowHeights = HashMap<Int, Float>(); val hiddenRows = HashSet<Int>()
                    val limit = maxRow + 200
                    rowRuns.forEach { (start, n, h) -> for (r in start until min(start + n, limit)) rowHeights[r] = h }
                    hiddenRuns.forEach { (start, n) -> for (r in start until min(start + n, limit)) hiddenRows += r }
                    // LibreOffice's default row (0.452 cm) and column (2.258 cm).
                    val defaultRow = rowHeights.values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 17f
                    val defaultCol = colWidths.values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 85f
                    val view = settings.tables[name].orEmpty()
                    val tabProps = props(tableStyle)
                    result = Sheet(
                        name, cells, maxRow + 1, maxCol + 1, colWidths, rowHeights, defaultCol, defaultRow, hiddenCols, hiddenRows, merges,
                        frozenRows = if (view["VerticalSplitMode"] == "2") view["VerticalSplitPosition"]?.toIntOrNull() ?: 0 else 0,
                        frozenCols = if (view["HorizontalSplitMode"] == "2") view["HorizontalSplitPosition"]?.toIntOrNull() ?: 0 else 0,
                        grid = (view["ShowGrid"] ?: settings.showGrid) != "false",
                        colStyles = colDefaults.mapValues { style(it.value) }.filterValues { styles[it].boxed },
                        tab = hex(tabProps["tab.tab-color"]), hidden = tabProps["tab.display"] == "false", drawings = drawings, looks = looks, bars = bars,
                    )
                    WriterLog.i("Sheets: ods sheet ${cells.size} cells, ${maxRow + 1}×${maxCol + 1}, ${merges.size} merged")
                    throw Enough() // the sheets after it are not read
                }
            })
            return result ?: error("No sheet ${target + 1}.")
        }

        class Settings(val tables: Map<String, Map<String, String>>, val active: String?, val showGrid: String?)

        /** LibreOffice's view settings: each sheet's frozen rows and columns, the grid, the sheet it was left on. */
        fun readSettings(): Settings {
            val bytes = parts("settings.xml") ?: return Settings(emptyMap(), null, null)
            return runCatching {
                val doc = OfficeFormats.parse(bytes)
                val tables = HashMap<String, Map<String, String>>()
                var active: String? = null; var grid: String? = null
                val named = doc.getElementsByTagNameNS("*", "config-item-map-named")
                for (i in 0 until named.length) {
                    val m = named.item(i) as Element
                    if (m.getAttributeNS("*", "name").ifEmpty { m.getAttribute("config:name") } != "Tables") continue
                    for (entry in m.kids("config-item-map-entry")) {
                        val items = entry.kids("config-item").associate { (it.getAttribute("config:name")) to it.textContent }
                        tables[entry.getAttribute("config:name")] = items
                    }
                    // The view these tables belong to: its active table and grid.
                    (m.parentNode as? Element)?.kids("config-item")?.forEach {
                        when (it.getAttribute("config:name")) { "ActiveTable" -> active = it.textContent; "ShowGrid" -> grid = it.textContent }
                    }
                }
                Settings(tables, active, grid)
            }.getOrElse { Settings(emptyMap(), null, null) }
        }
    }

    /** LibreOffice's "of:=SUM([.A1:.B2])" as Excel writes it: "=SUM(A1:B2)". */
    fun odfFormula(f: String): String {
        val body = f.substringAfter(':', f).let { if (f.startsWith("of:") || f.startsWith("oooc:") || f.startsWith("msoxl:")) it else f }
        return body.replace(Regex("""\[([^\]]*)]""")) { m ->
            m.groupValues[1].split(':').joinToString(":") { part ->
                val sheet = part.substringBefore('.', "").removePrefix("$").trim('\'')
                val cell = part.substringAfter('.')
                if (sheet.isEmpty()) cell else "$sheet!$cell"
            }
        }.let { if (it.startsWith("=")) it else "=$it" }
    }

    // --- .csv ---

    /** A text table: UTF-8 (or Windows Greek, as Excel saves a CSV here), the separator the first lines use most. */
    fun csv(bytes: ByteArray): Workbook {
        val text = runCatching {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrElse { String(bytes, charset("windows-1253")) }.removePrefix("﻿")
        val head = text.lineSequence().take(5).joinToString("\n")
        val sep = listOf(',', ';', '\t', '|').maxByOrNull { s -> head.count { it == s } } ?: ','
        val cells = CellMap()
        var r = 0; var c = 0; var maxCol = -1
        val field = StringBuilder(); var quoted = false; var i = 0
        fun put() {
            val s = field.toString()
            if (s.isNotEmpty()) {
                val n = s.trim().takeIf { Regex("""-?\d+([.,]\d+)?""").matches(it) }?.replace(',', '.')?.toDoubleOrNull()
                cells[Sheet.key(r, c)] = SheetCell(s, if (n != null) SheetKind.Number else SheetKind.Text, n)
                maxCol = max(maxCol, c)
            }
            field.setLength(0); c++
        }
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                ch == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
                !quoted && ch == sep -> put()
                !quoted && (ch == '\n' || ch == '\r') -> { put(); if (ch == '\r' && text.getOrNull(i + 1) == '\n') i++; r++; c = 0 }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || c > 0) put()
        var rows = 0
        cells.forEach { k, _ -> rows = max(rows, Sheet.row(k) + 1) }
        val sheet = Sheet("Sheet1", cells, rows, maxCol + 1, emptyMap(), emptyMap(), 64f, 20f, emptySet(), emptySet(), emptyList(), 0, 0)
        return Workbook(listOf(SheetTab("Sheet1")), listOf(SheetStyle())) { sheet }
    }
}
