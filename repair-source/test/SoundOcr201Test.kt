package com.example.altyazi
fun main() {
  val samples = listOf(
    "Let him go! - Brandyworth shouts]" to "Let him go! - [Brandyworth shouts]",
    "I shouting - [Phase Three snarlingl" to "[shouting] - [Phase Three snarling]",
    "I am shouting at you." to "I am shouting at you.",
    "I shout. I am happy." to "I shout. I am happy.",
    "[Eve groaning]" to "[Eve groaning]",
    "Yo, what the fuck?" to "Yo, what the fuck?",
    "Y'all tryna smoke?" to "Y'all tryna smoke?",
    "What a treat." to "What a treat.",
    "[Erickson] Brandyworth." to "[Erickson] Brandyworth."
  )
  samples.forEach { (source, expected) ->
    check(OcrSoundCue200.repair(source) == expected) { "Altered ordinary speech: $source" }
  }
  println("SOUND OCR 201: ${samples.size}/${samples.size} PASS")
}
