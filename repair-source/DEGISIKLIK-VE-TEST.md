# GameLingo - merkezi kalite güvenliği (deneysel)

- `CaptureService.publish`: bütün çıktı yollarında ortak kaynak/çeviri kalite kontrolü ve tanı kaydı.
- `CaptureService.saveToMemory`: açıkça şüpheli sonuçların kalıcı hafızaya yazılması engellendi.
- `CaptureService` hafıza okuma: eski kayıtların kalite kontrolünden geçirilmesi; şüpheli kaydın silinip yeniden çevrilmesi.
- `TranslationQualityGate`: açık yanlış soru ve çevrilmemiş kaynak kontrolü; tırnaklı ve konuşmacı ayrımlı kaynaklarda ihtiyatlı davranma.
- `tests/TranslationQualityGateTest.kt`: 11 kontrol, kotlinc/JVM ile geçti.

## Bilinen sınırlar
- Bu değişiklikler bir doğal dil modeli eklemez; deyim/bağlam/küfür doğruluğunu garanti etmez.
- Şüpheli canlı çeviri boş ekran oluşmasın diye gösterilir, ancak hafızaya kaydedilmez.
- Android SDK/Gradle APK derlemesi ve gerçek cihaz testi yapılmadı.
- İsimli altyazılar ve karmaşık soru biçimleri için ek uçtan uca test gereklidir.
