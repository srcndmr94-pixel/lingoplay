"""
DOSYALAR ARASI ÇAĞRI DENETİMİ.

Neden var: Kotlin derleyicisi bu ortamda yok. Mevcut denetimlerin hiçbiri
dosyalar arası bakmıyordu ve 11 Kasım'da şu oldu — bir düzenleme
LlmTranslate.kt içindeki `translate` fonksiyonunu tamamen sildi, ama
CaptureService.kt onu hâlâ çağırıyordu:

    35 betik      : TEMİZ
    kotlin_denetle: TEMİZ      (yalnız dosya İÇİNDE bakıyor)
    kaynak_denetle: TEMİZ      (XML/varlık denetimi)
    gerçek durum  : DERLENMİYOR

Elle kontrol ederken fark edildi. Bu betik o boşluğu kapatıyor: çağrılan her
fonksiyon bir yerde tanımlı mı?

NE DENETLER
  1. `Nesne.fonksiyon(...)` — Nesne projenin kendi object/class'ıysa,
     fonksiyon o tipte (ya da companion'ında) tanımlı olmalı.
  2. `degisken.fonksiyon(...)` — değişkenin tipi açıkça yazılmışsa
     (`val x: LlmTranslate`, `fun f(l: LlmTranslate)`) aynı denetim.
     Bugünkü hata tam buradan yakalanıyor: `refineOnDevice(l: LlmTranslate)`
     içindeki `l.translate(...)`.

NE DENETLEMEZ
  Android SDK, Kotlin stdlib ve kütüphane çağrıları. Tip çıkarımı yapmıyor;
  tipi yazılmamış değişkenlere dokunmuyor. Yanlış alarm üretmemek esas:
  gürültülü denetim, hiç denetim olmamasından kötüdür.
"""

import os
import re
import sys

KOK = os.path.dirname(os.path.abspath(__file__))
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


# --------------------------------------------------------------- 0) yardım

def yorumsuz(kod):
    """Satır yorumlarını, blok yorumlarını ve dizgeleri boşaltır."""
    kod = re.sub(r'"""(?:.|\n)*?"""', '""', kod)
    kod = re.sub(r"/\*(?:.|\n)*?\*/", " ", kod)
    kod = re.sub(r"//[^\n]*", "", kod)
    # Dizge içeriği: "Nesne.fonksiyon(" gibi metinler çağrı sanılmasın.
    kod = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', kod)
    kod = re.sub(r"'(?:\\.|[^'\\\n])'", "' '", kod)
    return kod


dosyalar = {}
for ad in sorted(os.listdir(KAYNAK)):
    if ad.endswith(".kt"):
        dosyalar[ad] = yorumsuz(open(os.path.join(KAYNAK, ad), encoding="utf-8").read())

if not dosyalar:
    print("BAŞARISIZ: Kotlin kaynağı bulunamadı")
    sys.exit(1)


# ------------------------------------------------- 1) tipler ve üyeleri

TIP_BAS = re.compile(
    r"^(?P<girinti>[ \t]*)(?:(?:private|internal|public|abstract|open|sealed|data|enum)\s+)*"
    r"(?P<tur>object|class|interface)\s+(?P<ad>[A-Z]\w*)")

# Tip -> tanımlı üye adları (fonksiyon + özellik)
uyeler = {}
# Tip -> hangi dosyada
tip_dosya = {}
# Üst düzey fonksiyonlar (her yerden çağrılabilir)
ust_duzey = set()
# Uzantı fonksiyonları: `fun Foo.bar(` -> Foo'nun üyesi sayılır
uzanti = {}


def blok_sonu(satirlar, bas, girinti):
    """Girintiye göre tipin gövdesinin bittiği satır."""
    for i in range(bas + 1, len(satirlar)):
        s = satirlar[i]
        if not s.strip():
            continue
        g = len(s) - len(s.lstrip())
        if g <= len(girinti) and re.match(r"^\s*\}", s):
            return i
    return len(satirlar)


UYE = re.compile(
    r"^\s*(?:(?:private|internal|public|protected|override|open|abstract|suspend|"
    r"inline|operator|external|final|const|lateinit|@\w+(?:\([^)]*\))?)\s+)*"
    r"(?:fun|val|var)\s+(?:<[^>]*>\s*)?(?P<ad>\w+)")

UZANTI = re.compile(
    r"^\s*(?:(?:private|internal|public|suspend|inline)\s+)*fun\s+(?:<[^>]*>\s*)?"
    r"(?P<tip>[A-Z]\w*)(?:<[^>]*>)?\??\.(?P<ad>\w+)")

for ad, kod in dosyalar.items():
    satirlar = kod.splitlines()
    for i, satir in enumerate(satirlar):
        m = TIP_BAS.match(satir)
        if not m:
            continue
        tip = m.group("ad")
        son = blok_sonu(satirlar, i, m.group("girinti"))
        bulunan = set()
        for s in satirlar[i:son]:
            mm = UYE.match(s)
            if mm:
                bulunan.add(mm.group("ad"))
        uyeler.setdefault(tip, set()).update(bulunan)
        tip_dosya.setdefault(tip, ad)
    # Üst düzey fonksiyonlar: girintisiz `fun ad(`
    for satir in satirlar:
        mu = UZANTI.match(satir)
        if mu:
            uzanti.setdefault(mu.group("tip"), set()).add(mu.group("ad"))
            continue
        m2 = re.match(
            r"^(?:(?:private|internal|public|suspend|inline)\s+)*fun\s+(?:<[^>]*>\s*)?(\w+)\s*\(", satir)
        if m2:
            ust_duzey.add(m2.group(1))
    # Üst düzey `val OfflineFix = OfflineFixEngine()` gibi tek örnekler
    for m3 in re.finditer(r"^va[lr]\s+(\w+)\s*(?::\s*(\w+))?\s*=\s*(\w+)\s*\(", kod, re.M):
        isim, yazili, ctor = m3.group(1), m3.group(2), m3.group(3)
        hedef = yazili or ctor
        if hedef in uyeler or hedef in dosyalar.keys():
            uyeler.setdefault(isim, set()).update(uyeler.get(hedef, set()))
            tip_dosya.setdefault(isim, tip_dosya.get(hedef, "?"))

for tip, adlar in uzanti.items():
    if tip in uyeler:
        uyeler[tip].update(adlar)


# --------------------------------------- 2) tipi YAZILI değişken adları

# Her dosyada: ad -> tip. Yalnız tipi açıkça yazılmış olanlar.
TIPLI = re.compile(
    r"(?:va[lr]|,|\(|^)\s*(?P<ad>\w+)\s*:\s*(?P<tip>[A-Z]\w*)\s*\??(?=[\s,)=\n])")
ATAMA = re.compile(r"va[lr]\s+(?P<ad>\w+)\s*=\s*(?P<tip>[A-Z]\w*)\s*\(")
# `val l = llm` gibi takma adlar
TAKMA = re.compile(r"va[lr]\s+(?P<ad>\w+)\s*=\s*(?P<kaynak>\w+)\s*(?:\?:|$|\n)")

# Kotlin stdlib'in her alıcıda çalışan üyeleri: tipte bulunmasa da geçerli.
HER_TIPTE = {
    "let", "also", "apply", "run", "takeIf", "takeUnless", "toString", "equals",
    "hashCode", "copy", "invoke", "javaClass", "component1", "component2",
    "to", "and", "or", "not", "compareTo", "rangeTo", "coerceIn", "coerceAtLeast",
    "coerceAtMost", "toInt", "toLong", "toFloat", "toDouble", "toString",
}


def tipli_adlar(kod):
    d = {}
    for m in TIPLI.finditer(kod):
        if m.group("tip") in uyeler:
            d[m.group("ad")] = m.group("tip")
    for m in ATAMA.finditer(kod):
        if m.group("tip") in uyeler:
            d[m.group("ad")] = m.group("tip")
    # Takma adlar: `val l = llm` -> l, llm'in tipini alır (iki tur yeter)
    for _ in range(2):
        for m in TAKMA.finditer(kod):
            k = m.group("kaynak")
            if k in d and m.group("ad") not in d:
                d[m.group("ad")] = d[k]
    return d


# --------------------------------------------------------- 3) çağrı denetimi

CAGRI = re.compile(r"(?<![\w.])(?P<alici>[A-Za-z]\w*)\s*(?:\?)?\.(?P<fon>[a-z]\w*)\s*\(")

for ad, kod in dosyalar.items():
    yerel = tipli_adlar(kod)
    for m in CAGRI.finditer(kod):
        alici, fon = m.group("alici"), m.group("fon")
        if fon in HER_TIPTE:
            continue
        # a) Projenin kendi object/class'ı üzerinden çağrı
        if alici in uyeler:
            if fon not in uyeler[alici]:
                satir = kod[:m.start()].count("\n") + 1
                hata.append(
                    "%s:%d  %s.%s() tanımlı değil (%s içinde böyle bir üye yok)"
                    % (ad, satir, alici, fon, tip_dosya.get(alici, "?")))
            continue
        # b) Tipi YAZILI değişken üzerinden çağrı
        tip = yerel.get(alici)
        if tip and fon not in uyeler.get(tip, set()):
            satir = kod[:m.start()].count("\n") + 1
            hata.append(
                "%s:%d  %s.%s() tanımlı değil (%s tipinde böyle bir üye yok, %s)"
                % (ad, satir, alici, fon, tip, tip_dosya.get(tip, "?")))


# ---------------------------------------------------------------- sonuç

if hata:
    print("Dosyalar arası çağrı denetimi DÜŞTÜ:")
    for h in hata:
        print("  ✗ " + h)
    print("\n%d tanımsız çağrı. Bu hata derlemeyi durdurur." % len(hata))
    sys.exit(1)

print("Dosyalar arası çağrı denetimi temiz (%d tip, %d üye, %d üst düzey fonksiyon)."
      % (len(uyeler), sum(len(v) for v in uyeler.values()), len(ust_duzey)))
