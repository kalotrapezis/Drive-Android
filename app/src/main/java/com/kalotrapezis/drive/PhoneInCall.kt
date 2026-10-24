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
    override fun onDestroy() { PhoneCalls.service = null; PhoneCalls.proximity(this, false); super.onDestroy() }
    override fun onCallAdded(call: Call) = PhoneCalls.added(this, call)
    override fun onCallRemoved(call: Call) = PhoneCalls.removed(this, call)
    @Deprecated("CallAudioState: still what Telecom sends on every Android Tetra runs on")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) { PhoneCalls.audio = audioState; PhoneCalls.changed(this) }
}

/** The calls Telecom gave Tetra, for the call screen (read in Compose: [tick] changes on every change). */
@Suppress("DEPRECATION") // Call.state and CallAudioState: the API 30 forms, still sent on 36
internal object PhoneCalls {
    var service: PhoneInCallService? = null
    var calls by mutableStateOf<List<Call>>(emptyList()); private set
    var audio by mutableStateOf<CallAudioState?>(null)
    var tick by mutableIntStateOf(0); private set
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
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) { service?.let { changed(it) } }
    }

    fun added(s: PhoneInCallService, call: Call) {
        call.registerCallback(callback)
        calls = calls + call
        WriterLog.i("Phone: a call ${if (call.state == Call.STATE_RINGING) "ringing" else "placed"} (${calls.size} now)")
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
    }

    fun changed(c: Context) { tick++; notify(c); proximity(c, wantsProximity()) }

    /** The call the screen shows: the one ringing, else the one talking, else the latest. */
    fun top(): Call? = calls.firstOrNull { it.state == Call.STATE_RINGING } ?: calls.lastOrNull { it.state == Call.STATE_ACTIVE } ?: calls.lastOrNull()

    fun number(call: Call): String? = call.details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() && call.details.handlePresentation == TelecomManager.PRESENTATION_ALLOWED }
    fun contact(call: Call): ContactInfo? = number(call)?.let { synchronized(names) { names[it] } }
    fun title(call: Call): String = contact(call)?.name ?: number(call) ?: "Hidden number"

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
        if (nm.getNotificationChannel(RINGING) == null) nm.createNotificationChannel(NotificationChannel(RINGING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH)
            .apply { setSound(null, null); enableVibration(false) }) // Telecom rings and vibrates
        if (nm.getNotificationChannel(ONGOING) == null) nm.createNotificationChannel(NotificationChannel(ONGOING, "Calls in progress", NotificationManager.IMPORTANCE_LOW))
        val ringing = call.state == Call.STATE_RINGING
        val screen = Intent(c, PhoneCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val open = PendingIntent.getActivity(c, 0, screen, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val answer = PendingIntent.getActivity(c, 1, Intent(screen).setAction(ANSWER), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun act(what: String, code: Int) = PendingIntent.getBroadcast(c, code, Intent(c, PhoneCallReceiver::class.java).setAction(what), PendingIntent.FLAG_IMMUTABLE)
        val person = Person.Builder().setName(title(call)).setImportant(true).build()
        val b = NotificationCompat.Builder(c, if (ringing) RINGING else ONGOING).setSmallIcon(R.drawable.ic_call)
            .setStyle(if (ringing) NotificationCompat.CallStyle.forIncomingCall(person, act("decline", 2), answer) else NotificationCompat.CallStyle.forOngoingCall(person, act("end", 3)))
            .setCategory(NotificationCompat.CATEGORY_CALL).setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(open).setFullScreenIntent(open, true)
        if (call.state == Call.STATE_ACTIVE && call.details.connectTimeMillis > 0) b.setUsesChronometer(true).setWhen(call.details.connectTimeMillis)
        else b.setContentText(stateText(call, 0))
        runCatching { nm.notify(NOTE, b.build()) }.onFailure { WriterLog.w("Phone: the call's notification (${it.message})") }
    }

    fun stateText(call: Call, now: Long): String = when (call.state) {
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_SELECT_PHONE_ACCOUNT -> "Choose a SIM"
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW -> "Calling…"
        Call.STATE_ACTIVE -> call.details.connectTimeMillis.takeIf { it > 0 && now > 0 }?.let { elapsed(now - it) } ?: "Connected"
        Call.STATE_HOLDING -> "On hold"
        else -> "Call ended"
    }

    fun elapsed(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun hangUp(call: Call) = if (call.state == Call.STATE_RINGING) call.reject(false, null) else call.disconnect()
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
    fun isDialer(c: Context) = c.getSystemService(android.app.role.RoleManager::class.java).isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)
}
