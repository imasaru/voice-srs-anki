package com.evan.voicesrs

/** h89: redact secrets in a diagnostic log line. Idempotent. Pure. */
object LogMask {
    // OpenAI-style sk- key: sk- followed by 16+ URL-safe chars.
    private val SK = Regex("sk-[A-Za-z0-9_-]{16,}")
    // scheme://user:pass@host  (host/path kept)
    private val URLINFO = Regex("""(?i)([a-z][a-z0-9+.-]*://)([^:/@\s]+):([^@\s]+)@""")
    // key= / key: / api_key= / api-key= / access_token= / token= / password=
    private val KV = Regex("""(?i)\b(key|token|api[_-]?key|access_token|password)([=:])[^&\s]+""")
    // Bearer <token>  (keep the word, drop the run of non-space)
    private val BEARER = Regex("""(?i)(bearer\s+)\S+""")
    // Long standalone hex token: 32+ hex chars, delimited by non-hex.
    private val HEX = Regex("""(?<![0-9a-fA-F])[0-9a-fA-F]{32,}(?![0-9a-fA-F])""")

    fun mask(s: String): String {
        var r = SK.replace(s, "***")
        r = URLINFO.replace(r) { "${it.groupValues[1]}***:***@" }
        r = KV.replace(r) { "${it.groupValues[1]}${it.groupValues[2]}***" }
        r = BEARER.replace(r) { "${it.groupValues[1]}***" }
        r = HEX.replace(r, "***")
        return r
    }
}
