package com.wildtribe.drive.utils

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Ring-buffer debug logger with 500-entry capacity.
 * Timestamps in IST (UTC+5:30). Entries survive across component restarts.
 * Export via [exportToFile] using FileProvider for sharing.
 *
 * TEST: Debug logs available for export
 */
object DebugLogger {

    private const val MAX_ENTRIES = 500
    private const val TAG = "WildTribeDrive"

    private val buffer = ConcurrentLinkedDeque<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata") // IST
    }

    /** Add a log entry. Thread-safe. */
    fun log(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] $tag: $message"
        Log.d(TAG, entry)

        buffer.addLast(entry)
        // Trim to max size
        while (buffer.size > MAX_ENTRIES) {
            buffer.pollFirst()
        }
    }

    /** Get all entries as a list (newest last). */
    fun getEntries(): List<String> = buffer.toList()

    /** Get entries as a single string for display. */
    fun getLog(): String = buffer.joinToString("\n")

    /** Clear all entries. */
    fun clear() {
        buffer.clear()
        log("LOGGER", "Log cleared")
    }

    /**
     * Write log to a file in the app's cache dir.
     * Returns the File, or null on error.
     */
    fun exportToFile(context: Context): java.io.File? {
        return try {
            val logsDir = java.io.File(context.cacheDir, "logs").also { it.mkdirs() }
            val file = java.io.File(logsDir, "wildtribe_log_${System.currentTimeMillis()}.txt")
            file.writeText(
                "Wild Tribe Drive Debug Log\n" +
                "Exported: ${dateFormat.format(Date())}\n" +
                "Entries: ${buffer.size}\n" +
                "═".repeat(60) + "\n\n" +
                getLog()
            )
            log("EXPORT", "Log exported to ${file.name}")
            file
        } catch (e: Exception) {
            log("EXPORT_ERROR", "Failed to export log: ${e.message}")
            null
        }
    }

    // Legacy aliases for compatibility
    fun d(tag: String, message: String) = log(tag, message)
    fun e(tag: String, message: String) = log("ERR/$tag", message)
    fun w(tag: String, message: String) = log("WARN/$tag", message)
}
