package com.bluepad.ui

import org.junit.Assert.*
import org.junit.Test

class AppearanceToggleTest {
    @Test fun freshInstallIsAlwaysMidnightRegardlessOfSystemTheme() {
        for(systemDark in listOf(false,true)) assertTrue(Appearance.restore(null).isDark(systemDark))
    }
    @Test fun existingExplicitAndSystemPreferencesSurviveUpdatesAndRestarts() {
        Appearance.entries.forEach { saved -> assertEquals(saved,Appearance.restore(saved.name)) }
        assertFalse(Appearance.restore("SYSTEM").isDark(false))
        assertTrue(Appearance.restore("SYSTEM").isDark(true))
    }
    @Test fun systemToggleSavesOppositeOfDisplayedTheme() {
        assertEquals(Appearance.DARK,Appearance.oppositeDisplayed(false))
        assertEquals(Appearance.LIGHT,Appearance.oppositeDisplayed(true))
    }
    @Test fun repeatedToggleRemainsExplicitAndPersistent() {
        var appearance = Appearance.DARK
        repeat(20) {
            appearance = Appearance.oppositeDisplayed(appearance.isDark(it%2 == 0))
            assertEquals(if(it%2 == 0) Appearance.LIGHT else Appearance.DARK,appearance)
            assertEquals(appearance,Appearance.restore(appearance.name))
        }
    }
}
