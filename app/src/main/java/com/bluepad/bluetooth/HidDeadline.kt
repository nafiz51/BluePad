package com.bluepad.bluetooth

/** One operation deadline; unrelated callbacks cannot cancel or extend a connection attempt. */
class HidDeadline {
    enum class Operation { PROFILE, REGISTRATION, CONNECTION, DISCONNECTION, RECOVERY }
    var operation: Operation? = null
        private set
    fun begin(next: Operation): Boolean {
        if (operation == next) return false
        operation = next
        return true
    }
    fun cancel(expected: Operation? = null): Boolean {
        if (expected != null && operation != expected) return false
        operation = null
        return true
    }
}
