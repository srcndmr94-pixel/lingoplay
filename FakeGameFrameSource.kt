package com.example.subtitles

import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Remote Play protokolü hazır olmadan ortak GameFramePipeline hattını test etmek için
 * bellek içi sahte video kaynağı.
 *
 * Bitmap sahipliği FakeGameFrameSource'a aittir. Kaynak, emit öncesinde güvenli bir
 * kopya üretir; tüketici GameFrame.bitmap üzerinde recycle() çağırmamalıdır.
 */
class FakeGameFrameSource(
    private val frameIntervalMs: Long = 33L
) : GameFrameSource {

    private val _state = MutableStateFlow<GameFrameSourceState>(GameFrameSourceState.Idle)
    override val state: StateFlow<GameFrameSourceState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<GameFrame>(
        replay = 0,
        extraBufferCapacity = 1
    )
    override val frames: Flow<GameFrame> = _frames.asSharedFlow()

    private val running = AtomicBoolean(false)

    override suspend fun start() {
        if (!running.compareAndSet(false, true)) return
        _state.value = GameFrameSourceState.Starting
        _state.value = GameFrameSourceState.Running
    }

    override suspend fun stop() {
        if (!running.compareAndSet(true, false)) return
        _state.value = GameFrameSourceState.Stopping
        _state.value = GameFrameSourceState.Idle
    }

    /** Tek bir kareyi, gerçek Remote Play decoder'ından gelmiş gibi yayınlar. */
    fun emit(bitmap: Bitmap, timestampMs: Long = SystemClock.elapsedRealtime()): Boolean {
        check(running.get()) { "FakeGameFrameSource başlatılmadı." }
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            ?: return false
        val accepted = _frames.tryEmit(GameFrame(copy, timestampMs))
        if (!accepted) copy.recycle()
        return accepted
    }

    /** Bir dizi kareyi yaklaşık video hızında yayınlamak için test yardımcısı. */
    suspend fun play(bitmaps: List<Bitmap>) {
        for (bitmap in bitmaps) {
            if (!running.get()) break
            emit(bitmap)
            delay(frameIntervalMs)
        }
    }

    fun isRunning(): Boolean = running.get()
}
