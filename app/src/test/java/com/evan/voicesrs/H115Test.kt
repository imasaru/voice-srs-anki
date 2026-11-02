package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * h115 get_exam_readiness: the pure forecast [examReadinessJson], the tool
 * schema, invoke routing through a fake [StudyAnki], and lastIvl-preserving
 * revRows parsing. Values below are hand-computed from the fixed dataset.
 */
class H115Test {
    private val tokyoZone = ZoneId.of("Asia/Tokyo")
    private val tokyoTz = TimeZone.getTimeZone("Asia/Tokyo")
    private val today = LocalDate.of(2026, 11, 2)
    private val exam = LocalDate.of(2027, 2, 15) // days_left = 105
    private val t0 = AnkiDay.startMs(today, tokyoZone)
    private val day = 86_400_000L

    // 7 cards: new=2 learning=2 young=1 mature=2 suspended=1 (total 7).
    private val cards = listOf(
        CardSnap(1, "Lang::Vocab", tags = listOf("verbs"), type = 0),
        CardSnap(2, "Lang::Grammar", tags = listOf("verbs"), type = 0),
        CardSnap(3, "Lang::Vocab", tags = listOf("nouns"), type = 1),
        CardSnap(4, "Lang::Grammar", tags = emptyList(), type = 3, ivl = 30),
        CardSnap(5, "Lang::Vocab", tags = listOf("nouns"), type = 2, ivl = 10),
        CardSnap(6, "Lang::Grammar", tags = listOf("nouns"), type = 2, ivl = 21),
        CardSnap(7, "Lang::Vocab", tags = listOf("nouns"), type = 2, ivl = 100, queue = -1)
    )
    private val tags = cards.associate { it.cardId to it.tags }

    // 9 revlog rows. type 0 = first-introduce (new), type 1 = review.
    // One row (card 7) sits before the window so pace/retention skip it.
    private val rows = listOf(
        RevRow(t0 + 1, 1, 3, 0, lastIvl = 0, deck = "Lang::Vocab"),
        RevRow(t0 + 2, 2, 3, 0, lastIvl = 0, deck = "Lang::Grammar"),
        RevRow(t0 + 3, 5, 3, 1, lastIvl = 10, deck = "Lang::Vocab"),
        RevRow(t0 + 4, 5, 1, 1, lastIvl = 10, deck = "Lang::Vocab"),
        RevRow(t0 + 5, 6, 3, 1, lastIvl = 30, deck = "Lang::Grammar"),
        RevRow(t0 + 6, 1, 1, 1, lastIvl = 25, deck = "Lang::Vocab"),
        RevRow(t0 + 7, 2, 1, 1, lastIvl = 5, deck = "Lang::Vocab"),
        RevRow(t0 + 8, 3, 3, 1, lastIvl = 0, deck = "Lang::Grammar"),
        RevRow(t0 - 1, 7, 1, 1, lastIvl = 5, deck = "Lang::Vocab")
    )

    private class FakeStudyAnki(
        private val cards: List<CardSnap>,
        private val rows: List<RevRow>
    ) : StudyAnki {
        var sawCardSnaps = false
        var sawRevRows = false
        override fun cardReviewsTree(deck: String, startId: Long): List<StudyReview> = emptyList()
        override fun cardsInfo(ids: List<Long>): List<OfflineCard> = emptyList()
        override fun noteTags(ids: List<Long>): Map<Long, List<String>> = emptyMap()
        override fun cardSnaps(query: String): List<CardSnap> {
            sawCardSnaps = true
            return cards
        }
        override fun revRows(deck: String, startMs: Long): List<RevRow> {
            sawRevRows = true
            return rows
        }
    }

    private fun runner(): StudyToolRunner =
        StudyToolRunner(FakeStudyAnki(cards, rows), "Lang", tokyoTz, examDate = { exam })

    @Test
    fun `fixed forecast JSON has states retention pace weakest and projection`() {
        val j = JSONObject(runner().examReadinessJson(today, exam, cards, rows, tags, 14, tokyoZone).toString())
        assertEquals("2027-02-15", j.optString("exam_date"))
        assertEquals(105, j.getInt("days_left"))

        val st = j.getJSONObject("states")
        assertEquals(2, st.getInt("new"))
        assertEquals(2, st.getInt("learning"))
        assertEquals(1, st.getInt("young"))
        assertEquals(2, st.getInt("mature"))
        assertEquals(1, st.getInt("suspended"))
        assertEquals(7, st.getInt("total"))

        val ret = j.getJSONObject("retention")
        assertEquals(7, ret.getInt("reviews"))
        assertEquals(42.9, ret.getDouble("pct"), 0.0)
        assertEquals(40.0, ret.getDouble("young_pct"), 0.0)
        assertEquals(50.0, ret.getDouble("mature_pct"), 0.0)

        val pc = j.getJSONObject("pace")
        assertEquals(14, pc.getInt("days"))
        assertEquals(0.1, pc.getDouble("new_per_day"), 0.0)
        assertEquals(0.6, pc.getDouble("reviews_per_day"), 0.0)

        val wd = j.getJSONArray("weakest_decks")
        assertEquals(2, wd.length())
        assertEquals("Lang::Vocab", wd.getJSONObject(0).getString("name"))
        assertEquals(4, wd.getJSONObject(0).getInt("lapses"))
        assertEquals(5, wd.getJSONObject(0).getInt("reviews"))
        assertEquals(80.0, wd.getJSONObject(0).getDouble("pct"), 0.0)
        assertEquals("Lang::Grammar", wd.getJSONObject(1).getString("name"))
        assertEquals(0.0, wd.getJSONObject(1).getDouble("pct"), 0.0)

        val wt = j.getJSONArray("weakest_tags")
        assertEquals(2, wt.length())
        assertEquals("verbs", wt.getJSONObject(0).getString("name"))
        assertEquals(2, wt.getJSONObject(0).getInt("lapses"))
        assertEquals(2, wt.getJSONObject(0).getInt("reviews"))
        assertEquals(100.0, wt.getJSONObject(0).getDouble("pct"), 0.0)
        assertEquals("nouns", wt.getJSONObject(1).getString("name"))
        assertEquals(2, wt.getJSONObject(1).getInt("lapses"))
        assertEquals(5, wt.getJSONObject(1).getInt("reviews"))
        assertEquals(40.0, wt.getJSONObject(1).getDouble("pct"), 0.0)

        val pr = j.getJSONObject("projection")
        assertEquals(18, pr.getInt("days_to_introduce"))
        assertEquals("2026-12-22", pr.optString("ready_date"))
        assertEquals(55, pr.getInt("slack_days"))
        assertTrue(pr.getBoolean("on_track"))
        assertEquals(0.0, pr.getDouble("required_new_per_day"), 0.0)

        val a = j.getJSONArray("assumptions")
        assertEquals(4, a.length())
        assertEquals("90% of new cards introduced", a.getString(0))
        assertEquals("mature = interval >= 21 days", a.getString(1))
        assertEquals("32 days from first learn to mature", a.getString(2))
        assertEquals("Anki day rolls over at 4:00", a.getString(3))
    }

    @Test
    fun `null exam leaves exam_date days_left and slack null but projection still computes`() {
        val j = JSONObject(runner().examReadinessJson(today, null, cards, rows, tags, 14, tokyoZone).toString())
        assertTrue(j.isNull("exam_date"))
        assertEquals(-1, j.optInt("days_left", -1))
        val pr = j.getJSONObject("projection")
        assertEquals(18, pr.getInt("days_to_introduce"))
        assertEquals("2026-12-22", pr.optString("ready_date"))
        assertTrue(pr.isNull("slack_days"))
        assertTrue(pr.isNull("on_track"))
        assertTrue(pr.isNull("required_new_per_day"))
    }

    @Test
    fun `window_days is clamped to 3 to 60`() {
        val j999 = JSONObject(runner().invoke(StudyTools.GET_EXAM_READINESS, "{\"window_days\":999}"))
        assertEquals(60, j999.getJSONObject("pace").getInt("days"))
        val j1 = JSONObject(runner().invoke(StudyTools.GET_EXAM_READINESS, "{\"window_days\":1}"))
        assertEquals(3, j1.getJSONObject("pace").getInt("days"))
    }

    @Test
    fun `pace window is the past windowDays days ending today`() {
        // A review from two days ago is inside the 3-day window (today, yesterday, the day before); one dated tomorrow is not.
        val rows = listOf(
            RevRow(t0 - 2 * day, 1L, 3, 1, lastIvl = 5, deck = "Lang::Vocab"),
            RevRow(t0 - 2 * day, 2L, 3, 1, lastIvl = 5, deck = "Lang::Vocab"),
            RevRow(t0 + day, 3L, 3, 1, lastIvl = 5, deck = "Lang::Vocab")
        )
        val j = JSONObject(runner().examReadinessJson(today, exam, emptyList(), rows, emptyMap(), 3, tokyoZone).toString())
        val pc = j.getJSONObject("pace")
        assertEquals(3, pc.getInt("days"))
        assertEquals(0.7, pc.getDouble("reviews_per_day"), 0.0) // 2 reviews / 3 days
        assertEquals(0.0, pc.getDouble("new_per_day"), 0.0)      // no type-0 rows
    }

    @Test
    fun `tool is listed in StudyTools schemas and names`() {
        assertTrue(StudyTools.names().contains(StudyTools.GET_EXAM_READINESS))
        val schemas = StudyTools.schemas().toString()
        assertTrue(schemas.contains(StudyTools.GET_EXAM_READINESS))
        assertTrue(schemas.contains("window_days"))
        assertTrue(schemas.contains("Numbers are computed by the app"))
    }

    @Test
    fun `invoke routes get_exam_readiness through the fake StudyAnki`() {
        val fake = FakeStudyAnki(cards, rows)
        val runner = StudyToolRunner(fake, "Lang", tokyoTz, examDate = { exam })
        val j = JSONObject(runner.invoke(StudyTools.GET_EXAM_READINESS, "{}"))
        assertTrue(fake.sawCardSnaps)
        assertTrue(fake.sawRevRows)
        assertEquals("2027-02-15", j.optString("exam_date"))
        assertEquals(7, j.getJSONObject("states").getInt("total"))
    }

    @Test
    fun `revRows parsing keeps lastIvl from revlog index 5`() {
        val arr = JSONArray()
        arr.put(JSONArray(listOf(1000L, 42L, 0L, 3, 21, 15, 3000, 5000L, 1)))
        val rows = StudyTools.parseRevRows(arr)
        assertEquals(1, rows.size)
        assertEquals(1000L, rows[0].atMs)
        assertEquals(42L, rows[0].cardId)
        assertEquals(3, rows[0].ease)
        assertEquals(1, rows[0].type)
        assertEquals(15, rows[0].lastIvl)
        assertFalse(rows[0].deck.isNotEmpty())
    }
}
