package com.kalotrapezis.drive

import android.Manifest
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import java.io.File
import android.os.Environment
import android.media.MediaScannerConnection
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Photo edits reuse the scanner's tools: crop & straighten corners, 90° rotation and brush markup. */
internal data class PhotoEdit(val quad: DocumentQuad? = null, val rotation: Int = 0, val strokes: List<ScanStroke> = emptyList()) {
    fun rotatedBy(degrees: Int) = copy(rotation = (rotation + degrees + 360) % 360, strokes = strokes.map { it.rotated(degrees) })
}

private const val PREVIEW_EDGE = 2048
// Full resolution for the 50 MP sensor (8160 px); needs android:largeHeap. Out of memory fails before anything is written.
private const val SAVE_EDGE = 8192

private val MARKUP_COLORS = listOf(
    android.graphics.Color.WHITE, android.graphics.Color.BLACK, 0xFFE53935.toInt(), 0xFFFDD835.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(),
)

private fun decodePhoto(context: Context, uri: Uri, maxEdge: Int): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        val scale = minOf(1f, maxEdge.toFloat() / maxOf(info.size.width, info.size.height))
        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = true
    }

private fun renderPhotoEdit(source: Bitmap, edit: PhotoEdit, maxEdge: Float, withStrokes: Boolean, recycleSource: Boolean = false): Bitmap {
    // Recycling each intermediate keeps a full-size save to two bitmaps at a time.
    val cropped = edit.quad?.let { quad -> cropScan(source, quad, maxEdge).also { if (recycleSource) source.recycle() } } ?: source
    val rotated = rotateScan(cropped, edit.rotation).also { if (recycleSource && it !== cropped) cropped.recycle() }
    val result = if (withStrokes && !rotated.isMutable) rotated.copy(Bitmap.Config.ARGB_8888, true).also { if (recycleSource) rotated.recycle() } else rotated
    if (withStrokes) drawStrokes(result, edit.strokes)
    return result
}

private fun renderForSave(context: Context, original: Uri, edit: PhotoEdit): Bitmap = try {
    renderPhotoEdit(decodePhoto(context, original, SAVE_EDGE), edit, SAVE_EDGE.toFloat(), withStrokes = true, recycleSource = true)
} catch (_: OutOfMemoryError) {
    error("Not enough memory to save this photo at full size. Nothing was changed.")
}

/**
 * Saves the edit as a new photo beside the original (same folder and date, location copied), never touching the
 * original. A failed write removes only the new, still-pending entry.
 */
private fun savePhotoCopy(context: Context, entry: Entry, edit: PhotoEdit): Uri {
    val original = checkNotNull(entry.contentUri) { "This photo is no longer available." }
    val image = renderForSave(context, original, edit)
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "${entry.name.substringBeforeLast('.')}_edited.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, entry.relativePath ?: "DCIM/Camera/")
        if (entry.takenMillis > 0) put(MediaStore.Images.Media.DATE_TAKEN, entry.takenMillis)
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val target = checkNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)) { "Could not create the edited photo." }
    try {
        checkNotNull(resolver.openOutputStream(target)).use { check(image.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "Could not write the edited photo." } }
        writeExif(context, target, readExif(context, original))
        resolver.update(target, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    } catch (failure: Throwable) {
        resolver.delete(target, null, null)
        throw failure
    }
    return target
}

/**
 * The key the gallery will list for [uri] once MediaStore has caught up with the new size (it includes the size).
 * Waits briefly for MediaStore; null if it never settles, which leaves the metadata where it was.
 */
private fun settledPhotoKey(context: Context, uri: Uri): String? {
    val bytes = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: return null
    repeat(20) {
        context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && c.getLong(2) == bytes) return PhotoMetadataRules.stableKey(uri.toString(), c.getString(1) ?: "Unnamed", c.getString(0) ?: "Unnamed", bytes)
        }
        Thread.sleep(100)
    }
    return null
}

/**
 * Replaces the original photo's pixels (after Android's own modify consent). Everything is rendered and encoded
 * first; the original is only opened for writing once the complete new file exists.
 */
private fun replacePhoto(context: Context, entry: Entry, edit: PhotoEdit): Uri {
    val original = checkNotNull(entry.contentUri) { "This photo is no longer available." }
    val exif = readExif(context, original)
    val encoded = File.createTempFile("edit", ".jpg", context.cacheDir)
    try {
        val image = renderForSave(context, original, edit)
        encoded.outputStream().use { check(image.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "Could not encode the edited photo." } }
        image.recycle()
        checkNotNull(context.contentResolver.openOutputStream(original, "wt")) { "Could not open the photo for writing." }.use { output ->
            encoded.inputStream().use { it.copyTo(output) }
        }
        writeExif(context, original, exif)
    } finally {
        encoded.delete()
    }
    // Refresh MediaStore's size, dimensions and thumbnail for the rewritten file.
    entry.relativePath?.let { folder ->
        MediaScannerConnection.scanFile(context, arrayOf(File(Environment.getExternalStorageDirectory(), folder + entry.name).path), null, null)
    }
    return original
}

private val EXIF_TAGS = listOf(
    ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
    ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
)

/** The original's date, camera and (when the app may read it) location. */
private fun readExif(context: Context, original: Uri): Map<String, String> = runCatching {
    val canReadLocation = context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
    val source = if (canReadLocation) MediaStore.setRequireOriginal(original) else original
    context.contentResolver.openInputStream(source)?.use { stream -> ExifInterface(stream).let { exif -> EXIF_TAGS.mapNotNull { tag -> exif.getAttribute(tag)?.let { tag to it } }.toMap() } }
}.getOrNull().orEmpty()

/** The pixels are already upright, so orientation is reset. */
private fun writeExif(context: Context, target: Uri, tags: Map<String, String>) = runCatching {
    context.contentResolver.openFileDescriptor(target, "rw")?.use { descriptor ->
        val exif = ExifInterface(descriptor.fileDescriptor)
        tags.forEach { (tag, value) -> exif.setAttribute(tag, value) }
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.saveAttributes()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoEditorScreen(entry: Entry, close: () -> Unit, saved: (uri: Uri, newPhotoKey: String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source by produceState<Bitmap?>(null, entry.contentUri) {
        value = entry.contentUri?.let { uri -> withContext(Dispatchers.IO) { runCatching { decodePhoto(context, uri, PREVIEW_EDGE) }.getOrNull() } }
    }
    var edit by remember(entry.contentUri) { mutableStateOf(PhotoEdit()) }
    var cropping by remember { mutableStateOf(false) }
    var marking by remember { mutableStateOf(false) }
    var brushColor by remember { mutableStateOf(0xFFE53935.toInt()) }
    var brushWidth by remember { mutableStateOf(0.012f) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discardOpen by remember { mutableStateOf(false) }
    var saveSheetOpen by remember { mutableStateOf(false) }
    // Geometry is re-rendered only when it changes; strokes are drawn live on top.
    val preview by produceState<Bitmap?>(null, source, edit.quad, edit.rotation) {
        value = source?.let { withContext(Dispatchers.Default) { renderPhotoEdit(it, edit, PREVIEW_EDGE.toFloat(), withStrokes = false) } }
    }
    val changed = edit != PhotoEdit()
    BackHandler {
        when {
            cropping -> cropping = false
            changed -> discardOpen = true
            else -> close()
        }
    }
    if (cropping) {
        CornerEditor(source, edit.quad ?: DocumentQuad.fullFrame(), DocumentQuad.fullFrame(), "Reset", done = { quad ->
            edit = edit.copy(quad = quad.takeUnless { it == DocumentQuad.fullFrame() })
            cropping = false
        }, cancel = { cropping = false })
        return
    }
    fun save(replace: Boolean) {
        saving = true
        error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                if (replace) replacePhoto(context, entry, edit).let { it to settledPhotoKey(context, it) } else savePhotoCopy(context, entry, edit) to null
            } }
                .onSuccess { (uri, newKey) -> saved(uri, newKey) }
                .onFailure { error = it.message ?: "Could not save the edited photo." }
            saving = false
        }
    }
    // Replacing another app's photo needs Android's own "allow modify" consent first.
    val modifyConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) save(replace = true)
    }
    fun replace() {
        val uri = entry.contentUri ?: return
        val request = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
        modifyConsent.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            val image = preview
            if (image == null) CircularProgressIndicator()
            else {
                Image(image.asImageBitmap(), contentDescription = entry.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                ScanStrokeLayer(image.width, image.height, edit.strokes, marking, brushColor, brushWidth) { stroke -> edit = edit.copy(strokes = edit.strokes + stroke) }
            }
            if (saving) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        Surface(
            color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AnimatedVisibility(marking) {
                    PaintPanel(MARKUP_COLORS, "Draw on the photo.", brushColor, { brushColor = it }, brushWidth, { brushWidth = it }) {
                        edit = edit.copy(strokes = edit.strokes.dropLast(1))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { marking = false; cropping = true }, enabled = source != null && !saving) { Icon(painterResource(R.drawable.ic_crop), contentDescription = "Crop and straighten") }
                    IconButton(onClick = { edit = edit.rotatedBy(270) }, enabled = !saving) { Icon(painterResource(R.drawable.ic_rotate_left), contentDescription = "Rotate left") }
                    IconButton(onClick = { edit = edit.rotatedBy(90) }, enabled = !saving) { Icon(painterResource(R.drawable.ic_rotate_right), contentDescription = "Rotate right") }
                    ScanIslandToggle(R.drawable.ic_edit, "Markup", marking) { marking = !marking }
                    IconButton(onClick = { edit = PhotoEdit() }, enabled = changed && !saving) { Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Undo all edits") }
                    IconButton(onClick = { marking = false; saveSheetOpen = true }, enabled = changed && !saving) { Icon(painterResource(R.drawable.ic_save), contentDescription = "Save or discard") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
        }
    }
    if (saveSheetOpen) ModalBottomSheet(onDismissRequest = { saveSheetOpen = false }, containerColor = islandColor(), contentColor = islandContentColor()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(entry.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            DriveWideAction(R.drawable.ic_save, "Save") { saveSheetOpen = false; replace() }
            DriveWideAction(R.drawable.ic_copy, "Save as copy") { saveSheetOpen = false; save(replace = false) }
            DriveWideAction(R.drawable.ic_cancel, "Discard changes") { saveSheetOpen = false; close() }
        }
    }
    if (discardOpen) AlertDialog(
        onDismissRequest = { discardOpen = false },
        title = { Text("Discard edits?") },
        text = { Text("The original photo is unchanged either way.") },
        confirmButton = { TextButton(onClick = { discardOpen = false; close() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discardOpen = false }) { Text("Keep editing") } },
    )
}

