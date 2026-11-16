#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
9.96 — Parantezsiz konuşan adı ("Man in Black No shape..."). KonusanOnEki
mantığının aynası: sabitler Kotlin kaynağından okunur, mantık burada yazılı;
kaynakta aynı satırların durduğu ayrıca denetlenir (ayna bayatlarsa düşer).
"""
import os
import re
import sys

KOK = os.path.dirname(os.path.abspath(__file__))
K = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")
src = open(os.path.join(K, "OfflineFix.kt"), encoding="utf-8").read()
cap = open(os.path.join(K, "CaptureService.kt"), encoding="utf-8").read()
hata = []

a = src.index("object KonusanOnEki {")
b = src.index("object AdDefteri {")
kod = src[a:b]


def kume(ad):
    m = re.search(r"private val %s = setOf\((.*?)\)" % ad, kod, re.S)
    return set(re.findall(r'"([^"]+)"', m.group(1))) if m else set()


YASAK, BAGLAC = kume("YASAK_TEK"), kume("BAGLAC")
if len(YASAK) < 40 or "the" not in YASAK or BAGLAC != {"in", "of", "the", "de", "la", "von", "van", "al", "du"}:
    hata.append("YASAK_TEK / BAGLAC okunamadı.")

# mantık koruması
for parca in ("while (n < a.size && n < b.size && n < 4 && a[n] == b[n]) n++",
              "if (!adSozcugu(on.first()) || !adSozcugu(on.last())) continue",
              "if (on.any { !adSozcugu(it) && it !in BAGLAC }) continue",
              "if (on[0].lowercase() in YASAK_TEK) continue",
              "if (k == 1 && on[0].length < 3) continue",
              "if (!cumleMi(a.subList(k, a.size)) || !cumleMi(b.subList(k, b.size))) continue",
              "w.length >= 2 && w.first().isUpperCase() && w.drop(1).any { it.isLowerCase() } &&",
              "w.any { k -> k.count { it.isLowerCase() } >= 3 }",
              "if (!buyukBaslar(a[k]) || !buyukBaslar(b[k])) continue",
              "if (a.subList(k, a.size) == b.subList(k, b.size)) continue",
              "if (ortakOran(a.subList(k, a.size), b.subList(k, b.size)) >= 0.5) continue",
              "w.map { k -> k.lowercase().filter { it.isLetter() } }.filter { it.length >= 3 }.toSet()",
              "return x.intersect(y).size.toDouble() / minOf(x.size, y.size)",
              'if (s.length < 6 || s.startsWith("[") || s.startsWith("(")) return satir',
              "while (sonSatirlar.size > 6) sonSatirlar.removeFirst()",
              'if (kalan.isEmpty() || !kalan.first().isUpperCase()) continue',
              'if (kalan.substringBefore(\' \').endsWith(":")) continue',
              'if (a[k].endsWith(":") || b[k].endsWith(":")) continue',
              "if (a[k] == b[k] && adSozcugu(a[k])) continue",
              'if (w.take(4).any { it.endsWith(":") }) return satir',
              'return "[$ad] $kalan"'):
    if parca not in kod:
        hata.append("KonusanOnEki değişmiş, ayna bayat: " + parca)
if "KonusanOnEki.uygula(AdDefteri.acikEtiketKapat(EtiketOnar.onar(OcrOnar.onar(raw))))" not in cap:
    hata.append("clean() zincirine bağlı değil.")
if "KonusanOnEki.temizle()" not in cap:
    hata.append("Yakalama başında sıfırlanmıyor (önceki oyunun adları taşınır).")


def ad_sozcugu(w):
    return len(w) >= 2 and w[0].isupper() and any(c.islower() for c in w[1:]) and \
        all(c.isalpha() or c in "'’-" for c in w)


def cumle_mi(w):
    return any(sum(1 for c in k if c.islower()) >= 3 for k in w)


def ortak(a, b):
    n = 0
    while n < len(a) and n < len(b) and n < 4 and a[n] == b[n]:
        n += 1
    for k in range(n, 0, -1):
        if k >= len(a) or k >= len(b):
            continue
        on = a[:k]
        if not ad_sozcugu(on[0]) or not ad_sozcugu(on[-1]):
            continue
        if any(not ad_sozcugu(w) and w not in BAGLAC for w in on):
            continue
        if on[0].lower() in YASAK:
            continue
        if k == 1 and len(on[0]) < 3:
            continue
        if not a[k][:1].isupper() or not b[k][:1].isupper():
            continue
        if a[k].endswith(":") or b[k].endswith(":"):
            continue
        if a[k] == b[k] and ad_sozcugu(a[k]):
            continue
        if not cumle_mi(a[k:]) or not cumle_mi(b[k:]):
            continue
        if ortak_oran(a[k:], b[k:]) >= 0.5:
            continue
        if a[k:] == b[k:]:
            continue
        return " ".join(on)
    return None


def sozler(w):
    return {x for x in ("".join(c for c in k.lower() if c.isalpha()) for k in w) if len(x) >= 3}


def ortak_oran(a, b):
    x, y = sozler(a), sozler(b)
    if not x or not y:
        return 0.0
    return len(x & y) / min(len(x), len(y))


class Ayna:
    def __init__(self):
        self.son, self.bilinen = [], []

    def uygula(self, satir):
        s = satir.strip()
        if len(s) < 6 or s.startswith("[") or s.startswith("("):
            return satir
        w = s.split()
        if any(x.endswith(":") for x in w[:4]):
            return satir
        if not self.son or self.son[-1] != s:
            for onceki in self.son:
                ad = ortak(w, onceki.split())
                if ad:
                    if ad not in self.bilinen:
                        self.bilinen.append(ad)
                    break
            self.son.append(s)
            self.son = self.son[-6:]
        for ad in sorted(self.bilinen, key=len, reverse=True):
            if not s.startswith(ad + " "):
                continue
            kalan = s[len(ad):].strip()
            if kalan and kalan[0].isupper() and not kalan.split(" ")[0].endswith(":"):
                return "[%s] %s" % (ad, kalan)
        return satir


# 1) 12 Kasım 9.95 kaydı, sırasıyla
akis = [
    ("The Mandate: an alliance of various sects united against the Maskwalkers.", None),
    ("Man in Black No shape. No shadow. Yet it moves through all. That's the path to know.", None),
    ("Who are you?", None),
    ("Show me your strength. Earn the answer.", None),
    ("Soul I touched a lóng Pillar bound in chains... and found myself here.", None),
    ("Soul Is this part of the Discordance? And where is your real body?",
     "[Soul] Is this part of the Discordance? And where is your real body?"),
    ("Man in Black Tch. The Lông don built these ruins to show off. Cowards, all of them.",
     "[Man in Black] Tch. The Lông don built these ruins to show off. Cowards, all of them."),
    ("Man in Black I'm trapped here thanks to your Order.., and Mu Tianmiao's schemes.",
     "[Man in Black] I'm trapped here thanks to your Order.., and Mu Tianmiao's schemes."),
    ("Soul It seems you have a long history with both the Lóng Clan and the Order.",
     "[Soul] It seems you have a long history with both the Lóng Clan and the Order."),
    ("Man in Black And I know plenty about you.", "[Man in Black] And I know plenty about you."),
]
ay = Ayna()
for satir, beklenen in akis:
    cikan = ay.uygula(satir)
    if cikan != (beklenen or satir):
        hata.append("yanlış: %s\n      beklenen: %s\n      çıkan   : %s" % (satir, beklenen or satir, cikan))
if sorted(ay.bilinen) != ["Man in Black", "Soul"]:
    hata.append("öğrenilen adlar yanlış: %s" % ay.bilinen)

# 2) yanlış alarm olmamalı: gerçek kayıt korpusları (köşeli etiketli diziler)
# Korpustaki GERÇEK parantezsiz konuşan adları (Wuchang kaydı): bunlar dönüşmeli.
GERCEK_KONUSAN = {"Old Man Gen", "Puppet Bi Yuan", "Soul", "Blue Clad Young Master"}
toplam = degisen = dogru = 0
ornek = []
for dosya in ("kayit_satirlari.txt", "batman_satirlari.txt", "onarim_ingilizce.txt"):
    yol = os.path.join(KOK, dosya)
    if not os.path.exists(yol):
        continue
    ay = Ayna()
    for l in open(yol, encoding="utf-8"):
        l = l.strip()
        if not l:
            continue
        toplam += 1
        c = ay.uygula(l)
        if c != l:
            m = re.match(r"\[([^\]]+)\] ", c)
            if m and m.group(1) in GERCEK_KONUSAN:
                dogru += 1
                continue
            degisen += 1
            if len(ornek) < 80:
                ornek.append("%s: %s -> %s" % (dosya, l[:60], c[:60]))
if dogru < 40:
    hata.append("Korpustaki gerçek konuşan adları tanınmıyor (yalnız %d satır)." % dogru)
if degisen > 0:
    hata.append("Korpusta %d/%d satır yanlışlıkla konuşan adlı sayıldı:\n      %s" % (degisen, toplam, "\n      ".join(ornek)))

# 2b) 12 Kasım 9.99 kaydı: aynı satırın bozuk okumaları ad öğretmemeli
ay = Ayna()
for l in ["Afailed Transcension subject, arelessly left to rot.",
          "Afailed Transcension subject, carelessly left to rot.",
          "Afailed Tianscension subject, carelessly left to rot."]:
    ay.uygula(l)
if ay.bilinen:
    hata.append("OCR varyantlarından ad öğrenildi: %s" % ay.bilinen)

# 3) elle seçilmiş tuzaklar
for a_, b_ in [("I Know what you did.", "I Need help."),
               ("Mark! Wait for me.", "Mark! Stop it."),
               ("Press X Jump over the wall.", "Press X Climb up."),
               ("Nolan Grayson is here.", "Nolan Grayson Is late."),   # tek ad satırı: ikincisi dönüşür, ilki küçük harf devam
               ("The Order Will rise again.", "The Order Is watching.")]:
    ay = Ayna()
    ay.uygula(a_)
    c = ay.uygula(b_)
    if a_.startswith(("I ", "Mark!", "Press")) and c != b_:
        hata.append("tuzak dönüştü: %s -> %s" % (b_, c))

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  - " + h)
    sys.exit(1)
print("Konuşan adı testi geçti: 'Man in Black', 'Soul' öğrenildi; %d korpus satırında %d gerçek konuşan adı tanındı, yanlış alarm yok." % (toplam, dogru))
