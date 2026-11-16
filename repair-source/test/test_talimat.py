"""
YAPAY ZEKÂ TALİMATI VE OCR ONARIMI.

Bu projede en büyük kaldıraç sistem talimatı: tek değişiklik, her satırda,
hiç görmediğimiz cümlelerde de geçerli. Sözlüğe kalıp eklemek ise yalnız o
satırda işe yarıyor. O yüzden talimatın maddeleri test altında.

Her madde kullanıcının kayıtlarında ÖLÇÜLMÜŞ bir hatadan geliyor:

  9.63 "He's alive?"  OCR "olive" okudu -> "Zeytin mi?"
        telefon talimatı modele "bu metin OCR'dan geliyor" DEMİYORDU
        (API talimatı diyordu; ikisi ayrışmıştı)
  9.63 "I beg to differ"      -> "Farklı olmak için yalvarıyorum"
  9.63 "word from my boys"    -> "kelime öldü"
  9.63 "[Batman] Vinny DeLeon, Roger Marshall," -> ekranda HİÇBİR ŞEY
  9.61 "Y'all know Boobie?"   -> "Hepiniz ... biliyor musun?" (tekil fiil)
  9.61 "Yo, we're here..."    -> "LAN, malı almaya geldik."
  9.63 "I'll see you soon."   OCR "II" okudu -> "II yakında görüşürüz."
"""

import os
import re
import sys

KOK = os.path.dirname(__file__)
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")

llm = open(os.path.join(KAYNAK, "LlmTranslate.kt"), encoding="utf-8").read()
api = open(os.path.join(KAYNAK, "AiTranslate.kt"), encoding="utf-8").read()
off = open(os.path.join(KAYNAK, "OfflineFix.kt"), encoding="utf-8").read()
koruma = open(os.path.join(KAYNAK, "AiGuard.kt"), encoding="utf-8").read()
yakala_src = open(os.path.join(KAYNAK, "CaptureService.kt"), encoding="utf-8").read()

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


# -------------------------------------------- 1) telefon tarafı talimatı

i = llm.index("private const val SYSTEM =")
blok = llm[i:llm.index("\n    }", i)]
# YORUM SATIRLARI ÇIKARILIR. Yorumların içindeki tırnaklı metin isteme
# girmiyor; sayılırsa hem uzunluk ölçümü şişer hem de "kural istemden
# çıkarıldı mı" denetimi yorum yüzünden yanlış sonuç verir.
blok = "\n".join(l for l in blok.splitlines() if not l.strip().startswith("//"))
sistem = " ".join(re.findall(r'"((?:[^"\\]|\\.)*)"', blok))

maddeler = {
    "OCR uyarısı": r"OCR",
    "anlamsız okumayı birebir çevirme": r"nonsensical|makes? no sense",
    "kelime kelime çevirme": r"meaning, not words|word by word",
    "deyim": r"[Ii]diom",
    "çokluk": r"plural",
    "yalnız ad satırı": r"only names|name list",
    "küfür korunur": r"never censor",
    "köşeli parantez": r"[Ss]quare-bracket",
    "sadece çeviri": r"ONLY the Turkish",
}
for ad, kalip in maddeler.items():
    bekle(re.search(kalip, sistem) is not None,
          "telefon talimatında eksik madde: " + ad)

# SESLENME KURALI İSTEMDE DEĞİL, KODDA.
#
# İstem her satırda baştan işleniyor (ölçüm: satır başına 2206 karakter,
# repliğin kendisi 40). Kodun DETERMİNİSTİK olarak uyguladığı bir kuralı
# isteme de yazmak bedelli, karşılıksız. Seslenme onarımı AiGuard.hitapDuzelt
# ile zorunlu, o yüzden istemden çıkarıldı.
#
# AMA kodun gerçekten uyguladığından emin olmak şart: kural istemden
# çıkarıldı ve kodda eksikse sessizce kaybolur. 9.69'da tam bu ortaya çıktı —
# hitapDuzelt yalnız hafıza yoluna bağlıydı, taze yapay zekâ çıktısına değil.
bekle("Lan" not in sistem,
      "seslenme kuralı hâlâ istemde: kod zaten uyguluyor, her satırda "
      "~150 karakter boşa gidiyor")
bas_blok = koruma[koruma.index("private val HITAP_BAS"):]
bas_blok = bas_blok[:bas_blok.index(".map {")]
for soz in ("look", "listen", "like", "man", "well"):
    bekle(re.search(r'"[^"]*\b%s\b[^"]*"\s*to' % soz, bas_blok) is not None,
          "AiGuard.HITAP_BAS'ta '%s' yok (istemden çıkarıldı, kodda da yoksa kayıp)" % soz)

# TEK KAPI: yapay zekâ çıktısı üç yoldan geliyor (hız, doğruluk, TV köprüsü)
# ve üçü de ad + seslenme onarımından geçmek zorunda.
bekle("private fun yzDuzelt(" in yakala_src,
      "yapay zekâ çıktısı tek kapıdan geçmiyor: bir yol onarımı atlar")
bekle(yakala_src.count("yzDuzelt(") >= 4,
      "üç yapay zekâ yolunun hepsi yzDuzelt kullanmıyor")
bekle("NameFix.koru(source," not in yakala_src and "NameFix.koru(src," not in yakala_src,
      "bir yol hâlâ NameFix.koru'yu doğrudan çağırıyor: seslenme onarımını atlar")
bekle("AiGuard.hitapDuzelt(kaynak, NameFix.koru(kaynak, ham.trim()))" in yakala_src,
      "yzDuzelt ikisini birlikte yapmıyor")


# --- Talimat UZUNLUĞU: her satırda baştan işleniyor, her kelime gecikme.
# 9.64'te maddeleri ekleyince 2202 karaktere çıktı ve yapay zekâ ortalaması
# 2800 ms -> 3808 ms oldu; 56 replikten yalnız 5'i düzeltilebildi.
bekle(len(sistem) <= 1800,
      "sistem talimatı çok uzun (%d karakter): her satırda baştan işleniyor, "
      "gecikmeye yazılıyor. Madde atma, KISALT." % len(sistem))

# --- İpucu sayısı da isteme giriyor.
argo_src = open(os.path.join(KAYNAK, "Slang.kt"), encoding="utf-8").read()
m = re.search(r"fun hintsFor\(line: String\): List<String> =\s*\n?\s*hints\.filter.*?\.take\((\d+)\)",
              argo_src, re.S)
bekle(m is not None, "hintsFor kayboldu")
if m:
    bekle(int(m.group(1)) <= 3,
          "satır başına %s ipucu isteme giriyor: gecikmeyi artırır" % m.group(1))

# --- YARIDA KESME KALDIRILDI.
# Yapay zekâ 3808 ms, altyazı 2-4 sn: kesme kuralı çevirinin neredeyse hiç
# bitmemesi demekti. Biten çeviri ya üst sıraya yazılıyor ya hafızaya giriyor.
yakala_src = open(os.path.join(KAYNAK, "CaptureService.kt"), encoding="utf-8").read()
bekle("cancelOthers" not in yakala_src,
      "yapay zekâ çevirisi yine yarıda kesiliyor: 56 replikten 5'i düzelir")
bekle("cancelOthers" not in llm,
      "cancelOthers ölü kod olarak duruyor")
bekle(yakala_src.count("discardIfStale = false") >= 2,
      "biten çeviri atılıyor: hem refineOnDevice hem accurateOnDevice "
      "sonucu saklamalı (hafıza ya da üst sıra)")

# --- Denetim Türkçe ek alan adı bozulmuş saymamalı.
bekle("val kok = kk.trimEnd('s').trimEnd('e')" in koruma,
      "'Viltrumites -> Viltrumitler' yine 'ad bozuldu' diye reddedilir")

# --- Özel adın büyük harfi geri konmalı.
tfix = open(os.path.join(KAYNAK, "TurkishFix.kt"), encoding="utf-8").read()
bekle("fun buyukHarfiGeriKoy(" in tfix,
      "çeviri motorunun küçülttüğü özel ad ('viltrumitler', 'regent') düzeltilmiyor")
bekle("if (kelime.length < 5) continue" in tfix,
      "büyük harf kuralı kısa kelimeleri de büyütüyor")


# --- ÖZEL AD KALKANI: motor tanımadığı adı sözcük sanıp çeviriyordu.
bekle("object NameShield" in off,
      "özel ad kalkanı yok: 'Toolock' -> 'aletini', 'Viltrumite' -> 'kaydırılmış'")
bekle("NameShield.shield(text)" in off and "NameShield.restore(" in off,
      "kalkan çeviri motoruna giden kapıya (base) bağlanmamış")
bekle("if (!Sozluk.hazir) return Kalkanli(text, emptyList())" in off,
      "sözlük yüklenmeden kalkan devreye giriyor: gerçek kelimeler de kalkanlanır")
bekle("!Sozluk.icinde(kucuk)" in off,
      "kalkan sözlük şartına bağlı değil: 'Because', 'Alien' gibi kelimeler kalkanlanır")

# --- Konuşmacı etiketindeki ad çevrilmemeli: "[Alien]" -> "[Yabancı]".
bekle("private fun etiketAdiniKoru(" in tfix,
      "konuşmacı etiketindeki karakter adı korunmuyor")

# --- redSebebi de yalnız adlık satırı muaf tutmalı (yetersizMi gibi).
bekle('ignoreCase = true) && !yalnizAdMi(kaynak)) return "çevrilmemiş"' in koruma,
      "'Raymond, Raymond.' ve 'Viltrumite!' yine 'çevrilmemiş' diye reddedilir")

# --- Konsol arayüzü altyazı sanılmamalı.
bekle('"playstation"' in yakala_src and '"play station"' in yakala_src,
      "PlayStation durum çubuğu yine çevrilir ('Play Station' -> 'Çalma istasyonu')")

# --- Karşılık kaynağın büyük/küçük harfini izlemeli.
bekle("private fun harfiEslestir(" in off,
      "sözlük karşılığı kaynağın harfini izlemiyor: ikinci satır büyük harfle "
      "başlar ya da 'This way.' küçük harf kalır")
bekle(off.count("harfiEslestir(core, it)") >= 3,
      "harfiEslestir tüm sözlük yollarına bağlanmamış")

# --- Konuşmacı etiketi kuralı SATIR BAŞINA dar olmalı.
# İlk hâli satırdaki bütün tek kelimelik etiketleri adla değiştiriyordu ve
# ses etiketini yok ediyordu:
#   "[Alien] Oh, shit! [grunts]" -> "[Alien] Ah, siktir! [Alien]"
bekle('Regex("^\\\\[(\\\\p{Lu}' in tfix,
      "etiket adı kuralı satır başına bağlı değil: satırdaki ses etiketini yok eder")
bekle("CEVIRI_ETIKETI" in tfix,
      "çeviri tarafındaki etiket de satır başına bağlı değil")

# --- OCR bileşik onarımı: iki kelimeli hâli MEŞRU olanlar eklenmemeli.
# "No body was ever found." (ceset) -> "Nobody was ever found." anlamı ters.
for riskli in ("no ?body", "any ?body", "some ?thing", "every ?thing"):
    bekle(riskli not in off,
          "riskli bileşik onarımı geri gelmiş (%s): iki kelimeli hâli gerçek "
          "ve sık, anlamı tersine çevirir" % riskli)

# --- OCR bölünmüş bileşik kelime.
bekle('"friendship"' in off,
      "'friend ship' -> 'arkadaş gemisi' onarılmıyor")


# ------------------------------ 2) API talimatı telefonla aynı maddeleri taşır

i = api.index("private fun systemPrompt()")
apiblok = api[i:api.index("\n    }", i)]
apiblok = "\n".join(l for l in apiblok.splitlines() if not l.strip().startswith("//"))
apistr = " ".join(re.findall(r'"((?:[^"\\]|\\.)*)"', apiblok))
for ad, kalip in {
    "OCR anlamsız okuma": r"anlamsız",
    "kelime kelime değil": r"[Kk]elime kelime",
    "çokluk": r"[Çç]okluk|çoğul",
    "seslenme": r"Lan",
    "yalnız ad satırı": r"[Yy]alnız adlardan",
}.items():
    bekle(re.search(kalip, apistr) is not None,
          "API talimatı telefon talimatından ayrışmış, eksik: " + ad)


# --------------------------------------------------- 3) I'll -> II onarımı

bekle(re.search(r"\[Il\|\]\{2,3\}", off) is not None,
      "\"II see you soon.\" -> \"I'll see you soon.\" onarımı yok")
# Roma rakamı bozulmasın diye fiil listesi şart.
m = re.search(r"\[Il\|\]\{2,3\}\\\\s\+\(\?=\((.*?)\)\\\\b\)", off, re.S)
bekle(m is not None, "II onarımı fiil listesine bağlı değil (roma rakamını bozar)")
if m:
    for f in ("see", "be", "get", "tell", "call"):
        bekle(f in m.group(1), "II onarımı fiil listesinde '%s' yok" % f)

# "Thope to never see..." -> "I hope ...": baştaki T/l, gerçek fiille gelirse "I".
m = re.search(r'Regex\(\s*\n?\s*"\^\[T\|l\]\(\?=\((.*?)\)\\\\b\)"', off, re.S)
bekle(m is not None, "\"Thope\" -> \"I hope\" kuralı yok")
if m:
    for f in ("hope", "think", "know", "need", "want", "already"):
        bekle(f in m.group(1), "Thope kuralı fiil listesinde '%s' yok" % f)

# Unvanlar TASLAK sözlüğünde de olmalı: talimatta olması yetmiyor,
# "No, General." -> "Hayır, Genel." taslaktan geliyordu.
for unvan in ("general = ", "colonel = ", "lieutenant = ", "admiral = "):
    bekle("\n" + unvan in off, "taslak sözlüğünde unvan yok: " + unvan.strip(" ="))

# Türkçede zarf fiilden önce gelir.
bekle("private val ZARF = setOf(" in off,
      "hâl zarfı kümesi yok: '[homurdanıyor sertçe]' gibi sıra hataları kalır")
bekle("for (z in zarflar) res.add(maxOf(0, res.size - 1), z)" in off,
      "zarf fiilin önüne taşınmıyor")


# ------------------------------------------- 4) ad yazımı: iç büyük harf

m = re.search(r'private val AD_YAZIMI = Regex\("([^"]+)"\)', koruma)
bekle(m is not None, "AD_YAZIMI kayboldu")
if m:
    bekle("\\\\p{L}" in m.group(1),
          "AD_YAZIMI iç büyük harfe izin vermiyor: 'DeLeon', 'McBride' ad "
          "sayılmaz ve '[Batman] Vinny DeLeon, Roger Marshall,' yine ekrana gelmez")


# ------------------------------------------ 5) kelime listesi: eksik kelimeler

import gzip

liste = os.path.join(KOK, "..", "app", "src", "main", "res", "raw", "kelimeler.gz")
with gzip.open(liste, "rt", encoding="utf-8") as f:
    kelimeler = set(f.read().split())
# "olive" listede ama "alive" değildi: OCR onarımı "olive"ı bilinen kelime
# görüp dokunmuyordu ve "O zeytin?" ekrana geliyordu.
for k in ("alive", "christmas", "pushers", "into", "things", "read"):
    bekle(k in kelimeler, "kelime listesinde '%s' yok" % k)
bekle(len(kelimeler) > 124_000, "kelime listesi küçülmüş: %d" % len(kelimeler))


# -------------------------------------- 6) ses sözcükleri: kırpılan etiketler

ses = off[off.index("object SoundTags {"):]
for k in ("thump", "creature", "yowling", "mewls", "curtain", "intriguing",
          "atmospheric", "haunting", "vocalizing", "alert", "tone", "sharp",
          "pants", "screech", "high-pitched", "snaps", "dispatcher", "clatters",
          "militaristic", "energetic", "fiercely", "weakly", "strained", "thumping",
          "soldiers", "children", "unison", "breaths", "gagging", "cadet", "intense"):
    bekle('"%s" to ' % k in ses,
          "ses sözcüğü '%s' yok: etiket kırpılıyor (örn. '[grunts and pants]' "
          "-> '[homurtular ve]') ve parantez onarımı da çalışmıyor" % k)


# ---------------------------------------------------------------- sonuç

if hata:
    print("Talimat testi DÜŞTÜ:")
    for h in hata:
        print("  ✗ " + h)
    sys.exit(1)

print("Talimat testi geçti: telefon ve API talimatı aynı maddeleri taşıyor, "
      "OCR onarımları yerinde.")

# 10.42.7: Yo is checked by production Kotlin policy, NOT by the old HITAP table.
bekle("YoContext10427.correct(kaynak, ceviri)" in koruma if "koruma" in globals() else "YoContext10427.correct(kaynak, ceviri)" in denetim, "Yeni bağlamsal Yo kuralı bağlı değil")
