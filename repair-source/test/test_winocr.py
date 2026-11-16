"""PC 5.13: Windows'un kendi okuyucusu (Windows.Media.Ocr). Sahte winrt modülleriyle:
kurulum, dil seçimi, görüntü aktarımı, sonuç biçimi, altyazı seçimi, hata olunca RapidOCR'a geçiş."""
import asyncio, importlib.util, os, sys, types
PC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pc")
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "stubs")); sys.path.insert(0, PC)
import numpy as np

durum = {"ingilizce": True, "hata": False, "buffer": None, "boyut": None}

def modul(ad, **ozellik):
    m = types.ModuleType(ad); m.__dict__.update(ozellik); sys.modules[ad] = m; return m

class Rect:
    def __init__(self, x, y, w, h): self.x, self.y, self.width, self.height = x, y, w, h
class Kelime:
    def __init__(self, t, x, y, w, h): self.text, self.bounding_rect = t, Rect(x, y, w, h)
class Satir:
    def __init__(self, kelimeler): self.words = kelimeler; self.text = " ".join(k.text for k in kelimeler)
class Motor:
    def __init__(self, dil): self.recognizer_language = types.SimpleNamespace(language_tag=dil)
    async def recognize_async(self, bmp):
        if durum["hata"]: raise OSError("WinRT hatası")
        w = bmp.w
        return types.SimpleNamespace(lines=[
            Satir([Kelime("Tarama", 30, 10, 90, 20), Kelime("(ms)", 125, 10, 40, 20)]),       # pencere yazısı (solda)
            Satir([Kelime("I", w * 0.35, 60, 8, 30), Kelime("guess", w * 0.37, 60, 70, 30),
                   Kelime("they", w * 0.42, 60, 60, 30), Kelime("know.", w * 0.47, 60, 70, 30)]),
        ])
class OcrEngine:
    max_image_dimension = 10000
    @staticmethod
    def is_language_supported(lang): return durum["ingilizce"] and lang.tag.startswith("en")
    @staticmethod
    def try_create_from_language(lang): return Motor(lang.tag)
    @staticmethod
    def try_create_from_user_profile_languages(): return Motor("tr-TR")
class Language:
    def __init__(self, tag): self.tag = tag
class DataWriter:
    def write_bytes(self, b): durum["buffer"] = len(b)
    def detach_buffer(self): return "buf"
class SoftwareBitmap:
    @staticmethod
    def create_copy_from_buffer(buf, fmt, w, h):
        durum["boyut"] = (w, h, fmt); return types.SimpleNamespace(w=w, h=h)

modul("winrt"); modul("winrt.windows")
modul("winrt.windows.media"); modul("winrt.windows.media.ocr", OcrEngine=OcrEngine)
modul("winrt.windows.globalization", Language=Language)
modul("winrt.windows.graphics"); modul("winrt.windows.graphics.imaging", SoftwareBitmap=SoftwareBitmap,
                                       BitmapPixelFormat=types.SimpleNamespace(BGRA8="BGRA8"))
modul("winrt.windows.storage"); modul("winrt.windows.storage.streams", DataWriter=DataWriter)

spec = importlib.util.spec_from_file_location("lingoplay", os.path.join(PC, "lingoplay_pc.py"))
lp = importlib.util.module_from_spec(spec); spec.loader.exec_module(lp)
sorun = []

# 1) Windows dışında (ve deneme bayrağı yokken) Windows OCR hiç denenmez
os.environ.pop("GL_WINOCR_DENE", None)
if lp.windows_ocr_olustur() is not None and not sys.platform.startswith("win"):
    sorun.append("Windows dışında Windows OCR kuruldu")

os.environ["GL_WINOCR_DENE"] = "1"
# 2) Kurulum: İngilizce okuma paketi varsa onu seçer
o = lp.okuyucu_olustur()
if not isinstance(o, lp.WindowsOcr) or o.dil != "en-US" or "Windows OCR (en-US)" not in lp.OCR_BILGI:
    sorun.append(f"Windows OCR kurulmadı: {o} {lp.OCR_BILGI}")

# 3) Görüntü aktarımı ve sonuç biçimi (RapidOCR ile aynı)
bgr = np.zeros((110, 1600, 3), dtype=np.uint8)
sonuc, _ = o(bgr, use_cls=False)
if durum["buffer"] != 1600 * 110 * 4 or durum["boyut"] != (1600, 110, "BGRA8"):
    sorun.append(f"görüntü yanlış aktarıldı: {durum}")
if len(sonuc) != 2 or sonuc[1][1] != "I guess they know." or len(sonuc[1][0]) != 4 or sonuc[1][2] < 0.5:
    sorun.append(f"sonuç biçimi yanlış: {sonuc}")

# 4) Motor.oku: pencere yazısı atılır, altyazı kalır
class Kutu:
    def yaz(self, *a, **k): pass
mot = lp.Motor(dict(lp.VARSAYILAN), Kutu(), lambda s: None)
if not isinstance(mot.ocr, lp.WindowsOcr): sorun.append("motor Windows OCR'ı seçmedi")
if mot.oku(bgr) != "I guess they know.": sorun.append(f"oku yanlış: {mot.oku(bgr)!r}")

# 5) Windows OCR hata verirse RapidOCR'a geçilir, program durmaz
durum["hata"] = True
lp.KAYIT.temizle()
mot.oku(bgr)
if isinstance(mot.ocr, lp.WindowsOcr) or "RapidOCR'a geçiliyor" not in lp.KAYIT.metin():
    sorun.append("hata sonrası RapidOCR'a geçilmedi")
if mot.oku(bgr) != "Hold on": sorun.append("RapidOCR'a geçtikten sonra okunmadı")
durum["hata"] = False

# 6) İngilizce paketi yoksa Windows'un diliyle (Türkçe) çalışır
durum["ingilizce"] = False
o2 = lp.WindowsOcr()
if "tr-TR" not in o2.dil: sorun.append(f"dil yedeği yanlış: {o2.dil}")
durum["ingilizce"] = True

# 7) Çok büyük görüntü sınırın altına indirilir, kutular asıl ölçeğe döner
o.en_buyuk = 800
sonuc, _ = o(bgr)
if durum["boyut"][0] > 800 or sonuc[1][0][1][0] < 1600 * 0.47:
    sorun.append(f"büyük görüntü ölçeklemesi yanlış: {durum['boyut']} {sonuc[1][0]}")

# 8) winrt yoksa: RapidOCR, açıklayıcı kayıtla
for ad in [k for k in sys.modules if k.startswith("winrt")]:
    del sys.modules[ad]
sys.modules["winrt"] = None
lp.KAYIT.temizle()
o3 = lp.okuyucu_olustur()
if isinstance(o3, lp.WindowsOcr) or "Windows OCR kullanılamadı" not in lp.KAYIT.metin():
    sorun.append("winrt yokken yedek çalışmadı")

# 9) derleme sonrası okuma testi dosyaya ekler (PIL varsa)
try:
    import PIL  # noqa
    import tempfile
    yol = os.path.join(tempfile.mkdtemp(), "model-testi.txt")
    open(yol, "w", encoding="utf-8").write("SONUÇ: GEÇTİ\n")
    lp.ocr_testi(yol)
    icerik = open(yol, encoding="utf-8").read()
    if not icerik.startswith("SONUÇ: GEÇTİ") or "okuma testi" not in icerik or "RapidOCR" not in icerik:
        sorun.append(f"okuma testi çıktısı: {icerik}")
    print("9)", icerik.strip().splitlines()[-1])
except ImportError:
    print("9) PIL yok, okuma testi atlandı")

if sorun:
    print("BAŞARISIZ:"); [print(" -", s) for s in sorun]; sys.exit(1)
print("Windows OCR testi geçti.")
