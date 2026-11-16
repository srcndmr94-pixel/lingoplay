# GameLingo 10.42.7 — Genel anlam ve bağlam düzeltmesi

## Kaynak ve kapsam

- **Temel:** `Altyazi_GameLingo_10.42.7_Yo_baglam_guncel_altyazi_kaynak.zip`; bozuk 10.43.x sürümleri temel alınmadı.
- **Yeni sürüm:** `10.42.7-meaning-flow-rc`, `versionCode 195`, `applicationId com.example.altyazi`.
- **Amaç:** Oyun/diziye özgü hazır replik sayısını artırmak değil, bilinmeyen İngilizce cümlelerde bağlam ve anlam kaybı riskini azaltmak.
- **Korunanlar:** DeepL → OPUS-MT → Google/Firefox canlı zinciri, alt altyazı konumu, OCR tarama hızı, ekran yakalama servisinin başlatma sırası, sözlük ve kişisel ayarlar.

## Kod değişiklikleri

1. **Anlamı çözmeye yardımcı bağlam:** `DialogueContextRepair` kabul anında kaydedilmiş en fazla üç önceki repliği (15 saniyelik sahne sınırı) topluyor. `TranslationTurnContext10427` bağımsız ve iptal edilebilir coroutine işiyle bunu eşliyor. `DeepLCeviri` yalnızca canlı istekte `context` alanını ekliyor; `text` alanına yalnızca güncel altyazı yazılıyor. DeepL API'nin `context` alanı çeviriye dahil edilmeden anlamı belirlemek için kullanılabilir. Diğer motorların davranışı aynı.
2. **Kopuk cümleler:** `DialogueContextRepair.joinedTranslationInput` birbiriyle çelişen yardımcı fiil birleşimlerini reddediyor (`My name is` + `was ...`), ancak bariz zarf tümleci devamlarını kabul ediyor (`leave them squirming` + `like turtles ...`). Farklı konuşmacı/kapalı cümle sınırları korunuyor.
3. **Uydurma içerik:** `TranslationQualityGate` kaynaktan gelmeyen `[ad]`, `[film/dizi]` ve benzeri yer tutucuları `INVENTED_PLACEHOLDER` olarak reddediyor. Geçerli `[Eve]` konuşmacı ve `[groans]` ses etiketleri istisna olarak korunuyor.
4. **Sesin anlamı:** `OfflineFix.SoundTags` elektrik cızırtısı, telefon titreşimi, hafif vızıltı ve genel vızıltıyı farklı Türkçe karşılıyor. Önceden düz `buzzing` için kullanılan `titriyor` genellemesi kaldırıldı.
5. **Genel ad yapısı:** `ProperNameStructure10427`, İngilizcede `A and B Surname` yapısı varken Türkçede `A, B ve Surname` diye parçalanmış çıktıyı onarabiliyor. Belirli film/oyun karakterlerine göre sabit liste kullanmıyor.
6. **Bağlam-duyarlı hafıza:** `TranslationCachePolicy10427` üç kelimeye kadar kısa repliklerin, kesik/yarım ifadelerin ve üç nokta içerenlerin yalnız kaynak metinle kalıcı eşlenmesini önlüyor. Hafıza sürümü 5→6 yükseltildi. Kullanıcının kişisel sözlüğü ve OPUS dil modeli silinmiyor. Bu, yalnızca eski kısa çevirinin yanlış bağlamda tekrarlanma riskini azaltır; tek başına çevirinin doğru olacağını garanti etmez.

## Yapılan testler

| Kontrol | Sonuç |
|---|---|
| Yeni bağlam, devam cümlesi, sahte yer tutucu (Kotlin/JVM) | **16/16 geçti** |
| Ses etiketi; gerçek üretim SoundTags nesnesiyle (Kotlin/JVM) | **8/8 geçti** |
| Genel çok sözcüklü isim koruması (Kotlin/JVM) | **5/5 geçti** |
| Çeviri hafızası kabul politikası (Kotlin/JVM) | **13/13 geçti** |
| Eski 78 repliklik tezgâh | **Geçti** |
| Mevcut 61 testin tam sıralı turu | **53 geçti, 8 başarısız, 0 zaman aşımı** |

### Sekiz açık eski kontrol

`test_denetleyici.py`, `test_yigin.py`, `test_unicode.py`, `test_yalniz_dogru.py`, `test_dusunme.py`, `test_bicim.py`, `test_google.py`, `kotlin_denetle.py`.

Bu sekiz kontrol önceki temel kaynağın test turunda da başarısızdı. Bazıları statik kod eşleşmesi/analiz beklentisi, bazıları gerçek entegrasyon boşluğuyla ilgili olabilir. **Hiçbiri bu düzeltmeyle çözülmüş sayılmıyor.** Özellikle Android regex uyumluluğu, yığın görünümü, Gemini düşünme ayarları ve canlı Google yedeği ayrı doğrulama gerektiriyor.

Test çıktısı `MEANING-ESKI-TAM-TEST-SONUC.json`, `MEANING-ESKI-TAM-TEST-CIKTI.txt` dosyalarında; testi yeniden çalıştıran komut `python run_all_61.py`.

## Henüz yapılmayan doğrulamalar

- **Android Gradle APK derlemesi yapılmadı** (bu ortamda Android SDK yok).
- **Pixel 9 Pro / Android 17 gerçek ekran yakalama, canlı DeepL, OPUS, overlay ve OCR uçtan uca testi yapılmadı.**
- DeepL canlı ağ `context` alanının gerçek sunucudan dönen kaliteye etkisi test edilmedi; 11 referans örneği burada yeniden çalıştırılmadı ve geçmiş benchmark tek başına bağlam etkisini kanıtlamaz.
- Küfür/argo/kişi zamirleri/deyimler için **%100 semantik doğruluk** garanti edilemez. Kural tabanlı kalite kapısı akıcı fakat anlamı yanlış çevrilmiş her cümleyi ayırt edemez.
- Mevcut ZIP, önceki yüklemelerle uyumlu geliştirme debug imzası için `app/debug.keystore` içeriyor. **Güvenli, genel dağıtım imzası değildir; ZIP'i herkese açık depo veya paylaşımda yayımlamayın.**
- GitHub Actions'ın mevcut kaynak ZIP'i Git geçmişinden seçen otomatik betiği değiştirilmedi. `versionCode 195` ve sürüm adını derleme sonucunda ayrıca doğrulamak gerekir.

## Sonuç

Bu paket mevcut 10.42.7'de **kaynak düzeyindeki belirli genel anlam/bağlam hatalarını hedefleyen bir adaydır**. Tamamen doğru çeviri veya sorunsuz Android çalışması olarak etiketlenmemelidir. Çalışan APK'yı silmeden önce doğru kaynak derlemesi ve gerçek cihaz testi gerekir.
