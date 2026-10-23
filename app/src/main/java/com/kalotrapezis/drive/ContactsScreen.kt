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
}

private enum class ContactsTab(val label: String, val icon: Int) { All("All", R.drawable.ic_person), Favourites("Favourites", R.drawable.ic_star), Labels("Labels", R.drawable.ic_label) }

/**
 * Contacts (Plan.md § Contacts): the list (search on top, favourites' faces, letters), a contact's page as the iPhone's
 * made Tetra's, Tetra's island at the bottom (All · Favourites · Labels, + at the right) and its drawer (Import, Trash,
 * Settings). A wide screen has the list at the left and the open contact beside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactsScreen(back: () -> Unit, hasAccess: Boolean, grant: () -> Unit, start: String? = null) {
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

    /** Runs [work] on the IO thread, says what it answers, and reads the folder again. */
    fun act(work: () -> String) = scope.launch {
        val r = withContext(Dispatchers.IO) { runCatching(work) }
        message = r.getOrElse { "Not done: ${it.message ?: it.javaClass.simpleName}" }
        r.exceptionOrNull()?.let { WriterLog.e("Contacts: a change failed", it) }
        version++
    }
    /** [c] (null: a new contact) saved as [i]; the page opens on it. */
    fun save(c: Contact?, i: ContactInfo, said: String = "Saved") = scope.launch {
        val r = withContext(Dispatchers.IO) { runCatching { ContactsFiles.save(root, c, i) } }
        r.onSuccess { openUid = it.info.uid; message = said }.onFailure { message = "Not saved: ${it.message}"; WriterLog.e("Contacts: save", it) }
        version++
    }
    fun imported(r: ContactsFiles.Imported) = "Imported: ${r.added} new" + if (r.same > 0) ", ${r.same} already here" else ""
    // Import: the phone's own contacts (the Google account's among them), or a file — vCard, Google's CSV, Takeout's zip
    fun importPhone() = scope.launch {
        busy = "Reading the phone's contacts…"
        val r = withContext(Dispatchers.IO) { runCatching { ContactsFiles.import(root, ContactsSystem.phoneCards(context) { n, of -> busy = "Reading the phone's contacts… $n of $of" }) } }
        busy = null
        message = r.fold(::imported) { "Could not read them: ${it.message}" }
        r.exceptionOrNull()?.let { WriterLog.e("Contacts: phone import", it) }
        version++
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
        version++
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::importFile) }

    val shown = remember(all, query, tab, label) {
        all.filter { c -> ContactsFiles.matches(c.info, query) && when {
            tab == ContactsTab.Favourites -> c.info.favourite
            tab == ContactsTab.Labels && label != null -> c.info.labels.any { it.equals(label, true) }
            else -> true
        } }
    }
    BackHandler(editing == null) { when { drawer -> drawer = false; !wide && open != null -> openUid = null; label != null -> label = null; query.isNotEmpty() -> query = ""; else -> back() } }

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
                        val chosen = wide && c.info.uid == openUid
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(MaterialTheme.shapes.large)
                            .background(if (chosen) CONTACTS_COLOR.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable { openUid = c.info.uid }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ContactAvatar(c.info, 40.dp)
                            Column(Modifier.weight(1f)) {
                                Text(c.info.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (query.isNotEmpty()) (c.info.phones.firstOrNull()?.value ?: c.info.emails.firstOrNull()?.value)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
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
                delete = ed.first?.let { c -> { editing = null; openUid = null; act { ContactsFiles.trash(root, c); "${c.info.name} moved to the Trash" } } },
                photo = { i, _ -> ContactAvatar(i, 96.dp) }) { i -> editing = null; save(ed.first, i, if (ed.first == null) "Contact created" else "Saved") }
            else if (open != null) ContactPage(open, all, wide, close = { openUid = null }, edit = { editing = open to open.info },
                star = { save(open, open.info.favourite(!open.info.favourite), if (open.info.favourite) "Not a favourite" else "Added to favourites") }, keep = { act { ContactsFiles.copies(root, open).forEach { f -> DriveRules.moveToTrash(root.parentFile!!, f.relativeTo(root.parentFile!!).path) }; "Kept this version; the other went to the Trash" } },
                openUid = { openUid = it })
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
                IslandBottomBar(expand = { drawer = true }, visible = !drawer) {
                    ContactsTab.entries.forEach { t -> IslandNavigationItem(t.label, tab == t, t.icon) { tab = t; label = null } }
                }
                IslandVisibility(!drawer) {
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
                DrawerButton(R.drawable.ic_sort_name, if (byLast) "Sort: last name" else "Sort: first name", byLast, Modifier.weight(1f)) {
                    byLast = !byLast; ContactsPrefs.setByLast(context, byLast)
                }
                DrawerButton(R.drawable.ic_delete, "Trash", false, Modifier.weight(1f)) { drawer = false; trashOpen = true }
            }
        }
    }
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
        Text(i.name.trim().firstOrNull()?.uppercase() ?: "?", color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Medium)
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
private fun ContactPage(c: Contact, all: List<Contact>, wide: Boolean, close: () -> Unit, edit: () -> Unit, star: () -> Unit, keep: () -> Unit, openUid: (String) -> Unit) {
    val context = LocalContext.current
    val i = c.info
    fun go(uri: String, action: String = Intent.ACTION_VIEW) = runCatching { context.startActivity(Intent(action, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { android.widget.Toast.makeText(context, "No app opens this", android.widget.Toast.LENGTH_SHORT).show() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(top = 72.dp, bottom = 32.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ContactAvatar(i, 120.dp)
                Text(i.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (i.job.isNotEmpty()) Text(i.job, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                val phone = i.phones.firstOrNull()?.value
                val mail = i.emails.firstOrNull()?.value
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    RoundAction(R.drawable.ic_chat, "Message", phone != null) { go("smsto:${Uri.encode(phone)}", Intent.ACTION_SENDTO) }
                    RoundAction(R.drawable.ic_call, "Call", phone != null) { go("tel:${Uri.encode(phone)}", Intent.ACTION_DIAL) }
                    RoundAction(R.drawable.ic_videocam, "Video", false) {}
                    RoundAction(R.drawable.ic_mail, "Mail", mail != null) { go("mailto:$mail", Intent.ACTION_SENDTO) }
                }
                if (c.versions > 1) DetailCard {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.versions} versions of this contact (a change on two devices). This is the newer.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = keep) { Text("Keep this one") }
                    }
                }
                if (i.phones.isNotEmpty()) DetailCard { i.phones.forEach { f -> DetailRow(f.label.ifEmpty { "Phone" }, f.value, onClick = { go("tel:${Uri.encode(f.value)}", Intent.ACTION_DIAL) }) } }
                if (i.emails.isNotEmpty()) DetailCard { i.emails.forEach { f -> DetailRow(f.label.ifEmpty { "Email" }, f.value, onClick = { go("mailto:${f.value}", Intent.ACTION_SENDTO) }) } }
                if (i.birthday != null || i.dates.isNotEmpty()) DetailCard {
                    i.birthday?.let { DetailRow("Birthday", dateText(it, true)) }
                    i.dates.forEach { f -> VDate.parse(f.value)?.let { DetailRow(f.label, dateText(it, false)) } }
                }
                if (i.addresses.isNotEmpty()) DetailCard { i.addresses.forEach { a ->
                    DetailRow(a.label.ifEmpty { "Address" }, a.text(), action = "Open in Maps") { go("geo:0,0?q=${Uri.encode(a.text())}") }
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
            Spacer(Modifier.weight(1f))
            Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = star) {
                        Icon(painterResource(if (i.favourite) R.drawable.ic_star else R.drawable.ic_star_border), contentDescription = if (i.favourite) "Remove from favourites" else "Add to favourites",
                            tint = if (i.favourite) CONTACTS_COLOR else islandContentColor())
                    }
                    TextButton(onClick = edit) { Text("Edit", color = islandContentColor()) }
                }
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

@Composable private fun DetailRow(label: String, value: String, action: String? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null && action == null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = if (onClick != null && action == null) CONTACTS_COLOR else MaterialTheme.colorScheme.onSurface)
        }
        if (action != null && onClick != null) TextButton(onClick = onClick) { Text(action, color = CONTACTS_COLOR) }
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
