package com.alexanderpeppe.pianobeam.data

import android.content.ContextWrapper
import com.alexanderpeppe.pianobeam.midi.MidiFileWriter
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MidiRepositoryRecoveryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context by lazy {
        object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = temporaryFolder.root
        }
    }
    private val metadata get() = File(context.filesDir, "library.json")
    private val midiDirectory get() = File(context.filesDir, "midi")
    private val midiBytes = MidiFileWriter.write(
        "Test song",
        listOf(
            MidiFileWriter.RecordedEvent(0, byteArrayOf(0x90.toByte(), 60, 100)),
            MidiFileWriter.RecordedEvent(500_000, byteArrayOf(0x80.toByte(), 60, 0))
        )
    )

    @Test
    fun absentLibraryCanInitializeDemosAndPersistThem() {
        val repository = MidiRepository(context)
        assertEquals(LibrarySnapshot(), repository.load())
        assertFalse(metadata.exists())
        val initialized = repository.ensureDemoMidi()
        assertEquals(2, initialized.files.size)
        assertEquals(1, initialized.playlists.size)
        assertEquals(initialized, MidiRepository(context).load())
    }

    @Test
    fun malformedLibraryBlocksDemoAndAllMutationsWithoutChangingExistingBytes() {
        val original = seedLibrary()
        val song = original.files.single()
        val backup = MidiRepository(context).exportBackupJson()
        val malformed = "{\"files\":[broken metadata".toByteArray()
        metadata.writeBytes(malformed)
        val storedFiles = midiDirectory.listFiles()!!.associate { it.name to it.readBytes() }
        val mutations: List<(MidiRepository) -> Any> = listOf(
            { it.ensureDemoMidi() },
            { it.createPlaylist("New playlist") },
            { it.renameFile(song.id, "Replacement title") },
            { it.deleteFile(song.id) },
            { it.purgeLibrary() },
            { it.restoreBackupJson(backup) },
            { it.importMidiBytes(midiBytes, "Imported.mid") },
            { it.importZipBytes(byteArrayOf(), "Imported.zip") },
            { it.saveRecordedMidi("Recording", midiBytes) },
            { it.stageMidiBytesImport(MidiRepository.MidiBytesImportRequest("test", midiBytes, "Test.mid")) },
            { it.commitStagedMidiImports(emptyList()) }
        )
        mutations.forEach { mutation ->
            val repository = MidiRepository(context)
            assertRecoveryError { mutation(repository) }
            // A second load on the same instance must not see a cached empty snapshot.
            assertRecoveryError { repository.load() }
            assertArrayEquals(malformed, metadata.readBytes())
            assertEquals(storedFiles.keys, midiDirectory.listFiles()!!.map { it.name }.toSet())
            storedFiles.forEach { (name, bytes) -> assertArrayEquals(bytes, File(midiDirectory, name).readBytes()) }
        }
    }

    @Test
    fun structurallyInvalidMetadataCannotBeTreatedAsAnEmptyLibrary() {
        val invalidSnapshots = listOf(
            "{}",
            "{\"files\":{},\"playlists\":[]}",
            "{\"files\":[42],\"playlists\":[]}",
            "{\"files\":[{}],\"playlists\":[]}",
            "{\"files\":[],\"playlists\":[null]}",
            "{\"files\":[],\"playlists\":[{\"id\":\"p\",\"itemIds\":{}}]}"
        )
        invalidSnapshots.forEach { json ->
            metadata.writeText(json)
            assertRecoveryError { MidiRepository(context).ensureDemoMidi() }
            assertEquals(json, metadata.readText())
        }
    }

    @Test
    fun readFailureIsReportedAndCanBeRetriedAfterRepair() {
        assertTrue(metadata.mkdir()) // Deterministic read error without depending on OS permission behavior.
        val sentinel = File(metadata, "keep.txt").apply { writeText("preserve me") }
        val repository = MidiRepository(context)
        assertRecoveryError { repository.ensureDemoMidi() }
        assertEquals("preserve me", sentinel.readText())
        assertTrue(sentinel.delete())
        assertTrue(metadata.delete())
        metadata.writeText("{\"files\":[],\"playlists\":[],\"bundledDemosEnabled\":false}")
        assertEquals(LibrarySnapshot(bundledDemosEnabled = false), repository.ensureDemoMidi())
    }

    @Test
    fun failedParseCanBeRetriedOnSameRepositoryAfterOriginalMetadataIsRestored() {
        val expected = seedLibrary()
        val originalBytes = metadata.readBytes()
        metadata.writeText("malformed")
        val repository = MidiRepository(context)
        repeat(2) { assertRecoveryError { repository.load() } }
        metadata.writeBytes(originalBytes)
        assertEquals(expected, repository.load())
        val withDemos = repository.ensureDemoMidi()
        assertTrue(withDemos.files.contains(expected.files.single()))
        assertTrue(withDemos.playlists.contains(expected.playlists.single()))
        assertEquals(withDemos, MidiRepository(context).load())
    }

    @Test
    fun malformedAtomicBackupDoesNotReplaceBaseOrDeletePendingWrite() {
        seedLibrary()
        val originalBytes = metadata.readBytes()
        val backup = File("${metadata.path}.bak").apply { writeText("malformed backup") }
        val pending = File("${metadata.path}.new").apply { writeText("pending write") }
        assertRecoveryError { MidiRepository(context).ensureDemoMidi() }
        assertArrayEquals(originalBytes, metadata.readBytes())
        assertEquals("malformed backup", backup.readText())
        assertEquals("pending write", pending.readText())
    }

    @Test
    fun validAtomicBackupRecoversWhenBaseIsMissingOrCorrupt() {
        val expected = seedLibrary()
        val originalBytes = metadata.readBytes()
        val backup = File("${metadata.path}.bak")
        for (baseMissing in listOf(true, false)) {
            if (baseMissing) assertTrue(metadata.delete()) else metadata.writeText("malformed base")
            backup.writeBytes(originalBytes)
            assertEquals(expected, MidiRepository(context).load())
            assertArrayEquals(originalBytes, metadata.readBytes())
            assertFalse(backup.exists())
        }
    }

    @Test
    fun legacyNameMigrationPreservesTitlesAndPlaylistRelationships() {
        val expected = seedLibrary()
        val root = JSONObject(metadata.readText()).apply { remove("nameNormalizationVersion") }
        metadata.writeText(root.toString())
        assertEquals(expected, MidiRepository(context).load())
        assertEquals(1, JSONObject(metadata.readText()).getInt("nameNormalizationVersion"))
    }

    private fun seedLibrary(): LibrarySnapshot {
        val repository = MidiRepository(context)
        val song = repository.importMidiBytes(midiBytes, "Test song.mid").value
        repository.renameFile(song.id, "My custom title")
        return repository.createPlaylist("My playlist", listOf(song.id)).snapshot
    }

    private fun assertRecoveryError(action: () -> Any) {
        val error = assertThrows(MidiRepository.LibraryRecoveryException::class.java) { action() }
        assertNotNull(error.cause)
        assertTrue(error.message!!.contains("preserved"))
    }
}
