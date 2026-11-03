package com.evan.voicesrs

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** h104c2: ring on the lit rating chip that empties over the auto-accept hold. */
class CountdownRing(color: Int, private val density: Float, val step: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * density; strokeCap = Paint.Cap.ROUND; this.color = color
    }
    private val oval = RectF()
    override fun draw(canvas: Canvas) {
        val inset = paint.strokeWidth / 2f
        oval.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
        canvas.drawArc(oval, -90f, sweep(step), false, paint)
    }
    override fun getIntrinsicWidth(): Int = (14 * density).toInt()
    override fun getIntrinsicHeight(): Int = (14 * density).toInt()
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    companion object {
        fun sweep(step: Int): Float = GradeConfirm.ringFraction(step) * 360f
    }
}
