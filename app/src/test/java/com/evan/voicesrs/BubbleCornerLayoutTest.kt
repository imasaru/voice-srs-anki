package com.evan.voicesrs

import android.app.Activity
import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** h121d1: bubble corner icon rows (layout only, part 1 of 2). */
@RunWith(RobolectricTestRunner::class)
class BubbleCornerLayoutTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun activity(): Activity =
        Robolectric.buildActivity(Activity::class.java).create().get()

    private fun inflated(): View {
        val activity = activity()
        val parent = FrameLayout(activity)
        return LayoutInflater.from(activity).inflate(R.layout.item_chat_turn, parent, false)
    }

    private fun <T : View> View.vf(id: Int): T = findViewById(id)

    private fun bubble(v: View, id: Int): LinearLayout = v.vf(id)

    private fun rowIds(row: ViewGroup): List<Int> =
        (0 until row.childCount).map { row.getChildAt(it).id }

    private fun iconDesc(v: View, id: Int, expected: String) {
        val view = v.vf<View>(id)
        assertNotNull("$id present", view)
        assertTrue("$id is ImageButton", view is ImageButton)
        val ib = view as ImageButton
        assertNotNull("$id has drawable", ib.drawable)
        assertEquals("$id contentDescription", expected, ib.contentDescription?.toString())
    }

    @Test
    fun a_cornerRowsAreFirstChildOfEachBubble() {
        val v = inflated()
        assertEquals("cardCorner first", R.id.cardCorner, bubble(v, R.id.cardBubble).getChildAt(0).id)
        assertEquals("userCorner first", R.id.userCorner, bubble(v, R.id.userBubble).getChildAt(0).id)
        assertEquals("aiCorner first", R.id.aiCorner, bubble(v, R.id.aiBubble).getChildAt(0).id)
    }

    @Test
    fun b_cornerRowChildIdsInOrder() {
        val v = inflated()
        assertEquals(
            "card order",
            listOf(R.id.cardBookmarkIcon, R.id.cardPlayIcon),
            rowIds(bubble(v, R.id.cardCorner))
        )
        assertEquals(
            "user order",
            listOf(R.id.userBookmarkIcon, R.id.userPlayIcon),
            rowIds(bubble(v, R.id.userCorner))
        )
        assertEquals(
            "ai order",
            listOf(R.id.aiNoteIcon, R.id.aiBookmarkIcon, R.id.aiPlayIcon),
            rowIds(bubble(v, R.id.aiCorner))
        )
    }

    @Test
    fun c_eachIconIsImagebuttonWithDrawableAndContentDescription() {
        val v = inflated()
        iconDesc(v, R.id.cardBookmarkIcon, app.getString(R.string.corner_bookmark))
        iconDesc(v, R.id.cardPlayIcon, app.getString(R.string.corner_read_aloud))
        iconDesc(v, R.id.userBookmarkIcon, app.getString(R.string.corner_bookmark))
        iconDesc(v, R.id.userPlayIcon, app.getString(R.string.corner_read_aloud))
        iconDesc(v, R.id.aiNoteIcon, app.getString(R.string.corner_make_card))
        iconDesc(v, R.id.aiBookmarkIcon, app.getString(R.string.corner_bookmark))
        iconDesc(v, R.id.aiPlayIcon, app.getString(R.string.corner_read_aloud))
    }

    @Test
    fun d_oldViewsStillPresent() {
        val v = inflated()
        assertNotNull("threadLink present", v.vf<View>(R.id.threadLink))
    }
}
