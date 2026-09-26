package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionRulesTest {
    @Test fun aPictureAndItsSecondsOfVideoAreOneAndAVideoOfItsOwnStays() {
        val items = listOf(
            Triple("DCIM/Card/", "MVIMG_1.jpg", false), Triple("DCIM/Card/", "MVIMG_1.MP4", true),
            Triple("DCIM/Card/", "20230529_201908.heic", false), Triple("DCIM/Card/", "20230529_201908(2).MP4", true),
            Triple("DCIM/Pixel/", "PXL_2.MP.jpg", false), Triple("DCIM/Pixel/", "PXL_2.mp4", true),
            Triple("DCIM/Card/", "holiday.mp4", true), Triple("DCIM/Other/", "MVIMG_1.MP4", true),
        )
        assertEquals(mapOf(1 to 0, 3 to 2, 5 to 4), MotionRules.pairs(items))
    }

    @Test fun theVideoInsideAMotionPhotoIsFoundAfterThePicture() {
        fun box(brand: String) = ByteArray(24).also { b ->
            b[3] = 24; "ftyp".toByteArray().copyInto(b, 4); brand.toByteArray().copyInto(b, 8)
        }
        val jpeg = byteArrayOf(-1, -40) + ByteArray(100) { 7 } + "ftyp in the text".toByteArray() + byteArrayOf(-1, -39)
        assertEquals(jpeg.size, MotionRules.embeddedVideoOffset(jpeg + box("mp42") + ByteArray(50)))
        assertEquals(-1, MotionRules.embeddedVideoOffset(jpeg))
        val heic = box("heic") + ByteArray(40)
        assertEquals(-1, MotionRules.embeddedVideoOffset(heic))
        assertEquals(heic.size, MotionRules.embeddedVideoOffset(heic + box("isom")))
    }
}
