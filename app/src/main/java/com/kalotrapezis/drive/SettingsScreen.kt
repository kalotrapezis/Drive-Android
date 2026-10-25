package com.kalotrapezis.drive

import androidx.compose.ui.res.pluralStringResource
import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings, re-arranged (the user, 24 Oct: "re-arrange it, modernise it … it does not separate a setting from text"):
 * a list of categories, each its own page — side by side on a wide screen, one at a time on a phone. Inside a page,
 * sections under quiet headers, one row per setting (title, a line of secondary text, the control at the end) and the
 * longer explanations under their section. One accent (the theme's own); an applet's colour only tints its icon in
 * the list. [items] are the rows' titles, for the search.
 */
internal enum class SettingsPage(val title: String, val icon: Int, val tint: Color?, val summary: String, val items: List<String>) {
    Sync("Sync", R.drawable.ic_sync, null, "Computer, automatic sync", listOf("Computer", "Open Sync", "Sync automatically", "Listen in the background")),
    Appearance("Appearance", R.drawable.ic_palette, null, "Theme, Home, app icons", listOf("Theme", "Language", "Photo on Home", "Hidden from Home", "App icons")),
    Gallery("Gallery", Category.Gallery.icon, Category.Gallery.color, "Access, search, folders", listOf("Photos and videos", "Search quality", "Folders")),
    Files("Files", Category.Files.icon, Category.Files.color, "Workspace, access", listOf("Drive workspace", "All files access")),
    Scanner("PDF scanner", Category.Scanner.icon, Category.Scanner.color, "Resolution", listOf("Resolution")),
    Office("Office", Category.Office.icon, Category.Office.color, "Opening files, editing, ribbon", listOf("Open Word documents here", "Open spreadsheets here", "Open presentations here",
        "Open PDFs here", "AutoSave", "Lengths in", "Read aloud speed", "The ribbon", "Its tools", "Tab marker")),
    Text("Text editor", Category.Text.icon, Category.Text.color, "Opening .txt files, word wrap", listOf("Open .txt files here", "Word wrap", "Save new files in")),
    Calendar("Calendar", Category.Calendar.icon, Category.Calendar.color, "Reminders, import and export", listOf("Reminders on this device", "Send a test reminder", "Notifications",
        "Alarms on time", "Battery", "Hyper Island", "Autostart", "Import", "Export all", "Share with Claude")),
    Timer("Timer", Category.Timer.icon, Category.Timer.color, "Share with Claude", listOf("Share with Claude")),
    Maps("Maps", Category.Maps.icon, Category.Maps.color, "Map, directions, parking, places", listOf("Map", "On foot", "By car", "Directions in", "Test voice", "Voice settings", "Warn of sharp bends",
        "Save where I park", "Open the map at my car", "ΑμεΑ parking near the destination", "Export places", "Import places", "Import from Google Maps",
        "Installed maps", "Send log")),
    Contacts("Contacts", Category.Contacts.icon, Category.Contacts.color, "Phone's contacts, birthdays", listOf("In the phone's contacts", "Birthdays go into")),
    Phone("Phone", Category.Phone.icon, Category.Phone.color, "Phone app, calls, the computer", listOf("Phone app", "Vibrate when answered", "Vibrate when a call ends",
        "Silence numbers not in Contacts", "Decline hidden numbers", "Calls on the computer", "Phone permission", "Open the Phone")),
    Data("Data", Category.Data.icon, Category.Data.color, "Your plan, warnings, roaming, live speed", listOf("Setup", "Usage access", "Your plan", "Warnings", "Roaming",
        "Live speed", "Signal drops", "Wi-Fi around", "Open Data")),
}

@Composable
internal fun SettingsTab(
    back: () -> Unit,
    metadataStore: PhotoMetadataStore,
    photoPermission: String,
    hasDriveAccess: Boolean,
    showPhotoBackdrop: Boolean,
    setShowPhotoBackdrop: (Boolean) -> Unit,
    managePhotos: () -> Unit,
    manageDrive: () -> Unit,
    openSync: () -> Unit,
    pairedDevice: Pairing?,
    forgetPairedDevice: () -> Unit,
    folders: List<DeviceFolder>,
    setFolderIncluded: (String, Boolean) -> Unit,
    openPart: (String) -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    var open by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var query by remember { mutableStateOf("") }
    val shown = open ?: if (wide) SettingsPage.Sync else null
    BackHandler { when { !wide && open != null -> open = null; query.isNotEmpty() -> query = ""; else -> back() } }

    val list: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                    IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back to Home") }
                }
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape, modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = null, Modifier.size(22.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Search settings", color = islandContentColor().copy(alpha = 0.7f), maxLines = 1)
                            BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = islandContentColor(), fontSize = 16.sp),
                                cursorBrush = SolidColor(islandContentColor()), modifier = Modifier.fillMaxWidth())
                        }
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear") }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                @Composable fun PageRow(p: SettingsPage, summary: String) = SettingRow(p.title, summary, onClick = { open = p; query = "" },
                    chosen = wide && p == shown, leading = { Icon(painterResource(p.icon), contentDescription = null, tint = p.tint ?: quiet(), modifier = Modifier.size(24.dp)) },
                ) { if (!wide) Chevron() }
                if (query.isBlank()) {
                    SettingsSection("Tetra") { listOf(SettingsPage.Sync, SettingsPage.Appearance).forEach { PageRow(it, it.summary) } }
                    SettingsSection("Apps") { SettingsPage.entries.drop(2).forEach { PageRow(it, it.summary) } }
                } else {
                    val q = query.trim().lowercase()
                    val hits = SettingsPage.entries.flatMap { p -> p.items.filter { q in it.lowercase() || q in p.title.lowercase() }.map { p to it } }
                    SettingsSection("${hits.size} found") { hits.forEach { (p, item) ->
                        SettingRow(item, p.title, onClick = { open = p; query = "" },
                            leading = { Icon(painterResource(p.icon), contentDescription = null, tint = p.tint ?: quiet(), modifier = Modifier.size(24.dp)) }) { Chevron() }
                    } }
                }
            }
        }
    }
    val page: @Composable (SettingsPage, Modifier) -> Unit = { p, m ->
        Column(m.statusBarsPadding()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!wide) Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                    IconButton(onClick = { open = null }) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back to Settings") }
                }
                Icon(painterResource(p.icon), contentDescription = null, tint = p.tint ?: MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(28.dp))
                Text(p.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Column(Modifier.widthIn(max = 760.dp)) { when (p) {
                    SettingsPage.Sync -> SyncPage(pairedDevice, forgetPairedDevice, openSync)
                    SettingsPage.Appearance -> AppearancePage(showPhotoBackdrop, setShowPhotoBackdrop, openPart)
                    SettingsPage.Gallery -> GalleryPage(photoPermission, managePhotos, metadataStore, folders, setFolderIncluded)
                    SettingsPage.Files -> FilesPage(hasDriveAccess, manageDrive)
                    SettingsPage.Scanner -> ScannerPage()
                    SettingsPage.Office -> OfficePage()
                    SettingsPage.Text -> TextEditorPage()
                    SettingsPage.Calendar -> CalendarPage()
                    SettingsPage.Timer -> SettingsSection("Claude") { SkillRows("tetra-timer", "Timer", "Lets Claude start, pause and stop your timers by editing their files.") }
                    SettingsPage.Maps -> MapsPage()
                    SettingsPage.Contacts -> ContactsPage()
                    SettingsPage.Phone -> PhonePage()
                    SettingsPage.Data -> DataPage()
                } }
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (wide) Row(Modifier.fillMaxSize()) {
            list(Modifier.width(380.dp).fillMaxHeight())
            shown?.let { key(it) { page(it, Modifier.weight(1f).fillMaxHeight()) } }
        } else if (shown != null) key(shown) { page(shown, Modifier.fillMaxSize()) } else list(Modifier.fillMaxSize())
    }
}

// ---- the parts every page is made of ----

/** Secondary text: the content colour, quieter (the dark theme's onSurfaceVariant is as bright as onSurface). */
@Composable private fun quiet() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.66f)
/** A row's own ground, a step off the page's; the gaps between rows show the page. */
@Composable private fun rowColor() = lookTint(Color(0xFF1C2221), Color(0xFFEAEFED))

/** A section: a quiet header, its rows in one rounded group, and what they need explained under it. */
@Composable private fun SettingsSection(title: String? = null, note: String? = null, content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        title?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = quiet(), modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = quiet(), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) }
    }

/** One setting: its title, a line about it (in the error colour when [warn]), and its control at the end. */
@Composable private fun SettingRow(title: String, summary: String? = null, onClick: (() -> Unit)? = null, warn: Boolean = false, chosen: Boolean = false,
    leading: (@Composable () -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) =
    Row(Modifier.fillMaxWidth().background(if (chosen) MaterialTheme.colorScheme.primaryContainer else rowColor())
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).heightIn(min = 60.dp)
        .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (warn) MaterialTheme.colorScheme.error else quiet()) }
        }
        trailing()
    }

@Composable private fun SwitchRow(title: String, summary: String?, on: Boolean, set: (Boolean) -> Unit) =
    SettingRow(title, summary, onClick = { set(!on) }) { Switch(checked = on, onCheckedChange = set) }

/** A setting of a few values: the chosen one at the end; a tap lists them all. */
@Composable private fun <T> ChoiceRow(title: String, summary: String?, options: List<Pair<T, String>>, chosen: T, pick: (T) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    SettingRow(title, summary, onClick = { menu = true }) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(options.firstOrNull { it.first == chosen }?.second.orEmpty(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(painterResource(R.drawable.ic_unfold_more), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; pick(value) },
                        trailingIcon = if (value == chosen) { { Icon(painterResource(R.drawable.ic_check), contentDescription = "Chosen", modifier = Modifier.size(18.dp)) } } else null)
                }
            }
        }
    }
}

/** A setting that is a permission or a state elsewhere: how it stands, and a button to it. */
@Composable private fun StatusRow(title: String, state: String, ok: Boolean, action: String?, act: () -> Unit) =
    SettingRow(title, state, warn = !ok) { if (action != null) TextButton(onClick = act) { Text(action) } }

@Composable private fun Chevron() = Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = quiet(), modifier = Modifier.size(20.dp))

/** A counter that goes up each time the screen comes back (from Android's settings): states read again. */
@Composable private fun resumes(): MutableIntState {
    val n = remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) n.intValue++ }
        lifecycle.lifecycle.addObserver(o); onDispose { lifecycle.lifecycle.removeObserver(o) }
    }
    return n
}

/** Share with Claude, as two rows: the skill to the share sheet, or saved in Files. */
@Composable private fun SkillRows(skill: String, part: String, what: String) {
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)!!.use { it.write(skillZip(context, skill)) } }
    }
    SettingRow("Share with Claude", what, onClick = { shareSkill(context, skill, part) }) { Icon(painterResource(R.drawable.ic_share), contentDescription = null, tint = quiet(), modifier = Modifier.size(20.dp)) }
    SettingRow("Save to Files", "The skill as a .zip", onClick = { save.launch("$skill.zip") })
}

// ---- the pages ----

@Composable private fun SyncPage(paired: Pairing?, forget: () -> Unit, openSync: () -> Unit) {
    val c = LocalContext.current
    SettingsSection("Computer", "A trusted device is known by a public-key fingerprint, not its IP or MAC address, so it is found again after a network change.") {
        SettingRow(paired?.name ?: "No computer is paired yet", paired?.hosts?.firstOrNull()) { if (paired != null) TextButton(onClick = forget) { Text("Forget") } }
        SettingRow("Open Sync", "Pairing, syncing now and the library", onClick = openSync) { Chevron() }
    }
    SettingsSection("Automatic", "Android may run a sync a little later to save battery.") {
        var every by remember { mutableStateOf(SyncJob.minutes(c)) }
        ChoiceRow("Sync automatically", "With Tetra closed too, on any local network, a phone's hotspot included", SyncJob.choices, every) { every = it; SyncJob.setMinutes(c, it) }
        var listen by remember { mutableStateOf(SyncListenService.enabled(c)) }
        SwitchRow("Listen in the background",
            if (listen) "With Tetra closed, the computer's syncs arrive at once and its Maps can ask where this device is; a small notification shows"
            else "Only while Tetra is open does the computer reach this device; otherwise it syncs on the schedule", listen) { listen = it; SyncListenService.setEnabled(c, it) }
    }
}

@Composable private fun AppearancePage(photoBackdrop: Boolean, setPhotoBackdrop: (Boolean) -> Unit, open: (String) -> Unit) {
    val context = LocalContext.current
    SettingsSection(null, "The same on every page; it does not follow the system or the wallpaper's colours. Maps' map has its own choice (Settings › Maps).") {
        ChoiceRow("Theme", "Dark or light", listOf(false to "Dark", true to "Light"), TetraLook.light) { TetraLook.set(context, it) }
    }
    SettingsSection(null, stringResource(R.string.settings_language_note)) {
        var language by remember { mutableStateOf(AppLocale.get(context)) }
        ChoiceRow(stringResource(R.string.settings_language), stringResource(R.string.settings_language_summary),
            listOf("" to stringResource(R.string.settings_language_system)) + AppLocale.tags.map { it to AppLocale.name(it) }, language) { language = it; AppLocale.set(context, it) }
    }
    // what is hidden from Home on this device, each still opened here or shown again
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    var layout by remember(wide) { mutableStateOf(HomeLayout.load(context, wide)) }
    val hidden = layout.filter { it.hidden }
    SettingsSection("Home", if (hidden.isEmpty()) null else "Hidden parts keep working (reminders, timers, sync).") {
        SwitchRow("Photo on Home", "A random gallery photo behind the Photos card", photoBackdrop, setPhotoBackdrop)
        if (hidden.isEmpty()) SettingRow("Hidden from Home", "Nothing is hidden. On Home, hold a part to move it; the pencil resizes and hides parts; this device only.")
        hidden.forEach { item ->
            SettingRow(HomeLayout.NAMES[item.id] ?: item.id, "Hidden from Home on this device") {
                if (item.id == "scanner") { TextButton({ open("scanner") }) { Text("Scanner") }; TextButton({ open("codes") }) { Text("Codes") } }
                else TextButton({ open(item.id) }) { Text("Open") }
                TextButton({ layout = HomeLayout.change(layout, item.copy(hidden = false, col = -1, row = -1), HomeLayout.columns(wide)); HomeLayout.save(context, wide, layout) }) { Text("Show") }
            }
        }
    }
    // a launcher icon for each part (asked 17 Oct): on the home screen and in the app list, opening straight into it
    SettingsSection("App icons", "Give a part of Tetra its own icon on the home screen and in the app list; it opens straight into that part.") {
        Applet.entries.forEach { a ->
            var on by remember { mutableStateOf(a.shown(context)) }
            SettingRow(a.label, onClick = { on = !on; a.show(context, on) }, leading = { Image(painterResource(a.art), contentDescription = null, modifier = Modifier.size(32.dp)) }) {
                Switch(checked = on, onCheckedChange = { on = it; a.show(context, it) })
            }
        }
    }
}

@Composable private fun GalleryPage(permission: String, manage: () -> Unit, store: PhotoMetadataStore, folders: List<DeviceFolder>, setIncluded: (String, Boolean) -> Unit) {
    SettingsSection("Access") { StatusRow("Photos and videos", permission, !permission.endsWith("not granted"), "Settings", manage) }
    var quality by remember { mutableStateOf(store.searchQuality()) }
    SettingsSection("Search", "Changing this re-analyzes photos the next time you open People or Documents. Advanced keeps the Fast tags too.") {
        ChoiceRow("Search quality", if (quality == PhotoSearchQuality.Fast) "Quick local labels" else "Fast labels plus experimental Scene tags; slower",
            listOf(PhotoSearchQuality.Fast to "Fast", PhotoSearchQuality.Advanced to "Advanced"), quality) { quality = it; store.setSearchQuality(it) }
    }
    // every folder you have answered about, while the phone can still see it; a new one asks first, in Help organize
    SettingsSection("Folders", "Camera, Screenshots and photos from the computer are always in. An included folder shows in Gallery, is backed up, and is kept as a collection.") {
        val answered = folders.filter { it.included != null }
        answered.forEach { f -> SwitchRow(f.name, f.countText, f.included == true) { setIncluded(f.name, it) } }
        if (answered.isEmpty()) SettingRow("No other folders yet", "New ones are asked about in Help organize")
    }
}

@Composable private fun FilesPage(access: Boolean, manage: () -> Unit) {
    SettingsSection("Storage", "Camera photos stay in DCIM/Camera and screenshots in Pictures/Screenshots. These paths are fixed so sync never needs to guess or duplicate photos.") {
        SettingRow("Drive workspace", DRIVE_PATH)
    }
    SettingsSection("Access") { StatusRow("All files access", if (access) "Granted" else "Not granted", access, "Settings", manage) }
}

@Composable private fun ScannerPage() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("scanner", Context.MODE_PRIVATE) }
    var dpi by remember { mutableStateOf(prefs.getInt(SCANNER_DPI, 300)) }
    SettingsSection("Saved pages", "300 dpi is what a flatbed scanner gives and what small print needs; 200 makes a file roughly half the size, plenty for a page you only need to read.") {
        ChoiceRow("Resolution", "How much detail a saved page keeps", listOf(300 to "300 dpi", 200 to "200 dpi"), dpi) { dpi = it; prefs.edit().putInt(SCANNER_DPI, it).apply() }
    }
}

@Composable private fun OfficePage() {
    val context = LocalContext.current
    val office = remember { TextEditorStore(context) }
    var opens by remember { mutableStateOf(office.opensOffice) }
    var sheets by remember { mutableStateOf(office.opensSheets) }
    var slides by remember { mutableStateOf(office.opensSlides) }
    var pdfs by remember { mutableStateOf(office.opensPdfs) }
    // Tetra in other apps' Open with (asked 16 Oct): Android's own Always there makes it the default
    SettingsSection("Open from Files", "Open with… still offers other apps. Other apps offer Tetra Office in their Open with too; choose Always there to open these files here every time.") {
        SwitchRow("Open Word documents here", if (opens) "A .docx or .odt opens in Words" else "A .docx or .odt opens in the app you chose for it", opens) { opens = it; office.opensOffice = it }
        SwitchRow("Open spreadsheets here", if (sheets) "An .xlsx, .ods, .csv or .xls opens in Tables" else "A spreadsheet opens in the app you chose for it", sheets) { sheets = it; office.opensSheets = it }
        SwitchRow("Open presentations here", if (slides) "A .pptx or .odp opens in Slides" else "A presentation opens in the app you chose for it", slides) { slides = it; office.opensSlides = it }
        SwitchRow("Open PDFs here", if (pdfs) "A .pdf opens in Office's PDF viewer" else "A PDF opens in the app you chose for it", pdfs) { pdfs = it; office.opensPdfs = it }
    }
    SettingsSection("Editing") {
        var auto by remember { mutableStateOf(office.autoSave) }
        SwitchRow("AutoSave", if (auto) "Saves a few seconds after you stop typing; the file as you opened it stays in Versions"
            else "Saves only when you press Save; changes are still kept as a recovery draft", auto) { auto = it; office.autoSave = it }
        LaunchedEffect(Unit) { WriterUnits.load(context) }
        ChoiceRow("Lengths in", "Words' rulers, margins, indents and sizes", listOf(false to "Centimetres", true to "Inches"), WriterUnits.inches) { WriterUnits.set(context, it) }
        var speed by remember { mutableStateOf(office.readAloudSpeed) }
        ChoiceRow("Read aloud speed", "Review › Read aloud; 1× is normal", listOf(0.75f to "0.75×", 1f to "1×", 1.25f to "1.25×", 1.5f to "1.5×", 2f to "2×"), speed) { speed = it; office.readAloudSpeed = it }
    }
    // the ribbon's place and size for Words, Tables and Slides (asked 16 Oct: one Office section; File › Settings opens the same)
    SettingsSection("Ribbon", "By itself: at the top and smaller while a mouse is in use, as on a computer; at the bottom, in reach of a hand, when you touch. Compact tools always use the dash.") {
        ChoiceRow("The ribbon", "Where it stands", listOf("auto" to "By itself", "bottom" to "Bottom", "top" to "Top"), OfficeUi.place) { OfficeUi.set(context, place = it) }
        ChoiceRow("Its tools", "How big they are", listOf("auto" to "By itself", "normal" to "Normal", "compact" to "Compact"), OfficeUi.size) { OfficeUi.set(context, size = it) }
        ChoiceRow("Tab marker", "A bubble behind the chosen tab or a dash under it", listOf("bubble" to "Bubble", "dash" to "Dash"), OfficeUi.marker) { OfficeUi.set(context, marker = it) }
    }
}

@Composable private fun TextEditorPage() {
    val context = LocalContext.current
    val editor = remember { TextEditorStore(context) }
    var txt by remember { mutableStateOf(editor.opensTxt) }
    var wrap by remember { mutableStateOf(editor.wordWrap) }
    var folder by remember { mutableStateOf(editor.saveFolder) }
    SettingsSection("Open from Files", "Open with… still offers other apps.") {
        SwitchRow("Open .txt files here", if (txt) "A .txt opens in the Text editor" else "A .txt opens in the app you chose for it", txt) { txt = it; editor.opensTxt = it }
    }
    SettingsSection("Editing") {
        SwitchRow("Word wrap", if (wrap) "Long lines continue on the next line" else "Long lines keep going; scroll sideways, like code", wrap) { wrap = it; editor.wordWrap = it }
        Column(Modifier.fillMaxWidth().background(rowColor()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Save new files in", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(folder, { folder = it; editor.saveFolder = it }, singleLine = true, prefix = { Text("/sdcard/") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Settings › Calendar (the user, 18 Oct: "We need more permisions in the settings. A calendar section with the import
 * expot"): each permission reminders need, its state and a button to it; the reminders switch; Import and Export all.
 */
@Composable private fun CalendarPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version = resumes()
    fun say(s: String) = Toast.makeText(context, s, Toast.LENGTH_LONG).show()
    fun open(i: Intent) = runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    val pkg = Uri.parse("package:${context.packageName}")
    val appDetails = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
    val notificationSettings = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { version.intValue++ }
    @Suppress("UNUSED_EXPRESSION") version.intValue // read again on each change
    val notifications = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exact = android.os.Build.VERSION.SDK_INT < 31 || context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
    val battery = context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    val xiaomi = android.os.Build.MANUFACTURER.equals("Xiaomi", true)
    val island = remember(version.intValue) { HyperIsland.isSupported(context) }
    SettingsSection("Reminders", "Every device reminds; this switch turns it off on this one only.") {
        var on by remember { mutableStateOf(CalendarAlarms.enabled(context)) }
        SwitchRow("Reminders on this device", if (on) "On" else "Off", on) { on = it; CalendarAlarms.setEnabled(context, it) }
        SettingRow("Send a test reminder", "In 5 seconds: it pops up over Tetra here; on the home screen, in the island", onClick = { CalendarAlarms.test(context); say("A test reminder comes in 5 seconds") })
    }
    SettingsSection("What reminders need") {
        StatusRow("Notifications", if (notifications) "Allowed: reminders show" else "Off: reminders will not show", notifications, if (notifications) "Settings" else "Allow") {
            if (!notifications && android.os.Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else open(notificationSettings)
        }
        StatusRow("Alarms on time", if (exact) "Allowed: reminders ring at their minute" else "Not allowed: reminders may come a few minutes late", exact, if (exact) null else "Allow") {
            if (android.os.Build.VERSION.SDK_INT >= 31) open(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
        }
        StatusRow("Battery", if (battery) "No restrictions: reminders ring on time" else "Restricted: a reminder can be held back", battery, "Settings") {
            open(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) || open(appDetails)
        }
        if (xiaomi) {
            StatusRow("Hyper Island", if (island) "On: a reminder shows in the island, with ✕ for done" else "Off: turn on Tetra's focus notifications", island, "Settings") { open(notificationSettings) }
            StatusRow("Autostart", "Needed for reminders after a restart (Xiaomi; Android does not tell apps whether it is on)", true, "Settings") {
                open(Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")) || open(appDetails)
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { say(withContext(Dispatchers.IO) { runCatching { importCalendars(context, uri) }.getOrElse { "Not imported: ${it.message}" } }) }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch { say(withContext(Dispatchers.IO) { runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(CalendarTransfer.exportAll(CalendarFiles.calendars(calendarRoot()))) }; "Exported"
        }.getOrElse { "Not exported: ${it.message}" } }) }
    }
    SettingsSection("Your calendars", "Your calendars are the folders in Tetra/Calendar: they sync with the computer, and Claude can manage them there.") {
        SettingRow("Import", "An .ics file or Google Takeout's zip; each calendar into the one of its name, an event already there updated",
            onClick = { pick.launch(arrayOf("text/calendar", "application/zip", "application/octet-stream", "*/*")) })
        SettingRow("Export all", "Every calendar as an .ics, in a zip", onClick = { save.launch("Tetra calendars.zip") })
    }
    SettingsSection("Claude") { SkillRows("tetra-calendar", "Calendar", "Lets Claude read and change this calendar by editing its files.") }
}

/** Settings › Maps (21 Oct): what is installed, where updates come from, the directions' language and voice, parking, places. */
@Composable private fun MapsPage() {
    val mapsContext = LocalContext.current
    var mapTheme by remember { mutableStateOf(MapsPrefs.mapTheme(mapsContext)) }
    var rolling by remember { mutableStateOf(MapsPrefs.rolling(mapsContext)) }
    SettingsSection("Map") {
        ChoiceRow("Map", "Its own light or dark, apart from Tetra's theme", listOf("light" to "Light", "dark" to "Dark"), mapTheme) { mapTheme = it; MapsPrefs.setMapTheme(mapsContext, it) }
        ChoiceRow("On foot", if (rolling) "Rolling: routes for a wheelchair, without steps or rough paths, and its icon" else "Walking: every footpath and step",
            listOf(false to "Walking", true to "Rolling"), rolling) { rolling = it; MapsPrefs.setRolling(mapsContext, it) }
        var mainRoads by remember { mutableStateOf(MapsPrefs.mainRoads(mapsContext)) }
        ChoiceRow("By car", if (mainRoads) "Main roads: the bigger roads; tracks and small country roads only when there is no other way, even if a little longer"
            else "Shortest: the shorter way, even on tracks and small roads",
            listOf(true to "Main roads", false to "Shortest"), mainRoads) { mainRoads = it; MapsPrefs.setMainRoads(mapsContext, it) }
    }
    var lang by remember { mutableStateOf(MapsPrefs.lang(mapsContext)) }
    var bends by remember { mutableStateOf(MapsPrefs.bends(mapsContext)) }
    SettingsSection("Directions", "The voice is Android's text-to-speech; its voices are in Android's settings.") {
        ChoiceRow("Directions in", "The words on the screen and the voice", listOf("el" to "Greek", "en" to "English"), lang) { lang = it; MapsPrefs.setLang(mapsContext, it) }
        SettingRow("Test voice", MapsPrefs.sample(lang), onClick = {
            var t: android.speech.tts.TextToSpeech? = null
            t = android.speech.tts.TextToSpeech(mapsContext.applicationContext) { ok ->
                if (ok == android.speech.tts.TextToSpeech.SUCCESS) {
                    val r = t?.setLanguage(MapsPrefs.ttsLocale(lang)) ?: -2
                    if (r < 0) Toast.makeText(mapsContext, "No ${if (lang == "en") "English" else "Greek"} voice: add it in Android's voice settings.", Toast.LENGTH_LONG).show()
                    else t?.speak(MapsPrefs.sample(lang), android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "test")
                }
            }
        })
        SettingRow("Voice settings", "Android's text-to-speech", onClick = { runCatching { mapsContext.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Chevron() }
        SwitchRow("Warn of sharp bends", "By car, \"Sharp bend to the right ahead\" a little before each tight curve where no turn is said, as on mountain roads", bends) { bends = it; MapsPrefs.setBends(mapsContext, it) }
    }
    var autoPark by remember { mutableStateOf(MapsPrefs.autoPark(mapsContext)) }
    var openAtCar by remember { mutableStateOf(MapsPrefs.openAtCar(mapsContext)) }
    var radius by remember { mutableStateOf(MapsPrefs.parkingRadius(mapsContext)) }
    SettingsSection("Parking", "The map's P button saves the spot any time. A saved spot is cleared when the car moves on: a new car trip, or car speed while the map is open.") {
        SwitchRow("Save where I park", "When directions by car end or are stopped, the spot is saved as My car", autoPark) { autoPark = it; MapsPrefs.setAutoPark(mapsContext, it) }
        SwitchRow("Open the map at my car", "While a parking spot is saved, Maps opens with directions on foot to it (not when you are already by it)", openAtCar) { openAtCar = it; MapsPrefs.setOpenAtCar(mapsContext, it) }
        ChoiceRow("ΑμεΑ parking near the destination", "How far from where you are going the car's Parking button looks", MapsPrefs.RADII, radius) { radius = it; MapsPrefs.setParkingRadius(mapsContext, it) }
    }
    // Google Maps' saved places from Takeout (the user, 21 Oct: "be able to bring stuff I saved over")
    val importScope = rememberCoroutineScope()
    var imported by remember { mutableStateOf<MapsImport.Result?>(null) }
    var importing by remember { mutableStateOf(false) }
    val pickTakeout = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        importScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching {
                val name = mapsContext.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "import.zip"
                val entries = mapsContext.contentResolver.openInputStream(uri)!!.use { MapsImport.read(name, it) }
                val root = MapsFiles.root(driveRoot())
                MapsImport.into(root, MapsSearch.load(root), entries)
            } }
            importing = false
            r.onSuccess { imported = it; WriterLog.i("Maps: imported ${it.places} places, ${it.missed.size} not placed") }
                .onFailure { WriterLog.e("Maps: import", it); Toast.makeText(mapsContext, "Could not read that file: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    // sharing: my places and the map's additions out as GeoJSON and in again (the user, 21 Oct: "μαζική εισαγωγή και εξαγωγή
    // των προσωπικών ή των δημώσιων στειχείων για διαμηρασμό και εμπλούτευση του χάρτη κάθε χρήστη")
    var exportWhat by remember { mutableStateOf<String?>(null) }
    var shared by remember { mutableStateOf<MapsShare.Imported?>(null) }
    val pickShared = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching {
                MapsShare.import(MapsFiles.root(driveRoot()), mapsContext.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) })
            } }
            r.onSuccess { shared = it; WriterLog.i("Maps: imported $it") }
                .onFailure { WriterLog.e("Maps: import of a shared file", it); Toast.makeText(mapsContext, "Not a places file: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    SettingsSection("Your places", "Google Takeout: choose only Saved and Maps (your places) at takeout.google.com (the others, as YouTube and Maps, carry videos and photos), then the .zip here, or one list's .csv. " +
        "Each list becomes a list here; a place without its spot in the file is found by its name, the others are named for you to find.") {
        SettingRow("Export places", "Your places and what you added to the map, as a GeoJSON file for another Tetra or another map app", onClick = { exportWhat = "" })
        SettingRow("Import places", "Adds such a file's places and map additions to yours", onClick = { pickShared.launch(arrayOf("application/geo+json", "application/json", "*/*")) })
        SettingRow("Import from Google Maps", if (importing) "Importing…" else "Your saved lists and starred places, from Google Takeout",
            onClick = { if (!importing) pickTakeout.launch(arrayOf("application/zip", "text/csv", "text/comma-separated-values", "application/json", "*/*")) })
    }
    exportWhat?.let { _ -> AlertDialog(onDismissRequest = { exportWhat = null }, title = { Text("Export") },
        text = { Text("What goes in the file?") },
        confirmButton = { Row {
            listOf("mine" to "My places", "map" to "Map additions", "both" to "Both").forEach { (k, label) -> TextButton(onClick = {
                exportWhat = null
                runCatching {
                    val root = MapsFiles.root(driveRoot())
                    val text = MapsShare.export(if (k == "map") emptyList() else MapsFiles.places(root), MapsFiles.lists(root), if (k == "mine") emptyList() else MapsSearch.added(root))
                    val file = java.io.File(mapsContext.cacheDir, "share/tetra-places-${java.time.LocalDate.now()}.geojson").apply { parentFile?.mkdirs(); writeText(text) }
                    val uri = androidx.core.content.FileProvider.getUriForFile(mapsContext, "${mapsContext.packageName}.files", file)
                    mapsContext.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/geo+json").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share places"))
                }.onFailure { WriterLog.e("Maps: export", it); Toast.makeText(mapsContext, "Could not export: ${it.message}", Toast.LENGTH_LONG).show() }
            }) { Text(label) } }
        } },
        dismissButton = { TextButton(onClick = { exportWhat = null }) { Text("Cancel") } }) }
    shared?.let { r -> AlertDialog(onDismissRequest = { shared = null }, title = { Text("Imported") },
        text = { Text("${r.places} places and ${r.added} map additions; ${r.skipped} already here or without a position.") },
        confirmButton = { TextButton(onClick = { shared = null }) { Text("OK") } }) }
    imported?.let { r -> AlertDialog(onDismissRequest = { imported = null }, title = { Text("Imported ${r.places} places") },
        text = { androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
            item { Text("Into ${r.lists} lists." + if (r.missed.isEmpty()) "" else " These could not be placed — search for them in Maps and Save:", style = MaterialTheme.typography.bodyMedium) }
            items(r.missed.size) { i -> Text("• " + r.missed[i], style = MaterialTheme.typography.bodySmall) }
        } },
        confirmButton = { TextButton(onClick = { imported = null }) { Text("OK") } }) }
    val files by produceState<List<Pair<String, String>>?>(null) { value = withContext(Dispatchers.IO) { MapsFiles.installed(MapsFiles.root(driveRoot())) } }
    SettingsSection("Installed maps", "Maps are made on the computer and come here with Local Sync, into Tetra/Maps: tools/maps-greece.sh downloads " +
        "the newest map of Greece and builds the routing for it (an Update button in desktop Tetra is planned).") {
        files?.forEach { (what, about) -> SettingRow(what, about) }
    }
    // the log and the last 7 days' drive traces (MapsTrace), for a problem seen on the road (the user, 23 Oct)
    SettingsSection("Problems") {
        SettingRow("Send log", "Tetra's log and the traces of your drives of the last 7 days, as one zip; they stay on this device until you send them",
            onClick = { MapsTrace.export(mapsContext)?.let { shareDriveFile(mapsContext, it) } })
    }
}

/**
 * Settings › Contacts (the user, 24 Oct: "a Contacts card in Settings, permission once"): the copy in the phone's own
 * contacts and the calendar birthdays go into. Calls from the computer are under Phone.
 */
@Composable private fun ContactsPage() {
    val context = LocalContext.current
    val version = resumes()
    val askWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) ContactsAccount.set(context, true); version.intValue++ }
    @Suppress("UNUSED_EXPRESSION") version.intValue // read again on each change
    val writes = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val copy = ContactsAccount.on(context) && writes
    val cals = remember(version.intValue) { runCatching { CalendarFiles.calendars(calendarRoot()) }.getOrDefault(emptyList()) }
    val chosen = cals.firstOrNull { it.dir.name == ContactsPrefs.birthdays(context) } ?: ContactsBirthdays.existing(cals)
    SettingsSection(null, "Your contacts are files in Tetra/Contacts, one per person: they sync with the computer, where Contacts shows them too.") {
        SwitchRow("In the phone's contacts", if (copy) "The dialer, Viber, WhatsApp and the car show these names and photos (a read-only copy, account Tetra)"
            else "Only Tetra shows your contacts", copy) { on ->
            when { !on -> { ContactsAccount.set(context, false); version.intValue++ }; writes -> { ContactsAccount.set(context, true); version.intValue++ }; else -> askWrite.launch(Manifest.permission.WRITE_CONTACTS) }
        }
        ChoiceRow("Birthdays go into", if (cals.isEmpty()) "No calendars yet" else "Each birthday and date is a yearly event there, reminded at 9:00 on the day and the day before",
            cals.map { it to it.name }, chosen) { c -> c?.let { ContactsPrefs.setBirthdays(context, it.dir.name) }; version.intValue++ }
    }
}

/** Settings › Data (Plan.md § Data): the plan is set up in Data's own Setup; here its state and the switches. */
@Composable private fun DataPage() {
    val context = LocalContext.current
    val back = resumes()
    val access = remember(back.intValue) { DataStats.hasAccess(context) }
    val plan = remember(back.intValue) { DataFiles.plan() }
    val prefs = remember { context.getSharedPreferences("data", android.content.Context.MODE_PRIVATE) }
    SettingsSection(null, "Data counts each app's mobile data and Wi-Fi from Android's own statistics. It stays on your devices: Local Sync carries it to your tablet and computer only.") {
        SettingRow("Setup", "Your plan, when it renews, roaming, warnings — step by step", onClick = { openData(context, "setup") }) { Chevron() }
        StatusRow("Usage access", if (access) "On" else "Off — Data sees only its own use", access, "Settings") {
            runCatching { context.startActivity(DataStats.accessIntent(context)) }.onFailure { context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
        SettingRow("Your plan", plan?.let { p -> when (p.kind) {
            DataPlan.UNLIMITED -> "Unlimited"; DataPlan.FAIR_USE -> "Unlimited, ${DataRules.bytes(p.size)} fair use"; else -> DataRules.bytes(p.size)
        } + " · renews " + DataRules.cycle(p, java.time.LocalDate.now()).end.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)) } ?: "Not set up", onClick = { openData(context, "setup") }) { Chevron() }
    }
    SettingsSection("Warnings") {
        SettingRow("Warnings", plan?.takeIf { it.limited }?.warn?.joinToString(" · ") { "$it %" } ?: "None", onClick = { openData(context, "setup") }) { Chevron() }
        var budget by remember { mutableStateOf(prefs.getBoolean("warnBudget", false)) }
        SwitchRow("When a day's budget is passed", null, budget) { budget = it; prefs.edit().putBoolean("warnBudget", it).apply() }
        SettingRow("Roaming", plan?.let { p -> (if (p.passPrice > 0) "Outside the EU: ${p.currency}${"%.0f".format(p.passPrice)} for ${DataRules.bytes(p.passSize)} a day" else "No day pass") +
            "; notices to turn data roaming off" } ?: "Not set up", onClick = { openData(context, "setup") }) { Chevron() }
    }
    SettingsSection("Live") {
        var live by remember { mutableStateOf(DataSpeed.enabled(context)) }
        SwitchRow("Live speed", "In the status bar while the screen is on", live) { live = it; DataSpeed.setEnabled(context, it) }
        var drops by remember { mutableStateOf(DataSignal.summaryOn(context)) }
        SwitchRow("Signal drops", "A daily summary of the drops (they are always listed in Data)", drops) { drops = it; DataSignal.setSummary(context, it) }
        val located = remember(back.intValue) { context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        StatusRow("Wi-Fi around", if (located) "Location allowed: the networks around and their channels" else "Needs the location permission (only while Signal is open)", located, if (located) null else "Settings") {
            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)))
        }
        SettingRow("Open Data", null, onClick = { openData(context) }) { Chevron() }
    }
}

/** Settings › Phone: the phone app's own settings ([PhoneSettings] is the same for the Phone's menu), and calls from the computer. */
@Composable private fun PhonePage() {
    val context = LocalContext.current
    val version = resumes()
    val askRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { version.intValue++ }
    val askCalls = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { version.intValue++; PhoneCall.Watcher.start(context.applicationContext) }
    @Suppress("UNUSED_EXPRESSION") version.intValue
    if (PhoneRelay.noCalls(context)) SettingsSection {
        SettingRow("No calls of its own", "This device's Phone calls through your phone, by way of the computer, and shows the phone's calls as they ring. The voice stays on the phone.")
    } else {
        val dialer = PhonePrefs.isDialer(context)
        SettingsSection("Phone app") {
            SettingRow(if (dialer) "Tetra is the phone app" else "Tetra is not the phone app",
                if (dialer) "Calls ring and are spoken on Tetra's own call screen. To give it back: Android's Default apps › Phone app."
                else "As the phone app, Tetra shows its own call screen and buzzes when they answer. The phone's own call recording is not shown then.") {
                TextButton(onClick = {
                    if (dialer) runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    else askRole.launch(context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER))
                }) { Text(if (dialer) "Change" else "Make it") }
            }
            SettingRow("Open the Phone", "Speed dial and spam are in its own settings", onClick = { context.startActivity(Intent(context, PhoneActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Chevron() }
        }
        var answer by remember { mutableStateOf(PhonePrefs.buzzAnswer(context)) }
        var end by remember { mutableStateOf(PhonePrefs.buzzEnd(context)) }
        var unknown by remember { mutableStateOf(PhonePrefs.silenceUnknown(context)) }
        var hidden by remember { mutableStateOf(PhonePrefs.declineHidden(context)) }
        SettingsSection("Calls") {
            SwitchRow("Vibrate when answered", "Two short taps when the other side picks up a call you made", answer) { answer = it; PhonePrefs.setBuzzAnswer(context, it) }
            SwitchRow("Vibrate when a call ends", "One tap when a call that was answered ends", end) { end = it; PhonePrefs.setBuzzEnd(context, it) }
            SwitchRow("Silence numbers not in Contacts", "They ring without sound and show in Recents as missed", unknown) { unknown = it; PhonePrefs.setSilenceUnknown(context, it) }
            SwitchRow("Decline hidden numbers", "Calls that hide their number are declined before they ring", hidden) { hidden = it; PhonePrefs.setDeclineHidden(context, it) }
        }
    }
    // calls from the computer (was in Settings › Contacts): one permission for Tetra (Android's "Phone"), not one per contact
    val calls = PhoneCall.allAllowed(context)
    SettingsSection("With the computer", (if (calls) "Once for Tetra, not for each contact. To take it back: Android's Settings › Apps › Tetra › Permissions › Phone and Call logs."
        else "Allow asks once, for Tetra (not for each contact): to place a call, to see a call ringing, and to answer or end it from the computer. The caller's number and the rest come with Tetra as the phone app.") +
        " The sound stays on the phone, unless the phone is connected to the computer by Bluetooth for calls (the computer's Settings › Calls).") {
        // the on/off (the user, 25 Oct: "the on/off was a different thing" from the permission below)
        var pc by remember { mutableStateOf(PhonePrefs.pcCalls(context)) }
        SwitchRow("Calls on the computer", if (pc) "The computer shows this phone's calls (answer, decline, end) and can place calls through it"
            else "Off: the computer hears of no call here and cannot place one", pc) {
            pc = it; PhonePrefs.setPcCalls(context, it)
            if (it && !calls) askCalls.launch(PhoneCall.PERMISSIONS) // turned on: Android's permission asked then (the user, 25 Oct)
        }
        StatusRow("Phone permission", if (calls) "Allowed: Call on the computer rings here at once, and the computer sees this phone's calls"
            else "Each call from the computer comes as a notification to tap, and the computer does not see this phone's calls", true, if (calls) "Settings" else "Allow") {
            if (calls) runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            else askCalls.launch(PhoneCall.PERMISSIONS)
        }
    }
}
