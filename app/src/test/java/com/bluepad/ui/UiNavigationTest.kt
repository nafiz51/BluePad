package com.bluepad.ui

import org.junit.Assert.*
import org.junit.Test

class UiNavigationTest {
    @Test fun backClosesEitherPanelWithoutExiting() {
        for(p in listOf(UiNavigation.Panel.SETTINGS,UiNavigation.Panel.CONNECTION)) {
            val n = UiNavigation(); n.toggle(p)
            assertEquals(UiNavigation.Back.CLOSE_PANEL,n.back(false)); n.closePanel()
            assertEquals(UiNavigation.Back.EXIT,n.back(false))
        }
    }
    @Test fun imeDismissalPrecedesPanelNavigation() {
        val n = UiNavigation(UiNavigation.Panel.SETTINGS)
        assertEquals(UiNavigation.Back.DISMISS_IME,n.back(true))
        assertEquals(UiNavigation.Panel.SETTINGS,n.panel)
        assertEquals(UiNavigation.Back.CLOSE_PANEL,n.back(false))
    }
    @Test fun panelSwitchingAndRotationRestoreHaveOneOwner() {
        val n = UiNavigation(); n.toggle(UiNavigation.Panel.SETTINGS); n.toggle(UiNavigation.Panel.CONNECTION)
        assertEquals(UiNavigation.Panel.CONNECTION,n.panel)
        assertEquals(n.panel,UiNavigation.restore(n.panel.name).panel)
        n.toggle(UiNavigation.Panel.CONNECTION); assertEquals(UiNavigation.Panel.MAIN,n.panel)
        assertEquals(UiNavigation.Panel.MAIN,UiNavigation.restore("bad").panel)
    }
    @Test fun keyboardViewportReservesControlsAndSupportsSmallImeWindows() {
        assertEquals(140,MainUiPolicy.keyboardHeight(600,144,140,48))
        assertEquals(96,MainUiPolicy.keyboardHeight(240,144,140,48))
        assertEquals(48,MainUiPolicy.keyboardHeight(100,144,140,48))
        assertEquals(0,MainUiPolicy.panelHeight(-10,144))
    }
}
