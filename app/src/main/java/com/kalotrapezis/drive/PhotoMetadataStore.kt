package com.kalotrapezis.drive

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

internal data class PhotoState(val favorite: Boolean = false)
internal data class PhotoLocation(
    val latitude: Double,
    val longitude: Double,
    val placeName: String? = null,
    val placeResolved: Boolean = false,
)
internal enum class PhotoSearchQuality { Fast, Advanced }

internal data class PhotoCollection(val id: Long, val name: String, val storedCount: Int)
internal data class FaceSample(val photoKey: String, val bounds: android.graphics.Rect)
internal data class FaceMergeUndo(val sourceName: String, val sampleIds: List<Long>)
internal data class PendingReview(
    val photoKey: String,
    val question: String,
    val candidateGroupId: Long? = null,
    val faceSampleId: Long? = null,
    val faceSample: FaceSample? = null,
)
internal data class FaceGroup(val id: Long, val name: String, val count: Int, val photoKey: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
internal fun FaceGroup.bounds(): android.graphics.Rect = android.graphics.Rect(left, top, right, bottom)

/** Private metadata only. It never changes the MediaStore item or its bytes. */
internal class PhotoMetadataStore(context: Context) : SQLiteOpenHelper(context, "photo_metadata.db", null, 12) {
    private val galleryPreferences = context.getSharedPreferences("photo_gallery", Context.MODE_PRIVATE)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE photo_state (photo_key TEXT PRIMARY KEY, favorite INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE collections (id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE UNIQUE)")
        db.execSQL("CREATE TABLE collection_membership (collection_id INTEGER NOT NULL, photo_key TEXT NOT NULL, PRIMARY KEY(collection_id, photo_key), FOREIGN KEY(collection_id) REFERENCES collections(id) ON DELETE CASCADE)")
        createAiTables(db)
        createFaceGroupingTables(db)
        createLabelTables(db)
        createLocationTable(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.inTransaction {
            execSQL("CREATE TABLE photo_state_v2 (photo_key TEXT PRIMARY KEY, favorite INTEGER NOT NULL DEFAULT 0)")
            execSQL("INSERT INTO photo_state_v2(photo_key, favorite) SELECT photo_key, favorite FROM photo_state")
            execSQL("DROP TABLE photo_state")
            execSQL("ALTER TABLE photo_state_v2 RENAME TO photo_state")
            createAiTables(this)
        }
        if (oldVersion < 3) createFaceTables(db)
        if (oldVersion == 3) db.execSQL("ALTER TABLE face_samples ADD COLUMN group_id INTEGER")
        if (oldVersion < 4) createFaceGroupingTables(db)
        if (oldVersion == 4) db.execSQL("ALTER TABLE face_reviews ADD COLUMN face_sample_id INTEGER")
        if (oldVersion < 6) createLabelTables(db)
        if (oldVersion < 7) db.execSQL("UPDATE photo_ai_record SET type = NULL, confidence = 0, review_state = 'none' WHERE user_verified = 0 AND photo_key NOT IN (SELECT photo_key FROM photo_ai_label WHERE lower(label) = 'paper')")
        if (oldVersion < 8) {
            if (oldVersion >= 3) db.execSQL("ALTER TABLE face_samples ADD COLUMN quality REAL NOT NULL DEFAULT 1.0")
            db.inTransaction {
                delete("face_reviews", null, null)
                delete("face_samples", null, null)
                delete("face_groups", null, null)
            }
        }
        if (oldVersion < 9) db.inTransaction {
            // Reject the old loose grouping index; media files and other local metadata stay untouched.
            delete("face_reviews", null, null)
            delete("face_samples", null, null)
            delete("face_groups", null, null)
        }
        if (oldVersion < 10) db.inTransaction {
            // Landmark-aligned embeddings cannot be mixed with the former box-crop embeddings.
            delete("face_reviews", null, null)
            delete("face_samples", null, null)
            delete("face_groups", null, null)
        }
        if (oldVersion < 11) createLocationTable(db)
        if (oldVersion < 12) {
            db.execSQL("ALTER TABLE photo_location ADD COLUMN place_name TEXT")
            db.execSQL("ALTER TABLE photo_location ADD COLUMN place_resolved INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun states(keys: Collection<String>): Map<String, PhotoState> {
        if (keys.isEmpty()) return emptyMap()
        return readableDatabase.query(
            "photo_state", arrayOf("photo_key", "favorite"),
            "photo_key IN (${keys.joinToString { "?" }})", keys.toTypedArray(), null, null, null,
        ).use { cursor -> buildMap { while (cursor.moveToNext()) put(cursor.getString(0), PhotoState(cursor.getInt(1) != 0)) } }
    }

    fun collections(): List<PhotoCollection> = readableDatabase.rawQuery(
        "SELECT c.id, c.name, COUNT(m.photo_key) FROM collections c LEFT JOIN collection_membership m ON m.collection_id = c.id GROUP BY c.id ORDER BY c.name COLLATE NOCASE",
        null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(PhotoCollection(cursor.getLong(0), cursor.getString(1), cursor.getInt(2))) } }

    fun collectionKeys(collectionId: Long): Set<String> = readableDatabase.query(
        "collection_membership", arrayOf("photo_key"), "collection_id = ?", arrayOf(collectionId.toString()), null, null, null,
    ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun createCollection(rawName: String): PhotoCollection {
        val name = PhotoMetadataRules.collectionName(rawName)
        val id = writableDatabase.insertOrThrow("collections", null, ContentValues().apply { put("name", name) })
        return PhotoCollection(id, name, 0)
    }

    fun addToCollection(collectionId: Long, keys: Collection<String>) = writableDatabase.inTransaction {
        keys.distinct().forEach { key ->
            insertWithOnConflict("collection_membership", null, ContentValues().apply {
                put("collection_id", collectionId)
                put("photo_key", key)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun removeFromCollection(collectionId: Long, keys: Collection<String>) = writableDatabase.inTransaction {
        keys.distinct().forEach { key ->
            delete("collection_membership", "collection_id = ? AND photo_key = ?", arrayOf(collectionId.toString(), key))
        }
    }

    fun deleteCollection(collectionId: Long) {
        writableDatabase.delete("collections", "id = ?", arrayOf(collectionId.toString()))
    }

    fun setFavorite(keys: Collection<String>, favorite: Boolean) = setState(keys, "favorite", favorite)

    fun locations(keys: Collection<String>): Map<String, PhotoLocation?> {
        if (keys.isEmpty()) return emptyMap()
        return readableDatabase.query(
            "photo_location", arrayOf("photo_key", "latitude", "longitude", "place_name", "place_resolved"),
            "photo_key IN (${keys.joinToString { "?" }})", keys.toTypedArray(), null, null, null,
        ).use { cursor -> buildMap {
            while (cursor.moveToNext()) put(cursor.getString(0), if (cursor.isNull(1) || cursor.isNull(2)) null else PhotoLocation(cursor.getDouble(1), cursor.getDouble(2), cursor.getString(3), cursor.getInt(4) != 0))
        } }
    }

    fun recordLocation(photoKey: String, location: PhotoLocation?) {
        writableDatabase.insertWithOnConflict("photo_location", null, ContentValues().apply {
            put("photo_key", photoKey)
            if (location == null) {
                putNull("latitude")
                putNull("longitude")
            } else {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("place_name", location.placeName)
                put("place_resolved", if (location.placeResolved) 1 else 0)
            }
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun recordPlaceName(photoKey: String, placeName: String?) {
        writableDatabase.update("photo_location", ContentValues().apply {
            put("place_name", placeName)
            put("place_resolved", 1)
        }, "photo_key = ?", arrayOf(photoKey))
    }

    fun forgetPhotos(keys: Collection<String>) = writableDatabase.inTransaction {
        val unique = keys.distinct()
        if (unique.isEmpty()) return@inTransaction
        val placeholders = unique.joinToString { "?" }
        val arguments = unique.toTypedArray()
        execSQL("DELETE FROM face_reviews WHERE photo_key IN ($placeholders) OR face_sample_id IN (SELECT id FROM face_samples WHERE photo_key IN ($placeholders))", arguments + arguments)
        delete("face_samples", "photo_key IN ($placeholders)", arguments)
        execSQL("DELETE FROM face_groups WHERE id NOT IN (SELECT DISTINCT group_id FROM face_samples WHERE group_id IS NOT NULL)")
        execSQL("DELETE FROM face_reviews WHERE candidate_group_id NOT IN (SELECT id FROM face_groups)")
        listOf("photo_ai_label", "photo_ai_record", "collection_membership", "photo_state", "photo_location").forEach { table ->
            delete(table, "photo_key IN ($placeholders)", arguments)
        }
    }

    fun classifiedKeys(type: String): Set<String> = keysFor("SELECT photo_key FROM photo_ai_record WHERE type = ?", arrayOf(type))

    fun reviewKeys(): Set<String> = keysFor(
        "SELECT photo_key FROM photo_ai_record WHERE review_state = 'pending' UNION SELECT r.photo_key FROM face_reviews r JOIN face_groups g ON g.id = r.candidate_group_id WHERE r.state = 'pending'",
        emptyArray(),
    )

    fun nextReview(): PendingReview? = readableDatabase.query(
        "photo_ai_record", arrayOf("photo_key"), "review_state = 'pending'", null, null, null, null, "1",
    ).use { cursor -> if (cursor.moveToFirst()) PendingReview(cursor.getString(0), "Is this a document?") else null }
        ?: readableDatabase.rawQuery(
            "SELECT r.photo_key, r.candidate_group_id, r.face_sample_id, s.left_edge, s.top_edge, s.right_edge, s.bottom_edge FROM face_reviews r JOIN face_samples s ON s.id = r.face_sample_id JOIN face_groups g ON g.id = r.candidate_group_id WHERE r.state = 'pending' LIMIT 1", null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else PendingReview(
                photoKey = cursor.getString(0),
                question = "Is this the same person?",
                candidateGroupId = cursor.getLong(1),
                faceSampleId = cursor.getLong(2),
                faceSample = FaceSample(cursor.getString(0), android.graphics.Rect(cursor.getInt(3), cursor.getInt(4), cursor.getInt(5), cursor.getInt(6))),
            )
        }

    fun resolveReview(review: PendingReview, accepted: Boolean) = writableDatabase.inTransaction {
        if (review.candidateGroupId == null) update("photo_ai_record", ContentValues().apply {
            put("type", if (accepted) "document" else null as String?)
            put("user_verified", 1)
            put("review_state", "none")
        }, "photo_key = ?", arrayOf(review.photoKey)) else {
            if (accepted) update("face_samples", ContentValues().apply { put("group_id", review.candidateGroupId) }, "id = ?", arrayOf(review.faceSampleId.toString()))
            update("face_reviews", ContentValues().apply { put("state", "resolved") }, "face_sample_id = ?", arrayOf(review.faceSampleId.toString()))
        }
    }

    fun skipReview(review: PendingReview) = writableDatabase.inTransaction {
        if (review.candidateGroupId == null) update("photo_ai_record", ContentValues().apply { put("review_state", "none") }, "photo_key = ?", arrayOf(review.photoKey))
        else update("face_reviews", ContentValues().apply { put("state", "skipped") }, "face_sample_id = ? AND candidate_group_id = ?", arrayOf(review.faceSampleId.toString(), review.candidateGroupId.toString()))
    }

    fun peopleKeys(): Set<String> = keysFor("SELECT DISTINCT photo_key FROM face_samples", emptyArray())

    fun faceGroups(): List<FaceGroup> = readableDatabase.rawQuery(
        "SELECT g.id, g.name, COUNT(all_samples.id), s.photo_key, s.left_edge, s.top_edge, s.right_edge, s.bottom_edge FROM face_groups g JOIN face_samples s ON s.id = (SELECT id FROM face_samples WHERE group_id = g.id ORDER BY id LIMIT 1) LEFT JOIN face_samples all_samples ON all_samples.group_id = g.id GROUP BY g.id ORDER BY g.name COLLATE NOCASE", null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(FaceGroup(cursor.getLong(0), cursor.getString(1), cursor.getInt(2), cursor.getString(3), cursor.getInt(4), cursor.getInt(5), cursor.getInt(6), cursor.getInt(7))) } }
        .sortedWith(compareBy<FaceGroup> { isGeneratedPersonName(it.name) }.thenBy { it.name.lowercase(Locale.ROOT) })

    fun faceGroup(groupId: Long): FaceGroup? = readableDatabase.rawQuery(
        "SELECT g.id, g.name, COUNT(all_samples.id), s.photo_key, s.left_edge, s.top_edge, s.right_edge, s.bottom_edge FROM face_groups g JOIN face_samples s ON s.id = (SELECT id FROM face_samples WHERE group_id = g.id ORDER BY id LIMIT 1) LEFT JOIN face_samples all_samples ON all_samples.group_id = g.id WHERE g.id = ? GROUP BY g.id",
        arrayOf(groupId.toString()),
    ).use { cursor -> if (cursor.moveToFirst()) FaceGroup(cursor.getLong(0), cursor.getString(1), cursor.getInt(2), cursor.getString(3), cursor.getInt(4), cursor.getInt(5), cursor.getInt(6), cursor.getInt(7)) else null }

    fun faceGroupKeys(groupId: Long): Set<String> = keysFor("SELECT DISTINCT photo_key FROM face_samples WHERE group_id = ?", arrayOf(groupId.toString()))

    fun labelsByPhoto(keys: Collection<String>): Map<String, List<String>> = valuesByPhoto(
        "SELECT photo_key, label FROM photo_ai_label WHERE photo_key IN (${keys.joinToString { "?" }}) ORDER BY label COLLATE NOCASE", keys,
    )

    fun peopleNamesByPhoto(keys: Collection<String>): Map<String, List<String>> = valuesByPhoto(
        "SELECT DISTINCT s.photo_key, g.name FROM face_samples s JOIN face_groups g ON g.id = s.group_id WHERE s.photo_key IN (${keys.joinToString { "?" }}) ORDER BY g.name COLLATE NOCASE", keys,
    )

    fun renameFaceGroup(groupId: Long, name: String) {
        val cleaned = PhotoMetadataRules.collectionName(name)
        writableDatabase.update("face_groups", ContentValues().apply { put("name", cleaned) }, "id = ?", arrayOf(groupId.toString()))
    }

    /** Keeps the open group and moves every face from the selected duplicate into it. */
    fun mergeFaceGroups(sourceGroupId: Long, targetGroupId: Long): FaceMergeUndo {
        require(sourceGroupId != targetGroupId) { "Choose two different people groups." }
        return writableDatabase.inTransaction {
            val sourceName = rawQuery("SELECT name FROM face_groups WHERE id = ?", arrayOf(sourceGroupId.toString())).use { cursor ->
                require(cursor.moveToFirst()) { "The selected person no longer exists." }
                cursor.getString(0)
            }
            val sampleIds = rawQuery("SELECT id FROM face_samples WHERE group_id = ?", arrayOf(sourceGroupId.toString())).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
            }
            update("face_reviews", ContentValues().apply { put("state", "resolved") }, "face_sample_id IN (SELECT id FROM face_samples WHERE group_id = ?)", arrayOf(sourceGroupId.toString()))
            update("face_reviews", ContentValues().apply { put("candidate_group_id", targetGroupId) }, "candidate_group_id = ?", arrayOf(sourceGroupId.toString()))
            update("face_samples", ContentValues().apply { put("group_id", targetGroupId) }, "group_id = ?", arrayOf(sourceGroupId.toString()))
            delete("face_groups", "id = ?", arrayOf(sourceGroupId.toString()))
            FaceMergeUndo(sourceName, sampleIds)
        }
    }

    fun undoFaceMerge(undo: FaceMergeUndo) = writableDatabase.inTransaction {
        if (undo.sampleIds.isEmpty()) return@inTransaction
        val restoredGroupId = insertOrThrow("face_groups", null, ContentValues().apply { put("name", undo.sourceName) })
        update("face_samples", ContentValues().apply { put("group_id", restoredGroupId) }, "id IN (${undo.sampleIds.joinToString { "?" }})", undo.sampleIds.map(Long::toString).toTypedArray())
    }

    fun needsAnalysis(photoKey: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM photo_ai_record WHERE photo_key = ? AND model_version = ? LIMIT 1",
        arrayOf(photoKey, analysisModelVersion()),
    ).use { !it.moveToFirst() }

    fun recordClassification(photoKey: String, documentConfidence: Float, faces: List<DetectedFace>, labels: List<String>, modelVersion: String = analysisModelVersion()) = writableDatabase.inTransaction {
        val document = documentConfidence >= 0.70f
        val review = documentConfidence in 0.40f..<0.70f
        val verifiedDocument = rawQuery("SELECT user_verified, type FROM photo_ai_record WHERE photo_key = ?", arrayOf(photoKey)).use { cursor ->
            if (cursor.moveToFirst() && cursor.getInt(0) != 0) true to cursor.getString(1) else false to null
        }
        insertWithOnConflict("photo_ai_record", null, ContentValues().apply {
            put("photo_key", photoKey)
            put("source_fingerprint", photoKey)
            put("type", if (verifiedDocument.first) verifiedDocument.second else if (document) "document" else null as String?)
            put("confidence", documentConfidence)
            put("model_version", modelVersion)
            put("user_verified", if (verifiedDocument.first) 1 else 0)
            put("review_state", if (!verifiedDocument.first && review) "pending" else "none")
        }, SQLiteDatabase.CONFLICT_REPLACE)
        delete("photo_ai_label", "photo_key = ?", arrayOf(photoKey))
        (if (faces.isNotEmpty()) labels + "Portrait" else labels).distinct().forEach { label ->
            insertOrThrow("photo_ai_label", null, ContentValues().apply {
                put("photo_key", photoKey)
                put("label", label)
            })
        }
        val candidates = faceCandidates()
        faces.forEach { face ->
            val match = candidates.maxByOrNull { cosineSimilarity(face.embedding, it.embedding) }
            val similarity = match?.let { cosineSimilarity(face.embedding, it.embedding) } ?: -1f
            val reliable = isReliableFace(face.quality, face.yaw, face.roll)
            val groupId = when {
                similarity >= 0.74f -> match!!.groupId
                !reliable && similarity >= 0.55f -> match!!.groupId
                !reliable -> null
                else -> createFaceGroup()
            }
            if (groupId == null) return@forEach
            val sampleId = insertWithOnConflict("face_samples", null, ContentValues().apply {
                put("photo_key", photoKey)
                put("left_edge", face.bounds.left)
                put("top_edge", face.bounds.top)
                put("right_edge", face.bounds.right)
                put("bottom_edge", face.bounds.bottom)
                put("embedding", face.embedding.toBytes())
                put("group_id", groupId)
                put("quality", face.quality)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            if (sampleId != -1L && reliable && similarity in 0.66f..<0.74f) insertWithOnConflict("face_reviews", null, ContentValues().apply {
                put("photo_key", photoKey)
                put("face_sample_id", sampleId)
                put("candidate_group_id", match!!.groupId)
                put("state", "pending")
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun peopleAnalysisEnabled(): Boolean = galleryPreferences.getBoolean("people_analysis_enabled", false)

    fun searchQuality(): PhotoSearchQuality = if (galleryPreferences.getString("search_quality", PhotoSearchQuality.Fast.name) == PhotoSearchQuality.Advanced.name) PhotoSearchQuality.Advanced else PhotoSearchQuality.Fast

    fun setSearchQuality(quality: PhotoSearchQuality) { galleryPreferences.edit().putString("search_quality", quality.name).apply() }

    fun documentsAnalysisEnabled(): Boolean = galleryPreferences.getBoolean("documents_analysis_enabled", false)

    fun setPeopleAnalysisEnabled(enabled: Boolean) { galleryPreferences.edit().putBoolean("people_analysis_enabled", enabled).apply() }

    fun setDocumentsAnalysisEnabled(enabled: Boolean) { galleryPreferences.edit().putBoolean("documents_analysis_enabled", enabled).apply() }

    private fun analysisModelVersion(): String = "local-v7-${searchQuality().name.lowercase(Locale.ROOT)}"

    fun hidesScreenshotsFromGallery(): Boolean = galleryPreferences.getBoolean("hide_screenshots", false)

    fun hidesDocumentsFromGallery(): Boolean = galleryPreferences.getBoolean("hide_documents", false)

    fun hidesPeopleFromCollections(): Boolean = galleryPreferences.getBoolean("hide_people_collection", false)

    fun hidesDocumentsFromCollections(): Boolean = galleryPreferences.getBoolean("hide_documents_collection", false)

    fun setHidesScreenshotsFromGallery(hide: Boolean) { galleryPreferences.edit().putBoolean("hide_screenshots", hide).apply() }

    fun setHidesDocumentsFromGallery(hide: Boolean) { galleryPreferences.edit().putBoolean("hide_documents", hide).apply() }

    fun setHidesPeopleFromCollections(hide: Boolean) { galleryPreferences.edit().putBoolean("hide_people_collection", hide).apply() }

    fun setHidesDocumentsFromCollections(hide: Boolean) { galleryPreferences.edit().putBoolean("hide_documents_collection", hide).apply() }

    private fun keysFor(query: String, arguments: Array<String>): Set<String> = readableDatabase.rawQuery(query, arguments).use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    private fun valuesByPhoto(query: String, keys: Collection<String>): Map<String, List<String>> {
        if (keys.isEmpty()) return emptyMap()
        return readableDatabase.rawQuery(query, keys.toTypedArray()).use { cursor -> buildMap {
            while (cursor.moveToNext()) {
                val photoKey = cursor.getString(0)
                put(photoKey, (get(photoKey).orEmpty() + cursor.getString(1)).distinct())
            }
        } }
    }

    private fun createLocationTable(db: SQLiteDatabase) = db.execSQL(
        "CREATE TABLE IF NOT EXISTS photo_location (photo_key TEXT PRIMARY KEY, latitude REAL, longitude REAL, place_name TEXT, place_resolved INTEGER NOT NULL DEFAULT 0)",
    )

    private fun setState(keys: Collection<String>, column: String, value: Boolean) = writableDatabase.inTransaction {
        keys.distinct().forEach { key ->
            insertWithOnConflict("photo_state", null, ContentValues().apply {
                put("photo_key", key)
                put(column, if (value) 1 else 0)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            update("photo_state", ContentValues().apply { put(column, if (value) 1 else 0) }, "photo_key = ?", arrayOf(key))
        }
    }

    private inline fun <T> SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        return try { block().also { setTransactionSuccessful() } } finally { endTransaction() }
    }

    private fun createAiTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS photo_ai_record (photo_key TEXT PRIMARY KEY, source_fingerprint TEXT NOT NULL, type TEXT, subtype TEXT, confidence REAL, model_version TEXT, policy_version INTEGER, user_verified INTEGER NOT NULL DEFAULT 0, review_state TEXT NOT NULL DEFAULT 'none')")
        createFaceTables(db)
    }

    private fun createFaceTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS face_samples (id INTEGER PRIMARY KEY, photo_key TEXT NOT NULL, left_edge INTEGER NOT NULL, top_edge INTEGER NOT NULL, right_edge INTEGER NOT NULL, bottom_edge INTEGER NOT NULL, embedding BLOB NOT NULL, group_id INTEGER, quality REAL NOT NULL DEFAULT 1.0, UNIQUE(photo_key, left_edge, top_edge, right_edge, bottom_edge))")
    }

    private fun createFaceGroupingTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS face_groups (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS face_reviews (id INTEGER PRIMARY KEY, photo_key TEXT NOT NULL, face_sample_id INTEGER NOT NULL, candidate_group_id INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'pending', UNIQUE(face_sample_id, candidate_group_id))")
    }

    private fun createLabelTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS photo_ai_label (photo_key TEXT NOT NULL, label TEXT NOT NULL COLLATE NOCASE, PRIMARY KEY(photo_key, label))")
    }

    private fun SQLiteDatabase.createFaceGroup(): Long = insertOrThrow("face_groups", null, ContentValues().apply {
        put("name", "Person ${rawQuery("SELECT COUNT(*) FROM face_groups", null).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) + 1 }}")
    })

    private fun SQLiteDatabase.faceCandidates(): List<FaceCandidate> = rawQuery(
        "SELECT group_id, embedding FROM face_samples WHERE group_id IS NOT NULL AND quality >= 0.68", null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(FaceCandidate(cursor.getLong(0), cursor.getBlob(1).toFloatArray())) } }

    private data class FaceCandidate(val groupId: Long, val embedding: FloatArray)
}

internal object PhotoSearchRules {
    private val aliases = mapOf(
        "θαλασσα" to setOf("sea", "ocean", "beach", "water", "coast", "shore"),
        "βουνο" to setOf("mountain", "hill", "cliff", "peak"),
        "νυχτα" to setOf("night", "darkness"),
        "μερα" to setOf("day", "daytime", "sunlight"),
        "προσωπο" to setOf("portrait", "person", "face"),
        "ζωο" to setOf("animal", "dog", "cat", "bird", "horse", "fish"),
    )

    fun matches(query: String, terms: Collection<String>): Boolean {
        val needle = normalized(query)
        if (needle.isBlank()) return true
        val candidates = aliases[needle].orEmpty() + needle
        return terms.any { term -> candidates.any { normalized(term).contains(it) } }
    }

    fun frequentTags(labels: Collection<String>, limit: Int = 5): List<String> = labels
        .groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit).map { it.key }

    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT)
}

internal fun isGeneratedPersonName(name: String): Boolean = name.matches(Regex("Person \\d+"))

private fun ByteArray.toFloatArray(): FloatArray = ByteBuffer.wrap(this).order(ByteOrder.nativeOrder()).let { buffer ->
    FloatArray(size / 4) { buffer.float }
}

private fun FloatArray.toBytes(): ByteArray = ByteBuffer.allocate(size * 4).order(ByteOrder.nativeOrder()).apply {
    forEach(::putFloat)
}.array()

internal object PhotoMetadataRules {
    fun stableKey(uri: String, relativePath: String?, name: String, sizeBytes: Long): String {
        val source = listOf(uri, relativePath.orEmpty(), name, sizeBytes.toString()).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun collectionName(rawName: String): String = rawName.trim().also {
        require(it.isNotEmpty()) { "Collection name cannot be empty." }
        require(it.length <= 60) { "Collection names can be at most 60 characters." }
        require(it.none(Char::isISOControl)) { "Collection name contains unsupported characters." }
    }

    fun visibleInGallery(isScreenshot: Boolean, isDocument: Boolean, hideScreenshots: Boolean, hideDocuments: Boolean): Boolean =
        !(hideScreenshots && isScreenshot) && !(hideDocuments && isDocument)
}
