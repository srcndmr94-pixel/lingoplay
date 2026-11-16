"""10.8 — Hafıza dolunca tamamı değil, en eski %10 silinir; sık kullanılan kalır.
Ayrıca telefon yapay zekâsı yüklenince ilk konuşma hazırlanır, kaynak ölçümü kayda yazılır."""
import os, re, sys
K = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "java", "com", "example", "altyazi")
ts = open(os.path.join(K, "TranslationStore.kt"), encoding="utf-8").read()
llm = open(os.path.join(K, "LlmTranslate.kt"), encoding="utf-8").read()
cap = open(os.path.join(K, "CaptureService.kt"), encoding="utf-8").read()
hata = []

put = re.search(r"fun put\(english: String, turkish: String\) \{(.*?)\n    \}", ts, re.S).group(1)
if "clear()" in put:
    hata.append("Hafıza dolunca hâlâ TAMAMI siliniyor.")
if "sp.all" in put.replace("if (adet < 0) adet = sp.all.size", ""):
    hata.append("Her yazışta bütün hafıza okunuyor (yavaş).")
for parca, ne in (("if (adet > MAX_ENTRIES) budaEnEski()", "dolunca budama yok"),
                  ("sortedBy { (gunler[it] as? Int) ?: 0 }", "en eski kayıt seçilmiyor"),
                  ("val silinecek = sirali.take(BUDAMA)", "budama sayısı uygulanmıyor"),
                  ("if (zaman.getInt(key, 0) != bugun) zaman.edit().putInt(key, bugun).apply()", "okunan kayıt tazelenmiyor")):
    if parca not in ts:
        hata.append("Eksik: " + ne)
mx = int(re.search(r"const val MAX_ENTRIES = (\d+)", ts).group(1))
bd = int(re.search(r"const val BUDAMA = (\d+)", ts).group(1))
if not (0 < bd < mx // 2):
    hata.append("Budama miktarı makul değil.")

# Algoritmanın aynası: 6000 farklı replik, her gün 50 yeni; ilk 20 replik her gün tekrar geçiyor.
sp, zaman, adet = {}, {}, -1
def get(k, gun):
    if k in sp:
        if zaman.get(k, 0) != gun: zaman[k] = gun
        return sp[k]
def put_(k, v, gun):
    global adet
    if adet < 0: adet = len(sp)
    yeni = k not in sp
    sp[k] = v; zaman[k] = gun
    if yeni: adet += 1
    if adet > mx:
        sirali = sorted(sp, key=lambda x: zaman.get(x, 0))
        for x in sirali[:bd]:
            del sp[x]; zaman.pop(x, None)
        adet = len(sirali) - bd
sayac = 0
for gun in range(1, 121):
    for i in range(20): get(f"sik{i}", gun) or put_(f"sik{i}", "x", gun)
    for _ in range(50):
        put_(f"r{sayac}", "y", gun); sayac += 1
if len(sp) < mx - bd or len(sp) > mx:
    hata.append(f"Budamadan sonra kayıt sayısı yanlış: {len(sp)}")
if not all(f"sik{i}" in sp for i in range(20)):
    hata.append("Her gün geçen replikler budamada silindi.")
if "r0" in sp:
    hata.append("En eski replik hâlâ duruyor.")
if adet != len(sp):
    hata.append("Sayaç gerçek kayıt sayısıyla tutmuyor.")

if "fun hazirla()" not in llm or "if (l.ready) l.hazirla()" not in cap:
    hata.append("Telefon yapay zekâsı yüklenince ilk konuşma hazırlanmıyor.")
if "log(kaynakDurumu())" not in cap or "nextKaynakMs = nowMs + KAYNAK_ARALIK_MS" not in cap:
    hata.append("Uzun oturumda kaynak ölçümü kayda yazılmıyor.")
for p in ("BATTERY_CHANGED", "EXTRA_TEMPERATURE", "getNativeHeapAllocatedSize"):
    if p not in cap:
        hata.append("Kaynak ölçümünde eksik: " + p)

if hata:
    print("BAŞARISIZ:")
    for h in hata: print("  -", h)
    sys.exit(1)
print(f"Hafıza budama testi geçti: 120 günde {sayac} replik, kalan {len(sp)} (sınır {mx}); her gün geçen 20 replik korundu, en eskiler silindi.")
