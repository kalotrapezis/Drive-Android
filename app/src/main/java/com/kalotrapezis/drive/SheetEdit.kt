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
        number(t, f)?.let { n ->
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
        if (affected.isEmpty()) return emptyMap()
        // worked out in the order they need each other (a cell's formula first when another reads it)
        val done = HashMap<Long, Any>()
        val working = HashSet<Long>()
        val unknown = HashSet<Long>() // a function the engine does not know: the cell is left as the file has it
        lateinit var ctx: SheetCalc.Context
        fun result(k: Long): Any? {
            done[k]?.let { return it }
            if (!working.add(k)) return SheetCalc.Err("#REF!") // a circle: Excel warns; here it is an error
            val v = try { SheetCalc.eval(sheet.cells[k]!!.formula!!, ctx) } catch (_: SheetCalc.Unknown) { unknown += k; valueOf(sheet.cells[k]) ?: "" }
            working -= k; done[k] = v
            return v
        }
        ctx = SheetCalc.Context(SheetCalc.Cells { s, r, c ->
            if (s == null || s.equals(name, true)) { val k = Sheet.key(r, c); if (k in affected) result(k) else valueOf(sheet.cells[k]) }
            else book.tabs.indexOfFirst { it.name.equals(s, true) }.let { i -> if (i < 0) SheetCalc.Err("#REF!") else valueOf(book.sheet(i)[r, c]) }
        }, format = book.format, names = book.names)
        val out = LinkedHashMap<Long, SheetCell>()
        for (k in affected) {
            val old = sheet.cells[k] ?: continue
            val v = result(k)
            if (k in unknown) continue
            val code = old.format ?: (sheet.look(old.style, book.styles)?.format ?: "General")
            out[k] = when (v) {
                is Double -> book.format.format(v, code).let { old.copy(text = it.text, kind = SheetKind.Number, value = v, color = it.color) }
                is Boolean -> old.copy(text = if (v) "TRUE" else "FALSE", kind = SheetKind.Bool, value = if (v) 1.0 else 0.0, color = null)
                is SheetCalc.Err -> old.copy(text = v.code, kind = SheetKind.Error, value = null, color = null)
                null -> old.copy(text = "", kind = SheetKind.Empty, value = null, color = null)
                else -> old.copy(text = v.toString(), kind = if (v.toString().isEmpty()) SheetKind.Empty else SheetKind.Text, value = null, color = null)
            }
        }
        return out
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

    /** Sets [changes] on sheet [index], works out what counts on them, and keeps it as one step. */
    fun apply(book: Workbook, index: Int, changes: Map<Long, SheetCell>) {
        val sheet = book.sheet(index)
        val before = LinkedHashMap<Long, SheetCell>()
        fun was(k: Long) = sheet.cells[k] ?: SheetCell("", SheetKind.Empty)
        changes.forEach { (k, c) -> before[k] = was(k); book.edit(index, k, c) }
        val worked = SheetRecalc.run(book, index, sheet, changes.keys)
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
}
