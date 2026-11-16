"""
BOŞLUK TARAMASI — kayıt beklemeden eksik arar.

Bu betiğin varlık sebebi bir çalışma biçimi hatası. 11 Kasım boyunca her
eksik ses sözcüğünü kullanıcının kaydı gösterdikçe tek tek ekledim:

    9.63 kaydı -> pants, screech, high-pitched, snaps eksik
    9.64 kaydı -> militaristic, energetic, fiercely, children... (15 tane)
    9.65 kaydı -> atmospheric, haunting, alert, tone... (7 tane)
    9.66 kaydı -> thump, creature, yowling, curtain... (6 tane)

Hepsi AYNI hata sınıfı: translatePart tanımadığı kelimeyi sessizce atıyor,
sonuç "[ses efekti]" ya da kırpılmış etiket oluyor. Dört kayıt boyunca aynı
sınıfı dört kez düzelttim; bir kez tarayıp kapatmak yerine.

Bu betik o soruyu öne alıyor: altyazı ses ipuçlarında sık geçen kelimelerden
hangileri sözlükte YOK? Yeni bir eksik çıkarsa test düşer ve ben kullanıcının
kaydını beklemeden eklerim.

AYRICA iki tutarlılık denetimi:
  - Hâl zarfı olan (-ly) sözcükler ZARF kümesinde olmalı, yoksa Türkçede
    fiilden sonra kalır: "[homurdanıyor sertçe]".
  - Kalıp tablosundaki anahtarlar keyOf sonrası çakışmamalı; çakışan girdi
    sessizce ölür.
"""

import os
import re
import sys

KOK = os.path.dirname(os.path.abspath(__file__))
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")
off = open(os.path.join(KAYNAK, "OfflineFix.kt"), encoding="utf-8").read()

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


def blok(s, baslik, acilis="mapOf("):
    """Dengeli parantezle keser; yorum ve dizge içindeki parantezi saymaz."""
    i = s.index(baslik)
    i = s.index(acilis, i)
    d, j = 0, i + len(acilis) - 1
    dizge = yorum = False
    while True:
        c = s[j]
        if yorum:
            if c == "\n":
                yorum = False
        elif dizge:
            if c == "\\":
                j += 1
            elif c == '"':
                dizge = False
        elif c == "/" and s[j + 1:j + 2] == "/":
            yorum = True
        elif c == '"':
            dizge = True
        elif c == "(":
            d += 1
        elif c == ")":
            d -= 1
            if d == 0:
                break
        j += 1
    return s[i:j]


ses = off[off.index("object SoundTags {"):]
WORDS = dict(re.findall(r'"([^"]+)"\s*to\s*"([^"]+)"',
                        blok(ses, "private val words: Map<String, String> = mapOf(")))
PHRASES = dict(re.findall(r'"([^"]+)"\s*to\s*"([^"]+)"',
                          blok(ses, "private val phrases = mapOf(")))
SKIP = set(re.findall(r'"([^"]+)"', blok(ses, "private val skip = setOf(", "setOf(")))
ZARF = set(re.findall(r'"([^"]+)"', blok(off, "private val ZARF = setOf(", "setOf(")))


# ------------------------------------------- 1) ses ipucu sözcük kapsaması

# Altyazı ses/sahne ipuçlarında sık geçen kelimeler. Liste büyüdükçe denetim
# güçlenir; yeni bir dizide eksik görülürse BURAYA da eklenir ki bir daha
# aynı sınıf gözden kaçmasın.
ADAYLAR = """
sighs gasps grunts groans screams shouts yells whispers laughs chuckles sobs cries
sniffs coughs sneezes yawns snores hiccups belches burps gulps swallows pants wheezes
footsteps knocking doorbell phone ringing buzzing beeping clicking ticking alarm siren
engine tires brakes horn crash explosion gunshot gunfire reload cocking slicing stabbing
punch kick slap thud thump crack snap splash rain thunder wind fire crackling sizzling
glass shattering metal scraping creaking squeaking rattling clanking clattering
music song melody theme humming singing chanting applause cheering booing whistling
growls snarls hisses barks meows purrs chirping howling roaring screeching
breathing inhales exhales choking gagging retching vomiting spitting
indistinct muffled distant faint overlapping simultaneous unintelligible inaudible
speaking shouting whispering yelling muttering stammering stuttering sobbing
static feedback distortion echo reverb beep tone chime ding dong
water dripping pouring flushing boiling bubbling
clothes rustling paper tearing zipper velcro
keyboard typing mouse printer scanner
elevator escalator train subway bus truck motorcycle helicopter airplane jet
baby child crowd audience murmuring
sword clashing arrow magic sparkle portal teleport
heartbeat pulse monitor flatline defibrillator
dramatic ominous suspenseful tense eerie upbeat somber melancholy triumphant playful
soft loud gentle harsh sudden slow fast rapid steady rhythmic
quietly loudly softly sharply slowly suddenly violently frantically desperately
grunting groaning panting gasping sighing laughing crying screaming yelling
continues continuing stops fades swells builds rises intensifies
creature curtain thump pants screech snaps alert children unison soldiers cadet
atmospheric haunting vocalizing militaristic energetic intriguing
""".split()

eksik = sorted({w for w in ADAYLAR if w not in WORDS and w not in SKIP})
bekle(not eksik,
      "ses ipucu sözlüğünde %d eksik sözcük: %s\n"
      "      Bunlar bir gün '[ses efekti]' ya da kırpılmış etiket olarak ekrana gelir."
      % (len(eksik), ", ".join(eksik)))


# ----------------------------------------- 2) hâl zarfları ZARF kümesinde mi

# "-ly" ile biten ama zarf OLMAYAN sıfatlar: bunlar ZARF'a girmemeli.
ZARF_DEGIL = {"melancholy", "ugly", "friendly", "lonely", "lively", "silly",
              "early", "only", "holy", "deadly", "likely", "daily", "weekly"}
zarfsiz = [w for w in WORDS
           if w.endswith("ly") and w not in ZARF and w not in SKIP and w not in ZARF_DEGIL]
bekle(not zarfsiz,
      "ZARF kümesinde olmayan hâl zarfları: %s\n"
      "      Türkçede zarf fiilden ÖNCE gelir; eksik kalırsa '[homurdanıyor sertçe]' olur."
      % ", ".join(zarfsiz))


# --------------------------------- 3) kalıp anahtarları çakışıyor mu

def key_of(t):
    return "".join(c for c in t.lower() if c.isalnum())


for tablo, ad in ((PHRASES, "SoundTags.phrases"),):
    gorulen = {}
    for k in tablo:
        kk = key_of(k)
        if kk in gorulen and gorulen[kk] != k:
            hata.append("%s içinde çakışan anahtar: %r ve %r aynı keyOf'a düşüyor, "
                        "biri sessizce ölü" % (ad, gorulen[kk], k))
        gorulen[kk] = k

# Hazır sözlük (GAME_ENTRIES + DEFAULT_ENTRIES) anahtar çakışması: SONRAKİ
# satır öncekini EZER, bu bilinçli bir kural. Ama aynı anahtara FARKLI
# karşılık yazılmışsa bu bir çelişkidir, bilinmesi gerekir.
sozluk = []
for blok_adi in ("DEFAULT_ENTRIES", "GAME_ENTRIES"):
    a = off.index('const val %s = """' % blok_adi)
    a = off.index('"""', a) + 3
    sozluk.append(off[a:off.index('"""', a)])
girdiler = {}
celiski = []
for satir in "\n".join(sozluk).splitlines():
    l = satir.strip()
    if not l or l.startswith("#") or l.startswith("@") or "=" not in l:
        continue
    k, v = l.split("=", 1)
    kk, vv = key_of(k), v.strip().rstrip(".!?… ")
    if kk in girdiler and girdiler[kk] != vv:
        celiski.append("%r: %r -> %r" % (k.strip(), girdiler[kk], vv))
    girdiler[kk] = vv
bekle(len(celiski) <= 0,
      "hazır sözlükte %d çelişkili girdi (aynı anahtar, farklı karşılık; "
      "sonraki öncekini eziyor):\n      %s" % (len(celiski), "\n      ".join(celiski[:8])))


# ---------------------------------------------------------------- sonuç

if hata:
    print("Boşluk taraması DÜŞTÜ:")
    for h in hata:
        print("  ✗ " + h)
    sys.exit(1)

print("Boşluk taraması temiz (%d ses sözcüğü, %d kalıp, %d sözlük girdisi, "
      "%d aday kelime tarandı)."
      % (len(WORDS), len(PHRASES), len(girdiler), len(set(ADAYLAR))))
