package com.alexanderpeppe.pianobeam.midi

import java.io.ByteArrayOutputStream

/** One MIDI 1.0 input stream. Call under the input's lock; create a new instance on reconnect. */
class MidiStreamParser {
    data class Message(val data: ByteArray, val timestampNs: Long)

    private var runningStatus = 0
    private var pendingStatus = 0
    private val pendingData = ByteArray(3)
    private var pendingSize = 0
    private var pendingLength = 0
    private var messageTimestampNs = 0L
    private var sysEx: ByteArrayOutputStream? = null

    fun reset() {
        runningStatus = 0
        pendingStatus = 0
        pendingSize = 0
        pendingLength = 0
        messageTimestampNs = 0L
        sysEx = null
    }

    fun parse(bytes: ByteArray, offset: Int, count: Int, timestampNs: Long): List<Message> {
        require(offset >= 0 && count >= 0 && offset <= bytes.size - count)
        val messages = mutableListOf<Message>()
        for (index in offset until offset + count) {
            val value = bytes[index].toInt() and 0xFF
            if (value >= 0xF8) {
                // Real-time bytes may occur anywhere and never consume data or cancel running status.
                messages += Message(byteArrayOf(value.toByte()), timestampNs)
                continue
            }

            val exclusive = sysEx
            if (exclusive != null && value < 0x80) {
                exclusive.write(value)
                continue
            }
            if (exclusive != null && value == 0xF7) {
                exclusive.write(value)
                messages += Message(exclusive.toByteArray(), messageTimestampNs)
                sysEx = null
                continue
            }

            if (value >= 0x80) {
                // A new non-real-time status aborts an incomplete message, including SysEx.
                sysEx = null
                pendingStatus = 0
                pendingSize = 0
                runningStatus = if (value < 0xF0) value else 0
                messageTimestampNs = timestampNs
                if (value == 0xF0) {
                    sysEx = ByteArrayOutputStream().also { it.write(value) }
                    continue
                }
                val length = when {
                    value < 0xF0 -> channelMessageLength(value)
                    value == 0xF1 || value == 0xF3 -> 2
                    value == 0xF2 -> 3
                    value == 0xF6 -> 1
                    else -> 0 // Undefined system common statuses and stray end-of-exclusive.
                }
                if (length == 1) {
                    messages += Message(byteArrayOf(value.toByte()), timestampNs)
                } else if (length > 1) {
                    startMessage(value, length, timestampNs)
                }
                continue
            }

            if (pendingStatus == 0) {
                if (runningStatus == 0) continue
                startMessage(runningStatus, channelMessageLength(runningStatus), timestampNs)
            }
            pendingData[pendingSize++] = value.toByte()
            if (pendingSize == pendingLength) {
                messages += Message(pendingData.copyOf(pendingLength), messageTimestampNs)
                pendingStatus = 0
                pendingSize = 0
            }
        }
        return messages
    }

    private fun startMessage(status: Int, length: Int, timestampNs: Long) {
        pendingStatus = status
        pendingData[0] = status.toByte()
        pendingSize = 1
        pendingLength = length
        messageTimestampNs = timestampNs
    }

    private fun channelMessageLength(status: Int): Int = when (status and 0xF0) {
        0xC0, 0xD0 -> 2
        else -> 3
    }
}
