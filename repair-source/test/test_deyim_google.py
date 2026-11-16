"""10.18 — Google ham girdi; deyim sadeleştirme yalnız yedek telefondaki motorda (ICU kontrolü)."""
import os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import icu_regex as I
K = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "java", "com", "example", "altyazi")
of = open(os.path.join(K, "OfflineFix.kt"), encoding="utf-8").read()
hata = []

def kural(anahtar):
    m = re.search(r'^\s*"((?:[^"\\]|\\.)*' + re.escape(anahtar) + r'(?:[^"\\]|\\.)*)" to "((?:[^"\\]|\\.)*)",', of, re.M)
    if not m:
        hata.append("Kural yok: " + anahtar); return None
    coz = lambda x: x.encode().decode("unicode_escape")
    return "(?i)" + coz(m.group(1)), coz(m.group(2))

kurallar = [k for k in (kural("stud\\\\b"), kural("back in the day"), kural("(view|sights|scenery)")) if k]

def uygula(t):
    for pat, rep in kurallar:
        r, e = I.degistir(pat, t, rep)
        if e: hata.append(f"ICU derleme hatası: {pat} {e}"); return t
        t = r
    return t

ornekler = [
    ("Well, Brian was quite the stud back in the day.", "Well, Brian was quite the womanizer back then."),
    ("He was a stud in college.", "He was a womanizer in college."),
    ("Yeah, let's take in the view.", "Yeah, let's enjoy the view."),
    ("We should take in the sights.", "We should enjoy the sights."),
    # dokunulmamalı
    ("I lost my earring stud.", "I lost my earring stud."),
    ("Take the view from the hill.", "Take the view from the hill."),
    ("The day is back in our hands.", "The day is back in our hands."),
]
for giris, beklenen in ornekler:
    cikis = uygula(giris)
    if cikis != beklenen:
        hata.append(f"{giris!r} -> {cikis!r}, beklenen {beklenen!r}")

m = re.search(r"gucluFn\?\.let \{ g ->(.*?)\n        \}", of, re.S)
if not m or "val k = NameShield.shield(GoogleSourcePreparation.prepare(text))" not in m.group(1):
    hata.append("Google yoluna özgün İngilizce gönderilmiyor.")
elif "SlangNormalizer.normalize" in m.group(1) or "Slang.normalize" in m.group(1):
    hata.append("Google yolunda İngilizce hâlâ yeniden yazılıyor.")
elif re.search(r"g\(\s*ProfanityShield|val k\s*=\s*ProfanityShield", m.group(1)):
    hata.append("Google'a kaynak metin küfür kalkanıyla değiştirilerek gönderiliyor.")
# Google başarısız olduğunda eski motorun normalleştirme ve küfür davranışı korunmalı.
if 'val norm = SlangNormalizer.normalize(Slang.normalize(GoogleSourcePreparation.prepare(text)))' not in of:
    hata.append("Yerleşik motorun mevcut argo geri dönüşü kaybolmuş.")

if "ladies' man" in uygula("quite the stud"):
    hata.append("'ladies' man' Google'da 'bayan bir erkek' oluyor.")
cap = open(os.path.join(K, "CaptureService.kt"), encoding="utf-8").read()
ts = open(os.path.join(K, "TranslationStore.kt"), encoding="utf-8").read()
if not re.search(r"if \(prefs\.hizliHafizaSurumu != TranslationStore\.HIZLI_SURUM\) \{\s*val eski = store\?\.count\(\) \?: 0\s*store\?\.clear\(\)\s*prefs\.hizliHafizaSurumu = TranslationStore\.HIZLI_SURUM", cap):
    hata.append("Kural değişince eski taslak hafızası silinmiyor (yanlış çeviri hafızadan geri geliyor).")
if "storeAi?.clear()" in cap.split("hizliHafizaSurumu")[1][:400]:
    hata.append("Sürüm yenilemesi yapay zekâ hafızasını da siliyor.")
m = re.search(r"const val HIZLI_SURUM = (\d+)", ts)
if not m or int(m.group(1)) < 3:
    hata.append("HIZLI_SURUM artırılmamış.")

if hata:
    print("BAŞARISIZ:")
    for h in hata: print("  -", h)
    sys.exit(1)
print(f"Ham-Google ve yedek-motor testi geçti (ICU): {len(ornekler)} örnek; 'stud', 'back in the day', 'take in the view' sadeleşiyor, benzer ama farklı cümlelere dokunulmuyor.")
