package com.evan.voicesrs

/**
 * AI explanation sections from the thread mock.
 * Headings are Core concept, Why &lt;letter&gt;, Common traps, and Example.
 * Inline code is a chip run. A fence is a code block. Note / Source / License / URL
 * lines are a footer. Spoken text is the section words with the footer left out.
 */
object SectionFormat {
    enum class Kind { HEADING, BODY, BULLET, CODE, FOOTER }

    data class Run(val text: String, val code: Boolean)

    data class Block(val kind: Kind, val text: String, val runs: List<Run>)

    data class Doc(val blocks: List<Block>, val footer: String, val spoken: String)

    private val headingMd = Regex("""^#{1,6}\s+(.*)$""")
    private val boldOnly = Regex("""^\*\*(.+)\*\*$""")
    private val known = Regex("""(?i)^(core concept|why [a-f]|common traps|example)$""")
    private val bullet = Regex("""^\s*(?:[-*•]|\d+[.)])\s+(.*)$""")
    private val noteLine = Regex("""(?i)^note\s*:""")

    fun parse(src: String): Doc {
        val clean = ThinkStrip.strip(src).replace("\r\n", "\n").replace('\r', '\n')
        val blocks = ArrayList<Block>()
        val foot = ArrayList<String>()
        val raw = clean.split('\n')
        var i = 0
        while (i < raw.size) {
            if (raw[i].trim().startsWith("```")) {
                val code = StringBuilder()
                i++
                while (i < raw.size && !raw[i].trim().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(raw[i])
                    i++
                }
                if (i < raw.size) i++
                val text = code.toString().trim('\n')
                if (text.isNotEmpty()) {
                    blocks += Block(Kind.CODE, text, listOf(Run(text, code = true)))
                }
                continue
            }
            val line = raw[i].trim()
            i++
            if (line.isEmpty()) continue
            if (isFooter(line)) {
                foot += line
                continue
            }
            val kept = Citation.withoutCitation(line)
            if (kept.isEmpty()) continue
            if (kept != line) {
                val tail = line.removePrefix(kept).trim()
                if (tail.isNotEmpty()) foot += tail
            }
            val heading = headingText(kept)
            if (heading != null) {
                val runs = inlineRuns(heading)
                blocks += Block(Kind.HEADING, runs.joinToString("") { it.text }, runs)
                continue
            }
            val item = bullet.matchEntire(kept)
            if (item != null) {
                val runs = inlineRuns(item.groupValues[1])
                blocks += Block(Kind.BULLET, runs.joinToString("") { it.text }, runs)
                continue
            }
            val runs = inlineRuns(kept)
            blocks += Block(Kind.BODY, runs.joinToString("") { it.text }, runs)
        }
        val footer = foot.joinToString("\n")
        if (footer.isNotEmpty()) {
            blocks += Block(Kind.FOOTER, footer, listOf(Run(footer, code = false)))
        }
        val spoken = blocks.filter { it.kind != Kind.FOOTER }
            .joinToString(" ") { it.text }
            .replace(Regex("\\s+"), " ")
            .trim()
        return Doc(blocks, footer, spoken)
    }

    fun spoken(src: String): String = parse(src).spoken

    private fun isFooter(line: String): Boolean {
        return Citation.isCitationLine(line) || noteLine.containsMatchIn(line)
    }

    private fun headingText(line: String): String? {
        val md = headingMd.matchEntire(line)
        val raw = when {
            md != null -> md.groupValues[1].trim()
            boldOnly.matches(line) -> boldOnly.matchEntire(line)!!.groupValues[1].trim()
            else -> line
        }
        val plain = raw.replace("**", "").replace("`", "").trim()
        if (known.matches(plain)) return displayHeading(plain)
        if (md != null && plain.isNotEmpty()) return plain
        return null
    }

    /** "why b" from the model becomes "Why B". */
    private fun displayHeading(plain: String): String {
        val why = Regex("""(?i)^why ([a-f])$""").matchEntire(plain)
        if (why != null) return "Why ${why.groupValues[1].uppercase()}"
        return plain.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            .let { titled ->
                when (titled.lowercase()) {
                    "core concept" -> "Core concept"
                    "common traps" -> "Common traps"
                    "example" -> "Example"
                    else -> titled
                }
            }
    }

    private fun inlineRuns(src: String): List<Run> {
        val scrubbed = src.replace("**", "").replace("__", "")
        val runs = ArrayList<Run>()
        val buf = StringBuilder()
        var i = 0
        fun flush() {
            if (buf.isNotEmpty()) {
                runs += Run(buf.toString(), code = false)
                buf.clear()
            }
        }
        while (i < scrubbed.length) {
            if (scrubbed[i] == '`') {
                val end = scrubbed.indexOf('`', i + 1)
                if (end > i + 1) {
                    flush()
                    runs += Run(scrubbed.substring(i + 1, end), code = true)
                    i = end + 1
                    continue
                }
            }
            if (scrubbed[i] != '`') buf.append(scrubbed[i])
            i++
        }
        flush()
        return runs.filter { it.text.isNotEmpty() }
    }
}
