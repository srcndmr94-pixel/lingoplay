#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
İnternet modu: düşünme ayarı modele göre seçiliyor mu?

Kullanıcının 12 Kasım 9.87 kaydında internet modundaki HER istek
"400: Request contains an invalid argument." ile döndü. İstek gövdesi her
modele sabit {"thinkingBudget": 0} gönderiyordu; bu alan yalnız Gemini 2.x
ailesinde geçerli, 3.x "thinkingLevel" istiyor.

Bu test canlı sunucuya GİDEMEZ (burada ağ ve anahtar yok). Baktığı şey,
kodun tek bir ayara bağlı kalmadığı: 400 gelince sıradaki ayarı deniyor,
tutanı hatırlıyor, başka hatalarda boşuna yeniden denemiyor.
"""
import os
import re
import sys

KOK = os.path.dirname(os.path.abspath(__file__))
K = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")
s = open(os.path.join(K, "AiTranslate.kt"), encoding="utf-8").read()
cap = open(os.path.join(K, "CaptureService.kt"), encoding="utf-8").read()
hata = []

a = s.find("private fun requestGemini")
b = s.find("private fun geminiIstek")
govde = s[a:b]
kod = "\n".join(l for l in govde.splitlines() if not l.strip().startswith("//"))

# 1) üç ayar da var ve ayrı gövde üretiyor
for parca, ne in (('.put("thinkingBudget", 0)', "Gemini 2.x ayarı"),
                  ('.put("thinkingLevel", "minimal")', "Gemini 3.x ayarı"),
                  ('else -> uretim.put("maxOutputTokens", 2048)', "ayarsız gövde")):
    if parca not in kod:
        hata.append("Eksik: " + ne + " (" + parca + ")")
if kod.count("thinkingBudget") != 1 or kod.count("thinkingLevel") != 1:
    hata.append("thinkingBudget ve thinkingLevel birer kez, AYRI dallarda olmalı; "
                "ikisi aynı istekte giderse sunucu 400 döner.")
m = re.search(r"DUSUNME_BUTCE -> (.*?)DUSUNME_SEVIYE -> (.*?)else ->", kod, re.S)
if not m or "thinkingLevel" in m.group(1) or "thinkingBudget" in m.group(2):
    hata.append("Düşünme dalları karışmış.")

# 2) sıra: 2.x'te bütçe, diğerlerinde seviye başta; ikisinde de üç ayar
m = re.search(r'startsWith\("gemini-2"\).*?listOf\((.*?)\).*?listOf\((.*?)\)', kod, re.S)
if not m:
    hata.append("dusunmeSirasi bulunamadı.")
else:
    eski = [x.strip() for x in m.group(1).split(",")]
    yeni = [x.strip() for x in m.group(2).split(",")]
    if eski[0] != "DUSUNME_BUTCE" or yeni[0] != "DUSUNME_SEVIYE":
        hata.append("İlk denenen ayar yanlış: 2.x -> bütçe, 3.x -> seviye olmalı.")
    if sorted(eski) != sorted(yeni) or len(set(eski)) != 3:
        hata.append("Her model için ÜÇ ayar da denenmeli (tahmin tutmazsa çıkış yolu).")

# 3) yalnız 400'de sıradaki ayar; tutan hatırlanıyor; bozulan unutuluyor
for parca, ne in (
    ('if (lastError?.startsWith("400") != true) return null',
     "400 dışındaki hatada (kota, anahtar, bağlantı) yeniden deneme olmamalı"),
    ("Usage.dusunmeAyari[model] = ayar", "tutan ayar hatırlanmıyor — her satır üç istek atar"),
    ("if (bilinen != null) Usage.dusunmeAyari.remove(model)",
     "hatırlanan ayar 400 vermeye başlarsa unutulmuyor"),
    ("val sira = if (bilinen != null) listOf(bilinen) else dusunmeSirasi(model)",
     "hatırlanan ayar kullanılmıyor"),
):
    if parca not in kod:
        hata.append("Eksik: " + ne)

# 4) hata iletisi modeli ve ayarı söylüyor; kayıt başlığı modeli yazıyor
if '" · model=" + model + " · düşünme=" + ayar' not in kod:
    hata.append("400 iletisinde model/ayar yok — bir sonraki kayıttan sebep okunamaz.")
if '" · model:" + (if (prefs.provider == Prefs.PROVIDER_GEMINI) prefs.geminiModel else prefs.model)' not in cap:
    hata.append("Kayıt başlığı internet modunda modeli yazmıyor.")

# 5) Kotlin sabitleri tanımlı
for ad in ("DUSUNME_BUTCE", "DUSUNME_SEVIYE", "DUSUNME_YOK"):
    if ("const val " + ad + " = ") not in s:
        hata.append("Sabit tanımsız: " + ad)

# ---------------------------------------------------------------------------
# 6) 9.89 — model reddedilince: hata silinmiyor, AYNI model yeniden seçilmiyor
#    (12 Kasım 9.88 kaydı: "boş yanıt" / "Sebep: bilinmiyor", model gemini-2.5-flash)
# ---------------------------------------------------------------------------
prefs = open(os.path.join(K, "Prefs.kt"), encoding="utf-8").read()
for parca, ne in (
    ("val asilHata = lastError", "asıl hata model listesi istenmeden ÖNCE saklanmıyor"),
    ("siraliAdaylar(fetchModels(key)).filter { it != activeModel }.take(3)",
     "reddedilen model adaylardan çıkarılmıyor — aynı model yeniden seçilir, hiçbir şey denenmez"),
    ('if (lastError == null) lastError = "sunucudan çeviri alınamadı · model=" + activeModel',
     "sebep boş kalabiliyor"),
):
    if parca not in s:
        hata.append("Eksik: " + ne)
m = re.search(r'const val DEFAULT_GEMINI_MODEL = "([^"]+)"', prefs)
if not m or "preview" in m.group(1) or not re.match(r"^gemini-\d+(\.\d+)?-flash(-lite)?$", m.group(1)):
    hata.append("Varsayılan Gemini modeli kararlı bir flash modeli olmalı (önizleme adları kapatılıyor).")

# Sıralama kuralının aynası: kalıplar kaynaktan okunuyor.
mk = re.search(r'KARARLI_FLASH = Regex\("([^"]+)"\)', s)
mi = re.search(r'ISE_YARAMAZ =\s*Regex\("([^"]+)"\)', s)
if not mk or not mi:
    hata.append("Sıralama kalıpları bulunamadı.")
else:
    KARARLI = re.compile(mk.group(1).replace("\\\\", "\\"))
    YARAMAZ = re.compile(mi.group(1))

    def surum(a):
        x = KARARLI.match(a)
        return float(x.group(1)) if x else 0.0

    def sirala(models):
        kararli = [a for a in models if KARARLI.match(a) and surum(a) >= 3.0]
        lite = sorted([a for a in kararli if a.endswith("-lite")], key=surum, reverse=True)
        flash = sorted([a for a in kararli if not a.endswith("-lite")], key=surum, reverse=True)
        eski = sorted([a for a in models if KARARLI.match(a) and surum(a) < 3.0], key=surum, reverse=True)
        kalan = [a for a in models if a.startswith("gemini") and "flash" in a and not YARAMAZ.search(a)]
        out = []
        for a in lite + flash + eski + kalan:
            if a not in out:
                out.append(a)
        return out

    # Google'ın Kasım 2026 kapatma sayfasındaki gerçek adlar
    LISTE = ["gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite",
             "gemini-3.8-flash-tts", "gemini-3.8-live", "gemini-3.1-flash-image",
             "gemini-3-flash-preview", "gemini-3.1-flash-lite", "gemini-3.5-flash",
             "gemini-3.5-flash-lite", "gemini-3.6-flash", "gemini-3.7-flash",
             "gemini-3.8-flash", "gemini-embedding-2", "gemini-3.1-pro-preview"]
    sira = sirala(LISTE)
    if sira[:2] != ["gemini-3.5-flash-lite", "gemini-3.1-flash-lite"]:
        hata.append("En yeni lite başta değil: %s" % sira[:3])
    if sira[2] != "gemini-3.8-flash":
        hata.append("Lite'lardan sonra en yeni flash gelmeli: %s" % sira[:4])
    if sira.index("gemini-2.5-flash") < sira.index("gemini-3.5-flash"):
        hata.append("2.x, 3.x'in önüne geçmiş (2.5 listede görünüp isteği reddediyor).")
    for kotu in ("gemini-3.8-flash-tts", "gemini-3.8-live", "gemini-3.1-flash-image",
                 "gemini-embedding-2", "gemini-2.5-pro"):
        if kotu in sira:
            hata.append("Altyazı çeviremeyecek model aday olmuş: " + kotu)
    # kullanıcının durumu: 2.5-flash reddedildi -> ilk üç aday içinde o yok, 3.x var
    aday = [a for a in sira if a != "gemini-2.5-flash"][:3]
    if "gemini-2.5-flash" in aday or not aday or not aday[0].startswith("gemini-3"):
        hata.append("Reddedilen modelden sonra denenecek adaylar yanlış: %s" % aday)
    # kod aynayla aynı mı (mantık koruması)
    for parca in ("(lite + flash + eski + kalan).distinct()",
                  'kararli.filter { it.endsWith("-lite") }.sortedByDescending { surum(it) }',
                  "KARARLI_FLASH.matches(it) && surum(it) >= 3.0"):
        if parca not in s:
            hata.append("siraliAdaylar değişmiş, ayna bayat: " + parca)

# ---------------------------------------------------------------------------
# 7) 9.90 — iyelik eki ad bozulması sayılmaz; 429 her zaman günlük kota değil
# ---------------------------------------------------------------------------
ag = open(os.path.join(K, "AiGuard.kt"), encoding="utf-8").read()
if not re.search(r"for \(\(i, ham\) in kelimeler\(kaynak\)\.withIndex\(\)\) \{.*?"
                 r"val k = ham\.substringBefore\('\\''\)\.substringBefore\('’'\)", ag, re.S):
    hata.append("Ad denetimi \"Liu's\" ekini atmıyor — \"Çünkü Liu geliyor.\" yine reddedilir.")


def ad_govdesi(k):
    return k.split("'")[0].split("’")[0]


for ham, beklenen in (("Liu's", "Liu"), ("Nolan’s", "Nolan"), ("Viltrumites", "Viltrumites")):
    if ad_govdesi(ham) != beklenen:
        hata.append("ad gövdesi yanlış: %s" % ham)
if 'mesaj.contains("PerDay", ignoreCase = true)' not in s or "İstek sınırı aşıldı (429)" not in s:
    hata.append("429 iletisi dakikalık sınırı günlük kotadan ayırmıyor.")

# 8) 9.92 — zaman aşımı devre kesiciyi tetiklemez (bir dakikalık taslak dönemi)
# 9.95: tek tük zaman aşımı uzun devreyi (60 sn) açmaz; yalnız 3 ardışık olanı kısa ara (30 sn) verir.
m8 = re.search(r'private fun basarisizOldu\(sebep: String\?\) \{(.*?)\n        \}\n', s, re.S)
if not m8 or "ardArdaZamanAsimi++" not in m8.group(1) or \
        m8.group(1).find("ardArdaZamanAsimi++") > m8.group(1).find("ardArdaHata++"):
    hata.append("Zaman aşımı uzun devreyi açıyor: iki geciken cevap bir dakika taslak demek.")

# 9) 9.94 — zaman aşımı iletisi süreyi, modeli ve türü söylüyor; "timeout"
#    kelimesi başta kalıyor (devre kesici istisnası ona bakıyor).
if 'lastError = "timeout · $tur · ${gecen} ms · model=$model"' not in s:
    hata.append("Zaman aşımı iletisi süre/model/tür içermiyor ya da 'timeout' ile başlamıyor.")
if "val basla = android.os.SystemClock.elapsedRealtime()" not in s:
    hata.append("Gemini isteğinin süresi ölçülmüyor.")

# 10) 9.95 — internet gecikince telefon devreye giriyor; üst üste zaman aşımı kısa ara veriyor
cap2 = open(os.path.join(K, "CaptureService.kt"), encoding="utf-8").read()
for parca, ne in (
    ("telefonaVerildi.compareAndSet(false, true)", "telefon iki kez çağrılabiliyor"),
    ("val telefonHazir = !hizli && telefon != null && telefon.ready", "Hızlı biçimde de telefon çağrılır"),
    ("refineOnDevice(telefon!!, source)", "telefon yapay zekâsı çağrılmıyor"),
):
    if parca not in cap2:
        hata.append("Eksik: " + ne)
m10 = re.search(r"delay\(taslakBekleme\)(.*?)\} else null\s*val internetSonucu = .*?val better = try \{ engine\.translate\(source\)", cap2, re.S)
if not m10 or "telefonaVer()" not in m10.group(1):
    hata.append("Gecikme yedeği telefonu başlatmıyor.")
m11 = re.search(r'log\("YAPAY ZEKÂ BAŞARISIZ: .*?if \(mode != Prefs\.MODE_HYBRID\) \{(.*?)\n        \}', cap2, re.S)
if not m11 or "telefonaVer()" not in m11.group(1) or "if (shownSource != source)" not in m11.group(1):
    hata.append("İnternet başarısız olunca telefona verilmiyor ya da taslak telefonun cevabını eziyor.")
for parca, ne in (
    ("if (ardArdaZamanAsimi >= 3) {", "üst üste zaman aşımı devreyi hiç açmıyor"),
    ("devreSuresiMs = ZAMAN_ASIMI_DEVRE_MS", "zaman aşımı arası kısa değil"),
    ("const val ZAMAN_ASIMI_DEVRE_MS = 30_000L", "kısa ara 30 sn değil"),
    ("< Usage.devreSuresiMs", "devre süresi değişkene bağlı değil"),
    ('sebep.startsWith("503")', "503 yoğunluk hatası ara vermiyor"),
):
    if parca not in s:
        hata.append("Eksik: " + ne)

# 11) 9.98 — telefon internetle AYNI ANDA başlar; internet önce gelirse telefon üstüne yazmaz
i_bas = cap2.find("if (mode != Prefs.MODE_HYBRID) telefonaVer()")
i_bekle = cap2.find("delay(taslakBekleme)")
i_istek = cap2.find("val better = try { engine.translate(source)")
if not (0 <= i_bas < i_bekle < i_istek):
    hata.append("Telefon internetle birlikte başlamıyor (önce bekliyor).")
if "internetGeldi.add(source)" not in cap2 or "if (internetGeldi.remove(source)) {" not in cap2:
    hata.append("Telefonun geç sonucu internet çevirisinin üstüne yazılabiliyor.")

# 12) 9.99 — asıl model gecikirse ikinci ücretsiz modele yedek istek
for parca, ne in (
    ("val (first, anaHata) = ikiliIste(text, activeModel, key)", "çeviri yedekli istekten geçmiyor"),
    ("withTimeoutOrNull(yedekBekle)", "yedek istek beklemeden ya da hiç gönderilmiyor"),
    ("if (sonuc.isCompleted) { bitir(null); return@launch }", "asıl cevap geldiyse de yedek gönderiliyor (kota israfı)"),
    ("val r = withTimeoutOrNull(ISTEK_UST_SINIR_MS) { sonuc.await() }", "toplam bekleme sınırsız"),
    ("lastError = anaHata ?: lastError", "yedeğin 404'ü asıl modeli değiştirtebilir"),
    ("private val istekKapsami = CoroutineScope(SupervisorJob() + Dispatchers.IO)",
     "istekler çağıranın kapsamında: geç dönen cevap ekranı bekletir"),
    ("kotuYedek.add(yedek)", "olmayan yedek model her satırda yeniden denenir"),
    (".firstOrNull { it != ana && it !in kotuYedek }", "yedek asıl modelle aynı olabilir"),
):
    if parca not in s:
        hata.append("Eksik: " + ne)
m12 = re.search(r"const val YEDEK_ISTEK_MS = (\d+)L", s)
if not m12 or not (500 <= int(m12.group(1)) <= 1000):
    hata.append("YEDEK_ISTEK_MS makul değil (500-1000 ms): asıl modelin hızlı cevapları 0,7-1,1 sn.")
if "@Volatile var anaHata" in s:
    hata.append("Yerel değişkende @Volatile: Kotlin derlemez.")

# 13) 10.0/10.42.7 — internet yolu varken kaba taslak hafıza kullanılmaz.
# 198'den itibaren bağlama duyarlı replikler YZ hafızasından da atlanıyor:
# eski test yalnız `if (!TranslationCachePolicy...)` biçimini beklediği için
# yeni ek `modelFirst && dependsOnContext` koşulunu yanlışlıkla hata sayıyordu.
if "val yzYoluVar = !hizli && (mode != Prefs.MODE_INSTANT || llmReady)" not in cap2:
    hata.append("İnternet/yerel yapay zekâ yolu algılanmıyor.")
else:
    memory_start = cap2.find("val remembered = ", cap2.find("val yzYoluVar = "))
    memory_end = cap2.find("remembered?.let", memory_start)
    memory_code = cap2[memory_start:memory_end] if memory_start >= 0 and memory_end >= 0 else ""
    if not all(part in memory_code for part in (
        "!TranslationCachePolicy10427.reusable(source)",
        "else if (yzYoluVar) storeAi?.get(source)",
        "else (storeAi?.get(source) ?: if (prefs.googleCeviri) null else store?.get(source))",
        "NaturalTranslationPolicy10427.dependsOnContext(source)",
    )):
        hata.append("İnternet modunda eski bozuk taslaklar hafızadan basılıyor, yapay zekâya gidilmiyor.")

# 14) 10.4 — kota koruma: yedek yalnız takılma döneminde erken; taslak yeterliyse internete gidilmez
for parca, ne in (
    ("withTimeoutOrNull(yedekBekle)", "yedek bekleme uyarlanmıyor"),
    ("YEDEK_ISTEK_MS else YEDEK_ISTEK_SAKIN_MS", "sakin dönemde de 0,7 sn'de yedek: kota iki kat hızlı biter"),
    ("Usage.sonTakilmaMs = System.currentTimeMillis()", "takılma zamanı tutulmuyor"),
):
    if parca not in s:
        hata.append("Eksik: " + ne)
# 10.5: taslak yeterli görünse de satır yapay zekâdan geçmeli (10.4 atlaması kaliteyi düşürdü)
if "kota korunuyor" in cap2 or "AiGuard.yzGerekli(source, anlik)" in cap2:
    hata.append("İnternet yolunda taslak yeterli diye YZ atlanıyor: 10.4'te çeviri kalitesini düşürdü.")

# 15) 10.6 — kotası biten model gün boyu atlanır, yedek beklemeden çıkar, internet durdurulmaz
for parca, ne in (
    ("if (hata.startsWith(GUNLUK_KOTA)) Usage.tukendi(model)", "kota biten model işaretlenmiyor"),
    ("if (Usage.tukenmisMi(secilen)) (yedekModel(secilen, key) ?: secilen) else secilen", "kotası biten model asıl kalıyor"),
    ("!Usage.tukenmisMi(it)", "yedek seçerken kotası biten model eleniyor değil"),
    ("withTimeoutOrNull(yedekBekle) { anaDustu.await() }", "asıl model hemen düşünce yedek yine bekliyor"),
    ("anaDustu.complete(Unit)", "asıl modelin düştüğü yedeğe bildirilmiyor"),
    ('getTimeZone("America/Los_Angeles")', "kota sıfırlama saati Google'ınkiyle (Pasifik) aynı değil"),
):
    if parca not in s:
        hata.append("Eksik: " + ne)
i_skip = s.find("if ((anaHata ?: \"\").startsWith(GUNLUK_KOTA) && yedekModel(activeModel, key) != null) return@withContext null")
i_dev = s.rfind("Usage.basarisizOldu(lastError)")
if not (0 <= i_skip < i_dev):
    hata.append("Tek modelin kotası bitince internet 60 sn durduruluyor.")

# 16) 10.6 — telefon sonucu, yolda olan internet çevirisini kısa süre bekler (ekranda 3 değişim yok)
for parca, ne in (
    ("withTimeoutOrNull(TELEFON_BEKLE_MS) { bekleyen.await() } ?: false", "telefon internet cevabını beklemiyor"),
    ("internetSonucu.complete(true)", "internet başarısı telefona bildirilmiyor"),
    ("internetSonucu.complete(false)", "internet başarısızlığı telefona bildirilmiyor: telefon boşuna bekler"),
):
    if parca not in cap2:
        hata.append("Eksik: " + ne)
m16 = re.search(r"private val TELEFON_BEKLE_MS = (\d+)L", cap2)
if not m16 or not (500 <= int(m16.group(1)) <= 1500):
    hata.append("TELEFON_BEKLE_MS makul değil (500-1500 ms).")
if cap2.count("internetSonucu.complete(") < 3:
    hata.append("İnternet sonucunun her çıkış yolunda bildirilmesi gerekir (başarı, başarısızlık, istisna).")

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  - " + h)
    sys.exit(1)
print("Düşünme ayarı testi geçti: üç ayar ayrı gövdede, 400'de sıradaki deneniyor, "
      "tutan hatırlanıyor. (Canlı sunucu burada denenemez.)")
