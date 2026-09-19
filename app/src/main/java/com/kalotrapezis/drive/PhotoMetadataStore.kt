package com.kalotrapezis.drive

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

internal data class PhotoState(val favorite: Boolean = false)

internal data class PhotoCollection(val id: Long, val name: String, val storedCount: Int)

/** Private metadata only. It never changes the MediaStore item or its bytes. */
internal class PhotoMetadataStore(context: Context) : SQLiteOpenHelper(context, "photo_metadata.db", null, 2) {
    private val galleryPreferences = context.getSharedPreferences("photo_gallery", Context.MODE_PRIVATE)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE photo_state (photo_key TEXT PRIMARY KEY, favorite INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE collections (id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE UNIQUE)")
        db.execSQL("CREATE TABLE collection_membership (collection_id INTEGER NOT NULL, photo_key TEXT NOT NULL, PRIMARY KEY(collection_id, photo_key), FOREIGN KEY(collection_id) REFERENCES collections(id) ON DELETE CASCADE)")
        createAiTables(db)
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

    fun setFavorite(keys: Collection<String>, favorite: Boolean) = setState(keys, "favorite", favorite)

    fun classifiedKeys(type: String): Set<String> = keysFor("SELECT photo_key FROM photo_ai_record WHERE type = ?", arrayOf(type))

    fun reviewKeys(): Set<String> = keysFor("SELECT photo_key FROM photo_ai_record WHERE review_state = 'pending'", emptyArray())

    fun hidesScreenshotsFromGallery(): Boolean = galleryPreferences.getBoolean("hide_screenshots", false)

    fun hidesDocumentsFromGallery(): Boolean = galleryPreferences.getBoolean("hide_documents", false)

    fun setHidesScreenshotsFromGallery(hide: Boolean) { galleryPreferences.edit().putBoolean("hide_screenshots", hide).apply() }

    fun setHidesDocumentsFromGallery(hide: Boolean) { galleryPreferences.edit().putBoolean("hide_documents", hide).apply() }

    private fun keysFor(query: String, arguments: Array<String>): Set<String> = readableDatabase.rawQuery(query, arguments).use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

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
    }
}

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
