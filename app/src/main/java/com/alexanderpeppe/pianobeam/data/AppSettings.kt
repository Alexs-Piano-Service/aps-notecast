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

enum class PedalOutputMode(val preferenceValue: String) {
    StandardControllers("standard_controllers"),
    StandardControllersAndRollNote18("standard_controllers_and_roll_note_18");

    companion object {
        fun fromPreference(value: String?): PedalOutputMode =
            values().firstOrNull { it.preferenceValue == value } ?: StandardControllers
    }
}

data class MidiChannelControl(
    val channel: Int,
    val muted: Boolean = false,
    val solo: Boolean = false,
    val volumePercent: Int = 100
)

data class SongInstrumentOverrides(
    val songId: String,
    val channelPrograms: Map<Int, Int> = emptyMap()
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
    val pedalOutputMode: PedalOutputMode = PedalOutputMode.StandardControllers,
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
    val confirmDiscardRecording: Boolean = true,
    val songInstrumentOverrides: List<SongInstrumentOverrides> = emptyList()
)

fun defaultChannelControls(): List<MidiChannelControl> =
    (1..16).map { MidiChannelControl(channel = it) }

fun AppSettings.instrumentOverridesForSong(songId: String?): Map<Int, Int> =
    songId
        ?.takeIf { it.isNotBlank() }
        ?.let { id -> songInstrumentOverrides.firstOrNull { it.songId == id }?.channelPrograms }
        .orEmpty()

fun AppSettings.instrumentOverrideSignatureForSong(songId: String?): String =
    instrumentOverridesForSong(songId)
        .toSortedMap()
        .entries
        .joinToString("|") { (channel, program) -> "$channel:$program" }

fun AppSettings.withSongInstrumentOverride(songId: String, channel: Int, program: Int?): AppSettings {
    val cleanSongId = songId.takeIf { it.isNotBlank() } ?: return this
    val cleanChannel = channel.takeIf { it in 1..16 } ?: return this
    val existing = instrumentOverridesForSong(cleanSongId).toMutableMap()
    if (program == null) {
        existing.remove(cleanChannel)
    } else {
        existing[cleanChannel] = program.coerceIn(0, 127)
    }
    val updated = songInstrumentOverrides
        .filterNot { it.songId == cleanSongId }
        .toMutableList()
    if (existing.isNotEmpty()) {
        updated += SongInstrumentOverrides(cleanSongId, existing.toSortedMap())
    }
    return copy(songInstrumentOverrides = updated.sortedBy { it.songId })
}

fun AppSettings.withClearedSongInstrumentOverrides(songId: String): AppSettings {
    val cleanSongId = songId.takeIf { it.isNotBlank() } ?: return this
    return copy(songInstrumentOverrides = songInstrumentOverrides.filterNot { it.songId == cleanSongId })
}
