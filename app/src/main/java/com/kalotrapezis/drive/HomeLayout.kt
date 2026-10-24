package com.kalotrapezis.drive

import android.content.Context

/**
 * One part of Home: large (its group, as before), small (just its icon, like a launcher's) or hidden, at a cell of Home's
 * grid (col, row; -1 until it has one).
 */
internal data class HomeItem(val id: String, val small: Boolean = false, val hidden: Boolean = false, val col: Int = -1, val row: Int = -1)

/**
 * Home's layout on this device (the user, 24 Oct: "like a launcher … a grid that expands, to drag and drop where I want
 * them"): a grid of cells, 8 across on a tablet and 4 on a phone, growing down as far as the parts go. A small part takes
 * one cell; a group half a tablet's width (a phone's whole width) and two rows; Local Sync and Settings one row. A part
 * dropped where others are moves them on to the next free cells. Hidden is only hidden (asked 24 Oct): reminders,
 * timers and sync go on. Kept in this device's preferences, one layout for each grid width, not synced; Settings can
 * never be hidden, or there would be no way back.
 */
internal object HomeLayout {
    const val SETTINGS = "settings"
    private val CARDS = setOf("sync", SETTINGS) // one row high
    // today's order: a tablet pairs Sync with Settings at the top; a phone has Maps before Contacts and Settings last
    val WIDE = listOf("sync", SETTINGS, "gallery", "files", "notes", "scanner", "office", "text", "calendar", "timer", "contacts", "maps")
    val NARROW = listOf("sync", "gallery", "files", "notes", "scanner", "office", "text", "calendar", "timer", "maps", "contacts", SETTINGS)

    val NAMES = mapOf("sync" to "Local Sync", SETTINGS to "Settings", "gallery" to "Photos", "files" to "Files", "notes" to "Notes",
        "scanner" to "Scanner & Codes", "office" to "Office", "text" to "Text editor", "calendar" to "Calendar", "timer" to "Timer",
        "contacts" to "Contacts", "maps" to "Maps")

    fun columns(wide: Boolean) = if (wide) 8 else 4
    private fun prefs(c: Context) = c.getSharedPreferences("home", Context.MODE_PRIVATE)
    fun load(c: Context, wide: Boolean) = columns(wide).let { cols -> place(parse(prefs(c).getString("grid$cols", null), if (wide) WIDE else NARROW), cols) }
    fun save(c: Context, wide: Boolean, items: List<HomeItem>) = prefs(c).edit().putString("grid${columns(wide)}", format(items)).apply()
    fun reset(c: Context, wide: Boolean) = prefs(c).edit().remove("grid${columns(wide)}").apply()

    /** "id:flags:col:row" (flags s small, h hidden), comma-separated; unknown ids dropped, a new part added unplaced. */
    fun parse(saved: String?, defaults: List<String>): List<HomeItem> {
        val items = saved.orEmpty().split(',').filter { it.isNotBlank() }.map { part ->
            val p = part.split(':')
            val id = p[0]; val flags = p.getOrElse(1) { "" }
            HomeItem(id, small = 's' in flags, hidden = 'h' in flags && id != SETTINGS, col = p.getOrNull(2)?.toIntOrNull() ?: -1, row = p.getOrNull(3)?.toIntOrNull() ?: -1)
        }.filter { it.id in defaults }.distinctBy { it.id }
        return items + defaults.filter { id -> items.none { it.id == id } }.map { HomeItem(it) }
    }

    fun format(items: List<HomeItem>) = items.joinToString(",") { "${it.id}:${if (it.small) "s" else ""}${if (it.hidden) "h" else ""}:${it.col}:${it.row}" }

    /** A part's width and height in cells. */
    fun size(item: HomeItem, cols: Int) = when {
        item.small -> (if (item.id == "scanner") 2 else 1) to 1 // Scanner & Codes: two apps, two icons
        else -> (if (cols >= 8) cols / 2 else cols) to (if (item.id in CARDS) 1 else 2)
    }

    /** How many rows the shown parts take. */
    fun height(items: List<HomeItem>, cols: Int) = items.filter { !it.hidden }.maxOfOrNull { it.row + size(it, cols).second } ?: 0

    /** Every shown part at a cell of its own: those that fit stay; the rest go to the first free cells, in the list's order. */
    fun place(items: List<HomeItem>, cols: Int): List<HomeItem> {
        val grid = Grid(cols)
        val kept = items.map { if (!it.hidden && it.col >= 0 && it.row >= 0 && grid.fits(it, it.col, it.row)) it.also { i -> grid.take(i, i.col, i.row) } else it }
        return kept.map { if (it.hidden || grid.has(it)) it else grid.firstFree(it, 0, 0).let { (c, r) -> it.copy(col = c, row = r).also { i -> grid.take(i, c, r) } } }
    }

    /** `id` dropped at (col, row); a part in the way moves on to the next free cells after where it was. */
    fun drop(items: List<HomeItem>, id: String, col: Int, row: Int, cols: Int): List<HomeItem> {
        val moved = items.first { it.id == id }
        val dropped = moved.copy(col = col.coerceIn(0, cols - size(moved, cols).first), row = row.coerceAtLeast(0))
        val grid = Grid(cols).apply { take(dropped, dropped.col, dropped.row) }
        val placed = mutableMapOf(id to dropped)
        items.filter { it.id != id && !it.hidden }.sortedWith(compareBy({ it.row }, { it.col })).forEach { other ->
            val (c, r) = if (other.col >= 0 && grid.fits(other, other.col, other.row)) other.col to other.row else grid.firstFree(other, other.col.coerceAtLeast(0), other.row.coerceAtLeast(0))
            placed[other.id] = other.copy(col = c, row = r).also { grid.take(it, c, r) }
        }
        return items.map { placed[it.id] ?: it }
    }

    /** One part changed (resized, shown) where it is, the others moving out of its way. */
    fun change(items: List<HomeItem>, item: HomeItem, cols: Int): List<HomeItem> {
        val next = items.map { if (it.id == item.id) item else it }
        return if (item.hidden) next else if (item.col < 0) place(next, cols) else drop(next, item.id, item.col, item.row, cols)
    }

    private class Grid(val cols: Int) {
        private val taken = mutableSetOf<Pair<Int, Int>>()
        private val ids = mutableSetOf<String>()
        fun fits(item: HomeItem, col: Int, row: Int): Boolean {
            val (w, h) = size(item, cols)
            return col + w <= cols && (0 until w).all { dx -> (0 until h).all { dy -> (col + dx to row + dy) !in taken } }
        }
        fun take(item: HomeItem, col: Int, row: Int) {
            val (w, h) = size(item, cols)
            for (dx in 0 until w) for (dy in 0 until h) taken += col + dx to row + dy
            ids += item.id
        }
        fun has(item: HomeItem) = item.id in ids
        /** The first cell, in reading order from (col, row), where the part fits; the grid grows down, so there always is one. */
        fun firstFree(item: HomeItem, col: Int, row: Int): Pair<Int, Int> {
            var c = col; var r = row
            while (!fits(item, c, r)) { if (++c >= cols) { c = 0; r++ } }
            return c to r
        }
    }
}
