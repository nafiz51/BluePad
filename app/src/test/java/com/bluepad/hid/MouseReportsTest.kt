package com.bluepad.hid
import org.junit.Assert.*
import org.junit.Test
class MouseReportsTest {
    @Test fun neutralReport() { assertArrayEquals(byteArrayOf(0,0,0), MouseReports.encode(false)) }
    @Test fun signedCoordinatesAndClamping() { assertArrayEquals(byteArrayOf(1,127,-127), MouseReports.encode(true,1000,-1000)); assertArrayEquals(byteArrayOf(0,-1,1),MouseReports.encode(false,-1,1)) }
    @Test fun releaseClearsButtonAfterCancellationOrFailure() {
        val buttons = MouseButtons()
        assertArrayEquals(byteArrayOf(1,0,0),buttons.press())
        assertArrayEquals(byteArrayOf(1,12,-3),buttons.move(12,-3))
        assertArrayEquals(byteArrayOf(0,0,0),buttons.release())
        assertFalse(buttons.pressed)
        assertArrayEquals(byteArrayOf(0,2,3),buttons.move(2,3))
        assertArrayEquals(byteArrayOf(0,0,0),buttons.release())
    }
    @Test fun repeatedPressDoesNotToggle() { val buttons = MouseButtons(); buttons.press(); buttons.press(); assertTrue(buttons.pressed); buttons.release(); assertFalse(buttons.pressed) }
}
