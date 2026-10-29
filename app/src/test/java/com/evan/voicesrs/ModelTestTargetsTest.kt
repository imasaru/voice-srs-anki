package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelTestTargetsTest {

    @Test
    fun bothModels() {
        val t = ModelTestTargets.build("http://h:8000/v1", "chat-m", "k", true, "http://d:9000/v1", "dec-m")
        assertEquals(2, t.size)
        assertEquals(ModelTestTargets.Kind.CHAT, t[0].kind)
        assertEquals("http://h:8000/v1", t[0].baseUrl)
        assertEquals("chat-m", t[0].model)
        assertEquals(ModelTestTargets.Kind.DECISION, t[1].kind)
        assertEquals("http://d:9000/v1", t[1].baseUrl)
        assertEquals("dec-m", t[1].model)
        assertEquals("k", t[1].apiKey)
    }

    @Test
    fun decisionOff() {
        val t = ModelTestTargets.build("http://h:8000/v1", "chat-m", "k", false, "http://d:9000/v1", "dec-m")
        assertEquals(1, t.size)
        assertEquals(ModelTestTargets.Kind.CHAT, t[0].kind)
    }

    @Test
    fun sameModelAndHostForBoth() {
        val t = ModelTestTargets.build("http://h:8000/v1/", "m", "k", true, "", "m")
        assertEquals(2, t.size)
        assertEquals("http://h:8000/v1", t[1].baseUrl)
        assertEquals("m", t[0].model)
        assertEquals("m", t[1].model)
    }

    @Test
    fun lineLabels() {
        assertEquals("Chat: ✓ ok", ModelTestTargets.line(ModelTestTargets.Kind.CHAT, "✓ ok"))
        assertEquals("Decision: ✗ HTTP 404", ModelTestTargets.line(ModelTestTargets.Kind.DECISION, "✗ HTTP 404"))
    }
}
