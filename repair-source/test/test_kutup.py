#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OLUMSUZLUK (kutup) KONTROLÜ — AiGuard.olumsuzlukDustu

En tehlikeli hata sınıfı anlamın tersine dönmesidir. Kullanıcının 11 Kasım
9.74 kaydından:
    "I'm not some criminal who escaped justice!"
      -> "Ben adaletten kaçan bir suçluyum!"      (olumsuzluk UÇTU)
Kullanıcı tam tersini okuyor. Eksik çeviriden çok daha kötü.

KURAL ASİMETRİK: yalnız "kaynak olumsuz + çeviride hiç olumsuzluk izi yok"
reddediliyor. Tersi normaldir ("Stay back!" -> "Yaklaşma!").

TASARIM İLKESİ: Türkçe tarafında FAZLA bulmak güvenli, AZ bulmak tehlikeli.
Az bulursak DOĞRU çevirileri reddederiz. Bu testin asıl işi o yanlış
alarmları ölçmek: aşağıdaki OLUMSUZ_CEVIRILER listesi kullanıcının gerçek
kayıtlarından toplandı ve HEPSİ olumsuz sayılmak ZORUNDA.

Kalıplar AiGuard.kt'den çalışma anında okunuyor; kalıp değişirse bu test
kendiliğinden yeni kalıbı ölçer.
"""

import os
import re
import sys

# python3 -I betik dizinini sys.path'ten çıkarıyor; yardımcıyı elle ekliyoruz.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import java_regex  # noqa: E402

KOK = os.path.dirname(os.path.abspath(__file__))
YOL = os.path.join(KOK, "..", "app", "src", "main", "java", "com",
                   "example", "altyazi", "AiGuard.kt")

with open(YOL, encoding="utf-8") as f:
    K = f.read()


KALIP_ADLARI = ("IN_OLUMSUZ", "TR_OLUMSUZ_SOZ", "TR_OLUMSUZ_EK", "TR_EMIR")

try:
    KALIP = {ad: java_regex.kalip_cikar(K, ad) for ad in KALIP_ADLARI}
except ValueError as e:
    print("BAŞARISIZ: kalıp okunamadı (AiGuard.kt değişmiş?): %s" % e)
    sys.exit(1)

# ÖNEMLİ: kalıplar JAVA'da çalıştırılıyor. Python'da "\\b"/"\\w" Unicode
# farkındadır, Java'da (Kotlin'in motoru) VARSAYILAN olarak yalnız ASCII.
# Bu testi Python regex'iyle yazmışım ve üç ürün hatası testten geçmişti:
# "\\bgöt\\b" Java'da "götürdü" içinde eşleşiyor, "\\w{2,}m[ae]z" ise
# "ölmez"i kaçırıyordu.
_ONBELLEK = {}


def _topla(istek):
    """istek: [(kalip_adi, metin), ...] -> tek Java çağrısıyla sonuçlar"""
    eksik = [(KALIP[a], m) for a, m in istek if (a, m) not in _ONBELLEK]
    if eksik:
        sonuc = java_regex.esle(eksik)
        i = 0
        for a, m in istek:
            if (a, m) in _ONBELLEK:
                continue
            esl, _, hata_m = sonuc[i]
            if hata_m:
                raise SystemExit("BAŞARISIZ: %s kalıbı derlenmedi: %s" % (a, hata_m))
            _ONBELLEK[(a, m)] = esl
            i += 1
    return [_ONBELLEK[(a, m)] for a, m in istek]


def hazirla(metinler):
    istek = []
    for m in metinler:
        for a in KALIP_ADLARI:
            istek.append((a, m))
    _topla(istek)


def _es(ad, metin):
    return _topla([(ad, metin)])[0]


def tr_olumsuz(t):
    return (_es("TR_OLUMSUZ_SOZ", t) or _es("TR_OLUMSUZ_EK", t)
            or _es("TR_EMIR", t))


def in_olumsuz(t):
    return _es("IN_OLUMSUZ", t)


def dustu(kaynak, ceviri):
    return in_olumsuz(kaynak) and not tr_olumsuz(ceviri)


hata = []

# ---------------------------------------------------------------------------
# 1) GERÇEK KAYITLARDAN olumsuz Türkçe çeviriler. Hepsi olumsuz SAYILMALI;
#    sayılmazsa doğru bir çeviri reddedilir. Kullanıcının kayıtlarında
#    ölçtüğüm 161 olumsuz çevirinin tamamı buradan türetildi.
# ---------------------------------------------------------------------------
OLUMSUZ_CEVIRILER = [
    "Hayır. Boobie'yi tanıyor musunuz?",
    "Hayır, Genel.",
    "Polis! Kıpırdama lan!",
    "Çok sağ ol, ama affetme.",
    "Sen beni Mister Prins'e satmayacaksın, anladın mı?",
    "Hak etmediğin bir üniformanın etrafında dolaşıyor.",
    "Kazanmadığınız bir üniformada dolaşıyor.",
    "Tehlikeli, donanımlı olmasa bile olduğu gibidir.",
    "Buralarda senin gibi bıyıklar gibi değiliz.",
    "Çünkü ikimiz de ölü değiliz.",
    "Hayır, çok uzun zamandır benim insanım değiller.",
    "Ve eylemi tekrarlamamayı tercih ederim.",
    "D-Daha yakın gelme!",
    "Bana bu sürüşe geri götürmediyse, şimdi tehlikede olmazdın.",
    "Bu parti hakkında bilmediğim bir parti için hazırım.",
    "Özel bir toplantı. İçeri girme!",
    "Hissettiğin gibi gelmeyebilir ama bunlar senin altın çağların.",
    "En çok değiliz. Sorumlu türler.",
    "Ayrıca, belki de ebeveynim olmadığım iyi bir şey.",
    "Mark güç alamadığı takdirde ne yapardın?",
    "Bir adım daha atma.",
    "Başka bir adım değil.",
    "Diğer viltrumitlerin heykellerini gözden geçirmeyiz, öyleyse neden onu?",
    "Şimdiye kadar maruz kalma için uygun bir tedavi bulamadık.",
    "Neden olmasın?",
    "Telia) Çünkü biz asla almazdık.",
    "[inler] Şaka yapmıyordun.",
    "Kimse bir şeyden şüphelenemez.",
    "Ve bu bir fırsat sadece geçemedik.",
    "Yakın. Ama tam olarak değil.",
    "Ve eğer bu yeterli değilse,",
    "Merak etmeyin dostlar, Venture sert bir teyze.",
    "Merak etme, arkadaşlar.",
    "Bir gün kökenlerini keşfedebilirsin. Ama sabırsız olmayın.",
    "Bana iki kez sormamı yapmayın.",
    "Sana asla zarar vermem!",
    "Bilmiyorum lan! Bana hiçbir şey söylemiyorlar!",
    "Yok, hiç duymadım onu.",
    "Hiçbir yere gitmiyor!",
    "Kimse bu kapıyı geçmiyor.",
    "Aklınızı vücudunuza geri yüklemek için başka bir yol yok mu?",
    "Olamaz? Nedenini merak ediyorum.",
    "Siz ikinizi de bir daha görmeyi ummuyorum.",
    "Unuttun, değil mi?",
    "Eğer haketmiyorsak, alamadık.",
    "Sakın bir daha yapma.",
    "Bu doğru olamaz.",
    "Dürüst olmak gerekirse, asla sormayı düşünmedim.",
    "Hiçbir dikkat çekmezsin.",
    "Şu anki halini hiçbir şey yenemez.",
    "Onu hiç duymadım.",
    "Bu senin sloganın değil mi?",
    "Artık güvende olmadığını söyledi.",
    "Yüz değil dostum!",
    "Ben aptal değilim.",
    "Ben böyle düşünmüyorum.",
    "Bunu duymak bile istemiyorum.",
    "Neden gelip almadı?",
    "Sokak satıcılarıyla başa çıkamaz mısın?",
    "Bunda mermi bile yok!",
    "O olamazdı.",
    "Görmüş olamaz.",
    "Bir şey yok.",
    # 12 Kasım kaydı: "i'd rather not." -> "Tercih etmem." DOĞRU çevirisi
    # "olumsuzluk düştü" diye reddedildi. 1. tekil geniş zaman olumsuzu.
    "Tercih etmem.",
    "Bilmem.",
    "Oraya gitmem.",
    "Bunu yapmazsın.",
    # --- ö/ü/ı/ğ ile BAŞLAYAN fiiller: Java'da "\\w" ASCII olduğu için
    # bunlar KAÇIYORDU ve doğru çeviriler "olumsuzluk düştü" diye
    # reddediliyordu. (?U) eklendi; buradaki satırlar onu koruyor.
    "O gün ölmedi.",
    "Böyle bir şey ölmez.",
    "Kimse gülmedi.",
    "Beni görmedi.",
    "Hiç üşümedi.",
    "Öğrenmedi bunu.",
    "Ölme, kal benimle.",
    "Şaşırmadım.",
    "Çalışmıyor.",
    "İnanmadım.",
    "Ümit etmiyorum.",
    "Ğ harfiyle başlamaz.",
    # "Sonsuzuz." BURAYA GİRMEZ: Türkçede olumsuzluk işareti taşımıyor ve
    # taşımaması doğru ("without" artık olumsuz sayılmıyor). Aşağıdaki
    # asimetri bölümünde yerinde duruyor.
]
hazirla(OLUMSUZ_CEVIRILER)
for c in OLUMSUZ_CEVIRILER:
    if not tr_olumsuz(c):
        hata.append("YANLIŞ ALARM (olumsuzluk var ama görülmedi): " + c)

# ---------------------------------------------------------------------------
# 2) GERÇEK TERSİNE DÖNMELER. Bunlar YAKALANMALI.
# ---------------------------------------------------------------------------
TERSINE_DONENLER = [
    ("I'm not some criminal who escaped justice!",
     "Ben adaletten kaçan bir suçluyum!"),
    ("He is not my father.", "O benim babam."),
    ("I don't want to go.", "Gitmek istiyorum."),
    ("Nobody saw us.", "Herkes bizi gördü."),
    ("She never called.", "O aradı."),
    ("We can't do this.", "Bunu yapabiliriz."),
]
for k, c in TERSINE_DONENLER:
    if not dustu(k, c):
        hata.append("KAÇIRILDI (anlam ters dönmüş ama yakalanmadı): %s -> %s" % (k, c))

# ---------------------------------------------------------------------------
# 3) ASİMETRİ: kaynak OLUMLU, çeviri olumsuz -> kural DOKUNMAMALI.
#    Türkçe çoğu şeyi olumsuz kurar.
# ---------------------------------------------------------------------------
ASIMETRI = [
    ("Stay back!", "Yaklaşma!"),
    ("Keep quiet.", "Sesini çıkarma."),
    ("Hold still.", "Kıpırdama."),
    ("Let go!", "Bırak, tutma!"),
    ("We are without end.", "Sonsuzuz."),
    ("Barely holding together.", "Zar zor bir arada."),
    # 9.87 — ünlem "no" olumsuzluk değil (12 Kasım 9.86 kaydı: doğru çeviri reddedildi)
    ("Wait, no! Stop, stop! He's on our side now.",
     "Dur, dur! Durun! O artık bizim tarafımızda."),
    ("No, no, no. This is perfect.", "Bu harika."),
    ("Oh no. She is here.", "Eyvah. O burada."),
    # 9.90 — "farklı" olumsuzluğu taşır (12 Kasım 9.89 kaydı: doğru çeviri reddedildi)
    ("My one little piece that ain't like the others.", "Diğerlerinden farklı tek parçam."),
]
for k, c in ASIMETRI:
    if dustu(k, c):
        hata.append("ASİMETRİ BOZULDU (olumlu kaynak reddedildi): %s -> %s" % (k, c))

# ---------------------------------------------------------------------------
# 4) ZAYIF İngilizce olumsuzlar listede OLMAMALI: sözlükle çevrilince
#    Türkçede olumsuzluk eki bırakmıyorlar.
# ---------------------------------------------------------------------------
# 4b) "no" yalnız ÜNLEMKEN sayılmaz; belirteçken ("no one", "no way", "no
#     idea") hâlâ olumsuzluktur. Kural fazla gevşerse bunlar kaçar.
for k, c in [("There is no way out.", "Bir çıkış yolu var."),
             ("No one saw us.", "Herkes bizi gördü."),
             ("I have no idea.", "Bir fikrim var."),
             ("No, I don't want it.", "Evet, istiyorum.")]:
    if not dustu(k, c):
        hata.append("KAÇIRILDI (belirteç 'no' artık sayılmıyor): %s -> %s" % (k, c))
if in_olumsuz("Wait, no! Stop."):
    hata.append("Ünlem 'no' hâlâ olumsuzluk sayılıyor.")

for z in ("without", "hardly", "barely", "scarcely", "unable"):
    if in_olumsuz("we are %s end" % z):
        hata.append("ZAYIF OLUMSUZ geri gelmiş: %s — doğru çeviriler reddedilir" % z)

# ---------------------------------------------------------------------------
# 5) Kural gerçekten bağlı mı? redSebebi içinde çağrılıyor olmalı.
# ---------------------------------------------------------------------------
if "olumsuzlukDustu(kaynak, taslak)" not in K:
    hata.append("olumsuzlukDustu yzGerekli'ye BAĞLI DEĞİL — taslak anlamı "
                "ters çevirdiğinde yapay zekâ çağrılmaz.")
if "olumsuzlukDustu(kaynak, y)" not in K:
    hata.append("olumsuzlukDustu redSebebi'ne BAĞLI DEĞİL — kural ölü kod.")
if '"olumsuzluk düştü"' not in K:
    hata.append("red sebebi dizgisi yok.")

if hata:
    print("BAŞARISIZ:")
    for h in hata:
        print("  - " + h)
    sys.exit(1)

print("Kutup testi geçti: %d gerçek olumsuz çeviride yanlış alarm yok, "
      "%d tersine dönme yakalandı, asimetri korunuyor."
      % (len(OLUMSUZ_CEVIRILER), len(TERSINE_DONENLER)))
