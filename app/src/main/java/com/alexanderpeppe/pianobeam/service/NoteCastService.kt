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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.alexanderpeppe.pianobeam.MainActivity
import com.alexanderpeppe.pianobeam.R
import com.alexanderpeppe.pianobeam.data.AppSettings
import com.alexanderpeppe.pianobeam.data.AppUiState
import com.alexanderpeppe.pianobeam.data.BleMidiDeviceItem
import com.alexanderpeppe.pianobeam.data.ConnectionUiState
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
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.coroutines.coroutineContext

class NoteCastService : Service() {
    companion object {
        private const val CHANNEL_ID = "pianobeam_playback"
        private const val NOTIFICATION_ID = 440
        private const val ACTION_PLAY_PAUSE = "com.alexanderpeppe.pianobeam.PLAY_PAUSE"
        private const val ACTION_PREVIOUS = "com.alexanderpeppe.pianobeam.PREVIOUS"
        private const val ACTION_NEXT = "com.alexanderpeppe.pianobeam.NEXT"
        private const val ACTION_STOP = "com.alexanderpeppe.pianobeam.STOP"
        private const val ACTION_PANIC = "com.alexanderpeppe.pianobeam.PANIC"
        private const val SCAN_WINDOW_MS = 12_000L
        private const val AUTO_RECONNECT_SCAN_WINDOW_MS = 4_500L
        private const val SCHEDULE_AHEAD_NS = 45_000_000L
        private const val START_DELAY_NS = 160_000_000L
        private const val PROGRESS_POST_INTERVAL_NS = 220_000_000L
        private const val PREFS_NAME = "pianobeam"
        private const val PREF_LAST_DEVICE_ADDRESS = "last_device_address"
        private const val PREF_LAST_DEVICE_NAME = "last_device_name"
        private const val PREF_KNOWN_DEVICES = "known_devices"
        private const val PREF_HIDDEN_DEVICES = "hidden_devices"
        private const val PREF_MANUAL_DISCONNECT = "manual_disconnect"

        private val MIDI_SERVICE_UUID: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
        private val MIDI_NAME_HINTS = listOf(
            "widi",
            "cme",
            "thru6",
            "thru 6",
            "midi",
            "md-bt",
            "ud-bt"
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
    private val midiDevicesByConnectionId = linkedMapOf<String, MidiDeviceInfo>()
    private var scanCallback: ScanCallback? = null
    private var scanTimeoutJob: Job? = null
    private var scanAutoConnectKnownDevices = false
    private var scanPreferredReconnectAddress: String? = null
    private var scanFallbackReconnectAddress: String? = null
    @Volatile
    private var autoReconnectPausedForDevicePicker = false
    private val midiDeviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            refreshAndroidMidiDevices()
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            val removedConnectionId = midiConnectionId(device)
            refreshAndroidMidiDevices()
            val connection = _state.value.connection
            if (connection.connected && connection.address == removedConnectionId) {
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
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED -> handleBluetoothDeviceDisconnected(intent.bluetoothDeviceExtra())
            }
        }
    }
    private var bluetoothStateReceiverRegistered = false

    private var midiDevice: MidiDevice? = null
    private var midiInputPort: MidiInputPort? = null
    private var midiOutputPort: MidiOutputPort? = null
    private var playbackJob: Job? = null
    private var recordingCountdownJob: Job? = null
    private var connectionMonitorJob: Job? = null
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
        val volumePercent: Int,
        val channelSignature: String
    )

    private val sequenceCacheLock = Any()
    private val preparedSequenceCache = object : LinkedHashMap<PreparedSequenceCacheKey, MidiFileParser.MidiSequence>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PreparedSequenceCacheKey, MidiFileParser.MidiSequence>?): Boolean =
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
        connectionMonitorJob?.cancel()
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
        appSettings = settings
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

    fun deleteMidiFile(itemId: String) {
        stopPlaybackIfUsing(itemId)
        repository.deleteFile(itemId)
        reloadLibrary("Removed MIDI file.")
    }

    fun renameMidiFile(itemId: String, title: String) {
        repository.renameFile(itemId, title)
        reloadLibrary("Renamed MIDI file.")
    }

    fun createPlaylist(name: String) {
        repository.createPlaylist(name)
        reloadLibrary("Created playlist.")
    }

    fun deletePlaylist(playlistId: String) {
        repository.deletePlaylist(playlistId)
        reloadLibrary("Deleted playlist.")
    }

    fun renamePlaylist(playlistId: String, name: String) {
        repository.renamePlaylist(playlistId, name)
        reloadLibrary("Renamed playlist.")
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
            availableConnectionAddress(candidate.address)?.let { address -> candidate to address }
        }
        if (available != null) {
            val (candidate, address) = available
            postMessage("Reconnecting to ${candidate.name}...")
            connect(address)
            return
        }
        val initialFallbackAddress = refreshedCandidates
            .drop(1)
            .firstNotNullOfOrNull { availableConnectionAddress(it.address) }
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
                    lastMessage = "Bluetooth is off. Turn it on to scan for WIDI devices."
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
        devicesByAddress.clear()
        bleScanItemsByAddress.clear()
        refreshBondedBluetoothDevices()
        refreshAndroidMidiDevices()
        scanAutoConnectKnownDevices = autoConnectKnownDevices
        scanPreferredReconnectAddress = knownReconnectCandidates().firstOrNull()?.address
        scanFallbackReconnectAddress = initialFallbackAddress
        val message = if (autoConnectKnownDevices) {
            "Looking for known BLE MIDI devices..."
        } else {
            "Scanning nearby BLE and Android MIDI devices..."
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
                clearReconnectScanState()
                _state.update {
                    it.copy(
                        connection = it.connection.copy(scanning = false),
                        lastMessage = "BLE scan failed with error $errorCode."
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
            clearReconnectScanState()
            _state.update {
                it.copy(
                    connection = it.connection.copy(scanning = false),
                    lastMessage = "Bluetooth permission was denied by Android."
                )
            }
        }
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
            _state.update { it.copy(connection = it.connection.copy(scanning = false)) }
            return
        }
        scanCallback = null
        runCatching {
            if (hasScanPermission()) bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(callback)
        }
        clearReconnectScanState()
        _state.update { it.copy(connection = it.connection.copy(scanning = false)) }
        if (shouldUseFallback && fallbackAddress != null && !_state.value.connection.connected && !_state.value.connection.connecting) {
            val fallback = knownReconnectCandidates().firstOrNull { sameConnectionTarget(it.address, fallbackAddress) }
            postMessage("Preferred device not found. Reconnecting to ${fallback?.name ?: "known MIDI device"}...")
            mainHandler.post { connect(availableConnectionAddress(fallbackAddress) ?: fallbackAddress) }
        }
    }

    fun connect(address: String) {
        val resolvedAddress = availableConnectionAddress(address) ?: address
        if (_state.value.connection.connected && sameConnectionTarget(_state.value.connection.address, resolvedAddress)) {
            postMessage("Already connected to ${_state.value.connection.deviceName ?: "that MIDI device"}.")
            return
        }
        clearManualDisconnectSuppression()
        if (isAndroidMidiConnectionId(resolvedAddress)) {
            connectAndroidMidiDevice(resolvedAddress)
            return
        }
        val device = devicesByAddress[resolvedAddress]
        if (device == null) {
            postMessage("That BLE MIDI device is no longer in the scan list. Scan again.")
            return
        }
        if (!hasConnectPermission()) {
            postMessage("Bluetooth connect permission is required before connecting.")
            return
        }
        val generation = connectionGeneration.incrementAndGet()
        stopBleScan()
        prepareForConnectionChange()
        val name = safeDeviceName(device)
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
                    _state.update {
                        it.copy(
                            connection = rememberedConnection(message = "Could not open $name as a BLE MIDI device."),
                            lastMessage = "Connection failed."
                        )
                    }
                    return@openBluetoothDevice
                }
                finishMidiConnection(openedDevice, name, resolvedAddress, generation)
            }, mainHandler)
        } catch (security: SecurityException) {
            if (isCurrentConnectionAttempt(generation, resolvedAddress)) {
                _state.update {
                    it.copy(
                        connection = rememberedConnection(message = "Bluetooth connect permission was denied by Android."),
                        lastMessage = "Connection permission denied."
                    )
                }
            }
        } catch (t: Throwable) {
            if (isCurrentConnectionAttempt(generation, resolvedAddress)) {
                _state.update {
                    it.copy(
                        connection = rememberedConnection(message = "Android could not start the BLE MIDI connection."),
                        lastMessage = "Connection failed: ${t.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    private fun prepareForConnectionChange() {
        val wasActive = _state.value.playback.isActive
        connectionMonitorJob?.cancel()
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
            _state.update { it.copy(playback = PlaybackUiState()) }
            updatePlaybackSurfaces(updateNotification = true)
            stopForegroundIfNeeded()
            releaseWakeLock()
        }
    }

    fun disconnect() {
        connectionGeneration.incrementAndGet()
        connectionMonitorJob?.cancel()
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

    fun removeDevice(address: String) {
        val connection = _state.value.connection
        val name = _state.value.bleDevices.firstOrNull { it.address == address }?.name
            ?: (if (connection.address == address) connection.deviceName else null)
            ?: "MIDI device"
        val wasCurrentConnection = connection.address == address
        if (wasCurrentConnection) {
            connectionGeneration.incrementAndGet()
            connectionMonitorJob?.cancel()
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
        val editor = prefs.edit()
            .putString(PREF_KNOWN_DEVICES, remainingKnownDevices.joinToString("\n") { "${it.address}\t${it.name}" })
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
            .remove(PREF_HIDDEN_DEVICES)
            .putBoolean(PREF_MANUAL_DISCONNECT, false)
            .apply()
        _state.update {
            it.copy(
                connection = it.connection.copy(
                    rememberedDeviceAddress = null,
                    rememberedDeviceName = null,
                    autoReconnectSuppressed = false,
                    message = "No preferred WIDI device"
                ),
                lastMessage = "Forgot preferred WIDI device."
            )
        }
    }

    fun connectionDiagnostics(): String {
        refreshKnownDevices()
        val connection = _state.value.connection
        return buildString {
            appendLine("Bluetooth hardware: ${if (connection.bluetoothAvailable) "available" else "not reported"}")
            appendLine("Bluetooth: ${if (connection.bluetoothEnabled) "on" else "off"}")
            appendLine("Connected: ${connection.deviceName ?: "no"}")
            appendLine("Preferred: ${connection.rememberedDeviceName ?: "none"}")
            appendLine("Reconnect paused: ${if (connection.autoReconnectSuppressed) "yes" else "no"}")
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
        val generation = connectionGeneration.incrementAndGet()
        stopBleScan()
        prepareForConnectionChange()
        val name = midiDeviceName(info)
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
        val inputPort = runCatching {
            val inputPortInfo = openedDevice.info.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_INPUT }
            val portNumber = inputPortInfo?.portNumber ?: 0
            openedDevice.openInputPort(portNumber)
        }.getOrNull()
        if (inputPort == null) {
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
        reconnectJob?.cancel()
        reconnectJob = null
        rememberConnectedDevice(connectionId, name)
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
        if (isAndroidMidiConnectionId(connectionId)) {
            refreshAndroidMidiDevices()
            val info = midiDevicesByConnectionId[connectionId] ?: return false
            val bluetoothDevice = midiBluetoothDevice(info)
            return bluetoothDevice == null || isBluetoothDeviceConnected(bluetoothDevice)
        }
        if (!hasConnectPermission()) return true
        val device = devicesByAddress[connectionId] ?: return false
        val bonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(true)
        if (!bonded) return false
        return midiInputPort != null && isBluetoothDeviceConnected(device)
    }

    @SuppressLint("MissingPermission")
    private fun handleBluetoothDeviceDisconnected(device: BluetoothDevice?) {
        if (device == null) return
        val address = runCatching { device.address }.getOrNull() ?: return
        val connection = _state.value.connection
        if (!connection.connected && !connection.connecting) return
        if (!currentConnectionMatchesBluetoothDevice(address)) return
        val name = connection.deviceName ?: runCatching { safeDeviceName(device) }.getOrDefault("MIDI device")
        AppEventLog.append("$name disconnected at the Bluetooth layer.")
        handleConnectionLost("$name disconnected.")
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

    @SuppressLint("MissingPermission")
    private fun isBluetoothDeviceConnected(device: BluetoothDevice): Boolean {
        if (!hasConnectPermission()) return true
        return runCatching {
            bluetoothManager.getConnectionState(device, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED
        }.getOrDefault(true)
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
        connectionMonitorJob?.cancel()
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
                val volumeSnapshot = _state.value.volumePercent
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
                            lastMessage = "Preparing ${item.title}..."
                        )
                    }
                    updatePlaybackSurfaces(updateNotification = true)
                }
                val sequence = loadPreparedSequence(item, settingsSnapshot, volumeSnapshot)
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
        settings: AppSettings,
        volumePercent: Int
    ): MidiFileParser.MidiSequence = withContext(Dispatchers.IO) {
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
            volumePercent = volumePercent.coerceIn(0, 100),
            channelSignature = settings.channelControls.cacheSignature()
        )
        synchronized(sequenceCacheLock) { preparedSequenceCache[key] }?.let { cached ->
            return@withContext cached
        }

        val parsed = MidiFileParser.parse(file.readBytes(), item.title)
        val prepared = prepareSequence(parsed, settings, volumePercent)
        synchronized(sequenceCacheLock) {
            preparedSequenceCache[key] = prepared
        }
        prepared
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
        settings: AppSettings,
        volumePercent: Int
    ): MidiFileParser.MidiSequence {
        val tempoPercent = settings.tempoPercent.coerceIn(50, 150)
        val adjustedEvents = sequence.events.mapNotNull { event ->
            transformMidiData(event.data, settings, volumePercent)?.let { data ->
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

    private fun transformMidiData(data: ByteArray, settings: AppSettings, volumePercent: Int): ByteArray? {
        if (data.isEmpty()) return null
        val status = data[0].toInt() and 0xFF
        if (status !in 0x80..0xEF) return data
        val channelNumber = (status and 0x0F) + 1
        val controls = settings.channelControls.normalizedControls()
        val control = controls[channelNumber] ?: MidiChannelControl(channel = channelNumber)
        val soloActive = controls.values.any { it.solo }
        if (control.muted || (soloActive && !control.solo)) return null

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

        if (messageType == 0xB0 && copy.size > 2 && (copy[1].toInt() and 0xFF) == 7) {
            copy[2] = scaledMidiVolume(copy[2].toInt() and 0xFF, volumePercent, control.volumePercent).toByte()
        }
        return copy
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
                port.send(event.data, 0, event.data.size, targetNs)

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

    private fun registerBluetoothStateReceiver() {
        if (bluetoothStateReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
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
                        val name = safeDeviceName(device)
                        val standardBleMidi = deviceHasBleMidiUuid(device)
                        bleScanItemsByAddress[address] = BleMidiDeviceItem(
                            name = name,
                            address = address,
                            rssi = null,
                            likelyMidi = true,
                            standardBleMidi = standardBleMidi,
                            source = "Paired Bluetooth",
                            detail = address
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

    private fun connectionTargetAliases(address: String): Set<String> {
        val bluetoothAddress = bluetoothAddressFromConnectionId(address)
        return when {
            bluetoothAddress != null -> setOf(address, bluetoothAddress)
            isAndroidMidiConnectionId(address) -> setOf(address)
            else -> setOf(address, androidBluetoothMidiConnectionId(address))
        }
    }

    private fun availableConnectionAddress(address: String): String? {
        if (midiDevicesByConnectionId.containsKey(address)) return address
        bluetoothAddressFromConnectionId(address)?.let { bluetoothAddress ->
            if (devicesByAddress.containsKey(bluetoothAddress)) return bluetoothAddress
        }
        if (!isAndroidMidiConnectionId(address)) {
            val androidMidiAddress = androidBluetoothMidiConnectionId(address)
            if (midiDevicesByConnectionId.containsKey(androidMidiAddress)) return androidMidiAddress
        }
        if (devicesByAddress.containsKey(address)) return address
        return null
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

    private fun midiProperty(info: MidiDeviceInfo, key: String): String? {
        return info.properties.get(key)?.toString()?.takeIf { it.isNotBlank() }
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
        val androidMidiBluetoothAddresses = midiDevicesByConnectionId.values
            .mapNotNull { info -> midiBluetoothDevice(info)?.let { runCatching { it.address }.getOrNull() } }
            .toSet()
        val midiItems = midiDevicesByConnectionId.values.map { info ->
            val name = midiDeviceName(info)
            BleMidiDeviceItem(
                name = name,
                address = midiConnectionId(info),
                rssi = null,
                likelyMidi = true,
                standardBleMidi = info.type == MidiDeviceInfo.TYPE_BLUETOOTH,
                source = if (info.type == MidiDeviceInfo.TYPE_BLUETOOTH) "Android Bluetooth MIDI" else "Android MIDI",
                detail = midiDeviceDetail(info)
            )
        }
        val bleItems = devicesByAddress.values.map { device ->
            val address = runCatching { device.address }.getOrNull() ?: ""
            if (address in androidMidiBluetoothAddresses) return@map null
            val existing = bleScanItemsByAddress[address]
            existing ?: BleMidiDeviceItem(
                name = safeDeviceName(device),
                address = address,
                source = "BLE scan",
                detail = address
            )
        }.filterNotNull()
        return (midiItems + bleItems)
            .filterNot { isDeviceHidden(it.address) }
            .distinctBy { it.address }
            .sortedWith(
                compareByDescending<BleMidiDeviceItem> { it.standardBleMidi }
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
        devicesByAddress[address] = device
        val name = safeDeviceName(device, result)
        val standardBleMidi = advertisesBleMidi(result)
        val likelyMidi = standardBleMidi || nameLooksLikeMidi(name)
        val current = _state.value.bleDevices.associateBy { it.address }.toMutableMap()
        val item = BleMidiDeviceItem(
            name = name,
            address = address,
            rssi = result.rssi,
            likelyMidi = likelyMidi,
            standardBleMidi = standardBleMidi,
            source = "BLE scan",
            detail = address
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
    private fun safeDeviceName(device: BluetoothDevice, result: ScanResult? = null): String {
        val fromScan = result?.scanRecord?.deviceName
        val fromDevice = runCatching { device.name }.getOrNull()
        return fromScan?.takeIf { it.isNotBlank() }
            ?: fromDevice?.takeIf { it.isNotBlank() }
            ?: "Nearby BLE Device"
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
        val normalized = name.lowercase()
        return MIDI_NAME_HINTS.any { normalized.contains(it) }
    }

    private fun restoreRememberedDevice() {
        _state.update { it.copy(connection = rememberedConnection(message = it.connection.message)) }
    }

    private fun isCurrentConnectionAttempt(generation: Long, address: String): Boolean {
        val connection = _state.value.connection
        return connectionGeneration.get() == generation &&
            connection.connecting &&
            connection.address == address
    }

    private fun rememberedDeviceName(): String? =
        prefs.getString(PREF_LAST_DEVICE_NAME, null)

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
        bleScanItemsByAddress.values
            .filter { it.source.contains("Paired", ignoreCase = true) || it.source.contains("Android", ignoreCase = true) }
            .forEach { item -> candidates += KnownMidiDevice(item.address, item.name) }
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
                val name = parts.getOrNull(1).orEmpty().trim()
                if (address.isBlank()) null else KnownMidiDevice(address, name.ifBlank { "Known MIDI device" })
            }
            .toList()
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
        val cleanName = name.replace('\t', ' ').replace('\n', ' ').trim().ifBlank { "MIDI device" }
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
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
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
        val connection = _state.value.connection
        if (!connection.connected) return
        if (!isAndroidMidiConnectionId(connection.address) && !isBluetoothEnabled()) return
        val port = midiInputPort ?: return
        val controls = appSettings.channelControls.normalizedControls()
        val now = System.nanoTime()
        try {
            for (channel in 0..15) {
                val control = controls[channel + 1] ?: MidiChannelControl(channel = channel + 1)
                val value = scaledMidiVolume(127, percent, control.volumePercent).toByte()
                val status = (0xB0 or channel).toByte()
                port.send(byteArrayOf(status, 7, value), 0, 3, now)
            }
            port.flush()
        } catch (_: Throwable) {
        }
    }

    private fun List<MidiChannelControl>.normalizedControls(): Map<Int, MidiChannelControl> =
        (1..16).associateWith { channel ->
            firstOrNull { it.channel == channel }?.copy(
                channel = channel,
                volumePercent = firstOrNull { it.channel == channel }?.volumePercent?.coerceIn(0, 100) ?: 100
            ) ?: MidiChannelControl(channel = channel)
        }

    private fun List<MidiChannelControl>.cacheSignature(): String =
        normalizedControls().values.joinToString("|") {
            "${it.channel}:${it.muted}:${it.solo}:${it.volumePercent.coerceIn(0, 100)}"
        }

    private fun scaledMidiVolume(rawValue: Int, masterPercent: Int, channelPercent: Int): Int {
        val scaled = rawValue.coerceIn(0, 127) *
            masterPercent.coerceIn(0, 100) *
            channelPercent.coerceIn(0, 100)
        return (scaled / 10_000).coerceIn(0, 127)
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
