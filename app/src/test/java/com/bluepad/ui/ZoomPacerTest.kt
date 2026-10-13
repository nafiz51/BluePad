package com.bluepad.ui
import org.junit.Assert.*
import org.junit.Test
class ZoomPacerTest {
    @Test fun fastInputHasOneImmediateStepAndBoundedCatchup() {
        val p = ZoomPacer(); p.submit(100)
        assertEquals(1,p.poll(0)); assertEquals(0,p.poll(29)); assertEquals(1L,p.delay(29))
        assertEquals(1,p.poll(30)); assertEquals(1,p.poll(60)); assertEquals(1,p.poll(90)); assertFalse(p.pending); assertEquals(0,p.poll(120))
    }
    @Test fun ongoingInputCannotBurstOrExtendItsDeadline() {
        val p = ZoomPacer(); p.submit(1); assertEquals(1,p.poll(100))
        for (time in 101L..129L) { p.submit(1); assertEquals(0,p.poll(time)) }
        assertEquals(1,p.poll(130)); assertEquals(0,p.poll(130))
    }
    @Test fun reversalReplacesOldDirectionAndCancellationDropsQueue() {
        val p = ZoomPacer(); p.submit(2); p.poll(0); p.submit(-1)
        assertEquals(-1,p.poll(30)); p.submit(2); p.cancel()
        assertEquals(0,p.poll(60)); assertFalse(p.pending); assertEquals(0,p.poll(120))
        p.submit(-1); assertEquals(-1,p.poll(120))
    }
    @Test fun slowFractionalPinchDoesNotLoseItsDistance() {
        val p = ZoomPacer(); val pinch = PinchGesture()
        fun f(spread: Float) = listOf(TouchpadMotion.Finger(0,-spread,0f),TouchpadMotion.Finger(1,100f+spread,0f))
        pinch.sample(f(0f))
        var sent = 0
        for (i in 1..32) { p.submit(pinch.sample(f(i*0.5f))); sent += p.poll(i*100L) }
        assertEquals(1,sent); assertFalse(p.pending)
    }
}
