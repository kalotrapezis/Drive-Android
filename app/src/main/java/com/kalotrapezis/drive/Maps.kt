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
 *     foot, bike); lists are the ids of the lists it is in; label, when there, is its name on the Home card (the place
 *     itself, "Πύργος", where the map shows the whole name, "Δημοτικό Σχολείο Πύργου"; the user, 21 Oct).
 *   added-places.tsv places put into the map's own data by hand, in the search list's form (the user's schools, 21 Oct)
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
    val label: String = "",
) {
    fun json(): String = JSONObject().put("id", id).put("name", name).put("lat", lat).put("lng", lng).put("icon", icon)
        .put("slot", slot).put("mode", mode).put("lists", org.json.JSONArray(lists)).apply { if (label.isNotBlank()) put("label", label) }.toString(1) + "\n"
    /** Its name on the Home card. */
    val short get() = label.ifBlank { name }

    companion object {
        /** Emblems by tab (the user, 21 Oct: "pharmases, hospitals, relatives travel locations, nature like waterfalls rivers beaches … in tabs with categories"). */
        val CATEGORIES = linkedMapOf(
            "General" to listOf("pin", "star", "heart", "accessible", "home", "work", "school"),
            "People" to listOf("family", "friend"),
            "Health" to listOf("pharmacy", "hospital", "doctor"),
            "Travel" to listOf("travel", "hotel", "food", "tavern", "cafe", "shop", "fuel", "parking", "accessible"),
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
                o.optJSONArray("lists")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() } }.orEmpty(),
                o.optString("label"))
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
    const val PHONE_SLOTS = 8 // half-size tiles on a phone (the user, 21 Oct: "half the size, so we can have 8")
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

/**
 * Which of a turn's spoken warnings is due ([step] its index, [toNext] metres ahead, [speed] m/s): "e" early, 2 km ahead
 * on a fast road; "f" far, about 20 s ahead (500 m at least); "n" at it, about 5 s ahead; or null. By car the warnings
 * follow the speed (the user, 21 Oct, on the Egnatia: "σε 3χμ πάρτη την έξοδο στα δεξιά" — 500 m at 120 km/h is 15 s).
 */
internal fun dueWarning(step: Int, toNext: Double, speed: Double, car: Boolean, said: Set<String>): String? {
    val near = if (car) maxOf(80.0, speed * 5) else 20.0
    val far = if (car) maxOf(500.0, speed * 20) else 150.0
    return when {
        toNext <= near -> "n".takeIf { "n$step" !in said }
        toNext <= far -> "f".takeIf { "f$step" !in said && toNext > near * 1.5 }
        car && speed > 22 && toNext <= 2200 && toNext > far * 1.5 -> "e".takeIf { "e$step" !in said && "f$step" !in said }
        else -> null
    }
}

/** A warning's distance as said: whole or half kilometres from 1 km, else metres to 50 (to 10 on foot). */
internal fun spokenDistance(m: Double, car: Boolean): Pair<Double, Boolean> =
    if (m >= 950) (Math.round(m / 500.0) * 0.5) to true else (Math.round(m / (if (car) 50.0 else 10.0)) * (if (car) 50.0 else 10.0)) to false

/**
 * The toll stations on route [r] (its [cum]ulative metres) and how far along it each is (the user, 21 Oct: "Do we know where
 * the talls are to show them on the map as dots on active routs in a menu show the talls"): OSM's toll booths within 40 m
 * of its line.
 */
internal fun tollsOn(r: Route, cum: DoubleArray, pois: List<Poi>): List<Pair<Poi, Double>> {
    if (r.lats.isEmpty()) return emptyList()
    val south = r.lats.min() - 0.01; val north = r.lats.max() + 0.01; val west = r.lngs.min() - 0.01; val east = r.lngs.max() + 0.01
    val found = pois.asSequence().filter { it.kind == "toll" && it.lat in south..north && it.lng in west..east }
        .mapNotNull { t -> progress(r, cum, t.lat, t.lng).first.takeIf { it.off < 40 }?.let { t to it.along } }
        .sortedBy { it.second }.toList()
    // one station, however many booths OSM has of it (Σπαθοβούνι came three times): within 500 m of the last one kept
    val out = ArrayList<Pair<Poi, Double>>()
    for (t in found) if (out.isEmpty() || t.second - out.last().second > 500) out += t
        else if (out.last().first.name.isEmpty() && t.first.name.isNotEmpty()) out[out.lastIndex] = t
    return out
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
    /** Accent-blind, Greek and English names together, and the Greek one in Latin letters (Greeklish), for search. */
    val key = MapsSearch.fold("$name $nameEn").let { "$it ${MapsSearch.latin(MapsSearch.fold(name))}" }
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
    /** Cities' own disabled parking spots from data.gov.gr (tools/maps-amea.py), kind disabled_parking. */
    const val AMEA = "greece-amea.tsv"
    /** Places put into the map's own data by hand (the user's schools, 21 Oct: "keep them as system locations"). */
    const val ADDED = "added-places.tsv"
    data class Kind(val key: String, val label: String, val emoji: String, val emblem: String)
    /** Search's pills, in this order. */
    val PILLS = listOf(
        Kind("restaurant", "Restaurants", "🍽️", "food"), Kind("cafe", "Cafés", "☕", "cafe"), Kind("supermarket", "Supermarkets", "🛒", "shop"),
        Kind("pharmacy", "Pharmacies", "💊", "pharmacy"), Kind("eopyy", "EOPYY pharmacies", "⚕️", "pharmacy"), Kind("fuel", "Fuel", "⛽", "fuel"), Kind("charging", "EV charging", "🔌", "fuel"),
        Kind("disabled_parking", "Disabled parking", "♿", "accessible"), Kind("parking", "Parking", "🅿️", "parking"),
        Kind("hospital", "Hospitals", "🏥", "hospital"), Kind("bakery", "Bakeries", "🥖", "food"), Kind("fast_food", "Fast food", "🍔", "food"),
        Kind("atm", "ATMs", "🏧", "pin"), Kind("hotel", "Hotels", "🛏️", "hotel"), Kind("beach", "Beaches", "🏖️", "beach"),
        Kind("museum", "Museums", "🏛️", "star"), Kind("toilets", "Toilets", "🚻", "pin"),
        Kind("toll", "Tolls", "💶", "pin"),
        Kind("kindergarten", "Kindergartens", "🧸", "school"), Kind("primary_school", "Primary schools", "🎒", "school"),
        Kind("secondary_school", "High schools", "🏫", "school"), Kind("school", "Other schools", "🏫", "school"), Kind("university", "Universities", "🎓", "school"),
    )
    private val SETTLEMENTS = mapOf("city" to "City", "town" to "Town", "village" to "Village", "suburb" to "Suburb", "neighbourhood" to "Neighbourhood",
        "hamlet" to "Hamlet", "island" to "Island", "islet" to "Islet", "locality" to "Place")
    fun settlement(kind: String) = kind in SETTLEMENTS
    private val ONE = mapOf("toll" to "Toll", "eopyy" to "EOPYY pharmacy", "university" to "University", "school" to "School", "pharmacy" to "Pharmacy",
        "charging" to "EV charging", "disabled_parking" to "Disabled parking", "fast_food" to "Fast food")
    fun label(kind: String) = ONE[kind] ?: PILLS.firstOrNull { it.key == kind }?.label?.removeSuffix("s")?.let { if (kind == "pharmacy") "Pharmacy" else if (kind == "charging") "EV charging" else it }
        ?: SETTLEMENTS[kind] ?: kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
    fun emoji(kind: String) = PILLS.firstOrNull { it.key == kind }?.emoji ?: if (settlement(kind)) "🏘️" else "📍"
    fun emblem(kind: String) = PILLS.firstOrNull { it.key == kind }?.emblem ?: "pin"

    /** Lower case, no accents, final sigma as sigma: "Ξάνθη" and "ξανθη" are the same. */
    fun fold(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace('ς', 'σ')

    private val GREEK = mapOf('α' to "a", 'β' to "v", 'γ' to "g", 'δ' to "d", 'ε' to "e", 'ζ' to "z", 'η' to "i", 'θ' to "th", 'ι' to "i", 'κ' to "k",
        'λ' to "l", 'μ' to "m", 'ν' to "n", 'ξ' to "ks", 'ο' to "o", 'π' to "p", 'ρ' to "r", 'σ' to "s", 'τ' to "t", 'υ' to "i", 'φ' to "f", 'χ' to "x",
        'ψ' to "ps", 'ω' to "o")

    /**
     * Greek and Greeklish brought to one spelling, so "krewnas", "kreonas" and "Κρεώνας" meet (the user typed "Krewnas", 21 Oct):
     * ω/ο → o, η/ι/υ/ει/οι → i, αι → e, ου → ou, θ → th, χ → x; in Latin, w → o, y/h → i (not in th), 8 → th.
     */
    fun latin(folded: String): String {
        val g = folded.replace("ου", "\u0001").replace("ει", "ι").replace("οι", "ι").replace("αι", "ε")
        val b = StringBuilder()
        for (c in g) b.append(if (c == '\u0001') "ou" else GREEK[c] ?: c.toString())
        return b.toString().replace("8", "th").replace('w', 'o').replace('y', 'i').replace(Regex("(?<![tcp])h"), "i").replace("ch", "x")
    }

    fun parse(line: String): Poi? {
        val c = line.split('\t')
        if (c.size < 4) return null
        val lat = c[1].toDoubleOrNull() ?: return null; val lng = c[2].toDoubleOrNull() ?: return null
        return Poi(c[0], lat, lng, c[3], c.getOrElse(4) { "" }, c.getOrElse(5) { "" }, c.getOrElse(6) { "" }, c.getOrElse(7) { "" },
            c.getOrElse(8) { "" }, c.getOrElse(9) { "" }, c.getOrElse(10) { "" })
    }

    private var cache: Triple<String, Long, List<Poi>>? = null

    /** The places put in by hand (added-places.tsv), drawn on the map always, as the map's own. */
    fun added(root: File): List<Poi> = File(root, ADDED).takeIf { it.isFile }?.readLines()?.mapNotNull(::parse).orEmpty()

    /** The search list in [root] with EOPYY's, read once (about 140 000 places) and kept while the files are the same. */
    @Synchronized fun load(root: File): List<Poi> {
        val files = listOf(File(root, FILE), File(root, EOPYY), File(root, AMEA), File(root, ADDED)).filter { it.isFile }
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
    fun search(all: List<Poi>, query: String, lat: Double, lng: Double, kind: String? = null, limit: Int = 60, nearby: Boolean = false): List<Poi> {
        val q = fold(query.trim())
        // Nearby with no words or kind: what is around, not the towns (the user, 21 Oct: "βάλε και ένα κουμπί Nearby")
        if (q.isEmpty() && kind == null) return if (!nearby) emptyList() else all.asSequence().filter { !settlement(it.kind) && Math.abs(it.lat - lat) < 0.05 && Math.abs(it.lng - lng) < 0.065 }
            .sortedBy { metresBetween(lat, lng, it.lat, it.lng) }.take(limit).toList()
        val words = q.split(' ').filter { it.isNotEmpty() }
        val greeklish = latin(q).split(' ').filter { it.isNotEmpty() }
        // a kind with words that name a town: that kind in and around it, not with the town in its name (the user, 21 Oct:
        // "agios loukas" + Supermarkets went to Αθήνα, "ψάχνεις την λέξη … την κατηγορία που βρήσκετε")
        if (kind != null && words.isNotEmpty()) {
            val town = all.asSequence().filter { settlement(it.kind) && (it.key.startsWith(q) || it.key.contains(" $q") || words.all { w -> it.key.contains(w) } || greeklish.all { w -> it.key.contains(w) }) }
                .minByOrNull { metresBetween(lat, lng, it.lat, it.lng) }
            if (town != null) return search(all, "", town.lat, town.lng, kind, limit, nearby = true)
        }
        return all.asSequence()
            .filter { (kind == null || it.kind == kind) && (words.all { w -> it.key.contains(w) } || greeklish.all { w -> it.key.contains(w) }) }
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

    /**
     * Where an address written as Google writes it is ("Φωκίωνος 5, Δράμα 661 32, Ελλάδα"): the same street and number in
     * the address list, in that postcode when it has one; null when not there.
     */
    fun locate(root: File, address: String, pois: List<Poi> = emptyList()): Pair<Double, Double>? {
        val a = load(root) ?: return null
        val first = address.substringBefore(',').trim()
        val m = Regex("""^(.*\S)\s+(\d+[A-Za-zΑ-Ωα-ω]?)$""").find(first) ?: return null
        val street = MapsSearch.fold(m.groupValues[1]); val number = m.groupValues[2].lowercase()
        val rest = address.substringAfter(',', "")
        val post = Regex("""\b(\d{3})\s?(\d{2})\b""").find(rest)?.let { it.groupValues[1] + it.groupValues[2] }
        // the town ("Ξάνθη 671 00" → Ξάνθη): the same street and number are in many towns ("Τσιμισκή 30" went to Δράμα)
        val town = MapsSearch.fold(rest.substringBefore(',').replace(Regex("""\d"""), "").trim())
        val towns = if (town.isEmpty()) emptyList() else pois.filter { MapsSearch.settlement(it.kind) && (MapsSearch.fold(it.name) == town || MapsSearch.fold(it.nameEn) == town) }
        for (i in a.lat.indices) {
            val r = a.rest[i].split('\t')
            if (r.getOrNull(1)?.lowercase() != number || MapsSearch.fold(r[0]) != street) continue
            val rowPost = r.getOrNull(2)?.replace(" ", "").orEmpty(); val rowTown = MapsSearch.fold(r.getOrNull(3).orEmpty())
            if (post != null && rowPost.isNotEmpty() && rowPost != post) continue
            val sure = (post != null && rowPost == post) || (town.isNotEmpty() && rowTown == town) ||
                towns.any { metresBetween(it.lat, it.lng, a.lat[i], a.lng[i]) < 20_000 }
            if (sure) return a.lat[i] to a.lng[i]
        }
        return null
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
    /** [note]: the list's note, or the address Takeout gives a starred or labelled place. */
    data class Entry(val list: String, val name: String, val note: String, val lat: Double?, val lng: Double?)
    data class Result(val places: Int, val lists: Int, val missed: List<String>)

    private val D34 = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
    private val AT = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val SEARCH = Regex("""/maps/search/(-?\d+\.\d+),\+?(-?\d+\.\d+)""")
    private val Q = Regex("""[?&]q=(-?\d+\.\d+),\+?(-?\d+\.\d+)""")

    fun coords(url: String): Pair<Double, Double>? = (D34.find(url) ?: AT.find(url) ?: SEARCH.find(url) ?: Q.find(url))
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
            // Takeout's starred places without a location keep it in the link: ?q=lat,lng, or ?q=the address
            val q = Regex("""[?&]q=([^&]+)""").find(p.optString("google_maps_url"))?.groupValues?.get(1)
                ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }?.takeIf { lat == null && coords("?q=$it") == null }
            val address = loc?.optString("address").orEmpty().ifEmpty { p.optString("address") }.ifEmpty { q.orEmpty() }
            val name = loc?.optString("name").orEmpty().ifEmpty { p.optString("name").ifEmpty { p.optString("Title") } }.ifEmpty { address }
            if (name.isEmpty() && lat == null) null else Entry(list, name, address, lat, lng)
        }
    }.getOrDefault(emptyList())

    /**
     * The entries of a Takeout .zip, read as it streams by (a Takeout can be gigabytes: YouTube's videos, Maps' photos; only
     * the lists' .csv files and the places' .json files are kept), or of one .csv / .json given by itself ([fileName] names its list).
     */
    fun read(fileName: String, input: java.io.InputStream): List<Entry> =
        if (fileName.endsWith(".zip", true)) read(fileName, ByteArray(0), input) else read(fileName, input.readBytes())

    fun read(fileName: String, bytes: ByteArray, stream: java.io.InputStream? = null): List<Entry> {
        fun one(name: String, data: ByteArray): List<Entry> {
            val base = name.substringAfterLast('/').substringBeforeLast('.')
            val text = data.toString(Charsets.UTF_8)
            return when {
                name.endsWith(".csv", true) -> csv(base, text)
                name.endsWith(".json", true) -> geojson(base, text)
                else -> emptyList()
            }
        }
        if (!fileName.endsWith(".zip", true)) return one(fileName, bytes)
        val out = ArrayList<Entry>()
        java.util.zip.ZipInputStream(stream ?: bytes.inputStream()).use { z ->
            generateSequence { z.nextEntry }.forEach { e ->
                val n = e.name
                // English or Greek Takeout: Saved / Αποθηκευμένα (the lists), Maps (your places) / Χάρτες (τα μέρη σας) (starred),
                // and the labelled places (Labeled places / Μέρη με ετικέτα); reviews and the rest passed over
                val file = n.substringAfterLast('/')
                val wanted = (("/Saved/" in "/$n" || "/Αποθηκευμένα/" in "/$n") && n.endsWith(".csv", true)) ||
                    (("Maps (your places)/" in n || "Χάρτες (τα μέρη σας)/" in n) && n.endsWith(".json", true) && file !in setOf("Reviews.json", "Κριτικές.json")) ||
                    file in setOf("Labeled places.json", "Μέρη με ετικέτα.json")
                if (!e.isDirectory && wanted) out += one(n, z.readBytes())
            }
        }
        return out
    }

    fun emoji(list: String) = when (list.lowercase()) {
        "favourite places", "favorite places", "αγαπημένα μέρη", "αγαπημένα" -> "❤️"
        "want to go", "θέλω να πάω" -> "🚩"
        "starred places", "saved places", "με αστέρι", "αποθηκευμένα μέρη" -> "⭐"
        "travel plans", "ταξιδιωτικά σχέδια" -> "✈️"
        "labeled places", "μέρη με ετικέτα" -> "🏷️"
        else -> "📌"
    }

    /**
     * Into Tetra: a list for each Google list (one of the same name is reused), a place for each entry placed (a place of
     * the same name within 30 m is reused and joins the list), the rest named in [Result.missed].
     */
    fun into(root: File, pois: List<Poi>, entries: List<Entry>): Result {
        var lists = MapsFiles.lists(root)
        val missed = ArrayList<String>(); var count = 0
        val byName = pois.filter { it.name.isNotEmpty() }.groupBy { MapsSearch.fold(it.name) }
        for ((listName, es) in entries.groupBy { it.list }) {
            val list = lists.firstOrNull { it.name.equals(listName, true) } ?: MapsFiles.saveList(root, PlaceList(MapsFiles.newId(), listName, emoji(listName))).also { lists = lists + it }
            for (e in es) {
                // its own spot; else its name, when one place in Greece has it; else its address in the address list
                val at = if (e.lat != null && e.lng != null) e.lat to e.lng
                    else byName[MapsSearch.fold(e.name)]?.takeIf { it.size == 1 }?.first()?.let { it.lat to it.lng }
                    ?: MapsAddresses.locate(root, e.note, pois)
                if (at == null) { missed += "${e.name} ($listName)"; continue }
                // a spot with no name of its own is named by its address
                val name = e.name.ifEmpty { runCatching { MapsAddresses.describe(root, pois, at.first, at.second) }.getOrNull()?.substringBefore(',').orEmpty() }
                val same = MapsFiles.places(root).firstOrNull { MapsSearch.fold(it.name) == MapsSearch.fold(name) && metresBetween(it.lat, it.lng, at.first, at.second) < 30 }
                MapsFiles.save(root, same?.copy(lists = (same.lists + list.id).distinct())
                    ?: Place(MapsFiles.newId(), name.ifEmpty { "Saved place" }, at.first, at.second, lists = listOf(list.id)))
                count++
            }
        }
        return Result(count, entries.map { it.list }.distinct().size, missed)
    }
}

/**
 * The map enriched by hand and shared (the user, 21 Oct: "να προσθέτω στοιχεία στο χάρτη με τα σωστά σύμβολα, όταν βρήσκο
 * θέσεις αμέα ή μαγαζιά … μαζική εισαγωγή και εξαγωγή των προσωπικών ή των δημώσιων στειχείων για διαμηρασμό και
 * εμπλούτευση του χάρτη κάθε χρήστη"). Map additions are lines of added-places.tsv (the search list's form: a kind, so the
 * right symbol and pill); sharing is GeoJSON, which other map apps read too:
 *
 *   {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[lng,lat]},
 *     "properties":{"tetra":"map","kind":"disabled_parking","name":"…","more":"…"}}, … "tetra":"place", "icon", "lists":["Work"] …]}
 */
internal object MapsShare {
    private fun line(p: Poi) = listOf(p.kind, "%.6f".format(java.util.Locale.ROOT, p.lat), "%.6f".format(java.util.Locale.ROOT, p.lng), p.name, p.nameEn, p.more,
        p.hours, p.phone, p.web, p.wheelchair, p.note).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') } + "\n"

    fun add(root: File, p: Poi) {
        val f = File(root, MapsSearch.ADDED)
        root.mkdirs(); f.appendText(line(p))
    }

    /** The addition at [p]'s spot and of its kind goes (the others are written back as they were). */
    fun remove(root: File, p: Poi) {
        val f = File(root, MapsSearch.ADDED)
        if (!f.isFile) return
        val keep = f.readLines().filter { l -> MapsSearch.parse(l)?.let { it.kind == p.kind && metresBetween(it.lat, it.lng, p.lat, p.lng) < 2 } != true }
        val part = File(root, ".${f.name}.part")
        part.writeText(keep.joinToString("") { "$it\n" })
        check(part.renameTo(f)) { "Could not change the map's additions." }
    }

    /** My places ([places], with their lists by name) and/or the map's additions ([added]) as GeoJSON. */
    fun export(places: List<Place>, lists: List<PlaceList>, added: List<Poi>): String {
        val fs = org.json.JSONArray()
        fun feature(lat: Double, lng: Double, props: JSONObject) = JSONObject().put("type", "Feature")
            .put("geometry", JSONObject().put("type", "Point").put("coordinates", org.json.JSONArray().put(lng).put(lat))).put("properties", props)
        places.filter { it.id != MapsFiles.PARKING }.forEach { p ->
            fs.put(feature(p.lat, p.lng, JSONObject().put("tetra", "place").put("name", p.name).put("icon", p.icon)
                .put("lists", org.json.JSONArray(lists.filter { it.id in p.lists }.map { it.name })).apply { if (p.label.isNotBlank()) put("label", p.label) }))
        }
        added.forEach { a ->
            fs.put(feature(a.lat, a.lng, JSONObject().put("tetra", "map").put("kind", a.kind).put("name", a.name).put("more", a.more)
                .apply { if (a.hours.isNotEmpty()) put("hours", a.hours); if (a.phone.isNotEmpty()) put("phone", a.phone); if (a.web.isNotEmpty()) put("web", a.web) }))
        }
        return JSONObject().put("type", "FeatureCollection").put("features", fs).toString(1) + "\n"
    }

    data class Imported(val places: Int, val added: Int, val skipped: Int)

    /**
     * A shared file into Tetra: "map" features become map additions (one already there, of its kind within 10 m, is not
     * added again), the rest my places (one of the same name within 30 m joins the lists instead); GeoJSON from elsewhere
     * (no "tetra") comes in as places, named by its name or title.
     */
    fun import(root: File, text: String): Imported {
        val fs = JSONObject(text).optJSONArray("features") ?: return Imported(0, 0, 0)
        var places = 0; var added = 0; var skipped = 0
        var lists = MapsFiles.lists(root)
        val have = MapsSearch.added(root).toMutableList()
        for (i in 0 until fs.length()) {
            val f = fs.optJSONObject(i) ?: continue
            val c = f.optJSONObject("geometry")?.takeIf { it.optString("type") == "Point" }?.optJSONArray("coordinates")
            val lng = c?.optDouble(0) ?: Double.NaN; val lat = c?.optDouble(1) ?: Double.NaN
            if (lat.isNaN() || lng.isNaN() || lat !in -90.0..90.0 || lng !in -180.0..180.0) { skipped++; continue }
            val p = f.optJSONObject("properties") ?: JSONObject()
            val name = p.optString("name").ifEmpty { p.optString("title").ifEmpty { p.optString("Title") } }
            if (p.optString("tetra") == "map") {
                val kind = p.optString("kind").ifEmpty { "pin" }
                if (have.any { it.kind == kind && metresBetween(it.lat, it.lng, lat, lng) < 10 }) { skipped++; continue }
                val poi = Poi(kind, lat, lng, name, more = p.optString("more"), hours = p.optString("hours"), phone = p.optString("phone"), web = p.optString("web"))
                add(root, poi); have += poi; added++
            } else {
                val listIds = (p.optJSONArray("lists")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()).filter { it.isNotBlank() }.map { ln ->
                    (lists.firstOrNull { it.name.equals(ln, true) } ?: MapsFiles.saveList(root, PlaceList(MapsFiles.newId(), ln, MapsImport.emoji(ln))).also { lists = lists + it }).id
                }
                val same = MapsFiles.places(root).firstOrNull { MapsSearch.fold(it.name) == MapsSearch.fold(name) && metresBetween(it.lat, it.lng, lat, lng) < 30 }
                if (same != null) { if (!same.lists.containsAll(listIds)) MapsFiles.save(root, same.copy(lists = (same.lists + listIds).distinct())); skipped++; continue }
                MapsFiles.save(root, Place(MapsFiles.newId(), name.ifEmpty { "Place" }, lat, lng, p.optString("icon").takeIf { it in Place.ICONS } ?: "pin",
                    lists = listIds, label = p.optString("label")))
                places++
            }
        }
        return Imported(places, added, skipped)
    }
}

/**
 * Standing still or moving, from the phone's accelerometer, for a long tunnel (the user, 21 Oct: "μπορεί για 5 λεπτά να μην
 * έχω GPS … μπορεί να χρησιμοποιεί τους αισθητήρες του κινητού"): a moving car shakes the phone with the road; a standing
 * one, engine running or not, hardly at all. The spread of the acceleration's size over the last two seconds tells them
 * apart (a speed cannot be had from the sensors alone: the error grows too fast).
 */
internal class StillDetector(private val windowNanos: Long = 2_000_000_000L, private val threshold: Double = 0.12) {
    private val times = ArrayDeque<Long>(); private val sizes = ArrayDeque<Double>()

    /** One reading of linear acceleration (gravity taken out), m/s², at [nanos]. */
    fun add(nanos: Long, x: Float, y: Float, z: Float) {
        times.addLast(nanos); sizes.addLast(Math.sqrt((x * x + y * y + z * z).toDouble()))
        while (times.isNotEmpty() && nanos - times.first() > windowNanos) { times.removeFirst(); sizes.removeFirst() }
    }

    /** Standing still: a full window of readings that hardly move; null while there are too few to tell. */
    fun still(): Boolean? {
        if (sizes.size < 20 || times.last() - times.first() < windowNanos * 3 / 4) return null
        val mean = sizes.average()
        return Math.sqrt(sizes.sumOf { (it - mean) * (it - mean) } / sizes.size) < threshold
    }
}
