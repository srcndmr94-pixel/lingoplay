# GameLingo OPUS-MT yerel motoru — deneysel kurulum

Bu paket **gerçek Helsinki-NLP/opus-tatoeba-en-tr** (Apache-2.0) Marian
çeviri ağını Android'de ONNX Runtime ile çalıştırmak için kaynak kodu içerir.
Sabit replik sözlüğü **DEĞİLDİR**. Model ağırlıkları kaynak ZIP'inde **YOKTUR**.

## Durum / dürüst sınır

- Uygulama OPUS-MT'yi varsayılan olarak KAPALI başlatır. Sadece doğrulanmış model
  ZIP'i içe aktarılıp ayarlardan açıkça açılırsa OPUS canlı hatta katılır.
- Model hazır değilse Google ve mevcut Firefox/yerel yedekler korunur.
- Modelin Pixel 9 Pro'da gerçek doğruluğu/hızı **henüz test edilmedi**.
- Tokenleştirme saf Kotlin Viterbi SentencePiece unigram + NFKC yaklaşımıdır;
  C++ SentencePiece normalizer'ının tüm özelleştirmeleri kapsanmaz. Bu yüzden
  gerçek modelden alınan sonuçları Transformers referansıyla karşılaştırmak şart.
- Greedy ONNX decoder, 500 ms garantisi vermez; sınırı 2,5 saniye olup geç
  kalırsa Google'a düşülür. Çalıştırıcı GPU hızlandırması kullanmıyor.

## Model üretimi

Repo GitHub'da ana dizinde kaynak ZIP olarak tutulduğu için **workflow YAML
ayrı yüklenmelidir**. `.github/workflows/opus_mt_model.yml` dosyasını GitHub'da
aynı yola ekleyin (ZIP'in içinde olması tek başına GitHub Actions'a tanıtmaz).

1. `Altyazi_GameLingo_10.40_opus_kaynak.zip` dosyasını GitHub ana dizinine yükleyin.
2. `.github/workflows/opus_mt_model.yml` dosyasını **ayrıca** GitHub deposundaki
   `.github/workflows/` yoluna ekleyin.
3. **Actions → GameLingo OPUS-MT yerel model paketi → Run workflow** çalıştırın.
4. Artifacts içinden `GameLingo-OPUS-MT-en-tr-model` ZIP'ini indirin.
5. Android ayarlarında **OPUS-MT model ZIP dosyasını seç** seçeneğini açın.
   İndirilen artifact ZIP'inin **içindeki** `GameLingo_OPUS_MT_en_tr_model.zip`
   dosyasını seçin.
6. Önce canlı çeviriyi durdurun, **OPUS / E4B / ML KIT / GOOGLE KARSILASTIR**
   testini çalıştırın. Gerçek sonuçları görmeden OPUS-MT'yi canlıya açmayın.

## Kapsam

- `OpusMtEngine.kt`: encoder/decoder ONNX, greedy seq2seq çözümleme.
- `OpusSentencePiece.kt`: ağırlıklı SentencePiece unigram / protobuf okuyucu.
- `OpusModelPack.kt`: zip doğrulama, güvenli içe alma, atomik paket değişimi.
- `tools/opus_mt/export_model.py`: Apache-2.0 modelin gerçek ağırlıklarından
  ONNX paketi üretimi ve şema doğrulaması.
- `EngineBenchmark1035.kt`: 11 repliklik bağımsız OPUS testi.
- `CaptureService.kt`: açıkça etkinleştirilen OPUS önceliği ve güvenli yedekler.

## Güvenlik ve lisans

Model: https://huggingface.co/Helsinki-NLP/opus-tatoeba-en-tr (Apache-2.0).
ONNX Runtime: MIT. Bağımlılık lisanslarına ve model atıf koşullarına uyun.
