package com.evan.voicesrs

import android.app.Activity
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowPopupMenu
import org.robolectric.shadows.ShadowToast

/** h121a: long-press "Copy" menu on past chat bubbles (Copy only). */
@RunWith(RobolectricTestRunner::class)
class BubbleMenuTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun activity(): Activity =
        Robolectric.buildActivity(Activity::class.java).create().get()

    /** Attach [bubble] to the activity's window so PopupMenu can anchor to it. */
    private fun toWindow(activity: Activity, bubble: View) {
        val root = activity.findViewById<View>(android.R.id.content) as ViewGroup
        val holder = LinearLayout(activity)
        root.addView(holder)
        holder.addView(bubble)
    }

    private fun clipboardText(): String? {
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.text?.toString()
    }

    @Test
    fun a_visibleTextJoinsVisibleTextViewsSkipsGoneAndBlank() {
        val v = LinearLayout(app)
        val a = TextView(app); a.text = "One"
        val gone = TextView(app); gone.text = "GoneText"; gone.visibility = View.GONE
        val blank = TextView(app); blank.text = "   "
        val b = TextView(app); b.text = "Two"
        v.addView(a); v.addView(gone); v.addView(blank); v.addView(b)
        assertEquals("One\nTwo", BubbleMenu.visibleText(v))
    }

    @Test
    fun b_visibleTextIsDepthFirstThroughVisibleGroupsSkipsInvisibleGroups() {
        val outer = LinearLayout(app)
        val inner = LinearLayout(app)
        val a = TextView(app); a.text = "A"
        val b = TextView(app); b.text = "B"
        inner.addView(a); inner.addView(b)
        val hidden = LinearLayout(app); hidden.addView(TextView(app).also { it.text = "Hidden" })
        hidden.visibility = View.GONE
        outer.addView(LinearLayout(app)) // empty visible group contributes nothing
        outer.addView(hidden)
        outer.addView(inner)
        assertEquals("A\nB", BubbleMenu.visibleText(outer))
    }

    @Test
    fun c_longPressShowsSingleCopyItemAndCopiesBubbleText() {
        val activity = activity()
        val bubble = LinearLayout(activity)
        bubble.addView(TextView(activity).also { it.text = "Question?" })
        bubble.addView(TextView(activity).also { it.text = "Answer" })
        toWindow(activity, bubble)
        BubbleMenu.attach(bubble)

        var tapped = false
        bubble.setOnClickListener { tapped = true }

        val handled = bubble.performLongClick()
        assertTrue("long-press listener consumes the event", handled)
        assertFalse("long-press must not fire the tap listener", tapped)

        val popup = ShadowPopupMenu.getLatestPopupMenu()
        assertNotNull("a PopupMenu should be shown", popup)
        val menu = popup.menu
        assertEquals("exactly one menu item", 1, menu.size())
        val item = menu.getItem(0)
        assertEquals("Copy", item.title.toString())

        // Click the real menu item (exercises the onMenuItemClick wiring we set in attach).
        popup.menu.performIdentifierAction(item.itemId, 0)

        assertEquals("Question?\nAnswer", clipboardText())
        assertEquals(app.getString(R.string.chat_copied), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun d_longPressDoesNotFireClickListener() {
        val activity = activity()
        val bubble = LinearLayout(activity)
        bubble.addView(TextView(activity).also { it.text = "Hi" })
        toWindow(activity, bubble)
        BubbleMenu.attach(bubble)
        var clicked = false
        bubble.setOnClickListener { clicked = true }
        bubble.performLongClick()
        assertFalse(clicked)
    }

    @Test
    fun e_longPressOnAlsoViewCopiesBubbleText() {
        val activity = activity()
        val bubble = LinearLayout(activity)
        bubble.addView(TextView(activity).also { it.text = "Q?" })
        val ans = TextView(activity); ans.text = "A"
        bubble.addView(ans)
        toWindow(activity, bubble)
        BubbleMenu.attach(bubble, ans) // ans is an "also" view

        assertTrue(ans.performLongClick())
        assertEquals("long-press alone must not copy", null, clipboardText())
        val popup = ShadowPopupMenu.getLatestPopupMenu()
        assertNotNull(popup)
        popup.menu.performIdentifierAction(popup.menu.getItem(0).itemId, 0)
        assertEquals("also view copies the BUBBLE's text, not its own", "Q?\nA", clipboardText())
    }

    @Test
    fun f_chatAdapterUsesBubbleMenuAndDroppedSelectable() {
        val path = listOf(
            "src/main/java/com/evan/voicesrs/ChatAdapter.kt",
            "app/src/main/java/com/evan/voicesrs/ChatAdapter.kt"
        ).firstOrNull { java.io.File(it).exists() }
        assertNotNull("ChatAdapter.kt path", path)
        val src = java.io.File(path).readText()
        assertTrue(src.contains("BubbleMenu.attach(card"))
        assertTrue(src.contains("R.id.userBubble"))
        assertTrue(src.contains("BubbleMenu.attach(ai"))
        assertFalse(src.contains("BubbleCopy.selectable(userAnswer"))
        assertFalse(src.contains("BubbleCopy.selectable(body"))
    }
}
