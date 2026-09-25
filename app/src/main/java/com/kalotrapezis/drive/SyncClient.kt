package com.kalotrapezis.drive

import android.content.ContentValues
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.coroutineContext

/** What the desktop's pairing QR carries (desktop: sync.js startPairing). */
/** The desktop stores the same string (faces.js EMBEDDING_MODEL): same weights, same eye alignment, one space. */
internal const val FACE_EMBEDDING_MODEL = "mobilefacenet-192-eyes38x44-74x44"

internal data class PairingQr(val name: String, val hosts: List<String>, val port: Int, val fingerprint: String, val code: String)
internal data class Pairing(val name: String, val hosts: List<String>, val port: Int, val fingerprint: String, val token: String)
internal data class BackupProgress(val stage: String, val done: Int, val total: Int)
internal data class BackupResult(val checked: Int, val sent: Int, val alreadyThere: Int, val failed: List<String>, val received: Int = 0)

/**
 * What this device may do with one kind of content, as the computer holds it (SYNC_PLAN.md 6j). Direction is
 * written from this device's point of view, because this device is the one that reads it and obeys: **send** is
 * phone → computer, **receive** is computer → phone, **both** is both. Keep says what the source does with its
 * copy afterwards, and only "everything" — a Copy — is implemented; two-way forces it anyway.
 */
internal data class SyncConnection(val content: String, val direction: String, val keep: String, val keepDays: Int = 30, val keepFavorites: Boolean = true) {
    val sends: Boolean get() = direction == "send" || direction == "both"
    val receives: Boolean get() = direction == "receive" || direction == "both"

    /** The sentence the phone shows for this row: the computer configures, the phone says what it was told. */
    fun sentence(computer: String): String {
        val what = if (content == "files") "Files" else "Photos"
        if (direction == "off") return "$what · not synced"
        val arrow = when (direction) {
            "send" -> "→ $computer"
            "receive" -> "← $computer"
            else -> "⇄ $computer"
        }
        return "$what $arrow · ${if (keep == "nothing") "Move, keeping ${SyncRules.span(keepDays)}" else "Copy"}"
    }

    companion object {
        val defaults = listOf(SyncConnection("photos", "both", "everything"), SyncConnection("files", "both", "everything"))
    }
}

internal object SyncRules {
    private val hex64 = Regex("^[0-9a-f]{64}$")

    /** How many times a sync that died on a lost network is started again, once a network is back. */
    const val NETWORK_RETRIES = 3

    /**
     * A sync that died when the Wi-Fi moved under it — an extender handing over to the main router, on
     * 24 September — is worth starting again the moment a network is back. Three times, and no more: after
     * that the computer is assumed to be away, and the next attempt waits for someone to open the app. A
     * cancellation is not a failure; Stop means stop.
     */
    fun retriesAfterNetworkLoss(attempt: Int, failure: Throwable?): Boolean =
        failure != null && failure !is kotlinx.coroutines.CancellationException && attempt < NETWORK_RETRIES

    fun parseQr(text: String): PairingQr? = runCatching {
        val o = JSONObject(text)
        if (o.getInt("v") != 1) return null
        val hosts = o.getJSONArray("hosts").let { a -> (0 until a.length()).map { a.getString(it) } }
        PairingQr(o.optString("name", "Computer"), hosts, o.getInt("port"), o.getString("fp").lowercase(), o.getString("code"))
            .takeIf { it.fingerprint.matches(hex64) && it.port in 1..65535 && it.hosts.isNotEmpty() && it.code.length >= 16 }
    }.getOrNull()

    /** Photos keep their phone folders on the computer (1:1), e.g. DCIM/Camera. */
    fun blobPath(sha256: String, relativePath: String?, name: String, modified: Long): String {
        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
        return "/blob/$sha256?path=${enc(relativePath.orEmpty().trim('/'))}&name=${enc(name)}&modified=$modified"
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /**
     * The colour of a number of places, the same as the computer's copies bar: 1 and 2 are the risk (red, yellow);
     * from 3 on the hue is found by halving — 0, 1/2, 1/4, 3/4, 1/8… of the way from green to magenta — so every
     * count stays as far from the others as it can. Hue, saturation, lightness.
     */
    fun copiesColor(n: Int): Triple<Float, Float, Float> {
        if (n <= 1) return Triple(358f, 0.75f, 0.59f)
        if (n == 2) return Triple(47f, 0.92f, 0.53f)
        var k = n - 3; var f = 0.0; var half = 0.5
        while (k > 0) { if (k and 1 == 1) f += half; k = k shr 1; half /= 2 }
        return Triple(Math.round(125 + f * 190).toFloat(), 0.75f, 0.52f)
    }

    /** "3 months", "1 year", "10 days": a Move's window, in the largest unit it divides into (as the computer shows it). */
    fun span(days: Int): String {
        val (unit, per) = listOf("year" to 365, "month" to 30, "week" to 7, "day" to 1).first { (_, n) -> days % n == 0 }
        val n = days / per
        return "$n $unit${if (n > 1) "s" else ""}"
    }

    /**
     * Where a photo the computer sends is allowed to land.
     *
     * Android does not let an app file an image just anywhere: the first folder of a MediaStore path has to be
     * one of the few it considers a place for pictures, and anything else is refused outright. So the computer's
     * own layout is kept when it starts somewhere this phone can write to — a photo that left DCIM/Camera goes
     * back to DCIM/Camera — and everything else lands under Pictures/Tetra rather than failing.
     */
    fun incomingFolder(path: String, video: Boolean): String {
        val clean = path.trim('/').takeIf { it.isNotEmpty() && it.split('/').all { part -> part.isNotBlank() && part != "." && part != ".." } }
        val allowed = if (video) setOf("DCIM", "Movies", "Pictures") else setOf("DCIM", "Pictures")
        return clean?.takeIf { it.substringBefore('/') in allowed } ?: if (video) "Movies/Tetra" else "Pictures/Tetra"
    }

    /**
     * Face boxes are stored in the pixels of the bitmap PhotoClassifier analysed (longest side capped at 1280),
     * so the same formula turns them back into the fractions of the upright image that both apps sync
     * (SYNC_PLAN.md "Face box"). Null when MediaStore did not report a size.
     */
    fun analysisSize(width: Int, height: Int): Pair<Int, Int>? {
        if (width <= 0 || height <= 0) return null
        val scale = minOf(1f, 1280f / maxOf(width, height))
        return (width * scale).toInt() to (height * scale).toInt()
    }
}

/** MediaStore derives the gallery date from the file mtime when the image has no readable EXIF. */
internal fun publishReceivedPhoto(resolver: ContentResolver, uri: Uri, taken: Long?) {
    taken?.let { time ->
        val path = resolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        // A wrong date is worth a warning, not the photo: failing here would delete it and fetch it again forever.
        if (path == null || !File(path).setLastModified(time)) android.util.Log.w("SyncClient", "Could not keep the date of $uri")
    }
    check(resolver.update(uri, ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
}

/** Separate from photo_metadata.db: content hashes (by the gallery's photo key) and receipts from the computer. */
internal class SyncStore(private val context: Context) : SQLiteOpenHelper(context, "sync.db", null, 3) {
    private val prefs = context.getSharedPreferences("sync_pairing", Context.MODE_PRIVATE)

    private companion object {
        /**
         * Photos a Move has finished with: sent, and confirmed by the computer's own reading of the bytes. A
         * sync never removes anything itself — it cannot, and should not: taking a photo off a phone is
         * Android's own request with Android's own confirmation, and that needs a screen. So they wait here
         * until someone says yes, and until then the photo is exactly where it was.
         */
        const val MOVE_QUEUE = "CREATE TABLE IF NOT EXISTS to_remove (photo_key TEXT PRIMARY KEY, sha256 TEXT NOT NULL, queued_at INTEGER NOT NULL)"
        const val PEERS = "CREATE TABLE IF NOT EXISTS peers (fp TEXT PRIMARY KEY, name TEXT NOT NULL, hosts TEXT NOT NULL, " +
            "port INTEGER NOT NULL, token TEXT NOT NULL, their_token TEXT NOT NULL, paired_at INTEGER NOT NULL)"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE identity (photo_key TEXT PRIMARY KEY, sha256 TEXT NOT NULL)")
        db.execSQL("CREATE TABLE receipts (photo_key TEXT PRIMARY KEY, sha256 TEXT NOT NULL, path TEXT NOT NULL, sent_at INTEGER NOT NULL)")
        db.execSQL(PEERS)
        db.execSQL(MOVE_QUEUE)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL(PEERS)
        if (oldVersion < 3) db.execSQL(MOVE_QUEUE)
    }

    /**
     * Every device this one is paired with, not just the computer: a phone and a tablet pair with each other and
     * with the same computer, so one remembered pairing was never going to be enough. Identity is the pinned
     * certificate fingerprint, which is why it is the key.
     */
    fun peers(): List<Peer> = readableDatabase.rawQuery(
        "SELECT name, hosts, port, fp, token, their_token FROM peers ORDER BY paired_at", null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(Peer(c.getString(0), c.getString(1).split(',').filter(String::isNotBlank),
                c.getInt(2), c.getString(3), c.getString(4), c.getString(5)))
        }
    }

    fun savePeer(peer: Peer) {
        writableDatabase.insertWithOnConflict("peers", null, ContentValues().apply {
            put("fp", peer.fingerprint); put("name", peer.name); put("hosts", peer.hosts.joinToString(","))
            put("port", peer.port); put("token", peer.token); put("their_token", peer.theirToken)
            put("paired_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun forgetPeer(fingerprint: String) { writableDatabase.delete("peers", "fp = ?", arrayOf(fingerprint)) }

    // ponytail: one active pairing only (pairing a second device overwrites this one; receipts are global,
    // not per-device). See SYNC_PLAN.md "Not yet: multiple paired devices" for the upgrade path.
    fun pairing(): Pairing? {
        val token = prefs.getString("token", null) ?: return null
        return Pairing(prefs.getString("name", "Computer")!!, prefs.getString("hosts", "")!!.split(',').filter(String::isNotBlank),
            prefs.getInt("port", 0), prefs.getString("fp", "")!!, token)
    }
    fun savePairing(p: Pairing) = prefs.edit().putString("name", p.name).putString("hosts", p.hosts.joinToString(","))
        .putInt("port", p.port).putString("fp", p.fingerprint).putString("token", p.token).apply()
    fun forgetPairing() = prefs.edit().clear().apply()
    /**
     * The rules the computer last told us, kept only so the Sync page can say what a sync will do before one
     * runs. They are never the authority — the computer's answer at sync time is.
     */
    fun connections(): List<SyncConnection> = prefs.getString("connections", null)
        ?.split(';')?.mapNotNull { row -> row.split(',').takeIf { it.size >= 3 }?.let {
            SyncConnection(it[0], it[1], it[2], it.getOrNull(3)?.toIntOrNull() ?: 30, it.getOrNull(4) != "0")
        } }
        ?.takeIf { it.isNotEmpty() } ?: SyncConnection.defaults

    fun saveConnections(rows: List<SyncConnection>) =
        prefs.edit().putString("connections", rows.joinToString(";") { "${it.content},${it.direction},${it.keep},${it.keepDays},${if (it.keepFavorites) 1 else 0}" }).apply()

    /** Trash items already safe in the purgatory, so they are not sent twice while Android counts down. */
    fun handedOver(uri: String) = uri in prefs.getStringSet("purgatory_sent", emptySet())!!
    fun markHandedOver(uri: String) = prefs.edit().putStringSet("purgatory_sent", prefs.getStringSet("purgatory_sent", emptySet())!! + uri).apply()

    /** The computer's overview from the last sync (GET /overview), or null before the first one. */
    fun overview(): JSONObject? = prefs.getString("overview", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    fun saveOverview(json: String) = prefs.edit().putString("overview", json).apply()

    fun lastBackup(): Long = prefs.getLong("last_backup", 0)
    fun setLastBackup(at: Long) = prefs.edit().putLong("last_backup", at).apply()

    /** One line for the Home card. */
    fun summary(): String = pairing()?.let { "Paired with ${it.name} · ${receiptCount()} photos on the computer" } ?: "No computer paired yet"

    fun cachedHash(photoKey: String): String? = readableDatabase.rawQuery("SELECT sha256 FROM identity WHERE photo_key = ?", arrayOf(photoKey)).use { if (it.moveToFirst()) it.getString(0) else null }
    fun saveHash(photoKey: String, sha256: String) { writableDatabase.insertWithOnConflict("identity", null, ContentValues().apply { put("photo_key", photoKey); put("sha256", sha256) }, SQLiteDatabase.CONFLICT_REPLACE) }
    /**
     * Everything is asked for again when the rules for accepting it change.
     *
     * `?since=` is an efficiency, and it quietly assumes that what we skipped before we would skip again. That
     * stops being true the moment this app learns to accept something it used to drop — as it did when a person
     * named on another device started being created here instead of ignored. Those people were named long ago,
     * so they sit outside every future window and would never arrive at all.
     *
     * So the rules carry a number. When it moves, the next sync asks from the beginning, once.
     */
    fun metadataSince(): Long = if (prefs.getInt("metadata_epoch", 0) == METADATA_EPOCH) prefs.getLong("last_metadata_sync", 0) else 0

    fun lastMetadataSync(): Long = prefs.getLong("last_metadata_sync", 0)
    fun setLastMetadataSync(at: Long) = prefs.edit().putLong("last_metadata_sync", at).putInt("metadata_epoch", METADATA_EPOCH).apply()
    fun saveReceipt(photoKey: String, sha256: String, path: String) { writableDatabase.insertWithOnConflict("receipts", null, ContentValues().apply {
        put("photo_key", photoKey); put("sha256", sha256); put("path", path); put("sent_at", System.currentTimeMillis())
    }, SQLiteDatabase.CONFLICT_REPLACE) }
    /** Verified on the computer, and this connection says Keep Nothing: waiting for someone to say yes. */
    fun queueForRemoval(photoKey: String, sha256: String) {
        writableDatabase.insertWithOnConflict("to_remove", null, ContentValues().apply {
            put("photo_key", photoKey); put("sha256", sha256); put("queued_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun queuedForRemoval(): Set<String> = readableDatabase.rawQuery("SELECT photo_key FROM to_remove", null)
        .use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }

    fun forgetQueued(photoKeys: Collection<String>) {
        if (photoKeys.isEmpty()) return
        writableDatabase.delete("to_remove", "photo_key IN (${photoKeys.joinToString { "?" }})", photoKeys.toTypedArray())
    }

    fun receiptCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM receipts", null).use { it.moveToFirst(); it.getInt(0) }

    /** Every photo this phone has given the computer — the proof that a copy coming back would be one it deleted. */
    fun receiptShas(): Set<String> = readableDatabase.rawQuery("SELECT DISTINCT sha256 FROM receipts", null).use { c ->
        buildSet { while (c.moveToNext()) add(c.getString(0)) }
    }
}

internal class SyncException(message: String) : Exception(message)

/** Talks only to the paired computer: HTTPS whose certificate must match the QR's SHA-256 fingerprint. */
/**
 * Which rules this app applies to metadata it receives. Raise it whenever it starts accepting something it used
 * to ignore, and every device asks for everything once more — otherwise what was skipped stays skipped for ever.
 *
 * 2: a person named on another device is created here rather than dropped (SYNC_PLAN.md 6y).
 */
/** Days before Android's own deletion at which a trashed photo goes to the purgatory (D6). */
private const val HAND_OVER_DAYS = 3
/** Files' Trash has no clock of its own; the same 30 days as Android's (D6). */
private const val FILES_TRASH_DAYS = 30
private const val METADATA_EPOCH = 6 // 5: reconcile every local copy of a face with the computer's group. 6: pull again the
// collection members that were dropped while their folder was hidden here (2026-09-25)

/** How many files cross at once. Four keeps the link busy; more turns a phone's Wi-Fi into stalled sockets. */
private const val AT_ONCE = 4

internal class SyncClient(private val context: Context, private val store: SyncStore) {
    private val metadataStore by lazy { PhotoMetadataStore(context) }
    private val driveMetadata by lazy { DriveMetadata(context) }
    private val driveRecents by lazy { DriveRecents(context) }
    private val driveManifest by lazy { DriveManifest(context) }

    private fun open(host: String, port: Int, fingerprint: String, path: String, method: String, token: String?): HttpsURLConnection {
        val pinned = object : X509TrustManager {
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                val actual = SyncRules.hex(MessageDigest.getInstance("SHA-256").digest(chain.first().encoded))
                if (actual != fingerprint) throw CertificateException("This is not the paired computer.")
            }
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = throw CertificateException("Not a server.")
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(pinned), null) }
        return (URL("https://$host:$port$path").openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = tls.socketFactory
            hostnameVerifier = HostnameVerifier { _, _ -> true } // identity is the pinned certificate, not a hostname
            requestMethod = method
            connectTimeout = 4_000
            readTimeout = 120_000
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
    }

    private fun HttpsURLConnection.jsonResult(): JSONObject {
        val ok = responseCode in 200..299
        val text = (if (ok) inputStream else errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val body = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
        if (!ok) throw SyncException(body.optString("error", "The computer answered $responseCode."))
        return body
    }

    private fun postJson(host: String, p: Pairing, path: String, body: JSONObject): JSONObject =
        open(host, p.port, p.fingerprint, path, "POST", p.token).run {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            outputStream.use { it.write(body.toString().toByteArray()) }
            jsonResult().also { disconnect() }
        }

    /** First address that answers wins; a changed network may still reach one of the others. */
    private fun <T> anyHost(hosts: List<String>, call: (String) -> T): T {
        var last: Exception? = null
        for (host in hosts) try { return call(host) } catch (e: SyncException) { throw e } catch (e: Exception) { last = e }
        throw SyncException("Could not reach the computer (${last?.message ?: "no address"}). Is Tetra open on it, on the same Wi-Fi?")
    }

    /**
     * The address the computer answers on *now*. The ones pairing knew come first, and if none of them answer
     * the beacon asks who out there holds the pinned certificate (SYNC_PLAN.md 6k) — a new IP after a router
     * restart, or a different network entirely, stops being the end of the pairing. Whatever answers is
     * remembered, so the next sync starts there.
     */
    private fun reach(p: Pairing): Pairing {
        var last: Exception? = null
        fun tryHost(candidate: Pairing): Pairing? = try {
            postJson(candidate.hosts.first(), candidate, "/have", JSONObject().put("hashes", JSONArray()))
            candidate
        } catch (e: SyncException) { throw e } catch (e: Exception) { last = e; null }

        for (host in p.hosts) tryHost(p.copy(hosts = listOf(host) + (p.hosts - host)))?.let { return it }
        for ((host, port) in SyncDiscovery.find(p.fingerprint)) {
            tryHost(p.copy(hosts = listOf(host) + (p.hosts - host), port = port))?.let { store.savePairing(it); return it }
        }
        throw SyncException("Could not reach the computer (${last?.message ?: "no address"}). Is Tetra open on it, on the same Wi-Fi?")
    }

    /**
     * Scanning hands over both halves at once: we prove we saw the code, and we say who we are — our own
     * certificate fingerprint, the port we listen on and a token the other device sends when it calls us.
     * Between two phones neither side is the client, so a pairing that only travelled one way would leave one
     * of them unable to ever start a sync. A computer simply ignores the extra fields.
     */
    fun pair(qr: PairingQr): Pairing = anyHost(qr.hosts) { host ->
        val ourToken = SyncRules.hex(ByteArray(32).also(java.security.SecureRandom()::nextBytes))
        val ourFingerprint = runCatching { SyncServer.fingerprint(SyncServer.identity().second) }.getOrNull()
        val body = open(host, qr.port, qr.fingerprint, "/pair", "POST", null).run {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            val request = JSONObject().put("code", qr.code).put("name", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("port", SyncServer.PORT).put("hosts", JSONArray(SyncServer.lanAddresses()))
                .put("token", ourToken)
                // Android's own answer to "phone or tablet": the narrowest the screen ever gets, in dp. 369 here,
                // 777 on the tablet, and 600 is where Android itself draws the line — so the computer can pick
                // the right picture without asking anyone (SYNC_PLAN.md 6af).
                .put("widthDp", context.resources.configuration.smallestScreenWidthDp)
            ourFingerprint?.let { request.put("fp", it) }
            outputStream.use { it.write(request.toString().toByteArray()) }
            jsonResult().also { disconnect() }
        }
        val hosts = listOf(host) + (qr.hosts - host)
        val name = body.optString("name", qr.name)
        store.savePeer(Peer(name, hosts, qr.port, qr.fingerprint, body.getString("token"), ourToken))
        Pairing(name, hosts, qr.port, qr.fingerprint, body.getString("token")).also(store::savePairing)
    }

    /**
     * The rows this device is to obey. The computer owns them; if it is too old to answer, Send & receive is
     * assumed, which is what every pairing before this change did.
     */
    private fun connections(host: String, p: Pairing): List<SyncConnection> = runCatching {
        val body = open(host, p.port, p.fingerprint, "/connections", "GET", p.token).jsonResult()
        val array = body.optJSONArray("connections") ?: return@runCatching SyncConnection.defaults
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            SyncConnection(o.getString("content"), o.optString("direction", "both"), o.optString("keep", "everything"),
                o.optInt("keepDays", 30).coerceAtLeast(1), o.optBoolean("keepFavorites", true))
        }.ifEmpty { SyncConnection.defaults }
    }.getOrDefault(SyncConnection.defaults).also(store::saveConnections)

    /**
     * Move a list of things, a few at a time, and report each one as it finishes.
     *
     * One file at a time left the link idle between files — most of a small file's time is the round trip, not
     * the bytes — and on a real library that is thousands of round trips spent waiting. Four at once is enough
     * to keep the link busy without turning a phone's Wi-Fi into a queue of stalled sockets.
     *
     * Each item is tried **twice**: a single dropped connection (a phone hopping access points) is not a file
     * that failed, it is the same request a second later. Whatever still fails is named, one line per file, and
     * nothing partial is kept, so the next sync simply asks again.
     *
     * Stop and Pause are checked before each item is picked up, so they land between files, never inside one.
     */
    private suspend fun <T> eachInParallel(
        items: List<T>,
        stage: String,
        checkpoint: suspend () -> Unit,
        progress: (BackupProgress) -> Unit,
        failed: MutableList<String>,
        name: (T) -> String,
        work: suspend (T) -> Unit,
    ): Int = coroutineScope {
        if (items.isEmpty()) return@coroutineScope 0
        val limit = Semaphore(AT_ONCE)
        val done = AtomicInteger(0)
        val succeeded = AtomicInteger(0)
        items.map { item ->
            async(Dispatchers.IO) {
                limit.withPermit {
                    coroutineContext.ensureActive()
                    checkpoint()
                    var result = runCatching { work(item) }
                    if (result.isFailure && result.exceptionOrNull() !is kotlinx.coroutines.CancellationException) {
                        delay(1_000)
                        result = runCatching { work(item) }
                    }
                    result.onSuccess { succeeded.incrementAndGet() }
                    progress(BackupProgress(stage, done.incrementAndGet(), items.size))
                    result.exceptionOrNull()?.let { failure ->
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        synchronized(failed) { failed += "${name(item)}: ${failure.message}" }
                    }
                }
            }
        }.awaitAll()
        succeeded.get()
    }

    private fun sha256(entry: Entry): String {
        store.cachedHash(entry.photoKey)?.let { return it }
        val digest = MessageDigest.getInstance("SHA-256")
        checkNotNull(context.contentResolver.openInputStream(checkNotNull(entry.contentUri))) { "Cannot read ${entry.name}." }.use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return SyncRules.hex(digest.digest()).also { store.saveHash(entry.photoKey, it) }
    }

    /**
     * A copy that notices Stop. `copyTo` hands a whole 400MB video to the socket before anything looks at the
     * coroutine again, which is why Stop did nothing until the video in flight had finished (24 Sept).
     * ponytail: a read already blocked on a dead socket still waits out readTimeout; closing the connection
     * from outside is the fix if that ever matters.
     */
    private suspend fun pump(input: java.io.InputStream, output: java.io.OutputStream, digest: MessageDigest? = null) {
        val buffer = ByteArray(256 * 1024)
        while (true) {
            coroutineContext.ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            digest?.update(buffer, 0, n)
            output.write(buffer, 0, n)
        }
    }

    /**
     * Trash → Purgatory → gone (SYNC_PLAN.md D6). Android deletes a trashed photo by itself when its 30 days are up;
     * one that is within [HAND_OVER_DAYS] of that is sent to the purgatory on the computer's drive instead, and
     * then left for Android to delete. If the computer has no drive right now it is trashed again, which starts
     * Android's clock again, so nothing expires unsent. Files' Trash has no clock of its own: an item there past
     * 30 days is sent the same way and then deleted here. Emptying a Trash by hand stays a plain delete.
     */
    private suspend fun handOverTrash(host: String, p: Pairing, failed: MutableList<String>) {
        val resolver = context.contentResolver
        val soon = System.currentTimeMillis() / 1000 + HAND_OVER_DAYS * 86_400L
        var noDrive = false
        for (collection in listOf(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI)) {
            val args = android.os.Bundle().apply {
                putInt(android.provider.MediaStore.QUERY_ARG_MATCH_TRASHED, android.provider.MediaStore.MATCH_ONLY)
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${android.provider.MediaStore.MediaColumns.DATE_EXPIRES} < ? AND ${android.provider.MediaStore.MediaColumns.VOLUME_NAME} = ?")
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(soon.toString(), android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY))
            }
            val columns = arrayOf(android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.provider.MediaStore.MediaColumns.SIZE)
            val items = resolver.query(collection, columns, args, null)?.use { c -> buildList { while (c.moveToNext()) {
                add(Triple(android.content.ContentUris.withAppendedId(collection, c.getLong(0)), (c.getString(2) ?: "").trim('/') + "/" + (c.getString(1) ?: "unnamed"), c.getLong(3)))
            } } }.orEmpty()
            for ((uri, origin, size) in items) {
                coroutineContext.ensureActive()
                if (store.handedOver(uri.toString())) continue
                val sha = MessageDigest.getInstance("SHA-256").let { d -> checkNotNull(resolver.openInputStream(uri)).use { input ->
                    val buffer = ByteArray(256 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; d.update(buffer, 0, n) }
                }; SyncRules.hex(d.digest()) }
                // Nothing is sent that the server or its backup already holds (asked 2026-09-25): Android may let it go.
                val held = postJson(host, p, "/held", JSONObject().put("hashes", JSONArray(listOf(sha)))).optJSONArray("held")
                if (held != null && held.length() > 0) { store.markHandedOver(uri.toString()); continue }
                if (noDrive) { retrash(uri); continue }
                when (sendToPurgatory(host, p, sha, "photo", origin, size) { checkNotNull(resolver.openInputStream(uri)) }) {
                    200 -> store.markHandedOver(uri.toString())
                    503 -> { noDrive = true; retrash(uri) }
                    else -> failed += "$origin: not taken by the purgatory"
                }
            }
        }
        // Files' Trash, by when each item went in (a move into Trash/ changes its ctime and nothing else).
        val trash = File(TetraFolder.root(android.os.Environment.getExternalStorageDirectory()), "Trash")
        val cutoff = System.currentTimeMillis() / 1000 - FILES_TRASH_DAYS * 86_400L
        if (!noDrive) for (item in trash.listFiles().orEmpty()) {
            if (android.system.Os.lstat(item.path).st_ctime > cutoff) continue
            val all = item.walkTopDown().filter { it.isFile }.toList()
            val ok = all.all { f ->
                val sha = MessageDigest.getInstance("SHA-256").let { d -> f.inputStream().use { input ->
                    val buffer = ByteArray(256 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; d.update(buffer, 0, n) }
                }; SyncRules.hex(d.digest()) }
                val code = sendToPurgatory(host, p, sha, "file", f.relativeTo(trash).invariantSeparatorsPath, f.length()) { f.inputStream() }
                if (code == 503) noDrive = true
                code == 200
            }
            if (ok) item.deleteRecursively() else if (noDrive) break else failed += "Files Trash/${item.name}: not taken by the purgatory"
        }
    }

    /** One item to the purgatory; the HTTP status (200 taken, 503 no drive there now). */
    private suspend fun sendToPurgatory(host: String, p: Pairing, sha: String, kind: String, origin: String, size: Long, source: () -> java.io.InputStream): Int {
        val c = open(host, p.port, p.fingerprint, "/purgatory/$sha?kind=$kind&path=${URLEncoder.encode(origin, "UTF-8")}", "PUT", p.token)
        c.doOutput = true
        c.setFixedLengthStreamingMode(size)
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.outputStream.use { out -> source().use { pump(it, out) } }
        return c.responseCode.also { c.disconnect() }
    }

    /** Trashed again: Android sets a fresh 30 days. Needs write access to the item, which Media management gives. */
    private fun retrash(uri: Uri) {
        runCatching {
            context.contentResolver.update(uri, ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null)
        }.onFailure { android.util.Log.w("Tetra", "Could not restart the Trash clock for $uri: ${it.message}") }
    }

    private suspend fun uploadOne(host: String, p: Pairing, sha: String, e: Entry) {
        val c = open(host, p.port, p.fingerprint, SyncRules.blobPath(sha, e.relativePath, e.name, e.takenMillis), "PUT", p.token)
        c.doOutput = true
        c.setFixedLengthStreamingMode(e.sizeBytes)
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.outputStream.use { out -> checkNotNull(context.contentResolver.openInputStream(e.contentUri!!)).use { pump(it, out) } }
        val receipt = c.jsonResult().also { c.disconnect() }
        if (receipt.optString("sha256") != sha || !receipt.optBoolean("verified")) throw SyncException("No verified receipt.")
        store.saveReceipt(e.photoKey, sha, receipt.getString("path"))
    }

    /**
     * One sync, in whichever directions the computer's connection rows allow (SYNC_PLAN.md 6i and 6j).
     *
     * Sending is what it always was: every gallery photo and video the computer does not have, each verified by
     * its SHA-256 there. Receiving is the same idea read backwards — what the computer holds and this phone does
     * not. Nothing is deleted on either side by either half; a Copy adds, and that is all it does.
     */
    suspend fun backUp(entries: List<Entry>, checkpoint: suspend () -> Unit = {}, progress: (BackupProgress) -> Unit): BackupResult {
        val paired = store.pairing() ?: throw SyncException("Pair with a computer first.")
        val p = reach(paired)
        val host = p.hosts.first()
        val failed = mutableListOf<String>()
        val bySha = linkedMapOf<String, Entry>()
        entries.forEachIndexed { i, e ->
            coroutineContext.ensureActive()
            checkpoint() // Pause waits here, between files, so a half-sent photo is never left behind
            if (i % 10 == 0) progress(BackupProgress("Checking photos", i, entries.size))
            runCatching { bySha.putIfAbsent(sha256(e), e) }.onFailure { failed += "${e.name}: ${it.message}" }
        }
        // What this device holds, in words rather than in hashes (SYNC_PLAN.md 6ae). The computer can name and
        // size everything it was given; for a photo it was never given, a hash is all it had — which is exactly
        // the photo worth warning about. Best-effort and never fatal: a computer that does not know the endpoint
        // simply answers 404 and everything else about this sync is unaffected.
        runCatching {
            bySha.entries.chunked(2_000).forEach { chunk ->
                val items = JSONArray(chunk.map { (sha, e) ->
                    JSONObject().put("sha256", sha).put("name", e.name).put("size", e.sizeBytes)
                        .put("video", e.isVideo).put("takenAt", e.takenMillis)
                })
                postJson(host, p, "/inventory", JSONObject().put("items", items))
            }
        }
        val rows = connections(host, p).associateBy { it.content }
        val photos = rows["photos"] ?: SyncConnection("photos", "both", "everything")
        val files = rows["files"] ?: SyncConnection("files", "both", "everything")
        // Asking what the computer is missing is only worth the round trip if we are allowed to send it.
        val missing = if (!photos.sends) emptyList() else bySha.keys.chunked(500).flatMap { batch ->
            val r = postJson(host, p, "/have", JSONObject().put("hashes", JSONArray(batch))).getJSONArray("missing")
            (0 until r.length()).map(r::getString)
        }
        val alreadyThere = if (photos.sends) bySha.size - missing.size else 0
        val sent = eachInParallel(missing, "Sending", checkpoint, progress, failed, { bySha.getValue(it).name }) { sha ->
            uploadOne(host, p, sha, bySha.getValue(sha))
        }
        // A Move keeps a window here — the last keepDays days, and favorites unless told otherwise — and offers the
        // rest (SYNC_PLAN.md D6, asked 2026-09-25). Nothing is removed here and now: a photo leaves this phone through
        // Android's own request, which needs a screen this service does not have, so the Sync page asks. Offered is
        // what the computer confirmed holding *in this sync* — its /have answer is checked against its disk — or what
        // it just received with a verified receipt; not only what this phone once sent, which left out every photo
        // that came from the computer in the first place.
        if (photos.sends && photos.keep == "nothing") {
            val onComputer = (bySha.keys - missing.toSet()) + store.receiptShas()
            val cutoff = System.currentTimeMillis() - photos.keepDays * 86_400_000L
            val favorites = if (photos.keepFavorites) metadataStore.states(bySha.values.map { it.photoKey }).filterValues { it.favorite }.keys else emptySet()
            store.forgetQueued(store.queuedForRemoval()) // the window may have changed since the last sync
            bySha.forEach { (sha, entry) ->
                if (sha in onComputer && entry.takenMillis in 1 until cutoff && entry.photoKey !in favorites) store.queueForRemoval(entry.photoKey, sha)
            }
        } else {
            // The card says Copy again. An offer to move photos off this phone must not outlive the rule that
            // made it, or a setting changed on the computer leaves a question here that nothing can answer.
            store.forgetQueued(store.queuedForRemoval())
        }
        // What the computer has and this phone does not, before the metadata pass, so a photo that has just
        // arrived already has its favourites, its people and its collections when that pass runs.
        var received = 0
        // "Already here" includes folders you left out of Tetra: a photo in DCIM/Creation is still on this phone,
        // and fetching it again because it is not shown would write a duplicate on every sync.
        val alsoHere = listDeviceMedia(context).mapNotNullTo(HashSet()) { store.cachedHash(it.photoKey) }
        if (photos.receives) runCatching { received += pullPhotos(host, p, bySha.keys + alsoHere, failed, checkpoint, progress) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else failed += "Photos from the computer: ${it.message}" }
        // Files (the Drive folder) go over the same connection, by path rather than by gallery entry.
        runCatching { received += syncFiles(host, p, files, failed, checkpoint, progress) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else failed += "Drive files: ${it.message}" }
        // Trash items about to be deleted by Android (or past their days in Files' Trash) go to the purgatory on the
        // computer's drive instead (SYNC_PLAN.md D6).
        runCatching { handOverTrash(host, p, failed) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else failed += "Trash to the purgatory: ${it.message}" }
        // The library, and where it is, as the computer sees it now — for this device's Sync page.
        runCatching { store.saveOverview(open(host, p.port, p.fingerprint, "/overview", "GET", p.token).jsonResult().toString()) }
        progress(BackupProgress("Done", missing.size, missing.size))
        store.setLastBackup(System.currentTimeMillis())
        // Best-effort — photos that did cross should not be reported as failed over this — but never silent:
        // a metadata sync that fails looks exactly like one that had nothing to say, and on 2026-09-23 that hid
        // a whole library's names failing to come back.
        runCatching { syncMetadata(host, p, listPhotos(context)) }.onFailure { failed += "Names, people and tags: ${it.message ?: "failed"}" }
        return BackupResult(bySha.size, sent, alreadyThere, failed, received)
    }

    /**
     * Photos the computer holds and this phone does not (SYNC_PLAN.md 6i). The phone says what it has — the
     * mirror image of `/have` — and the computer answers with what it could send.
     *
     * Each one is written straight into MediaStore as a **pending** item, which is this protocol's `.part` by
     * another name: a pending item is not in the gallery, is hashed as it is written, and is published only if
     * the hash is the one that was asked for. Anything else is deleted and counted as failed. The hash is saved
     * against the new photo's key at once, so the metadata pass right after this one can already place its
     * favourites, its people and its collections.
     */
    private suspend fun pullPhotos(host: String, p: Pairing, known: Collection<String>, failed: MutableList<String>, checkpoint: suspend () -> Unit, progress: (BackupProgress) -> Unit): Int {
        forgetHalfWrittenPhotos()
        val answer = postJson(host, p, "/library/manifest", JSONObject().put("hashes", JSONArray(known.toList())))
        val send = answer.optJSONArray("send") ?: return 0
        // Never bring back what this phone deleted. A receipt says "I gave the computer this photo"; if it is
        // not here any more, it was removed here on purpose, and a sync that undid that would be worse than one
        // that never ran. Resurrection is the same sin as deletion, read backwards.
        val sentFromHere = store.receiptShas()
        val wanted = (0 until send.length()).map(send::getJSONObject).filter { it.optString("sha256") !in sentFromHere }
        return eachInParallel(wanted, "Receiving photos", checkpoint, progress, failed, { it.optString("name") }) { item ->
            receivePhoto(host, p, item)
        }
    }

    /**
     * A photo half received when the app was killed is a **pending** MediaStore item: invisible in the gallery,
     * but ours and still taking up room. Android clears them itself after a week; a sync clears its own after an
     * hour, which is long enough that a download running right now is never mistaken for wreckage.
     */
    private fun forgetHalfWrittenPhotos() {
        val cutoff = (System.currentTimeMillis() - 60 * 60_000) / 1000
        for (collection in listOf(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        )) runCatching {
            val query = android.os.Bundle().apply {
                putInt(android.provider.MediaStore.QUERY_ARG_MATCH_PENDING, android.provider.MediaStore.MATCH_INCLUDE)
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${android.provider.MediaStore.MediaColumns.IS_PENDING} = 1 AND ${android.provider.MediaStore.MediaColumns.DATE_ADDED} < $cutoff")
            }
            context.contentResolver.query(collection, arrayOf(android.provider.MediaStore.MediaColumns._ID), query, null)?.use { cursor ->
                while (cursor.moveToNext()) runCatching {
                    context.contentResolver.delete(android.content.ContentUris.withAppendedId(collection, cursor.getLong(0)), null, null)
                }
            }
        }
    }

    private suspend fun receivePhoto(host: String, p: Pairing, item: JSONObject) {
        val sha = item.getString("sha256")
        val name = item.optString("name").takeIf { it.isNotBlank() && '/' !in it && it != "." && it != ".." }
            ?: throw SyncException("The computer sent a photo with no usable name.")
        val video = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("mp4", "mkv", "mov", "3gp", "webm", "avi")
        val folder = SyncRules.incomingFolder(item.optString("path"), video)
        val taken = item.optLong("modified").takeIf { it > 0 }
        val collection = if (video) android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            taken?.let { put(android.provider.MediaStore.MediaColumns.DATE_TAKEN, it) }
        }
        val uri = resolver.insert(collection, values) ?: throw SyncException("Android would not make room for $name.")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val connection = open(host, p.port, p.fingerprint, "/blob/$sha", "GET", p.token)
            connection.inputStream.use { input ->
                checkNotNull(resolver.openOutputStream(uri)) { "Cannot write $name." }.use { output -> pump(input, output, digest) }
            }
            connection.disconnect()
            if (SyncRules.hex(digest.digest()) != sha) throw SyncException("It changed on the way; nothing was kept.")
            publishReceivedPhoto(resolver, uri, taken)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        // The key the rest of the app knows this photo by, read back from MediaStore rather than guessed: the
        // name may have gained a "(1)" on the way in, and the key is built from the name it actually has.
        resolver.query(uri, arrayOf(
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
            android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
            android.provider.MediaStore.MediaColumns.SIZE,
        ), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) store.saveHash(
                PhotoMetadataRules.stableKey(uri.toString(), cursor.getString(1), cursor.getString(0), cursor.getLong(2)), sha,
            )
        }
    }

    /**
     * Everything the user made, both ways (SYNC_PLAN.md phases 6a–6c): document answers, favorites,
     * collections, search labels, people's names and the face → person grouping. Records are keyed by the
     * photo's SHA-256 and by UUIDs, so the two libraries agree without sharing any local id; newest
     * `updatedAt` wins per record, and removals travel as tombstones rather than as silence.
     *
     * The phone is the source of truth for document classification (the desktop cannot tell a document from a
     * text-heavy photo on pixels alone) and for its own face detection; the desktop may still rename a person.
     */
    private fun syncMetadata(host: String, p: Pairing, entries: List<Entry>) {
        val keysBySha = entries.mapNotNull { entry -> runCatching { sha256(entry) to entry.photoKey }.getOrNull() }
            .groupBy({ it.first }, { it.second })
        val keyFor = { record: JSONObject -> keysBySha[record.optString("sha256")]?.firstOrNull() }
        val sizes = entries.associate { it.photoKey to (it.width to it.height) }
        val sha = { key: String -> store.cachedHash(key) }
        val labels = metadataStore.allLabels()

        val body = JSONObject()
            .put("documents", JSONArray(metadataStore.documentRecords().mapNotNull { r -> sha(r.photoKey)?.let {
                JSONObject().put("sha256", it).put("type", r.type).put("confidence", r.confidence).put("userVerified", r.userVerified).put("updatedAt", r.updatedAt)
            } }))
            .put("favorites", JSONArray(metadataStore.favoriteRecords().mapNotNull { r -> sha(r.photoKey)?.let {
                JSONObject().put("sha256", it).put("favorite", r.favorite).put("updatedAt", r.updatedAt)
            } }))
            .put("collections", JSONArray(metadataStore.collectionRecords().map { r ->
                JSONObject().put("uuid", r.uuid).put("name", r.name).put("deleted", r.deleted)
                    .put("hidden", r.hiddenFromGallery).put("updatedAt", r.updatedAt)
            }))
            .put("collectionItems", JSONArray(metadataStore.collectionItemRecords().mapNotNull { r -> sha(r.photoKey)?.let {
                JSONObject().put("collection", r.collectionUuid).put("sha256", it).put("deleted", r.deleted).put("updatedAt", r.updatedAt)
            } }))
            .put("labels", JSONArray(labels.mapNotNull { (key, list) -> sha(key)?.let {
                JSONObject().put("sha256", it).put("labels", JSONArray(list))
            } }))
            .put("people", JSONArray(metadataStore.personRecords().map { r ->
                JSONObject().put("uuid", r.uuid).put("name", r.name).put("updatedAt", r.updatedAt)
                    .put("cover", r.coverUuid ?: JSONObject.NULL).put("hidden", r.hidden)
            }))
            .put("faces", JSONArray(metadataStore.faceRecords().mapNotNull { r -> faceJson(r, sha(r.photoKey), sizes[r.photoKey]) }))
            // Answers to Help organize. A question answered here must stop being asked over there, or the same
            // face is put to the user twice — and "no" is the answer that otherwise leaves no trace at all,
            // because it changes nothing about where the face sits.
            .put("reviews", JSONArray(metadataStore.reviewRecords().map { r ->
                JSONObject().put("face", r.faceUuid).put("person", r.personUuid).put("state", r.state).put("updatedAt", r.updatedAt)
            }))
            // Files (tags, favorites, folder colours) are keyed by their Drive-relative path, not by content.
            .put("files", JSONArray(driveMetadata.records().map { r ->
                JSONObject().put("path", r.path).put("favorite", r.favorite).put("color", r.color ?: JSONObject.NULL)
                    .put("tags", JSONArray(r.tags.toList())).put("updatedAt", r.updatedAt)
            }))
            .put("fileRecents", JSONArray(driveRecents.all().map { JSONObject().put("path", it.relativePath).put("openedAt", it.openedAt) }))
            .put("viewSettings", JSONObject()
                .put("hideScreenshots", metadataStore.hidesScreenshotsFromGallery())
                .put("hideDocuments", metadataStore.hidesDocumentsFromGallery())
                .put("updatedAt", metadataStore.viewSettingsUpdatedAt()))
        postJson(host, p, "/metadata", body)

        val since = store.metadataSince()
        val pulled = open(host, p.port, p.fingerprint, "/metadata?since=$since", "GET", p.token).jsonResult()
        pulled.each("documents") { d ->
            keyFor(d)?.let { metadataStore.applyIncomingDocument(it, d.optString("type", null), d.optDouble("confidence", 0.0).toFloat(), d.optBoolean("userVerified"), d.getLong("updatedAt")) }
        }
        pulled.each("favorites") { d -> keyFor(d)?.let { metadataStore.applyIncomingFavorite(it, d.optBoolean("favorite"), d.getLong("updatedAt")) } }
        pulled.each("collections") { d -> metadataStore.applyIncomingCollection(d.getString("uuid"), d.getString("name"), d.optBoolean("deleted"), d.getLong("updatedAt"), d.optBoolean("hidden")) }
        pulled.each("collectionItems") { d -> keyFor(d)?.let { metadataStore.applyIncomingCollectionItem(d.getString("collection"), it, d.optBoolean("deleted"), d.getLong("updatedAt")) } }
        pulled.each("labels") { d -> keyFor(d)?.let { key ->
            val list = d.optJSONArray("labels") ?: JSONArray()
            metadataStore.applyIncomingLabels(key, (0 until list.length()).map(list::getString))
        } }
        pulled.each("people") { d ->
            metadataStore.applyIncomingPerson(d.getString("uuid"), d.getString("name"), d.getLong("updatedAt"), d.optString("cover").ifEmpty { null }, d.optBoolean("hidden"))
        }
        // A face the computer found but this phone's detector missed arrives whole — box, embedding and all —
        // and is kept, so the photo still shows up under that person here (SYNC_PLAN.md 6m). The box comes as
        // fractions of the upright photo and goes back into the analyser's own pixels on the way in.
        pulled.each("faces") { d ->
            val box = d.optJSONArray("box")?.takeIf { it.length() == 4 }
            val embedding = d.optString("embedding").takeIf { it.isNotBlank() }
                ?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }.getOrNull() }
                ?.takeIf { d.optString("model") == FACE_EMBEDDING_MODEL } // another model's vector means nothing here
            keysBySha[d.optString("sha256")]?.forEach { key ->
                val size = sizes[key]?.let { SyncRules.analysisSize(it.first, it.second) }
                val bounds = if (size != null && box != null) android.graphics.Rect(
                    (box.getDouble(0) * size.first).toInt(), (box.getDouble(1) * size.second).toInt(),
                    (box.getDouble(2) * size.first).toInt(), (box.getDouble(3) * size.second).toInt(),
                ) else null
                metadataStore.applyIncomingFace(
                    d.getString("uuid"), d.optString("person").ifEmpty { null }, d.getLong("updatedAt"),
                    photoKey = key, bounds = bounds, embedding = embedding, quality = d.optDouble("quality", 1.0).toFloat(),
                )
            }
        }
        pulled.each("reviews") { d ->
            metadataStore.applyIncomingReview(d.getString("face"), d.getString("person"), d.optString("state"), d.getLong("updatedAt"))
        }
        pulled.each("files") { d ->
            val tags = d.optJSONArray("tags") ?: JSONArray()
            driveMetadata.applyIncoming(DriveMetaRecord(
                d.getString("path"), d.optBoolean("favorite"), d.optString("color").ifEmpty { null },
                (0 until tags.length()).map(tags::getString).toSet(), d.getLong("updatedAt"),
            ))
        }
        pulled.optJSONObject("viewSettings")?.let { v ->
            metadataStore.applyIncomingViewSettings(v.optBoolean("hideScreenshots"), v.optBoolean("hideDocuments"), v.optLong("updatedAt"))
        }
        pulled.optJSONArray("fileRecents")?.let { array ->
            driveRecents.merge((0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { DriveRecent(it.optString("path"), it.optLong("openedAt")) }
            }.filter { it.openedAt > 0 })
        }
        store.setLastMetadataSync(System.currentTimeMillis())
    }

    /**
     * The Files module (SYNC_PLAN.md phase 6e): the phone offers every file under Drive with its SHA-256, the
     * computer moves its own copy of anything that only changed place — a rename, or a move into Drive/Trash/ —
     * and asks for the rest. Nothing on the phone is changed, and nothing is ever deleted on either side.
     */
    private suspend fun syncFiles(host: String, p: Pairing, connection: SyncConnection, failed: MutableList<String>, checkpoint: suspend () -> Unit, progress: (BackupProgress) -> Unit): Int {
        val root = TetraFolder.root(android.os.Environment.getExternalStorageDirectory())
        if (!root.isDirectory) return 0
        val entries = driveManifest.entries(root)
        val manifest = JSONArray(entries.map { JSONObject().put("path", it.relativePath).put("sha256", it.sha256).put("size", it.sizeBytes).put("modified", it.modified) })
        val answer = postJson(host, p, "/files/manifest", JSONObject().put("files", manifest))
        if (connection.sends) {
            val want = answer.optJSONArray("want") ?: JSONArray()
            val byPath = entries.associateBy { it.relativePath }
            val mine = (0 until want.length()).mapNotNull { byPath[want.getString(it)] }
            val sent = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            eachInParallel(mine, "Sending files", checkpoint, progress, failed, { it.relativePath }) { entry ->
                uploadFile(host, p, entry, File(root, entry.relativePath))
                sent += entry.relativePath
            }
            // A Move keeps a window of files here and deletes the older ones — files only; system folders stay,
            // regular folders left empty go (asked 2026-09-25). Only what the computer confirmed holding in this sync
            // goes: it did not ask for it, or it just received it verified.
            if (connection.keep == "nothing") {
                val asked = (0 until want.length()).mapTo(HashSet()) { want.getString(it) }
                val cutoff = System.currentTimeMillis() - connection.keepDays * 86_400_000L
                val favorites = if (connection.keepFavorites) driveMetadata.records().filter { it.favorite }.mapTo(HashSet()) { it.path } else emptySet()
                entries.filter { e ->
                    !e.relativePath.startsWith("Trash/") && (e.relativePath !in asked || e.relativePath in sent) &&
                        e.modified in 1 until cutoff && e.relativePath !in favorites
                }.also { going ->
                    // Deleted, not trashed: the computer holds each one (asked 2026-09-25 — a Trash only keeps the room).
                    going.forEach { e -> runCatching { check(DriveRules.file(root, e.relativePath).delete()) { "could not delete it" } }.onFailure { failed += "${e.relativePath}: ${it.message}" } }
                    DriveRules.removeEmptyFolders(root, going.map { DriveRules.parent(it.relativePath) }.toSet())
                }
            }
        }
        if (!connection.receives) return 0
        // A move the computer made is followed, never downloaded: the bytes are already here, under another name.
        val moveTo = answer.optJSONArray("moveTo") ?: JSONArray()
        for (i in 0 until moveTo.length()) runCatching {
            val move = moveTo.getJSONObject(i)
            moveFile(root, move.getString("from"), move.getString("to"))
        }.onFailure { failed += "Moving a file: ${it.message}" }

        val have = answer.optJSONArray("have") ?: JSONArray()
        val incoming = (0 until have.length()).map(have::getJSONObject)
        return eachInParallel(incoming, "Receiving files", checkpoint, progress, failed, { it.optString("path") }) { file ->
            receiveFile(host, p, root, file)
        }
    }

    /** The same rules as a file arriving on the computer: verified into a .part, and nothing is ever replaced. */
    private suspend fun receiveFile(host: String, p: Pairing, root: File, file: JSONObject) {
        val sha = file.getString("sha256")
        val rel = file.getString("path")
        val target = DriveRules.newFile(root, rel)
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, ".${target.name}.${java.util.UUID.randomUUID()}.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val connection = open(host, p.port, p.fingerprint, "/file/$sha?path=${URLEncoder.encode(rel, "UTF-8")}", "GET", p.token)
            connection.inputStream.use { input -> part.outputStream().use { output -> pump(input, output, digest) } }
            connection.disconnect()
            if (SyncRules.hex(digest.digest()) != sha) throw SyncException("It changed on the way; nothing was kept.")
            if (!part.renameTo(target)) throw SyncException("Could not put it in place.")
            file.optLong("modified").takeIf { it > 0 }?.let { target.setLastModified(it) }
        } finally {
            part.delete()
        }
    }

    /** Follows a move the computer made. It never overwrites: if something is already there, both are kept. */
    private fun moveFile(root: File, from: String, to: String) {
        val source = DriveRules.file(root, from)
        val target = DriveRules.newFile(root, to)
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) throw SyncException("Could not move ${from} to ${to}.")
    }

    private suspend fun uploadFile(host: String, p: Pairing, entry: DriveFileEntry, file: File) {
        val path = "/file/${entry.sha256}?path=${URLEncoder.encode(entry.relativePath, "UTF-8")}&modified=${entry.modified}"
        val c = open(host, p.port, p.fingerprint, path, "PUT", p.token)
        c.doOutput = true
        c.setFixedLengthStreamingMode(entry.sizeBytes)
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.outputStream.use { out -> file.inputStream().use { pump(it, out) } }
        val receipt = c.jsonResult().also { c.disconnect() }
        if (!receipt.optBoolean("verified")) throw SyncException("No verified receipt for ${entry.relativePath}.")
    }

    /** Boxes travel as fractions of the upright photo, so the two apps' different decode sizes cancel out. */
    private fun faceJson(r: FaceRecord, sha256: String?, size: Pair<Int, Int>?): JSONObject? {
        if (sha256 == null) return null
        val box = size?.let { SyncRules.analysisSize(it.first, it.second) }?.let { (w, h) ->
            if (w <= 0 || h <= 0) null
            else JSONArray(listOf(r.bounds.left.toDouble() / w, r.bounds.top.toDouble() / h, r.bounds.right.toDouble() / w, r.bounds.bottom.toDouble() / h))
        }
        return JSONObject()
            .put("uuid", r.uuid).put("sha256", sha256).put("box", box ?: JSONObject.NULL)
            .put("embedding", android.util.Base64.encodeToString(r.embedding, android.util.Base64.NO_WRAP))
            .put("model", FACE_EMBEDDING_MODEL).put("quality", r.quality)
            .put("person", r.personUuid ?: JSONObject.NULL).put("updatedAt", r.updatedAt)
    }


    private inline fun JSONObject.each(name: String, block: (JSONObject) -> Unit) {
        val array = optJSONArray(name) ?: return
        for (i in 0 until array.length()) runCatching { block(array.getJSONObject(i)) }
    }
}
