package com.kalotrapezis.drive

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Words' pages unchanged (the Office engine's step 0, 17 Oct): the documents in assets/fingerprint laid out by
 * [OfficePages.layout], each page's pieces, their line ends, its floats (a text box's own lines too) and a hash of the
 * page drawn, against the stored copy for this device (assets/fingerprint/expected-<model>.txt). With no stored copy, or
 * a difference, the current one is written to the app's files folder (Android/data/…/files/fingerprint-<model>.txt) to
 * pull, look at and store. Run with `am instrument -e class com.kalotrapezis.drive.WordsFingerprintDeviceTest` — never
 * connectedAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class WordsFingerprintDeviceTest {
    @Test fun wordsPagesAreAsStored() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val target = inst.targetContext
        OfficeFonts.assets = target.assets
        val assets = inst.context.assets
        val model = Build.MODEL.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val now = buildString {
            for (name in assets.list("fingerprint")!!.filter { it.endsWith(".docx") || it.endsWith(".odt") }.sorted()) {
                val bytes = assets.open("fingerprint/$name").use { it.readBytes() }
                val kind = if (name.endsWith(".odt")) OfficeKind.Odt else OfficeKind.Docx
                val parts = OfficeFormats.unzip(bytes)
                val pages = OfficePages.layout(OfficeFormats.read(bytes, kind), DocStyles.of(kind, bytes)) { img -> img.bytes ?: img.part?.let { parts[it] } }
                appendLine("$name: ${pages.size} pages")
                pages.forEachIndexed { i, laid -> page(i, laid) }
            }
        }
        val out = java.io.File(target.getExternalFilesDir(null), "fingerprint-$model.txt")
        out.writeText(now)
        val stored = runCatching { assets.open("fingerprint/expected-$model.txt").use { it.readBytes().decodeToString() } }.getOrNull()
            ?: throw AssertionError("No stored fingerprint for $model; the current one is in $out — pull it into app/src/androidTest/assets/fingerprint/expected-$model.txt")
        if (stored != now) {
            val a = stored.lines(); val b = now.lines()
            val first = a.indices.firstOrNull { it >= b.size || a[it] != b[it] } ?: a.size
            fail("Words' pages changed at line ${first + 1} (current in $out):\nstored:  ${a.getOrNull(first)}\ncurrent: ${b.getOrNull(first)}")
        }
    }

    private fun f(v: Float) = "%.2f".format(java.util.Locale.ROOT, v)

    private fun StringBuilder.pieces(indent: String, pieces: List<LaidPage.Piece>) = pieces.forEach { p ->
        val l = p.layout
        val ends = (p.firstLine..p.lastLine).joinToString(",") { "${l.getLineEnd(it)}@${l.getLineTop(it)}" }
        appendLine("$indent piece ${f(p.x)},${f(p.y)} turn ${p.turn} ${f(p.tx)},${f(p.ty)} w ${l.width} lines $ends bottom ${l.getLineBottom(p.lastLine)}")
    }

    private fun StringBuilder.page(i: Int, laid: LaidPage) {
        appendLine(" page ${i + 1} (${laid.number}) ${f(laid.page.width)}x${f(laid.page.height)}")
        pieces("", laid.pieces)
        laid.boxes.forEach { appendLine("  box ${f(it.x)},${f(it.y)} ${f(it.w)}x${f(it.h)} ${Integer.toHexString(it.color)}") }
        laid.lines.forEach { appendLine("  line ${f(it.x1)},${f(it.y1)} ${f(it.x2)},${f(it.y2)} ${f(it.width)} ${Integer.toHexString(it.color)}") }
        laid.floats.forEach { fl ->
            appendLine("  float ${fl.picture.javaClass.simpleName} ${f(fl.x)},${f(fl.y)} ${f(fl.w)}x${f(fl.h)} behind ${fl.behind} z ${fl.z} turn ${f(fl.turn)}")
            (fl.picture as? TextBoxDrawable)?.text?.let { pieces("   ", it.pieces) }
        }
        // the page drawn at twice its size in points, as a hash of its pixels
        val bmp = Bitmap.createBitmap((laid.page.width * 2).toInt(), (laid.page.height * 2).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { drawColor(-1); scale(2f, 2f); laid.draw(this) }
        val px = java.nio.ByteBuffer.allocate(bmp.byteCount).also { bmp.copyPixelsToBuffer(it) }.array()
        bmp.recycle()
        appendLine("  drawn ${java.security.MessageDigest.getInstance("MD5").digest(px).joinToString("") { "%02x".format(it) }}")
    }
}
