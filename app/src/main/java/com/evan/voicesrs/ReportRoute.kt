package com.evan.voicesrs

/**
 * A typed or spoken "today's study report" goes to the report path wherever it
 * is asked (general box, live card, or a past card's thread) instead of being
 * answered as a card question. Reuses [UnderstandingInsights.classify].
 */
object ReportRoute {
    fun isReportRequest(text: String): Boolean =
        UnderstandingInsights.classify(text) == QuestionType.REPORT_REQUEST
}
