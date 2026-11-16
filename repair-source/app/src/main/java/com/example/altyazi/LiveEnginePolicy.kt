package com.example.altyazi

/**
 * Based on the user's 10.36 Pixel 9 Pro benchmark, E4B takes a median
 * 3.27 s per subtitle and timed out once. It is not a realtime default.
 * The 11-line benchmark is separate and can still invoke it explicitly.
 */
object LiveEnginePolicy {
    /** Only an explicit opt-in to slow, accuracy-first mode loads the local LLM. */
    fun usePhoneLlm(format: Int): Boolean = format == Prefs.BICIM_DOGRU

    /**
     * When no paid cloud AI mode was chosen, BALANCED must not silently take a
     * slower/less guarded path after the local LLM has been disabled.
     * Both ordinary modes therefore share one deterministic live path.
     */
    fun useFastPipeline(format: Int, mode: Int): Boolean =
        format == Prefs.BICIM_HIZLI ||
            (format == Prefs.BICIM_DENGELI && mode == Prefs.MODE_INSTANT)

    /**
     * BALANCED, unlike FAST, asks Firefox for a second opinion only if the
     * first result has a concrete quality warning. A bad alternative is never
     * promoted just because a second engine produced it.
     */
    fun secondOpinionForWarning(format: Int): Boolean = format == Prefs.BICIM_DENGELI

    /** Google is primary; Firefox then ML Kit are availability fallbacks. */
    fun primaryEngine(googleEnabled: Boolean): String =
        if (googleEnabled) "GOOGLE" else "FIREFOX_OR_MLKIT"
}
