package com.example.subtitles

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class RemoteConsoleType { PS4, PS5, UNKNOWN }
enum class RemoteVideoCodec { AVC, HEVC }

data class RemotePlayConsole(
    val id: String,
    val displayName: String,
    val host: String,
    val type: RemoteConsoleType
)

data class RemotePlayCredentials(
    val registrationKey: ByteArray,
    val sessionKey: ByteArray? = null
) {
    override fun toString(): String = "RemotePlayCredentials(redacted)"
}

data class RemotePlayConfiguration(
    val width: Int = 1280,
    val height: Int = 720,
    val targetFps: Int = 60,
    val enableAudio: Boolean = true
)

data class EncodedVideoPacket(
    val data: ByteArray,
    val presentationTimeUs: Long,
    val keyFrame: Boolean,
    val codec: RemoteVideoCodec
)

data class RemotePlayStatistics(
    val roundTripTimeMs: Long? = null,
    val receivedBitrate: Long? = null,
    val decodedFps: Float? = null,
    val droppedFrames: Long = 0,
    val reconnectCount: Int = 0
)

sealed interface RemotePlaySessionState {
    data object Idle : RemotePlaySessionState
    data object Connecting : RemotePlaySessionState
    data object Authenticating : RemotePlaySessionState
    data object Streaming : RemotePlaySessionState
    data object Reconnecting : RemotePlaySessionState
    data class Failed(val message: String, val cause: Throwable? = null) : RemotePlaySessionState
}

interface RemotePlaySession {
    val state: StateFlow<RemotePlaySessionState>
    val statistics: StateFlow<RemotePlayStatistics>
    val videoPackets: Flow<EncodedVideoPacket>

    suspend fun connect(
        console: RemotePlayConsole,
        credentials: RemotePlayCredentials,
        configuration: RemotePlayConfiguration
    )

    suspend fun disconnect()
}
