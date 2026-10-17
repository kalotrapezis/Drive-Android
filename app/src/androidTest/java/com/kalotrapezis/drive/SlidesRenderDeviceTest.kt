package com.kalotrapezis.drive

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a check: draws every slide of the decks pushed into the app's files folder (Android/data/…/files/slides-in) as
 * PNGs 1280 px wide into files/slides-out, to set beside LibreOffice's PDFs of the same decks (the Office engine, 17 Oct).
 * Skipped when nothing is pushed. `am instrument -w -e class com.kalotrapezis.drive.SlidesRenderDeviceTest …`.
 */
@RunWith(AndroidJUnit4::class)
class SlidesRenderDeviceTest {
    @Test fun drawPushedDecks() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        OfficeFonts.assets = target.assets
        val decks = java.io.File(target.getExternalFilesDir(null), "slides-in").listFiles()?.filter { Slides.isSlides(it.name) }.orEmpty()
        assumeTrue(decks.isNotEmpty())
        val out = java.io.File(target.getExternalFilesDir(null), "slides-out").apply { deleteRecursively(); mkdirs() }
        for (f in decks.sortedBy { it.name }) {
            val deck = Slides.read(f)
            val k = 1280f / deck.width
            deck.slides.forEachIndexed { i, s ->
                val bmp = Bitmap.createBitmap(1280, (deck.height * k).toInt(), Bitmap.Config.ARGB_8888)
                Slides.draw(Canvas(bmp).apply { drawColor(-1) }, deck, s, k)
                java.io.File(out, "${f.nameWithoutExtension}-${i + 1}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
            }
        }
    }
}
