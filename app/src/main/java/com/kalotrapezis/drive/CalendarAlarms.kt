package com.kalotrapezis.drive

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The Calendar's reminders (Plan.md § Calendar › Notifications). One exact alarm, for the next reminder only. Whenever
 * it rings, Tetra opens, an event is saved, a sync ends, the phone starts or its clock changes, [CalendarAlarms.plan]
 * reads the files again, shows every reminder due since it last looked (one missed while the phone was off: up to a day
 * late, said so) and sets the alarm for the next. Never a list kept aside that could go stale against the files.
 */

/** A reminder due: when, for which time of which event; [key] is the same each time it is worked out. */
internal data class DueReminder(val at: Instant, val occ: Occurrence, val key: String)

internal object CalendarAlarms {
    private const val CHANNEL = "calendar_reminders"
    private const val ACTION_RING = "com.kalotrapezis.drive.CALENDAR_RING"
    private const val ACTION_SNOOZE = "com.kalotrapezis.drive.CALENDAR_SNOOZE"
    private const val ACTION_AGAIN = "com.kalotrapezis.drive.CALENDAR_AGAIN"
    // shortcut: a reminder set more than 120 days before its event is not rung; a longer look when someone needs it.
    private val LEAD: Duration = Duration.ofDays(120)
    private val MISSED: Duration = Duration.ofDays(1)

    private fun prefs(c: Context) = c.getSharedPreferences("calendar_alarms", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("on", true)
    fun setEnabled(c: Context, on: Boolean) { prefs(c).edit().putBoolean("on", on).apply(); planSoon(c) }

    /** Every reminder of [cals] that rings in [from, to), earliest first. */
    fun reminders(cals: List<CalendarInfo>, from: Instant, to: Instant): List<DueReminder> =
        CalendarFiles.occurrences(cals, from.minus(MISSED), to.plus(LEAD)).flatMap { o ->
            o.event.alarms.flatMapIndexed { i, a ->
                a.times(o.start, o.end).mapIndexedNotNull { n, t ->
                    val at = t.toInstant()
                    if (at < from || at >= to) null else DueReminder(at, o, "${o.file.uid}|${o.start.toInstant()}|$i|$n")
                }
            }
        }.sortedBy { it.at }

    fun planSoon(c: Context) { val app = c.applicationContext; Thread { runCatching { plan(app) }.onFailure { WriterLog.e("Calendar reminders: plan failed", it) } }.start() }

    /** Shows what is due since the last look and sets the alarm for the next one. Off the main thread. */
    @Synchronized fun plan(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val ring = PendingIntent.getBroadcast(c, 0, Intent(c, CalendarAlarmReceiver::class.java).setAction(ACTION_RING), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val root = calendarRoot()
        val now = Instant.now()
        val p = prefs(c)
        if (!enabled(c) || !root.isDirectory) { am.cancel(ring); p.edit().putLong("upTo", now.toEpochMilli()).apply(); return }
        val cals = CalendarFiles.calendars(root)
        // The first look ever starts now: nothing from before Tetra knew of reminders.
        val upTo = p.getLong("upTo", now.toEpochMilli()).let { Instant.ofEpochMilli(it) }.coerceAtLeast(now.minus(MISSED))
        reminders(cals, upTo, now.plusSeconds(1)).forEach { show(c, it, late = Duration.between(it.at, now) > Duration.ofMinutes(5)) }
        p.edit().putLong("upTo", now.plusSeconds(1).toEpochMilli()).apply()
        val next = reminders(cals, now.plusSeconds(1), now.plus(LEAD)).firstOrNull()
        if (next == null) { am.cancel(ring); return }
        val at = next.at.toEpochMilli()
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, ring)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, ring) // exact alarms refused: Android's nearest, minutes late at worst
        WriterLog.i("Calendar reminders: next at ${next.at} for ${next.occ.event.title}")
    }

    private fun channel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Calendar reminders", NotificationManager.IMPORTANCE_HIGH).apply { description = "Reminders of your events" })
    }

    /** The words under a reminder's title: "Today 13:00 – 14:00 · Café Ermou", "Tue 20 Oct, all day". */
    fun whenText(c: Context, o: Occurrence): String {
        val zone = ZoneId.systemDefault()
        val day = if (o.allDay) o.start.toLocalDate() else o.start.withZoneSameInstant(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val dayText = when (day) { today -> "Today"; today.plusDays(1) -> "Tomorrow"; else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)) }
        val time = if (o.allDay) "$dayText, all day" else "$dayText ${timeText(c, o.start)} – ${timeText(c, o.end)}"
        return time + (o.event.place.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
    }

    private fun show(c: Context, r: DueReminder, late: Boolean) =
        post(c, r.key.hashCode(), r.occ.event.title.ifEmpty { "(No title)" }, (if (late) "Missed · " else "") + whenText(c, r.occ), r.occ.calendar.color)

    fun post(c: Context, id: Int, title: String, text: String, color: Int) {
        channel(c)
        val open = PendingIntent.getActivity(c, id, Intent(c, MainActivity::class.java).putExtra(Applet.EXTRA, Applet.Calendar.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val snooze = PendingIntent.getBroadcast(c, id, Intent(c, CalendarAlarmReceiver::class.java).setAction(ACTION_SNOOZE)
            .putExtra("id", id).putExtra("title", title).putExtra("text", text).putExtra("color", color), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_calendar_today)
            .setColor(color)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Snooze 10 min", snooze)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(id, n) } // notifications not allowed: nothing to show
    }

    /** Snooze: the same reminder again in ten minutes (an alarm of its own; a restart forgets it). */
    fun snooze(c: Context, i: Intent) {
        val id = i.getIntExtra("id", 0)
        NotificationManagerCompat.from(c).cancel(id)
        val again = PendingIntent.getBroadcast(c, id, Intent(c, CalendarAlarmReceiver::class.java).setAction(ACTION_AGAIN).putExtras(i), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + 10 * 60_000L
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, again)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, again)
    }

    fun handle(c: Context, i: Intent) = when (i.action) {
        ACTION_SNOOZE -> snooze(c, i)
        ACTION_AGAIN -> post(c, i.getIntExtra("id", 0), i.getStringExtra("title").orEmpty(), i.getStringExtra("text").orEmpty(), i.getIntExtra("color", CalendarFiles.PALETTE[0]))
        else -> plan(c) // its alarm, a start, a clock or zone change, an update
    }
}

/** The reminders' alarm, Snooze, and the moments the plan must be made again (boot, clock, zone, update). */
class CalendarAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val done = goAsync()
        val app = context.applicationContext
        WriterLog.init(app) // Tetra may not be open: the log is opened here too
        Thread {
            runCatching { CalendarAlarms.handle(app, intent) }.onFailure { WriterLog.e("Calendar reminders: ${intent.action} failed", it) }
            done.finish()
        }.start()
    }
}
