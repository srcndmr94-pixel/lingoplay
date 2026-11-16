package com.example.altyazi

/**
 * OCR kaynaklı, yüksek güvenli metin düzeltmeleri ve ilk kare güven denetimi.
 * Dil modeli değildir; cümleyi yeniden yazmaz, özel isimleri tahmin etmez.
 * GameTranslator'ın kararlı okuma yaklaşımının Android'e uyarlanmış küçük kısmı.
 */
object SubtitlePreparation {
    private val repairs = listOf(
        // Örnekleri gerçek test loglarından alındı; dar bağlam dışına uygulanmaz.
        // 10.42.3: Only observed split OCR tokens; preserve genuine English words.
        Regex("\\bnerv\\s+ous\\b", RegexOption.IGNORE_CASE) to "nervous",
        Regex("\\bmake\\s+a\\s+ifference\\b", RegexOption.IGNORE_CASE) to "make a difference",
        Regex("(?<=\\[)insects\\s+triling(?=])", RegexOption.IGNORE_CASE) to "insects trilling",
        Regex("\\bmost hand some\\b", RegexOption.IGNORE_CASE) to "most handsome",
        Regex("\\bmnonths\\b", RegexOption.IGNORE_CASE) to "months",
        Regex("\\bmomn\\b", RegexOption.IGNORE_CASE) to "mom",
        Regex("\\bhave never been mnore\\b", RegexOption.IGNORE_CASE) to "have never been more",
        Regex("\\bit is timne we were at war\\b", RegexOption.IGNORE_CASE) to "it is time we were at war",
        Regex("^H don't know\\b") to "I don't know",
        Regex("\\btw\\s+O-week\\b", RegexOption.IGNORE_CASE) to "two-week",
        Regex("\\bwara million\\b", RegexOption.IGNORE_CASE) to "war a million",
        Regex("\\bthere'sa\\b", RegexOption.IGNORE_CASE) to "there's a",
        Regex("\\bain['’]?t\\b", RegexOption.IGNORE_CASE) to "ain't",
        // Yalnız ayırt edici bağlamda OCR harf/boşluk kayıpları.
        Regex("\\bwhat,\\s*didl\\s+leave\\b", RegexOption.IGNORE_CASE) to "What, did I leave",
        Regex("^wet,\\s+that sounds like a challenge\\b", RegexOption.IGNORE_CASE) to "Well, that sounds like a challenge",
        Regex("\\b(suit)\\s+Al(?=])", RegexOption.IGNORE_CASE) to "suit AI",
        // 10.31: only high-confidence OCR repairs supported by actual logs.
        Regex("\\bmnom\\b", RegexOption.IGNORE_CASE) to "mom",
        Regex("\\bwhen you were born, your mnom\\b", RegexOption.IGNORE_CASE) to "When you were born, your mom",
        Regex("\\b(?<=\\[)all gruntingl$", RegexOption.IGNORE_CASE) to "all grunting]",
        Regex("\\b(?<=\\[)oliver gruntingl$", RegexOption.IGNORE_CASE) to "Oliver grunting]",
        Regex("\\b(?<=\\[)alien gruntingl$", RegexOption.IGNORE_CASE) to "Alien grunting]",
        Regex("\\bit always find my way back to you\\b", RegexOption.IGNORE_CASE) to "I always find my way back to you",
        Regex("\\byou take the left,\\s*t[iIl1]l take the right\\b", RegexOption.IGNORE_CASE) to "You take the left, I'll take the right",
        Regex("\\bshe's stronger than she look\\s+s\\b", RegexOption.IGNORE_CASE) to "She's stronger than she looks",
        // 10.34 — kaynak tarafından doğrulanabilen başlangıç OCR bozulmaları.
        // "lever tell you about" -> "Ever tell you about"; diğer lever metinleri korunur.
        Regex("^lever(?=\\s+tell\\s+you\\s+about\\b)", RegexOption.IGNORE_CASE) to "Ever",
        // "Wet, Ill walk" -> "Well, I'll walk"; belirsiz kelimeleri uydurma.
        Regex("^wet,\\s+[Il1]{2,3}(?=\\s+(?:walk|take|go|call|see|show|meet|drive|bring)\\b)", RegexOption.IGNORE_CASE) to "Well, I'll"
    )

    // A clipped CC sound label often loses its closing bracket. Repair only
    // sound-like -ing words ending with OCR's extra 'l' (']' -> 'l'). No names
    // or ordinary dialogue words are modified.
    private val clippedSoundEnd = Regex("""^(\[[a-z]+(?:[ -][a-z]+){0,4}ing)l$""", RegexOption.IGNORE_CASE)
    // OCR may lose the bracket between a sound cue and the first spoken word:
    // "(sighs Stick to the plan." is two segments, not one sentence.
    private val clippedSoundStart = Regex(
        """^[\[(]((?i:sighs|gasps|grunts|groans|laughs|chuckles|cries|coughs|exhales|inhales|yells|screams))\s+(?=[A-Z])"""
    )

    // The OCR sometimes adds an I/l immediately INSIDE a cue: [Igasping].
    // Strip it only when a known sound verb follows; never touch speech like [I wonder].
    private val ghostIInCue = Regex("""\[[Il|](?=(?:gasp(?:ing|s)?|grunt(?:ing|s)?|groan(?:ing|s)?|sigh(?:ing|s)?|scream(?:ing|s)?|pant(?:ing|s)?|laugh(?:s|ing)|chuckl(?:es|ing)|yell(?:s|ing))\])""", RegexOption.IGNORE_CASE)
    // A missing opening bracket before NAME + action: "Jane screams]".
    // The trailing ']' and an explicit cue verb are required to avoid rewriting dialogue.
    private val missingCueOpen = Regex(
        """(?<![\p{L}\[])(\p{Lu}[\p{Ll}]{1,24}(?:\s+\p{Lu}[\p{Ll}]{1,24})?)\s+(screams|screaming|grunts|groans|gasping|panting|crying|chuckling|laughing|yells|sighs)]"""
    )
    // "[Jane] pantingl" is one damaged sound cue, not spoken English.
    private val separatedNameAction = Regex(
        """^\[(\p{Lu}[\p{Ll}]{1,24})]\s+(panting|gasping|grunting|groaning|straining|screaming|crying|chuckling|laughing|sighing)[lI]$"""
    )

    // A missing ']' is often read as lower-case l at the end of a speaker
    // name: "(Kreggl The betrayer!" -> "[Kregg] The betrayer!".
    // Require an opening bracket, a plausible long proper name and an
    // uppercase first spoken word; never edit ordinary unbracketed dialogue.
    private val ghostSpeakerClose = Regex("""^[\[(]([\p{Lu}][\p{Ll}]{4,23})[lI1]\s+(?=[\p{Lu}])""")

    // OCR sometimes inserts an extra ']' before the adverb and then closes
    // the same sound cue again: "[grunts] softly]" -> "[grunts softly]".
    // Do not touch dialogue or cues followed by a separate spoken sentence.
    private val brokenCueAdverb = Regex("""^\[([a-z]+)]\s+(softly|loudly|quietly|heavily|weakly|faintly)]$""", RegexOption.IGNORE_CASE)
    // Isolated stage directions sometimes end with OCR's 'l' for ']'.
    private val isolatedSoundTail = Regex("""^\[([a-z]+(?:\s+[a-z]+){0,4}(?:grunts|groans|panting|straining|screaming|crying|gasping|groaning|grunting))l$""", RegexOption.IGNORE_CASE)
    // 10.42.5: Confirmed character speaker labels can arrive lowercase, or with
    // the OCR's missing second 'l'. Fix ONLY an initial, bracketed label with
    // following dialogue; never capitalize stage directions like [groans].
    private val damagedWilliamLabel = Regex("""^\[(?:wiliam|william)](?=\s+\S)""", RegexOption.IGNORE_CASE)

    fun repair(text: String): String {
        var current = damagedWilliamLabel.replace(text.trim(), "[William]")
        current = brokenCueAdverb.replace(current) { "[${it.groupValues[1]} ${it.groupValues[2]}]" }
        current = isolatedSoundTail.replace(current) { "[${it.groupValues[1]}]" }
        current = ghostSpeakerClose.replace(current) { "[${it.groupValues[1]}] " }
        // Short names escaped the 5+ character bracket repair: "[Zoel You helped".
        current = Regex("""\[Zoel\s+(?=[A-Z][a-z])""").replace(current, "[Zoe] ")
        // Confirmed same-series OCR transposition, not a generic name rewriter.
        current = Regex("""\bVitrumites\b""", RegexOption.IGNORE_CASE).replace(current, "Viltrumites")
        // Only here: "Here's break fast from a tube" is clearly breakfast.
        current = Regex("""\b(here['’]s)\s+break\s+fast\b""", RegexOption.IGNORE_CASE).replace(current) {
            it.groupValues[1] + " breakfast"
        }
        current = ghostIInCue.replace(current, "[")
        current = missingCueOpen.replace(current) { "[${it.groupValues[1]} ${it.groupValues[2]}]" }
        current = separatedNameAction.replace(current) { "[${it.groupValues[1]} ${it.groupValues[2]}]" }
        current = clippedSoundEnd.replace(current) { "${it.groupValues[1]}]" }
        current = clippedSoundStart.replace(current) { "[${it.groupValues[1].lowercase()}] " }
        for ((pattern, replacement) in repairs) {
            current = pattern.replace(current) { match ->
                if (match.value.firstOrNull()?.isUpperCase() == true) {
                    replacement.replaceFirstChar { it.uppercase() }
                } else replacement
            }
        }
        return GameplayLogRepair299.repairOcr(current)
    }

    private val safeShortWords = setOf(
        "a", "i", "o", "an", "am", "as", "at", "be", "by", "do", "go", "ha",
        "he", "hi", "if", "in", "is", "it", "me", "mr", "ms", "my", "no",
        "of", "oh", "ok", "on", "or", "so", "to", "tv", "uh", "um", "up",
        "us", "we", "ya", "ye", "ai", "dr", "st", "id", "vs"
    )
    private val words = Regex("[\\p{L}]+(?:['’][\\p{L}]+)?")
    private val suspiciousChars = Regex("[|#@%]|[\\p{L}]\\d[\\p{L}]|\\d[\\p{L}]\\d")

    /** Şüpheli OCR: ilk karede acele etmeyip ikinci, aynı okumayı bekle. */
    fun confidentFirstRead(text: String): Boolean {
        if (text.isBlank() || GameplayLogRepair299.waitForCleanReading(text)) return false
        // Sahne/ses etiketlerini gereksiz yere yavaşlatma.
        if (text.trim().matches(Regex("^\\[[^]\\n]+]$"))) return true
        if (suspiciousChars.containsMatchIn(text)) return false
        // A visibly clipped word following a determiner cannot be trusted on
        // the first OCR frame; give the next capture a chance to complete it.
        if (Regex("""\b(?:your|the|only)\s+(?:opt|noth)\s*$""", RegexOption.IGNORE_CASE).containsMatchIn(text)) return false
        val tokens = words.findAll(text).map { it.value }.toList()
        if (tokens.isEmpty()) return false
        return tokens.none { word ->
            word.length <= 2 && word.lowercase() !in safeShortWords
        }
    }
}

/**
 * Yalnız Google yolunda güvenli anlamsal normalleştirme.
 * Cümle sonundaki ", too" çoğu zaman "ben de" anlamındadır;
 * "too much/too late" hiçbir zaman değiştirilmez.
 */
object GoogleSourcePreparation {
    private val finalToo = Regex(",\\s*too([.!?…]*)$", RegexOption.IGNORE_CASE)

    fun prepare(text: String): String {
        val grammatical = SpokenEnglish204.forEngine(text)
        return finalToo.replace(grammatical) { match ->
            " as well" + match.groupValues[1]
        }
    }
}
