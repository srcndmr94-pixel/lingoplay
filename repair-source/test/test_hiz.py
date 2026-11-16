"""PC sürümü oyunu kasmasın: okuma sıklığı, işlemci payı, eski ayarın düzeltilmesi."""
import importlib.util, json, os, sys, tempfile, time
PC_YOLU = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pc", "lingoplay_pc.py")
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "stubs"))
import numpy as np
import mss as sahte_mss
import rapidocr_onnxruntime as sahte_ocr

spec = importlib.util.spec_from_file_location("lingoplay", PC_YOLU)
lp = importlib.util.module_from_spec(spec); spec.loader.exec_module(lp)
sorun = []

# 1) Yavaş OCR (200 ms): okuma süresinin en az 2 katı dinlenmeli
cagri = []
class YavasOCR:
    def __init__(self, **k): self.k = k
    def __call__(self, img, use_cls=None):
        cagri.append((time.time(), use_cls)); time.sleep(0.2)
        return [([(0,0),(9,0),(9,9),(0,9)], "Hold on", 0.95)], None
lp.RapidOCR = YavasOCR
n = {"i": 0}
def grab(self, b):
    n["i"] += 1
    k = np.zeros((b["height"], b["width"], 4), dtype=np.uint8); k[5:30, 10:400] = 190 + (n["i"] % 2) * 60; return k   # beyaz (altyazı rengi), her karede değişen
sahte_mss._S.grab = grab
class Kutu:
    def yaz(self, m, taslak=False): pass
ay = dict(lp.VARSAYILAN); ay["api_key"] = ""; ay["aralik_ms"] = 50
m = lp.Motor(ay, Kutu(), lambda s: None)
if not isinstance(m.ocr, YavasOCR) or m.ocr.k.get("intra_op_num_threads") != 2:
    sorun.append(f"OCR çekirdek sınırı verilmedi: {getattr(m.ocr,'k',None)}")
m.start(); time.sleep(4.0); m.dur(); time.sleep(0.8)
okuma = len(cagri)
pay = okuma * 0.2 / 4.0
print(f"4 sn'de {okuma} okuma, işlemci payı ≈ %{pay*100:.0f}")
# 5.12: gecikme için dinlenme kısaldı. Okuma en çok 2-4 çekirdek kullandığından %65 pay, 8 çekirdekte
# işlemcinin toplamda ~%15'i eder. Bundan sıkı okumak (dinlenmesiz) ise %85'in üstüne çıkar.
if pay > 0.65: sorun.append(f"okuma çok sık: %{pay*100:.0f}")
if okuma < 3: sorun.append("okuma neredeyse durdu")
if any(u is not False for _, u in cagri): sorun.append("yön düzeltme kapatılmadı")

# 2) Eski kayıtlı ayar (120 ms) düzeltilmeli
d = tempfile.mkdtemp(); yol = os.path.join(d, "ayar.json")
json.dump({"aralik_ms": 120}, open(yol, "w"))
lp.CONFIG_PATH = yol
if lp.ayarlari_yukle()["aralik_ms"] != 350: sorun.append("eski 120 ms ayarı düzeltilmedi")
json.dump({"aralik_ms": 500}, open(yol, "w"))
if lp.ayarlari_yukle()["aralik_ms"] != 500: sorun.append("kullanıcının uzun aralığı bozuldu")

# 3) Eski RapidOCR (ayar almayan, use_cls bilmeyen) yine çalışmalı
lp.RapidOCR = sahte_ocr.RapidOCR
m2 = lp.Motor(dict(lp.VARSAYILAN), Kutu(), lambda s: None)
if m2.ocr is None: sorun.append("ayar almayan OCR kurulamadı")
if m2.oku(np.zeros((40, 400, 3), dtype=np.uint8)) != "Hold on": sorun.append("eski OCR ile okuma bozuldu")

if sorun: print("BAŞARISIZ:", sorun); sys.exit(1)
print("Hız testi geçti.")
