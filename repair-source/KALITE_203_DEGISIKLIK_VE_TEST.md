# GameLingo 203 – Kalite kontrol düzeltmesi

Kaynak tabanı: doğrulanmış 202 (`GameLingo202_Derleme_Duzeltildi.zip`).
Sürüm: `10.42.7-quality-rc9`, `versionCode=203`.

## Yapılanlar

1. **LOST_NEGATION:** `sakın` artık olumsuzluk olarak tanınıyor. Unicode casefold ile `sakin` sözcüğünün yanlışlıkla `sakın` sayılması engellendi. `yapma` ve benzeri olumsuz fiiller de değerlendirmeye dahil edildi.
2. **Kişi/özne tersliği:** Kaynakta açıkça `you`, `y'all`, `ya'll`, `yall`, `whatchu` geçiyorsa ve kaynakta `I/we` yoksa, bariz birinci şahıs Türkçe çıktı (`...yorum`, `...yoruz`) reddediliyor. Örnek: `What the fuck y'all doing in here?` -> `Burada neler yapıyorum?`.
3. **Küfür kalite uyarısı:** `LOST_PROFANITY` mevcut DeepL davranışıyla tutarlı biçimde OPUS ve Google için de **uyarı**, tek başına zorunlu ret değil. Türkçe doğal bir çevirinin kötü Firefox yedeğine düşmesi önleniyor. Bu, motorun küfrü doğru çevirdiğini garanti etmez; BENCH202 logları tutulur.
4. **Tek kontrollü yerinde yükseltme:** İlk sonuç açıkça `hızlı yerel yedek` ise; bilinen bir kalite kusuru saptanmışsa; sonraki güçlü motor hatasız çıktı veriyorsa; aynı `runId` ve görünür kaynak geçerliyse; ilk yayından itibaren 1.200 ms içinde **en fazla bir kez** yenilenebilir. Yalnızca üslup farklarında, eski replikte, ekran dönüşüyle geçersizleşmiş istekte veya güçlü sonuç da sorunluysa yükseltme yapılmaz.
5. 199'daki **420 ms hedge**, **550 ms yerel timeout**, motor sırası, OCR bölgesi ve altyazı konumu değiştirilmedi. `BENCH202` kayıt biçimi karşılaştırılabilir olsun diye korundu.

## Test ve sınırlar

- Android bağımsız Kotlin testleri: **27/27** (kalite, kişi, eski runId, tek yükseltme, zaman aşımı, görünürlük).
- Önceki Türkçe kalite testi: **15/15**.
- Kotlin parametre denetimi, çağrı denetimi, XML kaynak denetimi: **geçti**.
- 203 çekirdek bütünlük testi: 8/8 sabit hash; 199 referansından **yalnızca üç açıkça izin verilen çekirdek dosya** değişti: `CaptureService.kt`, `TranslationQualityGate.kt`, `RealtimeCaptionPolicy199.kt`. 199'un orijinal hash listesi değiştirilmedi.
- 61 betiklik tarihsel Python test paketi: **tamamlanmadı**, `test_hiz.py` aşamasında uzun süre kaldı; başarı iddia edilmiyor. Bazı eski sürüme özgü testler mevcut dosya imzalarına uymuyor.
- **Android Gradle APK derlemesi ve Pixel 9 Pro canlı testi yapılmadı.** Android SDK/Gradle bu çalışma ortamında bulunmuyor.
- Ekran dönüşünde eski repliğin geçersiz kılınması mevcut güvenlik davranışıdır. Bu sürümde bilinçli olarak değiştirilmedi; ilgili canlı test hâlâ gerekli.

## Kurulum

202'deki gibi paket kimliği `com.example.altyazi.benchmark`; böylece 201 kararlı sürüm kalır, üçüncü ikon oluşmaz. Yeni CI debug imzası mevcut 202 APK'sından farklıysa **yalnız 202 benchmark uygulamasını** kaldırıp 203'ü kurmak gerekir. Bu durumda 202 uygulamasına ait ayarlar silinir. **201'i kaldırmayın.**

Depo herkese açık. Bu pakette `.keystore` ve `.jks` dosyası yoktur. Mevcut 201 public ZIP'indeki eski imza anahtarı güvenli sayılmamalıdır.
