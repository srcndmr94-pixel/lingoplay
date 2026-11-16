#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KÜFÜR KÖKLERİ KELİME SINIRINA BAĞLI MI?

Kullanıcının 11 Kasım kaydından gerçek kayıp:
    kaynak : "We need to light a fire under the medical examiner
              to get that tox report done."
    YZ     : "O toksikoloji raporunu alabilmek için adli tıp uzmanı..."
    sonuç  : YZ REDDEDİLDİ (küfür eklendi) · taslak kalıyor
    taslak : "...tıbbi muayenenin altındaki bir ateş yakmamız gerekiyor."

DOĞRU çeviri atıldı, BOZUK taslak ekranda kaldı. Sebep: TR_SERT kalıbında
"sik" kökü kelime sınırı olmadan yazılmıştı ve "tok-sik-oloji" içinde
eşleşiyordu.

Çarpışma listesi uygulamanın KENDİ sözlüğünden de besleniyor:
    "classical"  -> "klasik"      (kla-sik)
    "motorcycle" -> "motosiklet"  (moto-sik-let)
yani motosiklet geçen her satırda yapay zekâ çevirisi atılıyordu.

Bu test her küfür kökünün günlük kelimelerin İÇİNDE eşleşmediğini, ama
gerçek küfrü hâlâ yakaladığını doğruluyor. Kalıplar AiGuard.kt'den
çalışma anında okunuyor.
"""

import os
import re
import sys

# python3 -I betik dizinini sys.path'ten çıkarıyor; yardımcıyı elle ekliyoruz.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import java_regex  # noqa: E402

KOK = os.path.dirname(os.path.abspath(__file__))
YOL = os.path.join(KOK, "..", "app", "src", "main", "java", "com",
                   "example", "altyazi", "AiGuard.kt")

with open(YOL, encoding="utf-8") as f:
    K = f.read()


# ÖNEMLİ: kalıplar JAVA'da çalıştırılıyor (Kotlin'in gerçek motoru).
# Python'da "\\b" Unicode farkındadır, Java'da varsayılan ASCII'dir —
# bu test ilk hâlinde Python kullanıyordu ve "\\bgöt\\b" kalıbının
# Java'da "götürdü" İÇİNDE eşleştiğini GÖREMEDİ.
try:
    P_SERT = java_regex.kalip_cikar(K, "TR_SERT")
    P_KUFUR = java_regex.kalip_cikar(K, "TR_KUFUR")
except ValueError as e:
    print("BAŞARISIZ: kalıp okunamadı: %s" % e)
    sys.exit(1)

_ON = {}


def _es(pat, metin):
    if (pat, metin) not in _ON:
        esl, _, h = java_regex.esle([(pat, metin)])[0]
        if h:
            raise SystemExit("BAŞARISIZ: kalıp derlenmedi: %s" % h)
        _ON[(pat, metin)] = esl
    return _ON[(pat, metin)]


def toplu(pat, metinler):
    eksik = [(pat, m) for m in metinler if (pat, m) not in _ON]
    if eksik:
        for (p_, m_), (esl, _, h) in zip(eksik, java_regex.esle(eksik)):
            if h:
                raise SystemExit("BAŞARISIZ: kalıp derlenmedi: %s" % h)
            _ON[(p_, m_)] = esl


hata = []

# ---------------------------------------------------------------------------
# 1) GÜNLÜK KELİMELER küfür sayılmamalı. Hepsi gerçek çarpışma: içlerinde
#    bir küfür kökü alt dizgi olarak geçiyor.
# ---------------------------------------------------------------------------
TEMIZ = [
    # "sik" çarpışmaları
    "toksikoloji raporu",
    "O toksikoloji raporunu alabilmek için adli tıp uzmanına baskı yapmalıyız",
    "toksik madde",
    "eksik parça",
    "bir eksiklik var",
    "klasik müzik çalıyor",
    "[klasik müzik çalıyor]",
    "motosiklet sesi",
    "motosikletle geldi",
    "Meksika sınırı",
    # "bok" çarpışmaları (kelime sınırı zaten vardı, korunmalı)
    "bokstan sonra",
    "tombol değil",
    # "pic" çarpışmaları
    "epic bir an",
    "picasso tablosu",
    # "lan" çarpışmaları
    "planı bu",
    "lanse etti",
    "ilan verdi",
    "Atlanta'da",
    "selanik",
    # "be" çarpışmaları
    "beni bekle",
    "beraber gidelim",
    "bebek uyuyor",
    # "am" çarpışmaları
    "amaç bu",
    "ambulans geldi",
    "kampüste",
    # "götü" çarpışmaları
    "götürdü onu",
    "götürmek lazım",
    # --- Java'ya ÖZGÜ çarpışmalar: "\\b" ASCII olduğu için Türkçe harf
    # kelime sınırı sayılıyor ve kök kelimenin İÇİNDE eşleşiyor.
    "götürdü onu",
    "götürmek lazım",
    "götüreceğim seni",
    "beğendim bunu",
    "Beğendim",
    "beğenmedim",
    "yağmur yağıyor",
    "yoğun trafik",
    "sıkıntı yok",
    "boğazım ağrıyor",
    # diğer
    "aptalca değil ama",
    "salakça bir fikir değil",
    "şerefe",
    "piknik yaptık",
]
toplu(P_SERT, TEMIZ)
for t in TEMIZ:
    if _es(P_SERT, t):
        hata.append("TR_SERT günlük kelimede eşleşti: %r" % t)

# "götürdü", "aptalca", "salakça" TR_KUFUR'de bilerek eşleşebilir mi?
# Hayır: "götü" kökü "götürdü"nün içinde ve bu YANLIŞ.
for t in ("götürdü onu", "götürmek lazım", "planı bu", "lanse etti",
          "ilan verdi", "beni bekle", "beraber gidelim", "amaç bu",
          "ambulans geldi", "toksikoloji raporu", "klasik müzik",
          "motosiklet sesi", "eksik parça", "epic bir an"):
    if _es(P_KUFUR, t):
        hata.append("TR_KUFUR günlük kelimede eşleşti: %r" % t)

# ---------------------------------------------------------------------------
# 2) GERÇEK küfür hâlâ yakalanmalı. Yoksa "küfür düştü"/"küfür eklendi"
#    denetimleri işe yaramaz hâle gelir.
# ---------------------------------------------------------------------------
SERT = [
    "siktir git",
    "Siktir!",
    "hassiktir",
    "orospu çocuğu",
    "Seni piç",
    "amına koyayım",
    "Ne bok konuşuyorsun",
    "götü kalkmıyor",
    "şerefsiz herif",
    "yarrak kafa",
    "sikeyim böyle işi",
    "sikik bir durum",
]
toplu(P_SERT, SERT)
for t in SERT:
    if not _es(P_SERT, t):
        hata.append("TR_SERT GERÇEK küfrü kaçırdı: %r" % t)

KUFUR_EK = ["Biliyorum lan", "Gel ulan", "Ne be?", "kahretsin",
            "lanet olsun", "aptal herif", "salak mısın"]
toplu(P_KUFUR, KUFUR_EK)
for t in KUFUR_EK:
    if not _es(P_KUFUR, t):
        hata.append("TR_KUFUR pekiştiriciyi kaçırdı: %r" % t)

# 9.97 — 51 kaydın taramasında reddedilen DOĞRU çevirilerin sert sözcükleri
KUFUR_97 = ["Ufukta beliren o boktan her şeyi göremezsin.", "O pisliği zaten uyardım",
            "kahrolası herif", "bu pislik herifler", "amk ya"]
toplu(P_KUFUR, KUFUR_97)
for t_ in KUFUR_97:
    if not _es(P_KUFUR, t_):
        hata.append("TR_KUFUR yapay zekânın sert karşılığını kaçırdı: %r" % t_)
TEMIZ_97 = ["Boks maçı yarın.", "Bokser köpeği havladı.", "Bokseri gördün mü?"]
toplu(P_KUFUR, TEMIZ_97)
for t_ in TEMIZ_97:
    if _es(P_KUFUR, t_):
        hata.append("TR_KUFUR günlük kelimede eşleşti: %r" % t_)
try:
    P_IN = java_regex.kalip_cikar(K, "IN_KUFUR")
except ValueError as e:
    raise SystemExit("BAŞARISIZ: IN_KUFUR okunamadı: %s" % e)
IN_97 = ["we're losing civilians to these assholes.", "You bastards!", "Just your standard dipshits.",
         "Damned thing.", "goddamned emitter"]
toplu(P_IN, IN_97)
for t_ in IN_97:
    if not _es(P_IN, t_):
        hata.append("IN_KUFUR çoğul/ekli küfrü kaçırdı: %r" % t_)
IN_TEMIZ = ["He passed the classic test.", "Hello there.", "The shell is empty.", "Dickens wrote it."]
toplu(P_IN, IN_TEMIZ)
for t_ in IN_TEMIZ:
    if _es(P_IN, t_):
        hata.append("IN_KUFUR günlük kelimede eşleşti: %r" % t_)

# ---------------------------------------------------------------------------
# 3) Kalıplar hâlâ iki denetime bağlı mı?
# ---------------------------------------------------------------------------
if '"küfür eklendi"' not in K or '"küfür düştü"' not in K:
    hata.append("küfür denetimlerinden biri kaybolmuş")

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  - " + h)
    sys.exit(1)

print("Küfür sınırı testi geçti: %d günlük kelimede yanlış eşleşme yok, "
      "%d gerçek küfür yakalanıyor." % (len(TEMIZ), len(SERT) + len(KUFUR_EK)))
