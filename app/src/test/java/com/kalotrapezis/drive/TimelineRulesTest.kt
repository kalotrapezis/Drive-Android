package com.kalotrapezis.drive

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineRulesTest {

    @Test fun thumbnailsKeepTheirSizeOnAWiderScreen() {
        // A phone is exactly as it was: 2 / 3 / 6.
        assertEquals(2, TimelineRules.columns(TimelineScale.Week, 406))
        assertEquals(3, TimelineRules.columns(TimelineScale.Month, 406))
        assertEquals(6, TimelineRules.columns(TimelineScale.Year, 406))
        // A narrow phone never goes below the phone count, whatever the arithmetic says.
        assertEquals(2, TimelineRules.columns(TimelineScale.Week, 320))
        // The tablet, 1164dp across in landscape and 777dp in portrait, gets more and smaller.
        assertEquals(6, TimelineRules.columns(TimelineScale.Week, 1164))
        assertEquals(4, TimelineRules.columns(TimelineScale.Week, 777))
        assertEquals(9, TimelineRules.columns(TimelineScale.Month, 1164))
    }
    @Test
    fun `ISO week grouping keeps the week-year boundary in descending order`() {
        fun millis(date: LocalDate) = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

        val groups = listOf(millis(LocalDate.of(2021, 1, 4)), millis(LocalDate.of(2021, 1, 1)), millis(LocalDate.of(2020, 12, 31)))
            .map { TimelineRules.groupKey(it, TimelineScale.Week, ZoneOffset.UTC) }
            .distinct()

        assertEquals(listOf("2021-W01", "2020-W53"), groups)
    }

    @Test
    fun `grid item group keys include a date label before each group`() {
        assertEquals(
            listOf("2026-09", "2026-09", "2026-09", "2025-12", "2025-12"),
            TimelineRules.itemGroupKeys(listOf("2026-09" to 2, "2025-12" to 1)),
        )
    }

    @Test
    fun `hidden first date label keeps photo indexes aligned with the grid`() {
        assertEquals(
            listOf("2026-09", "2026-09", "2025-12", "2025-12"),
            TimelineRules.itemGroupKeys(listOf("2026-09" to 2, "2025-12" to 1), firstGroupHasHeader = false),
        )
    }

    @Test
    fun `visible group remains pinned until the first item enters the next group`() {
        val keys = TimelineRules.itemGroupKeys(listOf("2026-09" to 2, "2026-10" to 1))

        assertEquals("2026-09", TimelineRules.visibleGroupKey(keys, 0))
        assertEquals("2026-09", TimelineRules.visibleGroupKey(keys, 2))
        assertEquals("2026-10", TimelineRules.visibleGroupKey(keys, 3))
    }
}
