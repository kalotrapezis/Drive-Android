package com.kalotrapezis.drive

import android.content.Context
import android.provider.CallLog
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One line of Android's call log. */
internal data class LoggedCall(val id: Long, val number: String, val at: Long, val seconds: Long, val type: Int, val unseen: Boolean = false) {
    val missed get() = type == CallLog.Calls.MISSED_TYPE
}

/** Calls in a row from one number on one day, shown as one line ("Μαρία (3)"), newest first. */
internal data class CallGroup(val calls: List<LoggedCall>) {
    val newest get() = calls.first()
    val number get() = newest.number
}

/** Android's call log for the Phone's Recents (READ_CALL_LOG / WRITE_CALL_LOG, granted with the Phone app role). */
internal object PhoneLog {
    fun read(c: Context, limit: Int = 600): List<LoggedCall> {
        if (c.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) != android.content.pm.PackageManager.PERMISSION_GRANTED) return emptyList()
        val out = mutableListOf<LoggedCall>()
        runCatching {
            c.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE, CallLog.Calls.NEW),
                null, null, "${CallLog.Calls.DATE} DESC")?.use { q ->
                while (q.moveToNext() && out.size < limit) out.add(LoggedCall(q.getLong(0), q.getString(1).orEmpty(), q.getLong(2), q.getLong(3), q.getInt(4), q.getInt(5) == 1))
            }
        }.onFailure { WriterLog.w("Phone: the call log was not read (${it.message})") }
        return out
    }

    fun delete(c: Context, ids: Collection<Long>) = runCatching {
        ids.chunked(200).forEach { part -> c.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID} IN (${part.joinToString()})", null) }
    }.onFailure { WriterLog.w("Phone: calls not deleted (${it.message})") }

    /** The missed calls seen: Android's missed-call notification goes, and they are no longer "new". */
    fun seen(c: Context) = runCatching {
        c.getSystemService(android.telecom.TelecomManager::class.java).cancelMissedCallsNotification()
        c.contentResolver.update(CallLog.Calls.CONTENT_URI, android.content.ContentValues().apply { put(CallLog.Calls.NEW, 0) },
            "${CallLog.Calls.NEW} = 1 AND ${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE}", null)
    }

    /** Consecutive calls from the same number on the same day become one line. */
    fun group(calls: List<LoggedCall>, zone: ZoneId = ZoneId.systemDefault()): List<CallGroup> {
        val out = mutableListOf<MutableList<LoggedCall>>()
        for (c in calls) {
            val last = out.lastOrNull()?.first()
            if (last != null && same(last.number, c.number) && day(last.at, zone) == day(c.at, zone)) out.last().add(c) else out.add(mutableListOf(c))
        }
        return out.map { CallGroup(it) }
    }

    fun same(a: String, b: String) = ContactsWays.key(a).let { it.length >= 5 && it == ContactsWays.key(b) } || (a.isNotEmpty() && a == b)
    fun day(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
}

/** Search as you type on the keypad (Plan.md § Phone): the keys' Latin and Greek letters, accents ignored. */
internal object PhoneT9 {
    private val KEYS = mapOf(
        '2' to "ABCΑΒΓ", '3' to "DEFΔΕΖ", '4' to "GHIΗΘΙ", '5' to "JKLΚΛΜ", '6' to "MNOΝΞΟ",
        '7' to "PQRSΠΡΣ", '8' to "TUVΤΥΦ", '9' to "WXYZΧΨΩ",
    ).flatMap { (d, ls) -> ls.map { it to d } }.toMap()

    /** A name as keys: "Μαρία Π." → "62742 7". Letters off the keys and other marks drop out. */
    fun digits(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        .uppercase().map { ch -> if (ch.isDigit()) ch else KEYS[ch] ?: if (ch.isWhitespace() || ch == '-') ' ' else null }.filterNotNull().joinToString("")

    /** Does what was typed start one of the name's words (or the name run together), or sit inside one of its numbers? */
    fun matches(typed: String, name: String, numbers: List<String>): Boolean {
        if (typed.isEmpty()) return false
        val words = digits(name).split(' ').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(typed) } || words.joinToString("").startsWith(typed)) return true
        val plain = typed.filter(Char::isDigit)
        return plain.length >= 3 && numbers.any { n -> n.filter(Char::isDigit).contains(plain) }
    }
}

/** What an unknown Greek number is: "Mobile", "Athens", "Thessaloniki"… (a small table, offline); "Abroad" for other countries. */
internal object PhoneArea {
    private val AREAS = listOf("210" to "Athens", "2310" to "Thessaloniki", "2610" to "Patras", "2810" to "Heraklion", "2410" to "Larissa",
        "24210" to "Volos", "2510" to "Kavala", "2651" to "Ioannina", "22810" to "Syros", "28210" to "Chania")
    fun of(number: String): String? {
        val n = number.filter { it.isDigit() || it == '+' }
        val national = when {
            n.startsWith("+30") -> n.drop(3)
            n.startsWith("0030") -> n.drop(4)
            n.startsWith("+") || n.startsWith("00") -> return "Abroad"
            else -> n
        }
        if (national.length != 10) return null
        if (national.startsWith("69")) return "Mobile"
        return AREAS.filter { national.startsWith(it.first) }.maxByOrNull { it.first.length }?.second ?: if (national.startsWith("2")) "Landline" else null
    }
}

/**
 * The call history for the computer and any AI (Plan.md § Phone step 5): Android's call log copied, a month a file, as
 * Tetra/Phone/calls-2026-10.jsonl — one line per call, oldest first:
 * {"at":"2026-10-24T15:36:02+03:00","number":"+3069…","dir":"in|out|missed|declined|blocked|voicemail","secs":124,"contact":"<UID>","name":"Μαρία","note":"…"}.
 * Written by the phone only, while Tetra is the phone app (Google Play allows the call log to the phone app); the call log
 * stays Android's. A month whose calls were all deleted becomes an empty file (nothing is deleted, so nothing goes to Trash).
 */
internal object PhoneHistory {
    const val FOLDER = "Phone"
    private val MONTH = Regex("""calls-(\d{4}-\d{2})\.jsonl""")

    /** One call as a line, its fields always in this order (written by hand: a JSONObject's order is not promised). */
    fun line(call: LoggedCall, contact: ContactInfo?, note: String?, zone: ZoneId): String {
        val q = org.json.JSONObject::quote
        val dir = when (call.type) {
            CallLog.Calls.OUTGOING_TYPE -> "out"; CallLog.Calls.MISSED_TYPE -> "missed"; CallLog.Calls.REJECTED_TYPE -> "declined"
            CallLog.Calls.BLOCKED_TYPE -> "blocked"; CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"; else -> "in"
        }
        val fields = mutableListOf("at" to q(Instant.ofEpochMilli(call.at).atZone(zone).toOffsetDateTime().withNano(0).toString()), "number" to q(call.number),
            "dir" to q(dir), "secs" to "${call.seconds}")
        contact?.let { c -> c.uid.ifBlank { null }?.let { fields += "contact" to q(it) }; fields += "name" to q(c.name) }
        note?.let { fields += "note" to q(it) }
        return fields.joinToString(",", "{", "}") { (k, v) -> "\"$k\":$v" }
    }

    /** The month files' text from the calls: "2026-10" → its lines. */
    fun months(calls: List<LoggedCall>, who: (String) -> ContactInfo?, note: (LoggedCall) -> String?, zone: ZoneId): Map<String, String> =
        calls.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).let { t -> "%04d-%02d".format(t.year, t.monthValue) } }
            .mapValues { (_, l) -> l.sortedBy { it.at }.joinToString("") { line(it, who(it.number), note(it), zone) + "\n" } }

    fun write(c: Context) {
        if (!PhonePrefs.isDialer(c)) return
        runCatching {
            val zone = ZoneId.systemDefault()
            val calls = PhoneLog.read(c, limit = 50_000)
            val contacts = ContactsPreload.all?.map { it.info } ?: runCatching { ContactsFiles.all(contactsRoot()).map { it.info } }.getOrDefault(emptyList())
            val byKey = contacts.flatMap { p -> p.phones.map { ContactsWays.key(it.value) to p } }.filter { it.first.length >= 5 }.toMap()
            val notes = PhoneNotes.all()
            val want = months(calls, { byKey[ContactsWays.key(it)] }, { PhoneNotes.of(c, it.number, it.at, notes) }, zone)
            val dir = phoneRoot().apply { mkdirs() }
            val old = dir.listFiles().orEmpty().mapNotNull { f -> MONTH.matchEntire(f.name)?.groupValues?.get(1) }
            var changed = false
            (want.keys + old).forEach { m ->
                val f = java.io.File(dir, "calls-$m.jsonl")
                val text = want[m].orEmpty()
                if (!f.exists() || f.readText() != text) { f.writeText(text); changed = true }
            }
            if (changed) { WriterLog.i("Phone: the call history for the computer written (${calls.size} calls)"); ScopedSync.soon(c, "phone") }
        }.onFailure { WriterLog.w("Phone: the call history was not written (${it.message})") }
    }
}

/**
 * The keypad's codes, as every phone app answers them: *#*#4636#*#* and the like go to whatever app listens for them
 * (Android's sendDialerSpecialCode, the phone app's alone), *#06# shows the phone's IMEI or, where Android keeps it from
 * an ordinary app, opens About phone. Others (*100# for the balance) are calls, placed as any number.
 */
internal object PhoneCodes {
    private val SECRET = Regex("""\*#\*#(\d+)#\*#\*""")
    fun secret(typed: String) = SECRET.matchEntire(typed)?.groupValues?.get(1)

    /** A code just completed on the keypad: done here, and true (the number then clears). */
    fun typed(c: Context, typed: String): Boolean? {
        secret(typed)?.let { code ->
            runCatching { c.getSystemService(android.telephony.TelephonyManager::class.java).sendDialerSpecialCode(code) }
                .onFailure { WriterLog.w("Phone: code $code (${it.message})") }
            return true
        }
        if (typed == "*#06#") {
            @Suppress("MissingPermission")
            val ids = runCatching { val tm = c.getSystemService(android.telephony.TelephonyManager::class.java); (0 until tm.activeModemCount).mapNotNull { tm.getImei(it) } }.getOrNull()
            if (ids.isNullOrEmpty()) runCatching { c.startActivity(android.content.Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            else android.app.AlertDialog.Builder(c).setTitle("IMEI").setMessage(ids.joinToString("\n")).setPositiveButton("OK", null).show()
            return true
        }
        return null
    }
}
