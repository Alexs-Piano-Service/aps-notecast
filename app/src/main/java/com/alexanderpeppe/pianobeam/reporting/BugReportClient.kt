package com.alexanderpeppe.pianobeam.reporting

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.alexanderpeppe.pianobeam.BuildConfig
import com.alexanderpeppe.pianobeam.data.AppSettings
import com.alexanderpeppe.pianobeam.data.AppUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val contact: String,
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
        val logText = buildString {
            if (pendingCrash.isNotBlank()) {
                appendLine("Pending crash from previous run")
                appendLine("-------------------------------")
                appendLine(pendingCrash)
                appendLine()
            }
            if (includeLogs) append(AppEventLog.tailText(LOG_TAIL_CHARS))
        }
        return JSONObject().apply {
            put("report_id", reportId)
            put("created_at", Instant.now().toString())
            put("summary", input.summary.trim().ifBlank { "APS NoteCast bug report" }.take(MAX_SUMMARY_CHARS))
            put("description", input.description.trim().take(MAX_DESCRIPTION_CHARS))
            put("contact", input.contact.trim())
            put("app", appBlock(context))
            put("environment", environmentBlock(context))
            put("context", contextBlock(state, settings, diagnostics, pendingCrash.isNotBlank()))
            put(
                "logs",
                JSONObject().apply {
                    put("included", includeLogs)
                    put("tail_chars", logText.length)
                    put("total_chars", AppEventLog.totalTextChars())
                    put("truncated", AppEventLog.totalTextChars() > logText.length)
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
                    put("remembered_device_name", state.connection.rememberedDeviceName.orEmpty())
                    put("auto_reconnect_suppressed", state.connection.autoReconnectSuppressed)
                    put("message", state.connection.message)
                }
            )
            put(
                "playback",
                JSONObject().apply {
                    put("mode", state.playback.mode.name)
                    put("current_title", state.playback.currentTitle.orEmpty())
                    put("playlist_name", state.playback.playlistName.orEmpty())
                    put("progress_us", state.playback.progressUs)
                    put("duration_us", state.playback.durationUs)
                    put("error", state.playback.error.orEmpty())
                }
            )
            put(
                "settings",
                JSONObject().apply {
                    put("theme", settings.themeMode.preferenceValue)
                    put("auto_reconnect_enabled", settings.autoReconnectEnabled)
                    put("scan_on_launch", settings.scanOnLaunch)
                    put("reconnect_interval_seconds", settings.reconnectIntervalSeconds)
                    put("reconnect_timeout_seconds", settings.reconnectTimeoutSeconds)
                    put("playback_advance_mode", settings.playbackAdvanceMode.preferenceValue)
                    put("repeat_mode", settings.repeatMode.preferenceValue)
                    put("tempo_percent", settings.tempoPercent)
                    put("transpose_semitones", settings.transposeSemitones)
                }
            )
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
