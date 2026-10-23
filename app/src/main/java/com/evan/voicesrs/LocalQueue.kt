package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class OfflineCard(
    val cardId: Long,
    val front: String,
    val back: String,
    val buttons: List<Int> = listOf(1, 2, 3, 4),
    /** Null on batches written before note ids were stored. */
    val noteId: Long? = null,
    /** Note Correct field. Blank when missing or on batches written before it was stored. */
    val correct: String = "",
    val memoFront: String = "",
    val memoBack: String = "",
    val memoAvailable: Boolean = false,
    val memoHint: Boolean = false
)

data class PendingGrade(
    val cardId: Long,
    val ease: Int,
    val queuedAtMs: Long
)

/** Retry delays for a failed answerCards sync. Attempt 0 waits 2s, then doubles, capped at 60s. */
object SyncBackoff {
    const val BASE_MS = 2_000L
    const val CAP_MS = 60_000L

    fun delayMs(attempt: Int): Long {
        val shift = attempt.coerceIn(0, 5)
        var value = BASE_MS
        repeat(shift) {
            if (value >= CAP_MS / 2) return CAP_MS
            value *= 2
        }
        return value.coerceAtMost(CAP_MS)
    }
}

object PendingSync {
    /** Grades whose answerCards result was not true stay queued. */
    fun stillPending(grades: List<PendingGrade>, ok: List<Boolean>): List<PendingGrade> {
        return grades.filterIndexed { index, _ -> ok.getOrNull(index) != true }
    }
}

object LocalQueueJson {
    fun encodeGrades(grades: List<PendingGrade>): String {
        val arr = JSONArray()
        for (grade in grades) {
            arr.put(JSONObject().apply {
                put("cardId", grade.cardId)
                put("ease", grade.ease)
                put("queuedAtMs", grade.queuedAtMs)
            })
        }
        return JSONObject().put("grades", arr).toString()
    }

    fun decodeGrades(json: String): List<PendingGrade> {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            return emptyList()
        }
        val arr = root.optJSONArray("grades") ?: return emptyList()
        val out = ArrayList<PendingGrade>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("cardId", 0L)
            val ease = o.optInt("ease", 0)
            if (id == 0L || ease !in 1..4) continue
            out += PendingGrade(id, ease, o.optLong("queuedAtMs", 0L))
        }
        return out
    }

    fun encodeBatch(cards: List<OfflineCard>): String {
        val arr = JSONArray()
        for (card in cards) {
            val buttons = JSONArray()
            for (button in card.buttons) buttons.put(button)
            arr.put(JSONObject().apply {
                put("cardId", card.cardId)
                put("front", card.front)
                put("back", card.back)
                put("buttons", buttons)
                if (card.noteId != null) put("noteId", card.noteId)
                if (card.correct.isNotBlank()) put("correct", card.correct)
                if (card.memoFront.isNotBlank()) put("memoFront", card.memoFront)
                if (card.memoBack.isNotBlank()) put("memoBack", card.memoBack)
                put("memoAvailable", card.memoAvailable)
                put("memoHint", card.memoHint)
            })
        }
        return JSONObject().put("cards", arr).toString()
    }

    fun decodeBatch(json: String): List<OfflineCard> {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            return emptyList()
        }
        val arr = root.optJSONArray("cards") ?: return emptyList()
        val out = ArrayList<OfflineCard>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("cardId", 0L)
            if (id == 0L) continue
            val buttons = o.optJSONArray("buttons")
            val parsed = if (buttons == null) {
                listOf(1, 2, 3, 4)
            } else {
                val list = ArrayList<Int>(buttons.length())
                for (b in 0 until buttons.length()) {
                    val n = buttons.optInt(b, 0)
                    if (n in 1..4) list += n
                }
                list.ifEmpty { listOf(1, 2, 3, 4) }
            }
            val noteId = if (o.has("noteId") && !o.isNull("noteId")) {
                val note = o.optLong("noteId", 0L)
                if (note == 0L) null else note
            } else {
                null
            }
            out += OfflineCard(
                id,
                o.optString("front", ""),
                o.optString("back", ""),
                parsed,
                noteId,
                o.optString("correct", ""),
                o.optString("memoFront", ""),
                o.optString("memoBack", ""),
                o.optBoolean("memoAvailable", false),
                o.optBoolean("memoHint", false)
            )
        }
        return out
    }

    fun encodeIds(ids: List<Long>): String {
        val arr = JSONArray()
        for (id in ids) arr.put(id)
        return JSONObject().put("ids", arr).toString()
    }

    fun decodeIds(json: String): List<Long> {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            return emptyList()
        }
        val arr = root.optJSONArray("ids") ?: return emptyList()
        val out = ArrayList<Long>(arr.length())
        for (i in 0 until arr.length()) {
            val id = arr.optLong(i, 0L)
            if (id != 0L) out += id
        }
        return out
    }
}

/** filesDir-backed batch, grade queue, and skipped-card ids. No secrets. */
class LocalQueueStore(private val dir: File) {
    private val gradesFile get() = File(dir, "pending-grades.json")
    private val batchFile get() = File(dir, "offline-batch.json")
    private val skippedFile get() = File(dir, "skipped-ids.json")

    fun readGrades(): List<PendingGrade> = read(gradesFile, LocalQueueJson::decodeGrades)

    fun writeGrades(grades: List<PendingGrade>) {
        write(gradesFile, LocalQueueJson.encodeGrades(grades))
    }

    fun enqueueGrade(grade: PendingGrade) {
        writeGrades(readGrades() + grade)
    }

    fun readBatch(): List<OfflineCard> = read(batchFile, LocalQueueJson::decodeBatch)

    fun writeBatch(cards: List<OfflineCard>) {
        write(batchFile, LocalQueueJson.encodeBatch(cards))
    }

    fun hasBatch(): Boolean = readBatch().isNotEmpty()

    fun readSkipped(): List<Long> = read(skippedFile, LocalQueueJson::decodeIds)

    fun writeSkipped(ids: List<Long>) {
        if (ids.isEmpty()) {
            if (skippedFile.exists()) skippedFile.delete()
            return
        }
        write(skippedFile, LocalQueueJson.encodeIds(ids))
    }

    private fun <T> read(file: File, decode: (String) -> T): T {
        val empty = decode("")
        if (!file.isFile) return empty
        return try {
            decode(file.readText())
        } catch (_: Exception) {
            empty
        }
    }

    private fun write(file: File, json: String) {
        dir.mkdirs()
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.writeText(json)
            tmp.delete()
        }
    }
}
