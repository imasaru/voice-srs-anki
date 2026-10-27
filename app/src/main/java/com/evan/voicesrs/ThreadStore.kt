package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One persisted follow-up message. Roles are "user" and "assistant". */
data class ThreadMessage(val role: String, val text: String, val at: Long)

/** Thread for one card version. Keyed by note (or card) plus content hash. */
data class StoredThread(
    val cardId: Long,
    val noteId: Long?,
    val contentHash: String,
    val messages: List<ThreadMessage>,
    /** Corrected grade, when a past answer was changed. Null until then. */
    val ease: Int? = null,
    val transcript: String? = null
)

/**
 * Card-history threads as JSON under filesDir. Pure encode/decode so unit tests
 * do not need Android. Writes go to a temp file and then rename.
 */
object ThreadStore {
    const val FILE_NAME = "thread-history.json"
    private val lock = Any()

    fun file(dir: File): File = File(dir, FILE_NAME)

    fun load(dir: File): List<StoredThread> = synchronized(lock) {
        val f = file(dir)
        if (!f.isFile) return emptyList()
        val raw = try {
            f.readText()
        } catch (_: Exception) {
            return emptyList()
        }
        return decode(raw)
    }

    fun save(dir: File, threads: List<StoredThread>) = synchronized(lock) {
        dir.mkdirs()
        val target = file(dir)
        val part = File(dir, "$FILE_NAME.part")
        part.writeText(encode(threads))
        if (target.exists() && !target.delete()) {
            target.writeText(encode(threads))
            part.delete()
            return
        }
        if (!part.renameTo(target)) {
            target.writeText(part.readText())
            part.delete()
        }
    }

    fun upsert(dir: File, thread: StoredThread) {
        val all = load(dir).toMutableList()
        val index = all.indexOfFirst { sameCard(it, thread.cardId, thread.noteId, thread.contentHash) }
        if (index >= 0) all[index] = thread else all += thread
        save(dir, all)
    }

    fun find(threads: List<StoredThread>, cardId: Long, noteId: Long?, hash: String): StoredThread? {
        return threads.firstOrNull { sameCard(it, cardId, noteId, hash) }
    }

    fun sameCard(thread: StoredThread, cardId: Long, noteId: Long?, hash: String): Boolean {
        if (thread.contentHash != hash) return false
        if (noteId != null && thread.noteId != null) return thread.noteId == noteId
        return thread.cardId == cardId
    }

    fun toFollowUps(messages: List<ThreadMessage>): List<FollowUp> {
        val out = ArrayList<FollowUp>()
        var i = 0
        while (i < messages.size) {
            val message = messages[i]
            if (message.role != "user") {
                i++
                continue
            }
            val next = messages.getOrNull(i + 1)
            if (next != null && next.role == "assistant" && next.text.isNotBlank()) {
                out += FollowUp(
                    message.text,
                    ExplState.Ready(next.text, cached = true),
                    at = message.at,
                    replyAt = next.at
                )
                i += 2
            } else {
                out += FollowUp(message.text, ExplState.Pending, at = message.at)
                i += 1
            }
        }
        return out
    }

    fun messagesOf(followUps: List<FollowUp>): List<ThreadMessage> {
        val out = ArrayList<ThreadMessage>()
        for (follow in followUps) {
            if (follow.q.isBlank()) continue
            out += ThreadMessage("user", follow.q, follow.at)
            val replyAt = if (follow.replyAt > 0L) follow.replyAt else follow.at
            when (val state = follow.state) {
                is ExplState.Ready -> if (state.text.isNotBlank()) {
                    out += ThreadMessage("assistant", state.text, replyAt)
                }
                is ExplState.Error -> if (state.message.isNotBlank()) {
                    out += ThreadMessage("assistant", state.message, replyAt)
                }
                else -> Unit
            }
        }
        return out
    }

    fun encode(threads: List<StoredThread>): String {
        val array = JSONArray()
        for (thread in threads) {
            val messages = JSONArray()
            for (message in thread.messages) {
                messages.put(
                    JSONObject()
                        .put("role", message.role)
                        .put("text", message.text)
                        .put("at", message.at)
                )
            }
            val obj = JSONObject()
                .put("cardId", thread.cardId)
                .put("contentHash", thread.contentHash)
                .put("messages", messages)
            if (thread.noteId != null) obj.put("noteId", thread.noteId)
            if (thread.ease != null) obj.put("ease", thread.ease)
            if (!thread.transcript.isNullOrBlank()) obj.put("transcript", thread.transcript)
            array.put(obj)
        }
        return JSONObject().put("threads", array).toString()
    }

    fun decode(raw: String): List<StoredThread> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONObject(raw).optJSONArray("threads") ?: return emptyList()
            val out = ArrayList<StoredThread>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val messagesJson = obj.optJSONArray("messages") ?: JSONArray()
                val messages = ArrayList<ThreadMessage>(messagesJson.length())
                for (j in 0 until messagesJson.length()) {
                    val message = messagesJson.optJSONObject(j) ?: continue
                    val role = message.optString("role")
                    val text = message.optString("text")
                    if (role != "user" && role != "assistant") continue
                    if (text.isBlank()) continue
                    messages += ThreadMessage(role, text, message.optLong("at"))
                }
                out += StoredThread(
                    cardId = obj.optLong("cardId"),
                    noteId = if (obj.has("noteId") && !obj.isNull("noteId")) obj.optLong("noteId") else null,
                    contentHash = obj.optString("contentHash"),
                    messages = messages,
                    ease = if (obj.has("ease") && !obj.isNull("ease")) obj.optInt("ease") else null,
                    transcript = obj.optString("transcript").ifBlank { null }
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** Slack-style link under an AI bubble. */
object ThreadLabel {
    /**
     * Slack-style link text. When the last reply falls on the same calendar day
     * as `now` (in `zone`) only the clock is shown; an earlier day in the same
     * year shows "MMM d HH:mm"; anything older shows "MMM d, yyyy HH:mm".
     */
    fun text(
        count: Int,
        lastAt: Long?,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis()
    ): String {
        if (count <= 0 || lastAt == null || lastAt <= 0L) return "💬 Reply in thread"
        val nowDay = java.time.LocalDate.ofInstant(
            java.time.Instant.ofEpochMilli(now), zone
        )
        val lastDay = java.time.LocalDate.ofInstant(
            java.time.Instant.ofEpochMilli(lastAt), zone
        )
        val pattern = when {
            nowDay == lastDay -> "HH:mm"
            nowDay.year == lastDay.year -> "MMM d HH:mm"
            else -> "MMM d, yyyy HH:mm"
        }
        val clock = java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.ENGLISH).withZone(zone)
        val hhmm = clock.format(java.time.Instant.ofEpochMilli(lastAt))
        val noun = if (count == 1) "reply" else "replies"
        return "💬 $count $noun · last $hhmm"
    }

    fun count(followUps: List<FollowUp>): Int {
        return followUps.count { it.state is ExplState.Ready || it.state is ExplState.Error || it.q.isNotBlank() }
    }

    /** Latest question or reply clock, for the "last …" link (date shown when not today). */
    fun lastAt(followUps: List<FollowUp>): Long? {
        var best = 0L
        for (follow in followUps) {
            if (follow.at > best) best = follow.at
            if (follow.replyAt > best) best = follow.replyAt
        }
        return best.takeIf { it > 0L }
    }
}
