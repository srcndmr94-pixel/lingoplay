package com.example.subtitles

import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MediaProjection görüntüsünü ortak GameFrameSource sözleşmesine taşır.
 * MediaProjection izni/token'ı bu sınıfın dışındaki mevcut izin akışından sağlanır.
 */
class MediaProjectionFrameSource(
    private val mediaProjection: MediaProjection,
    private val width: Int,
    private val height: Int,
    private val densityDpi: Int,
    private val imageReader: ImageReader,
    private val imageConverter: (android.media.Image) -> Bitmap?
) : GameFrameSource {

    private val _state = MutableStateFlow<GameFrameSourceState>(GameFrameSourceState.Idle)
    override val state: StateFlow<GameFrameSourceState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<GameFrame>(extraBufferCapacity = 1)
    override val frames: Flow<GameFrame> = _frames.asSharedFlow()

    private val running = AtomicBoolean(false)
    private var virtualDisplay: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopInternal(stopProjection = false)
        }
    }

    override suspend fun start() {
        if (!running.compareAndSet(false, true)) return
        _state.value = GameFrameSourceState.Starting
        try {
            val worker = HandlerThread("GameFrame-MediaProjection").apply { start() }
            thread = worker
            handler = Handler(worker.looper)
            mediaProjection.registerCallback(projectionCallback, handler)

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val bitmap = imageConverter(image) ?: return@setOnImageAvailableListener
                    val accepted = _frames.tryEmit(
                        GameFrame(bitmap = bitmap, timestampMs = SystemClock.elapsedRealtime())
                    )
                    if (!accepted) bitmap.recycle()
                } finally {
                    image.close()
                }
            }, handler)

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "SubtitleGameFrame",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                handler
            )
            _state.value = GameFrameSourceState.Running
        } catch (t: Throwable) {
            _state.value = GameFrameSourceState.Failed(t.message ?: "MediaProjection başlatılamadı", t)
            stopInternal(stopProjection = false)
            throw t
        }
    }

    override suspend fun stop() {
        stopInternal(stopProjection = true)
    }

    private fun stopInternal(stopProjection: Boolean) {
        if (!running.getAndSet(false) && virtualDisplay == null && thread == null) return
        _state.value = GameFrameSourceState.Stopping
        imageReader.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        runCatching { mediaProjection.unregisterCallback(projectionCallback) }
        if (stopProjection) runCatching { mediaProjection.stop() }
        imageReader.close()
        thread?.quitSafely()
        thread = null
        handler = null
        _state.value = GameFrameSourceState.Idle
    }
}
