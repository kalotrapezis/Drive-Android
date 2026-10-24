package com.kalotrapezis.drive

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.view.KeyEvent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The Phone's colour: the icon's teal (Plan.md § Phone, question 6 — the plan's recommendation until the user says). */
internal val PHONE_COLOR = Color(0xFF168C93)
internal val CALL_GREEN = Color(0xFF2E9E5B)
internal val CALL_RED = Color(0xFFD93A3A)

/** A call's own screen, over the lock screen too: ringing, dialling, talking (Plan.md § Phone › Screens). */
class PhoneCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WriterLog.init(this)
        setShowWhenLocked(true); setTurnScreenOn(true)
        TetraLook.load(this)
        answerIfAsked(intent)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = TetraLook.light
        setContent { TetraTheme { PhoneCallScreen(done = { finish() }) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); answerIfAsked(intent) }
    override fun onResume() { super.onResume(); PhoneCalls.screenShown = true; PhoneCalls.changed(this) }
    override fun onPause() { PhoneCalls.screenShown = false; PhoneCalls.changed(this); super.onPause() }
    private fun answerIfAsked(i: Intent) { if (i.action == PhoneCalls.ANSWER) PhoneCalls.calls.firstOrNull { it.state == Call.STATE_RINGING }?.let(PhoneCalls::answer) }

    /** The volume keys quiet the ringing, as on every phone. */
    @Suppress("DEPRECATION")
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) && PhoneCalls.calls.any { it.state == Call.STATE_RINGING }) {
            runCatching { getSystemService(TelecomManager::class.java).silenceRinger() }; return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

/** Places a call (CALL_PHONE, which the Phone app role grants); false without the permission. */
internal fun placePhoneCall(c: Context, number: String): Boolean {
    if (c.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false
    return runCatching { c.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number, null), Bundle()); true }
        .onFailure { WriterLog.e("Phone: the call was not placed", it) }.getOrDefault(false)
}

@Suppress("DEPRECATION")
@Composable
private fun PhoneCallScreen(done: () -> Unit) {
    val context = LocalContext.current
    @Suppress("UNUSED_EXPRESSION") PhoneCalls.tick // read again on every change
    val live = PhoneCalls.top()
    var last by remember { mutableStateOf<Call?>(null) }
    if (live != null) last = live
    val call = live ?: last
    val ended = call == null || call.state == Call.STATE_DISCONNECTED || live == null
    if (ended) LaunchedEffect(Unit) { delay(1200); done() }
    if (call == null) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val contact = PhoneCalls.contact(call)
    var keys by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(callWash(PHONE_COLOR)), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.fillMaxHeight().widthIn(max = 460.dp).systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        if (contact != null) ContactAvatar(contact, 120.dp)
        else Box(Modifier.size(120.dp).clip(CircleShape).background(PHONE_COLOR), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = Color.White, modifier = Modifier.size(66.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(PhoneCalls.title(call), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val number = PhoneCalls.number(call)
        val label = contact?.phones?.firstOrNull { ContactsWays.key(it.value) == ContactsWays.key(number.orEmpty()) }?.label
        if (contact != null && number != null) Text(listOfNotNull(label?.ifBlank { null }, number).joinToString(" · "), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (number != null) PhoneArea.of(number)?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else if (call.details.hasProperty(Call.Details.PROPERTY_CONFERENCE)) Text("Conference · ${call.children.size} people", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (ended) "Call ended" else PhoneCalls.stateText(call, now), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        if (keys) Text(typed, style = MaterialTheme.typography.headlineSmall, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.weight(1f))
        when {
            ended -> Unit
            call.state == Call.STATE_SELECT_PHONE_ACCOUNT -> SimChoice(call)
            call.state == Call.STATE_RINGING -> Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundButton(R.drawable.ic_call_end, "Decline", CALL_RED, 76.dp) { PhoneCalls.hangUp(call) }
                RoundButton(R.drawable.ic_call, "Answer", CALL_GREEN, 76.dp) { PhoneCalls.answer(call) }
            }
            else -> {
                PhoneCalls.calls.filter { it != call && it.state != Call.STATE_DISCONNECTED && it.parent == null }.forEach { OtherCall(it, call) }
                if (keys) DialKeys(Modifier.padding(bottom = 16.dp), press = { ch -> call.playDtmfTone(ch); typed += ch }, release = { call.stopDtmfTone() })
                InCallControls(call, keys, toggleKeys = { keys = !keys }, addCall = {
                    context.startActivity(Intent(context, PhoneActivity::class.java).setAction(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
            }
        }
        if (!ended && call.state != Call.STATE_RINGING) RoundButton(R.drawable.ic_call_end, "End", CALL_RED, 76.dp, modifier = Modifier.padding(bottom = 24.dp)) { PhoneCalls.hangUp(call) }
    }
    }
}

/** A soft wash of the Phone's colour behind the call, darker below. */
private fun callWash(c: Color) = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.copy(alpha = 0.45f), Color.Transparent))

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InCallControls(call: Call, keys: Boolean, toggleKeys: () -> Unit, addCall: () -> Unit) {
    val context = LocalContext.current
    val audio = PhoneCalls.audio
    val muted = audio?.isMuted == true
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val mask = audio?.supportedRouteMask ?: 0
    val devices = audio?.supportedBluetoothDevices.orEmpty()
    var picking by remember { mutableStateOf(false) }
    var noting by remember { mutableStateOf(false) }
    val askNames = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { picking = true }
    val held = call.state == Call.STATE_HOLDING
    val canHold = call.details.can(Call.Details.CAPABILITY_HOLD) || call.details.can(Call.Details.CAPABILITY_SUPPORT_HOLD) || held
    val (routeIcon, routeName) = when (route) {
        CallAudioState.ROUTE_SPEAKER -> R.drawable.ic_volume_up to "Speaker"
        CallAudioState.ROUTE_BLUETOOTH -> R.drawable.ic_bluetooth_audio to (audio?.activeBluetoothDevice?.let { PhoneRoutes.name(context, it) } ?: "Bluetooth")
        CallAudioState.ROUTE_WIRED_HEADSET -> R.drawable.ic_volume_up to "Headset"
        else -> R.drawable.ic_volume_up to "Speaker"
    }
    val loud = route == CallAudioState.ROUTE_SPEAKER || route == CallAudioState.ROUTE_BLUETOOTH
    Column(Modifier.fillMaxWidth().padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundButton(if (muted) R.drawable.ic_mic_off else R.drawable.ic_mic, if (muted) "Muted" else "Mute", if (muted) Color.White else null, 64.dp, dark = muted) {
                PhoneCalls.service?.setMuted(!muted)
            }
            RoundButton(if (keys) R.drawable.ic_close else R.drawable.ic_dialpad, if (keys) "Hide keys" else "Keypad", null, 64.dp, click = toggleKeys)
            // more than earpiece and speaker (a headset, the buds, the computer): choose; else Speaker turns on and off
            RoundButton(routeIcon, if (devices.isNotEmpty() || mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) "$routeName ▾" else routeName, if (loud) Color.White else null, 64.dp, dark = loud) {
                if (devices.isEmpty()) PhoneCalls.service?.setAudioRoute(if (route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
                else if (android.os.Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) askNames.launch(Manifest.permission.BLUETOOTH_CONNECT)
                else picking = true
            }
        }
        if (!keys) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (canHold) RoundButton(if (held) R.drawable.ic_play else R.drawable.ic_pause, if (held) "Resume" else "Hold", if (held) Color.White else null, 64.dp, dark = held) {
                if (held) call.unhold() else call.hold()
            } else Spacer(Modifier.size(64.dp))
            RoundButton(R.drawable.ic_add, "Add call", null, 64.dp, click = addCall)
            RoundButton(R.drawable.ic_note_add, "Note", null, 64.dp) { noting = true }
        }
    }
    if (picking) ModalBottomSheet(onDismissRequest = { picking = false }, containerColor = SheetColor, contentColor = islandContentColor()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Sound", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            @Composable fun option(icon: Int, name: String, on: Boolean, pick: () -> Unit) =
                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(if (on) PHONE_COLOR.copy(alpha = 0.35f) else Color.Transparent)
                    .clickable { pick(); picking = false }.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(painterResource(icon), contentDescription = null); Text(name, style = MaterialTheme.typography.bodyLarge)
                }
            if (mask and CallAudioState.ROUTE_EARPIECE != 0) option(R.drawable.ic_call, "Phone", route == CallAudioState.ROUTE_EARPIECE) { PhoneCalls.service?.setAudioRoute(CallAudioState.ROUTE_EARPIECE) }
            if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) option(R.drawable.ic_volume_up, "Headset", route == CallAudioState.ROUTE_WIRED_HEADSET) { PhoneCalls.service?.setAudioRoute(CallAudioState.ROUTE_WIRED_HEADSET) }
            option(R.drawable.ic_volume_up, "Speaker", route == CallAudioState.ROUTE_SPEAKER) { PhoneCalls.service?.setAudioRoute(CallAudioState.ROUTE_SPEAKER) }
            devices.forEach { d ->
                option(R.drawable.ic_bluetooth_audio, PhoneRoutes.name(context, d), route == CallAudioState.ROUTE_BLUETOOTH && audio?.activeBluetoothDevice?.address == d.address) {
                    PhoneCalls.service?.requestBluetoothAudio(d)
                }
            }
        }
    }
    if (noting) CallNoteDialog(call) { noting = false }
}

/** A note typed during the call, kept with it (Tetra/Phone/notes.jsonl; Recents shows it under the call). */
@Composable
private fun CallNoteDialog(call: Call, done: () -> Unit) {
    val context = LocalContext.current
    val parts = PhoneCalls.parts(call)
    var text by remember { mutableStateOf(parts.firstNotNullOfOrNull { PhoneNotes.of(context, PhoneCalls.number(it).orEmpty(), it.details.creationTimeMillis) }.orEmpty()) }
    AlertDialog(onDismissRequest = done, title = { Text("Note on this call") },
        text = { OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { parts.forEach { p -> PhoneNotes.save(context, PhoneCalls.number(p).orEmpty(), p.details.creationTimeMillis, text.trim()) }; done() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = done) { Text("Cancel") } })
}

/** Another call beside the one shown: on hold, dialling, or waiting — Swap, Merge, End. */
@Composable
private fun OtherCall(o: Call, main: Call) {
    val contact = PhoneCalls.contact(o)
    Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CallAvatar(contact, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(PhoneCalls.title(o), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(PhoneCalls.stateText(o, 0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (o.state == Call.STATE_HOLDING) TextButton(onClick = { o.unhold() }) { Text("Swap") } // Telecom holds the other
            if (o in main.conferenceableCalls || (main.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) && o.state == Call.STATE_HOLDING))
                TextButton(onClick = { if (o in main.conferenceableCalls) main.conference(o) else main.mergeConference() }) { Text("Merge") }
            IconButton(onClick = { PhoneCalls.hangUp(o) }) { Icon(painterResource(R.drawable.ic_call_end), contentDescription = "End this call", tint = CALL_RED) }
        }
    }
}

/** The names of the sound's ways: a Bluetooth device that is Tetra's paired computer says so. */
internal object PhoneRoutes {
    fun name(c: Context, d: android.bluetooth.BluetoothDevice): String {
        val n = runCatching { d.name }.getOrNull()?.ifBlank { null } ?: return "Bluetooth"
        val computers = runCatching { SyncStore(c).peers().map { it.name.lowercase() } }.getOrDefault(emptyList())
        return if (n.lowercase() in computers) "Computer · $n" else n
    }
}

/** Two SIMs and "ask every time": Telecom waits for the Phone app to say which (call.phoneAccountSelected). */
@Composable
private fun SimChoice(call: Call) {
    val context = LocalContext.current
    val accounts = remember(call) {
        @Suppress("DEPRECATION") (call.details.extras?.getParcelableArrayList<PhoneAccountHandle>(Call.AVAILABLE_PHONE_ACCOUNTS)).orEmpty()
    }
    val telecom = context.getSystemService(TelecomManager::class.java)
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEachIndexed { k, h ->
            val name = runCatching { telecom.getPhoneAccount(h)?.label?.toString() }.getOrNull()?.ifBlank { null } ?: "SIM ${k + 1}"
            Button(onClick = { call.phoneAccountSelected(h, false) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Call with $name") }
        }
        if (accounts.isEmpty()) Text("The phone did not say which SIMs it has.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun RoundButton(icon: Int, label: String, fill: Color?, size: Dp, dark: Boolean = false, modifier: Modifier = Modifier, click: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size).clip(CircleShape).background(fill ?: LocalContentColor.current.copy(alpha = 0.12f)).clickable(onClickLabel = label, onClick = click),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = label, tint = if (dark) Color.Black else if (fill != null) Color.White else LocalContentColor.current, modifier = Modifier.size(size * 0.42f))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
    }
}

/** The keys' letters: the Latin ones and the Greek ones (Plan.md § Phone: T9 search in both, step 2). */
internal val KEY_LETTERS = mapOf('2' to "ABC ΑΒΓ", '3' to "DEF ΔΕΖ", '4' to "GHI ΗΘΙ", '5' to "JKL ΚΛΜ", '6' to "MNO ΝΞΟ", '7' to "PQRS ΠΡΣ", '8' to "TUV ΤΥΦ", '9' to "WXYZ ΧΨΩ", '0' to "+")

/** The 4×3 keys; [press] when a key goes down, [release] when it comes up (a call's tones last as long as the finger). */
@Composable
internal fun DialKeys(modifier: Modifier = Modifier, press: (Char) -> Unit, release: () -> Unit = {}, hold: ((Char) -> Unit)? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { ch ->
                    Column(Modifier.size(width = 88.dp, height = 64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .pointerInput(ch) { detectTapGestures(onPress = { press(ch); tryAwaitRelease(); release() }, onLongPress = hold?.let { h -> { h(ch) } }) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(ch.toString(), fontSize = 28.sp, fontWeight = FontWeight.Normal)
                        KEY_LETTERS[ch]?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    }
                }
            }
        }
    }
}

/** Settings › Phone. */
@Composable
internal fun PhoneSettings() {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) { // back from Android's settings: read the role again
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) version++ }
        lifecycle.lifecycle.addObserver(o); onDispose { lifecycle.lifecycle.removeObserver(o) }
    }
    val askRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { version++ }
    @Suppress("UNUSED_EXPRESSION") version
    val dialer = PhonePrefs.isDialer(context)
    var answer by remember { mutableStateOf(PhonePrefs.buzzAnswer(context)) }
    var end by remember { mutableStateOf(PhonePrefs.buzzEnd(context)) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (dialer) "Tetra is the phone app" else "Tetra is not the phone app", style = MaterialTheme.typography.titleSmall)
            Text(if (dialer) "Calls ring and are spoken on Tetra's own call screen. To give it back: Android's Default apps › Phone app."
                else "As the phone app, Tetra shows its own call screen and buzzes when they answer. The phone's own call recording is not shown then.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = {
            if (dialer) runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            else askRole.launch(context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER))
        }) { Text(if (dialer) "Change" else "Make it") }
    }
    @Composable fun toggle(title: String, about: String, on: Boolean, set: (Boolean) -> Unit) =
        Row(Modifier.fillMaxWidth().clickable { set(!on) }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = on, onCheckedChange = set)
        }
    toggle("Vibrate when answered", "Two short taps when the other side picks up a call you made.", answer) { answer = it; PhonePrefs.setBuzzAnswer(context, it) }
    toggle("Vibrate when a call ends", "One tap when a call that was answered ends.", end) { end = it; PhonePrefs.setBuzzEnd(context, it) }
    TextButton(onClick = { context.startActivity(Intent(context, PhoneActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open the keypad") }
}
