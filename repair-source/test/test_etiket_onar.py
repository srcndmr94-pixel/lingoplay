#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
9.97 — EtiketOnar: bozuk okunan etiket parantezleri. Kalıplar Kotlin
kaynağından okunup GERÇEK ICU motorunda (Android) çalıştırılır.
Ayrıca 51 kaydın tüm OKUNAN satırlarında, kuralın köşeli parantezi olmayan
ya da düzgün kapanmış satırlara dokunmadığı denetlenir.
"""
import glob
import os
import re
import sys

KOK = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, KOK)
import icu_regex  # noqa: E402
import java_regex  # noqa: E402

K = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")
src = open(os.path.join(K, "OfflineFix.kt"), encoding="utf-8").read()
cap = open(os.path.join(K, "CaptureService.kt"), encoding="utf-8").read()
hata = []

ADLAR = ("SUSLU_AC", "BORU_KAPA", "L_KAPA", "ACIK_SES")
a = src.index("object EtiketOnar {")
b = src.index("object KonusanOnEki {")
kod = src[a:b]
KALIP = {ad: java_regex.kalip_cikar(kod, ad) for ad in ADLAR}
YERINE = {"SUSLU_AC": "$1[", "BORU_KAPA": "[$1]", "L_KAPA": "[$1]", "ACIK_SES": "[$1] "}
for ad, yer in YERINE.items():
    kotlin = yer.replace("$", "\\$")
    if ('%s.replace(' % ad) not in kod or ('"%s")' % kotlin) not in kod:
        hata.append("%s yerine koyma metni değişmiş: %s" % (ad, yer))
sira = [kod.find("t = %s.replace" % ad) if ad != "SUSLU_AC" else kod.find("var t = SUSLU_AC.replace") for ad in ADLAR]
if any(x < 0 for x in sira) or sira != sorted(sira):
    hata.append("EtiketOnar kural sırası değişmiş (ayna bayat).")
if "if (s.indexOf('[') < 0 && s.indexOf('{') < 0) return s" not in kod:
    hata.append("hızlı çıkış koşulu değişmiş.")
if "EtiketOnar.onar(OcrOnar.onar(raw))" not in cap:
    hata.append("EtiketOnar clean() zincirine bağlı değil.")


def onar(s):
    if "[" not in s and "{" not in s:
        return s
    for ad in ADLAR:
        s, e = icu_regex.degistir(KALIP[ad], s, YERINE[ad])
        if e:
            raise SystemExit("BAŞARISIZ: %s ICU'da çalışmadı: %s" % (ad, e))
    return s


ORNEK = [
    ("{beeping| -One second.", "[beeping] -One second."),
    ("[hoarselyl Help.", "[hoarsely] Help."),
    ("[hoarselyl Hel-Help!", "[hoarsely] Hel-Help!"),
    ("[recorded Hey, this is Eve. Leave a message,", "[recorded] Hey, this is Eve. Leave a message,"),
    # dokunulmamalı
    ("[the Captain It will take some time to reach our destination,",
     "[the Captain It will take some time to reach our destination,"),
    ("[Nolan] You might want to take a step back.", "[Nolan] You might want to take a step back."),
    ("[suspenseful music playing]", "[suspenseful music playing]"),
    ("[uplifting music playingl", "[uplifting music playingl"),
    ("[doors open", "[doors open"),
    ("[electrical crackling, whooshingl [Tuva screams]", "[electrical crackling, whooshingl [Tuva screams]"),
    ("We need {something} here.", "We need {something} here."),
    ("Help! I'm stuck.", "Help! I'm stuck."),
]
for g, beklenen in ORNEK:
    c = onar(g)
    if c != beklenen:
        hata.append("yanlış: %r\n      beklenen: %r\n      çıkan   : %r" % (g, beklenen, c))

# tüm kayıtlar: düzgün kapanmış ya da parantezsiz satırlara dokunulmamalı
satirlar = set()
for yol in glob.glob("/mnt/user-data/uploads/*.txt") + [os.path.join(KOK, "kayit_satirlari.txt"),
                                                     os.path.join(KOK, "batman_satirlari.txt")]:
    if not os.path.exists(yol):
        continue
    for l in open(yol, encoding="utf-8", errors="replace"):
        l = l.rstrip("\n")
        if l.startswith("OKUNAN: "):
            l = l[8:]
        satirlar.add(l.strip())
degisen = 0
for l in sorted(satirlar):
    if not l:
        continue
    c = onar(l)
    if c == l:
        continue
    degisen += 1
    acik = l.count("[") + l.count("{") > l.count("]") + l.count("}")
    if not acik and c != l.replace("{", "[", 1):
        hata.append("düzgün satır değişti: %r -> %r" % (l, c))
    if c.replace("[", "").replace("]", "").replace("{", "").replace("}", "").replace("|", "").replace(" ", "") \
            != l.replace("[", "").replace("]", "").replace("{", "").replace("}", "").replace("|", "").replace(" ", "") \
            and not re.search(r"\p{L}l\]" if False else r"[a-z]l(?=[ \]])", l):
        hata.append("harf kaybı: %r -> %r" % (l, c))

if hata:
    print("BAŞARISIZ:")
    for h in hata[:30]:
        print("  - " + h)
    sys.exit(1)
print("Etiket onarımı testi geçti (ICU): %d örnek doğru; %d gerçek satırın %d'inde açık etiket kapatıldı, "
      "düzgün satırlara dokunulmadı." % (len(ORNEK), len(satirlar), degisen))
