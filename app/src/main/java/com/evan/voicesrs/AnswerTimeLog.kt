package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One locally timed review. [durationMs] 0 means the think clock never armed. */
data class AnswerTime(
    val cardId: Long,
    val deck: String,
    val ease: Int,
    val durationMs: Long,
    val atMs: Long
)

/**
 * App-local answer durations. Stock AnkiConnect starts the card timer right
 * before answering, so Anki's revlog.time is ~0 for app reviews; this log is
 * the source of answer time for the study tools. Capped JSON in filesDir.
 */
object AnswerTimeLog {
    const val FILE_NAME = "answer-times.json"
    const val CAP = 20_000
    private val lock = Any()

    @Volatile private var items: List<AnswerTime> = emptyList()
    @Volatile private var dir: File? = null

    fun file(dir: File): File = File(dir, FILE_NAME)

    fun snapshot(): List<AnswerTime> = synchronized(lock) { items }

    /** Loads once per directory; later calls are no-ops. */
    fun init(dir: File) = synchronized(lock) {
        if (this.dir == dir) return@synchronized
        this.dir = dir
        items = try {
            val f = file(dir)
            if (f.isFile) decode(f.readText()) else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun record(entry: AnswerTime) {
        if (entry.cardId == 0L) return
        val next: List<AnswerTime>
        synchronized(lock) {
            next = trim(items + entry)
            items = next
        }
        dir?.let { write(it, next) }
    }

    /** Test hook. */
    fun reset(rows: List<AnswerTime> = emptyList()) = synchronized(lock) {
        dir = null
        items = trim(rows)
    }

    fun trim(rows: List<AnswerTime>, cap: Int = CAP): List<AnswerTime> =
        if (rows.size <= cap) rows else rows.takeLast(cap)

    fun encode(rows: List<AnswerTime>): String {
        val arr = JSONArray()
        for (r in rows) {
            arr.put(
                JSONObject()
                    .put("cardId", r.cardId)
                    .put("deck", r.deck)
                    .put("ease", r.ease)
                    .put("durationMs", r.durationMs)
                    .put("at", r.atMs)
            )
        }
        return JSONObject().put("reviews", arr).toString()
    }

    fun decode(raw: String): List<AnswerTime> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("reviews") ?: return emptyList()
            val out = ArrayList<AnswerTime>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optLong("cardId", 0L)
                if (id == 0L) continue
                out += AnswerTime(
                    id,
                    o.optString("deck"),
                    o.optInt("ease", 0),
                    o.optLong("durationMs", 0L).coerceAtLeast(0L),
                    o.optLong("at", 0L)
                )
            }
            trim(out)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(dir: File, rows: List<AnswerTime>) {
        try {
            dir.mkdirs()
            val part = File(dir, "$FILE_NAME.part")
            part.writeText(encode(rows))
            val target = file(dir)
            if (!part.renameTo(target)) {
                target.writeText(part.readText())
                part.delete()
            }
        } catch (_: Exception) {
        }
    }
}
