"""10.7 — Anahtarsız Google Çeviri: sıra, düşüş, ara verme, cevap çözme, ayar."""
import json, os, re, sys
K = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "java", "com", "example", "altyazi")
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "layout", "activity_main.xml")
g = open(os.path.join(K, "GoogleCeviri.kt"), encoding="utf-8").read()
cap = open(os.path.join(K, "CaptureService.kt"), encoding="utf-8").read()
pr = open(os.path.join(K, "Prefs.kt"), encoding="utf-8").read()
ma = open(os.path.join(K, "MainActivity.kt"), encoding="utf-8").read()
xml = open(R, encoding="utf-8").read()
hata = []

def iste(metin, parca, ne):
    if parca not in metin:
        hata.append("Eksik: " + ne)

# Adres ve parametreler (anahtar YOK)
iste(g, '"https://translate.googleapis.com/translate_a/single"', "Google Çeviri adresi")
for p in ('"client", "gtx"', '"sl", "en"', '"tl", "tr"', '"dt", "t"', '"q", kaynak'):
    iste(g, p, "sorgu parametresi " + p)
if re.search(r"key|apiKey|activeKey", g.split("object GoogleCeviri")[1]):
    hata.append("Google Çeviri bir anahtar kullanıyor; anahtarsız olmalı.")
# Zaman aşımı ve ara verme
m = re.search(r"const val ZAMAN_ASIMI_MS = (\d+)L", g)
if not m or not (800 <= int(m.group(1)) <= 2500):
    hata.append("Google zaman aşımı makul değil (0,8–2,5 sn).")
iste(g, "if (ardArdaHata >= ARA_HATA_SAYISI)", "üst üste hatada ara verilmiyor (engellenirse her satır bekler)")
iste(g, "if (!kullanilabilir) return null", "ara süresinde yine deneniyor")
iste(g, "synchronized(hafiza) { hafiza[kaynak] }?.let { return it }", "aynı satır yeniden soruluyor")
iste(g, "addQueryParameter", "metin adrese kodlanmadan ekleniyor")

# Sıra: Google -> Firefox -> yerleşik
i_g = cap.find("val gSonuc = if (google && OfflineFix.gucluFn == null) GoogleCeviri.cevir(parca) else null")
i_f = cap.find("val ffSonuc = if (ff != null && ff.hazir) ff.cevir(parca) else null")
if not (0 <= i_g < i_f):
    hata.append("Taslak sırası Google -> Firefox değil.")
iste(cap, "if (gSonuc != null && yerTutucu.all { gSonuc.contains(it) }) {", "Google adı (yer tutucuyu) bozarsa Firefox'a düşülmüyor")
# İnternet yolunda Google açıkken Gemini çağrılmaz
m2 = re.search(r"if \(prefs\.googleCeviri \|\| prefs\.activeKey\.isBlank\(\) \|\| engine == null\) \{(.*?)\n            return\n        \}", cap, re.S)
if not m2 or "fastTranslateKeepingName(source)" not in m2.group(1):
    hata.append("Google Çeviri açıkken internet yolu yine Gemini'ye (anahtar/kota) gidiyor.")
elif cap.find("val better = try { engine.translate(source)") < cap.find("if (prefs.googleCeviri || prefs.activeKey.isBlank()"):
    hata.append("Gemini isteği Google kararından önce yapılıyor.")
iste(cap, "if (prefs.googleCeviri) log(GoogleCeviri.durum())", "kayıtta Google durumu yok")

# 10.10: Google'a ham cümle gider; argo/küfür kalkanı atlanır, düşerse eski yol
of = open(os.path.join(K, "OfflineFix.kt"), encoding="utf-8").read()
m3 = re.search(r"private suspend fun mlTranslate\(text: String(?:, naturalFirst: Boolean = false)?\): String \{(.*?)\n    \}", of, re.S)
if not m3:
    hata.append("mlTranslate bulunamadı.")
else:
    g_i = m3.group(1).find("gucluFn?.let")
    k_i = m3.group(1).find("ProfanityShield.shield(")
    if not (0 <= g_i < k_i):
        hata.append("Google'a giden cümle önce küfür kalkanından geçiyor (deyimler bozuluyor).")
    if "k.slots.all { r.contains(it.token) }" not in m3.group(1):
        hata.append("Google adı kaybedince eski yola düşülmüyor.")
# OPUS is opt-in; when disabled Google is still the primary strong translation
# function. When OPUS is enabled, Google must remain the explicit fallback.
if not ('OfflineFix.gucluFn = if (useDeepL || prefs.opusMtEnabled || prefs.googleCeviri)' in cap
        and 'remote ?: (if (prefs.opusMtEnabled' in cap
        and re.search(r'if \(prefs\.googleCeviri\) \{\s*GoogleCeviri\.cevir\(text\)\?\.takeIf', cap)
        and 'TranslationQualityGate.check(text, googleResult) == null' in cap):
    hata.append("Google güçlü motor veya DeepL/OPUS yedeği olarak bağlanmıyor")
iste(cap, "OfflineFix.gucluFn = null", "servis durunca güçlü motor bırakılmıyor")

# Ayar ve ekran
iste(pr, 'sp.getBoolean("googleCeviri", true)', "Google Çeviri varsayılan açık değil")
iste(xml, 'android:id="@+id/cbGoogle"', "ayar kutusu yok")
for p in ("cbGoogle = findViewById(R.id.cbGoogle)", "cbGoogle.isChecked = prefs.googleCeviri", "prefs.googleCeviri = cbGoogle.isChecked"):
    iste(ma, p, "ayar bağlantısı: " + p)

# Cevap çözme: Kotlin coz() ile aynı mantık, Google'ın gerçek cevap biçimiyle
def coz(govde):
    try:
        dis = json.loads(govde)
    except Exception:
        return None
    if not isinstance(dis, list) or not dis or not isinstance(dis[0], list):
        return None
    s = "".join(p[0] for p in dis[0] if isinstance(p, list) and p and isinstance(p[0], str)).strip()
    return s or None

ornekler = [
    ('[[["Herif polis olabilir, nereden bilelim. ","Dude could be a cop for all we know. ",null,null,10],'
     '["Beni özledin mi?","You miss me?",null,null,10]],null,"en",null,null,null,null,[]]',
     "Herif polis olabilir, nereden bilelim. Beni özledin mi?"),
    ('[[["Qx1 merhaba dedi.","Qx1 said hi.",null,null,3]],null,"en"]', "Qx1 merhaba dedi."),
    ('[null,null,"en"]', None),
    ('<html>429 Too Many Requests</html>', None),
    ('[[]]', None),
]
for govde, beklenen in ornekler:
    if coz(govde) != beklenen:
        hata.append(f"Cevap çözme yanlış: {govde[:40]} -> {coz(govde)!r}, beklenen {beklenen!r}")
# Kotlin tarafı aynı alanları kullanıyor mu
iste(g, "val parcalar = dis.optJSONArray(0)", "çözücü ilk diziyi okumuyor")
iste(g, "val tr = p.opt(0)", "çözücü parçanın ilk alanını okumuyor")
iste(g, "} catch (_: Throwable) {\n        null", "bozuk cevapta çökme koruması yok")

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  -", h)
    sys.exit(1)
print(f"Google Çeviri testi geçti: anahtarsız, sıra Google -> Firefox -> yerleşik, {len(ornekler)} cevap biçimi doğru çözüldü, Google açıkken kota kullanılmıyor.")
