"""OCR bozulursa program sessiz kalmamalı ve döngü ölmemeli."""
import importlib.util, os, sys, time
import os as _os
PC_YOLU = _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), "..", "pc", "lingoplay_pc.py")
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "stubs"))
import numpy as np, mss as sahte_mss, rapidocr_onnxruntime as sahte_ocr

spec = importlib.util.spec_from_file_location("lingoplay", PC_YOLU)
lp = importlib.util.module_from_spec(spec); spec.loader.exec_module(lp)

def grab(self, bolge):
    k = np.zeros((bolge["height"], bolge["width"], 4), dtype=np.uint8)
    k[20:60, 50:800] = np.random.randint(190, 255)   # beyaz yazı (ön elemeden geçsin)
    return k
sahte_mss._S.grab = grab

class BozukOCR:
    def __call__(self, img): raise ValueError("bicim hatasi")
lp.RapidOCR = BozukOCR

durumlar = []
class Kutu:
    def yaz(self, m, taslak=False): pass
motor = lp.Motor(dict(lp.VARSAYILAN, aralik_ms=80), Kutu(), durumlar.append)
motor.start()
time.sleep(1.2)
# Hata sonrasında OCR döngüsü hâlâ çalışıyor olmalı; durdurmadan önce doğrula.
dongu_hayatta = motor.is_alive()
motor.dur()
motor.join(timeout=2.0)

bildirildi = any("OCR hatası" in d for d in durumlar)
print("Durum satırı örneği:", durumlar[0] if durumlar else "(yok)")
print("Döngü OCR hatasından sonra hayatta:", dongu_hayatta)
if not bildirildi:
    print("BAŞARISIZ: OCR hatası kullanıcıya bildirilmedi")
if not dongu_hayatta:
    print("BAŞARISIZ: OCR hatası ana döngüyü durdurdu")
sys.exit(0 if bildirildi and dongu_hayatta else 1)
