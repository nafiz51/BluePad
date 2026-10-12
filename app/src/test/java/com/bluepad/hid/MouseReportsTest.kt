package com.bluepad.hid
import org.junit.Assert.*
import org.junit.Test
class MouseReportsTest {
    @Test fun physicalRightHoldUsesRightBitDuringMotionAndReleasesIndependently() {
        val buttons = MouseButtons()
        assertArrayEquals(byteArrayOf(2,0,0,0),buttons.change("physical",2,true))
        assertTrue(buttons.physicalHeld())
        assertArrayEquals(byteArrayOf(2,12,-3,0),buttons.move(12,-3))
        assertArrayEquals(byteArrayOf(3,0,0,0),buttons.press())
        assertArrayEquals(byteArrayOf(2,0,0,0),buttons.releasePhysical())
        assertTrue(buttons.physicalHeld())
        assertArrayEquals(byteArrayOf(0,0,0,0),buttons.change("physical",2,false))
        assertFalse(buttons.physicalHeld())
    }
    @Test fun physicalLeftRemainsDownAfterRightReleaseAndCleanupClearsBoth() {
        val buttons = MouseButtons(); buttons.press(); buttons.change("physical",2,true)
        assertArrayEquals(byteArrayOf(1,0,0,0),buttons.change("physical",2,false))
        assertArrayEquals(byteArrayOf(1,5,6,0),buttons.move(5,6))
        buttons.change("physical",2,true)
        assertArrayEquals(byteArrayOf(0,0,0,0),buttons.release())
        assertFalse(buttons.physicalHeld())
    }
    @Test fun neutralReport() { assertArrayEquals(byteArrayOf(0,0,0,0), MouseReports.encode(false)) }
    @Test fun signedCoordinatesAndClamping() { assertArrayEquals(byteArrayOf(1,127,-127,0), MouseReports.encode(true,1000,-1000)); assertArrayEquals(byteArrayOf(0,-1,1,0),MouseReports.encode(false,-1,1)) }
    @Test fun releaseClearsButtonAfterCancellationOrFailure() {
        val buttons = MouseButtons()
        assertArrayEquals(byteArrayOf(1,0,0,0),buttons.press())
        assertArrayEquals(byteArrayOf(1,12,-3,0),buttons.move(12,-3))
        assertArrayEquals(byteArrayOf(0,0,0,0),buttons.release())
        assertFalse(buttons.pressed)
        assertArrayEquals(byteArrayOf(0,2,3,0),buttons.move(2,3))
        assertArrayEquals(byteArrayOf(0,0,0,0),buttons.release())
    }
    @Test fun repeatedPressDoesNotToggle() { val buttons = MouseButtons(); buttons.press(); buttons.press(); assertTrue(buttons.pressed); buttons.release(); assertFalse(buttons.pressed) }
    @Test fun descriptorDeclaresAnUnnumberedFourByteRelativeMouse() {
        // Walk HID short items independently of the encoder: verify field sizes and semantics.
        var offset = 0
        var size = 0
        var count = 0
        var page = 0
        var minimum = 0
        var maximum = 0
        var depth = 0
        val usages = mutableListOf<Int>()
        val fields = mutableListOf<List<Int>>()
        val applications = mutableListOf<List<Int>>()
        val descriptor = MouseReports.descriptor
        while (offset < descriptor.size) {
            val prefix = descriptor[offset++].toInt() and 255
            assertNotEquals("Unexpected long item", 0xfe, prefix)
            val bytes = if ((prefix and 3) == 3) 4 else prefix and 3
            assertTrue("Truncated item", offset + bytes <= descriptor.size)
            var value = 0
            repeat(bytes) { value = value or ((descriptor[offset++].toInt() and 255) shl (8 * it)) }
            val type = (prefix shr 2) and 3
            val tag = prefix shr 4
            if (type == 1) when (tag) {
                0 -> page = value
                1 -> minimum = if (bytes == 1) value.toByte().toInt() else value
                2 -> maximum = value
                7 -> size = value
                8 -> fail("Descriptor must not declare a Report ID")
                9 -> count = value
            }
            if (type == 2 && tag == 0) usages.add(value)
            if (type == 0) {
                when (tag) {
                    8 -> fields.add(listOf(size, count, value, page, minimum, maximum) + usages)
                    10 -> { depth++; if (value == 1) applications.add(listOf(page) + usages) }
                    12 -> { depth--; assertTrue("Unbalanced collection", depth >= 0) }
                }
                usages.clear()
            }
        }
        assertEquals(0, depth)
        assertEquals(listOf(listOf(1, 2)), applications) // Generic Desktop / Mouse, no Keyboard.
        assertEquals(listOf(1, 3, 2, 9, 0, 1), fields[0]) // Three variable buttons.
        assertEquals(listOf(5, 1, 3, 9, 0, 1), fields[1]) // Constant padding.
        assertEquals(listOf(8, 3, 6, 1, -127, 127, 0x30, 0x31, 0x38), fields[2]) // Relative X/Y/wheel.
        assertEquals(3, fields.size)
        assertEquals(MouseReports.encode(false).size * 8, fields.sumOf { it[0] * it[1] })
    }
    @Test fun scrollPreservesButtonAndClampsWheel() {
        val buttons = MouseButtons()
        buttons.press()
        assertArrayEquals(byteArrayOf(1,0,0,127), buttons.scroll(500))
        assertArrayEquals(byteArrayOf(1,0,0,-127), buttons.scroll(-500))
        assertArrayEquals(byteArrayOf(0,0,0,0), buttons.release())
    }
    @Test fun bootProtocolPreservesMovementAndClickWithoutWheel() {
        assertEquals(0, MouseReports.reportId(false))
        assertEquals(2, MouseReports.reportId(true))
        val report = MouseReports.encode(true, -20, 12, 3)
        assertArrayEquals(byteArrayOf(1,-20,12), MouseReports.forProtocol(report, true))
        assertArrayEquals(byteArrayOf(1,-20,12,3), MouseReports.forProtocol(report, false))
    }
    @Test fun rightButtonAndIndependentOwnersHaveOrderedReports() {
        val buttons = MouseButtons()
        assertArrayEquals(byteArrayOf(2,0,0,0),buttons.change("gesture",2,true))
        assertArrayEquals(byteArrayOf(0,0,0,0),buttons.change("gesture",2,false))
        buttons.press()
        assertArrayEquals(byteArrayOf(3,0,0,0),buttons.change("gesture",2,true))
        assertArrayEquals(byteArrayOf(1,0,0,0),buttons.change("gesture",2,false))
        buttons.change("gesture",1,true)
        assertArrayEquals(byteArrayOf(1,0,0,0),buttons.releasePhysical())
        assertArrayEquals(byteArrayOf(0,0,0,0),buttons.change("gesture",1,false))
        buttons.press(); buttons.change("gesture",2,true)
        assertArrayEquals(byteArrayOf(0,0,0,0),buttons.release())
    }
}
