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
internal enum class SettingsPage(@androidx.annotation.StringRes val title: Int, val icon: Int, val tint: Color?, @androidx.annotation.StringRes val summary: Int, val items: List<Int>) {
    Sync(R.string.app_sync, R.drawable.ic_sync, null, R.string.settings_sync_summary, listOf(R.string.settings_computer, R.string.settings_open_sync, R.string.settings_sync_auto, R.string.settings_listen)),
    Appearance(R.string.settings_appearance, R.drawable.ic_palette, null, R.string.settings_appearance_summary, listOf(R.string.settings_theme, R.string.settings_language, R.string.settings_photo_on_home, R.string.settings_hidden_from_home, R.string.settings_app_icons)),
    Gallery(R.string.app_gallery, Category.Gallery.icon, Category.Gallery.color, R.string.settings_gallery_summary, listOf(R.string.settings_photos_videos, R.string.settings_search_quality, R.string.settings_folders)),
    Files(R.string.app_files, Category.Files.icon, Category.Files.color, R.string.settings_files_summary, listOf(R.string.settings_drive_workspace, R.string.settings_all_files)),
    Scanner(R.string.settings_pdf_scanner, Category.Scanner.icon, Category.Scanner.color, R.string.settings_resolution, listOf(R.string.settings_resolution)),
    Office(R.string.app_office, Category.Office.icon, Category.Office.color, R.string.settings_office_summary, listOf(R.string.settings_open_docx, R.string.settings_open_sheets, R.string.settings_open_slides,
        R.string.settings_open_pdfs, R.string.settings_autosave, R.string.settings_lengths, R.string.settings_read_aloud_speed, R.string.settings_the_ribbon, R.string.settings_its_tools, R.string.settings_tab_marker)),
    Text(R.string.app_text_editor, Category.Text.icon, Category.Text.color, R.string.settings_text_summary, listOf(R.string.settings_open_txt, R.string.settings_word_wrap, R.string.settings_save_new_in)),
    Calendar(R.string.app_calendar, Category.Calendar.icon, Category.Calendar.color, R.string.settings_calendar_summary, listOf(R.string.settings_reminders_here, R.string.settings_test_reminder, R.string.settings_notifications,
        R.string.settings_exact_alarms, R.string.settings_battery, R.string.settings_hyper_island, R.string.settings_autostart, R.string.common_import, R.string.settings_export_all, R.string.settings_share_claude)),
    Timer(R.string.app_timer, Category.Timer.icon, Category.Timer.color, R.string.settings_share_claude, listOf(R.string.settings_share_claude)),
    Maps(R.string.app_maps, Category.Maps.icon, Category.Maps.color, R.string.settings_maps_summary, listOf(R.string.settings_map, R.string.settings_on_foot, R.string.settings_by_car, R.string.settings_directions_in, R.string.settings_test_voice, R.string.settings_voice_settings, R.string.settings_sharp_bends,
        R.string.settings_save_parking, R.string.settings_open_at_car, R.string.settings_accessible_parking, R.string.settings_export_places, R.string.settings_import_places, R.string.settings_import_google,
        R.string.settings_installed_maps, R.string.settings_send_log)),
    Contacts(R.string.app_contacts, Category.Contacts.icon, Category.Contacts.color, R.string.settings_contacts_summary, listOf(R.string.settings_in_phone_contacts, R.string.settings_birthdays_into)),
    Phone(R.string.app_phone, Category.Phone.icon, Category.Phone.color, R.string.settings_phone_summary, listOf(R.string.settings_phone_app, R.string.settings_buzz_answer, R.string.settings_buzz_end,
        R.string.settings_silence_unknown, R.string.settings_decline_hidden, R.string.settings_pc_calls, R.string.settings_phone_permission, R.string.settings_open_phone)),
    Data(R.string.app_data, Category.Data.icon, Category.Data.color, R.string.settings_data_summary, listOf(R.string.settings_data_setup, R.string.settings_usage_access, R.string.settings_your_plan, R.string.settings_warnings, R.string.settings_roaming,
        R.string.settings_live_speed, R.string.settings_signal_drops, R.string.settings_wifi_around, R.string.settings_open_data)),
}

@Composable
internal fun SettingsTab(
    back: () -> Unit,
    metadataStore: PhotoMetadataStore,
    photoPermission: String,
    photoAccess: Boolean,
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
    openGuide: () -> Unit,
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
                    IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.home_back_to_home)) }
                }
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape, modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = null, Modifier.size(22.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text(stringResource(R.string.settings_search), color = islandContentColor().copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = islandContentColor(), fontSize = 16.sp),
                                cursorBrush = SolidColor(islandContentColor()), modifier = Modifier.fillMaxWidth())
                        }
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.common_clear)) }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                @Composable fun PageRow(p: SettingsPage, summary: String) = SettingRow(stringResource(p.title), summary, onClick = { open = p; query = "" },
                    chosen = wide && p == shown, leading = { Icon(painterResource(p.icon), contentDescription = null, tint = p.tint ?: quiet(), modifier = Modifier.size(24.dp)) },
                ) { if (!wide) Chevron() }
                if (query.isBlank()) {
                    SettingsSection(stringResource(R.string.app_name)) {
                        SettingRow(stringResource(R.string.guide_title), stringResource(R.string.guide_summary), onClick = openGuide,
                            leading = { Icon(painterResource(R.drawable.ic_school), contentDescription = null, tint = quiet(), modifier = Modifier.size(24.dp)) }) { if (!wide) Chevron() }
                        listOf(SettingsPage.Sync, SettingsPage.Appearance).forEach { PageRow(it, stringResource(it.summary)) }
                    }
                    SettingsSection(stringResource(R.string.settings_apps)) { SettingsPage.entries.drop(2).forEach { PageRow(it, stringResource(it.summary)) } }
                } else {
                    // the words as shown, in the language shown
                    val res = LocalContext.current.resources
                    val q = query.trim().lowercase()
                    val hits = SettingsPage.entries.flatMap { p -> p.items.map(res::getString).filter { q in it.lowercase() || q in res.getString(p.title).lowercase() }.map { p to it } }
                    SettingsSection(pluralStringResource(R.plurals.settings_found, hits.size, hits.size)) { hits.forEach { (p, item) ->
                        SettingRow(item, stringResource(p.title), onClick = { open = p; query = "" },
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
                    IconButton(onClick = { open = null }) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.settings_back)) }
                }
                Icon(painterResource(p.icon), contentDescription = null, tint = p.tint ?: MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(28.dp))
                Text(stringResource(p.title), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Column(Modifier.widthIn(max = 760.dp)) { when (p) {
                    SettingsPage.Sync -> SyncPage(pairedDevice, forgetPairedDevice, openSync)
                    SettingsPage.Appearance -> AppearancePage(showPhotoBackdrop, setShowPhotoBackdrop, openPart)
                    SettingsPage.Gallery -> GalleryPage(photoPermission, photoAccess, managePhotos, metadataStore, folders, setFolderIncluded)
                    SettingsPage.Files -> FilesPage(hasDriveAccess, manageDrive)
                    SettingsPage.Scanner -> ScannerPage()
                    SettingsPage.Office -> OfficePage()
                    SettingsPage.Text -> TextEditorPage()
                    SettingsPage.Calendar -> CalendarPage()
                    SettingsPage.Timer -> SettingsSection("Claude") { SkillRows("tetra-timer", "Timer", stringResource(R.string.settings_timer_skill)) }
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
                Text(options.firstOrNull { it.first == chosen }?.second.orEmpty(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(painterResource(R.drawable.ic_unfold_more), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; pick(value) },
                        trailingIcon = if (value == chosen) { { Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.common_chosen), modifier = Modifier.size(18.dp)) } } else null)
                }
            }
        }
    }
}

/** A setting that is a permission or a state elsewhere: how it stands, and a button to it. */
@Composable private fun StatusRow(title: String, state: String, ok: Boolean, action: String?, act: () -> Unit) =
    SettingRow(title, state, warn = !ok) { if (action != null) TextButton(onClick = act) { Text(action, maxLines = 1, overflow = TextOverflow.Ellipsis) } }

@Composable private fun Chevron() = Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = quiet(), modifier = Modifier.size(20.dp))

/** A counter that goes up each time the screen comes back (from Android's settings): states read again. */
@Composable internal fun resumes(): MutableIntState {
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
    SettingRow(stringResource(R.string.settings_share_claude), what, onClick = { shareSkill(context, skill, part) }) { Icon(painterResource(R.drawable.ic_share), contentDescription = null, tint = quiet(), modifier = Modifier.size(20.dp)) }
    SettingRow(stringResource(R.string.settings_save_to_files), stringResource(R.string.settings_skill_zip), onClick = { save.launch("$skill.zip") })
}

// ---- the pages ----

@Composable private fun SyncPage(paired: Pairing?, forget: () -> Unit, openSync: () -> Unit) {
    val c = LocalContext.current
    SettingsSection(stringResource(R.string.settings_computer), stringResource(R.string.settings_computer_note)) {
        SettingRow(paired?.name ?: stringResource(R.string.settings_no_computer), paired?.hosts?.firstOrNull()) { if (paired != null) TextButton(onClick = forget) { Text(stringResource(R.string.settings_forget), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        SettingRow(stringResource(R.string.settings_open_sync), stringResource(R.string.settings_open_sync_summary), onClick = openSync) { Chevron() }
    }
    SettingsSection(stringResource(R.string.settings_automatic), stringResource(R.string.settings_automatic_note)) {
        var every by remember { mutableStateOf(SyncJob.minutes(c)) }
        ChoiceRow(stringResource(R.string.settings_sync_auto), stringResource(R.string.settings_sync_auto_summary), SyncJob.choices.map { (m, label) -> m to stringResource(label) }, every) { every = it; SyncJob.setMinutes(c, it) }
        var listen by remember { mutableStateOf(SyncListenService.enabled(c)) }
        SwitchRow(stringResource(R.string.settings_listen),
            if (listen) stringResource(R.string.settings_listen_on)
            else stringResource(R.string.settings_listen_off), listen) { listen = it; SyncListenService.setEnabled(c, it) }
    }
}

@Composable private fun AppearancePage(photoBackdrop: Boolean, setPhotoBackdrop: (Boolean) -> Unit, open: (String) -> Unit) {
    val context = LocalContext.current
    SettingsSection(null, stringResource(R.string.settings_theme_note)) {
        ChoiceRow(stringResource(R.string.settings_theme), stringResource(R.string.settings_theme_summary), listOf(false to stringResource(R.string.settings_dark), true to stringResource(R.string.settings_light)), TetraLook.light) { TetraLook.set(context, it) }
    }
    SettingsSection(null, stringResource(R.string.settings_language_note)) {
        var language by remember { mutableStateOf(AppLocale.get(context)) }
        ChoiceRow(stringResource(R.string.settings_language), stringResource(R.string.settings_language_summary),
            listOf("" to "${stringResource(R.string.settings_language_system)} (${AppLocale.name(AppLocale.systemTag())})") + AppLocale.tags.map { it to AppLocale.name(it) }, language) { language = it; AppLocale.set(context, it) }
    }
    // what is hidden from Home on this device, each still opened here or shown again
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    var layout by remember(wide) { mutableStateOf(HomeLayout.load(context, wide)) }
    val hidden = layout.filter { it.hidden }
    SettingsSection(stringResource(R.string.app_home), if (hidden.isEmpty()) null else stringResource(R.string.settings_hidden_note)) {
        SwitchRow(stringResource(R.string.settings_photo_on_home), stringResource(R.string.settings_photo_on_home_summary), photoBackdrop, setPhotoBackdrop)
        if (hidden.isEmpty()) SettingRow(stringResource(R.string.settings_hidden_from_home), stringResource(R.string.settings_nothing_hidden))
        hidden.forEach { item ->
            SettingRow(HomeLayout.NAMES[item.id]?.let { stringResource(it) } ?: item.id, stringResource(R.string.settings_hidden_here)) {
                if (item.id == "scanner") { TextButton({ open("scanner") }) { Text(stringResource(R.string.app_scanner), maxLines = 1, overflow = TextOverflow.Ellipsis) }; TextButton({ open("codes") }) { Text(stringResource(R.string.app_codes), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                else TextButton({ open(item.id) }) { Text(stringResource(R.string.common_open), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                TextButton({ layout = HomeLayout.change(layout, item.copy(hidden = false, col = -1, row = -1), HomeLayout.columns(wide)); HomeLayout.save(context, wide, layout) }) { Text(stringResource(R.string.common_show), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
    // a launcher icon for each part (asked 17 Oct): on the home screen and in the app list, opening straight into it
    SettingsSection(stringResource(R.string.settings_app_icons), stringResource(R.string.settings_app_icons_note)) {
        Applet.entries.forEach { a ->
            var on by remember { mutableStateOf(a.shown(context)) }
            SettingRow(stringResource(a.label), onClick = { on = !on; a.show(context, on) }, leading = { Image(painterResource(a.art), contentDescription = null, modifier = Modifier.size(32.dp)) }) {
                Switch(checked = on, onCheckedChange = { on = it; a.show(context, it) })
            }
        }
    }
}

@Composable private fun GalleryPage(permission: String, granted: Boolean, manage: () -> Unit, store: PhotoMetadataStore, folders: List<DeviceFolder>, setIncluded: (String, Boolean) -> Unit) {
    SettingsSection(stringResource(R.string.settings_access)) { StatusRow(stringResource(R.string.settings_photos_videos), permission, granted, stringResource(R.string.common_settings), manage) }
    var quality by remember { mutableStateOf(store.searchQuality()) }
    SettingsSection(stringResource(R.string.common_search), stringResource(R.string.settings_search_note)) {
        ChoiceRow(stringResource(R.string.settings_search_quality), if (quality == PhotoSearchQuality.Fast) stringResource(R.string.settings_search_fast_summary) else stringResource(R.string.settings_search_advanced_summary),
            listOf(PhotoSearchQuality.Fast to stringResource(R.string.settings_search_fast), PhotoSearchQuality.Advanced to stringResource(R.string.settings_search_advanced)), quality) { quality = it; store.setSearchQuality(it) }
    }
    // every folder you have answered about, while the phone can still see it; a new one asks first, in Help organize
    SettingsSection(stringResource(R.string.settings_folders), stringResource(R.string.settings_folders_note)) {
        val answered = folders.filter { it.included != null }
        answered.forEach { f -> SwitchRow(f.name, pluralStringResource(R.plurals.settings_folder_count, f.entries.size, f.entries.size), f.included == true) { setIncluded(f.name, it) } }
        if (answered.isEmpty()) SettingRow(stringResource(R.string.settings_no_folders), stringResource(R.string.settings_no_folders_summary))
    }
}

@Composable private fun FilesPage(access: Boolean, manage: () -> Unit) {
    SettingsSection(stringResource(R.string.settings_storage), stringResource(R.string.settings_storage_note)) {
        SettingRow(stringResource(R.string.settings_drive_workspace), DRIVE_PATH)
    }
    SettingsSection(stringResource(R.string.settings_access)) { StatusRow(stringResource(R.string.settings_all_files), if (access) stringResource(R.string.settings_granted) else stringResource(R.string.settings_not_granted), access, stringResource(R.string.common_settings), manage) }
}

@Composable private fun ScannerPage() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("scanner", Context.MODE_PRIVATE) }
    var dpi by remember { mutableStateOf(prefs.getInt(SCANNER_DPI, 300)) }
    SettingsSection(stringResource(R.string.settings_saved_pages), stringResource(R.string.settings_saved_pages_note)) {
        ChoiceRow(stringResource(R.string.settings_resolution), stringResource(R.string.settings_resolution_summary), listOf(300 to "300 dpi", 200 to "200 dpi"), dpi) { dpi = it; prefs.edit().putInt(SCANNER_DPI, it).apply() }
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
    SettingsSection(stringResource(R.string.settings_open_from_files), stringResource(R.string.settings_office_open_note)) {
        SwitchRow(stringResource(R.string.settings_open_docx), if (opens) stringResource(R.string.settings_open_docx_on) else stringResource(R.string.settings_open_docx_off), opens) { opens = it; office.opensOffice = it }
        SwitchRow(stringResource(R.string.settings_open_sheets), if (sheets) stringResource(R.string.settings_open_sheets_on) else stringResource(R.string.settings_open_sheets_off), sheets) { sheets = it; office.opensSheets = it }
        SwitchRow(stringResource(R.string.settings_open_slides), if (slides) stringResource(R.string.settings_open_slides_on) else stringResource(R.string.settings_open_slides_off), slides) { slides = it; office.opensSlides = it }
        SwitchRow(stringResource(R.string.settings_open_pdfs), if (pdfs) stringResource(R.string.settings_open_pdfs_on) else stringResource(R.string.settings_open_pdfs_off), pdfs) { pdfs = it; office.opensPdfs = it }
    }
    SettingsSection(stringResource(R.string.settings_editing)) {
        var auto by remember { mutableStateOf(office.autoSave) }
        SwitchRow(stringResource(R.string.settings_autosave), if (auto) stringResource(R.string.settings_autosave_on)
            else stringResource(R.string.settings_autosave_off), auto) { auto = it; office.autoSave = it }
        LaunchedEffect(Unit) { WriterUnits.load(context) }
        ChoiceRow(stringResource(R.string.settings_lengths), stringResource(R.string.settings_lengths_summary), listOf(false to stringResource(R.string.settings_centimetres), true to stringResource(R.string.settings_inches)), WriterUnits.inches) { WriterUnits.set(context, it) }
        var speed by remember { mutableStateOf(office.readAloudSpeed) }
        ChoiceRow(stringResource(R.string.settings_read_aloud_speed), stringResource(R.string.settings_read_aloud_summary), listOf(0.75f to "0.75×", 1f to "1×", 1.25f to "1.25×", 1.5f to "1.5×", 2f to "2×"), speed) { speed = it; office.readAloudSpeed = it }
    }
    // the ribbon's place and size for Words, Tables and Slides (asked 16 Oct: one Office section; File › Settings opens the same)
    SettingsSection(stringResource(R.string.settings_ribbon), stringResource(R.string.settings_ribbon_note)) {
        ChoiceRow(stringResource(R.string.settings_the_ribbon), stringResource(R.string.settings_the_ribbon_summary), listOf("auto" to stringResource(R.string.settings_by_itself), "bottom" to stringResource(R.string.settings_bottom), "top" to stringResource(R.string.settings_top)), OfficeUi.place) { OfficeUi.set(context, place = it) }
        ChoiceRow(stringResource(R.string.settings_its_tools), stringResource(R.string.settings_its_tools_summary), listOf("auto" to stringResource(R.string.settings_by_itself), "normal" to stringResource(R.string.settings_normal), "compact" to stringResource(R.string.settings_compact)), OfficeUi.size) { OfficeUi.set(context, size = it) }
        ChoiceRow(stringResource(R.string.settings_tab_marker), stringResource(R.string.settings_tab_marker_summary), listOf("bubble" to stringResource(R.string.settings_bubble), "dash" to stringResource(R.string.settings_dash)), OfficeUi.marker) { OfficeUi.set(context, marker = it) }
    }
}

@Composable private fun TextEditorPage() {
    val context = LocalContext.current
    val editor = remember { TextEditorStore(context) }
    var txt by remember { mutableStateOf(editor.opensTxt) }
    var wrap by remember { mutableStateOf(editor.wordWrap) }
    var folder by remember { mutableStateOf(editor.saveFolder) }
    SettingsSection(stringResource(R.string.settings_open_from_files), stringResource(R.string.settings_text_open_note)) {
        SwitchRow(stringResource(R.string.settings_open_txt), if (txt) stringResource(R.string.settings_open_txt_on) else stringResource(R.string.settings_open_txt_off), txt) { txt = it; editor.opensTxt = it }
    }
    SettingsSection(stringResource(R.string.settings_editing)) {
        SwitchRow(stringResource(R.string.settings_word_wrap), if (wrap) stringResource(R.string.settings_word_wrap_on) else stringResource(R.string.settings_word_wrap_off), wrap) { wrap = it; editor.wordWrap = it }
        Column(Modifier.fillMaxWidth().background(rowColor()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_save_new_in), style = MaterialTheme.typography.bodyLarge)
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
    SettingsSection(stringResource(R.string.settings_reminders), stringResource(R.string.settings_reminders_note)) {
        var on by remember { mutableStateOf(CalendarAlarms.enabled(context)) }
        SwitchRow(stringResource(R.string.settings_reminders_here), if (on) stringResource(R.string.common_on) else stringResource(R.string.common_off), on) { on = it; CalendarAlarms.setEnabled(context, it) }
        SettingRow(stringResource(R.string.settings_test_reminder), stringResource(R.string.settings_test_reminder_summary), onClick = { CalendarAlarms.test(context); say(context.getString(R.string.settings_test_reminder_coming)) })
    }
    SettingsSection(stringResource(R.string.settings_reminders_need)) {
        StatusRow(stringResource(R.string.settings_notifications), if (notifications) stringResource(R.string.settings_notifications_on) else stringResource(R.string.settings_notifications_off), notifications, if (notifications) stringResource(R.string.common_settings) else stringResource(R.string.common_allow)) {
            if (!notifications && android.os.Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else open(notificationSettings)
        }
        StatusRow(stringResource(R.string.settings_exact_alarms), if (exact) stringResource(R.string.settings_exact_alarms_on) else stringResource(R.string.settings_exact_alarms_off), exact, if (exact) null else stringResource(R.string.common_allow)) {
            if (android.os.Build.VERSION.SDK_INT >= 31) open(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
        }
        StatusRow(stringResource(R.string.settings_battery), if (battery) stringResource(R.string.settings_battery_on) else stringResource(R.string.settings_battery_off), battery, stringResource(R.string.common_settings)) {
            open(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) || open(appDetails)
        }
        if (xiaomi) {
            StatusRow(stringResource(R.string.settings_hyper_island), if (island) stringResource(R.string.settings_hyper_island_on) else stringResource(R.string.settings_hyper_island_off), island, stringResource(R.string.common_settings)) { open(notificationSettings) }
            StatusRow(stringResource(R.string.settings_autostart), stringResource(R.string.settings_autostart_summary), true, stringResource(R.string.common_settings)) {
                open(Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")) || open(appDetails)
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { say(withContext(Dispatchers.IO) { runCatching { importCalendars(context, uri) }.getOrElse { context.getString(R.string.settings_not_imported, it.message.orEmpty()) } }) }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch { say(withContext(Dispatchers.IO) { runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(CalendarTransfer.exportAll(CalendarFiles.calendars(calendarRoot()))) }; context.getString(R.string.settings_exported)
        }.getOrElse { context.getString(R.string.settings_not_exported, it.message.orEmpty()) } }) }
    }
    SettingsSection(stringResource(R.string.settings_your_calendars), stringResource(R.string.settings_your_calendars_note)) {
        SettingRow(stringResource(R.string.common_import), stringResource(R.string.settings_calendar_import_summary),
            onClick = { pick.launch(arrayOf("text/calendar", "application/zip", "application/octet-stream", "*/*")) })
        SettingRow(stringResource(R.string.settings_export_all), stringResource(R.string.settings_export_all_summary), onClick = { save.launch("Tetra calendars.zip") })
    }
    SettingsSection("Claude") { SkillRows("tetra-calendar", "Calendar", stringResource(R.string.settings_calendar_skill)) }
}

/** Settings › Maps (21 Oct): what is installed, where updates come from, the directions' language and voice, parking, places. */
@Composable private fun MapsPage() {
    val mapsContext = LocalContext.current
    var mapTheme by remember { mutableStateOf(MapsPrefs.mapTheme(mapsContext)) }
    var rolling by remember { mutableStateOf(MapsPrefs.rolling(mapsContext)) }
    SettingsSection(stringResource(R.string.settings_map)) {
        ChoiceRow(stringResource(R.string.settings_map), stringResource(R.string.settings_map_summary), listOf("light" to stringResource(R.string.settings_light), "dark" to stringResource(R.string.settings_dark)), mapTheme) { mapTheme = it; MapsPrefs.setMapTheme(mapsContext, it) }
        ChoiceRow(stringResource(R.string.settings_on_foot), if (rolling) stringResource(R.string.settings_rolling_summary) else stringResource(R.string.settings_walking_summary),
            listOf(false to stringResource(R.string.settings_walking), true to stringResource(R.string.settings_rolling)), rolling) { rolling = it; MapsPrefs.setRolling(mapsContext, it) }
        var mainRoads by remember { mutableStateOf(MapsPrefs.mainRoads(mapsContext)) }
        ChoiceRow(stringResource(R.string.settings_by_car), if (mainRoads) stringResource(R.string.settings_main_roads_summary)
            else stringResource(R.string.settings_shortest_summary),
            listOf(true to stringResource(R.string.settings_main_roads), false to stringResource(R.string.settings_shortest)), mainRoads) { mainRoads = it; MapsPrefs.setMainRoads(mapsContext, it) }
    }
    var lang by remember { mutableStateOf(MapsPrefs.lang(mapsContext)) }
    var bends by remember { mutableStateOf(MapsPrefs.bends(mapsContext)) }
    SettingsSection(stringResource(R.string.settings_directions), stringResource(R.string.settings_directions_note)) {
        ChoiceRow(stringResource(R.string.settings_directions_in), stringResource(R.string.settings_directions_in_summary), listOf("el" to stringResource(R.string.settings_greek), "en" to stringResource(R.string.settings_english)), lang) { lang = it; MapsPrefs.setLang(mapsContext, it) }
        SettingRow(stringResource(R.string.settings_test_voice), MapsPrefs.sample(lang), onClick = {
            var t: android.speech.tts.TextToSpeech? = null
            t = android.speech.tts.TextToSpeech(mapsContext.applicationContext) { ok ->
                if (ok == android.speech.tts.TextToSpeech.SUCCESS) {
                    val r = t?.setLanguage(MapsPrefs.ttsLocale(lang)) ?: -2
                    if (r < 0) Toast.makeText(mapsContext, mapsContext.getString(if (lang == "en") R.string.settings_no_english_voice else R.string.settings_no_greek_voice), Toast.LENGTH_LONG).show()
                    else t?.speak(MapsPrefs.sample(lang), android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "test")
                }
            }
        })
        SettingRow(stringResource(R.string.settings_voice_settings), stringResource(R.string.settings_voice_settings_summary), onClick = { runCatching { mapsContext.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Chevron() }
        SwitchRow(stringResource(R.string.settings_sharp_bends), stringResource(R.string.settings_sharp_bends_summary), bends) { bends = it; MapsPrefs.setBends(mapsContext, it) }
    }
    var autoPark by remember { mutableStateOf(MapsPrefs.autoPark(mapsContext)) }
    var openAtCar by remember { mutableStateOf(MapsPrefs.openAtCar(mapsContext)) }
    var radius by remember { mutableStateOf(MapsPrefs.parkingRadius(mapsContext)) }
    SettingsSection(stringResource(R.string.settings_parking), stringResource(R.string.settings_parking_note)) {
        SwitchRow(stringResource(R.string.settings_save_parking), stringResource(R.string.settings_save_parking_summary), autoPark) { autoPark = it; MapsPrefs.setAutoPark(mapsContext, it) }
        SwitchRow(stringResource(R.string.settings_open_at_car), stringResource(R.string.settings_open_at_car_summary), openAtCar) { openAtCar = it; MapsPrefs.setOpenAtCar(mapsContext, it) }
        ChoiceRow(stringResource(R.string.settings_accessible_parking), stringResource(R.string.settings_accessible_parking_summary), MapsPrefs.RADII, radius) { radius = it; MapsPrefs.setParkingRadius(mapsContext, it) }
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
                .onFailure { WriterLog.e("Maps: import", it); Toast.makeText(mapsContext, mapsContext.getString(R.string.settings_file_unreadable, it.message.orEmpty()), Toast.LENGTH_LONG).show() }
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
                .onFailure { WriterLog.e("Maps: import of a shared file", it); Toast.makeText(mapsContext, mapsContext.getString(R.string.settings_not_places_file, it.message.orEmpty()), Toast.LENGTH_LONG).show() }
        }
    }
    SettingsSection(stringResource(R.string.settings_your_places), stringResource(R.string.settings_your_places_note)) {
        SettingRow(stringResource(R.string.settings_export_places), stringResource(R.string.settings_export_places_summary), onClick = { exportWhat = "" })
        SettingRow(stringResource(R.string.settings_import_places), stringResource(R.string.settings_import_places_summary), onClick = { pickShared.launch(arrayOf("application/geo+json", "application/json", "*/*")) })
        SettingRow(stringResource(R.string.settings_import_google), if (importing) stringResource(R.string.settings_importing) else stringResource(R.string.settings_import_google_summary),
            onClick = { if (!importing) pickTakeout.launch(arrayOf("application/zip", "text/csv", "text/comma-separated-values", "application/json", "*/*")) })
    }
    exportWhat?.let { _ -> AlertDialog(onDismissRequest = { exportWhat = null }, title = { Text(stringResource(R.string.common_export)) },
        text = { Text(stringResource(R.string.settings_export_what)) },
        confirmButton = { Row {
            listOf("mine" to stringResource(R.string.settings_my_places), "map" to stringResource(R.string.settings_map_additions), "both" to stringResource(R.string.settings_both)).forEach { (k, label) -> TextButton(onClick = {
                exportWhat = null
                runCatching {
                    val root = MapsFiles.root(driveRoot())
                    val text = MapsShare.export(if (k == "map") emptyList() else MapsFiles.places(root), MapsFiles.lists(root), if (k == "mine") emptyList() else MapsSearch.added(root))
                    val file = java.io.File(mapsContext.cacheDir, "share/tetra-places-${java.time.LocalDate.now()}.geojson").apply { parentFile?.mkdirs(); writeText(text) }
                    val uri = androidx.core.content.FileProvider.getUriForFile(mapsContext, "${mapsContext.packageName}.files", file)
                    mapsContext.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/geo+json").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), mapsContext.getString(R.string.settings_share_places)))
                }.onFailure { WriterLog.e("Maps: export", it); Toast.makeText(mapsContext, mapsContext.getString(R.string.settings_not_exported_maps, it.message.orEmpty()), Toast.LENGTH_LONG).show() }
            }) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        } },
        dismissButton = { TextButton(onClick = { exportWhat = null }) { Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } }) }
    shared?.let { r -> AlertDialog(onDismissRequest = { shared = null }, title = { Text(stringResource(R.string.settings_imported)) },
        text = { Text(stringResource(R.string.settings_imported_shared, r.places, r.added, r.skipped)) },
        confirmButton = { TextButton(onClick = { shared = null }) { Text(stringResource(R.string.common_ok), maxLines = 1, overflow = TextOverflow.Ellipsis) } }) }
    imported?.let { r -> AlertDialog(onDismissRequest = { imported = null }, title = { Text(pluralStringResource(R.plurals.settings_imported_places, r.places, r.places)) },
        text = { androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
            item { Text(pluralStringResource(R.plurals.settings_into_lists, r.lists, r.lists) + if (r.missed.isEmpty()) "" else " " + stringResource(R.string.settings_not_placed), style = MaterialTheme.typography.bodyMedium) }
            items(r.missed.size) { i -> Text("• " + r.missed[i], style = MaterialTheme.typography.bodySmall) }
        } },
        confirmButton = { TextButton(onClick = { imported = null }) { Text(stringResource(R.string.common_ok), maxLines = 1, overflow = TextOverflow.Ellipsis) } }) }
    // the maps here, one a country (desktop Tetra's map builder makes them; sync brings them), each removable
    val mapsRoot = remember { MapsFiles.root(driveRoot()) }
    var mapsVersion by remember { mutableIntStateOf(0) }
    val files by produceState<List<Triple<String, String, String>>?>(null, mapsVersion) { value = withContext(Dispatchers.IO) { MapsFiles.installed(mapsContext, mapsRoot) } }
    var removing by remember { mutableStateOf<Pair<String, String>?>(null) }
    val removeScope = rememberCoroutineScope()
    SettingsSection(stringResource(R.string.settings_installed_maps), stringResource(R.string.settings_installed_maps_note)) {
        if (files?.isEmpty() == true) SettingRow(stringResource(R.string.maps_no_map))
        files?.forEach { (id, name, about) -> SettingRow(name, about) {
            TextButton(onClick = { removing = id to name }) { Text(stringResource(R.string.settings_remove_map), maxLines = 1) }
        } }
    }
    removing?.let { (id, name) -> AlertDialog(onDismissRequest = { removing = null }, title = { Text(stringResource(R.string.settings_remove_map_title, name)) },
        text = { Text(stringResource(R.string.settings_remove_map_body)) },
        confirmButton = { TextButton(onClick = { removing = null; removeScope.launch { withContext(Dispatchers.IO) { MapsFiles.removeMap(mapsRoot, id) }; mapsVersion++ } }) {
            Text(stringResource(R.string.settings_remove_map), maxLines = 1) } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.common_cancel), maxLines = 1) } }) }
    // the log and the last 7 days' drive traces (MapsTrace), for a problem seen on the road (the user, 23 Oct)
    SettingsSection(stringResource(R.string.settings_problems)) {
        SettingRow(stringResource(R.string.settings_send_log), stringResource(R.string.settings_send_log_summary),
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
    SettingsSection(null, stringResource(R.string.settings_contacts_note)) {
        SwitchRow(stringResource(R.string.settings_in_phone_contacts), if (copy) stringResource(R.string.settings_in_phone_contacts_on)
            else stringResource(R.string.settings_in_phone_contacts_off), copy) { on ->
            when { !on -> { ContactsAccount.set(context, false); version.intValue++ }; writes -> { ContactsAccount.set(context, true); version.intValue++ }; else -> askWrite.launch(Manifest.permission.WRITE_CONTACTS) }
        }
        ChoiceRow(stringResource(R.string.settings_birthdays_into), if (cals.isEmpty()) stringResource(R.string.settings_no_calendars) else stringResource(R.string.settings_birthdays_summary),
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
    SettingsSection(null, stringResource(R.string.settings_data_note)) {
        SettingRow(stringResource(R.string.settings_data_setup), stringResource(R.string.settings_data_setup_summary), onClick = { openData(context, "setup") }) { Chevron() }
        StatusRow(stringResource(R.string.settings_usage_access), if (access) stringResource(R.string.common_on) else stringResource(R.string.settings_usage_access_off), access, stringResource(R.string.common_settings)) {
            runCatching { context.startActivity(DataStats.accessIntent(context)) }.onFailure { context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
        SettingRow(stringResource(R.string.settings_your_plan), plan?.let { p -> stringResource(R.string.settings_plan_renews, when (p.kind) {
            DataPlan.UNLIMITED -> stringResource(R.string.settings_unlimited); DataPlan.FAIR_USE -> stringResource(R.string.settings_fair_use, DataRules.bytes(p.size)); else -> DataRules.bytes(p.size)
        }, DataRules.cycle(p, java.time.LocalDate.now()).end.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()))) } ?: stringResource(R.string.settings_not_set_up), onClick = { openData(context, "setup") }) { Chevron() }
    }
    SettingsSection(stringResource(R.string.settings_warnings)) {
        SettingRow(stringResource(R.string.settings_warnings), plan?.takeIf { it.limited }?.warn?.joinToString(" · ") { "$it %" } ?: stringResource(R.string.common_none), onClick = { openData(context, "setup") }) { Chevron() }
        var budget by remember { mutableStateOf(prefs.getBoolean("warnBudget", false)) }
        SwitchRow(stringResource(R.string.settings_day_budget), null, budget) { budget = it; prefs.edit().putBoolean("warnBudget", it).apply() }
        SettingRow(stringResource(R.string.settings_roaming), plan?.let { p -> stringResource(R.string.settings_roaming_summary, if (p.passPrice > 0) stringResource(R.string.settings_day_pass, p.currency + "%.0f".format(p.passPrice), DataRules.bytes(p.passSize)) else stringResource(R.string.settings_no_day_pass)) } ?: stringResource(R.string.settings_not_set_up), onClick = { openData(context, "setup") }) { Chevron() }
    }
    SettingsSection(stringResource(R.string.settings_live)) {
        var live by remember { mutableStateOf(DataSpeed.enabled(context)) }
        SwitchRow(stringResource(R.string.settings_live_speed), stringResource(R.string.settings_live_speed_summary), live) { live = it; DataSpeed.setEnabled(context, it) }
        var drops by remember { mutableStateOf(DataSignal.summaryOn(context)) }
        SwitchRow(stringResource(R.string.settings_signal_drops), stringResource(R.string.settings_signal_drops_summary), drops) { drops = it; DataSignal.setSummary(context, it) }
        val located = remember(back.intValue) { context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        StatusRow(stringResource(R.string.settings_wifi_around), if (located) stringResource(R.string.settings_wifi_around_on) else stringResource(R.string.settings_wifi_around_off), located, if (located) null else stringResource(R.string.common_settings)) {
            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)))
        }
        SettingRow(stringResource(R.string.settings_open_data), null, onClick = { openData(context) }) { Chevron() }
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
        SettingRow(stringResource(R.string.settings_no_calls), stringResource(R.string.settings_no_calls_summary))
    } else {
        val dialer = PhonePrefs.isDialer(context)
        SettingsSection(stringResource(R.string.settings_phone_app)) {
            SettingRow(if (dialer) stringResource(R.string.settings_is_dialer) else stringResource(R.string.settings_not_dialer),
                if (dialer) stringResource(R.string.settings_is_dialer_summary)
                else stringResource(R.string.settings_not_dialer_summary)) {
                TextButton(onClick = {
                    if (dialer) runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    else askRole.launch(context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER))
                }) { Text(if (dialer) stringResource(R.string.settings_change) else stringResource(R.string.settings_make_it), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            SettingRow(stringResource(R.string.settings_open_phone), stringResource(R.string.settings_open_phone_summary), onClick = { context.startActivity(Intent(context, PhoneActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Chevron() }
        }
        var answer by remember { mutableStateOf(PhonePrefs.buzzAnswer(context)) }
        var end by remember { mutableStateOf(PhonePrefs.buzzEnd(context)) }
        var unknown by remember { mutableStateOf(PhonePrefs.silenceUnknown(context)) }
        var hidden by remember { mutableStateOf(PhonePrefs.declineHidden(context)) }
        SettingsSection(stringResource(R.string.settings_calls)) {
            SwitchRow(stringResource(R.string.settings_buzz_answer), stringResource(R.string.settings_buzz_answer_summary), answer) { answer = it; PhonePrefs.setBuzzAnswer(context, it) }
            SwitchRow(stringResource(R.string.settings_buzz_end), stringResource(R.string.settings_buzz_end_summary), end) { end = it; PhonePrefs.setBuzzEnd(context, it) }
            SwitchRow(stringResource(R.string.settings_silence_unknown), stringResource(R.string.settings_silence_unknown_summary), unknown) { unknown = it; PhonePrefs.setSilenceUnknown(context, it) }
            SwitchRow(stringResource(R.string.settings_decline_hidden), stringResource(R.string.settings_decline_hidden_summary), hidden) { hidden = it; PhonePrefs.setDeclineHidden(context, it) }
            var raise by remember { mutableStateOf(PhonePrefs.raiseAnswer(context)) }
            var flip by remember { mutableStateOf(PhonePrefs.flipSilence(context)) }
            SwitchRow(stringResource(R.string.phone_raise_answer), stringResource(R.string.phone_raise_answer_about), raise) { raise = it; PhonePrefs.setRaiseAnswer(context, it) }
            SwitchRow(stringResource(R.string.phone_flip_silence), stringResource(R.string.phone_flip_silence_about), flip) { flip = it; PhonePrefs.setFlipSilence(context, it) }
        }
    }
    // calls from the computer (was in Settings › Contacts): one permission for Tetra (Android's "Phone"), not one per contact
    val calls = PhoneCall.allAllowed(context)
    SettingsSection(stringResource(R.string.settings_with_computer), (if (calls) stringResource(R.string.settings_with_computer_on)
        else stringResource(R.string.settings_with_computer_off)) + " " + stringResource(R.string.settings_with_computer_sound)) {
        // the on/off (the user, 25 Oct: "the on/off was a different thing" from the permission below)
        var pc by remember { mutableStateOf(PhonePrefs.pcCalls(context)) }
        SwitchRow(stringResource(R.string.settings_pc_calls), if (pc) stringResource(R.string.settings_pc_calls_on)
            else stringResource(R.string.settings_pc_calls_off), pc) {
            pc = it; PhonePrefs.setPcCalls(context, it)
            if (it && !calls) askCalls.launch(PhoneCall.PERMISSIONS) // turned on: Android's permission asked then (the user, 25 Oct)
        }
        StatusRow(stringResource(R.string.settings_phone_permission), if (calls) stringResource(R.string.settings_phone_permission_on)
            else stringResource(R.string.settings_phone_permission_off), true, if (calls) stringResource(R.string.common_settings) else stringResource(R.string.common_allow)) {
            if (calls) runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            else askCalls.launch(PhoneCall.PERMISSIONS)
        }
    }
}
