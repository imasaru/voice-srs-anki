package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject

/** Data sent with a one-tap prompt chip. Always attached as fenced data, never as instructions. */
enum class ChipSource { TODAY_REVIEWS, CURRENT_CARD, CARD_CHATS, EXAM_READINESS }

data class PromptChip(
    val id: String,
    val name: String,
    val prompt: String,
    val source: ChipSource,
    val builtIn: Boolean = false
)

object PromptChips {
    const val REPORT_ID = "today_report"
    const val REPORT_NAME = "Today's report"
    const val REPORT_PROMPT = "Write today's study report from the study data."

    /** Built-in default list: only the Today's report chip. */
    fun defaults(): List<PromptChip> =
        listOf(PromptChip(REPORT_ID, REPORT_NAME, REPORT_PROMPT, ChipSource.TODAY_REVIEWS, builtIn = true))

    /** "Reset to default": the chip with id REPORT_ID gets the default name, prompt, source and builtIn=true,
     *  keeping its position. Other chips unchanged. If no REPORT_ID chip exists, the default is inserted at index 0. */
    fun resetBuiltIn(chips: List<PromptChip>): List<PromptChip> {
        val def = defaults()[0]
        if (chips.none { it.id == REPORT_ID }) return listOf(def) + chips
        return chips.map { if (it.id == REPORT_ID) def else it }
    }

    /** JSON array of {"id","name","prompt","source"(enum name),"builtIn"}; order kept. */
    fun toJson(chips: List<PromptChip>): String {
        val arr = JSONArray()
        for (c in chips) arr.put(JSONObject()
            .put("id", c.id).put("name", c.name).put("prompt", c.prompt)
            .put("source", c.source.name).put("builtIn", c.builtIn))
        return arr.toString()
    }

    /** Blank or unparseable text -> defaults(). "[]" -> empty list (the user deleted everything).
     *  An entry with a missing/blank id or name, or an unknown source, is skipped; the rest are kept in order. */
    fun fromJson(text: String): List<PromptChip> {
        if (text.isBlank()) return defaults()
        val arr = try { JSONArray(text) } catch (_: Exception) { return defaults() }
        val out = ArrayList<PromptChip>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            val name = o.optString("name", "")
            if (id.isBlank() || name.isBlank()) continue
            val prompt = o.optString("prompt", "")
            val source = ChipSource.values().firstOrNull { it.name == o.optString("source") } ?: continue
            val builtIn = o.optBoolean("builtIn", false)
            out.add(PromptChip(id, name, prompt, source, builtIn))
        }
        return out
    }
}
