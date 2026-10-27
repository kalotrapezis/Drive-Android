package com.kalotrapezis.drive

import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.GraphHopperConfig
import com.graphhopper.config.Profile
import com.graphhopper.jackson.Jackson
import com.graphhopper.routing.WeightingFactory
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.routing.ev.Orientation
import com.graphhopper.routing.ev.TurnRestriction
import com.graphhopper.routing.weighting.DefaultTurnCostProvider
import com.graphhopper.routing.weighting.TurnCostProvider
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.routing.weighting.custom.CustomWeighting
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.PMap
import com.graphhopper.util.Parameters
import com.graphhopper.util.Translation
import com.graphhopper.util.TranslationMap
import com.graphhopper.util.TurnCostsConfig
import java.io.File
import java.util.Locale

/** One leg of a route's spoken and shown directions; [at] is the index in [Route.points] where it begins. */
internal data class Step(val text: String, val sign: Int, val metres: Double, val at: Int, val street: String, val ref: String = "",
    val junction: Junction? = null, val lanes: Lanes? = null, val toward: String = "")

/** [roads]: what it mostly runs on — "motorway", "main" or "local" — for the choice between routes. */
internal data class Route(val mode: String, val lats: DoubleArray, val lngs: DoubleArray, val metres: Double, val millis: Long, val steps: List<Step>,
    val roads: String = "")

/**
 * Offline routing on the phone: the graph the computer built (tools/maps-greece.sh, tools/maps-graph.yml) opened read-only
 * from Tetra/Maps/greece-gh with GraphHopper 10.2. GraphHopper turns a profile's custom model into Java bytecode with Janino
 * at run time, which Android cannot load, so the three custom models are written out here by hand (TetraWeightings) —
 * the same rules as GraphHopper's car.json, foot.json and bike.json, which the graph's shortcuts were made with.
 */
internal object MapsRouting {
    /**
     * A car's times as GraphHopper gives them, at the limits, are short of a real drive (the user, 21 Oct: Άγιος Λουκάς →
     * Χαλκίδα "is 30 min in reality, our map says 25"): a fifth more.
     */
    const val CAR_TIME = 1.2

    /** How far a search that keeps the car's heading may look before the shortcuts take over (on the computer it gives up on Athens–Thessaloniki in 0.8 s). */
    private const val MAX_HEADED_NODES = 300_000

    /** What turning back on the road costs a route (GraphHopper's 300 s had it say "turn back" all the way, 23 Oct). */
    private const val TURN_BACK_SECONDS = 1200

    /**
     * Main roads (the user, 24 Oct: "it always chooses the shorter path … even if that means a road that falls off a cliff"):
     * the car's way without car.json's pull towards the shorter one (distance_influence 90, a second and a half a kilometre),
     * a smaller road counting slower. A request hint on the car profile, so no new graph; the graph's shortcuts are made
     * for car.json, so it searches without them, up to [MAX_MAIN_NODES].
     */
    const val MAIN_ROADS = "tetra.main_roads"
    // shortcut: past this (a drive across Greece) the shortest way is taken, mostly on motorways anyway; a car_main CH profile in maps-graph.yml (a rebuild) would lift it
    private const val MAX_MAIN_NODES = 500_000

    /** How much slower a road of [c] counts on Main roads: the big ones as they are, a track ten times slower. */
    fun mainPriority(c: com.graphhopper.routing.ev.RoadClass): Double = when (c) {
        com.graphhopper.routing.ev.RoadClass.MOTORWAY, com.graphhopper.routing.ev.RoadClass.TRUNK,
        com.graphhopper.routing.ev.RoadClass.PRIMARY, com.graphhopper.routing.ev.RoadClass.SECONDARY -> 1.0
        com.graphhopper.routing.ev.RoadClass.TERTIARY -> 0.8
        com.graphhopper.routing.ev.RoadClass.RESIDENTIAL, com.graphhopper.routing.ev.RoadClass.LIVING_STREET -> 0.6
        com.graphhopper.routing.ev.RoadClass.UNCLASSIFIED -> 0.4
        com.graphhopper.routing.ev.RoadClass.TRACK -> 0.1
        else -> 0.3
    }

    /** maps-graph.yml's profiles, as Jackson reads them there: GraphHopper compares their hash with the graph's. */
    private val PROFILES = linkedMapOf(
        "car" to """{"name":"car","turn_costs":{"vehicle_types":["motorcar","motor_vehicle"]},"custom_model_files":["car.json"]}""",
        "foot" to """{"name":"foot","custom_model_files":["foot.json"]}""",
        "bike" to """{"name":"bike","custom_model_files":["bike.json"]}""",
        "wheelchair" to """{"name":"wheelchair","custom_model":{"priority":[{"if":"!foot_access || hike_rating >= 1","multiply_by":"0"},""" +
            """{"if":"road_class == STEPS","multiply_by":"0"},{"if":"mtb_rating > 1","multiply_by":"0"},{"else":"","multiply_by":"foot_priority"}],""" +
            """"speed":[{"if":"true","limit_to":"foot_average_speed"},{"if":"true","limit_to":"4"}]}}""",
    )

    /** The profiles [dir]'s graph was built with (properties.txt), in maps-graph.yml's order: an older graph lacks the newer ones. */
    fun profiles(dir: File): List<String> {
        val built = runCatching { File(dir, "properties.txt").readLines().firstOrNull { it.startsWith("profiles=") } }.getOrNull()
            ?.substringAfter("=")?.split(",")?.map { it.substringBefore("|") }.orEmpty()
        return PROFILES.keys.filter { it in built }.ifEmpty { listOf("car", "foot", "bike") }
    }

    /** The graphs open, the last used last: up to three, so a route across borders and the limits on its way open none again. */
    private val hoppers = LinkedHashMap<File, GraphHopper>(4, 0.75f, true)

    /** The graph in [dir], opened once and kept (opening maps the files, it reads nothing much). */
    @Synchronized fun open(dir: File): GraphHopper {
        hoppers[dir]?.let { return it }
        hoppers.entries.firstOrNull()?.takeIf { hoppers.size >= 3 }?.let { it.value.close(); hoppers.remove(it.key) }
        val names = profiles(dir)
        val json = """{"profiles":[${names.joinToString(",") { PROFILES.getValue(it) }}],"profiles_ch":[${names.joinToString(",") { "{\"profile\":\"$it\"}" }}],"profiles_lm":[]}"""
        val config = Jackson.newObjectMapper().readValue(json, GraphHopperConfig::class.java)
            .putObject("graph.location", dir.absolutePath)
            .putObject("graph.dataaccess.default_type", "MMAP_RO")
            .putObject("import.osm.ignored_highways", "")
        val gh = TetraHopper()
        gh.setAllowWrites(false) // no gh.lock in a synced folder
        gh.init(config)
        check(gh.load()) { "No routing graph in ${dir.name}." }
        hoppers[dir] = gh
        return gh
    }

    /** A route by [mode] (car, foot, bike), its directions in [w]'s language. */
    fun route(dir: File, fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, mode: String, w: Words, heading: Double = Double.NaN,
              main: Boolean = false): Route =
        routes(dir, fromLat, fromLng, toLat, toLng, mode, w, heading, main).first()

    private val translations by lazy { TranslationMap().doImport() }
    private val translated = HashMap<String, Translation>()

    /**
     * GraphHopper's turn words in [lang], with Tetra's own over the lines it lacks (resources tetra/graphhopper/<lang>.txt,
     * GraphHopper's format: et, ga, lv and mt whole, a few lines of es, fi, hr, lt, nl, pt, ro, sl); English where neither has one.
     */
    @Synchronized fun translation(lang: String): Translation = translated.getOrPut(lang) {
        val base = translations.getWithFallBack(Locale.forLanguageTag(if (lang == "pt") "pt-PT" else lang)) // Tetra's Portuguese is Portugal's
        val ours = MapsRouting::class.java.getResourceAsStream("/tetra/graphhopper/$lang.txt") ?: return@getOrPut base
        val m = HashMap(base.asMap())
        ours.bufferedReader().useLines { ls -> ls.filter { '=' in it && !it.startsWith("#") }.forEach { m[it.substringBefore('=')] = it.substringAfter('=') } }
        val l = Locale.forLanguageTag(lang)
        object : Translation {
            override fun tr(key: String, vararg params: Any?) = m[key.lowercase()]?.takeIf { it.isNotEmpty() }?.let { String.format(Locale.ROOT, it, *params) } ?: key
            override fun asMap() = m
            override fun getLocale() = l
            override fun getLanguage() = lang
        }
    }

    /**
     * Up to three ways there, the fastest first (the user, 21 Oct, beside Google Maps: "can our map have alternative routs with
     * tolls or without, main roads over roral roads"): GraphHopper's alternatives on the same graph; for a car, none that
     * runs a tenth of its way on tracks.
     */
    fun routes(dir: File, fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, mode: String, w: Words,
               heading: Double = Double.NaN, main: Boolean = false): List<Route> {
        val legs = MapsFiles.legs(dir, fromLat, fromLng, toLat, toLng, w) { g, aLat, aLng, bLat, bLng -> seconds(g, aLat, aLng, bLat, bLng, mode) }
        if (legs.size == 1) return direct(legs[0].dir, fromLat, fromLng, toLat, toLng, mode, w, heading, main)
        // across borders: each leg on its own country's roads, driven to the crossing and on from there, as one route
        val t0 = System.nanoTime()
        val parts = legs.mapIndexed { i, l -> direct(l.dir, l.fromLat, l.fromLng, l.toLat, l.toLng, mode, w, if (i == 0) heading else Double.NaN, main, alternatives = false).first() }
        WriterLog.i("Maps: a route across ${legs.size - 1} border(s), through ${legs.joinToString(" → ") { it.dir.name }}, its legs in ${(System.nanoTime() - t0) / 1_000_000} ms")
        return listOf(join(parts))
    }

    /** How long from one point to the other in seconds, GraphHopper's (no line, no turns), or null when there is no way: to choose a border crossing. */
    fun seconds(dir: File, fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, mode: String): Double? {
        val mode = if (mode == "wheelchair" && "wheelchair" !in profiles(dir)) "foot" else mode
        val req = GHRequest(fromLat, fromLng, toLat, toLng).setProfile(mode)
        req.hints.putObject(Parameters.Routing.INSTRUCTIONS, false).putObject(Parameters.Routing.CALC_POINTS, false)
        val r = open(dir).route(req)
        return if (r.hasErrors()) null else r.best.time / 1000.0
    }

    /** Routes one after the other as one: their lines joined at the shared point, the arrival said only at the end. */
    fun join(parts: List<Route>): Route {
        var offset = 0
        val steps = parts.flatMapIndexed { i, r ->
            (if (i < parts.lastIndex) r.steps.filter { it.sign != com.graphhopper.util.Instruction.FINISH } else r.steps)
                .map { it.copy(at = it.at + offset) }.also { offset += r.lats.size - 1 }
        }
        fun line(of: (Route) -> DoubleArray) = parts.flatMapIndexed { i, r -> of(r).asList().drop(if (i == 0) 0 else 1) }.toDoubleArray()
        return Route(parts[0].mode, line { it.lats }, line { it.lngs }, parts.sumOf { it.metres }, parts.sumOf { it.millis }, steps, parts.maxBy { it.metres }.roads)
    }

    /** [routes] on the one graph [dir]; without [alternatives], the fastest alone (a leg across a border). */
    private fun direct(dir: File, fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, mode: String, w: Words,
                       heading: Double, main: Boolean, alternatives: Boolean = true): List<Route> {
        val gh = open(dir)
        val main = main && mode == "car"
        val tr = translation(w.lang); val locale = tr.locale
        val mode = if (mode == "wheelchair" && "wheelchair" !in profiles(dir)) "foot" else mode // a graph from before Rolling
        val req = GHRequest(fromLat, fromLng, toLat, toLng).setProfile(mode).setLocale(locale).setAlgorithm(if (alternatives) Parameters.Algorithms.ALT_ROUTE else "")
            .setPathDetails(listOf("street_name", "street_ref", "road_environment", "road_class"))
        req.hints.putObject("alternative_route.max_paths", 3)
        if (main) req.hints.putObject(MAIN_ROADS, true).putObject(Parameters.CH.DISABLE, true).putObject(Parameters.Routing.MAX_VISITED_NODES, MAX_MAIN_NODES)
        // a moving car goes on the way it faces (the user, 23 Oct: each new route said "turn back" for kilometres): one way,
        // without the graph's shortcuts, which take no heading; where that search grows too big, the shortcuts without it
        if (heading.isFinite()) {
            val ahead = GHRequest(fromLat, fromLng, toLat, toLng).setProfile(mode).setLocale(locale).setHeadings(listOf(heading, Double.NaN))
                .setAlgorithm(Parameters.Algorithms.ASTAR_BI).setPathDetails(req.pathDetails)
            ahead.hints.putObject(Parameters.CH.DISABLE, true).putObject(Parameters.Routing.MAX_VISITED_NODES, MAX_HEADED_NODES)
                .putObject(Parameters.Routing.HEADING_PENALTY, TURN_BACK_SECONDS).putObject(MAIN_ROADS, main)
            val r = gh.route(ahead)
            if (!r.hasErrors()) return listOf(MapsLanes.on(build(gh, r.best, tr, w, mode), dir.absoluteFile.parentFile, w))
        }
        val t0 = System.nanoTime()
        var rsp = gh.route(req)
        if (rsp.hasErrors()) rsp = gh.route(req.setAlgorithm(""))
        if (rsp.hasErrors() && main) { // too far to search without the shortcuts: today's way
            WriterLog.i("Maps: main roads too far to search (${rsp.errors.firstOrNull()?.message}), the shortest way instead")
            return direct(dir, fromLat, fromLng, toLat, toLng, mode, w, heading, false, alternatives)
        }
        val t1 = System.nanoTime() // alternatives not to be had here: the one way
        check(!rsp.hasErrors()) { words(rsp.errors.firstOrNull(), w) }
        val all = rsp.all.map { MapsLanes.on(build(gh, it, tr, w, mode), dir.absoluteFile.parentFile, w) }
        WriterLog.i("Maps: route found in ${(t1 - t0) / 1_000_000} ms${if (main) " on main roads" else ""}, its crossings and lanes in ${(System.nanoTime() - t1) / 1_000_000} ms")
        val usable = all.filter { r -> mode != "car" || r.roads != "track" }.ifEmpty { all }
        return usable.sortedBy { it.millis }
    }

    /** GraphHopper's error in the user's words (it said "Cannot find point 0: 37.9,23.55" for a spot at sea). */
    fun words(e: Throwable?, w: Words): String {
        val m = e?.message.orEmpty()
        return when {
            e is com.graphhopper.util.exceptions.PointNotFoundException || "Cannot find point" in m ->
                if ((e as? com.graphhopper.util.exceptions.PointNotFoundException)?.pointIndex == 0 || "point 0" in m) w.s(R.string.maps_error_not_near_you)
                else w.s(R.string.maps_error_destination_not_near)
            "Connection between locations not found" in m -> w.s(R.string.maps_error_no_way)
            else -> m.ifEmpty { w.s(R.string.maps_error_no_route) }
        }
    }

    private fun build(gh: GraphHopper, best: com.graphhopper.ResponsePath, tr: Translation, w: Words, mode: String): Route {
        val names = best.pathDetails["street_name"].orEmpty(); val refs = best.pathDetails["street_ref"].orEmpty()
        val environment = best.pathDetails["road_environment"].orEmpty()
        /** Whether a leg is mostly a ferry (GraphHopper 10 has no ferry instruction: it said "turn left" for 58 km of sea). */
        fun ferry(from: Int, to: Int) = environment.filter { it.value?.toString().equals("ferry", true) }
            .sumOf { maxOf(0, minOf(it.last, to) - maxOf(it.first, from)) } * 2 > (to - from)
        /** The road a leg [from]..[to] (point indexes) mostly runs on: its code and name, as "Α8 Ολυμπία Οδός". */
        fun mostly(from: Int, to: Int): String {
            fun longest(ds: List<com.graphhopper.util.details.PathDetail>) = ds.filter { it.last > from && it.first < to && (it.value as? String).orEmpty().isNotBlank() }
                .maxByOrNull { minOf(it.last, to) - maxOf(it.first, from) }?.value as? String
            return listOfNotNull(longest(refs)?.substringBefore(';'), longest(names)).distinct().joinToString(" ")
        }
        var at = 0
        val steps = best.instructions.map { ins ->
            var text = ins.getTurnDescription(tr)
            var street = ins.name.orEmpty(); var ref = (ins.extraInfoJSON["street_ref"] as? String).orEmpty()
            var toward = (ins.extraInfoJSON["street_destination"] as? String).orEmpty().ifEmpty { (ins.extraInfoJSON["street_destination_ref"] as? String).orEmpty() }
            if (ferry(at, at + ins.points.size())) {
                text = w.s(R.string.maps_take_ferry) + (if (street.isNotEmpty()) " ($street)" else "")
                street = w.s(R.string.maps_ferry)
            }
            // a turn onto a ramp with no name says where the ramp leads (the user, 21 Oct, on motorways: "Στρίψτε αριστερά" for 61 km)
            if (street.isEmpty() && ins.sign != com.graphhopper.util.Instruction.FINISH) {
                val road = mostly(at, at + ins.points.size())
                if (road.isNotEmpty() && ins.distance > 1000) {
                    text = w.s(R.string.maps_towards, text, road); toward = road
                    street = road.substringAfter(' ', road); ref = ref.ifEmpty { road.substringBefore(' ').takeIf { r -> r.any(Char::isDigit) }.orEmpty() }
                }
            }
            // a car's turn: its crossing, for the turn card's picture (the user, 23 Oct: Δράμα's five streets)
            val cross = if (mode == "car" && at > 0 && ins.sign !in setOf(0, 4, 5)) runCatching { junction(gh, best.points, at, at + ins.points.size(), ins.sign == 6) }.getOrNull() else null
            Step(text.replaceFirstChar { it.uppercase() }, ins.sign, ins.distance, at, street, ref, cross, toward = toward).also { at += ins.points.size() }
        }
        val pts = best.points
        // what it runs on, by share of its length (a motorway has few points for many kilometres)
        val share = HashMap<String, Double>()
        best.pathDetails["road_class"].orEmpty().forEach { d ->
            var m = 0.0
            for (i in d.first until minOf(d.last, pts.size() - 1)) m += metresBetween(pts.getLat(i), pts.getLon(i), pts.getLat(i + 1), pts.getLon(i + 1))
            share.merge(d.value?.toString()?.lowercase().orEmpty(), m, Double::plus)
        }
        val total = share.values.sum().coerceAtLeast(1.0)
        fun part(vararg k: String) = (k.sumOf { share[it] ?: 0.0 } * 100 / total).toInt()
        val roads = when {
            part("track") >= 10 -> "track"
            part("motorway", "trunk") >= 25 -> "motorway"
            part("motorway", "trunk", "primary", "secondary") >= 50 -> "main"
            else -> "local"
        }
        val millis = if (mode == "car") (best.time * CAR_TIME).toLong() else best.time
        return Route(mode, DoubleArray(pts.size()) { pts.getLat(it) }, DoubleArray(pts.size()) { pts.getLon(it) }, best.distance, millis, steps, roads)
    }

    /**
     * The crossing where a route's turn is, at point [at] of [pts] (the next turn's at [next]), as the car arrives: the car
     * roads leaving it from the graph, each one's direction ~20 m out; a [roundabout]'s from its ring's middle. Null where
     * the turn is not at a crossing the graph has.
     */
    private fun junction(gh: GraphHopper, pts: com.graphhopper.util.PointList, at: Int, next: Int, roundabout: Boolean): Junction? {
        val g = gh.baseGraph; val na = g.nodeAccess; val em = gh.encodingManager
        val access = em.getBooleanEncodedValue("car_access"); val ring = em.getBooleanEncodedValue("roundabout")
        fun car(e: EdgeIteratorState) = e.get(access) || e.getReverse(access)
        if (at < 1) return null
        val snap = gh.locationIndex.findClosest(pts.getLat(at), pts.getLon(at)) { car(it) }
        if (!snap.isValid) return null
        val node = if (snap.snappedPosition == com.graphhopper.storage.index.Snap.Position.TOWER) snap.closestNode
            else listOf(snap.closestEdge.baseNode, snap.closestEdge.adjNode).minBy { metresBetween(na.getLat(it), na.getLon(it), pts.getLat(at), pts.getLon(at)) }
        if (metresBetween(na.getLat(node), na.getLon(node), pts.getLat(at), pts.getLon(at)) > 15) return null
        val explorer = g.createEdgeExplorer()
        /** The car roads at [n] (but [skip]ped ones): each one's first stretch, its point ~40 m along to draw, and whether
         *  a car may leave [n] by it ([out]) and come to [n] by it ([into]). */
        class Arm(val first: Double, val lat: Double, val lng: Double, val out: Boolean, val into: Boolean)
        fun arms(n: Int, skip: (EdgeIteratorState) -> Boolean): List<Arm> {
            val out = ArrayList<Arm>()
            val it = explorer.setBaseNode(n)
            while (it.next()) {
                if (!car(it) || skip(it)) continue
                val geo = it.fetchWayGeometry(com.graphhopper.util.FetchMode.ALL)
                if (geo.size() < 2) continue
                var k = 1
                while (k < geo.size() - 1 && metresBetween(geo.getLat(0), geo.getLon(0), geo.getLat(k), geo.getLon(k)) < 40) k++
                out += Arm(bearing(geo.getLat(0), geo.getLon(0), geo.getLat(1), geo.getLon(1)), geo.getLat(k), geo.getLon(k), it.get(access), it.getReverse(access))
            }
            return out
        }
        fun apart(a: Double, b: Double) = Math.abs(((a - b) % 360 + 540) % 360 - 180)
        /** The way in, the way on, then each other road once: a dual carriageway's two halves — 15° apart, one only to the
         *  crossing and one only from it — are one road; two ways out of a fork, however close, are two. */
        fun oneEach(d: List<Double>, arms: List<Arm>, inArm: Int, outArm: Int): List<Double> {
            val keep = arrayListOf(inArm, outArm)
            fun halves(i: Int, j: Int) = apart(d[i], d[j]) < 15 && ((arms[i].out && !arms[i].into && arms[j].into && !arms[j].out) ||
                (arms[i].into && !arms[i].out && arms[j].out && !arms[j].into))
            for (i in d.indices) if (i !in keep && keep.none { halves(i, it) }) keep += i
            return keep.map { d[it] }
        }
        val dirs: List<Double>; val taken: Int
        // the crossing's nodes: this one and those a short road away — OpenStreetMap often draws one big crossing as two or
        // three small ones a few metres apart (Νικήσιανη's 5–6 streets: no node there has five roads)
        val group = LinkedHashSet<Int>()
        if (!roundabout) {
            val todo = ArrayDeque(listOf(node))
            while (todo.isNotEmpty() && group.size < 8) {
                val n = todo.removeFirst(); if (!group.add(n)) continue
                val it = explorer.setBaseNode(n)
                while (it.next()) if (car(it) && it.distance <= 25 && it.adjNode !in group &&
                    metresBetween(na.getLat(it.adjNode), na.getLon(it.adjNode), na.getLat(node), na.getLon(node)) <= 30) todo += it.adjNode
            }
        }
        /** The roads at the route's own node: the way in, the way on, the rest. */
        fun one(): List<Double>? {
            // the way in and the way on are the roads the route itself runs along: its points next to the crossing lie on them
            val nLat = na.getLat(node); val nLng = na.getLon(node)
            val all = arms(node) { false }
            if (all.size < 3 || at + 1 >= pts.size()) return null
            val back = bearing(nLat, nLng, pts.getLat(at - 1), pts.getLon(at - 1)); val on = bearing(nLat, nLng, pts.getLat(at + 1), pts.getLon(at + 1))
            val inArm = all.indices.minBy { apart(all[it].first, back) }; val outArm = all.indices.minBy { apart(all[it].first, on) }
            if (inArm == outArm) return null
            val ref = bearing(nLat, nLng, all[inArm].lat, all[inArm].lng) + 180 // the way in straight down
            val d = all.map { ((bearing(nLat, nLng, it.lat, it.lng) - ref) % 360 + 540) % 360 - 180 }
            return oneEach(d, all, inArm, outArm)
        }
        /** The roads leaving a crossing of several nodes, seen from its middle; the way on is where the route leaves it. */
        fun many(): List<Double>? {
            val cLat = group.map { na.getLat(it) }.average(); val cLng = group.map { na.getLon(it) }.average()
            val all = group.flatMap { n -> arms(n) { it.adjNode in group } }
            // the route ~30 m from the middle before and after: which roads it comes in and goes on by
            fun leave(by: Int): Int? { var k = at; while (k + by in 0 until pts.size()) { k += by; if (metresBetween(cLat, cLng, pts.getLat(k), pts.getLon(k)) >= 30) return k }; return null }
            val j = leave(-1) ?: return null; val k = leave(1) ?: return null
            val far = all.map { bearing(cLat, cLng, it.lat, it.lng) }
            val inArm = far.indices.minBy { apart(far[it], bearing(cLat, cLng, pts.getLat(j), pts.getLon(j))) }
            val outArm = far.indices.minBy { apart(far[it], bearing(cLat, cLng, pts.getLat(k), pts.getLon(k))) }
            if (inArm == outArm) return null
            val ref = far[inArm] + 180
            val d = far.map { ((it - ref) % 360 + 540) % 360 - 180 }
            return oneEach(d, all, inArm, outArm)
        }
        if (!roundabout) {
            // the crossing as several nodes only where that makes five roads or more; else the turn's own node, exactly
            val d = (if (group.size > 1) many()?.takeIf { it.size >= 5 } else null) ?: one() ?: return null
            dirs = d; taken = 1
        } else { // a roundabout: round the ring from the way in, its middle, the roads leaving it, seen from there
            val nodes = LinkedHashSet<Int>(); val todo = ArrayDeque(listOf(node))
            while (todo.isNotEmpty() && nodes.size < 60) {
                val n = todo.removeFirst(); if (!nodes.add(n)) continue
                val it = explorer.setBaseNode(n); while (it.next()) if (it.get(ring) && it.adjNode !in nodes) todo += it.adjNode
            }
            if (nodes.size < 3 || next + 1 >= pts.size()) return null
            val cLat = nodes.map { na.getLat(it) }.average(); val cLng = nodes.map { na.getLon(it) }.average()
            val all = nodes.flatMap { n -> arms(n) { it.get(ring) }.map { n to it } }
            // the way in at the entry node, the way out at the ring node where the next direction starts: the route runs on both
            val exit = nodes.minBy { metresBetween(na.getLat(it), na.getLon(it), pts.getLat(next), pts.getLon(next)) }
            fun road(n: Int, k: Int) = all.indices.filter { all[it].first == n }
                .minByOrNull { apart(all[it].second.first, bearing(na.getLat(n), na.getLon(n), pts.getLat(k), pts.getLon(k))) }
            val inArm = road(node, at - 1) ?: return null; val outArm = road(exit, next + 1) ?: return null
            if (inArm == outArm) return null
            val far = all.map { (_, a) -> bearing(cLat, cLng, a.lat, a.lng) }
            val ref = far[inArm] + 180
            dirs = oneEach(far.map { ((it - ref) % 360 + 540) % 360 - 180 }, all.map { it.second }, inArm, outArm); taken = 1
        }
        if (dirs.size < 3) return null
        val sorted = dirs.sorted()
        return Junction(sorted, sorted.indexOf(dirs[taken]), roundabout)
    }

    /**
     * The speed limit of the road nearest [lat],[lng] in km/h, from OSM's maxspeed in the graph (the user, 21 Oct: "ξέρουμε και
     * όρια ταχύτητας; να το βάλουμε να κοκκινίζει όταν το περνάω;"); null where it is not known or no road is near.
     */
    fun limitAt(dir: File, lat: Double, lng: Double): Int? {
        val gh = open(dir)
        val em = gh.encodingManager
        val access = em.getBooleanEncodedValue("car_access"); val max = em.getDecimalEncodedValue("max_speed")
        val snap = gh.locationIndex.findClosest(lat, lng) { e -> e.get(access) || e.getReverse(access) }
        if (!snap.isValid || snap.queryDistance > 40) return null
        val e = snap.closestEdge
        val v = listOf(e.get(max), e.getReverse(max)).filter { it.isFinite() && it > 0 }.minOrNull() ?: return null
        return Math.round(v).toInt()
    }

    private class TetraHopper : GraphHopper() {
        override fun createWeightingFactory() = WeightingFactory { profile, hints, noTurns -> weighting(profile, hints, noTurns) }

        private fun weighting(profile: Profile, request: PMap, noTurns: Boolean): Weighting {
            val em = encodingManager
            val hints = PMap(profile.hints).putAll(request)
            // as GraphHopper's DefaultWeightingFactory
            val turns = if (profile.hasTurnCosts() && !noTurns) {
                val o = if (em.hasEncodedValue(Orientation.KEY)) em.getDecimalEncodedValue(Orientation.KEY) else null
                val u = hints.getInt(Parameters.Routing.U_TURN_COSTS, profile.turnCostsConfig.uTurnCosts)
                DefaultTurnCostProvider(em.getTurnBooleanEncodedValue(TurnRestriction.key(profile.name)), o, baseGraph.baseGraph,
                    TurnCostsConfig(profile.turnCostsConfig).setUTurnCosts(u))
            } else TurnCostProvider.NO_TURN_COST_PROVIDER
            val heading = hints.getDouble(Parameters.Routing.HEADING_PENALTY, profile.customModel?.headingPenalty ?: Parameters.Routing.DEFAULT_HEADING_PENALTY)
            fun b(n: String) = em.getBooleanEncodedValue(n)
            fun d(n: String) = em.getDecimalEncodedValue(n)
            fun i(n: String) = em.getIntEncodedValue(n)
            fun EdgeIteratorState.v(e: BooleanEncodedValue, r: Boolean) = if (r) getReverse(e) else get(e)
            fun EdgeIteratorState.v(e: DecimalEncodedValue, r: Boolean) = if (r) getReverse(e) else get(e)
            fun EdgeIteratorState.v(e: IntEncodedValue, r: Boolean) = if (r) getReverse(e) else get(e)
            val p = when (profile.name) {
                "car" -> { // car.json, or Main roads ([MAIN_ROADS])
                    val access = b("car_access"); val speed = d("car_average_speed")
                    val road = em.getEnumEncodedValue(com.graphhopper.routing.ev.RoadClass.KEY, com.graphhopper.routing.ev.RoadClass::class.java)
                    if (hints.getBool(MAIN_ROADS, false)) CustomWeighting.Parameters({ e, r -> minOf(999.0, e.v(speed, r)) }, { speed.maxOrMaxStorableDecimal },
                        { e, r -> if (!e.v(access, r)) 0.0 else mainPriority(e.get(road)) }, { 1.0 }, 0.0, heading)
                    else CustomWeighting.Parameters({ e, r -> minOf(999.0, e.v(speed, r)) }, { speed.maxOrMaxStorableDecimal },
                        { e, r -> if (!e.v(access, r)) 0.0 else 1.0 }, { 1.0 }, 90.0, heading)
                }
                "foot" -> { // foot.json
                    val access = b("foot_access"); val speed = d("foot_average_speed"); val prio = d("foot_priority")
                    val hike = i("hike_rating"); val mtb = i("mtb_rating")
                    CustomWeighting.Parameters({ e, r -> minOf(999.0, e.v(speed, r)) }, { speed.maxOrMaxStorableDecimal },
                        { e, r ->
                            var v = 1.0
                            if (!e.v(access, r) || e.v(hike, r) >= 2) v *= 0.0
                            if (e.v(mtb, r) > 2) v *= 0.0 else v *= e.v(prio, r)
                            v
                        }, { prio.maxOrMaxStorableDecimal }, 0.0, heading)
                }
                "wheelchair" -> { // maps-graph.yml's inline model: foot.json without steps or rough paths, at most 4 km/h
                    val access = b("foot_access"); val speed = d("foot_average_speed"); val prio = d("foot_priority")
                    val hike = i("hike_rating"); val mtb = i("mtb_rating")
                    val road = em.getEnumEncodedValue(com.graphhopper.routing.ev.RoadClass.KEY, com.graphhopper.routing.ev.RoadClass::class.java)
                    CustomWeighting.Parameters({ e, r -> minOf(999.0, e.v(speed, r), 4.0) }, { minOf(speed.maxOrMaxStorableDecimal, 4.0) },
                        { e, r ->
                            var v = 1.0
                            if (!e.v(access, r) || e.v(hike, r) >= 1) v *= 0.0
                            if (e.get(road) == com.graphhopper.routing.ev.RoadClass.STEPS) v *= 0.0
                            if (e.v(mtb, r) > 1) v *= 0.0 else v *= e.v(prio, r)
                            v
                        }, { prio.maxOrMaxStorableDecimal }, 0.0, heading)
                }
                "bike" -> { // bike.json
                    val access = b("bike_access"); val speed = d("bike_average_speed"); val prio = d("bike_priority")
                    val hike = i("hike_rating"); val mtb = i("mtb_rating"); val round = b("roundabout")
                    CustomWeighting.Parameters({ e, r ->
                            var v = minOf(999.0, e.v(speed, r))
                            if (!e.v(access, r) && e.v(access, !r)) v = minOf(v, 5.0)
                            v
                        }, { speed.maxOrMaxStorableDecimal },
                        { e, r ->
                            var v = e.v(prio, r)
                            if (e.v(mtb, r) > 2) v *= 0.0
                            if (e.v(hike, r) > 1) v *= 0.0
                            val fwd = e.v(access, r); val back = e.v(access, !r)
                            if (!fwd && (!back || e.v(round, r))) v *= 0.0 else if (!fwd && back) v *= 0.2
                            v
                        }, { prio.maxOrMaxStorableDecimal }, 0.0, heading)
                }
                else -> throw IllegalArgumentException("No profile ${profile.name}")
            }
            return CustomWeighting(turns, p)
        }
    }
}
