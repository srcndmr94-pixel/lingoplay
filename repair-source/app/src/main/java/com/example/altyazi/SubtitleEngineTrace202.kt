package com.example.altyazi

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Benchmark-only diagnostics: no engine selection or overlay policy changes. */
class SubtitleEngineTrace202(
    val runId: Long,
    private val english: String,
    private val previousEnglish: String,
    private val startedMs: Long,
    private val nowMs: () -> Long,
    private val sink: (String) -> Unit,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SubtitleEngineTrace202> {
        fun field(value: String): String = value.replace("\r", " ").replace("\n", " / ")
            .replace("|", "¦").replace("\t", " ").take(320)
    }

    fun result(engine: String, durationMs: Long, raw: String?, accepted: Boolean, reason: String = "-") {
        // Display only source and translated text. Never log API keys or credentials.
        try {
            sink("BENCH202|id=$runId|stage=ENGINE|engine=$engine|ms=$durationMs" +
                "|since=${nowMs() - startedMs}|accepted=${if (accepted) "yes" else "no"}" +
                "|reason=${field(reason)}|source=${field(english)}" +
                "|context=${field(previousEnglish)}|output=${field(raw.orEmpty())}")
        } catch (_: Exception) { /* Bench instrumentation must not block translation. */ }
    }
}
