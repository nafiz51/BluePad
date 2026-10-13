package com.bluepad.ui

import com.bluepad.hid.MouseButtons
import org.junit.Assert.*
import org.junit.Test

class ExperimentalClickPacingTest {
    private val finger = TouchpadMotion.Finger(1,20f,20f)
    private val edges = mutableListOf<Pair<Long,TouchpadGestures.ButtonChange>>()
    private var now = 0L
    private fun machine() = TouchpadGestures(8f,40f,250,300,180,TouchpadGestures.CLICK_EDGE_MS,
        reportClock = { now }) { edges.add(now to it) }
    private fun touch(g: TouchpadGestures,t: Long,down: Boolean) {
        now=t; g.sample(t,if(down) listOf(finger) else emptyList(),100f)
    }
    private fun drain(g: TouchpadGestures,until: Long) {
        while(now < until) { now++; g.advance(now) }
    }
    @Test fun quickDoubleHasFourSpacedEdgesAndCorrectHidBytes() {
        val g=machine(); touch(g,0,true); touch(g,20,false); touch(g,50,true); touch(g,70,false); drain(g,250)
        assertEquals(listOf(70L,110L,150L,190L),edges.map { it.first })
        val buttons=MouseButtons()
        assertEquals(listOf(1,0,1,0),edges.map { buttons.change("gesture",it.second.mask,it.second.down)[0].toInt() })
        assertEquals(0,buttons.mask)
    }
    @Test fun boundaryAndModerateTapsDoNotLoseOrDuplicateClicks() {
        for(gap in listOf(95L,119L,120L,121L,200L)) {
            edges.clear(); now=0; val g=machine()
            touch(g,0,true); touch(g,20,false); touch(g,20+gap,true); touch(g,40+gap,false); drain(g,600)
            assertEquals("gap=$gap",listOf(true,false,true,false),edges.map { it.second.down })
            assertTrue(edges.zipWithNext().all { (a,b) -> b.first-a.first >= 40 })
        }
    }
    @Test fun tripleTapProducesExactlyThreeClicksWithoutExtraEdges() {
        val g=machine(); touch(g,0,true); touch(g,20,false); touch(g,50,true); touch(g,70,false)
        touch(g,100,true); touch(g,120,false); drain(g,600)
        assertEquals(listOf(true,false,true,false,true,false),edges.map { it.second.down })
        assertNull(g.holdDeadline)
    }
    @Test fun singleClickStartsAtSameDeadlineWithLongerReleaseInterval() {
        val g=machine(); touch(g,0,true); touch(g,20,false); drain(g,139); assertTrue(edges.isEmpty())
        drain(g,140); assertEquals(140L,edges.single().first)
        drain(g,180); assertEquals(listOf(140L,180L),edges.map { it.first })
    }
    @Test fun lateUiDispatchCannotCollapseScheduledEdges() {
        val g=machine(); touch(g,0,true); touch(g,20,false); touch(g,50,true); touch(g,70,false)
        now=500; g.advance(now); g.advance(now); assertEquals(2,edges.size)
        drain(g,580); assertEquals(listOf(70L,500L,540L,580L),edges.map { it.first })
    }
    @Test fun syntheticHoldAndMovementDragStillAvoidPrematureDoubleClick() {
        for(move in listOf(false,true)) {
            edges.clear(); now=0; val g=machine()
            touch(g,0,true); touch(g,20,false); touch(g,50,true)
            if(move) { now=80; g.sample(now,listOf(finger.copy(x=40f)),100f) } else drain(g,230)
            assertEquals(TouchpadGestures.Phase.DRAG,g.phase)
            assertEquals(listOf(true),edges.map { it.second.down })
            touch(g,300,false); assertEquals(listOf(true,false),edges.map { it.second.down })
        }
    }
    @Test fun cancellationReleasesHeldClickAndDropsRemainingEdges() {
        val g=machine(); touch(g,0,true); touch(g,20,false); touch(g,50,true); touch(g,70,false)
        g.cancel(); drain(g,600)
        assertEquals(listOf(true,false),edges.map { it.second.down }); assertNull(g.holdDeadline)
    }
    @Test fun workerBacklogCanStillCompressSubmissionDespiteUiSpacing() {
        val g=machine(); touch(g,0,true); touch(g,20,false); touch(g,50,true); touch(g,70,false); drain(g,250)
        // Fake a stalled serial worker: order is preserved, actual submission spacing is not guaranteed.
        val buttons=MouseButtons(); val transmitted=edges.map { 500L to buttons.change("gesture",it.second.mask,it.second.down)[0].toInt() }
        assertEquals(listOf(1,0,1,0),transmitted.map { it.second })
        assertTrue(transmitted.all { it.first == 500L })
    }
}
