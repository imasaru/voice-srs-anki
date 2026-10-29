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

/** h82 / p82a / R17: narrow split-screen timer row in every real host, at 320-700dp: nothing clipped or overlapping, switch full width at the right edge of line 1, 48dp targets, 36dp visible stepper circles with >= 8dp to the value. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h640dp")
class TimerRowNarrowTest {
    private val ctx = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_VoiceSrs)
    private val density get() = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    /** Inflates the row drilling at 1:26:23 with a 180-min goal (widest value) and lays it out in a host with the given side padding. */
    private fun row(sidePadDp: Int = 16): ViewGroup {
        val host = FrameLayout(ctx)
        host.setPadding(dp(sidePadDp), 0, dp(sidePadDp), 0)
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
        ViewClipCheck.assertNoChildClipped(root)
        val visible = (0 until root.childCount).map { root.getChildAt(it) }.filter { it.visibility == View.VISIBLE }
        for (i in visible.indices) for (j in i + 1 until visible.size) {
            val a = Rect(visible[i].left, visible[i].top, visible[i].right, visible[i].bottom)
            val b = Rect(visible[j].left, visible[j].top, visible[j].right, visible[j].bottom)
            assertTrue("${name(visible[i])} overlaps ${name(visible[j])}", !Rect.intersects(a, b))
        }
    }

    @Test
    fun `a line 1 icon elapsed label switch line 2 stepper`() {
        val row = row()
        val l1 = row.findViewById<ViewGroup>(R.id.timerLine1)
        val l2 = row.findViewById<ViewGroup>(R.id.timerLine2)
        assertTrue("line 2 is not below line 1", l2.top >= l1.bottom)
        listOf(R.id.timerIcon, R.id.elapsedChip, R.id.timerSwitchLabel, R.id.timerSwitch)
            .forEachIndexed { i, id -> assertEquals("line 1 order", i, l1.indexOfChild(row.findViewById(id))) }
        listOf(R.id.timerMinus, R.id.timerValue, R.id.timerPlus, R.id.timerSpacer)
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
        assertTrue("label > 2 lines", label.layout.lineCount <= 2)
        assertEquals("End drill at goal", lines.joinToString(" "))
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
        val l1 = row.findViewById<ViewGroup>(R.id.timerLine1)
        assertTrue("switch not at the right edge", sw.right == l1.width - l1.paddingRight)
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
        assertEquals("value ellipsized", 0, value.layout.getEllipsisCount(0))
        assertTrue("180 min wider than its box", value.layout.getLineWidth(0) <= value.width - value.paddingLeft - value.paddingRight)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `e same at w411dp`() {
        `a line 1 icon elapsed label switch line 2 stepper`()
        `b nothing clipped or overlapping`()
        `c switch has its full measured width at the right edge`()
        `d stepper targets are 48dp`()
        `f value has 8dp to each visible button edge and fits 180 min`()
    }

    /** p82a REPRO: the deck card insets the row more than the slim header, so at w411dp with a 48dp host the label+switch on line 2 of the old layout overflow the right edge. Fails on 978a534, passes after the fix. */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `g deck card inset at w411dp`() {
        val row = row(48)
        val l1 = row.findViewById<ViewGroup>(R.id.timerLine1)
        assertNoClipOrOverlap(row.findViewById(R.id.timerLines))
        val sw = row.findViewById<MaterialSwitch>(R.id.timerSwitch)
        assertTrue("switch not at right edge", sw.right == l1.width - l1.paddingRight)
        // idle case: elapsedChip GONE -> icon and switch both on line 1
        row.findViewById<View>(R.id.elapsedChip).visibility = View.GONE
        assertTrue("icon not on line 1", l1.indexOfChild(row.findViewById(R.id.timerIcon)) >= 0)
        assertTrue("switch not on line 1", l1.indexOfChild(sw) >= 0)
        assertEquals("switch not at right edge (idle)", sw.right, l1.width - l1.paddingRight)
    }

    // Hosts of include_timer_row (grep app/src/main/res):
    //  - drill header: layout/activity_main.xml (timerRow inside slimHeader, 16dp sides) at w320/w411/w700,
    //    and layout-w600dp/activity_main.xml at w600 (its left pane's statusCard -> include_timer_row;
    //    the timerRow is reached transitively, see the timerRowInclude ?: timerRow fallback in hostTest).
    //  - deck card: include_status_card.xml (statusCard app:contentPadding=20dp), hosted in the w600 list
    //    in a LinearLayout padding="@dimen/screen_padding"=20dp (replicated on the test's deckHost FrameLayout).

    private fun measureHost(root: View) {
        val w = dp(ctx.resources.configuration.screenWidthDp)
        val h = dp(ctx.resources.configuration.screenHeightDp)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun setTimerState(row: ViewGroup, drilling: Boolean) {
        val chip = row.findViewById<TextView>(R.id.elapsedChip)
        val sw = row.findViewById<MaterialSwitch>(R.id.timerSwitch)
        if (drilling) {
            chip.text = ElapsedChip.format(5_183_000L)
            chip.visibility = View.VISIBLE
            row.findViewById<TextView>(R.id.timerValue).text = "180 min"
            sw.isChecked = true
        } else {
            chip.visibility = View.GONE
            sw.isChecked = false
        }
    }

    private fun verifyRow(row: ViewGroup) {
        val l1 = row.findViewById<ViewGroup>(R.id.timerLine1)
        val sw = row.findViewById<MaterialSwitch>(R.id.timerSwitch)
        ViewClipCheck.assertNoChildClipped(row)
        assertTrue("switch not at right edge", sw.right == l1.width - l1.paddingRight)
        assertTrue("icon not in line1", l1.indexOfChild(row.findViewById(R.id.timerIcon)) >= 0)
        assertTrue("switch not in line1", l1.indexOfChild(sw) >= 0)
    }

    private fun hostTest() {
        // Drill-header host: base activity_main (timerRow inside slimHeader, 16dp sides).
        // The <include android:id="@+id/timerRowInclude"> overrides the timerRow CardView's own
        // id on the narrow layout (findViewById(R.id.timerRow) is null there), so take timerRowInclude
        // when present and fall back to timerRow (the only path at w600dp, reached via include_status_card).
        val drillRoot = LayoutInflater.from(ctx).inflate(R.layout.activity_main, null)
        val drillRow = drillRoot.findViewById<ViewGroup>(R.id.timerRowInclude)
                ?: drillRoot.findViewById<ViewGroup>(R.id.timerRow)
        for (drilling in listOf(true, false)) {
            setTimerState(drillRow, drilling)
            measureHost(drillRoot)
            verifyRow(drillRow)
        }

        // Deck-card host: include_status_card into a FrameLayout of screen width with the same
        // 20dp side padding the home list uses (layout-w600dp/activity_main.xml hosts statusCard in a
        // LinearLayout padding="@dimen/screen_padding"=20dp; statusCard itself has app:contentPadding=20dp).
        val w = dp(ctx.resources.configuration.screenWidthDp)
        val deckHost = FrameLayout(ctx)
        deckHost.setPadding(dp(20), 0, dp(20), 0)
        val card = LayoutInflater.from(ctx).inflate(R.layout.include_status_card, deckHost, false) as ViewGroup
        deckHost.addView(card)
        deckHost.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(ctx.resources.configuration.screenHeightDp), View.MeasureSpec.EXACTLY),
        )
        deckHost.layout(0, 0, w, deckHost.measuredHeight)
        for (drilling in listOf(true, false)) {
            setTimerState(card, drilling)
            deckHost.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(deckHost.measuredHeight, View.MeasureSpec.EXACTLY),
            )
            deckHost.layout(0, 0, w, deckHost.measuredHeight)
            verifyRow(card)
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `h hosts at 320dp`() = hostTest()

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `i hosts at 411dp`() = hostTest()

    @Test
    @Config(qualifiers = "w600dp-h891dp")
    fun `j hosts at 600dp`() = hostTest()

    @Test
    @Config(qualifiers = "w700dp-h891dp")
    fun `k hosts at 700dp`() = hostTest()
}
