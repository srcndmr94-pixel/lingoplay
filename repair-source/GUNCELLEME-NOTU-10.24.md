# GameLingo 10.24 – kalıcı yanlış çeviri ve HTTP 429 koruması

Temel: 10.23 derleme hatası düzeltilmiş kaynak paketi (`hitapDuzelt` doğru isim).

## Değişiklikler
1. Anahtarsız Google, HTTP 429 aldığı anda 2–15 dakika arasında artan arayla duraklar; sunucunun sayısal `Retry-After` değeri dikkate alınır. Timeout / diğer hatalar için önceki 3 hata / 60 saniye yolu korunur. Aynı süreçte ekran yakalama yeniden başladığında HTTP 429 duraklaması sıfırlanmaz. Google'a otomatik ek ağ isteği yapılmaz.
2. Google etkinse Firefox/ML Kit'ten gelen geçici yedek çeviriler artık `OfflineFix` motor önbelleğine veya HIZLI kalıcı çeviri hafızasına yazılmaz; önceki hızlı hafıza kayıtları Google açıkken okunmaz. Google başarılı çevirileri kendi 400 girdilik hafızasında tutar; kullanıcı sözlüğü ve YZ hafızası korunur.
3. Google kullanılamazken metin sonundaki `, too` ifadesi yedek çeviriye de `as well` biçiminde hazırlanır; `too much/too late` değişmez.
4. Üç bağlamı belirgin OCR hatası (`mnore`, `timne`, başlangıç `H don't know`) dar kapsamda düzeltilir. Özel isim tahmini yapılmaz.
5. Google beklemedeyken bitişik altyazıyı gereksiz yere yedek motora birleştirerek gönderen işlem atlanır.
6. Android ICU regex denetiminin üç tırnaklı ifade için yanlış ayrıştırdığı bir desen aynı anlamda standart Kotlin dizesine çevrildi.
7. Çeviri motorları, kullanıcı arayüzü, bölge seçimi ve ses/küfür kuralları başka biçimde değiştirilmedi. `app` sürümü 10.24 / versionCode 157.

## Testler
- Gerçek `GoogleCeviri.kt` ve HTTP 429 durumunu simüle eden Kotlin derleme/çalışma testi geçti (sahte HTTP yanıtı; gerçek Google ağı değil).
- `GoogleRateGuard` ve `SubtitlePreparation` Kotlin kontrolleri geçti.
- `OfflineFix` kaynak kodundaki `cached` ve `cachedRun` yöntemlerinin gerçek gövdeleriyle Kotlin testi geçti.
- `DialogueContextRepair` Kotlin testi geçti.
- Python/Java/Android ICU regex, XML, Kotlin parametre ve dosyalar arası çağrı kontrolleri geçti.
- Google ham yol, Firefox, deyim, küfür, 10.22 bağlam ve 10.23 bölge kontrol testleri geçti.
- `test/run_all.py` tam paketi zaman sınırına takıldı; tüm testler geçti iddiası yok.

## Doğrulama sınırı
**APK derlenmedi ve Android telefonda çalıştırılmadı.** Kod testleri APK derlemesinin ya da doğal Türkçe kalitesinin kanıtı değildir. Google endpoint'i resmî/sınırsız API değildir ve 429 tekrar yaşanabilir. Yedek Firefox çevirilerinin anlam kalitesi bu sürümde garanti edilmez.

## Yükleme
Kaynak ZIP'i `srcndmr94-pixel/lingoplay` GitHub deposunun ana dizinine yükleyin. Repodaki mevcut `.github/workflows/build.yml` dosyası en son yüklenen `altyazi` adlı ZIP'i açar ve Actions ile APK derlemeyi dener. Önceki çalışan APK'yi kaldırmayın.
