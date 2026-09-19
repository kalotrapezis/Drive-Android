package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PhotoMetadataRulesTest {
    @Test fun `stable key changes when MediaStore source details change`() {
        val first = PhotoMetadataRules.stableKey("content://media/1", "DCIM/Camera/", "photo.jpg", 100)
        assertEquals(first, PhotoMetadataRules.stableKey("content://media/1", "DCIM/Camera/", "photo.jpg", 100))
        assertNotEquals(first, PhotoMetadataRules.stableKey("content://media/1", "DCIM/Camera/", "photo.jpg", 101))
    }

    @Test fun `collection names are trimmed and validated`() {
        assertEquals("Family", PhotoMetadataRules.collectionName("  Family  "))
        assertThrows(IllegalArgumentException::class.java) { PhotoMetadataRules.collectionName("   ") }
    }

    @Test fun `gallery hiding affects only selected system collections`() {
        assertEquals(false, PhotoMetadataRules.visibleInGallery(isScreenshot = true, isDocument = false, hideScreenshots = true, hideDocuments = false))
        assertEquals(false, PhotoMetadataRules.visibleInGallery(isScreenshot = false, isDocument = true, hideScreenshots = false, hideDocuments = true))
        assertEquals(true, PhotoMetadataRules.visibleInGallery(isScreenshot = false, isDocument = false, hideScreenshots = true, hideDocuments = true))
    }
}
