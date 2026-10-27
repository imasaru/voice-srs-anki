package com.evan.voicesrs

import org.json.JSONObject

/**
 * Pure analysis over card-thread history. Reads the same JSON shape the app
 * stores per card (a list of threads, each with user/assistant messages) and
 * says, per card: what the learner asked, what kind of question it was, and the
 * concept the assistant surfaced. No Android, no UI, no I/O.
 */
enum class QuestionType {
    REPORT_REQUEST,
    DEFINITION,
    ANALOGY,
    VISUALIZE,
    EXAMPLE,
    COMPARE,
    WHY,
    RECALL_CONFUSION,
    ANSWER_ATTEMPT,
    EXPLAIN,
    OTHER,
}

data class ThreadMsg(val role: String, val text: String, val at: Long)

data class AskedQuestion(val text: String, val type: QuestionType, val at: Long)

data class CardInsight(val cardId: Long, val asked: List<AskedQuestion>, val concept: String, val bookmarked: Boolean) {
    val mainType: QuestionType
        get() = asked.groupingBy { it.type }.eachCount().maxByOrNull { it.value }?.key ?: QuestionType.OTHER
}

object UnderstandingInsights {
    const val BOOKMARK = "[bookmark]"

    private val ANSWER_ATTEMPT = Regex("^(i say|i think|my answer|answer is|it's|is it)\\b.{0,12}$")

    /**
     * Parse the thread history into a map keyed by cardId (LinkedHashMap, in
     * first-seen cardId order). Messages from every thread of a card are merged
     * and sorted by `at`. Malformed input yields an empty map (never throws).
     */
    fun parse(json: String): Map<Long, List<ThreadMsg>> {
        val result = LinkedHashMap<Long, MutableList<ThreadMsg>>()
        return try {
            val root = JSONObject(json)
            val threads = root.getJSONArray("threads")
            for (i in 0 until threads.length()) {
                // Review fix: one bad thread or message is skipped instead of dropping the whole history.
                val thread = threads.optJSONObject(i) ?: continue
                if (!thread.has("cardId")) continue
                val cardId = thread.optLong("cardId", -1L).takeIf { it > 0 } ?: continue
                val messages = thread.optJSONArray("messages") ?: continue
                val msgs = result.getOrPut(cardId) { mutableListOf() }
                for (j in 0 until messages.length()) {
                    val m = messages.optJSONObject(j) ?: continue
                    msgs.add(ThreadMsg(m.optString("role", ""), m.optString("text", ""), m.optLong("at", 0L)))
                }
            }
            for (k in result.keys) {
                result[k]!!.sortBy { it.at }
            }
            result
        } catch (e: Exception) {
            LinkedHashMap<Long, MutableList<ThreadMsg>>()
        }
    }

    /**
     * Classify a single user utterance. First matching rule wins (lower-cased,
     * trimmed).
     */
    fun classify(text: String): QuestionType {
        val t = text.trim().lowercase()
        return when {
            t.contains("study report") || t.contains("today's report") || t.contains("daily report") -> QuestionType.REPORT_REQUEST
            t.startsWith("what is ") || t.startsWith("what's a ") || t.startsWith("what is a ") || t.startsWith("what does ") || t.startsWith("what are ") -> QuestionType.DEFINITION
            t.contains("llama") || t.contains("analogy") || t.contains("animal") -> QuestionType.ANALOGY
            t.contains("visualiz") || t.contains("imagine") -> QuestionType.VISUALIZE
            t.contains("example") -> QuestionType.EXAMPLE
            t.contains("difference") || t.contains(" vs ") || t.contains("versus") || t.contains("compare") -> QuestionType.COMPARE
            t.startsWith("why") || t.contains(" why ") -> QuestionType.WHY
            t.contains("remember") || t.contains("last time") -> QuestionType.RECALL_CONFUSION
            ANSWER_ATTEMPT.matches(t) || (t.length == 1 && t[0] in 'a'..'d') -> QuestionType.ANSWER_ATTEMPT
            t.contains("explain") || t.contains("no idea") || t.contains("don't know") || t.contains("don't understand") || t.contains("what it means") -> QuestionType.EXPLAIN
            else -> QuestionType.OTHER
        }
    }

    /**
     * The concept the assistant surfaced for a thread: from the first assistant
     * message, the rest of the line after "Core concept:" (strip "*" chars, then
     * drop one trailing "." if present) — returned in full since it is a curated
     * one-liner; otherwise the first sentence (up to the first ". " or newline),
     * trimmed and capped at 80 chars. "" if no assistant message.
     */
    fun concept(msgs: List<ThreadMsg>): String {
        val firstAssistant = msgs.firstOrNull { it.role == "assistant" } ?: return ""
        val text = firstAssistant.text
        val coreIdx = text.indexOf("Core concept:")
        if (coreIdx >= 0) {
            var rest = text.substring(coreIdx + "Core concept:".length)
            val nl = rest.indexOf('\n')
            if (nl >= 0) rest = rest.substring(0, nl)
            rest = rest.replace("*", "")
            if (rest.endsWith(".")) rest = rest.dropLast(1)
            return rest.trim()
        }
        val end = text.indexOf(". ")
        val nlEnd = text.indexOf('\n')
        val sentenceEnd = when {
            end >= 0 && (nlEnd < 0 || end < nlEnd) -> end
            nlEnd >= 0 -> nlEnd
            end >= 0 -> end
            else -> text.length
        }
        return if (sentenceEnd >= text.length) text.trim().take(80) else text.substring(0, sentenceEnd).trim().take(80)
    }

    /**
     * One CardInsight per cardId, in the same order as [parse]. A user message
     * whose text starts with [BOOKMARK] marks the card bookmarked and is not a
     * question.
     */
    fun insights(json: String): List<CardInsight> {
        val parsed = parse(json)
        val out = mutableListOf<CardInsight>()
        for ((cardId, msgs) in parsed) {
            val asked = mutableListOf<AskedQuestion>()
            var bookmarked = false
            for (m in msgs) {
                if (m.role != "user") continue
                if (m.text.startsWith(BOOKMARK)) {
                    bookmarked = true
                    continue
                }
                asked.add(AskedQuestion(m.text, classify(m.text), m.at))
            }
            out.add(CardInsight(cardId, asked, concept(msgs), bookmarked))
        }
        return out
    }

    /**
     * "## Where understanding broke" for messages with `at` in [startMs]..[endMs]:
     * one bullet per card that had questions (report requests excluded) or a
     * bookmark in the window, most questions first. [labels] names a card when
     * the thread has no "Core concept" line.
     */
    fun whereBroke(json: String, startMs: Long, endMs: Long, labels: Map<Long, String> = emptyMap()): String {
        val rows = ArrayList<Triple<Int, Boolean, String>>()
        for ((cardId, all) in parse(json)) {
            val today = all.filter { it.at in startMs..endMs }
            val users = today.filter { it.role == "user" }
            val bookmarked = users.any { it.text.startsWith(BOOKMARK) }
            val asked = users.filter { !it.text.startsWith(BOOKMARK) }
                .map { AskedQuestion(it.text, classify(it.text), it.at) }
                .filter { it.type != QuestionType.REPORT_REQUEST }
            if (asked.isEmpty() && !bookmarked) continue
            val name = concept(all).ifBlank { labels[cardId].orEmpty() }.ifBlank { "Card $cardId" }
            val line = StringBuilder("- ").append(oneLine(name, 110))
            if (asked.isNotEmpty()) {
                val main = CardInsight(cardId, asked, "", false).mainType
                line.append(": ").append(asked.size).append(if (asked.size == 1) " question" else " questions")
                    .append(" (mostly ").append(main.name.lowercase().replace('_', ' ')).append(")")
                line.append(" — \"").append(oneLine(asked.first().text, 90)).append("\"")
            }
            if (bookmarked) line.append(if (asked.isEmpty()) ": bookmarked" else "; bookmarked")
            rows += Triple(asked.size, bookmarked, line.toString())
        }
        val sb = StringBuilder("## Where understanding broke\n")
        if (rows.isEmpty()) {
            sb.append("- No card-thread questions or bookmarks today.\n")
        } else {
            rows.sortedWith(compareByDescending<Triple<Int, Boolean, String>> { it.first }.thenByDescending { it.second })
                .forEach { sb.append(it.third).append('\n') }
        }
        return sb.toString()
    }

    private fun oneLine(t: String, max: Int): String {
        val flat = t.replace(Regex("\\s+"), " ").trim()
        return if (flat.length > max) flat.substring(0, max - 1).trimEnd() + "…" else flat
    }
}
