package com.evan.voicesrs
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
class H132gTest {
    @Test
    fun bindSavedUrlFieldReturnsFlushLambda() {
        val src = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt").readText()
        assertTrue("returns () -> Unit", src.contains("save: (String) -> Unit): () -> Unit"))
        assertTrue("neural setText false", src.contains("neuralUrlView.setText(held.neuralUrl, false)"))
        assertTrue("asr setText false", src.contains("asrUrlView.setText(held.asrUrl, false)"))
    }

    @Test
    fun flushTextsCommitsAllFiveSavedUrls() {
        val src = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt").readText()
        val block = src.substring(src.indexOf("flushTexts = {"), src.indexOf("\n        }", src.indexOf("flushTexts = {")))
        for (name in listOf("flushAnkiUrl()", "flushNeuralUrl()", "flushAsrUrl()", "flushAiUrl()", "flushDecisionUrl()")) {
            assertTrue("contains $name", block.contains(name))
        }
    }
}
