package com.kalotrapezis.drive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TimePickerSelectionMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background

internal val TIMER_COLOR = Color(0xFF26A69A)

/**
 * The Timer (Plan.md § Timer), as the user drew it (18 Oct): the Calendar's clock (hours on the outer and inner ring, then
 * minutes), Pause and Stop under it, the timers' cards beside it on a big screen, above it on a phone; no island. "Play is
 * not needed as soon as I set a timer starts": the finger lifted from the minutes starts it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimerScreen(back: () -> Unit, hasAccess: Boolean, grant: () -> Unit) {
    val context = LocalContext.current
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    val scope = rememberCoroutineScope()
    var lastId by remember { mutableStateOf<String?>(null) } // Pause and Stop under the clock: the timer last started here
    BackHandler { back() }

    if (!hasAccess) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
            Text("Timers are small files in Tetra's folder, so every device you sync with starts, pauses and stops with this one. Allow access to all files to use the Timer.")
            TextButton(onClick = grant) { Text("Allow access") }
        }
        return
    }
    // each device counts by itself: the files read twice a second, the time left worked out from them
    var now by remember { mutableStateOf(Instant.now()) }
    var timers by remember { mutableStateOf(emptyList<TimerRecord>()) }
    var read by remember { mutableStateOf(false) }
    OpenTiming("Timer", read)
    LaunchedEffect(Unit) {
        TimerService.check(context)
        // sync's old copies of timers, cleared each time the Timer opens (TimerFiles.clean)
        withContext(Dispatchers.IO) { runCatching { TimerFiles.clean(TimerControl.dir(), TimerControl.device()) }
            .onSuccess { if (it > 0) WriterLog.i("Timer: cleared $it old copies") }.onFailure { WriterLog.e("Timer: clean", it) } }
        while (true) {
            timers = withContext(Dispatchers.IO) { runCatching { TimerFiles.cards(TimerControl.dir()) }.getOrDefault(emptyList()) }
            read = true
            now = Instant.now()
            delay(500)
        }
    }
    val last = remember { TimerControl.last(context) }
    val clock = rememberTimePickerState((last / 3600).toInt().coerceAtMost(23), (last % 3600 / 60).toInt(), is24Hour = true)
    val chosen = clock.hour * 3600L + clock.minute * 60L
    fun start(seconds: Long) = scope.launch {
        if (seconds <= 0) return@launch
        val done = withContext(Dispatchers.IO) { runCatching { TimerControl.start(context, seconds) }.onFailure { WriterLog.e("Timer: could not start", it) } }
        done.getOrNull()?.let { lastId = it.id }
        done.exceptionOrNull()?.let { android.widget.Toast.makeText(context, it.message ?: "The timer could not start", android.widget.Toast.LENGTH_SHORT).show() }
    }
    fun change(r: TimerRecord, action: String) = scope.launch(Dispatchers.IO) { runCatching { TimerControl.change(context, r.id, action) }.onFailure { WriterLog.e("Timer: $action failed", it) } }

    // the cards, three to a row on a big screen, two on a phone (the user, 18 Oct: "The UI will fit 9 timers")
    val cards: @Composable () -> Unit = {
        val per = if (wide) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (timers.isEmpty() && wide) Text("Set hours, then minutes, on the clock: the timer starts when you lift your finger from the minutes. Pin a timer to keep it here, ready to start again.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            timers.chunked(per).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { r -> Box(Modifier.weight(1f)) { TimerCard(r, now, ::change) } }
                    repeat(per - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    val live = timers.filter { !it.over(now) && !it.ringing(now) }
    val current = live.firstOrNull { it.id == lastId } ?: live.firstOrNull() // else the one ending first
    val picker: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            // a finger lifted from the minutes starts the timer (watched, not taken: the picker works as ever)
            Box(Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val minutes = clock.selection == TimePickerSelectionMode.Minute
                    do { val e = awaitPointerEvent(PointerEventPass.Initial) } while (e.changes.any { it.pressed })
                    if (minutes) scope.launch { delay(150); start(clock.hour * 3600L + clock.minute * 60L); clock.selection = TimePickerSelectionMode.Hour }
                }
            }) { TimePicker(clock) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (current?.state == TimerRecord.PAUSED) Button(onClick = { change(current, TimerService.RESUME) }, modifier = Modifier.widthIn(min = 130.dp)) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null); Text("  Resume")
                } else Button(onClick = { current?.let { change(it, TimerService.PAUSE) } }, enabled = current != null, modifier = Modifier.widthIn(min = 130.dp)) {
                    Icon(painterResource(R.drawable.ic_pause), contentDescription = null); Text("  Pause")
                }
                OutlinedButton(onClick = { current?.let { change(it, TimerService.STOP) } }, enabled = current != null, modifier = Modifier.widthIn(min = 130.dp)) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null); Text("  Stop")
                }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (wide) Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 80.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { picker() }
                Box(Modifier.width(2.dp).fillMaxHeight(0.6f).background(MaterialTheme.colorScheme.surfaceVariant)) // the sketch's line
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { cards() }
            } else Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                cards(); picker()
                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
            FilesPageHeader(title = "Timer", icon = R.drawable.ic_timer, back = back, island = true,
                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp))
        }
    }
}

/**
 * A timer's card: the time left, its buttons under it; the pin at the top left keeps the card when it is stopped or done
 * (ready to start again), the trash at the top right takes it away — on every device.
 */
@Composable private fun TimerCard(r: TimerRecord, now: Instant, change: (TimerRecord, String) -> Unit) {
    val ringing = r.ringing(now)
    val ready = r.over(now) // a pinned card waiting to start again
    Surface(shape = MaterialTheme.shapes.extraLarge, color = if (ringing) TIMER_COLOR else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Box {
            IconButton(onClick = { change(r, if (r.pinned) TimerService.UNPIN else TimerService.PIN) }, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(painterResource(R.drawable.ic_push_pin), contentDescription = if (r.pinned) "Unpin" else "Pin",
                    tint = if (r.pinned) (if (ringing) Color.White else TIMER_COLOR) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
            IconButton(onClick = { change(r, TimerService.TRASH) }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(when { ringing -> "Time's up"; ready -> clockText(r.seconds * 1000); else -> clockText(r.leftMs(now)) },
                    style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace, maxLines = 1)
                Text(r.label.ifEmpty { durationText(r.seconds) } + if (r.state == TimerRecord.PAUSED) " · paused" else "",
                    style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        ringing -> Button(onClick = { change(r, TimerService.OK) }, modifier = Modifier.widthIn(min = 120.dp)) { Text("OK") }
                        ready -> FilledTonalButton(onClick = { change(r, TimerService.RESTART) }) { Icon(painterResource(R.drawable.ic_play), contentDescription = null); Text(" Start") }
                        r.state == TimerRecord.PAUSED -> IconButton(onClick = { change(r, TimerService.RESUME) }) { Icon(painterResource(R.drawable.ic_play), contentDescription = "Resume") }
                        else -> IconButton(onClick = { change(r, TimerService.PAUSE) }) { Icon(painterResource(R.drawable.ic_pause), contentDescription = "Pause") }
                    }
                    if (!ringing && !ready) IconButton(onClick = { change(r, TimerService.STOP) }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Stop") }
                }
            }
        }
    }
}
