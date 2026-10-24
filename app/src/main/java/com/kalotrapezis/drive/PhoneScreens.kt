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
private val GREEN = Color(0xFF2E9E5B)
private val RED = Color(0xFFD93A3A)

/** A call's own screen, over the lock screen too: ringing, dialling, talking (Plan.md § Phone › Screens). */
class PhoneCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WriterLog.init(this)
        setShowWhenLocked(true); setTurnScreenOn(true)
        TetraLook.load(this)
        answerIfAsked(intent)
        setContent { TetraTheme { PhoneCallScreen(done = { finish() }) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); answerIfAsked(intent) }
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

/** The keypad: what Android opens for a phone number (ACTION_DIAL, tel: links) — the Phone app's first part. */
class PhoneDialActivity : ComponentActivity() {
    private val number = mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WriterLog.init(this)
        TetraLook.load(this)
        number.value = intent.data?.schemeSpecificPart.orEmpty()
        setContent { TetraTheme { PhoneKeypad(number, back = { finish() }) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); intent.data?.schemeSpecificPart?.let { number.value = it } }
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
    Column(Modifier.fillMaxSize().background(callWash(PHONE_COLOR)).systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
        Text(if (ended) "Call ended" else PhoneCalls.stateText(call, now), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        if (keys) Text(typed, style = MaterialTheme.typography.headlineSmall, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.weight(1f))
        when {
            ended -> Unit
            call.state == Call.STATE_SELECT_PHONE_ACCOUNT -> SimChoice(call)
            call.state == Call.STATE_RINGING -> Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundButton(R.drawable.ic_call_end, "Decline", RED, 76.dp) { PhoneCalls.hangUp(call) }
                RoundButton(R.drawable.ic_call, "Answer", GREEN, 76.dp) { PhoneCalls.answer(call) }
            }
            keys -> DialKeys(Modifier.padding(bottom = 16.dp), press = { ch -> call.playDtmfTone(ch); typed += ch }, release = { call.stopDtmfTone() })
            else -> InCallControls(context)
        }
        if (!ended && call.state != Call.STATE_RINGING) Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(56.dp))
            RoundButton(R.drawable.ic_call_end, "End", RED, 76.dp) { PhoneCalls.hangUp(call) }
            if (call.state != Call.STATE_SELECT_PHONE_ACCOUNT) RoundButton(if (keys) R.drawable.ic_close else R.drawable.ic_dialpad, if (keys) "Hide" else "Keypad", null, 56.dp) { keys = !keys }
            else Spacer(Modifier.size(56.dp))
        }
    }
}

/** A soft wash of the Phone's colour behind the call, darker below. */
private fun callWash(c: Color) = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.copy(alpha = 0.45f), Color.Transparent))

@Suppress("DEPRECATION")
@Composable
private fun InCallControls(context: Context) {
    val audio = PhoneCalls.audio
    val muted = audio?.isMuted == true
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val routes = audio?.supportedRouteMask ?: 0
    val bluetooth = routes and CallAudioState.ROUTE_BLUETOOTH != 0
    // shortcut: Speaker cycles earpiece → speaker → Bluetooth; the route picker (Phone · Speaker · each device · Computer) is step 3
    val next = when (route) {
        CallAudioState.ROUTE_SPEAKER -> if (bluetooth) CallAudioState.ROUTE_BLUETOOTH else CallAudioState.ROUTE_WIRED_OR_EARPIECE
        CallAudioState.ROUTE_BLUETOOTH -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
        else -> CallAudioState.ROUTE_SPEAKER
    }
    val (routeIcon, routeName) = when (route) {
        CallAudioState.ROUTE_SPEAKER -> R.drawable.ic_volume_up to "Speaker"
        CallAudioState.ROUTE_BLUETOOTH -> R.drawable.ic_bluetooth_audio to (audio?.activeBluetoothDevice?.let { d -> runCatching { d.name }.getOrNull() } ?: "Bluetooth")
        CallAudioState.ROUTE_WIRED_HEADSET -> R.drawable.ic_volume_up to "Headset"
        else -> R.drawable.ic_volume_up to "Speaker"
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundButton(if (muted) R.drawable.ic_mic_off else R.drawable.ic_mic, if (muted) "Muted" else "Mute", if (muted) Color.White else null, 64.dp, dark = muted) {
            PhoneCalls.service?.setMuted(!muted)
        }
        RoundButton(routeIcon, routeName, if (route == CallAudioState.ROUTE_SPEAKER || route == CallAudioState.ROUTE_BLUETOOTH) Color.White else null, 64.dp,
            dark = route == CallAudioState.ROUTE_SPEAKER || route == CallAudioState.ROUTE_BLUETOOTH) {
            PhoneCalls.service?.setAudioRoute(next)
        }
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
private fun RoundButton(icon: Int, label: String, fill: Color?, size: Dp, dark: Boolean = false, click: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size).clip(CircleShape).background(fill ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)).clickable(onClickLabel = label, onClick = click),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = label, tint = if (dark) Color.Black else if (fill != null) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(size * 0.42f))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
    }
}

/** The keys' letters: the Latin ones and the Greek ones (Plan.md § Phone: T9 search in both, step 2). */
internal val KEY_LETTERS = mapOf('2' to "ABC ΑΒΓ", '3' to "DEF ΔΕΖ", '4' to "GHI ΗΘΙ", '5' to "JKL ΚΛΜ", '6' to "MNO ΝΞΟ", '7' to "PQRS ΠΡΣ", '8' to "TUV ΤΥΦ", '9' to "WXYZ ΧΨΩ", '0' to "+")

/** The 4×3 keys; [press] when a key goes down, [release] when it comes up (a call's tones last as long as the finger). */
@Composable
private fun DialKeys(modifier: Modifier = Modifier, press: (Char) -> Unit, release: () -> Unit = {}, hold: ((Char) -> Unit)? = null) {
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

/** The Phone app's keypad (step 1: a number and Call; search as you type and Recents come in step 2). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PhoneKeypad(number: MutableState<String>, back: () -> Unit) {
    val context = LocalContext.current
    var dialer by remember { mutableStateOf(PhonePrefs.isDialer(context)) }
    val askRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { dialer = PhonePrefs.isDialer(context) }
    val askCall = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok && placePhoneCall(context, number.value)) back() }
    val match by produceState<ContactInfo?>(null, number.value) { value = if (number.value.length < 5) null else withContext(Dispatchers.IO) { PhoneCalls.contactFor(number.value) } }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!dialer) Surface(color = PHONE_COLOR.copy(alpha = 0.18f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Make Tetra the phone app: its own call screen, and a buzz when they answer.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { askRole.launch(context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER)) }) { Text("Make") }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(number.value.ifEmpty { " " }, fontSize = if (number.value.length > 13) 28.sp else 36.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(match?.name ?: " ", style = MaterialTheme.typography.titleMedium, color = PHONE_COLOR)
        Spacer(Modifier.height(16.dp))
        DialKeys(press = { ch -> number.value += ch }, hold = { ch -> if (ch == '0') number.value = number.value.dropLast(1) + "+" })
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(56.dp))
            RoundButton(R.drawable.ic_call, "Call", GREEN, 72.dp) {
                val n = number.value.trim()
                if (n.isEmpty()) return@RoundButton
                if (placePhoneCall(context, n)) back() else askCall.launch(Manifest.permission.CALL_PHONE)
            }
            Box(Modifier.size(56.dp).clip(CircleShape).combinedClickable(onClickLabel = "Delete", onLongClick = { number.value = "" }) { number.value = number.value.dropLast(1) },
                contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_backspace), contentDescription = "Delete", modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
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
    TextButton(onClick = { context.startActivity(Intent(context, PhoneDialActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open the keypad") }
}
