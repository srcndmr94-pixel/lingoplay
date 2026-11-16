"""
Android kaynak dosyalarını derlemeden önce denetler.

Buradaki her kontrol, gerçekten yaşadığımız bir derleme hatasından doğdu:

1. Noktalı stil adı (Lingo.Label) — Android noktadan önceki kısmı ÜST STİL
   sayar. "Lingo" diye bir stil yoksa kaynak derleyici durur.
2. Kodun aradığı bir görünüm kimliğinin düzende bulunmaması.
3. Tanımsız renk, çizim (drawable) ya da stil kullanımı.
4. Bozuk XML.
5. Manifest'in var olmayan bir temayı göstermesi.
6. Gece temasında aynı stil adının bulunmaması.
"""

import os
import re
import sys
import xml.etree.ElementTree as ET

KOK = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main")


def oku(yol):
    with open(yol, encoding="utf-8") as f:
        return f.read()


def xml_dosyalari():
    for dizin, _, dosyalar in os.walk(KOK):
        for d in dosyalar:
            if d.endswith(".xml"):
                yield os.path.join(dizin, d)


def deger_dosyalari():
    for dizin, _, dosyalar in os.walk(os.path.join(KOK, "res")):
        if os.path.basename(dizin).startswith("values"):
            for d in dosyalar:
                if d.endswith(".xml"):
                    yield os.path.join(dizin, d)


def main():
    sorunlar = []

    # --- 1) XML sağlamlığı
    sayac = 0
    for yol in xml_dosyalari():
        sayac += 1
        try:
            ET.parse(yol)
        except Exception as e:
            sorunlar.append(f"bozuk XML: {os.path.basename(yol)} -> {e}")

    # --- toplanan kaynaklar
    stiller, renkler = set(), set()
    for yol in deger_dosyalari():
        metin = oku(yol)
        stiller |= set(re.findall(r'<style name="([^"]+)"', metin))
        renkler |= set(re.findall(r'<color name="([^"]+)"', metin))

    cizimler = set()
    for alt in ("drawable", "mipmap-anydpi-v26"):
        dizin = os.path.join(KOK, "res", alt)
        if os.path.isdir(dizin):
            cizimler |= {os.path.splitext(d)[0] for d in os.listdir(dizin)}

    # --- 2) Noktalı stil adlarının üst stili tanımlı mı
    hazir_onekler = ("Theme", "Widget", "TextAppearance", "ThemeOverlay",
                     "Base", "Platform", "Animation", "Shape")
    for ad in sorted(stiller):
        if "." not in ad:
            continue
        ust = ad.rsplit(".", 1)[0]
        if ust in stiller or ad.startswith(hazir_onekler):
            continue
        sorunlar.append(
            f'stil "{ad}" noktali: Android ust stil olarak "{ust}" ariyor, '
            f"o tanimli degil. Ya noktayi kaldirin ya da parent yazin.")

    # --- 3) Her ekranin kimlikleri kendi duzeninde var mi
    kod_dizini = os.path.join(KOK, "java", "com", "example", "altyazi")
    ekranlar = []
    for d in sorted(os.listdir(kod_dizini)):
        if not d.endswith(".kt"):
            continue
        kod = oku(os.path.join(kod_dizini, d))
        m = re.search(r"setContentView\(R\.layout\.(\w+)\)", kod)
        if not m:
            continue
        duzen_dosya = os.path.join(KOK, "res", "layout", m.group(1) + ".xml")
        if os.path.isfile(duzen_dosya):
            ekranlar.append((d, kod, duzen_dosya))

    for ad_kt, kod, duzen_dosya in ekranlar:
        duzen = oku(duzen_dosya)
        istenen = set(re.findall(r"R\.id\.(\w+)", kod))
        mevcut = set(re.findall(r'android:id="@\+id/(\w+)"', duzen))
        for ad in sorted(istenen - mevcut):
            sorunlar.append(
                f"{ad_kt}: aranan kimlik {os.path.basename(duzen_dosya)} "
                f"icinde yok: {ad}")

    duzen_yolu = os.path.join(KOK, "res", "layout", "activity_main.xml")
    kod_yolu = os.path.join(kod_dizini, "MainActivity.kt")

    # --- 3b) Duzende kisa yoldan yazilmis ozel gorunum var mi
    # ".RegionView" gibi bir yazim calisma aninda cokmeye yol acar: Android
    # noktayi UYGULAMA KIMLIGINE gore cozer, sinif orada degildir.
    for dizin, _, dosyalar in os.walk(os.path.join(KOK, "res")):
        for d in sorted(dosyalar):
            if not d.endswith(".xml"):
                continue
            for etiket in re.findall(r"<(\.[\w.]+)", oku(os.path.join(dizin, d))):
                sorunlar.append(
                    f"{d}: ozel gorunum kisa yoldan yazilmis ({etiket}); "
                    "tam paket adi yazilmali")

    # --- 4) Düzen ve temalarda kullanılan kaynaklar tanımlı mı
    for yol in list(xml_dosyalari()):
        metin = oku(yol)
        kisa = os.path.basename(yol)
        for ad in set(re.findall(r"@color/(\w+)", metin)):
            if ad not in renkler:
                sorunlar.append(f"{kisa}: tanimsiz renk @color/{ad}")
        for ad in set(re.findall(r"@drawable/(\w+)", metin)):
            if ad not in cizimler:
                sorunlar.append(f"{kisa}: tanimsiz cizim @drawable/{ad}")
        for ad in set(re.findall(r'"@style/(Lingo\w*)"', metin)):
            if ad not in stiller:
                sorunlar.append(f"{kisa}: tanimsiz stil @style/{ad}")

    # --- 4b) Koddaki tip ile duzendeki bilesen uyusuyor mu
    # Chip yerine RadioGroup okumak gibi hatalari derlemeden once yakalar.
    UYUMLU = {
        "TextView": {"TextView", "MaterialTextView"},
        "Button": {"Button", "MaterialButton", "AppCompatButton"},
        "EditText": {"EditText", "TextInputEditText"},
        "CheckBox": {"CheckBox", "MaterialCheckBox"},
        "RadioButton": {"RadioButton", "MaterialRadioButton"},
        "RadioGroup": {"RadioGroup"},
        "SeekBar": {"SeekBar"},
        "Spinner": {"Spinner"},
        "Chip": {"Chip"},
        "ChipGroup": {"ChipGroup"},
        "LinearLayout": {"LinearLayout"},
        "ScrollView": {"ScrollView"},
        "FrameLayout": {"FrameLayout"},
    }
    if os.path.isfile(duzen_yolu) and os.path.isfile(kod_yolu):
        duzen = oku(duzen_yolu)
        kod = oku(kod_yolu)

        # kimlik -> duzendeki etiket adi (paket onekini atiyoruz)
        etiket = {}
        for m in re.finditer(r"<([\w.]+)([^>]*?)android:id=\"@\+id/(\w+)\"",
                             duzen, re.S):
            etiket[m.group(3)] = m.group(1).split(".")[-1]

        istenen_tip = {}
        for m in re.finditer(r"findViewById<(\w+)>\(R\.id\.(\w+)\)", kod):
            istenen_tip[m.group(2)] = m.group(1)
        for m in re.finditer(
                r"private lateinit var (\w+):\s*(\w+)", kod):
            ad, tip = m.group(1), m.group(2)
            if re.search(rf"{ad}\s*=\s*findViewById\(R\.id\.(\w+)\)", kod):
                kimlik = re.search(
                    rf"{ad}\s*=\s*findViewById\(R\.id\.(\w+)\)", kod).group(1)
                istenen_tip[kimlik] = tip

        for kimlik, tip in sorted(istenen_tip.items()):
            gercek = etiket.get(kimlik)
            if gercek is None:
                continue
            izin = UYUMLU.get(tip)
            if izin is None:
                continue
            if gercek not in izin:
                sorunlar.append(
                    f"{kimlik}: kod {tip} bekliyor ama duzende {gercek} var")

    # --- 5) Manifest teması
    man = oku(os.path.join(KOK, "AndroidManifest.xml"))
    m = re.search(r'android:theme="@style/([\w.]+)"', man)
    if m and m.group(1) not in stiller:
        sorunlar.append(f"manifest temasi tanimsiz: {m.group(1)}")

    # --- 6) Gece teması
    gece = os.path.join(KOK, "res", "values-night", "themes.xml")
    if m and os.path.isfile(gece):
        if f'name="{m.group(1)}"' not in oku(gece):
            sorunlar.append(f"gece temasinda {m.group(1)} tanimli degil")

    if sorunlar:
        print("Kaynak denetimi SORUNLU:")
        for s in sorted(set(sorunlar)):
            print("  ✗", s)
        return 1

    print(f"Kaynak denetimi temiz ({sayac} XML, {len(stiller)} stil, "
          f"{len(renkler)} renk, {len(cizimler)} cizim).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
