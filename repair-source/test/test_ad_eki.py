"""
Kullanıcının 6 Kasım 22:17 kaydındaki dört sorunu sınar.

  1. Konuşan adı iki nokta olmadan başa yapışıyor:
       "D-don't come any closer!"  /  "Old Man Gen D-don't come any closer!"
     İki ayrı satır sanılıp üst üste çevriliyordu.
  2. Tek basamaklı OCR çöpü ("1 remember", "g0od") doğru YZ çevirisini
     "sayı düştü" diye reddettiriyordu.
  3. "Press O to throw..." tuş uyarısı altyazı sanılıyordu.
  4. "Now all LCàndo Refollo M hehindwomand" gibi çöp okumalar çevriliyordu.
"""

import gzip
import os
import re
import sys

KOK = os.path.dirname(__file__)
APP = os.path.join(KOK, "..", "app", "src", "main")
KAYNAK = os.path.join(APP, "java", "com", "example", "altyazi")

yakala = open(os.path.join(KAYNAK, "CaptureService.kt"), encoding="utf-8").read()
koruma = open(os.path.join(KAYNAK, "AiGuard.kt"), encoding="utf-8").read()
ocr = open(os.path.join(KAYNAK, "OcrSozluk.kt"), encoding="utf-8").read()

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


# --------------------------------------------- 1) adı iki nokta olmadan

bekle("fun adEkliMi" in yakala, "adEkliMi yok")
bekle("adEkliMi(a, b)" in yakala, "adEkliMi similar() icinde cagrilmiyor")

TR = str.maketrans("ışğüöçâîû", "isguocaiu")


def normalize(s):
    return "".join(c for c in s.lower().translate(TR) if c.isalnum())


def ad_ekli_mi(kisa, uzun):
    nk, nu = normalize(kisa), normalize(uzun)
    if len(nk) < 12 or len(nu) <= len(nk):
        return False
    if not nu.endswith(nk):
        return False
    fazla = len(nu) - len(nk)
    if fazla > len(nk):
        return False
    birikim = adet = 0
    for w in uzun.strip().split():
        if birikim >= fazla:
            break
        if not w[:1].isupper():
            return False
        birikim += len(normalize(w))
        adet += 1
        if adet > 4:
            return False
    return 1 <= adet <= 4 and birikim == fazla


def ayni(a, b):
    return ad_ekli_mi(a, b) or ad_ekli_mi(b, a)


for k, u in [
    ("D-don't come any closer!", "Old Man Gen D-don't come any closer!"),
    ("Come, come. Let's catch up a bit, eh?", "Old Man Gen Come, come. Let's catch up a bit, eh?"),
    ("A rare guest indeed! Come in, let's talk!",
     "Blue Clad Young Master Arare guest indeed! Come in, let's talk!"),
    ("Oh? A living soul in this place?", "Blue Clad Young Master Oh? A living soul in this place?"),
]:
    bekle(ayni(k, u), f"ayni sayilmadi: {k!r} / {u!r}")

for k, u in [
    ("I have to find her...", "It's too dangerous. I have to find her..."),
    ("Come in, let's talk!", "A rare guest indeed! Come in, let's talk!"),
    ("this old man could take on all of you!", "Back in my day, this old man could take on all of you!"),
]:
    bekle(not ayni(k, u), f"yanlis birlestirildi: {k!r} / {u!r}")


# --------------------------------------------------------- 2) sayı kuralı

m = re.search(r'Regex\("(\(\?<!\[\\\\p\{L\}\\\\d\]\)[^"]*)"\)', koruma)
bekle("\\\\d{2,}" in koruma, "sayi kurali iki basamakli olmali")
SAYI = re.compile(r"(?<![^\W\d_\d])\d{2,}(?![^\W\d_\d])")


def sayilar(s):
    return re.findall(r"(?<![^\W_])\d{2,}(?![^\W_])", s)


bekle(sayilar("The Village of Lu.. 1 remember it as a quiet place,") == [],
      "tek basamakli OCR copu hala sayi sayiliyor")
bekle(sayilar("what g0od is money") == [], "g0od hala sayi sayiliyor")
bekle(sayilar("We have 51 ships and 2 boats.") == ["51"], "gercek sayi yakalanmadi")


# ---------------------------------------------------------- 3) tuş uyarısı

bekle("TUS_UYARISI" in yakala, "tus uyarisi deseni yok")
TUS = re.compile(r"(?i)\b(press|hold|tap|push)\s+(?:and\s+hold\s+)?(?:the\s+)?"
                 r"([a-z]|[lr][123]|△|○|□|✕|circle|square|triangle|cross|touchpad|options)\b")
for s in ["Press O to throw the Crimson Viper", "Hold R2 to aim",
          "Press the Touchpad to open the map", "Tap X to continue"]:
    bekle(TUS.search(s) is not None, f"tus uyarisi gecti: {s}")
for s in ["Press on, men!", "Hold the line!", "Press your advantage.",
          "Hold still, this will hurt."]:
    bekle(TUS.search(s) is None, f"gercek replik tus sanildi: {s}")


# ----------------------------------------------------------- 4) çöp okuma

bekle("fun cokBozuk" in ocr, "cokBozuk yok")
bekle("OcrOnar.cokBozuk" in yakala, "cokBozuk OCR akisinda cagrilmiyor")

SOZ = os.path.join(APP, "res", "raw", "kelimeler.gz")
with gzip.open(SOZ, "rt", encoding="utf-8") as f:
    KELIMELER = {w.strip() for w in f if w.strip()}


def cok_bozuk(metin):
    toplam = bilinen = 0
    for ham in re.split(r"[^\w'’]+", metin, flags=re.UNICODE):
        w = re.split(r"['’]", ham)[0]
        if len(w) < 4 or any(ord(c) > 127 for c in w):
            continue
        toplam += 1
        if w.lower() in KELIMELER:
            bilinen += 1
    return toplam >= 4 and bilinen * 2 < toplam


for s in ["Years go by, and a man's nerve justain'twhat it used to be... "
          "Now all LCando Refollo M hehindwomand he Carefu",
          "Now alll candofsfollo Mehehind vo L andbo carefu"]:
    bekle(cok_bozuk(s), f"cop okuma gecti: {s[:40]}")
for s in ["A rare guest indeed! Come in, let's talk!",
          "But I heard something terrible happened in the Village of Lu!",
          "I should pay you for the damaged cart.",
          "When I was young I dreamt of roaming the Wulin... fearless, just like you."]:
    bekle(not cok_bozuk(s), f"gercek altyazi cop sanildi: {s[:40]}")


# ------------------------------------------- 5) "YZ her satirda" ayari

prefs = open(os.path.join(KAYNAK, "Prefs.kt"), encoding="utf-8").read()
ana = open(os.path.join(KAYNAK, "MainActivity.kt"), encoding="utf-8").read()
duzen = open(os.path.join(APP, "res", "layout", "activity_main.xml"), encoding="utf-8").read()

bekle("var alwaysAi" in prefs, "alwaysAi ayari Prefs'te yok")
bekle('getBoolean("alwaysAi", false)' in prefs, "varsayilan kapali olmali")
bekle("cbAlwaysAi" in duzen, "ayar kutusu arayuzde yok")
bekle(ana.count("cbAlwaysAi") >= 4, "ayar kutusu MainActivity'ye tam baglanmamis")
bekle("prefs.alwaysAi = cbAlwaysAi.isChecked" in ana, "ayar kaydedilmiyor")
bekle("cbAlwaysAi.isChecked = prefs.alwaysAi" in ana, "ayar yuklenmiyor")
bekle("!prefs.alwaysAi && !AiGuard.yzGerekli" in yakala, "ayar YZ kapisina baglanmamis")
# 9.91: bes kutu tek "ceviri bicimi" secimine indi; baslik artik bicimi yaziyor.
bekle("biçim:$bicimAdi" in yakala, "surum satirinda ceviri bicimi gorunmuyor")


# ------------------------------------------ 6) bozuk okuma hafizaya girmesin

depo = open(os.path.join(KAYNAK, "TranslationStore.kt"), encoding="utf-8").read()
bekle("fun hafizayaUygun" in yakala, "hafiza suzgeci yok")
bekle("HARF_RAKAM" in yakala, "harf+rakam deseni yok")
# The cache check may include additional predicates and line breaks.
kaydet = re.search(r"private fun saveToMemory\([^)]*\)\s*\{(.*?)\n    \}", yakala, re.S)
bekle(kaydet is not None and all(x in kaydet.group(1) for x in
      ("hafizayaUygun(source)", "TranslationCachePolicy10427.reusable(source)",
       "TranslationQualityGate.mayRemember(source, value)")),
      "saveToMemory OCR, baglam ve anlam suzgeclerini cagirmiyor")
bekle("fun purge" in depo, "eski bozuk kayitlari silen purge yok")
bekle("purge { HARF_RAKAM" in yakala, "acilista hafiza temizlenmiyor")

HR = re.compile(r"[^\W\d_]\d|\d[^\W\d_]", re.UNICODE)
# Kayittaki bozuk okumalar hafizaya girmemeli
for s_ in ["Old Man Gen I asked where she came from. She said the valley outpost... and... the campin thow O0d",
           "and. the. cam pin the WOode nhh O0d",
           "3old Man Gen I asked where she came from."]:
    bekle(HR.search(s_) is not None, f"bozuk okuma hafizaya girecek: {s_[:40]}")
# Temiz satirlar girebilmeli
for s_ in ["Tell me more about the girl.",
           "She was scared. Even her voice was shaking.",
           "We have 51 ships and 2 boats.",
           "Her eyes were red... but she didn't shed a single tear."]:
    bekle(HR.search(s_) is None, f"temiz satir hafizadan men edildi: {s_[:40]}")


# ------------------------- 7) kuyrugu bozuk tekrar okumalar birlesmeli

bekle("fun ayniBasMi" in yakala, "ortak bas kurali yok")
bekle("ayniBasMi(a, b)" in yakala, "ortak bas kurali similar() icinde cagrilmiyor")
m_az = re.search(r"ORTAK_BAS_EN_AZ\s*=\s*(\d+)", yakala)
m_or = re.search(r"ORTAK_BAS_ORAN\s*=\s*([\d.]+)f", yakala)
bekle(m_az is not None and m_or is not None, "ortak bas esikleri bulunamadi")
EN_AZ = int(m_az.group(1)) if m_az else 25
ORAN = float(m_or.group(1)) if m_or else 0.65


def bas_adaylari(s):
    out = [s]
    w = s.strip().split()
    for n in range(1, 5):
        if n >= len(w) or not w[n - 1][:1].isupper():
            break
        out.append(" ".join(w[n:]))
    return out


m_mut = re.search(r"ORTAK_BAS_MUTLAK\s*=\s*(\d+)", yakala)
MUTLAK = int(m_mut.group(1)) if m_mut else 40


def ortak_bas(a, b):
    na, nb = normalize(a), normalize(b)
    k = min(len(na), len(nb))
    if k < EN_AZ:
        return False
    i = 0
    while i < k and na[i] == nb[i]:
        i += 1
    if i >= MUTLAK:
        return True
    return i >= EN_AZ and i >= k * ORAN


def ayni_bas(a, b):
    return any(ortak_bas(x, y) for x in bas_adaylari(a) for y in bas_adaylari(b))


OKUMALAR = [
    "I asked where she came from. She said the valley outpost... and.. tho camninthe NOOds nhh",
    "Old Man Gen I asked where she came from. She said the valley outpost... and... uhh tho camninthe WOods",
    "I asked where she came from. She said the valley outpost... and... the. cam pin the WOode mhh",
    "Old Man Gen I asked where she came from. She said the valley, outpost... and... the camp in tho NO0d",
    "Old Man Gen I asked where she came from. She said the valley outpost... and.. the camn in the t M00d",
    "I asked where she came from. She said the valley outpost... and... rhh the camn in the w O0ds",
    "I asked where she came from. She said the valley outpost... and... tho camnintho WOOds nnhh",
    "Old Man Gen I asked where she came from. She said the valley outpost... and... nhh the camn in the O0de",
    "I asked where she came from, She said the valley outpost... and... the camn in the MOod",
    "Old Man Gen I asked where she came from, She said the valley outpost... and... tho camnintho MOods",
    "I asked where she came from. She said the valley outpost... and... tho camninthe MOOds",
    "Old Man Gen I asked where she came from. She said the valley outpost... and... tho cannin the w Ood",
]
for o in OKUMALAR[1:]:
    bekle(ayni_bas(OKUMALAR[0], o), f"ayni replik ayri sayildi: {o[:55]}")

bekle(m_mut is not None, "mutlak ortak bas esigi yok")

# 7 Kasim kaydi: uzun repliklerde kuyrugun yarisi bozuluyor, oran %65'in altina
# dusuyor ama ortak baslangic 46-64 harf. Bunlar ayni replik.
UZUN_GRUPLAR = [
    ["You three are renowned masters of the Wulin, To resist Soul Disnlacomont like thi VOu have m M resnect",
     "You three are renowned masters of the Wulin. To resist Soul VOu have m Mresnect Disnlacoment like this",
     "You three are renowned masters of the Wulin, To resist Soul Disnlacement like this v0u bave my rosnect"],
    ["Puppet Bi Yuan I am... Bi Yuan of the Tianxuan Sect. These two...Master Bihai of KIVe Tomnlo and Mastor Ho of tho Shenii School",
     "Puppet Bi Yuan I am... Bi Yuan of the Tianxuan Sect. These two.co Master Bihai of and Mastor Ho of the Shenii Schol",
     "Puppet Bi Yuan I am... Bi Yuan of the Tianxuan Sect. These two., Master Bihai of KIVe Temnle and Mastor He of the Shenii School"],
    ["It seems a Mandate Leader is trapped inside, but he has aleady lost conscionsnoss",
     "It seems a Mandate Leader is trapped inside, but he has already lost consciousnoss"],
]
for grup in UZUN_GRUPLAR:
    for o in grup[1:]:
        bekle(ayni_bas(grup[0], o), f"uzun replik ayri sayildi: {o[:50]}")

# Gercekten farkli replikler birlesmemeli (ortak baslangic 0-10 harf)
for a, b in [
    ("Old Man Gen Said the folks there had met..trouble.",
     "Old Man Gen She couldn't say what happened just that it wasn't safe anymore."),
    ("Hold the front. Guard the rear.", "Hold formation."),
    ("Find our bodies..within the village..", "Then... grant us death!"),
    ("It seems a Mandate Leader is trapped inside, but he has already lost consciousnoss",
     "Save Well of the Three Puppets Leave"),
    ("Is there no other way to restore your minds to your bodies?",
     "Heh... haha.. there is no other way!"),
    ("Kill our bodies... and what little remains of us..will fade...",
     "Then... these threads... will unravelo"),
]:
    bekle(not ayni_bas(a, b), f"farkli replik birlestirildi: {a[:45]}")

for a, b in [
    ("Tell me more about the girl.", "She was Scared, Even her voice was shaking."),
    ("Old Man Gen Said the folks there had met..trouble.",
     "Old Man Gen She couldn't say what happened just that it wasn't safe anymore."),
    ("That poor child. Whata cruel world...", "Go on ahead! These old legs aren't going anywhere!"),
    ("Wait here. When I reach the tunnel, come back and let you know.",
     "Go on ahead! These old legs aren't going anywhere!"),
]:
    bekle(not ayni_bas(a, b), f"farkli replik birlestirildi: {a[:40]}")


# ------------------------------------------ 8) Turkce yazi cevrilmesin

bekle("fun turkceYazi" in yakala, "turkce yazi suzgeci yok")
bekle("turkceYazi(collapsed" in yakala, "turkce suzgeci clean() icinde cagrilmiyor")
TR_HARF = "çğışöüÇĞİŞÖÜ"


def turkce_yazi(s):
    return any(len(k) >= 5 and any(c in TR_HARF for c in k)
               for k in re.split(r"[^\w]+", s, flags=re.UNICODE))


for s_ in ["Osil I Seçenekler", "Ayarlar ve Seçenekler", "Çeviriyi başlat"]:
    bekle(turkce_yazi(s_), f"turkce yazi gecti: {s_}")
for s_ in ["Tell me more about the girl.", "That poor child. What a cruel world.",
           "Go on ahead! These old legs aren't going anywhere!"]:
    bekle(not turkce_yazi(s_), f"ingilizce altyazi turkce sanildi: {s_}")


# ------------------------------- 9) konusan adini ogren ve ayir

bekle("fun adOgren" in yakala, "konusan adi ogrenme yok")
bekle("fun adiAyir" in yakala, "konusan adi ayirma yok")
bekle("adiAyir(clean(raw))" in yakala, "adiAyir OCR akisinda cagrilmiyor")
bekle("prefs.showSpeaker" in yakala, "konusani goster ayari kullanilmiyor")
bekle("KONUŞAN ADI ÖĞRENİLDİ" in yakala, "ogrenilen ad kayda yazilmiyor")


def ad_ogren(a, b):
    wa, wb = a.strip().split(), b.strip().split()
    uzun, kisa = (wa, wb) if len(wa) >= len(wb) else (wb, wa)
    fark = len(uzun) - len(kisa)
    if not (1 <= fark <= 4) or len(kisa) < 2:
        return None
    for i in range(fark):
        w = uzun[i]
        if not w[:1].isupper():
            return None
        if any((not c.isalpha()) and c not in ".'" for c in w):
            return None
    kalan, kisam = " ".join(uzun[fark:]), " ".join(kisa)
    if normalize(kalan) != normalize(kisam) and not ortak_bas(kalan, kisam):
        return None
    ad = " ".join(uzun[:fark])
    return ad if 2 <= len(ad) <= 32 else None


for a, b, beklenen in [
    ("I asked where she came from. She said the valley outpost... and.. tho camninthe NOOds nhh",
     "Old Man Gen I asked where she came from. She said the valley outpost... and... uhh tho camninthe WOods",
     "Old Man Gen"),
    ("D-don't come any closer!", "Old Man Gen D-don't come any closer!", "Old Man Gen"),
    ("Oh? A living soul in this place?",
     "Blue Clad Young Master Oh? A living soul in this place?", "Blue Clad Young Master"),
    ("Tell me more about the girl.", "Soul Tell me more about the girl.", "Soul"),
]:
    bekle(ad_ogren(a, b) == beklenen,
          f"ad ogrenilemedi: beklenen {beklenen!r}, gelen {ad_ogren(a, b)!r}")

for a, b in [
    ("I have to find her...", "It's too dangerous. I have to find her..."),
    ("come any closer!", "D-don't come any closer!"),
]:
    bekle(ad_ogren(a, b) is None, f"yanlis ad ogrenildi: {ad_ogren(a, b)!r}")


# ------------------------- 10) YZ sirasi: biten ceviri bosa gitmesin
#
# BU KURAL TERSINE DONDU (9.66). Eskiden yeni satir gelince suren ceviri
# yarida kesiliyordu, "sira ekrandaki satirda olsun" diye. Olcum bunun
# bedelini gosterdi: kullanicinin 11 Kasim 9.65 kaydinda yapay zeka ortalama
# 3808 ms suruyor, altyazi 2-4 sn duruyor; yani ceviri neredeyse HER seferinde
# bitmeden kesiliyordu. 56 replikten yalniz 5'i duzeltilebildi, 44'u zayif
# taslakla ekranda kaldi.
#
# Artik kesilmiyor: biten ceviri ya yigin'in ust sirasina yaziliyor ya
# hafizaya giriyor. Sira kendiliginden temizleniyor, cunku bekleyen satir
# kilidi aldiginda "llmLatest == source" degilse dusuyor.

llm = open(os.path.join(KAYNAK, "LlmTranslate.kt"), encoding="utf-8").read()
bekle("cancelOthers" not in llm and "cancelOthers" not in yakala,
      "yarida kesme geri gelmis: ceviri neredeyse hic bitmez")
bekle("cancelProcess" not in yakala,
      "ceviri baska bir yoldan yarida kesiliyor")
bekle(yakala.count("discardIfStale = false") >= 2,
      "biten ceviri atiliyor: hem hiz hem dogruluk yolunda saklanmali")
# Sira beklemesi kayda yaziliyor mu (olcebilelim)
bekle("lastWaitMs" in yakala, "sira beklemesi kayda yazilmiyor")
# stillWanted kilit ALINDIKTAN sonra kontrol edilmeli (bayat is bosa gitmesin)
kilit = llm.find("shared.lock.withLock")
kontrol = llm.find("if (!stillWanted()) return@withLock null")
bekle(kilit != -1 and kontrol != -1 and kontrol > kilit,
      "bayat satir kontrolu kilitten once yapiliyor")


# --------------------------- 11) ceviri ekranda yeterince kalsin

prefs2 = open(os.path.join(KAYNAK, "Prefs.kt"), encoding="utf-8").read()
bekle("var minShowSec" in prefs2, "en az gosterim suresi ayari yok")
bekle('getInt("minShowSec", 3)' in prefs2, "varsayilan 3 sn olmali")
bekle("coerceIn(1, 10)" in prefs2, "sure 1-10 sn araliginda olmali")
bekle("shownAtMs" in yakala, "gosterim zamani tutulmuyor")
bekle("yeterinceDurdu" in yakala, "en az sure kontrolu yok")
bekle("&& yeterinceDurdu" in yakala, "silme kontrolu sureye bagli degil")
bekle("sbMinShow" in duzen, "sure ayari arayuzde yok")
bekle(ana.count("sbMinShow") >= 4, "sure ayari MainActivity'ye tam baglanmamis")
bekle("prefs.minShowSec = sbMinShow.progress" in ana, "sure kaydedilmiyor")
bekle("sbMinShow.progress = prefs.minShowSec" in ana, "sure yuklenmiyor")


# ------------------------ 12) altyaziya yapisan tus uyarisi parcasi

bekle("HUD_PARCA" in yakala, "yapisan tus uyarisi temizligi yok")
HUDP = re.compile(r"(?i)\b(touchpad only|(?:press|hold|tap) \S{1,3} to skip|skip intro)\b")


def hud_at(g):
    t = re.sub(r"\s{2,}", " ", HUDP.sub(" ", g)).strip().lstrip("-–—:,").strip()
    return t if len(t) >= 3 else ""


bekle(hud_at("Touchpad only -said the folks there hadmet...trouble.")
      == "said the folks there hadmet...trouble.", "yapisan tus uyarisi atilmadi")
bekle(hud_at("Touchpad only") == "", "tek basina tus uyarisi elenmedi")
for s_ in ["She was scared. Even her voice was shaking.", "I had to skip the meeting."]:
    bekle(hud_at(s_) == s_, f"gercek replik bozuldu: {s_}")


# ----------------- 13) ad-dusme denetimi kucuk harfli kelimeleri reddetmesin

koruma = open(os.path.join(KAYNAK, "AiGuard.kt"), encoding="utf-8").read()
bekle("k.first().isLowerCase()" in koruma, "kucuk harf kelimeler ad denetiminden muaf degil")
bekle("if (i == 0) continue" in koruma, "cumlenin ilk kelimesi ad denetiminden muaf degil")

ADLAR_K = set(re.findall(r'"([^"]+)"',
    re.sub(r"//[^\n]*", "",
    re.search(r"ADLAR[^=]*=\s*setOf\((.*?)\n\s*\)", koruma, re.S).group(1))))
TERIMLER_K = dict(re.findall(r'"([^"]+)"\s+to\s+"([^"]+)"',
    re.search(r"TERIMLER[^=]*=\s*mapOf\((.*?)\n\s*\)", koruma, re.S).group(1)))


def ad_terim_dustu(kaynak, yz):
    yk = yz.lower()
    ks = [x for x in re.split(r"[^\w'’]+", kaynak) if x]
    for i, k in enumerate(ks):
        if len(k) < 3 or k[0].islower():
            continue
        kk = k.lower()
        if kk in TERIMLER_K:
            if kk not in yk and TERIMLER_K[kk] not in yk:
                return f"terim dustu: {k}"
            continue
        if i == 0 or kk not in ADLAR_K:
            continue
        if kk not in yk:
            return f"ad dustu: {k}"
    return None


# kayittaki yanlis reddedilenler artik gecmeli
bekle(ad_terim_dustu(
    "This power is easily stirred by external forces and may drive you into madness.",
    "Bu guc dis etkenlerle kolayca tetiklenir ve seni deliligin icine surukler.") is None,
    "'may' hala ad sanilip reddediliyor")
bekle(ad_terim_dustu(
    "In the Wulin, people drift like duckweed in a storm.",
    "Wulin'de insanlar firtinada su yosunu gibi suruklenir.") is None,
    "'storm' hala ad sanilip reddediliyor")
# gercek ad/terim dusmesi hala yakalanmali
bekle(ad_terim_dustu("I'll lay off to the Jackdaw.", "Gemiye cekilecegim.") is not None,
      "Jackdaw dusmesi yakalanmadi")
bekle(ad_terim_dustu("sold to the Templars have led them to us.",
                     "satilan haritalar onlari bize getirdi.") is not None,
      "Templars dusmesi yakalanmadi")
bekle(ad_terim_dustu("sold to the Templars have led them to us.",
                     "tapınakçılara satılan haritalar bize getirdi.") is None,
      "dogru Tapinakci cevirisi reddedildi")


# ---------------- 14) harita/menu kontrol cubugu (dugme simgeleri)

bekle("TEK_HARF_SIMGE" in yakala, "dugme simgesi suzgeci yok")
TEK = re.compile(r"(?<![^\W\d_'’])[B-HJ-Z](?![^\W\d_'’])")


def hud(s):
    return len(TEK.findall(s)) >= 2


for g in ["O Move O Zoom ỏ Reset View",
          "O Move O Zoom G Reset View A World Map O Back"]:
    bekle(hud(g), f"HUD cubugu taninmadi: {g}")
for g in ["I think original, something I've never seen before.",
          "A mysterious power lies within you.",
          "We grew up as orphans.",
          "Mu Tianmiao founded the Order to uphold the rules."]:
    bekle(not hud(g), f"gercek replik HUD sanildi: {g}")


# -------------------------------- 15) "bos geciyor": satir asla kaybolmasin
#
# Kullanicinin kaydindaki zincir:
#   TASLAK GÖSTERİLMEDİ (hızlı çeviri başarısız): AHHHHHHHHH!!!!
#   YZ REDDEDİLDİ (çevrilmemiş) · taslak kalıyor          <- kalacak taslak YOK
# Taslak yokken siki denetim satiri bomboş bırakıyordu.

cevrimdisi = open(os.path.join(KAYNAK, "OfflineFix.kt"), encoding="utf-8").read()

# a) taslak yokken denetim gevsiyor
bekle("fun yetersizMi" in koruma, "yetersizMi yok")
bekle("if (failed) {" in yakala and "AiGuard.yetersizMi(source, duzgun)" in yakala,
      "taslak yokken gevsek denetim baglanmamis")

# b) yapay zeka bos dondugunde son care: kaynagi goster
bekle("ÇEVRİLEMEDİ, KAYNAK GÖSTERİLİYOR" in yakala, "YZ bos donunce son care yok")
bekle(yakala.count('tag = " · çevrilemedi"') >= 2,
      "cevrimdisi tarafta son care yok")
# son care hafizaya YAZILMAMALI: yoksa Ingilizce satir kalici olur
for parca in yakala.split('tag = " · çevrilemedi"')[:-1]:
    bekle("remember = false" in parca[-120:], "cevrilemedi satiri hafizaya yaziliyor")

# c) parcali ceviri: tek parca duserse satirin tamami gitmesin
bekle("if (ceviren == 0) return null" in cevrimdisi,
      "parcali ceviri sayaci yok (hep-ya-hic geri gelmis)")
bekle(cevrimdisi.count("if (ceviren == 0) return null") >= 2,
      "replik VE ses etiketi yollarindan biri hala hep-ya-hic")
bekle("?: turnText" in cevrimdisi, "cevrilemeyen replik atiliyor")
bekle("if (ceviren == 0) null else out.joinToString" in cevrimdisi,
      "cumle dongusu hala tek hatada butun satiri dusuruyor")
bekle("translate(turnText, ml) ?: return null" not in cevrimdisi,
      "eski hep-ya-hic satiri hala duruyor")

# d) ad BOZULMASI denetimi (hafiza kirlenmesini onler)
bekle("ad bozuldu" in koruma, "ad bozulma denetimi yok")
bekle("fun mesafe" in koruma, "duzenleme mesafesi yok")


def mesafe(a, b, sinir):
    if abs(len(a) - len(b)) > sinir:
        return sinir + 1
    onceki = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        simdi = [i] + [0] * len(b)
        for j in range(1, len(b) + 1):
            simdi[j] = min(onceki[j] + 1, simdi[j - 1] + 1,
                           onceki[j - 1] + (0 if a[i - 1] == b[j - 1] else 1))
        if min(simdi) > sinir:
            return sinir + 1
        onceki = simdi
    return onceki[len(b)]


def ad_bozuldu(kaynak, yz):
    ks = re.findall(r"[^\W\d_]+", kaynak)
    for i, k in enumerate(ks):
        if len(k) < 5 or k[0].islower() or i == 0:
            continue
        kk = k.lower()
        if kk in yz.lower():
            continue
        for c in re.findall(r"[^\W\d_]+", yz):
            cc = c.lower()
            if cc[:1] != kk[:1] or abs(len(cc) - len(kk)) > 2:
                continue
            if 1 <= mesafe(cc, kk, 2) <= 2:
                return f"{k} -> {c}"
    return None


bekle(ad_bozuldu("Creek Valley Plains Entrance", "Creek Valley Plais Girişi"),
      "Plains->Plais bozulmasi yakalanmadi")
bekle(not ad_bozuldu("Creek Valley Plains Entrance", "Creek Valley Plains Girişi"),
      "dogru ad tasiyan ceviri reddedildi")
bekle(not ad_bozuldu("The heroes at Soulrend Cliff have fallen.",
                     "Soulrend Cliff'teki kahramanlar düştü."),
      "Turkce ekli ad bozulma sanildi")
bekle(not ad_bozuldu("Mu Tianmiao founded the Order to uphold the rules.",
                     "Mu Tianmiao kuralları korumak için Tarikatı kurdu."),
      "cevrilen ortak isim bozulma sanildi")

# e) kisalma denetimi KELIME degil HARF sayiyor
bekle("harf)" in koruma, "kisalma denetimi hala kelime sayiyor")
bekle("it.isLetterOrDigit() }" in koruma, "harf sayimi yok")
_t = "Belki de bu açık havaya uçurmak için buraya bir powderkeg yapabilirim..."
_y = "Belki buraya bir Powderkeg çekip bunu patlatabilirim..."
_th = sum(c.isalnum() for c in _t)
_yh = sum(c.isalnum() for c in _y)
bekle(not (_th >= 25 and _yh < _th * 0.6), "iyi kisa ceviri hala reddediliyor")
bekle(sum(c.isalnum() for c in "Haritalar") < sum(
    c.isalnum() for c in "Tapınakçılara sattığınız haritalar onları doğrudan bize yönlendirdi") * 0.6,
      "gercek icerik kaybi artik yakalanmiyor")

# f) ogrenilen konusan adinda aksan artigi
bekle("fun aksansiz" in yakala, "aksan katlama yok")
bekle("aksansiz(text)" in yakala, "ad ayirma aksansiz karsilastirmiyor")

import unicodedata


def aksansiz(s):
    return "".join(c for c in unicodedata.normalize("NFD", s)
                   if unicodedata.category(c) != "Mn")


bekle(aksansiz("Žuo Shang") == "Zuo Shang", "Zuo aksani silinmedi")
bekle(aksansiz("Mu Tianmião") == "Mu Tianmiao", "Tianmiao aksani silinmedi")

# g) yarisi bozuk okuma esigi: tam %50 de cop saysin
bekle("bilinen * 2 <= toplam" in ocr, "cokBozuk esigi tam yarida hala geciriyor")


# ------------------------------------------------------------------- sonuc

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print("Ad eki + sayı + tuş + çöp + YZ + hafıza + ortak baş + Türkçe + konuşan + ad-denetim + HUD testi geçti.")
