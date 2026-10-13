package com.bluepad.ui

/** Avoid resource reload/invalidation and callbacks for unchanged inset/layout notifications. */
class KeyboardTogglePresentation {
    private var displayed: Boolean? = null
    fun update(open: Boolean): Boolean {
        if (displayed == open) return false
        displayed = open
        return true
    }
}
