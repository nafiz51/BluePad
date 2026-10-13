package com.bluepad.bluetooth

/** One operation deadline; unrelated callbacks cannot cancel or extend a connection attempt. */
class HidDeadline {
    enum class Operation { PROFILE, REGISTRATION, CONNECTION, DISCONNECTION, RECOVERY }
    var revision = 0
        private set
    var operation: Operation? = null
        private set
    fun begin(next: Operation): Boolean {
        if (operation == next) return false
        revision++
        operation = next
        return true
    }
    fun cancel(expected: Operation? = null): Boolean {
        if (expected != null && operation != expected) return false
        revision++
        operation = null
        return true
    }
}
