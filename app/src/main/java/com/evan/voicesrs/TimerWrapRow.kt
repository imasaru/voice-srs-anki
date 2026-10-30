package com.evan.voicesrs

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.ceil

/** h94c: lead (child 0, line 1) + trail (child 1, label+switch). One line when the row's content width
 *  (own width minus padding) is >= 360dp, else trail drops below, right-aligned. Line 1 always fits:
 *  drop the icon first, then cap the elapsed chip width (ellipsized); the steppers are never squeezed. */
class TimerWrapRow(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {
    var oneLine: Boolean = false
        private set

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun outerW(v: View): Int {
        if (v.visibility == View.GONE) return 0
        val lp = v.layoutParams as? MarginLayoutParams
        return v.measuredWidth + (lp?.marginStart ?: 0) + (lp?.marginEnd ?: 0)
    }

    override fun generateLayoutParams(attrs: AttributeSet?) = MarginLayoutParams(context, attrs)
    override fun generateDefaultLayoutParams() = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val lead = getChildAt(0) as ViewGroup; val trail = getChildAt(1)
        val unspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        measureChild(trail, unspec, unspec)
        val gap = dp(8)
        val mode = MeasureSpec.getMode(wSpec)
        val avail = if (mode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE / 2
        else MeasureSpec.getSize(wSpec) - paddingLeft - paddingRight
        oneLine = avail >= dp(360)
        val budget = if (oneLine) avail - gap - trail.measuredWidth else avail
        val leadW = fitLead(lead, budget)
        lead.measure(MeasureSpec.makeMeasureSpec(maxOf(leadW, 0), MeasureSpec.EXACTLY), unspec)
        val pad = paddingTop + paddingBottom
        val h = pad + if (oneLine) maxOf(lead.measuredHeight, trail.measuredHeight) else lead.measuredHeight + trail.measuredHeight
        val content = if (mode == MeasureSpec.UNSPECIFIED) lead.measuredWidth + gap + trail.measuredWidth else avail
        setMeasuredDimension(resolveSize(content + paddingLeft + paddingRight, wSpec), resolveSize(h, hSpec))
    }

    /** Decide icon visibility and chip max width from natural widths, without remeasure loops (setters only on change). */
    private fun fitLead(lead: ViewGroup, budget: Int): Int {
        val icon = lead.findViewById<View>(R.id.timerIcon) ?: return budget
        val chip = lead.findViewById<TextView>(R.id.elapsedChip) ?: return budget
        val unspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var fixed = lead.paddingLeft + lead.paddingRight
        for (i in 0 until lead.childCount) {
            val c = lead.getChildAt(i)
            if (c === icon || c === chip || c.visibility == View.GONE) continue
            c.measure(getChildMeasureSpec(unspec, 0, c.layoutParams.width), getChildMeasureSpec(unspec, 0, c.layoutParams.height)); fixed += outerW(c)
        }
        val iconLp = icon.layoutParams as MarginLayoutParams
        val iconW = iconLp.width + iconLp.marginStart + iconLp.marginEnd
        val chipLp = chip.layoutParams as MarginLayoutParams
        val chipNat = if (chip.visibility == View.GONE) 0 else
            ceil(chip.paint.measureText(chip.text.toString())).toInt() + chip.compoundPaddingLeft + chip.compoundPaddingRight + chipLp.marginStart + chipLp.marginEnd
        val keepIcon = fixed + iconW + chipNat <= budget
        val iconVis = if (keepIcon) View.VISIBLE else View.GONE
        if (icon.visibility != iconVis) icon.visibility = iconVis
        val room = budget - fixed - (if (keepIcon) iconW else 0) - chipLp.marginStart - chipLp.marginEnd
        val cap = if (chip.visibility == View.GONE || chipNat <= budget - fixed - (if (keepIcon) iconW else 0)) Int.MAX_VALUE else maxOf(room, 0)
        if (chip.maxWidth != cap) chip.maxWidth = cap
        val chipW = if (chip.visibility == View.GONE) 0 else if (cap == Int.MAX_VALUE) chipNat else minOf(chipNat, cap + chipLp.marginStart + chipLp.marginEnd)
        // Width from our own sum (start/end margins included) so line 1 always holds the steppers.
        return minOf(budget, fixed + (if (keepIcon) iconW else 0) + chipW)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val lead = getChildAt(0); val trail = getChildAt(1)
        val cl = paddingLeft; val cr = width - paddingRight
        if (oneLine) {
            val ch = height - paddingTop - paddingBottom
            val lo = paddingTop + (ch - lead.measuredHeight) / 2
            val to = paddingTop + (ch - trail.measuredHeight) / 2
            lead.layout(cl, lo, cl + lead.measuredWidth, lo + lead.measuredHeight)
            trail.layout(cr - trail.measuredWidth, to, cr, to + trail.measuredHeight)
        } else {
            lead.layout(cl, paddingTop, cl + lead.measuredWidth, paddingTop + lead.measuredHeight)
            val tt = paddingTop + lead.measuredHeight
            trail.layout(cr - trail.measuredWidth, tt, cr, tt + trail.measuredHeight)
        }
    }
}
