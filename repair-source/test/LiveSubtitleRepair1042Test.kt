import com.example.altyazi.TranslationQualityGate
import com.example.altyazi.SubtitlePreparation
import com.example.altyazi.DialogueDisambiguation1038

fun main() {
    val cases = listOf(
        Triple("[Nolan] Mark?", "[Nolan] İşaret?", "[Nolan] Mark?"),
        Triple("[Nolan] Mark? [shuddering]", "[Nolan] İşaret? [ürperiyor]", "[Nolan] Mark? [ürperiyor]"),
        Triple("You guys are the best.", "You'dekiler en iyisidir.", "Siz harikasınız."),
        Triple("and just spitballing here", "Ve burada sadece tükürük topları.", "Öylesine fikir yürütüyorum."),
        Triple("I got big shoes to fill.", "Doldurmam gereken büyük bir yer var.", "Benden öncekinin yerini doldurmalıyım."),
        Triple("Now, you look regal.", "Şimdi, çok güzel görünüyorsun.", "Şimdi, heybetli görünüyorsun."),
        Triple("Please mark the sign.", "Lütfen işareti işaretleyin.", "Lütfen işareti işaretleyin."),
        Triple("Are you happy?", "Mutlu musun?", "Mutlu musun?")
    )
    for ((source, actual, expected) in cases) {
        val repaired = DialogueDisambiguation1038.repair(source, actual)
        check(repaired == expected) { "$source -> '$repaired' expected '$expected'" }
    }
    val ocrCases = listOf(
        "[beeps] -[Zoel You helped save the galaxy" to "[beeps] -[Zoe] You helped save the galaxy",
        "It's not generally what Vitrumites like to, uh.." to "It's not generally what Viltrumites like to, uh..",
        "Here's break fast from a tube." to "Here's breakfast from a tube.",
        "We break fast at noon." to "We break fast at noon.",
        "[Nolan] Mark? [shuddering]" to "[Nolan] Mark? [shuddering]"
    )
    for ((source, expected) in ocrCases) {
        val output = SubtitlePreparation.repair(source)
        check(output == expected) { "OCR '$source' -> '$output' expected '$expected'" }
    }
    check(TranslationQualityGate.check("And I fucking jumped.", "Ve ben de atladım.") == TranslationQualityGate.Problem.LOST_PROFANITY)
    check(TranslationQualityGate.check("And I fucking jumped.", "Ve ben de atladım, amına koyayım.") == null)
    println("15/15 GameLingo 10.42 targeted regression checks PASS")
}
