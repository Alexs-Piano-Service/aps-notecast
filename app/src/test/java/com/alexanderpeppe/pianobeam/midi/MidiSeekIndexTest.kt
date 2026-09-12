package com.alexanderpeppe.pianobeam.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MidiSeekIndexTest {
    @Test
    fun backwardSeekRestoresOriginalProgramWithoutAnOverride() {
        val bytes = javaClass.getResourceAsStream("/midi/notecast_seek_program_state.mid")!!.use { it.readBytes() }
        val sequence = MidiFileParser.parse(bytes, "Seek regression")
        val index = MidiSeekIndex(sequence.events, checkpointInterval = 2)
        assertEquals(73, index.positionAt(12_000_001).state.channels.getValue(4).program)
        val earlier = index.positionAt(2_000_000)
        assertEquals(40, earlier.state.channels.getValue(4).program)
        assertEquals(
            listOf(listOf(0xB3, 0, 0), listOf(0xB3, 32, 0), listOf(0xC3, 40)),
            earlier.state.channels.getValue(4).programMessages(4).unsigned()
        )
        assertEquals(2_000_000L, sequence.events[earlier.eventIndex].timeUs)
    }

    @Test
    fun bankSelectionsAreLatchedByProgramChangeAndLaterSelectionsStayPending() {
        val index = MidiSeekIndex(listOf(
            event(0, 0xB3, 0, 1), event(0, 0xB3, 32, 2), event(0, 0xC3, 40),
            event(10, 0xB3, 0, 3), event(10, 0xB3, 32, 4), event(20, 0xC3, 73)
        ))
        assertEquals(
            listOf(listOf(0xB3, 0, 1), listOf(0xB3, 32, 2), listOf(0xC3, 40), listOf(0xB3, 0, 3), listOf(0xB3, 32, 4)),
            index.positionAt(15).state.channels.getValue(4).programMessages(4).unsigned()
        )
        assertEquals(
            listOf(listOf(0xB3, 0, 3), listOf(0xB3, 32, 4), listOf(0xC3, 73)),
            index.positionAt(21).state.channels.getValue(4).programMessages(4).unsigned()
        )
    }

    @Test
    fun defaultBanksAndProgramOverrideReplaceLaterBankSelections() {
        val channel = MidiPlaybackState.Channel()
        assertEquals(listOf(listOf(0xB3, 0, 0), listOf(0xB3, 32, 0), listOf(0xC3, 0)), channel.programMessages(4).unsigned())
        channel.bankMsb = 12
        channel.bankLsb = 9
        channel.pendingBankMsb = 14
        channel.program = 73
        assertEquals(
            listOf(listOf(0xB7, 0, 0), listOf(0xB7, 32, 0), listOf(0xC7, 40)),
            channel.programMessages(8, overrideProgram = 40).unsigned()
        )
        assertEquals(73, channel.program)
    }

    @Test
    fun controllersPitchBendAndPressureRestoreAndDoNotLeakFromFutureEvents() {
        val index = MidiSeekIndex(listOf(
            event(1, 0xB3, 1, 23), event(1, 0xB3, 10, 20), event(1, 0xB3, 11, 90),
            event(1, 0xB3, 7, 80), event(1, 0xE3, 17, 80), event(1, 0xD3, 25),
            event(10, 0xB3, 1, 70), event(10, 0xB3, 74, 100), event(10, 0xE3, 0, 32)
        ))
        val messages = index.positionAt(2).state.controllerMessages(index.sourceChannels, index.controllersByChannel).unsigned()
        for (expected in listOf(listOf(0xB3, 1, 23), listOf(0xB3, 10, 20), listOf(0xB3, 11, 90),
            listOf(0xB3, 7, 80), listOf(0xB3, 74, 64), listOf(0xE3, 17, 80), listOf(0xD3, 25))) {
            assertTrue("missing $expected in $messages", expected in messages)
        }
        val initial = index.positionAt(0).state.controllerMessages(index.sourceChannels, index.controllersByChannel).unsigned()
        assertTrue(listOf(0xB3, 1, 0) in initial)
        assertTrue(listOf(0xB3, 10, 64) in initial)
        assertTrue(listOf(0xB3, 11, 127) in initial)
        assertTrue(listOf(0xE3, 0, 64) in initial)
    }

    @Test
    fun resetAllControllersResetsPerformanceStateAndKeepsProgramVolumePanAndEffects() {
        val events = listOf(
            event(0, 0xC3, 40), event(0, 0xB3, 1, 99), event(0, 0xB3, 7, 88), event(0, 0xB3, 10, 20),
            event(0, 0xB3, 11, 66), event(0, 0xB3, 64, 42), event(0, 0xB3, 91, 50),
            event(0, 0xE3, 0, 80), event(1, 0xB3, 121, 0)
        )
        val channel = MidiSeekIndex(events).positionAt(2).state.channels.getValue(4)
        assertEquals(40, channel.program)
        assertEquals(88, channel.controllers[7])
        assertEquals(20, channel.controllers[10])
        assertEquals(50, channel.controllers[91])
        assertEquals(0, channel.controllers[1])
        assertEquals(127, channel.controllers[11])
        assertEquals(0, channel.controllers[64])
        assertEquals(8192, channel.pitchBend)
    }

    @Test
    fun continuousAndBinaryPedalsRestoreAfterSeekAndExternalRelease() {
        val events = MidiPedalFanInState.SupportedControllers.flatMap { controller ->
            listOf(event(1, 0xB3, controller, 42), event(10, 0xB3, controller, 100))
        }.sortedBy { it.timeUs }
        val index = MidiSeekIndex(events)
        val state = index.positionAt(2).state
        val pedals = MidiPedalFanInState()
        state.channels.forEach { (channel, channelState) ->
            MidiPedalFanInState.SupportedControllers.forEach { pedals.prime(channel, it, channelState.controllers[it]) }
        }
        val routes = mapOf(4 to listOf(8))
        repeat(2) { // forced restoration must resend even after an external pause released the wire state
            assertTrue(pedals.forcedCurrentStateMessages(routes, MidiPedalFanInState.ValueMode.Continuous).all { it.value == 42 })
        }
        assertTrue(pedals.forcedCurrentStateMessages(routes, MidiPedalFanInState.ValueMode.Binary).all { it.value == 0 })
    }

    @Test
    fun parameterValuesRestoreWithSelectorsAndAbsoluteValuesInsteadOfReplayingIncrements() {
        val state = MidiPlaybackState()
        listOf(
            bytes(0xB3, 101, 0), bytes(0xB3, 100, 0), bytes(0xB3, 6, 12), bytes(0xB3, 38, 50),
            bytes(0xB3, 96, 0), bytes(0xB3, 99, 1), bytes(0xB3, 98, 2), bytes(0xB3, 6, 10), bytes(0xB3, 38, 20)
        ).forEach(state::accept)
        val messages = state.channels.getValue(4).parameterMessages(4).unsigned()
        assertEquals(listOf(listOf(0xB3, 101, 0), listOf(0xB3, 100, 0), listOf(0xB3, 6, 12), listOf(0xB3, 38, 51)), messages.take(4))
        assertFalse(messages.any { it[1] == 96 || it[1] == 97 })
        assertEquals(listOf(listOf(0xB3, 99, 1), listOf(0xB3, 98, 2)), messages.takeLast(2))
        val restored = MidiPlaybackState()
        messages.forEach { restored.accept(it.map(Int::toByte).toByteArray()) }
        assertEquals(messages, restored.channels.getValue(4).parameterMessages(4).unsigned())
    }

    @Test
    fun noteLedgerStateContainsOnlyOutstandingVoicesAndDoesNotEmitOldNotes() {
        val index = MidiSeekIndex(listOf(
            event(0, 0x93, 60, 100), event(1, 0x93, 60, 100), event(2, 0x83, 60, 0),
            event(3, 0x93, 62, 100), event(4, 0xB3, 123, 0)
        ))
        assertEquals(1, index.positionAt(3).state.channels.getValue(4).notes[60])
        assertEquals(0, index.positionAt(5).state.channels.getValue(4).notes.sum())
        val messages = index.positionAt(3).state.controllerMessages(index.sourceChannels, index.controllersByChannel)
        assertFalse(messages.any { (it[0].toInt() and 0xF0) in listOf(0x80, 0x90) })
    }

    @Test
    fun checkpointsMatchLinearReplayForRandomSeeksAndCannotBeMutatedByCallers() {
        val random = Random(42)
        val events = (0 until 5000).map { index ->
            val channel = random.nextInt(16)
            when (index % 5) {
                0 -> event(index.toLong(), 0xB0 or channel, 1, random.nextInt(128))
                1 -> event(index.toLong(), 0xC0 or channel, random.nextInt(128))
                2 -> event(index.toLong(), 0x90 or channel, 60, 100)
                3 -> event(index.toLong(), 0x80 or channel, 60, 0)
                else -> event(index.toLong(), 0xE0 or channel, random.nextInt(128), 64)
            }
        }
        val index = MidiSeekIndex(events, checkpointInterval = 64)
        for (time in listOf(0, 63, 64, 65, 5000, 6000) + List(30) { random.nextInt(5000) }) {
            val expected = MidiPlaybackState()
            events.takeWhile { it.timeUs < time }.forEach { expected.accept(it.data) }
            val actual = index.positionAt(time.toLong())
            assertEquals(events.count { it.timeUs < time }, actual.eventIndex)
            assertEquals(expected.controllerMessages(index.sourceChannels, index.controllersByChannel).unsigned(),
                actual.state.controllerMessages(index.sourceChannels, index.controllersByChannel).unsigned())
            expected.channels.forEach { (channel, state) ->
                assertEquals(state.programMessages(channel).unsigned(), actual.state.channels.getValue(channel).programMessages(channel).unsigned())
                assertEquals(state.notes.toList(), actual.state.channels.getValue(channel).notes.toList())
            }
            actual.state.accept(bytes(0xC3, 127))
            assertEquals(expected.channels[4]?.program, index.positionAt(time.toLong()).state.channels[4]?.program)
        }
    }

    private fun event(time: Long, vararg data: Int) = MidiFileParser.ScheduledMidiEvent(time, bytes(*data))
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun List<ByteArray>.unsigned() = map { data -> data.map { it.toInt() and 0xFF } }
}
