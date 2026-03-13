package com.wildtribe.drive.data

import com.wildtribe.drive.utils.DebugLogger

/**
 * Sealed class representing all BLE command types sent to MotoRound.
 *
 * TEST: NAV:R:300m command received by ESP32 (check Serial Monitor)
 */
sealed class NavCommand {
    data class Navigation(val dir: String, val dist: String) : NavCommand()
    data class Speed(val kph: Int) : NavCommand()
    data class Message(val text: String) : NavCommand()
    data class Trip(val dist: String, val duration: String, val eta: String) : NavCommand()
    object Clear : NavCommand()

    companion object {
        /**
         * Parse a raw BLE command string back into a [NavCommand].
         * Logs errors and unknown commands — FIX 4 implemented here.
         */
        fun parse(raw: String): NavCommand? {
            return try {
                when {
                    raw == "CLR" -> Clear
                    raw.startsWith("NAV:") -> {
                        val parts = raw.removePrefix("NAV:").split(":")
                        if (parts.size >= 2) Navigation(parts[0], parts[1])
                        else {
                            DebugLogger.log("NAV_PARSE_ERROR", "Bad NAV format: '$raw'")
                            null
                        }
                    }
                    raw.startsWith("SPD:") -> {
                        val kph = raw.removePrefix("SPD:").toIntOrNull()
                        if (kph != null) Speed(kph)
                        else {
                            DebugLogger.log("NAV_PARSE_ERROR", "Bad SPD value: '$raw'")
                            null
                        }
                    }
                    raw.startsWith("MSG:") -> Message(raw.removePrefix("MSG:"))
                    raw.startsWith("TRP:") -> {
                        val parts = raw.removePrefix("TRP:").split(":")
                        if (parts.size >= 3) Trip(parts[0], parts[1], parts[2])
                        else {
                            DebugLogger.log("NAV_PARSE_ERROR", "Bad TRP format: '$raw'")
                            null
                        }
                    }
                    else -> {
                        // FIX 4: Log unrecognized commands
                        DebugLogger.log("NAV_UNKNOWN", "Unrecognized command: '$raw'")
                        null
                    }
                }
            } catch (e: Exception) {
                // FIX 4: Log parse exceptions
                DebugLogger.log("NAV_PARSE_ERROR", "Failed to parse: '$raw' — ${e.message}")
                null
            }
        }
    }
}

/** Navigation direction enum */
enum class NavDirection {
    NONE, STRAIGHT, LEFT, RIGHT, UTURN, ARRIVE;

    companion object {
        fun fromCode(code: String?): NavDirection = when (code) {
            "R"  -> RIGHT
            "L"  -> LEFT
            "S"  -> STRAIGHT
            "UT" -> UTURN
            "AR" -> ARRIVE
            else -> NONE
        }
    }
}
