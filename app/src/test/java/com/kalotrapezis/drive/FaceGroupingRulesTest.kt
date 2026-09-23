package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FaceGroupingRulesTest {
    @Test fun `normalized embeddings compare by cosine similarity`() {
        val first = floatArrayOf(3f, 4f).l2Normalized()
        assertEquals(1f, cosineSimilarity(first, first), 0.0001f)
        assertEquals(0f, cosineSimilarity(first, floatArrayOf(-4f, 3f).l2Normalized()), 0.0001f)
    }

    @Test fun `blurred or tiny faces are not reliable group anchors`() {
        assertEquals(false, isReliableFace(faceQualityScore(36, 4)))
        assertEquals(true, isReliableFace(faceQualityScore(120, 20)))
    }

    @Test fun `profile and tilted faces are not reliable group anchors`() {
        assertEquals(false, isReliableFace(0.9f, yaw = 31f))
        assertEquals(false, isReliableFace(0.9f, roll = 21f))
    }

    @Test fun `tiny eye spacing is not suitable for face alignment`() {
        assertEquals(false, hasUsableEyeDistance(31f))
        assertEquals(true, hasUsableEyeDistance(32f))
    }

    @Test fun `generated person names sort after real names`() {
        assertEquals(true, isGeneratedPersonName("Person 104"))
        assertEquals(false, isGeneratedPersonName("Maria"))
    }

    @Test fun `two detectors that found the same face agree, and two faces do not`() {
        // The same face, found a few pixels apart by ML Kit and by the computer's detector.
        assertEquals(true, faceOverlap(100, 100, 200, 200, 104, 96, 196, 204) >= SAME_FACE_OVERLAP)
        // Two people standing side by side, boxes just touching.
        assertEquals(false, faceOverlap(100, 100, 200, 200, 195, 100, 295, 200) >= SAME_FACE_OVERLAP)
        assertEquals(0f, faceOverlap(0, 0, 0, 0, 0, 0, 0, 0), 0.0001f)
    }
}

class SameDayBonusTest {
    @Test fun `two moments on one calendar day share a day, across midnight they do not`() {
        val noon = 1_790_000_000_000L
        assertEquals(dayOf(noon), dayOf(noon + 3_600_000))
        assertNotEquals(dayOf(noon), dayOf(noon + 86_400_000))
    }

    @Test fun `a photo with no date is its own day, and never shares one`() {
        assertNotEquals(dayOf(0), dayOf(1_790_000_000_000L))
        assertEquals(dayOf(0), dayOf(-5))
    }
}
