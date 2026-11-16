# GameLingo 10.42.7 — Genel amaçlı doğal Türkçe çeviri (198)

**Temel:** Kullanıcının 197 kaynak paketi. **Yeni aday:** `versionCode=198`, `versionName=10.42.7-natural-context-rc4`. **Teslim:** Kaynak ZIP; APK değildir.

## Hedef

Bir film/oyunun repliklerine Türkçe cevap ezberletmek yerine, her yeni İngilizce altyazıda uygun motorun bağlamdan hareketle doğal ve doğru bir Türkçe karşılık üretmesi. Bu doğrultuda tekil replikler kalite regresyonu için saklanabilir, ancak çalışırken çeviri motorunu atlayan nihai cevap olarak kullanılmamalıdır.

## Değişiklikler

1. **Sahneye özel replik bypass'ı kaldırıldı:** `CaptureService.runTranslation` artık `SourceAwareDialogueFix.correct(bodyIn)` veya `DialogueContextRepair.contextualTranslation(source)` ile eski oyun sahnesinden hazır sonuç basmıyor. `ClauseRecovery` parçalarının da özel replik yanıtıyla değiştirilmesi kaldırıldı. Geçmiş örnekler ayrı test dosyalarında korunuyor, canlı akışa müdahale etmiyor.
2. **DENGELİ biçimde güçlü motor önce:** `NaturalTranslationPolicy10427.modelFirst` güçlendirilmiş çeviri zinciri kullanılabilirken tam diyalog cümlesinin çevrilmesini öncelemeyi sağlar. Hazır İngilizce→Türkçe cümle sözlüğü başarılı güçlü motorun önüne geçmez. **Kullanıcının kendi yazdığı sözlük girdileri ve ses efekt etiketleri öncelikli kalır.** HIZLI biçimin düşük gecikmeli hazır sözlük yolu değiştirilmedi.
3. **Bağlam korundu:** DeepL'de `text` yalnız geçerli altyazı, `context` son üç kabul edilmiş İngilizce replik olmaya devam eder. Belirli oyun/senaryoya göre yeni kod/sözlük eklenmedi.
4. **Eski hafızanın bağlamı zorlaması sınırlandı:** İngilizce zamirler/göstericilerle (`it`, `they`, `this`, `he`, `she`, `here` vb.) geçmişe bağlı satırlar güçlü model kullanılırken salt metin anahtarıyla kayıtlı eski Türkçeden alınmaz. Hızlı çeviri hafıza sürümü **6→7** yükseltildi; eskiden kalan otomatik kaba çeviri kayıtları ilk başlatmada silinir. Kişisel sözlük korunur; AI hafızası yerinde kalır fakat bağlam duyarlı yeniden kullanım atlanır.
5. **Küfür tonunu körce ekleme azaltıldı:** DeepL'in anlamlı cevabına genel `amına koyayım` tipi sabit son ek zorlanmaz. Küfür kaybı kalite uyarısıdır; `DENGELİ` koşullarda mevcut ikinci motor denetimi devam eder. Bu kural küfürün her durumda eksiksiz korunacağını garanti etmez.
6. Sürüm 198 yapıldı; yeni doğal çeviri politikası testleri eklendi. Bazı eski statik testler yeni yöntem imzasını ve bilinçli olarak kaldırılan sahne-özel bypass'ı tanıyacak şekilde güncellendi.

## Gerçekte doğrulanan testler

- `NaturalTranslationPolicy10427` gerçek Kotlin kaynak dosyası derlenip çalıştırıldı: **15/15 başarılı** (çeviri modu, motor varlığı, ses etiketleri ve bağlam bağımlılığı).
- Seçilmiş eski Python/Java kalite ve entegrasyon testleri: **16/16 başarılı** (konuşmacı, argo, sözlük, etiket, Google bağlantısı, Unicode, altyazı yığını vb.).
- `tests/verify_release.py`: **6/6 başarılı** (yeni canlı akış için güncellenmiş statik beklentiler).
- `tests/verify_release_1034.py`: **9/9 başarılı** (yeni canlı akış için güncellenmiş statik beklentiler).
- Genel kaynak/yol kontrolleri: **10/10 başarılı**.
- Tam **61 test** denendi fakat ortamın yürütme zaman sınırına takıldı; **61/61 başarılı iddia edilmiyor**.

## Açık doğrulamalar

- Android SDK/Gradle ile **APK derlemesi yapılmadı**.
- Pixel 9 Pro/Android 17 canlı OCR, ekran döndürme, altyazı konumu ve gerçek çeviri kalitesi **test edilmedi**.
- DeepL/OPUS/Google üzerinden aynı bilinmeyen replikler için kör değerlendirme yapılmadı; doğal çeviride ölçülmüş iyileşme iddiası yok.
- `DENGELİ` modda daha sık güçlü motor çağrısı olabilir. DeepL kullanılıyorsa **API kullanım/kota/ücret** artabilir. Harici motorlar veya model yoksa önceki hazır sözlük/yerel yedek yolu çalışır.
- Hızlı repliklerde eski altyazının iptal edilmesi ve MediaProjection'ın dönme sonrasında kare üretmemesi bu çalışma kapsamı dışında; **çözülmüş değiller**.
- `app/debug.keystore` kaynak paketinde hâlâ vardır; herkese açık GitHub deposuna koymayın. Bu bir geliştirme imzalama anahtarıdır.

**Karar:** 198, genel ve bağlamsal çeviri odaklı **kaynak adayıdır**. Gerçek cihazda doğrulanmadan stabil sürüm sayılmaz.
