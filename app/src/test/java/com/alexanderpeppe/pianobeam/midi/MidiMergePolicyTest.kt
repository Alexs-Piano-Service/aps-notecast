package com.alexanderpeppe.pianobeam.midi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiMergePolicyTest {
    @Test
    fun mergeDisabledNeverSuppressesSourceMessages() {
        assertFalse(
            MidiMergePolicy.shouldSuppressSourceMessage(
                byteArrayOf(0xC4.toByte(), 40),
                mergeAllEnabled = false
            )
        )
        assertFalse(
            MidiMergePolicy.shouldSuppressSourceMessage(
                byteArrayOf(0xB4.toByte(), 120.toByte(), 0),
                mergeAllEnabled = false
            )
        )
    }

    @Test
    fun mergeSuppressesSourceProgramAndBankSelection() {
        assertTrue(
            MidiMergePolicy.shouldSuppressSourceMessage(
                byteArrayOf(0xC4.toByte(), 40),
                mergeAllEnabled = true
            )
        )
        assertTrue(
            MidiMergePolicy.shouldSuppressSourceMessage(
                byteArrayOf(0xB4.toByte(), 0, 2),
                mergeAllEnabled = true
            )
        )
        assertTrue(
            MidiMergePolicy.shouldSuppressSourceMessage(
                byteArrayOf(0xB4.toByte(), 32, 3),
                mergeAllEnabled = true
            )
        )
        assertTrue(
            MidiMergePolicy.shouldSuppressSourceMessage(
                byteArrayOf(0xB4.toByte(), 7, 100),
                mergeAllEnabled = true
            )
        )
    }

    @Test
    fun mergeSuppressesSourceChannelModeControllers() {
        for (controller in 120..127) {
            assertTrue(
                "controller $controller",
                MidiMergePolicy.shouldSuppressSourceMessage(
                    byteArrayOf(0xBF.toByte(), controller.toByte(), 0),
                    mergeAllEnabled = true
                )
            )
        }
    }

    @Test
    fun mergePreservesNotesExpressiveMessagesAndOrdinaryControllers() {
        val messages = listOf(
            byteArrayOf(0x90.toByte(), 60, 100),
            byteArrayOf(0x80.toByte(), 60, 0),
            byteArrayOf(0xA0.toByte(), 60, 10),
            byteArrayOf(0xB0.toByte(), 10, 64),
            byteArrayOf(0xB0.toByte(), 64, 127.toByte()),
            byteArrayOf(0xD0.toByte(), 40),
            byteArrayOf(0xE0.toByte(), 0, 64)
        )

        messages.forEach { message ->
            assertFalse(
                "status ${(message[0].toInt() and 0xFF).toString(16)}",
                MidiMergePolicy.shouldSuppressSourceMessage(message, mergeAllEnabled = true)
            )
        }
    }

    @Test
    fun systemAndMalformedMessagesArePreserved() {
        assertFalse(
            MidiMergePolicy.shouldSuppressSourceMessage(
                byteArrayOf(0xF0.toByte(), 0x7E, 0xF7.toByte()),
                mergeAllEnabled = true
            )
        )
        assertFalse(MidiMergePolicy.shouldSuppressSourceMessage(byteArrayOf(), mergeAllEnabled = true))
        assertFalse(
            MidiMergePolicy.shouldSuppressSourceMessage(
                byteArrayOf(0xB0.toByte()),
                mergeAllEnabled = true
            )
        )
    }

    @Test
    fun acousticGrandProgramChangeUsesRequestedWireChannel() {
        assertArrayEquals(
            byteArrayOf(0xC5.toByte(), 0),
            MidiMergePolicy.acousticGrandProgramChange(6)
        )
        assertNull(MidiMergePolicy.acousticGrandProgramChange(0))
        assertNull(MidiMergePolicy.acousticGrandProgramChange(17))
    }
}
