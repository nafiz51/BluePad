package com.bluepad.ui

import com.bluepad.hid.MouseButtons
import org.junit.Assert.*
import org.junit.Test

class TouchpadGesturesTest {
    @Test fun staleEventTimeCannotShortenActualClickPressOrGap() {
        var dispatch = 0L
        val edges = mutableListOf<Pair<Long,TouchpadGestures.ButtonChange>>()
        val g = TouchpadGestures(8f,40f,250,300,180,20,reportClock = { dispatch }) { edges.add(dispatch to it) }
        dispatch=10; sample(g,0,f()); dispatch=40; sample(g,20)
        dispatch=70; sample(g,50,f()); dispatch=120; sample(g,80)
        assertEquals(listOf(120L),edges.map { it.first }); assertEquals(140L,g.holdDeadline)
        dispatch=121; g.advance(121); assertEquals(1,edges.size)
        dispatch=140; g.advance(140); dispatch=160; g.advance(160); dispatch=180; g.advance(180)
        assertEquals(listOf(120L,140L,160L,180L),edges.map { it.first })
        assertEquals(click(1)+click(1),edges.map { it.second })
    }
    @Test fun slowEmissionAndLateCallbacksStartNextDeadlineAfterActualDispatch() {
        var dispatch = 0L
        val edges = mutableListOf<Long>()
        val g = TouchpadGestures(8f,40f,250,300,180,20,reportClock = { dispatch }) {
            edges.add(dispatch); dispatch += 7
        }
        sample(g,0,f()); dispatch=20; sample(g,20); dispatch=50; sample(g,50,f())
        dispatch=100; sample(g,80); assertEquals(127L,g.holdDeadline)
        dispatch=126; g.advance(126); assertEquals(1,edges.size)
        dispatch=200; g.advance(200); assertEquals(227L,g.holdDeadline)
        dispatch=227; g.advance(227); dispatch=254; g.advance(254)
        assertEquals(listOf(100L,200L,227L,254L),edges)
    }
    @Test fun directionalJitterUsesNetDisplacementNotAccumulatedPath() {
        val g = TouchpadGestures(8f,40f,250,300,180,20) { events.add(it) }
        sample(g,0,f()); sample(g,20); sample(g,50,f())
        for ((index,x) in listOf(30f,10f,31f,9f,28f).withIndex()) {
            sample(g,60+index*10L,f(x=x)); assertEquals(TouchpadGestures.Phase.SECOND_TAP,g.phase)
        }
        g.sample(110,listOf(f(x=45f)),100f,ending = true); sample(g,110)
        g.advance(130); g.advance(150); g.advance(170)
        assertEquals(click(1)+click(1),events)
    }
    @Test fun exactDragThresholdRemainsTapButExceedingItStartsDragFromSecondOrigin() {
        for (offset in listOf(16f,17f)) {
            events.clear()
            val g = TouchpadGestures(8f,40f,250,300,180,20) { events.add(it) }
            sample(g,0,f()); sample(g,20); sample(g,50,f(x=35f)); sample(g,70,f(x=35f+offset))
            assertEquals(if (offset == 16f) TouchpadGestures.Phase.SECOND_TAP else TouchpadGestures.Phase.DRAG,g.phase)
            g.cancel(); assertEquals(if (offset == 16f) emptyList() else click(1),events)
        }
    }
    @Test fun stationaryHoldAfterMinorJitterStillBeginsDragAtOriginalDeadline() {
        val g = TouchpadGestures(8f,40f,250,300,180,20) { events.add(it) }
        sample(g,0,f()); sample(g,20); sample(g,50,f()); sample(g,70,f(x=31f))
        g.advance(229); assertTrue(events.isEmpty()); g.advance(230)
        assertEquals(TouchpadGestures.Phase.DRAG,g.phase)
        sample(g,240); assertEquals(click(1),events)
    }
    @Test fun cancellingJitteringSecondTapCannotCreateDelayedClickOrDrag() {
        val g = TouchpadGestures(8f,40f,250,300,180,20) { events.add(it) }
        sample(g,0,f()); sample(g,20); sample(g,50,f()); sample(g,70,f(x=31f))
        g.cancel(); g.advance(1000); assertTrue(events.isEmpty()); assertNull(g.holdDeadline)
    }
    @Test fun realisticFastTapIntervalsHaveFourDistinctPacedEdges() {
        for (gap in listOf(30L,50L,70L,95L,120L)) {
            val output = mutableListOf<Pair<Long,TouchpadGestures.ButtonChange>>()
            var clock = 0L
            val g = TouchpadGestures(8f,40f,250,300,180,20) { output.add(clock to it) }
            fun touch(t: Long, fingers: List<TouchpadMotion.Finger>, ending: Boolean = false) {
                clock = t; g.sample(t,fingers,100f,ending)
            }
            touch(0,listOf(f())); touch(20,listOf(f(x=22f)),true); touch(20,emptyList())
            touch(20+gap,listOf(f(x=21f))); touch(40+gap,listOf(f(x=23f)),true); touch(40+gap,emptyList())
            for (t in (60+gap)..(440+gap) step 20) { clock=t; g.advance(t) }
            assertEquals("gap=$gap",click(1)+click(1),output.map { it.second })
            assertTrue("gap=$gap output=$output",output.zipWithNext().all { (a,b) -> b.first-a.first >= 20 })
            assertNull(g.holdDeadline)
        }
    }
    @Test fun fastIntervalsFollowedByHoldOrLiveMovementRemainDrags() {
        for (gap in listOf(30L,50L,70L,95L)) for (move in listOf(false,true)) {
            events.clear()
            val g = TouchpadGestures(8f,40f,250,300,180,20) { events.add(it) }
            sample(g,0,f()); sample(g,20); sample(g,20+gap,f(x=21f))
            if (move) sample(g,30+gap,f(x=41f)) else g.advance(200+gap)
            assertEquals(TouchpadGestures.Phase.DRAG,g.phase)
            g.cancel(); g.advance(1000); assertEquals(click(1),events)
        }
    }
    @Test fun incidentalSecondTapMotionBeyondNormalSlopStillDoubleClicks() {
        val notes = mutableListOf<String>(); events.clear()
        val g = TouchpadGestures(8f,40f,250,300,180,20,diagnostic = { notes.add(it()) }) { events.add(it) }
        sample(g,0,f()); sample(g,20); sample(g,50,f()); sample(g,60,f(x=29f)); sample(g,70)
        g.advance(90); g.advance(110); g.advance(130)
        assertEquals(click(1)+click(1),events)
        assertTrue(notes.any { it.startsWith("slop_crossed") })
        assertFalse(notes.any { it.startsWith("drag_classified") })
        assertTrue(notes.any { it.startsWith("double_recognized") })
    }
    @Test fun moderateDoubleTapPreservesBothPacedClicks() {
        val g = TouchpadGestures(8f,40f,250,300,180,20) { events.add(it) }
        sample(g,0,f()); sample(g,40); sample(g,130,f())
        g.sample(210,listOf(f(x=30f)),100f,ending = true); sample(g,210)
        g.advance(230); g.advance(250); g.advance(270)
        assertEquals(click(1)+click(1),events)
    }
    @Test fun slowDoubleTapIsTwoPacedSinglesWithDistinctMouseReports() {
        val reports = mutableListOf<Int>(); val buttons = MouseButtons()
        val g = TouchpadGestures(8f,40f,250,300,180,20) { reports.add(buttons.change("gesture",it.mask,it.down)[0].toInt()) }
        sample(g,0,f()); sample(g,40); g.advance(160); g.advance(180)
        sample(g,200,f()); sample(g,260); g.advance(380); g.advance(400)
        assertEquals(listOf(1,0,1,0),reports); assertFalse(buttons.pressed)
    }
    @Test fun rapidDoubleTapFollowedByMovementDoesNotCancelScheduledClickEdges() {
        val g = TouchpadGestures(8f,40f,250,300,180,20) { events.add(it) }
        sample(g,0,f()); sample(g,30); sample(g,60,f()); sample(g,90)
        sample(g,100,f()); sample(g,110,f(x=40f)); g.advance(130); sample(g,150)
        g.advance(170); assertEquals(click(1)+click(1),events)
        assertEquals(TouchpadGestures.Phase.IDLE,g.phase)
    }
    @Test fun liveMovementAndStationaryHoldStillAcquireDragBeforeLift() {
        for (move in listOf(false,true)) {
            events.clear()
            val g = TouchpadGestures(8f,40f,250,300,180,20) { events.add(it) }
            sample(g,0,f()); sample(g,30); sample(g,65,f())
            if (move) sample(g,85,f(x=40f)) else g.advance(245)
            assertEquals(TouchpadGestures.Phase.DRAG,g.phase)
            assertEquals(listOf(TouchpadGestures.ButtonChange(1,true)),events)
            g.sample(250,listOf(f(x=30f)),100f,ending = true); sample(g,250)
            assertEquals(click(1),events)
        }
    }
    @Test fun fastDoubleTapLiftCoordinatesCannotStartDrag() {
        val g = TouchpadGestures(8f,40f,250,300,180,20) { events.add(it) }
        sample(g,0,f()); sample(g,30,f()); sample(g,30)
        sample(g,65,f())
        g.sample(95,listOf(f(x=30f)),100f,ending = true)
        sample(g,95)
        assertEquals(TouchpadGestures.Phase.IDLE,g.phase)
        g.advance(115); g.advance(135); g.advance(155)
        assertEquals(click(1)+click(1),events)
    }
    @Test fun productionSingleClickStartsAt120AndHas20MsPress() {
        val g = TouchpadGestures(8f,40f,250,300,180,20) { events.add(it) }
        sample(g,0,f()); sample(g,40); g.advance(159); assertTrue(events.isEmpty())
        g.advance(160); assertEquals(listOf(TouchpadGestures.ButtonChange(1,true)),events)
        g.advance(179); assertEquals(1,events.size); g.advance(180); assertEquals(click(1),events)
    }
    @Test fun normalSecondTapLongerThan100MsStillProducesSpacedDoubleClick() {
        val g = TouchpadGestures(8f,40f,250,300,180,20) { events.add(it) }
        sample(g,0,f()); sample(g,40); sample(g,100,f()); sample(g,250)
        assertEquals(1,events.size)
        g.advance(270); g.advance(290); g.advance(310)
        assertEquals(click(1)+click(1),events)
    }
    @Test fun lateTimerCannotCollapseFourDoubleClickEdgesIntoOneBurst() {
        val g = TouchpadGestures(8f,40f,250,300,180,20) { events.add(it) }
        sample(g,0,f()); sample(g,40); sample(g,100,f()); sample(g,140)
        g.advance(500); assertEquals(2,events.size)
        g.advance(519); assertEquals(2,events.size); g.advance(520); g.cancel()
        assertEquals(click(1)+click(1),events)
    }
    @Test fun pacedBrowserDragIsOneHeldPressAndCancelsSafely() {
        val g = TouchpadGestures(8f,40f,250,300,180,20) { events.add(it) }
        sample(g,0,f()); sample(g,40); sample(g,100,f()); sample(g,120,f(x=40f))
        assertEquals(TouchpadGestures.Phase.DRAG,g.phase)
        assertEquals(listOf(TouchpadGestures.ButtonChange(1,true)),events)
        g.cancel(); g.advance(1000); assertEquals(click(1),events)
    }
    @Test fun holdNeverSendsTheFirstClickOrPrematureSecondClick() {
        val g = machine(); sample(g,0,f()); sample(g,40); sample(g,100,f())
        assertTrue(events.isEmpty()); g.advance(199); assertTrue(events.isEmpty())
        g.advance(200); assertEquals(listOf(TouchpadGestures.ButtonChange(1,true)),events)
        sample(g,210); assertEquals(click(1),events)
    }
    @Test fun liftAfterHoldIsDragReleaseEvenIfTimerWasDelayed() {
        val g = machine(); sample(g,0,f()); sample(g,40); sample(g,100,f()); sample(g,230)
        assertEquals(click(1),events)
    }
    @Test fun singleTapRecognitionDeadlineAndCancellationAreBounded() {
        val g = machine(); sample(g,0,f()); sample(g,40)
        assertEquals(160L,g.holdDeadline); g.advance(159); assertTrue(events.isEmpty())
        g.advance(160); g.advance(400); assertEquals(click(1),events)
        events.clear(); sample(g,500,f()); sample(g,540); g.cancel(); g.advance(800)
        assertTrue(events.isEmpty())
    }
    private val events = mutableListOf<TouchpadGestures.ButtonChange>()
    private fun machine() = TouchpadGestures(8f,40f,250,300,100) { events.add(it) }
    private fun f(id: Int = 0, x: Float = 20f, y: Float = 20f) = TouchpadMotion.Finger(id,x,y)
    private fun sample(g: TouchpadGestures, time: Long, vararg fingers: TouchpadMotion.Finger) = g.sample(time,fingers.toList(),100f)
    private fun click(mask: Int) = listOf(TouchpadGestures.ButtonChange(mask,true),TouchpadGestures.ButtonChange(mask,false))
    @Test fun quickSingleTapClicksOnceAndDoesNotPermitMotion() {
        val g = machine(); sample(g,0,f()); sample(g,60,f(x=23f)); assertFalse(g.allowsMotion)
        sample(g,80); assertTrue(events.isEmpty()); g.advance(200); assertEquals(click(1),events)
    }
    @Test fun movementCancelsTapAndEnablesCursorMotion() {
        val g = machine(); sample(g,0,f()); sample(g,40,f(x=29f)); assertTrue(g.allowsMotion)
        sample(g,60); assertTrue(events.isEmpty())
    }
    @Test fun longSingleTouchDoesNotClick() {
        val g = machine(); sample(g,0,f()); sample(g,251,f()); sample(g,260)
        assertTrue(events.isEmpty())
    }
    @Test fun stripTouchNeverClicksEvenWhenItCrossesIntoMainArea() {
        val g = machine(); sample(g,0,f(x=88f)); sample(g,20,f(x=87f)); sample(g,50)
        assertTrue(events.isEmpty())
    }
    @Test fun twoFingerTapProducesOnlyRightClickDespiteStaggeredLift() {
        val g = machine(); sample(g,0,f()); sample(g,20,f(),f(1,40f))
        sample(g,50,f(),f(1,40f)); assertFalse(g.allowsMotion)
        sample(g,60,f(1,40f)); sample(g,80)
        assertEquals(click(2),events)
    }
    @Test fun twoFingerScrollCancelsRightAndLeftTapCandidates() {
        val g = machine(); sample(g,0,f()); sample(g,10,f(),f(1,40f))
        sample(g,30,f(y=30f),f(1,40f,30f)); assertTrue(g.allowsMotion)
        sample(g,60,f()); sample(g,80); assertTrue(events.isEmpty())
    }
    @Test fun secondFingerInStripCannotProduceRightClick() {
        val g = machine(); sample(g,0,f()); sample(g,10,f(),f(1,95f)); sample(g,50)
        assertTrue(events.isEmpty())
    }
    @Test fun ordinaryDoubleClickProducesExactlyTwoPressReleasePairs() {
        val g = machine(); sample(g,0,f()); sample(g,40)
        sample(g,100,f()); sample(g,140)
        assertEquals(click(1)+click(1),events)
    }
    @Test fun doubleTapHoldDragsUntilLiftWithOrderedButtons() {
        val buttons = MouseButtons(); val reports = mutableListOf<ByteArray>()
        val g = TouchpadGestures(8f,40f,250,300,100) { reports.add(buttons.change("gesture",it.mask,it.down)) }
        sample(g,0,f()); sample(g,40); sample(g,100,f()); g.advance(200)
        assertEquals(TouchpadGestures.Phase.DRAG,g.phase)
        reports.add(buttons.move(12,-5)); sample(g,220,f(x=32f)); sample(g,240)
        assertEquals(listOf(1,1,0),reports.map { it[0].toInt() })
        assertArrayEquals(byteArrayOf(1,12,-5,0),reports[1]); assertFalse(buttons.pressed)
    }
    @Test fun doubleTapMovementStartsDragBeforeHoldDeadline() {
        val g = machine(); sample(g,0,f()); sample(g,30); sample(g,100,f())
        sample(g,120,f(x=40f)); assertEquals(TouchpadGestures.Phase.DRAG,g.phase)
        assertEquals(listOf(TouchpadGestures.ButtonChange(1,true)),events)
        sample(g,150); assertEquals(click(1),events)
    }
    @Test fun cancellationReleasesDragAndInvalidatesDoubleTapMemory() {
        val g = machine(); sample(g,0,f()); sample(g,30); sample(g,100,f()); g.advance(200)
        g.cancel(); g.cancel(); g.advance(1000)
        assertEquals(click(1),events)
        sample(g,210,f()); g.advance(400); assertEquals(TouchpadGestures.Phase.SINGLE_TAP,g.phase)
    }
    @Test fun addingFingerDuringDragReleasesBeforeScrolling() {
        val g = machine(); sample(g,0,f()); sample(g,30); sample(g,100,f()); g.advance(200)
        sample(g,210,f(),f(1,40f)); assertEquals(TouchpadGestures.Phase.MOTION,g.phase)
        assertEquals(click(1),events)
        sample(g,230,f()); sample(g,240); assertEquals(2,events.size)
    }
    @Test fun threeDistinctFingersCancelTapEvenIfNeverSimultaneouslyDown() {
        val g = machine(); sample(g,0,f()); sample(g,10,f(),f(1,40f))
        sample(g,20,f(1,40f)); sample(g,30,f(1,40f),f(2,50f)); sample(g,60)
        assertTrue(events.isEmpty())
    }
    @Test fun distantOrLateSecondTouchDoesNotStartDrag() {
        val g = machine(); sample(g,0,f()); sample(g,30); sample(g,100,f(x=80f)); g.advance(200)
        assertEquals(TouchpadGestures.Phase.SINGLE_TAP,g.phase)
        sample(g,210); sample(g,600,f(x=80f)); g.advance(710)
        assertEquals(TouchpadGestures.Phase.SINGLE_TAP,g.phase)
    }
    @Test fun disconnectWhileDraggingClearsAllOwnersAndCancellationIsSafe() {
        val buttons = MouseButtons()
        val g = TouchpadGestures(8f,40f,250,300,100) { buttons.change("gesture",it.mask,it.down) }
        sample(g,0,f()); sample(g,30); sample(g,100,f()); g.advance(200)
        assertTrue(buttons.pressed)
        assertArrayEquals(byteArrayOf(0,0,0,0),buttons.release()) // Manager disconnect/close path.
        g.cancel(); assertFalse(buttons.pressed)
        assertArrayEquals(byteArrayOf(0,1,2,0),buttons.move(1,2))
    }
    @Test fun exactSlopAndTapDurationBoundariesRemainATap() {
        val g = machine(); sample(g,0,f()); sample(g,250,f(x=28f)); sample(g,250)
        g.advance(430)
        assertEquals(click(1),events)
    }
    @Test fun cancelledSecondTouchCannotAcquireAButtonFromItsOldTimer() {
        val g = machine(); sample(g,0,f()); sample(g,30); sample(g,100,f())
        g.cancel(); g.advance(500); assertTrue(events.isEmpty())
        assertEquals(TouchpadGestures.Phase.IDLE,g.phase)
    }
    @Test fun longOrCancelledTwoFingerTouchesDoNotClick() {
        val g = machine(); sample(g,0,f()); sample(g,10,f(),f(1,40f))
        sample(g,251,f(),f(1,40f)); sample(g,260); assertTrue(events.isEmpty())
        sample(g,300,f()); sample(g,310,f(),f(1,40f)); g.cancel(); sample(g,320)
        assertTrue(events.isEmpty())
    }
}
