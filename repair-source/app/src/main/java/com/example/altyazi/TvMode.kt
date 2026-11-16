package com.example.altyazi

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * TV modu: telefon kabloyla (USB-C → HDMI) TV'ye bağlıyken altyazı büyük ekrana göre ayarlanır.
 * Kablo bağlıyken TV telefonun ekranını birebir gösterir; TV modu ayrı bir ekran değil,
 * altyazının (yazı boyutu, kutu genişliği, kontrast) 2-3 metreden okunacak hâlidir.
 */
object TvMode {

    /** Uygulama Android TV'nin kendisinde mi çalışıyor (GameLingo TV)? */
    fun isTvDevice(context: Context): Boolean = try {
        val pm = context.packageManager
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        ui.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
            pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    } catch (_: Throwable) {
        false
    }

    /** Harici bir ekran (TV/monitör) bağlı mı? Kendi yakalama ekranımız ("altyazi") sayılmaz. */
    fun externalDisplayConnected(context: Context): Boolean = try {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).any {
            it.displayId != Display.DEFAULT_DISPLAY && it.name != "altyazi"
        }
    } catch (_: Throwable) {
        false
    }

    /**
     * Otomatik geçiş: TV bağlanınca TV modunu açar, kablo çıkınca (otomatik açılmışsa) kapatır.
     * Kullanıcı TV bağlıyken elle "Telefon"u seçtiyse, kablo çıkana kadar zorlamaz.
     * Bir şey değiştiyse true döner.
     */
    fun autoUpdate(context: Context, prefs: Prefs): Boolean {
        // TV'nin kendisinde: her zaman TV görünümü.
        if (isTvDevice(context)) {
            if (prefs.tvMode) return false
            prefs.tvMode = true
            return true
        }
        val now = State(prefs.tvMode, prefs.tvAuto, prefs.tvAutoOn, prefs.tvAutoSuppressed)
        val next = decide(externalDisplayConnected(context), now)
        if (next == now) return false
        prefs.tvMode = next.tvMode
        prefs.tvAutoOn = next.autoOn
        prefs.tvAutoSuppressed = next.suppressed
        return next.tvMode != now.tvMode
    }

    data class State(val tvMode: Boolean, val auto: Boolean, val autoOn: Boolean, val suppressed: Boolean)

    /** Saf karar fonksiyonu (test edilebilir): bağlantı durumuna göre yeni TV durumu. */
    fun decide(connected: Boolean, s: State): State {
        if (!connected) {
            return if (s.tvMode && s.autoOn) s.copy(tvMode = false, autoOn = false, suppressed = false)
            else s.copy(suppressed = false)
        }
        if (s.auto && !s.tvMode && !s.suppressed) return s.copy(tvMode = true, autoOn = true)
        return s
    }

    /** Kullanıcı elle seçti. */
    fun setManual(context: Context, prefs: Prefs, tv: Boolean) {
        prefs.tvMode = tv
        prefs.tvAutoOn = false
        prefs.tvAutoSuppressed = !tv && externalDisplayConnected(context)
    }

    const val TV_GUIDE_TITLE = "GameLingo TV kullanımı"

    /** TV'nin kendisine kurulan GameLingo TV için kılavuz. */
    fun tvGuide(packageName: String): String = """
NE YAPAR
TV'de oynayan görüntüdeki İngilizce altyazıyı okuyup Türkçesini üstüne yazar. Çeviri TV'nin içinde, internetsiz yapılır. TV'lerin belleği yetmediği için yapay zekâ yoktur; çeviri hızlı (taslak) kalitededir.

NEREDE ÇALIŞIR
• YouTube ve benzeri uygulamalar: çalışır.
• PS5: TV'nin HDMI girişindeki görüntü yakalanamaz. PS5'i TV'de bir Chiaki uygulamasıyla (örn. Pylux / Chiaki) açarsan çalışır.
• Netflix / Disney+: kopya koruması yüzünden siyah görünür, çalışmaz.

İLK KURULUM (bir kez)
1. "Diğer uygulamaların üzerinde göster" izni: TV'de bu ayar ekranı varsa uygulama seni oraya götürür, aç.
2. Ayar ekranı yoksa izni telefondan açarız:
   a. TV: Ayarlar > Cihaz Tercihleri > Hakkında > "Yapı"ya 7 kez bas (geliştirici seçenekleri açılır).
   b. TV: Ayarlar > Cihaz Tercihleri > Geliştirici seçenekleri > "Ağ hata ayıklama" / "USB hata ayıklama" açık.
   c. Telefona bir ADB uygulaması kur (örn. "Remote ADB Shell" ya da "Bugjaeger"), TV'nin IP adresine bağlan (TV: Ayarlar > Ağ).
   d. Şu komutu çalıştır:
      appops set $packageName SYSTEM_ALERT_WINDOW allow
   e. TV'de çıkan "hata ayıklamaya izin ver" sorusuna Evet de.

DOĞRU ÇEVİRİ İÇİN TELEFONU KULLAN (önerilir)
TV'nin kendi çevirisi kabadır. Telefondaki GameLingo'nun yapay zekâsı çeviriyi TV için yapabilir:
1. Telefonda GameLingo > Yapay zekâ bölümünde "TV ve bilgisayara çeviri sağla"ya bas. Ekranda 4 haneli kod çıkar.
2. TV'de ana ekrandaki "Telefonun yapay zekâsını kullan" düğmesine bas, kodu yaz, Eşleş de.
3. Telefon ile TV aynı Wi-Fi'da olmalı. Telefonu şarja takıp bırakabilirsin; ekran kapalıyken de çalışır.
Önce gri taslak çıkar, 2-4 sn sonra telefondaki yapay zekânın doğru çevirisi yerine geçer. Telefon kapanırsa TV kendi çevirisine döner.

KULLANIM
1. GameLingo TV'yi aç, Çeviriyi başlat'a bas (kumandada orta tuş).
2. Ekran kaydı iznini ver.
3. Ana tuşla izleyeceğin uygulamaya geç. Altyazılar Türkçe olarak üstte çıkar.
4. Durdurmak için GameLingo TV'ye dönüp Durdur'a bas.

SORUN ÇIKARSA
• Altyazı çıkmıyor ya da yanlış yazı çevriliyor: Ayarlar > Altyazı bölgesi'nde "Hazır ayar: altyazı en altta" düğmesine bas ya da üst/alt sınırı kumandanın sağ-sol tuşlarıyla ayarla (YouTube için %72 – %95).
• "Ekran kaydı bu cihazda yok" uyarısı: TV üreticisi bu özelliği kapatmış; bu TV'de çalışmaz.
• Yazı küçük ya da büyük: Görünüm'den yazı boyutunu değiştir.
""".trim()

    const val GUIDE_TITLE = "TV ile kullanım"

    val GUIDE: String = """
GEREKENLER
• Üzerinde "DP Alt Mode" ya da "video çıkışı" yazan USB-C → HDMI adaptör. Şarj girişi (PD) olan model önerilir; uzun oyunda pil bitmez.
• HDMI kablo ve TV.
• PS5 için: telefonda Chiaki-Up (PS Remote Play ekran kaydını engellediği için çeviri onunla çalışmaz).
• DualSense kol (Bluetooth ile telefona bağlanır).

BAĞLANTI
1. Adaptörü telefona, HDMI kabloyu TV'ye tak. TV'de o HDMI girişini seç.
2. Telefonda "Harici ekrana yansıtılsın mı?" sorusuna "Yansıt" de.
3. GameLingo'yu aç: üstte "TV" kendiliğinden seçilir (seçilmediyse dokun).
4. Çeviriyi başlat'a bas, ekran kaydına izin ver.
5. Chiaki-Up'ı aç, PS5'e bağlan, telefonu yatay tut.

TV MODU NE YAPAR
• Altyazı yaklaşık 1,5 kat büyür, kutu genişler, arka plan koyulaşır.
• Kutu TV kenarlarındaki kesilme payından uzak durur.
• Kablo çıkınca kendiliğinden Telefon moduna döner.

İYİ GÖRÜNTÜ İÇİN
• PS5 mümkünse kabloyla modeme bağlı olsun, telefon 5 GHz Wi-Fi'ye.
• Pil tasarrufu kapalı olsun; açıkken çeviri yavaşlayabilir.
• Yazı yine küçük gelirse Görünüm'den yazı boyutunu büyüt.
• TV'de kenarlarda ince siyah şerit normaldir (telefon 20:9, TV 16:9).

SORUN ÇIKARSA
• TV'de görüntü yok: adaptör video çıkışını desteklemiyordur (DP Alt Mode yazmalı).
• Oyun ekranı siyah, altyazı okunmuyor: PS Remote Play kullanıyorsundur; Chiaki-Up'a geç.
• Netflix / Disney+ siyah: kopya koruması; bu uygulamalarda çeviri çalışmaz.
• Altyazı hiç çıkmıyor: Ayarlar > Altyazı bölgesi'nde "Hazır ayar: altyazı en altta" düğmesine bas ya da sınırları ayarla.
""".trim()
}
