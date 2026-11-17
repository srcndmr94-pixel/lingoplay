#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parent.parent
src=root/"app/src/main/java/com/example/altyazi"
def replace(file,old,new):
    p=src/file
    s=p.read_text(encoding="utf-8")
    if s.count(old)!=1: raise RuntimeError(f"Fix4 patch anchor mismatch {file}: {s.count(old)}")
    p.write_text(s.replace(old,new,1),encoding="utf-8")
replace("CaptureService.kt",
    "if (OfflineFix.isNoiseLine(text)) { rejectedNoise++; continue }",
    "if (OfflineFix.isNoiseLine(text) || UiNoiseGuard202.isNonDialogue(text)) { rejectedNoise++; continue }")
replace("CaptureService.kt",
    'if (arayuzYazisi(text) || turkceYazi(text)) return ""',
    'if (arayuzYazisi(text) || turkceYazi(text) || UiNoiseGuard202.isNonDialogue(text)) return ""')
replace("CaptureService.kt",
    "GÖRÜNTÜ GELMİYOR: ",
    "YENİ KARE ALINMADI (ekran sabit olabilir): ")

guard=r'''package com.example.altyazi
import java.util.Locale

/** Conservative UI-title rejection; keep ordinary game dialogue untouched. */
object UiNoiseGuard202 {
    private val tokenPattern = Regex("""[\p{L}\p{N}]+""")
    private val turkishUiWords = setOf(
        "kasımda", "kasimda", "tüm", "tum", "erkekler", "yeni",
        "yayında", "yayinda", "izle", "şimdi", "simdi", "fragman",
        "bölüm", "bolum", "abonelik", "bildirimler", "görüntüleme", "goruntuleme"
    )
    private val englishWords = setOf(
        "the", "they", "i", "you", "he", "she", "we", "it", "are",
        "was", "were", "this", "that", "what", "with", "for", "not"
    )
    private val mediaTitleIndicators = listOf(
        Regex("""(?i)\bps5(?:\s+pro)?\b"""),
        Regex("""(?i)\bsony\b"""),
        Regex("""(?i)\banniversary\b"""),
        Regex("""(?i)\bgameplay\b"""),
        Regex("""(?i)\b(?:trailer|unboxing|review|showcase)\b"""),
        Regex("""(?i)\b\d+(?:st|nd|rd|th)\b""")
    )

    fun isNonDialogue(text: String): Boolean {
        val t = text.trim()
        if (t.length < 12) return false
        val tokens = tokenPattern.findAll(t.lowercase(Locale.ROOT)).map { it.value }.toList()
        // "ni KASIMDA TÜM ERKEKLER... Yeni": already Turkish UI, not English dialog.
        if (tokens.count { it in turkishUiWords } >= 3 &&
            tokens.none { it in englishWords }) return true
        // "IT DIED. My PS5 Pro Died, Sony Replaced It! 30th Anniversary."
        // A single brand name alone must never cause rejection.
        if (t.length >= 48 && mediaTitleIndicators.count { it.containsMatchIn(t) } >= 2) return true
        return false
    }
}
'''
(src/"UiNoiseGuard202.kt").write_text(guard,encoding="utf-8")
test=r'''package com.example.altyazi
import org.junit.Assert.*
import org.junit.Test

class GameLingo202UiNoiseFix4Test {
    @Test fun observedVideoTitleIsSkipped() {
        assertTrue(UiNoiseGuard202.isNonDialogue(
            "IT DIED. My PS5 Pro Died, Sony Replaced It! 30th Anniversary."))
    }
    @Test fun observedTurkishUiIsSkipped() {
        assertTrue(UiNoiseGuard202.isNonDialogue("ni KASIMDA TÜM ERKEKLER... Yeni"))
    }
    @Test fun ordinaryDialogueIsKept() {
        assertFalse(UiNoiseGuard202.isNonDialogue("My PS5 Pro is broken."))
        assertFalse(UiNoiseGuard202.isNonDialogue("Yo! Crackheads! Woo!"))
        assertFalse(UiNoiseGuard202.isNonDialogue("No. Y'all know Boobie?"))
        assertFalse(UiNoiseGuard202.isNonDialogue(
            "I never get to smoke these in front of Lori."))
        assertFalse(UiNoiseGuard202.isNonDialogue(
            "He wants it on the quiet. He only wants one of you."))
        assertFalse(UiNoiseGuard202.isNonDialogue("It's fucking Méndez!"))
        assertFalse(UiNoiseGuard202.isNonDialogue("You met Yeni last night."))
    }
}
'''
p=root/"app/src/test/java/com/example/altyazi/GameLingo202UiNoiseFix4Test.kt"
p.parent.mkdir(parents=True,exist_ok=True)
p.write_text(test,encoding="utf-8")
print("Fix4 patch applied.")
