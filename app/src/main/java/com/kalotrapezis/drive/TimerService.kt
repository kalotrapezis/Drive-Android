package com.kalotrapezis.drive

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The Timer's changes, from the screen, Home or a notification's button: the file written once, then sent at once. */
internal object TimerControl {
    fun dir() = TimerFiles.root(TetraFolder.root(Environment.getExternalStorageDirectory()))
    private fun device() = Build.MODEL ?: "Android"

    /** The clock opens on the length last started here (five minutes the first time). */
    fun last(c: Context) = c.getSharedPreferences("timer", Context.MODE_PRIVATE).getLong("last", 300L)
    private fun used(c: Context, seconds: Long) = c.getSharedPreferences("timer", Context.MODE_PRIVATE).edit().putLong("last", seconds).apply()

    fun start(c: Context, seconds: Long): TimerRecord {
        used(c, seconds)
        return TimerFiles.start(dir(), seconds, device()).also { sent(c) }
    }

    /** Pause, resume, stop, OK, start again, pin, unpin or delete ([TimerService] actions) on the timer [id], as its file is now. */
    fun change(c: Context, id: String, action: String) {
        val r = TimerFiles.read(dir()).firstOrNull { it.id == id } ?: return
        val now = Instant.now()
        val next = when (action) {
            TimerService.PAUSE -> r.pause(now, device())
            TimerService.RESUME -> r.resume(now, device())
            TimerService.STOP, TimerService.OK -> if (r.state == TimerRecord.STOPPED) r else r.stop(device())
            TimerService.RESTART -> r.restart(now, device()).also { used(c, r.seconds) }
            TimerService.PIN -> if (r.pinned) r else r.pin(true, device())
            TimerService.UNPIN -> if (!r.pinned) r else r.pin(false, device())
            TimerService.TRASH -> r.trash(device())
            else -> r
        }
        if (next === r) return
        TimerFiles.write(dir(), next)
        sent(c)
    }

    private fun sent(c: Context) {
        ScopedSync.soon(c, "timer", 0, orWhole = true) // to the other devices now; they stop, pause or start with it
        TimerService.check(c)
    }
}

/**
 * Runs while a timer is running, paused or ringing (Plan.md § Timer): its ongoing notification (the Hyper Island on a
 * Xiaomi; a countdown chronometer elsewhere) with Pause/Resume and Stop, the alarm and its OK at the end, and a look
 * at the other devices every 30 s (the user: "sync check every 30 sec"), besides the push each change makes. The end
 * itself is an alarm clock, so it rings on time with the screen off; the files say what is true, read each second.
 */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var shown = emptyMap<String, String>() // id → what its notification shows, to post only on a change
    private var player: MediaPlayer? = null
    private var wake: PowerManager.WakeLock? = null
    private var lastLook = 0L
    private var foreground = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        channels(this)
        scope.launch { while (isActive) { runCatching { tick() }.onFailure { WriterLog.e("Timer: tick failed", it) }; delay(1000) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android wants the notification at once: the first timer's as it is, or a placeholder the tick takes away
        val now = Instant.now()
        val first = runCatching { TimerFiles.active(TimerControl.dir(), now) }.getOrDefault(emptyList()).firstOrNull()
        val n = first?.let { notification(it, now) } ?: NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_timer).setContentTitle("Timer").setSilent(true).build()
        foreground = first?.let { notificationId(it.id) } ?: PLACEHOLDER
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) startForeground(foreground, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(foreground, n)
        }.onFailure { WriterLog.e("Timer: could not run in the foreground", it) }
        val id = intent?.getStringExtra(ID)
        val action = intent?.action
        if (id != null && action != null && action != CHECK && action != RING) scope.launch { runCatching { TimerControl.change(this@TimerService, id, action) }; tick() }
        if (action == RING) lastLook = 0 // ringing: ask the others at once whether it was answered there
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        quiet()
        super.onDestroy()
    }

    @Synchronized private fun tick() {
        val now = Instant.now()
        val active = TimerFiles.active(TimerControl.dir(), now)
        val nm = NotificationManagerCompat.from(this)
        if (active.isEmpty()) {
            quiet()
            alarm(null)
            shown.keys.forEach { nm.cancel(notificationId(it)) }
            shown = emptyMap()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val ringing = active.any { it.ringing(now) }
        if (ringing) ring() else quiet()
        // what each shows; a change (a pause here or there, a new timer, the end) posts it again
        val now2 = active.associate { it.id to "${it.rev}|${it.state}|${it.ringing(now)}" }
        if (now2 != shown) {
            active.forEach { r ->
                if (shown[r.id] == now2[r.id]) return@forEach
                val n = notification(r, now, announce = shown[r.id] == null || r.ringing(now))
                runCatching { nm.notify(notificationId(r.id), n) }
            }
            (shown.keys - now2.keys).forEach { nm.cancel(notificationId(it)) }
            // the foreground notification is the first timer's; the placeholder goes
            val firstId = notificationId(active.first().id)
            if (firstId != foreground) {
                runCatching { if (Build.VERSION.SDK_INT >= 34) startForeground(firstId, notification(active.first(), now), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(firstId, notification(active.first(), now)) }
                if (foreground == PLACEHOLDER) nm.cancel(PLACEHOLDER)
                foreground = firstId
            }
            shown = now2
            alarm(active.filter { it.state == TimerRecord.RUNNING && !it.ringing(now) }.mapNotNull { it.ends }.minOrNull())
        }
        // the 30 s look at the other devices: Stop or OK there stops it here even if their push was missed
        if (System.currentTimeMillis() - lastLook >= LOOK_MS) {
            lastLook = System.currentTimeMillis()
            scope.launch(Dispatchers.IO) {
                val store = SyncStore(applicationContext)
                if (store.pairing() != null && !SyncService.isRunning) runCatching { SyncClient(applicationContext, store).scopeOnly("timer") }
            }
        }
    }

    private fun notification(r: TimerRecord, now: Instant, announce: Boolean = false): Notification {
        val ringing = r.ringing(now)
        val name = r.label.ifEmpty { "${durationText(r.seconds)} timer" }
        val endsAt = r.ends?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a"))
        val title = when { ringing -> "Time's up"; r.state == TimerRecord.PAUSED -> "Paused · ${clockText(r.leftMs(now))} left"; else -> name }
        val text = when { ringing -> name; r.state == TimerRecord.PAUSED -> name; else -> "Ends at $endsAt" }
        val buttons = when {
            ringing -> listOf(Triple(OK, "OK", action(r.id, OK)))
            r.state == TimerRecord.PAUSED -> listOf(Triple(RESUME, "Resume", action(r.id, RESUME)), Triple(STOP, "Stop", action(r.id, STOP)))
            else -> listOf(Triple(PAUSE, "Pause", action(r.id, PAUSE)), Triple(STOP, "Stop", action(r.id, STOP)))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra(Applet.EXTRA, SCREEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(this, if (ringing) CHANNEL_DONE else CHANNEL)
            .setSmallIcon(R.drawable.ic_timer)
            .setColor(COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setCategory(if (ringing) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(if (ringing) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(!ringing) // the alarm's own sound plays; the notification only pops up
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (r.state == TimerRecord.RUNNING && !ringing && r.ends != null) // a countdown Android draws itself, no update each second
            b.setWhen(r.ends.toEpochMilli()).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        buttons.forEach { (_, label, pi) -> b.addAction(0, label, pi) }
        if (HyperIsland.isSupported(this)) HyperIsland.timer(b, title, text, r.ends?.toEpochMilli()?.takeIf { r.state == TimerRecord.RUNNING && !ringing },
            if (ringing) "Time's up" else clockText(r.leftMs(now)), announce, buttons)
        return b.build()
    }

    private fun action(id: String, what: String) = PendingIntent.getForegroundService(this, (id + what).hashCode(),
        Intent(this, TimerService::class.java).setAction(what).putExtra(ID, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** The next end as an alarm clock (on time in Doze, the screen off); none left, none set. */
    private fun alarm(at: Instant?) {
        val am = getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getForegroundService(this, 1, Intent(this, TimerService::class.java).setAction(RING), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (at == null) return am.cancel(pi)
        val show = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).putExtra(Applet.EXTRA, SCREEN), PendingIntent.FLAG_IMMUTABLE)
        runCatching { am.setAlarmClock(AlarmManager.AlarmClockInfo(at.toEpochMilli(), show), pi) }.onFailure { WriterLog.e("Timer: no alarm clock", it) }
    }

    private fun ring() {
        if (player != null) return
        WriterLog.i("Timer: ringing")
        wake = (getSystemService(PowerManager::class.java))?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Tetra:timer")?.apply { acquire(TimerRecord.UNANSWERED_MS) }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@TimerService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { WriterLog.e("Timer: the alarm sound could not play", it) }.getOrNull()
    }

    private fun quiet() {
        player?.let { runCatching { it.stop(); it.release() } }
        player = null
        wake?.let { if (it.isHeld) it.release() }
        wake = null
    }

    companion object {
        const val PAUSE = "com.kalotrapezis.drive.TIMER_PAUSE"
        const val RESUME = "com.kalotrapezis.drive.TIMER_RESUME"
        const val STOP = "com.kalotrapezis.drive.TIMER_STOP"
        const val OK = "com.kalotrapezis.drive.TIMER_OK"
        const val RESTART = "com.kalotrapezis.drive.TIMER_RESTART"
        const val PIN = "com.kalotrapezis.drive.TIMER_PIN"
        const val UNPIN = "com.kalotrapezis.drive.TIMER_UNPIN"
        const val TRASH = "com.kalotrapezis.drive.TIMER_TRASH"
        private const val RING = "com.kalotrapezis.drive.TIMER_RING"
        private const val CHECK = "com.kalotrapezis.drive.TIMER_CHECK"
        private const val ID = "id"
        /** MainActivity's word for the Timer screen (Applet.EXTRA), as an applet icon's. */
        const val SCREEN = "Timer"
        private const val CHANNEL = "timer"
        private const val CHANNEL_DONE = "timer_done"
        private const val PLACEHOLDER = 7999
        private const val LOOK_MS = 30_000L
        private const val COLOR = 0xFF26A69A.toInt()
        @Volatile var running = false; private set

        private fun notificationId(id: String) = 7000 + (id.hashCode() and 0x3ff)

        fun channels(c: Context) {
            val nm = c.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Timer", NotificationManager.IMPORTANCE_LOW).apply { description = "A timer running" })
            if (nm.getNotificationChannel(CHANNEL_DONE) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Timer done", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A timer that has run out"; setSound(null, null) // the alarm plays its own sound
            })
        }

        /**
         * After anything that may have changed the Timer's files (a change here, a sync): the service runs while there is
         * a timer. From the background Android may refuse to start it; an alarm a moment later is allowed to.
         */
        fun check(c: Context) {
            if (running) return // its next tick reads the files
            val app = c.applicationContext
            if (runCatching { TimerFiles.active(TimerControl.dir()) }.getOrDefault(emptyList()).isEmpty()) return
            val intent = Intent(app, TimerService::class.java).setAction(CHECK)
            runCatching { ContextCompat.startForegroundService(app, intent) }.onFailure {
                WriterLog.w("Timer: started by an alarm (${it.javaClass.simpleName})")
                val am = app.getSystemService(AlarmManager::class.java) ?: return
                val pi = PendingIntent.getForegroundService(app, 3, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 500, pi) }
            }
        }
    }
}
