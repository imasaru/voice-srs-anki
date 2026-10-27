package com.evan.voicesrs

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillUiUpdateTest {

    private var count = 0
    private val listener: (DrillUiState) -> Unit = { count++ }

    @After
    fun tearDown() {
        DrillUi.removeListener(listener)
        DrillUi.update { DrillUiState() }
    }

    @Test
    fun unchangedUpdateDoesNotNotify() {
        DrillUi.addListener(listener)
        DrillUi.update { it }
        assertEquals("no-op update should not notify", 0, count)
    }

    @Test
    fun changedUpdateNotifiesOnce() {
        DrillUi.addListener(listener)
        DrillUi.update { it.copy(chatShowResume = !it.chatShowResume) }
        assertEquals("one meaningful change should notify once", 1, count)
    }

    @Test
    fun sameCopyAppliedTwiceNotifiesOnce() {
        DrillUi.addListener(listener)
        DrillUi.update { it.copy(chatShowResume = true) }
        DrillUi.update { it.copy(chatShowResume = true) }
        assertEquals("identical state applied twice notifies only once", 1, count)
    }

    @Test
    fun changedUpdateStoresState() {
        DrillUi.addListener(listener)
        DrillUi.update { it.copy(chatShowResume = true) }
        assertTrue("state must be stored after a changed update", DrillUi.state.chatShowResume)
    }
}
