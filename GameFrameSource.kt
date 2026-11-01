package com.example.subtitles

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class GameFrame(
    val bitmap: Bitmap,
    val timestampMs: Long,
    val rotationDegrees: Int = 0
) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
}

sealed interface GameFrameSourceState {
    data object Idle : GameFrameSourceState
    data object Starting : GameFrameSourceState
    data object Running : GameFrameSourceState
    data object Stopping : GameFrameSourceState
    data class Failed(
        val message: String,
        val cause: Throwable? = null
    ) : GameFrameSourceState
}

/**
 * Ekran yakalama ve gelecekteki Remote Play kaynaklarının ortak sözleşmesi.
 * Bitmap yaşam döngüsü kaynak implementasyonuna aittir; tüketici recycle() çağırmamalıdır.
 */
interface GameFrameSource {
    val state: StateFlow<GameFrameSourceState>
    val frames: Flow<GameFrame>

    suspend fun start()
    suspend fun stop()
}

fun interface GameFrameProcessor {
    suspend fun process(frame: GameFrame)
}
