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

/** Something the screen offers to tap: a contact to call or a file to open. */
internal data class AssistantChoice(val label: String, val call: PendingCall? = null, val file: File? = null)

/** What a tool hands back: words for the model (as few as it needs), and what the screen shows besides. */
internal data class ToolResult(val forModel: String, val choices: List<AssistantChoice> = emptyList(), val call: PendingCall? = null,
    val image: File? = null, val open: File? = null)

/** The model's answer to one turn, with everything the tools left for the screen. */
internal data class AssistantReply(val text: String, val choices: List<AssistantChoice> = emptyList(), val call: PendingCall? = null,
    val images: List<File> = emptyList(), val open: File? = null)

/** Mistral's REST API; [http] posts a path and a body and answers the response's text (tests give their own). */
internal class Mistral(private val http: (path: String, body: String) -> String) {
    fun chat(messages: JSONArray, tools: JSONArray): JSONObject = JSONObject(http("/chat/completions", JSONObject()
        .put("model", CHAT).put("messages", messages).put("tools", tools).put("tool_choice", "auto").put("temperature", 0.2).toString()))
        .getJSONArray("choices").getJSONObject(0).getJSONObject("message")

    /** One stateless turn of the Conversations API with one of Mistral's own tools (web_search, image_generation). */
    fun conversation(input: String, tool: String, instructions: String): JSONArray = JSONObject(http("/conversations", JSONObject()
        .put("model", BUILT_IN).put("inputs", input).put("instructions", instructions).put("store", false)
        .put("tools", JSONArray().put(JSONObject().put("type", tool))).toString())).getJSONArray("outputs")

    companion object {
        const val API = "https://api.mistral.ai/v1"
        const val CHAT = "mistral-small-latest"
        const val BUILT_IN = "mistral-medium-latest" // the model Mistral's web search and image examples use
        const val TRANSCRIBE = "voxtral-small-transcribe-3"

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
) {
    private var contacts = emptyList<PendingCall>()
    private var files = emptyList<File>()

    fun run(name: String, args: JSONObject): ToolResult = runCatching {
        when (name) {
            "calendar_add" -> calendarAdd(args)
            "calendar_list" -> calendarList(args)
            "calendar_free" -> calendarFree(args)
            "timer_start" -> timerStart(args)
            "timer_list" -> timerList()
            "timer_stop" -> timerStop(args)
            "notes_search" -> notesSearch(args)
            "shopping_list" -> shoppingList(args)
            "files_find" -> filesFind(args)
            "file_open" -> pick(files, args) { f -> ToolResult("Opened ${f.name}.", open = f) }
            "contacts_find" -> contactsFind(args)
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
            (if (o.allDay) o.start.toLocalDate().toString() + " all day" else o.start.withZoneSameInstant(zone).format(SHOWN) + "–" + o.end.withZoneSameInstant(zone).toLocalTime().format(HM)) + ": " + o.event.title
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

    // shortcut: searches what the list keeps of each note (its first lines), not every line of a long one; read whole notes if that misses
    private fun notesSearch(a: JSONObject): ToolResult {
        val q = ContactsFiles.plain(a.getString("query"))
        val hits = notes().list().filter { it.trashedAt == null && ContactsFiles.plain(it.title + "\n" + it.content + "\n" + it.items.joinToString("\n") { i -> i.text }).contains(q) }
        if (hits.isEmpty()) return ToolResult("No note has that.")
        return ToolResult(hits.take(10).joinToString("\n") { n ->
            val line = (n.content.lines() + n.items.map { it.text }).firstOrNull { ContactsFiles.plain(it).contains(q) }.orEmpty().take(80)
            "- ${n.title.ifBlank { "(untitled)" }}" + if (line.isNotBlank()) ": $line" else ""
        })
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
        val q = a.getString("name")
        val words = ContactsFiles.plain(q).split(' ').filter { it.isNotBlank() }
        val found = ContactsFiles.all(ContactsFiles.root(tetra)).map { it.info }
            .filter { i -> i.phones.isNotEmpty() && words.all { w -> ContactsFiles.matches(i, w) } }
            .sortedBy { ContactsFiles.sortKey(it, false) }.take(9)
        contacts = found.flatMap { i ->
            val phones = i.phones.sortedByDescending { it.value == i.defaultPhone }
            phones.map { p -> PendingCall(if (phones.size > 1) "${i.name} (${p.label.ifBlank { "phone" }})" else i.name, p.value) }
        }
        if (contacts.isEmpty()) return ToolResult("No contact called $q with a phone number.")
        return ToolResult(contacts.mapIndexed { n, c -> "${n + 1}. ${c.name}" }.joinToString("\n"),
            choices = contacts.map { AssistantChoice(it.name, call = it) })
    }

    companion object {
        private val SHOWN = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm")
        private val HM = DateTimeFormatter.ofPattern("HH:mm")
        private val DAY_FROM = LocalTime.of(8, 0)
        private val DAY_TO = LocalTime.of(21, 0)

        private fun fn(name: String, what: String, required: List<String>, vararg props: Pair<String, JSONObject>) = JSONObject().put("type", "function")
            .put("function", JSONObject().put("name", name).put("description", what).put("parameters", JSONObject().put("type", "object")
                .put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } }).put("required", JSONArray(required))))
        private fun p(type: String, what: String) = JSONObject().put("type", type).put("description", what)
        private val CHOICE = "choice" to p("integer", "The number of the item in the list last shown (1 = the first).")

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
            fn("notes_search", "Find notes that contain some words; answers titles and the matching line.", listOf("query"), "query" to p("string", "Words to look for.")),
            fn("shopping_list", "Make a new checklist note (a shopping list).", listOf("items"),
                "title" to p("string", "The note's title; Ψώνια if not said."), "items" to JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("description", "One entry per item.")),
            fn("files_find", "Find files in Tetra whose name contains some words; answers a numbered list.", listOf("query"), "query" to p("string", "Part of the file's name.")),
            fn("file_open", "Open a file from the last files_find list.", listOf("choice"), CHOICE),
            fn("contacts_find", "Find the user's contacts by name; answers a numbered list of names (with the kind of number when one has several).", listOf("name"), "name" to p("string", "The name as said.")),
            fn("contact_call", "Ask to call a contact from the last contacts_find list. The phone asks the user to confirm; it does not call by itself.", listOf("choice"), CHOICE),
            fn("web_search", "Search the web for current facts, news, opening hours, prices.", listOf("query"), "query" to p("string", "What to search for.")),
            fn("make_image", "Draw a picture from a description.", listOf("prompt"), "prompt" to p("string", "What the picture shows, in detail.")),
        ))

        fun system(now: ZonedDateTime) = """
            You are Tetra Assistant, on the user's Android phone, inside the app Tetra (calendar, timers, notes, files, contacts).
            Now: ${now.format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm"))} (${now.zone}).
            Answer in the user's language (usually Greek), in one or two short plain sentences: answers are often read aloud. No markdown.
            Use the tools for anything about the user's own calendar, timers, notes, files or contacts; never invent what they hold.
            When a search finds several, say how many and ask which one (the screen shows the numbered list); "the first", "η δεύτερη" mean that list's numbers.
            A call is never placed by you: after contact_call say that the call waits for their yes.
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

    fun ask(text: String, at: Long = System.currentTimeMillis()): AssistantReply {
        if (at - lastAt > forgetMs) messages.clear()
        lastAt = at
        messages += JSONObject().put("role", "user").put("content", text)
        val choices = mutableListOf<AssistantChoice>(); val images = mutableListOf<File>()
        var call: PendingCall? = null; var open: File? = null
        repeat(ROUNDS) {
            val m = api.chat(JSONArray(listOf(JSONObject().put("role", "system").put("content", AssistantTools.system(now()))) + messages), AssistantTools.SCHEMA)
            val calls = m.optJSONArray("tool_calls")
            messages += JSONObject().put("role", "assistant").put("content", m.opt("content")?.takeIf { it != JSONObject.NULL } ?: "")
                .apply { if (calls != null && calls.length() > 0) put("tool_calls", calls) }
            if (calls == null || calls.length() == 0) return AssistantReply(text(m), choices, call, images, open)
            for (i in 0 until calls.length()) {
                val c = calls.getJSONObject(i); val f = c.getJSONObject("function")
                val args = when (val a = f.opt("arguments")) { is JSONObject -> a; is String -> runCatching { JSONObject(a.ifBlank { "{}" }) }.getOrDefault(JSONObject()); else -> JSONObject() }
                val r = tools.run(f.getString("name"), args)
                if (r.choices.isNotEmpty()) { choices.clear(); choices += r.choices }
                r.call?.let { call = it }; r.image?.let { images += it }; r.open?.let { open = it }
                messages += JSONObject().put("role", "tool").put("name", f.getString("name")).put("tool_call_id", c.optString("id")).put("content", r.forModel)
            }
        }
        return AssistantReply("", choices, call, images, open)
    }

    fun clear() = messages.clear()

    companion object {
        const val ROUNDS = 5 // tool rounds in one turn at most: find, then act, with room to spare

        /** A message's text: a string, or Mistral's chunks (text pieces among others). */
        fun text(m: JSONObject): String = when (val c = m.opt("content")) {
            is String -> c
            is JSONArray -> (0 until c.length()).mapNotNull { c.optJSONObject(it)?.takeIf { k -> k.optString("type") == "text" }?.optString("text") }.joinToString("")
            else -> ""
        }.trim()

        /** A spoken yes or no to a waiting call, heard by the phone itself: true, false, or null for anything else. */
        fun yesNo(said: String): Boolean? {
            val first = ContactsFiles.plain(said).split(Regex("[^\\p{L}]+")).firstOrNull { it.isNotEmpty() } ?: return null
            return when (first) { in YES -> true; in NO -> false; else -> null }
        }
        private val YES = setOf("ναι", "οκ", "εντάξει", "ντάξει", "κάλεσε", "πάρε", "ok", "okay", "yes").map(ContactsFiles::plain).toSet()
        private val NO = setOf("όχι", "άκυρο", "ακύρωσε", "σταμάτα", "no", "cancel").map(ContactsFiles::plain).toSet()
    }
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
