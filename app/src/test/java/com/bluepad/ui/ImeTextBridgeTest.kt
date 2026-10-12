package com.bluepad.ui
import com.bluepad.hid.UsKeyboard
import org.junit.Assert.*
import org.junit.Test
class ImeTextBridgeTest {
    private val events = mutableListOf<UsKeyboard.Stroke>()
    private fun bridge() = ImeTextBridge({ events.add(it) },{}).apply { enabled = true }
    @Test fun committedAsciiMapsWithoutDuplicateComposition() {
        val b = bridge(); b.compose("a"); b.compose("ab"); assertTrue(events.isEmpty())
        b.commit("ab"); b.finish(); assertEquals(listOf(UsKeyboard.Stroke(4),UsKeyboard.Stroke(5)),events)
    }
    @Test fun finishCompositionCommitsOnce() {
        val b = bridge(); b.compose("A"); b.finish(); b.finish(); assertEquals(listOf(UsKeyboard.Stroke(4,2)),events)
    }
    @Test fun unicodeCommitIsRejectedAtomically() {
        val b = bridge(); assertFalse(b.commit("aবাংলা")); assertTrue(events.isEmpty())
        assertFalse(b.commit("😀")); assertTrue(events.isEmpty())
    }
    @Test fun deletionEnterAndTabUseSpecialUsages() {
        val b = bridge(); b.delete(2,0); b.enter(); b.commit("\t")
        assertEquals(listOf(42,42,40,43),events.map { it.usage })
    }
    @Test fun composingDeletionDoesNotDeleteRemoteText() {
        val b = bridge(); b.compose("ab"); b.delete(1,0); b.compose("a"); b.finish()
        assertEquals(listOf(4),events.map { it.usage })
    }
    @Test fun cancelAndDisconnectSuppressLateCompositionCommit() {
        val b = bridge(); b.compose("a"); b.cancel(); b.finish(); assertTrue(events.isEmpty())
        b.enabled = false; assertFalse(b.commit("a")); assertTrue(events.isEmpty())
    }
    @Test fun unsupportedDeletionAndLargeCommitDoNotFloodReports() {
        val b = bridge(); assertFalse(b.delete(1,1)); assertFalse(b.delete(101,0))
        assertFalse(b.commit("a".repeat(513))); assertTrue(events.isEmpty())
    }
    @Test fun unsupportedCommitCannotCauseRemoteBackspaceUntilInputReset() {
        val b = bridge(); b.commit("😀"); assertFalse(b.delete(1,0)); assertFalse(b.commit("a"))
        assertTrue(events.isEmpty()); b.reset(); assertTrue(b.commit("a")); assertEquals(4,events.single().usage)
    }
    @Test fun correctionOfAlreadyCommittedTextIsRejected() {
        val b = bridge(); b.rejectReplacement(); assertFalse(b.commit("fixed")); assertTrue(events.isEmpty())
    }
}
