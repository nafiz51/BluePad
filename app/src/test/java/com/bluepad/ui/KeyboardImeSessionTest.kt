package com.bluepad.ui

import com.bluepad.bluetooth.InputSession
import org.junit.Assert.*
import org.junit.Test

class KeyboardImeSessionTest {
    @Test fun reproducedFirstTypingCloseReopenTypingWorksWithReusedEditor() {
        val s = KeyboardImeSession()
        var servedVersion = -1
        val reports = mutableListOf<ByteArray>()
        val keyboard = com.bluepad.hid.KeyboardState()
        repeat(50) {
            val v = s.requestOpen()
            assertTrue(s.activate(v,true,
                { if (servedVersion == -1) servedVersion = v; true }, // Existing IME may retain the served view.
                { servedVersion = v },
                { true }))
            assertTrue(s.accepts(servedVersion))
            if (s.accepts(servedVersion)) reports.addAll(keyboard.tap(com.bluepad.hid.UsKeyboard.Stroke(4)))
            s.close(); assertFalse(s.accepts(servedVersion))
        }
        assertEquals(100,reports.size)
        reports.chunked(2).forEach { assertEquals(4,it[0][2].toInt()); assertArrayEquals(ByteArray(8),it[1]) }
    }
    @Test fun repeatedOpenCloseRebindsAndRejectsPreviousConnections() {
        val session = KeyboardImeSession()
        var restarts = 0
        repeat(50) {
            val version = session.requestOpen()
            assertTrue(session.activate(version,true,{true},{ restarts++ },{true}))
            assertTrue(session.accepts(version)); session.close(); assertFalse(session.accepts(version))
        }
        assertEquals(50,restarts)
    }
    @Test fun focusRestartAndShowHaveExplicitOrder() {
        val s = KeyboardImeSession(); val events = mutableListOf<String>(); val v = s.requestOpen()
        assertTrue(s.activate(v,true,{events.add("focus");true},{events.add("restart")},{events.add("show");true}))
        assertEquals(listOf("focus","restart","show"),events)
        assertFalse(s.activate(v,true,{fail("No duplicate focus");true},{},{true}))
    }
    @Test fun unreadyWindowAndFailedFocusWaitForActualReadinessWithoutShowing() {
        val s = KeyboardImeSession(); val v = s.requestOpen()
        assertFalse(s.activate(v,false,{fail("No focus before layout/window ready");true},{},{true}))
        assertFalse(s.activate(v,true,{false},{fail("No restart without focus")},{true}))
        assertTrue(s.pending)
        assertTrue(s.activate(v,true,{true},{},{true})); assertFalse(s.pending)
    }
    @Test fun stalePostedShowCannotReopenClosedOrNewEditor() {
        val s = KeyboardImeSession(); val old = s.requestOpen(); s.close(); val current = s.requestOpen()
        assertFalse(s.activate(old,true,{fail("Stale show");true},{},{true}))
        assertTrue(s.activate(current,true,{true},{},{true}))
    }
    @Test fun recreationDisposesOldConnectionAndNewFieldRequiresActivation() {
        val old = KeyboardImeSession(); val oldVersion = old.requestOpen(); old.close()
        val fresh = KeyboardImeSession(); assertFalse(old.accepts(oldVersion)); assertFalse(fresh.open)
        val newVersion = fresh.requestOpen(); assertTrue(fresh.activate(newVersion,true,{true},{},{true}))
        assertTrue(fresh.accepts(newVersion)); assertFalse(old.accepts(oldVersion))
    }
    @Test fun navigationReturnRestoresFocusAndFreshConnection() {
        val s = KeyboardImeSession(); val nav = UiNavigation(); val v = s.requestOpen()
        s.activate(v,true,{true},{},{true}); nav.toggle(UiNavigation.Panel.SETTINGS)
        val pending = s.requestOpen()
        assertFalse(s.activate(pending,nav.panel == UiNavigation.Panel.MAIN,{true},{},{true}))
        nav.closePanel()
        assertTrue(s.activate(pending,nav.panel == UiNavigation.Panel.MAIN,{true},{},{true}))
        assertFalse(s.accepts(v))
    }
    @Test fun reconnectInvalidatesImeAndQueuedHidInputThenAllowsFreshTyping() {
        val ime = KeyboardImeSession(); val transport = InputSession()
        transport.acknowledge(transport.token); val captured = transport.capture()
        val old = ime.requestOpen(); ime.activate(old,true,{true},{},{true})
        transport.invalidate(); val fresh = ime.reset()
        assertFalse(ime.accepts(old)); assertFalse(transport.accepts(captured))
        transport.acknowledge(transport.token)
        assertTrue(ime.activate(fresh,true,{true},{},{true}))
        assertTrue(ime.accepts(fresh)); assertTrue(transport.accepts(transport.capture()))
    }
    @Test fun rejectedShowDoesNotCauseAnUnboundedLayoutRetryLoop() {
        val s = KeyboardImeSession(); val v = s.requestOpen()
        assertFalse(s.activate(v,true,{true},{},{false})); assertFalse(s.pending)
        assertFalse(s.activate(v,true,{fail("No repeated focus");true},{},{true}))
    }
    @Test fun resetWhileClosedNeverStartsKeyboardAndCancellationDuringRestartStopsShow() {
        val s = KeyboardImeSession(); val v = s.reset()
        assertFalse(s.pending); assertFalse(s.activate(v,true,{true},{},{true}))
        val open = s.requestOpen()
        assertFalse(s.activate(open,true,{true},{s.close()},{fail("Closed during restart");true}))
    }
}
