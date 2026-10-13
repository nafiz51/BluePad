package com.bluepad.bluetooth
import org.junit.Assert.*
import org.junit.Test
class ConnectionCallbacksTest {
    @Test fun currentBondedHostCanConnectAndInitiateConnection() {
        assertTrue(ConnectionCallbacks.accepts(3,3,true,null,"host",1,1,false,true))
        assertTrue(ConnectionCallbacks.accepts(3,3,true,"host","host",2,2,false,true))
    }
    @Test fun staleSessionOrInactiveSessionCannotChangeConnection() {
        assertFalse(ConnectionCallbacks.accepts(2,3,true,"host","host",2,2,false,true))
        assertFalse(ConnectionCallbacks.accepts(3,3,false,"host","host",2,2,false,true))
    }
    @Test fun delayedDisconnectedCallbackCannotDisconnectCurrentlyConnectedHost() {
        assertFalse(ConnectionCallbacks.accepts(3,3,true,"host","host",0,2,false,true))
    }
    @Test fun explicitDisconnectAndUnknownHostRejectAutomaticIncomingConnections() {
        assertFalse(ConnectionCallbacks.accepts(3,3,true,null,"host",2,2,true,true))
        assertFalse(ConnectionCallbacks.accepts(3,3,true,null,"host",2,2,false,false))
        assertFalse(ConnectionCallbacks.accepts(3,3,true,"other","host",2,2,false,true))
    }
    @Test fun disconnectionCanFinishAfterUserPauseOrBondRemoval() {
        assertTrue(ConnectionCallbacks.accepts(3,3,true,"host","host",0,0,true,false))
        assertTrue(ConnectionCallbacks.accepts(3,3,true,"host","host",3,3,true,false))
    }
}
