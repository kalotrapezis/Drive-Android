package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The guide's Next walks every step of every chapter once, in order, and Back walks the same way home. */
class GuideTest {
    @Test fun nextAndBackVisitEveryStep() {
        val all = GuideChapter.entries.flatMap { ch -> ch.steps.indices.map { GuidePlace.of(ch.ordinal, it) } }
        val walked = generateSequence(0) { GuidePlace.next(it) }.toList()
        assertEquals(all, walked)
        assertEquals(all.reversed(), generateSequence(all.last()) { GuidePlace.previous(it) }.toList())
        assertNull(GuidePlace.previous(0))
        walked.forEach { assertEquals(GuidePlace.chapter(it).steps[it % 100], GuidePlace.step(it)) }
    }
}
