package com.evan.voicesrs

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
    fun parseDump(text: String): List<DumpCard> = TODO()
    fun concept(card: DumpCard): String = TODO()   // group name or "Other"
    fun build(date: String, cards: List<DumpCard>): DailyReport = TODO()
    fun pct(d: Double): String = String.format(java.util.Locale.ROOT, "%.1f%%", d)  // 75.0%
    fun render(r: DailyReport): String = TODO()
}
