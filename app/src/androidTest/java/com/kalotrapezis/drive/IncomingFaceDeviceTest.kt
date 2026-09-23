package com.kalotrapezis.drive

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * What happens to a face the **computer** found (SYNC_PLAN.md 6m, 6r): real SQLite, so it can only run on a
 * device. It writes to a database of its own — never the app's — so running it cannot touch a real library.
 */
@RunWith(AndroidJUnit4::class)
class IncomingFaceDeviceTest {
    /** The app's own store, pointed at a scratch file: the same code, none of the user's data. */
    private class Scratch(base: Context, private val prefix: String) : ContextWrapper(base) {
        override fun getDatabasePath(name: String): File = super.getDatabasePath(prefix + name)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            super.openOrCreateDatabase(prefix + name, mode, factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: android.database.DatabaseErrorHandler?): SQLiteDatabase =
            super.openOrCreateDatabase(prefix + name, mode, factory, handler)
        override fun deleteDatabase(name: String): Boolean = super.deleteDatabase(prefix + name)
    }

    private lateinit var context: Context
    private lateinit var store: PhotoMetadataStore

    /** A unit vector with a chosen cosine to `base`, so a similarity can be asked for rather than hoped for. */
    private val base = FloatArray(192) { if (it % 2 == 0) 1f else 0f }.normalized()
    private val away = FloatArray(192) { if (it % 2 == 0) 0f else 1f }.normalized()
    private val elsewhere = FloatArray(192) { if (it % 2 == 0) 0f else if (it % 4 == 1) 1f else -1f }.normalized()
    /** Orthogonal to base and to both directions the probes lean in: somebody else entirely. */
    private val stranger = FloatArray(192) { if (it % 2 != 0) 0f else if (it % 4 == 0) 1f else -1f }.normalized()

    private fun FloatArray.normalized(): FloatArray {
        val length = kotlin.math.sqrt(sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(size) { this[it] / length }
    }

    /** A vector at a chosen cosine to `base`. `which` picks the direction it leans away in, so two faces can be
     *  the same distance from a person without being copies of each other. */
    private fun like(cosine: Float, which: Int = 0): ByteArray {
        val other = kotlin.math.sqrt(1f - cosine * cosine)
        val direction = if (which == 0) away else elsewhere
        return FloatArray(192) { cosine * base[it] + other * direction[it] }.bytes()
    }

    private fun ByteArray.toFloats(): FloatArray {
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.nativeOrder()).asFloatBuffer()
        return FloatArray(buffer.remaining()).also(buffer::get)
    }

    private fun FloatArray.bytes(): ByteArray = ByteBuffer.allocate(size * 4).order(ByteOrder.nativeOrder())
        .also { buffer -> forEach(buffer::putFloat) }.array()

    @Before fun setUp() {
        context = Scratch(InstrumentationRegistry.getInstrumentation().targetContext, "test_faces_")
        context.deleteDatabase("photo_metadata.db")
        store = PhotoMetadataStore(context)
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase("photo_metadata.db")
    }

    /** One face already here, in a person with a name, standing in for everything this phone found by itself. */
    private fun givenANamedPersonHere(): Long {
        store.recordClassification("photo-here", 0f, listOf(
            DetectedFace(Rect(10, 10, 120, 120), base.copyOf(), quality = 0.9f, yaw = 0f, roll = 0f),
        ), emptyList())
        val group = store.faceGroups().single()
        store.renameFaceGroup(group.id, "Άννα")
        return group.id
    }

    @Test fun aFaceFromTheComputerJoinsTheRightPersonHere() {
        val anna = givenANamedPersonHere()
        // The computer found this face; its person is one this phone has never heard of.
        store.applyIncomingFace(
            uuid = UUID.randomUUID().toString(), personUuid = null, updatedAt = 2_000,
            photoKey = "photo-from-the-computer", bounds = Rect(400, 400, 520, 520),
            embedding = like(0.95f), quality = 0.9f,
        )
        assertEquals("it belongs to the person it looks like, not to a new one", 1, store.faceGroups().size)
        assertTrue("and its photo is now one of hers", "photo-from-the-computer" in store.faceGroupKeys(anna))
    }

    @Test fun aFaceThatLooksLikeNobodyBecomesItsOwnPerson() {
        givenANamedPersonHere()
        store.applyIncomingFace(
            uuid = UUID.randomUUID().toString(), personUuid = null, updatedAt = 2_000,
            photoKey = "a-stranger", bounds = Rect(0, 0, 110, 110),
            embedding = like(0.10f), quality = 0.9f,
        )
        val groups = store.faceGroups()
        assertEquals("a stranger is a new person, not a mistake in an old one", 2, groups.size)
        assertTrue("and one nobody has named yet", groups.any { isGeneratedPersonName(it.name) })
    }

    @Test fun anUncertainFaceBecomesAQuestionInsteadOfAGuess() {
        givenANamedPersonHere()
        store.applyIncomingFace(
            uuid = UUID.randomUUID().toString(), personUuid = null, updatedAt = 2_000,
            photoKey = "maybe-her", bounds = Rect(0, 0, 130, 130),
            embedding = like(0.55f), quality = 0.9f, // inside 0.45–0.68: neither the same nor clearly not
        )
        assertEquals(2, store.faceGroups().size)
        val review = store.nextReview()
        assertNotEquals("the uncertain band is asked about, never assumed", null, review)
        assertEquals("maybe-her", review!!.photoKey)
    }

    @Test fun theComputersOwnGroupingIsFollowedWhenThisPhoneKnowsThePerson() {
        val anna = givenANamedPersonHere()
        val uuid = store.personRecords().single().uuid
        // The same person, this time named by uuid: her own grouping is kept, whatever she looks like.
        store.applyIncomingFace(
            uuid = UUID.randomUUID().toString(), personUuid = uuid, updatedAt = 2_000,
            photoKey = "definitely-her", bounds = Rect(0, 0, 110, 110),
            embedding = like(0.10f), quality = 0.9f,
        )
        assertEquals(1, store.faceGroups().size)
        assertTrue("definitely-her" in store.faceGroupKeys(anna))
    }

    @Test fun aQuestionAnsweredOnTheComputerStopsBeingAskedHere() {
        givenANamedPersonHere()
        store.applyIncomingFace(
            uuid = "face-in-question", personUuid = null, updatedAt = 2_000,
            photoKey = "maybe-her", bounds = Rect(0, 0, 130, 130),
            embedding = like(0.55f), quality = 0.9f,
        )
        val asked = store.nextReview()!!
        val person = store.personRecords().single { it.name == "Άννα" }.uuid

        // The same question, answered over there. Only the state travels; where the face went is the face's own
        // record. Without this, "no" would leave no trace at all — it moves nothing — and this phone would ask
        // about the same face for ever.
        store.applyIncomingReview("face-in-question", person, "resolved", System.currentTimeMillis())
        assertEquals("it is not asked here any more", null, store.nextReview())
        assertEquals("and the answer is offered on, once", 1, store.reviewRecords().size)

        // An older answer never overrules a newer one.
        store.applyIncomingReview("face-in-question", person, "skipped", 1)
        assertEquals("resolved", store.reviewRecords().single().state)
        assertEquals("maybe-her", asked.photoKey)
    }

    @Test fun twoPhotosOfOneMomentAreReadAsOneMoment() {
        val noon = 1_790_000_000_000L
        store.recordClassification("morning", 0f, listOf(
            DetectedFace(Rect(10, 10, 120, 120), base.copyOf(), quality = 0.9f, yaw = 0f, roll = 0f),
        ), emptyList(), takenMillis = noon)
        // Just under the line on the pixels alone; the same moment is what carries it over.
        store.recordClassification("seconds-later", 0f, listOf(
            DetectedFace(Rect(10, 10, 120, 120), like(0.72f).toFloats(), quality = 0.9f, yaw = 0f, roll = 0f),
        ), emptyList(), takenMillis = noon + 8_000)
        assertEquals("eight seconds apart, and recognised as the same person", 1, store.faceGroups().size)

        // The same likeness a week later earns nothing.
        store.recordClassification("next-week", 0f, listOf(
            DetectedFace(Rect(10, 10, 120, 120), like(0.72f, which = 1).toFloats(), quality = 0.9f, yaw = 0f, roll = 0f),
        ), emptyList(), takenMillis = noon + 7 * 86_400_000L)
        assertEquals("another day is not the same evidence", 2, store.faceGroups().size)

        // And the nudge cannot join two people who look nothing alike, however close in time.
        store.recordClassification("same-day-stranger", 0f, listOf(
            DetectedFace(Rect(10, 10, 120, 120), stranger.copyOf(), quality = 0.9f, yaw = 0f, roll = 0f),
        ), emptyList(), takenMillis = noon + 60_000)
        assertEquals("a stranger on the same day is still a stranger", 3, store.faceGroups().size)
    }

    @Test fun twoDevicesThatDisagreeAskInsteadOfTakingTurns() {
        val anna = givenANamedPersonHere()
        // A second photo of hers, so the disagreement covers more than one face.
        store.recordClassification("photo-here-2", 0f, listOf(
            DetectedFace(Rect(10, 10, 120, 120), base.copyOf(), quality = 0.9f, yaw = 0f, roll = 0f),
        ), emptyList())
        assertEquals("both faces are hers", 2, store.faceGroupKeys(anna).size)
        val hers = store.faceRecords()

        // The other device knows the same faces as someone else it has also named.
        val maria = UUID.randomUUID().toString()
        store.applyIncomingPerson(maria, "Μαρία", System.currentTimeMillis())
        // applyIncomingPerson only renames what is here, so give that person a group of her own first.
        store.applyIncomingFace(
            uuid = UUID.randomUUID().toString(), personUuid = null, updatedAt = 2_000,
            photoKey = "maria-photo", bounds = Rect(700, 700, 820, 820), embedding = like(0.05f), quality = 0.9f,
        )
        val mariaGroup = store.faceGroups().single { it.id != anna }
        store.renameFaceGroup(mariaGroup.id, "Μαρία")
        val mariaUuid = store.personRecords().single { it.name == "Μαρία" }.uuid

        val later = System.currentTimeMillis() + 60_000
        hers.forEach { store.applyIncomingFace(it.uuid, mariaUuid, later) }

        assertEquals("nobody was torn apart", 2, store.faceGroupKeys(anna).size)
        assertEquals("and nobody was quietly taken over", 1, store.faceGroupKeys(mariaGroup.id).size)
        val asked = store.nextReview()
        assertNotEquals("the difference is asked about", null, asked)
        assertEquals("one card for the pair, not one per face", mariaGroup.id, asked!!.candidateGroupId)

        // Answering it is what moves anything.
        store.resolveReview(asked, accepted = true)
        assertEquals(2, store.faceGroupKeys(mariaGroup.id).size)
        assertEquals(null, store.nextReview())
    }

    @Test fun aNumberFromAnotherDeviceNeverReplacesAName() {
        givenANamedPersonHere()
        val uuid = store.personRecords().single().uuid
        // Later than the rename itself, which is stamped with the real clock.
        val later = System.currentTimeMillis() + 60_000
        store.applyIncomingPerson(uuid, "Person 12", later)
        assertEquals("Άννα", store.faceGroups().single().name)
        store.applyIncomingPerson(uuid, "Anna", later + 1)
        assertEquals("but another name, written later, is a decision too", "Anna", store.faceGroups().single().name)
    }
}
