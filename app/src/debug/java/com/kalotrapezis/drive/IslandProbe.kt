package com.kalotrapezis.drive

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Debug builds only: `adb shell am broadcast -a com.kalotrapezis.drive.ISLAND_PROBE --es variant sync|reminder|plain|clear`
 * posts a test notification shaped as sync's island (which HyperOS shows), as the Calendar's reminder, or as a plain one,
 * to find what the island needs.
 */
class IslandProbe : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val variant = i.getStringExtra("variant") ?: "sync"
        if (variant == "clear") { listOf("sync", "reminder", "plain").forEach { NotificationManagerCompat.from(c).cancel(9000 + it.length) }; return }
        val ongoing = i.getBooleanExtra("ongoing", true)
        CalendarAlarms.post(c, 0, "", "", 0) // makes sure the channel exists; id 0 cancelled below
        NotificationManagerCompat.from(c).cancel(0)
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(c, "calendar_reminders").setSmallIcon(R.drawable.ic_calendar_today)
            .setContentTitle("Probe $variant").setContentText("Island probe").setContentIntent(open).setOngoing(ongoing)
        when (variant) {
            "sync" -> HyperIsland.decorate(b, "Probe sync", "Island probe", 1, 2, announce = true, business = "tetra_probe")
            "reminder" -> HyperIsland.reminder(b, "Probe reminder", "Island probe", "14:30", open, Icon.createWithResource(c, R.drawable.ic_reminder_done), open)
        }
        NotificationManagerCompat.from(c).notify(9000 + variant.length, b.build())
    }
}
