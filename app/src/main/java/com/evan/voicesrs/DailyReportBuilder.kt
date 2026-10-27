package com.evan.voicesrs

import org.json.JSONObject

data class DumpRev(val time: String, val ease: Int, val type: Int, val ivl: Int, val ms: Long)
data class DumpCard(val cid: Long, val deck: String, val domain: String, val stem: String, val correctText: String, val revs: List<DumpRev>)
data class PctRow(val name: String, val correct: Int, val total: Int) {
    val pct: Double get() = if (total == 0) 0.0 else correct * 100.0 / total
}
data class DailyReport(
    val date: String,              // "2026-10-26"
    val cards: Int,                // cards with >=1 rev
    val reviews: Int,              // total revs
    val accuracyPct: Double,       // revs with ease > 1 / reviews * 100
    val firstTryPct: Double,       // cards whose FIRST rev has ease > 1 / cards * 100
    val again: Int, val hard: Int, val good: Int, val easy: Int,
    val newCards: Int,             // cards whose first rev has type == 0
    val reviewCards: Int,          // cards - newCards
    val firstTime: String?, val lastTime: String?,  // min/max rev time, null if no revs
    val zeroMsReviews: Int,        // revs with ms == 0
    val byDeck: List<PctRow>,      // name = last "::" segment of deck; sorted by total desc
    val byDomain: List<PctRow>,    // blank domain -> "Unknown"; sorted by pct ASC (weakest first), ties by name
    val missedByConcept: Map<String, List<DumpCard>>, // cards with any ease==1 rev, grouped by concept(); LinkedHashMap in CONCEPTS order, "Other" last; empty groups omitted
)
object DailyReportBuilder {
    /** Ordered: the first group whose keyword appears (case-insensitive) in stem+" "+correctText wins. */
    val CONCEPTS: List<Pair<String, List<String>>> = listOf(
        "Clusters & compute" to listOf("cluster", "instance pool", "photon", "autoscal", "serverless", "compute"),
        "Notebook utilities" to listOf("%run", "dbutils", "dbfs", "widget", "notebook"),
        "Spark performance" to listOf("skew", "broadcast", "shuffle", "distinct", "group by", "cache", "partition pruning"),
        "SQL analytics" to listOf("rank", "row_number", "rows between", "filter(", "window", "explode", "pivot"),
        "Delta writes" to listOf("insert overwrite", "merge", "copy into", "optimize", "vacuum", "time travel"),
        "Unity Catalog" to listOf("unity catalog", "metastore", "hive", "external location", "storage credential", "system.access", "grant"),
        "Lakehouse basics" to listOf("lakehouse", "delta lake", "acid", "medallion"),
    )
    fun parseDump(text: String): List<DumpCard> {
        val out = ArrayList<DumpCard>()
        for (rawLine in text.lines()) {
            val line = rawLine.trimStart()
            if (!line.startsWith("{")) continue
            val obj = JSONObject(line)
            val fields = obj.optJSONObject("fields")
            val revs = ArrayList<DumpRev>()
            val revsArr = obj.optJSONArray("revs")
            if (revsArr != null) {
                for (i in 0 until revsArr.length()) {
                    val r = revsArr.getJSONArray(i)
                    revs += DumpRev(
                        time = r.optString(0).orEmpty(),
                        ease = r.optInt(1),
                        type = r.optInt(2),
                        ivl = r.optInt(3),
                        ms = r.optLong(4),
                    )
                }
            }
            out += DumpCard(
                cid = obj.optLong("cid"),
                deck = obj.optString("deck").orEmpty(),
                domain = fields?.optString("Domain").orEmpty(),
                stem = fields?.optString("Stem").orEmpty(),
                correctText = fields?.optString("CorrectText").orEmpty(),
                revs = revs,
            )
        }
        return out
    }

    fun concept(card: DumpCard): String {
        val hay = (card.stem + " " + card.correctText).lowercase()
        for ((group, keywords) in CONCEPTS) {
            for (kw in keywords) {
                if (hay.contains(kw.lowercase())) return group
            }
        }
        return "Other"
    }
    fun build(date: String, cards: List<DumpCard>): DailyReport {
        val withRevs = cards.filter { it.revs.isNotEmpty() }
        val allRevs = withRevs.flatMap { it.revs }
        val reviews = allRevs.size
        val accuracyPct = if (reviews == 0) 0.0 else allRevs.count { it.ease > 1 } * 100.0 / reviews
        val firstTryPct = if (withRevs.isEmpty()) 0.0 else withRevs.count { it.revs.first().ease > 1 } * 100.0 / withRevs.size
        val again = allRevs.count { it.ease == 1 }
        val hard = allRevs.count { it.ease == 2 }
        val good = allRevs.count { it.ease == 3 }
        val easy = allRevs.count { it.ease == 4 }
        val newCards = withRevs.count { it.revs.first().type == 0 }
        val reviewCards = withRevs.size - newCards
        val zeroMsReviews = allRevs.count { it.ms == 0L }
        val firstTime = allRevs.minByOrNull { pad5(it.time) }?.time
        val lastTime = allRevs.maxByOrNull { pad5(it.time) }?.time

        val byDeck = withRevs
            .groupBy { it.deck.split("::").lastOrNull()?.trim().orEmpty() }
            .map { (deckName, cs) ->
                PctRow(deckName, cs.sumOf { it.revs.count { r -> r.ease > 1 } }, cs.sumOf { it.revs.size })
            }
            .sortedWith(compareByDescending<PctRow> { it.total }.thenBy { it.name })

        val byDomain = withRevs
            .groupBy { if (it.domain.isBlank()) "Unknown" else it.domain }
            .map { (domain, cs) ->
                PctRow(domain, cs.sumOf { it.revs.count { r -> r.ease > 1 } }, cs.sumOf { it.revs.size })
            }
            .sortedWith(compareBy<PctRow> { it.pct }.thenBy { it.name })

        val buckets = CONCEPTS.map { it.first }.toMutableList()
        buckets.add("Other")
        val missed = LinkedHashMap<String, ArrayList<DumpCard>>()
        for (b in buckets) missed[b] = ArrayList()
        for (card in withRevs) {
            if (card.revs.any { it.ease == 1 }) {
                val c = concept(card)
                missed.getOrPut(c) { ArrayList() }.add(card)
            }
        }
        val missedByConcept = LinkedHashMap<String, List<DumpCard>>()
        for (b in buckets) {
            val list = missed[b]
            if (list != null && list.isNotEmpty()) missedByConcept[b] = list
        }

        return DailyReport(
            date = date,
            cards = withRevs.size,
            reviews = reviews,
            accuracyPct = accuracyPct,
            firstTryPct = firstTryPct,
            again = again,
            hard = hard,
            good = good,
            easy = easy,
            newCards = newCards,
            reviewCards = reviewCards,
            firstTime = firstTime,
            lastTime = lastTime,
            zeroMsReviews = zeroMsReviews,
            byDeck = byDeck,
            byDomain = byDomain,
            missedByConcept = missedByConcept,
        )
    }
    fun pct(d: Double): String = String.format(java.util.Locale.ROOT, "%.1f%%", d)  // 75.0%
    private fun pad5(t: String): String = if (t.length == 5) t + ":00" else t
    fun render(r: DailyReport): String {
        val sb = StringBuilder()
        sb.append("# Daily study report — ").append(r.date).append('\n')

        sb.append("## Time span\n")
        val span = if (r.firstTime != null && r.lastTime != null) "${r.firstTime}–${r.lastTime}" else "–"
        sb.append(span).append('\n')
        if (r.zeroMsReviews > 0) {
            sb.append("Note: ").append(r.zeroMsReviews)
                .append(" of ").append(r.reviews)
                .append(" answer times were recorded as 0 ms, so time-per-card is not reliable.")
                .append('\n')
        }

        sb.append("## Volume & accuracy\n")
        sb.append(r.cards).append(" cards, ").append(r.reviews).append(" reviews, ")
            .append(pct(r.accuracyPct)).append(" accuracy, ")
            .append(pct(r.firstTryPct)).append(" first-try\n")
        sb.append("Again ").append(r.again).append(" / Hard ").append(r.hard)
            .append(" / Good ").append(r.good).append(" / Easy ").append(r.easy).append('\n')
        sb.append(r.newCards).append(" new, ").append(r.reviewCards).append(" review\n")

        sb.append("## By deck\n")
        for (row in r.byDeck) {
            sb.append("- ").append(row.name).append(": ").append(pct(row.pct))
                .append(" (").append(row.total).append(" reviews)").append('\n')
        }

        sb.append("## Weakest domains\n")
        for (row in r.byDomain) {
            sb.append("- ").append(row.name).append(": ").append(pct(row.pct))
                .append(" (").append(row.correct).append("/").append(row.total).append(")").append('\n')
        }

        sb.append("## Missed cards by concept\n")
        for ((group, cards) in r.missedByConcept) {
            sb.append("### ").append(group).append(" (").append(cards.size).append(")\n")
            for (c in cards) {
                val stem = if (c.stem.length > 100) c.stem.substring(0, 100) else c.stem
                sb.append("- ").append(stem).append(" → ").append(c.correctText).append('\n')
            }
        }

        sb.append("## Focus next\n")
        for (row in r.byDomain.take(3)) {
            sb.append("- ").append(row.name).append('\n')
        }
        for (row in r.byDeck) {
            if (row.pct < 60.0) {
                sb.append("- Redo ").append(row.name).append('\n')
            }
        }

        return sb.toString()
    }
}
