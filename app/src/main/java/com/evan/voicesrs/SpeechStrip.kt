package com.evan.voicesrs

/** h138: text going to TTS loses emoji, arrows, geometric/dingbat symbols and the words "play button". */
object SpeechStrip {
    private val symbols = Regex("[\\x{2190}-\\x{2BFF}\\x{1F000}-\\x{1FAFF}\\x{FE0F}\\x{200D}\\x{24D8}\\x{FF0B}]")
    private val playWords = Regex("(?i)\\bplay button\\b")
    fun clean(text: String): String = text.lines().map { playWords.replace(symbols.replace(it, " "), " ").replace(Regex("[ \\t]+"), " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}
