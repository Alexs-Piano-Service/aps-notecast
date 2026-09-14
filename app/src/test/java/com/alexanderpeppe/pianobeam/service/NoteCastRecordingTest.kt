package com.alexanderpeppe.pianobeam.service

import android.app.Service
import android.content.ContextWrapper
import android.content.Context
import android.content.pm.ServiceInfo
import android.content.Intent
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.net.Uri
import android.os.Looper
import android.os.Handler
import com.alexanderpeppe.pianobeam.data.AppUiState
import com.alexanderpeppe.pianobeam.data.MidiRepository
import com.alexanderpeppe.pianobeam.data.PlaybackMode
import com.alexanderpeppe.pianobeam.data.PlaybackUiState
import com.alexanderpeppe.pianobeam.data.RecordingUiState
import com.alexanderpeppe.pianobeam.data.RecordingRecoveryStore
import com.alexanderpeppe.pianobeam.midi.MidiFileParser
import com.alexanderpeppe.pianobeam.midi.MidiFileWriter
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [NoteCastRecordingTest.TestMidiManager::class])
class NoteCastRecordingTest {
    // Robolectric does not supply the platform MIDI service. Only device discovery is stubbed;
    // the recording, repository, lifecycle, and foreground-service code below run unchanged.
    @Implements(MidiManager::class)
    class TestMidiManager {
        @Implementation fun registerDeviceCallback(callback: MidiManager.DeviceCallback, handler: Handler?) = Unit
        @Implementation fun unregisterDeviceCallback(callback: MidiManager.DeviceCallback) = Unit
        @Implementation fun getDevices(): Array<MidiDeviceInfo> = emptyArray()
    }
    @get:Rule val directory = TemporaryFolder()
    private lateinit var controller: ServiceController<NoteCastService>
    private lateinit var service: NoteCastService
    private val events = listOf(
        MidiFileWriter.RecordedEvent(0, byteArrayOf(0x90.toByte(), 60, 100)),
        MidiFileWriter.RecordedEvent(500_000, byteArrayOf(0x80.toByte(), 60, 0))
    )

    @Before fun setUp() {
        shadowOf(RuntimeEnvironment.getApplication()).setSystemService(Context.MIDI_SERVICE, Shadow.newInstanceOf(MidiManager::class.java))
        createService()
    }
    @After fun tearDown() { if (::controller.isInitialized) controller.destroy() }

    private fun createService() {
        controller = Robolectric.buildService(NoteCastService::class.java).create()
        service = controller.get()
        await { !service.state.value.recording.isRecovering }
    }

    @Test
    fun failedSaveCanBeRetriedWithoutRecapturingAndCannotBeOverwritten() {
        val midiDirectory = failRepositoryWrites()
        seedCapture()
        service.finishRecording("Original take")
        service.finishRecording("Duplicate save")
        service.cancelRecording()
        service.startRecording("Replacement during save")
        await { !service.state.value.recording.isSaving }
        val failed = service.state.value.recording
        assertTrue(failed.hasPendingRecording)
        assertFalse(failed.isRecording)
        assertEquals("Original take", failed.title)
        assertEquals(2, failed.eventCount)
        assertEquals(500_000L, failed.durationUs)
        service.startRecording("Replacement")
        assertEquals(failed, service.state.value.recording)

        assertTrue(midiDirectory.delete())
        assertTrue(midiDirectory.mkdir())
        service.finishRecording("Original take")
        await { !service.state.value.recording.isSaving }
        assertFalse(service.state.value.recording.hasPendingRecording)
        val saved = midiDirectory.listFiles()!!.single()
        assertArrayEquals(MidiFileWriter.write("Original take", events), saved.readBytes())
        assertFalse(File(service.noBackupFilesDir, "recording.recovery").exists())
    }

    @Test
    fun destroyAndRecreateRestoresActiveCaptureAsPendingAndExportKeepsIt() {
        seedCapture()
        controller.destroy()
        createService()
        val recovered = service.state.value.recording
        assertTrue(recovered.hasPendingRecording)
        assertFalse(recovered.isRecording)
        assertEquals("Original take", recovered.title)
        assertEquals(2, recovered.eventCount)
        val copy = File(directory.root, "export.mid")
        service.exportPendingRecording(Uri.fromFile(copy), recovered.title)
        await { !service.state.value.recording.isSaving }
        assertArrayEquals(MidiFileWriter.write(recovered.title, events), copy.readBytes())
        assertTrue(service.state.value.recording.hasPendingRecording)
        assertTrue(File(service.noBackupFilesDir, "recording.recovery").exists())
        service.cancelRecording()
        await { !service.state.value.recording.isSaving }
        assertFalse(service.state.value.recording.hasPendingRecording)
        assertFalse(File(service.noBackupFilesDir, "recording.recovery").exists())
    }

    @Test
    fun failedSaveSurvivesRecreationAndCanThenBeSaved() {
        failRepositoryWrites()
        seedCapture()
        service.finishRecording("Original take")
        await { !service.state.value.recording.isSaving }
        controller.destroy()
        createService()
        assertTrue(service.state.value.recording.hasPendingRecording)
        service.finishRecording(service.state.value.recording.title)
        await { !service.state.value.recording.isSaving }
        assertFalse(service.state.value.recording.hasPendingRecording)
        val item = service.state.value.files.single { it.title == "Original take" }
        val sequence = MidiFileParser.parse(MidiRepository(service).fileFor(item).readBytes(), item.title)
        assertEquals(2, sequence.events.size)
    }

    @Test
    fun recentsRemovalKeepsRecordingCountdownSavingAndPlaybackAlive() {
        for (recording in listOf(
            RecordingUiState(isRecording = true), RecordingUiState(isCountingDown = true), RecordingUiState(isSaving = true)
        )) {
            setState(service.state.value.copy(recording = recording, playback = PlaybackUiState()))
            assertEquals(Service.START_STICKY, service.onStartCommand(null, 0, 1))
            service.onTaskRemoved(Intent())
            assertFalse(shadowOf(service).isStoppedBySelf)
        }
        setState(service.state.value.copy(recording = RecordingUiState(), playback = PlaybackUiState(mode = PlaybackMode.Playing)))
        service.onTaskRemoved(Intent())
        assertFalse(shadowOf(service).isStoppedBySelf)
        setState(service.state.value.copy(playback = PlaybackUiState()))
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 2))
        service.onTaskRemoved(Intent())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun disconnectMovesActiveCaptureToPendingInsteadOfResettingIt() {
        seedCapture()
        NoteCastService::class.java.getDeclaredMethod("closeMidiConnection").apply { isAccessible = true }.invoke(service)
        assertTrue(service.state.value.recording.hasPendingRecording)
        assertEquals(2, service.state.value.recording.eventCount)
        assertFalse(service.state.value.recording.isRecording)
    }

    @Test
    fun activeCaptureIsCheckpointedBeforeAnyStopOrDestroyCallback() {
        seedCapture()
        invoke("startRecordingCheckpoints")
        val file = File(service.noBackupFilesDir, "recording.recovery")
        await { file.exists() }
        val recovered = RecordingRecoveryStore(file).read()!!
        assertArrayEquals(MidiFileWriter.write("Original take", events), recovered.midiBytes())
        assertTrue(service.state.value.recording.isRecording)
        service.cancelRecording()
        await { !service.state.value.recording.isSaving }
        assertFalse(file.exists())
        // A late checkpoint callback after discard cannot recreate the recovery file.
        invoke("checkpointRecording")
        assertFalse(file.exists())
    }

    @Test
    fun exportFailureRetainsTheCaptureForRetryAndDiscard() {
        seedCapture()
        invoke("closeMidiConnection")
        service.exportPendingRecording(Uri.fromFile(directory.root), "Original take")
        await { !service.state.value.recording.isSaving }
        assertTrue(service.state.value.recording.hasPendingRecording)
        assertEquals(2, service.state.value.recording.eventCount)
        assertTrue(File(service.noBackupFilesDir, "recording.recovery").exists())
    }

    @Test
    fun playbackCleanupDoesNotRemoveRecordingForegroundNotificationOrWakeLock() {
        seedCapture()
        invoke("startForegroundForRecording")
        invoke("acquireWakeLock")
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE, service.foregroundServiceType)
        invoke("stopForegroundIfNeeded")
        invoke("releaseWakeLock")
        assertFalse(shadowOf(service).isForegroundStopped)
        assertNotNull(shadowOf(service).lastForegroundNotification)
        val wakeLock = NoteCastService::class.java.getDeclaredField("wakeLock").apply { isAccessible = true }.get(service)
            as android.os.PowerManager.WakeLock
        assertTrue(wakeLock.isHeld)
        invoke("closeMidiConnection")
        assertTrue(shadowOf(service).isForegroundStopped)
        assertFalse(wakeLock.isHeld)
    }

    private fun invoke(name: String) {
        NoteCastService::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(service)
    }

    private fun failRepositoryWrites(): File {
        val context = object : ContextWrapper(service) {
            override fun getFilesDir(): File = directory.root
        }
        val repository = MidiRepository(context)
        val midiDirectory = File(directory.root, "midi")
        // Turn the expected directory into a file: a deterministic IOException at the save boundary.
        midiDirectory.delete()
        midiDirectory.writeText("injected write failure")
        NoteCastService::class.java.getDeclaredField("repository").apply { isAccessible = true }.set(service, repository)
        return midiDirectory
    }

    private fun seedCapture() {
        @Suppress("UNCHECKED_CAST")
        val capture = NoteCastService::class.java.getDeclaredField("recordingEvents").apply { isAccessible = true }
            .get(service) as MutableList<MidiFileWriter.RecordedEvent>
        capture.addAll(events)
        setState(service.state.value.copy(recording = RecordingUiState(
            isRecording = true, title = "Original take", eventCount = 2, durationUs = 500_000
        )))
    }

    private fun setState(state: AppUiState) {
        @Suppress("UNCHECKED_CAST")
        val flow = NoteCastService::class.java.getDeclaredField("_state").apply { isAccessible = true }
            .get(service) as MutableStateFlow<AppUiState>
        flow.value = state
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Timed out waiting for recording operation: ${service.state.value.recording}", condition())
    }
}
