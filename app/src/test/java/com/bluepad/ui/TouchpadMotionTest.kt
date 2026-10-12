package com.bluepad.ui

import org.junit.Assert.*
import org.junit.Test

class TouchpadMotionTest {
    private fun finger(id: Int, x: Float, y: Float = 0f) = TouchpadMotion.Finger(id,x,y)
    @Test fun fractionalMotionAndSensitivitySurviveFrameBoundaries() {
        val motion = TouchpadMotion().apply { sensitivity = 0.5f }
        motion.sample(listOf(finger(0,0f)))
        motion.sample(listOf(finger(0,0.4f)))
        assertTrue(motion.drain().empty)
        motion.sample(listOf(finger(0,1.2f)))
        assertEquals(TouchpadMotion.Delta(1,0,0), motion.drain())
        motion.sample(listOf(finger(0,2f)))
        assertTrue(motion.drain().empty)
    }
    @Test fun samplesCoalesceAndOverflowIsPreserved() {
        val motion = TouchpadMotion().apply { sensitivity = 3f }
        motion.sample(listOf(finger(0,0f)))
        motion.sample(listOf(finger(0,50f)))
        motion.sample(listOf(finger(0,100f)))
        assertEquals(127,motion.drain().x)
        assertEquals(127,motion.drain().x)
        assertEquals(46,motion.drain().x)
        assertTrue(motion.drain().empty)
    }
    @Test fun twoFingerScrollHasNoCursorMotionAndIndependentSensitivity() {
        val motion = TouchpadMotion().apply { sensitivity = 3f }
        motion.sample(listOf(finger(0,0f),finger(1,10f)))
        motion.sample(listOf(finger(0,40f,-12f),finger(1,50f,-12f)))
        assertTrue(motion.drain().empty) // Accumulate half a wheel tick.
        motion.sample(listOf(finger(0,80f,-24f),finger(1,90f,-24f)))
        assertEquals(TouchpadMotion.Delta(0,0,1),motion.drain())
        motion.sample(listOf(finger(0,80f,24f),finger(1,90f,24f)))
        assertEquals(TouchpadMotion.Delta(0,0,-2),motion.drain())
    }
    @Test fun liftingOneFingerRebasesWithoutCursorJump() {
        val motion = TouchpadMotion()
        motion.sample(listOf(finger(0,0f),finger(1,100f)))
        motion.sample(listOf(finger(1,100f)))
        assertTrue(motion.drain().empty)
        motion.sample(listOf(finger(1,103f)))
        assertEquals(TouchpadMotion.Delta(3,0,0),motion.drain())
    }
    @Test fun thirdFingerAndCancellationCannotLeaveResidualMotion() {
        val motion = TouchpadMotion()
        motion.sample(listOf(finger(0,0f),finger(1,0f),finger(2,0f)))
        motion.sample(listOf(finger(0,100f),finger(1,100f),finger(2,100f)))
        assertTrue(motion.drain().empty)
        motion.sample(listOf(finger(0,0f)))
        motion.sample(listOf(finger(0,100f)))
        motion.reset()
        assertTrue(motion.drain().empty)
        motion.sample(listOf(finger(0,500f)))
        assertTrue(motion.drain().empty)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidSensitivity() {
        TouchpadMotion().sensitivity = Float.NaN
    }
    @Test fun edgeSwipesScrollInBothDirectionsWithoutMovingCursor() {
        val motion = TouchpadMotion()
        motion.sample(listOf(finger(0,95f,50f)),100f)
        motion.sample(listOf(finger(0,99f,26f)),100f)
        assertEquals(TouchpadMotion.Delta(0,0,1),motion.drain())
        motion.sample(listOf(finger(0,91f,74f)),100f)
        assertEquals(TouchpadMotion.Delta(0,0,-2),motion.drain())
        motion.sample(listOf(finger(0,99f,74f)),100f)
        assertTrue(motion.drain().empty) // Horizontal-only edge motion is ignored.
    }
    @Test fun rightmostTenPercentIncludesTheExactBoundary() {
        for (width in listOf(100f,200f,400f)) {
            val boundary = width * 0.9f
            for (start in listOf(boundary - 0.01f,boundary,width - 0.01f)) {
                val motion = TouchpadMotion()
                motion.sample(listOf(finger(0,start,50f)),width)
                motion.sample(listOf(finger(0,start,26f)),width)
                val expected = if (start >= boundary) TouchpadMotion.Delta(0,0,1) else TouchpadMotion.Delta(0,-24,0)
                assertEquals("width=$width start=$start",expected,motion.drain())
            }
        }
    }
    @Test fun edgeModeRemainsScrollingWhenCrossingIntoCursorArea() {
        val motion = TouchpadMotion()
        motion.sample(listOf(finger(0,95f,50f)),100f)
        motion.sample(listOf(finger(0,10f,26f)),100f)
        assertEquals(TouchpadMotion.Delta(0,0,1),motion.drain())
    }
    @Test fun cursorModeRemainsMovementWhenCrossingIntoEdge() {
        val motion = TouchpadMotion()
        motion.sample(listOf(finger(0,10f,50f)),100f)
        motion.sample(listOf(finger(0,95f,26f)),100f)
        assertEquals(TouchpadMotion.Delta(85,-24,0),motion.drain())
    }
    @Test fun twoFingerTransitionRetainsOriginalModeWhenFirstFingerLifts() {
        for (start in listOf(10f,95f)) {
            val motion = TouchpadMotion()
            motion.sample(listOf(finger(0,start)),100f)
            motion.sample(listOf(finger(0,start),finger(1,99f)),100f)
            assertTrue(motion.drain().empty)
            motion.sample(listOf(finger(0,start,-24f),finger(1,99f,-24f)),100f)
            assertEquals(TouchpadMotion.Delta(0,0,1),motion.drain())
            motion.sample(listOf(finger(1,99f,-24f)),100f)
            assertTrue(motion.drain().empty) // Lift rebases without an artificial delta.
            motion.sample(listOf(finger(1,99f,-48f)),100f)
            val expected = if (start >= 90f) TouchpadMotion.Delta(0,0,1) else TouchpadMotion.Delta(0,-24,0)
            assertEquals(expected,motion.drain())
        }
    }
    @Test fun edgeScrollingAccumulatesSmallMovementsIndependentlyOfSensitivity() {
        val motion = TouchpadMotion().apply { sensitivity = 3f }
        motion.sample(listOf(finger(0,95f)),100f)
        for (step in 1..4) {
            motion.sample(listOf(finger(0,95f,-6f * step)),100f)
            assertEquals(TouchpadMotion.Delta(0,0,if (step == 4) 1 else 0),motion.drain())
        }
    }
    @Test fun cancellationClearsWheelResidualAndGestureLock() {
        val motion = TouchpadMotion()
        motion.sample(listOf(finger(0,95f)),100f)
        motion.sample(listOf(finger(0,95f,-30f)),100f)
        motion.reset()
        assertTrue(motion.drain().empty)
        motion.sample(listOf(finger(0,10f)),100f)
        motion.sample(listOf(finger(0,95f,24f)),100f)
        assertEquals(TouchpadMotion.Delta(85,24,0),motion.drain())
    }
    @Test fun liftingAllFingersLetsTheNextGestureChooseItsOwnMode() {
        val motion = TouchpadMotion()
        motion.sample(listOf(finger(0,95f)),100f)
        motion.sample(emptyList(),100f)
        motion.sample(listOf(finger(1,10f,100f)),100f)
        assertTrue(motion.drain().empty)
        motion.sample(listOf(finger(1,10f,124f)),100f)
        assertEquals(TouchpadMotion.Delta(0,24,0),motion.drain())
    }
}
