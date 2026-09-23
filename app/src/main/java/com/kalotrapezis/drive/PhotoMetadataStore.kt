package com.kalotrapezis.drive

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Rect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

internal data class PhotoState(val favorite: Boolean = false)
internal data class PhotoLocation(
    val latitude: Double,
    val longitude: Double,
    val placeName: String? = null,
    val placeResolved: Boolean = false,
)
internal enum class PhotoSearchQuality { Fast, Advanced }

internal data class PhotoCollection(val id: Long, val name: String, val storedCount: Int, val uuid: String? = null)
internal data class FaceSample(val photoKey: String, val bounds: android.graphics.Rect)
internal data class FaceMergeUndo(val sourceName: String, val sampleIds: List<Long>, val sourceUuid: String? = null)
/** The head a combined group was shown by, so History can draw it after the group itself is gone. */
internal data class FaceHead(val photoKey: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
internal data class FaceMerge(val id: Long, val sourceName: String, val sourceUuid: String?, val sampleIds: List<Long>, val mergedAt: Long, val head: FaceHead?)
internal data class PendingReview(
    val photoKey: String,
    val question: String,
    val candidateGroupId: Long? = null,
    val faceSampleId: Long? = null,
    val faceSample: FaceSample? = null,
)
internal data class FaceGroup(val id: Long, val name: String, val count: Int, val photoKey: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
internal data class FavoriteRecord(val photoKey: String, val favorite: Boolean, val updatedAt: Long)
internal data class CollectionRecord(val uuid: String, val name: String, val deleted: Boolean, val updatedAt: Long, val hiddenFromGallery: Boolean = false)
internal data class CollectionItemRecord(val collectionUuid: String, val photoKey: String, val deleted: Boolean, val updatedAt: Long)
internal data class PersonRecord(val uuid: String, val name: String, val updatedAt: Long)
internal data class FaceRecord(val uuid: String, val photoKey: String, val bounds: android.graphics.Rect, val embedding: ByteArray, val quality: Float, val personUuid: String?, val updatedAt: Long)
internal data class DocumentRecord(val photoKey: String, val type: String?, val confidence: Float, val userVerified: Boolean, val updatedAt: Long)
internal fun FaceGroup.bounds(): android.graphics.Rect = android.graphics.Rect(left, top, right, bottom)

/** Private metadata only. It never changes the MediaStore item or its bytes. */
internal class PhotoMetadataStore(context: Context) : SQLiteOpenHelper(context, "photo_metadata.db", null, 17) {

    private companion object {
        const val MERGE_HISTORY = "CREATE TABLE IF NOT EXISTS face_merges (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "target_id INTEGER NOT NULL, source_name TEXT NOT NULL, source_uuid TEXT, sample_ids TEXT NOT NULL, merged_at INTEGER NOT NULL)"
    }
    private val context = context.applicationContext
    private val galleryPreferences = this.context.getSharedPreferences("photo_gallery", Context.MODE_PRIVATE)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(MERGE_HISTORY)
        db.execSQL("CREATE TABLE photo_state (photo_key TEXT PRIMARY KEY, favorite INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE collections (id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE UNIQUE, updated_at INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0, hidden INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE collection_membership (collection_id INTEGER NOT NULL, photo_key TEXT NOT NULL, updated_at INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(collection_id, photo_key), FOREIGN KEY(collection_id) REFERENCES collections(id) ON DELETE CASCADE)")
        createAiTables(db)
        createFaceGroupingTables(db)
        createLabelTables(db)
        createLocationTable(db)
        addSyncIds(db)
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
        if (oldVersion < 13) addSyncIds(db)
        // ponytail: sync (SYNC_PLAN.md phase 6a) needs updated_at for last-write-wins; only
        // photo_ai_record does for now — faces/people get the same treatment in phase 6c.
        if (oldVersion < 14) db.inTransaction {
            execSQL("ALTER TABLE photo_ai_record ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            execSQL("UPDATE photo_ai_record SET updated_at = ${System.currentTimeMillis()} WHERE updated_at = 0")
        }
        // Phase 6b/6c: favorites, collections and people/faces sync the same way documents did — last-write-wins
        // by updated_at, removals as tombstones so they can travel instead of silently reappearing.
        if (oldVersion < 15) db.inTransaction {
            val now = System.currentTimeMillis()
            for (table in listOf("photo_state", "collections", "collection_membership", "face_groups", "face_samples")) {
                execSQL("ALTER TABLE $table ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                execSQL("UPDATE $table SET updated_at = $now")
            }
            for (table in listOf("collections", "collection_membership")) execSQL("ALTER TABLE $table ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
        }
        // "Hide this album from Gallery" belongs to the album, not to this phone, so it moves out of preferences
        // and onto the collection, where it travels with it (SYNC_PLAN.md "Which settings sync").
        if (oldVersion < 17) db.execSQL(MERGE_HISTORY)
        if (oldVersion < 16) db.inTransaction {
            execSQL("ALTER TABLE collections ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            val hidden = context.getSharedPreferences("photo_gallery", Context.MODE_PRIVATE).getStringSet("hidden_albums", emptySet()).orEmpty()
            hidden.forEach { uuid -> update("collections", ContentValues().apply { put("hidden", 1) }, "uuid = ?", arrayOf(uuid)) }
        }
    }

    /**
     * Permanent ids for sync (SYNC_PLAN.md): people, collections and faces keep their local integer ids and get a
     * UUID that is the same on every device. Existing rows are only extended, never rewritten.
     */
    private fun addSyncIds(db: SQLiteDatabase) = db.inTransaction {
        for (table in listOf("face_groups", "collections", "face_samples")) {
            execSQL("ALTER TABLE $table ADD COLUMN uuid TEXT")
            val ids = rawQuery("SELECT id FROM $table", null).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
            ids.forEach { id -> update(table, ContentValues().apply { put("uuid", UUID.randomUUID().toString()) }, "id = ?", arrayOf(id.toString())) }
            execSQL("CREATE UNIQUE INDEX ${table}_uuid ON $table(uuid)")
        }
    }

    /**
     * After "Save" replaces a photo, its size and so its key change. Favorite, collections and location follow the
     * photo; faces and labels are left to re-analysis because crop or rotation moved them.
     */
    fun rekeyPhoto(oldKey: String, newKey: String) {
        if (oldKey == newKey) return
        writableDatabase.inTransaction {
            for (table in listOf("photo_state", "collection_membership", "photo_location")) {
                execSQL("UPDATE OR IGNORE $table SET photo_key = ? WHERE photo_key = ?", arrayOf(newKey, oldKey))
                delete(table, "photo_key = ?", arrayOf(oldKey)) // only rows that already existed for the new key are left
            }
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
        "SELECT c.id, c.name, COUNT(m.photo_key), c.uuid FROM collections c LEFT JOIN collection_membership m ON m.collection_id = c.id AND m.deleted = 0 WHERE c.deleted = 0 GROUP BY c.id ORDER BY c.name COLLATE NOCASE",
        null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(PhotoCollection(cursor.getLong(0), cursor.getString(1), cursor.getInt(2), cursor.getString(3))) } }

    fun collectionKeys(collectionId: Long): Set<String> = readableDatabase.query(
        "collection_membership", arrayOf("photo_key"), "collection_id = ? AND deleted = 0", arrayOf(collectionId.toString()), null, null, null,
    ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun createCollection(rawName: String): PhotoCollection = writableDatabase.inTransaction {
        val name = PhotoMetadataRules.collectionName(rawName)
        val now = System.currentTimeMillis()
        val buried = rawQuery("SELECT id, uuid FROM collections WHERE name = ? COLLATE NOCASE AND deleted = 1", arrayOf(name)).use { if (it.moveToFirst()) it.getLong(0) to it.getString(1) else null }
        if (buried != null) { // the name was never freed by the tombstone, so reuse the row (and its UUID, so other devices see one collection)
            update("collections", ContentValues().apply { put("deleted", 0); put("updated_at", now) }, "id = ?", arrayOf(buried.first.toString()))
            return@inTransaction PhotoCollection(buried.first, name, 0, buried.second)
        }
        val uuid = UUID.randomUUID().toString()
        val id = insertOrThrow("collections", null, ContentValues().apply { put("name", name); put("uuid", uuid); put("updated_at", now) })
        PhotoCollection(id, name, 0, uuid)
    }

    fun addToCollection(collectionId: Long, keys: Collection<String>) = setMembership(collectionId, keys, member = true)

    fun removeFromCollection(collectionId: Long, keys: Collection<String>) = setMembership(collectionId, keys, member = false)

    /** A tombstone, not a delete: the removal has to reach the computer, and photos stay in the library. */
    fun deleteCollection(collectionId: Long) = writableDatabase.inTransaction {
        val now = System.currentTimeMillis()
        update("collections", ContentValues().apply { put("deleted", 1); put("updated_at", now) }, "id = ?", arrayOf(collectionId.toString()))
        update("collection_membership", ContentValues().apply { put("deleted", 1); put("updated_at", now) }, "collection_id = ? AND deleted = 0", arrayOf(collectionId.toString()))
    }

    private fun setMembership(collectionId: Long, keys: Collection<String>, member: Boolean) = writableDatabase.inTransaction {
        val now = System.currentTimeMillis()
        keys.distinct().forEach { key ->
            insertWithOnConflict("collection_membership", null, ContentValues().apply {
                put("collection_id", collectionId)
                put("photo_key", key)
                put("deleted", if (member) 0 else 1)
                put("updated_at", now)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
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
            put("updated_at", System.currentTimeMillis())
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

    /**
     * The people to show, and the face to show them by.
     *
     * A group is only as alive as its photos: when every photo of a person has been deleted or moved away there
     * is nothing left to show, so the group does not appear (the rows stay, because Android's own Trash holds a
     * deleted photo for thirty days and restoring it should bring the person back, name and all).
     *
     * The cover is the **best face**, not the first one found. Since the computer's faces now arrive with their
     * quality score, measured by the same formula, the better portrait wins wherever it was found — which is
     * usually the computer, because it detects at a larger size.
     */
    fun faceGroups(livePhotoKeys: Set<String>? = null): List<FaceGroup> = groupsFrom(livePhotoKeys, null)
        .sortedWith(compareBy<FaceGroup> { isGeneratedPersonName(it.name) }.thenBy { it.name.lowercase(Locale.ROOT) })

    fun faceGroup(groupId: Long, livePhotoKeys: Set<String>? = null): FaceGroup? = groupsFrom(livePhotoKeys, groupId).firstOrNull()

    private fun groupsFrom(livePhotoKeys: Set<String>?, onlyGroup: Long?): List<FaceGroup> = readableDatabase.rawQuery(
        "SELECT g.id, g.name, s.photo_key, s.left_edge, s.top_edge, s.right_edge, s.bottom_edge, s.quality " +
            "FROM face_groups g JOIN face_samples s ON s.group_id = g.id" + if (onlyGroup != null) " WHERE g.id = ?" else "",
        onlyGroup?.let { arrayOf(it.toString()) },
    ).use { cursor ->
        class Sample(val key: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val quality: Float)
        val names = HashMap<Long, String>()
        val samples = HashMap<Long, MutableList<Sample>>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val key = cursor.getString(2)
            if (livePhotoKeys != null && key !in livePhotoKeys) continue
            names[id] = cursor.getString(1)
            samples.getOrPut(id) { mutableListOf() }
                .add(Sample(key, cursor.getInt(3), cursor.getInt(4), cursor.getInt(5), cursor.getInt(6), cursor.getFloat(7)))
        }
        samples.mapNotNull { (id, list) ->
            val cover = list.maxByOrNull { it.quality } ?: return@mapNotNull null
            FaceGroup(id, names.getValue(id), list.distinctBy { it.key }.size, cover.key, cover.left, cover.top, cover.right, cover.bottom)
        }
    }

    fun faceGroupKeys(groupId: Long): Set<String> = keysFor("SELECT DISTINCT photo_key FROM face_samples WHERE group_id = ?", arrayOf(groupId.toString()))

    fun labelsByPhoto(keys: Collection<String>): Map<String, List<String>> = valuesByPhoto(
        "SELECT photo_key, label FROM photo_ai_label WHERE photo_key IN (${keys.joinToString { "?" }}) ORDER BY label COLLATE NOCASE", keys,
    )

    fun peopleNamesByPhoto(keys: Collection<String>): Map<String, List<String>> = valuesByPhoto(
        "SELECT DISTINCT s.photo_key, g.name FROM face_samples s JOIN face_groups g ON g.id = s.group_id WHERE s.photo_key IN (${keys.joinToString { "?" }}) ORDER BY g.name COLLATE NOCASE", keys,
    )

    fun renameFaceGroup(groupId: Long, name: String) {
        val cleaned = PhotoMetadataRules.collectionName(name)
        writableDatabase.update("face_groups", ContentValues().apply { put("name", cleaned); put("updated_at", System.currentTimeMillis()) }, "id = ?", arrayOf(groupId.toString()))
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
            val sourceUuid = rawQuery("SELECT uuid FROM face_groups WHERE id = ?", arrayOf(sourceGroupId.toString())).use { if (it.moveToFirst()) it.getString(0) else null }
            update("face_reviews", ContentValues().apply { put("state", "resolved") }, "face_sample_id IN (SELECT id FROM face_samples WHERE group_id = ?)", arrayOf(sourceGroupId.toString()))
            update("face_reviews", ContentValues().apply { put("candidate_group_id", targetGroupId) }, "candidate_group_id = ?", arrayOf(sourceGroupId.toString()))
            update("face_samples", ContentValues().apply { put("group_id", targetGroupId); put("updated_at", System.currentTimeMillis()) }, "group_id = ?", arrayOf(sourceGroupId.toString()))
            delete("face_groups", "id = ?", arrayOf(sourceGroupId.toString()))
            // Kept, not just offered for eight seconds: combining is the one action here that quietly destroys a
            // grouping, and the person it was wrong about is unreachable afterwards unless we remember them.
            insertWithOnConflict("face_merges", null, ContentValues().apply {
                put("source_name", sourceName)
                put("source_uuid", sourceUuid)
                put("target_id", targetGroupId)
                put("sample_ids", sampleIds.joinToString(","))
                put("merged_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_REPLACE)
            FaceMergeUndo(sourceName, sampleIds, sourceUuid)
        }
    }

    fun undoFaceMerge(undo: FaceMergeUndo) = writableDatabase.inTransaction {
        if (undo.sampleIds.isEmpty()) return@inTransaction
        // The same UUID comes back, so other devices see one continuous person rather than a new one.
        val restoredGroupId = insertOrThrow("face_groups", null, ContentValues().apply {
            put("name", undo.sourceName)
            put("uuid", undo.sourceUuid ?: UUID.randomUUID().toString())
            put("updated_at", System.currentTimeMillis())
        })
        update("face_samples", ContentValues().apply { put("group_id", restoredGroupId); put("updated_at", System.currentTimeMillis()) }, "id IN (${undo.sampleIds.joinToString { "?" }})", undo.sampleIds.map(Long::toString).toTypedArray())
        delete("face_merges", "sample_ids = ?", arrayOf(undo.sampleIds.joinToString(",")))
    }

    /**
     * Every group that was combined into this person, newest first, each still showing the head and the name it
     * had — usually a bare "Person 41" — so a combine that was wrong can be taken back long after the moment it
     * was made. The faces are still in the library; only which person they belong to changed.
     */
    fun mergeHistory(groupId: Long): List<FaceMerge> = readableDatabase.rawQuery(
        "SELECT id, source_name, source_uuid, sample_ids, merged_at FROM face_merges WHERE target_id = ? ORDER BY merged_at DESC",
        arrayOf(groupId.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val ids = cursor.getString(3).split(',').mapNotNull(String::toLongOrNull)
                if (ids.isEmpty()) continue
                val head = readableDatabase.rawQuery(
                    "SELECT photo_key, left_edge, top_edge, right_edge, bottom_edge FROM face_samples WHERE id IN (${ids.joinToString { "?" }}) ORDER BY quality DESC LIMIT 1",
                    ids.map(Long::toString).toTypedArray(),
                ).use { h -> if (h.moveToFirst()) FaceHead(h.getString(0), h.getInt(1), h.getInt(2), h.getInt(3), h.getInt(4)) else null }
                add(FaceMerge(cursor.getLong(0), cursor.getString(1), cursor.getString(2), ids, cursor.getLong(4), head))
            }
        }
    }

    fun restoreMerge(merge: FaceMerge) = undoFaceMerge(FaceMergeUndo(merge.sourceName, merge.sampleIds, merge.sourceUuid))

    /**
     * Take these photos' faces out of this person and give them a person of their own.
     *
     * The classifier's own joins are not combines and leave no history, so this is the only way back out of one.
     * The faces are not thrown away — they become a new "Person N" standing beside the others, which is exactly
     * what Combine puts back if the removal itself was wrong.
     */
    fun detachPhotosFromGroup(groupId: Long, photoKeys: Collection<String>): Boolean = writableDatabase.inTransaction {
        if (photoKeys.isEmpty()) return@inTransaction false
        val marks = photoKeys.joinToString { "?" }
        val args = (listOf(groupId.toString()) + photoKeys).toTypedArray()
        val sampleIds = rawQuery("SELECT id FROM face_samples WHERE group_id = ? AND photo_key IN ($marks)", args).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }
        if (sampleIds.isEmpty()) return@inTransaction false
        // Everything this group still holds must not be emptied: a person with no faces at all is not a person.
        val remaining = rawQuery("SELECT COUNT(*) FROM face_samples WHERE group_id = ? AND id NOT IN (${sampleIds.joinToString { "?" }})",
            (listOf(groupId.toString()) + sampleIds.map(Long::toString)).toTypedArray()).use { it.moveToFirst(); it.getInt(0) }
        if (remaining == 0) return@inTransaction false
        val ids = sampleIds.joinToString { "?" }
        val idArgs = sampleIds.map(Long::toString).toTypedArray()
        delete("face_reviews", "face_sample_id IN ($ids)", idArgs)
        update("face_samples", ContentValues().apply {
            put("group_id", createFaceGroup())
            put("updated_at", System.currentTimeMillis())
        }, "id IN ($ids)", idArgs)
        true
    }

    /**
     * Throws away the groups nobody has named, and the faces in them, so a rescan can group them again — with
     * whatever the thresholds are now. People you have named are left exactly as they are, together with their
     * faces: a rescan is for redoing the guessing, never for undoing a decision.
     *
     * Faces the computer sent come back on the next sync, since it still holds them.
     */
    fun forgetUnnamedFaces() = writableDatabase.inTransaction {
        execSQL("DELETE FROM face_reviews WHERE face_sample_id IN (SELECT s.id FROM face_samples s LEFT JOIN face_groups g ON g.id = s.group_id WHERE g.id IS NULL OR g.name GLOB 'Person [0-9]*')")
        execSQL("DELETE FROM face_samples WHERE id IN (SELECT s.id FROM face_samples s LEFT JOIN face_groups g ON g.id = s.group_id WHERE g.id IS NULL OR g.name GLOB 'Person [0-9]*')")
        execSQL("DELETE FROM face_groups WHERE id NOT IN (SELECT DISTINCT group_id FROM face_samples WHERE group_id IS NOT NULL)")
        execSQL("DELETE FROM face_reviews WHERE candidate_group_id NOT IN (SELECT id FROM face_groups)")
        execSQL("DELETE FROM face_merges WHERE target_id NOT IN (SELECT id FROM face_groups)")
    }

    /** Every photo already read by the model as it stands — one query, rather than one per photo in the gallery. */
    fun analyzedKeys(): Set<String> = keysFor(
        "SELECT photo_key FROM photo_ai_record WHERE model_version = ?", arrayOf(analysisModelVersion()),
    )

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
            put("updated_at", System.currentTimeMillis())
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
                similarity >= SAME_PERSON -> match!!.groupId
                !reliable -> null
                else -> createFaceGroup()
            }
            if (groupId == null) return@forEach
            if (overlappingFace(photoKey, face.bounds) != null) return@forEach // the computer already sent this face
            val sampleId = insertWithOnConflict("face_samples", null, ContentValues().apply {
                put("photo_key", photoKey)
                put("left_edge", face.bounds.left)
                put("top_edge", face.bounds.top)
                put("right_edge", face.bounds.right)
                put("bottom_edge", face.bounds.bottom)
                put("embedding", face.embedding.toBytes())
                put("group_id", groupId)
                put("quality", face.quality)
                put("uuid", UUID.randomUUID().toString())
                put("updated_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_IGNORE)
            if (sampleId != -1L && reliable && similarity in REVIEW_FROM..<SAME_PERSON) insertWithOnConflict("face_reviews", null, ContentValues().apply {
                put("photo_key", photoKey)
                put("face_sample_id", sampleId)
                put("candidate_group_id", match!!.groupId)
                put("state", "pending")
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    /** Every classified photo, for the sync push (SYNC_PLAN.md phase 6a). */
    fun documentRecords(): List<DocumentRecord> = readableDatabase.rawQuery(
        "SELECT photo_key, type, confidence, user_verified, updated_at FROM photo_ai_record WHERE type IS NOT NULL",
        null,
    ).use { c -> buildList { while (c.moveToNext()) add(DocumentRecord(c.getString(0), c.getString(1), c.getFloat(2), c.getInt(3) != 0, c.getLong(4))) } }

    /** From the sync pull: applied only if newer than what's stored locally (last-write-wins). */
    fun applyIncomingDocument(photoKey: String, type: String?, confidence: Float, userVerified: Boolean, updatedAt: Long) = writableDatabase.inTransaction {
        val localUpdatedAt = rawQuery("SELECT updated_at FROM photo_ai_record WHERE photo_key = ?", arrayOf(photoKey)).use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
        if (updatedAt <= localUpdatedAt) return@inTransaction
        insertWithOnConflict("photo_ai_record", null, ContentValues().apply {
            put("photo_key", photoKey)
            put("source_fingerprint", photoKey)
            put("type", type)
            put("confidence", confidence)
            put("user_verified", if (userVerified) 1 else 0)
            put("review_state", "none")
            put("updated_at", updatedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // --- Phase 6b/6c sync: everything else the user made (favorites, collections, labels, people, faces).
    // Exports carry the local photo_key and collection/person UUIDs; SyncClient maps photo_key → SHA-256.

    /** Every favorite. Unfavoriting is a value change, not a row removal, so it travels too. */
    fun favoriteRecords(): List<FavoriteRecord> = readableDatabase.rawQuery(
        "SELECT photo_key, favorite, updated_at FROM photo_state", null,
    ).use { c -> buildList { while (c.moveToNext()) add(FavoriteRecord(c.getString(0), c.getInt(1) != 0, c.getLong(2))) } }

    fun collectionRecords(): List<CollectionRecord> = readableDatabase.rawQuery(
        "SELECT uuid, name, deleted, updated_at, hidden FROM collections WHERE uuid IS NOT NULL", null,
    ).use { c -> buildList { while (c.moveToNext()) add(CollectionRecord(c.getString(0), c.getString(1), c.getInt(2) != 0, c.getLong(3), c.getInt(4) != 0)) } }

    fun collectionItemRecords(): List<CollectionItemRecord> = readableDatabase.rawQuery(
        "SELECT c.uuid, m.photo_key, m.deleted, m.updated_at FROM collection_membership m JOIN collections c ON c.id = m.collection_id WHERE c.uuid IS NOT NULL",
        null,
    ).use { c -> buildList { while (c.moveToNext()) add(CollectionItemRecord(c.getString(0), c.getString(1), c.getInt(2) != 0, c.getLong(3))) } }

    /** Only groups that still hold a face: a merged-away person needs no tombstone, it simply has none left. */
    fun personRecords(): List<PersonRecord> = readableDatabase.rawQuery(
        "SELECT g.uuid, g.name, MAX(g.updated_at, IFNULL(MAX(s.updated_at), 0)) FROM face_groups g JOIN face_samples s ON s.group_id = g.id WHERE g.uuid IS NOT NULL GROUP BY g.id",
        null,
    ).use { c -> buildList { while (c.moveToNext()) add(PersonRecord(c.getString(0), c.getString(1), c.getLong(2))) } }

    // ponytail: the whole set goes over on every sync — last-write-wins makes that safe and self-healing, and
    // 387 faces are ~260 KB of base64. Switch to an updated_at cursor if a library ever outgrows one request.
    fun faceRecords(): List<FaceRecord> = readableDatabase.rawQuery(
        "SELECT s.uuid, s.photo_key, s.left_edge, s.top_edge, s.right_edge, s.bottom_edge, s.embedding, s.quality, g.uuid, s.updated_at FROM face_samples s LEFT JOIN face_groups g ON g.id = s.group_id WHERE s.uuid IS NOT NULL",
        null,
    ).use { c -> buildList { while (c.moveToNext()) add(FaceRecord(
        c.getString(0), c.getString(1), android.graphics.Rect(c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5)),
        c.getBlob(6), c.getFloat(7), if (c.isNull(8)) null else c.getString(8), c.getLong(9),
    )) } }

    /** Labels are the search tags Photos shows. They are derived, never hand-deleted, so they only ever merge. */
    fun allLabels(): Map<String, List<String>> = readableDatabase.rawQuery("SELECT photo_key, label FROM photo_ai_label", null)
        .use { c -> buildMap<String, MutableList<String>> { while (c.moveToNext()) getOrPut(c.getString(0)) { mutableListOf() } += c.getString(1) } }

    fun applyIncomingFavorite(photoKey: String, favorite: Boolean, updatedAt: Long) = writableDatabase.inTransaction {
        if (updatedAt <= localUpdatedAt("photo_state", "photo_key", photoKey)) return@inTransaction
        insertWithOnConflict("photo_state", null, ContentValues().apply {
            put("photo_key", photoKey); put("favorite", if (favorite) 1 else 0); put("updated_at", updatedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun applyIncomingCollection(uuid: String, name: String, deleted: Boolean, updatedAt: Long, hiddenFromGallery: Boolean = false) = writableDatabase.inTransaction {
        val local = rawQuery("SELECT id, updated_at FROM collections WHERE uuid = ?", arrayOf(uuid)).use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else null }
        if (local != null) {
            if (updatedAt <= local.second) return@inTransaction
            update("collections", ContentValues().apply {
                put("name", name); put("deleted", if (deleted) 1 else 0); put("hidden", if (hiddenFromGallery) 1 else 0); put("updated_at", updatedAt)
            }, "id = ?", arrayOf(local.first.toString()))
            return@inTransaction
        }
        if (deleted) return@inTransaction // nothing here to bury
        // The name column is UNIQUE: a collection of the same name made on both devices is the same collection.
        val sameName = rawQuery("SELECT id FROM collections WHERE name = ? COLLATE NOCASE", arrayOf(name)).use { if (it.moveToFirst()) it.getLong(0) else null }
        if (sameName != null) update("collections", ContentValues().apply { put("uuid", uuid); put("deleted", 0); put("updated_at", updatedAt) }, "id = ?", arrayOf(sameName.toString()))
        else insertWithOnConflict("collections", null, ContentValues().apply {
            put("name", name); put("uuid", uuid); put("deleted", if (deleted) 1 else 0)
            put("hidden", if (hiddenFromGallery) 1 else 0); put("updated_at", updatedAt)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun applyIncomingCollectionItem(collectionUuid: String, photoKey: String, deleted: Boolean, updatedAt: Long) = writableDatabase.inTransaction {
        val collectionId = rawQuery("SELECT id FROM collections WHERE uuid = ?", arrayOf(collectionUuid)).use { if (it.moveToFirst()) it.getLong(0) else null } ?: return@inTransaction
        val local = rawQuery("SELECT updated_at FROM collection_membership WHERE collection_id = ? AND photo_key = ?", arrayOf(collectionId.toString(), photoKey)).use { if (it.moveToFirst()) it.getLong(0) else -1L }
        if (updatedAt <= local) return@inTransaction
        insertWithOnConflict("collection_membership", null, ContentValues().apply {
            put("collection_id", collectionId); put("photo_key", photoKey); put("deleted", if (deleted) 1 else 0); put("updated_at", updatedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** A name given on the computer. Unknown people are not created here: the phone owns its own grouping. */
    fun applyIncomingPerson(uuid: String, name: String, updatedAt: Long) = writableDatabase.inTransaction {
        val local = rawQuery("SELECT id, updated_at FROM face_groups WHERE uuid = ?", arrayOf(uuid)).use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else null } ?: return@inTransaction
        if (updatedAt <= local.second) return@inTransaction
        update("face_groups", ContentValues().apply { put("name", name); put("updated_at", updatedAt) }, "id = ?", arrayOf(local.first.toString()))
    }

    /**
     * A face the phone already knows, moved to another person on the computer. Faces the computer found on its
     * own are not inserted: their box is in the desktop's coordinates and the phone re-detects them itself.
     */
    /**
     * A face from the computer. One it already knew is only re-pointed at a person; one this phone has never
     * seen is inserted whole, because the computer's detector finds faces ML Kit misses and dropping them would
     * mean that photo never appears under that person here (SYNC_PLAN.md 6m).
     *
     * `bounds` arrive as fractions of the upright photo and are given here in the pixels of the bitmap the
     * analyser works in, which is the only coordinate system the rest of this store knows. A face whose person
     * has not arrived yet is skipped, not guessed at: the next sync brings it.
     */
    fun applyIncomingFace(
        uuid: String,
        personUuid: String?,
        updatedAt: Long,
        photoKey: String? = null,
        bounds: Rect? = null,
        embedding: ByteArray? = null,
        quality: Float = 1f,
    ) = writableDatabase.inTransaction {
        val groupId = personUuid?.let { p -> rawQuery("SELECT id FROM face_groups WHERE uuid = ?", arrayOf(p)).use { if (it.moveToFirst()) it.getLong(0) else null } }
        val local = rawQuery("SELECT id, updated_at FROM face_samples WHERE uuid = ?", arrayOf(uuid)).use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else null }
        if (local != null) {
            if (updatedAt <= local.second || groupId == null) return@inTransaction
            // A guess never overwrites a decision: "Person 41" is what the grouping called someone it had not
            // been told about, a name is what a human typed. Newest-wins decides between two of the same kind,
            // never between those two — otherwise a device that has just re-analysed from scratch can un-name a
            // whole library, which is exactly what happened on 2026-09-23.
            val incomingIsAGuess = isGeneratedPersonName(personName(groupId))
            val hereItHasAName = groupName(local.first).let { it.isNotBlank() && !isGeneratedPersonName(it) }
            if (incomingIsAGuess && hereItHasAName) return@inTransaction
            update("face_samples", ContentValues().apply { put("group_id", groupId); put("updated_at", updatedAt) }, "id = ?", arrayOf(local.first.toString()))
            return@inTransaction
        }
        if (groupId == null || photoKey == null || bounds == null || embedding == null || bounds.isEmpty) return@inTransaction
        if (overlappingFace(photoKey, bounds) != null) return@inTransaction // this phone found the same face itself
        insertWithOnConflict("face_samples", null, ContentValues().apply {
            put("photo_key", photoKey)
            put("left_edge", bounds.left)
            put("top_edge", bounds.top)
            put("right_edge", bounds.right)
            put("bottom_edge", bounds.bottom)
            put("embedding", embedding)
            put("group_id", groupId)
            put("quality", quality)
            put("uuid", uuid)
            put("updated_at", updatedAt)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun SQLiteDatabase.personName(groupId: Long): String =
        rawQuery("SELECT name FROM face_groups WHERE id = ?", arrayOf(groupId.toString())).use { if (it.moveToFirst()) it.getString(0) else "" }

    /** The name of the group a face sits in now, or "" when it sits in none. */
    private fun SQLiteDatabase.groupName(sampleId: Long): String = rawQuery(
        "SELECT g.name FROM face_samples s JOIN face_groups g ON g.id = s.group_id WHERE s.id = ?", arrayOf(sampleId.toString()),
    ).use { if (it.moveToFirst()) it.getString(0) else "" }

    /** Two boxes on one photo that overlap this much are the same face, whichever detector found it first. */
    private fun SQLiteDatabase.overlappingFace(photoKey: String, bounds: Rect): Long? = rawQuery(
        "SELECT id, left_edge, top_edge, right_edge, bottom_edge FROM face_samples WHERE photo_key = ?", arrayOf(photoKey),
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val other = Rect(cursor.getInt(1), cursor.getInt(2), cursor.getInt(3), cursor.getInt(4))
            if (faceOverlap(bounds, other) >= SAME_FACE_OVERLAP) return cursor.getLong(0)
        }
        null
    }

    fun applyIncomingLabels(photoKey: String, labels: List<String>) = writableDatabase.inTransaction {
        labels.forEach { label -> insertWithOnConflict("photo_ai_label", null, ContentValues().apply { put("photo_key", photoKey); put("label", label) }, SQLiteDatabase.CONFLICT_IGNORE) }
    }

    private fun SQLiteDatabase.localUpdatedAt(table: String, keyColumn: String, key: String): Long =
        rawQuery("SELECT updated_at FROM $table WHERE $keyColumn = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getLong(0) else -1L }

    fun peopleAnalysisEnabled(): Boolean = galleryPreferences.getBoolean("people_analysis_enabled", false)

    fun searchQuality(): PhotoSearchQuality = if (galleryPreferences.getString("search_quality", PhotoSearchQuality.Fast.name) == PhotoSearchQuality.Advanced.name) PhotoSearchQuality.Advanced else PhotoSearchQuality.Fast

    fun setSearchQuality(quality: PhotoSearchQuality) { galleryPreferences.edit().putString("search_quality", quality.name).apply() }

    fun documentsAnalysisEnabled(): Boolean = galleryPreferences.getBoolean("documents_analysis_enabled", false)

    fun setPeopleAnalysisEnabled(enabled: Boolean) { galleryPreferences.edit().putBoolean("people_analysis_enabled", enabled).apply() }

    fun setDocumentsAnalysisEnabled(enabled: Boolean) { galleryPreferences.edit().putBoolean("documents_analysis_enabled", enabled).apply() }

    private fun analysisModelVersion(): String = "local-v7-${searchQuality().name.lowercase(Locale.ROOT)}"

    fun hidesScreenshotsFromGallery(): Boolean = galleryPreferences.getBoolean("hide_screenshots", false)

    /** My albums whose photos stay out of the Gallery view (by album UUID, so renames and sync keep it). */
    fun albumsHiddenFromGallery(): Set<String> = keysFor("SELECT uuid FROM collections WHERE hidden = 1 AND uuid IS NOT NULL", emptyArray())

    fun setAlbumHiddenFromGallery(uuid: String, hide: Boolean) {
        writableDatabase.update("collections", ContentValues().apply {
            put("hidden", if (hide) 1 else 0); put("updated_at", System.currentTimeMillis())
        }, "uuid = ?", arrayOf(uuid))
    }

    fun hidesDocumentsFromGallery(): Boolean = galleryPreferences.getBoolean("hide_documents", false)

    fun hidesPeopleFromCollections(): Boolean = galleryPreferences.getBoolean("hide_people_collection", false)

    fun hidesDocumentsFromCollections(): Boolean = galleryPreferences.getBoolean("hide_documents_collection", false)

    fun setHidesScreenshotsFromGallery(hide: Boolean) { galleryPreferences.edit().putBoolean("hide_screenshots", hide).apply(); touchViewSettings() }

    fun setHidesDocumentsFromGallery(hide: Boolean) { galleryPreferences.edit().putBoolean("hide_documents", hide).apply(); touchViewSettings() }

    /**
     * The two "hide these from Photos" answers describe the library, not this phone, so they cross to the
     * computer. What a device should *do* — run face or document analysis, which model to use — stays local:
     * syncing that would start hours of work on a device that never asked for it.
     */
    fun viewSettingsUpdatedAt(): Long = galleryPreferences.getLong("view_settings_updated_at", 0)

    fun applyIncomingViewSettings(hideScreenshots: Boolean, hideDocuments: Boolean, updatedAt: Long) {
        if (updatedAt <= viewSettingsUpdatedAt()) return
        galleryPreferences.edit().putBoolean("hide_screenshots", hideScreenshots).putBoolean("hide_documents", hideDocuments)
            .putLong("view_settings_updated_at", updatedAt).apply()
    }

    private fun touchViewSettings() { galleryPreferences.edit().putLong("view_settings_updated_at", System.currentTimeMillis()).apply() }

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
            val now = System.currentTimeMillis()
            insertWithOnConflict("photo_state", null, ContentValues().apply {
                put("photo_key", key)
                put(column, if (value) 1 else 0)
                put("updated_at", now)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            update("photo_state", ContentValues().apply { put(column, if (value) 1 else 0); put("updated_at", now) }, "photo_key = ?", arrayOf(key))
        }
    }

    private inline fun <T> SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        return try { block().also { setTransactionSuccessful() } } finally { endTransaction() }
    }

    private fun createAiTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS photo_ai_record (photo_key TEXT PRIMARY KEY, source_fingerprint TEXT NOT NULL, type TEXT, subtype TEXT, confidence REAL, model_version TEXT, policy_version INTEGER, user_verified INTEGER NOT NULL DEFAULT 0, review_state TEXT NOT NULL DEFAULT 'none', updated_at INTEGER NOT NULL DEFAULT 0)")
        createFaceTables(db)
    }

    private fun createFaceTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS face_samples (id INTEGER PRIMARY KEY, photo_key TEXT NOT NULL, left_edge INTEGER NOT NULL, top_edge INTEGER NOT NULL, right_edge INTEGER NOT NULL, bottom_edge INTEGER NOT NULL, embedding BLOB NOT NULL, group_id INTEGER, quality REAL NOT NULL DEFAULT 1.0, updated_at INTEGER NOT NULL DEFAULT 0, UNIQUE(photo_key, left_edge, top_edge, right_edge, bottom_edge))")
    }

    private fun createFaceGroupingTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS face_groups (id INTEGER PRIMARY KEY, name TEXT NOT NULL, updated_at INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS face_reviews (id INTEGER PRIMARY KEY, photo_key TEXT NOT NULL, face_sample_id INTEGER NOT NULL, candidate_group_id INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'pending', UNIQUE(face_sample_id, candidate_group_id))")
    }

    private fun createLabelTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS photo_ai_label (photo_key TEXT NOT NULL, label TEXT NOT NULL COLLATE NOCASE, PRIMARY KEY(photo_key, label))")
    }

    private fun SQLiteDatabase.createFaceGroup(): Long = insertOrThrow("face_groups", null, ContentValues().apply {
        put("updated_at", System.currentTimeMillis())
        put("uuid", UUID.randomUUID().toString())
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

    fun visibleInGallery(isScreenshot: Boolean, isDocument: Boolean, hideScreenshots: Boolean, hideDocuments: Boolean, inHiddenAlbum: Boolean = false): Boolean =
        !(hideScreenshots && isScreenshot) && !(hideDocuments && isDocument) && !inHiddenAlbum
}
