"""
Kullanıcının 6 Kasım kaydındaki dört hatayı sınar.

Kayıttan birebir:
  OKUNAN: Osatint / Sprint / Gsprint        -> tuş uyarısı, altyazı sanıldı
  "Pm sorry to say."                        -> "I'm sorry" okunamadı
  "Wetve one more Riddle to solve."         -> "ıslatın" diye çevrildi
  "the Sage"                                -> "Adaçayı" diye çevrildi

Kurallar Kotlin kaynağından okunuyor ki test ile uygulama ayrışmasın.
"""

import os
import re
import sys

KOK = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main",
                   "java", "com", "example", "altyazi")

TURKCE = open(os.path.join(KOK, "TurkishFix.kt"), encoding="utf-8").read()
METIN = open(os.path.join(KOK, "TextFix.kt"), encoding="utf-8").read()
YAKALA = open(os.path.join(KOK, "CaptureService.kt"), encoding="utf-8").read()

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


# --------------------------------------------------------------- 1) isimler

blok = re.search(r"KARSILIKLAR[^=]*=\s*listOf\((.*?)\n\s*\)\n", TURKCE, re.S)
if not blok:
    print("KARSILIKLAR tablosu bulunamadi")
    sys.exit(1)

KARSILIK = {}
for isim, kuyruk in re.findall(r'"(\w+)"\s+to\s+listOf\(([^)]*)\)', blok.group(1)):
    KARSILIK[isim] = re.findall(r'"([^"]+)"', kuyruk)

bekle("Sage" in KARSILIK and "adaçayı" in KARSILIK["Sage"],
      "Sage -> adacayi karsiligi tabloda yok")
bekle("May" in KARSILIK, "ay adlari tablodan dusmus")
bekle(len(KARSILIK) >= 50, f"isim tablosu beklenenden kisa: {len(KARSILIK)}")

# Kotlin'deki EK deseni
ek_m = re.search(r'EK\s*=\s*\n?\s*"([^"]+)"', TURKCE)
bekle(ek_m is not None, "EK deseni bulunamadi")
EK = ek_m.group(1).replace("\\\\", "\\") if ek_m else ""

# Kotlin \p{L} -> Python [^\W\d_]
ON = r"(?<![^\W_])"
ARD = r"(?![^\W_])"


def bas_harf(k):
    if k == "i":
        return "[iIİ]"
    if k == "ı":
        return "[ıI]"
    return "[%s%s]" % (k, k.upper())


def isim_mi(isim, kaynak):
    k = kaynak.strip()
    if isim not in k:
        return False
    orta = re.search(r"(?<![.!?]\s)\b%s\b(?!\s*\d)" % isim, k)
    bas_degil = orta and orta.start() != 0
    etiket = re.match(r"^%s\s*[:,!?.]" % isim, k)
    return bool(bas_degil or etiket)


def koru(kaynak, ceviri):
    t = ceviri
    for isim, karsiliklar in KARSILIK.items():
        if not isim_mi(isim, kaynak):
            continue
        for kar in karsiliklar:
            govde = bas_harf(kar[0]) + re.escape(kar[1:])
            t = re.sub(ON + govde + EK + ARD, isim, t, flags=re.IGNORECASE)
    return t


# kayıttaki asıl vaka
kaynak = "Looks nothing like the Man they called the Sage, I'm sorry to say."
bekle(koru(kaynak, "Adaçayı dedikleri adama hiç benzemiyor.")
      == "Sage dedikleri adama hiç benzemiyor.", "Sage korunmadi (cumle basi)")
bekle(koru(kaynak, "adaçayını aradılar") == "Sage aradılar",
      "Sage cekim ekiyle korunmadi")

# "ı" ile biten karşılıklar: \b ile eşleşmiyordu, asıl hata buydu
bekle(koru("Tell Avci... no, tell Hunter about it.", "avcıya söyle") == "Hunter söyle"
      or "Hunter" in koru("tell Hunter about it", "avcıya söyle"),
      "sonu 'ı' olan karsilik yakalanmadi")

# tarih bozulmasın
bekle(koru("See you May 5.", "5 mayıs görüşürüz") == "5 mayıs görüşürüz",
      "tarih isim sanildi")
# cümle başı: isim sayılmaz
bekle(koru("Rose is a flower.", "Gül bir çiçektir.") == "Gül bir çiçektir.",
      "cumle basindaki kelime isim sanildi")
# adı geçmiyorsa dokunma
bekle(koru("I like flowers.", "Gülleri severim.") == "Gülleri severim.",
      "kaynakta olmayan isim ceviriye sokuldu")


# ------------------------------------------------------------- 2) OCR yazım

bekle("Wetve" in METIN or "t(ve|ll|re|d|m)" in METIN,
      "kesme-isareti-t kurali TextFix'te yok")


def ocr_duzelt(t):
    t = re.sub(r"\b(I|i|We|we|You|you|They|they)t(ve|ll|re|d|m)\b", r"\1'\2", t)
    t = re.sub(r"\b(He|he|She|she|It|it|That|that|There|there|Who|who)t(s|ll|d)\b",
               r"\1'\2", t)
    t = re.sub(r"\b(\w+n)tt\b", r"\1't", t, flags=re.IGNORECASE)
    t = re.sub(r"\b[PF]['’]?(m|ve|ll|d)\b", r"I'\1", t)
    return t


bekle(ocr_duzelt("Pm sorry to say.") == "I'm sorry to say.", "Pm -> I'm olmadi")
bekle(ocr_duzelt("Pve seen it.") == "I've seen it.", "Pve -> I've olmadi")
bekle(ocr_duzelt("Wetve one more Riddle to solve.")
      == "We've one more Riddle to solve.", "Wetve -> We've olmadi")
bekle(ocr_duzelt("theytre here") == "they're here", "theytre -> they're olmadi")
bekle(ocr_duzelt("I cantt do it") == "I can't do it", "cantt -> can't olmadi")
bekle(ocr_duzelt("dontt move") == "don't move", "dontt -> don't olmadi")
# bozulmamasi gerekenler
bekle(ocr_duzelt("Meet me at 5 PM today.") == "Meet me at 5 PM today.",
      "saat PM bozuldu")
bekle(ocr_duzelt("Turn on the FM radio.") == "Turn on the FM radio.",
      "FM bozuldu")
bekle(ocr_duzelt("The network is down.") == "The network is down.",
      "gercek kelime bozuldu")
bekle(ocr_duzelt("We went home.") == "We went home.", "went bozuldu")


# ------------------------------------------------------- 3) arayuz/HUD suzgeci

bekle("arayuzYazisi" in YAKALA, "arayuzYazisi suzgeci CaptureService'te yok")


def arayuz_yazisi(metin):
    s = metin.strip()
    if not s:
        return True
    if s[-1] in ".!?…,:;\"'’":
        return False
    if s[0] in "[(":
        return False
    kelimeler = [k for k in re.split(r"\s+", s) if k]
    if len(kelimeler) <= 2:
        return True
    harfler = [c for c in s if c.isalpha()]
    if len(kelimeler) <= 4 and harfler and all(c.isupper() for c in harfler):
        return True
    return False


# kayıttaki çöp satırlar
for cop in ["Osatint", "Sprint", "Gsprint", "PRESS R2", "HOLD X TO OPEN",
            "A Carry", "Touchpad only", "SARemedies full"]:
    bekle(arayuz_yazisi(cop), f"cop satir gecti: {cop}")

# gerçek altyazılar
gercek = [
    "Is this IT then? The grand Prize your Mentor asked me to see?",
    "Looks nothing like the Man they called the Sage, I'm sorry to say.",
    "Keep your Trousers tied up, we've one more Riddle to solve.",
    "Run!",
    "No.",
    "[kapı sesi]",
    "(whispering)",
    "Watch out behind you.",
    "I don't know",          # noktalamasiz ama cok kelimeli
    "Fetch top dollar, this one.",
]
for satir in gercek:
    bekle(not arayuz_yazisi(satir), f"gercek altyazi elendi: {satir}")


# ------------------------------------------------------ 4) yapisik I (OCR)

bekle("I_FIILLERI" in METIN, "yapisik I kurali TextFix'te yok")

i_blok = re.search(r"I_FIILLERI[^=]*=\s*setOf\((.*?)\n\s*\)", METIN, re.S)
I_FIIL = set(re.findall(r'"([^"]+)"', i_blok.group(1))) if i_blok else set()
bekle(len(I_FIIL) >= 50, f"I fiil listesi kisa: {len(I_FIIL)}")
for yasak in ("and", "or", "in", "is", "it", "ran"):
    bekle(yasak not in I_FIIL, f"tehlikeli kelime listede: {yasak}")

BASTAKI_I = re.compile(r"\b[IlL]([a-z]{2,})\b")


def i_duzelt(t):
    return BASTAKI_I.sub(
        lambda m: ("I " + m.group(1)) if m.group(1).lower() in I_FIIL else m.group(0), t)


bekle(i_duzelt("Iwonder what their Lives are worth to him?")
      == "I wonder what their Lives are worth to him?", "Iwonder duzelmedi")
bekle(i_duzelt("Imay thank you later") == "I may thank you later", "Imay duzelmedi")
bekle(i_duzelt("labsolve you of your Errors") == "I absolve you of your Errors",
      "labsolve duzelmedi")
bekle(i_duzelt("Lpity you Men.") == "I pity you Men.", "Lpity duzelmedi")
for saglam in ["land", "list", "lost", "love", "like", "live", "left", "Iran",
               "Iron", "Italy", "lemon", "laugh", "The land is green"]:
    bekle(i_duzelt(saglam) == saglam, f"bozuldu: {saglam}")


# ------------------------------------------------- 5) YZ kapisi ve denetimi

KORUMA = open(os.path.join(KOK, "AiGuard.kt"), encoding="utf-8").read()
bekle("fun yzGerekli" in KORUMA, "yzGerekli yok")
bekle("fun redSebebi" in KORUMA, "redSebebi yok")
bekle("TERIMLER" in KORUMA, "terim tablosu yok")
# tehlikeli kisa adlar ad listesinde olmamali
ad_blok = re.search(r"ADLAR[^=]*=\s*setOf\((.*?)\n\s*\)", KORUMA, re.S)
# Yorum satirlari ("// Bilerek YOK: \"Ben\"...") listeye karismasin.
ad_govde = re.sub(r"//[^\n]*", "", ad_blok.group(1)) if ad_blok else ""
ADLAR = set(re.findall(r'"([^"]+)"', ad_govde))
for yasak in ("ben", "anne", "jack", "mary", "jamaica", "london"):
    bekle(yasak not in ADLAR, f"riskli ad listede: {yasak}")
bekle("jackdaw" in ADLAR and "kenway" in ADLAR, "oyun adlari eksik")
# CaptureService gercekten denetimi cagiriyor mu
bekle("AiGuard.yzGerekli" in YAKALA, "yzGerekli CaptureService'te cagrilmiyor")
bekle(YAKALA.count("AiGuard.redSebebi") >= 3, "redSebebi tum YZ yollarinda yok")
bekle("YZ ATLANDI" in YAKALA, "YZ atlama kaydi yok")
bekle("YZ REDDEDİLDİ" in YAKALA, "YZ red kaydi yok")


# ------------------------------------------------------------------- sonuc

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print(f"İsim koruma + OCR yazım + arayüz süzgeci + YZ denetimi testi geçti "
      f"({len(KARSILIK)} isim, {len(I_FIIL)} I-fiili, {len(gercek)} gerçek altyazı).")
