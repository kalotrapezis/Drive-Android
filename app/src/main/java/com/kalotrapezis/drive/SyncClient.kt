package com.kalotrapezis.drive

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.coroutineContext

/** What the desktop's pairing QR carries (desktop: sync.js startPairing). */
internal data class PairingQr(val name: String, val hosts: List<String>, val port: Int, val fingerprint: String, val code: String)
internal data class Pairing(val name: String, val hosts: List<String>, val port: Int, val fingerprint: String, val token: String)
internal data class BackupProgress(val stage: String, val done: Int, val total: Int)
internal data class BackupResult(val checked: Int, val sent: Int, val alreadyThere: Int, val failed: List<String>)

internal object SyncRules {
    private val hex64 = Regex("^[0-9a-f]{64}$")

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
}

/** Separate from photo_metadata.db: content hashes (by the gallery's photo key) and receipts from the computer. */
internal class SyncStore(private val context: Context) : SQLiteOpenHelper(context, "sync.db", null, 1) {
    private val prefs = context.getSharedPreferences("sync_pairing", Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE identity (photo_key TEXT PRIMARY KEY, sha256 TEXT NOT NULL)")
        db.execSQL("CREATE TABLE receipts (photo_key TEXT PRIMARY KEY, sha256 TEXT NOT NULL, path TEXT NOT NULL, sent_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun pairing(): Pairing? {
        val token = prefs.getString("token", null) ?: return null
        return Pairing(prefs.getString("name", "Computer")!!, prefs.getString("hosts", "")!!.split(',').filter(String::isNotBlank),
            prefs.getInt("port", 0), prefs.getString("fp", "")!!, token)
    }
    fun savePairing(p: Pairing) = prefs.edit().putString("name", p.name).putString("hosts", p.hosts.joinToString(","))
        .putInt("port", p.port).putString("fp", p.fingerprint).putString("token", p.token).apply()
    fun forgetPairing() = prefs.edit().clear().apply()
    fun lastBackup(): Long = prefs.getLong("last_backup", 0)
    fun setLastBackup(at: Long) = prefs.edit().putLong("last_backup", at).apply()

    /** One line for the Home card. */
    fun summary(): String = pairing()?.let { "Paired with ${it.name} · ${receiptCount()} photos on the computer" } ?: "No computer paired yet"

    fun cachedHash(photoKey: String): String? = readableDatabase.rawQuery("SELECT sha256 FROM identity WHERE photo_key = ?", arrayOf(photoKey)).use { if (it.moveToFirst()) it.getString(0) else null }
    fun saveHash(photoKey: String, sha256: String) { writableDatabase.insertWithOnConflict("identity", null, ContentValues().apply { put("photo_key", photoKey); put("sha256", sha256) }, SQLiteDatabase.CONFLICT_REPLACE) }
    fun saveReceipt(photoKey: String, sha256: String, path: String) { writableDatabase.insertWithOnConflict("receipts", null, ContentValues().apply {
        put("photo_key", photoKey); put("sha256", sha256); put("path", path); put("sent_at", System.currentTimeMillis())
    }, SQLiteDatabase.CONFLICT_REPLACE) }
    fun receiptCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM receipts", null).use { it.moveToFirst(); it.getInt(0) }
}

internal class SyncException(message: String) : Exception(message)

/** Talks only to the paired computer: HTTPS whose certificate must match the QR's SHA-256 fingerprint. */
internal class SyncClient(private val context: Context, private val store: SyncStore) {

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
        throw SyncException("Could not reach the computer (${last?.message ?: "no address"}). Is Local Drive open on it, on the same Wi-Fi?")
    }

    fun pair(qr: PairingQr): Pairing = anyHost(qr.hosts) { host ->
        val body = open(host, qr.port, qr.fingerprint, "/pair", "POST", null).run {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            outputStream.use { it.write(JSONObject().put("code", qr.code).put("name", "${Build.MANUFACTURER} ${Build.MODEL}").toString().toByteArray()) }
            jsonResult().also { disconnect() }
        }
        Pairing(body.optString("name", qr.name), listOf(host) + (qr.hosts - host), qr.port, qr.fingerprint, body.getString("token")).also(store::savePairing)
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
     * Copy: every gallery photo and video the computer does not have yet, each verified by its SHA-256 there.
     * Nothing on the phone is changed or deleted.
     */
    suspend fun backUp(entries: List<Entry>, progress: (BackupProgress) -> Unit): BackupResult {
        val p = store.pairing() ?: throw SyncException("Pair with a computer first.")
        val host = anyHost(p.hosts) { h -> postJson(h, p, "/have", JSONObject().put("hashes", JSONArray())); h }
        val failed = mutableListOf<String>()
        val bySha = linkedMapOf<String, Entry>()
        entries.forEachIndexed { i, e ->
            coroutineContext.ensureActive()
            if (i % 10 == 0) progress(BackupProgress("Checking photos", i, entries.size))
            runCatching { bySha.putIfAbsent(sha256(e), e) }.onFailure { failed += "${e.name}: ${it.message}" }
        }
        val missing = bySha.keys.chunked(500).flatMap { batch ->
            val r = postJson(host, p, "/have", JSONObject().put("hashes", JSONArray(batch))).getJSONArray("missing")
            (0 until r.length()).map(r::getString)
        }
        var sent = 0
        missing.forEachIndexed { i, sha ->
            coroutineContext.ensureActive()
            progress(BackupProgress("Sending", i, missing.size))
            val e = bySha.getValue(sha)
            runCatching {
                val c = open(host, p.port, p.fingerprint, SyncRules.blobPath(sha, e.relativePath, e.name, e.takenMillis), "PUT", p.token)
                c.doOutput = true
                c.setFixedLengthStreamingMode(e.sizeBytes)
                c.setRequestProperty("Content-Type", "application/octet-stream")
                c.outputStream.use { out -> checkNotNull(context.contentResolver.openInputStream(e.contentUri!!)).use { it.copyTo(out, 256 * 1024) } }
                val receipt = c.jsonResult().also { c.disconnect() }
                if (receipt.optString("sha256") != sha || !receipt.optBoolean("verified")) throw SyncException("No verified receipt.")
                store.saveReceipt(e.photoKey, sha, receipt.getString("path"))
                sent++
            }.onFailure { failed += "${e.name}: ${it.message}" }
        }
        progress(BackupProgress("Done", missing.size, missing.size))
        store.setLastBackup(System.currentTimeMillis())
        return BackupResult(bySha.size, sent, bySha.size - missing.size, failed)
    }
}
