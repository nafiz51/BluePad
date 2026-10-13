package com.bluepad.ui

import org.junit.Assert.*
import org.junit.Test

class PinchGestureTest {
    @Test fun widenedStripDoesNotChangeTwoFingerScrollOrPinch() {
        val width = 100f
        assertEquals(88f,TouchpadMotion.scrollZoneStart(width),0.001f)
        val start = fingers(89f,99f)
        val scroll = PinchGesture(); val motion = TouchpadMotion()
        scroll.sample(start); motion.sample(start,width)
        val moved = fingers(89f,99f,-20f)
        assertEquals(0,scroll.sample(moved)); assertEquals(PinchGesture.Mode.SCROLL,scroll.mode)
        motion.sample(moved,width); assertEquals(TouchpadMotion.Delta(0,0,1),motion.drain())
        val pinch = PinchGesture(); pinch.sample(start)
        assertEquals(1,pinch.sample(fingers(57f,99f)))
        assertEquals(PinchGesture.Mode.PINCH,pinch.mode)
    }
    private fun fingers(a: Float, b: Float, y: Float = 0f) = listOf(TouchpadMotion.Finger(7,a,y),TouchpadMotion.Finger(9,b,y))
    @Test fun outwardAndInwardHaveCorrectSigns() {
        val p = PinchGesture(); p.sample(fingers(0f,100f))
        assertEquals(1,p.sample(fingers(-16f,116f))); assertEquals(PinchGesture.Mode.PINCH,p.mode)
        assertEquals(-1,p.sample(fingers(0f,100f)))
        p.reset(); p.sample(fingers(0f,100f)); assertEquals(-1,p.sample(fingers(16f,84f)))
    }
    @Test fun smallNoiseDoesNotClassifyOrZoom() {
        val p = PinchGesture(); p.sample(fingers(0f,100f))
        assertEquals(0,p.sample(fingers(-5f,105f))); assertEquals(PinchGesture.Mode.UNDECIDED,p.mode)
    }
    @Test fun thresholdAndFractionalAccumulationRetainSmallChanges() {
        val p = PinchGesture(); p.sample(fingers(0f,100f))
        assertEquals(0,p.sample(fingers(-6f,106f))); assertEquals(PinchGesture.Mode.PINCH,p.mode)
        assertEquals(0,p.sample(fingers(-15f,115f))); assertEquals(1,p.sample(fingers(-16f,116f)))
    }
    @Test fun translationLocksScrollingEvenWithSubsequentSpread() {
        val p = PinchGesture(); p.sample(fingers(0f,100f))
        assertEquals(0,p.sample(fingers(0f,100f,8f))); assertEquals(PinchGesture.Mode.SCROLL,p.mode)
        assertEquals(0,p.sample(fingers(-40f,140f,8f))); assertEquals(PinchGesture.Mode.SCROLL,p.mode)
    }
    @Test fun pinchCannotSwitchToScrollDuringTranslation() {
        val p = PinchGesture(); p.sample(fingers(0f,100f)); p.sample(fingers(-16f,116f))
        assertEquals(0,p.sample(fingers(-16f,116f,50f))); assertEquals(PinchGesture.Mode.PINCH,p.mode)
    }
    @Test fun liftingCancellationAndPointerReplacementResetBaseline() {
        val p = PinchGesture(); p.sample(fingers(0f,100f)); p.sample(fingers(-16f,116f))
        p.sample(fingers(0f,100f).take(1)); assertEquals(PinchGesture.Mode.UNDECIDED,p.mode)
        assertEquals(0,p.sample(fingers(50f,500f)))
        p.reset(); assertEquals(0,p.sample(fingers(0f,100f)))
        assertEquals(0,p.sample(listOf(TouchpadMotion.Finger(7,0f,0f),TouchpadMotion.Finger(10,200f,0f))))
    }
    @Test fun pointerOrderDoesNotAffectClassification() {
        val p = PinchGesture(); p.sample(fingers(0f,100f)); assertEquals(1,p.sample(fingers(-16f,116f).reversed()))
    }
    @Test fun ordinaryScrollStillUsesExistingWheelMath() {
        val p = PinchGesture(); val motion = TouchpadMotion()
        val start = fingers(0f,100f); p.sample(start); motion.sample(start)
        val moved = fingers(0f,100f,-20f); p.sample(moved)
        assertEquals(PinchGesture.Mode.SCROLL,p.mode); motion.sample(moved)
        assertEquals(1,motion.drain().wheel)
    }
}
