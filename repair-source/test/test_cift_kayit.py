#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HARİTALARDA ÇİFT ANAHTAR VAR MI?

Kotlin'de mapOf(...) aynı anahtarı iki kez alırsa SON değer kazanır ve
hiçbir uyarı çıkmaz. Bu sessiz ezme aynı hatayı üç kez yaptırdı:

  * "cover me" = Siper ol      -> doğru "Beni kolla" karşılığını ezmişti
  * "-in' -> -ing" kuralını listede zaten varken bir daha yazmıştım
  * 9.75/9.77 ses sözcüğü eklerken 46 kayıt mevcutları ezdi:
        "splashing": "su sesi" -> "şapırdıyor"   (özgün değer kayboldu)
        "muttering": "söyleniyor" -> "homurdanıyor"
        "scraping" : "sürtünüyor" -> "kazıyor"
        "shattering": "kırılıyor" -> "paramparça oluyor"

Test iki şeyi ayırıyor:
  A) ÇAKIŞMA (aynı anahtar, FARKLI değer) -> HATA. Biri sessizce ölü kod,
     diğeri belki de yanlış olan kazanıyor.
  B) Tam aynı tekrar (aynı anahtar, aynı değer) -> sadece gürültü; eşiği
     aşarsa bildiriliyor ki liste şişmesin.

Eski, bilinen çakışmalar ESKI_CAKISMA'da listeli: bunlar projede benden
önce vardı ve mevcut testler kazanan değere göre yazıldı. Yenisi eklenirse
test düşer.
"""

import os
import re
import sys

KOK = os.path.dirname(os.path.abspath(__file__))
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")

# (dosya, harita bildirimi) çiftleri
HARITALAR = [
    ("OfflineFix.kt", "private val words: Map<String, String> = mapOf("),
    ("OfflineFix.kt", "private val phrases = mapOf("),
    ("OfflineFix.kt", "private val roller: Map<String, String> = mapOf("),
    ("OfflineFix.kt", "private val araclar: Map<String, String> = mapOf("),
    ("OfflineFix.kt", "private val devamAdi: Map<String, String> = mapOf("),
]

# Benden ÖNCE var olan ve mevcut testlerin kazanan değere göre yazıldığı
# çakışmalar. Yeni bir çakışma eklenirse test düşer.
ESKI_CAKISMA = {"intensifies"}

# Tam aynı tekrar için tolerans: liste şişmesin.
AYNI_TEKRAR_SINIRI = 6

hata = []
uyari = []


def harita_ciftleri(kaynak, isaret):
    i = kaynak.index(isaret)
    j = kaynak.index("\n    )", i)
    blok = "\n".join(l for l in kaynak[i:j].splitlines()
                     if not l.strip().startswith("//"))
    return re.findall(r'"((?:[^"\\]|\\.)*)" to "((?:[^"\\]|\\.)*)"', blok)


for dosya, isaret in HARITALAR:
    with open(os.path.join(KAYNAK, dosya), encoding="utf-8") as f:
        kaynak = f.read()
    try:
        ciftler = harita_ciftleri(kaynak, isaret)
    except ValueError:
        hata.append("%s içinde harita bulunamadı: %s" % (dosya, isaret[:46]))
        continue
    if not ciftler:
        hata.append("%s / %s boş okundu — çıkarıcı bozulmuş olabilir."
                    % (dosya, isaret[:40]))
        continue

    d = {}
    for k, v in ciftler:
        d.setdefault(k, []).append(v)

    cakisma = {k: v for k, v in d.items()
               if len(set(v)) > 1 and k not in ESKI_CAKISMA}
    ayni = [k for k, v in d.items() if len(v) > 1 and len(set(v)) == 1]

    ad = isaret.split()[2].rstrip(":")
    if cakisma:
        for k, v in sorted(cakisma.items()):
            hata.append("%s / %s: ÇAKIŞMA %r -> %s · SON kazanır (%r), "
                        "diğeri sessizce ölü." % (dosya, ad, k, v, v[-1]))
    if len(ayni) > AYNI_TEKRAR_SINIRI:
        uyari.append("%s / %s: %d tam aynı tekrar (%s...)"
                     % (dosya, ad, len(ayni), ", ".join(sorted(ayni)[:5])))

# --- Sözlük girdileri (DEFAULT_ENTRIES + GAME_ENTRIES) --------------------
with open(os.path.join(KAYNAK, "OfflineFix.kt"), encoding="utf-8") as f:
    OFF = f.read()

girdiler = {}
for sabit in ("DEFAULT_ENTRIES", "GAME_ENTRIES"):
    i = OFF.index('const val %s = """' % sabit) + len('const val %s = """' % sabit)
    j = OFF.index('"""', i)
    for satir in OFF[i:j].splitlines():
        l = satir.strip()
        if not l or l.startswith("#") or l.startswith("@") or "=" not in l:
            continue
        anahtar, _, deger = l.partition("=")
        # keyOf: harf ve rakam dışındaki her şey atılıyor
        k = re.sub(r"[^0-9a-zçğıöşüA-ZÇĞİÖŞÜ]", "", anahtar).lower()
        girdiler.setdefault(k, []).append((sabit, deger.strip()))

# AYNI ANAHTARA düşen birden fazla girdi: keyOf noktalamayı attığı için
# "hey, guys" ile "hey guys" AYNI anahtardır. Değerleri aynıysa davranış
# bozulmaz ama bir tanesini düzenlemek SESSİZCE hiçbir şey yapmaz —
# son girdi kazanır. (Tam bu yüzden bir negatif kontrolüm boşa gitti.)
sozluk_tekrar = {k: v for k, v in girdiler.items()
                 if len(v) > 1 and len({d for _, d in v}) == 1}
if len(sozluk_tekrar) > 45:
    uyari.append("sözlükte aynı anahtara düşen %d tekrar girdi — birini "
                 "düzenlemek işe yaramaz, son girdi kazanır "
                 "(örn. %s)" % (len(sozluk_tekrar),
                                ", ".join(sorted(sozluk_tekrar)[:3])))

sozluk_cakisma = {k: v for k, v in girdiler.items()
                  if len({d for _, d in v}) > 1}
if sozluk_cakisma:
    for k, v in sorted(sozluk_cakisma.items())[:12]:
        hata.append("sözlük: ÇAKIŞMA %r -> %s · SON kazanır (%r)"
                    % (k, [d for _, d in v], v[-1][1]))

if uyari:
    print("UYARI:")
    for u in uyari:
        print("  ! " + u)

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  - " + h)
    sys.exit(1)

print("Çift kayıt testi geçti: %d harita ve %d sözlük anahtarında çakışma yok."
      % (len(HARITALAR), len(girdiler)))
