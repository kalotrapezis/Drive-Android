package com.kalotrapezis.drive

import org.json.JSONObject
import java.io.File
import java.time.Instant

/*
 * Tetra's Timer (Plan.md § Timer). A timer is one small JSON file in Tetra/Timer (~/Tetra/Files/Timer on the computer),
 * synced as files are, at once (DriveScope "timer"). Each device counts down by itself from the end time in it, so they
 * all show the same time left without any traffic while it runs; Start, Pause, Resume, Stop and OK each write the file
 * once and push it. The same format on the desktop (desktop/timer.js), so Claude can read or start one by hand:
 *
 *   {"id":"k3f9a2","seconds":300,"label":"","state":"running","ends":"2026-10-18T16:05:00Z","left":0,"rev":1,"by":"Tablet","pinned":false}
 *
 * state: running (counts down to `ends`), paused (`left` seconds remain), stopped. `pinned`: the card stays when it is
 * stopped or done, ready to start again (the user, 18 Oct: "a pin at the top left"); the trash takes it away. `rev` goes up by one at each change and
 * the highest wins (equal: stopped, then paused, then running), so clocks a few seconds apart never decide anything.
 */
internal data class TimerRecord(
    val id: String,
    val seconds: Long,
    val state: String,
    val ends: Instant?,
    val left: Double,
    val rev: Int,
    val label: String = "",
    val by: String = "",
    val file: File? = null,
    val pinned: Boolean = false,
) {
    /** Time left in ms by this device's clock: none once it has run out (it rings) or stopped. */
    fun leftMs(now: Instant): Long = when (state) {
        RUNNING -> ends?.let { (it.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(0) } ?: 0
        PAUSED -> (left * 1000).toLong()
        else -> 0
    }
    fun ringing(now: Instant) = state == RUNNING && ends != null && !now.isBefore(ends) && !over(now)
    /** Stopped, or ran out long ago with nobody there to tap OK: shown nowhere, its file free for the next timer. */
    fun over(now: Instant) = state == STOPPED || (state == RUNNING && (ends == null || now.isAfter(ends.plusMillis(UNANSWERED_MS))))

    /** On the screen: counting, paused or ringing, or pinned (then ready to start again when over). */
    fun card(now: Instant) = !over(now) || pinned
    /** Free for the next new timer: over, and not kept by a pin. */
    fun free(now: Instant) = over(now) && !pinned

    fun pause(now: Instant, by: String) = if (state != RUNNING) this else copy(state = PAUSED, left = leftMs(now) / 1000.0, ends = null, rev = rev + 1, by = by)
    fun resume(now: Instant, by: String) = if (state != PAUSED) this else copy(state = RUNNING, ends = now.plusMillis((left * 1000).toLong()), left = 0.0, rev = rev + 1, by = by)
    fun stop(by: String) = copy(state = STOPPED, ends = null, left = 0.0, rev = rev + 1, by = by)
    /** A pinned card's ▶: the same timer from the start again. */
    fun restart(now: Instant, by: String) = copy(state = RUNNING, ends = now.plusSeconds(seconds), left = 0.0, rev = rev + 1, by = by)
    fun pin(on: Boolean, by: String) = copy(pinned = on, rev = rev + 1, by = by)
    /** The trash: the card goes, on every device (a running one stops). */
    fun trash(by: String) = copy(state = STOPPED, ends = null, left = 0.0, pinned = false, rev = rev + 1, by = by)

    fun json(): String = JSONObject().put("id", id).put("seconds", seconds).put("label", label).put("state", state)
        .put("ends", ends?.toString() ?: JSONObject.NULL).put("left", left).put("rev", rev).put("by", by).put("pinned", pinned).toString(1) + "\n"

    companion object {
        const val RUNNING = "running"
        const val PAUSED = "paused"
        const val STOPPED = "stopped"
        /** A timer nobody answered rings for ten minutes at most, then is over. */
        const val UNANSWERED_MS = 10 * 60_000L
        private val ORDER = listOf(RUNNING, PAUSED, STOPPED)

        /** A file's record; anything that is not one (a stray file, half a write) is null, never an error. */
        fun parse(text: String, file: File? = null): TimerRecord? = runCatching {
            val o = JSONObject(text)
            val state = o.optString("state", RUNNING).lowercase().takeIf { it in ORDER } ?: return null
            val ends = o.optString("ends", "").takeIf { it.isNotEmpty() && it != "null" }?.let { Instant.parse(it) }
            TimerRecord(o.optString("id").ifEmpty { file?.nameWithoutExtension ?: return null }, o.optLong("seconds"), state, ends,
                o.optDouble("left", 0.0).takeIf { !it.isNaN() } ?: 0.0, o.optInt("rev", 0), o.optString("label"), o.optString("by"), file, o.optBoolean("pinned", false))
        }.getOrNull()

        /**
         * One record per timer: a timer changed on two devices between syncs arrives as "x (2).json" beside "x.json",
         * both with its id; the higher rev is it, and on a tie stopping wins (the user: "stopping the one device stops all").
         */
        fun winners(all: List<TimerRecord>): List<TimerRecord> = all.groupBy { it.id }.values.map { same ->
            same.maxWith(compareBy<TimerRecord>({ it.rev }, { ORDER.indexOf(it.state) }, { -(it.file?.name?.length ?: 0) }))
        }
    }
}

internal object TimerFiles {
    const val FOLDER = "Timer"

    fun root(tetra: File) = File(tetra, FOLDER)

    fun read(dir: File): List<TimerRecord> = TimerRecord.winners(dir.listFiles().orEmpty()
        .filter { it.isFile && !it.name.startsWith(".") && it.name.endsWith(".json", true) }
        .mapNotNull { f -> runCatching { TimerRecord.parse(f.readText(), f) }.getOrNull() })

    /** The timers to show: running, paused, or ringing, the one ending first on top. */
    fun active(dir: File, now: Instant = Instant.now()): List<TimerRecord> =
        read(dir).filterNot { it.over(now) }.sortedBy { it.leftMs(now) }

    /** The cards on the screen: the active ones, then the pinned ones ready to start, at most [MAX]. */
    fun cards(dir: File, now: Instant = Instant.now()): List<TimerRecord> =
        read(dir).filter { it.card(now) }.sortedWith(compareBy({ it.over(now) }, { it.leftMs(now) }, { it.seconds })).take(MAX)

    /** The screen holds nine (the user, 18 Oct: "The UI will fit 9 timers"). */
    const val MAX = 9

    /** Written beside, then renamed over: a sync or another device never reads half a timer. */
    fun write(dir: File, r: TimerRecord): TimerRecord {
        dir.mkdirs()
        val to = r.file ?: File(dir, "${r.id}.json")
        val part = File(dir, ".${to.name}.part")
        part.writeText(r.json())
        check(part.renameTo(to)) { "Could not save the timer." }
        return r.copy(file = to)
    }

    /**
     * A new timer, in the file of one that is over when there is one (its id carried on with a higher rev, which also tells
     * a device that missed the stop that the old one is over): the folder holds as many files as timers ever ran at once,
     * not one per timer ever started.
     */
    fun start(dir: File, seconds: Long, by: String, now: Instant = Instant.now(), label: String = ""): TimerRecord {
        val all = read(dir)
        check(all.count { it.card(now) } < MAX) { "Nine timers at most: stop or delete one first." }
        val free = all.filter { it.free(now) }.minByOrNull { it.file?.name ?: "" }
        val r = TimerRecord(free?.id ?: newId(), seconds, TimerRecord.RUNNING, now.plusSeconds(seconds), 0.0, (free?.rev ?: 0) + 1, label, by, free?.file)
        return write(dir, r)
    }

    fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(6)
}

/** "5 min", "1 h 30 min", "45 s": a duration in words. */
internal fun durationText(seconds: Long): String {
    val h = seconds / 3600; val m = seconds % 3600 / 60; val s = seconds % 60
    return listOfNotNull(h.takeIf { it > 0 }?.let { "$it h" }, m.takeIf { it > 0 }?.let { "$it min" }, s.takeIf { it > 0 || seconds == 0L }?.let { "$it s" }).joinToString(" ")
}

/** "4:05", "1:02:00": the time left on the clock face, rounded up so 0:00 is the end. */
internal fun clockText(ms: Long): String {
    val t = (ms + 999) / 1000
    val h = t / 3600; val m = t % 3600 / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
