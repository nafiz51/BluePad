package com.bluepad.ui

/** One touch owns a hold; unrelated fingers and duplicate cleanup cannot release it twice. */
class MouseButtonContact(private val press: () -> Unit, private val release: () -> Unit) {
    private var pointer: Int? = null
    val held get() = pointer != null
    fun down(id: Int) { if (!held) { pointer = id; press() } }
    fun up(id: Int): Boolean {
        if (pointer != id) return false
        pointer = null; release(); return true
    }
    fun cancel() { pointer?.let { up(it) } }
    fun activate() { if (!held) { press(); release() } }
}
