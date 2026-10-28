package com.evan.voicesrs

import android.app.Application
import android.content.Context
import android.text.Spanned
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadingSpanPaintTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val ctx: Context = app

    init {
        app.setTheme(R.style.Theme_VoiceSrs)
    }

    private fun blockViews(host: LinearLayout): List<TextView> =
        (0 until host.childCount).mapNotNull { i ->
            val row = host.getChildAt(i) as? LinearLayout ?: return@mapNotNull null
            row.getChildAt(row.childCount - 1) as? TextView
        }

    private fun highlightCount(v: TextView): Int {
        val t = v.text as? Spanned ?: return 0
        return t.getSpans(0, t.length, CardFaceBinder.ReadingHighlightSpan::class.java).size
    }

    @Test
    fun `a sentence is painted on the matching block`() {
        val host = LinearLayout(ctx)
        CardFaceBinder.fillSections(host, TextView(ctx), "Masking hides a column. It runs at query time.", "", readingSentence = "It runs at query time.")
        val views = blockViews(host)
        assertTrue(views.isNotEmpty())
        val target = views.first { highlightCount(it) == 1 }
        val t = target.text as Spanned
        val span = t.getSpans(0, t.length, CardFaceBinder.ReadingHighlightSpan::class.java).single()
        val seq = t.subSequence(t.getSpanStart(span), t.getSpanEnd(span)).toString()
        assertEquals("It runs at query time.", seq)
    }

    @Test
    fun `b a cleared sentence removes the span`() {
        val host = LinearLayout(ctx)
        CardFaceBinder.fillSections(host, TextView(ctx), "Masking hides a column. It runs at query time.", "", readingSentence = "It runs at query time.")
        assertEquals(1, blockViews(host).sumOf { highlightCount(it) })
        CardFaceBinder.paintReadingIn(host, "")
        assertEquals(0, blockViews(host).sumOf { highlightCount(it) })
        CardFaceBinder.paintReadingIn(host, "Masking hides a column.")
        assertEquals(1, blockViews(host).sumOf { highlightCount(it) })
    }
}
