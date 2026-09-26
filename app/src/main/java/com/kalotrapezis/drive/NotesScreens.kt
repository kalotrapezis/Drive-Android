package com.kalotrapezis.drive

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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

private enum class NotesView(val label: String, val icon: Int) {
    Home("Home", R.drawable.ic_home), Pinned("Pinned", R.drawable.ic_push_pin),
    Archived("Archived", R.drawable.ic_archive), Trash("Trash", R.drawable.ic_delete),
}

// Keep's palette, which the imported notes already use.
private val NOTE_COLORS = listOf("#F28B82", "#FBBC04", "#FFF475", "#CCFF90", "#A7FFEB", "#CBF0F8", "#AECBFA", "#D7AEFB", "#FDCFE8", "#E6C9A8", "#E8EAED")
private fun noteColor(hex: String?): Color? = hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
private val InkOnColor = Color(0xFF1B1D1F)

internal fun notesStore() = NotesStore(File(TetraFolder.root(Environment.getExternalStorageDirectory()), ".notes"))

private fun inView(n: Note, v: NotesView) = when (v) {
    NotesView.Trash -> n.trashedAt != null
    NotesView.Archived -> n.trashedAt == null && n.archivedAt != null
    NotesView.Pinned -> n.trashedAt == null && n.archivedAt == null && n.pinned
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
    BackHandler { when { drawer -> drawer = false; searching -> { searching = false; query = "" }; label != null -> label = null; view != NotesView.Home -> view = NotesView.Home; else -> back() } }

    val all = notes.orEmpty()
    val q = fold(query.trim())
    val shown = all.filter { n -> inView(n, view) && (label == null || label in n.labels) &&
        (q.isEmpty() || fold(listOf(n.title, n.content, n.labels.joinToString(" "), n.items.joinToString(" ") { it.text }).joinToString(" ")).contains(q)) }
        .sortedWith(if (view == NotesView.Trash) compareByDescending { it.trashedAt } else compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    val labels = all.filter { it.trashedAt == null }.flatMap { it.labels }.groupingBy { it }.eachCount().toSortedMap()

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
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(painterResource(R.drawable.ic_search), contentDescription = "Search notes") }
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
                    view == NotesView.Pinned -> "Pin a note to keep it at hand."
                    else -> "No notes yet. Start one below."
                }, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(shown, key = { it.id }) { n -> NoteCard(n) { open = n } }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom,
        ) {
            IslandBottomBar(expand = { drawer = true }, visible = true) {
                NotesView.entries.forEach { v -> IslandNavigationItem(v.label, view == v, v.icon) { view = v } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundIsland(R.drawable.ic_note_add, "New note") { create(false) }
                RoundIsland(R.drawable.ic_checklist, "New checklist") { create(true) }
            }
        }
        message?.let { Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)) { Text(it, Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) } }
    }

    if (drawer) ModalBottomSheet(onDismissRequest = { drawer = false }, containerColor = islandColor(), contentColor = islandContentColor()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pinned", style = MaterialTheme.typography.titleMedium)
            val pinned = all.filter { it.pinned && it.trashedAt == null && it.archivedAt == null }
            if (pinned.isEmpty()) Text("Pin a note from its editor to find it here.", style = MaterialTheme.typography.bodyMedium)
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(pinned, key = { it.id }) { n -> Box(Modifier.width(170.dp)) { NoteCard(n, small = true) { drawer = false; open = n } } }
            }
            Text("Labels", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("All", on = label == null) { label = null; drawer = false }
                labels.forEach { (l, count) -> Chip("$l  $count", on = label == l) { label = l; drawer = false } }
            }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun NoteCard(n: Note, small: Boolean = false, open: () -> Unit) {
    val tint = noteColor(n.color)
    Surface(
        color = tint ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentColor = if (tint != null) InkOnColor else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().clickable(onClick = open),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
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
    var changed by remember { mutableStateOf(false) }
    var undoTick by remember { mutableIntStateOf(0) }
    val undo = remember { NoteUndo(Snap(title, body.text, items)) }
    val trashed = initial.trashedAt != null
    val tint = noteColor(color)
    val ink = if (tint != null) InkOnColor else MaterialTheme.colorScheme.onSurface

    // Saved as you type (a short pause), the whole text each time.
    LaunchedEffect(title, body.text, items) {
        if (!changed) return@LaunchedEffect
        delay(400)
        withContext(Dispatchers.IO) { store.setText(initial.id, title, body.text, if (initial.checklist) items else null) }
    }
    fun edit(t: String = title, b: TextFieldValue = body, i: List<CheckItem> = items, step: Boolean = false) {
        val snap = Snap(t, b.text, i)
        if (step) undo.step(snap) else if (snap != Snap(title, body.text, items)) undo.push(snap)
        title = t; body = b; items = i; changed = true; undoTick++
    }
    fun apply(s: Snap?) { if (s == null) return; title = s.title; body = TextFieldValue(s.body, TextRange(s.body.length)); items = s.items; changed = true; undoTick++ }
    fun meta(change: (org.json.JSONObject) -> Unit) { changed = true; scope.launch(Dispatchers.IO) { store.update(initial.id, change) } }
    // Leaving: what is pending is saved, and the note as it now is goes to its history, if anything changed.
    fun leave(said: String? = null, last: ((org.json.JSONObject) -> Unit)? = null) = scope.launch {
        val saved = withContext(Dispatchers.IO) {
            if (changed) store.setText(initial.id, title, body.text, if (initial.checklist) items else null)
            last?.let { store.update(initial.id, it) }
            if (changed) store.snapshot(initial.id)
            store.get(initial.id)
        }
        onClose(saved, said)
    }
    fun format(f: (NoteFormat.Edit) -> NoteFormat.Edit) {
        val r = f(NoteFormat.Edit(body.text, body.selection.min, body.selection.max))
        edit(b = TextFieldValue(r.text, TextRange(r.start, r.end)), step = true)
    }
    BackHandler { if (tools) tools = false else leave() }

    Column(Modifier.fillMaxSize().background(tint ?: MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        // One line of the basics; everything else slides up from the bottom.
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Tool(R.drawable.ic_chevron_left, "Back", ink) { leave() }
            undoTick // read, so the two buttons follow the undo history, which is not state itself
            Tool(R.drawable.ic_undo, "Undo", ink, enabled = undo.canUndo && !trashed) { apply(undo.undo()) }
            Tool(R.drawable.ic_redo, "Redo", ink, enabled = undo.canRedo && !trashed) { apply(undo.redo()) }
            if (!initial.checklist && !reading && !trashed) {
                Tool(R.drawable.ic_format_bold, "Bold", ink) { format { NoteFormat.wrap(it, "**") } }
                Tool(R.drawable.ic_check_box, "Checkbox list", ink) { format { NoteFormat.prefix(it, "- [ ] ") } }
            }
            Spacer(Modifier.weight(1f))
            if (trashed) {
                TextButton(onClick = { leave("Note restored") { it.remove("trashedAt") } }) { Text("Restore", color = ink) }
                TextButton(onClick = { scope.launch(Dispatchers.IO) { store.remove(initial.id); withContext(Dispatchers.Main) { onClose(null, "Note deleted") } } }) { Text("Delete", color = Color(0xFFE35A4F)) }
            } else {
                if (!initial.checklist) Tool(if (reading) R.drawable.ic_edit_note else R.drawable.ic_visibility, if (reading) "Edit" else "Read", ink) { reading = !reading }
                Tool(R.drawable.ic_push_pin, if (pinned) "Unpin" else "Pin", ink, on = pinned) { pinned = !pinned; meta { it.put("isPinned", pinned) } }
                Tool(R.drawable.ic_keyboard_arrow_up, "More tools", ink) { tools = true }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            BasicTextField(title, { edit(t = it) }, readOnly = trashed, singleLine = false,
                textStyle = TextStyle(color = ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold), cursorBrush = SolidColor(ink),
                decorationBox = { inner -> Box { if (title.isEmpty()) Text("Title", color = ink.copy(alpha = 0.45f), fontSize = 24.sp); inner() } },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            when {
                initial.checklist -> ChecklistEditor(items, ink, trashed) { next, step -> edit(i = next, step = step) }
                reading -> Text(renderMarkdown(body.text), color = ink, style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp), modifier = Modifier.padding(bottom = 80.dp))
                else -> BasicTextField(body, { edit(b = it) }, readOnly = trashed,
                    textStyle = TextStyle(color = ink, fontSize = 16.sp, lineHeight = 24.sp), cursorBrush = SolidColor(ink),
                    visualTransformation = markdownVisualTransformation(),
                    decorationBox = { inner -> Box { if (body.text.isEmpty()) Text("Write here. **bold**, - [ ] a checkbox…", color = ink.copy(alpha = 0.45f)); inner() } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp).padding(bottom = 80.dp))
            }
            if (labels.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                labels.forEach { Text(it, color = ink, style = MaterialTheme.typography.labelMedium, modifier = Modifier.background(Color(0x33888888), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) }
            }
        }
    }

    if (tools) ModalBottomSheet(onDismissRequest = { tools = false }, containerColor = islandColor(), contentColor = islandContentColor()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!initial.checklist && !reading) {
                Text("Text", style = MaterialTheme.typography.titleSmall)
                FlowRow {
                    val w = Color.White
                    Tool(R.drawable.ic_title, "Heading", w) { format { NoteFormat.prefix(it, "# ") } }
                    Tool(R.drawable.ic_format_bold, "Bold", w) { format { NoteFormat.wrap(it, "**") } }
                    Tool(R.drawable.ic_format_italic, "Italic", w) { format { NoteFormat.wrap(it, "*") } }
                    Tool(R.drawable.ic_format_strikethrough, "Strikethrough", w) { format { NoteFormat.wrap(it, "~~") } }
                    Tool(R.drawable.ic_code, "Code", w) { format { NoteFormat.wrap(it, "`") } }
                    Tool(R.drawable.ic_format_list_bulleted, "Bulleted list", w) { format { NoteFormat.prefix(it, "- ") } }
                    Tool(R.drawable.ic_format_list_numbered, "Numbered list", w) { format { NoteFormat.prefix(it, "1. ") } }
                    Tool(R.drawable.ic_check_box, "Checkbox list", w) { format { NoteFormat.prefix(it, "- [ ] ") } }
                    Tool(R.drawable.ic_format_quote, "Quote", w) { format { NoteFormat.prefix(it, "> ") } }
                    Tool(R.drawable.ic_link, "Link", w) { format { e -> val t = e.text.substring(e.start, e.end).ifEmpty { "link" }; val md = "[$t](https://)"; NoteFormat.Edit(e.text.substring(0, e.start) + md + e.text.substring(e.end), e.start + t.length + 3, e.start + md.length - 1) } }
                    Tool(R.drawable.ic_horizontal_rule, "Divider", w) { format { e -> NoteFormat.Edit(e.text.substring(0, e.end) + "\n\n---\n" + e.text.substring(e.end), e.end + 6, e.end + 6) } }
                    Tool(R.drawable.ic_format_indent_increase, "Indent", w) { format { NoteFormat.indent(it, false) } }
                    Tool(R.drawable.ic_format_indent_decrease, "Outdent", w) { format { NoteFormat.indent(it, true) } }
                }
            }
            Text("Colour", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<String?>(null) + NOTE_COLORS).forEach { hex ->
                    Box(Modifier.size(34.dp).background(noteColor(hex) ?: Color(0xFF3A3F43), CircleShape)
                        .then(if (color == hex) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                        .clickable { color = hex; meta { if (hex == null) it.remove("color") else it.put("color", hex) } })
                }
            }
            Text("Labels", style = MaterialTheme.typography.titleSmall)
            val allLabels = remember { store.list().flatMap { it.labels }.toSortedSet() }
            var newLabel by remember { mutableStateOf("") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (allLabels + labels).toSortedSet().forEach { l ->
                    Chip(l, on = l in labels) { labels = if (l in labels) labels - l else labels + l; val now = labels; meta { it.put("labels", org.json.JSONArray(now)) } }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteField(newLabel, { newLabel = it }, "New label", Modifier.weight(1f))
                TextButton(onClick = { val l = newLabel.trim(); if (l.isNotEmpty() && l !in labels) { labels = labels + l; val now = labels; meta { it.put("labels", org.json.JSONArray(now)) } }; newLabel = "" }) { Text("Add", color = Color.White) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { scope.launch { history = withContext(Dispatchers.IO) { store.history(initial.id) } } }) { Icon(painterResource(R.drawable.ic_history), null, tint = Color.White); Text("  History", color = Color.White) }
                TextButton(onClick = {
                    tools = false
                    if (archived) { archived = false; meta { it.remove("archivedAt") } } else leave("Note archived") { it.put("archivedAt", System.currentTimeMillis()) }
                }) { Icon(painterResource(if (archived) R.drawable.ic_unarchive else R.drawable.ic_archive), null, tint = Color.White); Text(if (archived) "  Unarchive" else "  Archive", color = Color.White) }
                TextButton(onClick = { tools = false; leave("Note moved to Trash") { it.put("trashedAt", System.currentTimeMillis()) } }) {
                    Icon(painterResource(R.drawable.ic_delete), null, tint = Color.White); Text("  Trash", color = Color.White)
                }
            }
        }
    }

    history?.let { versions ->
        AlertDialog(
            onDismissRequest = { history = null },
            title = { Text("History") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A version is kept each time you leave the note with changes. Restoring keeps the current one too.", style = MaterialTheme.typography.bodySmall)
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
    IconButton(onClick = click, enabled = enabled, modifier = Modifier.size(44.dp).then(if (on) Modifier.background(tint.copy(alpha = 0.15f), CircleShape) else Modifier)) {
        Icon(painterResource(icon), contentDescription = description, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(22.dp))
    }

/** Open items in their order, then the done ones under a line; Next on the keyboard makes the next item. */
@Composable private fun ChecklistEditor(items: List<CheckItem>, ink: Color, readOnly: Boolean, change: (List<CheckItem>, Boolean) -> Unit) {
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
