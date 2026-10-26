package com.evan.voicesrs

/**
 * Status / info card. The Heard line shows only while the mic is actually
 * open (or right after a capture while listening / reporting what was heard),
 * then the current pipeline stage. It is hidden while TTS reads, while paused,
 * and in silent mode.
 */
object InfoCard {
    const val HEARD_EMPTY = "Heard …"
    const val LISTEN_ANSWER = "Listening — say 'Answer B'"

    fun heardLine(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return HEARD_EMPTY
        var body = trimmed
        if (body.startsWith("I heard:", ignoreCase = true)) {
            body = body.substringAfter(':').trim()
        } else if (body.startsWith("I heard", ignoreCase = true)) {
            body = body.substring(7).trim()
        } else if (body.startsWith("Heard:", ignoreCase = true)) {
            body = body.substringAfter(':').trim()
        }
        body = body.trim().trim('"').trim()
        if (body.isEmpty()) return HEARD_EMPTY
        return "Heard: \"$body\""
    }

    fun stage(status: String): String {
        val s = status.trim()
        if (s.isEmpty()) return ""
        if (s.startsWith("Heard", ignoreCase = true) || s.startsWith("I heard", ignoreCase = true)) {
            return ""
        }
        if (s.contains("Listening for your answer", ignoreCase = true)) return LISTEN_ANSWER
        return s
    }

    fun compose(heard: String?, status: String, drilling: Boolean, silent: Boolean = false, micOpen: Boolean = false): String {
        if (!drilling) return status.trim().ifBlank { "Ready" }
        val showHeard = !silent && (
            micOpen ||
            status.contains("Listening", ignoreCase = true) ||
            status.startsWith("Heard", ignoreCase = true) ||
            status.startsWith("I heard", ignoreCase = true)
        )
        val lines = ArrayList<String>()
        if (showHeard) lines += heardLine(heard)
        val stage = stage(status)
        if (stage.isNotBlank()) lines += stage
        return lines.joinToString("\n")
    }
}
