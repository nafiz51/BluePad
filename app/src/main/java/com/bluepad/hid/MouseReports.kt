package com.bluepad.hid

/** V4 mouse payload/standalone descriptor. CompositeReports adds ID 2 for V5A registration. */
object MouseReports {
    val descriptor = intArrayOf(
        0x05,0x01,0x09,0x02,0xA1,0x01,0x09,0x01,0xA1,0x00,
        0x05,0x09,0x19,0x01,0x29,0x03,0x15,0x00,0x25,0x01,
        0x95,0x03,0x75,0x01,0x81,0x02,0x95,0x01,0x75,0x05,0x81,0x03,
        0x05,0x01,0x09,0x30,0x09,0x31,0x09,0x38,0x15,0x81,0x25,0x7F,
        0x75,0x08,0x95,0x03,0x81,0x06,0xC0,0xC0
    ).map { it.toByte() }.toByteArray()
    fun encode(pressed: Boolean, dx: Int = 0, dy: Int = 0, wheel: Int = 0) = encode(if (pressed) 1 else 0, dx, dy, wheel)
    fun encode(buttons: Int, dx: Int = 0, dy: Int = 0, wheel: Int = 0) = byteArrayOf(
        (buttons and 7).toByte(), dx.coerceIn(-127,127).toByte(), dy.coerceIn(-127,127).toByte(), wheel.coerceIn(-127,127).toByte()
    )
    /** Legacy standalone routing; the composite transport uses CompositeReports.MOUSE_ID. */
    fun reportId(boot: Boolean) = if (boot) 2 else 0
    /** Boot mouse protocol has buttons/X/Y only; the wheel requires report protocol. */
    fun forProtocol(report: ByteArray, boot: Boolean): ByteArray {
        require(report.size == 4)
        return if (boot) report.copyOf(3) else report
    }
}

/** Failed sends clear local state; the transport attempts a neutral report or disconnects. */
class MouseButtons {
    private val owners = mutableMapOf<String, Int>()
    val mask get() = owners.values.fold(0) { result, value -> result or value }
    val pressed get() = mask and 1 != 0
    fun change(owner: String, button: Int, down: Boolean): ByteArray {
        require(button == 1 || button == 2)
        val current = owners[owner] ?: 0
        val next = if (down) current or button else current and button.inv()
        if (next == 0) owners.remove(owner) else owners[owner] = next
        return MouseReports.encode(mask)
    }
    fun physicalHeld() = (owners["physical"] ?: 0) != 0
    fun press() = change("physical",1,true)
    fun releasePhysical() = change("physical",1,false)
    /** Disconnect, shutdown and failure clear every owner. */
    fun release(): ByteArray { owners.clear(); return MouseReports.encode(0) }
    fun scroll(wheel: Int) = MouseReports.encode(mask, wheel = wheel)
    fun move(dx: Int, dy: Int) = MouseReports.encode(mask, dx, dy)
}
