package com.bluepad.hid

import org.junit.Assert.*
import org.junit.Test

class ZoomReportsTest {
    private fun sequence(compatibility: ZoomCompatibility, steps: Int) = ZoomReports.sequence(compatibility,steps,ByteArray(8),ByteArray(4))
    @Test fun standardSequencesCtrlWheelReleaseInBothDirections() {
        for (step in listOf(-1,1)) {
            val reports = sequence(ZoomCompatibility.STANDARD,step)
            assertEquals(listOf(1,2,1),reports.map { it.id })
            assertArrayEquals(KeyboardReports.encode(1,emptyList()),reports[0].payload)
            assertArrayEquals(byteArrayOf(0,0,0,step.toByte()),reports[1].payload)
            assertArrayEquals(ByteArray(8),reports.last().payload)
        }
    }
    @Test fun macUsesCommandShiftEqualsForPlusAndCommandMinus() {
        val plus = sequence(ZoomCompatibility.MACOS_COMPATIBILITY,1); val minus = sequence(ZoomCompatibility.MACOS_COMPATIBILITY,-1)
        assertArrayEquals(KeyboardReports.encode(10,listOf(0x2e)),plus[0].payload)
        assertArrayEquals(KeyboardReports.encode(8,listOf(0x2d)),minus[0].payload)
        for (reports in listOf(plus,minus)) {
            assertEquals(listOf(1,1),reports.map { it.id }); assertArrayEquals(ByteArray(8),reports.last().payload)
        }
    }
    @Test fun activeKeysModifiersAndMouseHoldsSuppressZoomWithoutMutation() {
        val keyboard = KeyboardState(); keyboard.press(4)
        val original = keyboard.report()
        assertTrue(ZoomReports.sequence(ZoomCompatibility.MACOS_COMPATIBILITY,1,original,ByteArray(4)).isEmpty())
        assertArrayEquals(original,keyboard.report())
        keyboard.clear(); keyboard.modifier(1,true)
        assertTrue(ZoomReports.sequence(ZoomCompatibility.STANDARD,1,keyboard.report(),ByteArray(4)).isEmpty())
        assertTrue(ZoomReports.sequence(ZoomCompatibility.MACOS_COMPATIBILITY,1,ByteArray(8),MouseReports.encode(2)).isEmpty())
    }
    @Test fun completedStepAndInterruptionCleanupAreNeutral() {
        val input = HidInputState()
        for (compatibility in ZoomCompatibility.entries) {
            val reports = sequence(compatibility,4); assertArrayEquals(ByteArray(8),reports.last().payload)
            assertArrayEquals(ByteArray(8),input.releaseAll(false).last().payload)
        }
        assertTrue(sequence(ZoomCompatibility.STANDARD,0).isEmpty())
        assertEquals(8,sequence(ZoomCompatibility.MACOS_COMPATIBILITY,100).size)
    }
    @Test fun zoomPreferenceDefaultsAndRestores() {
        assertEquals(ZoomCompatibility.STANDARD,ZoomCompatibility.restore(null))
        assertEquals(ZoomCompatibility.STANDARD,ZoomCompatibility.restore("unknown"))
        assertEquals(ZoomCompatibility.MACOS_COMPATIBILITY,ZoomCompatibility.restore("MACOS_COMPATIBILITY"))
    }
}
