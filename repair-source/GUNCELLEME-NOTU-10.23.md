# GameLingo 10.23 — görsel altyazı bölgesi ayarı

Temel: GameLingo 10.22 (altyazi-10.22-baglam-guclendirme.zip). Eski sürüm değiştirilmedi.

## Eklendi
- `RegionActivity`: ekrandaki örneğe benzeyen ayrı ayar ekranı; Üst / Alt / Sol / Sağ seçimi, % kaydırıcısı, Varsayılana Dön, Kaydet, Vazgeç.
- `RegionView`: mavi kenarlıklı OCR alanı; çerçeve içeriden sürüklenebilir, kenar ve köşelerden yeniden boyutlandırılabilir. Slider ile sürükleme aynı değeri günceller.
- `RegionBounds`: minimum %5 genişlik/yükseklik; yüzdelik değerler 0–100 aralığında sınırlandırılır.
- `CaptureService`: çalışırken son oyun karesinin çerçevesiz, küçük bir kopyasını yalnızca RAM'de tutar (en fazla 2 saniyede bir). Ayar ekranı açıldığında bu kare önizlemede görünür. Oyun kapalıysa veya yakalama yoksa siyah açıklama alanı görünür.
- ImageReader padding kırpıldı; önizleme ile OCR kesitinin aynı yatay koordinatlara dayanması sağlandı.
- Yüzde sınırları mevcut `Prefs.topPct/bottomPct/leftPct/rightPct` anahtarlarına kaydedilir. `CaptureService.processSubtitle` bu değerleri zaten kullanıyor; çeviri motoru değişmedi.
- Sürüm: 10.23, versionCode 156.

## Testler
- `RegionBounds` Kotlin/JVM: 12 sınır testi geçti.
- 36 Android XML dosyasının sözdizimi geçerli.
- `test_region_screen_1023.py`, bağlam, ekran yakalama, Google ve arayüz regresyonları: 9 seçilmiş Python test dosyası geçti.
- Tam test takımının tamamlandığı söylenemez; kapsamlı koşum süre sınırını aştı.
- Android tam derlemesi, gerçek telefon ve ekran döndürme testi yapılmadı; paket APK değildir.

## Bilinen sınırlar
- Görsel önizleme **canlı video değildir**; yakalama sırasında oyundayken alınmış son kareyi gösterir.
- Oyun DRM ile ekran görüntüsünü engelliyorsa önizleme siyah olabilir.
- Uygulama ana ekranı açıkken oyun yerine uygulamanın kendisi kaydedilmesin diye kare güncellenmez.
- Kaydet'e dokunulmadan yapılan ayarlar kalıcı olmaz.
- Çeviri kalitesi sorunlarını değiştiren bir kod bu güncellemeye eklenmedi.
