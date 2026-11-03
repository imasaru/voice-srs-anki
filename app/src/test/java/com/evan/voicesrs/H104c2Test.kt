package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class H104c2Test {
    @Test fun sweepMatchesRingFraction() {
        assertEquals(360f, CountdownRing.sweep(8), 0.01f)
        assertEquals(180f, CountdownRing.sweep(4), 0.01f)
        assertEquals(45f, CountdownRing.sweep(1), 0.01f)
        assertEquals(0f, CountdownRing.sweep(0), 0.01f)
    }

    @Test fun ratingSuggestDrawsRing() {
        val src = File("src/main/java/com/evan/voicesrs/RatingSuggest.kt").readText()
        assertTrue(src.contains("step: Int? = null"))
        assertTrue(src.contains("CountdownRing(chip.currentTextColor, density, step!!)"))
        assertTrue(src.contains("chip.chipIcon = null"))
    }

    @Test fun chatAdapterPassesStep() {
        val src = File("src/main/java/com/evan/voicesrs/ChatAdapter.kt").readText()
        assertTrue(src.contains("RatingSuggest.apply(good, 3, row.selectedEase, row.suggestedEase, row.autoAcceptStep)"))
    }

    @Test fun mainActivityPassesRingStep() {
        val src = File("src/main/java/com/evan/voicesrs/MainActivity.kt").readText()
        assertTrue(src.contains("state.autoAcceptStep"))
        assertTrue(src.contains("RatingSuggest.apply(chipGood, 3, sel, suggested, ringStep)"))
    }
}
