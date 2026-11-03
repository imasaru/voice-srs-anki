package com.evan.voicesrs

import android.app.Application
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** h122b: the fixed two-row timer row (timerLine1 on top, timerLine2 below) at several content
 *  widths and in two real hosts. Hero elapsed text is 28sp at >= 200dp content width, else 24sp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimerOverhaulTest {
    private val ctx = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_VoiceSrs)
    private val density get() = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    /** Inflate the row and measure it so timerLines content width is [rowDp]. */
    private fun row(rowDp: Int): ViewGroup {
        val host = FrameLayout(ctx); host.setBackgroundColor(Color.WHITE)
        val row = LayoutInflater.from(ctx).inflate(R.layout.include_timer_row, host, false) as ViewGroup
        host.addView(row)
        row.findViewById<TextView>(R.id.elapsedChip).apply { text = ElapsedChip.short(5_183_000L); visibility = View.VISIBLE }
        row.findViewById<TextView>(R.id.timerValue).text = "180 min"
        row.findViewById<MaterialButton>(R.id.timerGoalChip).isChecked = true
        host.measure(View.MeasureSpec.makeMeasureSpec(dp(rowDp + 100), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        host.layout(0, 0, dp(rowDp + 100), host.measuredHeight)
        val lines = row.findViewById<View>(R.id.timerLines)
        val w = dp(rowDp) + host.width - (lines.width - lines.paddingLeft - lines.paddingRight)
        host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        host.layout(0, 0, w, host.measuredHeight)
        return row
    }

    private fun verifyTwoRow(root: View, tag: String) {
        val row = root.findViewById<ViewGroup>(R.id.timerRowInclude) ?: root.findViewById<ViewGroup>(R.id.timerRow)
        assertTrue("$tag row present", row != null)
        val l1 = row!!.findViewById<ViewGroup>(R.id.timerLine1)
        val l2 = row.findViewById<ViewGroup>(R.id.timerLine2)
        assertTrue("$tag l1 above l2", l1.bottom <= l2.top)
        assertFalse("$tag oneLine", row.findViewById<TimerWrapRow>(R.id.timerLines).oneLine)
        ViewClipCheck.assertNoChildClipped(row)
    }

    @Test fun `two-row block at every content width`() {
        val widths = listOf(188, 214, 340, 600)
        val bars = HashMap<Int, Int>()
        for (w in widths) {
            val r = row(w)
            val l1 = r.findViewById<ViewGroup>(R.id.timerLine1)
            val l2 = r.findViewById<ViewGroup>(R.id.timerLine2)
            val wrap = r.findViewById<TimerWrapRow>(R.id.timerLines)
            // a) line 1 above line 2, oneLine never set, nothing clipped.
            assertTrue("w$w l1 above l2", l1.bottom <= l2.top)
            assertFalse("w$w oneLine", wrap.oneLine)
            ViewClipCheck.assertNoChildClipped(r.rootView)
            // b) elapsed text exact, no ellipsis, maxWidth not capped.
            val chip = r.findViewById<TextView>(R.id.elapsedChip)
            assertEquals("w$w chip", "1:26:23", chip.text.toString())
            assertEquals("w$w ellipsis", 0, chip.layout.getEllipsisCount(0))
            assertEquals("w$w cap", Int.MAX_VALUE, chip.maxWidth)
            // c) hero text size: 24sp below 200dp, 28sp at/above.
            assertEquals("w$w heroSp", if (w == 188) 24f else 28f, wrap.heroSp)
            // d) steppers sit on the line edges and are 48dp targets.
            val minus = r.findViewById<View>(R.id.timerMinus)
            val plus = r.findViewById<View>(R.id.timerPlus)
            assertEquals("w$w minus left", l2.paddingLeft, minus.left)
            assertEquals("w$w plus right", l2.width - l2.paddingRight, plus.right)
            assertTrue("w$w minus 48", minus.width >= dp(48) && minus.height >= dp(48))
            assertTrue("w$w plus 48", plus.width >= dp(48) && plus.height >= dp(48))
            // e) the goal bar is the only stretching element: between the steppers.
            val bar = r.findViewById<View>(R.id.timerGoalBar)
            val barBox = bar.parent as View // bar + timerValue share a FrameLayout in timerLine2
            assertTrue("w$w bar in line 2", barBox.parent === l2)
            assertTrue("w$w bar after minus", barBox.left >= minus.right)
            assertTrue("w$w bar before plus", barBox.right <= plus.left)
            bars[w] = bar.width
            // f) no stale ids from the old single-row layout (removed from resources).
            for (name in listOf("timerIcon", "timerSwitch", "timerSwitchLabel", "drillSilentSwitch"))
                assertEquals("w$w removed $name", 0, ctx.resources.getIdentifier(name, "id", ctx.packageName))
        }
        // e) the bar stretches with content: wider at 340 than at 188.
        assertTrue("bar 340 > 188", bars[340]!! > bars[188]!!)
    }

    @Test @Config(qualifiers = "w600dp-h891dp") fun `real w600 drill host`() {
        val w = dp(ctx.resources.configuration.screenWidthDp); val h = dp(ctx.resources.configuration.screenHeightDp)
        val root = LayoutInflater.from(ctx).inflate(R.layout.activity_main, null)
        setDrill(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        verifyTwoRow(root, "w600 drill")
    }

    @Test @Config(qualifiers = "w340dp-h891dp") fun `real 340 folded host`() {
        val w = dp(ctx.resources.configuration.screenWidthDp); val h = dp(ctx.resources.configuration.screenHeightDp)
        val host = FrameLayout(ctx); host.setPadding(dp(20), 0, dp(20), 0)
        val card = LayoutInflater.from(ctx).inflate(R.layout.include_status_card, host, false) as ViewGroup
        host.addView(card); setDrill(card)
        host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, w, h)
        verifyTwoRow(card, "w340 folded")
    }

    private fun setDrill(root: View) {
        val chip = root .findViewById<TextView>(R.id.elapsedChip); if (chip != null) { chip.text = ElapsedChip.short(5_183_000L); chip.visibility = View.VISIBLE }
        val value = root .findViewById<TextView>(R.id.timerValue); if (value != null) value.text = "180 min"
        val goal = root .findViewById<MaterialButton>(R.id.timerGoalChip); if (goal != null) goal.isChecked = true
    }
}
