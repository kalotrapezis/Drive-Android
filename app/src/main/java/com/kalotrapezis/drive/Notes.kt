package com.kalotrapezis.drive

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Notes (asked 2026-09-26): a hidden folder of Files, `/sdcard/Tetra/.notes/`, one `<id>.json` per note in the format
 * the Notes apps already write, labels only (no folders). The computer keeps the same folder (desktop/notes.js) and
 * the two meet over `/notes` at each sync: the newer `updatedAt` of each note wins, and a deletion beats any edit.
 *
 * - `deletions.json`: notes deleted for good, so no device brings one back.
 * - `history/<id>/<Title>-YYYY-MM-DD-HH-MM-N.json`: the note each time its editor closed with changes. Undo is the
 *   editor's own, in memory; this is what is left after it.
 *
 * Every write patches the note's own JSON, so fields a newer app wrote are kept.
 */
internal data class CheckItem(val id: String, val text: String, val checked: Boolean, val order: Int)

internal data class Note(
    val id: String, val title: String, val content: String, val checklist: Boolean, val items: List<CheckItem>,
    val labels: List<String>, val color: String?, val pinned: Boolean, val archivedAt: Long?, val trashedAt: Long?,
    val createdAt: Long, val updatedAt: Long,
) {
    val empty get() = title.isBlank() && content.isBlank() && items.none { it.text.isNotBlank() }
}

internal data class NoteVersion(val name: String, val at: Long, val title: String, val content: String, val items: List<CheckItem>, val n: Int = 0)

internal class NotesStore(val root: File, private val deviceId: String = "tetra-android") {
    private fun isId(id: String) = id.isNotEmpty() && id.length <= 64 && id.all { it.isLetterOrDigit() || it == '-' }
    private fun file(id: String): File { require(isId(id)) { "Not a note." }; return File(root, "$id.json") }
    private fun versions(id: String): File { require(isId(id)) { "Not a note." }; return File(File(root, "history"), id) }

    fun json(id: String): JSONObject? = runCatching { JSONObject(file(id).readText()) }.getOrNull()
    fun get(id: String): Note? = json(id)?.let(::parse)

    fun list(): List<Note> = root.listFiles { f -> f.isFile && f.name.endsWith(".json") && f.name != "deletions.json" }.orEmpty()
        .mapNotNull { f -> runCatching { parse(JSONObject(f.readText())) }.getOrNull() }

    /** Whole, through a temporary file, so a crash never leaves half a note. */
    fun write(o: JSONObject) {
        root.mkdirs()
        val target = file(o.getString("id"))
        val tmp = File(root, "${target.name}.tmp")
        tmp.writeText(o.toString(2))
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    fun create(checklist: Boolean, labels: List<String> = emptyList()): Note {
        val now = System.currentTimeMillis()
        val o = JSONObject().put("id", UUID.randomUUID().toString()).put("title", "").put("content", "")
            .put("createdAt", now).put("updatedAt", now).put("deviceId", deviceId).put("syncStatus", "LOCAL_ONLY")
            .put("noteType", if (checklist) "CHECKLIST" else "TEXT").put("isPinned", false).put("labels", JSONArray(labels))
        if (checklist) o.put("checklistItems", JSONArray())
        write(o)
        return parse(o)
    }

    /** A person's edit: the fields given change, everything else stays, and it is now the newest. */
    fun update(id: String, edit: (JSONObject) -> Unit): Note {
        val o = json(id) ?: error("That note is gone.")
        edit(o)
        o.put("updatedAt", System.currentTimeMillis()).put("deviceId", deviceId)
        write(o)
        return parse(o)
    }

    fun setText(id: String, title: String, content: String, items: List<CheckItem>?) = update(id) { o ->
        o.put("title", title).put("content", content)
        if (items != null) o.put("checklistItems", itemsJson(items, o.optJSONArray("checklistItems")))
    }

    /**
     * A copy of the note as it is now, in its history: taken before the first change of an opened note and when it is
     * left (asked 2026-09-26: "when I change stuff I can still see my old note before the edits"). The same text as
     * the newest copy is not kept twice, and only the newest [KEEP] copies stay.
     */
    fun snapshot(id: String, now: Long = System.currentTimeMillis()): String? {
        val o = json(id) ?: return null
        val note = parse(o)
        history(id).firstOrNull()?.let { if (it.title == note.title && it.content == note.content && it.items == note.items) return null }
        val dir = versions(id).apply { mkdirs() }
        val n = (dir.listFiles().orEmpty().mapNotNull { Regex("-(\\d+)\\.json$").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val stamp = "%04d-%02d-%02d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        val name = "${safeTitle(o.optString("title"))}-$stamp-$n.json"
        File(dir, name).writeText(JSONObject(o.toString()).put("savedAt", now).put("version", n).toString(2))
        history(id).drop(KEEP).forEach { File(dir, it.name).delete() }
        return name
    }

    fun history(id: String): List<NoteVersion> = versions(id).listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
        runCatching { JSONObject(f.readText()).let { o -> parse(o).let { NoteVersion(f.name, it.updatedAt, it.title, it.content, it.items, o.optInt("version")) } } }.getOrNull()
    }.sortedWith(compareByDescending<NoteVersion> { it.n }.thenByDescending { it.at })

    /** A version comes back as a new edit; what it replaces is kept in the history first. */
    fun restoreVersion(id: String, name: String): Note {
        require(File(name).name == name) { "Not a version." }
        val v = JSONObject(File(versions(id), name).readText())
        snapshot(id)
        return update(id) { o ->
            o.put("title", v.optString("title")).put("content", v.optString("content"))
            v.optJSONArray("checklistItems")?.let { o.put("checklistItems", it) }
        }
    }

    fun deletions(): JSONArray = runCatching { JSONArray(File(root, "deletions.json").readText()) }.getOrDefault(JSONArray())
    private fun deletedIds(list: JSONArray = deletions()) = (0 until list.length()).mapTo(HashSet()) { list.getJSONObject(it).optString("id") }

    /** For good: the file goes and the id is remembered. Its history stays, so it can still be read back. */
    fun remove(id: String, at: Long = System.currentTimeMillis()) {
        file(id).delete()
        val old = deletions()
        val list = JSONArray()
        for (i in 0 until old.length()) if (old.getJSONObject(i).optString("id") != id) list.put(old.getJSONObject(i))
        list.put(JSONObject().put("id", id).put("deletedAt", at).put("deviceId", deviceId))
        root.mkdirs()
        File(root, "deletions.json").writeText(list.toString())
    }

    /** Trash keeps a note 30 days, then it is deleted for good. */
    fun sweep(now: Long = System.currentTimeMillis()): Int = list().filter { it.trashedAt != null && it.trashedAt < now - 30 * 86_400_000L }
        .onEach { remove(it.id, now) }.size

    /** What this phone sends at a sync: every note and every deletion. */
    fun payload(): JSONObject = JSONObject().put("notes", JSONArray(list().mapNotNull { json(it.id) })).put("deletions", deletions())

    /** The computer's answer: notes newer there or missing here, and every deletion. Returns how many changed here. */
    fun apply(answer: JSONObject): Int {
        var changed = 0
        val theirs = answer.optJSONArray("deletions") ?: JSONArray()
        val mine = deletions()
        val known = deletedIds(mine)
        for (i in 0 until theirs.length()) {
            val d = theirs.getJSONObject(i)
            val id = d.optString("id")
            if (!isId(id) || id in known) continue
            mine.put(d); known += id
            if (file(id).delete()) changed++
        }
        root.mkdirs()
        File(root, "deletions.json").writeText(mine.toString())
        val notes = answer.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until notes.length()) {
            val n = notes.getJSONObject(i)
            val id = n.optString("id")
            if (!isId(id) || id in known) continue
            val here = json(id)
            if (here == null || n.optLong("updatedAt") > here.optLong("updatedAt")) { write(n); changed++ }
        }
        return changed
    }

    companion object {
        const val KEEP = 3 // copies of a note in its history
        fun safeTitle(t: String) = t.replace(Regex("[/\\\\\u0000:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim().take(80).ifEmpty { "Untitled" }

        fun parse(o: JSONObject): Note {
            val items = o.optJSONArray("checklistItems")?.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i) }.map {
                CheckItem(it.optString("id"), it.optString("text"), it.optBoolean("isChecked"), it.optInt("order"))
            } }.orEmpty()
            val labels = o.optJSONArray("labels")?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty()
            return Note(
                id = o.getString("id"), title = o.optString("title"), content = o.optString("content"),
                checklist = o.optString("noteType") == "CHECKLIST", items = items, labels = labels,
                color = o.optString("color").takeIf { it.startsWith("#") }, pinned = o.optBoolean("isPinned"),
                archivedAt = o.optLong("archivedAt").takeIf { it > 0 }, trashedAt = o.optLong("trashedAt").takeIf { it > 0 },
                createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
            )
        }

        /** Items as the Notes apps write them; an item's other fields (indentation, dates) are kept by its id. */
        fun itemsJson(items: List<CheckItem>, old: JSONArray?): JSONArray {
            val before = old?.let { a -> (0 until a.length()).map(a::getJSONObject).associateBy { it.optString("id") } }.orEmpty()
            return JSONArray(items.map { item ->
                (before[item.id] ?: JSONObject().put("createdAt", System.currentTimeMillis()).put("indentationLevel", 0).put("originalOrder", item.order))
                    .put("id", item.id).put("text", item.text).put("isChecked", item.checked).put("order", item.order)
            })
        }
    }
}

/** Unchecked in their order, then the checked ones below — as every Notes app shows a checklist. */
internal fun List<CheckItem>.sortedForList() = sortedWith(compareBy<CheckItem> { it.checked }.thenBy { it.order })

/**
 * Undo in memory, per open note, by the word (asked 2026-09-26): typing joins the step it is in until a word ends — a
 * space or a mark typed after it — or it pauses for [quietMs]. A toolbar action is a step of its own.
 */
internal class NoteUndo<T>(private var current: T, private val quietMs: Long = 2000) {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()
    private var last = 0L
    private var split = true // the next change starts a step of its own
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    fun push(next: T, now: Long = System.currentTimeMillis(), wordDone: Boolean = false) {
        if (split || now - last > quietMs) past.addLast(current)
        if (past.size > 500) past.removeFirst()
        current = next; future.clear(); last = now; split = wordDone
    }
    fun step(next: T) { split = true; push(next); split = true }
    fun undo(): T? = past.removeLastOrNull()?.also { future.addLast(current); current = it; split = true }
    fun redo(): T? = future.removeLastOrNull()?.also { past.addLast(current); current = it; split = true }

    companion object {
        /** One character typed at [at] that is not part of a word: the word before it is done. */
        fun endsWord(old: String, new: String, at: Int) = new.length == old.length + 1 && at in 1..new.length && !new[at - 1].isLetterOrDigit()
    }
}

/** Formatting a selection of the text: the same rules as the computer's editor (desktop/src/notesEdit.ts). */
internal object NoteFormat {
    data class Edit(val text: String, val start: Int, val end: Int)

    fun wrap(e: Edit, mark: String): Edit {
        val before = e.text.substring(0, e.start); val inside = e.text.substring(e.start, e.end); val after = e.text.substring(e.end)
        return if (before.endsWith(mark) && after.startsWith(mark))
            Edit(before.dropLast(mark.length) + inside + after.drop(mark.length), e.start - mark.length, e.end - mark.length)
        else Edit(before + mark + inside + mark + after, e.start + mark.length, e.end + mark.length)
    }

    private fun lines(e: Edit, change: (List<String>) -> List<String>): Edit {
        val from = e.text.lastIndexOf('\n', e.start - 1) + 1
        val nl = e.text.indexOf('\n', e.end)
        val to = if (nl == -1) e.text.length else nl
        val body = change(e.text.substring(from, to).split('\n')).joinToString("\n")
        return Edit(e.text.substring(0, from) + body + e.text.substring(to), from, from + body.length)
    }

    fun prefix(e: Edit, mark: String): Edit = lines(e) { ls ->
        val all = ls.all { it.startsWith(mark) }
        ls.mapIndexed { i, l -> if (all) l.drop(mark.length) else (if (mark == "1. ") "${i + 1}. " else mark) + l.replace(Regex("^(#{1,3} |- \\[[ x]\\] |- |\\d+\\. |> )"), "") }
    }

    fun indent(e: Edit, out: Boolean): Edit = lines(e) { ls -> ls.map { if (out) it.replaceFirst(Regex("^ {1,2}"), "") else "  $it" } }

    fun preview(n: Note, max: Int = 8): List<String> =
        if (n.checklist) n.items.sortedForList().take(max).map { "${if (it.checked) "☑" else "☐"} ${it.text}" }
        else n.content.lines().filter { it.isNotBlank() }.take(max).map {
            it.replace(Regex("^(#{1,3} |> )"), "").replace(Regex("^(\\s*)- \\[ \\] "), "$1☐ ").replace(Regex("^(\\s*)- \\[[xX]\\] "), "$1☑ ").replace(Regex("\\*\\*|~~|`"), "")
        }
}

/**
 * Notes cross on their own, a moment after writing pauses — as the old Notes app did, every two or three seconds
 * (asked 2026-09-26) — not only with the whole sync. Only the notes go: a few kilobytes, a second's work.
 */
internal object NotesSync {
    /** Counts up when notes from the computer land here, so an open list reloads. */
    val changed = MutableStateFlow(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    /** At once, and waits: pull to refresh, and opening Notes. Returns what changed here, or null when unreachable. */
    suspend fun now(context: Context): Int? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val app = context.applicationContext
        runCatching { SyncClient(app, SyncStore(app)).notesOnly() }.getOrNull()
    }

    fun soon(context: Context, delayMs: Long = 2_500) {
        val app = context.applicationContext
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            runCatching { SyncClient(app, SyncStore(app)).notesOnly() } // the computer asleep: the next pause, or the next sync
        }
    }
}
