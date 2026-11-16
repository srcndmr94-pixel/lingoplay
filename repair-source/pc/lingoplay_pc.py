"""
GameLingo PC (eski adıyla LingoPlay) — ekrandaki İngilizce altyazıyı anlık Türkçeye çevirir.

Windows için tek dosyalık program. Ekranın alt şeridini yakalar, yazıyı okur,
Türkçeye çevirir ve ekranın üstünde bir kutuda gösterir. Çeviriyi iki kaynaktan biri yapar (seçilebilir):
  - Bilgisayarın kendisi (varsayılan): internetsiz çeviri modeli, ilk açılışta bir kez indirilir.
  - Telefondaki GameLingo'nun yapay zekâsı: aynı Wi-Fi üzerinden, kodla eşleşir.

Android sürümüyle aynı mantık: kalıp sözlüğü önce, sonra kalıcı hafıza,
en son yapay zekâ. Bir replik için ekrana bir kez yazılır, değişmez.
"""

import collections
import itertools
import json
import math
import socket
import os
import re
import sys
import threading
import time
import tkinter as tk
from tkinter import ttk

try:
    import mss
except ImportError:
    mss = None

try:
    from rapidocr_onnxruntime import RapidOCR
except ImportError:
    RapidOCR = None

try:
    from kelimeler import KELIMELER   # İngilizce kelime listesi: OCR harf karışıklığını düzeltmek için
except Exception:
    KELIMELER = frozenset()


SURUM = "5.15"

APP_DIR = os.path.join(os.path.expanduser("~"), ".lingoplay")
CONFIG_PATH = os.path.join(APP_DIR, "ayarlar.json")
MEMORY_PATH = os.path.join(APP_DIR, "hafiza.json")

VARSAYILAN = {
    "oyun": "",
    "tur": "Oyun",
    "ust_yuzde": 74,
    "alt_yuzde": 99,
    "sol_yuzde": 8,
    "sag_yuzde": 92,
    "aralik_ms": 350,
    "ceviri_kaynagi": "bilgisayar",   # "bilgisayar" (internetsiz, varsayılan) | "telefon"
    "telefon_kod": "",
    "telefon_ip": "",
    "yazi_boyutu": 20,
    "konusan_goster": True,
    "ekran": 1,
}


# ---------------------------------------------------------------- kalıp sözlüğü

KALIPLAR = {
    "yes": "Evet", "no": "Hayır", "okay": "Tamam", "ok": "Tamam",
    "alright": "Pekâlâ", "all right": "Pekâlâ", "sure": "Tabii",
    "of course": "Elbette", "exactly": "Aynen öyle", "maybe": "Belki",
    "never": "Asla", "always": "Her zaman", "nothing": "Hiçbir şey",
    "i dont know": "Bilmiyorum", "i dont understand": "Anlamıyorum",
    "i see": "Anlıyorum", "got it": "Anladım", "understood": "Anlaşıldı",
    "forget it": "Boş ver", "never mind": "Boş ver", "no way": "Mümkün değil",
    "damn it": "Kahretsin", "what the hell": "Bu da ne böyle",
    "oh no": "Olamaz", "my god": "Tanrım",
    "hello": "Merhaba", "hi": "Selam", "hey": "Hey",
    "good morning": "Günaydın", "good evening": "İyi akşamlar",
    "good night": "İyi geceler", "goodbye": "Hoşça kal", "farewell": "Elveda",
    "see you": "Görüşürüz", "take care": "Kendine iyi bak",
    "thank you": "Teşekkür ederim", "thanks": "Teşekkürler",
    "youre welcome": "Rica ederim", "please": "Lütfen",
    "sorry": "Üzgünüm", "im sorry": "Üzgünüm", "excuse me": "Affedersiniz",
    "forgive me": "Beni bağışla", "congratulations": "Tebrikler",
    "well done": "Aferin", "good luck": "Bol şans",
    "look out": "Dikkat et", "watch out": "Dikkat et",
    "careful": "Dikkatli ol", "be careful": "Dikkatli ol",
    "get down": "Eğil", "take cover": "Siper al", "behind you": "Arkanda",
    "run": "Kaç", "move": "Kımılda", "go": "Git", "lets go": "Gidelim",
    "come on": "Hadi", "hurry": "Çabuk ol", "hurry up": "Acele et",
    "wait": "Bekle", "hold on": "Bir dakika", "stop": "Dur",
    "stop it": "Kes şunu", "stay back": "Geride dur", "stay here": "Burada kal",
    "follow me": "Beni takip et", "this way": "Bu taraftan",
    "over here": "Buraya", "get up": "Kalk", "im on it": "Hallediyorum",
    "leave it to me": "Bana bırak", "cover me": "Beni koru",
    "reloading": "Dolduruyorum", "theyre coming": "Geliyorlar",
    "hes dead": "Öldü", "dont move": "Kıpırdama", "dont": "Yapma",
    "its a trap": "Bu bir tuzak", "were surrounded": "Kuşatıldık",
    "i need help": "Yardıma ihtiyacım var", "help me": "Bana yardım et",
    "are you okay": "İyi misin", "are you all right": "İyi misin",
    "im fine": "İyiyim",
    "who are you": "Sen kimsin", "what is this": "Bu ne",
    "whats this": "Bu ne", "whats going on": "Neler oluyor",
    "what happened": "Ne oldu", "what do you mean": "Ne demek istiyorsun",
    "what do you want": "Ne istiyorsun", "where are you": "Neredesin",
    "where are we": "Neredeyiz", "why": "Neden", "why not": "Neden olmasın",
    "how": "Nasıl", "are you sure": "Emin misin",
    "do you understand": "Anlıyor musun", "can you hear me": "Beni duyuyor musun",
    "whos there": "Kim var orada", "what now": "Şimdi ne olacak",
    "i promise": "Söz veriyorum", "i swear": "Yemin ederim",
    "trust me": "Bana güven", "listen to me": "Beni dinle", "listen": "Dinle",
    "look at me": "Bana bak", "tell me": "Anlat bana",
    "its over": "Her şey bitti", "its too late": "Çok geç",
    "it doesnt matter": "Fark etmez", "theres no time": "Vakit yok",
    "i knew it": "Biliyordum", "youre right": "Haklısın",
    "youre wrong": "Yanılıyorsun", "im serious": "Ciddiyim",
    "are you serious": "Ciddi misin", "thats enough": "Bu kadar yeter",
    "enough": "Yeter", "calm down": "Sakin ol", "relax": "Rahatla",
    "dont worry": "Merak etme", "be quiet": "Sessiz ol",
    "shut up": "Kes sesini", "leave me alone": "Beni yalnız bırak",
    "get out": "Defol", "come with me": "Benimle gel",
    "stay with me": "Yanımda kal", "im coming": "Geliyorum",
    "im here": "Buradayım", "thank god": "Çok şükür",
    "i miss you": "Seni özledim", "i love you": "Seni seviyorum",
    "loading": "Yükleniyor", "saving": "Kaydediliyor",
    "checkpoint reached": "Kontrol noktasına ulaşıldı",
    "objective complete": "Görev tamamlandı", "new objective": "Yeni görev",
    "mission failed": "Görev başarısız", "level up": "Seviye atladın",
    "you died": "Öldün", "game over": "Oyun bitti", "continue": "Devam et",
    "skip": "Atla", "settings": "Ayarlar", "options": "Seçenekler",
    "inventory": "Envanter", "map": "Harita", "exit": "Çıkış",
}

ARAYUZ_KELIMELERI = {
    "l1", "l2", "l3", "r1", "r2", "r3", "ps", "touchpad", "skip",
    "options", "start", "select", "esc", "tab", "enter", "space",
}

KISALTMALAR = [
    ("dont", "don't"), ("doesnt", "doesn't"), ("didnt", "didn't"),
    ("cant", "can't"), ("couldnt", "couldn't"), ("wont", "won't"),
    ("wouldnt", "wouldn't"), ("shouldnt", "shouldn't"), ("isnt", "isn't"),
    ("arent", "aren't"), ("wasnt", "wasn't"), ("werent", "weren't"),
    ("havent", "haven't"), ("hasnt", "hasn't"), ("im", "I'm"),
    ("ive", "I've"), ("ill", "I'll"), ("youre", "you're"),
    ("youve", "you've"), ("hes", "he's"), ("shes", "she's"),
    ("thats", "that's"), ("theres", "there's"), ("theyre", "they're"),
    ("lets", "let's"), ("whats", "what's"),
]


def sadelestir(metin):
    return re.sub(r"[^a-z0-9 ]", "", metin.lower()).strip()


_BOZUK_IZINLI = re.compile(r"^(?:Mc|Mac|De|Di|La|Le|Van|Von|iP|eB)")


def kelime_bozuk_mu(w0):
    """Telefondaki OcrQuality'nin aynısı: harf karışmış/yarım okunmuş kelime mi?"""
    w = w0.strip(".,!?\"'…-)([]:;")
    if sum(c.isalpha() for c in w) < 3:
        return False
    if re.fullmatch(r"(?i)(?:o+h+|a+h+|h+m+|z+|m+h*m+|u+h+|e+h+|w+o+a*h*|sh+|ah+a+)[.!?,]*", w):
        return False
    if re.search(r"[a-zçğıöşü][A-ZÇĞİÖŞÜ]", w) and not _BOZUK_IZINLI.match(w) and not w.isupper():
        return True
    if re.search(r"[^\W\d_]\d|\d[^\W\d_]", w) and not re.fullmatch(r"(?i)\d+(st|nd|rd|th|s|am|pm|k|m|x)", w):
        return True
    if re.search(r"[^\W\d_][.!?;,|][^\W\d_]", w) and not re.fullmatch(r"(?:[A-Za-z]\.){2,}", w0.strip()):
        return True
    if re.search(r"(?i)([a-z])\1\1", w):
        return True
    return False


def altyazi_satirlari(kutular, genislik):
    """OCR kutularından altyazıyı seçer. kutular: (üst, alt, sol, sağ, metin).
    - Altyazı ortadadır: ortası kenarda (%18'in dışında) kalan kutular atılır (ortada kutu varsa).
    - Altyazı en alttaki satır öbeğidir: araya boşluk girmiş üstteki yazılar (pencere, menü) atılır.
    - En çok 3 satır."""
    if not kutular:
        return ""
    if genislik:
        orta = [k for k in kutular if 0.18 <= ((k[2] + k[3]) / 2) / genislik <= 0.82]
        if orta:
            kutular = orta
    kutular = sorted(kutular, key=lambda k: k[0])
    yukseklik = sorted(k[1] - k[0] for k in kutular)[len(kutular) // 2] or 1
    # satırlara topla (aynı yükseklikteki kutular aynı satır)
    satirlar = []
    for k in kutular:
        if satirlar and abs(k[0] - satirlar[-1]["ust"]) < yukseklik * 0.6:
            satirlar[-1]["kutular"].append(k)
            satirlar[-1]["alt"] = max(satirlar[-1]["alt"], k[1])
        else:
            satirlar.append({"ust": k[0], "alt": k[1], "kutular": [k]})
    # en alttan yukarı: satır arası bir satır yüksekliğinden fazla açılınca dur
    secilen = [satirlar[-1]]
    for st in reversed(satirlar[:-1]):
        if secilen[0]["ust"] - st["alt"] > yukseklik * 1.0 or len(secilen) >= 3:
            break
        secilen.insert(0, st)
    return " ".join(" ".join(k[4] for k in sorted(st["kutular"], key=lambda k: k[2])) for st in secilen)


_TR_HARF = "çğışöüÇĞİŞÖÜ"


def turkce_yazi_mi(metin):
    """Satır zaten Türkçe mi? Windows arayüzü, YouTube menüsü, Türkçe altyazı.

    Kullanıcının 7 Kasım kaydında bunlar İngilizce sanılıp "çevriliyor" ve
    bozuluyordu: "Kalite" -> "Kalit", "Altyazıla" -> "Altınyağıla",
    "Görev gücündeki arkadaşlarımız yolda." -> "Görev Gücündeki Arkadaşlarimizz."

    Beş harften uzun tek bir kelimede Türkçe harf olması yeter: İngilizce
    altyazıda böyle bir kelime bulunmaz.
    """
    for k in re.split(r"[^\w]+", metin, flags=re.UNICODE):
        if len(k) >= 5 and any(c in _TR_HARF for c in k):
            return True
    return False


def bozuk_mu(metin):
    kelimeler = [w for w in metin.split() if sum(c.isalpha() for c in w) >= 3]
    if not kelimeler:
        return False
    bozuk = sum(1 for w in kelimeler if kelime_bozuk_mu(w))
    return bozuk == 1 if len(kelimeler) == 1 else (bozuk > 0 and bozuk * 4 >= len(kelimeler))


def altyazi_rengi_var_mi(goruntu, oran=0.0015):
    """Bölgede beyaz ya da sarı (altyazı rengi) piksel var mı? mss görüntüsü BGR sırasındadır."""
    try:
        b, g, r = goruntu[:, :, 0], goruntu[:, :, 1], goruntu[:, :, 2]
        beyaz = (b > 185) & (g > 185) & (r > 185)
        sari = (r > 185) & (g > 170) & (b < 140)
        return float((beyaz | sari).mean()) >= oran
    except Exception:
        return True


# OCR'ın sık karıştırdığı harfler: düşük çözünürlükte "t" -> "l", "o" -> "a", "f" -> "I", "T" -> "1"
# OCR'ın karıştırdığı harfler. Her harfin birkaç adayı olabilir; hepsi sözlüğe
# sorulur ve yalnız sözlükte karşılığı olan kabul edilir.
#
# 7 Kasım kaydından eklenenler:
#   I -> l : "Iooks just like him" -> "looks", "Ioved it" -> "loved"
#   f -> t : "Lefs see how this goes" -> "Lets" -> "Let's"
#   q -> g : "a tough quy" -> "guy"
_KARISIK = {
    "l": ["t", "i"],
    "I": ["t", "l"],
    "1": ["t", "l"],
    "a": ["o"],
    "0": ["o"],
    "f": ["t"],
    "q": ["g"],
}
_I_ISIMLER = {"ian", "ivan", "iris", "isaac", "ida", "ike", "ina", "ira", "ines", "ivy", "igor", "ilsa", "ilya", "imani",
              "ingrid", "ira", "isla", "ismail", "ivo", "izzy", "iggy",
              "iran", "iraq", "italy", "india", "israel", "ireland", "idaho", "iowa"}
_TOKEN = re.compile(r"^([^\w']*)([\w']+)([^\w']*)$")

# Kısa kelimelerde birden çok aday sözlükte bulunabiliyor ("Il" -> "ll" ve "It").
# Gerçekte kullanılan kısa kelimeler öncelikli.
_YAYGIN_KISA = {
    "it", "is", "in", "to", "of", "he", "be", "we", "do", "go", "so", "no",
    "my", "me", "at", "as", "an", "if", "or", "up", "us", "by", "on", "am",
    "the", "and", "you", "that", "this", "was", "for", "are", "but", "not",
    "all", "can", "had", "her", "his", "him", "out", "get", "got", "let",
    "see", "say", "she", "too", "two", "way", "who", "why", "how", "now",
}


def _aday_kelimeler(kelime):
    yerler = [i for i, c in enumerate(kelime) if c in _KARISIK]
    if not yerler or len(yerler) > 6:
        return
    uretilen = 0
    for adet in (1, 2, 3):
        for secim in itertools.combinations(yerler, adet):
            # Seçilen her yerin HER adayı denenir (bir harfin birden çok karşılığı olabilir).
            for degisim in itertools.product(*(_KARISIK[kelime[i]] for i in secim)):
                harfler = list(kelime)
                for i, yeni in zip(secim, degisim):
                    harfler[i] = yeni
                uretilen += 1
                if uretilen > 300:
                    return
                yield "".join(harfler)


def harfleri_duzelt(metin):
    """Sözlükte olmayan kelimede karışık harfleri dener: "thal" -> "that", "lhey're" -> "they're",
    "Dan'l" -> "Don't", "Ieel" -> "feel". Sonuç sözlükte değilse kelimeye dokunmaz.
    Cümle ortasında Büyük harfle başlayan kelimeler isim sayılır, ellenmez."""
    if not KELIMELER:
        return metin
    parcalar = metin.split(" ")
    cikti = []
    cumle_basi = True
    for parca in parcalar:
        m = _TOKEN.match(parca)
        if not m:
            cikti.append(parca)
            # Harf içermeyen parça ("»", "-", "♪") cümleyi bitirmez ve cümle
            # başı olma durumunu BOZMAZ. Netflix altyazıları "» " ile başlıyor;
            # eskiden ardından gelen kelime cümle ortası sanılıp isim kabul
            # ediliyor ve hiç düzeltilmiyordu ("» Lefs see" -> "» Lets see").
            if any(c.isalpha() for c in parca):
                cumle_basi = parca.endswith((".", "!", "?"))
            continue
        on, govde, son = m.groups()
        yeni = govde
        kucuk = govde.lower()
        # Cümle ortasında Büyük harfle başlayan = isim (ellenmez). "I" ile başlayanlar hariç: "Ieel", "Il's", "Ihey"
        isim_gibi = govde[:1].isupper() and govde[1:].islower() and not cumle_basi and govde[0] != "I"
        if (len(govde) >= 2 and kucuk not in KELIMELER and not isim_gibi and not govde.isupper() and kucuk not in _I_ISIMLER
                and not any(c.isdigit() for c in govde.strip("1"))):
            # "Ieel" -> "feel" (I baştaki f)
            denenecek = list(_aday_kelimeler(govde))
            if govde.startswith("I") and len(govde) > 2:
                denenecek.insert(0, "f" + govde[1:])
            # Sözlükte karşılığı olan TÜM adaylar toplanır, sonra seçilir.
            # Tek tek bakıp ilkini almak yanlış sonuç veriyordu: "Il" için
            # "ll" (sözlükte var ama kimse kullanmaz) "It"ten önce geliyordu.
            uyanlar = [a for a in denenecek if a.lower() in KELIMELER]
            secim = next((a for a in uyanlar if a.lower() in _YAYGIN_KISA), None)
            if secim is None:
                secim = uyanlar[0] if uyanlar else None
            if secim is not None:
                    aday = secim
                    yeni = aday
                    # "Ieel" -> "feel" (cümle ortası küçük), "1hey" -> "They" (cümle başı büyük)
                    if govde[0] in "I1":
                        if cumle_basi:
                            yeni = yeni[0].upper() + yeni[1:]
                    elif govde[0].isupper():
                        yeni = yeni[0].upper() + yeni[1:]
        cikti.append(on + yeni + son)
        cumle_basi = (govde + son).endswith((".", "!", "?"))
        if not any(c.isalpha() for c in govde):
            cumle_basi = True
    sonuc = " ".join(cikti)
    # "lo pick up" -> "to pick up" ("lo" sözlükte var, o yüzden ayrıca)
    sonuc = re.sub(r"\blo (?=(pick|collect|go|get|be|do|see|say|tell|take|make|come|find|kill|help|stop|talk|leave|look|keep)\b)", "to ", sonuc)
    return sonuc


_KISA_SIK = {"to", "of", "in", "on", "is", "it", "at", "be", "we", "he", "me", "my", "no", "so", "up", "do",
             "go", "an", "as", "by", "or", "us", "if", "oh", "ok", "am", "hi", "yo"}


def _bol(kelime):
    """Bitişik okunmuş küçük harfli kelimeyi sözlükle böler: "topickup" -> "to pick up".
    En az parçalı bölme seçilir; bölünemezse None."""
    n = len(kelime)
    en_iyi = [None] * (n + 1)
    en_iyi[0] = (0.0, [])
    for i in range(1, n + 1):
        for j in range(max(0, i - 15), i):
            if en_iyi[j] is None:
                continue
            w = kelime[j:i]
            if len(w) == 1:
                if w not in ("a", "i"):
                    continue
                bedel = 1.3
            elif len(w) == 2 and w not in _KISA_SIK:
                continue   # "mi", "th" gibi: yabancı kelimeyi saçma parçalara bölmesin
            elif w in KELIMELER:
                bedel = 1.0
            else:
                continue
            aday = (en_iyi[j][0] + bedel, en_iyi[j][1] + [w])
            if en_iyi[i] is None or aday[0] < en_iyi[i][0]:
                en_iyi[i] = aday
    if en_iyi[n] is None or len(en_iyi[n][1]) < 2:
        return None
    parcalar = en_iyi[n][1]
    # "herm a no" gibi: tek harfli "a" yalnızca başta/sonda yapışmış olabilir ("givea", "aman")
    if any(w == "a" for w in parcalar[1:-1]):
        return None
    # Kısa kelime ("cabran" -> "cab ran" olmasın): içinde bağlaç gibi kısa bir parça yoksa bölme
    if n <= 6 and not any(w in _KISA_SIK or len(w) == 1 for w in parcalar):
        return None
    return ["I" if w == "i" else w for w in parcalar]


def bitisikleri_ayir(metin):
    """OCR'ın boşluk kaçırdığı yerleri açar:
    "Yo,is" -> "Yo, is" · "thispointI" -> "this point I" · "andwhatthefuckgoingon" -> "and what the fuck going on"."""
    # Noktalama sonrası boşluk ("Yo,is", "topickup.ForBoobie"); "U.S.", "a.m." gibi kısaltmalara dokunmaz.
    metin = re.sub(r"(?<=[A-Za-z]{2})([,;])(?=[A-Za-z])", r"\1 ", metin)
    metin = re.sub(r"(?<=[A-Za-z][a-z])([.!?])(?=[A-Z][a-z])", r"\1 ", metin)   # "pickup.For" (ama "Fi!st" değil)
    if not KELIMELER:
        return metin
    # küçük→Büyük geçişi ("thispointI", "YoRay"); McDonald, iPhone gibi bilinenler hariç
    metin = " ".join(p if _BOZUK_IZINLI.match(p) else re.sub(r"(?<=[a-z])(?=[A-Z])", " ", p)
                     for p in metin.split(" "))
    cikti = []
    for parca in metin.split(" "):
        m = re.fullmatch(r"([\"(¿¡-]*)([a-z]{5,})('(?:t|s|re|ll|m|ve|d))?([^a-z']*)", parca)
        if (m and (m.group(2) + (m.group(3) or "")) not in KELIMELER and m.group(2) not in KELIMELER
                and not any(a.lower() in KELIMELER for a in _aday_kelimeler(m.group(2)))):
            # (harf karışmışsa — "looled" -> "tooled" — bölme; harfleri_duzelt halleder)
            bolunmus = _bol(m.group(2))
            if bolunmus:
                # "reallydon't" -> "really don't": ek son parçaya yapışır
                parca = m.group(1) + " ".join(bolunmus) + (m.group(3) or "") + m.group(4)
        else:
            # "Ireally" -> "I really" (baştaki I kelimeye yapışmış)
            m2 = re.fullmatch(r"([\"(-]*)I([a-z]{4,})([^a-z]*)", parca)
            if m2 and ("i" + m2.group(2)) not in KELIMELER:
                if m2.group(2) in KELIMELER:   # yalnızca kalan tam bir kelimeyse ("Ireland" -> dokunma)
                    parca = m2.group(1) + "I " + m2.group(2) + m2.group(3)
        cikti.append(parca)
    return " ".join(cikti)


_KESME_ZAMIR = re.compile(r"\b(I|i|We|we|You|you|They|they)l(ve|ll|re|d|m)\b")
_KESME_TEKIL = re.compile(r"\b(He|he|She|she|It|it|That|that|There|there|Who|who|Let|let)l(s|ll|d)\b")
_KESME_OLUMSUZ = re.compile(r"\b(\w+n)lt\b", re.IGNORECASE)
_KESME_KUCUK_I = re.compile(r"\bi'(m|ve|ll|d)\b")

# ------------------------------------------------- büyük I, rakam 1 okunuyor
#
# OCR "I"yı tek başına 1 (bir) okuyor: "1 don't know", "1'm sorry".
# Tek harfli olduğu için sözlüklü onarıma girmiyor (orada 2+ harf şartı var).
# Telefon tarafındaki TextFix.kt ile AYNI kurallar; biri değişirse öteki de.
#
# Gerçek sayıyı bozmamak için her kural dar: rakamın ardından İngilizce bir
# kısaltma, olumsuz fiil ya da bilinen bir fiil gelmeli. "Chapter 1",
# "1 of 3", "1 bottle", "1-hour" gibi gerçek sayımlara dokunulmuyor.
_BIR_KESME = re.compile(r"(?<![^\W\d_])(?<!\d)1['’](m|ve|ll|d|re)(?![^\W\d_])")
_BIR_AM = re.compile(r"(?<![^\W\d_])(?<!\d)[I1]['’]am(?![^\W\d_])")
_BIR_OLUMSUZ = re.compile(r"(?<![^\W\d_])(?<!\d)1(?=\s+[a-z]+n['’]?t(?![^\W\d_]))")
_BIR_KEKEME = re.compile(r"(?<![^\W\d_])(?<!\d)1(?=[-–—]\s)")
_BIR_FIIL = re.compile(r"(?<![^\W\d_])(?<!\d)1(\s+)([a-z][\w'’]*)(?![^\W\d_])")

_I_FIILLERI = {
    "wonder", "may", "absolve", "pity", "swear", "suppose", "guess", "reckon",
    "promise", "believe", "remember", "forget", "hope", "wish", "mean", "need",
    "want", "know", "think", "said", "say", "saw", "see", "feel", "felt",
    "hate", "love", "like", "miss", "owe", "agree", "admit", "doubt", "expect",
    "intend", "prefer", "regret", "trust", "understand", "appreciate", "assume",
    "imagine", "realize", "suggest", "insist", "refuse", "decide", "heard",
    "hear", "found", "find", "told", "tell", "gave", "give", "took", "take",
    "came", "come", "went", "got", "get", "had", "have", "was", "am",
    "will", "would", "should", "could", "can", "must", "did", "do",
    "never", "always", "just", "really", "still", "only",
    "thought", "brought", "kept", "left", "made", "make", "put", "run", "sent",
    "gonna", "gotta", "wanna", "bet", "care", "dare", "deserve", "ought",
    "used", "tried", "try", "knew", "forgot", "swore", "wanted", "needed",
    "seen", "been", "figured", "noticed", "lost", "lose",
}


def biri_i_yap(metin):
    """Tek başına duran 1 rakamını, "I" olduğu belli olan yerlerde düzeltir."""
    metin = _BIR_KESME.sub(r"I'\1", metin)
    metin = _BIR_AM.sub("I am", metin)
    metin = _BIR_OLUMSUZ.sub("I", metin)
    metin = _BIR_KEKEME.sub("I", metin)

    def _f(m):
        kelime = m.group(2).strip("'’").lower()
        sonra = metin[m.end():]
        # "1 can of soup": fiil listesinde olsa da ardından "of" gelirse sayımdır.
        if kelime in _I_FIILLERI and not sonra.startswith(" of ") and sonra != " of":
            return "I" + m.group(1) + m.group(2)
        return m.group(0)

    return _BIR_FIIL.sub(_f, metin)


def kesme_isaretini_onar(metin):
    """Kesme işareti "l" okunmuş: "Youlre" -> "You're", "didnlt" -> "didn't".

    Kullanıcının 7 Kasım kaydından bu hata onlarca satırda var:
      "Youlre cute. I didnlt think we Iooked"  "something Ilve never"
      "Like, I donlt even wanna hear that"     "Well, welre picking something up"
      "actually ilm going to say no."          "Letls see how this goes."

    GÜVENLİK: değiştirilecek kelime sözlükte VARSA dokunulmaz. Yoksa "weld"
    -> "we'd", "held" -> "he'd" gibi gerçek kelimeler bozulurdu.
    """
    def degistir(yazi, desen, yerine):
        def _f(m):
            if KELIMELER and m.group(0).lower() in KELIMELER:
                return m.group(0)        # gerçek kelime: dokunma
            return m.expand(yerine)
        return desen.sub(_f, yazi)

    metin = degistir(metin, _KESME_ZAMIR, r"\1'\2")
    metin = degistir(metin, _KESME_TEKIL, r"\1'\2")
    metin = degistir(metin, _KESME_OLUMSUZ, r"\1't")
    # "ilm" -> "i'm" oldu; başı büyütülmeli.
    metin = _KESME_KUCUK_I.sub(r"I'\1", metin)
    return metin


def metni_onar(ham):
    metin = ham.replace("…", "...")
    metin = bitisikleri_ayir(re.sub(r"\s+", " ", metin).strip())
    # SIRA ÖNEMLİ: kesme işareti onarımı harf düzeltmesinden ÖNCE gelmeli.
    # Tersi olursa "Ilve" (= I've) harf düzeltmesinde "live" olup kaybediliyor.
    metin = kesme_isaretini_onar(metin)
    metin = biri_i_yap(metin)
    metin = harfleri_duzelt(metin)
    # Sık OCR harf hataları: "Thcy" -> "They", "thc" -> "the"
    metin = re.sub(r"\b([Tt])hcy\b", r"\1hey", metin)
    metin = re.sub(r"\b([Tt])hc\b", r"\1he", metin)
    metin = re.sub(r"\s+", " ", metin).strip(" -—\"'")
    for yanlis, dogru in KISALTMALAR:
        # Büyük harf korunur: "Lets go" -> "Let's go" (eskiden "let's go" oluyordu).
        def _k(m, d=dogru):
            return d[0].upper() + d[1:] if m.group(0)[:1].isupper() else d
        metin = re.sub(rf"\b{yanlis}\b", _k, metin, flags=re.IGNORECASE)
    return metin.strip()


def konusani_ayir(metin):
    """'JAMES: Merhaba' → ('JAMES', 'Merhaba'). İsim çeviriye girmez."""
    yer = metin.find(":")
    if 0 < yer <= 24:
        ad = metin[:yer].strip()
        geri = metin[yer + 1:].strip()
        if len(geri) > 1 and all(c.isalpha() or c in " .'-" for c in ad):
            return ad, geri
    return None, metin


# -------------------------------------------------------------------- hafıza

class Hafiza:
    """Çevrilen replikler diske yazılır; tekrar edince bedava ve anında gelir."""

    def __init__(self):
        self.veri = {}
        try:
            with open(MEMORY_PATH, encoding="utf-8") as f:
                self.veri = json.load(f)
        except Exception:
            self.veri = {}
        self.kilit = threading.Lock()

    def al(self, metin):
        return self.veri.get(sadelestir(metin))

    def koy(self, metin, ceviri):
        with self.kilit:
            self.veri[sadelestir(metin)] = ceviri
            if len(self.veri) > 5000:
                self.veri = dict(list(self.veri.items())[-3000:])
            try:
                os.makedirs(APP_DIR, exist_ok=True)
                with open(MEMORY_PATH, "w", encoding="utf-8") as f:
                    json.dump(self.veri, f, ensure_ascii=False)
            except Exception:
                pass


# ------------------------------------------------------------------- çeviri

# ---------------------------------------------------------- telefon köprüsü

class TelefonKopru:
    """Çeviriyi telefondaki GameLingo'nun yapay zekâsına yaptırır (internet ve anahtar gerekmez).
    Telefonda: GameLingo → "TV ve bilgisayara çeviri sağla". GameLingo TV ile aynı protokol:
      Bulma (UDP 47322, yayın): "GLFIND <kod>" -> "GLHERE"
      Deneme (TCP 47321):       "GL1 <kod> PING" -> "PONG 1/0"
      Çeviri (TCP 47321):       "GL1 <kod> <sıra> <öncekiSayısı>" + tür + ad + satır + öncekiler
                                -> "OK <çeviri>" | "SKIP" | "ERR <sebep>"
      Hızlı taslak:             başlığın sonuna " Q" -> telefonun hızlı çevirisi anında
    Arayüz: cevir(metin), son_hata, son_sure, atlandi."""

    TCP_PORT = 47321
    UDP_PORT = 47322

    def __init__(self, ayarlar):
        self.ayarlar = ayarlar
        self.son_hata = None
        self.son_sure = 0.0
        self.atlandi = False
        self.sira = 0
        self.onceki = []
        self.kilit = threading.Lock()

    @staticmethod
    def _tek(s):
        return (s or "").replace("\n", " ").replace("\r", " ").strip()

    def kod(self):
        return self._tek(str(self.ayarlar.get("telefon_kod", "")))

    def _istek(self, host, govde, sure):
        try:
            with socket.create_connection((host, self.TCP_PORT), timeout=2.0) as s:
                s.settimeout(sure)
                s.sendall(govde.encode("utf-8"))
                veri = b""
                while not veri.endswith(b"\n") and len(veri) < 65536:
                    parca = s.recv(4096)
                    if not parca:
                        break
                    veri += parca
                return veri.decode("utf-8", "replace").strip()
        except Exception:
            return None

    def bul(self, sure=2.5):
        """Telefonu kodla ağda arar; bulursa adresini döner."""
        kod = self.kod()
        if not kod:
            return None
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
                s.settimeout(sure)
                s.sendto(f"GLFIND {kod}".encode("utf-8"), ("255.255.255.255", self.UDP_PORT))
                veri, adres = s.recvfrom(64)
                if veri.decode("utf-8", "replace").startswith("GLHERE"):
                    return adres[0]
        except Exception:
            pass
        return None

    def ping(self, host):
        """None: ulaşılamadı; True/False: telefonda yapay zekâ hazır mı."""
        r = self._istek(host, f"GL1 {self.kod()} PING\n", 3.0)
        if not r or not r.startswith("PONG"):
            return None
        return r.endswith("1")

    def baglan(self):
        """Kayıtlı adresi dener, olmazsa arar. (durum metni, hazır mı) döner."""
        if not self.kod():
            return "Telefon kodu girilmemiş", False
        host = self._tek(self.ayarlar.get("telefon_ip", ""))
        p = self.ping(host) if host else None
        if p is None:
            bulunan = self.bul()
            if bulunan:
                host = bulunan
                self.ayarlar["telefon_ip"] = bulunan
                p = self.ping(host)
        if p is None:
            return "Telefon bulunamadı. Telefonda \"TV ve bilgisayara çeviri sağla\" açık mı, aynı Wi-Fi'da mısınız?", False
        if p is False:
            return f"Telefon bulundu ({host}) ama yapay zekâ modeli hazır değil", False
        return f"Telefon bağlı ✓ ({host})", True

    def taslak(self, metin):
        """Telefonun hızlı (yapay zekâsız) çevirisi: anında gelir, ekranda önce bu görünür."""
        kod = self.kod()
        host = self._tek(self.ayarlar.get("telefon_ip", ""))
        if not kod or not host:
            return None
        govde = (f"GL1 {kod} 0 0 Q\n"
                 f"{self._tek(self.ayarlar.get('tur') or 'Oyun')}\n"
                 f"{self._tek(self.ayarlar.get('oyun') or '')}\n"
                 f"{self._tek(metin)}\n")
        r = self._istek(host, govde, 3.0)
        if r and r.startswith("OK "):
            return r[3:].strip() or None
        return None

    def cevir(self, metin):
        self.son_sure = 0.0
        self.atlandi = False
        baslangic = time.time()
        kod = self.kod()
        if not kod:
            self.son_hata = "Telefon kodu girilmemiş (Çeviri → Ayarlar)"
            return None
        host = self._tek(self.ayarlar.get("telefon_ip", ""))
        if not host:
            host = self.bul()
            if not host:
                self.son_hata = "Telefon bulunamadı"
                return None
            self.ayarlar["telefon_ip"] = host
        with self.kilit:
            self.sira += 1
            sira = self.sira
            onceki = list(self.onceki[-2:])
            self.onceki = (self.onceki + [metin])[-2:]
        govde = (f"GL1 {kod} {sira} {len(onceki)}\n"
                 f"{self._tek(self.ayarlar.get('tur') or 'Oyun')}\n"
                 f"{self._tek(self.ayarlar.get('oyun') or '')}\n"
                 f"{self._tek(metin)}\n" + "".join(self._tek(o) + "\n" for o in onceki))
        r = self._istek(host, govde, 15.0)
        if r is None:
            # Adres değişmiş olabilir: bir kez yeniden ara.
            yeni = self.bul()
            if yeni and yeni != host:
                self.ayarlar["telefon_ip"] = yeni
                r = self._istek(yeni, govde, 15.0)
        self.son_sure = time.time() - baslangic
        if r is None:
            self.son_hata = "Telefona ulaşılamadı"
            return None
        if r.startswith("OK "):
            self.son_hata = None
            return r[3:].strip() or None
        if r.startswith("SKIP"):
            self.atlandi = True
            self.son_hata = None
            return None
        self.son_hata = "Telefon: " + (r[3:].strip() if r.startswith("ERR") else "anlaşılmayan cevap")
        return None


# ------------------------------------------------- bilgisayarın kendi çevirisi

MODEL_DIR = os.path.join(APP_DIR, "model_en_tr")
ARGOS_INDEKSLER = [
    "https://raw.githubusercontent.com/argosopentech/argospm-index/main/index.json",
    "https://github.com/argosopentech/argospm-index/raw/main/index.json",
]
ARGOS_INDEKS = ARGOS_INDEKSLER[0]   # testler bunu değiştirir
# Python'un kendi adıyla ("Python-urllib") gelen istekleri bazı sunucular 403 ile reddediyor.
INDIRME_BASLIKLARI = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                  "Chrome/130.0 Safari/537.36 GameLingo",
    "Accept": "*/*",
}
YEREL_IS_PARCACIGI = max(1, min(2, (os.cpu_count() or 4) // 4))   # oyunu kasmasın diye en çok 2


def model_klasorunu_bul(kok):
    """Açılmış model paketinde CTranslate2 klasörünü (model.bin) ve sentencepiece.model'i bulur."""
    model, sp = None, None
    for yol, _, dosyalar in os.walk(kok):
        if "model.bin" in dosyalar and model is None:
            model = yol
        if "sentencepiece.model" in dosyalar and sp is None:
            sp = os.path.join(yol, "sentencepiece.model")
    return (model, sp) if model and sp else (None, None)


def argos_baglantilari(indeks):
    """Argos paket listesinden İngilizce→Türkçe paketin bütün indirme adresleri: en yeni sürümün
    adresleri önce (yedek sunucular dahil), sonra eski sürümlerinkiler."""
    adaylar = [p for p in indeks
               if p.get("from_code") == "en" and p.get("to_code") == "tr" and p.get("links")]

    def surum(p):
        try:
            return tuple(int(x) for x in str(p.get("package_version", "0")).split("."))
        except ValueError:
            return (0,)

    adresler = []
    for p in sorted(adaylar, key=surum, reverse=True):
        for a in p["links"]:
            if a not in adresler:
                adresler.append(a)
    return adresler


def argos_baglantisi(indeks):
    adresler = argos_baglantilari(indeks)
    return adresler[0] if adresler else None


def _ac_adres(adres, sure):
    import urllib.request
    return urllib.request.urlopen(urllib.request.Request(adres, headers=INDIRME_BASLIKLARI), timeout=sure)


def _sunucu(adres):
    m = re.match(r"https?://([^/]+)", adres or "")
    return m.group(1) if m else adres


ARGO_KURALLARI = [
    # (desen, yerine) — büyük/küçük harf duyarsız; çeviri modeli argoyu bilmediği için düz İngilizceye
    (r"^\s*(?:like|err+|um+|uh+),\s*", ""),
    (r"\by'?all\b|\bya'll\b", "you guys"),
    (r"\btryna\b", "trying to"),
    (r"\bwanna\b", "want to"),
    (r"\bgonna\b", "going to"),
    (r"\bgotta\b", "have to"),
    (r"\bgimme\b", "give me"),
    (r"\blemme\b", "let me"),
    (r"\bdunno\b", "don't know"),
    (r"\bkinda\b", "kind of"),
    (r"\bain't\b", "is not"),
    (r"\bcuz\b|\b'cause\b", "because"),
    (r"\b'em\b", "them"),
    (r"\bnah\b", "no"),
    (r"\b(?:bruh|bro|homie|dawg)\b", "brother"),
    (r"\bwhat the (?:fuck|hell)\b", "what the hell"),
    (r"\bwhere the (?:fuck|hell)\b", "where the hell"),
    (r"\bfuck(?:ing)? around\b", "mess around"),
    (r"\bgive a fuck\b", "care"),
    (r"\bthat shit\b", "that crap"),
    (r"\bbullshit\b", "nonsense"),
    (r"\bfucking\b", "damn"),
    (r"\bbe cool\b", "take it easy"),
    (r"\bhold up\b", "wait"),
    (r"\balright\b", "all right"),
    (r"^\s*yo\b[,.!]?\s*", "Hey, "),
]
_ARGO = [(re.compile(d, re.IGNORECASE), y) for d, y in ARGO_KURALLARI]


def argo_sadele(metin):
    """Bilgisayarın çeviri modeli için: "Y'all tryna smoke?" -> "you guys trying to smoke?"."""
    for desen, yerine in _ARGO:
        metin = desen.sub(yerine, metin)
    metin = re.sub(r"\s+", " ", metin).strip(" ,")
    return metin[:1].upper() + metin[1:] if metin else metin


def uydurma_var_mi(kaynak, ceviri):
    """Çeviride kaynakta olmayan uzun sayı varsa model uydurmuştur ("+380638588488")."""
    kaynak_sayilar = set(re.findall(r"\d+", kaynak))
    return any(len(s_) >= 3 and s_ not in kaynak_sayilar for s_ in re.findall(r"\d+", ceviri))


class YerelCeviri:
    """Çeviriyi bilgisayarın kendisi yapar: Argos Translate'in İngilizce→Türkçe modeli,
    CTranslate2 ile (internetsiz, işlemcide). Model ilk açılışta bir kez indirilir (~100 MB),
    ~/.lingoplay/model_en_tr içine açılır. İnternet yoksa: .argosmodel dosyası ~/.lingoplay içine
    elle konursa o kullanılır. Arayüz TelefonKopru ile aynı: cevir, son_hata, son_sure, atlandi."""

    AD = "bilgisayar çevirisi"
    TASLAK_VAR = False   # ayrı taslak yok: çeviri zaten hızlı
    KALICI = False       # hafızaya yazılmaz (hafıza telefonun daha iyi çevirileri için)

    def __init__(self, ayarlar):
        self.ayarlar = ayarlar
        self.son_hata = "Çeviri modeli hazırlanıyor..."
        self.son_sure = 0.0
        self.atlandi = False
        self.hazir = False
        self.cevirici = None
        self.sp = None
        self.onbellek = collections.OrderedDict()
        self.kilit = threading.Lock()
        self.hazirlik_kilidi = threading.Lock()

    # --- hazırlık -----------------------------------------------------------
    def _elle_konan_paket(self):
        try:
            for ad in sorted(os.listdir(APP_DIR)):
                if ad.lower().endswith(".argosmodel"):
                    return os.path.join(APP_DIR, ad)
        except OSError:
            pass
        return None

    def _ac(self, paket):
        import shutil
        import zipfile
        gecici = MODEL_DIR + ".acilyor"
        shutil.rmtree(gecici, ignore_errors=True)
        with zipfile.ZipFile(paket) as z:
            z.extractall(gecici)
        if model_klasorunu_bul(gecici)[0] is None:
            # GitHub'dan inen "Dil-Paketi.zip": içinde .argosmodel var, onu aç.
            ic = [os.path.join(y, a) for y, _, ds in os.walk(gecici) for a in ds if a.lower().endswith(".argosmodel")]
            if ic:
                ic_paket = ic[0] + ".ic"
                os.replace(ic[0], ic_paket)
                with zipfile.ZipFile(ic_paket) as z:
                    z.extractall(os.path.join(gecici, "_ic"))
                os.remove(ic_paket)
        if model_klasorunu_bul(gecici)[0] is None:
            shutil.rmtree(gecici, ignore_errors=True)
            raise RuntimeError("indirilen pakette çeviri modeli yok")
        shutil.rmtree(MODEL_DIR, ignore_errors=True)
        os.replace(gecici, MODEL_DIR)

    def _indir(self, durum_yaz):
        durum_yaz("Çeviri modeli aranıyor (yalnızca ilk sefer)...")
        hatalar = []
        adresler = []
        for indeks in [ARGOS_INDEKS] + [a for a in ARGOS_INDEKSLER if a != ARGOS_INDEKS]:
            try:
                with _ac_adres(indeks, 20) as c:
                    adresler = argos_baglantilari(json.loads(c.read().decode("utf-8")))
                break
            except Exception as e:
                hatalar.append(f"liste {_sunucu(indeks)}: {type(e).__name__}: {e}")
                KAYIT.yaz(f"Paket listesi alınamadı ({indeks}): {type(e).__name__}: {e}")
        if not adresler:
            raise RuntimeError("; ".join(hatalar) or "paket listesinde İngilizce→Türkçe modeli yok")
        for adres in adresler:
            try:
                self._dosyayi_indir(adres, durum_yaz)
                return
            except Exception as e:
                hatalar.append(f"{_sunucu(adres)}: {type(e).__name__}: {e}")
                KAYIT.yaz(f"İndirilemedi ({adres}): {type(e).__name__}: {e}")
        raise RuntimeError(" · ".join(hatalar[-3:]))

    def _dosyayi_indir(self, adres, durum_yaz):
        KAYIT.yaz(f"Çeviri modeli indiriliyor: {adres}")
        hedef = os.path.join(APP_DIR, "en_tr.indiriliyor")
        with _ac_adres(adres, 30) as c, open(hedef, "wb") as f:
            toplam = int(c.headers.get("Content-Length") or 0)
            alinan, son = 0, 0.0
            while True:
                parca = c.read(1 << 16)
                if not parca:
                    break
                f.write(parca)
                alinan += len(parca)
                if time.time() - son > 0.5:
                    son = time.time()
                    yuzde = f" %{alinan * 100 // toplam}" if toplam else ""
                    durum_yaz(f"Çeviri modeli indiriliyor{yuzde} ({alinan // (1 << 20)} MB) — yalnızca ilk sefer")
        try:
            self._ac(hedef)
        finally:
            try:
                os.remove(hedef)
            except OSError:
                pass

    def hazirla(self, durum_yaz=lambda m: None):
        """Modeli bulur, yoksa indirir, belleğe yükler. Arka planda bir kez çağrılır."""
        with self.hazirlik_kilidi:
            if self.hazir:
                return True
            bas = time.time()
            try:
                import ctranslate2
                import sentencepiece
            except Exception as e:
                self.son_hata = f"Çeviri motoru yüklenemedi ({type(e).__name__}: {e}). Telefon seçeneğini kullanın."
                KAYIT.yaz(self.son_hata, hata=True)
                durum_yaz(self.son_hata)
                return False
            try:
                os.makedirs(APP_DIR, exist_ok=True)
                model, sp = model_klasorunu_bul(MODEL_DIR) if os.path.isdir(MODEL_DIR) else (None, None)
                if model is None:
                    elle = self._elle_konan_paket()
                    if elle:
                        durum_yaz("Çeviri modeli açılıyor...")
                        self._ac(elle)
                    else:
                        self._indir(durum_yaz)
                    model, sp = model_klasorunu_bul(MODEL_DIR)
                durum_yaz("Çeviri modeli yükleniyor...")
                self.sp = sentencepiece.SentencePieceProcessor(model_file=sp)
                self.cevirici = ctranslate2.Translator(model, device="cpu", compute_type="int8",
                                                       inter_threads=1, intra_threads=YEREL_IS_PARCACIGI)
                self.hazir = True
                self.son_hata = None
                KAYIT.yaz(f"Bilgisayar çevirisi hazır ({time.time() - bas:.1f} sn · {YEREL_IS_PARCACIGI} iş parçacığı)")
                durum_yaz("Dil paketi hazır ✓ Bilgisayar kendisi çeviriyor.")
                return True
            except Exception as e:
                self.son_hata = (f"Çeviri modeli hazırlanamadı: {e}. "
                                 "\"Dosyadan ekle\" ile paketi elle kurabilir ya da telefon seçeneğini kullanabilirsiniz.")
                KAYIT.yaz(self.son_hata, hata=True)
                durum_yaz(self.son_hata)
                return False

    # --- dil paketi yönetimi (arayüzdeki "Dil paketi" bölümü) ---------------
    @staticmethod
    def paket_durumu():
        """(yüklü mü, MB) — ~/.lingoplay/model_en_tr içinde açılmış model var mı."""
        if not os.path.isdir(MODEL_DIR) or model_klasorunu_bul(MODEL_DIR)[0] is None:
            return False, 0
        boyut = 0
        for yol, _, dosyalar in os.walk(MODEL_DIR):
            for ad in dosyalar:
                try:
                    boyut += os.path.getsize(os.path.join(yol, ad))
                except OSError:
                    pass
        return True, boyut // (1 << 20)

    def _sifirla(self):
        with self.kilit:
            self.hazir = False
            self.cevirici = None
            self.sp = None
            self.onbellek.clear()

    def paketi_indir(self, durum_yaz=lambda m: None):
        """Dil paketini baştan indirir ve yükler (düğmeden)."""
        import shutil
        self._sifirla()
        shutil.rmtree(MODEL_DIR, ignore_errors=True)
        return self.hazirla(durum_yaz)

    def dosyadan_ekle(self, yol, durum_yaz=lambda m: None):
        """Elle seçilen .argosmodel dosyasını açar ve yükler (internet yoksa)."""
        try:
            durum_yaz("Dil paketi açılıyor...")
            self._ac(yol)
        except Exception as e:
            self.son_hata = f"Dil paketi açılamadı: {type(e).__name__}: {e}"
            KAYIT.yaz(self.son_hata, hata=True)
            durum_yaz(self.son_hata)
            return False
        self._sifirla()
        return self.hazirla(durum_yaz)

    # --- çeviri -------------------------------------------------------------
    @staticmethod
    def _temizle(metin):
        metin = (metin or "").replace("▁", " ").replace("<unk>", "")
        return re.sub(r"\s+", " ", metin).strip()

    def cevir(self, metin):
        self.son_sure = 0.0
        self.atlandi = False
        if not self.hazir:
            return None
        anahtar = sadelestir(metin)
        if anahtar in self.onbellek:
            self.onbellek.move_to_end(anahtar)
            return self.onbellek[anahtar]
        if len(metin) > 200:
            self.son_hata = "Satır çok uzun (altyazı değil gibi), çevrilmedi"
            return None
        bas = time.time()
        try:
            sade = argo_sadele(metin)
            with self.kilit:
                parcalar = self.sp.encode(sade, out_type=str)
                sonuc = self.cevirici.translate_batch([parcalar], beam_size=4, max_decoding_length=160,
                                                      replace_unknowns=True)
                ceviri = self._temizle(self.sp.decode(sonuc[0].hypotheses[0]))
        except Exception as e:
            self.son_hata = f"Bilgisayar çevirisi: {type(e).__name__}: {e}"
            return None
        self.son_sure = time.time() - bas
        if not ceviri:
            self.son_hata = "Bilgisayar çevirisi boş döndü"
            return None
        if uydurma_var_mi(metin, ceviri):
            self.son_hata = f"Çeviri anlamsız çıktı, gösterilmedi: {ceviri}"
            return None
        self.son_hata = None
        self.onbellek[anahtar] = ceviri
        if len(self.onbellek) > 500:
            self.onbellek.popitem(last=False)
        return ceviri


# ------------------------------------------------- Chrome çevirisi (eklenti üzerinden)

CHROME_PORT = 47330
CHROME_BEKLEME = 3.0   # bir satırın çevirisi için en çok bekleme (sn)


class ChromeKopru:
    """Çeviriyi Chrome'daki GameLingo eklentisi yapar (Chrome'un yerleşik Google çeviri modeli).
    Program 127.0.0.1:47330'da küçük bir yerel sunucu açar; eklentinin arka plan sayfası buraya
    sürekli "iş var mı?" diye sorar (uzun bekleyen istek), satırı alıp çevirir ve sonucu geri yollar.
    Yalnızca bu bilgisayardan erişilir. Sıradan web sayfaları kullanamaz: özel başlık
    (X-GameLingo) ister, tarayıcı bunu sayfalara izin vermeden göndermez.
    Arayüz TelefonKopru/YerelCeviri ile aynı: cevir, hazir, son_hata, son_sure, atlandi."""

    AD = "Chrome çevirisi"
    TASLAK_VAR = False
    KALICI = False

    def __init__(self, ayarlar, port=None):
        self.ayarlar = ayarlar
        self.port = port or CHROME_PORT
        self.son_hata = None
        self.son_sure = 0.0
        self.atlandi = False
        self.kosul = threading.Condition()
        self.is_ = None            # (kimlik, metin) — yalnızca en yenisi bekler
        self.sonuclar = {}         # kimlik -> (ceviri, hata)
        self.sayac = itertools.count(1)
        self.son_temas = 0.0       # eklentinin en son sorduğu an
        self.bekleyen_istek = 0
        self.sunucu = None
        self.kapandi = False
        self.onbellek = collections.OrderedDict()

    # --- bağlantı -------------------------------------------------------------
    @property
    def hazir(self):
        with self.kosul:
            return self.bekleyen_istek > 0 or time.time() - self.son_temas < 4.0

    def baslat(self):
        if self.sunucu:
            return True
        import http.server
        kopru = self

        class Isleyici(http.server.BaseHTTPRequestHandler):
            # Her istek ayrı bağlantı: program kapanıp yeniden açılınca eklenti eski bağlantıda takılı kalmasın
            protocol_version = "HTTP/1.0"

            def log_message(self, *a):
                pass

            def _yanit(self, kod, govde=b""):
                self.send_response(kod)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(govde)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                if govde:
                    self.wfile.write(govde)

            def _izinli(self):
                return self.headers.get("X-GameLingo") == "1"

            def do_GET(self):
                if not self._izinli():
                    return self._yanit(403)
                if self.path.startswith("/durum"):
                    return self._yanit(200, json.dumps({"surum": SURUM}).encode())
                if not self.path.startswith("/is"):
                    return self._yanit(404)
                is_ = kopru._is_bekle(20.0)
                if is_ is None:
                    return self._yanit(204)
                self._yanit(200, json.dumps({"id": is_[0], "metin": is_[1]}, ensure_ascii=False).encode("utf-8"))

            def do_POST(self):
                if not self._izinli() or not self.path.startswith("/sonuc"):
                    return self._yanit(403)
                try:
                    uzunluk = min(int(self.headers.get("Content-Length") or 0), 65536)
                    veri = json.loads(self.rfile.read(uzunluk).decode("utf-8"))
                    kopru._sonuc_geldi(int(veri["id"]), veri.get("ceviri"), veri.get("hata"))
                except Exception:
                    return self._yanit(400)
                self._yanit(204)

            def do_OPTIONS(self):
                # Web sayfalarının ön kontrolüne izin verilmez (CORS başlığı yok).
                self._yanit(403)

        try:
            self.sunucu = http.server.ThreadingHTTPServer(("127.0.0.1", self.port), Isleyici)
        except OSError as e:
            self.son_hata = f"Chrome bağlantı noktası ({self.port}) açılamadı: {e}. Program iki kez mi açık?"
            KAYIT.yaz(self.son_hata, hata=True)
            return False
        self.kapandi = False
        self.sunucu.daemon_threads = True
        threading.Thread(target=self.sunucu.serve_forever, daemon=True).start()
        KAYIT.yaz(f"Chrome çevirisi için yerel bağlantı açıldı (127.0.0.1:{self.port})")
        return True

    def durdur(self):
        with self.kosul:
            self.kapandi = True          # bekleyen "iş var mı?" isteklerini hemen bırak
            self.kosul.notify_all()
        if self.sunucu:
            self.sunucu.shutdown()
            self.sunucu.server_close()
            self.sunucu = None

    def hazirla(self, durum_yaz=lambda m: None):
        if not self.baslat():
            durum_yaz(self.son_hata)
            return False
        for _ in range(30):            # eklenti bağlanmışsa birkaç saniye içinde sorar
            if self.hazir:
                break
            time.sleep(0.1)
        if self.hazir:
            durum_yaz("Chrome çevirisi bağlı ✓ Oyunu açabilirsiniz.")
            KAYIT.yaz("Chrome eklentisi bağlı")
        else:
            self.son_hata = "Chrome eklentisi bağlanmadı. Chrome açık mı, GameLingo eklentisi güncel mi (1.1)?"
            durum_yaz(self.son_hata)
            KAYIT.yaz(self.son_hata)
        return True

    # --- sunucu tarafı --------------------------------------------------------
    def _is_bekle(self, sure):
        bitis = time.time() + sure
        with self.kosul:
            self.bekleyen_istek += 1
            self.son_temas = time.time()
            try:
                while self.is_ is None:
                    if self.kapandi:
                        return None
                    kalan = bitis - time.time()
                    if kalan <= 0:
                        return None
                    self.kosul.wait(kalan)
                is_, self.is_ = self.is_, None
                return is_
            finally:
                self.bekleyen_istek -= 1
                self.son_temas = time.time()

    def _sonuc_geldi(self, kimlik, ceviri, hata):
        with self.kosul:
            self.son_temas = time.time()
            self.sonuclar[kimlik] = (ceviri, hata)
            while len(self.sonuclar) > 50:
                self.sonuclar.pop(next(iter(self.sonuclar)))
            self.kosul.notify_all()

    # --- çeviri ---------------------------------------------------------------
    def cevir(self, metin):
        self.son_sure = 0.0
        self.atlandi = False
        if not self.sunucu and not self.baslat():
            return None
        anahtar = sadelestir(metin)
        if anahtar in self.onbellek:
            self.onbellek.move_to_end(anahtar)
            return self.onbellek[anahtar]
        if not self.hazir:
            self.son_hata = "Chrome eklentisi bağlı değil (Chrome açık mı?)"
            return None
        bas = time.time()
        with self.kosul:
            kimlik = next(self.sayac)
            self.is_ = (kimlik, metin)      # bekleyen eski iş varsa onun yerine geçer
            self.kosul.notify_all()
            while kimlik not in self.sonuclar:
                kalan = bas + CHROME_BEKLEME - time.time()
                if kalan <= 0:
                    if self.is_ and self.is_[0] == kimlik:
                        self.is_ = None
                    self.son_hata = "Chrome çeviriyi zamanında döndürmedi"
                    return None
                self.kosul.wait(kalan)
            ceviri, hata = self.sonuclar.pop(kimlik)
        self.son_sure = time.time() - bas
        if hata or not ceviri:
            self.son_hata = f"Chrome çevirisi: {hata or 'boş döndü'}"
            return None
        ceviri = ceviri.strip()
        self.son_hata = None
        self.onbellek[anahtar] = ceviri
        if len(self.onbellek) > 500:
            self.onbellek.popitem(last=False)
        return ceviri


_YEREL = {"nesne": None}
_CHROME = {"nesne": None}


def cevirmen_olustur(ayarlar):
    """Seçili kaynağa göre çevirmen. Bilgisayarın modeli bir kez yüklenir; Durdur/Başlat'ta yeniden yüklenmez."""
    kaynak = str(ayarlar.get("ceviri_kaynagi", "bilgisayar"))
    if kaynak == "telefon":
        return TelefonKopru(ayarlar)
    if kaynak == "chrome":
        if _CHROME["nesne"] is None:
            _CHROME["nesne"] = ChromeKopru(ayarlar)
        _CHROME["nesne"].ayarlar = ayarlar
        return _CHROME["nesne"]
    if _YEREL["nesne"] is None:
        _YEREL["nesne"] = YerelCeviri(ayarlar)
    _YEREL["nesne"].ayarlar = ayarlar
    return _YEREL["nesne"]


# ------------------------------------------------------------ çalışma kaydı

class Kayit:
    """Çalışma kaydı: ne okundu, ne yazıldı, ne kadar sürdü, hangi hata çıktı.
    Son 400 satır bellekte (pencerede gösterilir/kopyalanır), hepsi dosyaya da yazılır."""

    def __init__(self):
        self.satirlar = collections.deque(maxlen=400)
        self.son_hata = ""
        self.kilit = threading.Lock()

    def dosya(self):
        return os.path.join(APP_DIR, "calisma_kaydi.txt")

    def yaz(self, metin, hata=False):
        satir = time.strftime("%H:%M:%S") + "  " + ("HATA: " if hata else "") + str(metin).replace("\n", " ")
        with self.kilit:
            self.satirlar.append(satir)
            if hata:
                self.son_hata = satir
        try:
            os.makedirs(APP_DIR, exist_ok=True)
            yol = self.dosya()
            if os.path.exists(yol) and os.path.getsize(yol) > 1_000_000:
                os.replace(yol, yol + ".eski")
            with open(yol, "a", encoding="utf-8") as f:
                f.write(time.strftime("%Y-%m-%d ") + satir + "\n")
        except Exception:
            pass

    def metin(self):
        with self.kilit:
            return "\n".join(self.satirlar)

    def temizle(self):
        with self.kilit:
            self.satirlar.clear()
            self.son_hata = ""


KAYIT = Kayit()


def sistem_bilgisi():
    """İşlemci çekirdeği ve boş bellek (Windows'ta); kayda yazılır."""
    parcalar = [f"{os.cpu_count() or '?'} çekirdek"]
    if sys.platform == "win32":
        try:
            import ctypes

            class MEMORYSTATUSEX(ctypes.Structure):
                _fields_ = [("dwLength", ctypes.c_ulong), ("dwMemoryLoad", ctypes.c_ulong),
                            ("ullTotalPhys", ctypes.c_ulonglong), ("ullAvailPhys", ctypes.c_ulonglong),
                            ("ullTotalPageFile", ctypes.c_ulonglong), ("ullAvailPageFile", ctypes.c_ulonglong),
                            ("ullTotalVirtual", ctypes.c_ulonglong), ("ullAvailVirtual", ctypes.c_ulonglong),
                            ("sullAvailExtendedVirtual", ctypes.c_ulonglong)]

            m = MEMORYSTATUSEX()
            m.dwLength = ctypes.sizeof(MEMORYSTATUSEX)
            if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(m)):
                parcalar.append(f"bellek %{m.dwMemoryLoad} dolu ({m.ullAvailPhys // 2**20} MB boş / "
                                f"{m.ullTotalPhys // 2**20} MB)")
        except Exception:
            pass
    return " · ".join(parcalar)


# ------------------------------------------------------------------- görünüm

RENK = {
    "zemin": "#0B1220",      # pencere
    "kart": "#131C2E",       # kartlar
    "cizgi": "#1F2B44",      # kart kenarı
    "alan": "#0B1220",       # yazı kutuları
    "alan_cizgi": "#2A3A58",
    "yazi": "#E6EDF7",
    "soluk": "#8FA3BF",
    "turkuaz": "#2EE6D6",
    "turkuaz_koyu": "#04342C",
    "mavi": "#2EA8FF",
    "mavi_zemin": "#13324A",
    "kirmizi": "#FF6B6B",
    "gri": "#5B6B85",
    "taslak": "#AEB9C8",
}
YAZI = "Segoe UI"


def durum_rengi(metin):
    """Durum satırına göre nokta rengi: hata kırmızı, çalışıyor turkuaz, diğerleri gri."""
    m = (metin or "").lower()
    if any(k in m for k in ("hata", "başarısız", "✗", "alınamadı", "yüklenemedi", "hazırlanamadı")):
        return RENK["kirmizi"]
    if any(k in m for k in ("çalışıyor", "ocr ", "✓")):
        return RENK["turkuaz"]
    return RENK["gri"]


# ------------------------------------------------------------------- ekran

class Kutu:
    """Ekranın üstünde duran, her zaman önde çeviri kutusu: koyu zemin, turkuaz alt şerit,
    konuşanın adı turkuaz."""

    def __init__(self, kok, yazi_boyutu=20):
        self.pencere = tk.Toplevel(kok)
        self.pencere.overrideredirect(True)
        self.pencere.attributes("-topmost", True)
        self.pencere.attributes("-alpha", 0.93)
        self.pencere.configure(bg=RENK["zemin"])
        govde = tk.Frame(self.pencere, bg=RENK["zemin"])
        govde.pack()
        self.ad = tk.Label(govde, text="", font=(YAZI, yazi_boyutu, "bold"),
                           fg=RENK["turkuaz"], bg=RENK["zemin"], padx=0, pady=8)
        self.etiket = tk.Label(govde, text="", font=(YAZI, yazi_boyutu, "bold"),
                               fg="white", bg=RENK["zemin"], wraplength=1100,
                               justify="center", padx=16, pady=8)
        self.ad.pack(side="left", anchor="n")
        self.etiket.pack(side="left")
        tk.Frame(self.pencere, bg=RENK["turkuaz"], height=3).pack(fill="x")
        self.pencere.withdraw()

    def yaz(self, metin, taslak=False):
        if not metin:
            self.pencere.withdraw()
            return
        # Taslak (hızlı çeviri) açık gri; yapay zekânın doğrusu beyaz.
        self.etiket.config(fg=RENK["taslak"] if taslak else "white")
        ad, govde = konusani_ayir(metin)
        if ad:
            self.ad.config(text="  " + ad + ":")
            self.etiket.config(text=govde, padx=8)
        else:
            self.ad.config(text="")
            self.etiket.config(text=metin, padx=16)
        self.pencere.update_idletasks()
        genislik = self.pencere.winfo_width()
        ekran = self.pencere.winfo_screenwidth()
        self.pencere.geometry(f"+{max(0, (ekran - genislik) // 2)}+40")
        self.pencere.deiconify()
        self.pencere.lift()


def bolge_yuzdeleri(ayarlar):
    """Üst/alt yüzde; bölge çok darsa ortasından en az EN_AZ_BOLGE_YUZDE yüksekliğe genişletir."""
    u = int(ayarlar.get("ust_yuzde", 74)); a = int(ayarlar.get("alt_yuzde", 99))
    if a - u < EN_AZ_BOLGE_YUZDE:
        orta = (u + a) / 2
        u = int(max(0, orta - EN_AZ_BOLGE_YUZDE / 2))
        a = int(min(100, u + EN_AZ_BOLGE_YUZDE))
        u = min(u, a - EN_AZ_BOLGE_YUZDE)
    return u, a


def bolgeyi_goster(kok, ayarlar, sure_ms=2500):
    """Okunan altyazı bölgesini birkaç saniye yarı saydam turkuaz çerçeveyle gösterir."""
    try:
        ekran_no = int(ayarlar.get("ekran", 1))
    except (TypeError, ValueError):
        ekran_no = 1
    ekran = None
    if mss is not None:
        try:
            with mss.mss() as sct:
                ekranlar = sct.monitors
                ekran = ekranlar[ekran_no] if ekran_no < len(ekranlar) else ekranlar[0]
        except Exception:
            ekran = None
    if not ekran:
        ekran = {"left": 0, "top": 0, "width": kok.winfo_screenwidth(), "height": kok.winfo_screenheight()}
    yuk, gen = ekran["height"], ekran["width"]
    u_y, a_y = bolge_yuzdeleri(ayarlar)
    ust = ekran["top"] + int(yuk * u_y / 100)
    alt = ekran["top"] + int(yuk * a_y / 100)
    sol = ekran["left"] + int(gen * ayarlar.get("sol_yuzde", 8) / 100)
    sag = ekran["left"] + int(gen * ayarlar.get("sag_yuzde", 92) / 100)
    p = tk.Toplevel(kok)
    p.overrideredirect(True)
    p.attributes("-topmost", True)
    p.attributes("-alpha", 0.35)
    p.configure(bg=RENK["turkuaz"])
    tk.Label(p, text="GameLingo burayı okuyor", font=(YAZI, 14, "bold"),
             fg=RENK["turkuaz_koyu"], bg=RENK["turkuaz"]).pack(expand=True)
    p.geometry(f"{max(40, sag - sol)}x{max(20, alt - ust)}+{sol}+{ust}")
    p.after(sure_ms, p.destroy)
    return p


# -------------------------------------------------------------------- motor

# Oyunu yavaşlatmasın diye: okuma en fazla bu kadar sık yapılır ve okuma, sürdüğü
# zamanın en az 2 katı kadar dinlenir (işlemcinin en fazla ~%33'ü okumaya gider).
EN_KISA_ARALIK = 0.30
OKUMA_GENISLIGI = 1280   # bundan genişse 2'ye/3'e bölünür; fazla küçültmek "t"yi "l" okutuyordu
# Okuma için çekirdek: en çok 4. (5.8'de 8 çekirdek denendi: okuma hızlanmadı, oyun ve yayın kasıp
# gecikme "ms" yükseldi. Okumanın çoğu tek çekirdekte geçiyor; fazla çekirdek sadece oyunu yoruyor.)
OCR_IS_PARCACIGI = max(2, min(4, (os.cpu_count() or 4) // 4))
# Dinlenme çarpanı: okuma süresinin 1,5 katı (zayıf işlemcide 2 katı).
DINLENME_CARPANI = 0.5 if (os.cpu_count() or 4) >= 8 else 0.8   # okuma sonrası dinlenme (okuma süresinin katı)
# Bölge en az bu kadar yüksek olmalı (%): çok dar bölge harflerin yarısını kesiyor, okuma çöp çıkıyordu.
EN_AZ_BOLGE_YUZDE = 8
OCR_BILGI = "?"


class WindowsOcr:
    """Windows'un kendi yazı okuyucusu (Windows.Media.Ocr; Fotoğraflar ve Ekran Alıntısı Aracı da bunu kullanır).
    İşlemcide çalışan RapidOCR'dan çok daha hızlıdır. Çağrılınca RapidOCR ile aynı biçimde sonuç döndürür:
    [(kutu [(x,y)x4], metin, güven), ...] — böylece satır seçimi ve sonrası aynen çalışır."""

    def __init__(self):
        import asyncio
        from winrt.windows.media.ocr import OcrEngine
        from winrt.windows.globalization import Language
        from winrt.windows.graphics.imaging import SoftwareBitmap, BitmapPixelFormat
        from winrt.windows.storage.streams import DataWriter
        self._SoftwareBitmap, self._BGRA8, self._DataWriter = SoftwareBitmap, BitmapPixelFormat.BGRA8, DataWriter
        motor, dil = None, None
        for etiket in ("en-US", "en-GB", "en"):
            try:
                lang = Language(etiket)
                if OcrEngine.is_language_supported(lang):
                    motor, dil = OcrEngine.try_create_from_language(lang), etiket
                    break
            except Exception:
                continue
        if motor is None:   # İngilizce okuma paketi yoksa: Windows'un dili (Türkçe de Latin harflerini okur)
            motor = OcrEngine.try_create_from_user_profile_languages()
            try:
                dil = motor.recognizer_language.language_tag + " (İngilizce paketi yok)"
            except Exception:
                dil = "Windows dili"
        if motor is None:
            raise RuntimeError("Windows OCR açılamadı")
        self.motor, self.dil = motor, dil
        self.dongu = asyncio.new_event_loop()
        try:
            self.en_buyuk = int(OcrEngine.max_image_dimension)
        except Exception:
            self.en_buyuk = 10000

    def __call__(self, goruntu, **_):
        import numpy as np
        g = goruntu
        if g.ndim == 3 and g.shape[2] == 3:   # BGR -> BGRA
            g = np.dstack([g, np.full(g.shape[:2], 255, dtype=np.uint8)])
        g = np.ascontiguousarray(g, dtype=np.uint8)
        h, w = g.shape[:2]
        if max(h, w) > self.en_buyuk:
            adim = math.ceil(max(h, w) / self.en_buyuk)
            g = np.ascontiguousarray(g[::adim, ::adim]); h, w = g.shape[:2]
        else:
            adim = 1
        yazici = self._DataWriter()
        yazici.write_bytes(g.tobytes())
        bmp = self._SoftwareBitmap.create_copy_from_buffer(yazici.detach_buffer(), self._BGRA8, w, h)
        sonuc = self.dongu.run_until_complete(self.motor.recognize_async(bmp))
        cikti = []
        for satir in sonuc.lines:
            kelimeler = list(satir.words)
            if not kelimeler:
                continue
            xs, ys, xe, ye = [], [], [], []
            for k in kelimeler:
                b = k.bounding_rect
                xs.append(b.x); ys.append(b.y); xe.append(b.x + b.width); ye.append(b.y + b.height)
            x0, y0, x1, y1 = min(xs) * adim, min(ys) * adim, max(xe) * adim, max(ye) * adim
            metin = " ".join(k.text for k in kelimeler)
            cikti.append(([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], metin, 0.9))
        return (cikti or None), None


def windows_ocr_olustur():
    if not sys.platform.startswith("win") and not os.environ.get("GL_WINOCR_DENE"):
        return None
    try:
        return WindowsOcr()
    except Exception as e:
        KAYIT.yaz(f"Windows OCR kullanılamadı, RapidOCR'a geçildi: {type(e).__name__}: {e}")
        return None


def okuyucu_olustur():
    """Önce Windows OCR (hızlı), olmazsa RapidOCR."""
    global OCR_BILGI
    w = windows_ocr_olustur()
    if w is not None:
        OCR_BILGI = f"Windows OCR ({w.dil})"
        return w
    return ocr_olustur()


def ocr_olustur():
    """OCR'ı işlemcinin tamamını değil, yalnızca birkaç çekirdeğini kullanacak şekilde kurar."""
    if not RapidOCR:
        return None
    global OCR_BILGI
    # Varsayılan ayar (det_limit_type="min", 736) ince altyazı şeridinin kısa kenarını 736 piksele
    # BÜYÜTÜYORDU: şerit onlarca kat büyüyüp okuma 1-2,5 sn sürüyordu. "max" ile algılama en çok
    # 960 piksellik küçük kopyada yapılır; harfler ise tam çözünürlüklü görüntüden okunur.
    hizli = {"det_limit_type": "max", "det_limit_side_len": 960, "max_side_len": 4000}
    cekirdek = {"intra_op_num_threads": OCR_IS_PARCACIGI, "inter_op_num_threads": 1}
    for ayar, bilgi in ((dict(hizli, **cekirdek), f"{OCR_IS_PARCACIGI} çekirdek · hızlı algılama"),
                        (cekirdek, f"{OCR_IS_PARCACIGI} çekirdek"), ({}, "varsayılan çekirdek")):
        try:
            o = RapidOCR(**ayar)
            OCR_BILGI = bilgi
            return o
        except Exception:
            continue
    return None


def dusuk_oncelik():
    """Windows'ta programı 'normalin altı' önceliğe alır: oyun her zaman önce çalışır."""
    if sys.platform != "win32":
        return
    try:
        import ctypes
        BELOW_NORMAL_PRIORITY_CLASS = 0x00004000
        k32 = ctypes.windll.kernel32
        k32.SetPriorityClass(k32.GetCurrentProcess(), BELOW_NORMAL_PRIORITY_CLASS)
    except Exception:
        pass


class Motor(threading.Thread):
    def __init__(self, ayarlar, kutu, durum_yaz):
        super().__init__(daemon=True)
        self.ayarlar = ayarlar
        self.kutu = kutu
        self.durum_yaz = durum_yaz
        self.calisiyor = True
        self.hafiza = Hafiza()
        self.cevirmen = cevirmen_olustur(ayarlar)
        self.ocr = okuyucu_olustur()
        self.yon_kapali = True   # altyazı hep düz: yön düzeltme adımını atla
        self.gosterilen = ""
        self.son_okunan = ""
        self.taslak_suresi = 0.0
        self.bos_kare = 0
        self.kilit = threading.Lock()
        self.tani = {"kare": 0, "ayni": 0, "okuma": 0, "bos": 0, "ocr_toplam": 0.0, "son": ""}
        self.tani_zamani = time.time()
        self.bekleyen = None
        self.yz_calisiyor = False
        self.taslaklar = {}
        self.onceki_kare = None
        self.ocr_suresi = 0.0
        self.gosterim_zamani = 0.0

    def dur(self):
        self.calisiyor = False
        KAYIT.yaz("Durduruldu")

    def hata(self, metin):
        """Hatayı hem durum satırına hem çalışma kaydına (son hata olarak) yazar."""
        KAYIT.yaz(metin, hata=True)
        self.durum_yaz(metin)

    def bolge(self, sct):
        ekran_no = int(self.ayarlar.get("ekran", 1))
        ekranlar = sct.monitors
        ekran = ekranlar[ekran_no] if ekran_no < len(ekranlar) else ekranlar[0]
        yuk = ekran["height"]
        gen = ekran["width"]
        u_y, a_y = bolge_yuzdeleri(self.ayarlar)
        ust = ekran["top"] + int(yuk * u_y / 100)
        alt = ekran["top"] + int(yuk * a_y / 100)
        sol = ekran["left"] + int(gen * self.ayarlar["sol_yuzde"] / 100)
        sag = ekran["left"] + int(gen * self.ayarlar["sag_yuzde"] / 100)
        return {"top": ust, "left": sol, "width": max(8, sag - sol), "height": max(8, alt - ust)}

    def oku(self, goruntu):
        if not self.ocr:
            return ""
        try:
            if self.yon_kapali:
                try:
                    sonuc, _ = self.ocr(goruntu, use_cls=False)
                except TypeError:
                    self.yon_kapali = False
                    sonuc, _ = self.ocr(goruntu)
            else:
                sonuc, _ = self.ocr(goruntu)
        except Exception as e:
            if isinstance(self.ocr, WindowsOcr):
                # Windows OCR bu bilgisayarda sorun çıkardı: hızlı okuyucudan vazgeç, RapidOCR'a geç.
                KAYIT.yaz(f"Windows OCR hatası, RapidOCR'a geçiliyor: {type(e).__name__}: {e}", hata=True)
                self.ocr = ocr_olustur()
                return ""
            # Sessiz kalmıyoruz: okuma hiç çalışmıyorsa sebebi görünsün.
            self.hata(f"OCR hatası: {type(e).__name__}: {e}")
            return ""
        if not sonuc:
            return ""
        genislik = goruntu.shape[1] if hasattr(goruntu, "shape") else 0
        kutular = []
        for kutu_, metin, guven in sonuc:
            t = (metin or "").strip()
            if not t or guven < 0.5:
                continue
            if sadelestir(t) in ARAYUZ_KELIMELERI:
                continue
            if len(t) < 3 or sum(c.isalpha() for c in t) < 3:
                continue
            xs = [p[0] for p in kutu_]
            ys = [p[1] for p in kutu_]
            kutular.append((min(ys), max(ys), min(xs), max(xs), t))
        return altyazi_satirlari(kutular, genislik)

    def benzer(self, a, b):
        if not a or not b:
            return a == b
        na, nb = sadelestir(a), sadelestir(b)
        if na == nb:
            return True
        uzun, kisa = (na, nb) if len(na) >= len(nb) else (nb, na)
        if not uzun:
            return False
        # Uzun ve birebir aynı başlangıç: OCR aynı altyazıyı okuyor ama son
        # birkaç kelimeyi her karede başka türlü bozuyor.
        ortak = 0
        for x, y in zip(na, nb):
            if x != y:
                break
            ortak += 1
        if ortak >= 40:
            return True
        # Yarım okuma: kısa olan, uzun olanın tam başlangıcı.
        # "Problem is he only worried about" / "...about himself."
        if ortak == len(kisa) and len(kisa) >= 20:
            return True
        if len(kisa) / len(uzun) < 0.8:
            return False
        # DÜZENLEME MESAFESİ (Levenshtein). Eskiden karakterler KONUM KONUM
        # karşılaştırılıyordu; OCR bir harf ekleyip çıkarınca hizalama kayıyor
        # ve aynı satır "yeni" sayılıp baştan çevriliyordu:
        #   "Now Loading..." / "Novv Loading,."  -> farklı sanılıyordu
        onceki = list(range(len(uzun) + 1))
        for i, ca in enumerate(kisa, 1):
            simdiki = [i] + [0] * len(uzun)
            for j, cb in enumerate(uzun, 1):
                simdiki[j] = min(onceki[j] + 1, simdiki[j - 1] + 1,
                                 onceki[j - 1] + (ca != cb))
            onceki = simdiki
        mesafe = onceki[-1]
        # Kısa satırlarda oran acımasız: "nowloading" / "novvloading" arasında
        # iki harf fark var ama oran %17 çıkıyor. 8 harften uzun satırlarda
        # iki harfe kadar farka izin veriyoruz.
        if len(uzun) >= 8 and mesafe <= max(2, int(0.12 * len(uzun))):
            return True
        return mesafe / len(uzun) <= 0.12

    def run(self):
        if mss is None or self.ocr is None:
            self.hata("Gerekli bileşenler yüklenemedi.")
            return

        import numpy as np

        u_y, a_y = bolge_yuzdeleri(self.ayarlar)
        KAYIT.yaz(f"SÜRÜM {SURUM} · {sistem_bilgisi()} · okuma {OCR_BILGI} · tarama {self.ayarlar.get('aralik_ms')} ms · "
                  f"bölge %{u_y}–%{a_y}" + (" (dar olduğu için genişletildi)" if (u_y, a_y) != (self.ayarlar.get('ust_yuzde'), self.ayarlar.get('alt_yuzde')) else "") + " · "
                  f"ekran {self.ayarlar.get('ekran')} · çeviri: "
                  + (f"telefon {self.ayarlar.get('telefon_ip') or 'aranacak'}" if isinstance(self.cevirmen, TelefonKopru)
                     else "Chrome eklentisi" if isinstance(self.cevirmen, ChromeKopru) else "bilgisayarın kendisi"))
        if hasattr(self.cevirmen, "hazirla"):
            threading.Thread(target=self.cevirmen.hazirla, args=(self.durum_yaz,), daemon=True).start()
        with mss.mss() as sct:
            while self.calisiyor:
                aralik = max(EN_KISA_ARALIK, self.ayarlar["aralik_ms"] / 1000)
                # Tarama aralığına okuma süresi de dahil: okuma 450 ms sürdüyse ayrıca 350 ms beklenmez.
                time.sleep(max(aralik - self.ocr_suresi, self.ocr_suresi * DINLENME_CARPANI, 0.05))
                try:
                    self.tani_yaz()
                    kare = sct.grab(self.bolge(sct))
                    self.tani["kare"] += 1
                    dizi = np.array(kare)[:, :, :3]

                    # Görüntü küçültülürse OCR belirgin biçimde hızlanır.
                    if dizi.shape[1] > OKUMA_GENISLIGI:
                        adim = max(2, math.ceil(dizi.shape[1] / OKUMA_GENISLIGI))
                        kucuk = np.ascontiguousarray(dizi[::adim, ::adim])
                    else:
                        kucuk = np.ascontiguousarray(dizi)

                    # Kare neredeyse aynıysa OCR'a hiç girmiyoruz.
                    imza = kucuk[::4, ::4].astype(np.int16)
                    if self.onceki_kare is not None and imza.shape == self.onceki_kare.shape:
                        if np.abs(imza - self.onceki_kare).max() < 6:
                            self.tani["ayni"] += 1
                            continue
                    self.onceki_kare = imza

                    # Ön eleme: bölgede altyazı rengi (beyaz/sarı) hiç yoksa OCR'a girme; işlemci boşa yorulmasın.
                    if not altyazi_rengi_var_mi(kucuk):
                        self.tani["eleme"] = self.tani.get("eleme", 0) + 1
                        ham = ""
                        self.ocr_suresi = 0.0
                        ocr_basla = None
                    else:
                        ocr_basla = time.time()
                        ham = self.oku(np.ascontiguousarray(dizi))   # tam çözünürlük: harfler ve boşluklar net
                        self.ocr_suresi = time.time() - ocr_basla
                    if ocr_basla is None:
                        pass
                    else:
                        self.tani["okuma"] += 1
                        self.tani["ocr_toplam"] += self.ocr_suresi
                    if ham:
                        self.tani["son"] = ham[:60]
                    else:
                        self.tani["bos"] += 1
                except Exception as e:
                    self.hata(f"Okuma hatası: {type(e).__name__}: {e}")
                    self.onceki_kare = None
                    time.sleep(0.5)
                    continue

                # Ekranda 10 saniyedir aynı çeviri duruyorsa bir aksilik var;
                # takılı kalmasın diye temizliyoruz.
                if self.gosterilen and time.time() - self.gosterim_zamani > 15:
                    self.gosterilen = ""
                    self.kutu.yaz("")

                metin = metni_onar(ham)
                if metin and turkce_yazi_mi(metin):
                    # Zaten Türkçe: çevirmeye kalkarsak bozuyoruz.
                    if time.time() - getattr(self, "son_turkce_kayit", 0) > 3:
                        self.son_turkce_kayit = time.time()
                        KAYIT.yaz(f"ZATEN TÜRKÇE (çevrilmedi): {metin[:60]}")
                    continue
                if metin and bozuk_mu(metin):
                    # Yarım/bozuk okuma: çevirme, temiz okumayı bekle
                    if time.time() - getattr(self, "son_bozuk_kayit", 0) > 3:
                        self.son_bozuk_kayit = time.time()
                        KAYIT.yaz(f"BOZUK OKUMA (beklendi, çevrilmedi): {metin}")
                    continue
                if len(metin) < 3:
                    self.bos_kare += 1
                    if self.bos_kare >= 6 and self.gosterilen:
                        self.gosterilen = ""
                        self.kutu.yaz("")
                    continue
                self.bos_kare = 0

                if self.benzer(metin, self.gosterilen) or self.benzer(metin, self.son_okunan):
                    continue   # zaten ekranda ya da yolda

                self.son_okunan = metin
                self.yeni_satir(metin)

    # --- çeviri akışı -------------------------------------------------------
    # Yeni satır gelince: kalıp/hafıza varsa anında; yoksa telefondan HIZLI TASLAK hemen (her satıra
    # ayrı, beklemesiz) ve YAPAY ZEKÂ için tek bir sıra: aynı anda tek istek, beklerken yeni satır
    # gelirse eskisi atlanır, hep en yenisi çevrilir (yavaş yapay zekâ ekranı geride bırakmaz).

    def yeni_satir(self, kaynak):
        KAYIT.yaz(f"OKUNAN (OCR {int(self.ocr_suresi * 1000)} ms): {kaynak}")
        if self._hazir_ceviri(kaynak):
            return
        if getattr(self.cevirmen, "TASLAK_VAR", True):
            threading.Thread(target=self._taslak_goster, args=(kaynak,), daemon=True).start()
        with self.kilit:
            self.bekleyen = kaynak
            if self.yz_calisiyor:
                return
            self.yz_calisiyor = True
        threading.Thread(target=self._yz_dongu, daemon=True).start()

    def _yz_dongu(self):
        while self.calisiyor:
            with self.kilit:
                kaynak = self.bekleyen
                self.bekleyen = None
                if kaynak is None:
                    self.yz_calisiyor = False
                    return
            try:
                self._yz(kaynak)
            except Exception as e:
                self.hata(f"Çeviri hatası: {type(e).__name__}: {e}")
        with self.kilit:
            self.yz_calisiyor = False

    def _adli(self, kaynak, metin):
        ad, _ = konusani_ayir(kaynak)
        return f"{ad}: {metin}" if (metin and ad and self.ayarlar.get("konusan_goster")) else metin

    def _hazir_ceviri(self, kaynak):
        """Kalıp sözlüğü ya da hafızada varsa anında gösterir."""
        ad, govde = konusani_ayir(kaynak)
        kalip = KALIPLAR.get(sadelestir(govde))
        if kalip:
            self.goster(kaynak, f"{ad}: {kalip}" if ad else kalip)
            KAYIT.yaz(f"YAZILAN · kalıp: {kalip}")
            return True
        kayitli = self.hafiza.al(kaynak)
        if kayitli:
            self.goster(kaynak, kayitli)
            KAYIT.yaz(f"YAZILAN · hafıza: {kayitli}")
            return True
        return False

    def _taslak_goster(self, kaynak):
        if not getattr(self.cevirmen, "TASLAK_VAR", True):
            return None
        _, govde = konusani_ayir(kaynak)
        bas = time.time()
        taslak = self.cevirmen.taslak(govde) if hasattr(self.cevirmen, "taslak") else None
        if not taslak:
            KAYIT.yaz(f"TASLAK ALINAMADI ({int((time.time() - bas) * 1000)} ms) · telefon 9.25 mi?")
            return None
        KAYIT.yaz(f"YAZILAN · taslak {int((time.time() - bas) * 1000)} ms: {taslak}")
        self.taslaklar[kaynak] = taslak
        if len(self.taslaklar) > 50:
            self.taslaklar.pop(next(iter(self.taslaklar)), None)
        # Yapay zekâ daha önce bitirdiyse taslakla üstüne yazma.
        if self.guncel_mi(kaynak) and self.hafiza.al(kaynak) is None:
            self.taslak_suresi = time.time() - bas
            self.goster(kaynak, self._adli(kaynak, taslak), taslak=True)
        return taslak

    def _yz(self, kaynak):
        if not getattr(self.cevirmen, "hazir", True):
            # Model hazırlanıyor ya da Chrome eklentisi bağlı değil: hata sayma, bekle.
            neden = ("Chrome eklentisi bağlı değil" if isinstance(self.cevirmen, ChromeKopru)
                     else "çeviri modeli hazır değil")
            KAYIT.yaz(f"BEKLİYOR ({neden}): {kaynak}")
            self.durum_yaz(f"Bekliyor: {neden}" + (" — Chrome'u açın" if isinstance(self.cevirmen, ChromeKopru) else ""))
            return
        _, govde = konusani_ayir(kaynak)
        ad_ = getattr(self.cevirmen, "AD", "yapay zekâ")
        # İsim çeviriye gitmez; çeviriden sonra başa eklenir.
        ceviri = self._adli(kaynak, self.cevirmen.cevir(govde))
        taslak = self.taslaklar.get(kaynak)
        if ceviri:
            kalici = getattr(self.cevirmen, "KALICI", True)
            if kalici:
                self.hafiza.koy(kaynak, ceviri)
            guncel = self.guncel_mi(kaynak)
            KAYIT.yaz(f"YAZILAN · {ad_} {int(self.cevirmen.son_sure * 1000)} ms"
                      + ("" if guncel else (" (geç kaldı, hafızaya alındı)" if kalici else " (geç kaldı)"))
                      + f": {ceviri}")
            if guncel:
                self.goster(kaynak, ceviri)
            self.durum_yaz(
                f"OCR {int(self.ocr_suresi * 1000)} ms · "
                + (f"taslak {int(self.taslak_suresi * 1000)} ms · " if taslak else "")
                + f"{ad_} {int(self.cevirmen.son_sure * 1000)} ms"
            )
        elif taslak:
            # Yapay zekâ cevap veremedi ama taslak ekranda: kalıcı hâle getir.
            if self.guncel_mi(kaynak):
                self.goster(kaynak, self._adli(kaynak, taslak))
            if not getattr(self.cevirmen, "atlandi", False):
                self.hata(f"Yapay zekâ çeviremedi, hızlı çeviri gösteriliyor: {self.cevirmen.son_hata}")
        elif getattr(self.cevirmen, "atlandi", False):
            KAYIT.yaz(f"ATLANDI (daha yeni satır geldi): {kaynak}")
        else:
            self.hata(f"Çeviri alınamadı: {self.cevirmen.son_hata}")

    def _isle(self, kaynak):
        """Tek satırı baştan sona sırayla işler (testler ve tek seferlik kullanım için)."""
        if self._hazir_ceviri(kaynak):
            return
        self._taslak_goster(kaynak)
        self._yz(kaynak)

    def tani_yaz(self, aralik=15):
        """15 sn'de bir özet: kaç kare alındı, kaçı aynıydı, okuma ortalama ne kadar sürdü."""
        if time.time() - self.tani_zamani < aralik:
            return
        t = self.tani
        ort = int(t["ocr_toplam"] / t["okuma"] * 1000) if t["okuma"] else 0
        KAYIT.yaz(f"TANI: kare {t['kare']} · aynı kare (atlandı) {t['ayni']} · ön elemede atlanan {t.get('eleme', 0)} · okuma {t['okuma']} "
                  f"(boş {t['bos']}) · OCR ort. {ort} ms · son okunan: \"{t['son']}\"")
        self.tani = {"kare": 0, "ayni": 0, "okuma": 0, "bos": 0, "ocr_toplam": 0.0, "son": ""}
        self.tani_zamani = time.time()

    def guncel_mi(self, kaynak):
        """Bu satır hâlâ ekrandaki en son okunan satır mı? (Eski çeviri yenisinin üstüne yazılmasın.)"""
        return not self.son_okunan or self.benzer(kaynak, self.son_okunan)

    def goster(self, kaynak, metin, taslak=False):
        self.gosterilen = kaynak
        self.gosterim_zamani = time.time()
        if taslak:
            self.kutu.yaz(metin, taslak=True)
        else:
            self.kutu.yaz(metin)


# ------------------------------------------------------------------ arayüz

def ayarlari_yukle():
    ayarlar = dict(VARSAYILAN)
    try:
        with open(CONFIG_PATH, encoding="utf-8") as f:
            ayarlar.update(json.load(f))
    except Exception:
        pass
    for eski in ("api_key", "model", "kaynak"):   # eski Gemini ayarları
        ayarlar.pop(eski, None)
    # Eski sürümlerin kaydettiği çok kısa aralık (120 ms) oyunu kasıyordu.
    try:
        if int(ayarlar.get("aralik_ms", 350)) < 300:
            ayarlar["aralik_ms"] = 350
    except (TypeError, ValueError):
        ayarlar["aralik_ms"] = 350
    return ayarlar


def ayarlari_kaydet(ayarlar):
    try:
        os.makedirs(APP_DIR, exist_ok=True)
        with open(CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(ayarlar, f, ensure_ascii=False, indent=2)
    except Exception:
        pass


def main():
    dusuk_oncelik()
    ayarlar = ayarlari_yukle()
    R = RENK

    kok = tk.Tk()
    kok.title(f"GameLingo {SURUM}")
    kok.geometry("540x880")
    kok.minsize(500, 680)
    kok.configure(bg=R["zemin"])

    dis = tk.Frame(kok, bg=R["zemin"], padx=14, pady=12)
    dis.pack(fill="both", expand=True)

    def kart(baslik_metni=None):
        cerceve = tk.Frame(dis, bg=R["kart"], highlightbackground=R["cizgi"],
                           highlightthickness=1, padx=14, pady=12)
        cerceve.pack(fill="x", pady=(0, 10))
        if baslik_metni:
            tk.Label(cerceve, text=baslik_metni.upper(), font=(YAZI, 8, "bold"),
                     fg=R["turkuaz"], bg=R["kart"]).pack(anchor="w", pady=(0, 6))
        return cerceve

    def giris(ust, degisken, gizli=False):
        return tk.Entry(ust, textvariable=degisken, font=(YAZI, 10), bg=R["alan"], fg=R["yazi"],
                        insertbackground=R["turkuaz"], relief="flat",
                        highlightthickness=1, highlightbackground=R["alan_cizgi"],
                        highlightcolor=R["turkuaz"], show="•" if gizli else "")

    def dugme(ust, metin, komut, dolu=False):
        if dolu:
            return tk.Button(ust, text=metin, command=komut, font=(YAZI, 11, "bold"),
                             bg=R["turkuaz"], fg=R["turkuaz_koyu"], activebackground="#5FF0E3",
                             activeforeground=R["turkuaz_koyu"], relief="flat", bd=0, cursor="hand2")
        return tk.Button(ust, text=metin, command=komut, font=(YAZI, 9, "bold"),
                         bg=R["kart"], fg=R["turkuaz"], activebackground=R["mavi_zemin"],
                         activeforeground=R["turkuaz"], relief="flat", bd=0, cursor="hand2",
                         highlightthickness=1, highlightbackground=R["turkuaz"])

    alanlar = {}

    def degisken(anahtar):
        d = tk.StringVar(value=str(ayarlar.get(anahtar, "")))
        alanlar[anahtar] = d
        return d

    # --- başlık ---
    baslik = tk.Frame(dis, bg=R["zemin"])
    baslik.pack(fill="x", pady=(0, 10))
    simge = tk.Canvas(baslik, width=38, height=38, bg=R["zemin"], highlightthickness=0)
    simge.pack(side="left")
    simge.create_rectangle(2, 2, 36, 36, outline=R["turkuaz"], width=2)
    simge.create_oval(8, 15, 20, 29, outline=R["turkuaz"], width=2)
    simge.create_oval(18, 15, 30, 29, outline=R["turkuaz"], width=2)
    simge.create_rectangle(13, 15, 25, 22, fill=R["zemin"], outline=R["zemin"])
    simge.create_line(13, 15, 25, 15, fill=R["turkuaz"], width=2)
    yazi_kutusu = tk.Frame(baslik, bg=R["zemin"], padx=10)
    yazi_kutusu.pack(side="left")
    ad_satiri = tk.Frame(yazi_kutusu, bg=R["zemin"])
    ad_satiri.pack(anchor="w")
    tk.Label(ad_satiri, text="Game", font=(YAZI, 17, "bold"), fg=R["yazi"], bg=R["zemin"]).pack(side="left")
    tk.Label(ad_satiri, text="Lingo", font=(YAZI, 17, "bold"), fg=R["turkuaz"], bg=R["zemin"]).pack(side="left")
    tk.Label(yazi_kutusu, text=f"Bilgisayar · sürüm {SURUM}", font=(YAZI, 8),
             fg=R["soluk"], bg=R["zemin"]).pack(anchor="w")
    tk.Label(baslik, text=" PC ", font=(YAZI, 8, "bold"), fg=R["mavi"], bg=R["mavi_zemin"],
             padx=6, pady=2).pack(side="right")

    # --- durum + başlat ---
    k0 = kart()
    durum_satiri = tk.Frame(k0, bg=R["kart"])
    durum_satiri.pack(fill="x", pady=(0, 10))
    nokta = tk.Canvas(durum_satiri, width=12, height=12, bg=R["kart"], highlightthickness=0)
    nokta.pack(side="left", padx=(0, 8))
    nokta_id = nokta.create_oval(1, 1, 11, 11, fill=R["gri"], outline="")
    def telefon_secili():
        return str(ayarlar.get("ceviri_kaynagi", "bilgisayar")) == "telefon"

    durum = tk.StringVar(value="Telefon kodunu yazıp Başlat'a basın."
                         if telefon_secili() and not str(ayarlar.get("telefon_kod", "")).strip()
                         else "Hazır. Başlat'a basın.")
    tk.Label(durum_satiri, textvariable=durum, font=(YAZI, 9), fg=R["yazi"], bg=R["kart"],
             wraplength=430, justify="left").pack(side="left", fill="x")

    def durum_goster(metin):
        durum.set(metin)
        try:
            nokta.itemconfig(nokta_id, fill=durum_rengi(metin))
        except Exception:
            pass
        try:
            son_hata.set(KAYIT.son_hata or "Son hata: yok")
            son_hata_etiketi.config(fg=R["kirmizi"] if KAYIT.son_hata else R["soluk"])
        except NameError:
            pass   # kayıt kartı henüz kurulmadı

    # --- çeviri kaynağı: bilgisayarın kendisi (varsayılan) ya da telefonun yapay zekâsı ---
    kc = kart("Çeviri")
    kaynak_cipleri = tk.Frame(kc, bg=R["kart"])
    kaynak_cipleri.pack(fill="x", pady=(0, 8))
    kaynak_dugmeleri = {}
    kaynak_bilgisi = tk.StringVar(value="")
    tk.Label(kc, textvariable=kaynak_bilgisi, font=(YAZI, 8), fg=R["soluk"], bg=R["kart"],
             wraplength=460, justify="left").pack(anchor="w")
    kt = tk.Frame(kc, bg=R["kart"])   # telefon ayarları: yalnızca telefon seçiliyken görünür
    kp = tk.Frame(kc, bg=R["kart"])   # dil paketi: yalnızca bilgisayar seçiliyken görünür
    BILGI = {
        "bilgisayar": "Çeviriyi bilgisayarın kendisi yapar, internet ve telefon gerekmez. "
                      "Önce aşağıdan dil paketini bir kez indir.",
        "telefon": "Çeviriyi telefondaki GameLingo'nun yapay zekâsı yapar (daha iyi kalite). "
                   "Telefon ve bilgisayar aynı Wi-Fi'da olmalı.",
        "chrome": "Çeviriyi Chrome'daki GameLingo eklentisi yapar (eklentideki kalite, internetsiz). "
                  "Oyun oynarken Chrome arka planda açık olmalı; eklenti sürümü 1.1 veya üstü.",
    }
    kch = tk.Frame(kc, bg=R["kart"])   # Chrome bağlantı durumu: yalnızca Chrome seçiliyken
    chrome_bilgisi = tk.StringVar(value="")
    chrome_etiketi = tk.Label(kch, textvariable=chrome_bilgisi, font=(YAZI, 9, "bold"), fg=R["soluk"], bg=R["kart"])
    chrome_etiketi.pack(anchor="w")

    def chrome_durumunu_yenile():
        if str(ayarlar.get("ceviri_kaynagi")) != "chrome":
            return
        k = cevirmen_olustur(dict(ayarlar, ceviri_kaynagi="chrome"))
        k.baslat()
        if k.hazir:
            chrome_bilgisi.set("Chrome eklentisi bağlı ✓")
            chrome_etiketi.config(fg=R["turkuaz"])
        else:
            chrome_bilgisi.set(k.son_hata if k.sunucu is None else
                               "Chrome eklentisi bekleniyor... (Chrome açık mı? Eklenti 1.1 mi?)")
            chrome_etiketi.config(fg=R["soluk"] if k.sunucu else R["kirmizi"])
        kok.after(1000, chrome_durumunu_yenile)

    def kaynak_sec(k):
        ayarlar["ceviri_kaynagi"] = k
        for ad_, b in kaynak_dugmeleri.items():
            secili = ad_ == k
            b.config(bg=R["turkuaz"] if secili else R["kart"],
                     fg=R["turkuaz_koyu"] if secili else R["soluk"])
        kaynak_bilgisi.set(BILGI[k])
        for cer in (kt, kp, kch):
            cer.pack_forget()
        if k == "telefon":
            kt.pack(fill="x", pady=(8, 0))
        elif k == "chrome":
            kch.pack(fill="x", pady=(8, 0))
            chrome_durumunu_yenile()
        else:
            kp.pack(fill="x", pady=(8, 0))
            paket_bilgisini_yenile()

    for k, metin in (("bilgisayar", "Bilgisayar"), ("chrome", "Chrome çevirisi"), ("telefon", "Telefonun yapay zekâsı")):
        b = tk.Button(kaynak_cipleri, text=metin, font=(YAZI, 9, "bold"), relief="flat", bd=0, padx=10, pady=3,
                      cursor="hand2", highlightthickness=1, highlightbackground=R["alan_cizgi"],
                      command=lambda k=k: (kaynak_sec(k), kaydet()))
        b.pack(side="left", padx=(0, 6))
        kaynak_dugmeleri[k] = b

    # --- dil paketi (bilgisayarın kendi çevirisi için) ---
    tk.Label(kp, text="DİL PAKETİ · İngilizce → Türkçe", font=(YAZI, 8, "bold"), fg=R["soluk"],
             bg=R["kart"]).pack(anchor="w")
    paket_bilgisi = tk.StringVar(value="")
    paket_etiketi = tk.Label(kp, textvariable=paket_bilgisi, font=(YAZI, 9), fg=R["yazi"], bg=R["kart"],
                             wraplength=460, justify="left")
    paket_etiketi.pack(anchor="w", pady=(2, 6))
    psatir = tk.Frame(kp, bg=R["kart"])
    psatir.pack(fill="x")
    paket_isi = {"suruyor": False}

    def yerel():
        return cevirmen_olustur(dict(ayarlar, ceviri_kaynagi="bilgisayar"))

    def paket_bilgisini_yenile(ilerleme=None):
        if ilerleme:
            paket_bilgisi.set(ilerleme)
            paket_etiketi.config(fg=durum_rengi(ilerleme) if durum_rengi(ilerleme) == R["kirmizi"] else R["yazi"])
            return
        var, mb = YerelCeviri.paket_durumu()
        paket_bilgisi.set(f"Yüklü ✓ ({mb} MB). İnternetsiz çalışır." if var
                          else "Yüklü değil. \"Dil paketini indir\"e bas (~100 MB, bir kez).")
        paket_etiketi.config(fg=R["turkuaz"] if var else R["yazi"])
        try:
            indir_dugmesi.config(text="Yeniden indir" if var else "Dil paketini indir")
        except NameError:
            pass

    def paket_isi_baslat(is_):
        if paket_isi["suruyor"]:
            return
        paket_isi["suruyor"] = True
        for b in (indir_dugmesi, dosya_dugmesi):
            b.config(state="disabled")

        def ilerleme(m):
            kok.after(0, lambda: paket_bilgisini_yenile(m))

        def calis():
            tamam = is_(ilerleme)

            def bitti():
                paket_isi["suruyor"] = False
                for b in (indir_dugmesi, dosya_dugmesi):
                    b.config(state="normal")
                if tamam:
                    paket_bilgisini_yenile()
                    durum_goster("Dil paketi hazır ✓ Başlat'a basabilirsin.")
                else:
                    paket_bilgisini_yenile(yerel().son_hata or "Dil paketi hazırlanamadı")
                    durum_goster(yerel().son_hata or "Dil paketi hazırlanamadı")

            kok.after(0, bitti)

        threading.Thread(target=calis, daemon=True).start()

    def paketi_indir():
        paket_isi_baslat(lambda yaz: yerel().paketi_indir(yaz))

    def dosyadan_ekle():
        try:
            from tkinter import filedialog
            yol = filedialog.askopenfilename(title="Dil paketi seç (.argosmodel)",
                                             filetypes=[("Dil paketi", "*.argosmodel *.zip"), ("Tümü", "*.*")])
        except Exception as e:
            durum_goster(f"Dosya seçilemedi: {e}")
            return
        if yol:
            paket_isi_baslat(lambda yaz: yerel().dosyadan_ekle(yol, yaz))

    indir_dugmesi = dugme(psatir, "Dil paketini indir", paketi_indir)
    indir_dugmesi.pack(side="left", fill="x", expand=True, ipady=4, padx=(0, 6))
    dosya_dugmesi = dugme(psatir, "Dosyadan ekle", dosyadan_ekle)
    dosya_dugmesi.pack(side="left", fill="x", expand=True, ipady=4, padx=(6, 0))

    tk.Label(kt, text="Telefonda GameLingo → \"TV ve bilgisayara çeviri sağla\"ya bas, ekrandaki 4 haneli kodu yaz.",
             font=(YAZI, 8), fg=R["soluk"], bg=R["kart"], wraplength=460, justify="left").pack(anchor="w", pady=(0, 6))
    kod_degisken = degisken("telefon_kod")
    ip_degisken = degisken("telefon_ip")
    ksat = tk.Frame(kt, bg=R["kart"])
    ksat.pack(fill="x")
    ksol = tk.Frame(ksat, bg=R["kart"])
    ksol.pack(side="left", fill="x", expand=True, padx=(0, 6))
    ksag = tk.Frame(ksat, bg=R["kart"])
    ksag.pack(side="left", fill="x", expand=True, padx=(6, 0))
    tk.Label(ksol, text="Kod", font=(YAZI, 8), fg=R["soluk"], bg=R["kart"]).pack(anchor="w")
    kod_girisi = giris(ksol, kod_degisken)
    kod_girisi.pack(fill="x", ipady=4)
    tk.Label(ksag, text="Adres (boş kalsın, kendisi bulur)", font=(YAZI, 8), fg=R["soluk"], bg=R["kart"]).pack(anchor="w")
    giris(ksag, ip_degisken).pack(fill="x", ipady=4)
    telefon_durumu = tk.StringVar(value="")
    tk.Label(kt, textvariable=telefon_durumu, font=(YAZI, 8), fg=R["yazi"], bg=R["kart"],
             wraplength=460, justify="left").pack(anchor="w", pady=(6, 0))

    def telefonu_bul():
        kaydet()
        telefon_durumu.set("Telefon aranıyor...")

        def calis():
            metin, hazir = TelefonKopru(ayarlar).baglan()
            KAYIT.yaz(f"Telefonu bul: {metin}", hata=not hazir)

            def arayuze_yaz():
                ip_degisken.set(ayarlar.get("telefon_ip", ""))
                kaydet()
                telefon_durumu.set(metin)
                durum_goster(metin)

            kok.after(0, arayuze_yaz)

        threading.Thread(target=calis, daemon=True).start()

    dugme(kt, "Telefonu bul ve bağlan", telefonu_bul).pack(fill="x", ipady=4, pady=(8, 0))
    kaynak_sec(str(ayarlar.get("ceviri_kaynagi", "bilgisayar"))
               if str(ayarlar.get("ceviri_kaynagi")) in ("telefon", "chrome") else "bilgisayar")

    # --- içerik ---
    k1 = kart("İçerik")
    tur_degisken = tk.StringVar(value=ayarlar.get("tur", "Oyun"))
    alanlar["tur"] = tur_degisken
    cipler = tk.Frame(k1, bg=R["kart"])
    cipler.pack(fill="x", pady=(0, 8))
    cip_dugmeleri = {}

    def tur_sec(t):
        tur_degisken.set(t)
        for ad_, b in cip_dugmeleri.items():
            secili = ad_ == t
            b.config(bg=R["turkuaz"] if secili else R["kart"],
                     fg=R["turkuaz_koyu"] if secili else R["soluk"])

    for t in ("Oyun", "Dizi", "Film", "Belgesel", "Video"):
        b = tk.Button(cipler, text=t, font=(YAZI, 9, "bold"), relief="flat", bd=0, padx=10, pady=3,
                      cursor="hand2", highlightthickness=1, highlightbackground=R["alan_cizgi"],
                      command=lambda t=t: tur_sec(t))
        b.pack(side="left", padx=(0, 6))
        cip_dugmeleri[t] = b
    tur_sec(tur_degisken.get() if tur_degisken.get() in cip_dugmeleri else "Oyun")
    tk.Label(k1, text="Adı (çeviri bağlamı için, örn. The Last of Us)", font=(YAZI, 8),
             fg=R["soluk"], bg=R["kart"]).pack(anchor="w")
    giris(k1, degisken("oyun")).pack(fill="x", ipady=4)

    # --- altyazı bölgesi ---
    k2 = kart("Altyazı bölgesi")
    surgu_degerleri = {}

    def surgu(ust, metin, anahtar):
        satir = tk.Frame(ust, bg=R["kart"])
        satir.pack(fill="x")
        tk.Label(satir, text=metin, font=(YAZI, 9), fg=R["soluk"], bg=R["kart"]).pack(side="left")
        deger = tk.StringVar(value=f"%{ayarlar.get(anahtar, 0)}")
        tk.Label(satir, textvariable=deger, font=(YAZI, 9, "bold"), fg=R["yazi"], bg=R["kart"]).pack(side="right")
        sayi = tk.IntVar(value=int(ayarlar.get(anahtar, 0)))

        def degisti(_=None):
            deger.set(f"%{sayi.get()}")
            ayarlar[anahtar] = int(sayi.get())

        tk.Scale(ust, from_=0, to=100, orient="horizontal", variable=sayi, showvalue=0,
                 command=degisti, bg=R["kart"], fg=R["yazi"], troughcolor=R["alan_cizgi"],
                 activebackground=R["turkuaz"], highlightthickness=0, bd=0, sliderrelief="flat",
                 sliderlength=18, width=10).pack(fill="x", pady=(2, 8))
        surgu_degerleri[anahtar] = sayi

    surgu(k2, "Üst sınır", "ust_yuzde")
    surgu(k2, "Alt sınır", "alt_yuzde")
    alt_satir = tk.Frame(k2, bg=R["kart"])
    alt_satir.pack(fill="x", pady=(0, 8))
    for metin, anahtar in (("Ekran no", "ekran"), ("Tarama (ms)", "aralik_ms"), ("Yazı boyutu", "yazi_boyutu")):
        sutun = tk.Frame(alt_satir, bg=R["kart"])
        sutun.pack(side="left", fill="x", expand=True, padx=(0, 8))
        tk.Label(sutun, text=metin, font=(YAZI, 8), fg=R["soluk"], bg=R["kart"]).pack(anchor="w")
        giris(sutun, degisken(anahtar)).pack(fill="x", ipady=3)
    dugme(k2, "Bölgeyi ekranda göster", lambda: (kaydet(), bolgeyi_goster(kok, ayarlar))).pack(fill="x", ipady=4)
    tk.Label(k2, text="Pencerede izliyorsanız üst sınırı 55 yapın; tam ekranda 74 uygundur.",
             font=(YAZI, 8), fg=R["soluk"], bg=R["kart"], wraplength=460, justify="left").pack(anchor="w", pady=(6, 0))

    kutu = Kutu(kok, yazi_boyutu=int(ayarlar.get("yazi_boyutu", 20) or 20))
    motor = {"nesne": None}

    def durum_yaz(metin):
        kok.after(0, lambda: durum_goster(metin))

    def kaydet():
        for anahtar, d in alanlar.items():
            deger = d.get().strip()
            if anahtar in ("ekran", "aralik_ms", "yazi_boyutu"):
                try:
                    ayarlar[anahtar] = int(deger)
                except ValueError:
                    pass
            else:
                ayarlar[anahtar] = deger
        for anahtar, sayi in surgu_degerleri.items():
            ayarlar[anahtar] = int(sayi.get())
        if ayarlar["alt_yuzde"] <= ayarlar["ust_yuzde"]:
            ayarlar["alt_yuzde"] = min(100, ayarlar["ust_yuzde"] + 5)
        try:
            kutu.ad.config(font=(YAZI, int(ayarlar.get("yazi_boyutu", 20)), "bold"))
            kutu.etiket.config(font=(YAZI, int(ayarlar.get("yazi_boyutu", 20)), "bold"))
        except Exception:
            pass
        ayarlari_kaydet(ayarlar)

    def baslat_durdur():
        if motor["nesne"]:
            motor["nesne"].dur()
            motor["nesne"] = None
            kutu.yaz("")
            ana_dugme.config(text="Başlat")
            durum_goster("Durduruldu.")
            return
        kaydet()
        if telefon_secili() and not str(ayarlar.get("telefon_kod", "")).strip():
            durum_goster("Önce telefon kodunu yaz (telefonda \"TV ve bilgisayara çeviri sağla\").")
            try:
                kod_girisi.focus_set()
            except Exception:
                pass
            return
        m = Motor(ayarlar, kutu, durum_yaz)
        m.start()
        motor["nesne"] = m
        ana_dugme.config(text="Durdur")
        durum_goster("Çalışıyor. Oyunu veya videoyu açabilirsiniz." if telefon_secili()
                     else "Çalışıyor. Çeviri modeli hazırlanıyor...")

    ana_dugme = dugme(k0, "Başlat", baslat_durdur, dolu=True)
    ana_dugme.pack(fill="x", ipady=7)

    # --- çalışma kaydı ---
    kk = kart("Çalışma kaydı")
    son_hata = tk.StringVar(value=KAYIT.son_hata or "Son hata: yok")
    son_hata_etiketi = tk.Label(kk, textvariable=son_hata, font=(YAZI, 8), fg=R["soluk"], bg=R["kart"],
                                wraplength=460, justify="left")
    son_hata_etiketi.pack(anchor="w", pady=(0, 8))
    ksatir = tk.Frame(kk, bg=R["kart"])
    ksatir.pack(fill="x")

    def kaydi_kopyala():
        try:
            kok.clipboard_clear()
            kok.clipboard_append(KAYIT.metin() or "(kayıt boş)")
            durum_goster("Çalışma kaydı panoya kopyalandı ✓  Sohbete yapıştırabilirsin.")
        except Exception as e:
            durum_goster(f"Kopyalanamadı: {e}")

    def kaydi_goster():
        p = tk.Toplevel(kok)
        p.title("GameLingo · çalışma kaydı")
        p.configure(bg=R["zemin"])
        p.geometry("820x520")
        alan = tk.Text(p, font=("Consolas", 9), bg=R["alan"], fg=R["yazi"], insertbackground=R["turkuaz"],
                       relief="flat", wrap="word", padx=10, pady=8)
        kaydirma = tk.Scrollbar(p, command=alan.yview)
        alan.configure(yscrollcommand=kaydirma.set)
        alt = tk.Frame(p, bg=R["zemin"], pady=6, padx=8)
        alt.pack(side="bottom", fill="x")
        kaydirma.pack(side="right", fill="y")
        alan.pack(side="left", fill="both", expand=True)

        def yenile():
            alan.delete("1.0", "end")
            alan.insert("end", KAYIT.metin() or "(kayıt boş — Başlat'a basınca dolmaya başlar)")
            alan.see("end")

        def temizle():
            KAYIT.temizle()
            yenile()
            durum_goster("Kayıt temizlendi.")

        dugme(alt, "Yenile", yenile).pack(side="left", padx=(0, 6), ipady=3, ipadx=8)
        dugme(alt, "Kopyala", kaydi_kopyala).pack(side="left", padx=(0, 6), ipady=3, ipadx=8)
        dugme(alt, "Temizle", temizle).pack(side="left", ipady=3, ipadx=8)
        tk.Label(alt, text=f"Dosya: {KAYIT.dosya()}", font=(YAZI, 8), fg=R["soluk"], bg=R["zemin"]).pack(side="right")
        yenile()
        return p

    dugme(ksatir, "Kaydı göster", kaydi_goster).pack(side="left", fill="x", expand=True, ipady=4, padx=(0, 6))
    dugme(ksatir, "Kaydı kopyala", kaydi_kopyala).pack(side="left", fill="x", expand=True, ipady=4, padx=(6, 0))

    def arayuz_hatasi(tur, deger, iz):
        import traceback
        KAYIT.yaz("Arayüz hatası: " + "".join(traceback.format_exception(tur, deger, iz))[-600:], hata=True)
        durum_goster(f"Arayüz hatası: {deger}")

    kok.report_callback_exception = arayuz_hatasi

    kok.protocol("WM_DELETE_WINDOW", lambda: (kaydet(), kok.destroy()))
    kok.mainloop()


MODEL_TESTI_CUMLELERI = [
    "Where are you going?",
    "He is waiting outside.",
    "We need to find the others before it gets dark.",
    "Don't move, or I'll shoot.",
]


def model_testi(cikti_yolu):
    """Derleme sonrası denetim (pencere açmaz): modeli indirir/yükler, birkaç cümle çevirir, sonucu dosyaya yazar."""
    satirlar = [f"GameLingo {SURUM} · model testi · {sistem_bilgisi()}"]
    y = YerelCeviri({})
    bas = time.time()
    tamam = y.hazirla(lambda m: None)
    satirlar.append(f"hazırlık: {'TAMAM' if tamam else 'HATA'} ({time.time() - bas:.1f} sn) {y.son_hata or ''}")
    if tamam:
        for c in MODEL_TESTI_CUMLELERI:
            t = y.cevir(c)
            satirlar.append(f"{c}  ->  {t}  ({int(y.son_sure * 1000)} ms)")
            tamam = tamam and bool(t)
    satirlar.append("SONUÇ: " + ("GEÇTİ" if tamam else "BAŞARISIZ"))
    with open(cikti_yolu, "w", encoding="utf-8") as f:
        f.write("\n".join(satirlar) + "\n")
    return tamam


def ocr_testi(cikti_yolu):
    """Derleme sonrası denetim: örnek bir altyazı görüntüsünü iki okuyucuyla okur, süreleri dosyaya EKLER."""
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
    cumle = "I guess they know we're coming."
    img = Image.new("RGB", (1600, 110), (12, 12, 12))
    try:
        yazi = ImageFont.truetype("arial.ttf", 44)
    except Exception:
        yazi = ImageFont.load_default()
    ImageDraw.Draw(img).text((380, 30), cumle, fill=(255, 255, 255), font=yazi)
    bgr = np.ascontiguousarray(np.array(img)[:, :, ::-1])
    satirlar = [f"--- okuma testi: \"{cumle}\""]
    for ad, yap in (("Windows OCR", windows_ocr_olustur), ("RapidOCR", ocr_olustur)):
        try:
            o = yap()
            if o is None:
                satirlar.append(f"{ad}: kullanılamadı"); continue
            sureler, metin = [], ""
            for _ in range(5):
                bas = time.time()
                try:
                    sonuc, _x = o(bgr, use_cls=False)
                except TypeError:
                    sonuc, _x = o(bgr)
                sureler.append(time.time() - bas)
                metin = " ".join(t for _k, t, _g in (sonuc or []))
            satirlar.append(f"{ad}{' (' + o.dil + ')' if hasattr(o, 'dil') else ''}: \"{metin}\" · "
                            f"ilk {int(sureler[0] * 1000)} ms, sonra ort. {int(sum(sureler[1:]) / 4 * 1000)} ms")
        except Exception as e:
            satirlar.append(f"{ad}: HATA {type(e).__name__}: {e}")
    with open(cikti_yolu, "a", encoding="utf-8") as f:
        f.write("\n".join(satirlar) + "\n")


if __name__ == "__main__":
    if "--ocr-testi" in sys.argv:
        i = sys.argv.index("--ocr-testi")
        ocr_testi(sys.argv[i + 1] if len(sys.argv) > i + 1 else "model-testi.txt")
        sys.exit(0)
    if "--model-testi" in sys.argv:
        i = sys.argv.index("--model-testi")
        yol = sys.argv[i + 1] if len(sys.argv) > i + 1 else "model-testi.txt"
        sys.exit(0 if model_testi(yol) else 1)
    try:
        main()
    except Exception as hata:
        import traceback
        KAYIT.yaz("Program çöktü: " + traceback.format_exc()[-800:], hata=True)
        print("Beklenmeyen hata:", hata)
        print("Ayrıntı çalışma kaydında:", KAYIT.dosya())
        input("Kapatmak için Enter'a basın...")
