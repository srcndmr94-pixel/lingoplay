package com.example.altyazi

/**
 * 10.42.5 / APK-299 saha kaydına dayalı dar kapsamlı OCR ve çıktı düzeltmeleri.
 * Buradaki kurallar motor seçmez, yeni altyazı üretmez ve bilinmeyen
 * eksik kelimeleri tahmin etmez. Sadece iki tarafta da açık kanıt bulunan
 * teknik terim/parçalanma sorunlarına dokunur.
 */
object GameplayLogRepair299 {
    private val ocrFixes = listOf(
        // Her kural yalnız kayıtla doğrulanan cümle parçasında çalışır.
        Regex("^Ata time(?=\\s+this\\s+dangerous\\b)", RegexOption.IGNORE_CASE) to "At a time",
        Regex("\\bsouls\\s+of\\s+our\\s+fatten\\b", RegexOption.IGNORE_CASE) to "souls of our fallen",
        Regex("\\bretun(?=\\s+to\\s+the\\s+Blight\\b)", RegexOption.IGNORE_CASE) to "return",
        Regex("\\bSha[ -]Chi\\s+unleased\\b", RegexOption.IGNORE_CASE) to "Sha-Chi unleashed",
        Regex("\\bunieasned\\s+otter(?=\\s+any\\s+swing\\b)", RegexOption.IGNORE_CASE) to "unleashed after",
        Regex("\\bStandand(?=\\s+Combo\\b|\\s*$)", RegexOption.IGNORE_CASE) to "Standard",
        Regex("\\btem\\s+per(?=\\s+this\\s+sword['’]s\\s+soul\\b)", RegexOption.IGNORE_CASE) to "temper",
        Regex("\\bIH(?=\\s+can['’]t\\s+explain\\s+it\\b)", RegexOption.IGNORE_CASE) to "I",
        Regex("^I['’]mused(?=\\s+to\\s+the\\s+wind\\b)", RegexOption.IGNORE_CASE) to "I'm used",
        // 15 Kasım APK-299 ikinci saha kaydı: düzeltmeler yalnız açık bağlamda.
        Regex("\\bTranscended\\s+heaters\\b", RegexOption.IGNORE_CASE) to "Transcended healers",
        Regex("^Xie\\s+iudong(?=\\s+[1Il]gave\\b)", RegexOption.IGNORE_CASE) to "Xie Qiudong",
        Regex("\\b1gave(?=\\s+her\\s+more\\s+time\\b)", RegexOption.IGNORE_CASE) to "I gave",
        Regex("\\bevershouldve(?=\\s+had[lI]?[.!]?\\s*$)", RegexOption.IGNORE_CASE) to "ever should've",
        Regex("(?<=should\'ve\\s)hadl\\s*$", RegexOption.IGNORE_CASE) to "had!",
        Regex("^Enpthe\\s+liew\\s+Meditine\\?$", RegexOption.IGNORE_CASE) to "Equip the new Medicine?",
        Regex("\\bpro\\s+longed(?=\\s+sleep\\b)", RegexOption.IGNORE_CASE) to "prolonged",
        // 10.42.7 gercek logu: yalniz acik baglamdaki bozuk OCR bitisleri.
        Regex("\\bThread\\s+Binding\\s+Sectwould\\b", RegexOption.IGNORE_CASE) to "Thread Binding Sect would",
        Regex("\\bdevicesthey\\b", RegexOption.IGNORE_CASE) to "devices they",
        Regex("^Go!\\s*T[Il1]l(?=\\s+hold\\s+them\\s+off)", RegexOption.IGNORE_CASE) to "Go! I'll",
        Regex("\\bnot\\s+alonel\\b", RegexOption.IGNORE_CASE) to "not alone!",
        Regex("\\bJunior\\s+Brothero\\b", RegexOption.IGNORE_CASE) to "Junior Brother",
        Regex("\\bmart\\s+I\\s+age(?=\\s+will\\s+fall\\s+through)", RegexOption.IGNORE_CASE) to "marriage",
        Regex("\\bXiu['’]sa\\b", RegexOption.IGNORE_CASE) to "Xiu's a",
        Regex("\\bdontcome\\s+back\\b", RegexOption.IGNORE_CASE) to "don't come back",
        Regex("\\bIdontneedto\\b", RegexOption.IGNORE_CASE) to "I don't need to",
        Regex("\\bSenior\\s+Brother\\b", RegexOption.IGNORE_CASE) to "Senior Brother"
    )

    // Bunlar kaynak kaydinda gorulen basliklardir; genel bir "ilk uc kelime isim"
    // sezgisi kullanilmaz. Bu, "How could this be" gibi cumleleri bolmez.
    private val knownSpeakerPrefix = Regex(
        "(?i)^(Keen Mandate Disciple|Worldly Mandate Disciple|Lone Mandate Disciple)" +
            "\\s+(?=I\\b|Hmph\\b|Thread[- ]Binding\\b|How\\b|Ah\\b)"
    )

    fun repairOcr(text: String): String {
        var fixed = text
        for ((pattern, replacement) in ocrFixes) fixed = pattern.replace(fixed, replacement)
        fixed = knownSpeakerPrefix.replace(fixed) { "[${it.groupValues[1]}] " }
        return fixed
    }

    /** Bir sonraki OCR karesini bekle; bozuk metni çevirip hafızaya yazma. */
    fun waitForCleanReading(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        // Bunlar gerçek saha logunda kopmuş veya ağır biçimde bozulmuştu.
        if (Regex("\\b(?:witti\\s+enogi|jtfarkwith|hdtd\\s+bieak|ineroa|inrrea)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(t)) return true
        if (Regex("\\bcharge\\s+thg\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(t)) return true
        if (Regex("\\bcan be unleashed after any swing during a Standard\\s*$", RegexOption.IGNORE_CASE)
                .containsMatchIn(t)) return true
        // Bozuk menü yazısını veya sözcüklerin parçalanmış hâlini normal
        // bir replik olarak çevirmeyelim; sonraki temiz OCR okumasını bekle.
        if (Regex("^(?:Traverse\\s+Ungrade\\s+Equipnjenl|sys\\s+Tems\\.|nevertr\\s+UIy\\s+runs\\s+dry\\.?)$", RegexOption.IGNORE_CASE)
                .containsMatchIn(t)) return true
        // Gerçek yetenek metni, ancak OCR'nin yarım bıraktığı son kelimeyi uyduramayız.
        if (Regex("\\b(?:deal|restore)\\s+ineroa\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(t)) return true
        return false
    }

    /** Süsleme süzgeci teknik oyun açıklamalarını çöp sanmamalı. */
    fun isGameplayInstruction(text: String): Boolean =
        Regex("^\\s*(?:These attacks convert HP|Press after dodging|Reduces damage taken|Can be unleashed after|Switching to this weapon|The Order maintains a tavern)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)

    /** Hatalı OCR'den sahte konuşmacı öğrenilmesini azaltır. */
    fun plausibleSpeakerName(name: String): Boolean {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 4 || name.length > 32) return false
        // 16 Kasım canlı kaydı: "Hey." bir kez eksik OCR gelince konuşan
        // adı sanılıp "KONUŞAN ADI ÖĞRENİLDİ: Hey" olarak saklanmıştı.
        // Selam/ünlemler tek başına asla karakter adı değildir.
        if (words.size == 1 && words[0].trimEnd('.', ',', '!', '?').lowercase() in
            setOf("hey", "yo", "hello", "hi", "yeah", "wow", "huh", "well", "oh", "okay", "what")) return false
        // "WU Xra H" gibi tek harfli OCR kırıntılarını ada dönüştürme.
        return words.all { w ->
            val cleaned = w.trimEnd('.')
            cleaned.length >= 2 && cleaned.first().isUpperCase() &&
                cleaned.all { it.isLetter() || it == '\'' || it == '’' }
        }
    }

    /** Tekrar eden oyun menüsü etiketleri. Sadece tam eşleşmeye uygulanır. */
    fun verifiedUiLabel(source: String): String? = when (source.trim()) {
        "Soul Temper Leave" -> "Ruh Tavlama · Ayrıl"
        "8 Soul Temper" -> "8 Ruh Tavlama"
        "Upgrade Medicine Leave" -> "İlacı geliştir · Ayrıl"
        "Controller Preset 1" -> "Kontrol düzeni 1"
        "Equip the new Medicine?" -> "Yeni ilacı kuşanmak istiyor musun?"
        else -> null
    }

    /** Kaynak iki kelimelik oyun terimiyse modelin ürettiği yapay 've' bağını düzelt. */
    fun repairTranslation(source: String, translated: String): String {
        var out = translated
        if (Regex("\\bBronze Essence\\b", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            out = out.replace(Regex("\\bBronze\\s+ve\\s+Essence(?:['’](?:ü|u|i|yi|yü))?", RegexOption.IGNORE_CASE), "Bronze Essence'i")
        }
        if (Regex("\\bSha[ -]Chi\\b", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            out = out.replace(Regex("\\bSha\\s+ve\\s+Chi(?:['’](?:ü|u|i|yi|yü))?", RegexOption.IGNORE_CASE), "Sha-Chi'yi")
        }
        if (Regex("\\bPerfect Transcension\\b", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            out = out.replace(Regex("\\bPerfect\\s+ve\\s+Transcension\\b", RegexOption.IGNORE_CASE), "Perfect Transcension")
        }
        // Bu kalıplar yalnız İngilizce kaynakta ilgili tam terim varsa
        // devreye girer. İsimleri çevirmeyiz, cümledeki Türkçe ekleri koruruz.
        if (source.contains(Regex("\\bHollow Heart\\b", RegexOption.IGNORE_CASE))) {
            out = out.replace(Regex("\\bHollow\\s+ve\\s+Heart(?:['’](?:yi|yı|i|ı|ü|u))?", RegexOption.IGNORE_CASE), "Hollow Heart'i")
        }
        if (source.contains(Regex("\\bXie Huichun\\b", RegexOption.IGNORE_CASE))) {
            out = out.replace(Regex("\\bXie\\s+ve\\s+Huichun\\b", RegexOption.IGNORE_CASE), "Xie Huichun")
        }
        if (source.contains(Regex("\\bMeigu Lake\\b", RegexOption.IGNORE_CASE))) {
            out = out.replace(Regex("\\bMeigu\\s+ve\\s+Lake(?:['’](?:in|nin|ı|i))?", RegexOption.IGNORE_CASE), "Meigu Lake'in")
        }
        if (source.contains(Regex("\\bWhite Serpent House\\b", RegexOption.IGNORE_CASE))) {
            out = out.replace(Regex("\\bWhite[, ]+Serpent\\s+ve\\s+House\\b", RegexOption.IGNORE_CASE), "White Serpent House")
        }
        if (source.contains(Regex("\\bPhantom Edge\\b", RegexOption.IGNORE_CASE))) {
            out = out.replace(Regex("\\bPhantom\\s+(?:ile|ve)\\s+Edge\\b", RegexOption.IGNORE_CASE), "Phantom Edge")
        }
        if (source.trimStart().startsWith("Third Stage", ignoreCase = true)) {
            out = out.replace(Regex("^Third Stage(?=[:\\s])", RegexOption.IGNORE_CASE), "Üçüncü evre")
        }
        // 10.42.7: iki ad / oyun terimi, tek tek kelimeye parcalanmasin.
        if (source.contains(Regex("\\bChang Hong and Lei Dang\\b", RegexOption.IGNORE_CASE))) {
            out = out.replace(Regex("\\bChang[,]?\\s+Hong\\s+ile\\s+Lei[,]?\\s+Dang\\b", RegexOption.IGNORE_CASE), "Chang Hong ve Lei Dang")
        }
        if (source.contains(Regex("\\bHeart-Carving Art\\b", RegexOption.IGNORE_CASE))) {
            out = out.replace(Regex("\\bHeart-Carving\\s+(?:ve|ile)\\s+Art\\b", RegexOption.IGNORE_CASE), "Heart-Carving Art")
        }
        if (source.contains(Regex("\\bMirage Estate\\b", RegexOption.IGNORE_CASE))) {
            out = out.replace(Regex("\\bMirage\\s+ve\\s+Estate\\b", RegexOption.IGNORE_CASE), "Mirage Estate")
        }
        if (source.contains(Regex("\\bSenior Brother\\b", RegexOption.IGNORE_CASE))) {
            out = out.replace(Regex("\\bSenior[,]?\\s+Brother\\b", RegexOption.IGNORE_CASE), "Senior Brother")
        }
        if (source.contains(Regex("\\bJunior Brother\\b", RegexOption.IGNORE_CASE))) {
            out = out.replace(Regex("\\bJunior[,]?\\s+Brother\\b", RegexOption.IGNORE_CASE), "Junior Brother")
        }
        return out
    }
}
