package com.example.subtitles

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.Surface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Gerçek codec eklenmeden RemotePlayFrameSource entegrasyonunu test eden decoder. */
class FakeRemoteVideoDecoder(
    private val frameFactory: (EncodedVideoPacket) -> Bitmap?
) : RemoteVideoDecoder {
    private val _frames = MutableSharedFlow<GameFrame>(extraBufferCapacity = 2)
    override val frames: Flow<GameFrame> = _frames.asSharedFlow()

    override suspend fun configure(
        codec: RemoteVideoCodec,
        width: Int,
        height: Int,
        renderSurface: Surface?
    ) = Unit

    override suspend fun queue(packet: EncodedVideoPacket) {
        val bitmap = frameFactory(packet) ?: return
        if (!_frames.tryEmit(GameFrame(bitmap, SystemClock.elapsedRealtime()))) {
            bitmap.recycle()
        }
    }

    override suspend fun flush() = Unit
    override suspend fun release() = Unit
}
