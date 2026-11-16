"""
ÇEVİRİ KATMANLARININ SIRASI.

Kullanıcının 11 Kasım 9.61 kaydındaki asıl sorun şuydu: elle yazdığımız
karşılıklar ekrana hiç gelmiyordu, çünkü önce HAFIZA servis ediliyordu ve
hafızada bir önceki oturumun bozuk çevirisi duruyordu. Soldaki hafızanın
verdiği, sağdaki aynı satır için sözlükte YAZILI olan:

    "These two say they're for Boobie."
        hafıza  "Bu ikisi Boobie'ye ait diyorlar."
        sözlük  "Bu ikisi Boobie için geldiklerini söylüyor."
    "Yo, we're here to pick up."
        hafıza  "Lan, malı almaya geldik."          ("Lan" küfür ekliyor)
        sözlük  "Hey, malı almaya geldik."
    "Yo Ray, it's him."
        hafıza  "Hey Ray, o o."
        sözlük  "Hey Ray, işte o."

Üstelik yapay zekâ hazırken hazır sözlük BÜSBÜTÜN atlanıyordu; oysa bu
sözlükteki satırların çoğu "yapay zekâ bunu yanlış çevirdiği için" elle
yazıldı. AiGuard.yzGerekli zaten "satır tamamen kalıptan geldiyse yapay
zekâyı çağırma" diyor, yani iki katman birbiriyle çelişiyordu.

Yeni sıra: SÖZLÜK -> HAFIZA -> TASLAK -> YAPAY ZEKÂ.
Bu test o sırayı ve yanındaki üç küçük düzeltmeyi kilitliyor.
"""

import os
import re
import sys

KOK = os.path.dirname(__file__)
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")

yakala = open(os.path.join(KAYNAK, "CaptureService.kt"), encoding="utf-8").read()
denetim = open(os.path.join(KAYNAK, "AiGuard.kt"), encoding="utf-8").read()
cevrimdisi = open(os.path.join(KAYNAK, "OfflineFix.kt"), encoding="utf-8").read()

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


# ------------------------------------------------------------ 1) sıra

govde = yakala[yakala.index("private suspend fun runTranslation("):]
govde = govde[:govde.index("\n    private suspend fun ", 10)] if "\n    private suspend fun " in govde[10:] else govde[:9000]

i_sozluk = govde.find("OfflineFix.lookup(")
i_hafiza = govde.find("storeAi?.get(source)")
bekle(i_sozluk >= 0, "runTranslation sözlüğe hiç bakmıyor")
bekle(i_hafiza >= 0, "runTranslation hafızaya hiç bakmıyor")
bekle(0 <= i_sozluk < i_hafiza,
      "SIRA BOZUK: hafıza sözlükten önce okunuyor (bozuk kayıt elle yazılmış karşılığı eziyor)")

# Hazır sözlük yapay zekâ hazırken atlanmamalı: "llmReady" koşulu sözlük
# satırının üstünde olmamalı.
bekle("lookupUserOnly" not in yakala and "lookupUserOnly" not in cevrimdisi,
      "lookupUserOnly geri gelmiş: yapay zekâ hazırken hazır sözlük yine atlanıyor")
sozluk_satiri = govde[govde.rfind("\n", 0, i_sozluk) + 1:govde.find("\n", i_sozluk)]
bekle("llmReady" not in sozluk_satiri,
      "sözlük adımı yine llmReady koşuluna bağlanmış: " + sozluk_satiri.strip())

# Sözlükte karşılığı olan satır için bayat hafıza kaydı silinmeli.
bekle(re.search(r"dictHit\?\.let\s*\{.*?forget\(source\)", govde, re.S) is not None,
      "sözlük tuttuğunda çelişen hafıza kaydı silinmiyor (sözlüğü değiştirmek işe yaramaz)")


# --------------------------------------------------- 2) seslenme onarımı

bekle("fun hitapDuzelt(" in denetim, "AiGuard.hitapDuzelt yok")
bekle(re.search(r"hitapDuzelt\(kaynak,\s*NameFix\.koru\(", denetim) is not None,
      "yapay zekâ cevabı seslenme onarımından geçmiyor (onayla)")
bekle("AiGuard.hitapDuzelt(source" in yakala,
      "hafızadan gelen çeviri seslenme onarımından geçmiyor (eski 'Lan' kayıtları kalır)")

# Nazik seslenmelerin hepsi listede olmalı; kullanıcının şikâyeti "Yo- lan".
bas = denetim[denetim.index("private val HITAP_BAS"):]
bas = bas[:bas.index(".map {")]
for soz in ("look", "listen", "like", "man"):
    bekle(re.search(r'"[^"]*\b%s\b[^"]*"\s*to' % soz, bas) is not None,
          "HITAP_BAS'ta '%s' yok" % soz)
bekle('"Hey"' in bas, "HITAP_BAS'ta 'Hey' karşılığı yok")


# ------------------------------------------------- 3) y'all tekil kalmaz

kural = re.search(r'"\\\\by\'\?all\\\\b\|\\\\bya\'\?ll\\\\b"\s*to\s*"([^"]*)"', cevrimdisi)
bekle(kural is not None, "y'all kuralı kayboldu")
if kural:
    bekle(kural.group(1) == "you",
          "y'all -> '%s': 'you all' çeviri motorunda 'Hepiniz' olup fiili tekil "
          "bırakıyor ('Hepiniz Boobie'yi biliyor musun?')" % kural.group(1))
# Çokluk bilgisi yapay zekâ yolunda ipucuyla korunmalı.
argo = open(os.path.join(KAYNAK, "Slang.kt"), encoding="utf-8").read()
bekle("PLURAL" in argo, "y'all çokluk ipucu Slang.hints'ten düşmüş")


# ----------------------------------- 4) yalnız addan oluşan satır düşmez

bekle(re.search(r"fun yalnizAdMi\(", denetim) is not None,
      "yalnizAdMi erişilebilir değil")
bekle("!AiGuard.yalnizAdMi(source)" in yakala,
      "'Raymond, Raymond.' gibi yalnız adlık satır yine 'taslak başarısız' sayılıyor")
bekle("if (isName(core)) return core + punct" in cevrimdisi,
      "tek adlık cümle ('Errnesto.') yine çeviri motoruna gidiyor")


# ------------------------------- 5) kırıntı kuralı gerçek kelimeyi saymaz

bekle("KISA_KELIMELER" in yakala, "KISA_KELIMELER listesi yok")
kisa = yakala[yakala.index("private val KISA_KELIMELER"):]
kisa = kisa[:kisa.index(")")]
for k in ("it", "is", "on", "my", "up", "we", "to", "no"):
    bekle('"%s"' % k in kisa, "KISA_KELIMELER'de '%s' yok" % k)
kirinti = re.search(r"kirinti >= (\d+) && kirinti \* 2 >= kelimeler\.size", yakala)
bekle(kirinti is not None,
      "kırıntı kuralında mutlak alt sınır yok: 3 kelimelik satır iki kırıntıyla düşer")
if kirinti:
    bekle(int(kirinti.group(1)) >= 3, "kırıntı alt sınırı 3'ten küçük")
bekle("sade !in KISA_KELIMELER" in yakala,
      "kırıntı sayımı yine yalnız uzunluğa bakıyor ('Put it on speakerphone.' düşer)")


# ---------------------------------------------------------------- sonuç

if hata:
    print("Katman testi DÜŞTÜ:")
    for h in hata:
        print("  ✗ " + h)
    sys.exit(1)

print("Katman testi geçti: sıra SÖZLÜK -> HAFIZA -> TASLAK -> YZ, "
      "seslenme onarımı iki yolda da devrede.")

# 10.42.7: Yo is checked by production Kotlin policy, NOT by the old HITAP table.
bekle("YoContext10427.correct(kaynak, ceviri)" in koruma if "koruma" in globals() else "YoContext10427.correct(kaynak, ceviri)" in denetim, "Yeni bağlamsal Yo kuralı bağlı değil")
