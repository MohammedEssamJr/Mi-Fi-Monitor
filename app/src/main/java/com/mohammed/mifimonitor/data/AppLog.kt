package com.mohammed.mifimonitor.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal on-device logger so problems can be diagnosed without a computer
 * or Logcat access. Writes every line to a file (mifi_debug.log, in the
 * app's private storage) AND keeps a live in-memory list the Logs screen
 * reads directly, so new entries show up immediately while the app runs.
 */
object AppLog {
    private const val FILE_NAME = "mifi_debug.log"
    private const val MAX_IN_MEMORY = 400

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null

    // Compose-observable — the Logs screen recomposes automatically as
    // new lines come in, no manual refresh needed.
    val entries = mutableStateListOf<String>()

    /** Call once (e.g. from MainActivity.onCreate) before any log() calls. */
    fun init(context: Context) {
        if (logFile != null) return
        logFile = File(context.applicationContext.filesDir, FILE_NAME)
        // Preload whatever was logged in previous app runs, most recent last.
        runCatching {
            logFile?.takeIf { it.exists() }?.readLines()?.takeLast(MAX_IN_MEMORY)?.let {
                entries.addAll(it)
            }
        }
    }

    fun log(tag: String, message: String) {
        val line = "${timeFormat.format(Date())} [$tag] $message"
        entries.add(line)
        if (entries.size > MAX_IN_MEMORY) entries.removeAt(0)
        runCatching { logFile?.appendText(line + "\n") }
    }

    fun logError(tag: String, e: Throwable) {
        log(tag, "ERROR ${e.javaClass.simpleName}: ${e.message}")
    }

    /** Full history from disk (may be longer than what's held in memory). */
    fun readFullLog(): String = runCatching { logFile?.readText() }.getOrNull() ?: entries.joinToString("\n")

    fun clear() {
        entries.clear()
        runCatching { logFile?.writeText("") }
    }
}
