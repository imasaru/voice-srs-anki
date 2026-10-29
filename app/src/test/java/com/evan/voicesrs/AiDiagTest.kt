package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

/** Repro + behavior for the one-diag-line-per-request helper. */
class AiDiagTest {

    @Test
    fun `a failure line has class message timings host model chars`() {
        val p = AiDiag.Probe(1000, true)
        p.byteAt(1300)
        val line = AiDiag.failure(
            SocketTimeoutException("Read timed out"),
            p, 61000, "https://api.example.com:8443/v1", "qwen3", 5120
        )
        assertEquals(
            "ai fail err=SocketTimeoutException: Read timed out elapsedMs=60000 " +
                "firstByteMs=300 firstTokenMs=none stream=true host=api.example.com " +
                "model=qwen3 chars=5120",
            line
        )
    }

    @Test
    fun `b success line has first byte first token and total`() {
        val p = AiDiag.Probe(1000, false)
        p.byteAt(1200)
        p.tokenAt(1500)
        val line = AiDiag.success(p, 4000, "https://api.test/v1", "m", 10)
        assertEquals(
            "ai ok totalMs=3000 firstByteMs=200 firstTokenMs=500 stream=false " +
                "host=api.test model=m chars=10",
            line
        )
    }

    @Test
    fun `c no key or credentials in the line`() {
        val p = AiDiag.Probe(1000, false)
        val line = AiDiag.failure(
            AiException("HTTP 401 Bearer sk-SECRET2 api_key=SECRET3", retryable = false),
            p, 2000, "https://user:secretpw@h.test/v1?key=SECRET1", "m", 5
        )
        assertTrue(line.contains("host=h.test"))
        assertFalse(line.contains("SECRET1"))
        assertFalse(line.contains("SECRET2"))
        assertFalse(line.contains("SECRET3"))
        assertFalse(line.contains("secretpw"))
    }

    @Test
    fun `d message is one line and clipped`() {
        val msg = ("noise line\n").repeat(200).take(500)
        val e = AiException(msg, retryable = false)
        val err = AiDiag.error(e)
        assertFalse(err.contains('\n'))
        assertTrue(err.length <= e.javaClass.simpleName.length + 2 + AiDiag.MSG_MAX)
    }

    @Test
    fun `e bad url gives question mark host`() {
        assertEquals("?", AiDiag.host("not a url"))
    }
}
