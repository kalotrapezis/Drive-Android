package com.kalotrapezis.drive

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Maps (Plan.md § Maps): saved places as files, Home's six slots, and offline routing on the computer's graph. */
class MapsTest {
    @Test fun places() {
        val root = Files.createTempDirectory("maps").toFile()
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

    /** On the computer's graph (tools/maps-greece.sh), when it is there: Syntagma to the Acropolis, each way to go. */
    @Test fun route() {
        val dir = File(System.getProperty("user.home"), "Tetra-maps/greece-gh")
        assumeTrue(File(dir, "properties").isFile)
        for (mode in Place.MODES) {
            val r = MapsRouting.route(dir, 37.9755, 23.7348, 37.9715, 23.7257, mode)
            println("$mode: ${r.metres.toInt()} m, ${r.millis / 1000} s, ${r.steps.size} steps: ${r.steps.take(3).map { it.text }}")
            assertTrue(r.metres in 800.0..3000.0)
            assertTrue(r.steps.isNotEmpty() && r.lats.size == r.lngs.size)
        }
        // Rolling: the wheelchair profile, when the graph has it — no steps, so never shorter than on foot
        if ("wheelchair" in MapsRouting.profiles(dir)) {
            val foot = MapsRouting.route(dir, 37.9755, 23.7348, 37.9715, 23.7257, "foot")
            val roll = MapsRouting.route(dir, 37.9755, 23.7348, 37.9715, 23.7257, "wheelchair")
            println("wheelchair: ${roll.metres.toInt()} m, ${roll.millis / 1000} s (foot ${foot.metres.toInt()} m)")
            assertTrue(roll.metres >= foot.metres - 1 && roll.millis >= foot.millis)
        }
        // the hand-written custom models give GraphHopper's own answers (its server on the same graph, 21 Oct)
        val gh = mapOf("car" to (471079.0 to 19950L), "foot" to (359126.0 to 230982L), "bike" to (463242.0 to 98452L))
        for ((mode, want) in gh) {
            val r = MapsRouting.route(dir, 37.9838, 23.7275, 40.6401, 22.9444, mode)
            println("$mode Athens–Thessaloniki: ${r.metres.toInt()} m, ${r.millis / 1000} s")
            assertEquals(want.first, r.metres, 2.0); assertEquals(want.second.toDouble(), r.millis / 1000.0, 2.0)
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
        val root = Files.createTempDirectory("import").toFile()
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
    /** At 120 km/h (33 m/s) a turn is warned at 2 km, at ~660 m and at ~165 m; in town at 500 m and 80 m; each once. */
    @Test fun warningsFollowTheSpeed() {
        var said = emptySet<String>()
        val heard = ArrayList<Pair<Int, String>>()
        for (d in 3000 downTo 0 step 30) dueWarning(4, d.toDouble(), 33.0, true, said)?.let { heard += d to it; said = said + "${it}4" }
        assertEquals(listOf("e", "f", "n"), heard.map { it.second })
        assertTrue(heard[0].first in 2100..2200); assertTrue(heard[1].first in 630..660); assertTrue(heard[2].first in 140..165)
        said = emptySet(); heard.clear()
        for (d in 1500 downTo 0 step 10) dueWarning(1, d.toDouble(), 12.0, true, said)?.let { heard += d to it; said = said + "${it}1" }
        assertEquals(listOf(500 to "f", 80 to "n"), heard)
        assertEquals(2.0 to true, spokenDistance(2140.0, true)); assertEquals(650.0 to false, spokenDistance(660.0, true))
        assertEquals(1.5 to true, spokenDistance(1400.0, true))
    }
}

class MapsMotorwayTest {
    /** Σχηματάρι to Ναύπλιο on the computer's graph, when it is there: the on-ramp to Olympia Odos names the road it leads onto. */
    @Test fun aRampSaysWhereItLeads() {
        val dir = File(System.getProperty("user.home"), "Tetra-maps/greece-gh")
        assumeTrue(File(dir, "properties").isFile)
        val r = MapsRouting.route(dir, 37.346366, 22.580585, 37.565923, 22.806878, "car")
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
        val maps = File(System.getProperty("user.home"), "Tetra-maps")
        val root = File(out!!).apply { mkdirs() }
        listOf(MapsSearch.FILE, MapsSearch.EOPYY, MapsAddresses.FILE).forEach { n -> File(maps, n).takeIf { it.isFile }?.copyTo(File(root, n), overwrite = true) }
        val entries = File(zip!!).inputStream().use { MapsImport.read(File(zip).name, it) }
        println("entries: ${entries.size} " + entries.groupingBy { it.list }.eachCount())
        val r = MapsImport.into(root, MapsSearch.load(root), entries)
        println("placed ${r.places}, lists ${r.lists}, missed ${r.missed.size}: ${r.missed}")
    }
}

class MapsLimitFerryTest {
    /** On the computer's graph, when it is there: a motorway's limit, and the ferry to Samothraki said as a ferry. */
    @Test fun limitsAndFerries() {
        val dir = File(System.getProperty("user.home"), "Tetra-maps/greece-gh")
        assumeTrue(File(dir, "properties").isFile)
        // Egnatia near Kavala, and Panepistimiou in Athens
        val r = MapsRouting.route(dir, 41.149912, 24.146884, 40.475293, 25.473550, "car")
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
    @Test fun twoWaysThere() {
        val dir = File(System.getProperty("user.home"), "Tetra-maps/greece-gh")
        assumeTrue(File(dir, "properties").isFile)
        val rs = MapsRouting.routes(dir, 37.277768, 22.585572, 37.3713423, 22.4651517, "car")
        rs.forEach { println("${it.millis / 60000} min ${(it.metres / 100).toInt() / 10.0} km ${it.roads}") }
        assertTrue(rs.size >= 2 && rs.none { it.roads == "track" })
        assertEquals(rs.sortedBy { it.millis }, rs)
        assertTrue(rs.any { it.roads == "motorway" } && rs.any { it.roads != "motorway" })
        // the tolls on each: the motorway passes OSM's toll stations, the local roads none
        val all = MapsSearch.load(File(System.getProperty("user.home"), "Tetra-maps"))
        assumeTrue(all.any { it.kind == "toll" })
        // (OSM has no toll station near Σχηματάρι or Οινόφυτα: the local roads have none, the motorway none known)
        assertTrue(rs.all { r -> tollsOn(r, cumulative(r), all).isEmpty() })
        // Σχηματάρι to Ναύπλιο: Ολυμπία Οδός's at Ελευσίνα and Μορέας' on the Α7 are on the way
        val far = MapsRouting.route(dir, 37.346366, 22.580585, 37.565923, 22.806878, "car")
        val t = tollsOn(far, cumulative(far), all)
        println("Σχηματάρι → Ναύπλιο tolls: ${t.map { "${it.first.title} (${it.first.more}) at ${(it.second / 1000).toInt()} km" }}")
        assertTrue(t.size >= 2 && t.any { "Ελευσίνας" in it.first.title })
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
