package com.bluepad.ui

import org.junit.Assert.*
import org.junit.Test

class MainUiPolicyTest {
    @Test fun appearanceDefaultsAndRestartRoundTrips() {
        assertEquals(Appearance.DARK,Appearance.restore(null))
        assertEquals(Appearance.DARK,Appearance.restore("invalid"))
        Appearance.entries.forEach { assertEquals(it,Appearance.restore(it.name)) }
    }
    @Test fun explicitThemesOverrideSystemWhileSystemTracksIt() {
        assertFalse(Appearance.LIGHT.isDark(true)); assertTrue(Appearance.DARK.isDark(false))
        assertFalse(Appearance.SYSTEM.isDark(false)); assertTrue(Appearance.SYSTEM.isDark(true))
    }
    @Test fun smallScreenPanelLeavesRoomForControlsAndTouchpad() {
        for(available in listOf(180,240,320,600)) {
            val fixed = 160
            val panel = MainUiPolicy.panelHeight(available,fixed)
            assertTrue(panel >= 0); assertTrue(panel <= available/2)
            assertTrue(available-panel >= fixed)
        }
        assertEquals(0,MainUiPolicy.panelHeight(100,160))
        assertEquals(44,MainUiPolicy.panelHeight(300,256)) // Reserve IME editor controls too.
    }
    @Test fun accessibleButtonsStayAtLeastFortyEightDp() {
        assertTrue(MainUiPolicy.TOUCH_TARGET_DP >= 48)
        assertTrue(MainUiPolicy.TOOLBAR_DP >= MainUiPolicy.TOUCH_TARGET_DP)
        assertTrue(MainUiPolicy.BUTTON_ROW_DP >= MainUiPolicy.TOUCH_TARGET_DP)
    }
    @Test fun connectedNameAndDisconnectedFeedbackAreAccurate() {
        assertEquals("Ubuntu",MainUiPolicy.hostLabel(true,"Ubuntu","Connected"))
        assertEquals("Connected",MainUiPolicy.hostLabel(true,null,"Connected"))
        assertEquals("Reconnecting",MainUiPolicy.hostLabel(false,"Ubuntu","Reconnecting"))
    }
    @Test fun keyboardToggleAndBackRemainUsableAcrossImeTransitions() {
        val state = KeyboardLayoutState()
        assertTrue(state.toggle()); assertFalse(state.observe(false))
        assertFalse(state.observe(true)); assertTrue(state.observe(false))
        state.close(); assertFalse(state.open)
        assertTrue(state.toggle()); assertFalse(state.toggle()); assertFalse(state.observe(true))
    }
}
