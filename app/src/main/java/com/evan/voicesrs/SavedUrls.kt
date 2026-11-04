package com.evan.voicesrs

/**
 * Saved URLs for any Settings URL field; nothing built in.
 *
 * Pure, no Android types. A saved list is newest-first, holds at most [MAX] entries, and never
 * stores blanks or duplicates (a trailing slash does not distinguish two entries). Settings pages
 * wire one of these lists to a dropdown + text field via `bindSavedUrlField`.
 */
object SavedUrls {
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

    /** Dropdown rows = saved URLs, then the [addLabel] row last. */
    fun menuItems(saved: List<String>, addLabel: String): List<String> = saved + addLabel

    /** True when [position] is the [addLabel] row of [menuItems]. */
    fun isAddRow(position: Int, saved: List<String>): Boolean = position == saved.size

    /** A blank field right after "+ Add …" means never mind: keep the previous URL, save nothing. */
    fun keepPrevious(raw: String, addingNew: Boolean): Boolean = addingNew && raw.isBlank()
}
