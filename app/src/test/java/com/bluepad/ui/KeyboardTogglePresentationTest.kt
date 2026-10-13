package com.bluepad.ui

import org.junit.Assert.*
import org.junit.Test

class KeyboardTogglePresentationTest {
    @Test fun unchangedClosedLayoutNotificationsNeverReloadIcon() {
        val presentation = KeyboardTogglePresentation(); var reloads = 0
        repeat(1000) { if (presentation.update(false)) reloads++ }
        assertEquals(1,reloads)
    }
    @Test fun onlyActualOpenCloseTransitionsUpdateTheDrawable() {
        val presentation = KeyboardTogglePresentation(); val states = mutableListOf<Boolean>()
        for(state in listOf(false,false,true,true,true,false,false,true,true)) if (presentation.update(state)) states.add(state)
        assertEquals(listOf(false,true,false,true),states)
    }
}
