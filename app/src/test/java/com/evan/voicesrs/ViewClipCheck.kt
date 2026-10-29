package com.evan.voicesrs
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertTrue
/** p82a: after measure+layout, fail if any VISIBLE descendant is outside its parent's bounds or laid out narrower/shorter than it measured. */
object ViewClipCheck {
    fun assertNoChildClipped(root: View) = walk(root, "root")
    private fun name(v: View) = if (v.id == View.NO_ID) v.javaClass.simpleName else try { v.resources.getResourceEntryName(v.id) } catch (e: Exception) { v.javaClass.simpleName }
    private fun walk(v: View, path: String) {
        if (v !is ViewGroup) return
        for (i in 0 until v.childCount) {
            val c = v.getChildAt(i); if (c.visibility != View.VISIBLE) continue
            val p = "$path/${name(c)}"
            assertTrue("$p clipped: [${c.left},${c.top},${c.right},${c.bottom}] outside ${v.width}x${v.height}", c.left >= 0 && c.top >= 0 && c.right <= v.width && c.bottom <= v.height)
            assertTrue("$p squeezed: ${c.width}x${c.height} < measured ${c.measuredWidth}x${c.measuredHeight}", c.width >= c.measuredWidth && c.height >= c.measuredHeight)
            walk(c, p)
        }
    }
}
