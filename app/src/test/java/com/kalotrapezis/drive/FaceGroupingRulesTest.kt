package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
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
}
