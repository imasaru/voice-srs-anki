package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/** h152a (item 62 part 1): SilentAsk gains a spoken "Rate Again" option in the item 36 prompt. */
class H152aTest {
    private fun readSrc(rel: String): String =
        listOf(rel, "app/$rel").firstOrNull { java.io.File(it).exists() }?.let { java.io.File(it).readText() }
            ?: throw IllegalStateException("$rel not found")

    @Test fun silentAskRateAgainOption() {
        val src = readSrc("src/main/java/com/evan/voicesrs/SilentAsk.kt")
        assertTrue("QUESTION updated", src.contains("QUESTION = \"No rating heard. Go silent, or say rate again?\""))
        assertTrue("MARKED_AGAIN present", src.contains("MARKED_AGAIN = \"No rating, marked Again.\""))
        assertTrue("Reply enum has RATE_AGAIN", src.contains("enum class Reply { YES, NO, NONE, RATE_AGAIN }"))
        assertTrue("Action enum present", src.contains("enum class Action"))
        assertTrue("action( present", src.contains("fun action("))
        assertFalse("old QUESTION gone", src.contains("Do you want to go into silent mode?"))
    }
}
