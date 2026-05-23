package com.alexanderpeppe.pianobeam.reporting

import java.time.Instant

object AppEventLog {
    private const val MAX_ENTRIES = 240
    private val lock = Any()
    private val entries = ArrayDeque<String>()

    fun append(message: String) {
        val clean = message.trim()
        if (clean.isEmpty()) return
        synchronized(lock) {
            entries.addLast("${Instant.now()}  $clean")
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
    }

    fun tailText(maxChars: Int = 64_000): String {
        val text = synchronized(lock) { entries.joinToString("\n") }
        if (maxChars <= 0 || text.length <= maxChars) return text
        return "[Earlier app events truncated]\n" + text.takeLast(maxChars)
    }

    fun totalTextChars(): Int = synchronized(lock) { entries.sumOf { it.length + 1 } }
}
