package com.evan.voicesrs

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** h121d2: wire the bubble corner icons in ChatAdapter.PastHolder (part 2 of 3). */
@RunWith(RobolectricTestRunner::class)
class BubbleCornerIconsTest {
    private val ctx =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_VoiceSrs)

    @After
    fun reset() {
        ReviewLog.clear()
        DrillUi.update { DrillUiState() }
    }

    /** Records the callbacks the corner icons are wired to. */
    private class Hooks {
        var bookmarkCount = 0
        var bookmarkTurn: ReviewTurn? = null
        var explExpandedCount = 0
        var spoke: Triple<Long, Int, String>? = null
    }

    private class Bind(val holder: ChatAdapter.PastHolder, val adapter: ChatAdapter)

    /** The seven corner ImageButtons plus the card stem, reached by id (PastHolder fields are private). */
    private class Icons(
        val cardBookmark: ImageButton,
        val cardPlay: ImageButton,
        val userBookmark: ImageButton,
        val userPlay: ImageButton,
        val aiNote: ImageButton,
        val aiBookmark: ImageButton,
        val aiPlay: ImageButton,
        val cardStem: TextView
    )

    private fun icons(holder: ChatAdapter.PastHolder): Icons = Icons(
        holder.itemView.findViewById(R.id.cardBookmarkIcon) as ImageButton,
        holder.itemView.findViewById(R.id.cardPlayIcon) as ImageButton,
        holder.itemView.findViewById(R.id.userBookmarkIcon) as ImageButton,
        holder.itemView.findViewById(R.id.userPlayIcon) as ImageButton,
        holder.itemView.findViewById(R.id.aiNoteIcon) as ImageButton,
        holder.itemView.findViewById(R.id.aiBookmarkIcon) as ImageButton,
        holder.itemView.findViewById(R.id.aiPlayIcon) as ImageButton,
        holder.itemView.findViewById(R.id.cardStem) as TextView
    )

    private fun bindHolder(
        isBookmarked: (ReviewTurn) -> Boolean = { false },
        hooks: Hooks = Hooks()
    ): Bind {
        val adapter = ChatAdapter(
            onExplExpanded = { hooks.explExpandedCount++ },
            onRetry = { },
            onThread = { },
            onSpeakSection = { id, index, text -> hooks.spoke = Triple(id, index, text) },
            onBookmark = { hooks.bookmarkTurn = it; hooks.bookmarkCount++ },
            isBookmarked = isBookmarked
        )
        // TYPE_PAST is a private const (inlined, no backing field); its value is 1 (see ChatAdapter companion).
        val holder = adapter.onCreateViewHolder(FrameLayout(ctx), 1) as ChatAdapter.PastHolder
        return Bind(holder, adapter)
    }

    private fun resId(ib: ImageButton): Int? = shadowOf(ib.drawable).createdFromResId

    private fun readyTurn(): ReviewTurn = ReviewLog.appendAfterGrade(
        ease = 3, cardId = 7L, noteId = null,
        frontText = "Masking hides a column. It runs at query time.",
        backText = "The model hides masking at query time.", contentHash = "d2-ready",
        transcript = "masking", cachedText = "The model hides masking at query time.",
        aiEnabled = true
    )!!

    private fun noExplainTurn(): ReviewTurn = ReviewLog.appendAfterGrade(
        ease = 3, cardId = 7L, noteId = null,
        frontText = "Masking hides a column.",
        backText = "answer", contentHash = "d2-none",
        transcript = "masking", cachedText = null,
        aiEnabled = false
    )!!

    @Test
    fun `a clicking a bookmark icon bookmarks the turn and fills all three`() {
        val hooks = Hooks()
        val bind = bindHolder(hooks = hooks)
        val turn = readyTurn()
        bind.holder.bind(turn)
        val i = icons(bind.holder)

        i.cardBookmark.performClick()

        assertEquals(1, hooks.bookmarkCount)
        assertEquals(turn, hooks.bookmarkTurn)
        assertEquals(R.drawable.ic_bookmark_filled, resId(i.cardBookmark))
        assertEquals(R.drawable.ic_bookmark_filled, resId(i.userBookmark))
        assertEquals(R.drawable.ic_bookmark_filled, resId(i.aiBookmark))
    }

    @Test
    fun `b bound bookmark icon reflects isBookmarked`() {
        val filled = bindHolder(isBookmarked = { true })
        filled.holder.bind(readyTurn())
        val f = icons(filled.holder)
        assertEquals(R.drawable.ic_bookmark_filled, resId(f.cardBookmark))
        assertEquals(R.drawable.ic_bookmark_filled, resId(f.userBookmark))
        assertEquals(R.drawable.ic_bookmark_filled, resId(f.aiBookmark))

        val outline = bindHolder(isBookmarked = { false })
        outline.holder.bind(readyTurn())
        val o = icons(outline.holder)
        assertEquals(R.drawable.ic_bookmark, resId(o.cardBookmark))
        assertEquals(R.drawable.ic_bookmark, resId(o.userBookmark))
        assertEquals(R.drawable.ic_bookmark, resId(o.aiBookmark))
    }

    @Test fun `f tapping a filled bookmark outlines all three`() {
        val hooks = Hooks()
        val bind = bindHolder(hooks = hooks, isBookmarked = { true })
        bind.holder.bind(readyTurn())
        val f = icons(bind.holder)
        assertEquals(R.drawable.ic_bookmark_filled, resId(f.cardBookmark))
        f.cardBookmark.performClick()
        assertEquals(1, hooks.bookmarkCount)
        assertEquals(R.drawable.ic_bookmark, resId(f.cardBookmark))
        assertEquals(R.drawable.ic_bookmark, resId(f.userBookmark))
        assertEquals(R.drawable.ic_bookmark, resId(f.aiBookmark))
    }

    @Test
    fun `c play icons speak their bubble with a negative section`() {
        val hooks = Hooks()
        val bind = bindHolder(hooks = hooks)
        val turn = readyTurn()
        bind.holder.bind(turn)

        icons(bind.holder).aiPlay.performClick()
        assertEquals(turn.turnId, hooks.spoke?.first)
        assertEquals(-3, hooks.spoke?.second)
        assertTrue("expected non-blank ai text, was '${hooks.spoke?.third}'", hooks.spoke?.third?.isNotBlank() == true)

        icons(bind.holder).cardPlay.performClick()
        assertEquals(turn.turnId, hooks.spoke?.first)
        assertEquals(-1, hooks.spoke?.second)
        assertTrue("expected non-blank card text, was '${hooks.spoke?.third}'", hooks.spoke?.third?.isNotBlank() == true)
    }

    @Test
    fun `d ai note icon is gone without a ready explanation and visible with one`() {
        val gone = bindHolder()
        gone.holder.bind(noExplainTurn())
        assertEquals(View.GONE, icons(gone.holder).aiNote.visibility)

        val shown = bindHolder()
        shown.holder.bind(readyTurn())
        assertEquals(View.VISIBLE, icons(shown.holder).aiNote.visibility)
    }

    @Test
    fun `e icon clicks leave the card collapsed and never expand the explanation`() {
        val hooks = Hooks()
        val bind = bindHolder(hooks = hooks)
        val turn = readyTurn()
        bind.holder.bind(turn)
        val collapsed = icons(bind.holder).cardStem.maxLines
        assertEquals(BubbleModel.COLLAPSED_LINES, collapsed)

        val i = icons(bind.holder)
        i.cardBookmark.performClick()
        i.cardPlay.performClick()
        i.userPlay.performClick()
        i.aiPlay.performClick()

        assertEquals(collapsed, icons(bind.holder).cardStem.maxLines)
        assertEquals(0, hooks.explExpandedCount)
        assertTrue("card should stay collapsed", bind.adapter.expandedIds().isEmpty())
    }
}
