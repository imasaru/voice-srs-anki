package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/** h151 (item 63 PDCA nit): study-block Start falls back when the block vanished. */
class H151Test {
    private fun readSrc(rel: String): String =
        listOf(rel, "app/$rel").firstOrNull { java.io.File(it).exists() }?.let { java.io.File(it).readText() }
            ?: throw IllegalStateException("$rel not found")

    private fun startDrillServiceBody(src: String): String {
        val a = src.indexOf("private fun startDrillService")
        assertTrue("startDrillService not found", a >= 0)
        val b = src.indexOf("\n    private fun ", a + 1)
        assertTrue("next method not found", b >= 0)
        return src.substring(a, b)
    }

    @Test fun studyBlockFallbackDoesNotReturnSilently() {
        val src = readSrc("src/main/java/com/evan/voicesrs/MainActivity.kt")
        val body = startDrillServiceBody(src)
        assertFalse("no silent ?: return", body.contains("loadStudyBlock(this) ?: return"))
        assertTrue("has fallback run block", body.contains("loadStudyBlock(this) ?: run {"))
        val n = Regex("launchDrillService\\(newBlock = false\\)").findAll(body).count()
        assertTrue("launchDrillService(newBlock = false) at least twice, got " + n, n >= 2)
    }
}
