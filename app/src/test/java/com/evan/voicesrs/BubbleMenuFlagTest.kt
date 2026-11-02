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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowPopupMenu

/** h121b: long-press "Ask about this" + "Flag card" rows added after "Copy". */
@RunWith(RobolectricTestRunner::class)
class BubbleMenuFlagTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun activity(): Activity =
        Robolectric.buildActivity(Activity::class.java).create().get()

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

    private fun bubbleWith(texts: List<String>): LinearLayout {
        val activity = activity()
        val bubble = LinearLayout(activity)
        for (t in texts) bubble.addView(TextView(activity).also { it.text = t })
        toWindow(activity, bubble)
        return bubble
    }

    @Test
    fun a_menuShowsCopyAskFlagInOrderWhenBothCallbacksSet() {
        val bubble = bubbleWith(listOf("Q?", "A"))
        BubbleMenu.attach(bubble, { _ -> }, { })
        bubble.performLongClick()
        val popup = ShadowPopupMenu.getLatestPopupMenu()
        assertNotNull(popup)
        val menu = popup.menu
        assertEquals("three items", 3, menu.size())
        assertEquals("Copy", menu.getItem(0).title.toString())
        assertEquals("Ask about this", menu.getItem(1).title.toString())
        assertEquals("Flag card", menu.getItem(2).title.toString())
    }

    @Test
    fun b_onlyAskShowsTwoItems() {
        val bubble = bubbleWith(listOf("Q?", "A"))
        BubbleMenu.attach(bubble, { _ -> }, null)
        bubble.performLongClick()
        val menu = ShadowPopupMenu.getLatestPopupMenu().menu
        assertEquals("two items", 2, menu.size())
        assertEquals("Copy", menu.getItem(0).title.toString())
        assertEquals("Ask about this", menu.getItem(1).title.toString())
    }

    @Test
    fun c_oldAttachShowsOneItem() {
        val bubble = bubbleWith(listOf("Q?", "A"))
        BubbleMenu.attach(bubble)
        bubble.performLongClick()
        val menu = ShadowPopupMenu.getLatestPopupMenu().menu
        assertEquals("one item", 1, menu.size())
        assertEquals("Copy", menu.getItem(0).title.toString())
    }

    @Test
    fun d_clickAskCallsOnAskWithQuoteAndDoesNotCopy() {
        val bubble = bubbleWith(listOf("Q?", "A"))
        var asked: String? = null
        BubbleMenu.attach(bubble, { asked = it }, null)
        bubble.performLongClick()
        val popup = ShadowPopupMenu.getLatestPopupMenu()
        popup.menu.performIdentifierAction(popup.menu.getItem(1).itemId, 0)
        assertEquals("> Q?\n> A\n", asked)
        assertEquals("asking must not copy", null, clipboardText())
    }

    @Test
    fun e_clickFlagCallsOnFlagOnceAndLongPressCallsNeither() {
        val bubble = bubbleWith(listOf("Q?", "A"))
        var flags = 0
        var asks = 0
        BubbleMenu.attach(bubble, { asks++ }, { flags++ })
        // Long-press alone fires neither callback.
        bubble.performLongClick()
        assertEquals("long-press must not fire callbacks", 0, flags)
        assertEquals("long-press must not fire callbacks", 0, asks)
        // Click Flag card.
        val popup = ShadowPopupMenu.getLatestPopupMenu()
        popup.menu.performIdentifierAction(popup.menu.getItem(2).itemId, 0)
        assertEquals("flag fires once", 1, flags)
        assertEquals("flag does not ask", 0, asks)
    }

    @Test
    fun f_quotePrefixedAndTrailingNewline() {
        assertEquals("> a\n> b\n", BubbleMenu.quote("a\nb"))
    }

    @Test
    fun g_addTagsParamsNamesNotesAndFlagTag() {
        val params = AnkiConnect.addTagsParams(listOf(5L))
        @Suppress("UNCHECKED_CAST")
        val notes = params["notes"] as? List<*>
        assertEquals("notes", listOf(5L), notes)
        assertEquals("tags", "voice-srs-flag", params["tags"])
    }

    @Test
    fun h_sourceContainsTheWiring() {
        val path = listOf(
            "src/main/java/com/evan/voicesrs/ChatAdapter.kt",
            "app/src/main/java/com/evan/voicesrs/ChatAdapter.kt"
        ).firstOrNull { java.io.File(it).exists() }
        assertNotNull("ChatAdapter.kt path", path)
        val adapterSrc = java.io.File(path).readText()
        assertTrue(adapterSrc.contains("onAskAbout"))
        assertTrue(adapterSrc.contains("onFlag"))

        val panePath = listOf(
            "src/main/java/com/evan/voicesrs/ChatPane.kt",
            "app/src/main/java/com/evan/voicesrs/ChatPane.kt"
        ).firstOrNull { java.io.File(it).exists() }
        assertNotNull("ChatPane.kt path", panePath)
        val paneSrc = java.io.File(panePath).readText()
        assertTrue(paneSrc.contains("flagCard"))
        assertTrue(paneSrc.contains("R.id.chatInput"))
    }
}
