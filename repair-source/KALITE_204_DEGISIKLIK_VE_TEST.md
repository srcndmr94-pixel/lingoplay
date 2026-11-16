# GameLingo 204 – Genel çeviri kalitesi düzeltmeleri

- LOST_PROFANITY uyarısı tek başına DeepL/OPUS/Google çevirisinin zayıf bir Firefox alternatifiyle değiştirilmesine yol açmaz.
- Yall, Whatchu, Aight, Bruh gibi yaygın İngilizce sokak konuşması sözcükleri korunan özel ad sayılmaz. Motor girdisi dilbilgisel İngilizceye normalize edilir; OCR ve log orijinal kalır.
- Türkçe metinde çevrilmeden kalan sokak dili ekrana verilmeden reddedilir.
- Yerel hızlı çeviri de aynı giriş normalizasyonunu kullanır.
- Motor sırası, 420 ms hedge, 550 ms timeout, altyazı konumu ve kullanıcı sözlüğü değişmedi.
- Ekran dönmesinden sonra 47 saniye görüntü gelmemesi ayrı bir MediaProjection sorunudur ve bu değişiklik kapsamında çözülmüş sayılmıyor.
- APK derlemesi ve Pixel 9 Pro canlı testi mevcut ortamda doğrulanmadı.

- 203 hızlı çeviri hafızası yeni kural sonrası otomatik yenilenir; kişisel sözlük ve DeepL anahtarı silinmez.

- PS5 yama kaynagi bulunmadigindan GitHub Actions PS5 isini atlar; telefon ve TV derleme adimlari etkilenmez.
