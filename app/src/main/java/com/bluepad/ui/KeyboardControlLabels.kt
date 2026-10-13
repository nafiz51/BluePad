package com.bluepad.ui

import com.bluepad.R

/** Spoken names for the existing abbreviated/symbol-only special-key controls. */
object KeyboardControlLabels {
    fun special(usage: Int): Int? = when(usage) {
        0x2a -> R.string.key_backspace
        0x29 -> R.string.key_escape
        0x50 -> R.string.key_left
        0x52 -> R.string.key_up
        0x4f -> R.string.key_right
        0x51 -> R.string.key_down
        else -> null // Other controls already have complete visible names.
    }
}
