package com.example.altyazi

fun main() {
    var checks = 0
    fun validate(ok: Boolean) { check(ok); checks++ }

    // The live strategy actually changes the costly work, not just labels.
    validate(!LiveEnginePolicy.usePhoneLlm(Prefs.BICIM_HIZLI))
    validate(!LiveEnginePolicy.usePhoneLlm(Prefs.BICIM_DENGELI))
    validate(LiveEnginePolicy.usePhoneLlm(Prefs.BICIM_DOGRU))
    validate(LiveEnginePolicy.useFastPipeline(Prefs.BICIM_HIZLI, Prefs.MODE_INSTANT))
    validate(LiveEnginePolicy.useFastPipeline(Prefs.BICIM_DENGELI, Prefs.MODE_INSTANT))
    validate(!LiveEnginePolicy.useFastPipeline(Prefs.BICIM_DENGELI, Prefs.MODE_AI))
    validate(!LiveEnginePolicy.useFastPipeline(Prefs.BICIM_DOGRU, Prefs.MODE_INSTANT))
    validate(!LiveEnginePolicy.secondOpinionForWarning(Prefs.BICIM_HIZLI))
    validate(LiveEnginePolicy.secondOpinionForWarning(Prefs.BICIM_DENGELI))
    validate(!LiveEnginePolicy.secondOpinionForWarning(Prefs.BICIM_DOGRU))
    validate(LiveEnginePolicy.primaryEngine(true) == "GOOGLE")
    validate(LiveEnginePolicy.primaryEngine(false) == "FIREFOX_OR_MLKIT")

    // Reversal prevents display and caching.
    validate(TranslationQualityGate.check("Leave no one alive.", "Kimseyi canlı bırak.") == TranslationQualityGate.Problem.REVERSED_COMMAND)
    validate(TranslationQualityGate.mustReject(TranslationQualityGate.check("Leave no one alive.", "Kimseyi canlı bırak.")))
    validate(TranslationQualityGate.check("Leave no one alive.", "Kimseyi sağ bırakma!") == null)
    validate(!TranslationQualityGate.mayRemember("Leave no one alive.", "Kimseyi canlı bırak."))

    // Naturalness warnings cannot, by themselves, blank the screen.
    validate(TranslationQualityGate.check("And I fucking jumped.", "Ve ben de atladım.") == TranslationQualityGate.Problem.LOST_PROFANITY)
    validate(TranslationQualityGate.check("And I fucking jumped.", "Ben de atladım, amına koyayım.") == null)
    validate(!TranslationQualityGate.mustReject(TranslationQualityGate.Problem.LOST_PROFANITY))
    validate(!TranslationQualityGate.mayRemember("And I fucking jumped.", "Ve ben de atladım."))
    validate(TranslationQualityGate.check("Our planetary defense network is back up.", "Gezegensel savunma ağımız yedeklendi.") == TranslationQualityGate.Problem.LITERAL_IDIOM)
    validate(TranslationQualityGate.check("Our planetary defense network is back up.", "Gezegen savunma ağımız tekrar çalışıyor.") == null)
    validate(TranslationQualityGate.check("I passed right out.", "Hemen dışarı çıktım.") == TranslationQualityGate.Problem.LITERAL_IDIOM)
    validate(TranslationQualityGate.choose("Our planetary defense network is back up.", "Gezegensel savunma ağımız yedeklendi.", "Gezegen savunma ağımız yeniden devrede.") == "Gezegen savunma ağımız yeniden devrede.")
    validate(TranslationQualityGate.choose("This is so fucked!", "Bu çok berbat!", null) == "Bu çok berbat!")
    validate(TranslationQualityGate.check("This is so fucked!", "Bu çok boktan!") == null)
    validate(TranslationQualityGate.check("I don't think so.", "Sanmıyorum.") == null)

    // Runtime telemetry records only a bounded number of samples.
    val window = LatencyWindow1037(3)
    validate(window.summary() == null)
    window.add(100)
    window.add(300)
    window.add(200)
    validate(window.summary() == "son 3 ağ çağrısı: p50 200 ms, p90 300 ms")
    window.add(20)
    validate(window.summary() == "son 3 ağ çağrısı: p50 200 ms, p90 300 ms")
    window.clear()
    validate(window.summary() == null)
    println("PASS $checks/31 GameLingo 10.37 live-policy, meaning & latency checks")
}
