package com.kalotrapezis.drive

import java.io.File
import java.nio.file.Files
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Timer's records (Plan.md § Timer): the time left, pause and resume, a stop reaching every device, clocks apart. */
class TimerTest {
    private val t0 = Instant.parse("2026-10-18T16:00:00Z")
    private lateinit var tablet: File
    private lateinit var computer: File

    @Before fun setUp() {
        val root = Files.createTempDirectory("timer").toFile()
        tablet = File(root, "tablet/Timer"); computer = File(root, "computer/Timer")
    }

    /** What sync does with a changed file: the other side's copy replaced (both changed: kept both, the second as "x (2)"). */
    private fun carry(from: File, to: File) = from.listFiles()!!.filter { it.name.endsWith(".json") }.forEach { f ->
        to.mkdirs()
        f.copyTo(File(to, f.name), overwrite = true)
    }

    @Test fun timeLeftCountsDownToTheEnd() {
        val r = TimerFiles.start(tablet, 300, "Tablet", t0)
        assertEquals(300_000, r.leftMs(t0))
        assertEquals(200_000, r.leftMs(t0.plusSeconds(100)))
        assertEquals(0, r.leftMs(t0.plusSeconds(400)))
        assertTrue(r.ringing(t0.plusSeconds(300)))
        assertTrue(!r.ringing(t0.plusSeconds(299)))
        assertTrue(r.over(t0.plusSeconds(300).plusMillis(TimerRecord.UNANSWERED_MS + 1))) // nobody tapped OK: over, not ringing for ever
        assertEquals("5:00", clockText(r.leftMs(t0)))
        assertEquals("0:01", clockText(1)) // rounded up: 0:00 is the end
        assertEquals("1:02:03", clockText(3_723_000))
        assertEquals("1 h 30 min", durationText(EnglishWords, 5400))
    }

    @Test fun pauseKeepsWhatIsLeftAndResumeCountsFromThen() {
        val r = TimerFiles.start(tablet, 300, "Tablet", t0)
        val paused = r.pause(t0.plusSeconds(100), "Tablet")
        assertEquals(TimerRecord.PAUSED, paused.state)
        assertEquals(200_000, paused.leftMs(t0.plusSeconds(5000))) // paused: the time stands still
        val resumed = paused.resume(t0.plusSeconds(1000), "Phone")
        assertEquals(t0.plusSeconds(1200), resumed.ends)
        assertEquals(3, resumed.rev)
        assertEquals(paused, paused.pause(t0, "x")) // pausing a paused one changes nothing
        // and it round-trips through its file
        val back = TimerFiles.read(TimerFiles.write(tablet, resumed).file!!.parentFile!!).single()
        assertEquals(resumed.copy(file = back.file), back)
    }

    @Test fun okOnOneDeviceStopsTheRingingOnTheOthers() {
        val started = TimerFiles.start(tablet, 600, "Tablet", t0)
        carry(tablet, computer)
        val end = t0.plusSeconds(600)
        assertTrue(TimerFiles.active(tablet, end).single().ringing(end)) // both ring at the end
        // OK on the computer: written in the computer's own file, the tablet's left as it was
        TimerFiles.write(computer, TimerFiles.read(computer).single().stop("Computer"))
        assertEquals(setOf("${started.id}.Tablet.json", "${started.id}.Computer.json"), computer.list()!!.toSet())
        carry(computer, tablet)
        assertTrue(TimerFiles.active(tablet, end.plusSeconds(5)).isEmpty()) // the tablet stops ringing
        assertTrue(TimerFiles.active(tablet, end.plusSeconds(5)).none { it.ringing(end.plusSeconds(5)) })
    }

    @Test fun twoChangesAtOnceMakeNoCopiesAndStoppingWins() {
        TimerFiles.start(tablet, 600, "Tablet", t0)
        carry(tablet, computer)
        TimerFiles.write(tablet, TimerFiles.read(tablet).single().pause(t0.plusSeconds(10), "Tablet")) // rev 2 here
        TimerFiles.write(computer, TimerFiles.read(computer).single().stop("Computer"))           // rev 2 there
        carry(tablet, computer); carry(computer, tablet) // each changed only its own file: nothing to keep twice
        for (d in listOf(tablet, computer)) {
            assertEquals(2, d.list()!!.size)
            assertTrue(TimerFiles.active(d, t0.plusSeconds(20)).isEmpty())
        }
        // a copy left by the old one-file-per-timer sync ("x (2).json") still reads as the same timer
        computer.listFiles()!!.first().let { it.copyTo(File(computer, it.nameWithoutExtension + " (2).json")) }
        assertTrue(TimerFiles.active(computer, t0.plusSeconds(20)).isEmpty())
        // a newer change (resumed after the stop was seen) still wins over the old stop
        val again = TimerFiles.read(computer).single().let { it.copy(state = TimerRecord.RUNNING, ends = t0.plusSeconds(900), rev = it.rev + 1, by = "Phone") }
        TimerFiles.write(computer, again)
        assertEquals(TimerRecord.RUNNING, TimerFiles.active(computer, t0.plusSeconds(20)).single().state)
    }

    @Test fun clocksAFewSecondsApartShowTheSameTimeLeftWithinTheGap() {
        val skew = 3L // the phone's clock 3 s ahead of the tablet's
        TimerFiles.start(tablet, 300, "Tablet", t0)
        carry(tablet, computer)
        val onTablet = TimerFiles.read(tablet).single().leftMs(t0.plusSeconds(60))
        val onPhone = TimerFiles.read(computer).single().leftMs(t0.plusSeconds(60 + skew))
        assertEquals(skew * 1000, onTablet - onPhone) // never more apart than the clocks
        // a pause by the fast clock, a resume by the slow one: still one timer, rev decides, not the times
        TimerFiles.write(computer, TimerFiles.read(computer).single().pause(t0.plusSeconds(60 + skew), "Phone"))
        carry(computer, tablet)
        val paused = TimerFiles.read(tablet).single()
        assertEquals(237_000, paused.leftMs(t0.plusSeconds(61))) // the same on both: a paused timer does not depend on any clock
        TimerFiles.write(tablet, paused.resume(t0.plusSeconds(70), "Tablet"))
        carry(tablet, computer)
        assertEquals(TimerRecord.RUNNING, TimerFiles.read(computer).single().state)
        assertEquals(3, TimerFiles.read(computer).single().rev)
    }

    @Test fun aNewTimerTakesTheFileOfOneThatIsOver() {
        val first = TimerFiles.start(tablet, 60, "Tablet", t0)
        TimerFiles.write(tablet, first.stop("Tablet"))
        val second = TimerFiles.start(tablet, 120, "Tablet", t0.plusSeconds(100))
        assertEquals(first.file, second.file)
        assertEquals(3, second.rev) // above the stop, so a device that missed it takes the new one
        val third = TimerFiles.start(tablet, 30, "Tablet", t0.plusSeconds(100)) // the second still runs: a file of its own
        assertTrue(third.file != second.file)
        assertEquals(2, TimerFiles.active(tablet, t0.plusSeconds(101)).size)
    }

    @Test fun aPinnedCardStaysReadyAndTheTrashTakesItAwayEverywhere() {
        val r = TimerFiles.start(tablet, 900, "Tablet", t0)
        TimerFiles.write(tablet, r.pin(true, "Tablet"))
        TimerFiles.write(tablet, TimerFiles.read(tablet).single().stop("Tablet"))
        assertTrue(TimerFiles.active(tablet, t0).isEmpty()) // nothing counting, no notification
        val card = TimerFiles.cards(tablet, t0).single() // but the card stays, ready to start again
        assertTrue(card.pinned && card.over(t0))
        assertTrue(TimerFiles.start(tablet, 60, "Tablet", t0).file != card.file) // a pinned card's file is not taken
        val again = card.restart(t0.plusSeconds(10), "Phone")
        assertEquals(t0.plusSeconds(910), again.ends)
        TimerFiles.write(tablet, again)
        carry(tablet, computer)
        TimerFiles.write(computer, TimerFiles.read(computer).first { it.id == card.id }.trash("Computer")) // the trash, on the computer
        carry(computer, tablet)
        assertTrue(TimerFiles.cards(tablet, t0.plusSeconds(20)).none { it.id == card.id })
    }

    @Test fun nineTimersAtMost() {
        repeat(TimerFiles.MAX) { TimerFiles.start(tablet, 60L + it, "Tablet", t0) }
        assertTrue(runCatching { TimerFiles.start(tablet, 30, "Tablet", t0) }.isFailure)
        assertEquals(TimerFiles.MAX, TimerFiles.cards(tablet, t0).size)
    }

    @Test fun aTimerClaudeWritesByHandIsRead() {
        tablet.mkdirs()
        File(tablet, "tea.json").writeText("""{"seconds": 180, "state": "running", "ends": "2026-10-18T16:03:00Z"}""")
        File(tablet, "notes.json").writeText("not a timer")
        val r = TimerFiles.active(tablet, t0).single()
        assertEquals("tea", r.id)
        assertEquals(180_000, r.leftMs(t0))
        assertNull(TimerRecord.parse("""{"state":"exploded"}"""))
    }

    /** The 18 Oct copies: all go, each timer kept as it is — a stop, a pin, a running one — and an older paused one never comes back. */
    @Test fun cleanClearsTheOldCopiesAndKeepsEachTimerAsItIs() {
        val now = t0.plusSeconds(3600)
        tablet.mkdirs()
        fun put(name: String, r: TimerRecord) = File(tablet, name).writeText(r.json())
        // a: an old paused file and a stopped copy that is newer — the stop must stay
        put("a.json", TimerRecord("a", 300, TimerRecord.PAUSED, null, 120.0, 1))
        put("a (2).json", TimerRecord("a", 300, TimerRecord.STOPPED, null, 0.0, 2, by = "Tablet"))
        // b: copies of a stopped timer beside its own file, which already says so
        put("b.Tablet.json", TimerRecord("b", 60, TimerRecord.STOPPED, null, 0.0, 3, by = "Tablet"))
        put("b (2).json", TimerRecord("b", 60, TimerRecord.STOPPED, null, 0.0, 2))
        put("b (3) (2).json", TimerRecord("b", 60, TimerRecord.STOPPED, null, 0.0, 1))
        // c: running now, only in a copy; d: pinned, only in copies (as the user's two on 21 Oct)
        put("c (2).json", TimerRecord("c", 7200, TimerRecord.RUNNING, now.plusSeconds(600), 0.0, 1))
        put("d (2).json", TimerRecord("d", 60, TimerRecord.STOPPED, null, 0.0, 1, pinned = true))
        put("d (3).json", TimerRecord("d", 60, TimerRecord.STOPPED, null, 0.0, 2, pinned = true))
        assertEquals(6, TimerFiles.clean(tablet, "Tablet"))
        assertEquals(setOf("a.json", "a.Tablet.json", "b.Tablet.json", "c.Tablet.json", "d.Tablet.json"), tablet.list()!!.toSet())
        val after = TimerFiles.read(tablet).associateBy { it.id }
        assertEquals(TimerRecord.STOPPED, after.getValue("a").state) // not the old paused one
        assertEquals(now.plusSeconds(600), after.getValue("c").ends)
        assertTrue(after.getValue("d").pinned); assertEquals(2, after.getValue("d").rev)
        assertEquals(setOf("c", "d"), TimerFiles.cards(tablet, now).map { it.id }.toSet())
        assertEquals(0, TimerFiles.clean(tablet, "Tablet")) // nothing more
    }
}
