package com.kalotrapezis.drive

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One fix's decisions while directions run — the same on the car's screen, the phone's, and in a drive replayed from its
 * trace (MapsReplayTest): where on the route the fix is, whether it is off it and a new route is due, whether this is the
 * arrival, which warning of the next turn and which sharp bend is due, whether the map should be close over a crossing.
 */
internal data class Tick(
    val p: Progress, val index: Int, val step: Step, val speed: Double,
    val off: Boolean, val reroute: Boolean, val arrived: Boolean, val due: String?, val bend: Int?, val close: Boolean,
) {
    /** What was said, with this tick's warning and bend added. */
    fun said(before: Set<String>): Set<String> {
        val k = p.next
        var s = when (due) { "n" -> before + "n$k" + "f$k" + "e$k"; "f" -> before + "f$k" + "e$k"; "e" -> before + "e$k"; else -> before }
        if (bend != null) s = s + "b$bend"
        return s
    }

    /** For the log: which warning of which turn, how far ahead, how fast, the lanes it named. */
    fun why() = "$due for step ${p.next}, ${p.toNext.toInt()} m, ${Math.round(speed * 3.6)} km/h" + (step.lanes?.let { " lanes ${it.turns.joinToString("|") { l -> l.joinToString(";") }} use ${it.use.sorted()}" } ?: "")

    /** The due warning's words. */
    fun words(lang: String, car: Boolean): String? = when (due) {
        "n" -> nearWords(lang, p.toNext, car, MapsLanes.said(step, lang))
        "f", "e" -> MapsPrefs.inDistance(lang, p.toNext, car, MapsLanes.said(step, lang))
        else -> null
    }
}

/** A fix for the log: where, how fast, which way, how sure ("37.27783,22.58769 54 km/h facing 123° ±8 m"). */
internal fun where(loc: android.location.Location) = "%.5f,%.5f".format(Locale.ROOT, loc.latitude, loc.longitude) +
    (if (loc.hasSpeed()) " ${Math.round(loc.speed * 3.6f)} km/h" else "") + (if (loc.hasBearing()) " facing ${loc.bearing.toInt()}°" else "") +
    (if (loc.hasAccuracy()) " ±${loc.accuracy.toInt()} m" else "")

/** [n] at a fix [lat],[lng] going [speed] m/s at [now] (ms); [bends] the setting; [finding] while a route is being found. */
internal fun guide(n: Nav, lat: Double, lng: Double, speed: Double, now: Long, bends: Boolean, finding: Boolean): Tick {
    val (p, i) = progress(n.route, n.cum, lat, lng, n.index)
    val car = n.mode == "car"
    val step = n.route.steps[p.next]
    if (p.off > (if (car) 50.0 else 30.0)) {
        val reroute = n.offFixes + 1 >= 3 && now - n.since > 10_000 && !finding
        return Tick(p, i, step, speed, off = true, reroute = reroute, arrived = false, due = null, bend = null, close = false)
    }
    // there: at the route's end, or near the place itself (a place saved off the road ends its route short of it)
    val arrived = p.left < (if (car) 50.0 else 20.0) || metresBetween(lat, lng, n.target.lat, n.target.lng) < (if (car) 60.0 else 25.0)
    val due = if (arrived) null else dueWarning(p.next, p.toNext, speed, car, n.said, step.lanes?.begins ?: 0.0)
    val bend = if (due == null && !arrived && car && bends) dueBend(n.bends, p.along, speed, n.said) else null
    return Tick(p, i, step, speed, off = false, reroute = false, arrived = arrived, due = due, bend = bend, close = car && closeView(step.junction, p.toNext, speed))
}

/**
 * A drive's trace (the user, 23 Oct: "what can you put in the logs to understand more?"): while directions run, a line a
 * fix in files/logs/drive/drive-<date>.csv, kept 7 days and sent with Maps' Send log — where the car was, how fast, which
 * way, how sure the GPS was, where on the route, and what the camera did — so a drive can be replayed in a test.
 *
 *     # <date time> <screen> to <lat> <lng> <mode> <name>        a route started (or a new one)
 *     <ms> <lat> <lng> <m/s> <bearing> <accuracy m> <provider> <off m> <along m> <to next m> <step> <camera>
 *
 * Space-separated; the place's name last, as it may have spaces. Only on this phone, never synced.
 */
internal object MapsTrace {
    const val DAYS = 7L
    private val day = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private var dir: File? = null

    fun folder(context: Context) = File(context.filesDir, "logs/drive")

    /** A route started on [screen] ("car" or "phone") to [target]: its header line; traces older than 7 days removed. */
    fun start(context: Context, screen: String, target: Place, mode: String) {
        val d = folder(context).apply { mkdirs() }; dir = d
        val oldest = LocalDate.now().minusDays(DAYS - 1)
        d.listFiles()?.filter { f -> f.name.removePrefix("drive-").removeSuffix(".csv").let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(oldest) == true }
            ?.forEach { it.delete() }
        // the name after the format: a name with % in it is not a format
        write("# ${LocalDateTime.now().format(time)} $screen to " + "%.6f %.6f".format(Locale.ROOT, target.lat, target.lng) + " $mode ${target.name}")
    }

    /** A fix while directions run, [p] where on the route (null off it), [camera] what the camera does. */
    fun fix(loc: android.location.Location, p: Progress?, camera: String) {
        if (dir == null) return
        write("%d %.6f %.6f %.1f %.0f %.0f %s %.0f %.0f %.0f %d %s".format(Locale.ROOT, loc.time, loc.latitude, loc.longitude,
            if (loc.hasSpeed()) loc.speed else -1f, if (loc.hasBearing()) loc.bearing else -1f, if (loc.hasAccuracy()) loc.accuracy else -1f,
            loc.provider ?: "?", p?.off ?: -1.0, p?.along ?: -1.0, p?.toNext ?: -1.0, p?.next ?: -1, camera))
    }

    private fun write(line: String) {
        val f = File(dir ?: return, "drive-${LocalDate.now().format(day)}.csv")
        synchronized(this) { runCatching { f.appendText(line + "\n") } }
    }

    /** One drive's fixes as written: the header's target, then each fix. */
    data class Fix(val time: Long, val lat: Double, val lng: Double, val speed: Double, val bearing: Double, val accuracy: Double)
    data class Drive(val header: String, val lat: Double, val lng: Double, val mode: String, val name: String, val fixes: List<Fix>)

    /** The drives in a trace file: each route's header and the fixes after it. */
    fun read(f: File): List<Drive> {
        val out = ArrayList<Drive>(); var head: Drive? = null; val fixes = ArrayList<Fix>()
        fun close() { head?.let { out += it.copy(fixes = fixes.toList()) }; fixes.clear() }
        for (line in f.readLines()) {
            if (line.startsWith("# ")) {
                close()
                val w = line.removePrefix("# ").split(' ')
                // date, time, screen, "to", lat, lng, mode, name…
                head = Drive(line, w[4].toDouble(), w[5].toDouble(), w[6], w.drop(7).joinToString(" "), emptyList())
            } else if (line.isNotBlank()) {
                val w = line.split(' ')
                fixes += Fix(w[0].toLong(), w[1].toDouble(), w[2].toDouble(), w[3].toDouble(), w[4].toDouble(), w[5].toDouble())
            }
        }
        close()
        return out
    }

    /** Maps' Send log: the log and the drives of the last 7 days in one zip. */
    fun export(context: Context): File? {
        val log = WriterLog.export(context) ?: return null
        val out = File(context.filesDir, "logs/Tetra Maps log.zip")
        java.util.zip.ZipOutputStream(out.outputStream()).use { z ->
            (listOf(log) + (folder(context).listFiles()?.sortedBy { it.name }.orEmpty())).forEach { f ->
                z.putNextEntry(java.util.zip.ZipEntry(f.name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
        return out
    }
}
