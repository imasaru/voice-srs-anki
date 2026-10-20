package com.evan.voicesrs

/**
 * Debug builds install a scripted listener here. Release leaves it null, so
 * [com.evan.voicesrs.DrillService] keeps using SpeechRecognizer.
 */
interface ScriptedListener {
    fun enabled(): Boolean
    /** Blocks up to [timeoutMs] for the next scripted utterance. */
    fun poll(timeoutMs: Long): String?
}

object SpeechHooks {
    @Volatile
    var scripted: ScriptedListener? = null
}
