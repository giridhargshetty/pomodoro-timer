package com.wildtribe.drive.util

import android.util.Log
import com.wildtribe.drive.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Centralized logging helper with:
 *  - Debug-only console output (stripped in release builds via ProGuard)
 *  - In-memory ring buffer for log export (last 500 entries)
 *  - File export to external storage for bug reports
 */
object LogHelper {

    private const val MAX_LOG_ENTRIES = 500
    private val logBuffer = ConcurrentLinkedDeque<String>()
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun d(tag: String, msg: String) {
        if (BuildConfig.ENABLE_LOGGING) Log.d("WildTribe/$tag", msg)
        appendToBuffer("D", tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.ENABLE_LOGGING) Log.i("WildTribe/$tag", msg)
        appendToBuffer("I", tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w("WildTribe/$tag", msg)
        appendToBuffer("W", tag, msg)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        Log.e("WildTribe/$tag", msg, throwable)
        appendToBuffer("E", tag, "$msg${throwable?.let { " – ${it.message}" } ?: ""}")
    }

    private fun appendToBuffer(level: String, tag: String, msg: String) {
        val entry = "${sdf.format(Date())} [$level] $tag: $msg"
        logBuffer.addLast(entry)
        if (logBuffer.size > MAX_LOG_ENTRIES) logBuffer.pollFirst()
    }

    /** Return all buffered log lines as a single string for export. */
    fun exportLogs(): String = logBuffer.joinToString("\n")

    /** Write logs to a file in the app's external files directory. */
    fun writeLogsToFile(externalFilesDir: File): File {
        val filename = "wildtribe_log_${System.currentTimeMillis()}.txt"
        val file = File(externalFilesDir, filename)
        FileWriter(file).use { writer ->
            writer.write("=== Wild Tribe Drive Debug Log ===\n")
            writer.write("Exported: ${sdf.format(Date())}\n\n")
            writer.write(exportLogs())
        }
        return file
    }
}
