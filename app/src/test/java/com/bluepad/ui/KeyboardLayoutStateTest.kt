package com.bluepad.ui

import org.junit.Assert.*
import org.junit.Test

class KeyboardLayoutStateTest {
    @Test fun startsWithTouchpadAndWaitsForImeShow() {
        val state = KeyboardLayoutState()
        assertFalse(state.open)
        assertTrue(state.toggle())
        assertFalse(state.observe(false))
        assertTrue(state.open)
    }
    @Test fun externalDismissalIsDetectedAfterActualVisibility() {
        val state = KeyboardLayoutState(); state.toggle()
        assertFalse(state.observe(true))
        assertTrue(state.observe(false))
        state.close(); assertFalse(state.open)
    }
    @Test fun secondToggleAndLifecycleCloseResetVisibilityHistory() {
        val state = KeyboardLayoutState(); state.toggle(); state.observe(true)
        assertFalse(state.toggle()); assertTrue(state.toggle())
        assertFalse(state.observe(false))
        state.close(); assertTrue(state.toggle()); assertFalse(state.observe(false))
    }
    @Test fun unrequestedVisibilityDoesNotOpenKeyboardControls() {
        val state = KeyboardLayoutState()
        assertFalse(state.observe(true)); assertFalse(state.open)
    }
}
