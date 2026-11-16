package com.example.altyazi

/**
 * Conservative lexical disambiguation of unmistakable literal MT errors.
 * Not a translation model: only edits a problematic Turkish fragment when
 * both the English source and the literal output independently match.
 * Never alters the English OCR text or a model's already-natural result.
 */
object DialogueDisambiguation1038 {
    private val tagQuestion = Regex("""(?:[.!?]\s+|,\s*)right\?\s*$""", RegexOption.IGNORE_CASE)
    private val literalRight = Regex("""\bsağ\s*\?$""", RegexOption.IGNORE_CASE)
    private val untranslatedNerd = Regex("""\b(?:poindexter|pointexter)(?:['’]sın)?\b""", RegexOption.IGNORE_CASE)
    private val birdsBees = Regex("""kuşlar\s+ve\s+arılar""", RegexOption.IGNORE_CASE)
    private val sororityLiteral = Regex("""kardeşlik\s+kızı""", RegexOption.IGNORE_CASE)

    fun repair(source: String, translated: String): String {
        if (source.isBlank() || translated.isBlank()) return translated
        var result = translated
        if (tagQuestion.containsMatchIn(source)) {
            result = result.replace(literalRight, "değil mi?")
            result = result.replace(Regex("""\.\s+değil mi\?$""", RegexOption.IGNORE_CASE), ", değil mi?")
        }
        if (Regex("""\bpoindexter\b""", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            // A proper name is never replaced unless the source literally
            // identifies the slang noun poindexter.
            result = result.replace(untranslatedNerd) { m ->
                if (m.value.endsWith("sın", ignoreCase = true)) "ineksin" else "inek"
            }
        }
        if (Regex("""\bbirds\s+and\s+the\s+bees\b""", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            result = result.replace(birdsBees, "cinsellik")
        }
        if (Regex("""\bsorority\s+girl\b""", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            result = result.replace(sororityLiteral, "öğrenci topluluğundaki kız")
        }
        // "orders" in a command/request is "emirler", not purchase orders.
        // Both English context and the Turkish literal must match.
        if (Regex("""\b(?:what\s+are\s+your|await(?:ing)?\s+your)\s+orders\b""", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            result = result.replace(Regex("""\bsiparişleriniz\b""", RegexOption.IGNORE_CASE), "emirleriniz")
        }
        // Idiom is a life-or-death situation, not an emotional touching moment.
        if (Regex("""\btouch\s+and\s+go\b""", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            result = result.replace(Regex("""(?<!\p{L})(?:çok\s+)?dokunaklıydı(?!\p{L})""", RegexOption.IGNORE_CASE), "durumu kritikti")
        }
        if (Regex("""\b(?:you|they|we)\s+are\s+avenged\b""", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            result = result.replace(Regex("""(?<!\p{L})intikamınızı\s+aldınız(?!\p{L})""", RegexOption.IGNORE_CASE)) { m ->
                if (m.value.first().isUpperCase()) "İntikamınız alındı" else "intikamınız alındı"
            }
        }
        // 10.42: targeted repairs grounded in the actual live subtitles.
        // With an optional speaker label and trailing sound cue, "Mark?"
        // is a vocative. The literal Turkish noun "işaret" must not replace it.
        val addressedMark = Regex("""^\s*(?:\[[^]]+]\s*)?mark\?\s*(?:\[(?:shuddering|panting|sighs|groans|grunts)]\s*)?$""", RegexOption.IGNORE_CASE)
        if (addressedMark.matches(source) &&
            Regex("""işaret\?""", RegexOption.IGNORE_CASE).containsMatchIn(result)) {
            result = result.replace(Regex("""işaret\?""", RegexOption.IGNORE_CASE), "Mark?")
        }
        // Never replace the entire translation here. A subtitle may contain
        // more than the idiom: "You guys are the best. Thank you." The old
        // whole-string assignment silently discarded the following sentence
        // (and any speaker label). Repair only the literal Turkish fragment.
        if (Regex("""\byou\s+guys\s+are\s+the\s+best\b""", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            result = result.replace(
                Regex("""you['’]?dekiler\s+en\s+iyisidir\.?""", RegexOption.IGNORE_CASE),
                "Siz harikasınız."
            )
        }
        if (Regex("""\bjust\s+spitballing\s+here\b""", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            val bad = Regex("""burada\s+sadece\s+tükürük\s+topları""", RegexOption.IGNORE_CASE)
            val single = Regex("""^ve\s+burada\s+sadece\s+tükürük\s+topları\.?$""", RegexOption.IGNORE_CASE)
            result = if (single.matches(result.trim())) {
                "Öylesine fikir yürütüyorum."
            } else result.replace(bad, "öylesine fikir yürütüyorum")
        }
        if (Regex("""\bbig\s+shoes\s+to\s+fill\b""", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            result = result.replace(
                Regex("""doldurmam\s+gereken\s+büyük\s+bir\s+yer\s+var""", RegexOption.IGNORE_CASE),
                "Benden öncekinin yerini doldurmalıyım"
            )
        }
        if (Regex("""\blook\s+regal\b""", RegexOption.IGNORE_CASE).containsMatchIn(source) &&
            Regex("""(?:çok\s+)?güzel\s+görünüyorsun""", RegexOption.IGNORE_CASE).containsMatchIn(result)) {
            result = result.replace(Regex("""(?:çok\s+)?güzel\s+görünüyorsun""", RegexOption.IGNORE_CASE), "heybetli görünüyorsun")
        }
        // 10.42.3: preserve all surrounding clauses; replace only a literal
        // mistranslated fragment when both source idiom and wrong output match.
        if (Regex("""\bthrew\s+(?:his|her|my|their)\s+back\s+out\s+at\s+work\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(source)) {
            result = result.replace(
                Regex("""(?<!\p{L})(?:sırtını|belini)\s+işten\s+attı(?!\p{L})""", RegexOption.IGNORE_CASE),
                "işte belini incitti"
            )
        }
        if (Regex("""\bwon['’]?t\s+make\s+a\s+difference\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(source)) {
            result = result.replace(
                Regex("""(?<!\p{L})bir\s+şey\s+olmaz(?!\p{L})""", RegexOption.IGNORE_CASE),
                "hiçbir şeyi değiştirmeyecek"
            )
        }
        return result
    }
}
