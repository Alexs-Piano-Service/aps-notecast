package com.alexanderpeppe.pianobeam.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVolumeTest {
    @Test
    fun hardwareVolumeDirectionChangesAppVolumeByOneStep() {
        assertEquals(93, adjustedAppVolumePercent(currentPercent = 88, direction = 1))
        assertEquals(83, adjustedAppVolumePercent(currentPercent = 88, direction = -1))
    }

    @Test
    fun hardwareVolumeAdjustmentsClampAtAppVolumeBounds() {
        assertEquals(APP_VOLUME_MAX_PERCENT, adjustedAppVolumePercent(currentPercent = 98, direction = 1))
        assertEquals(APP_VOLUME_MAX_PERCENT, adjustedAppVolumePercent(currentPercent = 100, direction = 1))
        assertEquals(APP_VOLUME_MIN_PERCENT, adjustedAppVolumePercent(currentPercent = 2, direction = -1))
        assertEquals(APP_VOLUME_MIN_PERCENT, adjustedAppVolumePercent(currentPercent = 0, direction = -1))
    }

    @Test
    fun unchangedDirectionOnlyNormalizesCurrentVolume() {
        assertEquals(88, adjustedAppVolumePercent(currentPercent = 88, direction = 0))
        assertEquals(APP_VOLUME_MIN_PERCENT, adjustedAppVolumePercent(currentPercent = -10, direction = 0))
        assertEquals(APP_VOLUME_MAX_PERCENT, adjustedAppVolumePercent(currentPercent = 110, direction = 0))
    }

    @Test
    fun repeatedHardwareVolumeAdjustmentsReachBothBounds() {
        val raised = (1..30).fold(0) { volume, _ -> adjustedAppVolumePercent(volume, direction = 1) }
        val lowered = (1..30).fold(100) { volume, _ -> adjustedAppVolumePercent(volume, direction = -1) }

        assertEquals(APP_VOLUME_MAX_PERCENT, raised)
        assertEquals(APP_VOLUME_MIN_PERCENT, lowered)
    }
}
