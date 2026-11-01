package com.example.subtitles

import android.view.Surface
import kotlinx.coroutines.flow.Flow

/** Decoder implementasyonu MediaCodec veya lisanslı native Remote Play çekirdeği olabilir. */
interface RemoteVideoDecoder {
    val frames: Flow<GameFrame>

    suspend fun configure(
        codec: RemoteVideoCodec,
        width: Int,
        height: Int,
        renderSurface: Surface? = null
    )

    suspend fun queue(packet: EncodedVideoPacket)
    suspend fun flush()
    suspend fun release()
}
