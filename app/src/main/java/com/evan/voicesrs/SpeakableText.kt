package com.evan.voicesrs

/**
 * h110: card HTML -> text a TTS engine can say. Pure, no Android.
 * Prose is kept word for word (same sentence ends as AnkiText.stripHtml, so SentenceSplit /
 * ReadingSpan alignment still finds the on-screen sentence). Only <code>/<pre> content and
 * identifier-like tokens are rewritten, and never with ". ", "!" or "?" so no sentence is added
 * or merged. On-screen text is untouched (display uses toDisplayHtml / fullQuestion).
 */
object SpeakableText {
    const val MAX_BLOCK_TOKENS = 25
    const val LONG_BLOCK = "code example shown on screen, first line:"
    private val pre = Regex("(?is)<pre\\b[^>]*>(.*?)</pre>")
    private val code = Regex("(?is)<code\\b[^>]*>(.*?)</code>")
    private val tag = Regex("<[^>]+>")
    private val lineBreak = Regex("(?i)<br\\s*/?>|</p>|</div>|</li>|</h[1-6]>")
    private val ws = Regex("[ \\t\\x0B\\f\\r]+")
    private val ident = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val proseIdent = Regex("(?<![\\w.])_*[A-Za-z][A-Za-z0-9]*(?:_+[A-Za-z0-9]+)+_*(?![\\w])|(?<![\\w])_[A-Za-z][A-Za-z0-9]*")
    private val codeToken = Regex("[A-Za-z_][A-Za-z0-9_]*|\\d+(?:\\.\\d+)?|\\.{2,}|==|!=|->|=>|>=|<=|\\S")
    private val SQL_SHORT = setOf("AS", "ON", "BY", "OR", "AND", "NOT", "ALL", "SET", "END", "IS", "IN", "IF", "ADD", "ROW")
    private val OPS = mapOf("==" to "double equals", "!=" to "not equals", "->" to "arrow", "=>" to "arrow",
        ">=" to "greater or equal", "<=" to "less or equal", ">" to "greater than", "<" to "less than",
        "=" to "equals", "*" to "star", "+" to "plus", "%" to "percent", "/" to "slash")

    /** Whole side: [codeSpans], then tags/entities like AnkiText.stripHtml, then prose identifiers. */
    fun speakable(html: String): String {
        var s = lineBreak.replace(codeSpans(html), "\n")
        s = decode(tag.replace(s, " "))
        s = prose(s)
        return tidy(s).lines().map { AnkiText.sanitizeForSpeech(it) }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    /** h110b: already-plain text (no tags, entities decoded): prose + sanitize only, so a literal < or > is kept. */
    fun speakablePlain(text: String): String =
        tidy(prose(text)).lines().map { AnkiText.sanitizeForSpeech(it) }.filter { it.isNotEmpty() }.joinToString("\n")

    /** Plain text: underscore identifiers and lone ==, ->, != outside code tags. */
    fun prose(text: String): String {
        val s = proseIdent.replace(text) { m -> words(m.value) }
        return s.replace(Regex("(?<=\\s|^)(==|->|!=)(?=\\s|$)")) { m -> OPS.getValue(m.value) }
    }

    /** Only <pre> and <code> rewritten to words; the rest of the HTML is returned as is (for AnkiText.speakable). */
    fun codeSpans(html: String): String {
        val s = pre.replace(html) { m -> "\n" + block(decode(tag.replace(lineBreak.replace(m.groupValues[1], "\n"), " "))) + "\n" }
        return code.replace(s) { m -> " " + inline(decode(tag.replace(m.groupValues[1], " "))) + " " }
    }

    /** <pre> body: short -> every line read; longer than [MAX_BLOCK_TOKENS] -> notice + first line. */
    private fun block(body: String): String {
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val count = lines.sumOf { codeToken.findAll(it).count() }
        if (count <= MAX_BLOCK_TOKENS) return lines.joinToString(", ") { inline(it) }
        return "$LONG_BLOCK " + inline(lines.firstOrNull().orEmpty())
    }

    /** One code span to words. Never emits '.', '!' or '?'. */
    fun inline(src: String): String {
        val out = ArrayList<String>()
        val toks = codeToken.findAll(src).map { it.value }.toList()
        for ((i, t) in toks.withIndex()) {
            val prev = toks.getOrNull(i - 1); val next = toks.getOrNull(i + 1)
            when {
                Regex("_{3,}").matches(t) -> out += "blank"
                t.first().isLetter() || t.first() == '_' -> out += words(t)
                t.first().isDigit() -> out += t
                t == "." -> if (next != null && ident.matches(next)) out += "dot"
                t == "(" && next == ")" -> {}
                t == ")" && prev == "(" -> {}
                t == "(" && prev != null && prev.any { it.isLowerCase() } && ident.matches(prev) -> out += "of"
                t == "(" || t == ")" || t == "[" || t == "]" || t == "{" || t == "}" -> out += ","
                t == "," -> out += ","
                t in OPS -> out += OPS.getValue(t)
                else -> {} // ; : quotes backticks and other symbols are silent
            }
        }
        return tidy(out.joinToString(" ")).trim(' ', ',')
    }

    /** snake_case / SCREAMING_CASE / camelCase identifier -> lower-case-ish words, underscores silent. */
    fun words(id: String): String {
        val parts = id.split('_').filter { it.isNotEmpty() }
            .flatMap { p -> Regex("[A-Z]+(?![a-z])\\d*|[A-Z]?[a-z]+\\d*|\\d+").findAll(p).map { it.value }.toList().ifEmpty { listOf(p) } }
        val screaming = id.contains('_') && id.none { it.isLowerCase() }
        return parts.joinToString(" ") { w ->
            val caps = w.all { it.isUpperCase() }
            when {
                caps && (screaming || w.length >= 4 || w in SQL_SHORT) -> w.lowercase()
                caps -> w
                else -> w.lowercase()
            }
        }
    }

    private fun decode(s: String): String = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&apos;", "'").replace("&nbsp;", " ").replace("&amp;", "&")

    private fun tidy(s: String): String = s.lines().joinToString("\n") { ws.replace(it, " ").trim() }
        .replace(Regex(" +,"), ",").replace(Regex(",(\\s*,)+"), ",").replace(Regex("\\( *,?"), "(").replace(Regex(",? *\\)"), ")")
        .replace(Regex(" *\\n[\\s]*"), "\n").replace(Regex(" +([.,;:?!)])"), "$1").replace(Regex("\\( +"), "(").replace(Regex(" {2,}"), " ").trim()
}
