package com.example.altyazi

/**
 * Restores the grammatical grouping of names already present in the source.
 * This does not invent or translate names and does not use a list of people,
 * titles, games, films or memorized dialogue.
 */
object ProperNameStructure10427 {
    private val joinedFamilyName = Regex("""\b([A-Z][a-z]{2,})\s+and\s+([A-Z][a-z]{2,})\s+([A-Z][a-z]{2,})\b""")

    fun repair(source: String, translated: String): String {
        var output = translated
        for (match in joinedFamilyName.findAll(source)) {
            val (first, second, last) = match.destructured
            // A translator incorrectly grouped the family name with 'and':
            // "A, B ve C" while the source contains "A and B C".
            // Only replace if all three names are still present in that order.
            val broken = Regex(
                "(?i)(?<![\\p{L}])" + Regex.escape(first) +
                    "\\s*,\\s*" + Regex.escape(second) +
                    "\\s+ve\\s+" + Regex.escape(last) + "(?![\\p{L}])"
            )
            output = broken.replace(output, "$first ve $second $last")
        }
        return output
    }
}
