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
    private fun carry(from: File, to: File, conflict: Boolean = false) = from.listFiles()!!.filter { it.name.endsWith(".json") }.forEach { f ->
        to.mkdirs()
        f.copyTo(File(to, if (conflict) f.nameWithoutExtension + " (2).json" else f.name), overwrite = true)
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
        assertEquals("1 h 30 min", durationText(5400))
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

    @Test fun stopOnOneDeviceStopsItOnTheOthers() {
        val started = TimerFiles.start(tablet, 600, "Tablet", t0)
        carry(tablet, computer)
        assertEquals(started.id, TimerFiles.active(computer, t0).single().id)
        // OK on the computer (it rang there): the stop comes back to the tablet as an edit of the same file
        TimerFiles.write(computer, TimerFiles.read(computer).single().stop("Computer"))
        carry(computer, tablet)
        assertTrue(TimerFiles.active(tablet, t0.plusSeconds(600)).isEmpty())
    }

    @Test fun twoChangesAtOnceStoppingWins() {
        TimerFiles.start(tablet, 600, "Tablet", t0)
        carry(tablet, computer)
        TimerFiles.write(tablet, TimerFiles.read(tablet).single().pause(t0.plusSeconds(10), "Tablet")) // rev 2 here
        TimerFiles.write(computer, TimerFiles.read(computer).single().stop("Computer"))           // rev 2 there
        carry(tablet, computer, conflict = true) // both changed: sync keeps both files
        assertEquals(2, computer.listFiles()!!.size)
        assertTrue(TimerFiles.active(computer, t0.plusSeconds(20)).isEmpty())
        // a newer change (resumed after the stop was seen) still wins over the old stop
        val again = TimerFiles.read(computer).single().let { it.copy(state = TimerRecord.RUNNING, ends = t0.plusSeconds(900), rev = it.rev + 1) }
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
}
