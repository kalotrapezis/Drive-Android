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
 *     {"id":"k3f9a2","name":"Home","lat":37.9715,"lng":23.7257,"icon":"home","slot":1,"mode":"car","lists":["fav"]}
 *     slot 1–12 puts it on Home's Maps card (0: not there; a phone shows 1–6); mode is how its one tap there goes (car,
 *     foot, bike); lists are the ids of the lists it is in.
 *   Lists/<id>.json  one list each, {"id":"fav","name":"Favourites","emoji":"❤️"} (the user, 21 Oct: "make lists with names
 *     and emojes and add favs in there")
 *   greece-places.tsv the offline search list (tools/maps-places.py): towns and places of Search's kinds
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
    val lists: List<String> = emptyList(),
) {
    fun json(): String = JSONObject().put("id", id).put("name", name).put("lat", lat).put("lng", lng).put("icon", icon)
        .put("slot", slot).put("mode", mode).put("lists", org.json.JSONArray(lists)).toString(1) + "\n"

    companion object {
        /** Emblems by tab (the user, 21 Oct: "pharmases, hospitals, relatives travel locations, nature like waterfalls rivers beaches … in tabs with categories"). */
        val CATEGORIES = linkedMapOf(
            "General" to listOf("pin", "star", "heart", "accessible", "home", "work", "school"),
            "People" to listOf("family", "friend"),
            "Health" to listOf("pharmacy", "hospital", "doctor"),
            "Travel" to listOf("travel", "hotel", "food", "cafe", "shop", "fuel", "parking", "accessible"),
            "Nature" to listOf("beach", "waterfall", "river", "lake", "mountain", "forest", "camping"),
        )
        val ICONS = CATEGORIES.values.flatten().distinct() // the accessible mark is in General and beside Parking (the user, 21 Oct: "a disability mark … for my parking spots and accessible places")
        fun category(icon: String) = CATEGORIES.entries.firstOrNull { icon in it.value }?.key ?: "General"
        val MODES = listOf("car", "foot", "bike")

        /** A file's place; anything that is not one (a stray file, half a write) is null, never an error. */
        fun parse(text: String, file: File? = null): Place? = runCatching {
            val o = JSONObject(text)
            val lat = o.getDouble("lat"); val lng = o.getDouble("lng")
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
            Place(o.optString("id").ifEmpty { file?.nameWithoutExtension ?: return null }, o.optString("name").ifEmpty { "Place" }, lat, lng,
                o.optString("icon").takeIf { it in ICONS } ?: "pin", o.optInt("slot", 0).takeIf { it in 0..MapsFiles.SLOTS } ?: 0,
                o.optString("mode").takeIf { it in MODES } ?: "car", file,
                o.optJSONArray("lists")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() } }.orEmpty())
        }.getOrNull()
    }
}

internal object MapsFiles {
    const val FOLDER = "Maps"
    const val MAP = "greece.pmtiles"
    const val GRAPH = "greece-gh"
    const val PLACES = "Places"
    /** Home's Maps card: twelve places on a tablet, the first six on a phone (the user, 20 Oct: "slots for 6 locations"; 21 Oct, the tablet: "very big … we can double them"). */
    const val SLOTS = 12
    const val PHONE_SLOTS = 6
    const val LISTS = "Lists"
    /** Where the car is: one place with this id ("My car"), saved over by the next parking. */
    const val PARKING = "parking"

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

    /** What is in Tetra/Maps, for Settings: the map and the routing, each with its size and date. */
    fun installed(root: File): List<Pair<String, String>> {
        val day = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH)
        fun about(f: File, size: Long) = if (!f.exists()) "not here yet" else
            "${size / 1_000_000} MB, ${java.time.Instant.ofEpochMilli(f.lastModified()).atZone(java.time.ZoneId.systemDefault()).format(day)}"
        val g = graph(root)
        return listOf("Map of Greece" to about(map(root), map(root).length()),
            "Routing (car, walk, bike)" to about(File(g, "properties"), g.listFiles().orEmpty().sumOf { it.length() }),
            "Saved places" to "${places(root).size}")
    }

    fun lists(root: File): List<PlaceList> = File(root, LISTS).listFiles().orEmpty()
        .filter { it.isFile && !it.name.startsWith(".") && it.name.endsWith(".json", true) }
        .mapNotNull { f -> runCatching { PlaceList.parse(f.readText(), f) }.getOrNull() }
        .sortedBy { it.name.lowercase() }

    fun saveList(root: File, l: PlaceList): PlaceList {
        val dir = File(root, LISTS).apply { mkdirs() }
        val to = File(dir, "${l.id}.json")
        val part = File(dir, ".${to.name}.part")
        part.writeText(l.json())
        check(part.renameTo(to)) { "Could not save the list." }
        return l.copy(file = to)
    }

    /** The list goes; its places stay (they keep its id, which no longer names a list and is passed over). */
    fun deleteList(root: File, l: PlaceList) { File(File(root, LISTS), "${l.id}.json").delete() }

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

internal data class PlaceList(val id: String, val name: String, val emoji: String, val file: File? = null) {
    fun json(): String = JSONObject().put("id", id).put("name", name).put("emoji", emoji).toString(1) + "\n"
    companion object {
        fun parse(text: String, file: File? = null): PlaceList? = runCatching {
            val o = JSONObject(text)
            PlaceList(o.optString("id").ifEmpty { file?.nameWithoutExtension ?: return null }, o.optString("name").ifEmpty { "List" }, o.optString("emoji"), file)
        }.getOrNull()
    }
}

/** A place of the search list (greece-places.tsv): a town, or a restaurant, pharmacy, charging point… with what OSM knows of it. */
internal data class Poi(
    val kind: String, val lat: Double, val lng: Double, val name: String, val nameEn: String = "", val more: String = "",
    val hours: String = "", val phone: String = "", val web: String = "", val wheelchair: String = "", val note: String = "",
) {
    /** Accent-blind, Greek and English names together, for search. */
    val key = MapsSearch.fold("$name $nameEn")
    val title get() = name.ifEmpty { nameEn }.ifEmpty { MapsSearch.label(kind) }
    fun place() = Place("", title, lat, lng, MapsSearch.emblem(kind))
}

/**
 * Search, offline (the user, 21 Oct: "show nearby lcations in an area like Google Maps … restorants and pharmases and
 * supermarkets or petrol stations or charging stations add disability parking … button pills in the search page").
 */
internal object MapsSearch {
    const val FILE = "greece-places.tsv"
    /** EOPYY's own pharmacies (tools/maps-eopyy.py), kind "eopyy" (the user, 21 Oct: "add them as a list as well as a pill"). */
    const val EOPYY = "greece-eopyy.tsv"
    data class Kind(val key: String, val label: String, val emoji: String, val emblem: String)
    /** Search's pills, in this order. */
    val PILLS = listOf(
        Kind("restaurant", "Restaurants", "🍽️", "food"), Kind("cafe", "Cafés", "☕", "cafe"), Kind("supermarket", "Supermarkets", "🛒", "shop"),
        Kind("pharmacy", "Pharmacies", "💊", "pharmacy"), Kind("eopyy", "EOPYY pharmacies", "⚕️", "pharmacy"), Kind("fuel", "Fuel", "⛽", "fuel"), Kind("charging", "EV charging", "🔌", "fuel"),
        Kind("disabled_parking", "Disabled parking", "♿", "accessible"), Kind("parking", "Parking", "🅿️", "parking"),
        Kind("hospital", "Hospitals", "🏥", "hospital"), Kind("bakery", "Bakeries", "🥖", "food"), Kind("fast_food", "Fast food", "🍔", "food"),
        Kind("atm", "ATMs", "🏧", "pin"), Kind("hotel", "Hotels", "🛏️", "hotel"), Kind("beach", "Beaches", "🏖️", "beach"),
        Kind("museum", "Museums", "🏛️", "star"), Kind("toilets", "Toilets", "🚻", "pin"),
        Kind("kindergarten", "Kindergartens", "🧸", "school"), Kind("primary_school", "Primary schools", "🎒", "school"),
        Kind("secondary_school", "High schools", "🏫", "school"), Kind("school", "Other schools", "🏫", "school"), Kind("university", "Universities", "🎓", "school"),
    )
    private val SETTLEMENTS = mapOf("city" to "City", "town" to "Town", "village" to "Village", "suburb" to "Suburb", "neighbourhood" to "Neighbourhood",
        "hamlet" to "Hamlet", "island" to "Island", "islet" to "Islet", "locality" to "Place")
    fun settlement(kind: String) = kind in SETTLEMENTS
    private val ONE = mapOf("eopyy" to "EOPYY pharmacy", "university" to "University", "school" to "School", "pharmacy" to "Pharmacy",
        "charging" to "EV charging", "disabled_parking" to "Disabled parking", "fast_food" to "Fast food")
    fun label(kind: String) = ONE[kind] ?: PILLS.firstOrNull { it.key == kind }?.label?.removeSuffix("s")?.let { if (kind == "pharmacy") "Pharmacy" else if (kind == "charging") "EV charging" else it }
        ?: SETTLEMENTS[kind] ?: kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
    fun emoji(kind: String) = PILLS.firstOrNull { it.key == kind }?.emoji ?: if (settlement(kind)) "🏘️" else "📍"
    fun emblem(kind: String) = PILLS.firstOrNull { it.key == kind }?.emblem ?: "pin"

    /** Lower case, no accents, final sigma as sigma: "Ξάνθη" and "ξανθη" are the same. */
    fun fold(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace('ς', 'σ')

    fun parse(line: String): Poi? {
        val c = line.split('\t')
        if (c.size < 4) return null
        val lat = c[1].toDoubleOrNull() ?: return null; val lng = c[2].toDoubleOrNull() ?: return null
        return Poi(c[0], lat, lng, c[3], c.getOrElse(4) { "" }, c.getOrElse(5) { "" }, c.getOrElse(6) { "" }, c.getOrElse(7) { "" },
            c.getOrElse(8) { "" }, c.getOrElse(9) { "" }, c.getOrElse(10) { "" })
    }

    private var cache: Triple<String, Long, List<Poi>>? = null

    /** The search list in [root] with EOPYY's, read once (about 140 000 places) and kept while the files are the same. */
    @Synchronized fun load(root: File): List<Poi> {
        val files = listOf(File(root, FILE), File(root, EOPYY)).filter { it.isFile }
        if (files.isEmpty()) return emptyList()
        val stamp = files.sumOf { it.lastModified() }
        cache?.let { (path, at, all) -> if (path == root.path && at == stamp) return all }
        val all = files.flatMap { f -> f.bufferedReader().useLines { lines -> lines.mapNotNull(::parse).toList() } }
        cache = Triple(root.path, stamp, all)
        return all
    }

    /**
     * What [query] finds, of [kind] when one is given, nearest to [lat],[lng] (the map's middle) first; a name that starts
     * with the query before one that has it inside, and towns before shops when only words are given.
     */
    fun search(all: List<Poi>, query: String, lat: Double, lng: Double, kind: String? = null, limit: Int = 60): List<Poi> {
        val q = fold(query.trim())
        if (q.isEmpty() && kind == null) return emptyList()
        val words = q.split(' ').filter { it.isNotEmpty() }
        return all.asSequence()
            .filter { (kind == null || it.kind == kind) && words.all { w -> it.key.contains(w) } }
            .map { p -> p to metresBetween(lat, lng, p.lat, p.lng) }
            .sortedWith(compareBy({ (p, _) -> if (q.isEmpty() || p.key.startsWith(q) || p.key.contains(" $q")) 0 else 1 },
                { (p, _) -> if (kind == null && q.isNotEmpty() && !settlement(p.kind)) 1 else 0 }, { (_, d) -> d }))
            .take(limit).map { it.first }.toList()
    }

    /** The list's own entry for something seen on the map: the same name, or the nearest, within [within] metres. */
    fun near(all: List<Poi>, lat: Double, lng: Double, name: String, within: Double = 40.0): Poi? {
        val n = fold(name)
        return all.asSequence().filter { Math.abs(it.lat - lat) < 0.001 && Math.abs(it.lng - lng) < 0.0013 }
            .map { it to metresBetween(lat, lng, it.lat, it.lng) }.filter { it.second <= within }
            .sortedWith(compareBy({ (p, _) -> if (n.isNotEmpty() && fold(p.name) == n) 0 else 1 }, { it.second }))
            .firstOrNull()?.first
    }
}

/**
 * Addresses (greece-addresses.tsv, tools/maps-places.py): the street, number, postcode and town of a spot (the user, 21 Oct:
 * "To click on a spot show the address the number the postcode … Google maps has no adress for my hpuse this has").
 */
internal object MapsAddresses {
    const val FILE = "greece-addresses.tsv"
    private class All(val path: String, val at: Long, val lat: DoubleArray, val lng: DoubleArray, val rest: Array<String>)
    private var cache: All? = null

    @Synchronized private fun load(root: File): All? {
        val f = File(root, FILE)
        if (!f.isFile) return null
        cache?.let { if (it.path == f.path && it.at == f.lastModified()) return it }
        val lat = ArrayList<Double>(); val lng = ArrayList<Double>(); val rest = ArrayList<String>()
        f.bufferedReader().useLines { lines -> lines.forEach { line ->
            val a = line.indexOf('\t'); val b = line.indexOf('\t', a + 1)
            if (a > 0 && b > a) { lat += line.substring(0, a).toDoubleOrNull() ?: return@forEach; lng += line.substring(a + 1, b).toDoubleOrNull() ?: 0.0; rest += line.substring(b + 1) }
        } }
        return All(f.path, f.lastModified(), lat.toDoubleArray(), lng.toDoubleArray(), rest.toTypedArray()).also { cache = it }
    }

    /** "Οδός 12, 67100 Ξάνθη": the nearest address within 60 m; else the town and the nearest postcode (within 1.5 km). */
    fun describe(root: File, pois: List<Poi>, lat: Double, lng: Double): String? {
        val a = load(root)
        var best = -1; var bestD = 60.0; var post = ""; var postD = 1500.0
        if (a != null) for (i in a.lat.indices) {
            if (Math.abs(a.lat[i] - lat) > 0.014 || Math.abs(a.lng[i] - lng) > 0.018) continue
            val d = metresBetween(lat, lng, a.lat[i], a.lng[i])
            if (d < bestD) { bestD = d; best = i }
            if (d < postD) a.rest[i].split('\t').getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { post = it; postD = d }
        }
        val town = pois.asSequence().filter { it.kind in setOf("city", "town", "village", "suburb") && Math.abs(it.lat - lat) < 0.1 && Math.abs(it.lng - lng) < 0.13 }
            .minByOrNull { metresBetween(lat, lng, it.lat, it.lng) }?.title.orEmpty()
        val r = if (best >= 0) a!!.rest[best].split('\t') else null
        val street = r?.let { "${it[0]} ${it.getOrElse(1) { "" }}".trim() }.orEmpty()
        val code = r?.getOrNull(2)?.ifEmpty { null } ?: post
        val place = r?.getOrNull(3)?.ifEmpty { null } ?: town
        return listOf(street, "$code $place".trim()).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { null }
    }
}

/**
 * Google Maps' saved places, from Google Takeout (the user, 21 Oct: "be able to bring stuff I saved over"): each list's
 * CSV (Saved/<list>.csv: Title, Note, URL…) and the starred places (Maps (your places)/Saved Places.json). A list entry
 * has a name and a link; the link's !3d…!4d… or @lat,lng gives the spot when it is there, else the name is looked up in the
 * offline search list; what cannot be placed is reported to be found by hand.
 */
internal object MapsImport {
    data class Entry(val list: String, val name: String, val note: String, val lat: Double?, val lng: Double?)
    data class Result(val places: Int, val lists: Int, val missed: List<String>)

    private val D34 = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
    private val AT = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val SEARCH = Regex("""/maps/search/(-?\d+\.\d+),\+?(-?\d+\.\d+)""")

    fun coords(url: String): Pair<Double, Double>? = (D34.find(url) ?: AT.find(url) ?: SEARCH.find(url))
        ?.let { m -> m.groupValues[1].toDouble() to m.groupValues[2].toDouble() }?.takeIf { it.first in -90.0..90.0 && it.second in -180.0..180.0 && (it.first != 0.0 || it.second != 0.0) }

    /** One CSV line's cells (quotes and doubled quotes as Google writes them). */
    fun cells(line: String): List<String> {
        val out = ArrayList<String>(); val b = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                q && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { b.append('"'); i++ }
                c == '"' -> q = !q
                c == ',' && !q -> { out += b.toString(); b.clear() }
                else -> b.append(c)
            }
            i++
        }
        return out + b.toString()
    }

    fun csv(list: String, text: String): List<Entry> {
        val lines = text.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
        val head = lines.firstOrNull()?.let(::cells)?.map { it.trim().lowercase() } ?: return emptyList()
        val title = head.indexOf("title"); val note = head.indexOf("note"); val url = head.indexOf("url")
        if (title < 0) return emptyList()
        return lines.drop(1).map(::cells).mapNotNull { c ->
            val name = c.getOrNull(title)?.trim().orEmpty()
            val at = c.getOrNull(url)?.let(::coords)
            if (name.isEmpty() && at == null) null else Entry(list, name, c.getOrNull(note).orEmpty().trim(), at?.first, at?.second)
        }
    }

    /** Takeout's GeoJSON of starred or labelled places. */
    fun geojson(list: String, text: String): List<Entry> = runCatching {
        val fs = JSONObject(text).optJSONArray("features") ?: return emptyList()
        (0 until fs.length()).mapNotNull { i ->
            val f = fs.optJSONObject(i) ?: return@mapNotNull null
            val p = f.optJSONObject("properties") ?: JSONObject()
            val loc = p.optJSONObject("location")
            val c = f.optJSONObject("geometry")?.optJSONArray("coordinates")
            var lat = c?.optDouble(1); var lng = c?.optDouble(0)
            if (lat == null || lng == null || lat.isNaN() || lng.isNaN() || (lat == 0.0 && lng == 0.0)) {
                val at = (p.optString("google_maps_url").ifEmpty { p.optString("Google Maps URL") }).let(::coords); lat = at?.first; lng = at?.second
            }
            val name = loc?.optString("name").orEmpty().ifEmpty { p.optString("name").ifEmpty { p.optString("Title") } }.ifEmpty { loc?.optString("address").orEmpty() }
            if (name.isEmpty() && lat == null) null else Entry(list, name, loc?.optString("address").orEmpty(), lat, lng)
        }
    }.getOrDefault(emptyList())

    /** The entries of a Takeout .zip, or of one .csv / .json given by itself ([fileName] names its list). */
    fun read(fileName: String, bytes: ByteArray): List<Entry> {
        fun one(name: String, data: ByteArray): List<Entry> {
            val base = name.substringAfterLast('/').substringBeforeLast('.')
            val text = data.toString(Charsets.UTF_8)
            return when {
                name.endsWith(".csv", true) -> csv(base, text)
                name.endsWith(".json", true) -> geojson(if (base.equals("Saved Places", true)) "Starred places" else base, text)
                else -> emptyList()
            }
        }
        if (!fileName.endsWith(".zip", true)) return one(fileName, bytes)
        val out = ArrayList<Entry>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { z ->
            generateSequence { z.nextEntry }.forEach { e ->
                val n = e.name
                if (!e.isDirectory && (("/Saved/" in "/$n" && n.endsWith(".csv", true)) || ("Maps (your places)/" in n && n.endsWith(".json", true)))) out += one(n, z.readBytes())
            }
        }
        return out
    }

    fun emoji(list: String) = when (list.lowercase()) {
        "favourite places", "favorite places", "αγαπημένα μέρη" -> "❤️"
        "want to go", "θέλω να πάω" -> "🚩"
        "starred places", "με αστέρι" -> "⭐"
        "travel plans", "ταξιδιωτικά σχέδια" -> "✈️"
        else -> "📌"
    }

    /**
     * Into Tetra: a list for each Google list (one of the same name is reused), a place for each entry placed (a place of
     * the same name within 30 m is reused and joins the list), the rest named in [Result.missed].
     */
    fun apply(root: File, pois: List<Poi>, entries: List<Entry>): Result {
        var lists = MapsFiles.lists(root)
        val missed = ArrayList<String>(); var count = 0
        val byName = pois.filter { it.name.isNotEmpty() }.groupBy { MapsSearch.fold(it.name) }
        for ((listName, es) in entries.groupBy { it.list }) {
            val list = lists.firstOrNull { it.name.equals(listName, true) } ?: MapsFiles.saveList(root, PlaceList(MapsFiles.newId(), listName, emoji(listName))).also { lists = lists + it }
            for (e in es) {
                val at = if (e.lat != null && e.lng != null) e.lat to e.lng
                    else byName[MapsSearch.fold(e.name)]?.takeIf { it.size == 1 }?.first()?.let { it.lat to it.lng }
                if (at == null) { missed += "${e.name} ($listName)"; continue }
                val same = MapsFiles.places(root).firstOrNull { MapsSearch.fold(it.name) == MapsSearch.fold(e.name) && metresBetween(it.lat, it.lng, at.first, at.second) < 30 }
                MapsFiles.save(root, same?.copy(lists = (same.lists + list.id).distinct())
                    ?: Place(MapsFiles.newId(), e.name.ifEmpty { "Saved place" }, at.first, at.second, lists = listOf(list.id)))
                count++
            }
        }
        return Result(count, entries.map { it.list }.distinct().size, missed)
    }
}
