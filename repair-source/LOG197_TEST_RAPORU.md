# GameLingo 10.42.7 — 16 Kasım canlı kayıt düzeltmeleri

**Aday kaynak:** versionCode 197; versionName `10.42.7-live-log-fix-rc3`.
**Temel:** 196 kaynak ZIP. Bu paket **kaynak koddur, APK değildir**.

## Kaynak kodunda değişenler

1. `OfflineFix.kt`: `what's happening` sözlük karşılığı `Ne var ne yok` yerine **Neler oluyor** oldu. `What's up` gibi farklı selamlaşmalara dokunulmadı.
2. `GameplayLogRepair299.kt`: `Hey`, `Yo`, `Hello` benzeri tek sözcüklü ünlemlerin yanlışlıkla konuşmacı adı öğrenilmesi engellendi. `Raymond`, `Old Man Gen` gibi adlar hâlâ kabul ediliyor.
3. `SourceAwareDialogueFix.kt`: gönderilen logda açıkça görülen `Phase One/Two/Three/Four/Five`, `tanks`, `long enough to learn to talk` satırlarına yalnızca birebir kaynak eşleşmesinde doğru çeviri eklendi. Bilinmeyen replikler değişmez. Sahneye özgü sabit eşleştirmelerdir; genel makine çeviri hatalarının hepsini gidermez.
4. `CaptureService.kt`: doğrulanmış birebir replikler **DENGELİ** biçimde de sözlük/hafızadan önce uygulanır; sonradan gelen eski cevapların yeni altyazı üzerinde görünmesini önleyen generation kontrolü korunur. Döndürme sonrası kare gelmemesi uyarısı artık tek nedenin durağan ekran olduğunu iddia etmiyor; kullanıcının ne zaman yeniden yakalama izni vermesi gerektiğini açıklıyor. Sanal ekranı tekrar oluşturan riskli yol eklenmedi.
5. `app/build.gradle.kts`: sürüm 197'ye yükseltildi.

## Test kanıtı

- Gerçek `SourceAwareDialogueFix` + `GameplayLogRepair299` Kotlin dosyalarıyla `test/LiveLogFix197Test.kt`: **14/14 başarılı**.
- İlgili eski Python kontrolleri: `test_konusan`, `test_etiket_onar`, `test_sozluk`, `test_tezgah`, `test_bicim`, `test_argo`, `test_kurtarma`, `test_google`, `test_yalniz_dogru`, `test_yigin`, `test_sablon`, `test_unicode`, `test_harf`: **13/13 başarılı** (kurtarma testindeki kısa log-ifadeleri eşitleme sonrasında tekrar PASS).
- Tam **61 test** otomasyonunu iki kez başlatmayı denedik ancak ortamın çalıştırma sınırında kesildi. Dolayısıyla yeni sürüm için **61/61 geçti denemez**. İlk tur 19 kontrolü başarıyla tamamlamıştı, fakat tam set tamamlanmadı.
- Android SDK ile tam APK derleme ve Pixel 9 Pro cihaz testi **yapılmadı**.

## Açık kalanlar

- `She's dying.` kısa repliğinin yeni satır kabul edildiğinde atlanması (`ESKİ İSTEK ATLANDI`), eski cevabı yeni replik üstüne yazmamak için mevcut korumadır; bu kayıtla **ortadan kalktığı kanıtlanamaz**. Tam çözüm için gerçek cihazda gecikme ve altyazı görünürlük süresi ölçülmeli.
- Döndürmeden sonraki 303 boş okumada yeni kare üretilmemiş. Bunun oyun/dizi durması mı, MediaProjection yüzeyi mi olduğuna yalnız logdan kesin karar verilemez. Kurtarma akışı korunmuştur; sadece uyarı iyileştirilmiştir. Sorun telefon üzerinde tekrar test edilmelidir.
- Logda DeepL kabul edilirken OPUS 0 kabul olması tek başına hata kanıtı değildir: yedek motor ilk motor başarılıyken kullanılmaz.
- `app/debug.keystore` kaynakta bulunmaktadır. **Herkese açık GitHub deposuna bu anahtarı göndermeyin.**
