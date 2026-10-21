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
import com.graphhopper.util.TurnCostsConfig
import java.io.File
import java.util.Locale

/** One leg of a route's spoken and shown directions; [at] is the index in [Route.points] where it begins. */
internal data class Step(val text: String, val sign: Int, val metres: Double, val at: Int, val street: String)

internal data class Route(val mode: String, val lats: DoubleArray, val lngs: DoubleArray, val metres: Double, val millis: Long, val steps: List<Step>)

/**
 * Offline routing on the phone: the graph the computer built (tools/maps-greece.sh, tools/maps-graph.yml) opened read-only
 * from Tetra/Maps/greece-gh with GraphHopper 10.2. GraphHopper turns a profile's custom model into Java bytecode with Janino
 * at run time, which Android cannot load, so the three custom models are written out here by hand (TetraWeightings) —
 * the same rules as GraphHopper's car.json, foot.json and bike.json, which the graph's shortcuts were made with.
 */
internal object MapsRouting {
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

    private var hopper: GraphHopper? = null
    private var loaded: File? = null

    /** The graph in [dir], opened once and kept (opening maps the files, it reads nothing much). */
    @Synchronized fun open(dir: File): GraphHopper {
        hopper?.let { if (loaded == dir) return it; it.close() }
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
        hopper = gh; loaded = dir
        return gh
    }

    /** A route by [mode] (car, foot, bike), its directions in [lang] (el or en: GraphHopper's own translations). */
    fun route(dir: File, fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, mode: String, lang: String = "el"): Route {
        val gh = open(dir)
        val locale = Locale.forLanguageTag(lang)
        val mode = if (mode == "wheelchair" && "wheelchair" !in profiles(dir)) "foot" else mode // a graph from before Rolling
        val rsp = gh.route(GHRequest(fromLat, fromLng, toLat, toLng).setProfile(mode).setLocale(locale))
        check(!rsp.hasErrors()) { rsp.errors.firstOrNull()?.message ?: "No route." }
        val best = rsp.best
        val tr = gh.translationMap.getWithFallBack(locale)
        var at = 0
        val steps = best.instructions.map { ins ->
            Step(ins.getTurnDescription(tr), ins.sign, ins.distance, at, ins.name.orEmpty()).also { at += ins.points.size() }
        }
        val pts = best.points
        return Route(mode, DoubleArray(pts.size()) { pts.getLat(it) }, DoubleArray(pts.size()) { pts.getLon(it) }, best.distance, best.time, steps)
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
            val heading = profile.customModel?.headingPenalty ?: Parameters.Routing.DEFAULT_HEADING_PENALTY
            fun b(n: String) = em.getBooleanEncodedValue(n)
            fun d(n: String) = em.getDecimalEncodedValue(n)
            fun i(n: String) = em.getIntEncodedValue(n)
            fun EdgeIteratorState.v(e: BooleanEncodedValue, r: Boolean) = if (r) getReverse(e) else get(e)
            fun EdgeIteratorState.v(e: DecimalEncodedValue, r: Boolean) = if (r) getReverse(e) else get(e)
            fun EdgeIteratorState.v(e: IntEncodedValue, r: Boolean) = if (r) getReverse(e) else get(e)
            val p = when (profile.name) {
                "car" -> { // car.json
                    val access = b("car_access"); val speed = d("car_average_speed")
                    CustomWeighting.Parameters({ e, r -> minOf(999.0, e.v(speed, r)) }, { speed.maxOrMaxStorableDecimal },
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
