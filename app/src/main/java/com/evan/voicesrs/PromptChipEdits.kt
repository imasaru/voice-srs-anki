package com.evan.voicesrs

/** Pure list edits for the One-tap prompts screen. No Android. */
object PromptChipEdits {
    /** Drag reorder: move the chip at [from] to index [to]. Either index out of range, or from == to -> list unchanged. */
    fun move(chips: List<PromptChip>, from: Int, to: Int): List<PromptChip> {
        if (from !in chips.indices || to !in chips.indices || from == to) return chips
        val out = chips.toMutableList()
        val c = out.removeAt(from)
        out.add(to, c)
        return out
    }

    /** Remove the chip with [id]. Unknown id -> list unchanged. */
    fun delete(chips: List<PromptChip>, id: String): List<PromptChip> = chips.filter { it.id != id }

    /** Editor Save: replace the chip with the same id in place, or append it at the end if the id is new. */
    fun upsert(chips: List<PromptChip>, chip: PromptChip): List<PromptChip> =
        if (chips.any { it.id == chip.id }) chips.map { if (it.id == chip.id) chip else it } else chips + chip

    /** Id for a new chip: "chip_N" with the smallest N >= 1 not already used. */
    fun newId(chips: List<PromptChip>): String {
        val used = chips.map { it.id }.toSet()
        var n = 1
        while ("chip_$n" in used) n++
        return "chip_$n"
    }

    /** Save is enabled only when both the name and the prompt are non-blank. */
    fun canSave(name: String, prompt: String): Boolean = name.isNotBlank() && prompt.isNotBlank()
}
