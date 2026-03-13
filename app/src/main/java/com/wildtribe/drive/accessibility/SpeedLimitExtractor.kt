package com.wildtribe.drive.accessibility

/**
 * Extracts speed limit values from raw text collected by [MapsAccessibilityService].
 *
 * Google Maps shows speed limit as a badge in some regions. This extractor
 * handles multiple formats and filters out obviously wrong values.
 */
object SpeedLimitExtractor {

    private val patterns = listOf(
        Regex("""[Ss]peed\s*[Ll]imit[:\s]+(\d{2,3})"""),
        Regex("""(\d{2,3})\s*(?:km/?h|kph)""", RegexOption.IGNORE_CASE),
        Regex("""(\d{2,3})\s*mph""", RegexOption.IGNORE_CASE),
        // Speed sign badge — standalone number in speed-sign context
        Regex("""^\s*(\d{2,3})\s*$""")
    )

    /** Valid speed limit range: 20–200 kph */
    private val validRange = 20..200

    /**
     * Extract speed limit from list of text nodes.
     * Returns null if no valid speed limit found.
     */
    fun extract(texts: List<String>): Int? {
        for (text in texts) {
            for (pattern in patterns) {
                val match = pattern.find(text)
                val value = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: continue
                if (value in validRange) return value
            }
        }
        return null
    }
}
