package com.kalotrapezis.drive

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/** The assistant's tools and its talk with the model, against a scripted Mistral and a scratch Tetra folder. */
class AssistantTest {
    private val zone = ZoneId.of("Europe/Athens")
    private val now = ZonedDateTime.of(2026, 10, 29, 9, 0, 0, 0, zone)
    private val tetra = scratchDir("assistant")

    /** Mistral that answers [replies] in turn, keeping each request's body. */
    private class Script(vararg replies: JSONObject) {
        val left = ArrayDeque(replies.toList())
        val sent = mutableListOf<JSONObject>()
        val api = Mistral({ _, body -> sent += JSONObject(body); JSONObject().put("choices", JSONArray().put(JSONObject().put("message", left.removeFirst()))).toString() })
    }
    private fun calls(vararg c: Pair<String, String>) = JSONObject().put("role", "assistant").put("content", "")
        .put("tool_calls", JSONArray(c.mapIndexed { i, (name, args) -> JSONObject().put("id", "call$i").put("function", JSONObject().put("name", name).put("arguments", args)) }))
    private fun says(text: String) = JSONObject().put("role", "assistant").put("content", text)
    private fun tools() = AssistantTools(tetra, "Phone", zone, { now })

    @Test fun aToolCallRunsHereAndItsAnswerGoesBack() {
        val s = Script(calls("timer_start" to """{"minutes":5,"label":"μακαρόνια"}"""), says("Ξεκίνησα χρονόμετρο 5 λεπτών."))
        val reply = AssistantChat(s.api, tools(), now = { now }).ask("βάλε χρονόμετρο 5 λεπτά για τα μακαρόνια")
        assertEquals("Ξεκίνησα χρονόμετρο 5 λεπτών.", reply.text)
        val timer = TimerFiles.read(TimerFiles.root(tetra)).single()
        assertEquals(300L, timer.seconds); assertEquals("μακαρόνια", timer.label)
        // the second request carries the call and the tool's answer, tied by its id
        val second = s.sent[1].getJSONArray("messages")
        val tool = (0 until second.length()).map { second.getJSONObject(it) }.single { it.getString("role") == "tool" }
        assertEquals("call0", tool.getString("tool_call_id"))
        assertTrue(tool.getString("content").startsWith("Started a timer of 5 min"))
        assertEquals("system", second.getJSONObject(0).getString("role"))
        assertEquals(AssistantTools.SCHEMA.length(), s.sent[0].getJSONArray("tools").length())
    }

    @Test fun severalMatchesAreANumberedListAndTheCallWaitsForTheUser() {
        val dir = ContactsFiles.root(tetra)
        ContactsFiles.save(dir, null, ContactInfo(formatted = "Μαρία Παπαδάκη", phones = listOf(VField("Mobile", "+306900000001"), VField("Work", "2310000001"))))
        ContactsFiles.save(dir, null, ContactInfo(formatted = "Μαρία Κωστοπούλου", phones = listOf(VField("Mobile", "+306900000002"))))
        ContactsFiles.save(dir, null, ContactInfo(formatted = "Νίκος Αλεξίου", phones = listOf(VField("Mobile", "+306900000003"))))
        val s = Script(calls("contacts_find" to """{"name":"Μαρια"}"""), says("Βρήκα τρία. Ποιο από αυτά;"),
            calls("contact_call" to """{"choice":2}"""), says("Η κλήση περιμένει το ναι σου."))
        val chat = AssistantChat(s.api, tools(), now = { now })
        val first = chat.ask("βρες τη Μαρία και πάρε τηλέφωνο")
        assertEquals(listOf("Μαρία Κωστοπούλου", "Μαρία Παπαδάκη (Mobile)", "Μαρία Παπαδάκη (Work)"), first.choices.map { it.label })
        assertNull(first.call)
        // the model saw names only, never a number
        val toModel = s.sent[1].getJSONArray("messages").let { m -> m.getJSONObject(m.length() - 1).getString("content") }
        assertFalse(toModel, Regex("\\d{6,}").containsMatchIn(toModel))
        val second = chat.ask("τη δεύτερη")
        assertEquals(PendingCall("Μαρία Παπαδάκη (Mobile)", "+306900000001"), second.call)
    }

    @Test fun calendarAddThenFreeTime() {
        val s = Script(calls("calendar_add" to """{"title":"Οδοντίατρος","start":"2026-10-30T10:00","end":"2026-10-30T11:30"}""",
            "calendar_free" to """{"date":"2026-10-30","minutes":60}"""), says("Έγινε."))
        AssistantChat(s.api, tools(), now = { now }).ask("οδοντίατρος αύριο στις 10")
        val ev = CalendarFiles.occurrences(CalendarFiles.calendars(CalendarFiles.root(tetra)), now.toInstant(), now.plusDays(3).toInstant()).single()
        assertEquals("Οδοντίατρος", ev.event.title)
        assertEquals(10, ev.start.withZoneSameInstant(zone).hour)
        val free = s.sent[1].getJSONArray("messages").let { m -> m.getJSONObject(m.length() - 1).getString("content") }
        assertEquals("Free on 2026-10-30: 08:00–10:00, 11:30–21:00", free)
        val list = tools().run("calendar_list", JSONObject().put("from", "2026-10-30")).forModel
        assertTrue(list, list.endsWith("10:00–11:30: Οδοντίατρος"))
    }

    @Test fun aShoppingListIsAChecklistNote() {
        val t = tools()
        t.run("shopping_list", JSONObject().put("items", JSONArray(listOf("γάλα", "ψωμί", " "))))
        val note = NotesStore(File(tetra, ".notes")).list().single()
        assertTrue(note.checklist); assertEquals("Ψώνια", note.title)
        assertEquals(listOf("γάλα", "ψωμί"), note.items.map { it.text })
        assertEquals("- Ψώνια: ψωμί", t.run("notes_search", JSONObject().put("query", "ΨΩΜΙ")).forModel)
    }

    @Test fun badArgumentsAndUnknownToolsAnswerTheModelNotCrash() {
        assertTrue(tools().run("calendar_add", JSONObject()).forModel.startsWith("Failed"))
        assertEquals("No tool called nope.", tools().run("nope", JSONObject()).forModel)
        assertEquals("No choice 1: the last list had 0.", tools().run("contact_call", JSONObject().put("choice", 1)).forModel)
    }

    @Test fun theSheetForgetsAfterAMinute() {
        val s = Script(says("Γεια."), says("Γεια ξανά."), says("Ναι;"))
        val chat = AssistantChat(s.api, tools(), forgetMs = 60_000, now = { now })
        chat.ask("γεια", at = 1_000)
        chat.ask("τι κάνεις", at = 30_000)
        assertEquals(4, s.sent[1].getJSONArray("messages").length()) // system, user, assistant, user
        chat.ask("άλλο", at = 200_000)
        assertEquals(2, s.sent[2].getJSONArray("messages").length()) // system and the new question only
    }

    @Test fun conversationsOutputIsRead() {
        val outputs = JSONArray("""[{"type":"tool.execution","name":"web_search"},
            {"type":"message.output","role":"assistant","content":[{"type":"text","text":"Ανοίγει στις 9."},
             {"type":"tool_reference","tool":"web_search","title":"Δήμος","url":"https://example.org/a"},
             {"type":"tool_file","tool":"image_generation","file_id":"f-1","file_name":"x","file_type":"png"}]}]""")
        val (text, sources, files) = Mistral.output(outputs)
        assertEquals("Ανοίγει στις 9.", text)
        assertEquals(listOf("Δήμος — https://example.org/a"), sources)
        assertEquals(listOf("f-1"), files)
        assertEquals("ok", Mistral.output(JSONArray("""[{"type":"message.output","content":"ok"}]""")).first)
    }

    @Test fun theBillCountsTokensToolCallsAndMinutes() {
        val usage = JSONObject().put("prompt_tokens", 1_000_000).put("completion_tokens", 1_000_000)
        val api = Mistral({ path, _ -> if (path == "/chat/completions") JSONObject().put("usage", usage).put("choices", JSONArray().put(JSONObject().put("message", says("ok")))).toString()
            else JSONObject().put("outputs", JSONArray()).toString() })
        api.chat(JSONArray(), JSONArray())
        assertEquals(0.4, api.bill.usd, 1e-9) // small: $0.1 in + $0.3 out a million
        api.conversation("καιρός", "web_search", "")
        assertEquals(0.43, api.bill.usd, 1e-9) // a search, no usage given
        api.bill.minutes(Mistral.TRANSCRIBE, 90.0)
        assertEquals(0.436, api.bill.usd, 1e-9)
        assertEquals("$0.4360", Bill.text(api.bill.usd)); assertEquals("$12.50", Bill.text(12.5))
        AssistantChat(api, tools()).clear()
        assertEquals(0.0, api.bill.usd, 0.0) // New chat: a new bill
    }

    @Test fun myWordsAreVoxtralsContextBias() {
        assertEquals(listOf("Tetra", "ΥΠΕΧΩΔΕ", "Μαρία"), Mistral.terms(" Tetra, ΥΠΕΧΩΔΕ,, Tetra ", listOf("Μαρία")))
        assertEquals(100, Mistral.terms((1..150).joinToString(",")).size)
        assertEquals(listOf("Καλοτραπέζης", "Θεολόγος", "Tetra", "ΥΠΕΧΩΔΕ"), Mistral.terms(AssistantWords.DEFAULT))
    }

    @Test fun aSpokenYesIsHeardByThePhone() {
        assertEquals(true, AssistantChat.yesNo("Ναι, κάλεσέ τη"))
        assertEquals(true, AssistantChat.yesNo("εντάξει"))
        assertEquals(false, AssistantChat.yesNo("Όχι, άσ' το"))
        assertNull(AssistantChat.yesNo("την πρώτη"))
        assertNull(AssistantChat.yesNo(""))
    }

    @Test fun aStreamedAnswerComesInPiecesAndItsToolCallIsJoined() {
        fun data(o: JSONObject) = "data: $o"
        fun delta(d: JSONObject) = data(JSONObject().put("choices", JSONArray().put(JSONObject().put("delta", d))))
        val rounds = ArrayDeque(listOf(
            listOf(delta(JSONObject().put("tool_calls", JSONArray().put(JSONObject().put("index", 0).put("id", "c1")
                    .put("function", JSONObject().put("name", "timer_start").put("arguments", """{"minu"""))))),
                delta(JSONObject().put("tool_calls", JSONArray().put(JSONObject().put("index", 0).put("function", JSONObject().put("arguments", "tes\":3}"))))),
                "", "data: [DONE]"),
            listOf(delta(JSONObject().put("content", "Ξεκίνησε. ")), delta(JSONObject().put("content", "Τρία λεπτά.")),
                data(JSONObject().put("choices", JSONArray()).put("usage", JSONObject().put("prompt_tokens", 1_000_000).put("completion_tokens", 0)))),
        ))
        val api = Mistral({ _, _ -> error("not streamed") }, stream = { _, body, line -> assertTrue(JSONObject(body).getBoolean("stream")); rounds.removeFirst().forEach(line) })
        val heard = StringBuilder()
        val reply = AssistantChat(api, tools(), now = { now }).ask("χρονόμετρο τρία λεπτά") { heard.append(it) }
        assertEquals("Ξεκίνησε. Τρία λεπτά.", reply.text); assertEquals(reply.text, heard.toString())
        assertEquals(180L, TimerFiles.read(TimerFiles.root(tetra)).single().seconds)
        assertEquals(0.1, api.bill.usd, 1e-9)
    }

    @Test fun theMeterCountsEveryBillAndAResetKeepsTheHistory() {
        val meter = Meter.of(null, 1_000)
        val a = Bill { meter.add(it) }; val b = Bill { meter.add(it) }
        a.minutes(Mistral.REALTIME, 60.0); b.chars("x".repeat(1000))
        b.reset() // a new chat clears its own bill, not the meter
        assertEquals(0.022, meter.trip, 1e-9)
        meter.reset(5_000); a.add(0.01)
        val back = Meter.of(meter.json(), 9_999) // as saved and read again after an update
        assertEquals(0.032, back.total, 1e-9); assertEquals(0.01, back.trip, 1e-9); assertEquals(5_000L, back.since)
        assertEquals(listOf(Meter.Trip(1_000, 5_000, 0.022)), back.past)
        assertEquals(0.0, Meter.of("not json", 7).total, 0.0)
    }

    @Test fun sentencesGoToTheVoiceAsSoonAsTheyEnd() {
        val cut = Sentences()
        assertEquals(emptyList<String>(), cut.push("Ναι. Η Μαρία"))
        assertEquals(listOf("Ναι. Η Μαρία έχει γενέθλια στις 3.5 το απόγευμα;"), cut.push(" έχει γενέθλια στις 3.5 το απόγευμα; Θέλεις"))
        assertEquals(emptyList<String>(), cut.push(" να της"))
        assertEquals("Θέλεις να της", cut.rest())
        val bill = Bill(); bill.chars("x".repeat(1000)); assertEquals(0.016, bill.usd, 1e-12)
    }

    @Test fun relationsLiveOnTheUsersOwnCard() {
        val dir = ContactsFiles.root(tetra)
        val me = ContactsFiles.save(dir, null, ContactInfo(formatted = "Γιώργος Δημητρίου"))
        ContactsFiles.save(dir, null, ContactInfo(formatted = "Μαρία Κωστοπούλου", phones = listOf(VField("Mobile", "+306900000002"))))
        ContactsFiles.save(dir, null, ContactInfo(formatted = "Ελένη Δημητρίου", phones = listOf(VField("Mobile", "+306900000004"))))
        var mine: String? = null
        val t = AssistantTools(tetra, "Phone", zone, { now }, me = { mine }, setMe = { mine = it })
        fun run(name: String, vararg a: Pair<String, String>) = t.run(name, JSONObject(a.toMap())).forModel
        assertTrue(run("relation_remember", "relation" to "Sister", "name" to "Μαρία Κ").startsWith("Tetra does not know which contact is the user"))
        assertEquals("Saved: the user's own card is Γιώργος Δημητρίου.", run("my_card", "name" to "Γιώργος Δημητρίου"))
        assertEquals(me.info.uid, mine)
        assertEquals("Saved on the user's card: Μαρία Κωστοπούλου is their Sister.", run("relation_remember", "relation" to "sister", "name" to "Μαρία Κ"))
        assertEquals("1. Μαρία Κωστοπούλου", run("contacts_find", "relation" to "Sister"))
        // in Contacts, as Apple and Google write it
        val text = ContactsFiles.root(tetra).listFiles()!!.single { it.readText().contains("Γιώργος") }.readText()
        assertTrue(text, text.contains("X-ABRELATEDNAMES:Μαρία Κωστοπούλου") && text.contains("X-ABLabel:_\$!<Sister>!\$_"))
        // a spouse is one: a new one says what it replaced
        run("relation_remember", "relation" to "Spouse", "name" to "Μαρία Κωστοπούλου")
        assertTrue(run("relation_remember", "relation" to "Spouse", "name" to "Ελένη").contains("It replaced Μαρία Κωστοπούλου"))
        assertEquals("1. Ελένη Δημητρίου", run("contacts_find", "relation" to "spouse"))
        assertEquals("Forgot: Ελένη Δημητρίου as the user's Spouse.", run("relation_forget", "relation" to "Spouse"))
        assertTrue(run("contacts_find", "relation" to "Spouse").startsWith("No Spouse is saved"))
        assertEquals("1. Μαρία Κωστοπούλου", run("contacts_find", "relation" to "Sister")) // the sister stayed
    }

    @Test fun vadEndsAPhraseAtAPauseAndWavIsWellFormed() {
        val vad = Vad()
        fun frame(amp: Int) = ShortArray(Vad.FRAME) { i -> (amp * Math.sin(i * 0.3)).toInt().toShort() }
        repeat(20) { assertFalse(vad.push(frame(30))) }
        repeat(30) { assertFalse(vad.push(frame(4000))) }
        assertTrue(vad.speaking)
        val ended = (1..40).firstOrNull { vad.push(frame(30)) }
        assertEquals(30, ended)
        assertTrue(vad.phrase().size >= 30 * Vad.FRAME)
        val wav = Vad.wav(ShortArray(10) { 1 })
        assertEquals(64, wav.size)
        assertEquals("RIFF", String(wav, 0, 4)); assertEquals("data", String(wav, 36, 4))
        assertEquals(0, Vad().phrase().size) // nothing said
    }
}
