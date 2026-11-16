"""
"Çöp okuma" süzgeci SAĞLAM satırları atmasın.

Kullanıcının 11 Kasım kaydında oyun ekranında şu yazıyordu:
    Yeah. Go inside and ask for Raymond, bruh.
Kaplamada bir önceki repliğin çevirisi duruyordu. Satır kayıtta ne OKUNAN ne
ÇEVRİLMEDİ olarak görünüyordu, çünkü clean() içinde "çöp okuma" sanılıp
sessizce atılıyordu:
    Yeah ✓  inside ✓  Raymond ✗  bruh ✗   -> 4 kelimenin 2'si bilinmiyor
hunspell listesi özel ad ve sokak ağzı içermiyor. İkisi de muaf tutuldu.

Testin ikinci yarısı süzgecin HÂLÂ çalıştığını doğruluyor: gerçek OCR çöpü
yakalanmaya devam etmeli, yoksa bozuk metinler çevrilip hafızaya giriyor.
"""

import gzip
import os
import re
import sys

KOK = os.path.dirname(__file__)
APP = os.path.join(KOK, "..", "app", "src", "main")
KAYNAK = os.path.join(APP, "java", "com", "example", "altyazi")

ocr = open(os.path.join(KAYNAK, "OcrSozluk.kt"), encoding="utf-8").read()
yakala = open(os.path.join(KAYNAK, "CaptureService.kt"), encoding="utf-8").read()

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


# --- kaynakta duruyor mu
bekle("Slang.SOKAK" in ocr, "argo listesi tek kaynaktan gelmiyor")
bekle("val ARGO = setOf(" not in ocr,
      "OcrSozluk içinde argo listesinin kopyası var: biri güncellenip öteki unutulur")
bekle("private val OZEL_AD" in ocr, "özel ad muafiyeti yok")
bekle("!cumleninIlki && OZEL_AD.matches(w)" in ocr,
      "cümlenin ilk kelimesi de özel ad sayılıyor: OCR çöpü kaçar")
bekle('dustu(collapsed2, "çöp okuma sanıldı")' in yakala,
      "çöp eleme hâlâ sessiz (yalnız tanı modunda yazıyor)")
bekle("prefs.diagnostics) log(\"ÇÖP OKUMA" not in yakala,
      "eski sessiz kayıt satırı duruyor")

# --- gerçek sözlükle mantığın aynısı
SOZ = os.path.join(APP, "res", "raw", "kelimeler.gz")
kelimeler = set()
with gzip.open(SOZ, "rt", encoding="utf-8", errors="ignore") as f:
    for satir in f:
        s = satir.strip().lower()
        if s:
            kelimeler.add(s)
bekle(len(kelimeler) > 100000, f"sözlük küçük: {len(kelimeler)}")

_slang = open(os.path.join(KAYNAK, "Slang.kt"), encoding="utf-8").read()
_argo_blok = re.search(r"val SOKAK: Set<String> = setOf\((.*?)\n\s*\)", _slang, re.S)
bekle(_argo_blok is not None, "Slang.SOKAK listesi bulunamadı")
ARGO = set(re.findall(r'"([^"]+)"', _argo_blok.group(1))) if _argo_blok else set()
bekle("bruh" in ARGO and "tryna" in ARGO, "argo listesi eksik")

AD = re.compile(r"^[A-Z][a-z]+$")
CUMLE_SONU = set(".!?…")


CUMLE_SONU_IM = ".!?…"


def cok_bozuk(metin):
    toplam = bilinen = 0
    cumle_basi = True
    for parca in re.split(r"([^\w'’]+)", metin):
        if not parca:
            continue
        if re.fullmatch(r"[^\w'’]+", parca):
            if any(c in CUMLE_SONU for c in parca):
                cumle_basi = True
            continue
        w = parca.split("'")[0].split("’")[0]
        ilki = cumle_basi
        cumle_basi = False
        if len(w) < 4 or any(ord(c) > 127 for c in w):
            continue
        toplam += 1
        if (w.lower() in kelimeler or w.lower() in ARGO
                or (not ilki and AD.fullmatch(w))):
            bilinen += 1
    if toplam >= 4 and bilinen == 0:
        return True
    # Noktalamayla biten satır gerçek cümledir; eşik yükselir.
    en_az = 6 if metin.rstrip()[-1:] in CUMLE_SONU_IM else 4
    return toplam >= en_az and bilinen * 2 <= toplam


# Sağlam satırlar: ASLA atılmamalı
SAGLAM = [
    "Yeah. Go inside and ask for Raymond, bruh.",
    "Boobie, huh? Yeah, he ain't here, though.",
    "Well, we're picking something up for him.",
    "Yo, what's up? Y'all tryna smoke?",
    "Mu Tianmiao founded the Order to uphold the rules.",
    "Straight up, first door on the right.",
    "I already told that motherfucker what the fuck going on, man.",
    "Yo, Ray! These two say they're for Boobie.",
    "He seriously sent y'all over here right now?",
    # Yabancı dil karışan gerçek replik: çöp sanılıp hiç çevrilmemişti.
    "Oyeme, mi hermano, we got a situation happening.",
    "Raymond, listen very closely, cabrón.",
]
for s in SAGLAM:
    if cok_bozuk(s):
        hata.append(f"sağlam satır çöp sanıldı: {s!r}")

# Gerçek OCR çöpü: yakalanmaya devam etmeli
COP = [
    "Athunib iog hating eases damage dealtte enomies",
    "Now all LCandoe Refollo M hehindwomand he Carefu",
]
for s in COP:
    if not cok_bozuk(s):
        hata.append(f"gerçek çöp artık yakalanmıyor: {s!r}")

if hata:
    print("Çöp okuma testi DÜŞTÜ:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print(f"Çöp okuma testi geçti ({len(SAGLAM)} sağlam satır korundu, "
      f"{len(COP)} çöp yakalandı, {len(ARGO)} argo muaf).")
