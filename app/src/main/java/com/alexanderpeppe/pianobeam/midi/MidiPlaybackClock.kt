package com.alexanderpeppe.pianobeam.midi

/** One pause-aware clock for scheduled events and the silent interval ending at End of Track. */
class MidiPlaybackClock(
    private val durationUs: Long,
    nowNs: Long,
    private val startDelayNs: Long = 0L
) {
    private var startNs = nowNs + startDelayNs
    private var pauseOffsetNs = 0L
    private var pauseStartedNs: Long? = null

    fun pause(nowNs: Long) {
        if (pauseStartedNs == null) pauseStartedNs = nowNs
    }

    fun resume(nowNs: Long) {
        pauseStartedNs?.let { pauseOffsetNs += nowNs - it }
        pauseStartedNs = null
    }

    fun seek(progressUs: Long, nowNs: Long) {
        startNs = nowNs + startDelayNs - progressUs.coerceIn(0L, durationUs) * 1_000L
        pauseOffsetNs = 0L
        pauseStartedNs = null
    }

    fun progressUs(nowNs: Long): Long =
        (((pauseStartedNs ?: nowNs) - startNs - pauseOffsetNs) / 1_000L).coerceIn(0L, durationUs)

    fun targetNs(timeUs: Long): Long = startNs + timeUs * 1_000L + pauseOffsetNs

    fun remainingNs(eventTimeUs: Long?, nowNs: Long, scheduleAheadNs: Long = 0L): Long =
        targetNs(eventTimeUs ?: durationUs) + (if (eventTimeUs == null) 400_000_000L else 0L) - scheduleAheadNs - nowNs
}
