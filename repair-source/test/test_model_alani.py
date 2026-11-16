"""
Model alanının doğru sağlayıcıya yazıldığını sınar.

Yaşadığımız hata: uygulamada iki ayrı model alanı var — prefs.model
(Anthropic) ve prefs.geminiModel (Gemini). Arayüzdeki tek "Model" kutusu
ise hangi sağlayıcı seçili olursa olsun hep prefs.model'e yazıyordu.
Gemini seçiliyken kullanıcı modeli elle değiştiremiyordu; MODELLERİ BUL
ile doğru adı bulsa bile uygulamaya geçmiyordu ve her replikte
"Model bulunamadı" hatası alınıyordu.

Bu test, model kutusuyla ilgili okuma/yazmaların sağlayıcıya duyarlı
olduğunu doğrular.
"""

import os
import re
import sys

KAYNAK = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main",
                      "java", "com", "example", "altyazi", "MainActivity.kt")

metin = open(KAYNAK, encoding="utf-8").read()
satirlar = metin.split("\n")
sorunlar = []


def saglayiciya_duyarli(indeks, pencere=6):
    """Satirin cevresinde saglayici kontrolu var mi?"""
    bas = max(0, indeks - pencere)
    son = min(len(satirlar), indeks + pencere + 1)
    cevre = "\n".join(satirlar[bas:son])
    return "PROVIDER_GEMINI" in cevre or "PROVIDER_ANTHROPIC" in cevre


# --- 1) Kutudan prefs'e yazma saglayiciya duyarli mi
for i, satir in enumerate(satirlar):
    if re.search(r"prefs\.model\s*=\s*etModel", satir):
        sorunlar.append(
            f"satir {i + 1}: model kutusu dogrudan prefs.model'e yaziliyor; "
            "Gemini seciliyken prefs.geminiModel olmali")
    if re.search(r"etModel\.setText\(prefs\.model\)", satir):
        sorunlar.append(
            f"satir {i + 1}: kutuya hep prefs.model yaziliyor; "
            "secili saglayicinin modeli gosterilmeli")

# --- 2) Kaydetme bolumunde her iki alan da ele alinmis mi
if "prefs.geminiModel =" not in metin:
    sorunlar.append("MainActivity hicbir yerde prefs.geminiModel yazmiyor")

# --- 3) Kutuya yazarken ve okurken saglayici kontrolu var mi
for i, satir in enumerate(satirlar):
    if "etModel.setText(" in satir and "prefs." in satir:
        if not saglayiciya_duyarli(i):
            sorunlar.append(
                f"satir {i + 1}: etModel.setText cevresinde saglayici "
                "kontrolu yok")

# --- 4) Ayar kaydedilince yapay zeka devresi yeniden aciliyor mu
if "AiTranslate.devreyiAc()" not in metin:
    sorunlar.append(
        "ayarlar kaydedilince AiTranslate.devreyiAc() cagrilmiyor; "
        "kullanici modeli duzeltse bile devre kapali kalabilir")

if sorunlar:
    print("Model alani testi DUSTU:")
    for s in sorunlar:
        print("  ✗", s)
    sys.exit(1)

print("Model alani testi temiz: kutu secili saglayicinin alanina baglaniyor.")
