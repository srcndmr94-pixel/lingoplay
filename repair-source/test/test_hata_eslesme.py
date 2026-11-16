"""
Hata mesajı ile o hatayı yakalayan kontrolün ayrışmadığını sınar.

Yaşadığımız hata şuydu: sunucudan 404 gelince mesaj Türkçeleştirilip
"Model bulunamadı..." yapılıyordu. Ama translate() içindeki kontrol hâlâ
metinde "404" arıyordu. Türkçe mesajda "404" geçmediği için otomatik model
keşfi hiç çalışmadı ve uygulama her replikte aynı geçersiz modelle gidip
boşa bir sunucu turu harcadı — altyazı bu yüzden geriden geliyordu.

Bu test, 404 mesajının translate() içindeki kontrollerden en az biriyle
eşleştiğini doğrular.
"""

import os
import re
import sys

KAYNAK = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main",
                      "java", "com", "example", "altyazi", "AiTranslate.kt")

metin = open(KAYNAK, encoding="utf-8").read()
sorunlar = []

# --- 1) anlasilirHata icindeki 404 mesajini bul
m = re.search(r'404\s*->\s*"((?:[^"\\]|\\.)*)"', metin)
if not m:
    sorunlar.append("anlasilirHata icinde 404 mesaji bulunamadi")
    mesaj_404 = ""
else:
    mesaj_404 = m.group(1)

# --- 2) translate() icindeki 404 yakalama kontrolunu bul
t = re.search(r"suspend fun translate\(.*?\n    \}", metin, re.S)
if not t:
    sorunlar.append("translate() bulunamadi")
    kaliplar = []
else:
    govde = t.group(0)
    blok = re.search(r"if\s*\(\s*err\.contains\((.*?)\n\s*\)\s*\{", govde, re.S)
    if not blok:
        blok = re.search(r"if\s*\(err\.contains\(.*?\)\s*\{", govde, re.S)
    parca = blok.group(0) if blok else ""
    kaliplar = re.findall(r'err\.contains\(\s*"((?:[^"\\]|\\.)*)"', parca)

# --- 3) Mesaj, kontrollerden biriyle eslesiyor mu
if mesaj_404 and kaliplar:
    eslesen = [k for k in kaliplar if k.lower() in mesaj_404.lower()]
    if not eslesen:
        sorunlar.append(
            f'404 mesaji "{mesaj_404[:40]}..." translate() icindeki '
            f"kontrollerin hicbiriyle eslesmiyor: {kaliplar}")
elif not kaliplar:
    sorunlar.append("translate() icinde err.contains kontrolu bulunamadi")

# --- 4) Devre kesici gercekten kullaniliyor mu
# devreyiAc() baska dosyadan cagriliyor, o yuzden tum kaynaga bakiyoruz.
DIZIN = os.path.dirname(KAYNAK)
tum = ""
for d in sorted(os.listdir(DIZIN)):
    if d.endswith(".kt"):
        tum += open(os.path.join(DIZIN, d), encoding="utf-8").read()

for gerekli in ("devreKapali", "basarisizOldu", "basariliOldu", "devreyiAc"):
    if tum.count(gerekli) < 2:
        sorunlar.append(
            f"{gerekli} tanimli ama hicbir yerden cagrilmiyor")

# --- 5) Gemini secilyken dogru model alaniyla karsilastiriliyor mu
if re.search(r"auto\s*!=\s*prefs\.model\b", metin):
    sorunlar.append(
        "auto, prefs.model ile karsilastiriliyor; Gemini seciliyken dogru "
        "alan prefs.geminiModel'dir, karsilastirma activeModel ile yapilmali")

if sorunlar:
    print("Hata eslesme testi DUSTU:")
    for s in sorunlar:
        print("  ✗", s)
    sys.exit(1)

print(f'Hata eslesme testi temiz: 404 mesaji "{mesaj_404[:28]}..." '
      f"kontrollerle eslesiyor, devre kesici bagli.")
