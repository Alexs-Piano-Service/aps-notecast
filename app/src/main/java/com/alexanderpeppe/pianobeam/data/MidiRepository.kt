package com.alexanderpeppe.pianobeam.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.alexanderpeppe.pianobeam.R
import com.alexanderpeppe.pianobeam.midi.MidiFileParser
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MidiRepository(private val context: Context) {
    companion object {
        private const val DEMO_CHOPIN_ANDANTE_ID = "demo-chopin-andante-polonaise"
        private const val DEMO_CHOPIN_ANDANTE_FILE = "demo_chopin_andante_polonaise.mid"
        private const val DEMO_CHOPIN_ANDANTE_ORIGINAL_NAME =
            "Frederic Chopin, Andante Spianato and Grande Polonaise Brillante, Op. 22 (Zuber-06).mid"
        private const val DEMO_CHOPIN_ETUDE_ID = "demo-chopin-etude-op10-no5"
        private const val DEMO_CHOPIN_ETUDE_FILE = "demo_chopin_etude_op10_no5.mid"
        private const val DEMO_CHOPIN_ETUDE_ORIGINAL_NAME =
            "Frederic Chopin - Etude Op. 10 No. 5 (KimG-04).mid"
        private const val DEMO_PLAYLIST_ID = "demo-playlist-two-chopin-pieces"
    }

    private val midiDir: File = File(context.filesDir, "midi").apply { mkdirs() }
    private val metadataFile: File = File(context.filesDir, "library.json")

    @Synchronized
    fun load(): LibrarySnapshot {
        if (!metadataFile.exists()) return LibrarySnapshot()
        return try {
            val root = JSONObject(metadataFile.readText())
            val files = root.optJSONArray("files")?.toMidiFiles() ?: emptyList()
            val playlists = root.optJSONArray("playlists")?.toPlaylists() ?: emptyList()
            LibrarySnapshot(files, playlists)
        } catch (t: Throwable) {
            LibrarySnapshot()
        }
    }

    @Synchronized
    fun ensureDemoMidi() {
        val snapshot = load()
        val andante = ensureDemoItem(
            existingFiles = snapshot.files,
            id = DEMO_CHOPIN_ANDANTE_ID,
            storedFileName = DEMO_CHOPIN_ANDANTE_FILE,
            originalName = DEMO_CHOPIN_ANDANTE_ORIGINAL_NAME,
            rawResourceId = R.raw.demo_chopin_andante_polonaise,
            importedAtMs = 0L
        )
        val etude = ensureDemoItem(
            existingFiles = snapshot.files,
            id = DEMO_CHOPIN_ETUDE_ID,
            storedFileName = DEMO_CHOPIN_ETUDE_FILE,
            originalName = DEMO_CHOPIN_ETUDE_ORIGINAL_NAME,
            rawResourceId = R.raw.demo_chopin_etude_op10_no5,
            importedAtMs = 1L
        )
        val demoIds = listOf(DEMO_CHOPIN_ANDANTE_ID, DEMO_CHOPIN_ETUDE_ID)
        val files = snapshot.files.filterNot { it.id in demoIds } + listOf(andante, etude)
        val samplePlaylist = MidiPlaylist(
            id = DEMO_PLAYLIST_ID,
            name = "Sample Playlist: Two Chopin Pieces",
            itemIds = demoIds,
            createdAtMs = 0L
        )
        val playlists = snapshot.playlists.filterNot { it.id == DEMO_PLAYLIST_ID } + samplePlaylist
        save(snapshot.copy(files = files, playlists = playlists))
    }

    private fun ensureDemoItem(
        existingFiles: List<MidiLibraryItem>,
        id: String,
        storedFileName: String,
        originalName: String,
        rawResourceId: Int,
        importedAtMs: Long
    ): MidiLibraryItem {
        val outFile = File(midiDir, storedFileName)
        if (!outFile.exists()) {
            context.resources.openRawResource(rawResourceId).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val bytes = outFile.readBytes()
        val parseResult = runCatching { MidiFileParser.parse(bytes, originalName) }
        val existing = existingFiles.firstOrNull { it.id == id }
        return MidiLibraryItem(
            id = id,
            title = existing?.title?.takeIf { it.isNotBlank() }
                ?: parseResult.getOrNull()?.title?.takeIf { it.isNotBlank() }
                ?: originalName.removeMidiExtension(),
            originalName = originalName,
            storedFileName = storedFileName,
            durationUs = parseResult.getOrNull()?.durationUs ?: existing?.durationUs ?: 0L,
            importedAtMs = importedAtMs,
            notes = "Bundled demo"
        )
    }

    @Synchronized
    fun importMidi(uri: Uri): MidiLibraryItem {
        val displayName = queryDisplayName(uri) ?: "Imported MIDI ${System.currentTimeMillis()}.mid"
        val id = UUID.randomUUID().toString()
        val storedName = "$id.mid"
        val outFile = File(midiDir, storedName)

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open $displayName" }
            outFile.outputStream().use { output -> input.copyTo(output) }
        }

        val parseResult = runCatching { MidiFileParser.parse(outFile.readBytes(), displayName) }
        val title = parseResult.getOrNull()?.title?.takeIf { it.isNotBlank() } ?: displayName.removeMidiExtension()
        val durationUs = parseResult.getOrNull()?.durationUs ?: 0L
        val notes = parseResult.exceptionOrNull()?.message?.let { "Imported, but parse check reported: $it" } ?: ""

        val item = MidiLibraryItem(
            id = id,
            title = title,
            originalName = displayName,
            storedFileName = storedName,
            durationUs = durationUs,
            importedAtMs = System.currentTimeMillis(),
            notes = notes
        )
        val snapshot = load()
        save(snapshot.copy(files = snapshot.files + item))
        return item
    }

    @Synchronized
    fun saveRecordedMidi(title: String, bytes: ByteArray): MidiLibraryItem {
        val cleanTitle = title.trim().ifBlank { "APS NoteCast Recording" }
        val id = UUID.randomUUID().toString()
        val storedName = "$id.mid"
        val displayName = "${cleanTitle.ensureMidiExtension()}"
        val outFile = File(midiDir, storedName)
        outFile.writeBytes(bytes)

        val parseResult = runCatching { MidiFileParser.parse(bytes, displayName) }
        val item = MidiLibraryItem(
            id = id,
            title = parseResult.getOrNull()?.title?.takeIf { it.isNotBlank() } ?: cleanTitle.removeMidiExtension(),
            originalName = displayName,
            storedFileName = storedName,
            durationUs = parseResult.getOrNull()?.durationUs ?: 0L,
            importedAtMs = System.currentTimeMillis(),
            notes = parseResult.exceptionOrNull()?.message?.let { "Recorded, but parse check reported: $it" } ?: ""
        )
        val snapshot = load()
        save(snapshot.copy(files = snapshot.files + item))
        return item
    }

    @Synchronized
    fun deleteFile(itemId: String) {
        val snapshot = load()
        val item = snapshot.files.firstOrNull { it.id == itemId }
        if (item != null) File(midiDir, item.storedFileName).delete()
        val playlists = snapshot.playlists.map { playlist ->
            playlist.copy(itemIds = playlist.itemIds.filterNot { it == itemId })
        }
        save(snapshot.copy(files = snapshot.files.filterNot { it.id == itemId }, playlists = playlists))
    }

    @Synchronized
    fun renameFile(itemId: String, title: String) {
        val cleanTitle = title.trim().ifBlank { "Untitled MIDI" }
        val snapshot = load()
        save(snapshot.copy(files = snapshot.files.map { item ->
            if (item.id == itemId) item.copy(title = cleanTitle) else item
        }))
    }

    @Synchronized
    fun createPlaylist(name: String): MidiPlaylist {
        val cleanName = name.trim().ifBlank { "Untitled Playlist" }
        val playlist = MidiPlaylist(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            itemIds = emptyList(),
            createdAtMs = System.currentTimeMillis()
        )
        val snapshot = load()
        save(snapshot.copy(playlists = snapshot.playlists + playlist))
        return playlist
    }

    @Synchronized
    fun deletePlaylist(playlistId: String) {
        val snapshot = load()
        save(snapshot.copy(playlists = snapshot.playlists.filterNot { it.id == playlistId }))
    }

    @Synchronized
    fun renamePlaylist(playlistId: String, name: String) {
        val cleanName = name.trim().ifBlank { "Untitled Playlist" }
        val snapshot = load()
        save(snapshot.copy(playlists = snapshot.playlists.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(name = cleanName) else playlist
        }))
    }

    @Synchronized
    fun duplicatePlaylist(playlistId: String): MidiPlaylist? {
        val snapshot = load()
        val source = snapshot.playlists.firstOrNull { it.id == playlistId } ?: return null
        val clone = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} copy",
            createdAtMs = System.currentTimeMillis()
        )
        save(snapshot.copy(playlists = snapshot.playlists + clone))
        return clone
    }

    @Synchronized
    fun addToPlaylist(playlistId: String, itemId: String) {
        val snapshot = load()
        val playlists = snapshot.playlists.map { playlist ->
            if (playlist.id != playlistId) playlist else playlist.copy(itemIds = playlist.itemIds + itemId)
        }
        save(snapshot.copy(playlists = playlists))
    }

    @Synchronized
    fun addToPlaylist(playlistId: String, itemIds: List<String>) {
        val cleanIds = itemIds.filter { id -> id.isNotBlank() }
        if (cleanIds.isEmpty()) return
        val snapshot = load()
        val validIds = snapshot.files.map { it.id }.toSet()
        val idsToAdd = cleanIds.filter { it in validIds }
        val playlists = snapshot.playlists.map { playlist ->
            if (playlist.id != playlistId) playlist else playlist.copy(itemIds = playlist.itemIds + idsToAdd)
        }
        save(snapshot.copy(playlists = playlists))
    }

    @Synchronized
    fun removeFromPlaylist(playlistId: String, index: Int) {
        val snapshot = load()
        val playlists = snapshot.playlists.map { playlist ->
            if (playlist.id != playlistId || index !in playlist.itemIds.indices) playlist
            else playlist.copy(itemIds = playlist.itemIds.toMutableList().also { it.removeAt(index) })
        }
        save(snapshot.copy(playlists = playlists))
    }

    @Synchronized
    fun movePlaylistItem(playlistId: String, fromIndex: Int, direction: Int) {
        val snapshot = load()
        val playlists = snapshot.playlists.map { playlist ->
            val toIndex = fromIndex + direction
            if (playlist.id != playlistId || fromIndex !in playlist.itemIds.indices || toIndex !in playlist.itemIds.indices) {
                playlist
            } else {
                val updated = playlist.itemIds.toMutableList()
                val item = updated.removeAt(fromIndex)
                updated.add(toIndex, item)
                playlist.copy(itemIds = updated)
            }
        }
        save(snapshot.copy(playlists = playlists))
    }

    fun fileFor(item: MidiLibraryItem): File = File(midiDir, item.storedFileName)

    @Synchronized
    fun exportBackupJson(): String {
        val snapshot = load()
        val root = JSONObject()
        root.put("version", 1)
        root.put("files", JSONArray().also { array ->
            snapshot.files.forEach { item ->
                val file = fileFor(item)
                array.put(JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("originalName", item.originalName)
                    put("storedFileName", item.storedFileName)
                    put("durationUs", item.durationUs)
                    put("importedAtMs", item.importedAtMs)
                    put("notes", item.notes)
                    if (file.exists()) {
                        put("contentBase64", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
                    }
                })
            }
        })
        root.put("playlists", JSONArray().also { array ->
            snapshot.playlists.forEach { playlist ->
                array.put(JSONObject().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("createdAtMs", playlist.createdAtMs)
                    put("itemIds", JSONArray().also { ids ->
                        playlist.itemIds.forEach { ids.put(it) }
                    })
                })
            }
        })
        return root.toString(2)
    }

    @Synchronized
    fun restoreBackupJson(json: String) {
        val root = JSONObject(json)
        val fileArray = root.optJSONArray("files") ?: JSONArray()
        val restoredFiles = mutableListOf<MidiLibraryItem>()
        midiDir.listFiles()?.forEach { it.delete() }
        for (index in 0 until fileArray.length()) {
            val obj = fileArray.optJSONObject(index) ?: continue
            val storedName = File(obj.optString("storedFileName", "${UUID.randomUUID()}.mid")).name
            val encoded = obj.optString("contentBase64", "")
            if (encoded.isBlank()) continue
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            File(midiDir, storedName).writeBytes(bytes)
            restoredFiles += MidiLibraryItem(
                id = obj.optString("id", UUID.randomUUID().toString()),
                title = obj.optString("title", obj.optString("originalName", "Restored MIDI").removeMidiExtension()),
                originalName = obj.optString("originalName", storedName),
                storedFileName = storedName,
                durationUs = obj.optLong("durationUs", 0L),
                importedAtMs = obj.optLong("importedAtMs", System.currentTimeMillis()),
                notes = obj.optString("notes", "")
            )
        }
        val restoredIds = restoredFiles.map { it.id }.toSet()
        val playlists = root.optJSONArray("playlists")?.toPlaylists()
            ?.map { playlist -> playlist.copy(itemIds = playlist.itemIds.filter { it in restoredIds }) }
            ?: emptyList()
        save(LibrarySnapshot(files = restoredFiles, playlists = playlists))
    }

    @Synchronized
    private fun save(snapshot: LibrarySnapshot) {
        val root = JSONObject()
        root.put("files", JSONArray().also { array ->
            snapshot.files.forEach { item ->
                array.put(JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("originalName", item.originalName)
                    put("storedFileName", item.storedFileName)
                    put("durationUs", item.durationUs)
                    put("importedAtMs", item.importedAtMs)
                    put("notes", item.notes)
                })
            }
        })
        root.put("playlists", JSONArray().also { array ->
            snapshot.playlists.forEach { playlist ->
                array.put(JSONObject().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("createdAtMs", playlist.createdAtMs)
                    put("itemIds", JSONArray().also { ids -> playlist.itemIds.forEach { ids.put(it) } })
                })
            }
        })
        metadataFile.writeText(root.toString(2))
    }

    private fun JSONArray.toMidiFiles(): List<MidiLibraryItem> = buildList {
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            add(
                MidiLibraryItem(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    originalName = obj.optString("originalName"),
                    storedFileName = obj.optString("storedFileName"),
                    durationUs = obj.optLong("durationUs", 0L),
                    importedAtMs = obj.optLong("importedAtMs", 0L),
                    notes = obj.optString("notes", "")
                )
            )
        }
    }.filter { it.id.isNotBlank() && it.storedFileName.isNotBlank() }

    private fun JSONArray.toPlaylists(): List<MidiPlaylist> = buildList {
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            val idsArray = obj.optJSONArray("itemIds") ?: JSONArray()
            val ids = buildList {
                for (j in 0 until idsArray.length()) add(idsArray.optString(j))
            }.filter { it.isNotBlank() }
            add(
                MidiPlaylist(
                    id = obj.optString("id"),
                    name = obj.optString("name", "Playlist"),
                    itemIds = ids,
                    createdAtMs = obj.optLong("createdAtMs", 0L)
                )
            )
        }
    }.filter { it.id.isNotBlank() }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        }.getOrNull()
    }

    private fun String.removeMidiExtension(): String =
        replace(Regex("\\.(mid|midi)$", RegexOption.IGNORE_CASE), "")

    private fun String.ensureMidiExtension(): String =
        if (endsWith(".mid", ignoreCase = true) || endsWith(".midi", ignoreCase = true)) this else "$this.mid"
}
