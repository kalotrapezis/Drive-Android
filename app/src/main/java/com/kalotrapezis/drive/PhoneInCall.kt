package com.kalotrapezis.drive

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.Person

/**
 * Tetra as the phone's Phone app (Plan.md § Phone): while Tetra holds ROLE_DIALER, Telecom hands every call, ringing or
 * dialled, to this service, and Tetra's own screen (PhoneCallActivity) shows it. Telecom still plays the ringtone.
 */
class PhoneInCallService : InCallService() {
    override fun onCreate() { super.onCreate(); PhoneCalls.service = this }
    override fun onDestroy() { PhoneCalls.service = null; PhoneCalls.proximity(this, false); PhoneGestures.watch(this, null); super.onDestroy() }
    override fun onCallAdded(call: Call) = PhoneCalls.added(this, call)
    override fun onCallRemoved(call: Call) = PhoneCalls.removed(this, call)
    @Deprecated("CallAudioState: still what Telecom sends on every Android Tetra runs on")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        val told = PhoneCalls.audio?.isMuted != audioState?.isMuted || PhoneCalls.audio?.route != audioState?.route // the computer's bubble shows both
        PhoneCalls.audio = audioState; PhoneCalls.changed(this)
        if (told) PhoneCalls.top()?.let { PhoneCalls.report(this, it) }
    }
}

/** The calls Telecom gave Tetra, for the call screen (read in Compose: [tick] changes on every change). */
@Suppress("DEPRECATION") // Call.state and CallAudioState: the API 30 forms, still sent on 36
internal object PhoneCalls {
    var service: PhoneInCallService? = null
    var calls by mutableStateOf<List<Call>>(emptyList()); private set
    var audio by mutableStateOf<CallAudioState?>(null)
    var tick by mutableIntStateOf(0); private set
    /** The call screen in front: the ringing notification then stays quiet (no heads-up over the screen itself). */
    var screenShown = false
    /** Outgoing calls already answered: the buzz once, not again after Hold → Resume. */
    private val answered = mutableSetOf<Call>()
    private val names = HashMap<String, ContactInfo?>()
    private const val RINGING = "phone_ringing"
    private const val ONGOING = "phone_call"
    private const val NOTE = 4147
    const val ANSWER = "com.kalotrapezis.drive.phone.ANSWER"

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            val s = service ?: return
            val outgoing = call.details.callDirection == Call.Details.DIRECTION_OUTGOING
            if (state == Call.STATE_ACTIVE && outgoing && answered.add(call) && PhonePrefs.buzzAnswer(s)) buzz(s, longArrayOf(0, 60, 80, 60))
            if (state == Call.STATE_DISCONNECTED && call.details.connectTimeMillis > 0 && PhonePrefs.buzzEnd(s)) buzz(s, longArrayOf(0, 120))
            changed(s)
            report(s, call)
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) { service?.let { changed(it) } }
    }

    fun added(s: PhoneInCallService, call: Call) {
        call.registerCallback(callback)
        calls = calls + call
        WriterLog.i("Phone: a call ${if (call.state == Call.STATE_RINGING) "ringing" else "placed"} (${calls.size} now)")
        report(s, call)
        number(call)?.let { n -> if (n !in names) Thread { val c = contactFor(n); synchronized(names) { names[n] = c }; android.os.Handler(s.mainLooper).post { changed(s) } }.start() }
        changed(s)
        // a ringing call over the lock screen or a dark screen at once; on a phone in use, its heads-up (the full-screen intent)
        val ringing = call.state == Call.STATE_RINGING
        val quiet = s.getSystemService(KeyguardManager::class.java).isKeyguardLocked || !s.getSystemService(PowerManager::class.java).isInteractive
        if (!ringing || quiet) runCatching { s.startActivity(Intent(s, PhoneCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { WriterLog.w("Phone: the call screen did not open (${it.message})") }
    }

    fun removed(s: PhoneInCallService, call: Call) {
        call.unregisterCallback(callback)
        calls = calls - call
        answered.remove(call)
        WriterLog.i("Phone: a call ended (${calls.size} left)")
        changed(s)
        report(s, call)
        val app = s.applicationContext // the call log has the call a moment later: then its copy for the computer
        android.os.Handler(s.mainLooper).postDelayed({ Thread { PhoneHistory.write(app) }.start() }, 3000)
    }

    // The computer's call bubble (Plan.md § Phone step 5): every incoming call, and the outgoing ones the computer asked for
    // (the phone's own calls stay its own, as before Tetra was the phone app).
    private val toComputer = mutableSetOf<Call>()
    fun report(c: Context, call: Call) {
        if (call.parent != null || !PhonePrefs.pcCalls(c)) return // a merged call's people: the merged call speaks for them
        val incoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING
        if (call !in toComputer) { if (call.state == Call.STATE_DISCONNECTED || !(incoming || PhoneCall.computerAsked(number(call)))) return; toComputer.add(call) }
        val state = when (call.state) {
            Call.STATE_RINGING -> "ringing"; Call.STATE_ACTIVE -> "active"; Call.STATE_HOLDING -> "held"; Call.STATE_DISCONNECTED -> "ended"
            else -> "calling"
        }
        val body = org.json.JSONObject().put("state", state).put("direction", if (incoming) "in" else "out").put("number", number(call).orEmpty())
            .put("missed", state == "ended" && call.details.disconnectCause?.code == android.telecom.DisconnectCause.MISSED)
            .put("muted", audio?.isMuted == true).put("route", routeName(audio?.route)).put("phoneApp", true).put("at", System.currentTimeMillis())
        if (state == "ended") toComputer.remove(call)
        val app = c.applicationContext
        Thread { runCatching { SyncClient(app, SyncStore(app)).callState(body) }.onFailure { WriterLog.w("Phone: the call's state did not reach the computer (${it.message})") } }.start()
    }

    /** The Bluetooth device the call was on when the computer's bubble moved it to the speaker: back there on "unspeaker". */
    private var beforeSpeaker: android.bluetooth.BluetoothDevice? = null

    /** The computer's bubble pressed: answer, decline, end, hold, unhold, mute, unmute, speaker, unspeaker, dtmf<digit>. Null while Tetra is not the phone app. */
    fun act(what: String): Boolean? {
        val s = service ?: return null
        val ringing = calls.firstOrNull { it.state == Call.STATE_RINGING }
        val call = top() ?: return what == "end" || what == "decline" // the other side hung up first: nothing left to end is done
        when {
            what == "answer" -> answer(ringing ?: return false)
            what == "decline" -> (ringing ?: return false).reject(false, null)
            what == "end" -> hangUp(call)
            what == "hold" -> call.hold()
            what == "unhold" -> call.unhold()
            what == "mute" || what == "unmute" -> s.setMuted(what == "mute")
            // the user, 27 Oct: the call's sound from the computer (the phone on its Bluetooth) to the phone's loudspeaker, and back
            what == "speaker" -> { beforeSpeaker = audio?.activeBluetoothDevice; s.setAudioRoute(CallAudioState.ROUTE_SPEAKER) }
            what == "unspeaker" -> {
                val back = beforeSpeaker?.takeIf { d -> audio?.supportedBluetoothDevices?.contains(d) == true }
                if (back != null) s.requestBluetoothAudio(back) else s.setAudioRoute(awayFromSpeaker(audio?.supportedRouteMask ?: 0))
            }
            what.startsWith("dtmf") && what.length == 5 && what[4] in "0123456789*#" -> {
                call.playDtmfTone(what[4]); android.os.Handler(s.mainLooper).postDelayed({ call.stopDtmfTone() }, 180)
            }
            else -> return false
        }
        return true
    }

    /** The route the computer's bubble shows: speaker, bluetooth, wired or earpiece. */
    fun routeName(route: Int?) = when (route) {
        CallAudioState.ROUTE_SPEAKER -> "speaker"; CallAudioState.ROUTE_BLUETOOTH -> "bluetooth"; CallAudioState.ROUTE_WIRED_HEADSET -> "wired"; else -> "earpiece"
    }
    /** Off the speaker: Bluetooth (the computer) when there is one, else the headset or the earpiece. */
    fun awayFromSpeaker(mask: Int) = if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) CallAudioState.ROUTE_BLUETOOTH else CallAudioState.ROUTE_WIRED_OR_EARPIECE

    fun changed(c: Context) { tick++; notify(c); proximity(c, wantsProximity()); PhoneGestures.watch(c, calls.firstOrNull { it.state == Call.STATE_RINGING }) }

    /** The call the screen shows: the one ringing, else the one talking, else the latest. */
    fun top(): Call? = calls.filter { it.parent == null }.let { c -> c.firstOrNull { it.state == Call.STATE_RINGING } ?: c.lastOrNull { it.state == Call.STATE_ACTIVE } ?: c.lastOrNull() }

    fun number(call: Call): String? = call.details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() && call.details.handlePresentation == TelecomManager.PRESENTATION_ALLOWED }
    /** The people on a call: a merged call's each, else the call itself. */
    fun parts(call: Call): List<Call> = call.children.ifEmpty { listOf(call) }
    fun contact(call: Call): ContactInfo? = number(call)?.let { synchronized(names) { names[it] } }
    fun title(c: Context, call: Call): String = if (call.details.hasProperty(Call.Details.PROPERTY_CONFERENCE))
        call.children.joinToString(", ") { title(c, it) }.ifEmpty { c.getString(R.string.phone_conference) } else contact(call)?.name ?: number(call) ?: c.getString(R.string.phone_hidden_number)

    /** The Tetra contact with this number (the last nine digits, as Contacts matches them), read from the files. */
    fun contactFor(number: String): ContactInfo? {
        val k = ContactsWays.key(number)
        if (k.length < 5) return null
        val all = ContactsPreload.all ?: runCatching { ContactsFiles.all(contactsRoot()) }.getOrNull()?.also { ContactsPreload.all = it } ?: return null
        return all.map { it.info }.firstOrNull { i -> i.phones.any { ContactsWays.key(it.value) == k } }
    }

    fun buzz(c: Context, pattern: LongArray) {
        val v = c.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        val effect = VibrationEffect.createWaveform(pattern, -1)
        runCatching { if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_COMMUNICATION_REQUEST)) else v.vibrate(effect) }
            .onFailure { WriterLog.w("Phone: no vibration (${it.message})") }
    }

    // The screen off at the ear: a call talking or dialling on the earpiece
    private var near: PowerManager.WakeLock? = null
    private fun wantsProximity() = audio?.route == CallAudioState.ROUTE_EARPIECE &&
        calls.any { it.state == Call.STATE_ACTIVE || it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
    fun proximity(c: Context, on: Boolean) {
        if (on && near == null) near = c.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Tetra:call")
            .takeIf { c.getSystemService(PowerManager::class.java).isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }?.apply { acquire(4 * 60 * 60_000L) }
        if (!on) near?.let { runCatching { it.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }; near = null }
    }

    /** The call's notification: Answer · Decline while ringing, Hang up after (Android's CallStyle). */
    private fun notify(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val call = top()
        if (call == null || call.state == Call.STATE_DISCONNECTED) { nm.cancel(NOTE); return }
        // made again each time: an existing channel keeps its sound and takes only the name, in the language now
        nm.createNotificationChannel(NotificationChannel(RINGING, c.getString(R.string.phone_channel_incoming), NotificationManager.IMPORTANCE_HIGH)
            .apply { setSound(null, null); enableVibration(false) }) // Telecom rings and vibrates
        nm.createNotificationChannel(NotificationChannel(ONGOING, c.getString(R.string.phone_channel_ongoing), NotificationManager.IMPORTANCE_LOW))
        val ringing = call.state == Call.STATE_RINGING
        val screen = Intent(c, PhoneCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val open = PendingIntent.getActivity(c, 0, screen, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val answer = PendingIntent.getActivity(c, 1, Intent(screen).setAction(ANSWER), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun act(what: String, code: Int) = PendingIntent.getBroadcast(c, code, Intent(c, PhoneCallReceiver::class.java).setAction(what), PendingIntent.FLAG_IMMUTABLE)
        val person = Person.Builder().setName(title(c, call)).setImportant(true).build()
        val b = NotificationCompat.Builder(c, if (ringing && !screenShown) RINGING else ONGOING).setSmallIcon(R.drawable.ic_call)
            .setStyle(if (ringing) NotificationCompat.CallStyle.forIncomingCall(person, act("decline", 2), answer) else NotificationCompat.CallStyle.forOngoingCall(person, act("end", 3)))
            .setCategory(NotificationCompat.CATEGORY_CALL).setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(open).setFullScreenIntent(open, true)
        if (call.state == Call.STATE_ACTIVE && call.details.connectTimeMillis > 0) b.setUsesChronometer(true).setWhen(call.details.connectTimeMillis)
        else b.setContentText(stateText(c, call, 0))
        runCatching { nm.notify(NOTE, b.build()) }.onFailure { WriterLog.w("Phone: the call's notification (${it.message})") }
    }

    fun stateText(c: Context, call: Call, now: Long): String = when (call.state) {
        Call.STATE_RINGING -> c.getString(R.string.phone_incoming)
        Call.STATE_SELECT_PHONE_ACCOUNT -> c.getString(R.string.phone_choose_sim)
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW -> c.getString(R.string.phone_calling)
        Call.STATE_ACTIVE -> call.details.connectTimeMillis.takeIf { it > 0 && now > 0 }?.let { elapsed(now - it) } ?: c.getString(R.string.phone_connected)
        Call.STATE_HOLDING -> c.getString(R.string.phone_on_hold)
        else -> c.getString(R.string.phone_call_ended)
    }

    fun elapsed(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun hangUp(call: Call) = if (call.state == Call.STATE_RINGING) call.reject(false, null) else call.disconnect()
}

/** Notes typed during calls: Tetra/Phone/notes.jsonl, one line per note ({"at", "number", "note"}), the last line for a call wins. */
internal object PhoneNotes {
    private fun file() = java.io.File(phoneRoot(), "notes.jsonl")
    private fun near(at: Long, other: Long) = kotlin.math.abs(at - other) < 15_000
    fun all(): List<Triple<Long, String, String>> = runCatching {
        file().readLines().mapNotNull { l -> runCatching { org.json.JSONObject(l).let { Triple(it.getLong("at"), it.getString("number"), it.getString("note")) } }.getOrNull() }
    }.getOrDefault(emptyList())
    /** The note for a call: the same number, started within 15 s (the call log's time and Telecom's differ a little). */
    fun of(c: Context, number: String, at: Long, notes: List<Triple<Long, String, String>> = all()) =
        notes.lastOrNull { near(it.first, at) && PhoneLog.same(it.second, number) }?.third?.ifBlank { null }
    fun save(c: Context, number: String, at: Long, note: String) = runCatching {
        file().apply { parentFile?.mkdirs() }.appendText(org.json.JSONObject().put("at", at).put("number", number).put("note", note).toString() + "\n")
        c.applicationContext.let { app -> Thread { PhoneHistory.write(app) }.start() } // the note into the computer's copy too
    }.onFailure { WriterLog.w("Phone: the note was not saved (${it.message})") }
}

/** Decline and Hang up from the call's notification. */
class PhoneCallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { PhoneCalls.top()?.let { PhoneCalls.hangUp(it) } }
}

/** Settings › Phone. */
internal object PhonePrefs {
    private fun p(c: Context) = c.getSharedPreferences("phone", Context.MODE_PRIVATE)
    fun buzzAnswer(c: Context) = p(c).getBoolean("buzz_answer", true)
    fun buzzEnd(c: Context) = p(c).getBoolean("buzz_end", false)
    fun setBuzzAnswer(c: Context, on: Boolean) = p(c).edit().putBoolean("buzz_answer", on).apply()
    fun setBuzzEnd(c: Context, on: Boolean) = p(c).edit().putBoolean("buzz_end", on).apply()
    /** Calls with the computer (Settings › Phone › With the computer): off, the computer hears of no call and places none. */
    fun pcCalls(c: Context) = p(c).getBoolean("pc_calls", true)
    fun setPcCalls(c: Context, on: Boolean) = p(c).edit().putBoolean("pc_calls", on).apply()
    fun silenceUnknown(c: Context) = p(c).getBoolean("silence_unknown", false)
    fun declineHidden(c: Context) = p(c).getBoolean("decline_hidden", false)
    fun setSilenceUnknown(c: Context, on: Boolean) = p(c).edit().putBoolean("silence_unknown", on).apply()
    fun setDeclineHidden(c: Context, on: Boolean) = p(c).edit().putBoolean("decline_hidden", on).apply()
    fun blockedNotify(c: Context) = p(c).getBoolean("blocked_notify", false)
    fun setBlockedNotify(c: Context, on: Boolean) = p(c).edit().putBoolean("blocked_notify", on).apply()
    fun raiseAnswer(c: Context) = p(c).getBoolean("raise_answer", false)
    fun flipSilence(c: Context) = p(c).getBoolean("flip_silence", false)
    fun setRaiseAnswer(c: Context, on: Boolean) = p(c).edit().putBoolean("raise_answer", on).apply()
    fun setFlipSilence(c: Context, on: Boolean) = p(c).edit().putBoolean("flip_silence", on).apply()
    fun isDialer(c: Context) = c.getSystemService(android.app.role.RoleManager::class.java).isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)
}

/**
 * While a call rings (Settings › Phone, both off at first): raised to the ear it is answered, turned face down it goes
 * quiet. The ear: the proximity sensor near while the phone stands up, after it was seen uncovered for half a second (a
 * phone in a pocket stays covered, so it never answers there). Face down: gravity on the screen's side for 0.4 s after the
 * phone was seen face up (already face down when the call came, it keeps ringing).
 */
internal object PhoneGestures : android.hardware.SensorEventListener {
    private var sensors: android.hardware.SensorManager? = null
    private var call: Call? = null
    private var app: Context? = null
    private var raise = false; private var flip = false
    private var near = false; private var standing = false; private var farSince = 0L; private var uncovered = false; private var faceUp = false; private var downSince = 0L

    fun watch(c: Context, ringing: Call?) {
        if (ringing === call) return
        sensors?.unregisterListener(this); sensors = null; call = null
        if (ringing == null) return
        raise = PhonePrefs.raiseAnswer(c); flip = PhonePrefs.flipSilence(c)
        if (!raise && !flip) return
        val m = c.getSystemService(android.hardware.SensorManager::class.java) ?: return
        call = ringing; app = c.applicationContext; sensors = m
        near = false; standing = false; farSince = 0L; uncovered = false; faceUp = false; downSince = 0L
        m.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)?.let { m.registerListener(this, it, android.hardware.SensorManager.SENSOR_DELAY_UI) }
        if (raise) m.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY)?.let { m.registerListener(this, it, android.hardware.SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    override fun onSensorChanged(e: android.hardware.SensorEvent) {
        val ringing = call ?: return
        val c = app ?: return
        if (e.sensor.type == android.hardware.Sensor.TYPE_PROXIMITY) {
            near = e.values[0] < minOf(e.sensor.maximumRange, 5f) // most report only 0 or their range
            farSince = if (near) 0L else farSince.takeIf { it > 0 } ?: e.timestamp
        } else {
            val (x, y, z) = e.values
            standing = z < 6f && (y > 3f || kotlin.math.abs(x) > 3f)
            if (z > 7f) faceUp = true
            if (flip && faceUp && z < -8f) {
                if (downSince == 0L) downSince = e.timestamp
                else if (e.timestamp - downSince > 400_000_000L) {
                    WriterLog.i("Phone: a ringing call silenced, the phone turned face down")
                    runCatching { c.getSystemService(TelecomManager::class.java).silenceRinger() }.onFailure { WriterLog.w("Phone: not silenced (${it.message})") }
                    watch(c, null); return
                }
            } else downSince = 0L
        }
        if (!near && farSince > 0L && e.timestamp - farSince > 500_000_000L) uncovered = true
        if (raise && uncovered && near && standing) {
            WriterLog.i("Phone: a ringing call answered, the phone raised to the ear")
            PhoneCalls.answer(ringing); watch(c, null)
        }
    }
}

/**
 * Missed calls: Android hands their notice to the phone app (TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION, count 0
 * to clear it), so it is Tetra's — "Missed call from Μαρία" with Call back and Message, a tap opening Recents › Missed.
 */
class PhoneMissedReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val nm = c.getSystemService(NotificationManager::class.java)
        val count = i.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        if (count <= 0) { nm.cancel(NOTE); return }
        val number = i.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)?.takeIf { it.isNotBlank() }
        val app = c.applicationContext
        val done = goAsync()
        Thread {
            runCatching {
                val who = number?.let { PhoneCalls.contactFor(it)?.name } ?: number
                nm.createNotificationChannel(NotificationChannel(CHANNEL, app.getString(R.string.phone_channel_missed), NotificationManager.IMPORTANCE_DEFAULT)) // again: its name follows the language
                val open = PendingIntent.getActivity(app, 10, Intent(app, PhoneActivity::class.java).putExtra(PhoneActivity.TAB, "missed").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                @Suppress("DEPRECATION") val clear = i.getParcelableExtra<PendingIntent>("android.telecom.extra.CLEAR_MISSED_CALLS_INTENT") // TelecomManager.EXTRA_CLEAR_MISSED_CALLS_INTENT, a system API name
                val b = NotificationCompat.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_call_missed).setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
                    .setContentTitle(app.resources.getQuantityString(R.plurals.phone_n_missed, count, count)).setContentText(if (count == 1) who ?: app.getString(R.string.phone_hidden_number) else who?.let { app.getString(R.string.phone_last_from, it) })
                    .setContentIntent(open).setAutoCancel(true).setDeleteIntent(clear)
                if (count == 1 && number != null) {
                    b.addAction(0, app.getString(R.string.phone_call_back), PendingIntent.getActivity(app, 11, Intent(Intent.ACTION_CALL, android.net.Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    b.addAction(0, app.getString(R.string.contacts_message), PendingIntent.getActivity(app, 12, Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${android.net.Uri.encode(number)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                }
                nm.notify(NOTE, b.build())
            }.onFailure { WriterLog.w("Phone: the missed-call notice (${it.message})") }
            done.finish()
        }.start()
    }
    private companion object { const val CHANNEL = "phone_missed"; const val NOTE = 4150 }
}
