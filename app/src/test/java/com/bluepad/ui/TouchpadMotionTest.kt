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
}
