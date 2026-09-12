package com.alexanderpeppe.pianobeam.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiStreamParserTest {
    @Test
    fun completeNoteAndEveryCallbackPartitionProduceTheSameEvents() {
        assertEveryPartition(
            bytes(0x90, 60, 100, 62, 110, 0x80, 60, 0, 62, 0),
            listOf(bytes(0x90, 60, 100), bytes(0x90, 62, 110), bytes(0x80, 60, 0), bytes(0x80, 62, 0))
        )
    }

    @Test
    fun realTimeInsideNotesAndBetweenRunningStatusMessagesDoesNotConsumeData() {
        assertEveryPartition(
            bytes(0x90, 60, 0xF8, 100, 0xFE, 62, 0xFF, 110),
            listOf(bytes(0xF8), bytes(0x90, 60, 100), bytes(0xFE), bytes(0xFF), bytes(0x90, 62, 110))
        )
    }

    @Test
    fun sysExRetainsContinuationPayloadAndSeparatesRealTime() {
        assertEveryPartition(
            bytes(0xF0, 0x7D, 1, 0xF8, 2, 3, 0xFE, 0xF7, 0x90, 60, 100),
            listOf(bytes(0xF8), bytes(0xFE), bytes(0xF0, 0x7D, 1, 2, 3, 0xF7), bytes(0x90, 60, 100))
        )
    }

    @Test
    fun systemCommonCancelsRunningStatusAndCanSpanCallbacks() {
        assertEveryPartition(
            bytes(0xC2, 40, 41, 0xF2, 1, 0xF8, 2, 60, 100, 0xF6, 0xD2, 50),
            listOf(bytes(0xC2, 40), bytes(0xC2, 41), bytes(0xF8), bytes(0xF2, 1, 2), bytes(0xF6), bytes(0xD2, 50))
        )
    }

    @Test
    fun allChannelMessageClassesSupportRunningStatus() {
        for (status in 0x80..0xEF) {
            val short = (status and 0xF0) == 0xC0 || (status and 0xF0) == 0xD0
            val first = if (short) bytes(status, 10) else bytes(status, 10, 20)
            val second = if (short) bytes(status, 30) else bytes(status, 30, 40)
            assertEveryPartition(first + second.drop(1), listOf(first, second))
        }
    }

    @Test
    fun newStatusAbortsIncompleteMessageWithoutCreatingInvalidData() {
        assertEveryPartition(
            bytes(0x90, 60, 0x80, 60, 0, 0xF0, 0x7D, 0xC0, 40, 0xF7),
            listOf(bytes(0x80, 60, 0), bytes(0xC0, 40))
        )
    }

    @Test
    fun incompleteInputIsNotEmittedOrSharedWithAnotherInput() {
        val first = MidiStreamParser()
        val second = MidiStreamParser()
        assertTrue(parse(first, bytes(0x90, 60)).isEmpty())
        assertTrue(parse(second, bytes(100)).isEmpty())
        assertEquals(listOf(listOf(0x90, 60, 100)), data(parse(first, bytes(100))))
        assertTrue(parse(first, bytes(0xF0, 0x7D, 1)).isEmpty())
        assertTrue(parse(second, bytes(2, 0xF7)).isEmpty())
        assertEquals(listOf(listOf(0xF0, 0x7D, 1, 2, 0xF7)), data(parse(first, bytes(2, 0xF7))))
    }

    @Test
    fun messageTimestampComesFromFirstByteIncludingRunningStatusAndSysEx() {
        val parser = MidiStreamParser()
        assertTrue(parse(parser, bytes(0x90, 60), 100).isEmpty())
        val note = parse(parser, bytes(0xF8, 100, 62), 200)
        assertEquals(listOf(200L, 100L), note.map { it.timestampNs })
        assertEquals(200L, parse(parser, bytes(110), 300).single().timestampNs)
        assertTrue(parse(parser, bytes(0xF0, 0x7D), 400).isEmpty())
        assertEquals(400L, parse(parser, bytes(1, 0xF7), 500).single().timestampNs)
    }

    @Test
    fun callbackBufferCanBeReusedWithoutChangingPendingOrCompletedEvents() {
        val parser = MidiStreamParser()
        val buffer = bytes(0x90, 60)
        assertTrue(parse(parser, buffer).isEmpty())
        buffer.fill(0)
        val note = parse(parser, bytes(100)).single()
        parse(parser, bytes(62, 110))
        assertEquals(listOf(0x90, 60, 100), note.data.unsigned())
    }

    @Test
    fun everyRealTimeBytePreservesRunningStatusAndPendingSystemCommonData() {
        for (realTime in 0xF8..0xFF) {
            assertEveryPartition(
                bytes(0xD0, 50, realTime, 60, 0xF1, realTime, 1, 0xF3, realTime, 2),
                listOf(
                    bytes(0xD0, 50), bytes(realTime), bytes(0xD0, 60), bytes(realTime), bytes(0xF1, 1),
                    bytes(realTime), bytes(0xF3, 2)
                )
            )
        }
    }

    @Test
    fun flushingDiscardsPendingDataAndRunningStatus() {
        val parser = MidiStreamParser()
        parse(parser, bytes(0x90, 60))
        parser.reset()
        assertTrue(parse(parser, bytes(100, 62, 110)).isEmpty())
        assertEquals(listOf(listOf(0xC0, 40)), data(parse(parser, bytes(0xC0, 40))))
        parse(parser, bytes(0xF0, 0x7D, 1))
        parser.reset()
        assertTrue(parse(parser, bytes(2, 0xF7)).isEmpty())
        assertEquals(listOf(listOf(0xF0, 0x7D, 3, 0xF7)), data(parse(parser, bytes(0xF0, 0x7D, 3, 0xF7))))
    }

    @Test
    fun fragmentedRecordingSurvivesMidiFileWriteAndRead() {
        val stream = bytes(0x90, 60, 0xF8, 100, 62, 110, 0xF0, 0x7D, 1, 2, 0xF7, 0x80, 60, 0, 62, 0)
        val parser = MidiStreamParser()
        val events = stream.flatMap { parse(parser, byteArrayOf(it)) }
            .map { MidiFileWriter.RecordedEvent(0, it.data) }
        val recorded = MidiFileParser.parse(MidiFileWriter.write("Regression", events), "Regression")
        assertEquals(
            listOf(
                listOf(0x90, 60, 100), listOf(0x90, 62, 110), listOf(0xF0, 0x7D, 1, 2, 0xF7),
                listOf(0x80, 60, 0), listOf(0x80, 62, 0)
            ),
            recorded.events.map { it.data.unsigned() }
        )
    }

    private fun assertEveryPartition(stream: ByteArray, expected: List<ByteArray>) {
        val expectedData = expected.map { it.unsigned() }
        for (mask in 0 until (1 shl (stream.size - 1))) {
            val parser = MidiStreamParser()
            val events = mutableListOf<MidiStreamParser.Message>()
            var start = 0
            for (end in 1..stream.size) {
                if (end == stream.size || mask and (1 shl (end - 1)) != 0) {
                    // Surround each callback with unrelated bytes to exercise offset/count as well.
                    val packet = bytes(0xFF) + stream.copyOfRange(start, end) + bytes(0xFF)
                    events += parser.parse(packet, 1, end - start, 123L)
                    assertTrue(parser.parse(packet, 1, 0, 123L).isEmpty())
                    start = end
                }
            }
            assertEquals("callback partition $mask for ${stream.unsigned()}", expectedData, data(events))
        }
    }

    private fun parse(parser: MidiStreamParser, bytes: ByteArray, timestamp: Long = 123L) =
        parser.parse(bytes, 0, bytes.size, timestamp)

    private fun data(messages: List<MidiStreamParser.Message>) = messages.map { it.data.unsigned() }
    private fun ByteArray.unsigned() = map { it.toInt() and 0xFF }
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
