package com.kalotrapezis.drive

import org.json.JSONObject
import java.io.File

/*
 * Tetra's Maps (Plan.md § Maps). Everything is a file in Tetra/Maps (~/Tetra/Files/Maps on the computer, hidden in
 * Files like the Calendar and the Timer, synced): the computer makes them (tools/maps-greece.sh) and sync carries them,
 * so the desktop can use the same ones later.
 *
 *   greece.pmtiles   the offline map (Protomaps' basemap cut to Greece), drawn by MapLibre
 *   greece-gh/       the offline routing graph for car, foot and bike (GraphHopper 10.2, the app's own version)
 *   Places/<id>.json one saved place each:
 *     {"id":"k3f9a2","name":"Home","lat":37.9715,"lng":23.7257,"icon":"home","slot":1,"mode":"car"}
 *     slot 1–6 puts it on Home's Maps card (0: not there); mode is how its one tap there goes (car, foot, bike).
 */
internal data class Place(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val icon: String = "pin",
    val slot: Int = 0,
    val mode: String = "car",
    val file: File? = null,
) {
    fun json(): String = JSONObject().put("id", id).put("name", name).put("lat", lat).put("lng", lng).put("icon", icon)
        .put("slot", slot).put("mode", mode).toString(1) + "\n"

    companion object {
        /** Emblems by tab (the user, 21 Oct: "pharmases, hospitals, relatives travel locations, nature like waterfalls rivers beaches … in tabs with categories"). */
        val CATEGORIES = linkedMapOf(
            "General" to listOf("pin", "star", "heart", "home", "work", "school"),
            "People" to listOf("family", "friend"),
            "Health" to listOf("pharmacy", "hospital", "doctor"),
            "Travel" to listOf("travel", "hotel", "food", "cafe", "shop", "fuel", "parking"),
            "Nature" to listOf("beach", "waterfall", "river", "lake", "mountain", "forest", "camping"),
        )
        val ICONS = CATEGORIES.values.flatten()
        fun category(icon: String) = CATEGORIES.entries.firstOrNull { icon in it.value }?.key ?: "General"
        val MODES = listOf("car", "foot", "bike")

        /** A file's place; anything that is not one (a stray file, half a write) is null, never an error. */
        fun parse(text: String, file: File? = null): Place? = runCatching {
            val o = JSONObject(text)
            val lat = o.getDouble("lat"); val lng = o.getDouble("lng")
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
            Place(o.optString("id").ifEmpty { file?.nameWithoutExtension ?: return null }, o.optString("name").ifEmpty { "Place" }, lat, lng,
                o.optString("icon").takeIf { it in ICONS } ?: "pin", o.optInt("slot", 0).takeIf { it in 0..MapsFiles.SLOTS } ?: 0,
                o.optString("mode").takeIf { it in MODES } ?: "car", file)
        }.getOrNull()
    }
}

internal object MapsFiles {
    const val FOLDER = "Maps"
    const val MAP = "greece.pmtiles"
    const val GRAPH = "greece-gh"
    const val PLACES = "Places"
    /** Home's Maps card holds six places (the user, 20 Oct: "slots for 6 locations"). */
    const val SLOTS = 6

    fun root(tetra: File) = File(tetra, FOLDER)
    fun map(root: File) = File(root, MAP)
    fun graph(root: File) = File(root, GRAPH)

    fun places(root: File): List<Place> = File(root, PLACES).listFiles().orEmpty()
        .filter { it.isFile && !it.name.startsWith(".") && it.name.endsWith(".json", true) }
        .mapNotNull { f -> runCatching { Place.parse(f.readText(), f) }.getOrNull() }
        .sortedBy { it.name.lowercase() }

    /** Home's six: slot n is the place that has it (two with one slot, after a sync: the first by name). */
    fun slots(places: List<Place>): List<Place?> = (1..SLOTS).map { n -> places.firstOrNull { it.slot == n } }

    /** Written beside, then renamed over, so a sync never reads half a place; taking a slot frees it on any other place. */
    fun save(root: File, p: Place): Place {
        val dir = File(root, PLACES).apply { mkdirs() }
        if (p.slot > 0) places(root).filter { it.slot == p.slot && it.id != p.id }.forEach { write(dir, it.copy(slot = 0)) }
        return write(dir, p)
    }

    private fun write(dir: File, p: Place): Place {
        val to = File(dir, "${p.id}.json")
        val part = File(dir, ".${to.name}.part")
        part.writeText(p.json())
        check(part.renameTo(to)) { "Could not save the place." }
        return p.copy(file = to)
    }

    fun delete(root: File, p: Place) { File(File(root, PLACES), "${p.id}.json").delete() }

    fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(6)
}

/** "350 m", "12 km", "1.4 km": a distance for a glance. */
internal fun metresText(m: Double): String = when {
    m < 950 -> "${(Math.round(m / 10.0) * 10).coerceAtLeast(0)} m"
    m < 9950 -> String.format(java.util.Locale.ENGLISH, "%.1f km", m / 1000)
    else -> "${Math.round(m / 1000)} km"
}

/** Where on a route a fix is: how far off it, how far along, and the next turn ahead. */
internal data class Progress(val off: Double, val along: Double, val left: Double, val next: Int, val toNext: Double)

/** A route's running total of metres at each point, for [progress]. */
internal fun cumulative(r: Route): DoubleArray {
    val c = DoubleArray(r.lats.size)
    for (i in 1 until c.size) c[i] = c[i - 1] + metresBetween(r.lats[i - 1], r.lngs[i - 1], r.lats[i], r.lngs[i])
    return c
}

/**
 * The fix [lat],[lng] put on route [r] (its [cum]ulative metres): the nearest point of its line, searched from [from] on
 * first (so a route that comes back past itself does not jump back), the whole line when that is far.
 */
internal fun progress(r: Route, cum: DoubleArray, lat: Double, lng: Double, from: Int = 0): Pair<Progress, Int> {
    fun best(range: IntRange): Triple<Double, Int, Double> {
        var bd = Double.MAX_VALUE; var bi = 0; var bt = 0.0
        val k = Math.cos(Math.toRadians(lat)) // metres per degree east, relative to north
        for (i in range) {
            val ax = (r.lngs[i] - lng) * k; val ay = r.lats[i] - lat
            val bx = (r.lngs[i + 1] - lng) * k; val by = r.lats[i + 1] - lat
            val dx = bx - ax; val dy = by - ay
            val len = dx * dx + dy * dy
            val t = if (len == 0.0) 0.0 else (-(ax * dx + ay * dy) / len).coerceIn(0.0, 1.0)
            val px = ax + t * dx; val py = ay + t * dy
            val d = Math.sqrt(px * px + py * py) * 111_195.0
            if (d < bd) { bd = d; bi = i; bt = t }
        }
        return Triple(bd, bi, bt)
    }
    val last = r.lats.size - 2
    if (last < 0) return Progress(metresBetween(lat, lng, r.lats[0], r.lngs[0]), 0.0, 0.0, r.steps.lastIndex, 0.0) to 0
    var (d, i, t) = best(from.coerceIn(0, last)..last)
    if (d > 60) best(0..last).let { (d2, i2, t2) -> if (d2 < d) { d = d2; i = i2; t = t2 } }
    val along = cum[i] + t * (cum[i + 1] - cum[i])
    val next = r.steps.indexOfFirst { it.at > i }.let { if (it < 0) r.steps.lastIndex else it }
    val toNext = (cum[r.steps[next].at.coerceAtMost(cum.lastIndex)] - along).coerceAtLeast(0.0)
    return Progress(d, along, (cum.last() - along).coerceAtLeast(0.0), next, toNext) to i
}

internal fun metresBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
    val a = Math.sin((p2 - p1) / 2).let { it * it } + Math.cos(p1) * Math.cos(p2) * Math.sin(Math.toRadians(lng2 - lng1) / 2).let { it * it }
    return 2 * 6_371_000 * Math.asin(Math.sqrt(a))
}
