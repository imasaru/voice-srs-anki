package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

/**
 * Runs [StudyTools] against AnkiConnect. cardReviews for the window,
 * cardsInfo for questions, notesInfo for tags. Failures return an error
 * JSON object so a grade or chat turn is not aborted.
 */
class StudyToolRunner(
    private val anki: AnkiConnect,
    private val defaultDeck: String,
    private val tz: TimeZone = TimeZone.getDefault(),
    private val timeLog: () -> List<AnswerTime> = { AnswerTimeLog.snapshot() }
) {
    fun invoke(name: String, argsJson: String): String {
        return try {
            val args = try {
                JSONObject(argsJson.ifBlank { "{}" })
            } catch (_: Exception) {
                JSONObject()
            }
            val from = args.optString("from").ifBlank { StudyTools.today(tz) }
            val to = args.optString("to").ifBlank { from }
            val deck = args.optString("deck").ifBlank { defaultDeck }
            val limit = args.optInt("limit", StudyTools.DEFAULT_LIMIT)
            val (rows, cards) = load(from, to, deck)
            when (name) {
                StudyTools.GET_STUDY_SUMMARY -> StudyTools.summary(rows)
                    .put("answer_time", answerTime(from, to, deck, rows.size))
                    .toString()
                StudyTools.GET_CATEGORY_STATS -> JSONObject()
                    .put("categories", StudyTools.categoryStats(rows, cards))
                    .put("answer_time", answerTime(from, to, deck, rows.size))
                    .toString()
                StudyTools.GET_MISSED_CARDS -> JSONObject()
                    .put("missed", StudyTools.missed(rows, cards, limit))
                    .toString()
                else -> JSONObject().put("error", "unknown tool").toString()
            }
        } catch (e: Exception) {
            JSONObject().put("error", e.javaClass.simpleName).toString()
        }
    }

    fun answerTime(from: String, to: String, deck: String, reviews: Int): JSONObject =
        StudyTools.answerTime(
            timeLog(),
            StudyTools.dayStartMs(from, tz),
            StudyTools.dayEndMs(to, tz),
            deck,
            reviews
        )

    fun todayBundle(deck: String): String {
        val day = StudyTools.today(tz)
        val (rows, cards) = load(day, day, deck.ifBlank { defaultDeck })
        return StudyTools.formatBundle(
            StudyTools.summary(rows),
            StudyTools.categoryStats(rows, cards),
            StudyTools.missed(rows, cards, StudyTools.DEFAULT_LIMIT)
        )
    }

    fun load(from: String, to: String, deck: String): Pair<List<StudyReview>, Map<Long, StudyCard>> {
        val start = StudyTools.dayStartMs(from, tz)
        val end = StudyTools.dayEndMs(to, tz)
        val raw = try {
            anki.cardReviews(deck, start - 1L)
        } catch (_: Exception) {
            emptyList()
        }
        val rows = raw.filter { it.atMs in start..end }
        val ids = rows.map { it.cardId }.distinct()
        val info = try {
            if (ids.isEmpty()) emptyList() else anki.cardsInfo(ids)
        } catch (_: Exception) {
            emptyList()
        }
        val noteIds = info.mapNotNull { it.noteId }.distinct()
        val tagsByNote = try {
            if (noteIds.isEmpty()) emptyMap() else anki.noteTags(noteIds)
        } catch (_: Exception) {
            emptyMap()
        }
        val cards = LinkedHashMap<Long, StudyCard>()
        for (card in info) {
            val tags = card.noteId?.let { tagsByNote[it] }.orEmpty()
            cards[card.cardId] = StudyCard(card.cardId, tags, card.front)
        }
        for (id in ids) {
            if (id !in cards) cards[id] = StudyCard(id, emptyList(), "")
        }
        return rows to cards
    }
}
