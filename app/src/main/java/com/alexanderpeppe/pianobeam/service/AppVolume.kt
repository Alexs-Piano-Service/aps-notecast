package com.alexanderpeppe.pianobeam.service

internal const val APP_VOLUME_MIN_PERCENT = 0
internal const val APP_VOLUME_MAX_PERCENT = 100
internal const val HARDWARE_VOLUME_STEP_PERCENT = 5

internal fun adjustedAppVolumePercent(currentPercent: Int, direction: Int): Int {
    val cleanCurrent = currentPercent.coerceIn(APP_VOLUME_MIN_PERCENT, APP_VOLUME_MAX_PERCENT)
    val delta = when {
        direction > 0 -> HARDWARE_VOLUME_STEP_PERCENT
        direction < 0 -> -HARDWARE_VOLUME_STEP_PERCENT
        else -> 0
    }
    return (cleanCurrent + delta).coerceIn(APP_VOLUME_MIN_PERCENT, APP_VOLUME_MAX_PERCENT)
}
