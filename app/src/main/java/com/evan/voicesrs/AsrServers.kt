package com.evan.voicesrs

/** h119: speech server URLs the user saved (nothing built in). Pure, no Android types. */
object AsrServers {
    const val MAX = 8

    /** Newest first, no blanks, no duplicates (a trailing slash does not count), at most [MAX]. */
    fun remember(saved: List<String>, url: String): List<String> {
        val u = url.trim()
        if (u.isEmpty()) return saved
        val key = u.trimEnd('/')
        return (listOf(u) + saved.filter { it.trim().trimEnd('/') != key }).take(MAX)
    }

    fun encode(list: List<String>): String = list.joinToString("\n")

    fun decode(raw: String?): List<String> =
        raw.orEmpty().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX)
}
