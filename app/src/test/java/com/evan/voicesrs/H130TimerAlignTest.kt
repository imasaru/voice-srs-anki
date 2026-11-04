package com.evan.voicesrs

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** h130: goal/voice toggles sit on the hero baseline (labels level with "elapsed") and keep
 *  the same right inset as the + stepper circle below them (mockup timer-overhaul.png). */
@RunWith(RobolectricTestRunner::class)
class H130TimerAlignTest {
    private val ctx = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_VoiceSrs)
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun row(): ViewGroup {
        val host = FrameLayout(ctx)
        val row = LayoutInflater.from(ctx).inflate(R.layout.include_timer_row, host, false) as ViewGroup
        host.addView(row)
        host.measure(View.MeasureSpec.makeMeasureSpec(dp(360), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        host.layout(0, 0, dp(360), host.measuredHeight)
        return row
    }

    private fun bottomIn(line: View, v: View): Int = (v.parent as View).top + v.bottom

    @Test fun `toggle labels line up with the elapsed caption`() {
        val r = row()
        val line = r.findViewById<ViewGroup>(R.id.timerLine1)
        val caption = r.findViewById<TextView>(R.id.elapsedCaption)
        val voiceGroup = r.findViewById<View>(R.id.voiceChip).parent as ViewGroup
        val voiceLabel = voiceGroup.getChildAt(1)
        assertTrue("label bottom ${bottomIn(line, voiceLabel)} vs caption ${bottomIn(line, caption)}",
            Math.abs(bottomIn(line, voiceLabel) - bottomIn(line, caption)) <= 1)
        val hero = caption.parent as View
        assertTrue("toggles ${voiceGroup.height} not taller than hero ${hero.height}", voiceGroup.height <= hero.height)
    }

    @Test fun `voice toggle keeps the plus stepper right inset`() {
        val r = row()
        val line = r.findViewById<ViewGroup>(R.id.timerLine1)
        val voiceGroup = r.findViewById<View>(R.id.voiceChip).parent as View
        val inset = ctx.resources.getDimensionPixelSize(R.dimen.timer_stepper_inset)
        assertEquals(line.width - inset, voiceGroup.right)
    }
}
