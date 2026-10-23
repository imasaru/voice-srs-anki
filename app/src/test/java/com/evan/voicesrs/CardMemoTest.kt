package com.evan.voicesrs

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardMemoTest {
    @Test
    fun `both memo fields are available and missing fields show a hint`() {
        val fields = JSONObject(
            """{"MemoFront":{"value":"front note","order":8},"MemoBack":{"value":"back note","order":9}}"""
        )
        val ok = CardMemo.of(fields)
        assertTrue(ok.available)
        assertFalse(ok.showHint)
        assertEquals("front note", ok.front)
        assertEquals("back note", ok.back)
        assertTrue(CardMemo.showsBack(revealed = true))
        assertFalse(CardMemo.showsBack(revealed = false))

        val missing = CardMemo.of(JSONObject("""{"Front":{"value":"q","order":0}}"""))
        assertFalse(missing.available)
        assertTrue(missing.showHint)
        assertEquals(CardMemo.MISSING_HINT, missing.hint)
        assertFalse(CardMemo.of(null).showHint)
        assertFalse(CardMemo.of(null).available)
    }
}
