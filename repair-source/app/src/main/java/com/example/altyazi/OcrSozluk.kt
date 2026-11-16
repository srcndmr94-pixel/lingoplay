package com.example.altyazi

import android.content.Context
import java.util.zip.GZIPInputStream

/**
 * SÖZLÜKLÜ OCR ONARIMI
 *
 * Bilgisayar sürümünde (pc/lingoplay_pc.py) uzun zamandır çalışan onarım
 * telefonda yoktu: telefon elle yazılmış kısa listelerle idare ediyordu.
 * Bu dosya aynı işi 124.207 kelimelik İngilizce sözlükle yapıyor.
 *
 * Kullanıcının kaydındaki hatalar ve sözlüğün verdiği karşılık:
 *   "Iwonder"   -> "I wonder"     (baştaki I kelimeye yapışmış)
 *   "labsolve"  -> "I absolve"
 *   "lvories"   -> "ivories"      (l harfi i okunmuş)
 *   "thal"      -> "that"
 *   "topickup"  -> "to pick up"
 *
 * Kural hep aynı ve temkinli: kelime SÖZLÜKTE VARSA hiç dokunulmaz, denenen
 * karşılık sözlükte YOKSA da dokunulmaz. Yani uydurma düzeltme yapamaz.
 */
object Sozluk {
    private const val VARLIK = "kelimeler.txt.gz"

    @Volatile private var kelimeler: Set<String>? = null
    @Volatile private var yukleniyor = false
    @Volatile var sonHata: String? = null
        private set

    val hazir: Boolean get() = kelimeler != null
    val adet: Int get() = kelimeler?.size ?: 0

    /** Hangi yoldan yüklendiği (kayda yazılıyor). */
    @Volatile var kaynak: String = "-"
        private set

    /**
     * Sözlüğü arka planda yükler. Birden çok kez çağrılabilir, bir kez yükler.
     *
     * İKİ YOLDAN birden deniyoruz. Kullanıcının 6 Kasım 21:42 kaydında varlık
     * kaynak zip'inde olmasına rağmen APK'da bulunamadı
     * ("FileNotFoundException: kelimeler.txt.gz"). Sebebi anlaşılamadığı için
     * aynı dosya res/raw içine de konuldu: biri paketlenmezse diğeri çalışır.
     * Hata hâlinde APK'daki varlıkların listesi de kayda yazılıyor.
     */
    fun yukle(context: Context) {
        if (kelimeler != null || yukleniyor) return
        yukleniyor = true
        val app = context.applicationContext
        val is_ = Runnable { yukleIsi(app) }
        Thread(is_, "sozluk-yukle").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /** Asıl yükleme. Ayrı fonksiyon: etiketli return'e gerek kalmasın. */
    private fun yukleIsi(app: Context) {
        try {
            val hatalar = ArrayList<String>(2)
            // 1) res/raw/kelimeler.gz
            //    Kullanıcının 6 Kasım 22:32 kaydında assets yolu APK'da bulunamadı,
            //    res/raw çalıştı ("SÖZLÜK HAZIR: 124207 kelime (res/raw)").
            //    O yüzden sıra tersine çevrildi ve assets kopyası kaldırıldı.
            val a = dene("res/raw") { app.resources.openRawResource(R.raw.kelimeler) }
            if (a != null) {
                kelimeler = a
                kaynak = "res/raw"
                sonHata = null
                return
            }
            hatalar.add(sonHata ?: "res/raw: bilinmeyen")
            // 2) assets/kelimeler.txt.gz (eski yol; artık varsayılan olarak yok)
            val b = dene("assets") { app.assets.open(VARLIK) }
            if (b != null) {
                kelimeler = b
                kaynak = "assets"
                sonHata = null
                return
            }
            hatalar.add(sonHata ?: "assets: bilinmeyen")
            // İkisi de olmadı: APK'da ne var, kayda yazalım.
            val varliklar = try {
                app.assets.list("")?.joinToString(", ")?.take(200) ?: "(okunamadı)"
            } catch (e: Throwable) {
                "(liste alınamadı: ${e.javaClass.simpleName})"
            }
            sonHata = hatalar.joinToString(" | ") + " | APK varlıkları: $varliklar"
        } finally {
            yukleniyor = false
        }
    }

    /** Bir akıştan kelime kümesi okur; olmazsa sonHata'yı yazıp null döner. */
    private fun dene(ad: String, akis: () -> java.io.InputStream): Set<String>? = try {
        val set = HashSet<String>(1 shl 18)   // 262144 kova: 124k kelime rahat sığar
        GZIPInputStream(akis()).bufferedReader().use { r ->
            r.forEachLine { satir ->
                val k = satir.trim()
                if (k.isNotEmpty()) set.add(k)
            }
        }
        if (set.size < 1000) {
            sonHata = "$ad: beklenenden az kelime (${set.size})"
            null
        } else {
            set
        }
    } catch (e: Throwable) {
        sonHata = "$ad: ${e.javaClass.simpleName}: ${e.message}"
        null
    }

    /** Kelime sözlükte mi? Sözlük henüz yüklenmediyse daima false. */
    fun icinde(kelime: String): Boolean {
        val s = kelimeler ?: return false
        return kelime.lowercase() in s
    }
}

object OcrOnar {

    /**
     * OCR'ın sık karıştırdığı harfler. Bilgisayar sürümündeki liste (l/I/1 -> t,
     * a -> o) artı telefon kaydından çıkan birkaç tanesi: "lvories" -> "ivories".
     * Her harfin birkaç adayı olabilir; hepsi sözlüğe sorulur.
     */
    private val KARISIK: Map<Char, CharArray> = mapOf(
        'l' to charArrayOf('t', 'i'),
        'I' to charArrayOf('t', 'l'),
        '1' to charArrayOf('t', 'l'),
        'a' to charArrayOf('o'),
        '0' to charArrayOf('o'),
        '5' to charArrayOf('s'),
        // Kayıttan: "Not such a tough quy anymore" -> "guy". Gerçek "qu" kelimeleri
        // ("queen", "quick") sözlükte olduğu için hiç denenmez, bozulmaz.
        'q' to charArrayOf('g'),
    )

    /**
     * Kısa kelimelerde birden çok aday sözlükte bulunabiliyor ("Il" için hem
     * "ll" hem "It"). Gerçekte kullanılan kısa kelimeler öncelikli.
     */
    private val YAYGIN_KISA = setOf(
        "it", "is", "in", "to", "of", "he", "be", "we", "do", "go", "so", "no",
        "my", "me", "at", "as", "an", "if", "or", "up", "us", "by", "on", "am",
        "the", "and", "you", "that", "this", "was", "for", "are", "but", "not",
        "all", "can", "had", "her", "his", "him", "out", "get", "got", "let",
        "see", "say", "she", "too", "two", "way", "who", "why", "how", "now",
    )

    /** Bölmede tek başına durabilen kısa kelimeler. */
    private val KISA_SIK = setOf(
        "to", "of", "in", "on", "is", "it", "at", "be", "we", "he", "me", "my",
        "no", "so", "up", "do", "go", "an", "as", "by", "or", "us", "if", "oh",
        "ok", "am", "hi", "yo",
    )

    /** "I" ile başlayan gerçek özel adlar: "Iran", "Ivy" bozulmasın. */
    private val I_ADLARI = setOf(
        "ian", "ivan", "iris", "isaac", "ida", "ike", "ina", "ira", "ines", "ivy",
        "igor", "ilsa", "ilya", "imani", "ingrid", "isla", "ismail", "ivo", "izzy",
        "iggy", "iran", "iraq", "italy", "india", "israel", "ireland", "idaho", "iowa",
    )

    private val IZINLI_BASLIK = Regex("^(?:Mc|Mac|De|Di|La|Le|Van|Von|iP|eB)")
    private val PARCA = Regex("^([^\\w']*)([\\w']+)([^\\w']*)$")

    // ------------------------------------------------------- harf adayları

    /**
     * Karışabilen harfleri tek tek (en çok 3 tanesini) değiştirip aday üretir.
     * "thal" -> "that" ('l' yerine 't'), "lvories" -> "ivories" ('l' yerine 'i').
     */
    fun adaylar(kelime: String): List<String> {
        val yerler = kelime.indices.filter { kelime[it] in KARISIK }
        if (yerler.isEmpty() || yerler.size > 6) return emptyList()
        val sonuc = ArrayList<String>(64)
        for (adet in 1..minOf(3, yerler.size)) {
            kombinasyon(yerler, adet, 0, ArrayList()) { secim ->
                uretVeEkle(kelime, secim, 0, sonuc)
            }
        }
        return sonuc
    }

    /** yerler listesinden [adet] tanesini seçen tüm kombinasyonlar. */
    private fun kombinasyon(
        yerler: List<Int>, adet: Int, bas: Int,
        birikim: MutableList<Int>, isle: (List<Int>) -> Unit,
    ) {
        if (birikim.size == adet) { isle(birikim); return }
        for (i in bas until yerler.size) {
            birikim.add(yerler[i])
            kombinasyon(yerler, adet, i + 1, birikim, isle)
            birikim.removeAt(birikim.size - 1)
        }
    }

    /** Seçilen yerlerin her aday harfi için bir kelime üretir. */
    private fun uretVeEkle(kelime: String, secim: List<Int>, i: Int, cikti: MutableList<String>) {
        if (cikti.size > 300) return
        if (i == secim.size) { cikti.add(kelime); return }
        val yer = secim[i]
        for (yeni in KARISIK[kelime[yer]] ?: return) {
            uretVeEkle(kelime.substring(0, yer) + yeni + kelime.substring(yer + 1), secim, i + 1, cikti)
        }
    }

    // --------------------------------------------------------- harf onarımı

    /**
     * Sözlükte olmayan kelimede karışık harfleri dener: "thal" -> "that".
     * Sonuç sözlükte değilse kelimeye DOKUNMAZ.
     * Cümle ortasında büyük harfle başlayan kelimeler isim sayılır, ellenmez.
     */
    fun harfleriDuzelt(metin: String): String {
        if (!Sozluk.hazir) return metin
        val parcalar = metin.split(" ")
        val cikti = ArrayList<String>(parcalar.size)
        var cumleBasi = true
        for (parca in parcalar) {
            val m = PARCA.find(parca)
            if (m == null) {
                cikti.add(parca)
                // Harf içermeyen parça ("»", "-", "♪") cümleyi bitirmez ve cümle
                // başı durumunu BOZMAZ. Altyazılar "» " ile başlayabiliyor; eskiden
                // ardından gelen kelime cümle ortası sanılıp isim kabul ediliyor ve
                // hiç düzeltilmiyordu ("» Lefs see" düzelmiyordu).
                if (parca.any { it.isLetter() }) {
                    cumleBasi = parca.endsWith(".") || parca.endsWith("!") || parca.endsWith("?")
                }
                continue
            }
            val (on, govde, son) = m.destructured
            var yeni = govde
            val kucuk = govde.lowercase()
            // Cümle ortasında Büyük harf = isim (ellenmez). "I" ile başlayanlar hariç: "Ieel", "Ihey"
            val isimGibi = govde.firstOrNull()?.isUpperCase() == true &&
                govde.drop(1).all { !it.isUpperCase() } && !cumleBasi && govde[0] != 'I'
            // 0 ve 1 gerçek rakam değil, OCR'ın o/l harfini yanlış okuması:
            // "g0od" -> "good", "1hey" -> "they". Bunlar düzeltmeye SOKULUR.
            // Kullanıcının kaydındaki "what g0od is money" bu yüzden düzelmiyordu.
            val rakamli = govde.any { it.isDigit() && it != '0' && it != '1' }
            if (govde.length >= 2 && !Sozluk.icinde(kucuk) && !isimGibi &&
                govde != govde.uppercase() && kucuk !in I_ADLARI && !rakamli
            ) {
                val denenecek = ArrayList<String>()
                // "Ieel" -> "feel": baştaki f harfi I okunmuş
                if (govde.startsWith("I") && govde.length > 2) denenecek.add("f" + govde.substring(1))
                denenecek.addAll(adaylar(govde))
                // Sözlükte karşılığı olan TÜM adaylar toplanır, sonra seçilir.
                // Tek tek bakıp ilkini almak yanlış sonuç veriyordu: "Il" için
                // "ll" (sözlükte var ama kimse kullanmaz) "It"ten önce geliyordu.
                val uyanlar = denenecek.filter { Sozluk.icinde(it) }
                val secim = uyanlar.firstOrNull { it.lowercase() in YAYGIN_KISA }
                    ?: uyanlar.firstOrNull()
                if (secim != null) {
                    run {
                        val aday = secim
                        yeni = aday
                        if (govde[0] == 'I' || govde[0] == '1') {
                            if (cumleBasi) yeni = yeni.replaceFirstChar { it.uppercase() }
                        } else if (govde[0].isUpperCase()) {
                            yeni = yeni.replaceFirstChar { it.uppercase() }
                        }
                    }
                }
            }
            cikti.add(on + yeni + son)
            val tam = govde + son
            cumleBasi = tam.endsWith(".") || tam.endsWith("!") || tam.endsWith("?")
            if (!govde.any { it.isLetter() }) cumleBasi = true
        }
        return cikti.joinToString(" ")
    }

    // --------------------------------------------------------- bitişik ayırma

    /**
     * Bitişik okunmuş küçük harfli kelimeyi sözlükle böler:
     * "topickup" -> "to pick up". En az parçalı bölme seçilir; bölünemezse null.
     */
    fun bol(kelime: String): List<String>? {
        if (!Sozluk.hazir) return null
        val n = kelime.length
        val bedeller = DoubleArray(n + 1) { Double.MAX_VALUE }
        val yol = arrayOfNulls<List<String>>(n + 1)
        bedeller[0] = 0.0
        yol[0] = emptyList()
        for (i in 1..n) {
            for (j in maxOf(0, i - 15) until i) {
                if (yol[j] == null) continue
                val w = kelime.substring(j, i)
                // Bedel null ise bu parça kabul edilmiyor. ("when" içinde continue
                // kullanmıyoruz: eski Kotlin sürümlerinde tuzak.)
                val bedel: Double? =
                    if (w.length == 1) {
                        if (w == "a" || w == "i") 1.3 else null
                    } else if (w.length == 2 && w !in KISA_SIK) {
                        null   // "mi", "th" gibi: yabancı kelimeyi saçma parçalara bölmesin
                    } else if (Sozluk.icinde(w)) {
                        1.0
                    } else {
                        null
                    }
                if (bedel == null) continue
                val toplam = bedeller[j] + bedel
                if (toplam < bedeller[i]) {
                    bedeller[i] = toplam
                    yol[i] = yol[j]!! + w
                }
            }
        }
        val parcalar = yol[n] ?: return null
        if (parcalar.size < 2) return null
        // "herm a no" gibi: tek harflik "a" yalnız başta ya da sonda olabilir
        if (parcalar.subList(1, maxOf(1, parcalar.size - 1)).any { it == "a" }) return null
        // Kısa kelime ("cabran" -> "cab ran" olmasın): kısa bağlaç parçası yoksa bölme
        if (n <= 6 && parcalar.none { it in KISA_SIK || it.length == 1 }) return null
        return parcalar.map { if (it == "i") "I" else it }
    }

    private val NOKTALAMA_BOSLUK = Regex("(?<=[A-Za-z]{2})([,;])(?=[A-Za-z])")
    private val CUMLE_BOSLUK = Regex("(?<=[A-Za-z][a-z])([.!?])(?=[A-Z][a-z])")
    private val KUCUK_BUYUK = Regex("(?<=[a-z])(?=[A-Z])")
    private val BOLUNEBILIR = Regex("([\"(¿¡-]*)([a-z]{5,})('(?:t|s|re|ll|m|ve|d))?([^a-z']*)")
    // Baştaki harf: I (doğrusu), l ya da L (OCR'ın I yerine okuduğu hâller).
    // Kayıttan: "Iwonder", "Imay", "labsolve", "(Lpity".
    private val BASTAKI_I = Regex("([\"(-]*)[IlL]([a-z]{3,})([^a-z]*)")

    /**
     * Sözlük hunspell'in KÖK listesi: türemiş biçimler içinde yok.
     * "remarkable", "reasonable", "understandable" sözlükte bulunmuyor; önlem
     * almazsak bölücü bunları "remark able" diye parçalıyor (kullanıcının
     * kaydındaki "You have remarkable Skills." satırında tam bu oldu).
     * Bilinen bir ek atılınca geriye sözlükteki bir kök kalıyorsa kelime gerçektir.
     */
    private val EKLER = listOf(
        "ability", "ibility", "ableness", "able", "ible", "ably", "ibly",
        "fulness", "ness", "ments", "ment", "lessly", "less", "fully", "ful",
        "ships", "ship", "hood", "ward", "wise", "like", "ings", "ing",
        "ers", "est", "ies", "ied", "ier", "ly", "ish", "ism", "ist", "ity",
        "tion", "sion", "ance", "ence", "ive", "ous", "al", "ed", "es", "er", "s",
    )

    /**
     * Sözlükte olmayan ama ÖN EKLİ gerçek kelimeler: "incredible", "unhelpful".
     * Bölücü bunları "in credible" diye ayırıyordu. Ön ekten sonra kalan parça
     * UZUN olmalı (5+ harf): yoksa "inthe" -> "in the" ayırması da engellenirdi.
     */
    private val ONEKLER = listOf(
        "un", "in", "im", "ir", "il", "dis", "re", "pre", "non", "mis",
        "over", "under", "sub", "anti", "inter", "trans", "semi", "super", "multi",
    )

    private fun onekliMi(w: String): Boolean {
        for (on in ONEKLER) {
            if (!w.startsWith(on)) continue
            val kalan = w.substring(on.length)
            if (kalan.length >= 5 && Sozluk.icinde(kalan)) return true
        }
        return false
    }

    /** Kelime, sözlükteki bir kökün türemiş hâli mi? */
    fun turevMi(w: String): Boolean {
        if (!Sozluk.hazir) return false
        if (onekliMi(w)) return true
        for (ek in EKLER) {
            if (!w.endsWith(ek)) continue
            val govde = w.dropLast(ek.length)
            if (govde.length < 3) continue
            if (Sozluk.icinde(govde)) return true
            if (Sozluk.icinde(govde + "e")) return true                       // movable -> move
            if (govde.length >= 2 && govde.last() == govde[govde.length - 2] &&
                Sozluk.icinde(govde.dropLast(1))) return true                 // running -> run
            if (govde.endsWith("i") && Sozluk.icinde(govde.dropLast(1) + "y")) return true  // happily -> happy
        }
        return false
    }

    /**
     * OCR'ın boşluk kaçırdığı yerleri açar:
     * "Yo,is" -> "Yo, is" · "Iwonder" -> "I wonder" · "topickup" -> "to pick up".
     */
    fun bitisikleriAyir(metin: String): String {
        var t = NOKTALAMA_BOSLUK.replace(metin, "$1 ")
        t = CUMLE_BOSLUK.replace(t, "$1 ")
        if (!Sozluk.hazir) return t
        // küçük->Büyük geçişi ("thispointI"); McDonald, iPhone gibi bilinenler hariç
        t = t.split(" ").joinToString(" ") { p ->
            if (IZINLI_BASLIK.containsMatchIn(p)) p else KUCUK_BUYUK.replace(p, " ")
        }
        return t.split(" ").joinToString(" ") { parca -> parcayiAyir(parca) }
    }

    private fun parcayiAyir(parca: String): String {
        // 1) Baştaki I kelimeye yapışmış ya da l okunmuş:
        //    "Iwonder" -> "I wonder", "Imay" -> "I may", "labsolve" -> "I absolve".
        //    Bölmeden ÖNCE deneniyor; yoksa "labsolve" sözlükte karşılığı olan
        //    "lab solve" diye bölünüyordu.
        val m2 = BASTAKI_I.matchEntire(parca)
        if (m2 != null) {
            val kalan = m2.groupValues[2]
            // Kalan TAM bir kelime olmalı ve bütünü sözlükte OLMAMALI:
            // "Ireland", "Iron", "lover", "land" böylece bozulmaz.
            // "Iran", "Ivy", "Italy" gibi gerçek adlar bölünmesin: BÜTÜN kelimeye bakılır.
            if (!Sozluk.icinde("i$kalan") && !Sozluk.icinde("l$kalan") &&
                Sozluk.icinde(kalan) && "i$kalan".lowercase() !in I_ADLARI
            ) {
                return m2.groupValues[1] + "I " + kalan + m2.groupValues[3]
            }
        }
        // 2) Bitişik yazılmış kelimeleri sözlükle böl.
        val m = BOLUNEBILIR.matchEntire(parca) ?: return parca
        val govde = m.groupValues[2]
        val ek = m.groupValues[3]
        if (Sozluk.icinde(govde + ek) || Sozluk.icinde(govde)) return parca
        if (turevMi(govde)) return parca                       // "remarkable" bölünmesin
        if (adaylar(govde).any { Sozluk.icinde(it) }) return parca  // harf karışmış: harfleriDuzelt halleder
        val bolunmus = bol(govde) ?: return parca
        return m.groupValues[1] + bolunmus.joinToString(" ") + ek + m.groupValues[4]
    }

    /**
     * Satırın ne kadarı gerçek İngilizce? Sözlük geldiği için artık ölçebiliyoruz.
     *
     * Kullanıcının kaydından, çevrilmemesi gereken satır:
     *   "Years go by, and a man's nerve justain'twhat it used to be...
     *    Now all LCàndo Refollo M hehindwomand he Carefu"
     * Yarısından fazlası sözlükte yoksa bu OCR çöpüdür; çevirmek yerine
     * temiz okumayı beklemek daha iyi.
     */
    /**
     * Sözlükte olmayan ama gerçek olan kelimeler: sokak ağzı ve kısaltmalar.
     * hunspell listesi bunları içermiyor, sayılmazsa sağlam satırlar çöp sanılıyor.
     */
    // Sokak ağzı listesi BURADA TUTULMUYOR: tek kaynağı Slang.SOKAK.
    // Eskiden kopyası vardı ve biri güncellenince öteki unutuluyordu.
    private val ARGO get() = Slang.SOKAK

    /** Olağan bir özel ad yazımı: tek büyük harf + küçük harfler ("Raymond"). */
    private val OZEL_AD = Regex("^\\p{Lu}\\p{Ll}+$")

    private val CUMLE_SONU = setOf('.', '!', '?', '…')

    /**
     * SÜSLÜ YAZI TİPİ ÇÖPÜ. Fragmanlar ve oyunlar bazı yazıları süslü/küçük
     * büyük harfli yazı tipiyle basıyor; OCR o yazıyı tanıyamıyor ama AYNI
     * karenin altındaki düz altyazıyı tanıyor. Sonuç: başı çöp, sonu gerçek
     * bir satır. Kullanıcının 11 Kasım 9.73 kaydından (Shrek fragmanı):
     *
     *   "ch At be w As worthy o F DArrying che Drince who had to prove to the King of Far Far Away"
     *    \________________ süslü yazının çöpü ______________/\_____ gerçek altyazı _____/
     *
     * Ekrana "CH AT OLDUĞU KRAL KRALI OLDU" diye çıkıyordu.
     *
     * Kelime oranına bakan [cokBozuk] bunu yakalayamaz, çünkü satırın yarısı
     * gerçekten doğru İngilizce. Süslü yazının kendi parmak izi var:
     *   - kelime başında İKİ büyük harf, sonra küçük: "DArrying", "WOrthy"
     *   - tek başına büyük harf (I ve A hariç): "o F", "S"
     *   - tek başına küçük harf: "h", "w"
     *   - sözlükte olmayan iki harfli kırıntı: "ch", "wo"
     *   - kelime içinde rakam: "in-0ame"
     * Gerçek altyazıda bunların hiçbiri yok.
     *
     * Eşik 3: tek bir işaret yetmez (gerçek satırda "CD" ya da "4" geçebilir).
     */
    private val IKI_BUYUK = Regex("^\\p{Lu}{2}\\p{Ll}")
    private val KISALTMA = setOf(
        "cd", "tv", "pc", "ps", "ok", "us", "uk", "un", "eu", "ai", "id", "it",
        "fbi", "cia", "dna", "ceo", "atm", "gps", "usb", "led", "suv", "mvp",
        // RPG HUD / yetenek açıklamaları: HP iki kez geçince süslü çöp sanılmasın.
        "hp", "mp", "sp", "ap", "xp", "npc", "dps", "aoe", "exp",
    )

    fun susluCop(metin: String): Boolean {
        val kelimeler = metin.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (kelimeler.size < 4) return false
        var puan = 0
        for (ham in kelimeler) {
            val k = ham.trim('.', ',', '!', '?', ':', ';', '"', '\'', '’', '-', '…', '[', ']')
            if (k.isEmpty()) continue
            val kucuk = k.lowercase()
            // kelime içinde harf+rakam karışık ("in-0ame", "0ame")
            if (k.any { it.isDigit() } && k.any { it.isLetter() } && k.length >= 4) {
                puan += 2
                continue
            }
            if (IKI_BUYUK.containsMatchIn(k)) { puan += 2; continue }
            if (k.length == 1) {
                // "I" ve "A" gerçek kelime; "h", "w", "F", "S" değil.
                if (kucuk != "i" && kucuk != "a") puan += 2
                continue
            }
            if (k.length <= 3 && k == k.uppercase() && kucuk !in KISALTMA) { puan++; continue }
            if (k.length == 2 && !Sozluk.icinde(kucuk)) puan++
        }
        return puan >= 3
    }

    /**
     * Süslü yazı çöpünün ARDINDAKİ gerçek altyazıyı döner, yoksa null.
     *
     * Satırı atmak yerine kırpıyoruz: çöpün peşinde sözlükte olan 4+ ardışık
     * kelime varsa gerçek altyazı odur. Yukarıdaki örnekte
     * "who had to prove to the King of Far Far Away" kalıyor.
     */
    fun temizKuyruk(metin: String): String? {
        if (!Sozluk.hazir) return null
        val parcalar = metin.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parcalar.size < 5) return null
        // Sondan geriye: sözlükte olan (ya da özel ad olan) kelimelerin
        // kesintisiz dizisi nerede başlıyor?
        var bas = parcalar.size
        var sayac = 0
        for (i in parcalar.indices.reversed()) {
            val k = parcalar[i].trim('.', ',', '!', '?', ':', ';', '"', '\'', '’', '-', '…')
                .substringBefore('\'').substringBefore('’')
            val tanidik = k.length >= 2 && (
                Sozluk.icinde(k.lowercase()) || OZEL_AD.matches(k) ||
                    k.lowercase() in setOf("i", "a", "to", "of", "he", "be", "is", "it")
                )
            if (!tanidik) break
            bas = i
            sayac++
        }
        if (sayac < 4 || bas == 0) return null
        return parcalar.subList(bas, parcalar.size).joinToString(" ")
    }

    /**
     * 9.87 — GERÇEK SATIRI KIRPMA.
     *
     * Kullanıcının 12 Kasım 9.86 kaydından (Black Myth: Wukong, ince yazı):
     *   "Because d hate to let you miss how IH slaughter each mongrel of the Court."
     * [susluCop] "d" ve "IH" yüzünden 3 puan verdi, [temizKuyruk] satırı
     * "slaughter each mongrel of the Court." diye kırptı; cümlenin yarısı gitti.
     *
     * Süslü yazı çöpünde kuyruktan ÖNCEKİ kelimeler saçmadır ("ch", "DArrying",
     * "prncao"). Burada ise önceki kelimelerin hepsi sözlükte. Ön ekteki 3+
     * harfli kelimelerden en az 4 tane varsa ve %70'i sözlükteyse satır
     * gerçektir: ne kırpılır ne atılır.
     */
    fun onEkGercek(metin: String, kuyruk: String?): Boolean {
        if (!Sozluk.hazir) return false
        val p = metin.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val kuyrukBoyu =
            if (kuyruk == null) 0
            else kuyruk.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val onBoyu = (p.size - kuyrukBoyu).coerceIn(0, p.size)
        var toplam = 0
        var bilinen = 0
        for (i in 0 until onBoyu) {
            val k = p[i].trim('.', ',', '!', '?', ':', ';', '"', '\'', '’', '-', '…', '[', ']')
                .substringBefore('\'').substringBefore('’')
            if (k.length < 3) continue
            toplam++
            if (k.all { it.isLetter() } && !IKI_BUYUK.containsMatchIn(k) &&
                Sozluk.icinde(k.lowercase())
            ) bilinen++
        }
        return toplam >= 4 && bilinen * 10 >= toplam * 7
    }

    /**
     * 9.87 — AYNI SATIRIN BOZUK OKUMALARI.
     *
     * İnce oyun yazısında OCR aynı satırı her karede başka türlü okuyor:
     *   "Because I'dhate to let you niss how tit slaughter each mongrel of the Court."
     *   "v Becąuse Ta hate to let you miss how.ll slaughter each mongrel of the Court."
     * Harf harf karşılaştırma (%12 fark sınırı) bunları AYRI satır sayıyordu;
     * her biri yeniden çeviriye gidiyor, yapay zekâ cevabı hep "geç kaldı"
     * oluyor, ekranda hiçbir şey çıkmıyordu.
     *
     * Burada KELİME KELİME bakıyoruz, sıraya bakmadan (iki satırlı altyazıda
     * OCR satırların sırasını da değiştiriyor): kısa okumadaki kelimelerin
     * %70'i öbüründe varsa (4+ harflide tek harf farka izin var) aynı satırdır.
     * En az 8 kelime şart: kısa repliklerde ("What do you want from me?" /
     * "What do you want me to do?") bu ölçü iki ayrı satırı karıştırır.
     */
    private val SOZCUK_DISI = Regex("[^\\p{L}\\p{Nd}'’]+")
    private val AKSAN_ISARETI = Regex("\\p{Mn}+")

    private fun gurultuSozcukleri(s: String): List<String> =
        AKSAN_ISARETI.replace(
            java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD), "",
        ).lowercase().split(SOZCUK_DISI).filter { it.isNotEmpty() }

    private fun yakinSozcuk(x: String, y: String): Boolean {
        if (x == y) return true
        if (x.length < 4 || y.length < 4) return false
        val fark = x.length - y.length
        if (fark > 1 || fark < -1) return false
        // tek harf değişmiş / eksik / fazla
        var i = 0
        while (i < x.length && i < y.length && x[i] == y[i]) i++
        var j = 0
        while (j < x.length - i && j < y.length - i &&
            x[x.length - 1 - j] == y[y.length - 1 - j]
        ) j++
        return x.length - i - j <= 1 && y.length - i - j <= 1
    }

    fun ayniGurultulu(a: String, b: String): Boolean {
        val sa = gurultuSozcukleri(a)
        val sb = gurultuSozcukleri(b)
        val kisa = if (sa.size <= sb.size) sa else sb
        val uzun = if (sa.size <= sb.size) sb else sa
        if (kisa.size < 8) return false
        if (kisa.size * 2 < uzun.size) return false
        val kalan = uzun.toMutableList()
        var eslesen = 0
        for (w in kisa) {
            val i = kalan.indexOfFirst { yakinSozcuk(w, it) }
            if (i >= 0) {
                eslesen++
                kalan.removeAt(i)
            }
        }
        return eslesen * 10 >= kisa.size * 7
    }

    fun cokBozuk(metin: String): Boolean {
        if (!Sozluk.hazir) return false
        var toplam = 0
        var bilinen = 0
        var cumleBasi = true
        var i = 0
        while (i < metin.length) {
            if (!metin[i].isLetter() && metin[i] != '\'' && metin[i] != '’') {
                if (metin[i] in CUMLE_SONU) cumleBasi = true
                i++
                continue
            }
            val bas = i
            while (i < metin.length &&
                (metin[i].isLetter() || metin[i] == '\'' || metin[i] == '’')
            ) i++
            val ham = metin.substring(bas, i)
            val w = ham.substringBefore('\'').substringBefore('’')
            val cumleninIlki = cumleBasi
            cumleBasi = false
            if (w.length < 4) continue
            if (w.any { it.code > 127 }) continue      // Türkçe/aksanlı: İngilizce sayma
            toplam++
            val tanidik = Sozluk.icinde(w) || turevMi(w.lowercase()) ||
                w.lowercase() in ARGO ||
                // CÜMLE ORTASINDA büyük harfle başlayan düzgün yazılmış kelime
                // özel addır, sözlükte olmaması normal: "...ask for Raymond".
                // Cümlenin İLK kelimesi sayılmaz, orada her kelime büyük başlar;
                // "LCandoe" gibi iki büyük harfli OCR çöpü de kalıba uymaz.
                (!cumleninIlki && OZEL_AD.matches(w))
            if (tanidik) bilinen++
        }
        // Az kelimeli satırlarda oran güvenilmez; en az 4 kelime arıyoruz.
        // Eşik "yarısı VEYA fazlası bilinmiyorsa çöp": kayıttaki
        // "Athunib iog hating eases damage dealtte enomies" satırı tam %50'de
        // kalıp süzgeçten geçiyor, sonra ters anlamlı çevrilip hafızaya giriyordu.
        //
        // Özel ad ve argo muafiyeti olmadan bu eşik SAĞLAM satırları da atıyordu:
        // "Yeah. Go inside and ask for Raymond, bruh." -> 4 kelimenin 2'si
        // ("Raymond", "bruh") sözlükte yok, satır çöp sanılıp hiç çevrilmiyordu.

        // Hiç bilinen kelime yoksa kısa satırda bile çöptür.
        if (toplam >= 4 && bilinen == 0) return true

        // NOKTALAMAYLA BİTEN satır için eşik yükseliyor. Gerçek altyazı cümlesi
        // nokta/ünlem/soru ile biter; OCR'ın yarım okuduğu çöp genelde bitmez.
        // Kullanıcının 11 Kasım kaydında yabancı dil karışan GERÇEK bir replik
        // çöp sanılıp hiç çevrilmemişti:
        //   "Oyeme, mi hermano, we got a situation happening."
        // "Oyeme" ve "hermano" İspanyolca, sözlükte yok; 4 kelimenin 2'si
        // bilinmiyor diye satır atılıyordu.
        val noktaliBitiyor = metin.trimEnd().lastOrNull() in CUMLE_SONU
        val enAz = if (noktaliBitiyor) 6 else 4
        return toplam >= enAz && bilinen * 2 <= toplam
    }


    // ---- 9.74: NOTA İŞARETİ ----------------------------------------------
    // Şarkı altyazısında ♪ OCR'da "J", "D", ">", "l", "¶" olarak okunuyor ve
    // çeviriye sızıyordu: "D And he's gotta be larger than life" ->
    // "D ve o hayattan daha büyük olmalı". Satır başındaki/sonundaki TEK
    // başına duran bu işaretleri atıyoruz. "I" ve "A" gerçek kelime, listede
    // yok; kırpma sonrası en az iki kelime kalmasını şart koşuyoruz.
    private val NOTA_HARF = setOf("j", "d", ">", "<", "¶", "♪", "♫", "†", "l", "|", "r")
    private val NOTA_BAS = Regex("^[\\s>♪♫¶|]*([JjDd>♪♫¶|lr<†])[\\s]+")
    private val NOTA_SON = Regex("[\\s]+([JjDd>♪♫¶|<†])[\\s>♪♫¶|.]*$")

    fun notaTemizle(metin: String): String {
        var t = metin.trim()
        for (tur in 0 until 2) {
            val b = NOTA_BAS.find(t)
            if (b != null && b.groupValues[1].lowercase() in NOTA_HARF) {
                val kalan = t.substring(b.value.length).trim()
                if (kalan.split(Regex("\\s+")).size >= 2) t = kalan else break
            } else break
        }
        val sn = NOTA_SON.find(t)
        if (sn != null && sn.groupValues[1].lowercase() in NOTA_HARF) {
            val kalan = t.substring(0, sn.range.first).trim()
            if (kalan.split(Regex("\\s+")).size >= 2) t = kalan
        }
        // Yalnız başına kalan ♪ / > işaretleri
        t = t.trim('♪', '♫', '¶', '>', '<', ' ')
        return t.trim()
    }

    // ---- 9.74: KIRILMIŞ KONUŞMACI PARANTEZİ ------------------------------
    // "[Telia] Because..." OCR'da "Telia) Because..." oluyordu; başıboş ")"
    // çeviriye aynen giriyordu. Satır başındaki "Ad)" / "Ad]" kalıbını
    // (önünde açan parantez YOKSA) "[Ad]" diye düzeltiyoruz.
    // Başta tire olabilir: ham OCR "-Nolan) Earth?" veriyor, tire sonradan
    // siliniyor; kalıp "^" ile başladığı için bu satırlar kaçıyordu.
    private val KIRIK_HITAP = Regex("^([\\s\\-–]*)(\\p{Lu}[\\p{L}'’]{1,19})[)\\]](\\s+)")

    fun hitapParantezi(metin: String): String {
        val m = KIRIK_HITAP.find(metin) ?: return metin
        return m.groupValues[1] + "[" + m.groupValues[2] + "]" + m.groupValues[3] +
            metin.substring(m.value.length)
    }

    // ---- 9.74: BÖLÜNMÜŞ KELİMEYİ BİRLEŞTİR -------------------------------
    // "it's gotta be s Oon" -> "soon". OCR kelimeyi ikiye bölüyor. Yalnız
    // 1-2 harfli bir parça + ardından gelen parça SÖZLÜKTE bir kelime
    // oluşturuyorsa birleştiriyoruz; sözlük şartı kuralı kendi kendine
    // koruyor ("an near" -> "annear" yok, dokunulmuyor).
    private val KISA_GERCEK = setOf(
        "a", "i", "o", "of", "to", "in", "on", "at", "is", "it", "be", "he",
        "we", "my", "no", "so", "do", "go", "up", "or", "as", "by", "if",
        "me", "us", "an", "am", "ok", "oh", "ah", "eh", "ye", "ya", "hi",
    )

    fun bolunmusuBirlestir(metin: String): String {
        if (!Sozluk.hazir) return metin
        val p = metin.split(" ").toMutableList()
        if (p.size < 2) return metin
        var i = 0
        while (i < p.size - 1) {
            val a = p[i]
            val b = p[i + 1]
            // İlk parçanın SONU noktalamayla bitiyorsa kelime ORADA bitmiştir;
            // iki kelimeyi birleştirmek yanlış olur. Eski hâl bunu
            // denetlemiyordu ve ayrıca ön eki yanlış hesaplıyordu
            // ("a.length - aSade.length" sondaki noktalamayı da sayıyordu):
            //   "Lu? Lu? Siktir."  ->  "LLulu? Siktir."
            // Gerçek kayıt korpusunda değişmezlik testiyle bulundu.
            val aOn = a.takeWhile { !it.isLetter() }          // baştaki noktalama
            val aGovde = a.substring(aOn.length)
            if (aGovde.isEmpty() || !aGovde.last().isLetter()) { i++; continue }
            val aSade = aGovde
            val bOn = b.takeWhile { it.isLetter() }
            val bKuyruk = b.substring(bOn.length)
            // Sözlükte a..z tek harfleri de var; tek harfli parçada sözlük
            // kontrolü yapılırsa kural hiç çalışmıyor ("s Oon" -> "soon").
            if (aSade.length !in 1..2 || bOn.length < 2 ||
                aSade.any { !it.isLetter() } ||
                aSade.lowercase() in KISA_GERCEK ||
                (aSade.length == 2 && Sozluk.icinde(aSade.lowercase()))
            ) { i++; continue }
            val birlesik = (aSade + bOn).lowercase()
            if (!Sozluk.icinde(birlesik)) { i++; continue }
            p[i] = aOn + aSade + bOn.lowercase() + bKuyruk
            p.removeAt(i + 1)
        }
        return p.joinToString(" ")
    }

    // ---- 9.77: AYRILMIŞ "-in'" EKİ --------------------------------------
    // OCR "Talkin'" sözcüğünü ikiye bölüyor: "Talk in'". Draft bunu
    // "'Bu çocuklara konuş" yapıyordu. Kök + "ing" sözlükteyse birleştir;
    // sonra argo katmanı "talkin'" -> "talking" düzeltmesini yapıyor.
    private val ING_AYRI = Regex("\\b(\\p{L}{3,})\\s+(in['’])(?=\\s|$|[.,!?])")

    fun ingAyrildi(metin: String): String {
        if (!Sozluk.hazir) return metin
        return ING_AYRI.replace(metin) { m ->
            val kok = m.groupValues[1]
            if (Sozluk.icinde((kok + "ing").lowercase())) kok + m.groupValues[2]
            else m.value
        }
    }

    // ---- 9.84: BOZUK OKUNAN "I" ZAMİRİ ---------------------------------
    // İtalik "I" üç biçimde bozuluyor (12 Kasım kaydı):
    //   "/ thought he was."        -> eğik çizgi      (taslak: "/ sandılar")
    //   "if lm being honest"       -> küçük L + m     ("I'm")
    //   "You thinkl would ever"    -> önceki kelimeye yapışık küçük L
    //                                 (taslak: "Thinkl, ... yardım ederdin mi?")
    // Üçüncüsü sözlükle korunuyor: "thinkl" sözlükte YOK, "think" VAR ve
    // ardından yardımcı fiil geliyor. "tell would" gibi gerçek kelimeye
    // dokunulmuyor.
    private val EGIK_I = Regex("(^|[\\s\\]\\)\\-–])/(?=\\s+\\p{Ll})")
    private val LM = Regex("(?<![\\p{L}'’])l['’]?m(?![\\p{L}'’])")
    private val YAPISIK_L = Regex(
        "(?<![\\p{L}'’])(\\p{L}{3,})l(\\s+)(would|will|could|can|should|have|had|was|am|" +
            "did|do|don't|didn't|can't|won't|wouldn't|couldn't|think|know|need|want)(?![\\p{L}])",
    )

    fun bozukI(metin: String): String {
        var t = EGIK_I.replace(metin) { it.groupValues[1] + "I" }
        t = LM.replace(t, "I'm")
        if (Sozluk.hazir) {
            t = YAPISIK_L.replace(t) { m ->
                val kok = m.groupValues[1]
                val tam = (kok + "l").lowercase()
                if (!Sozluk.icinde(tam) && Sozluk.icinde(kok.lowercase()))
                    kok + " I" + m.groupValues[2] + m.groupValues[3]
                else m.value
            }
        }
        return t
    }

    // ---- 9.74: KESME İŞARETİ DÜŞMESİ -------------------------------------
    // OCR kesme işaretini yutuyor: "Were here to show you..." geçmiş zamana
    // çevriliyordu ("buradaydık"). Yalnız arkasından gelen kelime "we're"i
    // kesinleştiriyorsa düzeltiyoruz; "Were you there?" bozulmuyor.
    private val KESME_ARDIL = listOf(
        "here", "there", "gonna", "ready", "done", "back", "about",
        "going", "just", "glad", "happy", "sorry", "live",
        "excited", "thrilled", "proud", "talking", "looking", "trying",
    )

    // "were" GERÇEK bir geçmiş zaman kelimesi: "These were done by hand."
    // cümlesini bozmamak için yalnız cümle/clause başında düzeltiyoruz.
    // "youre"/"theyre" İngilizce kelime değil, her yerde düzeltilebilir.
    private val KESME_BELIRSIZ: List<Pair<Regex, String>> = KESME_ARDIL.map { ard ->
        Regex(
            "(^|[.!?…\"'\\[\\]()–—-]\\s*)(were)(\\s+$ard\\b)",
            RegexOption.IGNORE_CASE,
        ) to "we're"
    }

    private val KESME_KESIN: List<Pair<Regex, String>> =
        listOf("theyre" to "they're", "youre" to "you're").flatMap { (ham, dogru) ->
            KESME_ARDIL.map { ard ->
                Regex(
                    "(?<![\\p{L}'’])($ham)(\\s+$ard\\b)",
                    RegexOption.IGNORE_CASE,
                ) to dogru
            }
        }

    fun kesmeyiGeriKoy(metin: String): String {
        var t = metin
        fun ayniHarf(ilk: String, dogru: String) =
            if (ilk.first().isUpperCase()) dogru.replaceFirstChar { it.uppercase() } else dogru
        for ((r, dogru) in KESME_BELIRSIZ) {
            t = r.replace(t) { m ->
                m.groupValues[1] + ayniHarf(m.groupValues[2], dogru) + m.groupValues[3]
            }
        }
        for ((r, dogru) in KESME_KESIN) {
            t = r.replace(t) { m -> ayniHarf(m.groupValues[1], dogru) + m.groupValues[2] }
        }
        return t
    }

    // ---- 9.74: ÇÖP ÖN EK ------------------------------------------------
    // "hegd ahtdd Surprise." -> "Surprise.". susluCop bu satıra puan
    // vermiyor (büyük harf/rakam işareti yok) ve cokBozuk noktayla bittiği
    // için 6 kelime arıyor. Burada ÖNDEKİ kelimelerin HEPSİ sözlükte yoksa
    // ve içlerinden biri İngilizcede olmayan ünsüz yığını taşıyorsa, tek
    // bilinen kuyruk kelimesi de kırpmaya yetiyor.
    private val UNSUZ_YIGINI = Regex("[bcdfghjklmnpqrstvwxz]{4}")
    private val UNLU = Regex("[aeiouy]")

    private val KISALTMA_EKI =
        Regex("\\p{L}(?:n['’]t|['’](?:ll|re|ve|d|s|m))", RegexOption.IGNORE_CASE)

    fun copOnEki(metin: String): String? {
        if (!Sozluk.hazir) return null
        val p = metin.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (p.size < 3) return null
        fun sade(w: String) = w.trim('.', ',', '!', '?', ':', ';', '"', '\'', '’', '-', '…', '[', ']')
            .substringBefore('\'').substringBefore('’')
        fun bilinir(w: String): Boolean {
            val k = sade(w)
            if (k.length < 2) return k.lowercase() == "i" || k.lowercase() == "a"
            return Sozluk.icinde(k.lowercase()) || turevMi(k.lowercase()) ||
                k.lowercase() in ARGO || OZEL_AD.matches(k)
        }
        var bas = p.size
        for (i in p.indices.reversed()) {
            if (!bilinir(p[i])) break
            bas = i
        }
        if (bas == 0 || bas == p.size) return null       // hepsi temiz / hiç temiz kuyruk yok
        val on = p.subList(0, bas)
        if (on.size < 2) return null                      // tek bilinmeyen ad olabilir
        if (on.any { sade(it).length < 3 }) return null    // "mi hermano" gibi kısa yabancı sözcük
        if (on.any { bilinir(it) }) return null
        // 9.87: "wouldn'tibe funifl phck you extra eye..." — ön ekte İngilizce
        // kısaltma (n't, 'll, 'd...) varsa bu çöp değil, YAPIŞIK okunmuş gerçek
        // cümledir; kırpılırsa cümlenin başı gider.
        if (on.any { KISALTMA_EKI.containsMatchIn(it) }) return null
        val sacma = on.any { w ->
            val k = sade(w).lowercase()
            UNSUZ_YIGINI.containsMatchIn(k) || !UNLU.containsMatchIn(k)
        }
        if (!sacma) return null
        return p.subList(bas, p.size).joinToString(" ")
    }

    private val THCY = Regex("\\b([Tt])hcy\\b")
    private val THC = Regex("\\b([Tt])hc\\b")
    private val BOSLUK = Regex("\\s+")

    /** Tüm onarım: ayırma -> harf düzeltme -> sık hatalar. Sözlük yoksa metne dokunmaz. */
    fun onar(ham: String): String {
        if (ham.isBlank()) return ham
        var t = BOSLUK.replace(ham, " ").trim()
        t = notaTemizle(t)
        t = hitapParantezi(t)
        t = bitisikleriAyir(t)
        t = harfleriDuzelt(t)
        t = bolunmusuBirlestir(t)
        t = ingAyrildi(t)
        t = bozukI(t)
        t = kesmeyiGeriKoy(t)
        t = THCY.replace(t, "$1hey")
        t = THC.replace(t, "$1he")
        return BOSLUK.replace(t, " ").trim()
    }
}
