package com.budgetpace.app.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSourcesTest {

    @Test
    fun testSmsBuildsPseudoPackageFromHeader() {
        assertEquals("sms:AX-KOTAKB-S", MessageSources.sms("AX-KOTAKB-S"))
    }

    @Test
    fun testSmsTrimsTheHeader() {
        assertEquals("sms:AX-KOTAKB-S", MessageSources.sms("  AX-KOTAKB-S  "))
    }

    @Test
    fun testSmsFallsBackToUnknown() {
        assertEquals("sms:unknown", MessageSources.sms(null))
        assertEquals("sms:unknown", MessageSources.sms(""))
        assertEquals("sms:unknown", MessageSources.sms("   "))
    }

    @Test
    fun testIsSms() {
        assertTrue(MessageSources.isSms("sms:AX-KOTAKB-S"))
        assertTrue(MessageSources.isSms("sms:unknown"))
        assertFalse(MessageSources.isSms(MessageSources.GOOGLE_MESSAGES))
        assertFalse(MessageSources.isSms(null))
    }

    @Test
    fun testIsSupported() {
        assertTrue(MessageSources.isSupported(MessageSources.GOOGLE_MESSAGES))
        assertTrue(MessageSources.isSupported("sms:AX-KOTAKB-S"))
        assertFalse(MessageSources.isSupported("com.whatsapp"))
        assertFalse(MessageSources.isSupported(null))
        assertFalse(MessageSources.isSupported(""))
    }
}
