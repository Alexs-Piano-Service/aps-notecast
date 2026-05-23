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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexanderpeppe.pianobeam.data.AppSettings
import com.alexanderpeppe.pianobeam.data.AppThemeMode
import com.alexanderpeppe.pianobeam.data.MidiChannelControl
import com.alexanderpeppe.pianobeam.data.MidiPlaylist
import com.alexanderpeppe.pianobeam.data.PlaybackAdvanceMode
import com.alexanderpeppe.pianobeam.data.RepeatMode
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    settings: AppSettings,
    playlists: List<MidiPlaylist>,
    preferredDeviceName: String?,
    onSettingsChange: (AppSettings) -> Unit,
    onForgetDevice: () -> Unit,
    onDiagnostics: () -> Unit,
    onBackupLibrary: () -> Unit,
    onRestoreLibrary: () -> Unit,
    onDismiss: () -> Unit
) {
    var showAdvancedMidi by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SettingSection("Appearance") {
                        AppThemeMode.values().forEach { mode ->
                            RadioRow(
                                title = mode.title,
                                subtitle = mode.subtitle,
                                selected = mode == settings.themeMode,
                                onClick = { onSettingsChange(settings.copy(themeMode = mode)) }
                            )
                        }
                    }
                }

                item {
                    SettingSection("Playback") {
                        RadioRow("Play next track", "Continue through a playlist", settings.playbackAdvanceMode == PlaybackAdvanceMode.AutoPlayNext) {
                            onSettingsChange(settings.copy(playbackAdvanceMode = PlaybackAdvanceMode.AutoPlayNext))
                        }
                        RadioRow("Stop after current song", "Finish this file, then stop", settings.playbackAdvanceMode == PlaybackAdvanceMode.StopAfterCurrent) {
                            onSettingsChange(settings.copy(playbackAdvanceMode = PlaybackAdvanceMode.StopAfterCurrent))
                        }
                        RadioRow("Stop after playlist", "Play the selected playlist once", settings.playbackAdvanceMode == PlaybackAdvanceMode.StopAfterPlaylist) {
                            onSettingsChange(settings.copy(playbackAdvanceMode = PlaybackAdvanceMode.StopAfterPlaylist))
                        }
                        CompactDivider()
                        RadioRow("No repeat", "Stop when the selected play mode finishes", settings.repeatMode == RepeatMode.Off) {
                            onSettingsChange(settings.copy(repeatMode = RepeatMode.Off))
                        }
                        RadioRow("Repeat one", "Loop the current MIDI file", settings.repeatMode == RepeatMode.One) {
                            onSettingsChange(settings.copy(repeatMode = RepeatMode.One))
                        }
                        RadioRow("Repeat playlist", "Loop playlists until stopped", settings.repeatMode == RepeatMode.Playlist) {
                            onSettingsChange(settings.copy(repeatMode = RepeatMode.Playlist))
                        }
                        SwitchRow(
                            title = "Shuffle playlists by default",
                            subtitle = "The shuffle button still overrides this",
                            checked = settings.shufflePlaylistsByDefault,
                            onCheckedChange = { onSettingsChange(settings.copy(shufflePlaylistsByDefault = it)) }
                        )
                    }
                }

                item {
                    SettingSection("Tempo And Key") {
                        SliderRow(
                            title = "Tempo",
                            valueLabel = "${settings.tempoPercent}%",
                            value = settings.tempoPercent.toFloat(),
                            range = 50f..150f,
                            onValueChange = { onSettingsChange(settings.copy(tempoPercent = it.roundToInt())) }
                        )
                        SliderRow(
                            title = "Transpose",
                            valueLabel = "${settings.transposeSemitones} semitones",
                            value = settings.transposeSemitones.toFloat(),
                            range = -12f..12f,
                            steps = 23,
                            onValueChange = { onSettingsChange(settings.copy(transposeSemitones = it.roundToInt())) }
                        )
                        SwitchRow(
                            title = "Leave channel 10 drums alone",
                            subtitle = "Useful for MIDI files with percussion",
                            checked = settings.excludeDrumChannelFromTranspose,
                            onCheckedChange = { onSettingsChange(settings.copy(excludeDrumChannelFromTranspose = it)) }
                        )
                    }
                }

                item {
                    SettingSection("Advanced MIDI") {
                        OutlinedButton(onClick = { showAdvancedMidi = !showAdvancedMidi }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showAdvancedMidi) "Hide Channel Controls" else "Show Channel Controls")
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
                    SettingSection("Recording") {
                        SwitchRow(
                            title = "Auto-trim silence",
                            subtitle = "Start the saved file at the first MIDI event",
                            checked = settings.autoTrimRecordingSilence,
                            onCheckedChange = { onSettingsChange(settings.copy(autoTrimRecordingSilence = it)) }
                        )
                        CycleRow(
                            title = "Save recordings to",
                            value = playlists.firstOrNull { it.id == settings.recordingTargetPlaylistId }?.name ?: "Library only",
                            onClick = {
                                val targets = listOf<String?>(null) + playlists.map { it.id }
                                val nextIndex = ((targets.indexOf(settings.recordingTargetPlaylistId).takeIf { it >= 0 } ?: 0) + 1) % targets.size
                                onSettingsChange(settings.copy(recordingTargetPlaylistId = targets[nextIndex]))
                            }
                        )
                        SliderRow(
                            title = "Count-in",
                            valueLabel = "${settings.recordingCountdownSeconds}s",
                            value = settings.recordingCountdownSeconds.toFloat(),
                            range = 0f..8f,
                            steps = 7,
                            onValueChange = { onSettingsChange(settings.copy(recordingCountdownSeconds = it.roundToInt())) }
                        )
                        SwitchRow(
                            title = "Metronome count-in",
                            subtitle = "Shows beats during the countdown",
                            checked = settings.recordingMetronomeEnabled,
                            onCheckedChange = { onSettingsChange(settings.copy(recordingMetronomeEnabled = it)) }
                        )
                        SwitchRow(
                            title = "Confirm discard",
                            subtitle = "Ask before throwing away an active recording",
                            checked = settings.confirmDiscardRecording,
                            onCheckedChange = { onSettingsChange(settings.copy(confirmDiscardRecording = it)) }
                        )
                    }
                }

                item {
                    SettingSection("Connection") {
                        SwitchRow(
                            title = "Auto-reconnect",
                            subtitle = "Keep looking for the preferred WIDI while APS NoteCast is open",
                            checked = settings.autoReconnectEnabled,
                            onCheckedChange = { onSettingsChange(settings.copy(autoReconnectEnabled = it)) }
                        )
                        SwitchRow(
                            title = "Scan on launch",
                            subtitle = "Begin looking for MIDI devices when the app opens",
                            checked = settings.scanOnLaunch,
                            onCheckedChange = { onSettingsChange(settings.copy(scanOnLaunch = it)) }
                        )
                        SliderRow(
                            title = "Reconnect every",
                            valueLabel = "${settings.reconnectIntervalSeconds}s",
                            value = settings.reconnectIntervalSeconds.toFloat(),
                            range = 3f..60f,
                            onValueChange = { onSettingsChange(settings.copy(reconnectIntervalSeconds = it.roundToInt())) }
                        )
                        SliderRow(
                            title = "Reconnect timeout",
                            valueLabel = "${settings.reconnectTimeoutSeconds}s",
                            value = settings.reconnectTimeoutSeconds.toFloat(),
                            range = 10f..180f,
                            onValueChange = { onSettingsChange(settings.copy(reconnectTimeoutSeconds = it.roundToInt())) }
                        )
                        Text(
                            "Preferred WIDI: ${preferredDeviceName ?: "none"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onDiagnostics, modifier = Modifier.weight(1f)) {
                                Text("Diagnostics")
                            }
                            OutlinedButton(onClick = onForgetDevice, modifier = Modifier.weight(1f), enabled = preferredDeviceName != null) {
                                Text("Forget")
                            }
                        }
                    }
                }

                item {
                    SettingSection("Library") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onBackupLibrary, modifier = Modifier.weight(1f)) {
                                Text("Backup")
                            }
                            OutlinedButton(onClick = onRestoreLibrary, modifier = Modifier.weight(1f)) {
                                Text("Restore")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(value, maxLines = 1)
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
            Text("Ch ${control.channel}", modifier = Modifier.width(42.dp), fontWeight = FontWeight.SemiBold)
            Checkbox(checked = control.muted, onCheckedChange = { onControlChange(control.copy(muted = it)) })
            Text("Mute", style = MaterialTheme.typography.bodySmall)
            Checkbox(checked = control.solo, onCheckedChange = { onControlChange(control.copy(solo = it)) })
            Text("Solo", style = MaterialTheme.typography.bodySmall)
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

private val AppThemeMode.title: String
    get() = when (this) {
        AppThemeMode.System -> "System default"
        AppThemeMode.Light -> "Light"
        AppThemeMode.Dark -> "Dark"
    }

private val AppThemeMode.subtitle: String
    get() = when (this) {
        AppThemeMode.System -> "Follow Android"
        AppThemeMode.Light -> "Use the light color scheme"
        AppThemeMode.Dark -> "Use the dark color scheme"
    }
