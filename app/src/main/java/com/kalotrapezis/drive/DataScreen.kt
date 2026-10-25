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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
/** Another phone's hotspot (the tablet's, the computer's use of the phone's): neither mobile nor plain Wi-Fi (the user, 25 Oct). */
private val HOTSPOT_COLOR = Color(0xFFFF9F1C)
/** A phone's metered Wi-Fi: the car's, Android Auto (the user, 25 Oct: "Android Auto has its own Wi-Fi … make it Android Auto"). */
private val CAR_COLOR = Color(0xFF26C6DA)

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
    val firstPlan = remember { DataFiles.plan() } // read once, not on every recomposition
    val plan by produceState(firstPlan, version) { value = withContext(Dispatchers.IO) { DataFiles.plan() } }
    var setup by remember { mutableStateOf(start == "setup" || (firstPlan == null && !PhoneRelay.noCalls(context))) }
    // the counts as last shown at once (opened again), else as written; today's from Android (a second and more on the phone)
    // follow, and the bars and numbers grow to them (the user, 25 Oct: "use the old data… then update with animations")
    val all by produceState(lastData, version) {
        if (value.isEmpty()) value = withContext(Dispatchers.IO) { DataFiles.all() }
        value = withContext(Dispatchers.IO) {
            runCatching { DataStats.record(context); DataStats.checkRoaming(context) }.onFailure { WriterLog.w("Data: ${it.message}") }
            DataStats.schedule(context); DataStats.watchUsage(context); DataSignal.watch(context)
            DataFiles.all()
        }.also { lastData = it }
    }
    OpenTiming("Data", all.isNotEmpty())
    // the plan and the other devices' counts fetched from the computer now, not at the next sync (the tablet has no plan of its own)
    LaunchedEffect(Unit) {
        val got = withContext(Dispatchers.IO) { runCatching { SyncClient(context, SyncStore(context)).scopeOnly("data") }.isSuccess }
        if (got) version++
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
/** [a] on its way to [b] ([t] from 0 to 1): each count between the two. */
private fun mix(a: Bar, b: Bar, t: Float): Bar {
    if (t >= 1f || a == b) return b
    fun m(x: Long, y: Long) = x + ((y - x) * t).toLong()
    return b.copy(mobile = m(a.mobile, b.mobile), wifi = m(a.wifi, b.wifi), hotspot = m(a.hotspot, b.hotspot), car = m(a.car, b.car),
        parts = if (a.parts.size == b.parts.size) a.parts.zip(b.parts) { x, y -> m(x, y) } else b.parts)
}

private data class Bar(val label: String, val from: LocalDate, val until: LocalDate, val mobile: Long, val wifi: Long, val hotspot: Long = 0, val hour: String? = null, val forecast: Boolean = false,
    val parts: List<Long> = emptyList(), val car: Long = 0) { // by app (the Apps view), in the series' order; Android Auto's link
    val total get() = if (parts.isNotEmpty()) parts.sum() else mobile + wifi + hotspot + car
}

/**
 * A day as (mobile, hotspot, Wi-Fi). On a phone the hotspot is what it shared (part of its mobile data) and a metered Wi-Fi is
 * the car's (Android Auto): Wi-Fi. On a tablet or a computer the hotspot is what it used through the phone (part of its Wi-Fi).
 */
private fun split(d: DataDay?, phone: Boolean): Triple<Long, Long, Long> = when {
    d == null -> Triple(0, 0, 0)
    phone -> Triple(d.mobile - d.hotspot, d.hotspot, d.wifi - d.viaHotspot) // the rest of its metered Wi-Fi is the car's: carOf
    else -> Triple(d.mobile, d.viaHotspot, d.wifi - d.viaHotspot)
}
private fun carOf(d: DataDay?, phone: Boolean) = if (phone && d != null) d.viaHotspot else 0L

private fun bars(span: Span, shift: Int, plan: DataPlan?, months: List<DataMonth>, today: LocalDate, phone: Boolean): List<Bar> {
    val days = DataFiles.days(months).associateBy { it.day }
    val split = days.mapValues { (_, d) -> split(d, phone) }
    val histories = listOf(split.mapValues { it.value.first }, split.mapValues { it.value.second }, split.mapValues { it.value.third })
    val carHistory = days.mapValues { (_, d) -> carOf(d, phone) }
    fun day(d: LocalDate, label: String): Bar {
        // the days to come: each kind's forecast from the weekdays before (faint bars)
        if (d.isAfter(today)) return histories.map { DataRules.forecast(it, d, today) }.let { (m, h, w) -> Bar(label, d, d.plusDays(1), m, w, h, forecast = true, car = DataRules.forecast(carHistory, d, today)) }
        val (m, h, w) = split(days[d], phone)
        return Bar(label, d, d.plusDays(1), m, w, h, car = carOf(days[d], phone))
    }
    return when (span) {
        Span.Day -> {
            val d = today.plusDays(shift.toLong())
            val hrs = months.flatMap { it.hours }.filter { it.hour.startsWith(d.toString()) }
            // Android's buckets are 2 hours from midnight UTC, so here they may start on odd hours: each goes to its pair of hours
            fun slot(h: DataHour) = (h.hour.substringAfter('T').toIntOrNull() ?: 0) / 2 * 2
            (0 until 24 step 2).map { h ->
                fun sum(net: String) = hrs.filter { slot(it) == h && it.net == net }.sumOf { it.bytes }
                if (phone) Bar("$h", d, d.plusDays(1), sum("mobile"), sum("wifi"), sum("tether"), "$h")
                else sum("hotspot").let { hot -> Bar("$h", d, d.plusDays(1), sum("mobile"), maxOf(0, sum("wifi") - hot), hot, "$h") }
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
                val s = inM.map { split(it, phone) }
                Bar(m.month.getDisplayName(DateStyle.NARROW, Locale.ENGLISH), m, m.plusMonths(1), s.sumOf { it.first }, s.sumOf { it.third }, s.sumOf { it.second }, car = inM.sumOf { carOf(it, phone) })
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
    var allApps by remember { mutableStateOf(false) }
    var byApp by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val isPhone = remember { !PhoneRelay.noCalls(context) }
    // the plan's data by app on every device (the user, 25 Oct: "a clear chart only with apps — Firefox, Claude, Teams, Viber"): on a phone its own apps on
    // mobile and what the tablet's and the computer's apps took through its hotspot; on a tablet its own apps
    val appDays = remember(all, isPhone) {
        val own = mine.flatMap { it.apps }.filter { if (isPhone) it.net == "mobile" && it.app != "hotspot" else true }
        val others = if (isPhone) all.filterKeys { it != DataFiles.device() }.values.flatten().flatMap { it.apps }.filter { it.net == "hotspot" } else emptyList()
        (own + others).map { Triple(it.day, it.name.ifBlank { it.app }, it.total) }
    }
    val shown = remember(span, shift, mine, plan) { bars(span, shift, plan, mine, today, isPhone) }
    val drops = remember(all) { DataSignal.drops(all).mapNotNull { runCatching { java.time.OffsetDateTime.parse(it.second.at).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime() }.getOrNull() } }
    val from = picked?.from ?: shown.first().from; val until = picked?.until ?: shown.last().until
    val apps = remember(mine, from, until) {
        mine.flatMap { it.apps }.filter { !it.day.isBefore(from) && it.day.isBefore(until) }.groupBy { it.app }
            .map { (k, l) ->
                fun sum(net: String) = l.filter { it.net == net }.sumOf { it.total }
                // on a phone "Hotspot" is what it shared (Android's tethering, on mobile); a metered Wi-Fi there is the car's
                val use = if (isPhone) (if (k == "hotspot") Use(0, sum("mobile"), sum("wifi")) else Use(sum("mobile"), 0, sum("wifi") + sum("hotspot")))
                    else Use(sum("mobile"), sum("hotspot"), sum("wifi"))
                Triple(k, l.first().name, use)
            }
            .filter { it.third.total > 0 }.sortedByDescending { it.third.mobile * 4 + it.third.hotspot * 2 + it.third.wifi } // what costs first
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val head: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!DataStats.hasAccess(context)) Notice("Turn on Usage access so Data can count each app's data. It stays on your devices.", "Open") {
                    runCatching { context.startActivity(DataStats.accessIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                // the plan is the phone's: a tablet has none of its own and only waits for it
                if (plan == null && PhoneRelay.noCalls(context)) Text("The phone's plan arrives with Local Sync once Data is set up on the phone.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (plan == null) Notice("Tell Data your plan: its size and when it renews, for warnings and a daily budget.", "Setup", openSetup)
                else if (status != null) PlanHead(plan, status)
                SpanChips(span) { span = it }
                if (span != Span.Day) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                        Row(Modifier.padding(3.dp)) {
                            listOf(false to "Kinds", true to "Apps").forEach { (a, label) ->
                                Surface(onClick = { byApp = a }, shape = CircleShape, color = if (a == byApp) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent) {
                                    Text(label, Modifier.padding(horizontal = 14.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                val appView = if (byApp && span != Span.Day) byApps(shown, appDays) else null
                Chart(appView?.first ?: shown, span, plan, status, picked, shift, { shift += it }, drops, appView?.second) { picked = if (picked == it) null else it }
                val myDays = remember(mine) { DataFiles.days(if (isPhone) mine else phone) }
                if (plan != null && status != null && plan.limited) CycleChart(plan, status, myDays.associate { it.day to DataRules.counted(plan, it) }, today)
                val metrics = remember(plan, status, mine) { DataInsights.metrics(plan, status, DataFiles.days(mine), today, isPhone) }
                MetricsCard(metrics, isPhone)
                val tips = remember(metrics, mine) { DataInsights.tips(plan, status, metrics, mine.flatMap { it.apps }.filter { status == null || it.day in status.cycle }, isPhone) }
                if (tips.isNotEmpty()) TipsCard(tips)
            }
        }
        val list: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            item { Text(picked?.let { "Apps · ${it.label}" + (if (span == Span.Day) ":00" else "") + " (tap the bar again for all)" } ?: "Apps", Modifier.padding(vertical = 8.dp),
                color = dataColor(), fontWeight = FontWeight.Bold) }
            if (apps.isEmpty()) item { Text(if (DataStats.hasAccess(context)) "Nothing used then" else "Needs Usage access", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val most = apps.maxOfOrNull { it.third.total } ?: 1
            // the ten that matter; the rest, a tap away (the user, 25 Oct: "no need to list every app")
            apps.take(10).forEach { (key, name, use) -> item(key = key) {
                // the phone's hotspot opens into each device that used it, and their apps (the user, 25 Oct: "a V expand option on hotspot")
                if (isPhone && key == "hotspot") HotspotRow(name, use, most, all, from, until) else AppRow(name, use, most) { app = key }
            } }
            if (apps.size > 10) item { TextButton(onClick = { allApps = true }) { Text("All apps and services (${apps.size})", color = dataColor()) } }
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
    if (allApps) AllApps(apps, { allApps = false }) { app = it }
}

/** The counts last shown: Data opened again starts from them (a few months of one file per device: small). */
private var lastData = emptyMap<String, List<DataMonth>>()

/** [v] counting to its new value when it changes (0.7 s), exact once there. */
@Composable private fun animated(v: Long): Long {
    val f by animateFloatAsState(v.toFloat(), tween(700), label = "count")
    return if (f == v.toFloat()) v else f.toLong()
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
    val used = animated(s.used); val today = animated(s.today)
    val share = if (plan.limited && s.size > 0) (used.toFloat() / s.size).coerceIn(0f, 1f) else 0f
    val over = plan.limited && used > s.size
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 12.dp.toPx(); val inset = w / 2
                drawArc(track, 0f, 360f, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w))
                drawArc(if (over) Color(0xFFE53935) else c, -90f, 360f * share, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(if (plan.limited) "${(used * 100 / s.size.coerceAtLeast(1))} %" else "∞", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val of = when (plan.kind) { DataPlan.FAIR_USE -> " of ${DataRules.bytes(s.size)} fair use"; DataPlan.LIMITED -> " of ${DataRules.bytes(s.size)}"; else -> " this cycle" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(DataRules.bytes(used) + of, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                Info("Your plan", "The ring: how much of the limit the cycle has used (counting from what you typed from the carrier, if you did). Budget: what is left " +
                    "shared over the days left, today included — staying under it, the plan lasts. Forecast: where your usual days take you by the renewal.")
            }
            Text("${s.daysLeft} days left · renews ${s.cycle.end.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}", style = MaterialTheme.typography.bodyMedium)
            Text("Today ${DataRules.bytes(today)}" + if (plan.limited) " · budget ${DataRules.bytes(s.budget)}/day" else " · average ${DataRules.bytes(s.average)}/day", style = MaterialTheme.typography.bodyMedium)
            if (plan.limited) {
                val runsOut = s.projection > s.size
                Text("Forecast: ${DataRules.bytes(s.projection)} by ${s.cycle.end.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} " + if (runsOut) "⚠" else "✓",
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
                    contentColor = if (s == span) onData() else MaterialTheme.colorScheme.onSurface) { Text(s.name, Modifier.padding(horizontal = 11.dp, vertical = 8.dp), maxLines = 1, softWrap = false) }
            }
        }
    }
}

/**
 * Bars of mobile (lime) and Wi-Fi (blue) stacked; the axis's top is a round number just over the tallest, so a 3 MB hour
 * and a 40 GB month both fill it (the old app stopped at 2 GB). In a cycle, the running total and the plan's line too.
 */
@Composable private fun Chart(bars: List<Bar>, span: Span, plan: DataPlan?, status: DataStatus?, picked: Bar?, shift: Int, move: (Int) -> Unit, drops: List<java.time.LocalDateTime> = emptyList(),
                              series: List<Pair<String, Color>>? = null, pick: (Bar) -> Unit) {
    val lime = dataColor(); val ink = MaterialTheme.colorScheme.onSurface; val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    // the same bars with new counts (today's from Android after the written ones) grow to them; another span or time is drawn at once
    val grow = remember { androidx.compose.animation.core.Animatable(1f) }
    var was by remember { mutableStateOf(bars) }
    var now by remember { mutableStateOf(bars) }
    LaunchedEffect(bars) {
        val same = now.size == bars.size && now.zip(bars).all { (a, b) -> a.label == b.label && a.from == b.from && a.parts.size == b.parts.size } && now.any { it.total > 0 }
        was = if (same) now.mapIndexed { i, a -> mix(was.getOrElse(i) { a }, a, grow.value) } else bars
        now = bars
        grow.snapTo(if (same) 0f else 1f)
        if (same) grow.animateTo(1f, tween(700))
    }
    val drawn = now.mapIndexed { i, b -> mix(was.getOrElse(i) { b }, b, grow.value) }
    val step = DataRules.axisStep(bars.maxOf { it.total })
    val lines = DataRules.axisLines(bars.maxOf { it.total }); val top = step * lines
    // the dashed line: the average of the days used so far (not today's, still running), on the bars' own scale
    val used = bars.filter { !it.forecast && it.total > 0 && !(span != Span.Day && span != Span.Year && it.from == LocalDate.now()) }
    val average = if (span == Span.Day || used.isEmpty()) null else used.sumOf { it.total } / used.size
    val title = when (span) {
        Span.Day -> bars.first().from.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
        Span.Year -> "${bars.first().from.year}"
        else -> bars.first().from.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) + " – " + bars.last().from.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { move(-1) }) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Earlier") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Info("The bars", "Each bar is a day (an hour in Day, a month in Year), stacked: mobile data (lime), the hotspot (orange) — on the phone what it " +
                        "shared, on the tablet and the computer what they used through it — and Wi-Fi (blue). Tap a bar for its apps; tap it again for all. Faint bars: the " +
                        "days to come, each the median of the same weekday over the last three weeks. Dashed line: your average day, everything shown counted. Red dots: signal drops. ‹ › go back and forth in time.")
                }
                val real = bars.filter { !it.forecast }
                Text(if (series != null) "Apps: ${DataRules.bytes(real.sumOf { it.total })}" else Use(real.sumOf { it.mobile }, real.sumOf { it.hotspot }, real.sumOf { it.wifi }, car = real.sumOf { it.car }).words(always = true),
                    style = MaterialTheme.typography.bodySmall, color = quiet)
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
                drawn.forEachIndexed { i, b ->
                    val x = i * slot + (slot - w) / 2
                    val dim = picked != null && picked != bars.getOrNull(i)
                    // stacked from the bottom: mobile, the hotspot, Wi-Fi; a forecast day faint, outlined
                    var base = 0L
                    for ((v, color) in if (series != null) b.parts.zip(series.map { it.second }) else listOf(b.mobile to lime, b.hotspot to HOTSPOT_COLOR, b.wifi to WIFI_COLOR, b.car to CAR_COLOR)) {
                        if (v > 0) {
                            val c = if (b.forecast) color.copy(alpha = 0.22f) else if (dim) color.copy(alpha = 0.35f) else color
                            drawRect(c, Offset(x, y(base + v)), Size(w, y(base) - y(base + v)))
                            if (b.forecast) drawRect(color.copy(alpha = 0.6f), Offset(x, y(base + v)), Size(w, y(base) - y(base + v)), style = Stroke(1.5f))
                        }
                        base += v
                    }
                }
                average?.let { a -> drawLine(ink.copy(alpha = 0.7f), Offset(0f, y(a)), Offset(size.width, y(a)), 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))) }
            }
        }
        // the signal's drops (every device's) as red marks under their bars
        val marked = bars.map { b -> drops.any { t -> !t.toLocalDate().isBefore(b.from) && t.toLocalDate().isBefore(b.until) && (span != Span.Day || t.hour / 2 * 2 == b.label.toIntOrNull()) } }
        if (marked.any { it }) Row(Modifier.padding(start = 44.dp).height(10.dp)) {
            marked.forEach { m -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { if (m) Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFE53935))) } }
        }
        Row(Modifier.padding(start = 44.dp)) {
            val every = maxOf(1, bars.size / 8)
            // a label wider than its bar's slot spills over its empty neighbours instead of being cut
            bars.forEachIndexed { i, b -> Box(Modifier.weight(1f).wrapContentWidth(unbounded = true)) {
                if (i % every == 0) Text(b.label, style = MaterialTheme.typography.labelSmall, color = quiet, softWrap = false, overflow = TextOverflow.Visible)
            } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            (series ?: listOfNotNull("Mobile" to lime, ("Hotspot" to HOTSPOT_COLOR).takeIf { bars.any { it.hotspot > 0 } }, "Wi-Fi" to WIFI_COLOR, ("Android Auto" to CAR_COLOR).takeIf { bars.any { it.car > 0 } })).forEach { (n, c) ->
                Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Text(n, style = MaterialTheme.typography.labelSmall, color = quiet, maxLines = 1)
            }
        }
        picked?.let { b -> Text("${b.label}${if (span == Span.Day) ":00" else ""}: " + (if (b.forecast) "forecast " else "") +
            (if (series != null) series.zip(b.parts).filter { it.second > 0 }.joinToString(" · ") { "${it.first.first} ${DataRules.bytes(it.second)}" } else Use(b.mobile, b.hotspot, b.wifi, car = b.car).words(always = true)),
            style = MaterialTheme.typography.bodySmall) }
        average?.let { a -> Text("Dashed: the average, ${DataRules.bytes(a)} a day" + (if (bars.any { it.forecast }) " · faint: the days to come, from your usual weekdays" else ""),
            style = MaterialTheme.typography.labelSmall, color = quiet) }
        if (marked.any { it }) Text("Red: signal drops (Signal › Drops)", style = MaterialTheme.typography.labelSmall, color = quiet)
    }
}

/** What was used: on mobile, over another phone's hotspot (the tablet's, the computer's), on plain Wi-Fi. */
internal data class Use(val mobile: Long, val hotspot: Long, val wifi: Long, val ethernet: Long = 0, val car: Long = 0) {
    val total get() = mobile + hotspot + wifi + ethernet + car
    fun words(always: Boolean = false) = listOfNotNull(
        (mobile > 0 || always && hotspot == 0L).takeIf { it }?.let { "Mobile ${DataRules.bytes(mobile)}" },
        hotspot.takeIf { it > 0 }?.let { "Hotspot ${DataRules.bytes(it)}" },
        (wifi > 0 || always).takeIf { it }?.let { "Wi-Fi ${DataRules.bytes(wifi)}" },
        ethernet.takeIf { it > 0 }?.let { "Ethernet ${DataRules.bytes(it)}" }, car.takeIf { it > 0 }?.let { "Android Auto ${DataRules.bytes(it)}" }).joinToString(" · ")
}

private val APP_COLORS = listOf(Color(0xFFFF9F1C), Color(0xFF4F86E8), Color(0xFFE91E63), Color(0xFF26C6DA), Color(0xFFAB47BC), Color(0xFFFFCA28))

/** The bars by app: the six that used most over the bars shown, each its colour, the rest grey. */
private fun byApps(bars: List<Bar>, appDays: List<Triple<LocalDate, String, Long>>): Pair<List<Bar>, List<Pair<String, Color>>> {
    val from = bars.first().from; val until = bars.last().until
    val inRange = appDays.filter { !it.first.isBefore(from) && it.first.isBefore(until) }
    val top = inRange.groupBy { it.second }.mapValues { (_, l) -> l.sumOf { it.third } }.entries.sortedByDescending { it.value }.take(6).map { it.key }
    val series = top.mapIndexed { i, n -> n to APP_COLORS[i] } + ("Other apps" to Color(0xFF8A8F94))
    return bars.map { b ->
        if (b.forecast) b.copy(parts = series.map { 0L })
        else inRange.filter { !it.first.isBefore(b.from) && it.first.isBefore(b.until) }.let { l ->
            b.copy(parts = top.map { n -> l.filter { it.second == n }.sumOf { it.third } } + l.filter { it.second !in top }.sumOf { it.third })
        }
    } to series
}

/** The hotspot's line, opening ▾ into the devices that used it and, in each, its apps — from their own files, as they counted. */
@Composable private fun HotspotRow(name: String, use: Use, most: Long, all: Map<String, List<DataMonth>>, from: LocalDate, until: LocalDate) {
    var open by remember { mutableStateOf(false) }
    val devices = remember(all, from, until) {
        all.filterKeys { it != DataFiles.device() }.map { (d, months) ->
            val apps = months.flatMap { it.apps }.filter { it.net == "hotspot" && !it.day.isBefore(from) && it.day.isBefore(until) }
                .groupBy { it.name.ifBlank { it.app } }.map { (n, l) -> n to l.sumOf { it.total } }.sortedByDescending { it.second }
            Triple(DataFiles.label(all, d), apps.sumOf { it.second }, apps)
        }.filter { it.second > 0 }.sortedByDescending { it.second }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { AppRow(name, use, most) { open = !open } }
            IconButton(onClick = { open = !open }) {
                Icon(painterResource(if (open) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down), contentDescription = if (open) "Close" else "Who used it")
            }
        }
        if (open) Column(Modifier.padding(start = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (devices.isEmpty()) Text("The tablet's and the computer's counts have not come yet (Local Sync).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            devices.forEach { (device, total, apps) ->
                Text("$device · ${DataRules.bytes(total)}", style = MaterialTheme.typography.titleSmall)
                apps.take(6).forEach { (n, v) ->
                    Row(Modifier.padding(start = 12.dp)) {
                        Text(n, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(DataRules.bytes(v), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (apps.size > 6) Text("and ${apps.size - 6} more, ${DataRules.bytes(apps.drop(6).sumOf { it.second })}", Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val counted = devices.sumOf { it.second }
            if (devices.isNotEmpty() && use.hotspot > counted) Text("Not counted by them: ${DataRules.bytes(use.hotspot - counted)} (a guest, hours they were off, overheads)",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Every app and service of the chosen days, the small ones too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AllApps(apps: List<Triple<String, String, Use>>, close: () -> Unit, open: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = close) {
        val most = apps.maxOfOrNull { it.third.total } ?: 1
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text("All apps and services", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp)) }
            apps.forEach { (key, name, use) -> item(key = key) { AppRow(name, use, most) { open(key) } } }
        }
    }
}

/** The cycle so far against the limit, and the forecast to its end — on a scale of its own, 0 to the limit. */
@Composable private fun CycleChart(plan: DataPlan, s: DataStatus, history: Map<LocalDate, Long>, today: LocalDate) {
    val lime = dataColor(); val ink = MaterialTheme.colorScheme.onSurface; val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val days = s.cycle.days
    val ahead = generateSequence(today.plusDays(1)) { it.plusDays(1) }.takeWhile { it.isBefore(s.cycle.end) }.map { DataRules.forecast(history, it, today) }.toList()
    val top = maxOf(s.size, s.projection)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("The cycle so far", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Info("The cycle so far", "The solid line adds up the plan's data day by day since ${s.cycle.start.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}; " +
                    "the dashed one carries it on with your usual days to the renewal; the top line is the limit. If the dashed line crosses it, the limit comes before the renewal — " +
                    "the day's budget above says how much a day would last.")
            }
            Row {
                Column(Modifier.height(130.dp).padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                    Text(DataRules.bytes(top), style = MaterialTheme.typography.labelSmall, color = quiet)
                    Text(DataRules.bytes(top / 2), style = MaterialTheme.typography.labelSmall, color = quiet)
                    Text("0", style = MaterialTheme.typography.labelSmall, color = quiet)
                }
                Canvas(Modifier.weight(1f).height(130.dp)) {
                    fun x(i: Int) = size.width * i / days
                    fun y(v: Long) = size.height - size.height * (v.toDouble() / top).toFloat()
                    drawLine(ink.copy(alpha = 0.5f), Offset(0f, y(s.size)), Offset(size.width, y(s.size)), 2f)
                    var acc = 0L; var last = Offset(0f, size.height)
                    s.perDay.forEachIndexed { i, v -> acc += v; val pt = Offset(x(i + 1), y(acc)); drawLine(lime, last, pt, 5f, StrokeCap.Round); last = pt }
                    var fAcc = s.used; var i = s.perDay.size
                    ahead.forEach { v -> fAcc += v; i++; val pt = Offset(x(i), y(fAcc)); drawLine(lime.copy(alpha = 0.6f), last, pt, 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))); last = pt }
                }
            }
            Text("${DataRules.bytes(s.used)} so far · about ${DataRules.bytes(s.projection)} by ${s.cycle.end.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} · limit ${DataRules.bytes(s.size)}",
                style = MaterialTheme.typography.bodySmall, color = quiet)
        }
    }
}

/** A few numbers under the charts. */
@Composable private fun MetricsCard(m: DataMetrics, phone: Boolean) {
    val b = DataRules::bytes
    val tiles = listOfNotNull(
        (if (phone) "Mobile data a day" else "A day, on average") to b(m.average),
        ("Workdays · weekends" to "${b(m.workdays)} · ${b(m.weekends)}").takeIf { m.workdays + m.weekends > 0 },
        m.busiest?.let { (d, v) -> "Busiest day" to "${d.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} · ${b(v)}" },
        ((if (phone) "Through the hotspot" else "Over the phone's hotspot") to "${m.hotspotShare} %").takeIf { m.hotspotShare > 0 },
        "On Wi-Fi" to "${m.wifiShare} %",
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("This cycle in numbers", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Info("In numbers", (if (phone) "The phone's mobile data, as the plan counts it" else "This device's data") + ". Average: the days so far, today left out. " +
                    "Workdays and weekends: the typical (median) day of each over the last three weeks, so one odd day does not pull it. " +
                    (if (phone) "Through the hotspot: the share of the mobile data the phone gave to other devices. " else "Over the phone's hotspot: the share of this device's Wi-Fi that was the phone's hotspot. ") +
                    "On Wi-Fi: Wi-Fi's share of everything this device used.")
            }
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (k, v) -> Column(Modifier.weight(1f)) {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.titleMedium)
                    } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Help for the plan, from the numbers above: information, nothing changed. */
@Composable private fun TipsCard(tips: List<Advice>) {
    Surface(color = dataColor().copy(alpha = 0.12f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("For your plan", style = MaterialTheme.typography.titleSmall, color = dataColor(), modifier = Modifier.weight(1f))
                Info("For your plan", "Worked out from your own days: the pace, where the data goes, the weekdays and the apps. Only information — Data changes nothing; " +
                    "each line says where you could change it yourself.")
            }
            tips.forEach { t -> Column {
                Text(t.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(t.text, style = MaterialTheme.typography.bodyMedium)
            } }
        }
    }
}

@Composable private fun AppRow(name: String, use: Use, most: Long, open: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = open).padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(use.words(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)) {
            val parts = listOf(use.mobile to dataColor(), use.hotspot to HOTSPOT_COLOR, use.wifi to WIFI_COLOR).map { (v, c) -> v.toFloat() / most.coerceAtLeast(1) to c }
            parts.forEach { (f, c) -> if (f > 0) Box(Modifier.weight(f).fillMaxHeight().background(c)) }
            val rest = 1 - parts.sumOf { it.first.toDouble() }.toFloat()
            if (rest > 0.001f) Spacer(Modifier.weight(rest))
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
        Bar("${d.dayOfMonth}", d, d.plusDays(1), l.filter { it.net == "mobile" }.sumOf { it.total }, l.filter { it.net == "wifi" }.sumOf { it.total }, l.filter { it.net == "hotspot" }.sumOf { it.total }) } }
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(rows.firstOrNull()?.name ?: key, style = MaterialTheme.typography.titleLarge)
            Chart(days, Span.Month, null, null, null, 0, {}) {}
            val mobile = rows.filter { it.net == "mobile" }
            Text("Last 30 days on mobile: ${DataRules.bytes(mobile.filter { !it.day.isBefore(today.minusDays(29)) }.sumOf { it.total })}")
            Text("In front ${DataRules.bytes(mobile.sumOf { it.fg })} · in the background ${DataRules.bytes(mobile.sumOf { it.bg })} · roaming ${DataRules.bytes(mobile.sumOf { it.roaming })}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Use(0, rows.filter { it.net == "hotspot" }.sumOf { it.total }, rows.filter { it.net == "wifi" }.sumOf { it.total }).words().ifEmpty { "No Wi-Fi" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val label = { d: String -> DataFiles.label(all, d) + if (d == DataFiles.device()) " (this one)" else "" }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("This cycle, from ${cycle.start.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}", color = dataColor(), fontWeight = FontWeight.Bold) }
        if (devices.isEmpty()) item { Text("Nothing counted yet. The tablet's and the computer's counts arrive with Local Sync.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val most = devices.values.maxOfOrNull { d -> d.sumOf { it.mobile + it.wifi + it.ethernet } } ?: 1
        devices.forEach { (d, days) -> item(key = d) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label(d), style = MaterialTheme.typography.titleSmall)
                val parts = days.map { split(it, d == phone) }
                AppRow("", Use(parts.sumOf { it.first }, parts.sumOf { it.second }, parts.sumOf { it.third }, days.sumOf { it.ethernet }, days.sumOf { carOf(it, d == phone) }), most) {}
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
