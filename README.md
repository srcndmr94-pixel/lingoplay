# SubtitlesProjectFinal

Derlenebilir Jetpack Compose proje yapısı.

İçerik:
- TextFix: OCR düzeltme
- SoundCaptionFix: yerel ses/müzik açıklaması çevirisi
- SubtitleProcessor: tekrar önleme, 220 ms debounce, bağlam tamponu
- TurkishTextFix: bağlamsal Türkçe düzeltme ve satır sınırı
- DataStore + ViewModel + Factory
- Slider ve canlı önizleme
- Sistem temasını algılama ve animasyonlu geçiş

## Çeviri motoru entegrasyonu

`SubtitleTranslator` arayüzünü kendi çeviri motorunuzla uygulayın:

```kotlin
val translator = SubtitleTranslator { source, context ->
    yourTranslator.translate(source, context)
}
```

Ardından gerçek OCR sonucunu `LiveSubtitleViewModel.onOcrText(rawText)` metoduna iletin.

Not: Proje gerçek OCR/kamera ve gerçek çeviri SDK'sını bilerek dahil etmez; bu servislerin anahtarları ve sağlayıcısı projeye özeldir.
