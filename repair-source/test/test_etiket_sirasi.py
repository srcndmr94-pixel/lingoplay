#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Etiket dallarının SIRASI ve KALIBI korunuyor mu?

9.73'te "[whispering] It's perfect." satırı "[fısıldıyor] [ses efekti]"
oluyordu. Kökü iki şeyin birleşimiydi:
  1. SoundTags içindeki "I -> [" onarımı "It's"i bozuyordu (9.74'te koşula
     bağlandı, Ses.java'da kilitli),
  2. doğru davranış, satırın inlineTag dalına düşmesine bağlı.

Bu test ikinci maddeyi koruyor. OfflineFix.kt'de sıra şu olmak ZORUNDA:

      inlineTag  ->  openSpeaker  ->  tagPrefix

çünkü:
  * inlineTag KÜÇÜK harfle başlayan etiketi yakalar ve SoundTags ile
    ÇEVİRİR:    "[whispering] It's perfect." -> "[fısıldıyor] Mükemmel."
  * tagPrefix her etiketi yakalar ama ÇEVİRMEZ (konuşmacı adı sanar):
    "[SHREK] Great job" -> "[SHREK] Harika iş" (doğru, ad çevrilmemeli)

Sıra ters çevrilirse ses etiketleri İngilizce kalır; inlineTag büyük harfi
de kabul ederse konuşmacı adları çevrilmeye çalışılır. İkisi de sessiz
bozulma olurdu.
"""

import os
import re
import sys

KOK = os.path.dirname(os.path.abspath(__file__))
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")
YOL = os.path.join(KAYNAK, "OfflineFix.kt")

hata = []
with open(YOL, encoding="utf-8") as f:
    s = f.read()

# --- 1) inlineTag kalıbı küçük harf şartını koruyor mu?
m = re.search(r'private val inlineTag = Regex\("([^"]+)"\)', s)
if not m:
    hata.append("inlineTag kalıbı bulunamadı")
else:
    kalip = m.group(1)
    if "[a-z]" not in kalip:
        hata.append(
            "inlineTag artık küçük harf şartı taşımıyor: " + kalip +
            " — büyük harfli konuşmacı adları ([SHREK]) ses etiketi sanılır."
        )

# --- 2) tagPrefix etiketi ÇEVİRMEMEYE devam ediyor mu?
i_tag = s.find("tagPrefix.find(text)")
if i_tag < 0:
    hata.append("tagPrefix dalı bulunamadı")
else:
    govde = s[i_tag:i_tag + 400]
    if "SoundTags.translate" in govde:
        hata.append("tagPrefix dalı artık etiketi çeviriyor — konuşmacı adları bozulur.")

# --- 3) SIRA: inlineTag, tagPrefix'ten ÖNCE gelmeli
i_inline = s.find("val inner = inlineTag.findAll(text)")
i_open = s.find("openSpeaker.find(text)")
if i_inline < 0:
    hata.append("inlineTag dalı bulunamadı")
elif i_tag >= 0 and i_inline > i_tag:
    hata.append(
        "SIRA BOZULDU: tagPrefix dalı inlineTag'den önce geliyor. "
        "Bu hâlde '[whispering] It's perfect.' ses etiketi çevrilmeden kalır."
    )
if i_open >= 0 and i_tag >= 0 and i_open > i_tag:
    hata.append("SIRA BOZULDU: openSpeaker dalı tagPrefix'ten sonra geliyor.")

# --- 4) inlineTag dalı etiketi SoundTags ile çeviriyor mu?
if i_inline >= 0:
    govde = s[i_inline:i_inline + 900]
    if "SoundTags.translate" not in govde:
        hata.append("inlineTag dalı artık SoundTags.translate çağırmıyor — "
                    "ses etiketleri İngilizce kalır.")

# --- 5) SoundTags'teki "I -> [" onarımı KOŞULA bağlı kalmalı
i_ses = s.find("isSoundWords")
if i_ses < 0:
    hata.append("isSoundWords bulunamadı — 'I -> [' onarımının koruması yok.")
else:
    # Koşulsuz blunt replace geri gelmiş mi?
    if re.search(r'Regex\("\(\^\|\\\\s\)\[Il\|\]\(\?=\[a-z\]\)"\)\s*\.replace\([^)]*,\s*"', s):
        hata.append("'I -> [' onarımı yeniden KOŞULSUZ hâle gelmiş — "
                    "\"It's\" gibi gerçek kelimeler bozulur.")

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  - " + h)
    sys.exit(1)

print("Etiket sırası testi geçti: inlineTag -> openSpeaker -> tagPrefix, "
      "küçük harf şartı ve koşullu 'I -> [' onarımı yerinde.")
