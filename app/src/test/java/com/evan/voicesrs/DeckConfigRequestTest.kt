package com.evan.voicesrs

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class DeckConfigRequestTest {

    @Test
    fun `getDeckConfig params JSON is deck only`() {
        val params = DeckConfigRequest.getDeckConfigParams("Driving")
        assertEquals("""{"deck":"Driving"}""", JSONObject(params).toString())
    }

    @Test
    fun `withDesiredRetention copies and sets retention without mutating input`() {
        val cfg = JSONObject()
            .put("id", 1655)
            .put("name", "Default")
            .put("new", JSONObject().put("perDay", 20))
        val copy = DeckConfigRequest.withDesiredRetention(cfg, 0.95)
        assertEquals(0.95, copy.optDouble("desiredRetention"), 0.0001)
        assertEquals(1655L, copy.getLong("id"))
        assertEquals("Default", copy.getString("name"))
        assertEquals(20, copy.getJSONObject("new").getInt("perDay"))
        // original untouched
        assertFalse(cfg.has("desiredRetention"))
        assertEquals(1655L, cfg.getLong("id"))
    }

    @Test
    fun `withDesiredRetention rejects out-of-range retention`() {
        val cfg = JSONObject().put("id", 1)
        assertThrows(IllegalArgumentException::class.java) {
            DeckConfigRequest.withDesiredRetention(cfg, 0.5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeckConfigRequest.withDesiredRetention(cfg, 1.0)
        }
    }

    @Test
    fun `desiredRetention reads value or null when missing`() {
        val withIt = JSONObject().put("id", 1).put("desiredRetention", 0.9)
        assertEquals(0.9, DeckConfigRequest.desiredRetention(withIt)!!, 0.0001)
        val without = JSONObject().put("id", 1)
        assertNull(DeckConfigRequest.desiredRetention(without))
    }

    @Test
    fun `saveDeckConfigParams wraps config under config and requires id`() {
        val cfg = JSONObject().put("id", 1655).put("desiredRetention", 0.9)
        val json = JSONObject(DeckConfigRequest.saveDeckConfigParams(cfg))
        assertTrue(json.has("config"))
        val wrapped = json.getJSONObject("config")
        assertEquals(1655L, wrapped.getLong("id"))
        assertEquals(0.9, wrapped.optDouble("desiredRetention"), 0.0001)
        assertThrows(IllegalArgumentException::class.java) {
            DeckConfigRequest.saveDeckConfigParams(JSONObject().put("name", "x"))
        }
    }

    @Test
    fun `setDueDate params build cards array of longs and validate days`() {
        val params = DeckConfigRequest.setDueDateParams(
            listOf(1_483_959_291_685L, 1_483_959_293_217L),
            "0-5"
        )
        val json = JSONObject(params)
        val cards = json.getJSONArray("cards")
        assertEquals(2, cards.length())
        assertEquals(1_483_959_291_685L, cards.getLong(0))
        assertEquals(1_483_959_293_217L, cards.getLong(1))
        assertEquals("0-5", json.getString("days"))

        listOf("0", "1!", "3-7").forEach { d ->
            DeckConfigRequest.setDueDateParams(listOf(1L), d)
        }
        listOf("", "7-3", "abc").forEach { d ->
            assertThrows(IllegalArgumentException::class.java) {
                DeckConfigRequest.setDueDateParams(listOf(1L), d)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeckConfigRequest.setDueDateParams(emptyList(), "0-5")
        }
    }

    @Test
    fun `setDeckConfigId and clone params shapes`() {
        val setParams = DeckConfigRequest.setDeckConfigIdParams(listOf("Deck A", "Deck B"), 1655)
        val setJson = JSONObject(setParams)
        val decks = setJson.getJSONArray("decks")
        assertEquals(2, decks.length())
        assertEquals("Deck A", decks.getString(0))
        assertEquals("Deck B", decks.getString(1))
        assertEquals(1655L, setJson.getLong("configId"))

        val cloneParams = DeckConfigRequest.cloneParams("Exam copy", 1)
        val cloneJson = JSONObject(cloneParams)
        assertEquals("Exam copy", cloneJson.getString("name"))
        assertEquals(1L, cloneJson.getLong("cloneFrom"))
    }

    @Test
    fun `parseMedia decodes base64 or null for missing and bad input`() {
        val b64 = Base64.getEncoder().encodeToString("hi".toByteArray())
        assertArrayEquals("hi".toByteArray(), DeckConfigRequest.parseMedia(b64))
        assertNull(DeckConfigRequest.parseMedia(false))
        assertNull(DeckConfigRequest.parseMedia(null))
        assertNull(DeckConfigRequest.parseMedia("%%%"))
    }

    @Test
    fun `parseCloneId maps number to long and false to null`() {
        assertEquals(1655L, DeckConfigRequest.parseCloneId(1655))
        assertNull(DeckConfigRequest.parseCloneId(false))
    }
}
