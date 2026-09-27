package com.kalotrapezis.drive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/*
 * Office › Text editor (asked 2026-09-27): a notepad. It opens any file as text and saves it back, with tabs for the
 * files open at once. No history, tags or colours — that is Notes. Android only; the computer has its own editors.
 */

/**
 * A file's bytes as text, and back. The editor works on '\n' only; a file written with Windows line endings (CRLF)
 * is saved with them again, and a byte-order mark stays if there was one — so saving changes only what was typed.
 */
internal object TextFileRules {
    data class Loaded(val text: String, val crlf: Boolean, val bom: Boolean)

    fun read(bytes: ByteArray): Loaded {
        val bom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        val raw = String(bytes, if (bom) 3 else 0, bytes.size - if (bom) 3 else 0, Charsets.UTF_8)
        return Loaded(raw.replace("\r\n", "\n"), crlf = "\r\n" in raw, bom = bom)
    }

    fun write(text: String, crlf: Boolean, bom: Boolean): ByteArray {
        val body = (if (crlf) text.replace("\n", "\r\n") else text).toByteArray(Charsets.UTF_8)
        return if (bom) byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body else body
    }

    /** A name for a new file: what was typed, with .txt when it has no extension of its own. */
    fun fileName(typed: String): String? {
        val name = typed.trim()
        if (name.isEmpty() || '/' in name || '\\' in name || name == "." || name == "..") return null
        return if ('.' in name.drop(1)) name else "$name.txt"
    }
}

/**
 * The symbols row (asked 2026-09-27: "all the </> weird stuff easily accessible"): what a phone keyboard hides.
 * A bracket or quote around a selection wraps it; </> closes the last HTML tag still open before the cursor.
 */
internal object CodeKeys {
    val keys = listOf("Tab", "</>", "<", ">", "/", "=", "\"", "'", "{", "}", "[", "]", "(", ")", ";", ":", "&", "#", "$", "_", "-", "|", "\\", "`", "!", "?", "@", "%", "*", "+", "~")
    private val pairs = mapOf("(" to ")", "[" to "]", "{" to "}", "\"" to "\"", "'" to "'", "`" to "`", "<" to ">")
    private val void = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr")

    /** The tag to close at [at]: the last one opened before it and not closed since. Null when all are closed. */
    fun openTag(text: String, at: Int): String? {
        val stack = ArrayDeque<String>()
        Regex("""<(/?)([A-Za-z][\w:-]*)[^<>]*?(/?)>""").findAll(text.substring(0, at)).forEach { m ->
            val name = m.groupValues[2].lowercase()
            when {
                m.groupValues[1] == "/" -> stack.indexOfLast { it == name }.takeIf { it >= 0 }?.let { while (stack.size > it) stack.removeLast() }
                m.groupValues[3] != "/" && name !in void -> stack.addLast(name)
            }
        }
        return stack.lastOrNull()
    }

    /** The text and selection after pressing [key]. */
    fun press(key: String, e: NoteFormat.Edit): NoteFormat.Edit {
        val insert = when (key) {
            "Tab" -> "    "
            "</>" -> openTag(e.text, e.start)?.let { "</$it>" } ?: return e
            else -> key
        }
        val close = pairs[key]
        if (close != null && e.end > e.start) // wrap the selection, and keep it selected
            return NoteFormat.Edit(e.text.substring(0, e.start) + key + e.text.substring(e.start, e.end) + close + e.text.substring(e.end), e.start + 1, e.end + 1)
        val at = e.start + insert.length
        return NoteFormat.Edit(e.text.substring(0, e.start) + insert + e.text.substring(e.end), at, at)
    }
}

/** The editor's settings, and its tabs: which files are open, and the unsaved text of each, kept until saved. */
internal class TextEditorStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("office_editor", Context.MODE_PRIVATE)
    private val drafts = File(context.applicationContext.filesDir, "editor-drafts")

    data class Tab(val id: String, val uri: String?, val name: String, val crlf: Boolean = false, val bom: Boolean = false)

    var textSize: Int
        get() = prefs.getInt("text_size", 15)
        set(sp) { prefs.edit().putInt("text_size", sp.coerceIn(11, 28)).apply() }

    var wordWrap: Boolean
        get() = prefs.getBoolean("word_wrap", true)
        set(on) { prefs.edit().putBoolean("word_wrap", on).apply() }

    /** Where Save puts a new file, under the phone's storage (default Tetra/Documents). */
    var saveFolder: String
        get() = prefs.getString("save_folder", "Tetra/Documents") ?: "Tetra/Documents"
        set(folder) { prefs.edit().putString("save_folder", folder.trim().trim('/').ifEmpty { "Tetra/Documents" }).apply() }

    fun saveDirectory(): File = File(Environment.getExternalStorageDirectory(), saveFolder)

    fun tabs(): List<Tab> = runCatching {
        val a = JSONArray(prefs.getString("tabs", "[]"))
        List(a.length()) { i -> a.getJSONObject(i).let { Tab(it.getString("id"), it.optString("uri").ifEmpty { null }, it.getString("name"), it.optBoolean("crlf"), it.optBoolean("bom")) } }
    }.getOrDefault(emptyList())

    fun setTabs(tabs: List<Tab>, current: String?) {
        val a = JSONArray()
        tabs.forEach { a.put(JSONObject().put("id", it.id).put("uri", it.uri ?: "").put("name", it.name).put("crlf", it.crlf).put("bom", it.bom)) }
        prefs.edit().putString("tabs", a.toString()).putString("current", current).apply()
        drafts.listFiles()?.forEach { f -> if (tabs.none { it.id == f.name }) f.delete() } // a closed tab's draft goes with it
    }

    fun current(): String? = prefs.getString("current", null)

    fun draft(id: String): String? = File(drafts, id).takeIf { it.isFile }?.readText()
    fun setDraft(id: String, text: String?) {
        val f = File(drafts, id)
        if (text == null) f.delete() else { drafts.mkdirs(); f.writeText(text) }
    }
}

/** Opens a text file handed over by another app ("Open with Tetra") as a tab of the editor. */
class TextEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        setContent { TetraTheme { TextEditorScreen(back = { finish() }, open = uri) } }
    }
}

private fun displayName(context: Context, uri: Uri): String =
    runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
        ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Text"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TextEditorScreen(back: () -> Unit, open: Uri? = null, start: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { TextEditorStore(context) }
    val tabs = remember { store.tabs().toMutableStateList() }
    var current by remember { mutableStateOf(store.current()) }
    val text = remember { mutableStateMapOf<String, TextFieldValue>() } // the text of each open tab
    val saved = remember { mutableStateMapOf<String, String>() } // as it is in the file: a tab differs → unsaved
    val undo = remember { HashMap<String, NoteUndo<String>>() }
    var undoTick by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(store.wordWrap) }
    var ribbon by remember { mutableStateOf(Ribbon.File) }
    var size by remember { mutableIntStateOf(store.textSize) }
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    var naming by remember { mutableStateOf<String?>(null) } // a tab being saved under a new name
    var closing by remember { mutableStateOf<String?>(null) } // a tab with unsaved changes being closed
    var message by remember { mutableStateOf<String?>(null) }
    val w = islandContentColor()

    fun persist() = store.setTabs(tabs, current)
    fun untitled(): String = generateSequence(1) { it + 1 }.map { if (it == 1) "Untitled" else "Untitled $it" }.first { n -> tabs.none { it.name == n } }
    fun newTab() {
        val t = TextEditorStore.Tab(UUID.randomUUID().toString(), null, untitled())
        tabs.add(t); text[t.id] = TextFieldValue(""); saved[t.id] = ""; current = t.id; persist()
    }
    suspend fun load(t: TextEditorStore.Tab): TextFileRules.Loaded? = withContext(Dispatchers.IO) {
        runCatching { t.uri?.let { u -> context.contentResolver.openInputStream(Uri.parse(u))?.use { TextFileRules.read(it.readBytes()) } } }.getOrNull()
    }
    suspend fun openUri(uri: Uri) {
        tabs.firstOrNull { it.uri == uri.toString() }?.let { current = it.id; persist(); return }
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        val name = withContext(Dispatchers.IO) { displayName(context, uri) }
        val file = load(TextEditorStore.Tab("", uri.toString(), name)) ?: run { message = "Could not open $name as text."; return }
        val t = TextEditorStore.Tab(UUID.randomUUID().toString(), uri.toString(), name, file.crlf, file.bom)
        tabs.add(t); text[t.id] = TextFieldValue(file.text); saved[t.id] = file.text; current = t.id; persist()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch { openUri(uri) } }
    // The open tabs come back as they were: the file's text, or the unsaved draft over it.
    LaunchedEffect(Unit) {
        for (t in tabs.toList()) {
            val file = load(t)?.text ?: ""
            saved[t.id] = file
            text[t.id] = TextFieldValue(withContext(Dispatchers.IO) { store.draft(t.id) } ?: file)
        }
        loaded = true
        if (open != null) openUri(open)
        if (start == "new") newTab()
        if (tabs.isEmpty()) newTab()
        if (tabs.none { it.id == current }) current = tabs.first().id
        if (start == "open") picker.launch(arrayOf("*/*"))
    }
    val tab = tabs.firstOrNull { it.id == current }
    val value = tab?.let { text[it.id] } ?: TextFieldValue("")
    fun dirty(id: String) = text[id]?.text != saved[id]
    // Unsaved text is kept as you type (a short pause), so closing the app never loses it.
    LaunchedEffect(tab?.id, value.text) {
        val t = tab ?: return@LaunchedEffect
        if (!loaded) return@LaunchedEffect
        delay(500)
        withContext(Dispatchers.IO) { store.setDraft(t.id, if (dirty(t.id)) value.text else null) }
    }

    fun edit(next: TextFieldValue) {
        val t = tab ?: return
        val before = text[t.id]?.text ?: ""
        if (next.text != before) undo.getOrPut(t.id) { NoteUndo(before) }.push(next.text, wordDone = NoteUndo.endsWord(before, next.text, next.selection.start))
        text[t.id] = next; undoTick++
    }
    fun press(key: String) {
        val t = tab ?: return
        val before = text[t.id] ?: return
        val r = CodeKeys.press(key, NoteFormat.Edit(before.text, before.selection.min, before.selection.max))
        if (r.text != before.text) undo.getOrPut(t.id) { NoteUndo(before.text) }.step(r.text)
        text[t.id] = TextFieldValue(r.text, androidx.compose.ui.text.TextRange(r.start, r.end)); undoTick++
    }
    fun restore(s: String?) { val t = tab ?: return; if (s != null) { text[t.id] = TextFieldValue(s, androidx.compose.ui.text.TextRange(s.length)); undoTick++ } }

    fun write(t: TextEditorStore.Tab, uri: Uri): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(TextFileRules.write(text[t.id]?.text ?: "", t.crlf, t.bom)) }
    }.isSuccess
    fun save(t: TextEditorStore.Tab, then: () -> Unit = {}) {
        if (t.uri == null) { naming = t.id; return }
        scope.launch {
            val ok = withContext(Dispatchers.IO) { write(t, Uri.parse(t.uri)) }
            if (ok) { saved[t.id] = text[t.id]?.text ?: ""; withContext(Dispatchers.IO) { store.setDraft(t.id, null) }; message = "Saved ${t.name}"; then() }
            else message = "Could not save ${t.name}."
        }
    }
    // Save as: a new file in the save folder. Never over another file.
    fun saveAs(id: String, typed: String) {
        val t = tabs.firstOrNull { it.id == id } ?: return
        val name = TextFileRules.fileName(typed) ?: run { message = "That is not a file name."; return }
        scope.launch {
            val dir = store.saveDirectory()
            val file = File(dir, name)
            val result = withContext(Dispatchers.IO) {
                when {
                    !dir.isDirectory && !dir.mkdirs() -> "Could not make ${store.saveFolder}."
                    file.exists() -> "$name is already in ${store.saveFolder}. Choose another name."
                    !write(t, Uri.fromFile(file)) -> "Could not save $name."
                    else -> null
                }
            }
            if (result != null) { message = result; return@launch }
            val renamed = t.copy(uri = Uri.fromFile(file).toString(), name = name)
            tabs[tabs.indexOfFirst { it.id == id }] = renamed
            saved[id] = text[id]?.text ?: ""; withContext(Dispatchers.IO) { store.setDraft(id, null) }
            naming = null; persist(); message = "Saved in ${store.saveFolder}"
        }
    }
    fun close(id: String, force: Boolean = false) {
        if (!force && dirty(id)) { closing = id; return }
        val i = tabs.indexOfFirst { it.id == id }
        if (i < 0) return
        tabs.removeAt(i); text.remove(id); saved.remove(id); undo.remove(id)
        if (tabs.isEmpty()) newTab() else if (current == id) current = tabs[(i - 1).coerceAtLeast(0)].id
        persist()
    }

    fun clipboard(cut: Boolean) {
        val t = tab ?: return
        val v = text[t.id] ?: return
        clip.setText(androidx.compose.ui.text.AnnotatedString(v.text.substring(v.selection.min, v.selection.max)))
        if (cut) press("")
    }
    fun paste() { clip.getText()?.text?.let { press(it) } }
    BackHandler { back() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            // Tabs: the files open at once, like a browser (asked 2026-09-27). A dot is unsaved; × closes.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { t ->
                    val on = t.id == current
                    Row(Modifier.clip(CircleShape).background(if (on) Color.White else Color(0xFF2A2E30)).clickable { current = t.id; persist() }
                        .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val ink = if (on) Color.Black else Color.White
                        Text((if (dirty(t.id)) "• " else "") + t.name, color = ink, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                        Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClickLabel = "Close ${t.name}") { close(t.id) }, contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = "Close ${t.name}", tint = ink, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2A2E30)).clickable(onClickLabel = "New tab") { newTab() }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = "New tab", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            // Word wrap off: the text is laid out as wide as its longest line and scrolls sideways, like code.
            val ink = MaterialTheme.colorScheme.onSurface
            val style = TextStyle(color = ink, fontSize = size.sp, lineHeight = (size * 1.45f).sp, fontFamily = FontFamily.Monospace)
            val screen = LocalConfiguration.current.screenWidthDp.dp
            if (tab != null) Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState()))) {
                BasicTextField(value, ::edit, textStyle = style, cursorBrush = SolidColor(ink),
                    decorationBox = { inner -> Box { if (value.text.isEmpty()) Text("Type here…", style = style.copy(color = ink.copy(alpha = 0.4f))); inner() } },
                    // As tall as the page, so a tap anywhere below the text starts typing, like a notepad.
                    modifier = (if (wrap) Modifier.fillMaxWidth() else Modifier.widthIn(min = screen)).heightIn(min = LocalConfiguration.current.screenHeightDp.dp - 140.dp)
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp))
            }
        }
        // A ribbon, like Word's, at the bottom (asked 2026-09-27): one line of tabs — File, Edit, Insert, View — and one
        // of that tab's tools, full width, scrolling sideways when they do not fit. On the keyboard when it is up.
        val imeOpen = WindowInsets.isImeVisible
        val u = tab?.let { undo[it.id] }
        // Tetra's own look: a floating island with the keyboard down; a flat strip on the keyboard when it is up.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(
            if (imeOpen) Modifier.background(islandColor())
            else Modifier.navigationBarsPadding().padding(12.dp).clip(MaterialTheme.shapes.extraLarge).background(islandColor()).padding(vertical = 4.dp))) {
            undoTick
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Tool(R.drawable.ic_chevron_left, "Back", w) { back() }
                Ribbon.entries.forEach { r ->
                    Text(r.label, color = if (ribbon == r) driveNavigationSelectedContentColor() else w, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 2.dp).clip(CircleShape).background(if (ribbon == r) driveNavigationSelectedColor() else Color.Transparent)
                            .clickable { ribbon = r }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val t = tab
                when (ribbon) {
                    Ribbon.File -> {
                        RibbonTool("New", w, R.drawable.ic_note_add) { newTab() }
                        RibbonTool("Open", w, R.drawable.ic_folder) { picker.launch(arrayOf("*/*")) }
                        RibbonTool("Save", w, R.drawable.ic_save, enabled = t != null && (t.uri == null || dirty(t.id))) { t?.let { save(it) } }
                        RibbonTool("Save as…", w) { t?.let { naming = it.id } }
                        RibbonTool("Close tab", w, R.drawable.ic_close) { t?.let { close(it.id) } }
                    }
                    Ribbon.Edit -> {
                        RibbonTool("Undo", w, R.drawable.ic_undo, enabled = u?.canUndo == true) { restore(u?.undo()) }
                        RibbonTool("Redo", w, R.drawable.ic_redo, enabled = u?.canRedo == true) { restore(u?.redo()) }
                        RibbonTool("Cut", w, enabled = !value.selection.collapsed) { clipboard(cut = true) }
                        RibbonTool("Copy", w, R.drawable.ic_copy, enabled = !value.selection.collapsed) { clipboard(cut = false) }
                        RibbonTool("Paste", w) { paste() }
                        RibbonTool("Select all", w) { t?.let { text[it.id] = value.copy(selection = androidx.compose.ui.text.TextRange(0, value.text.length)) } }
                    }
                    Ribbon.Insert -> CodeKeys.keys.forEach { k -> KeyButton(k, w, Modifier.widthIn(min = 38.dp)) { press(k) } }
                    Ribbon.View -> {
                        RibbonTool(if (wrap) "Word wrap: on" else "Word wrap: off", w, on = wrap) { wrap = !wrap; store.wordWrap = wrap }
                        RibbonTool("A−", w, enabled = size > 11) { size -= 1; store.textSize = size }
                        Text("$size", color = w, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 6.dp))
                        RibbonTool("A+", w, enabled = size < 28) { size += 1; store.textSize = size }
                        t?.let { Text(if (it.uri == null) "Not saved yet" else if (it.crlf) "Windows line endings, kept" else "LF line endings",
                            color = w.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp)) }
                    }
                }
            }
        }
        message?.let { m ->
            LaunchedEffect(m) { delay(2500); message = null }
            Surface(color = Color.Black, contentColor = Color.White, shape = CircleShape, modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)) {
                Text(m, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }

    naming?.let { id ->
        var typed by remember(id) { mutableStateOf(tabs.firstOrNull { it.id == id }?.name?.takeUnless { it.startsWith("Untitled") } ?: "") }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text("Save as") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text("File name") })
                    Text("In ${store.saveFolder} (Settings › Office). No extension adds .txt.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { saveAs(id, typed) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancel") } },
        )
    }
    closing?.let { id ->
        val t = tabs.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { closing = null },
            title = { Text("Save ${t?.name ?: "this file"}?") },
            text = { Text("It has changes that are not saved.") },
            confirmButton = { TextButton(onClick = { closing = null; t?.let { save(it) { close(id, force = true) } } }) { Text("Save") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { closing = null }) { Text("Cancel") }
                    TextButton(onClick = { closing = null; close(id, force = true) }) { Text("Don't save", color = Color(0xFFE35A4F)) }
                }
            },
        )
    }
}

/** A key of the symbols row: its character, the size of a Tool. */
@Composable private fun KeyButton(key: String, tint: Color, modifier: Modifier = Modifier, click: () -> Unit) = Box(
    modifier.height(40.dp).clip(CircleShape).clickable(onClickLabel = key, onClick = click),
    contentAlignment = Alignment.Center,
) { Text(key, color = tint, fontFamily = FontFamily.Monospace, fontSize = if (key.length > 1) 11.sp else 17.sp, maxLines = 1) }

internal enum class Ribbon(val label: String) { File("File"), Edit("Edit"), Insert("Insert"), View("View") }

/** A tool of the ribbon: its icon when it has one, and its name, like Word's "Track Changes". */
@Composable private fun RibbonTool(label: String, tint: Color, icon: Int? = null, enabled: Boolean = true, on: Boolean = false, click: () -> Unit) = Row(
    Modifier.height(40.dp).clip(CircleShape).then(if (on) Modifier.background(tint.copy(alpha = 0.15f)) else Modifier)
        .clickable(enabled = enabled, onClick = click).padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    val c = if (enabled) tint else tint.copy(alpha = 0.35f)
    icon?.let { Icon(painterResource(it), contentDescription = null, tint = c, modifier = Modifier.size(18.dp)) }
    Text(label, color = c, style = MaterialTheme.typography.labelLarge, maxLines = 1)
}
