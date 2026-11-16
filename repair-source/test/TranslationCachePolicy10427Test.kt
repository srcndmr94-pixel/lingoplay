package com.example.altyazi
fun main() {
  val cases=listOf(
    "Wet." to false,
    "You know." to false,
    "What's up?" to false,
    "No. No." to false,
    "I don't think so." to true,
    "I never said that." to true,
    "What does that mean?" to true,
    "but under the control of" to false,
    "[Eve] I need to see this." to true,
    "[Eve] Really?" to false,
    "You gonna leave them squirming" to false,
    "I can do things that matter..." to false,
    "They came back because" to false
  )
  cases.forEach{ (src, expected) ->
    check(TranslationCachePolicy10427.reusable(src)==expected){"$src: expected $expected"}
  }
  println("CONTEXTUAL CACHE: ${cases.size}/${cases.size} PASS")
}
