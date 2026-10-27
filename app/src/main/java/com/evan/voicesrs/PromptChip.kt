package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject

/** Data sent with a one-tap prompt chip. Always attached as fenced data, never as instructions. */
enum class ChipSource { TODAY_REVIEWS, CURRENT_CARD, CARD_CHATS }

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

    // Step 2 adds toJson / fromJson here.
}
