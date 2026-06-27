package com.alexanderpeppe.pianobeam.reporting

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.alexanderpeppe.pianobeam.BuildConfig
import com.alexanderpeppe.pianobeam.data.AppSettings
import com.alexanderpeppe.pianobeam.data.AppUiState
import com.alexanderpeppe.pianobeam.data.LastPlaybackSnapshot
import com.alexanderpeppe.pianobeam.data.MidiChannelControl
import com.alexanderpeppe.pianobeam.data.MidiLibraryItem
import com.alexanderpeppe.pianobeam.data.PlaybackChannelInfo
import com.alexanderpeppe.pianobeam.data.instrumentOverridesForSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class BugReportInput(
    val summary: String,
    val description: String,
    val senderEmail: String,
    val includeLogs: Boolean
)

data class BugReportResult(
    val reportId: String,
    val statusCode: Int,
    val duplicate: Boolean
)

object BugReportClient {
    private const val APP_NAME = "APS NoteCast"
    private const val APP_WEBSITE = "https://www.alexanderpeppe.com"
    private const val MAX_SUMMARY_CHARS = 300
    private const val MAX_DESCRIPTION_CHARS = 12_000
    private const val MAX_SENDER_EMAIL_CHARS = 320
    private const val LOG_TAIL_CHARS = 64_000

    suspend fun submit(
        context: Context,
        input: BugReportInput,
        state: AppUiState,
        settings: AppSettings,
        diagnostics: String
    ): BugReportResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val reportId = UUID.randomUUID().toString().replace("-", "")
        val payload = buildPayload(appContext, input, state, settings, diagnostics, reportId)
        val body = payload.toString()
        val statusAndResponse = postJson(body)
        val response = statusAndResponse.second
        val duplicate = response.optBoolean("duplicate", false)
        CrashReportStore.clear(appContext)
        BugReportResult(reportId = reportId, statusCode = statusAndResponse.first, duplicate = duplicate)
    }

    private fun buildPayload(
        context: Context,
        input: BugReportInput,
        state: AppUiState,
        settings: AppSettings,
        diagnostics: String,
        reportId: String
    ): JSONObject {
        val pendingCrash = CrashReportStore.pendingCrash(context)
        val includeLogs = input.includeLogs || pendingCrash.isNotBlank()
        val pendingCrashBlock = if (pendingCrash.isNotBlank()) {
            buildString {
                appendLine("Pending crash from previous run")
                appendLine("-------------------------------")
                appendLine(pendingCrash)
                appendLine()
            }
        } else {
            ""
        }
        val eventLogText = if (includeLogs) AppEventLog.tailText(LOG_TAIL_CHARS) else ""
        val eventLogTotalChars = if (includeLogs) AppEventLog.totalTextChars() else 0
        val senderEmail = input.senderEmail.trim().take(MAX_SENDER_EMAIL_CHARS)
        val logText = buildString {
            append(pendingCrashBlock)
            append(eventLogText)
        }
        val totalAvailableLogChars = pendingCrashBlock.length + eventLogTotalChars
        return JSONObject().apply {
            put("report_id", reportId)
            put("created_at", Instant.now().toString())
            put("summary", input.summary.trim().ifBlank { "APS NoteCast bug report" }.take(MAX_SUMMARY_CHARS))
            put("description", input.description.trim().take(MAX_DESCRIPTION_CHARS))
            put("email", senderEmail)
            put("sender_email", senderEmail)
            put("contact", senderEmail)
            put("app", appBlock(context))
            put("environment", environmentBlock(context))
            put("context", contextBlock(state, settings, diagnostics, pendingCrash.isNotBlank()))
            put(
                "logs",
                JSONObject().apply {
                    put("included", includeLogs)
                    put("tail_chars", logText.length)
                    put("total_chars", totalAvailableLogChars)
                    put("truncated", eventLogTotalChars > eventLogText.length)
                    put("text", logText)
                }
            )
        }
    }

    private fun appBlock(context: Context): JSONObject =
        JSONObject().apply {
            put("name", APP_NAME)
            put("version", packageVersionName(context))
            put("title", "$APP_NAME v${packageVersionName(context)}")
            put("website", APP_WEBSITE)
            put("package", context.packageName)
            put("build_type", BuildConfig.BUILD_TYPE)
            put("debug", BuildConfig.DEBUG)
        }

    private fun environmentBlock(context: Context): JSONObject =
        JSONObject().apply {
            put("platform", "Android")
            put("sdk", Build.VERSION.SDK_INT)
            put("release", Build.VERSION.RELEASE)
            put("manufacturer", Build.MANUFACTURER)
            put("brand", Build.BRAND)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("locale", Locale.getDefault().toLanguageTag())
            put("supported_abis", Build.SUPPORTED_ABIS.joinToString(","))
            put("installer", installerPackageName(context))
        }

    private fun contextBlock(
        state: AppUiState,
        settings: AppSettings,
        diagnostics: String,
        hasPendingCrash: Boolean
    ): JSONObject =
        JSONObject().apply {
            put("last_message", state.lastMessage)
            put("file_count", state.files.size)
            put("playlist_count", state.playlists.size)
            put("visible_midi_devices", state.bleDevices.size)
            put(
                "visible_midi_device_details",
                JSONArray().apply {
                    state.bleDevices.forEach { device ->
                        put(
                            JSONObject().apply {
                                put("name", device.name)
                                put("address", device.address)
                                put("rssi", device.rssi ?: JSONObject.NULL)
                                put("likely_midi", device.likelyMidi)
                                put("standard_ble_midi", device.standardBleMidi)
                                put("connectable", device.connectable)
                                put("added", device.added)
                                put("source", device.source)
                                put("detail", device.detail)
                            }
                        )
                    }
                }
            )
            put("diagnostics", diagnostics)
            put("pending_crash_included", hasPendingCrash)
            put(
                "connection",
                JSONObject().apply {
                    put("connected", state.connection.connected)
                    put("connecting", state.connection.connecting)
                    put("scanning", state.connection.scanning)
                    put("bluetooth_available", state.connection.bluetoothAvailable)
                    put("bluetooth_enabled", state.connection.bluetoothEnabled)
                    put("device_name", state.connection.deviceName.orEmpty())
                    put("address", state.connection.address.orEmpty())
                    put("remembered_device_name", state.connection.rememberedDeviceName.orEmpty())
                    put("remembered_device_address", state.connection.rememberedDeviceAddress.orEmpty())
                    put("auto_reconnect_suppressed", state.connection.autoReconnectSuppressed)
                    put("message", state.connection.message)
                }
            )
            put("playback", playbackBlock(state, settings))
            put(
                "settings",
                JSONObject().apply {
                    put("theme", settings.themeMode.preferenceValue)
                    put("font_scale_percent", settings.fontScalePercent)
                    put("midi_library_page_size", settings.midiLibraryPageSize)
                    put("auto_reconnect_enabled", settings.autoReconnectEnabled)
                    put("scan_on_launch", settings.scanOnLaunch)
                    put("reconnect_interval_seconds", settings.reconnectIntervalSeconds)
                    put("reconnect_timeout_seconds", settings.reconnectTimeoutSeconds)
                    put("playback_advance_mode", settings.playbackAdvanceMode.preferenceValue)
                    put("repeat_mode", settings.repeatMode.preferenceValue)
                    put("shuffle_playlists_by_default", settings.shufflePlaylistsByDefault)
                    put("app_volume_percent", state.volumePercent)
                    put("volume_control_mode", settings.volumeControlMode.preferenceValue)
                    put("pedal_output_mode", settings.pedalOutputMode.preferenceValue)
                    put("pedal_value_mode", settings.pedalValueMode.preferenceValue)
                    put("fold_channel_2_into_piano_channel", settings.foldChannel2IntoPianoChannel)
                    put("fold_pedals_into_piano_channel", settings.foldPedalsIntoPianoChannel)
                    put("velocity_scaling_enabled", settings.velocityScalingEnabled)
                    put("minimum_note_velocity", settings.minimumNoteVelocity)
                    put("tempo_percent", settings.tempoPercent)
                    put("transpose_semitones", settings.transposeSemitones)
                    put("exclude_drum_channel_from_transpose", settings.excludeDrumChannelFromTranspose)
                    put("channel_controls", channelControlsBlock(settings.channelControls))
                    put(
                        "current_song_instrument_overrides",
                        instrumentOverridesBlock(settings.instrumentOverridesForSong(state.playback.currentItemId))
                    )
                    put(
                        "last_song_instrument_overrides",
                        instrumentOverridesBlock(settings.instrumentOverridesForSong(state.lastPlayback.itemId))
                    )
                }
            )
        }

    private fun playbackBlock(state: AppUiState, settings: AppSettings): JSONObject {
        val currentItem = state.playback.currentItemId?.let { id -> state.files.firstOrNull { it.id == id } }
        val lastItem = state.lastPlayback.itemId?.let { id -> state.files.firstOrNull { it.id == id } }
        return JSONObject().apply {
            put("mode", state.playback.mode.name)
            put("current_title", state.playback.currentTitle.orEmpty())
            put("current_item_id", state.playback.currentItemId.orEmpty())
            put("playlist_name", state.playback.playlistName.orEmpty())
            put("playlist_id", state.playback.playlistId.orEmpty())
            put("current_track_number", state.playback.currentTrackNumber)
            put("total_tracks", state.playback.totalTracks)
            put("progress_us", state.playback.progressUs)
            put("duration_us", state.playback.durationUs)
            put("progress_percent", progressPercent(state.playback.progressUs, state.playback.durationUs) ?: JSONObject.NULL)
            put("sustain_pedal_pressed", state.playback.sustainPedalPressed)
            put("error", state.playback.error.orEmpty())
            put("current_library_item", libraryItemBlock(currentItem))
            put("detected_channels", channelInfoArray(state.playbackChannels))
            put(
                "current_song_instrument_overrides",
                instrumentOverridesBlock(settings.instrumentOverridesForSong(state.playback.currentItemId))
            )
            put("last_known", lastPlaybackBlock(state.lastPlayback, lastItem, settings))
        }
    }

    private fun lastPlaybackBlock(
        lastPlayback: LastPlaybackSnapshot,
        libraryItem: MidiLibraryItem?,
        settings: AppSettings
    ): JSONObject =
        JSONObject().apply {
            put("mode", lastPlayback.mode.name)
            put("title", lastPlayback.title.orEmpty())
            put("item_id", lastPlayback.itemId.orEmpty())
            put("playlist_name", lastPlayback.playlistName.orEmpty())
            put("playlist_id", lastPlayback.playlistId.orEmpty())
            put("current_track_number", lastPlayback.currentTrackNumber)
            put("total_tracks", lastPlayback.totalTracks)
            put("progress_us", lastPlayback.progressUs)
            put("duration_us", lastPlayback.durationUs)
            put("progress_percent", progressPercent(lastPlayback.progressUs, lastPlayback.durationUs) ?: JSONObject.NULL)
            put("sustain_pedal_pressed", lastPlayback.sustainPedalPressed)
            put("library_item", libraryItemBlock(libraryItem))
            put("detected_channels", channelInfoArray(lastPlayback.channels))
            put("song_instrument_overrides", instrumentOverridesBlock(settings.instrumentOverridesForSong(lastPlayback.itemId)))
        }

    private fun libraryItemBlock(item: MidiLibraryItem?): JSONObject =
        JSONObject().apply {
            put("found", item != null)
            put("title", item?.title.orEmpty())
            put("original_name", item?.originalName.orEmpty())
            put("duration_us", item?.durationUs ?: 0L)
            put("imported_at_ms", item?.importedAtMs ?: 0L)
            put("notes", item?.notes.orEmpty().take(500))
        }

    private fun channelInfoArray(channels: List<PlaybackChannelInfo>): JSONArray =
        JSONArray().apply {
            channels.forEach { channel ->
                put(
                    JSONObject().apply {
                        put("channel", channel.channel)
                        put("label", channel.label.orEmpty())
                        put("instrument_name", channel.instrumentName.orEmpty())
                        put("program_numbers", intArray(channel.programNumbers))
                        put("track_titles", stringArray(channel.trackTitles.take(12)))
                        put("meta_instrument_names", stringArray(channel.metaInstrumentNames.take(12)))
                    }
                )
            }
        }

    private fun channelControlsBlock(controls: List<MidiChannelControl>): JSONObject {
        val soloChannels = controls.filter { it.solo }.map { it.channel }
        val mutedChannels = controls.filter { it.muted }.map { it.channel }
        val nonDefaultVolumes = controls.filter { it.volumePercent != 100 }
        return JSONObject().apply {
            put("solo_channels", intArray(soloChannels))
            put("muted_channels", intArray(mutedChannels))
            put(
                "non_default_volume_channels",
                JSONArray().apply {
                    nonDefaultVolumes.forEach { control ->
                        put(
                            JSONObject().apply {
                                put("channel", control.channel)
                                put("volume_percent", control.volumePercent)
                            }
                        )
                    }
                }
            )
            put("has_solo", soloChannels.isNotEmpty())
            put("has_mutes", mutedChannels.isNotEmpty())
            put("has_channel_volume_overrides", nonDefaultVolumes.isNotEmpty())
        }
    }

    private fun instrumentOverridesBlock(overrides: Map<Int, Int>): JSONObject =
        JSONObject().apply {
            put("count", overrides.size)
            put(
                "channels",
                JSONArray().apply {
                    overrides.toSortedMap().forEach { (channel, program) ->
                        put(
                            JSONObject().apply {
                                put("channel", channel)
                                put("program", program)
                            }
                        )
                    }
                }
            )
        }

    private fun intArray(values: List<Int>): JSONArray =
        JSONArray().apply { values.forEach { put(it) } }

    private fun stringArray(values: List<String>): JSONArray =
        JSONArray().apply { values.forEach { put(it) } }

    private fun progressPercent(progressUs: Long, durationUs: Long): Double? =
        if (durationUs > 0L) {
            (progressUs.coerceIn(0L, durationUs).toDouble() / durationUs.toDouble()) * 100.0
        } else {
            null
        }

    private fun postJson(body: String): Pair<Int, JSONObject> {
        val endpoint = BuildConfig.BUG_REPORT_URL.trim()
        val secret = BuildConfig.BUG_REPORT_SECRET
        if (endpoint.isBlank()) throw IOException("No bug report endpoint is configured.")
        if (secret.isBlank()) throw IOException("No bug report signing secret is configured.")
        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Accept", "application/json, text/plain;q=0.9, */*;q=0.5")
            setRequestProperty("Connection", "close")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "APS NoteCast Bug Reporter")
            setRequestProperty("X-APS-Timestamp", timestamp)
            setRequestProperty("X-APS-Signature", "sha256=${hmacSha256Hex(secret, "$timestamp.$body")}")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val status = connection.responseCode
            val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
                .trim()
            if (status != 200 && status != 202) {
                throw IOException("HTTP $status${if (responseText.isNotBlank()) ": $responseText" else ""}")
            }
            val responseJson = runCatching {
                if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            }.getOrDefault(JSONObject())
            return status to responseJson
        } finally {
            connection.disconnect()
        }
    }

    private fun hmacSha256Hex(secret: String, text: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(text.toByteArray(StandardCharsets.UTF_8)).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }

    private fun packageVersionName(context: Context): String =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        }.getOrNull() ?: BuildConfig.VERSION_NAME

    private fun installerPackageName(context: Context): String =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName).orEmpty()
            }
        }.getOrDefault("")
}
