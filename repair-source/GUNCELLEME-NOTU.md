# GameLingo 10.22 — güvenli bağlam güçlendirmesi

Temel: `altyazi-10.21-kalite-duzeltme.zip` içindeki GameLingo 10.21 kaynakları.

## Kod değişiklikleri

- `DialogueContextRepair.kt`: Dört özel cümleyle sınırlı eski yaklaşımın yanına **genel ama sınırlı** iki-satır bağlam birleştirme eklendi. Önceki cümle açıkça yarım kalmışsa (ör. sonda `with`, `could`, `for` veya virgül), sonraki satır küçük harfle devam ediyorsa ve iki okuma en çok 7 saniye aralıklıysa tek Google çeviri girdisi hazırlanır.
- Yanlış birleşme önlemleri: önceki cümle bitmişse, araya konuşmacı/ses etiketi girmişse, kaynak çok uzunsa, belirgin devam yoksa veya 7 saniye aşılmışsa eski çeviri davranışı korunur.
- `CaptureService.kt`: sadece **HIZLI + Google açık** biçimde, tam sözlük/diyalog kurallarından sonra ama eski çeviri hafızasından önce birleştirilmiş girdiyi mevcut `fastTranslateKeepingName` fonksiyonuna verir. Başarılı olursa aynı altyazının kimliğiyle ekrana yazar; iki satırlı sonuç tek-satır çeviri olarak kalıcı hafızaya alınmaz. Ayrı bağlam isteği eklenmez. Birleştirme uygulanmazsa eski yol aynıdır.
- Google, Firefox, ML Kit, ses etiketi, isim/küfür koruma ve ekran yakalama kütüphaneleri değiştirilmedi.
- Android telefon kaynak sürümü `10.22`, `versionCode = 155`.

## Test edilenler

- 10.22 yeni gerçek Kotlin testinde: güvenli birleştirme, zaman sınırı, konuşmacı sınırı, geçmiş temizliği, yanlış birleşmeyi önleme: **geçti**.
- 10.21 eski gerçek Kotlin bağlam testi ve 10.20 mevcut bağlam testi: **geçti**.
- Google ham girdi / isim / küfür / yedek motor / argo kontrolleri: **geçti**.
- Üretim koduna ekleme noktasının korunması için yeni statik entegrasyon testi: **geçti**.

## Bilinen sınırlar

- Altyazı açık bir devam işareti göstermiyorsa birleştirilmez; her bağlam sorunu otomatik çözülmez.
- Birleştirilmiş çeviri önceki cümleyi de gösterebilir. Cümle anlamı için bu bilinçli tercihtir.
- Gerçek Google/Firefox çeviri doğruluğu, çeviri gecikmesi, Android tam derlemesi ve telefonda ekran testi **bu ortamda doğrulanmadı**. Bu dosya APK değildir.
- Önceki çalışan APK'yi silmeden önce yeni APK'yi test edin.
