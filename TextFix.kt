package com.example.subtitles

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object TextFix {

    private const val NO_CORRECTION = "#NONE#"

    private const val MAX_CACHE_SIZE = 10_000
    private const val MIN_SPLIT_WORD_LENGTH = 5
    private const val MIN_SPLIT_PART_LENGTH = 2

    private val WORD_REGEX =
        Regex("[A-Za-z]+(?:'[A-Za-z]+)?")

    private val TOKEN_REGEX =
        Regex("[A-Za-z]+(?:'[A-Za-z]+)?|[^A-Za-z]+")

    private val OCR_O_REGEX =
        Regex("[ỏóôö]")

    private val OCR_EMOJI_REGEX =
        Regex("[:;][Dd)]")

    private val PROTECTED_TERMS = setOf(
        "dao",
        "qi",
        "wulin",
        "murim",
        "dantian",
        "meridian",
        "transcension",
        "templar",
        "assassin",
        "brotherhood",
        "creed"
    )

    private val RAW_OCR_RULES = listOf(
        "oftraining" to "of training",
        "intothe" to "into the",
        "hatfar" to "half a",
        "litle" to "little",
        "satistied" to "satisfied",
        "trom" to "from",
        "inever" to "never",
        "halfdad" to "half dead",
        "hal-doad" to "half dead",
        "hal doad" to "half dead",
        "hal dead" to "half dead",
        "half doad" to "half dead",
        "ha dead" to "half dead",
        "ratn" to "rain",
        "mnd" to "mud",
        "tie mud" to "the mud"
    )

    private data class OcrRule(
        val pattern: Regex,
        val replacement: String
    )

    /*
     * Kurallar bir kez derlenir. Her onar() çağrısında yeniden
     * Regex oluşturulmaz.
     */
    private val OCR_RULES: List<OcrRule> =
        RAW_OCR_RULES
            .sortedByDescending { (source, _) -> source.length }
            .map { (source, replacement) ->
                val expression = if (
                    source.any(Char::isWhitespace) ||
                    '-' in source
                ) {
                    Regex.escape(source)
                } else {
                    "\\b${Regex.escape(source)}\\b"
                }

                OcrRule(
                    pattern = Regex(
                        expression,
                        RegexOption.IGNORE_CASE
                    ),
                    replacement = replacement
                )
            }

    private val WORDS: Set<String> =
        loadWords()

    /*
     * Yakın kelime aramasında yalnızca uygun uzunluktaki
     * adayların incelenmesini sağlar.
     */
    private val WORDS_BY_LENGTH: Map<Int, List<String>> =
        WORDS
            .groupBy { it.length }
            .mapValues { (_, words) -> words.sorted() }

    private val correctionCache =
        ConcurrentHashMap<String, String>()

    private val splitCache =
        ConcurrentHashMap<String, Pair<String, String>>()

    private val NO_SPLIT =
        "#NONE#" to "#NONE#"

    private fun loadWords(): Set<String> {
        val common = setOf(
            "a", "an", "the", "of", "to", "in", "for", "on", "with",
            "at", "by", "from", "and", "or", "but", "if", "as", "is",
            "are", "was", "were", "be", "been", "being", "have", "has",
            "had", "do", "does", "did", "will", "would", "can", "could",
            "should", "may", "might", "must", "shall",

            "this", "that", "these", "those", "i", "you", "he", "she",
            "it", "we", "they", "me", "him", "her", "us", "them", "my",
            "your", "his", "its", "our", "their", "mine", "yours",
            "hers", "ours", "theirs",

            "what", "which", "who", "whom", "whose", "where", "when",
            "why", "how", "all", "any", "both", "each", "few", "more",
            "most", "other", "some", "such", "no", "nor", "not", "only",
            "own", "same", "so", "than", "too", "very",

            "just", "also", "now", "here", "there", "then", "once",
            "again", "even", "still", "one", "two", "three", "four",
            "five", "first", "second", "last", "next", "new", "old",

            "good", "bad", "big", "small", "long", "short", "high",
            "low", "great", "little", "much", "many", "man", "woman",
            "child", "person", "people", "time", "day", "year", "way",
            "life", "world", "hand", "part", "place", "case", "week",
            "company", "system", "program", "question", "work",
            "government", "number", "night", "point", "home", "water",
            "room", "mother", "area", "money", "story", "fact", "month",
            "lot", "right", "study", "book", "eye", "job", "word",
            "business", "issue", "side", "kind", "head", "house",
            "service", "friend", "father", "power", "hour", "game",
            "line", "end", "member", "law", "car", "city", "community",
            "name", "president", "team", "minute", "idea", "kid", "body",
            "information", "back", "parent", "face", "others", "level",
            "office", "door", "health", "art", "war", "history", "party",
            "result", "change", "morning", "reason", "research", "girl",
            "guy", "moment", "air", "teacher", "force", "education",

            "training", "into", "half", "dead", "rain", "mud",
            "satisfied", "never", "said", "use", "up", "about", "out",
            "make", "like", "look", "write", "go", "see", "get", "come",
            "made", "call", "find", "down", "oil"
        )

        return (common + PROTECTED_TERMS)
            .asSequence()
            .map(::normalizeWord)
            .toSet()
    }

    private fun normalizeWord(word: String): String =
        word.lowercase(Locale.ROOT)

    private fun known(word: String): Boolean =
        normalizeWord(word) in WORDS

    private fun isProtectedTerm(word: String): Boolean =
        normalizeWord(word) in PROTECTED_TERMS

    private fun preserveCase(
        original: String,
        replacement: String
    ): String {
        val letters = original.filter(Char::isLetter)

        return when {
            letters.isNotEmpty() &&
                letters.all(Char::isUpperCase) -> {
                replacement.uppercase(Locale.ROOT)
            }

            original.firstOrNull()?.isUpperCase() == true -> {
                replacement.replaceFirstChar { character ->
                    character.titlecase(Locale.ROOT)
                }
            }

            else -> replacement
        }
    }

    private fun applyOcrRules(text: String): String {
        var result = text

        for (rule in OCR_RULES) {
            result = rule.pattern.replace(result) { match ->
                preserveCase(
                    original = match.value,
                    replacement = rule.replacement
                )
            }
        }

        return result
            .replace(OCR_O_REGEX, "o")
            .replace(OCR_EMOJI_REGEX, "")
    }

    /*
     * Belirtilen üst sınırdan daha uzak kelimelerde hesaplamayı
     * mümkün olduğunca erken durdurur.
     */
    private fun levenshtein(
        first: String,
        second: String,
        maxDistance: Int
    ): Int {
        if (first == second) {
            return 0
        }

        if (abs(first.length - second.length) > maxDistance) {
            return maxDistance + 1
        }

        if (first.isEmpty()) {
            return if (second.length <= maxDistance) {
                second.length
            } else {
                maxDistance + 1
            }
        }

        if (second.isEmpty()) {
            return if (first.length <= maxDistance) {
                first.length
            } else {
                maxDistance + 1
            }
        }

        /*
         * Kısa kelimenin sütun olarak kullanılması daha az bellek
         * tüketir.
         */
        val rows: String
        val columns: String

        if (first.length >= second.length) {
            rows = first
            columns = second
        } else {
            rows = second
            columns = first
        }

        var previous =
            IntArray(columns.length + 1) { index -> index }

        var current =
            IntArray(columns.length + 1)

        for (rowIndex in rows.indices) {
            current[0] = rowIndex + 1
            var rowMinimum = current[0]

            for (columnIndex in columns.indices) {
                val substitutionCost =
                    if (rows[rowIndex] == columns[columnIndex]) {
                        0
                    } else {
                        1
                    }

                current[columnIndex + 1] = minOf(
                    current[columnIndex] + 1,
                    previous[columnIndex + 1] + 1,
                    previous[columnIndex] + substitutionCost
                )

                rowMinimum = minOf(
                    rowMinimum,
                    current[columnIndex + 1]
                )
            }

            if (rowMinimum > maxDistance) {
                return maxDistance + 1
            }

            val temporary = previous
            previous = current
            current = temporary
        }

        return previous.last()
    }

    /*
     * Kısa kelimelerde yanlış düzeltme ihtimali daha yüksektir.
     */
    private fun maxCorrectionDistance(wordLength: Int): Int {
        return when {
            wordLength <= 3 -> 0
            wordLength <= 5 -> 1
            else -> 2
        }
    }

    private fun nearestWord(word: String): String? {
        val normalized = normalizeWord(word)

        if (known(normalized)) {
            return normalized
        }

        val maxDistance =
            maxCorrectionDistance(normalized.length)

        if (maxDistance == 0) {
            return null
        }

        var bestWord: String? = null
        var bestDistance = maxDistance + 1
        var equallyGoodCandidates = 0

        val minimumLength =
            maxOf(1, normalized.length - maxDistance)

        val maximumLength =
            normalized.length + maxDistance

        for (length in minimumLength..maximumLength) {
            val candidates =
                WORDS_BY_LENGTH[length].orEmpty()

            for (candidate in candidates) {
                val distance = levenshtein(
                    first = normalized,
                    second = candidate,
                    maxDistance = minOf(
                        maxDistance,
                        bestDistance
                    )
                )

                when {
                    distance > maxDistance -> {
                        // Uygun aday değil.
                    }

                    distance < bestDistance -> {
                        bestWord = candidate
                        bestDistance = distance
                        equallyGoodCandidates = 1
                    }

                    distance == bestDistance &&
                        candidate != bestWord -> {
                        equallyGoodCandidates++
                    }
                }
            }
        }

        /*
         * Aynı uzaklıkta birden fazla kelime varsa rastgele bir
         * seçim yapmak yerine orijinal kelime korunur.
         */
        return if (equallyGoodCandidates == 1) {
            bestWord
        } else {
            null
        }
    }

    private fun cacheCorrection(
        key: String,
        correction: String?
    ) {
        /*
         * Önbellek dolduğunda tüm kayıtları silmek ani performans
         * düşüşü oluşturabilir. Bunun yerine yeni kayıt eklenmez.
         */
        if (
            correctionCache.size < MAX_CACHE_SIZE ||
            correctionCache.containsKey(key)
        ) {
            correctionCache.putIfAbsent(
                key,
                correction ?: NO_CORRECTION
            )
        }
    }

    private fun nearestWordCached(word: String): String? {
        val key = normalizeWord(word)

        correctionCache[key]?.let { cached ->
            return if (cached == NO_CORRECTION) {
                null
            } else {
                cached
            }
        }

        val correction = nearestWord(key)

        cacheCorrection(
            key = key,
            correction = correction
        )

        return correction
    }

    private fun spellingFix(text: String): String {
        return WORD_REGEX.replace(text) { match ->
            val original = match.value
            val normalized = normalizeWord(original)

            when {
                isProtectedTerm(original) -> {
                    original
                }

                /*
                 * Küçük sözlükle apostroflu kelimeleri düzeltmek
                 * güvenilir değildir.
                 */
                '\'' in original -> {
                    original
                }

                /*
                 * Üç veya daha az karakterli kelimelerde düzeltme
                 * yapılmaz.
                 */
                normalized.length <= 3 -> {
                    original
                }

                else -> {
                    val correction =
                        nearestWordCached(normalized)

                    if (correction == null) {
                        original
                    } else {
                        preserveCase(
                            original = original,
                            replacement = correction
                        )
                    }
                }
            }
        }
    }

    private fun cacheSplit(
        key: String,
        split: Pair<String, String>?
    ) {
        if (
            splitCache.size < MAX_CACHE_SIZE ||
            splitCache.containsKey(key)
        ) {
            splitCache.putIfAbsent(
                key,
                split ?: NO_SPLIT
            )
        }
    }

    private fun splitWord(
        word: String
    ): Pair<String, String>? {
        val key = normalizeWord(word)

        splitCache[key]?.let { cached ->
            return if (cached == NO_SPLIT) {
                null
            } else {
                cached
            }
        }

        if (
            key.length < MIN_SPLIT_WORD_LENGTH ||
            known(key) ||
            isProtectedTerm(key) ||
            '\'' in key
        ) {
            cacheSplit(key, null)
            return null
        }

        var bestIndex = -1
        var bestScore = Int.MIN_VALUE
        var equallyGoodSplits = 0

        val finalIndex =
            key.length - MIN_SPLIT_PART_LENGTH

        for (index in MIN_SPLIT_PART_LENGTH..finalIndex) {
            val left = key.substring(0, index)
            val right = key.substring(index)

            if (!known(left) || !known(right)) {
                continue
            }

            val shortestPart =
                minOf(left.length, right.length)

            val balancePenalty =
                abs(left.length - right.length)

            /*
             * Uzun ve dengeli parçalara öncelik verir.
             */
            val score =
                shortestPart * 10 - balancePenalty

            when {
                score > bestScore -> {
                    bestScore = score
                    bestIndex = index
                    equallyGoodSplits = 1
                }

                score == bestScore -> {
                    equallyGoodSplits++
                }
            }
        }

        /*
         * Birden fazla eşit sonuç varsa bölme uygulanmaz.
         */
        val result =
            if (bestIndex < 0 || equallyGoodSplits != 1) {
                null
            } else {
                word.substring(0, bestIndex) to
                    word.substring(bestIndex)
            }

        cacheSplit(
            key = key,
            split = result
        )

        return result
    }

    private fun splitWords(text: String): String {
        return WORD_REGEX.replace(text) { match ->
            val original = match.value
            val split = splitWord(original)

            if (split == null) {
                original
            } else {
                "${split.first} ${split.second}"
            }
        }
    }

    private fun normalizeCapitals(text: String): String {
        val result = StringBuilder(text.length)
        var sentenceStart = true

        for (match in TOKEN_REGEX.findAll(text)) {
            var token = match.value

            if (token.firstOrNull()?.isLetter() == true) {
                val normalized =
                    normalizeWord(token)

                token = when {
                    /*
                     * İngilizcedeki "I" zamiri her zaman büyük yazılır.
                     */
                    normalized == "i" -> {
                        "I"
                    }

                    /*
                     * Korunan terimlerin mevcut yazımı değiştirilmez.
                     */
                    isProtectedTerm(normalized) -> {
                        token
                    }

                    /*
                     * Cümle başındaki bilinen kelime büyük harfle
                     * başlatılır.
                     */
                    sentenceStart && known(normalized) -> {
                        normalized.replaceFirstChar { character ->
                            character.titlecase(Locale.ROOT)
                        }
                    }

                    /*
                     * Cümle içindeki bilinen genel kelimelerde gereksiz
                     * büyük harf kaldırılır.
                     */
                    !sentenceStart && known(normalized) -> {
                        normalized
                    }

                    /*
                     * Sözlükte bulunmayan özel adların yazımı korunur.
                     */
                    else -> {
                        token
                    }
                }

                sentenceStart = false
            } else if (token.any(::isSentenceTerminator)) {
                sentenceStart = true
            }

            result.append(token)
        }

        return result.toString()
    }

    private fun isSentenceTerminator(
        character: Char
    ): Boolean {
        return character == '.' ||
            character == '?' ||
            character == '!'
    }

    /**
     * OCR kaynaklı İngilizce altyazı hatalarını düzeltir.
     *
     * İşlem sırası:
     * 1. Bilinen OCR hatalarını düzeltir.
     * 2. Birleşmiş kelimeleri ayırır.
     * 3. Yazım hatalarını düzeltir.
     * 4. Büyük-küçük harfleri normalleştirir.
     */
    fun onar(text: String): String {
        if (text.isBlank()) {
            return text
        }

        return text
            .let(::applyOcrRules)
            .let(::splitWords)
            .let(::spellingFix)
            .let(::normalizeCapitals)
    }
}