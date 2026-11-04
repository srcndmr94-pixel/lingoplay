package com.example.subtitles

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CentralTranslationResultHandler(
    private val showOverlay: (String, TranslationSource) -> Unit,
    private val onFailure: (TranslationResult.Failure) -> Unit = {},
    private val stateDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private var latestSubtitleId = Long.MIN_VALUE
    private var publishedSubtitleId: Long? = null

    suspend fun onNewSubtitle(subtitleId: Long) {
        withContext(stateDispatcher) {
            if (subtitleId > latestSubtitleId) {
                latestSubtitleId = subtitleId
                publishedSubtitleId = null
            }
        }
    }

    suspend fun resetSession() {
        withContext(stateDispatcher) {
            latestSubtitleId = Long.MIN_VALUE
            publishedSubtitleId = null
        }
    }

    suspend fun handle(result: TranslationResult) {
        withContext(stateDispatcher) {
            if (result.subtitleId != latestSubtitleId) return@withContext
            when (result) {
                is TranslationResult.Success -> publishSuccess(result)
                is TranslationResult.Failure -> publishFailure(result)
            }
        }
    }

    private fun publishSuccess(result: TranslationResult.Success) {
        if (publishedSubtitleId == result.subtitleId) return
        val translation = result.translation.trim()
        if (translation.isBlank()) return
        publishedSubtitleId = result.subtitleId
        try {
            showOverlay(translation, result.source)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    private fun publishFailure(result: TranslationResult.Failure) {
        try {
            onFailure(result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }
}
