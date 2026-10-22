package com.evan.voicesrs

import android.content.res.ColorStateList
import com.google.android.material.chip.Chip

/** Outline on the suggested rating. A real grade still has to be tapped or spoken. */
object RatingSuggest {
    fun apply(chip: Chip?, ease: Int, selected: Int?, suggested: Int?) {
        if (chip == null) return
        chip.isChecked = selected == ease
        val hint = selected == null && suggested == ease
        val density = chip.resources.displayMetrics.density
        chip.chipStrokeWidth = if (hint) 1.5f * density else 0f
        if (hint) {
            chip.chipStrokeColor = ColorStateList.valueOf(chip.context.getColor(R.color.chat_accent))
        }
    }
}
