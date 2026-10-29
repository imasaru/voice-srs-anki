package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Durable chat feed. JSON under filesDir, append + load, cap [CAP].
 * A corrupt file starts empty and does not crash.
 */
data class StoredChat(
    val kind: String,
    val id: Long,
    val at: Long,
    val question: String,
    val answer: String = "",
    val deck: String = "",
    val cardId: Long = 0L,
    val noteId: Long? = null,
    val ease: Int? = null,
    val transcript: String = "",
    val questionLabel: String = "",
    val contentHash: String = "",
    val judgeReason: String = "",
    val judgeEase: Int? = null
) {
    val key: String get() = "$kind:$id"

    fun toAsk(): ChatRow.Ask {
        val state = if (answer.isBlank()) ExplState.None else ExplState.Ready(answer, cached = true)
        return ChatRow.Ask(id, question, state, deck, at = this.at)
    }

    fun toTurn(): ReviewTurn {
        val explanation = if (answer.isBlank()) ExplState.None else ExplState.Ready(answer, cached = true)
        return ReviewTurn(
            turnId = id,
            cardId = cardId,
            noteId = noteId,
            frontText = question,
            backText = "",
            contentHash = contentHash,
            transcript = transcript,
            ease = ease,
            explanation = explanation,
            questionLabel = questionLabel,
            judgeReason = judgeReason,
            judgeEase = judgeEase,
            at = this.at
        )
    }

    companion object {
        const val ASK = "ask"
        const val TURN = "turn"

        fun ofAsk(ask: ChatRow.Ask, at: Long = ask.at.takeIf { it > 0 } ?: System.currentTimeMillis()): StoredChat? {
            val answer = when (val state = ask.state) {
                is ExplState.Ready -> state.text
                is ExplState.Error -> state.message
                else -> return null
            }
            if (ask.question.isBlank() && answer.isBlank()) return null
            return StoredChat(
                kind = ASK,
                id = ask.id,
                at = at,
                question = ask.question,
                answer = answer,
                deck = ask.deck
            )
        }

        fun ofTurn(turn: ReviewTurn, at: Long = turn.at.takeIf { it > 0 } ?: System.currentTimeMillis()): StoredChat {
            val answer = when (val state = turn.explanation) {
                is ExplState.Ready -> state.text
                is ExplState.Error -> state.message
                else -> ""
            }
            return StoredChat(
                kind = TURN,
                id = turn.turnId,
                at = at,
                question = turn.frontText,
                answer = answer,
                cardId = turn.cardId,
                noteId = turn.noteId,
                ease = turn.ease,
                transcript = turn.transcript,
                questionLabel = turn.questionLabel,
                contentHash = turn.contentHash,
                judgeReason = turn.judgeReason,
                judgeEase = turn.judgeEase
            )
        }
    }
}

object ChatHistoryStore {
    const val FILE_NAME = "chat-history.json"
    const val CAP = 2000
    private val lock = Any()

    @Volatile private var items: List<StoredChat> = emptyList()
    @Volatile private var dir: File? = null

    fun file(dir: File): File = File(dir, FILE_NAME)

    fun snapshot(): List<StoredChat> = synchronized(lock) { items }

    fun load(dir: File): List<StoredChat> = synchronized(lock) {
        this.dir = dir
        val f = file(dir)
        if (!f.isFile) {
            items = emptyList()
            return items
        }
        val raw = try {
            f.readText()
        } catch (_: Exception) {
            items = emptyList()
            return items
        }
        items = decode(raw)
        return items
    }

    fun save(dir: File, rows: List<StoredChat>) = synchronized(lock) {
        this.dir = dir
        val trimmed = trim(rows)
        items = trimmed
        write(dir, trimmed)
    }

    fun upsert(row: StoredChat) {
        val next: List<StoredChat>
        synchronized(lock) {
            val copy = items.toMutableList()
            val index = copy.indexOfFirst { it.key == row.key }
            if (index >= 0) copy[index] = row else copy += row
            next = trim(copy)
            items = next
        }
        dir?.let { write(it, next) }
    }

    fun encode(rows: List<StoredChat>): String {
        val arr = JSONArray()
        for (row in rows) {
            val o = JSONObject()
                .put("kind", row.kind)
                .put("id", row.id)
                .put("at", row.at)
                .put("question", row.question)
                .put("answer", row.answer)
            if (row.deck.isNotBlank()) o.put("deck", row.deck)
            if (row.cardId != 0L) o.put("cardId", row.cardId)
            if (row.noteId != null) o.put("noteId", row.noteId)
            if (row.ease != null) o.put("ease", row.ease)
            if (row.transcript.isNotBlank()) o.put("transcript", row.transcript)
            if (row.questionLabel.isNotBlank()) o.put("questionLabel", row.questionLabel)
            if (row.contentHash.isNotBlank()) o.put("contentHash", row.contentHash)
            if (row.judgeReason.isNotBlank()) o.put("judgeReason", row.judgeReason)
            if (row.judgeEase != null) o.put("judgeEase", row.judgeEase)
            arr.put(o)
        }
        return JSONObject().put("messages", arr).toString()
    }

    fun decode(raw: String): List<StoredChat> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("messages") ?: return emptyList()
            val out = ArrayList<StoredChat>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val kind = o.optString("kind")
                if (kind != StoredChat.ASK && kind != StoredChat.TURN) continue
                val id = o.optLong("id", 0L)
                if (id == 0L) continue
                out += StoredChat(
                    kind = kind,
                    id = id,
                    at = o.optLong("at", 0L),
                    question = o.optString("question"),
                    answer = o.optString("answer"),
                    deck = o.optString("deck"),
                    cardId = o.optLong("cardId", 0L),
                    noteId = if (o.has("noteId") && !o.isNull("noteId")) o.optLong("noteId") else null,
                    ease = if (o.has("ease") && !o.isNull("ease")) o.optInt("ease") else null,
                    transcript = o.optString("transcript"),
                    questionLabel = o.optString("questionLabel"),
                    contentHash = o.optString("contentHash"),
                    judgeReason = o.optString("judgeReason"),
                    judgeEase = if (o.has("judgeEase") && !o.isNull("judgeEase")) o.optInt("judgeEase") else null
                )
            }
            trim(out)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun trim(rows: List<StoredChat>, cap: Int = CAP): List<StoredChat> {
        if (rows.size <= cap) return rows
        return rows.takeLast(cap)
    }

    private fun write(dir: File, rows: List<StoredChat>) {
        dir.mkdirs()
        val target = file(dir)
        val part = File(dir, "$FILE_NAME.part")
        try {
            part.writeText(encode(rows))
            if (target.exists() && !target.delete()) {
                target.writeText(encode(rows))
                part.delete()
                return
            }
            if (!part.renameTo(target)) {
                target.writeText(part.readText())
                part.delete()
            }
        } catch (_: Exception) {
            runCatching { part.delete() }
        }
    }
}
