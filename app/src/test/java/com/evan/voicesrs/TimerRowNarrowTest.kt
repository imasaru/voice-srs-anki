package com.evan.voicesrs

import android.app.Application
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.materialswitch.MaterialSwitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** h82 / R17: narrow split-screen (320dp) two-line timer row: nothing clipped or overlapping, switch full width, 48dp targets, 36dp visible stepper circles with >= 8dp to the value. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h640dp")
class TimerRowNarrowTest {
    private val ctx = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_VoiceSrs)
    private val density get() = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    /** Inflates the row drilling at 1:26:23 with a 180-min goal (widest value) and lays it out like activity_main's slimHeader (16dp sides). */
    private fun row(): ViewGroup {
        val host = FrameLayout(ctx)
        host.setPadding(dp(16), 0, dp(16), 0)
        val row = LayoutInflater.from(ctx).inflate(R.layout.include_timer_row, host, false) as ViewGroup
        host.addView(row)
        row.findViewById<TextView>(R.id.elapsedChip).apply {
            text = ElapsedChip.format(5_183_000L)
            visibility = View.VISIBLE
        }
        row.findViewById<TextView>(R.id.timerValue).text = "180 min"
        row.findViewById<MaterialSwitch>(R.id.timerSwitch).isChecked = true
        val w = dp(ctx.resources.configuration.screenWidthDp)
        host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        host.layout(0, 0, w, host.measuredHeight)
        return row
    }

    private fun name(v: View) = if (v.id == View.NO_ID) v.javaClass.simpleName else ctx.resources.getResourceEntryName(v.id)

    /** Every visible child lies inside its parent's padding box, is not narrower than it measured, and does not overlap a visible sibling. */
    private fun assertNoClipOrOverlap(root: ViewGroup) {
        val visible = (0 until root.childCount).map { root.getChildAt(it) }.filter { it.visibility == View.VISIBLE }
        for (c in visible) {
            assertTrue("${name(c)} clipped left in ${name(root)}", c.left >= 0)
            assertTrue("${name(c)} clipped right in ${name(root)} (${c.right} > ${root.width})", c.right <= root.width)
            assertTrue("${name(c)} clipped top in ${name(root)}", c.top >= 0)
            assertTrue("${name(c)} clipped bottom in ${name(root)}", c.bottom <= root.height)
            assertTrue("${name(c)} squeezed (${c.width} < measured ${c.measuredWidth})", c.width >= c.measuredWidth)
        }
        for (i in visible.indices) for (j in i + 1 until visible.size) {
            val a = Rect(visible[i].left, visible[i].top, visible[i].right, visible[i].bottom)
            val b = Rect(visible[j].left, visible[j].top, visible[j].right, visible[j].bottom)
            assertTrue("${name(visible[i])} overlaps ${name(visible[j])}", !Rect.intersects(a, b))
        }
        visible.filterIsInstance<ViewGroup>().forEach { assertNoClipOrOverlap(it) }
    }

    @Test
    fun `a two lines line 1 icon and elapsed line 2 stepper and switch`() {
        val row = row()
        val l1 = row.findViewById<ViewGroup>(R.id.timerLine1)
        val l2 = row.findViewById<ViewGroup>(R.id.timerLine2)
        assertTrue("line 2 is not below line 1", l2.top >= l1.bottom)
        assertTrue(l1.indexOfChild(row.findViewById(R.id.timerIcon)) >= 0)
        assertTrue(l1.indexOfChild(row.findViewById(R.id.elapsedChip)) >= 0)
        listOf(R.id.timerMinus, R.id.timerValue, R.id.timerPlus, R.id.timerSpacer, R.id.timerSwitchLabel, R.id.timerSwitch)
            .forEachIndexed { i, id -> assertEquals("line 2 order", i, l2.indexOfChild(row.findViewById(id))) }
        val chip = row.findViewById<TextView>(R.id.elapsedChip)
        assertEquals("Elapsed 1:26:23", chip.text.toString())
        assertEquals(1, chip.layout.lineCount)
        assertEquals("elapsed is ellipsized", 0, chip.layout.getEllipsisCount(0))
    }

    @Test
    fun `b nothing clipped or overlapping`() {
        val row = row()
        assertNoClipOrOverlap(row.findViewById(R.id.timerLines))
        val label = row.findViewById<TextView>(R.id.timerSwitchLabel)
                for (i in 0 until label.layout.lineCount) assertEquals("label ellipsized", 0, label.layout.getEllipsisCount(i))
        val lines = (0 until label.layout.lineCount).map {
            label.text.subSequence(label.layout.getLineStart(it), label.layout.getLineEnd(it)).toString().trim()
        }
        if (ctx.resources.configuration.screenWidthDp < 400) {
            assertEquals("balanced break below 400dp", listOf("End drill", "at goal"), lines)
        } else {
            assertEquals("one line from 400dp", listOf("End drill at goal"), lines)
        }
        assertEquals("End drill at goal", row.findViewById<View>(R.id.timerSwitch).contentDescription.toString())
        val value = row.findViewById<TextView>(R.id.timerValue)
        assertEquals(1, value.layout.lineCount)
        assertEquals("value ellipsized", 0, value.layout.getEllipsisCount(0))
    }

    @Test
    fun `c switch has its full measured width at the right edge`() {
        val row = row()
        val sw = row.findViewById<MaterialSwitch>(R.id.timerSwitch)
        val laidOut = sw.width
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        sw.measure(spec, spec)
        assertTrue("switch ${laidOut}px < natural ${sw.measuredWidth}px", laidOut >= sw.measuredWidth)
        val l2 = row.findViewById<ViewGroup>(R.id.timerLine2)
        assertTrue("switch not at the right edge", sw.right == l2.width - l2.paddingRight)
        assertTrue("switch ${sw.height} < 48dp", sw.height >= dp(48))
    }

    @Test
    fun `d stepper targets are 48dp`() {
        val row = row()
        for (id in listOf(R.id.timerMinus, R.id.timerPlus)) {
            val v = row.findViewById<View>(id)
            assertTrue("${name(v)} ${v.width}x${v.height} < 48dp", v.width >= dp(48) && v.height >= dp(48))
        }
        assertTrue(row.findViewById<View>(R.id.timerLine2).height >= dp(48))
    }

    @Test
    fun `f value has 8dp to each visible button edge and fits 180 min`() {
        val row = row()
        val inset = ctx.resources.getDimensionPixelSize(R.dimen.timer_stepper_inset)
        val minus = row.findViewById<View>(R.id.timerMinus)
        val plus = row.findViewById<View>(R.id.timerPlus)
        val value = row.findViewById<TextView>(R.id.timerValue)
        assertEquals("visible circle", dp(36), minus.width - 2 * inset)
        assertEquals("visible circle", dp(36), plus.width - 2 * inset)
        val gapL = value.left - (minus.right - inset)
        val gapR = (plus.left + inset) - value.right
        assertTrue("gap minus->value ${gapL}px < 8dp", gapL >= dp(8))
        assertTrue("gap value->plus ${gapR}px < 8dp", gapR >= dp(8))
        assertEquals("180 min", value.text.toString())
        assertEquals(1, value.layout.lineCount)
        assertTrue("180 min wider than its box", value.layout.getLineWidth(0) <= value.width - value.paddingLeft - value.paddingRight)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `e same at w411dp`() {
        `a two lines line 1 icon and elapsed line 2 stepper and switch`()
        `b nothing clipped or overlapping`()
        `c switch has its full measured width at the right edge`()
        `d stepper targets are 48dp`()
        `f value has 8dp to each visible button edge and fits 180 min`()
    }
}
