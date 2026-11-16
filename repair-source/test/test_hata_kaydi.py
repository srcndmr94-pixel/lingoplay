"""
Hata kaydının eskimesini sınar (CrashLog.kt).

Kullanıcı 6 Kasım'da aynı hata raporunu üç kez gönderdi; hepsinin tarihi
20:59'du. Hata 21:23'te düzeltilmişti ama kayıt silinmiyordu: CrashLog eski
kaydı yalnızca SÜRÜM ADI değişirse siliyordu, sürüm adı ise güncellemeler
boyunca "9.35" olarak sabit kalmıştı. Bir kez düşen hata sonsuza kadar
gösteriliyordu.

Çözüm: kurulum zamanı (lastUpdateTime) damgası. Her APK kurulumunda değişir.
"""

import os
import re
import sys

KOK = os.path.dirname(__file__)
APP = os.path.join(KOK, "..", "app")
KAYNAK = os.path.join(APP, "src", "main", "java", "com", "example", "altyazi")

crash = open(os.path.join(KAYNAK, "CrashLog.kt"), encoding="utf-8").read()
ana = open(os.path.join(KAYNAK, "MainActivity.kt"), encoding="utf-8").read()
yakala = open(os.path.join(KAYNAK, "CaptureService.kt"), encoding="utf-8").read()
duzen = open(os.path.join(APP, "src", "main", "res", "layout", "activity_main.xml"),
             encoding="utf-8").read()
gradle = open(os.path.join(APP, "build.gradle.kts"), encoding="utf-8").read()

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


# ------------------------------------------------- 1) kurulum damgası

bekle("lastUpdateTime" in crash, "kurulum zamani damgasi yok")
bekle("fun installStamp" in crash, "installStamp yok")
bekle('append("Kurulum: ")' in crash, "kurulum damgasi dosyaya yazilmiyor")
bekle("Kurulum: (.+)" in crash, "kurulum damgasi okunmuyor")
bekle("kurulum != installStamp(context)" in crash,
      "eski kurulumdan kalan hata silinmiyor")
# Sürüm adı tek başına yeterli değildi; yine de ek kontrol olarak kalabilir.
bekle("f.delete()" in crash, "eskiyen kayit silinmiyor")


# ------------------------------------------------- 2) elle silme düğmesi

bekle("btnClearError" in duzen, "hata silme dugmesi arayuzde yok")
bekle("btnClearError" in ana, "hata silme dugmesi baglanmamis")
bekle("CrashLog.clear(this)" in ana, "dugme kaydi silmiyor")


# ------------------------------------------- 3) sürüm kayda elle yazılmasın

bekle("SÜRÜM $surumAdi" in yakala,
      "kayitta surum elle yazilmis; build.gradle degisince yanlis gosterir")
bekle(re.search(r'log\("SÜRÜM \d', yakala) is None,
      "kayitta sabit sürüm numarasi kalmis")
bekle("val surumAdi" in yakala, "surum adi APK'dan okunmuyor")


# --------------------------------------------- 4) sürüm gerçekten arttı mı

kod = re.search(r"versionCode\s*=\s*(\d+)", gradle)
ad = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
bekle(kod is not None and ad is not None, "surum bilgisi bulunamadi")
if kod and ad:
    bekle(int(kod.group(1)) >= 70, f"versionCode artmamis: {kod.group(1)}")
    bekle(ad.group(1) != "9.35", f"versionName hala eski: {ad.group(1)}")


# ------------------------------------------------------------------- sonuc

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print(f"Hata kaydı testi geçti (sürüm {ad.group(1) if ad else '?'}, "
      f"kurulum damgası devrede).")


# ---------------------------- çalışma kaydını temizleme (11 Kasım isteği)
#
# Kayıt oturumlar arası birikiyordu; gönderilen dökümde hangi satırın hangi
# denemeye ait olduğu anlaşılmıyordu. Artık temizlenebiliyor.

import os as _os

_KOK = _os.path.dirname(__file__)
_APP = _os.path.join(_KOK, "..", "app", "src", "main")
_svc = open(_os.path.join(_APP, "java", "com", "example", "altyazi", "CaptureService.kt"),
            encoding="utf-8").read()
_ana = open(_os.path.join(_APP, "java", "com", "example", "altyazi", "MainActivity.kt"),
            encoding="utf-8").read()
_duzen = open(_os.path.join(_APP, "res", "layout", "activity_main.xml"),
              encoding="utf-8").read()

_eksik = []
if "fun clearLog()" not in _svc:
    _eksik.append("CaptureService.clearLog yok")
if "btnClearLog" not in _duzen:
    _eksik.append("ayarlar ekranında kaydı temizle düğmesi yok")
if "CaptureService.clearLog()" not in _ana:
    _eksik.append("düğme clearLog'u çağırmıyor")

# Eleme sebepleri kayda düşmeli: satır neden çevrilmedi görülebilsin.
if "fun dustu(" not in _svc:
    _eksik.append("eleme sebebi kayda yazılmıyor")
for _sebep in ("kendi yazımız", "altyazı değil", "ekrandakiyle aynı sayıldı", "sıra dolu"):
    if _sebep not in _svc:
        _eksik.append(f"eleme sebebi eksik: {_sebep}")

# Kayıt tek bir sahneyi tutacak kadar uzun olmalı.
import re as _re
_m = _re.search(r"while \(logLines\.size > (\d+)\)", _svc)
if not _m:
    _eksik.append("kayıt uzunluğu bulunamadı")
elif int(_m.group(1)) < 300:
    _eksik.append(f"kayıt yalnız {_m.group(1)} satır: tek sahne sığmaz")

if _eksik:
    print("Kayıt temizleme testi DÜŞTÜ:")
    for _h in _eksik:
        print("  ✗", _h)
    sys.exit(1)

print("Kayıt temizleme ve eleme sebebi testi geçti.")
