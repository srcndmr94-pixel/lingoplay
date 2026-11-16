package com.example.altyazi

/** Checks structural errors without assuming that a fluent sentence is a faithful translation. */
object TranslationQualityGate {
    enum class Problem {
        INVENTED_QUESTION, LOST_NEGATION, UNTRANSLATED,
        REVERSED_COMMAND, LOST_PROHIBITION, IMPERATIVE_AS_INFINITIVE,
        LOST_PROFANITY, LITERAL_IDIOM, MISSING_QUESTION, MISSING_DETAIL, ENGLISH_FRAGMENT,
        INVENTED_PLACEHOLDER, PERSON_REVERSAL, UNTRANSLATED_SLANG
    }
    private val englishResidue = Regex("\\b(?:the|you|your|we|they|this|that|with|from|would|should|intro)\\b", RegexOption.IGNORE_CASE)
    // High-confidence mismatch: an explicitly second-person English sentence was
    // rendered as a Turkish first-person present-tense statement. Do not guess
    // when the English source also contains a first-person participant.
    private val explicitEnglishSecondPerson = Regex("""\b(?:you|y['’]?all|yall|whatchu)\b""", RegexOption.IGNORE_CASE)
    private val englishFirstPerson = Regex("""\b(?:i|i['’]m|i['’]ve|i['’]ll|we|we['’]re|my|me|us|our)\b""", RegexOption.IGNORE_CASE)
    private val turkishFirstPerson = Regex("""\b(?:ben|biz)\b|\b[\p{L}]+(?:[ıiuü]yorum|[ıiuü]yoruz)\b""", RegexOption.IGNORE_CASE)
    private val negativeEnglish = Regex("\\b(?:not|never|nothing|nobody|nowhere|cannot|can't|don't|doesn't|didn't|won't|wouldn't|shouldn't|couldn't|isn't|aren't|wasn't|weren't)\\b", RegexOption.IGNORE_CASE)
    private val negativeTurkish = Regex("(?<![\\p{L}])(?:değil(?:iz|im|sin|siniz|ler)?|yok|hayır|asla|hiç|kimse|hiçbir|olmaz|olmadı|olmadık|olmayacak|istemiyorum|istemez|istemedim|yapmadı|yapmadık)(?![\\p{L}])|(?:m[ae](?:d[ıiuü]|y[ae]c[ae]k|m[ıiuü]ş|m[ae]|z(?:di|miş)?))|(?:m[ıiuü]yor)", RegexOption.IGNORE_CASE)

    // Unicode case-insensitive matching makes Turkish 'sakın' also match
    // 'sakin', reversing the polarity of "Sakin ol, ...". Keep explicit
    // spellings in a case-sensitive regex instead of adding it to the
    // Unicode-IGNORE_CASE negativeTurkish expression.
    private val sakinWarning = Regex("""(?<![\p{L}])(?:sakın|Sakın|SAKIN)(?![\p{L}])""")
    // Real negative verbal suffixes, e.g. yapma / gitme / gelmeyin.
    private val turkishNegatedVerb = Regex("""\b[\p{L}]{2,}(?:ma|me)(?:\b|[mydznslıiuüae][\p{L}]*)""", RegexOption.IGNORE_CASE)

    // 10.37: warnings, NOT automatic corrections. A warning alone cannot
    // prove an alternative is better, so these do not hide a live subtitle.
    // They do stop a lossy translation being marked as verified in memory.
    private val explicitEnglishProfanity = Regex("""\b(?:fuck(?:ing|ed|er)?|shit(?:ty)?|motherfucker|asshole|bitch|bastard)\b""", RegexOption.IGNORE_CASE)
    private val turkishProfanity = Regex("""(?:sik|siktir|sikti|sikik|amına|bok|piç|orospu|yavşak|kaltak|göt|ibne|lanet)""", RegexOption.IGNORE_CASE)
    private val onlineAgain = Regex("""\b(?:network|system|service|connection)\b.*\bback up\b""", RegexOption.IGNORE_CASE)
    private val backupMistranslated = Regex("""\byedeklen(?:di|miş|iyor|ecek)\b""", RegexOption.IGNORE_CASE)
    private val fainted = Regex("""\bpass(?:ed)?\s+(?:right\s+)?out\b""", RegexOption.IGNORE_CASE)
    private val exited = Regex("""\bdışarı\s+çık(?:tım|tı|tık|tılar|tın)\b""", RegexOption.IGNORE_CASE)
    private val injuredBack = Regex("""\bthrew\s+(?:his|her|my|their)\s+back\s+out\b""", RegexOption.IGNORE_CASE)
    private val thrownBackLiteral = Regex("""(?<![\p{L}])(?:sırtını|belini)\s+(?:işten\s+)?attı(?![\p{L}])""", RegexOption.IGNORE_CASE)

    fun check(source: String, translated: String): Problem? {
        val s = source.trim()
        val t = translated.trim()
        if (s.isEmpty() || t.isEmpty()) return Problem.UNTRANSLATED
        // Punctuation/case differences do not turn an echoed English line into Turkish.
        fun folded(value: String) = value.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
        if (folded(s) == folded(t)) return Problem.UNTRANSLATED
        // A model/template leaked a fictional variable, not a translation.
        // Valid speaker/sound labels such as [Soul] or [groans] are excluded.
        val leakedSlot = Regex("""\[(?:ad|isim|name|film/dizi|movie|series|character|speaker|unknown|placeholder)\]""", RegexOption.IGNORE_CASE)
        if (leakedSlot.containsMatchIn(t) && !leakedSlot.containsMatchIn(s))
            return Problem.INVENTED_PLACEHOLDER
        // An English slang pronoun is not a Turkish caption or a character name.
        if (SpokenEnglish204.hasUntranslatedSlang(t)) return Problem.UNTRANSLATED_SLANG
        when (MeaningSafety.risk(s, t)) {
            MeaningSafety.Risk.REVERSED_NEGATIVE_COMMAND -> return Problem.REVERSED_COMMAND
            MeaningSafety.Risk.LOST_PROHIBITION -> return Problem.LOST_PROHIBITION
            MeaningSafety.Risk.IMPERATIVE_AS_INFINITIVE -> return Problem.IMPERATIVE_AS_INFINITIVE
            null -> Unit
        }
        if (explicitEnglishSecondPerson.containsMatchIn(s) &&
            !englishFirstPerson.containsMatchIn(s) && turkishFirstPerson.containsMatchIn(t))
            return Problem.PERSON_REVERSAL
        if (!s.contains('?') && t.endsWith('?') && !s.endsWith("...") &&
            !s.contains('"') && !s.contains('“') && !s.contains(':')) return Problem.INVENTED_QUESTION
        // Only explicit English negation; avoid guessing from ambiguous contractions.
        // "Don't worry" can be faithfully rendered as "boş ver";
        // this is not a lost negation. The same applies to "never mind".
        val idiomaticNegative = Regex("\\b(?:don['’]?t worry|never mind)\\b", RegexOption.IGNORE_CASE)
        if (negativeEnglish.containsMatchIn(s) && s.length >= 18 &&
            !idiomaticNegative.containsMatchIn(s) &&
            !negativeTurkish.containsMatchIn(t) && !sakinWarning.containsMatchIn(t) &&
            !turkishNegatedVerb.containsMatchIn(t) &&
            !t.contains('?')) return Problem.LOST_NEGATION
        // A two-question subtitle must not silently lose its second question.
        // Warning, not hard rejection: one Turkish question can occasionally
        // paraphrase both. The live pipeline retries as separate clauses.
        if (s.count { it == '?' } >= 2 && t.count { it == '?' } < s.count { it == '?' })
            return Problem.MISSING_QUESTION
        if (englishResidue.findAll(t).count() >= 2) return Problem.UNTRANSLATED
        when (LiveSubtitleQuality10425.defect(s, t)) {
            LiveSubtitleQuality10425.Defect.ENGLISH_RESIDUE -> return Problem.ENGLISH_FRAGMENT
            LiveSubtitleQuality10425.Defect.MISSING_DETAIL -> return Problem.MISSING_DETAIL
            null -> Unit
        }
        if (onlineAgain.containsMatchIn(s) && backupMistranslated.containsMatchIn(t))
            return Problem.LITERAL_IDIOM
        if (fainted.containsMatchIn(s) && exited.containsMatchIn(t))
            return Problem.LITERAL_IDIOM
        if (injuredBack.containsMatchIn(s) && thrownBackLiteral.containsMatchIn(t))
            return Problem.LITERAL_IDIOM
        if (explicitEnglishProfanity.containsMatchIn(s) &&
            !turkishProfanity.containsMatchIn(t)) return Problem.LOST_PROFANITY
        return null
    }

    /** A profanity warning alone is insufficient evidence to discard good MT syntax. */
    fun acceptableStrong(problem: Problem?): Boolean =
        problem == null || problem == Problem.LOST_PROFANITY

    /** Only these high-confidence errors prevent displaying an output. */
    fun mustReject(problem: Problem?): Boolean = when (problem) {
        // A prohibition can be validly paraphrased in affirmative Turkish:
        // "Don't panic!" -> "Sakin ol!". Warn on missing negation but
        // never suppress a potentially correct paraphrase solely on that.
        Problem.REVERSED_COMMAND, Problem.IMPERATIVE_AS_INFINITIVE,
        Problem.INVENTED_PLACEHOLDER, Problem.PERSON_REVERSAL,
        Problem.UNTRANSLATED_SLANG, Problem.UNTRANSLATED, Problem.ENGLISH_FRAGMENT -> true
        else -> false
    }

    fun choose(source: String, primary: String, alternative: String?): String {
        val firstIssue = check(source, primary)
        // Missing a literal swear word is a *style warning*, not evidence that
        // a second machine's grammar/meaning is better. Preserve the fluent MT.
        if (firstIssue == null || firstIssue == Problem.LOST_PROFANITY ||
            alternative.isNullOrBlank()) return primary
        return if (check(source, alternative) == null) alternative else primary
    }

    fun mayRemember(source: String, translated: String): Boolean = check(source, translated) == null
}
