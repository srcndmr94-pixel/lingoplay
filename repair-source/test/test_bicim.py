#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
9.91 — ÇEVİRİ BİÇİMİ: beş kutu yerine tek seçim.

12 Kasım 9.89 kayıtlarında ayarlar "hız öncelikli + iki satırlı yığın +
yalnız doğru çeviri" bileşimindeydi; ekranda iki ayrı repliğin çevirisi yan
yana kalıyor, kutulardan biri de hiç işlemiyordu. Kullanıcı: "gereksiz
kutucukları çıkaralım, kafa karışıklığı yapmasın".

Bu test şunlara bakar:
  1. Beş kutu ve "internet karma" ekranda GİZLİ, üçlü seçim var.
  2. Her biçim beş eski ayarı TEK ve tutarlı bir bileşime yazıyor.
  3. Güncellemeden gelen eski bileşim temizleniyor (ekran açılışı + yakalama başı).
  4. "Hızlı" biçimde yapay zekâ gerçekten devre dışı (telefon + internet).
"""
import os
import re
import sys
import xml.dom.minidom as minidom

KOK = os.path.dirname(os.path.abspath(__file__))
APP = os.path.join(KOK, "..", "app", "src", "main")
K = os.path.join(APP, "java", "com", "example", "altyazi")
oku = lambda ad: open(os.path.join(K, ad), encoding="utf-8").read()
prefs, ana, cap = oku("Prefs.kt"), oku("MainActivity.kt"), oku("CaptureService.kt")
xml_yol = os.path.join(APP, "res", "layout", "activity_main.xml")
hata = []

# 1) düzen
belge = minidom.parse(xml_yol)
ogeler = {}
for ad in ("CheckBox", "RadioButton", "RadioGroup"):
    for o in belge.getElementsByTagName(ad):
        ogeler[o.getAttribute("android:id").replace("@+id/", "")] = o
for gizli in ("cbAccuracy", "cbDogruyuBekle", "cbYalnizDogru", "cbYigin", "cbAlwaysAi", "rbHybrid"):
    o = ogeler.get(gizli)
    if o is None:
        hata.append("%s düzenden silinmiş — MainActivity findViewById çöker." % gizli)
    elif o.getAttribute("android:visibility") != "gone":
        hata.append("%s hâlâ ekranda görünüyor." % gizli)
grup = ogeler.get("rgBicim")
if grup is None:
    hata.append("rgBicim yok.")
else:
    ic = [c.getAttribute("android:id").replace("@+id/", "")
          for c in grup.getElementsByTagName("RadioButton")]
    if ic != ["rbBicimHizli", "rbBicimDengeli", "rbBicimDogru"]:
        hata.append("rgBicim içinde üç seçenek bu sırayla olmalı: %s" % ic)
    for c in grup.getElementsByTagName("RadioButton"):
        if c.getAttribute("android:visibility") == "gone":
            hata.append("Biçim seçeneği gizli kalmış.")

# 2) bicimUygula: her biçim için beş ayarın değeri (kaynaktan okunup yürütülüyor)
m = re.search(r"fun bicimUygula\(bicim: Int\) \{(.*?)\n    \}", prefs, re.S)
sabit = dict((a, int(d)) for a, d in re.findall(r"const val (BICIM_\w+) = (\d+)", prefs))
if not m or len(sabit) != 3 or len(set(sabit.values())) != 3:
    hata.append("bicimUygula ya da BICIM_* sabitleri bulunamadı.")
else:
    def uygula(b):
        d = {}
        for satir in m.group(1).strip().splitlines():
            ad, deger = [x.strip() for x in satir.split("=", 1)]
            deger = deger.replace("true", "True").replace("false", "False")
            for k, v in sabit.items():
                deger = deger.replace(k, str(v))
            d[ad] = eval(deger, {"bicim": b})
        return d
    for ad, b in sabit.items():
        d = uygula(b)
        beklenen = {"ceviriBicimi": b, "accuracyFirst": True, "dogruyuBekle": False,
                    "yigin": False, "alwaysAi": False, "yalnizDogru": ad == "BICIM_DOGRU"}
        if d != beklenen:
            hata.append("%s yanlış bileşim yazıyor: %s" % (ad, d))
if 'get() = sp.getInt("ceviriBicimi", BICIM_DENGELI)' not in prefs:
    hata.append("Varsayılan biçim Dengeli değil.")

# 3) eski bileşimin temizlenmesi
if not re.search(r'fun bicimiOnar\(\) \{\s*if \(!sp\.contains\("ceviriBicimi"\)\) bicimUygula\(BICIM_DENGELI\) '
                 r'else bicimUygula\(ceviriBicimi\)\s*if \(sp\.getInt\("mode", MODE_INSTANT\) == MODE_HYBRID\) mode = MODE_AI',
                 prefs):
    hata.append("bicimiOnar eski bileşimi / karma modu temizlemiyor.")
i_onar = ana.find("prefs.bicimiOnar()")
i_kutu = ana.find("cbAccuracy.isChecked = prefs.accuracyFirst")
if i_onar < 0 or i_kutu < 0 or i_onar > i_kutu:
    hata.append("Ayar ekranı kutuları onarımdan ÖNCE yüklüyor — eski bileşim geri kaydedilir.")
if not re.search(r"AdDefteri\.temizle\(\)\s*(KonusanOnEki\.temizle\(\)\s*)?prefs\.bicimiOnar\(\)", cap):
    hata.append("Yakalama başında biçim onarılmıyor.")
# kaydetme: biçim, gizli kutulardan ÖNCE uygulanmalı
i_b = ana.find("prefs.ceviriBicimi = bicim")
i_k = ana.find("prefs.accuracyFirst = cbAccuracy.isChecked")
arasi = ana[i_b:i_k] if 0 <= i_b < i_k else ""
for parca in ("cbAccuracy.isChecked = true", "cbDogruyuBekle.isChecked = false",
              "cbYalnizDogru.isChecked = bicim == Prefs.BICIM_DOGRU",
              "cbYigin.isChecked = false", "cbAlwaysAi.isChecked = false"):
    if parca not in arasi:
        hata.append("Kaydederken gizli kutu biçime göre ayarlanmıyor: " + parca)
if "rgBicim = findViewById(R.id.rgBicim)" not in ana:
    hata.append("rgBicim bağlanmamış (lateinit çöker).")

# 4) Hızlı: yapay zekâ yok
for parca, ne in (
    ("val hizli = LiveEnginePolicy.useFastPipeline(prefs.ceviriBicimi, prefs.mode)", "hızlı biçim okunmuyor"),
    ("val mode = if (hizli) Prefs.MODE_INSTANT else prefs.mode", "Hızlı'da internet yapay zekâsı çalışır"),
    ("val llmOn = l != null && l.ready && !hizli", "Hızlı'da telefon yapay zekâsı çalışır"),
    ("mode == Prefs.MODE_INSTANT && !hizli && (llm?.ready == true || bridge)",
     "Hızlı'da kaba çeviri hafızası kullanılmaz"),
    ("biçim:$bicimAdi", "kayıt başlığı biçimi yazmıyor"),
):
    if parca not in cap:
        hata.append("Eksik: " + ne)

# The policy is shared with startup, so check the implementation rather than
# assuming its expression must be inlined at every call site.
policy = open(os.path.join(K, "LiveEnginePolicy.kt"), encoding="utf-8").read()
if "format == Prefs.BICIM_HIZLI" not in policy or "format == Prefs.BICIM_DENGELI && mode == Prefs.MODE_INSTANT" not in policy:
    hata.append("Hızlı biçim politikası değişmiş veya devre dışı")

# ---------------------------------------------------------------------------
# 5) 9.93 — canlı çeviri kartı (gerçek süreler) ve hızlı konumlar
# ---------------------------------------------------------------------------
duzen = open(xml_yol, encoding="utf-8").read()
for kimlik in ("tvLastSrc", "tvLastTr", "tvStatOcr", "tvStatCeviri", "tvStatGecikme",
               "btnRegionBottom", "btnRegionTop", "btnRegionFull"):
    if duzen.count('android:id="@+id/%s"' % kimlik) != 1:
        hata.append("Düzende %s bir kez olmalı." % kimlik)
    if kimlik.startswith("tvStat") or kimlik in ("btnRegionTop", "btnRegionFull"):
        if ("R.id." + kimlik) not in ana:
            hata.append("%s koda bağlanmamış." % kimlik)
# süreler ölçülüyor mu (örnek veri değil)
if not re.search(r"val ocrBasi = android\.os\.SystemClock\.elapsedRealtime\(\)\s*"
                 r"val text = runOcr\(band, filterEdges = true\)\s*"
                 r"sonOcrMs = android\.os\.SystemClock\.elapsedRealtime\(\) - ocrBasi", cap):
    hata.append("Okuma süresi OCR çağrısının çevresinde ölçülmüyor.")
if "if (ms != null) sonCeviriMs = ms" not in cap:
    hata.append("Çeviri süresi yayın anında kaydedilmiyor.")
for ad in ("sonOcrMs", "sonCeviriMs"):
    if ("@Volatile var %s = 0L" % ad) not in cap:
        hata.append("%s tanımlı değil." % ad)
if '"${ocrMs + ceviriMs} ms"' not in ana:
    hata.append("Gecikme, ölçülen iki sürenin toplamı olarak gösterilmiyor.")
# hızlı konumların yazdığı sınırlar: üst < alt, 0..100 içinde
for dugme in ("btnRegionBottom", "btnRegionTop", "btnRegionFull"):
    m5 = re.search(r"R\.id\.%s\)\?\.setOnClickListener \{\s*sbTop\.progress = (\d+)\s*sbBottom\.progress = (\d+)\s*"
                   r"updateRegionLabel\(\)\s*save\(\)" % dugme, ana)
    if not m5:
        hata.append("%s sınırları yazıp kaydetmiyor." % dugme)
    else:
        ust, alt = int(m5.group(1)), int(m5.group(2))
        if not (0 <= ust < alt <= 100 and alt - ust >= 10):
            hata.append("%s geçersiz bölge yazıyor: %d-%d" % (dugme, ust, alt))

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  - " + h)
    sys.exit(1)
print("Çeviri biçimi testi geçti: beş kutu gizli, üç biçim tutarlı bileşim yazıyor, "
      "eski ayarlar temizleniyor, Hızlı'da yapay zekâ kapalı.")
