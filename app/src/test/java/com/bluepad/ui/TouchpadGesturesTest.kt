package com.bluepad.ui

import com.bluepad.hid.MouseButtons
import org.junit.Assert.*
import org.junit.Test

class TouchpadGesturesTest {
    private val events = mutableListOf<TouchpadGestures.ButtonChange>()
    private fun machine() = TouchpadGestures(8f,40f,250,300,100) { events.add(it) }
    private fun f(id: Int = 0, x: Float = 20f, y: Float = 20f) = TouchpadMotion.Finger(id,x,y)
    private fun sample(g: TouchpadGestures, time: Long, vararg fingers: TouchpadMotion.Finger) = g.sample(time,fingers.toList(),100f)
    private fun click(mask: Int) = listOf(TouchpadGestures.ButtonChange(mask,true),TouchpadGestures.ButtonChange(mask,false))
    @Test fun quickSingleTapClicksOnceAndDoesNotPermitMotion() {
        val g = machine(); sample(g,0,f()); sample(g,60,f(x=23f)); assertFalse(g.allowsMotion)
        sample(g,80); assertEquals(click(1),events)
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
        val g = machine(); sample(g,0,f(x=90f)); sample(g,20,f(x=89f)); sample(g,50)
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
        assertEquals(listOf(1,0,1,1,0),reports.map { it[0].toInt() })
        assertArrayEquals(byteArrayOf(1,12,-5,0),reports[3]); assertFalse(buttons.pressed)
    }
    @Test fun doubleTapMovementStartsDragBeforeHoldDeadline() {
        val g = machine(); sample(g,0,f()); sample(g,30); sample(g,100,f())
        sample(g,120,f(x=40f)); assertEquals(TouchpadGestures.Phase.DRAG,g.phase)
        assertEquals(click(1)+TouchpadGestures.ButtonChange(1,true),events)
        sample(g,150); assertEquals(click(1)+click(1),events)
    }
    @Test fun cancellationReleasesDragAndInvalidatesDoubleTapMemory() {
        val g = machine(); sample(g,0,f()); sample(g,30); sample(g,100,f()); g.advance(200)
        g.cancel(); g.cancel(); g.advance(1000)
        assertEquals(click(1)+click(1),events)
        sample(g,210,f()); g.advance(400); assertEquals(TouchpadGestures.Phase.SINGLE_TAP,g.phase)
    }
    @Test fun addingFingerDuringDragReleasesBeforeScrolling() {
        val g = machine(); sample(g,0,f()); sample(g,30); sample(g,100,f()); g.advance(200)
        sample(g,210,f(),f(1,40f)); assertEquals(TouchpadGestures.Phase.MOTION,g.phase)
        assertEquals(click(1)+click(1),events)
        sample(g,230,f()); sample(g,240); assertEquals(4,events.size)
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
        assertEquals(click(1),events)
    }
    @Test fun cancelledSecondTouchCannotAcquireAButtonFromItsOldTimer() {
        val g = machine(); sample(g,0,f()); sample(g,30); sample(g,100,f())
        g.cancel(); g.advance(500); assertEquals(click(1),events)
        assertEquals(TouchpadGestures.Phase.IDLE,g.phase)
    }
    @Test fun longOrCancelledTwoFingerTouchesDoNotClick() {
        val g = machine(); sample(g,0,f()); sample(g,10,f(),f(1,40f))
        sample(g,251,f(),f(1,40f)); sample(g,260); assertTrue(events.isEmpty())
        sample(g,300,f()); sample(g,310,f(),f(1,40f)); g.cancel(); sample(g,320)
        assertTrue(events.isEmpty())
    }
}
