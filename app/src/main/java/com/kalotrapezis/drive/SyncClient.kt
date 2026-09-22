package com.kalotrapezis.drive

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import kotlinx.coroutines.delay
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
/** The desktop stores the same string (faces.js EMBEDDING_MODEL): same weights, same eye alignment, one space. */
internal const val FACE_EMBEDDING_MODEL = "mobilefacenet-192-eyes38x44-74x44"

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

/** Separate from photo_metadata.db: content hashes (by the gallery's photo key) and receipts from the computer. */
internal class SyncStore(private val context: Context) : SQLiteOpenHelper(context, "sync.db", null, 1) {
    private val prefs = context.getSharedPreferences("sync_pairing", Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE identity (photo_key TEXT PRIMARY KEY, sha256 TEXT NOT NULL)")
        db.execSQL("CREATE TABLE receipts (photo_key TEXT PRIMARY KEY, sha256 TEXT NOT NULL, path TEXT NOT NULL, sent_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
    fun lastBackup(): Long = prefs.getLong("last_backup", 0)
    fun setLastBackup(at: Long) = prefs.edit().putLong("last_backup", at).apply()

    /** One line for the Home card. */
    fun summary(): String = pairing()?.let { "Paired with ${it.name} · ${receiptCount()} photos on the computer" } ?: "No computer paired yet"

    fun cachedHash(photoKey: String): String? = readableDatabase.rawQuery("SELECT sha256 FROM identity WHERE photo_key = ?", arrayOf(photoKey)).use { if (it.moveToFirst()) it.getString(0) else null }
    fun photoKeyForSha(sha256: String): String? = readableDatabase.rawQuery("SELECT photo_key FROM identity WHERE sha256 = ? LIMIT 1", arrayOf(sha256)).use { if (it.moveToFirst()) it.getString(0) else null }
    fun saveHash(photoKey: String, sha256: String) { writableDatabase.insertWithOnConflict("identity", null, ContentValues().apply { put("photo_key", photoKey); put("sha256", sha256) }, SQLiteDatabase.CONFLICT_REPLACE) }
    fun lastMetadataSync(): Long = prefs.getLong("last_metadata_sync", 0)
    fun setLastMetadataSync(at: Long) = prefs.edit().putLong("last_metadata_sync", at).apply()
    fun saveReceipt(photoKey: String, sha256: String, path: String) { writableDatabase.insertWithOnConflict("receipts", null, ContentValues().apply {
        put("photo_key", photoKey); put("sha256", sha256); put("path", path); put("sent_at", System.currentTimeMillis())
    }, SQLiteDatabase.CONFLICT_REPLACE) }
    fun receiptCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM receipts", null).use { it.moveToFirst(); it.getInt(0) }
}

internal class SyncException(message: String) : Exception(message)

/** Talks only to the paired computer: HTTPS whose certificate must match the QR's SHA-256 fingerprint. */
internal class SyncClient(private val context: Context, private val store: SyncStore) {
    private val metadataStore by lazy { PhotoMetadataStore(context) }

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

    private fun uploadOne(host: String, p: Pairing, sha: String, e: Entry) {
        val c = open(host, p.port, p.fingerprint, SyncRules.blobPath(sha, e.relativePath, e.name, e.takenMillis), "PUT", p.token)
        c.doOutput = true
        c.setFixedLengthStreamingMode(e.sizeBytes)
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.outputStream.use { out -> checkNotNull(context.contentResolver.openInputStream(e.contentUri!!)).use { it.copyTo(out, 256 * 1024) } }
        val receipt = c.jsonResult().also { c.disconnect() }
        if (receipt.optString("sha256") != sha || !receipt.optBoolean("verified")) throw SyncException("No verified receipt.")
        store.saveReceipt(e.photoKey, sha, receipt.getString("path"))
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
            // One retry: a single dropped connection (e.g. the phone hopping Wi-Fi networks) shouldn't
            // count a file as failed when the same request would succeed a second later.
            var result = runCatching { uploadOne(host, p, sha, e) }
            if (result.isFailure) { delay(1_000); result = runCatching { uploadOne(host, p, sha, e) } }
            result.onSuccess { sent++ }.onFailure { failed += "${e.name}: ${it.message}" }
        }
        progress(BackupProgress("Done", missing.size, missing.size))
        store.setLastBackup(System.currentTimeMillis())
        runCatching { syncMetadata(host, p, entries) } // best-effort: a blob backup that succeeded should not be reported as failed over this
        return BackupResult(bySha.size, sent, bySha.size - missing.size, failed)
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
                JSONObject().put("uuid", r.uuid).put("name", r.name).put("deleted", r.deleted).put("updatedAt", r.updatedAt)
            }))
            .put("collectionItems", JSONArray(metadataStore.collectionItemRecords().mapNotNull { r -> sha(r.photoKey)?.let {
                JSONObject().put("collection", r.collectionUuid).put("sha256", it).put("deleted", r.deleted).put("updatedAt", r.updatedAt)
            } }))
            .put("labels", JSONArray(labels.mapNotNull { (key, list) -> sha(key)?.let {
                JSONObject().put("sha256", it).put("labels", JSONArray(list))
            } }))
            .put("people", JSONArray(metadataStore.personRecords().map { r ->
                JSONObject().put("uuid", r.uuid).put("name", r.name).put("updatedAt", r.updatedAt)
            }))
            .put("faces", JSONArray(metadataStore.faceRecords().mapNotNull { r -> faceJson(r, sha(r.photoKey), sizes[r.photoKey]) }))
        postJson(host, p, "/metadata", body)

        val since = store.lastMetadataSync()
        val pulled = open(host, p.port, p.fingerprint, "/metadata?since=$since", "GET", p.token).jsonResult()
        pulled.each("documents") { d ->
            keyFor(d)?.let { metadataStore.applyIncomingDocument(it, d.optString("type", null), d.optDouble("confidence", 0.0).toFloat(), d.optBoolean("userVerified"), d.getLong("updatedAt")) }
        }
        pulled.each("favorites") { d -> keyFor(d)?.let { metadataStore.applyIncomingFavorite(it, d.optBoolean("favorite"), d.getLong("updatedAt")) } }
        pulled.each("collections") { d -> metadataStore.applyIncomingCollection(d.getString("uuid"), d.getString("name"), d.optBoolean("deleted"), d.getLong("updatedAt")) }
        pulled.each("collectionItems") { d -> keyFor(d)?.let { metadataStore.applyIncomingCollectionItem(d.getString("collection"), it, d.optBoolean("deleted"), d.getLong("updatedAt")) } }
        pulled.each("labels") { d -> keyFor(d)?.let { key ->
            val list = d.optJSONArray("labels") ?: JSONArray()
            metadataStore.applyIncomingLabels(key, (0 until list.length()).map(list::getString))
        } }
        pulled.each("people") { d -> metadataStore.applyIncomingPerson(d.getString("uuid"), d.getString("name"), d.getLong("updatedAt")) }
        pulled.each("faces") { d -> metadataStore.applyIncomingFace(d.getString("uuid"), d.optString("person").ifEmpty { null }, d.getLong("updatedAt")) }
        store.setLastMetadataSync(System.currentTimeMillis())
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

    private fun keyFor(record: JSONObject): String? = store.photoKeyForSha(record.optString("sha256"))

    private inline fun JSONObject.each(name: String, block: (JSONObject) -> Unit) {
        val array = optJSONArray(name) ?: return
        for (i in 0 until array.length()) runCatching { block(array.getJSONObject(i)) }
    }
}
