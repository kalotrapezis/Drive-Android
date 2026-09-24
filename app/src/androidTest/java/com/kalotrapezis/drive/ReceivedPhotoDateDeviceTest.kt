package com.kalotrapezis.drive

import android.content.ContentValues
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReceivedPhotoDateDeviceTest {
    @Test fun receivedPhotoKeepsCaptureDateAfterMediaScan() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val taken = 1_281_849_600_000L // 2010
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "tetra-date-${UUID.randomUUID()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Tetra/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.MediaColumns.DATE_TAKEN, taken)
        }))
        try {
            resolver.openOutputStream(uri)!!.use { out ->
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            publishReceivedPhoto(resolver, uri, taken)
            val path = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)!!.use {
                it.moveToFirst(); it.getString(0)
            }
            val scanned = CountDownLatch(1)
            MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> scanned.countDown() }
            assertTrue(scanned.await(10, TimeUnit.SECONDS))
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)!!.use {
                it.moveToFirst()
                assertEquals(taken, if (it.isNull(0)) it.getLong(1) * 1000 else it.getLong(0))
                assertEquals(taken / 1000, it.getLong(1))
            }
            assertTrue(listPhotos(context).any { it.contentUri == uri && it.takenMillis == taken })
            assertTrue(listPhotos(context).none { it.relativePath?.startsWith("Pictures/Viber/") == true })
        } finally {
            resolver.delete(uri, null, null)
        }
    }
}
