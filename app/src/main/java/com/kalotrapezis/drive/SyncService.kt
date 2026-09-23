package com.kalotrapezis.drive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal data class BackupState(val progress: BackupProgress?, val result: BackupResult?, val error: String?, val paused: Boolean = false)

/**
 * Runs the backup outside the Activity's lifecycle so it survives the app being closed or backgrounded.
 * ponytail: no restart-on-kill (Android can still kill a foreground service under memory pressure); add
 * WorkManager as a retry layer if that turns out to happen often in practice.
 */
internal class SyncService : Service() {
    companion object {
        private const val CHANNEL_ID = "sync"
        private const val AUTO_SYNC_GAP_MS = 15 * 60_000L

        /**
         * A VPN answers for the connection it hides, and an ad-blocking VPN answers "no idea": its network has
         * no metering to report and names no network underneath it. Asking it alone turned every automatic sync
         * off for anyone running one, so when a VPN is in the way this asks the real networks instead.
         */
        private fun onUnmeteredNetwork(networks: ConnectivityManager): Boolean {
            val active = networks.getNetworkCapabilities(networks.activeNetwork) ?: return false
            if (!active.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return active.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
            @Suppress("DEPRECATION") // allNetworks is how you see past a VPN; there is no replacement that does
            return networks.allNetworks.any { network ->
                networks.getNetworkCapabilities(network)?.let {
                    !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                } == true
            }
        }
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_PAUSE = "com.kalotrapezis.drive.PAUSE"

        private val _state = MutableStateFlow<BackupState?>(null)
        val state = _state.asStateFlow()
        val isRunning: Boolean get() = _state.value != null
        /** Set from the app, the notification and the Dynamic Island alike; the running service watches it. */
        internal val paused = MutableStateFlow(false)
        fun togglePause() { paused.value = !paused.value }

        /**
         * Sync without being asked — when the app opens, and after the scanner saves a PDF. It is deliberately
         * easy to talk out of: no paired computer, a backup already running, a metered connection or a sync a
         * few minutes ago and it simply does not happen. `gap` is 0 for "something just changed, send it now".
         */
        fun syncInBackground(context: Context, gap: Long = AUTO_SYNC_GAP_MS) {
            if (isRunning) return
            val app = context.applicationContext
            val store = SyncStore(app)
            if (store.pairing() == null) return
            if (System.currentTimeMillis() - store.lastBackup() < gap) return
            val networks = app.getSystemService(ConnectivityManager::class.java) ?: return
            // Wi-Fi only: a backup is the user's whole camera roll, never something to put on mobile data by itself.
            if (!onUnmeteredNetwork(networks)) return
            runCatching {
                ContextCompat.startForegroundService(app, Intent(app, SyncService::class.java).setAction(ACTION_START))
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val islandSupported by lazy { HyperIsland.isSupported(applicationContext) }
    private var lastText = ""
    private var lastDone = 0
    private var lastTotal = 0
    private var lastShown = 0L
    private var announced = false

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
        announced = false
        paused.value = false
        startForeground(NOTIFICATION_ID, notification("Checking photos…", 0, 0))
        _state.value = BackupState(null, null, null)
        // Pause can come from the app, the notification or the island; all three end up here.
        scope.launch { paused.collect { value ->
            if (job == null) return@collect
            _state.value = _state.value?.copy(paused = value)
            show(lastText, lastDone, lastTotal)
        } }
        job = scope.launch {
            val result = runCatching { client.backUp(listPhotos(applicationContext), checkpoint = { paused.first { !it } }) { p -> onProgress(p) } }
            _state.value = result.fold(
                { r -> BackupState(null, r, null) },
                { e -> BackupState(null, null, if (e is kotlinx.coroutines.CancellationException) "Stopped. Photos already sent are safe on the computer." else e.message ?: "Backup failed.") },
            )
            job = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            // A sync is the other moment photos appear here without anyone taking them.
            PhotoAnalysisService.start(applicationContext)
            stopSelf()
        }
    }

    private fun onProgress(p: BackupProgress) {
        _state.value = BackupState(p, null, null, paused.value)
        // The in-app screen follows every file; the notification does not need to, and re-posting it per file
        // makes the Dynamic Island flash open again each time.
        val last = p.done >= p.total
        if (last || System.currentTimeMillis() - lastShown >= 1_000) show("${p.stage} · ${p.done} of ${p.total}", p.done, p.total)
    }

    private fun show(text: String, done: Int, total: Int) {
        lastText = text; lastDone = done; lastTotal = total; lastShown = System.currentTimeMillis()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, done, total))
    }

    private fun notification(text: String, done: Int, total: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Sync", NotificationManager.IMPORTANCE_LOW))
        }
        val isPaused = paused.value
        val stopPending = service(1, ACTION_STOP)
        val pausePending = android.app.PendingIntent.getBroadcast(
            this, 2, Intent(ACTION_PAUSE).setPackage(packageName),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pauseLabel = if (isPaused) "Resume" else "Pause"
        val title = if (isPaused) "Backup paused" else "Backing up to computer"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(total, done, total == 0 && !isPaused)
            .setOngoing(true)
            .addAction(0, pauseLabel, pausePending)
            .addAction(0, "Stop", stopPending)
        // Additive: on a Xiaomi with focus notifications this also puts the backup in the Dynamic Island,
        // and everywhere else the extra is ignored and this is the same notification as before.
        if (islandSupported) {
            HyperIsland.decorate(builder, title, text, done, total, announce = !announced)
            announced = true
        }
        return builder.build()
    }

    private fun service(requestCode: Int, action: String): android.app.PendingIntent = android.app.PendingIntent.getService(
        this, requestCode, Intent(this, SyncService::class.java).setAction(action),
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        scope.cancel()
        _state.value = null
        super.onDestroy()
    }
}
