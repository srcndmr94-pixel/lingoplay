"""Programın gerçek tarama döngüsünü sahte ekranla uçtan uca çalıştırır."""

import importlib.util
import os
import sys
import time
import os as _os
PC_YOLU = _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), "..", "pc", "lingoplay_pc.py")

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "stubs"))

import numpy as np
import mss as sahte_mss
import rapidocr_onnxruntime as sahte_ocr

spec = importlib.util.spec_from_file_location("lingoplay", PC_YOLU)
lp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lp)

# Sahte ekran: her çağrıda biraz değişen 1920 genişliğinde bir şerit
sayac = {"n": 0}


def grab(self, bolge):
    sayac["n"] += 1
    kare = np.zeros((bolge["height"], bolge["width"], 4), dtype=np.uint8)
    kare[20:60, 50:800] = 200 + (sayac["n"] % 5)
    return kare


sahte_mss._S.grab = grab


class Kutu:
    def __init__(self):
        self.metin = None

    def yaz(self, m, taslak=False):
        self.metin = m


durumlar = []
kutu = Kutu()
ayarlar = dict(lp.VARSAYILAN)
ayarlar["aralik_ms"] = 80
ayarlar["api_key"] = ""

motor = lp.Motor(ayarlar, kutu, durumlar.append)
motor.start()
time.sleep(3.5)
motor.dur()
time.sleep(0.3)

print("Ekran yakalama sayısı:", sayac["n"])
print("OCR'a giden biçim:", sahte_ocr.KONTROL)
print("Ekrana yazılan:", kutu.metin)
print("Durum satırları:", durumlar[:3])

sorun = []
if sayac["n"] < 5:
    sorun.append("döngü yeterince dönmedi")
if sahte_ocr.KONTROL.get("contiguous") is not True:
    sorun.append("OCR'a bitişik olmayan dizi gidiyor")
if sahte_ocr.KONTROL.get("kanal") != 3:
    sorun.append("OCR'a 3 kanal gitmiyor")
if kutu.metin != "Bir dakika":
    sorun.append(f"çeviri ekrana yazılmadı: {kutu.metin}")
if any("hata" in d.lower() for d in durumlar):
    sorun.append(f"hata bildirildi: {durumlar}")

print()
if sorun:
    print("BAŞARISIZ:", sorun)
    sys.exit(1)
print("Uçtan uca test geçti: ekran okundu, çeviri kutuya yazıldı, hata yok.")
