package com.example.altyazi

/**
 * "Yo" bir hitaptır; tek başına küfür değildir. Türkçede zorunlu "lan" yoktur.
 * Küfür varsa bu sınıf küfür onarımı yapmaz, asıl çeviriyi korur.
 * Yeni selamlama dayatmak yerine yalnız kaynakta olmayan sert hitapları temizler.
 */
object YoContext10427 {
    private val heyYoSource = Regex("^hey[,!]?\\s+yo\\b", RegexOption.IGNORE_CASE)
    private val repeatedHey = Regex("^hey[,!.\\s]+hey\\b", RegexOption.IGNORE_CASE)
    private val yoStart = Regex("^yo+\\b", RegexOption.IGNORE_CASE)
    private val yoExclamation = Regex("^yo+\\s*!", RegexOption.IGNORE_CASE)
    private val hardEnglish = Regex("\\b(fuck\\w*|shit\\w*|bitch\\w*|motherfuck\\w*|asshole\\w*|cunt\\w*|bastard\\w*|damn\\w*)\\b", RegexOption.IGNORE_CASE)
    private val genericGreeting = Regex("^(?:hey|selam)\\b[\\s,!.…]*", RegexOption.IGNORE_CASE)
    private val hasTurkishLan = Regex("\\b(lan|ulan)\\b", RegexOption.IGNORE_CASE)
    private val addedHarshStart = Regex("^(?:lan|ulan)\\b([\\s,!.…]*)", RegexOption.IGNORE_CASE)
    private val addedHarshEnd = Regex("\\s+(?:lan|ulan)([?.!]*)$", RegexOption.IGNORE_CASE)

    fun startsWithYo(source: String): Boolean {
        val s = source.trimStart(' ', '-', '–', '\"', '\'')
        return yoStart.containsMatchIn(s)
    }

    fun correct(source: String, translated: String): String {
        val current = translated.trim()
        // "Hey, yo, Ray!" must not become "Hey, hey, Ray!".
        if (heyYoSource.containsMatchIn(source.trimStart()))
            return repeatedHey.replaceFirst(current, "Hey")
        if (!startsWithYo(source)) return translated
        if (hardEnglish.containsMatchIn(source)) {
            // The swear is already rendered by "lan"; avoid stacking "Hey" onto it.
            val greeting = genericGreeting.find(current)
            if (greeting != null) {
                val tail = current.substring(greeting.value.length).trimStart()
                if (hasTurkishLan.containsMatchIn(tail))
                    return tail.replaceFirstChar { it.uppercaseChar() }
            }
            return current  // Keep actual profanity; never strip it as an unwanted Yo.
        }
        val match = addedHarshStart.find(current)
        if (match != null) {
            val rest = current.substring(match.value.length).trimStart()
            if (rest.isEmpty()) return "Hey!"
            val natural = rest.replaceFirstChar { it.uppercaseChar() }
            // "Yo!" güçlü dikkat çekmedir, "Yo," ise cümleye eklenmesi şart olmayan hitap.
            return if (yoExclamation.containsMatchIn(source.trimStart())) "Hey, ${natural.replaceFirstChar { it.lowercaseChar() }}" else natural
        }
        return addedHarshEnd.replace(current) { it.groupValues[1] }
    }
}
