import com.example.altyazi.OwnTextLanguage299

fun main() {
    val notOwn = listOf(
        "Ben is here. Hurry!", "Ben, can you help me?",
        "You were there, Ben.", "The old man came back.",
        "Sacrifice a few to save the many.", "Wait!", "Get out!",
        "Soul Could I replace my poisoned heart?"
    )
    val own = listOf("Teşekkürler.", "Bu bir oyun.", "Evet, tamam", "Ben ve sen")
    notOwn.forEach { check(!OwnTextLanguage299.looksTurkish(it)) { "English discarded: $it" } }
    own.forEach { check(OwnTextLanguage299.looksTurkish(it)) { "Turkish missed: $it" } }
    println("${notOwn.size + own.size}/${notOwn.size + own.size} OwnTextLanguage299 checks PASS")
}
