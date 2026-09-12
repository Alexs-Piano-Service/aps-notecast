package com.alexanderpeppe.pianobeam.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiPlaybackClockTest {
    @Test
    fun fourSecondFileDoesNotFinishAtItsOneSecondNoteOff() {
        val clock = MidiPlaybackClock(4_000_000L, nowNs = 0)
        assertEquals(0L, clock.remainingNs(1_000_000, 1_000_000_000))
        assertTrue(clock.remainingNs(null, 1_000_000_000) >= 3_000_000_000)
        assertEquals(2_000_000L, clock.progressUs(2_000_000_000))
        assertEquals(0L, clock.remainingNs(null, 4_400_000_000))
    }

    @Test
    fun pauseDuringTrailingSilenceFreezesProgressAndDelaysPlaylistTransition() {
        val clock = MidiPlaybackClock(4_000_000L, nowNs = 0)
        clock.pause(2_000_000_000)
        clock.pause(3_000_000_000)
        assertEquals(2_000_000L, clock.progressUs(6_000_000_000))
        clock.resume(6_000_000_000)
        assertEquals(2_000_000L, clock.progressUs(6_000_000_000))
        assertEquals(2_400_000_000L, clock.remainingNs(null, 6_000_000_000))
        assertEquals(0L, clock.remainingNs(null, 8_400_000_000))
    }

    @Test
    fun seekingWithinTrailingSilenceRecomputesTheFinishDeadline() {
        val clock = MidiPlaybackClock(4_000_000L, nowNs = 0)
        clock.seek(3_000_000L, 10_000_000_000)
        assertEquals(3_000_000L, clock.progressUs(10_000_000_000))
        assertEquals(1_400_000_000L, clock.remainingNs(null, 10_000_000_000))
        clock.seek(2_000_000L, 11_000_000_000)
        clock.pause(11_000_000_000)
        clock.resume(20_000_000_000)
        assertEquals(2_000_000L, clock.progressUs(20_000_000_000))
        assertEquals(2_400_000_000L, clock.remainingNs(null, 20_000_000_000))
    }
}
