package com.evan.voicesrs

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** h122: vertical stack of two fixed rows (child 0 on top, child 1 below), both measured at full avail width.
 *  The only rule is the hero elapsed text size: 28sp at >= 200dp content width, else 24sp (mockup-spec).
 *  Line 1 always fits (no icon-drop, no maxWidth cap); the steppers are never squeezed. */
class TimerWrapRow(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {
    var oneLine: Boolean = false
        private set

    val heroSp: Float
        get() = _heroSp

    private var _heroSp = 28f

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun generateLayoutParams(attrs: AttributeSet?) = MarginLayoutParams(context, attrs)
    override fun generateDefaultLayoutParams() = MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val top = getChildAt(0); val bottom = getChildAt(1)
        val mode = MeasureSpec.getMode(wSpec)
        val avail = if (mode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE / 2 else MeasureSpec.getSize(wSpec) - paddingLeft - paddingRight
        val hero = findViewById<TextView>(R.id.elapsedChip)
        val target = if (avail >= dp(200)) 28f else 24f
        if (target != _heroSp) { _heroSp = target; hero.setTextSize(TypedValue.COMPLEX_UNIT_SP, target) }
        val unspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val match = MeasureSpec.makeMeasureSpec(avail, MeasureSpec.EXACTLY)
        top.measure(match, unspec); bottom.measure(match, unspec)
        val pad = paddingTop + paddingBottom
        val h = pad + top.measuredHeight + bottom.measuredHeight
        setMeasuredDimension(resolveSize(avail + paddingLeft + paddingRight, wSpec), resolveSize(h, hSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val top = getChildAt(0); val bottom = getChildAt(1)
        val cl = paddingLeft; val cr = width - paddingRight
        top.layout(cl, paddingTop, cr, paddingTop + top.measuredHeight)
        bottom.layout(cl, paddingTop + top.measuredHeight, cr, paddingTop + top.measuredHeight + bottom.measuredHeight)
    }
}
