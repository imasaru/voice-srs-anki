package com.evan.voicesrs

/** Read-only "Voice commands" list in Settings > Listening. VoiceCommandHelpTest keeps it truthful. */
object VoiceCommandHelp {
    data class Row(val label: String, val phrases: List<String>)

    val ROWS = listOf(
        Row("Answer B", listOf("answer B", "option B")),
        Row("Repeat", listOf("repeat", "say it again", "one more time")),
        Row("Pause", listOf("pause", "hold on")),
        Row("Stop", listOf("stop", "stop drill")),
        Row("Skip", listOf("skip", "skip card")),
        Row("Show answer", listOf("show answer")),
    )
}
