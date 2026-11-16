package com.example.altyazi

/**
 * YAPAY ZEKÂ DENETİMİ
 *
 * Kullanıcının 6 Kasım kaydı gösterdi ki yapay zekâ taslağı düzelttiği kadar
 * bozuyor da. Kayıttan, taslak DOĞRUYKEN yapay zekânın bozduğu satırlar:
 *
 *   "Tapınaktaki heykel"            -> "Tapu'daki Heykel"
 *   "Kanlı cehennem"                -> "Helikopter"
 *   "Hunter ve suikastçıları"       -> "Avcı ve cinayetleri"      (ismi çevirdi)
 *   "Tapınakçılara sattığınız ..."  -> "Satılan haritalar"        (yarısını attı)
 *   "Kim orada?"                    -> "Kim çıktı orada?"
 *
 * Üstelik her satırda yapay zekâ çalıştırmak 1,5-3,7 sn gecikme ve ekranda
 * yanıp sönme demek. Bu dosya iki iş yapıyor:
 *
 *   1. [yzGerekli]  Taslak sağlamsa yapay zekâyı HİÇ çağırma.
 *   2. [onayla]     Yapay zekâ cevabı taslaktan kötüyse reddet, taslak kalsın.
 *
 * İkisi de mekanik; "daha güzel mi" diye karar vermiyor, sadece ölçülebilir
 * bozulmaları yakalıyor.
 */
object AiGuard {

    /**
     * Çevrilmemesi gereken adlar. NameFix listesi Türkçe anlamı olan isimleri
     * kapsıyor; burası oyun/dizi evrenine ait, çeviride İngilizce kalması
     * gereken özel adlar. Küçük harfle tutuluyor.
     */
    private val ADLAR: Set<String> = setOf(
        // Assassin's Creed (kullanıcının kaydındaki oyun)
        "jackdaw", "kenway", "edward", "laurens", "prins", "havana", "tortuga",
        "nassau", "kingston", "breddah", "mentor", "abstergo", "paris", "madrid",
        // Bilerek YOK: "Ben", "Anne", "Jack", "Mary" gibi kısa adlar.
        // "anne" ve "ben" Türkçede gerçek kelime; ad listesine girerlerse
        // çeviri denetimi boş yere sonuç reddediyor.
    )

    /**
     * Türkçe karşılığı OLAN terimler: ya İngilizcesi ya Türkçesi cevapta bulunmalı.
     * "Templars" -> "Tapınakçılar" doğru çeviridir, reddedilmemeli; ama cevaptan
     * tamamen düşmesi ("Satılan haritalar") içerik kaybıdır, reddedilmeli.
     * Değerler GÖVDE: Türkçe ekler ("Tapınakçılara") eklenebilsin diye.
     */
    private val TERIMLER: Map<String, String> = mapOf(
        "templar" to "tapınakçı", "templars" to "tapınakçı",
        "assassin" to "suikastçı", "assassins" to "suikastçı",
        "observatory" to "gözlemev",
        "jamaica" to "jamaik", "london" to "londra", "lisbon" to "lizbon",
        "seville" to "sevilla", "boston" to "boston",
    )

    /** Bir kelime çevrilmemesi gereken ad mı? */
    fun adMi(kelime: String): Boolean {
        val k = kelime.trim().trim('.', ',', '!', '?', ':', ';', '"', '\'', '’', '(', ')')
            .lowercase()
        if (k.isEmpty()) return false
        if (k in ADLAR) return true
        // NameFix'in koruduğu adlar (Sage, Hope, Rose, Hunter...) da ad sayılır.
        return NameFix.korunanAdlar.any { it.equals(k, ignoreCase = true) }
    }

    private fun kelimeler(s: String): List<String> =
        s.split(Regex("[^\\p{L}\\p{N}'’]+")).filter { it.isNotBlank() }

    // ------------------------------------------------------------- 1) kapı

    /**
     * Yapay zekâ bu satır için gerçekten gerekli mi?
     *
     * Kayıttaki veriye göre yapay zekânın kazandığı yerler UZUN ve karmaşık
     * cümleler; kaybettiği yerler kısa, taslağın zaten doğru olduğu satırlar.
     * Kural buna göre:
     *
     *   - taslak yoksa / başarısızsa            -> gerekli
     *   - taslakta çevrilmemiş İngilizce kaldıysa -> gerekli ("Chattel'i yapacak")
     *   - kaynak uzunsa (9+ kelime)             -> gerekli
     *   - başka her durumda                     -> GEREKSİZ, taslak kalsın
     */
    fun yzGerekli(kaynak: String, taslak: String?): Boolean {
        if (taslak.isNullOrBlank()) return true
        if (taslak.trim().equals(kaynak.trim(), ignoreCase = true)) return true
        if (kalanIngilizce(kaynak, taslak) != null) return true
        if (tekrarEdenKelime(taslak)) return true
        // OLUMSUZLUK DÜŞMÜŞ: taslak anlamı TERSİNE çevirmiş olabilir.
        // "I'm not some criminal who escaped justice!" taslakta
        // "Ben adaletten kaçan bir suçluyum!" oluyordu. Bu sınıf, yapay
        // zekânın en çok işe yaradığı yer; mutlaka çağırılıyor.
        if (olumsuzlukDustu(kaynak, taslak)) return true
        // KELİME SAYISI ÖLÇÜSÜ KALDIRILDI. "9 kelimeden uzunsa çağır" kuralı
        // oyun diyaloğunun çoğunu (kısa replikler) denetimsiz bırakıyordu;
        // 11 Kasım 26 dakikalık kaydında 15 bozuk taslak bu yüzden geçti:
        //   "you know, think-tank stuff"  -> "Bilirsin, TANKLAR"
        //   "Move, move, move!"           -> "TAŞI, Taşı, Taşı!"
        //   "And you guys got our room sorted?" -> "odamızda SIRALADINIZ mı?"
        //
        // Yerine tek ve net bir ölçüt: taslak NEREDEN geldi?
        //  * Elle yazılmış kalıptan geldiyse güvenilir, yapay zekâ bozuyor.
        //  * Çeviri motorundan geldiyse makine çıktısıdır, yapay zekâ düzeltiyor.
        return !OfflineFix.tamamenKaliptanMi(kaynak)
    }

    /**
     * Taslakta arka arkaya aynı kelime var mı?
     *
     * Çeviri motoru bir cümleyi beceremediğinde en net işareti bu oluyor.
     * 11 Kasım kaydından:
     *   "Demek istediğim, siktir siktir sahip olabilir ve biraz bok..."
     *   "Hey Ray, o o. Ernesto."
     * İkisinde de yapay zekâ taslaktan belirgin iyi sonuç verdi. Gerçek
     * Türkçede arka arkaya aynı kelime yok denecek kadar az ("çok çok",
     * "az az" gibi pekiştirmeler muaf tutuluyor).
     */
    private val PEKISTIRME = setOf("çok", "az", "yavaş", "ağır", "hemen", "tek", "bir")

    /**
     * Araya YALNIZ boşluk giren tekrar bozuktur: "siktir siktir", "o o".
     * Noktalama girerse meşru pekiştirmedir ve dokunulmaz:
     *   "Come on, come on!" -> "Hadi, hadi!"
     *   "Move, move!"       -> "Kımılda, kımılda!"
     */
    private val TEKRAR = Regex("(?<![\\p{L}])(\\p{L}+)[ \\t]+\\1(?![\\p{L}])", RegexOption.IGNORE_CASE)

    fun tekrarEdenKelime(metin: String): Boolean =
        TEKRAR.findAll(metin).any { it.groupValues[1].lowercase() !in PEKISTIRME }

    /**
     * Taslakta çevrilmeden kalmış İngilizce kelime var mı?
     *
     * Sözlük gerekmiyor: kaynakta AYNEN geçen, 4+ harfli, ad olmayan bir kelime
     * çeviride de duruyorsa o kelime çevrilememiş demektir.
     */
    fun kalanIngilizce(kaynak: String, ceviri: String): String? {
        val kaynakKelimeler = kelimeler(kaynak).map { it.lowercase() }.toSet()
        for (ham in kelimeler(ceviri)) {
            // Türkçe ek kesme işaretiyle yapışıyor: "Chattel'i" -> gövde "Chattel".
            val k = ham.substringBefore('\'').substringBefore('’')
            if (k.length < 4) continue
            if (adMi(k)) continue
            if (k.lowercase() in TERIMLER) continue
            // Türkçe harf taşıyorsa Türkçe kelimedir.
            if (k.any { it in "çğıöşüÇĞİÖŞÜ" }) continue
            if (k.lowercase() in kaynakKelimeler) return k
        }
        return null
    }

    // --------------------------------------------------------- 2) denetim

    /**
     * Taslak YOKKEN kullanılan gevşek denetim.
     *
     * Ekranda gösterilecek başka bir şey olmadığı için yalnızca işe yaramaz
     * cevaplar elenir: boş ya da hiç çevrilmemiş. Kusurlu bir çeviri, boş
     * ekrandan iyidir. Sıkı denetim "iyi taslağı koru" diye vardı; ortada
     * taslak yoksa koruyacak bir şey de yok ve satır boş geçiyordu.
     */
    fun yetersizMi(kaynak: String, yz: String?): Boolean {
        val y = yz?.trim() ?: return true
        if (y.isBlank()) return true
        if (!y.equals(kaynak.trim(), ignoreCase = true)) return false
        // AYNI METİN DÖNDÜ. Satır yalnızca özel adlardan oluşuyorsa bu DOĞRU
        // cevaptır, çevrilmemiş değil: "Raymond, Raymond." zaten çevrilmez.
        // Kullanıcının 11 Kasım kaydında bu satır "çevrilmemiş" diye
        // reddedilip ekranda hiç görünmemişti.
        return !yalnizAdMi(kaynak)
    }

    /**
     * Satırdaki her kelime olağan bir özel ad yazımı mı ("Raymond", "Nesto")?
     *
     * İÇ BÜYÜK HARFE İZİN VAR: "DeLeon", "McBride", "O'Brien" gerçek ad
     * yazımları ama eski kalıp ikinci harften sonra yalnız küçük harf
     * istediği için bunları ad saymıyordu. 9.63 kaydında
     *   "[Batman] Vinny DeLeon, Roger Marshall,"
     * tam bu yüzden ne taslak ne yapay zekâ yoluyla ekrana geldi:
     *   TASLAK GÖSTERİLMEDİ (hızlı çeviri başarısız)
     *   YZ REDDEDİLDİ (çevrilmemiş) · ekranda bir şey yok
     * Satır yalnız adlardan oluşuyorsa çevirisi kendisidir.
     */
    private val AD_YAZIMI = Regex("^\\p{Lu}[\\p{L}'’]*$")

    fun yalnizAdMi(metin: String): Boolean {
        val k = kelimeler(metin)
        return k.isNotEmpty() && k.all { AD_YAZIMI.matches(it) }
    }

    /**
     * 9.87 — Satır yalnız AD DEFTERİNDEKİ adlardan mı oluşuyor ("Alien.")?
     *
     * Kullanıcının 12 Kasım 9.86 kaydında "[Alien] Well, welcome..." etiketiyle
     * öğrenilen ad, tek başına "Alien." diye geçince yapay zekâ onu "Yabancı."
     * diye çevirdi. Deftere etiketten girmiş ad çevrilmez; satır olduğu gibi
     * yayınlanır, yapay zekâya hiç gitmez.
     */
    fun defterAdiMi(metin: String): Boolean {
        val k = kelimeler(metin)
        return k.isNotEmpty() && k.size <= 3 &&
            k.all { AD_YAZIMI.matches(it) && AdDefteri.adMi(it.lowercase()) }
    }

    /** İki kelime arası düzenleme mesafesi (en çok [sinir] ile sınırlı). */
    private fun mesafe(a: String, b: String, sinir: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > sinir) return sinir + 1
        var onceki = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val simdi = IntArray(b.length + 1)
            simdi[0] = i
            var satirEnAz = i
            for (j in 1..b.length) {
                simdi[j] = minOf(
                    onceki[j] + 1, simdi[j - 1] + 1,
                    onceki[j - 1] + (if (a[i - 1] == b[j - 1]) 0 else 1)
                )
                if (simdi[j] < satirEnAz) satirEnAz = simdi[j]
            }
            if (satirEnAz > sinir) return sinir + 1
            onceki = simdi
        }
        return onceki[b.length]
    }

    /** Reddedilme sebebi; null dönerse cevap kabul edilir. */
    /** İngilizce küfür. Satırda varsa çeviride de sertlik beklenir. */
    // 9.97: çoğul ve ekli biçimler de ("assholes", "bastards", "dipshits",
    // "damned"). 51 kaydın taramasında "these assholes" küfür sayılmadığı
    // için doğru çeviri "küfür eklendi" diye reddedilmişti.
    private val IN_KUFUR = Regex(
        "(?i)\\b(fuck\\w*|shit\\w*|bitch\\w*|motherfuck\\w*|bastards?|assholes?|" +
            "dipshits?|jackass\\w*|cunts?|dicks?|piss\\w*|goddamn\\w*|damn\\w*|hell)\\b"
    )

    /**
     * SERT küfür. "Eklendi" denetiminde yalnız bunlar aranıyor.
     *
     * "lan", "be", "ya" bu listede YOK: bunlar küfür değil, sokak ağzının
     * seslenme sözleri ve "Yo," karşılığı olarak doğru. Hepsini eklendi
     * saymak DAHA İYİ çeviriyi reddettiriyordu. Kullanıcının 11 Kasım
     * kaydından:
     *   "Yo, we're here to pick up. For Boobie."
     *     taslak "Hey, buradayız. Boobie için."      ("pick up" düşmüş)
     *     YZ     "Lan, malı almaya geldik..."        reddedildi, oysa doğru
     */
    // KELİME BAŞI SINIRI ŞART. Eskiden kök "sik" sınırsızdı ve GÜNLÜK
    // kelimelerin içinde eşleşiyordu: "tok-sik-oloji", "ek-sik",
    // "kla-sik", "moto-sik-let", "Mek-sik-a". Son ikisi uygulamanın KENDİ
    // sözlüğünden çıkıyor ("classical" -> "klasik", "motorcycle" ->
    // "motosiklet"), yani motosiklet geçen her satırda yapay zekâ çevirisi
    // "küfür eklendi" diye atılıyordu. Kullanıcının 11 Kasım kaydından:
    //   "We need to light a fire under the medical examiner to get that
    //    tox report done."
    //   YZ: "O toksikoloji raporunu alabilmek için adli tıp uzmanı..."
    //   YZ REDDEDİLDİ (küfür eklendi) · taslak kalıyor:
    //        "...tıbbi muayenenin altındaki bir ateş yakmamız gerekiyor."
    // Doğru çeviri atıldı, bozuk taslak ekranda kaldı.
    // Kalıp ŞABLONSUZ yazılıyor: "$SERT_KOK" gibi bir Kotlin şablonu
    // kullanınca kalıp ancak çalışma anında oluşuyor ve test_kalip.py onu
    // statik derleyemiyor — yani sessizce denetimsiz kalıyor.
    // "(?U)" BAYRAĞI KULLANILMAZ — ANDROID'DE ÇÖKERTİR.
    //
    // 9.78'de bu kalıplara "(?iU)" eklemiştim; uygulama açılışta çöktü
    // ("GameLingo sürekli olarak duruyor"). Sebep: Android'in
    // java.util.regex'i OpenJDK değil ICU motorudur ve ICU "U" bayrağını
    // tanımaz: U_REGEX_INVALID_FLAG -> PatternSyntaxException -> nesne
    // kurulurken ExceptionInInitializerError.
    //
    // Üstelik gereksizdi: ICU'da "\\b" ve "\\w" ZATEN Unicode farkındadır.
    // "Java'da \\b ASCII" bulgum masaüstü JVM için doğruydu ama ürün orada
    // çalışmıyor. Testler artık kalıpları gerçek ICU motorunda deniyor
    // (test/icu_regex.py); masaüstü Java yalnız yedek.
    //
    // "(?-i:[iİ])" korunuyor: ICU'da zararsız, masaüstü Java'da "sık"ı
    // "sik" sanmayı önlüyor.
    private val TR_SERT = Regex(
        "(?i)(\\bs(?-i:[iİ])k|\\bam[ıi]na\\b|\\borospu|\\bpiç|\\bpic\\b|\\bbok\\b|" +
            // "\\bgötü" tek basina "götürdü"/"götürmek" ile carpisiyordu:
            // Turkcenin en sik fiillerinden biri. (?!r) onu disliyor.
            "hassiktir|\\bgöt\\b|\\bgötü(?!r)|\\bgotu\\b|\\byarrak|\\bşerefsiz)",
    )

    /** Türkçe küfür ve pekiştirici: "lan" ve "be" dahil, hafifi de sertlik sayılır. */
    private val TR_KUFUR = Regex(
        "(?i)(\\bs(?-i:[iİ])k|\\bam[ıi]na\\b|\\borospu|\\bpiç|\\bpic\\b|\\bbok\\b|" +
            "hassiktir|\\bgöt\\b|\\bgötü(?!r)|\\bgotu\\b|\\byarrak|\\bşerefsiz|" +
            "\\bkahretsin|\\blanet|\\blan\\b|\\bulan\\b|\\bbe\\b|" +
            "\\baptal|\\bsalak|" +
            // 9.97: yapay zekânın kullandığı sert karşılıklar da sertliktir:
            // "boktan", "pislik", "kahrolası", "herif", "amk". 51 kaydın
            // taramasında bunları taşıyan doğru çeviriler "küfür düştü" diye
            // reddedilip yerine bozuk taslak gösterilmişti.
            "\\bbok(?!s)|\\bpislik|\\bpisliğ|\\bkahrolas|\\bherif|\\bamk\\b|" +
            "\\bgeri ?zekal|\\bdangalak|\\bserseri)",
    )

    /**
     * Elle yazılmış, İNGİLİZCESİNDEN TAHMİN EDİLEMEYEN karşılıklar.
     *
     * Bunlar sözlükte doğru duruyor ve taslak doğru çeviriyor; yapay zekâ ise
     * kelime kelime çevirmeye kalkıp anlamı tersine döndürüyor. Kullanıcının
     * 11 Kasım kaydından:
     *   "It's twelve!"            taslak "Polis geldi"   YZ "Yüz on iki!"
     *   "street pushers"          taslak "sokak satıcısı" YZ "sokak polisleri"
     *   "Aww, too bad!"           taslak "Ah, ne yazık!"  YZ "Aman, neyse ki!"
     * Düz çevrilebilen kalıplar BURAYA GİRMEZ: "Hold up" -> "Bekle" için
     * yapay zekânın "Dur bir" demesi de doğrudur, engellenmemeli.
     *
     * kalıp (regex) -> cevapta mutlaka geçmesi gereken Türkçe kök.
     */
    private val KORUNAN: List<Pair<Regex, String>> = listOf(
        "\\bit'?s twelve\\b|\\btwelve'?s here\\b" to "polis",
        "\\bstreet pushers?\\b|\\bpushers?\\b" to "satıcı",
        "\\btoo bad\\b" to "yazık",
        "\\bholding pattern\\b" to "bekl",
        "\\bfeel me\\b" to "anl",
        // "We going up" merdivenden yukarı çıkmak; "yükselmek" balon/fiyat için.
        "\\bwe(?:'re)?\\s+go(?:ing|in)'?\\s+up\\b" to "yukarı",
        // "Right inside" yer tarifi ("hemen içeride"), "bir şeyin içinde" değil.
        "\\bright inside\\b" to "içeri",
    ).map { Regex("(?i)" + it.first) to it.second }

    /**
     * Saçma karşılıklar: kaynakta kalıp varsa cevapta bu kelime ASLA olmamalı.
     *
     * Kök aramanın işe yaramadığı durumlar için. "Straight up, first door on
     * the right" için "Dosdoğru ilerle" de "Dümdüz git" de doğru; ortak bir
     * kök yok. Ama "Açık SÖZLÜK, sağdaki ilk kapı" her hâlükârda saçma.
     */
    private val YASAK: List<Pair<Regex, String>> = listOf(
        "\\bstraight up\\b" to "sözlük",
        "\\btwelve\\b" to "yüz on iki",
        "\\bpushers?\\b" to "polis",
    ).map { Regex("(?i)" + it.first) to it.second }

    /**
     * Yapay zekâ bildiğimiz doğruyu bozmuş mu?
     *
     * Yalnız yukarıdaki korunan kalıplara bakılıyor. Genel bir "taslaktaki
     * kelimeler cevapta da olsun" kuralı yanlış olurdu: yapay zekânın daha
     * iyi bir eşanlamlı seçmesi de engellenirdi.
     */
    private fun kalipDustu(kaynak: String, yzKucuk: String): String? {
        for ((kalip, kok) in KORUNAN) {
            if (!kalip.containsMatchIn(kaynak)) continue
            if (!yzKucuk.contains(kok)) return "kalıp bozuldu: ${kalip.pattern} -> '$kok' yok"
        }
        for ((kalip, yasak) in YASAK) {
            if (!kalip.containsMatchIn(kaynak)) continue
            if (yzKucuk.contains(yasak)) return "saçma karşılık: '$yasak'"
        }
        return null
    }

    /**
     * Türkçe duyarlı küçültme.
     *
     * Varsayılan lowercase "İ" harfini "i" + birleşik nokta yapıyor; sonuç
     * "i̇çeride" olup "içeri" aramasıyla EŞLEŞMİYOR. Bu yüzden doğru çeviri
     * "İçeride, hemen şurada." kalıp denetiminde reddediliyordu.
     */
    private fun kucuk(s: String): String =
        s.replace('İ', 'i').replace('I', 'ı').lowercase()

    /**
     * Hafızadan gelen çeviri korunan kalıpları bozmuş mu?
     *
     * Hafıza yapay zekâ denetiminden GEÇMEDEN ekrana basılıyor; bir kez
     * bozuk kayıt girdiyse sonsuza kadar geri servis ediliyordu. Kullanıcının
     * kaydında "We going up" -> "Yükseliyoruz" her seferinde hafızadan
     * geliyordu. Bozuksa kayıt silinip satır yeniden çevriliyor.
     */
    fun hafizaBozuk(kaynak: String, ceviri: String): String? =
        kalipDustu(kaynak, kucuk(ceviri))

    // ------------------------------------------------------- 3) seslenme

    /**
     * CÜMLE BAŞI SESLENME SÖZLERİ. Karşılıkları sabittir ve tahmin gerektirmez.
     *
     * Yapay zekâ bunların hepsine "Lan" diyor. "Lan" Türkçede kavga başlatan
     * bir sözdür; "Yo," ise sokakta "Hey," demektir. Kullanıcının 11 Kasım
     * kaydında oyunun ağzı bu yüzden olduğundan sert çıkıyordu:
     *   "Yo, we're here to pick up."   -> "LAN, malı almaya geldik."
     *   "Like, might have to ..."      -> "LAN, ortalığı karıştırmak ..."
     *   "Man.. I don't even wanna ..." -> "LAN... o boktan şeyi ..."
     * Üçünde de kaynakta seslenme var, küfür yok.
     *
     * Reddetmiyoruz, ONARIYORUZ: cevabın gerisi genelde taslaktan iyi,
     * reddetmek iyi çeviriyi de çöpe atmak olurdu.
     */
    private val HITAP_BAS: List<Pair<Regex, String>> = listOf(
        "ay|aye|hey" to "Hey",
        "look" to "Bak",
        "listen" to "Dinle",
        "like" to "Yani",
        "man|aw man|oh man" to "Ya",
        "well" to "Şey",
    ).map { Regex("^(?:${it.first})\\b", RegexOption.IGNORE_CASE) to it.second }

    /**
     * Cevabın başındaki sert seslenme: "Lan,", "Ulan", "Be,".
     *
     * İkinci grup sonrasındaki noktalama: olduğu gibi korunuyor, yoksa
     * "Lan... o boktan şeyi" onarılırken duraklama kaybolup "Ya o boktan
     * şeyi" oluyordu.
     */
    private val SERT_BAS = Regex("^(lan|ulan|be|birader)\\b([\\s,!.…]*)", RegexOption.IGNORE_CASE)

    /**
     * Kaynak nazik bir seslenmeyle başlıyorsa çeviri de öyle başlasın.
     *
     * Yalnızca İLK kelimeye dokunur; cümlenin gerisi olduğu gibi kalır.
     * Kaynakta gerçek küfür varsa ("Yo, what the fuck?") cümle içindeki
     * sertlik korunur, çünkü değişen tek şey baştaki seslenme sözü.
     */
    private val LAN_ICINDE = Regex("\\b(lan|ulan)\\b", RegexOption.IGNORE_CASE)

    // 9.98 — "SİKİM" YOK. Kullanıcı: "what the fuck - bu ne lan demek, sikim
    // değil". Yapay zekâ talimata rağmen "Ne sikim oluyor?" yazarsa kod düzeltir:
    //   "Ne sikim oluyor?"           -> "Ne oluyor lan?"
    //   "Ne sikimden bahsediyorsun?" -> "Neden bahsediyorsun lan?"
    //   "gerçekten sikimde değil"    -> "gerçekten umurumda değil"
    private val SIKIMDEN = Regex("\\bne sikimden\\b", RegexOption.IGNORE_CASE)
    private val SIKIMDE_DEGIL = Regex("\\bsikimde değil", RegexOption.IGNORE_CASE)
    private val SIKIM = Regex("\\s*\\bsikim\\b", RegexOption.IGNORE_CASE)
    private val SON_NOKTALAMA = Regex("([?!.…]*)\\s*$")

    fun sikimDuzelt(ceviri: String): String {
        if (!ceviri.contains("sikim", ignoreCase = true)) return ceviri
        var t = SIKIMDEN.replace(ceviri) { if (it.value[0].isUpperCase()) "Neden" else "neden" }
        t = SIKIMDE_DEGIL.replace(t, "umurumda değil")
        val lanEkle = SIKIM.containsMatchIn(t) || t != ceviri && SIKIMDEN.containsMatchIn(ceviri)
        t = SIKIM.replace(t, "")
        if (lanEkle && !LAN_ICINDE.containsMatchIn(t)) {
            val m = SON_NOKTALAMA.find(t)
            t = if (m != null) t.substring(0, m.range.first).trimEnd() + " lan" + m.groupValues[1] else "$t lan"
        }
        return t.trim()
    }

    fun hitapDuzelt(kaynak: String, cevir: String): String {
        val ceviri = sikimDuzelt(cevir)
        if (YoContext10427.startsWithYo(kaynak)) {
            return YoContext10427.correct(kaynak, ceviri)
        }
        val k = kaynak.trimStart('-', '–', ' ', '"', '\'', '[')
        val karsilik = HITAP_BAS.firstOrNull { it.first.containsMatchIn(k) }?.second ?: return ceviri
        val m = SERT_BAS.find(ceviri) ?: return ceviri
        // Cümlenin gerisi boşsa dokunmuyoruz: "Lan!" tek başına cevabın
        // tamamı olabilir, onu da bozup ortada bir şey bırakmamak gerek.
        val kalan = ceviri.substring(m.value.length)
        if (kalan.isBlank()) return ceviri
        // Seslenmeden sonraki noktalama olduğu gibi kalıyor; hiç yoksa boşluk.
        val ayrac = m.groupValues[2].ifBlank { " " }
        return karsilik + ayrac + kalan
    }


    // ---- 9.75: OLUMSUZLUK (kutup) KONTROLÜ -------------------------------
    //
    // En tehlikeli hata sınıfı anlamın TERSİNE dönmesi. Kullanıcının
    // 11 Kasım 9.74 kaydından:
    //   "I'm not some criminal who escaped justice!"
    //     -> taslak: "Ben adaletten kaçan bir suçluyum!"   (olumsuzluk UÇTU)
    // Bu, eksik çeviriden çok daha kötü: kullanıcı tam tersini okuyor.
    //
    // Kural ASİMETRİK: yalnız "kaynak olumsuz, çeviri olumlu" reddediliyor.
    // Tersi (kaynak olumlu, çeviri olumsuz) normaldir; Türkçe çoğu şeyi
    // olumsuz kurar: "Stay back!" -> "Yaklaşma!"
    // TASARIM İLKESİ (ilk denememde tersini yapmışım, kaydın üstünde ölçünce
    // çıktı): Türkçe tarafında FAZLA bulmak GÜVENLİ, AZ bulmak TEHLİKELİ.
    // Kural "kaynak olumsuz + çeviride hiç olumsuzluk yok -> reddet"
    // olduğu için, Türkçe kalıbı geniş tutmak bizi yalnız daha temkinli
    // yapar; dar tutmak DOĞRU çevirileri reddettirir. İlk dar kalıpla
    // kullanıcının kayıtlarındaki 164 olumsuz çevirinin 42'si yanlışlıkla
    // işaretleniyordu ("Kıpırdama lan!", "Hayır, Genel.", "ölü değiliz").
    //
    // İngilizce listeden ZAYIF olumsuzlar çıkarıldı: without/hardly/barely/
    // scarcely/unable Türkçeye sözlükle çevriliyor ve olumsuzluk eki
    // bırakmıyor ("We are without end." -> "Sonsuzuz." DOĞRU).
    // 9.87: ÜNLEM "no" olumsuzluk sayılmaz. "Wait, no! Stop, stop! He's on our
    // side now." cümlesinin kendisi olumsuz değil; doğru çeviri ("Dur, dur!
    // Durun! O artık bizim tarafımızda.") "olumsuzluk düştü" diye reddedildi.
    // Noktalamayla biten "no" artık sayılmıyor; "no one", "no way" sayılıyor.
    private val IN_OLUMSUZ = Regex(
        "(?i)\\b(not|never|no(?![,.!?…])|none|nobody|no one|nothing|nowhere|neither|nor|" +
            "cannot|can't|won't|don't|doesn't|didn't|isn't|aren't|wasn't|weren't|" +
            "haven't|hasn't|hadn't|shouldn't|wouldn't|couldn't|mustn't|ain't)\\b|n't\\b",
    )

    /**
     * Türkçe olumsuzluk sözcükleri. Sonda \\b YOK: Türkçe ek alır
     * ("değiliz", "değilse", "hiçbiri", "yoktur", "kimsenin").
     */
    // 9.90: "farklı" da olumsuzluk taşır. 12 Kasım 9.89 kaydı:
    //   "My one little piece that ain't like the others."
    //   -> "Diğerlerinden farklı tek parçam."   (doğru; "olumsuzluk düştü" diye reddedildi)
    private val TR_OLUMSUZ_SOZ = Regex(
        "(?i)(\\bdeğil|\\byok|\\bhayır|\\basla|\\bhiç|\\bkimse|\\bolmaz|\\bolama|" +
            "\\bolmasa|\\bsakın|\\bne de\\b|\\bzar zor|\\byerine|\\bbila|\\bfarklı)",
    )

    /**
     * Türkçe olumsuzluk EKİ: -ma/-me (+ zaman/kişi/sıfat-fiil eki).
     * Sonda \\b YOK; ek üstüne ek geliyor: "satmayacaksın", "bilmediğim",
     * "kazanmadığınız", "yapmıyordun", "tekrarlamamayı".
     * Ayrıca ÇIPLAK emir kipi ("Kıpırdama!", "gelme", "girme") ayrı kalıpla
     * alınıyor; "kelime"/"deneme"/"sinema" gibi isimleri de yakalaması
     * SORUN DEĞİL, yukarıdaki ilke gereği güvenli yönde hata.
     */
    // Kayıt üstünde ölçerken eksik çıkan kipler de eklendi: olumsuz emir
    // çoğul (olmayın, etmeyin), istek (olmasın), isim-fiil (sormamı).
    // DİKKAT: bu açıklama Regex(...) çağrısının İÇİNE yazılmamalı; içindeki
    // tırnaklı örnekler kaynak tarayan testlerin kalıbı yanlış okumasına
    // yol açıyor (aynı tuzağa parantezli yorumla bir kez düşmüştüm).
    private val TR_OLUMSUZ_EK = Regex(
        "(?i)\\w{2,}m(?:[ae]z|[ae]d[ıi]|[ae]dığ|[ae]diğ|[ae]duğ|[ae]düğ|" +
            "[iıuü]yor|[ae]yacak|[ae]yecek|[ae]yeceğ|[ae]yacağ|[ae]mış|[ae]miş|" +
            "[ae]malı|[ae]meli|[ae]sa|[ae]se|[ae]yiz|[ae]yız|[ae]miz|[ae]mız|" +
            "[ae]ye|[ae]ya|[ae]m[ae]|[ae]ksizin|[ae]den|[ae]dan|" +
            "[ae]y[ıiu]n|[ae]s[ıi]n|[ae]m[ıi]|[ae]l[ıi]|[ae]nız|[ae]niz|" +
            // 1. tekil geniş zaman olumsuzu: "etmem", "bilmem", "gitmem".
            // 12 Kasım kaydında "i'd rather not." -> "Tercih etmem." DOĞRU
            // çevirisi "olumsuzluk düştü" diye reddedildi; bu biçim eksikti.
            "[ae]m\\b|[ae]zs[ıi]n|[ae]yiz\\b)",
    )

    /** Çıplak olumsuz emir: "Kıpırdama!", "gelme", "unutma", "atma". */
    // İki harfli kökten gelen olumsuz emir de sayılıyor (etme, atma).
    // Bazı isimleri de yakalıyor; ilke gereği güvenli yöndeki hata.
    private val TR_EMIR = Regex("\\b\\w{2,}m[ae]\\b", RegexOption.IGNORE_CASE)

    /** Çeviride herhangi bir olumsuzluk izi var mı? */
    fun trOlumsuzVar(ceviri: String): Boolean =
        TR_OLUMSUZ_SOZ.containsMatchIn(ceviri) ||
            TR_OLUMSUZ_EK.containsMatchIn(ceviri) ||
            TR_EMIR.containsMatchIn(ceviri)

    fun inOlumsuzVar(kaynak: String): Boolean = IN_OLUMSUZ.containsMatchIn(kaynak)

    /** Kaynak olumsuz ama çeviride hiç olumsuzluk yoksa anlam ters dönmüştür. */
    fun olumsuzlukDustu(kaynak: String, ceviri: String): Boolean =
        inOlumsuzVar(kaynak) && !trOlumsuzVar(ceviri)

    fun redSebebi(kaynak: String, taslak: String?, yz: String): String? {
        val y = yz.trim()
        if (y.isBlank()) return "boş"

        // a) Hiç çevirmemiş: İngilizcenin aynısını geri vermiş.
        //
        // YALNIZ ADDAN OLUŞAN SATIR MUAF: çevirisi kendisidir. Bu muafiyeti
        // yetersizMi'ye koymuşum ama buraya koymamışım; iki yoldan biri hâlâ
        // reddediyordu. Kullanıcının 11 Kasım 9.66 kaydından:
        //   YZ REDDEDİLDİ (çevrilmemiş) · taslak kalıyor: Raymond, Raymond.
        //   YZ REDDEDİLDİ (çevrilmemiş) · taslak kalıyor: Viltrumite!
        if (y.equals(kaynak.trim(), ignoreCase = true) && !yalnizAdMi(kaynak)) return "çevrilmemiş"

        val yKucukTam = kucuk(y)

        // a2) Elle yazılmış, tahmin edilemez kalıp bozulmuş.
        kalipDustu(kaynak, yKucukTam)?.let { return it }

        // a2b) OLUMSUZLUK DÜŞMÜŞ: anlam tersine dönmüş.
        // Taslak varsa ve taslak olumsuzluğu TAŞIYORSA, cevabın taşımaması
        // kesin kayıptır. Taslak yoksa da reddediyoruz: kaynak olumsuzken
        // çeviride hiç olumsuzluk izi olmaması neredeyse hep yanlıştır.
        if (olumsuzlukDustu(kaynak, y)) {
            if (taslak == null || trOlumsuzVar(taslak)) return "olumsuzluk düştü"
        }

        // a3) KÜFÜR DÜŞMÜŞ. Kullanıcı "küfür korunuyor" ayarını açtı; yapay zekâ
        // bazen sertliği siliyor: "What the fuck you talking about?" taslakta
        // "Ne bok konuşuyorsun sen?" iken cevapta "Ne cidden bahsediyorsun?"
        // oluyordu. Taslak da sertliği taşıyorsa cevabın taşımaması kayıptır.
        if (taslak != null && IN_KUFUR.containsMatchIn(kaynak) &&
            TR_KUFUR.containsMatchIn(taslak) && !TR_KUFUR.containsMatchIn(y)
        ) {
            return "küfür düştü"
        }

        // a4) KÜFÜR EKLENMİŞ. Tersi de hata: kaynakta küfür yokken yapay zekâ
        // kendiliğinden ekliyor. Kayıttan: "Yeah, I know." -> "Biliyorum LAN."
        // Oyunun ağzını bozmak, yumuşatmak kadar yanlış.
        if (taslak != null && !IN_KUFUR.containsMatchIn(kaynak) &&
            !TR_SERT.containsMatchIn(taslak) && TR_SERT.containsMatchIn(y)
        ) {
            return "küfür eklendi"
        }

        // b) Kaynaktaki ad/terim cevapta yok: düşen "Jackdaw", düşen "Templars".
        //
        // ÖNEMLİ: yalnızca BÜYÜK HARFLE başlayan kelimeler denetlenir. Koruma
        // listesinde "May" (ay) ve "Storm" (isim) gibi kelimeler var; küçük
        // harfli "may" (yardımcı fiil) ve "storm" (fırtına) bunlarla karışıp
        // doğru çevirileri "ad düştü" diye reddettiriyordu (7 Kasım kaydı).
        // Özel ad cümle içinde hemen her zaman büyük harflidir; NameFix.koru
        // zaten büyük harfli adları çeviride geri koyduğu için burada ADLAR'daki
        // oyuna özgü adları (Jackdaw, Kenway...) ve bilinen terimleri denetlemek
        // yetiyor.
        // kucuk() ŞART: y TÜRKÇE çeviridir. Varsayılan lowercase "İ"yi
        // "i" + birleşik nokta yapıyor, "İçeride" -> "i̇çeride" oluyor ve
        // "içeri" aramasıyla EŞLEŞMİYOR; doğru çeviri "terim düştü" diye
        // reddediliyordu. Yardımcı 349 ve 494'te vardı, BURADA YOKTU.
        val yKucuk = kucuk(y)
        for ((i, ham) in kelimeler(kaynak).withIndex()) {
            // 9.90: İngilizce iyelik/kısaltma eki adın parçası değil.
            // 12 Kasım 9.89 kaydı: "Because Liu's coming." -> "Çünkü Liu geliyor."
            // DOĞRU cevap "ad bozuldu: Liu's -> Liu" diye reddedildi.
            val k = ham.substringBefore('\'').substringBefore('’')
            if (k.length < 3) continue
            if (k.first().isLowerCase()) continue          // küçük harf: ad değil
            val kk = k.lowercase()
            val turkce = TERIMLER[kk]
            if (turkce != null) {
                // İngilizcesi de Türkçesi de yoksa terim düşmüş demektir.
                if (!yKucuk.contains(kk) && !yKucuk.contains(turkce)) return "terim düştü: $k"
                continue
            }
            // Yalnızca oyuna özgü kesin adlar (ADLAR). Cümlenin ilk kelimesi,
            // sıradan bir kelimenin büyük harfle başlamasından ibaret olabileceği
            // için denetlenmez.
            if (i == 0) continue
            if (kk in ADLAR && !yKucuk.contains(kk)) return "ad düştü: $k"

            // b2) Özel ad BOZULMUŞ: kaynakta doğru yazılan ad, cevapta bir iki
            // harf eksik/yanlış geçiyor. Kayıttan:
            //   kaynak "Creek Valley Plains Entrance"
            //   cevap  "Creek Valley Plais Girişi"      <- "Plains" -> "Plais"
            // Bu bozuk çeviri hafızaya da yazılıp defalarca geri servis ediliyordu.
            // Taslak genelde adı doğru taşıdığı için reddetmek doğru davranış.
            if (k.length >= 5 && !yKucuk.contains(kk)) {
                // TÜRKÇE EK ALAN AD BOZULMUŞ SAYILMAZ.
                //
                // Türkçe ada ek takıyor ve İngilizce çoğul eki düşüyor:
                //   "Viltrumites" -> "Viltrumitler"   (doğru çeviri)
                //   "Templars"    -> "Tapınakçılar"
                // Harf mesafesine bakan denetim bunları "ad bozuldu" sanıp
                // DOĞRU cevabı reddediyordu. Kullanıcının 11 Kasım 9.65
                // kaydından:
                //   YZ REDDEDİLDİ (ad bozuldu: Viltrumites -> Viltrumitler)
                //   ekranda kalan taslak: "Ancak, ancak kalan tüm viltrumitler..."
                // Çözüm: adın GÖVDESİ (çoğul 's' ve sessiz 'e' atılmış hâli)
                // cevaptaki bir kelimenin başındaysa ad yerindedir.
                val kok = kk.trimEnd('s').trimEnd('e')
                val ekliHal = kok.length >= 4 &&
                    kelimeler(y).any { kucuk(it).startsWith(kok) }
                if (!ekliHal) {
                    for (c in kelimeler(y)) {
                        val cc = kucuk(c)
                        if (cc.firstOrNull() != kk.firstOrNull()) continue
                        if (kotlin.math.abs(cc.length - kk.length) > 2) continue
                        if (mesafe(cc, kk, 2) in 1..2) return "ad bozuldu: $k -> $c"
                    }
                }
            }
        }

        // c) Sayılar düşmüş: "5 gemi" -> "gemiler".
        //
        // YALNIZCA iki basamaklı ve üstü, tek başına duran sayılar aranıyor.
        // Kullanıcının kaydında tek basamaklılar yüzünden DOĞRU çeviriler
        // reddedildi: OCR "I remember" yerine "1 remember", "good" yerine
        // "g0od" okumuştu; yapay zekâ bu çöpü haklı olarak atınca
        // "sayı düştü: 1" / "sayı düştü: 0" diye cevabı çöpe atıyorduk.
        val sayilar = Regex("(?<![\\p{L}\\d])\\d{2,}(?![\\p{L}\\d])").findAll(kaynak)
            .map { it.value }.toList()
        for (s in sayilar) if (!y.contains(s)) return "sayı düştü: $s"

        // d) Taslağa göre belirgin kısalma: cümlenin yarısı atılmış.
        //    "Tapınakçılara sattığınız haritalar onları doğrudan bize yönlendirdi"
        //    -> "Satılan haritalar onları doğrudan bize getirdi"
        // HARF sayısıyla ölçülüyor, kelime sayısıyla değil. Türkçe ekli dildir:
        // "Belki buraya bir Powderkeg çekip bunu patlatabilirim" 6 kelime ama
        // taslaktaki 11 kelimenin bilgisini taşıyor. Kelimeyle ölçünce bu DOĞRU
        // ve daha derli toplu cevap "içerik düştü" diye reddediliyordu.
        if (taslak != null && taslak.isNotBlank()) {
            val th = taslak.count { it.isLetterOrDigit() }
            val yh = y.count { it.isLetterOrDigit() }
            // EŞİK 0,60'tan 0,45'e indi. Büyük model (E4B) Türkçeyi çok daha
            // derli toplu kuruyor ve 0,60 DOĞRU cevapları eliyordu. 11 Kasım
            // E4B kaydından:
            //   "T always been there for you."
            //     taslak "T her zaman senin için orada olmuştur."  (bozuk)
            //     YZ     "Sen hep oradaydın."                      (doğru, reddedildi)
            // Gerçek içerik kaybı 0,45'te de yakalanıyor: 62 harflik cümlenin
            // yerine "Haritalar" (9 harf) gelmesi hâlâ eleniyor.
            if (th >= 25 && yh < th * 0.45) return "içerik düştü ($yh/$th harf)"
        }

        return null
    }

    /**
     * Yapay zekâ cevabını onaylar. Kabul edilirse düzeltilmiş hâlini,
     * reddedilirse null döner (o zaman taslak ekranda kalır).
     */
    fun onayla(kaynak: String, taslak: String?, yz: String?): String? {
        if (yz.isNullOrBlank()) return null
        // Adları geri koy: yapay zekâ "Hunter"ı "Avcı" yaptıysa burada düzelir.
        // Seslenme kaynağın tonuyla uyumlu olmalı, otomatik "lan" eklenmemeli.
        val duzgun = hitapDuzelt(kaynak, NameFix.koru(kaynak, yz.trim()))
        return if (redSebebi(kaynak, taslak, duzgun) == null) duzgun else null
    }
}
