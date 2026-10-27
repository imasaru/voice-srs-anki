package com.evan.voicesrs

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * [StudyToolRunner] against the [StudyAnki] seam, so no network is needed.
 * [FakeAnki.cardReviewsTree] mirrors AnkiConnect: it keeps only decks that
 * [StudyTools.deckMatches] the target, unions their rows, and sorts by atMs.
 */
class StudyToolRunnerTest {
    private val tz = TimeZone.getTimeZone("UTC")

    /** Today in [tz]; todayBundle reads the real clock, so a fixed date rots at midnight. */
    private fun today(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = tz }.format(java.util.Date())

    private class FakeAnki(
        private val rowsByDeck: Map<String, List<StudyReview>>,
        private val crashDeck: String? = null,
        private val crashCardsInfo: Boolean = false
    ) : StudyAnki {
        /** Deck names cardReviewsTree queried (i.e. the ones it kept). */
        val queried = mutableListOf<String>()

        override fun cardReviewsTree(deck: String, startId: Long): List<StudyReview> {
            val out = ArrayList<StudyReview>()
            for (name in rowsByDeck.keys) {
                if (!StudyTools.deckMatches(name, deck)) continue
                queried += name
                if (crashDeck == name) throw RuntimeException("boom $name")
                out.addAll(rowsByDeck.getValue(name))
            }
            return out.sortedBy { it.atMs }
        }

        override fun cardsInfo(ids: List<Long>): List<OfflineCard> {
            if (crashCardsInfo) throw RuntimeException("boom cardsInfo")
            return emptyList()
        }
        override fun noteTags(ids: List<Long>): Map<Long, List<String>> = emptyMap()
    }

    @Test
    fun todayBundleIncludesSubdeckReviews() {
        val day = today()
        val at = StudyTools.dayStartMs(day, tz) + 60_000L
        val fake = FakeAnki(
            mapOf(
                "Parent" to emptyList<StudyReview>(),
                "Parent::Sub" to listOf(StudyReview(at, 100L, 3, 5000L))
            )
        )
        val runner = StudyToolRunner(fake, "Parent", tz)
        val bundle = JSONObject(runner.todayBundle("Parent"))
        assertEquals(1, bundle.getJSONObject("summary").getInt("reviews"))
    }

    @Test
    fun todayBundleReturnsErrorWhenCardsInfoThrows() {
        val day = today()
        val at = StudyTools.dayStartMs(day, tz) + 60_000L
        val fake = FakeAnki(
            mapOf("Parent" to listOf(StudyReview(at, 100L, 3, 5000L))),
            crashCardsInfo = true
        )
        val runner = StudyToolRunner(fake, "Parent", tz)
        val bundle = JSONObject(runner.todayBundle("Parent"))
        assertTrue("expected an error key, got $bundle", bundle.has("error"))
    }

    @Test
    fun todayBundleErrorKeepsLocalAnswerTime() {
        val day = today()
        val at = StudyTools.dayStartMs(day, tz) + 60_000L
        val log = listOf(AnswerTime(1L, "Parent", 3, 3000L, at))
        val fake = FakeAnki(
            mapOf("Parent" to emptyList<StudyReview>()),
            crashDeck = "Parent"
        )
        val runner = StudyToolRunner(fake, "Parent", tz) { log }
        val bundle = JSONObject(runner.todayBundle("Parent"))
        assertTrue("expected an error key, got $bundle", bundle.has("error"))
        assertEquals(
            3000L,
            bundle.getJSONObject("answer_time").getLong("total_answer_time_ms")
        )
    }

    @Test
    fun cardReviewsTreeQueriesOnlyMatchingAndSortsByAtMs() {
        val fake = FakeAnki(
            mapOf(
                "Parent" to listOf(StudyReview(300L, 1L, 3, 0L)),
                "Parent::Sub" to listOf(
                    StudyReview(100L, 2L, 3, 0L),
                    StudyReview(200L, 3L, 3, 0L)
                ),
                "Other" to listOf(StudyReview(50L, 4L, 3, 0L))
            )
        )
        val tree = fake.cardReviewsTree("Parent", 0L)
        // Only the parent and its subdeck are queried; "Other" is skipped.
        assertEquals(listOf("Parent", "Parent::Sub"), fake.queried)
        // Rows from every matching deck merged and sorted by atMs.
        assertEquals(listOf(2L, 3L, 1L), tree.map { it.cardId })
    }
}
