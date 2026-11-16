package com.example.altyazi

/**
 * Conservative self-overlay language signal, *not* a translation engine.
 * A single word that exists in both languages (Ben, bir, ama, etc.)
 * cannot justify deleting a captured English OCR subtitle.
 * The exact displayed translation is separately removed by CaptureService.
 */
object OwnTextLanguage299 {
    private const val TURKISH_CHARACTERS = "çğıöşüÇĞİÖŞÜ"
    private val turkishWords = setOf(
        "bir", "bu", "şu", "için", "ile", "çok", "daha", "var", "yok",
        "değil", "ama", "gibi", "kadar", "sonra", "önce", "şey", "evet",
        "hayır", "tamam", "devam", "edin", "halinde", "abone", "izlemeye",
        "ve", "veya", "ben", "sen", "biz", "siz", "onlar", "bizim",
        "senin", "benim", "nasıl", "neden", "nerede", "kim", "hangi",
        "oldu", "olur", "yapıyor", "geldi", "gitti", "dedi", "bilmiyorum"
    )
    private val englishWords = setOf(
        "a", "an", "the", "and", "or", "but", "to", "of", "for", "with", "from",
        "i", "me", "my", "you", "your", "he", "she", "it", "we", "they",
        "is", "are", "was", "were", "be", "been", "do", "did", "does",
        "have", "has", "had", "can", "could", "will", "would", "not",
        "this", "that", "there", "here", "how", "what", "where", "who"
    )

    fun looksTurkish(text: String): Boolean {
        // Distinct Turkish glyphs are a strong signal; exact overlay text is
        // separately handled even when OCR strips all diacritics.
        if (text.any { it in TURKISH_CHARACTERS }) return true
        val words = Regex("[\\p{L}]+").findAll(text.lowercase())
            .map { it.value }.toList()
        if (words.size < 2 || words.any { it in englishWords }) return false
        return words.count { it in turkishWords } >= 2
    }
}
