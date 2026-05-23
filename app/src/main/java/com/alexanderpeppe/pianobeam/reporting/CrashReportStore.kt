package com.alexanderpeppe.pianobeam.reporting

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import kotlin.system.exitProcess

object CrashReportStore {
    private const val PREFS_NAME = "aps_notecast_crash_report"
    private const val KEY_PENDING_CRASH = "pending_crash"
    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            save(appContext, thread, throwable)
            previous?.uncaughtException(thread, throwable) ?: exitProcess(2)
        }
    }

    fun pendingCrash(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_CRASH, "")
            .orEmpty()

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_CRASH)
            .apply()
    }

    private fun save(context: Context, thread: Thread, throwable: Throwable) {
        val stack = StringWriter().also { writer ->
            throwable.printStackTrace(PrintWriter(writer))
        }.toString()
        val report = buildString {
            appendLine("Uncaught exception at ${Instant.now()}")
            appendLine("Thread: ${thread.name}")
            appendLine(stack)
        }.take(64_000)
        AppEventLog.append("Uncaught exception: ${throwable.javaClass.simpleName}: ${throwable.message.orEmpty()}")
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_CRASH, report)
            .commit()
    }
}
