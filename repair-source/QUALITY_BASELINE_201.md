# GameLingo 201 — Çalışanı koruyan geri alma ve dar düzeltme

**Kaynak temeli:** 199 sürümü. 200 sürümünün 125 ms ekstra bekleme ve her kalite uyarısını ret sayma davranışı alınmadı.

## Korunanlar
- DeepL / OPUS-MT / Google / Firefox sırası; dengeli modda 420 ms yerel yedek başlatma; 550 ms yerel çeviri sınırı
- 199'un mevcut çeviri hafızası, kullanıcı sözlüğü, bağlam ve altyazı konumu
- Hızlı/Doğru Önce modları ve eski repliğin yeni repliğe yazılmasını önleyen nesil kontrolü

## Sınırlı iyileştirmeler
- Yaygın kapalı altyazı ses işaretleri, ör. `both groaning`, `horn honking`, `exclaims`, tekil konuşma cümlesi ezberlenmeden çevriliyor
- Bazı OCR bozuk ses etiketi parantezleri onarılıyor; sıradan konuşma cümleleri korunuyor

## Doğrulama kuralı
- Yeni APK, 199 ve 200 ile ayrı sahnelerde karşılaştırılmadan daha iyi ilan edilmeyecek.
- Kısa/argo/deyim repliklerini gerçekten doğru çevirdiği cihaz testiyle gösterilmeli; statik kaynak testleri bunu kanıtlamaz.
- Geliştirme debug anahtarı ZIP içindedir; yalnız özel kaynak deposunda kullanılmalı. Genel paylaşım için çıkarılması ve imzalama sürecinin ayrılması gerekir.
