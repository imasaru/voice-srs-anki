package com.evan.voicesrs

/**
 * Display-only code chips, ported from anki-codewrap.py.
 * A side that already has `<code>` or `<pre>` is left alone.
 * Spoken text, [ContentHash], and prompts keep the plain field.
 */
object CodeDetect {
    const val SETTING_DEFAULT = true

    private val P2 = """\((?:[^()]|\([^()]*\))*\)"""
    private val CHAIN = Regex(
        """\b(?:dbutils|spark|df|pyspark)(?:\.[A-Za-z_]\w*(?:$P2)?)+"""
    )
    private val PATH = Regex(
        """(?:dbfs:/|s3a?://|abfss://|gs://|(?<![\w/])/Volumes/)[^\s<>"'(),;]+"""
    )
    private val FN = Regex(
        """\b(?:filter|transform|aggregate|array_contains|exists|forall|reduce|zip_with|map_filter|explode|from_json|to_json|collect_list|collect_set|array_distinct|flatten)$P2"""
    )
    private val PROT = Regex("""(?is)<pre\b.*?</pre>|<code\b.*?</code>|<[^>]+>""")
    private val TOKEN = Regex(
        """[ \t]*(?:(?<p>$P2)|(?<s>'[^'<]*'|&quot;.*?&quot;)|(?<n>\d+(?:\.\d+)?\b)|(?<o>&gt;=|&lt;=|&lt;&gt;|&gt;|&lt;|!=|<>|>=|<=|=|>|<|\*|\+)|(?<c>,)|(?<w>`[^`]+`|[A-Za-z_][\w.]*\w|[A-Za-z_]))"""
    )
    private val KEYWORD = Regex("""\b[A-Z][A-Z_]+\b""")

    private val START = setOf(
        "SELECT", "FROM", "WHERE", "JOIN", "INSERT", "UPDATE", "DELETE", "MERGE",
        "PIVOT", "UNPIVOT", "GROUP", "ORDER", "HAVING", "LIMIT", "VALUES", "CREATE",
        "COPY", "OPTIMIZE", "VACUUM", "DESCRIBE", "ALTER", "DROP", "ZORDER",
        "TBLPROPERTIES", "QUALIFY", "PARTITIONED", "CLUSTER", "WHEN", "CONVERT",
        "RESTORE", "TRUNCATE", "APPLY", "MSCK", "ANALYZE", "REFRESH", "CACHE",
        "SHOW", "LATERAL", "CONSTRAINT", "EXPECT", "USING", "VERSION", "TIMESTAMP",
        "INTERSECT", "EXCEPT", "UNION"
    )
    private val CONT = START + setOf(
        "BY", "INTO", "OVERWRITE", "DISTINCT", "ALL", "AS", "OF", "OR", "REPLACE",
        "TABLE", "TABLES", "VIEW", "LIVE", "STREAMING", "MATERIALIZED", "TEMP",
        "TEMPORARY", "EXTERNAL", "SCHEMA", "CATALOG", "IF", "NOT", "EXISTS",
        "MATCHED", "THEN", "SET", "ON", "AND", "IN", "IS", "NULL", "LEFT", "RIGHT",
        "INNER", "OUTER", "FULL", "CROSS", "ANTI", "SEMI", "HISTORY", "DETAIL",
        "EXTENDED", "TO", "DELTA", "CHANGES", "SOURCE", "TARGET", "KEYS",
        "SEQUENCE", "DESC", "ASC", "FOR", "ROW", "ROWS", "FAIL", "VIOLATION",
        "LIKE", "BETWEEN", "CASE", "END", "ELSE", "COUNT", "SUM", "AVG", "MIN",
        "MAX", "FILES", "FORMAT", "FILEFORMAT", "OPTIONS", "COPY_OPTIONS",
        "PATTERN", "GRANTS", "PARTITION", "REPAIR", "STATISTICS", "COMPUTE",
        "RETAIN", "HOURS", "DRY", "RUN", "CDC", "AUTO", "CHECK", "OWNER", "ADD",
        "COLUMN", "COLUMNS", "PRIVILEGES", "DATABASE", "PARTITIONS"
    )
    private val OPER = setOf(
        "FROM", "JOIN", "INTO", "TABLE", "VIEW", "UPDATE", "BY", "DISTINCT",
        "SELECT", "WHERE", "ON", "SET", "AND", "AS", "OPTIMIZE", "VACUUM",
        "HISTORY", "DETAIL", "LIMIT", "RETAIN", "ALL"
    )
    private val STOP = setOf(
        "the", "a", "an", "and", "or", "to", "which", "with", "without", "is",
        "are", "was", "be", "been", "being", "in", "on", "of", "for", "that",
        "this", "these", "those", "it", "its", "as", "by", "not", "no", "when",
        "then", "can", "will", "would", "should", "must", "may", "might", "only",
        "also", "but", "if", "so", "than", "into", "from", "at", "via", "using",
        "use", "used", "uses", "returns", "return", "creates", "create",
        "statement", "statements", "clause", "clauses", "syntax", "command",
        "commands", "query", "queries", "keyword", "keywords", "operation",
        "operations", "type", "types", "condition", "conditions", "result",
        "results", "behavior", "semantics", "logic", "step", "operator",
        "function", "functions", "table", "tables", "view", "views", "column",
        "columns", "row", "rows", "data", "each", "every", "all", "any", "both",
        "either", "new", "old", "same", "such", "per", "vs", "does", "do",
        "aggregation"
    )
    private val PAROK = setOf(
        "VALUES", "INSERT", "IN", "BY", "PARTITION", "OPTIONS", "TBLPROPERTIES",
        "COUNT", "SUM", "AVG", "MIN", "MAX", "EXISTS", "USING", "PIVOT",
        "UNPIVOT", "FOR", "AS"
    )
    private val NO_BARE_ID = setOf("OPTIMIZE", "VACUUM", "HISTORY", "DETAIL")

    /** Same wrapping as the script, including text outside an existing tag. */
    fun wrap(field: String): String {
        if (field.isEmpty()) return field
        val out = StringBuilder()
        var pos = 0
        for (match in PROT.findAll(field)) {
            out.append(procText(field.substring(pos, match.range.first)))
            out.append(match.value)
            pos = match.range.last + 1
        }
        out.append(procText(field.substring(pos)))
        return out.toString()
    }

    /**
     * Chips for a card side. Existing `<code>` or `<pre>` wins for the whole side.
     * [enabled] false leaves the text plain.
     */
    fun forDisplay(markup: String, enabled: Boolean): String {
        if (!enabled || markup.isEmpty()) return markup
        if (CodeMarkup.hasCode(markup)) return markup
        return wrap(markup)
    }

    private data class Span(val start: Int, val end: Int)

    private fun procText(text: String): String {
        if (text.isEmpty()) return text
        val found = ArrayList<Span>()
        for (regex in listOf(CHAIN, PATH, FN)) {
            for (match in regex.findAll(text)) {
                val start = match.range.first
                val end = trim(start, match.range.last + 1, text)
                if (end <= start) continue
                val slice = text.substring(start, end)
                if (regex === CHAIN && '(' !in slice && slice.startsWith("spark.") && slice.count { it == '.' } >= 2) {
                    found += Span(start, end)
                } else if (regex === CHAIN && '.' !in slice) {
                    continue
                } else {
                    found += Span(start, end)
                }
            }
        }
        for ((start, end) in sqlRuns(text)) {
            if (end > start) found += Span(start, end)
        }
        found.sortWith(compareBy<Span> { it.start }.thenByDescending { it.end - it.start })
        val kept = ArrayList<Span>()
        var last = 0
        for (span in found) {
            if (span.start < last) continue
            kept += span
            last = span.end
        }
        if (kept.isEmpty()) return text
        val out = StringBuilder()
        var cursor = 0
        for (span in kept) {
            out.append(text, cursor, span.start)
            out.append("<code>")
            out.append(text, span.start, span.end)
            out.append("</code>")
            cursor = span.end
        }
        out.append(text.substring(cursor))
        return out.toString()
    }

    private fun trim(start: Int, end: Int, text: String): Int {
        var e = end
        while (e > start && text[e - 1] in ".,;:!?") e--
        while (e > start && text[e - 1] == ')' &&
            text.substring(start, e).count { it == '(' } < text.substring(start, e).count { it == ')' }
        ) {
            e--
        }
        return e
    }

    private data class Tok(val kind: String, val value: String, val end: Int)

    private fun tokenAt(text: String, pos: Int): Tok? {
        if (pos > text.length) return null
        val match = TOKEN.find(text, pos) ?: return null
        if (match.range.first != pos) return null
        val kind = listOf("p", "s", "n", "o", "c", "w").firstOrNull { match.groups[it] != null } ?: return null
        return Tok(kind, match.groups[kind]!!.value, match.range.last + 1)
    }

    private fun nextOk(text: String, end: Int): Boolean {
        val tok = tokenAt(text, end) ?: return true
        if (tok.kind != "w") return true
        return tok.value.allUpper() && tok.value in CONT
    }

    private fun sqlRuns(text: String): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (match in KEYWORD.findAll(text)) {
            val word = match.value
            if (word !in START) continue
            val start = match.range.first
            if (out.isNotEmpty() && start < out.last().second) continue
            if (start > 0 && text[start - 1] == '-') continue
            var lastKw = word
            var end = match.range.last + 1
            var prev = if (word in OPER) "kwo" else "kw"
            var good = end
            while (true) {
                val tok = tokenAt(text, end) ?: break
                val kind = tok.kind
                val value = tok.value
                if (kind == "w") {
                    if (value.allUpper() && value in CONT) {
                        prev = if (value in OPER) "kwo" else "kw"
                        lastKw = value
                    } else if (value.allUpper() || value.lowercase() in STOP || value[0].isUpperCase()) {
                        break
                    } else if ((value.startsWith("`") || value.any { it == '_' || it == '.' || it.isDigit() }) &&
                        prev in setOf("kw", "kwo", "c", "o")
                    ) {
                        prev = "id"
                    } else if (prev in setOf("kwo", "c", "o") && lastKw !in NO_BARE_ID && nextOk(text, tok.end)) {
                        prev = "id"
                    } else {
                        break
                    }
                } else if (kind == "p" || kind == "s" || kind == "n") {
                    if (prev == "id" && kind != "p") break
                    if (kind == "p" && prev != "id" && lastKw !in PAROK) break
                    prev = "id"
                } else if (kind == "o") {
                    if (value == "*" && prev in setOf("kw", "kwo", "c")) prev = "id"
                    else if (prev == "id") prev = "o"
                    else break
                } else if (kind == "c") {
                    if (prev != "id") break
                    prev = "c"
                }
                end = tok.end
                if (prev == "id" || prev == "kw" || prev == "kwo") good = end
            }
            out += start to trim(start, good, text)
        }
        return out
    }

    private fun String.allUpper(): Boolean {
        var cased = false
        for (ch in this) {
            if (!ch.isLetter()) continue
            if (!ch.isUpperCase()) return false
            cased = true
        }
        return cased
    }
}