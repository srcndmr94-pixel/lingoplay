# 10.42.7 — Alt konum ve tek uygulama yakalama

VersionCode 191. Baz kaynak: kullanıcının OCR rapor düzeltmesi ZIP'i; SHA256 3ed66ad8076a6d6b7c02bf8f9366bc5c5f5bb0aa80afad229fdf8833a9128ed0.

Türkçe çeviri ekranın altında tutulur; OCR şeridinin üstüne zorunlu taşıma ve önceki çeviriyi üstte yığma bu gösterim yolunda kaldırıldı. Ayarlarda alt konum gösterilir. Kaynak metin düzeltmeleri ve ham OCR tanıları korunur. Başlangıçta tek uygulama yakalama açıklaması eklendi.

Kullanım koşulu: Android yakalama ekranında Tek uygulama seçilerek oyun/video uygulaması paylaşılmalıdır; GameLingo seçilmemelidir. Bu seçim Android sistem ekranında kullanıcı tarafından yapılır; uygulama seçimi doğrulamaz veya zorlamaz. Tüm ekran seçildiğinde kendi çeviri katmanı kayda karışabilir. Bu sürüm tüm ekran için katman izolasyonu sağlamaz.

Derleme başarılı; APK imzası doğrulandı. OCR kural testi 33/33, güncellenmiş akış/yardımcı yerleşim testi 22/22, kaynak entegrasyonu 31/31. Akış testindeki eski üst konum bağlantısı beklentisi yeni alt konum davranışına güncellendi. Yardımcı yerleşim geometrisi testlerinin geçmesi, canlı alt konumun cihazda doğrulandığı anlamına gelmez. Pixel'de canlı OCR ve tek uygulama yakalama testi yapılmadı.
