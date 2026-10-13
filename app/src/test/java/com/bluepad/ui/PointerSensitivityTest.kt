package com.bluepad.ui

import org.junit.Assert.*
import org.junit.Test

class PointerSensitivityTest {
    @Test fun sliderSupportsEveryTenthFromHalfToEightWithDefaultThreePointFive() {
        assertEquals(3.5f,TouchpadMotion().sensitivity,0f)
        assertEquals(0.5f,PointerSensitivity.value(0),0f)
        assertEquals(8f,PointerSensitivity.value(75),0f)
        assertEquals(5,PointerSensitivity.progress(1f))
        for (step in 0..75) {
            val value = PointerSensitivity.value(step)
            assertEquals(step,PointerSensitivity.progress(value))
            assertEquals(value,PointerSensitivity.restore(value),0f)
            TouchpadMotion().sensitivity = value
        }
    }
    @Test fun legacySavedValuesRemainValidAndInvalidSavedValuesAreSafe() {
        for (step in 0..25) {
            val saved = 0.5f + step / 10f
            assertEquals(saved,PointerSensitivity.restore(saved),0f)
            assertEquals(step,PointerSensitivity.progress(saved))
        }
        assertEquals(3.5f,PointerSensitivity.restore(Float.NaN),0f)
        assertEquals(3.5f,PointerSensitivity.restore(Float.POSITIVE_INFINITY),0f)
        assertEquals(0.5f,PointerSensitivity.restore(-1f),0f)
        assertEquals(8f,PointerSensitivity.restore(10f),0f)
    }
    @Test fun eightTimesMovementPreservesFractionalInputAcrossFrames() {
        val m = TouchpadMotion().apply { sensitivity = 8f }
        m.sample(listOf(TouchpadMotion.Finger(0,0f,0f)))
        m.sample(listOf(TouchpadMotion.Finger(0,0.03125f,0f)))
        assertTrue(m.drain().empty)
        m.sample(listOf(TouchpadMotion.Finger(0,0.125f,0f)))
        assertEquals(TouchpadMotion.Delta(1,0,0),m.drain())
        assertFalse(m.hasPendingReport)
    }
    @Test fun largeEightTimesMotionDrainsWithoutReportOverflowOrDistanceLoss() {
        val m = TouchpadMotion().apply { sensitivity = 8f }
        m.sample(listOf(TouchpadMotion.Finger(0,0f,0f)))
        m.sample(listOf(TouchpadMotion.Finger(0,1024f,-512f)))
        var x = 0; var y = 0; var reports = 0
        while (m.hasPendingReport) {
            val delta = m.drain()
            assertTrue(delta.x in -127..127); assertTrue(delta.y in -127..127)
            x += delta.x; y += delta.y; reports++
            assertTrue("Overflow must drain finitely",reports <= 65)
        }
        assertEquals(8192,x); assertEquals(-4096,y); assertEquals(65,reports)
        m.sample(listOf(TouchpadMotion.Finger(0,0f,0f)))
        m.reset(); assertFalse(m.hasPendingReport); assertTrue(m.drain().empty)
    }
    @Test fun bothScrollingMethodsAreIndependentOfPointerSensitivity() {
        for (sensitivity in listOf(0.5f,1f,3f,8f)) for (edge in listOf(false,true)) {
            val m = TouchpadMotion().apply { this.sensitivity = sensitivity }
            fun fingers(y: Float) = if (edge) listOf(TouchpadMotion.Finger(0,95f,y)) else
                listOf(TouchpadMotion.Finger(0,20f,y),TouchpadMotion.Finger(1,40f,y))
            m.sample(fingers(0f),100f); m.sample(fingers(-40f),100f)
            assertEquals(TouchpadMotion.Delta(0,0,2),m.drain())
        }
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsSensitivityAboveEight() { TouchpadMotion().sensitivity = 8.1f }
    @Test(expected = IllegalArgumentException::class) fun rejectsSensitivityBelowHalf() { TouchpadMotion().sensitivity = 0.4f }
}
