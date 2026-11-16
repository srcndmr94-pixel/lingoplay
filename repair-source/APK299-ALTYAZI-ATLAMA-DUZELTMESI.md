# GameLingo 10.42.5 (APK-299) — altyazı atlama düzeltmesi

**Baz alınan tek proje:** `Altyazi_GameLingo_10.42.5_APK299_ikinci_saha_duzeltme_kaynak.zip` (`versionCode 188`). Bu APK-299 yaklaşımındaki önceki kalite düzeltmeleridir; 10.43.x kodu bu pakete aktarılmadı.

**Yeni sürüm:** `versionName=10.42.5-apk299-ocr-flow-fix`, `versionCode=189`. Kaynak kodu paketidir; APK değil.

## Saha kaydından hareketle kodda bulunan üç atlama nedeni

1. `CaptureService.bandDegismedi()`: yalnızca 24×8 piksellik parlaklık örneklemesinde yeterli değişim görünmezse, OCR'ye tekrar girilmiyordu. Özellikle aynı arka plan üzerindeki küçük harf değişimleri kaçabilir. Yeni 32×12 örnekleme + görüntü aynı görünse bile **en çok 650 ms sonra OCR'yi yineleme**, doğrulama bekleyen adayda **180 ms sonra OCR'yi yineleme** eklendi. `SubtitleReadGuard299` ayrı saf Kotlin sınıfıdır. Bu sınırlar gerçek görüntü karelerinin gelmesi koşuluyla çalışır; Android uygulama ekranını hiç iletmiyorsa bunu onaramaz.
2. `CaptureService.handleText()`: `similar()`/`OcrOnar.ayniGurultulu()` önceki metnin uzun ortak başlangıcı olduğu için bir OCR okumasında **yarım**, daha sonraki okumada **tamamlanmış** cümleyi aynı kabul edebiliyordu. Artık anlamlı biçimde en az sekiz yeni harf/rakamla uzayan, öncesi yarım kalmış satır yeni okuma olarak işlenebiliyor. Tamamlanmış replikler, noktalamadan doğan farklar ve birkaç harflik OCR gürültüsü için tekrar engeli korunuyor.
3. `CaptureService.clean()`/`arayuzYazisi()`: kısa ve noktalamasız konuşmalar genel tuş/arayüz filtresi tarafından atılabiliyor, 2 harfli `NO` ise daha başlangıçta siliniyordu. Bilinen kısa diyaloglar (`NO`, `HELP ME`, `WAIT`, `GET OUT`, vb.) sınırlı beyaz listeyle geçirildi. `Sprint`, `O Move O Zoom`, `Controller Preset 1` vb. HUD ifadeleri hâlâ geçmiyor.

## Kapsam dışı bırakılanlar

- **Değişmedi:** DeepL → OPUS-MT → Google/Firefox motor sırası; OPUS/DeepL modelleri ve API ayarları; çevrilmiş Türkçeyi gösterme kuralları; Android `CaptureService.startForeground()` ve ekran yakalama izinleri; sözlük ve isim düzeltmeleri.
- Ekranda yanlış seçilmiş **altyazı bölgesi** veya uygulamanın **hiç kare vermemesi**, OCR'nin görüntüde olmayan metni okuyamaması gibi sorunları bu yama kesin olarak gideremez.
- Bilinmeyen, noktalamasız iki kelimelik replikler bile bazı durumlarda arayüz kabul edilebilir; beyaz liste bilinçli olarak dardır.

## Gerçekte çalıştırılan testler

- Yeni `test/SubtitleReadGuard299Test.kt` **33/33 JVM kontrolü PASS** (zamanlı OCR tekrar okuması, yarım replik genişlemesi, kısa diyalog ve HUD ayrımı).
- `tests/verify_10425_integration.py` **25/25 kaynak entegrasyonu PASS**.
- Eski tekil testlerde `test_tarama`, `test_bosluk_taramasi`, `test_isim_hud`, `test_arayuz`, `test_filtre`, `test_ayna`, `test_konusan`, `test_kurtarma`, `test_ekran`, `test_dongu`, `test_hata_kaydi`, `test_taslak_akisi`, `test_yigin`, `test_bozuk`, `test_metin` başarıyla tamamlandı.
- Eski **tam** `test/run_all.py` denemesi `test_pc_ocr.py` çalışırken toplam süre sınırına takıldı; **tam test paketi geçti denemez**. Statik `kotlin_denetle.py` önceden var olan bazı olası uyarılar raporladı; `kotlin_cagri.py` çağrı taraması temizdi.
- **Android/Gradle APK derlemesi ve gerçek Pixel cihaz testi yapılmadı:** bu çalışma ortamında Android SDK/Gradle bulunmuyor.

## Doğrulama sırasında önerilen kayıt

Yeni APK derlemesi yapıldığında build kaydında `versionCode 189` ve `10.42.5-apk299-ocr-flow-fix` bulunduğundan emin olunmalı; GitHub Actions'ın önceki ZIP'i otomatik seçme riski önceki proje yapılandırmasında devam ediyor. Telefon testinde özellikle uzun bir repliğin yarısı görünüp ardından tamamlanması, kısa noktalamasız replik ve sabit arka plan üzerinde değişen altyazı sınanmalı.
