package com.alexanderpeppe.pianobeam.midi

import java.util.ArrayDeque

/**
 * Tracks note voices by their source identity while preserving the destination used on note-on.
 *
 * A null destination is a logical voice whose note-on was not emitted. Keeping those voices in
 * the FIFO is intentional: the matching source note-off must be consumed instead of being routed
 * to a newer destination.
 */
class MidiSourceNoteLedger {
    data class ReleaseGroup(
        val outputChannel: Int,
        val note: Int,
        val count: Int
    )

    private data class Voice(var outputChannel: Int?)

    private val lock = Any()
    private val voicesBySourceAndNote = mutableMapOf<Int, ArrayDeque<Voice>>()

    /** Records an emitted destination, or null when the logical note-on was suppressed. */
    fun recordNoteOn(sourceChannel: Int, note: Int, outputChannel: Int?): Boolean = synchronized(lock) {
        if (!isValidSourceAndNote(sourceChannel, note)) return@synchronized false
        if (outputChannel != null && outputChannel !in MIDI_CHANNEL_RANGE) return@synchronized false
        queueFor(sourceChannel, note).addLast(Voice(outputChannel))
        true
    }

    /** Records a logical note-on without emitting it, for seek/state priming. */
    fun primeSilentNoteOn(sourceChannel: Int, note: Int): Boolean =
        recordNoteOn(sourceChannel, note, outputChannel = null)

    /**
     * Consumes the oldest matching voice and returns the channel used for its note-on.
     *
     * Null means either that the note-on was suppressed or that no matching voice exists. In both
     * cases the caller should suppress the source note-off.
     */
    fun consumeNoteOff(sourceChannel: Int, note: Int): Int? = synchronized(lock) {
        if (!isValidSourceAndNote(sourceChannel, note)) return@synchronized null
        val key = key(sourceChannel, note)
        val voices = voicesBySourceAndNote[key] ?: return@synchronized null
        val voice = voices.pollFirst() ?: return@synchronized null
        if (voices.isEmpty()) voicesBySourceAndNote.remove(key)
        voice.outputChannel
    }

    /** Restores a consumed emitted voice after its note-off could not be sent. */
    fun restoreConsumedVoice(sourceChannel: Int, note: Int, outputChannel: Int): Boolean = synchronized(lock) {
        if (!isValidSourceAndNote(sourceChannel, note) || outputChannel !in MIDI_CHANNEL_RANGE) {
            return@synchronized false
        }
        queueFor(sourceChannel, note).addFirst(Voice(outputChannel))
        true
    }

    /**
     * Marks every emitted voice released while retaining one silent placeholder per voice.
     *
     * The returned groups are the explicit note-offs the caller should emit. Retained placeholders
     * ensure delayed source note-offs cannot terminate voices retriggered after a route transition.
     */
    fun markAllEmittedVoicesReleased(): List<ReleaseGroup> = synchronized(lock) {
        val counts = mutableMapOf<ReleaseKey, Int>()
        voicesBySourceAndNote.forEach { (key, voices) ->
            val note = noteFromKey(key)
            voices.forEach { voice ->
                voice.outputChannel?.let { outputChannel ->
                    counts.increment(ReleaseKey(outputChannel, note))
                    voice.outputChannel = null
                }
            }
        }
        counts.toReleaseGroups()
    }

    /** Marks every voice on one physical output released while retaining source placeholders. */
    fun markOutputVoicesReleased(outputChannel: Int): Int = synchronized(lock) {
        if (outputChannel !in MIDI_CHANNEL_RANGE) return@synchronized 0
        var releasedCount = 0
        voicesBySourceAndNote.values.forEach { voices ->
            voices.forEach { voice ->
                if (voice.outputChannel == outputChannel) {
                    voice.outputChannel = null
                    releasedCount++
                }
            }
        }
        releasedCount
    }

    /**
     * Releases every emitted voice for one source and clears all of that source's logical voices.
     *
     * This is suitable for translating source-scoped CC120/CC123 into explicit note-offs without
     * affecting voices that other sources share on the same destination channel.
     */
    fun releaseAndClearSource(sourceChannel: Int): List<ReleaseGroup> = synchronized(lock) {
        if (sourceChannel !in MIDI_CHANNEL_RANGE) return@synchronized emptyList()
        val counts = mutableMapOf<ReleaseKey, Int>()
        for (note in MIDI_NOTE_RANGE) {
            val voices = voicesBySourceAndNote.remove(key(sourceChannel, note)) ?: continue
            voices.forEach { voice ->
                voice.outputChannel?.let { outputChannel ->
                    counts.increment(ReleaseKey(outputChannel, note))
                }
            }
        }
        counts.toReleaseGroups()
    }

    fun reset() {
        synchronized(lock) {
            voicesBySourceAndNote.clear()
        }
    }

    private fun queueFor(sourceChannel: Int, note: Int): ArrayDeque<Voice> =
        voicesBySourceAndNote.getOrPut(key(sourceChannel, note)) { ArrayDeque() }

    private fun isValidSourceAndNote(sourceChannel: Int, note: Int): Boolean =
        sourceChannel in MIDI_CHANNEL_RANGE && note in MIDI_NOTE_RANGE

    private fun key(sourceChannel: Int, note: Int): Int =
        (sourceChannel - 1) * MIDI_NOTES_PER_CHANNEL + note

    private fun noteFromKey(key: Int): Int = key % MIDI_NOTES_PER_CHANNEL

    private data class ReleaseKey(val outputChannel: Int, val note: Int)

    private fun MutableMap<ReleaseKey, Int>.increment(key: ReleaseKey) {
        this[key] = (this[key] ?: 0) + 1
    }

    private fun Map<ReleaseKey, Int>.toReleaseGroups(): List<ReleaseGroup> =
        entries
            .sortedWith(compareBy({ it.key.outputChannel }, { it.key.note }))
            .map { (key, count) ->
                ReleaseGroup(
                    outputChannel = key.outputChannel,
                    note = key.note,
                    count = count
                )
            }

    private companion object {
        val MIDI_CHANNEL_RANGE = 1..16
        val MIDI_NOTE_RANGE = 0..127
        const val MIDI_NOTES_PER_CHANNEL = 128
    }
}
