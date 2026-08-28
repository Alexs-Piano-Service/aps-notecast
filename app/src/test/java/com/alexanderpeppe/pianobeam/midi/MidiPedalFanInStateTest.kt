package com.alexanderpeppe.pianobeam.midi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiPedalFanInStateTest {
    @Test
    fun binaryModeUsesThresholdForEveryRawValueAndController() {
        MidiPedalFanInState.SupportedControllers.forEach { controller ->
            for (rawValue in 0..127) {
                val state = MidiPedalFanInState()
                val messages = state.update(
                    sourceChannel = 1,
                    controller = controller,
                    rawValue = rawValue,
                    destinationsBySource = mapOf(1 to listOf(5)),
                    valueMode = MidiPedalFanInState.ValueMode.Binary
                )
                val expected = if (rawValue >= 64) 127 else 0
                if (expected == 0) {
                    assertTrue("controller=$controller value=$rawValue", messages.isEmpty())
                } else {
                    assertEquals(
                        listOf(output(5, controller, expected)),
                        messages
                    )
                }
                assertEquals(rawValue, state.rawValue(1, controller))
            }
        }
    }

    @Test
    fun continuousModePreservesEveryRawValueAndController() {
        MidiPedalFanInState.SupportedControllers.forEach { controller ->
            val state = MidiPedalFanInState()
            for (rawValue in 0..127) {
                val messages = state.update(
                    sourceChannel = 16,
                    controller = controller,
                    rawValue = rawValue,
                    destinationsBySource = mapOf(16 to listOf(1)),
                    valueMode = MidiPedalFanInState.ValueMode.Continuous
                )
                if (rawValue == 0) {
                    assertTrue(messages.isEmpty())
                } else {
                    assertEquals(listOf(output(1, controller, rawValue)), messages)
                }
                assertEquals(rawValue, state.rawValue(16, controller))
            }
        }
    }

    @Test
    fun binaryFanInStaysPressedUntilEverySourceReleases() {
        val state = MidiPedalFanInState()
        val routes = mapOf(1 to listOf(7), 2 to listOf(7))

        assertEquals(
            listOf(output(7, 64, 127)),
            state.update(1, 64, 127, routes, MidiPedalFanInState.ValueMode.Binary)
        )
        assertTrue(state.update(2, 64, 80, routes, MidiPedalFanInState.ValueMode.Binary).isEmpty())
        assertTrue(state.update(1, 64, 0, routes, MidiPedalFanInState.ValueMode.Binary).isEmpty())
        assertEquals(
            listOf(output(7, 64, 0)),
            state.update(2, 64, 63, routes, MidiPedalFanInState.ValueMode.Binary)
        )
    }

    @Test
    fun continuousFanInUsesMaximumAndFallsBackToNextSource() {
        val state = MidiPedalFanInState()
        val routes = mapOf(1 to listOf(8), 2 to listOf(8), 3 to listOf(8))

        assertEquals(listOf(output(8, 66, 40)), state.update(1, 66, 40, routes, continuous))
        assertEquals(listOf(output(8, 66, 100)), state.update(2, 66, 100, routes, continuous))
        assertTrue(state.update(3, 66, 60, routes, continuous).isEmpty())
        assertEquals(listOf(output(8, 66, 60)), state.update(2, 66, 20, routes, continuous))
        assertEquals(listOf(output(8, 66, 40)), state.update(3, 66, 0, routes, continuous))
        assertEquals(listOf(output(8, 66, 20)), state.update(1, 66, 0, routes, continuous))
        assertEquals(listOf(output(8, 66, 0)), state.update(2, 66, 0, routes, continuous))
    }

    @Test
    fun fanOutEmitsEachDistinctDestinationInChannelOrder() {
        val state = MidiPedalFanInState()

        assertEquals(
            listOf(output(2, 67, 99), output(9, 67, 99), output(16, 67, 99)),
            state.update(
                sourceChannel = 4,
                controller = 67,
                rawValue = 99,
                destinationsBySource = mapOf(4 to listOf(16, 2, 9, 2)),
                valueMode = continuous
            )
        )
    }

    @Test
    fun everyMidiSourceCanRouteToEveryMidiDestination() {
        for (sourceChannel in 1..16) {
            for (destinationChannel in 1..16) {
                val state = MidiPedalFanInState()
                assertEquals(
                    "source=$sourceChannel destination=$destinationChannel",
                    listOf(output(destinationChannel, 64, 127)),
                    state.update(
                        sourceChannel = sourceChannel,
                        controller = 64,
                        rawValue = 64,
                        destinationsBySource = mapOf(sourceChannel to listOf(destinationChannel)),
                        valueMode = MidiPedalFanInState.ValueMode.Binary
                    )
                )
            }
        }
    }

    @Test
    fun normalRerouteReleasesOldDestinationBeforeRestoringNewOne() {
        val state = MidiPedalFanInState()
        state.update(3, 69, 120, mapOf(3 to listOf(12)), continuous)

        assertEquals(
            listOf(output(12, 69, 0), output(2, 69, 120)),
            state.changedCurrentStateMessages(
                destinationsBySource = mapOf(3 to listOf(2)),
                valueMode = continuous
            )
        )
        assertTrue(
            state.changedCurrentStateMessages(mapOf(3 to listOf(2)), continuous).isEmpty()
        )
    }

    @Test
    fun primeChangesRawStateWithoutEmittingOrPretendingItWasSent() {
        val state = MidiPedalFanInState()

        assertTrue(state.prime(1, 64, 100))
        assertTrue(state.updateWithoutEmitting(2, 64, 80))
        assertEquals(100, state.rawValue(1, 64))
        assertEquals(80, state.rawValue(2, 64))

        assertEquals(
            listOf(output(6, 64, 127)),
            state.changedCurrentStateMessages(
                destinationsBySource = mapOf(1 to listOf(6), 2 to listOf(6)),
                valueMode = MidiPedalFanInState.ValueMode.Binary
            )
        )
    }

    @Test
    fun forcedMessagesIncludeZerosAndRepeatUnchangedCurrentState() {
        val state = MidiPedalFanInState()
        state.prime(1, 64, 90)
        state.prime(2, 66, 45)
        val routes = mapOf(1 to listOf(4), 2 to listOf(4, 10))
        val expected = listOf(
            output(4, 64, 90),
            output(4, 66, 45),
            output(4, 67, 0),
            output(4, 69, 0),
            output(10, 64, 0),
            output(10, 66, 45),
            output(10, 67, 0),
            output(10, 69, 0)
        )

        assertEquals(expected, state.forcedCurrentStateMessages(routes, continuous))
        assertEquals(expected, state.forcedCurrentStateMessages(routes, continuous))
        assertTrue(state.changedCurrentStateMessages(routes, continuous).isEmpty())
    }

    @Test
    fun rawValuesSurviveOutputModeChanges() {
        val state = MidiPedalFanInState()
        val routes = mapOf(5 to listOf(11))
        state.prime(5, 67, 63)

        assertEquals(
            listOf(output(11, 64, 0), output(11, 66, 0), output(11, 67, 63), output(11, 69, 0)),
            state.forcedCurrentStateMessages(routes, continuous)
        )
        assertEquals(
            listOf(output(11, 67, 0)),
            state.changedCurrentStateMessages(routes, MidiPedalFanInState.ValueMode.Binary)
        )
        assertEquals(63, state.rawValue(5, 67))
    }

    @Test
    fun controllersRemainIndependentOnSharedDestination() {
        val state = MidiPedalFanInState()
        val routes = mapOf(1 to listOf(3), 2 to listOf(3))

        MidiPedalFanInState.SupportedControllers.forEachIndexed { index, controller ->
            assertEquals(
                listOf(output(3, controller, 70 + index)),
                state.update(1, controller, 70 + index, routes, continuous)
            )
        }
        MidiPedalFanInState.SupportedControllers.forEachIndexed { index, controller ->
            assertEquals(70 + index, state.rawValue(1, controller))
            assertEquals(0, state.rawValue(2, controller))
        }
    }

    @Test
    fun invalidInputsAndRoutesAreIgnored() {
        val state = MidiPedalFanInState()
        val invalidRoutes = mapOf(
            0 to listOf(4),
            1 to listOf(0, 17),
            17 to listOf(4)
        )

        assertFalse(state.updateWithoutEmitting(0, 64, 100))
        assertFalse(state.updateWithoutEmitting(17, 64, 100))
        assertFalse(state.updateWithoutEmitting(1, 63, 100))
        assertFalse(state.updateWithoutEmitting(1, 64, -1))
        assertFalse(state.updateWithoutEmitting(1, 64, 128))
        assertTrue(state.update(1, 64, 100, invalidRoutes, continuous).isEmpty())
        assertTrue(state.forcedCurrentStateMessages(invalidRoutes, continuous).isEmpty())
        assertNull(state.rawValue(0, 64))
        assertNull(state.rawValue(1, 65))
        assertEquals(100, state.rawValue(1, 64))
    }

    @Test
    fun clearResetsRawValuesAndOutputHistory() {
        val state = MidiPedalFanInState()
        val routes = mapOf(1 to listOf(5))
        state.update(1, 64, 127, routes, MidiPedalFanInState.ValueMode.Binary)

        state.clear()

        MidiPedalFanInState.SupportedControllers.forEach { controller ->
            assertEquals(0, state.rawValue(1, controller))
        }
        assertTrue(
            state.changedCurrentStateMessages(routes, MidiPedalFanInState.ValueMode.Binary).isEmpty()
        )
        assertEquals(
            MidiPedalFanInState.SupportedControllers.map { controller -> output(5, controller, 0) },
            state.forcedCurrentStateMessages(routes, MidiPedalFanInState.ValueMode.Binary)
        )

        state.update(1, 64, 127, routes, MidiPedalFanInState.ValueMode.Binary)
        state.reset()
        assertEquals(0, state.rawValue(1, 64))
    }

    @Test
    fun outputMessageEncodesAllMidiChannelEndpoints() {
        assertArrayEquals(
            byteArrayOf(0xB0.toByte(), 64, 127.toByte()),
            output(1, 64, 127).toMidiData()
        )
        assertArrayEquals(
            byteArrayOf(0xBF.toByte(), 69, 0),
            output(16, 69, 0).toMidiData()
        )
    }

    private fun output(destinationChannel: Int, controller: Int, value: Int) =
        MidiPedalFanInState.OutputMessage(destinationChannel, controller, value)

    private val continuous = MidiPedalFanInState.ValueMode.Continuous
}
