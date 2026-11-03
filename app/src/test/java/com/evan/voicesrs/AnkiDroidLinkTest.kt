package com.evan.voicesrs

import android.app.Activity
import android.app.Application
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

/** h121c: "Open in Anki" = 4th item of the long-press menu on past chat bubbles. */
@RunWith(RobolectricTestRunner::class)
class AnkiDroidLinkTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun activity(): Activity =
        Robolectric.buildActivity(Activity::class.java).create().get()

    private fun toWindow(activity: Activity, bubble: View) {
        val root = activity.findViewById<View>(android.R.id.content) as ViewGroup
        val holder = LinearLayout(activity)
        root.addView(holder)
        holder.addView(bubble)
    }

    private fun bubbleWith(texts: List<String>): LinearLayout {
        val activity = activity()
        val bubble = LinearLayout(activity)
        for (t in texts) bubble.addView(TextView(activity).also { it.text = t })
        toWindow(activity, bubble)
        return bubble
    }

    @Test
    fun uriForEncodesCardId() {
        assertEquals("anki://x-callback-url/browser?search=cid%3A123", AnkiDroidLink.uriFor(123).toString())
    }

    @Test
    fun intentForViewsAnkiDroidPackageWithUriData() {
        val intent = AnkiDroidLink.intentFor(123)
        assertEquals("android.intent.action.VIEW", intent.action)
        assertEquals("com.ichi2.anki", intent.`package`)
        assertEquals("data matches uriFor", AnkiDroidLink.uriFor(123).toString(), intent.data?.toString())
    }

    @Test
    fun longPressShowsFourItemsInOrderWhenAllCallbacksSet() {
        val bubble = bubbleWith(listOf("Q?", "A"))
        BubbleMenu.attach(bubble, { _ -> }, { }, onOpenAnki = { })
        bubble.performLongClick()
        val menu = ShadowPopupMenu.getLatestPopupMenu().menu
        assertEquals("four items", 4, menu.size())
        assertEquals("Copy", menu.getItem(0).title.toString())
        assertEquals("Ask about this", menu.getItem(1).title.toString())
        assertEquals("Flag card", menu.getItem(2).title.toString())
        assertEquals("Open in Anki", menu.getItem(3).title.toString())
    }

    @Test
    fun clickOpenInAnkiFiresOnlyThatCallback() {
        val bubble = bubbleWith(listOf("Q?", "A"))
        var open = 0
        var asks = 0
        var flags = 0
        BubbleMenu.attach(bubble, { asks++ }, { flags++ }, onOpenAnki = { open++ })
        bubble.performLongClick()
        val popup = ShadowPopupMenu.getLatestPopupMenu()
        popup.menu.performIdentifierAction(popup.menu.getItem(3).itemId, 0)
        assertEquals("open fires once", 1, open)
        assertEquals("ask not fired", 0, asks)
        assertEquals("flag not fired", 0, flags)
    }

    @Test
    fun attachWithoutOpenInAnkiStillShowsThreeItems() {
        val bubble = bubbleWith(listOf("Q?", "A"))
        BubbleMenu.attach(bubble, { _ -> }, { })
        bubble.performLongClick()
        val menu = ShadowPopupMenu.getLatestPopupMenu().menu
        assertEquals("three items", 3, menu.size())
        assertEquals("Copy", menu.getItem(0).title.toString())
        assertEquals("Ask about this", menu.getItem(1).title.toString())
        assertEquals("Flag card", menu.getItem(2).title.toString())
    }

    @Test
    fun sourceContainsTheWiring() {
        val adapterPath = listOf(
            "src/main/java/com/evan/voicesrs/ChatAdapter.kt",
            "app/src/main/java/com/evan/voicesrs/ChatAdapter.kt"
        ).firstOrNull { java.io.File(it).exists() }
        assertNotNull("ChatAdapter.kt path", adapterPath)
        assertTrue("ChatAdapter.kt must wire onOpenAnki",
            java.io.File(adapterPath).readText().contains("onOpenAnki"))

        val panePath = listOf(
            "src/main/java/com/evan/voicesrs/ChatPane.kt",
            "app/src/main/java/com/evan/voicesrs/ChatPane.kt"
        ).firstOrNull { java.io.File(it).exists() }
        assertNotNull("ChatPane.kt path", panePath)
        val paneSrc = java.io.File(panePath).readText()
        assertTrue("ChatPane.kt must use AnkiDroidLink.intentFor",
            paneSrc.contains("AnkiDroidLink.intentFor"))
        assertTrue("ChatPane.kt must catch ActivityNotFoundException",
            paneSrc.contains("ActivityNotFoundException"))

        val manifestPath = listOf(
            "src/main/AndroidManifest.xml",
            "app/src/main/AndroidManifest.xml"
        ).firstOrNull { java.io.File(it).exists() }
        assertNotNull("AndroidManifest.xml path", manifestPath)
        assertTrue("AndroidManifest.xml must declare AnkiDroid package",
            java.io.File(manifestPath).readText().contains("com.ichi2.anki"))
    }
}
