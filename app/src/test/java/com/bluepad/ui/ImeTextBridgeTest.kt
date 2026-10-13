package com.bluepad.ui
import com.bluepad.hid.UsKeyboard
import org.junit.Assert.*
import org.junit.Test
class ImeTextBridgeTest {
    @Test fun oversizedCommitIsRejectedBeforeAnyMappingAndBlocksUntilReset() {
        var mapped = 0
        val output = mutableListOf<UsKeyboard.Stroke>()
        val b = ImeTextBridge({ output.add(it) },{}, { mapped++; UsKeyboard.character(it) }).apply { enabled = true }
        assertFalse(b.commit("a".repeat(513)))
        assertFalse(b.commit("a".repeat(100_000)))
        assertEquals(0,mapped); assertTrue(output.isEmpty())
        assertFalse(b.commit("a")); assertFalse(b.delete(1,0)); assertEquals(0,mapped)
        b.reset(); assertTrue(b.commit("a")); assertEquals(1,mapped); assertEquals(1,output.size)
    }
    @Test fun finalCommitBoundariesKeepExistingAcceptanceAndOutputCounts() {
        for (length in listOf(0,1,511,512,513)) {
            val output = mutableListOf<UsKeyboard.Stroke>()
            val b = ImeTextBridge({ output.add(it) },{}).apply { enabled = true }
            assertEquals(length <= 512,b.commit("a".repeat(length)))
            assertEquals(if(length <= 512) length else 0,output.size)
        }
    }
    @Test fun invalidLastCharacterAtLimitDoesNotTransmitValidPrefix() {
        val b = bridge(); assertFalse(b.commit("a".repeat(511)+"é")); assertTrue(events.isEmpty())
    }
    @Test fun longCompositionCanBeShortenedBeforeSupportedFinalCommit() {
        val b = bridge(); b.compose("a".repeat(4096)); assertTrue(b.composing)
        b.compose("A".repeat(512)); assertTrue(b.finish()); assertFalse(b.composing)
        b.finish(); assertEquals(512,events.size); assertTrue(events.all { it == UsKeyboard.Stroke(4,2) })
    }
    @Test fun supportedCommitPreservesKeyboardPressReleasePairs() {
        val state = com.bluepad.hid.KeyboardState()
        val reports = mutableListOf<ByteArray>()
        val b = ImeTextBridge({ reports.addAll(state.tap(it)) },{}).apply { enabled = true }
        assertTrue(b.commit("Aa")); assertEquals(4,reports.size)
        assertArrayEquals(byteArrayOf(2,0,4,0,0,0,0,0),reports[0])
        assertArrayEquals(ByteArray(8),reports[1])
        assertArrayEquals(byteArrayOf(0,0,4,0,0,0,0,0),reports[2])
        assertArrayEquals(ByteArray(8),reports[3])
    }
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
