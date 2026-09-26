package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoLocationTest {
    @Test fun `video ISO 6709 coordinates are parsed`() {
        assertEquals(PhotoLocation(40.939, 24.412), parseIso6709Location("+40.939+24.412/"))
        assertEquals(PhotoLocation(-33.86, 151.21), parseIso6709Location("-33.86+151.21/"))
    }

    @Test fun `invalid coordinates are rejected`() {
        assertNull(parseIso6709Location(null))
        assertNull(parseIso6709Location("+95.0+24.0/"))
        assertNull(parseIso6709Location("not-a-location"))
    }

    @Test fun `nearest main location prefers the city`() {
        assertEquals("Kavala", mainPlaceName("Kavala", "Kavala", "East Macedonia and Thrace", "Greece"))
        assertEquals("East Macedonia and Thrace", mainPlaceName(null, null, "East Macedonia and Thrace", "Greece"))
    }
}
