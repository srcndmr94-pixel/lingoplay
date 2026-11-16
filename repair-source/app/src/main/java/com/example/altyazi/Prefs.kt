package com.example.altyazi

import android.content.Context

/** Tüm ayarlar burada saklanır. Servis ve ekran aynı yerden okur. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("altyazi", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) = sp.edit().putString("apiKey", v.trim()).apply()

    /** Oyun, Dizi, Film gibi içerik türü. Çeviri talimatına girer. */
    var contentType: String
        get() = (sp.getString("contentType", "") ?: "").ifBlank { "Oyun" }
        set(v) = sp.edit().putString("contentType", v.trim()).apply()

    var gameName: String
        get() = sp.getString("gameName", "") ?: ""
        set(v) = sp.edit().putString("gameName", v.trim()).apply()

    /** 0 = Anthropic (ücretli), 1 = Google Gemini (ücretsiz katman). */
    var provider: Int
        get() = sp.getInt("provider", PROVIDER_GEMINI)
        set(v) = sp.edit().putInt("provider", v).apply()

    /** Google AI Studio anahtarı. */
    var geminiKey: String
        get() = (sp.getString("geminiKey", "") ?: "").ifBlank { GOMULU_ANAHTAR }
        set(v) = sp.edit().putString("geminiKey", v.trim()).apply()

    var geminiModel: String
        get() = (sp.getString("geminiModel", "") ?: "").ifBlank { DEFAULT_GEMINI_MODEL }
        set(v) = sp.edit().putString("geminiModel", v.trim()).apply()

    /** Seçili sağlayıcının anahtarı. */
    val activeKey: String
        get() = if (provider == PROVIDER_GEMINI) geminiKey else apiKey

    var model: String
        get() = (sp.getString("model", "") ?: "").ifBlank { DEFAULT_MODEL }
        set(v) = sp.edit().putString("model", v.trim()).apply()

    /**
     * 0 = Anında: sadece telefonun içindeki çeviri, ~0,3 sn, hiç değişmez.
     * 1 = Yapay zekâ: ~1 sn gecikir, hiç değişmez, en doğru çeviri.
     * 2 = Karma: önce anında çıkar, yapay zekâ gelince bir kez düzelir.
     */
    var mode: Int
        // API anahtarı yoksa internet modları çalışamaz; sessizce kaba çeviriye düşmesin diye
        // çevrim dışı moda (telefondaki yapay zekâ) dönülür.
        get() = sp.getInt("mode", MODE_INSTANT).let { if (it != MODE_INSTANT && activeKey.isBlank()) MODE_INSTANT else it }
        set(v) = sp.edit().putInt("mode", v).apply()

    val useAi: Boolean
        get() = mode != MODE_INSTANT

    /** Doğruluk öncelikli: hızlı çeviriyi gösterme, yapay zekânın çevirisini bekle (varsayılan). */
    var accuracyFirst: Boolean
        get() = sp.getBoolean("accuracyFirst", true)
        set(v) = sp.edit().putBoolean("accuracyFirst", v).apply()

    /** GameLingo TV: çeviriyi telefondaki yapay zekâya yaptır (bkz. PhoneBridge.kt). */
    var bridgeOn: Boolean
        get() = sp.getBoolean("bridgeOn", false)
        set(v) = sp.edit().putBoolean("bridgeOn", v).apply()

    /** Telefonun gösterdiği 4 haneli eşleşme kodu. */
    var bridgePin: String
        get() = sp.getString("bridgePin", "") ?: ""
        set(v) = sp.edit().putString("bridgePin", v.trim()).apply()

    /** Telefonun ev ağındaki adresi (otomatik bulunur). */
    var bridgeHost: String
        get() = sp.getString("bridgeHost", "") ?: ""
        set(v) = sp.edit().putString("bridgeHost", v.trim()).apply()

    /** TV modu: altyazı büyük ekrana göre (bkz. TvMode.kt). */
    var tvMode: Boolean
        get() = sp.getBoolean("tvMode", false)
        set(v) = sp.edit().putBoolean("tvMode", v).apply()

    /** TV bağlanınca TV modunu kendiliğinden aç. */
    var tvAuto: Boolean
        get() = sp.getBoolean("tvAuto", true)
        set(v) = sp.edit().putBoolean("tvAuto", v).apply()

    /** TV modu otomatik açıldıysa true (kablo çıkınca otomatik kapanır). */
    var tvAutoOn: Boolean
        get() = sp.getBoolean("tvAutoOn", false)
        set(v) = sp.edit().putBoolean("tvAutoOn", v).apply()

    /** TV bağlıyken kullanıcı elle Telefon'u seçti: kablo çıkana kadar otomatik açma. */
    var tvAutoSuppressed: Boolean
        get() = sp.getBoolean("tvAutoSuppressed", false)
        set(v) = sp.edit().putBoolean("tvAutoSuppressed", v).apply()

    /** 10.2: anlık taslak için Firefox çeviri modeli (Offline Translator uygulaması). */
    /** Unverified local OPUS-MT ONNX engine: opt-in only after importing a model package. */
    var opusMtEnabled: Boolean
        get() = sp.getBoolean("opusMtEnabled", false)
        set(v) = sp.edit().putBoolean("opusMtEnabled", v).apply()

    /** Explicit opt-in: DeepL API charges/quota may apply. Default stays local OPUS-MT. */
    var deepLEnabled: Boolean
        get() = sp.getBoolean("deepLEnabled", false)
        set(v) = sp.edit().putBoolean("deepLEnabled", v).apply()

    var firefoxMotoru: Boolean
        get() = sp.getBoolean("firefoxMotoru", true)
        set(v) = sp.edit().putBoolean("firefoxMotoru", v).apply()

    /** 10.7: anahtarsız Google Çeviri (Chrome'daki çeviri). Açıkken Gemini/anahtar kullanılmaz. */
    var googleCeviri: Boolean
        get() = sp.getBoolean("googleCeviri", true)
        set(v) = sp.edit().putBoolean("googleCeviri", v).apply()

    var hizliHafizaSurumu: Int
        get() = sp.getInt("hizliHafizaSurumu", 0)
        set(v) = sp.edit().putInt("hizliHafizaSurumu", v).apply()

    var showSpeaker: Boolean
        get() = sp.getBoolean("showSpeaker", true)
        set(v) = sp.edit().putBoolean("showSpeaker", v).apply()

    /** Menü, görev ve tuş yazılarını da kendi yerlerinde anında çevirsin mi? */
    /**
     * Yapay zekâ HER satırda mı çalışsın?
     *
     * Kapalıyken (varsayılan) taslak sağlamsa yapay zekâ hiç çağrılmaz: gecikme
     * olmaz, ekrandaki yazı bir daha değişmez. Açıkken her satır yapay zekâya
     * gider — daha iyi çeviri ama 1,5-3,5 sn gecikme ve taslaktan düzeltilmişe
     * geçiş. Her iki durumda da yapay zekânın cevabı denetimden geçer (AiGuard).
     */
    var alwaysAi: Boolean
        get() = sp.getBoolean("alwaysAi", false)
        set(v) = sp.edit().putBoolean("alwaysAi", v).apply()

    var menuTranslate: Boolean
        get() = sp.getBoolean("menuTranslate", false)
        set(v) = sp.edit().putBoolean("menuTranslate", v).apply()

    /** Açıkken, çeviri çıkmadığında ekranda sebebini ve okunan metni gösterir. */
    var diagnostics: Boolean
        get() = sp.getBoolean("diagnostics", false)
        set(v) = sp.edit().putBoolean("diagnostics", v).apply()

    init {
        // Yeni sürümde altyazı bölgesi daha dar; eski kayıt varsa bir kez güncellenir.
        if (sp.getInt("defaultsVersion", 0) < 12) {
            sp.edit()
                .putInt("topPct", 74)
                .putInt("bottomPct", 99)
                .putBoolean("menuTranslate", false)
                // Varsayılan artık çevrimdışı: anahtar yok, kota yok, bekleme
                // yok. Yapay zekâ isteyen Gelişmiş'ten açar.
                .putInt("mode", MODE_INSTANT)
                // Tarama araligi: her 150 ms'de bir bakmak, altyazinin
                // ortalama 75 ms gec yakalanmasi demekti. 80'e cekiyoruz.
                .putInt("intervalMs", 80)
                .putBoolean("diagnostics", false)
                // Eski kayıtlardaki geçersiz model adını temizliyoruz; boş
                // kalınca aşağıdaki geçerli varsayılan devreye girer.
                .putString("geminiModel", "")
                .putInt("defaultsVersion", 12)
                .apply()
        }
    }

    /** Altyazı bölgesinin ekran yüksekliğine göre üst sınırı, yüzde. */
    var topPct: Int
        get() = sp.getInt("topPct", 74)
        set(v) = sp.edit().putInt("topPct", v).apply()

    /** Altyazı bölgesinin sol sınırı, yüzde. */
    var leftPct: Int
        get() = sp.getInt("leftPct", 8)
        set(v) = sp.edit().putInt("leftPct", v.coerceIn(0, 95)).apply()

    /** Altyazı bölgesinin sağ sınırı, yüzde. */
    var rightPct: Int
        get() = sp.getInt("rightPct", 92)
        set(v) = sp.edit().putInt("rightPct", v.coerceIn(5, 100)).apply()

    /** Altyazı bölgesinin alt sınırı, yüzde. */
    var bottomPct: Int
        get() = sp.getInt("bottomPct", 99)
        set(v) = sp.edit().putInt("bottomPct", v).apply()

    /** İki tarama arasındaki bekleme, milisaniye. Küçük = hızlı ama daha çok pil. */
    var intervalMs: Int
        get() = sp.getInt("intervalMs", 80)
        set(v) = sp.edit().putInt("intervalMs", v).apply()

    /**
     * Çeviri ekranda en az kaç saniye kalsın (1-10).
     *
     * Oyunun altyazısı kaybolunca bizimki yarım saniye içinde siliniyordu;
     * Türkçe çeviri genelde İngilizcesinden uzun olduğu için okumaya vakit
     * kalmıyordu. Yeni replik gelirse bu süre beklenmez, hemen değişir.
     */
    var minShowSec: Int
        get() = sp.getInt("minShowSec", 3).coerceIn(1, 10)
        set(v) = sp.edit().putInt("minShowSec", v.coerceIn(1, 10)).apply()

    /**
     * Hızlı konuşmada önceki replik üstte kalsın mı.
     *
     * Yapay zekâ düzeltmesi 2,5-4,5 sn sürüyor, altyazı satırları da 3-5 sn
     * ekranda kalıyor; düzeltme hep satırın son yarım saniyesinde yetişiyor ve
     * okunmadan yeni satır onu siliyordu. Açıkken silinmiyor: küçülüp
     * solgunlaşarak bir üst sıraya çıkıyor ve 1,5 sn daha okunabiliyor.
     *
     * Kapatılabilir: telefonda yatay oynarken iki kutu oyunun üstünü kapatıyor
     * diye düşünen kullanıcı tek satıra dönebilsin.
     */
    var yigin: Boolean
        get() = sp.getBoolean("yigin", true)
        set(v) = sp.edit().putBoolean("yigin", v).apply()

    /**
     * Çeviri, yapay zekâ cevap verene kadar beklesin mi.
     *
     * KAPALI (varsayılan): hızlı çeviri anında çıkar, yapay zekâ bitince
     * yazı düzelir. Uygulamanın baştan beri yaptığı şey; "anlık" olan bu.
     * AÇIK: satır 1-2 sn geç çıkar ama tek seferde doğru çıkar, yazı
     * taslaktan düzeltilmişe geçmez.
     *
     * Varsayılanı KAPALI: 9.45'te açık geldi ve kullanıcı haklı olarak
     * "geç okuyor, anlık ile alakası kalmadı" dedi. Gecikme bir tercih,
     * varsayılan davranış değil.
     */
    var dogruyuBekle: Boolean
        get() = sp.getBoolean("dogruyuBekle", false)
        set(v) = sp.edit().putBoolean("dogruyuBekle", v).apply()

    /**
     * YALNIZ DOĞRU ÇEVİRİ: hızlı motorun taslağı HİÇ gösterilmez.
     *
     * Ekrana yalnız güvenilir kaynaklar çıkar: sözlük, ses etiketi, hafıza
     * ve yapay zekânın çevirisi. Taslak arka planda yine üretilir (yapay
     * zekâya ipucu ve yedek olarak) ama yalnız yapay zekâ cevap veremezse
     * ya da cevabı reddedilirse görünür; ekran boş kalmasın diye.
     * Bedeli: satır, yapay zekâ bitene kadar (1,5-4 sn) görünmez.
     */
    var yalnizDogru: Boolean
        get() = sp.getBoolean("yalnizDogru", false)
        set(v) = sp.edit().putBoolean("yalnizDogru", v).apply()

    /**
     * 9.91 — ÇEVİRİ BİÇİMİ: beş ayrı kutunun yerine tek seçim.
     *
     * "Doğruluk öncelikli", "önce doğruyu bekle", "yalnız doğru çeviri",
     * "önceki satır üstte kalsın" ve "yapay zekâ her satırda" kutuları
     * birbirini etkiliyordu; yanlış bir bileşim (12 Kasım 9.89 kaydı: hız
     * öncelikli + yığın + yalnız doğru) ekranda iki ayrı repliğin çevirisini
     * yan yana bırakıyor, okunamaz hâle getiriyordu. Kutular artık ekranda
     * yok; değerlerini bu seçim yazıyor.
     */
    var ceviriBicimi: Int
        get() = sp.getInt("ceviriBicimi", BICIM_DENGELI)
        set(v) = sp.edit().putInt("ceviriBicimi", v).apply()

    /** Seçilen biçimi eski beş ayara çevirir; kodun geri kalanı onları okur. */
    fun bicimUygula(bicim: Int) {
        ceviriBicimi = bicim
        accuracyFirst = true
        dogruyuBekle = false
        yigin = false
        alwaysAi = false
        yalnizDogru = bicim == BICIM_DOGRU
    }

    /**
     * Güncellemeden gelen eski kutu bileşimlerini temizler: biçim hiç
     * seçilmemişse "Dengeli"den başlanır. Kaldırılan "internet karma" modu
     * "internet" sayılır. Her açılışta çağrılabilir, zararsızdır.
     */
    fun bicimiOnar() {
        if (!sp.contains("ceviriBicimi")) bicimUygula(BICIM_DENGELI) else bicimUygula(ceviriBicimi)
        if (sp.getInt("mode", MODE_INSTANT) == MODE_HYBRID) mode = MODE_AI
    }

    // ---------------------------------------------------- altyazı görünümü

    /** Altyazı yazı boyutu (sp). */
    var subtitleSize: Int
        get() = sp.getInt("subtitleSize", 18).coerceIn(12, 34)
        set(v) = sp.edit().putInt("subtitleSize", v.coerceIn(12, 34)).apply()

    /** 0 beyaz · 1 sarı · 2 açık yeşil · 3 turkuaz */
    var subtitleColor: Int
        get() = sp.getInt("subtitleColor", COLOR_WHITE)
        set(v) = sp.edit().putInt("subtitleColor", v).apply()

    /** 0 siyah · 1 koyu gri · 2 koyu lacivert */
    var subtitleBgColor: Int
        get() = sp.getInt("subtitleBgColor", BGC_BLACK)
        set(v) = sp.edit().putInt("subtitleBgColor", v).apply()

    /** 0 yarı saydam · 1 tam siyah · 2 arka plan yok */
    var subtitleBg: Int
        get() {
            // 9.29: varsayılan artık "Tam dolu" (koyu siyah). Eski varsayılanda (yarı saydam) kalanlar
            // bir kereye mahsus tam doluya geçer; sonra kullanıcı ne seçerse o kalır.
            if (!sp.getBoolean("bgTamDoluGecis", false)) {
                val e = sp.edit().putBoolean("bgTamDoluGecis", true)
                if (sp.getInt("subtitleBg", BG_SEMI) == BG_SEMI) e.putInt("subtitleBg", BG_SOLID)
                e.apply()   // apply() bellekteki değeri hemen günceller
            }
            return sp.getInt("subtitleBg", BG_SOLID)
        }
        set(v) = sp.edit().putInt("subtitleBg", v).apply()

    /** 0 şeridin üstünde · 1 şeridin üstünü kapat · 2 ekranın en altı */
    var subtitlePos: Int
        get() = sp.getInt("subtitlePos", POS_ABOVE)
        set(v) = sp.edit().putInt("subtitlePos", v).apply()

    /** Yazının çevresine koyu kenarlık; parlak sahnelerde okunurluğu artırır. */
    var subtitleOutline: Boolean
        get() = sp.getBoolean("subtitleOutline", true)
        set(v) = sp.edit().putBoolean("subtitleOutline", v).apply()

    companion object {
        /** Gömülü Google anahtarı; ayarlardan değiştirilebilir. */
        /** Kasıtlı boş: açık depoya gömülen anahtarlar iptal ediliyor. */
        const val GOMULU_ANAHTAR = ""

        const val DEFAULT_MODEL = "claude-haiku-5-5"

        /**
         * Hesapta gerçekten bulunan, hızlı ve hafif model.
         * Önceki varsayılan ("gemini-3.8") hiçbir hesapta yoktu; her replikte
         * "Model bulunamadı" alınmasının kaynağı buydu.
         */
        // 9.89: "gemini-3.1-flash-lite-preview" Mayıs 2026'da kapatıldı.
        const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash-lite"

        const val PROVIDER_ANTHROPIC = 0
        const val PROVIDER_GEMINI = 1
        const val BICIM_HIZLI = 0
        const val BICIM_DENGELI = 1
        const val BICIM_DOGRU = 2

        const val MODE_INSTANT = 0
        const val MODE_AI = 1
        const val MODE_HYBRID = 2

        // Altyazı görünümü seçenekleri
        const val COLOR_WHITE = 0
        const val COLOR_YELLOW = 1
        const val COLOR_GREEN = 2
        const val COLOR_CYAN = 3

        const val BGC_BLACK = 0
        const val BGC_GRAY = 1
        const val BGC_NAVY = 2

        const val BG_SEMI = 0
        const val BG_SOLID = 1
        const val BG_NONE = 2

        const val POS_ABOVE = 0
        const val POS_COVER = 1
        const val POS_BOTTOM = 2
        const val POS_BELOW = 3   // İngilizce altyazının hemen altında
    }
}
