# GameLingo 10.40-rc1 — Kaynak çekirdeği, yayımlanmamış aday

## Doğrulanmış gerçek sorun

GameLingo 10.39, devam eden çevirileri İngilizce kaynak metinle (`HashSet<String>`) takip ediyordu. A → B → A gibi yinelenen bir replikte önceki A hâlâ işleniyorsa yeni A engellenebiliyor; aynı metnin eski cevabının yeni altyazıyla karışma riski vardı. Kuyruk dolunca en güncel replik de kaybedilebiliyordu.

## Değiştirilen çalışma akışı

- Her kabul edilmiş altyazıya, metinden bağımsız monoton istek numarası (`SubtitleGenerationGate`) veriliyor.
- Hızlı/Dengeli ana çeviri yolundaki **15 publish noktası** yalnız güncel numaraya ait sonucu ekrana gönderebiliyor.
- Kuyruk doluysa yeni repliği silmek yerine en eski işlemin işi iptal edilerek yeni isteğe yer açılıyor.
- Aynı İngilizce metnin yeni göründüğü durumlar farklı numarayla işlenebiliyor.
- Capture kapanınca veya altyazı ekrandan kaybolunca eski istekler geçersiz kılınıyor.
- Hafıza başlangıç zamanının eski istek tarafından silinmemesi için map'te koşullu silme yapılıyor.

## Gerçekten çalıştırılmış testler

- `SubtitleGenerationGateTest.kt`: 8 işlev senaryosu ve 160 eşzamanlı güncelleme başarılı.
- `MeaningSafety1033Test.kt`: 43/43 kontrol başarılı.
- `Quality1031Test.kt`: 14/14 kontrol başarılı.
- `test_1039.py`: 21 isim ve konuşmacı OCR testi başarılı.
- `tests/verify_release.py`: 6/6 mevcut işlev bağlantısı başarılı.
- `tests/verify_live_request_core.py`: 11 statik çağrı/kimlik kontrolü başarılı.

## Tamamlanmamışlar — asla kanıtlanmış sayılmamalı

- **Android derlemesi yapılmadı:** yerel Android SDK, Gradle ve bağımlılık deposu erişimi yok.
- **Pixel 9 Pro cihaz testi yapılmadı.** Google/Firefox/API servisleri ve GPU ölçülmedi.
- **Doğal Türkçe modeli değiştirilmedi.** 10.36 benchmark'ında Google düşük gecikmeyle çalışsa da deyimler ve küfürde hatalı; E4B 3 sn'den yavaş; ML Kit anlam hatalı. Başka bir doğrulanmış model bulunmadığından bunu çözdüğümüz iddia edilemez.
- Telefon uygulaması ile TV/PC/PS5 sürümleri için gerçek bina testi yok.

Bu paket yalnız eşzamanlı altyazı güvenilirliği için sürüm adayıdır. Kullanıcıların asıl istediği yeni/kaliteli çeviri motoru değildir; son ürün gibi sunulmamalıdır.
