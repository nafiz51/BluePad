package com.bluepad.ui

import com.bluepad.R
import com.bluepad.hid.UsKeyboard
import org.junit.Assert.*
import org.junit.Test

class KeyboardControlLabelsTest {
    @Test fun abbreviatedAndArrowControlsHaveExplicitSpokenNames() {
        val expected = mapOf("Bksp" to R.string.key_backspace,"Esc" to R.string.key_escape,
            "←" to R.string.key_left,"↑" to R.string.key_up,"→" to R.string.key_right,"↓" to R.string.key_down)
        expected.forEach { (label,resource) -> assertEquals(resource,KeyboardControlLabels.special(UsKeyboard.special.getValue(label))) }
    }
    @Test fun completeVisibleNamesDoNotGetRedundantDescriptions() {
        for(label in listOf("Space","Enter","Tab","Delete")) assertNull(KeyboardControlLabels.special(UsKeyboard.special.getValue(label)))
    }
}
