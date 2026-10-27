package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportRouteTest {
    @Test
    fun reportRequestsRouteToReport() {
        assertTrue(ReportRoute.isReportRequest("today's study report"))
        assertTrue(ReportRoute.isReportRequest("  Can I get today's report?"))
        assertTrue(ReportRoute.isReportRequest("Daily report please"))
    }

    @Test
    fun cardQuestionsStayOnTheCard() {
        assertFalse(ReportRoute.isReportRequest("why is it B?"))
        assertFalse(ReportRoute.isReportRequest("what is a report in Databricks SQL dashboards"))
        assertFalse(ReportRoute.isReportRequest("explain this"))
    }
}
