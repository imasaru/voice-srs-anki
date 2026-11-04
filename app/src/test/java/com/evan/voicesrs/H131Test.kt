package com.evan.voicesrs

import android.app.Activity
import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class H131Test {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun inflated(): View {
        val a = Robolectric.buildActivity(Activity::class.java).create().get()
        return LayoutInflater.from(a).inflate(R.layout.item_chat_live, android.widget.FrameLayout(a), false)
    }

    private fun <T : View> View.vf(id: Int): T = findViewById(id)
    private fun rowIds(row: ViewGroup) = (0 until row.childCount).map { row.getChildAt(it).id }

    @Test
    fun liveBubbleLeadsWithCornerRow() {
        val bubble = inflated().vf<LinearLayout>(R.id.liveBubble)
        assertEquals("child 0 is liveCorner", R.id.liveCorner, bubble.getChildAt(0).id)
    }

    @Test
    fun liveCornerHasBookmarkThenReadAloud() {
        val corner = inflated().vf<LinearLayout>(R.id.liveCorner)
        assertEquals("corner child ids", listOf(R.id.liveBookmarkIcon, R.id.livePlayIcon), rowIds(corner))
        assertTrue("bookmark is ImageButton", corner.getChildAt(0) is ImageButton)
        assertTrue("play is ImageButton", corner.getChildAt(1) is ImageButton)
    }

    @Test
    fun liveCardHasNoMakeCardIcon() {
        val makeCard = app.getString(R.string.corner_make_card)
        assertTrue("no view carries the make-card label", collectDescs(inflated()).none { it == makeCard })
    }

    private fun collectDescs(v: View): List<String> = buildList {
        fun walk(node: View) {
            val cd = node.contentDescription
            if (cd != null && cd.isNotBlank()) add(cd.toString())
            if (node is ViewGroup) repeat(node.childCount) { walk(node.getChildAt(it)) }
        }
        walk(v)
    }
}
