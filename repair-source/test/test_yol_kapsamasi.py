"""
YOL KAPSAMASI — her çeviri yolu aynı denetimlerden geçiyor mu?

Bu betiğin varlık sebebi tekrarlayan bir hata deseni. Bir düzeltme yazıyorum,
bir yola bağlıyorum, "yapıldı" diyorum — ve başka yollarda hiç çalışmıyor:

  9.62  "Yo -> Lan olmaz" (AiGuard.hitapDuzelt) yazıldı, sekiz sürüm boyunca
        YALNIZ hafıza yoluna bağlıydı; taze yapay zekâ çıktısına hiç
        uygulanmadı. İstemdeki kural işi görüyordu, o kural kaldırılınca
        ortaya çıktı.
  9.70  API/internet modu HİÇBİR denetimden geçmiyordu: ad koruma yok,
        seslenme onarımı yok, AiGuard'ın tamamı yok (küfür düştü, küfür
        eklendi, ad bozuldu, içerik düştü). Üstelik denetimsiz çıktı
        hafızaya da yazılıyordu.

Ortak nokta: KATMAN DOĞRU, BAĞLANTI EKSİK. Bu betik bağlantıyı sayıyor.

Kural: yapay zekâdan gelen her metin, ekrana yazılmadan önce
  (a) yzDuzelt'ten  -> ad koruma + seslenme onarımı
  (b) AiGuard.redSebebi'nden  -> ölçülebilir bozulma denetimi
geçmek zorunda. Yeni bir yol eklenirse bu test onu da ister.
"""

import os
import re
import sys

KOK = os.path.dirname(os.path.abspath(__file__))
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")
yakala = open(os.path.join(KAYNAK, "CaptureService.kt"), encoding="utf-8").read()
kopru = open(os.path.join(KAYNAK, "PhoneBridge.kt"), encoding="utf-8").read()

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


def yorumsuz(kod):
    kod = re.sub(r"/\*(?:.|\n)*?\*/", " ", kod)
    kod = re.sub(r"//[^\n]*", "", kod)
    return kod


gov = yorumsuz(yakala)


# ------------------------------------------------- 1) tek kapı var ve kullanılıyor

bekle("private fun yzDuzelt(" in gov,
      "yzDuzelt yok: yapay zekâ çıktısı için tek kapı kalmamış")
bekle("AiGuard.hitapDuzelt(kaynak, NameFix.koru(kaynak, ham.trim()))" in gov,
      "yzDuzelt ad koruma ve seslenme onarımını birlikte yapmıyor")

# YAPAY ZEKÂ yollarında ad koruma tek başına çağrılmamalı; yoksa seslenme
# onarımı atlanır. İki meşru istisna var ve ikisi de aşağıda ayrıca
# denetleniyor:
#   yzDuzelt içindeki  NameFix.koru(kaynak, ...)   -> kapının kendisi
#   taslak yolundaki   NameFix.koru(body, ...)     -> yapay zekâ değil,
#       çeviri motoru çıktısı; "Lan" üretmesi beklenmiyor, karşılığı
#       sözlükteki "yo = Hey" ile zaten sabit.
tek_basina = re.findall(r"NameFix\.koru\((?!kaynak,|body,)(\w+)", gov)
bekle(not tek_basina,
      "%d yerde NameFix.koru yapay zekâ yolunda doğrudan çağrılıyor (%s): "
      "o yol seslenme onarımını atlar"
      % (len(tek_basina), ", ".join(sorted(set(tek_basina)))))


# --------------------------- 2) yapay zekâ çıktısı üreten HER yol kapıdan geçiyor

# Yapay zekâ metni üreten çağrılar. Yeni bir motor eklenirse buraya da
# eklenmeli; eklenmezse aşağıdaki sayı denetimi uyarır.
URETEN = [
    ("telefon yapay zekâsı (doğruluk yolu)", r"l\.translate\("),
    ("telefon yapay zekâsı (hız yolu)",      r"l\.translate\("),
    ("API / internet motoru",                r"engine\.translate\("),
    ("TV köprüsü",                           r"PhoneBridge\.Result\.Ok"),
]
for ad, kalip in URETEN:
    bekle(re.search(kalip, gov) is not None, "yol kayboldu: " + ad)

# Her "yapay zekâ metni" değişkeni publish'e gitmeden yzDuzelt'ten geçmeli.
# Ham çıktıyı doğrudan publish etmek yasak.
ham_yayin = re.findall(r"publish\([^,]+,\s*(better|r\.text|out)\b", gov)
bekle(not ham_yayin,
      "ham yapay zekâ çıktısı doğrudan ekrana yazılıyor (%s): denetimler atlanıyor"
      % ", ".join(sorted(set(ham_yayin))))

# Hafızaya da denetlenmiş metin yazılmalı.
ham_hafiza = re.findall(r"remember\(source,\s*better\b", gov)
bekle(not ham_hafiza,
      "denetimsiz yapay zekâ çıktısı hafızaya yazılıyor: bozuk kayıt kalıcı olur")

# redSebebi, yapay zekâ üreten yol sayısı kadar çağrılmalı.
sebep_sayisi = len(re.findall(r"AiGuard\.(?:redSebebi|yetersizMi)\(", gov))
bekle(sebep_sayisi >= 4,
      "AiGuard denetimi yalnız %d yerde çağrılıyor; yapay zekâ metni üreten "
      "yol sayısı daha fazla (bir yol denetimsiz)" % sebep_sayisi)

yz_duzelt_sayisi = len(re.findall(r"yzDuzelt\(", gov))
bekle(yz_duzelt_sayisi >= 5,
      "yzDuzelt yalnız %d yerde: tanım + dört yol beklenir" % yz_duzelt_sayisi)


# ------------------------------------------- 3) köprü tarafı da aynı katmanları uygular

bekle("NameFix.koru(req.line, TurkishFix.duzelt(t))" in yorumsuz(kopru),
      "TV köprüsü telefon tarafında ad koruma + Türkçe düzeltmeden geçmiyor")


# ------------------------------------------- 4) taslak yolu katmanlarını koruyor

bekle("NameFix.koru(body, TurkishFix.duzelt(candidate))" in gov
      and "AiGuard.hitapDuzelt(body" in gov,
      "taslak yolu ad koruma + Türkçe düzeltmeden geçmiyor")


# ---------------------------------------------------------------- sonuç

if hata:
    print("Yol kapsaması DÜŞTÜ:")
    for h in hata:
        print("  ✗ " + h)
    sys.exit(1)

print("Yol kapsaması temiz (%d yapay zekâ denetimi, %d yzDuzelt çağrısı, "
      "ham çıktı yayını yok)." % (sebep_sayisi, yz_duzelt_sayisi))
