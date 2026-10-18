package com.kalotrapezis.drive

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.io.File

/** Insert › Video in Words and Slides (asked 17 Oct): a film from the phone or a web address, its picture, and playing it. */
internal object OfficeVideo {
    /** YouTube's id of a video's address (watch, youtu.be, embed, shorts), else null. */
    fun youtube(url: String) = Regex("""(?:youtu\.be/|v=|/embed/|/shorts/)([A-Za-z0-9_-]{11})""").find(url)?.groupValues?.get(1)

    /** An online video's picture: YouTube's own, else a dark one naming the site (blocking: call it off the main thread). */
    fun poster(url: String): Bitmap = youtube(url)?.let { id -> runCatching {
        (java.net.URL("https://img.youtube.com/vi/$id/hqdefault.jpg").openConnection() as java.net.HttpURLConnection).run { connectTimeout = 5000; readTimeout = 5000
            inputStream.use { android.graphics.BitmapFactory.decodeStream(it) } } }.getOrNull() }
        ?: Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888).also { b ->
            val cv = android.graphics.Canvas(b); cv.drawColor(0xFF202124.toInt())
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 44f; textAlign = android.graphics.Paint.Align.CENTER }
            cv.drawText("Online video", 640f, 560f, p); p.textSize = 30f; p.color = 0xB3FFFFFF.toInt()
            cv.drawText(runCatching { java.net.URI(url).host }.getOrNull() ?: url.take(40), 640f, 610f, p)
        }

    /** A film chosen on the phone copied into [dir] (blocking): its path, a frame a second in as its picture, its height for its width. */
    fun fromPhone(context: android.content.Context, uri: Uri, dir: File): Triple<String, Bitmap, Float>? = runCatching {
        val ext = when (context.contentResolver.getType(uri)) { "video/quicktime" -> "mov"; "video/3gpp" -> "3gp"; "video/webm" -> "webm"; else -> "mp4" }
        val f = File(dir.apply { mkdirs() }, "${System.nanoTime()}.$ext")
        context.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        val r = android.media.MediaMetadataRetriever().apply { setDataSource(f.path) }
        val frame = (r.getFrameAtTime(1_000_000) ?: r.getFrameAtTime(0))!!.also { r.release() }
        Triple(f.path, frame, frame.height / frame.width.coerceAtLeast(1).toFloat())
    }.onFailure { WriterLog.e("video", it) }.getOrNull()

    /** Insert › Audio (Slides): the kinds of sound a deck keeps, and a film's or a sound's media type by its file's ending. */
    val SOUNDS = setOf("mp3", "m4a", "wav", "ogg", "opus", "aac", "flac")
    fun audio(path: String) = path.substringAfterLast('.', "").lowercase() in SOUNDS
    fun mime(ext: String, odp: Boolean = false) = when (ext.lowercase()) { "mov" -> "video/quicktime"; "3gp" -> "video/3gpp"; "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"
        "wav" -> if (odp) "audio/x-wav" else "audio/wav" // LibreOffice's name (with audio/wav its .pptx leaves the sound out)
        "ogg", "opus" -> "audio/ogg"; "aac" -> "audio/aac"; "flac" -> "audio/flac"; else -> "video/${ext.lowercase()}" }

    /** A sound chosen on the phone copied into [dir] (blocking): its path, else null. */
    fun soundFromPhone(context: android.content.Context, uri: Uri, dir: File): String? = runCatching {
        val type = context.contentResolver.getType(uri).orEmpty()
        val ext = SOUNDS.firstOrNull { mime(it) == type } ?: when (type) { "audio/x-wav" -> "wav"; "audio/mp3" -> "mp3"; "audio/x-m4a" -> "m4a"; else -> "mp3" }
        File(dir.apply { mkdirs() }, "${System.nanoTime()}.$ext").also { f -> context.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } } }.path
    }.onFailure { WriterLog.e("audio", it) }.getOrNull()

    /** A sound's picture on the slide, as PowerPoint's: a speaker on a light square (PNG bytes). */
    fun speaker(): ByteArray {
        val b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888); val c = android.graphics.Canvas(b)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8EAED.toInt() }
        c.drawRoundRect(0f, 0f, 192f, 192f, 28f, 28f, p); p.color = 0xFF3C4043.toInt()
        c.drawPath(android.graphics.Path().apply { moveTo(40f, 76f); lineTo(72f, 76f); lineTo(108f, 44f); lineTo(108f, 148f); lineTo(72f, 116f); lineTo(40f, 116f); close() }, p)
        p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 10f; p.strokeCap = android.graphics.Paint.Cap.ROUND
        c.drawArc(96f, 66f, 146f, 126f, -50f, 100f, false, p); c.drawArc(92f, 44f, 168f, 148f, -50f, 100f, false, p)
        return java.io.ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** A video's film to play (blocking): a web address as it is, a copy on the phone, else its part taken out of [file] (a zip) into [cache]. */
    fun film(m: String, file: File?, cache: File): String? = when {
        m.startsWith("http") || File(m).isFile -> m
        else -> file?.let { f -> runCatching { java.util.zip.ZipFile(f).use { z -> z.getEntry(m.removePrefix("./"))?.let { en ->
            File(cache, "film-${m.substringAfterLast('/')}").also { out -> z.getInputStream(en).use { i -> out.outputStream().use { i.copyTo(it) } } }.path } } }.getOrNull() }
    }

    /** Where a video's picture is (points, on its page or slide), [turn] degrees about its middle, and its [film]. */
    class Spot(val film: String, val x: Float, val y: Float, val w: Float, val h: Float, val turn: Float = 0f, /** ms left out at its start and end */ val trim: List<Int>? = null)

    /** A film's length in ms (blocking), else null. */
    fun length(film: String): Int? = runCatching { android.media.MediaMetadataRetriever().run { try { setDataSource(film); extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() } finally { release() } } }.getOrNull()

    /** A play mark in the middle of a video's picture (round, so a turn does not matter). */
    fun mark(c: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float) {
        val r = minOf(w, h) * 0.14f; val cx = x + w / 2; val cy = y + h / 2
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL; color = 0x99000000.toInt() }
        c.drawCircle(cx, cy, r, p)
        p.color = 0xF2FFFFFF.toInt(); c.drawPath(android.graphics.Path().apply { moveTo(cx - r * 0.35f, cy - r * 0.5f); lineTo(cx + r * 0.55f, cy); lineTo(cx - r * 0.35f, cy + r * 0.5f); close() }, p)
    }

    /** A video's picture on the pages: drawn with its play mark; [film] what a tap on it plays. */
    class Marked(val inner: android.graphics.drawable.Drawable, val film: String) : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) { inner.bounds = bounds; inner.draw(canvas)
            mark(canvas, bounds.left.toFloat(), bounds.top.toFloat(), bounds.width().toFloat(), bounds.height().toFloat()) }
        override fun setAlpha(alpha: Int) = inner.setAlpha(alpha)
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = inner.setColorFilter(colorFilter)
        @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}

/** A video played in its own frame on the page or slide (asked 17 Oct: "like an html embedding a frame"), filling [modifier]'s box:
 *  a film in Android's player with its controls, an online one in a web frame (YouTube's own player, as Word's Online Video plays it); ✕ stops it. */
@Composable
internal fun VideoFrame(film: String, modifier: Modifier, trim: List<Int>? = null, close: () -> Unit) = Box(modifier.background(Color.Black)) {
    androidx.compose.ui.viewinterop.AndroidView({ c ->
        if (film.startsWith("http")) android.webkit.WebView(c).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = android.webkit.WebChromeClient(); webViewClient = android.webkit.WebViewClient()
            setBackgroundColor(android.graphics.Color.BLACK)
            // YouTube's player wants a page around it (an embed opened bare says "Video unavailable"): an iframe, as a web page embeds it
            OfficeVideo.youtube(film)?.let { id -> loadDataWithBaseURL("https://kalotrapezis.github.io/",
                """<html><head><style>html,body{margin:0;height:100%;background:#000}iframe{position:fixed;top:0;left:0;width:100%;height:100%;border:0}</style></head><body><iframe src="https://www.youtube.com/embed/$id?autoplay=1&playsinline=1" allow="autoplay; encrypted-media; fullscreen" allowfullscreen></iframe></body></html>""",
                "text/html", "utf-8", null) } ?: loadUrl(film)
        } else android.widget.VideoView(c).apply {
            setMediaController(android.widget.MediaController(c).also { it.setAnchorView(this) })
            setVideoPath(film)
            // a trimmed film: from its start, paused at its end (PowerPoint's Trim Video)
            setOnPreparedListener { p -> val from = trim?.getOrNull(0) ?: 0; val cut = trim?.getOrNull(1) ?: 0
                // to the very frame, not the keyframe before it (a film may have one only at its start)
                if (from > 0) p.seekTo(from.toLong(), android.media.MediaPlayer.SEEK_CLOSEST); start()
                if (cut > 0) { val end = p.duration - cut
                    val watch = object : Runnable { override fun run() { if (!isAttachedToWindow) return
                        if (currentPosition >= end) pause() else postDelayed(this, 100) } }
                    postDelayed(watch, 100) } }
        }
    }, Modifier.matchParentSize(), onRelease = { v -> (v as? android.webkit.WebView)?.destroy(); (v as? android.widget.VideoView)?.stopPlayback() })
    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = 0.6f)).clickable(onClickLabel = "Stop the video", onClick = close),
        contentAlignment = Alignment.Center) { Text("✕", color = Color.White) }
}

/** Insert › Video: From this phone ([phone] null: the file cannot keep one) or Online video, whose address is asked here; [online] gets it. */
@Composable
internal fun VideoButton(tint: Color, enabled: Boolean = true, phone: (() -> Unit)?, online: (String) -> Unit) = Box {
    var choosing by remember { mutableStateOf(false) }
    var addressing by remember { mutableStateOf(false) }
    RibbonButton("Video", tint, R.drawable.ic_play, enabled = enabled) { if (phone == null) addressing = true else choosing = true }
    DropdownMenu(choosing, { choosing = false }) {
        phone?.let { DropdownMenuItem(text = { Text("From this phone") }, onClick = { choosing = false; it() }) }
        DropdownMenuItem(text = { Text("Online video") }, onClick = { choosing = false; addressing = true })
    }
    if (addressing) {
        var typed by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(onDismissRequest = { addressing = false }, title = { Text("Online video") },
            text = { androidx.compose.material3.OutlinedTextField(typed, { typed = it.trim() }, singleLine = true, label = { Text("Its address (YouTube or another site)") },
                placeholder = { Text("https://www.youtube.com/watch?v=…") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { androidx.compose.material3.TextButton(enabled = '.' in typed, onClick = { addressing = false; online(if (typed.startsWith("http")) typed else "https://$typed") }) { Text("Insert") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { addressing = false }) { Text("Cancel") } })
    }
}
