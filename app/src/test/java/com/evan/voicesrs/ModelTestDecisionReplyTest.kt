package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelTestDecisionReplyTest {
    @Test
    fun `a graded verdict shows the rating word`() {
        val v = OwnWordsJudge.Verdict.Graded(3, OwnWordsJudge.Rating.GOOD, "fine")
        assertEquals("good", ModelTestTargets.decisionReply(v))
    }

    @Test
    fun `b fallback shows not understood`() {
        assertEquals("not understood", ModelTestTargets.decisionReply(OwnWordsJudge.Verdict.Fallback))
    }
}
