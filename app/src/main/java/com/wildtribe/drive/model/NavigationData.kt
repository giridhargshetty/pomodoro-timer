package com.wildtribe.drive.model

/**
 * Parsed navigation state displayed on the dashboard.
 */
data class NavigationData(
    val direction: NavDirection = NavDirection.NONE,
    val distanceText: String = "",
    val instructionText: String = "Waiting for navigation…",
    val speed: Int = 0,
    val speedUnit: String = "km/h",
    val eta: String = "--:--",
    val distanceRemaining: String = "--",
    val tripDuration: String = "--",
    val lastCommandTime: Long = 0L
) {
    val isWaiting: Boolean
        get() = (System.currentTimeMillis() - lastCommandTime) > 10_000L && lastCommandTime > 0
}

/**
 * Navigation arrow directions matching the MotoRound command set.
 */
enum class NavDirection {
    NONE,
    STRAIGHT,
    LEFT,
    RIGHT,
    UTURN,
    ARRIVE
}

/**
 * Parsed representation of incoming BLE/Tasker commands.
 */
sealed class NavCommand {
    data class TurnRight(val distance: String) : NavCommand()
    data class TurnLeft(val distance: String) : NavCommand()
    data class GoStraight(val distance: String) : NavCommand()
    object UTurn : NavCommand()
    object Arrive : NavCommand()
    data class SpeedUpdate(val speed: Int) : NavCommand()
    data class Message(val text: String) : NavCommand()
    data class TripInfo(val distance: String, val duration: String, val eta: String) : NavCommand()
    object Clear : NavCommand()
    data class Unknown(val raw: String) : NavCommand()

    companion object {
        /**
         * Parse a raw UTF-8 command string into a typed [NavCommand].
         *
         * Supported formats:
         *  NAV:R:300m   NAV:L:1.2km   NAV:S:0   NAV:UT:   NAV:AR:
         *  SPD:65        MSG:text      TRP:45km:1h23m:18:30   CLR
         */
        fun parse(raw: String): NavCommand {
            val cmd = raw.trim()
            return when {
                cmd.startsWith("NAV:R:") -> TurnRight(cmd.removePrefix("NAV:R:"))
                cmd.startsWith("NAV:L:") -> TurnLeft(cmd.removePrefix("NAV:L:"))
                cmd.startsWith("NAV:S:")  -> GoStraight(cmd.removePrefix("NAV:S:"))
                cmd.startsWith("NAV:UT") -> UTurn
                cmd.startsWith("NAV:AR") -> Arrive
                cmd.startsWith("SPD:") -> {
                    val spd = cmd.removePrefix("SPD:").toIntOrNull() ?: 0
                    SpeedUpdate(spd)
                }
                cmd.startsWith("MSG:") -> Message(cmd.removePrefix("MSG:"))
                cmd.startsWith("TRP:") -> {
                    val parts = cmd.removePrefix("TRP:").split(":")
                    TripInfo(
                        distance = parts.getOrElse(0) { "--" },
                        duration = parts.getOrElse(1) { "--" },
                        eta = parts.getOrElse(2) { "--:--" }
                    )
                }
                cmd == "CLR" -> Clear
                else -> Unknown(cmd)
            }
        }
    }
}
