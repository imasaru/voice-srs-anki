package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException

class OwnWordsRetryTest {
    private fun reply(content: String): String {
        val choices = JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))
        return JSONObject().put("choices", choices).toString()
    }
    private val good = reply("{\"rating\":\"good\",\"reason\":\"names ALTER TABLE\"}")
    private val bad = reply("{\"rating\":\"go")   // cut off, like Q32

    /** Fake client: hands out the scripted replies in order; an Exception entry is thrown. */
    private class Fake(vararg val script: Any) {
        var calls = 0
        fun post(): String = when (val s = script[calls++]) {
            is Exception -> throw s
            else -> s as String
        }
    }

    @Test
    fun `a bad reply then good reply is graded after one retry`() {
        val f = Fake(bad, good)
        val a = OwnWordsJudge.judgeWithRetry({ false }) { f.post() }
        assertEquals(2, f.calls)
        assertEquals(2, a.tries)
        assertEquals(3, (a.verdict as OwnWordsJudge.Verdict.Graded).ease)
        assertNull(a.failLine)
    }

    @Test
    fun `b two bad replies give the unreadable line, never a third call`() {
        val f = Fake(bad, bad)
        val a = OwnWordsJudge.judgeWithRetry({ false }) { f.post() }
        assertEquals(2, f.calls)
        assertEquals(OwnWordsJudge.Verdict.Fallback, a.verdict)
        assertEquals(R.string.own_words_unreadable, a.failLine)
        assertEquals("unparseable or missing judge fields", a.reason)
    }

    @Test
    fun `c timeout twice gives the couldn't reach line`() {
        val f = Fake(SocketTimeoutException("Read timed out"), SocketTimeoutException("Read timed out"))
        val a = OwnWordsJudge.judgeWithRetry({ false }) { f.post() }
        assertEquals(2, f.calls)
        assertEquals(R.string.own_words_fallback, a.failLine)
        assertTrue(a.reason!!.startsWith("SocketTimeoutException"))
        // a connection error is retried too, and can still succeed
        val g = Fake(ConnectException("refused"), good)
        assertEquals(3, (OwnWordsJudge.judgeWithRetry({ false }) { g.post() }.verdict as OwnWordsJudge.Verdict.Graded).ease)
    }

    @Test
    fun `d no retry when stopped or on HTTP errors`() {
        val f = Fake(bad, good)
        val a = OwnWordsJudge.judgeWithRetry({ true }) { f.post() }
        assertEquals(1, f.calls)
        val h = Fake(AiHttpException(401), good)
        val b = OwnWordsJudge.judgeWithRetry({ false }) { h.post() }
        assertEquals(1, h.calls)
        assertEquals(R.string.own_words_fallback, b.failLine)
        assertEquals("HTTP 401", b.reason)
    }

    @Test
    fun `e raw reply log is short and has no keys`() {
        val raw = "Authorization: Bearer sk-abc123SECRET " + "x".repeat(300)
        val s = OwnWordsJudge.rawSnippet(raw)
        assertTrue(s.length <= OwnWordsJudge.RAW_LOG_MAX)
        assertFalse(s.contains("abc123SECRET"))
        assertTrue(s.contains("<redacted>"))
        assertEquals("none", OwnWordsJudge.rawSnippet(null))
        assertEquals("a b", OwnWordsJudge.rawSnippet("a\n  b"))
        assertEquals(160, OwnWordsJudge.MAX_TOKENS)
    }
}
