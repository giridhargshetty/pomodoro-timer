package com.wildtribe.drive.util

import com.wildtribe.drive.utils.DebugLogger

/**
 * Legacy logging helper — delegates to [DebugLogger].
 * Kept for source compatibility.
 */
object LogHelper {
    fun d(tag: String, msg: String) = DebugLogger.log(tag, msg)
    fun i(tag: String, msg: String) = DebugLogger.log(tag, msg)
    fun w(tag: String, msg: String) = DebugLogger.log("WARN/$tag", msg)
    fun e(tag: String, msg: String, throwable: Throwable? = null) =
        DebugLogger.log("ERR/$tag", "$msg${throwable?.let { " — ${it.message}" } ?: ""}")
}
