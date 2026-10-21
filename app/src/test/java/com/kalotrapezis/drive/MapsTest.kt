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
        val r = MapsImport.apply(root, listOf(Poi("pharmacy", 41.13, 24.88, "Φαρμακείο Χ")), es + g)
        assertEquals(4, r.places); assertEquals(listOf("Nowhere (Starred places)"), r.missed)
        assertEquals(setOf("Want to go", "Starred places"), MapsFiles.lists(root).map { it.name }.toSet())
        assertEquals("🚩", MapsFiles.lists(root).first { it.name == "Want to go" }.emoji)
        // a second import adds nothing new
        assertEquals(4, MapsImport.apply(root, emptyList(), es.take(1) + g.take(1)).places + 2)
        assertEquals(4, MapsFiles.places(root).size)
    }
}
