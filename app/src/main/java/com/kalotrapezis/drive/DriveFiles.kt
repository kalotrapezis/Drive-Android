package com.kalotrapezis.drive

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale

private const val RECENTS_PREFS = "drive_recents"
private const val RECENTS_KEY = "items"
private const val RECENTS_LIMIT = 50
private const val OPENERS_PREFS = "drive_openers"
const val FILE_OPENER_CHOSEN = "com.kalotrapezis.drive.FILE_OPENER_CHOSEN"
const val FILE_OPENER_PATH = "drive_opener_path"

data class DriveItem(val file: File, val relativePath: String, val isDirectory: Boolean)
data class DriveRecent(val relativePath: String, val openedAt: Long)
data class DriveDestination(val relativePath: String, val label: String)

enum class DriveFolderColor { Blue, Green, Yellow, Red, Purple }
data class DriveSpaceUsage(val bytesByType: Map<String, Long>) {
    val totalBytes: Long get() = bytesByType.values.sum()
}

/** File boundary rules are kept independent from Compose so they can be tested without a device. */
object DriveRules {
    /**
     * Folders the app itself relies on: the scanner saves into Scanned Documents, and both sync by these paths. They
     * are always there, marked with an emblem, and cannot be renamed, moved or put in the Trash — their contents can.
     */
    val SYSTEM_FOLDERS = listOf("Documents", "Documents/Scanned Documents")
    fun isSystem(relativePath: String) = relativePath in SYSTEM_FOLDERS
    fun ensureSystemFolders(root: File) = SYSTEM_FOLDERS.forEach { File(root, it).mkdirs() }
    private fun requireMovable(relativePath: String) = require(!isSystem(relativePath)) { "${relativePath.substringAfterLast('/')} is a system folder and stays where it is." }

    fun inside(root: File, candidate: File): Boolean {
        val rootPath = root.canonicalFile.path
        val candidatePath = candidate.canonicalFile.path
        return candidatePath == rootPath || candidatePath.startsWith("$rootPath${File.separator}")
    }

    fun folder(root: File, relativePath: String): File {
        require(relativePath.isSafeDriveRelativePath()) { "Invalid Drive folder." }
        return File(root, relativePath).canonicalFile.also {
            require(inside(root, it) && it.isDirectory) { "Folder is outside Drive." }
        }
    }

    fun file(root: File, relativePath: String): File {
        require(relativePath.isSafeDriveRelativePath()) { "Invalid Drive file." }
        return File(root, relativePath).canonicalFile.also {
            require(inside(root, it) && it.isFile) { "File is outside Drive." }
        }
    }

    /**
     * A place inside Drive for something that is not there yet — where a synced file lands. The path is checked
     * the same way, and a name already taken is kept: "letter.txt" becomes "letter (2).txt", never a replacement.
     */
    fun newFile(root: File, relativePath: String): File {
        require(relativePath.isSafeDriveRelativePath() && relativePath.isNotEmpty()) { "Invalid Drive file." }
        val wanted = File(root, relativePath).canonicalFile
        require(inside(root, wanted)) { "File is outside Drive." }
        if (!wanted.exists()) return wanted
        val base = wanted.name.substringBeforeLast('.', wanted.name)
        val extension = wanted.name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 2
        while (true) {
            val candidate = File(wanted.parentFile, "$base ($n)$extension")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    fun item(root: File, relativePath: String): File {
        require(relativePath.isSafeDriveRelativePath()) { "Invalid Drive item." }
        return File(root, relativePath).canonicalFile.also {
            require(inside(root, it) && it.exists()) { "Item is outside Drive." }
        }
    }

    fun relative(root: File, file: File): String {
        require(inside(root, file)) { "Item is outside Drive." }
        return file.canonicalFile.relativeTo(root.canonicalFile).invariantSeparatorsPath
    }

    fun parent(relativePath: String): String = relativePath.substringBeforeLast('/', "")

    fun destinations(root: File): List<DriveDestination> = root.walkTopDown()
        .filter { it.isDirectory && inside(root, it) }
        .map { file -> relative(root, file) }
        .filterNot { it == TRASH_FOLDER || it.startsWith("$TRASH_FOLDER/") }
        .sortedWith(compareBy<String> { it.isNotEmpty() }.thenBy { it.lowercase(Locale.ROOT) })
        .map { DriveDestination(it, if (it.isEmpty()) "Drive" else it) }
        .toList()

    fun allItems(root: File, includeTrash: Boolean = false): List<DriveItem> = root.walkTopDown()
        .onEnter { folder -> inside(root, folder) && (includeTrash || folder == root || relative(root, folder) != TRASH_FOLDER) }
        .filter { it != root && it.exists() && inside(root, it) }
        .map { DriveItem(it.canonicalFile, relative(root, it), it.isDirectory) }
        .toList()

    fun spaceUsage(root: File): DriveSpaceUsage = DriveSpaceUsage(
        allItems(root, includeTrash = true).asSequence().filterNot { it.isDirectory }
            .groupBy { fileTypeGroup(it.file.extension) }
            .mapValues { (_, items) -> items.sumOf { it.file.length() } },
    )

    fun rename(root: File, relativePath: String, newName: String): String {
        require(newName.isSafeDriveName()) { "Invalid name." }
        requireMovable(relativePath)
        val source = item(root, relativePath)
        val target = File(source.parentFile, newName).canonicalFile
        moveExisting(root, source, target)
        return relative(root, target)
    }

    fun copy(root: File, relativePath: String, destinationRelativePath: String): String {
        val source = item(root, relativePath)
        val destination = folder(root, destinationRelativePath)
        require(!source.isDirectory || !inside(source, destination)) { "A folder cannot be copied into itself." }
        val target = File(destination, source.name).canonicalFile
        require(inside(root, target) && !target.exists()) { "A file with this name already exists." }
        if (source.isDirectory) source.copyRecursively(target, overwrite = false) else Files.copy(source.toPath(), target.toPath())
        return relative(root, target)
    }

    fun move(root: File, relativePath: String, destinationRelativePath: String): String {
        requireMovable(relativePath)
        val source = item(root, relativePath)
        val destination = folder(root, destinationRelativePath)
        require(!source.isDirectory || !inside(source, destination)) { "A folder cannot be moved into itself." }
        val target = File(destination, source.name).canonicalFile
        moveExisting(root, source, target)
        return relative(root, target)
    }

    fun moveToTrash(root: File, relativePath: String): String {
        val trash = File(root, TRASH_FOLDER).canonicalFile
        require(inside(root, trash)) { "Trash is outside Drive." }
        if (!trash.exists()) require(trash.mkdirs()) { "Could not create Trash." }
        return move(root, relativePath, TRASH_FOLDER)
    }

    fun emptyTrash(root: File): Int {
        val trash = File(root, TRASH_FOLDER).canonicalFile
        require(inside(root, trash)) { "Trash is outside Drive." }
        if (!trash.exists()) return 0
        val items = trash.listFiles().orEmpty()
        items.forEach { item ->
            require(inside(root, item)) { "Trash contains an unsafe item." }
            check(item.deleteRecursively()) { "Could not permanently delete ${item.name}." }
        }
        return items.size
    }

    private fun moveExisting(root: File, source: File, target: File) {
        require(inside(root, target) && target != source && !target.exists()) { "A file with this name already exists." }
        Files.move(source.toPath(), target.toPath())
    }
}

fun fileTypeGroup(extension: String): String = when (extension.lowercase(Locale.ROOT)) {
    "doc", "docx", "odt" -> "Documents"
    "xls", "xlsx", "csv" -> "Spreadsheets"
    "pdf" -> "PDF"
    "txt", "md" -> "Text"
    "excalidraw" -> "Drawings"
    "jpg", "jpeg", "png", "webp", "gif", "heic", "heif" -> "Pictures"
    "mp4", "mov", "mkv", "avi", "webm" -> "Videos"
    else -> "Other"
}

private fun String.isSafeDriveRelativePath(): Boolean = isEmpty() ||
    (!startsWith('/') && split('/').all { it.isNotEmpty() && it != "." && it != ".." })

private fun String.isSafeDriveName(): Boolean = isNotBlank() && trim() == this &&
    this !in setOf(".", "..") && '/' !in this && '\\' !in this

private const val TRASH_FOLDER = "Trash"

fun listDriveFolder(root: File, relativePath: String): List<DriveItem> {
    val folder = DriveRules.folder(root, relativePath)
    return folder.listFiles().orEmpty()
        .filter { DriveRules.inside(root, it) }
        // Trash has its own way in; as a folder in the list it is just something to open by accident.
        .filterNot { relativePath.isEmpty() && it.name == TRASH_FOLDER }
        .map { file -> DriveItem(file.canonicalFile, DriveRules.relative(root, file), file.isDirectory) }
        .sortedWith(compareBy<DriveItem>({ !it.isDirectory }, { it.file.name.lowercase(Locale.ROOT) }))
}

object DriveRecentsRules {
    fun record(existing: List<DriveRecent>, relativePath: String, openedAt: Long): List<DriveRecent> =
        (listOf(DriveRecent(relativePath, openedAt)) + existing.filterNot { it.relativePath == relativePath }).take(RECENTS_LIMIT)
}

class DriveRecents(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(RECENTS_PREFS, Context.MODE_PRIVATE)

    fun read(root: File): List<DriveRecent> {
        val stored = runCatching { JSONArray(preferences.getString(RECENTS_KEY, "[]")) }.getOrElse { JSONArray() }
        val valid = buildList {
            for (index in 0 until stored.length()) {
                val item = stored.optJSONObject(index) ?: continue
                val path = item.optString("path")
                val openedAt = item.optLong("openedAt")
                if (openedAt > 0 && runCatching { DriveRules.file(root, path) }.isSuccess) add(DriveRecent(path, openedAt))
            }
        }.distinctBy { it.relativePath }.sortedByDescending { it.openedAt }.take(RECENTS_LIMIT)
        save(valid)
        return valid
    }

    fun record(root: File, relativePath: String, openedAt: Long) {
        DriveRules.file(root, relativePath)
        save(DriveRecentsRules.record(read(root), relativePath, openedAt))
    }

    /**
     * Stored recents, without pruning to what is on this device: a file opened on the computer is still a recent
     * file, and each side drops the entries it cannot see when it reads them (`read`).
     */
    fun all(): List<DriveRecent> {
        val stored = runCatching { JSONArray(preferences.getString(RECENTS_KEY, "[]")) }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until stored.length()) {
                val item = stored.optJSONObject(index) ?: continue
                val openedAt = item.optLong("openedAt")
                if (openedAt > 0) add(DriveRecent(item.optString("path"), openedAt))
            }
        }
    }

    /** The most recent open of each file wins, whichever device it happened on. */
    fun merge(incoming: List<DriveRecent>) {
        if (incoming.isEmpty()) return
        save((all() + incoming.filter { it.relativePath.isSafeDriveRelativePath() })
            .groupBy { it.relativePath }.map { (path, opens) -> DriveRecent(path, opens.maxOf { it.openedAt }) }
            .sortedByDescending { it.openedAt }.take(RECENTS_LIMIT))
    }

    private fun save(items: List<DriveRecent>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("path", it.relativePath).put("openedAt", it.openedAt)) }
        preferences.edit().putString(RECENTS_KEY, array.toString()).apply()
    }
}

/** One file's or folder's user metadata, as it travels between the phone and the computer. */
data class DriveMetaRecord(val path: String, val favorite: Boolean, val color: String?, val tags: Set<String>, val updatedAt: Long)

class DriveMetadata(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("drive_metadata", Context.MODE_PRIVATE)

    fun isFavorite(relativePath: String): Boolean = favorites().contains(relativePath)

    fun setFavorite(relativePath: String, favorite: Boolean) {
        require(relativePath.isSafeDriveRelativePath()) { "Invalid Drive item." }
        val updated = favorites()
        if (favorite) updated.add(relativePath) else updated.remove(relativePath)
        preferences.edit().putStringSet("favorites", updated).apply()
        touch(relativePath)
    }

    fun color(relativePath: String): DriveFolderColor? = colors()[relativePath]?.let { runCatching { DriveFolderColor.valueOf(it) }.getOrNull() }

    fun setColor(relativePath: String, color: DriveFolderColor?) {
        require(relativePath.isSafeDriveRelativePath()) { "Invalid Drive folder." }
        val updated = colors()
        if (color == null) updated.remove(relativePath) else updated[relativePath] = color.name
        saveColors(updated)
        touch(relativePath)
    }

    fun tags(relativePath: String): Set<String> = tagsByPath()[relativePath].orEmpty()

    fun createTag(name: String): String {
        val cleaned = DriveTagRules.name(name)
        preferences.edit().putStringSet("known_tags", knownTags() + cleaned).apply()
        return cleaned
    }

    fun setTags(relativePath: String, names: Set<String>) {
        require(relativePath.isSafeDriveRelativePath()) { "Invalid Drive item." }
        val cleaned = DriveTagRules.names(names)
        val updated = tagsByPath().toMutableMap()
        if (cleaned.isEmpty()) updated.remove(relativePath) else updated[relativePath] = cleaned
        saveTags(updated)
        preferences.edit().putStringSet("known_tags", knownTags() + cleaned).apply()
        touch(relativePath)
    }

    fun allTags(root: File): List<String> = (knownTags() + tagsByPath().filterKeys { path ->
        path != TRASH_FOLDER && !path.startsWith("$TRASH_FOLDER/") && runCatching { DriveRules.item(root, path) }.isSuccess
    }
        .values.flatten()).sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun favorites(root: File): List<DriveItem> {
        val valid = favorites().filter { runCatching { DriveRules.item(root, it) }.isSuccess }.toMutableSet()
        preferences.edit().putStringSet("favorites", valid).apply()
        return valid.filterNot { it == TRASH_FOLDER || it.startsWith("$TRASH_FOLDER/") }.map { relative ->
            val file = DriveRules.item(root, relative)
            DriveItem(file, relative, file.isDirectory)
        }.sortedBy { it.file.name.lowercase(Locale.ROOT) }
    }

    fun rewritePath(from: String, to: String) {
        preferences.edit().putStringSet("favorites", favorites().map { DriveStoredPathRules.rewrite(it, from, to) }.toSet()).apply()
        saveColors(colors().mapKeys { DriveStoredPathRules.rewrite(it.key, from, to) })
        saveTags(tagsByPath().mapKeys { DriveStoredPathRules.rewrite(it.key, from, to) })
        saveTimes(times().mapKeys { DriveStoredPathRules.rewrite(it.key, from, to) })
        touch(to)
    }

    // --- Sync (SYNC_PLAN.md phase 6d). Files are keyed by their Drive-relative path on both devices, so what a
    // record needs beyond its values is only a time: the newest edit of a path wins, and a tag that the newer
    // side's set no longer holds has been removed. Moving to Drive/Trash/ is a path change like any other.

    /** Everything the user set on a file or folder, one record per path. */
    fun records(): List<DriveMetaRecord> {
        val favorites = favorites()
        val colors = colors()
        val tags = tagsByPath()
        val times = times()
        return (favorites + colors.keys + tags.keys + times.keys).distinct().map { path ->
            DriveMetaRecord(path, path in favorites, colors[path], tags[path].orEmpty(), times[path] ?: 0L)
        }
    }

    /** From the computer. Applied only where its edit is newer than this phone's (last-write-wins per path). */
    fun applyIncoming(record: DriveMetaRecord) {
        if (!record.path.isSafeDriveRelativePath() || record.updatedAt <= (times()[record.path] ?: -1L)) return
        val favorites = favorites()
        if (record.favorite) favorites.add(record.path) else favorites.remove(record.path)
        val colors = colors()
        if (record.color == null) colors.remove(record.path) else colors[record.path] = record.color
        val tags = tagsByPath().toMutableMap()
        val cleaned = runCatching { DriveTagRules.names(record.tags) }.getOrDefault(emptySet())
        if (cleaned.isEmpty()) tags.remove(record.path) else tags[record.path] = cleaned
        preferences.edit().putStringSet("favorites", favorites).putStringSet("known_tags", knownTags() + cleaned).apply()
        saveColors(colors)
        saveTags(tags)
        saveTimes(times() + (record.path to record.updatedAt))
    }

    private fun touch(relativePath: String) = saveTimes(times() + (relativePath to System.currentTimeMillis()))

    private fun times(): Map<String, Long> = runCatching { JSONObject(preferences.getString("updated_at", "{}") ?: "{}") }
        .getOrElse { JSONObject() }.let { json -> buildMap { json.keys().forEach { put(it, json.optLong(it)) } } }

    private fun saveTimes(times: Map<String, Long>) {
        val json = JSONObject()
        times.forEach { (path, at) -> json.put(path, at) }
        preferences.edit().putString("updated_at", json.toString()).apply()
    }

    private fun favorites(): MutableSet<String> = preferences.getStringSet("favorites", emptySet()).orEmpty().toMutableSet()
    private fun knownTags(): Set<String> = preferences.getStringSet("known_tags", emptySet()).orEmpty().mapNotNull { name ->
        runCatching { DriveTagRules.name(name) }.getOrNull()
    }.toSet()
    private fun colors(): MutableMap<String, String> = runCatching { JSONObject(preferences.getString("colors", "{}") ?: "{}") }.getOrElse { JSONObject() }
        .let { json -> buildMap { json.keys().forEach { key -> put(key, json.optString(key)) } }.toMutableMap() }
    private fun saveColors(colors: Map<String, String>) {
        val json = JSONObject()
        colors.forEach { (path, color) -> json.put(path, color) }
        preferences.edit().putString("colors", json.toString()).apply()
    }

    private fun tagsByPath(): Map<String, Set<String>> = runCatching {
        val json = JSONObject(preferences.getString("tags", "{}") ?: "{}")
        buildMap {
            json.keys().forEach { path ->
                val values = json.optJSONArray(path) ?: return@forEach
                put(path, (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }.toSet())
            }
        }
    }.getOrDefault(emptyMap())

    private fun saveTags(tags: Map<String, Set<String>>) {
        val json = JSONObject()
        tags.forEach { (path, names) -> json.put(path, JSONArray(names.toList())) }
        preferences.edit().putString("tags", json.toString()).apply()
    }
}

/** A file of the Files module as it is offered to the computer (SYNC_PLAN.md phase 6e). */
data class DriveFileEntry(val relativePath: String, val sizeBytes: Long, val modified: Long, val sha256: String)

/**
 * Every file under Drive, hashed once and remembered against its size and modified time. Content is the
 * identity, so the computer can tell a moved or renamed file from a new one and move its own copy to match —
 * which is also how a move into Drive/Trash/ reaches the other device.
 */
class DriveManifest(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("drive_hashes", Context.MODE_PRIVATE)

    fun entries(root: File): List<DriveFileEntry> = walk(root, root).map { file ->
        val relative = DriveRules.relative(root, file)
        DriveFileEntry(relative, file.length(), file.lastModified(), hash(relative, file))
    }

    private fun walk(root: File, folder: File): List<File> = folder.listFiles().orEmpty()
        .filter { DriveRules.inside(root, it) && !it.name.startsWith(".") }
        .flatMap { if (it.isDirectory) walk(root, it) else listOf(it) }

    private fun hash(relativePath: String, file: File): String {
        val stamp = "${file.length()}:${file.lastModified()}"
        preferences.getString(relativePath, null)?.split('|')?.takeIf { it.size == 2 && it[0] == stamp }?.let { return it[1] }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return SyncRules.hex(digest.digest()).also { preferences.edit().putString(relativePath, "$stamp|$it").apply() }
    }
}

object DriveTagRules {
    fun name(value: String): String = value.trim().also {
        require(it.isNotEmpty() && it.length <= 32 && ',' !in it) { "Tags must be 1–32 characters and cannot contain commas." }
    }

    fun names(values: Set<String>): Set<String> {
        val cleaned = values.map(::name).toSet()
        require(cleaned.size == values.size) { "Tags must be unique." }
        return cleaned
    }
}

object DriveStoredPathRules {
    fun rewrite(path: String, from: String, to: String): String = when {
        path == from -> to
        path.startsWith("$from/") -> "$to${path.removePrefix(from)}"
        else -> path
    }
}

/** Stores the explicit Android handler selected for one Drive-relative file path. */
class DriveOpeners(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(OPENERS_PREFS, Context.MODE_PRIVATE)

    fun component(relativePath: String): ComponentName? {
        require(relativePath.isSafeDriveRelativePath()) { "Invalid Drive file." }
        return preferences.getString(relativePath, null)?.let(ComponentName::unflattenFromString)
    }

    fun set(relativePath: String, component: ComponentName) {
        require(relativePath.isSafeDriveRelativePath()) { "Invalid Drive file." }
        preferences.edit().putString(relativePath, component.flattenToString()).apply()
    }

    fun clear(relativePath: String) {
        require(relativePath.isSafeDriveRelativePath()) { "Invalid Drive file." }
        preferences.edit().remove(relativePath).apply()
    }

    fun rewritePath(from: String, to: String) {
        val edits = preferences.edit()
        preferences.all.forEach { (path, value) ->
            if (value !is String) return@forEach
            val rewritten = DriveStoredPathRules.rewrite(path, from, to)
            if (rewritten != path) {
                edits.remove(path)
                edits.putString(rewritten, value)
            }
        }
        edits.apply()
    }
}

class DriveOpenerReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FILE_OPENER_CHOSEN) return
        val path = intent.getStringExtra(FILE_OPENER_PATH) ?: return
        val component = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_CHOSEN_COMPONENT) ?: return
        if (path.isSafeDriveRelativePath()) DriveOpeners(context).set(path, component)
    }
}
