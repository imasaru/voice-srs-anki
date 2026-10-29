package com.evan.voicesrs

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnWordsListenTest {
    private val abcd = setOf("A", "B", "C", "D")
    private val keep = OwnWordsListen.Step.Keep
    private val restart = OwnWordsListen.Step.Restart

    /** Fake recognizer: replays callbacks; remaining window 10 s unless given. */
    private class FakeRecognizer(val listen: OwnWordsListen) {
        fun partial(vararg h: String) = listen.onPartial(h.toList())
        fun final(vararg h: String, leftMs: Long = 10_000L) = listen.onFinal(h.toList(), leftMs)
        fun error(code: Int, leftMs: Long = 10_000L) = listen.onError(code, leftMs)
        fun windowEnds() = listen.onDeadline()
    }

    @Test fun partialTheOrNextNeverEndsTheListen() {
        val rec = FakeRecognizer(OwnWordsListen(abcd))
        assertEquals(keep, rec.partial("the"))
        assertEquals(keep, rec.partial("next"))
        assertEquals(keep, rec.partial("answer b"))
    }

    @Test fun twoSegmentsAreStitchedForTheJudge() {
        val rec = FakeRecognizer(OwnWordsListen(abcd))
        assertEquals(restart, rec.final("the answer is basically", "the answer is basic"))
        assertEquals(restart, rec.error(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(restart, rec.final("light energy into sugar"))
        assertEquals(OwnWordsListen.Step.Finish("the answer is basically light energy into sugar"), rec.windowEnds())
        assertEquals(
            listOf("the answer is basically light energy into sugar", "the answer is basic light energy into sugar"),
            rec.listen.alternatives()
        )
    }

    @Test fun lowRemainingFinishesWithWhatWasSaid() {
        val rec = FakeRecognizer(OwnWordsListen(abcd))
        assertEquals(OwnWordsListen.Step.Finish("photosynthesis"), rec.final("photosynthesis", leftMs = 500L))
        assertEquals(OwnWordsListen.Step.Finish(null), FakeRecognizer(OwnWordsListen(abcd)).error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, leftMs = 500L))
    }

    @Test fun commandAloneOrFinalAnswerBFinishesAtOnce() {
        assertEquals(OwnWordsListen.Step.Finish("repeat"), FakeRecognizer(OwnWordsListen(abcd)).final("repeat"))
        assertEquals(OwnWordsListen.Step.Finish("skip"), FakeRecognizer(OwnWordsListen(abcd)).final("skip"))
        assertEquals(OwnWordsListen.Step.Finish("B"), FakeRecognizer(OwnWordsListen(abcd)).final("answer b"))
        assertEquals(restart, FakeRecognizer(OwnWordsListen(abcd)).final("next"))
        assertEquals(restart, FakeRecognizer(OwnWordsListen(abcd)).final("bee"))
    }

    @Test fun commandWordInsideALaterSegmentIsPartOfTheAnswer() {
        val rec = FakeRecognizer(OwnWordsListen(abcd))
        assertEquals(restart, rec.final("it is mitochondria"))
        assertEquals(restart, rec.final("repeat"))
        assertEquals(OwnWordsListen.Step.Finish("it is mitochondria repeat"), rec.windowEnds())
    }
}
