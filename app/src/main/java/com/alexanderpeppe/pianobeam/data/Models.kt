package com.alexanderpeppe.pianobeam.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
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
    val createdAtMs: Long,
    val colorHex: String? = null
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

data class ExternalMidiSource(
    val key: String,
    val displayName: String,
    val dialogSubtitle: String,
    val rightsSummary: String,
    val initialMessage: String,
    val searchMessage: String,
    val resultLabel: String,
    val searchUrl: String,
    val foundMessageSuffix: String,
    val allowedDownloadHosts: List<String> = emptyList(),
    val supportsFormatFilter: Boolean = false,
    val supportsChannelFilter: Boolean = false,
    val useInstrumentPianoFilter: Boolean = false
) {
    val isKuhmann: Boolean get() = key == KUHMANN_KEY
    val isMutopia: Boolean get() = key == MUTOPIA_KEY

    companion object {
        private const val KUHMANN_KEY = "kuhmann"
        private const val MUTOPIA_KEY = "mutopia"
        private const val KUHMANN_SEARCH_URL = "https://www.alexanderpeppe.com/kuhmann_midi_search.php"
        private const val MUTOPIA_SEARCH_URL = "https://www.alexanderpeppe.com/mutopia_midi_search.php"

        val Kuhmann = ExternalMidiSource(
            key = KUHMANN_KEY,
            displayName = "Kuhmann",
            dialogSubtitle = "Kuhmann source - noncommercial only",
            rightsSummary = "Personal/noncommercial unless permitted. Check rights first.",
            initialMessage = "Search the external Kuhmann MIDI source. Personal/noncommercial unless permitted.",
            searchMessage = "Searching external noncommercial Kuhmann MIDI source...",
            resultLabel = "external Kuhmann MIDI file",
            searchUrl = KUHMANN_SEARCH_URL,
            foundMessageSuffix = "Personal/noncommercial unless permitted.",
            allowedDownloadHosts = listOf("alexanderpeppe.com"),
            supportsFormatFilter = true,
            supportsChannelFilter = true
        )

        val Mutopia = ExternalMidiSource(
            key = MUTOPIA_KEY,
            displayName = "Mutopia",
            dialogSubtitle = "Mutopia Project - public domain",
            rightsSummary = "Public Domain / no rights reserved on source pages.",
            initialMessage = "Search the Mutopia Project MIDI source. Files are marked Public Domain / no rights reserved on their source pages.",
            searchMessage = "Searching Mutopia Project public-domain MIDI source...",
            resultLabel = "Mutopia Project MIDI file",
            searchUrl = MUTOPIA_SEARCH_URL,
            foundMessageSuffix = "Public Domain / no rights reserved on source pages.",
            allowedDownloadHosts = listOf("alexanderpeppe.com"),
            useInstrumentPianoFilter = true
        )

        val DefaultSources = listOf(Kuhmann, Mutopia)

        fun fromJson(json: String): List<ExternalMidiSource> {
            val root = json.trim()
            if (root.isBlank()) return DefaultSources
            val array = runCatching {
                if (root.startsWith("[")) JSONArray(root) else JSONObject(root).optJSONArray("sources") ?: JSONArray()
            }.getOrElse {
                return DefaultSources
            }
            val sources = buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toExternalMidiSource()?.let { add(it) }
                }
            }.distinctBy { it.key }
            return sources.ifEmpty { DefaultSources }
        }

        fun defaultForKey(key: String): ExternalMidiSource? =
            DefaultSources.firstOrNull { it.key == key }

        private fun JSONObject.toExternalMidiSource(): ExternalMidiSource? {
            val key = optCleanString("key", "")
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9_-]+"), "-")
                .trim('-', '_')
            if (key.isBlank()) return null
            val fallback = defaultForKey(key)
            val searchUrl = optCleanString("searchUrl", fallback?.searchUrl.orEmpty())
            if (searchUrl.isBlank()) return null
            val downloadHosts = optStringList("allowedDownloadHosts")
                .ifEmpty { listOfNotNull(searchUrl.hostOrNull()) }
                .map { it.normalizedHost() }
                .filter { it.isNotBlank() }
                .distinct()
            return ExternalMidiSource(
                key = key,
                displayName = optCleanString("displayName", fallback?.displayName ?: key),
                dialogSubtitle = optCleanString(
                    "dialogSubtitle",
                    fallback?.dialogSubtitle ?: "${optCleanString("displayName", key)} MIDI source"
                ),
                rightsSummary = optCleanString(
                    "rightsSummary",
                    fallback?.rightsSummary ?: "Check source rights before importing."
                ),
                initialMessage = optCleanString(
                    "initialMessage",
                    fallback?.initialMessage ?: "Search ${optCleanString("displayName", key)} MIDI files."
                ),
                searchMessage = optCleanString(
                    "searchMessage",
                    fallback?.searchMessage ?: "Searching ${optCleanString("displayName", key)} MIDI source..."
                ),
                resultLabel = optCleanString(
                    "resultLabel",
                    fallback?.resultLabel ?: "${optCleanString("displayName", key)} MIDI file"
                ),
                searchUrl = searchUrl,
                foundMessageSuffix = optCleanString(
                    "foundMessageSuffix",
                    fallback?.foundMessageSuffix ?: "Check source rights before importing."
                ),
                allowedDownloadHosts = downloadHosts,
                supportsFormatFilter = optBoolean("supportsFormatFilter", fallback?.supportsFormatFilter ?: false),
                supportsChannelFilter = optBoolean("supportsChannelFilter", fallback?.supportsChannelFilter ?: false),
                useInstrumentPianoFilter = optBoolean("useInstrumentPianoFilter", fallback?.useInstrumentPianoFilter ?: false)
            )
        }

        private fun JSONObject.optCleanString(name: String, fallback: String): String =
            optString(name, "").trim().ifBlank { fallback }

        private fun JSONObject.optStringList(name: String): List<String> {
            val array = optJSONArray(name) ?: return emptyList()
            return buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }

        private fun String.hostOrNull(): String? =
            runCatching { URL(this).host }.getOrNull()?.normalizedHost()

        private fun String.normalizedHost(): String =
            trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .removePrefix("*.")
                .lowercase(Locale.US)
    }
}

data class KuhmannMidiResult(
    val source: ExternalMidiSource = ExternalMidiSource.Kuhmann,
    val id: Int,
    val title: String,
    val folder: String,
    val filename: String,
    val url: String,
    val midiType: String,
    val midiFormat: Int?,
    val channelCount: Int,
    val channels: List<Int>,
    val noteChannels: List<Int> = emptyList(),
    val pedalOnlyChannels: List<Int> = emptyList(),
    val fileSize: Long,
    val sha256: String,
    val sourceName: String = source.displayName,
    val composer: String = "",
    val instrument: String = "",
    val style: String = "",
    val opus: String = "",
    val license: String = "",
    val licenseUrl: String = "",
    val maintainer: String = "",
    val sourceUrl: String = "",
    val ftpUrl: String = ""
) {
    val stableKey: String get() = "${source.key}:$id"
}

data class KuhmannSearchUiState(
    val searching: Boolean = false,
    val downloading: Boolean = false,
    val availableSources: List<ExternalMidiSource> = ExternalMidiSource.DefaultSources,
    val source: ExternalMidiSource = ExternalMidiSource.Kuhmann,
    val query: String = "",
    val format: Int? = null,
    val pianoOnly: Boolean = true,
    val channel: Int? = null,
    val limit: Int = 50,
    val results: List<KuhmannMidiResult> = emptyList(),
    val message: String = source.initialMessage,
    val lastImportedKeys: List<String> = emptyList(),
    val activePlaybackResultKey: String? = null
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

data class PlaybackChannelInfo(
    val channel: Int,
    val label: String? = null,
    val instrumentName: String? = null,
    val programNumbers: List<Int> = emptyList(),
    val trackTitles: List<String> = emptyList(),
    val metaInstrumentNames: List<String> = emptyList()
)

data class AppUiState(
    val files: List<MidiLibraryItem> = emptyList(),
    val playlists: List<MidiPlaylist> = emptyList(),
    val bleDevices: List<BleMidiDeviceItem> = emptyList(),
    val connection: ConnectionUiState = ConnectionUiState(),
    val playback: PlaybackUiState = PlaybackUiState(),
    val playbackChannels: List<PlaybackChannelInfo> = emptyList(),
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
