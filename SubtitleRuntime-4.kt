package com.example.subtitles

import android.content.Context

class SubtitleRuntime private constructor(
    private val onlineClient: SubtitleTranslator,
    private val offlineClient: OfflineTranslationEngine,
    apiKeyProvider: ApiKeyProvider,
    telemetry: TranslationTelemetry
) : AutoCloseable {

    val subtitleRouter = TranslationRouter(
        channel = "subtitle",
        online = onlineClient,
        offline = offlineClient,
        apiKeyProvider = apiKeyProvider,
        telemetry = telemetry,
        failureThreshold = 3,
        cooldownMs = 8_000L
    )

    val panelRouter = TranslationRouter(
        channel = "panel",
        online = onlineClient,
        offline = offlineClient,
        apiKeyProvider = apiKeyProvider,
        telemetry = telemetry,
        failureThreshold = 5,
        cooldownMs = 15_000L
    )

    val processor = SubtitleProcessor(
        translator = subtitleRouter,
        debounceMillis = 220L,
        contextSize = 3
    )

    val subtitleEntryPoint = SubtitleTranslationEntryPoint(
        processor = processor,
        logger = SubtitleSessionLog
    )

    constructor(
        context: Context,
        telemetry: TranslationTelemetry = NoOpTranslationTelemetry
    ) : this(
        onlineClient = GeminiSubtitleTranslator(ApiKeyStore(context)),
        offlineClient = OfflineTranslationEngineAdapter(
            OfflineSubtitleTranslator(requireWifiForModelDownload = true)
        ),
        apiKeyProvider = ApiKeyProviderAdapter(ApiKeyStore(context)),
        telemetry = telemetry
    )

    suspend fun prepare() = offlineClient.prepareModel()

    override fun close() {
        offlineClient.close()
        (onlineClient as? AutoCloseable)?.close()
    }
}
