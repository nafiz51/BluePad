package com.bluepad.hid

import org.junit.Assert.*
import org.junit.Test

/** Decode actual descriptor fields, including signed ranges and reset of local usages. */
class DescriptorFieldsTest {
    private data class Field(val id: Int, val type: Int, val page: Int, val usages: List<Int>,
        val minimumUsage: Int?, val maximumUsage: Int?, val size: Int, val count: Int,
        val minimum: Int, val maximum: Int, val flags: Int)
    private fun fields(): List<Field> {
        val bytes = CompositeReports.descriptor
        var offset = 0; var id = 0; var page = 0; var size = 0; var count = 0
        var minimum = 0; var maximum = 0; var depth = 0
        val usages = mutableListOf<Int>(); var lo: Int? = null; var hi: Int? = null
        val fields = mutableListOf<Field>()
        while (offset < bytes.size) {
            val prefix = bytes[offset++].toInt() and 255
            assertNotEquals(0xfe,prefix)
            val length = when (prefix and 3) { 3 -> 4; else -> prefix and 3 }
            assertTrue(offset + length <= bytes.size)
            var value = 0; repeat(length) { value = value or ((bytes[offset++].toInt() and 255) shl (it * 8)) }
            fun signed(): Int = if (length == 0) 0 else (value shl (32-length*8)) shr (32-length*8)
            when (prefix and 0xfc) {
                0x04 -> page = value
                0x08 -> usages.add(value)
                0x18 -> lo = value
                0x28 -> hi = value
                0x14 -> minimum = signed()
                0x24 -> maximum = if (minimum < 0) signed() else value
                0x74 -> size = value
                0x94 -> count = value
                0x84 -> { assertTrue(value in 1..255); id = value }
                0x80,0x90 -> {
                    assertTrue(depth > 0)
                    fields.add(Field(id,prefix and 0xfc,page,usages.toList(),lo,hi,size,count,minimum,maximum,value))
                }
                0xa0 -> depth++
                0xc0 -> { depth--; assertTrue(depth >= 0) }
                else -> fail("Unexpected item $prefix")
            }
            if (prefix and 0x0c == 0) { usages.clear(); lo = null; hi = null }
        }
        assertEquals(0,depth)
        return fields
    }
    @Test fun mouseFieldsHaveThreeButtonsPaddingAndSignedRelativeXYWheel() {
        val f = fields().filter { it.id == 2 }
        assertEquals(3,f.size)
        assertEquals(Field(2,0x80,9,emptyList(),1,3,1,3,0,1,2),f[0])
        assertEquals(5,f[1].size); assertEquals(1,f[1].count); assertEquals(3,f[1].flags)
        assertEquals(Field(2,0x80,1,listOf(0x30,0x31,0x38),null,null,8,3,-127,127,6),f[2])
    }
    @Test fun keyboardFieldsDefineEightModifiersReservedByteSixKeyArrayAndLedOutput() {
        val f = fields().filter { it.id == 1 }
        assertEquals(5,f.size)
        assertEquals(Field(1,0x80,7,emptyList(),0xe0,0xe7,1,8,0,1,2),f[0])
        assertEquals(8,f[1].size); assertEquals(1,f[1].count); assertEquals(3,f[1].flags)
        assertEquals(Field(1,0x90,8,emptyList(),1,5,1,5,0,1,2),f[2])
        assertEquals(3,f[3].size); assertEquals(1,f[3].count); assertEquals(3,f[3].flags)
        assertEquals(Field(1,0x80,7,emptyList(),0,0x73,8,6,0,0x73,0),f[4])
    }
    @Test fun encodedNeutralAndBoundaryReportsMatchDeclaredBitLengths() {
        val f = fields()
        for ((id,payload) in listOf(2 to MouseReports.encode(7,-127,127,-127),
                1 to KeyboardReports.encode(255,listOf(4,5,6,7,8,0x73)))) {
            assertEquals(payload.size*8,f.filter { it.id == id && it.type == 0x80 }.sumOf { it.size*it.count })
        }
        assertEquals(8,f.filter { it.type == 0x90 }.sumOf { it.size*it.count })
    }
    @Test fun descriptorUsesShortItemsCompatibleWithAndroid11NativeValidation() {
        val bytes = CompositeReports.descriptor
        var offset = 0; var ids = 0
        // Mirror the Android 11 framing walk, independently of the semantic parser above.
        while (offset < bytes.size) {
            val item = bytes[offset++].toInt() and 255
            assertNotEquals(0xfe,item); assertNotEquals(3,item and 3)
            if (item == 0x85) ids++
            offset += item and 3
        }
        assertEquals(bytes.size,offset); assertEquals(2,ids)
    }
}
