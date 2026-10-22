package com.evan.voicesrs

/**
 * Pure-Kotlin HTML cleanup for Anki card fields (unit-testable, no Android deps).
 * Strips tags, decodes common entities, collapses whitespace.
 *
 * Kengio decks hide a spoken script in `.spoken` (CSS `display:none`) and put a
 * longer explanation in `.screen-only`, with a short `.summary` beside it.
 * When `.spoken` is present, only that script is spoken. Otherwise the
 * `.screen-only` explanation is spoken and the short `.summary` is not, so the
 * explanation is read once. `.src` and Source / License / URL lines are never
 * spoken. [displayHtml] still draws `.screen-only` and hides `.summary`.
 * Cards with neither class match [stripHtml] / [toDisplayHtml].
 * [toDisplayHtml] keeps `<code>` and `<pre>` (entities decoded) so the card
 * face can draw chips. [stripHtml] and [spokenText] still drop those tags
 * and do not add pauses or punctuation.
 */
object AnkiText {
    private val scriptStyle = Regex("<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val lineBreak = Regex("<br\\s*/?>|</p>|</div>|</li>|</h[1-6]>", RegexOption.IGNORE_CASE)
    private val tag = Regex("<[^>]+>")
    private val whitespace = Regex("\\s+")
    private val soundMarker = Regex("\\[sound:[^\\]]*]", RegexOption.IGNORE_CASE)
    private val mediaTag = Regex(
        "<(?:img|audio|video|source|object|embed|svg|iframe|link|meta)\\b[^>]*>",
        RegexOption.IGNORE_CASE
    )
    private val numericEntity = Regex("&#(\\d+);")
    private val hexEntity = Regex("&#x([0-9a-fA-F]+);")
    /** Anki's default back template is `{{FrontSide}}<hr id=answer>`. */
    private val answerDivider = Regex(
        """<hr[^>]*\bid\s*=\s*["']?answer["']?[^>]*>""",
        RegexOption.IGNORE_CASE
    )
    /** A CSS declaration, so `display:none` inside another property does not match. */
    private val displayNoneDecl = Regex(
        """(?:^|;)\s*display\s*:\s*none\b""",
        RegexOption.IGNORE_CASE
    )
    private val voidTags = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    private val namedEntities = mapOf(
        "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">",
        "quot" to "\"", "apos" to "'", "rsquo" to "'", "lsquo" to "'",
        "rdquo" to "\"", "ldquo" to "\"", "ndash" to "-", "mdash" to "-",
        "hellip" to "...", "copy" to "(c)", "reg" to "(r)"
    )

    fun stripHtml(html: String): String {
        var s = scriptStyle.replace(html, " ")
        s = lineBreak.replace(s, "\n")
        s = tag.replace(s, " ")
        s = decodeEntities(s)
        s = whitespace.replace(s, " ")
        return s.trim()
    }

    /**
     * One display line. Same as [stripHtml] when there is no `<code>` or `<pre>`.
     * Those spans are kept, with entities decoded, so a card face can style them.
     */
    fun displayPlain(html: String): String {
        val (shielded, slots) = protectCode(html)
        if (slots.isEmpty()) return stripHtml(html)
        var s = lineBreak.replace(shielded, "\n")
        s = tag.replace(s, " ")
        s = decodeEntities(s)
        s = whitespace.replace(s, " ").trim()
        return restoreSlots(s, slots)
    }

    /**
     * Text to speak for one card side.
     *
     * If the side has an element classed `spoken`, only those elements are spoken.
     * A `.spoken` element is the replacement, so it is kept even when it also has
     * inline `display:none`. Screen-only text nested inside that script is not spoken.
     *
     * Otherwise the visible side is spoken, including `.screen-only`. When a
     * `.screen-only` explanation is present the short `.summary` is dropped, so
     * the same paragraph is not read twice. Inline `display:none` and `.src`
     * are dropped. Source / License / URL lines are dropped by [speakable].
     *
     * Does not look for the answer divider. Call [answerOnly] first on a back.
     */
    fun spokenText(html: String): String {
        val spoken = extractInners(html) { attrs -> hasClass(attrs, "spoken") }
        val source = if (spoken.isNotEmpty()) {
            spoken.joinToString("\n")
        } else {
            var body = html
            if (findNextElement(body, 0) { hasClass(it, "screen-only") } != null) {
                body = removeElements(body) { attrs ->
                    hasClass(attrs, "summary") && !hasClass(attrs, "screen-only")
                }
            }
            body
        }
        val cleaned = removeElements(source) { attrs ->
            when {
                hasClass(attrs, "spoken") -> false
                spoken.isNotEmpty() && hasClass(attrs, "screen-only") -> true
                hasClass(attrs, "screen-only") -> false
                else -> isInlineDisplayNone(attrs) || hasClass(attrs, "src")
            }
        }
        return speakable(cleaned)
    }

    /**
     * Same words as [stripHtml], except Source / License / URL lines and a
     * trailing citation clause are never spoken.
     */
    private fun speakable(html: String): String {
        var s = scriptStyle.replace(html, " ")
        s = lineBreak.replace(s, "\n")
        s = tag.replace(s, " ")
        s = decodeEntities(s)
        val kept = ArrayList<String>()
        for (line in s.split('\n')) {
            val trimmed = whitespace.replace(line, " ").trim()
            val spoken = Citation.withoutCitation(trimmed)
            if (spoken.isNotEmpty()) kept.add(spoken)
        }
        return kept.joinToString(" ")
    }

    /**
     * HTML after the first `<hr id=answer>` divider. Anki's default back
     * template starts with `{{FrontSide}}`, so the raw answer repeats the
     * question. If the marker is missing, or the remainder is blank once
     * tags are stripped, returns [html] unchanged.
     */
    fun answerOnly(html: String): String {
        val match = answerDivider.find(html) ?: return html
        val after = html.substring(match.range.last + 1)
        return if (stripHtml(after).isBlank()) html else after
    }

    /**
     * Sanitized HTML for on-screen cards. Keeps line breaks, basic emphasis,
     * `<code>`, and `<pre>`. Drops images, [sound:…] tags, media, scripts, and styles.
     * Safe to pass to HtmlCompat. Spoken TTS uses [spokenText].
     */
    fun toDisplayHtml(html: String): String {
        var s = html.replace("\u0000", "").replace("\u0001", "")
        s = scriptStyle.replace(s, "")
        s = soundMarker.replace(s, "")
        s = mediaTag.replace(s, "")
        val (shielded, slots) = protectCode(s)
        s = shielded
        s = Regex("(?i)<br\\s*/?>").replace(s, "\u0001BR\u0001")
        s = Regex("(?i)</(?:div|p|li|tr|h[1-6])>").replace(s, "\u0001BR\u0001")
        s = Regex("(?i)<(?:div|p|li|tr|h[1-6])(?:\\s[^>]*)?>").replace(s, "\u0001BR\u0001")
        s = Regex("(?i)<\\s*(b|strong|i|em)\\b[^>]*>").replace(s) {
            "\u0001${it.groupValues[1].lowercase()}\u0001"
        }
        s = Regex("(?i)</\\s*(b|strong|i|em)\\s*>").replace(s) {
            "\u0001/${it.groupValues[1].lowercase()}\u0001"
        }
        s = tag.replace(s, "")
        s = decodeEntities(s)
        s = s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        s = s.replace("\u0001b\u0001", "<b>")
            .replace("\u0001/b\u0001", "</b>")
            .replace("\u0001strong\u0001", "<strong>")
            .replace("\u0001/strong\u0001", "</strong>")
            .replace("\u0001i\u0001", "<i>")
            .replace("\u0001/i\u0001", "</i>")
            .replace("\u0001em\u0001", "<em>")
            .replace("\u0001/em\u0001", "</em>")
            .replace("\u0001BR\u0001", "<br>")
        s = whitespace.replace(s, " ")
        s = Regex("(?:\\s*<br>\\s*){2,}").replace(s, "<br>")
        s = s.trim()
        s = Regex("^(?:<br>\\s*)+").replace(s, "")
        s = Regex("(?:\\s*<br>)+$").replace(s, "")
        return restoreSlots(s.trim(), slots)
    }

    /**
     * On-screen HTML. Drops `.spoken` and inline `display:none`. Keeps `.screen-only`.
     * When a longer `.screen-only` explanation is present, the short `.summary`
     * is not drawn, so the same paragraph is not shown twice. TTS reads that
     * `.screen-only` text (see [spokenText]). The choice is the class, not a
     * string comparison. Then the same sanitizer as [toDisplayHtml]. Cards with
     * none of those markers match [toDisplayHtml].
     */
    fun displayHtml(html: String): String {
        var cleaned = removeElements(html) { attrs ->
            hasClass(attrs, "spoken") || isInlineDisplayNone(attrs)
        }
        if (findNextElement(cleaned, 0) { hasClass(it, "screen-only") } != null) {
            cleaned = removeElements(cleaned) { hasClass(it, "summary") }
        }
        return toDisplayHtml(cleaned)
    }

    private data class CodeSlot(val markup: String)

    /**
     * Pull `<pre>` then `<code>` out before the tag strip. The placeholder has
     * no whitespace, so the later collapse cannot touch the code text.
     * No match returns [html] unchanged, so cards without code stay byte-identical.
     */
    private fun protectCode(html: String): Pair<String, List<CodeSlot>> {
        if (!Regex("(?i)<\\s*(pre|code)\\b").containsMatchIn(html)) return html to emptyList()
        val slots = ArrayList<CodeSlot>()
        fun token(markup: String): String {
            val id = slots.size
            slots += CodeSlot(markup)
            return "\uE000$id\uE001"
        }
        var s = Regex("(?is)<pre\\b[^>]*>(.*?)</pre>").replace(html) { m ->
            val inner = blockInner(m.groupValues[1])
            if (inner.isEmpty()) "" else token("<pre>$inner</pre>")
        }
        s = Regex("(?is)<code\\b[^>]*>(.*?)</code>").replace(s) { m ->
            val inner = inlineInner(m.groupValues[1])
            if (inner.isEmpty()) "" else token("<code>$inner</code>")
        }
        return s to slots
    }

    private fun restoreSlots(html: String, slots: List<CodeSlot>): String {
        if (slots.isEmpty()) return html
        var s = html
        for (i in slots.indices) s = s.replace("\uE000$i\uE001", slots[i].markup)
        return s
    }

    private fun inlineInner(raw: String): String {
        val stripped = Regex("(?is)<[^>]+>").replace(raw, "")
        return escapeHtml(decodeEntities(stripped).replace(Regex("\\s+"), " ").trim())
    }

    private fun blockInner(raw: String): String {
        var s = Regex("(?is)<br\\s*/?>").replace(raw, "\n")
        s = Regex("(?is)</?code\\b[^>]*>").replace(s, "")
        s = Regex("(?is)<[^>]+>").replace(s, "")
        s = decodeEntities(s).replace("\r\n", "\n").replace('\r', '\n').trim('\n')
        return escapeHtml(s)
    }

    private fun escapeHtml(s: String): String {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    private fun decodeEntities(s: String): String {
        var out = s
        out = numericEntity.replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
        }
        out = hexEntity.replace(out) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
        }
        for ((name, ch) in namedEntities) out = out.replace("&$name;", ch)
        return out
    }

    private data class TagMatch(
        val name: String,
        val attrs: String,
        val start: Int,
        val tagEnd: Int,
        val selfClosing: Boolean,
        val closing: Boolean
    )

    private data class ElementRange(
        val start: Int,
        val end: Int,
        val innerStart: Int,
        val innerEnd: Int
    )

    private data class CloseSpan(val contentEnd: Int, val elementEnd: Int)

    /** Whole class token. `class="x spoken"` matches; `bespoken` does not. Quotes may be ' " or absent. */
    private fun hasClass(attrs: String, className: String): Boolean {
        val value = attrValue(attrs, "class") ?: return false
        return value.split(Regex("\\s+")).any { it == className }
    }

    private fun isInlineDisplayNone(attrs: String): Boolean {
        val style = attrValue(attrs, "style") ?: return false
        return displayNoneDecl.containsMatchIn(style)
    }

    /** First matching attribute. Names are case-insensitive. Values inside other attributes are ignored. */
    private fun attrValue(attrs: String, wanted: String): String? {
        var i = 0
        while (i < attrs.length) {
            while (i < attrs.length && attrs[i].isWhitespace()) i++
            if (i >= attrs.length) return null
            if (attrs[i] == '/') {
                i++
                continue
            }
            val nameStart = i
            while (i < attrs.length && (attrs[i].isLetterOrDigit() || attrs[i] == '-' || attrs[i] == ':')) i++
            if (i == nameStart) {
                i++
                continue
            }
            val name = attrs.substring(nameStart, i)
            while (i < attrs.length && attrs[i].isWhitespace()) i++
            if (i >= attrs.length || attrs[i] != '=') {
                if (name.equals(wanted, ignoreCase = true)) return ""
                continue
            }
            i++
            while (i < attrs.length && attrs[i].isWhitespace()) i++
            if (i >= attrs.length) {
                return if (name.equals(wanted, ignoreCase = true)) "" else null
            }
            val value: String
            val q = attrs[i]
            if (q == '"' || q == '\'') {
                val start = ++i
                while (i < attrs.length && attrs[i] != q) i++
                value = attrs.substring(start, i)
                if (i < attrs.length) i++
            } else {
                val start = i
                while (i < attrs.length && !attrs[i].isWhitespace()) i++
                value = attrs.substring(start, i)
            }
            if (name.equals(wanted, ignoreCase = true)) return value
        }
        return null
    }

    private fun isVoid(name: String) = name.lowercase() in voidTags

    /**
     * Next tag at or after [from]. Skips comments. `>` inside a quoted attribute
     * does not end the tag. Returns null when nothing tag-like remains.
     */
    private fun nextTag(html: String, from: Int): TagMatch? {
        var i = from
        while (i < html.length) {
            val lt = html.indexOf('<', i)
            if (lt < 0 || lt + 1 >= html.length) return null
            if (html.startsWith("<!--", lt)) {
                val end = html.indexOf("-->", lt + 4)
                i = if (end < 0) html.length else end + 3
                continue
            }
            val c1 = html[lt + 1]
            if (c1 == '!' || c1 == '?') {
                val end = html.indexOf('>', lt + 2)
                i = if (end < 0) html.length else end + 1
                continue
            }
            val closing = c1 == '/'
            var nameStart = if (closing) lt + 2 else lt + 1
            if (closing) {
                while (nameStart < html.length && html[nameStart].isWhitespace()) nameStart++
            }
            if (nameStart >= html.length || !html[nameStart].isLetter()) {
                i = lt + 1
                continue
            }
            var j = nameStart + 1
            while (j < html.length && (html[j].isLetterOrDigit() || html[j] == '-' || html[j] == ':')) j++
            val name = html.substring(nameStart, j)
            var k = j
            var quote: Char? = null
            while (k < html.length) {
                val ch = html[k]
                if (quote != null) {
                    if (ch == quote) quote = null
                } else if (ch == '"' || ch == '\'') {
                    quote = ch
                } else if (ch == '>') {
                    break
                }
                k++
            }
            val raw = if (k >= html.length) html.substring(j) else html.substring(j, k)
            val selfClosing = raw.trimEnd().endsWith('/')
            val attrs = raw.trim().removeSuffix("/").trim()
            val tagEnd = if (k >= html.length) html.length else k + 1
            return TagMatch(name, attrs, lt, tagEnd, selfClosing, closing)
        }
        return null
    }

    /** Matching close for [tagName], counting nested tags of the same name. Script and style bodies are opaque. */
    private fun matchingClose(html: String, tagName: String, contentStart: Int): CloseSpan {
        var depth = 1
        var i = contentStart
        while (i < html.length) {
            val tag = nextTag(html, i) ?: return CloseSpan(html.length, html.length)
            if (tag.tagEnd <= i) return CloseSpan(html.length, html.length)
            if (!tag.closing &&
                (tag.name.equals("script", true) || tag.name.equals("style", true)) &&
                !tag.name.equals(tagName, true) &&
                !tag.selfClosing && !isVoid(tag.name)
            ) {
                i = matchingClose(html, tag.name, tag.tagEnd).elementEnd
                continue
            }
            if (tag.name.equals(tagName, true)) {
                if (tag.closing) {
                    depth--
                    if (depth == 0) return CloseSpan(tag.start, tag.tagEnd)
                } else if (!tag.selfClosing && !isVoid(tag.name)) {
                    depth++
                }
            }
            i = tag.tagEnd
        }
        return CloseSpan(html.length, html.length)
    }

    private fun findNextElement(html: String, from: Int, predicate: (String) -> Boolean): ElementRange? {
        var i = from
        while (i < html.length) {
            val tag = nextTag(html, i) ?: return null
            if (tag.tagEnd <= i) return null
            if (tag.closing) {
                i = tag.tagEnd
                continue
            }
            if ((tag.name.equals("script", true) || tag.name.equals("style", true)) &&
                !tag.selfClosing && !isVoid(tag.name)
            ) {
                i = matchingClose(html, tag.name, tag.tagEnd).elementEnd
                continue
            }
            if (predicate(tag.attrs)) {
                if (tag.selfClosing || isVoid(tag.name)) {
                    return ElementRange(tag.start, tag.tagEnd, tag.tagEnd, tag.tagEnd)
                }
                val close = matchingClose(html, tag.name, tag.tagEnd)
                return ElementRange(tag.start, close.elementEnd, tag.tagEnd, close.contentEnd)
            }
            i = tag.tagEnd
        }
        return null
    }

    private fun extractInners(html: String, predicate: (String) -> Boolean): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < html.length) {
            val el = findNextElement(html, i, predicate) ?: break
            if (el.end <= i) break
            out.add(html.substring(el.innerStart, el.innerEnd))
            i = el.end
        }
        return out
    }

    /** Returns [html] unchanged when nothing matches, so unspecialized cards stay byte-identical. */
    private fun removeElements(html: String, predicate: (String) -> Boolean): String {
        val sb = StringBuilder()
        var i = 0
        var removed = false
        while (i < html.length) {
            val el = findNextElement(html, i, predicate) ?: break
            if (el.end <= i) break
            sb.append(html, i, el.start)
            i = el.end
            removed = true
        }
        if (!removed) return html
        sb.append(html, i, html.length)
        return sb.toString()
    }
}
