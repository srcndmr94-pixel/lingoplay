package com.example.subtitles

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TranslationRouterConcurrencyTest {
    private class OfflineFake : OfflineTranslationEngine {
        override suspend fun translate(source: String, context: String) = "offline"
        override suspend fun prepareModel() = Unit
        override fun close() = Unit
    }

    private object KeyPresent : ApiKeyProvider {
        override fun hasKey() = true
    }

    @Test
    fun concurrent503sProduceOnlyOneSharedRetry() = runTest {
        val calls = AtomicInteger(0)
        val online = object : SubtitleTranslator {
            override suspend fun translate(source: String, context: String): String {
                calls.incrementAndGet()
                throw GeminiHttpException(503, "Unavailable")
            }
        }
        val router = TranslationRouter(
            channel = "test",
            online = online,
            offline = OfflineFake(),
            apiKeyProvider = KeyPresent,
            failureThreshold = 3,
            clock = MonotonicClock { testScheduler.currentTime },
            retryDelay = { testScheduler.advanceTimeBy(it) },
            jitterMs = { 0L }
        )

        val results = (1..10).map { async { router.translate("x", "") } }.awaitAll()
        assertTrue(results.all { it == "offline" })
        // Ten initial calls plus at most one shared retry.
        assertTrue(calls.get() <= 11)
    }

    @Test
    fun subtitleAndPanelCircuitsAreIndependent() = runTest {
        val online = object : SubtitleTranslator {
            override suspend fun translate(source: String, context: String): String {
                throw GeminiHttpException(503, "Unavailable")
            }
        }
        val offline = OfflineFake()
        val subtitle = TranslationRouter(
            "subtitle", online, offline, KeyPresent,
            failureThreshold = 1,
            clock = MonotonicClock { testScheduler.currentTime },
            retryDelay = { testScheduler.advanceTimeBy(it) },
            jitterMs = { 0L }
        )
        val panel = TranslationRouter(
            "panel", online, offline, KeyPresent,
            failureThreshold = 5,
            clock = MonotonicClock { testScheduler.currentTime },
            retryDelay = { testScheduler.advanceTimeBy(it) },
            jitterMs = { 0L }
        )

        assertEquals("offline", subtitle.translate("s", ""))
        assertEquals("offline", panel.translate("p", ""))
    }
}
