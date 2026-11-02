package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** h118: Whisper think-time listens are not cut at the 8 s MIC_SLICE_MS cap; Google/Vosk keep it. */
class H118Test {

    @Test fun whisperListenGetsFullThinkRemaining() {
        assertEquals(20_000L, ThinkTime.engineSliceMs(EngineKind.WHISPER, 20_000L, ThinkTime.MIC_SLICE_MS))
        assertEquals(45_000L, ThinkTime.engineSliceMs(EngineKind.WHISPER, 45_000L, ThinkTime.MIC_SLICE_MS))
    }

    @Test fun googleAndVoskFallbackKeepEightSecondCap() {
        assertEquals(8_000L, ThinkTime.engineSliceMs(EngineKind.GOOGLE, 20_000L, ThinkTime.MIC_SLICE_MS))
        assertEquals(8_000L, ThinkTime.engineSliceMs(EngineKind.VOSK, 20_000L, ThinkTime.MIC_SLICE_MS))
        assertEquals(3_000L, ThinkTime.engineSliceMs(EngineKind.GOOGLE, 3_000L, ThinkTime.MIC_SLICE_MS))
        assertEquals(3_000L, ThinkTime.engineSliceMs(EngineKind.VOSK, 3_000L, ThinkTime.MIC_SLICE_MS))
    }

    @Test fun noCapMeansTimeoutUnchangedForEveryEngine() {
        for (k in EngineKind.values()) assertEquals(15_000L, ThinkTime.engineSliceMs(k, 15_000L, 0L))
    }

    private val ds: String by lazy {
        val f = listOf(
            File("src/main/java/com/evan/voicesrs/DrillService.kt"),
            File("app/src/main/java/com/evan/voicesrs/DrillService.kt")
        ).first { it.isFile }
        f.readText()
    }

    private fun body(decl: String): String {
        val lines = ds.lines()
        val start = lines.indexOfFirst { it.trim().startsWith(decl) }
        require(start >= 0) { "not found: $decl" }
        val sb = StringBuilder(); var depth = 0; var open = false
        for (i in start until lines.size) {
            sb.append(lines[i]).append('\n')
            for (c in lines[i]) { if (c == '{') { depth++; open = true } else if (c == '}') depth-- }
            if (open && depth == 0) break
        }
        return sb.toString()
    }

    /** The cap is decided per engine at the moment that engine listens, so a Whisper->Google fallback re-caps. */
    @Test fun capIsAppliedPerEngineInsideTheEngineLoop() {
        val think = body("private fun thinkForAnswer(")
        assertFalse("thinkForAnswer must not cap with minOf(..., MIC_SLICE_MS) any more",
            think.contains("ThinkTime.MIC_SLICE_MS)"))
        assertTrue("thinkForAnswer must hand the cap to the listen", think.contains("thinkSliceCapMs = ThinkTime.MIC_SLICE_MS"))
        assertTrue("thinkForAnswer must clear the cap after the listen", think.contains("thinkSliceCapMs = 0L"))
        val rec = body("private fun listenWithRecognizer(")
        assertTrue("listenWithRecognizer must size each engine attempt with engineSliceMs(kind, ...)",
            rec.contains("ThinkTime.engineSliceMs(kind, timeoutMs, thinkSliceCapMs)"))
    }
}
