package com.bluepad.ui

import org.junit.Assert.*
import org.junit.Test

class MouseButtonContactTest {
    private val events = mutableListOf<Boolean>()
    private val contact = MouseButtonContact({ events.add(true) },{ events.add(false) })
    @Test fun tapAndHoldHaveExactlyOneOrderedPressAndRelease() {
        contact.down(7); assertTrue(contact.held); assertEquals(listOf(true),events)
        contact.down(7); assertTrue(contact.up(7))
        assertFalse(contact.held); assertEquals(listOf(true,false),events)
    }
    @Test fun extraFingerCannotStealOrReleaseHold() {
        contact.down(7); contact.down(9); assertFalse(contact.up(9)); assertTrue(contact.held)
        contact.up(7); assertEquals(listOf(true,false),events)
    }
    @Test fun cancellationDisablingAndDetachmentAreIdempotent() {
        contact.down(7); contact.cancel(); contact.cancel(); assertFalse(contact.up(7))
        assertEquals(listOf(true,false),events)
    }
    @Test fun accessibilityClickIsCompleteButCannotDuplicateHeldTouch() {
        contact.activate(); assertEquals(listOf(true,false),events)
        events.clear(); contact.down(7); contact.activate(); contact.up(7)
        assertEquals(listOf(true,false),events)
    }
    @Test fun canPressAgainAfterCancellation() {
        contact.down(7); contact.cancel(); contact.down(9); contact.up(9)
        assertEquals(listOf(true,false,true,false),events)
    }
}
