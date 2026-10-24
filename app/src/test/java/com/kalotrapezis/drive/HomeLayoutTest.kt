package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeLayoutTest {
    private val defaults = listOf("sync", "settings", "notes", "maps", "contacts")

    @Test fun nothingSavedIsTodaysHome() = assertEquals(defaults.map { HomeItem(it) }, HomeLayout.parse(null, defaults))

    @Test fun aSavedLayoutComesBackAsItWas() {
        val items = listOf(HomeItem("maps", small = true), HomeItem("notes", hidden = true), HomeItem("settings"), HomeItem("sync", small = true, hidden = true), HomeItem("contacts"))
        assertEquals("maps:s,notes:h,settings,sync:sh,contacts", HomeLayout.format(items))
        assertEquals(items, HomeLayout.parse(HomeLayout.format(items), defaults))
    }

    @Test fun aNewAppletArrivesAfterItsNeighbourAndAGoneOneIsDropped() {
        // saved before Contacts existed, with an applet since removed; Contacts comes after Maps, where the defaults have it
        val items = HomeLayout.parse("maps:s,old,notes,sync,settings", defaults)
        assertEquals(listOf("maps", "contacts", "notes", "sync", "settings"), items.map { it.id })
        assertEquals(HomeItem("contacts"), items[1])
    }

    @Test fun settingsIsNeverHidden() = assertFalse(HomeLayout.parse("settings:h", defaults).first { it.id == "settings" }.hidden)

    @Test fun movingOneShiftsTheRest() =
        assertEquals(listOf("notes", "sync", "settings", "maps", "contacts"), HomeLayout.move(HomeLayout.parse(null, defaults), 2, 0).map { it.id })

    @Test fun largeOnesPairAndSmallOnesShareARow() {
        val items = HomeLayout.parse("sync,settings,notes,maps:s,contacts:s,old,a:h", defaults + "a")
        assertEquals(listOf(listOf("sync", "settings"), listOf("notes"), listOf("maps", "contacts")), HomeLayout.rows(items, 2).map { r -> r.map { it.id } })
        // a phone: one large a row; a hidden one leaves no gap
        assertEquals(listOf(listOf("sync"), listOf("settings"), listOf("notes"), listOf("maps", "contacts")), HomeLayout.rows(items, 1).map { r -> r.map { it.id } })
    }
}
