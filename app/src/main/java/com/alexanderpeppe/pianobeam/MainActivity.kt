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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.alexanderpeppe.pianobeam.data.AppSettings
import com.alexanderpeppe.pianobeam.data.AppUiState
import com.alexanderpeppe.pianobeam.data.AppThemeMode
import com.alexanderpeppe.pianobeam.data.BleMidiDeviceItem
import com.alexanderpeppe.pianobeam.data.MidiLibraryItem
import com.alexanderpeppe.pianobeam.data.MidiPlaylist
import com.alexanderpeppe.pianobeam.data.PlaybackMode
import com.alexanderpeppe.pianobeam.data.PlaybackUiState
import com.alexanderpeppe.pianobeam.data.formatClockTime
import com.alexanderpeppe.pianobeam.data.formatDuration
import com.alexanderpeppe.pianobeam.reporting.BugReportClient
import com.alexanderpeppe.pianobeam.reporting.BugReportInput
import com.alexanderpeppe.pianobeam.reporting.CrashReportStore
import com.alexanderpeppe.pianobeam.settings.AppSettingsStore
import com.alexanderpeppe.pianobeam.service.NoteCastService
import com.alexanderpeppe.pianobeam.ui.settings.SettingsDialog
import com.alexanderpeppe.pianobeam.ui.theme.NoteCastTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
        startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)

        setContent {
            val appSettings by settingsStore.settings.collectAsState()

            NoteCastTheme(themeMode = appSettings.themeMode) {
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
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun runtimePermissionsToRequest(): Array<String> {
        val permissions = bluetoothRuntimePermissions().toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions.toTypedArray()
    }

    private fun hasAllRuntimePermissions(): Boolean = bluetoothRuntimePermissions().all { permission ->
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
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Image(
                    painter = painterResource(R.drawable.logo_icon),
                    contentDescription = "APS NoteCast logo",
                    modifier = Modifier
                        .size(86.dp)
                        .padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            }
            CircularProgressIndicator()
            Text("Starting APS NoteCast")
        }
    }
}

@Composable
private fun BrandMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Image(
        painter = painterResource(if (darkTheme) R.drawable.logo_mark_white else R.drawable.logo_mark_dark),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun PrimaryLogoBanner(modifier: Modifier = Modifier) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Image(
        painter = painterResource(if (darkTheme) R.drawable.logo_primary_white else R.drawable.logo_primary_dark),
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

    LaunchedEffect(settings) {
        service.applySettings(settings)
    }
    LaunchedEffect(permissionsGranted) {
        service.refreshKnownDevices()
    }
    LaunchedEffect(showConnector) {
        service.setAutoReconnectPausedForDevicePicker(showConnector)
    }
    LaunchedEffect(permissionsGranted, settings.scanOnLaunch) {
        if (permissionsGranted && settings.scanOnLaunch) service.startBleScan()
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
                                    .size(40.dp)
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
                service = service
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

    if (showWizard && !state.connection.connected) {
        ConnectionWizardDialog(
            state = state,
            service = service,
            permissionsGranted = permissionsGranted,
            onRequestPermissions = onRequestPermissions,
            onRequestBluetoothEnable = onRequestBluetoothEnable,
            onOpenBluetoothPairing = onOpenBluetoothPairing,
            onOpenBatterySettings = onOpenBatterySettings,
            autoReconnectEnabled = settings.autoReconnectEnabled,
            onDismiss = { showWizard = false }
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
            text = { Text(diagnostics) },
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
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
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
                        .weight(1.35f)
                        .fillMaxHeight()
                )
                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DevicePanel(
                        state = state,
                        service = service,
                        permissionsGranted = permissionsGranted,
                        onRequestPermissions = onRequestPermissions,
                        onRequestBluetoothEnable = onRequestBluetoothEnable,
                        onOpenBluetoothPairing = onOpenBluetoothPairing,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PlayerPanel(
                        state = state,
                        service = service,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
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
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
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
    var showRecordDialog by rememberSaveable { mutableStateOf(false) }
    var addFilesPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameFileId by rememberSaveable { mutableStateOf<String?>(null) }
    var renamePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteFileId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    val expandedPlaylists = remember { mutableStateMapOf<String, Boolean>() }
    val playlistBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingFile by remember { mutableStateOf<MidiLibraryItem?>(null) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var paneOrigin by remember { mutableStateOf(Offset.Zero) }
    val filesById = remember(state.files) { state.files.associateBy { it.id } }

    fun finishDrag() {
        val file = draggingFile
        val position = dragPosition
        if (file != null && position != null) {
            val targetPlaylist = playlistBounds.entries.firstOrNull { it.value.contains(position) }?.key
            if (targetPlaylist != null) service.addToPlaylist(targetPlaylist, file.id)
        }
        draggingFile = null
        dragPosition = null
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    LibraryHero(
                        fileCount = state.files.size,
                        playlistCount = state.playlists.size,
                        onImport = { importLauncher.launch(arrayOf("audio/midi", "audio/x-midi", "application/octet-stream", "*/*")) },
                        onRecord = { showRecordDialog = true },
                        onCreatePlaylist = { showPlaylistDialog = true },
                        onOpenConnection = onOpenConnection
                    )
                }

                if (state.files.isEmpty() && state.playlists.isEmpty()) {
                    item {
                        EmptyLibraryCard(
                            onImport = { importLauncher.launch(arrayOf("audio/midi", "audio/x-midi", "application/octet-stream", "*/*")) },
                            onCreatePlaylist = { showPlaylistDialog = true }
                        )
                    }
                }

                if (state.playlists.isNotEmpty()) {
                    item { SectionLabel("Playlists") }
                    items(state.playlists, key = { it.id }) { playlist ->
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
                    item { SectionLabel("MIDI files") }
                    items(state.files, key = { it.id }) { item ->
                        MidiFileRow(
                            item = item,
                            selected = selectedKind == "file" && selectedId == item.id,
                            connected = state.connection.connected,
                            preparing = state.playback.isPreparing && state.playback.currentItemId == item.id,
                            playing = state.playback.isPlaying && state.playback.currentItemId == item.id,
                            paused = state.playback.isPaused && state.playback.currentItemId == item.id,
                            onSelect = { onSelectFile(item.id) },
                            onPlay = {
                                onSelectFile(item.id)
                                service.playFile(item.id)
                            },
                            onPause = { service.pausePlayback() },
                            onResume = { service.resumePlayback() },
                            onRename = { renameFileId = item.id },
                            onExport = { onExportMidi(item) },
                            onShare = { onShareMidi(item.id) },
                            onDelete = { deleteFileId = item.id },
                            onDragStart = { file, rootOffset ->
                                draggingFile = file
                                dragPosition = rootOffset
                            },
                            onDrag = { delta -> dragPosition = (dragPosition ?: Offset.Zero) + delta },
                            onDragEnd = ::finishDrag,
                            onDragCancel = {
                                draggingFile = null
                                dragPosition = null
                            }
                        )
                    }
                }
            }

            draggingFile?.let { file ->
                val position = dragPosition ?: Offset.Zero
                Surface(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = position.x - paneOrigin.x + 12f
                            translationY = position.y - paneOrigin.y + 12f
                        },
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface)
                        Text(file.title, color = MaterialTheme.colorScheme.inverseOnSurface, maxLines = 1)
                    }
                }
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

    if (showRecordDialog) {
        RecordDialog(
            state = state,
            service = service,
            settings = settings,
            onDismiss = { showRecordDialog = false }
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
}

@Composable
private fun LibraryHero(
    fileCount: Int,
    playlistCount: Int,
    onImport: () -> Unit,
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
        icon = { BrandMark(modifier = Modifier.size(56.dp), contentDescription = "APS NoteCast") },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("APS NoteCast")
                PrimaryLogoBanner()
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoLine("Version", "0.1.0")
                InfoLine("Built for", "BLE MIDI playback to WIDI and compatible player-piano adapters")
                InfoLine("Library", "Import MIDI files, use the bundled Chopin demo, create playlists, or record BLE MIDI input")
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                feedback = "Could not send bug report: ${throwable.message ?: "unknown error"}"
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
private fun MidiFileRow(
    item: MidiLibraryItem,
    selected: Boolean,
    connected: Boolean,
    preparing: Boolean,
    playing: Boolean,
    paused: Boolean,
    onSelect: () -> Unit,
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
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates = it }
            .clickable(onClick = onSelect)
            .dragFile(item, coordinates, onDragStart, onDrag, onDragEnd, onDragCancel),
        shape = RoundedCornerShape(4.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(24.dp))
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
                if (item.notes.isNotBlank()) {
                    Text(item.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 1)
                }
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

private fun Modifier.dragFile(
    item: MidiLibraryItem,
    coordinates: LayoutCoordinates?,
    onDragStart: (MidiLibraryItem, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
): Modifier = pointerInput(item.id, coordinates) {
    detectDragGesturesAfterLongPress(
        onDragStart = { localOffset ->
            onDragStart(item, coordinates?.localToRoot(localOffset) ?: Offset.Zero)
        },
        onDrag = { _, dragAmount -> onDrag(dragAmount) },
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel
    )
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
    onClone: () -> Unit,
    onDelete: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val color = when {
        highlighted -> MaterialTheme.colorScheme.tertiaryContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
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
                    Text("BLE MIDI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                onOpenBluetoothPairing = onOpenBluetoothPairing,
                compact = true
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
    service: NoteCastService
) {
    val playback = state.playback
    val selectedTitle = when (selectedKind) {
        "playlist" -> state.playlists.firstOrNull { it.id == selectedId }?.name
        else -> state.files.firstOrNull { it.id == selectedId }?.title
    }
    Surface(tonalElevation = 4.dp) {
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
            Spacer(Modifier.weight(1f))
            Text(durationUs.formatClockTime(), style = MaterialTheme.typography.bodySmall)
        }
    }
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
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp))
        Slider(
            value = state.volumePercent.toFloat(),
            onValueChange = { service.setVolume(it.roundToInt()) },
            valueRange = 0f..100f,
            enabled = state.connection.connected,
            modifier = Modifier.weight(1f)
        )
    }
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
                    connection.deviceName ?: connection.rememberedDeviceName ?: "BLE MIDI",
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
    onOpenBatterySettings: () -> Unit,
    autoReconnectEnabled: Boolean,
    onDismiss: () -> Unit
) {
    var pairingScreenOpened by rememberSaveable { mutableStateOf(false) }
    val hasPairedMidiDevice = state.bleDevices.any {
        it.source.contains("Paired", ignoreCase = true) || it.source.contains("Android", ignoreCase = true)
    }

    LaunchedEffect(permissionsGranted, state.connection.bluetoothEnabled, state.connection.connected, hasPairedMidiDevice) {
        if (
            permissionsGranted &&
            state.connection.bluetoothEnabled &&
            !state.connection.connected &&
            !hasPairedMidiDevice &&
            !pairingScreenOpened
        ) {
            pairingScreenOpened = true
            delay(700L)
            onOpenBluetoothPairing()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { BrandMark(modifier = Modifier.size(64.dp), contentDescription = "APS NoteCast") },
        title = { Text("Connect BLE MIDI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryLogoBanner()
                Text("Choose your piano adapter to start. You can continue offline and connect later.")
                BatteryUsageRecommendation(onOpenBatterySettings = onOpenBatterySettings)
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
private fun BatteryUsageRecommendation(onOpenBatterySettings: () -> Unit) {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Recommended battery setting", fontWeight = FontWeight.SemiBold)
            }
            Text(
                "For reliable Bluetooth MIDI Service playback, set APS NoteCast battery usage to Unrestricted. Android usually lists this under App battery usage, Allow background usage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            OutlinedButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
                Text("Open app battery settings")
            }
        }
    }
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
        icon = { BrandMark(modifier = Modifier.size(56.dp), contentDescription = "APS NoteCast") },
        title = { Text("BLE MIDI connection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    onOpenBluetoothPairing: () -> Unit,
    compact: Boolean = false
) {
    var removeDeviceAddress by rememberSaveable { mutableStateOf<String?>(null) }

    if (!permissionsGranted) {
        Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Bluetooth, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Grant Bluetooth access")
        }
        return
    }

    if (!state.connection.bluetoothAvailable) {
        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.errorContainer) {
            Text(
                "This Android device does not report Bluetooth hardware.",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        return
    }

    if (!state.connection.bluetoothEnabled) {
        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bluetooth is off", fontWeight = FontWeight.SemiBold)
                    Text("Turn it on to find WIDI devices.", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onRequestBluetoothEnable) {
                    Text("Turn on")
                }
            }
        }
        return
    }

    if (state.connection.rememberedDeviceName != null && !state.connection.connected) {
        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.connection.autoReconnectSuppressed) {
                        "Reconnect paused: ${state.connection.rememberedDeviceName}"
                    } else {
                        "Auto-reconnect: ${state.connection.rememberedDeviceName}"
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = {
                        service.autoReconnectIfPossible(force = true)
                    }
                ) {
                    Text(if (state.connection.autoReconnectSuppressed) "Connect" else "Find")
                }
            }
        }
    }

    if (state.connection.connecting || state.connection.scanning) {
        LoadingStatusPanel(
            title = if (state.connection.connecting) "Connecting to MIDI" else "Looking for MIDI",
            message = state.connection.message,
            actionLabel = if (state.connection.scanning) "Stop" else null,
            onAction = if (state.connection.scanning) ({ service.stopBleScan() }) else null
        )
    }

    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("1. Pair your WIDI device in Android Bluetooth.", fontWeight = FontWeight.SemiBold)
            Text(
                "2. Return to APS NoteCast, scan, then tap Connect on the paired MIDI device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onOpenBluetoothPairing, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Pair new device")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { service.startBleScan() },
            enabled = !state.connection.scanning && !state.connection.connecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Scan")
        }
    }

    val deviceResults = state.bleDevices.filterNot { sameDeviceTarget(it.address, state.connection.address) }

    if (state.connection.connected) {
        ConnectedDeviceRow(
            state = state,
            service = service,
            onRemove = { address -> removeDeviceAddress = address }
        )
    }

    if (deviceResults.isEmpty() && !state.connection.scanning && !state.connection.connecting) {
        Text(
            if (state.connection.connected) "Scan to find another MIDI device." else "No devices found. Pair first, then scan.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else if (deviceResults.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier.heightIn(max = if (compact) 210.dp else 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(deviceResults, key = { it.address }) { device ->
                DeviceResultRow(
                    device = device,
                    actionLabel = if (state.connection.connected) "Switch" else "Connect",
                    onConnect = { service.connect(device.address) },
                    onRemove = { removeDeviceAddress = device.address }
                )
            }
        }
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
private fun ConnectedDeviceRow(
    state: AppUiState,
    service: NoteCastService,
    onRemove: (String) -> Unit
) {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(state.connection.deviceName ?: "BLE MIDI Device", fontWeight = FontWeight.SemiBold)
                Text("Connected", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { service.disconnect() }) { Text("Disconnect") }
            state.connection.address?.let { address ->
                IconButton(onClick = { onRemove(address) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove ${state.connection.deviceName ?: "device"}")
                }
            }
        }
    }
}

@Composable
private fun DeviceResultRow(
    device: BleMidiDeviceItem,
    actionLabel: String,
    onConnect: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bluetooth, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        when {
                            device.standardBleMidi -> "BLE MIDI"
                            device.likelyMidi -> "Likely MIDI"
                            else -> "BLE"
                        },
                        device.source,
                        device.detail,
                        device.rssi?.let { "$it dBm" }
                    ).joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(onClick = onConnect) { Text(actionLabel) }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove ${device.name}")
            }
        }
    }
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
private fun AddFilesToPlaylistDialog(
    playlist: MidiPlaylist,
    files: List<MidiLibraryItem>,
    onDismiss: () -> Unit,
    onAdd: (List<String>) -> Unit
) {
    val selected = remember(playlist.id, files) { mutableStateMapOf<String, Boolean>() }
    val selectedIds = files.filter { selected[it.id] == true }.map { it.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        title = { Text("Add files") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(playlist.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(files, key = { it.id }) { item ->
                        val checked = selected[item.id] == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected[item.id] = !checked }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { selected[item.id] = it })
                            Column(Modifier.weight(1f)) {
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.durationUs.formatDuration(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(selectedIds) }, enabled = selectedIds.isNotEmpty()) {
                Text("Add ${selectedIds.size}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
        title = { Text("New playlist") },
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
                    Text("Connect BLE MIDI before recording.", color = MaterialTheme.colorScheme.error)
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
