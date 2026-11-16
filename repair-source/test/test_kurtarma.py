"""
EKRAN DÖNDÜKTEN SONRA YAKALAMANIN KURTARILMASI.

Kullanıcının 11 Kasım 9.64 kaydı bu oturumun en ciddi sorunuydu: ekran
döndükten sonra HİÇ kare gelmedi, uygulama 45 saniyeden fazla sıfır satır
çevirdi ve kullanıcı servisi elle yeniden başlatmak zorunda kaldı.

    Ekran döndü: 2142x960 · yakalama 2142x960
    Döndükten sonra kare yok, okuyucu yenileniyor (1/2)
    TANI: kare 0 (boş 44) ...
    Döndükten sonra kare yok, okuyucu yenileniyor (2/2)
    Döndükten sonra hâlâ kare yok. Ekran duruyorsa bu normal; ...
    TANI: kare 0 (boş 191) ...
    TANI: kare 0 (boş 338) ...
    TANI: kare 0 (boş 484) ...        <- hiç toparlanmadı

Kurtarma iki deneme sonra KALICI olarak vazgeçiyordu. "Ekran duruyorsa bu
normal" gerekçesi burada yanlış çıktı: kullanıcı yeniden başlattığında AYNI
YÖNDE anında çalıştı, yani sanal ekran gerçekten bozulmuştu.

Yeni davranış: denemeler bitmiyor, yalnızca seyreliyor (2,5 sn, 5 sn, ...
en çok 30 sn) ve ilk üçten sonra kayda yazılmıyor. Duran ekranda kare
gelmemesi hâlâ normal; bedeli neredeyse sıfır, karşılığı kendiliğinden
toparlanma.
"""

import os
import re
import sys

KOK = os.path.dirname(__file__)
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")
yakala = open(os.path.join(KAYNAK, "CaptureService.kt"), encoding="utf-8").read()

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


# Kurtarma bloğunu bul.
i = yakala.find("if (rotatedAtMs > 0 && framesSinceRotate == 0")
bekle(i >= 0, "dönme sonrası kurtarma bloğu yok")
blok = yakala[i:i + 1600] if i >= 0 else ""

# 1) Deneme sayısına BAĞLI bir üst sınır olmamalı: koşul yalnız zamana bakmalı.
bekle("reattachTries < KURTARMA_DENEMESI" not in blok,
      "kurtarma yine deneme sayısıyla sınırlı: iki denemeden sonra kalıcı vazgeçiyor")
bekle("reattachTries == KURTARMA_DENEMESI" not in blok.split("log(")[0],
      "kurtarma koşulu yine deneme sayısına bakıyor")

# 2) Denemeler arası süre büyümeli ve bir üst sınırı olmalı (sonsuz sıklıkta
#    yenileme pil yakar).
bekle(re.search(r"sonKurtarmaMs\s*>\s*minOf\(", blok) is not None,
      "denemeler arası süre son denemeye göre ölçülmüyor (hep tetiklenir)")
m = re.search(r"minOf\(\s*(\d[\d_]*)L?\s*\*\s*\(reattachTries \+ 1\)\s*,\s*(\d[\d_]*)L?\s*\)", blok)
bekle(m is not None, "kurtarma aralığı kademeli değil")
if m:
    taban = int(m.group(1).replace("_", ""))
    ust = int(m.group(2).replace("_", ""))
    bekle(taban >= 2000, "ilk kurtarma aralığı çok kısa (%d ms): boşuna yenileme" % taban)
    bekle(10_000 <= ust <= 120_000,
          "kurtarma üst aralığı makul değil (%d ms): çok sık pil yakar, çok seyrek toparlanmaz" % ust)

# 3) Kayıt gürültüsü sınırlı olmalı: her denemede satır yazmasın.
bekle("reattachTries <= KURTARMA_DENEMESI + 1" in blok,
      "her kurtarma denemesi kayda yazılıyor: 45 saniyede kayıt dolar")

# 4) "Bu normal" cümlesi artık tek başına kalmamalı; denemenin sürdüğü
#    söylenmeli, yoksa kullanıcı yine elle yeniden başlatır.
bekle("seyrekleşerek sürecek" in blok or "sürecek" in blok,
      "kullanıcıya denemelerin sürdüğü söylenmiyor")

# 5) Durum alanı dönmede ve durdurmada sıfırlanmalı.
bekle("sonKurtarmaMs = rotatedAtMs" in yakala,
      "dönme anında son kurtarma zamanı sıfırlanmıyor: ilk deneme hemen tetiklenir")
bekle(re.search(r"reattachTries = 0; sonKurtarmaMs = 0L", yakala) is not None,
      "servis durdurulurken sonKurtarmaMs sıfırlanmıyor")

# 6) ÜST SINIR olmalı. Sınırsız deneme kendi gözden geçirmemde kusurlu
#    çıktı: duran ekranda her 30 saniyede ImageReader yeniden kuruluyor,
#    sanal ekran iki kez boyutlanıyordu — saatte ~120 gereksiz kurulum.
bekle("KURTARMA_UST_SINIR" in yakala,
      "kurtarma denemesi sınırsız: duran ekranda sonsuza kadar okuyucu yeniliyor")
bekle("reattachTries < KURTARMA_UST_SINIR" in blok,
      "üst sınır kurtarma koşulunda kullanılmıyor")
m2 = re.search(r"KURTARMA_UST_SINIR = (\d+)", yakala)
bekle(m2 is not None, "üst sınır değeri okunamadı")
if m2:
    n = int(m2.group(1))
    bekle(10 <= n <= 40,
          "kurtarma üst sınırı makul değil (%d): az olursa toparlanmaz, "
          "çok olursa boşuna kurulum" % n)
bekle("Kurtarma denemeleri durduruldu" in yakala,
      "denemeler sessizce susuyor: kullanıcı neden çalışmadığını bilemez")

# 7) recoverCapture ilk denemede düz, sonrakilerde zorlayarak çalışmalı.
bekle("recoverCapture(zorla = reattachTries > 1)" in blok,
      "kurtarma hep aynı yöntemi deniyor")


if hata:
    print("Kurtarma testi DÜŞTÜ:")
    for h in hata:
        print("  ✗ " + h)
    sys.exit(1)

print("Kurtarma testi geçti: dönme sonrası yenileme vazgeçmiyor, "
      "aralık kademeli ve kayıt gürültüsü sınırlı.")
