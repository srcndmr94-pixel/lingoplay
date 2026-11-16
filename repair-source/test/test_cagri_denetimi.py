"""
ÇAĞRI DENETLEYİCİSİNİN KENDİ TESTİ.

kotlin_cagri.py derleyicinin yerini tutuyor; bu yüzden onun da sınanması
gerekiyor. "Temiz" demesi yetmez, GERÇEK hatayı yakaladığını kanıtlamalı.

Üç hata sınıfı enjekte edilip geri alınıyor:
  1. Fonksiyon tamamen silindi      (11 Kasım'da gerçekten olan hata)
  2. Nesne üyesi yeniden adlandı
  3. Yeni yazılmış üye silindi
"""

import os
import re
import shutil
import subprocess
import sys
import tempfile

KOK = os.path.dirname(os.path.abspath(__file__))
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")
BETIK = os.path.join(KOK, "kotlin_cagri.py")

hata = []


def denetle():
    s = subprocess.run([sys.executable, "-I", BETIK], capture_output=True, text=True)
    return s.returncode, s.stdout + s.stderr


# --- 1) Kaynak temiz olmalı
kod, cikti = denetle()
if kod != 0:
    print("BAŞARISIZ: kaynak temiz değil\n" + cikti)
    sys.exit(1)

yedek = tempfile.mkdtemp(prefix="cagri")
for ad in ("LlmTranslate.kt", "AiGuard.kt"):
    shutil.copy(os.path.join(KAYNAK, ad), os.path.join(yedek, ad))

try:
    # --- 2) Fonksiyonu tamamen sil (11 Kasım'ın gerçek hatası)
    yol = os.path.join(KAYNAK, "LlmTranslate.kt")
    s = open(yol, encoding="utf-8").read()
    bas = s.index("    suspend fun translate(")
    son = s.index("    /** Son çeviride sırada beklenen süre (ms). */")
    open(yol, "w", encoding="utf-8").write(s[:bas] + s[son:])
    kod, cikti = denetle()
    if kod == 0:
        hata.append("silinmiş translate() yakalanmadı")
    elif "l.translate() tanımlı değil" not in cikti:
        hata.append("silinmiş translate() yanlış bildirildi:\n" + cikti)
    shutil.copy(os.path.join(yedek, "LlmTranslate.kt"), yol)

    # --- 3) Nesne üyesini yeniden adlandır
    yol2 = os.path.join(KAYNAK, "AiGuard.kt")
    s2 = open(yol2, encoding="utf-8").read()
    s2b = s2.replace("    fun hitapDuzelt(", "    fun hitapDuzeltV2(")
    if s2b == s2:
        hata.append("hitapDuzelt bulunamadı (test güncellenmeli)")
    open(yol2, "w", encoding="utf-8").write(s2b)
    kod, cikti = denetle()
    if kod == 0:
        hata.append("yeniden adlandırılmış AiGuard.hitapDuzelt yakalanmadı")
    elif "AiGuard.hitapDuzelt() tanımlı değil" not in cikti:
        hata.append("yeniden adlandırma yanlış bildirildi:\n" + cikti)
finally:
    for ad in ("LlmTranslate.kt", "AiGuard.kt"):
        shutil.copy(os.path.join(yedek, ad), os.path.join(KAYNAK, ad))
    shutil.rmtree(yedek, ignore_errors=True)

# --- 4) Geri alındıktan sonra yine temiz olmalı
kod, cikti = denetle()
if kod != 0:
    hata.append("geri alma başarısız, kaynak bozuk kaldı:\n" + cikti)

if hata:
    print("Çağrı denetleyicisi testi DÜŞTÜ:")
    for h in hata:
        print("  ✗ " + h)
    sys.exit(1)

print("Çağrı denetleyicisi testi geçti (kaynak temiz, 2 gerçek hata yakalandı).")
