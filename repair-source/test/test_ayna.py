#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AYNA ÜRÜNLE AYNI DAVRANIYOR MU?

Tezgah.java bir ÖLÇÜ ALETİ: taslak yolunu Java'da yeniden kurup Kotlin
kaynağından veriyi okuyor. Alet üründen saparsa ölçüm yalan söyler — ve
11 Kasım'da iki kez söyledi:

  1. Ayna bütün argo kurallarına zorla "(?i)" ekliyor ama eşleşme büyük
     harfle başladığında karşılığın ilk harfini BÜYÜTMÜYORDU. Ürün
     büyütüyor. Ayna "Nothin." -> "nothing." gösteriyordu, ürün
     "Nothing." üretiyor.
  2. Bunu düzeltirken "(?i)"yi kaldırdım ve ayna TERS yöne saptı:
     kurallar büyük harfli cümle başında çalışmıyor sandım, "78 kural
     bozuk" diye yanlış sonuç çıkardım. Oysa ürün iki listeyi de
     IGNORE_CASE ile derliyor.

Bu test iki tarafın şu iki özellikte örtüştüğünü doğruluyor:
  A) kurallar BÜYÜK/küçük harf duyarsız derleniyor,
  B) eşleşme büyük harfle başlıyorsa karşılığın ilk harfi büyütülüyor.
"""

import os
import re
import sys

KOK = os.path.dirname(os.path.abspath(__file__))
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")

hata = []


def oku(ad):
    with open(os.path.join(KAYNAK, ad), encoding="utf-8") as f:
        return f.read()


OFF = oku("OfflineFix.kt")
SLANG = oku("Slang.kt")
with open(os.path.join(KOK, "Tezgah.java"), encoding="utf-8") as f:
    TEZGAH = f.read()

# --- A) ÜRÜN: iki kural listesi de harf duyarsız mı? -----------------------
if "RegexOption.IGNORE_CASE) to it.second }" not in OFF:
    hata.append("SlangNormalizer.rules artık IGNORE_CASE ile derlenmiyor — "
                "cümle başındaki büyük harfli kalıplar kaçar.")
if 'Regex("(?i)" + it.first) to it.second }' not in SLANG:
    hata.append("Slang.rewrites artık (?i) ile derlenmiyor.")

# --- B) ÜRÜN: iki normalize() de ilk harfi geri koyuyor mu? ---------------
HARF_GERI = "isUpperCase() && r.isNotEmpty()) r.substring(0, 1).uppercase()"
if OFF.count(HARF_GERI) < 1:
    hata.append("SlangNormalizer.normalize ilk harfi geri koymuyor.")
if SLANG.count(HARF_GERI) < 1:
    hata.append("Slang.normalize ilk harfi geri koymuyor.")

# --- AYNA: aynı iki özelliği taşıyor mu? ----------------------------------
i = TEZGAH.find("static String uygula(String t, List<String[]> kurallar) {")
if i < 0:
    hata.append("Tezgah.uygula bulunamadı — ayna yapısı değişmiş.")
else:
    govde = TEZGAH[i:i + 1400]
    if 'Pattern.compile("(?i)" + r[0])' not in govde:
        hata.append("AYNA SAPMASI: Tezgah.uygula kuralları (?i) ile derlemiyor; "
                    "ürün derliyor. Ölçüm olduğundan KÖTÜ görünür.")
    if "Character.toUpperCase(rep.charAt(0))" not in govde:
        hata.append("AYNA SAPMASI: Tezgah.uygula ilk harfi geri koymuyor; "
                    "ürün koyuyor. Ölçüm olduğundan KÖTÜ görünür.")
    if "m.group(g) == null" not in govde:
        hata.append("AYNA SAPMASI: Tezgah.uygula boş yakalama grubunu ele almıyor.")

# --- Kural listesinde ÇİFT kayıt var mı? ----------------------------------
# Aynı kalıbı iki kez yazmak sessiz çakışmaya yol açıyor: 9.77'de
# "-in' -> -ing" kuralını listede zaten varken bir daha eklemişim.
i = OFF.index("object SlangNormalizer {")
j = OFF.index("fun normalize(text: String): String {", i)
blok = "\n".join(l for l in OFF[i:j].splitlines() if not l.strip().startswith("//"))
kalipLar = [k for k, _ in re.findall(r'"((?:[^"\\]|\\.)*)" to "((?:[^"\\]|\\.)*)"', blok)]
tekrar = sorted({k for k in kalipLar if kalipLar.count(k) > 1})
if tekrar:
    hata.append("SlangNormalizer'da ÇİFT kalıp: " + ", ".join(tekrar[:5]))

# Aynı sonu paylaşan neredeyse-aynı kalıpları da bildir (sessiz çakışma).
cekirdek = {}
for k in kalipLar:
    c = k.replace("(?i)", "").replace("'", "").replace("’", "")
    c = re.sub(r"\[[^\]]*\]", "[]", c)
    cekirdek.setdefault(c, []).append(k)
yakin = {c: v for c, v in cekirdek.items() if len(v) > 1}
if yakin:
    ilk = list(yakin.values())[0]
    hata.append("SlangNormalizer'da neredeyse AYNI kalıp çifti (sessiz çakışma): "
                + " || ".join(ilk[:2]))

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  - " + h)
    sys.exit(1)

print("Ayna testi geçti: ürün ve tezgâh aynı harf davranışını paylaşıyor, "
      "%d argo kalıbında çift kayıt yok." % len(kalipLar))
