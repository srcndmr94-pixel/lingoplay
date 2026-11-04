package com.example.subtitles

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

class SubtitleProcessor(
    private val scope: CoroutineScope,
    private val translator: TranslationEngine,
    private val resultHandler: CentralTranslationResultHandler,
    private val debounceMillis: Long = 220L,
    private val contextSize: Int = 3
) {
    init {
        require(debounceMillis >= 0L) { "debounceMillis must be >= 0" }
        require(contextSize >= 0) { "contextSize must be >= 0" }
    }

    private val idGenerator = AtomicLong(0L)
    private val stateLock = Any()
    private val context = ArrayDeque<String>()
    private var debounceJob: Job? = null
    private var debounceOwnerId = Long.MIN_VALUE
    private var latestAcceptedId = Long.MIN_VALUE
    private var sessionGeneration = 0L
    private var lastAcceptedNormalized: String? = null

    suspend fun process(rawOcrText: String, normalizedEnglish: String) {
        val normalized = normalizedEnglish.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return

        val accepted = synchronized(stateLock) {
            if (normalized == lastAcceptedNormalized) null else {
                val subtitleId = idGenerator.incrementAndGet()
                val generation = sessionGeneration
                val previousContext = if (contextSize == 0) emptyList() else context.toList().takeLast(contextSize)
                if (contextSize > 0) {
                    context.addLast(normalized)
                    while (context.size > contextSize) context.removeFirst()
                }
                lastAcceptedNormalized = normalized
                latestAcceptedId = subtitleId
                AcceptedSubtitle(
                    SubtitleTranslationRequest(subtitleId, rawOcrText, normalized, previousContext),
                    generation
                )
            }
        } ?: return

        resultHandler.onNewSubtitle(accepted.request.subtitleId)

        val previousJob = synchronized(stateLock) {
            if (accepted.sessionGeneration != sessionGeneration || accepted.request.subtitleId != latestAcceptedId) {
                return@synchronized null
            }
            val oldJob = debounceJob
            debounceOwnerId = accepted.request.subtitleId
            val newJob = scope.launch { processAcceptedSubtitle(accepted) }
            debounceJob = newJob
            oldJob
        }
        previousJob?.cancel()
    }

    private suspend fun processAcceptedSubtitle(accepted: AcceptedSubtitle) {
        val thisJob = currentCoroutineContext().job
        try {
            if (debounceMillis > 0L) delay(debounceMillis)
            val allowed = synchronized(stateLock) {
                accepted.sessionGeneration == sessionGeneration &&
                    accepted.request.subtitleId == latestAcceptedId &&
                    accepted.request.subtitleId == debounceOwnerId &&
                    debounceJob === thisJob
            }
            if (!allowed) return
            val result = translator.translate(accepted.request)
            val current = synchronized(stateLock) {
                accepted.sessionGeneration == sessionGeneration &&
                    accepted.request.subtitleId == latestAcceptedId
            }
            if (!current) return
            resultHandler.handle(result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val relevant = synchronized(stateLock) {
                accepted.sessionGeneration == sessionGeneration &&
                    accepted.request.subtitleId == latestAcceptedId
            }
            if (!relevant) return
            resultHandler.handle(
                TranslationResult.Failure(
                    accepted.request.subtitleId,
                    "processor_error:${error::class.simpleName ?: "unknown"}"
                )
            )
        } finally {
            synchronized(stateLock) {
                if (accepted.sessionGeneration == sessionGeneration &&
                    accepted.request.subtitleId == debounceOwnerId &&
                    debounceJob === thisJob
                ) {
                    debounceJob = null
                    debounceOwnerId = Long.MIN_VALUE
                }
            }
        }
    }

    suspend fun clearContext() {
        val jobToCancel = synchronized(stateLock) {
            sessionGeneration++
            val oldJob = debounceJob
            debounceJob = null
            debounceOwnerId = Long.MIN_VALUE
            latestAcceptedId = Long.MIN_VALUE
            lastAcceptedNormalized = null
            context.clear()
            oldJob
        }
        jobToCancel?.cancel()
        resultHandler.resetSession()
    }

    private data class AcceptedSubtitle(
        val request: SubtitleTranslationRequest,
        val sessionGeneration: Long
    )
}
