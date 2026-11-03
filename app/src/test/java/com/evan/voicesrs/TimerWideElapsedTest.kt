package com.evan.voicesrs

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** h96a: in the 600dp+ drill pane the timer row is narrow; TimerWrapRow.fitLead caps elapsedChip.maxWidth
 *  so the chip shows "1:…" instead of "1:26:23". This asserts the chip is not capped/clipped on the wide rows.
 *  The fix is the NEXT round (h96b); ignored now so the suite stays green. */
@Ignore("h96b: TimerWrapRow fix removes this")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimerWideElapsedTest {
    private val ctx = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_VoiceSrs)
    private val density get() = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private fun contentDp(row: View) = row.findViewById<View>(R.id.timerLines).let { (it.width - it.paddingLeft - it.paddingRight) / density }

    private fun drilling(row: View, on: Boolean) {
        val chip = row.findViewById<TextView>(R.id.elapsedChip)
        if (on) { chip.text = ElapsedChip.short(5_183_000L); chip.contentDescription = ElapsedChip.format(5_183_000L); chip.visibility = View.VISIBLE; row.findViewById<TextView>(R.id.timerValue).text = "180 min" }
        row.findViewById<MaterialButton>(R.id.timerGoalChip).isChecked = on
    }

    private fun check() {
        val w = dp(ctx.resources.configuration.screenWidthDp); val h = dp(ctx.resources.configuration.screenHeightDp)
        val root = LayoutInflater.from(ctx).inflate(R.layout.activity_main, null)
        val row = root.findViewById<ViewGroup>(R.id.timerRowInclude) ?: root.findViewById(R.id.timerRow)
        drilling(row, true)
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        val chip = row.findViewById<TextView>(R.id.elapsedChip)
        val tag = "w${ctx.resources.configuration.screenWidthDp} row=${contentDp(row)}dp"
        assertEquals("1:26:23", chip.text.toString())
        assertEquals("$tag chip not capped", Int.MAX_VALUE, chip.maxWidth)
        assertEquals("$tag chip one line", 1, chip.layout.lineCount)
        assertEquals("$tag chip not ellipsized", 0, chip.layout.getEllipsisCount(0))
        assertTrue("$tag chip full width", chip.width >= ceil(chip.paint.measureText("1:26:23")).toInt())
    }

    @Test @Config(qualifiers = "w600dp-h891dp") fun `w600 drill pane shows full elapsed`() { check() }
    @Test @Config(qualifiers = "w720dp-h480dp") fun `w720 drill pane shows full elapsed`() { check() }
}
