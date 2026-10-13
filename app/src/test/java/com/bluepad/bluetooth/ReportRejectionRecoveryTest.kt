package com.bluepad.bluetooth

import com.bluepad.hid.HidInputState
import org.junit.Assert.*
import org.junit.Test

class ReportRejectionRecoveryTest {
    private class Harness {
        val session = InputSession()
        val inputs = HidInputState()
        val deadline = HidDeadline()
        val retry = ReconnectPolicy()
        val events = mutableListOf<String>()
        var recovering = false
        var connected = true
        fun reject(accepted: Boolean) = ReportRejectionRecovery.run(recovering,{
            recovering = true; session.invalidate(); inputs.releaseAll(false); connected = false
            deadline.begin(HidDeadline.Operation.RECOVERY); events.add("publish failed")
        },{ events.add("neutral") },{ events.add("disconnect"); accepted },{
            finish(); events.add("rejected cleanup")
        })
        fun finish() { session.invalidate(); inputs.releaseAll(false); deadline.cancel(); recovering = false; retry.failed() }
    }
    @Test fun rejectedReportFencesAndPublishesBeforeTransport() {
        val h = Harness(); h.session.acknowledge(h.session.token); val old = h.session.capture()
        h.inputs.mouse.press(); h.inputs.keyboard.press(0xe0); h.inputs.keyboard.press(6)
        h.reject(true)
        assertFalse(h.connected); assertFalse(h.session.accepts(old))
        assertEquals(0,h.inputs.mouse.mask); assertArrayEquals(ByteArray(8),h.inputs.keyboard.report())
        assertEquals(listOf("publish failed","neutral","disconnect"),h.events)
        assertEquals(HidDeadline.Operation.RECOVERY,h.deadline.operation)
    }
    @Test fun rejectedDisconnectFinishesWithoutWaitingForCallback() {
        val h = Harness(); h.reject(false)
        assertFalse(h.recovering); assertNull(h.deadline.operation)
        assertEquals("rejected cleanup",h.events.last())
    }
    @Test fun repeatedRejectionDoesNotDuplicateOrExtendRecovery() {
        val h = Harness(); h.reject(true); val revision = h.deadline.revision
        h.reject(true)
        assertEquals(3,h.events.size); assertEquals(revision,h.deadline.revision)
    }
    @Test fun missingCallbackLeavesExistingDeadlineArmedUntilCleanup() {
        val h = Harness(); h.reject(true)
        assertFalse(h.deadline.cancel(HidDeadline.Operation.REGISTRATION))
        h.finish(); assertFalse(h.recovering); assertNull(h.deadline.operation)
        assertEquals(2000L,h.retry.reserve(true))
    }
    @Test fun callbackOrProfileLossCleanupFencesOldInputs() {
        val h = Harness(); h.session.acknowledge(h.session.token); val old = h.session.capture()
        h.reject(true); h.finish(); h.session.acknowledge(h.session.token)
        assertFalse(h.session.accepts(old)); assertTrue(h.session.accepts(h.session.capture()))
    }
    @Test fun explicitDisconnectSuppressesRetryAfterRecovery() {
        val h = Harness(); h.reject(true); h.retry.disconnect(); h.finish()
        assertNull(h.retry.reserve(true)); assertTrue(h.retry.paused)
    }
    @Test fun repeatedRecoveryFailuresKeepThreeAttemptLimit() {
        val h = Harness()
        repeat(3) { h.reject(false); assertNotNull(h.retry.reserve(true)); assertTrue(h.retry.begin()) }
        h.reject(false); assertNull(h.retry.reserve(true)); assertEquals(3,h.retry.attempts)
    }
}
