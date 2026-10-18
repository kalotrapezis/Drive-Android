package com.kalotrapezis.drive

import android.content.Context
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The Calendar's screens (Plan.md § Calendar › Screens). A big screen as Google Calendar on the web: a side panel
 * (a small month, the calendars with their coloured boxes) and a month, week or day grid. A phone as Outlook: the week
 * row (pulled down: the month) over the agenda of the days. Everything is read from Tetra/Calendar's files.
 */

internal enum class CalView(val label: String) { Agenda("Agenda"), Day("Day"), ThreeDays("3 days"), Week("Week"), Month("Month"), Year("Year") }

/** The room the floating top (Files' pills) takes; the views start under it, the scrolling ones scroll beneath it. */
private val HEADER = 72.dp

internal fun calendarRoot() = CalendarFiles.root(TetraFolder.root(Environment.getExternalStorageDirectory()))

/** Which calendars this device hides (the checkboxes) and the last view: this screen's choices, not the files'. */
private object CalendarPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("calendar", Context.MODE_PRIVATE)
    fun hidden(c: Context): Set<String> = prefs(c).getStringSet("hidden", emptySet())!!.toSet()
    fun setHidden(c: Context, s: Set<String>) = prefs(c).edit().putStringSet("hidden", s).apply()
    fun view(c: Context, wide: Boolean): CalView = runCatching { CalView.valueOf(prefs(c).getString(if (wide) "viewWide" else "view", null)!!) }
        .getOrDefault(if (wide) CalView.Month else CalView.Agenda)
    fun last(c: Context): String? = prefs(c).getString("lastCalendar", null)
    fun setLast(c: Context, name: String) = prefs(c).edit().putString("lastCalendar", name).apply()
    fun stripWeeks(c: Context) = prefs(c).getInt("stripWeeks", 1)
    fun setStripWeeks(c: Context, n: Int) = prefs(c).edit().putInt("stripWeeks", n).apply()
    fun setView(c: Context, wide: Boolean, v: CalView) = prefs(c).edit().putString(if (wide) "viewWide" else "view", v.name).apply()
}

/** The editor open: on an occurrence (null: a new event), its draft and calendar. */
private class Editing(val occ: Occurrence?, val draft: CalEvent, val calendar: CalendarInfo?, val heading: String? = null)
private class Asking(val deleting: Boolean, val then: (Scope) -> Unit)

/** "All events" from one day: the change of that day's time moved onto the series' own start. */
private fun saveAll(f: CalFile, m: CalEvent?, o: Occurrence, e: CalEvent, moveTo: CalendarInfo?) {
    val base = m ?: return run { CalendarFiles.save(f.file, e) }
    val was = o.event.recurrenceId ?: o.start
    val shift = ChronoUnit.DAYS.between(was.toLocalDate(), e.start.toLocalDate())
    val start = base.start.toLocalDate().plusDays(shift).atTime(e.start.toLocalTime()).atZone(e.start.zone)
    CalendarFiles.save(f.file, base.copy(title = e.title, place = e.place, notes = e.notes, allDay = e.allDay, rrule = e.rrule, alarms = e.alarms,
        start = start, end = start.plus(java.time.Duration.between(e.start, e.end))))
    if (moveTo != null) CalendarFiles.move(f.file, moveTo)
}

private class CalData(val cals: List<CalendarInfo>, val occ: List<Occurrence>)

/** The days an occurrence covers on this device's clock: an all-day trip each of its days, a late party the next too. */
internal fun daysOf(o: Occurrence, zone: ZoneId): List<LocalDate> {
    val s = if (o.allDay) o.start.toLocalDate() else o.start.withZoneSameInstant(zone).toLocalDate()
    val e = if (o.allDay) o.end.toLocalDate().minusDays(1) else o.end.withZoneSameInstant(zone).minusNanos(1).toLocalDate()
    return generateSequence(s) { it.plusDays(1) }.takeWhile { !it.isAfter(e) }.take(366).toList().ifEmpty { listOf(s) }
}

internal fun byDay(occ: List<Occurrence>, zone: ZoneId): Map<LocalDate, List<Occurrence>> {
    val map = HashMap<LocalDate, MutableList<Occurrence>>()
    occ.forEach { o -> daysOf(o, zone).forEach { map.getOrPut(it) { mutableListOf() }.add(o) } }
    return map.mapValues { (_, l) -> l.sortedWith(compareBy<Occurrence>({ !it.allDay }, { it.start.toInstant() }, { it.event.title })) }
}

private fun firstDay(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
private fun weekOf(d: LocalDate): List<LocalDate> = d.with(TemporalAdjusters.previousOrSame(firstDay())).let { s -> (0L..6).map { s.plusDays(it) } }
private fun monthCells(ym: YearMonth): List<LocalDate> = ym.atDay(1).with(TemporalAdjusters.previousOrSame(firstDay())).let { s -> (0L..41).map { s.plusDays(it) } }

internal fun timeText(context: Context, t: ZonedDateTime): String =
    t.withZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.ENGLISH))

internal fun lengthText(minutes: Long): String = when {
    minutes <= 0 -> ""
    minutes % 1440 == 0L -> "${minutes / 1440}d"
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0L -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

private val ORDINAL = mapOf(1 to "first", 2 to "second", 3 to "third", 4 to "fourth", 5 to "fifth", -1 to "last", -2 to "second last")
private fun dayName(code: String) = DayOfWeek.entries.firstOrNull { it.name.take(2) == code.takeLast(2) }?.getDisplayName(TextStyle.FULL, Locale.ENGLISH) ?: code

/** An RRULE in words, as Google writes it: "Weekly on Tuesday", "Monthly on the third Tuesday", ", 6 times". */
internal fun repeatText(rule: String?, start: ZonedDateTime): String? {
    if (rule.isNullOrBlank()) return null
    val p = rule.split(';').associate { it.substringBefore('=').uppercase() to it.substringAfter('=') }
    val n = p["INTERVAL"]?.toIntOrNull() ?: 1
    val unit = when (p["FREQ"]) { "DAILY" -> "day"; "WEEKLY" -> "week"; "MONTHLY" -> "month"; "YEARLY" -> "year"; else -> return "Repeats" }
    val byDay = p["BYDAY"]?.split(',').orEmpty()
    val weekdays = listOf("MO", "TU", "WE", "TH", "FR")
    val head = when {
        unit == "week" && n == 1 && byDay.sorted() == weekdays.sorted() -> "Every weekday"
        n == 1 -> mapOf("day" to "Daily", "week" to "Weekly", "month" to "Monthly", "year" to "Yearly")[unit]!!
        else -> "Every $n ${unit}s"
    }
    val on = when {
        head == "Every weekday" -> ""
        unit == "week" && byDay.isNotEmpty() -> " on " + byDay.joinToString(", ") { dayName(it) }
        unit == "week" -> " on " + start.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        unit == "month" && byDay.isNotEmpty() -> byDay.first().let { d ->
            val k = d.dropLast(2).toIntOrNull() ?: p["BYSETPOS"]?.toIntOrNull()
            " on the " + (ORDINAL[k]?.let { "$it " } ?: "") + dayName(d)
        }
        unit == "month" -> " on day ${p["BYMONTHDAY"] ?: start.dayOfMonth}"
        unit == "year" -> " on ${start.dayOfMonth} ${start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
        else -> ""
    }
    val end = p["COUNT"]?.let { ", $it times" } ?: p["UNTIL"]?.let { u ->
        runCatching { ", until " + LocalDate.parse(u.take(8), DateTimeFormatter.BASIC_ISO_DATE).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) }.getOrNull()
    } ?: ""
    return head + on + end
}

private fun spanText(seconds: Long): String {
    val s = Math.abs(seconds)
    val parts = listOfNotNull((s / 604800).takeIf { it > 0 && s % 604800 == 0L }?.let { "$it week" + if (it > 1) "s" else "" })
        .ifEmpty {
            listOfNotNull((s / 86400).takeIf { it > 0 }?.let { "$it day" + if (it > 1) "s" else "" },
                (s % 86400 / 3600).takeIf { it > 0 }?.let { "$it hour" + if (it > 1) "s" else "" },
                (s % 3600 / 60).takeIf { it > 0 }?.let { "$it minute" + if (it > 1) "s" else "" })
        }
    return parts.joinToString(" ")
}

/**
 * A reminder in words: "30 minutes before", "The day before at 09:00", "3 days before at 09:00 · every day, 3 times".
 * Anything a day or more before says the clock time it rings at, worked out from [start].
 */
internal fun alarmText(a: CalAlarm, allDay: Boolean, start: ZonedDateTime): String {
    val t = a.days * 86400 + a.seconds
    val base = when {
        a.at != null -> "At " + a.at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH))
        a.fromEnd -> if (t == 0L) "At the end" else spanText(t) + if (t < 0) " before the end" else " after the end"
        t <= 0 && (allDay || t <= -86400) -> {
            val fires = start.plusDays(a.days).plusSeconds(a.seconds)
            val before = ChronoUnit.DAYS.between(fires.toLocalDate(), start.toLocalDate())
            val clock = fires.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            when (before) { 0L -> "On the day at $clock"; 1L -> "The day before at $clock"; 7L -> "A week before at $clock"; else -> "$before days before at $clock" }
        }
        t == 0L -> "At the time"
        else -> spanText(t) + if (t < 0) " before" else " after"
    }
    return if (a.repeat > 0) "$base · every ${spanText(a.every).removePrefix("1 ")}, ${a.repeat + 1} times" else base
}

/** The Calendar's own pink (its icon's, Home's group): today and the chosen day, as Google's blue. */
internal val CalendarPink = Color(0xFFF41879)

private fun onColor(c: Color) = if (c.luminance() > 0.55f) Color.Black else Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarScreen(back: () -> Unit, hasAccess: Boolean, grant: () -> Unit, startKind: NewKind? = null) {
    val context = LocalContext.current
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    val zone = remember { ZoneId.systemDefault() }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); today = LocalDate.now() } }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var view by remember(wide) { mutableStateOf(CalendarPrefs.view(context, wide)) }
    var version by remember { mutableIntStateOf(0) }
    var hidden by remember { mutableStateOf(CalendarPrefs.hidden(context)) }
    var drawer by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<Occurrence?>(null) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var asking by remember { mutableStateOf<Asking?>(null) } // a repeating event's This / Following / All
    var calDialog by remember { mutableStateOf<CalendarInfo?>(null) }
    var newCal by remember { mutableStateOf(false) }
    var deleteCal by remember { mutableStateOf<CalendarInfo?>(null) }
    var birthday by remember { mutableStateOf(false) }
    var schedule by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) { if (message != null) { delay(3000); message = null } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val root = remember { calendarRoot() }

    if (!hasAccess) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
            Text("Your calendars are files in Tetra's folder, so they sync with the computer and Claude can manage them. Allow access to all files to use the Calendar.")
            TextButton(onClick = grant) { Text("Allow access") }
        }
        return
    }
    // A file changed by sync, by another app or by Claude on the computer shows as soon as it lands.
    DisposableEffect(root) {
        root.mkdirs()
        val watcher = DriveWatcher(root) { version++; CalendarAlarms.planSoon(context) }
        onDispose { watcher.stop() }
    }
    val anchor = selected.year
    val data by produceState<CalData?>(null, version, anchor) {
        value = withContext(Dispatchers.IO) {
            val cals = CalendarFiles.calendars(root)
            val from = LocalDate.of(anchor - 1, 1, 1).atStartOfDay(zone).toInstant()
            val to = LocalDate.of(anchor + 2, 1, 1).atStartOfDay(zone).toInstant()
            CalData(cals, CalendarFiles.occurrences(cals, from, to))
        }
    }
    val cals = data?.cals.orEmpty()
    val days = remember(data, hidden) { byDay(data?.occ.orEmpty().filter { it.calendar.dir.name !in hidden }, zone) }
    fun toggle(c: CalendarInfo) {
        hidden = if (c.dir.name in hidden) hidden - c.dir.name else hidden + c.dir.name
        CalendarPrefs.setHidden(context, hidden)
    }
    fun setView(v: CalView) { view = v; CalendarPrefs.setView(context, wide, v) }
    /** Writes on the IO thread and says what [work] answers; the folder's watcher shows the result, the reminders follow. */
    fun writeSaying(work: () -> String) = scope.launch {
        val done = withContext(Dispatchers.IO) { runCatching(work).also { runCatching { CalendarAlarms.plan(context) } } }
        message = done.getOrElse { "Not saved: ${it.message ?: it.javaClass.simpleName}" }
        done.exceptionOrNull()?.let { WriterLog.e("Calendar: a change failed", it) }
        version++
    }
    fun write(said: String, work: () -> Unit) = writeSaying { work(); said }
    // Import: an .ics or Takeout's zip, from the picker or opened from another app
    var incoming by remember { mutableStateOf<List<CalendarTransfer.Incoming>?>(null) }
    fun readImport(uri: android.net.Uri) = scope.launch {
        val got = withContext(Dispatchers.IO) { runCatching {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: uri.lastPathSegment.orEmpty()
            if (bytes.size > 1 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) CalendarTransfer.readZip(bytes)
            else listOf(CalendarTransfer.read(bytes.toString(Charsets.UTF_8), name.substringAfterLast('/').removeSuffix(".ics").ifEmpty { "Imported" }))
        } }
        got.onSuccess { l -> l.filter { it.events.isNotEmpty() }.let { if (it.isEmpty()) message = "No events in that file." else incoming = it } }
            .onFailure { message = "Could not read it: ${it.message ?: it.javaClass.simpleName}"; WriterLog.e("Calendar: import failed", it) }
    }
    val pickImport = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { it?.let(::readImport) }
    LaunchedEffect(Unit) { CalendarInbox.pending.collect { u -> if (u != null) { CalendarInbox.pending.value = null; readImport(u) } } }
    // Export: one calendar as an .ics, or all as a zip, saved where the person chooses
    var exporting by remember { mutableStateOf<CalendarInfo?>(null) }
    fun saveTo(uri: android.net.Uri?, bytes: () -> ByteArray) { if (uri != null) writeSaying { context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes()) }; "Exported" } }
    val exportOne = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        exporting?.let { c -> saveTo(uri) { CalendarTransfer.export(c).toByteArray() } }; exporting = null
    }
    val exportAll = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        saveTo(uri) { CalendarTransfer.exportAll(CalendarFiles.calendars(root)) }
    }
    fun create(day: LocalDate, minute: Int? = null) {
        val now = java.time.LocalTime.now()
        val at = minute?.let { java.time.LocalTime.of(it / 60, it % 60) }
            ?: if (day == today) now.plusMinutes(60L - now.minute).withSecond(0).withNano(0).takeIf { it.isAfter(now) } ?: java.time.LocalTime.of(23, 0) else java.time.LocalTime.of(9, 0)
        val start = day.atTime(at).atZone(zone)
        val cal = cals.firstOrNull { it.dir.name == CalendarPrefs.last(context) && it.dir.name !in hidden } ?: cals.firstOrNull { it.dir.name !in hidden } ?: cals.firstOrNull()
        editing = Editing(null, CalEvent(CalendarFiles.newUid(), "", "", "", false, start, start.plusHours(1), alarms = defaultAlarms(false, start)), cal)
    }
    // + and Home's tiles: New, Schedule, Birthday, Reminder (the user, 18 Oct)
    fun start(k: NewKind) {
        when (k) {
            NewKind.Event -> create(selected)
            NewKind.Reminder -> { create(selected); editing = editing?.let { Editing(null, it.draft.copy(end = it.draft.start.plusMinutes(15), alarms = listOf(CalAlarm(0, 0))), it.calendar, "New reminder") } }
            NewKind.Birthday -> birthday = true
            NewKind.Schedule -> schedule = true
        }
    }
    LaunchedEffect(startKind, data != null) { if (startKind != null && data != null && editing == null) start(startKind) }
    fun saveEvent(ed: Editing, e: CalEvent, target: CalendarInfo?, scopeChoice: Scope?) = write(if (ed.occ == null) "Event created" else "Saved") {
        val to = target ?: CalendarFiles.calendars(root).firstOrNull() ?: CalendarFiles.newCalendar(root, "Personal", CalendarFiles.PALETTE[0])
        CalendarPrefs.setLast(context, to.dir.name)
        val o = ed.occ
        if (o == null) { CalendarFiles.save(CalendarFiles.newFile(to.dir, e.title, e.uid), e); return@write }
        val f = o.file
        val m = f.master
        val day = o.event.recurrenceId ?: o.start
        val moveTo = to.takeIf { it.dir != o.calendar.dir }
        when (if (m?.rrule == null && o.event.recurrenceId == null) null else scopeChoice) {
            null -> CalendarFiles.save(f.file, e).also { if (moveTo != null) CalendarFiles.move(f.file, moveTo) }
            Scope.One -> CalendarFiles.saveDay(f, day, e)
            Scope.Following -> if (m == null || day.toInstant() == m.start.toInstant()) saveAll(f, m, o, e, moveTo) else {
                val uid = CalendarFiles.newUid()
                CalendarFiles.split(f, day, CalendarFiles.newFile(to.dir, e.title, uid), e.copy(uid = uid, recurrenceId = null, exdates = emptyList(),
                    rrule = e.rrule?.split(';')?.filterNot { it.startsWith("COUNT=", true) }?.joinToString(";")))
            }
            Scope.All -> saveAll(f, m, o, e, moveTo)
        }
    }
    fun deleteEvent(o: Occurrence, scopeChoice: Scope?) = write("Deleted") {
        val f = o.file
        val m = f.master
        val day = o.event.recurrenceId ?: o.start
        when (if (m?.rrule == null && o.event.recurrenceId == null) null else scopeChoice) {
            null, Scope.All -> CalendarFiles.trash(root, f.file)
            Scope.One -> CalendarFiles.skipDay(f, day)
            Scope.Following -> if (m == null || day.toInstant() <= m.start.toInstant()) CalendarFiles.trash(root, f.file) else CalendarFiles.endBefore(f, day)
        }
    }
    // the user, 18 Oct: on a wide screen the island holds only Day · Week · Month · Year
    val views = if (wide) listOf(CalView.Day, CalView.Week, CalView.Month, CalView.Year) else listOf(CalView.Agenda, CalView.Day, CalView.ThreeDays, CalView.Month)
    LaunchedEffect(wide) { if (view !in views) setView(views.first()) }
    BackHandler { when { shown != null -> shown = null; drawer -> drawer = false; else -> back() } }
    editing?.let { ed ->
        val repeating = ed.occ != null && (ed.occ.file.master?.rrule != null || ed.occ.event.recurrenceId != null)
        EventEditor(ed.draft, ed.calendar, cals, isNew = ed.occ == null, wide = wide, cancel = { editing = null }, heading = ed.heading) { e, target ->
            if (repeating) asking = Asking(false) { sc -> saveEvent(ed, e, target, sc); editing = null }
            else { saveEvent(ed, e, target, null); editing = null }
        }
        asking?.let { a -> ScopeDialog(a.deleting, { asking = null }) { sc -> asking = null; a.then(sc) } }
        if (!wide) return
    }
    val open: (Occurrence) -> Unit = { shown = it }

    val body: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            when (view) {
                CalView.Month -> Box(Modifier.padding(top = HEADER)) { MonthGrid(YearMonth.from(selected), days, today, selected, compact = !wide, pick = { selected = it; if (!wide) setView(CalView.Agenda) }, open = open) }
                CalView.Week -> Box(Modifier.padding(top = HEADER)) { TimeGrid(weekOf(selected), days, today, open, ::create) }
                CalView.ThreeDays -> Box(Modifier.padding(top = HEADER)) { TimeGrid((0L..2).map { selected.plusDays(it) }, days, today, open, ::create) }
                CalView.Day -> Box(Modifier.padding(top = HEADER)) { TimeGrid(listOf(selected), days, today, open, ::create) }
                CalView.Agenda -> Agenda(anchor, days, today, selected, { selected = it }, open, top = HEADER)
                CalView.Year -> YearGrid(selected, today, days.keys) { selected = it; setView(CalView.Day) }
            }
        }
    }

    // Tetra's own island at the bottom (Files', Notes'): the views and New; pulled up, the drawer with the rest (the user,
    // 18 Oct: "an island and bottom drawer that opens for the rest like what calendars to see", on the big screen too).
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                if (wide) Row(Modifier.weight(1f)) {
                    // the user, 18 Oct: on a wide screen the small month and the calendars stay in sight, beside the grid
                    Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).navigationBarsPadding()
                        .padding(start = 16.dp, end = 8.dp, top = HEADER, bottom = 84.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MiniMonth(selected, today, days.keys) { selected = it }
                        CalendarList(cals, hidden, ::toggle, { calDialog = it }, { newCal = true },
                            { pickImport.launch(arrayOf("text/calendar", "application/zip", "application/octet-stream", "*/*")) }, { exportAll.launch("Tetra calendars.zip") }, settings = false)
                    }
                    // the island floats over the last row; the views stop above it
                    body(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding().padding(end = 8.dp, bottom = 92.dp)) // clear of the islands
                } else {
                    // the user, 18 Oct: the events on top, the small calendar at the bottom within the thumb's reach
                    body(Modifier.weight(1f).fillMaxWidth())
                    if (view != CalView.Month) BottomStrip(selected, today, days) { selected = it }
                    Spacer(Modifier.navigationBarsPadding().height(92.dp)) // the island and its gap below
                }
            }
            // Files' bottom: the island at the left, the one round action at the right (here New event)
            // the user, 18 Oct: the pills float over the content, as in Files — no solid band behind them
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                // Files' own top: Back and the title in islands, the dates' controls beside them
                FilesPageHeader(
                    title = if (wide) title(selected, view) else selected.format(DateTimeFormatter.ofPattern(if (selected.year == today.year) "MMMM" else "MMMM yyyy", Locale.ENGLISH)),
                    icon = R.drawable.ic_calendar_today, back = back, island = true,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    // a wide screen has Today and the arrows at the bottom, by the + (the user, 18 Oct); the top keeps the title
                    trailing = if (wide) null else ({
                        Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { selected = today }) { Text("Today", color = islandContentColor()) }
                                if (wide || view != CalView.Agenda) {
                                    IconButton(onClick = { selected = step(selected, view, -1) }) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous") }
                                    IconButton(onClick = { selected = step(selected, view, 1) }) { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next") }
                                }
                            }
                        }
                    }),
                )
                ReminderNotes()
            }
            // on a wide screen it starts at the grid, the side column left in sight
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = if (wide) 300.dp else 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IslandBottomBar(expand = { drawer = true }, visible = !drawer) {
                    views.forEach { v -> IslandNavigationItem(v.label, view == v, viewIcon(v)) { setView(v) } }
                }
                IslandVisibility(!drawer) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // the user, 18 Oct: Today and ‹ › in their own island beside the +, within the thumb's reach
                    if (wide) Surface(color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.extraLarge) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(18.dp)) // the main island's handle row, so both stand the same height
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { selected = step(selected, view, -1) }) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous") }
                                TextButton(onClick = { selected = today }) { Text("Today", color = islandContentColor()) }
                                IconButton(onClick = { selected = step(selected, view, 1) }) { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next") }
                            }
                        }
                    }
                    Box {
                        var kinds by remember { mutableStateOf(false) }
                        Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                            IconButton(onClick = { kinds = true }) { Icon(painterResource(R.drawable.ic_add), contentDescription = "New") }
                        }
                        DropdownMenu(expanded = kinds, onDismissRequest = { kinds = false }) {
                            NewKind.entries.forEach { k -> DropdownMenuItem(text = { Text(k.label) }, leadingIcon = { Icon(painterResource(k.icon), contentDescription = null) }, onClick = { kinds = false; start(k) }) }
                        }
                    }
                } }
            }
            message?.let { m ->
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)) { Text(m, Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }
            }
        }
    }
    if (drawer) ModalBottomSheet(onDismissRequest = { drawer = false }, containerColor = SheetColor, contentColor = islandContentColor()) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (wide) { RemindersSwitch(); ShareWithClaude() }
            else Surface(color = Color.White.copy(alpha = 0.08f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    CalendarList(cals, hidden, ::toggle, { drawer = false; calDialog = it }, { drawer = false; newCal = true },
                        { drawer = false; pickImport.launch(arrayOf("text/calendar", "application/zip", "application/octet-stream", "*/*")) }, { drawer = false; exportAll.launch("Tetra calendars.zip") })
                }
            }
        }
    }
    shown?.let { o ->
        val repeating = o.file.master?.rrule != null || o.event.recurrenceId != null
        EventCard(o, close = { shown = null },
            edit = {
                shown = null
                val m = o.file.master
                // a day of a repeat edits with the repeat shown; the editor's date is that day's
                val draft = if (m != null && o.event.recurrenceId == null) m.copy(start = o.start, end = o.end) else o.event.copy(rrule = m?.rrule)
                editing = Editing(o, draft, o.calendar)
            },
            delete = { shown = null; if (repeating) asking = Asking(true) { sc -> deleteEvent(o, sc) } else deleteEvent(o, null) },
            keep = { shown = null; write("Kept this version; the other went to the Trash") { CalendarFiles.copies(o.calendar, o.file).forEach { CalendarFiles.trash(root, it) } } })
    }
    if (editing == null) asking?.let { a -> ScopeDialog(a.deleting, { asking = null }) { sc -> asking = null; a.then(sc) } }
    if (newCal) CalendarDialog(null, cals.map { it.name }.toSet(), { newCal = false }) { name, color ->
        newCal = false
        write("Calendar $name created") { CalendarFiles.newCalendar(root, name, color) }
    }
    calDialog?.let { c ->
        CalendarDialog(c, cals.map { it.name }.toSet(), { calDialog = null }, delete = { calDialog = null; deleteCal = c },
            export = { calDialog = null; exporting = c; exportOne.launch("${c.name}.ics") }) { name, color ->
            calDialog = null
            write("Saved") {
                CalendarFiles.setColor(c.dir, color)
                if (name != c.name) {
                    val to = java.io.File(root, name)
                    require(!to.exists() && c.dir.renameTo(to)) { "Could not rename ${c.name}." }
                    if (c.dir.name in hidden) { hidden = hidden - c.dir.name + name; CalendarPrefs.setHidden(context, hidden) }
                }
            }
        }
    }
    if (birthday) BirthdayDialog({ birthday = false }) { name, date, yearKnown ->
        birthday = false
        write("$name's birthday saved") {
            val cal = CalendarFiles.calendars(root).firstOrNull { it.name.equals("Birthdays", true) } ?: CalendarFiles.newCalendar(root, "Birthdays", 0xFF8E24AA.toInt())
            val start = (if (yearKnown) date else date.withYear(today.year)).atStartOfDay(zone)
            val uid = CalendarFiles.newUid()
            val f = CalendarFiles.save(CalendarFiles.newFile(cal.dir, "$name birthday", uid),
                CalEvent(uid, "$name's birthday", "", "", true, start, start.plusDays(1), rrule = "FREQ=YEARLY", alarms = listOf(daysBeforeAt(start, 0, java.time.LocalTime.of(9, 0), false))))
            if (yearKnown) CalendarFiles.setProp(f.file, "X-TETRA-BIRTH-YEAR", date.year.toString())
        }
    }
    if (schedule) {
        val holidays = cals.firstOrNull { it.name.equals("Holidays", true) }
        ScheduleDialog(cals, cals.firstOrNull { it.dir.name == CalendarPrefs.last(context) } ?: cals.firstOrNull(), holidays != null, { schedule = false }) { title, place, weekdays, at, until, from, to, skip, cal ->
            schedule = false
            write("Schedule saved") {
                val into = cal ?: CalendarFiles.calendars(root).firstOrNull() ?: CalendarFiles.newCalendar(root, "Personal", CalendarFiles.PALETTE[0])
                // the Holidays calendar's days (all day) between the two dates, as exceptions
                val skipDays = if (skip && holidays != null) CalendarFiles.occurrences(listOf(holidays), from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
                    .filter { it.allDay }.flatMap { daysOf(it, zone) } else emptyList()
                val e = scheduleEvent(CalendarFiles.newUid(), title, place, weekdays, at, until, from, to, skipDays, zone)
                CalendarFiles.save(CalendarFiles.newFile(into.dir, title, e.uid), e)
            }
        }
    }
    incoming?.let { list ->
        ImportDialog(list, cals, { incoming = null }) { choices ->
            incoming = null
            writeSaying {
                var added = 0; var updated = 0
                choices.forEach { (inc, chosen) ->
                    val all = CalendarFiles.calendars(root)
                    val to = chosen ?: all.firstOrNull { it.name.equals(inc.name, true) }
                        ?: CalendarFiles.newCalendar(root, inc.name, inc.color ?: CalendarFiles.PALETTE[all.size % CalendarFiles.PALETTE.size])
                    CalendarTransfer.write(to, inc).let { (a, u) -> added += a; updated += u }
                }
                "Imported: $added new" + if (updated > 0) ", $updated updated" else ""
            }
        }
    }
    deleteCal?.let { c ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { deleteCal = null }, title = { Text("Delete ${c.name}?") },
            text = { Text("The calendar's folder and all its events go to Tetra's Trash, on every device after the next sync. They can be restored from the Trash.") },
            confirmButton = { TextButton(onClick = { deleteCal = null; write("${c.name} moved to the Trash") { DriveRules.moveToTrash(root.parentFile!!, c.dir.relativeTo(root.parentFile!!).path) } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteCal = null }) { Text("Cancel") } })
    }
}

private fun step(d: LocalDate, v: CalView, n: Long): LocalDate = when (v) {
    CalView.Year -> d.plusYears(n)
    CalView.Day -> d.plusDays(n)
    CalView.ThreeDays -> d.plusDays(3 * n)
    CalView.Week, CalView.Agenda -> d.plusWeeks(n)
    CalView.Month -> d.plusMonths(n)
}

private fun title(d: LocalDate, v: CalView): String = when (v) {
    CalView.Day -> d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))
    CalView.Week, CalView.ThreeDays -> {
        val span = if (v == CalView.Week) weekOf(d) else (0L..2).map { d.plusDays(it) }
        val a = span.first(); val b = span.last()
        if (a.month == b.month) a.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        else a.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)) + " – " + b.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
    }
    CalView.Year -> d.year.toString()
    else -> d.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
}

private fun viewIcon(v: CalView) = when (v) {
    CalView.Agenda -> R.drawable.ic_view_list
    CalView.Day -> R.drawable.ic_view_day
    CalView.ThreeDays, CalView.Week -> R.drawable.ic_view_column
    CalView.Month -> R.drawable.ic_view_grid
    CalView.Year -> R.drawable.ic_calendar_today
}

@Composable private fun CalendarList(cals: List<CalendarInfo>, hidden: Set<String>, toggle: (CalendarInfo) -> Unit, edit: (CalendarInfo) -> Unit, add: () -> Unit, import: () -> Unit, exportAll: () -> Unit, settings: Boolean = true) {
    Column {
        Text("My calendars", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
        if (cals.isEmpty()) Text("No calendars yet. Each folder in Tetra/Calendar is one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        cals.forEach { c ->
            val color = Color(c.color)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { toggle(c) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = c.dir.name !in hidden, onCheckedChange = { toggle(c) },
                    colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color, checkmarkColor = onColor(color)))
                Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { edit(c) }) { Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "${c.name}: colour, name, delete") }
            }
        }
        TextButton(onClick = add) { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.padding(end = 8.dp)); Text("New calendar") }
        Row {
            TextButton(onClick = import) { Text("Import") }
            if (cals.isNotEmpty()) TextButton(onClick = exportAll) { Text("Export all") }
        }
        if (settings) { RemindersSwitch(); ShareWithClaude() }
    }
}

/**
 * Share with Claude (asked 18 Oct): the tetra-calendar skill the app was built with (assets/tetra-calendar.zip, from
 * skills/tetra-calendar), to send to the Claude app, mail or Drive, or to save in Files.
 */
@Composable internal fun ShareWithClaude() {
    val context = LocalContext.current
    fun zip() = context.assets.open("tetra-calendar.zip").use { it.readBytes() }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)!!.use { it.write(zip()) } }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Share with Claude", style = MaterialTheme.typography.titleSmall)
        Text("Lets Claude read and change this calendar by editing its files.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                runCatching {
                    val file = java.io.File(context.cacheDir, "share/tetra-calendar.zip").apply { parentFile?.mkdirs(); writeBytes(zip()) }
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/zip")
                        .putExtra(android.content.Intent.EXTRA_STREAM, uri).putExtra(android.content.Intent.EXTRA_SUBJECT, "Tetra Calendar skill for Claude")
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share the skill"))
                }.onFailure { WriterLog.e("Calendar: sharing the skill failed", it) }
            }) { Text("Share") }
            TextButton(onClick = { save.launch("tetra-calendar.zip") }) { Text("Save to Files") }
        }
    }
}

@Composable internal fun RemindersSwitch() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(CalendarAlarms.enabled(context)) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Reminders on this device", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(on, { on = it; CalendarAlarms.setEnabled(context, it) }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = CalendarPink))
    }
}

/** The Year view: the twelve months small, a day tapped opens it. */
@Composable private fun YearGrid(selected: LocalDate, today: LocalDate, busy: Set<LocalDate>, pick: (LocalDate) -> Unit) {
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Adaptive(250.dp), Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 8.dp, top = HEADER, bottom = 84.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(12) { m ->
            val month = YearMonth.of(selected.year, m + 1)
            Column {
                Text(month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH), style = MaterialTheme.typography.titleSmall,
                    color = if (YearMonth.from(today) == month) CalendarPink else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
                Row { weekOf(today).forEach { Text(it.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                monthCells(month).chunked(7).forEach { week ->
                    Row { week.forEach { d -> if (d.month == month.month) DayDot(d, Modifier.weight(1f), d == selected, d == today, true, d in busy, 28.dp) { pick(d) } else Spacer(Modifier.weight(1f)) } }
                }
            }
        }
    }
}

/** The side panel's small month: ‹ › to look around without moving the main view; a day tapped goes there. */
@Composable private fun MiniMonth(selected: LocalDate, today: LocalDate, busy: Set<LocalDate>, pick: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    LaunchedEffect(selected) { month = YearMonth.from(selected) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 6.dp))
            IconButton(onClick = { month = month.minusMonths(1) }, Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous month") }
            IconButton(onClick = { month = month.plusMonths(1) }, Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next month") }
        }
        Row { weekOf(today).forEach { Text(it.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        monthCells(month).chunked(7).forEach { week ->
            Row { week.forEach { d -> DayDot(d, Modifier.weight(1f), d == selected, d == today, d.month == month.month, d in busy, 32.dp) { pick(d) } } }
        }
    }
}

/** A day's number in a circle: filled when chosen, ringed when today, dimmed outside its month, a dot under when busy. */
@Composable private fun DayDot(d: LocalDate, modifier: Modifier, chosen: Boolean, isToday: Boolean, inMonth: Boolean, busy: Boolean, size: Dp, click: () -> Unit) {
    val primary = CalendarPink
    Column(modifier.clickable(onClick = click).padding(vertical = 1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size).clip(CircleShape)
            .background(if (chosen) primary else Color.Transparent)
            .then(if (isToday && !chosen) Modifier.border(1.5.dp, primary, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
            Text("${d.dayOfMonth}", style = MaterialTheme.typography.bodySmall, fontWeight = if (isToday || busy) FontWeight.Bold else null,
                color = when { chosen -> Color.White; !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f); else -> MaterialTheme.colorScheme.onSurface })
        }
        Box(Modifier.padding(top = 1.dp).size(4.dp).clip(CircleShape).background(if (busy) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent))
    }
}

/**
 * The narrow screen's small calendar, at the bottom above the island (the user, 18 Oct): 1, 2 or 3 weeks from the chosen
 * day's, or its whole month — the handle dragged up for more, down for fewer, the choice kept; swiped sideways for the
 * next ones; a day tapped moves the events above to it.
 */
@Composable private fun BottomStrip(selected: LocalDate, today: LocalDate, days: Map<LocalDate, List<Occurrence>>, pick: (LocalDate) -> Unit) {
    val context = LocalContext.current
    var weeks by remember { mutableIntStateOf(CalendarPrefs.stripWeeks(context)) }
    fun set(n: Int) { weeks = n; CalendarPrefs.setStripWeeks(context, n) }
    val sizes = listOf(1, 2, 3, 6) // 6: the month
    var drag by remember { mutableStateOf(Offset.Zero) }
    val sel = rememberUpdated(selected)
    val month = weeks == 6
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).pointerInput(Unit) {
        detectDragGestures(onDragStart = { drag = Offset.Zero }, onDrag = { _, d -> drag += d }, onDragEnd = {
            val k = sizes.indexOf(weeks)
            if (Math.abs(drag.y) > Math.abs(drag.x)) { if (drag.y < -30) set(sizes[minOf(k + 1, sizes.lastIndex)]) else if (drag.y > 30) set(sizes[maxOf(k - 1, 0)]) }
            else if (Math.abs(drag.x) > 60) {
                val n = if (drag.x < 0) 1L else -1L
                pick(if (weeks == 6) sel.value.plusMonths(n) else sel.value.plusWeeks(n * weeks))
            }
        })
    }) {
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().clickable { set(sizes[(sizes.indexOf(weeks) + 1) % sizes.size]) }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
        }
        if (month) Text(selected.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, bottom = 2.dp))
        Row { weekOf(today).forEach { Text(it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        val rows = if (month) monthCells(YearMonth.from(selected)).chunked(7).filter { w -> w.any { it.month == selected.month } }
            else (0 until weeks).map { i -> weekOf(selected.plusWeeks(i.toLong())) }
        rows.forEach { week ->
            Row { week.forEach { d -> DayDot(d, Modifier.weight(1f), d == selected, d == today, !month || d.month == selected.month, !days[d].isNullOrEmpty(), 34.dp) { pick(d) } } }
        }
    }
}

@Composable private fun <T> rememberUpdated(v: T) = remember { mutableStateOf(v) }.also { it.value = v }

private sealed interface AgendaRow { val day: LocalDate }
private data class AgendaHead(override val day: LocalDate) : AgendaRow
private data class AgendaItem(override val day: LocalDate, val o: Occurrence) : AgendaRow
private data class AgendaNone(override val day: LocalDate) : AgendaRow

/** Outlook's agenda: every day from a year before to a year after, its events or "No events"; the week row follows it. */
@Composable private fun Agenda(anchor: Int, days: Map<LocalDate, List<Occurrence>>, today: LocalDate, selected: LocalDate, select: (LocalDate) -> Unit, open: (Occurrence) -> Unit, top: Dp = 0.dp) {
    val context = LocalContext.current
    val rows = remember(anchor, days) {
        val out = mutableListOf<AgendaRow>()
        var d = LocalDate.of(anchor - 1, 1, 1)
        val end = LocalDate.of(anchor + 2, 1, 1)
        while (d < end) {
            out.add(AgendaHead(d))
            days[d].orEmpty().ifEmpty { null }?.forEach { out.add(AgendaItem(d, it)) } ?: out.add(AgendaNone(d))
            d = d.plusDays(1)
        }
        out
    }
    val state = rememberLazyListState()
    val head = { day: LocalDate -> rows.indexOfFirst { it is AgendaHead && it.day == day }.coerceAtLeast(0) }
    LaunchedEffect(selected, rows) {
        if (rows.getOrNull(state.firstVisibleItemIndex)?.day != selected || state.firstVisibleItemIndex == 0) state.scrollToItem(head(selected))
    }
    LaunchedEffect(state, rows) {
        snapshotFlow { state.firstVisibleItemIndex to state.isScrollInProgress }.collect { (i, moving) -> if (moving) rows.getOrNull(i)?.day?.let(select) }
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = top)) {
        itemsIndexed(rows, key = { i, _ -> i }) { _, r ->
            when (r) {
                is AgendaHead -> Text(
                    (if (r.day == today) "Today · " else if (r.day == today.plusDays(1)) "Tomorrow · " else "") + r.day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
                    style = MaterialTheme.typography.labelLarge, color = if (r.day == today) CalendarPink else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 6.dp))
                is AgendaNone -> Text("No events", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                is AgendaItem -> {
                    val o = r.o
                    val color = Color(o.calendar.color)
                    Row(Modifier.fillMaxWidth().clickable { open(o) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(64.dp)) {
                            if (o.allDay) Text("All day", style = MaterialTheme.typography.bodyMedium)
                            else {
                                Text(if (o.start.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate() == r.day) timeText(context, o.start) else "…", style = MaterialTheme.typography.bodyMedium)
                                Text(lengthText(ChronoUnit.MINUTES.between(o.start, o.end)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Box(Modifier.padding(end = 12.dp).size(10.dp).clip(CircleShape).background(color))
                        Column(Modifier.weight(1f)) {
                            Text(o.event.title.ifEmpty { "(No title)" }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (o.event.place.isNotEmpty()) Text(o.event.place, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** An event as a chip: all-day filled with its colour, timed a dot and its time (Google's month grid). */
@Composable private fun Chip(o: Occurrence, day: LocalDate, compact: Boolean, open: (Occurrence) -> Unit) {
    val context = LocalContext.current
    val color = Color(o.calendar.color)
    val style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    if (o.allDay) Text(o.event.title.ifEmpty { "(No title)" }, style = style, color = onColor(color), maxLines = 1, overflow = TextOverflow.Clip,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(4.dp)).background(color).clickable { open(o) }.padding(horizontal = 4.dp))
    else Row(Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(4.dp)).clickable { open(o) }.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 3.dp).size(6.dp).clip(CircleShape).background(color))
        val starts = o.start.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate() == day
        Text((if (compact || !starts) "" else timeText(context, o.start) + " ") + o.event.title.ifEmpty { "(No title)" }, style = style, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

// shortcut: an event of several days shows as a chip on each day, not one bar across them (Google's); a bar when asked.
@Composable private fun MonthGrid(month: YearMonth, days: Map<LocalDate, List<Occurrence>>, today: LocalDate, selected: LocalDate, compact: Boolean, pick: (LocalDate) -> Unit, open: (Occurrence) -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxSize()) {
        Row { weekOf(today).forEach { Text(it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(), Modifier.weight(1f).padding(vertical = 4.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val rowH = maxHeight / 6
            val chipH = if (compact) 16.dp else 20.dp
            val fit = ((rowH - 28.dp) / chipH).toInt().coerceAtLeast(0)
            Column {
                monthCells(month).chunked(7).forEach { week ->
                    Row(Modifier.height(rowH)) {
                        week.forEach { d ->
                            val list = days[d].orEmpty()
                            Column(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, line).clickable { pick(d) }.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                val isToday = d == today
                                Box(Modifier.size(24.dp).clip(CircleShape).background(if (isToday) CalendarPink else Color.Transparent), contentAlignment = Alignment.Center) {
                                    Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelMedium, fontWeight = if (d == selected) FontWeight.Bold else null,
                                        color = when { isToday -> Color.White; d.month != month.month -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f); else -> MaterialTheme.colorScheme.onSurface })
                                }
                                val n = if (list.size <= fit) list.size else (fit - 1).coerceAtLeast(0)
                                list.take(n).forEach { Chip(it, d, compact, open) }
                                if (list.size > n) Text(if (n == 0 && compact) "•${list.size}" else "${list.size - n} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Day, 3 days and Week: hours down the side, events as blocks side by side where they overlap, a red line at now. */
@Composable private fun TimeGrid(dates: List<LocalDate>, days: Map<LocalDate, List<Occurrence>>, today: LocalDate, open: (Occurrence) -> Unit, slot: (LocalDate, Int) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val hourH = 52.dp
    val gutter = 52.dp
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(gutter))
            dates.forEach { d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(), style = MaterialTheme.typography.labelSmall, color = if (d == today) CalendarPink else MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.size(32.dp).clip(CircleShape).background(if (d == today) CalendarPink else Color.Transparent), contentAlignment = Alignment.Center) {
                        Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = if (d == today) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        val allDay = dates.map { d -> days[d].orEmpty().filter { it.allDay } }
        if (allDay.any { it.isNotEmpty() }) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Spacer(Modifier.width(gutter))
            allDay.forEachIndexed { i, list -> Column(Modifier.weight(1f).padding(horizontal = 1.dp)) { list.take(4).forEach { Chip(it, dates[i], false, open) }; if (list.size > 4) Text("${list.size - 4} more", style = MaterialTheme.typography.labelSmall) } }
        }
        HorizontalDivider()
        val scroll = rememberScrollState()
        val density = androidx.compose.ui.platform.LocalDensity.current
        LaunchedEffect(Unit) { scroll.scrollTo(with(density) { (hourH * 7.5f).roundToPx() }) }
        Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            Column(Modifier.width(gutter)) {
                (0..23).forEach { h -> Box(Modifier.height(hourH).fillMaxWidth().padding(end = 6.dp), contentAlignment = Alignment.TopEnd) {
                    if (h > 0) Text(hourText(context, h), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.offset(y = (-7).dp))
                } }
            }
            dates.forEach { d ->
                BoxWithConstraints(Modifier.weight(1f).height(hourH * 24).pointerInput(d) {
                    // an empty hour tapped: a new event there (Google's)
                    detectTapGestures { p -> slot(d, (p.y / size.height * 24).toInt().coerceIn(0, 23) * 60) }
                }.drawBehind {
                    val step = size.height / 24
                    for (h in 0..23) drawLine(line, Offset(0f, h * step), Offset(size.width, h * step), 1f)
                    drawLine(line, Offset(0f, 0f), Offset(0f, size.height), 1f)
                }) {
                    val w = maxWidth
                    val blocks = dayBlocks(d, days[d].orEmpty().filter { !it.allDay }, zone)
                    blocks.forEach { b ->
                        val color = Color(b.o.calendar.color)
                        val bw = w * b.width
                        val narrow = bw < 72.dp
                        // a thin edge in the page's colour keeps a cascaded card apart from the one under it
                        Column(Modifier.offset(x = w * b.left, y = hourH * (b.from / 60f)).width(bw).height(if (b.short) 20.dp else hourH * ((b.to - b.from) / 60f))
                            .border(1.dp, MaterialTheme.colorScheme.background, RoundedCornerShape(5.dp)).padding(1.dp).clip(RoundedCornerShape(4.dp))
                            .background(color).clickable { open(b.o) }.padding(horizontal = 4.dp, vertical = if (b.short) 0.dp else 2.dp)) {
                            Text(b.o.event.title.ifEmpty { "(No title)" } + if (b.short && !narrow) ", " + timeText(context, b.o.start) else "",
                                style = if (b.short) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, color = onColor(color),
                                maxLines = if (narrow || b.short) 1 else 2, softWrap = !narrow && !b.short, overflow = TextOverflow.Ellipsis)
                            if (!b.short && !narrow && b.to - b.from >= 45) Text(timeText(context, b.o.start) + " – " + timeText(context, b.o.end), style = MaterialTheme.typography.labelSmall, color = onColor(color).copy(alpha = 0.85f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (d == today) {
                        val now = LocalTime.now()
                        Box(Modifier.offset(y = hourH * ((now.hour * 60 + now.minute) / 60f) - 1.dp).fillMaxWidth().height(2.dp).background(Color(0xFFEA4335)))
                    }
                }
            }
        }
    }
}

private fun hourText(context: Context, h: Int) =
    if (android.text.format.DateFormat.is24HourFormat(context)) "%02d:00".format(h) else "${(h + 11) % 12 + 1} ${if (h < 12) "AM" else "PM"}"

private class TimeBlock(val o: Occurrence, val from: Int, val to: Int, val short: Boolean, var left: Float = 0f, var width: Float = 1f)

/**
 * The day's timed events laid out as Google does, so every title stays readable: events starting within a quarter of
 * an hour of each other (less than a title's height) share the width side by side; one starting later over a longer one is drawn on top of it, indented
 * (cascading), its own title clear. A very short one (under half an hour) is a small chip.
 */
private fun dayBlocks(d: LocalDate, occ: List<Occurrence>, zone: ZoneId): List<TimeBlock> {
    val dayStart = d.atStartOfDay(zone)
    val blocks = occ.map { o ->
        val from = ChronoUnit.MINUTES.between(dayStart, o.start).coerceIn(0, 1440).toInt()
        val to = ChronoUnit.MINUTES.between(dayStart, o.end).coerceIn(0, 1440).toInt()
        TimeBlock(o, from, minOf(maxOf(to, from + 22), 1440), short = to - from < 30)
    }.sortedWith(compareBy({ it.from }, { -it.to }))
    val groups = mutableListOf<MutableList<TimeBlock>>()
    for (b in blocks) {
        val g = groups.lastOrNull()
        // a short one never takes width from a long one: it sits on top, indented, as a chip
        if (g != null && b.short == g[0].short && b.from - g.last().from < 15 && b.from < g.maxOf { it.to }) g.add(b) else groups.add(mutableListOf(b))
    }
    groups.forEachIndexed { i, g ->
        val depth = groups.take(i).count { e -> e.maxOf { it.to } > g[0].from }.coerceAtMost(3)
        val left = depth * 0.14f
        g.forEachIndexed { k, b -> b.left = left + (1 - left) * k / g.size; b.width = (1 - left) / g.size }
    }
    return blocks
}

/** An event's card: when, how often, which calendar, its reminders, place and notes. */
@Composable private fun EventCard(o: Occurrence, close: () -> Unit, edit: () -> Unit, delete: () -> Unit, keep: () -> Unit) {
    val context = LocalContext.current
    val e = o.event
    val zone = ZoneId.systemDefault()
    val whenText = if (o.allDay) {
        val last = o.end.toLocalDate().minusDays(1)
        val f = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
        if (last <= o.start.toLocalDate()) o.start.format(f) else o.start.format(f) + " – " + last.format(f)
    } else {
        val s = o.start.withZoneSameInstant(zone); val en = o.end.withZoneSameInstant(zone)
        s.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)) + " · " + timeText(context, s) + " – " +
            (if (en.toLocalDate() != s.toLocalDate()) en.format(DateTimeFormatter.ofPattern("d MMM ", Locale.ENGLISH)) else "") + timeText(context, en)
    }
    Dialog(onDismissRequest = close) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 8.dp, end = 12.dp).size(14.dp).clip(RoundedCornerShape(4.dp)).background(Color(o.calendar.color)))
                    Text(e.title.ifEmpty { "(No title)" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = edit) { Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit") }
                    IconButton(onClick = delete) { Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete") }
                    IconButton(onClick = close) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close") }
                }
                Text(whenText, style = MaterialTheme.typography.bodyLarge)
                (o.file.master ?: e).let { m -> repeatText(m.rrule, m.start) }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (e.place.isNotEmpty()) Text(e.place, style = MaterialTheme.typography.bodyMedium)
                // a birthday with its year: the age that day
                o.file.comps.flatMap { c -> c.comps("VEVENT") }.firstOrNull { it.prop("RECURRENCE-ID") == null }?.prop("X-TETRA-BIRTH-YEAR")?.value?.trim()?.toIntOrNull()
                    ?.let { y -> if (o.start.year > y) Text("Turns ${o.start.year - y}", style = MaterialTheme.typography.bodyMedium) }
                Text(o.calendar.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                e.alarms.forEach { Text("🔔 " + alarmText(it, e.allDay, o.start), style = MaterialTheme.typography.bodyMedium) }
                if (e.notes.isNotEmpty()) Text(e.notes, style = MaterialTheme.typography.bodyMedium)
                if (o.file.versions > 1) {
                    Text("${o.file.versions} versions of this event came from a sync (changed on two devices); the newest is shown.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = keep) { Text("Keep this one") }
                }
                Text(o.file.file.relativeTo(o.calendar.dir.parentFile ?: File("/")).path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


/**
 * What stands between a reminder and the person: notifications off, and on a Xiaomi its own Autostart and battery
 * saver (a restart reaches Tetra only with Autostart). Asked once; a line stays while notifications are off.
 */
@Composable private fun ReminderNotes() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("calendar", Context.MODE_PRIVATE) }
    fun allowed() = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    var notifications by remember { mutableStateOf(allowed()) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { notifications = allowed() }
    LaunchedEffect(Unit) {
        if (!notifications && android.os.Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("askedNotifications", false)) {
            prefs.edit().putBoolean("askedNotifications", true).apply()
            ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun open(i: android.content.Intent) = runCatching { context.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    val appSettings = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
    // the rest (exact alarms, Hyper Island, Xiaomi's Autostart and battery) is in Settings › Calendar (the user, 18 Oct)
    if (!notifications) Note("Notifications are off for Tetra, so reminders will not show.", "Turn on" to { open(appSettings) })
}

/** What a calendar's import says when done: "Imported: 12 new, 3 updated". Each calendar into the one of its name. */
internal fun importCalendars(context: Context, uri: android.net.Uri): String {
    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Imported"
    val incoming = (if (bytes.size > 1 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) CalendarTransfer.readZip(bytes)
        else listOf(CalendarTransfer.read(bytes.toString(Charsets.UTF_8), name.removeSuffix(".ics")))).filter { it.events.isNotEmpty() }
    if (incoming.isEmpty()) return "No events in that file."
    val root = calendarRoot().apply { mkdirs() }
    var added = 0; var updated = 0
    incoming.forEach { inc ->
        val all = CalendarFiles.calendars(root)
        val to = all.firstOrNull { it.name.equals(inc.name, true) } ?: CalendarFiles.newCalendar(root, inc.name, inc.color ?: CalendarFiles.PALETTE[all.size % CalendarFiles.PALETTE.size])
        CalendarTransfer.write(to, inc).let { (a, u) -> added += a; updated += u }
    }
    runCatching { CalendarAlarms.plan(context) }
    return "Imported: $added new" + if (updated > 0) ", $updated updated" else ""
}

/**
 * Settings › Calendar (the user, 18 Oct: "We need more permisions in the settings. A calendar section with the import
 * expot"): each permission reminders need, its state and a button to it; the reminders switch; Import and Export all.
 */
@Composable internal fun CalendarSettings() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) { // back from Android's settings: read the states again
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) version++ }
        lifecycle.lifecycle.addObserver(o); onDispose { lifecycle.lifecycle.removeObserver(o) }
    }
    var said by remember { mutableStateOf<String?>(null) }
    fun open(i: android.content.Intent) = runCatching { context.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    val pkg = android.net.Uri.parse("package:${context.packageName}")
    val appDetails = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
    val notificationSettings = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { version++ }
    @Suppress("UNUSED_EXPRESSION") version // read again on each change
    val notifications = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exact = android.os.Build.VERSION.SDK_INT < 31 || context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
    val battery = context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    val xiaomi = android.os.Build.MANUFACTURER.equals("Xiaomi", true)
    val island = remember(version) { HyperIsland.isSupported(context) }
    @Composable fun row(title: String, state: String, ok: Boolean, action: String?, act: () -> Unit) =
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(state, style = MaterialTheme.typography.bodySmall, color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            }
            if (action != null) TextButton(onClick = act) { Text(action) }
        }
    Text("Your calendars are the folders in Tetra/Calendar: they sync with the computer, and Claude can manage them there.", style = MaterialTheme.typography.bodySmall)
    row("Notifications", if (notifications) "Allowed: reminders show" else "Off: reminders will not show", notifications, if (notifications) "Settings" else "Allow") {
        if (!notifications && android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) else open(notificationSettings)
    }
    row("Alarms on time", if (exact) "Allowed: reminders ring at their minute" else "Not allowed: reminders may come a few minutes late", exact, if (exact) null else "Allow") {
        if (android.os.Build.VERSION.SDK_INT >= 31) open(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
    }
    row("Battery", if (battery) "No restrictions: reminders ring on time" else "Restricted: a reminder can be held back", battery, "Settings") {
        open(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) || open(appDetails)
    }
    if (xiaomi) {
        row("Hyper Island", if (island) "On: a reminder shows in the island, with ✕ for done" else "Off: turn on Tetra's focus notifications", island, "Settings") { open(notificationSettings) }
        row("Autostart", "Needed for reminders after a restart (Xiaomi; Android does not tell apps whether it is on)", true, "Settings") {
            open(android.content.Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")) || open(appDetails)
        }
    }
    RemindersSwitch()
    ShareWithClaude()
    TextButton(onClick = { CalendarAlarms.test(context); said = "A test reminder comes in 5 seconds: go to the home screen to see it in the island (HyperOS shows none for the app in front)." }) { Text("Send a test reminder") }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { said = withContext(Dispatchers.IO) { runCatching { importCalendars(context, uri) }.getOrElse { "Not imported: ${it.message}" } } }
    }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch { said = withContext(Dispatchers.IO) { runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(CalendarTransfer.exportAll(CalendarFiles.calendars(calendarRoot()))) }; "Exported"
        }.getOrElse { "Not exported: ${it.message}" } } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Button(onClick = { pick.launch(arrayOf("text/calendar", "application/zip", "application/octet-stream", "*/*")) }) { Text("Import") }
        androidx.compose.material3.Button(onClick = { save.launch("Tetra calendars.zip") }) { Text("Export all") }
    }
    Text("Import: an .ics file or Google Takeout's zip; each calendar goes into the one of its name, an event already there is updated. Export: every calendar as an .ics, in a zip.", style = MaterialTheme.typography.bodySmall)
    said?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

@Composable private fun Note(text: String, vararg actions: Pair<String, () -> Unit>) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 2.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions.forEach { (label, act) -> TextButton(onClick = act) { Text(label) } } }
        }
    }
}

/** What another app asked the Calendar to import (an .ics opened with Tetra); the Calendar takes it when it shows. */
internal object CalendarInbox { val pending = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null) }

/** Import: one calendar into a new one or an existing one; Takeout's several each into its own (by name). */
@Composable private fun ImportDialog(list: List<CalendarTransfer.Incoming>, cals: List<CalendarInfo>, dismiss: () -> Unit, go: (List<Pair<CalendarTransfer.Incoming, CalendarInfo?>>) -> Unit) {
    val single = list.singleOrNull()
    var target by remember { mutableStateOf(single?.let { s -> cals.firstOrNull { it.name.equals(s.name, true) } }) }
    androidx.compose.material3.AlertDialog(onDismissRequest = dismiss, title = { Text("Import") }, confirmButton = {
        TextButton(onClick = { go(if (single != null) listOf(single to target) else list.map { it to null }) }) { Text("Import") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (single != null) {
                Text("${single.events.size} events from “${single.name}”, into:")
                var menu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text((target?.name ?: "A new calendar “${single.name}”") + "  ▾", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("A new calendar “${single.name}”") }, onClick = { target = null; menu = false })
                        cals.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(c.color))) }, onClick = { target = c; menu = false }) }
                    }
                }
                Text("An event already there (the same one, imported before) is updated, not doubled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("${list.size} calendars, each into the calendar of its name (made if there is none):")
                list.forEach { Text("• ${it.name} — ${it.events.size} events", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    })
}
