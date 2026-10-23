package com.kalotrapezis.drive

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The turn card's crossing pictures, drawn as on the car's screen: written to files/junctions to look at (adb pull). */
@RunWith(AndroidJUnit4::class)
class JunctionPictureDeviceTest {
    @Test fun draw() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "junctions").apply { mkdirs() }
        // from MapsJunctionTest on the real graph: Δράμα's 5 Δρόμοι, a five-road crossing there, Νικήσιανη, Ομόνοια
        val cases = mapOf(
            "5dromoi-exit2" to Junction(listOf(-180.0, -122.0, -37.0, 45.0, 107.0), 2, roundabout = true),
            "5dromoi-exit1" to Junction(listOf(-180.0, -122.0, -37.0, 45.0, 107.0), 4, roundabout = true),
            "drama-slight-right" to Junction(listOf(-180.0, -162.0, -110.0, 35.0, 70.0), 3),
            "nikisiani-sharp-right" to Junction(listOf(-180.0, -32.0, 113.0), 2),
            "omonia-exit2" to Junction(listOf(-180.0, -164.0, -93.0, 2.0, 69.0, 88.0), 3, roundabout = true),
            "corinth-fork-left" to Junction(listOf(-180.0, -6.0, 4.0), 1),
        )
        // the lanes strips: Ελαιώνας' exit, Κόρινθος' left fork to Τρίπολη, a town's turn lanes
        val strips = mapOf(
            "lanes-elaionas" to Lanes(MapsLanes.parse("through|through|slight_right"), setOf(2)),
            "lanes-corinth" to Lanes(MapsLanes.parse("slight_left|slight_left|through|through"), setOf(0, 1)),
            "lanes-town" to Lanes(MapsLanes.parse("left|through|through;right"), setOf(2)),
        )
        for ((name, l) in strips) File(dir, "$name.png").outputStream().use { lanesPicture(l, 120).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        for ((name, j) in cases) {
            val b = junctionPicture(j, 400)
            File(dir, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(b.width == 400)
        }
    }
}
