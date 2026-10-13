package com.bluepad.diagnostics

import org.junit.Assert.*
import org.junit.Test

class ConnectionTimelineTest {
    @Test fun timelinePreservesRegistrationAttemptCallbackAndDeadlineOrder() {
        var now = 10L
        val timeline = ConnectionTimeline({ now++ })
        listOf("register_result accepted=true","registration_callback registered=true",
            "connect_result accepted=true profileState=0","connection_callback state=1",
            "deadline_expired operation=CONNECTION","connection_reconcile_check profileState=1",
            "profile_close reason=Connection timed out").forEach { timeline.record(it) }
        timeline.failure()
        val report = timeline.report("api=30 manufacturer=realme model=C30")
        assertTrue(timeline.failed)
        assertTrue(report.indexOf("register_result") < report.indexOf("connect_result"))
        assertTrue(report.indexOf("connection_callback") < report.indexOf("deadline_expired"))
        assertTrue(report.contains("uptimeMs=10")); assertTrue(report.contains("api=30"))
    }
    @Test fun sustainedRetriesCannotGrowStorageIndefinitely() {
        val timeline = ConnectionTimeline({ 0L },3)
        repeat(10000) { timeline.record("attempt=$it") }
        val report = timeline.report("test")
        assertFalse(report.contains("attempt=9996\n"))
        assertTrue(report.contains("attempt=9997")); assertTrue(report.contains("attempt=9999"))
        assertTrue(report.contains("discarded=9997"))
        assertEquals(3,report.lines().count { it.startsWith("uptimeMs=") })
    }
    @Test fun maximumLineAndHeaderLengthsAreBounded() {
        val timeline = ConnectionTimeline({ 0L })
        repeat(1000) { timeline.record("x".repeat(10000)) }
        assertTrue(timeline.report("y".repeat(10000)).length < 75000)
    }
    @Test fun hostAddressDefenseAndNewlinesAreSanitized() {
        val timeline = ConnectionTimeline({ 0L })
        timeline.record("device=20:74:54:CC:03:F2\nforged_event")
        val report = timeline.report("api=30")
        assertFalse(report.contains("20:74:54")); assertTrue(report.contains("[redacted]"))
        assertEquals(1,report.lines().count { it.startsWith("uptimeMs=") })
    }
    @Test fun reportCallbacksNeverEnterConnectionReport() {
        val timeline = ConnectionTimeline({ 0L })
        listOf("get_report private","set_report private","set_protocol private").forEach { timeline.record(it) }
        assertFalse(timeline.report("test").contains("private"))
    }
    @Test fun failureRemainsAvailableAcrossRetriesAndCleanup() {
        val timeline = ConnectionTimeline({ 0L })
        assertFalse(timeline.failed); timeline.failure()
        timeline.record("profile_close"); timeline.record("register_request")
        assertTrue(timeline.failed)
    }
    @Test fun newManagerTimelineDoesNotRetainPreviousProcessHistory() {
        val first = ConnectionTimeline({ 0L }); first.record("old attempt"); first.failure()
        val second = ConnectionTimeline({ 0L }); assertFalse(second.failed)
        assertFalse(second.report("test").contains("old attempt"))
    }
}
