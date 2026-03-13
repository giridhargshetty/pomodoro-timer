package com.wildtribe.drive.accessibility

/**
 * Structured navigation data extracted from Google Maps screen text.
 */
data class NavData(
    val turn: String? = null,           // "R", "L", "S", "UT", "AR"
    val distance: String? = null,       // "300 m", "1.2 km"
    val eta: String? = null,            // "18:30"
    val speedLimit: Int? = null,        // 80
    val remainingDist: String? = null,  // "5.3 km"
    val street: String? = null          // "MG Road"
) {
    fun hasAnyData() = turn != null || distance != null || eta != null
}

/**
 * Parses raw text strings collected from Google Maps accessibility nodes into
 * structured [NavData]. Handles English + Hindi transliteration common in India.
 *
 * TEST: Maps navigation updates appear on Dashboard within 1 second
 */
class NavigationParser {

    // Distance patterns: "300 m", "1.2 km", "500 ft", "0.5 mi", "500m" (no space)
    private val distanceRegex = Regex(
        """(\d+(?:\.\d+)?)\s*(m\b|km\b|ft\b|mi\b)""",
        RegexOption.IGNORE_CASE
    )

    // Time patterns: "18:30", "6:30 PM", "12:45 AM", "Arrive at 6:30 PM"
    private val timeRegex = Regex(
        """(\d{1,2}:\d{2}(?:\s*[AP]M)?)\b""",
        RegexOption.IGNORE_CASE
    )

    // Speed limit text: "Speed limit: 70", "Speed limit 100"
    private val speedLimitRegex = Regex(
        """[Ss]peed\s*[Ll]imit[:\s]+(\d{2,3})""",
        RegexOption.IGNORE_CASE
    )

    // Turn keywords ordered: check U-turn before left/right
    private val uTurnKeywords = listOf("u-turn", "uturn", "u turn", "make a u", "यू-टर्न")
    private val arrivedKeywords = listOf(
        "you have arrived", "arrived", "destination", "you are here",
        "your destination is", "पहुंच गए"
    )
    private val turnRightKeywords = listOf(
        "turn right", "keep right", "exit right", "take right",
        "bear right", "dakshina", "daaye", "दाएं"
    )
    private val turnLeftKeywords = listOf(
        "turn left", "keep left", "exit left", "take left",
        "bear left", "baayi", "बाएं"
    )
    private val straightKeywords = listOf(
        "continue", "go straight", "head ", "proceed", "keep going",
        "stay on", "follow"
    )

    /**
     * Parse a list of text strings from the Maps screen.
     *
     * Real Google Maps strings this handles:
     *  - "In 300 m, turn right onto MG Road"
     *  - "Turn left"
     *  - "Continue for 1.2 km"
     *  - "You have arrived at your destination"
     *  - "In 500 m, make a U-turn"
     *  - "Arrive at 6:30 PM · 5.3 km remaining"
     *  - Mixed Hindi-English like "In 500m mudeyiri daaye"
     */
    fun parse(texts: List<String>): NavData {
        val combined = texts.joinToString(" | ").lowercase()
        val rawJoined = texts.joinToString(" ")

        // ── Turn Direction ─────────────────────────────────────────────────
        val turn: String? = when {
            uTurnKeywords.any { combined.contains(it) }     -> "UT"
            arrivedKeywords.any { combined.contains(it) }   -> "AR"
            turnRightKeywords.any { combined.contains(it) } -> "R"
            turnLeftKeywords.any { combined.contains(it) }  -> "L"
            straightKeywords.any { combined.contains(it) }  -> "S"
            else                                             -> null
        }

        // ── Distances ──────────────────────────────────────────────────────
        val distMatches = distanceRegex.findAll(rawJoined).toList()
        val distance = distMatches.firstOrNull()?.value?.trim()
        val remainingDist = if (distMatches.size > 1 &&
            distMatches[1].value.trim() != distance) {
            distMatches[1].value.trim()
        } else null

        // ── ETA ────────────────────────────────────────────────────────────
        var eta: String? = null
        for (text in texts) {
            if (eta == null) {
                eta = timeRegex.find(text)?.value?.trim()
            }
        }

        // ── Speed Limit ────────────────────────────────────────────────────
        var speedLimit: Int? = null
        for (text in texts) {
            if (speedLimit == null) {
                speedLimit = speedLimitRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()
            }
        }

        // ── Street Name (heuristic: text that looks like a road name) ──────
        val street = extractStreetName(texts, combined)

        return NavData(
            turn = turn,
            distance = distance,
            eta = eta,
            speedLimit = speedLimit,
            remainingDist = remainingDist,
            street = street
        )
    }

    /**
     * Attempt to extract street/road name from Maps text.
     * Looks for "onto <Name>", "on <Name>", or lines that look like road names.
     */
    private fun extractStreetName(texts: List<String>, combined: String): String? {
        val ontoRegex = Regex("""(?:onto|on)\s+([A-Z][A-Za-z\s]{3,30})""")
        for (text in texts) {
            val match = ontoRegex.find(text)
            if (match != null) return match.groupValues[1].trim()
        }
        return null
    }
}
