package com.evan.voicesrs

/** One log line per AI request. Pure. Never sees the API key or headers. */
object AiDiag {
    const val NONE = "none"
    const val MSG_MAX = 160

    /** Mutable timing probe for one request. Times are wall-clock ms (System.currentTimeMillis()). */
    class Probe(val startedMs: Long, val streaming: Boolean) {
        var firstByteMs: Long? = null
        var firstTokenMs: Long? = null
        fun byteAt(now: Long) { if (firstByteMs == null) firstByteMs = now }
        fun tokenAt(now: Long) { if (firstTokenMs == null) firstTokenMs = now }
    }

    /** Host only: no scheme, user info, port, path or query. "?" if unparsable. */
    fun host(baseUrl: String): String =
        runCatching { java.net.URI(baseUrl.trim()).host }.getOrNull()?.ifBlank { null } ?: "?"

    /** "Class: message", message one line, Bearer/key values redacted, clipped to MSG_MAX. */
    fun error(e: Throwable): String {
        val oneLine = (e.message ?: "").replace('\r', ' ').replace('\n', ' ')
        val redacted =
            Regex("(?i)(bearer\\s+|api[_-]?key[=:]\\s*|key=)\\S+").replace(oneLine) { "$1***" }
        val clipped = redacted.take(MSG_MAX)
        return if (clipped.isEmpty()) e.javaClass.simpleName else "${e.javaClass.simpleName}: $clipped"
    }

    fun success(p: Probe, nowMs: Long, baseUrl: String, model: String, chars: Int): String =
        "ai ok totalMs=${nowMs - p.startedMs} firstByteMs=${rel(p, p.firstByteMs)} firstTokenMs=${rel(p, p.firstTokenMs)} stream=${p.streaming} host=${host(baseUrl)} model=$model chars=$chars"

    fun failure(e: Throwable, p: Probe, nowMs: Long, baseUrl: String, model: String, chars: Int): String =
        "ai fail err=${error(e)} elapsedMs=${nowMs - p.startedMs} firstByteMs=${rel(p, p.firstByteMs)} firstTokenMs=${rel(p, p.firstTokenMs)} stream=${p.streaming} host=${host(baseUrl)} model=$model chars=$chars"

    /** ms field relative to the probe's startedMs; null -> NONE. */
    private fun rel(p: Probe, t: Long?): String = t?.let { (it - p.startedMs).toString() } ?: NONE
}
