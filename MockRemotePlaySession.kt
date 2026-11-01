package com.example.subtitles

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

class MockRemotePlaySession : RemotePlaySession {
    private val connected = AtomicBoolean(false)

    private val _state = MutableStateFlow<RemotePlaySessionState>(RemotePlaySessionState.Idle)
    override val state: StateFlow<RemotePlaySessionState> = _state.asStateFlow()

    private val _statistics = MutableStateFlow(RemotePlayStatistics())
    override val statistics: StateFlow<RemotePlayStatistics> = _statistics.asStateFlow()

    private val _videoPackets = MutableSharedFlow<EncodedVideoPacket>(extraBufferCapacity = 8)
    override val videoPackets: Flow<EncodedVideoPacket> = _videoPackets.asSharedFlow()

    override suspend fun connect(
        console: RemotePlayConsole,
        credentials: RemotePlayCredentials,
        configuration: RemotePlayConfiguration
    ) {
        if (!connected.compareAndSet(false, true)) return
        _state.value = RemotePlaySessionState.Connecting
        _state.value = RemotePlaySessionState.Authenticating
        _statistics.value = RemotePlayStatistics(decodedFps = configuration.targetFps.toFloat())
        _state.value = RemotePlaySessionState.Streaming
    }

    override suspend fun disconnect() {
        connected.set(false)
        _state.value = RemotePlaySessionState.Idle
        _statistics.value = RemotePlayStatistics()
    }

    fun emitVideo(packet: EncodedVideoPacket): Boolean {
        check(connected.get()) { "MockRemotePlaySession bağlı değil." }
        return _videoPackets.tryEmit(packet)
    }

    fun fail(message: String, cause: Throwable? = null) {
        connected.set(false)
        _state.value = RemotePlaySessionState.Failed(message, cause)
    }
}
