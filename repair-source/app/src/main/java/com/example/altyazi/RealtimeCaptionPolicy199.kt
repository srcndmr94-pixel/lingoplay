package com.example.altyazi

/** Gerçek oyuna/filme özgü içerik taşımayan canlı gecikme politikası. */
object RealtimeCaptionPolicy199 {
    const val HEDGE_AFTER_MS = 420L
    const val LOCAL_TIMEOUT_MS = 550L

    fun needsLocalHedge(balanced: Boolean, strongAvailable: Boolean, source: String): Boolean =
        balanced && strongAvailable && source.any(Char::isLetter) &&
            !Regex("""^\[[^]]{1,80}]$""").matches(source.trim())
}

/** Her replikte ilk güvenilir sonucu yayınla; geç sonucu ekranı değiştirmeden sakla. */
class StableCaptionGate199 {
    enum class Decision { FIRST, UPGRADE, LOCKED }

    private var publishedId = Long.MIN_VALUE
    private var firstWasLocalBackup = false
    private var firstIssue: TranslationQualityGate.Problem? = null
    private var firstAtMs = 0L
    private var upgraded = false

    /** Compatibility with the frozen 199 first-result policy/tests. */
    @Synchronized fun firstValidated(id: Long, isCurrent: (Long) -> Boolean): Boolean {
        if (id <= 0L || !isCurrent(id) || publishedId == id) return false
        publishedId = id
        firstWasLocalBackup = false
        firstIssue = null
        upgraded = false
        return true
    }

    /**
     * A later result is normally locked, exactly as in 199/201/202.
     * Only a local preview with an independently detectable defect may be
     * replaced once by a clean result for the SAME still-visible run.
     * An unknown/merely different translation is never considered better.
     */
    @Synchronized fun decide203(
        id: Long,
        isCurrent: (Long) -> Boolean,
        nowMs: Long,
        isLocalBackup: Boolean,
        currentCaptionStillVisible: Boolean,
        issue: TranslationQualityGate.Problem?,
    ): Decision {
        if (id <= 0L || !isCurrent(id)) return Decision.LOCKED
        if (publishedId != id) {
            publishedId = id
            firstWasLocalBackup = isLocalBackup
            firstIssue = issue
            firstAtMs = nowMs
            upgraded = false
            return Decision.FIRST
        }
        if (!firstWasLocalBackup || upgraded || !currentCaptionStillVisible ||
            isLocalBackup || nowMs < firstAtMs || nowMs - firstAtMs > 1200L ||
            !ControlledCaptionUpgrade203.clearlyBetter(firstIssue, issue)) return Decision.LOCKED
        upgraded = true
        return Decision.UPGRADE
    }
}

/** Conservative policy: no upgrade for stylistic differences or uncertain quality. */
object ControlledCaptionUpgrade203 {
    fun clearlyBetter(oldIssue: TranslationQualityGate.Problem?,
                      newIssue: TranslationQualityGate.Problem?): Boolean =
        newIssue == null && oldIssue in setOf(
            TranslationQualityGate.Problem.LOST_NEGATION,
            TranslationQualityGate.Problem.LOST_PROFANITY,
            TranslationQualityGate.Problem.MISSING_DETAIL,
            TranslationQualityGate.Problem.ENGLISH_FRAGMENT,
            TranslationQualityGate.Problem.LITERAL_IDIOM,
            TranslationQualityGate.Problem.MISSING_QUESTION,
        )
}
