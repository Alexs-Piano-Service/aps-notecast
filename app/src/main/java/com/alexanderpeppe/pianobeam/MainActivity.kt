package com.alexanderpeppe.pianobeam

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.alexanderpeppe.pianobeam.data.AppSettings
import com.alexanderpeppe.pianobeam.data.AppUiState
import com.alexanderpeppe.pianobeam.data.AppThemeMode
import com.alexanderpeppe.pianobeam.data.BleMidiDeviceItem
import com.alexanderpeppe.pianobeam.data.ExternalMidiSource
import com.alexanderpeppe.pianobeam.data.ImportUiState
import com.alexanderpeppe.pianobeam.data.KuhmannMidiResult
import com.alexanderpeppe.pianobeam.data.KuhmannSearchUiState
import com.alexanderpeppe.pianobeam.data.MAX_MIDI_LIBRARY_PAGE_SIZE
import com.alexanderpeppe.pianobeam.data.MidiChannelControl
import com.alexanderpeppe.pianobeam.data.MidiLibraryItem
import com.alexanderpeppe.pianobeam.data.MidiPlaylist
import com.alexanderpeppe.pianobeam.data.MIN_MIDI_LIBRARY_PAGE_SIZE
import com.alexanderpeppe.pianobeam.data.PlaybackChannelInfo
import com.alexanderpeppe.pianobeam.data.PlaybackMode
import com.alexanderpeppe.pianobeam.data.PlaybackUiState
import com.alexanderpeppe.pianobeam.data.VolumeControlMode
import com.alexanderpeppe.pianobeam.data.formatClockTime
import com.alexanderpeppe.pianobeam.data.formatDuration
import com.alexanderpeppe.pianobeam.data.instrumentOverridesForSong
import com.alexanderpeppe.pianobeam.data.withClearedSongInstrumentOverrides
import com.alexanderpeppe.pianobeam.data.withSongInstrumentOverride
import com.alexanderpeppe.pianobeam.midi.GeneralMidi
import com.alexanderpeppe.pianobeam.net.ApsNetworkStatus
import com.alexanderpeppe.pianobeam.reporting.BugReportClient
import com.alexanderpeppe.pianobeam.reporting.BugReportInput
import com.alexanderpeppe.pianobeam.reporting.CrashReportStore
import com.alexanderpeppe.pianobeam.settings.AppSettingsStore
import com.alexanderpeppe.pianobeam.service.NoteCastService
import com.alexanderpeppe.pianobeam.ui.settings.SettingsDialog
import com.alexanderpeppe.pianobeam.ui.theme.NoteCastTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private val midiImportMimeTypes = arrayOf(
    "audio/midi",
    "audio/x-midi",
    "audio/mid",
    "application/x-midi",
    "application/zip",
    "application/x-zip",
    "application/x-zip-compressed",
    "application/octet-stream",
    "*/*"
)

private const val MIDI_SEARCH_DEBOUNCE_MS = 140L

class MainActivity : ComponentActivity() {
    private var service by mutableStateOf<NoteCastService?>(null)
    private lateinit var settingsStore: AppSettingsStore
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as NoteCastService.LocalBinder).service()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReportStore.install(applicationContext)
        settingsStore = AppSettingsStore(this)
        val intent = Intent(this, NoteCastService::class.java)
        runCatching { startService(intent) }
        bindService(intent, connection, BIND_AUTO_CREATE)

        setContent {
            val appSettings by settingsStore.settings.collectAsState()

            NoteCastTheme(themeMode = appSettings.themeMode) {
                val baseDensity = LocalDensity.current
                val appDensity = remember(baseDensity, appSettings.fontScalePercent) {
                    Density(
                        density = baseDensity.density,
                        fontScale = baseDensity.fontScale * (appSettings.fontScalePercent.coerceIn(80, 110) / 100f)
                    )
                }
                CompositionLocalProvider(LocalDensity provides appDensity) {
                    var permissionsGranted by remember { mutableStateOf(hasAllRuntimePermissions()) }
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) {
                        permissionsGranted = hasAllRuntimePermissions()
                        service?.refreshKnownDevices()
                    }
                    val enableBluetoothLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) {
                        service?.refreshKnownDevices()
                    }
                    val pairingSettingsLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) {
                        service?.refreshKnownDevices()
                        if (appSettings.autoReconnectEnabled) service?.autoReconnectIfPossible()
                    }
                    val appSettingsLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) {}

                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val currentService = service
                        if (currentService == null) {
                            LoadingScreen()
                        } else {
                            NoteCastApp(
                                service = currentService,
                                permissionsGranted = permissionsGranted,
                                settings = appSettings,
                                onSettingsChange = settingsStore::updateSettings,
                                onRequestPermissions = {
                                    permissionLauncher.launch(runtimePermissionsToRequest())
                                },
                                onRequestBluetoothEnable = {
                                    if (!permissionsGranted) {
                                        permissionLauncher.launch(runtimePermissionsToRequest())
                                    } else {
                                        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                    }
                                },
                                onOpenBluetoothPairing = {
                                    if (!permissionsGranted) {
                                        permissionLauncher.launch(runtimePermissionsToRequest())
                                    } else {
                                        runCatching {
                                            pairingSettingsLauncher.launch(Intent(BLUETOOTH_PAIRING_SETTINGS_ACTION))
                                        }.onFailure {
                                            pairingSettingsLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                        }
                                    }
                                },
                                onOpenBatterySettings = {
                                    runCatching {
                                        appSettingsLauncher.launch(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:$packageName")
                                            }
                                        )
                                    }.onFailure {
                                        appSettingsLauncher.launch(Intent(Settings.ACTION_SETTINGS))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        service?.setAutoReconnectPausedForDevicePicker(false)
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }

    private fun bluetoothRuntimePermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun runtimePermissionsToRequest(): Array<String> {
        val permissions = startupPermissionsToVerify().toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions.distinct().toTypedArray()
    }

    private fun startupPermissionsToVerify(): List<String> =
        bluetoothRuntimePermissions() + networkPermissionsToVerify()

    private fun networkPermissionsToVerify(): List<String> =
        listOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

    private fun hasAllRuntimePermissions(): Boolean = startupPermissionsToVerify().all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val BLUETOOTH_PAIRING_SETTINGS_ACTION = "android.settings.BLUETOOTH_PAIRING_SETTINGS"
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LoadingIcon(
                modifier = Modifier.size(86.dp),
                contentDescription = "APS NoteCast logo"
            )
            CircularProgressIndicator()
            Text("Starting APS NoteCast")
        }
    }
}

@Composable
private fun LoadingIcon(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Image(
        painter = painterResource(R.drawable.loading_icon),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun AboutIcon(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Image(
        painter = painterResource(R.drawable.about_icon),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun BrandMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Image(
        painter = painterResource(R.drawable.app_header_logo),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun PrimaryLogoBanner(modifier: Modifier = Modifier) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Image(
        painter = painterResource(if (darkTheme) R.drawable.about_logo_dark else R.drawable.about_logo_light),
        contentDescription = "Alex's Piano Service logo",
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentScale = ContentScale.Fit
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteCastApp(
    service: NoteCastService,
    permissionsGranted: Boolean,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onOpenBluetoothPairing: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val context = LocalContext.current
    val state by service.state.collectAsState()
    var showConnector by rememberSaveable { mutableStateOf(false) }
    var showAppInfo by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showBugReport by rememberSaveable { mutableStateOf(false) }
    var showOverflowMenu by rememberSaveable { mutableStateOf(false) }
    var showWizard by rememberSaveable { mutableStateOf(true) }
    var diagnosticsText by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportFileId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedKind by rememberSaveable { mutableStateOf("file") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showReadingOverlay by remember { mutableStateOf(false) }
    val exportMidiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/midi")) { uri ->
        val itemId = pendingExportFileId
        if (uri != null && itemId != null) service.exportMidiFile(itemId, uri)
        pendingExportFileId = null
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) service.exportLibraryBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) service.restoreLibraryBackup(uri)
    }
    val showBatteryRecommendation = !settings.batteryRecommendationDismissed
    val dismissBatteryRecommendation = {
        onSettingsChange(settings.copy(batteryRecommendationDismissed = true))
    }

    LaunchedEffect(settings) {
        service.applySettings(settings)
    }
    LaunchedEffect(permissionsGranted) {
        service.refreshKnownDevices()
    }
    LaunchedEffect(showConnector) {
        service.setAutoReconnectPausedForDevicePicker(showConnector)
    }
    LaunchedEffect(permissionsGranted, settings.scanOnLaunch, settings.autoReconnectEnabled) {
        if (permissionsGranted && settings.scanOnLaunch) {
            if (settings.autoReconnectEnabled) delay(250L)
            val connection = service.state.value.connection
            if (!connection.connected && !connection.connecting && !connection.scanning) {
                service.startBleScan()
            }
        }
    }
    LaunchedEffect(permissionsGranted, settings.autoReconnectEnabled, settings.reconnectIntervalSeconds) {
        while (permissionsGranted && settings.autoReconnectEnabled) {
            service.autoReconnectIfPossible()
            delay(settings.reconnectIntervalSeconds.coerceIn(3, 60) * 1_000L)
        }
    }
    LaunchedEffect(state.connection.connected) {
        if (state.connection.connected) {
            showWizard = false
            showConnector = false
        }
    }
    LaunchedEffect(state.playback.currentItemId, state.files) {
        val playingItemId = state.playback.currentItemId
        if (playingItemId != null && state.files.any { it.id == playingItemId }) {
            selectedKind = "file"
            selectedId = playingItemId
        }
    }
    LaunchedEffect(state.playback.isPreparing, state.playback.currentItemId) {
        if (state.playback.isPreparing) {
            showReadingOverlay = false
            delay(550L)
            showReadingOverlay = true
        } else {
            showReadingOverlay = false
        }
    }
    LaunchedEffect(state.files, state.playlists) {
        val selectedStillExists = when (selectedKind) {
            "playlist" -> state.playlists.any { it.id == selectedId }
            else -> state.files.any { it.id == selectedId }
        }
        if (!selectedStillExists) {
            selectedKind = if (state.files.isNotEmpty()) "file" else "playlist"
            selectedId = state.files.firstOrNull()?.id ?: state.playlists.firstOrNull()?.id
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val titleText = if (maxWidth < 220.dp) "NoteCast" else "APS NoteCast"
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            BrandMark(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { showAppInfo = true },
                                contentDescription = "APS NoteCast"
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    titleText,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    state.lastMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    ConnectionPill(state = state, onClick = { showConnector = true })
                    IconButton(
                        onClick = { service.panic() },
                        enabled = state.connection.connected
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = RoundedCornerShape(0.dp),
                            color = if (state.connection.connected) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (state.connection.connected) {
                                MaterialTheme.colorScheme.onError
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showSettings = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("App info") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showAppInfo = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Report bug") },
                                leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showBugReport = true
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            TransportBar(
                state = state,
                selectedKind = selectedKind,
                selectedId = selectedId,
                onPlaySelection = {
                    when (selectedKind) {
                        "playlist" -> selectedId?.let { service.playPlaylist(it, shuffled = null) }
                        else -> selectedId?.let { service.playFile(it) }
                    }
                },
                service = service,
                settings = settings,
                onSettingsChange = onSettingsChange
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            AdaptiveHome(
                state = state,
                service = service,
                permissionsGranted = permissionsGranted,
                onRequestPermissions = onRequestPermissions,
                onRequestBluetoothEnable = onRequestBluetoothEnable,
                selectedKind = selectedKind,
                selectedId = selectedId,
                onSelectFile = {
                    selectedKind = "file"
                    selectedId = it
                },
                onSelectPlaylist = {
                    selectedKind = "playlist"
                    selectedId = it
                },
                onOpenConnection = { showConnector = true },
                onOpenBluetoothPairing = onOpenBluetoothPairing,
                onExportMidi = { item ->
                    pendingExportFileId = item.id
                    exportMidiLauncher.launch(item.originalName.ifBlank { "${item.title}.mid" })
                },
                onShareMidi = { itemId ->
                    service.shareMidiFileIntent(itemId)?.let { intent ->
                        context.startActivity(Intent.createChooser(intent, "Share MIDI"))
                    }
                },
                settings = settings,
                contentPadding = innerPadding
            )
            if (showReadingOverlay && state.playback.isPreparing) {
                ReadingMusicOverlay(
                    playback = state.playback,
                    onStop = { service.stopPlayback() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }

    if (showBatteryRecommendation) {
        BatteryRecommendationDialog(
            onOpenBatterySettings = {
                dismissBatteryRecommendation()
                onOpenBatterySettings()
            },
            onDismiss = dismissBatteryRecommendation
        )
    }

    if (showWizard && !state.connection.connected && !showBatteryRecommendation) {
        ConnectionWizardDialog(
            state = state,
            service = service,
            permissionsGranted = permissionsGranted,
            onRequestPermissions = onRequestPermissions,
            onRequestBluetoothEnable = onRequestBluetoothEnable,
            onOpenBluetoothPairing = onOpenBluetoothPairing,
            autoReconnectEnabled = settings.autoReconnectEnabled,
            onDismiss = {
                service.continueOffline()
                showWizard = false
            }
        )
    }

    if (showConnector) {
        ConnectionDialog(
            state = state,
            service = service,
            permissionsGranted = permissionsGranted,
            onRequestPermissions = onRequestPermissions,
            onRequestBluetoothEnable = onRequestBluetoothEnable,
            onOpenBluetoothPairing = onOpenBluetoothPairing,
            onDismiss = { showConnector = false }
        )
    }

    if (showAppInfo) {
        AppInfoDialog(onDismiss = { showAppInfo = false })
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            playlists = state.playlists,
            preferredDeviceName = state.connection.rememberedDeviceName,
            onSettingsChange = onSettingsChange,
            onForgetDevice = { service.forgetRememberedDevice() },
            onDiagnostics = { diagnosticsText = service.connectionDiagnostics() },
            onPlayChromaticScale = { velocity, noteLengthMs ->
                service.playDiagnosticChromaticScale(velocity, noteLengthMs)
            },
            onStopChromaticScale = {
                service.stopDiagnosticChromaticScale()
            },
            onSustainPedalChange = { pressed, playSustainedChord ->
                service.setPianoTestSustainPedal(pressed, playSustainedChord)
            },
            onBackupLibrary = { backupLauncher.launch("aps-notecast-library-backup.json") },
            onRestoreLibrary = { restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
            onDismiss = { showSettings = false }
        )
    }

    if (showBugReport) {
        BugReportDialog(
            context = context,
            state = state,
            settings = settings,
            service = service,
            onDismiss = { showBugReport = false }
        )
    }

    diagnosticsText?.let { diagnostics ->
        AlertDialog(
            onDismissRequest = { diagnosticsText = null },
            icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
            title = { Text("Connection diagnostics") },
            text = {
                ScrollableDialogColumn {
                    Text(diagnostics)
                }
            },
            confirmButton = {
                TextButton(onClick = { diagnosticsText = null }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun AdaptiveHome(
    state: AppUiState,
    service: NoteCastService,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    selectedKind: String,
    selectedId: String?,
    onSelectFile: (String) -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onOpenConnection: () -> Unit,
    onOpenBluetoothPairing: () -> Unit,
    onExportMidi: (MidiLibraryItem) -> Unit,
    onShareMidi: (String) -> Unit,
    settings: AppSettings,
    contentPadding: PaddingValues
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        val wide = maxWidth >= 780.dp
        LibraryPane(
            state = state,
            service = service,
            selectedKind = selectedKind,
            selectedId = selectedId,
            onSelectFile = onSelectFile,
            onSelectPlaylist = onSelectPlaylist,
            onOpenConnection = onOpenConnection,
            onExportMidi = onExportMidi,
            onShareMidi = onShareMidi,
            settings = settings,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (wide) 18.dp else 12.dp, vertical = if (wide) 14.dp else 10.dp)
        )
    }
}

@Composable
private fun LibraryPane(
    state: AppUiState,
    service: NoteCastService,
    selectedKind: String,
    selectedId: String?,
    onSelectFile: (String) -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onOpenConnection: () -> Unit,
    onExportMidi: (MidiLibraryItem) -> Unit,
    onShareMidi: (String) -> Unit,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        service.importMidiFiles(uris)
    }
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectedPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showRecordDialog by rememberSaveable { mutableStateOf(false) }
    var showKuhmannDialog by rememberSaveable { mutableStateOf(false) }
    var addFilesPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameFileId by rememberSaveable { mutableStateOf<String?>(null) }
    var renamePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var colorPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteFileId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDeleteSelected by rememberSaveable { mutableStateOf(false) }
    var playlistQuery by rememberSaveable { mutableStateOf("") }
    var midiFileSearchText by rememberSaveable { mutableStateOf("") }
    var appliedMidiFileQuery by rememberSaveable { mutableStateOf("") }
    var showMidiSearch by rememberSaveable { mutableStateOf(false) }
    var midiFilePageStart by rememberSaveable { mutableStateOf(0) }
    var selectedFileIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var midiFileDisplayModeName by rememberSaveable { mutableStateOf(MidiFileDisplayMode.Alphabetical.name) }
    val expandedPlaylists = remember { mutableStateMapOf<String, Boolean>() }
    val expandedMidiLetters = remember { mutableStateMapOf<String, Boolean>() }
    val midiLetterPageStarts = remember { mutableStateMapOf<String, Int>() }
    val playlistBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingFiles by remember { mutableStateOf(emptyList<MidiLibraryItem>()) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var paneOrigin by remember { mutableStateOf(Offset.Zero) }
    var listBounds by remember { mutableStateOf<Rect?>(null) }
    val gridState = rememberLazyListState()
    val libraryScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val filesById = remember(state.files) { state.files.associateBy { it.id } }
    val selectedFileIdSet = remember(selectedFileIds) { selectedFileIds.toSet() }
    val selectedFileItems = remember(state.files, selectedFileIdSet) { state.files.filter { it.id in selectedFileIdSet } }
    val selectionActive = selectedFileItems.isNotEmpty()
    var visibleMidiFiles by remember(state.files) { mutableStateOf(state.files) }
    val midiLibraryPageSize = settings.midiLibraryPageSize.coerceIn(
        MIN_MIDI_LIBRARY_PAGE_SIZE,
        MAX_MIDI_LIBRARY_PAGE_SIZE
    )
    val midiFileDisplayMode = MidiFileDisplayMode.entries
        .firstOrNull { it.name == midiFileDisplayModeName }
        ?: MidiFileDisplayMode.Alphabetical
    val midiSearchFiltering = midiFileSearchText.isNotBlank() && appliedMidiFileQuery != midiFileSearchText
    val forceFlatMidiList = midiFileSearchText.isNotBlank()
    val effectiveMidiFileDisplayMode = if (forceFlatMidiList) MidiFileDisplayMode.List else midiFileDisplayMode
    val sortedVisibleMidiFiles = remember(visibleMidiFiles) { visibleMidiFiles.sortedForMidiDisplay() }
    val midiDisplaySourceFiles = remember(visibleMidiFiles) { visibleMidiFiles }
    val normalizedMidiPageStart = midiFilePageStart.coercedPageStart(midiDisplaySourceFiles.size, midiLibraryPageSize)
    val displayedMidiFiles = remember(midiDisplaySourceFiles, normalizedMidiPageStart, midiLibraryPageSize) {
        midiDisplaySourceFiles
            .drop(normalizedMidiPageStart)
            .take(midiLibraryPageSize)
    }
    val midiDisplayStartNumber = if (displayedMidiFiles.isEmpty()) 0 else normalizedMidiPageStart + 1
    val midiDisplayEndNumber = normalizedMidiPageStart + displayedMidiFiles.size
    val midiFileGroups = remember(sortedVisibleMidiFiles, effectiveMidiFileDisplayMode) {
        if (effectiveMidiFileDisplayMode == MidiFileDisplayMode.Alphabetical) {
            midiFileLetterGroups(sortedVisibleMidiFiles)
        } else {
            emptyList()
        }
    }
    val allMidiLettersExpanded = midiFileGroups.all { group ->
        expandedMidiLetters[group.key] ?: group.defaultExpanded(midiLibraryPageSize)
    }
    val midiHeaderRangeStart = if (effectiveMidiFileDisplayMode == MidiFileDisplayMode.List) {
        midiDisplayStartNumber
    } else if (visibleMidiFiles.isNotEmpty()) {
        1
    } else {
        0
    }
    val midiHeaderRangeEnd = if (effectiveMidiFileDisplayMode == MidiFileDisplayMode.List) {
        midiDisplayEndNumber
    } else {
        visibleMidiFiles.size
    }
    val showPlaylistSearch = state.playlists.size > 10
    val visiblePlaylists = remember(state.playlists, playlistQuery, filesById, showPlaylistSearch) {
        if (!showPlaylistSearch || playlistQuery.isBlank()) {
            state.playlists
        } else {
            state.playlists.filter { playlist -> playlist.matchesPlaylistSearch(playlistQuery, filesById) }
        }
    }
    val midiHeaderItemIndex = remember(
        state.importState.importing,
        state.files.isEmpty(),
        state.playlists.isEmpty(),
        showPlaylistSearch,
        visiblePlaylists.size
    ) {
        var index = 1
        if (state.importState.importing) index += 1
        if (state.files.isEmpty() && state.playlists.isEmpty()) index += 1
        if (state.playlists.isNotEmpty()) {
            index += 1
            if (showPlaylistSearch) index += 1
            index += if (visiblePlaylists.isEmpty()) 1 else visiblePlaylists.size
        }
        index
    }
    val showScrollToTopButton by remember {
        derivedStateOf {
            midiFilePageStart > 0 ||
                midiLetterPageStarts.values.any { it > 0 } ||
                gridState.firstVisibleItemIndex > 2
        }
    }

    LaunchedEffect(state.files, midiFileSearchText) {
        val filesSnapshot = state.files
        val query = midiFileSearchText
        if (query.isBlank()) {
            appliedMidiFileQuery = ""
            visibleMidiFiles = filesSnapshot
        } else {
            delay(MIDI_SEARCH_DEBOUNCE_MS)
            val result = withContext(Dispatchers.Default) {
                filesSnapshot.filterMidiFiles(query)
            }
            appliedMidiFileQuery = query
            visibleMidiFiles = result
        }
    }

    LaunchedEffect(appliedMidiFileQuery, visibleMidiFiles.size, midiLibraryPageSize) {
        midiFilePageStart = 0
        midiLetterPageStarts.clear()
    }

    LaunchedEffect(appliedMidiFileQuery) {
        if (appliedMidiFileQuery.isNotBlank()) gridState.scrollToItem(0)
    }

    LaunchedEffect(state.files) {
        val validIds = state.files.map { it.id }.toSet()
        selectedFileIds = selectedFileIds.filter { it in validIds }
    }

    LaunchedEffect(midiFileGroups, midiLibraryPageSize) {
        val validLetters = midiFileGroups.map { it.key }.toSet()
        expandedMidiLetters.keys.filterNot { it in validLetters }.forEach { expandedMidiLetters.remove(it) }
        midiLetterPageStarts.keys.filterNot { it in validLetters }.forEach { midiLetterPageStarts.remove(it) }
        midiFileGroups.forEach { group ->
            val currentStart = midiLetterPageStarts[group.key] ?: 0
            val cleanStart = currentStart.coercedPageStart(group.files.size, midiLibraryPageSize)
            if (currentStart != cleanStart) {
                midiLetterPageStarts[group.key] = cleanStart
            }
        }
    }

    LaunchedEffect(showPlaylistSearch, playlistQuery, visiblePlaylists) {
        if (!showPlaylistSearch) playlistQuery = ""
        val visibleIds = visiblePlaylists.map { it.id }.toSet()
        playlistBounds.keys.filterNot { it in visibleIds }.forEach { playlistBounds.remove(it) }
    }

    LaunchedEffect(draggingFiles.isNotEmpty()) {
        if (draggingFiles.isEmpty()) return@LaunchedEffect
        while (true) {
            val bounds = listBounds
            val position = dragPosition
            if (bounds != null && position != null) {
                val edgeSize = 96f.coerceAtMost(bounds.height / 3f)
                val maxStep = 28f
                val scrollDelta = when {
                    position.y < bounds.top + edgeSize -> {
                        val pressure = ((bounds.top + edgeSize - position.y) / edgeSize).coerceIn(0f, 1f)
                        -maxStep * pressure
                    }
                    position.y > bounds.bottom - edgeSize -> {
                        val pressure = ((position.y - (bounds.bottom - edgeSize)) / edgeSize).coerceIn(0f, 1f)
                        maxStep * pressure
                    }
                    else -> 0f
                }
                if (scrollDelta != 0f) gridState.scrollBy(scrollDelta)
            }
            delay(16L)
        }
    }

    fun toggleFileSelection(itemId: String) {
        selectedFileIds = if (itemId in selectedFileIdSet) {
            selectedFileIds.filterNot { it == itemId }
        } else {
            selectedFileIds + itemId
        }
    }

    fun finishDrag() {
        val files = draggingFiles
        val position = dragPosition
        if (files.isNotEmpty() && position != null) {
            val targetPlaylist = playlistBounds.entries.firstOrNull { it.value.contains(position) }?.key
            if (targetPlaylist != null) service.addToPlaylist(targetPlaylist, files.map { it.id })
        }
        draggingFiles = emptyList()
        dragPosition = null
    }

    fun showMidiPage(startIndex: Int) {
        midiFilePageStart = startIndex.coercedPageStart(midiDisplaySourceFiles.size, midiLibraryPageSize)
        libraryScope.launch { gridState.scrollToItem(midiHeaderItemIndex) }
    }

    fun midiLetterHeaderIndex(letterKey: String): Int {
        var index = midiHeaderItemIndex + 1
        if (showMidiSearch || midiFileSearchText.isNotBlank()) index += 1
        if (visibleMidiFiles.isEmpty()) return midiHeaderItemIndex
        midiFileGroups.forEach { group ->
            if (group.key == letterKey) return index
            index += 1
            val expanded = expandedMidiLetters[group.key] ?: group.defaultExpanded(midiLibraryPageSize)
            if (expanded) {
                val pageStart = (midiLetterPageStarts[group.key] ?: 0)
                    .coercedPageStart(group.files.size, midiLibraryPageSize)
                index += group.files.drop(pageStart).take(midiLibraryPageSize).size
                if (group.files.size > midiLibraryPageSize) index += 1
            }
        }
        return midiHeaderItemIndex
    }

    fun showMidiLetterPage(letterKey: String, startIndex: Int) {
        val group = midiFileGroups.firstOrNull { it.key == letterKey } ?: return
        midiLetterPageStarts[letterKey] = startIndex.coercedPageStart(group.files.size, midiLibraryPageSize)
        libraryScope.launch { gridState.scrollToItem(midiLetterHeaderIndex(letterKey)) }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { paneOrigin = it.localToRoot(Offset.Zero) }
        ) {
            LazyColumn(
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { listBounds = it.boundsInRoot() },
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    LibraryHero(
                        fileCount = state.files.size,
                        playlistCount = state.playlists.size,
                        onImport = { importLauncher.launch(midiImportMimeTypes) },
                        onBrowseKuhmann = { showKuhmannDialog = true },
                        onRecord = { showRecordDialog = true },
                        onCreatePlaylist = { showPlaylistDialog = true },
                        onOpenConnection = onOpenConnection
                    )
                }
                if (state.importState.importing) {
                    item {
                        ImportProgressCard(importState = state.importState)
                    }
                }

                if (state.files.isEmpty() && state.playlists.isEmpty()) {
                    item {
                        EmptyLibraryCard(
                            onImport = { importLauncher.launch(midiImportMimeTypes) },
                            onCreatePlaylist = { showPlaylistDialog = true }
                        )
                    }
                }

                if (state.playlists.isNotEmpty()) {
                    item { SectionLabel("Playlists") }
                    if (showPlaylistSearch) {
                        item {
                            PlaylistSearchField(
                                query = playlistQuery,
                                onQueryChange = { playlistQuery = it },
                                playlistCount = state.playlists.size,
                                visibleCount = visiblePlaylists.size
                            )
                        }
                    }
                    if (visiblePlaylists.isEmpty()) {
                        item {
                            Text(
                                "No matching playlists.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                            )
                        }
                    }
                    items(visiblePlaylists, key = { it.id }) { playlist ->
                        DisposableEffect(playlist.id) {
                            onDispose { playlistBounds.remove(playlist.id) }
                        }
                        val expanded = expandedPlaylists[playlist.id] ?: false
                        val highlighted = dragPosition?.let { playlistBounds[playlist.id]?.contains(it) } == true
                        PlaylistFolder(
                            playlist = playlist,
                            filesById = filesById,
                            selected = selectedKind == "playlist" && selectedId == playlist.id,
                            expanded = expanded,
                            highlighted = highlighted,
                            connected = state.connection.connected,
                            preparing = state.playback.isPreparing &&
                                state.playback.playlistName == playlist.name &&
                                state.playback.currentItemId in playlist.itemIds,
                            onSelect = { onSelectPlaylist(playlist.id) },
                            onToggle = { expandedPlaylists[playlist.id] = !expanded },
                            onAddFiles = { addFilesPlaylistId = playlist.id },
                            onPlaySequential = {
                                onSelectPlaylist(playlist.id)
                                service.playPlaylist(playlist.id, shuffled = false)
                            },
                            onPlayShuffle = {
                                onSelectPlaylist(playlist.id)
                                service.playPlaylist(playlist.id, shuffled = true)
                            },
                            onRename = { renamePlaylistId = playlist.id },
                            onColor = { colorPlaylistId = playlist.id },
                            onClone = { service.duplicatePlaylist(playlist.id) },
                            onDelete = { deletePlaylistId = playlist.id },
                            onRemove = { index -> service.removeFromPlaylist(playlist.id, index) },
                            onMove = { index, direction -> service.movePlaylistItem(playlist.id, index, direction) },
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                playlistBounds[playlist.id] = coordinates.boundsInRoot()
                            }
                        )
                    }
                }

                if (state.files.isNotEmpty()) {
                    item {
                        MidiFilesHeader(
                            mode = effectiveMidiFileDisplayMode,
                            allLetterGroupsExpanded = allMidiLettersExpanded,
                            searchExpanded = showMidiSearch || midiFileSearchText.isNotBlank(),
                            queryActive = midiFileSearchText.isNotBlank(),
                            totalCount = visibleMidiFiles.size,
                            rangeStart = midiHeaderRangeStart,
                            rangeEnd = midiHeaderRangeEnd,
                            forceFlatList = forceFlatMidiList,
                            onModeChange = { midiFileDisplayModeName = it.name },
                            onToggleLetterGroups = {
                                val nextExpanded = !allMidiLettersExpanded
                                midiFileGroups.forEach { group ->
                                    expandedMidiLetters[group.key] = nextExpanded
                                }
                                if (!nextExpanded) midiLetterPageStarts.clear()
                            },
                            onToggleSearch = { showMidiSearch = !showMidiSearch }
                        )
                    }
                    if (showMidiSearch || midiFileSearchText.isNotBlank()) {
                        item {
                            MidiSearchField(
                                query = midiFileSearchText,
                                onQueryChange = {
                                    midiFilePageStart = 0
                                    midiFileSearchText = it
                                },
                                totalCount = state.files.size,
                                visibleCount = visibleMidiFiles.size,
                                filtering = midiSearchFiltering
                            )
                        }
                    }
                    if (visibleMidiFiles.isEmpty()) {
                        item {
                            Text(
                                "No matching MIDI files.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                            )
                        }
                    } else if (effectiveMidiFileDisplayMode == MidiFileDisplayMode.Alphabetical) {
                        midiFileGroups.forEach { group ->
                            item(key = "midi-letter-${group.key}") {
                                val expanded = expandedMidiLetters[group.key] ?: group.defaultExpanded(midiLibraryPageSize)
                                MidiLetterHeader(
                                    title = group.title,
                                    count = group.files.size,
                                    expanded = expanded,
                                    onToggle = {
                                        val nextExpanded = !expanded
                                        expandedMidiLetters[group.key] = nextExpanded
                                        if (!nextExpanded) midiLetterPageStarts.remove(group.key)
                                    }
                                )
                            }
                            val expanded = expandedMidiLetters[group.key] ?: group.defaultExpanded(midiLibraryPageSize)
                            if (expanded) {
                                val letterPageStart = (midiLetterPageStarts[group.key] ?: 0)
                                    .coercedPageStart(group.files.size, midiLibraryPageSize)
                                val letterPageFiles = group.files
                                    .drop(letterPageStart)
                                    .take(midiLibraryPageSize)
                                items(
                                    letterPageFiles,
                                    key = { "midi-file-${it.id}" },
                                    contentType = { "midi-file" }
                                ) { midiItem ->
                                    MidiFileRow(
                                        item = midiItem,
                                        selected = selectedKind == "file" && selectedId == midiItem.id,
                                        multiSelected = midiItem.id in selectedFileIdSet,
                                        selectionActive = selectionActive,
                                        connected = state.connection.connected,
                                        preparing = state.playback.isPreparing && state.playback.currentItemId == midiItem.id,
                                        playing = state.playback.isPlaying && state.playback.currentItemId == midiItem.id,
                                        paused = state.playback.isPaused && state.playback.currentItemId == midiItem.id,
                                        onSelect = { onSelectFile(midiItem.id) },
                                        onToggleSelected = { toggleFileSelection(midiItem.id) },
                                        onPlay = {
                                            onSelectFile(midiItem.id)
                                            service.playFile(midiItem.id)
                                        },
                                        onPause = { service.pausePlayback() },
                                        onResume = { service.resumePlayback() },
                                        onRename = { renameFileId = midiItem.id },
                                        onExport = { onExportMidi(midiItem) },
                                        onShare = { onShareMidi(midiItem.id) },
                                        onDelete = { deleteFileId = midiItem.id },
                                        onDragStart = { file, rootOffset ->
                                            draggingFiles = if (file.id in selectedFileIdSet && selectedFileItems.isNotEmpty()) {
                                                selectedFileItems
                                            } else {
                                                listOf(file)
                                            }
                                            dragPosition = rootOffset
                                        },
                                        onDrag = { delta -> dragPosition = (dragPosition ?: Offset.Zero) + delta },
                                        onDragEnd = ::finishDrag,
                                        onDragCancel = {
                                            draggingFiles = emptyList()
                                            dragPosition = null
                                        }
                                    )
                                }
                                if (group.files.size > midiLibraryPageSize) {
                                    item(key = "midi-letter-page-${group.key}") {
                                        MidiFilePagingFooter(
                                            rangeStart = letterPageStart + 1,
                                            rangeEnd = letterPageStart + letterPageFiles.size,
                                            totalCount = group.files.size,
                                            canPageBackward = letterPageStart > 0,
                                            canPageForward = letterPageStart + letterPageFiles.size < group.files.size,
                                            onPrevious = {
                                                showMidiLetterPage(group.key, letterPageStart - midiLibraryPageSize)
                                            },
                                            onNext = {
                                                showMidiLetterPage(group.key, letterPageStart + midiLibraryPageSize)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(
                            displayedMidiFiles,
                            key = { "midi-file-${it.id}" },
                            contentType = { "midi-file" }
                        ) { midiItem ->
                            MidiFileRow(
                                item = midiItem,
                                selected = selectedKind == "file" && selectedId == midiItem.id,
                                multiSelected = midiItem.id in selectedFileIdSet,
                                selectionActive = selectionActive,
                                connected = state.connection.connected,
                                preparing = state.playback.isPreparing && state.playback.currentItemId == midiItem.id,
                                playing = state.playback.isPlaying && state.playback.currentItemId == midiItem.id,
                                paused = state.playback.isPaused && state.playback.currentItemId == midiItem.id,
                                onSelect = { onSelectFile(midiItem.id) },
                                onToggleSelected = { toggleFileSelection(midiItem.id) },
                                onPlay = {
                                    onSelectFile(midiItem.id)
                                    service.playFile(midiItem.id)
                                },
                                onPause = { service.pausePlayback() },
                                onResume = { service.resumePlayback() },
                                onRename = { renameFileId = midiItem.id },
                                onExport = { onExportMidi(midiItem) },
                                onShare = { onShareMidi(midiItem.id) },
                                onDelete = { deleteFileId = midiItem.id },
                                onDragStart = { file, rootOffset ->
                                    draggingFiles = if (file.id in selectedFileIdSet && selectedFileItems.isNotEmpty()) {
                                        selectedFileItems
                                    } else {
                                        listOf(file)
                                    }
                                    dragPosition = rootOffset
                                },
                                onDrag = { delta -> dragPosition = (dragPosition ?: Offset.Zero) + delta },
                                onDragEnd = ::finishDrag,
                                onDragCancel = {
                                    draggingFiles = emptyList()
                                    dragPosition = null
                                }
                            )
                        }
                    }
                    if (effectiveMidiFileDisplayMode == MidiFileDisplayMode.List &&
                        visibleMidiFiles.size > midiLibraryPageSize
                    ) {
                        item {
                            MidiFilePagingFooter(
                                rangeStart = midiDisplayStartNumber,
                                rangeEnd = midiDisplayEndNumber,
                                totalCount = visibleMidiFiles.size,
                                canPageBackward = normalizedMidiPageStart > 0,
                                canPageForward = midiDisplayEndNumber < visibleMidiFiles.size,
                                onPrevious = { showMidiPage(normalizedMidiPageStart - midiLibraryPageSize) },
                                onNext = { showMidiPage(normalizedMidiPageStart + midiLibraryPageSize) }
                            )
                        }
                    }
                }
            }
            if (selectionActive) {
                MidiSelectionToolbar(
                    count = selectedFileItems.size,
                    onCreatePlaylist = { showSelectedPlaylistDialog = true },
                    onDelete = { confirmDeleteSelected = true },
                    onClear = { selectedFileIds = emptyList() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(10.dp)
                        .fillMaxWidth()
                )
            }

            if (showScrollToTopButton && draggingFiles.isEmpty()) {
                FloatingActionButton(
                    onClick = {
                        midiFilePageStart = 0
                        midiLetterPageStarts.clear()
                        libraryScope.launch { gridState.scrollToItem(0) }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 14.dp, bottom = if (selectionActive) 82.dp else 14.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.ExpandLess, contentDescription = "Scroll to top")
                }
            }

            if (draggingFiles.isNotEmpty()) {
                val position = dragPosition ?: Offset.Zero
                val previewWidth = with(density) {
                    ((listBounds?.width ?: 420f) - 20f).coerceIn(280f, 520f).toDp()
                }
                DraggedMidiFilePreview(
                    items = draggingFiles,
                    modifier = Modifier
                        .width(previewWidth)
                        .graphicsLayer {
                            translationX = position.x - paneOrigin.x + 12f
                            translationY = position.y - paneOrigin.y + 12f
                            alpha = 0.94f
                            scaleX = 0.99f
                            scaleY = 0.99f
                        }
                )
            }
        }
    }

    if (showPlaylistDialog) {
        AddPlaylistDialog(
            onDismiss = { showPlaylistDialog = false },
            onCreate = {
                service.createPlaylist(it)
                showPlaylistDialog = false
            }
        )
    }

    if (showSelectedPlaylistDialog) {
        AddPlaylistDialog(
            title = "Playlist from selection",
            onDismiss = { showSelectedPlaylistDialog = false },
            onCreate = {
                service.createPlaylist(it, selectedFileIds)
                selectedFileIds = emptyList()
                showSelectedPlaylistDialog = false
            }
        )
    }

    if (showRecordDialog) {
        RecordDialog(
            state = state,
            service = service,
            settings = settings,
            onDismiss = { showRecordDialog = false }
        )
    }

    if (showKuhmannDialog) {
        KuhmannMidiDialog(
            state = state.kuhmann,
            playback = state.playback,
            service = service,
            onDismiss = { showKuhmannDialog = false }
        )
    }

    addFilesPlaylistId?.let { playlistId ->
        state.playlists.firstOrNull { it.id == playlistId }?.let { playlist ->
            AddFilesToPlaylistDialog(
                playlist = playlist,
                files = state.files,
                onDismiss = { addFilesPlaylistId = null },
                onAdd = { itemIds ->
                    service.addToPlaylist(playlist.id, itemIds)
                    addFilesPlaylistId = null
                }
            )
        } ?: run { addFilesPlaylistId = null }
    }

    renameFileId?.let { itemId ->
        state.files.firstOrNull { it.id == itemId }?.let { item ->
            RenameDialog(
                title = "Rename MIDI",
                initialName = item.title,
                onDismiss = { renameFileId = null },
                onSave = {
                    service.renameMidiFile(item.id, it)
                    renameFileId = null
                }
            )
        } ?: run { renameFileId = null }
    }

    renamePlaylistId?.let { playlistId ->
        state.playlists.firstOrNull { it.id == playlistId }?.let { playlist ->
            RenameDialog(
                title = "Rename playlist",
                initialName = playlist.name,
                onDismiss = { renamePlaylistId = null },
                onSave = {
                    service.renamePlaylist(playlist.id, it)
                    renamePlaylistId = null
                }
            )
        } ?: run { renamePlaylistId = null }
    }

    colorPlaylistId?.let { playlistId ->
        state.playlists.firstOrNull { it.id == playlistId }?.let { playlist ->
            PlaylistColorDialog(
                playlist = playlist,
                onDismiss = { colorPlaylistId = null },
                onSelectColor = { colorHex ->
                    service.setPlaylistColor(playlist.id, colorHex)
                    colorPlaylistId = null
                }
            )
        } ?: run { colorPlaylistId = null }
    }

    deleteFileId?.let { itemId ->
        state.files.firstOrNull { it.id == itemId }?.let { item ->
            ConfirmDialog(
                title = "Delete MIDI?",
                message = "Remove ${item.title} from APS NoteCast?",
                confirmLabel = "Delete",
                onDismiss = { deleteFileId = null },
                onConfirm = {
                    service.deleteMidiFile(item.id)
                    deleteFileId = null
                }
            )
        } ?: run { deleteFileId = null }
    }

    deletePlaylistId?.let { playlistId ->
        state.playlists.firstOrNull { it.id == playlistId }?.let { playlist ->
            ConfirmDialog(
                title = "Delete playlist?",
                message = "Remove ${playlist.name}? MIDI files stay in the library.",
                confirmLabel = "Delete",
                onDismiss = { deletePlaylistId = null },
                onConfirm = {
                    service.deletePlaylist(playlist.id)
                    deletePlaylistId = null
                }
            )
        } ?: run { deletePlaylistId = null }
    }

    if (confirmDeleteSelected) {
        ConfirmDialog(
            title = "Delete selected MIDI?",
            message = "Remove ${selectedFileItems.size} MIDI file${if (selectedFileItems.size == 1) "" else "s"} from APS NoteCast? They will also be removed from playlists.",
            confirmLabel = "Delete",
            onDismiss = { confirmDeleteSelected = false },
            onConfirm = {
                service.deleteMidiFiles(selectedFileIds)
                selectedFileIds = emptyList()
                confirmDeleteSelected = false
            }
        )
    }
}

@Composable
private fun LibraryHero(
    fileCount: Int,
    playlistCount: Int,
    onImport: () -> Unit,
    onBrowseKuhmann: () -> Unit,
    onRecord: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenConnection: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "$fileCount files - $playlistCount playlists",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = onOpenConnection) {
                    Icon(Icons.Default.Bluetooth, contentDescription = "Open connection")
                }
            }
            BoxWithConstraints {
                val veryNarrow = maxWidth < 300.dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LibraryActionButton(Icons.Default.UploadFile, if (veryNarrow) "Add" else "Add MIDI", onImport, Modifier.weight(1f))
                    LibraryActionButton(Icons.Default.Search, if (veryNarrow) "Web" else "External", onBrowseKuhmann, Modifier.weight(1f))
                    LibraryActionButton(
                        Icons.Default.FiberManualRecord,
                        if (veryNarrow) "Record" else "Record MIDI",
                        onRecord,
                        Modifier.weight(1f),
                        inviting = true
                    )
                    LibraryActionButton(
                        Icons.Default.CreateNewFolder,
                        if (veryNarrow) "Playlist" else "New Playlist",
                        onCreatePlaylist,
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportProgressCard(importState: ImportUiState) {
    val total = importState.totalItems.coerceAtLeast(1)
    val processed = importState.processedItems.coerceIn(0, total)
    val progress = processed.toFloat() / total.toFloat()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LoadingIndicator(contentDescription = importState.message.ifBlank { "Importing MIDI files" })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Importing MIDI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${importState.processedItems}/${importState.totalItems} items - ${importState.importedFiles} files imported" +
                            if (importState.failedItems > 0) " - ${importState.failedItems} failed" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LibraryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inviting: Boolean = false
) {
    val colors = if (inviting) {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    } else {
        ButtonDefaults.filledTonalButtonColors()
    }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = colors,
        contentPadding = PaddingValues(horizontal = 6.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AppInfoDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AboutIcon(modifier = Modifier.size(96.dp), contentDescription = "APS NoteCast") },
        title = {
            Text(
                "APS NoteCast",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            ScrollableDialogColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryLogoBanner()
                InfoLine("Version", BuildConfig.VERSION_NAME)
                InfoLine("Built for", "Bluetooth LE MIDI or USB MIDI playback to compatible player-piano adapters")
                InfoLine("Cost", "Free and open-source. No subscriptions, in-app purchases, ads, or paid music catalog.")
                InfoLine("Library", "Import MIDI files, use bundled Mutopia demos, create playlists, or record MIDI input")
                InfoLine("External MIDI", "APS NoteCast can link to external MIDI sources for personal/noncommercial use. Rights vary by source.")
                InfoLine("Playback", "Sequence or shuffle playlists with pause, stop, skip, panic, and channel volume")
                InfoLine("Safety", "Stop and Panic send pedal-off, all-notes-off, reset controllers, and all-sound-off on all MIDI channels")
                InfoLine(
                    stringResource(R.string.developed_by),
                    "${stringResource(R.string.company_name)}\n${stringResource(R.string.company_address)}"
                )
                InfoLink(
                    label = stringResource(R.string.website),
                    value = stringResource(R.string.website_url),
                    onClick = { uriHandler.openUri("https://www.alexanderpeppe.com") }
                )
                InfoLink(
                    label = stringResource(R.string.disclaimer),
                    value = stringResource(R.string.disclaimer_url),
                    onClick = { uriHandler.openUri("https://www.alexanderpeppe.com/disclaimer/") }
                )
                InfoLink(
                    label = stringResource(R.string.dmca_policy),
                    value = stringResource(R.string.dmca_policy_url),
                    onClick = { uriHandler.openUri("https://www.alexanderpeppe.com/dmca-policy/") }
                )
                InfoLink(
                    label = stringResource(R.string.privacy_policy),
                    value = stringResource(R.string.privacy_policy_url),
                    onClick = { uriHandler.openUri("https://www.alexanderpeppe.com/privacy-policy/") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoLink(label: String, value: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(
            value,
            modifier = Modifier.clickable(onClick = onClick),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ScrollableDialogColumn(
    modifier: Modifier = Modifier,
    maxHeight: Dp = 360.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier = modifier.heightIn(max = maxHeight)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(scrollState)
                .padding(end = if (scrollState.maxValue > 0) 10.dp else 0.dp),
            verticalArrangement = verticalArrangement,
            content = content
        )
        DialogScrollThumb(
            scrollState = scrollState,
            maxHeight = maxHeight,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun DialogScrollThumb(
    scrollState: ScrollState,
    maxHeight: Dp,
    modifier: Modifier = Modifier
) {
    if (scrollState.maxValue <= 0) return
    val density = LocalDensity.current
    val viewportPx = with(density) { maxHeight.toPx() }
    val contentPx = viewportPx + scrollState.maxValue
    val minThumbPx = with(density) { 32.dp.toPx() }
    val thumbHeightPx = (viewportPx * viewportPx / contentPx).coerceAtLeast(minThumbPx)
    val maxOffsetPx = (viewportPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetPx = maxOffsetPx * (scrollState.value.toFloat() / scrollState.maxValue.toFloat())

    Surface(
        modifier = modifier
            .width(3.dp)
            .height(with(density) { thumbHeightPx.toDp() })
            .graphicsLayer { translationY = thumbOffsetPx },
        shape = RoundedCornerShape(2.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    ) {}
}

@Composable
private fun BugReportDialog(
    context: Context,
    state: AppUiState,
    settings: AppSettings,
    service: NoteCastService,
    onDismiss: () -> Unit
) {
    val pendingCrash = remember(context) { CrashReportStore.pendingCrash(context) }
    val scope = rememberCoroutineScope()
    var summary by rememberSaveable { mutableStateOf(if (pendingCrash.isNotBlank()) "Crash in APS NoteCast" else "") }
    var description by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var includeLogs by rememberSaveable { mutableStateOf(true) }
    var sending by rememberSaveable { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text("Report a Bug") },
        text = {
            ScrollableDialogColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tell us what happened and what you expected instead.")
                Text(
                    "The report includes app and Android details. Logs are optional and may include recent app events and MIDI device names.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (pendingCrash.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            "A crash from the previous run will be attached automatically.",
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it.take(300) },
                    label = { Text("Short summary") },
                    enabled = !sending && !sent,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(12_000) },
                    label = { Text("What happened?") },
                    enabled = !sending && !sent,
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it.take(300) },
                    label = { Text("Optional email or contact") },
                    enabled = !sending && !sent,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !sending && !sent) { includeLogs = !includeLogs },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeLogs,
                        onCheckedChange = { includeLogs = it },
                        enabled = !sending && !sent
                    )
                    Text("Include recent app events", style = MaterialTheme.typography.bodyMedium)
                }
                feedback?.let {
                    Text(
                        it,
                        color = if (sent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (sent) {
                        onDismiss()
                    } else {
                        scope.launch {
                            sending = true
                            feedback = "Sending bug report..."
                            val diagnostics = service.connectionDiagnostics()
                            runCatching {
                                BugReportClient.submit(
                                    context = context.applicationContext,
                                    input = BugReportInput(
                                        summary = summary,
                                        description = description,
                                        contact = contact,
                                        includeLogs = includeLogs
                                    ),
                                    state = state,
                                    settings = settings,
                                    diagnostics = diagnostics
                                )
                            }.onSuccess { result ->
                                sent = true
                                feedback = if (result.duplicate) {
                                    "Bug report received already. Reference: ${result.reportId.take(8)}"
                                } else {
                                    "Bug report sent. Reference: ${result.reportId.take(8)}"
                                }
                            }.onFailure { throwable ->
                                feedback = ApsNetworkStatus.userMessage(context, throwable)
                            }
                            sending = false
                        }
                    }
                },
                enabled = sent || (!sending && summary.isNotBlank())
            ) {
                Text(
                    when {
                        sent -> "Done"
                        sending -> "Sending"
                        else -> "Send Report"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) {
                Text(if (sent) "Close" else "Cancel")
            }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
    )
}

private enum class MidiFileDisplayMode {
    Alphabetical,
    List
}

@Composable
private fun MidiFilesHeader(
    mode: MidiFileDisplayMode,
    allLetterGroupsExpanded: Boolean,
    searchExpanded: Boolean,
    queryActive: Boolean,
    totalCount: Int,
    rangeStart: Int,
    rangeEnd: Int,
    forceFlatList: Boolean,
    onModeChange: (MidiFileDisplayMode) -> Unit,
    onToggleLetterGroups: () -> Unit,
    onToggleSearch: () -> Unit
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, start = 4.dp, end = 4.dp, bottom = 2.dp)
    ) {
        if (maxWidth < 360.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MidiFilesTitle(
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
                    totalCount = totalCount,
                    modifier = Modifier.fillMaxWidth()
                )
                MidiFileDisplayModeControl(
                    mode = mode,
                    allLetterGroupsExpanded = allLetterGroupsExpanded,
                    forceFlatList = forceFlatList,
                    onModeChange = onModeChange,
                    onToggleLetterGroups = onToggleLetterGroups,
                    searchExpanded = searchExpanded,
                    queryActive = queryActive,
                    onToggleSearch = onToggleSearch,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MidiFilesTitle(
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
                    totalCount = totalCount,
                    modifier = Modifier.weight(1f)
                )
                MidiFileDisplayModeControl(
                    mode = mode,
                    allLetterGroupsExpanded = allLetterGroupsExpanded,
                    forceFlatList = forceFlatList,
                    onModeChange = onModeChange,
                    onToggleLetterGroups = onToggleLetterGroups,
                    searchExpanded = searchExpanded,
                    queryActive = queryActive,
                    onToggleSearch = onToggleSearch,
                    modifier = Modifier.widthIn(min = 226.dp, max = 280.dp)
                )
            }
        }
    }
}

@Composable
private fun MidiFilesTitle(
    rangeStart: Int,
    rangeEnd: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            "MIDI files",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (rangeStart > 0 && (rangeStart != 1 || rangeEnd != totalCount)) {
            Text(
                "$rangeStart-$rangeEnd of $totalCount shown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MidiFileDisplayModeControl(
    mode: MidiFileDisplayMode,
    allLetterGroupsExpanded: Boolean,
    forceFlatList: Boolean,
    onModeChange: (MidiFileDisplayMode) -> Unit,
    onToggleLetterGroups: () -> Unit,
    searchExpanded: Boolean,
    queryActive: Boolean,
    onToggleSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alphabeticalSelected = mode == MidiFileDisplayMode.Alphabetical
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (alphabeticalSelected) {
                MidiLetterGroupToggleButton(
                    allLetterGroupsExpanded = allLetterGroupsExpanded,
                    onToggleLetterGroups = onToggleLetterGroups
                )
            }
            MidiSearchToggleButton(
                active = searchExpanded || queryActive,
                onClick = onToggleSearch
            )
            MidiFileDisplayModeSegment(
                label = "Alphabetical",
                selected = alphabeticalSelected && !forceFlatList,
                enabled = !forceFlatList,
                onClick = { if (!forceFlatList) onModeChange(MidiFileDisplayMode.Alphabetical) },
                modifier = Modifier.weight(1f)
            )
            MidiFileDisplayModeSegment(
                label = "All Songs",
                selected = mode == MidiFileDisplayMode.List,
                onClick = { onModeChange(MidiFileDisplayMode.List) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MidiSearchToggleButton(
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Search,
                contentDescription = if (active) "Hide MIDI search" else "Search MIDI files",
                modifier = Modifier.size(20.dp),
                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MidiLetterGroupToggleButton(
    allLetterGroupsExpanded: Boolean,
    onToggleLetterGroups: () -> Unit
) {
    val contentDescription = if (allLetterGroupsExpanded) {
        "Collapse all letter sections"
    } else {
        "Expand all letter sections"
    }
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clickable(onClick = onToggleLetterGroups),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (allLetterGroupsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun MidiFileDisplayModeSegment(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .height(38.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.45f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MidiSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    totalCount: Int,
    visibleCount: Int,
    filtering: Boolean
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Search MIDI files") },
        placeholder = { Text("Title or filename") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (filtering) {
                LoadingIndicator(
                    contentDescription = "Filtering MIDI files",
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(18.dp)
                )
            } else if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear MIDI search")
                }
            } else {
                Text(
                    "$visibleCount/$totalCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
    )
}

@Composable
private fun MidiFilePagingFooter(
    rangeStart: Int,
    rangeEnd: Int,
    totalCount: Int,
    canPageBackward: Boolean,
    canPageForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "$rangeStart-$rangeEnd of $totalCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = onPrevious, enabled = canPageBackward) {
                Icon(Icons.Default.ExpandLess, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Previous")
            }
            FilledTonalButton(onClick = onNext, enabled = canPageForward) {
                Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Next")
            }
        }
    }
}

private data class MidiLetterGroup(
    val key: String,
    val title: String,
    val files: List<MidiLibraryItem>
)

private fun MidiLetterGroup.defaultExpanded(pageSize: Int): Boolean =
    files.size <= pageSize

private fun List<MidiLibraryItem>.sortedForMidiDisplay(): List<MidiLibraryItem> =
    sortedWith(
        compareBy<MidiLibraryItem> { it.title.lowercase(Locale.US) }
            .thenBy { it.originalName.lowercase(Locale.US) }
    )

private fun midiFileLetterGroups(files: List<MidiLibraryItem>): List<MidiLetterGroup> =
    files
        .groupBy { midiFileLetterKey(it) }
        .toList()
        .sortedBy { (key, _) -> if (key == "#") "ZZZ" else key }
        .map { (key, groupFiles) ->
            MidiLetterGroup(
                key = key,
                title = if (key == "#") "Other" else key,
                files = groupFiles
            )
        }

private fun midiFileLetterKey(item: MidiLibraryItem): String {
    val first = item.title
        .ifBlank { item.originalName }
        .trim()
        .firstOrNull { it.isLetter() }
        ?: return "#"
    val key = first.toString().uppercase(Locale.US)
    return key.takeIf { it.length == 1 && it[0] in 'A'..'Z' } ?: "#"
}

@Composable
private fun MidiLetterHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
            Spacer(Modifier.weight(1f))
            Text(
                "$count MIDI file${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
        )
    }
}

@Composable
private fun PlaylistSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    playlistCount: Int,
    visibleCount: Int
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Search playlists") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear playlist search")
                }
            } else {
                Text(
                    "$visibleCount/$playlistCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
    )
}

@Composable
private fun LoadingIndicator(
    contentDescription: String,
    modifier: Modifier = Modifier.size(20.dp)
) {
    CircularProgressIndicator(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        strokeWidth = 2.5.dp,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun LoadingStatusPanel(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LoadingIndicator(
                contentDescription = title,
                modifier = Modifier.size(30.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun ReadingMusicOverlay(
    playback: PlaybackUiState,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.34f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Reading music" },
                    strokeWidth = 4.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Reading music", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        playback.currentTitle ?: "Preparing MIDI file",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Preparing playback data...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop loading")
                }
            }
        }
    }
}

@Composable
private fun MidiSelectionToolbar(
    count: Int,
    onCreatePlaylist: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "$count selected",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1
            )
            TextButton(onClick = onCreatePlaylist) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Playlist")
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete")
            }
            IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear selection")
            }
        }
    }
}

@Composable
private fun MidiFileRow(
    item: MidiLibraryItem,
    selected: Boolean,
    multiSelected: Boolean,
    selectionActive: Boolean,
    connected: Boolean,
    preparing: Boolean,
    playing: Boolean,
    paused: Boolean,
    onSelect: () -> Unit,
    onToggleSelected: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: (MidiLibraryItem, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val coordinatesHolder = remember { LayoutCoordinatesHolder() }
    var showMenu by remember { mutableStateOf(false) }
    val containerColor = when {
        multiSelected -> MaterialTheme.colorScheme.primaryContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinatesHolder.coordinates = it }
            .clickable(onClick = onSelect)
            .dragFile(item, coordinatesHolder, onDragStart, onDrag, onDragEnd, onDragCancel),
        shape = RoundedCornerShape(4.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onToggleSelected),
                contentAlignment = Alignment.Center
            ) {
                if (selectionActive || multiSelected) {
                    Checkbox(
                        checked = multiSelected,
                        onCheckedChange = { onToggleSelected() },
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = "Select ${item.title}", modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${item.durationUs.formatDuration()} - ${item.originalName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.notes.ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.notes.isNotBlank()) MaterialTheme.colorScheme.error else Color.Transparent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = {
                    when {
                        preparing -> onSelect()
                        playing -> onPause()
                        paused -> onResume()
                        else -> onPlay()
                    }
                },
                enabled = connected || playing || paused || preparing,
                modifier = Modifier.size(40.dp)
            ) {
                if (preparing) {
                    LoadingIndicator(contentDescription = "Reading ${item.title}")
                } else {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = when {
                            playing -> "Pause ${item.title}"
                            paused -> "Resume ${item.title}"
                            else -> "Play ${item.title}"
                        }
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions for ${item.title}")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export") },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onExport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DraggedMidiFilePreview(items: List<MidiLibraryItem>, modifier: Modifier = Modifier) {
    val firstItem = items.first()
    val multiple = items.size > 1
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    if (multiple) "${items.size} MIDI files" else firstItem.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (multiple) {
                        items.take(2).joinToString { it.title } + if (items.size > 2) "..." else ""
                    } else {
                        "${firstItem.durationUs.formatDuration()} - ${firstItem.originalName}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Add",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.dragFile(
    item: MidiLibraryItem,
    coordinatesHolder: LayoutCoordinatesHolder,
    onDragStart: (MidiLibraryItem, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
): Modifier {
    val currentItem by rememberUpdatedState(item)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    return pointerInput(item.id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { localOffset ->
                currentOnDragStart(currentItem, coordinatesHolder.coordinates?.localToRoot(localOffset) ?: Offset.Zero)
            },
            onDrag = { _, dragAmount -> currentOnDrag(dragAmount) },
            onDragEnd = { currentOnDragEnd() },
            onDragCancel = { currentOnDragCancel() }
        )
    }
}

private class LayoutCoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

@Composable
private fun PlaylistFolder(
    playlist: MidiPlaylist,
    filesById: Map<String, MidiLibraryItem>,
    selected: Boolean,
    expanded: Boolean,
    highlighted: Boolean,
    connected: Boolean,
    preparing: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
    onAddFiles: () -> Unit,
    onPlaySequential: () -> Unit,
    onPlayShuffle: () -> Unit,
    onRename: () -> Unit,
    onColor: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val playlistColor = playlist.colorHex.toPlaylistColor()
    val color = when {
        highlighted -> MaterialTheme.colorScheme.tertiaryContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
        playlistColor != null -> playlistColor.copy(alpha = 0.20f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    var showMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(4.dp),
        color = color
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggle, modifier = Modifier.size(34.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Toggle playlist")
                }
                playlistColor?.let { color ->
                    Surface(
                        modifier = Modifier
                            .width(7.dp)
                            .height(38.dp),
                        shape = RoundedCornerShape(3.dp),
                        color = color
                    ) {}
                    Spacer(Modifier.width(8.dp))
                }
                Icon(if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${playlist.itemIds.size} track${if (playlist.itemIds.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onAddFiles,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add MIDI files to ${playlist.name}")
                }
                IconButton(
                    onClick = { if (preparing) onSelect() else onPlaySequential() },
                    enabled = connected && playlist.itemIds.isNotEmpty(),
                    modifier = Modifier.size(40.dp)
                ) {
                    if (preparing) {
                        LoadingIndicator(contentDescription = "Reading ${playlist.name}")
                    } else {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Play ${playlist.name} in order")
                    }
                }
                IconButton(onClick = onPlayShuffle, enabled = connected && playlist.itemIds.isNotEmpty() && !preparing, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle ${playlist.name}")
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Playlist actions")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Add files") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onAddFiles()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Color") },
                            leadingIcon = { Icon(Icons.Default.FiberManualRecord, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onColor()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clone playlist") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onClone()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            if (expanded) {
                if (playlist.itemIds.isEmpty()) {
                    Text(
                        "Drop MIDI files here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 46.dp, bottom = 6.dp)
                    )
                } else {
                    playlist.itemIds.forEachIndexed { index, itemId ->
                        val item = filesById[itemId]
                        PlaylistTrackRow(
                            index = index,
                            item = item,
                            isFirst = index == 0,
                            isLast = index == playlist.itemIds.lastIndex,
                            onMove = onMove,
                            onRemove = onRemove
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    index: Int,
    item: MidiLibraryItem?,
    isFirst: Boolean,
    isLast: Boolean,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 46.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${index + 1}.", modifier = Modifier.width(28.dp), fontWeight = FontWeight.SemiBold)
        Column(Modifier.weight(1f)) {
            Text(item?.title ?: "Missing MIDI file", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item?.durationUs?.formatDuration() ?: "--:--", style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = { onMove(index, -1) }, enabled = !isFirst, modifier = Modifier.size(34.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move up", modifier = Modifier.graphicsLayer(rotationZ = -90f))
        }
        IconButton(onClick = { onMove(index, 1) }, enabled = !isLast, modifier = Modifier.size(34.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move down", modifier = Modifier.graphicsLayer(rotationZ = 90f))
        }
        IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove")
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    onImport: () -> Unit,
    onCreatePlaylist: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("No music yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImport) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add MIDI")
                }
                OutlinedButton(onClick = onCreatePlaylist) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New Playlist")
                }
            }
        }
    }
}

@Composable
private fun DevicePanel(
    state: AppUiState,
    service: NoteCastService,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onOpenBluetoothPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("MIDI Devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(state.connection.message, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.connection.connected) {
                    TextButton(onClick = { service.disconnect() }) { Text("Disconnect") }
                }
            }
            DeviceConnectorContent(
                state = state,
                service = service,
                permissionsGranted = permissionsGranted,
                onRequestPermissions = onRequestPermissions,
                onRequestBluetoothEnable = onRequestBluetoothEnable,
                onOpenBluetoothPairing = onOpenBluetoothPairing
            )
        }
    }
}

@Composable
private fun PlayerPanel(
    state: AppUiState,
    service: NoteCastService,
    modifier: Modifier = Modifier
) {
    val playback = state.playback
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Now playing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        playback.currentTitle ?: "Ready",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            PlaybackProgressSlider(
                playback = playback,
                onSeek = service::seekPlayback,
                modifier = Modifier.fillMaxWidth()
            )
            playback.playlistName?.let {
                AssistChip(
                    onClick = {},
                    label = { Text("$it - ${playback.currentTrackNumber}/${playback.totalTracks}") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) }
                )
            }
            playback.error?.let {
                AssistChip(onClick = {}, label = { Text(it) }, leadingIcon = { Icon(Icons.Default.Warning, null) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { service.skipToPrevious() }, enabled = playback.isActive && !playback.isPreparing) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                }
                IconButton(
                    onClick = {
                        when {
                            playback.isPlaying -> service.pausePlayback()
                            playback.isPaused -> service.resumePlayback()
                            playback.isPreparing -> service.stopPlayback()
                        }
                    },
                    enabled = playback.isActive
                ) {
                    if (playback.isPreparing) {
                        LoadingIndicator(contentDescription = "Reading music. Tap to stop.")
                    } else {
                        Icon(if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play or pause")
                    }
                }
                IconButton(onClick = { service.stopPlayback() }, enabled = playback.isActive) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
                IconButton(onClick = { service.skipToNext() }, enabled = playback.isActive && !playback.isPreparing) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }
        }
    }
}

@Composable
private fun TransportBar(
    state: AppUiState,
    selectedKind: String,
    selectedId: String?,
    onPlaySelection: () -> Unit,
    service: NoteCastService,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    val playback = state.playback
    val selectedTitle = when (selectedKind) {
        "playlist" -> state.playlists.firstOrNull { it.id == selectedId }?.name
        else -> state.files.firstOrNull { it.id == selectedId }?.title
    }
    Surface(
        modifier = Modifier.navigationBarsPadding(),
        tonalElevation = 4.dp
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 560.dp
            val contentModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
            if (compact) {
                Column(contentModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TransportTitle(playback = playback, selectedTitle = selectedTitle)
                    PlaybackProgressSlider(
                        playback = playback,
                        onSeek = service::seekPlayback,
                        compact = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TransportControls(
                        state = state,
                        service = service,
                        onPlaySelection = onPlaySelection,
                        hasSelection = selectedId != null,
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(contentModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TransportTitle(playback = playback, selectedTitle = selectedTitle)
                        PlaybackProgressSlider(
                            playback = playback,
                            onSeek = service::seekPlayback,
                            compact = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TransportControls(
                        state = state,
                        service = service,
                        onPlaySelection = onPlaySelection,
                        hasSelection = selectedId != null,
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        stackVolume = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackProgressSlider(
    playback: PlaybackUiState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val durationUs = playback.durationUs.coerceAtLeast(0L)
    val enabled = playback.isActive && !playback.isPreparing && durationUs > 0L
    var dragging by remember(playback.currentItemId, playback.durationUs) { mutableStateOf(false) }
    var dragValue by remember(playback.currentItemId, playback.durationUs) { mutableStateOf(0f) }
    val liveProgress = playback.progressUs.coerceIn(0L, durationUs)
    val displayedProgress = if (dragging) dragValue.toLong().coerceIn(0L, durationUs) else liveProgress
    val valueRangeEnd = durationUs.coerceAtLeast(1L).toFloat()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 4.dp)) {
        Slider(
            value = displayedProgress.toFloat().coerceIn(0f, valueRangeEnd),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                dragging = false
                onSeek(dragValue.toLong())
            },
            valueRange = 0f..valueRangeEnd,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 36.dp else 48.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(displayedProgress.formatClockTime(), style = MaterialTheme.typography.bodySmall)
            if (playback.sustainPedalPressed) {
                Spacer(Modifier.width(8.dp))
                PedalPressDot()
            }
            Spacer(Modifier.weight(1f))
            Text(durationUs.formatClockTime(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PedalPressDot(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .size(8.dp)
            .semantics { contentDescription = "Sustain pedal pressed" },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary
    ) {}
}

@Composable
private fun TransportTitle(
    playback: PlaybackUiState,
    selectedTitle: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            playback.currentTitle ?: selectedTitle ?: "Select a MIDI file or playlist",
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            when (playback.mode) {
                PlaybackMode.Preparing -> "Preparing ${playback.currentTitle ?: "track"}"
                PlaybackMode.Playing -> "Playing ${playback.progressUs.formatClockTime()} / ${playback.durationUs.formatClockTime()}"
                PlaybackMode.Paused -> "Paused ${playback.progressUs.formatClockTime()} / ${playback.durationUs.formatClockTime()}"
                PlaybackMode.Error -> playback.error ?: "Playback error"
                PlaybackMode.Idle -> "Ready"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TransportControls(
    state: AppUiState,
    service: NoteCastService,
    onPlaySelection: () -> Unit,
    hasSelection: Boolean,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    stackVolume: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (stackVolume) {
        Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TransportButtons(
                state = state,
                service = service,
                onPlaySelection = onPlaySelection,
                hasSelection = hasSelection
            )
            VolumeControl(
                state = state,
                service = service,
                settings = settings,
                onSettingsChange = onSettingsChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TransportButtons(
                state = state,
                service = service,
                onPlaySelection = onPlaySelection,
                hasSelection = hasSelection
            )
            VolumeControl(
                state = state,
                service = service,
                settings = settings,
                onSettingsChange = onSettingsChange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TransportButtons(
    state: AppUiState,
    service: NoteCastService,
    onPlaySelection: () -> Unit,
    hasSelection: Boolean,
    modifier: Modifier = Modifier
) {
    val playback = state.playback
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { service.skipToPrevious() }, enabled = playback.isActive && !playback.isPreparing) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
        }
        IconButton(
            onClick = {
                when {
                    playback.isPlaying -> service.pausePlayback()
                    playback.isPaused -> service.resumePlayback()
                    playback.isPreparing -> service.stopPlayback()
                    else -> onPlaySelection()
                }
            },
            enabled = playback.isActive || (state.connection.connected && hasSelection)
        ) {
            if (playback.isPreparing) {
                LoadingIndicator(contentDescription = "Reading music. Tap to stop.")
            } else {
                Icon(if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play or pause")
            }
        }
        IconButton(onClick = { service.stopPlayback() }, enabled = playback.isActive) {
            Icon(Icons.Default.Stop, contentDescription = "Stop")
        }
        IconButton(onClick = { service.skipToNext() }, enabled = playback.isActive && !playback.isPreparing) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next")
        }
    }
}

@Composable
private fun VolumeControl(
    state: AppUiState,
    service: NoteCastService,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMixer by rememberSaveable { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp))
        Slider(
            value = state.volumePercent.toFloat(),
            onValueChange = { service.setVolume(it.roundToInt()) },
            valueRange = 0f..100f,
            enabled = state.connection.connected,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { showMixer = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.GraphicEq, contentDescription = "Open volume mixer")
        }
    }
    if (showMixer) {
        VolumeMixerDialog(
            state = state,
            service = service,
            settings = settings,
            onSettingsChange = onSettingsChange,
            onDismiss = { showMixer = false }
        )
    }
}

private data class MixerChannelRowModel(
    val channel: Int,
    val instrumentName: String,
    val detail: String,
    val selectedProgram: Int,
    val hasInstrumentOverride: Boolean,
    val infoLines: List<Pair<String, String>>
)

@Composable
private fun VolumeMixerDialog(
    state: AppUiState,
    service: NoteCastService,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val currentSongId = state.playback.currentItemId?.takeIf { itemId -> state.files.any { it.id == itemId } }
    val songInstrumentOverrides = settings.instrumentOverridesForSong(currentSongId)
    val rows = remember(state.playback.isActive, state.playbackChannels, currentSongId, settings.songInstrumentOverrides) {
        mixerChannelRows(
            channels = if (state.playback.isActive) state.playbackChannels else emptyList(),
            songInstrumentOverrides = songInstrumentOverrides
        )
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text("Volume mixer", style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                        Text(
                            when (settings.volumeControlMode) {
                                VolumeControlMode.LegacyVolumeScaling -> "Legacy volume scaling"
                                VolumeControlMode.StandardMidiVolume -> "Standard MIDI volume (CC7)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        MixerMainVolumeRow(
                            volumePercent = state.volumePercent,
                            enabled = state.connection.connected,
                            onVolumeChange = service::setVolume
                        )
                    }
                    if (rows.isEmpty()) {
                        item {
                            Text(
                                "No mixable channels in this MIDI file.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    } else {
                        items(rows, key = { it.channel }) { row ->
                            MixerChannelRow(
                                row = row,
                                service = service,
                                currentSongId = currentSongId,
                                settings = settings,
                                onSettingsChange = onSettingsChange
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentSongId != null && songInstrumentOverrides.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                onSettingsChange(settings.withClearedSongInstrumentOverrides(currentSongId))
                            }
                        ) {
                            Text("Clear song instruments")
                        }
                    }
                    Button(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun MixerMainVolumeRow(
    volumePercent: Int,
    enabled: Boolean,
    onVolumeChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Main volume",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "${volumePercent.coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Slider(
                value = volumePercent.coerceIn(0, 100).toFloat(),
                onValueChange = { onVolumeChange(it.roundToInt().coerceIn(0, 100)) },
                valueRange = 0f..100f,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MixerChannelRow(
    row: MixerChannelRowModel,
    service: NoteCastService,
    currentSongId: String?,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    val channels = listOf(row.channel)
    val controls = channels.map { settings.channelControls.controlForChannel(it) }
    val volumePercent = controls.map { it.volumePercent.coerceIn(0, 100) }.average().roundToInt().coerceIn(0, 100)
    val muted = controls.all { it.muted }
    var showInfo by remember { mutableStateOf(false) }
    var showInstrumentMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            row.instrumentName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (row.infoLines.isNotEmpty()) {
                            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Info, contentDescription = "Channel information", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            row.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (row.hasInstrumentOverride) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Song override", maxLines = 1) }
                            )
                        }
                    }
                }
                Text("${volumePercent}%", style = MaterialTheme.typography.bodyMedium)
                Checkbox(
                    checked = muted,
                    onCheckedChange = { checked ->
                        onSettingsChange(
                            settings.copy(
                                channelControls = settings.channelControls.updateChannels(channels) {
                                    it.copy(muted = checked)
                                }
                            )
                        )
                    }
                )
                Text("Mute", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(
                        onClick = { showInstrumentMenu = true },
                        enabled = currentSongId != null
                    ) {
                        Text("Change")
                    }
                }
                if (currentSongId != null && row.hasInstrumentOverride) {
                    IconButton(
                        onClick = {
                            service.applyLiveInstrumentOverride(currentSongId, row.channel, null)
                            onSettingsChange(settings.withSongInstrumentOverride(currentSongId, row.channel, null))
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Clear instrument override")
                    }
                }
            }
            Slider(
                value = volumePercent.toFloat(),
                onValueChange = { value ->
                    val cleanValue = value.roundToInt().coerceIn(0, 100)
                    onSettingsChange(
                        settings.copy(
                            channelControls = settings.channelControls.updateChannels(channels) {
                                it.copy(volumePercent = cleanValue)
                            }
                        )
                    )
                },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    if (showInfo) {
        MixerChannelInfoDialog(row = row, onDismiss = { showInfo = false })
    }
    if (showInstrumentMenu && currentSongId != null) {
        InstrumentPickerDialog(
            row = row,
            onDismiss = { showInstrumentMenu = false },
            onSelect = { program ->
                showInstrumentMenu = false
                service.applyLiveInstrumentOverride(currentSongId, row.channel, program)
                onSettingsChange(settings.withSongInstrumentOverride(currentSongId, row.channel, program))
            }
        )
    }
}

@Composable
private fun InstrumentPickerDialog(
    row: MixerChannelRowModel,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val programIds = remember { GeneralMidi.programNames.indices.toList() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .widthIn(max = 560.dp)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.MusicNote, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text("Instrument", style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                        Text(
                            row.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(programIds, key = { it }) { program ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(program) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                if (program == row.selectedProgram) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                            Text(
                                GeneralMidi.programLabel(program),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MixerChannelInfoDialog(row: MixerChannelRowModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        title = { Text(row.instrumentName) },
        text = {
            ScrollableDialogColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                row.infoLines.forEach { (label, value) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

private fun mixerChannelRows(
    channels: List<PlaybackChannelInfo>,
    songInstrumentOverrides: Map<Int, Int>
): List<MixerChannelRowModel> {
    val present = channels
        .filter { it.channel in 1..16 }
        .distinctBy { it.channel }
        .sortedBy { it.channel }
    return present.map { channelInfo ->
        val overrideProgram = songInstrumentOverrides[channelInfo.channel]
        val sourceProgram = channelInfo.programNumbers.firstOrNull()
        val instrumentName = when {
            overrideProgram != null -> GeneralMidi.programName(overrideProgram)
            !channelInfo.instrumentName.isNullOrBlank() -> channelInfo.instrumentName
            sourceProgram != null -> GeneralMidi.sourceProgramNameForChannel(channelInfo.channel, sourceProgram)
            else -> GeneralMidi.defaultInstrumentNameForChannel(channelInfo.channel)
        }
        MixerChannelRowModel(
            channel = channelInfo.channel,
            instrumentName = instrumentName,
            detail = listOf(channelInfo.channel).channelDetail(),
            selectedProgram = overrideProgram ?: sourceProgram ?: GeneralMidi.defaultProgramForChannel(channelInfo.channel),
            hasInstrumentOverride = overrideProgram != null,
            infoLines = channelInfo.infoLines(overrideProgram)
        )
    }
}

private fun List<Int>.channelDetail(): String =
    if (size == 1) "Channel ${first()}" else "Channels ${joinToString(" and ")}"

private fun PlaybackChannelInfo.infoLines(overrideProgram: Int?): List<Pair<String, String>> {
    val hasExtraInfo = overrideProgram != null ||
        programNumbers.isNotEmpty() ||
        trackTitles.isNotEmpty() ||
        metaInstrumentNames.isNotEmpty() ||
        !label.isNullOrBlank()
    if (!hasExtraInfo) return emptyList()
    return buildList {
        add("MIDI channel" to channel.toString())
        if (overrideProgram != null) {
            add("Song override" to GeneralMidi.programLabel(overrideProgram))
        }
        if (programNumbers.isNotEmpty()) {
            add("MIDI program" to programNumbers.distinct().joinToString(" / ") {
                GeneralMidi.sourceProgramLabelForChannel(channel, it)
            })
        }
        if (metaInstrumentNames.isNotEmpty()) {
            add("Instrument name" to metaInstrumentNames.distinct().joinToString(" / "))
        }
        if (trackTitles.isNotEmpty()) {
            add("Track title" to trackTitles.distinct().joinToString(" / "))
        }
        label?.takeIf { it.isNotBlank() && it !in metaInstrumentNames && it !in trackTitles }?.let {
            add("Channel title" to it)
        }
    }
}

private fun List<MidiChannelControl>.controlForChannel(channel: Int): MidiChannelControl =
    firstOrNull { it.channel == channel }?.let { control ->
        control.copy(
            channel = channel,
            volumePercent = control.volumePercent.coerceIn(0, 100)
        )
    } ?: MidiChannelControl(channel = channel)

private fun List<MidiChannelControl>.updateChannels(
    channels: List<Int>,
    transform: (MidiChannelControl) -> MidiChannelControl
): List<MidiChannelControl> {
    val byChannel = associateBy { it.channel }.toMutableMap()
    channels.filter { it in 1..16 }.forEach { channel ->
        byChannel[channel] = transform(byChannel[channel] ?: MidiChannelControl(channel = channel)).copy(channel = channel)
    }
    return (1..16).map { channel -> byChannel[channel] ?: MidiChannelControl(channel = channel) }
}

@Composable
private fun ConnectionPill(state: AppUiState, onClick: () -> Unit) {
    val connection = state.connection
    val (label, icon, color) = when {
        connection.connected -> Triple("Connected", Icons.Default.BluetoothConnected, MaterialTheme.colorScheme.secondaryContainer)
        connection.connecting || connection.scanning -> Triple("Scanning", Icons.AutoMirrored.Filled.BluetoothSearching, MaterialTheme.colorScheme.tertiaryContainer)
        else -> Triple("Offline", Icons.Default.Bluetooth, MaterialTheme.colorScheme.surfaceVariant)
    }
    Surface(
        modifier = Modifier
            .padding(end = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = color
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 7.dp, end = 14.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { translationX = 1.5f }
                )
            }
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    connection.deviceName ?: connection.rememberedDeviceName ?: "MIDI Device",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ConnectionWizardDialog(
    state: AppUiState,
    service: NoteCastService,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onOpenBluetoothPairing: () -> Unit,
    autoReconnectEnabled: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MIDI adapter", style = MaterialTheme.typography.headlineSmall) },
        text = {
            ScrollableDialogColumn(maxHeight = 430.dp, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DeviceConnectorContent(
                    state = state,
                    service = service,
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = onRequestPermissions,
                    onRequestBluetoothEnable = onRequestBluetoothEnable,
                    onOpenBluetoothPairing = onOpenBluetoothPairing
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Continue offline")
            }
        }
    )
}

@Composable
private fun BatteryRecommendationDialog(
    onOpenBatterySettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
        title = { Text(stringResource(R.string.battery_recommendation_title)) },
        text = {
            ScrollableDialogColumn(maxHeight = 260.dp) {
                Text(stringResource(R.string.battery_recommendation_message))
            }
        },
        confirmButton = {
            Button(onClick = onOpenBatterySettings) {
                Text(stringResource(R.string.battery_recommendation_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        }
    )
}

@Composable
private fun ConnectionDialog(
    state: AppUiState,
    service: NoteCastService,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onOpenBluetoothPairing: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (state.connection.connected) "MIDI adapter connected" else "MIDI adapter",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            ScrollableDialogColumn(maxHeight = 430.dp, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DeviceConnectorContent(
                    state = state,
                    service = service,
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = onRequestPermissions,
                    onRequestBluetoothEnable = onRequestBluetoothEnable,
                    onOpenBluetoothPairing = onOpenBluetoothPairing
                )
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
private fun DeviceConnectorContent(
    state: AppUiState,
    service: NoteCastService,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onOpenBluetoothPairing: () -> Unit
) {
    var removeDeviceAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var renameDeviceAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var showScanDialog by rememberSaveable { mutableStateOf(false) }
    val connected = state.connection.connected
    val hasUsbMidiDevice = state.bleDevices.any { isUsbMidiDevice(it) }

    if (!permissionsGranted) {
        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (hasUsbMidiDevice) "Bluetooth access needed for WIDI adapters" else "App access needed",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (hasUsbMidiDevice) {
                        "USB MIDI adapters can still be used. Grant access to scan for WIDI adapters."
                    } else {
                        "APS NoteCast needs Bluetooth, location, and internet access to find MIDI devices and contact Alex's Piano Service."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (hasUsbMidiDevice) "Grant Bluetooth access" else "Grant app access")
                }
            }
        }
        if (!hasUsbMidiDevice) return
    }

    if (!state.connection.bluetoothAvailable) {
        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.errorContainer) {
            Text(
                if (hasUsbMidiDevice) {
                    "This Android device does not report Bluetooth hardware. USB MIDI adapters can still be used."
                } else {
                    "This Android device does not report Bluetooth hardware."
                },
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        if (!hasUsbMidiDevice) return
    }

    if (!state.connection.bluetoothEnabled) {
        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bluetooth is off", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (hasUsbMidiDevice) {
                            "USB MIDI adapters can still be used. Turn Bluetooth on for WIDI adapters."
                        } else {
                            "Turn it on to find WIDI adapters."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (!hasUsbMidiDevice) {
                    Button(onClick = onRequestBluetoothEnable) {
                        Text("Turn on")
                    }
                } else {
                    TextButton(onClick = onRequestBluetoothEnable) {
                        Text("Turn on")
                    }
                }
            }
        }
        if (!hasUsbMidiDevice) return
    }

    if (state.connection.rememberedDeviceName != null && !connected) {
        QuickReconnectPanel(
            deviceName = state.connection.rememberedDeviceName,
            reconnectPaused = state.connection.autoReconnectSuppressed,
            onReconnect = { service.autoReconnectIfPossible(force = true) }
        )
    }

    if (state.connection.connecting || state.connection.scanning) {
        LoadingStatusPanel(
            title = when {
                state.connection.connecting -> "Connecting to MIDI"
                connected -> "Looking for another MIDI device"
                else -> "Looking for MIDI"
            },
            message = if (connected && state.connection.scanning) {
                "The current connection stays active while APS NoteCast looks nearby."
            } else {
                state.connection.message
            },
            actionLabel = if (state.connection.scanning) "Stop" else null,
            onAction = if (state.connection.scanning) ({ service.stopBleScan() }) else null
        )
    } else if (!connected && state.connection.message.isNotBlank() && state.connection.message != "Not connected") {
        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                state.connection.message,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (connected) {
        ConnectedDeviceRow(
            state = state,
            service = service,
            onRename = { address -> renameDeviceAddress = address },
            onRemove = { address -> removeDeviceAddress = address }
        )
    } else {
        ConnectionInfoPanel(
            title = "No MIDI adapter connected",
            message = "Connect to a nearby adapter now, or save one so it is easier to find later."
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                showScanDialog = true
                service.startBleScan()
            },
            enabled = !state.connection.scanning && !state.connection.connecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    state.connection.scanning -> "Scanning..."
                    connected -> "Find another adapter"
                    else -> "Find MIDI adapters"
                }
            )
        }
    }

    val savedDevices = state.bleDevices
        .filter { it.added }
        .filterNot { sameDeviceTarget(it.address, state.connection.address) }
        .sortedWith(
            compareByDescending<BleMidiDeviceItem> { it.connectable }
                .thenBy { it.name }
        )
    val duplicateSavedNames = duplicateDeviceNames(savedDevices)

    if (savedDevices.isEmpty() && !state.connection.scanning && !state.connection.connecting) {
        if (!connected) {
            Text(
                "No saved adapters yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else if (savedDevices.isNotEmpty()) {
        ConnectionSectionHeader(
            title = "Saved adapters",
            subtitle = "Remembered by APS NoteCast. It still needs to be nearby and powered on."
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            savedDevices.forEach { device ->
                val canSearchForDevice = !state.connection.scanning && !state.connection.connecting
                SavedDeviceRow(
                    device = device,
                    displayName = deviceDisplayName(device, duplicateSavedNames),
                    onConnect = {
                        if (device.connectable) {
                            service.connect(device.address)
                        } else {
                            showScanDialog = true
                            service.startBleScan()
                        }
                    },
                    connectEnabled = device.connectable || canSearchForDevice,
                    onRename = { renameDeviceAddress = device.address },
                    onRemove = { removeDeviceAddress = device.address }
                )
            }
        }
    }

    if (showScanDialog) {
        ScanDevicesDialog(
            state = state,
            service = service,
            onDismiss = {
                service.stopBleScan()
                showScanDialog = false
            },
            onRename = { address -> renameDeviceAddress = address },
            onOpenBluetoothPairing = onOpenBluetoothPairing,
            onConnect = { address ->
                service.addDevice(address)
                service.connect(address)
                showScanDialog = false
            }
        )
    }

    renameDeviceAddress?.let { address ->
        val deviceName = state.bleDevices.firstOrNull { it.address == address }?.name
            ?: (if (state.connection.address == address) state.connection.deviceName else null)
            ?: "MIDI device"
        RenameDialog(
            title = "Rename MIDI device",
            initialName = deviceName,
            onDismiss = { renameDeviceAddress = null },
            onSave = { name ->
                service.renameDevice(address, name)
                renameDeviceAddress = null
            }
        )
    }

    removeDeviceAddress?.let { address ->
        val deviceName = state.bleDevices.firstOrNull { it.address == address }?.name
            ?: (if (state.connection.address == address) state.connection.deviceName else null)
            ?: "this MIDI device"
        ConfirmDialog(
            title = "Remove device?",
            message = "Remove $deviceName from APS NoteCast and clear its reconnect data? Android Bluetooth pairing is not changed.",
            confirmLabel = "Remove",
            onDismiss = { removeDeviceAddress = null },
            onConfirm = {
                service.removeDevice(address)
                removeDeviceAddress = null
            }
        )
    }
}

@Composable
private fun QuickReconnectPanel(
    deviceName: String,
    reconnectPaused: Boolean,
    onReconnect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onReconnect),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (reconnectPaused) "Reconnect paused" else "Saved adapter",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    deviceName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onReconnect) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun ConnectionInfoPanel(
    title: String,
    message: String
) {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConnectionSectionHeader(
    title: String,
    subtitle: String
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScanDevicesDialog(
    state: AppUiState,
    service: NoteCastService,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onOpenBluetoothPairing: () -> Unit,
    onConnect: (String) -> Unit
) {
    val availableResults = state.bleDevices
        .filterNot { sameDeviceTarget(it.address, state.connection.address) }
        .filter { it.source != "Saved" }
        .filter { it.likelyMidi || it.standardBleMidi || it.added || it.connectable }
        .sortedWith(
            compareByDescending<BleMidiDeviceItem> { it.connectable }
                .thenByDescending { it.standardBleMidi }
                .thenByDescending { it.likelyMidi }
                .thenBy { it.name }
        )
    val savedResults = state.bleDevices
        .filterNot { sameDeviceTarget(it.address, state.connection.address) }
        .filter { it.added && it.source == "Saved" }
        .sortedWith(
            compareByDescending<BleMidiDeviceItem> { it.connectable }
                .thenBy { it.name }
        )
    val duplicateScanNames = duplicateDeviceNames(availableResults + savedResults)
    val connected = state.connection.connected
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (connected) "Switch MIDI adapter" else "Find MIDI adapter",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            ScrollableDialogColumn(maxHeight = 430.dp, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.connection.scanning) {
                    LoadingStatusPanel(
                        title = if (connected) "Looking nearby" else "Looking nearby",
                        message = if (connected) {
                            "Current adapter stays connected."
                        } else {
                            state.connection.message
                        },
                        actionLabel = "Stop",
                        onAction = { service.stopBleScan() }
                    )
                }
                if (availableResults.isEmpty() && savedResults.isEmpty()) {
                    Text(
                        when {
                            state.connection.scanning -> "Searching..."
                            connected -> "No other adapters found. The current connection is unchanged."
                            else -> "No adapters found. Make sure the adapter is powered on and nearby, then scan again."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    if (availableResults.isNotEmpty()) {
                        ConnectionSectionHeader(
                            title = "Nearby now",
                            subtitle = "Connect uses it now. Save remembers it for later."
                        )
                    }
                    availableResults.forEach { device ->
                        ScanDeviceRow(
                            device = device,
                            displayName = deviceDisplayName(device, duplicateScanNames),
                            onSave = { service.addDevice(device.address) },
                            onConnect = { onConnect(device.address) },
                            onRename = { onRename(device.address) },
                            onPair = onOpenBluetoothPairing,
                            connectLabel = if (connected) "Switch" else "Connect"
                        )
                    }
                    if (savedResults.isNotEmpty()) {
                        ConnectionSectionHeader(
                            title = "Saved for reconnect",
                            subtitle = "Remembered or paired, but not always nearby."
                        )
                    }
                    savedResults.forEach { device ->
                        ScanDeviceRow(
                            device = device,
                            displayName = deviceDisplayName(device, duplicateScanNames),
                            onSave = { service.addDevice(device.address) },
                            onConnect = { onConnect(device.address) },
                            onRename = { onRename(device.address) },
                            onPair = onOpenBluetoothPairing,
                            connectLabel = if (connected) "Switch" else "Connect"
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { service.startBleScan() },
                enabled = !state.connection.scanning && !state.connection.connecting
            ) {
                Text("Scan again")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (availableResults.isEmpty() && savedResults.isEmpty() && !state.connection.scanning && !state.connection.connecting) {
                    TextButton(onClick = onOpenBluetoothPairing) {
                        Text("Bluetooth settings")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun ConnectedDeviceRow(
    state: AppUiState,
    service: NoteCastService,
    onRename: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.connection.deviceName ?: "MIDI Device",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("Connected", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { service.disconnect() }, modifier = Modifier.weight(1f)) {
                    Text("Disconnect", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                state.connection.address?.let { address ->
                    IconButton(onClick = { onRename(address) }) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename ${state.connection.deviceName ?: "device"}")
                    }
                    IconButton(onClick = { onRemove(address) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove ${state.connection.deviceName ?: "device"}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedDeviceRow(
    device: BleMidiDeviceItem,
    displayName: String,
    onConnect: () -> Unit,
    connectEnabled: Boolean,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        displayName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    DeviceStatusText(device)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onConnect, enabled = connectEnabled, modifier = Modifier.weight(1f)) {
                    Text(
                        if (device.connectable) "Connect" else "Find nearby",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename $displayName")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove $displayName")
                }
            }
        }
    }
}

@Composable
private fun ScanDeviceRow(
    device: BleMidiDeviceItem,
    displayName: String,
    onSave: () -> Unit,
    onConnect: () -> Unit,
    onRename: () -> Unit,
    onPair: () -> Unit,
    connectLabel: String = "Connect"
) {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        displayName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    DeviceStatusText(device)
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename $displayName")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (device.connectable && !device.added) {
                    OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                        Text("Save", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (device.connectable) {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(connectLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    TextButton(onClick = onPair, modifier = Modifier.fillMaxWidth()) {
                        Text("Bluetooth settings", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceStatusText(device: BleMidiDeviceItem) {
    Text(
        deviceStatusLabel(device),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

private fun deviceStatusLabel(device: BleMidiDeviceItem): String {
    val type = when {
        isUsbMidiDevice(device) -> "USB MIDI"
        device.source == "Virtual MIDI" || device.detail.startsWith("Virtual MIDI") -> "Virtual MIDI"
        device.source == "Android MIDI" -> "Android MIDI"
        device.standardBleMidi -> "BLE MIDI"
        device.likelyMidi -> "Possible MIDI"
        else -> "Bluetooth"
    }
    val availability = when {
        isUsbMidiDevice(device) && device.connectable -> "attached now"
        isUsbMidiDevice(device) -> "not attached"
        device.source == "Virtual MIDI" && device.connectable -> "available now"
        device.source == "Saved" && device.connectable -> "saved, available"
        device.source == "Saved" -> "saved, not seen nearby"
        device.source == "Paired Bluetooth" && device.connectable -> "paired in Android, available"
        device.source == "Paired Bluetooth" -> "paired in Android"
        device.connectable -> "nearby now"
        else -> "not ready to connect"
    }
    val signal = device.rssi?.let { " - $it dBm" }.orEmpty()
    return "$type - $availability$signal"
}

private fun isUsbMidiDevice(device: BleMidiDeviceItem): Boolean =
    device.source == "USB MIDI" || device.detail.startsWith("USB MIDI")

private fun duplicateDeviceNames(devices: List<BleMidiDeviceItem>): Set<String> =
    devices
        .groupingBy { it.name.trim().lowercase(Locale.US) }
        .eachCount()
        .filterValues { it > 1 }
        .keys

private fun deviceDisplayName(device: BleMidiDeviceItem, duplicateNames: Set<String>): String {
    val cleanName = device.name.ifBlank { "MIDI adapter" }
    return if (cleanName.trim().lowercase(Locale.US) in duplicateNames) {
        "$cleanName (${shortDeviceIdentifier(device.address)})"
    } else {
        cleanName
    }
}

private fun shortDeviceIdentifier(address: String): String {
    val bluetoothAddress = address.removePrefix("midi-bt:")
    val parts = bluetoothAddress.split(':').filter { it.isNotBlank() }
    return if (parts.size >= 2) parts.takeLast(2).joinToString(":") else bluetoothAddress.takeLast(5)
}

private fun sameDeviceTarget(left: String?, right: String?): Boolean {
    if (left.isNullOrBlank() || right.isNullOrBlank()) return false
    if (left == right) return true
    val leftBluetoothAddress = left.removePrefix("midi-bt:").takeIf { it != left }
        ?: left.takeUnless { it.startsWith("midi:") || it.startsWith("midi-bt:") }
    val rightBluetoothAddress = right.removePrefix("midi-bt:").takeIf { it != right }
        ?: right.takeUnless { it.startsWith("midi:") || it.startsWith("midi-bt:") }
    return !leftBluetoothAddress.isNullOrBlank() && leftBluetoothAddress == rightBluetoothAddress
}

@Composable
private fun KuhmannMidiDialog(
    state: KuhmannSearchUiState,
    playback: PlaybackUiState,
    service: NoteCastService,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    var limitText by rememberSaveable { mutableStateOf(state.limit.toString()) }
    var pianoOnly by rememberSaveable { mutableStateOf(state.pianoOnly) }
    var selectedSourceKey by rememberSaveable { mutableStateOf(state.source.key) }
    var showLimitControl by rememberSaveable { mutableStateOf(false) }
    var showCopyrightInfo by rememberSaveable { mutableStateOf(false) }
    var showRemovalInfo by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val availableSources = state.availableSources.ifEmpty { listOf(state.source) }
    val selectedSource = availableSources.firstOrNull { it.key == selectedSourceKey }
        ?: availableSources.firstOrNull { it.key == state.source.key }
        ?: state.source
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(state.source, state.availableSources) {
        selectedSourceKey = state.source.key
    }
    LaunchedEffect(state.results, state.source) {
        selected.clear()
    }
    LaunchedEffect(state.lastImportedKeys) {
        state.lastImportedKeys.forEach { selected.remove(it) }
    }
    val selectedResults = state.results.filter { selected[it.stableKey] == true && it.stableKey !in state.lastImportedKeys }
    val limitLabel = limitText.toIntOrNull()?.coerceIn(1, 999)?.toString() ?: "50"
    fun runSearch(searchPianoOnly: Boolean) {
        service.searchExternalMidi(
            source = selectedSource,
            query = query,
            format = null,
            pianoOnly = searchPianoOnly,
            channel = null,
            limit = limitText.toIntOrNull() ?: 50
        )
    }
    val searchAction = { runSearch(pianoOnly) }
    val modeChangeAction: (Boolean) -> Unit = { newPianoOnly ->
        pianoOnly = newPianoOnly
        if (query.isNotBlank() && !state.downloading) {
            runSearch(newPianoOnly)
        }
    }
    val sourceChangeAction: (ExternalMidiSource) -> Unit = { newSource ->
        selectedSourceKey = newSource.key
        selected.clear()
        service.selectExternalMidiSource(newSource)
    }
    val importAction = { service.downloadKuhmannMidi(selectedResults) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val columns = when {
                maxWidth >= 900.dp -> 3
                maxWidth >= 560.dp -> 2
                else -> 1
            }
            val compactControls = maxWidth < 620.dp
            val dialogMaxWidth = when (columns) {
                3 -> 1080.dp
                2 -> 760.dp
                else -> 540.dp
            }
            Surface(
                modifier = Modifier
                    .widthIn(max = dialogMaxWidth)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = maxHeight)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("External MIDI", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                            Text(
                                selectedSource.dialogSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    if (compactControls) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ExternalSourceSelector(
                                sources = availableSources,
                                selectedSource = selectedSource,
                                onSourceChange = sourceChangeAction,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                selectedSource.rightsSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { showCopyrightInfo = true }) {
                                    Text("Copyright")
                                }
                                TextButton(onClick = { showRemovalInfo = true }) {
                                    Text("DMCA")
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                ExternalSourceSelector(
                                    sources = availableSources,
                                    selectedSource = selectedSource,
                                    onSourceChange = sourceChangeAction,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    selectedSource.rightsSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = { showCopyrightInfo = true }) {
                                Text("Copyright")
                            }
                            TextButton(onClick = { showRemovalInfo = true }) {
                                Text("DMCA")
                            }
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search") },
                        placeholder = { Text("Composer, title, or style") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        }
                    )
                    if (compactControls) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                KuhmannSearchFilters(
                                    pianoOnly = pianoOnly,
                                    onPianoOnlyChange = modeChangeAction,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { showLimitControl = !showLimitControl }) {
                                    Text(if (showLimitControl) "Hide" else "Limit $limitLabel")
                                }
                            }
                            if (showLimitControl) {
                                KuhmannSmallNumberField("Limit", limitText, { limitText = it }, Modifier.fillMaxWidth())
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KuhmannSearchFilters(
                                    pianoOnly = pianoOnly,
                                    onPianoOnlyChange = modeChangeAction,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { showLimitControl = !showLimitControl }) {
                                    Text(if (showLimitControl) "Hide limit" else "Limit $limitLabel")
                                }
                            }
                            if (showLimitControl) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    KuhmannSmallNumberField("Limit", limitText, { limitText = it }, Modifier.width(132.dp))
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${state.results.size} shown - ${selectedResults.size} selected",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedResults.isNotEmpty() && !state.downloading) {
                            TextButton(onClick = { selected.clear() }) {
                                Text("Clear")
                            }
                        }
                    }
                    if (state.results.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (state.searching || state.downloading) {
                                LoadingIndicator(contentDescription = state.message)
                            } else {
                                Text(
                                    "Search by composer, title, or style.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            gridItems(state.results, key = { it.stableKey }) { result ->
                                val isCurrentPreview = state.activePlaybackResultKey == result.stableKey ||
                                    playback.currentItemId?.startsWith("external-preview-${result.source.key}-${result.id}-") == true ||
                                    (result.source.isKuhmann && playback.currentItemId?.startsWith("kuhmann-preview-${result.id}-") == true)
                                val resultPlaybackMode = if (isCurrentPreview) playback.mode else PlaybackMode.Idle
                                KuhmannResultRow(
                                    result = result,
                                    checked = selected[result.stableKey] == true,
                                    imported = result.stableKey in state.lastImportedKeys,
                                    enabled = !state.downloading,
                                    onCheckedChange = { selected[result.stableKey] = it },
                                    playbackMode = resultPlaybackMode,
                                    onPlay = {
                                        when (resultPlaybackMode) {
                                            PlaybackMode.Playing -> service.pausePlayback()
                                            PlaybackMode.Paused -> service.resumePlayback()
                                            PlaybackMode.Preparing -> Unit
                                            else -> service.playKuhmannMidi(result)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!compactControls) {
                            TextButton(onClick = onDismiss) { Text("Close") }
                            Spacer(Modifier.width(8.dp))
                        }
                        Button(
                            onClick = searchAction,
                            enabled = !state.searching && !state.downloading,
                            modifier = if (compactControls) Modifier.weight(1f) else Modifier
                        ) {
                            Text(if (state.searching) "Searching..." else "Search")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = importAction,
                            enabled = selectedResults.isNotEmpty() && !state.searching && !state.downloading,
                            modifier = if (compactControls) Modifier.weight(1f) else Modifier
                        ) {
                            Text(if (state.downloading) "Importing..." else "Import ${selectedResults.size}")
                        }
                    }
                }
            }
        }
    }
    if (showCopyrightInfo) {
        ExternalSourceCopyrightDialog(
            onDismiss = { showCopyrightInfo = false },
            onOpenDisclaimer = { uriHandler.openUri("https://www.alexanderpeppe.com/disclaimer/") }
        )
    }
    if (showRemovalInfo) {
        ExternalSourceRemovalDialog(
            onDismiss = { showRemovalInfo = false },
            onOpenDmcaPolicy = { uriHandler.openUri("https://www.alexanderpeppe.com/dmca-policy/") }
        )
    }
}

@Composable
private fun ExternalSourceCopyrightDialog(
    onDismiss: () -> Unit,
    onOpenDisclaimer: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copyright") },
        text = {
            ScrollableDialogColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("APS NoteCast is a MIDI playback and connection tool. It does not sell, license, or claim ownership of third-party MIDI or E-SEQ files.")
                Text("External archives may contain public-domain works, user-created sequences, live MIDI performances, arrangements, or files with unknown or mixed permissions.")
                Text("A public-domain composition does not always mean every MIDI file, arrangement, or performance of that composition is unrestricted.")
                Text("Use external files only for personal, noncommercial purposes unless you have permission or a separate license. Do not redistribute, sell, remaster, or use files for paid/commercial playback unless allowed by the rights holder.")
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenDisclaimer) { Text("Disclaimer") }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun ExternalSourceRemovalDialog(
    onDismiss: () -> Unit,
    onOpenDmcaPolicy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DMCA / Removal") },
        text = {
            ScrollableDialogColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("If you own rights to a MIDI file, E-SEQ file, arrangement, performance, or related file accessible through an external source shown in APS NoteCast and want it removed or delisted, please contact us.")
                Text("Include the file name or title, the source where it appears, your relationship to the work, and a good contact address. We will review removal requests promptly.")
                Text("APS NoteCast does not claim ownership of third-party files and does not intend to encourage copyright infringement.")
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenDmcaPolicy) { Text("Open policy") }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun ExternalSourceSelector(
    sources: List<ExternalMidiSource>,
    selectedSource: ExternalMidiSource,
    onSourceChange: (ExternalMidiSource) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Source",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(
                    selectedSource.displayName,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                sources.forEach { source ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                source.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            if (source.key == selectedSource.key) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            expanded = false
                            onSourceChange(source)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KuhmannSearchFilters(
    pianoOnly: Boolean,
    onPianoOnlyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KuhmannFilterButton("Piano", pianoOnly, { onPianoOnlyChange(true) }, Modifier.weight(1f))
        KuhmannFilterButton("Ensemble", !pianoOnly, { onPianoOnlyChange(false) }, Modifier.weight(1f))
    }
}

@Composable
private fun KuhmannFilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = if (selected) {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    } else {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = colors,
        contentPadding = PaddingValues(horizontal = 6.dp)
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun KuhmannSmallNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(3)) },
        modifier = modifier,
        singleLine = true,
        label = { Text(label) }
    )
}

private fun KuhmannMidiResult.externalResultFolderLine(): String {
    return folder.ifBlank {
        listOfNotNull(
            sourceName.takeIf { it.isNotBlank() },
            composer.takeIf { it.isNotBlank() },
            instrument.takeIf { it.isNotBlank() }
        ).joinToString(" / ").ifBlank { source.displayName }
    }
}

private fun KuhmannMidiResult.externalResultDetailLine(): String {
    return listOfNotNull(
        license.takeIf { it.isNotBlank() },
        style.takeIf { it.isNotBlank() },
        fileSize.takeIf { it > 0L }?.formatFileSize(),
        channels.takeIf { it.isNotEmpty() }?.joinToString(prefix = "ch ")
    ).joinToString(" - ").ifBlank { source.rightsSummary }
}

@Composable
private fun KuhmannResultRow(
    result: KuhmannMidiResult,
    checked: Boolean,
    imported: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    playbackMode: PlaybackMode,
    onPlay: () -> Unit
) {
    val rowEnabled = enabled && !imported
    val preparing = playbackMode == PlaybackMode.Preparing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = rowEnabled) { onCheckedChange(!checked) }
            .padding(horizontal = 2.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (imported) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = Color(0xFF2E7D32)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Downloaded",
                        modifier = Modifier.size(22.dp),
                        tint = Color.White
                    )
                }
            }
        } else {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.size(34.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    result.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                result.externalResultFolderLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                result.externalResultDetailLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onPlay,
            enabled = enabled && !preparing,
            modifier = Modifier.size(38.dp)
        ) {
            when (playbackMode) {
                PlaybackMode.Preparing -> LoadingIndicator(
                    contentDescription = "Loading ${result.title}",
                    modifier = Modifier.size(22.dp)
                )
                PlaybackMode.Playing -> Icon(Icons.Default.Pause, contentDescription = "Pause ${result.title}")
                PlaybackMode.Paused -> Icon(Icons.Default.PlayArrow, contentDescription = "Resume ${result.title}")
                else -> Icon(Icons.Default.PlayArrow, contentDescription = "Play ${result.title}")
            }
        }
    }
}

@Composable
private fun AddFilesToPlaylistDialog(
    playlist: MidiPlaylist,
    files: List<MidiLibraryItem>,
    onDismiss: () -> Unit,
    onAdd: (List<String>) -> Unit
) {
    val selected = remember(playlist.id, files) { mutableStateMapOf<String, Boolean>() }
    var query by rememberSaveable(playlist.id) { mutableStateOf("") }
    val cleanQuery = query.trim()
    val visibleFiles = remember(files, cleanQuery) {
        if (cleanQuery.isBlank()) {
            files
        } else {
            files.filter { it.matchesPlaylistFileSearch(cleanQuery) }
        }
    }
    val selectedIds = files.filter { selected[it.id] == true }.map { it.id }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val columns = when {
                maxWidth >= 900.dp -> 3
                maxWidth >= 560.dp -> 2
                else -> 1
            }
            val dialogMaxWidth = when (columns) {
                3 -> 1080.dp
                2 -> 760.dp
                else -> 540.dp
            }
            val listMaxHeight = when {
                maxHeight < 430.dp -> 180.dp
                maxHeight < 620.dp -> 300.dp
                else -> 500.dp
            }
            Surface(
                modifier = Modifier
                    .widthIn(max = dialogMaxWidth)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Add files", style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                            Text(
                                playlist.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "${visibleFiles.size}/${files.size} - ${selectedIds.size} selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search files") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { visibleFiles.forEach { selected[it.id] = true } },
                            enabled = visibleFiles.isNotEmpty()
                        ) {
                            Text("Select shown")
                        }
                        TextButton(
                            onClick = { visibleFiles.forEach { selected[it.id] = false } },
                            enabled = visibleFiles.any { selected[it.id] == true }
                        ) {
                            Text("Clear shown")
                        }
                    }
                    if (visibleFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 144.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (files.isEmpty()) "No MIDI files in library." else "No matching files.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = listMaxHeight),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            gridItems(visibleFiles, key = { it.id }) { item ->
                                val checked = selected[item.id] == true
                                PlaylistFilePickerRow(
                                    item = item,
                                    checked = checked,
                                    onCheckedChange = { selected[item.id] = it }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onAdd(selectedIds) }, enabled = selectedIds.isNotEmpty()) {
                            Text("Add ${selectedIds.size}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistFilePickerRow(
    item: MidiLibraryItem,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 2.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(34.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    item.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.durationUs.formatDuration(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                item.originalName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun MidiLibraryItem.matchesPlaylistFileSearch(query: String): Boolean {
    val searchable = "$title $originalName $notes".lowercase()
    return searchTokens(query).all { it in searchable }
}

private fun List<MidiLibraryItem>.filterMidiFiles(query: String): List<MidiLibraryItem> {
    val tokens = searchTokens(query)
    if (tokens.isEmpty()) return this
    return filter { it.matchesMidiFileSearch(tokens) }
}

private fun MidiLibraryItem.matchesMidiFileSearch(tokens: List<String>): Boolean {
    val searchable = "$title $originalName $notes ${durationUs.formatDuration()}".lowercase(Locale.US)
    return tokens.all { it in searchable }
}

private fun MidiPlaylist.matchesPlaylistSearch(query: String, filesById: Map<String, MidiLibraryItem>): Boolean {
    val trackTitles = itemIds.mapNotNull { filesById[it]?.title }.joinToString(" ")
    val searchable = "$name $trackTitles".lowercase(Locale.US)
    return searchTokens(query).all { it in searchable }
}

private fun searchTokens(query: String): List<String> =
    query
        .lowercase(Locale.US)
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

private fun Int.coercedPageStart(totalCount: Int, pageSize: Int): Int {
    if (totalCount <= 0) return 0
    val cleanPageSize = pageSize.coerceAtLeast(1)
    val maxStart = ((totalCount - 1) / cleanPageSize) * cleanPageSize
    return coerceIn(0, maxStart)
}

private fun Long.formatFileSize(): String {
    if (this < 1024L) return "$this B"
    if (this < 1024L * 1024L) return "${this / 1024L} KB"
    return String.format(java.util.Locale.US, "%.1f MB", this / (1024.0 * 1024.0))
}

private data class PlaylistColorOption(val label: String, val hex: String)

private val playlistColorOptions = listOf(
    PlaylistColorOption("Red", "#D64545"),
    PlaylistColorOption("Amber", "#C48A21"),
    PlaylistColorOption("Green", "#2E7D58"),
    PlaylistColorOption("Teal", "#148C8C"),
    PlaylistColorOption("Blue", "#3B6EA8"),
    PlaylistColorOption("Violet", "#7655A6"),
    PlaylistColorOption("Pink", "#B84F7D"),
    PlaylistColorOption("Gray", "#68707A")
)

@Composable
private fun PlaylistColorDialog(
    playlist: MidiPlaylist,
    onDismiss: () -> Unit,
    onSelectColor: (String?) -> Unit
) {
    var customColorText by rememberSaveable(playlist.id, playlist.colorHex) {
        mutableStateOf(playlist.colorHex ?: "#")
    }
    val customColorHex = customColorText.normalizedPlaylistColorHex()
    val customColor = customColorHex.toPlaylistColor()
    val customColorIncomplete = customColorText != "#" && customColorHex == null
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FiberManualRecord, contentDescription = null) },
        title = { Text("Playlist color") },
        text = {
            ScrollableDialogColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        PlaylistColorChoice(
                            label = "No color",
                            color = null,
                            selected = playlist.colorHex.isNullOrBlank(),
                            onClick = { onSelectColor(null) }
                        )
                    }
                    gridItems(playlistColorOptions, key = { it.hex }) { option ->
                        PlaylistColorChoice(
                            label = option.label,
                            color = option.hex.toPlaylistColor(),
                            selected = playlist.colorHex.equals(option.hex, ignoreCase = true),
                            onClick = { onSelectColor(option.hex) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = customColor ?: MaterialTheme.colorScheme.outline
                    ) {}
                    OutlinedTextField(
                        value = customColorText,
                        onValueChange = { customColorText = it.sanitizedPlaylistColorInput() },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Custom hex") },
                        isError = customColorIncomplete
                    )
                    Button(
                        onClick = { customColorHex?.let(onSelectColor) },
                        enabled = customColorHex != null
                    ) {
                        Text("Apply")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PlaylistColorChoice(
    label: String,
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
                color = color ?: MaterialTheme.colorScheme.outline
            ) {
                if (color == null) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun String?.toPlaylistColor(): Color? =
    this?.takeIf { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }?.let { hex ->
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    }

private fun String.normalizedPlaylistColorHex(): String? {
    val hex = trim().removePrefix("#")
    if (!hex.matches(Regex("^[0-9A-Fa-f]{6}$"))) return null
    return "#${hex.uppercase(java.util.Locale.US)}"
}

private fun String.sanitizedPlaylistColorInput(): String {
    val hex = trim()
        .removePrefix("#")
        .filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        .take(6)
        .uppercase(java.util.Locale.US)
    return if (hex.isEmpty()) "#" else "#$hex"
}

@Composable
private fun RenameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddPlaylistDialog(
    title: String = "New playlist",
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RecordDialog(
    state: AppUiState,
    service: NoteCastService,
    settings: AppSettings,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("APS NoteCast Recording") }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val recording = state.recording
    AlertDialog(
        onDismissRequest = { if (!recording.isRecording && !recording.isSaving && !recording.isCountingDown) onDismiss() },
        icon = { Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Record MIDI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("File name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(recording.message, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${recording.eventCount} events - ${recording.durationUs.formatClockTime()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (recording.isRecording) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                if (!state.connection.connected) {
                    Text("Connect a MIDI device before recording.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when {
                recording.isSaving -> TextButton(onClick = {}) { Text("Saving") }
                recording.isCountingDown -> TextButton(onClick = {}) { Text("Starting") }
                recording.isRecording -> Button(onClick = {
                    service.finishRecording(title)
                    onDismiss()
                }) {
                    Text("Save")
                }
                else -> Button(
                    onClick = { service.startRecording(title) },
                    enabled = state.connection.connected
                ) {
                    Text("Start")
                }
            }
        },
        dismissButton = {
            if (recording.isRecording || recording.isCountingDown) {
                TextButton(onClick = {
                    if (settings.confirmDiscardRecording) {
                        confirmDiscard = true
                    } else {
                        service.cancelRecording()
                        onDismiss()
                    }
                }) {
                    Text("Cancel")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard recording?",
            message = "The active recording will be lost.",
            confirmLabel = "Discard",
            onDismiss = { confirmDiscard = false },
            onConfirm = {
                service.cancelRecording()
                confirmDiscard = false
                onDismiss()
            }
        )
    }
}
