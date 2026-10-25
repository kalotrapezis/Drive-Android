package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DataTest {
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)
    private fun day(date: LocalDate, mobile: Long, roam: Long = 0, outside: Boolean = false) = DataDay(date, mobile, 0, roam, 0, 0, outside)

    @Test fun `a monthly cycle renews on its day, or the month's last when it has none`() {
        val p = DataPlan(day = 31)
        assertEquals(DataCycle(d(1, 31), d(2, 28)), DataRules.cycle(p, d(2, 10)))
        assertEquals(DataCycle(d(2, 28), d(3, 31)), DataRules.cycle(p, d(3, 1)))
        val p5 = DataPlan(day = 5)
        assertEquals(DataCycle(d(10, 5), d(11, 5)), DataRules.cycle(p5, d(10, 25)))
        assertEquals(DataCycle(d(9, 5), d(10, 5)), DataRules.cycle(p5, d(10, 4)))
        assertEquals(DataCycle(d(12, 5, 2025), d(1, 5)), DataRules.cycle(p5, d(1, 1)))
    }

    @Test fun `weekly and every N days`() {
        assertEquals(DataCycle(d(10, 19), d(10, 26)), DataRules.cycle(DataPlan(every = DataPlan.WEEK, day = 1), d(10, 25))) // Sunday → that week's Monday
        val p = DataPlan(every = DataPlan.DAYS, days = 28, from = d(10, 1))
        assertEquals(DataCycle(d(10, 29), d(11, 26)), DataRules.cycle(p, d(11, 2)))
        assertEquals(DataCycle(d(9, 3), d(10, 1)), DataRules.cycle(p, d(9, 20))) // before its start
    }

    @Test fun `the status counts the cycle's days, the carrier's figure and the pace`() {
        val p = DataPlan(kind = DataPlan.FAIR_USE, size = 300_000_000_000, day = 5)
        val days = (5..24).map { day(d(10, it), 2_000_000_000) } // 2 GB a day from the 5th to the 24th
        val s = DataRules.status(p, days, d(10, 24))
        assertEquals(40_000_000_000, s.used)
        assertEquals(12, s.daysLeft) // the 24th to 4 Nov
        assertEquals((300_000_000_000 - 40_000_000_000) / 12, s.budget)
        assertEquals(40_000_000_000 + 2_000_000_000L * 11, s.projection)
        val typed = p.copy(offsetStart = d(10, 5), offset = 1_000_000_000)
        assertEquals(41_000_000_000, DataRules.status(typed, days, d(10, 24)).used)
        assertEquals(40_000_000_000, DataRules.status(typed, days, d(11, 10)).let { DataRules.status(typed, days, d(10, 30)).used - 1_000_000_000 })
        assertEquals(0, DataRules.status(typed, days, d(11, 10)).used) // the offset was for October's cycle only
    }

    @Test fun `roaming outside the EU is the day pass's, not the plan's`() {
        val p = DataPlan(day = 1)
        val days = listOf(day(d(10, 1), 500_000_000), day(d(10, 2), 400_000_000, roam = 300_000_000, outside = true))
        assertEquals(600_000_000, DataRules.status(p, days, d(10, 2)).used)
        assertEquals(400_000_000, DataRules.status(p.copy(euCounts = false), listOf(day(d(10, 1), 500_000_000, roam = 100_000_000), day(d(10, 2), 100_000_000, roam = 100_000_000)), d(10, 2)).used)
    }

    @Test fun `rollover adds what was left of the cycle before`() {
        val p = DataPlan(size = 10_000_000_000, day = 1, rollover = true)
        val days = listOf(day(d(9, 10), 6_000_000_000), day(d(10, 2), 1_000_000_000))
        assertEquals(14_000_000_000, DataRules.status(p, days, d(10, 2)).size)
    }

    @Test fun `warnings come once each, the highest passed`() {
        val p = DataPlan(size = 100, warn = listOf(50, 80, 100))
        assertEquals(listOf(50, 80), DataRules.due(p, 85, 100, emptySet()))
        assertEquals(listOf(80), DataRules.due(p, 85, 100, setOf(50)))
        assertEquals(emptyList<Int>(), DataRules.due(p.copy(kind = DataPlan.UNLIMITED), 850, 100, emptySet()))
    }

    @Test fun `the chart's top is round and just over the tallest bar, at any size`() {
        assertEquals(4_000_000, DataRules.axisTop(3_100_000))
        assertEquals(40_000_000_000, DataRules.axisTop(38_000_000_000))
        DataRules.axisTop(2_300_000_000_000).let { assertTrue(it in 2_300_000_000_000..3_450_000_000_000) } // 2.3 TB: no cap at 2 GB
        assertTrue(DataRules.axisTop(1) >= 1)
    }

    @Test fun `sizes read and written as people say them`() {
        assertEquals("212 MB", DataRules.bytes(212_000_000))
        assertEquals("7.2 GB", DataRules.bytes(7_240_000_000))
        assertEquals("41 GB", DataRules.bytes(41_000_000_000))
        assertEquals(300_000_000_000, DataRules.parseSize("300 GB"))
        assertEquals(300_000_000_000, DataRules.parseSize("300"))
        assertEquals(1_500_000_000L, DataRules.parseSize("1,5gb"))
        assertEquals(800_000_000L, DataRules.parseSize("800 MB"))
        assertEquals(null, DataRules.parseSize("lots"))
    }

    @Test fun `a use in words, the hotspot apart`() {
        assertEquals("Hotspot 70 GB · Wi-Fi 28 GB", Use(0, 70_000_000_000, 28_000_000_000).words())
        assertEquals("Mobile 0 B · Wi-Fi 0 B", Use(0, 0, 0).words(always = true))
        assertEquals("Mobile 1.2 GB", Use(1_200_000_000, 0, 0).words())
    }

    @Test fun `the EU and outside it`() {
        assertTrue(DataRules.inEu("gr")); assertTrue(DataRules.inEu("NO"))
        assertFalse(DataRules.inEu("mk")); assertFalse(DataRules.inEu("tr"))
    }

    @Test fun `a month's file reads back as written, and the plan too`() {
        val m = DataMonth("X", listOf(DataDay(d(10, 25), 1, 2, 3, 4, 5, true)), listOf(DataApp(d(10, 25), "org.a", "A \"quoted\"", "mobile", 7, 8, 9)),
            listOf(DataHour("2026-10-25T14", "wifi", 10)), listOf(DataEvent("2026-10-25T14:02:11+03:00", "drop-mobile", 40, "4G")), "Xiaomi 15")
        assertEquals(m, DataFiles.parse("X", DataFiles.text(m)))
        assertEquals("Xiaomi 15", DataFiles.label(mapOf("X" to listOf(m)), "X")); assertEquals("Y", DataFiles.label(emptyMap(), "Y"))
        val p = DataPlan(kind = DataPlan.FAIR_USE, size = 300_000_000_000, every = DataPlan.DAYS, days = 28, from = d(10, 1), passPrice = 15.0, passSize = 300_000_000,
            offsetStart = d(10, 1), offset = -5, warn = listOf(50, 90), rev = 3, by = "phone")
        assertEquals(p, DataPlan.parse(p.toJson()))
    }
}
