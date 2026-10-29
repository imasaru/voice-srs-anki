package com.evan.voicesrs

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** h82 / R17: a long past answer at 320dp keeps a visible, unclipped "Good" pill in the header. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h640dp")
class PastBubblePillTest {
    private val ctx = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_VoiceSrs)
    private val longAnswer = "execute another notebook in the current notebooks context and keep its variables in scope"

    @After
    fun tearDown() = ReviewLog.clear()
    private fun bubble(): ViewGroup {
        val turn = ReviewLog.appendAfterGrade(
            ease = 3, cardId = 7L, noteId = null, frontText = "Q7", backText = "%run",
            contentHash = "h82", transcript = longAnswer, cachedText = null, aiEnabled = false
        )!!
        val host = FrameLayout(ctx)
        val row = LayoutInflater.from(ctx).inflate(R.layout.item_chat_turn, host, false)
        host.addView(row)
        CardFaceBinder.bindPills(
            row.findViewById(R.id.userAnswerText), row.findViewById(R.id.userRatingPill),
            turn.transcript, turn.ease, null, row.findViewById(R.id.userAnswerLabel)
        )
        val w = (ctx.resources.configuration.screenWidthDp * ctx.resources.displayMetrics.density).toInt()
        host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        host.layout(0, 0, w, host.measuredHeight)
        return row.findViewById(R.id.userBubble)
    }

    /** Left edge of [v] in [ancestor]'s coordinates. */
    private fun leftIn(v: View, ancestor: View): Int {
        var x = 0; var cur: View = v
        while (cur !== ancestor) { x += cur.left; cur = cur.parent as View }
        return x
    }

    @Test
    fun `a pill is visible and says Good`() {
        val pill = bubble().findViewById<TextView>(R.id.userRatingPill)
        assertEquals(View.VISIBLE, pill.visibility)
        assertEquals("Good", pill.text.toString())
    }

    @Test
    fun `b pill has its full width on one line`() {
        val pill = bubble().findViewById<TextView>(R.id.userRatingPill)
        val laidOut = pill.width
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        pill.measure(spec, spec)
        assertTrue("pill $laidOut < natural ${pill.measuredWidth}", laidOut >= pill.measuredWidth)
        assertEquals(1, pill.lineCount)
    }

    @Test
    fun `c pill sits inside the bubble padding`() {
        val b = bubble()
        val pill = b.findById<TextView>(R.id.userRatingPill)
        val left = leftIn(pill, b)
        assertTrue("pill left $left < paddingStart ${b.paddingLeft}", left >= b.paddingLeft)
        assertTrue("pill right ${left + pill.width} > ${b.width - b.paddingRight}", left + pill.width <= b.width - b.paddingRight)
    }

    @Test
    fun `d header has no RATING`() {
        val label = bubble().findViewById<TextView>(R.id.userAnswerLabel)
        assertFalse(label.text.toString().uppercase().contains("RATING"))
        assertEquals("YOUR ANSWER", label.text.toString())
    }

}
