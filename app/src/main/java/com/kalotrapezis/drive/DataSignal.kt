package com.kalotrapezis.drive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One Wi-Fi reading: dBm, link speed (Mb/s), frequency (MHz). */
internal data class WifiReading(val rssi: Int, val speed: Int, val mhz: Int, val ssid: String?)

/** A saved spot: where (its name), in which round (where the router was), and what was measured there. */
internal data class DataSpot(val at: String, val spot: String, val round: String, val rssi: Int, val speed: Int, val mhz: Int, val down: Double = 0.0, val up: Double = 0.0, val ping: Long = 0)

/**
 * Data › Signal (Plan.md § Data): the Wi-Fi meter, the spots to find the best place without a map (the user, 25 Oct: "make
 * the strength meter, if we can find it that way"), the networks around, and the signal's drops.
 */
internal object DataSignal {
    // ---- the rules, kept pure for the tests ----

    fun quality(rssi: Int) = when {
        rssi >= -55 -> "Excellent"; rssi >= -67 -> "Good: calls and video"; rssi >= -75 -> "Fair: the web"; rssi >= -85 -> "Poor: it may drop"; else -> "Almost none"
    }
    fun band(mhz: Int) = when { mhz >= 5925 -> "6 GHz"; mhz >= 4900 -> "5 GHz"; mhz > 0 -> "2.4 GHz"; else -> "" }
    fun channel(mhz: Int) = when {
        mhz == 2484 -> 14; mhz in 2412..2472 -> (mhz - 2407) / 5; mhz in 5000..5895 -> (mhz - 5000) / 5; mhz >= 5955 -> (mhz - 5950) / 5; else -> 0
    }
    /** The sound mode's pause between beeps: short when strong, long when weak (a metal detector's way). */
    fun beepEvery(rssi: Int) = (150 + (-30 - rssi).coerceIn(0, 60) * 25).toLong()

    /**
     * The 2.4 GHz channels 1, 6 and 11 rated by the networks heard on or near them (within four channels, as they
     * overlap), the stronger counting more; and 5 GHz's by their own channel. Least crowded first.
     */
    fun crowding(networks: List<Pair<Int, Int>>): List<Pair<Int, Double>> { // (channel, rssi)
        fun weight(rssi: Int) = ((rssi + 100).coerceIn(0, 70)) / 70.0 + 0.2
        val low = listOf(1, 6, 11).map { c -> c to networks.filter { (ch, _) -> ch in 1..14 && kotlin.math.abs(ch - c) < 5 }.sumOf { weight(it.second) } }
        val high = networks.filter { it.first > 14 }.groupBy { it.first }.map { (c, l) -> c to l.sumOf { weight(it.second) } }
        return (low + high).sortedBy { it.second }
    }

    /** Rounds side by side: each round's average over its spots (each spot's newest), best first. */
    fun rounds(spots: List<DataSpot>): List<Pair<String, Double>> = spots.groupBy { it.round }
        .map { (r, l) -> r to l.groupBy { it.spot }.values.map { s -> s.maxBy { it.at }.rssi }.average() }.sortedByDescending { it.second }

    /** A drop worth a line: gone at least [minSecs] and back within ten minutes (longer is leaving, not a drop). */
    fun isDrop(secs: Long, minSecs: Long = 5) = secs in minSecs..600

    // ---- the readings ----

    @Suppress("DEPRECATION") // getConnectionInfo still gives RSSI, speed and frequency without the location permission
    fun wifi(c: Context): WifiReading? {
        val info = c.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo ?: return null
        if (info.rssi <= -127 || info.frequency <= 0) return null
        val ssid = info.ssid?.trim('"')?.takeIf { it.isNotEmpty() && !it.contains("unknown ssid", true) }
        return WifiReading(info.rssi, info.linkSpeed, info.frequency, ssid)
    }

    fun located(c: Context) = c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // ---- spots: Tetra/Data/spots/<device>.jsonl ----

    private fun spotsFile(device: String = DataFiles.device()) = File(File(DataFiles.root(), "spots"), "$device.jsonl")

    fun spotLine(s: DataSpot): String = JSONObject().put("at", s.at).put("spot", s.spot).put("round", s.round).put("rssi", s.rssi).put("speed", s.speed).put("mhz", s.mhz)
        .apply { if (s.down > 0) put("pc", JSONObject().put("down", s.down).put("up", s.up).put("ms", s.ping)) }.toString()

    fun parseSpot(l: String): DataSpot? = runCatching { JSONObject(l).let { o -> val pc = o.optJSONObject("pc")
        DataSpot(o.getString("at"), o.getString("spot"), o.optString("round"), o.getInt("rssi"), o.optInt("speed"), o.optInt("mhz"), pc?.optDouble("down") ?: 0.0, pc?.optDouble("up") ?: 0.0, pc?.optLong("ms") ?: 0) } }.getOrNull()

    fun spots(): List<DataSpot> = File(DataFiles.root(), "spots").listFiles().orEmpty().filter { it.name.endsWith(".jsonl") }.flatMap { f -> f.readLines().mapNotNull(::parseSpot) }

    private fun rewrite(c: Context, keep: (DataSpot) -> Boolean, add: DataSpot? = null) {
        val f = spotsFile()
        val kept = (if (f.isFile) f.readLines() else emptyList()).filter { l -> parseSpot(l)?.let(keep) == true }
        DataFiles.write(f, (kept + listOfNotNull(add?.let(::spotLine))).joinToString("\n", postfix = "\n"))
        ScopedSync.soon(c, "data")
    }
    fun saveSpot(c: Context, s: DataSpot) = rewrite(c, { it.spot != s.spot || it.round != s.round }, s)
    fun deleteSpot(c: Context, spot: String, round: String) = rewrite(c, { it.spot != spot || it.round != round })

    // ---- drops: watched while Tetra's process lives (the listening service usually keeps it so) ----

    fun summaryOn(c: Context) = c.getSharedPreferences("data", Context.MODE_PRIVATE).getBoolean("dropsSummary", false)
    fun setSummary(c: Context, on: Boolean) = c.getSharedPreferences("data", Context.MODE_PRIVATE).edit().putBoolean("dropsSummary", on).apply()

    private var watching = false
    private fun stamp() = ZonedDateTime.now().toOffsetDateTime().withNano(0).toString()

    /** The network and the mobile signal watched for drops and roaming; Android calls back, nothing polls. */
    fun watch(c: Context) {
        val app = c.applicationContext
        if (watching) return
        watching = true
        val cm = app.getSystemService(ConnectivityManager::class.java)
        runCatching { cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            var wifi = false; var lostAt = 0L; var weakSince = 0L
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val now = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                if (now && !wifi && lostAt > 0) {
                    val secs = (System.currentTimeMillis() - lostAt) / 1000
                    if (isDrop(secs)) DataStats.note(app, DataEvent(stamp(), "drop-wifi", secs, wifi(app)?.ssid.orEmpty()))
                    lostAt = 0
                }
                wifi = now
                if (now) { // very weak for 30 s or more: a line of its own
                    val rssi = caps.signalStrength
                    if (rssi != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED && rssi < -80) { if (weakSince == 0L) weakSince = System.currentTimeMillis() }
                    else if (weakSince > 0) { val secs = (System.currentTimeMillis() - weakSince) / 1000; if (secs >= 30) DataStats.note(app, DataEvent(stamp(), "weak-wifi", secs)); weakSince = 0 }
                }
                runCatching { DataStats.checkRoaming(app) }
            }
            override fun onLost(network: Network) { if (wifi) lostAt = System.currentTimeMillis(); wifi = false }
        }) }.onFailure { WriterLog.w("Data: the network is not watched (${it.message})") }
        if (PhoneRelay.noCalls(app)) return
        val tm = app.getSystemService(TelephonyManager::class.java) ?: return
        runCatching { tm.registerTelephonyCallback(ContextCompat.getMainExecutor(app), Mobile(app, cm)) }.onFailure { WriterLog.w("Data: the mobile signal is not watched (${it.message})") }
    }

    private class Mobile(val c: Context, val cm: ConnectivityManager) : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener, TelephonyCallback.ServiceStateListener,
        TelephonyCallback.DataConnectionStateListener, TelephonyCallback.DisplayInfoListener {
        var noServiceAt = 0L; var dataLostAt = 0L; var noneSince = 0L; var gen = ""
        private fun onWifi() = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        override fun onServiceStateChanged(s: ServiceState) {
            if (s.state == ServiceState.STATE_OUT_OF_SERVICE || s.state == ServiceState.STATE_EMERGENCY_ONLY) { if (noServiceAt == 0L) noServiceAt = System.currentTimeMillis() }
            else if (s.state == ServiceState.STATE_IN_SERVICE && noServiceAt > 0) {
                val secs = (System.currentTimeMillis() - noServiceAt) / 1000
                if (isDrop(secs)) DataStats.note(c, DataEvent(stamp(), "no-service", secs, gen)); noServiceAt = 0
            }
        }
        override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
            if (state == TelephonyManager.DATA_DISCONNECTED && !onWifi()) { if (dataLostAt == 0L) dataLostAt = System.currentTimeMillis() }
            else if (state == TelephonyManager.DATA_CONNECTED && dataLostAt > 0) {
                val secs = (System.currentTimeMillis() - dataLostAt) / 1000
                if (isDrop(secs)) DataStats.note(c, DataEvent(stamp(), "drop-mobile", secs, gen)); dataLostAt = 0
            }
        }
        override fun onSignalStrengthsChanged(s: SignalStrength) {
            if (s.level == 0) { if (noneSince == 0L) noneSince = System.currentTimeMillis() }
            else if (noneSince > 0) { val secs = (System.currentTimeMillis() - noneSince) / 1000; if (secs in 30..3600) DataStats.note(c, DataEvent(stamp(), "weak-mobile", secs, gen)); noneSince = 0 }
        }
        override fun onDisplayInfoChanged(d: TelephonyDisplayInfo) {
            val now = when {
                d.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA || d.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED || d.networkType == TelephonyManager.NETWORK_TYPE_NR -> "5G"
                d.networkType == TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                d.networkType in listOf(TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA) -> "3G"
                d.networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN -> gen
                else -> "2G"
            }
            // only a fall to 3G or 2G is a line (5G ↔ 4G changes all day)
            if (now != gen && (now == "3G" || now == "2G") && gen != "" && gen != "3G" && gen != "2G") DataStats.note(c, DataEvent(stamp(), "fell-to-$now", 0, gen))
            gen = now
        }
    }

    private val DROPS = setOf("drop-mobile", "drop-wifi", "no-service", "weak-mobile", "weak-wifi", "left-hotspot", "fell-to-3G", "fell-to-2G")

    /** The drops of all devices, newest first: (device, event). */
    fun drops(all: Map<String, List<DataMonth>>): List<Pair<String, DataEvent>> =
        all.flatMap { (d, m) -> m.flatMap { it.events }.map { d to it } }.filter { it.second.kind in DROPS }.distinct().sortedByDescending { it.second.at }

    fun describe(e: DataEvent): String {
        val t = runCatching { OffsetDateTime.parse(e.at).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH)) }.getOrDefault(e.at)
        val len = if (e.secs >= 60) "${e.secs / 60} min ${e.secs % 60} s" else "${e.secs} s"
        return "$t · " + when (e.kind) {
            "drop-mobile" -> "mobile data lost $len" + (if (e.note.isNotEmpty()) " (${e.note})" else "")
            "no-service" -> "no service $len"
            "weak-mobile" -> "no mobile signal $len"
            "drop-wifi" -> "Wi-Fi lost $len" + (if (e.note.isNotEmpty()) " (${e.note})" else "")
            "weak-wifi" -> "Wi-Fi very weak $len"
            "left-hotspot" -> "off the phone's hotspot $len"
            else -> "fell from ${e.note} to ${e.kind.removePrefix("fell-to-")}"
        }
    }

    /** Yesterday's drops in one notice, once a day (Settings › Data › Signal drops, off by default). */
    fun daily(c: Context, all: Map<String, List<DataMonth>>) {
        if (!summaryOn(c)) return
        val p = c.getSharedPreferences("data", Context.MODE_PRIVATE)
        val yesterday = LocalDate.now().minusDays(1)
        if (p.getString("dropsDay", "") == yesterday.toString()) return
        val l = drops(all).filter { runCatching { OffsetDateTime.parse(it.second.at).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() == yesterday }.getOrDefault(false) }
        p.edit().putString("dropsDay", yesterday.toString()).apply()
        if (l.isNotEmpty()) DataStats.notify(c, 0xDA20, "Signal drops yesterday: ${l.size}", l.take(4).joinToString("\n") { (d, e) -> "$d: " + describe(e).substringAfter(" · ") })
    }
}

// ---- the Signal tab ----

@Composable
internal fun DataSignalTab(pad: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(DataSignal.wifi(context)) }
    val history = remember { mutableStateListOf<Int>() }
    var sound by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { reading = DataSignal.wifi(context); reading?.let { history += it.rssi; if (history.size > 120) history.removeAt(0) }; delay(1000) } }
    LaunchedEffect(sound) {
        if (!sound) return@LaunchedEffect
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() ?: return@LaunchedEffect
        try { while (true) { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 60); delay(DataSignal.beepEvery(reading?.rssi ?: -100)) } } finally { tone.release() }
    }
    var version by remember { mutableIntStateOf(0) }
    val spots by produceState(emptyList<DataSpot>(), version) { value = withContext(Dispatchers.IO) { runCatching { DataSignal.spots() }.getOrDefault(emptyList()) } }
    val all by produceState(emptyMap<String, List<DataMonth>>(), version) { value = withContext(Dispatchers.IO) { runCatching { DataFiles.all() }.getOrDefault(emptyMap()) } }
    var saving by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("data", Context.MODE_PRIVATE) }
    var round by remember { mutableStateOf(prefs.getString("round", "").orEmpty()) }
    var computer by remember { mutableStateOf<JSONObject?>(null) }
    var hotspot by remember { mutableStateOf(false) }
    LaunchedEffect(hotspot) { while (hotspot) { computer = withContext(Dispatchers.IO) { runCatching { SyncClient(context, SyncStore(context)).computerWifi() }.getOrElse { JSONObject().put("error", it.message) } }; delay(2000) } }
    var around by remember { mutableStateOf<List<ScanResult>?>(null) }
    var speed by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Meter(reading, history, sound) { sound = it } }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { saving = true }, enabled = reading != null, colors = ButtonDefaults.buttonColors(containerColor = dataColor(), contentColor = onData())) { Text("Save this spot") }
                OutlinedButton(onClick = {
                    speed = "Measuring…"
                    scope.launch { speed = withContext(Dispatchers.IO) { runCatching { SyncClient(context, SyncStore(context)).speedTest() }
                        .fold({ (d, u, ms) -> "To the computer: ↓ %.0f Mb/s · ↑ %.0f Mb/s · %d ms".format(d, u, ms) }, { "The computer did not answer: ${it.message}" }) } }
                }) { Text("Speed to the computer") }
            }
        }
        speed?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium) } }
        item { SectionTitle("Spots", "Stand somewhere and save it. Move the router (or the phone), name the new round, walk the same spots: the rounds side by side say which place was best.") }
        item {
            OutlinedTextField(round, { round = it; prefs.edit().putString("round", it).apply() },
                label = { Text("This round (where the router or the phone is)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        val rounds = DataSignal.rounds(spots)
        if (rounds.size > 1) item {
            Text("Best round: ${rounds.first().first.ifBlank { "(no name)" }}, average ${"%.0f".format(rounds.first().second)} dBm" +
                rounds.drop(1).joinToString("") { "\n${it.first.ifBlank { "(no name)" }}: ${"%.0f".format(it.second)} dBm" }, color = dataColor())
        }
        val bySpot = spots.groupBy { it.spot }.toList().sortedByDescending { (_, l) -> l.maxOf { it.rssi } }
        if (bySpot.isEmpty()) item { Text("No spots yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        bySpot.forEach { (name, l) -> item(key = "spot:$name") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                l.sortedByDescending { it.rssi }.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.round.ifBlank { "—" }}: ${s.rssi} dBm · ${s.speed} Mb/s · ${DataSignal.band(s.mhz)}" + (if (s.down > 0) " · PC ↓%.0f Mb/s".format(s.down) else ""),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { DataSignal.deleteSpot(context, s.spot, s.round) }; version++ } }) { Text("Remove") }
                    }
                    Strength(s.rssi)
                }
            }
        } }
        item { SectionTitle("Phone as hotspot", "Leave the computer where it is, move the phone, and watch what the computer sees of the phone's hotspot. The tablet shows its own on its own Signal page.") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (!hotspot) "Off" else computer?.let { c -> if (c.has("rssi")) "Computer: ${c.optInt("rssi")} dBm · ${c.optInt("speed")} Mb/s · ${c.optString("ssid")}" else c.optString("error", "The computer has no Wi-Fi reading") } ?: "Asking the computer…",
                    Modifier.weight(1f))
                Switch(hotspot, { hotspot = it })
            }
        }
        item { SectionTitle("Around", "The networks near and their channels, to choose the router's least crowded one. Needs the location permission and Location on; Android allows 4 scans in 2 minutes.") }
        item {
            if (!DataSignal.located(context)) Text("Allow location in Data's Setup or Settings › Data.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val wm = context.applicationContext.getSystemService(WifiManager::class.java)
                        @Suppress("DEPRECATION") runCatching { wm.startScan() }
                        delay(4000)
                        around = runCatching { wm.scanResults }.getOrNull().orEmpty()
                    }
                }) { Text("Scan") }
                around?.let { AroundList(it, reading) }
            }
        }
        item { SectionTitle("Drops", "Lost or very weak signal, from Android's own callbacks, on every device, recorded while Tetra runs; hours it was not running are gaps, not \"no drops\". Today's arrive with the next hourly look.") }
        val drops = DataSignal.drops(all).take(50)
        if (drops.isEmpty()) item { Text("None recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        drops.forEach { (device, e) -> item { Text((if (device == DataFiles.device()) "" else "$device · ") + DataSignal.describe(e), style = MaterialTheme.typography.bodySmall) } }
    }
    if (saving) SaveSpot(reading, round, close = { saving = false; version++ })
}

@Composable private fun SectionTitle(title: String, note: String) = Column(Modifier.padding(top = 8.dp)) {
    Text(title, color = dataColor(), fontWeight = FontWeight.Bold)
    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun signalColor(rssi: Int) = when { rssi >= -67 -> Color(0xFF7CC417); rssi >= -75 -> Color(0xFFF2B705); else -> Color(0xFFE53935) }

@Composable private fun Strength(rssi: Int) {
    val share = ((rssi + 95).coerceIn(0, 65)) / 65f
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(signalColor(rssi)))
    }
}

@Composable private fun Meter(r: WifiReading?, history: List<Int>, sound: Boolean, setSound: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r == null) Text("Not on Wi-Fi", style = MaterialTheme.typography.headlineSmall)
            else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${r.rssi}", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = signalColor(r.rssi))
                    Text("dBm", Modifier.padding(bottom = 14.dp), style = MaterialTheme.typography.titleMedium)
                }
                Text(DataSignal.quality(r.rssi), style = MaterialTheme.typography.titleMedium)
                Strength(r.rssi)
                Text(listOfNotNull(r.ssid, "${r.speed} Mb/s", DataSignal.band(r.mhz), DataSignal.channel(r.mhz).takeIf { it > 0 }?.let { "channel $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (history.size > 1) {
                val line = dataColor()
                Canvas(Modifier.fillMaxWidth().height(60.dp)) {
                    fun y(v: Int) = size.height * (1 - ((v + 95).coerceIn(0, 65) / 65f))
                    val step = size.width / 119
                    history.zipWithNext().forEachIndexed { i, (a, b) -> drawLine(line, Offset(i * step, y(a)), Offset((i + 1) * step, y(b)), 3f) }
                }
                Text("The last 2 minutes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sound: beeps faster as the signal grows — walk with the phone in your pocket", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(sound, setSound)
            }
        }
    }
}

@Composable private fun AroundList(list: List<ScanResult>, mine: WifiReading?) {
    @Suppress("DEPRECATION") fun name(s: ScanResult) = s.SSID.ifBlank { "(hidden)" }
    @Suppress("DEPRECATION") val others = list.filter { mine?.ssid == null || it.SSID != mine.ssid } // your own network does not crowd itself
    val rated = DataSignal.crowding(others.map { DataSignal.channel(it.frequency) to it.level })
    val mineCh = mine?.let { DataSignal.channel(it.mhz) }?.takeIf { it > 0 }
    Text("${list.size} ${if (list.size == 1) "network" else "networks"} heard. " + rated.firstOrNull { it.first <= 14 }?.let { "Least crowded 2.4 GHz channel: ${it.first}." }.orEmpty() +
        rated.firstOrNull { it.first > 14 }?.let { " 5 GHz: ${it.first}." }.orEmpty() + (mineCh?.let { " Yours: $it." }.orEmpty()), style = MaterialTheme.typography.bodyMedium)
    list.sortedByDescending { it.level }.take(25).forEach { s ->
        Text("${s.level} dBm · ch ${DataSignal.channel(s.frequency)} · ${DataSignal.band(s.frequency)} · ${name(s)}" + if (s.is80211mcResponder) " · RTT" else "", style = MaterialTheme.typography.bodySmall)
    }
}

/** Saving a spot: ten seconds of readings averaged, and the speed to the computer if it answers. */
@Composable private fun SaveSpot(start: WifiReading?, round: String, close: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (busy == null) close() }, title = { Text("Save this spot") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Where (desk, bedroom, balcony…)") }, singleLine = true)
            Text(if (round.isBlank()) "Round: (no name)" else "Round: $round", style = MaterialTheme.typography.bodySmall)
            busy?.let { Text(it) }
        }
    }, confirmButton = {
        TextButton(enabled = name.isNotBlank() && busy == null, onClick = {
            scope.launch {
                val readings = mutableListOf<WifiReading>()
                repeat(10) { i -> busy = "Measuring… ${10 - i} s"; DataSignal.wifi(context)?.let(readings::add); delay(1000) }
                busy = "Speed to the computer…"
                val pc = withContext(Dispatchers.IO) { runCatching { SyncClient(context, SyncStore(context)).speedTest(8_000_000) }.getOrNull() }
                val r = readings.ifEmpty { listOfNotNull(start) }
                if (r.isNotEmpty()) withContext(Dispatchers.IO) {
                    DataSignal.saveSpot(context, DataSpot(ZonedDateTime.now().toOffsetDateTime().withNano(0).toString(), name.trim(), round.trim(),
                        r.map { it.rssi }.average().toInt(), r.map { it.speed }.average().toInt(), r.last().mhz, pc?.first ?: 0.0, pc?.second ?: 0.0, pc?.third ?: 0))
                }
                close()
            }
        }) { Text("Measure and save") }
    }, dismissButton = { TextButton(enabled = busy == null, onClick = close) { Text("Cancel") } })
}
