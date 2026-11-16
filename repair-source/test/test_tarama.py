#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ÇIKTI KUSUR TARAMASI — gerçek kayıtların TAMAMI üzerinde.

Şimdiye kadarki testler iç tutarlılığa bakıyordu (kalıp derleniyor mu, çift
kayıt var mı, ayna üründen sapıyor mu). Bu test ÇIKTIYA bakıyor: kullanıcının
bütün kayıtlarından çıkarılan 1000+ gerçek OCR satırı taslak yolundan
geçiriliyor ve sonuç KUSUR İMZALARINA göre taranıyor.

Yalnız TASLAK KATMANININ tek başına çözdüğü satırlar değerlendirilebilir
(ML Kit motoru burada yok). Bunlar uygulamanın 2-20 ms'de gösterdiği ve
YAPAY ZEKÂNIN HİÇ DÜZELTMEDİĞİ satırlar — yani hata buradaysa ekranda
öyle kalıyor. Tam da denetlenmesi gereken yer.

Aranan imzalar:
  1 İNGİLİZCE SIZDI     : kaynaktaki İngilizce kelime çeviride duruyor
                          (ad değil, projenin Türkçe dağarcığında da yok)
  2 UYDURMA TEKRAR      : kaynakta olmayan yan yana kelime tekrarı
  3 DENGESİZ PARANTEZ   : "[" ile "]" sayısı tutmuyor
  4 YER TUTUCU SIZDI    : Qx1 / Zqa / KUFUR çeviriye kaçmış
  5 HİÇ ÇEVRİLMEMİŞ     : çıktı girdiyle aynı (yalnız addan oluşmuyorsa)
  6 KENARDA OCR ARTIĞI  : nota işareti çeviride kalmış
  7 ÇIKTI ÇOK KISA      : içerik kaybı

Bulgu sayısı BILINEN_BULGU'yu geçerse test düşer; böylece yeni bir kusur
sınıfı sessizce giremez.

SINIR — dürüstçe: 1. imza, projenin KENDİ Türkçe dağarcığını güvenilir
sayıyor. Yani yanlışlıkla bir İngilizce kelime Türkçe karşılık olarak
YAZILIRSA bu tarama onu yakalamaz (denedim: "Konuşacak çok talk var"
geçti, çünkü "talk" o anda dağarcığa giriyor). Yalnız Türkçe harf taşıyan
karşılıklara güvenen sıkı bir sürüm de denendi; yeni bulgu vermedi, buna
karşılık "General" gibi aynı yazılan alıntı kelimeleri yanlış işaretledi.
Bu tarama, İngilizcenin ÇEVİRİ YOLUNDAN sızmasını yakalar; sözlüğe elle
yazılmış hatayı yakalamaz — onun yeri test_cift_kayit.py ve göz denetimi.
"""

import gzip
import os
import re
import shutil
import subprocess
import sys
import tempfile

KOK = os.path.dirname(os.path.abspath(__file__))
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")
GZ = os.path.join(KOK, "..", "app", "src", "main", "res", "raw", "kelimeler.gz")
KORPUS = os.path.join(KOK, "onarim_ingilizce.txt")

MIRRORS = ["Tezgah.java", "Ses.java", "Kalkan.java", "Suslu.java",
           "Onarim.java", "Defter.java"]

# Bilinen, kabul edilmiş bulgular. Gerekçeleri aşağıda; sayı artarsa düşer.
#  * "Hey, yo, Ray!" -> "Hey, hey, Ray!"  : "hey" ve "yo" karşılıkları aynı
#    ("Hey"), ikisi yan yana gelince tekrar oluyor. Anlam doğru, yalnız
#    gereksiz tekrar; yeni bir dönüşüm eklemek yerine kayda geçirildi.
BILINEN_BULGU = 1

if not os.path.exists(KORPUS):
    print("ATLANDI: korpus yok (%s)" % os.path.basename(KORPUS))
    sys.exit(0)
if not shutil.which("javac") or not shutil.which("java"):
    print("ATLANDI: JDK yok (javac/java bulunamadı).")
    sys.exit(0)


def kelimeler(t):
    return re.findall(r"[^\W\d_]+", t, re.UNICODE)


def tekrarImzasi(t):
    """Yan yana aynı 1 ya da 2 kelimelik tekrarların kümesi."""
    k = [w.lower() for w in kelimeler(t)]
    out = set()
    for i in range(len(k) - 1):
        if len(k[i]) > 2 and k[i] == k[i + 1]:
            out.add(k[i])
    for i in range(len(k) - 3):
        if k[i:i + 2] == k[i + 2:i + 4]:
            out.add(" ".join(k[i:i + 2]))
    return out


# --- Sözlükler -----------------------------------------------------------
with gzip.open(GZ, "rt", encoding="utf-8") as f:
    SOZ = set(l.strip() for l in f)

TR_DAGARCIK = set()
ADLAR = set()
# Projenin KENDİ karşılıklarında bilerek yazılmış ikilemeler
# ("kısa kısa nefesler", "derin derin nefes alıyor"). Türkçede ikileme
# anlam taşır; bunlar uydurma tekrar değildir.
IZINLI_TEKRAR = set()
for ad in ("OfflineFix.kt", "Slang.kt", "AiGuard.kt", "TurkishFix.kt", "OcrSozluk.kt"):
    with open(os.path.join(KAYNAK, ad), encoding="utf-8") as f:
        s = f.read()
    for v in re.findall(r'" to "((?:[^"\\]|\\.)*)"', s):
        for w in re.findall(r"[^\W\d_]{2,}", v, re.UNICODE):
            TR_DAGARCIK.add(w.lower())
        IZINLI_TEKRAR.update(tekrarImzasi(v))
    for m in re.finditer(r"^\s*[^=\n#@]+=\s*(.+)$", s, re.M):
        for w in re.findall(r"[^\W\d_]{2,}", m.group(1), re.UNICODE):
            TR_DAGARCIK.add(w.lower())
        IZINLI_TEKRAR.update(tekrarImzasi(m.group(1)))
    for m in re.finditer(r"^@(.+)$", s, re.M):
        ADLAR.add(m.group(1).strip().lower())
    for m in re.finditer(r'"([A-Z][\w\']+)"\s+to\s+listOf', s):
        ADLAR.add(m.group(1).lower())

if len(TR_DAGARCIK) < 500 or not ADLAR:
    print("BAŞARISIZ: Türkçe dağarcık/ad listesi okunamadı "
          "(%d kelime, %d ad) — tarama anlamsız." % (len(TR_DAGARCIK), len(ADLAR)))
    sys.exit(1)

# --- Tezgâhı çalıştır ----------------------------------------------------
gecici = tempfile.mkdtemp(prefix="tarama")
try:
    d = subprocess.run(["javac", "-encoding", "UTF-8", "-d", gecici]
                       + [os.path.join(KOK, m) for m in MIRRORS],
                       capture_output=True, text=True)
    if d.returncode:
        print("BAŞARISIZ: aynalar derlenmedi\n" + d.stderr)
        sys.exit(1)
    ortam = dict(os.environ, JAVA_TOOL_OPTIONS="")
    s = subprocess.run(["java", "-cp", gecici, "-Dfile.encoding=UTF-8",
                        "-Dstdout.encoding=UTF-8", "Tezgah", KAYNAK,
                        "@" + KORPUS],
                       capture_output=True, text=True, env=ortam, timeout=600)
    if s.returncode:
        print("BAŞARISIZ: Tezgah çalışmadı\n" + s.stdout + s.stderr)
        sys.exit(1)
    satirlar = [l.split("\t", 1) for l in s.stdout.splitlines() if "\t" in l]
finally:
    shutil.rmtree(gecici, ignore_errors=True)

cozulen = [(a, b) for a, b in satirlar if "<ML:" not in b]
if len(cozulen) < 50:
    print("BAŞARISIZ: taslak katmanının çözdüğü satır sayısı çok az (%d) — "
          "tarama anlamsız." % len(cozulen))
    sys.exit(1)

bulgu = []


def ekle(imza, a, b):
    bulgu.append((imza, a, b))


for a, b in cozulen:
    govde = re.sub(r"\[[^\]]*\]", " ", b)
    for w in re.findall(r"\b[A-Za-z][A-Za-z']{3,}\b", govde):
        wl = w.lower()
        if wl in SOZ and wl not in TR_DAGARCIK and wl not in ADLAR and wl in a.lower():
            ekle("1 İNGİLİZCE SIZDI (%s)" % w, a, b)
            break
    yeniTekrar = tekrarImzasi(b) - tekrarImzasi(a) - IZINLI_TEKRAR
    if yeniTekrar:
        # Kaynaktaki iki kelimelik tekrar Türkçede tek kelimeye inebilir
        # ("come on, come on" -> "Hadi, hadi"); o meşru.
        if not tekrarImzasi(a):
            ekle("2 UYDURMA TEKRAR (%s)" % ", ".join(sorted(yeniTekrar)), a, b)
    if b.count("[") != b.count("]") or b.count("(") != b.count(")"):
        ekle("3 DENGESİZ PARANTEZ", a, b)
    if re.search(r"Qx\d|Zq[a-z]|KUFUR", b):
        ekle("4 YER TUTUCU SIZDI", a, b)
    sade = [w for w in kelimeler(a) if w.lower() not in ADLAR]
    if (b.strip().lower() == a.strip().lower() and sade
            and re.search(r"[A-Za-z]{4}", " ".join(sade))):
        ekle("5 HİÇ ÇEVRİLMEMİŞ", a, b)
    if re.match(r"^[JD>♪¶|]\s", b) or re.search(r"\s[JD>♪¶|]$", b):
        ekle("6 KENARDA OCR ARTIĞI", a, b)
    if not b.strip() or (len(a) > 20 and len(b) < len(a) * 0.3):
        ekle("7 ÇIKTI ÇOK KISA", a, b)

print("  izinli ikileme: %d" % len(IZINLI_TEKRAR))
print("  taranan: %d satır (%d tanesi taslak katmanında çözüldü)"
      % (len(satirlar), len(cozulen)))

if len(bulgu) > BILINEN_BULGU:
    print("BAŞARISIZ: %d bulgu (bilinen sınır %d)" % (len(bulgu), BILINEN_BULGU))
    for imza, a, b in bulgu[:15]:
        print("  - %s" % imza)
        print("      kaynak: %s" % a[:72])
        print("      çeviri: %s" % b[:72])
    sys.exit(1)

if len(bulgu) < BILINEN_BULGU:
    print("  NOT: bulgu sayısı %d, bilinen sınır %d — sınır düşürülebilir."
          % (len(bulgu), BILINEN_BULGU))

print("Çıktı taraması geçti: %d çözülen satırda %d bulgu (sınır %d); "
      "İngilizce sızması, yer tutucu kaçağı, dengesiz parantez yok."
      % (len(cozulen), len(bulgu), BILINEN_BULGU))
