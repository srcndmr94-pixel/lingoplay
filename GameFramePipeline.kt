package com.example.subtitles

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Kaynaktan gelen kareleri örnekler ve ortak OCR/çeviri işlemcisine aktarır.
 * collectLatest, daha yeni kare geldiğinde eski bekleyen işlem zincirinin iptal edilebilmesini sağlar.
 */
class GameFramePipeline(
    private val source: GameFrameSource,
    private val processor: GameFrameProcessor,
    private val sampler: FrameSampler = FrameSampler()
) {
    private var collectionJob: Job? = null

    fun bind(scope: CoroutineScope) {
        if (collectionJob != null) return
        collectionJob = scope.launch {
            source.frames.collectLatest { frame ->
                if (sampler.shouldAccept(frame.timestampMs)) {
                    processor.process(frame)
                }
            }
        }
    }

    suspend fun start() {
        sampler.reset()
        source.start()
    }

    suspend fun stop() {
        source.stop()
        collectionJob?.cancel()
        collectionJob = null
        sampler.reset()
    }
}
