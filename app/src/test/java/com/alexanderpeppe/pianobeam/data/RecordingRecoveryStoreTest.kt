package com.alexanderpeppe.pianobeam.data

import com.alexanderpeppe.pianobeam.midi.MidiFileParser
import com.alexanderpeppe.pianobeam.midi.MidiFileWriter
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecordingRecoveryStoreTest {
    @get:Rule val directory = TemporaryFolder()
    private val file get() = File(directory.root, "recording.recovery")
    private val recording = PendingRecording("Café 日本語", listOf(
        MidiFileWriter.RecordedEvent(123, byteArrayOf(0x90.toByte(), 60, 100)),
        MidiFileWriter.RecordedEvent(500_123, byteArrayOf(0x80.toByte(), 60, 0))
    ))

    @Test
    fun recoveryRoundTripsOriginalTitleTimestampsAndMidiBytes() {
        RecordingRecoveryStore(file).write(recording)
        val recovered = RecordingRecoveryStore(file).read()!!
        assertEquals(recording.title, recovered.title)
        assertEquals(recording.id, recovered.id)
        assertEquals(recording.events.map { it.timeUs }, recovered.events.map { it.timeUs })
        recording.events.zip(recovered.events).forEach { (expected, actual) -> assertArrayEquals(expected.data, actual.data) }
        assertArrayEquals(recording.midiBytes(), recovered.midiBytes())
        assertEquals(2, MidiFileParser.parse(recovered.midiBytes(), "Recovered.mid").events.size)
    }

    @Test
    fun failedCheckpointPreservesLastCompleteCapture() {
        val store = RecordingRecoveryStore(file)
        store.write(recording)
        val bytes = file.readBytes()
        // DataOutputStream.writeUTF throws IOException after startWrite, without exhausting storage.
        assertThrows(IOException::class.java) { store.write(recording.copy(title = "x".repeat(70_000))) }
        assertArrayEquals(bytes, file.readBytes())
        assertArrayEquals(recording.midiBytes(), RecordingRecoveryStore(file).read()!!.midiBytes())
    }

    @Test
    fun incompleteAtomicWriteRestoresLastCommittedCheckpoint() {
        RecordingRecoveryStore(file).write(recording)
        File("${file.path}.new").writeText("incomplete checkpoint")
        assertArrayEquals(recording.midiBytes(), RecordingRecoveryStore(file).read()!!.midiBytes())
    }

    @Test
    fun corruptRecoveryIsPreservedUntilExplicitDiscard() {
        file.writeText("broken recovery")
        assertThrows(IllegalArgumentException::class.java) { RecordingRecoveryStore(file).read() }
        assertEquals("broken recovery", file.readText())
        RecordingRecoveryStore(file).delete()
        assertNull(RecordingRecoveryStore(file).read())
    }
}
