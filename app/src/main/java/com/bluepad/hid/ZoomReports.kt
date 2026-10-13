package com.bluepad.hid

enum class HostOs { WINDOWS_LINUX, MACOS;
    companion object { fun restore(value: String?) = entries.firstOrNull { it.name == value } ?: WINDOWS_LINUX }
}

/** Temporary shortcuts never mutate user-owned keyboard state. Execute the entire list serially. */
object ZoomReports {
    fun sequence(os: HostOs, steps: Int, keyboard: ByteArray, mouse: ByteArray): List<HidInputState.Report> {
        require(keyboard.size == 8 && mouse.size == 4)
        if (steps == 0 || keyboard.any { it.toInt() != 0 } || mouse[0].toInt() != 0) return emptyList()
        val count = steps.coerceIn(-4,4)
        val neutral = HidInputState.Report(CompositeReports.KEYBOARD_ID,ByteArray(8))
        return if (os == HostOs.WINDOWS_LINUX) listOf(
            HidInputState.Report(CompositeReports.KEYBOARD_ID,KeyboardReports.encode(KeyboardReports.CTRL,emptyList())),
            HidInputState.Report(CompositeReports.MOUSE_ID,MouseReports.encode(0,wheel=count)),neutral
        ) else buildList {
            repeat(kotlin.math.abs(count)) {
                val stroke = UsKeyboard.character(if (count > 0) '+' else '-')!!
                add(HidInputState.Report(CompositeReports.KEYBOARD_ID,KeyboardReports.encode(KeyboardReports.SUPER or stroke.modifiers,listOf(stroke.usage))))
                add(neutral)
            }
        }
    }
}
