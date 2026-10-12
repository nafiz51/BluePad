package com.bluepad.hid

import org.junit.Assert.*
import org.junit.Test

class CompositeReportsTest {
    @Test fun descriptorIdsApplicationsAndLengthsAreConsistent() {
        var offset = 0; var id = 0; var size = 0; var count = 0; var page = 0; var usage = 0; var depth = 0
        val input = mutableMapOf<Int,Int>(); val output = mutableMapOf<Int,Int>()
        val ids = mutableListOf<Int>(); val applications = mutableListOf<Pair<Int,Int>>()
        val bytes = CompositeReports.descriptor
        while (offset < bytes.size) {
            val prefix = bytes[offset++].toInt() and 255
            assertNotEquals(0xfe,prefix)
            val length = if (prefix and 3 == 3) 4 else prefix and 3
            assertTrue(offset + length <= bytes.size)
            var value = 0; repeat(length) { value = value or ((bytes[offset++].toInt() and 255) shl (it * 8)) }
            when (prefix and 0xfc) {
                0x04 -> page = value
                0x08 -> usage = value
                0x74 -> size = value
                0x94 -> count = value
                0x84 -> { id = value; ids.add(id); assertTrue(id > 0) }
                0x80 -> { assertTrue(id > 0); input[id] = (input[id] ?: 0) + size * count }
                0x90 -> { assertTrue(id > 0); output[id] = (output[id] ?: 0) + size * count }
                0xa0 -> { depth++; if (value == 1) applications.add(page to usage) }
                0xc0 -> { depth--; assertTrue(depth >= 0) }
            }
        }
        assertEquals(0,depth); assertEquals(listOf(2,1),ids)
        assertEquals(listOf(1 to 2,1 to 6),applications)
        assertEquals(mapOf(2 to 32,1 to 64),input); assertEquals(mapOf(1 to 8),output)
    }
    @Test fun existingMouseDescriptorFieldsArePreservedByteForByte() {
        val mouse = CompositeReports.descriptor.take(MouseReports.descriptor.size + 2).toByteArray()
        val withoutId = mouse.take(6).toByteArray() + mouse.drop(8).toByteArray()
        assertArrayEquals(MouseReports.descriptor,withoutId)
        assertArrayEquals(byteArrayOf(0x85.toByte(),2),mouse.copyOfRange(6,8))
    }
    @Test fun reportRoutingAndBootLengthsMatchPayloads() {
        val mouse = MouseReports.encode(true,3,-5,1); val keys = KeyboardReports.encode(3,listOf(6))
        assertArrayEquals(mouse,CompositeReports.input(2,false,mouse,keys))
        assertArrayEquals(byteArrayOf(1,3,-5),CompositeReports.input(2,true,mouse,keys))
        assertArrayEquals(keys,CompositeReports.input(1,false,mouse,keys))
        assertArrayEquals(keys,CompositeReports.input(1,true,mouse,keys))
        assertNull(CompositeReports.input(0,false,mouse,keys)); assertNull(CompositeReports.input(3,false,mouse,keys))
    }
    @Test fun ledOutputValidatesIdAndOneByteLength() {
        assertEquals(31,CompositeReports.leds(1,byteArrayOf(0xff.toByte())))
        assertEquals(2,CompositeReports.leds(1,byteArrayOf(2)))
        assertNull(CompositeReports.leds(2,byteArrayOf(0)))
        assertNull(CompositeReports.leds(1,byteArrayOf()))
        assertNull(CompositeReports.leds(1,byteArrayOf(0,0)))
    }
    @Test fun switchingScreensAndDisconnectClearBothDevicesInReportAndBootModes() {
        for (boot in listOf(false,true)) {
            val inputs = HidInputState()
            inputs.mouse.change("gesture",1,true)
            inputs.keyboard.press(0xe0); inputs.keyboard.press(6)
            val reports = inputs.releaseAll(boot) // Exact manager screen-switch/cleanup contract.
            assertEquals(listOf(2,1),reports.map { it.id })
            assertArrayEquals(ByteArray(if (boot) 3 else 4),reports[0].payload)
            assertArrayEquals(ByteArray(8),reports[1].payload)
            assertFalse(inputs.mouse.pressed); assertEquals(0,inputs.keyboard.modifiers)
            assertArrayEquals(MouseReports.encode(false,2,3),inputs.mouse.move(2,3))
            assertEquals(4,inputs.keyboard.tap(UsKeyboard.Stroke(4))[0][2].toInt())
        }
    }
    @Test fun independentAxesAreEncodedAfterButtonsWithoutAnEmbeddedId() {
        assertArrayEquals(byteArrayOf(0,40,0,0),CompositeReports.input(2,false,MouseReports.encode(false,40,0),ByteArray(8)))
        assertArrayEquals(byteArrayOf(0,0,-40,0),CompositeReports.input(2,false,MouseReports.encode(false,0,-40),ByteArray(8)))
        // A legacy unnumbered host sees the Bluetooth Report ID as its first payload byte.
        val numbered = byteArrayOf(2) + MouseReports.encode(false,40,0)
        assertEquals(0,numbered[1].toInt()) // Cached host's X reads buttons, not X.
        assertEquals(40,numbered[2].toInt()) // Cached host's Y reads actual X.
    }
}
