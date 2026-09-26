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
 * A sync every 30 minutes on Wi-Fi, with the app closed too (asked 2026-09-26). Android runs it about then — it may
 * group it with other apps' work — and only on an unmetered network, the same rule as every sync that starts by
 * itself. It syncs in the job itself: a job may not start the foreground service from the background on newer
 * Android, and it has ten minutes, which a sync with nothing new needs a few seconds of.
 */
internal class SyncJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        val store = SyncStore(applicationContext)
        if (SyncService.isRunning || store.pairing() == null || System.currentTimeMillis() - store.lastBackup() < 25 * 60_000L) return false
        scope.launch {
            val failed = runCatching { SyncClient(applicationContext, store).backUp(listPhotos(applicationContext)) {} }.isFailure
            jobFinished(params, failed) // a failure (the computer asleep) is tried again later
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean { scope.cancel(); return true }

    companion object {
        private const val ID = 4130

        /** Once is enough: the schedule lasts across restarts. Called when the app opens. */
        fun schedule(context: Context) {
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            if (jobs.getPendingJob(ID) != null) return
            jobs.schedule(JobInfo.Builder(ID, ComponentName(context, SyncJob::class.java))
                .setPeriodic(30 * 60_000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPersisted(true)
                .build())
        }
    }
}
