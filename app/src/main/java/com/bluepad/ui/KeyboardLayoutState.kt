package com.bluepad.ui

/** Ignore hidden insets while a show request is pending; observe real dismissal after opening. */
class KeyboardLayoutState {
    var open = false
        private set
    private var observedVisible = false
    fun toggle(): Boolean {
        if (open) close() else open = true
        return open
    }
    fun observe(visible: Boolean): Boolean {
        if (!open) return false
        if (visible) observedVisible = true
        return observedVisible && !visible
    }
    fun close() { open = false; observedVisible = false }
}
