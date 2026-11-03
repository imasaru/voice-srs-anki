package com.evan.voicesrs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class H115wTest {
    @Test
    fun `get_exam_readiness tool call passes examDate`() {
        val src = File("src/main/java/com/evan/voicesrs/GeneralChat.kt")
        val line = src.readLines().firstOrNull { it.contains(".invoke(toolName, args)") }
                ?: error("could not find .invoke(toolName, args) line in GeneralChat.kt")
        assertTrue(
            "get_exam_readiness tool call must pass examDate = Prefs.loadExam(app, cfg.deck).date, same as readinessBlob",
            line.contains("examDate = { Prefs.loadExam(app, cfg.deck).date }"),
        )
    }
}
