package com.evan.voicesrs

/**
 * "Highlight sentence while reading" setting. [SETTING_DEFAULT] is the single
 * source of truth for the default, mirrored in [AnkiConfig], Prefs, and the
 * settings UI. [PlaybackControl.highlight] applies it to the section being read.
 */
object HighlightSentence {
    const val SETTING_DEFAULT = true
}
