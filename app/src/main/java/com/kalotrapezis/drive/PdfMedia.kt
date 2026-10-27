package com.kalotrapezis.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.artifex.mupdf.fitz.PDFAnnotation
import com.artifex.mupdf.fitz.PDFObject
import com.artifex.mupdf.fitz.PDFPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Films and sounds kept in a PDF (asked 27 Oct, as Words' and Slides' play theirs): a Screen annotation's rendition, a
 * RichMedia annotation's asset, a Movie's file — when the file is in the PDF — and a Sound annotation's samples (made a WAV).
 */
internal object PdfMedia {
    /** One on a page: the annotation's place among the page's, where it is (points from the page's top left), its file's name. */
    class Spot(val index: Int, val x: Float, val y: Float, val w: Float, val h: Float, val name: String)

    private fun PDFObject.at(key: String): PDFObject? = get(key)?.takeUnless { it.isNull }

    /** The films and sounds on [p] whose files are in the PDF (blocking); where each is its bounds (MuPDF has no Rect for a Screen annotation). */
    fun spots(p: PDFPage): List<Spot> { val o = p.bounds
        return p.annotations.orEmpty().withIndex().mapNotNull { (n, a) -> file(a)?.let { (name, _) -> a.bounds.let { r -> Spot(n, r.x0 - o.x0, r.y0 - o.y0, r.x1 - r.x0, r.y1 - r.y0, name) } } }
    }

    /** The [n]th annotation's film or sound on [p] written into [dir] to play (blocking); null: none there. */
    fun play(p: PDFPage, n: Int, dir: File): String? = p.annotations.orEmpty().getOrNull(n)?.let(::file)?.let { (name, bytes) ->
        bytes()?.let { b -> File(dir.apply { mkdirs() }, "pdf-film-${name.substringAfterLast('/')}").apply { writeBytes(b) }.path } }

    /** [a]'s file's name and a way to read it; null: not a film or sound kept in the file. */
    fun file(a: PDFAnnotation): Pair<String, () -> ByteArray?>? = runCatching {
        val o = a.`object`
        when (o.at("Subtype")?.asName()) {
            // its action (or one after it) a rendition, its media clip's data a file
            "Screen" -> generateSequence(o.at("A")) { it.at("Next")?.takeIf { n -> n.isDictionary } }.take(8).firstOrNull { it.at("S")?.asName() == "Rendition" }
                ?.at("R")?.let { r -> if (r.at("S")?.asName() == "SR") r.at("R")?.takeIf { it.isArray && it.size() > 0 }?.get(0) else r }
                ?.at("C")?.at("D")?.let(::spec)
            "RichMedia" -> o.at("RichMediaContent")?.at("Assets")?.at("Names")?.let { names -> (0 until names.size() / 2).map { names.get(2 * it + 1) } }?.firstNotNullOfOrNull(::spec)
            "Movie" -> o.at("Movie")?.at("F")?.let(::spec)
            "Sound" -> o.at("Sound")?.takeIf { it.isStream }?.let { s -> "sound.wav" to { wav(s) } }
            else -> null
        }?.takeIf { OfficeVideo.media(it.first) }
    }.getOrNull()

    /** A file specification with its file embedded (or the stream itself): its name and bytes. */
    private fun spec(f: PDFObject): Pair<String, () -> ByteArray?>? =
        if (f.isStream) "film.mp4" to { f.readStream() }
        else (f.at("EF")?.let { it.at("UF") ?: it.at("F") })?.let { s -> (f.at("UF") ?: f.at("F"))?.asString().orEmpty().ifEmpty { "film.mp4" } to { s.readStream() } }

    /** A Sound's samples as a WAV: its rate, channels and bits; Raw unsigned, Signed, muLaw or ALaw; 16-bit samples big-endian in a PDF. */
    fun wav(s: PDFObject): ByteArray {
        val rate = s.at("R")?.asFloat()?.toInt()?.takeIf { it > 0 } ?: 8000; val ch = s.at("C")?.asInteger()?.takeIf { it > 0 } ?: 1
        val bits = s.at("B")?.asInteger()?.takeIf { it == 16 } ?: 8; val enc = s.at("E")?.asName().orEmpty()
        val data = s.readStream()
        val format = when (enc) { "muLaw" -> 7; "ALaw" -> 6; else -> 1 }
        if (format == 1) {
            if (bits == 16) for (i in 0 until data.size - 1 step 2) { val hi = data[i]; data[i] = data[i + 1]; data[i + 1] = if (enc == "Signed") hi else (hi.toInt() xor 0x80).toByte() }
            else if (enc == "Signed") for (i in data.indices) data[i] = (data[i] + 128).toByte() // a WAV's 8 bits are unsigned
        }
        val bytes = if (format == 1) bits / 8 else 1
        return java.nio.ByteBuffer.allocate(44 + data.size).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVEfmt ".toByteArray()); putInt(16); putShort(format.toShort()); putShort(ch.toShort())
            putInt(rate); putInt(rate * ch * bytes); putShort((ch * bytes).toShort()); putShort((bytes * 8).toShort()); put("data".toByteArray()); putInt(data.size); put(data)
        }.array()
    }
}

/** Page [i]'s films and sounds over it (its size [s] in points): a film with Words' play mark; a tap plays it in its place, ✕ stops it. */
@Composable
internal fun PdfMediaLayer(doc: PdfDoc, i: Int, s: Size) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val spots by produceState(emptyList<PdfMedia.Spot>(), doc, i) { value = withContext(Dispatchers.IO) { doc.media(i) } }
    if (spots.isEmpty()) return
    var playing by remember { mutableStateOf<Pair<PdfMedia.Spot, String>?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val k = maxWidth / s.width
        for (sp in spots) Box(Modifier.offset(k * sp.x, k * sp.y).size(k * sp.w, k * sp.h).clickable {
            scope.launch { withContext(Dispatchers.IO) { doc.mediaFile(i, sp.index, context.cacheDir) }?.let { playing = sp to it; WriterLog.i("PDF: a film played") } }
        }) { if (!OfficeVideo.audio(sp.name)) Canvas(Modifier.fillMaxSize()) { drawIntoCanvas { OfficeVideo.mark(it.nativeCanvas, 0f, 0f, size.width, size.height) } } }
        playing?.let { (sp, f) -> VideoFrame(f, Modifier.offset(k * sp.x, k * sp.y).size(k * sp.w, k * sp.h)) { playing = null } }
    }
}
