package com.alexanderpeppe.pianobeam.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.alexanderpeppe.pianobeam.MainActivity
import com.alexanderpeppe.pianobeam.R
import com.alexanderpeppe.pianobeam.data.AppSettings
import com.alexanderpeppe.pianobeam.data.AppUiState
import com.alexanderpeppe.pianobeam.data.BleMidiDeviceItem
import com.alexanderpeppe.pianobeam.data.ConnectionUiState
import com.alexanderpeppe.pianobeam.data.KuhmannMidiResult
import com.alexanderpeppe.pianobeam.data.KuhmannSearchUiState
import com.alexanderpeppe.pianobeam.data.MidiChannelControl
import com.alexanderpeppe.pianobeam.data.MidiLibraryItem
import com.alexanderpeppe.pianobeam.data.MidiRepository
import com.alexanderpeppe.pianobeam.data.PlaybackAdvanceMode
import com.alexanderpeppe.pianobeam.data.PlaybackMode
import com.alexanderpeppe.pianobeam.data.PlaybackUiState
import com.alexanderpeppe.pianobeam.data.RecordingUiState
import com.alexanderpeppe.pianobeam.data.RepeatMode
import com.alexanderpeppe.pianobeam.data.formatClockTime
import com.alexanderpeppe.pianobeam.midi.MidiFileParser
import com.alexanderpeppe.pianobeam.midi.MidiFileWriter
import com.alexanderpeppe.pianobeam.net.ApsNetworkStatus
import com.alexanderpeppe.pianobeam.reporting.AppEventLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.coroutines.coroutineContext

class NoteCastService : Service() {
    companion object {
        private const val CHANNEL_ID = "pianobeam_playback"
        private const val NOTIFICATION_ID = 440
        private const val ACTION_PLAY_PAUSE = "com.alexanderpeppe.notecast.PLAY_PAUSE"
        private const val ACTION_PREVIOUS = "com.alexanderpeppe.notecast.PREVIOUS"
        private const val ACTION_NEXT = "com.alexanderpeppe.notecast.NEXT"
        private const val ACTION_STOP = "com.alexanderpeppe.notecast.STOP"
        private const val ACTION_PANIC = "com.alexanderpeppe.notecast.PANIC"
        private const val ACTION_DEBUG_SCAN = "com.alexanderpeppe.notecast.DEBUG_SCAN"
        private const val ACTION_DEBUG_DIAGNOSTICS = "com.alexanderpeppe.notecast.DEBUG_DIAGNOSTICS"
        private const val LOG_TAG = "NoteCastBle"
        private const val KUHMANN_SEARCH_URL = "https://www.alexanderpeppe.com/kuhmann_midi_search.php"
        private const val KUHMANN_SEARCH_MAX_LIMIT = 100
        private const val KUHMANN_MAX_DOWNLOAD_BYTES = 5 * 1024 * 1024
        private const val SCAN_WINDOW_MS = 12_000L
        private const val AUTO_RECONNECT_SCAN_WINDOW_MS = 4_500L
        private const val RECENT_BLE_SCAN_TTL_MS = 60_000L
        private const val CONNECTION_ATTACH_VERIFY_DELAY_MS = 1_500L
        private const val BLE_MIDI_READY_SETTLE_MS = 2_000L
        private const val SCHEDULE_AHEAD_NS = 45_000_000L
        private const val START_DELAY_NS = 160_000_000L
        private const val PROGRESS_POST_INTERVAL_NS = 220_000_000L
        private const val PREFS_NAME = "pianobeam"
        private const val PREF_LAST_DEVICE_ADDRESS = "last_device_address"
        private const val PREF_LAST_DEVICE_NAME = "last_device_name"
        private const val PREF_KNOWN_DEVICES = "known_devices"
        private const val PREF_HIDDEN_DEVICES = "hidden_devices"
        private const val PREF_DEVICE_ALIASES = "device_aliases"
        private const val PREF_MANUAL_DISCONNECT = "manual_disconnect"

        private val MIDI_SERVICE_UUID: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
        private val OPAQUE_BLE_NAME_REGEX = Regex("^[0-9A-Fa-f]{8,}$")
        private val MIDI_NAME_HINTS = listOf(
            "widi",
            "cme",
            "thru6",
            "thru 6",
            "midi",
            "md-bt",
            "ud-bt"
        )
        private val GENERIC_BLE_NAME_PREFIXES = listOf(
            "elk-ble"
        )
    }

    inner class LocalBinder : Binder() {
        fun service(): NoteCastService = this@NoteCastService
    }

    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var repository: MidiRepository
    private lateinit var midiManager: MidiManager
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var prefs: SharedPreferences
    private lateinit var mediaSession: MediaSession

    private val devicesByAddress = linkedMapOf<String, BluetoothDevice>()
    private val bleScanItemsByAddress = linkedMapOf<String, BleMidiDeviceItem>()
    private val bleScanSeenAtMsByAddress = linkedMapOf<String, Long>()
    private val bleScanSeenAtMsByName = linkedMapOf<String, Long>()
    private val midiDevicesByConnectionId = linkedMapOf<String, MidiDeviceInfo>()
    private var scanCallback: ScanCallback? = null
    private var scanTimeoutJob: Job? = null
    private var kuhmannSearchJob: Job? = null
    private var kuhmannDownloadJob: Job? = null
    private var scanAutoConnectKnownDevices = false
    private var scanPreferredReconnectAddress: String? = null
    private var scanFallbackReconnectAddress: String? = null
    private var scanSessionId = 0L
    private var scanStartedAtMs = 0L
    private val scanResultCountsByAddress = linkedMapOf<String, Int>()
    private var lastScanSummary = "No BLE scan has completed yet."
    @Volatile
    private var autoReconnectPausedForDevicePicker = false
    private val midiDeviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            refreshAndroidMidiDevices()
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            val removedConnectionId = midiConnectionId(device)
            val openedConnectionId = connectedMidiDeviceInfo?.let { midiConnectionId(it) }
            val removedCurrentDevice = connectedMidiDeviceInfo?.id == device.id ||
                sameConnectionTarget(openedConnectionId, removedConnectionId)
            refreshAndroidMidiDevices()
            val connection = _state.value.connection
            if (connection.connected && (sameConnectionTarget(connection.address, removedConnectionId) || removedCurrentDevice)) {
                handleConnectionLost("${connection.deviceName ?: "MIDI device"} disconnected.")
            }
        }
    }
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    refreshBluetoothState()
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_TURNING_OFF,
                        BluetoothAdapter.STATE_OFF -> handleBluetoothTurnedOff()
                        BluetoothAdapter.STATE_ON -> {
                            AppEventLog.append("Bluetooth turned on.")
                            refreshKnownDevices()
                            if (appSettings.autoReconnectEnabled) autoReconnectIfPossible()
                        }
                    }
                }
                BluetoothDevice.ACTION_PAIRING_REQUEST -> noteBluetoothPairingRequest(intent)
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> noteBluetoothBondStateChanged(intent)
                BluetoothDevice.ACTION_ACL_CONNECTED -> refreshKnownDevices()
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED -> noteBluetoothDeviceDisconnected(intent.bluetoothDeviceExtra())
            }
        }
    }
    private var bluetoothStateReceiverRegistered = false

    private var midiDevice: MidiDevice? = null
    private var connectedMidiDeviceInfo: MidiDeviceInfo? = null
    private var midiInputPort: MidiInputPort? = null
    private var midiOutputPort: MidiOutputPort? = null
    private var playbackJob: Job? = null
    private var recordingCountdownJob: Job? = null
    private var connectionMonitorJob: Job? = null
    private var connectionVerificationJob: Job? = null
    private var reconnectJob: Job? = null
    private val connectionGeneration = AtomicLong(0L)
    private val playbackGeneration = AtomicLong(0L)
    @Volatile
    private var appSettings: AppSettings = AppSettings()
    private var wakeLock: PowerManager.WakeLock? = null
    private var foreground = false
    @Volatile
    private var skipRequest: Int = 0
    @Volatile
    private var seekRequestUs: Long = -1L

    private data class PreparedSequenceCacheKey(
        val itemId: String,
        val storedFileName: String,
        val importedAtMs: Long,
        val fileLength: Long,
        val fileModifiedAtMs: Long,
        val tempoPercent: Int,
        val transposeSemitones: Int,
        val excludeDrumChannelFromTranspose: Boolean,
        val channelSignature: String
    )

    private data class PreparedPlaybackData(
        val sequence: MidiFileParser.MidiSequence,
        val channels: List<Int>
    )

    private val sequenceCacheLock = Any()
    private val preparedSequenceCache = object : LinkedHashMap<PreparedSequenceCacheKey, PreparedPlaybackData>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PreparedSequenceCacheKey, PreparedPlaybackData>?): Boolean =
            size > 4
    }

    private val recordingLock = Any()
    private val recordingEvents = mutableListOf<MidiFileWriter.RecordedEvent>()
    private var recordingStartedNs = 0L
    private var recordingReceiver: MidiReceiver? = null

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        repository = MidiRepository(applicationContext)
        midiManager = getSystemService(MidiManager::class.java)
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        createMediaSession()
        repository.ensureDemoMidi()
        restoreRememberedDevice()
        midiManager.registerDeviceCallback(midiDeviceCallback, mainHandler)
        registerBluetoothStateReceiver()
        refreshKnownDevices()
        reloadLibrary("Ready")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlaybackFromMediaControls()
            ACTION_PREVIOUS -> skipToPrevious()
            ACTION_NEXT -> skipToNext()
            ACTION_STOP -> stopPlayback(userRequested = true)
            ACTION_PANIC -> panic()
            ACTION_DEBUG_SCAN -> {
                logEvent("ADB requested BLE MIDI scan.")
                startBleScan()
            }
            ACTION_DEBUG_DIAGNOSTICS -> logEvent("ADB diagnostics:\n${connectionDiagnostics()}")
        }
        return if (_state.value.playback.isActive) START_STICKY else START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!_state.value.playback.isActive) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        val wasActive = _state.value.playback.isActive
        stopBleScan()
        unregisterBluetoothStateReceiver()
        runCatching { midiManager.unregisterDeviceCallback(midiDeviceCallback) }
        connectionGeneration.incrementAndGet()
        playbackGeneration.incrementAndGet()
        playbackJob?.cancel()
        recordingCountdownJob?.cancel()
        kuhmannSearchJob?.cancel()
        kuhmannDownloadJob?.cancel()
        connectionMonitorJob?.cancel()
        connectionVerificationJob?.cancel()
        reconnectJob?.cancel()
        if (wasActive) sendPanicMessages()
        closeMidiConnection()
        releaseWakeLock()
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    fun applySettings(settings: AppSettings) {
        val previousSettings = appSettings
        appSettings = settings
        val volumeBehaviorChanged = previousSettings.appControlsVolume != settings.appControlsVolume
        val channelVolumesChanged = previousSettings.channelControls.volumeSignature() != settings.channelControls.volumeSignature()
        val muteSoloChanged = previousSettings.channelControls.muteSoloSignature() != settings.channelControls.muteSoloSignature()
        if (_state.value.connection.connected && (volumeBehaviorChanged || channelVolumesChanged || muteSoloChanged)) {
            serviceScope.launch(Dispatchers.IO) {
                if (muteSoloChanged) sendPanicMessages()
                sendVolumeMessages(_state.value.volumePercent)
            }
        }
    }

    fun setAutoReconnectPausedForDevicePicker(paused: Boolean) {
        autoReconnectPausedForDevicePicker = paused
    }

    fun importMidiFiles(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        serviceScope.launch(Dispatchers.IO) {
            var imported = 0
            var failed = 0
            uris.forEach { uri ->
                runCatching {
                    repository.importMidi(uri)
                    imported++
                }.onFailure {
                    failed++
                }
            }
            withContext(Dispatchers.Main) {
                val message = when {
                    imported > 0 && failed == 0 -> "Imported $imported MIDI file${if (imported == 1) "" else "s"}."
                    imported > 0 -> "Imported $imported MIDI file${if (imported == 1) "" else "s"}; $failed failed."
                    else -> "Could not import the selected MIDI file${if (failed == 1) "" else "s"}."
                }
                reloadLibrary(message)
            }
        }
    }

    fun searchKuhmannMidi(query: String, format: Int?, pianoOnly: Boolean, channel: Int?, limit: Int) {
        val cleanQuery = query.trim()
        val cleanChannel = channel?.coerceIn(1, 16)
        val cleanLimit = limit.coerceIn(1, 100)
        if (cleanQuery.isBlank()) {
            _state.update {
                it.copy(
                    kuhmann = it.kuhmann.copy(
                        query = cleanQuery,
                        format = format,
                        pianoOnly = pianoOnly,
                        channel = cleanChannel,
                        limit = cleanLimit,
                        message = "Enter a search term."
                    )
                )
            }
            return
        }
        if (!ApsNetworkStatus.canReachInternet(this)) {
            val message = ApsNetworkStatus.userMessage(this)
            _state.update {
                it.copy(
                    kuhmann = it.kuhmann.copy(
                        searching = false,
                        query = cleanQuery,
                        format = format,
                        pianoOnly = pianoOnly,
                        channel = cleanChannel,
                        limit = cleanLimit,
                        message = message
                    ),
                    lastMessage = message
                )
            }
            return
        }
        kuhmannSearchJob?.cancel()
        _state.update {
            it.copy(
                kuhmann = it.kuhmann.copy(
                    searching = true,
                    query = cleanQuery,
                    format = format,
                    pianoOnly = pianoOnly,
                    channel = cleanChannel,
                    limit = cleanLimit,
                    message = "Searching Kuhmann MIDI..."
                )
            )
        }
        kuhmannSearchJob = serviceScope.launch(Dispatchers.IO) {
            runCatching {
                fetchKuhmannSearchResults(cleanQuery, format, pianoOnly, cleanChannel, cleanLimit)
            }.onSuccess { response ->
                withContext(Dispatchers.Main) {
                    val message = if (response.results.isEmpty()) {
                        "No Kuhmann MIDI files matched."
                    } else {
                        "Found ${response.results.size} of ${response.count} Kuhmann MIDI file${if (response.count == 1) "" else "s"}."
                    }
                    _state.update {
                        it.copy(
                            kuhmann = it.kuhmann.copy(
                                searching = false,
                                results = response.results,
                                message = message,
                                lastImportedIds = emptyList()
                            ),
                            lastMessage = message
                        )
                    }
                }
            }.onFailure { t ->
                if (t is CancellationException) throw t
                withContext(Dispatchers.Main) {
                    val message = ApsNetworkStatus.userMessage(this@NoteCastService, t)
                    _state.update {
                        it.copy(
                            kuhmann = it.kuhmann.copy(searching = false, message = message),
                            lastMessage = message
                        )
                    }
                }
            }
        }
    }

    fun downloadKuhmannMidi(results: List<KuhmannMidiResult>) {
        val uniqueResults = results.distinctBy { it.id }.filter { it.url.isNotBlank() }
        if (uniqueResults.isEmpty()) return
        if (!ApsNetworkStatus.canReachInternet(this)) {
            val message = ApsNetworkStatus.userMessage(this)
            _state.update {
                it.copy(
                    kuhmann = it.kuhmann.copy(downloading = false, message = message),
                    lastMessage = message
                )
            }
            return
        }
        kuhmannDownloadJob?.cancel()
        _state.update {
            it.copy(
                kuhmann = it.kuhmann.copy(
                    downloading = true,
                    message = "Downloading ${uniqueResults.size} Kuhmann MIDI file${if (uniqueResults.size == 1) "" else "s"}..."
                )
            )
        }
        kuhmannDownloadJob = serviceScope.launch(Dispatchers.IO) {
            var imported = 0
            var failed = 0
            var networkFailure = false
            val importedIds = mutableListOf<Int>()
            uniqueResults.forEach { result ->
                ensureActive()
                runCatching {
                    val bytes = downloadKuhmannBytes(result)
                    repository.importMidiBytes(
                        bytes = bytes,
                        displayName = result.filename.ifBlank { "${result.title}.mid" },
                        preferredTitle = result.title,
                        notePrefix = "Kuhmann MIDI: ${result.folder}"
                    )
                    imported++
                    importedIds += result.id
                }.onFailure { t ->
                    failed++
                    if (ApsNetworkStatus.isLikelyNetworkFailure(t)) networkFailure = true
                    logEvent("Kuhmann MIDI download failed id=${result.id} title=${result.title}: ${t.message ?: t::class.java.simpleName}")
                }
            }
            withContext(Dispatchers.Main) {
                val message = when {
                    imported > 0 && failed == 0 -> "Downloaded $imported Kuhmann MIDI file${if (imported == 1) "" else "s"}."
                    imported > 0 -> "Downloaded $imported Kuhmann MIDI file${if (imported == 1) "" else "s"}; $failed failed."
                    networkFailure -> ApsNetworkStatus.userMessage(this@NoteCastService)
                    else -> "Could not download the selected Kuhmann MIDI file${if (failed == 1) "" else "s"}."
                }
                reloadLibrary(message)
                _state.update {
                    it.copy(
                        kuhmann = it.kuhmann.copy(
                            downloading = false,
                            message = message,
                            lastImportedIds = importedIds
                        )
                    )
                }
            }
        }
    }

    fun deleteMidiFile(itemId: String) {
        stopPlaybackIfUsing(itemId)
        repository.deleteFile(itemId)
        reloadLibrary("Removed MIDI file.")
    }

    fun deleteMidiFiles(itemIds: List<String>) {
        val cleanIds = itemIds.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return
        if (cleanIds.any { _state.value.playback.currentItemId == it }) stopPlayback(userRequested = true)
        repository.deleteFiles(cleanIds)
        reloadLibrary("Removed ${cleanIds.size} MIDI file${if (cleanIds.size == 1) "" else "s"}.")
    }

    fun renameMidiFile(itemId: String, title: String) {
        repository.renameFile(itemId, title)
        reloadLibrary("Renamed MIDI file.")
    }

    fun createPlaylist(name: String) {
        repository.createPlaylist(name)
        reloadLibrary("Created playlist.")
    }

    fun createPlaylist(name: String, itemIds: List<String>) {
        repository.createPlaylist(name, itemIds)
        val count = itemIds.distinct().size
        reloadLibrary("Created playlist with $count MIDI file${if (count == 1) "" else "s"}.")
    }

    fun deletePlaylist(playlistId: String) {
        repository.deletePlaylist(playlistId)
        reloadLibrary("Deleted playlist.")
    }

    fun renamePlaylist(playlistId: String, name: String) {
        repository.renamePlaylist(playlistId, name)
        reloadLibrary("Renamed playlist.")
    }

    fun setPlaylistColor(playlistId: String, colorHex: String?) {
        repository.setPlaylistColor(playlistId, colorHex)
        reloadLibrary("Updated playlist color.")
    }

    fun duplicatePlaylist(playlistId: String) {
        repository.duplicatePlaylist(playlistId)
        reloadLibrary("Duplicated playlist.")
    }

    fun addToPlaylist(playlistId: String, itemId: String) {
        repository.addToPlaylist(playlistId, itemId)
        reloadLibrary("Added to playlist.")
    }

    fun addToPlaylist(playlistId: String, itemIds: List<String>) {
        repository.addToPlaylist(playlistId, itemIds)
        reloadLibrary("Added ${itemIds.size} MIDI file${if (itemIds.size == 1) "" else "s"} to playlist.")
    }

    fun removeFromPlaylist(playlistId: String, index: Int) {
        repository.removeFromPlaylist(playlistId, index)
        reloadLibrary("Removed from playlist.")
    }

    fun movePlaylistItem(playlistId: String, fromIndex: Int, direction: Int) {
        repository.movePlaylistItem(playlistId, fromIndex, direction)
        reloadLibrary("Playlist updated.")
    }

    fun exportMidiFile(itemId: String, uri: Uri) {
        val item = _state.value.files.firstOrNull { it.id == itemId } ?: return
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                contentResolver.openOutputStream(uri).use { output ->
                    requireNotNull(output) { "Could not open export destination." }
                    repository.fileFor(item).inputStream().use { input -> input.copyTo(output) }
                }
            }.onSuccess {
                withContext(Dispatchers.Main) { postMessage("Exported ${item.title}.") }
            }.onFailure {
                withContext(Dispatchers.Main) { postMessage("Could not export ${item.title}.") }
            }
        }
    }

    fun shareMidiFileIntent(itemId: String): Intent? {
        val item = _state.value.files.firstOrNull { it.id == itemId } ?: return null
        val file = repository.fileFor(item)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/midi"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, item.originalName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun exportLibraryBackup(uri: Uri) {
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val json = repository.exportBackupJson()
                contentResolver.openOutputStream(uri).use { output ->
                    requireNotNull(output) { "Could not open backup destination." }
                    output.write(json.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                withContext(Dispatchers.Main) { postMessage("Library backup exported.") }
            }.onFailure {
                withContext(Dispatchers.Main) { postMessage("Could not export library backup.") }
            }
        }
    }

    fun restoreLibraryBackup(uri: Uri) {
        stopPlayback(userRequested = false)
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val json = contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Could not open backup file." }
                    input.readBytes().toString(Charsets.UTF_8)
                }
                repository.restoreBackupJson(json)
                repository.ensureDemoMidi()
            }.onSuccess {
                withContext(Dispatchers.Main) { reloadLibrary("Library backup restored.") }
            }.onFailure {
                withContext(Dispatchers.Main) { postMessage("Could not restore library backup.") }
            }
        }
    }

    fun autoReconnectIfPossible(force: Boolean = false) {
        if (autoReconnectPausedForDevicePicker && !force) return
        if (_state.value.connection.connected || _state.value.connection.connecting || _state.value.connection.scanning) return
        if (force) clearManualDisconnectSuppression()
        if (manualDisconnectSuppressed() && !force) return
        refreshKnownDevices()
        val refreshedCandidates = knownReconnectCandidates()
        val preferred = refreshedCandidates.firstOrNull() ?: return
        val available = refreshedCandidates.firstNotNullOfOrNull { candidate ->
            availableAutoReconnectAddress(candidate.address)?.let { address -> candidate to address }
        }
        if (available != null) {
            val (candidate, address) = available
            postMessage("Reconnecting to ${candidate.name}...")
            connect(address)
            return
        }
        val initialFallbackAddress = refreshedCandidates
            .drop(1)
            .firstNotNullOfOrNull { availableAutoReconnectAddress(it.address) }
        if (!hasScanPermission()) {
            postMessage("Bluetooth scan permission is needed before looking for ${preferred.name}.")
            return
        }
        postMessage("Looking for known BLE MIDI devices...")
        startBleScan(autoConnectKnownDevices = true, initialFallbackAddress = initialFallbackAddress)
    }

    fun refreshBluetoothState() {
        val adapter = bluetoothManager.adapter
        _state.update {
            it.copy(
                connection = it.connection.copy(
                    bluetoothAvailable = adapter != null,
                    bluetoothEnabled = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
                )
            )
        }
    }

    fun refreshKnownDevices() {
        refreshBluetoothState()
        refreshBondedBluetoothDevices()
        refreshAndroidMidiDevices()
    }

    fun startBleScan(autoConnectKnownDevices: Boolean = false, initialFallbackAddress: String? = null) {
        if (!autoConnectKnownDevices) clearHiddenDevices()
        refreshKnownDevices()
        if (!hasScanPermission()) {
            postMessage("Bluetooth scan permission is required before scanning.")
            return
        }
        val adapter = bluetoothManager.adapter
        if (adapter == null) {
            postMessage("This device does not have Bluetooth hardware.")
            return
        }
        if (!isBluetoothEnabled()) {
            _state.update {
                it.copy(
                    connection = it.connection.copy(bluetoothEnabled = false, scanning = false),
                    lastMessage = "Bluetooth is off. Turn it on to scan for MIDI devices."
                )
            }
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            postMessage("Bluetooth LE scanning is not available right now.")
            return
        }

        stopBleScan(connectReconnectFallback = false)
        beginBleScanSession(autoConnectKnownDevices)
        refreshBondedBluetoothDevices()
        refreshAndroidMidiDevices()
        scanAutoConnectKnownDevices = autoConnectKnownDevices
        scanPreferredReconnectAddress = knownReconnectCandidates().firstOrNull()?.address
        scanFallbackReconnectAddress = initialFallbackAddress
        val message = when {
            autoConnectKnownDevices -> "Looking for known BLE MIDI devices..."
            _state.value.connection.connected -> "Looking for another BLE MIDI device..."
            else -> "Scanning nearby BLE and Android MIDI devices..."
        }
        _state.update {
            it.copy(
                bleDevices = mergedDeviceList(),
                connection = it.connection.copy(scanning = true, bluetoothEnabled = true, message = message),
                lastMessage = message
            )
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                addScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { addScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                scanTimeoutJob?.cancel()
                scanTimeoutJob = null
                scanCallback = null
                finishBleScanSession("failed with Android error $errorCode")
                clearReconnectScanState()
                val message = "BLE scan failed with error $errorCode."
                _state.update {
                    it.copy(
                        connection = it.connection.copy(scanning = false, message = message),
                        lastMessage = message
                    )
                }
            }
        }

        scanCallback = callback
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0L)
            .build()

        try {
            scanner.startScan(null, settings, callback)
            scanTimeoutJob = serviceScope.launch {
                delay(if (autoConnectKnownDevices) AUTO_RECONNECT_SCAN_WINDOW_MS else SCAN_WINDOW_MS)
                stopBleScan(connectReconnectFallback = true)
            }
        } catch (security: SecurityException) {
            finishBleScanSession("stopped because Bluetooth permission was denied")
            clearReconnectScanState()
            val message = "Bluetooth permission was denied by Android."
            _state.update {
                it.copy(
                    connection = it.connection.copy(scanning = false, message = message),
                    lastMessage = message
                )
            }
        }
    }

    private fun beginBleScanSession(autoConnectKnownDevices: Boolean) {
        scanSessionId += 1
        scanStartedAtMs = SystemClock.elapsedRealtime()
        scanResultCountsByAddress.clear()
        devicesByAddress.clear()
        bleScanItemsByAddress.clear()
        bleScanSeenAtMsByAddress.clear()
        bleScanSeenAtMsByName.clear()
        val mode = if (autoConnectKnownDevices) "auto-reconnect" else "manual"
        lastScanSummary = "BLE scan[$scanSessionId] is running in $mode mode."
        logEvent(lastScanSummary)
    }

    private fun finishBleScanSession(reason: String) {
        val session = scanSessionId
        val startedAt = scanStartedAtMs
        val durationMs = if (startedAt > 0L) SystemClock.elapsedRealtime() - startedAt else 0L
        val advertisementCount = scanResultCountsByAddress.values.sum()
        val devices = bleScanItemsByAddress.values.toList()
        val deviceSummary = devices
            .sortedWith(
                compareByDescending<BleMidiDeviceItem> { it.standardBleMidi }
                    .thenByDescending { it.likelyMidi }
                    .thenByDescending { it.rssi ?: -999 }
                    .thenBy { it.name }
            )
            .take(10)
            .joinToString("; ") { device ->
                val count = scanResultCountsByAddress[device.address] ?: 0
                val midi = when {
                    device.standardBleMidi -> "standard-midi"
                    device.likelyMidi -> "likely-midi"
                    else -> "plain-ble"
                }
                "${device.name}/${device.address} rssi=${device.rssi ?: "?"} $midi ads=$count"
            }
            .ifBlank { "none" }
        val moreDevices = (devices.size - 10).coerceAtLeast(0)
        val suffix = if (moreDevices > 0) "; +$moreDevices more" else ""
        lastScanSummary = "BLE scan[$session] $reason after ${durationMs}ms: ${devices.size} devices, $advertisementCount advertisements; $deviceSummary$suffix"
        logEvent(lastScanSummary)
        scanStartedAtMs = 0L
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        stopBleScan(connectReconnectFallback = false)
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan(connectReconnectFallback: Boolean) {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        val shouldUseFallback = connectReconnectFallback && scanAutoConnectKnownDevices
        val fallbackAddress = scanFallbackReconnectAddress
        val callback = scanCallback ?: run {
            clearReconnectScanState()
            _state.update { it.copy(connection = it.connection.scanStopped()) }
            return
        }
        scanCallback = null
        runCatching {
            if (hasScanPermission()) bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(callback)
        }
        finishBleScanSession(if (connectReconnectFallback) "timed out" else "stopped")
        clearReconnectScanState()
        _state.update { it.copy(connection = it.connection.scanStopped()) }
        if (shouldUseFallback && fallbackAddress != null && !_state.value.connection.connected && !_state.value.connection.connecting) {
            val fallback = knownReconnectCandidates().firstOrNull { sameConnectionTarget(it.address, fallbackAddress) }
            postMessage("Preferred device not found. Reconnecting to ${fallback?.name ?: "known MIDI device"}...")
            mainHandler.post { connect(availableConnectionAddress(fallbackAddress) ?: fallbackAddress) }
        }
    }

    fun connect(address: String) {
        val resolvedAddress = availableConnectionAddress(address) ?: address
        val currentConnection = _state.value.connection
        if (
            (currentConnection.connected || currentConnection.connecting) &&
            sameConnectionTarget(currentConnection.address, resolvedAddress)
        ) {
            val name = currentConnection.deviceName ?: currentConnection.rememberedDeviceName ?: "that MIDI device"
            postMessage(if (currentConnection.connecting) "Already connecting to $name..." else "Already connected to $name.")
            return
        }
        clearManualDisconnectSuppression()
        if (isAndroidMidiConnectionId(resolvedAddress)) {
            connectAndroidMidiDevice(resolvedAddress)
            return
        }
        val device = devicesByAddress[resolvedAddress]
        if (device == null) {
            updateConnectionMessage("That BLE MIDI device is not in the current scan list. Scan again with the device powered on.")
            return
        }
        if (!hasConnectPermission()) {
            updateConnectionMessage("Bluetooth connect permission is required before connecting.")
            return
        }
        val directlyConnectable = isDirectBluetoothMidiConnectionAvailable(resolvedAddress)
        if (!isRecentlyScannedBleDevice(resolvedAddress) && !directlyConnectable) {
            val name = runCatching { displayNameForBluetoothDevice(resolvedAddress, device, null) }.getOrDefault("That BLE MIDI device")
            updateConnectionMessage("$name was not seen in the latest scan. Scan again with the device powered on.")
            return
        }
        val generation = connectionGeneration.incrementAndGet()
        stopBleScan()
        prepareForConnectionChange()
        val name = displayNameForBluetoothDevice(resolvedAddress, device, null)
        logEvent(
            "Connecting BLE MIDI name=$name address=$resolvedAddress known=${isKnownDeviceAddress(resolvedAddress)} " +
                "recent=${isRecentlyScannedBleDevice(resolvedAddress)} bonded=${isBondedBluetoothDevice(device)}"
        )
        _state.update {
            it.copy(
                connection = ConnectionUiState(
                    connected = false,
                    connecting = true,
                    scanning = false,
                    deviceName = name,
                    address = resolvedAddress,
                    rememberedDeviceName = rememberedDeviceName(),
                    rememberedDeviceAddress = rememberedDeviceAddress(),
                    autoReconnectSuppressed = manualDisconnectSuppressed(),
                    message = "Connecting to $name..."
                ),
                lastMessage = "Connecting to $name..."
            )
        }

        try {
            midiManager.openBluetoothDevice(device, { openedDevice ->
                if (!isCurrentConnectionAttempt(generation, resolvedAddress) || !isBluetoothEnabled()) {
                    runCatching { openedDevice?.close() }
                    return@openBluetoothDevice
                }
                if (openedDevice == null) {
                    logEvent("BLE MIDI open returned null for $name address=$resolvedAddress")
                    _state.update {
                        it.copy(
                            connection = rememberedConnection(
                                message = "Could not open $name as a BLE MIDI device. Power-cycle the adapter, keep it nearby, then scan or connect again."
                            ),
                            lastMessage = "Connection failed."
                        )
                    }
                    return@openBluetoothDevice
                }
                finishMidiConnection(openedDevice, name, resolvedAddress, generation)
            }, mainHandler)
        } catch (security: SecurityException) {
            if (isCurrentConnectionAttempt(generation, resolvedAddress)) {
                logEvent("BLE MIDI connect permission denied for $name address=$resolvedAddress")
                _state.update {
                    it.copy(
                        connection = rememberedConnection(message = "Bluetooth connect permission was denied by Android."),
                        lastMessage = "Connection permission denied."
                    )
                }
            }
        } catch (t: Throwable) {
            if (isCurrentConnectionAttempt(generation, resolvedAddress)) {
                logEvent("BLE MIDI connect failed for $name address=$resolvedAddress: ${t.message ?: t::class.java.simpleName}")
                _state.update {
                    it.copy(
                        connection = rememberedConnection(
                            message = "Android could not start the BLE MIDI connection. Power-cycle the adapter, keep it nearby, then scan or connect again."
                        ),
                        lastMessage = "Connection failed: ${t.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    private fun prepareForConnectionChange() {
        val wasActive = _state.value.playback.isActive
        connectionMonitorJob?.cancel()
        connectionVerificationJob?.cancel()
        connectionVerificationJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        playbackGeneration.incrementAndGet()
        playbackJob?.cancel()
        playbackJob = null
        skipRequest = 0
        seekRequestUs = -1L
        if (wasActive) sendPanicMessages()
        closeMidiConnection()
        if (wasActive) {
            _state.update { it.copy(playback = PlaybackUiState(), playbackChannels = emptyList()) }
            updatePlaybackSurfaces(updateNotification = true)
            stopForegroundIfNeeded()
            releaseWakeLock()
        }
    }

    fun disconnect() {
        connectionGeneration.incrementAndGet()
        connectionMonitorJob?.cancel()
        connectionVerificationJob?.cancel()
        connectionVerificationJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        suppressAutoReconnectAfterManualDisconnect()
        stopPlayback(userRequested = true)
        closeMidiConnection()
        _state.update {
            it.copy(
                connection = rememberedConnection(message = "Disconnected"),
                lastMessage = "Disconnected."
            )
        }
    }

    fun continueOffline() {
        connectionGeneration.incrementAndGet()
        reconnectJob?.cancel()
        reconnectJob = null
        stopBleScan()
        suppressAutoReconnectAfterManualDisconnect()
        _state.update {
            it.copy(
                connection = rememberedConnection(message = "Auto-reconnect paused."),
                lastMessage = "Continuing offline."
            )
        }
    }

    fun renameDevice(address: String, name: String) {
        val cleanName = cleanStoredDeviceName(name)
        if (cleanName.isBlank()) return
        val aliases = deviceAliases().toMutableMap()
        connectionTargetAliases(address).forEach { aliasAddress ->
            aliases[aliasAddress] = cleanName
        }
        val updatedKnownDevices = knownDevices().map { known ->
            if (sameConnectionTarget(known.address, address)) known.copy(name = cleanName) else known
        }
        val editor = prefs.edit()
            .putString(PREF_DEVICE_ALIASES, aliases.toSortedMap().entries.joinToString("\n") { "${it.key}\t${it.value}" })
            .putString(PREF_KNOWN_DEVICES, updatedKnownDevices.joinToString("\n") { "${it.address}\t${it.name}" })
        if (sameConnectionTarget(rememberedDeviceAddress(), address)) {
            editor.putString(PREF_LAST_DEVICE_NAME, cleanName)
        }
        editor.apply()
        _state.update {
            val connection = it.connection
            val updatedConnection = if (sameConnectionTarget(connection.address, address)) {
                connection.copy(
                    deviceName = cleanName,
                    rememberedDeviceName = rememberedDeviceName(),
                    rememberedDeviceAddress = rememberedDeviceAddress()
                )
            } else {
                connection.copy(
                    rememberedDeviceName = rememberedDeviceName(),
                    rememberedDeviceAddress = rememberedDeviceAddress()
                )
            }
            it.copy(
                bleDevices = mergedDeviceList(),
                connection = updatedConnection,
                lastMessage = "Renamed MIDI device to $cleanName."
            )
        }
    }

    fun addDevice(address: String) {
        val currentItem = mergedDeviceList().firstOrNull { sameConnectionTarget(it.address, address) }
        val cleanName = cleanStoredDeviceName(
            currentItem?.name ?: deviceAlias(address) ?: "BLE MIDI Adapter ${shortBluetoothIdentifier(address)}"
        ).ifBlank { "MIDI device" }
        val updatedKnownDevices = (listOf(KnownMidiDevice(address, cleanName)) + knownDevices().filterNot {
            sameConnectionTarget(it.address, address)
        }).take(8)
        val hidden = hiddenDevices() - connectionTargetAliases(address)
        prefs.edit()
            .putString(PREF_KNOWN_DEVICES, updatedKnownDevices.joinToString("\n") { "${it.address}\t${it.name}" })
            .putString(PREF_HIDDEN_DEVICES, hidden.sorted().joinToString("\n"))
            .putBoolean(PREF_MANUAL_DISCONNECT, false)
            .apply()
        _state.update {
            it.copy(
                bleDevices = mergedDeviceList(),
                connection = it.connection.copy(
                    rememberedDeviceName = rememberedDeviceName(),
                    rememberedDeviceAddress = rememberedDeviceAddress(),
                    autoReconnectSuppressed = manualDisconnectSuppressed()
                ),
                lastMessage = "Added $cleanName to APS NoteCast."
            )
        }
    }

    fun removeDevice(address: String) {
        val connection = _state.value.connection
        val name = _state.value.bleDevices.firstOrNull { it.address == address }?.name
            ?: (if (connection.address == address) connection.deviceName else null)
            ?: "MIDI device"
        val wasCurrentConnection = connection.address == address
        if (wasCurrentConnection) {
            connectionGeneration.incrementAndGet()
            connectionMonitorJob?.cancel()
            connectionVerificationJob?.cancel()
            connectionVerificationJob = null
            reconnectJob?.cancel()
            reconnectJob = null
            stopPlayback(userRequested = true)
            closeMidiConnection()
        }

        hideDevice(address)
        connectionTargetAliases(address).forEach { alias ->
            devicesByAddress.remove(alias)
            bleScanItemsByAddress.remove(alias)
            midiDevicesByConnectionId.remove(alias)
        }

        val remainingKnownDevices = knownDevices().filterNot { sameConnectionTarget(it.address, address) }
        val remainingAliases = deviceAliases().filterNot { (aliasAddress, _) ->
            sameConnectionTarget(aliasAddress, address)
        }
        val editor = prefs.edit()
            .putString(PREF_KNOWN_DEVICES, remainingKnownDevices.joinToString("\n") { "${it.address}\t${it.name}" })
            .putString(PREF_DEVICE_ALIASES, remainingAliases.toSortedMap().entries.joinToString("\n") { "${it.key}\t${it.value}" })
            .putBoolean(PREF_MANUAL_DISCONNECT, false)
        if (sameConnectionTarget(rememberedDeviceAddress(), address)) {
            editor.remove(PREF_LAST_DEVICE_ADDRESS)
                .remove(PREF_LAST_DEVICE_NAME)
        }
        editor.apply()

        _state.update {
            val updatedConnection = if (wasCurrentConnection) {
                rememberedConnection(message = "Removed $name from APS NoteCast.")
            } else {
                it.connection.copy(
                    rememberedDeviceName = rememberedDeviceName(),
                    rememberedDeviceAddress = rememberedDeviceAddress(),
                    autoReconnectSuppressed = manualDisconnectSuppressed()
                )
            }
            it.copy(
                bleDevices = mergedDeviceList(),
                connection = updatedConnection,
                lastMessage = "Removed $name from the device list."
            )
        }
    }

    fun forgetRememberedDevice() {
        disconnect()
        prefs.edit()
            .remove(PREF_LAST_DEVICE_ADDRESS)
            .remove(PREF_LAST_DEVICE_NAME)
            .remove(PREF_KNOWN_DEVICES)
            .remove(PREF_DEVICE_ALIASES)
            .remove(PREF_HIDDEN_DEVICES)
            .putBoolean(PREF_MANUAL_DISCONNECT, false)
            .apply()
        _state.update {
            it.copy(
                connection = it.connection.copy(
                    rememberedDeviceAddress = null,
                    rememberedDeviceName = null,
                    autoReconnectSuppressed = false,
                    message = "No preferred MIDI device"
                ),
                lastMessage = "Forgot preferred MIDI device."
            )
        }
    }

    fun connectionDiagnostics(): String {
        refreshKnownDevices()
        val connection = _state.value.connection
        val known = knownDevices()
        return buildString {
            appendLine("Bluetooth hardware: ${if (connection.bluetoothAvailable) "available" else "not reported"}")
            appendLine("Bluetooth: ${if (connection.bluetoothEnabled) "on" else "off"}")
            appendLine("Connected: ${connection.deviceName ?: "no"}")
            appendLine("Preferred: ${connection.rememberedDeviceName ?: "none"}")
            appendLine("Reconnect paused: ${if (connection.autoReconnectSuppressed) "yes" else "no"}")
            appendLine("Known saved devices: ${known.size}")
            known.forEach { device ->
                appendLine("- ${device.name} (${device.address})")
            }
            appendLine("Last BLE scan: $lastScanSummary")
            appendLine("Visible MIDI devices: ${_state.value.bleDevices.size}")
            _state.value.bleDevices.forEach { device ->
                appendLine("- ${device.name} (${device.source}, ${device.detail})")
            }
        }.trim()
    }

    private fun connectAndroidMidiDevice(connectionId: String) {
        clearManualDisconnectSuppression()
        val info = midiDevicesByConnectionId[connectionId]
        if (info == null) {
            postMessage("That Android MIDI device is no longer available. Scan again.")
            return
        }
        val name = displayNameForMidiDevice(info)
        if (!isAvailableAndroidMidiConnection(connectionId)) {
            postMessage("$name is not currently attached through Android Bluetooth. Use Scan + Connect, or pair/connect it in Android Bluetooth settings and return to APS NoteCast.")
            return
        }
        val generation = connectionGeneration.incrementAndGet()
        stopBleScan()
        prepareForConnectionChange()
        _state.update {
            it.copy(
                connection = ConnectionUiState(
                    connected = false,
                    connecting = true,
                    scanning = false,
                    bluetoothAvailable = it.connection.bluetoothAvailable,
                    bluetoothEnabled = it.connection.bluetoothEnabled,
                    deviceName = name,
                    address = connectionId,
                    rememberedDeviceName = rememberedDeviceName(),
                    rememberedDeviceAddress = rememberedDeviceAddress(),
                    autoReconnectSuppressed = manualDisconnectSuppressed(),
                    message = "Connecting to $name..."
                ),
                lastMessage = "Connecting to $name..."
            )
        }

        try {
            midiManager.openDevice(info, { openedDevice ->
                if (!isCurrentConnectionAttempt(generation, connectionId) || !isBluetoothEnabled()) {
                    runCatching { openedDevice?.close() }
                    return@openDevice
                }
                if (openedDevice == null) {
                    _state.update {
                        it.copy(
                            connection = rememberedConnection(message = "Could not open $name as a MIDI device."),
                            lastMessage = "Connection failed."
                        )
                    }
                    return@openDevice
                }
                finishMidiConnection(openedDevice, name, connectionId, generation)
            }, mainHandler)
        } catch (t: Throwable) {
            if (isCurrentConnectionAttempt(generation, connectionId)) {
                _state.update {
                    it.copy(
                        connection = rememberedConnection(message = "Android could not start the MIDI connection."),
                        lastMessage = "Connection failed: ${t.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    private fun finishMidiConnection(openedDevice: MidiDevice, name: String, connectionId: String, generation: Long) {
        if (!isCurrentConnectionAttempt(generation, connectionId)) {
            runCatching { openedDevice.close() }
            return
        }
        logEvent("Opened MIDI device name=$name connectionId=$connectionId info=${midiDeviceDebugSummary(openedDevice.info)}")
        val inputPort = runCatching {
            val inputPortInfo = openedDevice.info.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_INPUT }
            val portNumber = inputPortInfo?.portNumber ?: 0
            openedDevice.openInputPort(portNumber)
        }.getOrNull()
        if (inputPort == null) {
            logEvent("MIDI device $name opened without an available input port.")
            runCatching { openedDevice.close() }
            if (isCurrentConnectionAttempt(generation, connectionId)) {
                _state.update {
                    it.copy(
                        connection = rememberedConnection(message = "$name opened, but no MIDI input port was available."),
                        lastMessage = "No MIDI input port available."
                    )
                }
            }
            return
        }
        val outputPort = runCatching {
            val outputPortInfo = openedDevice.info.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
            outputPortInfo?.let { openedDevice.openOutputPort(it.portNumber) }
        }.getOrNull()
        if (!isCurrentConnectionAttempt(generation, connectionId)) {
            runCatching { inputPort.close() }
            runCatching { outputPort?.close() }
            runCatching { openedDevice.close() }
            return
        }
        midiDevice = openedDevice
        midiInputPort = inputPort
        midiOutputPort = outputPort
        connectedMidiDeviceInfo = openedDevice.info
        logEvent("MIDI ports for $name: input=open output=${if (outputPort == null) "unavailable" else "open"}")
        connectionVerificationJob?.cancel()
        connectionVerificationJob = null
        if (!isConnectionHealthy(connectionId)) {
            _state.update {
                it.copy(
                    connection = it.connection.copy(
                        connecting = true,
                        message = "Waiting for Android Bluetooth to attach to $name..."
                    ),
                    lastMessage = "Waiting for Android Bluetooth to attach to $name..."
                )
            }
            scheduleInitialConnectionVerification(name, connectionId, generation)
            return
        }
        if (isBluetoothMidiConnection(connectionId, openedDevice.info)) {
            _state.update {
                it.copy(
                    connection = it.connection.copy(
                        connecting = true,
                        message = "Finalizing BLE MIDI connection to $name..."
                    ),
                    lastMessage = "Finalizing BLE MIDI connection to $name..."
                )
            }
            scheduleInitialConnectionVerification(name, connectionId, generation, BLE_MIDI_READY_SETTLE_MS)
            return
        }
        markMidiConnectionConnected(name, connectionId)
    }

    private fun markMidiConnectionConnected(name: String, connectionId: String) {
        reconnectJob?.cancel()
        reconnectJob = null
        rememberConnectedDevice(connectionId, name)
        logEvent("Connected MIDI name=$name connectionId=$connectionId")
        _state.update {
            it.copy(
                connection = ConnectionUiState(
                    connected = true,
                    connecting = false,
                    scanning = false,
                    bluetoothAvailable = it.connection.bluetoothAvailable,
                    bluetoothEnabled = it.connection.bluetoothEnabled,
                    deviceName = name,
                    address = connectionId,
                    rememberedDeviceName = name,
                    rememberedDeviceAddress = connectionId,
                    autoReconnectSuppressed = false,
                    message = "Connected to $name"
                ),
                lastMessage = "Connected to $name."
            )
        }
        startConnectionMonitor(name, connectionId)
    }

    private fun scheduleInitialConnectionVerification(
        name: String,
        connectionId: String,
        generation: Long,
        delayMs: Long = CONNECTION_ATTACH_VERIFY_DELAY_MS
    ) {
        connectionVerificationJob?.cancel()
        connectionVerificationJob = serviceScope.launch {
            delay(delayMs)
            if (!isCurrentConnectionAttempt(generation, connectionId)) return@launch
            if (isConnectionHealthy(connectionId)) {
                markMidiConnectionConnected(name, connectionId)
                return@launch
            }
            logEvent("$name opened as MIDI, but Android did not report an attached Bluetooth MIDI link.")
            closeMidiConnection()
            _state.update {
                it.copy(
                    connection = rememberedConnection(message = "$name opened, but Android did not finish attaching the BLE MIDI link. Power-cycle the adapter, then scan or connect again."),
                    playback = PlaybackUiState(),
                    playbackChannels = emptyList(),
                    lastMessage = "$name BLE MIDI attach did not finish."
                )
            }
            updatePlaybackSurfaces(updateNotification = true)
            stopForegroundIfNeeded()
            releaseWakeLock()
        }
    }

    private fun startConnectionMonitor(name: String, connectionId: String) {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = serviceScope.launch(Dispatchers.Default) {
            var missedChecks = 0
            while (isActive) {
                delay(2_000L)
                val connection = _state.value.connection
                if (!connection.connected || connection.address != connectionId) return@launch
                val healthy = withContext(Dispatchers.Main) { isConnectionHealthy(connectionId) }
                if (healthy) {
                    missedChecks = 0
                } else {
                    missedChecks++
                    if (missedChecks >= 2) {
                        withContext(Dispatchers.Main) {
                            handleConnectionLost("$name disconnected.")
                        }
                        return@launch
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun isConnectionHealthy(connectionId: String): Boolean {
        refreshBluetoothState()
        if (!isBluetoothEnabled()) return false
        if (midiInputPort == null || midiDevice == null) return false
        refreshAndroidMidiDevices()
        val openedConnectionId = connectedMidiDeviceInfo?.let { midiConnectionId(it) }
        val midiDevicePresent = midiDevicesByConnectionId.keys.any { availableConnectionId ->
            sameConnectionTarget(availableConnectionId, connectionId) ||
                sameConnectionTarget(availableConnectionId, openedConnectionId)
        }
        return midiDevicePresent && isBluetoothMidiConnectionAttached(connectionId, connectedMidiDeviceInfo)
    }

    private fun isBluetoothMidiConnection(connectionId: String, info: MidiDeviceInfo?): Boolean {
        return info?.type == MidiDeviceInfo.TYPE_BLUETOOTH ||
            info?.let { midiBluetoothDevice(it) } != null ||
            bluetoothAddressFromConnectionId(connectionId) != null ||
            !isAndroidMidiConnectionId(connectionId)
    }

    @SuppressLint("MissingPermission")
    private fun isBluetoothMidiConnectionAttached(connectionId: String, info: MidiDeviceInfo?): Boolean {
        val device = bluetoothDeviceForMidiConnection(connectionId, info) ?: return true
        if (!hasConnectPermission()) return true
        return runCatching {
            bluetoothManager.getConnectionState(device, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED ||
                bluetoothManager.getConnectedDevices(BluetoothProfile.GATT).any { connectedDevice ->
                    bluetoothDevicesReferToSameDevice(device, connectedDevice)
                }
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothDevicesReferToSameDevice(left: BluetoothDevice, right: BluetoothDevice): Boolean {
        val leftAddress = runCatching { left.address }.getOrNull()
        val rightAddress = runCatching { right.address }.getOrNull()
        if (!leftAddress.isNullOrBlank() && leftAddress == rightAddress) return true
        val leftName = runCatching { left.name }.getOrNull()
        val rightName = runCatching { right.name }.getOrNull()
        return !leftName.isNullOrBlank() && leftName == rightName
    }

    private fun bluetoothDeviceForMidiConnection(connectionId: String, info: MidiDeviceInfo?): BluetoothDevice? {
        info?.let { midiBluetoothDevice(it) }?.let { return it }
        val bluetoothAddress = bluetoothAddressFromConnectionId(connectionId)
            ?: connectionId.takeUnless { isAndroidMidiConnectionId(it) }
        return bluetoothAddress?.let { devicesByAddress[it] }
    }

    @SuppressLint("MissingPermission")
    private fun noteBluetoothPairingRequest(intent: Intent) {
        val device = intent.bluetoothDeviceExtra() ?: return
        val address = runCatching { device.address }.getOrNull() ?: return
        val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
        val key = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, BluetoothDevice.ERROR)
        val name = runCatching { displayNameForBluetoothDevice(address, device, null) }.getOrDefault("Bluetooth device")
        logEvent("Bluetooth pairing request for $name address=$address variant=${pairingVariantName(variant)} key=${if (key == BluetoothDevice.ERROR) "none" else "present"}")
        notePairingForCurrentMidiLink(address, name, "pairing request")
    }

    @SuppressLint("MissingPermission")
    private fun noteBluetoothBondStateChanged(intent: Intent) {
        val device = intent.bluetoothDeviceExtra() ?: return
        val address = runCatching { device.address }.getOrNull() ?: return
        val previousState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
        val newState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
        val name = runCatching { displayNameForBluetoothDevice(address, device, null) }.getOrDefault("Bluetooth device")
        logEvent("Bluetooth bond state for $name address=$address: ${bondStateName(previousState)} -> ${bondStateName(newState)}")
        if (newState == BluetoothDevice.BOND_BONDING) notePairingForCurrentMidiLink(address, name, "bonding state")
        if (!currentConnectionMatchesBluetoothDevice(address)) return
        val connection = _state.value.connection
        if (!connection.connected && !connection.connecting) return
        val message = when (newState) {
            BluetoothDevice.BOND_BONDING -> "Android requested Bluetooth pairing for $name. Pairing is okay; APS NoteCast will keep using the BLE MIDI connection."
            BluetoothDevice.BOND_BONDED -> "Connected to $name with Android Bluetooth pairing."
            BluetoothDevice.BOND_NONE -> if (previousState == BluetoothDevice.BOND_BONDING && connection.connected) {
                "Connected to $name. Bluetooth pairing was not completed."
            } else {
                null
            }
            else -> null
        } ?: return
        _state.update {
            it.copy(
                connection = it.connection.copy(message = message),
                lastMessage = message
            )
        }
    }

    private fun notePairingForCurrentMidiLink(
        address: String,
        name: String,
        source: String
    ) {
        if (!currentConnectionMatchesBluetoothDevice(address)) return
        val connection = _state.value.connection
        if (!connection.connected && !connection.connecting) return
        logEvent(
            "Android Bluetooth pairing requested for active BLE MIDI link name=$name address=$address source=$source"
        )
        val message = "Android requested Bluetooth pairing for $name. Pairing is okay; APS NoteCast will keep using the BLE MIDI connection."
        _state.update {
            it.copy(
                connection = it.connection.copy(message = message),
                lastMessage = message
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun noteBluetoothDeviceDisconnected(device: BluetoothDevice?) {
        if (device == null) return
        val address = runCatching { device.address }.getOrNull() ?: return
        val connection = _state.value.connection
        if (!connection.connected && !connection.connecting) return
        if (!currentConnectionMatchesBluetoothDevice(address)) return
        val name = connection.deviceName ?: runCatching { safeDeviceName(device) }.getOrDefault("MIDI device")
        AppEventLog.append("$name reported a Bluetooth-layer disconnect; waiting for MIDI I/O confirmation.")
        scheduleConnectionVerification("$name disconnected.")
    }

    private fun scheduleConnectionVerification(message: String) {
        val connection = _state.value.connection
        val address = connection.address ?: return
        if (!connection.connected && !connection.connecting) return
        val generation = connectionGeneration.get()
        connectionVerificationJob?.cancel()
        connectionVerificationJob = serviceScope.launch {
            delay(1_200L)
            if (connectionGeneration.get() != generation) return@launch
            val current = _state.value.connection
            if ((!current.connected && !current.connecting) || current.address != address) return@launch
            if (!isConnectionHealthy(address)) handleConnectionLost(message)
        }
    }

    @SuppressLint("MissingPermission")
    private fun currentConnectionMatchesBluetoothDevice(address: String): Boolean {
        val connectionAddress = _state.value.connection.address ?: return false
        if (connectionAddress == address) return true
        if (connectionAddress == androidBluetoothMidiConnectionId(address)) return true
        if (!isAndroidMidiConnectionId(connectionAddress)) return false
        val bluetoothDevice = midiDevicesByConnectionId[connectionAddress]?.let { midiBluetoothDevice(it) }
            ?: midiDevice?.info?.let { midiBluetoothDevice(it) }
        return runCatching { bluetoothDevice?.address == address }.getOrDefault(false)
    }

    private fun handleBluetoothTurnedOff() {
        AppEventLog.append("Bluetooth turned off.")
        connectionGeneration.incrementAndGet()
        stopBleScan()
        if (_state.value.connection.connected || _state.value.connection.connecting) {
            handleConnectionLost("Bluetooth was turned off.")
        } else {
            _state.update {
                it.copy(
                    connection = it.connection.copy(
                        bluetoothEnabled = false,
                        scanning = false,
                        connecting = false,
                        message = "Bluetooth is off"
                    ),
                    lastMessage = "Bluetooth is off."
                )
            }
        }
    }

    private fun handleConnectionLost(message: String) {
        val connection = _state.value.connection
        if (!connection.connected && !connection.connecting) return
        val deviceName = connection.deviceName ?: connection.rememberedDeviceName ?: "MIDI device"
        logEvent("Connection lost for $deviceName address=${connection.address ?: "unknown"}: $message")
        connectionMonitorJob?.cancel()
        connectionVerificationJob?.cancel()
        connectionVerificationJob = null
        connectionGeneration.incrementAndGet()
        playbackGeneration.incrementAndGet()
        playbackJob?.cancel()
        playbackJob = null
        skipRequest = 0
        seekRequestUs = -1L
        closeMidiConnection()
        val timeoutSeconds = appSettings.reconnectTimeoutSeconds.coerceIn(10, 180)
        _state.update {
            it.copy(
                connection = rememberedConnection(message = "$message Reconnecting for ${timeoutSeconds}s..."),
                playback = PlaybackUiState(),
                playbackChannels = emptyList(),
                lastMessage = "$deviceName connection lost."
            )
        }
        updatePlaybackSurfaces(updateNotification = true)
        stopForegroundIfNeeded()
        releaseWakeLock()
        if (appSettings.autoReconnectEnabled && !manualDisconnectSuppressed()) {
            startTimedReconnect(deviceName, timeoutSeconds)
        }
    }

    private fun startTimedReconnect(deviceName: String, timeoutSeconds: Int) {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            val deadline = System.currentTimeMillis() + timeoutSeconds.coerceIn(10, 180) * 1_000L
            while (isActive && System.currentTimeMillis() < deadline) {
                if (_state.value.connection.connected) return@launch
                autoReconnectIfPossible()
                delay(appSettings.reconnectIntervalSeconds.coerceIn(3, 60) * 1_000L)
            }
            if (!_state.value.connection.connected) {
                stopBleScan()
                _state.update {
                    it.copy(
                        connection = rememberedConnection(message = "Could not reconnect to $deviceName."),
                        lastMessage = "Reconnect timed out."
                    )
                }
            }
        }
    }

    fun playFile(itemId: String) {
        val item = _state.value.files.firstOrNull { it.id == itemId }
        if (item == null) {
            postMessage("Could not find that MIDI file.")
            return
        }
        playItems(listOf(item), playlistName = null)
    }

    fun playPlaylist(playlistId: String, shuffled: Boolean? = null) {
        val snapshot = _state.value
        val playlist = snapshot.playlists.firstOrNull { it.id == playlistId }
        if (playlist == null) {
            postMessage("Could not find that playlist.")
            return
        }
        val byId = snapshot.files.associateBy { it.id }
        val items = playlist.itemIds.mapNotNull { byId[it] }
        if (items.isEmpty()) {
            postMessage("That playlist is empty.")
            return
        }
        val shouldShuffle = shuffled ?: appSettings.shufflePlaylistsByDefault
        playItems(if (shouldShuffle) items.shuffled() else items, playlist.name)
    }

    fun pausePlayback() {
        if (!_state.value.playback.isPlaying) return
        _state.update {
            it.copy(
                playback = it.playback.copy(mode = PlaybackMode.Paused),
                lastMessage = "Playback paused."
            )
        }
        updatePlaybackSurfaces(updateNotification = true)
        serviceScope.launch(Dispatchers.IO) { sendPanicMessages() }
    }

    fun resumePlayback() {
        if (!_state.value.playback.isPaused) return
        _state.update {
            it.copy(
                playback = it.playback.copy(mode = PlaybackMode.Playing),
                lastMessage = "Playback resumed."
            )
        }
        updatePlaybackSurfaces(updateNotification = true)
    }

    fun seekPlayback(progressUs: Long) {
        val playback = _state.value.playback
        if (!playback.isActive || playback.durationUs <= 0L) return
        val cleanProgressUs = progressUs.coerceIn(0L, playback.durationUs)
        seekRequestUs = cleanProgressUs
        _state.update {
            it.copy(
                playback = it.playback.copy(progressUs = cleanProgressUs),
                lastMessage = "Seeking to ${cleanProgressUs.formatClockTime()}."
            )
        }
        updatePlaybackSurfaces(updateNotification = true)
    }

    fun skipToNext() {
        val playback = _state.value.playback
        if (!playback.isActive || playback.isPreparing) return
        seekRequestUs = -1L
        skipRequest = 1
        _state.update {
            it.copy(
                playback = it.playback.copy(mode = PlaybackMode.Playing),
                lastMessage = "Skipping to next track."
            )
        }
        updatePlaybackSurfaces(updateNotification = true)
        serviceScope.launch(Dispatchers.IO) { sendPanicMessages() }
    }

    fun skipToPrevious() {
        val playback = _state.value.playback
        if (!playback.isActive || playback.isPreparing) return
        seekRequestUs = -1L
        skipRequest = -1
        _state.update {
            it.copy(
                playback = it.playback.copy(mode = PlaybackMode.Playing),
                lastMessage = "Going back one track."
            )
        }
        updatePlaybackSurfaces(updateNotification = true)
        serviceScope.launch(Dispatchers.IO) { sendPanicMessages() }
    }

    private fun togglePlaybackFromMediaControls() {
        val playback = _state.value.playback
        when {
            playback.isPlaying -> pausePlayback()
            playback.isPaused -> resumePlayback()
            playback.isPreparing -> stopPlayback()
        }
    }

    fun setVolume(percent: Int) {
        val cleanPercent = percent.coerceIn(0, 100)
        _state.update { it.copy(volumePercent = cleanPercent) }
        serviceScope.launch(Dispatchers.IO) { sendVolumeMessages(cleanPercent) }
    }

    fun startRecording(title: String) {
        if (!_state.value.connection.connected || midiOutputPort == null) {
            postMessage("Connect to a BLE MIDI device with an output port before recording.")
            return
        }
        if (_state.value.recording.isRecording || recordingCountdownJob?.isActive == true) return
        val cleanTitle = title.trim().ifBlank { "APS NoteCast Recording" }
        val countdownSeconds = appSettings.recordingCountdownSeconds.coerceIn(0, 8)
        if (countdownSeconds > 0) {
            recordingCountdownJob?.cancel()
            recordingCountdownJob = serviceScope.launch {
                for (remaining in countdownSeconds downTo 1) {
                    _state.update {
                        it.copy(
                            recording = RecordingUiState(
                                isCountingDown = true,
                                title = cleanTitle,
                                message = if (appSettings.recordingMetronomeEnabled) {
                                    "Count-in $remaining..."
                                } else {
                                    "Recording starts in $remaining..."
                                }
                            ),
                            lastMessage = "Recording starts in $remaining."
                        )
                    }
                    delay(1_000L)
                }
                beginRecording(cleanTitle)
            }
            return
        }
        beginRecording(cleanTitle)
    }

    private fun beginRecording(cleanTitle: String) {
        val outputPort = midiOutputPort ?: return
        synchronized(recordingLock) {
            recordingEvents.clear()
            recordingStartedNs = if (appSettings.autoTrimRecordingSilence) 0L else System.nanoTime()
        }
        val receiver = object : MidiReceiver() {
            override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                recordIncomingMidi(msg, offset, count, timestamp)
            }
        }
        runCatching {
            outputPort.connect(receiver)
            recordingReceiver = receiver
            _state.update {
                it.copy(
                    recording = RecordingUiState(
                        isRecording = true,
                        isCountingDown = false,
                        title = cleanTitle,
                        message = "Waiting for BLE MIDI input..."
                    ),
                    lastMessage = "Recording $cleanTitle."
                )
            }
        }.onFailure {
            _state.update { state ->
                state.copy(
                    recording = RecordingUiState(message = "Could not listen to BLE MIDI input."),
                    lastMessage = "Recording could not start."
                )
            }
        }
    }

    fun finishRecording(title: String) {
        val current = _state.value.recording
        if (!current.isRecording) return
        stopRecordingReceiver()
        val cleanTitle = title.trim().ifBlank { current.title.ifBlank { "APS NoteCast Recording" } }
        val events = synchronized(recordingLock) { recordingEvents.toList() }
        if (events.isEmpty()) {
            synchronized(recordingLock) {
                recordingEvents.clear()
                recordingStartedNs = 0L
            }
            _state.update {
                it.copy(
                    recording = RecordingUiState(message = "Recording discarded because no MIDI input arrived."),
                    lastMessage = "No MIDI input was recorded."
                )
            }
            return
        }

        _state.update {
            it.copy(recording = current.copy(isRecording = false, isSaving = true, title = cleanTitle, message = "Saving recording..."))
        }
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val bytes = MidiFileWriter.write(cleanTitle, events)
                val item = repository.saveRecordedMidi(cleanTitle, bytes)
                appSettings.recordingTargetPlaylistId
                    ?.takeIf { playlistId -> repository.load().playlists.any { it.id == playlistId } }
                    ?.let { playlistId -> repository.addToPlaylist(playlistId, item.id) }
                item
            }.onSuccess {
                synchronized(recordingLock) {
                    recordingEvents.clear()
                    recordingStartedNs = 0L
                }
                withContext(Dispatchers.Main) {
                    reloadLibrary("Saved recording ${it.title}.")
                    _state.update { state -> state.copy(recording = RecordingUiState(message = "Saved ${it.title}.")) }
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            recording = RecordingUiState(message = "Could not save recording: ${error.message ?: "unknown error"}"),
                            lastMessage = "Recording save failed."
                        )
                    }
                }
            }
        }
    }

    fun cancelRecording() {
        recordingCountdownJob?.cancel()
        recordingCountdownJob = null
        stopRecordingReceiver()
        synchronized(recordingLock) {
            recordingEvents.clear()
            recordingStartedNs = 0L
        }
        _state.update {
            it.copy(
                recording = RecordingUiState(message = "Recording cancelled."),
                lastMessage = "Recording cancelled."
            )
        }
    }

    fun stopPlayback(userRequested: Boolean = true) {
        val generation = playbackGeneration.incrementAndGet()
        val wasActive = _state.value.playback.isActive
        playbackJob?.cancel()
        playbackJob = null
        skipRequest = 0
        seekRequestUs = -1L
        serviceScope.launch(Dispatchers.IO) {
            if (wasActive) sendPanicMessages()
            withContext(Dispatchers.Main) {
                if (!isCurrentPlayback(generation)) return@withContext
                _state.update {
                    it.copy(
                        playback = PlaybackUiState(),
                        playbackChannels = emptyList(),
                        lastMessage = if (userRequested) "Playback stopped." else it.lastMessage
                    )
                }
                updatePlaybackSurfaces(updateNotification = true)
                stopForegroundIfNeeded()
                releaseWakeLock()
            }
        }
    }

    fun panic() {
        val generation = playbackGeneration.incrementAndGet()
        playbackJob?.cancel()
        playbackJob = null
        skipRequest = 0
        seekRequestUs = -1L
        serviceScope.launch(Dispatchers.IO) {
            repeat(2) {
                sendPanicMessages()
                delay(120)
            }
            withContext(Dispatchers.Main) {
                if (!isCurrentPlayback(generation)) return@withContext
                _state.update {
                    it.copy(
                        playback = PlaybackUiState(),
                        playbackChannels = emptyList(),
                        lastMessage = "Panic sent: sustain off, all notes off, all sound off."
                    )
                }
                updatePlaybackSurfaces(updateNotification = true)
                stopForegroundIfNeeded()
                releaseWakeLock()
            }
        }
    }

    private fun playItems(items: List<MidiLibraryItem>, playlistName: String?) {
        if (midiInputPort == null || !_state.value.connection.connected) {
            postMessage("Connect to a BLE MIDI device before playing.")
            return
        }
        if (items.isEmpty()) {
            postMessage("There is nothing to play.")
            return
        }
        val generation = playbackGeneration.incrementAndGet()
        val replacingActivePlayback = _state.value.playback.isActive || playbackJob?.isActive == true
        val firstItem = items.first()
        skipRequest = 0
        seekRequestUs = -1L
        playbackJob?.cancel()
        _state.update {
            it.copy(
                playback = PlaybackUiState(
                    mode = PlaybackMode.Preparing,
                    currentTitle = firstItem.title,
                    currentItemId = firstItem.id,
                    playlistName = playlistName,
                    currentTrackNumber = 1,
                    totalTracks = items.size,
                    durationUs = firstItem.durationUs
                ),
                playbackChannels = emptyList(),
                lastMessage = if (replacingActivePlayback) "Switching to ${firstItem.title}..." else "Preparing ${firstItem.title}..."
            )
        }
        updatePlaybackSurfaces(updateNotification = true)
        playbackJob = serviceScope.launch(Dispatchers.Default) {
            if (replacingActivePlayback) {
                withContext(Dispatchers.IO) { sendPanicMessages() }
                delay(120)
            }
            runPlayback(items, playlistName, generation)
        }
    }

    private suspend fun runPlayback(items: List<MidiLibraryItem>, playlistName: String?, generation: Long) {
        var completedNormally = false
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            withContext(Dispatchers.Main) {
                acquireWakeLock()
                startForegroundForPlayback(items.first().title)
            }
            var index = 0
            while (index in items.indices) {
                coroutineContext.ensureActive()
                val item = items[index]
                val settingsSnapshot = appSettings
                withContext(Dispatchers.Main) {
                    if (!isCurrentPlayback(generation)) return@withContext
                    _state.update {
                        it.copy(
                            playback = PlaybackUiState(
                                mode = PlaybackMode.Preparing,
                                currentTitle = item.title,
                                currentItemId = item.id,
                                playlistName = playlistName,
                                currentTrackNumber = index + 1,
                                totalTracks = items.size,
                                progressUs = 0L,
                                durationUs = item.durationUs
                            ),
                            playbackChannels = emptyList(),
                            lastMessage = "Preparing ${item.title}..."
                        )
                    }
                    updatePlaybackSurfaces(updateNotification = true)
                }
                val playbackData = loadPreparedSequence(item, settingsSnapshot)
                val sequence = playbackData.sequence
                coroutineContext.ensureActive()
                withContext(Dispatchers.Main) {
                    if (!isCurrentPlayback(generation)) return@withContext
                    _state.update {
                        it.copy(
                            playback = PlaybackUiState(
                                mode = PlaybackMode.Playing,
                                currentTitle = item.title,
                                currentItemId = item.id,
                                playlistName = playlistName,
                                currentTrackNumber = index + 1,
                                totalTracks = items.size,
                                progressUs = 0L,
                                durationUs = sequence.durationUs
                            ),
                            playbackChannels = playbackData.channels,
                            lastMessage = "Playing ${item.title}"
                        )
                    }
                    updatePlaybackSurfaces(updateNotification = true)
                }
                sendVolumeMessages(_state.value.volumePercent)
                try {
                    playSequence(sequence, item)
                    sendPanicMessages()
                    delay(250)
                    nextTrackIndex(index, items.size, playlistName != null, settingsSnapshot)?.let { next ->
                        index = next
                    } ?: break
                } catch (skip: TrackSkipRequest) {
                    sendPanicMessages()
                    delay(160)
                    index = if (skip.direction > 0) index + 1 else (index - 1).coerceAtLeast(0)
                }
            }
            completedNormally = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (io: IOException) {
            if (isCurrentPlayback(generation)) {
                withContext(Dispatchers.Main) {
                    handleConnectionLost("MIDI connection was lost.")
                }
            }
        } catch (t: Throwable) {
            if (isCurrentPlayback(generation)) {
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            playback = PlaybackUiState(mode = PlaybackMode.Error, error = t.message ?: "Playback failed"),
                            playbackChannels = emptyList(),
                            lastMessage = "Playback error: ${t.message ?: "unknown error"}"
                        )
                    }
                    updatePlaybackSurfaces(updateNotification = true)
                }
                sendPanicMessages()
            }
        } finally {
            if (isCurrentPlayback(generation)) sendPanicMessages()
            withContext(Dispatchers.Main) {
                if (!isCurrentPlayback(generation)) return@withContext
                if (completedNormally) {
                    _state.update {
                        it.copy(
                            playback = PlaybackUiState(),
                            playbackChannels = emptyList(),
                            lastMessage = if (playlistName == null) "Playback finished." else "Playlist finished."
                        )
                    }
                    updatePlaybackSurfaces(updateNotification = true)
                }
                stopForegroundIfNeeded()
                releaseWakeLock()
                playbackJob = null
            }
        }
    }

    private class TrackSkipRequest(val direction: Int) : RuntimeException()

    private fun isCurrentPlayback(generation: Long): Boolean =
        playbackGeneration.get() == generation

    private suspend fun loadPreparedSequence(
        item: MidiLibraryItem,
        settings: AppSettings
    ): PreparedPlaybackData = withContext(Dispatchers.IO) {
        val file = repository.fileFor(item)
        val key = PreparedSequenceCacheKey(
            itemId = item.id,
            storedFileName = item.storedFileName,
            importedAtMs = item.importedAtMs,
            fileLength = file.length(),
            fileModifiedAtMs = file.lastModified(),
            tempoPercent = settings.tempoPercent.coerceIn(50, 150),
            transposeSemitones = settings.transposeSemitones,
            excludeDrumChannelFromTranspose = settings.excludeDrumChannelFromTranspose,
            channelSignature = settings.channelControls.cacheSignature()
        )
        synchronized(sequenceCacheLock) { preparedSequenceCache[key] }?.let { cached ->
            return@withContext cached
        }

        val parsed = MidiFileParser.parse(file.readBytes(), item.title)
        val prepared = prepareSequence(parsed, settings)
        val playbackData = PreparedPlaybackData(
            sequence = prepared,
            channels = parsed.channelNumbers()
        )
        synchronized(sequenceCacheLock) {
            preparedSequenceCache[key] = playbackData
        }
        playbackData
    }

    private fun nextTrackIndex(
        currentIndex: Int,
        totalTracks: Int,
        isPlaylist: Boolean,
        settings: AppSettings
    ): Int? {
        if (settings.repeatMode == RepeatMode.One) return currentIndex
        if (settings.playbackAdvanceMode == PlaybackAdvanceMode.StopAfterCurrent) return null
        val next = currentIndex + 1
        if (next < totalTracks) return next
        if (isPlaylist && settings.repeatMode == RepeatMode.Playlist && totalTracks > 0) return 0
        return null
    }

    private fun prepareSequence(
        sequence: MidiFileParser.MidiSequence,
        settings: AppSettings
    ): MidiFileParser.MidiSequence {
        val tempoPercent = settings.tempoPercent.coerceIn(50, 150)
        val adjustedEvents = sequence.events.mapNotNull { event ->
            transformMidiData(event.data, settings)?.let { data ->
                event.copy(
                    timeUs = scaleForTempo(event.timeUs, tempoPercent),
                    data = data
                )
            }
        }
        return sequence.copy(
            durationUs = scaleForTempo(sequence.durationUs, tempoPercent),
            events = adjustedEvents
        )
    }

    private fun scaleForTempo(timeUs: Long, tempoPercent: Int): Long =
        ((timeUs.coerceAtLeast(0L) * 100L) / tempoPercent.coerceIn(50, 150)).coerceAtLeast(0L)

    private fun transformMidiData(data: ByteArray, settings: AppSettings): ByteArray? {
        if (data.isEmpty()) return null
        val status = data[0].toInt() and 0xFF
        if (status !in 0x80..0xEF) return data
        val channelNumber = (status and 0x0F) + 1

        val copy = data.copyOf()
        val messageType = status and 0xF0
        if (
            settings.transposeSemitones != 0 &&
            (messageType == 0x80 || messageType == 0x90) &&
            copy.size > 1 &&
            !(settings.excludeDrumChannelFromTranspose && channelNumber == 10)
        ) {
            copy[1] = (copy[1].toInt() and 0xFF)
                .plus(settings.transposeSemitones)
                .coerceIn(0, 127)
                .toByte()
        }

        return copy
    }

    private fun applyLiveVolume(data: ByteArray): ByteArray? {
        if (data.isEmpty()) return null
        val status = data[0].toInt() and 0xFF
        if (status !in 0x80..0xEF) return data
        val channelNumber = (status and 0x0F) + 1
        val control = appSettings.channelControls.channelControl(channelNumber)
        val soloActive = appSettings.channelControls.any { it.solo }
        if (control.muted || (soloActive && !control.solo)) return null

        val messageType = status and 0xF0
        if (messageType != 0x90 && messageType != 0xB0) return data
        val masterPercent = _state.value.volumePercent

        if (!appSettings.appControlsVolume && messageType == 0xB0 && data.size > 2 && (data[1].toInt() and 0xFF) == 7) {
            val copy = data.copyOf()
            copy[2] = scaledMidiVolume(copy[2].toInt() and 0xFF, masterPercent, control.volumePercent).toByte()
            return copy
        }
        if (
            appSettings.appControlsVolume &&
            messageType == 0x90 &&
            data.size > 2 &&
            (data[2].toInt() and 0xFF) > 0
        ) {
            val scaled = scaledMidiVelocity(data[2].toInt() and 0xFF, masterPercent, control.volumePercent)
            if (scaled <= 0) return null
            val copy = data.copyOf()
            copy[2] = scaled.toByte()
            return copy
        }
        return data
    }

    private suspend fun playSequence(sequence: MidiFileParser.MidiSequence, item: MidiLibraryItem) {
        var startNs = System.nanoTime() + START_DELAY_NS
        var lastProgressPostNs = 0L
        var pauseOffsetNs = 0L
        var pauseStartedNs = 0L
        var eventIndex = 0

        suspend fun applySeek(progressUs: Long) {
            val cleanProgressUs = progressUs.coerceIn(0L, sequence.durationUs)
            sendPanicMessages()
            eventIndex = sequence.events.indexOfFirst { it.timeUs >= cleanProgressUs }.let { index ->
                if (index < 0) sequence.events.size else index
            }
            startNs = System.nanoTime() + START_DELAY_NS - cleanProgressUs * 1_000L
            pauseOffsetNs = 0L
            pauseStartedNs = 0L
            lastProgressPostNs = 0L
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        playback = it.playback.copy(
                            progressUs = cleanProgressUs,
                            durationUs = sequence.durationUs,
                            currentItemId = item.id
                        )
                    )
                }
                updatePlaybackSurfaces(updateNotification = true)
            }
        }

        playback@ while (true) {
            loop@ while (eventIndex < sequence.events.size) {
                coroutineContext.ensureActive()
                consumeSeekRequest(sequence.durationUs)?.let {
                    applySeek(it)
                    continue@loop
                }
                val event = sequence.events[eventIndex]
                while (true) {
                    coroutineContext.ensureActive()
                    consumeSkipRequest()?.let { throw TrackSkipRequest(it) }
                    consumeSeekRequest(sequence.durationUs)?.let {
                        applySeek(it)
                        continue@loop
                    }
                    if (_state.value.playback.isPaused) {
                        if (pauseStartedNs == 0L) pauseStartedNs = System.nanoTime()
                        delay(40)
                        continue
                    }
                    if (pauseStartedNs != 0L) {
                        pauseOffsetNs += System.nanoTime() - pauseStartedNs
                        pauseStartedNs = 0L
                    }
                    val targetNs = startNs + event.timeUs * 1_000L + pauseOffsetNs
                    val sleepNs = targetNs - SCHEDULE_AHEAD_NS - System.nanoTime()
                    if (sleepNs <= 0L) break
                    delay(min(20L, (sleepNs / 1_000_000L).coerceAtLeast(1L)))
                }
                consumeSkipRequest()?.let { throw TrackSkipRequest(it) }
                consumeSeekRequest(sequence.durationUs)?.let {
                    applySeek(it)
                    continue@loop
                }
                val targetNs = startNs + event.timeUs * 1_000L + pauseOffsetNs
                val port = midiInputPort ?: throw IOException("MIDI connection was lost")
                val data = applyLiveVolume(event.data)
                if (data != null) {
                    port.send(data, 0, data.size, targetNs)
                }

                val nowNs = System.nanoTime()
                if (nowNs - lastProgressPostNs > PROGRESS_POST_INTERVAL_NS) {
                    lastProgressPostNs = nowNs
                    val progress = event.timeUs.coerceAtMost(sequence.durationUs)
                    withContext(Dispatchers.Main) {
                        _state.update {
                            it.copy(playback = it.playback.copy(progressUs = progress, durationUs = sequence.durationUs, currentItemId = item.id))
                        }
                        updatePlaybackSurfaces(updateNotification = false)
                    }
                }
                eventIndex++
            }

            val finishNs = startNs + sequence.durationUs * 1_000L + pauseOffsetNs + 400_000_000L
            while (System.nanoTime() < finishNs && coroutineContext.isActive) {
                consumeSkipRequest()?.let { throw TrackSkipRequest(it) }
                consumeSeekRequest(sequence.durationUs)?.let {
                    applySeek(it)
                    if (eventIndex < sequence.events.size) continue@playback
                }
                delay(min(50L, ((finishNs - System.nanoTime()) / 1_000_000L).coerceAtLeast(1L)))
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(playback = it.playback.copy(progressUs = sequence.durationUs)) }
                    updatePlaybackSurfaces(updateNotification = false)
                }
            }
            break
        }
    }

    private fun stopPlaybackIfUsing(itemId: String) {
        if (_state.value.playback.currentItemId == itemId && _state.value.playback.isActive) stopPlayback(userRequested = true)
    }

    private data class KuhmannSearchResponse(val count: Int, val results: List<KuhmannMidiResult>)

    private fun fetchKuhmannSearchResults(query: String, format: Int?, pianoOnly: Boolean, channel: Int?, limit: Int): KuhmannSearchResponse {
        val requestLimit = if (pianoOnly) KUHMANN_SEARCH_MAX_LIMIT else limit
        val params = buildList {
            if (query.isNotBlank()) add("q=${query.urlEncoded()}")
            if (format != null) add("format=$format")
            if (channel != null) add("channel=$channel")
            add("limit=$requestLimit")
        }
        val response = httpGetText(URL("$KUHMANN_SEARCH_URL?${params.joinToString("&")}"))
        val root = JSONObject(response)
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("error", "Search request was not accepted."))
        }
        val resultsArray = root.optJSONArray("results")
        val results = buildList {
            if (resultsArray == null) return@buildList
            for (index in 0 until resultsArray.length()) {
                val obj = resultsArray.optJSONObject(index) ?: continue
                val id = obj.optInt("id", -1)
                val url = obj.optString("url")
                val title = obj.optString("title", obj.optString("filename", "Kuhmann MIDI"))
                if (id < 0 || url.isBlank()) continue
                val channelsArray = obj.optJSONArray("channels")
                val channels = buildList {
                    if (channelsArray != null) {
                        for (channelIndex in 0 until channelsArray.length()) {
                            val value = channelsArray.optInt(channelIndex, -1)
                            if (value in 1..16) add(value)
                        }
                    }
                }
                add(
                    KuhmannMidiResult(
                        id = id,
                        title = title,
                        folder = obj.optString("folder"),
                        filename = obj.optString("filename", "$title.mid"),
                        url = url,
                        midiType = obj.optString("midi_type"),
                        midiFormat = if (obj.has("midi_format") && !obj.isNull("midi_format")) obj.optInt("midi_format") else null,
                        channelCount = obj.optInt("channel_count", channels.size),
                        channels = channels,
                        fileSize = obj.optLong("file_size", 0L),
                        sha256 = obj.optString("sha256")
                    )
                )
            }
        }
        val filteredResults = if (pianoOnly) {
            results.filter { it.isPianoOnlyCandidate() }.take(limit)
        } else {
            results
        }
        val count = if (pianoOnly) filteredResults.size else root.optInt("count", filteredResults.size)
        return KuhmannSearchResponse(count = count, results = filteredResults)
    }

    private fun KuhmannMidiResult.isPianoOnlyCandidate(): Boolean {
        val cleanChannels = channels.distinct().sorted()
        val reportedCount = if (channelCount > 0) channelCount else cleanChannels.size
        return reportedCount <= 3 && (cleanChannels.isEmpty() || cleanChannels.all { it in 1..3 })
    }

    private fun downloadKuhmannBytes(result: KuhmannMidiResult): ByteArray {
        val url = URL(result.url)
        if (url.protocol != "https" || !url.host.endsWith("alexanderpeppe.com")) {
            throw IOException("Unexpected download host.")
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "audio/midi, audio/x-midi, application/octet-stream")
            setRequestProperty("User-Agent", "APS NoteCast/${_state.value.kuhmann.query.ifBlank { "kuhmann" }}")
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("HTTP $status")
            val contentLength = connection.contentLengthLong
            if (contentLength > KUHMANN_MAX_DOWNLOAD_BYTES) throw IOException("File is too large.")
            val bytes = connection.inputStream.use { it.readLimitedBytes(KUHMANN_MAX_DOWNLOAD_BYTES) }
            if (bytes.size < 4 || bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) != "MThd") {
                throw IOException("Downloaded file is not a MIDI file.")
            }
            val expectedSha = result.sha256.trim().lowercase()
            if (expectedSha.isNotBlank() && bytes.sha256Hex() != expectedSha) {
                throw IOException("Checksum did not match.")
            }
            bytes
        } finally {
            connection.disconnect()
        }
    }

    private fun httpGetText(url: URL): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "APS NoteCast")
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("HTTP $status")
            connection.inputStream.use { input ->
                input.readLimitedBytes(KUHMANN_MAX_DOWNLOAD_BYTES).toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readLimitedBytes(maxBytes: Int): ByteArray {
        val buffer = ByteArray(8 * 1024)
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (out.size() + read > maxBytes) throw IOException("Response is too large.")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun ByteArray.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun reloadLibrary(message: String = _state.value.lastMessage) {
        AppEventLog.append(message)
        val snapshot = repository.load()
        _state.update {
            it.copy(
                files = snapshot.files.sortedByDescending { file -> file.importedAtMs },
                playlists = snapshot.playlists.sortedBy { playlist -> playlist.createdAtMs },
                lastMessage = message
            )
        }
    }

    private fun postMessage(message: String) {
        AppEventLog.append(message)
        _state.update { it.copy(lastMessage = message) }
    }

    private fun updateConnectionMessage(message: String) {
        AppEventLog.append(message)
        _state.update {
            it.copy(
                connection = it.connection.copy(
                    message = message,
                    rememberedDeviceName = rememberedDeviceName(),
                    rememberedDeviceAddress = rememberedDeviceAddress(),
                    autoReconnectSuppressed = manualDisconnectSuppressed()
                ),
                lastMessage = message
            )
        }
    }

    private fun logEvent(message: String) {
        AppEventLog.append(message)
        Log.i(LOG_TAG, message)
    }

    private fun registerBluetoothStateReceiver() {
        if (bluetoothStateReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(bluetoothStateReceiver, filter)
        }
        bluetoothStateReceiverRegistered = true
    }

    private fun unregisterBluetoothStateReceiver() {
        if (!bluetoothStateReceiverRegistered) return
        runCatching { unregisterReceiver(bluetoothStateReceiver) }
        bluetoothStateReceiverRegistered = false
    }

    @SuppressLint("MissingPermission")
    private fun refreshBondedBluetoothDevices() {
        if (!hasConnectPermission()) return
        val adapter = bluetoothManager.adapter ?: return
        runCatching {
            adapter.bondedDevices
                .filter { device -> deviceHasBleMidiUuid(device) || nameLooksLikeMidi(safeDeviceName(device)) }
                .forEach { device ->
                    val address = runCatching { device.address }.getOrNull() ?: return@forEach
                    if (isDeviceHidden(address)) return@forEach
                    devicesByAddress[address] = device
                    if (!bleScanItemsByAddress.containsKey(address)) {
                        val rawName = safeDeviceName(device)
                        val standardBleMidi = deviceHasBleMidiUuid(device)
                        val recentlySeen = isRecentlyScannedBleDevice(address)
                        val connectable = isDirectBluetoothMidiConnectionAvailable(address)
                        bleScanItemsByAddress[address] = BleMidiDeviceItem(
                            name = displayNameForAddress(address, rawName, isMidi = true),
                            address = address,
                            rssi = null,
                            likelyMidi = true,
                            standardBleMidi = standardBleMidi,
                            connectable = connectable,
                            added = isKnownDeviceAddress(address),
                            source = "Paired Bluetooth",
                            detail = if (recentlySeen) "$address - seen in latest scan" else "$address - paired"
                        )
                    }
                }
        }
        _state.update { it.copy(bleDevices = mergedDeviceList()) }
    }

    private fun refreshAndroidMidiDevices() {
        val devices = midiManager.devices
            .filter { info ->
                info.ports.any { it.type == MidiDeviceInfo.PortInfo.TYPE_INPUT } &&
                    (info.type == MidiDeviceInfo.TYPE_BLUETOOTH || nameLooksLikeMidi(midiDeviceName(info))) &&
                    !isDeviceHidden(midiConnectionId(info))
            }
        midiDevicesByConnectionId.clear()
        devices.forEach { info -> midiDevicesByConnectionId[midiConnectionId(info)] = info }
        _state.update { it.copy(bleDevices = mergedDeviceList()) }
    }

    private fun midiConnectionId(info: MidiDeviceInfo): String {
        val bluetoothAddress = midiBluetoothDevice(info)?.let { device ->
            runCatching { device.address }.getOrNull()
        }
        return if (info.type == MidiDeviceInfo.TYPE_BLUETOOTH && !bluetoothAddress.isNullOrBlank()) {
            androidBluetoothMidiConnectionId(bluetoothAddress)
        } else {
            "midi:${info.id}"
        }
    }

    private fun androidBluetoothMidiConnectionId(bluetoothAddress: String): String = "midi-bt:$bluetoothAddress"

    private fun isAndroidMidiConnectionId(address: String?): Boolean =
        address?.startsWith("midi:") == true || address?.startsWith("midi-bt:") == true

    private fun bluetoothAddressFromConnectionId(address: String?): String? =
        address?.takeIf { it.startsWith("midi-bt:") }?.removePrefix("midi-bt:")?.takeIf { it.isNotBlank() }

    private fun sameConnectionTarget(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        if (left == right) return true
        val leftBluetoothAddress = bluetoothAddressFromConnectionId(left) ?: left.takeUnless { isAndroidMidiConnectionId(it) }
        val rightBluetoothAddress = bluetoothAddressFromConnectionId(right) ?: right.takeUnless { isAndroidMidiConnectionId(it) }
        return !leftBluetoothAddress.isNullOrBlank() && leftBluetoothAddress == rightBluetoothAddress
    }

    private fun connectionTargetKey(address: String): String =
        bluetoothAddressFromConnectionId(address) ?: address

    private fun connectionTargetAliases(address: String): Set<String> {
        val bluetoothAddress = bluetoothAddressFromConnectionId(address)
        return when {
            bluetoothAddress != null -> setOf(address, bluetoothAddress)
            isAndroidMidiConnectionId(address) -> setOf(address)
            else -> setOf(address, androidBluetoothMidiConnectionId(address))
        }
    }

    private fun availableConnectionAddress(address: String): String? {
        bluetoothAddressFromConnectionId(address)?.let { bluetoothAddress ->
            if (isDirectBluetoothMidiConnectionAvailable(bluetoothAddress)) return bluetoothAddress
        }
        if (!isAndroidMidiConnectionId(address) && isDirectBluetoothMidiConnectionAvailable(address)) {
            return address
        }
        if (midiDevicesByConnectionId.containsKey(address) && isAvailableAndroidMidiConnection(address)) return address
        if (!isAndroidMidiConnectionId(address)) {
            val androidMidiAddress = androidBluetoothMidiConnectionId(address)
            if (midiDevicesByConnectionId.containsKey(androidMidiAddress) && isAvailableAndroidMidiConnection(androidMidiAddress)) {
                return androidMidiAddress
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun isDirectBluetoothMidiConnectionAvailable(address: String): Boolean {
        val device = devicesByAddress[address] ?: return false
        return isRecentlyScannedBleDevice(address) || isBondedBluetoothDevice(device)
    }

    @SuppressLint("MissingPermission")
    private fun isBondedBluetoothDevice(device: BluetoothDevice?): Boolean {
        if (device == null || !hasConnectPermission()) return false
        return runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
    }

    private fun availableAutoReconnectAddress(address: String): String? {
        bluetoothAddressFromConnectionId(address)?.let { bluetoothAddress ->
            if (isDirectBluetoothMidiConnectionAvailable(bluetoothAddress)) return bluetoothAddress
        }
        if (!isAndroidMidiConnectionId(address) && isDirectBluetoothMidiConnectionAvailable(address)) {
            return address
        }
        if (midiDevicesByConnectionId.containsKey(address) && isAvailableAndroidMidiConnection(address)) return address
        bluetoothAddressFromConnectionId(address)?.let { bluetoothAddress ->
            val androidMidiAddress = androidBluetoothMidiConnectionId(bluetoothAddress)
            if (midiDevicesByConnectionId.containsKey(androidMidiAddress) && isAvailableAndroidMidiConnection(androidMidiAddress)) {
                return androidMidiAddress
            }
        }
        if (!isAndroidMidiConnectionId(address)) {
            val androidMidiAddress = androidBluetoothMidiConnectionId(address)
            if (midiDevicesByConnectionId.containsKey(androidMidiAddress) && isAvailableAndroidMidiConnection(androidMidiAddress)) {
                return androidMidiAddress
            }
        }
        return null
    }

    private fun isAvailableAndroidMidiConnection(connectionId: String): Boolean {
        val info = midiDevicesByConnectionId[connectionId] ?: return false
        return midiBluetoothDevice(info) == null ||
            isBluetoothMidiConnectionAttached(connectionId, info) ||
            isRecentlyScannedMidiDevice(info)
    }

    private fun midiDeviceName(info: MidiDeviceInfo): String {
        return midiProperty(info, MidiDeviceInfo.PROPERTY_NAME)
            ?: midiProperty(info, MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: "Android MIDI Device ${info.id}"
    }

    private fun midiDeviceDetail(info: MidiDeviceInfo): String {
        val type = when (info.type) {
            MidiDeviceInfo.TYPE_BLUETOOTH -> "Bluetooth MIDI"
            MidiDeviceInfo.TYPE_USB -> "USB MIDI"
            MidiDeviceInfo.TYPE_VIRTUAL -> "Virtual MIDI"
            else -> "Android MIDI"
        }
        return listOfNotNull(
            type,
            midiProperty(info, MidiDeviceInfo.PROPERTY_MANUFACTURER),
            midiProperty(info, MidiDeviceInfo.PROPERTY_PRODUCT)
        ).distinct().joinToString(" - ")
    }

    private fun midiDeviceDebugSummary(info: MidiDeviceInfo): String {
        val type = when (info.type) {
            MidiDeviceInfo.TYPE_BLUETOOTH -> "bluetooth"
            MidiDeviceInfo.TYPE_USB -> "usb"
            MidiDeviceInfo.TYPE_VIRTUAL -> "virtual"
            else -> "type-${info.type}"
        }
        val ports = info.ports.joinToString(",", limit = 8, truncated = "...") { port ->
            val portType = when (port.type) {
                MidiDeviceInfo.PortInfo.TYPE_INPUT -> "input"
                MidiDeviceInfo.PortInfo.TYPE_OUTPUT -> "output"
                else -> "type-${port.type}"
            }
            "$portType#${port.portNumber}:${port.name ?: "-"}"
        }.ifBlank { "none" }
        val bluetoothAddress = midiBluetoothDevice(info)?.let { device ->
            runCatching { device.address }.getOrNull()
        } ?: "none"
        val properties = info.properties.keySet()
            .sorted()
            .joinToString(",", limit = 8, truncated = "...")
            .ifBlank { "none" }
        return "id=${info.id} type=$type bluetooth=$bluetoothAddress ports=$ports properties=$properties"
    }

    private fun midiProperty(info: MidiDeviceInfo, key: String): String? {
        return info.properties.get(key)?.toString()?.cleanDeviceName()?.takeIf { it.isNotBlank() }
    }

    @Suppress("DEPRECATION")
    private fun midiBluetoothDevice(info: MidiDeviceInfo): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            info.properties.getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE, BluetoothDevice::class.java)
        } else {
            info.properties.getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun mergedDeviceList(): List<BleMidiDeviceItem> {
        val directBluetoothConnectionAddresses = devicesByAddress.keys
            .filter { address -> isDirectBluetoothMidiConnectionAvailable(address) }
            .toSet()
        val displayableMidiDevices = midiDevicesByConnectionId.values.filter { info ->
            val connectionId = midiConnectionId(info)
            val bluetoothAddress = midiBluetoothDevice(info)?.let { device -> runCatching { device.address }.getOrNull() }
            bluetoothAddress == null ||
                bluetoothAddress !in directBluetoothConnectionAddresses ||
                isBluetoothMidiConnectionAttached(connectionId, info)
        }
        val androidMidiBluetoothAddresses = displayableMidiDevices
            .mapNotNull { info -> midiBluetoothDevice(info)?.let { runCatching { it.address }.getOrNull() } }
            .toSet()
        val midiItems = displayableMidiDevices.map { info ->
            val connectionId = midiConnectionId(info)
            val name = displayNameForMidiDevice(info)
            BleMidiDeviceItem(
                name = name,
                address = connectionId,
                rssi = null,
                likelyMidi = true,
                standardBleMidi = info.type == MidiDeviceInfo.TYPE_BLUETOOTH,
                connectable = isAvailableAndroidMidiConnection(connectionId),
                added = isKnownDeviceAddress(connectionId),
                source = if (info.type == MidiDeviceInfo.TYPE_BLUETOOTH) "Android Bluetooth MIDI" else "Android MIDI",
                detail = midiDeviceDetail(info)
            )
        }
        val bleItems = devicesByAddress.values.map { device ->
            val address = runCatching { device.address }.getOrNull() ?: ""
            if (address in androidMidiBluetoothAddresses) return@map null
            if (!isFreshScanTimestamp(bleScanSeenAtMsByAddress[address]) && !isDirectBluetoothMidiConnectionAvailable(address)) return@map null
            val existing = bleScanItemsByAddress[address]
            val added = isKnownDeviceAddress(address)
            existing?.copy(
                added = added,
                connectable = isDirectBluetoothMidiConnectionAvailable(address)
            ) ?: BleMidiDeviceItem(
                name = displayNameForBluetoothDevice(address, device, null),
                address = address,
                connectable = isDirectBluetoothMidiConnectionAvailable(address),
                added = added,
                source = if (isBondedBluetoothDevice(device)) "Paired Bluetooth" else "BLE scan",
                detail = address
            )
        }.filterNotNull()
        val representedTargets = (midiItems + bleItems)
            .map { connectionTargetKey(it.address) }
            .toSet()
        val knownItems = knownDevices()
            .filterNot { connectionTargetKey(it.address) in representedTargets }
            .map { known ->
                val availableAddress = availableConnectionAddress(known.address)
                BleMidiDeviceItem(
                    name = displayNameForAddress(known.address, known.name, isMidi = true),
                    address = availableAddress ?: known.address,
                    rssi = null,
                    likelyMidi = true,
                    standardBleMidi = bluetoothAddressFromConnectionId(known.address) != null || !isAndroidMidiConnectionId(known.address),
                    connectable = availableAddress != null,
                    added = true,
                    source = "Saved",
                    detail = if (availableAddress != null) "Ready" else "not seen in latest scan"
                )
            }
        return (midiItems + bleItems + knownItems)
            .filterNot { isDeviceHidden(it.address) }
            .distinctBy { connectionTargetKey(it.address) }
            .sortedWith(
                compareByDescending<BleMidiDeviceItem> { it.added }
                    .thenByDescending { it.standardBleMidi }
                    .thenByDescending { it.likelyMidi }
                    .thenByDescending { it.rssi ?: -999 }
                    .thenBy { it.name }
            )
    }

    @SuppressLint("MissingPermission")
    private fun addScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = runCatching { device.address }.getOrNull() ?: return
        if (isDeviceHidden(address)) return
        val previousCount = scanResultCountsByAddress[address] ?: 0
        scanResultCountsByAddress[address] = previousCount + 1
        devicesByAddress[address] = device
        val rawName = safeDeviceName(device, result)
        rememberBleScanSighting(address, rawName)
        val standardBleMidi = advertisesBleMidi(result)
        val likelyMidi = standardBleMidi || nameLooksLikeMidi(rawName)
        val known = isKnownDeviceAddress(address)
        val hasAlias = deviceAlias(address) != null
        val name = displayNameForAddress(address, rawName, isMidi = likelyMidi || standardBleMidi || hasAlias || known)
        if (name != rawName) rememberBleScanSightingName(name)
        val detail = when {
            rawName.isBlank() || rawName == "Nearby BLE Device" || rawName == name -> address
            else -> "$rawName - $address"
        }
        val current = _state.value.bleDevices.associateBy { it.address }.toMutableMap()
        val connectableBleAdvert = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result.isConnectable
        } else {
            true
        }
        val previous = bleScanItemsByAddress[address]
        if (previousCount == 0) {
            logEvent(scanResultDebugSummary(result, address, rawName, name, standardBleMidi, likelyMidi))
        } else if (previous != null && previous.name != name) {
            logEvent("BLE scan[$scanSessionId] name changed address=$address from='${previous.name}' to='$name' raw='$rawName' ${scanNameSources(device, result)}")
        }
        val item = BleMidiDeviceItem(
            name = name,
            address = address,
            rssi = result.rssi,
            likelyMidi = likelyMidi,
            standardBleMidi = standardBleMidi,
            connectable = previous?.connectable == true ||
                likelyMidi ||
                standardBleMidi ||
                hasAlias ||
                known ||
                connectableBleAdvert,
            added = known,
            source = "BLE scan",
            detail = detail
        )
        current[address] = item
        bleScanItemsByAddress[address] = item
        _state.update {
            it.copy(
                bleDevices = mergedDeviceList(),
                lastMessage = "Found ${current.size} nearby BLE device${if (current.size == 1) "" else "s"}."
            )
        }
        considerReconnectScanCandidate(address)
    }

    private fun isRecentlyScannedBleDevice(address: String): Boolean {
        return isFreshScanTimestamp(bleScanSeenAtMsByAddress[address]).also { fresh ->
            if (!fresh) bleScanSeenAtMsByAddress.remove(address)
        }
    }

    private fun isRecentlyScannedMidiDevice(info: MidiDeviceInfo): Boolean {
        val bluetoothAddress = midiBluetoothDevice(info)?.let { device ->
            runCatching { device.address }.getOrNull()
        }
        if (!bluetoothAddress.isNullOrBlank() && isRecentlyScannedBleDevice(bluetoothAddress)) return true
        val nameKey = normalizedDeviceName(midiDeviceName(info))
        return isFreshScanTimestamp(bleScanSeenAtMsByName[nameKey]).also { fresh ->
            if (!fresh) bleScanSeenAtMsByName.remove(nameKey)
        }
    }

    private fun rememberBleScanSighting(address: String, name: String) {
        val now = SystemClock.elapsedRealtime()
        bleScanSeenAtMsByAddress[address] = now
        rememberBleScanSightingName(name, now)
    }

    private fun rememberBleScanSightingName(name: String, now: Long = SystemClock.elapsedRealtime()) {
        val nameKey = normalizedDeviceName(name)
        if (nameKey.isNotBlank()) bleScanSeenAtMsByName[nameKey] = now
    }

    private fun isFreshScanTimestamp(seenAt: Long?): Boolean {
        return seenAt != null && SystemClock.elapsedRealtime() - seenAt <= RECENT_BLE_SCAN_TTL_MS
    }

    private fun considerReconnectScanCandidate(address: String) {
        if (!scanAutoConnectKnownDevices) return
        if (_state.value.connection.connected || _state.value.connection.connecting) return
        val candidates = knownReconnectCandidates()
        val preferredAddress = scanPreferredReconnectAddress ?: candidates.firstOrNull()?.address
        if (preferredAddress != null && sameConnectionTarget(address, preferredAddress)) {
            val preferred = candidates.firstOrNull { sameConnectionTarget(it.address, address) }
            postMessage("Reconnecting to ${preferred?.name ?: "preferred MIDI device"}...")
            mainHandler.post { connect(availableConnectionAddress(preferredAddress) ?: address) }
            return
        }
        val index = candidates.indexOfFirst { sameConnectionTarget(it.address, address) }
        if (index < 0) return
        val currentIndex = scanFallbackReconnectAddress?.let { current ->
            candidates.indexOfFirst { sameConnectionTarget(it.address, current) }
        } ?: Int.MAX_VALUE
        if (index < currentIndex) scanFallbackReconnectAddress = address
    }

    @SuppressLint("MissingPermission")
    private fun scanResultDebugSummary(
        result: ScanResult,
        address: String,
        rawName: String,
        displayName: String,
        standardBleMidi: Boolean,
        likelyMidi: Boolean
    ): String {
        val device = result.device
        val bond = runCatching { bondStateName(device.bondState) }.getOrDefault("unknown")
        return "BLE scan[$scanSessionId] saw address=$address display='$displayName' raw='$rawName' rssi=${result.rssi} " +
            "connectable=${scanResultConnectable(result)} bond=$bond standardBleMidi=$standardBleMidi likelyMidi=$likelyMidi " +
            "${scanNameSources(device, result)} services=${scanServiceUuidSummary(result)}"
    }

    @SuppressLint("MissingPermission")
    private fun scanNameSources(device: BluetoothDevice, result: ScanResult? = null): String {
        val fromScan = result?.scanRecord?.deviceName?.cleanDeviceName()?.takeIf { it.isNotBlank() }
        val fromAlias = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { device.alias?.cleanDeviceName()?.takeIf { it.isNotBlank() } }.getOrNull()
        } else {
            null
        }
        val fromDevice = runCatching { device.name?.cleanDeviceName()?.takeIf { it.isNotBlank() } }.getOrNull()
        return "names(scan=${fromScan ?: "-"}, alias=${fromAlias ?: "-"}, device=${fromDevice ?: "-"})"
    }

    private fun scanResultConnectable(result: ScanResult): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result.isConnectable.toString()
        } else {
            "unknown"
        }
    }

    private fun scanServiceUuidSummary(result: ScanResult): String {
        return result.scanRecord
            ?.serviceUuids
            ?.joinToString(",", limit = 4, truncated = "...") { it.uuid.toString() }
            ?.ifBlank { "none" }
            ?: "none"
    }

    private fun bondStateName(state: Int): String {
        return when (state) {
            BluetoothDevice.BOND_BONDED -> "bonded"
            BluetoothDevice.BOND_BONDING -> "bonding"
            BluetoothDevice.BOND_NONE -> "none"
            else -> "unknown($state)"
        }
    }

    private fun pairingVariantName(variant: Int): String {
        return when (variant) {
            BluetoothDevice.PAIRING_VARIANT_PIN -> "pin"
            BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION -> "passkey-confirmation"
            BluetoothDevice.ERROR -> "none"
            else -> "variant-$variant"
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice, result: ScanResult? = null): String {
        val fromScan = result?.scanRecord?.deviceName
        val fromAlias = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { device.alias }.getOrNull()
        } else {
            null
        }
        val fromDevice = runCatching { device.name }.getOrNull()
        return fromScan?.cleanDeviceName()?.takeIf { it.isNotBlank() }
            ?: fromAlias?.cleanDeviceName()?.takeIf { it.isNotBlank() }
            ?: fromDevice?.cleanDeviceName()?.takeIf { it.isNotBlank() }
            ?: "Nearby BLE Device"
    }

    private fun displayNameForBluetoothDevice(address: String, device: BluetoothDevice, result: ScanResult?): String {
        val rawName = safeDeviceName(device, result)
        val isMidi = deviceHasBleMidiUuid(device) ||
            nameLooksLikeMidi(rawName) ||
            isKnownDeviceAddress(address) ||
            deviceAlias(address) != null
        return displayNameForAddress(address, rawName, isMidi = isMidi)
    }

    private fun displayNameForMidiDevice(info: MidiDeviceInfo): String {
        return displayNameForAddress(
            address = midiConnectionId(info),
            rawName = midiDeviceName(info),
            isMidi = true
        )
    }

    private fun displayNameForAddress(address: String, rawName: String, isMidi: Boolean): String {
        deviceAlias(address)
            ?.takeUnless { isGeneratedDeviceName(it) }
            ?.let { return it }
        knownDevices()
            .firstOrNull { sameConnectionTarget(it.address, address) }
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?.takeUnless { isGeneratedDeviceName(it) }
            ?.let { return it }
        val cleanName = rawName.cleanDeviceName()
        val shortId = shortBluetoothIdentifier(address)
        canonicalMidiDeviceName(cleanName, shortId)?.let { return it }
        return when {
            isMidi && isGeneratedDeviceName(cleanName) -> "BLE MIDI Adapter $shortId"
            isMidi && isOpaqueDeviceName(cleanName) -> "BLE MIDI Adapter $shortId"
            !isMidi && isOpaqueDeviceName(cleanName) -> "Nearby BLE Device $shortId"
            cleanName.isBlank() -> if (isMidi) "BLE MIDI Adapter $shortId" else "Nearby BLE Device $shortId"
            else -> cleanName
        }
    }

    private fun canonicalMidiDeviceName(cleanName: String, shortId: String): String? {
        val normalized = normalizedDeviceName(cleanName)
        return when {
            normalized == "widi thru6" || normalized.startsWith("widi thru6 ") -> "WIDI Thru6"
            normalized == "widi master" || normalized.startsWith("widi master ") -> "WIDI Master"
            normalized == "widi jack" || normalized.startsWith("widi jack ") -> "WIDI Jack"
            normalized == "widi uhost" || normalized.startsWith("widi uhost ") -> "WIDI Uhost"
            normalized.startsWith("elk-ble") -> "BLE MIDI Adapter $shortId"
            else -> null
        }
    }

    private fun isOpaqueDeviceName(name: String): Boolean {
        val normalized = normalizedDeviceName(name)
        return normalized.isBlank() ||
            normalized.startsWith("nearby ble device") ||
            OPAQUE_BLE_NAME_REGEX.matches(name.trim()) ||
            GENERIC_BLE_NAME_PREFIXES.any { normalized.startsWith(it) }
    }

    private fun isGeneratedNearbyBleName(name: String): Boolean =
        normalizedDeviceName(name).startsWith("nearby ble device")

    private fun isGeneratedMidiAdapterName(name: String): Boolean {
        val normalized = normalizedDeviceName(name)
        return normalized.startsWith("bluetooth midi adapter") ||
            normalized.startsWith("ble midi adapter") ||
            normalized.startsWith("nearby midi adapter")
    }

    private fun isGeneratedDeviceName(name: String): Boolean =
        isGeneratedNearbyBleName(name) || isGeneratedMidiAdapterName(name)

    private fun shortBluetoothIdentifier(address: String): String {
        val bluetoothAddress = bluetoothAddressFromConnectionId(address) ?: address
        val parts = bluetoothAddress.split(':').filter { it.isNotBlank() }
        return if (parts.size >= 2) parts.takeLast(2).joinToString(":") else bluetoothAddress.takeLast(5)
    }

    private fun advertisesBleMidi(result: ScanResult): Boolean {
        return result.scanRecord
            ?.serviceUuids
            ?.any { it.uuid == MIDI_SERVICE_UUID }
            ?: false
    }

    @SuppressLint("MissingPermission")
    private fun deviceHasBleMidiUuid(device: BluetoothDevice): Boolean {
        return runCatching {
            device.uuids?.any { it.uuid == MIDI_SERVICE_UUID } == true
        }.getOrDefault(false)
    }

    private fun nameLooksLikeMidi(name: String): Boolean {
        val normalized = normalizedDeviceName(name)
        return MIDI_NAME_HINTS.any { normalized.contains(it) }
    }

    private fun normalizedDeviceName(name: String): String = name.trim().lowercase()

    private fun String.cleanDeviceName(): String =
        replace('\t', ' ')
            .replace('\n', ' ')
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun restoreRememberedDevice() {
        _state.update { it.copy(connection = rememberedConnection(message = it.connection.message)) }
    }

    private fun isCurrentConnectionAttempt(generation: Long, address: String): Boolean {
        val connection = _state.value.connection
        return connectionGeneration.get() == generation &&
            connection.connecting &&
            connection.address == address
    }

    private fun rememberedDeviceName(): String? {
        val storedName = prefs.getString(PREF_LAST_DEVICE_NAME, null)?.cleanDeviceName()
        val storedAddress = rememberedDeviceAddress()
        return when {
            storedAddress.isNullOrBlank() -> storedName?.takeIf { it.isNotBlank() }
            else -> displayNameForAddress(
                address = storedAddress,
                rawName = storedName.orEmpty(),
                isMidi = true
            )
        }
    }

    private fun rememberedDeviceAddress(): String? =
        prefs.getString(PREF_LAST_DEVICE_ADDRESS, null)

    private data class KnownMidiDevice(val address: String, val name: String)

    private fun knownReconnectCandidates(): List<KnownMidiDevice> {
        val candidates = mutableListOf<KnownMidiDevice>()
        val lastAddress = rememberedDeviceAddress()
        val lastName = rememberedDeviceName()
        if (!lastAddress.isNullOrBlank()) {
            candidates += KnownMidiDevice(lastAddress, lastName?.takeIf { it.isNotBlank() } ?: "Preferred MIDI device")
        }
        candidates += knownDevices()
        return candidates
            .filter { it.address.isNotBlank() }
            .filterNot { isDeviceHidden(it.address) }
            .distinctBy { it.address }
    }

    private fun knownDevices(): List<KnownMidiDevice> {
        return prefs.getString(PREF_KNOWN_DEVICES, null)
            .orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t', limit = 2)
                val address = parts.getOrNull(0).orEmpty().trim()
                val name = parts.getOrNull(1).orEmpty().cleanDeviceName()
                if (address.isBlank()) null else KnownMidiDevice(address, name.ifBlank { "Known MIDI device" })
            }
            .toList()
    }

    private fun isKnownDeviceAddress(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        val rememberedAddress = rememberedDeviceAddress()
        if (!rememberedAddress.isNullOrBlank() && sameConnectionTarget(rememberedAddress, address)) return true
        return knownDevices().any { sameConnectionTarget(it.address, address) }
    }

    private fun deviceAliases(): Map<String, String> =
        prefs.getString(PREF_DEVICE_ALIASES, null)
            .orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t', limit = 2)
                val address = parts.getOrNull(0).orEmpty().trim()
                val name = parts.getOrNull(1).orEmpty().cleanDeviceName()
                if (address.isBlank() || name.isBlank()) null else address to name
            }
            .toMap()

    private fun deviceAlias(address: String?): String? {
        if (address.isNullOrBlank()) return null
        val aliases = deviceAliases()
        aliases[address]?.let { return it }
        return aliases.entries.firstOrNull { (aliasAddress, _) ->
            sameConnectionTarget(aliasAddress, address)
        }?.value
    }

    private fun hiddenDevices(): Set<String> =
        prefs.getString(PREF_HIDDEN_DEVICES, null)
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    private fun isDeviceHidden(address: String): Boolean =
        address.isNotBlank() && connectionTargetAliases(address).any { it in hiddenDevices() }

    private fun hideDevice(address: String) {
        if (address.isBlank()) return
        val updated = (hiddenDevices() + connectionTargetAliases(address)).sorted()
        prefs.edit()
            .putString(PREF_HIDDEN_DEVICES, updated.joinToString("\n"))
            .apply()
    }

    private fun clearHiddenDevices() {
        if (hiddenDevices().isEmpty()) return
        prefs.edit().remove(PREF_HIDDEN_DEVICES).apply()
    }

    private fun rememberConnectedDevice(address: String, name: String) {
        val cleanName = cleanStoredDeviceName(name).ifBlank { "MIDI device" }
        val updated = (listOf(KnownMidiDevice(address, cleanName)) + knownDevices().filterNot { sameConnectionTarget(it.address, address) })
            .take(8)
        val hidden = hiddenDevices() - connectionTargetAliases(address)
        prefs.edit()
            .putString(PREF_LAST_DEVICE_ADDRESS, address)
            .putString(PREF_LAST_DEVICE_NAME, cleanName)
            .putString(PREF_KNOWN_DEVICES, updated.joinToString("\n") { "${it.address}\t${it.name}" })
            .putString(PREF_HIDDEN_DEVICES, hidden.sorted().joinToString("\n"))
            .putBoolean(PREF_MANUAL_DISCONNECT, false)
            .apply()
    }

    private fun cleanStoredDeviceName(name: String): String = name.cleanDeviceName()

    private fun clearReconnectScanState() {
        scanAutoConnectKnownDevices = false
        scanPreferredReconnectAddress = null
        scanFallbackReconnectAddress = null
    }

    private fun manualDisconnectSuppressed(): Boolean =
        prefs.getBoolean(PREF_MANUAL_DISCONNECT, false)

    private fun suppressAutoReconnectAfterManualDisconnect() {
        prefs.edit().putBoolean(PREF_MANUAL_DISCONNECT, true).apply()
        _state.update { it.copy(connection = it.connection.copy(autoReconnectSuppressed = true)) }
    }

    private fun clearManualDisconnectSuppression() {
        prefs.edit().putBoolean(PREF_MANUAL_DISCONNECT, false).apply()
        _state.update { it.copy(connection = it.connection.copy(autoReconnectSuppressed = false)) }
    }

    private fun rememberedConnection(message: String): ConnectionUiState =
        ConnectionUiState(
            bluetoothAvailable = _state.value.connection.bluetoothAvailable,
            bluetoothEnabled = _state.value.connection.bluetoothEnabled,
            rememberedDeviceName = rememberedDeviceName(),
            rememberedDeviceAddress = rememberedDeviceAddress(),
            autoReconnectSuppressed = manualDisconnectSuppressed(),
            message = message
        )

    private fun ConnectionUiState.scanStopped(): ConnectionUiState {
        val stoppedMessage = if (connected) {
            deviceName?.let { "Connected to $it" } ?: "Connected"
        } else {
            "Scan stopped."
        }
        return copy(scanning = false, message = stoppedMessage)
    }

    private fun consumeSkipRequest(): Int? {
        val request = skipRequest
        if (request == 0) return null
        skipRequest = 0
        return request
    }

    private fun consumeSeekRequest(durationUs: Long): Long? {
        val request = seekRequestUs
        if (request < 0L) return null
        seekRequestUs = -1L
        return request.coerceIn(0L, durationUs.coerceAtLeast(0L))
    }

    private fun stopRecordingReceiver() {
        val receiver = recordingReceiver ?: return
        runCatching { midiOutputPort?.disconnect(receiver) }
        recordingReceiver = null
    }

    private fun recordIncomingMidi(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
        val messages = splitMidiMessages(msg, offset, count)
        if (messages.isEmpty()) return
        val nowNs = if (timestamp > 0L) timestamp else System.nanoTime()
        var elapsedUs = 0L
        var totalEvents = 0
        synchronized(recordingLock) {
            if (recordingStartedNs == 0L) recordingStartedNs = nowNs
            elapsedUs = ((nowNs - recordingStartedNs) / 1_000L).coerceAtLeast(0L)
            messages.forEach { data -> recordingEvents += MidiFileWriter.RecordedEvent(elapsedUs, data) }
            totalEvents = recordingEvents.size
        }
        _state.update {
            it.copy(
                recording = it.recording.copy(
                    isRecording = true,
                    eventCount = totalEvents,
                    durationUs = elapsedUs,
                    message = "Recording MIDI input..."
                )
            )
        }
    }

    private fun splitMidiMessages(msg: ByteArray, offset: Int, count: Int): List<ByteArray> {
        val end = (offset + count).coerceAtMost(msg.size)
        val messages = mutableListOf<ByteArray>()
        var index = offset.coerceAtLeast(0)
        var runningStatus = 0
        while (index < end) {
            var status = msg[index].toInt() and 0xFF
            if (status >= 0x80) {
                index++
            } else if (runningStatus != 0) {
                status = runningStatus
            } else {
                index++
                continue
            }

            when {
                status in 0x80..0xEF -> {
                    runningStatus = status
                    val dataLength = channelMessageDataLength(status)
                    if (index + dataLength > end) return messages
                    val data = ByteArray(dataLength + 1)
                    data[0] = status.toByte()
                    repeat(dataLength) { data[it + 1] = msg[index + it] }
                    messages += data
                    index += dataLength
                }

                status == 0xF0 -> {
                    runningStatus = 0
                    val sysexStart = index - 1
                    while (index < end && (msg[index].toInt() and 0xFF) != 0xF7) index++
                    if (index < end) index++
                    messages += msg.copyOfRange(sysexStart, index)
                }

                status == 0xF7 -> {
                    runningStatus = 0
                    messages += byteArrayOf(0xF7.toByte())
                }

                else -> {
                    runningStatus = 0
                    val dataLength = systemCommonDataLength(status)
                    if (index + dataLength <= end) index += dataLength else return messages
                }
            }
        }
        return messages
    }

    private fun channelMessageDataLength(status: Int): Int {
        return when (status and 0xF0) {
            0xC0, 0xD0 -> 1
            else -> 2
        }
    }

    private fun systemCommonDataLength(status: Int): Int {
        return when (status) {
            0xF1, 0xF3 -> 1
            0xF2 -> 2
            else -> 0
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        return runCatching { bluetoothManager.adapter?.isEnabled == true }.getOrDefault(false)
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun closeMidiConnection() {
        stopRecordingReceiver()
        val inputPort = midiInputPort
        val outputPort = midiOutputPort
        val device = midiDevice
        midiInputPort = null
        midiOutputPort = null
        midiDevice = null
        connectedMidiDeviceInfo = null
        runCatching { inputPort?.close() }
        runCatching { outputPort?.close() }
        runCatching { device?.close() }
        _state.update { it.copy(recording = RecordingUiState()) }
    }

    private fun sendPanicMessages() {
        val connection = _state.value.connection
        if (!connection.connected) return
        if (!isAndroidMidiConnectionId(connection.address) && !isBluetoothEnabled()) return
        val port = midiInputPort ?: return
        val now = System.nanoTime()
        try {
            for (channel in 0..15) {
                val status = (0xB0 or channel).toByte()
                val messages = arrayOf(
                    byteArrayOf(status, 64, 0),   // sustain pedal off
                    byteArrayOf(status, 66, 0),   // sostenuto off
                    byteArrayOf(status, 67, 0),   // soft pedal off
                    byteArrayOf(status, 120.toByte(), 0), // all sound off
                    byteArrayOf(status, 121.toByte(), 0), // reset all controllers
                    byteArrayOf(status, 123.toByte(), 0)  // all notes off
                )
                messages.forEach { msg -> port.send(msg, 0, msg.size, now) }
            }
            port.flush()
        } catch (_: Throwable) {
        }
    }

    private fun sendVolumeMessages(percent: Int) {
        if (appSettings.appControlsVolume) return
        val connection = _state.value.connection
        if (!connection.connected) return
        if (!isAndroidMidiConnectionId(connection.address) && !isBluetoothEnabled()) return
        val port = midiInputPort ?: return
        val controls = appSettings.channelControls.normalizedControls()
        val soloActive = controls.values.any { it.solo }
        val now = System.nanoTime()
        try {
            for (channel in 0..15) {
                val control = controls[channel + 1] ?: MidiChannelControl(channel = channel + 1)
                val value = if (control.muted || (soloActive && !control.solo)) {
                    0
                } else {
                    scaledMidiVolume(127, percent, control.volumePercent)
                }.toByte()
                val status = (0xB0 or channel).toByte()
                port.send(byteArrayOf(status, 7, value), 0, 3, now)
            }
            port.flush()
        } catch (_: Throwable) {
        }
    }

    private fun MidiFileParser.MidiSequence.channelNumbers(): List<Int> =
        events.mapNotNull { event ->
            val status = event.data.firstOrNull()?.toInt()?.and(0xFF) ?: return@mapNotNull null
            if (status in 0x80..0xEF) (status and 0x0F) + 1 else null
        }.distinct().sorted()

    private fun List<MidiChannelControl>.normalizedControls(): Map<Int, MidiChannelControl> =
        (1..16).associateWith { channel ->
            firstOrNull { it.channel == channel }?.copy(
                channel = channel,
                volumePercent = firstOrNull { it.channel == channel }?.volumePercent?.coerceIn(0, 100) ?: 100
            ) ?: MidiChannelControl(channel = channel)
        }

    private fun List<MidiChannelControl>.channelControl(channel: Int): MidiChannelControl =
        firstOrNull { it.channel == channel }?.let { control ->
            control.copy(
                channel = channel,
                volumePercent = control.volumePercent.coerceIn(0, 100)
            )
        } ?: MidiChannelControl(channel = channel)

    private fun List<MidiChannelControl>.cacheSignature(): String =
        normalizedControls().values.joinToString("|") {
            "${it.channel}:${it.muted}:${it.solo}:${it.volumePercent.coerceIn(0, 100)}"
        }

    private fun List<MidiChannelControl>.volumeSignature(): String =
        normalizedControls().values.joinToString("|") {
            "${it.channel}:${it.volumePercent.coerceIn(0, 100)}"
        }

    private fun List<MidiChannelControl>.muteSoloSignature(): String =
        normalizedControls().values.joinToString("|") {
            "${it.channel}:${it.muted}:${it.solo}"
        }

    private fun scaledMidiVolume(rawValue: Int, masterPercent: Int, channelPercent: Int): Int {
        val scaled = rawValue.coerceIn(0, 127) *
            masterPercent.coerceIn(0, 100) *
            channelPercent.coerceIn(0, 100)
        return (scaled / 10_000).coerceIn(0, 127)
    }

    private fun scaledMidiVelocity(rawValue: Int, masterPercent: Int, channelPercent: Int): Int {
        val raw = rawValue.coerceIn(1, 127)
        if (masterPercent <= 0 || channelPercent <= 0) return 0
        val scaled = scaledMidiVolume(raw, masterPercent, channelPercent)
        return scaled.coerceIn(1, 127)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val existing = wakeLock
        if (existing?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "APSNoteCast:MidiPlayback").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock
        if (lock?.isHeld == true) runCatching { lock.release() }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "APS NoteCast playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows MIDI playback status while APS NoteCast is driving a player piano."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun createMediaSession() {
        mediaSession = MediaSession(this, "APS NoteCast").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        resumePlayback()
                    }

                    override fun onPause() {
                        pausePlayback()
                    }

                    override fun onStop() {
                        stopPlayback(userRequested = true)
                    }

                    override fun onSkipToNext() {
                        skipToNext()
                    }

                    override fun onSkipToPrevious() {
                        skipToPrevious()
                    }

                    override fun onSeekTo(pos: Long) {
                        seekPlayback(pos * 1_000L)
                    }
                },
                mainHandler
            )
            setPlaybackState(buildPlaybackState(_state.value.playback))
        }
    }

    private fun startForegroundForPlayback(title: String) {
        val notification = buildNotification(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foreground = true
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(title: String) {
        if (!foreground) return
        if (!hasPostNotificationsPermission()) return
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(title))
        }
    }

    private fun stopForegroundIfNeeded() {
        if (!foreground) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        foreground = false
    }

    private fun updatePlaybackSurfaces(updateNotification: Boolean) {
        val playback = _state.value.playback
        updateMediaSession(playback)
        if (updateNotification && foreground) {
            updateNotification(playback.currentTitle ?: "APS NoteCast")
        }
    }

    private fun updateMediaSession(playback: PlaybackUiState = _state.value.playback) {
        if (!::mediaSession.isInitialized) return
        val durationMs = playback.durationUs.usToMs()
        val title = playback.currentTitle ?: "APS NoteCast"
        val subtitle = playback.playlistName ?: when (playback.mode) {
            PlaybackMode.Preparing -> "Preparing"
            PlaybackMode.Playing -> "Playing"
            PlaybackMode.Paused -> "Paused"
            PlaybackMode.Error -> "Playback error"
            PlaybackMode.Idle -> "Ready"
        }
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            .build()
        mediaSession.setMetadata(metadata)
        mediaSession.setPlaybackState(buildPlaybackState(playback))
        mediaSession.isActive = playback.isActive || playback.mode == PlaybackMode.Error
    }

    private fun buildPlaybackState(playback: PlaybackUiState): PlaybackState {
        val state = when (playback.mode) {
            PlaybackMode.Preparing -> PlaybackState.STATE_BUFFERING
            PlaybackMode.Playing -> PlaybackState.STATE_PLAYING
            PlaybackMode.Paused -> PlaybackState.STATE_PAUSED
            PlaybackMode.Error -> PlaybackState.STATE_ERROR
            PlaybackMode.Idle -> PlaybackState.STATE_STOPPED
        }
        val speed = if (playback.isPlaying) 1f else 0f
        val builder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SEEK_TO
            )
            .setState(state, playback.progressUs.usToMs(), speed)
        if (playback.mode == PlaybackMode.Error) {
            builder.setErrorMessage(playback.error ?: "Playback error")
        }
        return builder.build()
    }

    private fun buildNotification(title: String): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val openIntent = PendingIntent.getActivity(
            this,
            3,
            launchIntent,
            flags
        )
        val playback = _state.value.playback
        val displayTitle = playback.currentTitle ?: title
        val displayText = playback.playlistName ?: when (playback.mode) {
            PlaybackMode.Preparing -> "Preparing..."
            PlaybackMode.Playing -> "Playing ${playback.progressUs.formatClockTime()} / ${playback.durationUs.formatClockTime()}"
            PlaybackMode.Paused -> "Paused ${playback.progressUs.formatClockTime()} / ${playback.durationUs.formatClockTime()}"
            PlaybackMode.Error -> playback.error ?: "Playback error"
            PlaybackMode.Idle -> "Ready"
        }
        val playPauseAction = if (playback.isPlaying) {
            notificationAction(R.drawable.ic_notification_pause, "Pause", ACTION_PLAY_PAUSE, 12)
        } else {
            notificationAction(R.drawable.ic_notification_play, "Play", ACTION_PLAY_PAUSE, 12)
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_piano_notification)
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setContentIntent(openIntent)
            .setOngoing(playback.isActive)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setProgress(
                playback.durationUs.usToMs().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                playback.progressUs.usToMs().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                playback.durationUs <= 0L
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(notificationAction(R.drawable.ic_notification_previous, "Previous", ACTION_PREVIOUS, 11))
            .addAction(playPauseAction)
            .addAction(notificationAction(R.drawable.ic_notification_next, "Next", ACTION_NEXT, 13))
            .addAction(notificationAction(R.drawable.ic_notification_stop, "Stop", ACTION_STOP, 14))
            .build()
    }

    private fun notificationAction(
        iconResId: Int,
        title: String,
        action: String,
        requestCode: Int
    ): Notification.Action {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val intent = PendingIntent.getService(
            this,
            requestCode,
            Intent(this, NoteCastService::class.java).setAction(action),
            flags
        )
        return Notification.Action.Builder(
            Icon.createWithResource(this, iconResId),
            title,
            intent
        ).build()
    }

    private fun Long.usToMs(): Long = (this / 1_000L).coerceAtLeast(0L)

    private fun hasPostNotificationsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
