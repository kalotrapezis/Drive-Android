package com.kalotrapezis.drive

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Writer's log (asked 2026-10-01: "the word app is huge, we are going to need them"): what was opened, what was tapped,
 * what was saved, how long the layouts took, and every error — to logcat (tag Tetra.Writer, for adb) and to
 * files/logs/writer.log on the device, kept to about 1 MB (the older half in writer.log.1), so a problem seen on the
 * phone away from the computer can be sent: File › Send log. Words of documents are never written to it, only their
 * shape (paragraph counts, sizes, what Writer could not keep) — the user's documents are private.
 */
internal object WriterLog {
    private const val TAG = "Tetra.Writer"
    private const val MAX_BYTES = 512 * 1024L
    private var file: File? = null
    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    /** Once per process: where the log goes, and a line saying which app and device wrote what follows. */
    fun init(context: Context) {
        if (file != null) return
        file = File(context.filesDir, "logs/writer.log").apply { parentFile?.mkdirs() }
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        i("— Tetra $version on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        // A crash anywhere in the app: written down before the app goes (the system's own handler still runs after).
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e -> e("crash on ${t.name}", e); before?.uncaughtException(t, e) }
    }

    fun i(message: String) = write('I', message, null)
    fun w(message: String, error: Throwable? = null) = write('W', message, error)
    fun e(message: String, error: Throwable? = null) = write('E', message, error)

    /** [block] done, and how long it took. */
    inline fun <T> timed(what: String, block: () -> T): T {
        val start = System.nanoTime()
        return block().also { i("$what: ${(System.nanoTime() - start) / 1_000_000} ms") }
    }

    /** The log as one file to send: the older part first. */
    fun export(context: Context): File? {
        val f = file ?: return null
        val out = File(context.filesDir, "logs/Tetra Writer log.txt")
        synchronized(this) { out.writeText(File(f.path + ".1").takeIf { it.isFile }?.readText().orEmpty() + (f.takeIf { it.isFile }?.readText().orEmpty())) }
        return out
    }

    private fun write(level: Char, message: String, error: Throwable?) {
        when (level) { 'E' -> Log.e(TAG, message, error); 'W' -> Log.w(TAG, message, error); else -> Log.i(TAG, message) }
        val f = file ?: return
        val line = "${LocalDateTime.now().format(time)} $level $message\n" + (error?.let { Log.getStackTraceString(it) + "\n" } ?: "")
        synchronized(this) {
            runCatching {
                if (f.length() > MAX_BYTES) f.renameTo(File(f.path + ".1"))
                f.appendText(line)
            }
        }
    }
}
