"""
Sözlüklü OCR onarımını sınar (OcrSozluk.kt).

Bilgisayar sürümündeki onarım telefona taşındı: 124.207 kelimelik İngilizce
sözlük varlık olarak APK'ya giriyor, Kotlin tarafı onu yükleyip OCR hatalarını
düzeltiyor. Burada hem varlığın sağlamlığı hem de kuralların mantığı sınanıyor.

Kullanıcının 6 Kasım kaydındaki hatalar:
  Iwonder -> I wonder · Imay -> I may · labsolve -> I absolve
  (Lpity  -> (I pity  · lvories -> ivories
"""

import gzip
import os
import re
import sys

KOK = os.path.dirname(__file__)
APP = os.path.join(KOK, "..", "app", "src", "main")
# Sözlük res/raw'da duruyor: assets yolu APK'ya girmiyordu (6 Kasım kaydı).
VARLIK = os.path.join(APP, "res", "raw", "kelimeler.gz")
KAYNAK = os.path.join(APP, "java", "com", "example", "altyazi")

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


# --------------------------------------------------------------- 1) varlık

bekle(os.path.exists(VARLIK), "res/raw/kelimeler.gz yok")
if not os.path.exists(VARLIK):
    print("BAŞARISIZ: sözlük dosyası eksik")
    sys.exit(1)
# assets kopyasi kaldirildi: APK'ya girmiyordu, bosuna 334 KB tutuyordu.
bekle(not os.path.exists(os.path.join(APP, "assets", "kelimeler.txt.gz")),
      "assets kopyasi hala duruyor")

boyut = os.path.getsize(VARLIK)
bekle(boyut < 600_000, f"sozluk varligi fazla buyuk: {boyut} bayt")

with gzip.open(VARLIK, "rt", encoding="utf-8") as f:
    KELIMELER = {w.strip() for w in f if w.strip()}

bekle(len(KELIMELER) > 120_000, f"sozluk beklenenden kucuk: {len(KELIMELER)}")
for w in ("that", "wonder", "absolve", "pity", "ivories", "pickup", "iron", "able"):
    bekle(w in KELIMELER, f"sozlukte olmasi gereken kelime yok: {w}")
# hunspell kok listesi: turemis bicimler YOK. Kurallar buna gore yazildi.
bekle("remarkable" not in KELIMELER, "varsayim degisti: remarkable artik sozlukte")


# ------------------------------------------------------------ 2) Kotlin tarafı

ocr = open(os.path.join(KAYNAK, "OcrSozluk.kt"), encoding="utf-8").read()
yakala = open(os.path.join(KAYNAK, "CaptureService.kt"), encoding="utf-8").read()
app_kt = open(os.path.join(KAYNAK, "App.kt"), encoding="utf-8").read()

bekle("kelimeler.txt.gz" in ocr, "varlik adi Kotlin'de gecmiyor")
bekle("R.raw.kelimeler" in ocr, "res/raw yolu Kotlin'de yok")
bekle(ocr.index("R.raw.kelimeler") < ocr.index("app.assets.open"),
      "res/raw once denenmeli (assets APK'ya girmiyor)")
bekle("assets.list" in ocr, "hata halinde varlik listesi yazilmiyor")
bekle("GZIPInputStream" in ocr, "gz okuyucu yok")
for fn in ("fun yukle", "fun icinde", "fun harfleriDuzelt", "fun bol",
           "fun bitisikleriAyir", "fun onar", "fun turevMi", "fun adaylar"):
    bekle(fn in ocr, f"eksik fonksiyon: {fn}")
bekle("Sozluk.yukle" in app_kt, "sozluk uygulama acilisinda yuklenmiyor")
bekle("Sozluk.yukle" in yakala, "sozluk serviste yuklenmiyor")
bekle("OcrOnar.onar" in yakala, "onarim OCR akisina baglanmamis")
bekle("SÖZLÜK HAZIR" in yakala, "sozluk hazir kaydi yok")


# --------------------------------------------------- 3) kurallarin mantigi

def tablo(ad, metin):
    m = re.search(ad + r"[^=]*=\s*(?:listOf|setOf)\((.*?)\n\s*\)", metin, re.S)
    govde = re.sub(r"//[^\n]*", "", m.group(1)) if m else ""
    return [x for x in re.findall(r'"([^"]+)"', govde)]


EKLER = tablo("EKLER", ocr)
ONEKLER = tablo("ONEKLER", ocr)
I_ADLARI = tablo("I_ADLARI", ocr)
bekle(len(EKLER) >= 30, f"ek listesi kisa: {len(EKLER)}")
bekle("able" in EKLER and "ing" in EKLER, "temel ekler yok")
bekle("in" in ONEKLER and "un" in ONEKLER, "temel on ekler yok")
bekle("iran" in I_ADLARI and "italy" in I_ADLARI, "I ile baslayan adlar eksik")

# 0 ve 1 gercek rakam degil, OCR'in o/l yanlis okumasi: duzeltmeye girmeli.
bekle("it != '0' && it != '1'" in ocr,
      "0/1 rakam sayiliyor: 'g0od' -> 'good' duzelmez")
bekle("'0' to charArrayOf" in ocr and "'1' to charArrayOf" in ocr,
      "0/1 harf karisikligi tablosunda yok")


def turev_mi(w):
    for on in ONEKLER:
        if w.startswith(on) and len(w) - len(on) >= 5 and w[len(on):] in KELIMELER:
            return True
    for ek in EKLER:
        if not w.endswith(ek):
            continue
        g = w[: len(w) - len(ek)]
        if len(g) < 3:
            continue
        if g in KELIMELER or (g + "e") in KELIMELER:
            return True
        if len(g) >= 2 and g[-1] == g[-2] and g[:-1] in KELIMELER:
            return True
        if g.endswith("i") and (g[:-1] + "y") in KELIMELER:
            return True
    return False


# Gercek kelimeler bolunmemeli
for w in ("remarkable", "reasonable", "understandable", "incredible",
          "unbelievable", "running", "happily", "movable"):
    bekle(w in KELIMELER or turev_mi(w), f"gercek kelime korunmuyor: {w}")

# Yapisik yazim bolunebilmeli (turev sayilmamali)
for w in ("topickup", "andwhat", "inthe", "thispoint"):
    bekle(not turev_mi(w), f"yapisik yazim turev sanildi: {w}")

# Bastaki I kurali: gercek adlar bozulmamali
# Kotlin dizesi kacisli tirnak icerir: ([\"(-]*)[IlL]...
BASTAKI = re.compile(r'BASTAKI_I = Regex\("((?:\\.|[^"\\])+)"\)')
m = BASTAKI.search(ocr)
bekle(m is not None, "BASTAKI_I deseni bulunamadi")
bekle("[IlL]" in (m.group(1) if m else ""), "bastaki harf I/l/L degil")

for tam, kalan in [("iran", "ran"), ("iron", "ron"), ("italy", "taly"),
                   ("ireland", "reland"), ("land", "and"), ("lover", "over"),
                   ("list", "ist"), ("lads", "ads")]:
    korunur = tam in KELIMELER or tam in I_ADLARI or kalan not in KELIMELER
    bekle(korunur, f"bolunme riski: {tam} -> I {kalan}")

# Hatali okumalar duzelebilmeli
for tam, kalan in [("iwonder", "wonder"), ("imay", "may"),
                   ("labsolve", "absolve"), ("lpity", "pity")]:
    duzelir = tam not in KELIMELER and kalan in KELIMELER and tam not in I_ADLARI
    bekle(duzelir, f"duzelemeyecek: {tam} -> I {kalan}")


# ------------------------------------------------------------------- sonuc

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print(f"Sözlüklü OCR onarımı testi geçti "
      f"({len(KELIMELER)} kelime, {boyut // 1024} KB varlık, {len(EKLER)} ek).")
