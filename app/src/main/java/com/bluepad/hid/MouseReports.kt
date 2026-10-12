package com.bluepad.hid

/** Four-byte relative mouse with vertical wheel. No report ID or keyboard. */
object MouseReports {
    val descriptor = intArrayOf(
        0x05,0x01,0x09,0x02,0xA1,0x01,0x09,0x01,0xA1,0x00,
        0x05,0x09,0x19,0x01,0x29,0x03,0x15,0x00,0x25,0x01,
        0x95,0x03,0x75,0x01,0x81,0x02,0x95,0x01,0x75,0x05,0x81,0x03,
        0x05,0x01,0x09,0x30,0x09,0x31,0x09,0x38,0x15,0x81,0x25,0x7F,
        0x75,0x08,0x95,0x03,0x81,0x06,0xC0,0xC0
    ).map { it.toByte() }.toByteArray()
    fun encode(pressed: Boolean, dx: Int = 0, dy: Int = 0, wheel: Int = 0) = byteArrayOf(
        if (pressed) 1 else 0, dx.coerceIn(-127,127).toByte(), dy.coerceIn(-127,127).toByte(), wheel.coerceIn(-127,127).toByte()
    )
    /** Bluetooth Classic boot mouse has a fixed ID 2; report mode is unnumbered. */
    fun reportId(boot: Boolean) = if (boot) 2 else 0
    /** Boot mouse protocol has buttons/X/Y only; the wheel requires report protocol. */
    fun forProtocol(report: ByteArray, boot: Boolean): ByteArray {
        require(report.size == 4)
        return if (boot) report.copyOf(3) else report
    }
}

/** Failed sends clear local state; the transport attempts a neutral report or disconnects. */
class MouseButtons {
    var pressed = false
        private set
    fun press(): ByteArray { pressed = true; return MouseReports.encode(true) }
    fun release(): ByteArray { pressed = false; return MouseReports.encode(false) }
    fun scroll(wheel: Int) = MouseReports.encode(pressed, wheel = wheel)
    fun move(dx: Int, dy: Int) = MouseReports.encode(pressed, dx, dy)
}
