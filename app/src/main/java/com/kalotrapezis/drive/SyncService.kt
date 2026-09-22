package com.kalotrapezis.drive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class BackupState(val progress: BackupProgress?, val result: BackupResult?, val error: String?)

/**
 * Runs the backup outside the Activity's lifecycle so it survives the app being closed or backgrounded.
 * ponytail: no restart-on-kill (Android can still kill a foreground service under memory pressure); add
 * WorkManager as a retry layer if that turns out to happen often in practice.
 */
internal class SyncService : Service() {
    companion object {
        private const val CHANNEL_ID = "sync"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"

        private val _state = MutableStateFlow<BackupState?>(null)
        val state = _state.asStateFlow()
        val isRunning: Boolean get() = _state.value != null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> job?.cancel()
            else -> startBackup()
        }
        return START_NOT_STICKY
    }

    private fun startBackup() {
        if (job != null) return
        val store = SyncStore(applicationContext)
        val client = SyncClient(applicationContext, store)
        startForeground(NOTIFICATION_ID, notification("Checking photos…", 0, 0))
        _state.value = BackupState(null, null, null)
        job = scope.launch {
            val result = runCatching { client.backUp(listPhotos(applicationContext)) { p -> onProgress(p) } }
            _state.value = result.fold(
                { r -> BackupState(null, r, null) },
                { e -> BackupState(null, null, if (e is kotlinx.coroutines.CancellationException) "Stopped. Photos already sent are safe on the computer." else e.message ?: "Backup failed.") },
            )
            job = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun onProgress(p: BackupProgress) {
        _state.value = BackupState(p, null, null)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification("${p.stage} · ${p.done} of ${p.total}", p.done, p.total))
    }

    private fun notification(text: String, done: Int, total: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Sync", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = Intent(this, SyncService::class.java).setAction(ACTION_STOP)
        val stopPending = android.app.PendingIntent.getService(this, 0, stop, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle("Backing up to computer")
            .setContentText(text)
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        _state.value = null
        super.onDestroy()
    }
}
