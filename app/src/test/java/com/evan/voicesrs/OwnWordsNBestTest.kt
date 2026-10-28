package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnWordsNBestTest {
    @Test
    fun `a n-best finals are all in the judge input`() {
        val input = OwnWordsJudge.judgeInput(
            "call them asking policy",
            listOf("call them asking policy", "call the masking policy")
        )
        assertTrue(input.contains("call them asking policy"))
        assertTrue(input.contains("call the masking policy"))
        assertEquals("call them asking policy | call the masking policy", input)
    }

    @Test
    fun `b blanks and duplicates are dropped`() {
        assertEquals("x y | x z", OwnWordsJudge.judgeInput("x y", listOf("x y", "", null, "X Y", "x z")))
    }

    @Test
    fun `c a transcript not among the finals is used alone`() {
        assertEquals("B", OwnWordsJudge.judgeInput("B", listOf("call them asking policy")))
        assertEquals("call them", OwnWordsJudge.judgeInput("call them", emptyList()))
    }

    @Test
    fun `d the prompt tells the model they are alternatives`() {
        val body = OwnWordsJudge.requestBody("m", "q", "column masking policy", "a | b")
        assertTrue(body.contains("alternative transcriptions of one spoken answer"))
    }
}
