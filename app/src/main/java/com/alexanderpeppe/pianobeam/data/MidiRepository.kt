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
import java.util.zip.ZipInputStream

class MidiRepository(private val context: Context) {
    companion object {
        private const val DEMO_BEETHOVEN_FUR_ELISE_ID = "demo-mutopia-beethoven-fur-elise"
        private const val DEMO_BEETHOVEN_FUR_ELISE_FILE = "demo_beethoven_fur_elise.mid"
        private const val DEMO_BEETHOVEN_FUR_ELISE_TITLE = "Beethoven - Für Elise, WoO 59"
        private const val DEMO_BEETHOVEN_FUR_ELISE_ORIGINAL_NAME =
            "Mutopia - Beethoven - Fur Elise, WoO 59.mid"
        private const val DEMO_BACH_WTC1_PRELUDE1_ID = "demo-mutopia-bach-wtc1-prelude1"
        private const val DEMO_BACH_WTC1_PRELUDE1_FILE = "demo_bach_wtc1_prelude1.mid"
        private const val DEMO_BACH_WTC1_PRELUDE1_TITLE = "Bach - WTC I Prelude I, BWV 846"
        private const val DEMO_BACH_WTC1_PRELUDE1_ORIGINAL_NAME =
            "Mutopia - Bach - WTC I Prelude I, BWV 846.mid"
        private const val DEMO_PLAYLIST_ID = "demo-playlist-mutopia-public-domain"
        private val LEGACY_DEMO_IDS = setOf(
            "demo-chopin-andante-polonaise",
            "demo-chopin-etude-op10-no5"
        )
        private val LEGACY_DEMO_FILES = listOf(
            "demo_chopin_andante_polonaise.mid",
            "demo_chopin_etude_op10_no5.mid"
        )
    }

    class ZipWithoutMidiException(displayName: String) : IllegalArgumentException(
        "$displayName does not contain MIDI files."
    )

    data class ImportResult(
        val importedItems: List<MidiLibraryItem>,
        val createdPlaylist: MidiPlaylist? = null
    )

    data class BatchImportProgress(
        val processedItems: Int,
        val totalItems: Int,
        val importedFiles: Int,
        val failedItems: Int,
        val currentName: String
    )

    data class BatchImportResult(
        val importedItems: List<MidiLibraryItem>,
        val createdPlaylists: List<MidiPlaylist>,
        val failedItems: Int,
        val zipWithoutMidiItems: Int
    )

    private data class ZipMidiEntry(
        val displayName: String,
        val bytes: ByteArray
    )

    private enum class MidiFileExtension(
        val defaultSuffix: String,
        val fallbackBase: String,
        val regex: Regex
    ) {
        Midi(
            defaultSuffix = ".mid",
            fallbackBase = "Imported MIDI",
            regex = Regex("\\.(mid|midi)$", RegexOption.IGNORE_CASE)
        ),
        Zip(
            defaultSuffix = ".zip",
            fallbackBase = "Imported ZIP",
            regex = Regex("\\.zip$", RegexOption.IGNORE_CASE)
        )
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
            val snapshot = LibrarySnapshot(
                files = files,
                playlists = playlists,
                bundledDemosEnabled = root.optBoolean("bundledDemosEnabled", true)
            )
            val normalized = snapshot.normalizeImportedNames()
            if (normalized != snapshot) save(normalized)
            normalized
        } catch (t: Throwable) {
            LibrarySnapshot()
        }
    }

    @Synchronized
    fun ensureDemoMidi() {
        val snapshot = load()
        val demoIds = listOf(DEMO_BEETHOVEN_FUR_ELISE_ID, DEMO_BACH_WTC1_PRELUDE1_ID)
        val replacedDemoIds = demoIds + LEGACY_DEMO_IDS
        if (!snapshot.bundledDemosEnabled) {
            (LEGACY_DEMO_FILES + DEMO_BEETHOVEN_FUR_ELISE_FILE + DEMO_BACH_WTC1_PRELUDE1_FILE)
                .forEach { fileName -> runCatching { File(midiDir, fileName).delete() } }
            val files = snapshot.files.filterNot { it.id in replacedDemoIds }
            val playlists = snapshot.playlists
                .filterNot { it.id == DEMO_PLAYLIST_ID || it.itemIds.any { itemId -> itemId in replacedDemoIds } }
            if (files != snapshot.files || playlists != snapshot.playlists) {
                save(snapshot.copy(files = files, playlists = playlists))
            }
            return
        }
        LEGACY_DEMO_FILES.forEach { fileName ->
            runCatching { File(midiDir, fileName).delete() }
        }
        val furElise = ensureDemoItem(
            existingFiles = snapshot.files,
            id = DEMO_BEETHOVEN_FUR_ELISE_ID,
            title = DEMO_BEETHOVEN_FUR_ELISE_TITLE,
            storedFileName = DEMO_BEETHOVEN_FUR_ELISE_FILE,
            originalName = DEMO_BEETHOVEN_FUR_ELISE_ORIGINAL_NAME,
            rawResourceId = R.raw.demo_beethoven_fur_elise,
            importedAtMs = 0L
        )
        val bachPrelude = ensureDemoItem(
            existingFiles = snapshot.files,
            id = DEMO_BACH_WTC1_PRELUDE1_ID,
            title = DEMO_BACH_WTC1_PRELUDE1_TITLE,
            storedFileName = DEMO_BACH_WTC1_PRELUDE1_FILE,
            originalName = DEMO_BACH_WTC1_PRELUDE1_ORIGINAL_NAME,
            rawResourceId = R.raw.demo_bach_wtc1_prelude1,
            importedAtMs = 1L
        )
        val files = snapshot.files.filterNot { it.id in replacedDemoIds } + listOf(furElise, bachPrelude)
        val samplePlaylist = MidiPlaylist(
            id = DEMO_PLAYLIST_ID,
            name = "Sample Playlist: Mutopia Public Domain Demos",
            itemIds = demoIds,
            createdAtMs = 0L,
            colorHex = snapshot.playlists.firstOrNull { it.id == DEMO_PLAYLIST_ID }?.colorHex
        )
        val playlists = snapshot.playlists
            .filterNot { it.id == DEMO_PLAYLIST_ID || it.itemIds.any { itemId -> itemId in LEGACY_DEMO_IDS } }
            .plus(samplePlaylist)
        save(snapshot.copy(files = files, playlists = playlists))
    }

    private fun ensureDemoItem(
        existingFiles: List<MidiLibraryItem>,
        id: String,
        title: String,
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
        val existingTitle = existing?.title?.trim().orEmpty()
        return MidiLibraryItem(
            id = id,
            title = existingTitle.takeUnless { it.isBlank() || it.isPlaceholderMidiTitle() } ?: title,
            originalName = originalName,
            storedFileName = storedFileName,
            durationUs = parseResult.getOrNull()?.durationUs ?: existing?.durationUs ?: 0L,
            importedAtMs = importedAtMs,
            notes = "Bundled Mutopia Project demo - Public Domain / no rights reserved"
        )
    }

    @Synchronized
    fun importMidi(uri: Uri): MidiLibraryItem {
        val displayName = queryDisplayName(uri) ?: "Imported MIDI ${System.currentTimeMillis()}.mid"
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open $displayName" }
            return importMidiBytes(input.readBytes(), displayName)
        }
    }

    @Synchronized
    fun importMidiOrZip(uri: Uri): ImportResult {
        val displayName = queryDisplayName(uri) ?: "Imported MIDI ${System.currentTimeMillis()}.mid"
        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        val bytes = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open $displayName" }
            input.readBytes()
        }
        return if (displayName.isZipFileName() || mimeType.isZipMimeType() || bytes.hasZipHeader()) {
            importZipBytes(bytes, displayName)
        } else {
            ImportResult(importedItems = listOf(importMidiBytes(bytes, displayName)))
        }
    }

    @Synchronized
    fun importMidiOrZipBatch(
        uris: List<Uri>,
        onProgress: (BatchImportProgress) -> Unit = {}
    ): BatchImportResult {
        val cleanUris = uris.filterNot { it == Uri.EMPTY }
        if (cleanUris.isEmpty()) {
            return BatchImportResult(
                importedItems = emptyList(),
                createdPlaylists = emptyList(),
                failedItems = 0,
                zipWithoutMidiItems = 0
            )
        }
        val snapshot = load()
        val importedItems = mutableListOf<MidiLibraryItem>()
        val createdPlaylists = mutableListOf<MidiPlaylist>()
        var failedItems = 0
        var zipWithoutMidiItems = 0
        var nextImportedAtMs = System.currentTimeMillis()

        cleanUris.forEachIndexed { index, uri ->
            val displayName = queryDisplayName(uri) ?: "Imported MIDI ${System.currentTimeMillis()}.mid"
            runCatching {
                importMidiOrZipWithoutSaving(uri, displayName, nextImportedAtMs)
            }.onSuccess { result ->
                importedItems += result.importedItems
                result.createdPlaylist?.let { createdPlaylists += it }
                nextImportedAtMs += result.importedItems.size.coerceAtLeast(1)
            }.onFailure { t ->
                failedItems++
                if (t is ZipWithoutMidiException) zipWithoutMidiItems++
            }
            onProgress(
                BatchImportProgress(
                    processedItems = index + 1,
                    totalItems = cleanUris.size,
                    importedFiles = importedItems.size,
                    failedItems = failedItems,
                    currentName = displayName
                )
            )
        }

        if (importedItems.isNotEmpty() || createdPlaylists.isNotEmpty()) {
            save(
                snapshot.copy(
                    files = snapshot.files + importedItems,
                    playlists = snapshot.playlists + createdPlaylists
                )
            )
        }

        return BatchImportResult(
            importedItems = importedItems,
            createdPlaylists = createdPlaylists,
            failedItems = failedItems,
            zipWithoutMidiItems = zipWithoutMidiItems
        )
    }

    @Synchronized
    fun importZipBytes(bytes: ByteArray, displayName: String): ImportResult {
        val result = importZipBytesWithoutSaving(bytes, displayName, System.currentTimeMillis())
        val snapshot = load()
        save(snapshot.copy(files = snapshot.files + result.importedItems, playlists = snapshot.playlists + listOfNotNull(result.createdPlaylist)))
        return result
    }

    @Synchronized
    fun importMidiBytes(
        bytes: ByteArray,
        displayName: String,
        notePrefix: String = "",
        preferredTitle: String? = null
    ): MidiLibraryItem {
        val item = createMidiItem(
            bytes = bytes,
            displayName = displayName,
            notePrefix = notePrefix,
            preferredTitle = preferredTitle,
            importedAtMs = System.currentTimeMillis(),
            preferDisplayNameTitle = false,
            replaceTitleSeparators = false
        )
        val snapshot = load()
        save(snapshot.copy(files = snapshot.files + item))
        return item
    }

    private fun importMidiOrZipWithoutSaving(uri: Uri, displayName: String, importedAtMs: Long): ImportResult {
        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        val bytes = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open $displayName" }
            input.readBytes()
        }
        return if (displayName.isZipFileName() || mimeType.isZipMimeType() || bytes.hasZipHeader()) {
            importZipBytesWithoutSaving(bytes, displayName, importedAtMs)
        } else {
            ImportResult(
                importedItems = listOf(
                    createMidiItem(
                        bytes = bytes,
                        displayName = displayName,
                        notePrefix = "",
                        preferredTitle = null,
                        importedAtMs = importedAtMs,
                        preferDisplayNameTitle = false,
                        replaceTitleSeparators = false
                    )
                )
            )
        }
    }

    private fun importZipBytesWithoutSaving(
        bytes: ByteArray,
        displayName: String,
        importedAtMs: Long
    ): ImportResult {
        val cleanDisplayName = displayName
            .ifBlank { "Imported ZIP ${System.currentTimeMillis()}.zip" }
            .normalizeImportedFileName(
                extension = MidiFileExtension.Zip,
                replaceHyphenSymbols = true
            )
        val entries = readMidiEntriesFromZip(bytes)
        if (entries.isEmpty()) throw ZipWithoutMidiException(cleanDisplayName)

        val importedItems = entries.mapIndexed { index, entry ->
            createMidiItem(
                bytes = entry.bytes,
                displayName = entry.displayName,
                notePrefix = "Imported from $cleanDisplayName",
                preferredTitle = null,
                importedAtMs = importedAtMs + index,
                preferDisplayNameTitle = true,
                replaceTitleSeparators = true
            )
        }
        val playlist = MidiPlaylist(
            id = UUID.randomUUID().toString(),
            name = cleanDisplayName.toZipPlaylistTitle(),
            itemIds = importedItems.map { it.id },
            createdAtMs = System.currentTimeMillis()
        )
        return ImportResult(importedItems = importedItems, createdPlaylist = playlist)
    }

    private fun createMidiItem(
        bytes: ByteArray,
        displayName: String,
        notePrefix: String,
        preferredTitle: String?,
        importedAtMs: Long,
        preferDisplayNameTitle: Boolean,
        replaceTitleSeparators: Boolean
    ): MidiLibraryItem {
        val cleanDisplayName = displayName
            .ifBlank { "Imported MIDI ${System.currentTimeMillis()}.mid" }
            .normalizeImportedFileName(
                extension = MidiFileExtension.Midi,
                replaceHyphenSymbols = replaceTitleSeparators
            )
        val id = UUID.randomUUID().toString()
        val storedName = "$id.mid"
        val outFile = File(midiDir, storedName)
        outFile.writeBytes(bytes)
        val parseResult = runCatching { MidiFileParser.parse(bytes, cleanDisplayName) }
        val displayNameTitle = cleanDisplayName
            .removeMidiExtension()
            .normalizeImportedTitleText(replaceHyphenSymbols = replaceTitleSeparators)
        val parsedTitle = parseResult.getOrNull()
            ?.title
            ?.normalizeImportedTitleText(replaceHyphenSymbols = false)
            ?.takeIf { it.isNotBlank() }
        val title = preferredTitle
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.removeMidiExtension()
            ?.normalizeImportedTitleText(replaceHyphenSymbols = replaceTitleSeparators)
            ?: if (preferDisplayNameTitle) displayNameTitle else parsedTitle ?: displayNameTitle
        val durationUs = parseResult.getOrNull()?.durationUs ?: 0L
        val parseNote = parseResult.exceptionOrNull()?.message?.let { "Imported, but parse check reported: $it" }
        val notes = listOf(notePrefix.trim(), parseNote)
            .filterNot { it.isNullOrBlank() }
            .joinToString("; ")

        return MidiLibraryItem(
            id = id,
            title = title,
            originalName = cleanDisplayName,
            storedFileName = storedName,
            durationUs = durationUs,
            importedAtMs = importedAtMs,
            notes = notes
        )
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
    fun deleteFiles(itemIds: List<String>) {
        val idsToDelete = itemIds.filter { it.isNotBlank() }.toSet()
        if (idsToDelete.isEmpty()) return
        val snapshot = load()
        snapshot.files
            .filter { it.id in idsToDelete }
            .forEach { File(midiDir, it.storedFileName).delete() }
        val playlists = snapshot.playlists.map { playlist ->
            playlist.copy(itemIds = playlist.itemIds.filterNot { it in idsToDelete })
        }
        save(snapshot.copy(files = snapshot.files.filterNot { it.id in idsToDelete }, playlists = playlists))
    }

    @Synchronized
    fun purgeLibrary() {
        midiDir.listFiles()?.forEach { file -> runCatching { file.deleteRecursively() } }
        save(LibrarySnapshot(bundledDemosEnabled = false))
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
    fun createPlaylist(name: String, itemIds: List<String>): MidiPlaylist {
        val cleanName = name.trim().ifBlank { "Untitled Playlist" }
        val snapshot = load()
        val validIds = snapshot.files.map { it.id }.toSet()
        val playlist = MidiPlaylist(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            itemIds = itemIds.filter { it in validIds },
            createdAtMs = System.currentTimeMillis()
        )
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
    fun setPlaylistColor(playlistId: String, colorHex: String?) {
        val cleanColor = colorHex?.trim()?.takeIf { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }
        val snapshot = load()
        save(snapshot.copy(playlists = snapshot.playlists.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(colorHex = cleanColor) else playlist
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
        root.put("bundledDemosEnabled", snapshot.bundledDemosEnabled)
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
                    playlist.colorHex?.takeIf { it.isNotBlank() }?.let { put("colorHex", it) }
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
        save(
            LibrarySnapshot(
                files = restoredFiles,
                playlists = playlists,
                bundledDemosEnabled = root.optBoolean("bundledDemosEnabled", true)
            )
        )
    }

    @Synchronized
    private fun save(snapshot: LibrarySnapshot) {
        val root = JSONObject()
        root.put("bundledDemosEnabled", snapshot.bundledDemosEnabled)
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
                    playlist.colorHex?.takeIf { it.isNotBlank() }?.let { put("colorHex", it) }
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
                    createdAtMs = obj.optLong("createdAtMs", 0L),
                    colorHex = obj.optString("colorHex").takeIf { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }
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

    private fun readMidiEntriesFromZip(bytes: ByteArray): List<ZipMidiEntry> = buildList {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                try {
                    if (!entry.isDirectory && entry.name.isMidiFileName()) {
                        val displayName = entry.name.zipEntryDisplayName()
                        add(ZipMidiEntry(displayName, zip.readBytes()))
                    }
                } finally {
                    zip.closeEntry()
                }
            }
        }
    }

    private fun String.zipEntryDisplayName(): String {
        val name = substringAfterLast('/').substringAfterLast('\\').trim()
        return name
            .ifBlank { "Imported MIDI ${System.currentTimeMillis()}.mid" }
            .normalizeImportedFileName(
                extension = MidiFileExtension.Midi,
                replaceHyphenSymbols = true
            )
    }

    private fun String.removeMidiExtension(): String =
        replace(Regex("\\.(mid|midi)$", RegexOption.IGNORE_CASE), "")

    private fun String.isPlaceholderMidiTitle(): Boolean =
        trim().equals("control track", ignoreCase = true)

    private fun String.removeZipExtension(): String =
        replace(Regex("\\.zip$", RegexOption.IGNORE_CASE), "")

    private fun String.ensureMidiExtension(): String =
        if (endsWith(".mid", ignoreCase = true) || endsWith(".midi", ignoreCase = true)) this else "$this.mid"

    private fun String.isMidiFileName(): Boolean =
        endsWith(".mid", ignoreCase = true) || endsWith(".midi", ignoreCase = true)

    private fun String.isZipFileName(): Boolean =
        endsWith(".zip", ignoreCase = true)

    private fun String.isZipMimeType(): Boolean =
        equals("application/zip", ignoreCase = true) ||
            equals("application/x-zip", ignoreCase = true) ||
            equals("application/x-zip-compressed", ignoreCase = true)

    private fun ByteArray.hasZipHeader(): Boolean =
        size >= 4 &&
            this[0] == 0x50.toByte() &&
            this[1] == 0x4B.toByte() &&
            (
                (this[2] == 0x03.toByte() && this[3] == 0x04.toByte()) ||
                    (this[2] == 0x05.toByte() && this[3] == 0x06.toByte()) ||
                    (this[2] == 0x07.toByte() && this[3] == 0x08.toByte())
            )

    private fun String.toZipPlaylistTitle(): String {
        val name = normalizeImportedFileName(
            extension = MidiFileExtension.Zip,
            replaceHyphenSymbols = true
        )
            .removeZipExtension()
            .normalizeImportedTitleText(replaceHyphenSymbols = true)
        return name.ifBlank { "Imported Playlist" }
    }

    private fun LibrarySnapshot.normalizeImportedNames(): LibrarySnapshot {
        val zipImportedIds = files
            .filter { it.isZipImported() }
            .map { it.id }
            .toSet()
        if (zipImportedIds.isEmpty()) {
            val normalizedFiles = files.map { item ->
                item.copy(
                    title = item.title.normalizeImportedTitleText(replaceHyphenSymbols = false),
                    originalName = item.originalName.repairImportedText(),
                    notes = item.notes.repairImportedText()
                )
            }
            val normalizedPlaylists = playlists.map { playlist ->
                playlist.copy(name = playlist.name.normalizeImportedTitleText(replaceHyphenSymbols = false))
            }
            return copy(files = normalizedFiles, playlists = normalizedPlaylists)
        }

        val normalizedFiles = files.map { item ->
            val isZipImported = item.id in zipImportedIds
            val originalName = if (isZipImported) {
                item.originalName.normalizeImportedFileName(
                    extension = MidiFileExtension.Midi,
                    replaceHyphenSymbols = true
                )
            } else {
                item.originalName.repairImportedText()
            }
            val title = if (isZipImported) {
                originalName
                    .removeMidiExtension()
                    .normalizeImportedTitleText(replaceHyphenSymbols = true)
            } else {
                item.title
                    .normalizeImportedTitleText(replaceHyphenSymbols = false)
                    .ifBlank {
                        originalName
                            .removeMidiExtension()
                            .normalizeImportedTitleText(replaceHyphenSymbols = false)
                    }
            }
            item.copy(
                title = title,
                originalName = originalName,
                notes = if (isZipImported) item.notes.normalizeImportedNotes() else item.notes.repairImportedText()
            )
        }
        val normalizedPlaylists = playlists.map { playlist ->
            val isZipPlaylist = playlist.itemIds.any { it in zipImportedIds }
            playlist.copy(name = playlist.name.normalizeImportedTitleText(replaceHyphenSymbols = isZipPlaylist))
        }
        return copy(files = normalizedFiles, playlists = normalizedPlaylists)
    }

    private fun MidiLibraryItem.isZipImported(): Boolean =
        notes.trimStart().startsWith("Imported from ")

    private fun String.normalizeImportedNotes(): String {
        val repaired = repairImportedText()
        val prefix = "Imported from "
        if (!repaired.trimStart().startsWith(prefix)) return repaired
        val leadingWhitespace = repaired.takeWhile { it.isWhitespace() }
        val withoutLeading = repaired.trimStart()
        val parts = withoutLeading.split(";", limit = 2)
        val archiveName = parts[0]
            .removePrefix(prefix)
            .normalizeImportedFileName(
                extension = MidiFileExtension.Zip,
                replaceHyphenSymbols = true
            )
        val firstPart = "$prefix$archiveName"
        return leadingWhitespace + if (parts.size == 1) firstPart else "$firstPart;${parts[1]}"
    }

    private fun String.normalizeImportedFileName(
        extension: MidiFileExtension,
        replaceHyphenSymbols: Boolean
    ): String {
        val repaired = substringAfterLast('/')
            .substringAfterLast('\\')
            .repairImportedText()
            .trim()
        val suffix = extension.regex.find(repaired)?.value ?: extension.defaultSuffix
        val base = extension.regex
            .replace(repaired, "")
            .normalizeImportedTitleText(replaceHyphenSymbols = replaceHyphenSymbols)
            .ifBlank { "${extension.fallbackBase} ${System.currentTimeMillis()}" }
        return "$base$suffix"
    }

    private fun String.normalizeImportedTitleText(replaceHyphenSymbols: Boolean): String {
        var text = repairImportedText()
            .replace('_', ' ')
        if (replaceHyphenSymbols) {
            text = text.replace(Regex("\\s*[-\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015]+\\s*"), " ")
        }
        return text
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun String.repairImportedText(): String =
        replace("\u0081e", "'")
            .replace("\u0081f", "'")
            .replace("\u0081g", "\"")
            .replace("\u0081h", "\"")
            .replace("\u00E2\u0080\u0098", "'")
            .replace("\u00E2\u0080\u0099", "'")
            .replace("\u00E2\u0080\u009C", "\"")
            .replace("\u00E2\u0080\u009D", "\"")
            .replace("\u00E2\u0080\u0093", "-")
            .replace("\u00E2\u0080\u0094", "-")
            .replace('\u2018', '\'')
            .replace('\u2019', '\'')
            .replace('\u201A', '\'')
            .replace('\u201B', '\'')
            .replace('\u201C', '"')
            .replace('\u201D', '"')
            .replace('\u201E', '"')
            .replace('\u0091', '\'')
            .replace('\u0092', '\'')
            .replace('\u0093', '"')
            .replace('\u0094', '"')
            .replace(Regex("[\\u0000-\\u001F\\u007F-\\u009F]"), " ")
}
