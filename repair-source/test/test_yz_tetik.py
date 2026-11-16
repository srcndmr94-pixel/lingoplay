"""
Yapay zekâ NE ZAMAN çağrılmalı: taslak nereden geldiğine göre.

11 Kasım 26 dakikalık kaydı (YZ her satırda KAPALI) net bir desen gösterdi:

  Yapay zekâ ELLE YAZILMIŞ kalıpları BOZUYOR
      "Appreciate it."  kalıp "Sağ ol."        YZ "Teşekkürler."
      "Right inside."   kalıp "Hemen içeride." YZ "İçeride."
      "Pick up."        kalıp "Alacağız."      YZ "Mal almak."
  Yapay zekâ ÇEVİRİ MOTORU çıktısını DÜZELTİYOR
      "you know, think-tank stuff"  motor "Bilirsin, TANKLAR"
      "Move, move, move!"           motor "TAŞI, Taşı, Taşı!"
      "And you guys got our room sorted?"  motor "odamızda SIRALADINIZ mı?"

Eski ölçüt "kaynak 9+ kelimeyse çağır"dı; oyun diyaloğunun çoğu kısa olduğu
için 15 bozuk taslak denetimsiz geçti. Yeni ölçüt tek ve kaynağa dayalı:
satırın tamamı kalıptan karşılanıyorsa dokunma, yoksa yapay zekâ baksın.
"""

import os
import re
import sys

KOK = os.path.dirname(__file__)
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")

koruma = open(os.path.join(KAYNAK, "AiGuard.kt"), encoding="utf-8").read()
cevrimdisi = open(os.path.join(KAYNAK, "OfflineFix.kt"), encoding="utf-8").read()

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


# --- kaynakta duruyor mu
bekle("fun tamamenKaliptanMi" in cevrimdisi, "kalıp kaynağı sınaması yok")
bekle("OfflineFix.tamamenKaliptanMi(kaynak)" in koruma,
      "tetik hâlâ kelime sayısına bakıyor")
bekle("kelimeler(kaynak).size >= 9" not in koruma,
      "eski kelime sayısı ölçütü duruyor: kısa bozuk replikler denetimsiz geçer")
bekle("mlTranslate" not in
      cevrimdisi.split("fun tamamenKaliptanMi")[1].split("private suspend fun translateSentence")[0],
      "kalıp sınaması çeviri motorunu çağırıyor: yan etkili ve yavaş")

# --- hitap sözcükleri
bekle("private val HITAP" in cevrimdisi, "hitap tablosu yok")
bekle('"man" to "dostum"' in cevrimdisi, "man -> dostum eşlemesi yok")
_blok = re.search(r"private val HITAP = mapOf\((.*?)\n\s*\)", cevrimdisi, re.S)
HITAP = dict(re.findall(r'"([^"]+)" to "([^"]+)"', _blok.group(1))) if _blok else {}
bekle(len(HITAP) >= 8, f"hitap tablosu küçük: {len(HITAP)}")
for ing in ("man", "bro", "buddy", "dude"):
    bekle(ing in HITAP, f"hitap eksik: {ing}")
# İlk parça hitap olsa bile çevrilmemeli (cümlenin öznesi olabilir)
bekle("if (i > 0 && resolved[i] == null)" in cevrimdisi,
      "hitap kuralı cümlenin ilk parçasına da uygulanıyor: 'Man is here' bozulur")

# --- sözlükte gerçekten var mı: kalıptan gelmesi beklenen satırlar
_g = re.search(r'"""(.*?)"""', cevrimdisi, re.S)
SOZ = {}
if _g:
    for satir in _g.group(1).splitlines():
        if "=" in satir:
            a, b = satir.split("=", 1)
            SOZ[re.sub(r"[^\w ]", "", a.strip().lower()).strip()] = b.strip()

_ph = open(os.path.join(KAYNAK, "Phrases.kt"), encoding="utf-8").read()
for a, b in re.findall(r'put\("([^"]+)",\s*"([^"]+)"\)', _ph):
    SOZ.setdefault(re.sub(r"[^\w ]", "", a.lower()).strip(), b)

bekle(len(SOZ) > 300, f"sözlük küçük görünüyor: {len(SOZ)}")


def anahtar(s):
    return re.sub(r"[^\w ]", "", s.lower()).strip()


# Bu satırlar elle yazılmış: yapay zekâ bunlara DOKUNMAMALI.
KALIPTAN = ["Appreciate it.", "Right inside.", "Pick up.", "Move!", "Yep.",
            "Okay.", "Alright."]
for s in KALIPTAN:
    govde = s.rstrip(".!?")
    if anahtar(govde) not in SOZ:
        hata.append(f"kalıpta olması beklenen satır sözlükte yok: {s!r}")

# Bu satırlar çeviri motorundan geliyor: yapay zekâ ÇAĞRILMALI.
MOTORDAN = [
    "you know, think-tank stuff.",
    "And you guys got our room sorted?",
    "Hey, those waitresses were all NDA'd, yeah?",
    "Some security guy just took my guns.",
    "Yeah, baby, you snooze you lose!",
]
for s in MOTORDAN:
    govde = anahtar(s.rstrip(".!?"))
    if govde in SOZ:
        hata.append(f"motordan gelmesi beklenen satır sözlükte: {s!r}")

if hata:
    print("Yapay zekâ tetik testi DÜŞTÜ:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print(f"Yapay zekâ tetik testi geçti ({len(SOZ)} kalıp, {len(HITAP)} hitap; "
      f"{len(KALIPTAN)} satır korunur, {len(MOTORDAN)} satır denetlenir).")
