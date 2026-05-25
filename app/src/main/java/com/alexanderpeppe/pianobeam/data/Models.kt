package com.alexanderpeppe.pianobeam.data

import java.util.Locale

data class MidiLibraryItem(
    val id: String,
    val title: String,
    val originalName: String,
    val storedFileName: String,
    val durationUs: Long,
    val importedAtMs: Long,
    val notes: String = ""
)

data class MidiPlaylist(
    val id: String,
    val name: String,
    val itemIds: List<String>,
    val createdAtMs: Long
)

data class LibrarySnapshot(
    val files: List<MidiLibraryItem> = emptyList(),
    val playlists: List<MidiPlaylist> = emptyList()
)

data class BleMidiDeviceItem(
    val name: String,
    val address: String,
    val rssi: Int? = null,
    val likelyMidi: Boolean = false,
    val standardBleMidi: Boolean = false,
    val connectable: Boolean = true,
    val added: Boolean = false,
    val source: String = "BLE",
    val detail: String = address
)

data class KuhmannMidiResult(
    val id: Int,
    val title: String,
    val folder: String,
    val filename: String,
    val url: String,
    val midiType: String,
    val midiFormat: Int?,
    val channelCount: Int,
    val channels: List<Int>,
    val fileSize: Long,
    val sha256: String
)

data class KuhmannSearchUiState(
    val searching: Boolean = false,
    val downloading: Boolean = false,
    val query: String = "",
    val format: Int? = 0,
    val pianoOnly: Boolean = false,
    val channel: Int? = null,
    val limit: Int = 50,
    val results: List<KuhmannMidiResult> = emptyList(),
    val message: String = "Search the Kuhmann MIDI directory.",
    val lastImportedIds: List<Int> = emptyList()
)

data class ConnectionUiState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val scanning: Boolean = false,
    val bluetoothAvailable: Boolean = true,
    val bluetoothEnabled: Boolean = true,
    val deviceName: String? = null,
    val address: String? = null,
    val rememberedDeviceName: String? = null,
    val rememberedDeviceAddress: String? = null,
    val autoReconnectSuppressed: Boolean = false,
    val message: String = "Not connected"
)

enum class PlaybackMode {
    Idle,
    Preparing,
    Playing,
    Paused,
    Error
}

data class PlaybackUiState(
    val mode: PlaybackMode = PlaybackMode.Idle,
    val currentTitle: String? = null,
    val currentItemId: String? = null,
    val playlistName: String? = null,
    val currentTrackNumber: Int = 0,
    val totalTracks: Int = 0,
    val progressUs: Long = 0,
    val durationUs: Long = 0,
    val error: String? = null
) {
    val isPreparing: Boolean get() = mode == PlaybackMode.Preparing
    val isPlaying: Boolean get() = mode == PlaybackMode.Playing
    val isPaused: Boolean get() = mode == PlaybackMode.Paused
    val isActive: Boolean get() = mode == PlaybackMode.Preparing || mode == PlaybackMode.Playing || mode == PlaybackMode.Paused
}

data class RecordingUiState(
    val isRecording: Boolean = false,
    val isCountingDown: Boolean = false,
    val isSaving: Boolean = false,
    val title: String = "",
    val eventCount: Int = 0,
    val durationUs: Long = 0,
    val message: String = "Ready to record"
)

data class AppUiState(
    val files: List<MidiLibraryItem> = emptyList(),
    val playlists: List<MidiPlaylist> = emptyList(),
    val bleDevices: List<BleMidiDeviceItem> = emptyList(),
    val connection: ConnectionUiState = ConnectionUiState(),
    val playback: PlaybackUiState = PlaybackUiState(),
    val recording: RecordingUiState = RecordingUiState(),
    val kuhmann: KuhmannSearchUiState = KuhmannSearchUiState(),
    val volumePercent: Int = 88,
    val lastMessage: String = "Ready"
)

fun Long.formatDuration(): String {
    if (this <= 0L) return "--:--"
    val totalSeconds = this / 1_000_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

fun Long.formatClockTime(): String {
    val totalSeconds = (this / 1_000_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
