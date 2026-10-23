package com.kalotrapezis.drive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.media.ThumbnailUtils
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Files' thumbnails (the user, 23 Oct: "thumbnails in Files … for documents the SVGs you have at the homescreen as
 * overlay at the center"): a picture or a video its own; a document its first page, drawn by Tetra's own Words, Tables
 * and Slides, with Office's glyph for it in the middle. Made once and kept in the app's cache (never in Tetra's synced
 * folder), named by the file's path, size and time, so a changed file gets a new one.
 */
internal object FileThumbs {
    enum class Kind { Picture, Video, Pdf, Words, Tables, Slides }

    private const val SIZE = 384 // px, the longer side: a tablet's grid tile
    private val pictures = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "avif")
    private val videos = setOf("mp4", "mov", "mkv", "avi", "webm", "3gp", "m4v")

    fun kind(name: String): Kind? = when (name.substringAfterLast('.', "").lowercase()) {
        in pictures -> Kind.Picture
        in videos -> Kind.Video
        "pdf" -> Kind.Pdf
        "doc", "docx", "odt", "rtf" -> Kind.Words
        "xls", "xlsx", "xlsm", "ods", "csv", "tsv" -> Kind.Tables
        "ppt", "pptx", "ppsx", "odp" -> Kind.Slides
        else -> null
    }

    /** Office's glyph for a document (the Office home's rows: Words' lines, Tables' table, Slides' play) and its colour. */
    fun glyph(kind: Kind): Pair<Int, Color>? = when (kind) {
        Kind.Words -> R.drawable.ic_file_text to WORD_BLUE
        Kind.Tables -> R.drawable.ic_table to EXCEL_GREEN
        Kind.Slides -> R.drawable.ic_play to POWERPOINT_ORANGE
        else -> null
    }

    /** The cache's name for a file as it is now: another path, size or time is another thumbnail. */
    fun key(path: String, length: Long, modified: Long): String =
        MessageDigest.getInstance("SHA-1").digest("$path|$length|$modified".toByteArray()).joinToString("") { "%02x".format(it) }

    /** The oldest thumbnails beyond [keep] go (those of files changed or gone are never asked for again). */
    fun trim(dir: File, keep: Int) {
        val all = dir.listFiles()?.filter { it.isFile } ?: return
        if (all.size > keep) all.sortedByDescending { it.lastModified() }.drop(keep).forEach { it.delete() }
    }

    private val memory by lazy { // lazy: the rules above are tested on a computer, where Android's classes are only stubs
        object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) { override fun sizeOf(key: String, value: Bitmap) = value.byteCount }
    }
    // Pictures in a few at a time; documents one at a time apart, so a long document being laid out holds no picture up.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) private val quick = Dispatchers.IO.limitedParallelism(3)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) private val slow = Dispatchers.IO.limitedParallelism(1)
    @Volatile private var trimmed = false

    fun remembered(file: File, modified: Long): Bitmap? = memory.get(key(file.path, file.length(), modified))

    /** The file's thumbnail, made now if it is not in the cache; null when it cannot be drawn (then it is not tried again until it changes). */
    suspend fun load(context: Context, file: File, kind: Kind): Bitmap? {
        val key = key(file.path, file.length(), file.lastModified())
        memory.get(key)?.let { return it }
        return withContext(if (kind == Kind.Picture || kind == Kind.Video || kind == Kind.Pdf) quick else slow) {
            val dir = File(context.cacheDir, "file-thumbs").apply { mkdirs() }
            if (!trimmed) { trimmed = true; trim(dir, 4000) }
            val cached = File(dir, "$key.webp"); val none = File(dir, "$key.none")
            if (none.exists()) return@withContext null
            val bmp = (if (cached.exists()) BitmapFactory.decodeFile(cached.path) else null) ?: runCatching { make(context, file, kind) }
                .onFailure { WriterLog.e("Files: thumbnail of ${file.name}", it) }.getOrNull()?.also { b ->
                    val part = File(dir, "$key.part") // written aside and renamed: a half-written one never passes for done
                    runCatching { part.outputStream().use { b.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }; part.renameTo(cached) }
                }
            if (bmp == null) none.createNewFile() else memory.put(key, bmp)
            bmp
        }
    }

    private fun make(context: Context, file: File, kind: Kind): Bitmap? = when (kind) {
        Kind.Picture -> ThumbnailUtils.createImageThumbnail(file, Size(SIZE, SIZE), null)
        Kind.Video -> ThumbnailUtils.createVideoThumbnail(file, Size(SIZE, SIZE), null)
        Kind.Pdf -> PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            pdf.openPage(0).use { p ->
                val k = SIZE / maxOf(p.width, p.height, 1).toFloat()
                Bitmap.createBitmap(Math.round(p.width * k).coerceAtLeast(1), Math.round(p.height * k).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(android.graphics.Color.WHITE); p.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            }
        }
        Kind.Words -> OfficeKind.of(file.name)?.let { officeKind ->
            val bytes = file.readBytes(); val parts = OfficeFormats.unzip(bytes)
            // shortcut: the whole document is laid out for its first page; cut it to its first paragraphs if long ones are slow.
            OfficePages.layout(OfficeFormats.read(bytes, officeKind), DocStyles.of(officeKind, bytes)) { img -> img.bytes ?: img.part?.let { parts[it] } }
                .firstOrNull()?.let { laid -> page(laid.page.width, laid.page.height) { c, k -> c.scale(k, k); laid.draw(c) } }
        }
        Kind.Tables -> Sheets.read(file).let { book ->
            val sheet = book.sheet(book.active); val layout = SheetLayout.of(sheet); val pages = sheetPages(sheet, layout)
            pages.pages.firstOrNull()?.let { first -> page(pages.width, pages.height) { c, k ->
                c.scale(k, k); SheetPainter(sheet, book.styles, context.resources.displayMetrics.density, layout).printPage(c, pages, first, sheet.print.grid)
            } }
        }
        Kind.Slides -> if (!Slides.isSlides(file.name)) null else Slides.read(file).let { deck ->
            (deck.slides.firstOrNull { !it.hidden } ?: deck.slides.firstOrNull())?.let { slide -> page(deck.width, deck.height) { c, k -> Slides.draw(c, deck, slide, k) } }
        }
    }

    /** A white page [w] × [h] (any unit) at thumbnail size; [draw] gets the canvas and the scale from that unit. */
    private fun page(w: Float, h: Float, draw: (Canvas, Float) -> Unit): Bitmap {
        val k = SIZE / maxOf(w, h, 1f)
        val bmp = Bitmap.createBitmap(Math.round(w * k).coerceAtLeast(1), Math.round(h * k).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { drawColor(android.graphics.Color.WHITE); draw(this, k) }
        return bmp
    }
}

/**
 * A file's thumbnail in Files' list and grid, with Office's glyph over a document; [fallback] (its icon) for a file with
 * none, and while a picture's is being made. A document that cannot be drawn shows its glyph on a blank page.
 */
@Composable
internal fun FileThumb(file: File, glyphSize: Dp, modifier: Modifier, crop: Boolean, fallback: @Composable () -> Unit) {
    val kind = FileThumbs.kind(file.name) ?: return fallback()
    val context = LocalContext.current
    val modified = file.lastModified()
    val bitmap = produceState(FileThumbs.remembered(file, modified), file, modified) { value = FileThumbs.load(context, file, kind) }.value
    val glyph = FileThumbs.glyph(kind)
    if (bitmap == null && glyph == null) return fallback()
    Box(modifier.background(if (glyph != null) Color.White else Color.Transparent), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = if (crop && glyph == null) ContentScale.Crop else ContentScale.Fit, modifier = Modifier.matchParentSize()) }
        glyph?.let { (icon, tint) ->
            Box(Modifier.background(Color.White.copy(alpha = 0.9f), CircleShape).padding(glyphSize / 5)) {
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(glyphSize))
            }
        }
    }
}
