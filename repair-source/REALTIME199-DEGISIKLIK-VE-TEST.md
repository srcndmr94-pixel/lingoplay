# GameLingo 199 — Hızlı konuşmada boş altyazı ve gecikme azaltımı

**Temel:** `GameLingo_10.42.7_Genel_Dogal_Ceviri_198_Kaynak.zip`  
**Yeni sürüm:** `versionCode=199`, `versionName=10.42.7-realtime-stable-rc5`  
**Durum:** Kaynak adayı. Android APK derlemesi ve Pixel 9 Pro canlı testi henüz yapılmadı.

## Dayanak: 16 Kasım 2026 kullanıcı kaydı

- OCR örnek süreleri: yaklaşık **375–584 ms**.
- Kısa aralıklarla `ESKİ İSTEK ATLANDI` oluştu. `One big freak family.` ile `They talk about us like we're not there.` gibi satırlar çevrilecek fırsatı bulamadan yeni replik geldi.
- `And how perfect we aren't.` çevirisi yaklaşık **2012 ms** sürdü.
- Kayıtta çalışan uygulama hâlâ `10.42.7-unicode-regression-rc2` idi; bu kaynak 198/199 değildi.

## Yapılanlar

1. `CaptureService`: DENGELİ modda güçlü çeviri yavaşlarsa yerel ML Kit (hazır değilse kurulu Firefox) **420 ms sonra paralel** devreye giriyor. Yerel yedeğe **550 ms** sınır tanındı. Güçlü motora ikinci bir ağ isteği yapılmıyor; `DeepL → OPUS-MT → Google/Firefox` sırası değişmedi.
2. `fastLocalCaption199`: Güvenilirlik için kaynağın özel adları korunuyor; Qx ad belirteçleri yitirilirse, İngilizceyle aynı ya da kalite kapısının reddettiği yanıtlar ekrana çıkmıyor.
3. `StableCaptionGate199`: Aynı kaynak repliğin ilk geçerli Türkçe sonucu ekranda sabit kalıyor. Daha geç gelen sonuç ekranı değiştirmiyor; hafızaya yazılabilir. Eski replikler `subtitleGeneration` tarafından reddedilir; yeni repliğin üzerine yazılamaz.
4. Yeni akış HIZLI veya DOĞRU ÖNCE moduna uygulanmaz. Ekranın altındaki yerleşim, OCR kesme şeridi, kullanıcı sözlüğü ve mevcut motor sırası değişmedi.
5. `test/test_dusunme.py` kodun 198'de gelen bağlama duyarlı hafıza koşulunu da gerçek anlamıyla denetleyecek biçimde güncellendi; yeni kuralı yanlışlıkla eski taslak hafızası sızıntısı sanmıyor.

## Gerçekte çalıştırılan testler

- `python run_all_61.py`: **61 PASS / 0 FAIL / 0 TIMEOUT** (`REGRESSION199.log`; eski Python/Java statik ve akış testleri).
- `kotlinc RealtimeCaptionPolicy199.kt test/RealtimeCaption199Test.kt` ardından JVM yürütmesi: **11/11 PASS**. Replik kimliği, geç sonucun reddi, diğer biçimlerin etkilenmemesi, yedeğin zaman sınırları.
- `python tests/test_realtime_199_integration.py`: **10/10 PASS**. Üretim kaynak bağlantıları için statik kontrol.
- ZIP bütünlük taraması ve sürüm kimliği ayrıca doğrulanmıştır.

## Bilinen sınırlamalar ve karar

- **Android SDK/Gradle APK derlemesi yapılmadı. Gerçek cihazda performans kazanımı ölçülmedi.** Kotlin testleri yalnız bağımsız politika sınıfını derliyor; `CaptureService` Android derlemesi doğrulanmış değildir.
- Video karesi + OCR süresi (loglarda yaklaşık 400–600 ms) bu yama ile azaltılmıyor. 1 saniyeden kısa aralıklarla gelen tüm repliklerin kesin yakalanması/çevrilmesi garantilenemez.
- İlk geçerli sonuç bazen güçlü DeepL yerine daha hızlı yerel motordan gelebilir. Bu durum **hız ile çeviri doğallığı arasında takastır**. İlk sonuç sabitlendiğinden sonradan daha iyi çeviri gelse bile o replikte görünmez; hafızaya alınabilir. Hız yerine daha yüksek kalite gerekiyorsa ayrı DOĞRU ÖNCE biçimi değerlendirilmelidir.
- Önceki satırların ekranda yeni satırın yerine basılması özellikle engellenmiştir. `ESKİ İSTEK ATLANDI` bazen doğru ve istenen güvenlik davranışıdır; yalnız bu logun görünmesi hata değildir.
- Kaynakta **`app/debug.keystore` geliştirme imzalama anahtarı** bulunuyor. ZIP'i herkese açık GitHub deposuna koymayın. Anahtar mevcut debug APK üzerine güncelleme için tutulmuştur; güvenli dağıtım kimliği değildir.
