package com.kalotrapezis.drive

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/*
 * Tetra Assistant (Plan.md § Assistant): walkie-talkie, not live. What was said is recorded on the phone, written out
 * by Mistral's Voxtral, and sent with the conversation so far to mistral-small, which may call Tetra's tools; the
 * tools run here, on the phone's own files, and hand back only what the model needs (names and titles, never a
 * whole calendar, contact list or note). A call waits for the user's own yes (a tap, or a spoken "ναι" heard by the
 * phone, not the model). Web search and pictures are Mistral's own tools, through its Conversations API: the chat
 * API does not run them (docs.mistral.ai, Oct 2026).
 */

/** A call the model asked for, waiting for the user's yes. */
internal data class PendingCall(val name: String, val number: String)

/** Something the screen offers to tap: a contact to call, a file to open, or a place in Tetra to go ([go]). */
internal data class AssistantChoice(val label: String, val call: PendingCall? = null, val file: File? = null, val go: String? = null)

/** What a tool hands back: words for the model (as few as it needs), and what the screen shows besides. */
internal data class ToolResult(val forModel: String, val choices: List<AssistantChoice> = emptyList(), val call: PendingCall? = null,
    val image: File? = null, val open: File? = null, val go: String? = null, val photos: List<File> = emptyList())

/** The model's answer to one turn, with everything the tools left for the screen. */
internal data class AssistantReply(val text: String, val choices: List<AssistantChoice> = emptyList(), val call: PendingCall? = null,
    val images: List<File> = emptyList(), val open: File? = null, val go: String? = null)

/**
 * What a conversation has cost so far, in US dollars by Mistral's list prices (the user: "the bill came"; shown in the
 * chat's top bar and on the live screen).
 */
internal class Bill(private val counted: (Double) -> Unit = {}) {
    @Volatile var usd = 0.0; private set
    /** [counted] gets each cost too: the meter in Settings sums every chat's from here, so nothing is counted twice. */
    @Synchronized fun add(x: Double) { usd += x; counted(x) }
    @Synchronized fun reset() { usd = 0.0 }
    fun tokens(model: String, usage: JSONObject?) {
        val (inPrice, outPrice) = TOKENS[model] ?: return
        usage ?: return
        add((usage.optLong("prompt_tokens") * inPrice + usage.optLong("completion_tokens") * outPrice) / 1e6)
    }
    fun minutes(model: String, seconds: Double) = add((PER_MINUTE[model] ?: 0.0) * seconds / 60)
    /** Mistral's voice is paid by the character read. */
    fun chars(text: String) = add(text.length * PER_CHAR)

    companion object {
        // shortcut: Mistral's list prices in $ as published Oct 2026 (tools' from price tables, not Mistral's page); update when they change
        private val TOKENS = mapOf(Mistral.CHAT to (0.1 to 0.3), Mistral.BUILT_IN to (0.4 to 2.0))
        private val PER_MINUTE = mapOf(Mistral.TRANSCRIBE to 0.004, Mistral.REALTIME to 0.006)
        fun perMinute(model: String) = PER_MINUTE[model] ?: 0.0
        val PER_CALL = mapOf("web_search" to 0.03, "image_generation" to 0.10)
        const val PER_CHAR = 0.016 / 1000 // voxtral-mini-tts-3: $0.016 a thousand characters (Mistral's Voxtral TTS page)

        /** "$0.0123": a cent's fractions while it is small. */
        fun text(usd: Double) = if (usd < 1) "$%.4f".format(java.util.Locale.ROOT, usd) else "$%.2f".format(java.util.Locale.ROOT, usd)
    }
}

/**
 * Every AI Assist cost, like a car's odometer and trip meter: [total] never goes back, [reset] starts a new trip from
 * [since] and keeps the one before in [past], newest first.
 */
internal class Meter(var total: Double = 0.0, var trip: Double = 0.0, var since: Long = 0L, val past: MutableList<Trip> = mutableListOf()) {
    data class Trip(val from: Long, val to: Long, val usd: Double)
    fun add(x: Double) { total += x; trip += x }
    fun reset(at: Long) { past.add(0, Trip(since, at, trip)); trip = 0.0; since = at }
    fun json(): String = JSONObject().put("total", total).put("trip", trip).put("since", since)
        .put("past", JSONArray(past.map { JSONObject().put("from", it.from).put("to", it.to).put("usd", it.usd) })).toString()

    companion object {
        /** The saved meter, or a new one counting from [now]. */
        fun of(json: String?, now: Long): Meter = runCatching {
            val o = JSONObject(json!!); val p = o.optJSONArray("past") ?: JSONArray()
            Meter(o.optDouble("total", 0.0), o.optDouble("trip", 0.0), o.optLong("since", now),
                (0 until p.length()).map { p.getJSONObject(it).let { t -> Trip(t.getLong("from"), t.getLong("to"), t.getDouble("usd")) } }.toMutableList())
        }.getOrElse { Meter(since = now) }
    }
}

/**
 * Mistral's REST API; [http] posts a path and a body and answers the response's text, [stream] posts and hands back each
 * line of a server-sent stream (tests give their own).
 */
internal class Mistral(private val http: (path: String, body: String) -> String, val bill: Bill = Bill(),
    private val stream: ((path: String, body: String, line: (String) -> Unit) -> Unit)? = null) {
    /** One chat turn; with [onText] (and a [stream]) its words come as they are made, so the voice can start on the first sentence. */
    fun chat(messages: JSONArray, tools: JSONArray, onText: ((String) -> Unit)? = null): JSONObject {
        val body = JSONObject().put("model", CHAT).put("messages", messages).put("tools", tools).put("tool_choice", "auto").put("temperature", 0.2)
        if (onText == null || stream == null) return JSONObject(http("/chat/completions", body.toString()))
            .also { bill.tokens(CHAT, it.optJSONObject("usage")) }
            .getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val text = StringBuilder(); val calls = sortedMapOf<Int, JSONObject>()
        stream.invoke("/chat/completions", body.put("stream", true).toString()) { line ->
            val data = line.removePrefix("data:").trim().takeIf { line.startsWith("data:") && it.startsWith("{") } ?: return@invoke
            val chunk = JSONObject(data)
            chunk.optJSONObject("usage")?.let { bill.tokens(CHAT, it) }
            val delta = chunk.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: return@invoke
            AssistantChat.words(delta).takeIf { it.isNotEmpty() }?.let { text.append(it); onText(it) }
            // a call comes whole or in pieces by its index: the pieces' arguments are joined
            delta.optJSONArray("tool_calls")?.let { tc -> for (i in 0 until tc.length()) {
                val piece = tc.getJSONObject(i); val f = piece.optJSONObject("function") ?: JSONObject()
                val c = calls.getOrPut(piece.optInt("index", calls.size)) { JSONObject().put("type", "function").put("function", JSONObject().put("name", "").put("arguments", "")) }
                piece.optString("id").takeIf { it.isNotEmpty() }?.let { c.put("id", it) }
                val cf = c.getJSONObject("function")
                f.optString("name").takeIf { it.isNotEmpty() }?.let { cf.put("name", it) }
                when (val a = f.opt("arguments")) { is String -> cf.put("arguments", cf.getString("arguments") + a); is JSONObject -> cf.put("arguments", a.toString()) }
            } }
        }
        return JSONObject().put("role", "assistant").put("content", text.toString()).apply { if (calls.isNotEmpty()) put("tool_calls", JSONArray(calls.values)) }
    }

    /** One stateless turn of the Conversations API with one of Mistral's own tools (web_search, image_generation). */
    fun conversation(input: String, tool: String, instructions: String): JSONArray = JSONObject(http("/conversations", JSONObject()
        .put("model", BUILT_IN).put("inputs", input).put("instructions", instructions).put("store", false)
        .put("tools", JSONArray().put(JSONObject().put("type", tool))).toString()))
        .also { bill.tokens(BUILT_IN, it.optJSONObject("usage")); bill.add(Bill.PER_CALL[tool] ?: 0.0) }
        .getJSONArray("outputs")

    companion object {
        const val API = "https://api.mistral.ai/v1"
        const val CHAT = "mistral-small-latest"
        const val BUILT_IN = "mistral-medium-latest" // the model Mistral's web search and image examples use
        const val TRANSCRIBE = "voxtral-small-transcribe-3"
        const val REALTIME = "voxtral-mini-transcribe-realtime-3"
        const val TTS = "voxtral-mini-tts-3"

        /**
         * Voxtral's context_bias from "My words" (comma-separated, Settings › AI Assist) and the extra names: at most 100 terms,
         * as Voxtral takes (the VOICE app's terms()).
         */
        fun terms(words: String, more: List<String> = emptyList()): List<String> =
            (words.split(',') + more).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(100)

        /** A conversation's last answer: its text pieces joined, the sources' titles and links, the files it made. */
        fun output(outputs: JSONArray): Triple<String, List<String>, List<String>> {
            val text = StringBuilder(); val sources = mutableListOf<String>(); val files = mutableListOf<String>()
            for (i in 0 until outputs.length()) {
                val o = outputs.getJSONObject(i)
                if (o.optString("type") != "message.output") continue
                when (val c = o.opt("content")) {
                    is String -> text.append(c)
                    is JSONArray -> for (j in 0 until c.length()) c.optJSONObject(j)?.let { k -> when (k.optString("type")) {
                        "text" -> text.append(k.optString("text"))
                        "tool_reference" -> sources += listOf(k.optString("title"), k.optString("url")).filter { it.isNotBlank() }.joinToString(" — ")
                        "tool_file" -> files += k.optString("file_id")
                    } }
                }
            }
            return Triple(text.toString().trim(), sources.distinct(), files.filter { it.isNotBlank() })
        }
    }
}

/**
 * The tools, each a thin bridge into Tetra's own code on the files under [tetra]. [changed] tells the app what was
 * written ("calendar", "timer", "notes") so it syncs and plans reminders; [search] and [image] are Mistral's tools.
 * One per conversation: the numbered lists it last showed are what "the first one" means.
 */
internal class AssistantTools(
    private val tetra: File,
    private val device: String,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(zone) },
    private val changed: (String) -> Unit = {},
    private val search: (String) -> String = { error("No web search here.") },
    private val image: (String) -> File = { error("No pictures here.") },
    /** The UID of the user's own card in Contacts (Apple's "My Card"), where their relations are kept; asked once. */
    private val me: () -> String? = { null },
    private val setMe: (String) -> Unit = {},
    /** The library's photos taken at a place: by the place name each keeps, else near where [at] finds it. */
    private val photos: (place: String, at: () -> Pair<Double, Double>?) -> List<File> = { _, _ -> emptyList() },
) {
    private var contacts = emptyList<PendingCall>()
    private var files = emptyList<File>()
    private var notesFound = emptyList<Note>()

    fun run(name: String, args: JSONObject): ToolResult = runCatching {
        when (name) {
            "calendar_add" -> calendarAdd(args)
            "calendar_list" -> calendarList(args)
            "calendar_free" -> calendarFree(args)
            "timer_start" -> timerStart(args)
            "timer_list" -> timerList()
            "timer_stop" -> timerStop(args)
            "notes_search" -> notesSearch(args)
            "note_new" -> noteNew(args)
            "note_open" -> pick(notesFound, args) { n -> ToolResult("Opened the note.", go = "note:${n.id}") }
            "navigate" -> navigate(args)
            "photos_place" -> photosPlace(args)
            "youtube" -> args.getString("query").trim().let { q -> ToolResult("YouTube is open on a search for \"$q\".", go = "youtube:$q") }
            "shopping_list" -> shoppingList(args)
            "files_find" -> filesFind(args)
            "file_open" -> pick(files, args) { f -> ToolResult("Opened ${f.name}.", open = f) }
            "contacts_find" -> contactsFind(args)
            "my_card" -> myCard(args)
            "relation_remember" -> relationRemember(args)
            "relation_forget" -> relationForget(args)
            "contact_call" -> pick(contacts, args) { c -> ToolResult("The call to ${c.name} waits for the user's yes (a button on the screen).", call = c) }
            "web_search" -> ToolResult(search(args.getString("query")))
            "make_image" -> image(args.getString("prompt")).let { ToolResult("The picture is on the screen, saved as ${it.name}.", image = it) }
            else -> ToolResult("No tool called $name.")
        }
    }.getOrElse { ToolResult("Failed: ${it.message}") }

    private fun <T> pick(list: List<T>, args: JSONObject, use: (T) -> ToolResult): ToolResult {
        val n = args.optInt("choice", 0)
        return list.getOrNull(n - 1)?.let(use) ?: ToolResult("No choice $n: the last list had ${list.size}.")
    }

    // ---- Calendar ----
    private fun calendars() = CalendarFiles.calendars(CalendarFiles.root(tetra))
    private fun time(s: String): ZonedDateTime = LocalDateTime.parse(s.trim().replace(' ', 'T').take(16)).atZone(zone)
    private fun day(s: String): LocalDate = LocalDate.parse(s.trim().take(10))

    private fun calendarAdd(a: JSONObject): ToolResult {
        val title = a.getString("title").trim()
        val allDay = a.optBoolean("all_day", false)
        val start = if (allDay) day(a.getString("start")).atStartOfDay(zone) else time(a.getString("start"))
        val end = a.optString("end").takeIf { it.isNotBlank() }?.let { if (allDay) day(it).plusDays(1).atStartOfDay(zone) else time(it) }
            ?: if (allDay) start.plusDays(1) else start.plusHours(1)
        val root = CalendarFiles.root(tetra)
        val cal = calendars().firstOrNull() ?: CalendarFiles.newCalendar(root.apply { mkdirs() }, "Calendar", CalendarFiles.PALETTE[0])
        val uid = CalendarFiles.newUid()
        CalendarFiles.save(CalendarFiles.newFile(cal.dir, title, uid),
            CalEvent(uid, title, a.optString("place"), "", allDay, start, end, alarms = defaultAlarms(allDay, start)))
        changed("calendar")
        return ToolResult("Added \"$title\" on ${start.format(SHOWN)} to the calendar ${cal.name}.")
    }

    private fun calendarList(a: JSONObject): ToolResult {
        val from = day(a.getString("from")).atStartOfDay(zone)
        val to = a.optString("to").takeIf { it.isNotBlank() }?.let { day(it) }?.plusDays(1)?.atStartOfDay(zone) ?: from.plusDays(1)
        val found = CalendarFiles.occurrences(calendars(), from.toInstant(), to.toInstant())
        if (found.isEmpty()) return ToolResult("Nothing in the calendar then.")
        return ToolResult(found.take(30).joinToString("\n") { o ->
            (if (o.allDay) o.start.toLocalDate().toString() + " all day" else o.start.withZoneSameInstant(zone).format(SHOWN) + "–" + o.end.withZoneSameInstant(zone).toLocalTime().format(HM)) + ": " + o.event.title +
                o.event.place.takeIf { it.isNotBlank() }?.let { " @ $it" }.orEmpty()
        })
    }

    /** The free stretches of a day between 08:00 and 21:00 of at least [minutes]. */
    private fun calendarFree(a: JSONObject): ToolResult {
        val d = day(a.getString("date"))
        val need = Duration.ofMinutes(a.optLong("minutes", 60))
        val dayStart = d.atTime(DAY_FROM).atZone(zone); val dayEnd = d.atTime(DAY_TO).atZone(zone)
        val busy = CalendarFiles.occurrences(calendars(), dayStart.toInstant(), dayEnd.toInstant()).filter { !it.allDay }
            .map { it.start.toInstant() to it.end.toInstant() }.sortedBy { it.first }
        val free = mutableListOf<String>()
        var at = maxOf(dayStart.toInstant(), now().toInstant().takeIf { d == now().toLocalDate() } ?: dayStart.toInstant())
        for ((s, e) in busy + (dayEnd.toInstant() to dayEnd.toInstant())) {
            if (Duration.between(at, s) >= need) free += at.atZone(zone).toLocalTime().format(HM) + "–" + s.atZone(zone).toLocalTime().format(HM)
            if (e > at) at = e
        }
        return ToolResult(if (free.isEmpty()) "No free time of that length on $d." else "Free on $d: " + free.joinToString(", "))
    }

    // ---- Timer ----
    private fun timers() = TimerFiles.root(tetra)

    private fun timerStart(a: JSONObject): ToolResult {
        val seconds = a.optLong("minutes", 0) * 60 + a.optLong("seconds", 0)
        if (seconds <= 0) return ToolResult("Say how long.")
        val r = TimerFiles.start(timers(), seconds, device, now().toInstant(), a.optString("label"))
        changed("timer")
        return ToolResult("Started a timer of ${seconds / 60} min ${seconds % 60} s" + r.label.let { if (it.isBlank()) "." else " ($it)." })
    }

    private fun timerList(): ToolResult {
        val at = now().toInstant()
        val list = TimerFiles.active(timers(), at)
        if (list.isEmpty()) return ToolResult("No timer is running.")
        return ToolResult(list.mapIndexed { i, r -> "${i + 1}. ${r.label.ifBlank { "timer" }}: ${r.leftMs(at) / 60000} min ${r.leftMs(at) / 1000 % 60} s left, ${r.state}" }.joinToString("\n"))
    }

    private fun timerStop(a: JSONObject): ToolResult {
        val list = TimerFiles.active(timers(), now().toInstant())
        val n = a.optInt("choice", 0)
        val stop = if (n == 0) list else listOfNotNull(list.getOrNull(n - 1))
        if (stop.isEmpty()) return ToolResult("No such timer is running.")
        stop.forEach { TimerFiles.write(timers(), it.stop(device)) }
        changed("timer")
        return ToolResult("Stopped ${stop.size} timer(s).")
    }

    // ---- Notes ----
    private fun notes() = NotesStore(File(tetra, ".notes"), "tetra-assistant")

    // shortcut: reads every note whole for each search (the user: find what a note has inside); an on-disk text index past a few thousand notes
    private fun notesSearch(a: JSONObject): ToolResult {
        val q = ContactsFiles.plain(a.getString("query"))
        val store = notes()
        val hits = store.whole(store.list().filter { it.trashedAt == null }.map { it.id })
            .filter { ContactsFiles.plain(it.title + "\n" + it.content + "\n" + it.items.joinToString("\n") { i -> i.text }).contains(q) }
            .sortedByDescending { it.updatedAt }.take(9)
        notesFound = hits
        if (hits.isEmpty()) return ToolResult("No note has that.")
        fun title(n: Note) = n.title.ifBlank { n.content.lineSequence().firstOrNull { it.isNotBlank() }?.take(40) ?: "(untitled)" }
        return ToolResult(hits.mapIndexed { i, n ->
            val line = (n.content.lines() + n.items.map { it.text }).firstOrNull { ContactsFiles.plain(it).contains(q) }.orEmpty().trim().take(80)
            "${i + 1}. ${title(n)}" + if (line.isNotBlank()) ": $line" else ""
        }.joinToString("\n"), choices = hits.map { AssistantChoice(title(it), go = "note:${it.id}") })
    }

    private fun noteNew(a: JSONObject): ToolResult {
        val text = a.getString("text").trim().ifEmpty { return ToolResult("Say what the note says.") }
        val store = notes()
        val n = store.create(checklist = false)
        store.setText(n.id, a.optString("title").trim(), text, null)
        changed("notes")
        return ToolResult("Saved the note in Notes.", choices = listOf(AssistantChoice(a.optString("title").ifBlank { text.take(40) }, go = "note:${n.id}")))
    }

    // ---- Maps and Photos: the installed map, offline ----
    // shortcut: ranks matches from the middle of Greece, not from where the phone is; pass the fix when "the nearest Lidl" matters
    private fun locate(text: String): Place? {
        val root = MapsFiles.root(tetra)
        val pois = runCatching { MapsSearch.load(root) }.getOrDefault(emptyList())
        runCatching { MapsAddresses.locate(root, text, pois) }.getOrNull()?.let { (lat, lng) -> return Place("", text, lat, lng) }
        return MapsSearch.search(pois, text, 38.4, 23.8, limit = 1).firstOrNull()?.let { Place("", it.name, it.lat, it.lng) }
    }

    private fun navigate(a: JSONObject): ToolResult {
        val contact = a.optString("contact").trim()
        // a contact's address stays on the phone: the model says whose, Tetra looks it up
        val where = if (contact.isNotEmpty()) named(contact).firstNotNullOfOrNull { it.addresses.firstOrNull()?.text()?.takeIf { t -> t.isNotBlank() } }
            ?: return ToolResult("No address is saved for $contact.")
            else a.optString("place").trim().ifEmpty { return ToolResult("Say where to.") }
        val p = locate(where) ?: return ToolResult("Not on the map; Tetra Maps is open on a search for it.", go = "maps-search:$where")
        return ToolResult("Directions to ${if (contact.isNotEmpty()) contact else p.name} started in Tetra Maps.", go = "maps-go:${p.lat},${p.lng},${p.name}")
    }

    private fun photosPlace(a: JSONObject): ToolResult {
        val place = a.getString("place").trim()
        val found = photos(place) { locate(place)?.let { it.lat to it.lng } }
        if (found.isEmpty()) return ToolResult("No photo from $place.")
        return ToolResult("${found.size} photos from $place; the newest are on the screen.", photos = found.take(SHOWN_PHOTOS))
    }

    private fun shoppingList(a: JSONObject): ToolResult {
        val items = a.getJSONArray("items").let { arr -> (0 until arr.length()).map { arr.getString(it).trim() }.filter { it.isNotEmpty() } }
        val store = notes()
        val n = store.create(checklist = true)
        store.setText(n.id, a.optString("title").ifBlank { "Ψώνια" }, "", items.mapIndexed { i, t -> CheckItem(java.util.UUID.randomUUID().toString(), t, false, i) })
        changed("notes")
        return ToolResult("Made the list \"${a.optString("title").ifBlank { "Ψώνια" }}\" with ${items.size} items in Notes.")
    }

    // ---- Files ----
    private fun filesFind(a: JSONObject): ToolResult {
        val q = ContactsFiles.plain(a.getString("query"))
        val found = DriveRules.allItems(tetra).filter { !it.isDirectory && ContactsFiles.plain(it.file.name).contains(q) }
            .sortedByDescending { it.lastModified }.take(8)
        files = found.map { it.file }
        if (found.isEmpty()) return ToolResult("No file's name has that.")
        return ToolResult(found.mapIndexed { i, it -> "${i + 1}. ${it.relativePath}" }.joinToString("\n"),
            choices = found.map { AssistantChoice(it.file.name, file = it.file) })
    }

    // ---- Contacts ----
    /** Each match's name and the label of each of its numbers: the numbers themselves stay on the phone. */
    private fun contactsFind(a: JSONObject): ToolResult {
        val relation = a.optString("relation").trim()
        val found = if (relation.isNotEmpty()) {
            val card = myContact() ?: return ToolResult(NO_ME)
            val names = card.info.related.filter { sameLabel(it.label, relation) }.map { it.value }
            if (names.isEmpty()) return ToolResult("No $relation is saved on the user's card. Ask who it is, then relation_remember.")
            names.flatMap { n -> named(n) }.distinct().filter { it.phones.isNotEmpty() }
                .ifEmpty { return ToolResult("The user's $relation is ${names.joinToString(", ")}, but no contact by that name has a phone number.") }
        } else {
            val keys = a.optJSONArray("keywords")?.let { k -> (0 until k.length()).map { ContactsFiles.plain(k.optString(it)).trim() } }.orEmpty().filter { it.length >= 3 }
            val q = a.optString("name").ifBlank { if (keys.isEmpty()) return ToolResult("Say a name, a relation or some words.") else "" }
            val words = ContactsFiles.plain(q).split(' ').filter { it.isNotBlank() }
            val all = allContacts().map { it.info }
            val byName = if (words.isEmpty()) emptyList() else all.filter { i -> i.phones.isNotEmpty() && words.all { w -> ContactsFiles.matches(i, w) } }
            // "η κομμώτρια": the words the model gives (κομμωτ, κουρε, hair) in anything the card says, and the user's own relations' labels
            byName.ifEmpty {
                val mine = myContact()?.info?.related.orEmpty().filter { r -> keys.any { ContactsFiles.plain(r.label).contains(it) } }.flatMap { named(it.value) }
                (mine + all.filter { i -> keys.any { about(i).contains(it) } }).distinct().filter { it.phones.isNotEmpty() }
            }.sortedBy { ContactsFiles.sortKey(it, false) }.take(9)
        }
        contacts = found.flatMap { i ->
            // the number marked as the default is the one called, without asking (the user); all of them only when none is
            val phones = i.phones.filter { it.value == i.defaultPhone }.ifEmpty { i.phones }
            phones.map { p -> PendingCall(if (phones.size > 1) "${i.name} (${p.label.ifBlank { "phone" }})" else i.name, p.value) }
        }
        if (contacts.isEmpty()) return ToolResult("No contact like that with a phone number.")
        return ToolResult(contacts.mapIndexed { n, c -> "${n + 1}. ${c.name}" }.joinToString("\n"),
            choices = contacts.map { AssistantChoice(it.name, call = it) })
    }

    // ---- Relations: on the user's own card, as Apple and Google write them (X-ABRELATEDNAMES with its X-ABLabel), so they
    // sync and show in Contacts, where they can be changed; no file of the assistant's own ----
    private fun allContacts() = ContactsFiles.all(ContactsFiles.root(tetra))
    private fun myContact() = me()?.let { uid -> allContacts().firstOrNull { it.info.uid == uid } }
    private fun sameLabel(a: String, b: String) = ContactsFiles.plain(a) == ContactsFiles.plain(b)
    /** All a card says about who someone is, without accents: what keywords are looked for in. */
    private fun about(i: ContactInfo) = ContactsFiles.plain((listOf(i.name, i.nickname, i.org, i.department, i.title, i.note) + i.categories +
        i.related.flatMap { listOf(it.label, it.value) }).joinToString("\n"))

    /** The contacts a name means: the one with that very name, else those with all its words. */
    private fun named(name: String): List<ContactInfo> {
        val all = allContacts().map { it.info }
        all.filter { ContactsFiles.plain(it.name) == ContactsFiles.plain(name) }.takeIf { it.isNotEmpty() }?.let { return it }
        val words = ContactsFiles.plain(name).split(' ').filter { it.isNotBlank() }
        return all.filter { i -> words.isNotEmpty() && words.all { w -> ContactsFiles.matches(i, w) } }.sortedBy { ContactsFiles.sortKey(it, false) }
    }
    private fun one(name: String, use: (ContactInfo) -> ToolResult): ToolResult {
        val found = named(name)
        return when (found.size) {
            0 -> ToolResult("No contact called $name.")
            1 -> use(found.single())
            else -> ToolResult("Several contacts match $name: ${found.take(9).joinToString("; ") { it.name }}. Ask which, then call again with the full name.")
        }
    }

    private fun myCard(a: JSONObject) = one(a.getString("name")) { i -> setMe(i.uid); ToolResult("Saved: the user's own card is ${i.name}.") }

    private fun relationRemember(a: JSONObject): ToolResult {
        val relation = a.getString("relation").trim().ifEmpty { return ToolResult("Say the relation.") }
        val label = VCard.RELATIONS.firstOrNull { sameLabel(it, relation) } ?: relation
        val card = myContact() ?: return ToolResult(NO_ME)
        return one(a.getString("name")) { person ->
            val related = card.info.related
            if (related.any { sameLabel(it.label, label) && sameLabel(it.value, person.name) }) return@one ToolResult("Already saved: ${person.name} is the user's $label.")
            // a spouse or a mother is one; brothers, sisters, children and friends may be several
            val replaced = if (label in ONLY_ONE || label !in VCard.RELATIONS) related.filter { sameLabel(it.label, label) } else emptyList()
            ContactsFiles.save(ContactsFiles.root(tetra), card, card.info.copy(related = related - replaced.toSet() + VField(label, person.name)))
            changed("contacts")
            ToolResult("Saved on the user's card: ${person.name} is their $label." +
                if (replaced.isEmpty()) "" else " It replaced ${replaced.joinToString(", ") { it.value }}; tell the user.")
        }
    }

    private fun relationForget(a: JSONObject): ToolResult {
        val relation = a.getString("relation").trim()
        val card = myContact() ?: return ToolResult(NO_ME)
        val name = a.optString("name").trim()
        val gone = card.info.related.filter { sameLabel(it.label, relation) && (name.isEmpty() || ContactsFiles.plain(it.value).contains(ContactsFiles.plain(name))) }
        if (gone.isEmpty()) return ToolResult("No $relation was saved.")
        ContactsFiles.save(ContactsFiles.root(tetra), card, card.info.copy(related = card.info.related - gone.toSet()))
        changed("contacts")
        return ToolResult("Forgot: ${gone.joinToString(", ") { it.value }} as the user's $relation.")
    }

    companion object {
        private val SHOWN = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm")
        private val HM = DateTimeFormatter.ofPattern("HH:mm")
        private val DAY_FROM = LocalTime.of(8, 0)
        private val DAY_TO = LocalTime.of(21, 0)
        private const val SHOWN_PHOTOS = 6

        private fun fn(name: String, what: String, required: List<String>, vararg props: Pair<String, JSONObject>) = JSONObject().put("type", "function")
            .put("function", JSONObject().put("name", name).put("description", what).put("parameters", JSONObject().put("type", "object")
                .put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } }).put("required", JSONArray(required))))
        private fun p(type: String, what: String) = JSONObject().put("type", type).put("description", what)
        private val CHOICE = "choice" to p("integer", "The number of the item in the list last shown (1 = the first).")

        private const val NO_ME = "Tetra does not know which contact is the user. Ask the user for their own name as saved in Contacts, then call my_card, then try again."
        private const val RELATION = "Spouse, Partner, Mother, Father, Parent, Brother, Sister, Child, Friend, Assistant or Manager in English; any other in the user's own word (κουμπάρος)."
        private val ONLY_ONE = setOf("Spouse", "Partner", "Mother", "Father", "Assistant", "Manager")

        /** What the model may call, in Mistral's function-calling form. */
        val SCHEMA: JSONArray = JSONArray(listOf(
            fn("calendar_add", "Add an event to the user's calendar.", listOf("title", "start"),
                "title" to p("string", "The event's name, in the user's words."), "start" to p("string", "Local start, YYYY-MM-DDTHH:MM (a date alone when all_day)."),
                "end" to p("string", "Local end, same form; leave out for one hour."), "all_day" to p("boolean", "A whole-day event."), "place" to p("string", "Where, if said.")),
            fn("calendar_list", "What is in the calendar from one day to another.", listOf("from"),
                "from" to p("string", "First day, YYYY-MM-DD."), "to" to p("string", "Last day, YYYY-MM-DD; leave out for one day.")),
            fn("calendar_free", "Free times on a day between 08:00 and 21:00.", listOf("date"),
                "date" to p("string", "YYYY-MM-DD."), "minutes" to p("integer", "How long the free time must be; 60 if not said.")),
            fn("timer_start", "Start a countdown timer in Tetra.", emptyList(),
                "minutes" to p("integer", "Minutes."), "seconds" to p("integer", "Seconds, besides the minutes."), "label" to p("string", "What it is for, if said.")),
            fn("timer_list", "The running and paused timers and the time left.", emptyList()),
            fn("timer_stop", "Stop a timer from the last timer_list, or all of them.", emptyList(), "choice" to p("integer", "Its number in timer_list; leave out to stop all.")),
            fn("notes_search", "Find notes with some words anywhere in them; answers a numbered list of titles and the matching line.", listOf("query"), "query" to p("string", "Words to look for.")),
            fn("note_open", "Open a note from the last notes_search list in Tetra's Notes.", listOf("choice"), CHOICE),
            fn("note_new", "Make a new text note with what the user dictates.", listOf("text"),
                "text" to p("string", "The note's text, as said."), "title" to p("string", "A title, only if the user gave one.")),
            fn("shopping_list", "Make a new checklist note (a shopping list).", listOf("items"),
                "title" to p("string", "The note's title; Ψώνια if not said."), "items" to JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("description", "One entry per item.")),
            fn("files_find", "Find files in Tetra whose name contains some words; answers a numbered list.", listOf("query"), "query" to p("string", "Part of the file's name.")),
            fn("file_open", "Open a file from the last files_find list.", listOf("choice"), CHOICE),
            fn("contacts_find", "Find the user's contacts by name, by their relation to the user (\"my sister\"), or by what they do (keywords); answers a numbered list of names (with the kind of number when one has several).", emptyList(),
                "name" to p("string", "The name as said."), "relation" to p("string", RELATION),
                "keywords" to JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))
                    .put("description", "For a person known by a job or a role (η κομμώτρια, ο υδραυλικός): several related words and stems in Greek and English (κομμωτ, κουρε, κομμωτηριο, hair), looked for in names, companies, notes and labels.")),
            fn("relation_remember", "Save who someone is to the user (\"Maria K. is my sister\") on the user's own card in Contacts. No need to ask first.", listOf("relation", "name"),
                "relation" to p("string", RELATION), "name" to p("string", "The contact's name.")),
            fn("relation_forget", "Forget a saved relation.", listOf("relation"), "relation" to p("string", RELATION), "name" to p("string", "Only this person, when several have the relation.")),
            fn("my_card", "Say which contact is the user themself, once, when a relation tool asks for it.", listOf("name"), "name" to p("string", "The user's own name as in Contacts.")),
            fn("contact_call", "Ask to call a contact from the last contacts_find list. The phone asks the user to confirm; it does not call by itself.", listOf("choice"), CHOICE),
            fn("navigate", "Start driving directions in Tetra Maps (offline map), to a place or to a contact's saved address.", emptyList(),
                "place" to p("string", "A place, a town or an address (\"Ερμού 10, Αθήνα\")."), "contact" to p("string", "A contact's name, to go to their address.")),
            fn("photos_place", "Show the user's photos taken at a place.", listOf("place"), "place" to p("string", "A town or a place.")),
            fn("youtube", "Open YouTube on a search for a song or a video.", listOf("query"), "query" to p("string", "The song or video, with the artist if said.")),
            fn("web_search", "Search the web for current facts, news, opening hours, prices.", listOf("query"), "query" to p("string", "What to search for.")),
            fn("make_image", "Draw a picture from a description.", listOf("prompt"), "prompt" to p("string", "What the picture shows, in detail.")),
        ))

        fun system(now: ZonedDateTime) = """
            You are Tetra Assistant, on the user's Android phone, inside the app Tetra (calendar, timers, notes, files, contacts, maps, photos).
            Now: ${now.format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm"))} (${now.zone}).
            Answer in the user's language (usually Greek), in one or two short plain sentences: answers are often read aloud. No markdown.
            Use the tools for anything about the user's own calendar, timers, notes, files or contacts; never invent what they hold.
            When a search finds several, say how many and ask which one (the screen shows the numbered list); "the first", "η δεύτερη" mean that list's numbers.
            A call is never placed by you: after contact_call say that the call waits for their yes.
            "My sister", "ο μπαμπάς μου", "η γυναίκα μου": contacts_find with that relation. When the user says who someone is to them, relation_remember it.
            Someone known by a job or role ("πάρε την κομμώτρια"): contacts_find by relation, and if none, by keywords.
            "Ψάξε στο internet", "google it", news, prices, opening hours, anything current: always web_search.
            "Κλείσε ραντεβού" without a time: calendar_free first, offer two or three times, then calendar_add the one chosen.
            "Πάμε εκεί", "βάλε προορισμό": navigate; "there" is the last place in this conversation (an event's place, a search result); for a contact's home, navigate with contact.
            A song or a video: youtube. "Κάνε σημείωση": note_new.
            Work out dates yourself from today's date ("αύριο", "την Πέμπτη").
        """.trimIndent()
    }
}

/**
 * One conversation: the messages so far, forgotten [forgetMs] after the user's last words (the voice sheet: a minute, so
 * "την πρώτη" follows "βρες τη Μαρία"; the chat screen keeps it until New chat).
 */
internal class AssistantChat(private val api: Mistral, private val tools: AssistantTools, private val forgetMs: Long = Long.MAX_VALUE,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() }) {
    private val messages = mutableListOf<JSONObject>()
    private var lastAt = 0L
    val bill get() = api.bill

    /** The user's [text] answered, the tools run on the way; [onText] gets the answer's words as they come. */
    fun ask(text: String, at: Long = System.currentTimeMillis(), onText: ((String) -> Unit)? = null): AssistantReply {
        if (at - lastAt > forgetMs) messages.clear()
        lastAt = at
        messages += JSONObject().put("role", "user").put("content", text)
        val choices = mutableListOf<AssistantChoice>(); val images = mutableListOf<File>()
        var call: PendingCall? = null; var open: File? = null; var go: String? = null
        repeat(ROUNDS) {
            val m = api.chat(JSONArray(listOf(JSONObject().put("role", "system").put("content", AssistantTools.system(now()))) + messages), AssistantTools.SCHEMA, onText)
            val calls = m.optJSONArray("tool_calls")
            messages += JSONObject().put("role", "assistant").put("content", m.opt("content")?.takeIf { it != JSONObject.NULL } ?: "")
                .apply { if (calls != null && calls.length() > 0) put("tool_calls", calls) }
            if (calls == null || calls.length() == 0) return AssistantReply(text(m), choices, call, images, open, go)
            for (i in 0 until calls.length()) {
                val c = calls.getJSONObject(i); val f = c.getJSONObject("function")
                val args = when (val a = f.opt("arguments")) { is JSONObject -> a; is String -> runCatching { JSONObject(a.ifBlank { "{}" }) }.getOrDefault(JSONObject()); else -> JSONObject() }
                val r = tools.run(f.getString("name"), args)
                if (r.choices.isNotEmpty()) { choices.clear(); choices += r.choices }
                r.call?.let { call = it }; r.image?.let { images += it }; images += r.photos; r.open?.let { open = it }; r.go?.let { go = it }
                messages += JSONObject().put("role", "tool").put("name", f.getString("name")).put("tool_call_id", c.optString("id")).put("content", r.forModel)
            }
        }
        return AssistantReply("", choices, call, images, open, go)
    }

    fun clear() { messages.clear(); bill.reset() }

    companion object {
        const val ROUNDS = 5 // tool rounds in one turn at most: find, then act, with room to spare

        /** A message's text: a string, or Mistral's chunks (text pieces among others). */
        fun text(m: JSONObject): String = words(m).trim()
        /** The same, spaces kept: a streamed piece's edges are the spaces between words. */
        fun words(m: JSONObject): String = when (val c = m.opt("content")) {
            is String -> c
            is JSONArray -> (0 until c.length()).mapNotNull { c.optJSONObject(it)?.takeIf { k -> k.optString("type") == "text" }?.optString("text") }.joinToString("")
            else -> ""
        }

        /** A spoken yes or no to a waiting call, heard by the phone itself: true, false, or null for anything else. */
        fun yesNo(said: String): Boolean? {
            val first = ContactsFiles.plain(said).split(Regex("[^\\p{L}]+")).firstOrNull { it.isNotEmpty() } ?: return null
            return when (first) { in YES -> true; in NO -> false; else -> null }
        }
        private val YES = setOf("ναι", "οκ", "εντάξει", "ντάξει", "κάλεσε", "πάρε", "ok", "okay", "yes").map(ContactsFiles::plain).toSet()
        private val NO = setOf("όχι", "άκυρο", "ακύρωσε", "σταμάτα", "no", "cancel").map(ContactsFiles::plain).toSet()
    }
}

/**
 * An answer coming a few words at a time, cut into sentences for the voice: each is read as soon as it ends and the next
 * made while it plays (the user: the voice was slow). A cut needs a stop and a space after it ("3.5" stays whole) and a
 * dozen letters before it, so "κ. Νίκος" or "Ναι." do not go alone.
 */
internal class Sentences {
    private val buf = StringBuilder()
    fun push(words: String): List<String> {
        buf.append(words)
        val out = mutableListOf<String>()
        while (true) {
            val end = END.findAll(buf).firstOrNull { buf.substring(0, it.range.first).trim().length >= 12 } ?: break
            out += buf.substring(0, end.range.last + 1).trim()
            buf.delete(0, end.range.last + 1)
        }
        return out
    }
    /** What is left once the answer is whole. */
    fun rest(): String = buf.toString().trim().also { buf.clear() }

    private companion object { val END = Regex("[.!?;…·:]+\\s+|\n+") }
}

/** Voice activity: 30 ms frames of 16 kHz speech in, one phrase out when the speaker pauses (the VOICE app's Vad, ported). */
internal class Vad(private val pauseFrames: Int = 30, private val maxFrames: Int = 20 * 1000 / 30) {
    private var noise = 200.0
    private val calib = mutableListOf<Double>()
    private val pre = ArrayDeque<ShortArray>()
    private val seg = mutableListOf<ShortArray>()
    private var silence = 0
    private var speech = 0
    var speaking = false; private set

    /** True when a phrase has ended: [phrase] holds it. */
    fun push(frame: ShortArray): Boolean {
        val r = rms(frame)
        if (calib.size < 13) { calib += r; noise = calib.average(); return false }
        val th = maxOf(noise * 2.5 + 120, 280.0)
        if (!speaking) {
            pre.addLast(frame); if (pre.size > 6) pre.removeFirst()
            if (r > th) { speaking = true; silence = 0; speech = 1; seg.clear(); seg += pre } else noise = 0.95 * noise + 0.05 * r
            return false
        }
        seg += frame
        if (r > th) { silence = 0; speech++ } else silence++
        return (silence >= pauseFrames || seg.size >= maxFrames) && speech >= 7
    }

    /** What was said so far (also when the user taps stop mid-phrase); empty when nothing was. */
    fun phrase(): ShortArray = if (speech < 7) ShortArray(0) else ShortArray(seg.sumOf { it.size }).also { out -> var n = 0; seg.forEach { it.copyInto(out, n); n += it.size } }

    companion object {
        const val RATE = 16000
        const val FRAME = 480
        fun rms(f: ShortArray): Double = if (f.isEmpty()) 0.0 else Math.sqrt(f.sumOf { it.toDouble() * it } / f.size)

        /** 16-bit mono PCM as a WAV file. */
        fun wav(pcm: ShortArray): ByteArray = java.nio.ByteBuffer.allocate(44 + pcm.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size * 2); put("WAVEfmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16); put("data".toByteArray()); putInt(pcm.size * 2)
            pcm.forEach { putShort(it) }
        }.array()
    }
}
