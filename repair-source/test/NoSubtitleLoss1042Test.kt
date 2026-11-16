import com.example.altyazi.DialogueDisambiguation1038

fun main() {
    val cases = listOf(
        Triple("You guys are the best. Thank you.", "You'dekiler en iyisidir. Teşekkür ederim.", "Siz harikasınız. Teşekkür ederim."),
        Triple("[Zoe] You guys are the best.", "[Zoe] You'dekiler en iyisidir.", "[Zoe] Siz harikasınız."),
        Triple("I got big shoes to fill. This will be hard.", "Doldurmam gereken büyük bir yer var. Bu zor olacak.", "Benden öncekinin yerini doldurmalıyım. Bu zor olacak."),
        Triple("And just spitballing here, we could leave now.", "Ve burada sadece tükürük topları, şimdi gidebiliriz.", "Ve öylesine fikir yürütüyorum, şimdi gidebiliriz."),
        Triple("You guys are the best.", "You'dekiler en iyisidir.", "Siz harikasınız."),
        Triple("and just spitballing here", "Ve burada sadece tükürük topları.", "Öylesine fikir yürütüyorum."),
        Triple("I got big shoes to fill.", "Doldurmam gereken büyük bir yer var.", "Benden öncekinin yerini doldurmalıyım."),
        Triple("You guys are the best. Thank you.", "Siz harikasınız. Teşekkür ederim.", "Siz harikasınız. Teşekkür ederim."),
        Triple("What are your orders?", "Siparişleriniz neler?", "emirleriniz neler?")
    )
    for ((source, translation, expected) in cases) {
        val actual = DialogueDisambiguation1038.repair(source, translation)
        check(actual == expected) { "$source => $actual (expected $expected)" }
    }
    println("9/9 additional anti-truncation checks PASS")
}
