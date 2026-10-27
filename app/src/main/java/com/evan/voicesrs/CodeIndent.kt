package com.evan.voicesrs

/**
 * Normalizes the leading whitespace of a fenced code line so it survives Android's
 * text layout: leading tabs become fixed-width and leading spaces become
 * non-breaking spaces ('\u00A0'), which keeps them from collapsing. Inner spaces
 * are left untouched so alignment within a line is preserved.
 */
object CodeIndent {
    private const val NBSP = '\u00A0'
    private const val TAB_WIDTH = 4

    /** Turns each leading tab into four spaces, then every leading space into a
     *  non-breaking space. Inner spaces are unchanged. */
    fun expand(line: String): String {
        var end = 0
        while (end < line.length && (line[end] == ' ' || line[end] == '\t')) end++
        val prefix = line.substring(0, end).replace("\t", " ".repeat(TAB_WIDTH)).replace(' ', NBSP)
        return prefix + line.substring(end)
    }

    /** Number of leading spaces after expanding each leading tab to [TAB_WIDTH]. */
    fun depth(line: String): Int {
        var spaces = 0
        for (c in line) {
            when (c) {
                '\t' -> spaces += TAB_WIDTH
                ' ' -> spaces++
                else -> break
            }
        }
        return spaces
    }
}
