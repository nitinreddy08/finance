package com.budgetpace.app.domain.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsBodyAssemblerTest {

    @Test
    fun testPartsAreConcatenatedInOrderWithNoSeparator() {
        val parts = listOf(
            "Sent Rs.27.00 from Kotak Bank AC X7970 to paytm.s2ebz",
            "rr@pty on 06-08-26.UPI Ref 621859049153.",
        )

        assertEquals(
            "Sent Rs.27.00 from Kotak Bank AC X7970 to paytm.s2ebzrr@pty on 06-08-26.UPI Ref 621859049153.",
            SmsBodyAssembler.join(parts),
        )
    }

    @Test
    fun testNullPartsAreSkipped() {
        assertEquals("abcd", SmsBodyAssembler.join(listOf("ab", null, "cd")))
    }

    @Test
    fun testSinglePart() {
        assertEquals("one part", SmsBodyAssembler.join(listOf("one part")))
    }

    @Test
    fun testNothingUsableIsNull() {
        assertNull(SmsBodyAssembler.join(emptyList()))
        assertNull(SmsBodyAssembler.join(listOf(null, null)))
        assertNull(SmsBodyAssembler.join(listOf("", "")))
        assertNull(SmsBodyAssembler.join(listOf("  ")))
    }
}
