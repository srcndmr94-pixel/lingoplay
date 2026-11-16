package com.example.altyazi

/**
 * Telefonun çevirisinden ÇIKAN Türkçeyi düzeltir.
 *
 * Cihaz üstü çeviri motoru hızlı ve sınırsız, ama iki şeyi düzenli olarak
 * yanlış yapıyor; ikisi de kullanıcının kaydında görüldü:
 *
 *  1. Soru eki: "Do I know that name?" -> "Bu ismi biliyorum mi?"
 *     Doğrusu "biliyor muyum". Ek fiilden sonra gelmeli, kişi eki de ekin
 *     üstüne binmeli.
 *
 *  2. Köşeli parantezli ses etiketleri: "[dramatic music playing]" ->
 *     "[Dramatik Müzik Oyna Ingl". Bunlar cümle değil, sahne notu; kelime
 *     kelime çevrilince saçmalıyor. Kendi tablomuzdan karşılıyoruz.
 */
/**
 * Türkçede anlamı olan İngilizce özel isimler.
 *
 * "I miss you, May."    -> hızlı çeviri "Seni özledim, mayıs."
 * "they called the Sage" -> hızlı çeviri "Adaçayı dedikleri"   (kullanıcının kaydından)
 *
 * Kaynakta cümle başı DIŞINDA büyük harfle geçiyorsa kelime isimdir; çeviride
 * Türkçeleşmiş hâlini bulup İngilizce ismi geri koyuyoruz. Kullanıcının kuralı:
 * isimler çevrilmez.
 *
 * Liste bilerek adlarla sınırlı. "Brown", "Young", "Little" gibi sıfat soylu
 * soyadları dışarıda: bazı oyunlar cümle içinde her adı büyük harfle yazıyor,
 * o yüzden "the Brown door" -> "Brown kapı" gibi ters tepmeler olurdu.
 */
object NameFix {
    /**
     * Türkçe çekim ekleri: "adaçayını", "adaçayına", "gülün", "gülleri" de yakalansın.
     * Sıra: kaynaştırma harfi (n/y) + çoğul (ler/lar) + hâl eki. Hepsi isteğe bağlı.
     */
    private const val EK =
        "(?:['’]?[ny]?(?:l[ae]r)?(?:n?[ıiuü]n|[ıiuü]|[ae]|[dt][ae]n?)?)?"

    /** İngilizce isim -> telefon çevirisinin ürettiği Türkçe karşılık(lar). */
    private val KARSILIKLAR: List<Pair<String, List<String>>> = listOf(
        // aylar
        "Eve" to listOf("havva"),
        "May" to listOf("mayıs"), "June" to listOf("haziran"),
        "April" to listOf("nisan"), "August" to listOf("ağustos"),
        // kullanıcının kaydından: hızlı çeviri "adaçayı", yapay zekâ "bilge" diyordu;
        // ikisi de aynı satırda görünüp yanıp sönüyordu. İkisi de ada çevrilir.
        "Sage" to listOf("adaçayı", "adaçay", "bilge"),
        // soyut adlar
        "Hope" to listOf("umut", "ümit"), "Faith" to listOf("inanç", "iman"),
        "Grace" to listOf("zarafet", "lütuf", "inayet"), "Joy" to listOf("neşe", "sevinç"),
        "Angel" to listOf("melek"), "Dawn" to listOf("şafak"),
        "Victor" to listOf("galip"), "Noble" to listOf("asil"),
        "Savage" to listOf("vahşi"), "Swift" to listOf("hızlı"),
        "Price" to listOf("fiyat"), "Page" to listOf("sayfa"),
        // mevsim ve doğa
        "Summer" to listOf("yaz"), "Autumn" to listOf("sonbahar"), "Winter" to listOf("kış"),
        "Storm" to listOf("fırtına"), "River" to listOf("nehir"), "Brook" to listOf("dere"),
        "Star" to listOf("yıldız"), "Cliff" to listOf("uçurum"), "Hill" to listOf("tepe"),
        "Forest" to listOf("orman"), "Stone" to listOf("taş"), "Flint" to listOf("çakmaktaşı"),
        "Ash" to listOf("kül"),
        // bitkiler
        "Rose" to listOf("gül"), "Lily" to listOf("zambak"), "Violet" to listOf("menekşe"),
        "Daisy" to listOf("papatya"), "Ivy" to listOf("sarmaşık"), "Willow" to listOf("söğüt"),
        "Holly" to listOf("çobanpüskülü"), "Olive" to listOf("zeytin"),
        "Basil" to listOf("fesleğen"), "Rosemary" to listOf("biberiye"),
        "Heather" to listOf("funda"), "Iris" to listOf("süsen"), "Jasmine" to listOf("yasemin"),
        "Poppy" to listOf("gelincik"), "Pepper" to listOf("biber"), "Clover" to listOf("yonca"),
        "Fern" to listOf("eğreltiotu"), "Ginger" to listOf("zencefil"),
        // taşlar ve madenler
        "Pearl" to listOf("inci"), "Ruby" to listOf("yakut"), "Amber" to listOf("kehribar"),
        "Hazel" to listOf("fındık"), "Crystal" to listOf("kristal"), "Jade" to listOf("yeşim"),
        "Silver" to listOf("gümüş"), "Steel" to listOf("çelik"), "Diamond" to listOf("elmas"),
        // hayvanlar
        "Fox" to listOf("tilki"), "Wolf" to listOf("kurt"), "Bear" to listOf("ayı"),
        "Hawk" to listOf("şahin"), "Eagle" to listOf("kartal"),
        "Robin" to listOf("kızılgerdan"), "Wren" to listOf("çalıkuşu"),
        // meslek soyadları
        "Hunter" to listOf("avcı"), "Baker" to listOf("fırıncı"), "Cook" to listOf("aşçı"),
        "Miller" to listOf("değirmenci"), "Smith" to listOf("demirci"),
        "Fisher" to listOf("balıkçı"), "Mason" to listOf("duvarcı"), "Archer" to listOf("okçu"),
        "Scout" to listOf("izci"),
        // lakaplar
        "Rusty" to listOf("paslı"), "Dusty" to listOf("tozlu"),
        "Sunny" to listOf("güneşli"), "Rocky" to listOf("kayalık"),
    )

    /**
     * Kelime sınırı için \b KULLANILMIYOR.
     *
     * Java'da \b, \w üzerinden tanımlı ve \w yalnızca İngiliz harflerini kapsıyor.
     * "adaçayı" sonundaki "ı" Java'ya göre harf değil; "\\badaçayı\\b" deseni bu
     * yüzden hiç eşleşmiyordu. Aynı şey "kış", "avcı", "gümüş" gibi onlarca
     * karşılık için de geçerliydi. \p{L} ile kendi sınırımızı kuruyoruz.
     */
    private const val ON = "(?<![\\p{L}\\p{N}])"
    private const val ARD = "(?![\\p{L}\\p{N}])"

    /**
     * Baş harfin büyüğü: çeviri cümle başında "Adaçayı", "Çakmaktaşı", "İnci"
     * diye dönüyor. Kotlin'in IGNORE_CASE'i Türkçe harfleri katlamadığı için
     * baş harfi iki hâliyle birden yazıyoruz. "i" için hem I hem İ gerekiyor.
     */
    private fun basHarf(k: Char): String = when (k) {
        'i' -> "[iIİ]"
        'ı' -> "[ıI]"
        else -> "[$k${k.uppercaseChar()}]"
    }

    /** "adaçayı" -> çekim ekleriyle birlikte yakalayan desen. Bir kez kurulur. */
    private val DESENLER: List<Pair<String, List<Regex>>> by lazy {
        KARSILIKLAR.map { (isim, karsiliklar) ->
            isim to karsiliklar.map { k ->
                val govde = basHarf(k.first()) + Regex.escape(k.substring(1))
                Regex("$ON$govde$EK$ARD", RegexOption.IGNORE_CASE)
            }
        }
    }

    /** Korunan İngilizce adların listesi (AiGuard denetiminde de kullanılıyor). */
    val korunanAdlar: List<String> by lazy { KARSILIKLAR.map { it.first } }

    /**
     * "Bu kelime gerçekten ad mı" denetiminin desenleri.
     *
     * İki şey düzeltildi:
     *  1. Regex.escape: ad DOĞRUDAN kalıba gömülüyordu. Bugünkü 77 adda
     *     metakarakter yok, ama "St. John" gibi bir ad eklenirse "." her
     *     harfi tutar ("Stx John" de eşleşir); "(" eklenirse kalıp hiç
     *     derlenmez ve satır çevirisi çalışma anında çöker.
     *  2. Önbellek: desenler her satırda yeniden derleniyordu. Artık bir
     *     kez kuruluyor (govde desenleri gibi).
     */
    private val AD_DENETIMI: Map<String, Pair<Regex, Regex>> by lazy {
        KARSILIKLAR.associate { (isim, _) ->
            val g = Regex.escape(isim)
            isim to Pair(
                Regex("(?<![.!?]\\s|^)(?<![\\p{L}\\p{N}])$g(?![\\p{L}\\p{N}])(?!\\s*\\d)"),
                Regex("^$g\\s*[:,!?.]"),
            )
        }
    }

    fun koru(kaynak: String, ceviri: String): String {
        val k = kaynak.trim()
        if (k.isEmpty()) return ceviri
        var t = ceviri
        for ((isim, desenler) in DESENLER) {
            // Hızlı eleme: isim kaynakta hiç geçmiyorsa desenleri çalıştırma.
            if (!k.contains(isim)) continue
            // Cümle başında değil, ardından sayı gelmiyor ("May 5" tarihtir) -> isim.
            val (d1, d2) = AD_DENETIMI[isim] ?: continue
            val isimMi = d1.containsMatchIn(k) || d2.containsMatchIn(k)
            if (!isimMi) continue
            for (d in desenler) t = d.replace(t, isim)
        }
        return buyukHarfiGeriKoy(k, etiketAdiniKoru(k, t))
    }

    /**
     * KÖŞELİ PARANTEZ İÇİNDEKİ KELİME ADDIR.
     *
     * "[Alien]", "[Kregg]", "[Nolan]", "[Thaedus]" konuşmacı etiketidir ve
     * çevrilmemeli. Kullanıcının 11 Kasım 9.66 kaydında yapay zekâ karakterin
     * adını çeviriyordu:
     *   "[Alien] Well, welcome to Talescria."  ->  "[YABANCI] Şey, ..."
     *   "Alien. You're alive."                 ->  "YABANCI. Hayattasın."
     * "alien" sözlükte gerçek bir kelime olduğu için ad kalkanı onu
     * yakalamıyor; ama köşeli parantez içinde geçmesi ad olduğunu söylüyor.
     */
    //
    // YALNIZ SATIR BAŞINDAKİ etiket. Konuşmacı etiketi satırın başında olur,
    // ses etiketi her yerde olabilir. Bu ayrımı yapmayan ilk hâlim satırdaki
    // BÜTÜN tek kelimelik etiketleri adla değiştiriyordu ve ses etiketini
    // yok ediyordu:
    //   kaynak "[Alien] Oh, shit! [grunts]"
    //   çeviri "[Yabancı] Ah, siktir! [homurtular]"
    //   bozuk  "[Alien] Ah, siktir! [Alien]"
    // En çok üç kelimelik ad: "[Grand Regent]", "[Space Racer]". Her kelime
    // büyük harfle başlamalı; "[Nolan sighs]" gibi ses kuyruğu ad değildir.
    private val ETIKET_ADI = Regex("^\\[(\\p{Lu}[\\p{L}'’]*(?: \\p{Lu}[\\p{L}'’]*){0,2})]")
    private val CEVIRI_ETIKETI = Regex("^\\[([^\\[\\]]{1,30})]")

    private fun etiketAdiniKoru(kaynak: String, ceviri: String): String {
        val ad = ETIKET_ADI.find(kaynak.trimStart())?.groupValues?.get(1) ?: return ceviri
        if (ad.length < 3) return ceviri
        val m = CEVIRI_ETIKETI.find(ceviri.trimStart()) ?: return ceviri
        val ic = m.groupValues[1]
        if (ic.equals(ad, ignoreCase = true)) return ceviri
        // Çevirideki etiket, kaynaktaki adla AYNI kelime sayısındaysa
        // konuşmacı adıdır ("[Büyük Regent]" <- "[Grand Regent]").
        // "[yumuşak müzik çalıyor]" gibi ses etiketine dokunulmaz: kaynak
        // tarafı zaten ETIKET_ADI kalıbına uymaz.
        if (ic.contains(',') || ic.split(' ').size != ad.split(' ').size) return ceviri
        val bas = ceviri.length - ceviri.trimStart().length
        return ceviri.substring(0, bas) + "[" + ad + "]" +
            ceviri.trimStart().substring(m.value.length)
    }

    /**
     * ÖZEL ADIN BÜYÜK HARFİ GERİ KONUR.
     *
     * Çeviri motoru tanımadığı büyük harfli kelimeyi küçültüyor. Kullanıcının
     * 11 Kasım 9.65 kaydından:
     *   "all remaining Viltrumites have been infected" -> "... viltrumitler ..."
     *   "It is the end of our race, Regent."           -> "... regent."
     *   "suitable for Viltrumite interbreeding"        -> "... viltrumite ..."
     *
     * Kural dar tutuluyor, yoksa cümle içindeki her kelimeyi büyütme riski var:
     *  - Kaynakta kelime CÜMLE BAŞINDA DEĞİL ve büyük harfle başlıyor olmalı
     *    (cümle başındaki büyük harf özel ad göstergesi değil).
     *  - Çeviride aynı GÖVDEYLE başlayan küçük harfli bir kelime olmalı
     *    (Türkçe ek alabilir: "viltrumitler", "regent'in").
     *  - Gövde en az 4 harf: "the", "I" gibi kelimeler karışmasın.
     */
    private fun buyukHarfiGeriKoy(kaynak: String, ceviri: String): String {
        var t = ceviri
        val kelimeler = Regex("[\\p{L}][\\p{L}'’]*").findAll(kaynak).toList()
        for ((i, m) in kelimeler.withIndex()) {
            val kelime = m.value
            if (kelime.length < 5) continue
            if (!kelime.first().isUpperCase()) continue
            // Cümle başı mı? Öncesinde harf yoksa ya da nokta varsa atla.
            val onceki = kaynak.substring(0, m.range.first).trimEnd()
            if (i == 0 || onceki.isEmpty() || onceki.last() in ".!?…") continue
            // Gövde: çoğul 's' ve sessiz 'e' atılır ("Viltrumites" -> "viltrumit").
            val kok = kelime.lowercase().trimEnd('s').trimEnd('e')
            if (kok.length < 4) continue
            t = Regex("(?<![\\p{L}])$kok([\\p{L}'’]*)").replace(t) { c ->
                // Zaten büyük harfliyse dokunulmaz.
                kok.substring(0, 1).uppercase(tr) + kok.substring(1) + c.groupValues[1]
            }
        }
        return t
    }

    private val tr = java.util.Locale("tr")
}

object TurkishFix {

    /**
     * Ses/sahne etiketleri. OCR bunları sık bozduğu için tam eşleşme
     * aramıyoruz; parçada geçen anahtar kelimeye bakıyoruz.
     */
    private val SES_IPUCLARI: List<Pair<String, String>> = listOf(
        "footstep" to "ayak sesleri",
        "gunshot" to "silah sesi",
        "gunfire" to "silah sesi",
        "explos" to "patlama",
        "whisper" to "fısıldayarak",
        "scream" to "çığlık",
        "shout" to "bağırarak",
        "yell" to "bağırarak",
        "groan" to "inleyerek",
        "grunt" to "homurdanarak",
        "sigh" to "iç çekerek",
        "laugh" to "gülerek",
        "chuckl" to "kıkırdayarak",
        "sob" to "ağlayarak",
        "cry" to "ağlayarak",
        "strain" to "zorlanarak",
        "pant" to "nefes nefese",
        "breath" to "nefes sesi",
        "thunder" to "gök gürültüsü",
        "siren" to "siren sesi",
        "engine" to "motor sesi",
        "applau" to "alkış",
        "clap" to "alkış",
        "knock" to "kapı vuruluyor",
        "door" to "kapı sesi",
        "phone" to "telefon çalıyor",
        "ring" to "zil sesi",
        "beep" to "bip sesi",
        "static" to "parazit",
        "music" to "müzik çalıyor",
    )

    /**
     * Satır bir ses etiketiyse Türkçe karşılığını döndürür, değilse null.
     * Böyle satırlar çeviri motoruna hiç gitmez: hem doğru çıkar hem bedava.
     */
    fun sesIpucu(ingilizce: String): String? {
        val kirpik = ingilizce.trim()
        // Kapanış parantezi OCR'da sık kayboluyor, o yüzden sadece açılışa bakıyoruz.
        if (!kirpik.startsWith("[") && !kirpik.startsWith("(")) return null

        val sade = kirpik.lowercase().filter { it.isLetter() || it == ' ' }
        if (sade.isBlank()) return null

        if (sade.contains("dramatic") && sade.contains("music")) {
            return "[gerilim müziği]"
        }
        if (sade.contains("tense") && sade.contains("music")) {
            return "[gergin müzik]"
        }

        for ((anahtar, karsilik) in SES_IPUCLARI) {
            if (sade.contains(anahtar)) return "[$karsilik]"
        }
        return null
    }

    /**
     * "-yor" ekinden sonra gelen soru ekini yerine oturtur.
     * "biliyorum mi" -> "biliyor muyum", "geliyorsun mu" -> "geliyor musun"
     *
     * "-yor"dan sonra soru eki Türkçede daima "mu"dur; kişi eki de ekin
     * üstüne biner. Kural bilerek dar: yalnızca bu kalıba dokunuyor.
     */
    // DİKKAT: \w Java'da yalnızca İngiliz harflerini kapsar; "çalışıyorum"
    // gibi kelimelerde kural tutmazdı. \p{L} tüm harfleri kapsıyor.
    private val SORU_EKI = Regex(
        "(?<![\\p{L}])([\\p{L}]*?[ıiuü]yor)(um|sun|uz|sunuz)\\s+m[ıiuü](?![\\p{L}])",
        RegexOption.IGNORE_CASE
    )

    private fun kisiEki(ek: String): String = when (ek.lowercase()) {
        "um" -> "muyum"
        "sun" -> "musun"
        "uz" -> "muyuz"
        "sunuz" -> "musunuz"
        else -> "mu"
    }

    fun duzelt(turkce: String): String {
        var metin = SORU_EKI.replace(turkce) { eslesme ->
            val govde = eslesme.groupValues[1]
            govde + " " + kisiEki(eslesme.groupValues[2])
        }

        // Noktalama öncesi boşluk ve çoklu boşluk temizliği
        metin = metin.replace(Regex("\\s+([,.!?:;])"), "$1")
        metin = metin.replace(Regex("\\s+"), " ").trim()

        return metin
    }
}
