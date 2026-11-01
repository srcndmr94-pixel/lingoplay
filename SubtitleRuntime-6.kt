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

    // Her SubtitleProcessor çağrısı bu kapıdan geçer. Filtre atlanamaz.
    private val guardedSubtitleTranslator = GuardedSubtitleTranslator(subtitleRouter)

    val processor = SubtitleProcessor(
        translator = guardedSubtitleTranslator,
        debounceMillis = 220L,
        contextSize = 3
    )

    constructor(
        context: Context,
        telemetry: TranslationTelemetry = NoOpTranslationTelemetry
    ) : this(
        context = context,
        apiKeyStore = ApiKeyStore(context),
        telemetry = telemetry
    )

    private constructor(
        context: Context,
        apiKeyStore: ApiKeyStore,
        telemetry: TranslationTelemetry
    ) : this(
        // Gemini ve hasKey kontrolü aynı ApiKeyStore örneğini kullanır.
        onlineClient = GeminiSubtitleTranslator(apiKeyStore),
        offlineClient = OfflinePolishingTranslator(
            OfflineTranslationEngineAdapter(
                OfflineSubtitleTranslator(requireWifiForModelDownload = true)
            )
        ),
        apiKeyProvider = ApiKeyProviderAdapter(apiKeyStore),
        telemetry = telemetry
    )

    suspend fun prepare() = offlineClient.prepareModel()

    override fun close() {
        offlineClient.close()
        (onlineClient as? AutoCloseable)?.close()
    }
}
