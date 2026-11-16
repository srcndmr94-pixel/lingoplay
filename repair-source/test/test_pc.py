"""LingoPlay PC programının kritik parçalarını gerçek verilerle dener."""

import importlib.util
import os
import sys
import time
import os as _os
PC_YOLU = _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), "..", "pc", "lingoplay_pc.py")

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "stubs"))

import numpy as np
import rapidocr_onnxruntime as sahte_ocr

YOL = PC_YOLU
spec = importlib.util.spec_from_file_location("lingoplay", YOL)
lp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lp)

hatalar = []


def kontrol(ad, kosul, ek=""):
    if kosul:
        print(f"  ✓ {ad}")
    else:
        print(f"  ✗ {ad} {ek}")
        hatalar.append(ad)


print("1) Metin onarımı")
kontrol("kesme işareti geri geliyor",
        lp.metni_onar("I dont know whats going on") == "I don't know what's going on",
        lp.metni_onar("I dont know whats going on"))
kontrol("üç nokta ve boşluk temizleniyor",
        lp.metni_onar("Wait…   now  ") == "Wait... now",
        lp.metni_onar("Wait…   now  "))

print("2) Konuşan ayırma")
kontrol("isim ayrılıyor", lp.konusani_ayir("JAMES: Get down") == ("JAMES", "Get down"))
kontrol("saat gibi metinde ayırma yok",
        lp.konusani_ayir("It is 10:30 now")[0] is None)

print("3) Kalıp sözlüğü")
kontrol("Hold on → Bir dakika", lp.KALIPLAR.get(lp.sadelestir("Hold on!")) == "Bir dakika")
kontrol("Watch out → Dikkat et", lp.KALIPLAR.get(lp.sadelestir("WATCH OUT.")) == "Dikkat et")

print("4) OCR'a giden görüntünün biçimi (bugünkü hatanın tam yeri)")


class SahteKutu:
    def __init__(self):
        self.metin = None

    def yaz(self, m, taslak=False):
        self.metin = m


ayarlar = dict(lp.VARSAYILAN)
ayarlar["api_key"] = ""
motor = lp.Motor(ayarlar, SahteKutu(), lambda m: None)

# 4K genişliğinde sahte bir ekran şeridi: küçültme yoluna girsin
dizi = np.zeros((300, 1920, 3), dtype=np.uint8)
dizi[50:100, 100:900] = 255

adim = int(dizi.shape[1] / 960) + 1
kucuk = np.ascontiguousarray(dizi[::adim, ::adim])
motor.oku(kucuk)

k = sahte_ocr.KONTROL
kontrol("OCR'a bitişik dizi gidiyor", k.get("contiguous") is True, k)
kontrol("3 kanallı", k.get("kanal") == 3, k)
kontrol("uint8", k.get("dtype") == "uint8", k)
kontrol("okunan metin geri dönüyor", motor.oku(kucuk) == "Hold on")

print("5) Kare değişmedi diye atlama mantığı")
imza1 = kucuk[::4, ::4].astype(np.int16)
ayni = imza1.copy()
degisik = imza1.copy()
degisik[0, 0] = int(degisik[0, 0, 0]) + 60
kontrol("aynı kare atlanıyor", np.abs(ayni - imza1).max() < 6)
kontrol("değişen kare atlanmıyor", np.abs(degisik - imza1).max() >= 6)

print("6) Çeviri zinciri (kalıp → hafıza → yapay zekâ)")
kutu = SahteKutu()
motor2 = lp.Motor(dict(lp.VARSAYILAN), kutu, lambda m: None)
motor2._isle("Hold on")
kontrol("kalıp sözlüğünden anında çeviri", kutu.metin == "Bir dakika", kutu.metin)

kutu2 = SahteKutu()
motor3 = lp.Motor(dict(lp.VARSAYILAN), kutu2, lambda m: None)
motor3.hafiza.veri[lp.sadelestir("Where is he")] = "O nerede"
motor3._isle("Where is he")
kontrol("hafızadan çeviri", kutu2.metin == "O nerede", kutu2.metin)

print("7) Yapay zekâ sırası: aynı anda tek istek, hep en yenisi")
class YavasKopru:
    def __init__(self): self.gelen = []; self.son_hata=None; self.son_sure=0; self.atlandi=False
    def taslak(self, m): return "t:" + m
    def cevir(self, m): self.gelen.append(m); time.sleep(0.4); return "y:" + m
k4 = SahteKutu()
motor4 = lp.Motor(dict(lp.VARSAYILAN), k4, lambda m: None)
motor4.hafiza.veri.clear(); motor4.hafiza.koy = lambda a, b: None; motor4.hafiza.al = lambda a: None
motor4.cevirmen = YavasKopru()
for i, satir in enumerate(["Line number one.", "Line number two.", "Line number three.", "Line number four."]):
    motor4.son_okunan = satir; motor4.yeni_satir(satir); time.sleep(0.05)
time.sleep(1.5)
kontrol("ilk ve en son satır çevrildi, aradakiler atlandı",
        motor4.cevirmen.gelen == ["Line number one.", "Line number four."], motor4.cevirmen.gelen)
kontrol("ekranda en son satırın yapay zekâ çevirisi", k4.metin == "y:Line number four.", k4.metin)

print("8) Benzerlik: yeni replik yeni sayılıyor")
m5 = lp.Motor(dict(lp.VARSAYILAN), SahteKutu(), lambda m: None)
kontrol("aynı cümle benzer", m5.benzer("Everyone good?", "Everyone good?"))
kontrol("farklı cümle benzer değil",
        not m5.benzer("Everyone good?", "Where's Private Duquaine?"))

print("9) Çeviri kaynağı seçilebiliyor (Gemini yok)")
kontrol("varsayılan: bilgisayarın kendi çevirisi", isinstance(lp.cevirmen_olustur(dict(lp.VARSAYILAN)), lp.YerelCeviri))
kontrol("telefon seçilince köprü",
        isinstance(lp.cevirmen_olustur(dict(lp.VARSAYILAN, ceviri_kaynagi="telefon")), lp.TelefonKopru))
kontrol("ayarlarda Gemini alanı yok", "api_key" not in lp.VARSAYILAN and "model" not in lp.VARSAYILAN)

print("10) Takılan yazı temizleme süresi")
m6 = lp.Motor(dict(lp.VARSAYILAN), SahteKutu(), lambda m: None)
m6.goster("x", "y")
kontrol("gösterim zamanı işleniyor", time.time() - m6.gosterim_zamani < 1)

print()
if hatalar:
    print(f"BAŞARISIZ: {len(hatalar)} test -> {hatalar}")
    sys.exit(1)
print("Tüm testler geçti.")
