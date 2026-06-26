package com.alexanderpeppe.pianobeam.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexanderpeppe.pianobeam.R
import com.alexanderpeppe.pianobeam.data.AppSettings
import com.alexanderpeppe.pianobeam.data.AppThemeMode
import com.alexanderpeppe.pianobeam.data.MAX_MIDI_LIBRARY_PAGE_SIZE
import com.alexanderpeppe.pianobeam.data.MidiChannelControl
import com.alexanderpeppe.pianobeam.data.MidiPlaylist
import com.alexanderpeppe.pianobeam.data.MIN_MIDI_LIBRARY_PAGE_SIZE
import com.alexanderpeppe.pianobeam.data.PedalOutputMode
import com.alexanderpeppe.pianobeam.data.PedalValueMode
import com.alexanderpeppe.pianobeam.data.PlaybackAdvanceMode
import com.alexanderpeppe.pianobeam.data.RepeatMode
import com.alexanderpeppe.pianobeam.data.VolumeControlMode
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    settings: AppSettings,
    libraryFileCount: Int,
    playlists: List<MidiPlaylist>,
    preferredDeviceName: String?,
    onSettingsChange: (AppSettings) -> Unit,
    onForgetDevice: () -> Unit,
    onDiagnostics: () -> Unit,
    onPlayChromaticScale: (velocity: Int, noteLengthMs: Int) -> Unit,
    onStopChromaticScale: () -> Unit,
    onSustainPedalChange: (pressed: Boolean, playSustainedChord: Boolean) -> Unit,
    onBackupLibrary: () -> Unit,
    onRestoreLibrary: () -> Unit,
    onPurgeLibrary: () -> Unit,
    onDismiss: () -> Unit
) {
    var showAdvancedMidi by rememberSaveable { mutableStateOf(false) }
    var diagnosticVelocity by rememberSaveable { mutableStateOf(64) }
    var diagnosticNoteLengthMs by rememberSaveable { mutableStateOf(180) }
    var pedalTestPlaysChord by rememberSaveable { mutableStateOf(true) }
    var showPurgeLibraryConfirmation by rememberSaveable { mutableStateOf(false) }
    val libraryPlaylistCount = playlists.size
    val libraryHasItems = libraryFileCount > 0 || libraryPlaylistCount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SettingSection(stringResource(R.string.settings_appearance)) {
                        SliderRow(
                            title = stringResource(R.string.settings_text_zoom),
                            valueLabel = "${settings.fontScalePercent}%",
                            value = settings.fontScalePercent.toFloat(),
                            range = 80f..110f,
                            steps = 5,
                            onValueChange = { onSettingsChange(settings.copy(fontScalePercent = it.roundToInt())) }
                        )
                        CompactDivider()
                        AppThemeMode.values().forEach { mode ->
                            RadioRow(
                                title = stringResource(mode.titleResId),
                                subtitle = stringResource(mode.subtitleResId),
                                selected = mode == settings.themeMode,
                                onClick = { onSettingsChange(settings.copy(themeMode = mode)) }
                            )
                        }
                    }
                }

                item {
                    SettingSection(stringResource(R.string.settings_playback)) {
                        RadioRow(stringResource(R.string.settings_play_next), stringResource(R.string.settings_play_next_subtitle), settings.playbackAdvanceMode == PlaybackAdvanceMode.AutoPlayNext) {
                            onSettingsChange(settings.copy(playbackAdvanceMode = PlaybackAdvanceMode.AutoPlayNext))
                        }
                        RadioRow(stringResource(R.string.settings_stop_after_current), stringResource(R.string.settings_stop_after_current_subtitle), settings.playbackAdvanceMode == PlaybackAdvanceMode.StopAfterCurrent) {
                            onSettingsChange(settings.copy(playbackAdvanceMode = PlaybackAdvanceMode.StopAfterCurrent))
                        }
                        RadioRow(stringResource(R.string.settings_stop_after_playlist), stringResource(R.string.settings_stop_after_playlist_subtitle), settings.playbackAdvanceMode == PlaybackAdvanceMode.StopAfterPlaylist) {
                            onSettingsChange(settings.copy(playbackAdvanceMode = PlaybackAdvanceMode.StopAfterPlaylist))
                        }
                        CompactDivider()
                        RadioRow(stringResource(R.string.settings_no_repeat), stringResource(R.string.settings_no_repeat_subtitle), settings.repeatMode == RepeatMode.Off) {
                            onSettingsChange(settings.copy(repeatMode = RepeatMode.Off))
                        }
                        RadioRow(stringResource(R.string.settings_repeat_one), stringResource(R.string.settings_repeat_one_subtitle), settings.repeatMode == RepeatMode.One) {
                            onSettingsChange(settings.copy(repeatMode = RepeatMode.One))
                        }
                        RadioRow(stringResource(R.string.settings_repeat_playlist), stringResource(R.string.settings_repeat_playlist_subtitle), settings.repeatMode == RepeatMode.Playlist) {
                            onSettingsChange(settings.copy(repeatMode = RepeatMode.Playlist))
                        }
                        SwitchRow(
                            title = stringResource(R.string.settings_shuffle_default),
                            subtitle = stringResource(R.string.settings_shuffle_default_subtitle),
                            checked = settings.shufflePlaylistsByDefault,
                            onCheckedChange = { onSettingsChange(settings.copy(shufflePlaylistsByDefault = it)) }
                        )
                        CompactDivider()
                        RadioRow(
                            title = stringResource(R.string.settings_volume_legacy),
                            subtitle = stringResource(R.string.settings_volume_legacy_subtitle),
                            selected = settings.volumeControlMode == VolumeControlMode.LegacyVolumeScaling,
                            onClick = { onSettingsChange(settings.copy(volumeControlMode = VolumeControlMode.LegacyVolumeScaling)) }
                        )
                        RadioRow(
                            title = stringResource(R.string.settings_volume_standard),
                            subtitle = stringResource(R.string.settings_volume_standard_subtitle),
                            selected = settings.volumeControlMode == VolumeControlMode.StandardMidiVolume,
                            onClick = { onSettingsChange(settings.copy(volumeControlMode = VolumeControlMode.StandardMidiVolume)) }
                        )
                        CompactDivider()
                        RadioRow(
                            title = stringResource(R.string.settings_pedal_standard),
                            subtitle = stringResource(R.string.settings_pedal_standard_subtitle),
                            selected = settings.pedalOutputMode == PedalOutputMode.StandardControllers,
                            onClick = { onSettingsChange(settings.copy(pedalOutputMode = PedalOutputMode.StandardControllers)) }
                        )
                        RadioRow(
                            title = stringResource(R.string.settings_pedal_roll),
                            subtitle = stringResource(R.string.settings_pedal_roll_subtitle),
                            selected = settings.pedalOutputMode == PedalOutputMode.StandardControllersAndRollNote18,
                            onClick = { onSettingsChange(settings.copy(pedalOutputMode = PedalOutputMode.StandardControllersAndRollNote18)) }
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_pedal_values),
                            subtitle = stringResource(R.string.settings_pedal_values_subtitle),
                            checked = settings.pedalValueMode == PedalValueMode.Binary,
                            onCheckedChange = {
                                onSettingsChange(
                                    settings.copy(
                                        pedalValueMode = if (it) PedalValueMode.Binary else PedalValueMode.Continuous
                                    )
                                )
                            }
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_fold_channel),
                            subtitle = stringResource(R.string.settings_fold_channel_subtitle),
                            checked = settings.foldChannel2IntoPianoChannel,
                            onCheckedChange = { onSettingsChange(settings.copy(foldChannel2IntoPianoChannel = it)) }
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_fold_pedals),
                            subtitle = stringResource(R.string.settings_fold_pedals_subtitle),
                            checked = settings.foldPedalsIntoPianoChannel,
                            onCheckedChange = { onSettingsChange(settings.copy(foldPedalsIntoPianoChannel = it)) }
                        )
                        CompactDivider()
                        SwitchRow(
                            title = stringResource(R.string.settings_velocity_floor),
                            subtitle = stringResource(R.string.settings_velocity_floor_subtitle),
                            checked = settings.velocityScalingEnabled,
                            onCheckedChange = { onSettingsChange(settings.copy(velocityScalingEnabled = it)) }
                        )
                        SliderRow(
                            title = stringResource(R.string.settings_minimum_velocity),
                            valueLabel = settings.minimumNoteVelocity.toString(),
                            value = settings.minimumNoteVelocity.toFloat(),
                            range = 1f..127f,
                            steps = 125,
                            onValueChange = { onSettingsChange(settings.copy(minimumNoteVelocity = it.roundToInt())) }
                        )
                    }
                }

                item {
                    SettingSection(stringResource(R.string.settings_tempo_key)) {
                        SliderRow(
                            title = stringResource(R.string.settings_tempo),
                            valueLabel = "${settings.tempoPercent}%",
                            value = settings.tempoPercent.toFloat(),
                            range = 50f..150f,
                            onValueChange = { onSettingsChange(settings.copy(tempoPercent = it.roundToInt())) }
                        )
                        SliderRow(
                            title = stringResource(R.string.settings_transpose),
                            valueLabel = stringResource(R.string.settings_transpose_value, settings.transposeSemitones),
                            value = settings.transposeSemitones.toFloat(),
                            range = -12f..12f,
                            steps = 23,
                            onValueChange = { onSettingsChange(settings.copy(transposeSemitones = it.roundToInt())) }
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_drum_channel),
                            subtitle = stringResource(R.string.settings_drum_channel_subtitle),
                            checked = settings.excludeDrumChannelFromTranspose,
                            onCheckedChange = { onSettingsChange(settings.copy(excludeDrumChannelFromTranspose = it)) }
                        )
                    }
                }

                item {
                    SettingSection(stringResource(R.string.settings_advanced_midi)) {
                        OutlinedButton(onClick = { showAdvancedMidi = !showAdvancedMidi }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (showAdvancedMidi) stringResource(R.string.settings_hide_channel_controls) else stringResource(R.string.settings_show_channel_controls),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (showAdvancedMidi) {
                    items(settings.channelControls.size) { index ->
                        val control = settings.channelControls.getOrNull(index) ?: MidiChannelControl(channel = index + 1)
                        ChannelControlRow(
                            control = control,
                            onControlChange = { updated ->
                                onSettingsChange(settings.copy(channelControls = settings.channelControls.updatedChannel(updated)))
                            }
                        )
                    }
                }

                item {
                    SettingSection(stringResource(R.string.settings_recording)) {
                        SwitchRow(
                            title = stringResource(R.string.settings_auto_trim),
                            subtitle = stringResource(R.string.settings_auto_trim_subtitle),
                            checked = settings.autoTrimRecordingSilence,
                            onCheckedChange = { onSettingsChange(settings.copy(autoTrimRecordingSilence = it)) }
                        )
                        CycleRow(
                            title = stringResource(R.string.settings_recordings_to),
                            value = playlists.firstOrNull { it.id == settings.recordingTargetPlaylistId }?.name ?: stringResource(R.string.settings_library_only),
                            onClick = {
                                val targets = listOf<String?>(null) + playlists.map { it.id }
                                val nextIndex = ((targets.indexOf(settings.recordingTargetPlaylistId).takeIf { it >= 0 } ?: 0) + 1) % targets.size
                                onSettingsChange(settings.copy(recordingTargetPlaylistId = targets[nextIndex]))
                            }
                        )
                        SliderRow(
                            title = stringResource(R.string.settings_count_in),
                            valueLabel = "${settings.recordingCountdownSeconds}s",
                            value = settings.recordingCountdownSeconds.toFloat(),
                            range = 0f..8f,
                            steps = 7,
                            onValueChange = { onSettingsChange(settings.copy(recordingCountdownSeconds = it.roundToInt())) }
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_metronome),
                            subtitle = stringResource(R.string.settings_metronome_subtitle),
                            checked = settings.recordingMetronomeEnabled,
                            onCheckedChange = { onSettingsChange(settings.copy(recordingMetronomeEnabled = it)) }
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_confirm_discard),
                            subtitle = stringResource(R.string.settings_confirm_discard_subtitle),
                            checked = settings.confirmDiscardRecording,
                            onCheckedChange = { onSettingsChange(settings.copy(confirmDiscardRecording = it)) }
                        )
                    }
                }

                item {
                    SettingSection(stringResource(R.string.settings_connection)) {
                        SwitchRow(
                            title = stringResource(R.string.settings_auto_reconnect),
                            subtitle = stringResource(R.string.settings_auto_reconnect_subtitle),
                            checked = settings.autoReconnectEnabled,
                            onCheckedChange = { onSettingsChange(settings.copy(autoReconnectEnabled = it)) }
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_scan_on_launch),
                            subtitle = stringResource(R.string.settings_scan_on_launch_subtitle),
                            checked = settings.scanOnLaunch,
                            onCheckedChange = { onSettingsChange(settings.copy(scanOnLaunch = it)) }
                        )
                        SliderRow(
                            title = stringResource(R.string.settings_reconnect_every),
                            valueLabel = "${settings.reconnectIntervalSeconds}s",
                            value = settings.reconnectIntervalSeconds.toFloat(),
                            range = 3f..60f,
                            onValueChange = { onSettingsChange(settings.copy(reconnectIntervalSeconds = it.roundToInt())) }
                        )
                        SliderRow(
                            title = stringResource(R.string.settings_reconnect_timeout),
                            valueLabel = "${settings.reconnectTimeoutSeconds}s",
                            value = settings.reconnectTimeoutSeconds.toFloat(),
                            range = 10f..180f,
                            onValueChange = { onSettingsChange(settings.copy(reconnectTimeoutSeconds = it.roundToInt())) }
                        )
                        Text(
                            stringResource(R.string.settings_preferred_midi_device, preferredDeviceName ?: stringResource(R.string.common_none)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onDiagnostics, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_connection_report), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(onClick = onForgetDevice, modifier = Modifier.weight(1f), enabled = preferredDeviceName != null) {
                                Text(stringResource(R.string.action_forget), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                item {
                    SettingSection(stringResource(R.string.settings_piano_test)) {
                        Text(
                            stringResource(R.string.settings_diagnostic_chromatic),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SliderRow(
                            title = stringResource(R.string.settings_diagnostic_scale_velocity),
                            valueLabel = diagnosticVelocity.toString(),
                            value = diagnosticVelocity.toFloat(),
                            range = 1f..127f,
                            steps = 125,
                            onValueChange = { diagnosticVelocity = it.roundToInt().coerceIn(1, 127) }
                        )
                        SliderRow(
                            title = stringResource(R.string.settings_diagnostic_note_length),
                            valueLabel = "${diagnosticNoteLengthMs}ms",
                            value = diagnosticNoteLengthMs.toFloat(),
                            range = 50f..1000f,
                            steps = 18,
                            onValueChange = {
                                diagnosticNoteLengthMs = ((it / 10f).roundToInt() * 10).coerceIn(50, 1000)
                            }
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onPlayChromaticScale(diagnosticVelocity, diagnosticNoteLengthMs) },
                                modifier = Modifier.weight(2f)
                            ) {
                                Text(stringResource(R.string.action_play_scale), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = onStopChromaticScale,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.action_stop), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        CheckboxRow(
                            title = stringResource(R.string.settings_diagnostic_pedal_chord),
                            subtitle = stringResource(R.string.settings_diagnostic_pedal_chord_subtitle),
                            checked = pedalTestPlaysChord,
                            onCheckedChange = { pedalTestPlaysChord = it }
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onSustainPedalChange(true, pedalTestPlaysChord) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.settings_diagnostic_pedal_on), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { onSustainPedalChange(false, false) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.settings_diagnostic_pedal_off), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                item {
                    SettingSection(stringResource(R.string.settings_library)) {
                        SliderRow(
                            title = stringResource(R.string.settings_midi_files_per_load),
                            valueLabel = settings.midiLibraryPageSize.toString(),
                            value = settings.midiLibraryPageSize.toFloat(),
                            range = MIN_MIDI_LIBRARY_PAGE_SIZE.toFloat()..MAX_MIDI_LIBRARY_PAGE_SIZE.toFloat(),
                            steps = 17,
                            onValueChange = {
                                onSettingsChange(settings.copy(midiLibraryPageSize = it.roundToInt().snapMidiLibraryPageSize()))
                            }
                        )
                        CompactDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onBackupLibrary, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.action_backup), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(onClick = onRestoreLibrary, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.action_restore), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        OutlinedButton(
                            onClick = { showPurgeLibraryConfirmation = true },
                            enabled = libraryHasItems,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.action_purge_library), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            stringResource(R.string.library_purge_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        }
    )

    if (showPurgeLibraryConfirmation) {
        AlertDialog(
            onDismissRequest = { showPurgeLibraryConfirmation = false },
            title = { Text(stringResource(R.string.library_purge_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.library_purge_confirm_message, libraryFileCount, libraryPlaylistCount)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPurgeLibraryConfirmation = false
                        onPurgeLibrary()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_purge_library))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeLibraryConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        content()
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CheckboxRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(10.dp))
            Text(valueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CycleRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) {
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChannelControlRow(control: MidiChannelControl, onControlChange: (MidiChannelControl) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.label_channel, control.channel), modifier = Modifier.width(52.dp), fontWeight = FontWeight.SemiBold, maxLines = 1)
            Checkbox(checked = control.muted, onCheckedChange = { onControlChange(control.copy(muted = it)) })
            Text(stringResource(R.string.label_mute), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Checkbox(checked = control.solo, onCheckedChange = { onControlChange(control.copy(solo = it)) })
            Text(stringResource(R.string.label_solo), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text("${control.volumePercent}%", style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = control.volumePercent.toFloat(),
            onValueChange = { onControlChange(control.copy(volumePercent = it.roundToInt().coerceIn(0, 100))) },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CompactDivider() {
    HorizontalDivider(Modifier.padding(vertical = 2.dp))
}

private fun List<MidiChannelControl>.updatedChannel(updated: MidiChannelControl): List<MidiChannelControl> {
    val byChannel = associateBy { it.channel }.toMutableMap()
    byChannel[updated.channel] = updated
    return (1..16).map { channel -> byChannel[channel] ?: MidiChannelControl(channel = channel) }
}

private fun Int.snapMidiLibraryPageSize(): Int =
    (((this + 12) / 25) * 25).coerceIn(MIN_MIDI_LIBRARY_PAGE_SIZE, MAX_MIDI_LIBRARY_PAGE_SIZE)

private val AppThemeMode.titleResId: Int
    get() = when (this) {
        AppThemeMode.System -> R.string.settings_theme_system
        AppThemeMode.Light -> R.string.settings_theme_light
        AppThemeMode.Dark -> R.string.settings_theme_dark
    }

private val AppThemeMode.subtitleResId: Int
    get() = when (this) {
        AppThemeMode.System -> R.string.settings_theme_system_subtitle
        AppThemeMode.Light -> R.string.settings_theme_light_subtitle
        AppThemeMode.Dark -> R.string.settings_theme_dark_subtitle
    }
