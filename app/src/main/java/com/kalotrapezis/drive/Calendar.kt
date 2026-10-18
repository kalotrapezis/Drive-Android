package com.kalotrapezis.drive

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import java.util.UUID
import org.dmfs.rfc5545.DateTime as RecurTime
import org.dmfs.rfc5545.recur.RecurrenceRule

/*
 * Tetra's Calendar (Plan.md § Calendar). The calendars are files, so Claude or any person can change them:
 *   Tetra/Calendar/<calendar>/color         one line, #RRGGBB (vdir's own convention, as khal and vdirsyncer)
 *   Tetra/Calendar/<calendar>/<event>.ics   one iCalendar (RFC 5545) file per event, its changed days inside it
 * Files sync carries the folder as it carries everything else; nothing here talks to sync.
 */

/** One content line, `NAME;PARAMS:VALUE`, kept exactly as read: an edit only replaces the lines it changes. */
class ICalProp(val name: String, val params: String, val value: String) {
    /** A parameter's value, quotes taken off; params is ";KEY=value;KEY2=\"v\"" as written. */
    fun param(key: String): String? = ICal.splitOutsideQuotes(params, ';').firstNotNullOfOrNull { p ->
        val eq = p.indexOf('=')
        if (eq > 0 && p.substring(0, eq).equals(key, true)) p.substring(eq + 1).trim('"') else null
    }
    val text get() = ICal.unescape(value)
    override fun toString() = "$name$params:$value"
}

class ICalComp(val name: String, val items: MutableList<Any> = mutableListOf()) {
    fun props(n: String) = items.filterIsInstance<ICalProp>().filter { it.name == n }
    fun prop(n: String) = props(n).firstOrNull()
    fun comps(n: String) = items.filterIsInstance<ICalComp>().filter { it.name == n }
    fun remove(n: String) { items.removeAll { it is ICalProp && it.name == n } }
    /** Replaces the first [n] where it stands (the rest go), or adds it before the first inner component. */
    fun set(n: String, value: String?, params: String = "") {
        val at = items.indexOfFirst { it is ICalProp && it.name == n }
        remove(n)
        if (value == null) return
        val prop = ICalProp(n, params, value)
        if (at >= 0) items.add(minOf(at, items.size), prop)
        else items.add(items.indexOfFirst { it is ICalComp }.let { if (it < 0) items.size else it }, prop)
    }
}

object ICal {
    fun parse(text: String): List<ICalComp> {
        val top = mutableListOf<ICalComp>()
        val stack = ArrayDeque<ICalComp>()
        for (line in text.replace("\r\n", "\n").replace("\r", "\n").replace(Regex("\n[ \t]"), "").split('\n')) {
            if (line.isBlank()) continue
            val colon = indexOutsideQuotes(line, ':')
            if (colon < 0) continue // not a content line; a broken file still shows what it can
            val head = line.substring(0, colon)
            val semi = head.indexOf(';').let { if (it < 0) head.length else it }
            val name = head.substring(0, semi).uppercase()
            val value = line.substring(colon + 1)
            when (name) {
                "BEGIN" -> ICalComp(value.trim().uppercase()).also { c -> stack.lastOrNull()?.items?.add(c) ?: top.add(c); stack.addLast(c) }
                "END" -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.lastOrNull()?.items?.add(ICalProp(name, head.substring(semi), value))
            }
        }
        return top
    }

    /** RFC 5545's form: CRLF, lines folded at 75 octets without splitting a letter. */
    fun write(comps: List<ICalComp>): String = buildString {
        fun line(s: String) {
            var bytes = 0
            var i = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                val n = String(Character.toChars(cp)).toByteArray().size
                if (bytes + n > 75) { append("\r\n "); bytes = 1 }
                appendCodePoint(cp); bytes += n; i += Character.charCount(cp)
            }
            append("\r\n")
        }
        fun comp(c: ICalComp) {
            line("BEGIN:${c.name}")
            c.items.forEach { if (it is ICalComp) comp(it) else line(it.toString()) }
            line("END:${c.name}")
        }
        comps.forEach(::comp)
    }

    fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")
    fun unescape(s: String) = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) { val n = s[i + 1]; append(if (n == 'n' || n == 'N') '\n' else n); i += 2 }
            else { append(c); i++ }
        }
    }

    fun indexOutsideQuotes(s: String, ch: Char): Int {
        var quoted = false
        for (i in s.indices) { if (s[i] == '"') quoted = !quoted else if (s[i] == ch && !quoted) return i }
        return -1
    }

    fun splitOutsideQuotes(s: String, ch: Char): List<String> {
        val out = mutableListOf<String>()
        var rest = s
        while (true) {
            val i = indexOutsideQuotes(rest, ch)
            if (i < 0) { if (rest.isNotEmpty()) out.add(rest); return out }
            if (i > 0) out.add(rest.substring(0, i))
            rest = rest.substring(i + 1)
        }
    }

    /** An IANA name as written, Mozilla's "/mozilla.org/…/Europe/Athens", or Outlook's Windows name; else this device's. */
    fun zone(tzid: String?): ZoneId {
        if (tzid.isNullOrBlank()) return ZoneId.systemDefault()
        runCatching { return ZoneId.of(tzid) }
        val parts = tzid.trim('/').split('/')
        for (k in parts.indices) runCatching { return ZoneId.of(parts.drop(k).joinToString("/")) }
        runCatching { android.icu.util.TimeZone.getIDForWindowsID(tzid, "001")?.let { return ZoneId.of(it) } }
        return ZoneId.systemDefault()
    }

    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /** A DATE or DATE-TIME property: the time in its own zone (UTC for "Z", the device's when floating), and whether it is a date. */
    fun time(p: ICalProp, deviceZone: ZoneId = ZoneId.systemDefault()): Pair<ZonedDateTime, Boolean>? = runCatching {
        val v = p.value.trim().substringBefore(',')
        if (p.param("VALUE").equals("DATE", true) || v.length == 8) LocalDate.parse(v.take(8), DATE).atStartOfDay(deviceZone) to true
        else if (v.endsWith("Z", true)) LocalDateTime.parse(v.dropLast(1), DATE_TIME).atZone(ZoneOffset.UTC) to false
        else LocalDateTime.parse(v.take(15), DATE_TIME).atZone(p.param("TZID")?.let(::zone) ?: deviceZone) to false
    }.getOrNull()

    /** The value and params to write [t] as DTSTART/DTEND/EXDATE/RECURRENCE-ID. */
    fun timeValue(t: ZonedDateTime, date: Boolean): Pair<String, String> = when {
        date -> DATE.format(t) to ";VALUE=DATE"
        t.zone == ZoneOffset.UTC -> DATE_TIME.format(t) + "Z" to ""
        else -> DATE_TIME.format(t) to ";TZID=${t.zone.id}"
    }

    /** RFC 5545's duration, e.g. -P2DT15H, as nominal days and seconds (days follow the clock across a DST change). */
    fun duration(s: String): Pair<Long, Long>? {
        val m = Regex("([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?").matchEntire(s.trim().uppercase()) ?: return null
        val sign = if (m.groupValues[1] == "-") -1 else 1
        fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0
        return sign * (g(2) * 7 + g(3)) to sign * (g(4) * 3600 + g(5) * 60 + g(6))
    }

    fun durationValue(days: Long, seconds: Long): String {
        val total = days * 86400 + seconds
        val a = Math.abs(total)
        val d = a / 86400; val h = a % 86400 / 3600; val m = a % 3600 / 60; val sec = a % 60
        val time = (if (h > 0) "${h}H" else "") + (if (m > 0) "${m}M" else "") + (if (sec > 0) "${sec}S" else "")
        return (if (total < 0) "-" else "") + "P" + (if (d > 0) "${d}D" else "") + when {
            time.isNotEmpty() -> "T$time"
            d == 0L -> "T0S"
            else -> ""
        }
    }
}

/**
 * A reminder (VALARM): [days] and [seconds] from the start (or the end), or at a set time; [repeat] more after it,
 * [every] seconds apart — RFC 5545's REPEAT/DURATION, which is how "3 days before at 9:00, each day" is written.
 */
data class CalAlarm(val days: Long, val seconds: Long, val fromEnd: Boolean = false, val at: Instant? = null, val repeat: Int = 0, val every: Long = 0) {
    fun times(start: ZonedDateTime, end: ZonedDateTime): List<ZonedDateTime> {
        val first = at?.atZone(start.zone) ?: (if (fromEnd) end else start).plusDays(days).plusSeconds(seconds)
        return (0..repeat.coerceIn(0, 100)).map { first.plusSeconds(every * it) }
    }
}

/** One VEVENT: the event itself or, with [recurrenceId], one changed day of a repeating one. Times in their own zone. */
data class CalEvent(
    val uid: String,
    val title: String,
    val place: String,
    val notes: String,
    val allDay: Boolean,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val rrule: String? = null,
    val exdates: List<ZonedDateTime> = emptyList(),
    val recurrenceId: ZonedDateTime? = null,
    val cancelled: Boolean = false,
    val alarms: List<CalAlarm> = emptyList(),
    val modified: Instant? = null,
)

/** An event file: the event, its changed days, and the parsed file kept for writing it back. */
class CalFile(val file: File, val comps: List<ICalComp>, val master: CalEvent?, val overrides: List<CalEvent>) {
    val uid get() = master?.uid ?: overrides.firstOrNull()?.uid ?: file.nameWithoutExtension
    val modified: Instant get() = (listOfNotNull(master) + overrides).mapNotNull { it.modified }.maxOrNull() ?: Instant.ofEpochMilli(file.lastModified())
    /** Copies of this event in the same calendar (sync's "x (2).ics" after a change on two devices); 1 when alone. */
    var versions = 1
}

data class CalendarInfo(val name: String, val dir: File, val color: Int)

/** One time an event happens, in the range asked for. */
data class Occurrence(val calendar: CalendarInfo, val file: CalFile, val event: CalEvent, val start: ZonedDateTime, val end: ZonedDateTime) {
    val allDay get() = event.allDay
}

object CalendarFiles {
    const val FOLDER = "Calendar"

    /** Google Calendar's colours, with Tetra's pink first: the picker's palette and new calendars' defaults. */
    val PALETTE = listOf(0xFFE91E63, 0xFFD50000, 0xFFE67C73, 0xFFF4511E, 0xFFF6BF26, 0xFF33B679, 0xFF0B8043,
        0xFF039BE5, 0xFF3F51B5, 0xFF7986CB, 0xFF8E24AA, 0xFF616161).map { it.toInt() }

    fun root(tetra: File) = File(tetra, FOLDER)

    fun calendars(root: File): List<CalendarInfo> = (root.listFiles() ?: emptyArray())
        .filter { it.isDirectory && !it.name.startsWith(".") }
        .sortedBy { it.name.lowercase() }
        .mapIndexed { i, dir ->
            val name = File(dir, "displayname").takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null } ?: dir.name
            CalendarInfo(name, dir, color(dir) ?: PALETTE[i % PALETTE.size])
        }

    fun color(dir: File): Int? = File(dir, "color").takeIf { it.isFile }?.readText()?.trim()?.removePrefix("#")
        ?.take(6)?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }

    fun colorText(color: Int) = "#%06X".format(color and 0xFFFFFF)

    fun newCalendar(root: File, name: String, color: Int): CalendarInfo {
        val clean = name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "-").trimStart('.').ifEmpty { "Calendar" }
        val dir = File(root, clean)
        require(!dir.exists()) { "A calendar named $clean already exists." }
        require(dir.mkdirs()) { "Could not create $clean." }
        setColor(dir, color)
        return CalendarInfo(clean, dir, color)
    }

    fun setColor(dir: File, color: Int) = File(dir, "color").writeText(colorText(color) + "\n")

    private val cache = HashMap<String, Triple<Long, Long, CalFile>>()

    /** Every event file of a calendar; a file is read again only when its size or time changed. */
    fun files(cal: CalendarInfo): List<CalFile> {
        val all = (cal.dir.listFiles() ?: emptyArray()).filter { it.isFile && it.name.endsWith(".ics", true) && !it.name.startsWith(".") }
        val read = all.map { f ->
            synchronized(cache) {
                cache[f.path]?.takeIf { it.first == f.length() && it.second == f.lastModified() }?.third
            } ?: read(f).also { synchronized(cache) { cache[f.path] = Triple(f.length(), f.lastModified(), it) } }
        }
        // The same event twice (a conflict in sync): the newer is shown and says so; the other stays on disk.
        return read.groupBy { it.uid }.values.map { same -> same.maxBy { it.modified }.also { it.versions = same.size } }
    }

    fun read(f: File): CalFile = parse(f, runCatching { f.readText() }.getOrDefault(""))

    fun parse(f: File, text: String, deviceZone: ZoneId = ZoneId.systemDefault()): CalFile {
        val comps = ICal.parse(text)
        val events = comps.flatMap { if (it.name == "VEVENT") listOf(it) else it.comps("VEVENT") }.mapNotNull { event(it, deviceZone) }
        return CalFile(f, comps, events.firstOrNull { it.recurrenceId == null }, events.filter { it.recurrenceId != null })
    }

    fun event(c: ICalComp, deviceZone: ZoneId = ZoneId.systemDefault()): CalEvent? {
        val (start, allDay) = c.prop("DTSTART")?.let { ICal.time(it, deviceZone) } ?: return null
        val end = c.prop("DTEND")?.let { ICal.time(it, deviceZone)?.first }
            ?: c.prop("DURATION")?.let { ICal.duration(it.value) }?.let { (d, s) -> start.plusDays(d).plusSeconds(s) }
            ?: if (allDay) start.plusDays(1) else start
        return CalEvent(
            uid = c.prop("UID")?.value?.trim().orEmpty(),
            title = c.prop("SUMMARY")?.text.orEmpty(),
            place = c.prop("LOCATION")?.text.orEmpty(),
            notes = c.prop("DESCRIPTION")?.text.orEmpty(),
            allDay = allDay,
            start = start,
            end = if (end.isAfter(start)) end else if (allDay) start.plusDays(1) else start,
            rrule = c.prop("RRULE")?.value?.trim(),
            exdates = c.props("EXDATE").flatMap { p -> p.value.split(',').mapNotNull { v -> ICal.time(ICalProp(p.name, p.params, v), deviceZone)?.first } },
            recurrenceId = c.prop("RECURRENCE-ID")?.let { ICal.time(it, deviceZone)?.first },
            cancelled = c.prop("STATUS")?.value.equals("CANCELLED", true),
            alarms = c.comps("VALARM").mapNotNull(::alarm),
            modified = (c.prop("LAST-MODIFIED") ?: c.prop("DTSTAMP"))?.let { ICal.time(it, deviceZone)?.first?.toInstant() },
        )
    }

    private fun alarm(c: ICalComp): CalAlarm? {
        val trigger = c.prop("TRIGGER") ?: return null
        val repeat = c.prop("REPEAT")?.value?.trim()?.toIntOrNull() ?: 0
        val every = c.prop("DURATION")?.let { ICal.duration(it.value) }?.let { (d, s) -> d * 86400 + s } ?: 0
        if (trigger.param("VALUE").equals("DATE-TIME", true)) {
            val at = ICal.time(trigger)?.first?.toInstant() ?: return null
            return CalAlarm(0, 0, at = at, repeat = repeat, every = every)
        }
        val (d, s) = ICal.duration(trigger.value) ?: return null
        return CalAlarm(d, s, fromEnd = trigger.param("RELATED").equals("END", true), repeat = repeat, every = every)
    }

    /** Every time the calendars' events happen between [from] and [to] (an event overlapping the edge counts), by start. */
    fun occurrences(cals: List<CalendarInfo>, from: Instant, to: Instant): List<Occurrence> =
        cals.flatMap { cal -> files(cal).flatMap { occurrences(cal, it, from, to) } }.sortedBy { it.start.toInstant() }

    fun occurrences(cal: CalendarInfo, f: CalFile, from: Instant, to: Instant): List<Occurrence> {
        val out = mutableListOf<Occurrence>()
        fun add(e: CalEvent, s: ZonedDateTime, end: ZonedDateTime) {
            if (!e.cancelled && s.toInstant() < to && (end.toInstant() > from || s.toInstant() >= from)) out.add(Occurrence(cal, f, e, s, end))
        }
        val m = f.master
        val moved = f.overrides.mapNotNull { it.recurrenceId?.toInstant() }.toSet()
        if (m != null) {
            if (m.rrule == null) add(m, m.start, m.end)
            else {
                val length = java.time.Duration.between(m.start, m.end)
                val skip = m.exdates.map { it.toInstant() }.toSet() + moved
                instants(m, from.minus(length), to).forEach { s -> if (s.toInstant() !in skip) add(m, s, s.plus(length)) }
            }
        }
        f.overrides.forEach { add(it, it.start, it.end) }
        return out
    }

    /** The starts of a repeating event from [from] on, until [to], in the event's own clock (09:00 stays 09:00 after DST). */
    fun instants(e: CalEvent, from: Instant, to: Instant): List<ZonedDateTime> {
        val rule = runCatching { RecurrenceRule(e.rrule, RecurrenceRule.RfcMode.RFC5545_LAX) }.getOrNull() ?: return listOf(e.start)
        val s = e.start
        val first = if (e.allDay) RecurTime(s.year, s.monthValue - 1, s.dayOfMonth)
            else RecurTime(TimeZone.getTimeZone(s.zone), s.year, s.monthValue - 1, s.dayOfMonth, s.hour, s.minute, s.second)
        val it = rule.iterator(first)
        if (from.isAfter(s.toInstant())) it.fastForward(from.toEpochMilli() - 86_400_000L * 2) // a day's margin for floating dates
        val out = mutableListOf<ZonedDateTime>()
        var guard = 0
        while (it.hasNext() && guard++ < 20_000) {
            val t = it.nextDateTime()
            val z = if (e.allDay) LocalDate.of(t.year, t.month + 1, t.dayOfMonth).atStartOfDay(s.zone)
                else LocalDateTime.of(t.year, t.month + 1, t.dayOfMonth, t.hours, t.minutes, t.seconds).atZone(s.zone)
            if (!z.toInstant().isBefore(to)) break
            if (!z.toInstant().isBefore(from)) out.add(z)
        }
        return out
    }

    // ——— Writing ———

    /** The file name for a new event: its title and six letters of its UID; never renamed later (a rename is a move in sync). */
    fun fileName(title: String, uid: String): String {
        val slug = title.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40).trimEnd('-').ifEmpty { "event" }
        return "$slug-${uid.filter { it.isLetterOrDigit() }.take(6).lowercase()}.ics"
    }

    fun newUid() = UUID.randomUUID().toString()

    /**
     * Writes [e] as the event of [file] (a new file when it does not exist): what the editor knows is replaced, every
     * other line (attendees, Google's and Outlook's own, changed days) kept as it was.
     */
    fun save(file: File, e: CalEvent, now: Instant = Instant.now()): CalFile {
        val comps = if (file.isFile) ICal.parse(file.readText()) else emptyList()
        val cal = comps.firstOrNull { it.name == "VCALENDAR" } ?: ICalComp("VCALENDAR", mutableListOf(
            ICalProp("VERSION", "", "2.0"), ICalProp("PRODID", "", "-//Tetra//Calendar//EN")))
        val vevent = cal.comps("VEVENT").firstOrNull { it.prop("RECURRENCE-ID") == null }
            ?: ICalComp("VEVENT").also { cal.items.add(cal.items.indexOfFirst { it is ICalComp && it.name == "VEVENT" }.let { i -> if (i < 0) cal.items.size else i }, it) }
        fill(vevent, e, now, isNew = vevent.prop("DTSTART") == null)
        val all = if (comps.isEmpty()) listOf(cal) else comps
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, ".${file.name}.part")
        tmp.writeText(ICal.write(all))
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
        return read(file)
    }

    private fun fill(v: ICalComp, e: CalEvent, now: Instant, isNew: Boolean) {
        val stamp = ICal.timeValue(now.atZone(ZoneOffset.UTC), false).first
        v.set("UID", e.uid)
        v.set("DTSTAMP", stamp)
        v.set("LAST-MODIFIED", stamp)
        v.set("SEQUENCE", if (isNew) "0" else ((v.prop("SEQUENCE")?.value?.trim()?.toIntOrNull() ?: 0) + 1).toString())
        v.set("SUMMARY", ICal.escape(e.title))
        ICal.timeValue(e.start, e.allDay).let { (value, params) -> v.set("DTSTART", value, params) }
        v.remove("DURATION")
        ICal.timeValue(e.end, e.allDay).let { (value, params) -> v.set("DTEND", value, params) }
        v.set("LOCATION", e.place.ifEmpty { null }?.let(ICal::escape))
        v.set("DESCRIPTION", e.notes.ifEmpty { null }?.let(ICal::escape))
        v.set("RRULE", e.rrule)
        v.remove("EXDATE")
        e.exdates.forEach { x -> ICal.timeValue(x, e.allDay).let { (value, params) -> v.items.add(v.items.indexOfFirst { it is ICalComp }.let { if (it < 0) v.items.size else it }, ICalProp("EXDATE", params, value)) } }
        v.items.removeAll { it is ICalComp && it.name == "VALARM" }
        e.alarms.forEach { v.items.add(alarmComp(it)) }
    }

    fun alarmComp(a: CalAlarm) = ICalComp("VALARM").apply {
        items.add(ICalProp("ACTION", "", "DISPLAY"))
        items.add(ICalProp("DESCRIPTION", "", "Reminder"))
        if (a.at != null) items.add(ICalProp("TRIGGER", ";VALUE=DATE-TIME", ICal.timeValue(a.at.atZone(ZoneOffset.UTC), false).first))
        else items.add(ICalProp("TRIGGER", if (a.fromEnd) ";RELATED=END" else "", ICal.durationValue(a.days, a.seconds)))
        if (a.repeat > 0) {
            items.add(ICalProp("REPEAT", "", a.repeat.toString()))
            items.add(ICalProp("DURATION", "", ICal.durationValue(0, a.every)))
        }
    }
}
