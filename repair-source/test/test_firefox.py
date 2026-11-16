#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
10.2 — Firefox çeviri motoru (Offline Translator arayüzü). Derlenemeyen
Kotlin/AIDL için kaynak düzeyinde denetim: arayüz dosyaları yayıncınınkiyle
aynı imzada mı, Android 11+ görünürlük izni var mı, AIDL derlemesi açık mı,
motor cevap vermezse/adı bozarsa yerleşik motora düşülüyor mu.
"""
import os
import re
import sys

KOK = os.path.dirname(os.path.abspath(__file__))
APP = os.path.join(KOK, "..", "app")
K = os.path.join(APP, "src", "main", "java", "com", "example", "altyazi")
oku = lambda *p: open(os.path.join(*p), encoding="utf-8").read()
hata = []

AIDL = os.path.join(APP, "src", "main", "aidl", "dev", "davidv", "translator")
beklenen = {
    "ITranslationService.aidl": r"interface ITranslationService \{\s*void translate\(String textToTranslate, String fromLanguage, String toLanguage, ITranslationCallback callback\);\s*\}",
    "ITranslationCallback.aidl": r"oneway interface ITranslationCallback \{\s*void onTranslationResult\(String translatedText\);\s*void onTranslationError\(in TranslationError error\);\s*\}",
    "TranslationError.aidl": r"parcelable TranslationError \{\s*ErrorType type;\s*@nullable String language;\s*@nullable String message;\s*\}",
    "ErrorType.aidl": r"enum ErrorType \{\s*COULD_NOT_DETECT_LANGUAGE,\s*DETECTED_BUT_UNAVAILABLE,\s*UNEXPECTED,\s*\}",
}
for ad, desen in beklenen.items():
    yol = os.path.join(AIDL, ad)
    if not os.path.exists(yol):
        hata.append("AIDL eksik: " + ad)
        continue
    m = oku(yol)
    if "package dev.davidv.translator;" not in m:
        hata.append("%s paketi yanlış (servis bağlanmaz)" % ad)
    if not re.search(desen, m):
        hata.append("%s imzası yayıncınınkinden farklı" % ad)
    if "OR MIT" not in m:
        hata.append("%s lisans başlığı (MIT) eksik" % ad)

man = oku(APP, "src", "main", "AndroidManifest.xml")
if not re.search(r"<queries>\s*(<[^>]+>\s*)*<package android:name=\"dev\.davidv\.translator\" />", man):
    hata.append("Manifestte <queries> paketi yok: Android 11+ servisi göremez.")
gr = oku(APP, "build.gradle.kts")
if not re.search(r"buildFeatures \{[^}]*aidl = true", gr, re.S):
    hata.append("AIDL derlemesi açık değil (buildFeatures.aidl).")

ff = oku(K, "FirefoxCeviri.kt")
for parca, ne in (
    ('const val EYLEM = "dev.davidv.translator.ITranslationService"', "servis eylemi"),
    ("Intent(EYLEM).setPackage(PAKET)", "açık paketle bağlanma"),
    ('s.translate(metin, "en", "tr", geriDonus)', "dil kodları"),
    ("withTimeoutOrNull(ZAMAN_ASIMI_MS) { sonuc.await() }", "zaman aşımı"),
    ("servis = ITranslationService.Stub.asInterface(binder)", "bağlantı"),
):
    if parca not in ff:
        hata.append("FirefoxCeviri: eksik %s" % ne)
m = re.search(r"const val ZAMAN_ASIMI_MS = (\d+)L", ff)
if not m or not (500 <= int(m.group(1)) <= 2500):
    hata.append("ZAMAN_ASIMI_MS makul değil.")

cap = oku(K, "CaptureService.kt")
for parca, ne in (
    ("if (ffSonuc != null && yerTutucu.all { ffSonuc.contains(it) }) ffSonuc else f?.translate(parca)",
     "Firefox cevap vermezse ya da adı bozarsa yerleşik motora düşülmüyor"),
    ("if (f == null && ff == null && !google) return null", "hiçbir motor yokken çıkış"),
    ("firefox?.birak()", "kapanışta servis bırakılmıyor"),
    ("firefox = if (prefs.firefoxMotoru) FirefoxCeviri(this)", "ayar dikkate alınmıyor"),
):
    if parca not in cap:
        hata.append("CaptureService: " + ne)

ana = oku(K, "MainActivity.kt")
for parca in ("cbFirefox = findViewById(R.id.cbFirefox)", "cbFirefox.isChecked = prefs.firefoxMotoru",
              "prefs.firefoxMotoru = cbFirefox.isChecked", "R.id.btnFirefoxIndir", "R.id.tvFirefoxDurum"):
    if parca not in ana:
        hata.append("Ayar ekranı bağlı değil: " + parca)

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  - " + h)
    sys.exit(1)
print("Firefox motoru testi geçti: arayüz yayıncıyla aynı, görünürlük ve AIDL açık, yedek düşüş var.")
