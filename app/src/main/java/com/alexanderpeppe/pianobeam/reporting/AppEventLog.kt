package com.alexanderpeppe.pianobeam.reporting

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant

object AppEventLog {
    private const val PREFS_NAME = "aps_notecast_event_log"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 240
    private val lock = Any()
    private val entries = ArrayDeque<String>()
    @Volatile
    private var prefs: SharedPreferences? = null

    fun install(context: Context) {
        synchronized(lock) {
            if (prefs != null) return
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existingEntries = entries.toList()
            entries.clear()
            prefs
                ?.getString(KEY_ENTRIES, "")
                .orEmpty()
                .lineSequence()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
                .forEach { entries.addLast(it) }
            existingEntries.forEach { entries.addLast(it) }
            trimLocked()
            persistLocked()
        }
    }

    fun append(message: String) {
        val clean = message.trim()
        if (clean.isEmpty()) return
        synchronized(lock) {
            entries.addLast("${Instant.now()}  $clean")
            trimLocked()
            persistLocked()
        }
    }

    fun tailText(maxChars: Int = 64_000): String {
        val text = synchronized(lock) { entries.joinToString("\n") }
        if (maxChars <= 0 || text.length <= maxChars) return text
        return "[Earlier app events truncated]\n" + text.takeLast(maxChars)
    }

    fun totalTextChars(): Int = synchronized(lock) { entries.joinToString("\n").length }

    fun flush() {
        synchronized(lock) {
            prefs
                ?.edit()
                ?.putString(KEY_ENTRIES, entries.joinToString("\n"))
                ?.commit()
        }
    }

    private fun trimLocked() {
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    private fun persistLocked() {
        prefs
            ?.edit()
            ?.putString(KEY_ENTRIES, entries.joinToString("\n"))
            ?.apply()
    }
}
