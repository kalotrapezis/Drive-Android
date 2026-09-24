package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderRulesTest {
    @Test fun foldersAreNamedWhereAndroidKeepsPhotos() {
        assertEquals("Camera", FolderRules.albumName("DCIM/Camera/"))
        assertEquals("Camera", FolderRules.albumName("DCIM/Camera/Camera/"))
        assertEquals("Viber", FolderRules.albumName("Pictures/Viber/"))
        assertEquals("Viber", FolderRules.albumName("pictures/Viber/"))
        assertEquals("Viber", FolderRules.albumName("Movies/Viber/"))
        assertEquals("Gallery", FolderRules.albumName("Pictures/Gallery/owner/Archive/"))
        assertEquals("Pictures", FolderRules.albumName("Pictures/"))
        assertEquals("Download", FolderRules.albumName("Download/MegaDownloads/Παιχνίδια/presets/"))
    }

    @Test fun filesAreNeverOffered() {
        for (path in listOf("SyncThing/Εκπαίδευση/Scs/", "usb1/Mikroi/fill/", "Drive/Photos/", "Documents/ViberDownloads/", "", null)) {
            assertEquals(path, null, FolderRules.albumName(path))
        }
    }

    @Test fun onlyDefaultsAndYesAreShown() {
        assertTrue(FolderRules.isIncluded("DCIM/Camera/", emptyMap()))
        assertTrue(FolderRules.isIncluded("DCIM/camera/", emptyMap()))
        assertTrue(FolderRules.isIncluded("Pictures/Screenshots/", emptyMap()))
        assertTrue(FolderRules.isIncluded("Pictures/Tetra/", emptyMap()))
        assertFalse("never asked", FolderRules.isIncluded("DCIM/Creation/", emptyMap()))
        assertFalse(FolderRules.isIncluded("Pictures/Viber/", mapOf("viber" to false)))
        assertTrue(FolderRules.isIncluded("Movies/Viber/", mapOf("viber" to true)))
        assertFalse(FolderRules.isIncluded("SyncThing/x/", mapOf("syncthing" to true)))
    }
}
