package com.alexanderpeppe.pianobeam.midi

import com.alexanderpeppe.pianobeam.data.PlaybackChannelInfo

/**
 * Resolves the wire channel used for a source MIDI channel.
 *
 * Source-channel controls (mute, solo, volume, and instrument overrides) stay keyed by
 * [sourceChannel]. Only the emitted status byte is rewritten to the returned channel.
 */
object MidiChannelRouter {
    fun outputChannel(
        sourceChannel: Int,
        channelInfo: PlaybackChannelInfo? = null,
        instrumentOverrideProgram: Int? = null,
        explicitAssignments: Map<Int, Int> = emptyMap(),
        acousticPianoInputChannel: Int = 1,
        foldChannel2IntoPianoChannel: Boolean = true,
        mergeAllInstrumentsToPianoChannel: Boolean = false
    ): Int {
        if (sourceChannel !in 1..16) return sourceChannel
        val pianoChannel = acousticPianoInputChannel.coerceIn(1, 16)
        if (isMergedAsAcousticPiano(sourceChannel, mergeAllInstrumentsToPianoChannel)) {
            return pianoChannel
        }
        explicitAssignments[sourceChannel]
            ?.takeIf { it in 1..16 }
            ?.let { return it }
        if (sourceChannel == 1 || (sourceChannel == 2 && foldChannel2IntoPianoChannel)) {
            return pianoChannel
        }
        if (instrumentOverrideProgram != null) {
            return if (GeneralMidi.isAcousticGrandPianoProgram(instrumentOverrideProgram)) {
                pianoChannel
            } else {
                sourceChannel
            }
        }
        return if (channelInfo?.usesAcousticGrandPianoInstrument() == true) {
            pianoChannel
        } else {
            sourceChannel
        }
    }

    /** Merge-all intentionally makes every source part one Acoustic Grand Piano voice. */
    fun isMergedAsAcousticPiano(
        sourceChannel: Int,
        mergeAllInstrumentsToPianoChannel: Boolean
    ): Boolean =
        mergeAllInstrumentsToPianoChannel && sourceChannel in 1..16

    fun isLikelyPianoSource(
        sourceChannel: Int,
        channelInfo: PlaybackChannelInfo?,
        instrumentOverrideProgram: Int?,
        explicitAssignments: Map<Int, Int>,
        acousticPianoInputChannel: Int,
        mergeAllInstrumentsToPianoChannel: Boolean
    ): Boolean {
        if (sourceChannel !in 1..16) return false
        if (isMergedAsAcousticPiano(sourceChannel, mergeAllInstrumentsToPianoChannel)) {
            return true
        }
        if (sourceChannel == 1 || sourceChannel == 2) return true
        if (explicitAssignments[sourceChannel] == acousticPianoInputChannel.coerceIn(1, 16)) return true
        if (instrumentOverrideProgram != null) {
            return GeneralMidi.isAcousticGrandPianoProgram(instrumentOverrideProgram)
        }
        if (channelInfo?.usesAcousticGrandPianoInstrument() == true) return true
        return false
    }

    fun PlaybackChannelInfo.usesAcousticGrandPianoInstrument(): Boolean {
        if (channel == 10) return false
        if (programNumbers.any { GeneralMidi.isAcousticGrandPianoProgram(it) }) return true
        val names = buildList {
            label?.let { add(it) }
            instrumentName?.let { add(it) }
            addAll(trackTitles)
            addAll(metaInstrumentNames)
        }
        return names.any { GeneralMidi.isAcousticGrandPianoName(it) }
    }
}
