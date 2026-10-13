package com.bluepad.bluetooth

import org.junit.Assert.*
import org.junit.Test

class ConnectionReconciliationTest {
    private fun eligible(state: Int = 2, active: Boolean = true, registered: Boolean = true,
        disconnecting: Boolean = false, paused: Boolean = false, host: String? = "host", bonded: Boolean = true) =
        ConnectionCallbacks.canReconcile(active,registered,disconnecting,paused,host,"host",bonded,state)

    @Test fun android11NeedsNoNearbyDevicesPromptButAndroid12Does() {
        assertFalse(BluetoothPermissions.requiresConnectPermission(28))
        assertFalse(BluetoothPermissions.requiresConnectPermission(30))
        assertTrue(BluetoothPermissions.requiresConnectPermission(31))
        assertTrue(BluetoothPermissions.requiresConnectPermission(36))
    }
    @Test fun alreadyConnectedProfileNeedsNoNewCallback() { assertTrue(eligible()) }
    @Test fun registeredVirtualCableCanRestoreKnownBondedHost() { assertTrue(eligible(host = null)) }
    @Test fun pairingOrConnectingAloneNeverEnablesInput() {
        for (state in listOf(0,1,3)) assertFalse(eligible(state))
    }
    @Test fun missingCallbackCanBeReconciledAtDeadlineOnlyAfterProfileSuccess() {
        val deadline = HidDeadline(); deadline.begin(HidDeadline.Operation.CONNECTION)
        assertFalse(eligible(1)); assertEquals(HidDeadline.Operation.CONNECTION,deadline.operation)
        assertTrue(eligible(2)); deadline.cancel(); assertNull(deadline.operation)
    }
    @Test fun backgroundAndLostRegistrationCannotRestoreSession() {
        assertFalse(eligible(active = false)); assertFalse(eligible(registered = false))
    }
    @Test fun explicitDisconnectAndRecoverySuppressReconciliation() {
        assertFalse(eligible(paused = true)); assertFalse(eligible(disconnecting = true))
    }
    @Test fun otherOrUnbondedHostCannotRestoreSession() {
        assertFalse(eligible(host = "other")); assertFalse(eligible(bonded = false))
    }
    @Test fun duplicateSuccessAndOldDisconnectRemainFenced() {
        assertTrue(eligible()); assertTrue(eligible())
        assertFalse(ConnectionCallbacks.accepts(1,2,true,"host","host",2,2,false,true))
        assertFalse(ConnectionCallbacks.accepts(2,2,true,"host","host",0,2,false,true))
    }
    @Test fun disconnectionDeadlineIsBoundedAndCannotBeExtendedByDuplicates() {
        val deadline = HidDeadline(); deadline.begin(HidDeadline.Operation.CONNECTION)
        assertTrue(deadline.begin(HidDeadline.Operation.DISCONNECTION))
        val revision = deadline.revision
        assertFalse(deadline.begin(HidDeadline.Operation.DISCONNECTION)); assertEquals(revision,deadline.revision)
        deadline.cancel(); assertTrue(deadline.begin(HidDeadline.Operation.CONNECTION))
    }
}
