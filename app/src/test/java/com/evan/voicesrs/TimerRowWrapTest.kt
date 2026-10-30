package com.evan.voicesrs

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.materialswitch.MaterialSwitch
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** h94c: compact timer row. Row content width (own width minus padding) >= 360dp -> one line; below -> label+switch wrap to line 2, right-aligned.
 *  Nothing clipped, 36dp visible stepper circles, label "End at goal", switch desc "End drill at goal".
 *  Also every host placement (activity_main drill header, include_status_card deck card). Writes wrap_*.png. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimerRowWrapTest {
    private val ctx = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_VoiceSrs)
    private val density get() = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private fun name(v: View) = if (v.id == View.NO_ID) v.javaClass.simpleName else ctx.resources.getResourceEntryName(v.id)

    private fun drilling(row: View, on: Boolean) {
        val chip = row.findViewById<TextView>(R.id.elapsedChip)
        if (on) { chip.text = ElapsedChip.short(5_183_000L); chip.contentDescription = ElapsedChip.format(5_183_000L); chip.visibility = View.VISIBLE; row.findViewById<TextView>(R.id.timerValue).text = "180 min" }
        else { chip.visibility = View.GONE; row.findViewById<TextView>(R.id.timerValue).text = "20 min" }
        row.findViewById<MaterialSwitch>(R.id.timerSwitch).isChecked = on
    }

    private fun contentDp(row: View) = row.findViewById<View>(R.id.timerLines).let { (it.width - it.paddingLeft - it.paddingRight) / density }

    /** The row alone, sized so its content width (timerLines minus padding) is [rowDp]. */
    private fun bareRow(rowDp: Int, on: Boolean): ViewGroup {
        val host = FrameLayout(ctx); host.setBackgroundColor(Color.WHITE)
        val row = LayoutInflater.from(ctx).inflate(R.layout.include_timer_row, host, false) as ViewGroup
        host.addView(row); drilling(row, on)
        host.measure(View.MeasureSpec.makeMeasureSpec(dp(rowDp + 100), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        host.layout(0, 0, dp(rowDp + 100), host.measuredHeight)
        val lines = row.findViewById<View>(R.id.timerLines)
        val w = dp(rowDp) + host.width - (lines.width - lines.paddingLeft - lines.paddingRight)
        host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        host.layout(0, 0, w, host.measuredHeight)
        return host
    }

    private fun verify(row: View, expectOneLine: Boolean, tag: String) {
        val wrap = row.findViewById<TimerWrapRow>(R.id.timerLines)
        val l1 = row.findViewById<ViewGroup>(R.id.timerLine1); val l2 = row.findViewById<ViewGroup>(R.id.timerLine2)
        val sw = row.findViewById<MaterialSwitch>(R.id.timerSwitch)
        val label = row.findViewById<TextView>(R.id.timerSwitchLabel)
        val wDp = contentDp(row)
        assertEquals("$tag oneLine at content ${wDp}dp", expectOneLine, wrap.oneLine)
        if (expectOneLine) assertTrue("$tag one line: l2 not beside l1", l2.top < l1.bottom && l2.left >= l1.right)
        else assertTrue("$tag wrap: l2 not below l1", l2.top >= l1.bottom)
        ViewClipCheck.assertNoChildClipped(row.rootView)
        assertFalse("$tag l1/l2 overlap", Rect.intersects(Rect(l1.left, l1.top, l1.right, l1.bottom), Rect(l2.left, l2.top, l2.right, l2.bottom)))
        assertEquals("$tag switch at right edge", wrap.width - wrap.paddingRight, l2.right)
        assertEquals("$tag switch end of l2", l2.width, sw.right)
        assertEquals("End at goal", label.text.toString())
        assertEquals(1, label.layout.lineCount); assertEquals(0, label.layout.getEllipsisCount(0))
        assertEquals("End drill at goal", sw.contentDescription.toString())
        val value = row.findViewById<TextView>(R.id.timerValue)
        assertEquals(1, value.layout.lineCount); assertEquals(0, value.layout.getEllipsisCount(0))
        val chip = row.findViewById<TextView>(R.id.elapsedChip)
        if (chip.visibility == View.VISIBLE) assertEquals("1:26:23", chip.text.toString())
        val inset = ctx.resources.getDimensionPixelSize(R.dimen.timer_stepper_inset)
        for (id in listOf(R.id.timerMinus, R.id.timerPlus)) {
            val b = row.findViewById<View>(id)
            assertEquals("$tag ${name(b)} visible circle 36dp", dp(36), b.width - 2 * inset)
            assertEquals("$tag ${name(b)} visible circle 36dp", dp(36), b.height - 2 * inset)
            assertTrue("$tag ${name(b)} target < 48dp", b.width >= dp(48) && b.height >= dp(48))
        }
    }

    private fun shotsDir(): File {
        var d = File(".").absoluteFile
        repeat(6) { if (File(d, "settings.gradle").exists() || File(d, "settings.gradle.kts").exists()) return File(d, "app/build/screenshots"); d = d.parentFile ?: return@repeat }
        return File("app/build/screenshots")
    }
    private fun shot(v: View, name: String) {
        val bmp = Bitmap.createBitmap(maxOf(v.width, 1), maxOf(v.height, 1), Bitmap.Config.ARGB_8888)
        Canvas(bmp).also { it.drawColor(Color.WHITE); v.draw(it) }
        val f = File(shotsDir(), "$name.png"); f.parentFile!!.mkdirs()
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun bare(rowDp: Int) {
        for (on in listOf(true, false)) {
            val h = bareRow(rowDp, on)
            assertEquals("content width", rowDp.toFloat(), contentDp(h.getChildAt(0)), 0.5f)
            verify(h.getChildAt(0), rowDp >= 360, "bare ${rowDp}dp drilling=$on")
            assertEquals("bare: chip not ellipsized", Int.MAX_VALUE, h.findViewById<TextView>(R.id.elapsedChip).maxWidth)
            shot(h, "wrap_row_${rowDp}dp_" + if (on) "drill" else "idle")
        }
    }

    /** Hosts: activity_main drill header (layout or layout-w600dp) and include_status_card in the 20dp home list padding. */
    private fun hosts() {
        val w = dp(ctx.resources.configuration.screenWidthDp); val h = dp(ctx.resources.configuration.screenHeightDp)
        val root = LayoutInflater.from(ctx).inflate(R.layout.activity_main, null)
        val row = root.findViewById<ViewGroup>(R.id.timerRowInclude) ?: root.findViewById(R.id.timerRow)
        for (on in listOf(true, false)) {
            drilling(row, on)
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, w, h)
            val rowDp = contentDp(row)
            verify(row, rowDp >= 360f, "drill host w${ctx.resources.configuration.screenWidthDp} drilling=$on row=${rowDp}dp")
            if (on) shot(row, "wrap_host_drill_w${ctx.resources.configuration.screenWidthDp}dp")
        }
        val deckHost = FrameLayout(ctx); deckHost.setPadding(dp(20), 0, dp(20), 0); deckHost.setBackgroundColor(Color.WHITE)
        val card = LayoutInflater.from(ctx).inflate(R.layout.include_status_card, deckHost, false) as ViewGroup
        deckHost.addView(card)
        for (on in listOf(true, false)) {
            drilling(card, on)
            deckHost.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            deckHost.layout(0, 0, w, deckHost.measuredHeight)
            val rowDp = contentDp(card)
            verify(card, rowDp >= 360f, "deck host w${ctx.resources.configuration.screenWidthDp} drilling=$on row=${rowDp}dp")
            if (!on) shot(deckHost, "wrap_host_deck_w${ctx.resources.configuration.screenWidthDp}dp")
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp") fun `w320 bare row and hosts`() { bare(320); hosts() }
    @Test @Config(qualifiers = "w340dp-h891dp") fun `w340 bare row and hosts`() { bare(340); hosts() }
    @Test @Config(qualifiers = "w360dp-h800dp") fun `w360 bare row and hosts`() { bare(360); hosts() }
    @Test @Config(qualifiers = "w411dp-h891dp") fun `w411 bare row and hosts`() { bare(411); hosts() }
    @Test @Config(qualifiers = "w600dp-h891dp") fun `w600 bare row and hosts`() { bare(600); hosts() }
    @Test @Config(qualifiers = "w700dp-h891dp") fun `w700 bare row and hosts`() { bare(700); hosts() }
    @Test @Config(qualifiers = "w411dp-h891dp") fun `content 359 wraps 360 one line`() {
        verify(bareRow(359, true).getChildAt(0), false, "359"); verify(bareRow(360, true).getChildAt(0), true, "360")
    }
}
