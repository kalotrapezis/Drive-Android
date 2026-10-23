package com.kalotrapezis.drive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.provider.ContactsContract
import java.io.ByteArrayOutputStream

/**
 * Contacts and Android (Plan.md § Contacts › Outside Tetra): the phone's own contacts read in (the Google account's ones
 * already on the phone, READ_CONTACTS), and photos made square for a card.
 */
internal object ContactsSystem {
    /**
     * The phone's contacts as cards: Android writes each as a vCard itself (its own exporter), then Tetra adds what that
     * leaves out — the star, the labels (groups) and the full-size photo.
     */
    fun phoneCards(context: Context, progress: (Int, Int) -> Unit = { _, _ -> }): List<List<ICalProp>> {
        val cr = context.contentResolver
        val groups = HashMap<Long, String>()
        cr.query(ContactsContract.Groups.CONTENT_URI, arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE, ContactsContract.Groups.SYSTEM_ID), null, null, null)?.use { c ->
            // Android's own groups ("My Contacts", "Starred in Android", "Family" made by the system) are not labels a person made
            while (c.moveToNext()) if (c.isNull(2)) c.getString(1)?.takeIf { it.isNotBlank() && !it.equals(ContactInfo.STARRED_GROUP, true) }?.let { groups[c.getLong(0)] = it }
        }
        val labels = HashMap<Long, MutableList<String>>()
        cr.query(ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID),
            "${ContactsContract.Data.MIMETYPE} = ?", arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE), null)?.use { c ->
            while (c.moveToNext()) groups[c.getLong(1)]?.let { labels.getOrPut(c.getLong(0)) { mutableListOf() }.add(it) }
        }
        val people = mutableListOf<Triple<Long, String, Boolean>>()
        cr.query(ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.STARRED), null, null, null)?.use { c ->
            while (c.moveToNext()) c.getString(1)?.let { people.add(Triple(c.getLong(0), it, c.getInt(2) != 0)) }
        }
        return people.mapIndexedNotNull { n, (id, lookup, starred) ->
            progress(n, people.size)
            runCatching {
                val text = cr.openInputStream(android.net.Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookup))!!.use { String(it.readBytes()) }
                val lines = VCard.cards(text).firstOrNull() ?: return@runCatching null
                val info = VCard.info(lines)
                val full = ContactsContract.Contacts.openContactPhotoInputStream(cr, android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id), true)
                    ?.use { it.readBytes() }?.let { square(it) }
                val cats = (info.categories + labels[id].orEmpty() + listOfNotNull(ContactInfo.STARRED.takeIf { starred })).distinct()
                VCard.apply(lines, info, info.copy(categories = cats, photo = full ?: info.photo))
            }.onFailure { WriterLog.e("Contacts: the phone's contact $id", it) }.getOrNull()
        }
    }

    /**
     * A photo as a contact's: square, [size] px, a JPEG in base64. [box] (in the picture's pixels) is the part to keep — a
     * face from Photos, made larger by [room] so the head and some round it fit; without one, the middle square.
     */
    fun square(bytes: ByteArray, box: RectF? = null, room: Float = 1.8f, size: Int = 512): String? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { square(it, box, room, size) }

    fun square(bmp: Bitmap, box: RectF? = null, room: Float = 1.8f, size: Int = 512): String {
        val w = bmp.width; val h = bmp.height
        val side: Int; val cx: Float; val cy: Float
        if (box != null) {
            side = (maxOf(box.width(), box.height()) * room).toInt().coerceIn(1, minOf(w, h)); cx = box.centerX(); cy = box.centerY()
        } else { side = minOf(w, h); cx = w / 2f; cy = h / 2f }
        val left = (cx - side / 2f).toInt().coerceIn(0, w - side)
        val top = (cy - side / 2f).toInt().coerceIn(0, h - side)
        val out = Bitmap.createScaledBitmap(Bitmap.createBitmap(bmp, left, top, side, side), minOf(size, side), minOf(size, side), true)
        return ByteArrayOutputStream().use { b -> out.compress(Bitmap.CompressFormat.JPEG, 85, b); java.util.Base64.getEncoder().encodeToString(b.toByteArray()) }
    }

    /** The other apps' rows for a contact's numbers (Plan.md § Contacts › Outside Tetra), as the phone's own Contacts shows them. */
    fun appWays(context: Context, phones: List<String>): List<AppWay> {
        if (phones.isEmpty() || context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return emptyList()
        val rows = mutableListOf<AppRow>()
        val mimes = ContactsWays.WAYS.keys.map { "vnd.android.cursor.item/$it" }
        context.contentResolver.query(ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2),
            "${ContactsContract.Data.MIMETYPE} IN (${mimes.joinToString { "?" }})", mimes.toTypedArray(), null)?.use { c ->
            while (c.moveToNext()) rows.add(AppRow(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: ""))
        }
        return ContactsWays.ways(rows, phones)
    }

    /** The calls to and from a contact's numbers, newest first (READ_CALL_LOG; none without it). */
    fun calls(context: Context, phones: List<String>, limit: Int = 200): List<CallEntry> {
        if (phones.isEmpty() || context.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) != android.content.pm.PackageManager.PERMISSION_GRANTED) return emptyList()
        val keys = phones.map { ContactsWays.key(it) }.filter { it.length >= 5 }.toSet()
        val out = mutableListOf<CallEntry>()
        context.contentResolver.query(android.provider.CallLog.Calls.CONTENT_URI, arrayOf(android.provider.CallLog.Calls.NUMBER, android.provider.CallLog.Calls.DATE,
            android.provider.CallLog.Calls.DURATION, android.provider.CallLog.Calls.TYPE), null, null, "${android.provider.CallLog.Calls.DATE} DESC")?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val n = c.getString(0) ?: continue
                if (ContactsWays.key(n) in keys) out.add(CallEntry(n, c.getLong(1), c.getLong(2), c.getInt(3)))
            }
        }
        return out
    }
}

/** One row another app put in the phone's contacts: its id, MIME type, number (or WhatsApp's jid), app name. */
internal data class AppRow(val id: Long, val mime: String, val data1: String, val app: String)
internal enum class Way { Message, Call, Video }
/** A way to reach a number through another app, opened as the phone's Contacts opens it (ACTION_VIEW on the row). */
internal data class AppWay(val app: String, val way: Way, val number: String, val id: Long, val mime: String)
internal data class CallEntry(val number: String, val at: Long, val seconds: Long, val type: Int)

internal object ContactsWays {
    /** The rows Tetra offers, by MIME type (as read on the user's phone, 23 Oct; Viber Out, a paid call, left out). */
    val WAYS = mapOf(
        "vnd.com.whatsapp.profile" to Way.Message, "vnd.com.whatsapp.voip.call" to Way.Call, "vnd.com.whatsapp.video.call" to Way.Video,
        "vnd.com.whatsapp.w4b.profile" to Way.Message, "vnd.com.whatsapp.w4b.voip.call" to Way.Call, "vnd.com.whatsapp.w4b.video.call" to Way.Video,
        "vnd.com.viber.voip.viber_number_message" to Way.Message, "vnd.com.viber.voip.viber_number_call" to Way.Call,
        "vnd.org.thoughtcrime.securesms.contact" to Way.Message, "vnd.org.thoughtcrime.securesms.call" to Way.Call,
        "vnd.org.telegram.messenger.android.profile" to Way.Message, "vnd.org.telegram.messenger.android.call" to Way.Call,
        "vnd.org.telegram.messenger.android.call.video" to Way.Video,
    )
    private val NAMES = mapOf("whatsapp.w4b" to "WhatsApp Business", "whatsapp" to "WhatsApp", "viber" to "Viber", "securesms" to "Signal", "telegram" to "Telegram")
    /** The app a row belongs to: opened in it, not in another that claims the MIME type too (Mindomo did, on the user's phone). */
    private val PACKAGES = mapOf("whatsapp.w4b" to "com.whatsapp.w4b", "whatsapp" to "com.whatsapp", "viber" to "com.viber.voip", "securesms" to "org.thoughtcrime.securesms", "telegram" to "org.telegram.messenger")
    fun packageOf(mime: String) = PACKAGES.entries.firstOrNull { mime.contains(it.key) }?.value

    /** A number's last nine digits: "+30 697 000 0002", "00306970000002" and "6970000002" alike. */
    fun key(n: String) = n.substringBefore('@').filter(Char::isDigit).takeLast(9)

    /** The rows for these numbers, once per app, way and number. */
    fun ways(rows: List<AppRow>, phones: List<String>): List<AppWay> {
        val byKey = phones.associateBy { key(it) }.filterKeys { it.length >= 5 }
        return rows.mapNotNull { r ->
            val mime = r.mime.removePrefix("vnd.android.cursor.item/")
            val way = WAYS[mime] ?: return@mapNotNull null
            val number = byKey[key(r.data1)] ?: return@mapNotNull null
            val app = NAMES.entries.firstOrNull { mime.contains(it.key) }?.value ?: r.app
            AppWay(app, way, number, r.id, r.mime)
        }.distinctBy { Triple(it.app, it.way, key(it.number)) }
    }
}
