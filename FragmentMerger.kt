package com.example.altyazicevir.merger

import android.os.SystemClock
import java.text.Normalizer
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ==========================================
// 1. TEXT NORMALIZER
// ==========================================

object TextNormalizer {

    private val punctuationRegex = Regex("[^\\p{L}\\p{N}\\s]")
    private val multiSpaceRegex = Regex("\\s+")

    fun normalize(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
            .lowercase(Locale.forLanguageTag("tr"))
            .replace(punctuationRegex, " ")
            .replace(multiSpaceRegex, " ")
            .trim()
    }
}

// ==========================================
// 2. OCR SCREEN / UI FILTER
// ==========================================

object SubtitleFilter {

    // Uygulama ve sosyal medya isimleri.
    // DİKKAT: Tek harfli ad eklenmez. "x" (Twitter) buradaydı ve " x " kalıbıyla
    // eşleşip "X marks the spot", "Meet me at the x" gibi gerçek replikleri eliyordu.
    // Kumanda simgesi okumaları zaten controllerPrompts ve controllerPrefixRegex ile yakalanıyor.
    private val appNames = setOf(
        "facebook",
        "messenger",
        "instagram",
        "whatsapp",
        "tiktok",
        "youtube",
        "discord",
        "telegram",
        "spotify",
        "twitter",
        "geoguessr",
        "geo guessr",
        "google",
        "chrome",
        "play store",
        "app store"
    )

    // Tek başına arayüz olarak kabul edilen ifadeler.
    private val interfaceLabels = setOf(
        "exit",
        "see shop",
        "open shop",
        "return to menu",
        "press any key",
        "press to continue",
        "loading",
        "settings",
        "inventory",
        "main menu",
        "pause menu",
        "resume game"
    )

    // Kontrol simgesi + eylem şeklindeki OCR okumaları.
    // "Dive" veya "Exit" kelimesi tek başına engellenmez.
    private val controllerPrompts = setOf(
        "x exit",
        "x shop",
        "o dive",
        "o swim",
        "o lunge",
        "o interact",
        "o talk",
        "o select",
        "o confirm",
        "o cancel",
        "o jump",
        "o climb",
        "o open",
        "o close",
        "o pick up",
        "o use"
    )

    private val controllerPrefixRegex = Regex("^[xo]\\s+")

    /**
     * Boş metin için false döner. Boş OCR "yoksayılacak arayüz metni" değildir:
     * altyazının bittiğini bildiren sinyaldir ve birleştiricinin boş kare yoluna
     * ulaşması gerekir. Burada true dönmek o yolu tamamen ölü bırakıyordu.
     */
    fun shouldIgnore(rawText: String): Boolean {

        val normalized = TextNormalizer.normalize(rawText)

        if (normalized.isBlank()) return false

        if (normalized in interfaceLabels) return true
        if (normalized in controllerPrompts) return true

        // Uygulama adı içeren masaüstü / sosyal medya OCR metinleri.
        val padded = " $normalized "

        if (appNames.any { name -> padded.contains(" $name ") }) {
            return true
        }

        // X Exit, O Dive gibi kontrol düğmesi okumaları.
        if (controllerPrefixRegex.containsMatchIn(normalized)) {

            val firstWord = normalized.substringBefore(" ")
            val rest = normalized.substringAfter(" ", "")

            if (
                firstWord == "x" &&
                rest in setOf("exit", "shop", "select", "confirm")
            ) {
                return true
            }

            if (
                firstWord == "o" &&
                rest in setOf(
                    "dive", "swim", "lunge", "interact",
                    "talk", "select", "confirm", "cancel",
                    "jump", "climb", "open", "close"
                )
            ) {
                return true
            }
        }

        // Birden fazla kontrol eylemi aynı OCR satırındaysa
        // bunu altyazı yerine oyun arayüzü kabul et.
        val controlWords = setOf(
            "swim", "lunge", "dive", "jump",
            "interact", "select", "confirm"
        )

        val words = normalized.split(" ")
        val controlWordCount = words.count { it in controlWords }

        if (
            controlWordCount >= 2 &&
            (words.contains("o") || words.contains("x"))
        ) {
            return true
        }

        return false
    }
}

// ==========================================
// 3. SIMILARITY ENGINE
// Unicode-aware Banded Levenshtein
// ==========================================

class SimilarityEngine(
    private val similarityThreshold: Double
) {

    init {
        require(similarityThreshold in 0.0..1.0)
    }

    private fun toCodePoints(text: String): IntArray {
        val result = ArrayList<Int>()
        var index = 0

        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            result.add(codePoint)
            index += Character.charCount(codePoint)
        }

        return result.toIntArray()
    }

    /**
     * Not: eşik aşıldığında gerçek benzerlik değil 0.0 döner.
     * Dönen sayı yalnızca eşikle karşılaştırılmalıdır.
     */
    fun calculateSimilarity(s1: String, s2: String): Double {

        if (s1 == s2) return 1.0

        val lhs = toCodePoints(s1)
        val rhs = toCodePoints(s2)

        val maxLen = max(lhs.size, rhs.size)

        if (maxLen == 0) return 1.0

        val maxDistance = (maxLen * (1.0 - similarityThreshold)).toInt()

        if (abs(lhs.size - rhs.size) > maxDistance) {
            return 0.0
        }

        val distance = bandedLevenshteinDistance(lhs, rhs, maxDistance)

        if (distance > maxDistance) return 0.0

        return 1.0 - distance.toDouble() / maxLen
    }

    private fun bandedLevenshteinDistance(
        lhs: IntArray,
        rhs: IntArray,
        maxDistance: Int
    ): Int {

        val lhsLength = lhs.size
        val rhsLength = rhs.size

        if (abs(lhsLength - rhsLength) > maxDistance) {
            return maxDistance + 1
        }

        val inf = Int.MAX_VALUE / 4

        var previous = IntArray(lhsLength + 1) { inf }
        var current = IntArray(lhsLength + 1) { inf }

        for (j in 0..min(lhsLength, maxDistance)) {
            previous[j] = j
        }

        for (i in 1..rhsLength) {

            current.fill(inf)

            if (i <= maxDistance) {
                current[0] = i
            }

            val start = max(1, i - maxDistance)
            val end = min(lhsLength, i + maxDistance)

            var rowMin = current[0]

            for (j in start..end) {

                val substitutionCost =
                    if (lhs[j - 1] == rhs[i - 1]) 0 else 1

                val value = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + substitutionCost
                )

                current[j] = value
                rowMin = min(rowMin, value)
            }

            // Bu satırda eşik aşıldıysa devam etmek gereksiz.
            if (rowMin > maxDistance) {
                return maxDistance + 1
            }

            val temp = previous
            previous = current
            current = temp
        }

        return previous[lhsLength]
    }
}

// ==========================================
// 4. SUBTITLE VARIANTS
// ==========================================

data class VariantRecord(
    var rawText: String,
    var frequency: Int,
    var lastSeenTime: Long
)

// ==========================================
// 5. SUBTITLE CANDIDATE
// ==========================================

data class SubtitleCandidate(
    var rawText: String,
    var normalizedText: String,
    val firstSeenTime: Long,
    var lastSeenTime: Long,
    var hitCount: Int,
    val variantFrequencies: MutableMap<String, VariantRecord> = mutableMapOf(),
    var hasEmitted: Boolean = false,
    var lastEmittedNormalized: String? = null
) {

    fun addVariant(raw: String, now: Long) {

        val normalized = TextNormalizer.normalize(raw)
        if (normalized.isBlank()) return

        val existing = variantFrequencies[normalized]

        if (existing == null) {
            variantFrequencies[normalized] = VariantRecord(
                rawText = raw,
                frequency = 1,
                lastSeenTime = now
            )
        } else {
            existing.frequency++
            existing.lastSeenTime = now

            if (raw.length > existing.rawText.length) {
                existing.rawText = raw
            }
        }
    }

    fun observe(raw: String, now: Long) {

        hitCount++
        lastSeenTime = now

        addVariant(raw, now)

        // Ana metni tek seferlik OCR uzatmasına göre değiştirme;
        // güvenilir varyant seçildikten sonra güncelle.
        val bestText = getBestVariantText()

        rawText = bestText
        normalizedText = TextNormalizer.normalize(bestText)
    }

    /**
     * @param allowSingleSightExtension adayın ömrü bittiğinde (finalize) true geçilir.
     *   O noktada yeni kare gelmeyeceği için kelime sınırında uzanan tek kez görülmüş
     *   varyant da kabul edilir. Aksi hâlde son karede tamamlanan altyazı
     *   ("The door is" x3, sonra "The door is open" x1) hiç yayınlanmıyordu.
     *   Kelime sınırı şartı korunduğu için OCR çöpü ("Runnn xq") yine elenir.
     */
    fun getBestVariantText(
        allowSingleSightExtension: Boolean = false
    ): String {

        if (variantFrequencies.isEmpty()) return rawText

        val repeatedVariants = variantFrequencies.entries.filter {
            it.value.frequency >= 2
        }

        val reliableVariants =
            if (repeatedVariants.isNotEmpty()) {
                repeatedVariants
            } else {
                variantFrequencies.entries.toList()
            }

        val base = reliableVariants.maxWithOrNull(
            compareBy<Map.Entry<String, VariantRecord>> {
                it.value.frequency
            }.thenBy {
                it.key.length
            }.thenBy {
                it.value.lastSeenTime
            }
        ) ?: return rawText

        val baseKey = base.key

        val extensionPool =
            if (allowSingleSightExtension) {
                variantFrequencies.entries.toList()
            } else {
                reliableVariants
            }

        val bestExtension = extensionPool
            .filter { candidate ->
                candidate.key.length > baseKey.length &&
                    candidate.key.startsWith(baseKey) &&
                    candidate.key[baseKey.length] == ' ' &&
                    (
                        allowSingleSightExtension ||
                            candidate.value.frequency >= 2
                        )
            }
            .maxWithOrNull(
                compareBy<Map.Entry<String, VariantRecord>> {
                    it.value.frequency
                }.thenBy {
                    it.key.length
                }.thenBy {
                    it.value.lastSeenTime
                }
            )

        return (bestExtension ?: base).value.rawText
    }
}

// ==========================================
// 6. EMISSION CACHE - TTL + LRU
// ==========================================

class EmissionCache(
    private val ttlMillis: Long,
    private val maxSize: Int
) {

    init {
        require(ttlMillis > 0)
        require(maxSize > 0)
    }

    private val cache = LinkedHashMap<String, Long>(maxSize, 0.75f, true)

    @Synchronized
    fun contains(normalizedText: String, now: Long): Boolean {
        prune(now)
        return cache.containsKey(normalizedText)
    }

    @Synchronized
    fun put(normalizedText: String, now: Long) {

        prune(now)
        cache[normalizedText] = now

        while (cache.size > maxSize) {
            val oldestKey = cache.keys.iterator().next()
            cache.remove(oldestKey)
        }
    }

    @Synchronized
    private fun prune(now: Long) {

        val iterator = cache.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()

            if (now - entry.value >= ttlMillis) {
                iterator.remove()
            }
        }
    }
}

// ==========================================
// 7. FRAGMENT MERGER
// ==========================================

class FragmentMerger(

    private val minConsecutiveMatches: Int = 3,
    private val minStabilityMillis: Long = 400L,
    private val maxWaitMillis: Long = 1500L,
    private val maxInactivityMillis: Long = 1000L,
    private val similarityThreshold: Double = 0.85,
    emissionCacheTtlMillis: Long = 10000L,
    maxCacheSize: Int = 100,
    private val emptyGracePeriodMillis: Long = 500L,
    private val clock: () -> Long = {
        SystemClock.elapsedRealtime()
    }

) {

    init {
        require(minConsecutiveMatches > 0)
        require(minStabilityMillis >= 0)
        require(maxWaitMillis > 0)
        require(maxInactivityMillis > 0)
        require(similarityThreshold in 0.0..1.0)
        require(emptyGracePeriodMillis >= 0)
    }

    private var currentCandidate: SubtitleCandidate? = null
    private var pendingAlternativeCandidate: SubtitleCandidate? = null
    private var emptySinceTimestamp: Long = -1L

    private val similarityEngine = SimilarityEngine(similarityThreshold)

    private val emissionCache = EmissionCache(emissionCacheTtlMillis, maxCacheSize)

    private val lock = Any()

    sealed class MergerResult {
        object Pending : MergerResult()

        /**
         * @param isUpdate true ise bu, az önce yayınlanan metnin düzeltilmiş/uzamış hâlidir.
         *   Çağıran taraf ekrandaki yazıyı YERİNDE değiştirmeli, yeni bir altyazı saymamalı
         *   ve bulut çevirisini yalnızca metin oturduktan sonra istemelidir.
         */
        data class Stable(
            val text: String,
            val isUpdate: Boolean = false
        ) : MergerResult()
    }

    // ==========================================
    // MAIN OCR INPUT
    // ==========================================

    fun processRawOcr(rawText: String): MergerResult =
        synchronized(lock) {

            val now = clock()
            val trimmedRaw = rawText.trim()
            val cleaned = TextNormalizer.normalize(trimmedRaw)

            // SIRA ÖNEMLİ: boş OCR kontrolü süzgeçten ÖNCE gelir.
            // Boş metin "yoksayılacak arayüz metni" değil, altyazının bittiği sinyalidir.
            // Süzgeç önce çalıştığında handleEmptyOcr'a hiç ulaşılmıyor, emptySinceTimestamp
            // hiç atanmıyor ve emptyGracePeriodMillis tamamen işlevsiz kalıyordu.
            if (cleaned.isBlank()) {
                return@synchronized handleEmptyOcr(now)
            }

            // Arayüz / uygulama metinlerini birleştiriciye alma.
            // Bu metin boş OCR sayılmaz; mevcut adayı sonlandırmaz.
            if (SubtitleFilter.shouldIgnore(trimmedRaw)) {
                return@synchronized MergerResult.Pending
            }

            emptySinceTimestamp = -1L

            val current = currentCandidate

            if (current == null) {

                val alternative = pendingAlternativeCandidate

                if (alternative != null) {
                    alternative.observe(trimmedRaw, now)
                    currentCandidate = alternative
                    pendingAlternativeCandidate = null

                    return@synchronized emitIfReady(alternative, now)
                }

                currentCandidate = createCandidate(trimmedRaw, cleaned, now)

                return@synchronized MergerResult.Pending
            }

            val similarity = similarityEngine.calculateSimilarity(
                current.normalizedText,
                cleaned
            )

            if (
                isExtensionOrSimilar(
                    current.normalizedText,
                    cleaned,
                    similarity
                )
            ) {
                current.observe(trimmedRaw, now)

                // Eski aday tekrar görüldüyse alternatif artık
                // ardışık bir altyazı olarak kabul edilmez.
                pendingAlternativeCandidate = null

                return@synchronized emitIfReady(current, now)
            }

            // Gelen metin mevcut adaydan farklı.
            // Önce alternatif adayla karşılaştır.
            var alternative = pendingAlternativeCandidate

            if (alternative != null) {

                val altSimilarity = similarityEngine.calculateSimilarity(
                    alternative.normalizedText,
                    cleaned
                )

                if (
                    isExtensionOrSimilar(
                        alternative.normalizedText,
                        cleaned,
                        altSimilarity
                    )
                ) {
                    alternative.observe(trimmedRaw, now)
                } else {
                    alternative = createCandidate(trimmedRaw, cleaned, now)
                    pendingAlternativeCandidate = alternative
                }

            } else {
                alternative = createCandidate(trimmedRaw, cleaned, now)
                pendingAlternativeCandidate = alternative
            }

            // Alternatif yeterince kararlı hale geldiyse
            // eski adayı sonuçlandır ve alternatifi etkinleştir.
            if (isValidForEmission(alternative)) {

                val oldResult = finalizeCandidate(current, now)

                currentCandidate = alternative
                pendingAlternativeCandidate = null
                emptySinceTimestamp = -1L

                // Tek çağrıda tek sonuç döndürüldüğü için eski adayın sonucu önceliklidir.
                // Yeni aday sonraki OCR/flush çağrısında yayınlanır.
                return@synchronized oldResult
            }

            MergerResult.Pending
        }

    // ==========================================
    // EMPTY OCR HANDLING
    // ==========================================

    private fun handleEmptyOcr(now: Long): MergerResult {

        // currentCandidate bir var olduğu için akıllı dönüşüm uygulanmaz;
        // yerel bir değişkene alınmazsa finalizeCandidate çağrısı derlenmez.
        val current = currentCandidate ?: return MergerResult.Pending

        if (emptySinceTimestamp == -1L) {
            emptySinceTimestamp = now
        }

        if (now - emptySinceTimestamp < emptyGracePeriodMillis) {
            return MergerResult.Pending
        }

        val result = finalizeCandidate(current, now)

        // Alternatif adayı silmek yerine sıradaki aday yap.
        currentCandidate = pendingAlternativeCandidate
        pendingAlternativeCandidate = null
        emptySinceTimestamp = -1L

        return result
    }

    // ==========================================
    // FLUSH / TIMEOUT
    // ==========================================

    /**
     * Zaman aşımı mantığının TAMAMI burada. processRawOcr artık zaman aşımı kontrol
     * etmiyor, bu yüzden maxWaitMillis ve maxInactivityMillis yalnızca bu fonksiyon
     * çağrıldığında çalışır. Tarama döngüsünde her karenin sonunda çağırın; aday
     * yoksa maliyeti yoktur.
     */
    fun flush(): MergerResult = synchronized(lock) {

        val now = clock()
        val current = currentCandidate
            ?: return@synchronized MergerResult.Pending

        val emptyElapsed =
            emptySinceTimestamp != -1L &&
                now - emptySinceTimestamp >= emptyGracePeriodMillis

        val inactivityExceeded =
            now - current.lastSeenTime >= maxInactivityMillis

        val totalLifeExceeded =
            !current.hasEmitted &&
                now - current.firstSeenTime >= maxWaitMillis

        if (!emptyElapsed && !inactivityExceeded && !totalLifeExceeded) {
            return@synchronized MergerResult.Pending
        }

        val enoughHits = current.hitCount >= minConsecutiveMatches

        // maxWait doldu ama yeterli OCR okuması yok ve akış hâlâ sürüyor:
        // adayı kapatma, yeni karelerin gelmesine izin ver.
        if (totalLifeExceeded && !inactivityExceeded && !emptyElapsed && !enoughHits) {
            return@synchronized MergerResult.Pending
        }

        val result = if (enoughHits) {
            finalizeCandidate(
                current,
                now,
                forceTimeValidation = true
            )
        } else {
            MergerResult.Pending
        }

        // Bekleyen alternatif korunur, sıradaki aday olur.
        currentCandidate = pendingAlternativeCandidate
        pendingAlternativeCandidate = null
        emptySinceTimestamp = -1L

        return@synchronized result
    }

    // ==========================================
    // EMISSION VALIDATION
    // ==========================================

    private fun isValidForEmission(candidate: SubtitleCandidate): Boolean {

        val stableEnough = candidate.hitCount >= minConsecutiveMatches

        val timeEnough =
            candidate.lastSeenTime - candidate.firstSeenTime >= minStabilityMillis

        return stableEnough && timeEnough
    }

    private fun emitIfReady(
        candidate: SubtitleCandidate,
        now: Long
    ): MergerResult {

        return if (isValidForEmission(candidate)) {
            emitCandidate(candidate, now)
        } else {
            MergerResult.Pending
        }
    }

    // ==========================================
    // EMISSION
    // ==========================================

    private fun emitCandidate(
        candidate: SubtitleCandidate,
        now: Long,
        allowSingleSightExtension: Boolean = false
    ): MergerResult {

        val text = candidate.getBestVariantText(allowSingleSightExtension)

        val normalized = TextNormalizer.normalize(text)

        if (normalized.isBlank()) {
            return MergerResult.Pending
        }

        // Aynı sürümü tekrar yayınlama.
        if (normalized == candidate.lastEmittedNormalized) {
            return MergerResult.Pending
        }

        val isUpdate = candidate.hasEmitted

        // Önbellek yalnızca yeni altyazılar için; aynı altyazının güncellemeleri engellenmez.
        if (!isUpdate && emissionCache.contains(normalized, now)) {
            // hasEmitted ve lastEmittedNormalized İŞARETLENMEZ: ekrana bir şey basılmadı.
            // İşaretlenirse metin sonradan büyüdüğünde yayın isUpdate=true gelir ve
            // çağıran taraf hiç gösterilmemiş bir altyazıyı güncellemeye çalışır.
            return MergerResult.Pending
        }

        if (!isUpdate) {
            emissionCache.put(normalized, now)
        }

        candidate.hasEmitted = true
        candidate.lastEmittedNormalized = normalized

        return MergerResult.Stable(
            text = text,
            isUpdate = isUpdate
        )
    }

    // ==========================================
    // FINALIZE
    // ==========================================

    private fun finalizeCandidate(
        candidate: SubtitleCandidate,
        now: Long,
        forceTimeValidation: Boolean = false
    ): MergerResult {

        val valid = isValidForEmission(candidate)

        if (!valid) {

            val enoughHits = candidate.hitCount >= minConsecutiveMatches

            // maxWait dolduğunda süre şartı esner, minimum OCR okuma sayısı korunur.
            if (!forceTimeValidation || !enoughHits) {
                return MergerResult.Pending
            }
        }

        // Adayın ömrü burada bitiyor: tek kez görülmüş kelime sınırı uzantısı kabul edilir.
        return emitCandidate(
            candidate,
            now,
            allowSingleSightExtension = true
        )
    }

    // ==========================================
    // INIT
    // ==========================================

    private fun createCandidate(
        raw: String,
        normalized: String,
        now: Long
    ): SubtitleCandidate {

        val candidate = SubtitleCandidate(
            rawText = raw,
            normalizedText = normalized,
            firstSeenTime = now,
            lastSeenTime = now,
            hitCount = 1
        )

        candidate.addVariant(raw, now)

        return candidate
    }

    // ==========================================
    // SIMILARITY / EXTENSION
    // ==========================================

    /**
     * Uzantı yalnızca KELİME SINIRINDA sayılır. Düz startsWith kullanmak
     * "go" ile "good", "run" ile "running", "we can" ile "we cannot" çiftlerini
     * aynı altyazının devamı sanıyordu.
     */
    private fun isWordBoundaryExtension(
        shorter: String,
        longer: String
    ): Boolean {

        if (shorter.isBlank() || longer.isBlank()) return false
        if (longer.length <= shorter.length) return false
        if (!longer.startsWith(shorter)) return false

        return longer[shorter.length] == ' '
    }

    private fun isExtensionOrSimilar(
        existing: String,
        incoming: String,
        similarity: Double
    ): Boolean {

        if (existing == incoming) return true

        if (similarity >= similarityThreshold) return true

        // Gerçek kelime sınırı uzatması.
        if (isWordBoundaryExtension(existing, incoming)) return true

        // OCR geçici olarak daha kısa okumuş olabilir.
        if (isWordBoundaryExtension(incoming, existing)) return true

        return false
    }
}
