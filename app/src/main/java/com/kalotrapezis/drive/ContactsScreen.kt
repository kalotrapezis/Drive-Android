package com.kalotrapezis.drive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Contacts' colour: the icon's own brown (Plan.md § Contacts › Home). */
internal val CONTACTS_COLOR = Color(0xFFBE7E4D)

internal fun contactsRoot() = ContactsFiles.root(TetraFolder.root(Environment.getExternalStorageDirectory()))

internal object ContactsPrefs {
    private fun p(c: Context) = c.getSharedPreferences("contacts", Context.MODE_PRIVATE)
    fun byLast(c: Context) = p(c).getBoolean("byLast", false)
    fun setByLast(c: Context, on: Boolean) = p(c).edit().putBoolean("byLast", on).apply()
    /** Settings › Contacts › Birthdays go into: a calendar's folder; none chosen: Γενέθλια or Birthdays (ContactsBirthdays.calendar). */
    fun birthdays(c: Context) = p(c).getString("birthdays", null)
    fun setBirthdays(c: Context, dir: String) = p(c).edit().putString("birthdays", dir).apply()
    /** Events of the birthdays calendar the person said are no one here ("Not this person" in the review). */
    fun dismissed(c: Context): Set<String> = p(c).getStringSet("notLinked", emptySet())!!
    fun dismiss(c: Context, uids: Collection<String>) = p(c).edit().putStringSet("notLinked", dismissed(c) + uids).apply()
}

/**
 * Every contact's dates made to match its events — except those an unanswered link suggestion names (an event already in
 * the calendar may be theirs: linking it, not a second event). Answers the suggestions left.
 */
internal fun syncBirthdays(context: Context, contacts: List<ContactInfo>): List<ContactsBirthdays.Link> {
    val calRoot = calendarRoot()
    val links = ContactsBirthdays.suggestions(calRoot, contacts, ContactsPrefs.birthdays(context), ContactsPrefs.dismissed(context))
    val waiting = links.map { it.contact.uid }.toSet()
    if (ContactsBirthdays.syncAll(calRoot, contacts.filter { it.uid !in waiting }, ContactsPrefs.birthdays(context)) > 0) CalendarAlarms.planSoon(context)
    return links
}

private enum class ContactsTab(val label: String, val icon: Int) { All("All", R.drawable.ic_person), Favourites("Favourites", R.drawable.ic_star), Labels("Labels", R.drawable.ic_label) }

/**
 * Contacts (Plan.md § Contacts): the list (search on top, favourites' faces, letters), a contact's page as the iPhone's
 * made Tetra's, Tetra's island at the bottom (All · Favourites · Labels, + at the right) and its drawer (Import, Trash,
 * Settings). A wide screen has the list at the left and the open contact beside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactsScreen(back: () -> Unit, hasAccess: Boolean, grant: () -> Unit, start: String? = null, openMaps: (String) -> Unit = {}) {
    val context = LocalContext.current
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(ContactsTab.All) }
    var label by remember { mutableStateOf<String?>(null) }
    var openUid by remember { mutableStateOf(start) }
    var drawer by remember { mutableStateOf(false) }
    var trashOpen by remember { mutableStateOf(false) }
    var byLast by remember { mutableStateOf(ContactsPrefs.byLast(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var links by remember { mutableStateOf<List<ContactsBirthdays.Link>>(emptyList()) } // events in the birthdays calendar that seem to be these people
    var reviewing by remember { mutableStateOf(false) }
    var faceMatches by remember { mutableStateOf<List<Pair<ContactInfo, PhotoPerson>>>(emptyList()) } // contacts with no photo Photos knows by name
    var facesOpen by remember { mutableStateOf(false) }
    var mergeOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var choosingCalendar by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) } // long press: many at once (the user, 23 Oct: "how do I multi select?")
    var labelling by remember { mutableStateOf(false) }
    var deletingMany by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<Contact?, ContactInfo>?>(null) } // the contact (null: a new one) and where its editor starts
    LaunchedEffect(message) { if (message != null) { delay(3500); message = null } }
    val root = remember { contactsRoot() }

    if (!hasAccess) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
            Text("Your contacts are files in Tetra's folder, one per person, so they go with your other files and Claude can read them. Allow access to all files to use Contacts.")
            TextButton(onClick = grant) { Text("Allow access") }
        }
        return
    }
    DisposableEffect(root) {
        root.mkdirs()
        val watcher = DriveWatcher(root) { version++ }
        onDispose { watcher.stop() }
    }
    val all by produceState(emptyList<Contact>(), version, byLast) {
        value = withContext(Dispatchers.IO) { runCatching { ContactsFiles.sorted(ContactsFiles.all(root), byLast) }.getOrDefault(emptyList()) }
    }
    val open = all.firstOrNull { it.info.uid == openUid }
    // on a wide screen a contact is always open beside the list
    LaunchedEffect(wide, all) { if (wide && open == null) openUid = all.firstOrNull()?.info?.uid }

    /** A change made here goes to the other devices at once (Contacts' part, SYNC_PLAN 6ap), its birthdays' events with it. */
    fun pushed() { ScopedSync.soon(context, "contacts", 0, orWhole = true); ScopedSync.soon(context, "calendar", 0, orWhole = true) }
    /** Runs [work] on the IO thread, says what it answers, and reads the folder again. */
    fun act(work: () -> String) = scope.launch {
        val r = withContext(Dispatchers.IO) { runCatching(work) }
        message = r.getOrElse { "Not done: ${it.message ?: it.javaClass.simpleName}" }
        r.exceptionOrNull()?.let { WriterLog.e("Contacts: a change failed", it) }
        version++; pushed()
    }
    /** [c] (null: a new contact) saved as [i]; the page opens on it. */
    fun save(c: Contact?, i: ContactInfo, said: String = "Saved") = scope.launch {
        val r = withContext(Dispatchers.IO) { runCatching { ContactsFiles.save(root, c, i).also { saved ->
            // its birthday and dates into the calendar (the contact is the truth)
            runCatching { val calRoot = calendarRoot(); if (ContactsBirthdays.sync(calRoot, saved.info, ContactsPrefs.birthdays(context), ContactsBirthdays.linked(calRoot)[saved.info.uid].orEmpty()) > 0) CalendarAlarms.planSoon(context) }
                .onFailure { WriterLog.e("Contacts: dates into the calendar", it) }
        } } }
        r.onSuccess { openUid = it.info.uid; message = said }.onFailure { message = "Not saved: ${it.message}"; WriterLog.e("Contacts: save", it) }
        version++; pushed()
    }
    fun imported(r: ContactsFiles.Imported) = "Imported: ${r.added} new" + if (r.same > 0) ", ${r.same} already here" else ""
    /** Contacts with no photo whose faces Photos knows: offered after an import (and in the drawer). */
    suspend fun faces(show: Boolean) {
        faceMatches = withContext(Dispatchers.IO) { runCatching { ContactsFaces.matches(ContactsFiles.all(root).map { it.info }, ContactsFaces.people(context)) { it.name } }.getOrDefault(emptyList()) }
        if (show && faceMatches.isNotEmpty()) facesOpen = true
    }
    /** After an import or a link: the dates into the calendar, and the events there that may be these people offered for linking. */
    suspend fun birthdays(show: Boolean) {
        val l = withContext(Dispatchers.IO) { runCatching { syncBirthdays(context, ContactsFiles.all(root).map { it.info }) }.onFailure { WriterLog.e("Contacts: birthdays", it) }.getOrDefault(emptyList()) }
        links = l
        if (show && l.isNotEmpty()) reviewing = true else faces(show)
    }
    LaunchedEffect(Unit) { birthdays(false) }
    // Import: the phone's own contacts (the Google account's among them), or a file — vCard, Google's CSV, Takeout's zip
    fun importPhone() = scope.launch {
        busy = "Reading the phone's contacts…"
        val r = withContext(Dispatchers.IO) { runCatching { ContactsFiles.import(root, ContactsSystem.phoneCards(context) { n, of -> busy = "Reading the phone's contacts… $n of $of" }) } }
        busy = null
        message = r.fold(::imported) { "Could not read them: ${it.message}" }
        r.exceptionOrNull()?.let { WriterLog.e("Contacts: phone import", it) }
        version++; pushed()
        birthdays(true)
    }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) importPhone() else message = "Tetra needs to read the phone's contacts to bring them in." }
    fun fromPhone() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) importPhone()
        else askContacts.launch(Manifest.permission.READ_CONTACTS)
    }
    fun importFile(uri: Uri) = scope.launch {
        busy = "Importing…"
        val r = withContext(Dispatchers.IO) { runCatching {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: ""
            ContactsFiles.import(root, ContactsImport.read(bytes, name))
        } }
        busy = null
        message = r.fold(::imported) { "Could not read it: ${it.message ?: it.javaClass.simpleName}" }
        r.exceptionOrNull()?.let { WriterLog.e("Contacts: file import", it) }
        version++; pushed()
        birthdays(true)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::importFile) }
    // the phone's copy (Tetra's account) follows every change: a save, an import, a merge, a file that came by sync
    // on only when it can be: chosen (the default since the user's yes) and Tetra may write the phone's contacts
    var systemOn by remember { mutableStateOf(ContactsAccount.on(context) && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    LaunchedEffect(version, systemOn) { ContactsAccount.soon(context) }
    var callsOn by remember { mutableStateOf(PhoneCall.allowed(context)) }
    val askCalls = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> callsOn = ok; message = if (ok) "A call from the computer now rings at once" else "Calls from the computer come as a notification to tap" }
    val askWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { ContactsAccount.set(context, true); systemOn = true; message = "Your contacts are in the phone's contacts too" }
        else message = "Tetra needs to write the phone's contacts for this."
    }
    // Export: all, the favourites or a label, as one .vcf saved where the person chooses
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/vcard")) { uri ->
        val cs = exporting
        if (uri != null) act { context.contentResolver.openOutputStream(uri)!!.use { it.write(ContactsMerge.export(cs).toByteArray()) }; "${cs.size} contacts exported" }
    }
    val duplicates = remember(all) { ContactsMerge.pairs(all) }

    val shown = remember(all, query, tab, label) {
        all.filter { c -> ContactsFiles.matches(c.info, query) && when {
            tab == ContactsTab.Favourites -> c.info.favourite
            tab == ContactsTab.Labels && label != null -> c.info.labels.any { it.equals(label, true) }
            else -> true
        } }
    }
    BackHandler(editing == null) { when { selected.isNotEmpty() -> selected = emptySet(); drawer -> drawer = false; !wide && open != null -> openUid = null; label != null -> label = null; query.isNotEmpty() -> query = ""; else -> back() } }

    val list: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            val state = rememberLazyListState()
            LazyColumn(state = state, contentPadding = PaddingValues(top = 84.dp, bottom = 120.dp), modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                if (busy != null) item { Text(busy!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (tab == ContactsTab.Labels && label == null && query.isEmpty()) {
                    val labels = all.flatMap { it.info.labels }.groupingBy { it }.eachCount().toSortedMap(String.CASE_INSENSITIVE_ORDER)
                    if (labels.isEmpty()) item { Hint("No labels yet. A contact's labels (Family, School…) come with an import, or are added on its page.") }
                    labels.forEach { (name, n) -> item(key = "label:$name") {
                        Row(Modifier.fillMaxWidth().clickable { label = name }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(painterResource(R.drawable.ic_label), contentDescription = null, tint = CONTACTS_COLOR)
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text("$n", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } }
                    return@LazyColumn
                }
                if (label != null) item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = CONTACTS_COLOR.copy(alpha = 0.18f), shape = CircleShape) {
                            Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label!!, style = MaterialTheme.typography.labelLarge)
                                IconButton(onClick = { label = null }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "All contacts", Modifier.size(18.dp)) }
                            }
                        }
                    }
                }
                // the favourites' faces on top of All (the user's sketch: round faces, one tap opens)
                val favs = all.filter { it.info.favourite }
                if (tab == ContactsTab.All && query.isEmpty() && label == null && favs.isNotEmpty()) item(key = "favs") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(favs, key = { "f" + it.info.uid }) { c ->
                            Column(Modifier.width(64.dp).clickable { openUid = c.info.uid }, horizontalAlignment = Alignment.CenterHorizontally) {
                                ContactAvatar(c.info, 56.dp)
                                Text(c.info.given.ifBlank { c.info.name }, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (shown.isEmpty() && busy == null) item {
                    Hint(when {
                        all.isEmpty() -> "No contacts yet. Pull up the island for Import: this phone's contacts, or a file from Google Contacts (vCard, CSV or Takeout's zip)."
                        query.isNotEmpty() -> "No one matches \"$query\"."
                        tab == ContactsTab.Favourites -> "No favourites yet: the star on a contact's page adds one."
                        else -> "No one here."
                    })
                }
                var last = ""
                shown.forEach { c ->
                    val l = ContactsFiles.letter(c.info, byLast)
                    if (l != last && query.isEmpty()) { last = l; item(key = "h:$l") { Text(l, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 2.dp), color = CONTACTS_COLOR, fontWeight = FontWeight.Bold) } }
                    item(key = c.info.uid) {
                        val picked = c.info.uid in selected
                        val chosen = picked || (wide && selected.isEmpty() && c.info.uid == openUid)
                        fun toggle() { selected = if (picked) selected - c.info.uid else selected + c.info.uid }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(MaterialTheme.shapes.large)
                            .background(if (picked) CONTACTS_COLOR.copy(alpha = 0.4f) else if (chosen) CONTACTS_COLOR.copy(alpha = 0.22f) else Color.Transparent)
                            .combinedClickable(onLongClick = ::toggle) { if (selected.isNotEmpty()) toggle() else openUid = c.info.uid }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (picked) Box(Modifier.size(40.dp).clip(CircleShape).background(CONTACTS_COLOR), contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_check), contentDescription = "Selected", tint = Color.White)
                            } else ContactAvatar(c.info, 40.dp)
                            Column(Modifier.weight(1f)) {
                                Text(c.info.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                // the first number (or e-mail, company) under the name, small and grey, as on the computer (the user, 24 Oct)
                                (c.info.phones.firstOrNull()?.value ?: c.info.emails.firstOrNull()?.value ?: c.info.org.ifBlank { null })?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            // many chosen: their count, Select all and × in the search pill's place
            if (selected.isNotEmpty()) Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                    IconButton(onClick = { selected = emptySet() }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Done choosing") }
                }
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape, modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(start = 18.dp, end = 4.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${selected.size} selected", Modifier.weight(1f))
                        TextButton(onClick = { selected = if (selected.size == shown.size) emptySet() else shown.map { it.info.uid }.toSet() }) {
                            Text(if (selected.size == shown.size) "None" else "All ${shown.size}", color = islandContentColor())
                        }
                    }
                }
            } else
            // the search pill, floating over the list as Files' top does
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                    IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
                }
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape, modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = null, Modifier.size(22.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text(if (all.isEmpty()) "Contacts" else "Search ${all.size} contacts", color = islandContentColor().copy(alpha = 0.7f), maxLines = 1)
                            BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = islandContentColor(), fontSize = 16.sp),
                                cursorBrush = SolidColor(islandContentColor()), modifier = Modifier.fillMaxWidth())
                        }
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear") }
                    }
                }
            }
        }
    }
    val page: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            val ed = editing
            if (ed != null) ContactEditor(ed.second, ed.first == null, all.flatMap { it.info.labels }.distinctBy { it.lowercase() }, cancel = { editing = null },
                delete = ed.first?.let { c -> { editing = null; openUid = null; act {
                    ContactsFiles.trash(root, c)
                    calendarRoot().let { cr -> ContactsBirthdays.sync(cr, c.info, null, ContactsBirthdays.linked(cr)[c.info.uid].orEmpty(), gone = true) }
                    "${c.info.name} moved to the Trash"
                } } },
                photo = { i, set -> ContactPhotoEdit(i, set) }) { i -> editing = null; save(ed.first, i, if (ed.first == null) "Contact created" else "Saved") }
            else if (open != null) ContactPage(open, all, wide, close = { openUid = null }, edit = { editing = open to open.info },
                star = { save(open, open.info.favourite(!open.info.favourite), if (open.info.favourite) "Not a favourite" else "Added to favourites") }, keep = { act { ContactsFiles.copies(root, open).forEach { f -> DriveRules.moveToTrash(root.parentFile!!, f.relativeTo(root.parentFile!!).path) }; "Kept this version; the other went to the Trash" } },
                openUid = { openUid = it }, openMaps = openMaps)
            else if (wide) Hint("Choose a contact.", Modifier.align(Alignment.Center))
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (wide) Row(Modifier.fillMaxSize()) {
                list(Modifier.width(380.dp).fillMaxHeight())
                page(Modifier.weight(1f).fillMaxHeight())
            } else if (open != null || editing != null) page(Modifier.fillMaxSize()) else list(Modifier.fillMaxSize())
            // Tetra's island at the bottom, + at the right (Files', the Calendar's); on a wide screen under the list
            if (wide || (open == null && editing == null)) Row(Modifier.align(Alignment.BottomStart).then(if (wide) Modifier.width(380.dp) else Modifier.fillMaxWidth())
                .navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (selected.isNotEmpty()) {
                    val many = all.filter { it.info.uid in selected }
                    Surface(color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.extraLarge) {
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            IconButton(onClick = { labelling = true }) { Icon(painterResource(R.drawable.ic_label), contentDescription = "Label") }
                            IconButton(onClick = {
                                val on = !many.all { it.info.favourite }
                                act { many.forEach { c -> ContactsFiles.save(root, c, c.info.favourite(on)) }; if (on) "${many.size} added to favourites" else "${many.size} not favourites" }
                            }) { Icon(painterResource(if (many.all { it.info.favourite }) R.drawable.ic_star else R.drawable.ic_star_border), contentDescription = "Favourites") }
                            IconButton(onClick = {
                                runCatching {
                                    val f = File(File(context.cacheDir, "share").apply { mkdirs() }, "${many.size} contacts.vcf").apply { writeText(ContactsMerge.export(many)) }
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/x-vcard").putExtra(Intent.EXTRA_STREAM, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share ${many.size} contacts"))
                                }.onFailure { WriterLog.e("Contacts: share many", it) }
                            }) { Icon(painterResource(R.drawable.ic_share), contentDescription = "Share") }
                            IconButton(onClick = { exporting = many; exportTo.launch("${many.size} contacts.vcf") }) { Icon(painterResource(R.drawable.ic_file_upload), contentDescription = "Export") }
                            if (many.size == 2) IconButton(onClick = {
                                val (a, b) = many.sortedByDescending { it.lines.size }
                                selected = emptySet()
                                act {
                                    val kept = ContactsMerge.merge(root, a, b)
                                    calendarRoot().let { cr -> runCatching { ContactsBirthdays.moveLinks(cr, b.info.uid, kept.info); ContactsBirthdays.sync(cr, kept.info, ContactsPrefs.birthdays(context), ContactsBirthdays.linked(cr)[kept.info.uid].orEmpty()) } }
                                    openUid = kept.info.uid
                                    "Merged"
                                }
                            }) { Icon(painterResource(R.drawable.ic_cell_merge), contentDescription = "Merge the two") }
                            IconButton(onClick = { deletingMany = true }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete") }
                        }
                    }
                } else IslandBottomBar(expand = { drawer = true }, visible = !drawer) {
                    ContactsTab.entries.forEach { t -> IslandNavigationItem(t.label, tab == t, t.icon) { tab = t; label = null } }
                }
                if (selected.isEmpty()) IslandVisibility(!drawer) {
                    Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                        IconButton(onClick = { editing = null to ContactInfo(categories = listOfNotNull(label)) }) { Icon(painterResource(R.drawable.ic_add), contentDescription = "New contact") }
                    }
                }
            }
            message?.let { m ->
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp, start = 24.dp, end = 24.dp)) { Text(m, Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }
            }
        }
    }
    if (drawer) ModalBottomSheet(onDismissRequest = { drawer = false }, containerColor = SheetColor, contentColor = islandContentColor()) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Import", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrawerButton(R.drawable.ic_phone, "From this phone", false, Modifier.weight(1f)) { drawer = false; fromPhone() }
                DrawerButton(R.drawable.ic_file_download, "From a file", false, Modifier.weight(1f)) {
                    drawer = false; pickFile.launch(arrayOf("text/vcard", "text/x-vcard", "text/directory", "text/csv", "text/comma-separated-values", "application/zip", "application/octet-stream", "*/*"))
                }
            }
            Text("A file from Google Contacts (Export › vCard or Google CSV), Google Takeout's zip, or any .vcf. A person already here is not added twice.",
                style = MaterialTheme.typography.bodySmall, color = islandContentColor().copy(alpha = 0.7f))
            HorizontalDivider(color = islandContentColor().copy(alpha = 0.15f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrawerButton(R.drawable.ic_file_upload, "Export", false, Modifier.weight(1f)) { drawer = false; exportOpen = true }
                DrawerButton(R.drawable.ic_cell_merge, if (duplicates.isEmpty()) "Merge & fix" else "Merge & fix (${duplicates.size})", false, Modifier.weight(1f)) { drawer = false; mergeOpen = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrawerButton(R.drawable.ic_sort_name, if (byLast) "Sort: last name" else "Sort: first name", byLast, Modifier.weight(1f)) {
                    byLast = !byLast; ContactsPrefs.setByLast(context, byLast)
                }
                DrawerButton(R.drawable.ic_delete, "Trash", false, Modifier.weight(1f)) { drawer = false; trashOpen = true }
            }
            DrawerButton(R.drawable.ic_phone, if (systemOn) "In the phone's contacts: on" else "In the phone's contacts: off", systemOn, Modifier.fillMaxWidth()) {
                when {
                    systemOn -> { ContactsAccount.set(context, false); systemOn = false; message = "Tetra's copy left the phone's contacts" }
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED -> { ContactsAccount.set(context, true); systemOn = true }
                    else -> askWrite.launch(Manifest.permission.WRITE_CONTACTS)
                }
            }
            DrawerButton(R.drawable.ic_call, if (callsOn) "Calls from the computer: at once" else "Calls from the computer: as a notification", callsOn, Modifier.fillMaxWidth()) {
                if (!callsOn) askCalls.launch(Manifest.permission.CALL_PHONE) else message = "Turn it off in Android's settings › Apps › Tetra › Permissions › Phone"
            }
            Text("A read-only copy under the account Tetra, so the dialer, Viber, WhatsApp and the car show these names and photos. Edits are made here.",
                style = MaterialTheme.typography.bodySmall, color = islandContentColor().copy(alpha = 0.7f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val calName = remember(drawer) { runCatching { CalendarFiles.calendars(calendarRoot()).let { cals -> cals.firstOrNull { it.dir.name == ContactsPrefs.birthdays(context) } ?: ContactsBirthdays.existing(cals) }?.name }.getOrNull() ?: ContactsBirthdays.NAME }
                DrawerButton(R.drawable.ic_cake, "Birthdays: $calName", false, Modifier.weight(1f)) { drawer = false; choosingCalendar = true }
                if (faceMatches.isNotEmpty()) DrawerButton(R.drawable.ic_person, "Faces from Photos (${faceMatches.size})", false, Modifier.weight(1f)) { drawer = false; facesOpen = true }
                if (links.isNotEmpty()) DrawerButton(R.drawable.ic_link, "Link birthdays (${links.size})", false, Modifier.weight(1f)) { drawer = false; reviewing = true }
            }
            Text("Each birthday and date is an event in that calendar, reminded at 9:00 on the day and the day before; change it here and the event follows.",
                style = MaterialTheme.typography.bodySmall, color = islandContentColor().copy(alpha = 0.7f))
        }
    }
    if (reviewing) LinkReview(links, { reviewing = false }) { chosen ->
        reviewing = false
        scope.launch {
            busy = "Linking…"
            val n = withContext(Dispatchers.IO) { runCatching {
                chosen.forEach { ContactsBirthdays.link(calendarRoot(), root, it) }
                ContactsPrefs.dismiss(context, (links - chosen.toSet()).map { it.event.uid })
                chosen.size
            }.onFailure { WriterLog.e("Contacts: link", it) }.getOrDefault(0) }
            busy = null
            version++; pushed()
            birthdays(false)
            faces(true)
            message = "$n linked; the other birthdays are in the calendar"
        }
    }
    if (choosingCalendar) {
        val cals = remember { runCatching { CalendarFiles.calendars(calendarRoot()) }.getOrDefault(emptyList()) }
        ModalBottomSheet(onDismissRequest = { choosingCalendar = false }, containerColor = SheetColor, contentColor = islandContentColor()) {
            Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Birthdays go into", style = MaterialTheme.typography.titleMedium)
                val now = cals.firstOrNull { it.dir.name == ContactsPrefs.birthdays(context) } ?: ContactsBirthdays.existing(cals)
                cals.forEach { c ->
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { ContactsPrefs.setBirthdays(context, c.dir.name); choosingCalendar = false; message = "New birthdays go into ${c.name}" }
                        .padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(c.color)))
                        Text(c.name, Modifier.weight(1f))
                        if (c == now) Icon(painterResource(R.drawable.ic_check), contentDescription = "Chosen")
                    }
                }
                if (cals.isEmpty()) Text("No calendars yet: ${ContactsBirthdays.NAME} is made with the first birthday.", color = islandContentColor().copy(alpha = 0.7f))
            }
        }
    }
    if (exportOpen) {
        val choices = listOf("All contacts" to all, "Favourites" to all.filter { it.info.favourite }) +
            all.flatMap { it.info.labels }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }.map { l -> l to all.filter { c -> c.info.labels.any { it.equals(l, true) } } }
        ModalBottomSheet(onDismissRequest = { exportOpen = false }, containerColor = SheetColor, contentColor = islandContentColor()) {
            Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp).verticalScroll(rememberScrollState())) {
                Text("Export as a .vcf", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                Text("One file any phone, Google Contacts or Apple's imports, each contact with its photo.", style = MaterialTheme.typography.bodySmall, color = islandContentColor().copy(alpha = 0.7f))
                choices.filter { it.second.isNotEmpty() }.forEach { (name, cs) ->
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { exportOpen = false; exporting = cs; exportTo.launch("$name.vcf") }.padding(horizontal = 8.dp, vertical = 14.dp)) {
                        Text(name, Modifier.weight(1f)); Text("${cs.size}", color = islandContentColor().copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
    if (mergeOpen) MergeSheet(duplicates, { mergeOpen = false }, merge = { pairs ->
        act {
            val calRoot = calendarRoot()
            pairs.forEach { (a, b) ->
                val kept = ContactsMerge.merge(root, a, b)
                runCatching { ContactsBirthdays.moveLinks(calRoot, b.info.uid, kept.info); ContactsBirthdays.sync(calRoot, kept.info, ContactsPrefs.birthdays(context), ContactsBirthdays.linked(calRoot)[kept.info.uid].orEmpty()) }
                    .onFailure { WriterLog.e("Contacts: a merge's dates", it) }
            }
            if (pairs.size == 1) "Merged" else "${pairs.size} merged"
        }
    }, notSame = { a, b -> act { ContactsMerge.notTheSame(a, b); "Kept apart" } })
    if (facesOpen) FacesForMany(faceMatches, { facesOpen = false }) { chosen ->
        facesOpen = false
        scope.launch {
            busy = "Making photos…"
            val n = withContext(Dispatchers.IO) { chosen.count { (c, p) -> runCatching {
                val (photo, face) = p.cover?.let { ContactsFaces.portrait(context, it, p.box) } ?: return@runCatching false
                val now = ContactsFiles.all(root).firstOrNull { it.info.uid == c.uid } ?: return@runCatching false
                ContactsFiles.save(root, now, now.info.copy(photo = photo, face = face, photoUrl = null, person = p.uuid)); true
            }.getOrDefault(false) } }
            busy = null
            message = "$n photos from Photos"
            version++; pushed()
            faces(false)
        }
    }
    if (labelling) {
        val many = all.filter { it.info.uid in selected }
        LabelPick(all.flatMap { it.info.labels }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }, many, { labelling = false }) { l ->
            labelling = false
            // all of them have it: taken off; otherwise given to each
            val off = many.all { c -> c.info.labels.any { it.equals(l, true) } }
            act {
                many.forEach { c -> ContactsFiles.save(root, c, c.info.copy(categories = if (off) c.info.categories.filterNot { it.equals(l, true) } else (c.info.categories + l).distinctBy { it.lowercase() })) }
                if (off) "$l taken off ${many.size}" else "$l given to ${many.size}"
            }
        }
    }
    if (deletingMany) androidx.compose.material3.AlertDialog(onDismissRequest = { deletingMany = false }, title = { Text("Delete ${selected.size} contacts?") },
        text = { Text("They go to Tetra's Trash, with their birthdays' events, and can be restored from there (Contacts › Trash).") },
        confirmButton = { TextButton(onClick = {
            deletingMany = false
            val many = all.filter { it.info.uid in selected }
            selected = emptySet(); openUid = null
            act {
                val cr = calendarRoot(); val links = ContactsBirthdays.linked(cr)
                many.forEach { c -> ContactsFiles.trash(root, c); runCatching { ContactsBirthdays.sync(cr, c.info, null, links[c.info.uid].orEmpty(), gone = true) } }
                "${many.size} moved to the Trash"
            }
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deletingMany = false }) { Text("Cancel") } })
    if (trashOpen) ContactsTrash(root, version, { trashOpen = false }) { c -> act { ContactsFiles.restore(root, c); "${c.info.name} restored" } }
}

@Composable private fun Hint(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

/** A contact's round face: the photo, or the initial on a soft brown (the plan's page). Decoded once per photo. */
@Composable internal fun ContactAvatar(i: ContactInfo, size: Dp) {
    val photo = remember(i.photo) { i.photo?.let { runCatching { java.util.Base64.getDecoder().decode(it) }.getOrNull() }
        ?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    if (photo != null) Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
    else Box(Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFD7A982), CONTACTS_COLOR, Color(0xFF8D5A3B)))), contentAlignment = Alignment.Center) {
        val first = i.name.trim().firstOrNull()?.takeIf { it.isLetterOrDigit() }
        if (first == null) Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
        else Text(first.uppercase(), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Medium)
    }
}

/** "23 Oct · turns 41", "23 Oct 1985", "23 Oct". */
internal fun dateText(d: VDate, birthday: Boolean, today: LocalDate = LocalDate.now()): String {
    val day = d.inYear(today.year).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    if (d.year == null) return day
    val next = d.inYear(today.year).let { if (it.isBefore(today)) d.inYear(today.year + 1) else it }
    val age = next.year - d.year
    return if (birthday && age in 1..130) "$day · ${if (next == today) "$age today" else "turns $age"}" else "$day ${d.year}"
}

/**
 * A contact's page (the user's iPhone screenshot in Tetra's dark look): the round face, the name and job, round Message ·
 * Call · Video · Mail buttons, then the details in cards.
 */
@Composable
private fun ContactPage(c: Contact, all: List<Contact>, wide: Boolean, close: () -> Unit, edit: () -> Unit, star: () -> Unit, keep: () -> Unit, openUid: (String) -> Unit, openMaps: (String) -> Unit) {
    val context = LocalContext.current
    val i = c.info
    fun go(uri: String, action: String = Intent.ACTION_VIEW) { runCatching { context.startActivity(Intent(action, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { android.widget.Toast.makeText(context, "No app opens this", android.widget.Toast.LENGTH_SHORT).show() } }
    var history by remember(c.info.uid) { mutableStateOf(false) }
    var strip by remember { mutableStateOf<Pair<String, List<Pair<String, () -> Unit>>>?>(null) }
    var qr by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(top = 72.dp, bottom = 96.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) { // clear of Edit at the bottom
            Column(Modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ContactAvatar(i, 120.dp)
                Text(i.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (i.job.isNotEmpty()) Text(i.job, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                // the other apps' rows for these numbers, and the calls with them (both need the phone's permissions)
                var permits by remember { mutableIntStateOf(0) }
                val ways by produceState(emptyList<AppWay>(), i.phones, permits) { value = withContext(Dispatchers.IO) { runCatching { ContactsSystem.appWays(context, i.phones.map { it.value }) }.getOrDefault(emptyList()) } }
                val calls by produceState(emptyList<CallEntry>(), i.phones, permits) { value = withContext(Dispatchers.IO) { runCatching { ContactsSystem.calls(context, i.phones.map { it.value }) }.getOrDefault(emptyList()) } }
                val askCalls = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permits++ }
                fun open(w: AppWay) { runCatching {
                    val i = Intent(Intent.ACTION_VIEW).setDataAndType(android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Data.CONTENT_URI, w.id), w.mime).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(Intent(i).setPackage(ContactsWays.packageOf(w.mime))) } catch (_: android.content.ActivityNotFoundException) { context.startActivity(i) }
                }.onFailure { android.widget.Toast.makeText(context, "${w.app} did not open", android.widget.Toast.LENGTH_SHORT).show() } }
                fun sms(n: String) = go("smsto:${Uri.encode(n)}", Intent.ACTION_SENDTO)
                fun dial(n: String) = go("tel:${Uri.encode(n)}", Intent.ACTION_DIAL)
                fun mail(m: String) = go("mailto:$m", Intent.ACTION_SENDTO)
                /** One way: at once; several (a number's apps, two numbers): a strip from the bottom to choose. */
                fun choose(title: String, options: List<Pair<String, () -> Unit>>) { if (options.size == 1) options.single().second() else if (options.isNotEmpty()) strip = title to options }
                fun numberText(n: String) = i.phones.firstOrNull { it.value == n }?.label?.ifEmpty { null }?.let { "$it $n" } ?: n
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    RoundAction(R.drawable.ic_chat, "Message", i.phones.isNotEmpty()) {
                        choose("Message", i.phones.map { f -> "SMS · ${numberText(f.value)}" to { sms(f.value) } } + ways.filter { it.way == Way.Message }.map { w -> "${w.app} · ${w.number}" to { open(w) } })
                    }
                    RoundAction(R.drawable.ic_call, "Call", i.phones.isNotEmpty()) {
                        choose("Call", i.phones.map { f -> "Phone · ${numberText(f.value)}" to { dial(f.value) } } + ways.filter { it.way == Way.Call }.map { w -> "${w.app} · ${w.number}" to { open(w) } })
                    }
                    RoundAction(R.drawable.ic_videocam, "Video", ways.any { it.way == Way.Video }) {
                        choose("Video", ways.filter { it.way == Way.Video }.map { w -> "${w.app} · ${w.number}" to { open(w) } })
                    }
                    RoundAction(R.drawable.ic_mail, "Mail", i.emails.isNotEmpty()) { choose("Mail", i.emails.map { e -> e.value to { mail(e.value) } }) }
                }
                // Details | History (the iPhone's Voicemail cannot be read by any app but the phone's own: History instead)
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Row(Modifier.padding(4.dp)) {
                        listOf(false, true).forEach { h ->
                            Surface(onClick = { history = h }, shape = CircleShape, color = if (history == h) CONTACTS_COLOR else Color.Transparent,
                                contentColor = if (history == h) Color.White else MaterialTheme.colorScheme.onSurface) {
                                Text(if (h) "History" else "Details", Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
                val canCalls = context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
                if (history) {
                    if (!canCalls) DetailCard {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Calls with ${i.given.ifBlank { i.name }} come from the phone's call log.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { askCalls.launch(Manifest.permission.READ_CALL_LOG) }) { Text("Allow", color = CONTACTS_COLOR) }
                        }
                    } else if (calls.isEmpty()) Hint("No calls with ${i.given.ifBlank { i.name }} in the phone's call log.")
                    else DetailCard { calls.forEach { call -> CallRow(call, i.phones.size > 1) { dial(call.number) } } }
                    val dates = i.birthday?.let { listOf("Birthday" to dateText(it, true)) }.orEmpty() + i.dates.mapNotNull { f -> VDate.parse(f.value)?.let { f.label to dateText(it, false) } }
                    if (dates.isNotEmpty()) DetailCard { dates.forEach { (l, t) -> DetailRow(l, t) } }
                    return@Column
                }
                calls.firstOrNull()?.let { last -> DetailCard {
                    Row(Modifier.fillMaxWidth().clickable { history = true }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Last call", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(callText(last), style = MaterialTheme.typography.bodyLarge)
                        }
                        Text("Call history ›", color = CONTACTS_COLOR)
                    }
                } }
                if (c.versions > 1) DetailCard {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.versions} versions of this contact (a change on two devices). This is the newer.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = keep) { Text("Keep this one") }
                    }
                }
                if (i.phones.isNotEmpty()) DetailCard { i.phones.forEach { f ->
                    // each number's apps as small chips: a tap writes to them there
                    val apps = ways.filter { it.number == f.value }.groupBy { it.app }
                    DetailRow(f.label.ifEmpty { "Phone" }, f.value, onClick = { dial(f.value) }) {
                        apps.forEach { (app, ws) -> Surface(onClick = { open(ws.firstOrNull { it.way == Way.Message } ?: ws.first()) }, shape = CircleShape, color = CONTACTS_COLOR.copy(alpha = 0.2f)) {
                            Text(app, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                        } }
                    }
                } }
                if (i.emails.isNotEmpty()) DetailCard { i.emails.forEach { f -> DetailRow(f.label.ifEmpty { "Email" }, f.value, onClick = { mail(f.value) }) } }
                if (i.birthday != null || i.dates.isNotEmpty()) DetailCard {
                    i.birthday?.let { DetailRow("Birthday", dateText(it, true)) }
                    i.dates.forEach { f -> VDate.parse(f.value)?.let { DetailRow(f.label, dateText(it, false)) } }
                }
                if (i.addresses.isNotEmpty()) DetailCard { i.addresses.forEach { a ->
                    DetailRow(a.label.ifEmpty { "Address" }, a.text(), action = "Open in Maps", onClick = { openMaps(listOf(a.street, a.city).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { a.text() }) })
                } }
                if (i.urls.isNotEmpty()) DetailCard { i.urls.forEach { f -> DetailRow(f.label.ifEmpty { "Website" }, f.value, onClick = { go(if (f.value.contains("://")) f.value else "https://${f.value}") }) } }
                if (i.related.isNotEmpty()) DetailCard { i.related.forEach { f ->
                    val other = all.firstOrNull { it.info.uid != i.uid && ContactsFiles.plain(it.info.name) == ContactsFiles.plain(f.value) }
                    DetailRow(f.label.ifEmpty { "Related" }, f.value, onClick = other?.let { o -> { openUid(o.info.uid) } })
                } }
                if (i.nickname.isNotEmpty() || i.labels.isNotEmpty() || i.note.isNotEmpty()) DetailCard {
                    if (i.nickname.isNotEmpty()) DetailRow("Nickname", i.nickname)
                    if (i.labels.isNotEmpty()) DetailRow("Labels", i.labels.joinToString(" · "))
                    if (i.note.isNotEmpty()) DetailRow("Notes", i.note)
                }
            }
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!wide) Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                IconButton(onClick = close) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Contacts") }
            }
        }
        // the contact's own buttons in one island at the bottom right, within the thumb's reach (the user, 24 Oct: Edit
        // there, then "bring the star, maybe share too"): Share · ☆ · Edit
        Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)) {
            Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                // Share: as a .vcf (Viber, WhatsApp, mail take it) or as a QR code another phone scans
                IconButton(onClick = { strip = "Share ${i.name}" to listOf(
                    "Send as a contact file (.vcf)" to {
                        runCatching {
                            val f = File(File(context.cacheDir, "share").apply { mkdirs() }, ContactsFiles.fileName(i.name, i.uid))
                            f.writeText(ContactsMerge.export(listOf(c)))
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/x-vcard").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share ${i.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.onFailure { WriterLog.e("Contacts: share", it) }
                        Unit
                    },
                    "Show as a QR code" to { qr = true },
                ) }) { Icon(painterResource(R.drawable.ic_share), contentDescription = "Share") }
                IconButton(onClick = star) {
                    Icon(painterResource(if (i.favourite) R.drawable.ic_star else R.drawable.ic_star_border), contentDescription = if (i.favourite) "Remove from favourites" else "Add to favourites",
                        tint = if (i.favourite) CONTACTS_COLOR else islandContentColor())
                }
                Surface(onClick = edit, color = Color.White.copy(alpha = 0.12f), contentColor = islandContentColor(), shape = CircleShape, modifier = Modifier.padding(4.dp)) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = null)
                        Text("Edit")
                    }
                }
            }
        }
    }
    strip?.let { (title, options) -> WaysStrip(title, options) { strip = null } }
    // the card without its photo (a QR code holds ~3 KB), black on white, for a phone's camera or Tetra's Codes
    if (qr) {
        val bmp = remember(c) { runCatching { qrBitmap(ContactsMerge.export(listOf(c), noPhoto = true), 720, utf8 = true).asImageBitmap() }.getOrNull() }
        androidx.compose.ui.window.Dialog(onDismissRequest = { qr = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.White, contentColor = Color.Black) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (bmp != null) Image(bmp, contentDescription = "QR code of ${i.name}", modifier = Modifier.size(300.dp)) else Text("Too much to fit in a QR code: share it as a file.")
                    Text(i.name, style = MaterialTheme.typography.titleMedium)
                    Text("Scan it with a phone's camera, or Tetra › Codes › Add to Contacts.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Message, Call, Video or Mail with several ways: a strip from the bottom (no popup lists on a touch screen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun WaysStrip(title: String, options: List<Pair<String, () -> Unit>>, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = SheetColor, contentColor = islandContentColor()) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            options.forEach { (label, act) ->
                Text(label, Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { dismiss(); act() }.padding(horizontal = 8.dp, vertical = 14.dp))
            }
        }
    }
}

@Composable private fun RoundAction(icon: Int, label: String, enabled: Boolean, click: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(onClick = click, enabled = enabled, shape = CircleShape, color = if (enabled) CONTACTS_COLOR else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), contentDescription = label) }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable private fun DetailCard(content: @Composable () -> Unit) =
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }

@Composable private fun DetailRow(label: String, value: String, action: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null && action == null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = if (onClick != null && action == null) CONTACTS_COLOR else MaterialTheme.colorScheme.onSurface)
        }
        if (action != null && onClick != null) TextButton(onClick = onClick) { Text(action, color = CONTACTS_COLOR) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { trailing() }
    }
}

/** "Mon 15:36 · in · 2 min": a call in a few words. */
private fun callText(c: CallEntry): String {
    val at = java.time.Instant.ofEpochMilli(c.at).atZone(java.time.ZoneId.systemDefault())
    val day = if (at.toLocalDate() == LocalDate.now()) "Today" else if (at.toLocalDate() == LocalDate.now().minusDays(1)) "Yesterday"
        else at.format(DateTimeFormatter.ofPattern(if (at.year == LocalDate.now().year) "EEE d MMM" else "d MMM yyyy", Locale.ENGLISH))
    val kind = when (c.type) {
        android.provider.CallLog.Calls.INCOMING_TYPE -> "in"; android.provider.CallLog.Calls.OUTGOING_TYPE -> "out"
        android.provider.CallLog.Calls.MISSED_TYPE -> "missed"; android.provider.CallLog.Calls.REJECTED_TYPE -> "declined"; else -> "call"
    }
    val length = if (c.seconds <= 0) "" else " · " + if (c.seconds < 60) "${c.seconds} s" else "${(c.seconds + 30) / 60} min"
    return "$day ${at.format(DateTimeFormatter.ofPattern("HH:mm"))} · $kind$length"
}

@Composable private fun CallRow(c: CallEntry, showNumber: Boolean, click: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val missed = c.type == android.provider.CallLog.Calls.MISSED_TYPE || c.type == android.provider.CallLog.Calls.REJECTED_TYPE
        Icon(painterResource(R.drawable.ic_call), contentDescription = null, tint = if (missed) MaterialTheme.colorScheme.error else CONTACTS_COLOR, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(callText(c), color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (showNumber) Text(c.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

/** Contacts' part of Tetra's Trash, newest first, each with Restore. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ContactsTrash(root: File, version: Int, dismiss: () -> Unit, restore: (Contact) -> Unit) {
    val trashed by produceState(emptyList<Contact>(), version) { value = withContext(Dispatchers.IO) { runCatching { ContactsFiles.trashed(root) }.getOrDefault(emptyList()) } }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = SheetColor, contentColor = islandContentColor()) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Trash", style = MaterialTheme.typography.titleMedium)
            if (trashed.isEmpty()) Text("No contacts in the Trash.", color = islandContentColor().copy(alpha = 0.7f))
            trashed.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ContactAvatar(c.info, 36.dp)
                    Text(c.info.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { restore(c) }) { Text("Restore") }
                }
            }
        }
    }
}

/**
 * The events already in the birthdays calendar that seem to be these people (Γενέθλια's from Google Calendar): each pair
 * ticked; Link adds the contact to the event (no second event), an unticked one stays as it is and is not asked again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun LinkReview(links: List<ContactsBirthdays.Link>, later: () -> Unit, go: (List<ContactsBirthdays.Link>) -> Unit) {
    var picked by remember(links) { mutableStateOf(links.toSet()) }
    ModalBottomSheet(onDismissRequest = later, containerColor = SheetColor, contentColor = islandContentColor()) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Birthdays already in the calendar", style = MaterialTheme.typography.titleMedium)
            Text("These events seem to be these people. Linked, the event stays and follows the contact; nothing is doubled.",
                style = MaterialTheme.typography.bodySmall, color = islandContentColor().copy(alpha = 0.7f))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                links.forEach { l ->
                    Row(Modifier.fillMaxWidth().clickable { picked = if (l in picked) picked - l else picked + l }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.Checkbox(l in picked, { picked = if (l in picked) picked - l else picked + l })
                        ContactAvatar(l.contact, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(l.contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${l.title} · ${l.event.master?.start?.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}" + if (l.sameDate) " · same day" else "",
                                style = MaterialTheme.typography.bodySmall, color = islandContentColor().copy(alpha = 0.7f), maxLines = 2)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = later) { Text("Later") }
                TextButton(onClick = { go(picked.toList()) }) { Text(if (picked.isEmpty()) "None of them" else "Link ${picked.size}") }
            }
        }
    }
}

/**
 * The editor's photo: From Photos (the faces Photos found), Choose a picture (any, its middle square), Remove (back to
 * the initial). The photo goes into the file, 512 px.
 */
@Composable private fun ContactPhotoEdit(i: ContactInfo, set: ((ContactInfo) -> ContactInfo) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fromPhotos by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    fun use(uri: Uri, box: android.graphics.Rect?, person: String?) = scope.launch {
        working = true
        val b = withContext(Dispatchers.IO) { ContactsFaces.portrait(context, uri, box) }
        working = false
        // a face from Photos keeps its person; any picture gets the contact's (a new uuid if none): the computer then finds
        // that face in its copy of the picture and puts it in Photos under this contact's name (desktop contactfaces.js)
        if (b != null) set { it.copy(photo = b.first, face = b.second, photoUrl = null, person = person ?: it.person ?: java.util.UUID.randomUUID().toString()) }
        else android.widget.Toast.makeText(context, "That picture could not be read", android.widget.Toast.LENGTH_SHORT).show()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { use(it, null, null) } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ContactAvatar(i, 96.dp)
        if (working) Text("Making the photo…", style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick = { fromPhotos = true }) { Text("From Photos", color = CONTACTS_COLOR) }
            TextButton(onClick = { pick.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Choose a picture", color = CONTACTS_COLOR) }
            if (i.photo != null || i.photoUrl != null) TextButton(onClick = { set { it.copy(photo = null, photoUrl = null, face = null) } }) { Text("Remove", color = CONTACTS_COLOR) }
        }
    }
    if (fromPhotos) PhotosFacePicker(i.name, { fromPhotos = false }) { p, uri, box -> fromPhotos = false; use(uri, box, p.uuid) }
}

/**
 * Merge & fix: the same person twice, side by side, what the second adds marked; Merge keeps every value in one file (the
 * other to the Trash), Not the same remembers it in both files; Merge all at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MergeSheet(pairs: List<Pair<Contact, Contact>>, dismiss: () -> Unit, merge: (List<Pair<Contact, Contact>>) -> Unit, notSame: (Contact, Contact) -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = SheetColor, contentColor = islandContentColor()) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Merge & fix", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (pairs.size > 1) TextButton(onClick = { dismiss(); merge(pairs) }) { Text("Merge all ${pairs.size}") }
            }
            if (pairs.isEmpty()) Text("No one is here twice.", color = islandContentColor().copy(alpha = 0.7f))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pairs.forEach { (a, b) ->
                    Surface(color = Color.White.copy(alpha = 0.08f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(a, b).forEach { c -> Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ContactAvatar(c.info, 36.dp)
                                    Column {
                                        Text(c.info.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text((c.info.phones.map { it.value } + c.info.emails.map { it.value }).take(2).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                            color = islandContentColor().copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                } }
                            }
                            // what the second brings that the first has not (the fields that differ)
                            val m = ContactsMerge.merged(a.info, b.info)
                            val adds = (m.phones - a.info.phones.toSet()).map { it.value } + (m.emails - a.info.emails.toSet()).map { it.value } +
                                listOfNotNull(m.birthday?.takeIf { a.info.birthday == null }?.let { "Birthday ${dateToText(it)}" }, "Photo".takeIf { a.info.photo == null && m.photo != null }) +
                                (m.labels - a.info.labels.toSet())
                            if (adds.isNotEmpty()) Text("Adds: " + adds.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = CONTACTS_COLOR)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { notSame(a, b) }) { Text("Not the same") }
                                TextButton(onClick = { merge(listOf(a to b)) }) { Text("Merge") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Many contacts' label: one of those in use (✓ when all the chosen have it: a tap takes it off), or a new one. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable private fun LabelPick(labels: List<String>, many: List<Contact>, dismiss: () -> Unit, pick: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = SheetColor, contentColor = islandContentColor()) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Label ${many.size} contacts", style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                labels.forEach { l ->
                    val all = many.all { c -> c.info.labels.any { it.equals(l, true) } }
                    androidx.compose.material3.FilterChip(all, { pick(l) }, label = { Text(l) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = CONTACTS_COLOR, selectedLabelColor = Color.White))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("New label") }, modifier = Modifier.weight(1f))
                TextButton(enabled = name.isNotBlank(), onClick = { pick(name.trim()) }) { Text("Add") }
            }
        }
    }
}

/**
 * Settings › Contacts (the user, 24 Oct: "a Contacts card in Settings, permission once"): calls from the computer — one
 * permission for Tetra (Android's "Phone"), not one per contact; without it each call comes as a notification to tap —,
 * the copy in the phone's own contacts, and the calendar birthdays go into.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable internal fun ContactsSettings() {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) { // back from Android's settings: read the states again
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) version++ }
        lifecycle.lifecycle.addObserver(o); onDispose { lifecycle.lifecycle.removeObserver(o) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { version++ }
    val askWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) ContactsAccount.set(context, true); version++ }
    val appDetails = { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    @Suppress("UNUSED_EXPRESSION") version // read again on each change
    val calls = PhoneCall.allowed(context)
    val writes = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val copy = ContactsAccount.on(context) && writes
    @Composable fun row(title: String, state: String, action: String?, act: () -> Unit) =
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(state, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (action != null) TextButton(onClick = act) { Text(action) }
        }
    Text("Your contacts are files in Tetra/Contacts, one per person: they sync with the computer, where Contacts shows them too.", style = MaterialTheme.typography.bodySmall)
    row("Calls from the computer", if (calls) "Allowed: Call on the computer rings the number here at once" else "Each call from the computer comes as a notification: tap it to call",
        if (calls) "Settings" else "Allow") { if (calls) appDetails() else ask.launch(Manifest.permission.CALL_PHONE) }
    if (calls) Text("Once for Tetra, not for each contact. To take it back: Settings › Apps › Tetra › Permissions › Phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    row("In the phone's contacts", if (copy) "On: the dialer, Viber, WhatsApp and the car show these names and photos (a read-only copy, account Tetra)"
        else "Off: only Tetra shows your contacts", if (copy) "Turn off" else "Turn on") {
        when { copy -> { ContactsAccount.set(context, false); version++ }; writes -> { ContactsAccount.set(context, true); version++ }; else -> askWrite.launch(Manifest.permission.WRITE_CONTACTS) }
    }
    val cals = remember(version) { runCatching { CalendarFiles.calendars(calendarRoot()) }.getOrDefault(emptyList()) }
    val chosen = cals.firstOrNull { it.dir.name == ContactsPrefs.birthdays(context) } ?: ContactsBirthdays.existing(cals)
    Text("Birthdays go into", style = MaterialTheme.typography.titleSmall)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cals.forEach { c ->
            androidx.compose.material3.FilterChip(c == chosen, { ContactsPrefs.setBirthdays(context, c.dir.name); version++ }, label = { Text(c.name) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = CONTACTS_COLOR, selectedLabelColor = Color.White))
        }
    }
    Text("Each birthday and date is a yearly event there, reminded at 9:00 on the day and the day before.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
