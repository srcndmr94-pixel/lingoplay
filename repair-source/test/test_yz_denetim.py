"""
Yapay zekânın SAĞLAM taslağı bozmasını engelleyen denetimler.

Kullanıcının 11 Kasım 10:25 kaydında küçük model (E2B) satırların çoğunda
taslaktan kötü sonuç üretti. İki tür bozma kesin olarak yakalanabiliyor:

1. KALIP BOZULMASI — elle yazılmış, İngilizcesinden tahmin edilemeyen
   karşılıklar. Sözlükte doğru duruyor, taslak doğru çeviriyor, yapay zekâ
   kelime kelime çevirip anlamı tersine döndürüyor:
       "It's twelve!"     taslak "Polis geldi"    YZ "Yüz on iki!"
       "street pushers"   taslak "sokak satıcısı" YZ "sokak polisleri"
       "Aww, too bad!"    taslak "Ah, ne yazık!"  YZ "Aman, neyse ki!"

2. KÜFÜR DÜŞMESİ — "küfür korunuyor" ayarı açıkken yapay zekâ sertliği
   siliyor: "What the fuck you talking about?" taslakta "Ne bok konuşuyorsun
   sen?" iken cevapta "Ne cidden bahsediyorsun?" oluyordu.

Testin ikinci yarısı en az birincisi kadar önemli: yapay zekânın DAHA İYİ
çevirilerini engellememeli. "Hold up" için taslak "Bekle", yapay zekâ "Dur
bir" diyor; ikisi de doğru, reddedilmemeli.
"""

import os
import re
import sys

KAYNAK = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main",
                      "java", "com", "example", "altyazi", "AiGuard.kt")
metin = open(KAYNAK, encoding="utf-8").read()

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


bekle("private val KORUNAN" in metin, "korunan kalıp listesi yok")
bekle("fun kalipDustu" in metin, "kalıp denetimi yok")
bekle("küfür düştü" in metin, "küfür denetimi yok")
bekle("kalipDustu(kaynak, yKucukTam)" in metin, "kalıp denetimi redSebebi'ye bağlanmamış")

# Hafıza da denetlenmeli: bozuk kayıt sonsuza kadar geri servis ediliyordu.
bekle("fun hafizaBozuk" in metin, "hafıza denetimi yok")
_svc = open(os.path.join(os.path.dirname(KAYNAK), "CaptureService.kt"), encoding="utf-8").read()
bekle("AiGuard.hafizaBozuk(source, it)" in _svc, "hafıza denetimi bağlanmamış")
bekle("HAFIZADAN SİLİNDİ" in _svc, "bozuk hafıza kaydı silinmiyor")
bekle("storeAi?.forget(source)" in _svc, "yapay zekâ hafızasından silinmiyor")
_store = open(os.path.join(os.path.dirname(KAYNAK), "TranslationStore.kt"), encoding="utf-8").read()
bekle("fun forget(" in _store, "TranslationStore.forget yok")


def desenler(ad):
    blok = re.search(rf"private val {ad}[^=]*= Regex\(\s*(.*?)\n\s*\)", metin, re.S)
    return blok.group(1) if blok else ""


IN_KUFUR = re.compile(desenler("IN_KUFUR").replace('"', "").replace("+", "").replace("\\\\", "\\")
                      .replace("(?i)", "").strip(), re.IGNORECASE) if desenler("IN_KUFUR") else None
bekle(IN_KUFUR is not None, "İngilizce küfür deseni okunamadı")

_kor = re.search(r"private val KORUNAN: List<Pair<Regex, String>> = listOf\((.*?)\n\s*\)\.map",
                 metin, re.S)
_yas = re.search(r"private val YASAK: List<Pair<Regex, String>> = listOf\((.*?)\n\s*\)\.map",
                 metin, re.S)
bekle(_yas is not None, "YASAK listesi yok")
YASAK = [(d.replace("\\\\", "\\"), k)
         for d, k in re.findall(r'"((?:[^"\\\\]|\\\\.)*)" to "((?:[^"\\\\]|\\\\.)*)"', _yas.group(1))] if _yas else []
bekle("fun kucuk" in metin, "Türkçe duyarlı küçültme yok: İ harfi eşleşmez")
bekle(_kor is not None, "KORUNAN listesi okunamadı")
KORUNAN = [(d.replace("\\\\", "\\"), k)
           for d, k in re.findall(r'"((?:[^"\\]|\\.)*)" to "((?:[^"\\]|\\.)*)"', _kor.group(1))] if _kor else []
bekle(len(KORUNAN) >= 3, f"korunan kalıp az: {len(KORUNAN)}")

TR_KUFUR = re.compile(
    r"(sik|siktir|amına|amina|orospu|piç|pic|\bbok\b|hassiktir|kahretsin|lanet|"
    r"\blan\b|\bulan\b|\bbe\b|götü|gotu|yarrak|aptal|salak|şerefsiz)", re.IGNORECASE)


def kucuk(s):
    """Kotlin'deki kucuk() ile aynı: İ -> i, I -> ı."""
    return s.replace("İ", "i").replace("I", "ı").lower()


def red(kaynak, taslak, yz):
    y = kucuk(yz)
    for desen, kok in KORUNAN:
        if re.search(desen, kaynak, re.IGNORECASE) and kok not in y:
            return "kalıp bozuldu"
    for desen, yasak in YASAK:
        if re.search(desen, kaynak, re.IGNORECASE) and yasak in y:
            return "saçma karşılık"
    if (taslak and IN_KUFUR and IN_KUFUR.search(kaynak)
            and TR_KUFUR.search(taslak) and not TR_KUFUR.search(yz)):
        return "küfür düştü"
    return None


KOTU = [
    ("Hey, yo, Ray! It's twelve! They tooled up and everything!",
     "Hey, hey, Ray! Polis geldi! Tepeden tırnağa silahlılar!",
     "Hey, yo, Ray! Yüz on iki! Silahlı geldiler ve her şey!"),
    ("Can't you fucking handle a bunch of street pushers?",
     "Bir avuç sokak satıcısıyla bile başa çıkamıyor musunuz be?",
     "Sokak polislerini yönetemiyor musun lan?"),
    ("Aww, too bad!", "Ah, ne yazık!", "Aman, neyse ki!"),
    ("What the fuck you talking about?", "Ne bok konuşuyorsun sen?",
     "Ne cidden bahsediyorsun?"),
    # 10:50 kaydı: merdiven çıkmak "yükselmek" değil, yer tarifi "içinde" değil.
    ("Yo! We going up.", "Hey! Yukarı çıkıyoruz.", "Hey! Yükseliyoruz."),
    ("Right inside.", "Hemen içeride.", "Tam içinde."),
    ("Straight up, first door on the right.", "Dosdoğru ilerle, sağdaki ilk kapı.",
     "Açık sözlük, sağdaki ilk kapı."),
]
for kaynak, taslak, yz in KOTU:
    if red(kaynak, taslak, yz) is None:
        hata.append(f"bozma yakalanmadı: {yz!r}")

IYI = [
    ("Alright, that's the block right here.", "Pekâlâ, Buradaki blok bu.",
     "Tamam, o mahalle burası."),
    ("Hold up, bruh.", "Bekle, bruh.", "Dur bir, kanka."),
    ("Yeah, he fucking knows.", "Evet, biliyor amına koyayım.", "Evet, biliyor lan."),
    ("Police! Don't fucking move!", "Polis! Kıpırdama lan!", "Polis! Kıpırdama lan!"),
    ("Fuck Boobie! We gotta get out of this.",
     "Boobie'yi siktir et! Bu işten sıyrılmamız lazım.",
     "Boobie'ye siktir! Bundan kurtulmalıyız."),
    ("My partner's off the grid right now, you feel me?",
     "Ortağım şu an ortalarda yok, anlıyorsun ya?",
     "Ortaklığım şu an tamamen izole, anlıyor musun?"),
    ("we in a holding pattern right here.", "Burada bekliyoruz.", "Burada bekliyoruz."),
    ("Come on, we need to find a way out.", "Hadi, Bir çıkış yolu bulmalıyız.",
     "Hadi ya, bir çıkış yolu bulmalıyız."),
    ("Appreciate it.", "Sağ ol.", "Teşekkürler."),
    ("Looks like it.", "Gibi görünüyor.", "Görünüşe göre öyle."),
    # Geçerli başka söyleyişler engellenmemeli.
    ("Yo! We going up.", "Hey! Yukarı çıkıyoruz.", "Hadi, yukarı çıkalım."),
    ("Right inside.", "Hemen içeride.", "İçeride, hemen şurada."),
    ("Straight up, first door on the right.", "Dosdoğru ilerle, sağdaki ilk kapı.",
     "Dümdüz git, sağdaki ilk kapı."),
]
for kaynak, taslak, yz in IYI:
    sebep = red(kaynak, taslak, yz)
    if sebep is not None:
        hata.append(f"doğru çeviri reddedildi ({sebep}): {yz!r}")

# --------------------- kısalma eşiği: E4B derli toplu çeviriyor
#
# 0,60 eşiği doğru cevapları eliyordu. 11 Kasım E4B kaydından:
#   "T always been there for you."
#     taslak "T her zaman senin için orada olmuştur."  (bozuk)
#     YZ     "Sen hep oradaydın."                      (doğru, REDDEDİLDİ)
# Eşik 0,45'e indi; gerçek içerik kaybı hâlâ yakalanıyor.

_e = re.search(r"yh < th \* ([\d.]+)", metin)
bekle(_e is not None, "kısalma eşiği bulunamadı")
if _e:
    ESIK = float(_e.group(1))
    bekle(ESIK <= 0.5, f"eşik {ESIK}: derli toplu doğru çeviriler eleniyor")
    bekle(ESIK >= 0.3, f"eşik {ESIK}: gerçek içerik kaybı kaçar")

    def harf(t):
        return sum(c.isalnum() for c in t)

    def kisaldi(taslak, yz):
        th, yh = harf(taslak), harf(yz)
        return th >= 25 and yh < th * ESIK

    KABUL = [
        ("T her zaman senin için orada olmuştur.", "Sen hep oradaydın."),
        ("Nerede olduğunu anlayana kadar ve neler oluyor, amına koyayım?",
         "Nerede lan, ne oluyor anlamadan..."),
        ("Belki de bu açık havaya uçurmak için buraya bir powderkeg yapabilirim...",
         "Belki buraya bir Powderkeg çekip bunu patlatabilirim..."),
        ("Bir avuç sokak satıcısıyla bile başa çıkamıyor musunuz be?",
         "Sokak satıcılarıyla başa çıkamaz mısın lan?"),
    ]
    for taslak, yz in KABUL:
        if kisaldi(taslak, yz):
            hata.append(f"derli toplu doğru çeviri reddedildi: {yz!r}")

    RED = [
        ("Tapınakçılara sattığınız haritalar onları doğrudan bize yönlendirdi", "Haritalar"),
        ("Annenin evinin dışında bir adamım olduğunu biliyorsun, değil mi?", "Biliyorsun."),
    ]
    for taslak, yz in RED:
        if not kisaldi(taslak, yz):
            hata.append(f"gerçek içerik kaybı kaçtı: {yz!r}")


# ---------------- yapay zekâyı GEREKSİZ yere çağırma, gerektiğinde çağır
#
# 11 Kasım E4B kaydında taslak çok iyileşti: yapay zekâ 9 satırı düzeltti,
# 11 satırı bozdu. Her satırda çalıştırmak artık net kazanç değil. Çözüm
# denetim eklemek değil, TETİĞİ keskinleştirmek: taslak bozuksa çağır.

bekle("fun tekrarEdenKelime" in metin, "tekrar eden kelime tetiği yok")
bekle("tekrarEdenKelime(taslak)" in metin, "tetik yzGerekli'ye bağlanmamış")
bekle("küfür eklendi" in metin, "kaynakta olmayan küfürün eklenmesi denetlenmiyor")
bekle("private val TR_SERT" in metin, "sert küfür listesi ayrılmamış")
_sert = re.search(r"private val TR_SERT = Regex\((.*?)\n\s*\)", metin, re.S)
bekle(_sert is not None and "\\blan\\b" not in _sert.group(1),
      "'lan' sert küfür sayılıyor: 'Yo' karşılığı doğru çeviriler reddedilir")
bekle(_sert is not None and "sik" in _sert.group(1), "sert küfür listesi boş görünüyor")

PEK = {"çok", "az", "yavaş", "ağır", "hemen", "tek", "bir"}
_TEKRAR = re.compile(r"(?<![^\W\d_])([^\W\d_]+)[ \t]+\1(?![^\W\d_])", re.IGNORECASE)


def tekrarli(t):
    return any(m.group(1).lower() not in PEK for m in _TEKRAR.finditer(t))


for _t in ["Demek istediğim, siktir siktir sahip olabilir ve biraz bok, söylüyorum diyorum.",
           "Hey Ray, o o. Ernesto."]:
    if not tekrarli(_t):
        hata.append(f"bozuk taslak yakalanmadı: {_t!r}")
# Meşru tekrar: araya noktalama giriyor, yapay zekâ çağrılmamalı.
for _t in ["Hadi, hadi!", "Kımılda, kımılda!", "Çok çok iyi.", "Bir bir saydı.",
           "Sanırım geleceğimizi biliyorlar."]:
    if tekrarli(_t):
        hata.append(f"meşru tekrar bozuk sanıldı: {_t!r}")

# Boşa çizim: yalnız noktalama değişmişse ekran tazelenmemeli.
_svc2 = open(os.path.join(os.path.dirname(KAYNAK), "CaptureService.kt"), encoding="utf-8").read()
bekle("fun ayniYazi" in _svc2, "noktalama farkı hâlâ yeniden çizdiriyor")
bekle("ayniYazi(shownValue, value)" in _svc2, "ayniYazi publish'e bağlanmamış")


def sade(t):
    return "".join(c for c in t.lower() if c.isalnum())


for _x, _y in [("Dostum..", "Dostum..."),
               ("Sorun şu ki sadece kendini düşünüyor.", "Sorun şu ki, sadece kendini düşünüyor.")]:
    if sade(_x) != sade(_y):
        hata.append(f"boşa çizim yakalanmadı: {_x!r} / {_y!r}")
for _x, _y in [("Hemen içeride.", "İçeride."), ("Evet, biliyorum.", "Biliyorum lan.")]:
    if sade(_x) == sade(_y):
        hata.append(f"gerçek fark aynı sanıldı: {_x!r} / {_y!r}")


# ------------- yalnız addan oluşan satır "çevrilmemiş" sayılmamalı
#
# "Raymond, Raymond." zaten çevrilmez; yapay zekâ aynısını döndürdüğünde bu
# DOĞRU cevaptır. 11 Kasım kaydında "çevrilmemiş" diye reddedilip satır
# ekranda hiç görünmemişti.

bekle("fun yalnizAdMi" in metin, "yalnız-ad denetimi yok")
bekle("!yalnizAdMi(kaynak)" in metin, "yetersizMi hâlâ ad satırlarını reddediyor")

_AD = re.compile(r"^[A-ZÇĞİÖŞÜ][a-zçğıöşü'’]*$")


def yalniz_ad(t):
    k = re.findall(r"[^\W\d_'’]+", t)
    return bool(k) and all(_AD.match(w) for w in k)


def yetersiz(kaynak, yz):
    if not yz or not yz.strip():
        return True
    if yz.strip().lower() != kaynak.strip().lower():
        return False
    return not yalniz_ad(kaynak)


for _s in ["Raymond, Raymond.", "Yo, Nesto!", "Lucia, Jason."]:
    if yetersiz(_s, _s):
        hata.append(f"ad satırı reddedildi, ekranda hiç görünmez: {_s!r}")
for _s in ["Hello there.", "STOP RIGHT THERE", "Where is he?"]:
    if not yetersiz(_s, _s):
        hata.append(f"çevrilmemiş satır kabul edildi: {_s!r}")


# ------------------- küfür tercihi (9.98, kullanıcı): "what the fuck" -> "bu ne lan", "sikim" yok
_off = open(os.path.join(os.path.dirname(KAYNAK), "OfflineFix.kt"), encoding="utf-8").read()
bekle("what the fuck = Bu ne lan" in _off, "'what the fuck' 'Bu ne lan' değil")
bekle("what the fuck is going on = Ne oluyor lan" in _off, "'what the fuck is going on' 'Ne oluyor lan' değil")
bekle("sikim" not in _off, "sözlükte hâlâ 'sikim' karşılığı var")
bekle("\nyo = Hey\n" in _off, "'yo' sözlükte artık 'Hey' değil")
bekle("ne bok" not in _off, "sözlükte hâlâ 'ne bok' karşılığı var")
for _k in ["oyeme = ", "mi hermano = ", "fuck boobie = ", "we gotta get out of this = "]:
    bekle(_k in _off, f"istenen kalıp eklenmemiş: {_k}")


if hata:
    print("Yapay zekâ denetim testi DÜŞTÜ:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print(f"Yapay zekâ denetim testi geçti ({len(KOTU)} bozma yakalandı, "
      f"{len(IYI)} doğru çeviri geçti, {len(KORUNAN)} korunan kalıp).")
