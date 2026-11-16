# GameLingo 201 — 199 temelinde kontrollü geri dönüş

**Tarih:** 16 Kasım 2026  
**Kaynak sürümü:** `10.42.7-stable-quality-rc7`, `versionCode 201`  
**Durum:** Aday kaynak kod; Android APK derlenmedi ve Pixel 9 Pro üzerinde test edilmedi.

## Neden
Gerçek cihaz kayıtlarında 200 sürümünde bazı argo/deyimler 199'a kıyasla kötüleşti. Motor yanıtları zamana ve bağlama göre farklı olabiliyor. 200'de değiştirilen 125 ms ekstra güçlü motor bekleme politikası ve daha katı hızlı yedek reddi her zaman doğal Türkçe sağlamadı. Dolayısıyla 200'ü genel kalite iyileştirmesi olarak kabul etmiyoruz.

## Yapılan değişiklikler

- **Doğal çeviri kaynağı 199**: DeepL/OPUS-MT/Google/Firefox rotası, 420 ms yerel yedek başlatma, 550 ms sınır, cache, bağlam ve geç sonuç koruması byte-for-byte korundu.
- 200'ün 125 ms ek kalite beklemesi ve `LocalCaptionQuality200` eşiği **geri alınmış** durumda.
- 200'deki ses etiketi sözlüğünden **yalnızca genel kapalı altyazı** ifadeleri korundu; konuşma cümlelerinin özel çevirileri eklenmedi.
- `OcrSoundCue200` sadece OCR'nin kırdığı bazı ses işareti parantezlerini onarıyor. Ana motor akışına ek çağrı yapılmıyor.
- `tests/quality_baseline_199_sha256.json` ve `test_quality_baseline_201.py` ile çeviri çekirdeğinin 199'dan fark edilmeden değişmesi **test hatası** sayılıyor.
- `tests/QUALITY_REVIEW_201.md` doğal çeviri için farklı bağlamlardan elle değerlendirilecek kabul örneklerini içeriyor. Test metinleri uygulamanın sözlüğüne eklenmedi.

## Doğrulanan

- **61/61** eski otomatik kontrol geçti (0 FAIL, 0 TIMEOUT).
- **10/10** gerçek `SoundTags` Kotlin testi geçti.
- **9/9** gerçek `OcrSoundCue200` Kotlin testi geçti.
- **8/8** çekirdek dosya kontrolü 199 ile içerik düzeyinde eşleşiyor (CaptureService için yalnız onaylanan ses onarım çağrısı farklı).
- Arşiv ZIP bütünlüğü ayrıca doğrulandı.

## Bilerek değiştirmediğimiz ve açık kalanlar

- Tek tek deyimleri sabit çeviri olarak kodlamadık. Modelin bağlamı yanlış yorumladığı örnekler ancak canlı nitelik testiyle ayırt edilebilir.
- 199'un hızlı yerel yedeği hâlâ bazı cümlelerde hatalı olabilir; bu paket **bütün çevirileri düzeltti** iddiası taşımaz.
- Gerçek uygulamada değişken yanıtlar modele, bağlama, internet koşullarına ve hangi yedeğin önce tamamlandığına bağlıdır. Bu paket, daha önce doğrulanmamış 200 motor-zamanlama değişikliklerini geri alır; tüm değişkenliği gidermez.
- Android APK/Gradle derlemesi ve Pixel 9 Pro uçtan uca test yapılmadı; bu ortamda Gradle/Android SDK kurulumu doğrulanmış değil.
- `app/debug.keystore` eski uygulamanın üzerine güncelleme imzasını korumak için kaynakta mevcut. **Herkese açık GitHub deposuna yüklemeyin.**
