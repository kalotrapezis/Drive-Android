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
            while (c.moveToNext()) if (c.isNull(2)) c.getString(1)?.takeIf { it.isNotBlank() }?.let { groups[c.getLong(0)] = it }
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
}
