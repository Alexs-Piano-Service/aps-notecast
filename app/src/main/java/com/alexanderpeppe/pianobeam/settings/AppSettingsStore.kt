package com.alexanderpeppe.pianobeam.settings

import android.content.Context
import com.alexanderpeppe.pianobeam.data.AppSettings
import com.alexanderpeppe.pianobeam.data.AppThemeMode
import com.alexanderpeppe.pianobeam.data.MidiChannelControl
import com.alexanderpeppe.pianobeam.data.PlaybackAdvanceMode
import com.alexanderpeppe.pianobeam.data.RepeatMode
import com.alexanderpeppe.pianobeam.data.VolumeControlMode
import com.alexanderpeppe.pianobeam.data.defaultChannelControls
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())

    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setThemeMode(themeMode: AppThemeMode) {
        preferences.edit()
            .putString(KEY_THEME_MODE, themeMode.preferenceValue)
            .apply()
        _settings.value = _settings.value.copy(themeMode = themeMode)
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(autoReconnectEnabled = enabled))
    }

    fun updateSettings(settings: AppSettings) {
        val cleanSettings = settings.copy(
            fontScalePercent = settings.fontScalePercent.coerceIn(80, 110),
            reconnectIntervalSeconds = settings.reconnectIntervalSeconds.coerceIn(3, 60),
            reconnectTimeoutSeconds = settings.reconnectTimeoutSeconds.coerceIn(10, 180),
            minimumNoteVelocity = settings.minimumNoteVelocity.coerceIn(1, 127),
            tempoPercent = settings.tempoPercent.coerceIn(50, 150),
            transposeSemitones = settings.transposeSemitones.coerceIn(-12, 12),
            recordingCountdownSeconds = settings.recordingCountdownSeconds.coerceIn(0, 8),
            channelControls = normalizedChannelControls(settings.channelControls)
        )
        preferences.edit()
            .putString(KEY_THEME_MODE, cleanSettings.themeMode.preferenceValue)
            .putInt(KEY_FONT_SCALE_PERCENT, cleanSettings.fontScalePercent)
            .putBoolean(KEY_AUTO_RECONNECT, cleanSettings.autoReconnectEnabled)
            .putBoolean(KEY_SCAN_ON_LAUNCH, cleanSettings.scanOnLaunch)
            .putInt(KEY_RECONNECT_INTERVAL_SECONDS, cleanSettings.reconnectIntervalSeconds)
            .putInt(KEY_RECONNECT_TIMEOUT_SECONDS, cleanSettings.reconnectTimeoutSeconds)
            .putString(KEY_PLAYBACK_ADVANCE_MODE, cleanSettings.playbackAdvanceMode.preferenceValue)
            .putString(KEY_REPEAT_MODE, cleanSettings.repeatMode.preferenceValue)
            .putBoolean(KEY_SHUFFLE_PLAYLISTS, cleanSettings.shufflePlaylistsByDefault)
            .putString(KEY_VOLUME_CONTROL_MODE, cleanSettings.volumeControlMode.preferenceValue)
            .putBoolean(KEY_APP_CONTROLS_VOLUME, cleanSettings.volumeControlMode == VolumeControlMode.LegacyVolumeScaling)
            .putBoolean(KEY_VELOCITY_SCALING_ENABLED, cleanSettings.velocityScalingEnabled)
            .putInt(KEY_MINIMUM_NOTE_VELOCITY, cleanSettings.minimumNoteVelocity)
            .putInt(KEY_TEMPO_PERCENT, cleanSettings.tempoPercent)
            .putInt(KEY_TRANSPOSE_SEMITONES, cleanSettings.transposeSemitones)
            .putBoolean(KEY_EXCLUDE_DRUM_CHANNEL, cleanSettings.excludeDrumChannelFromTranspose)
            .putBoolean(KEY_AUTO_TRIM_RECORDING, cleanSettings.autoTrimRecordingSilence)
            .putString(KEY_RECORDING_TARGET_PLAYLIST, cleanSettings.recordingTargetPlaylistId)
            .putInt(KEY_RECORDING_COUNTDOWN_SECONDS, cleanSettings.recordingCountdownSeconds)
            .putBoolean(KEY_RECORDING_METRONOME, cleanSettings.recordingMetronomeEnabled)
            .putBoolean(KEY_CONFIRM_DISCARD_RECORDING, cleanSettings.confirmDiscardRecording)
            .also { editor ->
                cleanSettings.channelControls.forEach { control ->
                    val prefix = channelKeyPrefix(control.channel)
                    editor.putBoolean("${prefix}_muted", control.muted)
                    editor.putBoolean("${prefix}_solo", control.solo)
                    editor.putInt("${prefix}_volume", control.volumePercent.coerceIn(0, 100))
                }
            }
            .apply()
        _settings.value = cleanSettings
    }

    private fun loadSettings(): AppSettings =
        AppSettings(
            themeMode = AppThemeMode.fromPreference(preferences.getString(KEY_THEME_MODE, null)),
            fontScalePercent = preferences.getInt(KEY_FONT_SCALE_PERCENT, 100).coerceIn(80, 110),
            autoReconnectEnabled = preferences.getBoolean(KEY_AUTO_RECONNECT, true),
            scanOnLaunch = preferences.getBoolean(KEY_SCAN_ON_LAUNCH, false),
            reconnectIntervalSeconds = preferences.getInt(KEY_RECONNECT_INTERVAL_SECONDS, 8).coerceIn(3, 60),
            reconnectTimeoutSeconds = preferences.getInt(KEY_RECONNECT_TIMEOUT_SECONDS, 30).coerceIn(10, 180),
            playbackAdvanceMode = PlaybackAdvanceMode.fromPreference(preferences.getString(KEY_PLAYBACK_ADVANCE_MODE, null)),
            repeatMode = RepeatMode.fromPreference(preferences.getString(KEY_REPEAT_MODE, null)),
            shufflePlaylistsByDefault = preferences.getBoolean(KEY_SHUFFLE_PLAYLISTS, false),
            volumeControlMode = VolumeControlMode.fromPreference(preferences.getString(KEY_VOLUME_CONTROL_MODE, null))
                ?: VolumeControlMode.fromLegacyPreference(preferences.getBoolean(KEY_APP_CONTROLS_VOLUME, true)),
            velocityScalingEnabled = preferences.getBoolean(KEY_VELOCITY_SCALING_ENABLED, true),
            minimumNoteVelocity = preferences.getInt(KEY_MINIMUM_NOTE_VELOCITY, 32).coerceIn(1, 127),
            tempoPercent = preferences.getInt(KEY_TEMPO_PERCENT, 100).coerceIn(50, 150),
            transposeSemitones = preferences.getInt(KEY_TRANSPOSE_SEMITONES, 0).coerceIn(-12, 12),
            excludeDrumChannelFromTranspose = preferences.getBoolean(KEY_EXCLUDE_DRUM_CHANNEL, true),
            channelControls = normalizedChannelControls(defaultChannelControls().map { control ->
                val prefix = channelKeyPrefix(control.channel)
                control.copy(
                    muted = preferences.getBoolean("${prefix}_muted", false),
                    solo = preferences.getBoolean("${prefix}_solo", false),
                    volumePercent = preferences.getInt("${prefix}_volume", 100).coerceIn(0, 100)
                )
            }),
            autoTrimRecordingSilence = preferences.getBoolean(KEY_AUTO_TRIM_RECORDING, true),
            recordingTargetPlaylistId = preferences.getString(KEY_RECORDING_TARGET_PLAYLIST, null),
            recordingCountdownSeconds = preferences.getInt(KEY_RECORDING_COUNTDOWN_SECONDS, 0).coerceIn(0, 8),
            recordingMetronomeEnabled = preferences.getBoolean(KEY_RECORDING_METRONOME, false),
            confirmDiscardRecording = preferences.getBoolean(KEY_CONFIRM_DISCARD_RECORDING, true)
        )

    private fun normalizedChannelControls(controls: List<MidiChannelControl>): List<MidiChannelControl> {
        val byChannel = controls.associateBy { it.channel }
        return (1..16).map { channel ->
            byChannel[channel]?.copy(
                channel = channel,
                volumePercent = byChannel[channel]?.volumePercent?.coerceIn(0, 100) ?: 100
            ) ?: MidiChannelControl(channel = channel)
        }
    }

    private fun channelKeyPrefix(channel: Int): String = "channel_$channel"

    companion object {
        private const val PREFERENCES_NAME = "pianobeam_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FONT_SCALE_PERCENT = "font_scale_percent"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_SCAN_ON_LAUNCH = "scan_on_launch"
        private const val KEY_RECONNECT_INTERVAL_SECONDS = "reconnect_interval_seconds"
        private const val KEY_RECONNECT_TIMEOUT_SECONDS = "reconnect_timeout_seconds"
        private const val KEY_PLAYBACK_ADVANCE_MODE = "playback_advance_mode"
        private const val KEY_REPEAT_MODE = "repeat_mode"
        private const val KEY_SHUFFLE_PLAYLISTS = "shuffle_playlists"
        private const val KEY_VOLUME_CONTROL_MODE = "volume_control_mode"
        private const val KEY_APP_CONTROLS_VOLUME = "app_controls_volume"
        private const val KEY_VELOCITY_SCALING_ENABLED = "velocity_scaling_enabled"
        private const val KEY_MINIMUM_NOTE_VELOCITY = "minimum_note_velocity"
        private const val KEY_TEMPO_PERCENT = "tempo_percent"
        private const val KEY_TRANSPOSE_SEMITONES = "transpose_semitones"
        private const val KEY_EXCLUDE_DRUM_CHANNEL = "exclude_drum_channel"
        private const val KEY_AUTO_TRIM_RECORDING = "auto_trim_recording"
        private const val KEY_RECORDING_TARGET_PLAYLIST = "recording_target_playlist"
        private const val KEY_RECORDING_COUNTDOWN_SECONDS = "recording_countdown_seconds"
        private const val KEY_RECORDING_METRONOME = "recording_metronome"
        private const val KEY_CONFIRM_DISCARD_RECORDING = "confirm_discard_recording"
    }
}
