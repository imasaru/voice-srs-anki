package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

/** h109 reviewer check: every spoken part of a choice card maps to the stem or a choice row. */
class H109Test {
    private val rows = listOf("A. Use repartition(8)", "B. Use coalesce(1)")

    @Test fun `every spoken part of a choice card maps to a shown block`() {
        val parts = SentenceReading.parts("Which fixes the skew? A. Use repartition(8) B. Use coalesce(1)")
        val blocks = listOf("Which fixes the skew?", "") + rows
        assertEquals(listOf("Which fixes the skew?", "A. Use repartition(8)", "B. Use coalesce(1)"), parts)
        // each choice letter starts its own part, so each row lights on its own (h123).
        assertEquals(listOf(0, 2, 3), parts.map { ReadingSpan.pick(blocks, it)!!.block })
    }
}
