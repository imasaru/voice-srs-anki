package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiClientTimeoutTest {
    @Test
    fun `a report waits ten minutes for the first token`() {
        assertEquals(null, AiTimeoutProfile.REPORT.expired(599_000, 0, null, null))
        assertEquals(AiTimeout.Phase.WAITING, AiTimeoutProfile.REPORT.expired(600_000, 0, null, null))
    }

    @Test
    fun `b report total starts at the first token`() {
        val firstTokenAt = 500_000L
        val now1 = 1_099_000L
        assertEquals(null, AiTimeoutProfile.REPORT.expired(now1, 0, firstTokenAt, now1 - 1_000))
        val now2 = 1_100_001L
        assertEquals(AiTimeout.Phase.GENERATING, AiTimeoutProfile.REPORT.expired(now2, 0, firstTokenAt, now2 - 1_000))
    }

    @Test
    fun `c report keeps the sixty second idle limit`() {
        assertEquals(null, AiTimeoutProfile.REPORT.expired(79_999, 0, 10_000, 20_000))
        assertEquals(AiTimeout.Phase.GENERATING, AiTimeoutProfile.REPORT.expired(80_000, 0, 10_000, 20_000))
    }

    @Test
    fun `d default is unchanged`() {
        assertEquals(null, AiTimeoutProfile.DEFAULT.expired(179_999, 0, null, null))
        assertEquals(AiTimeout.Phase.WAITING, AiTimeoutProfile.DEFAULT.expired(180_000, 0, null, null))
        assertEquals(AiTimeout.Phase.GENERATING, AiTimeoutProfile.DEFAULT.expired(300_001, 0, 100_000, 299_000))
        assertEquals(AiTimeouts.FIRST_TOKEN_MS, AiTimeoutProfile.DEFAULT.firstTokenMs)
    }

    @Test
    fun `e report socket read timeout covers the first token wait`() {
        assertTrue(AiTimeoutProfile.REPORT.firstTokenMs == 600_000 && AiTimeoutProfile.REPORT.idleMs == 60_000 && AiTimeoutProfile.REPORT.totalFromFirstToken)
    }
}
