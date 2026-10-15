package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test

/** Review › Read aloud: each paragraph in its language; equations said in words. */
class ReadAloudTest {
    @Test fun `a paragraph is read in Greek when it has Greek letters`() {
        assertEquals("el", languageOf("Η Αθήνα, 2026").language)
        assertEquals("en", languageOf("Athens, 2026").language)
        assertEquals("el", languageOf("ἀρχή").language)
    }

    @Test fun `equations are said in words`() {
        assertEquals("x equals a plus b over 2", OfficeMath.spoken(OfficeMath.fromLinear("x=(a+b)/2"), false))
        assertEquals("x ίσον a συν b διά 2", OfficeMath.spoken(OfficeMath.fromLinear("x=(a+b)/2"), true))
        assertEquals("a squared plus b squared equals c squared", OfficeMath.spoken(OfficeMath.fromLinear("a^2+b^2=c^2"), false))
        assertEquals("the sum from i equals 1 to n of x sub i", OfficeMath.spoken(OfficeMath.fromLinear("∑_(i=1)^n x_i"), false))
        assertEquals("η τετραγωνική ρίζα του x", OfficeMath.spoken(OfficeMath.fromLinear("sqrt(x)"), true))
    }
}
