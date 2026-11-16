"""
"Altyazı değil" süzgecini, kullanıcının gerçek çalışma kaydından alınmış
satırlara karşı sınar.

Engel listelerini Kotlin kaynağından okuyoruz; böylece liste orada değişince
test de onunla birlikte değişir, ikisi ayrışmaz.
"""

import os
import re
import sys

KAYNAK = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main",
                      "java", "com", "example", "altyazi", "CaptureService.kt")


def liste_cek(metin, ad):
    """Kotlin'deki setOf/listOf blogundan dizeleri cikarir."""
    m = re.search(rf"{ad}\s*=\s*(?:setOf|listOf)\((.*?)\n\s*\)", metin, re.S)
    if not m:
        raise AssertionError(f"{ad} kaynakta bulunamadi")
    return [s for s in re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))]


metin = open(KAYNAK, encoding="utf-8").read()
ENGEL = liste_cek(metin, "BLOCK_PHRASES")
CUMLE = set(liste_cek(metin, "SENTENCE_WORDS"))


def altyazi_degil(text):
    """CaptureService.altyaziDegil ile ayni mantik."""
    kucuk = text.lower()
    for kalip in ENGEL:
        if kalip in kucuk:
            return True

    kelimeler = [k for k in re.split(r"\s+", text) if k]

    sikisik = "".join(kucuk.split())
    if "en•tr" in sikisik or "tr•en" in sikisik:
        return True

    if re.search(r"\.(zip|apk|rar|pdf|mp4|png|jpg)\b", kucuk):
        return True
    if re.search(r"\d+[.,]?\d*\s?(mb|kb|gb)\b", kucuk):
        return True
    for k in kelimeler:
        if len(k) >= 16 and any(c.isdigit() for c in k) and any(
                c.isalpha() for c in k):
            return True

    if len(kelimeler) >= 3:
        kirinti = sum(1 for k in kelimeler if len(k) <= 2)
        if kirinti * 2 >= len(kelimeler):
            return True

    if len(kelimeler) < 5:
        return False

    for k in kelimeler:
        if k.lower().strip(".,!?:;\"'") in CUMLE:
            return False

    if sum(text.count(c) for c in ".!?") >= 2:
        return False

    ozel_ad = 0
    for k in kelimeler:
        if len(k) > 1 and k[0].isupper() and all(
                c.islower() or c in ".-" for c in k[1:]):
            ozel_ad += 1
    return ozel_ad >= 5


# --- Kayittan gelen gercek satirlar -------------------------------------
ENGELLENMELI = [
    "Uygulama içerikleri, güvenlik nedeniyle gizlendi",
    "Uygulama içerikleri, güvenlik nedeniyle gizlendi. Bölümler",
    "Hamish Linklater Bruce Wayne Michelle C. Bonilla Renee Montoya "
    "Krystal Joy Brown",
    "Hamish Linklater: Bruce Wayne Michelle C. Bonilla: Renee Montoya "
    "Alt YazKEystal Joy Brown Dialogue Boost",
    "Hamish Linklater - Batman X-Ray",
    "prime Ekran görüntüsü",
    # Kendi cikti/arayuz kirintilarimiz; buyuyerek geri gelen dongu
    "f h b C V EN• TR",
    "b C V EN• TR",
    "bcventr l b C V EN• TR",
    "S D EHBCVTR• TRBCV TR • TR K b C V EN• TR",
    "d EHBCVTR•TR b C V EN• TR",
    # Telefonun kendi bildirimi / indirme kaydi
    "AQ.Ab8RN6INOtS1mivlvizQ/uBVrUsKOrPM5Fjh Pxsm Bugun altyazi-apk.zip ZIP 51 MB",
    "altyazi-apk.zip ZIP 51 MB • ZIP",
]

GECMELI = [
    "The CEOs approached your company for funding. Rejected.",
    "Why are you here on a Saturday, Lucius?",
    "[straining] Go on! Do it!",
    "I can put my finger on Gregory anytime, anywhere. Bank on it.",
    "Get down!",
    "We need to talk about what happened last night.",
    "You should have told me.",
    "Bruce Wayne. Gotham City. Arkham Asylum.",
]

hata = []

for s in ENGELLENMELI:
    if not altyazi_degil(s):
        hata.append(f"ENGELLENMELIYDI ama gecti: {s[:60]}")

for s in GECMELI:
    if altyazi_degil(s):
        hata.append(f"GERCEK ALTYAZI ama engellendi: {s[:60]}")



# ===================================================================
# En alttaki yazi blogunu secme (CaptureService.enAlttakiKume)
#
# Gercek hata: X-Ray oyuncu listesi ile replik ic ice gecmisti:
#   "There are an awful lot Hamish Linklater - Bruce Wayne of unanswered
#    quest ions."
# Altyazi satirlari birbirine yakin, diger bloklar uzaktir.
# ===================================================================

def en_alttaki_kume(satirlar, serit_yuksekligi):
    if len(satirlar) <= 1:
        return satirlar
    bosluk = max(int(serit_yuksekligi * 0.14), 12)
    sonuc = []
    onceki = satirlar[-1][0]
    for ust, metin in reversed(satirlar):
        if sonuc and onceki - ust > bosluk:
            break
        sonuc.append((ust, metin))
        onceki = ust
        if len(sonuc) >= 3:
            break
    return list(reversed(sonuc))


# Kadro seridi ustte (y=10), replik altta (y=150 ve 180); serit 300 piksel.
kume = en_alttaki_kume(
    [(10, "Hamish Linklater - Bruce Wayne"),
     (150, "There are an awful lot"),
     (180, "of unanswered questions.")], 300)
metinler = [m for _, m in kume]
if "Hamish Linklater - Bruce Wayne" in metinler:
    hata.append("kadro seridi ayiklanmadi: " + str(metinler))
if metinler != ["There are an awful lot", "of unanswered questions."]:
    hata.append("replik satirlari eksik/yanlis: " + str(metinler))

# Tek blok: iki satir da kalmali
kume2 = en_alttaki_kume([(150, "Birinci satir"), (180, "Ikinci satir")], 300)
if len(kume2) != 2:
    hata.append("tek blok bolundu: " + str(kume2))

if hata:
    print("Filtre testi DUSTU:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print(f"Filtre testi temiz: {len(ENGELLENMELI)} cop satiri engellendi, "
      f"{len(GECMELI)} gercek altyazi gecti.")
