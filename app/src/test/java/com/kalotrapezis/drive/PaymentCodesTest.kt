package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentCodesTest {
    @Test fun `finds a valid RF code with or without grouping spaces`() {
        assertEquals("RF18539007547034", PaymentCodes.findRf("Κωδικός πληρωμής: RF18 5390 0754 7034"))
        assertEquals("RF18539007547034", PaymentCodes.findRf("rf18539007547034"))
    }

    @Test fun `ignores a following word and rejects a bad checksum`() {
        assertEquals("RF18539007547034", PaymentCodes.findRf("RF18 5390 0754 7034 AMOUNT 25"))
        assertNull(PaymentCodes.findRf("RF19 5390 0754 7034"))
        assertNull(PaymentCodes.findRf("REF 1234"))
    }

    @Test fun `formats in groups of four`() {
        assertEquals("RF18 5390 0754 7034", PaymentCodes.format("RF18539007547034"))
    }
}
