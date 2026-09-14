package com.alexanderpeppe.pianobeam.data

import android.util.AtomicFile
import com.alexanderpeppe.pianobeam.midi.MidiFileWriter
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

internal data class PendingRecording(
    val title: String,
    val events: List<MidiFileWriter.RecordedEvent>,
    val id: String = UUID.randomUUID().toString()
) {
    val durationUs: Long get() = events.maxOfOrNull { it.timeUs } ?: 0L
    fun midiBytes(): ByteArray = MidiFileWriter.write(title, events)
}

/** Private, atomic checkpoints, independent of library metadata and MIDI imports. */
internal class RecordingRecoveryStore(private val baseFile: File) {
    private val file = AtomicFile(baseFile)
    private val backupFile = File("${baseFile.path}.bak")

    fun read(): PendingRecording? {
        if (!baseFile.exists() && !backupFile.exists()) return null
        return DataInputStream(file.openRead().buffered()).use { input ->
            require(input.readInt() == 1) { "Unsupported recording recovery version" }
            val id = UUID.fromString(input.readUTF()).toString()
            val title = input.readUTF()
            val count = input.readInt()
            require(count >= 0 && count <= input.available() / 13) { "Invalid recording recovery event count" }
            val events = List(count) {
                val timeUs = input.readLong()
                val size = input.readInt()
                require(timeUs >= 0 && size > 0 && size <= input.available()) { "Invalid recording recovery event" }
                MidiFileWriter.RecordedEvent(timeUs, ByteArray(size).also { input.readFully(it) })
            }
            require(input.read() == -1) { "Unexpected recording recovery data" }
            PendingRecording(title, events, id)
        }
    }

    fun write(recording: PendingRecording) {
        val stream = file.startWrite()
        try {
            val output = DataOutputStream(stream.buffered())
            output.writeInt(1)
            output.writeUTF(recording.id)
            output.writeUTF(recording.title)
            output.writeInt(recording.events.size)
            recording.events.forEach { event ->
                output.writeLong(event.timeUs)
                output.writeInt(event.data.size)
                output.write(event.data)
            }
            output.flush()
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    fun delete() {
        file.delete()
        if (baseFile.exists() || backupFile.exists() || File("${baseFile.path}.new").exists()) {
            throw IOException("Could not remove recording recovery file")
        }
    }
}
