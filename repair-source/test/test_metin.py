"""
OCR onarımını kullanıcının gerçek çalışma kaydındaki satırlarla sınar.

Buradaki bozulmaların hepsi gerçekten yaşandı:
  "Right youare!"            -> "Sağ Yout!" diye çevrilmişti
  "foryou as.Repayment."     -> "yararlı yazıcıyı" diye çevrilmişti
  "Rd be everobliged."       -> "Rd everobliged" diye çıkmıştı
  "withye Cap'n"             -> "ve Drake Aparting Cap'n" olmuştu

Kelime havuzunu Kotlin kaynağından okuyoruz; havuz orada değişince test de
onunla değişir, ikisi ayrışmaz.

Testin ikinci yarısı en az birincisi kadar önemli: bölme kuralının GERÇEK
kelimeleri bölmediğini doğruluyor. Fazla cömert bir kural "notice" kelimesini
"no tice" yapar ve çeviriyi iyileştirmek yerine bozar.
"""

import os
import re
import sys

KAYNAK = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main",
                      "java", "com", "example", "altyazi", "TextFix.kt")

metin = open(KAYNAK, encoding="utf-8").read()

blok = re.search(r"WORDS:\s*Set<String>\s*=\s*setOf\((.*?)\n\s*\)", metin, re.S)
if not blok:
    print("WORDS havuzu kaynakta bulunamadi")
    sys.exit(1)
WORDS = {w.lower() for w in re.findall(r'"((?:[^"\\]|\\.)*)"', blok.group(1))}


def bilinen(k):
    return bool(k) and k.lower() in WORDS


def bol(govde):
    for i in range(len(govde) - 2, 1, -1):
        sol, sag = govde[:i], govde[i:]
        if not bilinen(sol):
            continue
        if bilinen(sag) or (len(sag) >= 4 and len(sol) >= 3):
            return sol + " " + sag
    return None


def parcayi_ayir(parca):
    if "'" in parca:
        yarim = parca.split("'")
        if len(yarim) == 2 and bilinen(yarim[0]) and bilinen(yarim[1]):
            return yarim[0] + " " + yarim[1]

    # Kotlin tarafindaki koruma: hic harf yoksa dokunma.
    if not any(c.isalpha() for c in parca):
        return parca

    bas = ""
    for c in parca:
        if c.isalpha():
            break
        bas += c
    son = ""
    for c in reversed(parca):
        if c.isalpha():
            break
        son = c + son

    bas_i, son_i = len(bas), len(parca) - len(son)
    # Kotlin'de substring(bas_i, son_i) bas_i > son_i oldugunda COKER.
    # Python sessizce bos dondurdugu icin ilk seferinde bu hatayi kacirdik;
    # artik burada acikca patliyoruz ki test yakalasin.
    if bas_i > son_i:
        raise AssertionError(
            f"substring araligi gecersiz: begin={bas_i} end={son_i} "
            f'parca="{parca}" — Kotlin burada cokerdi')
    govde = parca[bas_i:son_i]

    if len(govde) < 5 or bilinen(govde):
        return parca
    b = bol(govde)
    return parca if b is None else bas + b + son


def onar(raw):
    t = raw.replace("…", "...")
    t = re.sub(r"\s*\.\s*\.\s*\.", "...", t)
    t = re.sub(r"\s+", " ", t).strip()
    t = t.strip("-—«»\"").strip()
    t = re.sub(r"(?<=[a-z])\.(?=[A-Z])", ". ", t)
    t = " ".join(parcayi_ayir(p) for p in t.split(" "))
    return re.sub(r"\s+", " ", t).strip()


hata = []

# --- Kayittan gelen gercek bozulmalar: duzelmeli --------------------
DUZELMELI = [
    ("Right youare!", "you are"),
    ("'l not play Duck and Drake withye Cap'n,", "with ye"),
    ("I need Coin to build a little Emporium. Rd be everobliged.",
     "ever obliged"),
    ("and a local Shop'would bring in many a usefulWare foryou as.Repayment.",
     "for you"),
    ("and a local Shop'would bring in many a usefulWare foryou as.Repayment.",
     "Shop would"),
    ("and a local Shop'would bring in many a usefulWare foryou as.Repayment.",
     "as. Repayment"),
]

for giris, beklenen in DUZELMELI:
    cikti = onar(giris)
    if beklenen not in cikti:
        hata.append(f'"{beklenen}" bekleniyordu, cikan: "{cikti}"')

# --- Bozulmamasi gereken gercek kelimeler ---------------------------
BOZULMAMALI = [
    "notice", "building", "heading", "playing", "window", "manage",
    "forest", "better", "wonder", "important", "Gregory", "Emporium",
    "another", "without", "something", "anytime", "therefore", "sometimes",
    "everyone", "understand", "tonight", "anywhere", "forever",
]

for kelime in BOZULMAMALI:
    cikti = onar(kelime)
    if cikti != kelime:
        hata.append(f'"{kelime}" bolunmemeliydi, cikan: "{cikti}"')

# --- Harf icermeyen parcalar cokmeye yol acmamali -------------------
# 31.10.2026'da gercek cokme: "begin 1, end 0, length 1"
HARFSIZ = ["-", "...", "•", "!", "?!", "—", ":", "\"", "- ... •", "1 2 3"]

for giris in HARFSIZ:
    try:
        onar(giris)
    except AssertionError as e:
        hata.append(f'"{giris}" cokmeye yol aciyor: {e}')
    except Exception as e:
        hata.append(f'"{giris}" beklenmedik hata: {e}')

# --- Tam cumleler bozulmadan gecmeli --------------------------------
GECMELI = [
    "I can put my finger on Gregory anytime, anywhere.",
    "Calm? How can I stay calm? Thorne can find me here!",
    "We need to talk about what happened last night.",
]

for cumle in GECMELI:
    cikti = onar(cumle)
    if cikti != cumle:
        hata.append(f'cumle degisti:\n    once: {cumle}\n    sonra: {cikti}')


# --- Buyuk I, rakam 1 okunuyor --------------------------------------
#
# Kullanicinin bildirdigi hata: OCR "I"yi tek basina 1 (bir) goruyor.
# Tek harfli oldugu icin sozluklu onarima girmiyordu (orada 2+ harf sarti var).
#
# Testin ikinci yarisi en az birincisi kadar onemli: GERCEK sayilarin
# bozulmadigini dogruluyor. Fazla comert bir kural "Chapter 1"i
# "Chapter I" yapar ve menuleri bozar.

for ad in ("BIR_KESME", "BIR_AM", "BIR_OLUMSUZ", "BIR_KEKEME", "BIR_FIIL"):
    if f"val {ad} = Regex(" not in metin:
        hata.append(f"{ad} kurali kaynakta yok")
if "BIR_FIIL.replace(text)" not in metin:
    hata.append("BIR_FIIL clean() icinde cagrilmiyor")
if '" of "' not in metin:
    hata.append('"1 can of soup" korumasi yok')

_fblok = re.search(r"I_FIILLERI:\s*Set<String>\s*=\s*setOf\((.*?)\n\s*\)", metin, re.S)
if not _fblok:
    hata.append("I_FIILLERI listesi kaynakta bulunamadi")
    I_FIILLERI = set()
else:
    # Yorum satirlarindaki ornek kelimeler listeye karismasin.
    _temiz = re.sub(r"//[^\n]*", "", _fblok.group(1))
    I_FIILLERI = set(re.findall(r'"([^"]+)"', _temiz))

_KESME = re.compile(r"(?<![^\W\d_])(?<!\d)1['’](m|ve|ll|d|re)(?![^\W\d_])")
_AM = re.compile(r"(?<![^\W\d_])(?<!\d)[I1]['’]am(?![^\W\d_])")
_OLUMSUZ = re.compile(r"(?<![^\W\d_])(?<!\d)1(?=\s+[a-z]+n['’]?t(?![^\W\d_]))")
_KEKEME = re.compile(r"(?<![^\W\d_])(?<!\d)1(?=[-–—]\s)")
_FIILD = re.compile(r"(?<![^\W\d_])(?<!\d)1(\s+)([a-z][\w'’]*)(?![^\W\d_])")


def biri_i_yap(t):
    t = _KESME.sub(r"I'\1", t)
    t = _AM.sub("I am", t)
    t = _OLUMSUZ.sub("I", t)
    t = _KEKEME.sub("I", t)
    kaynak = t

    def _f(m):
        k = m.group(2).strip("'’").lower()
        sonra = kaynak[m.end():]
        if k in I_FIILLERI and not sonra.startswith(" of ") and sonra != " of":
            return "I" + m.group(1) + m.group(2)
        return m.group(0)

    return _FIILD.sub(_f, t)


BIR_DUZELMELI = [
    ("1'm sorry to say.", "I'm sorry to say."),
    ("1've seen it before.", "I've seen it before."),
    ("1'll be right back.", "I'll be right back."),
    ("1'd rather not.", "I'd rather not."),
    ("1'am not ready.", "I am not ready."),
    ("I'am not ready.", "I am not ready."),
    ("1 don't know.", "I don't know."),
    ("1 can't do this.", "I can't do this."),
    ("1 didn't mean it.", "I didn't mean it."),
    ("1 wonder what happened.", "I wonder what happened."),
    ("1 remember the camp.", "I remember the camp."),
    ("1 am the one.", "I am the one."),
    ("1 was never there.", "I was never there."),
    ("1 gotta go now.", "I gotta go now."),
    ("1- 1 don't know!", "I- I don't know!"),
    ("Well, 1 think so.", "Well, I think so."),
]
for giris, beklenen in BIR_DUZELMELI:
    cikti = biri_i_yap(giris)
    if cikti != beklenen:
        hata.append(f"1->I duzelmedi: {giris!r} -> {cikti!r} (beklenen {beklenen!r})")

BIR_BOZULMAMALI = [
    "Chapter 1 begins here.",
    "1 of 3 crates found.",
    "Level 1 complete.",
    "You need 1 more key.",
    "1 bottle of rum left.",
    "1-hour timer started.",
    "1-2 players supported.",
    "Only 1 remains.",
    "It costs 1 gold.",
    "1 May 1944.",
    "Press 1 to continue.",
    "1 can of soup is left.",
    "Objective 1 of 2",
]
for giris in BIR_BOZULMAMALI:
    cikti = biri_i_yap(giris)
    if cikti != giris:
        hata.append(f"gercek sayi bozuldu: {giris!r} -> {cikti!r}")

# Telefon ve PC ayni kurallari kullanmali; biri degisirse oteki de degissin.
_PC = os.path.join(os.path.dirname(__file__), "..", "pc", "lingoplay_pc.py")
_pc = open(_PC, encoding="utf-8").read()
if "def biri_i_yap" not in _pc:
    hata.append("PC tarafinda 1->I onarimi yok")
if "biri_i_yap(metin)" not in _pc:
    hata.append("PC tarafinda biri_i_yap metni_onar icinde cagrilmiyor")
_pcf = re.search(r"_I_FIILLERI = \{(.*?)\n\}", _pc, re.S)
if not _pcf:
    hata.append("PC _I_FIILLERI listesi bulunamadi")
elif I_FIILLERI and set(re.findall(r'"([^"]+)"', _pcf.group(1))) != I_FIILLERI:
    _eksik = I_FIILLERI - set(re.findall(r'"([^"]+)"', _pcf.group(1)))
    _fazla = set(re.findall(r'"([^"]+)"', _pcf.group(1))) - I_FIILLERI
    hata.append(f"telefon/PC fiil listeleri ayrismis (eksik={sorted(_eksik)} fazla={sorted(_fazla)})")

if hata:
    print("Metin onarim testi DUSTU:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print(f"Metin onarim testi temiz: {len(DUZELMELI)} bozulma duzeldi, "
      f"{len(BOZULMAMALI)} gercek kelime ve {len(GECMELI)} cumle korundu; "
      f"1->I: {len(BIR_DUZELMELI)} duzeltme, {len(BIR_BOZULMAMALI)} sayi korundu.")
