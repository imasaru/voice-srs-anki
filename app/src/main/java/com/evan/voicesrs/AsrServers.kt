package com.evan.voicesrs

/** h119: speech server URLs the user saved (nothing built in). Delegates to [SavedUrls]. */
object AsrServers {
    const val MAX = SavedUrls.MAX

    /** Newest first, no blanks, no duplicates (a trailing slash does not count), at most [MAX]. */
    fun remember(saved: List<String>, url: String): List<String> = SavedUrls.remember(saved, url)

    fun encode(list: List<String>): String = SavedUrls.encode(list)

    fun decode(raw: String?): List<String> = SavedUrls.decode(raw)

    /** h119b: dropdown rows = saved servers, then the "+ Add server…" row last. */
    fun menuItems(saved: List<String>, addLabel: String): List<String> = SavedUrls.menuItems(saved, addLabel)

    /** True when [position] is the "+ Add server…" row of [menuItems]. */
    fun isAddRow(position: Int, saved: List<String>): Boolean = SavedUrls.isAddRow(position, saved)

    /** h119c: a blank field right after "+ Add server…" means never mind: keep the previous URL, save nothing. */
    fun keepPrevious(raw: String, addingNew: Boolean): Boolean = SavedUrls.keepPrevious(raw, addingNew)
}
