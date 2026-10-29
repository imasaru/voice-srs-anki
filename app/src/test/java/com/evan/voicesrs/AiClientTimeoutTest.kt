package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiClientTimeoutTest {
    @Test
    fun `a report waits ten minutes for the first token`() {
        assertEquals(null, REPORT.expired(599_000, 0, null, null))
        assertEquals(AiTimeout.Phase.WAITING, REPORT.expired(600_000, 0, null, null))
    }

    @Test
    fun `b report total starts at the first token`() {
        val firstTokenAt = 500_000
        assertEquals(null, REPORT.expired(1_099_000, 0, firstTokenAt, 1_099_000 - 1_000))
        assertEquals(AiTimeout.Phase.GENERATING, REPORT.expired(1_100_001, 0, firstTokenAt, 1_100_001 - 1_000))
    }

    @Test
    fun `c report keeps the sixty second idle limit`() {
        assertEquals(null, REPORT.expired(79_999, 0, 10_000, 20_000))
        assertEquals(AiTimeout.Phase.GENERATING, REPORT.expired(80_000, 0, 10_000, 20_000))
    }

    @Test
    fun `d default is unchanged`() {
        assertEquals(null, DEFAULT.expired(179_999, 0, null, null))
        assertEquals(AiTimeout.Phase.WAITING, DEFAULT.expired(180_000, 0, null, null))
        assertEquals(AiTimeout.Phase.GENERATING, DEFAULT.expired(300_001, 0, 100_000, 299_000))
        assertEquals(AiTimeouts.FIRST_TOKEN_MS, DEFAULT.firstTokenMs)
    }

    @Test
    fun `e report socket read timeout covers the first token wait`() {
        assertTrue(REPORT.firstTokenMs == 600_000 && REPORT.idleMs == 60_000 && REPORT.totalFromFirstToken)
    }
}
