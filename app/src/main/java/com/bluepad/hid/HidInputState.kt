package com.bluepad.hid

/** Shared cancellation contract for screen switches, disconnect, failure and shutdown. */
class HidInputState {
    data class Report(val id: Int, val payload: ByteArray)
    val mouse = MouseButtons()
    val keyboard = KeyboardState()
    fun releaseAll(boot: Boolean): List<Report> = listOf(
        Report(CompositeReports.MOUSE_ID,MouseReports.forProtocol(mouse.release(),boot)),
        Report(CompositeReports.KEYBOARD_ID,keyboard.clear())
    )
}
