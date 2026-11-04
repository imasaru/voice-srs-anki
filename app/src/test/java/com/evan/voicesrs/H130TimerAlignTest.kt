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
        // h133: the circle itself is inset 6dp inside the 40dp button, so the column sits flush and
        // the visible circle lines up with the + circle below.
        assertEquals(line.width, voiceGroup.right)
    }

    /** h133: on the Fold3 the toggle circles (32dp visible) stood taller than the 28sp digits and rose above
     *  them; keep the 40dp target but draw a 28dp circle (6dp insets, same as the steppers' inset). */
    @Test fun `toggle circle fits inside the hero text height`() {
        val r = row()
        val hero = r.findViewById<TextView>(R.id.elapsedChip)
        for (id in listOf(R.id.timerGoalChip, R.id.voiceChip)) {
            val b = r.findViewById<com.google.android.material.button.MaterialButton>(id)
            val visible = b.height - b.insetTop - b.insetBottom
            assertEquals(dp(28), visible)
            assertTrue("circle $visible < hero ${hero.height}", visible < hero.height)
        }
    }

    @Test fun `toggle labels lift into the circle inset`() {
        val r = row()
        val lift = ctx.resources.getDimensionPixelSize(R.dimen.timer_toggle_label_lift)
        assertEquals(-dp(4), lift)
        for (id in listOf(R.id.timerGoalChip, R.id.voiceChip)) {
            val label = (r.findViewById<View>(id).parent as ViewGroup).getChildAt(1)
            assertEquals(lift, (label.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        }
    }

    /** h134: with iconGravity start + padding 11dp the Fold3 drew the icon ~6dp right of the 28dp circle
     *  centre; textStart + zero padding centres it on a text-less button. */
    @Test fun `toggle icons are centred in their circles`() {
        val r = row()
        for (id in listOf(R.id.timerGoalChip, R.id.voiceChip)) {
            val b = r.findViewById<com.google.android.material.button.MaterialButton>(id)
            assertEquals(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START, b.iconGravity)
            assertEquals(b.paddingLeft, b.paddingRight); assertEquals(0, b.iconPadding)
        }
    }
}
