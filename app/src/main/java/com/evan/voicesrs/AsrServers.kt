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

    /** h119b: dropdown rows = saved servers, then the "+ Add server…" row last. */
    fun menuItems(saved: List<String>, addLabel: String): List<String> = saved + addLabel

    /** True when [position] is the "+ Add server…" row of [menuItems]. */
    fun isAddRow(position: Int, saved: List<String>): Boolean = position == saved.size

    /** h119c: a blank field right after "+ Add server…" means never mind: keep the previous URL, save nothing. */
    fun keepPrevious(raw: String, addingNew: Boolean): Boolean = addingNew && raw.isBlank()
}
