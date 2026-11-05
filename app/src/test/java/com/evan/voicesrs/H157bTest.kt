package com.evan.voicesrs

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** h157b: ReplayPause wired into card corner read-aloud (tracker 14B part 2). */
class H157bTest {
    private fun read(path: String): String =
        File("../app/src/main/java/com/evan/voicesrs/$path").takeIf { it.isFile }?.readText()
            ?: File("src/main/java/com/evan/voicesrs/$path").readText()

    @Test
    fun playbackSpeakerPlayTakesOnEnded() {
        val src = read("PlaybackSpeaker.kt")
        assertTrue(src.contains("fun play(context: Context, turnId: Long, section: Int, text: String, onEnded: () -> Unit = {})"))
        assertTrue(src.contains("onEnded()"))
    }

    @Test
    fun drillServiceCompanionHooksReplayPause() {
        val src = read("DrillService.kt")
        assertTrue(src.contains("fun onCardReplayStarting()"))
        assertTrue(src.contains("fun onCardReplayFinished()"))
        assertTrue(src.contains("ReplayPause.starting(running)"))
        assertTrue(src.contains("ReplayPause.finished(cardReplayWasDrilling)"))
        assertTrue(src.contains("holdListenForReplay()"))
        assertTrue(src.contains("releaseListenAfterReplay(resetMiss = plan.resetMissCount)"))
        assertTrue(src.contains("replayAborted.getAndSet(false)"))
        assertTrue(src.contains("replayResetMiss.getAndSet(false)"))
    }

    @Test
    fun chatPaneWiresCardSectionOnly() {
        val src = read("ChatPane.kt")
        assertTrue(src.contains("index == SpeakSection.CARD"))
        assertTrue(src.contains("DrillService.onCardReplayStarting()"))
        assertTrue(src.contains("DrillService.onCardReplayFinished()"))
        assertFalse("AI/user paths must keep bare play", src.contains("SpeakSection.AI") && src.contains("onCardReplayStarting"))
    }
}
