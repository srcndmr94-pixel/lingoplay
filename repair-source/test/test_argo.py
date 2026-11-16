"""
Sokak argosu zinciri: Slang.rewrites + SlangNormalizer.rules.

İki normalleştirici var ve arka arkaya çalışıyor (OfflineFix.mlTranslate):
    SlangNormalizer.normalize(Slang.normalize(text))

GÖREV AYRIMI — bu testin asıl koruduğu şey:
  * Slang.rewrites      -> KELİME DAĞARCIĞI (suç/sokak sözcüğü -> sade İngilizce)
  * SlangNormalizer     -> DİLBİLGİSİ (ain't, gonna, y'all, düşen is/are...)

Kopya kural yazmak sessizce hata üretiyor, çünkü Slang ÖNCE çalışıp
SlangNormalizer'ın daha akıllı kuralını eziyor. 11 Kasım'da tam bu oldu:

    "You ain't ask yourselves that?"
        yalnız SlangNormalizer -> "You didn't ask yourselves that?"   doğru
        Slang'de kopya kural   -> "You are not ask yourselves that?"  bozuk
    "Whatcha doing?"
        yalnız SlangNormalizer -> "what are you doing?"               doğru
        Slang'de kopya kural   -> "what do you doing?"                bozuk

Testin son bölümü bu kopyayı bir daha yazılamaz hâle getiriyor.
"""

import os
import re
import sys
import warnings

warnings.filterwarnings("ignore", category=FutureWarning)

KOK = os.path.dirname(__file__)
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")

slang = open(os.path.join(KAYNAK, "Slang.kt"), encoding="utf-8").read()
offline = open(os.path.join(KAYNAK, "OfflineFix.kt"), encoding="utf-8").read()

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


def java2py(d):
    """Java karakter sınıflarını Python'un anladığı hâle çevirir."""
    return (d.replace("[\\p{L}']*", "[^\\W\\d_']*")
             .replace("\\p{Lu}", "[A-ZÇĞİÖŞÜ]")
             .replace("\\p{Ll}", "[a-zçğıöşü]")
             .replace("\\p{L}", "[^\\W\\d_]"))


def kurallar(metin, desen):
    blok = re.search(desen, metin, re.S)
    if not blok:
        return None
    return [(java2py(d.replace("\\\\", "\\")), k)
            for d, k in re.findall(r'"((?:[^"\\]|\\.)*)" to "((?:[^"\\]|\\.)*)"', blok.group(1))]


SLANG = kurallar(slang, r"rewrites: List<Pair<Regex, String>> = listOf\((.*?)\n\s*\)\.map")
NORM = kurallar(offline, r"object SlangNormalizer \{\s*private val rules[^=]*= listOf\((.*?)\n\s*\)\.map")
bekle(SLANG is not None, "Slang.rewrites okunamadı")
bekle(NORM is not None, "SlangNormalizer.rules okunamadı")
SLANG = SLANG or []
NORM = NORM or []


def uygula(t, kural_listesi):
    for desen, karsilik in kural_listesi:
        def _f(m, k=karsilik):
            r = k
            for g, v in enumerate(m.groups(), 1):
                if v is not None:
                    r = r.replace(f"${g}", v)
            return r
        try:
            t = re.sub(desen, _f, t, flags=re.IGNORECASE)
        except re.error:
            pass          # Python'un desteklemediği Java deseni: atla
    return t


def zincir(t):
    """Gerçek çağrı sırası: Slang önce, SlangNormalizer sonra."""
    return uygula(uygula(t, SLANG), NORM)


# ------------------------------------------------- 1) dönüşümler çalışıyor mu

DUZELMELI = [
    # Kopya silinince DÜZELEN satırlar
    ("You ain't ask yourselves that?", "You didn't ask yourselves that?"),
    ("Whatcha doing?", "what are you doing?"),
    ("Why he ain't come to collect?", "Why he didn't come to collect?"),
    # Sadeleştirme sırasında "ya'll" yazımı kaybolmuş, kısaltma kuralına
    # takılıp "you'll" oluyordu. Üç yazım da aynı kuralda.
    #
    # KARŞILIK "you all" DEĞİL, "you": çeviri motoru "you all" görünce
    # "Hepiniz" diyor, fiili tekil bırakıyor ve cümle bozuluyordu
    # ("Hepiniz Boobie'yi biliyor musun?"). Türkçede çokluk fiil ekinde
    # taşınır. Yapay zekâ yolunda çokluk Slang.hints ipucuyla korunuyor.
    ("Yo. Ya'll need something?", "hey. you need something?"),
    ("Yall need something?", "you need something?"),
    # Kullanıcının kaydındaki replikler
    ("Yo, what's up? Y'all tryna smoke?", "hey, what's up? you looking to buy weed?"),
    ("No. Y'all know Boobie?", "No. you know Boobie?"),
    ("Nah. Whatchu want?", "no. what do you want?"),
    ("Boobie, huh? Yeah, he ain't here, though.", "Boobie, huh? Yeah, he isn't here, though."),
    ("I ain't going.", "I'm not going."),
    ("They ain't here.", "They aren't here."),
    ("I'm gonna go.", "I'm going to go."),
    ("We gotta get out of this.", "We have to get out of this."),
    # "has gotta" tek kuralla "has have to" oluyordu
    ("This has gotta be the door, right?", "This has got to be the door, right?"),
    ("I've gotta go.", "I've got to go."),
    ("Yo, is Raymond around?", "hey, is Raymond here?"),
    ("Yeah. Go inside and ask for Raymond, bruh.",
     "Yeah. Go inside and ask to see Raymond, bruh."),
    ("Like, might have to fuck around.", "I mean, might have to fuck around."),
]
for giris, beklenen in DUZELMELI:
    cikti = zincir(giris)
    if cikti != beklenen:
        hata.append(f"argo düzelmedi: {giris!r} -> {cikti!r} (beklenen {beklenen!r})")

BOZULMAMALI = [
    "The wall is tall.",
    "I want to go home.",
    "The park is just around the corner.",
    "Smoke rose from the chimney.",
]
for giris in BOZULMAMALI:
    cikti = zincir(giris)
    if cikti != giris:
        hata.append(f"gerçek cümle bozuldu: {giris!r} -> {cikti!r}")


# ------------------------------------- 2) KOPYA YASAĞI (asıl koruma)
#
# Dilbilgisi kelimesi iki listede birden geçmemeli. Geçerse Slang önce
# çalışıp SlangNormalizer'ın kuralını eziyor ve sessizce bozuyor.

DILBILGISI = ["ain't", "gonna", "wanna", "gotta", "hafta", "y'all", "yall",
              "tryna", "aight", "whatcha", "whatchu", "dunno", "kinda",
              "sorta", "outta", "finna", "'em", "c'mon"]

slang_desenleri = " ".join(d for d, _ in SLANG)
for kelime in DILBILGISI:
    kacis = kelime.replace("'", "'?")
    if re.search(re.escape(kelime).replace("'", "'?"), slang_desenleri, re.IGNORECASE):
        # "tryna smoke" gibi ANLAM kuralları serbest: yanında bir içerik kelimesi olmalı.
        for d, _ in SLANG:
            if re.search(re.escape(kelime).replace("'", "'?"), d, re.IGNORECASE):
                sade = re.sub(r"\\\\b|\\\\s\*|\(\?[:=!<][^)]*\)|[\\\\^$?*+|()\[\]{}]", " ", d)
                if len(sade.split()) < 2:
                    hata.append(
                        f"'{kelime}' hem Slang hem SlangNormalizer'da: Slang önce çalışıp "
                        f"ötekini eziyor (desen: {d})")

norm_desenleri = " ".join(d for d, _ in NORM)
for kelime in DILBILGISI:
    bekle(re.search(re.escape(kelime).replace("'", "'?"), norm_desenleri, re.IGNORECASE)
          or kelime in ("yall",),
          f"'{kelime}' SlangNormalizer'dan kaybolmuş")

bekle(len(SLANG) <= 60,
      f"Slang.rewrites {len(SLANG)} kurala çıkmış: dilbilgisi kuralları sızmış olabilir")


# ------------------------------------------------------------------- sonuç

if hata:
    print("Argo testi DÜŞTÜ:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print(f"Argo zinciri testi geçti (Slang {len(SLANG)} kelime kuralı + "
      f"SlangNormalizer {len(NORM)} dilbilgisi kuralı, kopya yok; "
      f"{len(DUZELMELI)} dönüşüm, {len(BOZULMAMALI)} koruma).")
