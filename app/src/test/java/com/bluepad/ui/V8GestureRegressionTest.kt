package com.bluepad.ui

import org.junit.Assert.*
import org.junit.Test

class V8GestureRegressionTest {
    private fun f(id: Int, x: Float = 30f, y: Float = 100f) = TouchpadMotion.Finger(id,x,y)
    @Test fun naturalScrollAndIndependentSensitivityPreserveFractionsAndReversal() {
        val m = TouchpadMotion().apply { sensitivity = 8f; scrollSensitivity = 2f; naturalScrolling = true }
        m.sample(listOf(f(0),f(1,60f)))
        m.sample(listOf(f(0,y=96f),f(1,60f,96f))); assertTrue(m.drain().empty)
        m.sample(listOf(f(0,y=90f),f(1,60f,90f))); assertEquals(-1,m.drain().wheel)
        m.sample(listOf(f(0,y=100f),f(1,60f,100f))); assertEquals(1,m.drain().wheel)
        assertTrue(m.drain().empty)
    }
    @Test fun stripUsesSameSettingsAndKeepsBoundaryLock() {
        val m = TouchpadMotion().apply { naturalScrolling = true; scrollSensitivity = 0.5f }
        m.sample(listOf(f(0,88f)),100f); m.sample(listOf(f(0,30f,60f)),100f)
        assertEquals(TouchpadMotion.Delta(0,0,-1),m.drain())
        m.reset(); m.sample(listOf(f(0,87.9f)),100f); m.sample(listOf(f(0,90f,90f)),100f)
        assertEquals(0,m.drain().wheel)
    }
    @Test fun classifiedScrollCannotProduceTwoFingerClickAtSlopBoundary() {
        val events = mutableListOf<TouchpadGestures.ButtonChange>()
        val g = TouchpadGestures(8f,100f,250,300,180) { events.add(it) }
        g.sample(0,listOf(f(0)),300f); g.sample(10,listOf(f(0),f(1,60f)),300f)
        g.suppressTaps(); g.sample(50,listOf(f(0,y=92f),f(1,60f,92f)),300f)
        g.sample(60,listOf(f(1,60f,92f)),300f); g.sample(70,emptyList(),300f); g.advance(500)
        assertTrue(events.isEmpty())
    }
    @Test fun standardTwoTapProducesExactlyRightDownAndUp() {
        val edges = mutableListOf<TouchpadGestures.ButtonChange>()
        val g = TouchpadGestures(8f,100f,250,300,180,clickGapMs=20) { edges.add(it) }
        g.sample(0,listOf(f(0)),300f); g.sample(10,listOf(f(0),f(1,60f)),300f)
        g.sample(40,listOf(f(1,60f)),300f); g.sample(60,emptyList(),300f); g.advance(80)
        assertEquals(listOf(TouchpadGestures.ButtonChange(2,true),TouchpadGestures.ButtonChange(2,false)),edges)
    }
    @Test fun pinchCancelsTwoTapCandidateAndCannotLeaveRightClick() {
        val edges = mutableListOf<TouchpadGestures.ButtonChange>()
        val g = TouchpadGestures(8f,100f,250,300,180) { edges.add(it) }
        val p = PinchGesture(); g.sample(0,listOf(f(0)),300f)
        val pair = listOf(f(0),f(1,90f)); g.sample(10,pair,300f); p.sample(pair)
        p.sample(listOf(f(0,10f),f(1,110f))); assertEquals(PinchGesture.Mode.PINCH,p.mode)
        g.cancel(); g.advance(500); assertTrue(edges.isEmpty())
    }
    @Test fun cursorStartsOnFirstSlopCrossingWithoutWaitingForTapDeadline() {
        val g = TouchpadGestures(8f,100f,250,300,180) { fail("Cursor movement must not click") }
        val m = TouchpadMotion()
        val start = listOf(f(0)); g.sample(0,start,300f); m.sample(start,300f)
        val moved = listOf(f(0,39f,102f)); g.sample(8,moved,300f)
        assertTrue(g.allowsMotion); assertNull(g.holdDeadline)
        m.sample(moved,300f); assertEquals(TouchpadMotion.Delta(9,2,0),m.drain())
    }
    @Test fun cursorMotionIsIdenticalForAllScrollPreferencesAndBatchedSamples() {
        val results = mutableListOf<List<TouchpadMotion.Delta>>()
        for (natural in listOf(false,true)) for(speed in listOf(0.5f,1f,3f)) {
            val m = TouchpadMotion().apply { sensitivity = 1.3f; naturalScrolling = natural; scrollSensitivity = speed }
            m.sample(listOf(f(0)),300f)
            val frames = mutableListOf<TouchpadMotion.Delta>()
            for(i in 1..100) {
                m.sample(listOf(f(0,30f+i*0.35f,100f-i*0.21f)),300f)
                if(i%4 == 0) frames.add(m.drain())
            }
            results.add(frames)
        }
        results.drop(1).forEach { assertEquals(results.first(),it) }
        assertTrue(results.first().any { it.x != 0 && it.y != 0 })
    }
    @Test fun existingScrollPreferencesSurviveAndObsoleteGestureKeysAreIgnored() {
        val s = ScrollSettings.restore(mapOf("natural" to "true","scroll" to "2.3","THREE_UP.action" to "CUSTOM","desktop" to "WINDOWS"))
        assertEquals(ScrollSettings(true,2.3f),s); assertEquals(s,ScrollSettings.restore(s.encode()))
        assertEquals(setOf("natural","scroll"),s.encode().keys)
        assertEquals(ScrollSettings(),ScrollSettings.restore(mapOf("scroll" to "NaN","natural" to "bad")))
    }
}
