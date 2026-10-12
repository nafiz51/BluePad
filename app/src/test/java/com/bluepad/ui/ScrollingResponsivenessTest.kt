package com.bluepad.ui

import org.junit.Assert.*
import org.junit.Test

class ScrollingResponsivenessTest {
    private fun fingers(y: Float, edge: Boolean) = if (edge) listOf(TouchpadMotion.Finger(0,95f,y)) else
        listOf(TouchpadMotion.Finger(0,20f,y),TouchpadMotion.Finger(1,40f,y))
    @Test fun bothScrollPathsEmitAt20dpAndRetainSubTickMovement() {
        for (edge in listOf(false,true)) {
            val m = TouchpadMotion(); m.sample(fingers(0f,edge),100f)
            m.sample(fingers(-19f,edge),100f); assertFalse(m.hasPendingReport); assertTrue(m.drain().empty)
            m.sample(fingers(-20f,edge),100f); assertTrue(m.hasPendingReport)
            assertEquals(TouchpadMotion.Delta(0,0,1),m.drain()); assertFalse(m.hasPendingReport)
            m.sample(fingers(-25f,edge),100f); assertTrue(m.drain().empty)
            m.sample(fingers(-40f,edge),100f); assertEquals(1,m.drain().wheel)
        }
    }
    @Test fun constantMotionHasEvenCadenceWithoutIncreasingPerFrameReportCount() {
        val m = TouchpadMotion(); m.sample(fingers(0f,true),100f)
        val reports = mutableListOf<Int>()
        for (frame in 1..30) {
            m.sample(fingers(-2f * frame,true),100f) // 120dp/s at 60Hz.
            if (m.hasPendingReport) { assertEquals(1,m.drain().wheel); reports.add(frame) }
        }
        assertEquals(listOf(10,20,30),reports)
    }
    @Test fun batchingDoesNotLoseDistanceAndReversalsDoNotCreateJitterTicks() {
        val m = TouchpadMotion(); m.sample(fingers(0f,true),100f)
        m.sample(fingers(-45f,true),100f); assertEquals(2,m.drain().wheel)
        m.sample(fingers(-44f,true),100f); assertFalse(m.hasPendingReport)
        m.sample(fingers(-45f,true),100f); assertFalse(m.hasPendingReport)
        m.sample(fingers(-20f,true),100f); assertEquals(-1,m.drain().wheel)
        assertFalse(m.hasPendingReport)
        m.reset(); assertFalse(m.hasPendingReport)
    }
    @Test fun wheelOverflowDrainsOnlyWhileAnotherWholeReportIsReady() {
        val m = TouchpadMotion(); m.sample(fingers(0f,true),100f)
        m.sample(fingers(-6000f,true),100f)
        val reports = mutableListOf<Int>()
        while (m.hasPendingReport) reports.add(m.drain().wheel)
        assertEquals(listOf(127,127,46),reports)
        assertFalse(m.hasPendingReport)
    }
    @Test fun v4TapGatePreservesInitialScrollDistanceAndCancelsRightClick() {
        val clicks = mutableListOf<TouchpadGestures.ButtonChange>()
        val g = TouchpadGestures(8f,40f,250,300,100) { clicks.add(it) }
        val m = TouchpadMotion()
        val first = listOf(TouchpadMotion.Finger(0,20f,0f))
        g.sample(0,first,100f); m.sample(first,100f)
        g.sample(10,fingers(0f,false),100f); m.sample(fingers(0f,false),100f)
        for (step in 1..20) {
            val input = fingers(-step.toFloat(),false)
            g.sample(10L + step * 5,input,100f)
            if (g.allowsMotion) m.sample(input,100f)
            if (step < 20) assertFalse(m.hasPendingReport)
        }
        assertEquals(1,m.drain().wheel)
        g.sample(120,emptyList(),100f); assertTrue(clicks.isEmpty())
    }
    @Test fun twoFingerTapJitterRemainsSuppressedWithFasterScrollQuantum() {
        val changes = mutableListOf<TouchpadGestures.ButtonChange>()
        val g = TouchpadGestures(8f,40f,250,300,100) { changes.add(it) }
        val m = TouchpadMotion()
        g.sample(0,listOf(TouchpadMotion.Finger(0,20f,0f)),100f)
        g.sample(10,fingers(0f,false),100f); m.sample(fingers(0f,false),100f)
        g.sample(30,fingers(-7f,false),100f)
        if (g.allowsMotion) m.sample(fingers(-7f,false),100f)
        assertFalse(m.hasPendingReport)
        g.sample(60,emptyList(),100f)
        assertEquals(listOf(TouchpadGestures.ButtonChange(2,true),TouchpadGestures.ButtonChange(2,false)),changes)
    }
    @Test fun tinySamplesHaveTheSameTickCountAsOneLargeSample() {
        for (edge in listOf(false,true)) for (count in listOf(10,200,1000)) {
            val m = TouchpadMotion(); m.sample(fingers(0f,edge),100f)
            var ticks = 0
            for (step in 1..count) {
                m.sample(fingers(-20f * step / count,edge),100f)
                ticks += m.drain().wheel
            }
            assertEquals("edge=$edge samples=$count",1,ticks)
            assertFalse(m.hasPendingReport)
        }
    }
    @Test fun acceptedScrollStaysMotionThroughPauseAndFingerTransitions() {
        val changes = mutableListOf<TouchpadGestures.ButtonChange>()
        val g = TouchpadGestures(8f,40f,250,300,100) { changes.add(it) }
        g.sample(0,listOf(TouchpadMotion.Finger(0,20f,0f)),100f)
        g.sample(10,fingers(0f,false),100f)
        g.sample(30,fingers(-10f,false),100f)
        assertTrue(g.allowsMotion)
        g.sample(60,listOf(TouchpadMotion.Finger(1,40f,-10f)),100f)
        g.sample(100,fingers(-10f,false),100f)
        g.sample(120,emptyList(),100f)
        assertTrue(changes.isEmpty())
        assertEquals(TouchpadGestures.Phase.IDLE,g.phase)
    }
}
