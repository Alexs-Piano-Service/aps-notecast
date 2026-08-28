package com.alexanderpeppe.pianobeam.midi

/** Rules that keep source channel state from fighting after every part is merged to one channel. */
object MidiMergePolicy {
    fun shouldSuppressSourceMessage(data: ByteArray, mergeAllEnabled: Boolean): Boolean {
        if (!mergeAllEnabled || data.isEmpty()) return false
        val status = data[0].toInt() and 0xFF
        if (status !in 0x80..0xEF) return false
        return when (status and 0xF0) {
            0xC0 -> true // one Acoustic Grand program is emitted separately
            0xB0 -> {
                val controller = data.getOrNull(1)?.toInt()?.and(0xFF) ?: return false
                controller == 0 || controller == 7 || controller == 32 || controller in 120..127
            }
            else -> false
        }
    }

    fun acousticGrandProgramChange(channel: Int): ByteArray? =
        channel.takeIf { it in 1..16 }?.let {
            byteArrayOf((0xC0 or (it - 1)).toByte(), 0)
        }
}
