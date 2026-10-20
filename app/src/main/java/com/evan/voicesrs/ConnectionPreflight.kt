package com.evan.voicesrs

data class PreflightReport(val ok: Boolean, val message: String)

/**
 * A drill may start only when Anki answers `version` and the chosen deck is in `deckNames`.
 */
object ConnectionPreflight {
    fun evaluate(
        version: Int?,
        deckNames: List<String>?,
        deck: String,
        error: String?
    ): PreflightReport {
        if (version == null || deckNames == null) {
            val why = error?.trim().orEmpty().ifBlank { "No response from Anki." }
            return PreflightReport(
                false,
                "Can't reach Anki. $why Tap Retry when Anki is open."
            )
        }
        if (deckNames.none { it == deck }) {
            return PreflightReport(
                false,
                "Deck \"$deck\" is not in Anki. Pick another deck in Settings, then tap Retry."
            )
        }
        return PreflightReport(true, "Anki $version")
    }
}
