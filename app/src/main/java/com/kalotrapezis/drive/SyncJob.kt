package com.kalotrapezis.drive

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A sync every [minutes] (Settings › Sync, 30 by default), with the app closed too (asked 2026-09-26, the interval
 * 2026-10-13). Android runs it about then — it may group it with other apps' work, and in Doze it waits for the next
 * maintenance window. It no longer waits for unmetered Wi-Fi: a phone's hotspot is metered, so on one the job never
 * ran, and a sync only ever talks to private LAN addresses, which mobile data does not carry. It syncs in the job itself: a job may not start the foreground service from the background on newer
 * Android, and it has ten minutes, which a sync with nothing new needs a few seconds of.
 */
internal class SyncJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        val store = SyncStore(applicationContext)
        // A new photo or video: sync now (asked 2026-09-27) — the computer there or not, the trigger is armed again.
        val newPhoto = params.jobId == NEW_PHOTO
        if (newPhoto) watchNewPhotos(applicationContext)
        if (SyncService.isRunning || store.pairing() == null || !onLocalNetwork() || (!newPhoto && System.currentTimeMillis() - store.lastBackup() < minutes(applicationContext) * 50_000L)) return false
        scope.launch {
            val failed = runCatching { SyncClient(applicationContext, store).backUp(listPhotos(applicationContext)) {} }.isFailure
            jobFinished(params, failed) // a failure (the computer asleep) is tried again later
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean { scope.cancel(); return true }

    companion object {
        private const val ID = 4130
        private const val NEW_PHOTO = 4131
        private const val MINUTES = "auto_sync_minutes"
        val choices = listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "1 hour")

        /** 0 is Off. Android runs a periodic job no more often than every 15 minutes. */
        fun minutes(context: Context): Int = context.getSharedPreferences("sync_job", Context.MODE_PRIVATE).getInt(MINUTES, 30)

        fun setMinutes(context: Context, value: Int) {
            context.getSharedPreferences("sync_job", Context.MODE_PRIVATE).edit().putInt(MINUTES, value).apply()
            schedule(context)
        }

        /** On some LAN — Wi-Fi, Ethernet, or this phone's own hotspot, which has no "active network" when data is off. */
        private fun onLocalNetwork(): Boolean = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().toList().any { i ->
                i.isUp && !i.isLoopback && i.inetAddresses.toList().any { it is java.net.Inet4Address && it.isSiteLocalAddress }
            }
        }.getOrDefault(false)

        /**
         * When Android adds a photo or a video, a sync about ten seconds later (a burst is one sync), on Wi-Fi, with
         * the app closed too. A job watching MediaStore runs once, so it is armed again each time; it does not survive a
         * restart, so opening the app arms it as well.
         */
        fun watchNewPhotos(context: Context) {
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            val flags = JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
            jobs.schedule(JobInfo.Builder(NEW_PHOTO, ComponentName(context, SyncJob::class.java))
                .addTriggerContentUri(JobInfo.TriggerContentUri(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, flags))
                .addTriggerContentUri(JobInfo.TriggerContentUri(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, flags))
                .setTriggerContentUpdateDelay(10_000)
                .setTriggerContentMaxDelay(60_000)
                .build())
        }

        /**
         * The schedule lasts across restarts; called when the app opens and when the interval changes. A job from an
         * older build (Wi-Fi only) or another interval is replaced.
         */
        fun schedule(context: Context) {
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            jobs.getPendingJob(NEW_PHOTO).let { if (it == null || it.networkType != JobInfo.NETWORK_TYPE_NONE) watchNewPhotos(context) }
            val every = minutes(context) * 60_000L
            if (every == 0L) return jobs.cancel(ID)
            jobs.getPendingJob(ID)?.let { if (it.intervalMillis == every && it.networkType == JobInfo.NETWORK_TYPE_NONE) return }
            jobs.schedule(JobInfo.Builder(ID, ComponentName(context, SyncJob::class.java))
                .setPeriodic(every)
                .setPersisted(true)
                .build())
        }
    }
}
