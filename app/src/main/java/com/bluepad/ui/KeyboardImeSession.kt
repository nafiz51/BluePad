package com.bluepad.ui

/** Owns editor generations and pending IME activation, independently of Bluetooth. */
class KeyboardImeSession {
    var version = 0
        private set
    var open = false
        private set
    var pending = false
        private set
    fun requestOpen(): Int { version++; open = true; pending = true; return version }
    fun reset(): Int { version++; pending = open; return version }
    fun close() { version++; open = false; pending = false }
    fun accepts(connectionVersion: Int) = open && connectionVersion == version
    /** Focus/restart/show are executed in order only when layout and window are ready. */
    fun activate(requestVersion: Int, ready: Boolean, focus: () -> Boolean, restart: () -> Unit, show: () -> Boolean): Boolean {
        if (!accepts(requestVersion) || !pending || !ready || !focus()) return false
        restart()
        // restart may synchronously invalidate/recreate input; never show a canceled request.
        if (!accepts(requestVersion)) return false
        pending = false // One attempt per real activation request; no layout-driven retry loop.
        return show()
    }
}
