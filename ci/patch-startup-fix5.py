#!/usr/bin/env python3
"""Fix5: diagnose DeepL cold-start failures; prevent severely incomplete OPUS output."""
from pathlib import Path
root=Path(__file__).resolve().parent.parent
src=root/"app/src/main/java/com/example/altyazi"
def replace(file,old,new):
    p=src/file
    code=p.read_text(encoding="utf-8")
    if code.count(old)!=1: raise RuntimeError(f"Fix5 anchor mismatch {file}: {code.count(old)}")
    p.write_text(code.replace(old,new,1),encoding="utf-8")

replace("CaptureService.kt",
    "private val deepLAccepted = java.util.concurrent.atomic.AtomicInteger(0)",
    "private val deepLStartupAttempts = java.util.concurrent.atomic.AtomicInteger(0)\n    private val deepLAccepted = java.util.concurrent.atomic.AtomicInteger(0)")
replace("CaptureService.kt",
    "DeepLCeviri.resetSession()",
    "DeepLCeviri.resetSession()\n        deepLStartupAttempts.set(0)")
replace("CaptureService.kt",
    '''withTimeoutOrNull(1400L) { DeepLCeviri.translate(text, key, previous) }''',
    '''val budgetMs = DeepLStartPolicy202.timeoutForAttempt(deepLStartupAttempts.getAndIncrement())
            val t0 = android.os.SystemClock.elapsedRealtime()
            val translated = withTimeoutOrNull(budgetMs) { DeepLCeviri.translate(text, key, previous) }
            if (translated.isNullOrBlank()) {
                val elapsed = android.os.SystemClock.elapsedRealtime() - t0
                val reason = if (elapsed >= budgetMs - 30L) "zaman aşımı" else DeepLCeviri.lastStatus
                log("DEEPL BAŞARISIZ: " + elapsed + "ms · sebep=" + reason + " · OPUS-MT yedeğine geçiliyor")
            }
            translated''')
replace("CaptureService.kt",
    '''!MeaningSafety.lostContextualProfanity(rawBody, restored)''',
    '''!MeaningSafety.lostContextualProfanity(rawBody, restored) &&
                    !LiveResultCompleteness202.isTruncated(rawBody, restored)''')
replace("LiveSubtitleRepair202.kt",
    '''"clean this shit up" -> "Şu boku temizle!"''',
    '''"yo! crackheads! woo" -> "Hey! Keşler! Vay be!"
        "clean this shit up" -> "Şu boku temizle!"''')
replace("DeepLCeviri.kt",
    '''if (!DeepLPolicy.canRequest(source, key) || System.currentTimeMillis() < nextAllowed) return null''',
    '''if (!DeepLPolicy.canRequest(source, key)) {
            lastStatus = "istek yapılamadı (anahtar veya metin koşulu)"
            return null
        }
        if (System.currentTimeMillis() < nextAllowed) {
            lastStatus = "API bekleme süresi (önceki HTTP kısıtı)"
            return null
        }''')

policy = r'''package com.example.altyazi

/**
 * DeepL's OkHttp client itself has a 1800ms call timeout.
 * The old 1400ms outer timeout killed cold-start requests before that
 * network timeout could report its reason.
 */
object DeepLStartPolicy202 {
    fun timeoutForAttempt(attempt: Int): Long =
        if (attempt in 0..1) 1850L else 1400L
}

/** A full three-part English subtitle must not collapse into a single Turkish word. */
object LiveResultCompleteness202 {
    private val words = Regex("""\p{L}+""")
    fun isTruncated(source: String, translated: String): Boolean {
        val parts = Regex("""[!?]+""").findAll(source).count()
        if (parts < 3) return false
        val uniqueSourceWords = words.findAll(source).map { it.value.lowercase() }.distinct().count()
        if (uniqueSourceWords < 3) return false
        return words.findAll(translated).count() <= 1
    }
}
'''
(src/"LiveResultCompleteness202.kt").write_text(policy,encoding="utf-8")

test=r'''package com.example.altyazi

import org.junit.Assert.*
import org.junit.Test

class GameLingo202Fix5Test {
    @Test fun coldStartGetsTheFullHttpTimeout() {
        assertEquals(1850L, DeepLStartPolicy202.timeoutForAttempt(0))
        assertEquals(1850L, DeepLStartPolicy202.timeoutForAttempt(1))
        assertEquals(1400L, DeepLStartPolicy202.timeoutForAttempt(2))
    }
    @Test fun sourceVerifiedCatchphraseAvoidsBrokenOpus() {
        assertEquals("Hey! Keşler! Vay be!",
            LiveSubtitleRepair202.verifiedTranslation("Yo! Crackheads! Woo!"))
    }
    @Test fun badOpusDropsMostClauses() {
        assertTrue(LiveResultCompleteness202.isTruncated("Yo! Crackheads! Woo!", "Çatlaklar!"))
        assertFalse(LiveResultCompleteness202.isTruncated("Yo! Crackheads! Woo!", "Hey! Keşler! Vay be!"))
    }
    @Test fun singleWordShortDialoguesStayAllowed() {
        assertFalse(LiveResultCompleteness202.isTruncated("What?", "Ne?"))
        assertFalse(LiveResultCompleteness202.isTruncated("Go! Go! Go!", "Git!"))
        assertFalse(LiveResultCompleteness202.isTruncated("Boobie, huh? Yeah, he ain't here, though.",
            "Boobie, ha? Evet ama burada değil."))
    }
}
'''
path=root/"app/src/test/java/com/example/altyazi/GameLingo202Fix5Test.kt"
path.parent.mkdir(parents=True,exist_ok=True)
path.write_text(test,encoding="utf-8")
print("Fix5: explicit DeepL fallback reason, startup timeout, short phrase quality guard.")
