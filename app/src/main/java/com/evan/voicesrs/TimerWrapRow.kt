package com.evan.voicesrs

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/** h94c: wrap lead (child 0) + trail (child 1) on one line if they fit the row's own width,
 *  else drop the trail below the lead, right-aligned. Wrap is decided by the row width only. */
class TimerWrapRow(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {
    var oneLine: Boolean = false
        private set

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val avail = if (View.MeasureSpec.getMode(wSpec) == View.MeasureSpec.EXACTLY)
            View.MeasureSpec.getSize(wSpec) - paddingLeft - paddingRight
        else View.MeasureSpec.getSize(wSpec)
        val lead = getChildAt(0); val trail = getChildAt(1)
        val wrap = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val aim = View.MeasureSpec.makeMeasureSpec(avail, View.MeasureSpec.AT_MOST)
        measureChild(lead, aim, wrap); measureChild(trail, aim, wrap)
        val gap = (8f * resources.displayMetrics.density).toInt()
        oneLine = lead.measuredWidth + gap + trail.measuredWidth <= avail
        val pad = paddingTop + paddingBottom
        val h = if (oneLine) pad + maxOf(lead.measuredHeight, trail.measuredHeight)
        else pad + lead.measuredHeight + trail.measuredHeight
        setMeasuredDimension(resolveSize(avail + paddingLeft + paddingRight, wSpec), h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, b: Int, r: Int) {
        val lead = getChildAt(0); val trail = getChildAt(1)
        val cl = paddingLeft; val cr = width - paddingRight
        val ch = height - paddingTop - paddingBottom
        if (oneLine) {
            val lo = (ch - lead.measuredHeight) / 2
            val to = (ch - trail.measuredHeight) / 2
            lead.layout(cl, paddingTop + lo, cl + lead.measuredWidth, paddingTop + lo + lead.measuredHeight)
            trail.layout(cr - trail.measuredWidth, paddingTop + to, cr, paddingTop + to + trail.measuredHeight)
        } else {
            lead.layout(cl, paddingTop, cl + lead.measuredWidth, paddingTop + lead.measuredHeight)
            trail.layout(cr - trail.measuredWidth, paddingTop + lead.measuredHeight, cr, paddingTop + lead.measuredHeight + trail.measuredHeight)
        }
    }
}
