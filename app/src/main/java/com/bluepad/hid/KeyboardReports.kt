package com.bluepad.hid

object KeyboardReports {
    const val CTRL = 1
    const val SHIFT = 2
    const val ALT = 4
    const val SUPER = 8
    fun encode(modifiers: Int, keys: Collection<Int>): ByteArray {
        require(modifiers in 0..255 && keys.size <= 6 && keys.distinct().size == keys.size)
        require(keys.all { it in 4..0x73 })
        return ByteArray(8).apply {
            this[0] = modifiers.toByte()
            keys.forEachIndexed { index, usage -> this[index + 2] = usage.toByte() }
        }
    }
}

/** US physical-key mapping. The computer's configured layout determines actual characters. */
object UsKeyboard {
    data class Stroke(val usage: Int, val modifiers: Int = 0)
    val special = linkedMapOf("Space" to 0x2c, "Enter" to 0x28, "Bksp" to 0x2a,
        "Tab" to 0x2b, "Esc" to 0x29, "Delete" to 0x4c,
        "←" to 0x50, "↑" to 0x52, "↓" to 0x51, "→" to 0x4f)
    private const val plain = "-=[]\\;'`,./"
    private const val shifted = "_+{}|:\"~<>?"
    private val punctuationUsages = intArrayOf(0x2d,0x2e,0x2f,0x30,0x31,0x33,0x34,0x35,0x36,0x37,0x38)
    fun character(char: Char): Stroke? = when {
        char in 'a'..'z' -> Stroke(4 + (char - 'a'))
        char in 'A'..'Z' -> Stroke(4 + (char - 'A'), KeyboardReports.SHIFT)
        char in '1'..'9' -> Stroke(0x1e + (char - '1'))
        char == '0' -> Stroke(0x27)
        char == ' ' -> Stroke(0x2c)
        char == '\n' -> Stroke(0x28)
        char == '\t' -> Stroke(0x2b)
        char in plain -> Stroke(punctuationUsages[plain.indexOf(char)])
        char in shifted -> Stroke(punctuationUsages[shifted.indexOf(char)], KeyboardReports.SHIFT)
        char in "!@#$%^&*()" -> Stroke(if (char == ')') 0x27 else 0x1e + "!@#$%^&*(".indexOf(char), KeyboardReports.SHIFT)
        else -> null
    }
}

class KeyboardState {
    private val keys = linkedSetOf<Int>()
    var modifiers = 0
        private set
    fun report() = KeyboardReports.encode(modifiers,keys)
    fun press(usage: Int): ByteArray? {
        if (usage in 0xe0..0xe7) return modifier(1 shl (usage - 0xe0),true)
        require(usage in 4..0x73)
        if (usage in keys || keys.size == 6) return null // Reject seventh key, preserve current six.
        keys.add(usage); return report()
    }
    fun release(usage: Int): ByteArray? {
        if (usage in 0xe0..0xe7) return modifier(1 shl (usage - 0xe0),false)
        require(usage in 4..0x73)
        if (!keys.remove(usage)) return null
        return report()
    }
    fun modifier(bit: Int, down: Boolean): ByteArray? {
        require(bit in 1..128 && bit and (bit - 1) == 0)
        val next = if (down) modifiers or bit else modifiers and bit.inv()
        if (next == modifiers) return null
        modifiers = next; return report()
    }
    fun tap(stroke: UsKeyboard.Stroke): List<ByteArray> {
        require(stroke.usage in 4..0x73 && stroke.modifiers in 0..255)
        if (stroke.usage in keys || keys.size == 6) return emptyList()
        val original = modifiers
        modifiers = modifiers or stroke.modifiers; keys.add(stroke.usage)
        val down = report()
        keys.remove(stroke.usage); modifiers = original
        return listOf(down,report())
    }
    fun clear(): ByteArray { keys.clear(); modifiers = 0; return report() }
}
