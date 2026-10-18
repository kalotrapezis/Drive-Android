package com.kalotrapezis.drive

import android.content.Context
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import kotlinx.coroutines.withContext

/*
 * The Calendar's screens (Plan.md § Calendar › Screens). A big screen as Google Calendar on the web: a side panel
 * (a small month, the calendars with their coloured boxes) and a month, week or day grid. A phone as Outlook: the week
 * row (pulled down: the month) over the agenda of the days. Everything is read from Tetra/Calendar's files.
 */

internal enum class CalView(val label: String) { Agenda("Agenda"), Day("Day"), ThreeDays("3 days"), Week("Week"), Month("Month") }

internal fun calendarRoot() = CalendarFiles.root(TetraFolder.root(Environment.getExternalStorageDirectory()))

/** Which calendars this device hides (the checkboxes) and the last view: this screen's choices, not the files'. */
private object CalendarPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("calendar", Context.MODE_PRIVATE)
    fun hidden(c: Context): Set<String> = prefs(c).getStringSet("hidden", emptySet())!!.toSet()
    fun setHidden(c: Context, s: Set<String>) = prefs(c).edit().putStringSet("hidden", s).apply()
    fun view(c: Context, wide: Boolean): CalView = runCatching { CalView.valueOf(prefs(c).getString(if (wide) "viewWide" else "view", null)!!) }
        .getOrDefault(if (wide) CalView.Month else CalView.Agenda)
    fun setView(c: Context, wide: Boolean, v: CalView) = prefs(c).edit().putString(if (wide) "viewWide" else "view", v.name).apply()
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

/** A reminder in words: "30 minutes before", "3 days before at 09:00 · every day, 3 times". */
internal fun alarmText(a: CalAlarm, allDay: Boolean): String {
    val base = when {
        a.at != null -> "At " + a.at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH))
        a.fromEnd -> (if (a.days * 86400 + a.seconds == 0L) "At the end" else spanText(a.days * 86400 + a.seconds) + if (a.days * 86400 + a.seconds < 0) " before the end" else " after the end")
        else -> {
            val t = a.days * 86400 + a.seconds
            if (allDay && t <= 0) {
                val before = (-t + 86399) / 86400
                val clock = LocalTime.ofSecondOfDay(((t % 86400) + 86400) % 86400).format(DateTimeFormatter.ofPattern("HH:mm"))
                when (before) { 0L -> "On the day at $clock"; 1L -> "The day before at $clock"; else -> "$before days before at $clock" }
            } else if (t == 0L) "At the time" else spanText(t) + if (t < 0) " before" else " after"
        }
    }
    return if (a.repeat > 0) "$base · every ${spanText(a.every).removePrefix("1 ")}, ${a.repeat + 1} times" else base
}

/** The Calendar's own pink (its icon's, Home's group): today and the chosen day, as Google's blue. */
internal val CalendarPink = Color(0xFFF41879)

private fun onColor(c: Color) = if (c.luminance() > 0.55f) Color.Black else Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarScreen(back: () -> Unit, hasAccess: Boolean, grant: () -> Unit) {
    val context = LocalContext.current
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    val zone = remember { ZoneId.systemDefault() }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); today = LocalDate.now() } }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var view by remember(wide) { mutableStateOf(CalendarPrefs.view(context, wide)) }
    var version by remember { mutableIntStateOf(0) }
    var hidden by remember { mutableStateOf(CalendarPrefs.hidden(context)) }
    var panel by remember { mutableStateOf(true) }
    var drawer by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<Occurrence?>(null) }
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
        val watcher = DriveWatcher(root) { version++ }
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
    val views = if (wide) listOf(CalView.Day, CalView.Week, CalView.Month, CalView.Agenda) else listOf(CalView.Agenda, CalView.Day, CalView.ThreeDays, CalView.Month)
    LaunchedEffect(wide) { if (view !in views) setView(views.first()) }
    BackHandler { when { shown != null -> shown = null; drawer -> drawer = false; else -> back() } }
    val open: (Occurrence) -> Unit = { shown = it }

    val body: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            when (view) {
                CalView.Month -> MonthGrid(YearMonth.from(selected), days, today, selected, compact = !wide, pick = { selected = it; if (!wide) setView(CalView.Agenda) }, open = open)
                CalView.Week -> TimeGrid(weekOf(selected), days, today, open)
                CalView.ThreeDays -> TimeGrid((0L..2).map { selected.plusDays(it) }, days, today, open)
                CalView.Day -> TimeGrid(listOf(selected), days, today, open)
                CalView.Agenda -> Agenda(anchor, days, today, selected, { selected = it }, open)
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (wide) Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
                IconButton(onClick = { panel = !panel }) { Icon(painterResource(R.drawable.ic_menu), contentDescription = if (panel) "Hide the side panel" else "Show the side panel") }
                Text("Calendar", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 16.dp))
                OutlinedButton(onClick = { selected = today }) { Text("Today") }
                IconButton(onClick = { selected = step(selected, view, -1) }) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous") }
                IconButton(onClick = { selected = step(selected, view, 1) }) { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next") }
                Text(title(selected, view), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                ViewMenu(view, views, ::setView)
            }
            Row(Modifier.weight(1f)) {
                if (panel) Column(Modifier.width(264.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniMonth(selected, today, days.keys) { selected = it }
                    CalendarList(cals, hidden, ::toggle)
                }
                body(Modifier.weight(1f).fillMaxHeight())
            }
        } else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
                IconButton(onClick = { drawer = true }) { Icon(painterResource(R.drawable.ic_menu), contentDescription = "Calendars") }
                Text(selected.format(DateTimeFormatter.ofPattern(if (selected.year == today.year) "MMMM" else "MMMM yyyy", Locale.ENGLISH)),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1)
                IconButton(onClick = { selected = today }) { Icon(painterResource(R.drawable.ic_calendar_today), contentDescription = "Today") }
                ViewMenu(view, views, ::setView)
            }
            if (view == CalView.Agenda) WeekStrip(selected, today, days) { selected = it }
            else if (view != CalView.Month) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = step(selected, view, -1) }) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous") }
                Text(title(selected, view), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { selected = step(selected, view, 1) }) { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next") }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = selected.minusMonths(1) }) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous month") }
                Text(selected.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { selected = selected.plusMonths(1) }) { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next month") }
            }
            body(Modifier.weight(1f).fillMaxWidth())
        }
    }
    if (drawer) ModalBottomSheet(onDismissRequest = { drawer = false }) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) { CalendarList(cals, hidden, ::toggle) }
    }
    shown?.let { EventCard(it) { shown = null } }
}

private fun step(d: LocalDate, v: CalView, n: Long): LocalDate = when (v) {
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
    else -> d.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
}

@Composable private fun ViewMenu(view: CalView, views: List<CalView>, set: (CalView) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(view.label + "  ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            views.forEach { v -> DropdownMenuItem(text = { Text(v.label) }, onClick = { open = false; set(v) }) }
        }
    }
}

@Composable private fun CalendarList(cals: List<CalendarInfo>, hidden: Set<String>, toggle: (CalendarInfo) -> Unit) {
    Column {
        Text("My calendars", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
        if (cals.isEmpty()) Text("No calendars yet. Each folder in Tetra/Calendar is one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        cals.forEach { c ->
            val color = Color(c.color)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { toggle(c) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = c.dir.name !in hidden, onCheckedChange = { toggle(c) },
                    colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color, checkmarkColor = onColor(color)))
                Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** The phone's top: the chosen day's week, pulled down to its whole month; swiped sideways for the next week or month. */
@Composable private fun WeekStrip(selected: LocalDate, today: LocalDate, days: Map<LocalDate, List<Occurrence>>, pick: (LocalDate) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val sel = rememberUpdated(selected)
    Column(Modifier.fillMaxWidth().pointerInput(Unit) {
        detectDragGestures(onDragStart = { drag = Offset.Zero }, onDrag = { _, d -> drag += d }, onDragEnd = {
            if (Math.abs(drag.y) > Math.abs(drag.x)) { if (drag.y > 40) expanded = true else if (drag.y < -40) expanded = false }
            else if (Math.abs(drag.x) > 60) {
                val n = if (drag.x < 0) 1L else -1L
                pick(if (expanded) sel.value.plusMonths(n) else sel.value.plusWeeks(n))
            }
        })
    }) {
        Row { weekOf(today).forEach { Text(it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        val weeks = if (expanded) monthCells(YearMonth.from(selected)).chunked(7).filter { w -> w.any { it.month == selected.month } } else listOf(weekOf(selected))
        weeks.forEach { week ->
            Row { week.forEach { d -> DayDot(d, Modifier.weight(1f), d == selected, d == today, !expanded || d.month == selected.month, !days[d].isNullOrEmpty(), 36.dp) { pick(d) } } }
        }
        Box(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
        }
        HorizontalDivider()
    }
}

@Composable private fun <T> rememberUpdated(v: T) = remember { mutableStateOf(v) }.also { it.value = v }

private sealed interface AgendaRow { val day: LocalDate }
private data class AgendaHead(override val day: LocalDate) : AgendaRow
private data class AgendaItem(override val day: LocalDate, val o: Occurrence) : AgendaRow
private data class AgendaNone(override val day: LocalDate) : AgendaRow

/** Outlook's agenda: every day from a year before to a year after, its events or "No events"; the week row follows it. */
@Composable private fun Agenda(anchor: Int, days: Map<LocalDate, List<Occurrence>>, today: LocalDate, selected: LocalDate, select: (LocalDate) -> Unit, open: (Occurrence) -> Unit) {
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
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
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
@Composable private fun TimeGrid(dates: List<LocalDate>, days: Map<LocalDate, List<Occurrence>>, today: LocalDate, open: (Occurrence) -> Unit) {
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
                BoxWithConstraints(Modifier.weight(1f).height(hourH * 24).drawBehind {
                    val step = size.height / 24
                    for (h in 0..23) drawLine(line, Offset(0f, h * step), Offset(size.width, h * step), 1f)
                    drawLine(line, Offset(0f, 0f), Offset(0f, size.height), 1f)
                }) {
                    val w = maxWidth
                    val blocks = dayBlocks(d, days[d].orEmpty().filter { !it.allDay }, zone)
                    blocks.forEach { b ->
                        val color = Color(b.o.calendar.color)
                        Column(Modifier.offset(x = w * b.col / b.cols, y = hourH * (b.from / 60f)).width(w / b.cols).height(hourH * ((b.to - b.from) / 60f))
                            .padding(1.dp).clip(RoundedCornerShape(4.dp)).background(color).clickable { open(b.o) }.padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Text(b.o.event.title.ifEmpty { "(No title)" }, style = MaterialTheme.typography.labelMedium, color = onColor(color), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (b.to - b.from >= 45) Text(timeText(context, b.o.start) + " – " + timeText(context, b.o.end), style = MaterialTheme.typography.labelSmall, color = onColor(color).copy(alpha = 0.85f), maxLines = 1)
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

private class TimeBlock(val o: Occurrence, val from: Int, val to: Int, var col: Int = 0, var cols: Int = 1)

/** The day's timed events in minutes of the day, overlapping ones given columns side by side (Google's way). */
private fun dayBlocks(d: LocalDate, occ: List<Occurrence>, zone: ZoneId): List<TimeBlock> {
    val dayStart = d.atStartOfDay(zone)
    val blocks = occ.map { o ->
        val from = ChronoUnit.MINUTES.between(dayStart, o.start).coerceIn(0, 1440).toInt()
        val to = ChronoUnit.MINUTES.between(dayStart, o.end).coerceIn(0, 1440).toInt()
        TimeBlock(o, from, minOf(maxOf(to, from + 20), 1440)) // a short one still tall enough to tap
    }.sortedWith(compareBy({ it.from }, { -it.to }))
    var cluster = mutableListOf<TimeBlock>()
    var clusterEnd = -1
    fun close() { val n = (cluster.maxOfOrNull { it.col } ?: 0) + 1; cluster.forEach { it.cols = n }; cluster = mutableListOf() }
    for (b in blocks) {
        if (b.from >= clusterEnd) { close(); clusterEnd = -1 }
        val ends = IntArray(cluster.size + 1) { -1 }
        cluster.forEach { ends[it.col] = maxOf(ends[it.col], it.to) }
        b.col = ends.indices.first { ends[it] <= b.from }
        cluster.add(b)
        clusterEnd = maxOf(clusterEnd, b.to)
    }
    close()
    return blocks
}

/** An event's card: when, how often, which calendar, its reminders, place and notes. */
@Composable private fun EventCard(o: Occurrence, close: () -> Unit) {
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
                    IconButton(onClick = close) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close") }
                }
                Text(whenText, style = MaterialTheme.typography.bodyLarge)
                (o.file.master ?: e).let { m -> repeatText(m.rrule, m.start) }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (e.place.isNotEmpty()) Text(e.place, style = MaterialTheme.typography.bodyMedium)
                Text(o.calendar.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                e.alarms.forEach { Text("🔔 " + alarmText(it, e.allDay), style = MaterialTheme.typography.bodyMedium) }
                if (e.notes.isNotEmpty()) Text(e.notes, style = MaterialTheme.typography.bodyMedium)
                if (o.file.versions > 1) Text("${o.file.versions} versions of this event came from a sync; the newest is shown.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Text(o.file.file.relativeTo(o.calendar.dir.parentFile ?: File("/")).path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Home's line for the Calendar: what is on now or next within a month ("09:30 Dentist", "Tomorrow · Trip"). */
internal fun nextEventText(context: Context): String? {
    val root = calendarRoot()
    if (!root.isDirectory) return null
    val hidden = CalendarPrefs.hidden(context)
    val now = java.time.Instant.now()
    val o = runCatching { CalendarFiles.occurrences(CalendarFiles.calendars(root).filter { it.dir.name !in hidden }, now, now.plus(java.time.Duration.ofDays(31))) }
        .getOrNull()?.firstOrNull() ?: return null
    val today = LocalDate.now()
    val day = if (o.allDay) o.start.toLocalDate() else o.start.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
    val title = o.event.title.ifEmpty { "(No title)" }
    return when {
        day <= today -> if (o.allDay) "Today · $title" else timeText(context, o.start) + " " + title
        day == today.plusDays(1) -> "Tomorrow · $title"
        else -> day.format(DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)) + " · " + title
    }
}
