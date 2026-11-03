package com.evan.voicesrs

/** h120: speech language for Whisper (server code) and Google (locale tag). AUTO sends no language. */
enum class AsrLanguage(val code: String?, val googleTag: String?) {
    AUTO(null, null),
    EN("en", "en-US"),
    JA("ja", "ja-JP");

    companion object {
        /** Stored as [name]; anything unknown or missing is English. */
        fun parse(raw: String?): AsrLanguage = values().firstOrNull { it.name == raw } ?: EN

        /** h120b: dropdown pick -> language; labels are in enum order (AUTO, EN, JA). Unknown = EN. */
        fun fromLabel(label: String, labels: List<String>): AsrLanguage = values().getOrNull(labels.indexOf(label)) ?: EN
    }
}
