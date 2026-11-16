# GameLingo 10.42.7 — Kaynak kodu onarımı ve regresyon kontrolü

**Hazırlanma:** 16.11.2026
**Temel:** `Altyazi_GameLingo_10.42.7_anlam_baglam_duzeltme_kaynak.zip` (önceki sürüm kodu 195)
**Aday:** `versionCode = 196`, `versionName = 10.42.7-unicode-regression-rc2`
**Durum:** Kaynak düzeyinde düzeltme; **APK veya cihazda doğrulanmış kararlı sürüm değildir.**

## Gerçek uygulama kodundaki düzeltmeler

1. **Android ICU ve Türkçe harfler:** Kaynaktaki `(?iu)` gibi Android tarafında desteklenmeyen gömülü bayraklar kaldırıldı. Başta geçici `(?i)` kullanıldı, ancak gerçek Kotlin testinde `Ödememiz` gibi büyük harfler kaçırıldığı görülünce ilgili regexler `RegexOption.IGNORE_CASE` ile güncellendi. Bu, Android ile daha uyumlu ve Unicode büyük/küçük harflerini doğru işleyen yaklaşım. Altyazı kalite, bağlam, OCR onarımı, isim ve argo işleme içindeki ilgili eşleşmeler buna dahildir.
2. **İsteğe bağlı yığın üst satırı:** `CaptureService.kt` içinde `Overlay.setSubtitle` çağrısı önceki gösterilen altyazıyı önceden her koşulda `null` gönderiyordu; şimdi yalnızca kullanıcı **yığın modunu etkinleştirmişse** önceki satırı iletiyor. Yeni satırın ve mevcut alt konumun yerleşimi başka türlü değiştirilmedi.
3. **Çalışma kaydı:** “DOĞRU ÖNCE” kayıt başlığı, deneysel E4B modu ve taslak davranışını açıkça gösteriyor; böylece hangi modun çalıştığı takip edilebiliyor.
4. **Sürüm ayrımı:** Yeni kaynak `versionCode 196` ile etiketlendi; önceki APK/kaynak karışıklığının ayrıştırılması amaçlandı.

## Eski testler neden başarısızdı?

İlk kaynakta **61 testten 53'ü geçiyor, 8'i başarısızdı**. Bu sekiz testten bazıları gerçek uygulama eksikliğine, bazıları güncellenmiş kodu yanlış okuyan eski statik testlere işaret ediyordu. `test/test_bicim.py`, `test/test_dusunme.py`, `test/test_google.py`, `test/test_sablon.py`, `test/Tezgah.java`, `test/Defter.java` ve Kotlin statik denetleyici beklentileri güncel kod mimarisini kontrol edecek şekilde düzeltildi; Google motorunun veya hafızanın işleyişi test geçsin diye değiştirilmedi. Gerçek yeni sözdizimi kaynakta destekleniyor, yanlış statik alarm kapatıldı.

## Son test sonuçları

| Kontrol | Sonuç |
|---|---|
| `python run_all_61.py` | **61 PASS / 0 FAIL / 0 TIMEOUT** |
| Gerçek üretim Kotlin sınıfları ile `UnicodeCase10427Test.kt` | **8 PASS / 0 FAIL** |
| `test_unicode.py`, `test_yigin.py`, `test_google.py`, `kotlin_denetle.py` | **PASS** (61'lik turun içinde) |
| Kaynak ZIP bütünlük kontrolü | ZIP oluşturulduktan sonra ayrıca yapıldı |

Kotlin bağımsız testi aşağıdaki kaynak dosyalarını derleyip çalıştırır: `TranslationQualityGate.kt`, `MeaningSafety.kt`, `LiveSubtitleQuality10425.kt`, `test/UnicodeCase10427Test.kt`. Test, Türkçe `Ö/İ` büyük harfleri, bağlamda anlam/olumsuzluk, kaynakta olmayan yer tutucu, küfür kaybı ve eksik detayı kapsar. Bu testler **tüm Android uygulamasını** derlemez.

## Korunan ayarlar ve bilinçli olarak değiştirilmemiş alanlar

- **DeepL → OPUS-MT → Google/Firefox** çeviri zinciri korunmuştur; yeni dış servis veya token gereksinimi eklenmemiştir.
- Mevcut **alttaki Türkçe altyazı** konumlandırması değiştirilmemiştir.
- OCR tarama hızı, model dosyaları, sözlük ve kişisel ayarlar ile uygulamanın genel tasarımı değiştirilmemiştir.
- Önceden mevcut genel anlam/bağlam düzeltmeleri korunmuştur.

## Açık kalan doğrulama

- Bu ortamda Android SDK/Gradle ile **APK derlenmedi**; Pixel 9 Pro / Android 17 üzerinde ekran yakalama, OCR, gecikme, overlay ve ağ motorları **test edilmedi**.
- Kod değişiklikleri gerçek cihazda hiç hata olmayacağını kanıtlamaz. Regresyon testi yalnızca denenen durumlar için kanıttır.
- Kaynak paketinde eski Android debug imzalama anahtarı (`app/debug.keystore`) bulunuyor. Bu anahtar **gizli tutulmalı**, herkese açık GitHub deposuna rastgele eklenmemelidir.
- GitHub `lingoPlay` deposuna doğrudan değişiklik gönderilemedi. Paket, yeni sürüm kaynak olarak derleme iş akışına **kullanıcı tarafından** alınmalıdır; elde edilen APK'nın `versionCode 196` olduğu doğrulanmalıdır.

## Kurulum ilkesi

Bu dosya bir **kaynak ZIP'idir**, APK değildir. Mevcut çalışan APK'yi kaldırmadan yeni derlemeyi ve kaynak sürümünü kontrol edin. Özellikle İngilizce altyazı ile Türkçe kutunun çakışmasını, yeni replik geçişlerini ve kısa repliklerin bağlam değiştirdiğinde nasıl çevrildiğini cihazda deneyin.
