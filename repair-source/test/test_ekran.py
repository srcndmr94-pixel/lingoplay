"""
Ekran yakalama kurallarını sınar (CaptureService.kt).

Kullanıcının 6 Kasım 20:59 hata raporu:
    SecurityException: Don't re-use the resultData ... Don't take multiple
    captures by invoking MediaProjection#createVirtualDisplay multiple times
    on the same instance.
        at CaptureService.configureDisplay(CaptureService.kt:453)
        at CaptureService.recoverCapture(CaptureService.kt:568)

Sebep: dönme sonrası kurtarmanın "sert" kademesi sanal ekranı serbest bırakıp
yeniden kurmaya çalışıyordu. Android buna izin vermiyor; üstelik o ana kadar
çalışan sanal ekran da yok oluyor ve yakalama kalıcı ölüyordu.

Bu test, aynı hatanın kaynağa geri dönmesini engeller.
"""

import os
import re
import sys

KAYNAK = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main",
                      "java", "com", "example", "altyazi", "CaptureService.kt")
metin = open(KAYNAK, encoding="utf-8").read()

# Yorumlar sayimi bozmasin: "createVirtualDisplay" aciklamalarda da geciyor.
kod = re.sub(r"/\*.*?\*/", "", metin, flags=re.S)
kod = re.sub(r"//[^\n]*", "", kod)

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


def govde(ad):
    """Bir fonksiyonun gövdesini kabaca alır (bir sonraki 'private fun'a kadar)."""
    m = re.search(r"private fun " + ad + r"\b", kod)
    if not m:
        return None
    sonraki = re.search(r"\n    (?:private |@Volatile |)(?:fun|val|var) ", kod[m.end():])
    return kod[m.end(): m.end() + (sonraki.start() if sonraki else 4000)]


# ------------------------------------------- 1) sanal ekran yalnız bir kez

cagri = kod.count("createVirtualDisplay")
bekle(cagri == 1,
      f"createVirtualDisplay kodda {cagri} yerde cagriliyor; yalnizca bir cagri olmali")

bekle("sanalEkraninIzni" in metin, "ayni izinle ikinci kurulumu engelleyen alan yok")

# Koruma, createVirtualDisplay cagrisindan ONCE gelmeli
yer_koruma = kod.find("projection === sanalEkraninIzni")
yer_kurulum = kod.find("createVirtualDisplay")
bekle(yer_koruma != -1, "izin karsilastirmasi yok")
bekle(yer_koruma != -1 and yer_koruma < yer_kurulum,
      "koruma createVirtualDisplay'den sonra geliyor")

# Durdurunca kilit kalkmali (yeni BASLAT yeni izin alir)
birak = govde("releaseDisplay")
bekle(birak is not None and "sanalEkraninIzni = null" in birak,
      "releaseDisplay izni sifirlamiyor")


# --------------------------------------- 2) kurtarma sanal ekrani oldurmesin

kurtar = govde("recoverCapture")
bekle(kurtar is not None, "recoverCapture bulunamadi")
if kurtar:
    bekle("configureDisplay" not in kurtar,
          "recoverCapture configureDisplay cagiriyor: ikinci kurulum riski")
    bekle(".release()" not in kurtar,
          "recoverCapture sanal ekrani release ediyor: yakalama kalici oluyor")
    bekle("displayCreated = false" not in kurtar,
          "recoverCapture displayCreated'i sifirliyor: ikinci kurulum riski")
    bekle("virtualDisplay = null" not in kurtar,
          "recoverCapture sanal ekrani birakiyor")
    # Yapmasi gerekenler
    bekle("ImageReader.newInstance" in kurtar, "kurtarma yeni okuyucu kurmuyor")
    bekle("setSurface" in kurtar, "kurtarma yuzeyi baglamiyor")


# ----------------------------------------------- 3) donme oturma beklemesi

bekle("Ölçü iki kez aynı çıkana kadar bekle" in metin or "oturdu" in metin,
      "donme sonrasi olcu oturma beklemesi yok")

# Kurtarma denemesi sayisi makul
m = re.search(r"KURTARMA_DENEMESI\s*=\s*(\d+)", metin)
bekle(m is not None, "kurtarma deneme sayisi sabiti yok")
if m:
    bekle(2 <= int(m.group(1)) <= 10, f"kurtarma deneme sayisi tuhaf: {m.group(1)}")


# ----------------------------- 4) sinif govdesinde "const val" olmaz

# Kotlin "const val"e yalnizca ust duzeyde, object'te ve companion object'te
# izin veriyor. CaptureService bir class: buradaki her const val companion
# object'in ICINDE olmali, yoksa derleme hatasi.
comp = kod.find("companion object")
for m in re.finditer(r"^\s*(?:private |internal |)const val (\w+)", kod, re.M):
    bekle(comp != -1 and m.start() > comp,
          f"const val {m.group(1)} companion object disinda (derlenmez)")


# ------------------------------------------------------------------- sonuc

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print("Ekran yakalama testi geçti (tek sanal ekran, kurtarma güvenli, const val yerinde).")
