"""
Bilgisayar sürümünün OCR onarımını ve tekrar süzgecini sınar.

Kullanıcının 7 Kasım PC kaydındaki hatalar:
  kesme işareti "l" okunuyor
    "Youlre cute. I didnlt think"  "something Ilve never"  "welre picking"
    "actually ilm going to say no."  "Letls see how this goes."
  I -> l karışması
    "it Iooks just like him."  "Ioved it. Love, Iove, Iove"
  f -> t karışması
    "» Lefs see how this goes."
  Türkçe yazı çevriliyor (ve bozuluyor)
    "Kalite" -> "Kalit"   "Altyazıla" -> "Altınyağıla"
    "Görev gücündeki arkadaşlarımız yolda." -> "...Arkadaşlarimizz."
  aynı satır tekrar tekrar çevriliyor
    "Now Loading..." / "Novv Loading,." farklı sanılıyordu

Modül tkinter istediği için GUI modülleri sahte modüllerle değiştiriliyor.
"""

import os
import sys
import types

PC = os.path.join(os.path.dirname(__file__), "..", "pc")

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


# ------------------------------------------------- GUI modüllerini sahtele

class _Any:
    def __init__(self, *a, **k):
        pass

    def __getattr__(self, n):
        return _Any()

    def __call__(self, *a, **k):
        return _Any()


for ad in ["tkinter", "tkinter.ttk", "tkinter.font", "tkinter.messagebox",
           "tkinter.filedialog", "tkinter.scrolledtext", "tkinter.colorchooser",
           "win32api", "win32con", "win32gui", "win32clipboard", "pywintypes",
           "winreg", "pystray", "PIL", "PIL.Image", "PIL.ImageTk", "PIL.ImageGrab",
           "PIL.ImageDraw", "PIL.ImageFont", "mss", "keyboard", "pyperclip"]:
    if ad not in sys.modules:
        m = types.ModuleType(ad)
        m.__getattr__ = lambda n: _Any()
        sys.modules[ad] = m

sys.path.insert(0, PC)
try:
    import lingoplay_pc as L
except Exception as e:                                  # pragma: no cover
    print(f"BAŞARISIZ: pc modülü yüklenemedi: {type(e).__name__}: {e}")
    sys.exit(1)

bekle(len(L.KELIMELER) > 120_000, f"pc sozlugu kisa: {len(L.KELIMELER)}")


# ------------------------------------------------------- 1) OCR onarımı

DUZELMELI = [
    ("» Youlre cute. I didnlt think we Iooked",
     "» You're cute. I didn't think we looked"),
    ("» I think original, something Ilve never",
     "» I think original, something I've never"),
    ("Like, I donlt even wanna hear that shit.",
     "Like, I don't even wanna hear that shit."),
    ("Well, welre picking something up for him.",
     "Well, we're picking something up for him."),
    ("actually ilm going to say no.", "actually I'm going to say no."),
    ("» Letls see how this goes.", "» Let's see how this goes."),
    ("» Lefs see how this goes.", "» Let's see how this goes."),
    ("it Iooks just like him.", "it looks just like him."),
    ("Ioved it. Love, Iove, Iove, Iove that.", "Loved it. Love, love, love, love that."),
    ("Not such a tough quy anymore, huh?", "Not such a tough guy anymore, huh?"),
]
for girdi, beklenen in DUZELMELI:
    gelen = L.metni_onar(girdi)
    bekle(gelen == beklenen, f"onarilmadi:\n      girdi   : {girdi}\n"
                             f"      beklenen: {beklenen}\n      gelen   : {gelen}")

BOZULMAMALI = [
    "He held the weld together.", "The shell fell and the bell rang.",
    "I left the list on the land.", "We all held hands.", "She felt the cold wind.",
    "Who said that?", "The queen asked a quick question.", "Let us go to the lake.",
    "I love you too.", "» My name is Anishi.", "» Howie, what do you think?",
    "» I sell cell phones.", "» Well, I love you, too.",
    "Straight up, first door on the right.", "How you doing, big guy?",
]
for girdi in BOZULMAMALI:
    gelen = L.metni_onar(girdi)
    bekle(gelen == girdi, f"gercek metin bozuldu:\n      {girdi}\n   -> {gelen}")


# --------------------------------------------------- 2) Türkçe yazı süzgeci

bekle(hasattr(L, "turkce_yazi_mi"), "turkce suzgeci yok")
if hasattr(L, "turkce_yazi_mi"):
    for g in ["Altyazıla", "Görev gücündeki arkadaşlarımız yolda.",
              "Ayrıntılar Kaldır", "İngilizce", "Otomatik çevir",
              "ÇALIŞMA KAYDI Son hata: yok"]:
        bekle(L.turkce_yazi_mi(g), f"turkce yazi gecti: {g}")
    for g in ["How you doing, big guy?", "Straight up, first door on the right.",
              "» Thank you so much.", "Boobie, huh? Yeah, he ain't here, though.",
              "I sell cell phones."]:
        bekle(not L.turkce_yazi_mi(g), f"ingilizce replik turkce sanildi: {g}")


# ------------------------------------------------------- 3) tekrar süzgeci

sinif = next((getattr(L, a) for a in dir(L)
              if isinstance(getattr(L, a), type) and "benzer" in getattr(L, a).__dict__), None)
bekle(sinif is not None, "benzer() iceren sinif bulunamadi")
if sinif:
    benzer = sinif.benzer.__get__(object(), sinif)
    for a, b, beklenen in [
        ("Now Loading...", "Novv Loading,.", True),
        ("Now Loading...", "Now Loading_", True),
        ("Now Loading...", "Now Loadjng...", True),
        ("it's pu rest. Erdi Gozoinar", "it's rest. Erdi Gozoinar", True),
        ("» Lefs see how this goes.", "» Letls see how this goes.", True),
        ("Problem is he only worried about himself.",
         "Problem is he only worried about", True),
        ("» Thank you.", "» Thank you so much.", False),
        ("» Yeah.", "» Yes.", False),
        ("No.", "Go.", False),
        ("How did he do that?", "What?", False),
        ("» My name is Anishi.", "» Where are you from?", False),
        ("» Howie, what do you think?", "» Heidi?", False),
        ("SIMON, PLEASE.", "PLEASE SAY YES.", False),
    ]:
        bekle(benzer(a, b) == beklenen,
              f"benzerlik yanlis ({benzer(a, b)}, beklenen {beklenen}): {a[:40]!r} / {b[:30]!r}")


# ------------------------------------------------------------------- sonuc

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print(f"PC OCR onarımı testi geçti ({len(DUZELMELI)} düzeltme, "
      f"{len(BOZULMAMALI)} bozulmama, tekrar süzgeci dahil).")
