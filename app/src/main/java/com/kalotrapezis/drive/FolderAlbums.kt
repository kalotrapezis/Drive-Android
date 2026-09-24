package com.kalotrapezis.drive

/**
 * Which folders are photos (SYNC_PLAN.md §7 / D). The phone holds far more pictures than a gallery should show —
 * Viber, SyncThing mirrors, a USB copy, game presets under Download — so a folder is only ever *offered* when it
 * sits where Android keeps photos, and only *shown* once it is a default or you said yes to it.
 */
internal object FolderRules {
    /** Always in, never asked: the camera, screenshots, and what the computer sends back. */
    val DEFAULTS = setOf("camera", "screenshots", "tetra")
    private val PHOTO_ROOTS = setOf("dcim", "pictures", "movies")

    /**
     * The album a file's folder belongs to, or null for a folder that is files, not photos (Drive, SyncThing,
     * Documents, a USB copy, the storage root). Named after the folder under DCIM/Pictures/Movies, so
     * Pictures/Viber, Movies/Viber and pictures/Viber are one "Viber"; everything under Download is one question.
     */
    fun albumName(relativePath: String?): String? {
        val parts = relativePath?.split('/')?.filter { it.isNotBlank() }.orEmpty()
        val root = parts.firstOrNull()?.lowercase() ?: return null
        return when {
            root == "download" -> "Download"
            root !in PHOTO_ROOTS -> null
            parts.size == 1 -> parts[0]
            else -> parts[1]
        }
    }

    fun isDefault(album: String) = album.lowercase() in DEFAULTS

    /** `choices` maps a lower-cased album name to the answer given; no entry means never asked. */
    fun isIncluded(relativePath: String?, choices: Map<String, Boolean>): Boolean =
        albumName(relativePath)?.lowercase()?.let { it in DEFAULTS || choices[it] == true } == true
}

/** A folder found on this phone, for its Help organize card and its row in Gallery tools. */
internal data class DeviceFolder(val name: String, val entries: List<Entry>, val included: Boolean?) {
    val countText get() = "${entries.size} ${if (entries.size == 1) "photo or video" else "photos and videos"}"
}

internal fun deviceFolders(all: List<Entry>, choices: Map<String, Boolean>): List<DeviceFolder> = all
    .groupBy { FolderRules.albumName(it.relativePath)?.lowercase() }
    .mapNotNull { (key, entries) ->
        key?.takeUnless(FolderRules::isDefault)?.let {
            // Named the way most of its files spell it: pictures/Viber should not rename Viber.
            val name = entries.groupingBy { e -> FolderRules.albumName(e.relativePath)!! }.eachCount().maxBy { e -> e.value }.key
            DeviceFolder(name, entries.sortedByDescending(Entry::takenMillis), choices[it])
        }
    }
    .sortedByDescending { it.entries.size }
