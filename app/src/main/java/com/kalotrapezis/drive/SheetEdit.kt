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
 * on — numbers as their series (1, 2 → 3, 4), a date day by day, "Week 1" → "Week 2", formulas with their relative
 * references moved ($ ones kept), anything else copied over and over.
 */
internal object SheetFill {
    private val REF = Regex("""(?<![\p{L}\p{N}_.$'])(?:('(?:[^']|'')+'|[\p{L}_][\p{L}\p{N}_.]*)!)?(\$?[A-Za-z]{1,3}\$?\d+(?::\$?[A-Za-z]{1,3}\$?\d+)?)(?![\p{L}\p{N}_(!])""")
    private val CELL = Regex("""(\$?)([A-Za-z]{1,3})(\$?)(\d+)""")
    private val NUMBERED = Regex("""^(.*?)(\d+)$""")

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
