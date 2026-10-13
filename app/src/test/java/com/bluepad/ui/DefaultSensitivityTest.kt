package com.bluepad.ui

import org.junit.Assert.*
import org.junit.Test

class DefaultSensitivityTest {
    @Test fun naturalScrollingDefaultsOnAndPreservesSavedChoices() {
        assertTrue(ScrollSettings().natural)
        assertTrue(ScrollSettings.restore(emptyMap()).natural)
        for (saved in listOf(false,true)) {
            val settings = ScrollSettings.restore(mapOf("natural" to saved.toString()))
            assertEquals(saved,settings.natural)
            assertEquals(settings,ScrollSettings.restore(settings.encode()))
        }
    }
    @Test fun missingPreferencesUseNewDefaultsAndCorrectSliderPositions() {
        assertEquals(3.5f,PointerSensitivity.fromSaved(null),0f)
        assertEquals(30,PointerSensitivity.progress(PointerSensitivity.fromSaved(null)))
        assertEquals(3.5f,PointerSensitivity.value(30),0f)
        assertEquals(2.5f,ScrollSettings.restore(emptyMap()).sensitivity,0f)
        assertEquals(20,((ScrollSettings().sensitivity-0.5f)*10).toInt())
    }
    @Test fun savedValuesIncludingPreviousDefaultsRemainValid() {
        for(saved in listOf(0.5f,1f,3f,3.5f,8f)) assertEquals(saved,PointerSensitivity.fromSaved(saved),0f)
        for(saved in listOf(0.5f,1f,2.5f,3f)) {
            val settings = ScrollSettings.restore(mapOf("scroll" to saved.toString()))
            assertEquals(saved,settings.sensitivity,0f)
            assertEquals(settings,ScrollSettings.restore(settings.encode()))
        }
    }
    @Test fun themeChangesCannotChangeSavedSensitivityValues() {
        val cursor = 1.4f; val scroll = mapOf("scroll" to "1.7","natural" to "true")
        Appearance.entries.forEach {
            Appearance.oppositeDisplayed(it.isDark(false))
            assertEquals(cursor,PointerSensitivity.fromSaved(cursor),0f)
            assertEquals(ScrollSettings(true,1.7f),ScrollSettings.restore(scroll))
        }
    }
}
