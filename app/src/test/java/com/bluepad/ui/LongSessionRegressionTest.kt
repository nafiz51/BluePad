package com.bluepad.ui

import com.bluepad.bluetooth.InputSession
import com.bluepad.hid.HidInputState
import com.bluepad.hid.KeyboardReports
import org.junit.Assert.*
import org.junit.Test

/** Deterministic stream integrity, not hardware latency, memory or battery benchmarks. */
class LongSessionRegressionTest {
    @Test fun sustainedFractionalMovementAndReversalPreserveTotals() {
        val motion = TouchpadMotion().apply { sensitivity = 3.5f }
        var x = 0; var sumX = 0; var sumY = 0
        motion.sample(listOf(TouchpadMotion.Finger(1,0f,0f)))
        repeat(20_000) { i ->
            x += if(i < 10_000) 1 else -1
            motion.sample(listOf(TouchpadMotion.Finger(1,x*0.25f,-x*0.5f)))
            val d = motion.drain(); sumX += d.x; sumY += d.y
            assertEquals(0,d.wheel)
        }
        assertEquals(0,sumX); assertEquals(0,sumY); assertFalse(motion.hasPendingReport)
    }
    @Test fun repeatedLargeSupportedCommitsKeepOrderedReportsAndUserModifier() {
        val inputs = HidInputState(); inputs.keyboard.modifier(KeyboardReports.CTRL,true)
        var strokes = 0; var reports = 0
        val bridge = ImeTextBridge({ stroke ->
            val pair = inputs.keyboard.tap(stroke)
            assertEquals(2,pair.size)
            assertEquals(stroke.usage,pair[0][2].toInt() and 255)
            assertEquals(KeyboardReports.CTRL or stroke.modifiers,pair[0][0].toInt() and 255)
            assertArrayEquals(KeyboardReports.encode(KeyboardReports.CTRL,emptyList()),pair[1])
            strokes++; reports += pair.size
        },{}).apply { enabled = true }
        repeat(200) { assertTrue(bridge.commit("Aa".repeat(256))) }
        assertEquals(102_400,strokes); assertEquals(204_800,reports)
        inputs.releaseAll(false); assertArrayEquals(ByteArray(8),inputs.keyboard.report())
    }
    @Test fun repeatedInputSessionCleanupNeverReacceptsOldInput() {
        val session = InputSession(); val inputs = HidInputState()
        repeat(1000) {
            session.acknowledge(session.token); val old = session.capture()
            inputs.mouse.press(); inputs.keyboard.press(0xe0); inputs.keyboard.press(6)
            session.invalidate(); inputs.releaseAll(false)
            assertFalse(session.accepts(old)); assertEquals(0,inputs.mouse.mask)
            assertArrayEquals(ByteArray(8),inputs.keyboard.report())
            session.acknowledge(session.token); assertFalse(session.accepts(old))
            assertTrue(session.accepts(session.capture()))
        }
    }
}
