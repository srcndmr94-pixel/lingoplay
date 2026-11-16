#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ŞABLONLU KALIPLAR ÇALIŞMA ANINDA DERLENİYOR MU?

Kaynaktaki bazı Regex'ler Kotlin şablonuyla kuruluyor:
    Regex("(?i)^(?:${it.first})\\b")
    Regex("(?i)\\b${it.first}\\b")
    Regex("(?<![\\p{L}\\p{N}])$g(?![\\p{L}\\p{N}])")
Son hâlleri ancak çalışma anında belli olur, bu yüzden test_kalip.py
onları ATLIYOR — 11 kalıp denetimsiz kalıyordu. Bozuk bir regex Kotlin'de
DERLEME hatası değildir: ilk kullanımda PatternSyntaxException atar, yani
uygulama altyazı çevirirken çöker.

Bu test besleyen listeleri kaynaktan okuyup şablonu DOLDURUYOR ve her
birleşimi java.util.regex ile derliyor (Kotlin'in gerçek motoru).

Ayrıca listelere regex metakarakteri sızıp sızmadığına bakıyor: kaçışsız
gömülen bir "." her harfi tutar, kaçışsız bir "(" kalıbı hiç derletmez.
"""

import os
import re
import sys

KOK = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, KOK)
import java_regex  # noqa: E402

KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")


def oku(ad):
    with open(os.path.join(KAYNAK, ad), encoding="utf-8") as f:
        return f.read()


def blok(kaynak, isaret, bitis=None):
    i = kaynak.index(isaret)
    j = kaynak.index(bitis, i) if bitis else kaynak.index("\n    )", i)
    return "\n".join(l for l in kaynak[i:j].splitlines()
                     if not l.strip().startswith("//"))


def ciftler(govde):
    return re.findall(r'"((?:[^"\\]|\\.)*)"\s+to\s+"((?:[^"\\]|\\.)*)"', govde)


def tekler(govde):
    return re.findall(r'"((?:[^"\\]|\\.)*)"', govde)


def coz(h):
    k = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "\\": "\\", "$": "$"}
    return re.sub(r"\\(.)", lambda m: k.get(m.group(1), "\\" + m.group(1)), h)


AG = oku("AiGuard.kt")
OS_ = oku("OcrSozluk.kt")
OF = oku("OfflineFix.kt")
TX = oku("TextFix.kt")
TF = oku("TurkishFix.kt")

istek = []   # (etiket, kalip)
hata = []

# --- 1) AiGuard.HITAP_BAS: "(?i)^(?:${it.first})\b" ----------------------
try:
    g = blok(AG, "private val HITAP_BAS", ".map {")
    for ilk, _ in ciftler(g):
        istek.append(("HITAP_BAS[%s]" % ilk[:24], "(?i)^(?:%s)\\b" % coz(ilk)))
except ValueError:
    hata.append("HITAP_BAS bulunamadı")

# --- 1b) AiGuard.KORUNAN / YASAK: "(?i)" + it.first ---------------------
# Bu iki liste test_kalip.py'de ŞABLONLU diye atlanıyordu ve hiçbir test
# kapsamıyordu: 2 liste, onlarca kalıp denetimsizdi.
for ad in ("KORUNAN", "YASAK"):
    try:
        g = blok(AG, "private val %s" % ad, ".map {")
        bulunan = ciftler(g)
        if not bulunan:
            hata.append("%s listesi boş okundu" % ad)
        for ilk, _ in bulunan:
            istek.append(("%s[%s]" % (ad, ilk[:24]), "(?i)%s" % coz(ilk)))
    except ValueError:
        hata.append("%s bulunamadı" % ad)

# --- 2) OcrSozluk.KESME_BELIRSIZ: were + ardıl ---------------------------
try:
    ard = tekler(blok(OS_, "private val KESME_ARDIL = listOf(", "\n    )"))
    for a in ard:
        istek.append(("KESME_BELIRSIZ[%s]" % a,
                      "(?i)(^|[.!?…\"'\\[\\]()–—-]\\s*)(were)(\\s+%s\\b)" % a))
    if not ard:
        hata.append("KESME_ARDIL boş okundu")
except ValueError:
    hata.append("KESME_ARDIL bulunamadı")

# --- 3) OcrSozluk.KESME_KESIN: theyre/youre + ardıl ---------------------
try:
    g = blok(OS_, "private val KESME_KESIN", "fun kesmeyiGeriKoy")
    for ham, _ in ciftler(g):
        for a in ard:
            istek.append(("KESME_KESIN[%s+%s]" % (ham, a),
                          "(?i)(?<![\\p{L}'’])(%s)(\\s+%s\\b)" % (ham, a)))
except ValueError:
    hata.append("KESME_KESIN bulunamadı")

# --- 4) ProfanityShield: rule("...") -> "(?i)$p" -------------------------
try:
    i = OF.index("object ProfanityShield {")
    j = OF.index("fun shield(", i)
    govde = "\n".join(l for l in OF[i:j].splitlines()
                      if not l.strip().startswith("//"))
    kurallar = re.findall(r'rule\(\s*"((?:[^"\\]|\\.)*)"', govde)
    for p in kurallar:
        istek.append(("rule[%s]" % p[:28], "(?i)%s" % coz(p)))
    if not kurallar:
        hata.append("ProfanityShield rule(...) bulunamadı")
except ValueError:
    hata.append("ProfanityShield bulunamadı")

# --- 5) ProfanityShield kelime haritası: "(?i)\b${it.first}\b" ----------
try:
    match = re.search(r'\.map\s*\{\s*Regex\("[^"\n]*\$\{it\.first\}[^"\n]*"(?:,\s*RegexOption\.IGNORE_CASE)?\)\s+to\s+it\.second\s*\}', OF)
    if not match:
        raise ValueError("küfür kelime haritası bağlanmıyor")
    i = match.start()
    bas = OF.rindex("= listOf(", 0, i)
    govde = "\n".join(l for l in OF[bas:i].splitlines()
                      if not l.strip().startswith("//"))
    kelimeler = ciftler(govde)
    for k, _ in kelimeler:
        istek.append(("kufur[%s]" % k[:28], "(?i)\\b%s\\b" % coz(k)))
    if not kelimeler:
        hata.append("küfür kelime haritası boş okundu")
except ValueError:
    hata.append("küfür kelime haritası bulunamadı")

# --- 6) TextFix yazım düzeltmeleri: "(?i)\b$wrong\b" --------------------
try:
    i = TX.index('"(?i)\\\\b$wrong\\\\b"') if '"(?i)\\\\b$wrong\\\\b"' in TX \
        else TX.index('\\b$wrong\\b')
    # Besleyen harita: "wrong" anahtarları
    m = re.search(r'(?:val|private val)\s+(\w+)[^\n]*=\s*mapOf\(', TX)
    if m:
        govde = blok(TX, m.group(0), "\n    )")
        for w, _ in ciftler(govde):
            istek.append(("yazim[%s]" % w[:24], "(?i)\\b%s\\b" % coz(w)))
except ValueError:
    pass   # TextFix'te şablon yoksa sorun değil

# --- 7) TurkishFix: govde ve ad denetimi --------------------------------
try:
    i = TF.index("KARSILIKLAR")
    j = TF.index("private const val ON", i)
    govde = "\n".join(l for l in TF[i:j].splitlines()
                      if not l.strip().startswith("//"))
    adlar = re.findall(r'"((?:[^"\\]|\\.)*)"\s+to\s+listOf', govde)
    EK = None
    me = re.search(r'private const val EK\s*=\s*"((?:[^"\\]|\\.)*)"', TF)
    if me:
        EK = coz(me.group(1))
    ON = "(?<![\\p{L}\\p{N}])"
    ARD = "(?![\\p{L}\\p{N}])"
    for isim in adlar:
        g = re.escape(isim)
        istek.append(("AD_DENETIMI[%s]" % isim,
                      "(?<![.!?]\\s|^)%s%s%s(?!\\s*\\d)" % (ON, g, ARD)))
        istek.append(("AD_BASI[%s]" % isim, "^%s\\s*[:,!?.]" % g))
    # karşılıkların gövde deseni
    karsilik = re.findall(r'listOf\(((?:\s*"(?:[^"\\]|\\.)*"\s*,?)+)\)', govde)
    if EK:
        for grup in karsilik[:60]:
            for k in tekler(grup):
                if not k:
                    continue
                ilk = k[0]
                bas = {"i": "[iIİ]", "ı": "[ıI]"}.get(ilk, "[%s%s]" % (ilk, ilk.upper()))
                istek.append(("KARSILIK[%s]" % k[:20],
                              "%s%s%s%s%s" % (ON, bas, re.escape(k[1:]), EK, ARD)))
    if not adlar:
        hata.append("KARSILIKLAR adları boş okundu")
except ValueError as e:
    hata.append("TurkishFix listeleri okunamadı: %s" % e)

# --- Metakarakter sızması -----------------------------------------------
META = set("\\^$.|?*+()[]{}")
try:
    for isim in adlar:
        if any(c in META for c in isim):
            hata.append("KARSILIKLAR adı regex metakarakteri içeriyor: %r — "
                        "kalıba gömülmeden önce Regex.escape şart." % isim)
except NameError:
    pass

# --- Hepsini JAVA'da derle ----------------------------------------------
if not istek:
    print("BAŞARISIZ: hiç şablonlu kalıp kurulamadı — çıkarıcı bozulmuş.")
    sys.exit(1)

try:
    sonuc = java_regex.esle([(p, "deneme metni") for _, p in istek])
except java_regex.JavaYok:
    print("ATLANDI: JDK yok, şablonlu kalıplar derlenemedi.")
    sys.exit(0)

for (etiket, pat), (_, _, h) in zip(istek, sonuc):
    if h:
        hata.append("DERLENMEDİ %s: %s\n       kalıp: %s" % (etiket, h, pat[:110]))

if hata:
    print("BAŞARISIZ:")
    for x in hata:
        print("  - " + x)
    sys.exit(1)

print("Şablon testi geçti [" + java_regex.MOTOR + "]: %d çalışma anı kalıbı derlendi, "
      "listelerde kaçışsız metakarakter yok." % len(istek))
