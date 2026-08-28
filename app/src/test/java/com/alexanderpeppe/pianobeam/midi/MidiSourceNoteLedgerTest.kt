package com.alexanderpeppe.pianobeam.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiSourceNoteLedgerTest {
    @Test
    fun overlappingSamePitchVoicesConsumeDestinationsInFifoOrder() {
        val ledger = MidiSourceNoteLedger()

        assertTrue(ledger.recordNoteOn(sourceChannel = 3, note = 60, outputChannel = 7))
        assertTrue(ledger.recordNoteOn(sourceChannel = 3, note = 60, outputChannel = 9))

        assertEquals(7, ledger.consumeNoteOff(sourceChannel = 3, note = 60))
        assertEquals(9, ledger.consumeNoteOff(sourceChannel = 3, note = 60))
        assertNull(ledger.consumeNoteOff(sourceChannel = 3, note = 60))
    }

    @Test
    fun routeTransitionPlaceholderProtectsRetriggerFromOldNoteOff() {
        val ledger = MidiSourceNoteLedger()
        ledger.recordNoteOn(sourceChannel = 1, note = 64, outputChannel = 2)

        assertEquals(
            listOf(MidiSourceNoteLedger.ReleaseGroup(outputChannel = 2, note = 64, count = 1)),
            ledger.markAllEmittedVoicesReleased()
        )
        ledger.recordNoteOn(sourceChannel = 1, note = 64, outputChannel = 8)

        assertNull(ledger.consumeNoteOff(sourceChannel = 1, note = 64))
        assertEquals(8, ledger.consumeNoteOff(sourceChannel = 1, note = 64))
    }

    @Test
    fun suppressedAndOrphanNoteOffsNeverFallThroughToCurrentRoute() {
        val ledger = MidiSourceNoteLedger()
        ledger.recordNoteOn(sourceChannel = 5, note = 67, outputChannel = null)
        ledger.recordNoteOn(sourceChannel = 5, note = 67, outputChannel = 11)

        assertNull(ledger.consumeNoteOff(sourceChannel = 5, note = 67))
        assertEquals(11, ledger.consumeNoteOff(sourceChannel = 5, note = 67))
        assertNull(ledger.consumeNoteOff(sourceChannel = 5, note = 67))
        assertNull(ledger.consumeNoteOff(sourceChannel = 6, note = 67))
    }

    @Test
    fun failedNoteOffCanRestoreConsumedVoiceToFront() {
        val ledger = MidiSourceNoteLedger()
        ledger.recordNoteOn(sourceChannel = 4, note = 72, outputChannel = 6)
        ledger.recordNoteOn(sourceChannel = 4, note = 72, outputChannel = 10)

        val failedDestination = ledger.consumeNoteOff(sourceChannel = 4, note = 72)
        assertEquals(6, failedDestination)
        assertTrue(ledger.restoreConsumedVoice(sourceChannel = 4, note = 72, outputChannel = failedDestination!!))

        assertEquals(6, ledger.consumeNoteOff(sourceChannel = 4, note = 72))
        assertEquals(10, ledger.consumeNoteOff(sourceChannel = 4, note = 72))
    }

    @Test
    fun transitionReleaseGroupsEmittedVoicesAndRetainsEveryPlaceholder() {
        val ledger = MidiSourceNoteLedger()
        ledger.recordNoteOn(sourceChannel = 1, note = 60, outputChannel = 2)
        ledger.recordNoteOn(sourceChannel = 1, note = 60, outputChannel = 2)
        ledger.recordNoteOn(sourceChannel = 2, note = 60, outputChannel = 2)
        ledger.recordNoteOn(sourceChannel = 2, note = 61, outputChannel = 3)
        ledger.recordNoteOn(sourceChannel = 2, note = 62, outputChannel = null)

        assertEquals(
            listOf(
                MidiSourceNoteLedger.ReleaseGroup(outputChannel = 2, note = 60, count = 3),
                MidiSourceNoteLedger.ReleaseGroup(outputChannel = 3, note = 61, count = 1)
            ),
            ledger.markAllEmittedVoicesReleased()
        )
        assertTrue(ledger.markAllEmittedVoicesReleased().isEmpty())

        assertNull(ledger.consumeNoteOff(sourceChannel = 1, note = 60))
        assertNull(ledger.consumeNoteOff(sourceChannel = 1, note = 60))
        assertNull(ledger.consumeNoteOff(sourceChannel = 2, note = 60))
        assertNull(ledger.consumeNoteOff(sourceChannel = 2, note = 61))
        assertNull(ledger.consumeNoteOff(sourceChannel = 2, note = 62))
    }

    @Test
    fun sourceScopedReleaseDoesNotClearOtherSourcesOnSharedDestination() {
        val ledger = MidiSourceNoteLedger()
        ledger.recordNoteOn(sourceChannel = 1, note = 60, outputChannel = 4)
        ledger.recordNoteOn(sourceChannel = 1, note = 60, outputChannel = 4)
        ledger.recordNoteOn(sourceChannel = 1, note = 62, outputChannel = 5)
        ledger.recordNoteOn(sourceChannel = 1, note = 63, outputChannel = null)
        ledger.recordNoteOn(sourceChannel = 2, note = 60, outputChannel = 4)

        assertEquals(
            listOf(
                MidiSourceNoteLedger.ReleaseGroup(outputChannel = 4, note = 60, count = 2),
                MidiSourceNoteLedger.ReleaseGroup(outputChannel = 5, note = 62, count = 1)
            ),
            ledger.releaseAndClearSource(sourceChannel = 1)
        )

        assertNull(ledger.consumeNoteOff(sourceChannel = 1, note = 60))
        assertNull(ledger.consumeNoteOff(sourceChannel = 1, note = 63))
        assertEquals(4, ledger.consumeNoteOff(sourceChannel = 2, note = 60))
    }

    @Test
    fun outputScopedAllSoundOffRetainsPlaceholdersOnlyForThatWireChannel() {
        val ledger = MidiSourceNoteLedger()
        ledger.recordNoteOn(sourceChannel = 1, note = 60, outputChannel = 4)
        ledger.recordNoteOn(sourceChannel = 2, note = 61, outputChannel = 4)
        ledger.recordNoteOn(sourceChannel = 2, note = 62, outputChannel = 8)

        assertEquals(2, ledger.markOutputVoicesReleased(outputChannel = 4))
        assertEquals(0, ledger.markOutputVoicesReleased(outputChannel = 17))
        assertNull(ledger.consumeNoteOff(sourceChannel = 1, note = 60))
        assertNull(ledger.consumeNoteOff(sourceChannel = 2, note = 61))
        assertEquals(8, ledger.consumeNoteOff(sourceChannel = 2, note = 62))
    }

    @Test
    fun silentPrimingParticipatesInFifoBeforeNewlyEmittedVoice() {
        val ledger = MidiSourceNoteLedger()
        assertTrue(ledger.primeSilentNoteOn(sourceChannel = 7, note = 48))
        ledger.recordNoteOn(sourceChannel = 7, note = 48, outputChannel = 12)

        assertNull(ledger.consumeNoteOff(sourceChannel = 7, note = 48))
        assertEquals(12, ledger.consumeNoteOff(sourceChannel = 7, note = 48))
    }

    @Test
    fun resetClearsAllSourcesAndInvalidValuesAreRejected() {
        val ledger = MidiSourceNoteLedger()
        ledger.recordNoteOn(sourceChannel = 1, note = 0, outputChannel = 1)
        ledger.recordNoteOn(sourceChannel = 16, note = 127, outputChannel = 16)

        assertFalse(ledger.recordNoteOn(sourceChannel = 0, note = 60, outputChannel = 1))
        assertFalse(ledger.recordNoteOn(sourceChannel = 1, note = 128, outputChannel = 1))
        assertFalse(ledger.recordNoteOn(sourceChannel = 1, note = 60, outputChannel = 17))
        assertFalse(ledger.restoreConsumedVoice(sourceChannel = 1, note = 60, outputChannel = 0))

        ledger.reset()

        assertNull(ledger.consumeNoteOff(sourceChannel = 1, note = 0))
        assertNull(ledger.consumeNoteOff(sourceChannel = 16, note = 127))
        assertTrue(ledger.releaseAndClearSource(sourceChannel = 1).isEmpty())
    }
}
