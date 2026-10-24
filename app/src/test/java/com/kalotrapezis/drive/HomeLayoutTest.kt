package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeLayoutTest {
    private val at = { items: List<HomeItem> -> items.filter { !it.hidden }.associate { it.id to (it.col to it.row) } }

    @Test fun nothingSavedIsTodaysHome() {
        // a tablet: Sync and Settings side by side on one row, then the groups two by two, two rows each
        val tablet = HomeLayout.place(HomeLayout.parse(null, HomeLayout.WIDE), 8)
        assertEquals(mapOf("sync" to (0 to 0), "settings" to (4 to 0), "gallery" to (0 to 1), "files" to (4 to 1), "notes" to (0 to 3)), at(tablet).filterKeys { it in setOf("sync", "settings", "gallery", "files", "notes") })
        assertEquals(13, HomeLayout.height(tablet, 8)) // the Phone (24 Oct) a row of groups more
        // a phone: one under another, Settings last
        val phone = HomeLayout.place(HomeLayout.parse(null, HomeLayout.NARROW), 4)
        assertEquals(0 to 0, at(phone)["sync"]); assertEquals(0 to 1, at(phone)["gallery"]); assertEquals(0 to 23, at(phone)["settings"])
    }

    @Test fun aSavedLayoutComesBackAsItWas() {
        val items = listOf(HomeItem("maps", size = HomeSize.Small, col = 3, row = 5), HomeItem("notes", hidden = true), HomeItem("settings", col = 0, row = 0))
        assertEquals("maps:s:3:5,notes:h:-1:-1,settings::0:0", HomeLayout.format(items))
        assertEquals(items, HomeLayout.parse(HomeLayout.format(items), listOf("maps", "notes", "settings")))
    }

    @Test fun aGoneAppletIsDroppedAndANewOneFindsAFreeCell() {
        val items = HomeLayout.place(HomeLayout.parse("old::0:0,maps:s:0:0,notes:s:2:0", listOf("maps", "notes", "contacts")), 4)
        assertEquals(listOf("maps", "notes", "contacts"), items.map { it.id })
        assertEquals(mapOf("maps" to (0 to 0), "notes" to (2 to 0), "contacts" to (0 to 1)), at(items)) // a group: the whole width, under
    }

    @Test fun settingsIsNeverHidden() = assertFalse(HomeLayout.parse("settings:h:0:0", listOf("settings")).single().hidden)

    @Test fun droppedWhereAnotherIsTheOtherMovesOn() {
        val items = listOf(HomeItem("a", size = HomeSize.Small, col = 0, row = 0), HomeItem("b", size = HomeSize.Small, col = 1, row = 0), HomeItem("c", size = HomeSize.Small, col = 3, row = 2))
        // c onto b's cell: b moves to the next free one; a gap left where c was
        assertEquals(mapOf("a" to (0 to 0), "c" to (1 to 0), "b" to (2 to 0)), at(HomeLayout.drop(items, "c", 1, 0, 4)))
        // anywhere, even far below: the grid grows
        assertEquals(3 to 9, at(HomeLayout.drop(items, "a", 3, 9, 4))["a"])
        // a group dropped near the edge is kept inside the grid, and pushes the small ones under it
        val big = items + HomeItem("g", col = 0, row = 4)
        assertEquals(4 to 0, at(HomeLayout.drop(big, "g", 6, 0, 8))["g"])
        val after = at(HomeLayout.drop(big, "g", 0, 0, 8))
        assertEquals(0 to 0, after["g"]); assertEquals(4 to 0, after["a"]); assertEquals(5 to 0, after["b"]); assertEquals(3 to 2, after["c"])
    }

    @Test fun madeLargeItPushesItsNeighbours() {
        val items = listOf(HomeItem("a", size = HomeSize.Small, col = 0, row = 0), HomeItem("b", size = HomeSize.Small, col = 1, row = 0))
        assertEquals(mapOf("a" to (0 to 0), "b" to (0 to 2)), at(HomeLayout.change(items, items[0].copy(size = HomeSize.Large), 4)))
        // hidden and shown again: back at the first free cell
        val hidden = HomeLayout.change(items, items[0].copy(hidden = true), 4)
        assertEquals(0 to 0, at(HomeLayout.change(hidden, hidden[0].copy(hidden = false, col = -1, row = -1), 4))["a"])
    }

    @Test fun aMediumPartIsItsIconInFourCells() {
        val items = listOf(HomeItem("a", size = HomeSize.Small, col = 0, row = 0), HomeItem("b", size = HomeSize.Small, col = 1, row = 1), HomeItem("c", size = HomeSize.Small, col = 2, row = 0))
        assertEquals(2 to 2, HomeLayout.size(HomeItem("maps", size = HomeSize.Medium), 8))
        assertEquals(2 to 2, HomeLayout.size(HomeItem("maps", size = HomeSize.Medium), 4)) // half a phone's width
        // a made medium: its 2 by 2 block, b (inside it) moves on; c beside it stays
        assertEquals(mapOf("a" to (0 to 0), "b" to (2 to 1), "c" to (2 to 0)), at(HomeLayout.change(items, items[0].copy(size = HomeSize.Medium), 4)))
        assertEquals("maps:m:2:4", HomeLayout.format(listOf(HomeItem("maps", size = HomeSize.Medium, col = 2, row = 4))))
        assertEquals(HomeSize.Medium, HomeLayout.parse("maps:m:2:4", listOf("maps")).single().size)
    }
}
