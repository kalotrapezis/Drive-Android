package com.kalotrapezis.drive

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class PhoneTab(val icon: Int) { Recents(R.drawable.ic_history), Favourites(R.drawable.ic_star), Contacts(R.drawable.ic_contacts), Recordings(R.drawable.ic_mic) }

/**
 * The Phone applet (Plan.md § Phone), Tetra's way (the user, 24 Oct: "an island for recents, favourites, contacts,
 * recordings, everything centred"): the list under a floating search pill, the island in the middle at the bottom, the
 * keypad a round button beside it. Also what Android opens for a number (ACTION_DIAL, tel: links): then the keypad.
 */
class PhoneActivity : ComponentActivity() {
    private val number = mutableStateOf("")
    private val keypad = mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WriterLog.init(this)
        TetraLook.load(this)
        take(intent)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = TetraLook.light
        setContent { TetraTheme { PhoneApp(keypad, number, back = { finish() }) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); take(intent) }
    private fun take(i: Intent) {
        if (i.action == Intent.ACTION_DIAL || i.data != null) { keypad.value = true; number.value = i.data?.schemeSpecificPart.orEmpty() }
    }
}

/** Opens Tetra's Contacts on a person, or at a new contact with this number. */
internal fun openInContacts(c: Context, uid: String? = null, newNumber: String? = null) = c.startActivity(Intent(c, MainActivity::class.java)
    .putExtra(Applet.EXTRA, if (uid != null) "contact:$uid" else "contact-new:${newNumber.orEmpty()}")
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))

/** The Phone's own folder, Tetra/Phone: the recordings made on the computer land in Recordings (Plan.md § Phone). */
internal fun phoneRoot() = java.io.File(TetraFolder.root(android.os.Environment.getExternalStorageDirectory()), "Phone")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneApp(keypad: MutableState<Boolean>, number: MutableState<String>, back: () -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) { // back from a call or Android's settings, and every change to the call log: read again
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) version++ }
        val log = object : ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) { override fun onChange(self: Boolean) { version++ } }
        lifecycle.lifecycle.addObserver(o)
        runCatching { context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, log) }
        onDispose { lifecycle.lifecycle.removeObserver(o); context.contentResolver.unregisterContentObserver(log) }
    }
    var tab by remember { mutableStateOf(PhoneTab.Recents) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<CallGroup>()) }
    val dialer = remember(version) { PhonePrefs.isDialer(context) }
    val askRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { version++ }
    val contacts by produceState(ContactsPreload.all?.map { it.info } ?: emptyList(), version) {
        value = withContext(Dispatchers.IO) { runCatching { ContactsFiles.all(contactsRoot()).also { ContactsPreload.all = it }.map { it.info } }.getOrDefault(value) }
    }
    val calls by produceState(emptyList<LoggedCall>(), version) { value = withContext(Dispatchers.IO) { PhoneLog.read(context) } }
    val byKey = remember(contacts) { contacts.flatMap { c -> c.phones.map { ContactsWays.key(it.value) to c } }.filter { it.first.length >= 5 }.toMap() }
    fun who(n: String) = byKey[ContactsWays.key(n)]
    LaunchedEffect(tab, dialer, calls.size) { if (tab == PhoneTab.Recents && dialer) withContext(Dispatchers.IO) { PhoneLog.seen(context) } }
    var drawer by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<CallGroup?>(null) }
    val askCall = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun call(n: String) { if (n.isNotBlank() && !placePhoneCall(context, n)) askCall.launch(Manifest.permission.CALL_PHONE) }
    BackHandler(keypad.value || selected.isNotEmpty() || query.isNotEmpty()) {
        when { keypad.value -> keypad.value = false; selected.isNotEmpty() -> selected = emptySet(); else -> query = "" }
    }
    // the list's room under the pill and above the island
    val top = PaddingValues(top = 76.dp, bottom = 96.dp)
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxHeight().widthIn(max = 560.dp).fillMaxWidth()) {
            if (keypad.value) Keypad(number, contacts, call = ::call)
            else when (tab) {
                PhoneTab.Recents -> Recents(calls, ::who, dialer, query, selected, { selected = it }, top, call = ::call, open = { details = it }) {
                    askRole.launch(context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER))
                }
                PhoneTab.Favourites -> Favourites(contacts.filter { it.favourite && (query.isBlank() || it.name.contains(query, true)) }.sortedBy { ContactsFiles.sortKey(it, false) }, top, call = ::call)
                PhoneTab.Contacts -> PhoneContacts(contacts, query, top, call = ::call)
                PhoneTab.Recordings -> Recordings(query, top, version)
            }
            // the pill at the top: ‹ and search, or the chosen calls' count and Delete
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                    IconButton(onClick = { when { keypad.value -> keypad.value = false; selected.isNotEmpty() -> selected = emptySet(); else -> back() } }) {
                        Icon(painterResource(if (selected.isNotEmpty()) R.drawable.ic_close else R.drawable.ic_chevron_left), contentDescription = "Back")
                    }
                }
                if (!keypad.value) Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape, modifier = Modifier.weight(1f)) {
                    if (selected.isNotEmpty()) Row(Modifier.padding(start = 18.dp, end = 4.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${selected.sumOf { it.calls.size }} chosen", Modifier.weight(1f))
                        IconButton(onClick = { PhoneLog.delete(context, selected.flatMap { g -> g.calls.map { it.id } }); selected = emptySet() }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete")
                        }
                    } else Row(Modifier.padding(start = 16.dp, end = 4.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = null, Modifier.size(22.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Search ${tab.name.lowercase()}", color = islandContentColor().copy(alpha = 0.7f), maxLines = 1)
                            BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = islandContentColor(), fontSize = 16.sp),
                                cursorBrush = SolidColor(islandContentColor()), modifier = Modifier.fillMaxWidth())
                        }
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear") }
                    }
                }
            }
            // the island in the middle, the keypad's round button beside it
            if (!keypad.value) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IslandBottomBar(expand = { drawer = true }, visible = !drawer) {
                    PhoneTab.entries.forEach { t -> IslandNavigationItem(t.name, tab == t, t.icon) { tab = t; selected = emptySet() } }
                }
                IslandVisibility(!drawer) {
                    Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                        IconButton(onClick = { keypad.value = true }, modifier = Modifier.size(56.dp)) { Icon(painterResource(R.drawable.ic_dialpad), contentDescription = "Keypad") }
                    }
                }
            }
        }
    }
    details?.let { g ->
        ModalBottomSheet(onDismissRequest = { details = null }, containerColor = SheetColor, contentColor = islandContentColor()) {
            CallDetails(g, calls.filter { PhoneLog.same(it.number, g.number) }, who(g.number), call = { call(it); details = null },
                delete = { ids -> PhoneLog.delete(context, ids); details = null; version++ })
        }
    }
    if (drawer) ModalBottomSheet(onDismissRequest = { drawer = false }, containerColor = SheetColor, contentColor = islandContentColor()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Phone", style = MaterialTheme.typography.titleLarge)
            PhoneSettings()
            SpeedDialSettings(contacts)
        }
    }
}

private enum class RecentsFilter { All, Missed, Unknown }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Recents(calls: List<LoggedCall>, who: (String) -> ContactInfo?, dialer: Boolean, query: String, selected: Set<CallGroup>, select: (Set<CallGroup>) -> Unit,
                    pad: PaddingValues, call: (String) -> Unit, open: (CallGroup) -> Unit, makeDialer: () -> Unit) {
    var filter by remember { mutableStateOf(RecentsFilter.All) }
    val zone = ZoneId.systemDefault()
    val groups = remember(calls, query, filter) {
        val q = query.trim().lowercase()
        PhoneLog.group(calls.filter { c ->
            when (filter) { RecentsFilter.All -> true; RecentsFilter.Missed -> c.missed; RecentsFilter.Unknown -> who(c.number) == null }
        }, zone).filter { g -> q.isEmpty() || g.number.filter(Char::isDigit).contains(q.filter(Char::isDigit).ifEmpty { "\u0000" }) || who(g.number)?.name?.lowercase()?.contains(q) == true }
    }
    val today = LocalDate.now(zone)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
        if (!dialer) item {
            Surface(color = PHONE_COLOR.copy(alpha = 0.18f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Make Tetra the phone app: its own call screen, Recents, and a buzz when they answer.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = makeDialer) { Text("Make") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                RecentsFilter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.name) }) }
            }
        }
        if (groups.isEmpty()) item { Hint(if (filter == RecentsFilter.Missed) "No missed calls" else if (query.isNotEmpty()) "No calls match \"$query\"" else "No calls yet") }
        var last: String? = null
        groups.forEach { g ->
            val d = PhoneLog.day(g.newest.at, zone)
            val head = when (d) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> "Earlier" }
            if (head != last) { last = head; item(key = "h:$head") { Text(head, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 2.dp), color = PHONE_COLOR, fontWeight = FontWeight.Bold) } }
            item(key = g.newest.id) {
                val c = who(g.number)
                val chosen = g in selected
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(MaterialTheme.shapes.large).background(if (chosen) PHONE_COLOR.copy(alpha = 0.3f) else Color.Transparent)
                    .combinedClickable(onLongClick = { select(selected + g) }) { if (selected.isEmpty()) open(g) else select(if (chosen) selected - g else selected + g) }
                    .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CallAvatar(c, 42.dp)
                    Column(Modifier.weight(1f)) {
                        val missed = g.newest.missed
                        Text((c?.name ?: g.number.ifBlank { "Hidden number" }) + if (g.calls.size > 1) " (${g.calls.size})" else "",
                            color = if (missed) CALL_RED else MaterialTheme.colorScheme.onSurface, fontWeight = if (missed && g.newest.unseen) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CallTypeIcon(g.newest.type, 16.dp)
                            val where = c?.phones?.firstOrNull { ContactsWays.key(it.value) == ContactsWays.key(g.number) }?.label?.ifBlank { null } ?: PhoneArea.of(g.number)
                            Text(listOfNotNull(where, whenText(g.newest.at, zone, today)).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    if (g.number.isNotBlank()) IconButton(onClick = { call(g.number) }) { Icon(painterResource(R.drawable.ic_call), contentDescription = "Call", tint = PHONE_COLOR) }
                }
            }
        }
    }
}

@Composable private fun Hint(text: String) = Text(text, Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Tetra's contacts, A to Ω, each with its call button; a tap opens the person in Contacts. */
@Composable
private fun PhoneContacts(contacts: List<ContactInfo>, query: String, pad: PaddingValues, call: (String) -> Unit) {
    val context = LocalContext.current
    val shown = remember(contacts, query) {
        contacts.filter { it.phones.isNotEmpty() && (query.isBlank() || it.name.contains(query, true) || it.phones.any { p -> p.value.filter(Char::isDigit).contains(query.filter(Char::isDigit).ifEmpty { "\u0000" }) }) }
            .sortedBy { ContactsFiles.sortKey(it, false) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
        if (shown.isEmpty()) item { Hint(if (query.isNotEmpty()) "No one matches \"$query\"." else "No contacts with a number yet.") }
        var last = ""
        shown.forEach { c ->
            val l = ContactsFiles.letter(c, false)
            if (l != last && query.isEmpty()) { last = l; item(key = "h:$l") { Text(l, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 2.dp), color = PHONE_COLOR, fontWeight = FontWeight.Bold) } }
            item(key = c.uid.ifBlank { c.name }) {
                val n = c.defaultPhone ?: c.phones.first().value
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(MaterialTheme.shapes.large).clickable { openInContacts(context, uid = c.uid) }
                    .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ContactAvatar(c, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(n, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { call(n) }) { Icon(painterResource(R.drawable.ic_call), contentDescription = "Call ${c.name}", tint = PHONE_COLOR) }
                }
            }
        }
    }
}

/** The calls recorded on the computer (Plan.md § Phone › Call recording), synced here: Tetra/Phone/Recordings, newest first. */
@Composable
private fun Recordings(query: String, pad: PaddingValues, version: Int) {
    val context = LocalContext.current
    val files by produceState(emptyList<java.io.File>(), version) {
        value = withContext(Dispatchers.IO) { java.io.File(phoneRoot(), "Recordings").listFiles { f -> f.isFile && f.extension.lowercase() in setOf("ogg", "opus", "m4a", "mp3", "wav") }?.sortedByDescending { it.lastModified() }.orEmpty() }
    }
    val shown = files.filter { query.isBlank() || it.name.contains(query, true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
        if (shown.isEmpty()) item {
            Hint(if (query.isNotEmpty()) "No recording matches \"$query\"." else "Calls recorded on the computer show here. A phone app cannot record a call's other side on Android; when the call's sound is on the computer, its call bubble can.")
        }
        items(shown, key = { it.path }) { f ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(MaterialTheme.shapes.large).clickable {
                runCatching {
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "audio/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { WriterLog.w("Phone: a recording did not open (${it.message})") }
            }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(PHONE_COLOR.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_mic), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(f.nameWithoutExtension, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${f.length() / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun whenText(at: Long, zone: ZoneId, today: LocalDate): String {
    val t = Instant.ofEpochMilli(at).atZone(zone)
    val d = t.toLocalDate()
    return when {
        d == today || d == today.minusDays(1) -> t.format(DateTimeFormatter.ofPattern("HH:mm"))
        d.isAfter(today.minusDays(7)) -> t.format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH))
        d.year == today.year -> t.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
        else -> t.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
    }
}

@Composable
private fun CallTypeIcon(type: Int, size: Dp) {
    val (icon, tint, what) = when (type) {
        CallLog.Calls.OUTGOING_TYPE -> Triple(R.drawable.ic_call_made, CALL_GREEN, "Outgoing")
        CallLog.Calls.MISSED_TYPE -> Triple(R.drawable.ic_call_missed, CALL_RED, "Missed")
        CallLog.Calls.REJECTED_TYPE -> Triple(R.drawable.ic_call_end, MaterialTheme.colorScheme.onSurfaceVariant, "Declined")
        CallLog.Calls.BLOCKED_TYPE -> Triple(R.drawable.ic_block, MaterialTheme.colorScheme.onSurfaceVariant, "Blocked")
        else -> Triple(R.drawable.ic_call_received, PHONE_COLOR, "Incoming")
    }
    Icon(painterResource(icon), contentDescription = what, tint = tint, modifier = Modifier.size(size))
}

private fun typeText(type: Int) = when (type) {
    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"; CallLog.Calls.MISSED_TYPE -> "Missed"; CallLog.Calls.REJECTED_TYPE -> "Declined"
    CallLog.Calls.BLOCKED_TYPE -> "Blocked"; CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"; else -> "Incoming"
}

@Composable
internal fun CallAvatar(c: ContactInfo?, size: Dp) {
    if (c != null) ContactAvatar(c, size)
    else Box(Modifier.size(size).clip(CircleShape).background(PHONE_COLOR.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/** A line of Recents opened: who, the ways to reach them, and every call with that number. */
@Composable
private fun CallDetails(g: CallGroup, all: List<LoggedCall>, c: ContactInfo?, call: (String) -> Unit, delete: (List<Long>) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val notes = remember { PhoneNotes.all() } // shortcut: read on the main thread, a few KB; move off it if notes grow large
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CallAvatar(c, 88.dp)
                Text(c?.name ?: g.number.ifBlank { "Hidden number" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 10.dp), textAlign = TextAlign.Center)
                if (g.number.isNotBlank()) Text(listOfNotNull(c?.let { g.number }, PhoneArea.of(g.number)).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = islandContentColor().copy(alpha = 0.7f))
                if (g.number.isNotBlank()) Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundButton(R.drawable.ic_call, "Call", CALL_GREEN, 56.dp) { call(g.number) }
                    RoundButton(R.drawable.ic_chat, "Message", null, 56.dp) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(g.number)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    RoundButton(R.drawable.ic_person, if (c != null) "Contact" else "Add", null, 56.dp) { openInContacts(context, uid = c?.uid?.ifBlank { null }, newNumber = g.number) }
                    RoundButton(R.drawable.ic_copy, "Copy", null, 56.dp) {
                        context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Number", g.number))
                    }
                }
            }
        }
        items(all.take(50), key = { it.id }) { l ->
            Column {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CallTypeIcon(l.type, 18.dp)
                val t = Instant.ofEpochMilli(l.at).atZone(zone)
                Text(t.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)), Modifier.weight(1f))
                Text(listOfNotNull(typeText(l.type), l.seconds.takeIf { it > 0 }?.let { PhoneCalls.elapsed(it * 1000) }).joinToString(" · "), color = islandContentColor().copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall)
            }
            PhoneNotes.of(context, l.number, l.at, notes)?.let { Text(it, Modifier.padding(start = 28.dp, bottom = 4.dp), style = MaterialTheme.typography.bodyMedium, color = islandContentColor().copy(alpha = 0.85f)) }
            }
        }
        item {
            TextButton(onClick = { delete(g.calls.map { it.id }) }, modifier = Modifier.padding(top = 8.dp)) {
                Text(if (g.calls.size == 1) "Delete this call" else "Delete these ${g.calls.size} calls", color = CALL_RED)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Favourites(people: List<ContactInfo>, pad: PaddingValues, call: (String) -> Unit) {
    val context = LocalContext.current
    if (people.isEmpty()) { Box(Modifier.padding(pad)) { Hint("Star people in Contacts and they show here: one tap calls, holding opens them.") }; return }
    LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 76.dp, bottom = 96.dp)) {
        items(people, key = { it.uid.ifBlank { it.name } }) { p ->
            val number = p.defaultPhone ?: p.phones.firstOrNull()?.value
            Column(Modifier.clip(MaterialTheme.shapes.medium).combinedClickable(onLongClick = { openInContacts(context, uid = p.uid) }) { if (number != null) call(number) else openInContacts(context, uid = p.uid) }
                .padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ContactAvatar(p, 72.dp)
                Text(p.name, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** The keypad with search as you type (Latin and Greek letters); hold 1 for voicemail, 2–9 for speed dial. */
@Composable
private fun Keypad(number: MutableState<String>, contacts: List<ContactInfo>, call: (String) -> Unit) {
    val context = LocalContext.current
    var assigning by remember { mutableStateOf<Char?>(null) }
    val typed = number.value
    val found = remember(typed, contacts) {
        if (typed.isEmpty()) emptyList() else contacts.filter { PhoneT9.matches(typed, it.name, it.phones.map { p -> p.value }) }
            .sortedWith(compareBy({ !it.favourite }, { it.name.lowercase() })).take(30)
    }
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true) {
            items(found, key = { it.uid.ifBlank { it.name } }) { p ->
                val n = p.phones.firstOrNull { PhoneT9.matches(typed.filter(Char::isDigit), "", listOf(it.value)) }?.value ?: p.defaultPhone ?: p.phones.firstOrNull()?.value
                Row(Modifier.fillMaxWidth().clickable { if (n != null) number.value = n }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ContactAvatar(p, 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (n != null) Text(n, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (n != null) IconButton(onClick = { call(n) }) { Icon(painterResource(R.drawable.ic_call), contentDescription = "Call ${p.name}", tint = PHONE_COLOR) }
                }
            }
        }
        Text(typed.ifEmpty { " " }, fontSize = if (typed.length > 13) 28.sp else 36.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Text(PhoneArea.of(typed)?.takeIf { found.isEmpty() } ?: " ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        DialKeys(press = { ch -> number.value += ch }, hold = { ch ->
            when (ch) {
                '0' -> number.value = number.value.dropLast(1) + "+"
                '1' -> if (number.value == "1") { number.value = ""; PhoneSpeed.voicemail(context) }
                in '2'..'9' -> if (number.value == ch.toString()) {
                    number.value = ""
                    PhoneSpeed.get(context, ch)?.let { call(it.second) } ?: run { assigning = ch }
                }
            }
        })
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(56.dp))
            RoundButton(R.drawable.ic_call, "Call", CALL_GREEN, 68.dp) { call(number.value.trim()) }
            @OptIn(ExperimentalFoundationApi::class)
            Box(Modifier.size(56.dp).clip(CircleShape).combinedClickable(onClickLabel = "Delete", onLongClick = { number.value = "" }) { number.value = number.value.dropLast(1) },
                contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_backspace), contentDescription = "Delete", modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    assigning?.let { key -> SpeedDialPicker(key, contacts, dismiss = { assigning = null }) }
}

/** Speed dial: a contact's number on a key, held from the keypad. Kept per phone ("phone" preferences). */
internal object PhoneSpeed {
    private fun p(c: Context) = c.getSharedPreferences("phone", Context.MODE_PRIVATE)
    fun get(c: Context, key: Char): Pair<String, String>? = p(c).getString("speed_$key", null)?.split('\n')?.takeIf { it.size == 2 }?.let { it[0] to it[1] }
    fun set(c: Context, key: Char, name: String?, number: String?) = p(c).edit().apply { if (number == null) remove("speed_$key") else putString("speed_$key", "$name\n$number") }.apply()
    fun voicemail(c: Context) = runCatching { c.getSystemService(android.telecom.TelecomManager::class.java).placeCall(Uri.fromParts("voicemail", "", null), Bundle()) }
        .onFailure { WriterLog.w("Phone: voicemail not called (${it.message})") }
}

@Composable
private fun SpeedDialPicker(key: Char, contacts: List<ContactInfo>, dismiss: () -> Unit) {
    val context = LocalContext.current
    var q by remember { mutableStateOf("") }
    val rows = remember(q, contacts) {
        contacts.filter { q.isBlank() || it.name.contains(q, true) }.flatMap { c -> c.phones.map { c.name to it } }.take(60)
    }
    AlertDialog(onDismissRequest = dismiss, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
        title = { Text("Speed dial $key") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Search contacts") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(rows) { (name, f) ->
                        Column(Modifier.fillMaxWidth().clickable { PhoneSpeed.set(context, key, name, f.value); dismiss() }.padding(vertical = 8.dp)) {
                            Text(name); Text(listOf(f.label, f.value).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        })
}

@Composable
private fun SpeedDialSettings(contacts: List<ContactInfo>) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var assigning by remember { mutableStateOf<Char?>(null) }
    @Suppress("UNUSED_EXPRESSION") version
    Text("Speed dial", style = MaterialTheme.typography.titleSmall)
    Text("Hold a key on the keypad to call: 1 is voicemail.", style = MaterialTheme.typography.bodySmall, color = islandContentColor().copy(alpha = 0.7f))
    ('2'..'9').forEach { k ->
        val s = PhoneSpeed.get(context, k)
        Row(Modifier.fillMaxWidth().clickable { assigning = k }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$k", Modifier.width(28.dp), fontWeight = FontWeight.Bold)
            Text(s?.let { "${it.first} · ${it.second}" } ?: "—", Modifier.weight(1f))
            if (s != null) TextButton(onClick = { PhoneSpeed.set(context, k, null, null); version++ }) { Text("Remove") }
        }
    }
    assigning?.let { k -> SpeedDialPicker(k, contacts, dismiss = { assigning = null; version++ }) }
}
