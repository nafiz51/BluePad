package com.bluepad.hid

import org.junit.Assert.*
import org.junit.Test

class ZoomReportsTest {
    private fun sequence(os: HostOs, steps: Int) = ZoomReports.sequence(os,steps,ByteArray(8),ByteArray(4))
    @Test fun windowsLinuxSequencesCtrlWheelReleaseInBothDirections() {
        for (step in listOf(-1,1)) {
            val reports = sequence(HostOs.WINDOWS_LINUX,step)
            assertEquals(listOf(1,2,1),reports.map { it.id })
            assertArrayEquals(KeyboardReports.encode(1,emptyList()),reports[0].payload)
            assertArrayEquals(byteArrayOf(0,0,0,step.toByte()),reports[1].payload)
            assertArrayEquals(ByteArray(8),reports.last().payload)
        }
    }
    @Test fun macUsesCommandShiftEqualsForPlusAndCommandMinus() {
        val plus = sequence(HostOs.MACOS,1); val minus = sequence(HostOs.MACOS,-1)
        assertArrayEquals(KeyboardReports.encode(10,listOf(0x2e)),plus[0].payload)
        assertArrayEquals(KeyboardReports.encode(8,listOf(0x2d)),minus[0].payload)
        for (reports in listOf(plus,minus)) {
            assertEquals(listOf(1,1),reports.map { it.id }); assertArrayEquals(ByteArray(8),reports.last().payload)
        }
    }
    @Test fun activeKeysModifiersAndMouseHoldsSuppressZoomWithoutMutation() {
        val keyboard = KeyboardState(); keyboard.press(4)
        val original = keyboard.report()
        assertTrue(ZoomReports.sequence(HostOs.MACOS,1,original,ByteArray(4)).isEmpty())
        assertArrayEquals(original,keyboard.report())
        keyboard.clear(); keyboard.modifier(1,true)
        assertTrue(ZoomReports.sequence(HostOs.WINDOWS_LINUX,1,keyboard.report(),ByteArray(4)).isEmpty())
        assertTrue(ZoomReports.sequence(HostOs.MACOS,1,ByteArray(8),MouseReports.encode(2)).isEmpty())
    }
    @Test fun completedStepAndInterruptionCleanupAreNeutral() {
        val input = HidInputState()
        for (os in HostOs.entries) {
            val reports = sequence(os,4); assertArrayEquals(ByteArray(8),reports.last().payload)
            assertArrayEquals(ByteArray(8),input.releaseAll(false).last().payload)
        }
        assertTrue(sequence(HostOs.WINDOWS_LINUX,0).isEmpty())
        assertEquals(8,sequence(HostOs.MACOS,100).size)
    }
    @Test fun hostPreferenceDefaultsAndRestores() {
        assertEquals(HostOs.WINDOWS_LINUX,HostOs.restore(null))
        assertEquals(HostOs.WINDOWS_LINUX,HostOs.restore("unknown"))
        assertEquals(HostOs.MACOS,HostOs.restore("MACOS"))
    }
}
