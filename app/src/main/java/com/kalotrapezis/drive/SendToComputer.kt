package com.kalotrapezis.drive

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Send to computer (the user, 23 Oct): Tetra in Android's share menu. What is shared goes to the paired computer at
 * once, over the Wi-Fi link sync already has (desktop sync.js PUT /send), into its Files › From phone. Each file is
 * first copied into a private outbox — another app's permission to read it ends with this screen — so with the
 * computer away the files wait there and go when it is seen: a JobScheduler retry, the next sync, or its "sync now".
 */
class SendToComputer : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.streamExtra())
            Intent.ACTION_SEND_MULTIPLE -> intent.streamsExtra()
            else -> emptyList()
        }
        val computer = SyncStore(this).pairing()?.name
        when {
            computer == null -> { Toast.makeText(this, "No computer is paired. Pair one in Tetra › Settings › Sync.", Toast.LENGTH_LONG).show(); return finish() }
            uris.isEmpty() -> { Toast.makeText(this, "Nothing to send: only files can go to the computer.", Toast.LENGTH_LONG).show(); return finish() }
        }
        val app = applicationContext
        thread(name = "tetra-send") {
            val failed = uris.count { uri -> runCatching { Outbox.add(app, contentResolver.openInputStream(uri)!!, displayName(uri)) }.onFailure { WriterLog.w("Send to computer: $uri: ${it.message}") }.isFailure }
            runOnUiThread {
                if (failed > 0) Toast.makeText(this, "$failed of ${uris.size} could not be read", Toast.LENGTH_LONG).show()
                finish()
            }
            Outbox.soon(app, say = true)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamExtra(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM)
    @Suppress("DEPRECATION")
    private fun Intent.streamsExtra(): List<Uri> = getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()

    private fun displayName(uri: Uri): String {
        val named = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentResolver.getType(uri))
        return Outbox.safeName(named, ext)
    }
}

/** Files on their way to the computer: filesDir/outbox/<sha256>/<name>, each gone once the computer has it verified. */
internal object Outbox {
    private const val WAITING = 4143
    const val EXTRA = "outbox"
    private val running = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun dir(context: Context) = File(context.filesDir, "outbox")

    /** A name the computer takes: no folders, no control characters; one is made up when the sending app gave none. */
    fun safeName(name: String?, ext: String?): String {
        val clean = name.orEmpty().substringAfterLast('/').substringAfterLast('\\').filter { it >= ' ' }.trim().take(200)
        return clean.takeIf { it.isNotEmpty() && it != "." && it != ".." }
            ?: "Shared ${java.text.SimpleDateFormat("yyyy-MM-dd HHmmss", java.util.Locale.ROOT).format(java.util.Date())}${ext?.let { ".$it" }.orEmpty()}"
    }

    fun add(context: Context, input: InputStream, name: String): File {
        val tmp = File(dir(context), "." + UUID.randomUUID()).apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { DigestOutputStream(File(tmp, name).outputStream(), digest).use(it::copyTo) }
        val held = File(dir(context), SyncRules.hex(digest.digest()))
        if (held.exists()) { tmp.deleteRecursively(); return File(held, name) } // shared twice: it goes once
        check(tmp.renameTo(held)) { "could not keep it" }
        return File(held, name)
    }

    fun waiting(context: Context): List<File> = dir(context).listFiles()?.filter { it.isDirectory && it.name.length == 64 }
        ?.sortedBy { it.lastModified() }?.mapNotNull { d -> d.listFiles()?.firstOrNull { it.isFile } }.orEmpty()

    /** Try now; the computer away, Android tries again once there is a network, with a growing gap. [say]: a toast of how it went. */
    fun soon(context: Context, say: Boolean = false) {
        val app = context.applicationContext
        running.launch {
            if (waiting(app).isEmpty()) return@launch
            val store = SyncStore(app)
            runCatching { SyncClient(app, store).sendOutbox() }
                .onSuccess { n -> if (say && n > 0) toast(app, "Sent ${files(n)} to ${store.pairing()?.name ?: "the computer"}") }
                .onFailure {
                    WriterLog.w("Send to computer: waiting (${it.message})")
                    val old = ScopedSync.tooOld(it)
                    if (say) toast(app, if (old) "The computer needs the new Tetra to take these." else "${store.pairing()?.name ?: "The computer"} is not reachable now: ${files(waiting(app).size)} will go when it is.")
                    if (!old) wait(app)
                }
        }
    }

    /** Run by Android when there is a network, and again with a growing gap until the computer has them (SyncJob). */
    private fun wait(context: Context) {
        val jobs = context.getSystemService(JobScheduler::class.java) ?: return
        jobs.schedule(JobInfo.Builder(WAITING, ComponentName(context, SyncJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setExtras(android.os.PersistableBundle().apply { putBoolean(EXTRA, true) })
            .setPersisted(true)
            .build())
    }

    private fun files(n: Int) = if (n == 1) "1 file" else "$n files"
    private fun toast(context: Context, text: String) = Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
}

/**
 * Send to phone, this side (the user, 23 Oct): a file the computer sends (SyncServer PUT /send) lands in Tetra's
 * Files › From computer, hashed while written and kept only if it matches; a name already there with other bytes
 * keeps both, "name (2)". One notification for a burst: "3 files from <computer>".
 */
internal object FromComputer {
    const val FOLDER = "From computer"
    private const val CHANNEL = "from_computer"
    private const val NOTE = 4144
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val names = mutableListOf<String>()
    private var shown: Runnable? = null

    /** The file kept, in [dir]. Throws IllegalArgumentException for a bad name or bytes that are not [sha]. */
    fun receive(input: InputStream, length: Long, dir: File, sha: String, name: String, modified: Long): File {
        require(name.isNotEmpty() && name.length < 256 && '/' !in name && '\\' !in name && '\u0000' !in name && name != "." && name != "..") { "Invalid name." }
        dir.mkdirs()
        val part = File(dir, ".$name.${UUID.randomUUID()}.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestOutputStream(part.outputStream(), digest).use { out ->
                val buffer = ByteArray(256 * 1024)
                var left = length
                while (left > 0) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n < 0) throw java.io.EOFException("The computer stopped sending.")
                    out.write(buffer, 0, n)
                    left -= n
                }
            }
            require(SyncRules.hex(digest.digest()) == sha) { "The file changed in transit; nothing was kept." }
            val same = File(dir, name)
            if (same.isFile && same.length() == length && sha256(same) == sha) {
                part.delete(); return same // sent again after its answer was lost
            }
            val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
            val kept = generateSequence(1) { it + 1 }.map { n -> File(dir, if (n == 1) name else "${name.substring(0, dot)} ($n)${name.substring(dot)}") }.first { !it.exists() }
            check(part.renameTo(kept)) { "could not keep it" }
            if (modified > 0) kept.setLastModified(modified)
            return kept
        } finally { part.delete() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(256 * 1024)
        file.inputStream().use { while (true) { val n = it.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return SyncRules.hex(digest.digest())
    }

    fun arrived(context: Context, name: String, from: String) {
        val app = context.applicationContext
        handler.post {
            names += name
            shown?.let(handler::removeCallbacks)
            shown = Runnable { notify(app, names.toList(), from); names.clear() }.also { handler.postDelayed(it, 1_500) }
        }
    }

    private fun notify(context: Context, files: List<String>, from: String) {
        val nm = context.getSystemService(NotificationManager::class.java)?.takeIf { it.areNotificationsEnabled() } ?: return
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Files from the computer", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Files the computer sends with Send to phone" })
        val open = PendingIntent.getActivity(context, NOTE, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(NOTE, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_computer)
            .setContentTitle("${if (files.size == 1) "1 file" else "${files.size} files"} from $from")
            .setContentText("In Files › $FOLDER: ${files.distinct().joinToString(", ")}")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
