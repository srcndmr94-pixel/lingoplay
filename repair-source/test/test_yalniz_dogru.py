#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
"YALNIZ DOĞRU ÇEVİRİ" modu bağlı mı?

Kullanıcının isteği (12 Kasım): hızlı motorun ilk çevirisi ekrana HİÇ
çıkmasın, yalnız doğru olan görünsün; ilk çeviri arka planda kalsın.

Bu mod dört yerde birden doğru bağlanmak zorunda; biri eksik kalırsa ya
taslak yine görünür ya da ekran boş kalır:
  1. ayar saklanıyor ve ekrandaki kutuya bağlı,
  2. taslak yayınlanmıyor,
  3. yapay zekâ cevap veremezse / reddedilirse taslak YEDEK olarak çıkıyor,
  4. geç gelen doğru çeviri boşa gitmiyor.
Ayrıca yapay zekâ konuşmasının önceden hazırlanması (ısıtma) tek konuşma
kuralına uyuyor mu, ona bakılıyor.
"""
import os
import re
import sys

KOK = os.path.dirname(os.path.abspath(__file__))
APP = os.path.join(KOK, "..", "app", "src", "main")
K = os.path.join(APP, "java", "com", "example", "altyazi")


def oku(*p):
    with open(os.path.join(*p), encoding="utf-8") as f:
        return f.read()


prefs = oku(K, "Prefs.kt")
main = oku(K, "MainActivity.kt")
cap = oku(K, "CaptureService.kt")
llm = oku(K, "LlmTranslate.kt")
xml = oku(APP, "res", "layout", "activity_main.xml")
hata = []

# 1) ayar
if 'sp.getBoolean("yalnizDogru", false)' not in prefs:
    hata.append("Prefs.yalnizDogru yok ya da varsayılanı kapalı değil.")
if '@+id/cbYalnizDogru' not in xml:
    hata.append("activity_main.xml içinde cbYalnizDogru kutusu yok.")
for parca in ("findViewById(R.id.cbYalnizDogru)",
              "cbYalnizDogru.isChecked = prefs.yalnizDogru",
              "prefs.yalnizDogru = cbYalnizDogru.isChecked"):
    if parca not in main:
        hata.append("MainActivity bağlantısı eksik: " + parca)

# 2) taslak yayınlanmıyor: taslakIsi dalının İLK koşulu yalnizDogru olmalı
m = re.search(r"val taslakIsi = if \(!failed\) \{\s*if \(prefs\.yalnizDogru\) \{(.*?)\} else if", cap, re.S)
if not m:
    hata.append("taslakIsi dalı yalnizDogru ile başlamıyor — taslak yine ekrana çıkar.")
else:
    # 9.90: taslak ANINDA çıkmaz; yalnız yapay zekâ YEDEK_TASLAK_MS içinde
    # yetişmezse ve satır hâlâ ekrandaysa çıkar. (9.86'daki "hiç çıkmaz"
    # kuralı hızlı konuşmada ekranı boş bırakıyordu.)
    dal = "\n".join(l for l in m.group(1).splitlines() if not l.strip().startswith("//"))
    i_bekle, i_yayin = dal.find("delay(YEDEK_TASLAK_MS)"), dal.find("publish(")
    if i_bekle < 0 or i_yayin < 0 or i_yayin < i_bekle:
        hata.append("yalnizDogru dalında taslak beklemeden yayınlanıyor (ya da yedek hiç yok).")
    if "if (translatingFor == source && shownSource != source)" not in dal:
        hata.append("Yedek taslak, satır geçmiş ya da doğrusu gelmiş olsa da basılıyor.")
mm = re.search(r"private val YEDEK_TASLAK_MS = (\d+)L", cap)
if not mm or not (800 <= int(mm.group(1)) <= 2000):
    hata.append("YEDEK_TASLAK_MS yok ya da makul aralıkta (800-2000 ms) değil.")
if "(prefs.accuracyFirst || prefs.yalnizDogru)" not in cap:
    hata.append("Kutu 'hız öncelikli' ayarında işlemiyor (12 Kasım 9.89 kaydı).")
# internet yolunda da aynı yedek + bitince iptal
if not re.search(r"val kisaSatir = .*?<= 3\s*val taslakBekleme = if \(!prefs\.yalnizDogru && kisaSatir\) 0L else YEDEK_INTERNET_MS\s*"
                 r"val gecikmeYedegi = if \(mode != Prefs\.MODE_HYBRID\) scope\.launch \{\s*delay\(taslakBekleme\)", cap):
    hata.append("İnternet yolunda taslak: yalnız kısa satırda anında, uzun satırda ve Doğru önce'de YEDEK_INTERNET_MS sonra olmalı.")
mi = re.search(r"private val YEDEK_INTERNET_MS = (\d+)L", cap)
if not mi or not (800 <= int(mi.group(1)) <= 1500):
    hata.append("YEDEK_INTERNET_MS makul değil (800-1500 ms).")
if not re.search(r"val better = try \{ engine\.translate\(source\) \}.*?\n        \}\n        gecikmeYedegi\?\.cancel\(\)", cap, re.S):
    hata.append("İnternet cevabı gelince yedek iptal edilmiyor — doğru çevirinin üstüne taslak basılabilir.")

# 3) yedek yollar
if cap.count('tag = " · taslak (yedek)"') < 2:
    hata.append("Yedek taslak iki yolda da olmalı (YZ cevap vermedi + YZ reddedildi); "
                "yoksa bu modda ekran boş kalır.")
i = cap.find('log("YZ REDDEDİLDİ')
if i < 0 or "prefs.yalnizDogru" not in cap[i:i + 700]:
    hata.append("YZ reddedilince yalnizDogru modunda yedek taslak gösterilmiyor.")

# 4) 9.92: GEÇMİŞ satırın cevabı ekrana ÇIKMAZ (yalnız hafızaya girer).
#    9.86'daki "geç geldi, yine de göster" kuralı ekranda bir önceki repliğin
#    çevirisini bırakıyordu; kullanıcı konuşulanla yazılanı eşleştiremedi.
if "val gecGoster = ayniSatirinCevabi\n" not in cap:
    hata.append("Geçmiş satırın cevabı hâlâ ekrana basılabiliyor (gecGoster yalnız aynı-satır olmalı).")
if "GEÇ GELDİ (yine de gösteriliyor)" in cap:
    hata.append("'Geç geldi, yine de göster' yolu hâlâ kodda.")
# aynı satırın bozuk okumasına verilen cevap geç sayılmaz: YZ + benzerlik şart
if not re.search(r"val ayniSatirinCevabi = translatingFor != source &&\s*"
                 r"tag\.contains\(\"YZ\"\) && OcrOnar\.ayniGurultulu\(source, translatingFor\)", cap):
    hata.append("ayniSatirinCevabi koşulu değişmiş: YZ ve benzerlik ikisi birden şart.")
# 9.87: yalnız defterdeki adlardan oluşan satır yapay zekâya gitmemeli
i9 = cap.find("if (AiGuard.defterAdiMi(source))")
j9 = cap.find("if (!prefs.alwaysAi && !AiGuard.yzGerekli(source, quick))")
if i9 < 0 or j9 < 0 or i9 > j9:
    hata.append("defterAdiMi kısa yolu yok ya da 'her satırda YZ' ayarından SONRA — "
                "\"Alien.\" yine \"Yabancı.\" olur.")
ai = oku(K, "AiGuard.kt")
if "AD_YAZIMI.matches(it) && AdDefteri.adMi(it.lowercase())" not in ai:
    hata.append("defterAdiMi ad defterine bakmıyor.")
if "if (translatingFor != source && !gecGoster)" not in cap:
    hata.append("gecGoster hesaplanıyor ama geç-kaldı dalını atlatmıyor.")

# 5) kayıt başlığı modu söylüyor mu
if "DOĞRU ÖNCE (taslak yalnız gecikince)" not in cap:
    hata.append("Kayıt başlığı modu yazmıyor; kayıttan hangi modda çekildiği anlaşılmaz.")

# 6) ısıtma: tek konuşma kuralı ve güvenli geri düşüş
if "val conv = hazir ?: yeniKonusma(e)" not in llm:
    hata.append("Sıcak konuşma yoksa yenisi açılmıyor.")
if "shared.sicak = try { yeniKonusma(e) } catch (_: Throwable) { null }" not in llm:
    hata.append("Isıtma hatası yakalanmıyor — hazırlık çökerse çeviri de çöker.")
i = llm.find("try { conv.close() } catch")
j = llm.find("isit()", i)
if i < 0 or j < 0:
    hata.append("isit() konuşma kapatıldıktan SONRA çağrılmıyor — aynı anda iki konuşma açılır.")
if "try { shared.sicak?.close() } catch" not in llm:
    hata.append("Model yenilenirken sıcak konuşma kapatılmıyor.")
if "kurulum ${l.lastKurulumMs} ms" not in cap:
    hata.append("Kurulum süresi kayda yazılmıyor — ısıtmanın kazancı ölçülemez.")

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  - " + h)
    sys.exit(1)
print("Yalnız-doğru modu testi geçti: ayar bağlı, taslak gizli, iki yedek yol ve "
      "geç cevap yolu yerinde, ısıtma tek konuşma kuralına uyuyor.")
