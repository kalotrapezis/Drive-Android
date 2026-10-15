package com.kalotrapezis.drive

import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The viewer's own ways of looking at a sheet (asked 30 Sep): rows filtered by a column's values, sorted by a column,
 * and columns pinned side by side at the start — none of them changes the file. A [SheetLayout] is the order the rows
 * and columns are drawn in.
 */
internal data class SheetSort(val col: Int, val ascending: Boolean)

internal class SheetLayout(
    /** The sheet's rows and columns in the order drawn (rows a filter leaves out are not in it). */
    val rows: IntArray, val cols: IntArray,
    val frozenRows: Int, val frozenCols: Int,
) {
    private val rowAt = reverse(rows); private val colAt = reverse(cols)
    /** Where sheet row [r] is drawn; -1: not drawn. */
    fun row(r: Int) = if (r in rowAt.indices) rowAt[r] else -1
    fun col(c: Int) = if (c in colAt.indices) colAt[c] else -1

    companion object {
        private fun reverse(a: IntArray) = IntArray((a.maxOrNull() ?: -1) + 1) { -1 }.also { r -> a.forEachIndexed { i, v -> r[v] = i } }

        /** Empty cells past the used ones, as Excel's sheet goes on. */
        const val MORE_ROWS = 60
        const val MORE_COLS = 12

        fun of(sheet: Sheet, pins: List<Int> = emptyList(), filters: Map<Int, Set<String>> = emptyMap(), sort: SheetSort? = null): SheetLayout {
            // As far as the cells and the drawings over them go.
            val drawnCols = sheet.drawings.maxOfOrNull { max(it.col, it.toCol) + 1 + (it.width / sheet.defaultColWidth).toInt() } ?: 0
            val drawnRows = sheet.drawings.maxOfOrNull { max(it.row, it.toRow) + 1 + (it.height / sheet.defaultRowHeight).toInt() } ?: 0
            val totalCols = maxOf(sheet.cols, sheet.frozenCols, drawnCols) + MORE_COLS
            val totalRows = maxOf(sheet.rows, sheet.frozenRows, drawnRows) + MORE_ROWS
            // Columns: the pinned ones in the order pinned, then the file's frozen ones, then the rest.
            val pinned = pins.filter { it in 0 until totalCols }.distinct()
            val frozen = (0 until sheet.frozenCols).filter { it !in pinned }
            val cols = (pinned + frozen + (sheet.frozenCols until totalCols).filter { it !in pinned }).toIntArray()
            val frozenCols = pinned.size + frozen.size
            if (filters.isEmpty() && sort == null) return SheetLayout(IntArray(totalRows) { it }, cols, sheet.frozenRows, frozenCols)
            // Rows: the header and what is above it stay; the table's rows below it are filtered, then sorted.
            val header = header(sheet)
            val end = sheet.autoFilter?.r2?.plus(1)?.coerceAtMost(totalRows) ?: sheet.rows
            val data = (header + 1 until end).filter { r -> filters.all { (c, keep) -> value(sheet[r, c]) in keep } }
            val sorted = sort?.let { s -> data.sortedWith(order(sheet, s)) } ?: data
            val rows = ((0..header).toList() + sorted + (end until totalRows)).toIntArray()
            return SheetLayout(rows, cols, sheet.frozenRows, frozenCols)
        }

        /**
         * The table's header row: the file's AutoFilter's first row, else the last frozen row, else the first of the
         * top 50 rows that is nearly as full as the fullest (a title above a table has one cell, its header many).
         */
        fun header(sheet: Sheet): Int {
            sheet.autoFilter?.let { return it.r1 }
            if (sheet.frozenRows > 0) return sheet.frozenRows - 1
            val counts = IntArray(min(50, sheet.rows))
            sheet.cells.forEach { k, cell -> val r = Sheet.row(k); if (r < counts.size && cell.text.isNotBlank()) counts[r]++ }
            val most = counts.maxOrNull() ?: return 0
            return counts.indexOfFirst { it * 10 >= most * 6 }.coerceAtLeast(0)
        }

        /** A cell as a filter lists it: what it shows, trimmed; "" for an empty one. */
        fun value(cell: SheetCell?) = cell?.text?.trim().orEmpty()

        /** Numbers by value, words as the phone's language orders them (Greek and English), empty cells last. */
        fun order(sheet: Sheet, s: SheetSort): Comparator<Int> {
            val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
            return Comparator { a, b ->
                val x = sheet[a, s.col]; val y = sheet[b, s.col]
                val ex = value(x).isEmpty(); val ey = value(y).isEmpty()
                if (ex || ey) return@Comparator if (ex && ey) 0 else if (ex) 1 else -1 // empty last, either way
                val nx = x?.value?.takeIf { x.kind == SheetKind.Number }; val ny = y?.value?.takeIf { y.kind == SheetKind.Number }
                val c = when {
                    nx != null && ny != null -> nx.compareTo(ny)
                    nx != null -> -1 // numbers before words, as Excel
                    ny != null -> 1
                    else -> collator.compare(value(x), value(y))
                }
                if (s.ascending) c else -c
            }
        }

        /** A column's values under the header for its filter: each with how many rows have it, in the sort's order. */
        fun values(sheet: Sheet, col: Int): List<Pair<String, Int>> {
            val header = header(sheet)
            val end = sheet.autoFilter?.r2?.plus(1) ?: sheet.rows
            val counts = LinkedHashMap<String, Int>()
            val first = HashMap<String, Int>()
            for (r in header + 1 until end) { val v = value(sheet[r, col]); counts[v] = (counts[v] ?: 0) + 1; first.putIfAbsent(v, r) }
            val byRow = order(sheet, SheetSort(col, true))
            return counts.entries.sortedWith { a, b -> byRow.compare(first.getValue(a.key), first.getValue(b.key)) }.map { it.key to it.value }
        }
    }
}

/**
 * Search across a sheet's cells (asked 30 Sep: "typos and accents forgiven, Greek and English"): letters compared
 * without accents or case (ς as σ), every word of the search must be in the cell, and a word of four letters or more
 * may be one letter off (seven or more: two) — missing, extra, wrong or swapped.
 */
internal object SheetSearch {
    private val MARKS = Regex("\\p{Mn}+")
    private val WORD = Regex("[\\p{L}\\p{N}]+")

    fun norm(s: String): String = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(MARKS, "").replace('ς', 'σ')

    fun words(query: String): List<String> = WORD.findAll(norm(query)).map { it.value }.toList()

    /** Whether a cell's (normalised) text has every one of the (normalised) [words]. */
    fun matches(text: String, words: List<String>): Boolean {
        if (words.isEmpty()) return false
        var cellWords: List<String>? = null
        return words.all { w ->
            if (text.contains(w)) return@all true
            val allowed = if (w.length >= 7) 2 else if (w.length >= 4) 1 else 0
            if (allowed == 0) return@all false
            (cellWords ?: WORD.findAll(text).map { it.value }.toList().also { cellWords = it }).any { t ->
                // the whole word, or its start (a word being typed: "παπαδοπ" finds "Παπαδόπουλος")
                (abs(t.length - w.length) <= allowed && distance(w, t) <= allowed) || (t.length > w.length && distance(w, t.substring(0, w.length)) <= allowed)
            }
        }
    }

    /** The cells of [sheet] that match [query], row by row. */
    fun find(sheet: Sheet, query: String): List<Long> {
        val words = words(query)
        if (words.isEmpty()) return emptyList()
        val out = ArrayList<Long>()
        val general = SheetFormat()
        sheet.cells.forEach { k, cell ->
            if (cell.text.isEmpty()) return@forEach
            // A number is found by its digits as well as as it is shown ("1234" finds "1.234,50").
            val text = norm(cell.text) + (cell.value?.takeIf { cell.kind == SheetKind.Number }?.let { " " + general.general(it) } ?: "")
            if (matches(text, words)) out += k
        }
        out.sort() // the key is the row, then the column
        return out
    }

    /** Letters to change, add, drop or swap to make [a] into [b] (optimal string alignment). */
    fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = min(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }
}
