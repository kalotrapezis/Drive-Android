package com.kalotrapezis.drive

import java.io.File
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Timer's alarms and stopwatch (TimerAlarms.kt): when they ring, an answer reaching every device, and the timers untouched. */
class TimerAlarmsTest {
    private val zone = ZoneId.of("Europe/Athens")
    private val t0 = Instant.parse("2026-10-29T18:00:00Z") // Thursday 20:00 in Athens
    private lateinit var phone: File
    private lateinit var tablet: File

    @Before fun setUp() {
        val root = Files.createTempDirectory("alarms").toFile()
        phone = File(root, "phone/Timer"); tablet = File(root, "tablet/Timer")
    }

    private fun carry(from: File, to: File) = from.listFiles()!!.filter { it.name.endsWith(".json") }.forEach { to.mkdirs(); it.copyTo(File(to, it.name), overwrite = true) }
    private fun at(local: String) = java.time.LocalDateTime.parse(local).atZone(zone).toInstant()

    @Test fun aSingleAlarmRingsOnceAndADismissOnOneDeviceStopsItOnTheOther() {
        val a = TimerAlarmFiles.add(phone, LocalTime.of(7, 30), emptySet(), "Σχολείο", "Phone", t0, zone)
        assertEquals(at("2026-10-30T07:30"), a.next(t0, zone))
        carry(phone, tablet)
        val ring = at("2026-10-30T07:31")
        val onTablet = TimerAlarmFiles.ringing(tablet, ring, zone).single()
        assertEquals(at("2026-10-30T07:30"), onTablet.second)
        TimerAlarmFiles.write(tablet, onTablet.first.dismiss(onTablet.second, "Tablet"))
        carry(tablet, phone)
        assertTrue(TimerAlarmFiles.ringing(phone, ring, zone).isEmpty())
        assertNull(TimerAlarmFiles.alarms(phone).single().next(ring, zone)) // once: off now
        assertTrue(TimerAlarmFiles.ringing(phone, at("2026-10-30T07:45"), zone).isEmpty())
    }

    @Test fun aRepeatingAlarmSnoozesSkipsAndStopsRingingAfterTenMinutes() {
        val a = TimerAlarmFiles.add(phone, LocalTime.of(7, 0), setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), "", "Phone", t0, zone)
        assertEquals(at("2026-10-30T07:00"), a.next(t0, zone)) // Friday
        val ring = at("2026-10-30T07:02")
        val snoozed = a.snoozed(a.ringing(ring, zone)!!, ring, "Phone")
        assertNull(snoozed.ringing(ring, zone))
        assertEquals(at("2026-10-30T07:12"), snoozed.next(ring, zone))
        assertEquals(at("2026-10-30T07:12"), snoozed.ringing(at("2026-10-30T07:13"), zone))
        assertNull(a.ringing(at("2026-10-30T07:10:01"), zone)) // nobody answered: ten minutes, then quiet
        val skipped = a.dismiss(a.next(t0, zone)!!, "Phone")
        assertEquals(at("2026-11-02T07:00"), skipped.next(t0, zone)) // Friday skipped: Monday
        assertTrue(skipped.on(t0, zone))
    }

    @Test fun offOnAndDeleteGoByRevAndADeletedFileIsUsedAgain() {
        val a = TimerAlarmFiles.add(phone, LocalTime.of(6, 0), emptySet(), "", "Phone", t0, zone)
        TimerAlarmFiles.write(phone, a.switchOff("Phone"))
        assertTrue(!TimerAlarmFiles.alarms(phone).single().on(t0, zone))
        TimerAlarmFiles.write(phone, TimerAlarmFiles.alarms(phone).single().switchOn(t0, zone, "Phone"))
        assertEquals(at("2026-10-30T06:00"), TimerAlarmFiles.upcoming(phone, t0, zone)!!.second)
        TimerAlarmFiles.write(phone, TimerAlarmFiles.alarms(phone).single().delete("Phone"))
        assertTrue(TimerAlarmFiles.alarms(phone).isEmpty())
        val b = TimerAlarmFiles.add(phone, LocalTime.of(8, 0), DayOfWeek.entries.toSet(), "", "Phone", t0, zone)
        assertEquals(a.id, b.id)
        assertEquals(a.rev + 4, b.rev)
        assertEquals(1, phone.listFiles()!!.size)
    }

    @Test fun theStopwatchCountsHoldsAndLapsAcrossDevices() {
        val w = TimerAlarmFiles.write(phone, StopwatchRecord().start(t0, "Phone"))
        carry(phone, tablet)
        val there = TimerAlarmFiles.stopwatch(tablet)
        assertEquals(65_000, there.elapsedMs(t0.plusSeconds(65)))
        val held = TimerAlarmFiles.write(tablet, there.lap(t0.plusSeconds(30), "Tablet").hold(t0.plusSeconds(65), "Tablet"))
        carry(tablet, phone)
        val back = TimerAlarmFiles.stopwatch(phone)
        assertEquals(StopwatchRecord.HELD, back.state)
        assertEquals(listOf(30.0), back.laps)
        assertEquals(65_000, back.elapsedMs(t0.plusSeconds(500)))
        assertEquals(85_000, back.start(t0.plusSeconds(500), "Phone").elapsedMs(t0.plusSeconds(520)))
        assertEquals(w.rev + 2, held.rev)
        assertEquals("1:05.0", stopwatchText(65_000))
    }

    @Test fun timersAndAlarmsShareTheFolderWithoutMixing() {
        TimerFiles.start(phone, 300, "Phone", t0)
        TimerAlarmFiles.add(phone, LocalTime.of(7, 0), emptySet(), "", "Phone", t0, zone)
        TimerAlarmFiles.write(phone, StopwatchRecord().start(t0, "Phone"))
        assertEquals(1, TimerFiles.read(phone).size)
        assertEquals(1, TimerAlarmFiles.alarms(phone).size)
        assertEquals(StopwatchRecord.COUNTING, TimerAlarmFiles.stopwatch(phone).state)
        assertEquals(0, TimerFiles.clean(phone, "Phone"))
        // the file as Claude's helper writes it
        val o = JSONObject(File(phone.listFiles()!!.first { it.name.startsWith("alarm-") }.path).readText())
        assertEquals("07:00", o.getString("time")); assertEquals("alarm", o.getString("kind"))
    }

    @Test fun theAssistantSetsListsAndDeletesAlarmsAndRunsTheStopwatch() {
        val tetra = phone.parentFile!!
        var now = t0.atZone(zone)
        val tools = AssistantTools(tetra, "Phone", zone, now = { now })
        assertEquals("Alarm set for 07:00, every day.", tools.run("alarm_add", JSONObject("""{"time":"7:00","days":["MO","TU","WE","TH","FR","SA","SU"]}""")).forModel)
        assertEquals("Alarm set for 06:00, once, 2026-10-30.", tools.run("alarm_add", JSONObject("""{"time":"06:00","date":"2026-10-30","label":"Πτήση"}""")).forModel)
        assertEquals("1. 06:00 once (Πτήση)\n2. 07:00 every day", tools.run("alarm_list", JSONObject()).forModel)
        tools.run("alarm_delete", JSONObject("""{"choice":1}"""))
        assertEquals("1. 07:00 every day", tools.run("alarm_list", JSONObject()).forModel)
        tools.run("stopwatch", JSONObject("""{"action":"start"}"""))
        now = now.plusSeconds(75)
        assertEquals("Stopwatch held at 0 h 1 min 15 s.", tools.run("stopwatch", JSONObject("""{"action":"pause"}""")).forModel)
    }
}
