package com.alexanderpeppe.pianobeam.midi

import java.nio.charset.Charset
import kotlin.math.roundToLong

object MidiFileParser {
    private val latin1: Charset = Charsets.ISO_8859_1

    data class MidiSequence(
        val title: String,
        val durationUs: Long,
        val events: List<ScheduledMidiEvent>,
        val channelLabels: Map<Int, String> = emptyMap()
    )

    data class ScheduledMidiEvent(
        val timeUs: Long,
        val data: ByteArray
    )

    private data class RawMidiEvent(
        val tick: Long,
        val data: ByteArray,
        val order: Long
    )

    private data class TempoEvent(
        val tick: Long,
        val usPerQuarter: Int,
        val order: Long
    )

    private data class TrackInfo(
        val trackName: String?,
        val instrumentName: String?,
        val channelPrefix: Int?,
        val noteChannels: Set<Int>
    )

    fun parse(bytes: ByteArray, fallbackTitle: String): MidiSequence {
        val reader = ByteReader(bytes)
        require(reader.readAscii(4) == "MThd") { "Not a Standard MIDI file" }
        val headerLength = reader.readInt32()
        require(headerLength >= 6) { "Invalid MIDI header length" }
        val format = reader.readUInt16()
        val trackCount = reader.readUInt16()
        val division = reader.readUInt16()
        if (headerLength > 6) reader.skip(headerLength - 6)
        require(format in 0..2) { "Unsupported MIDI format $format" }
        require(trackCount > 0) { "MIDI file has no tracks" }

        val rawEvents = mutableListOf<RawMidiEvent>()
        val tempos = mutableListOf<TempoEvent>()
        val trackInfos = mutableListOf<TrackInfo>()
        var title: String? = null
        var order = 0L

        repeat(trackCount) {
            if (!reader.canRead(8)) return@repeat
            val chunkId = reader.readAscii(4)
            val length = reader.readInt32()
            if (chunkId != "MTrk") {
                reader.skip(length)
                return@repeat
            }
            val trackEnd = reader.position + length
            var tick = 0L
            var runningStatus = 0
            var trackName: String? = null
            var instrumentName: String? = null
            var channelPrefix: Int? = null
            val noteChannels = mutableSetOf<Int>()

            while (reader.position < trackEnd) {
                tick += reader.readVariableLengthQuantity()
                var status = reader.readU8()
                if (status < 0x80) {
                    require(runningStatus != 0) { "Running status used before a MIDI status byte" }
                    reader.position -= 1
                    status = runningStatus
                } else if (status in 0x80..0xEF) {
                    runningStatus = status
                } else {
                    runningStatus = 0
                }

                when {
                    status == 0xFF -> {
                        val metaType = reader.readU8()
                        val metaLength = reader.readVariableLengthQuantity().toInt()
                        val data = reader.readBytes(metaLength)
                        when (metaType) {
                            0x03 -> {
                                val cleanName = cleanMetaText(data)
                                if (trackName.isNullOrBlank()) trackName = cleanName
                                if (title.isNullOrBlank()) title = cleanName
                            }
                            0x04 -> if (instrumentName.isNullOrBlank()) {
                                instrumentName = cleanMetaText(data)
                            }
                            0x20 -> if (data.size == 1) {
                                channelPrefix = ((data[0].toInt() and 0xFF) + 1).coerceIn(1, 16)
                            }
                            0x51 -> if (data.size == 3) {
                                val usPerQuarter = ((data[0].toInt() and 0xFF) shl 16) or
                                    ((data[1].toInt() and 0xFF) shl 8) or
                                    (data[2].toInt() and 0xFF)
                                tempos += TempoEvent(tick, usPerQuarter, order++)
                            }
                        }
                    }

                    status == 0xF0 || status == 0xF7 -> {
                        val sysexLength = reader.readVariableLengthQuantity().toInt()
                        val data = reader.readBytes(sysexLength)
                        val message = if (status == 0xF0) {
                            byteArrayOf(0xF0.toByte()) + data
                        } else {
                            // Escaped sysex data. If it already contains a full message, send it as-is.
                            data
                        }
                        if (message.isNotEmpty()) rawEvents += RawMidiEvent(tick, message, order++)
                    }

                    status in 0x80..0xEF -> {
                        val length = channelMessageDataLength(status)
                        val message = ByteArray(length + 1)
                        message[0] = status.toByte()
                        repeat(length) { message[it + 1] = reader.readU8().toByte() }
                        val messageType = status and 0xF0
                        if (messageType == 0x90 && message.size > 2 && (message[2].toInt() and 0xFF) > 0) {
                            noteChannels += (status and 0x0F) + 1
                        }
                        rawEvents += RawMidiEvent(tick, message, order++)
                    }

                    else -> error("Unsupported MIDI status byte 0x${status.toString(16)}")
                }
            }
            if (reader.position != trackEnd) reader.position = trackEnd
            trackInfos += TrackInfo(
                trackName = trackName,
                instrumentName = instrumentName,
                channelPrefix = channelPrefix,
                noteChannels = noteChannels
            )
        }

        val scheduled = schedule(rawEvents, tempos, division)
        val duration = scheduled.maxOfOrNull { it.timeUs } ?: 0L
        val cleanTitle = title ?: fallbackTitle.replace(Regex("\\.(mid|midi)$", RegexOption.IGNORE_CASE), "")
        return MidiSequence(cleanTitle, duration, scheduled, channelLabels(trackInfos, cleanTitle))
    }

    private fun cleanMetaText(data: ByteArray): String? =
        data.toString(latin1)
            .replace('\u0000', ' ')
            .trim()
            .replace(Regex("\\s+"), " ")
            .takeIf { it.isNotBlank() }

    private fun channelLabels(trackInfos: List<TrackInfo>, title: String): Map<Int, String> {
        val labels = mutableMapOf<Int, MutableList<String>>()
        trackInfos.forEach { info ->
            val label = info.instrumentName
                ?: info.trackName?.takeUnless { it.equals(title, ignoreCase = true) }
                ?: return@forEach
            val channels = when {
                info.noteChannels.isNotEmpty() -> info.noteChannels
                info.channelPrefix != null -> setOf(info.channelPrefix)
                else -> emptySet()
            }
            channels.filter { it in 1..16 }.forEach { channel ->
                labels.getOrPut(channel) { mutableListOf() } += label
            }
        }
        return labels.mapValues { (_, values) -> values.distinct().joinToString(" / ") }
    }

    private fun schedule(
        rawEvents: List<RawMidiEvent>,
        tempos: List<TempoEvent>,
        division: Int
    ): List<ScheduledMidiEvent> {
        val events = rawEvents.sortedWith(compareBy<RawMidiEvent> { it.tick }.thenBy { it.order })
        if (events.isEmpty()) return emptyList()

        val ppq = division and 0x7FFF
        val isPpq = (division and 0x8000) == 0
        if (!isPpq) {
            val smpteByte = ((division ushr 8) and 0xFF).toByte().toInt()
            val fps = when (val rawFps = -smpteByte) {
                24 -> 24.0
                25 -> 25.0
                29 -> 29.97
                30 -> 30.0
                else -> rawFps.coerceAtLeast(1).toDouble()
            }
            val ticksPerFrame = (division and 0xFF).coerceAtLeast(1)
            val usPerTick = 1_000_000.0 / (fps * ticksPerFrame)
            return events.map { ScheduledMidiEvent((it.tick * usPerTick).roundToLong(), it.data) }
        }

        require(ppq > 0) { "Invalid MIDI time division" }
        val tempoEvents = tempos.sortedWith(compareBy<TempoEvent> { it.tick }.thenBy { it.order })
        var tempoIndex = 0
        var currentTempo = 500_000 // microseconds per quarter note
        var lastTempoTick = 0L
        var lastTempoUs = 0L

        fun advanceTempo(toTick: Long) {
            while (tempoIndex < tempoEvents.size && tempoEvents[tempoIndex].tick <= toTick) {
                val tempo = tempoEvents[tempoIndex]
                if (tempo.tick > lastTempoTick) {
                    lastTempoUs += ((tempo.tick - lastTempoTick) * currentTempo) / ppq
                    lastTempoTick = tempo.tick
                }
                currentTempo = tempo.usPerQuarter
                tempoIndex++
            }
        }

        return events.map { event ->
            advanceTempo(event.tick)
            val eventUs = lastTempoUs + ((event.tick - lastTempoTick) * currentTempo) / ppq
            ScheduledMidiEvent(eventUs, event.data)
        }
    }

    private fun channelMessageDataLength(status: Int): Int {
        return when (status and 0xF0) {
            0xC0, 0xD0 -> 1
            else -> 2
        }
    }

    private class ByteReader(private val bytes: ByteArray) {
        var position: Int = 0

        fun canRead(count: Int): Boolean = position + count <= bytes.size

        fun readAscii(count: Int): String = readBytes(count).toString(Charsets.US_ASCII)

        fun readU8(): Int {
            require(position < bytes.size) { "Unexpected end of MIDI file" }
            return bytes[position++].toInt() and 0xFF
        }

        fun readUInt16(): Int = (readU8() shl 8) or readU8()

        fun readInt32(): Int = (readU8() shl 24) or (readU8() shl 16) or (readU8() shl 8) or readU8()

        fun readVariableLengthQuantity(): Long {
            var value = 0L
            var count = 0
            while (true) {
                val b = readU8()
                value = (value shl 7) or (b and 0x7F).toLong()
                count++
                require(count <= 4) { "Invalid MIDI variable-length quantity" }
                if ((b and 0x80) == 0) return value
            }
        }

        fun readBytes(count: Int): ByteArray {
            require(count >= 0) { "Negative byte count" }
            require(position + count <= bytes.size) { "Unexpected end of MIDI file" }
            return bytes.copyOfRange(position, position + count).also { position += count }
        }

        fun skip(count: Int) {
            require(count >= 0) { "Negative skip count" }
            require(position + count <= bytes.size) { "Unexpected end of MIDI file" }
            position += count
        }
    }
}
