package com.example.altyazi

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dev.davidv.translator.ITranslationCallback
import dev.davidv.translator.ITranslationService
import dev.davidv.translator.TranslationError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 10.2 — FIREFOX ÇEVİRİ MOTORU (anlık taslak için).
 *
 * Kullanıcı: "PC'de Chrome'dan düzgün çıkıyor, telefonda neden olmuyor?"
 * Chrome'un yerleşik çeviri modeli yalnız masaüstünde çalışıyor. Firefox'un
 * çeviri modelleri ise açık kaynak ve Android'de çalışıyor. Bu modelleri
 * telefonda çalıştıran ücretsiz, açık kaynak "Offline Translator" uygulaması
 * (dev.davidv.translator, F-Droid) başka uygulamalara bir arayüz sunuyor:
 * ITranslationService. GameLingo o arayüze bağlanır; çeviri internetsiz ve
 * telefonda yapılır. Arayüz dosyaları (app/src/main/aidl) MIT lisanslıdır.
 *
 * Uygulama kurulu değilse ya da cevap vermezse hiçbir şey bozulmaz: taslak
 * eskisi gibi telefonun yerleşik çeviri motorundan gelir.
 */
class FirefoxCeviri(private val baglam: Context) {

    companion object {
        const val PAKET = "dev.davidv.translator"
        const val EYLEM = "dev.davidv.translator.ITranslationService"
        const val INDIRME = "https://f-droid.org/packages/dev.davidv.translator/"
        /** Tek parça için bekleme. Aşılırsa taslak yerleşik motordan gelir. */
        const val ZAMAN_ASIMI_MS = 1500L

        @Volatile var basarili = 0
        @Volatile var basarisiz = 0
        @Volatile var sonHata: String? = null

        fun kurulu(context: Context): Boolean = try {
            context.packageManager.getPackageInfo(PAKET, 0)
            true
        } catch (_: Throwable) {
            false
        }

        fun durum(): String =
            "Firefox motoru: $basarili çeviri, $basarisiz başarısız" +
                (sonHata?.let { " · son hata: $it" } ?: "")
    }

    @Volatile private var servis: ITranslationService? = null
    @Volatile private var baglaniyor = false

    private val baglanti = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            servis = ITranslationService.Stub.asInterface(binder)
            baglaniyor = false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            servis = null
        }

        override fun onBindingDied(name: ComponentName?) {
            servis = null
            baglaniyor = false
        }

        override fun onNullBinding(name: ComponentName?) {
            servis = null
            baglaniyor = false
            sonHata = "servis bağlantıyı kabul etmedi"
        }
    }

    val hazir: Boolean get() = servis != null

    /** Servise bağlanmayı başlatır. Kurulu değilse false. */
    fun baglan(): Boolean {
        if (servis != null || baglaniyor) return true
        if (!kurulu(baglam)) {
            sonHata = "Offline Translator kurulu değil"
            return false
        }
        val niyet = Intent(EYLEM).setPackage(PAKET)
        baglaniyor = try {
            baglam.bindService(niyet, baglanti, Context.BIND_AUTO_CREATE)
        } catch (e: Throwable) {
            sonHata = e.message ?: e.javaClass.simpleName
            false
        }
        if (!baglaniyor && sonHata == null) sonHata = "bağlanma reddedildi"
        return baglaniyor
    }

    fun birak() {
        try {
            if (servis != null || baglaniyor) baglam.unbindService(baglanti)
        } catch (_: Throwable) {
        }
        servis = null
        baglaniyor = false
    }

    /** İngilizce -> Türkçe. Cevap gelmezse ya da hata olursa null. */
    suspend fun cevir(metin: String): String? {
        val s = servis
        if (s == null) {
            baglan()
            return null
        }
        val sonuc = CompletableDeferred<String?>()
        val geriDonus = object : ITranslationCallback.Stub() {
            override fun onTranslationResult(translatedText: String?) {
                sonuc.complete(translatedText)
            }

            override fun onTranslationError(error: TranslationError?) {
                sonHata = listOfNotBlank("hata ${error?.type}", error?.language, error?.message)
                sonuc.complete(null)
            }
        }
        try {
            s.translate(metin, "en", "tr", geriDonus)
        } catch (e: Throwable) {
            sonHata = e.message ?: e.javaClass.simpleName
            servis = null
            basarisiz++
            return null
        }
        val r = withTimeoutOrNull(ZAMAN_ASIMI_MS) { sonuc.await() }
        if (r.isNullOrBlank()) {
            basarisiz++
            if (r == null && !sonuc.isCompleted) sonHata = "zaman aşımı (${ZAMAN_ASIMI_MS} ms)"
            return null
        }
        basarili++
        return r
    }

    private fun listOfNotBlank(vararg p: String?): String =
        p.filter { !it.isNullOrBlank() }.joinToString(" · ")
}
