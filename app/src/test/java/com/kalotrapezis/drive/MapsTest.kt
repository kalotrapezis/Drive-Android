package com.kalotrapezis.drive

import java.io.File
import java.nio.file.Files
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test

/** Tetra's words in Greek and English, as the tests below hear them. */
private val el = testWords("el")
private val en = testWords("en")

/** Maps (Plan.md § Maps): saved places as files, Home's six slots, and offline routing on the computer's graph. */
class MapsTest {
    @Test fun places() {
        val root = File(Files.createTempDirectory("tetra").toFile(), "Maps") // its Trash beside it, as in Tetra
        val home = MapsFiles.save(root, Place("a1", "Home", 37.97, 23.72, "home", slot = 1))
        MapsFiles.save(root, Place("b2", "Work", 38.0, 23.8, "work", slot = 2, mode = "foot"))
        assertEquals(listOf("Home", "Work"), MapsFiles.places(root).map { it.name })
        // taking slot 1 frees it on Home
        MapsFiles.save(root, Place("c3", "School", 37.9, 23.7, "school", slot = 1))
        val slots = MapsFiles.slots(MapsFiles.places(root))
        assertEquals("School", slots[0]?.name); assertEquals("Work", slots[1]?.name); assertNull(slots[2])
        assertEquals(0, MapsFiles.places(root).first { it.id == home.id }.slot)
        assertEquals("foot", MapsFiles.places(root).first { it.id == "b2" }.mode)
        // stray files are not places
        File(root, "Places/x.json").writeText("{\"name\":\"no coordinates\"}")
        File(root, "Places/.c3.json.part").writeText("half")
        assertEquals(3, MapsFiles.places(root).size)
        MapsFiles.delete(root, home)
        assertEquals(2, MapsFiles.places(root).size)
        assertEquals("350 m", metresText(347.0)); assertEquals("1.4 km", metresText(1380.0)); assertEquals("12 km", metresText(12400.0))
    }

    /** Two devices each put a place into slot 7 while apart: the newer keeps it, the other moves to the first free slot. */
    @Test fun slotsAfterSync() {
        val root = File(Files.createTempDirectory("tetra").toFile(), "Maps") // its Trash beside it, as in Tetra
        val dir = File(root, "Places").apply { mkdirs() }
        fun put(name: String, p: Place, at: Long) = File(dir, name).apply { writeText(p.json()); setLastModified(at) }
        val day = 1_792_700_000_000L
        (1..5).forEach { put("s$it.json", Place("s$it", "School $it", 38.0, 23.5, slot = it), day) }
        put("market.json", Place("market", "My Market", 37.35, 22.58, "shop", slot = 7), day + 60_000)
        put("home.json", Place("home", "Σπίτι", 37.27, 22.58, "home", slot = 7), day + 120_000)
        // a sync's older copy of home, in slot 6: not a place of its own
        put("home (2).json", Place("home", "Σπίτι", 37.27, 22.58, "home", slot = 6), day)
        val slots = MapsFiles.slots(MapsFiles.places(root))
        assertEquals(listOf("School 1", "School 2", "School 3", "School 4", "School 5", "My Market", "Σπίτι", null),
            slots.take(8).map { it?.name })
        // on Home's card as on the place's own page; nothing written
        assertEquals(6, MapsFiles.places(root).first { it.id == "market" }.slot)
        assertEquals(7, Place.parse(File(dir, "market.json").readText())?.slot)
        // the one moved can be put back: taking slot 7 frees it on home
        MapsFiles.save(root, MapsFiles.places(root).first { it.id == "market" }.copy(slot = 7))
        assertEquals(listOf("market", null), MapsFiles.slots(MapsFiles.places(root)).let { listOf(it[6]?.id, it[5]?.id) })
        assertEquals(listOf("home (2).json"), File(root.parentFile, "Trash").list()!!.toList())
        // a save is the place even when an older copy's date is the same or later (the phone, 23 Oct): its copies go
        put("market (2).json", Place("market", "My Market", 37.35, 22.58, "shop", slot = 9), System.currentTimeMillis() + 60_000)
        MapsFiles.save(root, Place("market", "My Market", 37.35, 22.58, "shop", slot = 8))
        assertEquals(8, MapsFiles.places(root).first { it.id == "market" }.slot)
        assertTrue(!File(dir, "market (2).json").exists())
        // all twelve full: the older stays saved, off Home
        listOf(6, 8, 9, 10, 11, 12).forEach { put("x$it.json", Place("x$it", "X$it", 38.0, 23.5, slot = it), day) }
        put("late.json", Place("late", "Late", 38.0, 23.5, slot = 1), day - 60_000)
        assertEquals(0, MapsFiles.places(root).first { it.id == "late" }.slot)
    }

    /** On the computer's graph (tools/maps-greece.sh), when it is there: Syntagma to the Acropolis, each way to go. */
    @Test fun route() {
        val dir = File(System.getProperty("user.home"), "Tetra/.System/Maps/Data/greece-gh")
        assumeTrue(File(dir, "properties").isFile)
        for (mode in Place.MODES) {
            val r = MapsRouting.route(dir, 37.9755, 23.7348, 37.9715, 23.7257, mode, el)
            println("$mode: ${r.metres.toInt()} m, ${r.millis / 1000} s, ${r.steps.size} steps: ${r.steps.take(3).map { it.text }}")
            assertTrue(r.metres in 800.0..3000.0)
            assertTrue(r.steps.isNotEmpty() && r.lats.size == r.lngs.size)
        }
        // Rolling: the wheelchair profile, when the graph has it — no steps, so never shorter than on foot
        if ("wheelchair" in MapsRouting.profiles(dir)) {
            val foot = MapsRouting.route(dir, 37.9755, 23.7348, 37.9715, 23.7257, "foot", el)
            val roll = MapsRouting.route(dir, 37.9755, 23.7348, 37.9715, 23.7257, "wheelchair", el)
            println("wheelchair: ${roll.metres.toInt()} m, ${roll.millis / 1000} s (foot ${foot.metres.toInt()} m)")
            assertTrue(roll.metres >= foot.metres - 1 && roll.millis >= foot.millis)
        }
        // the hand-written custom models give GraphHopper's own answers (its server on the same graph, 21 Oct)
        val gh = mapOf("car" to (471079.0 to 19950L), "foot" to (359126.0 to 230982L), "bike" to (463242.0 to 98452L))
        for ((mode, want) in gh) {
            val r = MapsRouting.route(dir, 37.9838, 23.7275, 40.6401, 22.9444, mode, el)
            println("$mode Athens–Thessaloniki: ${r.metres.toInt()} m, ${r.millis / 1000} s")
            assertEquals(want.first, r.metres, 2.0)
            assertEquals(want.second * (if (mode == "car") MapsRouting.CAR_TIME else 1.0), r.millis / 1000.0, 3.0)
        }
    }
}

class MapsProgressTest {
    // a straight 3-point line north along 23.72°E, a turn at the middle point
    private val r = Route("car", doubleArrayOf(37.970, 37.971, 37.972), doubleArrayOf(23.72, 23.72, 23.72), 222.0, 60_000,
        listOf(Step("Start", 0, 111.0, 0, ""), Step("Turn right", 2, 111.0, 1, ""), Step("Arrive", 4, 0.0, 2, "")))
    private val cum = cumulative(r)

    @Test fun along() {
        val (p, i) = progress(r, cum, 37.9705, 23.7201)
        assertEquals(0, i)
        assertTrue(p.off in 5.0..12.0)            // ~9 m east of the line
        assertEquals(55.6, p.along, 1.0)
        assertEquals(1, p.next); assertEquals(55.6, p.toNext, 1.0)
        val (q, j) = progress(r, cum, 37.9715, 23.72, i)
        assertEquals(1, j); assertEquals(2, q.next); assertEquals(55.6, q.left, 1.0)
        assertTrue(progress(r, cum, 37.9715, 23.73).first.off > 800)
    }
}

class MapsImportTest {
    @Test fun takeout() {
        val csv = "Title,Note,URL,Tags,Comment\n" +
            "\"Ταβέρνα, \"\"Ο Νίκος\"\"\",,https://www.google.com/maps/place/x/data=!4m2!3m1!1s0x0:0x1!3d41.1381!4d24.8864,,\n" +
            "Φαρμακείο Χ,note,https://www.google.com/maps/place/Farmakeio/data=!4m2!3m1!1s0x14:0x22,,\n" +
            "Dropped,,\"https://www.google.com/maps/search/40.64,+22.94\",,\n"
        val es = MapsImport.csv("Want to go", csv)
        assertEquals(3, es.size)
        assertEquals("Ταβέρνα, \"Ο Νίκος\"", es[0].name); assertEquals(41.1381, es[0].lat!!, 1e-9)
        assertNull(es[1].lat); assertEquals("note", es[1].note)
        assertEquals(22.94, es[2].lng!!, 1e-9)
        val gj = """{"type":"FeatureCollection","features":[{"geometry":{"coordinates":[23.7257,37.9715],"type":"Point"},"properties":{"location":{"name":"Acropolis","address":"Athens"}}},
            {"geometry":{"coordinates":[0,0],"type":"Point"},"properties":{"google_maps_url":"http://maps.google.com/?cid=1","location":{"name":"Nowhere"}}}]}"""
        val g = MapsImport.geojson("Starred places", gj)
        assertEquals(2, g.size); assertEquals(37.9715, g[0].lat!!, 1e-9); assertNull(g[1].lat)
        // into Tetra: the unplaced one found by its exact name in the search list, the other missed
        val root = File(Files.createTempDirectory("tetra").toFile(), "Maps")
        val r = MapsImport.into(root, listOf(Poi("pharmacy", 41.13, 24.88, "Φαρμακείο Χ")), es + g)
        assertEquals(4, r.places); assertEquals(listOf("Nowhere (Starred places)"), r.missed)
        assertEquals(setOf("Want to go", "Starred places"), MapsFiles.lists(root).map { it.name }.toSet())
        assertEquals("🚩", MapsFiles.lists(root).first { it.name == "Want to go" }.emoji)
        // a second import adds nothing new
        assertEquals(4, MapsImport.into(root, emptyList(), es.take(1) + g.take(1)).places + 2)
        assertEquals(4, MapsFiles.places(root).size)
    }
}

class MapsWarningTest {
    /** At 120 km/h (33 m/s) a turn is warned at 3 km, at ~660 m and at ~230 m; in town at 500 m and 120 m (a street early); each once. */
    @Test fun warningsFollowTheSpeed() {
        var said = emptySet<String>()
        val heard = ArrayList<Pair<Int, String>>()
        for (d in 4000 downTo 0 step 30) dueWarning(4, d.toDouble(), 33.0, true, said)?.let { heard += d to it; said = said + "${it}4" }
        assertEquals(listOf("e", "f", "n"), heard.map { it.second })
        assertTrue(heard[0].first in 3100..3200); assertTrue(heard[1].first in 630..660); assertTrue(heard[2].first in 200..231)
        said = emptySet(); heard.clear()
        for (d in 1500 downTo 0 step 10) dueWarning(1, d.toDouble(), 12.0, true, said)?.let { heard += d to it; said = said + "${it}1" }
        assertEquals(listOf(500 to "f", 120 to "n"), heard)
        assertEquals(2.0 to "km", spokenDistance(2140.0, true)); assertEquals(650.0 to "m", spokenDistance(660.0, true))
        assertEquals(1.5 to "km", spokenDistance(1400.0, true))
        // an exit whose lane begins 300 m before it: "take the exit" there, where the broken line starts
        said = emptySet(); heard.clear()
        for (d in 4000 downTo 0 step 10) dueWarning(5, d.toDouble(), 33.0, true, said, begins = 300.0)?.let { heard += d to it; said = said + "${it}5" }
        assertEquals(listOf("e", "f", "n"), heard.map { it.second }); assertEquals(320, heard[2].first)
    }
}

class MapsMotorwayTest {
    /** Σχηματάρι to Ναύπλιο on the computer's graph, when it is there: the on-ramp to Olympia Odos names the road it leads onto. */
    @Test fun aRampSaysWhereItLeads() {
        val dir = File(System.getProperty("user.home"), "Tetra/.System/Maps/Data/greece-gh")
        assumeTrue(File(dir, "properties").isFile)
        val r = MapsRouting.route(dir, 37.346366, 22.580585, 37.565923, 22.806878, "car", el)
        r.steps.forEach { println("${it.sign}\t${it.metres.toInt()} m\t${it.text}\t[${it.ref}] ${it.street}") }
        assertTrue(r.steps.none { it.metres > 20_000 && it.street.isEmpty() })
    }
}

class MapsGreeklishTest {
    @Test fun greeklishFindsGreek() {
        val all = listOf(Poi("village", 37.36, 22.44, "Κρεώνας", "Kreonas"), Poi("village", 37.21, 22.55, "Σκάλα"),
            Poi("primary_school", 37.27, 22.58, "Δημοτικό Σχολείο Αγίου Λουκά"), Poi("city", 41.13, 24.88, "Ξάνθη", "Xanthi"))
        for (q in listOf("krewnas", "Kreonas", "κρεωνας", "Κρεώνας")) assertEquals(q, "Κρεώνας", MapsSearch.search(all, q, 38.0, 23.0).firstOrNull()?.name)
        assertEquals("Σκάλα", MapsSearch.search(all, "skala", 38.0, 23.0).firstOrNull()?.name)
        assertEquals("Δημοτικό Σχολείο Αγίου Λουκά", MapsSearch.search(all, "agiou louka", 38.0, 23.0).firstOrNull()?.name)
        assertEquals("Ξάνθη", MapsSearch.search(all, "xanthi", 38.0, 23.0).firstOrNull()?.name)
    }
}

class MapsImportZipTest {
    /** A Takeout zip, read as a stream: the lists and starred places taken, the rest (a big video) passed over. */
    @Test fun zipStreamsAndKeepsOnlyTheLists() {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("Takeout/YouTube and YouTube Music/videos/a.mp4")); z.write(ByteArray(2_000_000)); z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("Takeout/Saved/Want to go.csv")); z.write("Title,Note,URL\nA,,\"https://www.google.com/maps/search/40.64,+22.94\"\n".toByteArray()); z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("Takeout/Maps (your places)/Saved Places.json"))
            z.write("""{"features":[{"geometry":{"coordinates":[23.7,37.9]},"properties":{"location":{"name":"B"}}}]}""".toByteArray()); z.closeEntry()
        }
        val es = MapsImport.read("takeout-1.zip", out.toByteArray().inputStream())
        assertEquals(listOf("Want to go" to "A", "Saved Places" to "B"), es.map { it.list to it.name })
    }
}

class MapsTakeoutRunTest {
    /** Opt-in (TETRA_TAKEOUT=<zip> TETRA_IMPORT_OUT=<dir>): a real Takeout imported into a folder of its own, with the computer's search list and addresses. */
    @Test fun importARealTakeout() {
        val zip = System.getenv("TETRA_TAKEOUT"); val out = System.getenv("TETRA_IMPORT_OUT")
        assumeTrue(zip != null && out != null)
        val maps = File(System.getProperty("user.home"), "Tetra/.System/Maps/Data")
        val root = File(out!!).apply { mkdirs() }
        listOf("greece-places.tsv", MapsSearch.EOPYY, "greece-addresses.tsv").forEach { n -> File(maps, n).takeIf { it.isFile }?.copyTo(File(root, n), overwrite = true) }
        File(root, "greece.pmtiles").createNewFile() // a map's lists are read beside its map
        val entries = File(zip!!).inputStream().use { MapsImport.read(File(zip).name, it) }
        println("entries: ${entries.size} " + entries.groupingBy { it.list }.eachCount())
        val r = MapsImport.into(root, MapsSearch.load(root), entries)
        println("placed ${r.places}, lists ${r.lists}, missed ${r.missed.size}: ${r.missed}")
    }
}

class MapsLimitFerryTest {
    /** On the computer's graph, when it is there: a motorway's limit, and the ferry to Samothraki said as a ferry. */
    @Test fun limitsAndFerries() {
        val dir = File(System.getProperty("user.home"), "Tetra/.System/Maps/Data/greece-gh")
        assumeTrue(File(dir, "properties").isFile)
        // Egnatia near Kavala, and Panepistimiou in Athens
        val r = MapsRouting.route(dir, 41.149912, 24.146884, 40.475293, 25.473550, "car", el)
        // along the route to Samothraki: the limits known on the way (on a road, not beside it)
        val limits = (0 until r.lats.size step maxOf(1, r.lats.size / 40)).map { MapsRouting.limitAt(dir, r.lats[it], r.lngs[it]) }
        println("limits: $limits")
        assertTrue(limits.count { it != null } > limits.size / 3)
        val ferry = r.steps.filter { it.text.contains("πλοίο") }
        println("ferry: " + ferry.map { "${it.text} ${it.metres.toInt()} m" })
        assertTrue(ferry.isNotEmpty() && ferry.all { it.metres > 30_000 })
    }
}

class MapsAlternativesTest {
    /** Άγιος Λουκάς to the 2nd school of Κρεώνας, beside Google's two (21 Oct): the motorway and the local roads, no tracks, fastest first. */
    @Ignore("its places were made up before publishing; put your own back locally to run it") @Test fun twoWaysThere() {
        val dir = File(System.getProperty("user.home"), "Tetra/.System/Maps/Data/greece-gh")
        assumeTrue(File(dir, "properties").isFile)
        val rs = MapsRouting.routes(dir, 37.277768, 22.585572, 37.3713423, 22.4651517, "car", el)
        rs.forEach { println("${it.millis / 60000} min ${(it.metres / 100).toInt() / 10.0} km ${it.roads}") }
        assertTrue(rs.size >= 2 && rs.none { it.roads == "track" })
        assertEquals(rs.sortedBy { it.millis }, rs)
        assertTrue(rs.any { it.roads == "motorway" } && rs.any { it.roads != "motorway" })
        // the tolls on each: the motorway passes OSM's toll stations, the local roads none
        val all = MapsSearch.load(File(System.getProperty("user.home"), "Tetra/.System/Maps/Data"))
        assumeTrue(all.any { it.kind == "toll" })
        // (OSM has no toll station near Σχηματάρι or Οινόφυτα: the local roads have none, the motorway none known)
        assertTrue(rs.all { r -> tollsOn(r, cumulative(r), all).isEmpty() })
        // Σχηματάρι to Ναύπλιο: Ολυμπία Οδός's at Ελευσίνα and Μορέας' on the Α7 are on the way
        val far = MapsRouting.route(dir, 37.346366, 22.580585, 37.565923, 22.806878, "car", el)
        val t = tollsOn(far, cumulative(far), all)
        println("Σχηματάρι → Ναύπλιο tolls: ${t.map { "${it.first.name} (${it.first.more}) at ${(it.second / 1000).toInt()} km" }}")
        assertTrue(t.size >= 2 && t.any { "Ελευσίνας" in it.first.name })
    }
}

class MapsKindInTownTest {
    /** Words that name a town with a pill: that kind in the town, nearest first, not shops with the town in their name. */
    @Test fun aKindInATown() {
        val all = listOf(Poi("village", 37.2777, 22.5878, "Άγιος Λουκάς"), Poi("supermarket", 37.2758, 22.5880, ""),
            Poi("supermarket", 37.3492, 22.5846, "My Market"), Poi("supermarket", 37.98, 23.73, "Σούπερ Μάρκετ Άγιος Λουκάς"))
        val r = MapsSearch.search(all, "agios loukas", 37.98, 23.73, "supermarket")
        assertEquals(listOf(37.2758, 37.3492), r.take(2).map { it.lat })
    }
}

class MapsShareTest {
    /** Mine and the map's additions out as GeoJSON and into another Tetra: each where it belongs, nothing twice. */
    @Test fun exportAndImport() {
        val a = Files.createTempDirectory("share-a").toFile()
        val work = MapsFiles.saveList(a, PlaceList("w", "Work", "💼"))
        MapsFiles.save(a, Place("s1", "Δημοτικό Σχολείο Πύργου", 37.3505, 22.4857, "school", slot = 2, lists = listOf(work.id), label = "Πύργος"))
        MapsShare.add(a, Poi("disabled_parking", 41.1380, 24.8880, "ΑμεΑ", more = "Κεντρική πλατεία"))
        MapsShare.add(a, Poi("supermarket", 37.2758, 22.5880, "Μίνι μάρκετ"))
        val json = MapsShare.export(MapsFiles.places(a), MapsFiles.lists(a), MapsSearch.added(a))
        val b = Files.createTempDirectory("share-b").toFile()
        assertEquals(MapsShare.Imported(1, 2, 0), MapsShare.import(b, json))
        assertEquals(MapsShare.Imported(0, 0, 3), MapsShare.import(b, json)) // again: nothing twice
        val p = MapsFiles.places(b).single()
        assertEquals("Πύργος", p.label); assertEquals("school", p.icon); assertEquals(0, p.slot) // a slot is one's own Home
        assertEquals(listOf("Work"), MapsFiles.lists(b).filter { it.id in p.lists }.map { it.name })
        assertEquals(setOf("disabled_parking", "supermarket"), MapsSearch.added(b).map { it.kind }.toSet())
        MapsShare.remove(b, MapsSearch.added(b).first { it.kind == "supermarket" })
        assertEquals(listOf("disabled_parking"), MapsSearch.added(b).map { it.kind })
    }
}

class StillDetectorTest {
    @Test fun standingAndDriving() {
        val r = java.util.Random(7)
        val d = StillDetector()
        assertNull(d.still())
        // standing, engine idling: ±0.03 m/s²
        for (i in 0 until 100) d.add(i * 25_000_000L, (r.nextGaussian() * 0.03).toFloat(), (r.nextGaussian() * 0.03).toFloat(), (r.nextGaussian() * 0.03).toFloat())
        assertEquals(true, d.still())
        // driving on a tunnel's road: ±0.6 m/s²
        for (i in 100 until 200) d.add(i * 25_000_000L, (r.nextGaussian() * 0.6).toFloat(), (r.nextGaussian() * 0.6).toFloat(), (r.nextGaussian() * 0.4).toFloat())
        assertEquals(false, d.still())
    }
}

/**
 * Routes with edge cases, on the computer's graph when it is there (the user, 21 Oct: "try deferent rutes that have edge cases
 * to find bugs"): each is routed, then checked as the app uses it — steps said, progress along it, tolls, the voice's
 * warnings — and whatever looks wrong is printed; the test fails on anything thrown that a user would see as a crash.
 */
class MapsEdgeCaseTest {
    private data class Case(val name: String, val from: Pair<Double, Double>, val to: Pair<Double, Double>, val mode: String = "car", val expectRoute: Boolean = true)

    @Test fun edgeCases() {
        val dir = File(System.getProperty("user.home"), "Tetra/.System/Maps/Data/greece-gh")
        assumeTrue(File(dir, "properties").isFile)
        val all = MapsSearch.load(File(System.getProperty("user.home"), "Tetra/.System/Maps/Data"))
        val cases = listOf(
            Case("Άγιος Λουκάς → Χαλκίδα (time ~30 min)", 37.277768 to 22.585572, 38.462274 to 23.594611),
            Case("same point", 37.2777 to 22.5855, 37.2777 to 22.5855),
            Case("one metre apart", 37.27770 to 22.58550, 37.27771 to 22.58550),
            Case("from the sea off Πειραιάς", 37.90 to 23.55, 37.9420 to 23.6465),
            Case("to Σόφια, outside the map", 41.1381 to 24.8864, 42.6977 to 23.3219, expectRoute = false),
            Case("ferry Πειραιάς → Αίγινα", 37.9420 to 23.6465, 37.7465 to 23.4280),
            Case("Αλεξανδρούπολη → Καλαμάτα", 40.8457 to 25.8739, 37.0389 to 22.1142),
            Case("Ρίο → Αντίρριο bridge", 38.3115 to 21.7880, 38.3800 to 21.7680),
            Case("Ηράκλειο → Χανιά", 35.3387 to 25.1442, 35.5138 to 24.0180),
            Case("Κέρκυρα → Ηγουμενίτσα (ferry)", 39.6243 to 19.9217, 39.5036 to 20.2650),
            Case("Ξάνθη old town, one-ways", 41.1440 to 24.8890, 41.1365 to 24.8836),
            Case("Μέτσοβο → Βωβούσα, mountains", 39.7700 to 21.1830, 39.9370 to 21.0480),
            Case("Καστελλόριζο → Ρόδος", 36.1497 to 29.5940, 36.4349 to 28.2176),
            Case("bike on the Egnatia's line", 40.9709 to 24.3373, 41.0300 to 24.6000, "bike"),
            Case("on foot across Αθήνα", 37.9755 to 23.7348, 38.0350 to 23.7880, "foot"),
            Case("wheelchair through Πλάκα's steps", 37.9715 to 23.7300, 37.9740 to 23.7255, "wheelchair"),
        )
        val problems = ArrayList<String>()
        for (c in cases) {
            val t0 = System.nanoTime()
            val rs = runCatching { MapsRouting.routes(dir, c.from.first, c.from.second, c.to.first, c.to.second, c.mode, el) }
            val ms = (System.nanoTime() - t0) / 1_000_000
            rs.onFailure { e ->
                println("✗ ${c.name}: ${e.javaClass.simpleName}: ${e.message} ($ms ms)")
                if (e !is IllegalStateException) problems += "${c.name}: ${e.javaClass.simpleName} ${e.message}"
                else if (c.expectRoute) problems += "${c.name}: no route (${e.message})"
            }
            rs.onSuccess { list ->
                val r = list.first()
                val cum = cumulative(r)
                val tolls = tollsOn(r, cum, all)
                println("✓ ${c.name}: ${list.size} way(s), ${(r.metres / 100).toInt() / 10.0} km, ${r.millis / 60000} min, ${r.roads}, ${r.steps.size} steps, tolls ${tolls.size}, $ms ms")
                if (!c.expectRoute) problems += "${c.name}: a route where none should be"
                if (r.lats.size < 2 && r.metres > 1) problems += "${c.name}: ${r.lats.size} points for ${r.metres} m"
                r.steps.filter { it.text.isBlank() }.forEach { problems += "${c.name}: a step without words at ${it.at}" }
                r.steps.filter { it.metres > 20_000 && it.street.isEmpty() && "πλοίο" !in it.text }.forEach { problems += "${c.name}: ${(it.metres / 1000).toInt()} km on '${it.text}' with no road named" }
                if (r.steps.lastOrNull()?.sign != 4) problems += "${c.name}: last step is not the arrival (${r.steps.lastOrNull()?.text})"
                // the end of the route reads as arrived; its middle has a next turn; warnings come in order
                if (r.lats.size >= 2) {
                    val (end, _) = progress(r, cum, r.lats.last(), r.lngs.last())
                    if (end.left > 5) problems += "${c.name}: ${end.left.toInt()} m left at the route's end"
                    val mid = r.lats.size / 2
                    val (p, _) = progress(r, cum, r.lats[mid], r.lngs[mid])
                    if (p.off > 1) problems += "${c.name}: ${p.off.toInt()} m off the route on its own line"
                    if (p.next !in r.steps.indices) problems += "${c.name}: next step ${p.next} out of ${r.steps.size}"
                }
                if (r.millis <= 0 && r.metres > 10) problems += "${c.name}: no time for ${r.metres} m"
                if (c.mode == "car" && r.metres > 1000 && r.metres / (r.millis / 3_600_000.0) / 1000 > 130) problems += "${c.name}: average over 130 km/h"
                list.drop(1).forEach { alt -> if (alt.millis < r.millis) problems += "${c.name}: an alternative faster than the first" }
            }
        }
        println("\nPROBLEMS (${problems.size}):"); problems.forEach { println(" - $it") }
        assertTrue(problems.filter { "Exception" in it || "Error" in it }.joinToString("\n"), problems.none { "Exception" in it || "Error" in it })
    }
}

class MapsCheckTest {
    @Test fun placingTakesItOffTheList() {
        val root = Files.createTempDirectory("check").toFile()
        File(root, MapsCheck.FILE).writeText("a1\t41.137\t24.891\tΞάνθη\tΠλάτωνος 44, Ξάνθη\tΔημιουργία θέσης\thttps://diavgeia.gov.gr/decision/view/a1\t2012-01-25\n" +
            "b2\t41.139\t24.889\tΞάνθη\tΘερμοπυλών, Ξάνθη\tΔημιουργία θέσης\thttps://diavgeia.gov.gr/decision/view/b2\t2013-09-16\nbroken line\n")
        assertEquals(listOf("a1", "b2"), MapsCheck.load(root).map { it.id })
        assertEquals("Ξάνθη", MapsCheck.load(root).first().municipality)
        MapsCheck.done(root, "a1")
        assertEquals(listOf("b2"), MapsCheck.load(root).map { it.id })
    }
}

/** Trips (A → B → C) as files in Tetra/Maps/Trips. */
class MapsTripTest {
    @Test fun trips() {
        val root = Files.createTempDirectory("trips").toFile()
        val t = Trip("t1", "Σχολεία", listOf(Place("", "Πύργος", 37.36, 22.58), Place("", "Πύλη", 37.32, 22.53), Place("", "", 37.30, 22.60)))
        MapsFiles.saveTrip(root, t)
        File(root, "Trips/bad.json").writeText("{\"stops\":[]}")
        val back = MapsFiles.trips(root)
        assertEquals(1, back.size)
        assertEquals(listOf("Πύργος", "Πύλη", "Stop C"), back[0].stops.map { it.name })
        assertEquals(22.53, back[0].stops[1].lng, 1e-9)
        MapsFiles.deleteTrip(root, back[0])
        assertTrue(MapsFiles.trips(root).isEmpty())
    }

    /** A moving car's new route goes on the way it faces, not back (the user, 23 Oct, near Σκάλα: "a new route" every 11 s for 3 km). */
    @Test fun headed() {
        val dir = File(System.getProperty("user.home"), "Tetra/.System/Maps/Data/greece-gh")
        assumeTrue(File(dir, "properties").isFile)
        fun bearing(r: Route): Double { // of the route's first ~50 m
            var k = 1; while (k < r.lats.size - 1 && metresBetween(r.lats[0], r.lngs[0], r.lats[k], r.lngs[k]) < 50) k++
            val y = Math.sin(Math.toRadians(r.lngs[k] - r.lngs[0])) * Math.cos(Math.toRadians(r.lats[k]))
            val x = Math.cos(Math.toRadians(r.lats[0])) * Math.sin(Math.toRadians(r.lats[k])) -
                Math.sin(Math.toRadians(r.lats[0])) * Math.cos(Math.toRadians(r.lats[k])) * Math.cos(Math.toRadians(r.lngs[k] - r.lngs[0]))
            return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
        }
        fun apart(a: Double, b: Double) = Math.abs(((a - b) % 360 + 540) % 360 - 180)
        // from Σκάλα to the school of Άγιος Λουκάς; then from points along that way, facing back where it came from
        val plain = MapsRouting.route(dir, 37.2115, 22.5470, 37.2777, 22.5877, "car", el)
        var turned = 0; var tried = 0
        for (k in listOf(plain.lats.size / 5, plain.lats.size / 3, plain.lats.size / 2, plain.lats.size * 2 / 3)) {
            val lat = (plain.lats[k] + plain.lats[k + 1]) / 2; val lng = (plain.lngs[k] + plain.lngs[k + 1]) / 2 // between two points: on the road
            val on = MapsRouting.route(dir, lat, lng, 37.2777, 22.5877, "car", el)
            val back = (bearing(on) + 180) % 360
            val t0 = System.nanoTime()
            val headed = MapsRouting.route(dir, lat, lng, 37.2777, 22.5877, "car", el, heading = back)
            println("at $k: ${on.metres.toInt()} m at ${bearing(on).toInt()}°; facing ${back.toInt()}°: ${headed.metres.toInt()} m at " +
                "${bearing(headed).toInt()}° in ${(System.nanoTime() - t0) / 1_000_000} ms")
            tried++; if (apart(bearing(headed), back) < 60) turned++
        }
        assertTrue("$turned of $tried went on the way they faced", turned >= tried / 2)
        val t1 = System.nanoTime() // too far for the headed search: the shortcuts' route, at once
        val far = MapsRouting.route(dir, 37.9838, 23.7275, 40.6401, 22.9444, "car", el, heading = 0.0)
        println("Athens–Thessaloniki facing north: ${far.metres.toInt()} m in ${(System.nanoTime() - t1) / 1_000_000} ms")
        assertTrue(far.metres in 400_000.0..600_000.0)
    }
}

class MapsBendTest {
    /** A way north then east every 10 m, turning through a curve of radius [radius] m. */
    private fun way(radius: Double, steps: List<Step> = emptyList()): Route {
        val lats = ArrayList<Double>(); val lngs = ArrayList<Double>()
        val m = 1 / 111_320.0; val e = m / Math.cos(Math.toRadians(38.0))
        for (i in 0..20) { lats += 38.0 + i * 10 * m; lngs += 23.5 } // 200 m north
        val cy = 38.0 + 200 * m; val cx = 23.5 + radius * e
        val arc = (Math.PI / 2 * radius / 10).toInt().coerceAtLeast(1)
        for (k in 1..arc) { val a = Math.PI - k * (Math.PI / 2) / arc; lats += cy + Math.sin(a) * radius * m; lngs += cx + Math.cos(a) * radius * e }
        val y = lats.last(); val x = lngs.last()
        for (i in 1..20) { lats += y; lngs += x + i * 10 * e } // 200 m east
        return Route("car", lats.toDoubleArray(), lngs.toDoubleArray(), 0.0, 0L, steps)
    }

    @Test fun sharpBendsOnly() {
        val tight = way(20.0)
        val bends = bendsOn(tight, cumulative(tight))
        assertEquals(1, bends.size); assertTrue(bends[0].right); assertEquals(215.0, bends[0].along, 30.0)
        val wide = way(250.0) // a gentle curve: nothing to say
        assertTrue(bendsOn(wide, cumulative(wide)).isEmpty())
        val turn = way(20.0, listOf(Step("Στρίψτε δεξιά", 2, 0.0, 21, ""))) // a turn the directions say already
        assertTrue(bendsOn(turn, cumulative(turn)).isEmpty())
        // said once, from 150 m before at town speed, never within the last 30 m
        assertEquals(null, dueBend(bends, bends[0].along - 200, 10.0, emptySet()))
        assertEquals(0, dueBend(bends, bends[0].along - 140, 10.0, emptySet()))
        assertEquals(null, dueBend(bends, bends[0].along - 140, 10.0, setOf("b0")))
        assertEquals(null, dueBend(bends, bends[0].along - 20, 10.0, emptySet()))
    }
}

class MapsJunctionTest {
    /** On the computer's graph: a turn's crossing from the graph — the way in behind, the way on another road. */
    @Test fun crossings() {
        val dir = File(System.getProperty("user.home"), "Tetra/.System/Maps/Data/greece-gh")
        assumeTrue(File(dir, "properties").isFile)
        val drives = listOf("Δράμα" to doubleArrayOf(41.1440, 24.1300, 41.1570, 24.1600), "Ομόνοια" to doubleArrayOf(37.9890, 23.7200, 37.9800, 23.7330),
            "Χαλκίδα" to doubleArrayOf(38.4700, 23.5900, 38.4500, 23.6200),
            // where the user took the street next to theirs among 5–6 (23 Oct): across the village each way
            "5 Δρόμοι" to doubleArrayOf(41.1554, 24.1517, 41.1529, 24.1550), // Δράμα's roundabout of five roads (the user's picture, 23 Oct)
            "Νικήσιανη" to doubleArrayOf(40.9430, 24.1350, 40.9540, 24.1500), "Νικήσιανη " to doubleArrayOf(40.9540, 24.1350, 40.9430, 24.1500))
        var busy = 0; var rings = 0; var all = 0
        for ((name, d) in drives) {
            val r = MapsRouting.route(dir, d[0], d[1], d[2], d[3], "car", el)
            for (s in r.steps) {
                val j = s.junction ?: continue
                all++; if (j.busy) busy++; if (j.roundabout) rings++
                println("$name: ${s.text} — ${j.arms.map { it.toInt() }}, taken ${j.arms[j.taken].toInt()}${if (j.roundabout) " (roundabout)" else ""}")
                assertTrue("the way in is behind", Math.abs(Math.abs(j.arms[j.way]) - 180) < 40)
                assertTrue("the way on is not back", j.taken != j.way)
                // the way on on the side the directions say (GraphHopper's sign: + right, − left) — at a crossing of several
                // nodes the picture shows the way through it all, which can differ from its first turn
                if (j.arms.size >= 5) continue
                if (!j.roundabout && s.sign in 1..3) assertTrue("${s.text}: right", j.arms[j.taken] > 0)
                if (!j.roundabout && s.sign in -3..-1) assertTrue("${s.text}: left", j.arms[j.taken] < 0)
            }
        }
        println("$all crossings, $busy busy, $rings roundabouts")
        assertTrue(all >= 5 && busy >= 1)
    }
}

class MapsLanesTest {
    @Test fun lanesToTake() {
        val exit = MapsLanes.parse("through|through|slight_right")
        assertEquals(setOf(2), MapsLanes.use(exit, 7)) // keep right: the exit lane
        assertEquals(setOf(0, 1), MapsLanes.use(exit, -7)) // keep left: the road goes on
        assertEquals("από τη δεξιά λωρίδα", MapsLanes.words(Lanes(exit, setOf(2)), el))
        assertEquals("από τις 2 αριστερές λωρίδες", MapsLanes.words(Lanes(exit, setOf(0, 1)), el))
        val corinth = MapsLanes.parse("slight_left|slight_left|through|through") // Α7 to Τρίπολη leaves on the left
        assertEquals(setOf(0, 1), MapsLanes.use(corinth, -7))
        assertEquals("from the 2 left lanes", MapsLanes.words(Lanes(corinth, setOf(0, 1)), en))
        assertEquals(listOf(listOf("left"), listOf("through"), listOf("through", "right")), MapsLanes.parse("left|none|through;right"))
        assertEquals("από τη μεσαία λωρίδα", MapsLanes.words(Lanes(MapsLanes.parse("left|through|right"), setOf(1)), el))
        assertNull(MapsLanes.words(Lanes(exit, setOf(0, 1, 2)), el)) // all of them: nothing to say
        assertTrue(MapsLanes.use(MapsLanes.parse("left|through"), 2).isEmpty()) // a right turn the lanes do not show
    }

    /** On the computer's graph and lanes file: the turns where the user met them (23 Oct). */
    @Test fun lanesOnRealRoutes() {
        val dir = File(System.getProperty("user.home"), "Tetra/.System/Maps/Data/greece-gh")
        assumeTrue(File(dir, "properties").isFile && File(dir.parentFile, "greece-lanes.tsv").isFile)
        val tripoli = MapsRouting.route(dir, 37.9838, 23.7275, 37.5100, 22.3720, "car", el)
        val corinth = tripoli.steps.first { "Τρίπολη" in it.text && it.sign == -7 }
        println("Κόρινθος: ${corinth.text} — ${corinth.lanes}")
        assertEquals(setOf(0, 1), corinth.lanes?.use)
        // and its picture: the fork, Τρίπολη to the left (the user: "it says left — let it show it too")
        val fork = corinth.junction
        println("Κόρινθος fork: $fork")
        assertTrue(fork != null && fork.busy && fork.arms[fork.taken] < 0)
        assertTrue(corinth.lanes!!.begins in 100.0..700.0) // where the exit's lanes begin, for "take the exit" there
        val elaionas = MapsRouting.route(dir, 37.2777, 22.5877, 38.4140, 23.2270, "car", el)
        val with = elaionas.steps.filter { it.lanes != null }
        with.forEach { println("Ελαιώνας: ${MapsLanes.said(it, el)} — ${it.lanes}") }
        assertTrue(with.isNotEmpty())
    }
}

/**
 * A drive replayed (the user, 23 Oct: "a test that replays a trace"): its fixes one by one through [guide], as the car's
 * screen takes them, a new route from the fix where it says so — with the car's heading or, to compare, without.
 */
class MapsReplayTest {
    private val dir = File(System.getProperty("user.home"), "Tetra/.System/Maps/Data/greece-gh")

    data class Played(val reroutes: Int, val said: List<String>, val arrived: Boolean)

    private fun replay(d: MapsTrace.Drive, heading: Boolean): Played {
        val target = Place("", d.name, d.lat, d.lng, "pin", mode = d.mode)
        fun route(f: MapsTrace.Fix) = MapsRouting.route(dir, f.lat, f.lng, d.lat, d.lng, d.mode, el,
            heading = if (heading && f.speed > 2.5 && f.bearing >= 0) f.bearing else Double.NaN)
        var r = route(d.fixes.first())
        var n = Nav(target, d.mode, r, cumulative(r), d.fixes.first().time)
        var reroutes = 0; val said = ArrayList<String>()
        for (f in d.fixes) {
            val g = guide(n, f.lat, f.lng, maxOf(0.0, f.speed), f.time, true, false)
            if (g.off) {
                n = n.copy(offFixes = n.offFixes + 1)
                if (g.reroute) { r = route(f); n = Nav(target, d.mode, r, cumulative(r), f.time); reroutes++; said += "Νέα διαδρομή" }
                continue
            }
            if (g.arrived) return Played(reroutes, said, true)
            g.words(el, d.mode == "car")?.let { said += it }
            g.bend?.let { said += MapsPrefs.bendWords(el, n.bends[it].right) }
            n = n.copy(index = g.index, p = g.p, offFixes = 0, said = g.said(n.said))
        }
        return Played(reroutes, said, false)
    }

    /** A drive along [r] at [ms] m/s, a fix a second, each facing the way on. */
    private fun drive(r: Route, ms: Double, to: Place): MapsTrace.Drive {
        val cum = cumulative(r); val fixes = ArrayList<MapsTrace.Fix>(); var at = 0.0; var i = 0; var t = 1_000_000L
        while (at < cum.last()) {
            while (i < cum.size - 2 && cum[i + 1] < at) i++
            val k = if (cum[i + 1] > cum[i]) (at - cum[i]) / (cum[i + 1] - cum[i]) else 0.0
            val lat = r.lats[i] + k * (r.lats[i + 1] - r.lats[i]); val lng = r.lngs[i] + k * (r.lngs[i + 1] - r.lngs[i])
            fixes += MapsTrace.Fix(t, lat, lng, ms, (bearing(r.lats[i], r.lngs[i], r.lats[i + 1], r.lngs[i + 1]) + 360) % 360, 5.0)
            at += ms; t += 1000
        }
        return MapsTrace.Drive("synthetic", to.lat, to.lng, "car", to.name, fixes)
    }

    /** Directions to Άγιος Λουκάς from Σκάλα, the driver going towards Πύλη instead (23 Oct: "a new route" every 11 s). */
    @Test fun aDriverWhoGoesElsewhere() {
        assumeTrue(File(dir, "properties").isFile)
        val elsewhere = MapsRouting.route(dir, 37.2115, 22.5470, 37.2180, 22.4100, "car", el) // Σκάλα → west, to Πύλη
        val d = drive(elsewhere, 15.0, Place("", "Δημοτικό Σχολείο Αγίου Λουκά", 37.2777, 22.5877, "pin"))
        val plain = replay(d, heading = false); val headed = replay(d, heading = true)
        println("${d.fixes.size} s of driving away: without the heading ${plain.reroutes} new routes, with it ${headed.reroutes}")
        assertTrue(headed.reroutes <= plain.reroutes)
    }

    /** Directions followed: the turns said in order, no new route, and there at the end. */
    @Test fun aDriverWhoFollows() {
        assumeTrue(File(dir, "properties").isFile)
        val to = Place("", "Δημοτικό Σχολείο Αγίου Λουκά", 37.2777, 22.5877, "pin")
        val r = MapsRouting.route(dir, 37.2115, 22.5470, to.lat, to.lng, "car", el)
        val played = replay(drive(r, 14.0, to), heading = true)
        played.said.forEach { println("  $it") }
        assertEquals(0, played.reroutes)
        assertTrue(played.said.size >= r.steps.size - 2)
    }

    /** The phone's own traces, put in ~/Tetra/.System/Maps/Data/traces (adb pull …/files/logs/drive): each drive replayed and told. */
    @Test fun realTraces() {
        val traces = File(dir.parentFile, "traces").listFiles { f -> f.name.endsWith(".csv") }.orEmpty()
        assumeTrue(File(dir, "properties").isFile && traces.isNotEmpty())
        for (f in traces.sortedBy { it.name }) for (d in MapsTrace.read(f)) {
            if (d.fixes.size < 10) continue
            val played = replay(d, heading = true)
            println("${f.name} ${d.header}: ${d.fixes.size} fixes, ${played.reroutes} new routes, arrived ${played.arrived}")
            played.said.forEach { println("  $it") }
        }
    }
}

class MapsAddressTest {
    /** The user's house in Άγιος Λουκάς (23 Oct): no house there has a number, so its street and the village's postcode. */
    @Ignore("its places were made up before publishing; put your own back locally to run it") @Test fun aVillageWithoutNumbers() {
        val root = File(System.getProperty("user.home"), "Tetra/.System/Maps/Data")
        assumeTrue(File(root, "greece-addresses.tsv").isFile && File(root, "greece-streets.tsv").isFile)
        val pois = MapsSearch.load(root)
        assertEquals("Οδός Παραδείγματος, 12345 Άγιος Λουκάς", MapsAddresses.describe(root, pois, 37.27642, 22.58903))
        // a spot tapped on a road takes that road
        assertEquals("Πλατάνι, 12345 Άγιος Λουκάς", MapsAddresses.describe(root, pois, 37.27642, 22.58903, "Πλατάνι"))
    }

    /** A sync's copies of the parked car ("parking (2).json") leave one car: the newest; deleting a place takes its copies. */
    @Test fun oneCar() {
        val root = File(Files.createTempDirectory("tetra").toFile(), "Maps") // its Trash beside it, as in Tetra
        val dir = File(root, "Places").apply { mkdirs() }
        fun car(file: String, lat: Double, at: Long) = File(dir, file).apply {
            writeText("""{"id":"parking","name":"My car","lat":$lat,"lng":23.5,"icon":"parking"}"""); setLastModified(at)
        }
        car("parking (2).json", 38.1, 1_000_000L); car("parking (3).json", 38.3, 3_000_000L); car("parking.json", 38.2, 2_000_000L)
        File(dir, "home.json").writeText("""{"id":"home","name":"Home","lat":38.0,"lng":23.0}""")
        val places = MapsFiles.places(root)
        assertEquals(listOf("Home", "My car"), places.map { it.name })
        assertEquals(38.3, places.first { it.id == "parking" }.lat, 1e-9)
        assertEquals(setOf("home.json", "parking (3).json"), dir.list()!!.toSet())
        MapsFiles.delete(root, places.first { it.id == "parking" })
        assertEquals(setOf("home.json"), dir.list()!!.toSet())
        // to Tetra's Trash, never deleted
        assertEquals(setOf("parking.json", "parking (2).json", "parking (3).json"), File(root.parentFile, "Trash").list()!!.toSet())
    }
}

/**
 * Main roads (the user, 24 Oct, home → My Market and the phone's route to Σκάλα's school: "it always chooses the shorter
 * path … even if that means a road that falls off a cliff"), on the computer's graph when it is there.
 */
class MapsMainRoadsTest {
    private val dir = File(System.getProperty("user.home"), "Tetra/.System/Maps/Data/greece-gh")

    /** Kilometres of each road class along the way from → to, as the phone asks it. */
    private fun classes(from: Pair<Double, Double>, to: Pair<Double, Double>, main: Boolean): Map<String, Double> {
        val gh = MapsRouting.open(dir)
        val req = com.graphhopper.GHRequest(from.first, from.second, to.first, to.second).setProfile("car").setPathDetails(listOf("road_class"))
        if (main) req.hints.putObject(MapsRouting.MAIN_ROADS, true).putObject(com.graphhopper.util.Parameters.CH.DISABLE, true)
        val p = gh.route(req).best
        val km = HashMap<String, Double>()
        p.pathDetails["road_class"].orEmpty().forEach { d ->
            for (i in d.first until d.last) km.merge(d.value.toString(), metresBetween(p.points.getLat(i), p.points.getLon(i), p.points.getLat(i + 1), p.points.getLon(i + 1)) / 1000, Double::plus)
        }
        return km
    }

    @Ignore("its places were made up before publishing; put your own back locally to run it") @Test fun homeToMarket() {
        assumeTrue(File(dir, "properties").isFile)
        val home = 37.27639 to 22.58912; val market = 37.349324 to 22.584650; val skala = 37.212858 to 22.551289
        for ((name, to) in listOf("My Market" to market, "Σκάλα school" to skala)) for (main in listOf(false, true)) {
            val r = MapsRouting.route(dir, home.first, home.second, to.first, to.second, "car", el, main = main)
            val km = classes(home, to, main)
            println("$name ${if (main) "main roads" else "shortest"}: ${"%.1f".format(r.metres / 1000)} km, ${r.millis / 60_000} min, ${r.roads}; " +
                km.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${"%.1f".format(it.value)}" })
        }
        // below secondary: on 24 Oct shortest took 3.6 km of tertiary to My Market and 2 km of track to Σκάλα
        val small = { km: Map<String, Double> -> km.filterKeys { it !in setOf("motorway", "trunk", "primary", "secondary") }.values.sum() }
        for (to in listOf(market, skala)) assertTrue(small(classes(home, to, true)) < small(classes(home, to, false)))
        assertTrue((classes(home, skala, true)["track"] ?: 0.0) < 0.3)
    }
}

/** Maps of several countries (the map builder, 27 Oct): each <id>.pmtiles with its lists and roads beside it. */
class MapsPacksTest {
    /** A .pmtiles file holding only its header, with [b] west, south, east, north. */
    private fun pmtiles(f: File, vararg b: Double) {
        val h = java.nio.ByteBuffer.allocate(127).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        h.put("PMTiles".toByteArray()); h.put(3)
        b.forEachIndexed { i, v -> h.putInt(102 + 4 * i, Math.round(v * 1e7).toInt()) }
        f.writeBytes(h.array())
    }

    @Test fun twoCountries() {
        val root = File(Files.createTempDirectory("tetra").toFile(), "Maps").apply { mkdirs() }
        pmtiles(File(root, "greece.pmtiles"), 18.97, 34.59, 29.66, 41.75)
        pmtiles(File(root, "cyprus.pmtiles"), 31.0, 34.0, 35.0, 36.0)
        File(root, "cyprus.json").writeText("""{"name":"Cyprus","bounds":[32.2,34.5,34.7,35.8],"drive":"left"}""")
        File(root, "greece-places.tsv").writeText("city\t37.98\t23.73\tΑθήνα\tAthens\n")
        File(root, "cyprus-places.tsv").writeText("city\t35.17\t33.36\tΛευκωσία\tNicosia\n")
        File(root, "added-places.tsv").writeText("school\t37.27\t22.58\tΣχολείο\t\n")
        File(root, "lisbon-places.tsv").writeText("city\t38.72\t-9.14\tLisboa\t\n") // no map of its own: not read

        assertEquals(listOf("cyprus", "greece"), MapsFiles.ids(root))
        val (cy, gr) = MapsFiles.maps(root)
        assertEquals("Cyprus", cy.name); assertTrue(cy.driveLeft); assertEquals(32.2, cy.bounds!![0], 1e-9)
        assertEquals("Greece", gr.name); assertEquals(41.75, gr.bounds!![3], 1e-6) // from its own header
        assertEquals("cyprus-gh", MapsFiles.graphAt(root, 35.17, 33.36).name)
        assertEquals("greece-gh", MapsFiles.graphAt(root, 37.98, 23.73).name)
        assertEquals("cyprus-gh", MapsFiles.graphAt(root, 0.0, 0.0).name) // on no map: the first's, which says it is off the roads
        assertEquals(setOf("Αθήνα", "Λευκωσία", "Σχολείο"), MapsSearch.load(root).map { it.name }.toSet())

        File(root, "cyprus-gh").mkdirs(); File(root, "cyprus-gh/properties").writeText("x")
        MapsFiles.removeMap(root, "cyprus")
        assertEquals(listOf("greece"), MapsFiles.ids(root))
        assertEquals(setOf("added-places.tsv", "greece-places.tsv", "greece.pmtiles", "lisbon-places.tsv"), root.list()!!.toSet())
        assertTrue(File(root.parentFile, "Trash/cyprus-gh/properties").isFile) // to the Trash, so the phone and tablet follow
    }

    /** Across borders: each map's own roads to a crossing and on from there, the fastest crossing; a map's removal takes its crossings. */
    @Test fun acrossBorders() {
        val root = File(Files.createTempDirectory("tetra").toFile(), "Maps").apply { mkdirs() }
        pmtiles(File(root, "greece.pmtiles"), 18.97, 34.59, 29.66, 41.75)
        pmtiles(File(root, "macedonia.pmtiles"), 20.45, 40.85, 23.04, 42.37)
        pmtiles(File(root, "serbia.pmtiles"), 18.8, 42.2, 23.0, 46.2)
        pmtiles(File(root, "cyprus.pmtiles"), 31.0, 34.0, 35.0, 36.0)
        val gr = File(root, "greece-gh"); val w = testWords("en")
        // made-up times: a straight line at a kilometre a minute, an hour more through Niki
        val asked = ArrayList<String>()
        val seconds = { g: File, aLat: Double, aLng: Double, bLat: Double, bLng: Double ->
            asked += g.name; Math.hypot(aLat - bLat, (aLng - bLng) * Math.cos(Math.toRadians(aLat))) * 111 * 60 + (if (Math.abs(bLng - 21.418) < 0.01) 3600 else 0)
        }
        fun legs(toLat: Double, toLng: Double) = MapsFiles.legs(gr, 40.64, 22.94, toLat, toLng, w, seconds)
        assertEquals(listOf(MapsFiles.Leg(gr, 40.64, 22.94, 37.98, 23.73)), legs(37.98, 23.73)) // Athens: as before, nothing asked
        assertTrue(asked.isEmpty())
        assertTrue(runCatching { legs(41.99, 21.43) }.exceptionOrNull()?.message.orEmpty().startsWith("Tetra knows no way from Greece to Macedonia"))
        File(root, "greece_macedonia-crossings.tsv").writeText("41.133893\t22.548533\t\tmotorway_link\t1\n40.916288\t21.418277\t\ttrunk\t2\n41.127685\t22.551662\tΕύζωνοι\ttrunk\t1\n")
        assertEquals(3, MapsFiles.crossings(root, "macedonia", "greece").size)
        val skopje = legs(41.99, 21.43)
        assertEquals(listOf("greece-gh", "macedonia-gh"), skopje.map { it.dir.name })
        assertEquals(41.13, skopje[0].toLat, 0.01); assertEquals(skopje[0].toLat, skopje[1].fromLat, 0.0) // Evzonoi, not Niki
        // Bitola, inside Greece's bounds too: Greece's roads do not reach it, so across the border
        val bitola = MapsFiles.legs(gr, 40.64, 22.94, 41.03, 21.33, w) { g, a, b, c, d -> if (g.name == "greece-gh" && c == 41.03) null else seconds(g, a, b, c, d) }
        assertEquals(listOf("greece-gh", "macedonia-gh"), bitola.map { it.dir.name })
        // three countries: Thessaloniki to Belgrade through North Macedonia
        assertTrue(runCatching { legs(44.79, 20.45) }.exceptionOrNull()?.message.orEmpty().contains("Greece to Serbia"))
        File(root, "macedonia_serbia-crossings.tsv").writeText("42.234902\t21.703802\t\tmotorway\t1\n42.326463\t22.331514\tГолеш\ttertiary\t2\n")
        assertEquals(listOf("greece-gh" to 41.13, "macedonia-gh" to 42.23, "serbia-gh" to 44.79), legs(44.79, 20.45).map { it.dir.name to Math.round(it.toLat * 100) / 100.0 })
        assertTrue(runCatching { legs(35.17, 33.36) }.exceptionOrNull()?.message.orEmpty().contains("Cyprus"))
        MapsFiles.removeMap(root, "macedonia")
        assertEquals(setOf("cyprus.pmtiles", "greece.pmtiles", "serbia.pmtiles"), root.list()!!.toSet())
    }

    /** Legs joined as one route: the line at the shared point once, the crossing's "arrive" left out, the steps' points moved on. */
    @Test fun legsJoined() {
        fun leg(m: Double, lat: Double) = Route("car", doubleArrayOf(lat, lat + 1), doubleArrayOf(0.0, 1.0), m, (m * 100).toLong(),
            listOf(Step("go", 0, m, 0, "A"), Step("arrive", 4, 0.0, 1, "")))
        val r = MapsRouting.join(listOf(leg(100.0, 0.0), leg(50.0, 1.0)))
        assertEquals(listOf(0.0, 1.0, 2.0), r.lats.toList()); assertEquals(150.0, r.metres, 0.0); assertEquals(15_000L, r.millis)
        assertEquals(listOf(0 to 0, 0 to 1, 4 to 2), r.steps.map { it.sign to it.at })
    }
}

/**
 * Routes across borders on the maps' own graphs, when Files/Maps (or TETRA_MAPS) has Greece, North Macedonia and Bulgaria with
 * their crossings (the desktop map builder's): as the phone and the car ask for them.
 */
class MapsBorderTest {
    @Test fun acrossBorders() {
        val root = File(System.getenv("TETRA_MAPS") ?: (System.getProperty("user.home") + "/Tetra/Files/Maps"))
        assumeTrue(MapsFiles.crossings(root, "greece", "macedonia").isNotEmpty())
        val w = testWords("en")
        fun go(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Route {
            val t0 = System.currentTimeMillis()
            val r = MapsRouting.route(MapsFiles.graphAt(root, fromLat, fromLng), fromLat, fromLng, toLat, toLng, "car", w)
            println("$fromLat,$fromLng → $toLat,$toLng: ${r.metres.toInt()} m, ${r.millis / 60000} min, ${r.steps.size} steps, in ${System.currentTimeMillis() - t0} ms")
            return r
        }
        val skopje = go(40.6401, 22.9444, 41.9973, 21.4280) // Thessaloniki → Skopje, by Evzonoi
        assertTrue(skopje.metres in 200_000.0..250_000.0 && skopje.millis / 60000 < 200)
        assertTrue(skopje.lats.any { it > 41.2 } && skopje.steps.count { it.sign == 4 } == 1 && skopje.steps.size < 30) // no turning round at the border
        val back = go(41.9973, 21.4280, 40.6401, 22.9444)
        assertEquals(skopje.metres, back.metres, 20_000.0)
        assertEquals(470_553.0, go(40.6401, 22.9444, 37.9838, 23.7275).metres, 2_000.0) // Athens: Greece's own roads, as before
        if (MapsFiles.crossings(root, "bulgaria", "greece").isNotEmpty()) {
            val sofia = go(40.6401, 22.9444, 42.6977, 23.3219) // by Promachonas
            assertTrue(sofia.metres in 250_000.0..320_000.0)
        }
        val off = runCatching { go(40.6401, 22.9444, 44.79, 20.45) } // Belgrade: no map of Serbia here
        println("to Belgrade: ${off.exceptionOrNull()?.message}")
        assertTrue(off.isFailure)
    }
}

/**
 * Directions in every language Tetra has (the user, 27 Oct: "Why it's only Greek? … It shouldn't be hard to be in all the
 * languages we have"): Tetra's own words from the string resources, GraphHopper's turn words beside them (Tetra's own where
 * it has none), and miles with feet or yards where the phone's region measures so.
 */
class MapsWordsTest {
    @Test fun greekAsBefore() {
        assertEquals("Σε 2 χιλιόμετρα, στρίψτε δεξιά", MapsPrefs.inDistance(el, 2140.0, true, "στρίψτε δεξιά"))
        assertEquals("Σε 1 χιλιόμετρο, x", MapsPrefs.inDistance(el, 1000.0, true, "x"))
        assertEquals("Σε 1,5 χιλιόμετρα, x", MapsPrefs.inDistance(el, 1400.0, true, "x"))
        assertEquals("Σε 650 μέτρα, x", MapsPrefs.inDistance(el, 660.0, true, "x"))
        assertEquals("Ακολουθεί απότομη στροφή δεξιά", MapsPrefs.bendWords(el, true))
        assertEquals("Συνεχίστε ευθεία προς Θεσπιές", MapsLanes.straight(Step("Μείνετε δεξιά και οδηγείστε προς Θεσπιές", 7, 0.0, 1, "", toward = "Θεσπιές"), el))
    }

    @Test fun german() {
        val de = testWords("de")
        assertEquals("In 2 Kilometern, rechts abbiegen", MapsPrefs.inDistance(de, 2140.0, true, "rechts abbiegen"))
        assertEquals("In 1 Kilometer, x", MapsPrefs.inDistance(de, 1000.0, true, "x"))
        assertEquals("In 1,5 Kilometern, x", MapsPrefs.inDistance(de, 1400.0, true, "x"))
        assertEquals("über die 2 rechten Fahrstreifen", MapsLanes.words(Lanes(MapsLanes.parse("left|through|right"), setOf(1, 2)), de))
        assertEquals("über den mittleren Fahrstreifen", MapsLanes.words(Lanes(MapsLanes.parse("left|through|right"), setOf(1)), de))
        assertEquals("Geradeaus weiterfahren Richtung Theben", MapsLanes.straight(Step("Rechts halten", 7, 0.0, 1, "", toward = "Theben"), de))
        assertEquals("Achtung, scharfe Linkskurve", MapsPrefs.bendWords(de, false))
        assertEquals("Keine Route.", MapsRouting.words(null, de))
        assertEquals("In 300 Metern, rechts abbiegen", MapsPrefs.sample(de))
    }

    @Test fun graphHopperWords() {
        assertEquals("rechts abbiegen", MapsRouting.translation("de").tr("turn_right"))
        assertEquals("στρίψτε δεξιά", MapsRouting.translation("el").tr("turn_right"))
        // none in GraphHopper: Tetra's own, and English for a line neither has
        assertEquals("pagriezieties pa labi", MapsRouting.translation("lv").tr("turn_right"))
        assertEquals("Ag an timpeallán, tóg slí amach 2", MapsRouting.translation("ga").tr("roundabout_exit", 2))
        // GraphHopper's Spanish without "toward": Tetra's line; Portugal's Portuguese, its U-turn Tetra's
        assertEquals("gira a la derecha y continúa hacia Y", MapsRouting.translation("es").tr("toward_destination", "gira a la derecha", "Y"))
        assertEquals("faça inversão de marcha", MapsRouting.translation("pt").tr("u_turn"))
        assertEquals("pt", MapsRouting.translation("pt").locale.language)
    }

    @Test fun miles() {
        val us = testWords("en", "us"); val uk = testWords("en", "uk")
        assertEquals("In 2 miles, turn right", MapsPrefs.inDistance(us, 3300.0, true, "turn right"))
        assertEquals("In 1 mile, x", MapsPrefs.inDistance(uk, 1609.0, true, "x"))
        assertEquals("In 0.5 miles, x", MapsPrefs.inDistance(uk, 800.0, true, "x"))
        assertEquals("In 500 feet, x", MapsPrefs.inDistance(us, 150.0, true, "x"))
        assertEquals("In 150 yards, x", MapsPrefs.inDistance(uk, 140.0, true, "x"))
        assertEquals("In 30 yards, x", MapsPrefs.inDistance(uk, 30.0, false, "x")) // on foot, to 10 yards
        assertEquals("In 350 yards, turn right", MapsPrefs.sample(uk))
        // shown the same way
        assertEquals("0.3 mi", metresText(500.0, "us")); assertEquals("300 ft", metresText(91.0, "us"))
        assertEquals("100 yd", metresText(91.0, "uk")); assertEquals("12 mi", metresText(20_000.0, "uk"))
        assertEquals("350 m", metresText(347.0, "metric"))
        // in another language too
        assertEquals("In 2 Meilen, x", MapsPrefs.inDistance(testWords("de", "us"), 3300.0, true, "x"))
    }

    /**
     * A half (1.5 km) as each language says a fraction (the user, 27 Oct, approving the fix): CLDR's category for the real number
     * by ICU — Czech, Slovak, Lithuanian "many", Polish "other", which no whole number has — and whole numbers as before.
     */
    @Test fun halves() {
        fun say(lang: String, m: Double) = MapsPrefs.inDistance(testWords(lang), m, true, "x")
        val want = mapOf(
            "cs" to listOf("Za 1,5 kilometru, x", "Za 2 kilometry, x", "Za 5 kilometrů, x"),
            "sk" to listOf("O 1,5 kilometra, x", "O 2 kilometre, x", "O 5 kilometrov, x"),
            "pl" to listOf("Za 1,5 kilometra, x", "Za 2 kilometry, x", "Za 5 kilometrów, x"),
            "lt" to listOf("Po 1,5 kilometro, x", "Po 2 kilometrų, x", "Po 5 kilometrų, x"),
            "ro" to listOf("Peste 1,5 kilometri, x", "Peste 2 kilometri, x", "Peste 5 kilometri, x"),
            "sl" to listOf("Čez 1,5 kilometre, x", "Čez 2 kilometra, x", "Čez 5 kilometrov, x"),
            "fr" to listOf("Dans 1,5 kilomètre, x", "Dans 2 kilomètres, x", "Dans 5 kilomètres, x"),
            "pt" to listOf("Dentro de 1,5 quilómetros, x", "Dentro de 2 quilómetros, x", "Dentro de 5 quilómetros, x"))
        for ((lang, w) in want) assertEquals(lang, w, listOf(1400.0, 2000.0, 5000.0).map { say(lang, it) })
        assertEquals("Za 2,5 kilometru, x", say("cs", 2600.0)); assertEquals("Za 1 kilometr, x", say("cs", 1000.0))
        assertEquals("Za 1,5 mili, x", MapsPrefs.inDistance(testWords("pl", "uk"), 1.5 * MILE, true, "x"))
    }

    /** In all 24, a half's words are those of its CLDR category: the plural's own item, or the fraction string that has it. */
    @Test fun halvesInEveryLanguage() {
        for (lang in File("src/main/res").list()!!.filter { it.matches(Regex("values-[a-z]{2}")) }.map { it.removePrefix("values-") } + "en") {
            val w = testWords(lang)
            val rules = com.ibm.icu.text.PluralRules.forLocale(Words.pluralLocale(lang))
            val xml = File("src/main/res/values${if (lang == "en") "" else "-$lang"}/strings.xml").readText()
            fun item(unit: String, c: String) = Regex("""<plurals name="maps_in_$unit">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).find(xml)!!.groupValues[1]
                .let { Regex("""<item quantity="$c">(.*?)</item>""").find(it)?.groupValues?.get(1) }
            val miles = testWords(lang, "uk")
            fun form(unit: String, x: Double): String {
                val c = rules.select(x)
                val f = item(unit, c) ?: error("$lang has no $c for $x $unit")
                return String.format(Locale.forLanguageTag(lang), f.replace("\\'", "'"), w.number(x), "x")
            }
            for (x in listOf(1.5, 2.5, 5.5, 11.5, 21.5)) assertEquals("$lang $x km", form("km", x), MapsPrefs.inDistance(w, x * 1000, true, "x"))
            for (x in listOf(0.5, 1.5, 2.5, 5.5)) assertEquals("$lang $x mi", form("mi", x), MapsPrefs.inDistance(miles, x * MILE, true, "x"))
        }
    }

    /** Every language's warning fills in, and GraphHopper's words for it are its own (StringsTest checks every key is there). */
    @Test fun everyLanguageFills() {
        for (lang in File("src/main/res").list()!!.filter { it.matches(Regex("values-[a-z]{2}")) }.map { it.removePrefix("values-") } + "en") {
            val w = testWords(lang)
            val said = MapsPrefs.inDistance(w, 2000.0, true, "X")
            assertTrue("$lang: $said", "2" in said && "X" in said)
            // and GraphHopper's turn words in it, no English left among those Tetra says
            val tr = MapsRouting.translation(lang)
            if (lang != "en") for (k in listOf("turn_right", "keep_left", "u_turn", "continue_onto", "toward_destination", "roundabout_exit_onto", "finish"))
                assertTrue("$lang $k: ${tr.tr(k, "A", "B")}", tr.tr(k, "A", "B") != MapsRouting.translation("en").tr(k, "A", "B"))
        }
    }
}

/** A foreign place's name in the app's language (the user, 27 Oct: "the Greek names … show the normal one and the other below it"). */
class MapsOtherNamesTest {
    @Test fun aBulgarianTownByItsGreekName() {
        val p = MapsSearch.parse("town\t41.567\t23.729\tГоце Делчев\tGotse Delchev\t2900\t\t\t\t\t\tel=Νευροκόπι|de=Goze Deltschew")!!
        assertEquals("Νευροκόπι", p.named("el")); assertEquals("Gotse Delchev", p.named("en")); assertEquals("Goze Deltschew", p.named("de"))
        assertNull(p.named("fr")) // none in French: the card shows only its own
        assertEquals(listOf("Гоце Делчев"), MapsSearch.search(listOf(p), "νευροκοπι", 41.5, 23.7).map { it.name }) // accent-blind
        assertEquals(listOf("Гоце Делчев"), MapsSearch.search(listOf(p), "Gotse", 41.5, 23.7).map { it.name })
        // an older list without the column, and a name the same as its own, show nothing under it
        assertNull(MapsSearch.parse("town\t41.0\t23.0\tΣέρρες\tSerres")!!.named("el"))
    }
}
