"""
Ses etiketlerini sınar (OfflineFix.kt / SoundTags).

Kullanıcının 7 Kasım dizi kaydındaki hatalar:
  "suspenseful music continues" -> "Süslü müzik devam ediyor"   (parantez düşmüş)
  "fighting grunts and groans"  -> "Grunts ve ingilizlerle savaşmak"
  "[yelling continues]"         -> "bağırıyor devam ediyor"     (bozuk Türkçe)
  "[driver]" / "[man]"          -> "ses efekti"                 (konuşan etiketi)
  "[crashes]" / "[gate buzzing]"-> "ses efekti" / "titriyor"
"""

import os
import re
import sys

KAYNAK = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main",
                      "java", "com", "example", "altyazi")
src = open(os.path.join(KAYNAK, "OfflineFix.kt"), encoding="utf-8").read()
yakala = open(os.path.join(KAYNAK, "CaptureService.kt"), encoding="utf-8").read()

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


def tablo(ad):
    m = re.search(ad + r"[^=]*=\s*mapOf\((.*?)\n    \)", src, re.S)
    if not m:
        return {}
    govde = re.sub(r"//[^\n]*", "", m.group(1))
    return dict(re.findall(r'"([^"]+)"\s+to\s+"([^"]+)"', govde))


def kume(ad):
    m = re.search(ad + r"[^=]*=\s*setOf\((.*?)\)", src, re.S)
    return set(re.findall(r'"([^"]+)"', m.group(1))) if m else set()


words = tablo("private val words")
phrases = tablo("private val phrases")
devam = tablo("private val devamAdi")
skip = kume("private val skip")
adjectives = kume("private val adjectives")

bekle(len(words) > 200, f"ses etiketi sozlugu kisa: {len(words)}")
bekle(len(devam) >= 30, f"devam tablosu kisa: {len(devam)}")
bekle("fun parantezsizEtiket" in src, "parantezsiz etiket tanima yok")
bekle("SoundTags.parantezsizEtiket" in yakala, "parantezsiz etiket clean() icinde kullanilmiyor")


def ceviri(content):
    tokens = [t for t in re.split(r"[\s,]+", content.lower()) if t]
    key = " ".join(t.strip("'-.") for t in tokens)
    if key in phrases:
        return phrases[key]
    if 2 <= len(tokens) <= 4 and tokens[-1].strip("'-.") in ("continues", "continue", "continuing"):
        onek = [t.strip("'-.") for t in tokens[:-1]]
        ad = devam.get(onek[-1]) if onek else None
        if ad:
            if ad == "müzik" and len(onek) >= 2 and onek[-2] in adjectives:
                ad = "müziği"
            sif = " ".join(words[w] for w in onek[:-1] if w in words)
            return (ad if not sif else f"{sif} {ad}") + " devam ediyor"
    res, known = [], 0
    for i, raw in enumerate(tokens):
        w = raw.strip("'-.")
        if w in skip or w not in words:
            continue
        known += 1
        res.append("müziği" if (w == "music" and i > 0 and tokens[i - 1].strip("'") in adjectives)
                   else words[w])
    return "ses efekti" if known == 0 else " ".join(res)


def parantezsiz(text):
    s = text.strip()
    if not s or s[0].isupper():
        return False
    if s[-1] in ".!?[]" or "[" in s or "]" in s:
        return False
    toks = [t for t in re.split(r"[\s,]+", s.lower()) if t]
    if not (2 <= len(toks) <= 6):
        return False
    if " ".join(t.strip("'-.") for t in toks) in phrases:
        return True
    bil = 0
    for raw in toks:
        w = raw.strip("'-.")
        if w in skip:
            continue
        if w not in words:
            return False
        bil += 1
    return bil >= 2


# ----------------------------------------- 1) "X continues" Turkcesi duzgun

for etiket, beklenen in [
    ("yelling continues", "bağırma devam ediyor"),
    ("blaring continues", "siren sesi devam ediyor"),
    ("dramatic music continues", "dramatik müzik devam ediyor"),
    ("suspenseful music continues", "gerilim müziği devam ediyor"),
]:
    bekle(ceviri(etiket) == beklenen,
          f"[{etiket}] -> beklenen {beklenen!r}, gelen {ceviri(etiket)!r}")

# Hicbir cevirisi "ses efekti" olmamali (sozlukte var)
for etiket in ["crashes", "gate buzzing", "music intensifying", "music sting",
               "driver", "man", "woman", "narrator", "driver sighs"]:
    bekle(ceviri(etiket) != "ses efekti", f"[{etiket}] hala 'ses efekti'")

bekle(ceviri("gate buzzing") == "kapı vızıldıyor",
      f"gate buzzing -> {ceviri('gate buzzing')!r}")
bekle(ceviri("driver") == "sürücü", "konusan etiketi cevrilmiyor")


# ------------------------------------- 2) parantezsiz etiket tanima

for g in ["suspenseful music continues", "fighting grunts and groans"]:
    bekle(parantezsiz(g), f"parantezsiz etiket taninmadi: {g}")

# Gercek replikler etiket sanilmamali
for g in ["The music continues", "Hell of a way to die.. and it's almost Christmas.",
          "I can take care of myself.", "Ladies.", "Let me go! Please!",
          "the police commissioner's daughter's in here.. Let's go."]:
    bekle(not parantezsiz(g), f"gercek replik etiket sanildi: {g}")


# ------------------------------------------------------------------- sonuc

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print(f"Ses etiketi testi geçti ({len(words)} kelime, {len(phrases)} kalıp, "
      f"{len(devam)} devam biçimi).")
