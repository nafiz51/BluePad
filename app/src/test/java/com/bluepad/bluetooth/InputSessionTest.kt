package com.bluepad.bluetooth

import com.bluepad.hid.HidInputState
import org.junit.Assert.*
import org.junit.Test

class InputSessionTest {
    @Test fun oldUiTimersCannotUseNewConnectionBeforeUiCleanupAcknowledgesIt() {
        val s = InputSession(); assertFalse(s.accepts(s.capture()))
        s.acknowledge(s.token); assertTrue(s.accepts(s.capture()))
        val old = s.token; s.invalidate()
        assertFalse(s.accepts(s.capture()))
        s.acknowledge(old); assertFalse(s.accepts(s.capture()))
        s.acknowledge(s.token); assertTrue(s.accepts(s.capture()))
    }
    @Test fun queuedInputBeforeDisconnectCannotRunAfterReconnect() {
        val session = InputSession(); val queued = session.token
        session.invalidate(); session.invalidate()
        assertFalse(session.accepts(queued)); assertTrue(session.accepts(session.token))
    }
    @Test fun lifecycleAndExplicitDisconnectInvalidateAlreadyQueuedWork() {
        val session = InputSession(); val queued = session.token
        session.invalidate(); assertFalse(session.accepts(queued))
    }
    @Test fun interruptionClearsBothDevicesAndDoesNotReplayClicks() {
        val session = InputSession(); val input = HidInputState(); val old = session.token
        input.mouse.change("physical",1,true); input.mouse.change("gesture",2,true)
        input.keyboard.press(0xe0); input.keyboard.press(4)
        session.invalidate(); val neutral = input.releaseAll(false)
        assertFalse(session.accepts(old))
        assertArrayEquals(ByteArray(4),neutral[0].payload); assertArrayEquals(ByteArray(8),neutral[1].payload)
        assertFalse(input.mouse.physicalHeld()); assertEquals(0,input.keyboard.modifiers)
    }
}
