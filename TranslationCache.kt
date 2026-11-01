package com.example.subtitles

import java.util.LinkedHashMap

data class TranslationCacheStats(
    val size: Int,
    val hits: Long,
    val misses: Long,
    val evictions: Long
)

/**
 * Hızlı, bellek içi LRU çeviri önbelleği.
 * Cache yalnız metin sonucunu saklar; API anahtarı veya kullanıcı sırrı saklamaz.
 * contextKey, aynı kaynak cümlenin farklı bağlamlarda yanlış sonucu paylaşmasını önler.
 */
class TranslationCache(
    private val maxEntries: Int = 256,
    private val ttlMs: Long = 30 * 60 * 1000L,
    private val nowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() }
) {
    init {
        require(maxEntries > 0)
        require(ttlMs > 0)
    }

    private data class Key(
        val source: String,
        val contextKey: String,
        val engineKey: String
    )

    private data class Entry(
        val translated: String,
        val createdAtMs: Long
    )

    private val entries = object : LinkedHashMap<Key, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>?): Boolean {
            val remove = size > maxEntries
            if (remove) evictions++
            return remove
        }
    }

    private var hits = 0L
    private var misses = 0L
    private var evictions = 0L

    @Synchronized
    fun get(source: String, context: String = "", engineKey: String = "default"): String? {
        val key = keyOf(source, context, engineKey)
        val entry = entries[key]
        if (entry == null) {
            misses++
            return null
        }
        if (nowMs() - entry.createdAtMs > ttlMs) {
            entries.remove(key)
            misses++
            return null
        }
        hits++
        return entry.translated
    }

    @Synchronized
    fun put(source: String, translated: String, context: String = "", engineKey: String = "default") {
        val cleanResult = translated.trim()
        if (cleanResult.isBlank()) return
        entries[keyOf(source, context, engineKey)] = Entry(cleanResult, nowMs())
    }

    @Synchronized
    fun invalidate(source: String, context: String = "", engineKey: String = "default") {
        entries.remove(keyOf(source, context, engineKey))
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun stats(): TranslationCacheStats = TranslationCacheStats(
        size = entries.size,
        hits = hits,
        misses = misses,
        evictions = evictions
    )

    private fun keyOf(source: String, context: String, engineKey: String): Key = Key(
        source = normalize(source),
        contextKey = normalize(context).takeLast(320),
        engineKey = engineKey.trim().lowercase()
    )

    private fun normalize(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()
}
