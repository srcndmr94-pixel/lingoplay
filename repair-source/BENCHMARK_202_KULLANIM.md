# GameLingo 202 — Sadece ölçüm sürümü

- `versionCode=202`, `versionName=10.42.7-benchmark-rc8`
- Çeviri motorlarının sırası, 420 ms yerel yedek, 550 ms timeout, ilk-sonuç kilidi, altyazı yerleşimi değişmedi.
- 201 kaynak koduna yalnız `BENCH202|...` ölçüm kayıtları eklendi.
- DeepL, OPUS, Google ve hızlı ML Kit/Firefox denemeleri: motor adı, gecikme, kabul/red, Türkçe yanıt, İngilizce kaynak, önceki bağlam, runId kaydı.
- `stage=DISPLAY_ACCEPTED`: altyazı gösterim hattına kabul edilen sonuç. `stage=LOCKED`: ilk sonuç nedeniyle sonradan gösterilmeyen sonuç.
- Kayıtlar İngilizce/Türkçe diyalog içerebilir; paylaşmadan önce kişisel metinleri kontrol edin. API anahtarı kayda alınmaz.
- `tools/benchmark202_log.py calisma_kaydi.txt`: aynı klasöre CSV dökümleri üretir. Yanıtların kalite puanı otomatik verilmez.
- GitHub deposu herkese açık olduğundan **imzalama anahtarı pakete dahil edilmedi**. **202 uygulaması 201'in yanına ayrı simgeyle kurulur** (`com.example.altyazi.benchmark`). Bu, eskisini silmeden karşılaştırmayı sağlar. Yeni uygulamada DeepL ve diğer ayarları yeniden yapılandırmanız gerekir.
- Yeni debug APK'sı kendi imzasına sahiptir. Yeniden derlenen 202 APK'sını üzerine kuramazsanız yalnız 202 benchmark uygulamasını kaldırıp yeniden kurabilirsiniz; 201 etkilenmez.
- Mevcut açık kaynak ZIP'deki eski debug anahtarının sızmış sayılması gerekir; gelecekte üretim anahtarı rotasyonu planlanmalıdır.
- İlk sonuç kilidini değiştirmedik. 202, 201'den daha iyi çeviri yapma iddiasında değildir.
- Android APK / cihaz testi bu ortamda yapılmadı. Statik regresyon ve araç testleri ayrıca raporlanır.
