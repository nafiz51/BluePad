package com.bluepad.bluetooth

/** Foreground-only bounded attempts; Android adapters/timers remain in HidManager. */
class ReconnectPolicy(var enabled: Boolean = true, var paused: Boolean = false) {
    private var immediateSeries = false
    var attempts = 0
        private set
    var connecting = false
        private set
    var pending = false
        private set
    fun reserve(eligible: Boolean, immediate: Boolean = false): Long? {
        if (!eligible || !enabled || paused || connecting || pending || attempts >= 3) return null
        pending = true
        if (immediate && attempts == 0) { immediateSeries = true; return 0L }
        val index = if (immediateSeries) (attempts-1).coerceAtLeast(0) else attempts
        return listOf(2000L,4000L,8000L)[index]
    }
    fun begin(): Boolean {
        if (!pending || !enabled || paused) return false
        pending = false; connecting = true; attempts++; return true
    }
    fun incoming() { pending = false; connecting = true }
    fun failed() { connecting = false }
    fun success() { cancel(); attempts = 0; immediateSeries = false }
    fun manual() { cancel(); attempts = 0; immediateSeries = false; paused = false; connecting = true }
    fun disconnect() { paused = true; cancel() }
    fun cancelPending() { pending = false }
    fun cancel() { pending = false; connecting = false }
    fun foreground() { cancel(); attempts = 0; immediateSeries = false }
}
