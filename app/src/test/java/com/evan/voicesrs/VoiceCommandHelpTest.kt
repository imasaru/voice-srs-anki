package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandHelpTest {
    private fun row(label: String) = VoiceCommandHelp.ROWS.first { it.label == label }

    @Test fun labelsInMockupOrder() {
        assertEquals(listOf("Answer B", "Repeat", "Pause", "Stop", "Skip", "Show answer"), VoiceCommandHelp.ROWS.map { it.label })
    }

    @Test fun everyListedPhraseDoesWhatItSays() {
        row("Repeat").phrases.forEach { assertEquals(it, VoiceAction.Repeat, GradeMapper.parse(it)) }
        row("Pause").phrases.forEach { assertEquals(it, VoiceAction.Pause, GradeMapper.parse(it)) }
        row("Stop").phrases.forEach { assertEquals(it, VoiceAction.Pause, GradeMapper.parse(it)) }
        row("Skip").phrases.forEach { assertEquals(it, VoiceAction.Skip, OwnWordsMode.answerAction(it, lettersOn = false)) }
        row("Show answer").phrases.forEach { assertTrue(it, ThinkTime.isShowAnswer(it)) }
        row("Answer B").phrases.forEach {
            assertEquals(it, "B", OwnWordsMode.prefixedLetter(it, setOf("A", "B", "C", "D"), partial = false))
        }
    }
}
