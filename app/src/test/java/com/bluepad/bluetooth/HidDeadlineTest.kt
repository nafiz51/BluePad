package com.bluepad.bluetooth
import org.junit.Assert.*
import org.junit.Test
class HidDeadlineTest {
    @Test fun registrationCallbackCannotCancelConnectingDeadline() {
        val d = HidDeadline(); d.begin(HidDeadline.Operation.CONNECTION)
        assertFalse(d.cancel(HidDeadline.Operation.REGISTRATION))
        assertEquals(HidDeadline.Operation.CONNECTION,d.operation)
    }
    @Test fun repeatedConnectingCallbacksCannotExtendTheDeadline() {
        val d = HidDeadline(); assertTrue(d.begin(HidDeadline.Operation.CONNECTION))
        assertFalse(d.begin(HidDeadline.Operation.CONNECTION))
    }
    @Test fun incomingConnectionCanStartDeadlineWithoutExplicitConnectRequest() {
        val d = HidDeadline(); assertTrue(d.begin(HidDeadline.Operation.CONNECTION))
        assertTrue(d.cancel()); assertNull(d.operation)
    }
    @Test fun matchingRegistrationCallbackCancelsOnlyItsOwnDeadline() {
        val d = HidDeadline(); d.begin(HidDeadline.Operation.REGISTRATION)
        assertTrue(d.cancel(HidDeadline.Operation.REGISTRATION)); assertNull(d.operation)
    }
    @Test fun recoveryAndDisconnectReplaceConnectionDeadlineAndCleanupClearsIt() {
        val d = HidDeadline(); d.begin(HidDeadline.Operation.CONNECTION)
        assertTrue(d.begin(HidDeadline.Operation.DISCONNECTION))
        assertTrue(d.begin(HidDeadline.Operation.RECOVERY))
        assertFalse(d.cancel(HidDeadline.Operation.REGISTRATION))
        d.cancel(); assertTrue(d.begin(HidDeadline.Operation.PROFILE))
    }
}
