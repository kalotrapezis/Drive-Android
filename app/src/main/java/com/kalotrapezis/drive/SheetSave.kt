package com.kalotrapezis.drive

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Saving a spreadsheet's edits back into its file (asked 16 Oct): only what changed is written — in an .xlsx the rows
 * of the edited cells, in an .ods the edited cells — and every other part is copied as it was, so nothing the viewer
 * does not draw is lost. Excel and LibreOffice are asked to work the formulas out again when they open it.
 */
internal object SheetSave {
    /** Writes [book]'s edits into [target] (a new file), reading the parts from [source]. */
    fun save(book: Workbook, source: File, target: File) {
        when (book.kind) {
            "xlsx" -> xlsx(book, source, target)
            "ods" -> ods(book, source, target)
            else -> csv(book, target)
        }
    }

    // --- .xlsx ---

    private fun esc(s: String) = OfficeFormats.esc(s)

    /** A number as the file keeps it: 12, 0.1, 1.0E20. */
    private fun num(v: Double) = if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()

    /** A cell as SpreadsheetML: its value by kind, a formula with its value, words inline; null: no cell at all. */
    fun cellXml(ref: String, style: Int, cell: SheetCell): String? {
        val s = if (style > 0) " s=\"$style\"" else ""
        val f = cell.formula?.removePrefix("=")?.let { "<f>${esc(it)}</f>" } ?: ""
        return when {
            cell.formula != null -> when (cell.kind) {
                SheetKind.Number -> "<c r=\"$ref\"$s>$f<v>${num(cell.value ?: 0.0)}</v></c>"
                SheetKind.Bool -> "<c r=\"$ref\"$s t=\"b\">$f<v>${if (cell.value == 1.0) 1 else 0}</v></c>"
                SheetKind.Error -> "<c r=\"$ref\"$s t=\"e\">$f<v>${esc(cell.text)}</v></c>"
                SheetKind.Text -> "<c r=\"$ref\"$s t=\"str\">$f<v>${esc(cell.text)}</v></c>"
                SheetKind.Empty -> "<c r=\"$ref\"$s t=\"str\">$f<v></v></c>"
            }
            cell.kind == SheetKind.Number -> "<c r=\"$ref\"$s><v>${num(cell.value ?: 0.0)}</v></c>"
            cell.kind == SheetKind.Bool -> "<c r=\"$ref\"$s t=\"b\"><v>${if (cell.value == 1.0) 1 else 0}</v></c>"
            cell.kind == SheetKind.Error -> "<c r=\"$ref\"$s t=\"e\"><v>${esc(cell.text)}</v></c>"
            cell.kind == SheetKind.Text -> "<c r=\"$ref\"$s t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(cell.text)}</t></is></c>"
            style > 0 -> "<c r=\"$ref\"$s/>"
            else -> null
        }
    }

    private val CELL = Regex("""<c\b[^>]*?(?:/>|>.*?</c>)""", RegexOption.DOT_MATCHES_ALL)
    private fun attr(tag: String, name: String) = Regex("""\s$name="([^"]*)"""").find(tag)?.groupValues?.get(1)

    /**
     * A sheet's XML with its edited cells: each row with an edit written again (its other cells as they were), new
     * rows where they go; [styleOf] the style to save a cell with (its old one: [old]); a shared formula an edit
     * breaks spelled out in the cells that shared it ([formulaAt]).
     */
    fun patchSheet(xml: String, edits: Map<Long, SheetCell>, styleOf: (SheetCell, Int?) -> Int, formulaAt: (Int, Int) -> String?, rows: Int, cols: Int): String {
        val open = Regex("""<sheetData\s*/>|<sheetData[^>]*>""").find(xml) ?: error("No sheetData.")
        val body0 = if (open.value.endsWith("/>")) open.range.last + 1 else open.range.last + 1
        val end = if (open.value.endsWith("/>")) body0 else xml.indexOf("</sheetData>", body0)
        val data = xml.substring(body0, end)
        // the rows: where each one is in sheetData, by number
        class Row(val r: Int, val start: Int, val end: Int)
        val list = ArrayList<Row>()
        var i = 0; var next = 0
        while (true) {
            val at = data.indexOf("<row", i); if (at < 0) break
            if (data.getOrNull(at + 4)?.let { it == ' ' || it == '>' || it == '/' || it == '\n' } != true) { i = at + 4; continue }
            val tagEnd = data.indexOf('>', at)
            val tag = data.substring(at, tagEnd + 1)
            val r = attr(tag, "r")?.toIntOrNull()?.minus(1) ?: next
            val close = if (tag.endsWith("/>")) tagEnd + 1 else data.indexOf("</row>", tagEnd).let { if (it < 0) error("A row without its end.") else it + 6 }
            list += Row(r, at, close); next = r + 1; i = close
        }
        val byRow = list.associateBy { it.r }
        val editRows = edits.keys.groupBy { Sheet.row(it) }
        // shared formulas an edit breaks: the masters among the edited cells, and the cells of their groups
        val broken = HashMap<String, SheetRange>()
        for ((r, keys) in editRows) {
            val row = byRow[r] ?: continue
            for (m in CELL.findAll(data.substring(row.start, row.end))) {
                val ref = attr(m.value.substringBefore('>'), "r")?.let(Sheets::cellRef) ?: continue
                if (Sheet.key(ref.first, ref.second) !in keys) continue
                Regex("""<f\b([^>]*)>""").find(m.value)?.groupValues?.get(1)?.let { a ->
                    if (attr(a, "t") == "shared") { val si = attr(a, "si"); val g = attr(a, "ref")?.let(Sheets::rangeRef); if (si != null && g != null) broken[si] = g }
                }
            }
        }
        val rewrite = HashSet(editRows.keys)
        broken.values.forEach { g -> for (r in g.r1..g.r2) if (r in byRow) rewrite += r }

        fun rowXml(r: Int, old: String?): String {
            val tag = old?.let { it.substring(0, it.indexOf('>') + 1) } ?: "<row r=\"${r + 1}\">"
            val start = tag.removeSuffix("/>").removeSuffix(">").replace(Regex("""\sspans="[^"]*""""), "")
            val cells = java.util.TreeMap<Int, String>()
            var sequential = 0
            if (old != null && !tag.endsWith("/>")) for (m in CELL.findAll(old)) {
                val c = attr(m.value.substringBefore('>'), "r")?.let(Sheets::cellRef)?.second ?: sequential
                sequential = c + 1
                var x = m.value
                // a broken shared formula: written out in full here
                Regex("""<f\b([^>]*?)(?:/>|>([^<]*)</f>)""").find(x)?.let { f ->
                    if (attr(f.groupValues[1], "t") == "shared" && attr(f.groupValues[1], "si") in broken) formulaAt(r, c)?.let { full -> x = x.replace(f.value, "<f>${esc(full.removePrefix("="))}</f>") }
                }
                cells[c] = x
            }
            for (k in editRows[r].orEmpty()) {
                val c = Sheet.col(k)
                val oldStyle = cells[c]?.let { attr(it.substringBefore('>'), "s")?.toIntOrNull() }
                val x = cellXml(Sheets.colName(c) + (r + 1), styleOf(edits.getValue(k), oldStyle), edits.getValue(k))
                if (x == null) cells.remove(c) else cells[c] = x
            }
            return if (cells.isEmpty()) "$start/>" else "$start>${cells.values.joinToString("")}</row>"
        }

        val out = StringBuilder(xml.length + 1024)
        out.append(xml, 0, open.range.first).append("<sheetData>")
        var at = 0
        val newRows = (rewrite - byRow.keys).sorted().toMutableList()
        for (row in list) {
            while (newRows.isNotEmpty() && newRows.first() < row.r) out.append(data, at, row.start).also { at = row.start }.append(rowXml(newRows.removeAt(0), null))
            if (row.r in rewrite) { out.append(data, at, row.start).append(rowXml(row.r, data.substring(row.start, row.end))); at = row.end }
        }
        out.append(data, at, data.length)
        newRows.forEach { out.append(rowXml(it, null)) }
        out.append("</sheetData>")
        out.append(xml, if (open.value.endsWith("/>")) open.range.last + 1 else end + "</sheetData>".length, xml.length)
        // the used range, grown to the edits
        return out.toString().replaceFirst(Regex("""<dimension ref="[^"]*"\s*/>"""), "<dimension ref=\"A1:${Sheets.colName(maxOf(cols, 1) - 1)}${maxOf(rows, 1)}\"/>")
    }

    /** Excel's own number format ids for formats a typed value takes; others are added to the file. */
    private val BUILT = mapOf("0%" to 9, "0.00%" to 10, "d/m/yyyy" to 14, "h:mm" to 20, "h:mm:ss" to 21, "d/m/yyyy h:mm" to 22)

    private fun xlsx(book: Workbook, source: File, target: File) {
        val zip = ZipFile(source)
        val replaced = HashMap<String, ByteArray?>()
        fun read(path: String) = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { it.readBytes() } } ?: error("No $path.")
        // styles for typed formats: (the cell's style, a format) → a new xf
        val wanted = LinkedHashMap<Pair<Int, String>, Int>()
        var styles = book.parts["styles"]?.let { String(read(it)) }
        val xfCount = styles?.let { st -> Regex("""<cellXfs[^>]*>(.*?)</cellXfs>""", RegexOption.DOT_MATCHES_ALL).find(st)?.groupValues?.get(1)?.let { Regex("""<xf\b""").findAll(it).count() } } ?: 0
        fun styleOf(cell: SheetCell, old: Int?): Int {
            val base = old ?: cell.fileStyle
            val code = cell.format ?: return base
            if (styles == null) return base
            return wanted.getOrPut(base to code) { xfCount + wanted.size }
        }
        for ((i, edits) in book.edits) {
            if (edits.isEmpty()) continue
            val path = book.parts["$i"] ?: error("No sheet ${i + 1}.")
            val sheet = book.sheet(i)
            val xml = String(read(path), Charsets.UTF_8)
            replaced[path] = patchSheet(xml, edits, ::styleOf, { r, c -> sheet[r, c]?.formula }, sheet.rows, sheet.cols).toByteArray(Charsets.UTF_8)
        }
        if (wanted.isNotEmpty() && styles != null) {
            // the formats: Excel's own ids, or new ones in numFmts
            var st: String = styles
            val ids = HashMap<String, Int>()
            var nextId = maxOf(163, Regex("""numFmtId="(\d+)"""").findAll(st).maxOfOrNull { it.groupValues[1].toInt() } ?: 163) + 1
            val newFmts = StringBuilder()
            for ((_, code) in wanted.keys) if (code !in ids) ids[code] = BUILT[code] ?: (nextId++).also { newFmts.append("<numFmt numFmtId=\"$it\" formatCode=\"${esc(code)}\"/>") }
            if (newFmts.isNotEmpty()) st = if ("<numFmts" in st) st.replace(Regex("""<numFmts[^>]*>"""), "$0$newFmts").replace(Regex("""(<numFmts[^>]*count=")(\d+)""")) { m -> m.groupValues[1] + (m.groupValues[2].toInt() + newFmts.split("<numFmt ").size - 1) }
                else st.replaceFirst(Regex("""(<styleSheet[^>]*>)"""), "$1<numFmts count=\"${newFmts.split("<numFmt ").size - 1}\">$newFmts</numFmts>")
            // each new xf: the cell's own, its number format changed
            val xfs = Regex("""(<cellXfs[^>]*>)(.*?)(</cellXfs>)""", RegexOption.DOT_MATCHES_ALL).find(st)!!
            val list = Regex("""<xf\b[^>]*?(?:/>|>.*?</xf>)""", RegexOption.DOT_MATCHES_ALL).findAll(xfs.groupValues[2]).map { it.value }.toList()
            val added = wanted.keys.joinToString("") { (base, code) ->
                val xf = list.getOrElse(base) { list.first() }
                xf.replaceFirst(Regex("""numFmtId="\d+""""), "numFmtId=\"${ids[code]}\"").let { if ("numFmtId=" in it) it else it.replaceFirst("<xf ", "<xf numFmtId=\"${ids[code]}\" ") }
                    .replace(Regex("""\sapplyNumberFormat="[^"]*""""), "").replaceFirst("<xf ", "<xf applyNumberFormat=\"1\" ")
            }
            st = st.replaceRange(xfs.range, xfs.groupValues[1].replace(Regex("""count="\d+""""), "count=\"${list.size + wanted.size}\"") + xfs.groupValues[2] + added + xfs.groupValues[3])
            styles = st
            replaced[book.parts.getValue("styles")] = st.toByteArray(Charsets.UTF_8)
        }
        // formulas worked out again on opening; Excel's calculation chain dropped (it lists the old formulas)
        book.parts["workbook"]?.let { wb ->
            var x = String(read(wb), Charsets.UTF_8)
            x = if (Regex("""<calcPr\b""").containsMatchIn(x)) x.replace(Regex("""<calcPr\b([^>]*?)(/?)>""")) { m -> "<calcPr" + m.groupValues[1].replace(Regex("""\sfullCalcOnLoad="[^"]*""""), "") + " fullCalcOnLoad=\"1\"" + m.groupValues[2] + ">" }
            else listOf("</definedNames>", "</externalReferences>", "</sheets>").firstNotNullOfOrNull { t -> x.indexOf(t).takeIf { it >= 0 }?.let { it + t.length } }
                ?.let { at -> x.substring(0, at) + "<calcPr fullCalcOnLoad=\"1\"/>" + x.substring(at) } ?: x
            replaced[wb] = x.toByteArray(Charsets.UTF_8)
            val rels = wb.substringBeforeLast('/', "").let { if (it.isEmpty()) "_rels/" else "$it/_rels/" } + wb.substringAfterLast('/') + ".rels"
            zip.getEntry(rels)?.let { replaced[rels] = String(read(rels)).replace(Regex("""<Relationship\b[^>]*calcChain[^>]*/>"""), "").toByteArray() }
        }
        zip.entries().asSequence().firstOrNull { it.name.endsWith("calcChain.xml") }?.let { e ->
            replaced[e.name] = null
            replaced["[Content_Types].xml"] = String(read("[Content_Types].xml")).replace(Regex("""<Override\b[^>]*calcChain[^>]*/>"""), "").toByteArray()
        }
        rezip(zip, target, replaced, stored = null)
        zip.close()
    }

    /** The zip again into [target]: each part as it was, or [replaced] (null: left out); [stored] first and not compressed. */
    fun rezip(zip: ZipFile, target: File, replaced: Map<String, ByteArray?>, stored: String?) {
        ZipOutputStream(target.outputStream().buffered()).use { out ->
            val entries = zip.entries().asSequence().toList().sortedBy { if (it.name == stored) 0 else 1 }
            for (e in entries) {
                if (e.isDirectory) continue
                val data = if (e.name in replaced) replaced[e.name] ?: continue else null
                val ne = ZipEntry(e.name)
                if (e.name == stored) {
                    val bytes = data ?: zip.getInputStream(e).use { it.readBytes() }
                    ne.method = ZipEntry.STORED; ne.size = bytes.size.toLong(); ne.compressedSize = bytes.size.toLong()
                    ne.crc = CRC32().apply { update(bytes) }.value
                    out.putNextEntry(ne); out.write(bytes); out.closeEntry(); continue
                }
                out.putNextEntry(ne)
                if (data != null) out.write(data) else zip.getInputStream(e).use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }

    // --- .ods ---

    private const val TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    private const val OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    private const val TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private const val CALCEXT = "urn:org:documentfoundation:names:experimental:calc:xmlns:calcext:1.0"

    /** An Excel formula in LibreOffice's way: "of:=SUM([.A1:.B2];[$Other.C3])". */
    fun odfFormula(f: String): String {
        val body = f.removePrefix("=")
        val ref = Regex("""(?:('(?:[^']|'')+'|[\p{L}_][\p{L}\p{N}_.]*)!)?(\$?[A-Za-z]{1,3}\$?\d+)(?::(\$?[A-Za-z]{1,3}\$?\d+))?(?![\p{L}\p{N}_(])""")
        val out = StringBuilder()
        // outside words in quotes: references bracketed, commas as semicolons
        val parts = body.split('"')
        parts.forEachIndexed { i, part ->
            if (i > 0) out.append('"')
            if (i % 2 == 1) { out.append(part); return@forEachIndexed }
            out.append(ref.replace(part.replace(',', ';')) { m ->
                val sheet = m.groupValues[1].takeIf { it.isNotEmpty() }?.let { s -> "$$s." } ?: "."
                "[" + sheet + m.groupValues[2] + (m.groupValues[3].takeIf { it.isNotEmpty() }?.let { ":" + "." + it } ?: "") + "]"
            })
        }
        return "of:=$out"
    }

    private fun ods(book: Workbook, source: File, target: File) {
        val zip = ZipFile(source)
        val entry = zip.getEntry("content.xml") ?: error("Not a spreadsheet.")
        // shortcut: content.xml is changed as a whole document; a giant one (hundreds of MB) is too big to save here
        check(entry.size in 0..80_000_000) { "This spreadsheet is too big to save on this device." }
        val doc = OfficeFormats.parse(zip.getInputStream(entry).use { it.readBytes() })
        val tables = doc.getElementsByTagNameNS(TABLE, "table").let { l -> (0 until l.length).map { l.item(it) as org.w3c.dom.Element } }
            .filter { t -> generateSequence(t.parentNode) { it.parentNode }.none { it is org.w3c.dom.Element && it.localName == "table" } }
        for ((i, edits) in book.edits) {
            val table = tables.getOrNull(i) ?: continue
            for ((k, cell) in edits) odsCell(table, Sheet.row(k), Sheet.col(k), cell, book)
        }
        rezip(zip, target, mapOf("content.xml" to ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + OfficeFormats.xml(doc.documentElement)).toByteArray(Charsets.UTF_8)), stored = "mimetype")
        zip.close()
    }

    private fun kids(e: org.w3c.dom.Element, vararg names: String): List<org.w3c.dom.Element> {
        val out = ArrayList<org.w3c.dom.Element>()
        var n = e.firstChild
        while (n != null) { if (n is org.w3c.dom.Element && n.namespaceURI == TABLE && n.localName in names) out += n; n = n.nextSibling }
        return out
    }

    /** A table's rows in order (inside header rows and row groups too). */
    private fun rows(e: org.w3c.dom.Element): List<org.w3c.dom.Element> = kids(e, "table-row", "table-header-rows", "table-row-group", "table-rows")
        .flatMap { if (it.localName == "table-row") listOf(it) else rows(it) }

    /** The element that is the [index]th of its repeated run, split from it ([attr]: the repeat attribute). */
    private fun single(e: org.w3c.dom.Element, offset: Int, attr: String): org.w3c.dom.Element {
        val n = e.getAttributeNS(TABLE, attr).toIntOrNull() ?: 1
        if (n <= 1) return e
        val parent = e.parentNode
        fun copy(count: Int) = (e.cloneNode(true) as org.w3c.dom.Element).apply { if (count > 1) setAttributeNS(TABLE, "table:$attr", "$count") else removeAttributeNS(TABLE, attr) }
        val one = copy(1)
        if (offset > 0) parent.insertBefore(copy(offset), e)
        parent.insertBefore(one, e)
        if (n - offset - 1 > 0) parent.insertBefore(copy(n - offset - 1), e)
        parent.removeChild(e)
        return one
    }

    private fun odsCell(table: org.w3c.dom.Element, r: Int, c: Int, cell: SheetCell, book: Workbook) {
        val doc = table.ownerDocument
        // the row: found through the repeats, or added at the end
        var at = 0; var row: org.w3c.dom.Element? = null
        for (e in rows(table)) {
            val n = e.getAttributeNS(TABLE, "number-rows-repeated").toIntOrNull() ?: 1
            if (r < at + n) { row = single(e, r - at, "number-rows-repeated"); break }
            at += n
        }
        if (row == null) {
            if (r > at) table.appendChild(doc.createElementNS(TABLE, "table:table-row").apply { setAttributeNS(TABLE, "table:number-rows-repeated", "${r - at}"); appendChild(doc.createElementNS(TABLE, "table:table-cell")) })
            row = doc.createElementNS(TABLE, "table:table-row") as org.w3c.dom.Element
            table.appendChild(row)
        }
        var col = 0; var target: org.w3c.dom.Element? = null
        for (e in kids(row, "table-cell", "covered-table-cell")) {
            val n = e.getAttributeNS(TABLE, "number-columns-repeated").toIntOrNull() ?: 1
            if (c < col + n) { target = single(e, c - col, "number-columns-repeated"); break }
            col += n
        }
        if (target == null) {
            if (c > col) row.appendChild(doc.createElementNS(TABLE, "table:table-cell").apply { if (c - col > 1) setAttributeNS(TABLE, "table:number-columns-repeated", "${c - col}") })
            target = doc.createElementNS(TABLE, "table:table-cell") as org.w3c.dom.Element
            row.appendChild(target)
        }
        if (target.localName == "covered-table-cell") return // inside a merged cell: nothing of its own
        // its value: what it was goes, the edit comes; its look, comments and pictures stay
        for (a in listOf("value-type", "value", "date-value", "time-value", "boolean-value", "string-value", "currency")) target.removeAttributeNS(OFFICE, a)
        target.removeAttributeNS(CALCEXT, "value-type"); target.removeAttributeNS(TABLE, "formula")
        var child = target.firstChild
        while (child != null) { val nx = child.nextSibling; if (child is org.w3c.dom.Element && child.namespaceURI == TEXT && child.localName == "p") target.removeChild(child); child = nx }
        cell.formula?.let { target.setAttributeNS(TABLE, "table:formula", odfFormula(it)) }
        fun type(t: String) { target.setAttributeNS(OFFICE, "office:value-type", t); target.setAttributeNS(CALCEXT, "calcext:value-type", t) }
        when (cell.kind) {
            SheetKind.Number -> {
                val v = cell.value ?: 0.0
                if (cell.format?.let(book.format::isDate) == true) { type("date"); target.setAttributeNS(OFFICE, "office:date-value", book.format.dateTime(v).toString().let { if (it.endsWith("T00:00")) it.substringBefore('T') else it }) }
                else if (cell.format?.contains('%') == true) { type("percentage"); target.setAttributeNS(OFFICE, "office:value", num(v)) }
                else { type("float"); target.setAttributeNS(OFFICE, "office:value", num(v)) }
            }
            SheetKind.Bool -> { type("boolean"); target.setAttributeNS(OFFICE, "office:boolean-value", if (cell.value == 1.0) "true" else "false") }
            SheetKind.Text -> type("string")
            SheetKind.Error -> type("error")
            SheetKind.Empty -> Unit
        }
        if (cell.text.isNotEmpty()) cell.text.split('\n').forEach { line -> target.appendChild(doc.createElementNS(TEXT, "text:p").apply { textContent = line }) }
    }

    // --- .csv ---

    /** The cells as a table of words in its own separator and letters (a CSV keeps no formulas: their values). */
    private fun csv(book: Workbook, target: File) {
        val (sep, charset) = book.csv ?: (',' to Charsets.UTF_8)
        val sheet = book.sheet(0)
        val text = buildString {
            for (r in 0 until sheet.rows) {
                if (r > 0) append("\r\n")
                for (c in 0 until sheet.cols) {
                    if (c > 0) append(sep)
                    val cell = sheet[r, c] ?: continue
                    // a number as typed (the formula bar's way), words quoted when they hold the separator, a quote or a line
                    val t = if (cell.kind == SheetKind.Number && cell.value != null) SheetInput.plain(cell.value, book.format) else cell.text
                    append(if (t.any { it == sep || it == '"' || it == '\n' || it == '\r' }) "\"" + t.replace("\"", "\"\"") + "\"" else t)
                }
            }
            append("\r\n")
        }
        target.writeBytes(text.toByteArray(charset))
    }
}
