# GameLingo — Altyazı atlama ve OCR düzeltme raporu

**Tarih:** 15 Kasım 2026  
**İncelenen paket:** Altyazi_GameLingo_10.42.5_APK299_altyazi_atlama_duzeltme_kaynak.zip  
**Kaynak sürümü:** 10.42.5-apk299-ocr-flow-fix, versionCode 189  
**Dayanak:** Paket içindeki kaynak kodu, OCR-TAM-TEST-RAPORU.md ve kullanıcının önceki 10.43.6 ekran görüntüsü/logları.

> Önceki ekran görüntüsü başka bir sürüme aittir. Bu görüntü, bu paketin cihazda aynı hatayı yaptığına tek başına kanıt değildir. Aşağıdaki kod davranışları incelenen pakette doğrulanmıştır; cihazdaki tüm atlamaların tek nedeni kesinleştirilmemiştir. Bu rapor bir düzeltme planıdır; değişiklikler uygulanmış değildir.

## 1. Sonuç ve öncelik

Öncelik çeviri motorunu değiştirmek değil, İngilizce altyazının tamamının OCR'dan çeviri motoruna ulaşmasını sağlamaktır. Kaynakta hem çeviri kutusunun altyazıyla çakışmasına izin veren konumlandırma hem de konuma göre gerçek OCR satırlarını eleyebilecek bir filtre bulunuyor. Mevcut testler gerçek Android ekran yakalama ve ML Kit OCR zincirini doğrulamıyor.

## 2. Öncelik 1 — Konuma göre İngilizce satırların elenmesi

**Yer:** `app/src/main/java/com/example/altyazi/CaptureService.kt`, `runOcr()`.

**Doğrulanan davranış:** “Altında” konumunda, OCR satırının üst kenarı `shownBoxTopPx - dp(2)` sınırına ulaşıyorsa satır atılıyor. Kontrol kutunun alt kenarını ve yatay sınırlarını kullanmıyor. Böylece yalnızca Türkçe kutunun içi değil, aşağısındaki bütün satırlar elenebiliyor. Tek satırdan iki satıra geçen İngilizce altyazının yeni alt satırı bu sınırın altında kalabilir.

**Yapılacak düzeltme:** Bu sınırsız dikey eleme kaldırılmalı. Kendi çevirisini dışlama, gerçek kutu sınırları ve gösterilen metinle ilişkilendirilmiş bir kontrol olmalı. Kaynak altyazının görünürlüğü ayrıca korunmalı; sonradan metin filtresi uygulamak, kutunun örttüğü pikselleri geri getirmez.

**Kabul kontrolü:** Türkçe kutu görünürken bir satırlı İngilizce altyazı iki satıra dönüşmeli. İkinci satır OCR çıktısında ve motora gönderilen kaynakta eksiksiz bulunmalı. Kutunun daha aşağısındaki farklı İngilizce satırlar yalnızca konum nedeniyle silinmemeli.

## 3. Öncelik 1 — Türkçe kutunun kaynak altyazıyı kapatması

**Yer:** `CaptureService.kt`, `processSubtitle()` ve `showSubtitle()`; `Overlay.kt`, `setSubtitle()`.

**Doğrulanan davranış:** Kutunun alt kenarını OCR şeridinin üzerinde tutan `subtitleBottomPx` sınırı yalnızca `POS_ABOVE` seçeneğinde uygulanıyor. Örtme, alt ve altında konumları için aynı koruma yok. Önceki kullanıcı görüntüsünde Türkçe ve İngilizce yazıların çakışması açıkça görülüyor.

**Yapılacak düzeltme:** Canlı OCR sırasında Türkçe kutu ölçülmüş yüksekliğiyle birlikte kaynak okuma alanının dışında tutulmalı. Yazı büyüklüğü, uzun çeviri ve önceki çeviri satırı da yerleşim hesabına dahil edilmeli. Alan yetmediğinde güvenli konuma geçilmeli. Konum veya ekran yönü değişince eski koordinatlar geçersizleştirilip yeniden hesaplanmalı.

**Kabul kontrolü:** Yatay/dikey dönüşte, büyük yazıda ve çok satırlı çeviride Türkçe kutu kaynak altyazıyı örtmemeli. Yeni kaynak altyazılar Türkçe kutu görünürken okunabilmeli. Kutuyu gizleyerek ve göstererek yapılan karşılaştırmada satır kaybı oluşmamalı.

## 4. Öncelik 2 — Tamamlanan OCR metninin tekrar sanılması

**Yer:** `SubtitleReadGuard299.kt`, `meaningfulExtension()`; `CaptureService.kt`, `handleText()`.

**Doğrulanan davranış:** Uzama istisnası; önceki metin en az 12 harf/rakam içeriyorsa, yeni metin eskiyle birebir aynı normalize edilmiş başlangıca sahipse ve en az 8 harf/rakam eklenmişse çalışıyor. Önceki metnin tek nokta, soru veya ünlemle bitmesi de istisnayı kapatıyor. Bu şartlar sağlanmazsa mevcut benzerlik filtresi hâlâ devrede.

**Yapılacak düzeltme:** Birkaç karakterlik OCR düzeltmesini ve kısa ama anlam değiştiren ekleri hesaba katan, sözcük/satır temelli bir karşılaştırma kullanılmalı. Noktalama tek başına tamamlanmışlık kanıtı sayılmamalı. Gerçek tekrarlar ve yeni konuşmacılar yine ayrılmalı.

**Kabul kontrolü:** Yarım→tam cümle, ilk sözcüğünde OCR düzeltmesi, yapay nokta, kısa olumsuzluk eki ve ikinci satır eklenmesi ayrı senaryolarla sınanmalı. Sadece boşluk/noktalama değişimi gereksiz yeni çeviri oluşturmamalı.

## 5. Öncelik 2 — Eski OCR sonucunun yeni okuma gibi kullanılması

**Yer:** `CaptureService.kt`, `processSubtitle()`.

**Doğrulanan davranış:** Görüntü örnekleri aynı sayılıp OCR atlandığında, `candidateSeen == 1` ise `handleText(lastOcrRaw)` çağrılıyor. Bu, yeni bir OCR ölçümü olmadan aday doğrulamasını ilerletebilir. Periyodik yeniden okuma faydalıdır; fakat bunun gerçek kare işleme süresine eklenen bir eşik olduğu unutulmamalıdır.

**Yapılacak düzeltme:** Yeni OCR gözlemi ile önbellekten tekrar işlenen metin ayrılmalı. Aday güveni yalnızca yeni ölçümle artırılmalı veya zaman temelli kabul açıkça ayrı bir politika olarak tanımlanmalı. Düşük kare hızında ve sabit arka planda davranış ölçülmeli.

**Kabul kontrolü:** Önbellekteki metnin tekrar işlenmesi iki bağımsız OCR okuması gibi sayılmamalı. Sabit arka planda değişen kısa altyazılar yakalanmalı.

## 6. Öncelik 2 — Atlamanın hangi aşamada olduğunun görünmemesi

**Doğrulanan davranış:** `lastOcrRaw`, ML Kit'in ham çıktısını değil, `runOcr()` içindeki filtrelerden ve blok seçiminden geçmiş metni tutuyor. Dolayısıyla “son OCR boş” kaydı tek başına “ML Kit hiç yazı görmedi” anlamına gelmez.

**Yapılacak düzeltme:** Tanılama modunda ham OCR satırları, filtrelenmiş satırlar, eleme nedenleri ve motora gönderilen kaynak ayrı kaydedilmeli. OCR başarısızlığı, boş görüntü, kendi yazısı ve tekrar engellemesi ayrı sayaçlarda tutulmalı.

**Kabul kontrolü:** Kaçan her replik için son görüldüğü aşama belirlenebilmeli. Gecikme, yalnızca çeviri süresi değil yakalama→OCR→çeviri→gösterim aşamalarıyla ölçülmeli.

## 7. Derleme ve test gereklilikleri

- Gönderilen rapor 61 kontrolün 53'ünün geçtiğini, 8'inin başarısız olduğunu bildiriyor. Eski sürümde de başarısız olmaları bu kontrolleri otomatik olarak önemsiz yapmaz; her biri gerçek kusur veya eskimiş test beklentisi olarak ayrıştırılmalı.
- Bu incelemede `tests/verify_10425_integration.py` yeniden çalıştırıldı: **25/25 geçti**. Bu sonuç kaynak entegrasyonunu kontrol eder; gerçek ekran görüntüsünün doğru okunmasını kanıtlamaz.
- `test_pc_ocr.py`, masaüstü OCR metin onarımı/tekrar süzgeci testidir; Android ML Kit ve MediaProjection için uçtan uca kanıt değildir.
- GitHub iş akışı kaynak ZIP'i commit geçmişinden otomatik seçiyor. Derlenecek kaynak açıkça sabitlenmeli; APK'nın versionCode değeri ve kaynak özeti kayıt altına alınmalı. “APK-299” iş numarası tek başına kaynak sürümünü tanımlamaz.
- Nihai doğrulama: Android APK derlemesi, imza kontrolü ve aynı altyazı sahnesinde gerçek Pixel 9 Pro ekran yakalama/OCR/overlay kontrolü. Birim testleri bunların yerine sunulmamalı.

## 8. Teslim ölçütü

Tek, kaynağı belli APK ve değişiklik listesi teslim edilmeli. Hangi kontrollerin geçtiği ve cihazda hangilerinin gerçekten çalıştırıldığı ayrı belirtilmeli. İngilizce kaynak eksik okunurken çeviri motorunun hızlı cevap vermesi başarı sayılmamalı. Yeni model karşılaştırması bu düzeltmelerin ön koşulu değildir.
