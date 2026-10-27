package com.evan.voicesrs

/**
 * Pure text formatter for the slim top-bar subtitle (approved declutter mockup
 * v2). No UI in this round: layouts (53B) and keyboard-collapse (53C) come
 * later and read these strings unchanged.
 */
object SlimBarText {

    private const val SEPARATOR = " · "

    fun elapsed(elapsedMs: Long): String {
        val ms = if (elapsedMs < 0) 0 else elapsedMs
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "Elapsed ${hours}:${String.format("%02d", minutes)}:${String.format("%02d", seconds)}"
        } else {
            "Elapsed ${minutes}:${String.format("%02d", seconds)}"
        }
    }

    fun block(blockMin: Int, goalMin: Int): String {
        val clamped = if (blockMin < 0) 0 else blockMin
        return if (goalMin > 0) {
            "$clamped/$goalMin min"
        } else {
            "$clamped min"
        }
    }

    fun idle(dueCount: Int, blockMin: Int, goalMin: Int): String {
        val due = if (dueCount < 0) 0 else dueCount
        return "$due due$SEPARATOR${block(blockMin, goalMin)}"
    }

    fun drilling(status: String, elapsedMs: Long, blockMin: Int, goalMin: Int): String {
        val label = status.trim().ifBlank { "Drilling" }
        return "$label$SEPARATOR${elapsed(elapsedMs)}$SEPARATOR${block(blockMin, goalMin)}"
    }

    fun paused(elapsedMs: Long, blockMin: Int, goalMin: Int): String {
        return "Paused$SEPARATOR${elapsed(elapsedMs)}$SEPARATOR${block(blockMin, goalMin)}"
    }

    fun keyboardLine(deck: String, blockMin: Int, goalMin: Int): String {
        val trimmed = deck.trim()
        return if (goalMin > 0) {
            "$trimmed · $blockMin/$goalMin"
        } else {
            "$trimmed · $blockMin min"
        }
    }
}
