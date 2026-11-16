# GameLingo APK-299 — OCR ve altyazı kaybı için rapor temelli kaynak düzeltmesi

Tarih: 15.11.2026  
Temel: `10.42.5-apk299-ocr-flow-fix` / `versionCode 189`  
Yeni kaynak: `10.42.5-apk299-ocr-band-guard` / `versionCode 190`

**Bu bir kaynak kodu ZIP'idir, telefonda denenmiş APK değildir.** Android Gradle derlemesi ve Pixel 9 Pro üzerinde MediaProjection/ML Kit uçtan uca testi bu ortamda yapılmadı.

## Uygulanan değişiklikler

1. `CaptureService.runOcr`: `POS_BELOW` konumunda `ekranY(box.top) >= shownBoxTopPx` koşuluyla bütün alttaki gerçek İngilizce satırların atılması kaldırıldı. Kendi metnimiz/HUD/gürültü filtreleri korundu.
2. `CaptureService.processSubtitle` + `Overlay.setSubtitle`: tüm OCR şeridinin gerçek koordinatları overlay'e taşınıyor. Güncel ve önceki kutuların ölçülmüş yüksekliği ile, altyazılar OCR şeridinin dışına yerleşiyor. Altta sığmazsa üst konuma geçiyor; hiç yer yoksa Türkçe kutu gizleniyor. Bu kullanıcı konum tercihini dar alanda değiştirebilir, ama kaynak piksellerini korur. Font boyutu değişince yerleşim yeniden hesaplanıyor.
3. `CaptureService.processSubtitle`: OCR'nin yapılmadığı karelerde `handleText(lastOcrRaw)` çağrısı kaldırıldı. Bir adayın ikinci doğrulaması ancak gerçekten yeni OCR sonucuyla yapılabilir.
4. `SubtitleReadGuard299`: sözcük temelli `meaningfulRevision` eklendi. Yapay nokta ile kesilen metnin genişlemesi, kısa olumsuzluk ekleri ve bir karakterlik OCR düzeltmesi işleniyor. Sadece noktalama farkları yeni replik sayılmıyor; küçük OCR düzeltmeleri iki ayrı gözlem gerektiriyor.
5. Ekran dönerken eski kaynak/overlay koordinatları ve adaylar sıfırlanıyor. Yeni görüntünün metni tekrar OCR ile doğrulanıyor.
6. Tanı kayıtları `ham ML Kit`, `filtrelenmiş OCR`, `seçilen blok`, `son OCR`, eleme gerekçesi sayıları ve `yakalama/ OCR` sürelerini ayırıyor; ayrıca OCR çağrısı hata sayaçları var. Çeviriye gönderilen kaynak zaten `OKUNAN:` ile kaydediliyor.
7. Overlay kapatılırken kendi metin filtresinden İngilizce `shownSource` değil, gerçek gösterilen Türkçe `shownValue` çıkartılıyor.
8. `test_yigin.py` içindeki yalnızca tek satırlık `overlay.setSubtitle(...)` çağrısı kabul eden eski biçimsel kontrol, çok satırlı aynı çağrıyı da kabul edecek biçimde güncellendi. Üst satırın gerçekten bağlanıp bağlanmadığı kontrolü korunuyor.

Çeviri motorları, sıraya koyma, OPUS model yükleme, ön plan servisi ve sözlük mantığı değiştirilmedi. GitHub deposundaki otomatik ZIP seçen derleme iş akışı **bu yerel ZIP ile otomatik olarak güncellenmez**; Actions'ta yeni kaynağın ve sürüm kodunun seçildiği kontrol edilmelidir.

## Bağımsız testler

- `test/SubtitleReadGuard299Test.kt`: **33/33 PASS**.
- `test/OcrFlowReport299Test.kt`: **22/22 PASS** (önbellek ve taze OCR ayrımı, yarım/uzayan cümle, olumsuzluk, OCR düzeltmesi, yerleşim ve kaynağın şerit koruması).
- `tests/verify_10425_integration.py`: **31/31 PASS**.
- `test/run_all.py` listesindeki **61 eski testin tümü** tek tek, ilk hatada durmadan çalıştırıldı: **53 PASS, 8 FAIL, 0 TIMEOUT**.
- Kalan sekiz test: `test_denetleyici.py`, `test_tezgah.py`, `test_unicode.py`, `test_yalniz_dogru.py`, `test_dusunme.py`, `test_bicim.py`, `test_google.py`, `kotlin_denetle.py`. Önceki tam test raporunda aynı sekiz başarısızlık belgelenmişti; otomatik olarak önemsiz sayılmamalı.
- Güncel loglar: `OCR299-ESKI-TAM-TEST-LOG.txt`, `OCR299-ESKI-TAM-TEST-SONUC.json` ve `OCR299-ESKI-TAM-TEST-KONSOL.txt` (ayrıca konuşmada ayrı dosya olarak sağlanır).

**Doğrulanmayan:** Android APK derlemesi, APK imzası, gerçek Pixel 9 Pro'da İngilizce 1→2 satıra geçişinin görüntüden OCR tarafından okunması ve ısı/hız etkisi. Bu kaynak kodunu “tamamen düzeldi” diye dağıtmayın.
