package com.evan.voicesrs

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
/** h139c: Material check/error icons instead of ✓ / ✗ glyphs on the Settings test results. */
@RunWith(RobolectricTestRunner::class)
class H139cTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val ctx: Context = app
    private val resources: Resources = ctx.resources
    init { app.setTheme(R.style.Theme_VoiceSrs) }

    private fun exists(rel: String): Boolean =
        listOf("src/main/$rel", "app/src/main/$rel").any { java.io.File(it).exists() }
    private fun read(rel: String): String =
        listOf("src/main/$rel", "app/src/main/$rel").first { java.io.File(it).exists() }.let { java.io.File(it).readText() }

    private fun count(needle: String, s: String): Int =
        if (needle.isEmpty()) 0 else s.split(needle).size - 1

    @Test fun `a anki summary drops the check glyph`() {
        val s = ConnectionSummary.ankiOk(6, 2, "Deck", "3 due")
        assertTrue("should start with \"Connected · \"", s.startsWith("Connected · "))
        assertFalse("no ✓ glyph", s.contains("✓"))
    }

    @Test fun `b failure uses Failed prefix instead of glyph`() {
        assertEquals("Failed: Timed out", ConnectionSummary.failure(java.net.SocketTimeoutException("x")))
    }

    @Test fun `c icon maps success to check, failure to error, testing to none`() {
        assertEquals(R.drawable.ic_check_circle, ConnectionSummary.icon(true))
        assertEquals(R.drawable.ic_error, ConnectionSummary.icon(false))
        assertEquals(0, ConnectionSummary.icon(null))
    }

    @Test fun `d source carries none of the old glyphs and wires the new helper`() {
        val conn = read("java/com/evan/voicesrs/ConnectionSummary.kt")
        val settings = read("java/com/evan/voicesrs/SettingsActivity.kt")
        val both = conn + "\n" + settings
        assertFalse("✓ remains", both.contains("✓"))
        assertFalse("✗ remains", both.contains("✗"))
        assertTrue("missing showResult(testResultView, text, ok)", settings.contains("showResult(testResultView, text, ok)"))
        assertTrue("missing showResult(aiTestResult, message, allOk)", settings.contains("showResult(aiTestResult, message, allOk)"))
    }

    @Test fun `e the two drawables exist`() {
        assertTrue("missing res/drawable/ic_check_circle.xml", exists("res/drawable/ic_check_circle.xml"))
        assertTrue("missing res/drawable/ic_error.xml", exists("res/drawable/ic_error.xml"))
    }
}
