package com.bluepad.hid

import org.junit.Assert.*
import org.junit.Test

class KeyboardReportsTest {
    @Test fun neutralAndSixKeyEncoding() {
        assertArrayEquals(ByteArray(8),KeyboardReports.encode(0,emptyList()))
        assertArrayEquals(byteArrayOf(15,0,4,5,6,7,8,9),KeyboardReports.encode(15,listOf(4,5,6,7,8,9)))
    }
    @Test fun everyModifierBitWorksWithPressAndRelease() {
        val state = KeyboardState()
        for (usage in 0xe0..0xe7) state.press(usage)
        assertEquals(255,state.report()[0].toInt() and 255)
        for (usage in 0xe0..0xe7) state.release(usage)
        assertArrayEquals(ByteArray(8),state.report())
    }
    @Test fun duplicatePressAndUnknownReleaseAreNoOps() {
        val state = KeyboardState(); state.press(4)
        assertNull(state.press(4)); assertNull(state.release(5))
        assertArrayEquals(byteArrayOf(0,0,4,0,0,0,0,0),state.report())
        state.release(4); assertNull(state.release(4)); assertArrayEquals(ByteArray(8),state.report())
    }
    @Test fun seventhKeyRejectedWithoutCorruptingSixHeldKeys() {
        val state = KeyboardState(); (4..9).forEach { state.press(it) }
        assertNull(state.press(10)); assertEquals((4..9).toList(),state.report().drop(2).map { it.toInt() })
        state.release(6); assertNotNull(state.press(10))
        assertEquals(listOf(4,5,7,8,9,10),state.report().drop(2).map { it.toInt() })
    }
    @Test fun repeatedTapAlwaysHasPressThenRelease() {
        val state = KeyboardState()
        repeat(3) {
            val reports = state.tap(UsKeyboard.character('a')!!)
            assertEquals(2,reports.size)
            assertArrayEquals(byteArrayOf(0,0,4,0,0,0,0,0),reports[0])
            assertArrayEquals(ByteArray(8),reports[1])
        }
    }
    @Test fun shortcutsPreserveMultipleStickyModifiers() {
        val state = KeyboardState(); state.modifier(KeyboardReports.CTRL,true); state.modifier(KeyboardReports.ALT,true)
        val reports = state.tap(UsKeyboard.character('C')!!)
        assertEquals(7,reports[0][0].toInt()); assertEquals(6,reports[0][2].toInt())
        assertEquals(5,reports[1][0].toInt()); assertTrue(reports[1].drop(2).all { it.toInt() == 0 })
        state.clear(); assertArrayEquals(ByteArray(8),state.report())
    }
    @Test fun ctrlCVAAndAltTabHaveCorrectUsagesAndModifiers() {
        for ((char,usage) in listOf('c' to 6,'v' to 25,'a' to 4)) {
            val state = KeyboardState(); state.modifier(1,true)
            assertArrayEquals(byteArrayOf(1,0,usage.toByte(),0,0,0,0,0),state.tap(UsKeyboard.character(char)!!)[0])
        }
        val state = KeyboardState(); state.modifier(4,true)
        assertArrayEquals(byteArrayOf(4,0,43,0,0,0,0,0),state.tap(UsKeyboard.Stroke(0x2b))[0])
    }
    @Test fun tapNeverReleasesAnAlreadyHeldKeyOrExceedsRollover() {
        val state = KeyboardState(); state.press(4)
        assertTrue(state.tap(UsKeyboard.Stroke(4)).isEmpty())
        (5..9).forEach { state.press(it) }; assertTrue(state.tap(UsKeyboard.Stroke(10)).isEmpty())
        assertEquals((4..9).toList(),state.report().drop(2).map { it.toInt() })
    }
    @Test fun allLettersNumbersAndPunctuationMapToUsPhysicalKeys() {
        for (char in 'a'..'z') {
            assertEquals(4 + (char - 'a'),UsKeyboard.character(char)!!.usage)
            assertEquals(KeyboardReports.SHIFT,UsKeyboard.character(char.uppercaseChar())!!.modifiers)
        }
        for (char in '1'..'9') assertEquals(0x1e + (char - '1'),UsKeyboard.character(char)!!.usage)
        assertEquals(0x27,UsKeyboard.character('0')!!.usage)
        val plain = "-=[]\\;'`,./"; val shifted = "_+{}|:\"~<>?"
        val usages = listOf(45,46,47,48,49,51,52,53,54,55,56)
        for (index in plain.indices) {
            assertEquals(UsKeyboard.Stroke(usages[index]),UsKeyboard.character(plain[index]))
            assertEquals(UsKeyboard.Stroke(usages[index],2),UsKeyboard.character(shifted[index]))
        }
        "!@#$%^&*()".forEachIndexed { index,char -> assertEquals(UsKeyboard.Stroke(if (index == 9) 39 else 30 + index,2),UsKeyboard.character(char)) }
        assertNull(UsKeyboard.character('é')); assertNull(UsKeyboard.character('中'))
    }
    @Test fun specialsUseStandardCodes() {
        assertEquals(listOf(44,40,42,43,41,76,80,82,81,79),UsKeyboard.special.values.toList())
    }
    @Test fun cancellationAndDisconnectClearAllKeysAndModifiersIdempotently() {
        val state = KeyboardState(); state.press(0xe0); state.press(0xe1); state.press(4); state.press(5)
        assertArrayEquals(ByteArray(8),state.clear()); assertArrayEquals(ByteArray(8),state.clear())
        assertArrayEquals(byteArrayOf(0,0,6,0,0,0,0,0),state.press(6))
    }
    @Test(expected = IllegalArgumentException::class) fun encoderRejectsSevenKeys() { KeyboardReports.encode(0,(4..10).toList()) }
    @Test(expected = IllegalArgumentException::class) fun encoderRejectsDuplicateKeys() { KeyboardReports.encode(0,listOf(4,4)) }
    @Test(expected = IllegalArgumentException::class) fun modifierMustBeOneBit() { KeyboardState().modifier(3,true) }
}
