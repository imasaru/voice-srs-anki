package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Bookmark(
    val id: Long,
    val at: Long,
    val turnId: Long,
    val cardId: Long,
    val label: String,
    val quote: String
)

/**
 * Chat bookmarks as pretty JSON plus a markdown twin for the model.
 * Both files live in app storage.
 */
object BookmarkStore {
    const val FILE_NAME = "bookmarks.json"
    const val MARKDOWN_NAME = "bookmarks.md"
    private val lock = Any()
    @Volatile private var cachedTurnIds: Pair<String, Set<Long>>? = null

    fun file(dir: File): File = File(dir, FILE_NAME)

    fun load(dir: File): List<Bookmark> = synchronized(lock) {
        val f = file(dir)
        if (!f.isFile) return emptyList()
        val raw = try {
            f.readText()
        } catch (_: Exception) {
            return emptyList()
        }
        return decode(raw)
    }

    fun save(dir: File, marks: List<Bookmark>) = synchronized(lock) {
        cachedTurnIds = null
        dir.mkdirs()
        writeAtomic(File(dir, FILE_NAME), encode(marks))
        writeAtomic(File(dir, MARKDOWN_NAME), toMarkdown(marks))
    }

    fun add(dir: File, bookmark: Bookmark): List<Bookmark> {
        val all = load(dir).toMutableList()
        if (bookmark.turnId != 0L && all.any { it.turnId == bookmark.turnId }) return all
        val id = if (bookmark.id > 0L) bookmark.id else nextId(all)
        all += bookmark.copy(id = id)
        save(dir, all)
        return all
    }

    /** h121e: a second tap removes. Returns true when the turn is bookmarked after the call. */
    fun toggle(dir: File, bookmark: Bookmark): Boolean {
        val all = load(dir)
        if (bookmark.turnId != 0L && all.any { it.turnId == bookmark.turnId }) {
            save(dir, all.filter { it.turnId != bookmark.turnId })
            return false
        }
        add(dir, bookmark)
        return true
    }

    /** h136: true when a live (turnId 0) bookmark already exists for this card+quote. */
    fun isLiveMarked(dir: File, cardId: Long, quote: String): Boolean =
        load(dir).any { it.turnId == 0L && it.cardId == cardId && it.quote == quote.trim() }

    /** h136: add/remove the live bookmark for this card+quote; returns true when now marked. */
    fun toggleLive(dir: File, cardId: Long, quote: String, at: Long): Boolean {
        val q = quote.trim()
        if (isLiveMarked(dir, cardId, q)) {
            save(dir, load(dir).filterNot { it.turnId == 0L && it.cardId == cardId && it.quote == q })
            return false
        }
        add(dir, Bookmark(id = 0L, at = at, turnId = 0L, cardId = cardId, label = "", quote = q))
        return true
    }

    /** h121e: bookmarked turn ids, cached until the next save (every row bind asks). */
    fun turnIds(dir: File): Set<Long> {
        val key = dir.absolutePath
        cachedTurnIds?.let { if (it.first == key) return it.second }
        val ids = load(dir).map { it.turnId }.filter { it != 0L }.toSet()
        cachedTurnIds = key to ids
        return ids
    }

    fun remove(dir: File, id: Long): List<Bookmark> {
        val all = load(dir).filter { it.id != id }
        save(dir, all)
        return all
    }

    fun nextId(marks: List<Bookmark>): Long = (marks.maxOfOrNull { it.id } ?: 0L) + 1L

    fun encode(marks: List<Bookmark>): String {
        val arr = JSONArray()
        for (mark in marks) {
            arr.put(JSONObject().apply {
                put("id", mark.id)
                put("at", mark.at)
                put("turnId", mark.turnId)
                put("cardId", mark.cardId)
                put("label", mark.label)
                put("quote", mark.quote)
            })
        }
        return JSONObject().put("bookmarks", arr).toString(2)
    }

    fun decode(json: String): List<Bookmark> {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            return emptyList()
        }
        val arr = root.optJSONArray("bookmarks") ?: return emptyList()
        val out = ArrayList<Bookmark>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("id", 0L)
            val quote = o.optString("quote", "")
            if (id == 0L || quote.isBlank()) continue
            out += Bookmark(
                id = id,
                at = o.optLong("at", 0L),
                turnId = o.optLong("turnId", 0L),
                cardId = o.optLong("cardId", 0L),
                label = o.optString("label", ""),
                quote = quote
            )
        }
        return out
    }

    fun toMarkdown(marks: List<Bookmark>): String {
        if (marks.isEmpty()) return "# Voice SRS bookmarks\n\n(none)\n"
        val sb = StringBuilder("# Voice SRS bookmarks\n")
        for (mark in marks) {
            sb.append("\n## ").append(mark.id).append(". ").append(mark.label.ifBlank { "Bookmark" })
            sb.append('\n')
            if (mark.cardId != 0L) sb.append("card: ").append(mark.cardId).append('\n')
            sb.append('\n').append(mark.quote.trim()).append("\n")
        }
        return sb.toString()
    }

    private fun writeAtomic(target: File, text: String) {
        val part = File(target.parentFile, target.name + ".part")
        part.writeText(text)
        if (target.exists() && !target.delete()) {
            target.writeText(text)
            part.delete()
            return
        }
        if (!part.renameTo(target)) {
            target.writeText(part.readText())
            part.delete()
        }
    }
}
