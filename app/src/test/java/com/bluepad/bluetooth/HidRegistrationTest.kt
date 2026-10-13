package com.bluepad.bluetooth

import com.bluepad.hid.CompositeReports
import org.junit.Assert.*
import org.junit.Test

class HidRegistrationTest {
    @Test fun comboSubclassAdvertisesBothBootDevicesWithoutOtherDeviceBits() {
        val subclass = HidRegistration.SUBCLASS.toInt() and 255
        assertEquals(0xc0,subclass)
        assertEquals(0x40,subclass and 0x40)
        assertEquals(0x80,subclass and 0x80)
        assertEquals(0,subclass and 0x3f)
        assertEquals(1,CompositeReports.KEYBOARD_ID)
        assertEquals(2,CompositeReports.MOUSE_ID)
    }
    @Test fun sdpTextFitsAndroid11NativeStringBuffersAndContainsNoEmbeddedNulls() {
        for (text in listOf(HidRegistration.NAME,HidRegistration.DESCRIPTION,HidRegistration.PROVIDER)) {
            assertTrue(text.isNotEmpty()); assertFalse(text.contains('\u0000'))
            assertTrue(text.toByteArray(Charsets.UTF_8).size < 50)
        }
    }
    @Test fun descriptorFitsAndroid11OneByteNestedSdpLengthEncoding() {
        assertEquals(119,CompositeReports.descriptor.size)
        assertTrue(CompositeReports.descriptor.size + 4 <= 255)
    }
}
