package com.kalotrapezis.drive

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager

/**
 * Screening before a call rings (Plan.md § Phone, step 4): Telecom asks the phone app's screening service about every
 * incoming call. Settings › Phone: numbers not in Contacts ring silently (still in Recents as missed), hidden numbers are
 * declined. Blocked numbers are Android's own list, which Telecom applies before this.
 */
class PhoneScreening : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val ok = CallResponse.Builder().build()
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return respondToCall(details, ok)
        val number = details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() && details.handlePresentation == TelecomManager.PRESENTATION_ALLOWED }
        val response = when {
            number == null && PhonePrefs.declineHidden(this) -> CallResponse.Builder().setDisallowCall(true).setRejectCall(true).build()
            number != null && PhonePrefs.silenceUnknown(this) && PhoneCalls.contactFor(number) == null -> CallResponse.Builder().setSilenceCall(true).build()
            else -> ok
        }
        if (response !== ok) WriterLog.i("Phone: a call ${if (number == null) "from a hidden number declined" else "from a number not in Contacts silenced"}")
        respondToCall(details, response)
    }
}

/** Android's blocked numbers (BlockedNumberContract): only the phone app, the SMS app and the carrier may read and change them. */
internal object PhoneBlocked {
    fun can(c: Context) = runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(c) }.getOrDefault(false)
    fun all(c: Context): List<Pair<Long, String>> = runCatching {
        c.contentResolver.query(BlockedNumberContract.BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ID, BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
            null, null, null)?.use { q -> buildList { while (q.moveToNext()) add(q.getLong(0) to q.getString(1).orEmpty()) } }.orEmpty()
    }.getOrDefault(emptyList())
    fun isBlocked(c: Context, number: String) = runCatching { BlockedNumberContract.isBlocked(c, number) }.getOrDefault(false)
    fun block(c: Context, number: String) = runCatching {
        c.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) })
    }.onFailure { WriterLog.w("Phone: not blocked (${it.message})") }
    fun unblock(c: Context, number: String) = runCatching { BlockedNumberContract.unblock(c, number) }.onFailure { WriterLog.w("Phone: not unblocked (${it.message})") }
}

/**
 * A person's ringtone: Android's own CUSTOM_RINGTONE on the phone's contact for that number (Tetra's copy in the phone's
 * contacts, or Google's), which Telecom plays whichever app is the phone app.
 */
internal object PhoneRingtone {
    private fun contactId(c: Context, number: String): Long? = runCatching {
        c.contentResolver.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)), arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?.use { q -> if (q.moveToFirst()) q.getLong(0) else null }
    }.getOrNull()
    fun known(c: Context, number: String) = contactId(c, number) != null
    fun get(c: Context, number: String): Uri? = contactId(c, number)?.let { id ->
        runCatching {
            c.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts.CUSTOM_RINGTONE), "${ContactsContract.Contacts._ID} = ?", arrayOf("$id"), null)
                ?.use { q -> if (q.moveToFirst()) q.getString(0)?.let(Uri::parse) else null }
        }.getOrNull()
    }
    fun set(c: Context, number: String, tone: Uri?) = contactId(c, number)?.let { id ->
        runCatching {
            c.contentResolver.update(ContactsContract.Contacts.CONTENT_URI, ContentValues().apply { put(ContactsContract.Contacts.CUSTOM_RINGTONE, tone?.toString()) },
                "${ContactsContract.Contacts._ID} = ?", arrayOf("$id"))
        }.onFailure { WriterLog.w("Phone: the ringtone was not set (${it.message})") }
    }
}

/** Declining with a message: Telecom sends it as an SMS (Call.reject with a text), as Android's own phone app does. */
internal val QUICK_REPLIES = listOf("Can't talk now. I'll call you back.", "I'll call you right back.", "I'm in a meeting, I'll call you later.", "On my way.")
