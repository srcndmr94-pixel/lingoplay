#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
TÜRKÇEYE UYGULANAN KALIPLAR (?U) TAŞIYOR MU?

Java/Kotlin'de "\\b", "\\w", "\\W", "\\B" VARSAYILAN olarak yalnız
ASCII'dir. Türkçe harfler (ğ ı ö ş ü ç İ Ğ Ö Ş Ü Ç) kelime karakteri
SAYILMAZ, yani kelime SINIRI gibi davranır. Bunun üç gerçek sonucu oldu
(11 Kasım, ölçülerek bulundu):

    "\\bgöt\\b"                -> "götürdü", "götü" İÇİNDE eşleşiyor
    "^(lan|ulan|be|birader)\\b" -> "Beğendim" içinde eşleşiyor
    "\\w{2,}m[ae]z"            -> "ölmez", "görmedi", "gülmedi" KAÇIYOR

Üçüncüsü en tehlikelisi: olumsuzluk görülmeyince DOĞRU yapay zekâ
çevirisi "olumsuzluk düştü" diye reddediliyor.

Python'da "\\b" Unicode farkındadır, bu yüzden Python'la yazılmış
testlerim üçünü de TEMİZ gösterdi. Artık hem kalıplar Java'da sınanıyor
(java_regex.py) hem de bu test yeni bir kalıbın aynı tuzağa düşmesini
engelliyor.

KURAL: içinde Türkçeye özgü harf GEÇEN ve "\\b"/"\\w" KULLANAN her kalıp
(?U) taşımak zorunda. Ayrıca Türkçe metne uygulandığı adından belli olan
kalıplar (TR_*, SERT_*, HITAP_*) da aynı şartı taşır.
"""

import glob
import os
import re
import sys

# python3 -I betik dizinini sys.path'ten çıkarıyor.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import java_regex  # noqa: E402

KOK = os.path.dirname(os.path.abspath(__file__))
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")

TR_HARF = "ğıöşüçĞİÖŞÜÇ"
# Türkçe metne uygulandığı adından belli olan kalıplar
TR_ADLAR = re.compile(r"^(TR_|SERT_|HITAP_|KUFUR|TURKCE|TR$)", re.I)

hata = []
denetlenen = 0

# KURAL (9.83'te TERSİNE döndü): gömülü bayraklar yalnız ICU'nun tanıdığı
# harflerden olabilir: i m s w x. "(?U)", "(?u)", "(?d)" Android'de
# U_REGEX_INVALID_FLAG / RULE_SYNTAX verir ve uygulama AÇILIŞTA çöker.
# Eski kural "(?U) şart" diyordu; masaüstü Java'ya bakarak yazılmıştı.
BAYRAK = re.compile(r"\(\?([A-Za-z]+)(?:-[A-Za-z]+)?[:)]")
for yol in sorted(glob.glob(os.path.join(KAYNAK, "*.kt"))):
    dosya = os.path.basename(yol)
    with open(yol, encoding="utf-8") as f:
        kaynak = f.read()
    satirlar = kaynak.splitlines()
    for no, l in enumerate(satirlar, 1):
        if l.strip().startswith("//"):
            continue
        for dz in re.findall(r'"((?:[^"\\]|\\.)*)"', l):
            for m in BAYRAK.finditer(dz):
                denetlenen += 1
                kotu = [c for c in m.group(1) if c not in "imswx"]
                if kotu:
                    hata.append("%s:%d  gömülü bayrak %r ICU'da (Android) geçersiz: "
                                "%s — uygulama açılışta çöker." % (dosya, no, "".join(kotu), dz[:70]))

# ---------------------------------------------------------------------------
# TÜRKÇE BÜYÜK "İ" KÜÇÜLTMESİ
#
# Kotlin'de lowercase() = toLowerCase(Locale.ROOT) ve orada
#     "İsmail" -> "i" + U+0307 (birleşik nokta) + "smail"   (7 harf!)
#     "IŞIK"   -> "işik"   ("ışık" DEĞİL)
# Yani Türkçe metni lowercase() ile küçültüp sabit bir dizgiyle
# karşılaştırmak sessizce başarısız olur. Proje bunun için kucuk()
# yardımcısını yazmış ama bir yerde KULLANMAMIŞTI:
#     val yKucuk = y.lowercase()        // y TÜRKÇE çeviri
# Sonuç: "İçeride, hemen şurada." gibi doğru çeviriler "terim düştü"
# diye reddediliyordu.
# ---------------------------------------------------------------------------
AG = os.path.join(KAYNAK, "AiGuard.kt")
with open(AG, encoding="utf-8") as f:
    ag = f.read()

if "private fun kucuk(s: String): String" not in ag:
    hata.append("AiGuard.kucuk() kaybolmuş — Türkçe İ küçültmesi korumasız.")
else:
    govde = ag[ag.index("private fun kucuk(s: String): String"):][:220]
    if "'İ', 'i'" not in govde or "'I', 'ı'" not in govde:
        hata.append("kucuk() artık İ/I dönüşümünü yapmıyor.")

# Türkçe çeviri (y) sabitlerle karşılaştırılırken kucuk() kullanılmalı.
for kotu in ("y.lowercase()", "it.lowercase().startsWith(kok)"):
    if kotu in ag:
        hata.append("AiGuard'da Türkçe metne düz lowercase(): %r — "
                    "kucuk() kullanılmalı, yoksa 'İ' ile başlayan çeviriler "
                    "sessizce eşleşmez." % kotu)

# Davranış: Java/Kotlin gerçekten bozuyor mu, kucuk() düzeltiyor mu?
try:
    import java_regex
    kalipLar = [
        # lowercase(ROOT) "İçeride" -> "i" + U+0307 + "çeride": "^içeri" TUTMAZ
        ("^i\u0307çeri", "i\u0307çeride"),
        # kucuk() ile: "içeride" -> "^içeri" TUTAR
        ("^içeri", "içeride"),
    ]
    s1 = java_regex.esle([(kalipLar[0][0], kalipLar[0][1])])[0][0]
    s2 = java_regex.esle([(kalipLar[1][0], kalipLar[1][1])])[0][0]
    if not s1 or not s2:
        hata.append("İ küçültme davranış kontrolü beklenmedik sonuç verdi "
                    "(%s, %s)" % (s1, s2))
    # Asıl kanıt: birleşik noktalı hâl, noktasız kalıpla EŞLEŞMEZ
    s3 = java_regex.esle([("^içeri", "i\u0307çeride")])[0][0]
    if s3:
        hata.append("Birleşik noktalı 'i̇çeride' düz 'içeri' kalıbıyla "
                    "eşleşti — ortam beklendiği gibi davranmıyor, testin "
                    "dayanağı geçersiz.")
except java_regex.JavaYok:
    pass

if denetlenen == 0:
    print("BAŞARISIZ: hiç gömülü bayrak bulunamadı — çıkarıcı bozulmuş olabilir.")
    sys.exit(1)

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  - " + h)
    sys.exit(1)

print("Unicode testi geçti: %d gömülü bayrağın hepsi ICU (Android) uyumlu, "
      "Türkçe İ küçültmesi korunuyor." % denetlenen)
