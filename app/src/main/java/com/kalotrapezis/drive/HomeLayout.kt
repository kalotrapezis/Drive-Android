package com.kalotrapezis.drive

import android.content.Context

/** One part of Home: shown large (its group, as before), small (just its icon, like a launcher's), or hidden. */
internal data class HomeItem(val id: String, val small: Boolean = false, val hidden: Boolean = false)

/**
 * Home's layout on this device (the user, 24 Oct: "like a launcher: rearrange the icons, hide/disable, resize"): the parts
 * in their order, each large, small or hidden. Hidden is only hidden (asked 24 Oct): reminders, timers and sync go on.
 * Kept in this device's preferences, not synced; Settings can never be hidden, or there would be no way back.
 */
internal object HomeLayout {
    const val SETTINGS = "settings"
    // today's order: a tablet pairs Sync with Settings at the top; a phone has Maps before Contacts and Settings last
    val WIDE = listOf("sync", SETTINGS, "gallery", "files", "notes", "scanner", "office", "text", "calendar", "timer", "contacts", "maps")
    val NARROW = listOf("sync", "gallery", "files", "notes", "scanner", "office", "text", "calendar", "timer", "maps", "contacts", SETTINGS)

    val NAMES = mapOf("sync" to "Local Sync", SETTINGS to "Settings", "gallery" to "Photos", "files" to "Files", "notes" to "Notes",
        "scanner" to "Scanner & Codes", "office" to "Office", "text" to "Text editor", "calendar" to "Calendar", "timer" to "Timer",
        "contacts" to "Contacts", "maps" to "Maps")

    private fun prefs(c: Context) = c.getSharedPreferences("home", Context.MODE_PRIVATE)
    fun load(c: Context, wide: Boolean) = parse(prefs(c).getString("layout", null), if (wide) WIDE else NARROW)
    fun save(c: Context, items: List<HomeItem>) = prefs(c).edit().putString("layout", format(items)).apply()
    fun reset(c: Context) = prefs(c).edit().remove("layout").apply()

    /** "id", "id:s" (small), "id:h" (hidden), "id:sh", comma-separated; unknown ids dropped, a new part added after the one the defaults put it after. */
    fun parse(saved: String?, defaults: List<String>): List<HomeItem> {
        val items = saved.orEmpty().split(',').filter { it.isNotBlank() }.map { part ->
            val id = part.substringBefore(':'); val flags = part.substringAfter(':', "")
            HomeItem(id, small = 's' in flags, hidden = 'h' in flags && id != SETTINGS)
        }.filter { it.id in defaults }.distinctBy { it.id }.toMutableList()
        defaults.forEachIndexed { i, id ->
            if (items.none { it.id == id }) {
                val after = defaults.take(i).lastOrNull { d -> items.any { it.id == d } }
                items.add(if (after == null) 0 else items.indexOfFirst { it.id == after } + 1, HomeItem(id))
            }
        }
        return items
    }

    fun format(items: List<HomeItem>) = items.joinToString(",") { it.id + (if (it.small || it.hidden) ":" + (if (it.small) "s" else "") + (if (it.hidden) "h" else "") else "") }

    fun move(items: List<HomeItem>, from: Int, to: Int) = items.toMutableList().apply { add(to, removeAt(from)) }

    /** Home's rows: the shown parts in order, large ones `perRow` at a time, a run of small ones together in one (wrapping) row. */
    fun rows(items: List<HomeItem>, perRow: Int): List<List<HomeItem>> {
        val rows = mutableListOf<List<HomeItem>>()
        var run = mutableListOf<HomeItem>()
        for (item in items.filter { !it.hidden }) {
            if (run.isNotEmpty() && (run[0].small != item.small || (!item.small && run.size == perRow))) { rows += run; run = mutableListOf() }
            run += item
        }
        if (run.isNotEmpty()) rows += run
        return rows
    }
}
