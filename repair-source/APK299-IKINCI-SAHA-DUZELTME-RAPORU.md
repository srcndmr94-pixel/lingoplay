# GameLingo — APK-299 ikinci saha düzeltmesi

**Temel:** `1-Telefon-APK-299.zip` (10.42.5-deepl-quality APK) ile sürümü eşleşen 10.42.5 kaynak kodundan yapılmış `10.42.5-apk299-log-fix` kaynak paketi. **Bu sürümler derlenmiş bayt bakımından eşit olduğu doğrulanmış sürümler değildir.** Önceki 10.43.x mimarileri aktarılmadı.

**Yeni kaynak:** `versionName=10.42.5-apk299-log-fix2`, `versionCode=188`, `applicationId=com.example.altyazi`.

## Uygulananlar

1. OCR'de yalnız logda açık kanıt bulunan bağlamlara müdahale: `Transcended heaters`→`Transcended healers`, `Xie iudong 1gave ... evershouldve hadl` zinciri, `Enpthe liew Meditine?`→`Equip the new Medicine?`, `pro longed sleep`→`prolonged sleep`. Kaynağı belirsiz, fazlasıyla bozuk `Traverse Ungrade Equipnjenl`, `nevertr UIy runs dry`, `sys Tems.` okunmaları tercüme edilmek yerine temiz kare bekler.
2. Sadece kaynak İngilizcede terim bulunduğunda, çeviride parçalanan `Hollow Heart`, `Xie Huichun`, `Meigu Lake`, `White Serpent House`, `Phantom Edge` ifadeleri korunur; önceki Bronze Essence ve Sha-Chi düzeltmeleri aynen kalır.
3. Motorların farklı yayın yollarından çıkan metinler, `CaptureService.publish()` ortak gösterim noktasından geçerken aynı kaynak-bağımlı düzeltmeye uğrar; çeviri hafızasına da düzeltilmiş metin yazılır. Özellikle DeepL'nin doğrudan yanıtına da uygulanır.
4. Doğrulanmış menü metinleri: `Upgrade Medicine Leave` → `İlacı geliştir · Ayrıl`; `Controller Preset 1` → `Kontrol düzeni 1`; `Equip the new Medicine?` → `Yeni ilacı kuşanmak istiyor musun?`. Hızlı yolda birebir kaynak eşleşmesi gerektirir.
5. Kaynakta birebir görülen ve anlamı ters çevrilebilen birkaç replik için nokta atışı Türkçe karşılık: `Sacrifice a few to save the many`, Soul'un zehirli kalp repliği, `Xie Qiudong I gave...`, `Xie Huichun devoted...`, `Transcended healers...`. Bunlar genel çeviri motoru başarısını yükselttiği iddiası değil, kaynak-özel güvenli sabitlemelerdir.
6. **Değişmedi:** DeepL → OPUS-MT → Google/Firefox motor önceliği, OPUS model dosyaları, OCR görüntü yakalama, foreground service, API yapılandırması, ses etiketi ana mekanizması ve uygulama paketi kimliği.

## Kontroller

- Kotlin/JVM: **60/60** `GameplayLog299Test.kt` kontrolü (uygulama davranışının sınırlı parçaları).
- Python kaynak entegrasyonu: **20/20** `verify_10425_integration.py` kontrolü.
- Önceki isim testleri: `test_1039.py` **11+3 PASS**, ses etiketi testleri **7 PASS** (ayrı çalıştırma çıktılarından).
- Tam Android/Gradle APK derlemesi: **yapılmadı**; burada Android SDK bulunmuyor.
- Pixel 9 Pro canlı altyazı: **yapılmadı**.
- Kaynak ZIP sağlamlığı: arşiv düzeyinde test edildi.

## Sınırlar

Bu dar kapsamlı yama, rastgele yeni oyun adlarını otomatik olarak kusursuz ayırmayı veya tüm OCR kırılmalarını kurtarmayı garanti etmez. Kaynakta olmayan eksik sözcükler uydurulmaz. Modelin çevirdiği diğer bozuk cümleler hâlâ bulunabilir. Uygulamadaki debug anahtarı ve GitHub Actions'ın geçmiş ZIP seçimi sorunu bu sürümde değiştirilmemiştir. APK oluştururken derlenen ZIP'in bu sürüm olduğundan emin olun.
