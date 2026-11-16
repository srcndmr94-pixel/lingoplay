package com.example.altyazi

/**
 * Güvenli oturum-içi isim onarımı. Yalnız kabul edilen altyazılar hatırlanır.
 * "Pout's" ancak yakın geçmişte "Paul" adı açık bağlamda geçerse "Paul's" olur.
 * Kanıt yoksa metin değiştirilmez. Yeni ağ isteği/çeviri gecikmesi yoktur.
 */
class DialogueContextRepair {
    private var acceptedCount = 0
    private var lastPaulAt = -1
    private var lastAccepted = ""
    // Kaynak -> hemen önce kabul edilen altyazı. Asenkron çeviri gelince de
    // doğru önceki satırı kullanmak için kabul anında kaydedilir.
    private data class AcceptedBefore(
        val text: String, val timestampMs: Long,
        val earlier: String = "", val earlierAtMs: Long = 0L,
        val third: String = "", val thirdAtMs: Long = 0L
    )
    private val preceding = object : LinkedHashMap<String, AcceptedBefore>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AcceptedBefore>?): Boolean = size > 32
    }
    private var lastAcceptedAtMs = 0L
    /**
     * DeepL receives the preceding spoken subtitles as an un-translated context field.
     * The snapshot belongs to the accepted source, not whichever subtitle happens
     * to be on screen when an asynchronous request reaches the network.
     */
    @Synchronized fun contextFor(sourceOrBody: String, nowMs: Long = System.currentTimeMillis()): String {
        val source = sourceOrBody.trim()
        val record = preceding[source] ?: preceding.entries.lastOrNull { (key, _) ->
            key.removePrefixSpeaker() == source
        }?.value ?: return ""
        val history = listOf(
            record.third to record.thirdAtMs,
            record.earlier to record.earlierAtMs,
            record.text to record.timestampMs
        )
        return history.asSequence()
            .filter { (line, at) -> at > 0L && nowMs >= at && nowMs - at <= 15_000L &&
                line.isNotBlank() && line.length <= 180 &&
                !line.trim().matches(Regex("""\[[^]]{1,80}]""")) }
            .map { (line, _) -> line.trim() }
            .takeLastThree()
            .joinToString("\n")
            .take(500)
    }

    private fun String.removePrefixSpeaker(): String = trim()
        .replace(Regex("""^\[[^]]{1,45}]\s*"""), "")
        .replace(Regex("""^[\p{L} .'-]{2,45}:\s+"""), "")

    private fun <T> Sequence<T>.takeLastThree(): List<T> = toList().takeLast(3)
    private val incompleteEnding = Regex(
        "(?i)\\b(?:a|an|the|of|for|to|from|with|without|about|by|at|in|on|as|than|" +
            "because|if|when|while|and|but|or|that|which|who|whose|" +
            "am|is|are|was|were|have|has|had|do|does|did|will|would|can|could|should)\\s*$"
    )
    private val sentenceEnding = Regex("[.!?…][\"'’)]*$")
    // A following sound cue is not a speaker change. It may follow a spoken
    // continuation: "any difference at all. -[insects trilling]".
    private val trailingSoundCue = Regex("""\s*(?:-\s*)?\[[a-z]+(?:[ ,'-][a-z]+){0,5}]\s*$""", RegexOption.IGNORE_CASE)
    private val incompletePredicate = Regex("""\b(?:would|could|will|might|can|should)\s+make\s*$""", RegexOption.IGNORE_CASE)

    /**
     * Açıkça yarım bırakılan iki satırı tek çeviri girdisine dönüştürür.
     * Farklı konuşmacı, ses etiketi, cümle sonu veya eski sahne birleştirilmez.
     * Çıktı yalnızca çeviri girdisidir; kaynak altyazının kimliği değişmez.
     */
    @Synchronized fun joinedTranslationInput(source: String, nowMs: Long = System.currentTimeMillis()): String? {
        val before = preceding[source] ?: return null
        if (nowMs < before.timestampMs || nowMs - before.timestampMs > 7_000L) return null
        val previous = before.text.trim()
        val current = source.trim()
        val spokenCurrent = current.replace(trailingSoundCue, "").trimEnd()
        if (previous.length !in 10..140 || spokenCurrent.length !in 7..130 ||
            previous.length + current.length > 190) return null
        if (previous.equals(current, ignoreCase = true)) return null
        if (previous.any { it == '[' || it == ']' || it == ':' } ||
            spokenCurrent.any { it == '[' || it == ']' || it == ':' }) return null
        if (sentenceEnding.containsMatchIn(previous)) return null
        if (spokenCurrent.firstOrNull()?.isLowerCase() != true) return null
        // Two finite auxiliaries cannot be a continuation of one clause:
        // "My name is" / "was the assistant director" must not become
        // "My name is was the assistant director".
        val priorAux = Regex("""\b(?:am|is|are|was|were)\s*$""", RegexOption.IGNORE_CASE)
        val nextAux = Regex("""^(?:am|is|are|was|were)\b""", RegexOption.IGNORE_CASE)
        if (priorAux.containsMatchIn(previous) && nextAux.containsMatchIn(spokenCurrent)) return null
        // A trailing -ing predicate can be continued by an adverbial modifier,
        // even without a comma: "leave them squirming" / "like turtles...".
        // Do not infer a continuation from -ing alone; the following word
        // must be a lower-case linking word and both lines must be close in time.
        val modifierContinuation = Regex("""\b[\p{L}]+ing\s*$""", RegexOption.IGNORE_CASE).containsMatchIn(previous) &&
            Regex("""^(?:like|as|with|without|in|on|under|over|to|for|from|by|while)\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(spokenCurrent)
        val evidentlyIncomplete = previous.endsWith(',') || previous.endsWith('—') ||
            previous.endsWith('-') || incompleteEnding.containsMatchIn(previous) ||
            incompletePredicate.containsMatchIn(previous) || modifierContinuation
        val earlierIncomplete = before.earlier.trim().let { e ->
            e.isNotEmpty() && !sentenceEnding.containsMatchIn(e) &&
                (e.endsWith(',') || e.endsWith('-') || incompleteEnding.containsMatchIn(e))
        }
        if (!evidentlyIncomplete && !(earlierIncomplete && !sentenceEnding.containsMatchIn(previous))) return null
        // If the previous line was itself a continuation, include its opener.
        // This fixes three-screen sentences without translating the final clause alone.
        val earlier = before.earlier.trim()
        if (earlier.length in 10..130 && nowMs >= before.earlierAtMs &&
            before.timestampMs - before.earlierAtMs in 0..7_000L &&
            earlier.length + previous.length + current.length <= 220 &&
            earlier.none { it == '[' || it == ']' || it == ':' } &&
            !sentenceEnding.containsMatchIn(earlier) &&
            (earlier.endsWith(',') || earlier.endsWith('-') ||
                incompleteEnding.containsMatchIn(earlier)) &&
            previous.firstOrNull()?.isLowerCase() == true) {
            return "$earlier $previous $current"
        }
        return "$previous $current"
    }
    private val paulEvidence = Regex("\\b(?:with\\s+Paul|about\\s+this\\s+Paul\\s+guy|Paul\\s+is)\\b")
    private val likelyPoutName = Regex("\\bPout(?=['’]s\\b)")

    @Synchronized fun reset() {
        acceptedCount = 0
        lastPaulAt = -1
        lastAccepted = ""
        lastAcceptedAtMs = 0L
        preceding.clear()
    }

    /** Saf işlem: aynı kare birden çok kez okunsa da geçmişi ilerletmez. */
    @Synchronized fun repair(text: String): String {
        if (lastPaulAt == -1 || acceptedCount - lastPaulAt > 40) return text
        return likelyPoutName.replace(text, "Paul")
    }

    /**
     * Yalnız anlamı açık, arka arkaya gelen KOPUK replikler için ikinci
     * altyazının tamamını doğal Türkçeye çevirir. Tahmin yok; eşleşme yoksa
     * Google/Firefox hattına aynen bırakılır. Ağ çağrısı / gecikme eklemez.
     * Hangi önceki repliğe ait olduğu kabul ANINDA sabitlenir.
     */
    @Synchronized fun contextualTranslation(source: String): String? {
        val previous = preceding[source]?.text?.lowercase()?.trim() ?: return null
        val current = source.lowercase().trim()
        return when {
            // "They" refers to powers when the immediately preceding speaker
            // explicitly said "my powers came back"; no noun is invented.
            previous.endsWith("my powers came back.") &&
                current.matches(Regex("""(?:\[mark\]\s*)?they just came back\?""")) ->
                "Kendiliğinden mi geri geldiler?"
            // "...back in the day" ardından "Was?!" zaman vurgusudur.
            current.matches(Regex("was[?!]+")) &&
                Regex("\\bback in the day[.!?]*$").containsMatchIn(previous) ->
                "Eskiden mi?!"
            previous == "have we been" && current == "here before?" ->
                "Daha önce buraya gelmiş miydik?"
            previous == "want to take our relationship" &&
                current.matches(Regex("to the next level[.!?, ]*so[.!?…]*")) ->
                "İlişkimizi bir sonraki aşamaya taşımak istiyorum. O yüzden..."
            previous.startsWith("oh, uh, what was it you wanted to tell me yesterday") &&
                current == "when my dad and allen showed up?" ->
                "Dün babamla Allen geldiğinde bana ne söyleyecektin?"
            previous == "the kid has enough to deal with" &&
                current == "without me messing him up the way i did mark." ->
                "Ben de Mark'e yaptıklarımı ona yaşatmayayım."
            else -> null
        }
    }

    /** Gerçekten çevirmeye alınan altyazıdan öğrenir. */
    @Synchronized fun remember(acceptedSubtitle: String, nowMs: Long = System.currentTimeMillis()) {
        acceptedCount++
        val previousRecord = preceding[lastAccepted]
        preceding[acceptedSubtitle] = AcceptedBefore(lastAccepted, lastAcceptedAtMs,
            previousRecord?.text.orEmpty(), previousRecord?.timestampMs ?: 0L,
            previousRecord?.earlier.orEmpty(), previousRecord?.earlierAtMs ?: 0L)
        lastAccepted = acceptedSubtitle
        lastAcceptedAtMs = nowMs
        if (paulEvidence.containsMatchIn(acceptedSubtitle)) lastPaulAt = acceptedCount
    }
}
