package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** h89: LogMask redaction + DiagLog/AiDiag wiring. */
class LogMaskTest {
    @Test
    fun a_masksBearer() {
        assertEquals("auth Bearer *** ok", LogMask.mask("auth Bearer sk-abc123 ok"))
    }

    @Test
    fun b_masksKeyTokenParams() {
        val m = LogMask.mask("x key=SECRET1 token=SECRET2 api_key=SECRET3 password=SECRET4")
        assertTrue(!m.contains("SECRET"))
        assertTrue(m.contains("key=***"))
        assertTrue(m.contains("token=***"))
        assertTrue(m.contains("api_key=***"))
        assertTrue(m.contains("password=***"))
    }

    @Test
    fun c_masksUrlUserInfoAndQuery() {
        val m = LogMask.mask("hit https://user:secretpw@h.test/v1?key=SECRET1&x=1")
        assertTrue(m.contains("h.test"))
        assertFalse(m.contains("secretpw"))
        assertFalse(m.contains("SECRET1"))
        assertFalse(m.contains("user:secretpw"))
    }

    @Test
    fun d_keepsHostAndPlainText() {
        assertEquals(
            "1970-01-01T09:00:00.000+09:00 LISTEN start card=3 ms=1500",
            LogMask.mask("1970-01-01T09:00:00.000+09:00 LISTEN start card=3 ms=1500"),
        )
    }

    @Test
    fun e_masksSkStyleKeys() {
        assertFalse(
            LogMask.mask("model=qwen key=sk-ABCDEFGHIJKLMNOPQRSTUV").contains("sk-ABCDEFGHIJKLMNOPQRSTUV"),
        )
    }

    @Test
    fun f_idempotent() {
        val s = "Bearer tok api_key=xyz https://u:p@h.test/?token=abc"
        assertEquals(LogMask.mask(s), LogMask.mask(LogMask.mask(s)))
    }


}
