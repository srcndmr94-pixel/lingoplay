package com.example.altyazi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * GOOGLE ÇEVİRİ (anahtarsız; resmî olmayan uç nokta, 429 sınırı olabilir).
 *
 * Kullanıcı: "Chrome'da sorun olmuyor, Firefox'ta kaba çeviri oluyor. API
 * anahtarı kullanıp kota derdiyle uğraşmak istemiyorum."
 *
 * Chrome'un çevirisi Google Translate'in sunucudaki modelinden gelir.
 * Aynı modele anahtarsız ulaşılan herkese açık adres (client=gtx) burada
 * kullanılıyor: anahtar yok, kota sayacı yok, ücret yok. Resmî, belgelenmiş
 * bir hizmet değil; Google çok yoğun kullanımda bir süre engelleyebilir. O
 * yüzden:
 *   - her cevap için en çok ZAMAN_ASIMI_MS beklenir,
 *   - üst üste ARA_HATA_SAYISI hata olursa ARA_MS boyunca hiç denenmez,
 *   - bu sürelerde taslak Firefox'tan (yoksa yerleşik motordan) gelir.
 * Aynı satır bir daha sorulmaz: son cevaplar hafızada tutulur.
 */
object GoogleCeviri {

    const val ZAMAN_ASIMI_MS = 1500L
    const val ARA_HATA_SAYISI = 3
    const val ARA_MS = 60_000L
    private const val HAFIZA = 400

    @Volatile var basarili = 0
    @Volatile var basarisiz = 0
    @Volatile var sonHata: String? = null
    // İsteğe bağlı tanı kaydı: Google'a giren gerçek metin ve ham karşılık.
    @Volatile var trace: ((String) -> Unit)? = null
    @Volatile private var ardArdaHata = 0
    @Volatile private var araBitis = 0L
    private val rateGuard = GoogleRateGuard()
    private val stateLock = Any()
    private val latency = LatencyWindow1037()

    private val istemci = OkHttpClient.Builder()
        .connectTimeout(ZAMAN_ASIMI_MS, TimeUnit.MILLISECONDS)
        .readTimeout(ZAMAN_ASIMI_MS, TimeUnit.MILLISECONDS)
        .callTimeout(ZAMAN_ASIMI_MS, TimeUnit.MILLISECONDS)
        .build()

    private val hafiza = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > HAFIZA
    }

    /** Şu an denenebilir mi (art arda hatalardan sonra ara verilmediyse). */
    val kullanilabilir: Boolean
        get() = System.currentTimeMillis() >= araBitis &&
            rateGuard.remainingMs(System.currentTimeMillis()) == 0L

    fun durum(): String =
        "Google Çeviri (anahtarsız): $basarili çeviri, $basarisiz başarısız" +
            (if (!kullanilabilir) " · ${maxOf((araBitis - System.currentTimeMillis()).coerceAtLeast(0L), rateGuard.remainingMs(System.currentTimeMillis())) / 1000} sn ara" else "") +
            (latency.summary()?.let { " · $it" } ?: "") +
            (sonHata?.let { " · son hata: $it" } ?: "")

    fun temizle() {
        ardArdaHata = 0
        araBitis = 0L
        // Servis yeniden başlatıldı diye sunucunun 429 beklemesini sıfırlama.
        // Aynı uygulama süreci içindeki rateGuard korunur.
    }

    /** İngilizce -> Türkçe. Başarısızsa null; çağıran Firefox'a düşer. */
    suspend fun cevir(metin: String, kullanHafiza: Boolean = true): String? {
        val kaynak = metin.trim()
        if (kaynak.isEmpty()) return null
        if (kullanHafiza) synchronized(hafiza) { hafiza[kaynak] }?.let { return it }
        if (!kullanilabilir) return null
        return withContext(Dispatchers.IO) {
            val startedNs = System.nanoTime()
            var httpCode: Int? = null
            var retryAfter: String? = null
            val sonuc = try {
                val adres = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
                    .addQueryParameter("client", "gtx")
                    .addQueryParameter("sl", "en")
                    .addQueryParameter("tl", "tr")
                    .addQueryParameter("dt", "t")
                    .addQueryParameter("q", kaynak)
                    .build()
                val istek = Request.Builder().url(adres).get().build()
                istemci.newCall(istek).execute().use { cevap ->
                    if (!cevap.isSuccessful) {
                        httpCode = cevap.code
                        retryAfter = cevap.header("Retry-After")
                        sonHata = "HTTP ${cevap.code}"
                        null
                    } else {
                        val govde = cevap.body?.string().orEmpty()
                        val metinTr = coz(govde)
                        if (metinTr == null) sonHata = "anlaşılmayan cevap"
                        metinTr
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sonHata = e.message ?: "bağlantı hatası"
                null
            }
            latency.add((System.nanoTime() - startedNs) / 1_000_000L)
            if (sonuc == null) {
                trace?.invoke("GOOGLE HAM: ${kaynak.take(85)} => BAŞARISIZ (${sonHata ?: "yanıt yok"})")
                synchronized(stateLock) {
                    basarisiz++
                    if (httpCode == 429) {
                        rateGuard.on429(System.currentTimeMillis(), retryAfter)
                        ardArdaHata = 0
                    } else {
                        ardArdaHata++
                        if (ardArdaHata >= ARA_HATA_SAYISI) {
                            araBitis = System.currentTimeMillis() + ARA_MS
                            ardArdaHata = 0
                        }
                    }
                }
            } else {
                trace?.invoke("GOOGLE HAM: ${kaynak.take(85)} => ${sonuc.take(110)}")
                synchronized(stateLock) {
                    basarili++
                    ardArdaHata = 0
                    if (rateGuard.remainingMs(System.currentTimeMillis()) == 0L) {
                        rateGuard.onSuccess()
                        sonHata = null
                    }
                }
                // Raw Google LRU must not repeatedly serve a translation that
                // failed the same meaning/profanity checks as the overlay.
                if (kullanHafiza && TranslationQualityGate.mayRemember(kaynak, sonuc)) {
                    synchronized(hafiza) { hafiza[kaynak] = sonuc }
                }
            }
            sonuc
        }
    }

    /**
     * Cevap biçimi: [[["Çeviri 1","Source 1",...],["Çeviri 2","Source 2",...]], ...]
     * İlk dizinin her elemanının ilk alanı çevrilmiş bir cümle parçasıdır.
     */
    fun coz(govde: String): String? = try {
        val dis = JSONArray(govde)
        val parcalar = dis.optJSONArray(0)
        if (parcalar == null) {
            null
        } else {
            val sb = StringBuilder()
            for (i in 0 until parcalar.length()) {
                val p = parcalar.optJSONArray(i) ?: continue
                val tr = p.opt(0)
                if (tr is String) sb.append(tr)
            }
            val s = sb.toString().trim()
            if (s.isEmpty()) null else s
        }
    } catch (_: Throwable) {
        null
    }
}
