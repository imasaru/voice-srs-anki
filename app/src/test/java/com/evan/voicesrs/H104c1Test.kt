package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class H104c1Test {
    @Test fun ringStepAndFraction() {
        assertEquals(8, GradeConfirm.ringStep(2000L))
        assertEquals(8, GradeConfirm.ringStep(1999L))
        assertEquals(7, GradeConfirm.ringStep(1750L))
        assertEquals(4, GradeConfirm.ringStep(1000L))
        assertEquals(1, GradeConfirm.ringStep(250L))
        assertEquals(1, GradeConfirm.ringStep(1L))
        assertEquals(0, GradeConfirm.ringStep(0L))
        assertEquals(0, GradeConfirm.ringStep(-5L))
        assertEquals(8, GradeConfirm.ringStep(5000L))
        assertEquals(0.5f, GradeConfirm.ringFraction(4), 0.0001f)
        assertEquals(1f, GradeConfirm.ringFraction(8), 0.0001f)
        assertEquals(0f, GradeConfirm.ringFraction(0), 0.0001f)
    }

    @Test fun uiCarriesStep() {
        assertTrue(File("src/main/java/com/evan/voicesrs/DrillUi.kt").readText().contains("val autoAcceptStep: Int? = null"))
        val svc = File("src/main/java/com/evan/voicesrs/DrillService.kt").readText()
        assertTrue(svc.contains("it.copy(autoAcceptStep = step)"))
        assertTrue(svc.contains("it.copy(autoAcceptStep = null)"))
        assertTrue(svc.contains("GradeConfirm.countdownStatus(mapped, left)"))
        assertTrue(File("src/main/java/com/evan/voicesrs/ChatFeed.kt").readText().contains("state.autoAcceptStep"))
    }
}
