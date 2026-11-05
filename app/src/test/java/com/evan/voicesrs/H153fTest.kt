package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-scan check that the optional cid is threaded through AiClient into
 * the AiDiag success/failure lines (h153f). Pure JUnit 4, no Robolectric.
 */
class H153fTest {

    private fun aiClientText(): String {
        return File("src/main/java/com/evan/voicesrs/AiClient.kt").readText()
    }

    @Test
    fun a_diagCallsPassCid() {
        val src = aiClientText()
        assertTrue("success diag must carry cid",
            src.contains("AiDiag.success(probe, System.currentTimeMillis(), baseUrl, model, chars, cid)"))
        assertTrue("failure diag must carry cid",
            src.contains("AiDiag.failure(e, probe, System.currentTimeMillis(), baseUrl, model, chars, cid)"))
    }

    @Test
    fun b_completeHasCidParam() {
        val lines = File("src/main/java/com/evan/voicesrs/AiClient.kt").readLines()
        val i = lines.indexOfFirst {
            it.contains("fun complete(") &&
                !it.contains("completeChat") &&
                !it.contains("completeMessages") &&
                !it.contains("completeOnce") &&
                !it.contains("completeWithTools") &&
                !it.contains("completeJson")
        }
        assertTrue("fun complete( must exist", i >= 0)
        val end = minOf(i + 25, lines.size)
        val window = lines.subList(i, end).joinToString("\n")
        assertTrue("complete( must expose cid: String? = null",
            window.contains("cid: String? = null"))
    }
}
