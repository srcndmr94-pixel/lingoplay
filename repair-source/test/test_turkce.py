"""
Çevrimdışı çevirinin Türkçe düzeltmelerini sınar.

İki hata sınıfı, ikisi de kullanıcının gerçek kaydından:

  "Do I know that name?"      -> "Bu ismi biliyorum mi?"   (soru eki yanlış)
  "[dramatic music playing]"  -> "[Dramatik Müzik Oyna Ingl" (ses etiketi)

Ses etiketi tablosunu Kotlin kaynağından okuyoruz ki ikisi ayrışmasın.
"""

import os
import re
import sys

KAYNAK = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main",
                      "java", "com", "example", "altyazi", "TurkishFix.kt")

metin = open(KAYNAK, encoding="utf-8").read()

blok = re.search(r"SES_IPUCLARI[^=]*=\s*listOf\((.*?)\n\s*\)", metin, re.S)
if not blok:
    print("SES_IPUCLARI tablosu bulunamadi")
    sys.exit(1)
TABLO = re.findall(r'"([^"]+)"\s+to\s+"([^"]+)"', blok.group(1))

# Kotlin'deki \p{L} karsiligi: Python'da re modulu \p desteklemez,
# Unicode harf icin [^\W\d_] kullaniyoruz.
HARF = r"[^\W\d_]"
SORU_EKI = re.compile(
    rf"(?<!{HARF})({HARF}*?[ıiuü]yor)(um|sun|uz|sunuz)\s+m[ıiuü](?!{HARF})",
    re.IGNORECASE | re.UNICODE,
)

KISI = {"um": "muyum", "sun": "musun", "uz": "muyuz", "sunuz": "musunuz"}


def duzelt(t):
    def degistir(m):
        return m.group(1) + " " + KISI.get(m.group(2).lower(), "mu")
    t = SORU_EKI.sub(degistir, t)
    t = re.sub(r"\s+([,.!?:;])", r"\1", t)
    return re.sub(r"\s+", " ", t).strip()


def ses_ipucu(ing):
    k = ing.strip()
    if not (k.startswith("[") or k.startswith("(")):
        return None
    sade = "".join(c for c in k.lower() if c.isalpha() or c == " ")
    if not sade.strip():
        return None
    if "dramatic" in sade and "music" in sade:
        return "[gerilim müziği]"
    if "tense" in sade and "music" in sade:
        return "[gergin müzik]"
    for anahtar, karsilik in TABLO:
        if anahtar in sade:
            return f"[{karsilik}]"
    return None


hata = []

# --- Soru eki ------------------------------------------------------
SORU = [
    ("Bu ismi biliyorum mi?", "Bu ismi biliyor muyum?"),
    ("Geliyorsun mu?", "Geliyor musun?"),
    ("Biliyoruz mu?", "Biliyor muyuz?"),
    ("Geliyorsunuz mu?", "Geliyor musunuz?"),
    # Turkce harf iceren govde de calismali
    ("Çalışıyorum mu?", "Çalışıyor muyum?"),
]
for giris, beklenen in SORU:
    cikti = duzelt(giris)
    if cikti != beklenen:
        hata.append(f'"{giris}" -> "{cikti}" (beklenen: "{beklenen}")')

# --- Zaten dogru olanlar bozulmamali -------------------------------
DOKUNMA = [
    "Bu doğru mu?",
    "Geliyor mu?",
    "Biliyor muyum?",
    "Onu gördün mü?",
    "Kapıyı kapat.",
]
for giris in DOKUNMA:
    cikti = duzelt(giris)
    if cikti != giris:
        hata.append(f'"{giris}" bozulmamaliydi -> "{cikti}"')

# --- Ses etiketleri ------------------------------------------------
SES = [
    ("[dramatic music playing]", "[gerilim müziği]"),
    # OCR bozmus hali: kapanis parantezi yok, kelime parcalanmis
    ("[dramatic music play ingl", "[gerilim müziği]"),
    ("[groans]", "[inleyerek]"),
    ("[straining]", "[zorlanarak]"),
    ("[door opens]", "[kapı sesi]"),
    ("[gunshot]", "[silah sesi]"),
]
for giris, beklenen in SES:
    cikti = ses_ipucu(giris)
    if cikti != beklenen:
        hata.append(f'ses etiketi "{giris}" -> {cikti} (beklenen: {beklenen})')

# --- Normal cumleler ses etiketi sayilmamali -----------------------
SES_DEGIL = [
    "Why are you here on a Saturday, Lucius?",
    "The music was too loud.",
    "Get down!",
]
for giris in SES_DEGIL:
    if ses_ipucu(giris) is not None:
        hata.append(f'"{giris}" ses etiketi sayildi')

if hata:
    print("Turkce duzeltme testi DUSTU:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print(f"Turkce duzeltme testi temiz: {len(SORU)} soru eki duzeldi, "
      f"{len(DOKUNMA)} dogru cumle korundu, {len(SES)} ses etiketi eslesti.")
