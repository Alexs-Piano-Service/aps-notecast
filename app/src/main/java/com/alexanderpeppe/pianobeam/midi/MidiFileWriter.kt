package com.alexanderpeppe.pianobeam.midi

import java.io.ByteArrayOutputStream

object MidiFileWriter {
    private const val PPQ = 480
    private const val DEFAULT_TEMPO_US_PER_QUARTER = 500_000

    data class RecordedEvent(
        val timeUs: Long,
        val data: ByteArray
    )

    fun write(title: String, events: List<RecordedEvent>): ByteArray {
        val cleanEvents = events
            .filter { it.data.isNotEmpty() }
            .sortedBy { it.timeUs }
        val track = ByteArrayOutputStream()

        val titleData = titleBytes(title)
        track.writeVariableLength(0)
        track.write(byteArrayOf(0xFF.toByte(), 0x03))
        track.writeVariableLength(titleData.size)
        track.write(titleData)

        track.writeVariableLength(0)
        track.write(byteArrayOf(0xFF.toByte(), 0x51, 0x03, 0x07, 0xA1.toByte(), 0x20))

        var lastTick = 0L
        cleanEvents.forEach { event ->
            val tick = ((event.timeUs.coerceAtLeast(0L) * PPQ) / DEFAULT_TEMPO_US_PER_QUARTER).coerceAtLeast(lastTick)
            val midiData = event.data
            if (isWritableEvent(midiData)) {
                track.writeVariableLength((tick - lastTick).toInt())
                writeMidiEvent(track, midiData)
                lastTick = tick
            }
        }

        track.writeVariableLength(0)
        track.write(byteArrayOf(0xFF.toByte(), 0x2F, 0x00))

        val out = ByteArrayOutputStream()
        out.writeAscii("MThd")
        out.writeInt32(6)
        out.writeInt16(0)
        out.writeInt16(1)
        out.writeInt16(PPQ)
        out.writeAscii("MTrk")
        out.writeInt32(track.size())
        out.write(track.toByteArray())
        return out.toByteArray()
    }

    private fun titleBytes(title: String): ByteArray =
        title.trim().ifBlank { "APS NoteCast Recording" }.toByteArray(Charsets.ISO_8859_1)

    private fun isWritableEvent(data: ByteArray): Boolean {
        val status = data.first().toInt() and 0xFF
        return status in 0x80..0xEF || status == 0xF0 || status == 0xF7
    }

    private fun writeMidiEvent(out: ByteArrayOutputStream, data: ByteArray) {
        val status = data.first().toInt() and 0xFF
        when {
            status in 0x80..0xEF -> out.write(data)
            status == 0xF0 || status == 0xF7 -> {
                out.write(status)
                out.writeVariableLength(data.size - 1)
                out.write(data, 1, data.size - 1)
            }
        }
    }

    private fun ByteArrayOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun ByteArrayOutputStream.writeInt16(value: Int) {
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeInt32(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeVariableLength(rawValue: Int) {
        var value = rawValue.coerceAtLeast(0)
        val bytes = mutableListOf(value and 0x7F)
        value = value ushr 7
        while (value > 0) {
            bytes += (value and 0x7F) or 0x80
            value = value ushr 7
        }
        bytes.asReversed().forEach { write(it) }
    }
}
