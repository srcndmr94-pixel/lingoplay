"""
İki satırlı altyazı yığını, okuma süresi ve geç gelen düzeltme.

Kullanıcının 11 Kasım kaydındaki sorun: yapay zekâ düzeltmesi 2,6-4,5 sn
sürüyor, altyazı satırları 3-5 sn ekranda kalıyor. Düzeltme hep satırın son
yarım saniyesinde yetişiyor ve okunmadan yeni satır onu siliyordu:

    YAZILAN · taslak: Hey, ne bok?
    YAZILAN · YZ 2580 ms: Lan, ne oluyor ya?
    OKUNAN: Yo, what's up? ...        <- yeni satır düzeltmeyi hemen sildi

Üç kural eklendi:
  1. Okuma süresi yazının uzunluğundan hesaplanır (sabit süre yerine);
     kullanıcının "en az N sn" kaydırıcısı alt sınır olarak kalır.
  2. Yeni satır 1,5 sn içinde gelirse önceki satır silinmez, küçülüp
     solgunlaşarak üst sıraya çıkar ve 1,5 sn daha okunur.
  3. Geç gelen düzeltme sadece hafızaya yazılmaz; satır hâlâ üst sıradaysa
     oradaki taslağın yerine geçer. Çok geç gelirse gösterilmez.

Testin ikinci yarısı kuralların GEREKSİZ yere çalışmadığını doğruluyor:
yavaş konuşmada yığın oluşmamalı, aynı repliğin düzeltmesi üste çıkmamalı
(aynı cümle iki kez görünürdü), ayar kapalıyken hiçbiri çalışmamalı.
"""

import os
import re
import sys

KOK = os.path.dirname(__file__)
APP = os.path.join(KOK, "..", "app", "src", "main")
KAYNAK = os.path.join(APP, "java", "com", "example", "altyazi")

yakala = open(os.path.join(KAYNAK, "CaptureService.kt"), encoding="utf-8").read()
katman = open(os.path.join(KAYNAK, "Overlay.kt"), encoding="utf-8").read()
ayar = open(os.path.join(KAYNAK, "Prefs.kt"), encoding="utf-8").read()
ana = open(os.path.join(KAYNAK, "MainActivity.kt"), encoding="utf-8").read()
duzen = open(os.path.join(APP, "res", "layout", "activity_main.xml"), encoding="utf-8").read()

hata = []


def bekle(kosul, mesaj):
    if not kosul:
        hata.append(mesaj)


# ------------------------------------------------- 1) kaynakta duruyor mu

bekle("fun okumaSuresi" in yakala, "okumaSuresi yok")
bekle(">= okumaMs" in yakala, "temizleme hâlâ sabit süreye bakıyor")
bekle("prefs.minShowSec * 1000L" in yakala, "kaydırıcı alt sınır olarak kullanılmıyor")
bekle("fun oncekiyiZamanla" in yakala, "üst sıra zamanlayıcısı yok")
bekle("fun yiginiBosalt" in yakala, "yığın boşaltma yok")
bekle("yiginiBosalt()" in yakala.split("override fun onDestroy")[-1],
      "servis kapanırken bekleyen yığın işi kaldırılmıyor")
# Both layout pathways are allowed: older dynamic OCR position, or newer
# fixed-bottom positioning. In either case the previous line must be wired.
old_layout = bool(re.search(r"overlay\.setSubtitle\(\s*value\s*,\s*ust\s*,\s*refined\s*,\s*draft\s*,\s*subtitleBottomPx\s*,\s*oncekiMetin", yakala))
new_layout = bool(re.search(r"overlay\.setSubtitle\(\s*value\s*,\s*screenHeight\s*-\s*dp\(110\)\s*,\s*refined\s*,\s*draft\s*,\s*-1\s*,\s*if \(prefs\.yigin\) oncekiMetin else null", yakala))
bekle(old_layout or new_layout, "üst sıra setSubtitle'a bağlanmamış")
bekle("fun oncekiYaz" in katman, "Overlay.oncekiYaz yok")
bekle("fun oncekiSoldur" in katman, "Overlay.oncekiSoldur yok")
bekle("prevView" in katman, "Overlay'de ikinci kutu yok")

# Ayar ve arayüz
bekle("var yigin: Boolean" in ayar, "yigin ayarı yok")
bekle('sp.getBoolean("yigin", true)' in ayar, "yigin varsayılanı açık değil")
bekle("cbYigin" in duzen, "ayarlar ekranında onay kutusu yok")
bekle("prefs.yigin = cbYigin.isChecked" in ana, "onay kutusu kaydedilmiyor")
bekle("cbYigin.isChecked = prefs.yigin" in ana, "onay kutusu yüklenmiyor")

# Üç satır DEĞİL iki satır: telefonda yatay oynarken ekranı kapatmasın.
bekle("olderView" not in katman and "ucuncu" not in katman,
      "üçüncü satır eklenmiş (telefonda oyunun üstünü kapatır)")


# ------------------------------------- 2) sabitler kaynaktan okunuyor

def sabit(ad, metin=None):
    m = re.search(rf"val {ad} = (\d+)L", metin if metin is not None else yakala)
    if not m:
        hata.append(f"{ad} sabiti bulunamadı")
        return 0
    return int(m.group(1))


YIGIN_ICINDE = sabit("YIGIN_ICINDE")
ONCEKI_TUT = sabit("ONCEKI_TUT")
GEC_SINIR = sabit("GEC_SINIR")

bekle(YIGIN_ICINDE >= 1000, f"yığın penceresi çok dar: {YIGIN_ICINDE} ms")
bekle(ONCEKI_TUT >= 1000, f"üst sıra okunacak kadar durmuyor: {ONCEKI_TUT} ms")


# --------------------------------------- 3) mantığın aynısı, sahte saatle

class Ekran:
    def __init__(self, yigin=True, min_show=3):
        self.yigin = yigin
        self.min_show = min_show
        self.simdi = 0
        self.gosterilen_kaynak = ""
        self.gosterilen = ""
        self.gosterim_an = 0
        self.okuma_ms = 0
        self.onceki = None
        self.onceki_kaynak = ""
        self.onceki_kalkar = 0
        self.ceviriliyor = ""

    def okuma_suresi(self, v):
        return max(self.min_show * 1000, min(8000, 2000 + len(v) * 60))

    def _temizle_ustu(self):
        if self.onceki is not None and self.simdi >= self.onceki_kalkar:
            self.onceki = None
            self.onceki_kaynak = ""

    def publish(self, kaynak, deger, etiket=""):
        self._temizle_ustu()
        if self.ceviriliyor != kaynak:
            if (self.yigin and "YZ" in etiket and self.onceki_kaynak == kaynak
                    and self.simdi - self.gosterim_an < GEC_SINIR):
                self.onceki = deger
                self.onceki_kalkar = self.simdi + ONCEKI_TUT
                return "ust-sira"
            return "gec-kaldi"
        if (self.yigin and self.gosterilen_kaynak and self.gosterilen_kaynak != kaynak
                and self.gosterilen and self.simdi - self.gosterim_an < YIGIN_ICINDE
                and self.gosterilen != deger):
            self.onceki = self.gosterilen
            self.onceki_kaynak = self.gosterilen_kaynak
            self.onceki_kalkar = self.simdi + ONCEKI_TUT
        elif self.gosterilen_kaynak != kaynak:
            self.onceki = None
            self.onceki_kaynak = ""
        self.gosterilen_kaynak = kaynak
        self.gosterilen = deger
        self.gosterim_an = self.simdi
        self.okuma_ms = self.okuma_suresi(deger)
        return "yazildi"

    def temizlenir_mi(self):
        return self.simdi - self.gosterim_an >= self.okuma_ms

    def satirlar(self):
        self._temizle_ustu()
        r = []
        if self.onceki is not None:
            r.append("^" + self.onceki)
        if self.gosterilen:
            r.append(self.gosterilen)
        return r


# --- okuma süresi
e = Ekran()
bekle(e.okuma_suresi("Ne?") == 3000, f"kısa satır kaydırıcının altına düştü: {e.okuma_suresi('Ne?')}")
UZUN = "Lan, biraz boktan bir şeyler yapıp sallanmam gerekebilir, anladın mı?"
bekle(e.okuma_suresi(UZUN) > 3000, "uzun satır hâlâ 3 sn")
bekle(e.okuma_suresi("x" * 400) == 8000, "üst sınır 8 sn değil")
bekle(Ekran(min_show=5).okuma_suresi("Ne?") == 5000, "kaydırıcı alt sınır olarak çalışmıyor")

# --- kullanıcının kaydındaki gerçek akış
e = Ekran()
e.ceviriliyor = "what the fuck"
e.publish("what the fuck", "Hey, ne bok?", " · taslak")
e.simdi = 2580
e.publish("what the fuck", "Lan, ne oluyor ya?", " · YZ")
bekle(e.satirlar() == ["Lan, ne oluyor ya?"],
      f"aynı repliğin düzeltmesi üste çıktı (cümle iki kez görünür): {e.satirlar()}")
e.simdi = 2880
e.ceviriliyor = "whats up"
e.publish("whats up", "Hey, naber?", " · taslak")
bekle(e.satirlar() == ["^Lan, ne oluyor ya?", "Hey, naber?"],
      f"düzeltme üst sıraya çıkmadı: {e.satirlar()}")
e.simdi = 2880 + ONCEKI_TUT
bekle(e.satirlar() == ["Hey, naber?"], f"üstteki satır kalkmadı: {e.satirlar()}")

# --- yavaş konuşmada yığın OLUŞMAMALI
e = Ekran()
e.ceviriliyor = "bir"
e.publish("bir", "Birinci satır.", " · taslak")
e.simdi = YIGIN_ICINDE + 1000
e.ceviriliyor = "iki"
e.publish("iki", "İkinci satır.", " · taslak")
bekle(e.satirlar() == ["İkinci satır."], f"yavaş konuşmada da yığın oluştu: {e.satirlar()}")

# --- geç gelen düzeltme üst sıradaki taslağın yerine geçsin
e = Ekran()
e.ceviriliyor = "A"
e.publish("A", "A taslak", " · taslak")
e.simdi = 500
e.ceviriliyor = "B"
e.publish("B", "B taslak", " · taslak")
bekle(e.satirlar() == ["^A taslak", "B taslak"], f"A üste çıkmadı: {e.satirlar()}")
e.simdi = 1000
bekle(e.publish("A", "A DÜZELTİLMİŞ", " · YZ") == "ust-sira", "geç düzeltme üst sıraya düşmedi")
bekle(e.satirlar() == ["^A DÜZELTİLMİŞ", "B taslak"],
      f"üst sıradaki taslak düzeltilmişle değişmedi: {e.satirlar()}")

# --- çok geç gelen düzeltme sonraki sahnenin üstüne düşmesin
e = Ekran()
e.ceviriliyor = "A"
e.publish("A", "A taslak", " · taslak")
e.simdi = 500
e.ceviriliyor = "B"
e.publish("B", "B taslak", " · taslak")
e.simdi = 500 + GEC_SINIR + 1
bekle(e.publish("A", "A DÜZELTİLMİŞ", " · YZ") == "gec-kaldi", "çok geç düzeltme yine de gösterildi")

# --- ayar kapalıyken eski davranış
e = Ekran(yigin=False)
e.ceviriliyor = "A"
e.publish("A", "A taslak", " · taslak")
e.simdi = 300
e.ceviriliyor = "B"
e.publish("B", "B taslak", " · taslak")
bekle(e.satirlar() == ["B taslak"], f"ayar kapalıyken de üst sıra var: {e.satirlar()}")
bekle(e.publish("A", "A DÜZELTİLMİŞ", " · YZ") == "gec-kaldi", "ayar kapalıyken üst sıra kullanıldı")

# --- uzun satır erken silinmesin
e = Ekran()
e.ceviriliyor = "uzun"
e.publish("uzun", UZUN, " · YZ")
e.simdi = 3000
bekle(not e.temizlenir_mi(), "uzun satır 3 sn'de silindi (eski davranış)")
e.simdi = e.okuma_suresi(UZUN)
bekle(e.temizlenir_mi(), "uzun satır hiç silinmiyor")


# ------------------------------- 4) ekran gürültüsü: taslak gecikmesi
#
# 11 Kasım kaydı (hızlı model E2B, YZ 0,97-2,60 sn): taslak hemen basılınca
# 23 satırın 23'ünde yazı değişiyordu, kullanıcı "çok karışık" dedi.
# İki kural eklendi:
#   a) Düzeltme taslağın AYNISIYSA ekran tazelenmez (kayıtta 5 satır).
#   b) Taslak yalnız yapay zekâ gecikirse çıkar; bekleme süresi son
#      çevirilerin hızına göre ayarlanır (sabit değil).

bekle("val taslakBekle: Long" in yakala, "taslak gecikmesi yok")
bekle("yzOrtMs" in yakala, "yapay zekâ süresi ölçülmüyor (gecikme sabit kalmış)")
bekle("yzOrtMs = (yzOrtMs * 2 + ms) / 3" in yakala, "ölçüm güncellenmiyor")
bekle("taslakIsi?.cancel()" in yakala, "cevap gelince bekleyen taslak iptal edilmiyor")
bekle("shownSource == source && ayniYazi(shownValue, value)" in yakala,
      "aynı yazı yeniden çiziliyor (noktalama farkı dahil)")
bekle(yakala.count("showSubtitle(quick, refined = true)") == 0,
      "atlama yolunda hâlâ ikinci bir çizim var")

# Red yollarında taslak İPTAL EDİLMEMELİ: orada ekrana çıkacak tek şey o.
_yz = yakala.split("TASLAK GECİKMELİ ÇIKAR")[1].split("TV: çeviriyi telefona yaptır")[0]
_red = _yz.split("YZ REDDEDİLDİ")[1].split("taslakIsi?.cancel()")[0]
bekle("return@launch" in _red, "red yolu beklendiği gibi durmuyor (test eskimiş)")
bekle(_yz.index("YZ REDDEDİLDİ") < _yz.index("taslakIsi?.cancel()"),
      "taslak, red denetiminden ÖNCE iptal ediliyor: reddedilince ekran boş kalır")

_m = re.search(r"\(yzOrtMs \+ (\d+)L\)\.coerceIn\((\d+)L, (\d+)L\)", yakala)
if not _m:
    hata.append("taslakBekle sınırları okunamadı")
else:
    _pay, _alt, _ust = (int(g) for g in _m.groups())
    bekle(_ust <= 2000, f"ekran {_ust} ms boş kalabiliyor, fazla uzun")
    bekle(_alt >= 500, f"alt sınır {_alt} ms, taslak neredeyse hemen çıkar")

    # Kaydın gerçek süreleriyle: değişim sayısı ve boş kalma ölçülüyor.
    SURELER = [1492, 2584, 1918, 1334, 1465, 2247, 1248, 1404, 1053, 2404, 1012,
               1984, 966, 1615, 1032, 1644, 1002, 1531, 2096, 1175, 1947, 1846, 1552]
    AYNI = {8, 12, 13, 16, 17}        # YZ çıktısı taslakla birebir aynı olan satırlar

    def akis(eski_akis):
        """Kaç satırda ekrandaki yazı değişiyor, ekran en çok ne kadar boş kalıyor."""
        ort, degisen, en_uzun_bos = 1500, 0, 0
        for i, yz_ms in enumerate(SURELER):
            if eski_akis:
                # Taslak hemen basılıyor, düzeltme aynı olsa bile yeniden çiziliyor.
                degisen += 1
                continue
            beklenen = max(_alt, min(_ust, ort + _pay))
            if yz_ms > beklenen:                       # taslak çıktı
                en_uzun_bos = max(en_uzun_bos, beklenen)
                if i not in AYNI:                      # aynıysa ekran tazelenmez
                    degisen += 1
            else:                                      # taslak hiç çıkmadı
                en_uzun_bos = max(en_uzun_bos, yz_ms)
            ort = (ort * 2 + yz_ms) // 3
        return degisen, en_uzun_bos

    eski, _ = akis(eski_akis=True)
    yeni, bos = akis(eski_akis=False)
    bekle(eski == len(SURELER), "eski akış ölçümü bozuk (test eskimiş)")
    bekle(yeni * 2 < eski, f"yazı değişimi yeterince azalmadı: {yeni}/{len(SURELER)}")
    bekle(bos <= 2000, f"ekran {bos} ms boş kalıyor")

    # Büyük model (E4B, 2,6-4,5 sn): taslak YİNE çıkmalı, ekran boş kalmasın.
    yavas_bekle = max(_alt, min(_ust, (1500 * 2 + 4000) // 3 + _pay))
    bekle(4533 > yavas_bekle, "yavaş modelde taslak hiç çıkmıyor, ekran saniyelerce boş kalır")


# --------------------------- 5) HİÇBİR REPLİK KAYBOLMASIN (9.45 gerilemesi)
#
# Taslak gecikmesi eklenince hızlı art arda gelen replikler hiç görünmeden
# kayboldu. 11 Kasım 08:01 kaydı, açılıştaki ilk blok:
#     OKUNAN: Yo! Crackheads! Woo!              -> hiç yazılmadı
#     OKUNAN: I guess they know we're coming.   -> hiç yazılmadı
#     OKUNAN: Alright, that's the block...      -> yazıldı
# Sebep: taslak beklerken yeni satır geldiği için iş atlanıyordu, yapay zekâ
# da yeni satır yüzünden iptal ediliyordu. İki kural eklendi:
#   a) Ekranda hiçbir şey yoksa taslak BEKLEMEDEN çıkar.
#   b) Beklerken satır geçerse taslak üst sırada gösterilir, atılmaz.

bekle("if (shownValue.isEmpty())" in yakala, "ekran boşken bekleme atlanmıyor")

# VARSAYILAN ANINDA. 9.45'te gecikme varsayılan geldi ve kullanıcı "geç okuyor,
# anlık ile alakası kalmadı" dedi. Gecikme artık bir TERCİH; varsayılanı kapalı.
bekle("var dogruyuBekle: Boolean" in ayar, "bekleme ayarı yok")
bekle('sp.getBoolean("dogruyuBekle", false)' in ayar,
      "bekleme varsayılan AÇIK geldi: uygulama anlık olmaktan çıkar")
bekle("if (!prefs.dogruyuBekle)" in yakala, "ayar kapalıyken taslak hemen çıkmıyor")
bekle("cbDogruyuBekle" in duzen, "ayarlar ekranında bekleme kutusu yok")
bekle("prefs.dogruyuBekle = cbDogruyuBekle.isChecked" in ana, "bekleme kutusu kaydedilmiyor")

# Okuma süresinin üst sınırı: 8 sn fazla yapışkandı, kaplama oyunun gerisinde kaldı.
_u = re.search(r"val OKUMA_EN_COK = (\d+)L", yakala)
if not _u:
    hata.append("okuma süresi üst sınırı bulunamadı")
else:
    bekle(int(_u.group(1)) <= 5000,
          f"çeviri {int(_u.group(1))} ms ekranda kalabiliyor, kaplama geride kalır")
bekle("fun ustSiradaGoster" in yakala, "üst sırada gösterme yardımcısı yok")
bekle("ustSiradaGoster(source, quick!!)" in yakala,
      "beklerken geçen satırın taslağı hâlâ atılıyor")


class Akis:
    """Ekranda ne göründüğünü izler; hiçbir repliğin kaybolmadığını doğrular."""

    def __init__(self, bekleme, eski_akis=False, yigin_acik=True):
        self.bekleme = bekleme
        self.eski_akis = eski_akis
        self.yigin_acik = yigin_acik
        self.alt = ""
        self.gorulen = []

    def replik(self, taslak, yz, yz_ms, sonraki_gelir):
        bostu = not self.alt
        if self.eski_akis or bostu:
            self.alt = taslak
            self.gorulen.append(taslak)
        elif yz is not None and yz_ms <= self.bekleme:
            pass                                   # YZ önce geldi, taslak iptal
        elif sonraki_gelir <= self.bekleme:
            if self.yigin_acik:                    # beklerken geçti: üst sıra
                self.gorulen.append(taslak)
        else:
            self.alt = taslak
            self.gorulen.append(taslak)
        if yz is not None and yz_ms < sonraki_gelir and yz != self.alt:
            self.alt = yz
            self.gorulen.append(yz)


if "_ust" in dir():
    BEKLEME = _ust            # en kötü durum: gecikme üst sınırda

    # Kayıttaki birinci blok: açılış, ekran boş, üç replik hızlı art arda.
    a = Akis(BEKLEME)
    a.replik("Hey! Crackheads! Woo!", None, 0, 900)
    a.replik("Sanırım geleceğimizi biliyorlar.", None, 0, 900)
    a.replik("Pekâlâ, Buradaki blok bu.", "Tamam, blok tam da burada.", 1998, 5000)
    for beklenen in ("Hey! Crackheads! Woo!", "Sanırım geleceğimizi biliyorlar.",
                     "Tamam, blok tam da burada."):
        bekle(beklenen in a.gorulen, f"replik kayboldu: {beklenen}")

    # İkinci blok: aynı desen.
    a = Akis(BEKLEME)
    a.replik("Havalı ol, yapma... biliyorsun.", None, 0, 800)
    a.replik("Ne?", None, 0, 800)
    a.replik("Şey, hiçbir şey.", "Hadi, hiç.", 1440, 5000)
    for beklenen in ("Havalı ol, yapma... biliyorsun.", "Ne?", "Hadi, hiç."):
        bekle(beklenen in a.gorulen, f"replik kayboldu: {beklenen}")

    # Sakin diyalogda gecikme HÂLÂ çalışmalı: taslak görünmemeli.
    a = Akis(BEKLEME)
    a.replik("İlk taslak", "İlk YZ", 1000, 6000)
    n = len(a.gorulen)
    a.replik("İkinci taslak", "İkinci YZ", 1000, 6000)
    bekle(a.gorulen[n:] == ["İkinci YZ"],
          f"sakin diyalogda taslak yine göründü (gecikme çalışmıyor): {a.gorulen[n:]}")

    # Yavaş yapay zekâda taslak yine çıkmalı, ekran saniyelerce boş kalmasın.
    a = Akis(BEKLEME)
    a.replik("İlk", "İlk YZ", 1000, 6000)
    n = len(a.gorulen)
    a.replik("Yavaş taslak", "Yavaş YZ", 4500, 6000)
    bekle(a.gorulen[n:] == ["Yavaş taslak", "Yavaş YZ"],
          f"yavaş yapay zekâda taslak çıkmadı: {a.gorulen[n:]}")

    # Yığın kapalıyken bile açılış replikleri kaybolmamalı (kural a yeterli).
    a = Akis(BEKLEME, yigin_acik=False)
    a.replik("Birinci", None, 0, 900)
    bekle("Birinci" in a.gorulen, "yığın kapalıyken açılış repliği kayboldu")


# -------------- kendi yazımız listesine İNGİLİZCE kaynak girmemeli
#
# "displayed" = "bu yazıyı biz çizdik, yeniden okuyup çevirme". İçine İngilizce
# kaynak konunca oyunun KENDİ altyazısı kendi yazımız sanılıp atlanıyordu:
#   ÇEVRİLMEDİ (kendi yazımız): I've always been there for you.

bekle("displayed.add(normalize(shownSource))" not in yakala,
      "kendi yazımız listesine İngilizce kaynak ekleniyor: gerçek altyazı atlanır")
bekle("shownValue.takeIf { it.isNotEmpty() }?.let { displayed.add(normalize(it)) }" in yakala,
      "menü temizlenince ekrandaki Türkçe çeviri listede tutulmuyor")
for _ekleme in re.findall(r"displayed\.add\(normalize\((\w+)\)\)", yakala):
    bekle(_ekleme not in ("shownSource", "source", "text", "raw"),
          f"displayed listesine çeviri değil kaynak ekleniyor: {_ekleme}")


# ------------------------------------------------------------------- sonuç

if hata:
    print("Yığın testi DÜŞTÜ:")
    for h in hata:
        print("  ✗", h)
    sys.exit(1)

print(f"Yığın testi geçti (pencere {YIGIN_ICINDE} ms, üst sıra {ONCEKI_TUT} ms, "
      f"geç sınır {GEC_SINIR} ms; kayıttaki 23 satırda yazı değişimi {eski} -> {yeni}, "
      f"ekran en çok {bos} ms boş).")
