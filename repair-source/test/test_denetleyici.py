"""
Statik denetleyicinin kendi sınaması.

İki tehlike var ve ikisi de sessiz:
  1. Denetleyici yanlış alarm üretirse gerçek hata kalabalıkta kaybolur.
     (11 Kasım'da 22 uyarının 22'si yanlış alarmdı; kullanıcı haklı olarak
     "bunları gerçek derleme hatası saymıyorum" dedi.)
  2. Yanlış alarmları susturmak için fazla gevşetilirse denetleyici hiçbir
     şey yakalamaz ve "temiz" çıktısı anlamsızlaşır.

Bu test ikisini birden bağlar: kaynak temiz OLMALI, enjekte edilen gerçek
hatalar ise YAKALANMALI.
"""

import os
import re
import subprocess
import sys
import tempfile

KOK = os.path.dirname(__file__)
DENETLEYICI = os.path.join(KOK, "kotlin_denetle.py")
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")
HEDEF = os.path.join(KAYNAK, "AiGuard.kt")

hata = []


def calistir():
    r = subprocess.run([sys.executable, DENETLEYICI], capture_output=True, text=True, cwd=os.path.join(KOK, ".."))
    return r.stdout + r.stderr


# --- 1) Kaynak temiz olmalı
cikti = calistir()
if "✗" in cikti:
    for satir in cikti.splitlines():
        if "✗" in satir:
            hata.append(f"yanlış alarm (gerçek hata bunun arasında kaybolur): {satir.strip()}")

# --- 2) Gerçek hatalar yakalanmalı
ORIJINAL = open(HEDEF, encoding="utf-8").read()

DENEYLER = [
    # (açıklama, bul, koy, beklenen uyarı parçası)
    ("başka fonksiyonun parametresini kullanmak",
     "    fun yetersizMi(kaynak: String, yz: String?): Boolean {\n        val y = yz?.trim() ?: return true",
     "    fun yetersizMi(kaynak: String, yz: String?): Boolean {\n        if (taslak != null) return false\n        val y = yz?.trim() ?: return true",
     "taslak"),
    ("tanımsız fonksiyon çağırmak",
     "        if (y.isBlank()) return true",
     "        if (y.isBlank()) return hicOlmayanFonksiyon()",
     "hicOlmayanFonksiyon"),
]

for aciklama, bul, koy, beklenen in DENEYLER:
    if ORIJINAL.count(bul) != 1:
        hata.append(f"deney kurulamadı ({aciklama}): çapa bulunamadı")
        continue
    try:
        open(HEDEF, "w", encoding="utf-8").write(ORIJINAL.replace(bul, koy, 1))
        c = calistir()
        if beklenen not in c:
            hata.append(f"GERÇEK HATA YAKALANMADI ({aciklama}): denetleyici artık işe yaramıyor")
    finally:
        open(HEDEF, "w", encoding="utf-8").write(ORIJINAL)

# Geri yüklendiğini doğrula
if open(HEDEF, encoding="utf-8").read() != ORIJINAL:
    hata.append("AiGuard.kt geri yüklenemedi")

if hata:
    print("Denetleyici testi DÜŞTÜ:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print(f"Denetleyici testi geçti (kaynak temiz, {len(DENEYLER)} gerçek hata yakalandı).")
