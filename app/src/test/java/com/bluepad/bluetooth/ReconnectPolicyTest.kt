package com.bluepad.bluetooth

import org.junit.Assert.*
import org.junit.Test

class ReconnectPolicyTest {
    @Test fun reopeningCanAttemptImmediatelyAndSuccessResetsBudget() {
        val p = ReconnectPolicy(); assertEquals(0L,p.reserve(true,true)); assertTrue(p.begin())
        p.success(); assertEquals(0,p.attempts); assertFalse(p.connecting); assertFalse(p.pending)
    }
    @Test fun temporaryDisconnectionHasExactlyThreeBackoffs() {
        val p = ReconnectPolicy()
        for (delay in listOf(2000L,4000L,8000L)) {
            assertEquals(delay,p.reserve(true)); assertTrue(p.begin()); p.failed()
        }
        assertNull(p.reserve(true)); assertEquals(3,p.attempts)
    }
    @Test fun timeoutOrRejectedRequestConsumesBudgetWithoutInfiniteRetry() {
        val p = ReconnectPolicy(); p.reserve(true,true); p.begin(); p.failed()
        assertEquals(2000L,p.reserve(true)); p.begin(); p.failed()
        assertEquals(4000L,p.reserve(true)); p.begin(); p.failed(); assertNull(p.reserve(true))
    }
    @Test fun duplicatePendingOrInFlightAttemptIsRejected() {
        val p = ReconnectPolicy(); p.reserve(true); assertNull(p.reserve(true))
        assertTrue(p.begin()); assertFalse(p.begin()); assertNull(p.reserve(true))
    }
    @Test fun explicitDisconnectPersistsSuppressionAcrossForegrounds() {
        val p = ReconnectPolicy(); p.reserve(true); p.disconnect(); p.foreground()
        assertNull(p.reserve(true,true)); assertFalse(p.begin())
        p.manual(); assertFalse(p.paused); assertTrue(p.connecting)
    }
    @Test fun disabledBluetoothPermissionOrUnknownHostCannotReserve() {
        val p = ReconnectPolicy(); assertNull(p.reserve(false))
        p.enabled=false; assertNull(p.reserve(true)); p.enabled=true
        assertEquals(2000L,p.reserve(true))
    }
    @Test fun retryCancellationInvalidatesItsPendingAttempt() {
        val p = ReconnectPolicy(); p.reserve(true); p.cancelPending(); assertFalse(p.begin())
        assertEquals(2000L,p.reserve(true)); p.cancel(); assertFalse(p.begin())
    }
    @Test fun incomingConnectionCancelsDuplicateOutgoingRetry() {
        val p = ReconnectPolicy(); p.reserve(true); p.incoming()
        assertFalse(p.begin()); assertNull(p.reserve(true)); p.success(); assertFalse(p.connecting)
    }
    @Test fun interruptionStartsFreshBoundedEpisodeWithoutClearingUserIntent() {
        val p = ReconnectPolicy(); p.reserve(true); p.begin(); p.cancel(); p.foreground()
        assertEquals(0,p.attempts); assertEquals(0L,p.reserve(true,true))
    }
}
