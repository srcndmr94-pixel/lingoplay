"""PC 5.11: bitişik okunan kelimeler açılıyor (5.10 kaydındaki gerçek satırlar), isimler/doğru cümleler bozulmuyor;
OCR hızlı algılama ayarıyla kuruluyor ve tam çözünürlüklü görüntüyü okuyor."""
import importlib.util, os, sys
PC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pc")
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "stubs")); sys.path.insert(0, PC)
spec = importlib.util.spec_from_file_location("lingoplay", os.path.join(PC, "lingoplay_pc.py"))
lp = importlib.util.module_from_spec(spec); spec.loader.exec_module(lp)
sorun = []

# 5.10 kaydında "BOZUK OKUMA" diye atlanan ya da yanlış çevrilen satırlar
ACILACAK = {
    "Yo,is Raymond around?": "Yo, is Raymond around?",
    "Yo,we're here topickup.ForBoobie.": "Yo, we're here to pickup. For Boobie.",
    "andwhatthefuckgoingon": "and what the fuck going on",
    "at thispointI reallydon't givea fuck.": "at this point I really don't give a fuck.",
    "at thispointIreally don't give a fuck.": "at this point I really don't give a fuck.",
    "YoRay,it's him.Ernesto.": "Yo Ray, it's him. Ernesto.",
    "Yo,Nesto!": "Yo, Nesto!",
    "Yo,phone.": "Yo, phone.",
    "whatareyoudoing": "what are you doing",
    "It's a beautiful daytoday.": "It's a beautiful day today.",
    "Mr.White, I'm sorry.": "Mr. White, I'm sorry.",
}
DOKUNULMAYACAK = [
    "Everything is fine.", "I'm McDonald from the U.S. at 5 a.m.", "Unbelievable!",
    "Where's Private Duquaine?", "Hello Jesse, let's cook.", "Heisenberg", "Ireland", "Iran is far.",
    "Ivan, Israel, Italy, Irene.", "They're gonna kill us.", "I don't think so.", "Wait, hello there, guys.",
    "Breaking Bad", "Oyeme, mi hermano.",
]
for ham, beklenen in ACILACAK.items():
    m = lp.metni_onar(ham)
    if m != beklenen: sorun.append(f"{ham!r} -> {m!r} (beklenen {beklenen!r})")
    if lp.bozuk_mu(m): sorun.append(f"açıldıktan sonra hâlâ bozuk: {m!r}")
for t in DOKUNULMAYACAK:
    m = lp.metni_onar(t)
    if m != t: sorun.append(f"doğru metin bozuldu: {t!r} -> {m!r}")
m = lp.metni_onar("Oyeme,mihermano,we got a situation happening.")
if "herm a" in m: sorun.append(f"yabancı kelime saçma bölündü: {m!r}")
print("Örnek:", lp.metni_onar("at thispointI reallydon't givea fuck."))

# OCR kurulumu: kısa kenarı büyütme yok (det_limit_type=max)
import rapidocr_onnxruntime as ro
gelen = []
class KayitliOCR(ro.RapidOCR):
    def __init__(self, **k): gelen.append(k)
lp.RapidOCR = KayitliOCR
lp.ocr_olustur()
if not gelen or gelen[0].get("det_limit_type") != "max" or gelen[0].get("det_limit_side_len", 9999) > 1280:
    sorun.append(f"OCR hızlı algılama ayarıyla kurulmadı: {gelen}")
if "hızlı" not in lp.OCR_BILGI: sorun.append(f"OCR bilgisi: {lp.OCR_BILGI}")

# Eski rapidocr bu ayarları tanımazsa yine kurulabilmeli
class EskiOCR(ro.RapidOCR):
    def __init__(self, **k):
        if "det_limit_type" in k: raise TypeError("bilinmeyen ayar")
lp.RapidOCR = EskiOCR
if lp.ocr_olustur() is None: sorun.append("eski OCR sürümünde kurulamadı")

# Motor OCR'a küçültülmemiş görüntü veriyor mu
import numpy as np
boyutlar = []
class Olcen(ro.RapidOCR):
    def __init__(self, **k): pass
    def __call__(self, img, **k):
        boyutlar.append(img.shape); return None, None
lp.RapidOCR = Olcen
class Kutu:
    def yaz(self, *a, **k): pass
mot = lp.Motor(dict(lp.VARSAYILAN), Kutu(), lambda s: None)
goruntu = np.zeros((90, 2200, 3), dtype=np.uint8); goruntu[30:60, 100:1500] = 255
mot.oku(np.ascontiguousarray(goruntu))
if boyutlar and boyutlar[0][1] != 2200: sorun.append(f"OCR küçültülmüş görüntü aldı: {boyutlar}")
src = open(os.path.join(PC, "lingoplay_pc.py"), encoding="utf-8").read()
if "ham = self.oku(kucuk)" in src: sorun.append("motor hâlâ küçültülmüş kareyi okuyor")

if sorun:
    print("BAŞARISIZ:"); [print(" -", s) for s in sorun]; sys.exit(1)
print("Bitişik kelime ve hızlı OCR testi geçti.")
