package com.bluepad.diagnostics

import com.bluepad.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsTest {
    @Test fun normalDebugBuildIsQuietEvenWithVerboseTags() {
        assertFalse(BuildConfig.VERBOSE_DIAGNOSTICS)
        assertFalse(Diagnostics.permitted(false,true))
    }
    @Test fun optInRequiresBothBuildAndTag() {
        assertFalse(Diagnostics.permitted(true,false))
        assertFalse(Diagnostics.permitted(false,false))
        assertTrue(Diagnostics.permitted(true,true))
    }
    @Test fun disabledLoggingDoesNotConstructMessagesOrCallSink() {
        repeat(1000) {
            Diagnostics.emit(false,{ error("Message must stay lazy") }) { error("Sink must stay silent") }
        }
    }
    @Test fun enabledLoggingBuildsAndEmitsOnceInOrder() {
        val events = mutableListOf<String>()
        Diagnostics.emit(true,{ events.add("format"); "report" }) { events.add(it) }
        assertEquals(listOf("format","report"),events)
    }
}
