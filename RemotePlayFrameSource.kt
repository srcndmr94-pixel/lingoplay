package com.example.subtitles

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** RemotePlaySession video paketlerini decoder üzerinden ortak GameFrameSource'a taşır. */
class RemotePlayFrameSource(
    private val session: RemotePlaySession,
    private val decoder: RemoteVideoDecoder,
    private val scope: CoroutineScope,
    private val console: RemotePlayConsole,
    private val credentials: RemotePlayCredentials,
    private val configuration: RemotePlayConfiguration
) : GameFrameSource {

    private val _state = MutableStateFlow<GameFrameSourceState>(GameFrameSourceState.Idle)
    override val state: StateFlow<GameFrameSourceState> = _state.asStateFlow()
    override val frames: Flow<GameFrame> = decoder.frames

    private var packetJob: Job? = null

    override suspend fun start() {
        if (packetJob != null) return
        _state.value = GameFrameSourceState.Starting
        try {
            decoder.configure(
                codec = RemoteVideoCodec.AVC,
                width = configuration.width,
                height = configuration.height
            )
            packetJob = scope.launch {
                session.videoPackets.collect { decoder.queue(it) }
            }
            session.connect(console, credentials, configuration)
            _state.value = GameFrameSourceState.Running
        } catch (t: Throwable) {
            _state.value = GameFrameSourceState.Failed(t.message ?: "Remote Play başlatılamadı", t)
            stop()
            throw t
        }
    }

    override suspend fun stop() {
        _state.value = GameFrameSourceState.Stopping
        packetJob?.cancel()
        packetJob = null
        runCatching { session.disconnect() }
        runCatching { decoder.flush() }
        runCatching { decoder.release() }
        _state.value = GameFrameSourceState.Idle
    }
}
