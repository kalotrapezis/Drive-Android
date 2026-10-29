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

internal val ASSISTANT_COLOR = Color(0xFF7857F1)

internal fun openAssistant(c: Context) = c.startActivity(Intent(c, AssistantActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

/** The voice sheet's conversation lives on between openings, and is forgotten after a minute without a word. */
private object SheetMemory { var chat: AssistantChat? = null }

private data class Bubble(val mine: Boolean, val text: String, val choices: List<AssistantChoice> = emptyList(), val call: PendingCall? = null,
    val images: List<File> = emptyList(), val error: Boolean = false)

private enum class Phase { Idle, Listening, Thinking }

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
    BackHandler { close() }

    fun say(text: String, error: Boolean = false) { bubbles += Bubble(false, text, error = error) }
    fun open(f: File) = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase(Locale.ROOT)) ?: "application/octet-stream"
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { WriterLog.e("Assistant: could not open ${f.name}", it); say(context.getString(R.string.assistant_cannot_open), true) }
    fun call(p: PendingCall) {
        pending = null
        WriterLog.i("Assistant: calling at the user's yes")
        if (!placePhoneCall(context, p.number)) context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", p.number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (sheet) close()
    }
    fun choose(c: AssistantChoice) { c.call?.let(::call); c.file?.let { open(it); if (sheet) close() } }

    fun send(text: String) = scope.launch {
        if (text.isBlank()) return@launch
        bubbles += Bubble(true, text)
        // a yes or no to a waiting call is heard by the phone, never decided by the model
        pending?.let { p -> AssistantChat.yesNo(text)?.let { yes -> if (yes) call(p) else { pending = null; say(context.getString(R.string.assistant_call_cancelled)) }; return@launch } }
        phase = Phase.Thinking
        val r = withContext(Dispatchers.IO) { runCatching { chat.ask(text) } }
        phase = Phase.Idle
        val reply = r.getOrElse { say(it.shownMessage(context) ?: context.getString(R.string.assistant_server, 0), true); WriterLog.w("Assistant: ${it.message}"); return@launch }
        bubbles += Bubble(false, reply.text, reply.choices, reply.call, reply.images)
        pending = reply.call
        reply.open?.let(::open)
        if (aloud && reply.text.isNotBlank()) reader.read(listOf(reply.text), 0)
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
        val text = withContext(Dispatchers.IO) { runCatching { AssistantNet.transcribe(AssistantNet.key(context), pcm) } }
        phase = Phase.Idle
        text.onSuccess { if (it.isBlank()) say(context.getString(R.string.assistant_heard_nothing)) else send(it) }
            .onFailure { say(it.shownMessage(context) ?: "", true) }
    } }
    fun mic() = when (phase) { Phase.Idle -> hear(10_000); Phase.Listening -> stopNow.set(true); Phase.Thinking -> Unit }
    LaunchedEffect(Unit) { if (sheet && hasKey) hear(8_000) }

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
                Text(stringResource(R.string.app_assistant), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { aloud = !aloud; prefs.edit().putBoolean("aloud", aloud).apply(); if (!aloud) reader.stop() }) {
                    Icon(painterResource(if (aloud) R.drawable.ic_volume_up else R.drawable.ic_volume_off), contentDescription = stringResource(R.string.assistant_read_aloud))
                }
                IconButton(onClick = { chat.clear(); bubbles.clear(); pending = null; reader.stop() }) { Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.assistant_new_chat)) }
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
                else MicButton(phase, hearing, ::mic)
            }
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
