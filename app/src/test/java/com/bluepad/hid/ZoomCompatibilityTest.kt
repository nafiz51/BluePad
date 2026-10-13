package com.bluepad.hid

import org.junit.Assert.*
import org.junit.Test

class ZoomCompatibilityTest {
    @Test fun legacyMacPreservesCommandZoom() {
        assertEquals(ZoomCompatibility.MACOS_COMPATIBILITY,ZoomCompatibility.restore(null,"MACOS"))
    }
    @Test fun legacyStandardMissingAndInvalidDefaultToCtrlWheel() {
        for (legacy in listOf(null,"WINDOWS_LINUX","unknown"))
            assertEquals(ZoomCompatibility.STANDARD,ZoomCompatibility.restore(null,legacy))
    }
    @Test fun explicitNewSelectionWinsOverLegacyAndSurvivesRestart() {
        for (setting in ZoomCompatibility.entries) {
            val restored = ZoomCompatibility.restore(setting.name,"MACOS")
            assertEquals(setting,restored)
            assertEquals(setting,ZoomCompatibility.restore(restored.name))
        }
        assertEquals(ZoomCompatibility.STANDARD,ZoomCompatibility.restore("invalid","MACOS"))
    }
    @Test fun migrationPreservesExactLegacyReportSequences() {
        val neutralKeyboard = ByteArray(8); val neutralMouse = ByteArray(4)
        val standard = ZoomReports.sequence(ZoomCompatibility.restore(null,"WINDOWS_LINUX"),1,neutralKeyboard,neutralMouse)
        assertArrayEquals(KeyboardReports.encode(1,emptyList()),standard.first().payload)
        assertEquals(listOf(1,2,1),standard.map { it.id })
        val mac = ZoomReports.sequence(ZoomCompatibility.restore(null,"MACOS"),1,neutralKeyboard,neutralMouse)
        assertArrayEquals(KeyboardReports.encode(10,listOf(0x2e)),mac.first().payload)
        assertArrayEquals(ByteArray(8),mac.last().payload)
    }
}
