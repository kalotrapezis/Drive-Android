package com.kalotrapezis.drive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** The assistant's chat (Home › Assistant): type or speak, answers as bubbles, read aloud when the speaker is on. */
open class AssistantActivity : ComponentActivity() {
    protected open val sheet = false
    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLocale.wrap(base))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WriterLog.init(this)
        TetraLook.load(this)
        if (!sheet) androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = TetraLook.light
        setContent { TetraTheme { AssistantApp(sheet) { finish() } } }
    }
}

/** The small sheet over any app (the phone's assistant gesture, or its own icon): listens at once, answers, and goes. */
class AssistantSheetActivity : AssistantActivity() { override val sheet = true }

internal val ASSISTANT_COLOR = Color(0xFFFD6A04)

internal fun openAssistant(c: Context) = c.startActivity(Intent(c, AssistantActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

/** The voice sheet's conversation lives on between openings, and is forgotten after a minute without a word. */
private object SheetMemory { var chat: AssistantChat? = null }

private data class Bubble(val mine: Boolean, val text: String, val choices: List<AssistantChoice> = emptyList(), val call: PendingCall? = null,
    val images: List<File> = emptyList(), val error: Boolean = false)

private enum class Phase { Idle, Listening, Thinking }

/** The live screen's record: what it is doing decides how it turns. */
private enum class LivePhase(val degreesPerSecond: Float) { Connecting(20f), Listening(40f), Thinking(90f), Speaking(200f), Stopping(0f) }

@Composable
private fun AssistantApp(sheet: Boolean, close: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chat = remember { if (sheet) SheetMemory.chat ?: assistantChat(context, 60_000).also { SheetMemory.chat = it } else assistantChat(context) }
    val bubbles = remember { mutableStateListOf<Bubble>() }
    var phase by remember { mutableStateOf(Phase.Idle) }
    var hearing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingCall?>(null) }
    var hasKey by remember { mutableStateOf(AssistantKey.get(context) != null) }
    val prefs = remember { context.getSharedPreferences("assistant", Context.MODE_PRIVATE) }
    var aloud by remember { mutableStateOf(sheet || prefs.getBoolean("aloud", false)) }
    val reader = rememberReadAloud()
    val stopNow = remember { AtomicBoolean(false) }
    var hear: (Long) -> Unit = {}
    var spent by remember { mutableDoubleStateOf(chat.bill.usd) }
    // live (the waveform button): one at a time, its record and captions over the chat
    var live by remember { mutableStateOf<AssistantLive?>(null) }
    var liveShown by remember { mutableStateOf(false) }
    var livePhase by remember { mutableStateOf(LivePhase.Connecting) }
    var liveYou by remember { mutableStateOf("") }
    var liveAi by remember { mutableStateOf("") }
    var liveLevel by remember { mutableFloatStateOf(0f) }
    var liveStart by remember { mutableLongStateOf(0L) }
    var liveLast by remember { mutableLongStateOf(0L) }
    var liveNow by remember { mutableLongStateOf(0L) }
    fun endLive(@androidx.annotation.StringRes why: Int? = null) {
        val l = live ?: return
        live = null; l.end(); reader.stop()
        chat.bill.minutes(Mistral.REALTIME, (System.currentTimeMillis() - liveStart) / 1000.0); spent = chat.bill.usd
        why?.let { bubbles += Bubble(false, context.getString(it)) }
        livePhase = LivePhase.Stopping
        scope.launch { delay(1_500); if (live == null) liveShown = false } // the record slows to a stop first
    }
    DisposableEffect(Unit) { onDispose { live?.end() } }
    BackHandler { if (live != null) endLive() else close() }

    fun say(text: String, error: Boolean = false) { bubbles += Bubble(false, text, error = error) }
    fun open(f: File) = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase(Locale.ROOT)) ?: "application/octet-stream"
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { WriterLog.e("Assistant: could not open ${f.name}", it); say(context.getString(R.string.assistant_cannot_open), true) }
    fun call(p: PendingCall) {
        pending = null
        endLive()
        WriterLog.i("Assistant: calling at the user's yes")
        if (!placePhoneCall(context, p.number)) context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", p.number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (sheet) close()
    }
    fun choose(c: AssistantChoice) { c.call?.let(::call); c.file?.let { open(it); if (sheet) close() } }

    fun send(text: String) = scope.launch {
        if (text.isBlank()) return@launch
        bubbles += Bubble(true, text)
        val inLive = live != null
        if (inLive) { liveYou = text; liveAi = "" }
        // a yes or no to a waiting call is heard by the phone, never decided by the model
        pending?.let { p -> AssistantChat.yesNo(text)?.let { yes -> if (yes) call(p) else { pending = null; say(context.getString(R.string.assistant_call_cancelled)) }; return@launch } }
        phase = Phase.Thinking
        live?.muted = true; if (inLive) livePhase = LivePhase.Thinking
        val r = withContext(Dispatchers.IO) { runCatching { chat.ask(text) } }
        phase = Phase.Idle; spent = chat.bill.usd
        fun listenAgain() { live?.let { it.muted = false; livePhase = LivePhase.Listening; liveLast = System.currentTimeMillis() } }
        val reply = r.getOrElse { say(it.shownMessage(context) ?: context.getString(R.string.assistant_server, 0), true); WriterLog.w("Assistant: ${it.message}"); listenAgain(); return@launch }
        bubbles += Bubble(false, reply.text, reply.choices, reply.call, reply.images)
        pending = reply.call
        reply.open?.let(::open)
        if (inLive) liveAi = reply.text
        val speaking = (aloud || inLive) && reply.text.isNotBlank() && reader.read(listOf(reply.text), 0)
        if (inLive) {
            if (speaking) { livePhase = LivePhase.Speaking; snapshotFlow { reader.speaking }.first { !it } }
            listenAgain(); return@launch
        }
        if (!sheet) return@launch
        // the sheet: a question or a list waits for the answer by voice (a few seconds); anything else is done
        snapshotFlow { reader.speaking }.first { !it }
        when {
            reply.open != null -> close()
            reply.text.trimEnd().endsWith('?') || reply.choices.isNotEmpty() || reply.call != null -> hear(6_000)
            reply.images.isEmpty() -> { delay(1_500); close() }
        }
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) hear(10_000) else say(context.getString(R.string.assistant_mic_needed), true) }
    hear = { waitMs -> scope.launch {
        if (!hasKey) return@launch
        if (!hasMic(context)) { askMic.launch(Manifest.permission.RECORD_AUDIO); return@launch }
        reader.stop(); stopNow.set(false); phase = Phase.Listening
        val pcm = withContext(Dispatchers.IO) { runCatching { listen(waitMs, { stopNow.get() || !isActive }) { hearing = it } }.getOrNull() }
        hearing = false
        if (pcm == null) { phase = Phase.Idle; if (sheet && pending == null && bubbles.lastOrNull()?.images.isNullOrEmpty()) close(); return@launch }
        phase = Phase.Thinking
        val text = withContext(Dispatchers.IO) { runCatching { AssistantNet.transcribe(AssistantNet.key(context), pcm, AssistantWords.terms(context), chat.bill) } }
        phase = Phase.Idle; spent = chat.bill.usd
        text.onSuccess { if (it.isBlank()) say(context.getString(R.string.assistant_heard_nothing)) else send(it) }
            .onFailure { say(it.shownMessage(context) ?: "", true) }
    } }
    fun mic() = when (phase) { Phase.Idle -> hear(10_000); Phase.Listening -> stopNow.set(true); Phase.Thinking -> Unit }
    LaunchedEffect(Unit) { if (sheet && hasKey) hear(8_000) }

    fun startLive() = scope.launch {
        if (!hasKey || live != null || phase != Phase.Idle) return@launch
        if (!hasMic(context)) { askMic.launch(Manifest.permission.RECORD_AUDIO); return@launch }
        reader.stop()
        liveYou = ""; liveAi = ""; liveLevel = 0f; livePhase = LivePhase.Connecting; liveShown = true
        liveStart = System.currentTimeMillis(); liveLast = liveStart; liveNow = liveStart
        val terms = withContext(Dispatchers.IO) { AssistantWords.terms(context) }
        live = AssistantLive(AssistantNet.key(context), terms,
            heard = { t -> scope.launch { liveYou = t; liveAi = ""; liveLast = System.currentTimeMillis(); if (livePhase == LivePhase.Connecting) livePhase = LivePhase.Listening } },
            turn = { t -> scope.launch { send(t) } },
            level = { liveLevel = it },
            failed = { e -> scope.launch { say(e.shownMessage(context) ?: "", true); endLive() } },
        ).also { it.start(); livePhase = LivePhase.Listening }
        WriterLog.i("AI Assist: live on")
    }
    // the bill guard (live is paid by the minute): 20 s with nobody talking, or 5 minutes, and it ends by itself
    LaunchedEffect(liveShown) {
        while (liveShown) {
            liveNow = System.currentTimeMillis()
            if (live != null && liveNow - liveStart >= LIVE_MAX_MS) endLive(R.string.assistant_live_cap)
            else if (live != null && livePhase == LivePhase.Listening && liveNow - liveLast >= LIVE_SILENCE_MS) endLive(R.string.assistant_live_silence)
            delay(250)
        }
    }

    val status = when (phase) {
        Phase.Listening -> stringResource(if (hearing) R.string.assistant_hearing else R.string.assistant_listening)
        Phase.Thinking -> stringResource(R.string.assistant_thinking)
        Phase.Idle -> null
    }
    val list: @Composable (Modifier) -> Unit = { m ->
        val state = rememberLazyListState()
        LaunchedEffect(bubbles.size) { if (bubbles.isNotEmpty()) state.animateScrollToItem(bubbles.size - 1) }
        LazyColumn(m, state = state, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!hasKey) item { AssistantKeyField { hasKey = true; if (sheet) hear(8_000) } }
            itemsIndexed(bubbles) { i, b -> BubbleView(b, pending.takeIf { i == bubbles.size - 1 }, ::choose, ::call, { pending = null; say(context.getString(R.string.assistant_call_cancelled)) }, ::open) }
        }
    }

    if (sheet) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(remember { MutableInteractionSource() }, null) { close() }, contentAlignment = Alignment.BottomCenter) {
            Surface(Modifier.fillMaxWidth().widthIn(max = 640.dp).clickable(remember { MutableInteractionSource() }, null) {}, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), tonalElevation = 6.dp) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(painterResource(R.drawable.assistant_art), contentDescription = null, Modifier.size(28.dp))
                        Text(stringResource(R.string.app_assistant), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (spent > 0) Text(Bill.text(spent), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    list(Modifier.fillMaxWidth().heightIn(max = 420.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MicButton(phase, hearing, ::mic)
                        Text(status ?: stringResource(R.string.assistant_tap_to_talk), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = close) { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.common_close)) }
                    }
                }
            }
        }
        return
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.common_back)) }
                Image(painterResource(R.drawable.assistant_art), contentDescription = null, Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.app_assistant), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // the thread's running total (the user: "the bill came")
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(Bill.text(spent), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                IconButton(onClick = { aloud = !aloud; prefs.edit().putBoolean("aloud", aloud).apply(); if (!aloud) reader.stop() }) {
                    Icon(painterResource(if (aloud) R.drawable.ic_volume_up else R.drawable.ic_volume_off), contentDescription = stringResource(R.string.assistant_read_aloud))
                }
                IconButton(onClick = { chat.clear(); bubbles.clear(); pending = null; reader.stop(); spent = 0.0 }) { Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.assistant_new_chat)) }
            }
            list(Modifier.weight(1f).fillMaxWidth())
            status?.let { Text(it, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            var typed by remember { mutableStateOf("") }
            fun sendTyped() { val t = typed.trim(); typed = ""; send(t) }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(typed, { typed = it }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.assistant_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    shape = RoundedCornerShape(24.dp), maxLines = 5, enabled = hasKey,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { sendTyped() }))
                if (typed.isNotBlank()) FilledIconButton(onClick = ::sendTyped, enabled = phase == Phase.Idle) { Icon(painterResource(R.drawable.ic_arrow_upward), contentDescription = stringResource(R.string.common_send)) }
                else {
                    MicButton(phase, hearing, ::mic)
                    FilledTonalIconButton(onClick = { startLive() }, enabled = hasKey && phase == Phase.Idle, modifier = Modifier.size(52.dp)) {
                        Icon(painterResource(R.drawable.ic_graphic_eq), contentDescription = stringResource(R.string.assistant_live))
                    }
                }
            }
        }
        if (liveShown) LiveScreen(livePhase, liveLevel, liveYou, liveAi,
            Bill.text(spent + Bill.perMinute(Mistral.REALTIME) * (liveNow - liveStart).coerceAtLeast(0) / 60_000.0), clockOf(liveNow - liveStart),
            pending, bubbles.lastOrNull()?.choices.orEmpty(), ::choose, ::call, { pending = null; say(context.getString(R.string.assistant_call_cancelled)) }, { endLive() })
    }
}

private const val LIVE_MAX_MS = 5 * 60_000L
private const val LIVE_SILENCE_MS = 20_000L

private fun clockOf(ms: Long) = (ms.coerceAtLeast(0) / 1000).let { "%d:%02d:%02d".format(Locale.ROOT, it / 3600, it / 60 % 60, it % 60) }

/**
 * Live, as the user drew it: a record that turns with the talk (steady while the AI speaks, slowly with its grooves glowing
 * to the voice while it listens, a slow half-turn while it thinks, slowing to a stop at X), the last exchange as captions,
 * and X beside what it has cost and how long it has been on.
 */
@Composable
private fun LiveScreen(phase: LivePhase, level: Float, you: String, ai: String, cost: String, clock: String, pending: PendingCall?,
    choices: List<AssistantChoice>, choose: (AssistantChoice) -> Unit, call: (PendingCall) -> Unit, cancel: () -> Unit, end: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.app_assistant), style = MaterialTheme.typography.titleLarge)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Vinyl(phase, level, Modifier.fillMaxWidth(0.82f).widthIn(max = 420.dp)) }
            Surface(Modifier.fillMaxWidth().widthIn(max = 560.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(18.dp).heightIn(min = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(caption(stringResource(R.string.assistant_you), you.ifBlank { if (phase == LivePhase.Connecting) stringResource(R.string.assistant_live_connecting) else "…" }),
                        style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    if (ai.isNotBlank() || phase == LivePhase.Thinking) Text(caption(stringResource(R.string.assistant_ai), ai.ifBlank { stringResource(R.string.assistant_thinking) }),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    choices.take(5).forEachIndexed { i, c -> Text("${i + 1}. ${c.label}", Modifier.fillMaxWidth().clickable { choose(c) }.padding(vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (pending != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { call(pending) }) { Text(stringResource(R.string.assistant_call, pending.name), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        TextButton(onClick = cancel) { Text(stringResource(R.string.common_cancel), maxLines = 1) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().widthIn(max = 560.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledIconButton(onClick = end, enabled = phase != LivePhase.Stopping, modifier = Modifier.size(64.dp)) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.assistant_live_end), Modifier.size(28.dp))
                }
                Surface(Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(contentAlignment = Alignment.Center) { Text("$cost / $clock", style = MaterialTheme.typography.titleLarge, maxLines = 1) }
                }
            }
        }
    }
}

private fun caption(who: String, said: String) = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)); append("$who: "); pop(); append(said)
}

@Composable
private fun Vinyl(phase: LivePhase, level: Float, modifier: Modifier) {
    val label = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.assistant_art)
    val target by rememberUpdatedState(phase.degreesPerSecond)
    val glow by rememberUpdatedState(if (phase == LivePhase.Listening) level else 0f)
    var angle by remember { mutableFloatStateOf(0f) }
    var pulse by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var speed = 0f; var last = 0L
        while (true) withFrameNanos { t ->
            if (last != 0L) {
                val dt = (t - last) / 1e9f
                speed += (target - speed) * (dt * 1.5f).coerceAtMost(1f) // eases up and down: X slows it to a stop
                angle = (angle + speed * dt) % 360f
                pulse += (glow - pulse) * (dt * 10f).coerceAtMost(1f)
            }
            last = t
        }
    }
    androidx.compose.foundation.Canvas(modifier.aspectRatio(1f)) {
        val r = size.minDimension / 2
        drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF2C2C31), Color(0xFF0E0E10)), center, r), r)
        val groove = Color(0xFFFFB74D)
        for (i in 0 until 26) {
            val wave = 0.5f + 0.5f * kotlin.math.sin(i * 0.9f + angle / 25f)
            drawCircle(Color.White.copy(alpha = 0.05f).compositeOver(groove.copy(alpha = (pulse * 0.6f * wave).coerceIn(0f, 0.6f))),
                radius = r * (0.40f + i * 0.022f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
        }
        rotate(angle) {
            drawArc(Color.White.copy(alpha = 0.07f), -100f, 30f, useCenter = true) // a sheen, so the turning shows
            drawArc(Color.White.copy(alpha = 0.07f), 80f, 30f, useCenter = true)
            val lr = r * 0.36f
            clipPath(androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, lr)) }) {
                // the icon's middle (its robot) fills the label
                drawImage(label, srcOffset = androidx.compose.ui.unit.IntOffset((label.width * 0.12f).toInt(), (label.height * 0.12f).toInt()),
                    srcSize = androidx.compose.ui.unit.IntSize((label.width * 0.76f).toInt(), (label.height * 0.76f).toInt()),
                    dstOffset = androidx.compose.ui.unit.IntOffset((center.x - lr).toInt(), (center.y - lr).toInt()), dstSize = androidx.compose.ui.unit.IntSize((lr * 2).toInt(), (lr * 2).toInt()))
            }
            drawCircle(Color(0xFF0E0E10), radius = r * 0.025f) // the spindle hole
        }
    }
}

@Composable
private fun MicButton(phase: Phase, hearing: Boolean, tap: () -> Unit) {
    val on = phase == Phase.Listening
    FilledIconButton(onClick = tap, enabled = phase != Phase.Thinking, modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (on) (if (hearing) Color(0xFFD32F2F) else Color(0xFFE57373)) else MaterialTheme.colorScheme.primary)) {
        if (phase == Phase.Thinking) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else Icon(painterResource(R.drawable.ic_mic), contentDescription = stringResource(if (on) R.string.common_stop else R.string.assistant_tap_to_talk))
    }
}

@Composable
private fun BubbleView(b: Bubble, pending: PendingCall?, choose: (AssistantChoice) -> Unit, call: (PendingCall) -> Unit, cancel: () -> Unit, open: (File) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (b.mine) Alignment.End else Alignment.Start) {
        if (b.text.isNotBlank()) Surface(shape = RoundedCornerShape(20.dp), color = when {
            b.error -> MaterialTheme.colorScheme.errorContainer
            b.mine -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }, modifier = Modifier.widthIn(max = 560.dp)) { Text(b.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge) }
        if (b.choices.isNotEmpty()) Column(Modifier.padding(top = 6.dp).widthIn(max = 560.dp).clip(RoundedCornerShape(16.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            b.choices.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).clickable { choose(c) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(c.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (c.call != null) Icon(painterResource(R.drawable.ic_call), contentDescription = null, Modifier.size(20.dp))
                }
            }
        }
        b.images.forEach { f ->
            val bmp = remember(f) { runCatching { BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() }.getOrNull() }
            bmp?.let { Image(it, contentDescription = f.name, Modifier.padding(top = 6.dp).widthIn(max = 360.dp).clip(RoundedCornerShape(16.dp)).clickable { open(f) }, contentScale = ContentScale.FillWidth) }
        }
        if (pending != null && b.call == pending) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { call(pending) }) {
                Icon(painterResource(R.drawable.ic_call), contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.assistant_call, pending.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = cancel) { Text(stringResource(R.string.common_cancel), maxLines = 1) }
        }
    }
}

/** The Mistral key: here when it is missing, and in Settings › Assistant. */
@Composable
internal fun AssistantKeyField(saved: () -> Unit = {}) {
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    var has by remember { mutableStateOf(AssistantKey.get(context) != null) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.assistant_key), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (has) R.string.assistant_key_saved else R.string.assistant_key_missing), style = MaterialTheme.typography.bodySmall,
            color = if (has) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(key, { key = it }, Modifier.weight(1f), singleLine = true, visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text(stringResource(R.string.assistant_key_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) })
            Button(onClick = { runCatching { AssistantKey.set(context, key) }.onFailure { WriterLog.e("Assistant: the key was not saved", it) }
                key = ""; has = AssistantKey.get(context) != null; if (has) saved() }, enabled = key.isNotBlank()) { Text(stringResource(R.string.common_save), maxLines = 1) }
        }
        Text(stringResource(R.string.assistant_key_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}
