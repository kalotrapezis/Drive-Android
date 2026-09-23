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
import java.io.File
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
internal class SyncStore(private val context: Context) : SQLiteOpenHelper(context, "sync.db", null, 2) {
    private val prefs = context.getSharedPreferences("sync_pairing", Context.MODE_PRIVATE)

    private companion object {
        const val PEERS = "CREATE TABLE IF NOT EXISTS peers (fp TEXT PRIMARY KEY, name TEXT NOT NULL, hosts TEXT NOT NULL, " +
            "port INTEGER NOT NULL, token TEXT NOT NULL, their_token TEXT NOT NULL, paired_at INTEGER NOT NULL)"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE identity (photo_key TEXT PRIMARY KEY, sha256 TEXT NOT NULL)")
        db.execSQL("CREATE TABLE receipts (photo_key TEXT PRIMARY KEY, sha256 TEXT NOT NULL, path TEXT NOT NULL, sent_at INTEGER NOT NULL)")
        db.execSQL(PEERS)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL(PEERS)
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
            ourFingerprint?.let { request.put("fp", it) }
            outputStream.use { it.write(request.toString().toByteArray()) }
            jsonResult().also { disconnect() }
        }
        val hosts = listOf(host) + (qr.hosts - host)
        val name = body.optString("name", qr.name)
        store.savePeer(Peer(name, hosts, qr.port, qr.fingerprint, body.getString("token"), ourToken))
        Pairing(name, hosts, qr.port, qr.fingerprint, body.getString("token")).also(store::savePairing)
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
        val missing = bySha.keys.chunked(500).flatMap { batch ->
            val r = postJson(host, p, "/have", JSONObject().put("hashes", JSONArray(batch))).getJSONArray("missing")
            (0 until r.length()).map(r::getString)
        }
        var sent = 0
        missing.forEachIndexed { i, sha ->
            coroutineContext.ensureActive()
            checkpoint()
            progress(BackupProgress("Sending", i, missing.size))
            val e = bySha.getValue(sha)
            // One retry: a single dropped connection (e.g. the phone hopping Wi-Fi networks) shouldn't
            // count a file as failed when the same request would succeed a second later.
            var result = runCatching { uploadOne(host, p, sha, e) }
            if (result.isFailure) { delay(1_000); result = runCatching { uploadOne(host, p, sha, e) } }
            result.onSuccess { sent++ }.onFailure { failed += "${e.name}: ${it.message}" }
        }
        // Files (the Drive folder) go over the same connection, by path rather than by gallery entry.
        runCatching { backUpFiles(host, p, progress) }.onFailure { failed += "Drive files: ${it.message}" }
        progress(BackupProgress("Done", missing.size, missing.size))
        store.setLastBackup(System.currentTimeMillis())
        // Best-effort — photos that did cross should not be reported as failed over this — but never silent:
        // a metadata sync that fails looks exactly like one that had nothing to say, and on 2026-09-23 that hid
        // a whole library's names failing to come back.
        runCatching { syncMetadata(host, p, entries) }.onFailure { failed += "Names, people and tags: ${it.message ?: "failed"}" }
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
            }))
            .put("faces", JSONArray(metadataStore.faceRecords().mapNotNull { r -> faceJson(r, sha(r.photoKey), sizes[r.photoKey]) }))
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

        val since = store.lastMetadataSync()
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
        pulled.each("people") { d -> metadataStore.applyIncomingPerson(d.getString("uuid"), d.getString("name"), d.getLong("updatedAt")) }
        // A face the computer found but this phone's detector missed arrives whole — box, embedding and all —
        // and is kept, so the photo still shows up under that person here (SYNC_PLAN.md 6m). The box comes as
        // fractions of the upright photo and goes back into the analyser's own pixels on the way in.
        val keysBySha = entries.mapNotNull { e -> store.cachedHash(e.photoKey)?.let { it to e.photoKey } }.toMap()
        pulled.each("faces") { d ->
            val key = keysBySha[d.optString("sha256")]
            val size = key?.let { sizes[it] }?.let { SyncRules.analysisSize(it.first, it.second) }
            val box = d.optJSONArray("box")?.takeIf { it.length() == 4 }
            val bounds = if (size != null && box != null) android.graphics.Rect(
                (box.getDouble(0) * size.first).toInt(), (box.getDouble(1) * size.second).toInt(),
                (box.getDouble(2) * size.first).toInt(), (box.getDouble(3) * size.second).toInt(),
            ) else null
            val embedding = d.optString("embedding").takeIf { it.isNotBlank() }
                ?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }.getOrNull() }
                ?.takeIf { d.optString("model") == FACE_EMBEDDING_MODEL } // another model's vector means nothing here
            metadataStore.applyIncomingFace(
                d.getString("uuid"), d.optString("person").ifEmpty { null }, d.getLong("updatedAt"),
                photoKey = key, bounds = bounds, embedding = embedding, quality = d.optDouble("quality", 1.0).toFloat(),
            )
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
    private fun backUpFiles(host: String, p: Pairing, progress: (BackupProgress) -> Unit) {
        val root = File(android.os.Environment.getExternalStorageDirectory(), "Drive")
        if (!root.isDirectory) return
        val entries = driveManifest.entries(root)
        val manifest = JSONArray(entries.map { JSONObject().put("path", it.relativePath).put("sha256", it.sha256).put("size", it.sizeBytes) })
        val answer = postJson(host, p, "/files/manifest", JSONObject().put("files", manifest))
        val want = answer.optJSONArray("want") ?: JSONArray()
        val byPath = entries.associateBy { it.relativePath }
        for (i in 0 until want.length()) {
            val entry = byPath[want.getString(i)] ?: continue
            progress(BackupProgress("Sending files", i, want.length()))
            uploadFile(host, p, entry, File(root, entry.relativePath))
        }
    }

    private fun uploadFile(host: String, p: Pairing, entry: DriveFileEntry, file: File) {
        val path = "/file/${entry.sha256}?path=${URLEncoder.encode(entry.relativePath, "UTF-8")}&modified=${entry.modified}"
        val c = open(host, p.port, p.fingerprint, path, "PUT", p.token)
        c.doOutput = true
        c.setFixedLengthStreamingMode(entry.sizeBytes)
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.outputStream.use { out -> file.inputStream().use { it.copyTo(out, 256 * 1024) } }
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

    private fun keyFor(record: JSONObject): String? = store.photoKeyForSha(record.optString("sha256"))

    private inline fun JSONObject.each(name: String, block: (JSONObject) -> Unit) {
        val array = optJSONArray(name) ?: return
        for (i in 0 until array.length()) runCatching { block(array.getJSONObject(i)) }
    }
}
