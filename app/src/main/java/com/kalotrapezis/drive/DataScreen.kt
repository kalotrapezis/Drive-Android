package com.kalotrapezis.drive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateStyle
import java.util.Locale

/** Data's lime (the user's icon, 25 Oct); a darker lime on the light theme, where the bright one cannot be read on white. */
@Composable internal fun dataColor() = lookTint(Color(0xFFA1F81A), Color(0xFF4E8A0A))
internal val DATA_COLOR = Color(0xFFA1F81A)
/** Text on Data's lime: black on the bright one, white on the light theme's dark one. */
@Composable internal fun onData() = lookTint(Color.Black, Color.White)
private val WIFI_COLOR = Color(0xFF4F86E8)

/** Data, its own window as the Phone is (Plan.md § Data): Usage · Devices · Signal, and Setup the first time. */
class DataActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WriterLog.init(this)
        TetraLook.load(this)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = TetraLook.light
        val tab = intent.getStringExtra(TAB)
        setContent { TetraTheme { DataApp(tab, back = { finish() }) } }
    }
    companion object { const val TAB = "com.kalotrapezis.drive.data.TAB" } // Home's slots: usage, devices, signal
}

internal fun openData(c: Context, tab: String? = null) = c.startActivity(Intent(c, DataActivity::class.java).putExtra(DataActivity.TAB, tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

private enum class DataTab(val icon: Int) { Usage(R.drawable.ic_data_usage), Devices(R.drawable.ic_devices), Signal(R.drawable.ic_wifi) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataApp(start: String?, back: () -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) { // back from Android's settings (Usage access): read again
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) version++ }
        lifecycle.lifecycle.addObserver(o); onDispose { lifecycle.lifecycle.removeObserver(o) }
    }
    val plan by produceState(DataFiles.plan(), version) { value = withContext(Dispatchers.IO) { DataFiles.plan() } }
    var setup by remember { mutableStateOf(start == "setup" || (DataFiles.plan() == null && !PhoneRelay.noCalls(context))) }
    val all by produceState(emptyMap<String, List<DataMonth>>(), version) {
        value = withContext(Dispatchers.IO) {
            runCatching { DataStats.record(context); DataStats.checkRoaming(context) }.onFailure { WriterLog.w("Data: ${it.message}") }
            DataStats.schedule(context); DataStats.watchUsage(context); DataSignal.watch(context)
            DataFiles.all()
        }
    }
    var tab by remember { mutableStateOf(DataTab.entries.firstOrNull { it.name.equals(start, true) } ?: DataTab.Usage) }
    var drawer by remember { mutableStateOf(false) }
    if (setup) { DataSetup(plan, all[DataFiles.device()].orEmpty(), done = { setup = false; version++ }, back = { if (plan == null) back() else setup = false }); return }
    val top = PaddingValues(top = 76.dp, bottom = 96.dp)
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxHeight().widthIn(max = 980.dp).fillMaxWidth()) {
            when (tab) {
                DataTab.Usage -> UsageTab(plan, all, top, openSetup = { setup = true }) { version++ }
                DataTab.Devices -> DevicesTab(all, top)
                DataTab.Signal -> DataSignalTab(top)
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                    IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
                }
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                    Text("Data · ${tab.name}", Modifier.padding(horizontal = 18.dp, vertical = 13.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                IslandBottomBar(expand = { drawer = true }, visible = !drawer) {
                    DataTab.entries.forEach { t -> IslandNavigationItem(t.name, tab == t, t.icon) { tab = t } }
                }
            }
        }
    }
    if (drawer) ModalBottomSheet(onDismissRequest = { drawer = false }, containerColor = SheetColor, contentColor = islandContentColor()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Data", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { drawer = false; setup = true }, colors = sheetButton()) { Text("Setup — your plan, roaming, warnings") }
            var live by remember { mutableStateOf(DataSpeed.enabled(context)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Live speed in the status bar", Modifier.weight(1f)); Switch(live, { live = it; DataSpeed.setEnabled(context, it) })
            }
            Stopwatch()
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_DATA_USAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, colors = sheetButton()) { Text("Android's data settings (limit, Data Saver)") }
        }
    }
}

/** The sheet is dark on both themes: its buttons in the bright lime. */
@Composable private fun sheetButton() = ButtonDefaults.textButtonColors(contentColor = DATA_COLOR)

// ---- Usage ----

private enum class Span { Day, Week, Cycle, Month, Year }

/** A bar of the chart: its label, the days it covers ([hour] for a day's hours), and its bytes. */
private data class Bar(val label: String, val from: LocalDate, val until: LocalDate, val mobile: Long, val wifi: Long, val hour: String? = null)

private fun bars(span: Span, shift: Int, plan: DataPlan?, months: List<DataMonth>, today: LocalDate): List<Bar> {
    val days = DataFiles.days(months).associateBy { it.day }
    fun day(d: LocalDate, label: String) = days[d].let { Bar(label, d, d.plusDays(1), it?.mobile ?: 0, it?.wifi ?: 0) }
    return when (span) {
        Span.Day -> {
            val d = today.plusDays(shift.toLong())
            val hrs = months.flatMap { it.hours }.filter { it.hour.startsWith(d.toString()) }
            // Android's buckets are 2 hours from midnight UTC, so here they may start on odd hours: each goes to its pair of hours
            fun slot(h: DataHour) = (h.hour.substringAfter('T').toIntOrNull() ?: 0) / 2 * 2
            (0 until 24 step 2).map { h ->
                Bar("$h", d, d.plusDays(1), hrs.filter { slot(it) == h && it.net == "mobile" }.sumOf { it.bytes }, hrs.filter { slot(it) == h && it.net == "wifi" }.sumOf { it.bytes }, "$h")
            }
        }
        Span.Week -> (6 downTo 0).map { today.plusWeeks(shift.toLong()).minusDays(it.toLong()) }.map { day(it, it.dayOfWeek.getDisplayName(DateStyle.SHORT, Locale.ENGLISH).take(2)) }
        Span.Cycle -> {
            val c = DataRules.cycle(plan ?: DataPlan(), today).let { c0 -> var c = c0; repeat(-shift) { c = DataRules.cycle(plan ?: DataPlan(), c.start.minusDays(1)) }; c }
            generateSequence(c.start) { it.plusDays(1) }.takeWhile { it.isBefore(c.end) }.map { day(it, "${it.dayOfMonth}") }.toList()
        }
        Span.Month -> {
            val m = today.withDayOfMonth(1).plusMonths(shift.toLong())
            (0 until m.lengthOfMonth()).map { m.plusDays(it.toLong()) }.map { day(it, "${it.dayOfMonth}") }
        }
        Span.Year -> {
            val y = today.withDayOfYear(1).plusYears(shift.toLong())
            (0 until 12).map { y.plusMonths(it.toLong()) }.map { m ->
                val inM = days.values.filter { it.day.year == m.year && it.day.month == m.month }
                Bar(m.month.getDisplayName(DateStyle.NARROW, Locale.ENGLISH), m, m.plusMonths(1), inM.sumOf { it.mobile }, inM.sumOf { it.wifi })
            }
        }
    }
}

@Composable
private fun UsageTab(plan: DataPlan?, all: Map<String, List<DataMonth>>, pad: PaddingValues, openSetup: () -> Unit, changed: () -> Unit) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val mine = all[DataFiles.device()].orEmpty()
    // the plan's numbers are the phone's: on the tablet, the phone's files (the one with a hotspot or mobile data)
    val phone = if (PhoneRelay.noCalls(context)) all.values.maxByOrNull { m -> DataFiles.days(m).sumOf { it.mobile } }.orEmpty() else mine
    val status = remember(plan, phone) { plan?.let { DataRules.status(it, DataFiles.days(phone), today) } }
    var span by remember { mutableStateOf(Span.Cycle) }
    var shift by remember(span) { mutableIntStateOf(0) }
    var picked by remember(span, shift) { mutableStateOf<Bar?>(null) }
    var app by remember { mutableStateOf<String?>(null) }
    val shown = remember(span, shift, mine, plan) { bars(span, shift, plan, mine, today) }
    val from = picked?.from ?: shown.first().from; val until = picked?.until ?: shown.last().until
    val apps = remember(mine, from, until) {
        mine.flatMap { it.apps }.filter { !it.day.isBefore(from) && it.day.isBefore(until) }.groupBy { it.app }
            .map { (k, l) -> Triple(k, l.first().name, l.partition { it.net == "mobile" }.let { (m, w) -> m.sumOf { it.total } to w.sumOf { it.total } }) }
            .filter { it.third.first + it.third.second > 0 }.sortedByDescending { it.third.first * 4 + it.third.second } // mobile first: it is what costs
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val head: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!DataStats.hasAccess(context)) Notice("Turn on Usage access so Data can count each app's data. It stays on your devices.", "Open") {
                    runCatching { context.startActivity(DataStats.accessIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                if (plan == null) Notice("Tell Data your plan: its size and when it renews, for warnings and a daily budget.", "Setup", openSetup)
                else if (status != null) PlanHead(plan, status)
                SpanChips(span) { span = it }
                Chart(shown, span, plan, status, picked, shift, { shift += it }) { picked = if (picked == it) null else it }
            }
        }
        val list: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            item { Text(picked?.let { "Apps · ${it.label}" + (if (span == Span.Day) ":00" else "") + " (tap the bar again for all)" } ?: "Apps", Modifier.padding(vertical = 8.dp),
                color = dataColor(), fontWeight = FontWeight.Bold) }
            if (apps.isEmpty()) item { Text(if (DataStats.hasAccess(context)) "Nothing used then" else "Needs Usage access", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            apps.forEach { (key, name, mw) -> item(key = key) { AppRow(name, mw.first, mw.second, apps.first().third.let { it.first + it.second }) { app = key } } }
        }
        if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1.3f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(pad)) { head() }
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = pad) { list() }
        } else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = pad) {
            item { head() }
            list()
        }
    }
    app?.let { key -> AppSheet(key, mine) { app = null } }
}

@Composable private fun Notice(text: String, action: String, act: () -> Unit) =
    Surface(color = dataColor().copy(alpha = 0.16f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = act) { Text(action, color = dataColor()) }
        }
    }

/** The ring (used of the plan) and the numbers beside it. */
@Composable private fun PlanHead(plan: DataPlan, s: DataStatus) {
    val c = dataColor(); val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val share = if (plan.limited && s.size > 0) (s.used.toFloat() / s.size).coerceIn(0f, 1f) else 0f
    val over = plan.limited && s.used > s.size
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 12.dp.toPx(); val inset = w / 2
                drawArc(track, 0f, 360f, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w))
                drawArc(if (over) Color(0xFFE53935) else c, -90f, 360f * share, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(if (plan.limited) "${(s.used * 100 / s.size.coerceAtLeast(1))} %" else "∞", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val of = when (plan.kind) { DataPlan.FAIR_USE -> " of ${DataRules.bytes(s.size)} fair use"; DataPlan.LIMITED -> " of ${DataRules.bytes(s.size)}"; else -> " this cycle" }
            Text(DataRules.bytes(s.used) + of, style = MaterialTheme.typography.titleMedium)
            Text("${s.daysLeft} days left · renews ${s.cycle.end.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}", style = MaterialTheme.typography.bodyMedium)
            Text("Today ${DataRules.bytes(s.today)}" + if (plan.limited) " · budget ${DataRules.bytes(s.budget)}/day" else " · average ${DataRules.bytes(s.average)}/day", style = MaterialTheme.typography.bodyMedium)
            if (plan.limited) {
                val runsOut = s.projection > s.size
                Text(if (runsOut) "At this pace: ${DataRules.bytes(s.projection)} by the end ⚠" else "At this pace: ${DataRules.bytes(s.projection)} by the end ✓",
                    color = if (runsOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable private fun SpanChips(span: Span, pick: (Span) -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
        Row(Modifier.padding(4.dp)) {
            Span.entries.forEach { s ->
                Surface(onClick = { pick(s) }, shape = CircleShape, color = if (s == span) dataColor() else Color.Transparent,
                    contentColor = if (s == span) onData() else MaterialTheme.colorScheme.onSurface) { Text(s.name, Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
            }
        }
    }
}

/**
 * Bars of mobile (lime) and Wi-Fi (blue) stacked; the axis's top is a round number just over the tallest, so a 3 MB hour
 * and a 40 GB month both fill it (the old app stopped at 2 GB). In a cycle, the running total and the plan's line too.
 */
@Composable private fun Chart(bars: List<Bar>, span: Span, plan: DataPlan?, status: DataStatus?, picked: Bar?, shift: Int, move: (Int) -> Unit, pick: (Bar) -> Unit) {
    val lime = dataColor(); val ink = MaterialTheme.colorScheme.onSurface; val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val step = DataRules.axisStep(bars.maxOf { it.mobile + it.wifi })
    val lines = DataRules.axisLines(bars.maxOf { it.mobile + it.wifi }); val top = step * lines
    val running = if (span == Span.Cycle && shift == 0 && plan?.limited == true && status != null) status else null
    val title = when (span) {
        Span.Day -> bars.first().from.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
        Span.Year -> "${bars.first().from.year}"
        else -> bars.first().from.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) + " – " + bars.last().from.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { move(-1) }) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Earlier") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text("Mobile ${DataRules.bytes(bars.sumOf { it.mobile })} · Wi-Fi ${DataRules.bytes(bars.sumOf { it.wifi })}", style = MaterialTheme.typography.bodySmall, color = quiet)
            }
            IconButton(onClick = { move(1) }, enabled = shift < 0) { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Later") }
        }
        Row {
            Column(Modifier.height(200.dp).padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                (lines downTo 0).forEach { Text(DataRules.bytes(step * it), style = MaterialTheme.typography.labelSmall, color = quiet) }
            }
            Canvas(Modifier.weight(1f).height(200.dp).pointerInput(bars) {
                detectTapGestures { o -> bars.getOrNull((o.x / size.width * bars.size).toInt())?.let(pick) }
            }) {
                val grid = quiet.copy(alpha = 0.25f)
                for (i in 0..lines) { val y = size.height * i / lines; drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f) }
                val slot = size.width / bars.size; val w = slot * 0.7f
                fun y(v: Long) = size.height - size.height * (v.toDouble() / top).toFloat()
                bars.forEachIndexed { i, b ->
                    val x = i * slot + (slot - w) / 2
                    val dim = picked != null && picked != b
                    val m = if (dim) lime.copy(alpha = 0.35f) else lime; val f = if (dim) WIFI_COLOR.copy(alpha = 0.35f) else WIFI_COLOR
                    drawRect(m, Offset(x, y(b.mobile)), Size(w, size.height - y(b.mobile)))
                    drawRect(f, Offset(x, y(b.mobile + b.wifi)), Size(w, y(b.mobile) - y(b.mobile + b.wifi)))
                }
                // the cycle's running total against the plan, and the pace to its end, on a scale of its own (the plan's size at the top)
                running?.let { s ->
                    val scale = maxOf(s.size, s.projection).toDouble()
                    fun ry(v: Long) = size.height - size.height * (v / scale).toFloat()
                    drawLine(ink.copy(alpha = 0.6f), Offset(0f, ry(s.size)), Offset(size.width, ry(s.size)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                    var acc = 0L; var last = Offset(0f, size.height)
                    s.perDay.forEachIndexed { i, v -> acc += v; val p = Offset((i + 1) * slot, ry(acc)); drawLine(ink, last, p, 4f, StrokeCap.Round); last = p }
                    drawLine(ink.copy(alpha = 0.6f), last, Offset(size.width, ry(s.projection)), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                }
            }
        }
        Row(Modifier.padding(start = 44.dp)) {
            val every = maxOf(1, bars.size / 8)
            // a label wider than its bar's slot spills over its empty neighbours instead of being cut
            bars.forEachIndexed { i, b -> Box(Modifier.weight(1f).wrapContentWidth(unbounded = true)) {
                if (i % every == 0) Text(b.label, style = MaterialTheme.typography.labelSmall, color = quiet, softWrap = false, overflow = TextOverflow.Visible)
            } }
        }
        picked?.let { b -> Text("${b.label}${if (span == Span.Day) ":00" else ""}: mobile ${DataRules.bytes(b.mobile)} · Wi-Fi ${DataRules.bytes(b.wifi)}", style = MaterialTheme.typography.bodySmall) }
        if (running != null) Text("Line: the cycle's total · dashed: your plan and the pace to its end", style = MaterialTheme.typography.labelSmall, color = quiet)
    }
}

@Composable private fun AppRow(name: String, mobile: Long, wifi: Long, most: Long, open: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = open).padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(listOfNotNull(mobile.takeIf { it > 0 }?.let { "${DataRules.bytes(it)} mobile" }, wifi.takeIf { it > 0 }?.let { "${DataRules.bytes(it)} Wi-Fi" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)) {
            val m = mobile.toFloat() / most.coerceAtLeast(1); val w = wifi.toFloat() / most.coerceAtLeast(1)
            if (m > 0) Box(Modifier.weight(m).fillMaxHeight().background(dataColor()))
            if (w > 0) Box(Modifier.weight(w).fillMaxHeight().background(WIFI_COLOR))
            if (1 - m - w > 0.001f) Spacer(Modifier.weight(1 - m - w))
        }
    }
}

/** An app's page: its days, foreground and background, roaming, and Android's settings for it (to restrict its data). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AppSheet(key: String, months: List<DataMonth>, close: () -> Unit) {
    val context = LocalContext.current
    val rows = remember(key, months) { months.flatMap { it.apps }.filter { it.app == key } }
    val today = LocalDate.now()
    val days = (29 downTo 0).map { today.minusDays(it.toLong()) }.map { d -> rows.filter { it.day == d }.let { l ->
        Bar("${d.dayOfMonth}", d, d.plusDays(1), l.filter { it.net == "mobile" }.sumOf { it.total }, l.filter { it.net == "wifi" }.sumOf { it.total }) } }
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(rows.firstOrNull()?.name ?: key, style = MaterialTheme.typography.titleLarge)
            Chart(days, Span.Month, null, null, null, 0, {}, {})
            val mobile = rows.filter { it.net == "mobile" }
            Text("Last 30 days on mobile: ${DataRules.bytes(mobile.filter { !it.day.isBefore(today.minusDays(29)) }.sumOf { it.total })}")
            Text("In front ${DataRules.bytes(mobile.sumOf { it.fg })} · in the background ${DataRules.bytes(mobile.sumOf { it.bg })} · roaming ${DataRules.bytes(mobile.sumOf { it.roaming })}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Wi-Fi: ${DataRules.bytes(rows.filter { it.net == "wifi" }.sumOf { it.total })}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if ('.' in key) TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", key, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text("Android's settings for this app (background data)") }
        }
    }
}

// ---- Devices: the three together, and the phone's hotspot ----

@Composable private fun DevicesTab(all: Map<String, List<DataMonth>>, pad: PaddingValues) {
    val today = LocalDate.now()
    val plan = DataFiles.plan()
    val cycle = DataRules.cycle(plan ?: DataPlan(), today)
    fun inCycle(m: List<DataMonth>) = DataFiles.days(m).filter { it.day in cycle }
    val devices = all.mapValues { (_, m) -> inCycle(m) }.filterValues { it.isNotEmpty() }
    val phone = devices.maxByOrNull { (_, d) -> d.sumOf { it.mobile } }?.key
    val hotspot = devices[phone].orEmpty().sumOf { it.hotspot }
    val clients = devices.filterKeys { it != phone }.mapValues { (_, d) -> d.sumOf { it.viaHotspot } }.filterValues { it > 0 }
    val label = { d: String -> if (d == DataFiles.device()) "$d (this one)" else d }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("This cycle, from ${cycle.start.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}", color = dataColor(), fontWeight = FontWeight.Bold) }
        if (devices.isEmpty()) item { Text("Nothing counted yet. The tablet's and the computer's counts arrive with Local Sync.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val most = devices.values.maxOfOrNull { d -> d.sumOf { it.mobile + it.wifi } } ?: 1
        devices.forEach { (d, days) -> item(key = d) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label(d), style = MaterialTheme.typography.titleSmall)
                AppRow(listOfNotNull("Mobile ${DataRules.bytes(days.sumOf { it.mobile })}".takeIf { days.sumOf { it.mobile } > 0 },
                    "Wi-Fi ${DataRules.bytes(days.sumOf { it.wifi })}", days.sumOf { it.viaHotspot }.takeIf { it > 0 }?.let { "over the phone's hotspot ${DataRules.bytes(it)}" }).joinToString(" · "),
                    days.sumOf { it.mobile }, days.sumOf { it.wifi }, most) {}
            }
        } }
        if (hotspot > 0 || clients.isNotEmpty()) item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The phone's hotspot: ${DataRules.bytes(hotspot)}", style = MaterialTheme.typography.titleMedium)
                    val rows = clients.entries.sortedByDescending { it.value }.map { it.key to it.value } + ("Difference" to (hotspot - clients.values.sum()))
                    rows.forEachIndexed { i, (who, b) ->
                        val share = if (hotspot > 0) (b * 100 / hotspot).toInt() else 0
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(label(who) + if (i == 0 && who != "Difference") "  · the most" else "", Modifier.weight(1f))
                            Text("${DataRules.bytes(maxOf(0, b))}${if (b < 0) " (−${DataRules.bytes(-b)})" else ""} · $share %", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text("The phone counts all its hotspot carried; the tablet and the computer each count what they used over it. The difference: " +
                        "another device on the hotspot, hours they were off or not counting, and the network's own overheads. Tetra's own sync between your " +
                        "devices never reaches the mobile network and is not counted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---- the stopwatch: data used between Start and Stop (a trip, a film) ----

@Composable private fun Stopwatch() {
    val context = LocalContext.current
    val p = remember { context.getSharedPreferences("data", Context.MODE_PRIVATE) }
    var started by remember { mutableLongStateOf(p.getLong("swAt", 0)) }
    var laps by remember { mutableStateOf(p.getString("swLaps", "").orEmpty().lines().filter { it.isNotBlank() }) }
    fun mobile() = TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes()
    fun total() = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Stopwatch", style = MaterialTheme.typography.titleMedium)
        if (started == 0L) TextButton(onClick = {
            started = System.currentTimeMillis()
            p.edit().putLong("swAt", started).putLong("swMobile", mobile()).putLong("swTotal", total()).apply()
        }, colors = sheetButton()) { Text("Start counting") }
        else {
            val m = mobile() - p.getLong("swMobile", 0); val t = total() - p.getLong("swTotal", 0)
            Text("Counting since ${java.time.Instant.ofEpochMilli(started).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH))}: " +
                "mobile ${DataRules.bytes(maxOf(0, m))} · Wi-Fi ${DataRules.bytes(maxOf(0, t - m))}")
            TextButton(onClick = {
                val lap = "${java.time.Instant.ofEpochMilli(started).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH))}: mobile ${DataRules.bytes(maxOf(0, m))}, Wi-Fi ${DataRules.bytes(maxOf(0, t - m))}"
                laps = (listOf(lap) + laps).take(10); started = 0
                p.edit().putLong("swAt", 0).putString("swLaps", laps.joinToString("\n")).apply()
            }, colors = sheetButton()) { Text("Stop") }
        }
        laps.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("Data used between Start and Stop: a trip, a film. A restart while counting loses the count.", style = MaterialTheme.typography.labelSmall)
    }
}

// ---- Setup: one step a page (the user, 25 Oct: "a Setup page that takes you by the hand step by step") ----

@Composable
private fun DataSetup(old: DataPlan?, months: List<DataMonth>, done: () -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var plan by remember { mutableStateOf(old ?: DataPlan(kind = DataPlan.FAIR_USE, size = 300_000_000_000, day = LocalDate.now().dayOfMonth)) }
    var usedSoFar by remember { mutableStateOf("") }
    val prefs = remember { context.getSharedPreferences("data", Context.MODE_PRIVATE) }
    var live by remember { mutableStateOf(DataSpeed.enabled(context)) }
    var budgetWarn by remember { mutableStateOf(prefs.getBoolean("warnBudget", false)) }
    var resumed by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumed++ }
        lifecycle.lifecycle.addObserver(o); onDispose { lifecycle.lifecycle.removeObserver(o) }
    }
    val access = remember(resumed) { DataStats.hasAccess(context) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumed++ }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resumed++ }
    val steps = listOf("Welcome", "Usage access", "Your plan", "When it renews", "Roaming", "Used so far", "Warnings", "Live speed", "Wi-Fi around", "Done")
    fun finish() {
        var p = plan
        DataRules.parseSize(usedSoFar)?.let { typed ->
            val st = DataRules.status(p.copy(offsetStart = null, offset = 0), DataFiles.days(months), LocalDate.now())
            p = p.copy(offsetStart = st.cycle.start, offset = typed - st.used)
        }
        DataFiles.savePlan(context, p)
        prefs.edit().putBoolean("warnBudget", budgetWarn).apply()
        DataSpeed.setEnabled(context, live)
        done()
    }
    BackHandler { if (step > 0) step-- else back() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Data setup · ${step + 1} of ${steps.size}", style = MaterialTheme.typography.labelLarge, color = dataColor())
            Text(steps[step], style = MaterialTheme.typography.headlineMedium)
            when (step) {
                0 -> {
                    Text("Data counts how much mobile data and Wi-Fi each app uses, warns you before your plan runs out, and shows the phone, the tablet and the computer together.")
                    Text("To count each app, it reads Android's own usage statistics. Everything stays on your devices: Tetra sends it only to your own tablet and computer, with Local Sync, and to no one else.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                1 -> {
                    Text("Android asks you to switch this on yourself, in its settings: on Tetra's page there, turn on \"Permit usage access\" (it may say \"Permit access to app usage data\"; on Xiaomi the list is \"Apps with usage access\").")
                    Text(if (access) "✓ Usage access is on" else "Not on yet", color = if (access) dataColor() else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    if (!access) Button(onClick = {
                        runCatching { context.startActivity(DataStats.accessIntent(context)) }.onFailure { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    }) { Text("Open Android's page") }
                    Text("Skip it and Data shows only today's totals.", style = MaterialTheme.typography.bodySmall)
                }
                2 -> {
                    Choice(listOf(DataPlan.LIMITED to "Limited", DataPlan.FAIR_USE to "Unlimited with a limit", DataPlan.UNLIMITED to "Unlimited"), plan.kind) { plan = plan.copy(kind = it) }
                    when (plan.kind) {
                        DataPlan.FAIR_USE -> Text("\"Unlimited\" plans have a fair-use limit in their terms (often a few hundred GB). The number is in your carrier's terms.", style = MaterialTheme.typography.bodyMedium)
                        DataPlan.UNLIMITED -> Text("No warnings; the graphs and the per-app lists stay.", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (plan.limited) SizeField(if (plan.kind == DataPlan.FAIR_USE) "The limit" else "The plan's size", plan.size) { plan = plan.copy(size = it) }
                    if (plan.limited) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Carry what is left to the next cycle (rollover)", Modifier.weight(1f)); Switch(plan.rollover, { plan = plan.copy(rollover = it) })
                    }
                }
                3 -> {
                    Choice(listOf(DataPlan.MONTH to "Monthly", DataPlan.WEEK to "Weekly", DataPlan.DAYS to "Every N days"), plan.every) {
                        plan = plan.copy(every = it, day = if (it == DataPlan.WEEK) LocalDate.now().dayOfWeek.value else LocalDate.now().dayOfMonth, from = LocalDate.now())
                    }
                    when (plan.every) {
                        DataPlan.MONTH -> { Text("On day"); Choice((1..31).map { it to "$it" }, plan.day) { plan = plan.copy(day = it) } }
                        DataPlan.WEEK -> Choice(DayOfWeek.entries.map { it.value to it.getDisplayName(DateStyle.SHORT, Locale.ENGLISH) }, plan.day) { plan = plan.copy(day = it) }
                        else -> { Stepper("Every", plan.days, 1..365, " days") { plan = plan.copy(days = it) }; Text("Counting from today, ${plan.from}") }
                    }
                    val c = DataRules.cycle(plan, LocalDate.now())
                    Text("This cycle: ${c.start.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} – ${c.end.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}", color = dataColor())
                    if (plan.limited) SizeField("A fixed daily quota (optional; empty = what is left over the days left)", plan.daily, optional = true) { plan = plan.copy(daily = it) }
                }
                4 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("In the EU, roaming counts against my plan (as at home)", Modifier.weight(1f)); Switch(plan.euCounts, { plan = plan.copy(euCounts = it) }) }
                    Text("Outside the EU: a day pass?", style = MaterialTheme.typography.titleMedium)
                    var price by remember { mutableStateOf(if (plan.passPrice > 0) "%.0f".format(plan.passPrice) else "") }
                    OutlinedTextField(price, { price = it; plan = plan.copy(passPrice = it.replace(',', '.').toDoubleOrNull() ?: 0.0) }, label = { Text("Price a day (€), empty = none") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (plan.passPrice > 0) SizeField("Data a day", plan.passSize.takeIf { it > 0 } ?: 300_000_000) { plan = plan.copy(passSize = it) }
                    Text("Abroad, Data tells you when roaming begins, the day's first MB (when a day pass starts costing), at 80 % and 100 % of the pass, and when you are back — each with a button to Android's switch to turn data roaming off. Android lets no app switch it off by itself.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                5 -> {
                    Text("Your carrier's app or SMS says how much you have used this cycle. Type it to start level with it (optional): Data then counts from there.")
                    OutlinedTextField(usedSoFar, { usedSoFar = it }, label = { Text("Used so far, e.g. 41 GB") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (usedSoFar.isNotBlank() && DataRules.parseSize(usedSoFar) == null) Text("Write a size, as 41 GB or 800 MB", color = MaterialTheme.colorScheme.error)
                }
                6 -> {
                    Text("Warnings at", style = MaterialTheme.typography.titleMedium)
                    listOf(50, 80, 90, 100).forEach { w -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$w %", Modifier.weight(1f)); Checkbox(w in plan.warn, { on -> plan = plan.copy(warn = (if (on) plan.warn + w else plan.warn - w).sorted()) })
                    } }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("When a day's budget is passed", Modifier.weight(1f)); Switch(budgetWarn, { budgetWarn = it }) }
                    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        Button(onClick = { askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                }
                7 -> {
                    Text("The download and upload speed as the status bar's icon, while the screen is on (it stops in your pocket, so it costs next to nothing).")
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Live speed", Modifier.weight(1f)); Switch(live, { live = it }) }
                }
                8 -> {
                    Text("To list the Wi-Fi networks around and rate their channels, Android needs the location permission and Location switched on — only while Data's Signal page is open. Optional: the signal meter itself needs nothing.")
                    val has = remember(resumed) { context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED }
                    if (has) Text("✓ Location allowed", color = dataColor()) else Button(onClick = { askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Allow location") }
                }
                else -> {
                    Text("Ready. " + when (plan.kind) {
                        DataPlan.UNLIMITED -> "No limit; Data counts and shows."
                        else -> "${DataRules.bytes(plan.size)}${if (plan.kind == DataPlan.FAIR_USE) " fair use" else ""}, renewing ${DataRules.cycle(plan, LocalDate.now()).end.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}."
                    })
                    Text("Change any of it later from Data's ⋯ › Setup, or Settings › Data.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (step > 0) step-- else back() }) { Text(if (step == 0) "Cancel" else "‹ Back") }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                steps.indices.forEach { i -> Box(Modifier.padding(3.dp).size(if (i == step) 9.dp else 6.dp).clip(CircleShape).background(if (i <= step) dataColor() else MaterialTheme.colorScheme.outlineVariant)) }
            }
            Button(onClick = { if (step == steps.lastIndex) finish() else step++ },
                enabled = !(step == 5 && usedSoFar.isNotBlank() && DataRules.parseSize(usedSoFar) == null),
                colors = ButtonDefaults.buttonColors(containerColor = dataColor(), contentColor = onData())) { Text(if (step == steps.lastIndex) "Done" else "Next") }
        }
    }
}

@Composable private fun <T> Choice(options: List<Pair<T, String>>, chosen: T, pick: (T) -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) -> FilterChip(v == chosen, { pick(v) }, { Text(label) },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = dataColor(), selectedLabelColor = onData())) }
    }

@Composable private fun Stepper(label: String, value: Int, range: IntRange, unit: String = "", set: (Int) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f))
    IconButton(onClick = { set((value - 1).coerceIn(range)) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
    Text("$value$unit", style = MaterialTheme.typography.titleMedium)
    IconButton(onClick = { set((value + 1).coerceIn(range)) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
}

@Composable private fun SizeField(label: String, bytes: Long, optional: Boolean = false, set: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (bytes > 0) DataRules.bytes(bytes) else "") }
    val bad = text.isNotBlank() && DataRules.parseSize(text) == null
    OutlinedTextField(text, { text = it; DataRules.parseSize(it)?.let(set) ?: run { if (optional && it.isBlank()) set(0) } }, label = { Text(label) }, singleLine = true,
        isError = bad, supportingText = { Text(if (bad) "Write a size, as 300 GB or 800 MB" else "A number alone is GB") }, modifier = Modifier.fillMaxWidth())
}
