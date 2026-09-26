package com.kalotrapezis.drive

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.UUID

// Notes (asked 2026-09-26): the same notes as the computer's, in Files' hidden .notes folder (Notes.kt). No folders,
// labels only; an island for Home / Pinned / Archived / Trash, pulled up for the pins and labels, and an editor with
// one line of tools whose full set slides up from the bottom.

private enum class NotesView(val label: String) { Home("Home"), Archived("Archived"), Trash("Trash") }

// Keep's palette, which the imported notes already use.
private val NOTE_COLORS = listOf("#F28B82", "#FBBC04", "#FFF475", "#CCFF90", "#A7FFEB", "#CBF0F8", "#AECBFA", "#D7AEFB", "#FDCFE8", "#E6C9A8", "#E8EAED")
private fun noteColor(hex: String?): Color? = hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
private val InkOnColor = Color(0xFF1B1D1F)
// Sheets are solid: over a page of coloured notes a see-through one was hard to read (asked 2026-09-26).
private val SheetColor = Color(0xFF16191A)

internal fun notesStore() = NotesStore(File(TetraFolder.root(Environment.getExternalStorageDirectory()), ".notes"))

private fun inView(n: Note, v: NotesView) = when (v) {
    NotesView.Trash -> n.trashedAt != null
    NotesView.Archived -> n.trashedAt == null && n.archivedAt != null
    NotesView.Home -> n.trashedAt == null && n.archivedAt == null
}
private fun fold(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

/** `start`: true opens a new checklist, false a new note, null the list (the home card's three doors). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NotesTab(back: () -> Unit, start: Boolean?, hasAccess: Boolean, grant: () -> Unit) {
    val store = remember { notesStore() }
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var notes by remember { mutableStateOf<List<Note>?>(null) }
    var view by remember { mutableStateOf(NotesView.Home) }
    var label by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var drawer by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Note?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(version, hasAccess) { if (hasAccess) notes = withContext(Dispatchers.IO) { store.sweep(); store.list() } }
    fun create(checklist: Boolean) = scope.launch {
        open = withContext(Dispatchers.IO) { store.create(checklist, listOfNotNull(label)) }
    }
    LaunchedEffect(start) { if (hasAccess && start != null) create(start) }
    LaunchedEffect(message) { if (message != null) { delay(3000); message = null } }

    if (!hasAccess) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
            Text("Notes live in Tetra's folder, next to Files, so they can sync with the computer. Allow access to all files to use them.")
            TextButton(onClick = grant) { Text("Allow access") }
        }
        return
    }
    open?.let { note ->
        NoteEditor(store, note, onClose = { saved, said ->
            open = null; version++
            message = said
            // Nothing written: no note.
            if (said == null && saved != null && saved.empty) scope.launch(Dispatchers.IO) { store.remove(saved.id); withContext(Dispatchers.Main) { version++ } }
        })
        return
    }
    BackHandler { when { selected.isNotEmpty() -> selected = emptySet(); drawer -> drawer = false; searching -> { searching = false; query = "" }; label != null -> label = null; view != NotesView.Home -> view = NotesView.Home; else -> back() } }

    val all = notes.orEmpty()
    val q = fold(query.trim())
    val shown = all.filter { n -> inView(n, view) && (label == null || label in n.labels) &&
        (q.isEmpty() || fold(listOf(n.title, n.content, n.labels.joinToString(" "), n.items.joinToString(" ") { it.text }).joinToString(" ")).contains(q)) }
        .sortedWith(if (view == NotesView.Trash) compareByDescending { it.trashedAt } else compareByDescending { it.updatedAt })
    // Pinned on top of Home, the rest below them (asked 2026-09-26).
    val pinned = if (view == NotesView.Home) shown.filter { it.pinned } else emptyList()
    val others = shown - pinned.toSet()
    val labels = all.filter { it.trashedAt == null }.flatMap { it.labels }.groupingBy { it }.eachCount().toSortedMap()
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    val picked = all.filter { it.id in selected }
    fun act(said: String, change: (org.json.JSONObject) -> Unit) = scope.launch(Dispatchers.IO) {
        picked.forEach { store.update(it.id, change) }
        withContext(Dispatchers.Main) { selected = emptySet(); version++; message = said }
    }
    val card: @Composable (Note) -> Unit = { n ->
        NoteCard(n, selected = n.id in selected, selecting = selected.isNotEmpty(), longPress = { selected = if (n.id in selected) selected - n.id else selected + n.id }) {
            if (selected.isEmpty()) open = n else selected = if (n.id in selected) selected - n.id else selected + n.id
        }
    }
    val header: @Composable (String) -> Unit = { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, top = 8.dp)) }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(165.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
                        Text(label ?: if (view == NotesView.Home) "Notes" else view.label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (view == NotesView.Trash && shown.isNotEmpty()) TextButton(onClick = {
                            scope.launch(Dispatchers.IO) { shown.forEach { store.remove(it.id) }; withContext(Dispatchers.Main) { version++; message = "Notes Trash emptied" } }
                        }) { Text("Empty") }
                    }
                    if (searching) NoteField(query, { query = it }, "Search notes", Modifier.fillMaxWidth(), autofocus = true)
                    if (label != null) Chip("$label  ✕", on = true) { label = null }
                }
            }
            if (notes == null) item(span = StaggeredGridItemSpan.FullLine) { Text("Loading…", Modifier.padding(24.dp)) }
            else if (shown.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                Text(when {
                    q.isNotEmpty() -> "No note matches."
                    view == NotesView.Trash -> "The Trash is empty. Notes stay here 30 days."
                    view == NotesView.Archived -> "Nothing archived."
                    else -> "No notes yet. Start one below."
                }, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (pinned.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) { header("Pinned") }
                items(pinned, key = { it.id }) { card(it) }
                if (others.isNotEmpty()) item(span = StaggeredGridItemSpan.FullLine) { header("Others") }
            }
            items(others, key = { it.id }) { card(it) }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom,
        ) {
            if (selected.isNotEmpty()) Surface(shape = MaterialTheme.shapes.extraLarge, color = islandColor(), contentColor = islandContentColor()) {
                // What you picked: the navigation gives way to what can be done with it.
                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected = emptySet() }) { Icon(painterResource(R.drawable.ic_cancel), contentDescription = "Clear selection") }
                    Text("${selected.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
                    if (view == NotesView.Trash) {
                        IconButton(onClick = { act("Restored") { it.remove("trashedAt") } }) { Icon(painterResource(R.drawable.ic_restore), contentDescription = "Restore") }
                        IconButton(onClick = { scope.launch(Dispatchers.IO) { picked.forEach { store.remove(it.id) }; withContext(Dispatchers.Main) { selected = emptySet(); version++; message = "Deleted" } } }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete for good", tint = Color(0xFFE35A4F)) }
                    } else {
                        val allPinned = picked.all { it.pinned }
                        IconButton(onClick = { act(if (allPinned) "Unpinned" else "Pinned") { it.put("isPinned", !allPinned) } }) { Icon(painterResource(R.drawable.ic_push_pin), contentDescription = if (allPinned) "Unpin" else "Pin") }
                        if (view == NotesView.Archived) IconButton(onClick = { act("Unarchived") { it.remove("archivedAt") } }) { Icon(painterResource(R.drawable.ic_unarchive), contentDescription = "Unarchive") }
                        else IconButton(onClick = { act("Archived") { it.put("archivedAt", System.currentTimeMillis()) } }) { Icon(painterResource(R.drawable.ic_archive), contentDescription = "Archive") }
                        IconButton(onClick = { act("Moved to Trash") { it.put("trashedAt", System.currentTimeMillis()) } }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = "Move to Trash") }
                    }
                }
            } else IslandBottomBar(expand = { drawer = true }, visible = !drawer) {
                // Home, then the two ways to start; a tablet has room for Archive and Trash too, a phone keeps them in the drawer.
                IslandNavigationItem("Home", view == NotesView.Home, R.drawable.ic_home) { view = NotesView.Home }
                IslandNavigationItem("New note", false, R.drawable.ic_note_add) { create(false) }
                IslandNavigationItem("Checklist", false, R.drawable.ic_checklist) { create(true) }
                if (wide) {
                    IslandNavigationItem("Archived", view == NotesView.Archived, R.drawable.ic_archive) { view = NotesView.Archived }
                    IslandNavigationItem("Trash", view == NotesView.Trash, R.drawable.ic_delete) { view = NotesView.Trash }
                }
            }
            // Search where it is everywhere else in the app: bottom right.
            if (selected.isEmpty() && !drawer) RoundIsland(R.drawable.ic_search, "Search notes") { searching = !searching; if (!searching) query = "" }
        }
        message?.let { Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)) { Text(it, Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) } }
    }

    if (drawer) ModalBottomSheet(onDismissRequest = { drawer = false }, containerColor = SheetColor, contentColor = islandContentColor()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!wide) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrawerButton(R.drawable.ic_archive, "Archived", view == NotesView.Archived, Modifier.weight(1f)) { view = NotesView.Archived; drawer = false }
                DrawerButton(R.drawable.ic_delete, "Trash", view == NotesView.Trash, Modifier.weight(1f)) { view = NotesView.Trash; drawer = false }
            }
            // The labels, one under another in their own panel (the user's sketch, 2026-09-26).
            Surface(color = Color.White.copy(alpha = 0.08f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    LabelRow("All notes", null, label == null) { label = null; drawer = false }
                    labels.forEach { (l, count) -> LabelRow(l, count, label == l) { label = l; drawer = false } }
                }
            }
        }
    }
}

@Composable private fun LabelRow(name: String, count: Int?, on: Boolean, click: () -> Unit) = Row(
    Modifier.fillMaxWidth().clickable(onClick = click).background(if (on) Color.White.copy(alpha = 0.14f) else Color.Transparent).padding(horizontal = 18.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
) {
    Icon(painterResource(R.drawable.ic_label), contentDescription = null, modifier = Modifier.size(20.dp))
    Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (count != null) Text("$count", style = MaterialTheme.typography.labelMedium, color = islandContentColor().copy(alpha = 0.6f))
}

@Composable private fun DrawerButton(icon: Int, label: String, on: Boolean, modifier: Modifier, click: () -> Unit) = Surface(
    color = if (on) driveNavigationSelectedColor() else Color.White.copy(alpha = 0.10f),
    contentColor = if (on) driveNavigationSelectedContentColor() else islandContentColor(),
    shape = MaterialTheme.shapes.large, modifier = modifier.clickable(onClick = click),
) {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null); Text(label, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable private fun RoundIsland(icon: Int, description: String, click: () -> Unit) =
    Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
        IconButton(onClick = click) { Icon(painterResource(icon), contentDescription = description) }
    }

@Composable private fun Chip(text: String, on: Boolean, click: () -> Unit) = Surface(
    color = if (on) driveNavigationSelectedColor() else Color.White.copy(alpha = 0.10f),
    contentColor = if (on) driveNavigationSelectedContentColor() else islandContentColor(),
    shape = CircleShape, modifier = Modifier.clickable(onClick = click),
) { Text(text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge) }

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun NoteCard(n: Note, small: Boolean = false, selected: Boolean = false, selecting: Boolean = false, longPress: (() -> Unit)? = null, open: () -> Unit) {
    val tint = noteColor(n.color)
    Surface(
        color = tint ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentColor = if (tint != null) InkOnColor else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
            .then(if (selected) Modifier.border(3.dp, Color.White, MaterialTheme.shapes.large) else Modifier)
            .combinedClickable(onClick = open, onLongClick = longPress),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // A check while choosing, as on photos.
                if (selecting) Box(
                    Modifier.padding(end = 8.dp).size(22.dp).then(if (selected) Modifier.background(Color.White, CircleShape) else Modifier.border(2.dp, LocalContentColor.current.copy(alpha = 0.6f), CircleShape)),
                    contentAlignment = Alignment.Center,
                ) { if (selected) Text("✓", color = Color.Black, style = MaterialTheme.typography.labelMedium) }
                if (n.title.isNotBlank()) Text(n.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                else Spacer(Modifier.weight(1f))
                if (n.pinned && !small) Icon(painterResource(R.drawable.ic_push_pin), contentDescription = "Pinned", modifier = Modifier.size(16.dp))
            }
            NoteFormat.preview(n, if (small) 3 else 8).forEach { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            if (!small && n.labels.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                n.labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(Color(0x33888888), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp)) }
            }
        }
    }
}

@Composable private fun NoteField(value: String, change: (String) -> Unit, hint: String, modifier: Modifier = Modifier, autofocus: Boolean = false) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autofocus) focus.requestFocus() }
    Surface(color = Color.White.copy(alpha = 0.08f), shape = CircleShape, modifier = modifier) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            if (value.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            BasicTextField(value, change, singleLine = true, textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth().focusRequester(focus))
        }
    }
}

private data class Snap(val title: String, val body: String, val items: List<CheckItem>)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NoteEditor(store: NotesStore, initial: Note, onClose: (Note?, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial.title) }
    var body by remember { mutableStateOf(TextFieldValue(initial.content)) }
    var items by remember { mutableStateOf(initial.items) }
    var labels by remember { mutableStateOf(initial.labels) }
    var color by remember { mutableStateOf(initial.color) }
    var pinned by remember { mutableStateOf(initial.pinned) }
    var archived by remember { mutableStateOf(initial.archivedAt != null) }
    var reading by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<NoteVersion>?>(null) }
    var tagsOpen by remember { mutableStateOf(false) }
    var formatting by remember { mutableStateOf(false) }
    val bodyFocus = remember { FocusRequester() }
    val imeOpen = WindowInsets.isImeVisible
    var toBody by remember { mutableIntStateOf(0) } // a checklist's first item takes the focus when this counts up
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var changed by remember { mutableStateOf(false) }
    var snapped by remember { mutableStateOf(false) }
    var undoTick by remember { mutableIntStateOf(0) }
    val undo = remember { NoteUndo(Snap(title, body.text, items)) }
    val trashed = initial.trashedAt != null
    val tint = noteColor(color)
    val ink = if (tint != null) InkOnColor else MaterialTheme.colorScheme.onSurface

    // Saved as you type (a short pause), the whole text each time.
    LaunchedEffect(title, body.text, items) {
        if (!changed) return@LaunchedEffect
        delay(400)
        withContext(Dispatchers.IO) {
            // The note as it was when opened goes to its history before the first change is written over it.
            if (!snapped) { store.snapshot(initial.id); snapped = true }
            store.setText(initial.id, title, body.text, if (initial.checklist) items else null)
        }
    }
    fun edit(t: String = title, b: TextFieldValue = body, i: List<CheckItem> = items, step: Boolean = false) {
        val snap = Snap(t, b.text, i)
        val done = NoteUndo.endsWord(title, t, t.length) || NoteUndo.endsWord(body.text, b.text, b.selection.start)
        if (step) undo.step(snap) else if (snap != Snap(title, body.text, items)) undo.push(snap, wordDone = done)
        title = t; body = b; items = i; changed = true; undoTick++
    }
    fun apply(s: Snap?) { if (s == null) return; title = s.title; body = TextFieldValue(s.body, TextRange(s.body.length)); items = s.items; changed = true; undoTick++ }
    fun meta(change: (org.json.JSONObject) -> Unit) { changed = true; scope.launch(Dispatchers.IO) { store.update(initial.id, change) } }
    // Leaving: what is pending is saved, and the note as it now is goes to its history, if anything changed.
    fun leave(said: String? = null, last: ((org.json.JSONObject) -> Unit)? = null) = scope.launch {
        val saved = withContext(Dispatchers.IO) {
            if (changed) { if (!snapped) { store.snapshot(initial.id); snapped = true }; store.setText(initial.id, title, body.text, if (initial.checklist) items else null) }
            last?.let { store.update(initial.id, it) }
            if (changed) store.snapshot(initial.id)
            store.get(initial.id)
        }
        onClose(saved, said)
    }
    fun archive() { if (archived) { archived = false; meta { it.remove("archivedAt") } } else leave("Note archived") { it.put("archivedAt", System.currentTimeMillis()) } }
    fun toTrash() { leave("Note moved to Trash") { it.put("trashedAt", System.currentTimeMillis()) } }
    fun format(f: (NoteFormat.Edit) -> NoteFormat.Edit) {
        val r = f(NoteFormat.Edit(body.text, body.selection.min, body.selection.max))
        edit(b = TextFieldValue(r.text, TextRange(r.start, r.end)), step = true)
    }
    BackHandler { if (tools) tools = false else leave() }

    Box(Modifier.fillMaxSize().background(tint ?: MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = if (imeOpen) 64.dp else 120.dp)) {
            // Enter in the title goes on to the body (asked 2026-09-26): the text, or a checklist's first item.
            BasicTextField(title, { t ->
                if ('\n' in t) { if (initial.checklist) toBody++ else bodyFocus.requestFocus() } else edit(t = t)
            }, readOnly = trashed, singleLine = false, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { if (initial.checklist) toBody++ else bodyFocus.requestFocus() }),
                textStyle = TextStyle(color = ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold), cursorBrush = SolidColor(ink),
                decorationBox = { inner -> Box { if (title.isEmpty()) Text("Title", color = ink.copy(alpha = 0.45f), fontSize = 24.sp); inner() } },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            when {
                initial.checklist -> ChecklistEditor(items, ink, trashed, toBody) { next, step -> edit(i = next, step = step) }
                reading -> Text(renderMarkdown(body.text), color = ink, style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp), modifier = Modifier.padding(bottom = 80.dp))
                else -> BasicTextField(body, { edit(b = it) }, readOnly = trashed,
                    textStyle = TextStyle(color = ink, fontSize = 16.sp, lineHeight = 24.sp), cursorBrush = SolidColor(ink),
                    visualTransformation = markdownVisualTransformation(),
                    decorationBox = { inner -> Box { if (body.text.isEmpty()) Text("Write here. **bold**, - [ ] a checkbox…", color = ink.copy(alpha = 0.45f)); inner() } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp).padding(bottom = 80.dp).focusRequester(bodyFocus))
            }
            if (labels.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                labels.forEach { Text(it, color = ink, style = MaterialTheme.typography.labelMedium, modifier = Modifier.background(Color(0x33888888), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) }
            }
        }
        // The tools are an island at the bottom, like the rest of Tetra: one line of the basics, pulled up (or its grip
        // tapped) for everything else (asked 2026-09-26: not a bar at the top with the rest at the bottom).
        val w = islandContentColor()
        // With the keyboard up the island would take half of what is left: like Google Keep, it becomes one thin strip
        // on the keyboard — the tools for writing, and the rest one tap away (asked 2026-09-26).
        // Two islands in one: the note's own controls, or the formatting tools, swapped by a button (asked 2026-09-26).
        // With the keyboard up it is one thin strip on the keyboard, as in Google Keep; otherwise a floating island,
        // out of the way while a sheet or dialog is up.
        val canFormat = !initial.checklist && !reading && !trashed
        val row: @Composable () -> Unit = {
            undoTick // read, so undo and redo follow the history, which is not state itself
            if (formatting && canFormat) {
                Tool(R.drawable.ic_title, "Heading", w) { format { NoteFormat.prefix(it, "# ") } }
                Tool(R.drawable.ic_format_bold, "Bold", w) { format { NoteFormat.wrap(it, "**") } }
                Tool(R.drawable.ic_format_italic, "Italic", w) { format { NoteFormat.wrap(it, "*") } }
                Tool(R.drawable.ic_format_strikethrough, "Strikethrough", w) { format { NoteFormat.wrap(it, "~~") } }
                Tool(R.drawable.ic_format_list_bulleted, "Bulleted list", w) { format { NoteFormat.prefix(it, "- ") } }
                Tool(R.drawable.ic_format_list_numbered, "Numbered list", w) { format { NoteFormat.prefix(it, "1. ") } }
                Tool(R.drawable.ic_check_box, "Checkbox list", w) { format { NoteFormat.prefix(it, "- [ ] ") } }
                Tool(R.drawable.ic_format_quote, "Quote", w) { format { NoteFormat.prefix(it, "> ") } }
                Tool(R.drawable.ic_code, "Code", w) { format { NoteFormat.wrap(it, "`") } }
                Tool(R.drawable.ic_link, "Link", w) { format { e -> val t = e.text.substring(e.start, e.end).ifEmpty { "link" }; val md = "[$t](https://)"; NoteFormat.Edit(e.text.substring(0, e.start) + md + e.text.substring(e.end), e.start + t.length + 3, e.start + md.length - 1) } }
                Tool(R.drawable.ic_horizontal_rule, "Divider", w) { format { e -> NoteFormat.Edit(e.text.substring(0, e.end) + "\n\n---\n" + e.text.substring(e.end), e.end + 6, e.end + 6) } }
                Tool(R.drawable.ic_format_indent_increase, "Indent", w) { format { NoteFormat.indent(it, false) } }
                Tool(R.drawable.ic_format_indent_decrease, "Outdent", w) { format { NoteFormat.indent(it, true) } }
            } else if (!trashed) {
                Tool(R.drawable.ic_undo, "Undo", w, enabled = undo.canUndo) { apply(undo.undo()) }
                Tool(R.drawable.ic_redo, "Redo", w, enabled = undo.canRedo) { apply(undo.redo()) }
                Tool(R.drawable.ic_label, "Tags", w, on = labels.isNotEmpty()) { tagsOpen = true }
                Tool(R.drawable.ic_history, "History", w) { scope.launch { history = withContext(Dispatchers.IO) { store.history(initial.id) } } }
                if (!initial.checklist) Tool(if (reading) R.drawable.ic_edit_note else R.drawable.ic_visibility, if (reading) "Edit" else "Read", w, on = reading) { reading = !reading }
                Tool(R.drawable.ic_push_pin, if (pinned) "Unpin" else "Pin", w, on = pinned) { pinned = !pinned; meta { it.put("isPinned", pinned) } }
                if (wide) {
                    Tool(if (archived) R.drawable.ic_unarchive else R.drawable.ic_archive, if (archived) "Unarchive" else "Archive", w) { archive() }
                    Tool(R.drawable.ic_delete, "Move to Trash", w) { toTrash() }
                }
            }
        }
        val swap: @Composable () -> Unit = { if (canFormat) Tool(R.drawable.ic_swap_horiz, if (formatting) "Note controls" else "Formatting tools", w, on = formatting) { formatting = !formatting } }
        val screen = LocalConfiguration.current.screenWidthDp.dp
        if (imeOpen && !trashed) Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(islandColor()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            swap()
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { row() }
            Tool(R.drawable.ic_keyboard_arrow_up, "More tools", w) { tools = true }
        } else Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) {
            IslandBottomBar(expand = { if (!trashed) tools = true }, visible = !tools && !tagsOpen && history == null) {
                Tool(R.drawable.ic_chevron_left, "Back", w) { leave() }
                if (trashed) {
                    TextButton(onClick = { leave("Note restored") { it.remove("trashedAt") } }) { Text("Restore", color = w) }
                    TextButton(onClick = { scope.launch(Dispatchers.IO) { store.remove(initial.id); withContext(Dispatchers.Main) { onClose(null, "Note deleted") } } }) { Text("Delete", color = Color(0xFFE35A4F)) }
                } else {
                    swap()
                    Row(Modifier.widthIn(max = screen - 150.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { row() }
                }
            }
        }
    }

    if (tools) ModalBottomSheet(onDismissRequest = { tools = false }, containerColor = SheetColor, contentColor = islandContentColor()) {
        // Big tiles, as in Files' tools sheet (asked 2026-09-26: the small ones were too small on a phone).
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!initial.checklist && !reading) {
                Text("Text", style = MaterialTheme.typography.titleSmall)
                val textTools: List<Triple<Int, String, () -> Unit>> = listOf(
                    Triple(R.drawable.ic_title, "Heading") { format { NoteFormat.prefix(it, "# ") } },
                    Triple(R.drawable.ic_format_bold, "Bold") { format { NoteFormat.wrap(it, "**") } },
                    Triple(R.drawable.ic_format_italic, "Italic") { format { NoteFormat.wrap(it, "*") } },
                    Triple(R.drawable.ic_format_strikethrough, "Strikethrough") { format { NoteFormat.wrap(it, "~~") } },
                    Triple(R.drawable.ic_code, "Code") { format { NoteFormat.wrap(it, "`") } },
                    Triple(R.drawable.ic_format_list_bulleted, "Bulleted list") { format { NoteFormat.prefix(it, "- ") } },
                    Triple(R.drawable.ic_format_list_numbered, "Numbered list") { format { NoteFormat.prefix(it, "1. ") } },
                    Triple(R.drawable.ic_check_box, "Checkbox list") { format { NoteFormat.prefix(it, "- [ ] ") } },
                    Triple(R.drawable.ic_format_quote, "Quote") { format { NoteFormat.prefix(it, "> ") } },
                    Triple(R.drawable.ic_link, "Link") { format { e -> val t = e.text.substring(e.start, e.end).ifEmpty { "link" }; val md = "[$t](https://)"; NoteFormat.Edit(e.text.substring(0, e.start) + md + e.text.substring(e.end), e.start + t.length + 3, e.start + md.length - 1) } },
                    Triple(R.drawable.ic_horizontal_rule, "Divider") { format { e -> NoteFormat.Edit(e.text.substring(0, e.end) + "\n\n---\n" + e.text.substring(e.end), e.end + 6, e.end + 6) } },
                    Triple(R.drawable.ic_format_indent_increase, "Indent") { format { NoteFormat.indent(it, false) } },
                    Triple(R.drawable.ic_format_indent_decrease, "Outdent") { format { NoteFormat.indent(it, true) } },
                )
                textTools.chunked(if (wide) 7 else 4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (icon, name, run) -> DriveActionTile(icon, name, run) }
                        repeat((if (wide) 7 else 4) - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text("Colour", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (listOf<String?>(null) + NOTE_COLORS).forEach { hex ->
                    Box(Modifier.size(44.dp).background(noteColor(hex) ?: Color(0xFF3A3F43), CircleShape)
                        .then(if (color == hex) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                        .clickable { color = hex; meta { if (hex == null) it.remove("color") else it.put("color", hex) } })
                }
            }
            // A tablet has Archive and Trash on the island; a phone has them here.
            if (!wide) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { DriveWideAction(if (archived) R.drawable.ic_unarchive else R.drawable.ic_archive, if (archived) "Unarchive" else "Archive") { tools = false; archive() } }
                Box(Modifier.weight(1f)) { DriveWideAction(R.drawable.ic_delete, "Trash") { tools = false; toTrash() } }
            }
        }
    }

    if (tagsOpen) NoteTagsSheet(store, labels, dismiss = { tagsOpen = false }) { chosen ->
        labels = chosen; tagsOpen = false; meta { it.put("labels", org.json.JSONArray(chosen)) }
    }

    history?.let { versions ->
        AlertDialog(
            onDismissRequest = { history = null },
            title = { Text("History") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The note as it was before you last changed it, and as you left it — the newest three. Restoring keeps the current one too.", style = MaterialTheme.typography.bodySmall)
                    if (versions.isEmpty()) Text("No versions yet.")
                    versions.forEach { v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(v.at), style = MaterialTheme.typography.titleSmall)
                                Text(v.title.ifBlank { "Untitled" } + " · " + (v.content.lines().firstOrNull { it.isNotBlank() } ?: v.items.firstOrNull()?.text.orEmpty()),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    val n = withContext(Dispatchers.IO) { store.restoreVersion(initial.id, v.name) }
                                    edit(t = n.title, b = TextFieldValue(n.content), i = n.items, step = true)
                                    history = null; tools = false
                                }
                            }) { Text("Restore") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { history = null }) { Text("Close") } },
        )
    }
}

@Composable private fun Tool(icon: Int, description: String, tint: Color, enabled: Boolean = true, on: Boolean = false, click: () -> Unit) =
    IconButton(onClick = click, enabled = enabled, modifier = Modifier.size(40.dp).then(if (on) Modifier.background(tint.copy(alpha = 0.15f), CircleShape) else Modifier)) {
        Icon(painterResource(icon), contentDescription = description, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(21.dp))
    }

/** Open items in their order, then the done ones under a line; Next on the keyboard makes the next item. */
@Composable private fun ChecklistEditor(items: List<CheckItem>, ink: Color, readOnly: Boolean, focusFirst: Int, change: (List<CheckItem>, Boolean) -> Unit) {
    val sorted = items.sortedForList()
    val open = sorted.filter { !it.checked }
    val done = sorted.filter { it.checked }
    var focus by remember { mutableStateOf<String?>(null) }
    fun insertAfter(id: String?) {
        val at = if (id == null) open.size else open.indexOfFirst { it.id == id } + 1
        val added = CheckItem(UUID.randomUUID().toString(), "", false, at)
        val list = (open.take(at) + added + open.drop(at)).mapIndexed { n, i -> i.copy(order = n) }
        change(list + done, true); focus = added.id
    }
    LaunchedEffect(focusFirst) { if (focusFirst > 0) { if (open.isEmpty()) insertAfter(null) else focus = open.first().id } }
    @Composable fun row(i: CheckItem) {
        val requester = remember(i.id) { FocusRequester() }
        LaunchedEffect(focus) { if (focus == i.id) { requester.requestFocus(); focus = null } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { change(items.map { if (it.id == i.id) it.copy(checked = !it.checked, order = if (it.checked) open.size else it.order) else it }, true) }, enabled = !readOnly) {
                Icon(painterResource(if (i.checked) R.drawable.ic_check_box else R.drawable.ic_check_box_outline_blank), contentDescription = if (i.checked) "Done" else "Not done", tint = ink)
            }
            BasicTextField(i.text, { t -> change(items.map { if (it.id == i.id) it.copy(text = t) else it }, false) }, readOnly = readOnly, singleLine = true,
                textStyle = TextStyle(color = ink.copy(alpha = if (i.checked) 0.55f else 1f), fontSize = 16.sp, textDecoration = if (i.checked) TextDecoration.LineThrough else null),
                cursorBrush = SolidColor(ink), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { insertAfter(i.id) }),
                modifier = Modifier.weight(1f).focusRequester(requester).padding(vertical = 10.dp))
            if (!readOnly) IconButton(onClick = { change(items.filter { it.id != i.id }, true) }) { Icon(painterResource(R.drawable.ic_cancel), contentDescription = "Remove item", tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) }
        }
    }
    Column {
        open.forEach { row(it) }
        if (!readOnly) TextButton(onClick = { insertAfter(null) }) { Icon(painterResource(R.drawable.ic_add), null, tint = ink); Text("  Add item", color = ink) }
        if (done.isNotEmpty()) {
            Text("${done.size} done", color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            done.forEach { row(it) }
        }
        Spacer(Modifier.heightIn(min = 80.dp))
    }
}

/** Tags for a note, chosen or created: the same sheet as a file's tags in Files (asked 2026-09-26). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun NoteTagsSheet(store: NotesStore, initial: List<String>, dismiss: () -> Unit, save: (List<String>) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    var newTag by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val known = remember { store.list().flatMap { it.labels } }
    val all = (known + selected).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = SheetColor, contentColor = islandContentColor()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tags", style = MaterialTheme.typography.titleLarge)
            Text("Select existing tags, or create one for this note.")
            if (all.isNotEmpty()) FlowRow(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                all.forEach { tag ->
                    Surface(
                        color = if (tag in selected) driveNavigationSelectedColor() else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (tag in selected) driveNavigationSelectedContentColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier.clickable { selected = if (tag in selected) selected - tag else selected + tag },
                    ) { Text("#$tag", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) }
                }
            }
            androidx.compose.material3.OutlinedTextField(newTag, { newTag = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("New tag") }, singleLine = true,
                isError = error != null, supportingText = error?.let { { Text(it) } })
            DriveWideAction(R.drawable.ic_tag, "Add tag") {
                val t = newTag.trim()
                if (t.isEmpty() || t.length > 60) error = "A tag is 1 to 60 characters." else {
                    selected = selected + (all.firstOrNull { it.equals(t, ignoreCase = true) } ?: t); newTag = ""
                }
            }
            DriveWideAction(R.drawable.ic_check, "Save tags") { save(selected.distinct()) }
        }
    }
}
