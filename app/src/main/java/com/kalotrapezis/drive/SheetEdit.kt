package com.kalotrapezis.drive

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.util.TreeMap

/**
 * Excel's editing (asked 16 Oct: "edit cells, the formula bar, save back to the file"): what the formula bar shows for
 * a cell, what a typed entry becomes, the cells that count on a changed one worked out again, and undo.
 */
internal object SheetInput {
    /** Data › Text to columns (Excel's, delimited): [text] cut at each [by] (outside "quotes"), spaces round each piece trimmed; "" for a space: runs of spaces as one. */
    fun split(text: String, by: String): List<String> {
        if (by == " ") return text.trim().split(Regex("\\s+"))
        val out = ArrayList<String>(); val sb = StringBuilder(); var quoted = false; var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' -> quoted = !quoted
                !quoted && text.startsWith(by, i) -> { out += sb.toString().trim(); sb.setLength(0); i += by.length; continue }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString().trim()
        return out
    }
    /** Data › Remove duplicates (Excel's): the rows of [rows] kept — each the first of those alike (any case, spaces round words trimmed); a header row always kept. */
    fun unique(rows: List<List<String>>, headers: Boolean): List<Int> {
        val seen = HashSet<List<String>>()
        return rows.indices.filter { i -> (headers && i == 0) || seen.add(rows[i].map { it.trim().lowercase() }) }
    }
    /** Find and Replace: [content] (a cell as typed: its words, or its formula) with every [find] in it, any case, made [with]; null: none in it. */
    fun replaced(content: String, find: String, with: String): String? {
        if (find.isEmpty()) return null
        val r = Regex(Regex.escape(find), RegexOption.IGNORE_CASE)
        return if (r.containsMatchIn(content)) r.replace(content, Regex.escapeReplacement(with)) else null
    }
    /** A cell as the formula bar shows it (Excel's: the value as typed — 1234.5, not 1.234,50; a date as a date). */
    fun text(cell: SheetCell?, format: String, f: SheetFormat): String {
        if (cell == null) return ""
        cell.formula?.let { return it }
        val v = cell.value
        return when (cell.kind) {
            SheetKind.Number -> if (v == null) cell.text else {
                val code = cell.format ?: format
                when {
                    f.isDate(code) -> {
                        val t = f.dateTime(v)
                        val day = "${t.dayOfMonth}/${t.monthValue}/${t.year}"
                        val time = if (t.second == 0) "%d:%02d".format(t.hour, t.minute) else "%d:%02d:%02d".format(t.hour, t.minute, t.second)
                        if (v < 1 && v >= 0) time else if (v % 1.0 == 0.0) day else "$day $time"
                    }
                    '%' in SheetFormat.strip(code) -> plain(v * 100, f) + "%"
                    else -> plain(v, f)
                }
            }
            SheetKind.Bool -> if (v == 1.0) "TRUE" else "FALSE"
            else -> cell.text
        }
    }

    /** A number in full (15 digits, as Excel keeps it), with the phone's decimal mark and no thousands. */
    fun plain(v: Double, f: SheetFormat): String =
        BigDecimal(v).round(MathContext(15, RoundingMode.HALF_UP)).stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString().replace('.', f.decimalSeparator())

    /**
     * What a typed entry becomes in a cell that was [old], of look [style]: a formula (=…, its value worked out later),
     * words (a leading ' keeps digits as words), TRUE/FALSE, a percent, a date or time, or a number in the phone's way
     * of writing it.
     */
    fun cell(input: String, old: SheetCell?, styleIndex: Int, format: String, f: SheetFormat): SheetCell {
        val keep = SheetCell("", SheetKind.Empty, style = old?.style ?: styleIndex, link = old?.link, note = old?.note, own = old?.own ?: -1, format = old?.format)
        if (input.isEmpty()) return keep.copy(format = null)
        if (input.startsWith("=") && input.length > 1) return keep.copy(formula = "=" + formula(input.drop(1)), format = old?.format)
        if (input.startsWith("'")) return keep.copy(text = input.drop(1), kind = SheetKind.Text, format = null)
        val t = input.trim()
        when (t.uppercase()) {
            "TRUE", "ΑΛΗΘΕΣ" -> return keep.copy(text = "TRUE", kind = SheetKind.Bool, value = 1.0)
            "FALSE", "ΨΕΥΔΕΣ" -> return keep.copy(text = "FALSE", kind = SheetKind.Bool, value = 0.0)
        }
        fun shown(v: Double, code: String) = f.format(v, code).let { keep.copy(text = it.text, kind = SheetKind.Number, value = v, color = it.color) }
        // 25%: a percent; a General cell takes the percent format, as Excel gives it
        Regex("""^(.+?)\s*%$""").matchEntire(t)?.let { m ->
            number(m.groupValues[1], f)?.let { n ->
                val decimals = m.groupValues[1].substringAfter(f.decimalSeparator(), "").length
                val code = if (format.equals("General", true) || '%' !in format) (if (decimals > 0) "0." + "0".repeat(decimals) + "%" else "0%") else format
                return shown(n / 100, code).copy(format = code.takeIf { it != format })
            }
        }
        // a date (day first, as written here) or a time
        Regex("""^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})(?:\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?$""").matchEntire(t)?.let { m ->
            val (d, mo, y) = m.destructured
            val year = y.toInt().let { if (it < 100) 2000 + it else it }
            runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()?.let { date ->
                val time = m.groupValues[4].takeIf { it.isNotEmpty() }?.let { LocalTime.of(it.toInt(), m.groupValues[5].toInt(), m.groupValues[6].toIntOrNull() ?: 0) }
                val v = SheetCalc.serial(date.atTime(time ?: LocalTime.MIDNIGHT))
                val code = if (f.isDate(format)) format else if (time != null) "d/m/yyyy h:mm" else "d/m/yyyy"
                return shown(v, code).copy(format = code.takeIf { it != format })
            }
        }
        Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?$""").matchEntire(t)?.let { m ->
            val v = (m.groupValues[1].toInt() * 3600 + m.groupValues[2].toInt() * 60 + (m.groupValues[3].toIntOrNull() ?: 0)) / 86_400.0
            val code = if (f.isDate(format)) format else if (m.groupValues[3].isNotEmpty()) "h:mm:ss" else "h:mm"
            return shown(v, code).copy(format = code.takeIf { it != format })
        }
        number(t, f)?.let { n0 ->
            // a percent cell: 25 is 25%, as Excel's automatic percent entry (0,25 too)
            val n = if ('%' in SheetFormat.strip(format) && kotlin.math.abs(n0) >= 1) n0 / 100 else n0
            // a date cell given a plain number keeps it as a number (Excel would show the date of that number)
            return shown(n, format).copy(format = old?.format?.takeIf { !f.isDate(it) && '%' !in it })
        }
        return keep.copy(text = input, kind = SheetKind.Text, format = null)
    }

    /**
     * A typed number: the phone's decimal mark and thousands (1.234,5 here), or the other way when it cannot be the
     * phone's (12.5 with one dot and a decimal comma: a decimal point), a sign, € $ £.
     */
    fun number(s: String, f: SheetFormat): Double? {
        var t = s.trim().removePrefix("€").removeSuffix("€").removePrefix("$").removePrefix("£").trim()
        if (t.isEmpty() || !Regex("""^[+-]?[\d.,\s]*\d[\d.,\s]*(?:[eE][+-]?\d+)?$""").matches(t)) return null
        t = t.replace(" ", "").replace(" ", "")
        val dec = f.decimalSeparator(); val grp = f.groupSeparator()
        t = when {
            dec in t && grp in t -> t.replace(grp.toString(), "").replace(dec, '.')
            dec in t -> if (t.count { it == dec } == 1) t.replace(dec, '.') else return null
            grp in t -> if (Regex("""^[+-]?\d{1,3}(\Q$grp\E\d{3})+$""").matches(t)) t.replace(grp.toString(), "") else if (t.count { it == grp } == 1) t.replace(grp, '.') else return null
            else -> t
        }
        return t.toDoubleOrNull()
    }

    /**
     * A typed formula as Excel keeps it: ; between arguments (LibreOffice's habit) as commas, names and references in
     * capitals (=sum(a1;b2) is =SUM(A1,B2)) — not words in quotes, nor a sheet's name in ' '.
     */
    fun formula(s: String): String {
        val out = StringBuilder(); var quote = false; var sheet = false
        for (ch in s) {
            when {
                ch == '"' && !sheet -> { quote = !quote; out.append(ch) }
                ch == '\'' && !quote -> { sheet = !sheet; out.append(ch) }
                quote || sheet -> out.append(ch)
                ch == ';' -> out.append(',')
                else -> out.append(ch.uppercaseChar())
            }
        }
        return out.toString()
    }
}

/**
 * Formulas worked out again after an edit: those that read a changed cell, and those that read them, on and on (and
 * those that read another sheet's changed cells). Each comes back as its cell with its new value; a formula with a
 * function [SheetCalc] does not know keeps the file's value.
 */
internal object SheetRecalc {
    fun valueOf(cell: SheetCell?): Any? = when (cell?.kind) {
        null, SheetKind.Empty -> null
        SheetKind.Number -> cell.value
        SheetKind.Bool -> cell.value == 1.0
        SheetKind.Error -> SheetCalc.Err(cell.text)
        SheetKind.Text -> cell.text
    }

    /** [changed]: the cells of sheet [index] that changed. Returns each formula cell's new cell. */
    fun run(book: Workbook, index: Int, sheet: Sheet, changed: Collection<Long>): Map<Long, SheetCell> {
        val name = book.tabs[index].name
        // the changed cells by row, to test a range against quickly
        val dirty = TreeMap<Int, MutableSet<Int>>()
        fun mark(k: Long) { dirty.getOrPut(Sheet.row(k)) { HashSet() } += Sheet.col(k) }
        changed.forEach(::mark)
        fun hits(g: SheetRange) = dirty.subMap(g.r1, true, g.r2, true).values.any { cols -> cols.any { it in g.c1..g.c2 } }
        val elsewhere = book.edits.filterKeys { it != index }.mapKeys { book.tabs[it.key].name.lowercase() }
        fun hitsElsewhere(sheet: String, g: SheetRange) = elsewhere[sheet.lowercase()]?.keys?.any { k -> Sheet.row(k) in g.r1..g.r2 && Sheet.col(k) in g.c1..g.c2 } == true
        // every formula and what it reads
        val formulas = HashMap<Long, List<SheetCalc.Ref>>()
        sheet.cells.forEach { k, cell ->
            val f = cell.formula ?: return@forEach
            formulas[k] = try { SheetCalc.refs(SheetCalc.parse(f), names = book.names) } catch (_: SheetCalc.Unknown) { return@forEach }
        }
        val affected = LinkedHashSet<Long>()
        changed.filter { it in formulas }.forEach { affected += it }
        var grew = true
        while (grew) {
            grew = false
            for ((k, refs) in formulas) {
                if (k in affected) continue
                if (refs.any { r -> if (r.sheet == null || r.sheet.equals(name, true)) hits(r.range) else hitsElsewhere(r.sheet, r.range) }) { affected += k; mark(k); grew = true }
            }
        }
        // spills by the formula they come from (one look over the sheet, when needed)
        val spillIndex by lazy { HashMap<Long, MutableList<Long>>().also { m -> sheet.cells.forEach { k, c -> if (c.spilledFrom >= 0) m.getOrPut(c.spilledFrom) { ArrayList() } += k } } }
        fun spilled(from: Long): List<Long> = spillIndex[from].orEmpty()
        fun emptied(t: Long) = (sheet.cells[t] ?: SheetCell("", SheetKind.Empty)).copy(text = "", kind = SheetKind.Empty, value = null, color = null, spilledFrom = -1)
        val out = LinkedHashMap<Long, SheetCell>()
        // a spilling formula typed over or cleared: its spill's cells emptied
        for (k in changed) if (sheet.cells[k]?.formula == null) spilled(k).forEach { t -> out[t] = emptied(t) }
        if (affected.isEmpty()) return out
        // worked out in the order they need each other (a cell's formula first when another reads it)
        val done = HashMap<Long, Any>()
        val working = HashSet<Long>()
        val unknown = HashSet<Long>() // a function the engine does not know: the cell is left as the file has it
        val spills = HashMap<Long, SheetCalc.Arr>() // a formula's several values, to spill
        lateinit var ctx: SheetCalc.Context
        fun result(k: Long): Any? {
            done[k]?.let { return it }
            if (!working.add(k)) return SheetCalc.Err("#REF!") // a circle: Excel warns; here it is an error
            val v = try { SheetCalc.evalSpill(sheet.cells[k]!!.formula!!, ctx).let { r -> if (r is SheetCalc.Arr) { spills[k] = r; r.rows.firstOrNull()?.firstOrNull() ?: 0.0 } else r } } // an empty cell in a spill shows 0, as Excel's
                catch (_: SheetCalc.Unknown) { unknown += k; valueOf(sheet.cells[k]) ?: "" }
            working -= k; done[k] = v
            return v
        }
        ctx = SheetCalc.Context(SheetCalc.Cells { s, r, c ->
            if (s == null || s.equals(name, true)) { val k = Sheet.key(r, c); if (k in affected) result(k) else valueOf(sheet.cells[k]) }
            else book.tabs.indexOfFirst { it.name.equals(s, true) }.let { i -> if (i < 0) SheetCalc.Err("#REF!") else valueOf(book.sheet(i)[r, c]) }
        }, format = book.format, names = book.names, formulaOf = { s, r, c ->
            if (s == null || s.equals(name, true)) sheet.cells[Sheet.key(r, c)]?.formula
            else book.tabs.indexOfFirst { it.name.equals(s, true) }.takeIf { it >= 0 }?.let { book.sheet(it)[r, c]?.formula } })
        fun shown(old: SheetCell, v: Any?): SheetCell {
            val code = old.format ?: (sheet.look(old.style, book.styles)?.format ?: "General")
            return when (v) {
                is Double -> book.format.format(v, code).let { old.copy(text = it.text, kind = SheetKind.Number, value = v, color = it.color) }
                is Boolean -> old.copy(text = if (v) "TRUE" else "FALSE", kind = SheetKind.Bool, value = if (v) 1.0 else 0.0, color = null)
                is SheetCalc.Err -> old.copy(text = v.code, kind = SheetKind.Error, value = null, color = null)
                null -> old.copy(text = "", kind = SheetKind.Empty, value = null, color = null)
                else -> old.copy(text = v.toString(), kind = if (v.toString().isEmpty()) SheetKind.Empty else SheetKind.Text, value = null, color = null)
            }
        }
        // a spill's cells: each the cell below or right of its formula (the old spill's cells it no longer covers emptied);
        // a cell in the way with something of its own: Excel's #SPILL! instead
        for (k in affected) {
            val old = sheet.cells[k] ?: continue
            val v = result(k)
            if (k in unknown) continue
            val arr = spills[k]
            val r0 = Sheet.row(k); val c0 = Sheet.col(k)
            val area = arr?.let { SheetRange(r0, c0, r0 + it.rows.size - 1, c0 + it.width - 1) }?.takeIf { it.r2 > it.r1 || it.c2 > it.c1 }
            val before = if (old.spill != null) spilled(k) else emptyList()
            val blocked = area != null && (area.r1..area.r2).any { r -> (area.c1..area.c2).any { c -> val t = Sheet.key(r, c)
                t != k && sheet.cells[t]?.let { it.spilledFrom != k && (it.kind != SheetKind.Empty || it.formula != null) } == true } }
            before.forEach { t -> if (area == null || blocked || (Sheet.row(t) to Sheet.col(t)) !in area) out[t] = emptied(t) }
            if (area != null && !blocked) {
                out[k] = shown(old, v).copy(spill = area)
                for (r in area.r1..area.r2) for (c in area.c1..area.c2) { val t = Sheet.key(r, c); if (t == k) continue
                    out[t] = shown((sheet.cells[t] ?: SheetCell("", SheetKind.Empty)).copy(formula = null, spill = null, spilledFrom = k), arr.rows[r - r0][c - c0] ?: 0.0) }
            } else out[k] = (if (blocked) shown(old, SheetCalc.Err("#SPILL!")) else shown(old, v)).copy(spill = null)
        }
        return out
    }
}

/**
 * Formatting chosen cells (asked 16 Oct with editing): each cell's look changed by [change] — given the look and the
 * cell's place in the range, for borders round it — as a look the workbook has or a new one; a number shown again in a
 * new number format. Returns the changed cells, for [SheetHistory.apply].
 */
internal object SheetLook {
    fun change(book: Workbook, sheet: Sheet, keys: Collection<Long>, change: (SheetStyle, Long) -> SheetStyle): Map<Long, SheetCell> {
        val out = LinkedHashMap<Long, SheetCell>()
        for (k in keys) {
            val r = Sheet.row(k); val c = Sheet.col(k)
            val cell = sheet.cells[k] ?: SheetCell("", SheetKind.Empty, style = sheet.rowStyles[r] ?: sheet.colStyles[c] ?: 0)
            // the look the file gives it, under a table's or a rule's
            val base = cell.fileStyle
            val old = book.styles.getOrNull(base) ?: SheetStyle()
            val now = change(old, k)
            if (now == old) continue
            val i = book.styleFor(now)
            val format = if (now.format != old.format) null else cell.format
            val text = if (cell.kind == SheetKind.Number && cell.value != null && (format ?: now.format) != (cell.format ?: old.format))
                book.format.format(cell.value, format ?: now.format).let { cell.copy(text = it.text, color = it.color) } else cell
            out[k] = text.copy(style = i, own = -1, format = format)
        }
        return out
    }

    /** A number format with one more or one fewer decimal (General: 0.0 or 0). */
    fun decimals(code: String, delta: Int): String {
        if (code.equals("General", true) || code == "@") return if (delta > 0) "0.0" else "0"
        return SheetFormat.split(code).joinToString(";") { sec ->
            val m = Regex("""([0#?,]*)(\.([0#?]*))?""").findAll(sec).firstOrNull { it.value.any { c -> c == '0' || c == '#' } } ?: return@joinToString sec
            val now = m.groupValues[3].length
            val n = maxOf(0, now + delta)
            sec.replaceRange(m.range, m.groupValues[1] + if (n > 0) "." + "0".repeat(n) else "")
        }
    }

    /**
     * One of Excel's cell styles: its font colour, fill, weight, size, font and borders ([box]: all four) — put over a look,
     * only the [parts] Excel's style includes ("font", "fill", "border"), the rest of the cell's look kept.
     */
    class CellStyle(val name: String, val color: Int?, val fill: Int? = null, val bold: Boolean = false, val italic: Boolean = false,
                    val size: Float? = null, val font: String? = null, val top: SheetLine? = null, val bottom: SheetLine? = null, val box: SheetLine? = null,
                    val parts: String = "font fill border")
    private fun rgb(v: Long) = (0xFF000000 or v).toInt()
    private fun line(v: Long, width: Float = 1f, double: Boolean = false) = SheetLine(rgb(v), width, double)
    /** Home › Cell styles: Excel's own (its Office theme's colours), in its gallery's order. */
    val STYLES = listOf(
        CellStyle("Normal", null),
        CellStyle("Good", rgb(0x006100), rgb(0xC6EFCE), parts = "font fill"), CellStyle("Bad", rgb(0x9C0006), rgb(0xFFC7CE), parts = "font fill"),
        CellStyle("Neutral", rgb(0x9C5700), rgb(0xFFEB9C), parts = "font fill"),
        CellStyle("Calculation", rgb(0xFA7D00), rgb(0xF2F2F2), bold = true, box = line(0x7F7F7F)),
        CellStyle("Check cell", rgb(0xFFFFFF), rgb(0xA5A5A5), bold = true, box = line(0x3F3F3F, 3f, double = true)),
        CellStyle("Explanatory text", rgb(0x7F7F7F), italic = true, parts = "font"),
        CellStyle("Input", rgb(0x3F3F76), rgb(0xFFCC99), box = line(0x7F7F7F)),
        CellStyle("Linked cell", rgb(0xFA7D00), bottom = line(0xFF8001, 3f, double = true), parts = "font border"),
        CellStyle("Note", null, rgb(0xFFFFCC), box = line(0xB2B2B2), parts = "fill border"),
        CellStyle("Output", rgb(0x3F3F3F), rgb(0xF2F2F2), bold = true, box = line(0x3F3F3F)),
        CellStyle("Warning text", rgb(0xFF0000), parts = "font"),
        CellStyle("Title", rgb(0x44546A), size = 18f, font = "Calibri Light", parts = "font"),
        CellStyle("Heading 1", rgb(0x44546A), bold = true, size = 15f, bottom = line(0x4472C4, 3f), parts = "font border"),
        CellStyle("Heading 2", rgb(0x44546A), bold = true, size = 13f, bottom = line(0xA2B8E1, 3f), parts = "font border"),
        CellStyle("Heading 3", rgb(0x44546A), bold = true, bottom = line(0x8EA9DB, 2f), parts = "font border"),
        CellStyle("Heading 4", rgb(0x44546A), bold = true, parts = "font"),
        CellStyle("Total", null, bold = true, top = line(0x4472C4), bottom = line(0x4472C4, 3f, double = true), parts = "font border"),
    )
    /** [st] in cell style [c]: the parts it includes those of [c] over the workbook's [normal]; the number format, alignment and the rest kept. */
    fun styled(st: SheetStyle, c: CellStyle, normal: SheetStyle): SheetStyle {
        var s = st
        if ("font" in c.parts) s = s.copy(font = c.font ?: normal.font, size = c.size ?: normal.size, bold = c.bold, italic = c.italic, underline = false, strike = false, color = c.color)
        if ("fill" in c.parts) s = s.copy(fill = c.fill)
        if ("border" in c.parts) s = s.copy(left = c.box, right = c.box, top = c.box ?: c.top, bottom = c.box ?: c.bottom)
        return s
    }

    /** A border round a range: each edge where the cell is on the range's edge ([all]: between the cells too). */
    fun edges(st: SheetStyle, k: Long, g: SheetRange, line: SheetLine?, all: Boolean, onlyBottom: Boolean = false): SheetStyle {
        val r = Sheet.row(k); val c = Sheet.col(k)
        if (onlyBottom) return st.copy(bottom = if (r == g.r2) line else st.bottom)
        if (line == null) return st.copy(left = null, right = null, top = null, bottom = null)
        return st.copy(
            left = if (all || c == g.c1) line else st.left, right = if (all || c == g.c2) line else st.right,
            top = if (all || r == g.r1) line else st.top, bottom = if (all || r == g.r2) line else st.bottom,
        )
    }
}

/** Undo and redo of edits: each step the cells as they were and as they became (the formulas worked out again too). */
internal class SheetHistory {
    /** Cells changed: as they were, as they became. */
    open class Step(val sheet: Int, val before: Map<Long, SheetCell>, val after: Map<Long, SheetCell>)
    /**
     * A change to the whole file (rows or columns inserted, deleted): the copy before it and the edits not yet in it,
     * and the copy after.
     */
    class FileStep(sheet: Int, val from: java.io.File, val edits: Map<Int, LinkedHashMap<Long, SheetCell>>, val to: java.io.File, val focus: Long) :
        Step(sheet, mapOf(focus to SheetCell("", SheetKind.Empty)), mapOf(focus to SheetCell("", SheetKind.Empty)))
    private val undos = ArrayDeque<Step>(); private val redos = ArrayDeque<Step>()
    /** Steps since the file was saved (undoing them all is a saved file again). */
    var unsaved = 0; private set
    val canUndo get() = undos.isNotEmpty()
    val canRedo get() = redos.isNotEmpty()

    /** Told when a change is refused: a protected sheet's locked cells (Excel's Protect Sheet; its password is not asked here). */
    var refused: (() -> Unit)? = null
    /** Whether cell [k] of [sheet] is one a protected sheet keeps as it is (locked: its look's, else its row's or column's, else Excel's default). */
    fun locked(book: Workbook, sheet: Sheet, k: Long): Boolean = sheet.protected &&
        (book.styles.getOrNull(sheet.cells[k]?.fileStyle ?: sheet.rowStyles[Sheet.row(k)] ?: sheet.colStyles[Sheet.col(k)] ?: 0) ?: SheetStyle()).locked

    /** Sets [changes] on sheet [index], works out what counts on them, and keeps it as one step. */
    fun apply(book: Workbook, index: Int, changes: Map<Long, SheetCell>) {
        val sheet = book.sheet(index)
        if (changes.keys.any { locked(book, sheet, it) }) { refused?.invoke(); return }
        val before = LinkedHashMap<Long, SheetCell>()
        fun was(k: Long) = sheet.cells[k] ?: SheetCell("", SheetKind.Empty)
        // a cell typed into another's spill: that formula worked out again (#SPILL! now)
        val spilling = changes.keys.mapNotNull { k -> sheet.cells[k]?.spilledFrom?.takeIf { it >= 0 && changes[k]?.spilledFrom != it } }
        changes.forEach { (k, c) -> before[k] = was(k); book.edit(index, k, c) }
        val worked = SheetRecalc.run(book, index, sheet, changes.keys + spilling)
        worked.forEach { (k, c) -> before.putIfAbsent(k, was(k)); book.edit(index, k, c) }
        SheetRules.again(sheet, book.styles) // a value crossing a rule's line changes its look
        undos.addLast(Step(index, before, changes + worked)); if (undos.size > 200) undos.removeFirst()
        redos.clear(); unsaved++
        WriterLog.i("Sheets: ${changes.size} cells changed, ${worked.size} formulas worked out again")
    }

    /** A whole-file change done: one step. */
    fun file(step: FileStep) { undos.addLast(step); if (undos.size > 200) undos.removeFirst(); redos.clear(); unsaved++ }

    /** Undone: a cell step's cells put back here; a [FileStep] is for the screen to read its copy again. */
    fun undo(book: Workbook): Step? = undos.removeLastOrNull()?.also { s ->
        if (s !is FileStep) { s.before.forEach { (k, c) -> book.edit(s.sheet, k, c) }; SheetRules.again(book.sheet(s.sheet), book.styles) }
        redos.addLast(s); unsaved--
    }
    fun redo(book: Workbook): Step? = redos.removeLastOrNull()?.also { s ->
        if (s !is FileStep) { s.after.forEach { (k, c) -> book.edit(s.sheet, k, c) }; SheetRules.again(book.sheet(s.sheet), book.styles) }
        undos.addLast(s); unsaved++
    }
    fun saved() { unsaved = 0; undos.clear(); redos.clear() }
    /** The file saved with every step in it: Undo keeps going back past it (as Word's), the steps since counted from here. */
    fun markSaved() { unsaved = 0 }
}

/**
 * The functions the cell bar offers while typing (asked 16 Oct: "I don't know them by heart"): each with what it takes and
 * what it does, narrowed by the letters typed before the cursor; inside one's brackets, what that one takes.
 */
object SheetFunctions {
    class Fn(val name: String, val args: String, val says: String)

    val all = listOf(
        Fn("SUM", "number1, number2, …", "Adds the numbers"),
        Fn("AVERAGE", "number1, number2, …", "The average of the numbers"),
        Fn("COUNT", "value1, value2, …", "How many cells hold numbers"),
        Fn("COUNTA", "value1, value2, …", "How many cells are not empty"),
        Fn("COUNTBLANK", "range", "How many cells are empty"),
        Fn("MAX", "number1, number2, …", "The largest number"),
        Fn("MIN", "number1, number2, …", "The smallest number"),
        Fn("IF", "test, if true, if false", "One value or another, by a test"),
        Fn("SUMIF", "range, criterion, [sum range]", "Adds the cells that match"),
        Fn("COUNTIF", "range, criterion", "Counts the cells that match"),
        Fn("AVERAGEIF", "range, criterion, [average range]", "The average of the cells that match"),
        Fn("SUMIFS", "sum range, range1, criterion1, …", "Adds the cells that match every test"),
        Fn("COUNTIFS", "range1, criterion1, …", "Counts the cells that match every test"),
        Fn("AVERAGEIFS", "average range, range1, criterion1, …", "The average of the cells that match every test"),
        Fn("MAXIFS", "max range, range1, criterion1, …", "The largest of the cells that match"),
        Fn("MINIFS", "min range, range1, criterion1, …", "The smallest of the cells that match"),
        Fn("ROUND", "number, digits", "Rounds to so many decimals"),
        Fn("ROUNDUP", "number, digits", "Rounds away from zero"),
        Fn("ROUNDDOWN", "number, digits", "Rounds towards zero"),
        Fn("TRUNC", "number, [digits]", "Cuts the decimals off"),
        Fn("INT", "number", "Rounds down to a whole number"),
        Fn("MOD", "number, divisor", "The remainder of a division"),
        Fn("ABS", "number", "The number without its sign"),
        Fn("SQRT", "number", "The square root"),
        Fn("POWER", "number, power", "A number raised to a power"),
        Fn("PRODUCT", "number1, number2, …", "Multiplies the numbers"),
        Fn("MEDIAN", "number1, number2, …", "The middle number"),
        Fn("STDEV", "number1, number2, …", "Standard deviation of a sample"),
        Fn("STDEV.P", "number1, number2, …", "Standard deviation of everyone"),
        Fn("STDEV.S", "number1, number2, …", "Standard deviation of a sample (as STDEV)"),
        Fn("STDEVP", "number1, number2, …", "Standard deviation of everyone (as STDEV.P)"),
        Fn("VAR", "number1, number2, …", "Variance of a sample"),
        Fn("VAR.P", "number1, number2, …", "Variance of everyone"),
        Fn("VAR.S", "number1, number2, …", "Variance of a sample (as VAR)"),
        Fn("VARP", "number1, number2, …", "Variance of everyone (as VAR.P)"),
        Fn("RANK", "number, range, [order]", "Where a number stands in a list"),
        Fn("RANK.EQ", "number, range, [order]", "Where a number stands in a list (as RANK)"),
        Fn("LARGE", "range, k", "The k-th largest"),
        Fn("SMALL", "range, k", "The k-th smallest"),
        Fn("CEILING", "number, step", "Rounds up to a multiple"),
        Fn("FLOOR", "number, step", "Rounds down to a multiple"),
        Fn("CEILING.MATH", "number, [step]", "Rounds up to a multiple (1 if not said)"),
        Fn("FLOOR.MATH", "number, [step]", "Rounds down to a multiple (1 if not said)"),
        Fn("MROUND", "number, multiple", "Rounds to the nearest multiple"),
        Fn("SIGN", "number", "1, 0 or −1, by the sign"),
        Fn("EXP", "number", "e raised to the number"),
        Fn("LN", "number", "The natural logarithm"),
        Fn("LOG10", "number", "The logarithm in base 10"),
        Fn("PI", "", "π, 3,14159…"),
        Fn("AND", "test1, test2, …", "TRUE when every test is"),
        Fn("OR", "test1, test2, …", "TRUE when any test is"),
        Fn("NOT", "test", "The opposite of the test"),
        Fn("IFERROR", "value, if error", "The value, or another when it is an error"),
        Fn("IFNA", "value, if #N/A", "The value, or another when it is #N/A"),
        Fn("VLOOKUP", "key, table, column, [FALSE]", "Finds a row by its first cell; a cell of it"),
        Fn("HLOOKUP", "key, table, row, [FALSE]", "Finds a column by its top cell; a cell of it"),
        Fn("XLOOKUP", "key, look in, return from, [if none]", "Finds a key and returns what is beside it"),
        Fn("FILTER", "cells, keep (e.g. B2:B9>5), [if none]", "The rows that pass, spilled below"),
        Fn("SORT", "cells, [by column], [1 up or -1 down]", "The rows sorted, spilled below"),
        Fn("UNIQUE", "cells, [by column], [only once]", "Each row once, spilled below"),
        Fn("SEQUENCE", "rows, [columns], [start], [step]", "Numbers in order, spilled below"),
        Fn("MATCH", "key, range, [0]", "Where a value is in a range"),
        Fn("INDEX", "range, row, [column]", "The cell at a row and column of a range"),
        Fn("CHOOSE", "n, value1, value2, …", "The n-th of the values"),
        Fn("ROW", "[cell]", "The cell's row number"),
        Fn("COLUMN", "[cell]", "The cell's column number"),
        Fn("ROWS", "range", "How many rows a range has"),
        Fn("COLUMNS", "range", "How many columns a range has"),
        Fn("CONCAT", "text1, text2, …", "Joins texts together"),
        Fn("CONCATENATE", "text1, text2, …", "Joins texts together"),
        Fn("TEXTJOIN", "between, skip empty, text1, …", "Joins texts with something between"),
        Fn("LEN", "text", "How many characters"),
        Fn("LEFT", "text, [count]", "The first characters"),
        Fn("RIGHT", "text, [count]", "The last characters"),
        Fn("MID", "text, start, count", "Characters from the middle"),
        Fn("UPPER", "text", "IN CAPITALS"),
        Fn("LOWER", "text", "in small letters"),
        Fn("PROPER", "text", "Each Word Capitalised"),
        Fn("TRIM", "text", "Takes out extra spaces"),
        Fn("SUBSTITUTE", "text, old, new", "Puts new text in place of old"),
        Fn("REPT", "text, times", "The text repeated"),
        Fn("FIND", "what, text, [start]", "Where a text is (case counts)"),
        Fn("SEARCH", "what, text, [start]", "Where a text is (any case, * and ?)"),
        Fn("EXACT", "text1, text2", "TRUE when two texts are the same"),
        Fn("VALUE", "text", "A number written as text, as a number"),
        Fn("TEXT", "number, format", "A number written in a format"),
        Fn("TODAY", "", "Today's date"),
        Fn("NOW", "", "The date and time now"),
        Fn("DATE", "year, month, day", "A date from its parts"),
        Fn("YEAR", "date", "The year of a date"),
        Fn("MONTH", "date", "The month of a date"),
        Fn("DAY", "date", "The day of a date"),
        Fn("WEEKDAY", "date, [type]", "The day of the week, as a number"),
        Fn("HOUR", "time", "The hour of a time"),
        Fn("MINUTE", "time", "The minutes of a time"),
        Fn("SECOND", "time", "The seconds of a time"),
        Fn("DAYS", "end, start", "Days between two dates"),
        Fn("EDATE", "date, months", "The date so many months later"),
        Fn("EOMONTH", "date, months", "The last day of a month"),
        Fn("ISBLANK", "cell", "TRUE when the cell is empty"),
        Fn("ISNUMBER", "value", "TRUE when it is a number"),
        Fn("ISTEXT", "value", "TRUE when it is text"),
        Fn("ISERROR", "value", "TRUE when it is an error"),
        Fn("ISNA", "value", "TRUE when it is #N/A"),
        Fn("NA", "", "The error #N/A"),
    )

    private val byName = all.associateBy { it.name }

    /** Where the name typed before [cursor] starts (letters, digits and dots after the last other character). */
    fun start(t: String, cursor: Int): Int {
        var i = cursor.coerceIn(0, t.length)
        while (i > 0 && (t[i - 1].isLetterOrDigit() || t[i - 1] == '.')) i--
        return i
    }

    /** The functions for what is typed: those starting with the letters, then those with them inside; all for "=" alone. */
    fun matching(t: String, cursor: Int): List<Fn> {
        val s = start(t, cursor)
        val typed = t.substring(s, cursor.coerceIn(s, t.length)).uppercase()
        // a number, a cell's name (B12) or a sheet's (Rows!): not a function's name
        if (typed.isNotEmpty() && (typed[0].isDigit() || Regex("""[A-Z]{1,3}\d+""").matches(typed) || t.getOrNull(cursor) == '!')) return emptyList()
        if (typed.isEmpty()) return if (t.substring(0, s).trimEnd().let { it == "=" }) all else emptyList()
        return all.filter { it.name.startsWith(typed) } + all.filter { !it.name.startsWith(typed) && typed.length > 1 && typed in it.name }
    }

    /** The function whose brackets the cursor is in, for what it takes. */
    fun inside(t: String, cursor: Int): Fn? {
        var depth = 0; var i = cursor.coerceIn(0, t.length) - 1; var quoted = false
        while (i >= 0) {
            val c = t[i]
            if (c == '"') quoted = !quoted
            else if (!quoted) when (c) {
                ')' -> depth++
                '(' -> if (depth == 0) return byName[t.substring(start(t, i), i).uppercase()] else depth--
            }
            i--
        }
        return null
    }

    /** [fn] put in for the name typed before [cursor]: "NAME(", the cursor after it, "=" first when the cell had none. */
    fun insert(t: String, cursor: Int, fn: Fn): Pair<String, Int> {
        val s = start(t, cursor)
        val rest = t.substring(cursor.coerceIn(s, t.length))
        val open = if (rest.startsWith("(")) "" else "(" + (if (fn.args.isEmpty()) ")" else "") // its bracket already typed: kept
        var out = t.substring(0, s) + fn.name + open + rest
        var at = s + fn.name.length + 1 + (if (fn.args.isEmpty() && open.isNotEmpty()) 1 else 0)
        if (!out.startsWith("=")) { out = "=$out"; at++ }
        return out to at
    }
}

/**
 * Excel's fill handle (asked 16 Oct; on the phone a dot at the chosen cells' corner, dragged): the chosen cells carried
 * on — numbers as their series (1, 2 → 3, 4), a date day by day, "Week 1" → "Week 2", month and day names on (January →
 * February, Mon → Tue), formulas with their relative references moved ($ ones kept), anything else copied over and over.
 */
internal object SheetFill {
    private val REF = Regex("""(?<![\p{L}\p{N}_.$'])(?:('(?:[^']|'')+'|[\p{L}_][\p{L}\p{N}_.]*)!)?(\$?[A-Za-z]{1,3}\$?\d+(?::\$?[A-Za-z]{1,3}\$?\d+)?)(?![\p{L}\p{N}_(!])""")
    private val CELL = Regex("""(\$?)([A-Za-z]{1,3})(\$?)(\d+)""")
    private val NUMBERED = Regex("""^(.*?)(\d+)$""")
    // Excel's own lists the fill handle runs through (its Custom Lists), long and short
    private val NAMES = listOf(java.time.format.TextStyle.FULL, java.time.format.TextStyle.SHORT).flatMap { st ->
        listOf(java.time.Month.entries.map { it.getDisplayName(st, java.util.Locale.ENGLISH) }, java.time.DayOfWeek.entries.map { it.getDisplayName(st, java.util.Locale.ENGLISH) }) }
    private fun named(t: String) = NAMES.firstOrNull { l -> l.any { it.equals(t.trim(), true) } }?.let { l -> l to l.indexOfFirst { it.equals(t.trim(), true) } }
    private fun cased(like: String, word: String) = when { like.length > 1 && like == like.uppercase() -> word.uppercase(); like == like.lowercase() -> word.lowercase(); else -> word }

    /**
     * Excel's Fill ▸ Series: [count] values from [start] on, [step] apart — added ("Linear"), multiplied ("Growth"), or a
     * date's ("Day", "Weekday": Monday to Friday, "Month", "Year"), [start] and the dates as Excel's day numbers.
     */
    fun stepped(start: Double, count: Int, step: Double, type: String): List<Double> {
        val epoch = LocalDate.of(1899, 12, 30)
        val day = epoch.plusDays(kotlin.math.floor(start).toLong()); val time = start - kotlin.math.floor(start)
        fun serial(d: LocalDate) = (d.toEpochDay() - epoch.toEpochDay()) + time
        var weekday = day
        return (0 until count).map { i -> when (type) {
            "Growth" -> start * Math.pow(step, i.toDouble())
            "Day" -> start + step * i
            "Weekday" -> { if (i > 0) repeat(kotlin.math.abs(step.toInt())) { do weekday = weekday.plusDays(if (step < 0) -1 else 1) while (weekday.dayOfWeek.value > 5) }; serial(weekday) }
            "Month" -> serial(day.plusMonths((step * i).toLong()))
            "Year" -> serial(day.plusYears((step * i).toLong()))
            else -> start + step * i
        } }
    }

    /** A formula copied [dr] rows and [dc] columns away: its relative references follow; one moved off the sheet is #REF!. */
    // shortcut: whole columns and rows (A:A, 3:3) stay as they are; move them too if fills of such formulas come up
    fun moved(f: String, dr: Int, dc: Int): String = f.split('"').mapIndexed { i, part ->
        if (i % 2 == 1) part else REF.replace(part) { m ->
            var off = false
            val ref = CELL.replace(m.groupValues[2]) { c ->
                val (cAbs, col, rAbs, row) = c.destructured
                val index = col.uppercase().fold(0) { a, ch -> a * 26 + (ch - 'A' + 1) } - 1
                val cc = if (cAbs.isEmpty()) index + dc else index
                val rr = if (rAbs.isEmpty()) row.toInt() + dr else row.toInt()
                if (cc < 0 || rr < 1) off = true
                if (off) "" else cAbs + Sheets.colName(cc) + rAbs + rr
            }
            if (off) "#REF!" else (m.groupValues[1].takeIf { it.isNotEmpty() }?.let { "$it!" } ?: "") + ref
        }
    }.joinToString("\"")

    /**
     * The [count] cells after [src] (one line of the fill, in its direction), [move] giving source cell j's formula as it
     * reads in new cell i, [codeOf] a cell's number format.
     */
    fun series(src: List<SheetCell?>, count: Int, f: SheetFormat, codeOf: (SheetCell) -> String, move: (String, Int, Int) -> String): List<SheetCell> {
        val n = src.size
        val numbers = src.all { it != null && it.formula == null && it.kind == SheetKind.Number && it.value != null }
        // a straight line through the numbers (Excel's trend: 1, 2 → 3, 4; 10, 20 → 30)
        val xs = src.indices.map { it.toDouble() }; val ys = src.map { it?.value ?: 0.0 }
        val mx = xs.average(); val my = ys.average()
        val slope = if (n < 2) 0.0 else xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / xs.sumOf { (it - mx) * (it - mx) }
        val texts = src.map { c -> c?.takeIf { it.formula == null && it.kind == SheetKind.Text }?.let { NUMBERED.matchEntire(it.text) } }
        val samePrefix = n >= 2 && texts.all { it != null } && texts.map { it!!.groupValues[1] }.distinct().size == 1
        val names = src.map { c -> c?.takeIf { it.formula == null && it.kind == SheetKind.Text }?.let { named(it.text) } }
        // names from one list: their step (Jan, Mar → May), else one on
        val nameStep = if (n >= 2 && names.all { it != null } && names.map { it!!.first }.distinct().size == 1) names.last()!!.second - names[n - 2]!!.second else 1
        val textSlope = if (samePrefix) texts.last()!!.groupValues[2].toDouble() - texts[n - 2]!!.groupValues[2].toDouble() else 1.0
        fun shown(s: SheetCell, v: Double) = f.format(v, codeOf(s)).let { s.copy(value = v, text = it.text, color = it.color, note = null, link = null) }
        return (0 until count).map { i ->
            val j = i % n; val at = n + i
            val s = src[j] ?: return@map SheetCell("", SheetKind.Empty)
            val t = texts[j]
            when {
                s.formula != null -> s.copy(formula = move(s.formula, j, i), text = "", kind = SheetKind.Empty, value = null, note = null, link = null)
                numbers && n >= 2 -> shown(s, my + slope * (at - mx))
                numbers && f.isDate(codeOf(s)) -> shown(s, s.value!! + at) // a date: the next days
                names[j] != null -> { val (l, k) = names[j]!!; val from = if (n >= 2 && names.all { it?.first == l }) names.last()!!.second + nameStep * (at - (n - 1)) else k + (i / n + 1)
                    s.copy(text = cased(s.text.trim(), l[Math.floorMod(from, l.size)]), note = null, link = null) }
                t != null -> {
                    val digits = t.groupValues[2]
                    // one prefix: its numbers' step on from the last (Week 1, Week 3 → Week 5); else each one up by one a round
                    val k = if (samePrefix) texts.last()!!.groupValues[2].toLong() + (textSlope * (at - (n - 1))).toLong() else digits.toLong() + (i / n + 1)
                    s.copy(text = t.groupValues[1] + k.toString().padStart(digits.length, '0'), note = null, link = null)
                }
                else -> s.copy(note = null)
            }
        }
    }
}

/**
 * Paste (asked 16 Oct): cells copied here go in with their formulas moved, looks and formats as they were; text from
 * another app is read as a table (a tab between cells, a line between rows, as spreadsheets copy them) and typed in.
 */
internal object SheetPaste {
    /** Cells copied in this app: rows of (the sheet's row, column, the cell), and the text put on the clipboard for them. */
    class Clip(val sheet: Int, val cells: List<List<Triple<Int, Int, SheetCell?>>>, val text: String, val cut: Boolean)

    /** Text as a table: a cell in quotes may hold tabs, lines and "" for a quote (Excel's and LibreOffice's way). */
    fun table(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>(); var row = ArrayList<String>(); val cell = StringBuilder()
        var i = 0; var quoted = false
        val t = text.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n')
        while (i < t.length) {
            val ch = t[i]
            when {
                quoted && ch == '"' && t.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                quoted && ch == '"' -> quoted = false
                !quoted && ch == '"' && cell.isEmpty() -> quoted = true
                !quoted && ch == '\t' -> { row.add(cell.toString()); cell.clear() }
                !quoted && ch == '\n' -> { row.add(cell.toString()); cell.clear(); rows.add(row); row = ArrayList() }
                else -> cell.append(ch)
            }
            i++
        }
        row.add(cell.toString()); rows.add(row)
        return rows
    }
}

/**
 * Excel's Insert ▸ Shift cells down / right and Delete ▸ Shift cells up / left on cells [g] of [sheet]: as cell changes
 * (one step of Undo) — the cells after them in their columns (rows) moved, [g]'s own gone when deleting, and every
 * formula on the sheet that reads the moved cells following them ([SheetShift] limited to those columns or rows).
 * shortcut: merged cells, rules, validations, links, notes and other sheets' formulas stay where they were; upgrade by
 * giving the file-level SheetShift cell moves across rows.
 */
internal object SheetCellShift {
    fun changes(sheet: Sheet, g: SheetRange, rows: Boolean, insert: Boolean): Map<Long, SheetCell> {
        val n = if (rows) g.r2 - g.r1 + 1 else g.c2 - g.c1 + 1
        val shift = SheetShift(sheet.name, rows, if (rows) g.r1 else g.c1, if (insert) n else -n, if (rows) g.c1..g.c2 else g.r1..g.r2)
        val left = LinkedHashMap<Long, SheetCell>(); val placed = HashMap<Long, SheetCell>()
        sheet.cells.forEach { k, cell ->
            val r = Sheet.row(k); val c = Sheet.col(k)
            val f = cell.formula?.let { shift.formula(it, sheet.name) }
            val now = if (f != null && f != cell.formula) cell.copy(formula = f, text = "", kind = SheetKind.Empty, value = null) else cell
            val to = shift.cell(r, c)
            if (to != r to c) { left[k] = SheetCell("", SheetKind.Empty); to?.let { placed[Sheet.key(it.first, it.second)] = now } }
            else if (now !== cell) placed[k] = now
        }
        return left.apply { putAll(placed) } // a moved cell lands where another left: it wins
    }
}

/**
 * Data › Sort (Excel's dialog, changing the file): rows of cells [g] (after its first with [headers]) reordered by [keys]
 * (a column, ascending or not), then by the next — Excel's order: numbers, words (any case), TRUE and FALSE, errors,
 * and blanks last either way; rows alike keep their order. Formulas move as Excel's do when it sorts (references in
 * their own row follow). The cell changes, for one step of Undo.
 */
internal object SheetSortRows {
    fun changes(sheet: Sheet, g: SheetRange, keys: List<Pair<Int, Boolean>>, headers: Boolean): Map<Long, SheetCell> {
        val rows = ((if (headers) g.r1 + 1 else g.r1)..g.r2).toList()
        fun rank(c: SheetCell?) = when { c == null || c.kind == SheetKind.Empty -> 4; c.kind == SheetKind.Number -> 0; c.kind == SheetKind.Bool -> 2; c.kind == SheetKind.Error -> 3; else -> 1 }
        val order = Comparator<Int> { a, b ->
            for ((col, up) in keys) {
                val x = sheet[a, col]; val y = sheet[b, col]; val rx = rank(x); val ry = rank(y)
                if (rx == 4 || ry == 4) { if (rx != ry) return@Comparator rx.compareTo(ry) else continue } // blanks last, up or down
                val d = if (rx != ry) rx.compareTo(ry) else when (rx) {
                    0, 2 -> (x!!.value ?: 0.0).compareTo(y!!.value ?: 0.0)
                    1 -> x!!.text.compareTo(y!!.text, ignoreCase = true)
                    else -> 0
                }
                if (d != 0) return@Comparator if (up) d else -d
            }
            0
        }
        val out = LinkedHashMap<Long, SheetCell>()
        rows.sortedWith(order).forEachIndexed { i, from -> val to = rows[i]
            if (from != to) for (c in g.c1..g.c2) out[Sheet.key(to, c)] = sheet[from, c]?.let { if (it.formula != null) it.copy(formula = SheetFill.moved(it.formula, to - from, 0), text = "", kind = SheetKind.Empty, value = null) else it } ?: SheetCell("", SheetKind.Empty)
        }
        return out
    }

    /** Excel's current region: the block of filled cells round [r], [c] (no empty row or column through it), within [rows] × [cols]. */
    fun region(sheet: Sheet, r: Int, c: Int, rows: Int, cols: Int): SheetRange {
        var g = SheetRange(r, c, r, c)
        fun filled(rr: Int, cc: Int) = rr in 0 until rows && cc in 0 until cols && sheet[rr, cc]?.let { it.kind != SheetKind.Empty || it.formula != null } == true
        while (true) {
            val up = (g.c1 - 1..g.c2 + 1).any { filled(g.r1 - 1, it) }; val down = (g.c1 - 1..g.c2 + 1).any { filled(g.r2 + 1, it) }
            val left = (g.r1 - 1..g.r2 + 1).any { filled(it, g.c1 - 1) }; val right = (g.r1 - 1..g.r2 + 1).any { filled(it, g.c2 + 1) }
            if (!up && !down && !left && !right) return g
            g = SheetRange(if (up) g.r1 - 1 else g.r1, if (left) g.c1 - 1 else g.c1, if (down) g.r2 + 1 else g.r2, if (right) g.c2 + 1 else g.c2)
        }
    }
}

/**
 * Formulas › Trace Precedents, Trace Dependents and Error Checking (Excel's): the cells a cell's formula reads, the cells
 * whose formulas read it (this sheet's), and the cells showing an error, in reading order — marked on the sheet as found
 * cells are (Excel draws arrows; a phone marks them).
 */
internal object SheetTrace {
    private fun refs(f: String, names: Map<String, String>) = runCatching { SheetCalc.refs(SheetCalc.parse(f), names = names) }.getOrDefault(emptyList())

    fun precedents(sheet: Sheet, r: Int, c: Int, names: Map<String, String> = emptyMap()): Set<Long> {
        val f = sheet[r, c]?.formula ?: return emptySet()
        return refs(f, names).filter { it.sheet == null || it.sheet.trim('\'').equals(sheet.name, true) }
            .flatMap { g -> (g.range.r1..minOf(g.range.r2, g.range.r1 + 9_999)).flatMap { rr -> (g.range.c1..minOf(g.range.c2, g.range.c1 + 99)).map { cc -> Sheet.key(rr, cc) } } }.toSet()
    }

    fun dependents(sheet: Sheet, r: Int, c: Int, names: Map<String, String> = emptyMap()): Set<Long> {
        val out = HashSet<Long>()
        sheet.cells.forEach { k, cell -> val f = cell.formula ?: return@forEach
            if (refs(f, names).any { (it.sheet == null || it.sheet.trim('\'').equals(sheet.name, true)) && r in it.range.r1..it.range.r2 && c in it.range.c1..it.range.c2 }) out += k }
        return out
    }

    fun errors(sheet: Sheet): List<Long> = ArrayList<Long>().also { out -> sheet.cells.forEach { k, cell -> if (cell.kind == SheetKind.Error) out += k } }
        .sortedWith(compareBy({ Sheet.row(it) }, { Sheet.col(it) }))
}

/**
 * Review › Spelling in Tables (Words' dictionaries, [WriterSpelling]): each word of the sheet's cells of words (not
 * formulas, not numbers), in reading order, to check; a word changed in its cell.
 */
internal object SheetSpelling {
    /** The words to check: their cell, where they are in its words, the word. */
    fun words(sheet: Sheet): List<Triple<Long, IntRange, String>> {
        val out = ArrayList<Triple<Long, IntRange, String>>()
        sheet.cells.forEach { k, c -> if (c.kind == SheetKind.Text && c.formula == null) WriterSpelling.words(c.text).forEach { r -> out += Triple(k, r, c.text.substring(r)) } }
        return out.sortedWith(compareBy({ Sheet.row(it.first) }, { Sheet.col(it.first) }, { it.second.first }))
    }

    /** Cell [cell]'s words with [r] made [with] (its rich text's looks given up: one look, the cell's). */
    fun changed(cell: SheetCell, r: IntRange, with: String) = cell.copy(text = cell.text.replaceRange(r, with), runs = null)
}

/**
 * Data › What-If › Goal Seek (Excel's): the value of cell [by] that makes formula cell [set] give [to] — tried as the
 * sheet works it out (each try the cell changed, its formulas worked out again, then put back), by the secant method
 * from its own value; null when no value is found (a hundred tries).
 */
internal object SheetGoalSeek {
    fun solve(book: Workbook, index: Int, set: Long, to: Double, by: Long): Double? {
        val sheet = book.sheet(index)
        val was = sheet.cells[by] ?: SheetCell("", SheetKind.Empty)
        fun f(x: Double): Double? {
            book.edit(index, by, was.copy(text = SheetFormat().general(x), kind = SheetKind.Number, value = x, formula = null))
            val out = SheetRecalc.run(book, index, sheet, listOf(by))
            return (out[set] ?: sheet.cells[set])?.value?.minus(to)
        }
        try {
            var x0 = was.value ?: 0.0; var x1 = if (x0 == 0.0) 1.0 else x0 * 1.1
            var f0 = f(x0) ?: return null; var f1 = f(x1) ?: return null
            repeat(100) {
                if (kotlin.math.abs(f1) <= 1e-9 * maxOf(1.0, kotlin.math.abs(to))) return x1
                if (f1 == f0) return null
                val x2 = x1 - f1 * (x1 - x0) / (f1 - f0)
                if (x2.isNaN() || x2.isInfinite()) return null
                x0 = x1; f0 = f1; x1 = x2; f1 = f(x1) ?: return null
            }
            return null
        } finally { book.edit(index, by, was) } // the cell as it was: the found value goes in as one change of its own
    }
}

/** Formulas › Evaluate formula (Excel's): cell [k]'s formula worked out a step at a time ([SheetCalc.steps]), on the cells' values now. */
internal object SheetEvaluate {
    fun steps(book: Workbook, index: Int, k: Long): List<String> {
        val sheet = book.sheet(index); val name = book.tabs[index].name
        val f = sheet.cells[k]?.formula ?: return emptyList()
        fun at(s: String?) = if (s == null || s.equals(name, true)) sheet else book.tabs.indexOfFirst { it.name.equals(s, true) }.takeIf { it >= 0 }?.let { book.sheet(it) }
        val ctx = SheetCalc.Context({ s, r, c -> at(s)?.let { SheetRecalc.valueOf(it[r, c]) } ?: SheetCalc.Err("#REF!") }, format = book.format, names = book.names,
            formulaOf = { s, r, c -> at(s)?.get(r, c)?.formula })
        return SheetCalc.steps(f, ctx)
    }
}

/**
 * Part of a cell's words in a look of its own, typed (Excel's: letters chosen while typing in a cell, then Bold): the runs kept
 * beside the typed words — re-cut as the words change (kept letters keep their looks, typed ones take the look of the letter
 * before them, as Words), and a part's look changed.
 */
internal object SheetRich {
    private fun looks(runs: List<DocRun>) = runs.flatMap { r -> List(r.text.length) { r.copy(text = "") } }
    /** [looks] one a letter of [text], joined into runs where they are alike. */
    private fun joined(text: String, looks: List<DocRun>): List<DocRun> {
        val out = ArrayList<DocRun>()
        looks.forEachIndexed { i, l -> val last = out.lastOrNull()
            if (last != null && last.copy(text = "") == l) out[out.lastIndex] = last.copy(text = last.text + text[i]) else out += l.copy(text = text[i].toString()) }
        return out
    }
    /** [runs] (the words [old]) for the words now [new]. */
    fun edited(runs: List<DocRun>, old: String, new: String): List<DocRun> {
        if (runs.joinToString("") { it.text } != old) return listOf(DocRun(new))
        var pre = 0; while (pre < old.length && pre < new.length && old[pre] == new[pre]) pre++
        var suf = 0; while (suf < old.length - pre && suf < new.length - pre && old[old.length - 1 - suf] == new[new.length - 1 - suf]) suf++
        val was = looks(runs)
        val typed = was.getOrNull(pre - 1) ?: was.getOrNull(pre) ?: DocRun("")
        return joined(new, was.take(pre) + List(new.length - pre - suf) { typed } + was.takeLast(suf))
    }
    /** [runs] with letters [from] until [to] changed by [change]. */
    fun styled(runs: List<DocRun>, from: Int, to: Int, change: (DocRun) -> DocRun): List<DocRun> {
        val text = runs.joinToString("") { it.text }
        return joined(text, looks(runs).mapIndexed { i, l -> if (i in from until to) change(l) else l })
    }
    /** Whether every letter from [from] until [to] has [test]. */
    fun all(runs: List<DocRun>, from: Int, to: Int, test: (DocRun) -> Boolean) = looks(runs).subList(from.coerceAtLeast(0), to.coerceAtMost(looks(runs).size)).all(test)
    /** Whether the runs say anything (a letter bold, italic, underlined or struck): else the cell's own look is enough. */
    fun any(runs: List<DocRun>) = runs.any { it.bold || it.italic || it.underline || it.strike }
}
