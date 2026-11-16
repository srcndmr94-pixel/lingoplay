# GameLingo — APK-299 baz sürüm saha düzeltmesi

**Kaynak alınan APK:** `1-Telefon-APK-299.zip` içindeki `app-debug.apk`.
AndroidManifest sürüm: `10.42.5-deepl-quality` (`versionCode=173`, paket `com.example.altyazi`).
**Kaynak temeli:** Aynı sürüm bilgisine sahip `Altyazi_GameLingo_10.42.5_deepl_kalite_kaynak.zip`.
**Yeni kaynak:** `versionCode=187`, `versionName=10.42.5-apk299-log-fix`.
APK ve kaynak sürüm bilgileri eşleşir; bu, derleme baytlarının birebir eşit olduğu anlamına gelmez.

## Müdahaleler

1. Kayıtta doğrulanan belirgin OCR kırılmalarının bağlama sınırlı düzeltilmesi:
   `Ata time` → `At a time`; `souls of our fatten` → `souls of our fallen`;
   `retun to the Blight` → `return to the Blight`; `Sha Chi unleased` →
   `Sha-Chi unleashed`; `unieasned otter any swing` →
   `unleashed after any swing`; `Standand Combo` → `Standard Combo`;
   `tem per this sword's soul` → `temper this sword's soul`;
   `IH can't explain it` → `I can't explain it`.
   Bu düzeltmeler genel kelime eşleştirmesi **değil**, yalnız logdaki kanıtlı bağlama bağlıdır.
2. `charge thg`, `...during a Standard` ve ağır bozulmuş `Witti enogi`/
   `Jtfarkwith`/`ineroa` okumalarını çeviri ve hafızaya göndermeden temiz OCR
   karesini bekleme. Yazılmayan bir kelimeyi tahmin ederek tamamlama yapılmaz.
3. HP / MP / XP / NPC vb. meşru HUD kısaltmaları “süslü yazı” puanına
   katılmıyor. Gerçek yetenek açıklamaları dekoratif metin filtresinde korunuyor.
4. `WU Xra H` gibi tek harfli parça içeren sahte konuşmacı adları öğrenilmiyor.
   Öğrenilmiş adlar yalnız kelime sınırında eşleştiriliyor.
5. Model çıktısı `Bronze ve Essence'ü`, `Sha ve Chi'ü` veya `Perfect ve
   Transcension` dediğinde kaynakta ilgili oyun terimi gerçekten bulunuyorsa
   terim korunuyor. `Soul Temper Leave` ve `8 Soul Temper` yalnız **tam
   eşleşen** oyun menüsü etiketleriyse sabit çeviriliyor.
6. Normal tekrarlar artık tanı kapalıyken “ÇEVRİLMEDİ” olarak günlük doldurmuyor;
   gerçek filtrelemeler günlükte kalıyor.
7. Temel sürümdeki ses etiketi OCR regex'inde bulunmayan `groupValues[1]`
   kullanımına karşı yakalama grubu eklendi. `(sighs Stick to the plan.`
   benzeri bir girdi JVM'de `IndexOutOfBoundsException` oluşturuyordu.

**Korunanlar:** DeepL/OPUS-MT/Google/Firefox zincirinin bu APK'deki mevcut
ayarı, OCR mimarisi, ekran yakalama, kullanıcı sözlüğü ve APK paket kimliği.
Başka sürümlerden modül veya özellik taşınmadı.

## Doğrulama (Android emülatör veya gerçek cihaz DEĞİL)

- `GameplayLog299Test.kt`: 34/34 Kotlin/JVM regresyonu geçti.
- `test_1039.py`: 11 + 3 ad tanıma ve 7 ses/konuşmacı OCR testi geçti.
- `test_short_name_kotlin.py`: 6/6 Kotlin ad koruma kontrolü geçti.
- `verify_10425_integration.py`: 19/19 kaynak bağlantı kontrolü geçti.
- Mevcut `test_cop_okuma.py`, `test_ses_etiketi.py`, `test_isim_hud.py` geçti.
- Android Gradle derlemesi ve Pixel 9 Pro uygulama testi: **YAPILMADI**.
- Eski 10.34/10.35 statik sürüm betikleri, sürüm adını `10.42` biçiminde
  beklediği için kaynak temeliyle uyumsuz; başarısız oldukları gerçeği gizlenmedi.

## Model karşılaştırmasının sınırları

Kullanıcının Pixel 9 Pro kıyaslamasında (11 sabit örnek, OCR ve uygulama
hafızası dışında) medyanlar OPUS-MT 137 ms, DeepL 283 ms, Google 189 ms,
ML Kit 60 ms, E4B 3918 ms. Bunlar canlı OCR→altyazı uçtan uca süreleri
veya anlam puanları değil. OPUS-MT bazı kaynak ayrıntılarını/küfürleri
atlayabiliyor. Bu yama motor sırasını değiştirmez.

## Kullanımdan önce

GitHub Actions derlemesinde **yeni ZIP'in seçildiğini logdan teyit edin**.
Mevcut workflow geçmişten yanlış ZIP seçebiliyor. Debug anahtarı da kaynak
paketinde yer alıyor; bunu herkese açık dağıtım anahtarı olarak kullanmayın.
Önceki APK'yi yedekleyin, mevcut uygulama verilerini temizlemeyin.
