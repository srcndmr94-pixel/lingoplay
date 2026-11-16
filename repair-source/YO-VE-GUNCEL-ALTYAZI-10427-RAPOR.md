# GameLingo 10.42.7 — Bağlamsal Yo / güncel altyazı düzeltmesi

Temel: `Altyazi_GameLingo_10.42.7_log_stabilite_duzeltmesi_kaynak.zip` (versionCode 193)
Yeni kaynak: `10.42.7-yo-context-caption-fix`, versionCode **194**.

## Ne değişti?

1. `AiGuard.kt` içinde `Yo = Lan` zorlaması kaldırıldı; `YoContext10427.kt` ile bağlama göre yalnız uygunsuz eklenen sert hitap temizleniyor. Kaynakta küfür varsa mevcut sertlik korunuyor, sansür yapılmıyor. `Yo, what's up?` için `Lan, naber?` -> `Naber?`; `Yo, what the fuck?` için `Bu ne lan?` korunuyor. `Yo!` tek başına `Hey!` olabilir.
2. `AiTranslate.kt` içindeki zorunlu `Yo=Lan` sistem talimatı bağlamsal hitap olarak değiştirildi. `OfflineFix.kt` içindeki `yo`, `yo phone`, `yo ray` ifadelerindeki sabit `Lan` kaldırıldı. Eski hafızadan gelen kaynak bağımlı çeviriler dahil tüm `publish()` sonuçları `YoContext10427.correct()` üzerinden geçiyor.
3. `CaptureService.kt`: **yeni replik çeviriye gönderilmeden önce**, ekranda önceki replikten kalan Türkçe kutu temizleniyor; aynı repliğin OCR harf dalgalanmasında gereksiz temizleme yok (`CaptionSwitch10427.kt`). Eski cevapların yeni satırın üstüne yazılmasını engelleyen mevcut `subtitleGeneration` denetimi korunuyor. Yeni çeviri hazır olana kadar Türkçe kutu kısa süre boş kalabilir; önceki replik yanlışlıkla yeni İngilizceye eşlik etmeyecek.
4. Türkçe altyazının **alttaki konumu** ve DeepL → OPUS-MT → Google/Firefox motor sırası aynen bırakıldı. OCR hız/frekansı ve sözlüğün geri kalanı değişmedi.
5. Eski testlerdeki sabit `Yo=Lan` beklentileri güncellendi. Test kopyası yerine gerçek Kotlin dosyalarını çalıştıran `test/test_yo_caption_10427.py` ve `test/YoAndCaption10427Test.kt` eklendi.

## Bu ortamda doğrulanan

- 14 bağlamsal `Yo` + idempotence durumu, **5/5** caption geçişi: PASS (gerçek Kotlin sınıfları derlendi ve çalıştırıldı).
- **8** `AiGuard.hitapDuzelt` örneği gerçek üretim kodu ile derlenip çalıştırıldı: PASS (yalnız bağımlı nesneler stub).
- `test/test_yo_caption_10427.py`: üretim bağlantısı, eski kutunun yeni çeviri başlamadan temizlenmesi, alt pozisyonun korunması: PASS.
- Eski **78** repliklik tezgâhın kaynak karşılığı (sözlük katmanı): PASS (74 tam eşleşme).
- Çıktı kusur taraması: PASS (174 çözülen satır / önceden kabul edilmiş 1 bulgu).
- `test_katman.py`, `test_talimat.py`, `test_yz_denetim.py`, `test_argo.py`, `test_kalip.py`, `test_sozluk.py`, `test_cagri_denetimi.py`: PASS.
- Bağımsız eski testlerden `test_tezgah.py` içinde `Defter.java` konuşmacı sıra denetimi başarısız; ayrıca `test_bicim.py`, `test_denetleyici.py` ve `test_google.py` başarısız. Bunların tamamını çözülmüş saymıyoruz; kapsamı bu değişikliklerin dışında. **Tüm eski testler başarılı iddiası yok.**
- Tam Android Gradle derlemesi ve gerçek Pixel 9 Pro canlı görüntü testi **yapılmadı**. Cihazda başarının doğrulandığı iddia edilmez.

## Cihaz kabul senaryoları

1. İngilizce `Really, huh?` -> yeni İngilizce `Where you at, Vice City girls?` geçişinde eski Türkçe yazı kalmamalı.
2. `Yo, what's up?` gibi normal konuşmalara zorla `Lan` eklenmemeli; `Yo, what the fuck?` sertliği korunmalı.
3. `Yo! We going up.` gibi dikkat çağrısı gerektiğinde Türkçe hitap doğal olmalı.
4. Ekranın altındaki Türkçe yazının konumu değişmemeli.
5. Aynı İngilizce satırın OCR gürültüsü sonucu yeni satır sanılıp kutu sürekli yanıp sönmemeli.

**Bu paket kaynak kodudur, APK değildir.** Önceki çalışan APK'yi kaldırmadan yeni sürüm cihazda test edilmelidir.
