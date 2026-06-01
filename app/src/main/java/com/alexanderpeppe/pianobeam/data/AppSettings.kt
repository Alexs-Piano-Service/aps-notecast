package com.alexanderpeppe.pianobeam.data

enum class AppThemeMode(val preferenceValue: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromPreference(value: String?): AppThemeMode =
            values().firstOrNull { it.preferenceValue == value } ?: System
    }
}

enum class PlaybackAdvanceMode(val preferenceValue: String) {
    AutoPlayNext("auto_play_next"),
    StopAfterCurrent("stop_after_current"),
    StopAfterPlaylist("stop_after_playlist");

    companion object {
        fun fromPreference(value: String?): PlaybackAdvanceMode =
            values().firstOrNull { it.preferenceValue == value } ?: StopAfterPlaylist
    }
}

enum class RepeatMode(val preferenceValue: String) {
    Off("off"),
    One("one"),
    Playlist("playlist");

    companion object {
        fun fromPreference(value: String?): RepeatMode =
            values().firstOrNull { it.preferenceValue == value } ?: Off
    }
}

enum class VolumeControlMode(val preferenceValue: String) {
    LegacyVolumeScaling("legacy_volume_scaling"),
    StandardMidiVolume("standard_midi_volume");

    companion object {
        fun fromPreference(value: String?): VolumeControlMode? =
            values().firstOrNull { it.preferenceValue == value }

        fun fromLegacyPreference(appControlsVolume: Boolean): VolumeControlMode =
            if (appControlsVolume) LegacyVolumeScaling else StandardMidiVolume
    }
}

data class MidiChannelControl(
    val channel: Int,
    val muted: Boolean = false,
    val solo: Boolean = false,
    val volumePercent: Int = 100
)

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.System,
    val fontScalePercent: Int = 100,
    val autoReconnectEnabled: Boolean = true,
    val scanOnLaunch: Boolean = false,
    val reconnectIntervalSeconds: Int = 8,
    val reconnectTimeoutSeconds: Int = 30,
    val playbackAdvanceMode: PlaybackAdvanceMode = PlaybackAdvanceMode.StopAfterPlaylist,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shufflePlaylistsByDefault: Boolean = false,
    val volumeControlMode: VolumeControlMode = VolumeControlMode.LegacyVolumeScaling,
    val velocityScalingEnabled: Boolean = true,
    val minimumNoteVelocity: Int = 32,
    val tempoPercent: Int = 100,
    val transposeSemitones: Int = 0,
    val excludeDrumChannelFromTranspose: Boolean = true,
    val channelControls: List<MidiChannelControl> = defaultChannelControls(),
    val autoTrimRecordingSilence: Boolean = true,
    val recordingTargetPlaylistId: String? = null,
    val recordingCountdownSeconds: Int = 0,
    val recordingMetronomeEnabled: Boolean = false,
    val confirmDiscardRecording: Boolean = true
)

fun defaultChannelControls(): List<MidiChannelControl> =
    (1..16).map { MidiChannelControl(channel = it) }
