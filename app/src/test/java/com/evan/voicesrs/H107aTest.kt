package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** h107a: a bare-letter partial must not end the answer listen; only prefixed forms do. */
class H107aTest {
    private val letters = setOf("A", "B", "C", "D", "E", "F")

    @Test
    fun `a bare letter homophone or the never ends the listen`() {
        for (partial in listOf("a", "be", "see", "dee", "the bee", "the", "a data")) {
            assertNull(
                "bare '$partial' must not end the listen",
                SpokenLetter.partialEnds(listOf(partial), letters)
            )
        }
    }

    @Test
    fun `a prefixed letter partial ends the listen`() {
        assertEquals("B", SpokenLetter.partialEnds(listOf("option b"), letters))
        assertEquals("C", SpokenLetter.partialEnds(listOf("answer is c"), letters))
        assertEquals("D", SpokenLetter.partialEnds(listOf("noise", "letter d"), letters))
    }

    @Test
    fun `own-words final after a bare-letter partial is unmatched`() {
        val d = SpokenLetter.decide(
            listOf("a data skew problem"),
            listOf("a data skew problem"),
            letters,
            speechDetected = true
        )
        assertEquals(SpokenLetter.Decision.Unmatched("a data skew problem"), d)
    }

    @Test
    fun `a bare final still grades as its letter`() {
        assertEquals(SpokenLetter.Decision.Match("B"), SpokenLetter.decide(listOf("b"), emptyList(), letters))
    }

    @Test
    fun `onPartialResults gates the absorb on partialEnds`() {
        val f = listOf(
            File("src/main/java/com/evan/voicesrs/DrillService.kt"),
            File("app/src/main/java/com/evan/voicesrs/DrillService.kt")
        ).firstOrNull { it.isFile }
        requireNotNull(f) { "DrillService.kt not found" }
        val src = f.readText()
        val start = src.indexOf("override fun onPartialResults(")
        require(start >= 0) { "onPartialResults not found" }
        val rest = src.substring(start)
        val end = rest.indexOf("override fun ", 1)
        val body = if (end >= 0) rest.substring(0, end) else rest
        if (!body.contains("SpokenLetter.partialEnds(")) {
            throw AssertionError("onPartialResults must gate absorbAnswer on SpokenLetter.partialEnds(")
        }
    }
}
